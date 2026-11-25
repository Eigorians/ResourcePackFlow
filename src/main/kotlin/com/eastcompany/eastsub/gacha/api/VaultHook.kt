package com.eastcompany.eastsub.gacha.api

import net.milkbowl.vault.economy.Economy
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.plugin.java.JavaPlugin

class VaultHook(
    private val plugin: JavaPlugin
) {
    private var economy: Economy? = null

    object GachaConfig {
        const val DEFAULT_COST: Int = 50
    }

    /**
     * Vault経済サービスとの接続
     */
    fun setup(): Boolean {
        economy = null

        if (!plugin.server.pluginManager.isPluginEnabled("Vault")) {
            return false
        }

        val provider: RegisteredServiceProvider<Economy> =
            plugin.server.servicesManager
                .getRegistration(Economy::class.java)
                ?: return false

        economy = provider.provider
        return true
    }

    /**
     * 経済機能が利用可能か
     */
    fun isAvailable(): Boolean {
        return economy != null
    }

    /**
     * 残高取得
     */
    fun getBalance(player: OfflinePlayer): Double? {
        return economy?.getBalance(player)
    }

    /**
     * お金を追加
     */
    fun deposit(player: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0.0) return false

        return economy
            ?.depositPlayer(player, amount)
            ?.transactionSuccess() == true
    }

    /**
     * お金を引き落とす
     */
    fun withdraw(player: OfflinePlayer, amount: Int): Boolean {
        if (amount <= 0) return false

        return economy
            ?.withdrawPlayer(player, amount.toDouble())
            ?.transactionSuccess() == true
    }

    /**
     * 通貨名取得
     */
    fun getCurrencyName(): String? {
        return economy?.currencyNamePlural()
    }

    /**
     * 経済サービス取得
     */
    fun getEconomy(): Economy? {
        return economy
    }
}