package com.eastcompany.eastsub.gacha.web.handler

import com.eastcompany.eastsub.gacha.history.GachaHistory
import com.eastcompany.eastsub.gacha.web.page.PackHistoryPage
import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler

/**
 * ログイン中のプレイヤー自身のガチャ履歴ページ（GET /history）
 */
class PackHistoryHandler(
    private val authManager: ResourcePackSessionManager
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

        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            sendText(exchange, 405, "Method Not Allowed")
            return
        }

        val playerName = authManager.getPlayerName(playerUuid)
        val history = GachaHistory.get(playerName)   // 履歴が無ければ null

        val html = PackHistoryPage.render(playerName, history).toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, html.size.toLong())
        exchange.responseBody.use { it.write(html) }
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