package com.eastcompany.eastsub.gacha

import com.eastcompany.eastsub.gacha.ai.GeminiClient
import com.eastcompany.eastsub.gacha.ai.GeminiFactory
import com.eastcompany.eastsub.gacha.api.VaultHook
import com.eastcompany.eastsub.gacha.command.GachaCommand
import com.eastcompany.eastsub.gacha.history.GachaHistory
import com.eastcompany.eastsub.gacha.listener.GachaStandListener
import com.eastcompany.eastsub.gacha.listener.PackOpenListener
import com.eastcompany.eastsub.gacha.model.GachaSettingManager
import com.eastcompany.eastsub.gacha.model.ModelConfigManager
import com.eastcompany.eastsub.gacha.stand.GachaStandManager
import com.eastcompany.eastsub.resourcePackFlow.ResourcePackFlow
import com.eastcompany.eastsub.resourcePackFlow.http.PackHttpServer
import com.eastcompany.eastsub.gacha.web.handler.ExistingItemHandler
import com.eastcompany.eastsub.gacha.web.handler.LogoutHandler
import com.eastcompany.eastsub.gacha.web.handler.PackBulkEditHandler
import com.eastcompany.eastsub.gacha.web.handler.PackEditHandler
import com.eastcompany.eastsub.gacha.web.handler.PackHistoryHandler
import com.eastcompany.eastsub.gacha.web.handler.PackHomeHandler
import com.eastcompany.eastsub.gacha.web.handler.PackListHandler
import com.eastcompany.eastsub.gacha.web.handler.PackLoginHandler
import com.eastcompany.eastsub.gacha.web.handler.PackSettingsIoHandler
import com.eastcompany.eastsub.gacha.web.handler.PackUploadHandler
import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager

class GachaMain(
    private val plugin: ResourcePackFlow,
    private val packHttpServer: PackHttpServer
) {

    lateinit var authManager: ResourcePackSessionManager
        private set

    lateinit var modelConfigManager: ModelConfigManager
        private set

    private val gachaSettingManager by lazy {
        GachaSettingManager(plugin.dataFolder, plugin.logger)
    }

    lateinit var vaultHook: VaultHook
        private set

    /**
     * 現在使用中のGeminiクライアント。
     *
     * config.yml の gemini.api-key / gemini.model を
     * リロードした際に、このインスタンスを作り直す。
     */
    private var geminiClient: GeminiClient? = null

    /**
     * /add-existing 用のHandler。
     *
     * HTTPコンテキスト自体は維持し、
     * リロード時には内部のGeminiClientだけ差し替える。
     */
    private var existingItemHandler: ExistingItemHandler? = null

    /**
     * ガチャシステム全体の初期化処理
     */
    fun init() {

        plugin.logger.info("GachaMain を初期化しています...")

        vaultHook = VaultHook(plugin)

        if (vaultHook.setup()) {
            plugin.logger.info("Vault経済連携に成功しました。")
        } else {
            plugin.logger.warning(
                "Vault経済連携を利用できません。経済機能は無効です。"
            )
        }

        // 1. マネージャー層の初期化
        authManager = ResourcePackSessionManager(plugin.dataFolder)
        modelConfigManager = ModelConfigManager(plugin, authManager)

        // 2. コマンド・イベントリスナーの登録
        val gachaCommand = GachaCommand(plugin, authManager)
        val pluginCommand = plugin.getCommand("gacha")

        if (pluginCommand != null) {
            pluginCommand.setExecutor(gachaCommand)
            pluginCommand.setTabCompleter(gachaCommand)
        } else {
            plugin.logger.warning(
                "plugin.yml に gacha コマンドが登録されていません。"
            )
        }

        plugin.server.pluginManager.registerEvents(
            PackOpenListener(modelConfigManager ,authManager),
            plugin
        )

        // ★ gachaSettingManager を渡して初期化
        GachaStandManager.init(
            plugin,
            modelConfigManager,
            gachaSettingManager,
            vaultHook
        )

        GachaStandManager.reloadStands()

        plugin.server.pluginManager.registerEvents(
            GachaStandListener(authManager),
            plugin
        )

        plugin.server.pluginManager.registerEvents(
            SkinJoinListener(authManager),
            plugin
        )

        SkinSupport.preloadMissing(
            plugin,
            authManager
        )

        // Gemini初期化
        geminiClient = GeminiFactory.create(plugin)

        // 3. HTTPコンテキストの登録
        registerHttpHandlers()

        GachaHistory.init(plugin)
    }

    /**
     * Geminiの設定を再読み込みする。
     *
     * config.yml の
     *   gemini.api-key
     *   gemini.model
     *
     * を再取得して GeminiClient を作り直す。
     */
    fun reloadGemini() {
        plugin.logger.info("Gemini の設定を再読み込みしています...")

        val oldClient = geminiClient

        geminiClient = GeminiFactory.create(plugin)

        if (geminiClient != null) {
            plugin.logger.info(
                "Gemini の設定を再読み込みしました。"
            )
        } else {
            plugin.logger.warning(
                "Gemini の設定を再読み込みしましたが、APIキーが設定されていないためAI機能は無効です。"
            )
        }

        // 既存のHTTP Handlerにも新しいClientを渡す
        existingItemHandler?.setGeminiClient(geminiClient)

        if (oldClient != null && geminiClient == null) {
            plugin.logger.info(
                "Gemini APIクライアントを無効化しました。"
            )
        }
    }

    /**
     * HTTP サーバーへガチャ関連のエンドポイントを登録
     */
    private fun registerHttpHandlers() {
        val server = packHttpServer.httpServer

        if (server == null) {
            plugin.logger.warning(
                "HTTP サーバーが動いていないため、ガチャのWebハンドラーを登録できませんでした。"
            )
            return
        }

        server.createContext(
            "/home",
            PackHomeHandler(
                plugin,
                authManager
            )
        )

        server.createContext(
            "/login",
            PackLoginHandler(
                authManager
            )
        )

        server.createContext(
            "/upload",
            PackUploadHandler(
                plugin,
                authManager,
                modelConfigManager
            )
        )

        server.createContext(
            "/edit",
            PackEditHandler(
                plugin,
                authManager,
                modelConfigManager
            )
        )

        server.createContext(
            "/list",
            PackListHandler(
                plugin,
                authManager,
                modelConfigManager,
                gachaSettingManager
            )
        )

        server.createContext(
            "/logout",
            LogoutHandler(
                authManager
            )
        )

        // ★ Handlerをフィールドとして保持
        existingItemHandler = ExistingItemHandler(
            plugin,
            authManager,
            modelConfigManager,
            geminiClient
        )

        server.createContext(
            "/add-existing",
            existingItemHandler!!
        )

        server.createContext(
            "/history",
            PackHistoryHandler(
                authManager
            )
        )

        server.createContext(
            "/bulk-edit",
            PackBulkEditHandler(
                plugin,
                authManager,
                modelConfigManager
            )
        )
        server.createContext(
            "/settings-io",
            PackSettingsIoHandler(plugin,
                authManager,
                modelConfigManager ,
                gachaSettingManager)
        )
    }

    /**
     * ガチャシステム全体の終了処理
     */
    fun shutdown() {
        plugin.logger.info("GachaMain を終了します...")

        authManager.shutdown()

        geminiClient = null
        existingItemHandler = null
    }

    fun reloadAfterConfigReload() {
        plugin.logger.info(
            "ガチャシステムの設定を再読み込みしています..."
        )

        // Geminiクライアントを再生成する
        reloadGemini()

        plugin.logger.info(
            "ガチャシステムの設定再読み込み処理が完了しました。"
        )
    }
    fun reloadHttpHandlers() {
        registerHttpHandlers()
    }
}