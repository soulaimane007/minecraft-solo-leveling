package com.soulaimane.sololeveling;

import com.soulaimane.sololeveling.dungeon.engine.DungeonDefinition;
import com.soulaimane.sololeveling.dungeon.engine.DungeonRegistry;
import com.soulaimane.sololeveling.item.DungeonKeyItem;
import com.soulaimane.sololeveling.item.MagicPaperItem;
import com.soulaimane.sololeveling.magic.Magic;
import com.soulaimane.sololeveling.magic.MagicRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SoloLeveling.MOD_ID);

    public static final Supplier<CreativeModeTab> SOLO_LEVELING_TAB = CREATIVE_MODE_TABS.register("solo_leveling_tab", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.INGREDIENTS)
            .title(Component.translatable("creativetab.sololeveling.solo_leveling_tab"))
            .icon(() -> new ItemStack(ModItems.SYSTEM_KEY.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.SYSTEM_KEY.get());
                output.accept(ModItems.SYSTEM_KEY_LOST_CAVE.get());
                output.accept(ModItems.SYSTEM_KEY_REPLAY.get());
                output.accept(ModItems.ZOMBIE_CRYSTAL.get());
                output.accept(ModItems.ZOMBIE_KING_CRYSTAL.get());
                output.accept(ModItems.HEALTH_POTION.get());
                output.accept(ModItems.GREATER_HEALTH_POTION.get());
                output.accept(ModItems.STAMINA_POTION.get());
                output.accept(ModItems.MANA_POTION.get());

                // One Magic Paper for every magic and one Dungeon Key for every dungeon: a new magic or dungeon
                // appears here by itself (the registries are filled when the mod starts, long before this runs).
                for (Magic magic : MagicRegistry.all()) {
                    output.accept(MagicPaperItem.create(magic));
                }
                for (DungeonDefinition dungeon : DungeonRegistry.all()) {
                    output.accept(DungeonKeyItem.create(dungeon));
                }
            }).build());
}
