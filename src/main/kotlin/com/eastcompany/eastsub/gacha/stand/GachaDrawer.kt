package com.eastcompany.eastsub.gacha.stand

import com.eastcompany.eastsub.gacha.model.GachaSettingManager
import com.eastcompany.eastsub.gacha.model.ModelConfigManager
import com.eastcompany.eastsub.gacha.model.ModelRarity
import com.eastcompany.eastsub.gacha.model.RegisteredModelData
import kotlin.random.Random

/** 抽選の対象になるレア度か（UNCLASSIFIED と MISS はモデルとしては抽選しない） */
fun ModelRarity.isDrawable(): Boolean =
    this != ModelRarity.UNCLASSIFIED && this != ModelRarity.MISS

/** 抽選結果 */
sealed interface DrawResult {
    /** モデルが当たった */
    data class Win(val model: RegisteredModelData) : DrawResult

    /** ハズレ（MISS の重みに当たった） */
    data object Miss : DrawResult

    /** 抽選できるモデルがそもそも登録されていない */
    data object NoModels : DrawResult

    /** PDC に保存するための文字列 */
    fun toToken(): String = when (this) {
        is Win -> "model:${model.modelId}"
        Miss -> "miss"
        NoModels -> "none"
    }
}

/** 排出確率の表示用 */
data class RarityOdds(val rarity: ModelRarity, val percent: Double)

/**
 * 抽選だけを担当するクラス。
 *
 * 1. 作成者が持つモデルのレア度ごとに重みを取り、MISS の重みも加えてレア度を抽選
 * 2. 当たったレア度の中から均等にモデルを選ぶ
 *
 * 排出確率の表示（[odds]）と実際の抽選が同じ計算になる。
 * MISS の重みは settings.weights["MISS"]。0 または未設定ならハズレは出ない。
 */
class GachaDrawer(
    private val modelConfigManager: ModelConfigManager,
    private val settingManager: GachaSettingManager,
    private val random: Random = Random.Default
) {

    fun validModels(author: String): List<RegisteredModelData> =
        modelConfigManager.loadModelsByAuthor(author).values.filter { it.rarity.isDrawable() }

    fun findModel(author: String, modelId: String): RegisteredModelData? =
        validModels(author).firstOrNull { it.modelId.equals(modelId, ignoreCase = true) }

    fun draw(author: String): DrawResult {
        val models = validModels(author)
        if (models.isEmpty()) return DrawResult.NoModels

        val table = buildTable(author, models)
        // 重みが全て未設定なら均等に当てる
        if (table.total <= 0) return DrawResult.Win(models.random(random))

        var roll = random.nextInt(table.total)
        for ((rarity, weight) in table.rarityWeights) {
            if (roll < weight) {
                return DrawResult.Win(table.groups.getValue(rarity).random(random))
            }
            roll -= weight
        }
        return DrawResult.Miss
    }

    /** 実際の抽選と同じ前提での排出確率（登録済みのレア度のみ。MISS は最後） */
    fun odds(author: String): List<RarityOdds> {
        val models = validModels(author)
        if (models.isEmpty()) return emptyList()

        val table = buildTable(author, models)
        if (table.total <= 0) return emptyList()

        val result = table.rarityWeights.map { (rarity, weight) ->
            RarityOdds(rarity, weight * 100.0 / table.total)
        }.toMutableList()
        if (table.missWeight > 0) {
            result += RarityOdds(ModelRarity.MISS, table.missWeight * 100.0 / table.total)
        }
        return result
    }

    /** PDC に保存したトークンから抽選結果を復元する。復元できなければ null */
    fun restore(author: String, token: String): DrawResult? = when {
        token == "miss" -> DrawResult.Miss
        token == "none" -> DrawResult.NoModels
        token.startsWith("model:") ->
            findModel(author, token.removePrefix("model:"))?.let { DrawResult.Win(it) }
        else -> null
    }

    private class Table(
        val groups: Map<ModelRarity, List<RegisteredModelData>>,
        val rarityWeights: List<Pair<ModelRarity, Int>>,
        val missWeight: Int
    ) {
        val total: Int = rarityWeights.sumOf { it.second } + missWeight
    }

    private fun buildTable(author: String, models: List<RegisteredModelData>): Table {
        val weights = settingManager.loadSettings(author).weights
        val groups = models.groupBy { it.rarity }
        val rarityWeights = groups.keys
            .sortedBy { it.ordinal }
            .map { it to (weights[it.name] ?: 0).coerceAtLeast(0) }
            .filter { it.second > 0 }
        val missWeight = (weights[ModelRarity.MISS.name] ?: 0).coerceAtLeast(0)
        return Table(groups, rarityWeights, missWeight)
    }

    /** 目押し用のリール。レア度の重みに比例してコマを配分し、シャッフルする */
    fun buildReel(author: String, size: Int = 100): List<DrawResult> {
        val models = validModels(author)
        if (models.isEmpty()) return emptyList()

        val table = buildTable(author, models)
        if (table.total <= 0) return models.shuffled(random).map { DrawResult.Win(it) }

        val reel = mutableListOf<DrawResult>()
        for ((rarity, weight) in table.rarityWeights) {
            val slots = maxOf(1, Math.round(weight * size / table.total.toDouble()).toInt())
            val pool = table.groups.getValue(rarity).shuffled(random)
            repeat(slots) { reel += DrawResult.Win(pool[it % pool.size]) }
        }
        if (table.missWeight > 0) {
            val slots = maxOf(1, Math.round(table.missWeight * size / table.total.toDouble()).toInt())
            repeat(slots) { reel += DrawResult.Miss }
        }
        reel.shuffle(random)
        return reel
    }
}