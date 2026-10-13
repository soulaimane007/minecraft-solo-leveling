package com.soulaimane.sololeveling;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    // Create a Deferred Register for items
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SoloLeveling.MOD_ID);

    // Register the "system_key" item
    public static final DeferredItem<Item> SYSTEM_KEY = ITEMS.registerSimpleItem("system_key", new Item.Properties());
}