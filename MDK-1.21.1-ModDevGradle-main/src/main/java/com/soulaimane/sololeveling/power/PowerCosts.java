package com.soulaimane.sololeveling.power;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.SoloLeveling;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.event.ManaEvents;
import com.soulaimane.sololeveling.event.StaminaEvents;
import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * The one place that decides whether a skill or a magic can be used right now:
 * cooldown, stamina, mana. Both systems call {@link #execute}.
 */
public final class PowerCosts {
    private PowerCosts() {}

    /**
     * @param entry         action bar entry ("skill:..." / "magic:...") used for the cooldown
     * @param stamina       stamina to pay (0 = none)
     * @param mana          mana to pay (0 = none)
     * @param cooldownTicks cooldown that starts after a successful use (0 = none)
     * @param effect        the actual effect; return false to cancel without paying anything
     * @return true if it was used
     */
    public static boolean execute(ServerPlayer p, String entry, float stamina, float mana,
                                  int cooldownTicks, BooleanSupplier effect) {
        int left = PowerCooldowns.remaining(p, entry);
        if (left > 0) {
            SLUtil.actionBar(p, "§7Cooldown: " + String.format(Locale.ROOT, "%.1f", left / 20.0) + "s");
            return false;
        }

        PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        if (stamina > 0f) {
            if (d.exhausted()) {
                SLUtil.actionBar(p, "§c[System] Too exhausted");
                return false;
            }
            if (d.stamina() < stamina) {
                SLUtil.actionBar(p, "§c[System] Not enough stamina (" + Math.round(stamina) + ")");
                return false;
            }
        }
        if (mana > 0f && d.mana() < mana) {
            SLUtil.actionBar(p, "§c[System] Not enough mana (" + Math.round(mana) + ")");
            return false;
        }

        boolean used;
        try {
            used = effect.getAsBoolean();
        } catch (RuntimeException ex) {
            // A bug in a skill or magic must never kick the player (an exception in a packet handler would)
            SoloLeveling.LOGGER.error("The effect of {} failed", entry, ex);
            SLUtil.actionBar(p, "§c[System] That power failed (see the game log).");
            return false;
        }
        if (!used) {
            return false;
        }

        if (stamina > 0f) StaminaEvents.spend(p, stamina);
        if (mana > 0f) ManaEvents.tryConsume(p, mana);
        if (cooldownTicks > 0) {
            PowerCooldowns.start(p, entry, cooldownTicks);
            PowerSync.sendCooldown(p, entry, cooldownTicks);
        }
        return true;
    }
}
