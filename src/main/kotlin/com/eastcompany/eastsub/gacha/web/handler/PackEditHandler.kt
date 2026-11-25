package com.eastcompany.eastsub.gacha.web.handler

import com.eastcompany.eastsub.gacha.item.GachaItemBuilder
import com.eastcompany.eastsub.gacha.model.ModelConfigManager
import com.eastcompany.eastsub.gacha.model.RegisteredModelData
import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import com.eastcompany.eastsub.gacha.web.form.BoolSetting
import com.eastcompany.eastsub.gacha.web.form.EnumSetting
import com.eastcompany.eastsub.gacha.web.form.ModelSettings
import com.eastcompany.eastsub.gacha.web.page.PackEditPage
import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class PackEditHandler(
    private val plugin: ResourcePackFlow,
    private val authManager: ResourcePackSessionManager,
    private val modelConfigManager: ModelConfigManager
) : HttpHandler {

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
        if (path.startsWith("/edit/texture")) {
            handleTextureGet(exchange)
            return
        }

        if (exchange.requestMethod.equals("POST", ignoreCase = true)) {
            handlePost(exchange, playerUuid)
        } else {
            handleGet(exchange, playerUuid)
        }
    }

    private fun handleGet(exchange: HttpExchange, playerUuid: java.util.UUID) {
        val playerName = authManager.getPlayerName(playerUuid)
        val queryMap = parseQueryParams(exchange.requestURI.query)
        val modelId = queryMap["modelId"]?.lowercase()

        if (modelId.isNullOrBlank()) {
            sendResponse(exchange, 400, "modelId が指定されていません。")
            return
        }

        val data = modelConfigManager.getModel(playerName, modelId)
        if (data == null) {
            sendResponse(exchange, 404, "指定されたモデルデータが存在しません。")
            return
        }

        val html = PackEditPage.render(playerName, data).toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, html.size.toLong())
        exchange.responseBody.use { it.write(html) }
    }

    private fun handleTextureGet(exchange: HttpExchange) {
        val relPath = parseQueryParams(exchange.requestURI.query)["path"]
        if (relPath.isNullOrBlank() || !relPath.endsWith(".png", ignoreCase = true)) {
            sendResponse(exchange, 400, "invalid path")
            return
        }

        val packRoot = File(plugin.dataFolder, "resourcepack").canonicalFile
        val candidate = if (relPath.startsWith("resourcepack/")) {
            File(plugin.dataFolder, relPath)
        } else {
            File(packRoot, "assets/$relPath")
        }
        val file = candidate.canonicalFile

        // resourcepack/ の外は一切返さない
        if (!file.path.startsWith(packRoot.path + File.separator) || !file.isFile) {
            sendResponse(exchange, 404, "Texture image not found")
            return
        }

        val bytes = file.readBytes()
        exchange.responseHeaders.set("Content-Type", "image/png")
        exchange.responseHeaders.set("Cache-Control", "no-cache")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun handlePost(exchange: HttpExchange, playerUuid: java.util.UUID) {
        val playerName = authManager.getPlayerName(playerUuid)
        val requestBodyText = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val params = parseQueryParams(requestBodyText)

        val modelId = params["modelId"]?.lowercase()
        val action = params["action"]

        if (modelId.isNullOrBlank()) {
            sendResponse(exchange, 400, "modelId が不足しています。")
            return
        }

        val currentData = modelConfigManager.getModel(playerName, modelId)
        if (currentData == null) {
            sendResponse(exchange, 404, "対象のモデルが見つかりません。")
            return
        }

        when (action) {
            "build_and_send" -> {
                val player = Bukkit.getPlayer(playerUuid)
                if (player == null || !player.isOnline) {
                    sendResponse(exchange, 400, "該当するプレイヤーがオンラインではありません。")
                    return
                }

                val success = plugin.resourcePackManager.buildAndApplyTo(player)
                if (success) {
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        ensurePlayerHasModelItem(player, currentData)
                    })
                    sendResponse(exchange, 200, "OK")
                } else {
                    sendResponse(exchange, 500, "リソースパックのビルドまたは送信に失敗しました。")
                }
            }

            "give_pack" -> {
                val player = Bukkit.getPlayer(playerUuid)
                if (player == null || !player.isOnline) {
                    sendResponse(exchange, 400, "該当するプレイヤーがオンラインではありません。")
                    return
                }

                val success = plugin.resourcePackManager.buildAndApplyTo(player)
                if (!success) {
                    sendResponse(exchange, 500, "リソースパックのビルドまたは送信に失敗しました。")
                    return
                }

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (player.gameMode != GameMode.CREATIVE) {
                        player.sendMessage("§c[ResourcePackFlow] クリエイティブモードではないため、パックアイテムを追加できませんでした。")
                        return@Runnable
                    }

                    val packItem = GachaItemBuilder.createPackItem(currentData, 1)
                    val leftover = player.inventory.addItem(packItem)
                    if (leftover.isNotEmpty()) {
                        for (dropItem in leftover.values) {
                            player.world.dropItemNaturally(player.location, dropItem)
                        }
                    }
                    player.sendMessage("§a[ResourcePackFlow] パックアイテム「${currentData.itemName}§a」をインベントリに追加しました。")
                })
                sendResponse(exchange, 200, "OK")
            }

            "delete" -> {
                if (!currentData.deletable && !currentData.external) {
                    sendResponse(exchange, 403, "このモデルは削除がロックされているため削除できません。")
                    return
                }

                val deleted = modelConfigManager.deleteModel(playerName, modelId)
                if (deleted) {
                    sendResponse(exchange, 200, "OK")
                } else {
                    sendResponse(exchange, 500, "削除処理に失敗しました。")
                }
            }

            else -> {
                val newItemName = params["itemName"]?.trim()
                if (newItemName.isNullOrBlank()) {
                    sendResponse(exchange, 400, "表示名は必須です。"); return
                }

                var updatedData = currentData.copy(
                    itemName = newItemName,
                    description = params["description"]?.trim(),
                    recommendedItem = params["recommendedItem"]?.trim().takeIf { !it.isNullOrBlank() }
                )

                for (s in ModelSettings.editable) {
                    // 変更不可の項目 (disabled で送信されない) は現在値を維持
                    if (!s.applicable(updatedData)) continue
                    when (s) {
                        // チェックボックスは未チェック = 未送信 = false
                        is BoolSetting -> updatedData = s.apply(updatedData, (params[s.key] == "true").toString())
                        is EnumSetting -> params[s.key]?.takeIf { s.accepts(it) }
                            ?.let { updatedData = s.apply(updatedData, it) }
                    }
                }

                if (modelConfigManager.saveModel(playerName, updatedData)) sendResponse(exchange, 200, "OK")
                else sendResponse(exchange, 500, "更新保存に失敗しました。")
            }
        }
    }

    private fun ensurePlayerHasModelItem(player: Player, data: RegisteredModelData) {
        if (player.gameMode != GameMode.CREATIVE) {
            player.sendMessage("§c[ResourcePackFlow] クリエイティブモードではないため、モデルアイテムを追加できませんでした。")
            return
        }

        try {
            val modelItem = GachaItemBuilder.createModelItem(data, 1)
            val material = modelItem.type
            val gachaKey = GachaItemBuilder.gachaKey(data)
            val isCmd = data.slotType.equals("custom_model_data", ignoreCase = true)
            val floatValue = data.customModelDataFloat

            val hasItem = player.inventory.contents.filterNotNull().any { item ->
                if (item.type != material) return@any false
                val meta = item.itemMeta ?: return@any false

                if (isCmd) {
                    if (!meta.hasCustomModelData()) return@any false
                    val component = meta.customModelDataComponent
                    if (floatValue != null) {
                        component.floats.contains(floatValue)
                    } else {
                        component.strings.contains(gachaKey)
                    }
                } else {
                    meta.hasItemModel() && meta.itemModel == GachaItemBuilder.itemModelKey(data)
                }
            }

            if (!hasItem) {
                player.inventory.addItem(modelItem)
                player.sendMessage("§a[ResourcePackFlow] カスタムアイテム「${data.itemName}§a」をインベントリに追加しました。")
            }
        } catch (e: Exception) {
            plugin.logger.warning("モデルアイテム (${data.modelId}) の付与中にエラーが発生しました: ${e.message}")
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