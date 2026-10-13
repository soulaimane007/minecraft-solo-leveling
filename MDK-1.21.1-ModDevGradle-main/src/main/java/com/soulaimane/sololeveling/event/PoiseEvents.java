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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

public class PoiseEvents {

    /** Block knockback when poise is high enough. */
    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        double poise    = getTotalPoise(p);
        double incoming = p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 2;
        if (poise >= incoming) e.setCanceled(true);
    }

    /** Defender-side: apply physical damage reduction. */
    @SubscribeEvent
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