package com.eastcompany.eastsub.gacha.item

import com.eastcompany.eastsub.gacha.model.ModelRarity
import com.eastcompany.eastsub.gacha.model.RegisteredModelData
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType

object GachaItemBuilder {

    val GACHA_PDC_KEY = NamespacedKey("gacha", "key")
    val GACHA_BACKUP_PDC_KEY = NamespacedKey("gacha", "backup_meta")

    private val gson = Gson()

    // ------------------------------------------------------------------
    // キー・素材の解決
    // ------------------------------------------------------------------

    /** ベースアイテムの Material。MISS は強制的に STICK */
    private fun baseMaterial(data: RegisteredModelData): Material {
        data.recommendedItem
        val name = if (data.rarity == ModelRarity.MISS) {
            "STICK"
        } else {
            (data.baseItem?.ifBlank { "stick" } ?: "stick").uppercase()
        }
        return Material.getMaterial(name) ?: Material.STICK
    }

    private fun loadMaterial(data: RegisteredModelData): Material {

        val rawName = if (data.rarity == ModelRarity.MISS) {
            "STICK"
        } else {
            val base = data.baseItem
            if (base.isNullOrBlank() || base.equals("stick", ignoreCase = true)) {
                val recommended = data.recommendedItem ?: "stick"
                recommended
            } else {
                base
            }
        }

        val material = Material.getMaterial(rawName.uppercase()) ?: Material.STICK
        return material
    }

    private fun rawKey(data: RegisteredModelData): String = data.key ?: data.modelId

    /**
     * PDC・custom_model_data(strings) に入れるキー。
     * - 通常（アップロード）: 必ず "gacha:" 付き
     * - external（既存アイテム登録）: 識別コードをそのまま使う
     */
    fun gachaKey(data: RegisteredModelData): String {
        val raw = rawKey(data)
        if (data.external) return raw
        return "gacha:${raw.removePrefix("gacha:")}"
    }

    /** item_model に設定する NamespacedKey */
    fun itemModelKey(data: RegisteredModelData): NamespacedKey {
        val raw = rawKey(data)
        if (data.external) {
            return NamespacedKey.fromString(raw) ?: NamespacedKey("gacha", data.modelId)
        }
        return NamespacedKey("gacha", raw.removePrefix("gacha:"))
    }

    // ------------------------------------------------------------------
    // アイテム生成
    // ------------------------------------------------------------------

    fun createPackItem(data: RegisteredModelData, amount: Int = 1): ItemStack {
        val item = ItemStack(Material.STICK, amount)
        val meta = item.itemMeta ?: return item

        meta.setDisplayName("${ChatColor.WHITE}§l[${data.rarity.color}${data.rarity.displayName}${ChatColor.WHITE}] ${ChatColor.RESET}${data.itemName}")

        val loreList = mutableListOf<String>()
        if (!data.description.isNullOrBlank()) {
            data.description.split("\n").forEach { line ->
                loreList.add("§7$line")
            }
        }
        loreList.add("")
        loreList.add("§8PDC: ${packFileName(data.rarity, gachaKey(data))}")
        meta.lore = loreList

        meta.persistentDataContainer.set(GACHA_PDC_KEY, PersistentDataType.STRING, gachaKey(data))

        // パックの見た目は {key}_pack_{レア度}.json を item_model で指定
        meta.itemModel = packItemModelKey(data)

        item.itemMeta = meta
        return item
    }

    fun createModelItem(
        data: RegisteredModelData,
        amount: Int = 1
    ): ItemStack {
        val item = ItemStack(loadMaterial(data), amount)
        val meta = item.itemMeta ?: return item

        applyModelMeta(meta, data)

        item.itemMeta = meta
        return item
    }

    // ------------------------------------------------------------------
    // 既存アイテムへの適用 / 解除
    // ------------------------------------------------------------------

    fun applyModelToItem(
        player: Player,
        targetItem: ItemStack?,
        data: RegisteredModelData
    ): Boolean {
        if (targetItem == null || targetItem.type == Material.AIR) {
            player.sendMessage(
                "§c[Gacha] 適用対象のアイテムをメインハンドに持ってください。"
            )
            return false
        }

        val meta = targetItem.itemMeta ?: return false
        val pdc = meta.persistentDataContainer

        if (pdc.has(GACHA_PDC_KEY, PersistentDataType.STRING)) {
            player.sendMessage(
                "§c[Gacha] このアイテムには既にリソースパックが適用されています！"
            )
            return false
        }

        // custom_model_data の素材チェックは既存のまま残す
        if (data.slotType.equals("custom_model_data", ignoreCase = true)) {
            val requiredMaterial = baseMaterial(data)

            if (targetItem.type != requiredMaterial) {
                player.sendMessage(
                    Component.text("[Gacha] このモデルは「", NamedTextColor.RED)
                        .append(Component.translatable(requiredMaterial.translationKey()))
                        .append(Component.text("」にのみ適用可能です。（現在: "))
                        .append(Component.translatable(targetItem.type.translationKey()))
                        .append(Component.text("）"))
                )
                return false
            }
        }

        // 既存メタ情報のバックアップ
        val backupObj = JsonObject().apply {
            if (meta.hasDisplayName()) {
                addProperty("displayName", meta.displayName)
            }

            if (meta.hasLore()) {
                val loreArray = JsonArray()
                meta.lore?.forEach { loreArray.add(it) }
                add("lore", loreArray)
            }

            if (meta.hasItemModel()) {
                addProperty("itemModel", meta.itemModel?.toString())
            }

            if (meta.hasCustomModelData()) {
                val cmd = meta.customModelDataComponent

                val cmdArray = JsonArray()
                cmd.strings.forEach { cmdArray.add(it) }
                add("customModelData", cmdArray)

                val floatArray = JsonArray()
                cmd.floats.forEach { floatArray.add(it) }
                add("customModelDataFloats", floatArray)
            }

            if (meta.hasEquippable()) {
                val eq = meta.equippable
                addProperty("equippableSlot", eq.slot.name)
                eq.model?.let {
                    addProperty("equippableModel", it.toString())
                }
                // applyModelMeta が書き換える項目 (swappable) も含め、元の値を保存
                addProperty("equippableSwappable", eq.isSwappable)
                addProperty("equippableDispensable", eq.isDispensable)
                addProperty("equippableDamageOnHurt", eq.isDamageOnHurt)
            }

            // エンチャントの光沢 (hideEnchants で上書きされるため、元の状態を保存)
            if (meta.hasEnchantmentGlintOverride()) {
                addProperty("enchantmentGlintOverride", meta.enchantmentGlintOverride)
            }
        }

        pdc.set(
            GACHA_BACKUP_PDC_KEY,
            PersistentDataType.STRING,
            gson.toJson(backupObj)
        )

        // モデルの適用処理を共通化
        applyModelMeta(meta, data)

        targetItem.itemMeta = meta
        return true
    }

    fun removeModelFromItem(
        targetItem: ItemStack?
    ): Boolean {
        if (targetItem == null || targetItem.type == Material.AIR || !targetItem.hasItemMeta()) {
            return false
        }

        val meta = targetItem.itemMeta ?: return false
        val pdc = meta.persistentDataContainer

        if (!pdc.has(GACHA_PDC_KEY, PersistentDataType.STRING)) {
            return false
        }

        pdc.remove(GACHA_PDC_KEY)

        if (pdc.has(GACHA_BACKUP_PDC_KEY, PersistentDataType.STRING)) {
            val jsonString = pdc.get(GACHA_BACKUP_PDC_KEY, PersistentDataType.STRING)
            if (!jsonString.isNullOrBlank()) {
                try {
                    val backupObj = JsonParser.parseString(jsonString).asJsonObject

                    if (backupObj.has("displayName")) {
                        meta.setDisplayName(backupObj.get("displayName").asString)
                    } else {
                        meta.setDisplayName(null)
                    }

                    if (backupObj.has("lore")) {
                        val loreList = backupObj.getAsJsonArray("lore").map { it.asString }
                        meta.lore = loreList
                    } else {
                        meta.lore = null
                    }

                    if (backupObj.has("itemModel")) {
                        val modelKeyStr = backupObj.get("itemModel").asString
                        meta.itemModel = NamespacedKey.fromString(modelKeyStr)
                    } else {
                        meta.itemModel = null
                    }

                    val component = meta.customModelDataComponent
                    component.strings = if (backupObj.has("customModelData")) {
                        backupObj.getAsJsonArray("customModelData").map { it.asString }
                    } else {
                        emptyList()
                    }
                    component.floats = if (backupObj.has("customModelDataFloats")) {
                        backupObj.getAsJsonArray("customModelDataFloats").map { it.asFloat }
                    } else {
                        emptyList()
                    }
                    meta.setCustomModelDataComponent(component)

                    // ★追加: equippable の復元
                    if (backupObj.has("equippableSlot")) {
                        val eq = meta.equippable
                        eq.slot = EquipmentSlot.valueOf(backupObj.get("equippableSlot").asString)
                        eq.model = if (backupObj.has("equippableModel")) {
                            NamespacedKey.fromString(backupObj.get("equippableModel").asString)
                        } else null
                        if (backupObj.has("equippableSwappable")) {
                            eq.isSwappable = backupObj.get("equippableSwappable").asBoolean
                        }
                        if (backupObj.has("equippableDispensable")) {
                            eq.isDispensable = backupObj.get("equippableDispensable").asBoolean
                        }
                        if (backupObj.has("equippableDamageOnHurt")) {
                            eq.isDamageOnHurt = backupObj.get("equippableDamageOnHurt").asBoolean
                        }
                        meta.setEquippable(eq)
                    } else {
                        meta.setEquippable(null)
                    }

                    // エンチャントの光沢: 元が未設定なら未設定に戻す
                    meta.setEnchantmentGlintOverride(
                        if (backupObj.has("enchantmentGlintOverride")) {
                            backupObj.get("enchantmentGlintOverride").asBoolean
                        } else null
                    )

                } catch (e: Exception) {
                    meta.setDisplayName(null)
                    meta.lore = null
                    meta.itemModel = null
                    meta.setEquippable(null) // ★追加
                    meta.setEnchantmentGlintOverride(null)
                }
            }
            pdc.remove(GACHA_BACKUP_PDC_KEY)
        } else {
            meta.setDisplayName(null)
            meta.lore = null
            meta.itemModel = null
            meta.setEquippable(null) // ★追加
            meta.setEnchantmentGlintOverride(null)
            val component = meta.customModelDataComponent
            component.strings = emptyList()
            component.floats = emptyList()
            meta.setCustomModelDataComponent(component)
        }

        targetItem.itemMeta = meta

        return true
    }

    fun packFileName(rarity: ModelRarity, key: String): String {
        val packName = rarity.packModelPath.substringAfter(":") // 例: pack_s
        val safeKey = key.lowercase().replace(Regex("[^a-z0-9_.-]"), "_")
        return "${safeKey}_$packName"
    }

    /** パックアイテムの item_model */
    fun packItemModelKey(data: RegisteredModelData): NamespacedKey =
        NamespacedKey("gacha_system", packFileName(data.rarity, gachaKey(data)))

    private fun applyModelMeta(
        meta: ItemMeta,
        data: RegisteredModelData
    ) {
        // 表示名
        if(meta.displayName() == null)
            meta.setDisplayName("§f${data.itemName}")

        // モデル識別用 PDC
        meta.persistentDataContainer.set(
            GACHA_PDC_KEY,
            PersistentDataType.STRING,
            gachaKey(data)
        )

        // スロットタイプに応じたモデル設定
        if (data.slotType.equals("custom_model_data", ignoreCase = true)) {
            val component = meta.customModelDataComponent
            val floatValue = data.customModelDataFloat

            if (floatValue != null) {
                // range_dispatch(custom_model_data) 用
                component.floats = listOf(floatValue)
            } else {
                // strings を使ったモデル識別用
                component.strings = listOf(gachaKey(data))
            }

            meta.setCustomModelDataComponent(component)
        } else {
            // item_model
            meta.itemModel = itemModelKey(data)
        }

        val equippable = meta.equippable
        equippable.slot = EquipmentSlot.HEAD
        equippable.isSwappable = false   // 右クリックでは装備しない
        equippable.model = null          // ★防具の見た目を消す → item_model が頭に表示される
        meta.setEquippable(equippable)

        if(data.hideEnchants){
            meta.setEnchantmentGlintOverride(false)
        }

        if (data.applyLoreToItem) {
            val loreList = mutableListOf<String>()
            if (!data.description.isNullOrBlank()) {
                data.description.split("\n").forEach { line ->
                    loreList.add("§7$line")
                }
            }
            if (loreList.isNotEmpty()) {
                val merged = (meta.lore ?: emptyList()).toMutableList()
                merged.addAll(loreList)
                meta.lore = merged
            }
        }
    }

}