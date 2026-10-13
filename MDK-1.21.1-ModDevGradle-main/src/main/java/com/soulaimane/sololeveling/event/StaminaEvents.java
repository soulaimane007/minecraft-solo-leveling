package com.soulaimane.sololeveling.event;

import com.soulaimane.sololeveling.Config;
import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.EquipmentLoadState;
import com.soulaimane.sololeveling.data.ModDataMaps;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.data.SoftCap;
import com.soulaimane.sololeveling.network.SyncStaminaPayload;
import com.soulaimane.sololeveling.registry.ModAttributes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class StaminaEvents {

    private static final float MELEE_COST_BASE = 16.7f;
    private static final float JUMP_COST       = 4f;
    private static final float SYNC_EPSILON    = 0.001f;

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);

        if (d.exhausted()) {
            e.setCanceled(true);
            p.displayClientMessage(Component.literal("§c[System] Too exhausted to attack."), true);
            return;
        }

        double weight = getWeight(p.getMainHandItem());
        float cost = (float)(MELEE_COST_BASE * weight / 3.0);
        spend(p, cost);
    }

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);

        if (d.exhausted()) {
            e.setBlocked(false);
            p.displayClientMessage(Component.literal("§c[System] Too exhausted to block."), true);
            return;
        }
        float cost = (float)(Config.SERVER.staminaCostBlockMultiplier.get() * e.getBlockedDamage());
        spend(p, cost);
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);

        if (d.exhausted()) {
            var m = p.getDeltaMovement();
            if (m.y > 0) p.setDeltaMovement(m.x, 0, m.z);
            p.displayClientMessage(Component.literal("§c[System] Too exhausted to jump."), true);
            return;
        }
        spend(p, JUMP_COST);
    }

    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent e) {
        Player player = e.getPlayer();
        if (!(player instanceof ServerPlayer sp)) return;
        if (!Config.SERVER.staminaBreakBlockToggle.get()) return;

        PlayerSystemData d = sp.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        if (d.exhausted()) {
            e.setCanceled(true);
            sp.displayClientMessage(Component.literal("§c[System] Too exhausted to mine."), true);
            return;
        }
        spend(sp, Config.SERVER.staminaCostBreakBlock.get().floatValue());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;

        PlayerSystemData before = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        PlayerSystemData after  = tickStamina(p, before);

        if (after != before) {
            p.setData(ModAttachments.PLAYER_SYSTEM_DATA, after);
        }

        boolean staminaChanged = Math.abs(after.stamina() - before.stamina()) > SYNC_EPSILON;
        boolean stateChanged   = after.exhausted() != before.exhausted();

        if (staminaChanged || stateChanged) {
            sendStaminaSync(p, after);
        }
    }

    private static PlayerSystemData tickStamina(ServerPlayer p, PlayerSystemData d) {
        float   maxSta    = getMaxStamina(p);
        float   stamina   = d.stamina();
        int     delay     = d.staminaDelayTicks();
        boolean exhausted = d.exhausted();
        boolean changed   = false;
        boolean physicallyActive = false;   // blocks regen during sprint

        // 1. Sprint drain — physicallyActive prevents same-tick regen
        if (p.isSprinting()) {
            EquipmentLoadState state = getLoadState(p, d);
            if (state == EquipmentLoadState.OVERWEIGHT || exhausted) {
                p.setSprinting(false);
            } else {
                float cost = (float)(Config.SERVER.staminaCostSprintPerSecond.get() / 20.0);
                stamina = Math.max(0f, stamina - cost);
                physicallyActive = true;
                int cfgDelay = (int)(Config.SERVER.staminaRegenDelaySeconds.get() * 20);
                delay = Math.max(delay, Math.max(cfgDelay, 1));
                changed = true;
            }
        }

        // 2. Delay countdown OR regen (blocked if sprinting)
        if (delay > 0) {
            delay--;
            changed = true;
        } else if (!physicallyActive && stamina < maxSta) {
            double effAgi  = SoftCap.effective(d.agility(), d.rank());
            double baseReg = Config.SERVER.staminaRegenBase.get() + (effAgi * 0.05);
            EquipmentLoadState state = getLoadState(p, d);
            double regen   = baseReg * state.staminaRegenMultiplier;
            float  ns      = Math.min(maxSta, stamina + (float)(regen / 20.0));
            if (Math.abs(ns - stamina) > 1e-6f) {
                stamina = ns;
                changed = true;
            }
        }

        // 3. Exhausted transitions
        if (stamina <= 0f && !exhausted) {
            exhausted = true;
            changed = true;
        } else if (exhausted && stamina >= maxSta * Config.SERVER.exhaustedExitPercent.get()) {
            exhausted = false;
            changed = true;
        }

        if (!changed) return d;
        return d.withStamina(stamina).withDelayTicks(delay).withExhausted(exhausted);
    }

    public static void spend(ServerPlayer p, float amount) {
        if (amount <= 0) return;
        PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        float newSta = Math.max(0f, d.stamina() - amount);
        PlayerSystemData updated = d
                .withStamina(newSta)
                .withDelayTicks((int)(Config.SERVER.staminaRegenDelaySeconds.get() * 20));

        if (newSta <= 0f && !updated.exhausted()) {
            updated = updated.withExhausted(true);
        }
        p.setData(ModAttachments.PLAYER_SYSTEM_DATA, updated);
        sendStaminaSync(p, updated);
    }

    public static void sendStaminaSync(ServerPlayer p, PlayerSystemData d) {
        PacketDistributor.sendToPlayer(p,
                new SyncStaminaPayload(d.stamina(), getMaxStamina(p), d.exhausted()));
    }

    public static float getMaxStamina(ServerPlayer p) {
        AttributeInstance a = p.getAttribute(ModAttributes.MAX_STAMINA);
        return a != null ? (float) a.getValue() : 100f;
    }

    public static double getWeight(ItemStack stack) {
        if (stack.isEmpty()) return 1.0;
        Double w = stack.getItemHolder().getData(ModDataMaps.WEIGHT);
        if (w != null && w > 0) return w;
        var item = stack.getItem();
        if (item instanceof SwordItem)    return 4.0;
        if (item instanceof AxeItem)      return 5.0;
        if (item instanceof PickaxeItem)  return 4.0;
        if (item instanceof ShovelItem)   return 3.0;
        if (item instanceof HoeItem)      return 2.0;
        return 1.0;
    }

    private static EquipmentLoadState getLoadState(ServerPlayer p, PlayerSystemData d) {
        AttributeInstance maxLoadAttr = p.getAttribute(ModAttributes.MAX_EQUIP_LOAD);
        double maxLoad = maxLoadAttr != null ? maxLoadAttr.getValue() : 30.0;
        double ratio   = maxLoad > 0 ? d.currentEquipLoad() / maxLoad : 0;
        return EquipmentLoadState.fromRatio(ratio);
    }
}