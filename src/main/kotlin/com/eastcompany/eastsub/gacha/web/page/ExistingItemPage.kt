package com.eastcompany.eastsub.gacha.web.page

import com.eastcompany.eastsub.gacha.item.ItemFilter
import com.eastcompany.eastsub.gacha.web.template.WebHtmlTemplates
import org.bukkit.Material

object ExistingItemPage {

    private val baseItemOptions: String by lazy {
        Material.entries
            .filter { !ItemFilter.isBlocked(it)}
            .sortedBy { it.name }
            .joinToString("\n") { mat ->
                val key = mat.name.lowercase()
                val selected = if (key == "stick") " selected" else ""
                """<option value="$key"$selected>$key</option>"""
            }
    }

    fun render(playerName: String): String {
        val body = """
            <div style="max-width:700px;background:#fff;padding:20px;border-radius:8px;box-shadow:0 2px 4px rgba(0,0,0,0.1);">
                <h2 style="margin-top:0;">既存アイテム登録画面</h2>

                <div style="background:#e7f1ff;border:1px solid #b8daff;color:#004085;padding:12px;border-radius:4px;margin-bottom:20px;">
                    resourcepack 内に配置済みのアイテムを選んで、ガチャに追加します。<b>ファイルのアップロードは行いません。</b><br>
                    この方法で登録したものは、画面からは削除できません（ファイルを守るため）。
                </div>

                <form id="existingForm" action="/add-existing" method="post"
                    style="display:flex;flex-direction:column;gap:15px;"
                    onsubmit="return handleSubmit(event)">

                    <div>
                        <label><b>対象フォルダ (resourcepack/assets/ 直下):</b></label><br>
                        <select id="nsSelect" style="padding:8px;width:100%;">
                            <option value="">すべて</option>
                        </select>
                        <span style="font-size:0.8em;color:#666;">
                            選択は保存され、次回以降も固定されます。下のアイテム・モデル・サムネの検索すべてに適用されます。
                        </span>
                    </div>

                    <div>
                        <label><b>アイテム定義から自動入力（任意）:</b></label><br>
                        <input type="text" id="itemSearch"
                            placeholder="assets/*/items/*.json を検索（識別コード・ファイル名）"
                            style="width:100%;padding:8px;box-sizing:border-box;">
                        <div id="itemList"
                            style="max-height:220px;overflow:auto;border:1px solid #ccc;margin-top:4px;background:#fff;">
                        </div>
                        <div id="itemStatus" style="margin-top:4px;font-size:0.85em;color:#856404;"></div>
                        <span style="font-size:0.8em;color:#666;">
                            選ぶと、スロット・識別コード・ベースアイテム・モデルファイル・モデルIDが入力されます。
                        </span>
                    </div>

                    <hr style="border:0;border-top:1px solid #eee;width:100%;margin:0;">

                    <div>
                        <label><b>モデルID（半角英数字、ハイフン、アンダースコア）:</b></label><br>
                        <input type="text" id="modelIdInput" name="modelId"
                            placeholder="モデルファイルの選択時に自動入力"
                            pattern="^[a-zA-Z0-9_-]+${'$'}"
                            required
                            style="width:100%;padding:8px;box-sizing:border-box;">
                        <span style="font-size:0.8em;color:#666;">
                            モデルファイルを選択するたびに、ファイル名から自動設定されます。必要なら手動で変更できます。
                        </span>
                    </div>

                    <div>
                        <label><b>表示名:</b></label><br>
                        <div style="display:flex;gap:8px;">
                            <input type="text" name="itemName" id="itemNameInput"
                                placeholder="ゲーム内での表示名を入力してください"
                                required style="flex:1;padding:8px;box-sizing:border-box;">
                            <button type="button" id="aiNameBtn" onclick="suggestName(true)"
                                style="padding:8px 12px;background:#6f42c1;color:white;border:none;border-radius:4px;cursor:pointer;white-space:nowrap;">
                                AIで名前を提案
                            </button>
                        </div>
                        <span id="aiNameStatus" style="font-size:0.8em;color:#856404;"></span>
                    </div>

                    <div>
                        <label><b>スロット選択:</b></label><br>
                        <div style="display:flex;gap:20px;margin-top:5px;">
                            <label style="cursor:pointer;">
                                <input type="radio" name="slotType" value="custom_model_data"
                                    checked onclick="toggleSlotType('cmd')">
                                <b>CustomModelData</b>
                            </label>
                            <label style="cursor:pointer;">
                                <input type="radio" name="slotType" value="item_model"
                                    onclick="toggleSlotType('im')">
                                <b>ItemModel</b>
                            </label>
                        </div>
                    </div>

                    <div>
                        <label><b id="codeLabel">識別コード (CustomModelData):</b></label><br>
                        <input type="text" name="code" id="codeInput"
                            required placeholder="例: mypack:sword"
                            pattern="^[a-zA-Z0-9_.:/-]+${'$'}"
                            style="width:100%;padding:8px;box-sizing:border-box;">
                        <span id="codeHint" style="font-size:0.8em;color:#666;">
                            minecraft/items/*.json の when に入っている文字列を入力してください。
                        </span>
                    </div>

                    <div id="cmdFloatBox">
                        <label><b>CMD 数値（数値で識別するアイテムのみ）:</b></label><br>
                        <input type="number" step="any" name="cmdFloat" id="cmdFloatInput"
                            placeholder="例: 1"
                            style="width:100%;padding:8px;box-sizing:border-box;">
                        <span style="font-size:0.8em;color:#666;">
                            range_dispatch の threshold に対応する値です。文字列で識別するアイテムは空欄にしてください。
                        </span>
                    </div>

                    <div id="baseItemContainer">
                        <label><b>ベースアイテム（CustomModelDataの場合は必須）:</b></label><br>
                        <select name="baseItem" id="baseItemSelect"
                            style="padding:8px;width:100%;">
                            $baseItemOptions
                        </select>
                    </div>

                    <div class="picker" data-type="model">
                        <label><b>モデルファイル (.json)（必須）:</b></label><br>
                        <input type="text" class="q"
                            placeholder="assets 内を検索（スペース区切りで AND）"
                            style="width:100%;padding:8px;box-sizing:border-box;">
                        <div class="list file-list"
                            style="max-height:200px;overflow:auto;border:1px solid #ccc;margin-top:4px;background:#fff;padding:4px;">
                        </div>
                        <div class="selected"
                            style="margin-top:6px;padding:8px;font-size:0.9em;color:#065f46;background:#f3f4f6;border:1px solid #d1d5db;border-radius:4px;word-break:break-all;">
                            未選択
                        </div>
                        <input type="hidden" name="modelFile" id="modelFileHidden">
                        <input type="hidden" name="modelLocation" id="modelLocationHidden">
                    </div>

                    <div class="picker" data-type="texture">
                        <label><b>サムネファイル (.png)（任意）:</b></label><br>
                        <input type="text" class="q"
                            placeholder="assets 内を検索（スペース区切りで AND）"
                            style="width:100%;padding:8px;box-sizing:border-box;">
                        <div class="list file-list"
                            style="max-height:200px;overflow:auto;border:1px solid #ccc;margin-top:4px;background:#fff;padding:4px;">
                        </div>
                        <img class="preview" alt="サムネイルプレビュー"
                            style="display:none;width:96px;height:96px;object-fit:contain;image-rendering:pixelated;border:2px solid #059669;border-radius:4px;margin-top:8px;background:#f1f3f5;">
                        <div class="selected"
                            style="margin-top:6px;padding:8px;font-size:0.9em;color:#065f46;background:#f3f4f6;border:1px solid #d1d5db;border-radius:4px;word-break:break-all;">
                            未選択
                        </div>
                        <button type="button" class="clear"
                            style="margin-top:4px;padding:5px 10px;font-size:0.85em;">
                            サムネイル選択を解除
                        </button>
                        <input type="hidden" name="thumbnailFile">
                    </div>

                    <div>
                        <label><b>説明欄・自由記載:</b></label><br>
                        <textarea name="description" rows="4"
                            style="width:100%;padding:8px;box-sizing:border-box;"></textarea>
                    </div>

                    <button type="submit" id="submitBtn"
                        style="padding:12px;background:#28a745;color:white;border:none;border-radius:4px;cursor:pointer;font-weight:bold;">
                        登録
                    </button>
                </form>

                <br>
                <button style="background:#6c757d;color:white;padding:8px 15px;border:none;border-radius:4px;cursor:pointer;"
                    onclick="location.href='/home'">
                    ホームへ戻る
                </button>
            </div>

            <style>
                .file-list .file-row {
                    display:block;
                    width:100%;
                    box-sizing:border-box;
                    text-align:left;
                    padding:9px 10px;
                    margin:3px 0;
                    border:1px solid #e5e7eb;
                    border-radius:4px;
                    background:#fff;
                    color:#222;
                    cursor:pointer;
                    font-size:0.85em;
                    overflow-wrap:anywhere;
                }
                .file-list .file-row:hover {
                    background:#eff6ff;
                    border-color:#60a5fa;
                }
                .file-list .file-row.is-selected {
                    background:#d1fae5;
                    border:2px solid #059669;
                    color:#065f46;
                    font-weight:bold;
                }
                .file-list .file-row.is-selected::before {
                    content:'✓ 選択中  ';
                }
                .file-list .item-row.is-selected {
                    background:#dbeafe;
                    border:2px solid #2563eb;
                    color:#1e40af;
                }
            </style>

            <script>
                // ---- 対象フォルダ(assets 直下)の選択。localStorage に保存して固定する ----
                var NS_KEY = 'existingItemNamespace';

                function nsParam() {
                    return '&ns=' + encodeURIComponent(document.getElementById('nsSelect').value);
                }

                function reloadAllLists() {
                    document.querySelectorAll('.picker').forEach(function (box) { loadPicker(box); });
                    if (window.reloadItemList) window.reloadItemList();
                }

                (function setupNamespace() {
                    var sel = document.getElementById('nsSelect');
                    var saved = '';
                    try { saved = localStorage.getItem(NS_KEY) || ''; } catch (e) {}

                    // 保存済みの値を先に反映しておく（最初の検索から絞り込みが効く）
                    if (saved) {
                        var tmp = document.createElement('option');
                        tmp.value = saved;
                        tmp.textContent = saved;
                        sel.appendChild(tmp);
                        sel.value = saved;
                    }

                    sel.addEventListener('change', function () {
                        try { localStorage.setItem(NS_KEY, sel.value); } catch (e) {}
                        reloadAllLists();
                    });

                    fetch('/add-existing/namespaces')
                        .then(function (r) { return r.json(); })
                        .then(function (list) {
                            var current = sel.value;
                            sel.innerHTML = '';

                            var all = document.createElement('option');
                            all.value = '';
                            all.textContent = 'すべて';
                            sel.appendChild(all);

                            list.forEach(function (ns) {
                                var o = document.createElement('option');
                                o.value = ns;
                                o.textContent = ns;
                                sel.appendChild(o);
                            });

                            if (current && list.indexOf(current) >= 0) {
                                sel.value = current;
                            } else if (current) {
                                // 保存済みのフォルダが無くなっていたら「すべて」に戻す
                                sel.value = '';
                                try { localStorage.setItem(NS_KEY, ''); } catch (e) {}
                                reloadAllLists();
                            }
                        })
                        .catch(function () {});
                })();

                function toggleSlotType(type) {
                    var container = document.getElementById('baseItemContainer');
                    var label = document.getElementById('codeLabel');
                    var hint = document.getElementById('codeHint');

                    if (type === 'cmd') {
                        container.style.display = 'block';
                        document.getElementById('cmdFloatBox').style.display = 'block';
                        label.textContent = '識別コード (CustomModelData):';
                        hint.textContent = 'minecraft/items/*.json の when に入っている文字列を入力してください。';
                    } else {
                        container.style.display = 'none';
                        document.getElementById('cmdFloatBox').style.display = 'none';
                        document.getElementById('cmdFloatInput').value = '';
                        label.textContent = 'ItemModel コード:';
                        hint.textContent = '例: namespace:path （assets/namespace/items/path.json に対応するコード）';
                    }
                }

                function modelIdFromPath(p) {
                    var name = p.substring(p.lastIndexOf('/') + 1)
                        .replace(/\.json${'$'}/i, '');

                    return name.toLowerCase()
                        .replace(/[^a-zA-Z0-9_-]/g, '_');
                }

                function idFromText(text) {
                    var last = text.substring(
                        Math.max(text.lastIndexOf('/'), text.lastIndexOf(':')) + 1
                    );

                    return last.replace(/\.json${'$'}/i, '')
                        .toLowerCase()
                        .replace(/[^a-zA-Z0-9_-]/g, '_');
                }

                function setSelectedLabel(box, path) {
                    var selected = box.querySelector('.selected');

                    selected.textContent = path ? '✓ 選択中: ' + path : '未選択';
                    selected.style.background = path ? '#d1fae5' : '#f3f4f6';
                    selected.style.border = path
                        ? '2px solid #059669'
                        : '1px solid #d1d5db';
                    selected.style.color = path ? '#065f46' : '#666';
                    selected.style.fontWeight = path ? 'bold' : 'normal';
                }

                function setModelFile(path) {
                    var box = document.querySelector('.picker[data-type=model]');
                    var hidden = document.getElementById('modelFileHidden');

                    hidden.value = path || '';
                    setSelectedLabel(box, path || '');

                    box.querySelectorAll('.file-row').forEach(function (row) {
                        row.classList.toggle('is-selected', row.dataset.path === path);
                    });
                }

                function setTextureFile(path) {
                    var box = document.querySelector('.picker[data-type=texture]');
                    var hidden = box.querySelector('input[name=thumbnailFile]');
                    var preview = box.querySelector('.preview');

                    hidden.value = path || '';
                    setSelectedLabel(box, path || '');

                    box.querySelectorAll('.file-row').forEach(function (row) {
                        row.classList.toggle('is-selected', row.dataset.path === path);
                    });

                    if (path) {
                        preview.src = '/add-existing/asset?path=' + encodeURIComponent(path);
                        preview.style.display = 'block';
                    } else {
                        preview.style.display = 'none';
                        preview.removeAttribute('src');
                    }
                }

                // モデルの選択に合わせてPNGを検索する。
                // 同じnamespace・同じファイル名を最優先し、
                // 見つからない場合は自動選択せず、現在の選択を解除する。
                function autoSelectTexture(modelPath) {
                    var box = document.querySelector('.picker[data-type=texture]');
                    var input = box.querySelector('.q');

                    // 前回の自動検索結果が遅れて返ってきても採用しない
                    var requestId = String(Date.now()) + Math.random();
                    box.dataset.autoRequest = requestId;

                    setTextureFile('');

                    fetch('/add-existing/model-textures?path=' + encodeURIComponent(modelPath))
                        .then(function (r) {
                            if (!r.ok) throw new Error('モデルのテクスチャ取得に失敗しました');
                            return r.json();
                        })
                        .then(function (textures) {
                            if (box.dataset.autoRequest !== requestId) return;

                            if (!Array.isArray(textures) || textures.length === 0) {
                                input.value = '';
                                loadPicker(box);
                                return;
                            }

                            // モデルJSONから取得した最初の実在PNGを選択
                            var texturePath = textures[0];

                            setTextureFile(texturePath);
                            input.value = texturePath;
                            loadPicker(box);
                        })
                        .catch(function (err) {
                            console.warn('テクスチャ自動選択エラー:', err);
                            if (box.dataset.autoRequest === requestId) {
                                setTextureFile('');
                            }
                        });
                }

                function loadPicker(box) {
                    var type = box.getAttribute('data-type');
                    var input = box.querySelector('.q');
                    var list = box.querySelector('.list');
                    var hidden = box.querySelector('input[type=hidden]');
                    var selectedPath = hidden.value;
                    var query = input.value;

                    fetch('/add-existing/search?type=' + type + '&q=' + encodeURIComponent(query) + nsParam())
                        .then(function (r) {
                            if (!r.ok) throw new Error('検索に失敗しました');
                            return r.json();
                        })
                        .then(function (items) {
                            list.innerHTML = '';

                            if (!items.length) {
                                list.textContent = '見つかりません';
                                return;
                            }

                            items.forEach(function (p) {
                                var row = document.createElement('button');
                                row.type = 'button';
                                row.className = 'file-row';
                                row.dataset.path = p;
                                row.textContent = p;

                                if (p === selectedPath) {
                                    row.classList.add('is-selected');
                                }

                                row.onclick = function () {
                                    if (type === 'model') {
                                        setModelFile(p);

                                        var idInput = document.getElementById('modelIdInput');
                                        idInput.value = modelIdFromPath(p);

                                        // 手動で入力されていたモデルIDも選択したファイル名で更新
                                        document.getElementById('modelLocationHidden').value = '';

                                        autoSelectTexture(p);
                                    } else {
                                        setTextureFile(p);
                                    }
                                };

                                list.appendChild(row);
                            });
                        })
                        .catch(function () {
                            list.textContent = '検索に失敗しました';
                        });
                }

                function setupPicker(box) {
                    var input = box.querySelector('.q');
                    var clearBtn = box.querySelector('.clear');
                    var timer = null;

                    input.addEventListener('input', function () {
                        clearTimeout(timer);
                        timer = setTimeout(function () {
                            loadPicker(box);
                        }, 250);
                    });

                    if (clearBtn) {
                        clearBtn.onclick = function () {
                            box.dataset.autoRequest = '';
                            setTextureFile('');
                        };
                    }

                    loadPicker(box);
                }

                document.querySelectorAll('.picker').forEach(setupPicker);

                // ---- items/*.json からの自動入力 ----
                function applyEntry(e) {
                    var isCmd = e.kind === 'custom_model_data';

                    document.querySelector(
                        'input[name=slotType][value="' + e.kind + '"]'
                    ).checked = true;

                    toggleSlotType(isCmd ? 'cmd' : 'im');

                    document.getElementById('codeInput').value = e.code;
                    document.getElementById('cmdFloatInput').value =
                        isCmd && e.floatValue != null ? e.floatValue : '';

                    if (isCmd && e.baseItem) {
                        document.getElementById('baseItemSelect').value = e.baseItem;
                    }

                    var idInput = document.getElementById('modelIdInput');
                    idInput.value = idFromText(
                        e.modelFile || e.modelLocation || e.code
                    );

                    var status = document.getElementById('itemStatus');
                    var locHidden = document.getElementById('modelLocationHidden');

                    if (e.modelFile) {
                        setModelFile(e.modelFile);
                        locHidden.value = '';

                        // 一覧にないモデルでも、PNG検索とプレビューを実行する
                        autoSelectTexture(e.modelFile);

                        status.textContent = '';
                    } else if (e.modelLocation) {
                        setModelFile('');
                        locHidden.value = e.modelLocation;

                        var modelBox = document.querySelector('.picker[data-type=model]');
                        setSelectedLabel(modelBox, '');

                        modelBox.querySelector('.selected').textContent =
                            '定義が指すモデルを使用: ' + e.modelLocation;

                        status.textContent =
                            'モデルは assets にありません。アイテム定義が指す ' +
                            e.modelLocation +
                            ' を使用します。別のモデルにしたい場合は、下の検索から選んでください。';

                        // バニラモデルなどではファイル名だけでPNGを特定できないため、
                        // 誤った画像を自動選択しない
                        setTextureFile('');
                    } else {
                        setModelFile('');
                        locHidden.value = '';
                        setTextureFile('');

                        status.textContent =
                            'このアイテム定義からモデルを特定できませんでした。下の検索から選んでください。';
                    }

                }

                (function setupItemPicker() {
                    var input = document.getElementById('itemSearch');
                    var list = document.getElementById('itemList');
                    var timer = null;
                    var currentEntry = null;

                    function load() {
                        fetch('/add-existing/items?q=' + encodeURIComponent(input.value) + nsParam())
                            .then(function (r) {
                                if (!r.ok) throw new Error('検索に失敗しました');
                                return r.json();
                            })
                            .then(function (items) {
                                list.innerHTML = '';

                                if (!items.length) {
                                    list.textContent = '見つかりません';
                                    return;
                                }

                                items.forEach(function (e) {
                                    var row = document.createElement('button');
                                    row.type = 'button';
                                    row.className = 'file-row item-row';
                                    row.style.width = '100%';

                                    if (e.registered) {
                                        row.style.opacity = '0.55';
                                    }

                                    var tag = e.kind === 'custom_model_data'
                                        ? (e.floatValue != null
                                            ? 'CMD数値 ' + e.floatValue
                                            : 'CMD')
                                        : 'ItemModel';

                                    var main = document.createElement('div');
                                    main.textContent = '[' + tag + '] ' + e.code +
                                        (e.registered ? '（登録済み）' : '');

                                    var sub = document.createElement('div');
                                    sub.style.cssText = 'font-size:0.8em;color:#666;margin-top:4px;';
                                    sub.textContent = e.source +
                                        (e.modelFile
                                            ? ' ／ モデル: ' + e.modelFile
                                            : e.modelLocation
                                                ? ' ／ モデル: ' + e.modelLocation + '（assets に無し）'
                                                : ' ／ モデル特定不可');

                                    row.appendChild(main);
                                    row.appendChild(sub);

                                    if (currentEntry &&
                                        currentEntry.code === e.code &&
                                        currentEntry.source === e.source) {
                                        row.classList.add('is-selected');
                                    }

                                    row.onclick = function () {
                                        currentEntry = e;

                                        list.querySelectorAll('.item-row').forEach(function (r) {
                                            r.classList.remove('is-selected');
                                        });

                                        row.classList.add('is-selected');
                                        applyEntry(e);
                                    };

                                    list.appendChild(row);
                                });
                            })
                            .catch(function () {
                                list.textContent = '読み込みに失敗しました';
                            });
                    }

                    input.addEventListener('input', function () {
                        clearTimeout(timer);
                        timer = setTimeout(load, 250);
                    });

                    // 対象フォルダを切り替えたときに一覧を読み直すための入口
                    window.reloadItemList = load;
                    load();
                })();

                // ---- AI による表示名の提案 ----
                function suggestName(force) {
                    var nameInput = document.getElementById('itemNameInput');
                    var btn = document.getElementById('aiNameBtn');
                    var status = document.getElementById('aiNameStatus');

                    // 自動提案は、空欄 または 前回のAI提案のままのときだけ上書きする
                    if (!force && nameInput.value.trim() && nameInput.dataset.ai !== '1') return;

                    var code = document.getElementById('codeInput').value.trim();
                    if (!code) {
                        status.textContent = '先に識別コードを選択または入力してください。';
                        return;
                    }

                    var model = document.getElementById('modelFileHidden').value ||
                                document.getElementById('modelLocationHidden').value;
                    var isCmd = document.querySelector('input[name=slotType]:checked').value === 'custom_model_data';
                    var base = isCmd ? document.getElementById('baseItemSelect').value : '';

                    btn.disabled = true;
                    status.textContent = 'AIが名前を考えています...';

                    fetch('/add-existing/suggest-name?code=' + encodeURIComponent(code) +
                          '&model=' + encodeURIComponent(model) +
                          '&baseItem=' + encodeURIComponent(base))
                        .then(function (r) {
                            if (!r.ok) return r.text().then(function (t) { throw new Error(t); });
                            return r.json();
                        })
                        .then(function (d) {
                            nameInput.value = d.name;
                            nameInput.dataset.ai = '1';
                            status.textContent = '';
                        })
                        .catch(function (e) {
                            status.textContent = e.message || 'AIの名前提案に失敗しました。';
                        })
                        .finally(function () { btn.disabled = false; });
                }

                document.getElementById('itemNameInput').addEventListener('input', function () {
                    this.dataset.ai = '';   // 手で編集したらAI提案扱いを解除
                });

                function handleSubmit(e) {
                    var modelFile = document.getElementById('modelFileHidden').value;
                    var modelLocation = document.getElementById('modelLocationHidden').value;
                    var modelId = document.getElementById('modelIdInput').value.trim();

                    if (!modelFile && !modelLocation) {
                        alert('モデルファイルを選択してください。');
                        return false;
                    }

                    if (!modelId) {
                        alert('モデルIDを入力してください。');
                        return false;
                    }

                    document.getElementById('submitBtn').disabled = true;
                    return true;
                }
            </script>
        """.trimIndent()

        return WebHtmlTemplates.renderPage(
            "Add Existing - ResourcePackFlow",
            playerName,
            body
        )
    }
}