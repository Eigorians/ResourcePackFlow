package com.eastcompany.eastsub.gacha.web.session

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.bukkit.Bukkit
import java.io.File
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ResourcePackSessionManager(private val dataFolder: File) {

    private val passwordExpirationMillis = TimeUnit.MINUTES.toMillis(5)
    // Webセッションの有効期限（例: 7日間）
    private val sessionExpirationMillis = TimeUnit.DAYS.toMillis(30)

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // 保存ファイル
    private val usersFile = File(dataFolder, "users.json")
    private val sessionsFile = File(dataFolder, "sessions.json")

    private data class AuthEntry(
        val password: String,
        val expiresAt: Long
    )

    /**
     * Webセッション保存用のデータ構造
     */
    data class SessionEntry(
        val playerUuid: String,
        val expiresAt: Long
    )

    /**
     * プレイヤーデータ構造
     * skinValue / skinSignature は後から追加したフィールド。
     * 既存の users.json（フィールドなし）は Gson により null で読み込まれる。
     */
    data class PlayerData(
        val uuid: String,
        val name: String,
        val skinValue: String? = null,
        val skinSignature: String? = null
    )

    // 保持データ
    private val activePasswords = ConcurrentHashMap<UUID, AuthEntry>()
    private val webSessions = ConcurrentHashMap<String, SessionEntry>()

    // users.json のメモリキャッシュ（登録順を保持するため LinkedHashMap）
    // key = uuid（小文字）。アクセスはすべて this の @Synchronized で保護する
    private val usersCache = LinkedHashMap<String, PlayerData>()

    private val scheduler: ScheduledExecutorService

    init {
        // データフォルダーがなければ自動作成
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }

        // 0. users.json をキャッシュへ読み込み
        loadUsersFromFile()

        // 1. 永続化されたWebセッションの復元
        loadSessions()

        // 2. 定期クリーンアップ（期限切れのパスワード & Webセッションを消去）
        scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "ResourcePackSessionManager-Cleaner").apply { isDaemon = true }
        }
        scheduler.scheduleAtFixedRate({
            val now = System.currentTimeMillis()
            activePasswords.entries.removeIf { it.value.expiresAt <= now }

            val sessionRemoved = webSessions.entries.removeIf { it.value.expiresAt <= now }
            if (sessionRemoved) {
                saveSessions()
            }
        }, 1, 5, TimeUnit.MINUTES)
    }

    /** プラグイン停止時に呼ぶ */
    fun shutdown() {
        scheduler.shutdownNow()
    }

    // ------------------------------------------------------------------
    // Web セッション永続化
    // ------------------------------------------------------------------

    /**
     * sessions.json からセッション一覧をロードする
     */
    @Synchronized
    private fun loadSessions() {
        if (!sessionsFile.exists()) return

        try {
            val jsonText = sessionsFile.readText(Charsets.UTF_8)
            if (jsonText.isNotBlank()) {
                val type = object : TypeToken<Map<String, SessionEntry>>() {}.type
                val loaded: Map<String, SessionEntry>? = gson.fromJson(jsonText, type)
                if (loaded != null) {
                    val now = System.currentTimeMillis()
                    // 有効期限内のセッションのみメモリに展開
                    loaded.filterValues { it.expiresAt > now }.forEach { (token, entry) ->
                        webSessions[token] = entry
                    }
                }
            }
        } catch (e: Exception) {
            Bukkit.getLogger().warning("[ResourcePackFlow] sessions.json の読み込みに失敗しました: ${e.message}")
        }
    }

    /**
     * Webセッション状態を sessions.json へ保存する
     */
    @Synchronized
    private fun saveSessions() {
        try {
            val jsonText = gson.toJson(webSessions)
            sessionsFile.writeText(jsonText, Charsets.UTF_8)
        } catch (e: Exception) {
            Bukkit.getLogger().warning("[ResourcePackFlow] sessions.json の保存に失敗しました: ${e.message}")
        }
    }

    // ------------------------------------------------------------------
    // users.json（キャッシュ + 永続化）
    // ------------------------------------------------------------------

    /**
     * users.json をファイルから読み込み、キャッシュを作り直す
     */
    @Synchronized
    private fun loadUsersFromFile() {
        usersCache.clear()
        if (!usersFile.exists()) return

        try {
            val jsonText = usersFile.readText(Charsets.UTF_8)
            if (jsonText.isNotBlank()) {
                val type = object : TypeToken<MutableList<PlayerData>>() {}.type
                val loaded: List<PlayerData>? = gson.fromJson(jsonText, type)
                loaded?.forEach { usersCache[it.uuid.lowercase()] = it }
            }
        } catch (e: Exception) {
            Bukkit.getLogger().warning("[ResourcePackFlow] users.json の読み込みに失敗しました: ${e.message}")
        }
    }

    /**
     * キャッシュの内容を users.json へ書き出す
     */
    @Synchronized
    private fun persistUsers() {
        try {
            usersFile.writeText(gson.toJson(usersCache.values.toList()), Charsets.UTF_8)
        } catch (e: Exception) {
            Bukkit.getLogger().warning("[ResourcePackFlow] users.json への保存に失敗しました: ${e.message}")
        }
    }

    /**
     * 全ユーザーをリストとして返す（キャッシュのコピー。ファイルI/Oなし）
     */
    @Synchronized
    fun loadAllUsers(): MutableList<PlayerData> = usersCache.values.toMutableList()

    @Synchronized
    fun loadPlayerDataByUuid(playerUuid: UUID): PlayerData? =
        usersCache[playerUuid.toString().lowercase()]

    @Synchronized
    fun loadPlayerDataByName(playerName: String): PlayerData? {
        if (playerName.isBlank()) return null
        return usersCache.values.firstOrNull { it.name.equals(playerName, ignoreCase = true) }
    }

    @Synchronized
    fun savePlayerData(playerUuid: UUID, playerName: String) {
        if (playerName.isBlank()) return

        val key = playerUuid.toString().lowercase()
        if (usersCache.containsKey(key)) return

        usersCache[key] = PlayerData(uuid = playerUuid.toString(), name = playerName)
        persistUsers()
    }

    /**
     * スキン（textures プロパティ）を保存する。
     * 未登録ユーザーは無視する。name を渡すと名前変更にも追従する。
     * 内容が変わっていなければファイルへは書かない。
     * 非同期スレッドから呼んでも安全。
     */
    @Synchronized
    fun updateSkin(playerUuid: UUID, value: String, signature: String?, name: String? = null) {
        val key = playerUuid.toString().lowercase()
        val old = usersCache[key] ?: return

        val newName = if (!name.isNullOrBlank()) name else old.name
        if (old.skinValue == value && old.skinSignature == signature && old.name == newName) return

        usersCache[key] = old.copy(name = newName, skinValue = value, skinSignature = signature)
        persistUsers()
    }

    fun registerOrGetPlayer(playerUuid: UUID): String {
        val existingData = loadPlayerDataByUuid(playerUuid)
        if (existingData != null) {
            return existingData.name
        }

        val offlinePlayer = Bukkit.getOfflinePlayer(playerUuid)
        val initialName = offlinePlayer.name ?: "UnknownPlayer"

        savePlayerData(playerUuid, initialName)

        return initialName
    }

    // ------------------------------------------------------------------
    // パスワード / Web セッション
    // ------------------------------------------------------------------

    fun generateAndRegister(playerUuid: UUID): String {
        registerOrGetPlayer(playerUuid)

        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-="
        val random = SecureRandom()
        val password = (1..16)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")

        val expiresAt = System.currentTimeMillis() + passwordExpirationMillis
        activePasswords[playerUuid] = AuthEntry(password, expiresAt)
        return password
    }

    fun consumePassword(inputPassword: String): UUID? {
        val now = System.currentTimeMillis()
        val foundEntry = activePasswords.entries.firstOrNull { (_, entry) ->
            entry.expiresAt > now && entry.password == inputPassword
        } ?: return null

        val playerUuid = foundEntry.key
        activePasswords.remove(playerUuid)

        registerOrGetPlayer(playerUuid)

        return playerUuid
    }

    /**
     * Webセッションを作成し、sessions.json へ永続化する
     */
    fun createWebSession(playerUuid: UUID): String {
        val sessionToken = UUID.randomUUID().toString()
        val expiresAt = System.currentTimeMillis() + sessionExpirationMillis

        webSessions[sessionToken] = SessionEntry(playerUuid.toString(), expiresAt)
        saveSessions() // ファイルへ即時書き込み

        return sessionToken
    }

    /**
     * セッションJSトークンから UUID を取得する（期限切れチェック含む）
     */
    fun getPlayerUuidBySession(sessionToken: String?): UUID? {
        if (sessionToken.isNullOrBlank()) return null

        val entry = webSessions[sessionToken] ?: return null
        if (entry.expiresAt <= System.currentTimeMillis()) {
            webSessions.remove(sessionToken)
            saveSessions()
            return null
        }

        return try {
            UUID.fromString(entry.playerUuid)
        } catch (_: Exception) {
            null
        }
    }

    fun getPlayerName(playerUuid: UUID): String {
        return registerOrGetPlayer(playerUuid)
    }

    /**
     * セッションを削除（破棄）し、sessions.json に反映する
     */
    fun invalidateSession(sessionToken: String?) {
        if (!sessionToken.isNullOrBlank()) {
            if (webSessions.remove(sessionToken) != null) {
                saveSessions()
            }
        }
    }
}