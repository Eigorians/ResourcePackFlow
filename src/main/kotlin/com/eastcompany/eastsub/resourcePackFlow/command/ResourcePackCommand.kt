package com.eastcompany.eastsub.resourcePackFlow.command

import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ResourcePackCommand(
    private val plugin: ResourcePackFlow
) : CommandExecutor, TabCompleter {

    private val help = ResourcePackHelp()
    private val addonCommand = AddonCommand(plugin)
    private val downloadCommand = DownloadCommand(plugin)

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (args.isEmpty()) {
            help.send(sender, null)
            return true
        }

        when (args[0].lowercase()) {
            "build" -> handleBuild(sender)
            "apply" -> handleApply(sender, args)
            "info" -> handleInfo(sender)
            "reload" -> handleReload(sender)
            "setting" -> handleSetting(sender)
            "addon" -> addonCommand.execute(sender, args)
            "download" -> downloadCommand.execute(sender)
            "help" -> {
                val page = args.getOrNull(1)?.toIntOrNull()
                help.send(sender, page)
            }
            "api" -> handleApi(sender, args)

            else -> {
                sender.sendMessage(
                    "${ChatColor.RED}[リソースパック] 不明なサブコマンドです。"
                )
                help.send(sender, null)
            }
        }

        return true
    }

    // =========================
    // Build
    // =========================

    private fun handleBuild(sender: CommandSender) {
        if (!sender.hasPermission("resourcepackflow.build")) {
            sender.sendMessage(
                "${ChatColor.RED}このコマンドを実行する権限がありません。"
            )
            return
        }

        sender.sendMessage(
            "${ChatColor.YELLOW}[ResourcePackFlow] リソースパックをビルドしています..."
        )

        plugin.server.scheduler.runTaskAsynchronously(
            plugin,
            Runnable {
                try {
                    val result = plugin.resourcePackManager.build()

                    plugin.server.scheduler.runTask(
                        plugin,
                        Runnable {
                            sender.sendMessage(
                                "${ChatColor.GREEN}[リソースパック] ビルドが完了しました。"
                            )

                            sender.sendMessage(
                                "${ChatColor.GRAY}SHA-1: ${result.sha1}"
                            )

                            sender.sendMessage(
                                "${ChatColor.GRAY}Size: ${result.size} bytes"
                            )
                        }
                    )

                } catch (e: Exception) {
                    plugin.logger.severe(
                        "リソースパックのビルドに失敗しました: ${e.message}"
                    )
                    e.printStackTrace()

                    plugin.server.scheduler.runTask(
                        plugin,
                        Runnable {
                            sender.sendMessage(
                                "${ChatColor.RED}[ResourcePackFlow] ビルドに失敗しました。"
                            )

                            if (e.message != null) {
                                sender.sendMessage(
                                    "${ChatColor.RED}${e.message}"
                                )
                            }
                        }
                    )
                }
            }
        )
    }

    // =========================
    // Apply
    // =========================

    private fun handleApply(
        sender: CommandSender,
        args: Array<out String>
    ) {
        val targets: List<Player>
        val force: Boolean

        when (args.size) {

            // /resourcepack apply
            1 -> {
                if (sender !is Player) {
                    sender.sendMessage(
                        "${ChatColor.RED}コンソールから実行する場合はプレイヤー名を指定してください。"
                    )
                    return
                }

                targets = listOf(sender)
                force = true
            }

            // /resourcepack apply <player|@a>
            2 -> {
                targets = getTargets(sender, args[1]) ?: return
                force = false
            }

            // /resourcepack apply <player|@a> <true|false>
            3 -> {
                targets = getTargets(sender, args[1]) ?: return

                force = when (args[2].lowercase()) {
                    "true" -> true
                    "false" -> false

                    else -> {
                        sender.sendMessage(
                            "${ChatColor.RED}true または false を指定してください。"
                        )
                        return
                    }
                }
            }

            else -> {
                sender.sendMessage(
                    "${ChatColor.RED}使用方法: /resourcepack apply [player|@a] [true|false]"
                )
                return
            }
        }

        // 他人へ強制適用する場合だけ権限が必要
        // 他人を対象にする場合は権限が必要
        if (
            targets.any { it != sender } &&
            !sender.hasPermission("resourcepackflow.apply.others")
        ) {
            sender.sendMessage(
                "${ChatColor.RED}他のプレイヤーにリソースパックを送信する権限がありません。"
            )
            return
        }

        // =========================
        // 即時適用
        // =========================

        if (force) {
            var successCount = 0
            var failedCount = 0

            targets.forEach { target ->
                if (plugin.resourcePackManager.applyTo(target)) {
                    successCount++
                } else {
                    failedCount++
                }
            }

            // 自分だけの場合は従来どおり余計なメッセージを出さない
            if (targets.size == 1 && targets[0] == sender) {
                if (failedCount > 0) {
                    sender.sendMessage(
                        "${ChatColor.RED}リソースパック送信に失敗しました。"
                    )
                }
                return
            }

            sender.sendMessage(
                "${ChatColor.GREEN}[リソースパック] ${successCount}人に送信しました。"
            )

            if (failedCount > 0) {
                sender.sendMessage(
                    "${ChatColor.RED}${failedCount}人への送信に失敗しました。"
                )
            }

            return
        }

        // =========================
        // クリックして適用
        // =========================

        val message = ComponentBuilder("[リソースパック] ")
            .color(ChatColor.YELLOW)
            .append("[ここをクリックして適用]")
            .color(ChatColor.GREEN)
            .event(
                ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/resourcepack apply"
                )
            )
            .event(
                HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    ComponentBuilder("クリックして適用")
                        .color(ChatColor.YELLOW)
                        .create()
                )
            )
            .create()

        targets.forEach { target ->
            target.spigot().sendMessage(*message)
        }

        if (!(targets.size == 1 && targets[0] == sender)) {
            sender.sendMessage(
                "${ChatColor.GREEN}[リソースパック] ${targets.size}人に適用メッセージを送信しました。"
            )
        }
    }

    private fun getTargets(
        sender: CommandSender,
        argument: String
    ): List<Player>? {

        // @a = オンラインプレイヤー全員
        if (argument.equals("@a", ignoreCase = true)) {
            val players = plugin.server.onlinePlayers.toList()

            if (players.isEmpty()) {
                sender.sendMessage(
                    "${ChatColor.RED}オンラインのプレイヤーがいません。"
                )
                return null
            }

            return players
        }

        // 通常のプレイヤー名
        val player = plugin.server.getPlayer(argument)

        if (player == null) {
            sender.sendMessage(
                "${ChatColor.RED}プレイヤー '$argument' が見つかりません。"
            )
            return null
        }

        return listOf(player)
    }

    // =========================
    // Info
    // =========================

    private fun handleInfo(sender: CommandSender) {
        val config = plugin.configManager

        sender.sendMessage(
            "${ChatColor.GOLD}===== リソースパック ====="
        )

        sender.sendMessage(
            "${ChatColor.GRAY}ResourcePack Directory: " +
                    "${ChatColor.WHITE}${config.resourcePackDirectory}"
        )

        sender.sendMessage(
            "${ChatColor.GRAY}HTTP Bind Host: " +
                    "${ChatColor.WHITE}${config.httpBindHost}"
        )

        sender.sendMessage(
            "${ChatColor.GRAY}HTTP Port: " +
                    "${ChatColor.WHITE}${config.httpPort}"
        )

        val publicHost = config.publicHost
        val publicHostError = getPublicHostError(publicHost)

        if (publicHostError != null) {
            sender.sendMessage(
                "${ChatColor.RED}Public Host: $publicHost"
            )

            sender.sendMessage(
                "${ChatColor.RED}設定エラー: $publicHostError"
            )

            sender.sendMessage(
                "${ChatColor.YELLOW}正しい例: " +
                        "${ChatColor.WHITE}public-host: \"mc.example.jp\""
            )

            sender.sendMessage(
                "${ChatColor.YELLOW}IPを使う場合: " +
                        "${ChatColor.WHITE}public-host: \"203.0.113.10\""
            )
        } else {
            sender.sendMessage(
                "${ChatColor.GRAY}Public Host: " +
                        "${ChatColor.WHITE}$publicHost"
            )

            sender.sendMessage(
                "${ChatColor.GRAY}Public Pack URL: " +
                        "${ChatColor.WHITE}${config.publicPackUrl}"
            )
        }

        sender.sendMessage(
            "${ChatColor.GRAY}Local URL: " +
                    "${ChatColor.WHITE}${config.localUrl}"
        )
    }

    private fun getPublicHostError(host: String): String? {

        if (host.isBlank()) {
            return "public-host が設定されていません。"
        }

        if (host.equals("example.com", ignoreCase = true)) {
            return "public-host が初期値 example.com のままです。"
        }

        if (host.startsWith("http://", ignoreCase = true) ||
            host.startsWith("https://", ignoreCase = true)
        ) {
            return "public-host には http:// や https:// を含めないでください。"
        }

        if (host.contains("/")) {
            return "public-host には /pack.zip などのパスを含めないでください。"
        }

        if (host.contains(":")) {
            return "public-host にはポート番号を含めないでください。port は別項目で設定してください。"
        }

        val isIpv4 = host.matches(
            Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        )

        if (isIpv4) {
            val valid = host.split(".")
                .mapNotNull { it.toIntOrNull() }
                .let { parts ->
                    parts.size == 4 && parts.all { it in 0..255 }
                }

            if (!valid) {
                return "IPv4アドレスの形式が正しくありません。"
            }

            return null
        }

        val isDomain = host.matches(
            Regex(
                """^(?=.{1,253}$)([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$"""
            )
        )

        if (!isDomain) {
            return "ドメイン名またはIPv4アドレスの形式が正しくありません。"
        }

        return null
    }

    // =========================
    // Reload
    // =========================

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("resourcepackflow.reload")) {
            sender.sendMessage(
                "${ChatColor.RED}このコマンドを実行する権限がありません。"
            )
            return
        }

        sender.sendMessage(
            "${ChatColor.YELLOW}[ResourcePackFlow] 再読み込みを開始します..."
        )

        try {
            // 1. 設定を再読み込み
            plugin.configManager.reload()
            if (!plugin.addonSettingsManager.reload()) {
                sender.sendMessage(
                    "${ChatColor.RED}addons.yml の書式に誤りがあるため、アドオン設定は再読み込みしていません。"
                )
            }

            // 2. 設定を検証
            val errors = plugin.configManager.validate()
            if (errors.isNotEmpty()) {
                sender.sendMessage(
                    "${ChatColor.RED}設定に問題があるため、再読み込みを中止しました。"
                )
                errors.forEach {
                    sender.sendMessage("${ChatColor.RED}- $it")
                }
                return
            }

            // 3. 自分が担当しているHTTPサーバーだけを停止
            plugin.packHttpServer.stop()

            // 4. HTTPサーバーの起動を確認
            val serverStarted = plugin.packHttpServer.ensureRunning()
            if (!serverStarted) {
                sender.sendMessage(
                    "${ChatColor.RED}HTTPサーバーを起動できませんでした。ログを確認してください。"
                )
                return
            }

            // 5. Geminiを再生成し、HTTPハンドラーを登録
            plugin.gachaMain.reloadGemini()
            plugin.gachaMain.reloadHttpHandlers()

            sender.sendMessage(
                "${ChatColor.GREEN}[ResourcePackFlow] 設定・HTTPサーバー・Geminiの再読み込みが完了しました。"
            )
        } catch (e: Exception) {
            plugin.logger.severe(
                "再読み込みに失敗しました: ${e.message}"
            )
            e.printStackTrace()

            sender.sendMessage(
                "${ChatColor.RED}再読み込みに失敗しました。サーバーログを確認してください。"
            )
        }
    }

    // =========================
    // Setting
    // =========================

    private fun handleSetting(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(
                "${ChatColor.RED}このコマンドはプレイヤーのみ実行できます。"
            )
            return
        }

        plugin.playerSettingsGui.open(sender)
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

        // /resourcepack <...>
        if (args.size == 1) {
            val commands = mutableListOf(
                "apply",
                "download",
                "info",
                "setting",
                "help"
            )

            if (sender.hasPermission("resourcepackflow.build")) {
                commands += "build"
            }

            if (sender.hasPermission("resourcepackflow.reload")) {
                commands += "reload"
            }

            if (sender.hasPermission(AddonCommand.PERMISSION)) {
                commands += "addon"
            }

            return commands.filter {
                it.startsWith(args[0], ignoreCase = true)
            }
        }

        // /resourcepack addon <...>
        if (args.size > 1 && args[0].equals("addon", ignoreCase = true)) {
            return addonCommand.complete(sender, args)
        }

        // /resourcepack apply <player>
        if (
            args.size == 2 &&
            args[0].equals("apply", ignoreCase = true) &&
            sender.hasPermission("resourcepackflow.apply.others")
        ) {
            return plugin.server.onlinePlayers
                .map { it.name }
                .filter {
                    it.startsWith(args[1], ignoreCase = true)
                }
        }

        // /resourcepack apply <player> <true|false>
        if (
            args.size == 3 &&
            args[0].equals("apply", ignoreCase = true) &&
            sender.hasPermission("resourcepackflow.apply.others")
        ) {
            return listOf(
                "true",
                "false"
            ).filter {
                it.startsWith(args[2], ignoreCase = true)
            }
        }

        return emptyList()
    }

    private fun handleApi(
        sender: CommandSender,
        args: Array<out String>
    ) {
        if (!sender.hasPermission("resourcepackflow.api")) {
            sender.sendMessage(
                "${ChatColor.RED}このコマンドを実行する権限がありません。"
            )
            return
        }

        if (args.size < 2) {
            sender.sendMessage(
                "${ChatColor.YELLOW}/resourcepack api <APIキー>"
            )
            sender.sendMessage(
                "${ChatColor.YELLOW}/resourcepack api status"
            )
            return
        }

        if (args[1].equals("status", ignoreCase = true)) {
            // APIキーの値は表示しない
            sender.sendMessage(
                "${ChatColor.GREEN}APIキーの設定状態を確認してください。"
            )
            return
        }

        val apiKey = args[1].trim()
        if (apiKey.isBlank()) {
            sender.sendMessage("${ChatColor.RED}APIキーを指定してください。")
            return
        }

        // TODO: ConfigManagerにAPIキーを保存する処理を実装
        // plugin.configManager.setApiKey(apiKey)

        sender.sendMessage(
            "${ChatColor.GREEN}APIキーを受け付けました。保存処理とGeminiの再読み込みを接続してください。"
        )
    }


}