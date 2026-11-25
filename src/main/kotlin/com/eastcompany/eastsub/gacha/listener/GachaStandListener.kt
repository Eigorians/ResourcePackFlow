package com.eastcompany.eastsub.gacha.listener

import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager
import com.eastcompany.eastsub.gacha.stand.GachaModelListGui
import com.eastcompany.eastsub.gacha.stand.GachaModelListHolder
import com.eastcompany.eastsub.gacha.stand.GachaStandController
import com.eastcompany.eastsub.gacha.stand.GachaStandKeys
import com.eastcompany.eastsub.gacha.stand.GachaStandManager
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.entity.Display
import org.bukkit.entity.Interaction
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import java.util.*

class GachaStandListener(private val sessionManager: ResourcePackSessionManager) : Listener {

    @EventHandler
    fun onChildEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val entity = event.entity
        val pdc = entity.persistentDataContainer

        val isParent = pdc.has(GachaStandManager.KEY_IS_STAND_PARENT, PersistentDataType.BYTE)
        val isChild = pdc.has(GachaStandManager.KEY_PARENT_TEXT_UUID, PersistentDataType.STRING)

        if (!isParent && !isChild) return

        val damager = event.damager

        // 1. クリエイティブモードのプレイヤーによる攻撃のときは解体する
        // クリエイティブ、または個人スタンドの所有者なら解体
        if (damager is Player &&
            (damager.gameMode == GameMode.CREATIVE || canRemoveStand(damager, entity))
        ) {
            event.isCancelled = true

            if (isParent) {
                GachaStandManager.removeGachaStand(entity)
            } else if (isChild) {
                GachaStandManager.removeGachaStandFromChildEntity(entity)
            }

            notifyRemoval(damager)
            return
        }

        event.isCancelled = true

        event.isCancelled = true
    }

    /**
     * プレイヤーのパンチ（Interaction・無敵のマネキンでも発火する）
     */
    @EventHandler
    fun onPlayerAttackStandPart(event: PrePlayerAttackEntityEvent) {
        val entity = event.attacked
        val pdc = entity.persistentDataContainer

        val isParent = pdc.has(GachaStandManager.KEY_IS_STAND_PARENT, PersistentDataType.BYTE)
        val isChild = pdc.has(GachaStandManager.KEY_PARENT_TEXT_UUID, PersistentDataType.STRING)
        if (!isParent && !isChild) return

        event.isCancelled = true   // スタンドを壊させない
        val player = event.player

        // 1. クリエイティブは解体
        if (player.gameMode == GameMode.CREATIVE || canRemoveStand(player, entity)) {
            if (isParent) {
                GachaStandManager.removeGachaStand(entity)
            } else {
                GachaStandManager.removeGachaStandFromChildEntity(entity)
            }

            notifyRemoval(player)
            return
        }

        // 2. サバイバル等はマネキン・説明ラベルで操作説明を表示
        val standIndex = pdc.get(GachaStandManager.KEY_STAND_INDEX, PersistentDataType.INTEGER) ?: 0
        if (standIndex == GachaStandManager.INDEX_MANNEQUIN ||
            standIndex == GachaStandManager.INDEX_MANNEQUIN_TEXT) {
            sendHowToPlay(player)
        }
    }

    private fun sendHowToPlay(player: Player) {
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.6f, 1.2f)

        player.sendMessage("§8§m                                        ")
        player.sendMessage("§f[§eガチャの遊び方 & 操作説明§f]")
        player.sendMessage("")

        // ── ガチャ ──
        player.sendMessage("§6■ ガチャ")
        player.sendMessage("§f・§b浮いているアイテム§fを右クリックで、次のアイテムを見れます")
        player.sendMessage("§f・§bマネキン§fをShift+右クリックで、モデルの一覧を確認できます")
        player.sendMessage("")

        // ── パックの使い方 ──
        player.sendMessage("§6■ パックの使い方")
        player.sendMessage("§f・§bメインハンド§fにパックを持って右クリック")
        player.sendMessage("  §7→ モデルの名前・レア度・説明を確認できます")
        player.sendMessage("§f・§bオフハンド§fにパック、§bメインハンド§fに着せたいアイテムを持って右クリック")
        player.sendMessage("  §7→ アイテムにモデルが適用されます(パックは1個消費)")
        player.sendMessage("")
        player.sendMessage("§e§l! §e注意")
        player.sendMessage("§f・§7すでにモデルが適用されたアイテムには重ねて着せられません")
        player.sendMessage("§f・§7一部のモデルは、対応するアイテムにのみ適用できます")
        player.sendMessage("§f・§7スタックしている場合は、1個だけに適用されます")
        player.sendMessage("")

        // ── 見えない場合 ──
        player.sendMessage("§c■ テクスチャが見えない場合")
        player.sendMessage("§f・サーバーリストからリソースパックを有効にしてください")
        player.sendMessage("")

        // ── 推奨 ──
        player.sendMessage("§a■ 推奨設定")
        player.sendMessage("§f・§b/resourcepack setting§f : ログイン時 リソースパックを自動適用")
        player.sendMessage("§f・§b/resourcepack apply§f : リソースパックを今すぐ適用")
        player.sendMessage("§8§m                                        ")
    }

    @EventHandler
    fun onArmorStandManipulate(event: PlayerArmorStandManipulateEvent) {
        val pdc = event.rightClicked.persistentDataContainer
        if (pdc.has(GachaStandManager.KEY_PARENT_TEXT_UUID, PersistentDataType.STRING)) {
            event.isCancelled = true
        }
    }

    /**
     * 右クリック検知：各ボタン・マネキン・プレビューアイテムの操作分岐
     */
    @EventHandler
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) return

        val clickedEntity = event.rightClicked
        val pdc = clickedEntity.persistentDataContainer

        if (!pdc.has(GachaStandManager.KEY_PARENT_TEXT_UUID, PersistentDataType.STRING)) return

        val parentUuidStr = pdc.get(GachaStandManager.KEY_PARENT_TEXT_UUID, PersistentDataType.STRING) ?: return
        val standIndex = pdc.get(GachaStandManager.KEY_STAND_INDEX, PersistentDataType.INTEGER) ?: 0

        // スタンドの構成エンティティの場合は右クリックをキャンセル
        if (clickedEntity is Interaction || clickedEntity is Mannequin || clickedEntity is Display) {
            event.isCancelled = true
        } else {
            return
        }

        val player = event.player
        val controller = GachaStandController(UUID.fromString(parentUuidStr))

        when (standIndex) {
            GachaStandManager.INDEX_EXECUTE_BUTTON -> {
                controller.toggleOrStartGacha(player)
            }
            GachaStandManager.INDEX_MANNEQUIN,
            GachaStandManager.INDEX_MANNEQUIN_TEXT -> {
                // Shift+右クリック: モデル一覧 / 通常の右クリック: 作者切り替え
                if (player.isSneaking) {
                    controller.openModelList(player)
                } else {
                    controller.switchToNextAuthor(player, sessionManager)
                }
            }
            GachaStandManager.INDEX_PREVIEW_ITEM -> {
                controller.switchToNextModel(player)
            }
            // 4. 情報板クリック ➔ 権限があれば /gacha login を実行
            GachaStandKeys.INDEX_INFO_BOARD -> {
                controller.onInfoBoardClick(player)
            }
        }

    }


    /** モデル一覧インベントリ: 取り出し・移動を全て禁止し、ページ送りだけ処理する */
    @EventHandler
    fun onModelListClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? GachaModelListHolder ?: return
        event.isCancelled = true

        // 自分のインベントリ側のクリックは何もしない
        if (event.clickedInventory != event.view.topInventory) return

        val player = event.whoClicked as? Player ?: return
        GachaModelListGui.onClick(player, holder, event.rawSlot)
    }

    @EventHandler
    fun onModelListDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is GachaModelListHolder) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerInteractAtEntity(event: PlayerInteractAtEntityEvent) {
        val pdc = event.rightClicked.persistentDataContainer
        if (pdc.has(GachaStandManager.KEY_PARENT_TEXT_UUID, PersistentDataType.STRING)) {
            event.isCancelled = true
        }
    }

    private fun notifyRemoval(damager: org.bukkit.entity.Entity?) {
        if (damager is Player) {
            damager.playSound(damager.location, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.0f)
            damager.sendMessage("§a[GachaStand] ガチャスタンドを解体しました。")
        }
    }

    private fun canRemoveStand(player: Player, entity: org.bukkit.entity.Entity): Boolean {
        val pdc = if (entity.persistentDataContainer.has(
                GachaStandKeys.IS_STAND_PARENT,
                PersistentDataType.BYTE
            )
        ) {
            entity.persistentDataContainer
        } else {
            val parentUuid = entity.persistentDataContainer.get(
                GachaStandKeys.PARENT_TEXT_UUID,
                PersistentDataType.STRING
            )?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            } ?: return false

            org.bukkit.Bukkit.getEntity(parentUuid)?.persistentDataContainer
                ?: return false
        }

        val isPersonal = pdc.get(
            GachaStandKeys.IS_PERSONAL_STAND,
            PersistentDataType.BYTE
        ) == GachaStandKeys.FLAG_ON

        if (!isPersonal) return false

        val ownerUuid = pdc.get(
            GachaStandKeys.CURRENT_AUTHOR_UUID,
            PersistentDataType.STRING
        ) ?: return false

        return ownerUuid.equals(player.uniqueId.toString(), ignoreCase = true)
    }
}