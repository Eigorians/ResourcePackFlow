package com.eastcompany.eastsub.resourcePackFlow.listener

import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import com.eastcompany.eastsub.resourcePackFlow.config.AutoApplyMode
import com.eastcompany.eastsub.resourcePackFlow.config.ResourcePackMode
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerJoinListener(
    private val plugin: ResourcePackFlow
) : Listener {

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        plugin.resourcePackManager.forget(event.player.uniqueId)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player

        // ログイン直後ではなく少し待ってから処理
        plugin.server.scheduler.runTaskLater(
            plugin,
            Runnable {
                handleResourcePack(player)
            },
            20L
        )
    }

    private fun handleResourcePack(player: Player) {
        if (!player.isOnline) {
            return
        }

        val settings = plugin.playerSettingsManager

        val currentHash =
            plugin.resourcePackManager.getCurrentPackHash()

        val lastHash = settings.getLastPackHash(
            player.uniqueId
        )

        // =========================
        // Server ResourcePack Mode
        // =========================

        val serverMode = plugin.configManager.resourcePackMode

        when (serverMode) {

            ResourcePackMode.FORCE -> {
                /*
                 * サーバー設定を最優先。
                 * 個人設定に関係なくリソースパックを適用する。
                 */
                plugin.resourcePackManager.applyTo(player)

                saveCurrentHash(
                    player,
                    currentHash
                )

                return
            }

            ResourcePackMode.MESSAGE -> {
                /*
                 * サーバー設定を最優先。
                 * 個人設定に関係なくメッセージを表示する。
                 */
                sendResourcePackMessage(player)

                saveCurrentHash(
                    player,
                    currentHash
                )

                return
            }

            ResourcePackMode.OFF -> {
                /*
                 * サーバー側からの強制指定なし。
                 * 以下の個人設定を使用する。
                 */
            }
        }

        // =========================
        // Player Settings
        // =========================

        val autoApplyMode = settings.getAutoApply(
            player.uniqueId
        )

        var messageRequired = false

        // =========================
        // Auto Apply
        // =========================

        when (autoApplyMode) {

            AutoApplyMode.APPLY -> {
                plugin.resourcePackManager.applyTo(player)
            }

            AutoApplyMode.MESSAGE -> {
                messageRequired = true
            }

            AutoApplyMode.OFF -> {
                // 何もしない
            }
        }

        // =========================
        // Notification
        // =========================

        // =========================
        // Message
        // =========================

        if (messageRequired) {
            sendResourcePackMessage(player)
        }

        // =========================
        // Pack Hash
        // =========================

        saveCurrentHash(
            player,
            currentHash
        )
    }

    private fun saveCurrentHash(
        player: Player,
        currentHash: String?
    ) {
        if (currentHash == null) {
            return
        }

        plugin.playerSettingsManager.setLastPackHash(
            player.uniqueId,
            currentHash
        )
    }

    private fun sendResourcePackMessage(player: Player) {

        val component = ComponentBuilder()
            // リソースパック適用
            .append("[リソースパックを適用]")
            .color(ChatColor.GREEN)
            .bold(true)
            .event(
                ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/resourcepack apply"
                )
            )

            .append("   ")
            .bold(false)

            // ダウンロード (基本パックとアドオンの候補を表示)
            .append("[ダウンロード]")
            .color(ChatColor.YELLOW)
            .bold(true)
            .event(
                ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/resourcepack download"
                )
            )

            .append("   ")
            .bold(false)

            // 通知設定
            .append("[通知設定を変更]")
            .color(ChatColor.AQUA)
            .bold(true)
            .event(
                ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/resourcepack setting"
                )
            )

            .create()

        player.spigot().sendMessage(*component)
    }
}