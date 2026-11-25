package com.eastcompany.eastsub.gacha.stand

import com.eastcompany.eastsub.gacha.web.form.ModelSettings
import com.eastcompany.eastsub.gacha.item.GachaItemBuilder
import com.eastcompany.eastsub.gacha.model.RegisteredModelData
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.UUID

/** 一覧インベントリの持ち主。クリック時に「これは一覧GUIか」を判定するために使う */
class GachaModelListHolder(
    val parentTextUuid: UUID,
    val author: String,
    val models: List<RegisteredModelData>,
    val page: Int
) : InventoryHolder {
    private lateinit var inv: Inventory

    internal fun bind(inventory: Inventory) {
        inv = inventory
    }

    override fun getInventory(): Inventory = inv
}

/**
 * スタンドのモデル一覧インベントリ (Shift+右クリックで開く)。
 * 表示専用。アイテムは取り出せない。
 */
object GachaModelListGui {

    private const val ROWS = 6
    private const val SIZE = ROWS * 9
    private const val PAGE_SIZE = SIZE - 9   // 最下段はナビゲーション

    const val SLOT_PREV = SIZE - 9           // 45
    const val SLOT_INFO = SIZE - 5           // 49
    const val SLOT_NEXT = SIZE - 1           // 53

    private val legacy = LegacyComponentSerializer.legacySection()

    fun open(
        player: Player,
        parentTextUuid: UUID,
        author: String,
        models: List<RegisteredModelData>,
        page: Int = 0
    ) {
        // レア度の高い順 → モデルID順
        val sorted = models.sortedWith(
            compareBy(
                { ModelSettings.rarityOrder.indexOf(it.rarity).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                { it.modelId }
            )
        )
        val pageCount = maxOf(1, (sorted.size + PAGE_SIZE - 1) / PAGE_SIZE)
        val safePage = page.coerceIn(0, pageCount - 1)

        val holder = GachaModelListHolder(parentTextUuid, author, sorted, safePage)
        val title = legacy.deserialize("§8ガチャ一覧 - $author (${safePage + 1}/$pageCount)")
        val inv = Bukkit.createInventory(holder, SIZE, title)
        holder.bind(inv)

        // モデル
        sorted.drop(safePage * PAGE_SIZE).take(PAGE_SIZE).forEachIndexed { i, model ->
            inv.setItem(i, modelItem(model))
        }

        // 最下段 (ナビゲーション)
        val filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ")
        for (slot in PAGE_SIZE until SIZE) inv.setItem(slot, filler)

        if (safePage > 0) inv.setItem(SLOT_PREV, namedItem(Material.ARROW, "§e« 前のページ"))
        if (safePage < pageCount - 1) inv.setItem(SLOT_NEXT, namedItem(Material.ARROW, "§e次のページ »"))
        inv.setItem(
            SLOT_INFO,
            namedItem(
                Material.BARRIER, "§c閉じる",
                listOf("§7作成者: §b$author", "§7モデル数: §a${sorted.size}個")
            )
        )

        player.openInventory(inv)
    }

    /** 一覧インベントリ内のクリック処理 (ナビゲーションのみ) */
    fun onClick(player: Player, holder: GachaModelListHolder, rawSlot: Int) {
        when (rawSlot) {
            SLOT_PREV -> if (holder.page > 0) {
                playClick(player)
                open(player, holder.parentTextUuid, holder.author, holder.models, holder.page - 1)
            }
            SLOT_NEXT -> {
                val pageCount = maxOf(1, (holder.models.size + PAGE_SIZE - 1) / PAGE_SIZE)
                if (holder.page < pageCount - 1) {
                    playClick(player)
                    open(player, holder.parentTextUuid, holder.author, holder.models, holder.page + 1)
                }
            }
            SLOT_INFO -> player.closeInventory()
        }
    }

    // ------------------------------------------------------------------
    // アイテム生成
    // ------------------------------------------------------------------

    private fun modelItem(model: RegisteredModelData): ItemStack {
        // 「？」表示のモデルは中身を明かさない
        if (model.hiddenFromPreview) {
            return ItemStack(Material.STICK).apply {
                editMeta {
                    it.setItemModel(NamespacedKey.fromString("gacha_system:question_mark")!!)
                    it.setDisplayName("§f？？？")
                    it.lore = listOf("§7レア度: ${model.rarity.color}${model.rarity.displayName}", "§8（当選するまで非公開）")
                }
            }
        }

        val item = GachaItemBuilder.createModelItem(model)
        item.editMeta { meta ->
            // 一覧用のコピーが本物のガチャアイテムとして使われないよう、識別キーを消す
            meta.persistentDataContainer.remove(GachaItemBuilder.GACHA_PDC_KEY)

            val lore = mutableListOf("§7レア度: ${model.rarity.color}${model.rarity.displayName}")
            model.description?.takeIf { it.isNotBlank() }?.let { desc ->
                lore.add("")
                desc.split("\n").forEach { lore.add("§7$it") }
            }
            meta.lore = lore
        }
        return item
    }

    private fun namedItem(material: Material, name: String, lore: List<String> = emptyList()): ItemStack =
        ItemStack(material).apply {
            editMeta {
                it.setDisplayName(name)
                if (lore.isNotEmpty()) it.lore = lore
            }
        }

    private fun playClick(player: Player) {
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.5f)
    }
}