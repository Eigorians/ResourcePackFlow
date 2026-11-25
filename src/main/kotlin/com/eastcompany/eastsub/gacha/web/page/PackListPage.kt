package com.eastcompany.eastsub.gacha.web.page

import com.eastcompany.eastsub.gacha.api.VaultHook
import com.eastcompany.eastsub.gacha.model.ModelRarity
import com.eastcompany.eastsub.gacha.model.RegisteredModelData
import com.eastcompany.eastsub.gacha.web.template.WebHtmlTemplates

object PackListPage {

    fun render(
        playerName: String,
        models: List<RegisteredModelData>,
        savedWeights: Map<String, Int> = emptyMap(),
        currentPrice: Int = VaultHook.GachaConfig.DEFAULT_COST
    ): String {
        val groupedModels = models.groupBy { it.rarity }

        val defaultRarities = listOf(
            ModelRarity.S, ModelRarity.A, ModelRarity.B,
            ModelRarity.C, ModelRarity.D, ModelRarity.E,
            ModelRarity.F, ModelRarity.MISS,
            ModelRarity.UNCLASSIFIED
        )

        val defaultWeights = mapOf(
            "MISS" to 50,
            "F" to 50,
            "S" to 0, "A" to 0, "B" to 0, "C" to 0, "D" to 0, "E" to 0,
            "UNCLASSIFIED" to 0
        )

        val tierSectionsHtml = defaultRarities.joinToString("\n") { rarity ->
            val initWeight = savedWeights[rarity.name] ?: defaultWeights[rarity.name] ?: 0
            renderTierSection(rarity.name, groupedModels[rarity] ?: emptyList(), initWeight)
        }

        val body = """
            <style>
                .list-card-root {
                    max-width: 1100px !important;
                    width: 95% !important;
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

                .list-card-root * {
                    box-sizing: border-box !important;
                }

                .list-header-row {
                    display: flex !important;
                    justify-content: space-between !important;
                    align-items: center !important;
                    border-bottom: 2px solid #f1f3f5 !important;
                    padding-bottom: 14px !important;
                    margin-bottom: 16px !important;
                    flex-wrap: wrap !important;
                    gap: 12px !important;
                }

                .list-title-text {
                    margin: 0 !important;
                    font-size: 1.4rem !important;
                    font-weight: bold !important;
                    color: #212529 !important;
                }

                .price-setting-panel {
                    display: flex !important;
                    align-items: center !important;
                    gap: 12px !important;
                    padding: 12px 16px !important;
                    background: #f8f9fa !important;
                    border: 1px solid #e9ecef !important;
                    border-left: 4px solid #28a745 !important;
                    border-radius: 6px !important;
                    margin-bottom: 20px !important;
                }

                .price-input-style {
                    width: 110px !important;
                    height: 36px !important;
                    padding: 0 10px !important;
                    border: 1px solid #ced4da !important;
                    border-radius: 4px !important;
                    font-size: 1rem !important;
                    font-weight: bold !important;
                    color: #28a745 !important;
                    text-align: right !important;
                }

                .total-badge {
                    display: inline-flex !important;
                    align-items: center !important;
                    padding: 6px 14px !important;
                    border-radius: 20px !important;
                    font-size: 0.9rem !important;
                    font-weight: bold !important;
                    background: #e7f5ff !important;
                    color: #1971c2 !important;
                    border: 1px solid #a5d8ff !important;
                }

                .save-status-badge {
                    display: inline-flex !important;
                    align-items: center !important;
                    padding: 4px 10px !important;
                    border-radius: 4px !important;
                    font-size: 0.8rem !important;
                    font-weight: bold !important;
                    margin-left: 10px !important;
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

                .btn-list-action {
                    display: inline-flex !important;
                    align-items: center !important;
                    justify-content: center !important;
                    height: 38px !important;
                    padding: 0 16px !important;
                    border: none !important;
                    border-radius: 4px !important;
                    font-size: 0.88rem !important;
                    font-weight: bold !important;
                    cursor: pointer !important;
                    white-space: nowrap !important;
                    background: #007bff !important;
                    color: white !important;
                    transition: opacity 0.2s !important;
                    text-decoration: none !important;
                }

                .btn-list-action:hover {
                    opacity: 0.9 !important;
                }

                .btn-add-tier {
                    background: #28a745 !important;
                }

                .btn-import {
                    background: #6f42c1 !important;
                }

                .btn-rebuild {
                    background: #e67e22 !important;
                }

                .tier-section {
                    display: flex !important;
                    margin-bottom: 12px !important;
                    border: 1px solid #dee2e6 !important;
                    border-radius: 6px !important;
                    overflow: hidden !important;
                    background: #f8f9fa !important;
                    min-height: 60px !important;
                    transition: all 0.2s !important;
                }

                .tier-section.hidden-tier {
                    display: none !important;
                }

                .tier-label {
                    width: 90px !important;
                    min-width: 90px !important;
                    display: flex !important;
                    align-items: center !important;
                    justify-content: center !important;
                    font-weight: bold !important;
                    font-size: 1.05rem !important;
                    color: #fff !important;
                    background: #6c757d !important;
                    user-select: none !important;
                }

                .tier-label[data-rarity="MISS"] { background: #111115 !important; color: #ff6b6b !important; }
                .tier-label[data-rarity="S"] { background: #ffc107 !important; color: #212529 !important; }
                .tier-label[data-rarity="A"] { background: #dc3545 !important; }
                .tier-label[data-rarity="B"] { background: #007bff !important; }
                .tier-label[data-rarity="C"] { background: #28a745 !important; }
                .tier-label[data-rarity="D"] { background: #6f42c1 !important; }
                .tier-label[data-rarity="E"] { background: #fd7e14 !important; }
                .tier-label[data-rarity="F"] { background: #6c757d !important; }
                .tier-label[data-rarity="UNCLASSIFIED"] { background: #adb5bd !important; font-size: 0.8rem !important; }

                .tier-weight-control {
                    width: 200px !important;
                    min-width: 200px !important;
                    padding: 8px 12px !important;
                    display: flex !important;
                    align-items: center !important;
                    justify-content: space-between !important;
                    background: #ffffff !important;
                    border-right: 1px solid #dee2e6 !important;
                }

                .weight-input-group {
                    display: flex !important;
                    flex-direction: column !important;
                    gap: 4px !important;
                }

                .weight-input {
                    width: 75px !important;
                    height: 28px !important;
                    padding: 0 6px !important;
                    border: 1px solid #ced4da !important;
                    border-radius: 4px !important;
                    font-size: 0.88rem !important;
                    font-weight: bold !important;
                    text-align: right !important;
                }

                .weight-percent-label {
                    font-size: 0.8rem !important;
                    font-weight: bold !important;
                    color: #28a745 !important;
                }

                .btn-delete-tier {
                    background: #dc3545 !important;
                    color: white !important;
                    border: none !important;
                    border-radius: 4px !important;
                    width: 26px !important;
                    height: 26px !important;
                    cursor: pointer !important;
                    font-size: 0.85rem !important;
                    display: inline-flex !important;
                    align-items: center !important;
                    justify-content: center !important;
                    padding: 0 !important;
                }

                .btn-delete-tier:hover {
                    background: #c82333 !important;
                }

                .tier-dropzone {
                    flex: 1 !important;
                    display: flex !important;
                    flex-wrap: nowrap !important;
                    overflow-x: auto !important;
                    overflow-y: hidden !important;
                    gap: 8px !important;
                    padding: 8px 12px !important;
                    align-items: center !important;
                    transition: background 0.2s !important;
                    scrollbar-width: thin;
                }

                .tier-dropzone::-webkit-scrollbar {
                    height: 6px;
                }
                .tier-dropzone::-webkit-scrollbar-thumb {
                    background: #ced4da;
                    border-radius: 3px;
                }
                .tier-dropzone::-webkit-scrollbar-thumb:hover {
                    background: #adb5bd;
                }

                .tier-dropzone.drag-over {
                    background: #e2e6ea !important;
                    border: 2px dashed #007bff !important;
                }

                .tier-dropzone.disabled-dropzone {
                    background: #1e1e24 !important;
                    color: #8a8a9e !important;
                    cursor: not-allowed !important;
                }

                .model-chip {
                    display: inline-flex !important;
                    align-items: center !important;
                    gap: 8px !important;
                    background: #ffffff !important;
                    border: 1px solid #ced4da !important;
                    border-radius: 20px !important;
                    padding: 4px 12px 4px 6px !important;
                    cursor: grab !important;
                    box-shadow: 0 1px 3px rgba(0,0,0,0.05) !important;
                    transition: transform 0.15s, box-shadow 0.15s, border-color 0.15s !important;
                    user-select: none !important;
                    height: 36px !important;
                    flex-shrink: 0 !important;
                }

                .model-chip:active {
                    cursor: grabbing !important;
                }

                .model-chip:hover {
                    transform: translateY(-1px) !important;
                    box-shadow: 0 3px 6px rgba(0,0,0,0.12) !important;
                    border-color: #007bff !important;
                }

                .model-chip.dragging {
                    opacity: 0.3 !important;
                }

                .model-chip-thumb {
                    width: 24px !important;
                    height: 24px !important;
                    object-fit: contain !important;
                    image-rendering: pixelated !important;
                    border: 1px solid #ddd !important;
                    border-radius: 50% !important;
                    background: repeating-conic-gradient(#eee 0% 25%, #fff 0% 50%) 50% / 8px 8px !important;
                    flex-shrink: 0 !important;
                }

                .model-chip-title {
                    font-size: 0.85rem !important;
                    font-weight: bold !important;
                    color: #212529 !important;
                    white-space: nowrap !important;
                }

                .modal-overlay {
                    position: fixed; top: 0; left: 0; width: 100%; height: 100%;
                    background: rgba(0,0,0,0.4); display: none; justify-content: center; align-items: center; z-index: 1000;
                }
                .modal-box {
                    background: #fff; padding: 20px 24px; border-radius: 8px; width: 320px; box-shadow: 0 4px 12px rgba(0,0,0,0.15);
                }
            </style>

            <div class="list-card-root">
                <div class="list-header-row">
                    <div>
                        <h2 class="list-title-text" style="display: inline-block; margin-right: 8px !important;">モデル一覧・確率設定</h2>
                        <span id="totalWeightDisplay" class="total-badge">総重み: 0</span>
                        <span id="saveStatusBadge" class="save-status-badge">保存完了</span>
                    </div>
                    <div style="display: flex; gap: 8px; flex-wrap: wrap;">
                        <button type="button" class="btn-list-action" style="background: #17a2b8;" onclick="location.href='/upload'">+ モデル追加</button>
                        <button type="button" class="btn-list-action btn-import" onclick="location.href='/add-existing'">+ モデル取り込み</button>
                        <button type="button" class="btn-list-action btn-add-tier" onclick="openAddTierModal()">+ レア度を追加</button>
                        <button type="button" class="btn-list-action btn-rebuild" onclick="rebuildResourcePack()">リソースパックを更新</button>
                        <button type="button" class="btn-list-action" style="background: #fd7e14;" onclick="location.href='/bulk-edit'">一括設定変更</button>
                    </div>
                </div>

                <!-- プレイヤー単位のガチャ金額設定パネル -->
                <div class="price-setting-panel">
                    <label style="font-weight: bold; font-size: 0.95rem; color: #212529; white-space: nowrap;">
                        💰 あなたのガチャ1回あたりの金額 (最低 ${VaultHook.GachaConfig.DEFAULT_COST} 以上):
                    </label>
                    <input type="number" id="globalPriceInput" class="price-input-style" value="$currentPrice" min="10" step="1" oninput="onPriceInputChange()">
                    <span style="font-size: 0.85rem; color: #6c757d;"> Pen</span>
                </div>

                <div id="tierList">
                    $tierSectionsHtml
                </div>

                <br>
                <button style="background:#6c757d; color:white; padding:8px 15px; border:none; border-radius:4px; cursor:pointer;"
                    onclick="location.href='/home'">
                    ホームへ戻る
                </button>
            </div>

            <!-- レア度追加モーダル -->
            <div id="addTierModal" class="modal-overlay">
                <div class="modal-box">
                    <h3 style="margin-top: 0; font-size: 1.1rem; color: #212529;">追加するレア度を選択</h3>
                    <div style="margin: 16px 0;">
                        <select id="tierSelectDropdown" style="width: 100%; padding: 8px; font-size: 0.95rem; border: 1px solid #ced4da; border-radius: 4px;"></select>
                    </div>
                    <div style="display: flex; justify-content: flex-end; gap: 8px;">
                        <button type="button" style="padding: 6px 12px; background: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;" onclick="closeAddTierModal()">キャンセル</button>
                        <button type="button" style="padding: 6px 16px; background: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; font-weight: bold;" onclick="confirmAddTier()">追加</button>
                    </div>
                </div>
            </div>

            <script>
                let draggedCard = null;
                let autoSaveTimer = null;
                let autoSavePriceTimer = null;

                // アラートや確認なしで直接リソースパックを更新
                function rebuildResourcePack() {
                    fetch('/list/rebuild-pack', {
                        method: 'POST'
                    }).then(res => {
                        if (!res.ok) {
                            console.error("リソースパックの更新に失敗しました。");
                        }
                    }).catch(err => {
                        console.error("通信エラー", err);
                    });
                }

                // ガチャ金額のリアルタイム変更＆オートセーブ
                function onPriceInputChange() {
                    const priceInput = document.getElementById('globalPriceInput');
                    let val = parseInt(priceInput.value, 10);
                    
                    clearTimeout(autoSavePriceTimer);
                    autoSavePriceTimer = setTimeout(() => {
                        if (isNaN(val) || val < 10) {
                            val = 10;
                            priceInput.value = 10;
                        }
                        autoSavePrice(val);
                    }, 500);
                }

                function autoSavePrice(priceVal) {
                    showSaveStatus('saving');

                    const params = new URLSearchParams();
                    params.append('price', priceVal);

                    fetch('/list/update-price', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                        body: params.toString()
                    }).then(res => {
                        if (res.ok) {
                            showSaveStatus('saved');
                        } else {
                            console.error("金額の自動保存エラー");
                        }
                    }).catch(err => console.error("通信エラー", err));
                }

                function onWeightInputChange() {
                    calculatePercentages();
                    
                    clearTimeout(autoSaveTimer);
                    autoSaveTimer = setTimeout(() => {
                        autoSaveWeights();
                    }, 400);
                }

                function calculatePercentages() {
                    const visibleSections = document.querySelectorAll('.tier-section:not(.hidden-tier)');
                    let totalWeight = 0;

                    visibleSections.forEach(section => {
                        if (section.dataset.rarity !== 'UNCLASSIFIED') {
                            const input = section.querySelector('.weight-input');
                            if (input) {
                                totalWeight += parseInt(input.value) || 0;
                            }
                        }
                    });

                    document.getElementById('totalWeightDisplay').textContent = '総重み: ' + totalWeight;

                    visibleSections.forEach(section => {
                        const rarity = section.dataset.rarity;
                        if (rarity !== 'UNCLASSIFIED') {
                            const input = section.querySelector('.weight-input');
                            if (input) {
                                const val = parseInt(input.value) || 0;
                                const percentSpan = document.getElementById('percent-' + rarity);

                                if (totalWeight > 0) {
                                    const percent = ((val / totalWeight) * 100).toFixed(1);
                                    percentSpan.textContent = percent + '%';
                                } else {
                                    percentSpan.textContent = '0.0%';
                                }
                            }
                        }
                    });
                }

                function autoSaveWeights() {
                    showSaveStatus('saving');

                    const inputs = document.querySelectorAll('.weight-input');
                    const weightsMap = {};
                    inputs.forEach(input => {
                        const section = input.closest('.tier-section');
                        if (section && !section.classList.contains('hidden-tier')) {
                            weightsMap[input.dataset.rarity] = parseInt(input.value) || 0;
                        } else {
                            weightsMap[input.dataset.rarity] = 0;
                        }
                    });

                    fetch('/list/update-weights', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(weightsMap)
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

                function deleteTier(rarity) {
                    if (rarity === 'UNCLASSIFIED') return;

                    const labelName = (rarity === 'MISS') ? '外れ' : rarity;
                    if (!confirm(labelName + " レア度を削除しますか？\n（重みは0になり、含まれていたアイテムはすべて未分類へ移動します）")) return;

                    const section = document.getElementById('section-' + rarity);
                    if (!section) return;

                    const input = section.querySelector('.weight-input');
                    if (input) input.value = 0;

                    const dropzone = section.querySelector('.tier-dropzone');
                    const unclassifiedZone = document.querySelector('.tier-dropzone[data-rarity="UNCLASSIFIED"]');
                    
                    if (dropzone && unclassifiedZone) {
                        const chips = Array.from(dropzone.querySelectorAll('.model-chip'));
                        chips.forEach(chip => {
                            unclassifiedZone.appendChild(chip);
                            updateModelRarity(chip.dataset.modelId, 'UNCLASSIFIED');
                        });
                    }

                    section.classList.add('hidden-tier');
                    onWeightInputChange();
                }

                function openAddTierModal() {
                    const hiddenSections = Array.from(document.querySelectorAll('.tier-section.hidden-tier'));
                    if (hiddenSections.length === 0) {
                        alert("追加可能な非表示レア度はありません。");
                        return;
                    }

                    const dropdown = document.getElementById('tierSelectDropdown');
                    dropdown.innerHTML = "";

                    hiddenSections.forEach(s => {
                        const r = s.dataset.rarity;
                        const dispName = (r === 'MISS') ? '外れ (MISS)' : r;
                        const opt = document.createElement('option');
                        opt.value = r;
                        opt.textContent = dispName;
                        dropdown.appendChild(opt);
                    });

                    document.getElementById('addTierModal').style.display = 'flex';
                }

                function closeAddTierModal() {
                    document.getElementById('addTierModal').style.display = 'none';
                }

                function confirmAddTier() {
                    const dropdown = document.getElementById('tierSelectDropdown');
                    const targetRarity = dropdown.value;
                    
                    if (targetRarity) {
                        const targetSection = document.getElementById('section-' + targetRarity);
                        if (targetSection) {
                            targetSection.classList.remove('hidden-tier');
                            onWeightInputChange();
                        }
                    }
                    closeAddTierModal();
                }

                window.addEventListener('DOMContentLoaded', calculatePercentages);

                document.addEventListener('dragstart', (e) => {
                    const chip = e.target.closest('.model-chip');
                    if (chip) {
                        draggedCard = chip;
                        chip.classList.add('dragging');
                        e.dataTransfer.setData('text/plain', chip.dataset.modelId);
                    }
                });

                document.addEventListener('dragend', (e) => {
                    const chip = e.target.closest('.model-chip');
                    if (chip) {
                        chip.classList.remove('dragging');
                        draggedCard = null;
                    }
                });

                function setupDropzone(dropzone) {
                    dropzone.addEventListener('dragover', (e) => {
                        if (dropzone.dataset.rarity === 'MISS') return;
                        
                        e.preventDefault();
                        dropzone.classList.add('drag-over');
                    });

                    dropzone.addEventListener('dragleave', () => {
                        dropzone.classList.remove('drag-over');
                    });

                    dropzone.addEventListener('drop', (e) => {
                        if (dropzone.dataset.rarity === 'MISS') return;

                        e.preventDefault();
                        dropzone.classList.remove('drag-over');

                        if (draggedCard) {
                            const targetTier = dropzone.dataset.rarity;
                            const modelId = draggedCard.dataset.modelId;

                            dropzone.appendChild(draggedCard);
                            updateModelRarity(modelId, targetTier);
                        }
                    });
                }

                document.querySelectorAll('.tier-dropzone').forEach(setupDropzone);

                function updateModelRarity(modelId, newRarity) {
                    const params = new URLSearchParams();
                    params.append('modelId', modelId);
                    params.append('rarity', newRarity);

                    fetch('/list/update-rarity', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                        body: params.toString()
                    }).then(res => {
                        if (!res.ok) {
                            alert("レアリティの更新に失敗しました。");
                            location.reload();
                        }
                    }).catch(() => {
                        alert("通信エラーが発生しました。");
                        location.reload();
                    });
                }
            </script>
        """.trimIndent()

        return WebHtmlTemplates.renderPage("Model List - ResourcePackFlow", playerName, body)
    }

    private fun renderTierSection(rarityName: String, models: List<RegisteredModelData>, initialWeight: Int): String {
        val labelDisplayName = when (rarityName) {
            "MISS" -> "外れ"
            "UNCLASSIFIED" -> "未分類"
            else -> rarityName
        }

        val isInitiallyHidden = initialWeight == 0 && rarityName != "MISS" && rarityName != "F" && rarityName != "UNCLASSIFIED"
        val hiddenClass = if (isInitiallyHidden) " hidden-tier" else ""

        val chipsHtml = models.joinToString("\n") { model ->
            val thumbPath = model.texturePaths.firstOrNull() ?: ""
            val thumbSrc = if (thumbPath.isNotBlank()) "/edit/texture?path=$thumbPath" else ""

            """
            <div class="model-chip" draggable="true" data-model-id="${model.modelId}" onclick="location.href='/edit?modelId=${model.modelId}'" title="${model.itemName} (${model.modelId}) を編集">
                ${if (thumbSrc.isNotBlank()) "<img src=\"$thumbSrc\" alt=\"\" class=\"model-chip-thumb\">" else ""}
                <span class="model-chip-title">${model.itemName}</span>
            </div>
            """.trimIndent()
        }

        val weightControlHtml = if (rarityName == "UNCLASSIFIED") {
            """
            <div class="tier-weight-control" style="justify-content: center; background: #f1f3f5;">
                <span style="font-size: 0.8rem; font-weight: bold; color: #6c757d;">未分類アイテム保管</span>
            </div>
            """.trimIndent()
        } else {
            """
            <div class="tier-weight-control">
                <div class="weight-input-group">
                    <div style="display: flex; align-items: center; gap: 4px;">
                        <span style="font-size: 0.78rem; font-weight: bold; color: #495057;">重み:</span>
                        <input type="number" class="weight-input" data-rarity="$rarityName" min="0" value="$initialWeight" oninput="onWeightInputChange()">
                    </div>
                    <div class="weight-percent-label">
                        確率: <span id="percent-$rarityName">0.0%</span>
                    </div>
                </div>
                <button type="button" class="btn-delete-tier" title="レア度を削除" onclick="deleteTier('$rarityName')">✕</button>
            </div>
            """.trimIndent()
        }

        val dropzoneExtraClass = if (rarityName == "MISS") " disabled-dropzone" else ""
        val dropzoneContent = if (rarityName == "MISS") {
            """<span style="font-size: 0.8rem; color: #8a8a9e; font-style: italic;">※ 外れ枠にはモデルを割り当てできません</span>"""
        } else {
            chipsHtml
        }

        return """
            <div id="section-$rarityName" class="tier-section$hiddenClass" data-rarity="$rarityName">
                <div class="tier-label" data-rarity="$rarityName">$labelDisplayName</div>
                $weightControlHtml
                <div class="tier-dropzone$dropzoneExtraClass" data-rarity="$rarityName">
                    $dropzoneContent
                </div>
            </div>
        """.trimIndent()
    }
}