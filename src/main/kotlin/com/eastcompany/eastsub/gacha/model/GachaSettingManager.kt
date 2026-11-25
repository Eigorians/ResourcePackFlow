package com.eastcompany.eastsub.gacha.model

import com.eastcompany.eastsub.gacha.api.VaultHook
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.logging.Logger

/**
 * プレイヤーごとのガチャ設定データ（金額 ＋ レアリティ重み）
 */
data class PlayerGachaSettings(
    val price: Int = VaultHook.GachaConfig.DEFAULT_COST,
    val weights: Map<String, Int> = mapOf(
        "MISS" to 50,
        "F" to 50,
        "S" to 0,
        "A" to 0,
        "B" to 0,
        "C" to 0,
        "D" to 0,
        "E" to 0,
        "UNCLASSIFIED" to 0
    )
)

/**
 * プレイヤーごとのガチャ金額・重み設定を一括管理するマネージャークラス
 * 保存先: plugins/ResourcePackFlow/resourcepack/config/data/プレイヤー名.json
 */
class GachaSettingManager(
    private val dataFolder: File,
    private val logger: Logger
) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // ★ 指定の保存先: plugins/ResourcePackFlow/resourcepack/config/data
    private val dataDir = File(dataFolder, "resourcepack/config/data")

    init {
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
    }

    /**
     * プレイヤーのガチャ設定（金額・重み）を全取得
     */
    fun loadSettings(playerName: String): PlayerGachaSettings {
        if (playerName.isBlank()) return PlayerGachaSettings()

        val file = File(dataDir, "${playerName.lowercase()}.json")
        if (!file.exists()) {
            return PlayerGachaSettings()
        }

        return try {
            val json = file.readText(StandardCharsets.UTF_8)
            val settings = gson.fromJson(json, PlayerGachaSettings::class.java)
            if (settings != null) {
                // 最低金額 100 のバリデーション補正
                val validPrice = if (settings.price < VaultHook.GachaConfig.DEFAULT_COST) VaultHook.GachaConfig.DEFAULT_COST else settings.price
                settings.copy(price = validPrice)
            } else {
                PlayerGachaSettings()
            }
        } catch (e: Exception) {
            logger.warning("config/data/${playerName.lowercase()}.json の読み込みに失敗したため初期値を使用します: ${e.message}")
            PlayerGachaSettings()
        }
    }

    /**
     * 重み（Weights）のみを取得する互換ヘルパー
     */
    fun loadWeights(playerName: String): Map<String, Int> {
        return loadSettings(playerName).weights
    }

    /**
     * ガチャ金額（Price）のみを取得するヘルパー（最低 100）
     */
    fun loadPrice(playerName: String): Int {
        return loadSettings(playerName).price
    }

    /**
     * プレイヤーのガチャ設定を一括保存する
     */
    fun saveSettings(playerName: String, settings: PlayerGachaSettings): Boolean {
        if (playerName.isBlank()) return false

        val file = File(dataDir, "${playerName.lowercase()}.json")
        val validPrice = if (settings.price < VaultHook.GachaConfig.DEFAULT_COST) VaultHook.GachaConfig.DEFAULT_COST else settings.price
        val targetSettings = settings.copy(price = validPrice)

        return try {
            val json = gson.toJson(targetSettings)
            file.writeText(json, StandardCharsets.UTF_8)
            true
        } catch (e: Exception) {
            logger.severe("config/data/${playerName.lowercase()}.json への保存に失敗しました: ${e.message}")
            false
        }
    }

    /**
     * 重み（Weights）のみを更新・保存する
     */
    fun saveWeights(playerName: String, weightsMap: Map<String, Int>): Boolean {
        val currentSettings = loadSettings(playerName)
        val updatedSettings = currentSettings.copy(weights = weightsMap)
        return saveSettings(playerName, updatedSettings)
    }

    /**
     */
    fun savePrice(playerName: String, price: Int): Boolean {
        val validPrice = if (price < VaultHook.GachaConfig.DEFAULT_COST) VaultHook.GachaConfig.DEFAULT_COST else price
        val currentSettings = loadSettings(playerName)
        val updatedSettings = currentSettings.copy(price = validPrice)
        return saveSettings(playerName, updatedSettings)
    }

    /**
     * 初期重みのデフォルト値
     */
    fun getDefaultWeights(): Map<String, Int> {
        return PlayerGachaSettings().weights
    }
}