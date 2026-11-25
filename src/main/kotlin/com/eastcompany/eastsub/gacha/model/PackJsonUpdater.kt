package com.eastcompany.eastsub.gacha.model

import com.eastcompany.eastsub.gacha.item.GachaItemBuilder
import com.eastcompany.eastsub.gacha.model.ModelRarity
import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * パックJSON (assets/gacha_system/items/{key}_pack_{レア度}.json) の更新を一手に引き受ける。
 * 1キー × 1レア度 = 1ファイル。
 */
class PackJsonUpdater(private val plugin: ResourcePackFlow) {

    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    /** 全レアリティのパックJSONを出力 */
    fun addToAllRarities(whenKey: String, modelLocation: String = whenKey) {
        for (r in ModelRarity.entries) {
            update(r, whenKey, modelLocation)
        }
    }

    /**
     * @param whenKey       識別コード(ファイル名に使用)
     * @param modelLocation 実際に表示するモデルの場所。省略時は whenKey と同じ
     */
    fun update(rarity: ModelRarity, whenKey: String, modelLocation: String = whenKey) {
        val fileName = packFileName(rarity, whenKey)

        val dir = File(plugin.dataFolder, "resourcepack/assets/gacha_system/items")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$fileName.json")

        try {
            val root = createPackJson(rarity.packModelPath, modelLocation)
            file.writeText(prettyGson.toJson(root), StandardCharsets.UTF_8)
            plugin.logger.info("パック定義ファイル ($fileName.json) を出力しました。")
        } catch (e: Exception) {
            plugin.logger.warning("パックJSON ($fileName.json) の出力中にエラーが発生しました: ${e.message}")
        }
    }

    /** 出力ファイル名(拡張子なし)。例: key_pack_common */
    fun packFileName(rarity: ModelRarity, whenKey: String): String =
        GachaItemBuilder.packFileName(rarity, whenKey)

    /** 手持ち表示時だけ modelLocation、それ以外(GUI等)は packModelPath を表示 */
    /** packModelPath の上に modelLocation を重ねて表示 */
    private fun modelObj(location: String) = JsonObject().apply {
        addProperty("type", "minecraft:model")
        addProperty("model", location)
    }

    private fun createPackJson(packModelPath: String, modelLocation: String) = JsonObject().apply {
        add("model", JsonObject().apply {
            addProperty("type", "minecraft:select")
            addProperty("property", "minecraft:display_context")
            add("cases", JsonArray().apply {
                // 手持ち: 個別モデルのみ
                add(JsonObject().apply {
                    add("when", JsonArray().apply {
                        add("firstperson_righthand"); add("firstperson_lefthand")
                        add("thirdperson_righthand"); add("thirdperson_lefthand")
                    })
                    add("model", modelObj(modelLocation))
                })
                // GUI: パックの上に個別モデルをオーバーレイ
                add(JsonObject().apply {
                    addProperty("when", "gui")
                    add("model", JsonObject().apply {
                        addProperty("type", "minecraft:composite")
                        add("models", JsonArray().apply {
                            add(modelObj(packModelPath))   // 下
                            add(modelObj(modelLocation))   // 上
                        })
                    })
                })
            })
            add("fallback", modelObj(packModelPath))
        })
    }
}