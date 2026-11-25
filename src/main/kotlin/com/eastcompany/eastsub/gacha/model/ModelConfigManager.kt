package com.eastcompany.eastsub.gacha.model

import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager
import com.eastcompany.eastsub.gacha.item.ItemFilter
import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ModelConfigManager(
    private val plugin: ResourcePackFlow,
    private val sessionManager: ResourcePackSessionManager
) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val configDir = File("${plugin.dataFolder}/resourcepack", "config")

    // 著者名ベースのキャッシュ: ConcurrentHashMap<authorName, MutableMap<modelId, RegisteredModelData>>
    private val cache = ConcurrentHashMap<String, MutableMap<String, RegisteredModelData>>()

    init {
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
    }

    /**
     * ★ 元通りの仕様を維持
     * ユーザー名 (初回名) の config/名前.json から Map 形式で全モデルデータを読み込む
     */
    @Synchronized
    fun loadModelsByAuthor(authorName: String): MutableMap<String, RegisteredModelData> {
        if (authorName.isBlank()) return mutableMapOf()

        cache[authorName]?.let { return it }

        val file = File(configDir, "$authorName.json")
        if (!file.exists()) {
            val emptyMap = mutableMapOf<String, RegisteredModelData>()
            cache[authorName] = emptyMap
            return emptyMap
        }

        return try {
            val jsonText = file.readText(StandardCharsets.UTF_8)
            if (jsonText.isNotBlank()) {
                val type = object : TypeToken<MutableMap<String, RegisteredModelData>>() {}.type
                val map: MutableMap<String, RegisteredModelData> = gson.fromJson(jsonText, type) ?: mutableMapOf()

                val rawRoot = JsonParser.parseString(jsonText).asJsonObject
                var migrated = false
                for ((key, model) in map.toMap()) {
                    val raw = rawRoot.getAsJsonObject(key) ?: continue
                    if (raw.has("dropAsItem")) continue

                    // 旧フィールド dropAsPack からの移行 (dropAsItem = !dropAsPack)。
                    // どちらも無い古いデータは、従来の既定 (パック排出) を維持する。
                    val legacyDropAsPack = raw.get("dropAsPack")
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asBoolean
                        ?: true
                    map[key] = model.copy(dropAsItem = !legacyDropAsPack && ItemFilter.canDropAsItem(model))
                    migrated = true
                }
                if (migrated) {
                    try {
                        file.writeText(gson.toJson(map), StandardCharsets.UTF_8)
                    } catch (e: Exception) {
                        plugin.logger.warning("config/$authorName.json の dropAsItem 移行の書き込みに失敗しました: ${e.message}")
                    }
                }

                cache[authorName] = map
                map
            } else {
                val emptyMap = mutableMapOf<String, RegisteredModelData>()
                cache[authorName] = emptyMap
                emptyMap
            }
        } catch (e: Exception) {
            plugin.logger.warning("config/$authorName.json の読み込みに失敗しました: ${e.message}")
            val emptyMap = mutableMapOf<String, RegisteredModelData>()
            cache[authorName] = emptyMap
            emptyMap
        }
    }

    /**
     * ★ 元通りの仕様を維持
     * プレイヤーUUIDから users.json の初回名を取得し、モデル Map を読み込む
     */
    fun loadModelsByPlayerUuid(playerUuid: UUID): MutableMap<String, RegisteredModelData> {
        val authorName = sessionManager.getPlayerName(playerUuid)
        return loadModelsByAuthor(authorName)
    }

    /**
     * 指定した著者名 (authorName) と modelId から単一のモデルデータを取得する
     */
    fun getModel(authorName: String, modelId: String): RegisteredModelData? {
        if (authorName.isBlank() || modelId.isBlank()) return null
        val modelsMap = loadModelsByAuthor(authorName)
        return modelsMap[modelId.lowercase()]
    }

    /**
     * UUID から直接単一のモデルデータを取得する便利メソッド
     */
    fun getModelByPlayerUuid(playerUuid: UUID, modelId: String): RegisteredModelData? {
        val authorName = sessionManager.getPlayerName(playerUuid)
        return getModel(authorName, modelId)
    }

    /**
     * 指定した著者名 (authorName) の config/著者名.json にモデルを保存（追記・上書き）する
     */
    @Synchronized
    fun saveModel(authorName: String, data: RegisteredModelData): Boolean {
        if (authorName.isBlank()) return false

        try {
            val currentMap = loadModelsByAuthor(authorName)
            val key = data.modelId.lowercase()
            currentMap[key] = data

            val file = File(configDir, "$authorName.json")
            val jsonText = gson.toJson(currentMap)
            file.writeText(jsonText, StandardCharsets.UTF_8)

            cache[authorName] = currentMap
            return true
        } catch (e: Exception) {
            plugin.logger.warning("config/$authorName.json への書き込みに失敗しました: ${e.message}")
            return false
        }
    }

    /**
     * 高度なモデル物理削除機能
     * リソースパック内の関連モデルJSON、テクスチャPNG、CustomModelData定義箇所の特定除去を自動実行します。
     */
    @Synchronized
    fun deleteModel(authorName: String, modelId: String): Boolean {
        if (authorName.isBlank() || modelId.isBlank()) return false

        try {
            val currentMap = loadModelsByAuthor(authorName)
            val key = modelId.lowercase()
            val targetModel = currentMap[key] ?: return false

            // 削除ロック：deletable が false のモデルは削除不可
            // ただし既存アイテム登録(external)は、元ファイルを消さないので削除できる
            if (!targetModel.deletable && !targetModel.external) {
                plugin.logger.warning(
                    "モデル ($modelId) は削除がロックされているため削除できません。"
                )
                return false
            }

            // 1. リソースパック側のクリーンアップ
            if (targetModel.external) {
                // 既存アイテム登録: 元のモデル・テクスチャ・定義ファイルには一切触れない。
                // ガチャのパック定義 (gacha/items/pack_x.json) からだけ外す
                targetModel.key?.let { removeFromPackJsons(it) }
            } else {
                // アップロードしたモデル: 物理ファイル・定義のクリーンアップ処理実行
                cleanupResourcePackFiles(authorName, targetModel, currentMap)
            }

            // 2. メタデータ (config/著者名.json) からの削除
            currentMap.remove(key)
            val file = File(configDir, "$authorName.json")
            val jsonText = gson.toJson(currentMap)
            file.writeText(jsonText, StandardCharsets.UTF_8)

            cache[authorName] = currentMap
            if (targetModel.external) {
                plugin.logger.info(
                    "既存アイテム登録のモデル ($modelId) をガチャ一覧と config から削除しました。（元ファイルは残しています）"
                )
            } else {
                plugin.logger.info(
                    "モデルデータ ($modelId) および関連リソースファイルを完全に物理削除しました。"
                )
            }
            return true

        } catch (e: Exception) {
            plugin.logger.warning(
                "config/$authorName.json からの削除処理に失敗しました: ${e.message}"
            )
            return false
        }
    }

    /**
     * 削除対象モデルのリソースパック用物理ファイル（JSON / PNG）とケース定義箇所のクリーンアップ
     */
    private fun cleanupResourcePackFiles(
        authorName: String,
        target: RegisteredModelData,
        allModels: Map<String, RegisteredModelData>
    ) {
        val safeModelId = target.modelId.lowercase()

        // --- A. 3Dモデル本体 (.json) の削除 ---
        val modelFile = File(plugin.dataFolder, "resourcepack/assets/gacha/models/$authorName/$safeModelId.json")
        if (modelFile.exists()) {
            if (modelFile.delete()) {
                plugin.logger.info("モデル本体ファイル ($safeModelId.json) を削除しました。")
            }
        }

        // --- B. テクスチャ画像 (.png) の安全削除（他モデルで共有されている場合は保持） ---
        val otherModels = allModels.filterKeys { it != safeModelId }.values
        val otherUsedTextures = otherModels.flatMap { it.texturePaths }.toSet()

        for (textureRelPath in target.texturePaths) {
            if (!otherUsedTextures.contains(textureRelPath)) {
                val textureFile = File(plugin.dataFolder, "resourcepack/assets/$textureRelPath")
                if (textureFile.exists()) {
                    if (textureFile.delete()) {
                        plugin.logger.info("テクスチャファイル ($textureRelPath) を削除しました。")
                    }
                }
            } else {
                plugin.logger.info("テクスチャ ($textureRelPath) は他のモデルでも使用されているため保持します。")
            }
        }

        // --- C. スロットタイプ別のクリーンアップ処理 ---
        if (target.slotType.equals("item_model", ignoreCase = true)) {
            val itemModelFile = File(plugin.dataFolder, "resourcepack/assets/gacha/items/$authorName/$safeModelId.json")
            if (itemModelFile.exists()) {
                if (itemModelFile.delete()) {
                    plugin.logger.info("ItemModelラッパーファイル ($safeModelId.json) を削除しました。")
                }
            }
        } else {
            val baseItem = target.baseItem?.ifBlank { "stick" } ?: "stick"
            if (!baseItem.matches(Regex("^[a-z0-9_]+$"))) {
                plugin.logger.warning("不正な baseItem ($baseItem) のため、items JSON のクリーンアップをスキップしました。")
                return
            }
            val baseItemJsonFile = File(plugin.dataFolder, "resourcepack/assets/minecraft/items/$baseItem.json")

            if (baseItemJsonFile.exists()) {
                try {
                    val jsonContent = baseItemJsonFile.readText(StandardCharsets.UTF_8)
                    val rootObj = JsonParser.parseString(jsonContent).asJsonObject

                    if (rootObj.has("model") && rootObj.get("model").isJsonObject) {
                        val modelObj = rootObj.getAsJsonObject("model")
                        if (modelObj.has("cases") && modelObj.get("cases").isJsonArray) {
                            val casesArray = modelObj.getAsJsonArray("cases")
                            val targetWhenKey = "gacha:$authorName/$safeModelId"

                            val iterator = casesArray.iterator()
                            var removedCount = 0
                            while (iterator.hasNext()) {
                                val element = iterator.next()
                                if (element.isJsonObject) {
                                    val whenVal = element.asJsonObject.get("when")?.asString
                                    if (whenVal == targetWhenKey || whenVal == safeModelId) {
                                        iterator.remove()
                                        removedCount++
                                    }
                                }
                            }

                            if (removedCount > 0) {
                                if (casesArray.size() > 0) {
                                    baseItemJsonFile.writeText(gson.toJson(rootObj), StandardCharsets.UTF_8)
                                    plugin.logger.info("ベースアイテム定義 ($baseItem.json) からモデルエントリ ($targetWhenKey) を削除しました。")
                                } else {
                                    baseItemJsonFile.delete()
                                    plugin.logger.info("ベースアイテム定義 ($baseItem.json) の要素が空になったためファイルを削除しました。")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("ベースアイテム定義 ($baseItem.json) の特定箇所削除中にエラーが発生しました: ${e.message}")
                }
            }
        }
    }

    /**
     * 全レアリティのパック定義 (resourcepack/assets/gacha/items/pack_x.json) から、
     * when が whenKey の分岐だけを取り除く。元のアイテム・モデルのファイルは触らない。
     */
    private fun removeFromPackJsons(whenKey: String) {
        for (rarity in ModelRarity.entries) {
            val packFileName = rarity.packModelPath.substringAfter("gacha:")
            val packFile = File(plugin.dataFolder, "resourcepack/assets/gacha/items/$packFileName.json")
            if (!packFile.exists()) continue

            try {
                val root = JsonParser.parseString(packFile.readText(StandardCharsets.UTF_8)).asJsonObject
                val cases = root.getAsJsonObject("model")?.getAsJsonArray("cases") ?: continue

                var removed = 0
                val iterator = cases.iterator()
                while (iterator.hasNext()) {
                    val elem = iterator.next()
                    if (!elem.isJsonObject) continue
                    val w = elem.asJsonObject.get("when")
                    if (w != null && w.isJsonPrimitive && w.asString == whenKey) {
                        iterator.remove()
                        removed++
                    }
                }

                if (removed > 0) {
                    packFile.writeText(gson.toJson(root), StandardCharsets.UTF_8)
                    plugin.logger.info("パック定義 ($packFileName.json) からキー '$whenKey' を削除しました。")
                }
            } catch (e: Exception) {
                plugin.logger.warning("パック定義 ($packFileName.json) の更新中にエラーが発生しました: ${e.message}")
            }
        }
    }

    fun updateModelRarity(authorName: String, modelId: String, newRarity: ModelRarity): Boolean {
        val currentData = getModel(authorName, modelId) ?: return false
        val updatedData = currentData.copy(rarity = newRarity)
        return saveModel(authorName, updatedData)
    }

    @Synchronized
    fun reloadConfig() {
        cache.clear()
        plugin.logger.info("モデル設定のキャッシュをクリアしました。次回アクセス時に再読み込みします。")
    }
    fun getAllAuthorNames(): List<String> {
        val dir = configDir

        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }

        return dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()
    }
}