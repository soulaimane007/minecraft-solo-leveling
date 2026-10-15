package com.soulaimane.sololeveling;

import com.soulaimane.sololeveling.item.CrystalItem;
import com.soulaimane.sololeveling.item.DungeonKeyItem;
import com.soulaimane.sololeveling.item.LostCaveKeyItem;
import com.soulaimane.sololeveling.item.MagicPaperItem;
import com.soulaimane.sololeveling.item.ReplayKeyItem;
import com.soulaimane.sololeveling.item.SystemKeyItem;
import com.soulaimane.sololeveling.item.SystemPotionItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SoloLeveling.MOD_ID);

    // ── Keys: one per person, never stacked (see KeyRules) ──
    // "System Key: Basic" opens the Tutorial Dungeon. The registry name stays "system_key"
    // so keys that already exist in your worlds keep working.
    public static final DeferredItem<SystemKeyItem> SYSTEM_KEY = ITEMS.registerItem(
            "system_key",
            SystemKeyItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    // "System Key: Lost Cave": given when the Zombie King falls. Its dungeon comes later.
    public static final DeferredItem<LostCaveKeyItem> SYSTEM_KEY_LOST_CAVE = ITEMS.registerItem(
            "system_key_lost_cave",
            LostCaveKeyItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    // "System Key: Replay": sold by the Blacksmith of the Bonfire camp. Right-click the Rift Stone with it
    // and the Tutorial Dungeon starts again. Not limited to one: you pay for every run.
    public static final DeferredItem<ReplayKeyItem> SYSTEM_KEY_REPLAY = ITEMS.registerItem(
            "system_key_replay",
            ReplayKeyItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    // ── The two "universal" items of the new systems (v0.5.0) ──
    // ONE Magic Paper item for every magic: the magic is written in the item's custom data (see MagicPaperItem).
    public static final DeferredItem<MagicPaperItem> MAGIC_PAPER = ITEMS.registerItem(
            "magic_paper",
            MagicPaperItem::new,
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

    // ONE Dungeon Key item for every dungeon registered in ModDungeons: the dungeon is written in the item's custom data
    // (see DungeonKeyItem). One per person, never stacked, like the System Keys.
    public static final DeferredItem<DungeonKeyItem> DUNGEON_KEY = ITEMS.registerItem(
            "dungeon_key",
            DungeonKeyItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    // ── Currency (dropped by zombies, spent at the Bonfire merchants) ──
    public static final DeferredItem<CrystalItem> ZOMBIE_CRYSTAL = ITEMS.registerItem(
            "zombie_crystal",
            props -> new CrystalItem(props, "item.sololeveling.zombie_crystal.desc", false),
            new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<CrystalItem> ZOMBIE_KING_CRYSTAL = ITEMS.registerItem(
            "zombie_king_crystal",
            props -> new CrystalItem(props, "item.sololeveling.zombie_king_crystal.desc", true),
            new Item.Properties().rarity(Rarity.EPIC));

    // ── Merchant potions ──
    public static final DeferredItem<SystemPotionItem> HEALTH_POTION = ITEMS.registerItem(
            "health_potion",
            props -> new SystemPotionItem(props, SystemPotionItem.Kind.HEALTH),
            new Item.Properties().stacksTo(16));

    public static final DeferredItem<SystemPotionItem> GREATER_HEALTH_POTION = ITEMS.registerItem(
            "greater_health_potion",
            props -> new SystemPotionItem(props, SystemPotionItem.Kind.GREATER_HEALTH),
            new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

    public static final DeferredItem<SystemPotionItem> STAMINA_POTION = ITEMS.registerItem(
            "stamina_potion",
            props -> new SystemPotionItem(props, SystemPotionItem.Kind.STAMINA),
            new Item.Properties().stacksTo(16));

    public static final DeferredItem<SystemPotionItem> MANA_POTION = ITEMS.registerItem(
            "mana_potion",
            props -> new SystemPotionItem(props, SystemPotionItem.Kind.MANA),
            new Item.Properties().stacksTo(16));
}
