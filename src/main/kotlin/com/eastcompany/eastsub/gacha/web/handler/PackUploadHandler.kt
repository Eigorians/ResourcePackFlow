package com.eastcompany.eastsub.gacha.web.handler

import com.eastcompany.eastsub.gacha.model.ModelConfigManager
import com.eastcompany.eastsub.gacha.model.ModelRarity
import com.eastcompany.eastsub.gacha.model.RegisteredModelData
import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import com.eastcompany.eastsub.gacha.model.PackJsonUpdater
import com.eastcompany.eastsub.gacha.web.form.MultipartParser
import com.eastcompany.eastsub.gacha.web.page.PackUploadPage
import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import org.bukkit.Material
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class PackUploadHandler(
    private val plugin: ResourcePackFlow,
    private val authManager: ResourcePackSessionManager,
    private val modelConfigManager: ModelConfigManager
) : HttpHandler {

    private val maxFileSizeBytes: Long = 10 * 1024 * 1024 // 10MB

    /** リクエスト全体の上限（モデル1つ + 複数テクスチャを想定） */
    private val maxRequestBytes: Long = 64L * 1024 * 1024 // 64MB

    private companion object {
        val BASE_ITEM_REGEX = Regex("^[a-z0-9_]+$")
    }

    private val packJsonUpdater = PackJsonUpdater(plugin)

    override fun handle(exchange: HttpExchange) {
        val sessionToken = parseCookie(exchange.getRequestHeader("Cookie"), "session_token")
        val playerUuid = authManager.getPlayerUuidBySession(sessionToken)

        if (playerUuid == null) {
            exchange.responseHeaders.set("Location", "/login")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
            return
        }

        val path = exchange.requestURI.path
        if (path == "/upload/check-exists") {
            handleCheckExists(exchange, playerUuid)
            return
        }

        if (exchange.requestMethod.equals("POST", ignoreCase = true)) {
            handlePost(exchange, playerUuid)
        } else {
            handleGet(exchange, playerUuid)
        }
    }

    private fun handleCheckExists(exchange: HttpExchange, playerUuid: java.util.UUID) {
        val playerName = authManager.getPlayerName(playerUuid)
        val queryMap = parseQueryParams(exchange.requestURI.query)
        val modelId = queryMap["modelId"]?.lowercase()

        if (modelId.isNullOrBlank()) {
            sendResponse(exchange, 400, "modelId missing")
            return
        }

        val existingModel = modelConfigManager.getModel(playerName, modelId)
        val exists = existingModel != null

        val responseJson = """{"exists": $exists}"""
        val bytes = responseJson.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun handleGet(exchange: HttpExchange, playerUuid: java.util.UUID) {
        val playerName = authManager.getPlayerName(playerUuid)
        val html = PackUploadPage.render(playerName).toByteArray(Charsets.UTF_8)

        exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, html.size.toLong())
        exchange.responseBody.use { it.write(html) }
    }

    private fun handlePost(exchange: HttpExchange, playerUuid: java.util.UUID) {
        val contentType = exchange.getRequestHeader("Content-Type") ?: ""
        if (!contentType.contains("multipart/form-data")) {
            sendResponse(exchange, 400, "Invalid Request Content-Type")
            return
        }

        try {
            val boundary = MultipartParser.extractBoundary(contentType) ?: run {
                sendResponse(exchange, 400, "Boundary not found")
                return
            }

            // 申告サイズが上限を超えていれば読み込む前に拒否する
            val declaredLength = exchange.getRequestHeader("Content-Length")?.toLongOrNull()
            if (declaredLength != null && declaredLength > maxRequestBytes) {
                sendResponse(exchange, 413, "リクエストが大きすぎます（合計最大${maxRequestBytes / 1024 / 1024}MBまで）。")
                return
            }

            val multipartData = try {
                MultipartParser.parse(exchange.requestBody, boundary, maxRequestBytes)
            } catch (e: MultipartParser.PayloadTooLargeException) {
                sendResponse(exchange, 413, "リクエストが大きすぎます（合計最大${maxRequestBytes / 1024 / 1024}MBまで）。")
                return
            }

            val modelId = multipartData.getFormField("modelId")?.trim()
            val itemName = multipartData.getFormField("itemName")?.trim()
            val slotType = multipartData.getFormField("slotType") ?: "custom_model_data"
            val baseItem = multipartData.getFormField("baseItem")
            val description = multipartData.getFormField("description")?.trim()
            val canEquipHead = multipartData.getFormField("canEquipHead") == "true"
            val isOverwrite = multipartData.getFormField("overwrite") == "true"

            // レアリティの受取とフォールバック処理
            val rarityRaw = multipartData.getFormField("rarity")?.trim()?.uppercase()
            val rarity = try {
                if (rarityRaw != null) ModelRarity.valueOf(rarityRaw) else ModelRarity.UNCLASSIFIED
            } catch (e: IllegalArgumentException) {
                ModelRarity.UNCLASSIFIED
            }

            val modelFile = multipartData.getFile("modelFile")
            val textureFiles = multipartData.getFiles("textureFiles")

            if (modelId.isNullOrBlank() || itemName.isNullOrBlank()) {
                sendResponse(exchange, 400, "必須項目（モデルID、表示名）が不足しています。")
                return
            }

            if (modelFile == null && textureFiles.isEmpty()) {
                sendResponse(exchange, 400, "3Dモデル (.json) または テクスチャ (.png) のいずれかをアップロードしてください。")
                return
            }

            if (!modelId.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
                sendResponse(exchange, 400, "モデルIDには半角英数字、アンダースコア(_)、ハイフン(-)のみ使用できます。")
                return
            }

            if ((modelFile != null && modelFile.bytes.size > maxFileSizeBytes) || textureFiles.any { v -> v.bytes.size > maxFileSizeBytes }) {
                sendResponse(exchange, 400, "ファイルサイズが大きすぎます（1ファイル最大10MBまで）。")
                return
            }

            val targetBaseItem = if (baseItem.isNullOrBlank()) "stick" else baseItem.trim().lowercase()

            // ファイル名に使うため、パス区切りや ".." を含む値を拒否する
            if (slotType != "item_model" && !targetBaseItem.matches(BASE_ITEM_REGEX)) {
                sendResponse(exchange, 400, "ベースアイテム名には半角英小文字・数字・アンダースコア(_)のみ使用できます。")
                return
            }
            val safeModelId = modelId.lowercase()
            val playerName = authManager.getPlayerName(playerUuid)   // 表示・設定ファイル用（そのまま）
            val pathOwner = toPathOwner(playerName)                  // key・リソースパス用（小文字化）

            if (isOverwrite) {
                plugin.logger.info("モデル ($safeModelId) の上書きリクエストを受信したため、上書き元データをクリーンアップします。")
                modelConfigManager.deleteModel(playerName, safeModelId)
            }

            val uploadedTextureMap = textureFiles.associateBy {
                it.filename.substringBeforeLast('.').lowercase()
            }

            // --- 🔍 3Dモデル JSON の解析・生成 ---
            val finalJsonString: String
            val textureMap: Map<String, String>

            if (modelFile != null) {
                val originalJsonString = String(modelFile.bytes, StandardCharsets.UTF_8)
                finalJsonString = transformTexturePaths(originalJsonString, pathOwner)
                textureMap = extractTexturePathsFromJson(originalJsonString)

                val missingTextures = textureMap.keys.filter { reqName ->
                    val lowerName = reqName.lowercase()
                    val isVanilla = isVanillaMaterial(reqName)
                    val isUploaded = uploadedTextureMap.containsKey(lowerName)

                    !isVanilla && !isUploaded
                }

                if (missingTextures.isNotEmpty()) {
                    sendResponse(exchange, 400, "エラー: 3Dモデルが要求している以下のテクスチャが見つかりません: ${missingTextures.joinToString(", ")}")
                    return
                }
            } else {
                val primaryTexName = uploadedTextureMap.keys.firstOrNull() ?: safeModelId

                finalJsonString = """
                    {
                      "parent": "item/generated",
                      "textures": {
                        "layer0": "gacha:item/$pathOwner/$primaryTexName"
                      }
                    }
                """.trimIndent()

                textureMap = mapOf(primaryTexName to "gacha:item/$pathOwner/$primaryTexName")
            }

            // --- 📁 3Dモデル本体 (.json) の保存 ---
            val modelTargetDir = File(plugin.dataFolder, "resourcepack/assets/gacha/models/$pathOwner")
            if (!modelTargetDir.exists()) modelTargetDir.mkdirs()

            val destinationJsonFile = File(modelTargetDir, "$safeModelId.json")
            destinationJsonFile.writeText(finalJsonString, StandardCharsets.UTF_8)

            // 追加・更新するモデル識別キー（例: "gacha:steve/my_model"）
            val fullAddedKey = "gacha:$pathOwner/$safeModelId"

            // --- 📁 スロットタイプに応じた定義の処理 ---
            if (slotType == "item_model") {
                val itemModelTargetDir = File(plugin.dataFolder, "resourcepack/assets/gacha/items/$pathOwner")
                if (!itemModelTargetDir.exists()) itemModelTargetDir.mkdirs()

                val itemModelJsonFile = File(itemModelTargetDir, "$safeModelId.json")
                val itemModelJson = """
                    {
                      "model": {
                        "type": "minecraft:model",
                        "model": "$fullAddedKey"
                      }
                    }
                """.trimIndent()

                itemModelJsonFile.writeText(itemModelJson, StandardCharsets.UTF_8)
            } else {
                val minecraftItemsDir = File(plugin.dataFolder, "resourcepack/assets/minecraft/items")
                if (!minecraftItemsDir.exists()) minecraftItemsDir.mkdirs()

                val baseItemJsonFile = File(minecraftItemsDir, "$targetBaseItem.json")
                val gson = GsonBuilder().setPrettyPrinting().create()

                val rootObject: JsonObject = if (baseItemJsonFile.exists()) {
                    try {
                        val content = baseItemJsonFile.readText(StandardCharsets.UTF_8)
                        JsonParser.parseString(content).asJsonObject
                    } catch (e: Exception) {
                        createBaseSelectJson(targetBaseItem)
                    }
                } else {
                    createBaseSelectJson(targetBaseItem)
                }

                try {
                    val modelObj = if (rootObject.has("model") && rootObject.get("model").isJsonObject) {
                        rootObject.getAsJsonObject("model")
                    } else {
                        JsonObject().also { rootObject.add("model", it) }
                    }

                    val casesArray = if (modelObj.has("cases") && modelObj.get("cases").isJsonArray) {
                        modelObj.getAsJsonArray("cases")
                    } else {
                        JsonArray().also { modelObj.add("cases", it) }
                    }

                    val newCase = JsonObject().apply {
                        addProperty("when", fullAddedKey)
                        add("model", JsonObject().apply {
                            addProperty("type", "minecraft:model")
                            addProperty("model", fullAddedKey)
                        })
                    }

                    val iterator = casesArray.iterator()
                    while (iterator.hasNext()) {
                        val elem = iterator.next()
                        if (elem.isJsonObject) {
                            val whenVal = elem.asJsonObject.get("when")?.asString
                            if (whenVal == safeModelId || whenVal == fullAddedKey) {
                                iterator.remove()
                            }
                        }
                    }

                    casesArray.add(newCase)
                    baseItemJsonFile.writeText(gson.toJson(rootObject), StandardCharsets.UTF_8)

                } catch (e: Exception) {
                    plugin.logger.warning("JSONの構造操作中にエラーが発生しました: ${e.message}")
                    sendResponse(exchange, 500, "JSON Structure Processing Error")
                    return
                }
            }

            // ★【変更箇所】レアリティに関わらず、全てのパックJSON (pack.json, pack_a.json, pack_b.json等) を一括更新
            packJsonUpdater.addToAllRarities(fullAddedKey)

            val savedTexturePaths = mutableListOf<String>()

            for ((texName, originalPath) in textureMap) {
                val matchedFile = uploadedTextureMap[texName.lowercase()] ?: continue
                val subFolder = if (originalPath.contains("block")) "block" else "item"

                val textureTargetDir = File(plugin.dataFolder, "resourcepack/assets/gacha/textures/$subFolder/$pathOwner")
                if (!textureTargetDir.exists()) textureTargetDir.mkdirs()

                val destinationPngFile = File(textureTargetDir, "$texName.png")
                destinationPngFile.writeBytes(matchedFile.bytes)

                savedTexturePaths.add("gacha/textures/$subFolder/$pathOwner/$texName.png")
                plugin.logger.info("テクスチャを保存しました: ${destinationPngFile.absolutePath}")
            }

            // --- 📄 config/初回ユーザー名.json へメタデータの保存 ---
            // key は fullAddedKey（例: "gacha:steve/my_model"）をそのまま保存する
            val registeredModelData = RegisteredModelData(
                modelId = safeModelId,
                itemName = itemName,
                slotType = slotType,
                baseItem = if (slotType == "custom_model_data") targetBaseItem else null,
                description = description,
                rarity = rarity,
                key = fullAddedKey,
                modelPath = "gacha/models/$pathOwner/$safeModelId.json",
                itemPath = if (slotType == "item_model") "gacha/items/$pathOwner/$safeModelId.json" else "minecraft/items/$targetBaseItem.json",
                texturePaths = savedTexturePaths
            )

            val isConfigSaved = modelConfigManager.saveModel(playerName, registeredModelData)
            if (!isConfigSaved) {
                plugin.logger.warning("ファイル群は保存されましたが、config/$playerName.json へのメタデータ保存に失敗しました。")
            }

            plugin.logger.info("プレイヤー ($playerName) のjsonモデル・テクスチャ・設定メタデータを保存しました。")

            exchange.responseHeaders.set("Location", "/edit?modelId=$safeModelId")
            exchange.sendResponseHeaders(302, -1)
        } catch (e: Exception) {
            plugin.logger.warning("モデル・テクスチャ保存中にエラーが発生しました: ${e.message}")
            sendResponse(exchange, 500, "Internal Server Error")
        } finally {
            exchange.close()
        }
    }

    private fun createBaseSelectJson(targetBaseItem: String): JsonObject {
        return JsonObject().apply {
            add("model", JsonObject().apply {
                addProperty("type", "minecraft:select")
                addProperty("property", "minecraft:custom_model_data")
                addProperty("index", 0)
                add("cases", JsonArray())
                add("fallback", JsonObject().apply {
                    addProperty("type", "minecraft:model")
                    addProperty("model", "minecraft:item/$targetBaseItem")
                })
            })
        }
    }

    private fun extractTexturePathsFromJson(json: String): Map<String, String> {
        val results = mutableMapOf<String, String>()
        try {
            val texturesIndex = json.indexOf("\"textures\"")
            if (texturesIndex == -1) return results

            val braceStart = json.indexOf('{', texturesIndex)
            val braceEnd = json.indexOf('}', braceStart)
            if (braceStart == -1 || braceEnd == -1) return results

            val texturesBlock = json.substring(braceStart, braceEnd)

            val matcher = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"").findAll(texturesBlock)
            for (match in matcher) {
                val keyName = match.groupValues[1]
                val pathValue = match.groupValues[2]

                if (pathValue.startsWith("#")) continue

                var lastName = pathValue.substringAfterLast('/')
                if (lastName.contains(':')) {
                    lastName = lastName.substringAfterLast(':')
                }

                lastName = lastName.replace(Regex("(_particle|\\.particle)$"), "")

                if (lastName.isNotBlank() && lastName != "particle") {
                    results[lastName] = pathValue
                }
            }
        } catch (e: Exception) {
            // パース失敗時は空を返す
        }
        return results
    }

    private fun isVanillaMaterial(textureName: String): Boolean {
        if (textureName.startsWith("#")) {
            return true
        }

        val rawName = textureName.substringAfterLast('/')
        val cleanName = rawName.replace(Regex("(_particle|\\.particle)$"), "").uppercase()

        return Material.getMaterial(rawName.uppercase()) != null || Material.getMaterial(cleanName) != null
    }

    /**
     * key・リソースパス用の名前。リソース位置は [a-z0-9/._-] のみ有効なため、
     * 小文字化し、それ以外の文字は "_" に置き換える。
     */
    private fun toPathOwner(playerName: String): String =
        playerName.lowercase().replace(Regex("[^a-z0-9_.-]"), "_")

    private fun transformTexturePaths(json: String, pathOwner: String): String {
        try {
            val texturesIndex = json.indexOf("\"textures\"")
            if (texturesIndex == -1) return json

            val braceStart = json.indexOf('{', texturesIndex)
            val braceEnd = json.indexOf('}', braceStart)
            if (braceStart == -1 || braceEnd == -1) return json

            var texturesBlock = json.substring(braceStart, braceEnd)
            val originalBlock = texturesBlock

            val matcher = Regex("(\"[^\"]+\"\\s*:\\s*\")([^\"]+)(\")").findAll(texturesBlock)
            for (match in matcher) {
                val prefix = match.groupValues[1]
                val pathValue = match.groupValues[2]
                val suffix = match.groupValues[3]

                if (pathValue.startsWith("gacha:") || pathValue.startsWith("#")) continue

                val lastName = pathValue.substringAfterLast('/').let {
                    if (it.contains(':')) it.substringAfterLast(':') else it
                }

                if (isVanillaMaterial(lastName)) {
                    continue
                }

                if (lastName.isNotBlank()) {
                    val cleanFileName = lastName.replace(Regex("(_particle|\\.particle)$"), "")
                    val newPath = when {
                        pathValue.contains("item") -> "gacha:item/$pathOwner/$cleanFileName"
                        else -> "gacha:block/$pathOwner/$cleanFileName"
                    }
                    texturesBlock = texturesBlock.replace("$prefix$pathValue$suffix", "$prefix$newPath$suffix")
                }
            }

            return json.replace(originalBlock, texturesBlock)
        } catch (e: Exception) {
            plugin.logger.warning("JSONのテクスチャパス書き換えに失敗しました: ${e.message}")
        }
        return json
    }

    private fun parseQueryParams(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (param in query.split("&")) {
            val pair = param.split("=", limit = 2)
            if (pair.size == 2) {
                val key = URLDecoder.decode(pair[0], "UTF-8")
                val value = URLDecoder.decode(pair[1], "UTF-8")
                result[key] = value
            }
        }
        return result
    }

    private fun sendResponse(exchange: HttpExchange, statusCode: Int, message: String) {
        val bytes = message.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun parseCookie(cookieHeader: String?, key: String): String? {
        if (cookieHeader.isNullOrBlank()) return null
        for (cookie in cookieHeader.split(";")) {
            val parts = cookie.trim().split("=", limit = 2)
            if (parts.size == 2 && parts[0] == key) return parts[1]
        }
        return null
    }

    private fun HttpExchange.getRequestHeader(name: String): String? = this.requestHeaders.getFirst(name)
}