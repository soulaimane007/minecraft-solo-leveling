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
 * System Key: Lost Cave. Handed out when the Zombie King falls.
 * The Lost Cave dungeon does not exist yet, so for now the key only tells you so.
 * (When the dungeon is added, this is the class that will open its gate.)
 */
public class LostCaveKeyItem extends Item {

    private static final int USE_COOLDOWN_TICKS = 40;

    public LostCaveKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            SLUtil.actionBar(serverPlayer, "§d[System] The gate of the Lost Cave is not open yet.");
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
        tooltip.add(Component.translatable("item.sololeveling.system_key_lost_cave.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.sololeveling.system_key_lost_cave.desc2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.sololeveling.system_key.desc4").withStyle(ChatFormatting.DARK_GRAY));
    }
}
