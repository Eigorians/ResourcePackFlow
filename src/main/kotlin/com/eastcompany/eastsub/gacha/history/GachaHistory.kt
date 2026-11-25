package com.eastcompany.eastsub.gacha.history

import com.eastcompany.eastsub.gacha.history.GachaHistory.MAX_ENTRIES
import com.eastcompany.eastsub.gacha.history.GachaHistory.groupConsecutive
import com.eastcompany.eastsub.gacha.model.ModelRarity
import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import com.google.gson.Gson
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 「誰が自分（作成者）のガチャを回したか」の履歴。
 *
 * 保存先:
 * plugins/ResourcePackFlow/resourcepack/config/history/<作成者名>.json
 *
 * - totalSpins       : 他のプレイヤーに回された合計回数
 * - totalEarned      : 稼いだ総額
 * - playerStats      : プレイヤーごとの累計回数・獲得アイテム
 * - entries          : 直近 [MAX_ENTRIES] 件
 *
 * 同じ人が続けて回した分は、表示時に [groupConsecutive] でまとめる。
 */
object GachaHistory {

    const val MAX_ENTRIES = 100

    /** 同じ人でも、これ以上間が空いたら別のグループにする */
    const val GROUP_GAP_MILLIS = 10L * 60 * 1000

    /**
     * 1回分の履歴
     */
    data class Entry(
        val timestamp: Long,
        val time: String,
        val playerUuid: String?,
        val playerName: String,
        val result: String,          // "WIN" or "MISS"
        val modelId: String? = null,
        val itemName: String? = null,
        val rarity: String? = null,
        val earned: Double = 0.0
    )

    /**
     * プレイヤーごとの全期間集計
     */
    data class PlayerStats(
        var playerUuid: String? = null,
        var playerName: String = "",
        var totalSpins: Int = 0,

        /**
         * アイテム名 -> 獲得回数
         *
         * はずれはここには入れず、totalSpins - items.values.sum() で計算する。
         */
        val items: MutableMap<String, Int> = mutableMapOf()
    )

    data class AuthorHistory(
        var authorUuid: String?,
        var authorName: String,

        var totalSpins: Int = 0,
        var totalEarned: Double = 0.0,

        /**
         * プレイヤーを一意に識別するためのキー -> 集計
         *
         * UUIDがある場合:
         *   uuid:<UUID>
         *
         * UUIDがない場合:
         *   name:<名前>
         */
        val playerStats: MutableMap<String, PlayerStats> = mutableMapOf(),

        /**
         * 直近100件
         */
        val entries: MutableList<Entry> = mutableListOf()
    )

    /**
     * 連続して回した分のまとまり
     */
    data class SpinGroup(
        val playerName: String,
        val playerUuid: String?,
        val count: Int,
        val earned: Double,
        val newestTime: String,
        val oldestTime: String,
        val entries: List<Entry>
    )

    /**
     * Gson読み込み用。
     *
     * playerStats が存在しない旧JSONにも対応する。
     */
    private data class Raw(
        val authorUuid: String?,
        val authorName: String?,
        val totalSpins: Int?,
        val totalEarned: Double?,
        val playerStats: Map<String, PlayerStats>?,
        val entries: List<Entry>?
    )

    private val rarityOrder = listOf(
        ModelRarity.S,
        ModelRarity.A,
        ModelRarity.B,
        ModelRarity.C,
        ModelRarity.D,
        ModelRarity.E,
        ModelRarity.F,
        ModelRarity.UNCLASSIFIED,
        ModelRarity.MISS
    )

    private lateinit var dir: File

    private val gson = Gson()

    private val lock = Any()

    /**
     * key = 作成者名（小文字）
     */
    private val cache = HashMap<String, AuthorHistory>()

    private val timeFormat =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "GachaHistory-Writer").apply {
            isDaemon = true
        }
    }

    // ------------------------------------------------------------------
    // 初期化
    // ------------------------------------------------------------------

    fun init(plugin: ResourcePackFlow) {
        dir = File(
            plugin.dataFolder,
            "resourcepack/config/history"
        )

        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    // ------------------------------------------------------------------
    // 書き込み
    // ------------------------------------------------------------------

    /**
     * 作成者のガチャが1回回されたことを記録する。
     */
    fun record(
        authorName: String,
        authorUuid: String?,
        playerUuid: String?,
        playerName: String,
        result: String,
        earned: Double,
        modelId: String? = null,
        itemName: String? = null,
        rarity: String? = null
    ) {
        val name = authorName
            .takeIf { it.isNotBlank() }
            ?: return

        val now = Instant.now()

        val entry = Entry(
            timestamp = now.toEpochMilli(),
            time = timeFormat.format(now),
            playerUuid = playerUuid,
            playerName = playerName,
            result = result,
            modelId = modelId,
            itemName = itemName,
            rarity = rarity,
            earned = earned
        )

        val json: String
        val file: File

        synchronized(lock) {
            val history = loadLocked(name)
                ?: AuthorHistory(
                    authorUuid = authorUuid,
                    authorName = name
                ).also {
                    cache[name.lowercase()] = it
                }

            history.authorUuid =
                authorUuid ?: history.authorUuid

            history.authorName = name

            // ----------------------------------------------------------
            // 全体集計
            // ----------------------------------------------------------

            history.totalSpins++
            history.totalEarned += earned

            // ----------------------------------------------------------
            // プレイヤー別集計
            // ----------------------------------------------------------

            val playerKey = playerKey(
                playerUuid = playerUuid,
                playerName = playerName
            )

            val stats = history.playerStats[playerKey]
                ?: PlayerStats(
                    playerUuid = playerUuid,
                    playerName = playerName
                ).also {
                    history.playerStats[playerKey] = it
                }

            // 名前が変わった場合は最新の名前を表示
            stats.playerName = playerName

            // UUIDが後から取得できるようになった場合
            if (!playerUuid.isNullOrBlank()) {
                stats.playerUuid = playerUuid
            }

            stats.totalSpins++

            // WINしたアイテムだけ記録
            if (
                result.equals("WIN", ignoreCase = true)
            ) {
                val itemKey = itemName
                    ?.takeIf { it.isNotBlank() }
                    ?: modelId
                        ?.takeIf { it.isNotBlank() }
                    ?: "不明なアイテム"

                stats.items[itemKey] =
                    (stats.items[itemKey] ?: 0) + 1
            }

            // ----------------------------------------------------------
            // 直近履歴
            // ----------------------------------------------------------

            history.entries.add(entry)

            while (history.entries.size > MAX_ENTRIES) {
                history.entries.removeAt(0)
            }

            // ----------------------------------------------------------
            // 保存
            // ----------------------------------------------------------

            json = gson.toJson(history)

            file = findFile(name)
                ?: File(
                    dir,
                    fileName(name)
                )
        }

        executor.execute {
            write(file, json)
        }
    }

    private fun write(
        file: File,
        json: String
    ) {
        try {
            val tmp = File(
                file.parentFile,
                file.name + ".tmp"
            )

            tmp.writeText(
                json,
                StandardCharsets.UTF_8
            )

            Files.move(
                tmp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: Exception) {
            org.bukkit.Bukkit.getLogger().warning(
                "[GachaHistory] ${file.name} の保存に失敗しました: ${e.message}"
            )
        }
    }

    // ------------------------------------------------------------------
    // 読み取り
    // ------------------------------------------------------------------

    /**
     * 作成者名から履歴を取得する。
     *
     * コピーを返す。
     */
    fun get(
        authorName: String
    ): AuthorHistory? =
        synchronized(lock) {
            loadLocked(authorName)?.let {
                it.copy(
                    playerStats = it.playerStats.mapValues { (_, stats) ->
                        stats.copy(
                            items = stats.items.toMutableMap()
                        )
                    }.toMutableMap(),
                    entries = it.entries.toMutableList()
                )
            }
        }

    /**
     * 稼いだ総額。
     */
    fun totalEarned(
        authorName: String
    ): Double =
        get(authorName)?.totalEarned ?: 0.0

    // ------------------------------------------------------------------
    // プレイヤー集計
    // ------------------------------------------------------------------

    /**
     * プレイヤー別集計を表示用に取得する。
     *
     * 回数の多い順。
     */
    fun playerStats(
        history: AuthorHistory
    ): List<PlayerStats> {
        return history.playerStats.values
            .sortedWith(
                compareByDescending<PlayerStats> { it.totalSpins }
                    .thenBy { it.playerName.lowercase() }
            )
    }

    /**
     * はずれ回数。
     */
    fun missCount(
        stats: PlayerStats
    ): Int {
        val winCount = stats.items.values.sum()

        return (stats.totalSpins - winCount)
            .coerceAtLeast(0)
    }

    /**
     * プレイヤーのアイテム集計を表示用に取得する。
     */
    fun itemStats(
        stats: PlayerStats
    ): List<Pair<String, Int>> {
        return stats.items.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key.lowercase() }
            )
            .map {
                it.key to it.value
            }
    }

    // ------------------------------------------------------------------
    // 連続履歴グループ
    // ------------------------------------------------------------------

    /**
     * 新しい順に見て、
     * 同じ人が続けて回した分を1つにまとめる。
     *
     * 別の人が間に入る、
     * または GROUP_GAP_MILLIS 以上空くと別グループ。
     */
    fun groupConsecutive(
        history: AuthorHistory
    ): List<SpinGroup> {

        val groups =
            mutableListOf<MutableList<Entry>>()

        for (e in history.entries.asReversed()) {

            val current = groups.lastOrNull()
            val prev = current?.last()

            val joinable =
                prev != null &&
                        sameSpinner(prev, e) &&
                        prev.timestamp - e.timestamp <= GROUP_GAP_MILLIS

            if (
                current != null &&
                joinable
            ) {
                current.add(e)
            } else {
                groups.add(
                    mutableListOf(e)
                )
            }
        }

        return groups.map { g ->
            SpinGroup(
                playerName = g.first().playerName,
                playerUuid = g.first().playerUuid,
                count = g.size,
                earned = g.sumOf { it.earned },
                newestTime = g.first().time,
                oldestTime = g.last().time,
                entries = g
            )
        }
    }

    private fun sameSpinner(
        a: Entry,
        b: Entry
    ): Boolean {

        if (
            a.playerUuid != null &&
            b.playerUuid != null
        ) {
            return a.playerUuid.equals(
                b.playerUuid,
                ignoreCase = true
            )
        }

        return a.playerName.equals(
            b.playerName,
            ignoreCase = true
        )
    }

    // ------------------------------------------------------------------
    // その他
    // ------------------------------------------------------------------

    fun rarityOf(
        e: Entry
    ): ModelRarity =
        if (
            e.result.equals(
                "WIN",
                ignoreCase = true
            )
        ) {
            ModelRarity.fromString(e.rarity)
        } else {
            ModelRarity.MISS
        }

    /**
     * レア度ごとの件数
     */
    fun summarize(
        entries: List<Entry>
    ): List<Pair<ModelRarity, Int>> {

        val counts =
            entries
                .groupingBy {
                    rarityOf(it)
                }
                .eachCount()

        return rarityOrder.mapNotNull { rarity ->
            counts[rarity]?.let {
                rarity to it
            }
        }
    }

    /**
     * 金額の表示。
     */
    fun formatMoney(
        value: Double
    ): String =
        if (
            value == value.toLong().toDouble()
        ) {
            value.toLong().toString()
        } else {
            String.format(
                Locale.ROOT,
                "%.1f",
                value
            )
        }

    /**
     * ゲーム内表示用。
     */
    fun formatLines(
        history: AuthorHistory,
        limit: Int = 10
    ): List<String> {

        val lines =
            mutableListOf<String>()

        lines +=
            "§f[§eガチャ履歴§f] §b${history.authorName}"

        lines +=
            "§fこれまでに §a${history.totalSpins} §f回 回されました"

        lines +=
            "§f稼いだ総額: §e${formatMoney(history.totalEarned)} PEN"

        val groups =
            groupConsecutive(history)
                .take(limit)

        if (groups.isNotEmpty()) {

            lines +=
                "§7直近の履歴（最大 $MAX_ENTRIES 件まで保存）"

            groups.forEach { g ->

                val label =
                    if (g.count == 1) {

                        val e =
                            g.entries.first()

                        val r =
                            rarityOf(e)

                        if (
                            e.result.equals(
                                "WIN",
                                ignoreCase = true
                            )
                        ) {
                            "${r.color}[${r.displayName}] §f${
                                e.itemName
                                    ?: e.modelId
                                    ?: "?"
                            }"
                        } else {
                            "§8[はずれ]"
                        }

                    } else {

                        summarize(g.entries)
                            .joinToString(" ") {
                                    (r, n) ->
                                "${r.color}[${r.displayName}]§f×$n"
                            }
                    }

                val countText =
                    if (g.count > 1) {
                        " §e×${g.count}"
                    } else {
                        ""
                    }

                val shortTime =
                    g.newestTime
                        .takeIf { it.length >= 16 }
                        ?.substring(5, 16)
                        ?: g.newestTime

                lines +=
                    "§7$shortTime §8| §b${g.playerName}$countText §8| $label §8| §a+${formatMoney(g.earned)}"
            }
        }

        return lines
    }

    // ------------------------------------------------------------------
    // 内部処理
    // ------------------------------------------------------------------

    /**
     * UUIDがある場合はUUIDを優先。
     * UUIDがない場合だけ名前を使う。
     */
    private fun playerKey(
        playerUuid: String?,
        playerName: String
    ): String {

        if (!playerUuid.isNullOrBlank()) {
            return "uuid:${playerUuid.lowercase()}"
        }

        return "name:${playerName.lowercase()}"
    }

    /**
     * キャッシュ → ファイルの順。
     */
    private fun loadLocked(
        authorName: String
    ): AuthorHistory? {

        val key =
            authorName.lowercase()

        cache[key]?.let {
            return it
        }

        val file =
            findFile(authorName)
                ?: return null

        return try {

            val raw =
                gson.fromJson(
                    file.readText(
                        StandardCharsets.UTF_8
                    ),
                    Raw::class.java
                )
                    ?: return null

            val entries =
                raw.entries
                    ?.toMutableList()
                    ?: mutableListOf()

            val playerStats =
                raw.playerStats
                    ?.mapValues { (_, stats) ->
                        stats.copy(
                            items =
                                stats.items.toMutableMap()
                        )
                    }
                    ?.toMutableMap()
                    ?: mutableMapOf()

            val history =
                AuthorHistory(
                    authorUuid = raw.authorUuid,
                    authorName =
                        raw.authorName
                            ?: authorName,

                    totalSpins =
                        raw.totalSpins
                            ?: entries.size,

                    totalEarned =
                        raw.totalEarned
                            ?: entries.sumOf {
                                it.earned
                            },

                    playerStats =
                        playerStats,

                    entries =
                        entries
                )

            /*
             * 旧バージョンのJSONには playerStats がない。
             *
             * その場合は保存されている entries
             * （最大100件）から可能な範囲で再構築する。
             */
            if (
                raw.playerStats == null &&
                entries.isNotEmpty()
            ) {
                rebuildPlayerStatsFromEntries(
                    history
                )
            }

            history.also {
                cache[key] = it
            }

        } catch (e: Exception) {

            org.bukkit.Bukkit.getLogger().warning(
                "[GachaHistory] ${file.name} の読み込みに失敗しました: ${e.message}"
            )

            null
        }
    }

    /**
     * 旧形式のentriesからプレイヤー集計を再構築。
     *
     * 注意:
     * entriesは最大100件なので、
     * 100件より古いデータは復元できない。
     */
    private fun rebuildPlayerStatsFromEntries(
        history: AuthorHistory
    ) {

        history.playerStats.clear()

        for (entry in history.entries) {

            val key =
                playerKey(
                    entry.playerUuid,
                    entry.playerName
                )

            val stats =
                history.playerStats[key]
                    ?: PlayerStats(
                        playerUuid =
                            entry.playerUuid,

                        playerName =
                            entry.playerName
                    ).also {
                        history.playerStats[key] = it
                    }

            stats.playerName =
                entry.playerName

            if (
                !entry.playerUuid.isNullOrBlank()
            ) {
                stats.playerUuid =
                    entry.playerUuid
            }

            stats.totalSpins++

            if (
                entry.result.equals(
                    "WIN",
                    ignoreCase = true
                )
            ) {

                val itemKey =
                    entry.itemName
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: entry.modelId
                            ?.takeIf {
                                it.isNotBlank()
                            }
                        ?: "不明なアイテム"

                stats.items[itemKey] =
                    (stats.items[itemKey] ?: 0) + 1
            }
        }
    }

    private fun fileName(
        name: String
    ): String =
        name.replace(
            Regex("[^A-Za-z0-9_.\\-]"),
            "_"
        ) + ".json"

    /**
     * 大文字小文字を区別せずにファイルを探す。
     */
    private fun findFile(
        name: String
    ): File? {

        val target =
            fileName(name)

        val exact =
            File(dir, target)

        if (exact.exists()) {
            return exact
        }

        return dir
            .listFiles()
            ?.firstOrNull {
                it.name.equals(
                    target,
                    ignoreCase = true
                )
            }
    }

    /**
     * プラグイン停止時に呼ぶ。
     */
    fun shutdown() {

        executor.shutdown()

        executor.awaitTermination(
            5,
            TimeUnit.SECONDS
        )
    }
}