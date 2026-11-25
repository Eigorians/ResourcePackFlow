package com.eastcompany.eastsub.resourcePackFlow.http

import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import com.eastcompany.eastsub.resourcePackFlow.pack.AddonPackManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.ExecutorService

class PackHttpServer(
    private val plugin: ResourcePackFlow,
) {

    // 外部（GachaMain等）から組み込みサーバーにアクセスできるように公開プロパティ化
    var httpServer: HttpServer? = null
        private set

    private var executor: ExecutorService? = null

    private val port: Int
        get() = plugin.configManager.httpPort

    /**
     * HTTP 配信を利用可能な状態にする。
     * 1. 自分が起動済み → OK
     * 2. 他のResourcePackFlowが起動済み → OK
     * 3. 誰もいない → 自分が起動
     */
    @Synchronized
    fun ensureRunning(): Boolean {
        // 自分自身が既にHTTPサーバーを持っている
        if (httpServer != null) {
            return true
        }

        // 他のSpigotのResourcePackFlowが配信しているか確認
        if (isResourcePackFlowRunning()) {
            plugin.logger.info("既存のResourcePackFlow HTTP Serverを使用します。")
            return true
        }

        return start()
    }

    /**
     * 自分自身がHTTPサーバーを起動する。
     */
    private fun start(): Boolean {
        return try {
            val createdServer = HttpServer.create(
                InetSocketAddress(plugin.configManager.httpBindHost.ifBlank { "0.0.0.0" }, port),
                0
            )

            // ルートからのリダイレクト設定
            createdServer.createContext("/") { exchange ->
                exchange.responseHeaders.set("Location", "/home")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }

            // =========================
            // Resource Pack 配信
            // =========================
            createdServer.createContext("/pack.zip") { exchange ->
                if (exchange.requestMethod != "GET") {
                    exchange.sendResponseHeaders(405, -1)
                    exchange.close()
                    return@createContext
                }

                serveZip(exchange, File(plugin.dataFolder, "pack.zip"))
            }

            // =========================
            // 追加パック配信 (/addons/<ID>.zip)
            // =========================
            createdServer.createContext("/addons/") { exchange ->
                if (exchange.requestMethod != "GET") {
                    exchange.sendResponseHeaders(405, -1)
                    exchange.close()
                    return@createContext
                }

                val name = exchange.requestURI.path.removePrefix("/addons/")
                val id = name.removeSuffix(".zip")

                // "<ID>.zip" の形以外は受け付けない（パストラバーサル対策）
                if (!name.endsWith(".zip") || !id.matches(AddonPackManager.ADDON_ID_REGEX)) {
                    serveZip(exchange, null)
                    return@createContext
                }

                serveZip(
                    exchange,
                    File(File(plugin.dataFolder, plugin.configManager.addonsDirectory), "$id.zip")
                )
            }

            // =========================
            // Health Check
            // =========================
            createdServer.createContext("/resourcepackflow/health") { exchange ->
                if (exchange.requestMethod != "GET") {
                    exchange.sendResponseHeaders(405, -1)
                    exchange.close()
                    return@createContext
                }

                val response = "ResourcePackFlow".toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
                exchange.sendResponseHeaders(200, response.size.toLong())

                exchange.responseBody.use { it.write(response) }
            }

            executor = java.util.concurrent.ThreadPoolExecutor(
                4, 16, 30L, java.util.concurrent.TimeUnit.SECONDS,
                java.util.concurrent.LinkedBlockingQueue(64)
            ) { r -> Thread(r, "ResourcePackFlow-HTTP").apply { isDaemon = true } }
            createdServer.executor = executor
            createdServer.start()

            this.httpServer = createdServer

            plugin.logger.info("ResourcePackFlow HTTP Serverを ${plugin.configManager.httpBindHost}:$port で起動しました。")
            true

        } catch (e: Exception) {
            /*
             * ensureRunning() のチェック直後に別SpigotがHTTPサーバーを起動する競合もあり得るため、
             * 起動失敗後に再チェック。
             */
            if (isResourcePackFlowRunning()) {
                plugin.logger.info("別のResourcePackFlow HTTP Serverが起動しました。そちらを使用します。")
                true
            } else {
                plugin.logger.severe("HTTP Serverを起動できませんでした: ${e.message}")
                false
            }
        }
    }

    /** zip を返す。ファイルが無ければ 404 */
    private fun serveZip(exchange: HttpExchange, packFile: File?) {
        if (packFile == null || !packFile.isFile) {
            val message = "Resource pack not found".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(404, message.size.toLong())
            exchange.responseBody.use { it.write(message) }
            return
        }

        exchange.responseHeaders.set("Content-Type", "application/zip")
        exchange.responseHeaders.set("Content-Disposition", "attachment; filename=\"${packFile.name}\"")
        exchange.responseHeaders.set("Cache-Control", "no-cache")

        exchange.sendResponseHeaders(200, packFile.length())
        exchange.responseBody.use { output ->
            packFile.inputStream().buffered().use { input ->
                input.copyTo(output)
            }
        }
    }

    /**
     * 同じマシン上ですでにResourcePackFlowが HTTP 配信しているか確認する。
     */
    private fun isResourcePackFlowRunning(): Boolean {
        var connection: HttpURLConnection? = null

        return try {
            val url = URL("http://127.0.0.1:$port/resourcepackflow/health")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 500
            connection.readTimeout = 500

            if (connection.responseCode != 200) {
                return false
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            response.trim() == "ResourcePackFlow"

        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * このSpigot自身がHTTP配信担当だった場合のみ停止する。
     */
    fun stop() {
        val currentServer = httpServer ?: return

        currentServer.stop(0)
        httpServer = null

        executor?.shutdownNow()
        executor = null

        plugin.logger.info("ResourcePackFlow HTTP Serverを停止しました。")
    }
}