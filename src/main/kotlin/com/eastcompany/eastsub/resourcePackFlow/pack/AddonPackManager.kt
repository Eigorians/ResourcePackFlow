package com.eastcompany.eastsub.resourcePackFlow.pack

import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** addons ディレクトリに置かれた追加パック(zip) */
data class AddonPack(
    val id: String,
    val file: File
) {
    val size: Long get() = file.length()

    /** クライアントへ送るときのパックID。同じ追加パックは常に同じUUIDになる */
    val packUuid: UUID get() = AddonPackManager.addonUuid(id)
}

/**
 * 追加パック(zip)の管理。
 *
 * 追加パックは本体の pack.zip とは別のリソースパックとして送信する
 * (Minecraft 1.20.3 以降はサーバーから複数のパックを重ねて送れる)。
 * マージはしないので、ビルド時の処理は増えず、
 * クライアントもパックごとにキャッシュを使える。
 *
 * - 追加パック: <dataFolder>/<addons-directory> 内の zip（ファイル名がID）
 * - 配信URL  : /addons/<ID>.zip
 */
class AddonPackManager(
    private val plugin: ResourcePackFlow
) {

    companion object {
        val ADDON_ID_REGEX = Regex("^[A-Za-z0-9_-]+$")

        /** 本体 pack.zip のパックID */
        val BASE_PACK_UUID: UUID = UUID.nameUUIDFromBytes("ResourcePackFlow:base".toByteArray(Charsets.UTF_8))

        fun addonUuid(id: String): UUID =
            UUID.nameUUIDFromBytes("ResourcePackFlow:addon:$id".toByteArray(Charsets.UTF_8))
    }

    /** SHA-1 のキャッシュ（パス → (更新日時, サイズ, SHA-1)） */
    private val hashCache = ConcurrentHashMap<String, Triple<Long, Long, String>>()

    /** 警告済みのファイル名（一覧取得はGUIや補完のたびに走るため、警告は1回だけ） */
    private val warnedNames = ConcurrentHashMap.newKeySet<String>()

    val addonsDirectory: File
        get() = File(plugin.dataFolder, plugin.configManager.addonsDirectory)

    /** 追加パックディレクトリが無ければ作る */
    fun ensureDirectories() {
        if (!addonsDirectory.exists()) {
            addonsDirectory.mkdirs()
        }
    }

    /** 利用できる追加パック一覧（ID順） */
    fun listAddons(): List<AddonPack> {
        val dir = addonsDirectory
        if (!dir.isDirectory) return emptyList()

        return dir.listFiles { f -> f.isFile && f.name.endsWith(".zip", ignoreCase = true) }
            .orEmpty()
            .mapNotNull { file ->
                val id = file.name.substring(0, file.name.length - 4)
                if (!id.matches(ADDON_ID_REGEX)) {
                    if (warnedNames.add(file.name)) {
                        plugin.logger.warning(
                            "追加パック ${file.name} はファイル名に使えない文字を含むため無視します（半角英数字・_・- のみ）。"
                        )
                    }
                    null
                } else {
                    AddonPack(id, file)
                }
            }
            .sortedBy { it.id.lowercase() }
    }

    /** IDから追加パックを探す（HTTP配信用） */
    fun findAddon(id: String): AddonPack? {
        if (!id.matches(ADDON_ID_REGEX)) return null
        return listAddons().firstOrNull { it.id == id }
    }

    /** 本人の選択を優先し、未選択ならアドオンの初期状態に従う */
    fun isEnabled(player: UUID, id: String): Boolean =
        plugin.playerSettingsManager.getAddonChoice(player, id)
            ?: plugin.addonSettingsManager.get(id).defaultEnabled

    /** そのプレイヤーにとってONの追加パック */
    fun enabledAddons(player: UUID): List<AddonPack> =
        listAddons().filter { isEnabled(player, it.id) }

    /** ファイルの SHA-1（更新日時・サイズが同じならキャッシュを使う） */
    fun sha1Of(file: File): String {
        val path = file.absolutePath
        val modified = file.lastModified()
        val size = file.length()

        hashCache[path]?.let { (m, s, hash) ->
            if (m == modified && s == size) return hash
        }

        val hash = PackHasher.sha1Hex(file)
        hashCache[path] = Triple(modified, size, hash)
        return hash
    }

    /**
     * 以前のマージ方式で作られた packs/ フォルダが残っていれば削除する。
     */
    fun cleanupLegacyVariants() {
        val legacy = File(plugin.dataFolder, "packs")
        if (!legacy.isDirectory) return

        legacy.listFiles { f -> f.isFile && f.name.matches(Regex("^pack-[0-9a-f]{16}\\.zip$")) }
            .orEmpty()
            .forEach { it.delete() }

        if (legacy.listFiles().isNullOrEmpty()) {
            legacy.delete()
            plugin.logger.info("不要になった packs/ フォルダを削除しました。")
        }
    }
}
