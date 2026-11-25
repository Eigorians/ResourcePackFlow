package com.eastcompany.eastsub.gacha.listener

import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager
import com.eastcompany.eastsub.gacha.item.GachaItemBuilder
import com.eastcompany.eastsub.gacha.item.GachaItemChecker
import com.eastcompany.eastsub.gacha.item.ItemFilter
import com.eastcompany.eastsub.gacha.model.ModelConfigManager
import com.eastcompany.eastsub.gacha.model.RegisteredModelData
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import java.util.*

class PackOpenListener(
    private val modelConfigManager: ModelConfigManager,
    private val sessionManager: ResourcePackSessionManager
) : Listener {

    // メインハンド側で処理した直後に来るオフハンドのイベントを無視するため
    private val handledAt = HashMap<UUID, Long>()


    /**
     * gachaKey からモデルデータを検索する。
     * - 登録済み全ユーザー（オフライン含む）+ 現在オンラインのプレイヤーを作者候補にする
     * - 比較は GachaItemBuilder.gachaKey() に統一（"gacha:" 有無のずれ対策）
     */
    private fun findModelData(gachaKey: String): RegisteredModelData? {
        val authorNames = LinkedHashMap<String, String>() // lowercase -> 元の名前
        sessionManager.loadAllUsers().forEach { user ->
            if (user.name.isNotBlank()) authorNames.putIfAbsent(user.name.lowercase(), user.name)
        }
        Bukkit.getOnlinePlayers().forEach { p ->
            authorNames.putIfAbsent(p.name.lowercase(), p.name)
        }

        return authorNames.values
            .asSequence()
            .flatMap { name ->
                modelConfigManager.loadModelsByAuthor(name).values.asSequence()
            }
            .firstOrNull { model ->
                GachaItemBuilder.gachaKey(model) == gachaKey
            }
    }

    /** オフハンドのパックで、メインハンドのアイテムにモデルを適用する */
    private fun applyFromOffhand(player: Player, packItem: ItemStack) {
        val fullGachaKey = GachaItemChecker.getGachaKey(packItem) ?: return
        val modelData = findModelData(fullGachaKey)

        if (modelData == null) {
            Bukkit.getLogger().warning("[Gacha] model not found: key=$fullGachaKey")
            player.sendMessage("§c[Gacha] 該当するモデルデータが見つかりませんでした。")
            return
        }

        val mainHandItem = player.inventory.itemInMainHand

        if (mainHandItem.type.isAir) {
            player.sendMessage("§c適用するアイテムをメインハンドに持ってください。")
            return
        }
        if (ItemFilter.isBlocked(mainHandItem.type)) {
            player.sendMessage("§cその種類のアイテムにはモデルを適用できません。")
            return
        }
        if (GachaItemChecker.isPackItem(mainHandItem)) {
            player.sendMessage("§cパックにはモデルを適用できません。")
            return
        }

        // メインハンドのアイテムを1個だけ取り出して適用
        val singleItem = mainHandItem.clone()
        singleItem.amount = 1

        if (!GachaItemBuilder.applyModelToItem(player, singleItem, modelData)) return

        // 元のスタックから1個減らす
        if (mainHandItem.amount > 1) {
            mainHandItem.amount -= 1
        } else {
            player.inventory.setItemInMainHand(null)
        }

        // 適用済みの1個をインベントリに戻す（入らなければドロップ）
        player.inventory.addItem(singleItem).values.forEach {
            player.world.dropItemNaturally(player.location, it)
        }

        // パックを1個消費
        packItem.amount -= 1

        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
    }

    private companion object {
        const val DUPLICATE_WINDOW_MS = 200L
        const val LINE = "§8§m"
    }

    @EventHandler
    fun onPlayerRightClickPack(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player

        // 1. オフハンドのパックを優先（メインハンドのヘルメット等が先に装備されるのを防ぐ）
        if (tryApplyOffhandPriority(event, player)) return

        // 2. 1で処理済みなら、直後に来るオフハンドのイベントは無視
        if (isDuplicateOffhandEvent(event, player)) return

        // 3. メインハンドでブロックを右クリックした場合は対象外
        if (event.hand == EquipmentSlot.HAND && event.action == Action.RIGHT_CLICK_BLOCK) return

        // 4. パックアイテム（gacha:pack_x）以外は処理しない
        val item = event.item ?: return
        if (!GachaItemChecker.isPackItem(item)) return

        event.isCancelled = true

        if (event.hand == EquipmentSlot.OFF_HAND) {
            applyFromOffhand(player, item)      // パターン②: メインハンドのアイテムに着せる
            player.sendMessage("/gacha resetで戻せます")
        } else {
            showPackInfo(player, item)          // パターン①: 名前・説明・レア度を表示
        }
    }

    /** オフハンドにパック、メインハンドに別アイテムを持っている場合、パックを優先して適用する */
    private fun tryApplyOffhandPriority(event: PlayerInteractEvent, player: Player): Boolean {
        if (event.hand != EquipmentSlot.HAND) return false

        val offItem = player.inventory.itemInOffHand
        val mainItem = event.item

        val shouldApply = GachaItemChecker.isPackItem(offItem) &&
                mainItem != null &&
                !GachaItemChecker.isPackItem(mainItem)
        if (!shouldApply) return false

        event.isCancelled = true
        handledAt[player.uniqueId] = System.currentTimeMillis()
        applyFromOffhand(player, offItem)
        return true
    }

    /** 直前に処理済みのオフハンドイベントならキャンセルして true を返す */
    private fun isDuplicateOffhandEvent(event: PlayerInteractEvent, player: Player): Boolean {
        if (event.hand != EquipmentSlot.OFF_HAND) return false

        val last = handledAt[player.uniqueId] ?: return false
        if (System.currentTimeMillis() - last >= DUPLICATE_WINDOW_MS) return false

        event.isCancelled = true
        return true
    }

    private fun showPackInfo(player: Player, item: ItemStack) {
        val fullGachaKey = GachaItemChecker.getGachaKey(item) ?: return
        val modelData = findModelData(fullGachaKey)

        if (modelData == null) {
            Bukkit.getLogger().warning("[Gacha] model not found: key=$fullGachaKey")
            player.sendMessage("§c[Gacha] 該当するモデルデータが見つかりませんでした。")
            return
        }

        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.8f, 1.2f)

        player.sendMessage(LINE)
        player.sendMessage("§6§l ▶ モデル情報")
        player.sendMessage("")
        player.sendMessage(" §e名前　§8» §f${modelData.itemName}")
        player.sendMessage(" §eレア度 §8» ${modelData.rarity.color}§l${modelData.rarity.displayName}")
        sendApplyRestriction(player, modelData)
        sendRecommendItem(player, modelData)
        player.sendMessage("")
        sendDescription(player, modelData.description)
        player.sendMessage("")
        player.spigot().sendMessage(createHowToUseButton())
        player.sendMessage(LINE)
    }

    private fun sendDescription(player: Player, description: String?) {
        player.sendMessage(" §e説明")

        if (description.isNullOrBlank()) {
            return
        }

        description.split("\n").forEach { line ->
            player.sendMessage("   §8│ §f$line")
        }
    }

    private fun sendApplyRestriction(player: Player, modelData: RegisteredModelData) {
        if (modelData.slotType != "custom_model_data") return

        val baseItem = modelData.baseItem ?: return
        val material = Material.getMaterial(baseItem.uppercase()) ?: return

        player.sendMessage(
            Component.text(" 適用先　", NamedTextColor.YELLOW)
                .append(Component.text("» ", NamedTextColor.DARK_GRAY))
                .append(Component.translatable(material.translationKey()).color(NamedTextColor.RED))
                .append(Component.text(" のみ", NamedTextColor.RED))
        )
    }

    private fun sendRecommendItem(player: Player, modelData: RegisteredModelData) {
        val recommendedItem = modelData.recommendedItem ?: return
        val material = Material.getMaterial(recommendedItem.uppercase()) ?: return

        player.sendMessage(
            Component.text(" 推奨アイテム　", NamedTextColor.YELLOW)
                .append(Component.text("» ", NamedTextColor.DARK_GRAY))
                .append(Component.translatable(material.translationKey()).color(NamedTextColor.RED))
                .append(Component.text("", NamedTextColor.RED))
        )
    }

    private fun createHowToUseButton(): TextComponent =
        TextComponent(" ${ChatColor.GREEN}[パックの使用法を見る (Click)]").apply {
            hoverEvent = HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                Text(arrayOf(TextComponent("§7クリックで使用方法を表示")))
            )
            clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gacha howtouse")
        }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        handledAt.remove(event.player.uniqueId)
    }
}