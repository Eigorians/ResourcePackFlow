package com.eastcompany.eastsub.gacha.web.page

import com.eastcompany.eastsub.gacha.item.ItemFilter
import com.eastcompany.eastsub.gacha.model.RegisteredModelData
import com.eastcompany.eastsub.gacha.web.form.BoolSetting
import com.eastcompany.eastsub.gacha.web.form.EnumSetting
import com.eastcompany.eastsub.gacha.web.form.ModelSettings
import com.eastcompany.eastsub.gacha.web.form.htmlEscape
import com.eastcompany.eastsub.gacha.web.template.WebHtmlTemplates
import org.bukkit.ChatColor
import org.bukkit.Material

object PackEditPage {

    private val colorOptionsHtml: String = listOf(
        Pair("black", "黒 (Black)" to ChatColor.BLACK),
        Pair("dark_blue", "ダークブルー (Dark Blue)" to ChatColor.DARK_BLUE),
        Pair("dark_green", "ダークグリーン (Dark Green)" to ChatColor.DARK_GREEN),
        Pair("dark_aqua", "ダークアクア (Dark Aqua)" to ChatColor.DARK_AQUA),
        Pair("dark_red", "ダークレッド (Dark Red)" to ChatColor.DARK_RED),
        Pair("dark_purple", "ダークパープル (Dark Purple)" to ChatColor.DARK_PURPLE),
        Pair("gold", "ゴールド (Gold)" to ChatColor.GOLD),
        Pair("gray", "グレー (Gray)" to ChatColor.GRAY),
        Pair("dark_gray", "ダークグレー (Dark Gray)" to ChatColor.DARK_GRAY),
        Pair("blue", "青 (Blue)" to ChatColor.BLUE),
        Pair("green", "緑 (Green)" to ChatColor.GREEN),
        Pair("aqua", "アクア (Aqua)" to ChatColor.AQUA),
        Pair("red", "赤 (Red)" to ChatColor.RED),
        Pair("light_purple", "ライトパープル (Light Purple)" to ChatColor.LIGHT_PURPLE),
        Pair("yellow", "黄色 (Yellow)" to ChatColor.YELLOW),
        Pair("white", "白 (White)" to ChatColor.WHITE)
    ).joinToString("\n") { (key, info) ->
        val (label, color) = info
        val hex = when (color) {
            ChatColor.BLACK -> "#000000"
            ChatColor.DARK_BLUE -> "#0000AA"
            ChatColor.DARK_GREEN -> "#00AA00"
            ChatColor.DARK_AQUA -> "#00AAAA"
            ChatColor.DARK_RED -> "#AA0000"
            ChatColor.DARK_PURPLE -> "#AA00AA"
            ChatColor.GOLD -> "#FFAA00"
            ChatColor.GRAY -> "#AAAAAA"
            ChatColor.DARK_GRAY -> "#555555"
            ChatColor.BLUE -> "#5555FF"
            ChatColor.GREEN -> "#55FF55"
            ChatColor.AQUA -> "#55FFFF"
            ChatColor.RED -> "#FF5555"
            ChatColor.LIGHT_PURPLE -> "#FF55FF"
            ChatColor.YELLOW -> "#FFFF55"
            ChatColor.WHITE -> "#FFFFFF"
            else -> "#333333"
        }
        """<option value="$key" data-hex="$hex">$label</option>"""
    }

    /** 推奨アイテム欄: アイテム排出オフ(パック)のとき選べる全素材 */
    private val allMaterialNames: String by lazy {
        Material.values()
            .filter { !it.isLegacy && it.isItem }
            .joinToString(",") { "\"${it.name}\"" }
    }

    /** 推奨アイテム欄: アイテム排出オンのとき選べる素材（適用が許可されているもの） */
    private val allowedMaterialNames: String by lazy {
        Material.values()
            .filter { !it.isLegacy && it.isItem && !ItemFilter.isBlockedApplyItems(it) }
            .joinToString(",") { material ->
                val name = material.name
                val jp = ItemFilter.translations[name]
                "\"[$jp] : $name\""
            }
    }

    fun render(playerName: String, data: RegisteredModelData): String {
        val rarityBadgeStyle = data.rarity.badgeStyle

        val texturePreviewsHtml = if (data.texturePaths.isNotEmpty()) {
            data.texturePaths.joinToString("\n") { path ->
                val fileName = path.substringAfterLast('/')
                """
                <div style="display: flex; flex-direction: column; align-items: center; width: 72px; padding: 6px; background: #ffffff; border: 1px solid #dee2e6; border-radius: 6px; box-sizing: border-box;">
                    <img src="/edit/texture?path=${path}" alt="$fileName" style="width: 60px; height: 60px; object-fit: contain; image-rendering: pixelated; border: 1px solid #ddd; background: repeating-conic-gradient(#eee 0% 25%, #fff 0% 50%) 50% / 12px 12px; border-radius: 4px;">
                    <span style="font-size: 0.7em; color: #6c757d; margin-top: 4px; text-overflow: ellipsis; overflow: hidden; white-space: nowrap; width: 100%; text-align: center;" title="$fileName">$fileName</span>
                </div>
                """.trimIndent()
            }
        } else {
            """<span style="font-size: 0.85em; color: #6c757d;">(関連するカスタムテクスチャはありません)</span>"""
        }

        val rawKey = data.key ?: "${playerName.lowercase()}/${data.modelId}"
        val cleanKey = if (rawKey.startsWith("gacha:")) rawKey.substringAfter("gacha:") else rawKey
        val pdcFullKey = "gacha:$cleanKey"

        val currentColor = data.itemName.let { name ->
            if (name.startsWith("§") && name.length >= 2) {
                when (name[1]) {
                    '0' -> "black"
                    '1' -> "dark_blue"
                    '2' -> "dark_green"
                    '3' -> "dark_aqua"
                    '4' -> "dark_red"
                    '5' -> "dark_purple"
                    '6' -> "gold"
                    '7' -> "gray"
                    '8' -> "dark_gray"
                    '9' -> "blue"
                    'a' -> "green"
                    'b' -> "aqua"
                    'c' -> "red"
                    'd' -> "light_purple"
                    'e' -> "yellow"
                    'f' -> "white"
                    else -> ""
                }
            } else ""
        }
        val rawItemName = if (data.itemName.startsWith("§") && data.itemName.length >= 2) {
            data.itemName.substring(2)
        } else {
            data.itemName
        }

        // ---- 推奨アイテム欄の表示制御 ----
        val isItemModel = data.slotType.equals("item_model", ignoreCase = true)
        val savedRecommended = data.recommendedItem ?: ""

        val recommendedHtml = if (isItemModel) """
            <div class="edit-form-field">
                <label class="edit-label-text">推奨アイテム パックにしない場合このアイテムで排出:</label>
                <select id="recommendedItemSelect" name="recommendedItem" class="edit-input-style" onchange="triggerAutoSave()"></select>
                <p id="recommendedHint" style="font-size: 0.8em; color: #6c757d; margin: 6px 0 0 0;"></p>
            </div>
        """ else ""

        val body = """
            <style>
                .edit-card-root {
                    max-width: 720px !important;
                    width: 100% !important;
                    margin: 20px auto !important;
                    padding: 28px !important;
                    background: #ffffff !important;
                    border-radius: 8px !important;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.08) !important;
                    text-align: left !important;
                    box-sizing: border-box !important;
                    color: #333 !important;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important;
                }

                .edit-card-root * {
                    box-sizing: border-box !important;
                }

                .edit-header-area {
                    border-bottom: 2px solid #f1f3f5 !important;
                    padding-bottom: 14px !important;
                    margin-bottom: 22px !important;
                    display: flex !important;
                    justify-content: space-between !important;
                    align-items: center !important;
                }

                .edit-title-text {
                    margin: 0 0 10px 0 !important;
                    font-size: 1.4rem !important;
                    font-weight: bold !important;
                    color: #212529 !important;
                }

                .save-status-badge {
                    display: inline-flex !important;
                    align-items: center !important;
                    padding: 4px 10px !important;
                    border-radius: 4px !important;
                    font-size: 0.8rem !important;
                    font-weight: bold !important;
                    transition: opacity 0.3s !important;
                    opacity: 0;
                }

                .save-status-badge.saving {
                    background: #fff3cd !important;
                    color: #856404 !important;
                    opacity: 1 !important;
                }

                .save-status-badge.saved {
                    background: #d4edda !important;
                    color: #155724 !important;
                    opacity: 1 !important;
                }

                .badge-container {
                    display: flex !important;
                    flex-wrap: wrap !important;
                    gap: 8px !important;
                    align-items: center !important;
                }

                .badge-item {
                    display: inline-flex !important;
                    align-items: center !important;
                    justify-content: center !important;
                    padding: 4px 10px !important;
                    border-radius: 4px !important;
                    font-size: 0.78rem !important;
                    font-weight: bold !important;
                    line-height: 1.2 !important;
                    white-space: nowrap !important;
                }

                .edit-form-field {
                    margin-bottom: 18px !important;
                    text-align: left !important;
                }

                .edit-label-text {
                    display: block !important;
                    font-size: 0.88em !important;
                    font-weight: bold !important;
                    color: #495057 !important;
                    margin-bottom: 6px !important;
                    text-align: left !important;
                }

                .edit-input-style {
                    width: 100% !important;
                    height: 40px !important;
                    padding: 0 12px !important;
                    border: 1px solid #ced4da !important;
                    border-radius: 4px !important;
                    font-size: 0.95em !important;
                    background: #ffffff !important;
                    color: #333 !important;
                }

                .edit-input-style:disabled {
                    background: #e9ecef !important;
                    color: #6c757d !important;
                }

                .edit-textarea-style {
                    width: 100% !important;
                    padding: 10px 12px !important;
                    border: 1px solid #ced4da !important;
                    border-radius: 4px !important;
                    font-size: 0.95em !important;
                    resize: vertical !important;
                    background: #ffffff !important;
                    color: #333 !important;
                }

                .edit-btn-action {
                    display: inline-flex !important;
                    align-items: center !important;
                    justify-content: center !important;
                    height: 38px !important;
                    padding: 0 18px !important;
                    border: none !important;
                    border-radius: 4px !important;
                    font-size: 0.9rem !important;
                    font-weight: bold !important;
                    cursor: pointer !important;
                    white-space: nowrap !important;
                    text-decoration: none !important;
                    transition: opacity 0.2s !important;
                }

                .edit-btn-action:hover {
                    opacity: 0.85 !important;
                }

                .copy-input-group {
                    display: flex !important;
                    gap: 8px !important;
                    align-items: center !important;
                }

                .btn-copy-action {
                    height: 40px !important;
                    padding: 0 14px !important;
                    background: #17a2b8 !important;
                    color: white !important;
                    border: none !important;
                    border-radius: 4px !important;
                    font-size: 0.85rem !important;
                    font-weight: bold !important;
                    cursor: pointer !important;
                    white-space: nowrap !important;
                    transition: background 0.2s !important;
                }

                .btn-copy-action:hover {
                    background: #138496 !important;
                }

                .pdc-info-box {
                    background: #f8f9fa !important;
                    border: 1px solid #e9ecef !important;
                    border-left: 4px solid #6f42c1 !important;
                    padding: 14px 16px !important;
                    border-radius: 6px !important;
                    margin-bottom: 18px !important;
                }

                .pdc-code-text {
                    font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace !important;
                    font-weight: bold !important;
                    color: #6f42c1 !important;
                    background: #f1f0f7 !important;
                    padding: 2px 6px !important;
                    border-radius: 4px !important;
                }
            </style>

            <div class="edit-card-root">
                <div class="edit-header-area">
                    <div>
                        <h2 class="edit-title-text">モデル詳細・編集画面</h2>
                        <div class="badge-container">
                            <span class="badge-item" style="background: #e9ecef; color: #495057; border: 1px solid #ced4da;">${data.slotType}</span>
                            <span class="badge-item" style="$rarityBadgeStyle">RARITY: ${data.rarity.displayName}</span>
                        </div>
                    </div>
                    <div>
                        <span id="saveStatusBadge" class="save-status-badge">保存完了</span>
                    </div>
                </div>

                <form id="editForm" style="display: flex; flex-direction: column;">
                    <input type="hidden" name="modelId" value="${data.modelId}">
                    <input type="hidden" id="combinedItemName" name="itemName" value="${data.itemName}">

                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 18px;">
                        <div>
                            <label class="edit-label-text">モデル ID (固定):</label>
                            <input type="text" class="edit-input-style" value="${data.modelId}" disabled>
                        </div>
                        <div>
                            <label class="edit-label-text">ベースアイテム (固定):</label>
                            <input type="text" class="edit-input-style" value="${data.baseItem ?: "なし (ItemModel)"}" disabled>
                        </div>
                    </div>

                    <!-- PDC識別タグ情報エリア -->
                    <div class="pdc-info-box">
                        <label class="edit-label-text" style="color: #6f42c1; font-size: 0.95em;">
                            🧩 PDC 識別キー (PersistentDataContainer):
                        </label>
                        <div class="copy-input-group" style="margin-bottom: 8px;">
                            <input type="text" id="pdcKeyInput" class="edit-input-style" value="$pdcFullKey" readonly style="font-family: monospace; font-weight: bold; color: #6f42c1; background: #fff;">
                            <button type="button" class="btn-copy-action" style="background: #6f42c1;" onclick="copyToClipboard('pdcKeyInput', this)">コピー</button>
                        </div>
                        <p style="font-size: 0.82em; color: #555; margin: 0; line-height: 1.45;">
                            <b>【外部プラグイン連携について】</b><br>
                            このアイテムには、別の自作プラグインから個体を一元識別できるように、<b>PersistentDataContainer (PDC)</b> に <span class="pdc-code-text">gacha:key</span> という名前空間キーで値 <span class="pdc-code-text">$pdcFullKey</span> が永続付与されます。
                        </p>
                    </div>

                    <div class="edit-form-field">
                        <label class="edit-label-text">登録テクスチャプレビュー:</label>
                        <div style="display: flex; flex-wrap: wrap; gap: 10px; padding: 12px; background: #f8f9fa; border: 1px solid #dee2e6; border-radius: 6px; min-height: 76px; align-items: center;">
                            $texturePreviewsHtml
                        </div>
                    </div>

                    <!-- 表示名 ＆ ChatColor 選択フィールド ＆ 色付きサンプル表示レイアウト -->
                    <div class="edit-form-field">
                        <label class="edit-label-text">表示名 (ゲーム内表記) &amp; チャットカラー:</label>

                        <!-- 1. サンプル文字表示欄 -->
                        <div style="margin-bottom: 8px; padding: 10px 14px; background: #1e1e24; border-radius: 4px; border: 1px solid #333; display: flex; align-items: center;">
                            <span style="font-size: 0.8rem; color: #aaa; margin-right: 10px;">サンプル:</span>
                            <span id="namePreviewBox" style="font-size: 1.05rem; font-weight: bold;">(プレビュー)</span>
                        </div>

                        <!-- 2. 色選択 ＆ 3. 文字入力 -->
                        <div style="display: flex; gap: 8px;">
                            <select id="chatColorSelect" class="edit-input-style" style="width: 180px;" onchange="updateCombinedName()">
                                <option value="" data-hex="#ffffff">(色なし)</option>
                                $colorOptionsHtml
                            </select>
                            <input type="text" id="itemNameInput" class="edit-input-style" value="$rawItemName" required placeholder="表示名を入力" oninput="updateCombinedName()">
                        </div>
                    </div>

                    <div class="edit-form-field">
                        <label class="edit-label-text">パックとガチャに表示される説明欄・自由記載:</label>
                        <textarea name="description" id="descriptionInput" rows="3" class="edit-textarea-style" oninput="triggerAutoSave()">${data.description ?: ""}</textarea>
                    </div>

                    <!-- 設定項目 (ModelSettings.editable から自動生成) -->
                    ${settingsHtml(data)}

                    $recommendedHtml

                    <hr style="border: 0; border-top: 1px solid #eee; margin: 6px 0 20px 0;">

                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <button type="button" class="edit-btn-action" style="background: #6c757d; color: white;" onclick="location.href='/list'">← 一覧に戻る</button>

                        <div style="display: flex; gap: 8px; flex-wrap: wrap;">
                            <button type="button" id="givePackBtn" class="edit-btn-action" style="background: #fd7e14; color: white;" onclick="submitGivePack()">🎁 パック取得</button>
                            <button type="button" id="buildBtn" class="edit-btn-action" style="background: #28a745; color: white;" onclick="submitBuildAndSend()">📦 パック更新＆送信</button>
                            <button type="button" class="edit-btn-action" style="background: #dc3545; color: white;" onclick="submitDelete()">削除</button>
                        </div>
                    </div>
                </form>
            </div>

            <script>
                const ALL_MATERIALS = [$allMaterialNames];
                const ALLOWED_MATERIALS = [$allowedMaterialNames];
                const SAVED_RECOMMENDED = "$savedRecommended";

                window.addEventListener('DOMContentLoaded', function() {
                    const savedColor = "${currentColor}";
                    if (savedColor) {
                        const select = document.getElementById('chatColorSelect');
                        select.value = savedColor;
                    }
                    updateCombinedName(false);
                    rebuildRecommended(SAVED_RECOMMENDED);
                });

                function getColorCode(colorName) {
                    const map = {
                        'black': '§0', 'dark_blue': '§1', 'dark_green': '§2', 'dark_aqua': '§3',
                        'dark_red': '§4', 'dark_purple': '§5', 'gold': '§6', 'gray': '§7',
                        'dark_gray': '§8', 'blue': '§9', 'green': '§a', 'aqua': '§b',
                        'red': '§c', 'light_purple': '§d', 'yellow': '§e', 'white': '§f'
                    };
                    return map[colorName] || '';
                }

                function rebuildRecommended(preferred) {
                    const select = document.getElementById('recommendedItemSelect');
                    if (!select) return;

                    // チェックあり = アイテム排出 → 許可素材のみに絞る / チェックなし(パック) = すべての素材
                    const checkbox = document.getElementById('chk_dropAsItem');
                    const itemMode = checkbox ? checkbox.checked : false;
                    const list = itemMode ? ALLOWED_MATERIALS : ALL_MATERIALS;

                    select.innerHTML = '';
                    const none = document.createElement('option');
                    none.value = '';
                    none.textContent = '([木の棒] 未選択)';
                    select.appendChild(none);

                    list.forEach(function (rawItemString) {
                        // 例: "[ファントムの被膜] : PHANTOM_MEMBRANE" から純粋な "PHANTOM_MEMBRANE" を抽出する
                        const materialName = rawItemString.includes(":")
                            ? rawItemString.split(":").pop().trim()
                            : rawItemString.trim();

                        const opt = document.createElement('option');
                        // 保存される値を純粋な Material 名にする
                        opt.value = materialName;
                        // 表示は元の文字列にする
                        opt.textContent = rawItemString;
                        select.appendChild(opt);
                    });

                    // 比較・選択時もクリーンな名前で比較
                    const cleanPreferred = preferred && preferred.includes(":")
                        ? preferred.split(":").pop().trim()
                        : preferred;

                    select.value = cleanPreferred;

                    document.getElementById('recommendedHint').textContent = itemMode
                        ? '許可されている素材だけを表示しています。'
                        : 'すべての素材から選べます。';
                }

                function onSettingChanged(key) {
                    if (key === 'dropAsItem') {
                        const select = document.getElementById('recommendedItemSelect');
                        rebuildRecommended(select ? select.value : '');
                    }
                    triggerAutoSave();
                }

                function updateCombinedName(doSave = true) {
                    const colorSelect = document.getElementById('chatColorSelect');
                    const nameInput = document.getElementById('itemNameInput');
                    const combinedInput = document.getElementById('combinedItemName');
                    const previewBox = document.getElementById('namePreviewBox');

                    const selectedOption = colorSelect.options[colorSelect.selectedIndex];
                    const hexColor = selectedOption ? selectedOption.getAttribute('data-hex') : '#ffffff';

                    const colorCode = getColorCode(colorSelect.value);
                    const rawName = nameInput.value.trim();

                    combinedInput.value = colorCode + rawName;

                    // サンプル文字の更新
                    previewBox.style.color = hexColor;
                    previewBox.textContent = rawName.length > 0 ? rawName : "(プレビュー)";

                    if (doSave) {
                        triggerAutoSave();
                    }
                }

                let autoSaveTimer = null;

                function triggerAutoSave() {
                    showSaveStatus('saving');
                    clearTimeout(autoSaveTimer);
                    autoSaveTimer = setTimeout(() => {
                        autoSaveEdit();
                    }, 400);
                }

                function autoSaveEdit() {
                    const combinedName = document.getElementById('combinedItemName').value;
                    if (!combinedName) return;

                    const form = document.getElementById('editForm');
                    const params = new URLSearchParams(new FormData(form));
                    params.append('action', 'update');

                    fetch('/edit', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                        },
                        body: params.toString()
                    }).then(res => {
                        if (res.ok) {
                            showSaveStatus('saved');
                        } else {
                            console.error("オートセーブエラー");
                        }
                    }).catch(err => console.error("通信エラー", err));
                }

                function showSaveStatus(status) {
                    const badge = document.getElementById('saveStatusBadge');
                    if (status === 'saving') {
                        badge.textContent = "保存中...";
                        badge.className = "save-status-badge saving";
                    } else if (status === 'saved') {
                        badge.textContent = "✓ 自動保存されました";
                        badge.className = "save-status-badge saved";
                        setTimeout(() => {
                            badge.style.opacity = '0';
                        }, 2000);
                    }
                }

                function copyToClipboard(inputId, btn) {
                    const inputElement = document.getElementById(inputId);
                    if (!inputElement) return;

                    inputElement.select();
                    inputElement.setSelectionRange(0, 99999);

                    navigator.clipboard.writeText(inputElement.value).then(() => {
                        const originalText = btn.textContent;
                        const originalBg = btn.style.background;
                        btn.textContent = "コピー完了！";
                        btn.style.background = "#28a745";

                        setTimeout(() => {
                            btn.textContent = originalText;
                            btn.style.background = originalBg;
                        }, 1800);
                    }).catch(err => {
                        console.error("コピーに失敗しました", err);
                    });
                }

                function submitGivePack() {
                    const btn = document.getElementById('givePackBtn');
                    const originalText = btn.textContent;

                    btn.disabled = true;
                    btn.textContent = "付与中...";

                    const form = document.getElementById('editForm');
                    const params = new URLSearchParams(new FormData(form));
                    params.append('action', 'give_pack');

                    fetch('/edit', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                        },
                        body: params.toString()
                    }).then(res => {
                        if (res.ok) {
                            btn.textContent = "付与完了！";
                            setTimeout(() => {
                                btn.textContent = originalText;
                                btn.disabled = false;
                            }, 1500);
                        } else {
                            btn.textContent = originalText;
                            btn.disabled = false;
                            res.text().then(text => alert("付与エラー: " + text));
                        }
                    }).catch(() => {
                        btn.textContent = originalText;
                        btn.disabled = false;
                    });
                }

                function submitBuildAndSend() {
                    const btn = document.getElementById('buildBtn');
                    const originalText = btn.textContent;

                    btn.disabled = true;
                    btn.textContent = "送信中...";

                    const form = document.getElementById('editForm');
                    const params = new URLSearchParams(new FormData(form));
                    params.append('action', 'build_and_send');

                    fetch('/edit', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                        },
                        body: params.toString()
                    }).then(res => {
                        if (res.ok) {
                            btn.textContent = "送信完了！";
                            setTimeout(() => {
                                btn.textContent = originalText;
                                btn.disabled = false;
                            }, 1500);
                        } else {
                            btn.textContent = originalText;
                            btn.disabled = false;
                            res.text().then(text => alert("送信エラー: " + text));
                        }
                    }).catch(() => {
                        btn.textContent = originalText;
                        btn.disabled = false;
                    });
                }

                function submitDelete() {
                    if (!confirm("本当にこのモデルデータを削除しますか？")) return;

                    const form = document.getElementById('editForm');
                    const params = new URLSearchParams(new FormData(form));
                    params.append('action', 'delete');

                    fetch('/edit', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                        },
                        body: params.toString()
                    }).then(res => {
                        if (res.ok) {
                            location.href = '/list';
                        } else {
                            res.text().then(text => alert("エラー: " + text));
                        }
                    }).catch(() => {});
                }
            </script>
        """.trimIndent()

        return WebHtmlTemplates.renderPage("Edit - ResourcePackFlow", playerName, body)
    }

    /** ModelSettings.editable から設定欄 (チェックボックス / セレクト) を生成 */
    private fun settingsHtml(data: RegisteredModelData): String =
        ModelSettings.editable.joinToString("\n") { s ->
            val ok = s.applicable(data)
            val note = s.inapplicableNote?.takeIf { !ok }?.let {
                """<p style="font-size:0.8em; color:#6c757d; margin:6px 0 0 25px;">${htmlEscape(it)}</p>"""
            } ?: ""

            when (s) {
                is BoolSetting -> {
                    val checked = ok && s.get(data)
                    val hint = s.hint?.let { "（${htmlEscape(it)}）" } ?: ""
                    """
                    <div class="edit-form-field" style="margin-bottom: 14px !important;">
                        <label style="cursor: ${if (ok) "pointer" else "not-allowed"}; display: inline-flex; align-items: center; gap: 8px; font-size: 0.9em; font-weight: bold; color: #333;${if (ok) "" else " opacity: 0.55;"}">
                            <input type="checkbox" id="chk_${s.key}" name="${s.key}" value="true"${if (checked) " checked" else ""}${if (ok) "" else " disabled"} style="width: 17px; height: 17px; margin: 0;" onchange="onSettingChanged('${s.key}')">
                            ${htmlEscape(s.label)}$hint
                        </label>
                        $note
                    </div>
                    """.trimIndent()
                }
                is EnumSetting -> {
                    val cur = s.getValue(data)
                    val opts = s.choices.joinToString("") { (v, l) ->
                        """<option value="${htmlEscape(v)}"${if (v == cur) " selected" else ""}>${htmlEscape(l)}</option>"""
                    }
                    """
                    <div class="edit-form-field">
                        <label class="edit-label-text">${htmlEscape(s.label)}:</label>
                        <select name="${s.key}" class="edit-input-style"${if (ok) "" else " disabled"} onchange="onSettingChanged('${s.key}')">$opts</select>
                        $note
                    </div>
                    """.trimIndent()
                }
            }
        }
}