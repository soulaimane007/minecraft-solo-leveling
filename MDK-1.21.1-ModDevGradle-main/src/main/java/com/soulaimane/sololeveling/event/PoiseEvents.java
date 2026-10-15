package com.soulaimane.sololeveling.event;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.ModDataMaps;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.data.SoftCap;
import com.soulaimane.sololeveling.registry.ModAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

public class PoiseEvents {

    /**
     * Poise absorbs knockback.
     * 20 poise cancels a full-strength (1.0) knockback, a normal melee hit (0.4) needs 8.
     * Less poise than that only reduces the knockback proportionally.
     */
    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        float strength = e.getStrength();
        if (strength <= 0f) return;

        double poise = getTotalPoise(p);
        if (poise <= 0.0) return;

        double ratio = poise / (strength * 20.0);
        if (ratio >= 1.0) {
            e.setCanceled(true);
        } else {
            e.setStrength((float) (strength * (1.0 - ratio)));
        }
    }

    /**
     * Defender-side: apply physical damage reduction.
     * LOW priority so it runs AFTER WeaponDamageEvents has set the attacker's damage
     * (otherwise a player-vs-player hit would overwrite the reduction).
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        p.setData(ModAttachments.LAST_HIT_TICK.get(), p.tickCount);

        if (isPhysical(e.getSource())) {
            AttributeInstance drAttr = p.getAttribute(ModAttributes.PHYSICAL_DAMAGE_REDUCTION);
            if (drAttr != null) {
                float dr = (float) Math.min(drAttr.getValue(), 0.95);
                e.setAmount(e.getAmount() * (1f - dr));
            }
        }
    }

    public static double getTotalPoise(ServerPlayer p) {
        double total = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = p.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            Double poise = stack.getItemHolder().getData(ModDataMaps.POISE);
            if (poise != null) total += poise;
        }
        PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        total += SoftCap.effective(d.durability(), d.rank()) * 0.5;
        return total;
    }

    private static boolean isPhysical(DamageSource src) {
        var tag = src.typeHolder();
        if (tag.is(DamageTypeTags.IS_FIRE)) return false;
        if (tag.is(DamageTypeTags.WITCH_RESISTANT_TO)) return false;
        if (tag.is(DamageTypeTags.BYPASSES_ARMOR)) return false;
        return true;
    }
}