package com.eastcompany.eastsub.gacha.stand

import com.eastcompany.eastsub.gacha.api.VaultHook
import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager
import com.eastcompany.eastsub.gacha.model.GachaSettingManager
import com.eastcompany.eastsub.gacha.model.ModelConfigManager
import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.persistence.PersistentDataType
import java.util.*

/**
 * ガチャスタンドの窓口。生成・削除・再読み込みと、各部品の保持だけを行う。
 *
 * - 生成:       [GachaStandSpawner]
 * - 毎 tick:    [GachaStandTicker]
 * - 1 台の操作: [GachaStandController]
 * - 抽選:       [GachaDrawer]
 * - 景品付与:   [GachaRewardGiver]
 */
object GachaStandManager {

    private lateinit var repository: GachaStandRepository
    private lateinit var spawner: GachaStandSpawner
    private lateinit var ticker: GachaStandTicker

    lateinit var modelConfigManager: ModelConfigManager
        private set
    lateinit var gachaSettingManager: GachaSettingManager
        private set
    lateinit var drawer: GachaDrawer
        private set
    lateinit var plugin: ResourcePackFlow
        private set

    val rewardGiver = GachaRewardGiver()

    // 既存コードとの互換用エイリアス（新規コードは GachaStandKeys を直接使う）
    val KEY_IS_STAND_PARENT get() = GachaStandKeys.IS_STAND_PARENT
    val KEY_PARENT_TEXT_UUID get() = GachaStandKeys.PARENT_TEXT_UUID
    val KEY_STAND_INDEX get() = GachaStandKeys.STAND_INDEX

    const val INDEX_PREVIEW_ITEM = GachaStandKeys.INDEX_PREVIEW_ITEM
    const val INDEX_EXECUTE_BUTTON = GachaStandKeys.INDEX_EXECUTE_BUTTON
    const val INDEX_MANNEQUIN = GachaStandKeys.INDEX_MANNEQUIN
    const val INDEX_MANNEQUIN_TEXT = GachaStandKeys.INDEX_MANNEQUIN_TEXT

    lateinit var vaultHook: VaultHook
        private set

    fun init(
        plugin: ResourcePackFlow,
        modelConfigManager: ModelConfigManager,
        gachaSettingManager: GachaSettingManager,
        vaultHook: VaultHook
    ) {
        this.modelConfigManager = modelConfigManager
        this.gachaSettingManager = gachaSettingManager
        this.vaultHook = vaultHook
        this.plugin = plugin

        repository = GachaStandRepository(plugin)
        drawer = GachaDrawer(modelConfigManager, gachaSettingManager)
        spawner = GachaStandSpawner(modelConfigManager, gachaSettingManager)
        ticker = GachaStandTicker(plugin, repository)
        ticker.start()
    }

    /** プラグイン無効化時に呼ぶ */
    fun shutdown() {
        if (::ticker.isInitialized) ticker.stop()
    }

    fun resetStandTimer(parentUuid: UUID) {
        ticker.resetIdleTimer(parentUuid)
    }

    fun createGachaStand(player: Player) {
        val baseLoc = player.location.block.location.add(0.5, 0.0, 0.5)
        val snappedYaw = Math.round(player.location.yaw / 90.0f) * 90.0f
        baseLoc.yaw = snappedYaw
        baseLoc.pitch = 0.0f

        val parentUuid = spawner.spawn(baseLoc, snappedYaw, player) ?: return
        repository.saveStand(parentUuid, baseLoc)
        ticker.invalidate()
    }

    fun reloadStands() {
        repository.loadConfig()

        for (record in repository.loadAllStands()) {
            val world = Bukkit.getWorld(record.worldName) ?: continue

            val loc = Location(
                world,
                record.x,
                record.y,
                record.z,
                record.yaw,
                0.0f
            )

            loc.chunk.load()

            val parentEntity = Bukkit.getEntity(record.parentTextUuid)
            if (parentEntity != null && parentEntity.isValid) continue

            // 新しいスタンドを生成
            val newParentUuid = spawner.spawn(loc, record.yaw) ?: continue

            val parent = Bukkit.getEntity(newParentUuid) as? TextDisplay
            if (parent == null) {
                continue
            }

            val pdc = parent.persistentDataContainer

            if (record.isPersonal) {
                pdc.set(
                    GachaStandKeys.IS_PERSONAL_STAND,
                    PersistentDataType.BYTE,
                    GachaStandKeys.FLAG_ON
                )

                record.ownerUuid?.let { ownerUuid ->
                    pdc.set(
                        GachaStandKeys.CURRENT_AUTHOR_UUID,
                        PersistentDataType.STRING,
                        ownerUuid.toString()
                    )
                }

                record.authorName?.let { authorName ->
                    pdc.set(
                        GachaStandKeys.CURRENT_AUTHOR,
                        PersistentDataType.STRING,
                        authorName
                    )
                }

                // 所有者のモデルを復元
                record.ownerUuid?.let { ownerUuid ->
                    val controller = GachaStandController(newParentUuid)

                    val firstModel = modelConfigManager
                        .loadModelsByPlayerUuid(ownerUuid)
                        .values
                        .firstOrNull { it.rarity.isDrawable() }

                    if (firstModel != null) {
                        controller.applyModelData(firstModel)
                    } else {
                        controller.showNoModels(record.authorName ?: "Unknown")
                    }
                }
            }

            // 古い登録を削除して新しいUUIDで保存
            repository.removeStandByParentUuid(record.parentTextUuid)

            repository.saveStand(
                parentUuid = newParentUuid,
                location = loc,
                isPersonal = record.isPersonal,
                ownerUuid = record.ownerUuid,
                authorName = record.authorName
            )
        }
        ticker.invalidate()
    }

    fun removeGachaStand(parentTextEntity: Entity) {
        val parentPdc = parentTextEntity.persistentDataContainer
        if (!parentPdc.has(GachaStandKeys.IS_STAND_PARENT, PersistentDataType.BYTE)) return

        val parentUuid = parentTextEntity.uniqueId
        ticker.forget(parentUuid)

        parentPdc.get(GachaStandKeys.CHILD_UUIDS, PersistentDataType.STRING)
            ?.split(",")
            ?.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.forEach { Bukkit.getEntity(it)?.remove() }

        parentTextEntity.remove()
        repository.removeStandByParentUuid(parentUuid)
        ticker.invalidate()
    }

    fun removeGachaStandFromChildEntity(childEntity: Entity) {
        val parentUuid = childEntity.persistentDataContainer
            .get(GachaStandKeys.PARENT_TEXT_UUID, PersistentDataType.STRING)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return

        val parentEntity = Bukkit.getEntity(parentUuid)
        if (parentEntity != null) {
            removeGachaStand(parentEntity)
        } else {
            childEntity.remove()
        }
    }
    /**
     * 自分専用のガチャスタンドを生成
     */
    fun createMyGachaStand(player: Player, authManager: ResourcePackSessionManager) {
        val baseLoc = player.location.block.location.add(0.5, 0.0, 0.5)

        val snappedYaw = Math.round(player.location.yaw / 90.0f) * 90.0f
        baseLoc.yaw = snappedYaw
        baseLoc.pitch = 0.0f

        // 作成者を指定して生成
        val parentUuid = spawner.spawn(baseLoc, snappedYaw, player) ?: return

        val parent = Bukkit.getEntity(parentUuid) as? TextDisplay
        if (parent == null) {
            // 親エンティティを取得できない場合は保存しない
            return
        }

        val pdc = parent.persistentDataContainer

        // 個人用スタンドとして登録
        pdc.set(
            GachaStandKeys.IS_PERSONAL_STAND,
            PersistentDataType.BYTE,
            GachaStandKeys.FLAG_ON
        )

        pdc.set(
            GachaStandKeys.CURRENT_AUTHOR,
            PersistentDataType.STRING,
            authManager.getPlayerName(player.uniqueId)
        )

        pdc.set(
            GachaStandKeys.CURRENT_AUTHOR_UUID,
            PersistentDataType.STRING,
            player.uniqueId.toString()
        )

        // YAMLにも個人用情報を保存
        repository.saveStand(
            parentUuid = parentUuid,
            location = baseLoc,
            isPersonal = true,
            ownerUuid = player.uniqueId,
            authorName = authManager.getPlayerName(player.uniqueId)
        )

        ticker.invalidate()
    }

}