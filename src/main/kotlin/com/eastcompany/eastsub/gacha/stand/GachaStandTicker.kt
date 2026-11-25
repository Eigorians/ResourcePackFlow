package com.eastcompany.eastsub.gacha.stand

import com.eastcompany.eastsub.gacha.stand.GachaStandController.Companion.ROULETTE_INTERVAL_TICKS
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.TextDisplay
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import kotlin.math.sin

/**
 * 毎 tick 動くループを担当する。
 *
 * - 通常時: プレビューの回転と上下浮遊、10 秒ごとのモデル自動切り替え
 * - 実行時: プレビュー固定、2 tick ごとのルーレット演出、5 秒で自動停止
 */
class GachaStandTicker(
    private val plugin: JavaPlugin,
    private val repository: GachaStandRepository
) {

    private val idleTicks = ConcurrentHashMap<UUID, Int>()
    private val itemYaws = ConcurrentHashMap<UUID, Float>()
    private val hoverTicks = ConcurrentHashMap<UUID, Int>()

    // 毎 tick 読み直さないよう一定間隔でキャッシュする
    private var records = repository.loadAllStands()
    private var ticksSinceRefresh = 0
    private var needsRefresh = false
    private val hintHidden = ConcurrentHashMap.newKeySet<UUID>()

    private var task: BukkitTask? = null

    fun start() {
        task?.cancel()
        task = object : BukkitRunnable() {
            override fun run() = tick()
        }.runTaskTimer(plugin, 1L, 1L)
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    /** スタンドの追加・削除・再読み込みの後に呼ぶ */
    fun invalidate() {
        needsRefresh = true
    }

    fun resetIdleTimer(parentUuid: UUID) {
        idleTicks[parentUuid] = 0
    }

    fun forget(parentUuid: UUID) {
        idleTicks.remove(parentUuid)
        itemYaws.remove(parentUuid)
        hoverTicks.remove(parentUuid)
        hintHidden.remove(parentUuid)   // ★追加
    }

    private fun tick() {
        refreshRecordsIfNeeded()

        for (record in records) {
            tickStand(record.worldName, record.x, record.z, record.parentTextUuid)
        }
    }

    private fun refreshRecordsIfNeeded() {
        ticksSinceRefresh++
        if (!needsRefresh && ticksSinceRefresh < REFRESH_INTERVAL_TICKS) return

        needsRefresh = false
        ticksSinceRefresh = 0
        records = repository.loadAllStands()

        // 削除済みスタンドのタイマーを掃除する
        val alive = records.map { it.parentTextUuid }.toSet()
        idleTicks.keys.retainAll(alive)
        itemYaws.keys.retainAll(alive)
        hoverTicks.keys.retainAll(alive)
        hintHidden.retainAll(alive)
    }

    private fun tickStand(worldName: String, x: Double, z: Double, parentUuid: UUID) {
        val world = Bukkit.getWorld(worldName) ?: return

        // 負の座標でも正しいチャンクになるよう floor してからシフトする
        val chunkX = floor(x).toInt() shr 4
        val chunkZ = floor(z).toInt() shr 4
        if (!world.isChunkLoaded(chunkX, chunkZ)) return

        val controller = GachaStandController(parentUuid)
        val parent = controller.getParentEntity() ?: return
        val pdc = parent.persistentDataContainer

        val executing = pdc.get(GachaStandKeys.IS_EXECUTING, PersistentDataType.BYTE) == GachaStandKeys.FLAG_ON
        updateSwitchHint(parent, parentUuid, executing)   // ★追加
        if (executing) {
            tickExecuting(controller, pdc)
        } else {
            tickIdle(controller, parentUuid)
        }
    }

    // ------------------------------------------------------------------
    // 実行中
    // ------------------------------------------------------------------

    private fun tickExecuting(controller: GachaStandController, pdc: PersistentDataContainer) {
        val executeTick = (pdc.get(GachaStandKeys.EXECUTE_TICK, PersistentDataType.INTEGER) ?: 0) + 1
        pdc.set(GachaStandKeys.EXECUTE_TICK, PersistentDataType.INTEGER, executeTick)

        // 回転と浮遊を止める
        controller.getPreviewDisplay()?.let {
            applyPreviewTransform(it, yOffset = 0f, rotation = Quaternionf(), duration = 1)
        }

        if (executeTick % ROULETTE_INTERVAL_TICKS == 0) {
            controller.tickRoulette()
        }
        if (executeTick >= ROULETTE_DURATION_TICKS) {
            controller.stopGachaRoulette()
        }
    }

    // ------------------------------------------------------------------
    // 通常時
    // ------------------------------------------------------------------

    private fun tickIdle(controller: GachaStandController, parentUuid: UUID) {
        controller.getPreviewDisplay()?.let { preview ->
            val yaw = ((itemYaws[parentUuid] ?: 0f) + ROTATE_STEP_DEGREES) % 360f
            itemYaws[parentUuid] = yaw

            val hover = (hoverTicks[parentUuid] ?: 0) + 1
            hoverTicks[parentUuid] = hover

            val yOffset = (sin(hover * HOVER_SPEED) * HOVER_AMPLITUDE).toFloat()
            val rotation = Quaternionf(AxisAngle4f(Math.toRadians(yaw.toDouble()).toFloat(), 0f, 1f, 0f))

            applyPreviewTransform(preview, yOffset, rotation, duration = 2)
        }

        val tick = (idleTicks[parentUuid] ?: 0) + 1
        if (tick >= AUTO_SWITCH_TICKS) {
            idleTicks[parentUuid] = 0
            controller.switchToNextModelAuto()
        } else {
            idleTicks[parentUuid] = tick
        }
    }

    /** 実行中は空、通常時は案内文。状態が変わった tick にだけエンティティを探す */
    private fun updateSwitchHint(parent: Entity, parentUuid: UUID, executing: Boolean) {
        val isHidden = parentUuid in hintHidden
        if (executing == isHidden) return   // 状態が変わっていなければ何もしない

        val hint = findSwitchHint(parent) ?: return
        if (executing) {
            hint.text = ""
            hintHidden.add(parentUuid)
        } else {
            hint.text = GachaStandSpawner.SWITCH_HINT_TEXT
            hintHidden.remove(parentUuid)
        }
    }

    private fun findSwitchHint(parent: Entity): TextDisplay? =
        parent.persistentDataContainer
            .get(GachaStandKeys.CHILD_UUIDS, PersistentDataType.STRING)
            ?.split(",")
            ?.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.mapNotNull { Bukkit.getEntity(it) }
            ?.firstOrNull {
                it.persistentDataContainer.get(GachaStandKeys.STAND_INDEX, PersistentDataType.INTEGER) ==
                        GachaStandKeys.INDEX_SWITCH_HINT
            } as? TextDisplay


    private fun applyPreviewTransform(
        preview: ItemDisplay,
        yOffset: Float,
        rotation: Quaternionf,
        duration: Int
    ) {
        preview.interpolationDuration = duration
        preview.interpolationDelay = 0
        preview.transformation = Transformation(
            Vector3f(0f, yOffset, 0f),
            rotation,
            Vector3f(PREVIEW_SCALE, PREVIEW_SCALE, PREVIEW_SCALE),
            Quaternionf()
        )
    }

    private companion object {
        const val REFRESH_INTERVAL_TICKS = 100
        const val ROULETTE_DURATION_TICKS = 100
        const val AUTO_SWITCH_TICKS = 200
        const val ROTATE_STEP_DEGREES = 2.5f
        const val HOVER_SPEED = 0.06
        const val HOVER_AMPLITUDE = 0.20
        const val PREVIEW_SCALE = 1.2f
    }

}