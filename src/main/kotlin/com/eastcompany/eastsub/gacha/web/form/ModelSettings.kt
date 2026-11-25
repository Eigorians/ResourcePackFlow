package com.eastcompany.eastsub.gacha.web.form

import com.eastcompany.eastsub.gacha.item.ItemFilter
import com.eastcompany.eastsub.gacha.model.ModelRarity
import com.eastcompany.eastsub.gacha.model.RegisteredModelData

internal fun htmlEscape(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

/**
 * モデル設定項目の定義 (1項目 = 1インスタンス)。
 * 編集ページ / 一括設定ページ / 両ハンドラはすべて [ModelSettings.all] をループするだけ。
 */
sealed class ModelSetting(
    /** フォーム/リクエストのパラメータ名 */
    val key: String,
    val label: String,
    /** 一覧テーブルの列見出し */
    val shortLabel: String = label,
    /** 一括設定ページに出すか */
    val bulkable: Boolean = true,
    /** 個別編集ページに出すか */
    val editable: Boolean = true,
    /** このモデルで変更可能か (false なら操作不可・一括ではスキップ) */
    val applicable: (RegisteredModelData) -> Boolean = { true },
    /** applicable = false のときに表示する注記 */
    val inapplicableNote: String? = null,
    /** applicable = false のとき一覧セルに表示する文字 */
    val fixedLabel: String = "固定"
) {
    /** raw がこの項目の値として正しいか */
    abstract fun accepts(raw: String): Boolean

    /** raw を反映した新しいデータを返す (accepts が true の前提) */
    abstract fun apply(d: RegisteredModelData, raw: String): RegisteredModelData

}

class BoolSetting(
    key: String,
    label: String,
    shortLabel: String = label,
    bulkable: Boolean = true,
    editable: Boolean = true,
    applicable: (RegisteredModelData) -> Boolean = { true },
    inapplicableNote: String? = null,
    fixedLabel: String = "固定",
    /** 個別編集ページでラベルの後ろに付ける補足 */
    val hint: String? = null,
    val get: (RegisteredModelData) -> Boolean,
    val set: (RegisteredModelData, Boolean) -> RegisteredModelData
) : ModelSetting(key, label, shortLabel, bulkable, editable, applicable, inapplicableNote, fixedLabel) {
    override fun accepts(raw: String) = raw == "true" || raw == "false"
    override fun apply(d: RegisteredModelData, raw: String) = set(d, raw == "true")
}

class EnumSetting(
    key: String,
    label: String,
    shortLabel: String = label,
    bulkable: Boolean = true,
    editable: Boolean = true,
    applicable: (RegisteredModelData) -> Boolean = { true },
    inapplicableNote: String? = null,
    fixedLabel: String = "固定",
    /** (値, 表示名) の並び。表示順もこの順 */
    val choices: List<Pair<String, String>>,
    val getValue: (RegisteredModelData) -> String,
    val setValue: (RegisteredModelData, String) -> RegisteredModelData
) : ModelSetting(key, label, shortLabel, bulkable, editable, applicable, inapplicableNote, fixedLabel) {
    override fun accepts(raw: String) = choices.any { it.first == raw }
    override fun apply(d: RegisteredModelData, raw: String) = setValue(d, raw)

    fun labelOf(value: String): String = choices.firstOrNull { it.first == value }?.second ?: value
}

object ModelSettings {

    /** レア度の表示・ソート順 */
    val rarityOrder: List<ModelRarity> = listOf(
        ModelRarity.S, ModelRarity.A, ModelRarity.B, ModelRarity.C,
        ModelRarity.D, ModelRarity.E, ModelRarity.F, ModelRarity.UNCLASSIFIED
    )

    fun rarityLabel(r: ModelRarity): String = if (r == ModelRarity.UNCLASSIFIED) "未分類" else r.name

    // ★ 新しい設定項目はここに1つ足すだけ (RegisteredModelData にフィールドを追加するのも忘れずに)
    val all: List<ModelSetting> = listOf(
        BoolSetting(
            key = "hiddenFromPreview",
            label = "ガチャのプレビューで「？」表示にする",
            shortLabel = "プレビュー？表示",
            hint = "当選時に初めて正体が表示される",
            get = { it.hiddenFromPreview },
            set = { d, v -> d.copy(hiddenFromPreview = v) }
        ),
        BoolSetting(
            key = "dropAsItem",
            label = "アイテムとして排出する",
            shortLabel = "アイテム排出",
            hint = "オフならパックとして排出",
            applicable = { ItemFilter.canDropAsItem(it) },
            inapplicableNote = "このモデルのベースアイテムはレアリティが高いため、常にパックとして排出されます。",
            fixedLabel = "固定(パック)",
            get = { it.dropAsItem },
            set = { d, v -> d.copy(dropAsItem = v) }
        ),
        BoolSetting(
            key = "hideEnchants",
            label = "エンチャントを非表示にする",
            shortLabel = "エンチャント非表示",
            get = { it.hideEnchants },
            set = { d, v -> d.copy(hideEnchants = v) }
        ),
        BoolSetting(
            key = "excludeFromPreview",
            label = "ガチャのプレビューから除外する",
            shortLabel = "プレビュー除外",
            hint = "プレビュー一覧に一切出さない",
            get = { it.excludeFromPreview },
            set = { d, v -> d.copy(excludeFromPreview = v) }
        ),
        EnumSetting(
            key = "rarity",
            label = "レア度",
            editable = false, // 個別編集ページでは従来どおりバッジ表示のみ。出したければ true に
            choices = rarityOrder.map { it.name to rarityLabel(it) },
            getValue = { it.rarity.name },
            setValue = { d, v -> d.copy(rarity = ModelRarity.valueOf(v)) }
        ),
        BoolSetting(
            key = "applyLoreToItem",
            label = "Lore をアイテムにも適用する",
            shortLabel = "Lore適用",
            hint = "オフなら説明文はアイテムに付かない",
            get = { it.applyLoreToItem },
            set = { d, v -> d.copy(applyLoreToItem = v) }
        ),
    )

    val bulkable: List<ModelSetting> get() = all.filter { it.bulkable }
    val editable: List<ModelSetting> get() = all.filter { it.editable }
}