package com.eastcompany.eastsub.resourcePackFlow.gui

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import java.util.Locale

/** 追加パックのアイコン指定 (マテリアル名 または item_model) */
sealed interface AddonIcon {

    fun createItem(): ItemStack

    data class OfMaterial(val material: Material) : AddonIcon {
        override fun createItem() = ItemStack(material)
    }

    data class OfModel(val key: NamespacedKey) : AddonIcon {
        override fun createItem() = ItemStack(Material.PAPER).apply {
            itemMeta = itemMeta?.apply { setItemModel(key) }
        }
    }

    companion object {
        private val MODEL_REGEX = Regex("^([a-z0-9_.-]+):([a-z0-9_./-]+)$")

        /**
         * "diamond" や "minecraft:diamond" はマテリアル、
         * それ以外の "名前空間:パス" は item_model として扱う。
         */
        fun parse(text: String): AddonIcon? {
            val materialName = text.lowercase(Locale.ROOT).removePrefix("minecraft:")

            Material.getMaterial(materialName.uppercase(Locale.ROOT))
                ?.takeIf { !it.name.startsWith("LEGACY_") && it.isItem && !it.isAir }
                ?.let { return OfMaterial(it) }

            val (namespace, path) = MODEL_REGEX.matchEntire(text)?.destructured ?: return null

            // ファイルの存在確認に使うため、上の階層を指すものは受け付けない
            if (".." in namespace || ".." in path) return null

            return NamespacedKey.fromString("$namespace:$path")?.let(::OfModel)
        }
    }
}
