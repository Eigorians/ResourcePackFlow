package com.eastcompany.eastsub.gacha.web.handler

import com.eastcompany.eastsub.gacha.web.page.PackLoginPage
import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.net.URLDecoder

class PackLoginHandler(
    private val authManager: ResourcePackSessionManager
) : HttpHandler {

    override fun handle(exchange: HttpExchange) {
        val sessionToken = parseCookie(exchange.getRequestHeader("Cookie"), "session_token")
        val playerUuid = authManager.getPlayerUuidBySession(sessionToken)

        val method = exchange.requestMethod

        if (method.equals("GET", ignoreCase = true)) {
            // すでにログイン済みの場合は /home へ自動転送
            if (playerUuid != null) {
                exchange.responseHeaders.set("Location", "/home")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
                return
            }
            handleGet(exchange)
        } else if (method.equals("POST", ignoreCase = true)) {
            handlePost(exchange)
        } else {
            exchange.sendResponseHeaders(405, -1)
            exchange.close()
        }
    }

    private fun handleGet(exchange: HttpExchange) {
        val html = PackLoginPage.renderForm().toByteArray(Charsets.UTF_8)

        exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, html.size.toLong())
        exchange.responseBody.use { it.write(html) }
    }

    private fun handlePost(exchange: HttpExchange) {
        val body = exchange.requestBody.bufferedReader().use { it.readText() }
        val params = parseUrlEncoded(body)
        val password = params["password"] ?: ""

        // 統合マネージャー経由でワンタイムパスワードを消費しUUIDを取得
        val uuid = authManager.consumePassword(password)
        if (uuid != null) {
            val newToken = authManager.createWebSession(uuid)
            // 認証成功: Cookieにセットし、/home へリダイレクト
            exchange.responseHeaders.set("Set-Cookie", "session_token=$newToken; Path=/; HttpOnly")
            exchange.responseHeaders.set("Location", "/home")
            exchange.sendResponseHeaders(302, -1)
        } else {
            val errorHtml = PackLoginPage.renderFailed().toByteArray(Charsets.UTF_8)

            exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(401, errorHtml.size.toLong())
            exchange.responseBody.use { it.write(errorHtml) }
        }
        exchange.close()
    }

    private fun parseUrlEncoded(body: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (pair in body.split("&")) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[key] = value
            }
        }
        return map
    }

    private fun parseCookie(cookieHeader: String?, key: String): String? {
        if (cookieHeader.isNullOrBlank()) return null
        for (cookie in cookieHeader.split(";")) {
            val parts = cookie.trim().split("=", limit = 2)
            if (parts.size == 2 && parts[0] == key) {
                return parts[1]
            }
        }
        return null
    }

    private fun HttpExchange.getRequestHeader(name: String): String? {
        return this.requestHeaders.getFirst(name)
    }
}