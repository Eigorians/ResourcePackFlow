package com.eastcompany.eastsub.gacha.web.page

import com.eastcompany.eastsub.gacha.model.RegisteredModelData
import com.eastcompany.eastsub.gacha.web.form.BoolSetting
import com.eastcompany.eastsub.gacha.web.form.EnumSetting
import com.eastcompany.eastsub.gacha.web.form.ModelSetting
import com.eastcompany.eastsub.gacha.web.form.ModelSettings
import com.eastcompany.eastsub.gacha.web.form.htmlEscape
import com.eastcompany.eastsub.gacha.web.template.WebHtmlTemplates

object PackBulkEditPage {

    private fun esc(s: String) = htmlEscape(s)

    /** 一括変更パネルの <select> (設定ごとに自動生成) */
    private fun fieldHtml(s: ModelSetting): String {
        val options: List<Pair<String, String>> = when (s) {
            is BoolSetting -> listOf("true" to "ON にする", "false" to "OFF にする")
            is EnumSetting -> s.choices
        }
        val opts = options.joinToString("\n") { (v, l) -> """<option value="${esc(v)}">${esc(l)}</option>""" }
        return """
            <div class="bulk-field">
                <label class="bulk-label" for="bulk_${s.key}">${esc(s.label)}</label>
                <select id="bulk_${s.key}" data-setting="${s.key}" class="bulk-select">
                    <option value="keep">変更しない</option>
                    $opts
                </select>
            </div>
        """.trimIndent()
    }

    /** 一覧の1セル */
    private fun cellHtml(s: ModelSetting, m: RegisteredModelData): String {
        if (!s.applicable(m)) return esc(s.fixedLabel)
        return when (s) {
            is BoolSetting -> if (s.get(m)) "✔" else "－"
            is EnumSetting -> """<span class="bulk-tag">${esc(s.labelOf(s.getValue(m)))}</span>"""
        }
    }

    fun render(playerName: String, models: List<RegisteredModelData>): String {
        val rarityOrder = ModelSettings.rarityOrder
        val bulkable = ModelSettings.bulkable

        val settingFieldsHtml = bulkable.joinToString("\n") { fieldHtml(it) }

        val rarityFilterOptions = rarityOrder.joinToString("\n") {
            """<option value="${it.name}">${ModelSettings.rarityLabel(it)}</option>"""
        }

        val headerCols = bulkable.joinToString("\n") { "<th>${esc(it.shortLabel)}</th>" }

        val rowsHtml = models
            .sortedWith(compareBy({ rarityOrder.indexOf(it.rarity).let { i -> if (i < 0) 99 else i } }, { it.modelId }))
            .joinToString("\n") { m ->
                val thumb = m.texturePaths.firstOrNull()
                val thumbHtml = if (thumb != null) {
                    """<img src="/edit/texture?path=${esc(thumb)}" alt="" class="bulk-thumb">"""
                } else {
                    """<span class="bulk-thumb bulk-thumb-empty"></span>"""
                }
                val cols = bulkable.joinToString("\n") { "<td>${cellHtml(it, m)}</td>" }
                """
                <tr class="bulk-row" data-rarity="${m.rarity.name}" data-name="${esc(m.itemName.lowercase())} ${esc(m.modelId.lowercase())}">
                    <td><input type="checkbox" class="bulk-check" value="${esc(m.modelId)}"></td>
                    <td>$thumbHtml</td>
                    <td><a href="/edit?modelId=${esc(m.modelId)}" class="bulk-link">${esc(m.itemName)}</a><div class="bulk-sub">${esc(m.modelId)}</div></td>
                    $cols
                </tr>
                """.trimIndent()
            }

        val body = """
            <style>
                .bulk-root { max-width: 1100px; width: 95%; margin: 20px auto; padding: 28px; background: #fff; border-radius: 8px;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.08); color: #333; box-sizing: border-box;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; text-align: left; }
                .bulk-root * { box-sizing: border-box; }
                .bulk-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #f1f3f5;
                    padding-bottom: 14px; margin-bottom: 18px; flex-wrap: wrap; gap: 12px; }
                .bulk-title { margin: 0; font-size: 1.4rem; font-weight: bold; color: #212529; }
                .bulk-panel { background: #f8f9fa; border: 1px solid #e9ecef; border-left: 4px solid #007bff; border-radius: 6px;
                    padding: 14px 16px; margin-bottom: 18px; }
                .bulk-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 12px; margin-bottom: 12px; }
                .bulk-label { display: block; font-size: 0.85em; font-weight: bold; color: #495057; margin-bottom: 4px; }
                .bulk-select, .bulk-input { width: 100%; height: 36px; padding: 0 10px; border: 1px solid #ced4da; border-radius: 4px;
                    background: #fff; font-size: 0.92em; color: #333; }
                .bulk-btn { height: 38px; padding: 0 18px; border: none; border-radius: 4px; font-size: 0.9rem; font-weight: bold;
                    cursor: pointer; color: #fff; background: #007bff; }
                .bulk-btn:disabled { opacity: 0.5; cursor: not-allowed; }
                .bulk-btn-gray { background: #6c757d; }
                .bulk-btn-small { height: 30px; padding: 0 12px; font-size: 0.8rem; }
                .bulk-toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 10px; }
                .bulk-count { font-weight: bold; color: #1971c2; margin-left: auto; }
                .bulk-table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
                .bulk-table th { background: #f1f3f5; text-align: left; padding: 8px 10px; border-bottom: 2px solid #dee2e6; white-space: nowrap; }
                .bulk-table td { padding: 6px 10px; border-bottom: 1px solid #eee; vertical-align: middle; }
                .bulk-row:hover { background: #f8f9fa; }
                .bulk-row.hidden-row { display: none; }
                .bulk-thumb { width: 32px; height: 32px; object-fit: contain; image-rendering: pixelated; border: 1px solid #ddd; border-radius: 4px;
                    background: repeating-conic-gradient(#eee 0% 25%, #fff 0% 50%) 50% / 8px 8px; display: inline-block; }
                .bulk-thumb-empty { background: #f1f3f5; }
                .bulk-link { color: #212529; font-weight: bold; text-decoration: none; }
                .bulk-link:hover { text-decoration: underline; }
                .bulk-sub { font-size: 0.75rem; color: #868e96; }
                .bulk-tag { display: inline-block; padding: 2px 8px; border-radius: 4px; background: #e9ecef; font-size: 0.78rem; font-weight: bold; }
                .bulk-result { margin-top: 12px; padding: 10px 14px; border-radius: 4px; font-size: 0.9rem; display: none; white-space: pre-line; }
                .bulk-result.ok { display: block; background: #d4edda; color: #155724; }
                .bulk-result.ng { display: block; background: #f8d7da; color: #721c24; }
            </style>

            <div class="bulk-root">
                <div class="bulk-header">
                    <h2 class="bulk-title">一括設定変更</h2>
                    <div style="display:flex; gap:8px;">
                        <button type="button" class="bulk-btn bulk-btn-gray" onclick="location.href='/settings-io'">設定の出力・取り込み</button>
                        <button type="button" class="bulk-btn bulk-btn-gray" onclick="location.href='/list'">← 一覧に戻る</button>
                    </div>
                </div>

                <div class="bulk-panel">
                    <div class="bulk-grid">
                        $settingFieldsHtml
                    </div>
                    <p style="font-size:0.8em; color:#6c757d; margin:0 0 12px 0;">
                        「変更しない」の項目は現在の値のまま維持されます。モデルの条件により変更できない項目（一覧で「固定」と表示）は、そのモデルだけスキップされます。
                    </p>
                    <button type="button" id="applyBtn" class="bulk-btn" onclick="applyBulk()" disabled>選択したモデルに適用</button>
                    <div id="bulkResult" class="bulk-result"></div>
                </div>

                <div class="bulk-toolbar">
                    <input type="text" id="searchInput" class="bulk-input" style="width:220px;" placeholder="名前・IDで絞り込み" oninput="applyFilter()">
                    <select id="rarityFilter" class="bulk-select" style="width:150px;" onchange="applyFilter()">
                        <option value="">全レア度</option>
                        $rarityFilterOptions
                    </select>
                    <button type="button" class="bulk-btn bulk-btn-gray bulk-btn-small" onclick="setVisibleChecked(true)">表示中を全選択</button>
                    <button type="button" class="bulk-btn bulk-btn-gray bulk-btn-small" onclick="setVisibleChecked(false)">全解除</button>
                    <span id="selectedCount" class="bulk-count">選択中: 0</span>
                </div>

                <table class="bulk-table">
                    <thead>
                        <tr>
                            <th style="width:36px;"><input type="checkbox" id="checkAll" onchange="setVisibleChecked(this.checked)"></th>
                            <th style="width:48px;"></th>
                            <th>モデル</th>
                            $headerCols
                        </tr>
                    </thead>
                    <tbody id="bulkBody">
                        $rowsHtml
                    </tbody>
                </table>
            </div>

            <script>
                function visibleRows() {
                    return Array.from(document.querySelectorAll('.bulk-row:not(.hidden-row)'));
                }

                function applyFilter() {
                    const q = document.getElementById('searchInput').value.trim().toLowerCase();
                    const r = document.getElementById('rarityFilter').value;
                    document.querySelectorAll('.bulk-row').forEach(function (row) {
                        const matchName = !q || row.dataset.name.includes(q);
                        const matchRarity = !r || row.dataset.rarity === r;
                        row.classList.toggle('hidden-row', !(matchName && matchRarity));
                    });
                    updateCount();
                }

                function setVisibleChecked(checked) {
                    visibleRows().forEach(function (row) {
                        row.querySelector('.bulk-check').checked = checked;
                    });
                    if (!checked) {
                        document.querySelectorAll('.bulk-check').forEach(function (c) { c.checked = false; });
                    }
                    updateCount();
                }

                function selectedIds() {
                    return Array.from(document.querySelectorAll('.bulk-check:checked')).map(function (c) { return c.value; });
                }

                function updateCount() {
                    const n = selectedIds().length;
                    document.getElementById('selectedCount').textContent = '選択中: ' + n;
                    document.getElementById('applyBtn').disabled = n === 0;
                }

                document.getElementById('bulkBody').addEventListener('change', function (e) {
                    if (e.target.classList.contains('bulk-check')) updateCount();
                });

                function showResult(ok, text) {
                    const box = document.getElementById('bulkResult');
                    box.className = 'bulk-result ' + (ok ? 'ok' : 'ng');
                    box.textContent = text;
                }

                function applyBulk() {
                    const ids = selectedIds();
                    if (ids.length === 0) return;

                    // 設定項目は data-setting から集める (項目を足してもここは変更不要)
                    const settings = {};
                    document.querySelectorAll('[data-setting]').forEach(function (el) {
                        settings[el.dataset.setting] = el.value;
                    });

                    const allKeep = Object.values(settings).every(function (v) { return v === 'keep'; });
                    if (allKeep) {
                        showResult(false, '変更する項目を1つ以上選択してください。');
                        return;
                    }

                    if (!confirm(ids.length + ' 件のモデルに設定を適用しますか？')) return;

                    const btn = document.getElementById('applyBtn');
                    btn.disabled = true;
                    btn.textContent = '適用中...';

                    const params = new URLSearchParams();
                    params.append('modelIds', ids.join(','));
                    Object.keys(settings).forEach(function (k) { params.append(k, settings[k]); });

                    fetch('/bulk-edit', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                        body: params.toString()
                    }).then(function (res) {
                        return res.text().then(function (text) { return { ok: res.ok, text: text }; });
                    }).then(function (r) {
                        btn.textContent = '選択したモデルに適用';
                        if (r.ok) {
                            showResult(true, r.text + '\n（反映のためページを再読み込みします）');
                            setTimeout(function () { location.reload(); }, 1200);
                        } else {
                            btn.disabled = false;
                            showResult(false, r.text);
                        }
                    }).catch(function () {
                        btn.disabled = false;
                        btn.textContent = '選択したモデルに適用';
                        showResult(false, '通信エラーが発生しました。');
                    });
                }

                updateCount();
            </script>
        """.trimIndent()

        return WebHtmlTemplates.renderPage("Bulk Edit - ResourcePackFlow", playerName, body)
    }
}