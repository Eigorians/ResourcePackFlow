package com.eastcompany.eastsub.gacha.web.page

import com.eastcompany.eastsub.gacha.item.ItemFilter
import com.eastcompany.eastsub.gacha.web.template.WebHtmlTemplates
import org.bukkit.Material

object PackUploadPage {

    private val cachedBaseItemOptions: String by lazy {
        Material.entries
            .filter { !ItemFilter.isBlocked(it) && !it.isAir }
            .sortedBy { it.name }
            .joinToString("\n") { mat ->
                val key = mat.name.lowercase()
                val selected = if (key == "stick") " selected" else ""
                """<option value="$key"$selected>$key ($key)</option>"""
            }
    }

    fun render(playerName: String): String {
        val baseItemOptions = cachedBaseItemOptions

        val body = """
            <div style="max-width: 700px; background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                <h2 style="margin-top: 0;">リソースパック・モデル登録画面</h2>
                
                <div style="background: #fff3cd; border: 1px solid #ffeeba; color: #856404; padding: 12px; border-radius: 4px; margin-bottom: 20px;">
                    <b>⚠️ 注意事項</b><br>
                    アップロード前に必ず <b>testサーバー</b> で動作を確認してください。
                </div>

                <hr style="border: 0; border-top: 1px solid #eee; margin: 20px 0;">
                
                <form id="uploadForm" action="/upload" method="post" enctype="multipart/form-data" style="display: flex; flex-direction: column; gap: 15px;" onsubmit="handleFormSubmit(event)">
                    <input type="hidden" id="overwriteInput" name="overwrite" value="false">
                    
                    <div>
                        <label><b>モデルID (半角英数字、ハイフン、アンダースコアのみ):</b></label><br>
                        <input type="text" id="modelIdInput" name="modelId" placeholder="空欄の場合、ファイル名が自動で入ります" pattern="^[a-zA-Z0-9_\-]+$" required style="width: 100%; padding: 8px; box-sizing: border-box;">
                        <span style="font-size: 0.8em; color: #666;">※日本語やスペースは使用できません。ファイル名や内部IDとして使用されます。</span>
                    </div>

                    <div>
                        <label><b>表示名:</b></label><br>
                        <input type="text" id="itemNameInput" name="itemName" placeholder="ゲーム内での表示名を入力してください" required style="width: 100%; padding: 8px; box-sizing: border-box;">
                    </div>

                    <div>
                        <label><b>3Dモデル (.json) (任意):</b></label><br>
                        <input type="file" id="modelFileInput" name="modelFile" accept=".json" onchange="onModelFileSelected(this)">
                        <div id="modelFileList" style="margin-top: 5px; display: flex; flex-direction: column; gap: 4px;"></div>
                        <p style="font-size: 0.85em; color: #666; margin: 4px 0 0 0;">※未選択でテクスチャのみ上げた場合、標準2D平面モデルが自動生成されます。</p>
                    </div>

                    <div>
                        <label><b>テクスチャ (.png) (複数可、任意):</b></label><br>
                        <input type="file" id="textureFileInput" name="textureFiles" accept=".png" multiple onchange="onTextureFilesSelected(this)">
                        
                        <div id="textureRequirementWarning" style="color: #dc3545; font-weight: bold; font-size: 0.9em; margin-top: 6px; display: none;">
                            ⚠️ 以下のテクスチャはバニラに存在しないため、PNG画像のアップロードが必要です：<span id="missingTextureList"></span>
                        </div>

                        <!-- 固定サイズの画像プレビューコンテナ -->
                        <div id="texturePreviewContainer" style="display: flex; flex-wrap: wrap; gap: 10px; margin-top: 10px;"></div>

                        <div id="textureFileList" style="margin-top: 5px; display: flex; flex-direction: column; gap: 4px;"></div>
                        <p style="font-size: 0.85em; color: #666; margin: 4px 0 0 0;">※バニラに存在しないカスタムテクスチャのみアップロードしてください。</p>
                    </div>

                    <div>
                        <label><b>スロット選択:</b></label><br>
                        <div style="display: flex; gap: 20px; margin-top: 5px;">
                            <label style="cursor: pointer;"><input type="radio" name="slotType" value="custom_model_data" checked onclick="toggleSlotType('cmd')"> <b>CustomModelData</b></label>
                            <label style="cursor: pointer;"><input type="radio" name="slotType" value="item_model" onclick="toggleSlotType('im')"> <b>ItemModel</b></label>
                        </div>
                        
                        <div id="slotDesc" style="background: #f8f9fa; border-left: 4px solid #007bff; padding: 10px; margin-top: 8px; font-size: 0.9em; color: #333; line-height: 1.4;">
                            <b>【CustomModelData の特徴】</b><br>
                            ・既存のアイテムをベースにします。未導入プレイヤーに安全に表示されます。<br>
                            ・<span style='color: #dc3545; font-weight: bold;'>【重要】選択した1つのベースアイテムに対して1つのカスタムモデルしか適用・割り当てできません。</span>
                        </div>
                    </div>

                    <div id="baseItemContainer">
                        <label><b>ベースアイテム (CustomModelDataの場合は必須):</b></label><br>
                        <select name="baseItem" id="baseItemSelect" required style="padding: 8px; width: 100%;">
                            $baseItemOptions
                        </select>
                    </div>

                    <div>
                        <label><b>説明欄・自由記載:</b></label><br>
                        <textarea name="description" rows="4" style="width: 100%; padding: 8px; box-sizing: border-box;"></textarea>
                    </div>

                    <button type="submit" id="submitBtn" style="padding: 12px; background: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; font-weight: bold;">アップロード＆登録</button>
                </form>
                <br>
                <button style="background: #6c757d; color: white; padding: 8px 15px; border: none; border-radius: 4px; cursor: pointer;" onclick="location.href='/home'">ホームへ戻る</button>
            </div>

            <script>
                const MAX_FILE_SIZE = 10 * 1024 * 1024;
                let requiredTextures = [];
                
                const fileDataTransfers = {
                    modelFileInput: new DataTransfer(),
                    textureFileInput: new DataTransfer()
                };

                function extractModelIdFromFileName(fileName) {
                    const lastDot = fileName.lastIndexOf('.');
                    let name = (lastDot !== -1) ? fileName.substring(0, lastDot) : fileName;
                    name = name.replace(/(_particle|\.particle)$/i, '');
                    return name.toLowerCase().replace(/[^a-zA-Z0-9_\-]/g, '_');
                }

                function checkAndSyncModelId() {
                    const modelDt = fileDataTransfers.modelFileInput;
                    const textureDt = fileDataTransfers.textureFileInput;
                    const modelIdInput = document.getElementById('modelIdInput');

                    if (modelDt.files.length === 0 && textureDt.files.length === 0) {
                        modelIdInput.value = "";
                        return;
                    }

                    if (!modelIdInput.value.trim()) {
                        if (modelDt.files.length > 0) {
                            modelIdInput.value = extractModelIdFromFileName(modelDt.files[0].name);
                        } else if (textureDt.files.length > 0) {
                            modelIdInput.value = extractModelIdFromFileName(textureDt.files[0].name);
                        }
                    }
                }

                function onModelFileSelected(input) {
                    if (!input.files || !input.files[0]) return;
                    const file = input.files[0];
                    
                    const dt = fileDataTransfers.modelFileInput;
                    dt.items.clear();
                    dt.items.add(file);
                    input.files = dt.files;

                    checkAndSyncModelId();

                    const reader = new FileReader();
                    reader.onload = function(e) {
                        try {
                            const jsonText = e.target.result;
                            requiredTextures = extractCustomTexturesFromJson(jsonText);
                            updateTextureWarning();
                        } catch (err) {
                            console.error("JSON parse error", err);
                        }
                    };
                    reader.readAsText(file);

                    renderFileList('modelFileInput', 'modelFileList');
                }

                function extractCustomTexturesFromJson(json) {
                    let textures = [];
                    try {
                        const obj = JSON.parse(json);
                        if (obj && obj.textures && typeof obj.textures === 'object') {
                            for (let key in obj.textures) {
                                let path = obj.textures[key];
                                
                                if (typeof path === 'string' && path !== "particle") {
                                    if (path.startsWith("#") || path.startsWith("block/") || path.startsWith("item/") || path.startsWith("minecraft:") || path.startsWith("entity/")) {
                                        continue;
                                    }
                                    
                                    let lastName = path.substring(path.lastIndexOf('/') + 1);
                                    if (lastName.includes(':')) {
                                        lastName = lastName.substring(lastName.lastIndexOf(':') + 1);
                                    }
                                    
                                    lastName = lastName.replace(/(_particle|\.particle)$/i, '');

                                    if (lastName && !textures.includes(lastName)) {
                                        textures.push(lastName);
                                    }
                                }
                            }
                        }
                    } catch (e) {
                        console.error("JSONのパースに失敗しました:", e);
                    }
                    return textures;
                }

                function onTextureFilesSelected(input) {
                    const dt = fileDataTransfers.textureFileInput;
                    for (let i = 0; i < input.files.length; i++) {
                        dt.items.add(input.files[i]);
                    }
                    input.files = dt.files;

                    checkAndSyncModelId();

                    renderFileList('textureFileInput', 'textureFileList');
                    updateTextureWarning();
                    renderTexturePreviews();
                }

                function renderTexturePreviews() {
                    const container = document.getElementById('texturePreviewContainer');
                    container.innerHTML = "";
                    const dt = fileDataTransfers.textureFileInput;

                    for (let i = 0; i < dt.files.length; i++) {
                        const file = dt.files[i];
                        if (!file.type.startsWith('image/')) continue;

                        const wrapper = document.createElement('div');
                        wrapper.style.cssText = "width: 80px; text-align: center; background: #f8f9fa; border: 1px solid #dee2e6; padding: 6px; border-radius: 6px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);";

                        const img = document.createElement('img');
                        img.style.cssText = "width: 64px; height: 64px; object-fit: contain; image-rendering: pixelated; border: 1px solid #ccc; background: repeating-conic-gradient(#eee 0% 25%, #fff 0% 50%) 50% / 16px 16px;";
                        
                        const reader = new FileReader();
                        reader.onload = function(e) {
                            img.src = e.target.result;
                        };
                        reader.readAsDataURL(file);

                        const nameLabel = document.createElement('div');
                        nameLabel.style.cssText = "font-size: 0.75em; color: #495057; margin-top: 4px; text-overflow: ellipsis; overflow: hidden; white-space: nowrap;";
                        nameLabel.textContent = file.name;

                        wrapper.appendChild(img);
                        wrapper.appendChild(nameLabel);
                        container.appendChild(wrapper);
                    }
                }

                function updateTextureWarning() {
                    const warningDiv = document.getElementById('textureRequirementWarning');
                    const spanList = document.getElementById('missingTextureList');
                    
                    const dt = fileDataTransfers.textureFileInput;
                    let uploadedNames = [];
                    for (let i = 0; i < dt.files.length; i++) {
                        let fname = dt.files[i].name;
                        let cleanName = fname.substring(0, fname.lastIndexOf('.')).replace(/(_particle|\.particle)$/i, '').toLowerCase();
                        uploadedNames.push(cleanName);
                    }

                    let missing = requiredTextures.filter(req => !uploadedNames.includes(req.toLowerCase()));

                    if (missing.length > 0) {
                        spanList.textContent = missing.join(", ");
                        warningDiv.style.display = 'block';
                    } else {
                        warningDiv.style.display = 'none';
                    }
                }

                function renderFileList(inputId, listId) {
                    const listContainer = document.getElementById(listId);
                    listContainer.innerHTML = "";
                    const dt = fileDataTransfers[inputId];

                    for (let i = 0; i < dt.files.length; i++) {
                        const file = dt.files[i];
                        const itemDiv = document.createElement('div');
                        itemDiv.style.cssText = "display: flex; align-items: center; justify-content: space-between; background: #f1f3f5; padding: 4px 8px; border-radius: 4px; font-size: 0.9em;";
                        
                        const nameSpan = document.createElement('span');
                        nameSpan.textContent = file.name + " (" + (file.size / 1024).toFixed(1) + " KB)";
                        
                        const removeBtn = document.createElement('button');
                        removeBtn.type = "button";
                        removeBtn.textContent = "×";
                        removeBtn.style.cssText = "background: #dc3545; color: white; border: none; border-radius: 50%; width: 20px; height: 20px; cursor: pointer; font-weight: bold; line-height: 18px; text-align: center; padding: 0;";
                        removeBtn.onclick = function() {
                            dt.items.remove(i);
                            document.getElementById(inputId).files = dt.files;
                            renderFileList(inputId, listId);
                            
                            if (inputId === 'textureFileInput') {
                                updateTextureWarning();
                                renderTexturePreviews();
                            } else if (inputId === 'modelFileInput') {
                                requiredTextures = [];
                                updateTextureWarning();
                            }

                            checkAndSyncModelId();
                        };

                        itemDiv.appendChild(nameSpan);
                        itemDiv.appendChild(removeBtn);
                        listContainer.appendChild(itemDiv);
                    }
                }

                // ★ 送信前に既存判定を行い、上書き警告確認を出すメインハンドラ
                async function handleFormSubmit(e) {
                    e.preventDefault();

                    const modelIdInput = document.getElementById('modelIdInput').value.trim();
                    const regex = /^[a-zA-Z0-9_\-]+$/;
                    if (!regex.test(modelIdInput)) {
                        alert("モデルIDには半角英数字、アンダースコア(_)、ハイフン(-)のみ使用できます。");
                        return;
                    }

                    const modelInput = document.getElementById('modelFileInput');
                    const textureInput = document.getElementById('textureFileInput');

                    const hasModel = modelInput.files && modelInput.files.length > 0;
                    const hasTexture = textureInput.files && textureInput.files.length > 0;

                    if (!hasModel && !hasTexture) {
                        alert("3Dモデル (.json) または テクスチャ (.png) のいずれかをアップロードしてください。");
                        return;
                    }

                    const submitBtn = document.getElementById('submitBtn');
                    submitBtn.disabled = true;

                    try {
                        // 1. モデルIDが既に登録されているかバックエンドへ事前確認
                        const checkRes = await fetch('/upload/check-exists?modelId=' + encodeURIComponent(modelIdInput));
                        if (checkRes.ok) {
                            const data = await checkRes.json();
                            
                            if (data.exists) {
                                const confirmMsg = "⚠️ モデルID '" + modelIdInput + "' は既に存在します。\n既存のモデル・テクスチャ・設定データを削除して上書きしますか？";
                                if (!confirm(confirmMsg)) {
                                    submitBtn.disabled = false;
                                    return; // キャンセルされた場合は送信中止
                                }
                                // 上書き同意フラグをセット
                                document.getElementById('overwriteInput').value = "true";
                            }
                        }

                        // 2. フォームの正式送信
                        document.getElementById('uploadForm').submit();

                    } catch (err) {
                        alert("通信エラーが発生しました: " + err);
                        submitBtn.disabled = false;
                    }
                }

                function toggleSlotType(type) {
                    const select = document.getElementById('baseItemSelect');
                    const container = document.getElementById('baseItemContainer');
                    const desc = document.getElementById('slotDesc');
                    
                    if (type === 'cmd') {
                        container.style.display = 'block';
                        select.required = true;
                        desc.innerHTML = "<b>【CustomModelData の特徴】</b><br>・既存のアイテムをベースにします。リソースパック未導入プレイヤーに安全に表示されます。<br>・<span style='color: #dc3545; font-weight: bold;'>【重要】選択した1つのベースアイテムに対して1つのカスタムモデルしか適用・割り当てできません。</span>";
                    } else {
                        container.style.display = 'none';
                        select.required = false;
                        select.value = '';
                        desc.innerHTML = "<b>【ItemModel の特徴】</b><br>・独自のモデルを直接指定します。<br>・<span style='color: #dc3545; font-weight: bold;'>未導入プレイヤーには紫と黒になります。</span>";
                    }
                }
            </script>
        """.trimIndent()

        return WebHtmlTemplates.renderPage("Upload - ResourcePackFlow", playerName, body)
    }
}