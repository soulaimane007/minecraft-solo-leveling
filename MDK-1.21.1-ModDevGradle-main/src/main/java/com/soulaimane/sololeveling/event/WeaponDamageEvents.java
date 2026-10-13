package com.soulaimane.sololeveling.event;

import com.soulaimane.sololeveling.Config;
import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.ModDataMaps;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.data.SoftCap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Replaces vanilla melee damage with a formula-driven calculation:
 *
 *     final_damage = (weapon_base + k × effective_str^exp) × cooldown_scale
 *
 * Applied only when:
 *   - the damage source's entity is a ServerPlayer
 *   - the direct entity IS that player (i.e. melee, not arrow/throwable)
 *   - the target is a LivingEntity
 *
 * Non-melee damage (arrows, potions, environmental) is untouched.
 */
public class WeaponDamageEvents {

    private static final double DEFAULT_UNARMED_BASE = 1.0;
    private static final double FALLBACK_WEAPON_BASE = 1.0;

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        // Only player melee
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (event.getSource().getDirectEntity() != attacker) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        ItemStack weapon = attacker.getMainHandItem();
        PlayerSystemData data = attacker.getData(ModAttachments.PLAYER_SYSTEM_DATA);

        double weaponBase = getWeaponBase(weapon);
        double effStr = SoftCap.effective(data.strength(), data.rank());

        double baseDamage = weaponBase
                + Config.SERVER.strDamageCoefficient.get()
                * Math.pow(effStr, Config.SERVER.strDamageExponent.get());

        // Respect vanilla cooldown scaling
        float cooldown = attacker.getAttackStrengthScale(0.5f);
        double scaled = baseDamage * (0.2 + cooldown * cooldown * 0.8);

        event.setAmount((float) scaled);
    }

    /**
     * Weapon base damage lookup:
     *   1. Data-map entry (weapon_damage.json)
     *   2. Class-based fallback (Sword → 2.85, Axe → 4.0, etc.)
     *   3. Unarmed fallback = 1.0 for empty hand / non-weapons
     */
    public static double getWeaponBase(ItemStack stack) {
        if (stack.isEmpty()) return DEFAULT_UNARMED_BASE;

        Double d = stack.getItemHolder().getData(ModDataMaps.WEAPON_DAMAGE);
        if (d != null && d > 0) return d;

        // Fallback if data map missing
        var item = stack.getItem();
        if (item instanceof net.minecraft.world.item.SwordItem) return 2.85;
        if (item instanceof net.minecraft.world.item.AxeItem)   return 4.0;
        if (item instanceof net.minecraft.world.item.TridentItem) return 5.0;
        if (item instanceof net.minecraft.world.item.MaceItem)  return 7.0;
        return FALLBACK_WEAPON_BASE;
    }

    /**
     * Public helper for the system panel and tooltips.
     */
    public static double computeDamage(int str, com.soulaimane.sololeveling.data.Rank rank, double weaponBase) {
        double effStr = SoftCap.effective(str, rank);
        return weaponBase
                + Config.SERVER.strDamageCoefficient.get()
                * Math.pow(effStr, Config.SERVER.strDamageExponent.get());
    }
}