package com.soulaimane.sololeveling.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Currency item (Zombie Crystal / Zombie King Crystal) with a one-line tooltip. */
public class CrystalItem extends Item {

    private final String descriptionKey;
    private final boolean glint;

    public CrystalItem(Properties properties, String descriptionKey, boolean glint) {
        super(properties);
        this.descriptionKey = descriptionKey;
        this.glint = glint;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return glint;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY));
    }
}
