package com.eastcompany.eastsub.gacha.item

import com.eastcompany.eastsub.gacha.model.RegisteredModelData
import org.bukkit.Material

class ItemFilter {

    companion object {

        private val ALLOWED_ITEMS = setOf(
            Material.APPLE,
            Material.ARROW,
            Material.BAKED_POTATO,
            Material.BEEF,
            Material.BEETROOT,
            Material.BEETROOT_SEEDS,
            Material.BEETROOT_SOUP,
            Material.BONE,
            Material.BOOK,
            Material.BOW,
            Material.BOWL,
            Material.BREAD,
            Material.BRICK,
            Material.BRUSH,
            Material.BUNDLE,
            Material.CARROT,
            Material.CARROT_ON_A_STICK,
            Material.CHARCOAL,
            Material.CHAINMAIL_BOOTS,
            Material.CHAINMAIL_CHESTPLATE,
            Material.CHAINMAIL_HELMET,
            Material.CHAINMAIL_LEGGINGS,
            Material.CHARCOAL,
            Material.CHICKEN,
            Material.CLAY_BALL,
            Material.CLOCK,
            Material.COAL,
            Material.COCOA_BEANS,
            Material.COD,
            Material.COMPASS,
            Material.COOKED_BEEF,
            Material.COOKED_CHICKEN,
            Material.COOKED_COD,
            Material.COOKED_MUTTON,
            Material.COOKED_PORKCHOP,
            Material.COOKED_RABBIT,
            Material.COOKED_SALMON,
            Material.COOKIE,
            Material.CROSSBOW,
            Material.DRIED_KELP,
            Material.FEATHER,
            Material.FERMENTED_SPIDER_EYE,
            Material.FIREWORK_STAR,
            Material.FISHING_ROD,
            Material.FLINT,
            Material.FLINT_AND_STEEL,
            Material.GLASS_BOTTLE,
            Material.GLISTERING_MELON_SLICE,
            Material.GLOW_BERRIES,
            Material.GLOW_INK_SAC,
            Material.GLOWSTONE_DUST,
            Material.GUNPOWDER,
            Material.HONEY_BOTTLE,
            Material.HONEYCOMB,
            Material.INK_SAC,
            Material.IRON_BOOTS,
            Material.IRON_CHESTPLATE,
            Material.IRON_HELMET,
            Material.IRON_LEGGINGS,
            Material.LAPIS_LAZULI,
            Material.LEATHER,
            Material.MAP,
            Material.MELON_SEEDS,
            Material.MELON_SLICE,
            Material.MUSHROOM_STEW,
            Material.MUTTON,
            Material.NETHER_BRICK,
            Material.PAINTING,
            Material.PAPER,
            Material.PHANTOM_MEMBRANE,
            Material.POISONOUS_POTATO,
            Material.PORKCHOP,
            Material.POTATO,
            Material.POTION,
            Material.PRISMARINE_CRYSTALS,
            Material.PRISMARINE_SHARD,
            Material.PUFFERFISH,
            Material.PUMPKIN_PIE,
            Material.PUMPKIN_SEEDS,
            Material.QUARTZ,
            Material.RABBIT,
            Material.RABBIT_FOOT,
            Material.RABBIT_HIDE,
            Material.RABBIT_STEW,
            Material.ROTTEN_FLESH,
            Material.SALMON,
            Material.SHEARS,
            Material.SLIME_BALL,
            Material.SPECTRAL_ARROW,
            Material.SPIDER_EYE,
            Material.SPYGLASS,
            Material.STICK,
            Material.STONE_AXE,
            Material.STONE_HOE,
            Material.STONE_PICKAXE,
            Material.STONE_SHOVEL,
            Material.STONE_SPEAR,
            Material.STONE_SWORD,
            Material.SUGAR,
            Material.SUSPICIOUS_STEW,
            Material.SWEET_BERRIES,
            Material.TIPPED_ARROW,
            Material.TROPICAL_FISH,
            Material.WARPED_FUNGUS_ON_A_STICK,
            Material.WHEAT_SEEDS,
            Material.WOODEN_AXE,
            Material.WOODEN_HOE,
            Material.WOODEN_PICKAXE,
            Material.WOODEN_SHOVEL,
            Material.WOODEN_SPEAR,
            Material.WOODEN_SWORD,
            Material.WRITABLE_BOOK,
            Material.WRITTEN_BOOK,
            Material.SNOWBALL,
            Material.EGG,
            Material.SHIELD,
        )

        val translations: Map<String, String>
            get() = itemTranslations

        private val itemTranslations = mapOf(
            "APPLE" to "りんご",
            "ARROW" to "矢",
            "BAKED_POTATO" to "ベイクドポテト",
            "BEEF" to "生の牛肉",
            "BEETROOT" to "ビートルート",
            "BEETROOT_SEEDS" to "ビートルートの種",
            "BEETROOT_SOUP" to "ビートルートスープ",
            "BONE" to "骨",
            "BOOK" to "本",
            "BOW" to "弓",
            "BOWL" to "ボウル",
            "BREAD" to "パン",
            "BRUSH" to "ブラシ",
            "BUNDLE" to "バンドル",
            "CARROT" to "ニンジン",
            "CARROT_ON_A_STICK" to "ニンジン付きの棒",
            "CHICKEN" to "生の鶏肉",
            "CLOCK" to "時計",
            "COCOA_BEANS" to "カカオ豆",
            "COD" to "生のタラ",
            "COMPASS" to "コンパス",
            "COOKED_BEEF" to "ステーキ",
            "COOKED_CHICKEN" to "焼き鳥",
            "COOKED_COD" to "焼きタラ",
            "COOKED_MUTTON" to "焼き羊肉",
            "COOKED_PORKCHOP" to "焼き豚肉",
            "COOKED_RABBIT" to "焼きウサギ肉",
            "COOKED_SALMON" to "焼きサケ",
            "COOKIE" to "クッキー",
            "CROSSBOW" to "クロスボウ",
            "DRIED_KELP" to "乾燥コンブ",
            "FEATHER" to "羽根",
            "FISHING_ROD" to "釣り竿",
            "FLINT_AND_STEEL" to "火打石と打ち金",
            "GLASS_BOTTLE" to "ガラス瓶",
            "IRON_BOOTS" to "鉄のブーツ",
            "IRON_CHESTPLATE" to "鉄のチェストプレート",
            "IRON_HELMET" to "鉄のヘルメット",
            "IRON_LEGGINGS" to "鉄のレギンス",
            "CHAINMAIL_BOOTS" to "チェーンブーツ",
            "CHAINMAIL_CHESTPLATE" to "チェーンチェストプレート",
            "CHAINMAIL_HELMET" to "チェーンヘルメット",
            "CHAINMAIL_LEGGINGS" to "チェーンレギンス",
            "ITEM_FRAME" to "額縁",
            "MUSHROOM_STEW" to "キノコシチュー",
            "MUTTON" to "生の羊肉",
            "PAPER" to "紙",
            "PORKCHOP" to "生の豚肉",
            "POTATO" to "ジャガイモ",
            "POTION" to "ポーション",
            "PUFFERFISH" to "フグ",
            "PUMPKIN_PIE" to "パンプキンパイ",
            "PUMPKIN_SEEDS" to "カボチャの種",
            "QUARTZ" to "ネザークォーツ",
            "RABBIT" to "生のウサギ肉",
            "RABBIT_STEW" to "ウサギシチュー",
            "ROTTEN_FLESH" to "腐った肉",
            "SALMON" to "生のサケ",
            "SHEARS" to "ハサミ",
            "SLIME_BALL" to "スライムボール",
            "SPECTRAL_ARROW" to "光の矢",
            "SPIDER_EYE" to "クモの目",
            "SPYGLASS" to "望遠鏡",
            "STICK" to "棒",
            "STONE_AXE" to "石の斧",
            "STONE_HOE" to "石のクワ",
            "STONE_PICKAXE" to "石のツルハシ",
            "STONE_SHOVEL" to "石のシャベル",
            "STONE_SPEAR" to "石の槍",
            "STONE_SWORD" to "石の剣",
            "SUGAR" to "砂糖",
            "SUSPICIOUS_STEW" to "怪しいシチュー",
            "SWEET_BERRIES" to "スイートベリー",
            "TIPPED_ARROW" to "効果付きの矢",
            "TROPICAL_FISH" to "熱帯魚",
            "WARPED_FUNGUS_ON_A_STICK" to "歪んだキノコ付きの棒",
            "WHEAT_SEEDS" to "小麦の種",
            "WOODEN_AXE" to "木の斧",
            "WOODEN_HOE" to "木のクワ",
            "WOODEN_PICKAXE" to "木のツルハシ",
            "WOODEN_SHOVEL" to "木のシャベル",
            "WOODEN_SPEAR" to "木の槍",
            "WOODEN_SWORD" to "木の剣",
            "WRITABLE_BOOK" to "本と羽根ペン",
            "WRITTEN_BOOK" to "記入済みの本"
        )


        /**
         * ガチャの抽選対象として許可されているか（ホワイトリスト方式）
         */
        fun isBlockedApplyItems(material: Material): Boolean {
            if (isBlocked(material)) return true
            return !ALLOWED_ITEMS.contains(material)
        }

        fun isBlocked(material: Material): Boolean {
            if (!material.isItem || material.isAir || material.isBlock) return true
            if (material in SINGLE_USE_ITEMS) return true
            if (material in PLACEMENT_ITEMS) return true
            if (material in SPECIAL_EQUIPMENT) return true

            return false
        }

        /**
         * baseItem の文字列 ("stick" / "STICK" / "minecraft:stick" など) を Material に変換する。
         */
        fun resolveBaseMaterial(baseItem: String?): Material? {
            val normalized = baseItem
                ?.trim()
                ?.removePrefix("minecraft:")
                ?.uppercase()
                ?.takeIf { it.isNotBlank() }
                ?: return null
            return Material.getMaterial(normalized)
        }

        /**
         * アイテムとして排出する設定を選べるか。
         * - item_model: 選べる
         * - custom_model_data: baseItem が ALLOWED_ITEMS に含まれていれば選べる。
         *   含まれていなければ選べず、常にパックとして排出する
         */
        fun canDropAsItem(data: RegisteredModelData): Boolean {
            if (data.slotType.equals("item_model", ignoreCase = true)) return true
            val material = resolveBaseMaterial(data.baseItem) ?: return false
            return !isBlockedApplyItems(material)
        }


        private val SINGLE_USE_ITEMS = setOf(
            Material.EGG,
            Material.SNOWBALL,
            Material.ENDER_PEARL,
            Material.ENDER_EYE,
            Material.EXPERIENCE_BOTTLE,
            Material.SPLASH_POTION,
            Material.LINGERING_POTION,
            Material.FIRE_CHARGE,
            Material.WIND_CHARGE,
            Material.FIREWORK_ROCKET,
            Material.TRIDENT
        )

        private val PLACEMENT_ITEMS = setOf(
            Material.REDSTONE,
            Material.STRING,
            Material.ARMOR_STAND,
            Material.BUCKET,
            Material.WATER_BUCKET,
            Material.LAVA_BUCKET,
            Material.MILK_BUCKET,
            Material.POWDER_SNOW_BUCKET,
            Material.AXOLOTL_BUCKET,
            Material.COD_BUCKET,
            Material.SALMON_BUCKET,
            Material.PUFFERFISH_BUCKET,
            Material.TROPICAL_FISH_BUCKET,
            Material.TADPOLE_BUCKET,
            Material.MINECART,
            Material.CHEST_MINECART,
            Material.FURNACE_MINECART,
            Material.HOPPER_MINECART,
            Material.TNT_MINECART,
            Material.COMMAND_BLOCK_MINECART,
            Material.LEAD,
            Material.NAME_TAG,
            Material.SADDLE,
            Material.BONE_MEAL,
        )

        private val SPECIAL_EQUIPMENT = setOf(
            Material.LEATHER_CHESTPLATE,
            Material.CHAINMAIL_CHESTPLATE,
            Material.COPPER_CHESTPLATE,
            Material.IRON_CHESTPLATE,
            Material.GOLDEN_CHESTPLATE,
            Material.DIAMOND_CHESTPLATE,
            Material.NETHERITE_CHESTPLATE,
            Material.LEATHER_LEGGINGS,
            Material.CHAINMAIL_LEGGINGS,
            Material.COPPER_LEGGINGS,
            Material.IRON_LEGGINGS,
            Material.GOLDEN_LEGGINGS,
            Material.DIAMOND_LEGGINGS,
            Material.NETHERITE_LEGGINGS,
            Material.LEATHER_BOOTS,
            Material.CHAINMAIL_BOOTS,
            Material.COPPER_BOOTS,
            Material.IRON_BOOTS,
            Material.GOLDEN_BOOTS,
            Material.DIAMOND_BOOTS,
            Material.NETHERITE_BOOTS,
            Material.ELYTRA,
            Material.WOLF_ARMOR,
            Material.LEATHER_HORSE_ARMOR,
            Material.IRON_HORSE_ARMOR,
            Material.GOLDEN_HORSE_ARMOR,
            Material.DIAMOND_HORSE_ARMOR,
        )
    }
}