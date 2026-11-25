package com.eastcompany.eastsub.resourcePackFlow.command

import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.io.File
import java.net.URI

/** /resourcepack download : 本体と全アドオンのダウンロードリンクを候補として表示 */
class DownloadCommand(
    private val plugin: ResourcePackFlow
) {

    fun execute(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}このコマンドはプレイヤーのみ実行できます。")
            return
        }

        val packFile = File(plugin.dataFolder, "pack.zip")
        if (!packFile.isFile || packFile.length() <= 0L) {
            sender.sendMessage("${ChatColor.RED}[ResourcePackFlow] リソースパックがまだビルドされていません。")
            return
        }

        if (!plugin.packHttpServer.ensureRunning()) {
            sender.sendMessage("${ChatColor.RED}[ResourcePackFlow] リソースパック配信サーバーを起動できませんでした。")
            return
        }

        // 適用時と同じく、LAN内の人にはLAN側のURLを渡す
        val root = plugin.configManager.selectPackUrl(sender)?.trimEnd('/')

        // http(s) 以外のURLをクリック先に入れるとクライアントがメッセージを読めない
        if (root == null || !isWebUrl(root)) {
            sender.sendMessage("${ChatColor.RED}[ResourcePackFlow] ダウンロード用URLを取得できませんでした。")
            return
        }

        sender.sendMessage("${ChatColor.GOLD}===== ダウンロードするパックを選択 =====")

        sendCandidate(
            player = sender,
            label = "${ChatColor.GREEN}[基本パック]",
            details = sizeText(packFile.length()),
            description = emptyList(),
            url = "$root/pack.zip"
        )

        val manager = plugin.resourcePackManager.addonPacks
        val addons = manager.listAddons()

        for (addon in addons) {
            val info = plugin.addonSettingsManager.get(addon.id)

            val state = if (manager.isEnabled(sender.uniqueId, addon.id)) {
                "${ChatColor.GREEN}ON"
            } else {
                "${ChatColor.DARK_GRAY}OFF"
            }

            sendCandidate(
                player = sender,
                label = "${ChatColor.AQUA}[${colorize(info.name)}${ChatColor.AQUA}]",
                details = "${sizeText(addon.size)} $state",
                description = info.description.map { colorize(it) },
                url = "$root/addons/${addon.id}.zip"
            )
        }

        if (addons.isNotEmpty()) {
            sender.sendMessage("${ChatColor.GRAY}※手動で導入する場合は、基本パックを一番上に置いてください。")
        }
    }

    /** 1候補につき1行。行のどこを押してもダウンロードURLを開く */
    private fun sendCandidate(
        player: Player,
        label: String,
        details: String,
        description: List<String>,
        url: String
    ) {
        val hover = (description.map { "${ChatColor.GRAY}$it" } + "${ChatColor.YELLOW}クリックでダウンロード")
            .joinToString("\n")

        val line = TextComponent(*TextComponent.fromLegacyText("$label $details")).apply {
            clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, url)
            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(TextComponent.fromLegacyText(hover)))
        }

        player.spigot().sendMessage(line)
    }

    private fun isWebUrl(url: String): Boolean {
        val scheme = runCatching { URI(url).scheme }.getOrNull()
        return scheme == "http" || scheme == "https"
    }

    private fun sizeText(bytes: Long) =
        "${ChatColor.GRAY}${String.format("%.1f MB", bytes / 1024.0 / 1024.0)}"

    private fun colorize(text: String) = ChatColor.translateAlternateColorCodes('&', text)
}
