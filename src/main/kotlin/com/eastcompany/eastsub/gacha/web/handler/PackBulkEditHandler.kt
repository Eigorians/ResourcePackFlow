package com.eastcompany.eastsub.gacha.web.handler

import com.eastcompany.eastsub.gacha.model.ModelConfigManager
import com.eastcompany.eastsub.gacha.model.RegisteredModelData
import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import com.eastcompany.eastsub.gacha.web.form.ModelSetting
import com.eastcompany.eastsub.gacha.web.form.ModelSettings
import com.eastcompany.eastsub.gacha.web.page.PackBulkEditPage
import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 一括設定変更ページ (/bulk-edit)
 *  GET  : 自分の全モデルを表示
 *  POST : 選択したモデルへ設定を一括適用
 */
class PackBulkEditHandler(
    private val plugin: ResourcePackFlow,
    private val authManager: ResourcePackSessionManager,
    private val modelConfigManager: ModelConfigManager
) : HttpHandler {

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

        if (exchange.requestMethod.equals("POST", ignoreCase = true)) {
            handlePost(exchange, playerName)
        } else {
            handleGet(exchange, playerName)
        }
    }

    private fun handleGet(exchange: HttpExchange, playerName: String) {
        val models = modelConfigManager.loadModelsByAuthor(playerName).values.toList()
        val html = PackBulkEditPage.render(playerName, models).toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, html.size.toLong())
        exchange.responseBody.use { it.write(html) }
    }

    private fun handlePost(exchange: HttpExchange, playerName: String) {
        val body = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val params = parseQueryParams(body)

        val modelIds = params["modelIds"]?.split(",")?.map { it.trim().lowercase() }
            ?.filter { it.isNotBlank() }?.distinct().orEmpty()
        if (modelIds.isEmpty()) { sendResponse(exchange, 400, "対象のモデルが選択されていません。"); return }

        // 「変更しない」以外の指定を (設定, 値) に集め、同時に検証する
        val changes = mutableListOf<Pair<ModelSetting, String>>()
        for (s in ModelSettings.bulkable) {
            val raw = params[s.key]
            if (raw.isNullOrBlank() || raw == "keep") continue
            if (!s.accepts(raw)) { sendResponse(exchange, 400, "「${s.label}」の値が不正です。"); return }
            changes += s to raw
        }
        if (changes.isEmpty()) { sendResponse(exchange, 400, "変更する項目が指定されていません。"); return }

        var updated = 0; var notFound = 0; var failed = 0
        val skipped = linkedMapOf<ModelSetting, Int>()

        for (modelId in modelIds) {
            val current: RegisteredModelData = modelConfigManager.getModel(playerName, modelId)
                ?: run { notFound++; continue }

            var next = current
            for ((s, raw) in changes) {
                if (s.applicable(current)) next = s.apply(next, raw)
                else skipped.merge(s, 1, Int::plus)
            }
            if (next == current) continue

            if (modelConfigManager.saveModel(playerName, next)) updated++
            else {
                failed++
                plugin.logger.warning("一括設定変更: モデル保存に失敗しました ($playerName/$modelId)")
            }
        }

        val message = buildString {
            append("$updated 件を更新しました。")
            skipped.forEach { (s, n) -> append("\n「${s.label}」は変更できないモデルが $n 件あったためスキップしました。") }
            if (notFound > 0) append("\n見つからないモデル: $notFound 件")
            if (failed > 0) append("\n保存に失敗: $failed 件")
        }
        sendResponse(exchange, if (failed > 0) 500 else 200, message)
    }

    private fun parseQueryParams(query: String?): Map<String, String> {
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
}