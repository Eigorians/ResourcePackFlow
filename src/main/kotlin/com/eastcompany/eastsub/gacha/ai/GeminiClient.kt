package com.eastcompany.eastsub.gacha.ai

import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * Gemini API (REST) クライアント。
 * モデル名は引数で差し替える。
 *
 * 非同期(CompletableFuture)なので、メインスレッドをブロックしない。
 */
class GeminiClient(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val timeout: Duration = Duration.ofSeconds(30)
) {

    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val gson = Gson()

    /**
     * テキストを生成する。
     * 失敗時は例外つきの Future になる。
     */
    fun generate(
        prompt: String,
        systemInstruction: String? = null
    ): CompletableFuture<String> {

        val body = JsonObject().apply {

            add("contents", JsonArray().apply {
                add(JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("text", prompt)
                        })
                    })
                })
            })

            if (systemInstruction != null) {
                add("systemInstruction", JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("text", systemInstruction)
                        })
                    })
                })
            }
        }

        val request = HttpRequest.newBuilder()
            .uri(
                URI.create(
                    "$BASE_URL/models/$model:generateContent"
                )
            )
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    gson.toJson(body)
                )
            )
            .build()

        return http
            .sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
            )
            .thenApply { res ->

                if (res.statusCode() !in 200..299) {
                    throw GeminiException(
                        res.statusCode(),
                        parseError(res.body())
                    )
                }

                extractText(res.body())
            }
    }

    /**
     * 現在使えるモデル名の一覧を取得する。
     * 旧モデルが残っているかの確認用。
     */
    fun listModels(): CompletableFuture<List<String>> {

        val request = HttpRequest.newBuilder()
            .uri(
                URI.create(
                    "$BASE_URL/models?pageSize=200"
                )
            )
            .timeout(timeout)
            .header("x-goog-api-key", apiKey)
            .GET()
            .build()

        return http
            .sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
            )
            .thenApply { res ->

                if (res.statusCode() !in 200..299) {
                    throw GeminiException(
                        res.statusCode(),
                        parseError(res.body())
                    )
                }

                JsonParser
                    .parseString(res.body())
                    .asJsonObject
                    .getAsJsonArray("models")
                    ?.map {
                        it.asJsonObject
                            .get("name")
                            .asString
                            .removePrefix("models/")
                    }
                    ?: emptyList()
            }
    }

    /**
     * Gemini API のレスポンスから本文を取り出す。
     */
    private fun extractText(json: String): String {

        val root = JsonParser
            .parseString(json)
            .asJsonObject

        val parts = root
            .getAsJsonArray("candidates")
            ?.firstOrNull()
            ?.asJsonObject
            ?.getAsJsonObject("content")
            ?.getAsJsonArray("parts")
            ?: throw GeminiException(
                200,
                "応答に候補がありません(安全フィルタなどでブロックされた可能性)"
            )

        return parts.joinToString("") {
            it.asJsonObject
                .get("text")
                ?.asString
                ?: ""
        }
    }

    /**
     * Gemini API のエラー本文を取得する。
     */
    private fun parseError(body: String): String =
        runCatching {
            JsonParser
                .parseString(body)
                .asJsonObject
                .getAsJsonObject("error")
                .get("message")
                .asString
        }.getOrDefault(body)

    class GeminiException(
        val status: Int,
        message: String
    ) : RuntimeException("[$status] $message")

    companion object {

        private const val BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta"

        const val DEFAULT_MODEL =
            "gemini-3.5-flash-lite"
    }
}


/**
 * GeminiClient の生成を担当する Factory。
 *
 * APIキーは以下の優先順位で取得する。
 *
 * 1. 環境変数 GEMINI_API_KEY
 * 2. config.yml の gemini.api-key
 *
 * model は config.yml の gemini.model。
 */
object GeminiFactory {

    fun create(plugin: ResourcePackFlow): GeminiClient? {

        val key =
            System.getenv("GEMINI_API_KEY")
                ?.takeIf { it.isNotBlank() }
                ?: plugin.config
                    .getString("gemini.api-key")
                    ?.takeIf { it.isNotBlank() }

        if (key == null) {

            plugin.logger.warning(
                "Gemini の API キーが未設定です。AI 機能は無効になります。"
            )

            return null
        }

        val model =
            plugin.config.getString("gemini.model")
                ?: GeminiClient.DEFAULT_MODEL

        return GeminiClient(
            apiKey = key,
            model = model
        )
    }
}