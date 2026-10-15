package com.soulaimane.sololeveling.item;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.event.ManaEvents;
import com.soulaimane.sololeveling.event.StaminaEvents;
import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

/**
 * The merchant potions. They hook into the Solo Leveling resources:
 * health (vanilla HP), stamina and mana (System data).
 */
public class SystemPotionItem extends Item {

    /** The Health Potion heals 4 hearts (8 HP), whatever your maximum health is. */
    public static final float HEALTH_POTION_HEARTS = 4.0f;

    public enum Kind {
        // id, share of max health restored, flat health restored (in HP, 2 HP = 1 heart)
        HEALTH("health_potion", 0f, HEALTH_POTION_HEARTS * 2.0f),
        GREATER_HEALTH("greater_health_potion", 1.0f, 0f),
        STAMINA("stamina_potion", 0f, 0f),
        MANA("mana_potion", 0f, 0f);

        public final String id;
        public final float healFraction;
        public final float healFlat;

        Kind(String id, float healFraction, float healFlat) {
            this.id = id;
            this.healFraction = healFraction;
            this.healFlat = healFlat;
        }
    }

    private static final int DRINK_TICKS = 32;

    private final Kind kind;

    public SystemPotionItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind getKind() { return kind; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return DRINK_TICKS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer serverPlayer) {
            applyEffect(serverPlayer);
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
            serverPlayer.awardStat(Stats.ITEM_USED.get(this));
        }

        if (entity instanceof Player player && !player.getAbilities().instabuild) {
            stack.shrink(1);
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (stack.isEmpty()) {
                return bottle;
            }
            if (!player.getInventory().add(bottle)) {
                player.drop(bottle, false);
            }
        }

        entity.gameEvent(GameEvent.DRINK);
        return stack;
    }

    private void applyEffect(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        ParticleOptions particle;

        switch (kind) {
            case HEALTH, GREATER_HEALTH -> {
                float before = p.getHealth();
                p.heal(p.getMaxHealth() * kind.healFraction + kind.healFlat);
                float healed = p.getHealth() - before;
                // 2 HP = 1 heart. Shown in hearts because that is what the player sees on the HUD.
                String hearts = formatHearts(healed / 2.0f);
                SLUtil.actionBar(p, "§c+" + hearts + (hearts.equals("1") ? " heart" : " hearts"));
                particle = ParticleTypes.HEART;
            }
            case STAMINA -> {
                PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
                PlayerSystemData updated = d
                        .withStamina(StaminaEvents.getMaxStamina(p))
                        .withExhausted(false)
                        .withDelayTicks(0);
                p.setData(ModAttachments.PLAYER_SYSTEM_DATA, updated);
                StaminaEvents.sendStaminaSync(p, updated);
                SLUtil.actionBar(p, "§aStamina restored");
                particle = ParticleTypes.HAPPY_VILLAGER;
            }
            case MANA -> {
                ManaEvents.fill(p);
                SLUtil.actionBar(p, "§bMana restored");
                particle = ParticleTypes.ENCHANT;
            }
            default -> particle = ParticleTypes.HAPPY_VILLAGER;
        }

        level.sendParticles(particle, p.getX(), p.getY() + 1.0, p.getZ(), 12, 0.4, 0.6, 0.4, 0.05);
        SLUtil.playSound(level, p.getX(), p.getY(), p.getZ(),
                "minecraft:entity.generic.drink", SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    /** 4.0 → "4", 2.5 → "2.5". */
    private static String formatHearts(float hearts) {
        if (Math.abs(hearts - Math.round(hearts)) < 0.05f) {
            return Integer.toString(Math.round(hearts));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", hearts);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.sololeveling." + kind.id + ".desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
