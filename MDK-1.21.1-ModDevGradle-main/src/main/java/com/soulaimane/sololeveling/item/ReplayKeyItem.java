package com.soulaimane.sololeveling.item;

import com.soulaimane.sololeveling.util.SLUtil;
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
 * System Key: Replay. The Blacksmith of the Bonfire camp sells it for Zombie King Crystals.
 *
 * It does nothing on its own: right-click the Rift Stone in the camp with it (that click is handled in
 * DungeonEvents, which calls DungeonManager.useRiftStone) and the Tutorial Dungeon starts again.
 * One key is used up per run. Unlike the other two keys it has no "one per person" rule: you pay for it.
 */
public class ReplayKeyItem extends Item {

    private static final int USE_COOLDOWN_TICKS = 20;

    public ReplayKeyItem(Properties properties) {
        super(properties);
    }

    /** Right-click in the air: only a reminder of where this key is used. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            SLUtil.actionBar(serverPlayer, "§6[System] Use this key on the Rift Stone in the Bonfire camp.");
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
        tooltip.add(Component.translatable("item.sololeveling.system_key_replay.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.sololeveling.system_key_replay.desc2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.sololeveling.system_key_replay.desc3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
