package com.eastcompany.eastsub.gacha.web.handler

import com.eastcompany.eastsub.gacha.model.GachaSettingManager
import com.eastcompany.eastsub.gacha.model.ModelConfigManager
import com.eastcompany.eastsub.gacha.model.ModelRarity
import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import com.eastcompany.eastsub.gacha.web.page.PackListPage
import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class PackListHandler(
    private val plugin: ResourcePackFlow,
    private val authManager: ResourcePackSessionManager,
    private val modelConfigManager: ModelConfigManager,
    private val settingManager: GachaSettingManager
) : HttpHandler {

    companion object {
        /** ガチャ1回あたりの最低金額（画面側の min と合わせる） */
        const val MIN_PRICE = 10

        /** ガチャ1回あたりの最高金額（運営の方針に合わせて調整） */
        const val MAX_PRICE = 1_000_000
    }

    private val gson = Gson()

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
        if (exchange.requestMethod.equals("POST", ignoreCase = true)) {
            when (path) {
                "/list/update-rarity" -> handleUpdateRarity(exchange, playerUuid)
                "/list/update-weights" -> handleUpdateWeights(exchange, playerUuid)
                "/list/update-price" -> handleUpdatePrice(exchange, playerUuid)
                "/list/rebuild-pack" -> handleRebuildPack(exchange, playerUuid)
                else -> sendResponse(exchange, 404, "Not Found")
            }
        } else {
            handleGet(exchange, playerUuid)
        }
    }

    private fun handleGet(exchange: HttpExchange, playerUuid: java.util.UUID) {
        val playerName = authManager.getPlayerName(playerUuid)
        val modelsMap = modelConfigManager.loadModelsByAuthor(playerName)
        val modelsList = modelsMap.values.toList()

        // config/data/プレイヤー名.json から全設定（金額＋重み）をロード
        val settings = settingManager.loadSettings(playerName)

        val html = PackListPage.render(playerName, modelsList, settings.weights, settings.price)
            .toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, html.size.toLong())
        exchange.responseBody.use { it.write(html) }
    }

    /**
     * ガチャ金額（1回あたりのコスト）の更新処理
     * 数値でない・範囲外の値は保存せず 400 を返す。
     */
    private fun handleUpdatePrice(exchange: HttpExchange, playerUuid: java.util.UUID) {
        val playerName = authManager.getPlayerName(playerUuid)
        val requestBodyText = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val params = parseQueryParams(requestBodyText)

        val priceParsed = params["price"]?.toIntOrNull()
        if (priceParsed == null || priceParsed !in MIN_PRICE..MAX_PRICE) {
            sendResponse(exchange, 400, "ガチャ金額は ${MIN_PRICE}〜${MAX_PRICE} の数値で指定してください。")
            return
        }

        val saved = settingManager.savePrice(playerName, priceParsed)
        if (saved) {
            sendResponse(exchange, 200, "OK")
        } else {
            sendResponse(exchange, 500, "ガチャ金額の保存に失敗しました。")
        }
    }

    /**
     * 重み（Weight）一括保存の処理
     * 負の重み・存在しないレア度のキーは 400 を返す。
     */
    private fun handleUpdateWeights(exchange: HttpExchange, playerUuid: java.util.UUID) {
        val playerName = authManager.getPlayerName(playerUuid)
        val requestBodyText = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

        try {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            val weightsMap: Map<String, Int>? = gson.fromJson(requestBodyText, type)

            if (weightsMap == null) {
                sendResponse(exchange, 400, "リクエストが空です。")
                return
            }

            val valid = weightsMap.all { (key, weight) ->
                weight >= 0 && runCatching { ModelRarity.valueOf(key) }.isSuccess
            }
            if (!valid) {
                sendResponse(exchange, 400, "重みは0以上、キーは有効なレア度で指定してください。")
                return
            }

            val saved = settingManager.saveWeights(playerName, weightsMap)
            if (saved) {
                sendResponse(exchange, 200, "OK")
            } else {
                sendResponse(exchange, 500, "重み設定の保存に失敗しました。")
            }
        } catch (e: Exception) {
            sendResponse(exchange, 400, "不正なJSON形式です: ${e.message}")
        }
    }

    private fun handleUpdateRarity(exchange: HttpExchange, playerUuid: java.util.UUID) {
        val playerName = authManager.getPlayerName(playerUuid)
        val requestBodyText = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val params = parseQueryParams(requestBodyText)

        val modelId = params["modelId"]?.lowercase()
        val rarityRaw = params["rarity"]?.uppercase()

        if (modelId.isNullOrBlank() || rarityRaw.isNullOrBlank()) {
            sendResponse(exchange, 400, "パラメータが不足しています。")
            return
        }

        val newRarity = try {
            ModelRarity.valueOf(rarityRaw)
        } catch (e: IllegalArgumentException) {
            ModelRarity.UNCLASSIFIED
        }

        val updated = modelConfigManager.updateModelRarity(playerName, modelId, newRarity)
        if (updated) {
            sendResponse(exchange, 200, "OK")
        } else {
            sendResponse(exchange, 500, "レアリティ更新に失敗しました。")
        }
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

    private fun handleRebuildPack(exchange: HttpExchange, playerUuid: java.util.UUID) {
        val player = org.bukkit.Bukkit.getPlayer(playerUuid)
        if (player == null || !player.isOnline) {
            sendResponse(exchange, 400, "プレイヤーが見つかりません。")
            return
        }

        try {
            // resourcePackManager を通じてビルドと適用を実行
            // ※ 実際のプラグインのメソッド名に合わせて調整してください
            plugin.resourcePackManager.buildAndApplyTo(player)
            sendResponse(exchange, 200, "OK")
        } catch (e: Exception) {
            sendResponse(exchange, 500, "リソースパックのビルドエラー: ${e.message}")
        }
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