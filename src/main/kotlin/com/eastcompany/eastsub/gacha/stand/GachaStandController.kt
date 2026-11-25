package com.eastcompany.eastsub.gacha.stand

import com.eastcompany.eastsub.gacha.SkinSupport
import com.eastcompany.eastsub.gacha.history.GachaHistory
import com.eastcompany.eastsub.gacha.web.session.ResourcePackSessionManager
import com.eastcompany.eastsub.gacha.item.GachaItemBuilder
import com.eastcompany.eastsub.gacha.model.ModelRarity
import com.eastcompany.eastsub.gacha.model.RegisteredModelData
import com.eastcompany.eastsub.gacha.stand.GachaStandManager.plugin
import io.papermc.paper.datacomponent.item.ResolvableProfile
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.*
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import java.util.*

/**
 * 1 つのスタンドに対する操作と表示更新を担当する。
 * 抽選は [GachaDrawer]、景品付与は [GachaRewardGiver]、毎 tick の処理は [GachaStandTicker] が行う。
 */
class GachaStandController(val parentTextUuid: UUID) {

    private val drawer get() = GachaStandManager.drawer
    private val rewardGiver get() = GachaStandManager.rewardGiver


    // ------------------------------------------------------------------
    // エンティティ取得
    // ------------------------------------------------------------------

    fun getParentEntity(): TextDisplay? {
        val entity = Bukkit.getEntity(parentTextUuid) as? TextDisplay ?: return null
        val isParent = entity.persistentDataContainer
            .has(GachaStandKeys.IS_STAND_PARENT, PersistentDataType.BYTE)
        return if (isParent) entity else null
    }

    fun getChildEntity(targetIndex: Int): Entity? = findChild<Entity>(targetIndex)

    fun getPreviewDisplay(): ItemDisplay? = findChild(GachaStandKeys.INDEX_PREVIEW_ITEM)

    /**
     * 指定インデックスかつ型 T の子エンティティを探す。
     * プレビュー用の Interaction も同じインデックスを持つため、型でも絞り込む。
     */
    private inline fun <reified T : Entity> findChild(index: Int): T? {
        val parent = getParentEntity() ?: return null
        if (index == GachaStandKeys.INDEX_EXECUTE_BUTTON) return parent as? T

        val raw = parent.persistentDataContainer
            .get(GachaStandKeys.CHILD_UUIDS, PersistentDataType.STRING) ?: return null

        return raw.split(",").firstNotNullOfOrNull { text ->
            val uuid = runCatching { UUID.fromString(text) }.getOrNull()
                ?: return@firstNotNullOfOrNull null
            val entity = Bukkit.getEntity(uuid) as? T ?: return@firstNotNullOfOrNull null
            val childIndex = entity.persistentDataContainer
                .get(GachaStandKeys.STAND_INDEX, PersistentDataType.INTEGER)
            if (childIndex == index) entity else null
        }
    }

    private fun isExecuting(pdc: PersistentDataContainer): Boolean =
        pdc.get(GachaStandKeys.IS_EXECUTING, PersistentDataType.BYTE) == GachaStandKeys.FLAG_ON

    // ------------------------------------------------------------------
    // ガチャ実行（開始 / 停止）
    // ------------------------------------------------------------------

    /** ボタンがクリックされた時の処理（開始 or 途中停止） */
    fun toggleOrStartGacha(player: Player ): Boolean {

        var earned = 0.0

        val pdc = getParentEntity()?.persistentDataContainer ?: return false

        // 実行中なら、支払いをせずに停止する
        if (isExecuting(pdc)) {
            stopGachaRoulette()
            player.playSound(
                player.location,
                Sound.UI_BUTTON_CLICK,
                0.6f,
                1.2f
            )
            return true
        }

        val author = pdc.get(GachaStandKeys.CURRENT_AUTHOR, PersistentDataType.STRING)

        if (author == null) {
            player.sendActionBar("§cガチャの作成者を取得できません。")
            playDenySound(player)
            return false
        }

// 自分自身のガチャかどうか（UUID があれば UUID、無ければ名前で判定）
        val authorUuid = pdc.get(GachaStandKeys.CURRENT_AUTHOR_UUID, PersistentDataType.STRING)
        val isSelf = authorUuid?.equals(player.uniqueId.toString(), ignoreCase = true)
            ?: author.equals(player.name, ignoreCase = true)

        // 自分のガチャは設定に関係なく固定額
        // クリエイティブモードのプレイヤーは無料
        // 自分自身のガチャも固定額、それ以外は設定価格
        val price = when {
            player.gameMode == org.bukkit.GameMode.CREATIVE -> 0
            isSelf -> SELF_PRICE
            else -> GachaStandManager.gachaSettingManager.loadSettings(author).price
        }

        val vault = GachaStandManager.vaultHook

        if (!vault.isAvailable()) {
            player.sendActionBar("§c経済システムが利用できません。")
            playDenySound(player)
            return false
        }

        if (price > 0) {
            val balance = vault.getBalance(player)

            if (balance == null || balance < price.toDouble()) {
                player.sendActionBar("§c所持金が不足しています。必要金額: $price")
                playDenySound(player)          // ★ お金が足りないときの音
                return false
            }

            if (!vault.withdraw(player, price)) {
                player.sendActionBar("§c支払いに失敗しました。")
                playDenySound(player)
                return false
            }

            player.sendActionBar("§aガチャ料金 §e-${price} PEN")

            // 他人のガチャのときだけ、料金の50%を作成者に送金
            if (!isSelf) {
                val authorPlayer = Bukkit.getOfflinePlayer(author)
                val creatorShare = price / 2.0
                earned = creatorShare

                if (creatorShare > 0.0) {
                    if (!vault.deposit(authorPlayer, creatorShare)) {
                        vault.deposit(player, price.toDouble())
                        player.sendActionBar("§c送金に失敗したため、支払いを取り消しました。")
                        playDenySound(player)
                        return false
                    }
                }

                val playerBalance = vault.getBalance(player) ?: 0.0
                player.sendActionBar(
                    "§aガチャ料金: §e-${price} PEN §7(${author}へ50%送金) §8| §b残高: §e${"%.2f".format(playerBalance)} PEN"
                )

                authorPlayer.player?.let { creator ->
                    val creatorBalance = vault.getBalance(creator) ?: 0.0
                    creator.sendActionBar(
                        "§e${player.name} §7がガチャを回しました！ §a+${creatorShare} PEN §8| §b残高: §e${"%.2f".format(creatorBalance)} PEN"
                    )
                }
            }
        }

        startGacha(pdc, player , isSelf ,earned)
        return true
    }

    private fun startGacha(pdc: PersistentDataContainer, player: Player, isSelf: Boolean, earned: Double) {
        val author = pdc.get(GachaStandKeys.CURRENT_AUTHOR, PersistentDataType.STRING)
        val reel = if (author != null) drawer.buildReel(author) else emptyList()

        pdc.set(GachaStandKeys.EXECUTING_PLAYER_UUID, PersistentDataType.STRING, player.uniqueId.toString())
        pdc.set(GachaStandKeys.REEL, PersistentDataType.STRING, reel.joinToString(",") { it.toToken() })
        pdc.set(GachaStandKeys.EARNED, PersistentDataType.DOUBLE, earned)
        pdc.set(GachaStandKeys.EXECUTE_TICK, PersistentDataType.INTEGER, 0)
        pdc.set(GachaStandKeys.IS_EXECUTING, PersistentDataType.BYTE, GachaStandKeys.FLAG_ON)

        updateExecuteButton(BUTTON_STOP)
        playExecuteSound(player)
    }

    /** ルーレット演出を 1 コマ進める（見た目のみ。景品には影響しない） */
    fun tickRoulette() {
        val parent = getParentEntity() ?: return
        val pdc = parent.persistentDataContainer
        val author = pdc.get(GachaStandKeys.CURRENT_AUTHOR, PersistentDataType.STRING) ?: return
        val tick = pdc.get(GachaStandKeys.EXECUTE_TICK, PersistentDataType.INTEGER) ?: 0

        parent.world.playSound(parent.location, Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.5f, 1.8f)

        when (val shown = reelResultAt(pdc, author, tick) ?: drawer.draw(author)) {
            is DrawResult.Win -> {
                val item = if (shown.model.hiddenFromPreview || shown.model.excludeFromPreview) createQuestionMarkItem()
                else GachaItemBuilder.createModelItem(shown.model)
                updatePreviewItem(item)
                updateRarityTag(shown.model.rarity)
            }
            DrawResult.Miss, DrawResult.NoModels -> {
                updatePreviewItem(ItemStack(Material.STICK))
                updateRarityTag(null)
            }
        }
    }

    /** ルーレット停止。開始時に確定した結果を表示し、景品を付与する。 */
    fun stopGachaRoulette() {
        val parent = getParentEntity() ?: return
        val pdc = parent.persistentDataContainer

        val author = pdc.get(GachaStandKeys.CURRENT_AUTHOR, PersistentDataType.STRING)
        val executeTick = pdc.get(GachaStandKeys.EXECUTE_TICK, PersistentDataType.INTEGER) ?: 0
        val result = when {
            author == null -> DrawResult.NoModels
            else -> reelResultAt(pdc, author, executeTick) ?: drawer.draw(author)
        }

        val playerUuid = pdc.get(GachaStandKeys.EXECUTING_PLAYER_UUID, PersistentDataType.STRING)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val player = playerUuid?.let { Bukkit.getPlayer(it) }
        val earned = pdc.get(GachaStandKeys.EARNED, PersistentDataType.DOUBLE) ?: 0.0
        val authorUuid = pdc.get(GachaStandKeys.CURRENT_AUTHOR_UUID, PersistentDataType.STRING)

        pdc.set(GachaStandKeys.IS_EXECUTING, PersistentDataType.BYTE, GachaStandKeys.FLAG_OFF)
        pdc.set(GachaStandKeys.EXECUTE_TICK, PersistentDataType.INTEGER, 0)
        pdc.remove(GachaStandKeys.REEL)
        pdc.remove(GachaStandKeys.EARNED)
        pdc.remove(GachaStandKeys.PENDING_RESULT)
        pdc.remove(GachaStandKeys.EXECUTING_PLAYER_UUID)

        when (result) {
            is DrawResult.Win -> applyModelData(result.model)
            DrawResult.Miss -> {
                showMiss()
                player?.inventory?.addItem(ItemStack(Material.STICK))
            }
            DrawResult.NoModels -> showNoModels(author ?: "Unknown")
        }
        playResultSound(parent, result)

        // 履歴(他人のガチャのときだけ)
        if (author != null && playerUuid != null) {
            val isSelf = authorUuid?.equals(playerUuid.toString(), ignoreCase = true)
                ?: author.equals(Bukkit.getOfflinePlayer(playerUuid).name, ignoreCase = true)
            if (!isSelf) {
                val name = player?.name ?: Bukkit.getOfflinePlayer(playerUuid).name ?: "Unknown"
                when (result) {
                    is DrawResult.Win -> GachaHistory.record(
                        author, authorUuid, playerUuid.toString(), name, "WIN", earned,
                        result.model.modelId, result.model.itemName, result.model.rarity.name
                    )
                    else -> GachaHistory.record(author, authorUuid, playerUuid.toString(), name, "MISS", earned)
                }
            }
        }

        rewardGiver.give(player, result)
        GachaStandManager.resetStandTimer(parentTextUuid)
    }

    private fun resolveFinalResult(pdc: PersistentDataContainer, author: String?): DrawResult {
        if (author == null) return DrawResult.NoModels

        val token = pdc.get(GachaStandKeys.PENDING_RESULT, PersistentDataType.STRING)
        val restored = token?.let { drawer.restore(author, it) }
        return restored ?: drawer.draw(author)
    }

    fun playExecuteSound(player: Player) {
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.0f)
    }

    // ------------------------------------------------------------------
    // 作成者・モデルの切り替え
    // ------------------------------------------------------------------

    fun switchToNextAuthor(
        player: Player,
        sessionManager: ResourcePackSessionManager
    ): Boolean {

        // 専用スタンドは作者変更不可
        if (isPersonalStand()) {
            player.sendActionBar("§cこのガチャスタンドの作成者は固定されています。")
            playDenySound(player)
            return false
        }
        val parent = getParentEntity() ?: return false
        val pdc = parent.persistentDataContainer
        if (isExecuting(pdc)) return false

        GachaStandManager.resetStandTimer(parentTextUuid)

        // 1. 全ユーザーを取得（キャッシュから返るのでファイルI/Oなし）
        val allUsers = sessionManager.loadAllUsers()
        if (allUsers.isEmpty()) return false

        // 2. 有効なモデルが1つ以上登録されている作者のみに絞り込む
        val activeUsers = allUsers.filter { user ->
            drawer.validModels(user.name).isNotEmpty()
        }

        if (activeUsers.isEmpty()) {
            player.sendActionBar("§c切り替え可能な作者（モデル登録済み）がいません。")
            return false
        }

        // 3. 現在の作者を特定し、次のユーザーを決定
        val currentUuidStr = pdc.get(GachaStandKeys.CURRENT_AUTHOR_UUID, PersistentDataType.STRING)
        val currentIndex = activeUsers.indexOfFirst { it.uuid.equals(currentUuidStr, ignoreCase = true) }
        val nextUser = activeUsers[nextIndex(currentIndex, activeUsers.size)]

        // 4. PDC に新しい作者の「名前」と「UUID」の両方を保存
        pdc.set(GachaStandKeys.CURRENT_AUTHOR, PersistentDataType.STRING, nextUser.name)
        pdc.set(GachaStandKeys.CURRENT_AUTHOR_UUID, PersistentDataType.STRING, nextUser.uuid)

        // 5. マネキンのスキンを適用
        findChild<Mannequin>(GachaStandKeys.INDEX_MANNEQUIN)?.let { mannequin ->
            // 保存済みスキンがあれば即時適用（ネットワークなし）
            mannequin.profile = ResolvableProfile.resolvableProfile(SkinSupport.buildProfile(nextUser))

            // 未保存の作者だけ非同期で取得 → メインスレッドで適用
            if (nextUser.skinValue == null) {
                SkinSupport.fetchAndSave(sessionManager, nextUser) { updated ->
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        val nowUuid = pdc.get(GachaStandKeys.CURRENT_AUTHOR_UUID, PersistentDataType.STRING)
                        // 取得待ちの間に別の作者へ切り替わっていたら適用しない
                        if (!mannequin.isValid || !updated.uuid.equals(nowUuid, ignoreCase = true)) return@Runnable
                        mannequin.profile = ResolvableProfile.resolvableProfile(SkinSupport.buildProfile(updated))
                    })
                }
            }
        }

        showFirstModelOrEmpty(nextUser.name)

        player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f)
        return true
    }


    fun switchToNextModel(player: Player): Boolean {
        GachaStandManager.resetStandTimer(parentTextUuid)

        val switched = switchToNextModelAuto()
        if (switched) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.5f)
        }
        return switched
    }

    /** 自動切り替え用（音なし）。ガチャ実行中は何もしない。 */
    fun switchToNextModelAuto(): Boolean {
        val parent = getParentEntity() ?: return false
        val pdc = parent.persistentDataContainer
        if (isExecuting(pdc)) return false

        val author = pdc.get(GachaStandKeys.CURRENT_AUTHOR, PersistentDataType.STRING) ?: return false
        val currentModelId = pdc.get(GachaStandKeys.CURRENT_MODEL_ID, PersistentDataType.STRING) ?: ""

        // プレビュー除外のモデルは切り替え表示の対象外 (抽選には含まれる)
        val models = previewModels(author)
        if (models.isEmpty()) return false

        val currentIndex = models.indexOfFirst { it.modelId.equals(currentModelId, ignoreCase = true) }
        applyModelData(models[nextIndex(currentIndex, models.size)])
        return true
    }

    /** Shift+右クリック: 現在の作者のモデル一覧をインベントリで表示する (プレビュー除外のモデルは載せない) */
    fun openModelList(player: Player): Boolean {
        val parent = getParentEntity() ?: return false
        val author = parent.persistentDataContainer
            .get(GachaStandKeys.CURRENT_AUTHOR, PersistentDataType.STRING) ?: return false

        GachaStandManager.resetStandTimer(parentTextUuid)

        // excludeFromPreview に関係なく全モデルを取得
        val models = drawer.validModels(author)

        if (models.isEmpty()) {
            player.sendActionBar("§c表示できるモデルがありません。")
            playDenySound(player)
            return false
        }

        GachaModelListGui.open(player, parentTextUuid, author, models)
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.5f)
        return true
    }

    private fun nextIndex(currentIndex: Int, size: Int): Int =
        if (currentIndex == -1 || currentIndex + 1 >= size) 0 else currentIndex + 1

    // ------------------------------------------------------------------
    // 表示の更新
    // ------------------------------------------------------------------

    /** モデルを表示する（プレビュー・マネキン・ボタン・レア度タグ・情報板） */
    fun applyModelData(data: RegisteredModelData) {
        getParentEntity()?.persistentDataContainer
            ?.set(GachaStandKeys.CURRENT_MODEL_ID, PersistentDataType.STRING, data.modelId)

        val modelItem = GachaItemBuilder.createModelItem(data)

        val previewItem = if (data.hiddenFromPreview) {
            createQuestionMarkItem()
        } else {
            modelItem
        }

        updatePreviewItem(previewItem)
        setMannequinItems(hand = previewItem, head = previewItem)

        updateExecuteButton(BUTTON_START)
        updateRarityTag(data.rarity)
        refreshInfoBoard(data)
    }

    /** ハズレを表示する */
    fun showMiss() {
        getParentEntity()?.persistentDataContainer?.remove(GachaStandKeys.CURRENT_MODEL_ID)

        val stick = ItemStack(Material.STICK)
        updatePreviewItem(stick)
        setMannequinItems(hand = stick, head = ItemStack(Material.AIR))

        updateExecuteButton(BUTTON_START)
        updateRarityTag(ModelRarity.MISS)
        refreshInfoBoard(null)
    }

    /** 有効なモデルが無い状態を表示する */
    fun showNoModels(author: String) {
        getParentEntity()?.persistentDataContainer?.remove(GachaStandKeys.CURRENT_MODEL_ID)

        updatePreviewItem(ItemStack(Material.STICK))
        setMannequinItems(hand = ItemStack(Material.AIR), head = ItemStack(Material.AIR))

        updateExecuteButton(BUTTON_DISABLED)
        updateRarityTag(null)

        val price = GachaStandManager.gachaSettingManager.loadPrice(author)
        updateInfoBoard(
            "§f[§eガチャ情報§f]                                                   \n§f作成者: §b$author\n§f価格: §a$price PEN\n\n§c(有効なモデル未登録)"
        )
    }

    /** 作成者の最初の有効モデルを表示。無ければ「未登録」表示 */
    fun showFirstModelOrEmpty(author: String) {
        val all = drawer.validModels(author)
        val first = all.firstOrNull { !it.excludeFromPreview }
        when {
            first != null -> applyModelData(first)
            all.isNotEmpty() -> showAllHidden()   // 全モデルがプレビュー除外
            else -> showNoModels(author)
        }
    }

    /** プレビューに出してよいモデル (excludeFromPreview = false) だけ */
    private fun previewModels(author: String): List<RegisteredModelData> =
        drawer.validModels(author).filter { !it.excludeFromPreview }

    /** モデルはあるが全てプレビュー除外のとき: 「？」表示にする */
    private fun showAllHidden() {
        getParentEntity()?.persistentDataContainer?.remove(GachaStandKeys.CURRENT_MODEL_ID)

        val question = createQuestionMarkItem()
        updatePreviewItem(question)
        setMannequinItems(hand = question, head = ItemStack(Material.AIR))

        updateExecuteButton(BUTTON_START)
        updateRarityTag(null)
        refreshInfoBoard(null, unknown = true)
    }

    fun updateRarityTag(rarity: ModelRarity?): Boolean {
        val entity = findChild<TextDisplay>(GachaStandKeys.INDEX_RARITY_TAG) ?: return false
        entity.text = if (rarity != null && rarity != ModelRarity.UNCLASSIFIED) {
            "§f[${rarity.color}§l${rarity.displayName}§f]"
        } else {
            ""
        }
        return true
    }

    /** 情報板を更新する。data が null のときはハズレ表示 */
    fun refreshInfoBoard(data: RegisteredModelData?, unknown: Boolean = false): Boolean {
        val parent = getParentEntity() ?: return false
        val author = parent.persistentDataContainer
            .get(GachaStandKeys.CURRENT_AUTHOR, PersistentDataType.STRING) ?: "Unknown"

        val price = GachaStandManager.gachaSettingManager.loadSettings(author).price

        // レア度ごとのモデル数
        val countByRarity = drawer.validModels(author).groupingBy { it.rarity }.eachCount()

        val oddsText = drawer.odds(author)
            .filter { it.rarity != ModelRarity.MISS }
            .joinToString("\n") { odds ->
                val percent = String.format(Locale.ROOT, "%5.1f%%", odds.percent)
                val name = odds.rarity.displayName.padEnd(4, ' ')
                val count = countByRarity[odds.rarity] ?: 0
                "${odds.rarity.color}$name§f: §a$percent §7(${count}個)"
            }

        val itemName: String
        val rarityText: String
        val description: String
        if (unknown) {
            itemName = "？？？"
            rarityText = "§7？"
            description = "？"
        } else if (data != null) {
            itemName = data.itemName
            rarityText = "${data.rarity.color}${data.rarity.displayName}"
            description = data.description?.takeIf { it.isNotBlank() } ?: "なし"
        } else {
            itemName = "ハズレ"
            rarityText = "${ModelRarity.MISS.color}${ModelRarity.MISS.displayName}"
            description = "なし"
        }

        val lineWidth = 150         // updateInfoBoard の entity.lineWidth と同じ値
        val textWidth = lineWidth - 10  // 折り返しの余裕

        val text = buildString {
            appendLine(padTo("§f[§eガチャ情報§f]", lineWidth - 10))   // 幅を固定
            appendLine(wrapText("§f作成者: §b$author", textWidth))
            appendLine("§fガチャ1回: §a$price PEN")
            appendLine()
            appendLine("§f[§6排出確率§f]")
            appendLine(oddsText)
            appendLine()
            appendLine("§f[§b表示中§f]")
            appendLine(wrapText("§fアイテム名: §b$itemName", textWidth))
            appendLine("§fレア度: $rarityText")
            appendLine("§f説明:")
            append(wrapText(description, textWidth))
        }
        return updateInfoBoard(text)
    }

    fun onInfoBoardClick(player: Player): Boolean {
        if (!player.hasPermission(LOGIN_PERMISSION)) {
            playDenySound(player)
            player.sendMessage("§c・ガチャ追加の権限がありません")
            player.sendMessage("§7詳細はDiscordスレッドの§f「リソパガチャの登録方法について」§7を参照してください")
            player.sendMessage("§7権限は誰でも申請できます")
            return false
        }

        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.6f, 1.2f)
        player.performCommand("gacha login")
        return true
    }

    private val COLOR_CODE = Regex("§[0-9a-fk-orA-FK-OR]")

    /** マイクラ標準フォントでの1文字の幅（px・字間込みの概算） */
    private fun charWidth(c: Char): Int = when {
        c == ' ' -> 4
        c in "i.,:;!|" -> 2
        c in "l'" -> 3
        c in "tI[]" -> 4
        c in "fk<>()" -> 5
        c.code < 128 -> 6
        else -> 9   // 日本語などの全角
    }

    /** 色コードを除いた表示幅 */
    private fun visualWidth(text: String): Int =
        COLOR_CODE.replace(text, "").sumOf { charWidth(it) }

    /** 空白で埋めて、指定の幅にそろえる */
    private fun padTo(text: String, targetWidth: Int): String {
        val spaces = ((targetWidth - visualWidth(text)) / charWidth(' ')).coerceAtLeast(0)
        return text + " ".repeat(spaces)
    }

    /** maxWidth を超えたところで改行を入れる（色コードは幅に数えない） */
    private fun wrapText(text: String, maxWidth: Int): String {
        val out = StringBuilder()
        var width = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '§' && i + 1 < text.length -> {      // 色コードはそのまま出力
                    out.append(c).append(text[i + 1])
                    i += 2
                    continue
                }
                c == '\n' -> {
                    out.append(c)
                    width = 0
                }
                else -> {
                    val w = charWidth(c)
                    if (width + w > maxWidth) {
                        out.append('\n')
                        width = 0
                    }
                    out.append(c)
                    width += w
                }
            }
            i++
        }
        return out.toString()
    }

    fun updateInfoBoard(newText: String): Boolean {
        val entity = findChild<TextDisplay>(GachaStandKeys.INDEX_INFO_BOARD) ?: return false
        entity.lineWidth = 600
        entity.text = newText
        return true
    }

    fun updateMannequinSkin(player: Player, sessionManager: ResourcePackSessionManager? = null): Boolean {
        val mannequin = findChild<Mannequin>(GachaStandKeys.INDEX_MANNEQUIN) ?: return false
        mannequin.profile = ResolvableProfile.resolvableProfile(player.playerProfile)

        val authorName = sessionManager?.getPlayerName(player.uniqueId) ?: player.name
        getParentEntity()?.persistentDataContainer
            ?.set(GachaStandKeys.CURRENT_AUTHOR, PersistentDataType.STRING, authorName)
        return true
    }

    fun updatePreviewItem(itemStack: ItemStack): Boolean {
        val entity = getPreviewDisplay() ?: return false
        entity.setItemStack(itemStack)
        entity.isGlowing = false
        entity.glowColorOverride = null
        return true
    }

    fun updateExecuteButton(newText: String): Boolean {
        val entity = findChild<TextDisplay>(GachaStandKeys.INDEX_EXECUTE_BUTTON) ?: return false
        entity.text = newText
        return true
    }

    fun setMannequinItem(itemStack: ItemStack, slot: EquipmentSlot = EquipmentSlot.HAND): Boolean {
        val mannequin = findChild<Mannequin>(GachaStandKeys.INDEX_MANNEQUIN) ?: return false
        return runCatching {
            mannequin.equipment.setItem(slot, itemStack)
            true
        }.getOrDefault(false)
    }

    private fun setMannequinItems(hand: ItemStack, head: ItemStack) {
        setMannequinItem(hand, EquipmentSlot.HAND)
        setMannequinItem(head, EquipmentSlot.HEAD)
    }

    companion object {
        const val BUTTON_START = "§8« §a§l▶ ガチャを回す §8»"
        const val BUTTON_STOP = "§8« §c§l■ ストップ §8»"
        const val BUTTON_DISABLED = "§8« §7§m ガチャを回す §8»"
        const val SELF_PRICE = 100
        const val ROULETTE_INTERVAL_TICKS = 3
        const val LOGIN_PERMISSION = "resourcepackflow.admin.login"
    }

    private fun createQuestionMarkItem(): ItemStack {
        return ItemStack(Material.STICK).apply {
            editMeta {
                it.setItemModel(
                    org.bukkit.NamespacedKey.fromString("gacha_system:question_mark")!!
                )
            }
        }
    }

    /** 結果に応じた音を鳴らす（スタンド周辺の全員に聞こえる） */
    private fun playResultSound(parent: Entity, result: DrawResult) {
        val world = parent.world
        val loc = parent.location
        fun play(sound: Sound, volume: Float = 1.0f, pitch: Float = 1.0f) =
            world.playSound(loc, sound, volume, pitch)

        when (result) {
            is DrawResult.Win -> when (result.model.rarity) {
                ModelRarity.S -> {                       // ファンファーレ + レベルアップ
                    play(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f)
                    play(Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 1.2f)
                }
                ModelRarity.A -> {
                    play(Sound.ENTITY_PLAYER_LEVELUP, 1.2f, 1.0f)
                    play(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f)
                }
                ModelRarity.B -> play(Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 0.8f)
                ModelRarity.C -> play(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f)
                ModelRarity.D -> play(Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.4f)
                ModelRarity.E -> play(Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.0f)
                ModelRarity.F -> play(Sound.UI_BUTTON_CLICK, 0.8f, 1.0f)
                ModelRarity.MISS, ModelRarity.UNCLASSIFIED -> Unit
            }
            DrawResult.Miss -> play(Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            DrawResult.NoModels -> Unit
        }
    }
    /** 今表示されているコマの結果。リールが無ければ null */
    private fun reelResultAt(pdc: PersistentDataContainer, author: String, executeTick: Int): DrawResult? {
        val raw = pdc.get(GachaStandKeys.REEL, PersistentDataType.STRING) ?: return null
        val tokens = raw.split(",")
        if (tokens.isEmpty()) return null
        val pos = (executeTick / ROULETTE_INTERVAL_TICKS) % tokens.size
        return drawer.restore(author, tokens[pos])
    }

    private fun playDenySound(player: Player) {
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f)
    }
    private fun isPersonalStand(): Boolean {
        val pdc = getParentEntity()?.persistentDataContainer ?: return false

        return pdc.get(
            GachaStandKeys.IS_PERSONAL_STAND,
            PersistentDataType.BYTE
        ) == GachaStandKeys.FLAG_ON
    }
    private fun removeSpawnedEntities(
        parent: TextDisplay,
        childUuids: List<String>
    ) {
        childUuids.forEach { uuidText ->
            val uuid = runCatching {
                UUID.fromString(uuidText)
            }.getOrNull() ?: return@forEach

            Bukkit.getEntity(uuid)?.remove()
        }

        parent.remove()
    }
}