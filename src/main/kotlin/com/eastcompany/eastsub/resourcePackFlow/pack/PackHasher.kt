package com.eastcompany.eastsub.resourcePackFlow.pack

import java.io.File
import java.security.MessageDigest

object PackHasher {

    fun sha1(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-1")

        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)

            while (true) {
                val read = input.read(buffer)

                if (read == -1) {
                    break
                }

                digest.update(buffer, 0, read)
            }
        }

        return digest.digest()
    }

    fun sha1Hex(file: File): String {
        return sha1(file).joinToString("") { byte ->
            "%02x".format(byte)
        }
    }
}