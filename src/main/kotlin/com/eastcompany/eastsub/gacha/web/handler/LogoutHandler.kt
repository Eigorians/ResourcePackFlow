package com.eastcompany.eastsub.gacha.web.handler

import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler

/**
 * POST /logout : Webセッションを破棄し、Cookie を削除して /login へ戻す。
 * GET など POST 以外は何もせず /home へ戻す（URL を直接開いてもログアウトしない）。
 */
class LogoutHandler(
    private val authManager: ResourcePackSessionManager
) : HttpHandler {

    override fun handle(exchange: HttpExchange) {
        try {
            if (!exchange.requestMethod.equals("POST", ignoreCase = true)) {
                redirect(exchange, "/home")
                return
            }

            val sessionToken = parseCookie(exchange.requestHeaders.getFirst("Cookie"), "session_token")
            authManager.invalidateSession(sessionToken)

            // Cookie を即時失効させる（Path は発行時と同じ "/" を想定）
            exchange.responseHeaders.add(
                "Set-Cookie",
                "session_token=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax"
            )
            redirect(exchange, "/login")
        } finally {
            exchange.close()
        }
    }

    private fun redirect(exchange: HttpExchange, location: String) {
        exchange.responseHeaders.set("Location", location)
        exchange.sendResponseHeaders(302, -1)
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