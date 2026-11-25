package com.eastcompany.eastsub.resourcePackFlow.config

import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import org.bukkit.entity.Player
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

class ConfigManager(
    private val plugin: ResourcePackFlow
) {

    init {
        // config.yml が存在しなければ生成
        plugin.saveDefaultConfig()
    }

    // =========================
    // HTTP Server
    // =========================

    /**
     * 内蔵HTTPサーバーの待受アドレス。
     *
     * 通常は 0.0.0.0 を指定し、
     * すべてのネットワークインターフェースで待ち受ける。
     */
    val httpBindHost: String
        get() = plugin.config.getString(
            "http.bind-host",
            "0.0.0.0"
        )!!.trim()

    /**
     * 内蔵HTTPサーバーの待受ポート。
     */
    val httpPort: Int
        get() = plugin.config.getInt(
            "http.port",
            8071
        )

    val publicUrl: String
        get() = "http://$publicHost:$httpPort"

    /**
     * 外部プレイヤーがアクセスするホスト名またはグローバルIP。
     *
     * 例:
     * example.com
     * 203.0.113.10
     */
    val publicHost: String
        get() = plugin.config.getString(
            "http.public-host",
            ""
        )!!.trim()

    /**
     * LAN内プレイヤー向けURL。
     *
     * "auto" の場合は ResourcePackManager 側で
     * プレイヤーに到達可能なLAN側IPv4を自動判定する。
     *
     * 手動指定する場合:
     * http://192.168.1.10:8071/pack.zip
     */
    val localUrl: String
        get() = plugin.config.getString(
            "http.local-url",
            "auto"
        )!!.trim()

    /**
     * 外部プレイヤー向けのリソースパックURL。
     */
    val publicPackUrl: String
        get() = "http://$publicHost:$httpPort/pack.zip"


    // =========================
    // Resource Pack
    // =========================

    val resourcePackDirectory: String
        get() = plugin.config.getString(
            "resource-pack.directory",
            "resourcepack"
        )!!.trim()

    /**
     * 追加パック(zip)を置くディレクトリ。
     * ここにある *.zip は本体とは別のリソースパックとして送信する。
     * プレイヤーは個人設定で、どのzipを使うか選べる。
     */
    val addonsDirectory: String
        get() = plugin.config.getString(
            "resource-pack.addons-directory",
            "addons"
        )!!.trim().ifBlank { "addons" }

    fun setResourcePackMode(mode: ResourcePackMode) {
        plugin.config.set(
            "resource-pack.mode",
            mode.configValue
        )

        plugin.saveConfig()
    }

    val resourcePackMode: ResourcePackMode
        get() = ResourcePackMode.fromConfig(
            plugin.config.getString(
                "resource-pack.mode",
                "off"
            ) ?: "off"
        )


    // =========================
    // Config操作
    // =========================

    fun reload() {
        plugin.reloadConfig()
    }

    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (httpBindHost.isBlank()) {
            errors += "http.bind-host が設定されていません。"
        }

        if (httpPort !in 1..65535) {
            errors += "http.port は 1～65535 の範囲で指定してください。"
        }

        if (publicHost.isBlank()) {
            errors += "http.public-host が設定されていません。"
        }

        if (localUrl.isBlank()) {
            errors += "http.local-url が設定されていません。"
        }

        if (resourcePackDirectory.isBlank()) {
            errors += "resource-pack.directory が設定されていません。"
        }

        return errors
    }
    val loginUrl: String
        get() {
            // ローカル（ループバック）でアクセスするためのURLとする場合
            return "http://127.0.0.1:$httpPort/login"
        }

    /**
     * プレイヤー個別の状況や設定に応じて最適なパックURLを返すメソッド（将来の拡張用）
     */
    fun getPackUrl(isLocalPlayer: Boolean = false): String {
        return if (isLocalPlayer && localUrl != "auto" && localUrl.isNotBlank()) {
            localUrl
        } else {
            publicPackUrl
        }
    }

    fun selectPackUrl(player: Player): String? {
        val playerAddress = player.address?.address

        if (playerAddress == null) {
            plugin.logger.warning(
                "プレイヤー ${player.name} の接続元IPを取得できません。public URLを使用します。"
            )
            return publicUrl
        }

        // localhost / loopback 接続の場合
        if (playerAddress.isLoopbackAddress) {
            return "http://127.0.0.1:$httpPort"
        }

        val isLan = playerAddress.isSiteLocalAddress ||
                playerAddress.isLinkLocalAddress

        // グローバルIPからの接続
        if (!isLan) {
            return publicUrl
        }

        val configuredLocalUrl = localUrl

        if (!configuredLocalUrl.equals("auto", ignoreCase = true)) {
            // 呼び出し側で "/pack.zip" や "/login" を付けるため、ベースURLに揃える
            // (旧形式の "http://192.168.1.10:8071/pack.zip" も受け付ける)
            return configuredLocalUrl
                .trimEnd('/')
                .removeSuffix("/pack.zip")
                .trimEnd('/')
        }

        val localIp = detectLocalIpv4For(playerAddress)

        if (localIp == null) {
            plugin.logger.warning(
                "プレイヤー ${player.name} (${playerAddress.hostAddress}) に対応するLAN側IPを自動取得できませんでした。public URLへフォールバックします。"
            )
            return publicUrl
        }

        return "http://$localIp:$httpPort"
    }

    private fun detectLocalIpv4For(playerAddress: InetAddress): String? {
        if (playerAddress !is Inet4Address) {
            return detectAnyLocalIpv4()
        }

        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null

        for (networkInterface in interfaces) {
            if (!networkInterface.isUp || networkInterface.isLoopback) {
                continue
            }

            for (interfaceAddress in networkInterface.interfaceAddresses) {
                val serverAddress = interfaceAddress.address

                if (serverAddress !is Inet4Address || !serverAddress.isSiteLocalAddress) {
                    continue
                }

                val prefixLength = interfaceAddress.networkPrefixLength.toInt()
                if (prefixLength !in 1..32) {
                    continue
                }

                if (isSameSubnet(serverAddress, playerAddress, prefixLength)) {
                    return serverAddress.hostAddress
                }
            }
        }

        return detectAnyLocalIpv4()
    }

    private fun detectAnyLocalIpv4(): String? {
        return NetworkInterface.getNetworkInterfaces()
            ?.toList()
            ?.asSequence()
            ?.filter { it.isUp && !it.isLoopback }
            ?.flatMap { it.inetAddresses.toList().asSequence() }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }

    private fun isSameSubnet(
        first: Inet4Address,
        second: Inet4Address,
        prefixLength: Int
    ): Boolean {
        val firstBytes = first.address
        val secondBytes = second.address

        var remainingBits = prefixLength

        for (index in firstBytes.indices) {
            if (remainingBits <= 0) {
                break
            }

            val bits = minOf(8, remainingBits)
            val mask = (0xFF shl (8 - bits)) and 0xFF

            val firstValue = firstBytes[index].toInt() and 0xFF
            val secondValue = secondBytes[index].toInt() and 0xFF

            if ((firstValue and mask) != (secondValue and mask)) {
                return false
            }

            remainingBits -= bits
        }

        return true
    }
}
