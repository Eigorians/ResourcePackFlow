package com.eastcompany.eastsub.gacha.item

import com.eastcompany.eastsub.gacha.model.ModelRarity
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object GachaItemChecker {

    private const val PACK_NAMESPACE = "gacha_system"

    /**
     * 対象のアイテムがガチャパックアイテム
     * (item_model = gacha_system:{key}_pack_{レア度}) かどうかを判定
     */
    fun isPackItem(item: ItemStack?): Boolean {
        if (item == null || !item.hasItemMeta()) return false
        val meta = item.itemMeta ?: return false

        // 1. PDC (gacha:key) が付与されているか確認
        if (!meta.persistentDataContainer.has(GachaItemBuilder.GACHA_PDC_KEY, PersistentDataType.STRING)) {
            return false
        }

        // 2. item_model が設定されているか確認
        if (!meta.hasItemModel()) return false
        val model = meta.itemModel ?: return false

        // 3. namespace が gacha_system で、名前が *_pack_{レア度} で終わるか検証
        if (model.namespace != PACK_NAMESPACE) return false
        return ModelRarity.entries.any { rarity ->
            val packName = rarity.packModelPath.substringAfter(":") // 例: pack_s
            model.key.endsWith("_$packName")
        }
    }

    /**
     * パックアイテムからモデルの識別キー (例: "gacha:steve/my_model") を取得
     */
    fun getGachaKey(item: ItemStack?): String? {
        if (item == null || !item.hasItemMeta()) return null
        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(GachaItemBuilder.GACHA_PDC_KEY, PersistentDataType.STRING)
    }
}