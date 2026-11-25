package com.eastcompany.eastsub.gacha.web.handler

import com.eastcompany.eastsub.gacha.api.VaultHook
import com.eastcompany.eastsub.gacha.item.ItemFilter
import com.eastcompany.eastsub.gacha.model.GachaSettingManager
import com.eastcompany.eastsub.gacha.model.ModelConfigManager
import com.eastcompany.eastsub.gacha.model.ModelRarity
import com.eastcompany.eastsub.gacha.model.PlayerGachaSettings
import com.eastcompany.eastsub.gacha.model.RegisteredModelData
import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import com.eastcompany.eastsub.gacha.web.form.BoolSetting
import com.eastcompany.eastsub.gacha.web.form.EnumSetting
import com.eastcompany.eastsub.gacha.web.form.ModelSetting
import com.eastcompany.eastsub.gacha.web.form.ModelSettings
import com.eastcompany.eastsub.gacha.web.page.PackSettingsIoPage
import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import org.bukkit.Material
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 設定の一括出力・取り込みページ (/settings-io)
 *  GET  /settings-io                      : ページ表示
 *  GET  /settings-io/export?modelIds=a,b  : 設定を JSON でダウンロード (modelIds 省略 = 全モデル)
 *  POST /settings-io/import?dryRun=true   : JSON を取り込む (dryRun=true なら検証のみで保存しない)
 *
 * 対象:
 *  - models        : [ModelSettings.all] の項目 + 表示名 / 説明 / 推奨アイテム
 *                    + "full": RegisteredModelData の全項目 (完全移行用)
 *  - gachaSettings : 作成者単位のガチャ設定 (値段 price / レア度ごとの重み weights)
 *
 * 取り込みモード (mode):
 *  - settings (既定) : 同じモデルIDの既存モデルだけ、設定項目を更新する
 *  - migrate         : "full" のデータでモデルを登録する (無ければ新規作成、あれば onExisting=skip|overwrite)
 *
 * リソースパックのファイル (テクスチャ・モデル JSON) 自体は出力・取り込みの対象外。
 * 取り込み先に同じファイルが置いてあることが前提。
 */
class PackSettingsIoHandler(
    private val plugin: ResourcePackFlow,
    private val authManager: ResourcePackSessionManager,
    private val modelConfigManager: ModelConfigManager,
    private val gachaSettingManager: GachaSettingManager
) : HttpHandler {

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    companion object {
        const val FORMAT_VERSION = 1
        private const val MAX_IMPORT_BYTES = 2 * 1024 * 1024   // 2MB
        private const val MAX_LISTED = 10                      // 結果に並べる詳細の最大件数
        private const val MAX_NAME_LENGTH = 200
        private const val MAX_DESCRIPTION_LENGTH = 2000
        private const val MAX_PATH_LENGTH = 300
        private const val MAX_TEXTURES = 100

        /** モデルID: ファイル名・パスに使われるため、安全な文字だけ許可する */
        private val MODEL_ID_REGEX = Regex("^[a-z0-9_.-]{1,100}$")
        private val KEY_REGEX = Regex("^[A-Za-z0-9_.:/-]{1,200}$")
    }

    private enum class FullResult { CREATED, OVERWRITTEN, UNCHANGED, EXISTS_SKIPPED, INVALID, FAILED }

    override fun handle(exchange: HttpExchange) {
        val sessionToken = parseCookie(exchange.requestHeaders.getFirst("Cookie"), "session_token")
        val playerUuid = authManager.getPlayerUuidBySession(sessionToken)

        if (playerUuid == null) {
            exchange.responseHeaders.set("Location", "/login")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
            return
        }

        val playerName = authManager.getPlayerName(playerUuid)
        val path = exchange.requestURI.path.trimEnd('/')
        val isPost = exchange.requestMethod.equals("POST", ignoreCase = true)

        when {
            !isPost && path.endsWith("/export") -> handleExport(exchange, playerName)
            isPost && path.endsWith("/import") -> handleImport(exchange, playerName)
            !isPost -> handlePage(exchange, playerName)
            else -> sendText(exchange, 405, "許可されていないメソッドです。")
        }
    }

    // ------------------------------------------------------------------
    // ページ
    // ------------------------------------------------------------------

    private fun handlePage(exchange: HttpExchange, playerName: String) {
        val models = modelConfigManager.loadModelsByAuthor(playerName).values.toList()
        val html = PackSettingsIoPage.render(playerName, models).toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, html.size.toLong())
        exchange.responseBody.use { it.write(html) }
    }

    // ------------------------------------------------------------------
    // 出力
    // ------------------------------------------------------------------

    private fun handleExport(exchange: HttpExchange, playerName: String) {
        val query = parseParams(exchange.requestURI.rawQuery)
        val all = modelConfigManager.loadModelsByAuthor(playerName)

        val wanted = query["modelIds"]
            ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotBlank() }?.toSet()

        val targets = all.values
            .filter { wanted == null || it.modelId.lowercase() in wanted }
            .sortedBy { it.modelId }

        val modelsJson = JsonObject()
        for (m in targets) {
            val obj = JsonObject()
            for (s in ModelSettings.all) {
                when (s) {
                    is BoolSetting -> obj.addProperty(s.key, s.get(m))
                    is EnumSetting -> obj.addProperty(s.key, s.getValue(m))
                }
            }
            // テキスト項目 (表示名の色コードは § のまま出力される)
            obj.addProperty("itemName", m.itemName)
            obj.addProperty("description", m.description ?: "")
            if (isItemModel(m.slotType)) obj.addProperty("recommendedItem", m.recommendedItem ?: "")
            // 完全移行用: データの全項目
            obj.add("full", fullJson(m))
            modelsJson.add(m.modelId, obj)
        }

        val root = JsonObject().apply {
            addProperty("version", FORMAT_VERSION)
            addProperty("author", playerName)
            addProperty("exportedAt", java.time.Instant.now().toString())
            add("gachaSettings", gachaSettingsJson(playerName))
            add("models", modelsJson)
        }

        val bytes = gson.toJson(root).toByteArray(StandardCharsets.UTF_8)
        val safeName = playerName.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)

        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.set(
            "Content-Disposition", "attachment; filename=\"gacha-settings-$safeName-$date.json\""
        )
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    /** RegisteredModelData の全項目 (null の項目は出力されない = 取り込み時は null 扱い) */
    private fun fullJson(m: RegisteredModelData): JsonObject = JsonObject().apply {
        addProperty("modelId", m.modelId)
        addProperty("itemName", m.itemName)
        addProperty("slotType", m.slotType)
        addProperty("baseItem", m.baseItem)
        addProperty("description", m.description)
        addProperty("rarity", m.rarity.name)
        addProperty("registeredAt", m.registeredAt)
        addProperty("key", m.key)
        addProperty("modelPath", m.modelPath)
        addProperty("itemPath", m.itemPath)
        add("texturePaths", JsonArray().also { arr -> m.texturePaths.forEach { arr.add(it) } })
        addProperty("deletable", m.deletable)
        addProperty("hiddenFromPreview", m.hiddenFromPreview)
        addProperty("dropAsItem", m.dropAsItem)
        addProperty("external", m.external)
        addProperty("customModelDataFloat", m.customModelDataFloat)
        addProperty("recommendedItem", m.recommendedItem)
        addProperty("hideEnchants", m.hideEnchants)
        addProperty("excludeFromPreview", m.excludeFromPreview)
    }

    private fun gachaSettingsJson(playerName: String): JsonObject {
        val s = gachaSettingManager.loadSettings(playerName)
        return JsonObject().apply {
            addProperty("price", s.price)
            add("weights", JsonObject().apply {
                s.weights.forEach { (rarity, weight) -> addProperty(rarity, weight) }
            })
        }
    }

    // ------------------------------------------------------------------
    // 取り込み
    // ------------------------------------------------------------------

    private fun handleImport(exchange: HttpExchange, playerName: String) {
        val params = parseParams(exchange.requestURI.rawQuery)
        val dryRun = params["dryRun"] == "true"
        val includeGacha = params["includeGacha"] != "false"
        val includeText = params["includeText"] != "false"
        val migrate = params["mode"] == "migrate"
        val overwriteExisting = params["onExisting"] == "overwrite"

        val raw = exchange.requestBody.readNBytes(MAX_IMPORT_BYTES + 1)
        if (raw.size > MAX_IMPORT_BYTES) {
            sendText(exchange, 413, "ファイルが大きすぎます (最大 2MB)。")
            return
        }

        val root: JsonObject = try {
            JsonParser.parseString(String(raw, StandardCharsets.UTF_8)).asJsonObject
        } catch (e: Exception) {
            sendText(exchange, 400, "JSON の形式が不正です。")
            return
        }

        val version = runCatching { root.get("version").asInt }.getOrNull()
        if (version != FORMAT_VERSION) {
            sendText(exchange, 400, "未対応の形式です (version=$version)。このページで出力したファイルを使ってください。")
            return
        }
        val models = runCatching { root.getAsJsonObject("models") }.getOrNull()
        val gachaSection = runCatching { root.getAsJsonObject("gachaSettings") }.getOrNull()
        if (models == null && gachaSection == null) {
            sendText(exchange, 400, "\"models\" も \"gachaSettings\" も見つかりません。")
            return
        }

        var updated = 0
        var created = 0
        var existsSkipped = 0
        var unchanged = 0
        var failed = 0
        val notFound = mutableListOf<String>()
        val invalid = mutableListOf<String>()
        val missingTextures = mutableListOf<String>()
        val skipped = linkedMapOf<ModelSetting, Int>()

        val sourceAuthor = runCatching { root.get("author").asString }.getOrNull()
        val authorMismatch = migrate && sourceAuthor != null && !sourceAuthor.equals(playerName, ignoreCase = true)

        val gachaReport = mutableListOf<String>()
        if (includeGacha && gachaSection != null) {
            if (!importGachaSettings(playerName, gachaSection, dryRun, gachaReport, invalid)) failed++
        }

        for ((rawId, element) in models?.entrySet().orEmpty()) {
            val modelId = rawId.trim().lowercase()

            if (!element.isJsonObject) {
                invalid += "$modelId (形式が不正)"
                continue
            }

            // 完全移行: "full" のデータでモデルを登録する (無ければ新規作成)
            if (migrate) {
                when (importFullModel(playerName, modelId, element.asJsonObject, overwriteExisting, dryRun, invalid, missingTextures)) {
                    FullResult.CREATED -> created++
                    FullResult.OVERWRITTEN -> updated++
                    FullResult.UNCHANGED -> unchanged++
                    FullResult.EXISTS_SKIPPED -> existsSkipped++
                    FullResult.FAILED -> failed++
                    FullResult.INVALID -> Unit   // 理由は invalid に追加済み
                }
                continue
            }

            val current: RegisteredModelData? = modelConfigManager.getModel(playerName, modelId)
            if (current == null) {
                notFound += modelId
                continue
            }

            val obj = element.asJsonObject
            // 型を明示する (推論だと RegisteredModelData? になり、ループ内の再代入で null 許容扱いになる)
            var next: RegisteredModelData = current

            if (includeText) {
                // 表示名 (必須・空は不可)
                val nameEl = obj.get("itemName")
                if (nameEl != null) {
                    val t = textOrNull(nameEl)?.trim()
                    if (t == null) invalid += "$modelId / 表示名 (文字列ではありません)"
                    else if (t.isBlank()) invalid += "$modelId / 表示名 (空にはできません)"
                    else if (t.length > MAX_NAME_LENGTH) invalid += "$modelId / 表示名 ($MAX_NAME_LENGTH 文字まで)"
                    else next = next.copy(itemName = t)
                }

                // 説明 (空文字は「説明なし」)
                val descEl = obj.get("description")
                if (descEl != null) {
                    val t = textOrNull(descEl)?.trim()
                    if (t == null) invalid += "$modelId / 説明 (文字列ではありません)"
                    else if (t.length > MAX_DESCRIPTION_LENGTH) invalid += "$modelId / 説明 ($MAX_DESCRIPTION_LENGTH 文字まで)"
                    else next = next.copy(description = t)
                }

                // 推奨アイテム (item_model のモデルのみ。空文字は未選択)
                val recEl = obj.get("recommendedItem")
                if (recEl != null && isItemModel(current.slotType)) {
                    val t = textOrNull(recEl)?.trim()
                    if (t == null) {
                        invalid += "$modelId / 推奨アイテム (文字列ではありません)"
                    } else if (t.isEmpty()) {
                        next = next.copy(recommendedItem = null)
                    } else {
                        val material = Material.getMaterial(t.uppercase())
                        if (material == null) invalid += "$modelId / 推奨アイテム = $t (存在しない素材)"
                        else next = next.copy(recommendedItem = material.name)
                    }
                }
            }

            for (s in ModelSettings.all) {
                val value = obj.get(s.key) ?: continue
                if (!value.isJsonPrimitive) {
                    invalid += "$modelId / ${s.label}"
                    continue
                }
                val text = value.asString
                if (!s.accepts(text)) {
                    invalid += "$modelId / ${s.label} = $text"
                    continue
                }
                if (!s.applicable(current)) {
                    skipped.merge(s, 1, Int::plus)
                    continue
                }
                next = s.apply(next, text)
            }

            if (next == current) {
                unchanged++
            } else if (dryRun) {
                updated++
            } else if (modelConfigManager.saveModel(playerName, next)) {
                updated++
            } else {
                failed++
                plugin.logger.warning("設定の取り込み: モデル保存に失敗しました ($playerName/$modelId)")
            }
        }

        val message = buildString {
            append(if (dryRun) "【検証のみ・保存していません】\n" else "")
            if (migrate) {
                append("${if (dryRun) "新規登録される予定" else "新規登録しました"}: $created 件\n")
            }
            append("${if (dryRun) "更新される予定" else "更新しました"}: $updated 件")
            append("\n変更なし: $unchanged 件")
            if (existsSkipped > 0) append("\n同じIDのモデルが既にあるためスキップ: $existsSkipped 件")
            gachaReport.forEach { append("\n$it") }
            if (authorMismatch) {
                append("\n※ 出力元の作成者は「$sourceAuthor」、取り込み先は「$playerName」です。")
                append("\n  データ内のファイルのパスは変換しません。ファイルは出力元と同じ場所に置いてください。")
            }
            if (missingTextures.isNotEmpty()) {
                append("\nテクスチャのファイルが見つかりません: ${missingTextures.size} 件 (${listed(missingTextures)})")
                append("\n  → 取り込み先にリソースパックのファイルを置いてから、パック更新をしてください。")
            }
            skipped.forEach { (s, n) -> append("\n「${s.label}」は変更できないモデルが $n 件あったためスキップ") }
            if (notFound.isNotEmpty()) {
                append("\n見つからないモデル: ${notFound.size} 件 (${listed(notFound)})")
            }
            if (invalid.isNotEmpty()) {
                append("\n不正な値: ${invalid.size} 件 (${listed(invalid)})")
            }
            if (failed > 0) append("\n保存に失敗: $failed 件")
        }
        sendText(exchange, if (failed > 0) 500 else 200, message)
    }

    // ------------------------------------------------------------------
    // 完全移行
    // ------------------------------------------------------------------

    private fun importFullModel(
        playerName: String,
        modelId: String,
        obj: JsonObject,
        overwrite: Boolean,
        dryRun: Boolean,
        invalid: MutableList<String>,
        missingTextures: MutableList<String>
    ): FullResult {
        val full = obj.get("full")
        if (full == null || !full.isJsonObject) {
            invalid += "$modelId (完全データがありません。新しい版のこのページで出力し直してください)"
            return FullResult.INVALID
        }
        val data = parseFullModel(modelId, full.asJsonObject, invalid) ?: return FullResult.INVALID

        val current: RegisteredModelData? = modelConfigManager.getModel(playerName, modelId)
        if (current != null && !overwrite) return FullResult.EXISTS_SKIPPED

        data.texturePaths.filter { !textureExists(it) }.forEach { missingTextures += "$modelId: $it" }

        if (current == data) return FullResult.UNCHANGED

        val result = if (current == null) FullResult.CREATED else FullResult.OVERWRITTEN
        if (dryRun) return result
        if (modelConfigManager.saveModel(playerName, data)) return result

        plugin.logger.warning("設定の取り込み: モデルの保存に失敗しました ($playerName/$modelId)")
        return FullResult.FAILED
    }

    /**
     * "full" を検証して RegisteredModelData にする。不正なら invalid に理由を足して null。
     * パスは後でファイルの読み書き・削除に使われるため、".." などを含むものは拒否する。
     */
    private fun parseFullModel(modelId: String, o: JsonObject, invalid: MutableList<String>): RegisteredModelData? {
        fun bad(what: String): RegisteredModelData? {
            invalid += "$modelId / $what"
            return null
        }

        if (!MODEL_ID_REGEX.matches(modelId) || modelId.contains("..")) return bad("モデルIDに使えない文字があります")

        val declaredId = optText(o, "modelId")
        if (declaredId != null && !declaredId.equals(modelId, ignoreCase = true)) return bad("modelId が一致しません ($declaredId)")

        // 文字列でなければならない項目 (null / 未指定は可)
        for (k in listOf("baseItem", "description", "key", "modelPath", "itemPath", "recommendedItem")) {
            if (isBadText(o, k)) return bad("$k (文字列ではありません)")
        }

        // baseItem はファイル名 (minecraft/items/<baseItem>.json) に使われるため厳密に検証する
        val importedBaseItem = optText(o, "baseItem")?.trim()
        if (!importedBaseItem.isNullOrEmpty() && !Regex("^[a-z0-9_]+$").matches(importedBaseItem)) {
            return bad("baseItem が不正 ($importedBaseItem)")
        }

        val itemName = optText(o, "itemName")?.trim()
        if (itemName.isNullOrBlank()) return bad("表示名がありません")
        if (itemName.length > MAX_NAME_LENGTH) return bad("表示名 ($MAX_NAME_LENGTH 文字まで)")

        val slotType = optText(o, "slotType")?.trim()?.lowercase()
        if (slotType != "custom_model_data" && slotType != "item_model") return bad("slotType が不正 ($slotType)")

        val rarityText = optText(o, "rarity")?.trim()?.uppercase()
        val rarity = rarityText?.let { runCatching { ModelRarity.valueOf(it) }.getOrNull() }
            ?: return bad("rarity が不正 ($rarityText)")

        val description = optText(o, "description")?.trim()
        if (description != null && description.length > MAX_DESCRIPTION_LENGTH) {
            return bad("説明 ($MAX_DESCRIPTION_LENGTH 文字まで)")
        }

        val key = optText(o, "key")?.trim()
        if (key != null && (!KEY_REGEX.matches(key) || key.contains(".."))) return bad("key に使えない文字があります ($key)")

        val modelPath = optText(o, "modelPath")?.trim()
        if (modelPath != null && !isSafePath(modelPath, allowColon = true)) return bad("modelPath が不正 ($modelPath)")
        val itemPath = optText(o, "itemPath")?.trim()
        if (itemPath != null && !isSafePath(itemPath, allowColon = true)) return bad("itemPath が不正 ($itemPath)")

        val textures = mutableListOf<String>()
        val texturesEl = o.get("texturePaths")
        if (texturesEl != null && !texturesEl.isJsonNull) {
            if (!texturesEl.isJsonArray || texturesEl.asJsonArray.size() > MAX_TEXTURES) return bad("texturePaths が不正")
            for (el in texturesEl.asJsonArray) {
                val p = textOrNull(el)?.trim()
                if (p == null || !isSafePath(p, allowColon = false)) return bad("texturePaths に不正なパスがあります ($el)")
                textures += p
            }
        }

        val recommended = optText(o, "recommendedItem")?.trim()?.takeIf { it.isNotEmpty() }
        if (recommended != null && Material.getMaterial(recommended.uppercase()) == null) {
            return bad("recommendedItem = $recommended (存在しない素材)")
        }

        val floatEl = o.get("customModelDataFloat")
        val cmdFloat: Float? = when {
            floatEl == null || floatEl.isJsonNull -> null
            floatEl.isJsonPrimitive && floatEl.asJsonPrimitive.isNumber -> floatEl.asFloat
            else -> return bad("customModelDataFloat が数値ではありません")
        }

        val registeredAtEl = o.get("registeredAt")
        val registeredAt: Long = when {
            registeredAtEl == null || registeredAtEl.isJsonNull -> System.currentTimeMillis()
            registeredAtEl.isJsonPrimitive && registeredAtEl.asJsonPrimitive.isNumber -> registeredAtEl.asLong
            else -> return bad("registeredAt が数値ではありません")
        }

        val data = RegisteredModelData(
            modelId = modelId,
            itemName = itemName,
            slotType = slotType,
            baseItem = optText(o, "baseItem")?.trim(),
            description = description,
            rarity = rarity,
            registeredAt = registeredAt,
            key = key,
            modelPath = modelPath,
            itemPath = itemPath,
            texturePaths = textures,
            deletable = optBool(o, "deletable", true),
            hiddenFromPreview = optBool(o, "hiddenFromPreview", false),
            dropAsItem = optBool(o, "dropAsItem", false),
            external = optBool(o, "external", false),
            customModelDataFloat = cmdFloat,
            recommendedItem = recommended?.let { Material.getMaterial(it.uppercase())?.name },
            hideEnchants = optBool(o, "hideEnchants", false),
            excludeFromPreview = optBool(o, "excludeFromPreview", false)
        )

        // 許可アイテム以外は常にパック排出 (既存データの移行処理と同じ扱い)
        return if (data.dropAsItem && !ItemFilter.canDropAsItem(data)) data.copy(dropAsItem = false) else data
    }

    private fun optText(o: JsonObject, key: String): String? {
        val e = o.get(key)
        return if (e == null || e.isJsonNull) null else textOrNull(e)
    }

    /** 値があるのに文字列ではない */
    private fun isBadText(o: JsonObject, key: String): Boolean {
        val e = o.get(key)
        return e != null && !e.isJsonNull && textOrNull(e) == null
    }

    private fun optBool(o: JsonObject, key: String, default: Boolean): Boolean {
        val e = o.get(key)
        return if (e != null && e.isJsonPrimitive && e.asJsonPrimitive.isBoolean) e.asBoolean else default
    }

    /** ファイルパスとして安全か (親ディレクトリ参照・絶対パス・バックスラッシュ・制御文字を拒否) */
    private fun isSafePath(p: String, allowColon: Boolean): Boolean {
        if (p.isBlank() || p.length > MAX_PATH_LENGTH) return false
        if (p.contains("..") || p.contains('\\') || p.startsWith("/")) return false
        if (!allowColon && p.contains(':')) return false
        return p.none { it.isISOControl() }
    }

    /** ハンドラの /edit/texture と同じ解決規則でテクスチャの有無を調べる */
    private fun textureExists(rel: String): Boolean = runCatching {
        val file = if (rel.startsWith("resourcepack/")) {
            File(plugin.dataFolder, rel)
        } else {
            File(File(plugin.dataFolder, "resourcepack"), "assets/$rel")
        }
        file.isFile
    }.getOrDefault(false)

    /**
     * 値段・重みの取り込み。指定された項目だけ上書きし、無い項目は現在値を維持する。
     * @return 保存に失敗したら false (検証のみのときは常に true)
     */
    private fun importGachaSettings(
        playerName: String,
        section: JsonObject,
        dryRun: Boolean,
        report: MutableList<String>,
        invalid: MutableList<String>
    ): Boolean {
        val current = gachaSettingManager.loadSettings(playerName)
        val minPrice = VaultHook.GachaConfig.DEFAULT_COST

        var price = current.price
        section.get("price")?.also { el ->
            val v = intOrNull(el)
            when {
                v == null -> invalid += "値段 (整数ではありません)"
                v < minPrice -> {
                    price = minPrice
                    report += "値段が $minPrice 未満のため $minPrice に補正しました"
                }
                else -> price = v
            }
        }

        var weights = current.weights
        section.get("weights")?.also { el ->
            if (!el.isJsonObject) {
                invalid += "重み (形式が不正)"
                return@also
            }
            val known = gachaSettingManager.getDefaultWeights().keys
            val merged = current.weights.toMutableMap()
            for ((rawKey, rawValue) in el.asJsonObject.entrySet()) {
                val key = rawKey.trim().uppercase()
                val v = intOrNull(rawValue)
                when {
                    key !in known -> invalid += "重み $rawKey (不明なレア度)"
                    v == null || v < 0 -> invalid += "重み $rawKey = $rawValue (0以上の整数のみ)"
                    else -> merged[key] = v
                }
            }
            if (merged.values.sum() <= 0) {
                invalid += "重み (合計が0になるため取り込みません)"
            } else {
                weights = merged
            }
        }

        val next = PlayerGachaSettings(price = price, weights = weights)
        if (next == current) {
            report += "値段・確率: 変更なし"
            return true
        }

        if (next.price != current.price) report += "値段: ${current.price} → ${next.price}"
        val changedWeights = next.weights.filter { (k, v) -> current.weights[k] != v }
        if (changedWeights.isNotEmpty()) {
            report += "重み: " + changedWeights.entries.joinToString(", ") { (k, v) ->
                "$k ${current.weights[k] ?: 0}→$v"
            }
        }

        if (dryRun) return true
        if (gachaSettingManager.saveSettings(playerName, next)) return true
        plugin.logger.warning("設定の取り込み: ガチャ設定の保存に失敗しました ($playerName)")
        report += "値段・確率の保存に失敗しました"
        return false
    }

    /** JSON の文字列ならその値、そうでなければ null */
    private fun textOrNull(el: JsonElement): String? =
        if (el.isJsonPrimitive && el.asJsonPrimitive.isString) el.asString else null

    private fun isItemModel(slotType: String?): Boolean =
        slotType.equals("item_model", ignoreCase = true)

    /** JSON の数値が整数ならその値、そうでなければ null (文字列の "100" も許可) */
    private fun intOrNull(el: JsonElement): Int? {
        if (!el.isJsonPrimitive) return null
        return runCatching {
            val d = el.asDouble
            if (d != Math.floor(d) || d > Int.MAX_VALUE || d < Int.MIN_VALUE) null else d.toInt()
        }.getOrNull()
    }

    private fun listed(items: List<String>): String {
        val head = items.take(MAX_LISTED).joinToString(", ")
        return if (items.size > MAX_LISTED) "$head ほか" else head
    }

    // ------------------------------------------------------------------
    // 共通
    // ------------------------------------------------------------------

    private fun parseParams(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (param in query.split("&")) {
            val pair = param.split("=", limit = 2)
            if (pair.size == 2) {
                result[URLDecoder.decode(pair[0], "UTF-8")] = URLDecoder.decode(pair[1], "UTF-8")
            }
        }
        return result
    }

    private fun sendText(exchange: HttpExchange, statusCode: Int, message: String) {
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
}