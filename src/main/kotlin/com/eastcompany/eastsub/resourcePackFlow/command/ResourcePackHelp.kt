package com.eastcompany.eastsub.resourcePackFlow.command

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ResourcePackHelp {

    fun send(
        sender: CommandSender,
        page: Int?
    ) {
        when (page) {
            null -> sendIndex(sender)
            1 -> sendResourcePackHelp(sender)
            2 -> sendTheHatHelp(sender)
            3 -> sendTheHatCreateHelp(sender)
            4 -> sendResourcePackAddHelp(sender)

            else -> {
                sender.sendMessage(
                    "${org.bukkit.ChatColor.RED}" +
                            "ヘルプページは 1～4 を指定してください。"
                )

                sendIndex(sender)
            }
        }
    }

    // ==================================================
    // Help Index
    // ==================================================

    private fun sendIndex(sender: CommandSender) {
        sender.sendMessage("")
        sender.sendMessage(
            "${org.bukkit.ChatColor.GOLD}" +
                    "========== ResourcePackFlow Help =========="
        )

        sender.sendMessage("")
        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}/resourcepack help 1" +
                    "${org.bukkit.ChatColor.GRAY} - ResourcePackコマンド"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}/resourcepack help 2" +
                    "${org.bukkit.ChatColor.GRAY} - TheHatコマンド"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}/resourcepack help 3" +
                    "${org.bukkit.ChatColor.GRAY} - TheHatモデルの作り方"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}/resourcepack help 4" +
                    "${org.bukkit.ChatColor.GRAY} - リソースパック追加方法"
        )

        sender.sendMessage("")

        // プレイヤーならクリックでもページを開ける
        if (sender is Player) {
            val component = ComponentBuilder()
                .append("[1: ResourcePack]")
                .color(ChatColor.GREEN)
                .event(
                    ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/resourcepack help 1"
                    )
                )
                .event(
                    HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        ComponentBuilder("ResourcePackコマンドを見る")
                            .color(ChatColor.YELLOW)
                            .create()
                    )
                )

                .append("  ")

                .append("[2: TheHat]")
                .color(ChatColor.AQUA)
                .event(
                    ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/resourcepack help 2"
                    )
                )
                .event(
                    HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        ComponentBuilder("TheHatコマンドを見る")
                            .color(ChatColor.YELLOW)
                            .create()
                    )
                )

                .append("  ")

                .append("[3: 作り方]")
                .color(ChatColor.LIGHT_PURPLE)
                .event(
                    ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/resourcepack help 3"
                    )
                )
                .event(
                    HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        ComponentBuilder("TheHatモデルの作り方を見る")
                            .color(ChatColor.YELLOW)
                            .create()
                    )
                )

                .append("  ")

                .append("[4: 追加方法]")
                .color(ChatColor.GOLD)
                .event(
                    ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/resourcepack help 4"
                    )
                )
                .event(
                    HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        ComponentBuilder("リソースパックの追加方法を見る")
                            .color(ChatColor.YELLOW)
                            .create()
                    )
                )
                .create()

            sender.spigot().sendMessage(*component)
        }

        sender.sendMessage("")
        sender.sendMessage(
            "${org.bukkit.ChatColor.GOLD}" +
                    "==========================================="
        )
    }

    // ==================================================
    // Help 1
    // ResourcePack
    // ==================================================

    private fun sendResourcePackHelp(sender: CommandSender) {
        sender.sendMessage("")
        sender.sendMessage(
            "${org.bukkit.ChatColor.GOLD}" +
                    "===== ResourcePackFlow Help 1/3 ====="
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.AQUA}${org.bukkit.ChatColor.BOLD}" +
                    "ResourcePack コマンド"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}/resourcepack apply"
        )
        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "自分に現在のリソースパックを送信します。"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}/resourcepack download"
        )
        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "基本パックとアドオンのダウンロードリンクを表示します。"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}/resourcepack setting"
        )
        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "リソースパックの個人設定GUIを開きます。"
        )
        sender.sendMessage(
            "${org.bukkit.ChatColor.DARK_GRAY}" +
                    "ログイン時の適用方法や通知方法を変更できます。"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}/resourcepack info"
        )
        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "現在のリソースパック配信設定を表示します。"
        )

        if (sender.hasPermission("resourcepackflow.apply.others")) {
            sender.sendMessage("")

            sender.sendMessage(
                "${org.bukkit.ChatColor.YELLOW}" +
                        "/resourcepack apply <player> <true|false>"
            )

            sender.sendMessage(
                "${org.bukkit.ChatColor.GRAY}" +
                        "指定したプレイヤーへリソースパックを送信します。"
            )

            sender.sendMessage(
                "${org.bukkit.ChatColor.GREEN}true" +
                        "${org.bukkit.ChatColor.GRAY}" +
                        "  - 即時リソースパックを送信"
            )

            sender.sendMessage(
                "${org.bukkit.ChatColor.YELLOW}false" +
                        "${org.bukkit.ChatColor.GRAY}" +
                        " - クリックして適用できるメッセージを送信"
            )
        }

        if (sender.hasPermission("resourcepackflow.build")) {
            sender.sendMessage("")

            sender.sendMessage(
                "${org.bukkit.ChatColor.YELLOW}/resourcepack build"
            )

            sender.sendMessage(
                "${org.bukkit.ChatColor.GRAY}" +
                        "resourcepackフォルダからpack.zipを生成します。"
            )
        }

        if (sender.hasPermission("resourcepackflow.reload")) {
            sender.sendMessage("")

            sender.sendMessage(
                "${org.bukkit.ChatColor.YELLOW}/resourcepack reload"
            )

            sender.sendMessage(
                "${org.bukkit.ChatColor.GRAY}" +
                        "config.ymlとaddons.ymlを再読み込みします。"
            )
        }

        if (sender.hasPermission(AddonCommand.PERMISSION)) {
            sender.sendMessage("")

            sender.sendMessage(
                "${org.bukkit.ChatColor.YELLOW}/resourcepack addon"
            )

            sender.sendMessage(
                "${org.bukkit.ChatColor.GRAY}" +
                        "アドオンの名前・アイコン・初期状態・詳細を設定します。"
            )
        }

        sendFooter(sender, 1)
    }

    // ==================================================
    // Help 2
    // TheHat Command
    // ==================================================

    private fun sendTheHatHelp(sender: CommandSender) {
        sender.sendMessage("")
        sender.sendMessage(
            "${org.bukkit.ChatColor.GOLD}" +
                    "===== ResourcePackFlow Help 2/3 ====="
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.AQUA}${org.bukkit.ChatColor.BOLD}" +
                    "TheHat コマンド"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}" +
                    "/thehat <階層> <アイテム名> <true|false>"
        )

        sender.sendMessage("")
        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "メインハンドに持っているアイテムへ、"
        )
        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "指定したTheHatモデルを適用します。"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.WHITE}＜階層＞"
        )
        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "設定した自分の名前 modelsフォルダ内のフォルダ名を指定します。"
        )
        sender.sendMessage(
            "${org.bukkit.ChatColor.DARK_GRAY}" +
                    "例: taro"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.WHITE}＜アイテム名＞"
        )
        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "適用したいモデルのJSONファイル名を指定します。"
        )
        sender.sendMessage(
            "${org.bukkit.ChatColor.DARK_GRAY}" +
                    "例: hat.json → hat"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.WHITE}＜true / false＞"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.GREEN}true" +
                    "${org.bukkit.ChatColor.GRAY}" +
                    "  - 頭に装備可能にする"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}false" +
                    "${org.bukkit.ChatColor.GRAY}" +
                    " - 頭への装備機能を追加しない"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.WHITE}使用例:"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}" +
                    "/thehat taro hat true"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "→ taro/hat のモデルを適用し、頭に装備可能にします。"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}/thehat reset"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "手に持っているアイテムからTheHatモデルを解除します。"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.DARK_GRAY}" +
                    "Equippableも元アイテムの状態へ戻します。"
        )

        sendFooter(sender, 2)
    }

    // ==================================================
    // Help 3
    // TheHat Create
    // ==================================================

    private fun sendTheHatCreateHelp(sender: CommandSender) {
        sender.sendMessage("")
        sender.sendMessage(
            "${org.bukkit.ChatColor.GOLD}" +
                    "===== ResourcePackFlow Help 3/3 ====="
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.AQUA}${org.bukkit.ChatColor.BOLD}" +
                    "TheHat モデルの作り方"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}① 自分用のフォルダを作成"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "以下の場所に自分の名前のフォルダを作成します。"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.WHITE}" +
                    "resourcepack/assets/thehat/models/<自分の名前>/"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}② 3Dモデルを配置"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "作成したフォルダへモデルのJSONファイルを入れます。"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.WHITE}例:"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "resourcepack/assets/thehat/models/taro/hat.json"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}③ リソースパックをビルド"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.WHITE}/resourcepack build"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "を実行してリソースパックを更新します。"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}④ モデルをアイテムへ適用"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "モデルを付けたいアイテムをメインハンドに持ちます。"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.WHITE}" +
                    "/thehat <自分の名前> <モデル名> <true|false>"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.WHITE}例:"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.GREEN}" +
                    "/thehat taro hat true"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "これで手に持っているアイテムにhatモデルが適用されます。"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.DARK_GRAY}" +
                    "※ JSONファイルの拡張子 .json はコマンドでは入力しません。"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.DARK_GRAY}" +
                    "※ trueにすると頭に装備できるアイテムになります。"
        )

        sendFooter(sender, 3)
    }

    // ==================================================
    // Footer
    // ==================================================

    private fun sendFooter(
        sender: CommandSender,
        currentPage: Int
    ) {
        sender.sendMessage("")

        if (sender is Player) {
            val builder = ComponentBuilder()

            if (currentPage > 1) {
                builder
                    .append("[← 前へ]")
                    .color(ChatColor.YELLOW)
                    .event(
                        ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            "/resourcepack help ${currentPage - 1}"
                        )
                    )
            }

            builder
                .append("  ")
                .append("[Help]")
                .color(ChatColor.AQUA)
                .event(
                    ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/resourcepack help"
                    )
                )

            if (currentPage < 4) {
                builder
                    .append("  ")
                    .append("[次へ →]")
                    .color(ChatColor.YELLOW)
                    .event(
                        ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            "/resourcepack help ${currentPage + 1}"
                        )
                    )
            }

            sender.spigot().sendMessage(*builder.create())
        } else {
            sender.sendMessage(
                "${org.bukkit.ChatColor.GRAY}" +
                        "/resourcepack help <1-4>"
            )
        }

        sender.sendMessage("")
        sender.sendMessage(
            "${org.bukkit.ChatColor.GOLD}" +
                    "======================================"
        )
    }

    // ==================================================
// Help 4
// ResourcePack Add
// ==================================================

    private fun sendResourcePackAddHelp(sender: CommandSender) {
        sender.sendMessage("")
        sender.sendMessage(
            "${org.bukkit.ChatColor.GOLD}" +
                    "===== ResourcePackFlow Help 4/4 ====="
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.AQUA}${org.bukkit.ChatColor.BOLD}" +
                    "リソースパックの追加方法"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}① ファイルを追加"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "追加したいリソースパックのファイルを以下のフォルダに追加します。"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.WHITE}" +
                    "plugins/ResourcePackFlow/resourcepack"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.YELLOW}② リソースパックをビルド"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.WHITE}/resourcepack build"
        )

        sender.sendMessage(
            "${org.bukkit.ChatColor.GRAY}" +
                    "を実行してリソースパックを更新します。"
        )

        sender.sendMessage("")

        sender.sendMessage(
            "${org.bukkit.ChatColor.RED}" +
                    "※ /resourcepack build の実行には resourcepackflow.build 権限が必要です。"
        )

        sendFooter(sender, 4)
    }
}