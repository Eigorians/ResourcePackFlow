package com.eastcompany.eastsub.gacha.web.handler

import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager
import com.eastcompany.eastsub.gacha.web.page.PackHomePage
import com.eastcompany.eastsub.gacha.web.template.WebHtmlTemplates
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import org.bukkit.Bukkit
import java.nio.charset.StandardCharsets

class PackHomeHandler(
    private val plugin: ResourcePackFlow,
    private val authManager: ResourcePackSessionManager
) : HttpHandler {

    override fun handle(exchange: HttpExchange) {
        try {
            handleRequest(exchange)
        } catch (e: Exception) {
            plugin.logger.warning(
                "ホーム画面の表示中にエラーが発生しました: ${e.message}"
            )

            runCatching {
                sendPlainText(
                    exchange = exchange,
                    statusCode = 500,
                    text = "Internal Server Error"
                )
            }
        } finally {
            exchange.close()
        }
    }

    private fun handleRequest(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            exchange.responseHeaders.set("Allow", "GET")
            sendPlainText(
                exchange = exchange,
                statusCode = 405,
                text = "Method Not Allowed"
            )
            return
        }

        val playerName = getAuthenticatedPlayerName(exchange)

        if (playerName == null) {
            redirectToLogin(exchange)
            return
        }

        sendHtml(
            exchange = exchange,
            statusCode = 200,
            html = WebHtmlTemplates.renderPage(
                title = "ホーム - ResourcePackFlow",
                playerName = playerName,
                bodyContent = PackHomePage.renderBody()
            )
        )
    }

    // =========================================================
    // Authentication
    // =========================================================

    private fun getAuthenticatedPlayerName(
        exchange: HttpExchange
    ): String? {
        val sessionToken = parseCookie(
            cookieHeader = exchange.requestHeaders.getFirst("Cookie"),
            key = SESSION_COOKIE
        ) ?: return null

        val playerUuid = authManager.getPlayerUuidBySession(sessionToken)
            ?: return null

        return Bukkit.getOfflinePlayer(playerUuid).name
    }

    private fun redirectToLogin(exchange: HttpExchange) {
        exchange.responseHeaders.set("Location", LOGIN_PATH)
        exchange.sendResponseHeaders(302, -1)
    }

    // =========================================================
    // Response
    // =========================================================

    private fun sendHtml(
        exchange: HttpExchange,
        statusCode: Int,
        html: String
    ) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)

        exchange.responseHeaders.set(
            "Content-Type",
            "text/html; charset=utf-8"
        )
        exchange.responseHeaders.set("X-Content-Type-Options", "nosniff")
        exchange.responseHeaders.set("Cache-Control", "no-store")

        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())

        exchange.responseBody.use { output ->
            output.write(bytes)
        }
    }

    private fun sendPlainText(
        exchange: HttpExchange,
        statusCode: Int,
        text: String
    ) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)

        exchange.responseHeaders.set(
            "Content-Type",
            "text/plain; charset=utf-8"
        )
        exchange.responseHeaders.set("Cache-Control", "no-store")

        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())

        exchange.responseBody.use { output ->
            output.write(bytes)
        }
    }

    // =========================================================
    // Cookie
    // =========================================================

    private fun parseCookie(
        cookieHeader: String?,
        key: String
    ): String? {
        if (cookieHeader.isNullOrBlank()) {
            return null
        }

        return cookieHeader
            .split(";")
            .asSequence()
            .map { it.trim() }
            .map { it.split("=", limit = 2) }
            .firstOrNull { parts ->
                parts.size == 2 && parts[0] == key
            }
            ?.get(1)
            ?.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val SESSION_COOKIE = "session_token"
        private const val LOGIN_PATH = "/login"
    }
}