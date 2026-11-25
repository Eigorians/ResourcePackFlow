package com.eastcompany.eastsub.resourcePackFlow.pack

import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackZipper(
    private val plugin: ResourcePackFlow
) {

    fun createZip(): File {

        val sourceDirectory = File(
            plugin.dataFolder,
            plugin.configManager.resourcePackDirectory
        )

        require(sourceDirectory.exists()) {
            "リソースパックディレクトリが存在しません: ${sourceDirectory.absolutePath}"
        }

        require(sourceDirectory.isDirectory) {
            "リソースパックのパスがディレクトリではありません: ${sourceDirectory.absolutePath}"
        }

        val outputFile = File(
            plugin.dataFolder,
            "pack.zip"
        )

        return createZipInternal(
            sourceDirectory,
            outputFile
        )
    }

    private fun createZipInternal(
        sourceDirectory: File,
        outputFile: File
    ): File {

        val temporaryFile = File.createTempFile(
            "pack-",
            ".zip.tmp",
            plugin.dataFolder
        )

        try {

            ZipOutputStream(
                FileOutputStream(temporaryFile)
            ).use { zip ->

                val root = sourceDirectory.toPath()

                Files.walk(root).use { paths ->

                    paths
                        .filter { Files.isRegularFile(it) }
                        .forEach { path ->

                            val entryName = root
                                .relativize(path)
                                .toString()
                                .replace("\\", "/")

                            val entry = ZipEntry(entryName)

                            zip.putNextEntry(entry)

                            Files.copy(
                                path,
                                zip
                            )

                            zip.closeEntry()
                        }
                }
            }

            require(
                temporaryFile.exists() &&
                        temporaryFile.length() > 0
            ) {
                "一時ZIPファイルの生成に失敗しました。"
            }

            if (outputFile.exists()) {
                backupCurrentZip(outputFile)
            }

            try {

                Files.move(
                    temporaryFile.toPath(),
                    outputFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )

            } catch (
                e: AtomicMoveNotSupportedException
            ) {

                plugin.logger.warning(
                    "ATOMIC_MOVEがサポートされていないため通常の置換を使用します。"
                )

                Files.move(
                    temporaryFile.toPath(),
                    outputFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }

            return outputFile

        } catch (e: Exception) {

            if (temporaryFile.exists()) {
                temporaryFile.delete()
            }

            throw e
        }
    }
    private fun backupCurrentZip(outputFile: File) {

        val backupDirectory = File(
            plugin.dataFolder,
            "backup"
        )

        if (!backupDirectory.exists()) {
            require(backupDirectory.mkdirs()) {
                "バックアップディレクトリを作成できませんでした: ${backupDirectory.absolutePath}"
            }
        }

        val timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")
        )

        val backupFile = File(
            backupDirectory,
            "pack-$timestamp.zip"
        )

        Files.copy(
            outputFile.toPath(),
            backupFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )

        plugin.logger.info(
            "旧リソースパックをバックアップしました: ${backupFile.name}"
        )
    }

}