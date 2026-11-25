package com.eastcompany.eastsub.gacha.command

import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager
import com.eastcompany.eastsub.gacha.item.GachaItemBuilder
import com.eastcompany.eastsub.gacha.stand.GachaStandManager
import com.eastcompany.eastsub.gacha.stand.GachaStandManager.modelConfigManager
import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class GachaCommand(
    private val plugin: ResourcePackFlow,
    private val authManager: ResourcePackSessionManager
) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行可能です。")
            return true
        }

        if (args.isEmpty()) {
            sendUsageHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "login" -> handleLogin(sender)
            "howtouse" -> handleHowToUse(sender)
            "reset" -> handleReset(sender)
            "stand" -> handleSummon(sender)
            "give" -> handleGive(sender, args)
            "createmystand" -> handleCreateMyStand(sender)
            else -> sendUsageHelp(sender)
        }

        return true
    }

    /**
     * タブ補完機能 (howtouse は意図的に除外)
     */
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        val canGive = sender.isOp ||
                sender.hasPermission("resourcepackflow.admin.give")

        val result = when (args.size) {
            // /gacha <command>
            1 -> buildList {
                add("reset")

                if (
                    sender.isOp ||
                    sender.hasPermission("resourcepackflow.admin.login")
                ) {
                    add("login")
                }

                if (
                    sender.isOp ||
                    sender.hasPermission("resourcepackflow.admin.gachastand")
                ) {
                    add("stand")
                }

                if (canGive) {
                    add("give")
                }
                if (
                    sender.isOp ||
                    sender.hasPermission("resourcepackflow.admin.login")
                ) {
                    add("createmystand")
                }
            }

            // /gacha give <type>
            2 -> {
                if (args[0].equals("give", ignoreCase = true) && canGive) {
                    listOf("pack", "model")
                } else {
                    emptyList()
                }
            }

            // /gacha give pack|model <username>
            3 -> {
                if (
                    args[0].equals("give", ignoreCase = true) &&
                    (args[1].equals("pack", ignoreCase = true) ||
                            args[1].equals("model", ignoreCase = true)) &&
                    canGive
                ) {
                    modelConfigManager.getAllAuthorNames()
                } else {
                    emptyList()
                }
            }

            // /gacha give pack|model <username> <modelId>
            4 -> {
                if (
                    args[0].equals("give", ignoreCase = true) &&
                    (args[1].equals("pack", ignoreCase = true) ||
                            args[1].equals("model", ignoreCase = true)) &&
                    canGive
                ) {
                    modelConfigManager
                        .loadModelsByAuthor(args[2])
                        .keys
                        .toList()
                } else {
                    emptyList()
                }
            }

            else -> emptyList()
        }

        return result
            .filter { it.startsWith(args.last(), ignoreCase = true) }
            .sorted()
            .toMutableList()
    }

    /**
     * コマンドのヘルプ案内メッセージ
     */
    private fun sendUsageHelp(player: Player) {
        player.sendMessage("§a[ResourcePackFlow] §f使用方法:")
        if (player.hasPermission("resourcepackflow.admin.login") || player.isOp) {
            player.sendMessage("§7 - §e/gacha login §f: Web管理画面のログイン認証を行います。")
        }
        if (player.hasPermission("resourcepackflow.admin.gachastand") || player.isOp) {
            player.sendMessage("§7 - §e/gacha stand §f: 足元にガチャスタンドを召喚します。")
        }
        if (player.hasPermission("resourcepackflow.admin.login") || player.isOp) {
            player.sendMessage(
                "§7 - §e/gacha createmystand §f: 自分専用のガチャスタンドを作成します。"
            )
        }
        player.sendMessage("§7 - §e/gacha reset §f: メインハンドのアイテムのリソースパックを解除します。")
    }

    /**
     * Web ログイン認証処理 (/gacha login)
     */
    private fun handleLogin(player: Player) {
        if (!player.hasPermission("resourcepackflow.admin.login") && !player.isOp) {
            player.sendMessage("§c[ResourcePackFlow] このコマンドを実行する権限がありません。")
            return
        }

        val newPassword = authManager.generateAndRegister(player.uniqueId)
        val host = plugin.configManager.selectPackUrl(player)
        val loginUrl = "$host/login"

        player.sendMessage("§6=========================================")
        player.sendMessage("§e Web管理画面 ログイン認証")
        player.sendMessage("§7 ※パスワードの有効期限は §c5分間 §7です。")
        player.sendMessage("")

        val copyButton = TextComponent(" [パスワードをコピー] ").apply {
            color = ChatColor.GREEN
            isBold = true
            clickEvent = ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, newPassword)
            hoverEvent = HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                ComponentBuilder("§aクリックしてパスワードをクリップボードにコピー").create()
            )
        }

        val passMessage = TextComponent(" 1. ").apply {
            color = ChatColor.GRAY
            addExtra(copyButton)
        }
        player.spigot().sendMessage(passMessage)

        val urlButton = TextComponent(" [ログイン画面を開く] ").apply {
            color = ChatColor.AQUA
            isBold = true
            isUnderlined = true
            clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, loginUrl)
            hoverEvent = HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                ComponentBuilder("§bクリックしてブラウザでWeb画面を開く\n§7$loginUrl").create()
            )
        }

        val urlMessage = TextComponent(" 2. ").apply {
            color = ChatColor.GRAY
            addExtra(urlButton)
        }
        player.spigot().sendMessage(urlMessage)

        player.sendMessage("§6=========================================")
    }

    /**
     * パックアイテム使用方法の案内メッセージ (/gacha howtouse)
     */
    private fun handleHowToUse(player: Player) {
        player.sendMessage("§e==================================================")
        player.sendMessage("§a[Gacha] §b§lカスタムリソースパック適用ガイド")
        player.sendMessage("§e==================================================")
        player.sendMessage("§fパックアイテムのモデルを手持ちアイテムへ着せ替える手順:")
        player.sendMessage("")
        player.sendMessage("§6 1. §f適用させたい元のアイテムを §e§lメインハンド §fに持ちます。")
        player.sendMessage("§6 2. §f入手したパックを §e§lオフハンド（左手） §fに持ちます。")
        player.sendMessage("§6 3. §fその状態で §b§l右クリック §fを押すと着せ替えが完了します！")
        player.sendMessage("§8 ※ 一部パックは特定のアイテムにしか着せられません")
        player.sendMessage("§8 ※ モデルを削除したい場合は手持ちで §e/gacha reset §8を実行してください。")
        player.sendMessage("§e==================================================")
    }

    /**
     * メインハンドのアイテムからリソースパックを削除・復元 (/gacha reset)
     */
    private fun handleReset(player: Player) {
        val targetItem = player.inventory.itemInMainHand

        val success = GachaItemBuilder.removeModelFromItem(targetItem)

        if (success) {
            player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 0.8f, 1.2f)
            player.sendMessage("§a[Gacha] メインハンドのアイテムからリソースパックを解除し、元の状態に復元しました！")
        } else {
            player.sendMessage("§c[Gacha] メインハンドのアイテムには解除可能なリソースパックが適用されていません。")
        }
    }

    /**
     * ガチャスタンドの召喚処理 (/gacha stand)
     */
    private fun handleSummon(player: Player) {
        if (!player.hasPermission("resourcepackflow.admin.gachastand") && !player.isOp) {
            player.sendMessage("§c[GachaStand] ガチャスタンドを召喚する権限がありません。")
            return
        }

        try {
            // ★ Player オブジェクトを渡して視点方向と足元座標を取得・生成
            GachaStandManager.createGachaStand(player)

            val loc = player.location
            player.playSound(loc, Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f)
            player.sendMessage("§a[GachaStand] 向きに合わせてガチャスタンドを召喚しました！")
        } catch (e: Exception) {
            player.sendMessage("§c[GachaStand] ガチャスタンドの生成中にエラーが発生しました: ${e.message}")
            e.printStackTrace()
        }
    }


    private fun handleGive(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行可能です。")
            return
        }

        if (!sender.isOp &&
            !sender.hasPermission("resourcepackflow.admin.give")
        ) {
            sender.sendMessage("§cこのコマンドを実行する権限がありません。")
            return
        }

        // /gacha give pack|model <ユーザー名> <モデルID>
        if (args.size != 4) {
            sender.sendMessage("§e使用方法:")
            sender.sendMessage("§7/gacha give pack <ユーザー名> <モデルID>")
            sender.sendMessage("§7/gacha give model <ユーザー名> <モデルID>")
            return
        }

        val type = args[1].lowercase()
        val authorName = args[2]
        val modelId = args[3].lowercase()

        if (type != "pack" && type != "model") {
            sender.sendMessage("§c種別は pack または model を指定してください。")
            return
        }

        // 指定されたユーザーの登録モデルを取得
        val models = modelConfigManager.loadModelsByAuthor(authorName)

        val data = models.entries.firstOrNull {
            it.key.equals(modelId, ignoreCase = true) ||
                    it.value.modelId.equals(modelId, ignoreCase = true)
        }?.value

        if (data == null) {
            sender.sendMessage(
                "§cユーザー '$authorName' のモデル '$modelId' が見つかりません。"
            )
            return
        }

        // pack はパックアイテム、model は完成品アイテム
        val item = when (type) {
            "pack" -> GachaItemBuilder.createPackItem(data)
            "model" -> GachaItemBuilder.createModelItem(data)
            else -> return
        }

        // インベントリに追加し、入りきらない分は足元にドロップ
        val leftovers = sender.inventory.addItem(item)

        leftovers.values.forEach { leftover ->
            sender.world.dropItemNaturally(sender.location, leftover)
        }

        val itemType = if (type == "pack") "パック" else "完成品モデル"

        sender.sendMessage(
            "§a[ Gacha ] §f${authorName} のモデル '$modelId' の${itemType}を付与しました。"
        )
    }
    /**
     * 自分専用のガチャスタンド作成 (/gacha createmystand)
     */
    private fun handleCreateMyStand(player: Player) {

        // login と同じ権限
        if (
            !player.hasPermission("resourcepackflow.admin.login") &&
            !player.isOp
        ) {
            player.sendMessage(
                "§c[GachaStand] このコマンドを実行する権限がありません。"
            )
            return
        }

        try {
            GachaStandManager.createMyGachaStand(player , authManager)

            player.playSound(
                player.location,
                Sound.BLOCK_ANVIL_USE,
                0.8f,
                1.2f
            )

            player.sendMessage(
                "§a[GachaStand] あなた専用のガチャスタンドを作成しました！"
            )

        } catch (e: Exception) {
            player.sendMessage(
                "§c[GachaStand] 生成に失敗しました: ${e.message}"
            )
            e.printStackTrace()
        }
    }
}