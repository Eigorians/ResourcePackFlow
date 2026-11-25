package com.eastcompany.eastsub.gacha.model

import org.bukkit.ChatColor

/**
 * レアリティ定義 (S ~ F + MISS + 未分類)
 * ChatColor および 各レアリティ専用のパックモデルパス (gacha_system:pack_x) を管理
 */
enum class ModelRarity(
    val displayName: String,
    val color: ChatColor,
    val packModelPath: String, // ★ アイテムモデルパス (例: "gacha_system:pack_s")
    val badgeStyle: String     // WebUI用スタイル
) {
    S(
        displayName = "S",
        color = ChatColor.YELLOW,
        packModelPath = "gacha_system:pack_s",
        badgeStyle = "background: #fff3cd; color: #856404; border: 1px solid #ffeeba;"
    ),
    A(
        displayName = "A",
        color = ChatColor.RED,
        packModelPath = "gacha_system:pack_a",
        badgeStyle = "background: #f8d7da; color: #721c24; border: 1px solid #f5c6cb;"
    ),
    B(
        displayName = "B",
        color = ChatColor.BLUE,
        packModelPath = "gacha_system:pack_b",
        badgeStyle = "background: #cce5ff; color: #004085; border: 1px solid #b8daff;"
    ),
    C(
        displayName = "C",
        color = ChatColor.GREEN,
        packModelPath = "gacha_system:pack_c",
        badgeStyle = "background: #d4edda; color: #155724; border: 1px solid #c3e6cb;"
    ),
    D(
        displayName = "D",
        color = ChatColor.DARK_PURPLE,
        packModelPath = "gacha_system:pack_d",
        badgeStyle = "background: #e8daef; color: #512e5f; border: 1px solid #d7bde2;"
    ),
    E(
        displayName = "E",
        color = ChatColor.GOLD, // オレンジ
        packModelPath = "gacha_system:pack_e",
        badgeStyle = "background: #ffe5d0; color: #a04000; border: 1px solid #f5cba7;"
    ),
    F(
        displayName = "F",
        color = ChatColor.GRAY,
        packModelPath = "gacha_system:pack_f",
        badgeStyle = "background: #e2e3e5; color: #383d41; border: 1px solid #d6d8db;"
    ),
    MISS(
        displayName = "はずれ",
        color = ChatColor.DARK_GRAY,
        packModelPath = "gacha_system:pack",
        badgeStyle = "background: #d6d8d9; color: #1b1e21; border: 1px solid #c6c8ca;"
    ),
    UNCLASSIFIED(
        displayName = "未分類",
        color = ChatColor.BLACK,
        packModelPath = "gacha_system:pack_u",
        badgeStyle = "background: #212529; color: #ffffff; border: 1px solid #000000;"
    );

    companion object {
        fun fromString(raw: String?): ModelRarity {
            if (raw.isNullOrBlank()) return UNCLASSIFIED
            return try {
                valueOf(raw.trim().uppercase())
            } catch (e: IllegalArgumentException) {
                UNCLASSIFIED
            }
        }
    }
}

/**
 * 登録されたモデルの情報を保持するデータクラス
 */
data class RegisteredModelData(
    val modelId: String,          // モデルID (例: "magic_wand")
    val itemName: String,         // 表示名 (例: "魔法の杖")
    val slotType: String,         // "custom_model_data" または "item_model"
    val baseItem: String?,        // ベースアイテム (CMDの場合に指定。例: "stick")
    val description: String?,     // 説明欄・自由記載
    val rarity: ModelRarity = ModelRarity.UNCLASSIFIED, // レアリティ
    val registeredAt: Long = System.currentTimeMillis(), // 登録日時
    val key: String? = null,                             // アイテム適用キー
    val modelPath: String? = null,                       // モデルJSONパス
    val itemPath: String? = null,                        // アイテム定義パス
    val texturePaths: List<String> = emptyList(),         // テクスチャ画像パスリスト
    val deletable: Boolean = true,                       // 削除可能か
    val hiddenFromPreview: Boolean = false,              // プレビューから隠すか
    val dropAsItem: Boolean = false,             // true: アイテムとして排出 / false: パックとして排出
    /**
     * 既存リソースパック内のアイテムを選んで登録したものか。
     * true の場合、key は「識別コードそのまま」（gacha_system: を付け足さない）。
     * 既存の JSON にこの項目が無い場合は false として読まれる。
     */
    val external: Boolean = false,
    /**
     * 数値(float)の custom_model_data で識別するアイテムの値（range_dispatch の threshold）。
     * 文字列の CMD で識別するアイテムは null。
     */
    val customModelDataFloat: Float? = null,
    val recommendedItem: String? = null,
    val hideEnchants: Boolean = false,        // エンチャントを非表示にするか
    val excludeFromPreview: Boolean = false,  // ガチャプレビューから除くか
    val applyLoreToItem: Boolean = false,
)