package com.soulaimane.sololeveling.item;

import com.soulaimane.sololeveling.dungeon.DungeonManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * System Key: Basic (registry name "system_key").
 * Right-click anywhere: opens the gate to the Tutorial Dungeon.
 * Right-click inside the dungeon: brings you back to where you opened the gate.
 */
public class SystemKeyItem extends Item {

    private static final int USE_COOLDOWN_TICKS = 60;

    public SystemKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            DungeonManager.useKey(serverPlayer, stack);
            player.getCooldowns().addCooldown(this, USE_COOLDOWN_TICKS);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.sololeveling.system_key.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.sololeveling.system_key.desc2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.sololeveling.system_key.desc3").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.sololeveling.system_key.desc4").withStyle(ChatFormatting.DARK_GRAY));
    }
}
