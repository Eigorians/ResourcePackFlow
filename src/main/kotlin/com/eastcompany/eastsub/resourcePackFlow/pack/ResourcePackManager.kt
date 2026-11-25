package com.eastcompany.eastsub.resourcePackFlow.pack

import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ResourcePackManager(
    private val plugin: ResourcePackFlow
) {

    private val zipper = PackZipper(plugin)
    private val theHatModelGenerator = TheHatModelGenerator(plugin)

    /** 追加パック(addons フォルダの zip)の管理 */
    val addonPacks = AddonPackManager(plugin).also { it.ensureDirectories() }

    /**
     * resourcepack ディレクトリをZIP化して、SHA-1を計算する。
     */
    fun build(): BuildResult {
        val lockFile = File(plugin.dataFolder, "build.lock")

        RandomAccessFile(lockFile, "rw").use { randomAccessFile ->
            randomAccessFile.channel.use { channel ->
                plugin.logger.info("リソースパックのビルドロックを取得しています...")

                channel.lock().use {
                    plugin.logger.info("リソースパックのビルドロックを取得しました。")

                    // TheHat用items JSONを生成
                    theHatModelGenerator.generate()

                    // リソースパックをZIP化
                    val zip = zipper.createZip()

                    // ZIP確定後にSHA-1を計算
                    val hash = PackHasher.sha1Hex(zip)
                    val size = zip.length()

                    plugin.logger.info("Resource pack ZIP: ${zip.name}")
                    plugin.logger.info("Resource pack SHA-1: $hash")
                    plugin.logger.info("Resource pack size: $size bytes")

                    // 追加パックは別パックとして送るのでマージ不要。
                    // 以前のマージ方式の残り(packs/)があれば掃除する
                    addonPacks.cleanupLegacyVariants()

                    val addons = addonPacks.listAddons()
                    if (addons.isNotEmpty()) {
                        plugin.logger.info("追加パック: ${addons.joinToString(", ") { it.id }}")
                    }

                    return BuildResult(
                        sha1 = hash,
                        size = size
                    )
                }
            }
        }
    }

    /**
     * ★【今回追加】パックをビルド（更新）し、特定のプレイヤーにのみ送信・適用する。
     */
    fun buildAndApplyTo(player: Player): Boolean {
        plugin.logger.info("プレイヤー ${player.name} 用にリソースパックの更新ビルドと適用を開始します...")

        // 1. リソースパックを再ビルド
        val result = try {
            build()
        } catch (e: Exception) {
            plugin.logger.severe("リソースパックのビルド中にエラーが発生しました: ${e.message}")
            player.sendMessage("§c[ResourcePackFlow] リソースパックの更新ビルドに失敗しました。")
            return false
        }

        // 2. 指定プレイヤーへ適用
        val success = applyTo(player)
        if (success) {
            plugin.logger.info("プレイヤー ${player.name} へ更新後のリソースパック (SHA-1: ${result.sha1}) を適用しました。")
        }
        return success
    }

    /**
     * ★【便利メソッド】パックをビルド（更新）し、現在オンラインの全プレイヤーに一括適用する。
     */
    fun buildAndApplyToAll(): Map<String, Boolean> {
        plugin.logger.info("全オンラインプレイヤー用にリソースパックの更新ビルドと適用を開始します...")

        val result = try {
            build()
        } catch (e: Exception) {
            plugin.logger.severe("リソースパックのビルド中にエラーが発生しました: ${e.message}")
            return emptyMap()
        }

        val resultMap = mutableMapOf<String, Boolean>()
        for (player in Bukkit.getOnlinePlayers()) {
            val success = applyTo(player)
            resultMap[player.name] = success
        }

        plugin.logger.info("全プレイヤーへのリソースパック更新送信が完了しました。(対象: ${resultMap.size}人)")
        return resultMap
    }

    /**
     * 現在のpack.zipのSHA-1を取得する。
     */
    fun getCurrentPackHash(): String? {
        val packFile = File(plugin.dataFolder, "pack.zip")

        if (!packFile.exists() || !packFile.isFile) {
            return null
        }

        return try {
            PackHasher.sha1Hex(packFile)
        } catch (e: Exception) {
            plugin.logger.warning("Resource pack SHA-1の取得に失敗しました: ${e.message}")
            null
        }
    }

    /**
     * プレイヤーへリソースパックを送信する。
     *
     * 本体 pack.zip と、そのプレイヤーにとってONの追加パックを
     * それぞれ別のリソースパックとして重ねて送る。
     * 実際の送信は SHA-1 計算後にメインスレッドで行うため、少し遅れて届く。
     */
    fun applyTo(player: Player): Boolean {
        val packFile = File(plugin.dataFolder, "pack.zip")

        if (!packFile.exists() || !packFile.isFile || packFile.length() <= 0L) {
            player.sendMessage("§c[ResourcePackFlow] リソースパックがまだビルドされていません。")
            plugin.logger.warning("リソースパックを送信できません: pack.zip が存在しないか空です。")
            return false
        }

        if (!plugin.packHttpServer.ensureRunning()) {
            player.sendMessage("§c[ResourcePackFlow] リソースパック配信サーバーを起動できませんでした。")
            return false
        }

        val baseUrl = plugin.configManager.selectPackUrl(player)
        if (baseUrl.isNullOrBlank()) {
            player.sendMessage("§c[ResourcePackFlow] リソースパック配信用URLを取得できませんでした。")
            return false
        }
        val root = baseUrl.trimEnd('/')

        val allAddons = addonPacks.listAddons()
        val included = allAddons.filter { addonPacks.isEnabled(player.uniqueId, it.id) }

        // 送るパック: 追加パック → 本体 の順。
        // 後から送ったパックほど上に重なるため、本体を最後にして本体の内容を優先させる。
        val packs = included.map { PackToSend(it.packUuid, "$root/addons/${it.id}.zip", it.file, it.id) } +
                PackToSend(AddonPackManager.BASE_PACK_UUID, "$root/pack.zip", packFile, "本体")

        // 取り外す候補: このプラグインが送り得る全パック + 前回送ったパック
        // (重ね順をそろえるため、送るパックも一度外してから送り直す。
        //  SHA-1 が同じならクライアントはキャッシュを使うので再ダウンロードはされない)
        val toRemove = buildSet {
            add(AddonPackManager.BASE_PACK_UUID)
            allAddons.forEach { add(it.packUuid) }
            sentPacks[player.uniqueId]?.let { addAll(it) }
        }

        // SHA-1 の計算はファイルが大きいと重いので非同期で行い、送信はメインスレッドで行う
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val hashes = packs.associate { pack ->
                pack.uuid to runCatching { hexToBytes(addonPacks.sha1Of(pack.file)) }
                    .onFailure { plugin.logger.warning("SHA-1の計算に失敗したため、ハッシュなしで送信します (${pack.label}): ${it.message}") }
                    .getOrNull()
            }

            plugin.server.scheduler.runTask(plugin, Runnable {
                if (!player.isOnline) return@Runnable
                sendPacks(player, packs, hashes, toRemove)
            })
        })

        return true
    }

    private data class PackToSend(
        val uuid: UUID,
        val url: String,
        val file: File,
        val label: String
    )

    /** プレイヤーごとに、前回送ったパックID（含めない設定にしたパックを外すため） */
    private val sentPacks = ConcurrentHashMap<UUID, Set<UUID>>()

    private fun sendPacks(
        player: Player,
        packs: List<PackToSend>,
        hashes: Map<UUID, ByteArray?>,
        toRemove: Set<UUID>
    ) {
        toRemove.forEach { player.removeResourcePack(it) }

        for (pack in packs) {
            plugin.logger.info("Resource pack URL for ${player.name} (${pack.label}): ${pack.url}")
            player.addResourcePack(pack.uuid, pack.url, hashes[pack.uuid], null, false)
        }

        sentPacks[player.uniqueId] = packs.map { it.uuid }.toSet()

        val addonCount = packs.size - 1
        val message = if (addonCount > 0) {
            "§e[リソースパック] リソースパックを送信しました。(追加パック $addonCount 件)"
        } else {
            "§e[リソースパック] リソースパックを送信しました。"
        }

        player.spigot().sendMessage(
            net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
            *net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message)
        )
    }

    /** ログアウトしたプレイヤーの記録を消す */
    fun forget(playerUuid: UUID) {
        sentPacks.remove(playerUuid)
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

data class BuildResult(
    val sha1: String,
    val size: Long
)