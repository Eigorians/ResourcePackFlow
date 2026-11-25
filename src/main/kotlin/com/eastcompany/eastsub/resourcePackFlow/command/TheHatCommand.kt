package com.eastcompany.eastsub.resourcePackFlow.command

import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*

class TheHatCommand(
    private val plugin: ResourcePackFlow
) : CommandExecutor, TabCompleter {

    private val help = ResourcePackHelp()

    private val originalItemKey = NamespacedKey(
        plugin,
        "thehat_original_item"
    )

    private fun serializeItem(item: ItemStack): ByteArray {
        val output = ByteArrayOutputStream()

        BukkitObjectOutputStream(output).use { stream ->
            stream.writeObject(item)
        }

        return output.toByteArray()
    }

    private fun deserializeItem(bytes: ByteArray): ItemStack {
        ByteArrayInputStream(bytes).use { input ->
            BukkitObjectInputStream(input).use { stream ->
                return stream.readObject() as ItemStack
            }
        }
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (sender !is Player) {
            sender.sendMessage(
                "${ChatColor.RED}このコマンドはプレイヤーのみ実行できます。"
            )
            return true
        }

        if (!sender.hasPermission("resourcepackflow.thehat")) {
            sender.sendMessage(
                "${ChatColor.RED}このコマンドを実行する権限がありません。"
            )
            return true
        }

        // =========================
        // Help
        // =========================

        if (args.isNotEmpty() && args[0].equals("help", ignoreCase = true)) {
            val page = args.getOrNull(1)?.toIntOrNull() ?: 1

            help.send(sender, page)
            return true
        }

        // =========================
        // Reset
        // =========================

        if (args.size == 1 && args[0].equals("reset", ignoreCase = true)) {
            resetModel(sender)
            return true
        }

        // =========================
        // Apply Model
        // =========================

        // 階層 / アイテム名 / true|false の3引数必須
        if (args.size != 3) {
            sender.sendMessage(
                "${ChatColor.YELLOW}/thehat <階層> <アイテム名> <true/false>"
            )
            sender.sendMessage(
                "${ChatColor.GRAY}true = 頭に装備可能 / false = 装備不可"
            )
            sender.sendMessage(
                "${ChatColor.GRAY}/thehat reset = モデルを解除"
            )
            return true
        }

        val category = args[0]
        val itemName = args[1]

        val equippable = when (args[2].lowercase()) {
            "true" -> true
            "false" -> false

            else -> {
                sender.sendMessage(
                    "${ChatColor.RED}第3引数は true または false を指定してください。"
                )
                return true
            }
        }

        applyModel(
            sender,
            category,
            itemName,
            equippable
        )

        return true
    }

    // =========================
    // Apply Model
    // =========================

    private fun applyModel(
        player: Player,
        category: String,
        itemName: String,
        equippable: Boolean
    ) {
        val modelName = "${category}_${itemName}"
            .lowercase(Locale.ROOT)

        val modelFile = File(
            plugin.dataFolder,
            "${plugin.configManager.resourcePackDirectory}" +
                    "/assets/thehat/items/$modelName.json"
        )

        if (!modelFile.exists() || !modelFile.isFile) {
            player.sendMessage(
                "${ChatColor.RED}[ResourcePackFlow] " +
                        "モデルが見つかりません: $category/$itemName"
            )
            return
        }

        val item = player.inventory.itemInMainHand

        if (item.type == Material.AIR) {
            player.sendMessage(
                "${ChatColor.RED}[ResourcePackFlow] " +
                        "モデルを適用するアイテムを手に持ってください。"
            )
            return
        }

        val meta = item.itemMeta ?: run {
            player.sendMessage(
                "${ChatColor.RED}[ResourcePackFlow] " +
                        "このアイテムにはモデルを適用できません。"
            )
            return
        }

        val modelKey = NamespacedKey(
            "thehat",
            modelName
        )

        try {
            /*
             * TheHatを最初に適用する時だけ、
             * 変更前のItemStack全体を保存する。
             *
             * 2回目以降のTheHat変更では上書きしない。
             */
            if (!meta.persistentDataContainer.has(
                    originalItemKey,
                    PersistentDataType.BYTE_ARRAY
                )
            ) {
                val originalBytes = serializeItem(item)

                meta.persistentDataContainer.set(
                    originalItemKey,
                    PersistentDataType.BYTE_ARRAY,
                    originalBytes
                )
            }

            // モデル適用
            meta.setItemModel(modelKey)

            if (equippable) {
                val equipment = meta.getEquippable()
                equipment.slot = EquipmentSlot.HEAD
                meta.setEquippable(equipment)
            } else {
                val defaultItem = ItemStack(item.type)
                val defaultMeta = defaultItem.itemMeta

                if (defaultMeta != null) {
                    meta.setEquippable(
                        defaultMeta.getEquippable()
                    )
                } else {
                    meta.setEquippable(null)
                }
            }

            item.itemMeta = meta

        } catch (e: Exception) {
            plugin.logger.warning(
                "ItemModelの設定に失敗しました: ${e.message}"
            )

            player.sendMessage(
                "${ChatColor.RED}[ResourcePackFlow] " +
                        "モデルの設定に失敗しました。"
            )
            return
        }

        player.sendMessage(
            "${ChatColor.GREEN}[ResourcePackFlow] " +
                    "$category/$itemName のモデルを適用しました。" +
                    if (equippable) {
                        " ${ChatColor.GRAY}(頭装備: ON)"
                    } else {
                        " ${ChatColor.GRAY}(頭装備: OFF)"
                    }
        )
    }

    // =========================
    // Reset Model
    // =========================

    private fun resetModel(player: Player) {
        val item = player.inventory.itemInMainHand

        if (item.type == Material.AIR) {
            player.sendMessage(
                "${ChatColor.RED}[ResourcePackFlow] " +
                        "モデルを解除するアイテムを手に持ってください。"
            )
            return
        }

        val meta = item.itemMeta ?: run {
            player.sendMessage(
                "${ChatColor.RED}[ResourcePackFlow] " +
                        "このアイテムにはItemMetaがありません。"
            )
            return
        }

        val originalBytes =
            meta.persistentDataContainer.get(
                originalItemKey,
                PersistentDataType.BYTE_ARRAY
            )

        if (originalBytes == null) {
            player.sendMessage(
                "${ChatColor.YELLOW}[ResourcePackFlow] " +
                        "このアイテムにはTheHatの復元データがありません。"
            )
            return
        }

        try {
            val originalItem = deserializeItem(originalBytes)

            val originalMeta = originalItem.itemMeta

            if (originalMeta == null) {
                player.sendMessage(
                    "${ChatColor.RED}[リソースパック] " +
                            "元のアイテム情報を復元できませんでした。"
                )
                return
            }

            item.itemMeta = originalMeta

        } catch (e: Exception) {
            plugin.logger.warning(
                "TheHatモデルの復元に失敗しました: ${e.message}"
            )

            player.sendMessage(
                "${ChatColor.RED}[ResourcePackFlow] " +
                        "モデルの復元に失敗しました。"
            )
            return
        }

        player.sendMessage(
            "${ChatColor.GREEN}[ResourcePackFlow] " +
                    "TheHatモデルを解除し、元の状態へ戻しました。"
        )
    }

    // =========================
    // Tab Complete
    // =========================

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {

        val modelsDir = File(
            plugin.dataFolder,
            "${plugin.configManager.resourcePackDirectory}" +
                    "/assets/thehat/models"
        )

        // =========================
        // /thehat <階層>
        // =========================

        if (args.size == 1) {

            val suggestions = mutableListOf<String>()

            /*
             * resetはmodelsディレクトリが無くても
             * 使用できるようにする。
             */
            suggestions += "reset"

            if (modelsDir.exists() && modelsDir.isDirectory) {

                modelsDir
                    .listFiles()
                    ?.filter {
                        it.isDirectory
                    }
                    ?.mapTo(suggestions) {
                        it.name
                    }
            }

            return suggestions
                .filter {
                    it.startsWith(
                        args[0],
                        ignoreCase = true
                    )
                }
                .sorted()
        }

        // =========================
        // /thehat <階層> <アイテム名>
        // =========================

        if (args.size == 2) {

            /*
             * resetに第2引数は存在しない
             */
            if (
                args[0].equals(
                    "reset",
                    ignoreCase = true
                )
            ) {
                return emptyList()
            }

            if (!modelsDir.exists() || !modelsDir.isDirectory) {
                return emptyList()
            }

            val categoryDir = File(
                modelsDir,
                args[0]
            )

            if (
                !categoryDir.exists() ||
                !categoryDir.isDirectory
            ) {
                return emptyList()
            }

            return categoryDir
                .listFiles()
                ?.filter {
                    it.isFile &&
                            it.extension.equals(
                                "json",
                                ignoreCase = true
                            )
                }
                ?.map {
                    it.nameWithoutExtension
                }
                ?.filter {
                    it.startsWith(
                        args[1],
                        ignoreCase = true
                    )
                }
                ?.sorted()
                ?: emptyList()
        }
        if (args.size == 3) {
            return listOf("true", "false")
                .filter {
                    it.startsWith(
                        args[2],
                        ignoreCase = true
                    )
                }
        }

        return emptyList()
    }
}