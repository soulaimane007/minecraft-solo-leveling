package com.soulaimane.sololeveling.client;

import com.soulaimane.sololeveling.data.EquipmentLoadState;
import com.soulaimane.sololeveling.data.Rank;
import com.soulaimane.sololeveling.data.SoftCap;

public final class StatFormulas {
    private StatFormulas() {}

    private static final double HP_BASE               = 8.0;
    private static final double HP_PER_VIT            = 0.4;   // 5 VIT = 1 heart
    private static final double STAMINA_BASE          = 100.0;
    private static final double STAMINA_PER_DUR       = 0.25;
    private static final double MANA_BASE             = 20.0;
    private static final double MANA_PER_INT          = 0.5;
    private static final double MANA_REGEN_PER_INT    = 0.02;
    private static final double STAMINA_REGEN_BASE    = 25.0;
    private static final double STAMINA_REGEN_PER_AGI = 0.05;
    private static final double ATTACK_SPD_PER_AGI    = 0.005;
    private static final double MOVE_SPD_PER_AGI      = 0.002;
    private static final double EQUIP_BASE            = 30.0;
    private static final double EQUIP_PER_STR         = 0.5;
    private static final double EQUIP_PER_DUR         = 1.5;
    private static final double DR_PER_DUR            = 0.01;
    private static final double DR_CAP                = 0.40;
    private static final double KNOCKBACK_PER_STR     = 0.02;
    private static final double RANGE_PER_SENSE       = 0.05;
    private static final double STR_K = 0.024;
    private static final double STR_EXP               = 1.5;
    private static final double INT_K                 = 0.5;
    private static final double INT_EXP               = 1.5;

    public static double maxHp(int vit, Rank rank) {
        return HP_BASE + SoftCap.effective(vit, rank) * HP_PER_VIT;
    }
    public static double maxStamina(int dur, Rank rank) {
        return STAMINA_BASE + SoftCap.effective(dur, rank) * STAMINA_PER_DUR;
    }
    public static double maxMana(int intel, Rank rank) {
        return MANA_BASE + SoftCap.effective(intel, rank) * MANA_PER_INT;
    }
    public static double manaRegen(int intel, Rank rank) {
        return SoftCap.effective(intel, rank) * MANA_REGEN_PER_INT;
    }
    public static double staminaRegen(int agi, Rank rank, EquipmentLoadState state) {
        double effAgi = SoftCap.effective(agi, rank);
        return (STAMINA_REGEN_BASE + effAgi * STAMINA_REGEN_PER_AGI) * state.staminaRegenMultiplier;
    }
    public static double attackSpeed(int agi, Rank rank) {
        return SoftCap.effective(agi, rank) * ATTACK_SPD_PER_AGI;
    }
    public static double movementSpeed(int agi, Rank rank) {
        return SoftCap.effective(agi, rank) * MOVE_SPD_PER_AGI;
    }
    public static double maxEquipLoad(int str, int dur, Rank rank) {
        return EQUIP_BASE
                + SoftCap.effective(str, rank) * EQUIP_PER_STR
                + SoftCap.effective(dur, rank) * EQUIP_PER_DUR;
    }
    public static double damageReduction(int dur, Rank rank) {
        return Math.min(SoftCap.effective(dur, rank) * DR_PER_DUR, DR_CAP);
    }
    public static double knockback(int str, Rank rank) {
        return SoftCap.effective(str, rank) * KNOCKBACK_PER_STR;
    }
    public static double interactionRange(int sen, Rank rank) {
        return SoftCap.effective(sen, rank) * RANGE_PER_SENSE;
    }

    /** Weapon damage from STR: base + k × effective_str^exp. */
    public static double weaponDamage(int str, Rank rank, double weaponBase) {
        return weaponBase + STR_K * Math.pow(SoftCap.effective(str, rank), STR_EXP);
    }

    /** Magic damage from INT: base + k × effective_int^exp. */
    public static double magicDamage(int intel, Rank rank) {
        return INT_K * Math.pow(SoftCap.effective(intel, rank), INT_EXP);
    }
}