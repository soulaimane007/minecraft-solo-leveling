package com.soulaimane.sololeveling.item;

import com.soulaimane.sololeveling.ModItems;
import com.soulaimane.sololeveling.client.ClientPowerView;
import com.soulaimane.sololeveling.magic.Magic;
import com.soulaimane.sololeveling.magic.MagicManager;
import com.soulaimane.sololeveling.magic.MagicRegistry;
import com.soulaimane.sololeveling.power.Requirements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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
import java.util.Locale;

/**
 * The Magic Paper. ONE item for every magic: the magic written on the paper is stored in the item's custom data
 * ({magic:"sololeveling:flame_bolt"}), so a new magic needs no new item, model or texture.
 *
 * Right-click: if the player meets the requirements, the paper burns and he learns the magic.
 * Otherwise the paper stays and the chat lists what is missing.
 *
 * Get one with  /give @s sololeveling:magic_paper[custom_data={magic:"sololeveling:flame_bolt"}]
 * or  /sl magic give flame_bolt  (every magic also has a paper in the creative tab).
 */
public class MagicPaperItem extends Item {

    public static final String TAG = "magic";

    public MagicPaperItem(Properties properties) {
        super(properties);
    }

    // ─── The magic written on a paper ────────────────────────────────

    public static ItemStack create(Magic magic) {
        return create(magic, 1);
    }

    public static ItemStack create(Magic magic, int count) {
        ItemStack stack = new ItemStack(ModItems.MAGIC_PAPER.get(), count);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG, magic.id()));
        return stack;
    }

    /** The magic written on this paper, or null (blank paper, or a magic that no longer exists). */
    public static Magic magicOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String id = data.copyTag().getString(TAG);
        return id.isEmpty() ? null : MagicRegistry.byId(id);
    }

    // ─── Using it ────────────────────────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            Magic magic = magicOf(stack);
            if (magic == null) {
                serverPlayer.displayClientMessage(Component.literal("§7This paper is blank."), true);
                return InteractionResultHolder.fail(stack);
            }
            MagicManager.LearnResult result = MagicManager.learnFromPaper(serverPlayer, magic);
            if (result == MagicManager.LearnResult.LEARNED && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // ─── Looks ───────────────────────────────────────────────────────

    @Override
    public Component getName(ItemStack stack) {
        Magic magic = magicOf(stack);
        if (magic == null) {
            return super.getName(stack);
        }
        return Component.translatableWithFallback("item.sololeveling.magic_paper.named",
                "Magic Paper: %s", magic.displayName());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return magicOf(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        Magic magic = magicOf(stack);
        if (magic == null) {
            tooltip.add(Component.literal("A blank paper. Nothing is written on it.").withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltip.add(magic.description().copy().withStyle(ChatFormatting.GRAY));
        if (magic.manaCost() > 0f) {
            tooltip.add(Component.literal("Mana: " + format(magic.manaCost())).withStyle(ChatFormatting.AQUA));
        }
        if (magic.cooldownTicks() > 0) {
            tooltip.add(Component.literal("Cooldown: " + format(magic.cooldownTicks() / 20.0) + "s")
                    .withStyle(ChatFormatting.DARK_AQUA));
        }

        if (!magic.requirements().isEmpty()) {
            tooltip.add(Component.literal("To learn it you need:").withStyle(ChatFormatting.GOLD));
            for (Requirements.Line line : magic.requirements().lines(ClientPowerView.INSTANCE)) {
                tooltip.add(Component.literal((line.met() ? " ✔ " : " ✘ ") + line.text())
                        .withStyle(line.met() ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
        }
        tooltip.add(Component.literal("Right-click to burn the paper and learn the magic.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String format(double v) {
        return Math.abs(v - Math.round(v)) < 0.05 ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }
}
