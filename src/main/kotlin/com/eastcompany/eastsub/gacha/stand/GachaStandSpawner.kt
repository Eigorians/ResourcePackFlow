package com.eastcompany.eastsub.gacha.stand

import com.eastcompany.eastsub.gacha.model.GachaSettingManager
import com.eastcompany.eastsub.gacha.model.ModelConfigManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Vector3f
import java.util.*

/**
 * スタンドを構成するエンティティの生成を担当する。
 */
class GachaStandSpawner(
    private val modelConfigManager: ModelConfigManager,
    private val gachaSettingManager: GachaSettingManager
) {

    private val lift = 0.3

    companion object {
        const val SWITCH_HINT_TEXT = "§f[§bクリックで切り替え§f]"
    }

    /** スタンド一式を生成し、親エンティティの UUID を返す */
    fun spawn(baseLoc: Location, snappedYaw: Float, creator: Player? = null): UUID? {
        val world = baseLoc.world ?: return null

        val yawRad = Math.toRadians(snappedYaw.toDouble())
        val rightDir = Vector(-Math.cos(yawRad), 0.0, -Math.sin(yawRad)).normalize()
        val forwardDir = Vector(-Math.sin(yawRad), 0.0, Math.cos(yawRad)).normalize()

        val parent = spawnParent(baseLoc, snappedYaw)
        val parentUuidText = parent.uniqueId.toString()

        val mannequinLoc = baseLoc.clone().add(rightDir.clone().multiply(2.0)).apply { yaw = snappedYaw }

        val childUuids = listOf(
            spawnMannequin(mannequinLoc, parentUuidText),
            spawnSwitchLabel(mannequinLoc, forwardDir, snappedYaw, parentUuidText),
            spawnInfoBoard(baseLoc, rightDir, snappedYaw, parentUuidText),
            spawnInfoBoardHitbox(baseLoc, rightDir, parentUuidText),   // ← 追加
            spawnPreview(baseLoc, snappedYaw, parentUuidText),
            spawnSwitchHint(baseLoc, snappedYaw, parentUuidText),   // ★追加
            spawnRarityTag(baseLoc, snappedYaw, parentUuidText),
            spawnInteraction(
                baseLoc.clone().add(0.0, 0.95 + lift, 0.0), 0.8f, 0.6f,
                parentUuidText, GachaStandKeys.INDEX_EXECUTE_BUTTON
            ),
            spawnInteraction(
                baseLoc.clone().add(0.0, 1.8 + lift, 0.0), 0.8f, 1.4f,
                parentUuidText, GachaStandKeys.INDEX_PREVIEW_ITEM
            )
        )

        val parentPdc = parent.persistentDataContainer
        parentPdc.set(GachaStandKeys.IS_STAND_PARENT, PersistentDataType.BYTE, GachaStandKeys.FLAG_ON)
        parentPdc.set(GachaStandKeys.CHILD_UUIDS, PersistentDataType.STRING, childUuids.joinToString(","))
        parentPdc.set(GachaStandKeys.IS_EXECUTING, PersistentDataType.BYTE, GachaStandKeys.FLAG_OFF)

        if (creator != null) initializeForCreator(parent.uniqueId, creator)

        return parent.uniqueId
    }

    // ------------------------------------------------------------------
    // 初期状態（作成者のモデルを表示）
    // ------------------------------------------------------------------

    private fun initializeForCreator(parentUuid: UUID, creator: Player) {
        val controller = GachaStandController(parentUuid)
        controller.updateMannequinSkin(creator)

        val parentPdc = controller.getParentEntity()?.persistentDataContainer ?: return

        parentPdc.set(
            GachaStandKeys.CURRENT_AUTHOR,
            PersistentDataType.STRING,
            creator.name
        )

        parentPdc.set(
            GachaStandKeys.CURRENT_AUTHOR_UUID,
            PersistentDataType.STRING,
            creator.uniqueId.toString()
        )

        val firstModel = modelConfigManager.loadModelsByPlayerUuid(creator.uniqueId)
            .values.firstOrNull { it.rarity.isDrawable() }

        if (firstModel != null) {
            controller.applyModelData(firstModel)
        } else {
            controller.showNoModels(creator.name)
        }
    }

    // ------------------------------------------------------------------
    // 各エンティティの生成
    // ------------------------------------------------------------------

    private fun spawnParent(baseLoc: Location, yaw: Float): TextDisplay {
        val loc = baseLoc.clone().add(0.0, 0.95 + lift, 0.0).apply { this.yaw = yaw }
        return spawnEntity<TextDisplay>(loc, EntityType.TEXT_DISPLAY).apply {
            billboard = Display.Billboard.FIXED
            text = GachaStandController.BUTTON_START
            isSeeThrough = false
            isShadowed = true
        }
    }

    private fun spawnMannequin(loc: Location, parentUuid: String): String {
        val mannequin = spawnEntity<Mannequin>(loc, EntityType.MANNEQUIN).apply {
            setGravity(false)
            isInvulnerable = true
            isPersistent = true
            isImmovable = true
            isCustomNameVisible = false
        }
        return mannequin.tag(parentUuid, GachaStandKeys.INDEX_MANNEQUIN)
    }

    private fun spawnSwitchLabel(
        mannequinLoc: Location,
        forwardDir: Vector,
        yaw: Float,
        parentUuid: String
    ): String {
        val loc = mannequinLoc.clone()
            .add(forwardDir.clone().multiply(0.35))
            .add(0.0, 1.05, 0.0)
            .apply { this.yaw = yaw }

        val label = spawnEntity<TextDisplay>(loc, EntityType.TEXT_DISPLAY).apply {
            billboard = Display.Billboard.FIXED
            // 3行表記 (Shift+右クリで一覧表示を追加)
            val isPersonal = Bukkit.getEntity(UUID.fromString(parentUuid))
                ?.persistentDataContainer
                ?.has(GachaStandKeys.IS_PERSONAL_STAND, PersistentDataType.BYTE) == true

            text = if (isPersonal) {
                "§f[§b右クリで切り替え§f]\n§f[§eパンチで説明§f]"
            } else {
                "§f[§b右クリで切り替え§f]\n§f[§aShift+右クリで一覧を表示§f]\n§f[§eパンチで説明§f]"
            }
            isSeeThrough = false
            isShadowed = true
            scaleTo(0.6f) // 文字数が増えるためスケールを調整
        }
        return label.tag(parentUuid, GachaStandKeys.INDEX_MANNEQUIN_TEXT)
    }
    /** 情報板のクリック用当たり判定 */
    private fun spawnInfoBoardHitbox(
        baseLoc: Location,
        rightDir: Vector,
        parentUuid: String
    ): String {
        // 情報板テキストの中心付近。テキストは y=0.95 を起点に上へ伸びる
        val loc = baseLoc.clone()
            .add(rightDir.clone().multiply(-3.0))
            .add(0.0, 0.95, 0.0)

        return spawnInteraction(loc, 1.6f, 2.0f, parentUuid, GachaStandKeys.INDEX_INFO_BOARD)
    }

    private fun spawnInfoBoard(
        baseLoc: Location,
        rightDir: Vector,
        yaw: Float,
        parentUuid: String
    ): String {
        val loc = baseLoc.clone()
            .add(rightDir.clone().multiply(-3.0))
            .add(0.0, 0.95, 0.0)
            .apply { this.yaw = yaw }

        val board = spawnEntity<TextDisplay>(loc, EntityType.TEXT_DISPLAY).apply {
            billboard = Display.Billboard.FIXED
            alignment = TextDisplay.TextAlignment.LEFT
            lineWidth = 300
            text = "§f[§eガチャ情報§f]\n§7確率・報酬一覧"
            isSeeThrough = false
            isShadowed = true
        }
        return board.tag(parentUuid, GachaStandKeys.INDEX_INFO_BOARD)
    }


    private fun spawnSwitchHint(baseLoc: Location, yaw: Float, parentUuid: String): String {
        val loc = baseLoc.clone().add(0.0, 3.2 + lift, 0.0).apply { this.yaw = yaw }

        val hint = spawnEntity<TextDisplay>(loc, EntityType.TEXT_DISPLAY).apply {
            billboard = Display.Billboard.FIXED
            text = SWITCH_HINT_TEXT
            isSeeThrough = false
            isShadowed = true
            scaleTo(0.6f)
        }
        return hint.tag(parentUuid, GachaStandKeys.INDEX_SWITCH_HINT)
    }

    private fun spawnPreview(baseLoc: Location, yaw: Float, parentUuid: String): String {
        val loc = baseLoc.clone().add(0.0, 2.5 + lift, 0.0).apply { this.yaw = yaw }

        val preview = spawnEntity<ItemDisplay>(loc, EntityType.ITEM_DISPLAY).apply {
            billboard = Display.Billboard.FIXED
            displayWidth = 1.2f
            displayHeight = 1.2f
            setItemStack(ItemStack(Material.STICK))
        }
        return preview.tag(parentUuid, GachaStandKeys.INDEX_PREVIEW_ITEM)
    }

    private fun spawnRarityTag(baseLoc: Location, yaw: Float, parentUuid: String): String {
        val loc = baseLoc.clone().add(0.0, 1.40 + lift, 0.0).apply { this.yaw = yaw }

        val tag = spawnEntity<TextDisplay>(loc, EntityType.TEXT_DISPLAY).apply {
            billboard = Display.Billboard.FIXED
            text = ""
            isSeeThrough = false
            isShadowed = true
            scaleTo(0.65f)
        }
        return tag.tag(parentUuid, GachaStandKeys.INDEX_RARITY_TAG)
    }

    private fun spawnInteraction(
        loc: Location,
        width: Float,
        height: Float,
        parentUuid: String,
        index: Int
    ): String {
        val interaction = spawnEntity<Interaction>(loc, EntityType.INTERACTION).apply {
            interactionWidth = width
            interactionHeight = height
        }
        return interaction.tag(parentUuid, index)
    }

    // ------------------------------------------------------------------
    // ヘルパー
    // ------------------------------------------------------------------

    private inline fun <reified T : Entity> spawnEntity(loc: Location, type: EntityType): T =
        loc.world!!.spawnEntity(loc, type) as T

    /** 親 UUID とインデックスを PDC に書き、子の UUID 文字列を返す */
    private fun Entity.tag(parentUuid: String, index: Int): String {
        persistentDataContainer.set(GachaStandKeys.PARENT_TEXT_UUID, PersistentDataType.STRING, parentUuid)
        persistentDataContainer.set(GachaStandKeys.STAND_INDEX, PersistentDataType.INTEGER, index)
        return uniqueId.toString()
    }

    private fun TextDisplay.scaleTo(scale: Float) {
        val current = transformation
        transformation = Transformation(
            current.translation,
            current.leftRotation,
            Vector3f(scale, scale, scale),
            current.rightRotation
        )
    }
}