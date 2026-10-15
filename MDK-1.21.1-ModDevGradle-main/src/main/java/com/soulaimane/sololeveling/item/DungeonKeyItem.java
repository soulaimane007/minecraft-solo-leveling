package com.soulaimane.sololeveling.item;

import com.soulaimane.sololeveling.ModItems;
import com.soulaimane.sololeveling.client.ClientPowerView;
import com.soulaimane.sololeveling.dungeon.engine.DungeonDefinition;
import com.soulaimane.sololeveling.dungeon.engine.DungeonEngine;
import com.soulaimane.sololeveling.dungeon.engine.DungeonRegistry;
import com.soulaimane.sololeveling.power.Requirements;
import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Dungeon Key. ONE item for every dungeon: the dungeon is written in the item's custom data
 * ({dungeon:"sololeveling:goblin_cave"}), so a new dungeon needs no new item, model, texture or language line.
 *
 * Right-click: opens the gate of the dungeon written on the key. Right-click inside the dungeon: leaves it
 * (when the level allows it). Whether the key is used up is decided by the dungeon (default: yes).
 *
 * A key can also be written for one level: {dungeon:"sololeveling:goblin_cave", level:3}. A player who has not
 * cleared the levels before it starts at the highest level he may play.
 *
 * Get one with  /give @s sololeveling:dungeon_key[custom_data={dungeon:"sololeveling:goblin_cave"}]
 * or  /sl dungeon give goblin_cave [level]  (every dungeon also has a key in the creative tab).
 */
public class DungeonKeyItem extends Item {

    public static final String TAG_DUNGEON = "dungeon";
    public static final String TAG_LEVEL = "level";

    private static final int USE_COOLDOWN_TICKS = 60;

    public DungeonKeyItem(Properties properties) {
        super(properties);
    }

    // ─── The dungeon written on a key ────────────────────────────────

    /** A key that opens the dungeon at the level the player has reached. */
    public static ItemStack create(DungeonDefinition dungeon) {
        return create(dungeon, 0);
    }

    /** @param level 1-based level written on the key, 0 = none */
    public static ItemStack create(DungeonDefinition dungeon, int level) {
        ItemStack stack = new ItemStack(ModItems.DUNGEON_KEY.get());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(TAG_DUNGEON, dungeon.id());
            if (level > 0) {
                tag.putInt(TAG_LEVEL, level);
            }
        });
        return stack;
    }

    /** The dungeon written on this key, or null (blank key, or a dungeon that no longer exists). */
    public static DungeonDefinition dungeonOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String id = data.copyTag().getString(TAG_DUNGEON);
        return id.isEmpty() ? null : DungeonRegistry.get(id);
    }

    /** The level written on this key (1-based), 0 when there is none. */
    public static int levelOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : Math.max(0, data.copyTag().getInt(TAG_LEVEL));
    }

    // ─── Using it ────────────────────────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        DungeonDefinition dungeon = dungeonOf(stack);

        if (dungeon == null) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                SLUtil.actionBar(serverPlayer, "§7This key is blank: it opens no gate.");
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (DungeonEngine.isInside(serverPlayer)) {
                DungeonEngine.leave(serverPlayer, false);
            } else {
                DungeonEngine.enter(serverPlayer, dungeon, stack, levelOf(stack), false);
            }
            player.getCooldowns().addCooldown(this, USE_COOLDOWN_TICKS);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // ─── Looks ───────────────────────────────────────────────────────

    @Override
    public Component getName(ItemStack stack) {
        DungeonDefinition dungeon = dungeonOf(stack);
        if (dungeon == null) {
            return super.getName(stack);
        }
        MutableComponent name = Component.translatableWithFallback("item.sololeveling.dungeon_key.named",
                "Dungeon Key: %s", dungeon.displayName());
        return name.withStyle(style -> style.withColor(TextColor.fromRgb(dungeon.color() & 0xFFFFFF)));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return dungeonOf(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        DungeonDefinition dungeon = dungeonOf(stack);
        if (dungeon == null) {
            tooltip.add(Component.literal("A blank key. It opens no gate.").withStyle(ChatFormatting.GRAY));
            return;
        }

        if (!dungeon.description().getString().isEmpty()) {
            tooltip.add(dungeon.description().copy().withStyle(ChatFormatting.GRAY));
        }

        int levels = dungeon.levelCount();
        int written = levelOf(stack);
        tooltip.add(Component.literal(levels + (levels == 1 ? " level" : " levels")
                + (written > 0 ? "  -  opens at level " + Math.min(written, levels) : ""))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal(dungeon.consumesKey() ? "The key is used up when the gate opens."
                        : "The key is kept when the gate opens.")
                .withStyle(ChatFormatting.DARK_GRAY));

        Requirements need = dungeon.entryRequirements();
        if (!need.isEmpty()) {
            tooltip.add(Component.literal("To enter you need:").withStyle(ChatFormatting.GOLD));
            for (Requirements.Line line : need.lines(ClientPowerView.INSTANCE)) {
                tooltip.add(Component.literal((line.met() ? " ✔ " : " ✘ ") + line.text())
                        .withStyle(line.met() ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
        }
        tooltip.add(Component.literal("Right-click to open the gate.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
