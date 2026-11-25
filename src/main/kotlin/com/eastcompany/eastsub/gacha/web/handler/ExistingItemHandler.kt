package com.eastcompany.eastsub.gacha.web.handler

import com.eastcompany.eastsub.gacha.ai.GeminiClient
import com.eastcompany.eastsub.gacha.model.ModelConfigManager
import com.eastcompany.eastsub.gacha.model.ModelRarity
import com.eastcompany.eastsub.gacha.model.RegisteredModelData
import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import com.eastcompany.eastsub.gacha.model.PackJsonUpdater
import com.eastcompany.eastsub.gacha.web.page.ExistingItemPage
import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * 既存の（resourcepack/assets 内に配置済みの）アイテムをガチャに登録するハンドラ。
 *
 *  GET  /add-existing         ページ表示
 *  GET  /add-existing/search  モデル(.json) / テクスチャ(.png) の検索
 *  GET  /add-existing/items   assets/＊/items/＊.json を解析した識別コード一覧
 *  GET  /add-existing/asset   サムネ画像のプレビュー
 *  POST /add-existing         登録
 */
class ExistingItemHandler(
    private val plugin: ResourcePackFlow,
    private val authManager: ResourcePackSessionManager,
    private val modelConfigManager: ModelConfigManager,
    @Volatile private var gemini: GeminiClient?
) : HttpHandler {

    private val nameCooldown = java.util.concurrent.ConcurrentHashMap<UUID, Long>()
    private val hintRegex = Regex("^[a-zA-Z0-9_.:/\\-]*$")

    private val assetsRoot = File(plugin.dataFolder, "resourcepack/assets")
    private val gson = Gson()
    private val packJsonUpdater = PackJsonUpdater(plugin)

    private val idRegex = Regex("^[a-zA-Z0-9_\\-]+$")
    private val codeRegex = Regex("^[a-zA-Z0-9_.:/\\-]+$")
    private val baseItemRegex = Regex("^[a-z0-9_]+$")
    private val locationRegex = Regex("^[a-z0-9_.-]+:[a-z0-9_./-]+$")
    private val nsRegex = Regex("^[a-z0-9_.-]*$")

    override fun handle(exchange: HttpExchange) {
        try {
            val token = parseCookie(exchange.requestHeaders.getFirst("Cookie"), "session_token")
            val playerUuid = authManager.getPlayerUuidBySession(token)
            if (playerUuid == null) {
                exchange.responseHeaders.set("Location", "/login")
                exchange.sendResponseHeaders(302, -1)
                return
            }

            val post = exchange.requestMethod.equals("POST", ignoreCase = true)
            when (exchange.requestURI.path.trimEnd('/')) {
                "/add-existing" -> if (post) handlePost(exchange, playerUuid) else handleGet(exchange, playerUuid)
                "/add-existing/search" -> handleSearch(exchange)
                "/add-existing/namespaces" -> handleNamespaces(exchange)     // ← 追加
                "/add-existing/items" -> handleItems(exchange, authManager.getPlayerName(playerUuid))
                "/add-existing/asset" -> handleAsset(exchange)
                "/add-existing/model-textures" -> handleModelTextures(exchange)
                "/add-existing/suggest-name" -> handleSuggestName(exchange, playerUuid)
                else -> sendText(exchange, 404, "Not Found")
            }
        } catch (e: Exception) {
            plugin.logger.warning("既存アイテム登録処理でエラー: ${e.message}")
            runCatching { sendText(exchange, 500, "Internal Server Error") }
        } finally {
            exchange.close()
        }
    }

    // ------------------------------------------------------------------
    // GET: ページ
    // ------------------------------------------------------------------

    private fun handleGet(exchange: HttpExchange, playerUuid: UUID) {
        val html = ExistingItemPage.render(authManager.getPlayerName(playerUuid)).toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, html.size.toLong())
        exchange.responseBody.use { it.write(html) }
    }

    // ------------------------------------------------------------------
    // GET: モデル / テクスチャ検索
    // ------------------------------------------------------------------

    private fun handleSearch(exchange: HttpExchange) {
        val q = parseForm(exchange.requestURI.rawQuery)
        val (folder, ext) = when (q["type"]) {
            "model" -> "models" to ".json"
            "texture" -> "textures" to ".png"
            else -> return sendText(exchange, 400, "type invalid")
        }
        val ns = q["ns"].orEmpty().takeIf { it.matches(nsRegex) }
            ?: return sendText(exchange, 400, "ns invalid")
        val terms = q["q"].orEmpty().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

        val results = if (!assetsRoot.isDirectory) emptyList() else {
            val root = assetsRoot.toPath()
            assetsRoot.walkTopDown()
                .filter { it.isFile && it.name.endsWith(ext, ignoreCase = true) }
                .map { root.relativize(it.toPath()).toString().replace(File.separatorChar, '/') }
                .filter { rel ->
                    val seg = rel.split('/')

                    // gacha_system を含むパスは除外
                    !rel.contains("gacha_system", ignoreCase = true) &&
                            seg.size >= 3 &&
                            seg[1] == folder &&
                            (ns.isEmpty() || seg[0] == ns) &&
                            terms.all { rel.lowercase().contains(it) }
                }
                .sorted()
                .take(200)
                .toList()
        }

        sendJson(exchange, results)
    }

    private fun handleNamespaces(exchange: HttpExchange) {
        val list = assetsRoot.listFiles { f -> f.isDirectory }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
        sendJson(exchange, list)
    }

    // ------------------------------------------------------------------
    // GET: サムネ画像
    // ------------------------------------------------------------------

    private fun handleAsset(exchange: HttpExchange) {
        val file = resolveAsset(parseForm(exchange.requestURI.rawQuery)["path"], ".png")
            ?: return sendText(exchange, 404, "Not Found")
        val bytes = file.readBytes()
        exchange.responseHeaders.set("Content-Type", "image/png")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    // ------------------------------------------------------------------
// GET: モデルJSONから対応するPNGを取得
// ------------------------------------------------------------------

    private fun handleModelTextures(exchange: HttpExchange) {
        val query = parseForm(exchange.requestURI.rawQuery)
        val modelPath = query["path"]

        val modelFile = resolveAsset(modelPath, ".json")
            ?: return sendText(exchange, 404, "Model not found")

        val textures = collectModelTextures(modelFile, mutableSetOf())

        sendJson(exchange, textures.distinct())
    }

    /**
     * モデルJSONの textures を読み取り、実在するPNGのassets相対パスを返す。
     * parentモデルも再帰的に読み込む。
     */
    private fun collectModelTextures(
        modelFile: File,
        visited: MutableSet<String>
    ): List<String> {
        val canonicalPath = modelFile.canonicalPath

        // parentの循環参照を防ぐ
        if (!visited.add(canonicalPath)) return emptyList()

        val json = try {
            JsonParser.parseString(
                modelFile.readText(StandardCharsets.UTF_8)
            ).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        // 親モデルのテクスチャを先に取得
        val parentTextures = mutableMapOf<String, String>()
        val parent = json.get("parent")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString

        if (!parent.isNullOrBlank()) {
            val parentFile = resolveModelReference(parent)
            if (parentFile != null) {
                // 親のtexturesを直接取得する
                val parentMap = collectModelTextureMap(parentFile, visited)
                parentTextures.putAll(parentMap)
            }
        }

        // 子モデルのtexturesで親の定義を上書き
        val ownTextures = json.get("textures")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject

        ownTextures?.entrySet()?.forEach { (key, value) ->
            if (value.isJsonPrimitive) {
                parentTextures[key] = value.asString
            }
        }

        // #layer0 のような参照を解決し、実在するPNGに変換
        return parentTextures.values
            .mapNotNull { resolveTextureReference(it, parentTextures) }
            .distinct()
    }

    /**
     * モデルのtexturesマップを親から継承して返す。
     */
    private fun collectModelTextureMap(
        modelFile: File,
        visited: MutableSet<String>
    ): Map<String, String> {
        val canonicalPath = modelFile.canonicalPath
        if (!visited.add(canonicalPath)) return emptyMap()

        val json = try {
            JsonParser.parseString(
                modelFile.readText(StandardCharsets.UTF_8)
            ).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: Exception) {
            null
        } ?: return emptyMap()

        val result = mutableMapOf<String, String>()

        val parent = json.get("parent")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString

        if (!parent.isNullOrBlank()) {
            resolveModelReference(parent)?.let { parentFile ->
                result.putAll(collectModelTextureMap(parentFile, visited))
            }
        }

        json.get("textures")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.entrySet()
            ?.forEach { (key, value) ->
                if (value.isJsonPrimitive) {
                    result[key] = value.asString
                }
            }

        return result
    }

    /**
     * "rlaiy:item/rod" -> assets/rlaiy/models/item/rod.json
     */
    private fun resolveModelReference(reference: String): File? {
        val namespace = if (reference.contains(':')) {
            reference.substringBefore(':')
        } else {
            "minecraft"
        }

        val path = reference.substringAfter(':')
        if (namespace.isBlank() || path.isBlank()) return null

        if (!namespace.matches(Regex("[a-z0-9_.-]+"))) return null
        if (!path.matches(Regex("[a-z0-9_./-]+"))) return null
        if (path.split('/').any { it == ".." || it == "." }) return null

        return resolveAsset("$namespace/models/$path.json", ".json")
    }

    /**
     * "rlaiy:item/rod" -> assets/rlaiy/textures/item/rod.png
     * "#layer0" のような参照にも対応する。
     */
    private fun resolveTextureReference(
        reference: String,
        textures: Map<String, String>,
        resolving: MutableSet<String> = mutableSetOf()
    ): String? {
        if (reference.startsWith("#")) {
            val key = reference.removePrefix("#")

            if (!resolving.add(key)) return null

            val next = textures[key] ?: return null
            return resolveTextureReference(next, textures, resolving)
        }

        val namespace = if (reference.contains(':')) {
            reference.substringBefore(':')
        } else {
            "minecraft"
        }

        val path = reference.substringAfter(':')

        if (!namespace.matches(Regex("[a-z0-9_.-]+"))) return null
        if (!path.matches(Regex("[a-z0-9_./-]+"))) return null
        if (path.split('/').any { it == ".." || it == "." }) return null

        val rel = "$namespace/textures/$path.png"

        return rel.takeIf { resolveAsset(it, ".png") != null }
    }

    /** assets 配下に実在するファイルだけを返す（パストラバーサル対策） */
    private fun resolveAsset(rel: String?, ext: String): File? {
        if (rel.isNullOrBlank() || !rel.endsWith(ext, ignoreCase = true)) return null
        val file = File(assetsRoot, rel).canonicalFile
        val rootPath = assetsRoot.canonicalPath + File.separator
        if (!file.path.startsWith(rootPath)) return null
        return file.takeIf { it.isFile }
    }

    /** "ns/models/a/b.json" -> "ns:a/b" */
    private fun toModelLocation(rel: String): String? {
        val seg = rel.split('/')
        if (seg.size < 3 || seg[1] != "models") return null
        return seg[0] + ":" + seg.drop(2).joinToString("/").removeSuffix(".json")
    }

    // ------------------------------------------------------------------
    // GET: items/*.json の解析（識別コードの自動検出）
    // ------------------------------------------------------------------

    private data class ItemEntry(
        val kind: String,          // "custom_model_data" | "item_model"
        val code: String,          // 識別コード
        val source: String,        // 例: "minecraft/items/stick.json"
        val baseItem: String?,     // CMD のみ
        val modelFile: String?,    // 例: "mypack/models/item/sword.json"（実在する場合のみ）
        val registered: Boolean,   // 自分が登録済みか
        val floatValue: Float? = null, // 数値(float)の CMD の場合の値。文字列 CMD は null
        val modelLocation: String? = null // 定義が指すモデルの場所（例: "minecraft:item/bow"）。assets に無くても入る
    )

    private fun handleItems(exchange: HttpExchange, playerName: String) {
        val q = parseForm(exchange.requestURI.rawQuery)
        val ns = q["ns"].orEmpty().takeIf { it.matches(nsRegex) }
            ?: return sendText(exchange, 400, "ns invalid")
        val terms = q["q"].orEmpty().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val registeredKeys = modelConfigManager.loadModelsByAuthor(playerName).values
            .mapNotNull { it.key }
            .toSet()

        val entries = scanItemDefinitions(registeredKeys)
            .filter { e ->
                ns.isEmpty() ||
                        e.source.substringBefore('/') == ns ||              // 定義ファイルがそのフォルダにある
                        e.modelLocation?.substringBefore(':') == ns         // 定義が指すモデルがそのフォルダにある
            }
            .filter { e ->
                val hay = (e.code + " " + e.source).lowercase()
                terms.all { hay.contains(it) }
            }
            .sortedWith(compareBy({ it.kind }, { it.code }))
            .take(300)

        sendJson(exchange, entries)
    }

    private fun scanItemDefinitions(registeredKeys: Set<String>): List<ItemEntry> {
        if (!assetsRoot.isDirectory) return emptyList()
        val root = assetsRoot.toPath()
        val result = mutableListOf<ItemEntry>()

        assetsRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
            .forEach { file ->
                val rel = root.relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                val seg = rel.split('/')
                if (seg.size < 3 || seg[1] != "items") return@forEach

                val ns = seg[0]
                val name = seg.drop(2).joinToString("/").removeSuffix(".json")
                // ガチャ用に自動生成されるパック定義は対象外
                // (例: key_pack_s / レア度なしの key_pack / pack_s そのもの)
                if (ns == "gacha_system" && isGeneratedPackName(name)) return@forEach

                val modelNode: JsonElement? = try {
                    JsonParser.parseString(file.readText(StandardCharsets.UTF_8)).asJsonObject.get("model")
                } catch (e: Exception) {
                    null
                }
                if (modelNode == null) return@forEach

                if (ns == "minecraft") {
                    // CMD: minecraft/items/<base>.json の select(custom_model_data) の各 when
                    if (!name.contains('/')) {
                        val cases = mutableListOf<Pair<String, JsonElement?>>()
                        collectCmdCases(modelNode, cases)
                        cases.distinctBy { it.first }.forEach { (code, node) ->
                            result += ItemEntry(
                                kind = "custom_model_data",
                                code = code,
                                source = rel,
                                baseItem = name,
                                modelFile = firstLeafModel(node)?.let(::toExistingModelFile),
                                modelLocation = firstLeafModel(node)?.let(::normalizeLocation),
                                registered = code in registeredKeys
                            )
                        }

                        // 数値(float)の CMD: range_dispatch(custom_model_data) の各 threshold
                        val floatEntries = mutableListOf<Pair<Float, JsonElement?>>()
                        collectCmdFloatEntries(modelNode, floatEntries)
                        floatEntries.distinctBy { it.first }.forEach { (value, node) ->
                            val code = "cmdf:$name:${formatFloat(value)}"
                            result += ItemEntry(
                                kind = "custom_model_data",
                                code = code,
                                source = rel,
                                baseItem = name,
                                modelFile = firstLeafModel(node)?.let(::toExistingModelFile),
                                modelLocation = firstLeafModel(node)?.let(::normalizeLocation),
                                registered = code in registeredKeys,
                                floatValue = value
                            )
                        }
                    }
                } else {
                    // ItemModel: ファイルの場所がそのままコードになる
                    val code = "$ns:$name"
                    result += ItemEntry(
                        kind = "item_model",
                        code = code,
                        source = rel,
                        baseItem = null,
                        modelFile = firstLeafModel(modelNode)?.let(::toExistingModelFile),
                        modelLocation = firstLeafModel(modelNode)?.let(::normalizeLocation),
                        registered = code in registeredKeys
                    )
                }
            }
        return result
    }

    /** パック名 (pack, pack_s, pack_a ...) は ModelRarity の packModelPath から求める */
    private val packNames: Set<String> =
        com.eastcompany.eastsub.gacha.model.ModelRarity.entries
            .map { it.packModelPath.substringAfter(':') }
            .toSet()

    /** ガチャ用に自動生成されたパック定義のファイル名か */
    private fun isGeneratedPackName(name: String): Boolean {
        val base = name.substringAfterLast('/')
        return packNames.any { base == it || base.endsWith("_$it") }
    }

    /** select(custom_model_data) の when 値と、その model 部分を再帰的に集める */
    private fun collectCmdCases(node: JsonElement?, out: MutableList<Pair<String, JsonElement?>>) {
        if (node == null || !node.isJsonObject) return
        val obj = node.asJsonObject
        val type = obj.get("type")?.asString?.removePrefix("minecraft:")
        val property = obj.get("property")?.asString?.removePrefix("minecraft:")

        if (type == "select" && property == "custom_model_data") {
            obj.get("cases")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { c ->
                val co = c.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val w = co.get("when")
                val values = when {
                    w == null -> emptyList()
                    w.isJsonArray -> w.asJsonArray.filter { it.isJsonPrimitive }.map { it.asString }
                    w.isJsonPrimitive -> listOf(w.asString)
                    else -> emptyList()
                }
                values.forEach { out += it to co.get("model") }
            }
        }
        childNodes(obj).forEach { collectCmdCases(it, out) }
    }

    /**
     * range_dispatch(property=custom_model_data, index=0) の entries を再帰的に集める。
     * floats[0] に入れるべき値は threshold / scale。
     * use_duration など他のプロパティの range_dispatch は対象外。
     */
    private fun collectCmdFloatEntries(node: JsonElement?, out: MutableList<Pair<Float, JsonElement?>>) {
        if (node == null || !node.isJsonObject) return
        val obj = node.asJsonObject
        val type = obj.get("type")?.asString?.removePrefix("minecraft:")
        val property = obj.get("property")?.asString?.removePrefix("minecraft:")
        val index = obj.get("index")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0

        if (type == "range_dispatch" && property == "custom_model_data" && index == 0) {
            val scale = obj.get("scale")?.takeIf { it.isJsonPrimitive }?.asFloat ?: 1f
            obj.get("entries")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { e ->
                val eo = e.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val threshold = eo.get("threshold")?.takeIf { it.isJsonPrimitive }?.asFloat ?: return@forEach
                val value = if (scale != 0f) threshold / scale else threshold
                out += value to eo.get("model")
            }
        }
        childNodes(obj).forEach { collectCmdFloatEntries(it, out) }
    }

    /** 1.0 -> "1"、1.5 -> "1.5" */
    private fun formatFloat(value: Float): String =
        if (value == Math.floor(value.toDouble()).toFloat()) value.toLong().toString() else value.toString()

    /** 最初に見つかった minecraft:model の model 値（"ns:path"）を返す */
    private fun firstLeafModel(node: JsonElement?): String? {
        if (node == null || !node.isJsonObject) return null
        val obj = node.asJsonObject
        val type = obj.get("type")?.asString?.removePrefix("minecraft:")
        if (type == "model") return obj.get("model")?.takeIf { it.isJsonPrimitive }?.asString
        for (child in childNodes(obj)) {
            firstLeafModel(child)?.let { return it }
        }
        return null
    }

    /** item 定義の入れ子をたどるための子ノード */
    private fun childNodes(obj: JsonObject): List<JsonElement> {
        val list = mutableListOf<JsonElement>()
        obj.get("model")?.let { list.add(it) }
        listOf("cases", "entries").forEach { key ->
            obj.get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { c ->
                if (c.isJsonObject) c.asJsonObject.get("model")?.let { list.add(it) }
            }
        }
        obj.get("models")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { list.add(it) }
        // condition は on_false（通常時）のモデルを優先する
        listOf("on_false", "on_true", "fallback").forEach { key -> obj.get(key)?.let { list.add(it) } }
        return list
    }

    /** "item/bow" -> "minecraft:item/bow"。名前空間が無ければ minecraft */
    private fun normalizeLocation(location: String): String =
        if (location.contains(':')) location else "minecraft:$location"

    /** "ns:path"（または "path"）→ "ns/models/path.json"。実ファイルがあるときだけ返す */
    private fun toExistingModelFile(location: String): String? {
        val ns = if (location.contains(':')) location.substringBefore(':') else "minecraft"
        val path = location.substringAfter(':')
        val rel = "$ns/models/$path.json"
        return rel.takeIf { resolveAsset(it, ".json") != null }
    }

    // ------------------------------------------------------------------
    // POST: 登録
    // ------------------------------------------------------------------

    private fun handlePost(exchange: HttpExchange, playerUuid: UUID) {
        val body = String(exchange.requestBody.readNBytes(64 * 1024), StandardCharsets.UTF_8)
        val f = parseForm(body)

        val playerName = authManager.getPlayerName(playerUuid)
        val modelId = f["modelId"]?.trim()?.lowercase().orEmpty()
        val itemName = f["itemName"]?.trim().orEmpty()
        val slotType = f["slotType"] ?: "custom_model_data"
        val code = f["code"]?.trim().orEmpty()
        val modelRel = f["modelFile"]?.trim()
        val thumbRel = f["thumbnailFile"]?.trim()?.ifBlank { null }
        val baseItem = f["baseItem"]?.trim()?.ifBlank { null } ?: "stick"
        val description = f["description"]?.trim()
        val canEquipHead = f["canEquipHead"] == "true"
        val cmdFloatRaw = f["cmdFloat"]?.trim()?.ifBlank { null }
        val cmdFloat: Float? = if (cmdFloatRaw == null) null else {
            cmdFloatRaw.toFloatOrNull()?.takeIf { it.isFinite() }
                ?: return sendText(exchange, 400, "CMD 数値が不正です。")
        }

        if (modelId.isBlank() || itemName.isBlank() || code.isBlank()) {
            return sendText(exchange, 400, "必須項目（モデルID、表示名、識別コード）が不足しています。")
        }
        if (!modelId.matches(idRegex)) {
            return sendText(exchange, 400, "モデルIDには半角英数字、アンダースコア(_)、ハイフン(-)のみ使用できます。")
        }
        if (!code.matches(codeRegex)) {
            return sendText(exchange, 400, "識別コードに使えない文字が含まれています。")
        }
        if (slotType != "custom_model_data" && slotType != "item_model") {
            return sendText(exchange, 400, "slotType が不正です。")
        }
        if (slotType == "custom_model_data" && !baseItem.matches(baseItemRegex)) {
            return sendText(exchange, 400, "ベースアイテムが不正です。")
        }
        if (cmdFloat != null && slotType != "custom_model_data") {
            return sendText(exchange, 400, "CMD 数値は CustomModelData のときだけ指定できます。")
        }
        val hasModelFile = !modelRel.isNullOrBlank()
        if (hasModelFile && resolveAsset(modelRel, ".json") == null) {
            return sendText(exchange, 400, "モデルファイルが見つかりません。")
        }
        if (thumbRel != null && resolveAsset(thumbRel, ".png") == null) {
            return sendText(exchange, 400, "サムネファイルが見つかりません。")
        }
        // モデルファイルを選んだ場合はそれを優先。無ければ、定義が指すモデルの場所（バニラ等）をそのまま使う
        val modelLocation: String = if (hasModelFile) {
            toModelLocation(modelRel!!)
                ?: return sendText(exchange, 400, "モデルは assets/<namespace>/models/ 配下から選んでください。")
        } else {
            f["modelLocation"]?.trim()?.takeIf { it.matches(locationRegex) }
                ?: return sendText(exchange, 400, "モデルファイルを選択してください。")
        }

        // deletable=false のため上書き（削除→再登録）はできない。重複は拒否する
        if (modelConfigManager.getModel(playerName, modelId) != null) {
            return sendText(exchange, 409, "同じモデルIDが既に登録されています。別のIDにしてください。")
        }
        val myModels = modelConfigManager.loadModelsByAuthor(playerName).values
        if (myModels.any { it.key == code }) {
            return sendText(exchange, 409, "同じ識別コードが既に登録されています。")
        }
        if (cmdFloat != null && myModels.any { it.customModelDataFloat == cmdFloat && it.baseItem == baseItem }) {
            return sendText(exchange, 409, "同じベースアイテム・同じ CMD 数値のモデルが既に登録されています。")
        }

        val data = RegisteredModelData(
            modelId = modelId,
            itemName = itemName,
            slotType = slotType,
            baseItem = if (slotType == "custom_model_data") baseItem else null,
            description = description,
            rarity = ModelRarity.UNCLASSIFIED,
            key = code,
            modelPath = if (hasModelFile) modelRel else null,
            itemPath = null,
            texturePaths = listOfNotNull(thumbRel),
            deletable = false,
            external = true,
            customModelDataFloat = cmdFloat
        )

        packJsonUpdater.addToAllRarities(code, modelLocation)

        if (!modelConfigManager.saveModel(playerName, data)) {
            return sendText(exchange, 500, "メタデータの保存に失敗しました。")
        }

        plugin.logger.info("既存アイテム ($code) をプレイヤー ($playerName) のモデル ($modelId) として登録しました。")
        exchange.responseHeaders.set("Location", "/edit?modelId=$modelId")
        exchange.sendResponseHeaders(302, -1)
    }


    // ------------------------------------------------------------------
    // ユーティリティ
    // ------------------------------------------------------------------

    private fun parseForm(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (pair in raw.split("&")) {
            val kv = pair.split("=", limit = 2)
            if (kv.size == 2) {
                map[URLDecoder.decode(kv[0], "UTF-8")] = URLDecoder.decode(kv[1], "UTF-8")
            }
        }
        return map
    }

    private fun parseCookie(header: String?, key: String): String? {
        if (header.isNullOrBlank()) return null
        for (c in header.split(";")) {
            val p = c.trim().split("=", limit = 2)
            if (p.size == 2 && p[0] == key) return p[1]
        }
        return null
    }

    private fun sendJson(exchange: HttpExchange, value: Any) {
        val bytes = gson.toJson(value).toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendText(exchange: HttpExchange, status: Int, msg: String) {
        val bytes = msg.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
    private fun handleSuggestName(exchange: HttpExchange, playerUuid: UUID) {
        val client = gemini
            ?: return sendText(exchange, 503, "AI機能は無効です(APIキー未設定)。")

        // 連打防止(APIの利用料金対策)
        val now = System.currentTimeMillis()
        if (now - (nameCooldown[playerUuid] ?: 0L) < 3000) {
            return sendText(exchange, 429, "少し待ってから再度お試しください。")
        }
        nameCooldown[playerUuid] = now

        val q = parseForm(exchange.requestURI.rawQuery)
        val code = q["code"]?.takeIf { it.matches(codeRegex) }
            ?: return sendText(exchange, 400, "識別コードが不正です。")
        val model = q["model"].orEmpty().takeIf { it.matches(hintRegex) }
            ?: return sendText(exchange, 400, "モデルが不正です。")
        val base = q["baseItem"].orEmpty().takeIf { it.matches(hintRegex) }
            ?: return sendText(exchange, 400, "ベースアイテムが不正です。")

        val prompt = buildString {
            appendLine("あなたはMinecraftのアイテムに表示名を付ける担当です。")
            appendLine("次の情報から、日本語の短い表示名を1つだけ考えてください。")
            appendLine("- 識別コード: $code")
            if (model.isNotBlank()) appendLine("- モデル: $model")
            if (base.isNotBlank()) appendLine("- ベースアイテム: $base")
            appendLine()
            append("条件: 16文字以内。名前だけを出力。説明・記号・引用符・色コードは付けない。")
        }

        val name = try {
            client.generate(prompt).get(20, java.util.concurrent.TimeUnit.SECONDS)
                .lineSequence().firstOrNull().orEmpty()
                .replace(Regex("[§&\"'`「」『』*#]"), "")
                .trim()
                .take(20)
        } catch (e: Exception) {
            plugin.logger.warning("AI名前提案に失敗: ${e.cause?.message ?: e.message}")
            return sendText(exchange, 502, "AIから名前を取得できませんでした。")
        }

        if (name.isBlank()) return sendText(exchange, 502, "AIの応答が空でした。")
        sendJson(exchange, mapOf("name" to name))
    }

    fun setGeminiClient(client: GeminiClient?) {
        gemini = client
    }
}