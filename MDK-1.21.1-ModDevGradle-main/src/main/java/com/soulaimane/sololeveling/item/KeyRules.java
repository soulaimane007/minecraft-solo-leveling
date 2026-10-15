package com.soulaimane.sololeveling.item;

import com.soulaimane.sololeveling.ModItems;
import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Rules shared by every System Key:
 *  - a player carries at most ONE key of each kind,
 *  - keys never stack (stack size 1),
 *  - a key is an ordinary item: it drops when you die and it can be lost.
 */
public final class KeyRules {
    private KeyRules() {}

    /** True for any System Key (Basic or Lost Cave). */
    public static boolean isKey(ItemStack stack) {
        return stack.is(ModItems.SYSTEM_KEY.get()) || stack.is(ModItems.SYSTEM_KEY_LOST_CAVE.get());
    }

    /** True when the player carries that key anywhere (hotbar, inventory, armor slots, off hand). */
    public static boolean has(Player player, Item key) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(key)) return true;
        }
        return false;
    }

    /**
     * Gives one key, unless the player already carries one.
     * @return true if a key was given
     */
    public static boolean giveIfMissing(ServerPlayer player, Item key) {
        if (has(player, key)) return false;
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(key));
        return true;
    }

    /**
     * Keeps the first key found (with a count of one) and removes every other one.
     * @return how many keys were removed
     */
    public static int enforceSingle(ServerPlayer player, Item key) {
        Inventory inv = player.getInventory();
        boolean kept = false;
        int removed = 0;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.is(key)) continue;

            if (!kept) {
                kept = true;
                if (stack.getCount() > 1) {
                    removed += stack.getCount() - 1;
                    stack.setCount(1);
                }
            } else {
                removed += stack.getCount();
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        return removed;
    }

    /** Applies {@link #enforceSingle} to every kind of key and tells the player if something was removed. */
    public static void cleanUp(ServerPlayer player) {
        int removed = enforceSingle(player, ModItems.SYSTEM_KEY.get())
                + enforceSingle(player, ModItems.SYSTEM_KEY_LOST_CAVE.get());
        if (removed > 0) {
            SLUtil.chat(player, "§6[System] §7You can only carry one key of each kind: §e"
                    + removed + " extra removed§7.");
        }
    }
}
