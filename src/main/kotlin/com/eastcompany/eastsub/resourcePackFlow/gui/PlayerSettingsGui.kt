package com.eastcompany.eastsub.resourcePackFlow.gui

import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import com.eastcompany.eastsub.resourcePackFlow.command.DownloadCommand
import com.eastcompany.eastsub.resourcePackFlow.config.AutoApplyMode
import com.eastcompany.eastsub.resourcePackFlow.config.ResourcePackMode
import com.eastcompany.eastsub.resourcePackFlow.pack.AddonPack
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

/**
 * 個人設定GUI。
 * 上に「何の設定か」、その真下に「今の状態」を表示する。どちらを押しても動作は同じ。
 */
class PlayerSettingsGui(
    private val plugin: ResourcePackFlow
) : Listener {

    companion object {
        private const val MAIN_TITLE = "リソパ 設定"
        private const val ADDONS_TITLE = "リソパ 設定 > アドオン"

        private const val ADMIN_PERMISSION = "resourcepackflow.admin"

        private const val ROW = 9

        private const val MAIN_SIZE = 27
        private const val AUTO_APPLY_SLOT = 10
        private const val SERVER_MODE_SLOT = 12
        private const val ADDONS_SLOT = 14
        private const val DOWNLOAD_SLOT = 16

        private const val ADDONS_SIZE = 45

        /** アイコンを置く段の先頭スロット。その真下の段がON/OFF */
        private val ADDON_ICON_ROWS = listOf(0, 18)
        private val ADDONS_PER_PAGE = ADDON_ICON_ROWS.size * ROW

        private const val EMPTY_SLOT = 22
        private const val BACK_SLOT = 36
        private const val PREVIOUS_SLOT = 39
        private const val GUIDE_SLOT = 40
        private const val NEXT_SLOT = 41

        // ダブルクリックは通常クリック2回の後に追加で届くため含めない
        private val TOGGLE_CLICKS = setOf(
            ClickType.LEFT,
            ClickType.RIGHT,
            ClickType.SHIFT_LEFT,
            ClickType.SHIFT_RIGHT
        )
    }

    private val downloadCommand = DownloadCommand(plugin)

    fun open(player: Player) = open(player, SettingsScreen.MAIN)

    private fun open(player: Player, screen: SettingsScreen) {
        val holder = PlayerSettingsHolder(screen)

        val inventory = when (screen) {
            SettingsScreen.MAIN -> Bukkit.createInventory(holder, MAIN_SIZE, MAIN_TITLE)
            SettingsScreen.ADDONS -> Bukkit.createInventory(holder, ADDONS_SIZE, ADDONS_TITLE)
        }

        holder.setInventory(inventory)
        render(player, holder)
        player.openInventory(inventory)
    }

    private fun render(player: Player, holder: PlayerSettingsHolder) {
        holder.inventory.clear()

        when (holder.screen) {
            SettingsScreen.MAIN -> renderMain(player, holder)
            SettingsScreen.ADDONS -> renderAddons(player, holder)
        }
    }

    // =========================
    // Main
    // =========================

    private fun renderMain(player: Player, holder: PlayerSettingsHolder) {
        renderAutoApply(player, holder)

        // OPにのみサーバー設定を表示
        if (player.hasPermission(ADMIN_PERMISSION)) {
            renderServerMode(holder)
        }

        val addons = plugin.resourcePackManager.addonPacks
        val total = addons.listAddons().size
        val enabled = addons.enabledAddons(player.uniqueId).size

        holder.inventory.setItem(
            ADDONS_SLOT,
            item(
                Material.CHEST,
                "${ChatColor.AQUA}アドオン",
                "${ChatColor.GRAY}音声などの追加パックを選べます。",
                "",
                "${ChatColor.YELLOW}クリックで開く"
            )
        )
        holder.inventory.setItem(
            ADDONS_SLOT + ROW,
            item(
                if (enabled > 0) Material.LIME_DYE else Material.GRAY_DYE,
                "${ChatColor.WHITE}ON: $enabled / $total",
                "${ChatColor.YELLOW}クリックで開く"
            )
        )

        holder.inventory.setItem(
            DOWNLOAD_SLOT,
            item(
                Material.PAPER,
                "${ChatColor.LIGHT_PURPLE}ダウンロードURL",
                "${ChatColor.GRAY}基本パックとアドオンのリンクを",
                "${ChatColor.GRAY}チャットに表示します。",
                "",
                "${ChatColor.YELLOW}クリックで表示"
            )
        )
        holder.inventory.setItem(
            DOWNLOAD_SLOT + ROW,
            item(
                Material.MAP,
                "${ChatColor.WHITE}パック ${total + 1} 件",
                "${ChatColor.YELLOW}クリックで表示"
            )
        )
    }

    private fun renderAutoApply(player: Player, holder: PlayerSettingsHolder) {
        val mode = plugin.playerSettingsManager.getAutoApply(player.uniqueId)

        // toggleAutoApply で切り替わる順に並べる
        val options = linkedMapOf(
            AutoApplyMode.APPLY to "自動で適用",
            AutoApplyMode.OFF to "何もしない",
            AutoApplyMode.MESSAGE to "メッセージで案内"
        )

        val material = when (mode) {
            AutoApplyMode.APPLY -> Material.LIME_CONCRETE
            AutoApplyMode.MESSAGE -> Material.BELL
            AutoApplyMode.OFF -> Material.BARRIER
        }

        val lore = choices(options, mode)

        holder.inventory.setItem(
            AUTO_APPLY_SLOT,
            item(Material.OAK_DOOR, "${ChatColor.GREEN}ログインした時", *lore)
        )
        holder.inventory.setItem(
            AUTO_APPLY_SLOT + ROW,
            item(material, "${ChatColor.WHITE}${options[mode]}", *lore)
        )
    }

    private fun renderServerMode(holder: PlayerSettingsHolder) {
        val mode = plugin.configManager.resourcePackMode

        // toggleServerMode で切り替わる順に並べる
        val options = linkedMapOf(
            ResourcePackMode.FORCE to "全員に強制適用",
            ResourcePackMode.MESSAGE to "全員にメッセージで案内",
            ResourcePackMode.OFF to "各自の設定に任せる"
        )

        val material = when (mode) {
            ResourcePackMode.FORCE -> Material.REDSTONE_BLOCK
            ResourcePackMode.MESSAGE -> Material.BELL
            ResourcePackMode.OFF -> Material.PLAYER_HEAD
        }

        val lore = arrayOf(
            "${ChatColor.RED}OP専用。個人設定より優先されます。",
            "",
            *choices(options, mode)
        )

        holder.inventory.setItem(
            SERVER_MODE_SLOT,
            item(Material.COMMAND_BLOCK, "${ChatColor.GOLD}サーバー全体の設定", *lore)
        )
        holder.inventory.setItem(
            SERVER_MODE_SLOT + ROW,
            item(material, "${ChatColor.WHITE}${options[mode]}", *lore)
        )
    }

    /** 選択肢を全て並べ、今選ばれているものに印を付けた説明文 */
    private fun <T> choices(options: Map<T, String>, current: T): Array<String> {
        val lines = options.map { (value, label) ->
            if (value == current) "${ChatColor.GREEN}▶ $label" else "${ChatColor.DARK_GRAY}　$label"
        }

        return (lines + "" + "${ChatColor.YELLOW}クリックで切り替え").toTypedArray()
    }

    // =========================
    // Addons
    // =========================

    private fun renderAddons(player: Player, holder: PlayerSettingsHolder) {
        val inventory = holder.inventory
        val manager = plugin.resourcePackManager.addonPacks
        val addons = manager.listAddons()
        val pageCount = ((addons.size + ADDONS_PER_PAGE - 1) / ADDONS_PER_PAGE).coerceAtLeast(1)

        holder.page = holder.page.coerceIn(0, pageCount - 1)
        holder.addonSlots.clear()

        addons.drop(holder.page * ADDONS_PER_PAGE).take(ADDONS_PER_PAGE).forEachIndexed { index, addon ->
            val iconSlot = ADDON_ICON_ROWS[index / ROW] + index % ROW
            val enabled = manager.isEnabled(player.uniqueId, addon.id)

            inventory.setItem(iconSlot, createAddonIcon(addon, enabled))
            inventory.setItem(iconSlot + ROW, createAddonState(addon, enabled))

            holder.addonSlots[iconSlot] = addon.id
            holder.addonSlots[iconSlot + ROW] = addon.id
        }

        if (addons.isEmpty()) {
            inventory.setItem(
                EMPTY_SLOT,
                item(Material.STRUCTURE_VOID, "${ChatColor.GRAY}利用できるアドオンはありません")
            )
        }

        inventory.setItem(BACK_SLOT, item(Material.OAK_DOOR, "${ChatColor.YELLOW}設定に戻る"))

        if (holder.page > 0) {
            inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW, "${ChatColor.YELLOW}← 前のページ"))
        }

        inventory.setItem(
            GUIDE_SLOT,
            item(
                Material.BOOK,
                "${ChatColor.AQUA}使い方 (${holder.page + 1}/$pageCount ページ)",
                "${ChatColor.GRAY}クリックでON/OFFを切り替えます。",
                "${ChatColor.GRAY}変更は /resourcepack apply で反映されます。"
            )
        )

        if (holder.page < pageCount - 1) {
            inventory.setItem(NEXT_SLOT, item(Material.ARROW, "${ChatColor.YELLOW}次のページ →"))
        }
    }

    private fun createAddonIcon(addon: AddonPack, enabled: Boolean): ItemStack {
        val info = plugin.addonSettingsManager.get(addon.id)
        val stack = info.icon?.let { AddonIcon.parse(it) }?.createItem() ?: ItemStack(Material.BUNDLE)
        val sizeText = String.format("%.1f MB", addon.size / 1024.0 / 1024.0)

        val lore = buildList {
            info.description.forEach { add("${ChatColor.GRAY}${colorize(it)}") }
            if (info.description.isNotEmpty()) add("")

            add(stateText(enabled))
            add("${ChatColor.GRAY}サイズ: ${ChatColor.WHITE}$sizeText")
            add("")
            add("${ChatColor.YELLOW}クリックで切り替え")
        }

        return decorate(stack, "${ChatColor.WHITE}${colorize(info.name)}", lore)
    }

    private fun createAddonState(addon: AddonPack, enabled: Boolean): ItemStack =
        item(
            if (enabled) Material.LIME_DYE else Material.GRAY_DYE,
            stateText(enabled),
            "${ChatColor.GRAY}${colorize(plugin.addonSettingsManager.get(addon.id).name)}",
            "${ChatColor.YELLOW}クリックで切り替え"
        )

    private fun stateText(enabled: Boolean) =
        if (enabled) "${ChatColor.GREEN}ON" else "${ChatColor.RED}OFF"

    private fun toggleAddon(player: Player, id: String) {
        val addons = plugin.resourcePackManager.addonPacks

        // 表示した後にzipが削除されている
        if (addons.findAddon(id) == null) return

        val next = !addons.isEnabled(player.uniqueId, id)
        plugin.playerSettingsManager.setAddonChoice(player.uniqueId, id, next)

        val name = colorize(plugin.addonSettingsManager.get(id).name)

        player.sendMessage(
            "${ChatColor.YELLOW}[ResourcePackFlow] アドオン「$name${ChatColor.YELLOW}」を" +
                    "${stateText(next)}${ChatColor.YELLOW}にしました。" +
                    "${ChatColor.GRAY} /resourcepack apply で反映されます。"
        )
    }

    // =========================
    // Item
    // =========================

    private fun item(material: Material, name: String, vararg lore: String): ItemStack =
        decorate(ItemStack(material), name, lore.toList())

    private fun decorate(stack: ItemStack, name: String, lines: List<String>): ItemStack =
        stack.apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(name)
                lore = lines
                addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            }
        }

    private fun colorize(text: String): String =
        ChatColor.translateAlternateColorCodes('&', text)

    // =========================
    // Click
    // =========================

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        // GUIにアイテムを入れられると、再描画で消えてしまう
        if (event.view.topInventory.holder is PlayerSettingsHolder) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {

        // 自分のGUIかHolderで判定
        val holder = event.view.topInventory.holder as? PlayerSettingsHolder ?: return

        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return

        // 下側のプレイヤーInventoryクリックは無視
        if (event.clickedInventory != event.view.topInventory) {
            return
        }

        if (event.click !in TOGGLE_CLICKS) {
            return
        }

        when (holder.screen) {
            SettingsScreen.MAIN -> clickMain(player, holder, event.rawSlot)
            SettingsScreen.ADDONS -> clickAddons(player, holder, event.rawSlot)
        }
    }

    private fun clickMain(player: Player, holder: PlayerSettingsHolder, slot: Int) {
        when (slot) {
            AUTO_APPLY_SLOT, AUTO_APPLY_SLOT + ROW -> toggleAutoApply(player)

            SERVER_MODE_SLOT, SERVER_MODE_SLOT + ROW -> {
                if (!player.hasPermission(ADMIN_PERMISSION)) return
                toggleServerMode()
            }

            ADDONS_SLOT, ADDONS_SLOT + ROW -> {
                switchScreen(player, holder, SettingsScreen.ADDONS)
                return
            }

            DOWNLOAD_SLOT, DOWNLOAD_SLOT + ROW -> {
                showDownloadLinks(player, holder)
                return
            }

            else -> return
        }

        // 切り替え後の状態を即座にGUIへ反映
        render(player, holder)
    }

    private fun clickAddons(player: Player, holder: PlayerSettingsHolder, slot: Int) {
        when (slot) {
            BACK_SLOT -> {
                switchScreen(player, holder, SettingsScreen.MAIN)
                return
            }

            // ページの範囲は renderAddons で丸める
            PREVIOUS_SLOT -> holder.page--
            NEXT_SLOT -> holder.page++

            else -> toggleAddon(player, holder.addonSlots[slot] ?: return)
        }

        render(player, holder)
    }

    /** リンクはチャットに出すため、GUIを閉じてから表示する（クリック中は閉じられないので次のtick） */
    private fun showDownloadLinks(player: Player, from: PlayerSettingsHolder) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!player.isOnline) return@Runnable

            if (player.openInventory.topInventory.holder === from) {
                player.closeInventory()
            }

            downloadCommand.execute(player)
        })
    }

    /** クリックイベント中は openInventory できないため、次のtickで開く */
    private fun switchScreen(player: Player, from: PlayerSettingsHolder, screen: SettingsScreen) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (player.isOnline && player.openInventory.topInventory.holder === from) {
                open(player, screen)
            }
        })
    }

    private fun toggleAutoApply(player: Player) {
        val manager = plugin.playerSettingsManager

        val current = manager.getAutoApply(
            player.uniqueId
        )

        val next = when (current) {
            AutoApplyMode.APPLY ->
                AutoApplyMode.OFF

            AutoApplyMode.OFF ->
                AutoApplyMode.MESSAGE

            AutoApplyMode.MESSAGE ->
                AutoApplyMode.APPLY
        }

        manager.setAutoApply(
            player.uniqueId,
            next
        )
    }

    private fun toggleServerMode() {
        val config = plugin.configManager

        val next = when (config.resourcePackMode) {
            ResourcePackMode.FORCE ->
                ResourcePackMode.MESSAGE

            ResourcePackMode.MESSAGE ->
                ResourcePackMode.OFF

            ResourcePackMode.OFF ->
                ResourcePackMode.FORCE
        }

        config.setResourcePackMode(next)
    }
}
