package com.eastcompany.eastsub.gacha.stand

import com.eastcompany.eastsub.gacha.item.GachaItemBuilder
import com.eastcompany.eastsub.gacha.item.ItemFilter
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 抽選結果に応じた景品の付与・メッセージ・サウンドを担当する。
 */
class GachaRewardGiver {

    fun give(player: Player?, result: DrawResult) {
        if (player == null || !player.isOnline) return

        when (result) {
            is DrawResult.Win -> {
                player.sendMessage("§a[Gacha] §f${result.model.itemName} §aを獲得しました！")
                if (result.model.dropAsItem && ItemFilter.canDropAsItem(result.model)) {
                    giveItem(player, GachaItemBuilder.createModelItem(result.model, 1))
                }else{
                    giveItem(player, GachaItemBuilder.createPackItem(result.model, 1))
                    player.sendMessage("§a[Gacha] 使用方法はアイテムを    右クリックで説明を確認してください")
                }
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f)
            }

            DrawResult.Miss, DrawResult.NoModels -> {
                player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 0.5f, 1.0f)
            }
        }
    }

    /** インベントリに入り切らない分は足元にドロップする */
    private fun giveItem(player: Player, item: ItemStack) {
        val leftover = player.inventory.addItem(item)
        if (leftover.isEmpty()) return

        val loc = player.location
        leftover.values.forEach { loc.world?.dropItem(loc, it) }
        player.sendMessage("§e[Gacha] インベントリがいっぱいの為、アイテムが足元にドロップしました。")
    }
}