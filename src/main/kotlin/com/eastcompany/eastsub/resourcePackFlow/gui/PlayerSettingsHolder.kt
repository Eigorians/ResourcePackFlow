package com.eastcompany.eastsub.resourcePackFlow.gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

enum class SettingsScreen {
    MAIN,
    ADDONS
}

class PlayerSettingsHolder(
    val screen: SettingsScreen
) : InventoryHolder {

    /** 追加パック画面のページ (0始まり) */
    var page = 0

    /** 表示中のスロット → 追加パックID。表示後にzipが増減しても別のパックを切り替えないため */
    val addonSlots = mutableMapOf<Int, String>()

    private var inventory: Inventory? = null

    fun setInventory(inventory: Inventory) {
        this.inventory = inventory
    }

    override fun getInventory(): Inventory {
        return inventory
            ?: throw IllegalStateException("Inventory has not been initialized")
    }
}
