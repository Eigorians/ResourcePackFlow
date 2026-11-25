package com.eastcompany.eastsub.resourcePackFlow.pack

import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import java.io.File

class TheHatModelGenerator(
    private val plugin: ResourcePackFlow
) {

    fun generate() {
        val resourcePackDirectory = File(
            plugin.dataFolder,
            plugin.configManager.resourcePackDirectory
        )

        val basePath = File(
            resourcePackDirectory,
            "assets/thehat"
        )

        val modelsDir = File(
            basePath,
            "models"
        )

        val itemsDir = File(
            basePath,
            "items"
        )

        // thehat/models が無ければ何もしない
        if (!modelsDir.exists() || !modelsDir.isDirectory) {
            plugin.logger.info(
                "TheHat modelsディレクトリが存在しないため、Item Model生成をスキップします。"
            )
            return
        }

        if (!itemsDir.exists()) {
            require(itemsDir.mkdirs()) {
                "TheHat itemsディレクトリを作成できませんでした: ${itemsDir.absolutePath}"
            }
        }

        var generatedCount = 0

        modelsDir
            .walkTopDown()
            .filter {
                it.isFile &&
                        it.extension.equals("json", ignoreCase = true)
            }
            .forEach { jsonFile ->

                val relativePath = jsonFile
                    .relativeTo(modelsDir)
                    .path
                    .replace("\\", "/")

                val fileNameWithoutExt =
                    jsonFile.nameWithoutExtension

                val parentPath = jsonFile
                    .parentFile
                    .relativeTo(modelsDir)
                    .path
                    .replace("\\", "/")

                val newFileName =
                    if (
                        parentPath.isEmpty() ||
                        parentPath == "."
                    ) {
                        "$fileNameWithoutExt.json"
                    } else {
                        "${parentPath.replace("/", "_")}_${fileNameWithoutExt}.json"
                    }

                val outputFile = File(
                    itemsDir,
                    newFileName
                )

                val modelPath =
                    "thehat:${relativePath.removeSuffix(".json")}"

                val jsonContent = """
                    {
                      "model": {
                        "type": "minecraft:model",
                        "model": "$modelPath"
                      }
                    }
                """.trimIndent()

                outputFile.writeText(
                    jsonContent,
                    Charsets.UTF_8
                )

                generatedCount++
            }

        plugin.logger.info(
            "TheHat Item Modelを ${generatedCount} 件生成しました。"
        )
    }
}