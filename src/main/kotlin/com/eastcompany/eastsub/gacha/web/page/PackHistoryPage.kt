package com.eastcompany.eastsub.gacha.web.page

import com.eastcompany.eastsub.gacha.history.GachaHistory
import com.eastcompany.eastsub.gacha.model.ModelRarity
import com.eastcompany.eastsub.gacha.web.template.WebHtmlTemplates

object PackHistoryPage {

    fun render(
        playerName: String,
        history: GachaHistory.AuthorHistory?
    ): String {

        val totalSpins =
            history?.totalSpins ?: 0

        val totalEarned =
            GachaHistory.formatMoney(
                history?.totalEarned ?: 0.0
            )

        // ==============================================================
        // プレイヤー別集計
        // ==============================================================

        val playerStats =
            history
                ?.let {
                    GachaHistory.playerStats(it)
                }
                ?: emptyList()

        val playerRowsHtml =
            playerStats.mapIndexed { index, stats ->

                val missCount =
                    GachaHistory.missCount(stats)

                val itemCount =
                    stats.items.values.sum()

                val itemDetails =
                    buildItemDetails(
                        stats
                    )

                """
                <tr
                    class="player-summary-row clickable"
                    onclick="togglePlayerDetail($index)"
                >
                    <td>
                        <b>${esc(stats.playerName)}</b>
                    </td>

                    <td>
                        <span class="count-chip">
                            ${stats.totalSpins} 回
                        </span>
                    </td>

                    <td>
                        <span class="win-chip">
                            当たり ${itemCount} 回
                        </span>

                        <span class="miss-chip">
                            はずれ ${missCount} 回
                        </span>
                    </td>
                </tr>

                <tr
                    id="player-detail-$index"
                    class="player-detail-row"
                    style="display: none;"
                >
                    <td colspan="3">
                        <div class="player-detail-box">

                            <div class="player-detail-title">
                                ${esc(stats.playerName)}
                                さんの獲得アイテム
                            </div>

                            $itemDetails

                        </div>
                    </td>
                </tr>
                """.trimIndent()

            }.joinToString("\n")

        // ==============================================================
        // 直近履歴
        // ==============================================================

        val groups =
            history
                ?.let {
                    GachaHistory.groupConsecutive(it)
                }
                ?: emptyList()

        val rowsHtml =
            groups.mapIndexed { index, g ->

                val multi =
                    g.count > 1

                val clickAttr =
                    if (multi) {
                        """ onclick="toggleDetail($index)" """
                    } else {
                        ""
                    }

                val rowClass =
                    if (multi) {
                        "group-row clickable"
                    } else {
                        "group-row"
                    }

                val resultCell =
                    if (!multi) {

                        val e =
                            g.entries.first()

                        val rarity =
                            GachaHistory.rarityOf(e)

                        val itemText =
                            if (
                                e.result.equals(
                                    "WIN",
                                    ignoreCase = true
                                )
                            ) {
                                " " + esc(
                                    e.itemName
                                        ?: e.modelId
                                        ?: "?"
                                )
                            } else {
                                " はずれ"
                            }

                        badge(rarity) + itemText

                    } else {

                        GachaHistory
                            .summarize(g.entries)
                            .joinToString("") {
                                    (rarity, count) ->
                                badge(
                                    rarity,
                                    " × $count"
                                )
                            }
                    }

                val countChip =
                    if (multi) {
                        """
                        <span class="count-chip">
                            × ${g.count}
                        </span>
                        """.trimIndent()
                    } else {
                        ""
                    }

                val mainRow =
                    """
                    <tr
                        class="$rowClass"
                        $clickAttr
                    >
                        <td class="col-time">
                            ${esc(timeText(g))}
                        </td>

                        <td>
                            <b>${esc(g.playerName)}</b>
                            $countChip
                        </td>

                        <td>
                            $resultCell
                        </td>

                        <td class="col-earned">
                            +${GachaHistory.formatMoney(g.earned)} PEN
                        </td>
                    </tr>
                    """.trimIndent()

                val detailRow =
                    if (multi) {

                        val lines =
                            g.entries.joinToString("") { e ->

                                val rarity =
                                    GachaHistory.rarityOf(e)

                                val itemText =
                                    if (
                                        e.result.equals(
                                            "WIN",
                                            ignoreCase = true
                                        )
                                    ) {
                                        " " + esc(
                                            e.itemName
                                                ?: e.modelId
                                                ?: "?"
                                        )
                                    } else {
                                        " はずれ"
                                    }

                                """
                                <div class="detail-line">
                                    <span class="col-time">
                                        ${esc(e.time)}
                                    </span>

                                    ${badge(rarity)}

                                    $itemText

                                    <span class="muted">
                                        +${GachaHistory.formatMoney(e.earned)}
                                    </span>
                                </div>
                                """.trimIndent()
                            }

                        """
                        <tr
                            id="detail-$index"
                            class="detail-row"
                            style="display: none;"
                        >
                            <td colspan="4">
                                $lines
                            </td>
                        </tr>
                        """.trimIndent()

                    } else {
                        ""
                    }

                mainRow + "\n" + detailRow

            }.joinToString("\n")

        // ==============================================================
        // プレイヤー集計部分
        // ==============================================================

        val playerTableHtml =
            if (playerStats.isEmpty()) {

                """
                <div class="empty-box">
                    まだ誰もあなたのガチャを回していません。
                </div>
                """.trimIndent()

            } else {

                """
                <div class="table-wrap">

                    <table class="history-table player-table">

                        <thead>
                            <tr>
                                <th>回した人</th>
                                <th>回数</th>
                                <th>結果</th>
                            </tr>
                        </thead>

                        <tbody>
                            $playerRowsHtml
                        </tbody>

                    </table>

                </div>

                <div
                    class="muted"
                    style="font-size: 0.78rem; margin-top: 8px;"
                >
                    ※ プレイヤーをクリックすると、
                    その人が獲得したアイテムの内訳を表示します。
                </div>
                """.trimIndent()
            }

        // ==============================================================
        // 直近履歴
        // ==============================================================

        val tableOrEmpty =
            if (groups.isEmpty()) {

                """
                <div class="empty-box">
                    まだガチャ履歴がありません。
                </div>
                """.trimIndent()

            } else {

                """
                <div class="table-wrap">

                    <table class="history-table">

                        <thead>
                            <tr>
                                <th>日時</th>
                                <th>回した人</th>
                                <th>結果</th>
                                <th>収益</th>
                            </tr>
                        </thead>

                        <tbody>
                            $rowsHtml
                        </tbody>

                    </table>

                </div>

                <div
                    class="muted"
                    style="font-size: 0.78rem; margin-top: 8px;"
                >
                    ※ 同じ人が続けて回した分は
                    ×回数でまとめています。
                    まとめた行をクリックすると内訳を表示します。
                </div>
                """.trimIndent()
            }

        // ==============================================================
        // HTML
        // ==============================================================

        val body =
            """
            <style>

                .history-card-root {
                    max-width: 1000px !important;
                    width: 95% !important;
                    margin: 20px auto !important;
                    padding: 28px !important;
                    background: #ffffff !important;
                    border-radius: 8px !important;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.08) !important;
                    text-align: left !important;
                    box-sizing: border-box !important;
                    color: #333 !important;
                    font-family:
                        -apple-system,
                        BlinkMacSystemFont,
                        "Segoe UI",
                        Roboto,
                        sans-serif !important;
                }

                .history-card-root * {
                    box-sizing: border-box !important;
                }

                .history-header-row {
                    border-bottom: 2px solid #f1f3f5 !important;
                    padding-bottom: 14px !important;
                    margin-bottom: 16px !important;
                }

                .history-title-text {
                    margin: 0 !important;
                    font-size: 1.4rem !important;
                    font-weight: bold !important;
                    color: #212529 !important;
                }

                .section-title {
                    margin-top: 28px !important;
                    margin-bottom: 10px !important;
                    font-size: 1.05rem !important;
                    font-weight: bold !important;
                    color: #343a40 !important;
                }

                .stat-row {
                    display: flex !important;
                    gap: 12px !important;
                    flex-wrap: wrap !important;
                    margin-bottom: 20px !important;
                }

                .stat-card {
                    flex: 1 1 220px !important;
                    padding: 14px 18px !important;
                    background: #f8f9fa !important;
                    border: 1px solid #e9ecef !important;
                    border-left: 4px solid #007bff !important;
                    border-radius: 6px !important;
                }

                .stat-card.earn {
                    border-left-color: #28a745 !important;
                }

                .stat-label {
                    font-size: 0.82rem !important;
                    color: #6c757d !important;
                    margin-bottom: 4px !important;
                }

                .stat-value {
                    font-size: 1.8rem !important;
                    font-weight: bold !important;
                    color: #007bff !important;
                }

                .stat-card.earn .stat-value {
                    color: #28a745 !important;
                }

                .stat-unit {
                    font-size: 0.9rem !important;
                    font-weight: normal !important;
                    color: #6c757d !important;
                }

                .table-wrap {
                    overflow-x: auto !important;
                    border: 1px solid #dee2e6 !important;
                    border-radius: 6px !important;
                }

                .history-table {
                    width: 100% !important;
                    border-collapse: collapse !important;
                    font-size: 0.9rem !important;
                }

                .history-table th {
                    background: #f1f3f5 !important;
                    color: #495057 !important;
                    text-align: left !important;
                    padding: 10px 14px !important;
                    border-bottom: 1px solid #dee2e6 !important;
                    white-space: nowrap !important;
                }

                .history-table td {
                    padding: 9px 14px !important;
                    border-bottom: 1px solid #f1f3f5 !important;
                }

                .group-row:hover,
                .player-summary-row:hover {
                    background: #f8f9fa !important;
                }

                .clickable {
                    cursor: pointer !important;
                }

                .count-chip {
                    display: inline-block !important;
                    margin-left: 6px !important;
                    padding: 2px 8px !important;
                    border-radius: 10px !important;
                    background: #e7f5ff !important;
                    color: #1971c2 !important;
                    border: 1px solid #a5d8ff !important;
                    font-size: 0.8rem !important;
                    font-weight: bold !important;
                }

                .win-chip {
                    display: inline-block !important;
                    margin-right: 5px !important;
                    padding: 2px 8px !important;
                    border-radius: 10px !important;
                    background: #ebfbee !important;
                    color: #2b8a3e !important;
                    border: 1px solid #b2f2bb !important;
                    font-size: 0.78rem !important;
                    font-weight: bold !important;
                }

                .miss-chip {
                    display: inline-block !important;
                    padding: 2px 8px !important;
                    border-radius: 10px !important;
                    background: #f8f9fa !important;
                    color: #868e96 !important;
                    border: 1px solid #dee2e6 !important;
                    font-size: 0.78rem !important;
                    font-weight: bold !important;
                }

                .player-detail-row td {
                    background: #fafbfc !important;
                    padding: 0 !important;
                }

                .player-detail-box {
                    padding: 14px 18px 16px 40px !important;
                    border-left: 3px solid #74c0fc !important;
                }

                .player-detail-title {
                    font-weight: bold !important;
                    margin-bottom: 10px !important;
                    color: #495057 !important;
                }

                .item-stat-row {
                    display: flex !important;
                    align-items: center !important;
                    gap: 8px !important;
                    padding: 5px 0 !important;
                    border-bottom: 1px solid #f1f3f5 !important;
                }

                .item-stat-name {
                    flex: 1 !important;
                }

                .item-stat-count {
                    font-weight: bold !important;
                    color: #1971c2 !important;
                }

                .miss-stat {
                    color: #868e96 !important;
                }

                .detail-row td {
                    background: #fafbfc !important;
                    padding: 8px 14px 8px 40px !important;
                }

                .detail-line {
                    padding: 3px 0 !important;
                    font-size: 0.85rem !important;
                }

                .col-time {
                    white-space: nowrap !important;
                    color: #6c757d !important;
                }

                .col-earned {
                    white-space: nowrap !important;
                    font-weight: bold !important;
                    color: #28a745 !important;
                }

                .rarity-badge {
                    display: inline-flex !important;
                    align-items: center !important;
                    padding: 3px 10px !important;
                    border-radius: 12px !important;
                    font-size: 0.82rem !important;
                    font-weight: bold !important;
                    margin-right: 6px !important;
                    white-space: nowrap !important;
                }

                .muted {
                    color: #868e96 !important;
                }

                .empty-box {
                    padding: 40px 20px !important;
                    text-align: center !important;
                    color: #868e96 !important;
                    background: #f8f9fa !important;
                    border: 1px dashed #ced4da !important;
                    border-radius: 6px !important;
                }

                .btn-history-nav {
                    background: #6c757d !important;
                    color: white !important;
                    padding: 8px 15px !important;
                    border: none !important;
                    border-radius: 4px !important;
                    cursor: pointer !important;
                    margin-right: 8px !important;
                }

            </style>

            <div class="history-card-root">

                <div class="history-header-row">
                    <h2 class="history-title-text">
                        ガチャ履歴（${esc(playerName)} さんのガチャ）
                    </h2>
                </div>

                <!-- ================================================= -->
                <!-- 全体統計 -->
                <!-- ================================================= -->

                <div class="stat-row">

                    <div class="stat-card">

                        <div class="stat-label">
                            回された回数
                        </div>

                        <div class="stat-value">
                            $totalSpins
                            <span class="stat-unit">
                                回
                            </span>
                        </div>

                    </div>

                    <div class="stat-card earn">

                        <div class="stat-label">
                            稼いだ総額（料金の50%）
                        </div>

                        <div class="stat-value">
                            $totalEarned
                            <span class="stat-unit">
                                PEN
                            </span>
                        </div>

                    </div>

                </div>

                <!-- ================================================= -->
                <!-- プレイヤー別 -->
                <!-- ================================================= -->

                <div class="section-title">
                    誰が何回回したか
                </div>

                $playerTableHtml

                <!-- ================================================= -->
                <!-- 直近履歴 -->
                <!-- ================================================= -->

                <div class="section-title">
                    直近のガチャ履歴
                </div>

                $tableOrEmpty

                <br>

                <button
                    class="btn-history-nav"
                    onclick="location.href='/list'"
                >
                    モデル一覧へ
                </button>

                <button
                    class="btn-history-nav"
                    onclick="location.href='/home'"
                >
                    ホームへ戻る
                </button>

            </div>

            <script>

                function toggleDetail(i) {
                    var row =
                        document.getElementById(
                            'detail-' + i
                        );

                    if (!row) return;

                    row.style.display =
                        (row.style.display === 'none')
                            ? 'table-row'
                            : 'none';
                }

                function togglePlayerDetail(i) {
                    var row =
                        document.getElementById(
                            'player-detail-' + i
                        );

                    if (!row) return;

                    row.style.display =
                        (row.style.display === 'none')
                            ? 'table-row'
                            : 'none';
                }

            </script>
            """.trimIndent()

        return WebHtmlTemplates.renderPage(
            "Gacha History - ResourcePackFlow",
            playerName,
            body
        )
    }

    // ==============================================================
    // アイテム内訳
    // ==============================================================

    private fun buildItemDetails(
        stats: GachaHistory.PlayerStats
    ): String {

        val items =
            GachaHistory.itemStats(stats)

        val itemHtml =
            items.joinToString("") { (itemName, count) ->

                """
                <div class="item-stat-row">

                    <span class="item-stat-name">
                        ${esc(itemName)}
                    </span>

                    <span class="item-stat-count">
                        × $count
                    </span>

                </div>
                """.trimIndent()
            }

        val missCount =
            GachaHistory.missCount(stats)

        val missHtml =
            if (missCount > 0) {

                """
                <div class="item-stat-row">

                    <span class="item-stat-name miss-stat">
                        はずれ
                    </span>

                    <span class="item-stat-count miss-stat">
                        × $missCount
                    </span>

                </div>
                """.trimIndent()

            } else {
                ""
            }

        if (
            itemHtml.isBlank() &&
            missHtml.isBlank()
        ) {

            return """
                <div class="muted">
                    獲得アイテムはありません。
                </div>
            """.trimIndent()
        }

        return itemHtml + missHtml
    }

    // ==============================================================
    // レア度バッジ
    // ==============================================================

    private fun badge(
        rarity: ModelRarity,
        suffix: String = ""
    ): String =
        """
        <span
            class="rarity-badge"
            style="${rarity.badgeStyle}"
        >
            ${esc(rarity.displayName)}$suffix
        </span>
        """.trimIndent()

    // ==============================================================
    // 時刻
    // ==============================================================

    /**
     * 1回なら日時、
     * まとめた行なら「最初 〜 最後」
     */
    private fun timeText(
        g: GachaHistory.SpinGroup
    ): String {

        if (g.count == 1) {
            return g.newestTime
        }

        val sameDay =
            g.oldestTime.take(10) ==
                    g.newestTime.take(10) &&
                    g.newestTime.length >= 19

        val newest =
            if (sameDay) {
                g.newestTime.substring(11)
            } else {
                g.newestTime
            }

        return g.oldestTime +
                " 〜 " +
                newest
    }

    // ==============================================================
    // HTMLエスケープ
    // ==============================================================

    private fun esc(
        s: String
    ): String =
        s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}