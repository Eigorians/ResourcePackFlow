package com.eastcompany.eastsub.gacha

import com.destroystokyo.paper.profile.PlayerProfile
import com.destroystokyo.paper.profile.ProfileProperty
import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager
import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager.PlayerData
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import java.util.*

object SkinSupport {

    private fun PlayerProfile.textures(): ProfileProperty? =
        properties.firstOrNull { it.name == "textures" }

    /** 保存済みスキンから PlayerProfile を組み立てる（ネットワークなし・即時） */
    fun buildProfile(user: PlayerData): PlayerProfile {
        val profile = Bukkit.createProfile(UUID.fromString(user.uuid), user.name)
        val value = user.skinValue
        if (value != null) {
            profile.setProperty(ProfileProperty("textures", value, user.skinSignature))
        }
        return profile
    }

    /** ログイン中プレイヤーのスキンを保存（問い合わせ不要） */
    fun saveFromOnline(manager: ResourcePackSessionManager, player: Player) {
        val tex = player.playerProfile.textures() ?: return
        manager.updateSkin(player.uniqueId, tex.value, tex.signature, player.name)
    }

    /** Mojang から非同期取得して保存。完了後に onDone(更新後の PlayerData) を呼ぶ（非同期スレッド） */
    fun fetchAndSave(
        manager: ResourcePackSessionManager,
        user: PlayerData,
        onDone: ((PlayerData) -> Unit)? = null
    ) {
        val uuid = UUID.fromString(user.uuid)
        Bukkit.createProfile(uuid, user.name).update().thenAccept { resolved ->
            val tex = resolved.textures() ?: return@thenAccept
            manager.updateSkin(uuid, tex.value, tex.signature)
            manager.loadPlayerDataByUuid(uuid)?.let { onDone?.invoke(it) }
        }.exceptionally { e ->
            Bukkit.getLogger().warning("[Skin] 取得失敗 ${user.name}: ${e.message}")
            null
        }
    }

    /** 起動時: スキン未保存の作者を、レート制限対策で間隔を空けて先読み */
    fun preloadMissing(plugin: Plugin, manager: ResourcePackSessionManager) {
        manager.loadAllUsers()
            .filter { it.skinValue == null }
            .forEachIndexed { i, user ->
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, Runnable {
                    fetchAndSave(manager, user)
                }, i * 20L)
            }
    }
}

class SkinJoinListener(private val manager: ResourcePackSessionManager) : Listener {
    @EventHandler
    fun onJoin(e: PlayerJoinEvent) = SkinSupport.saveFromOnline(manager, e.player)
}