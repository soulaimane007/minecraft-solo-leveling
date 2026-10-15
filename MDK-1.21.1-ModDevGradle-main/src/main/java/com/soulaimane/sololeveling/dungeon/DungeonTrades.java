package com.soulaimane.sololeveling.dungeon;

import com.soulaimane.sololeveling.Config;
import com.soulaimane.sololeveling.ModItems;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.common.BasicItemListing;

/**
 * The shops of the Bonfire camp. Prices are in Zombie Crystals (ZC) and Zombie King Crystals (KC).
 * Every trade can be repeated forever (max uses 9999). Villagers normally drop a few experience
 * orbs per trade; DungeonEvents cancels every orb in the dungeon, so trading never gives XP.
 *
 * The Blacksmith's first offer is the System Key: Replay (price from the config, 2 KC by default),
 * the key that starts the dungeon again at the Rift Stone.
 */
public final class DungeonTrades {
    private DungeonTrades() {}

    public enum Role {
        ALCHEMIST("sl_npc_alchemist", "§5§lAlchemist"),
        BLACKSMITH("sl_npc_blacksmith", "§6§lBlacksmith");

        public final String tag;
        public final String displayName;

        Role(String tag, String displayName) {
            this.tag = tag;
            this.displayName = displayName;
        }
    }

    /** Replaces whatever offers the villager had with the shop of its role. Server side only. */
    public static void fill(Villager villager, Role role) {
        MerchantOffers offers = villager.getOffers();
        offers.clear();

        switch (role) {
            case ALCHEMIST -> {
                add(offers, villager, zc(2), new ItemStack(ModItems.HEALTH_POTION.get()));
                add(offers, villager, zc(5), new ItemStack(ModItems.GREATER_HEALTH_POTION.get()));
                add(offers, villager, zc(2), new ItemStack(ModItems.STAMINA_POTION.get()));
                add(offers, villager, zc(2), new ItemStack(ModItems.MANA_POTION.get()));
                add(offers, villager, zc(1), new ItemStack(Items.COOKED_BEEF, 6));
                add(offers, villager, zc(4), new ItemStack(Items.GOLDEN_APPLE));
                add(offers, villager, kc(1), new ItemStack(ModItems.GREATER_HEALTH_POTION.get(), 3));
                add(offers, villager, kc(2), new ItemStack(Items.TOTEM_OF_UNDYING));
            }
            case BLACKSMITH -> {
                // The key to repeat the dungeon. The price (Zombie King Crystals) comes from the server config.
                add(offers, villager, kc(Config.SERVER.dungeonReplayKeyPrice.get()), new ItemStack(ModItems.SYSTEM_KEY_REPLAY.get()));
                add(offers, villager, zc(2), new ItemStack(Items.STONE_SWORD));
                add(offers, villager, zc(1), new ItemStack(Items.LEATHER_HELMET));
                add(offers, villager, zc(3), new ItemStack(Items.LEATHER_CHESTPLATE));
                add(offers, villager, zc(2), new ItemStack(Items.LEATHER_LEGGINGS));
                add(offers, villager, zc(1), new ItemStack(Items.LEATHER_BOOTS));
                add(offers, villager, zc(8), new ItemStack(Items.IRON_SWORD));
                add(offers, villager, zc(5), new ItemStack(Items.IRON_HELMET));
                add(offers, villager, zc(8), new ItemStack(Items.IRON_CHESTPLATE));
                add(offers, villager, zc(7), new ItemStack(Items.IRON_LEGGINGS));
                add(offers, villager, zc(4), new ItemStack(Items.IRON_BOOTS));
                add(offers, villager, zc(6), new ItemStack(Items.SHIELD));
                add(offers, villager, zc(6), new ItemStack(Items.BOW));
                add(offers, villager, zc(2), new ItemStack(Items.ARROW, 16));
                add(offers, villager, zc(30), new ItemStack(Items.DIAMOND_SWORD));
                add(offers, villager, kc(3), new ItemStack(Items.DIAMOND_CHESTPLATE));
                // System Key: Basic is not for sale (one per person, see KeyRules). Only the Replay key is.
            }
        }
    }

    private static ItemStack zc(int count) {
        return new ItemStack(ModItems.ZOMBIE_CRYSTAL.get(), count);
    }

    private static ItemStack kc(int count) {
        return new ItemStack(ModItems.ZOMBIE_KING_CRYSTAL.get(), count);
    }

    /** maxUses 9999, no XP, no price scaling. */
    private static void add(MerchantOffers offers, Villager villager, ItemStack cost, ItemStack result) {
        MerchantOffer offer = new BasicItemListing(cost, result, 9999, 0, 0.0f)
                .getOffer(villager, villager.getRandom());
        if (offer != null) {
            offers.add(offer);
        }
    }
}
