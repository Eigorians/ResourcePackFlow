package com.eastcompany.eastsub.gacha.stand

import com.eastcompany.eastsub.gacha.stand.GachaStandManager.plugin
import org.bukkit.NamespacedKey

/**
 * スタンドのエンティティに付ける PDC キーとインデックス定数。
 */
object GachaStandKeys {

    val REEL = NamespacedKey(plugin, "reel")       // STRING
    val EARNED = NamespacedKey(plugin, "earned")   // DOUBLE
    // 親・子の関係
    val IS_STAND_PARENT = NamespacedKey("gacha", "is_stand_parent")
    val CHILD_UUIDS = NamespacedKey("gacha", "child_uuids")
    val PARENT_TEXT_UUID = NamespacedKey("gacha", "parent_text_uuid")
    val STAND_INDEX = NamespacedKey("gacha", "stand_index")

    // 表示中の作成者・モデル
    val CURRENT_AUTHOR = NamespacedKey("gacha", "current_author")
    val CURRENT_AUTHOR_UUID = NamespacedKey("gacha", "current_author_uuid") // 追加
    val CURRENT_MODEL_ID = NamespacedKey("gacha", "current_model_id")

    // ガチャ実行状態
    val IS_EXECUTING = NamespacedKey("gacha", "is_executing")
    val EXECUTE_TICK = NamespacedKey("gacha", "execute_tick")
    val EXECUTING_PLAYER_UUID = NamespacedKey("gacha", "executing_player_uuid")

    /** 開始時に確定させた抽選結果（DrawResult.toToken の文字列） */
    val PENDING_RESULT = NamespacedKey("gacha", "pending_result")

    val IS_PERSONAL_STAND = NamespacedKey(
        plugin,
        "is_personal_stand"
    )

    const val INDEX_PREVIEW_ITEM = 0
    const val INDEX_EXECUTE_BUTTON = 1
    const val INDEX_MANNEQUIN = 2
    const val INDEX_INFO_BOARD = 3
    const val INDEX_MANNEQUIN_TEXT = 4
    const val INDEX_RARITY_TAG = 5
    const val INDEX_SWITCH_HINT = 8

    const val FLAG_ON: Byte = 1
    const val FLAG_OFF: Byte = 0
}