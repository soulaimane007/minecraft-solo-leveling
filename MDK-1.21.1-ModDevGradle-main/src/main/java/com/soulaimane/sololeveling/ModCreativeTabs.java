package com.soulaimane.sololeveling;

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
            }).build());
}