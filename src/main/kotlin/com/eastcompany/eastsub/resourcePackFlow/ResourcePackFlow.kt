package com.eastcompany.eastsub.resourcePackFlow

import com.eastcompany.eastsub.gacha.GachaMain
import com.eastcompany.eastsub.resourcePackFlow.command.ResourcePackCommand
import com.eastcompany.eastsub.resourcePackFlow.command.TheHatCommand
import com.eastcompany.eastsub.resourcePackFlow.config.AddonSettingsManager
import com.eastcompany.eastsub.resourcePackFlow.config.ConfigManager
import com.eastcompany.eastsub.resourcePackFlow.config.PlayerSettingsManager
import com.eastcompany.eastsub.resourcePackFlow.gui.PlayerSettingsGui
import com.eastcompany.eastsub.resourcePackFlow.http.PackHttpServer
import com.eastcompany.eastsub.resourcePackFlow.listener.PlayerJoinListener
import com.eastcompany.eastsub.resourcePackFlow.pack.ResourcePackManager
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.jar.JarFile

class ResourcePackFlow : JavaPlugin() {

    lateinit var configManager: ConfigManager
        private set

    lateinit var resourcePackManager: ResourcePackManager
        private set

    lateinit var packHttpServer: PackHttpServer
        private set

    lateinit var playerSettingsManager: PlayerSettingsManager
        private set

    lateinit var addonSettingsManager: AddonSettingsManager
        private set

    lateinit var playerSettingsGui: PlayerSettingsGui
        private set

    lateinit var gachaMain: GachaMain
        private set

    override fun onEnable() {
        // 1. 設定ファイルおよび各種マネージャーの読み込み
        configManager = ConfigManager(this)
        playerSettingsManager = PlayerSettingsManager(this)
        addonSettingsManager = AddonSettingsManager(this)

        // 設定のバリデーションチェック
        val errors = configManager.validate()
        if (errors.isNotEmpty()) {
            logger.warning("設定に問題があります。")
            errors.forEach { logger.warning("- $it") }
        }

        // 2. リソースパックの初期セットアップ
        setupResourcePack()

        // 3. HTTP Server 起動
        packHttpServer = PackHttpServer(this)
        packHttpServer.ensureRunning()

        // 4. リソースパックマネージャー初期化
        resourcePackManager = ResourcePackManager(this)

        // 5. コマンド・イベントリスナーの登録
        registerCommandsAndListeners()

        // 6. ガチャサブシステムの初期化
        gachaMain = GachaMain(this, packHttpServer)
        gachaMain.init()

        logger.info("ResourcePackFlow が有効になりました。")
    }

    private fun registerCommandsAndListeners() {
        // コマンド登録
        val resourcePackCommand = ResourcePackCommand(this)
        getCommand("resourcepack")?.apply {
            setExecutor(resourcePackCommand)
            tabCompleter = resourcePackCommand
        } ?: logger.severe("plugin.yml に resourcepack コマンドが登録されていません。")

        val theHatCommand = TheHatCommand(this)
        getCommand("thehat")?.apply {
            setExecutor(theHatCommand)
            tabCompleter = theHatCommand
        }

        // GUIおよびイベント登録
        playerSettingsGui = PlayerSettingsGui(this)
        server.pluginManager.registerEvents(playerSettingsGui, this)
        server.pluginManager.registerEvents(PlayerJoinListener(this), this)
    }

    /**
     * resourcepack ディレクトリが存在しない場合、
     * jar 内の resourcepack/ をデータフォルダへコピーする。
     */
    private fun setupResourcePack() {
        val directoryName = configManager.resourcePackDirectory
        val targetDirectory = File(dataFolder, directoryName)

        if (!targetDirectory.exists()) {
            logger.info("リソースパックディレクトリを作成します...")
        }

        // 毎回実行して、不足しているファイルだけを追加する
        copyResourceDirectory(
            sourcePath = "resourcepack",
            targetDirectory = targetDirectory
        )

        logger.info("初期リソースパックの確認が完了しました: ${targetDirectory.absolutePath}")
    }

    /**
     * jar 内の指定ディレクトリを再帰的にコピーする。
     */
    private fun copyResourceDirectory(
        sourcePath: String,
        targetDirectory: File
    ) {
        try {
            val cleanSourcePath = sourcePath.trim('/')
            val prefix = "$cleanSourcePath/"

            val jarFile = File(javaClass.protectionDomain.codeSource.location.toURI())

            JarFile(jarFile).use { jar ->
                val entries = jar.entries()

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entryName = entry.name

                    if (entryName.startsWith(prefix) && !entry.isDirectory) {
                        val relativePath = entryName.substring(prefix.length)
                        val targetFile = File(targetDirectory, relativePath)

                        if (targetFile.exists()) {
                            continue
                        }

                        targetFile.parentFile?.mkdirs()

                        jar.getInputStream(entry).use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.severe("初期リソースパックのコピーに失敗しました: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDisable() {
        if (::packHttpServer.isInitialized) {
            packHttpServer.stop()
        }

        if (::gachaMain.isInitialized) {
            gachaMain.shutdown()
        }

        logger.info("ResourcePackFlow が無効になりました。")
    }
}