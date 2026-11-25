package com.eastcompany.eastsub.resourcePackFlow.config

import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/** 管理者が設定する追加パックの表示情報と初期状態 */
data class AddonInfo(
    val name: String,
    /** マテリアル名、または item_model のID (名前空間:パス) */
    val icon: String?,
    val defaultEnabled: Boolean,
    val description: List<String>
)

/** addons.yml の読み書き。未設定の追加パックは初期状態OFF */
class AddonSettingsManager(
    private val plugin: ResourcePackFlow
) {

    private val file = File(plugin.dataFolder, "addons.yml")

    private var config = YamlConfiguration()

    init {
        reload()
    }

    /**
     * ファイルから読み直す。
     * 書式が壊れている場合は、読み込み済みの内容を残して false を返す。
     */
    fun reload(): Boolean {
        val loaded = YamlConfiguration()

        if (file.exists()) {
            try {
                loaded.load(file)
            } catch (e: Exception) {
                plugin.logger.severe("addons.yml を読み込めませんでした: ${e.message}")
                return false
            }
        }

        config = loaded
        return true
    }

    fun get(id: String): AddonInfo {
        val path = "addons.$id"

        // 手で書く場合は1行の文字列でも受け付ける
        val description = if (config.isString("$path.description")) {
            config.getString("$path.description").orEmpty().lines()
        } else {
            config.getStringList("$path.description")
        }

        return AddonInfo(
            name = config.getString("$path.name")?.takeIf { it.isNotBlank() } ?: id,
            icon = config.getString("$path.icon")?.takeIf { it.isNotBlank() },
            defaultEnabled = config.getBoolean("$path.default", false),
            description = description
        )
    }

    fun setName(id: String, name: String?) = edit(id, "name", name)

    fun setIcon(id: String, icon: String?) = edit(id, "icon", icon)

    fun setDefaultEnabled(id: String, enabled: Boolean) = edit(id, "default", enabled)

    fun setDescription(id: String, lines: List<String>?) = edit(id, "description", lines)

    private fun edit(id: String, key: String, value: Any?): Boolean {
        // 手動編集を上書きしないよう、最新のファイルに対して変更する。壊れていれば保存しない
        if (!reload()) return false

        config.set("addons.$id.$key", value)

        return try {
            config.save(file)
            true
        } catch (e: Exception) {
            plugin.logger.severe("addons.yml の保存に失敗しました: ${e.message}")
            reload()
            false
        }
    }
}
