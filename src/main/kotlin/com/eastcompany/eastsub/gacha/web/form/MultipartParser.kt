package com.eastcompany.eastsub.gacha.web.form

import java.io.InputStream
import java.nio.charset.StandardCharsets

object MultipartParser {

    data class MultipartFile(val filename: String, val contentType: String, val bytes: ByteArray)

    class MultipartResult {
        val fields = mutableMapOf<String, String>()
        val files = mutableMapOf<String, MutableList<MultipartFile>>()

        fun getFormField(name: String): String? = fields[name]
        fun getFile(name: String): MultipartFile? = files[name]?.firstOrNull()
        fun getFiles(name: String): List<MultipartFile> = files[name] ?: emptyList()
    }

    fun extractBoundary(contentType: String): String? {
        for (param in contentType.split(";")) {
            val trimmed = param.trim()
            if (trimmed.startsWith("boundary=")) {
                return trimmed.substring(9).removeSurrounding("\"")
            }
        }
        return null
    }

    /** リクエスト本文が上限を超えたときに投げられる */
    class PayloadTooLargeException(val limit: Long) :
        RuntimeException("Request body exceeds $limit bytes")

    /**
     * @param maxBytes 本文の最大バイト数。超えた時点で読み込みを打ち切り [PayloadTooLargeException] を投げる
     */
    fun parse(inputStream: InputStream, boundary: String, maxBytes: Long = Long.MAX_VALUE): MultipartResult {
        val result = MultipartResult()
        val rawBytes = readLimited(inputStream, maxBytes)
        val delimiter = "--$boundary".toByteArray(StandardCharsets.ISO_8859_1)

        val parts = splitByteArray(rawBytes, delimiter)
        for (part in parts) {
            if (part.isEmpty()) continue

            val headerEnd = findHeaderEnd(part)
            if (headerEnd == -1) continue

            val headerStr = String(part, 0, headerEnd, StandardCharsets.UTF_8)
            val contentBytes = part.copyOfRange(headerEnd + 4, part.size - 2)

            val disposition = headerStr.lines().find { it.startsWith("Content-Disposition:", ignoreCase = true) } ?: continue
            val nameMatch = Regex("name=\"(.*?)\"").find(disposition)
            val name = nameMatch?.groupValues?.get(1) ?: continue

            val filenameMatch = Regex("filename=\"(.*?)\"").find(disposition)
            if (filenameMatch != null && filenameMatch.groupValues[1].isNotBlank()) {
                val filename = filenameMatch.groupValues[1]
                val contentTypeLine = headerStr.lines().find { it.startsWith("Content-Type:", ignoreCase = true) }
                val contentType = contentTypeLine?.substringAfter(":")?.trim() ?: "application/octet-stream"

                result.files.computeIfAbsent(name) { mutableListOf() }
                    .add(MultipartFile(filename, contentType, contentBytes))
            } else {
                result.fields[name] = String(contentBytes, StandardCharsets.UTF_8)
            }
        }
        return result
    }

    private fun readLimited(input: InputStream, maxBytes: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) throw PayloadTooLargeException(maxBytes)
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun splitByteArray(src: ByteArray, delimiter: ByteArray): List<ByteArray> {
        val list = mutableListOf<ByteArray>()
        var start = 0
        var i = 0
        while (i <= src.size - delimiter.size) {
            var match = true
            for (j in delimiter.indices) {
                if (src[i + j] != delimiter[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                if (i > start) list.add(src.copyOfRange(start, i))
                start = i + delimiter.size
                i += delimiter.size - 1
            }
            i++
        }
        return list
    }

    private fun findHeaderEnd(src: ByteArray): Int {
        for (i in 0 until src.size - 3) {
            if (src[i] == '\r'.code.toByte() && src[i + 1] == '\n'.code.toByte() &&
                src[i + 2] == '\r'.code.toByte() && src[i + 3] == '\n'.code.toByte()) {
                return i
            }
        }
        return -1
    }
}