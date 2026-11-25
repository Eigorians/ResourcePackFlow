package com.eastcompany.eastsub.gacha.stand

import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.*

data class GachaStandRecord(
    val id: String,
    val worldName: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val parentTextUuid: UUID,
    val isPersonal: Boolean = false,
    val ownerUuid: UUID? = null,
    val authorName: String? = null
)

class GachaStandRepository(private val plugin: JavaPlugin) {

    private val file = File(plugin.dataFolder, "stands.yml")
    private var config: YamlConfiguration = YamlConfiguration()

    init {
        loadConfig()
    }

    fun loadConfig() {
        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.saveResource("stands.yml", false)
        }
        config = YamlConfiguration.loadConfiguration(file)
    }

    /**
     * 新規スタンドの設置情報を保存する
     */
    fun saveStand(
        parentUuid: UUID,
        location: Location,
        isPersonal: Boolean = false,
        ownerUuid: UUID? = null,
        authorName: String? = null
    ) {
        val id = UUID.randomUUID().toString()
        val path = "stands.$id"

        config.set("$path.world", location.world?.name ?: "world")
        config.set("$path.x", location.x)
        config.set("$path.y", location.y)
        config.set("$path.z", location.z)
        config.set("$path.yaw", location.yaw)
        config.set("$path.parent_uuid", parentUuid.toString())

        // 個人用スタンド情報
        config.set("$path.personal", isPersonal)
        config.set("$path.owner_uuid", ownerUuid?.toString())
        config.set("$path.author_name", authorName)

        config.save(file)
    }

    /**
     * スタンド解体時に config から削除する
     */
    fun removeStandByParentUuid(parentUuid: UUID) {
        val standsSection = config.getConfigurationSection("stands") ?: return
        for (key in standsSection.getKeys(false)) {
            val uuidStr = config.getString("stands.$key.parent_uuid")
            if (uuidStr == parentUuid.toString()) {
                config.set("stands.$key", null)
                config.save(file)
                break
            }
        }
    }

    /**
     * 全ガチャスタンドのデータリストを取得する
     */
    fun loadAllStands(): List<GachaStandRecord> {
        val records = mutableListOf<GachaStandRecord>()
        val standsSection = config.getConfigurationSection("stands") ?: return records

        for (id in standsSection.getKeys(false)) {
            val path = "stands.$id"
            val world = config.getString("$path.world") ?: continue
            val x = config.getDouble("$path.x")
            val y = config.getDouble("$path.y")
            val z = config.getDouble("$path.z")
            val yaw = config.getDouble("$path.yaw").toFloat()
            val uuidStr = config.getString("$path.parent_uuid") ?: continue

            val isPersonal = config.getBoolean("$path.personal", false)
            val ownerUuidStr = config.getString("$path.owner_uuid")
            val ownerUuid = ownerUuidStr?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            }
            val authorName = config.getString("$path.author_name")

            records.add(
                GachaStandRecord(
                    id = id,
                    worldName = world,
                    x = x,
                    y = y,
                    z = z,
                    yaw = yaw,
                    parentTextUuid = UUID.fromString(uuidStr),
                    isPersonal = isPersonal,
                    ownerUuid = ownerUuid,
                    authorName = authorName
                )
            )
        }
        return records
    }
}