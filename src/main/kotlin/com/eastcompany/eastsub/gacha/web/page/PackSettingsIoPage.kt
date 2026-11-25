package com.eastcompany.eastsub.gacha.web.page

import com.eastcompany.eastsub.gacha.model.RegisteredModelData
import com.eastcompany.eastsub.gacha.web.form.ModelSettings
import com.eastcompany.eastsub.gacha.web.form.htmlEscape
import com.eastcompany.eastsub.gacha.web.template.WebHtmlTemplates

object PackSettingsIoPage {

    private fun esc(s: String) = htmlEscape(s)

    fun render(playerName: String, models: List<RegisteredModelData>): String {
        val rarityOrder = ModelSettings.rarityOrder

        val rowsHtml = models
            .sortedWith(compareBy({ rarityOrder.indexOf(it.rarity).let { i -> if (i < 0) 99 else i } }, { it.modelId }))
            .joinToString("\n") { m ->
                """
                <tr class="io-row">
                    <td><input type="checkbox" class="io-check" value="${esc(m.modelId)}"></td>
                    <td><span class="io-tag">${esc(ModelSettings.rarityLabel(m.rarity))}</span></td>
                    <td><b>${esc(m.itemName)}</b><div class="io-sub">${esc(m.modelId)}</div></td>
                </tr>
                """.trimIndent()
            }

        val keysText = ModelSettings.all.joinToString("、") { esc(it.label) }

        val body = """
            <style>
                .io-root { max-width: 900px; width: 95%; margin: 20px auto; padding: 28px; background: #fff; border-radius: 8px;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.08); color: #333; box-sizing: border-box;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; text-align: left; }
                .io-root * { box-sizing: border-box; }
                .io-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #f1f3f5;
                    padding-bottom: 14px; margin-bottom: 18px; flex-wrap: wrap; gap: 12px; }
                .io-title { margin: 0; font-size: 1.4rem; font-weight: bold; color: #212529; }
                .io-panel { background: #f8f9fa; border: 1px solid #e9ecef; border-left: 4px solid #007bff; border-radius: 6px;
                    padding: 14px 16px; margin-bottom: 18px; }
                .io-panel.import { border-left-color: #fd7e14; }
                .io-h3 { margin: 0 0 8px 0; font-size: 1.05rem; }
                .io-note { font-size: 0.82em; color: #6c757d; margin: 0 0 12px 0; line-height: 1.5; }
                .io-btn { height: 38px; padding: 0 18px; border: none; border-radius: 4px; font-size: 0.9rem; font-weight: bold;
                    cursor: pointer; color: #fff; background: #007bff; }
                .io-btn:disabled { opacity: 0.5; cursor: not-allowed; }
                .io-btn-gray { background: #6c757d; }
                .io-btn-orange { background: #fd7e14; }
                .io-btn-small { height: 30px; padding: 0 12px; font-size: 0.8rem; }
                .io-toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 10px; }
                .io-count { font-weight: bold; color: #1971c2; margin-left: auto; }
                .io-table { width: 100%; border-collapse: collapse; font-size: 0.9rem; background: #fff; }
                .io-table th { background: #f1f3f5; text-align: left; padding: 8px 10px; border-bottom: 2px solid #dee2e6; }
                .io-table td { padding: 6px 10px; border-bottom: 1px solid #eee; vertical-align: middle; }
                .io-scroll { max-height: 320px; overflow-y: auto; border: 1px solid #dee2e6; border-radius: 4px; margin-bottom: 12px; }
                .io-sub { font-size: 0.75rem; color: #868e96; }
                .io-tag { display: inline-block; padding: 2px 8px; border-radius: 4px; background: #e9ecef; font-size: 0.78rem; font-weight: bold; }
                .io-textarea { width: 100%; min-height: 160px; padding: 10px 12px; border: 1px solid #ced4da; border-radius: 4px;
                    font-family: SFMono-Regular, Menlo, Consolas, monospace; font-size: 0.82rem; margin-bottom: 10px; }
                .io-result { margin-top: 12px; padding: 10px 14px; border-radius: 4px; font-size: 0.9rem; display: none; white-space: pre-line; }
                .io-result.ok { display: block; background: #d4edda; color: #155724; }
                .io-result.ng { display: block; background: #f8d7da; color: #721c24; }
            </style>

            <div class="io-root">
                <div class="io-header">
                    <h2 class="io-title">設定の出力・取り込み</h2>
                    <div style="display:flex; gap:8px;">
                        <button type="button" class="io-btn io-btn-gray" onclick="location.href='/bulk-edit'">一括設定変更</button>
                        <button type="button" class="io-btn io-btn-gray" onclick="location.href='/list'">← 一覧に戻る</button>
                    </div>
                </div>

                <div class="io-panel">
                    <h3 class="io-h3">① 出力（バックアップ・コピー用）</h3>
                    <p class="io-note">
                        モデルごとの項目: $keysText、表示名、説明、推奨アイテム<br>
                        ガチャ全体の設定: 値段、レア度ごとの排出確率（重み）<span style="color:#868e96;">（モデルを選んでも常に含まれます）</span><br>
                        JSON ファイルとして保存します。テクスチャのファイル、スロット種別、ベースアイテムなどは含まれません。
                    </p>

                    <div class="io-toolbar">
                        <button type="button" class="io-btn io-btn-gray io-btn-small" onclick="setAll(true)">全選択</button>
                        <button type="button" class="io-btn io-btn-gray io-btn-small" onclick="setAll(false)">全解除</button>
                        <span id="selectedCount" class="io-count">選択中: 0</span>
                    </div>
                    <div class="io-scroll">
                        <table class="io-table">
                            <thead><tr><th style="width:36px;"></th><th style="width:80px;">レア度</th><th>モデル</th></tr></thead>
                            <tbody id="ioBody">
                                $rowsHtml
                            </tbody>
                        </table>
                    </div>
                    <button type="button" id="exportSelectedBtn" class="io-btn" onclick="exportSelected()" disabled>選択したモデルを出力</button>
                    <button type="button" class="io-btn io-btn-gray" onclick="exportAll()">全モデルを出力</button>
                </div>

                <div class="io-panel import">
                    <h3 class="io-h3">② 取り込み</h3>
                    <p class="io-note">
                        ①で出力した JSON を読み込みます。<b>同じモデルID</b>のモデルだけが対象で、見つからないモデルは無視されます。<br>
                        まず「検証のみ」で結果を確認してから、「取り込む」を押してください。
                    </p>
                    <input type="file" id="fileInput" accept=".json,application/json" style="margin-bottom:10px;" onchange="loadFile(this)">
                    <label style="display:block; font-size:0.88em; margin-bottom:6px; cursor:pointer;">
                        <input type="checkbox" id="includeText" checked style="margin-right:6px;">表示名・説明・推奨アイテムも取り込む
                    </label>
                    <label style="display:block; font-size:0.88em; margin-bottom:10px; cursor:pointer;">
                        <input type="checkbox" id="includeGacha" checked style="margin-right:6px;">値段・排出確率（重み）も取り込む
                    </label>
                    <textarea id="jsonText" class="io-textarea" placeholder="ここに JSON を貼り付けるか、上でファイルを選択してください" oninput="updateImportButtons()"></textarea>
                    <button type="button" id="dryRunBtn" class="io-btn io-btn-gray" onclick="runImport(true)" disabled>検証のみ（保存しない）</button>
                    <button type="button" id="importBtn" class="io-btn io-btn-orange" onclick="runImport(false)" disabled>取り込む</button>
                    <div id="ioResult" class="io-result"></div>
                </div>
            </div>

            <script>
                function checks() { return Array.from(document.querySelectorAll('.io-check')); }
                function selectedIds() { return checks().filter(function (c) { return c.checked; }).map(function (c) { return c.value; }); }

                function updateCount() {
                    const n = selectedIds().length;
                    document.getElementById('selectedCount').textContent = '選択中: ' + n;
                    document.getElementById('exportSelectedBtn').disabled = n === 0;
                }

                function setAll(v) {
                    checks().forEach(function (c) { c.checked = v; });
                    updateCount();
                }

                document.getElementById('ioBody').addEventListener('change', function (e) {
                    if (e.target.classList.contains('io-check')) updateCount();
                });

                function exportSelected() {
                    const ids = selectedIds();
                    if (ids.length === 0) return;
                    location.href = '/settings-io/export?modelIds=' + encodeURIComponent(ids.join(','));
                }

                function exportAll() {
                    location.href = '/settings-io/export';
                }

                function showResult(ok, text) {
                    const box = document.getElementById('ioResult');
                    box.className = 'io-result ' + (ok ? 'ok' : 'ng');
                    box.textContent = text;
                }

                function updateImportButtons() {
                    const has = document.getElementById('jsonText').value.trim().length > 0;
                    document.getElementById('dryRunBtn').disabled = !has;
                    document.getElementById('importBtn').disabled = !has;
                }

                function loadFile(input) {
                    const file = input.files && input.files[0];
                    if (!file) return;
                    const reader = new FileReader();
                    reader.onload = function () {
                        document.getElementById('jsonText').value = reader.result;
                        updateImportButtons();
                    };
                    reader.readAsText(file, 'UTF-8');
                }

                function runImport(dryRun) {
                    const text = document.getElementById('jsonText').value.trim();
                    if (!text) return;
                    if (!dryRun && !confirm('設定を取り込みます。現在の設定は上書きされます。よろしいですか？')) return;

                    const dryBtn = document.getElementById('dryRunBtn');
                    const impBtn = document.getElementById('importBtn');
                    dryBtn.disabled = true;
                    impBtn.disabled = true;

                    const includeGacha = document.getElementById('includeGacha').checked;
                    const includeText = document.getElementById('includeText').checked;
                    fetch('/settings-io/import?dryRun=' + (dryRun ? 'true' : 'false')
                        + '&includeGacha=' + (includeGacha ? 'true' : 'false')
                        + '&includeText=' + (includeText ? 'true' : 'false'), {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json; charset=UTF-8' },
                        body: text
                    }).then(function (res) {
                        return res.text().then(function (t) { return { ok: res.ok, text: t }; });
                    }).then(function (r) {
                        updateImportButtons();
                        showResult(r.ok, r.text);
                    }).catch(function () {
                        updateImportButtons();
                        showResult(false, '通信エラーが発生しました。');
                    });
                }

                updateCount();
                updateImportButtons();
            </script>
        """.trimIndent()

        return WebHtmlTemplates.renderPage("Settings IO - ResourcePackFlow", playerName, body)
    }
}