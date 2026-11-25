package com.eastcompany.eastsub.resourcePackFlow.command

import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import com.eastcompany.eastsub.resourcePackFlow.gui.AddonIcon
import com.eastcompany.eastsub.resourcePackFlow.pack.AddonPackManager
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID

/** /resourcepack addon ... : 追加パックの表示情報と初期状態の設定 */
class AddonCommand(
    private val plugin: ResourcePackFlow
) {

    companion object {
        const val PERMISSION = "resourcepackflow.addon"

        private const val RESET = "reset"
        private val ACTIONS = listOf("list", "info", "uuid", "name", "icon", "default", "description")
    }

    private val materialNames: List<String> by lazy {
        Material.values()
            .filter { !it.name.startsWith("LEGACY_") && it.isItem && !it.isAir }
            .map { it.name.lowercase() }
    }

    fun execute(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission(PERMISSION)) {
            error(sender, "このコマンドを実行する権限がありません。")
            return
        }

        val action = args.getOrNull(1)?.lowercase()

        if (action == "list") {
            sendList(sender)
            return
        }

        val id = args.getOrNull(2)

        // IDを省略すると本体も含めた全パック
        if (action == "uuid" && id == null) {
            sendAllUuids(sender)
            return
        }

        if (action == null || action !in ACTIONS || id == null) {
            sendUsage(sender)
            return
        }

        val addon = plugin.resourcePackManager.addonPacks.findAddon(id)
        if (addon == null) {
            error(sender, "アドオン '$id' が見つかりません。/resourcepack addon list で確認できます。")
            return
        }

        if (action == "uuid") {
            sendUuid(sender, addon.id, addon.packUuid)
            return
        }

        if (action == "info") {
            sendInfo(sender, id)
            return
        }

        val value = args.drop(3).joinToString(" ").trim()
        if (value.isEmpty()) {
            sendUsage(sender)
            return
        }

        val isReset = value.equals(RESET, ignoreCase = true)
        val settings = plugin.addonSettingsManager

        val saved = when (action) {
            "name" -> settings.setName(id, if (isReset) null else value)

            "description" -> settings.setDescription(
                id,
                if (isReset) null else value.split('|').map { it.trim() }
            )

            "default" -> {
                val enabled = when (value.lowercase()) {
                    "on", "true" -> true
                    "off", "false", RESET -> false
                    else -> {
                        error(sender, "on または off を指定してください。")
                        return
                    }
                }
                settings.setDefaultEnabled(id, enabled)
            }

            "icon" -> {
                val icon = if (isReset) null else AddonIcon.parse(value)

                if (!isReset && icon == null) {
                    error(sender, "アイコンにはマテリアル名 (例: jukebox) か item_model (例: eastcompany:bell) を指定してください。")
                    return
                }

                if (icon is AddonIcon.OfModel) warnIfModelMissing(sender, icon)
                settings.setIcon(id, if (isReset) null else value)
            }

            else -> return
        }

        if (!saved) {
            error(sender, "addons.yml を保存できませんでした。書式が壊れていないかサーバーログを確認してください。")
            return
        }

        sender.sendMessage("${ChatColor.GREEN}[ResourcePackFlow] アドオン $id の設定を保存しました。")
        sendInfo(sender, id)
    }

    /** GUIはアドオンOFFの人も見るため、モデルは本体パックに必要 */
    private fun warnIfModelMissing(sender: CommandSender, icon: AddonIcon.OfModel) {
        val namespace = icon.key.namespace
        if (namespace == "minecraft") return

        val file = File(
            plugin.dataFolder,
            "${plugin.configManager.resourcePackDirectory}/assets/$namespace/items/${icon.key.key}.json"
        )

        if (!file.isFile) {
            sender.sendMessage(
                "${ChatColor.YELLOW}注意: 本体パックに ${icon.key} がありません。" +
                        "アドオン内のモデルだと、そのアドオンがOFFの人には表示されません。"
            )
        }
    }

    private fun sendList(sender: CommandSender) {
        val addons = plugin.resourcePackManager.addonPacks.listAddons()

        if (addons.isEmpty()) {
            sender.sendMessage(
                "${ChatColor.GRAY}アドオンがありません。" +
                        "plugins/${plugin.name}/${plugin.configManager.addonsDirectory}/ にzipを入れてください。"
            )
            return
        }

        sender.sendMessage("${ChatColor.GOLD}===== アドオン一覧 =====")

        for (addon in addons) {
            val info = plugin.addonSettingsManager.get(addon.id)
            sender.sendMessage(
                "${ChatColor.YELLOW}${addon.id}${ChatColor.GRAY} - ${ChatColor.WHITE}${colorize(info.name)}" +
                        "${ChatColor.GRAY} (初期状態: ${onOff(info.defaultEnabled)})"
            )
        }
    }

    private fun sendAllUuids(sender: CommandSender) {
        sender.sendMessage("${ChatColor.GOLD}===== パックUUID =====")
        sendUuid(sender, "(本体)", AddonPackManager.BASE_PACK_UUID)
        plugin.resourcePackManager.addonPacks.listAddons().forEach { sendUuid(sender, it.id, it.packUuid) }
    }

    /** プレイヤーにはクリックでコピーできる形、コンソールには文字列で表示する */
    private fun sendUuid(sender: CommandSender, label: String, uuid: UUID) {
        val text = "${ChatColor.YELLOW}$label${ChatColor.GRAY}: ${ChatColor.WHITE}$uuid"

        if (sender !is Player) {
            sender.sendMessage(text)
            return
        }

        val component = TextComponent(*TextComponent.fromLegacyText(text)).apply {
            clickEvent = ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, uuid.toString())
            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text("クリックでUUIDをコピー"))
        }
        sender.spigot().sendMessage(component)
    }

    private fun sendInfo(sender: CommandSender, id: String) {
        val info = plugin.addonSettingsManager.get(id)
        val label = ChatColor.GRAY
        val value = ChatColor.WHITE

        sender.sendMessage("${ChatColor.GOLD}===== $id =====")
        sender.sendMessage("${label}名前: $value${colorize(info.name)}")
        sender.sendMessage("${label}アイコン: $value${info.icon ?: "未設定"}")
        sender.sendMessage("${label}初期状態: $value${onOff(info.defaultEnabled)}")
        sender.sendMessage("${label}詳細:${if (info.description.isEmpty()) " ${value}未設定" else ""}")
        info.description.forEach { sender.sendMessage("  $value${colorize(it)}") }
    }

    private fun sendUsage(sender: CommandSender) {
        listOf(
            "list" to "一覧",
            "info <ID>" to "設定の確認",
            "uuid [ID]" to "UUIDの確認 (ID省略で全パック)",
            "name <ID> <名前|reset>" to "表示名",
            "icon <ID> <マテリアル|item_model|reset>" to "アイコン",
            "default <ID> <on|off>" to "初期状態",
            "description <ID> <説明|reset>" to "詳細 (説明中の | で改行)"
        ).forEach { (usage, summary) ->
            sender.sendMessage("${ChatColor.YELLOW}/resourcepack addon $usage${ChatColor.GRAY} - $summary")
        }
    }

    private fun onOff(value: Boolean) = if (value) "ON" else "OFF"

    private fun colorize(text: String) = ChatColor.translateAlternateColorCodes('&', text)

    private fun error(sender: CommandSender, message: String) {
        sender.sendMessage("${ChatColor.RED}$message")
    }

    fun complete(sender: CommandSender, args: Array<out String>): List<String> {
        if (!sender.hasPermission(PERMISSION)) return emptyList()

        val action = args.getOrNull(1)?.lowercase()

        val candidates = when {
            args.size == 2 -> ACTIONS

            args.size == 3 && action != "list" && action in ACTIONS ->
                plugin.resourcePackManager.addonPacks.listAddons().map { it.id }

            args.size == 4 -> when (action) {
                "name", "description" -> listOf(RESET)
                "default" -> listOf("on", "off")
                "icon" -> listOf(RESET) + materialNames
                else -> emptyList()
            }

            else -> emptyList()
        }

        return candidates.filter { it.startsWith(args.last(), ignoreCase = true) }
    }
}
