package com.eastcompany.eastsub.resourcePackFlow.config

import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.*

class PlayerSettingsManager(
    private val plugin: ResourcePackFlow
) {

    private val file = File(
        plugin.dataFolder,
        "players.yml"
    )

    private var config: YamlConfiguration

    init {
        // plugins/ResourcePackFlow/ が無ければ作成
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }

        // players.yml が無ければ作成
        if (!file.exists()) {
            file.createNewFile()
        }

        config = YamlConfiguration.loadConfiguration(file)
    }

    // =========================
    // Auto Apply
    // =========================

    fun getAutoApply(uuid: UUID): AutoApplyMode {
        val value = config.getString(
            "players.$uuid.auto-apply",
            AutoApplyMode.MESSAGE.configValue
        ) ?: AutoApplyMode.MESSAGE.configValue

        return AutoApplyMode.fromConfig(value)
    }

    fun setAutoApply(
        uuid: UUID,
        mode: AutoApplyMode
    ) {
        config.set(
            "players.$uuid.auto-apply",
            mode.configValue
        )

        save()
    }

    // =========================
    // Save / Reload
    // =========================

    fun save() {
        try {
            config.save(file)
        } catch (e: Exception) {
            plugin.logger.severe(
                "players.yml の保存に失敗しました: ${e.message}"
            )

            e.printStackTrace()
        }
    }

    fun getLastPackHash(uuid: UUID): String? {
        return config.getString(
            "players.$uuid.last-pack-hash"
        )
    }

    fun setLastPackHash(
        uuid: UUID,
        hash: String
    ) {
        config.set(
            "players.$uuid.last-pack-hash",
            hash
        )

        save()
    }

    // =========================
    // Addon Packs
    // =========================

    /**
     * プレイヤー自身が選んだON/OFF。選んでいなければ null（アドオンの初期状態に従う）。
     * 以前の excluded-addons に入っているものは、OFFを選んだものとして扱う。
     */
    fun getAddonChoice(uuid: UUID, id: String): Boolean? {
        val path = "players.$uuid.addons.$id"
        if (config.isBoolean(path)) return config.getBoolean(path)

        val excluded = config.getStringList("players.$uuid.excluded-addons")
        return if (id in excluded) false else null
    }

    fun setAddonChoice(uuid: UUID, id: String, enabled: Boolean) {
        config.set("players.$uuid.addons.$id", enabled)
        save()
    }

    fun reload() {
        config = YamlConfiguration.loadConfiguration(file)
    }
}