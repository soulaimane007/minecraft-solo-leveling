package com.soulaimane.sololeveling;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class Config {

    public static final ModConfigSpec SPEC;
    public static final Server SERVER;

    static {
        Pair<Server, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = pair.getLeft();
        SPEC = pair.getRight();
    }

    public static class Server {
        // HP
        public final ModConfigSpec.DoubleValue baseHealthPenalty;
        public final ModConfigSpec.DoubleValue healthPerVitality;

        // Damage scaling
        public final ModConfigSpec.DoubleValue strDamageCoefficient;
        public final ModConfigSpec.DoubleValue strDamageExponent;
        public final ModConfigSpec.DoubleValue intMagicCoefficient;
        public final ModConfigSpec.DoubleValue intMagicExponent;

        // Stamina
        public final ModConfigSpec.DoubleValue baseStamina;
        public final ModConfigSpec.DoubleValue staminaRegenBase;
        public final ModConfigSpec.DoubleValue staminaRegenDelaySeconds;
        public final ModConfigSpec.DoubleValue staminaCostMeleeWeightDivisor;
        public final ModConfigSpec.DoubleValue staminaCostBlockMultiplier;
        public final ModConfigSpec.DoubleValue staminaCostSprintPerSecond;
        public final ModConfigSpec.DoubleValue staminaCostJump;
        public final ModConfigSpec.DoubleValue staminaCostBreakBlock;
        public final ModConfigSpec.BooleanValue staminaBreakBlockToggle;
        public final ModConfigSpec.DoubleValue exhaustedExitPercent;

        // Mana
        public final ModConfigSpec.DoubleValue baseMana;
        public final ModConfigSpec.DoubleValue manaPerIntelligence;
        public final ModConfigSpec.DoubleValue manaRegenPerIntelligence;

        // Equip load
        public final ModConfigSpec.DoubleValue equipLoadBase;
        public final ModConfigSpec.DoubleValue equipLoadPerDurability;
        public final ModConfigSpec.DoubleValue equipLoadPerStrength;

        // Dexterity
        public final ModConfigSpec.IntValue startingDexterity;

        // Damage reduction
        public final ModConfigSpec.DoubleValue physicalReductionSoftCap;

        Server(ModConfigSpec.Builder b) {
            b.comment("Solo Leveling — Server Config").push("general");

            b.comment("Baseline values").push("baseline");
            baseHealthPenalty = b.defineInRange("baseHealthPenalty", 12.0, 0.0, 40.0);
            healthPerVitality = b.defineInRange("healthPerVitality", 0.4, 0.0, 5.0); // 5 VIT = 1 heart
            baseStamina = b.defineInRange("baseStamina", 100.0, 1.0, 1000.0);
            baseMana = b.defineInRange("baseMana", 20.0, 0.0, 1000.0);
            startingDexterity = b.defineInRange("startingDexterity", 1, 0, 100);
            b.pop();

            b.comment("Damage scaling — STR contributes weapon damage via k * str^exp").push("damage");
            strDamageCoefficient = b.defineInRange("strDamageCoefficient", 0.024, 0.0, 10.0);
            strDamageExponent = b.defineInRange("strDamageExponent", 1.5, 0.5, 3.0);
            intMagicCoefficient = b.defineInRange("intMagicCoefficient", 0.5, 0.0, 10.0);
            intMagicExponent = b.defineInRange("intMagicExponent", 1.5, 0.5, 3.0);
            b.pop();

            b.comment("Stamina tuning").push("stamina");
            staminaRegenBase = b.defineInRange("staminaRegenBase", 25.0, 0.0, 100.0);
            staminaRegenDelaySeconds = b.defineInRange("staminaRegenDelaySeconds", 0.01, 0.0, 10.0);
            staminaCostMeleeWeightDivisor = b.defineInRange("staminaCostMeleeWeightDivisor", 3.0, 0.1, 20.0);
            staminaCostBlockMultiplier = b.defineInRange("staminaCostBlockMultiplier", 11.1, 0.0, 100.0);
            staminaCostSprintPerSecond = b.defineInRange("staminaCostSprintPerSecond", 4.0, 0.0, 100.0);
            staminaCostJump = b.defineInRange("staminaCostJump", 4.0, 0.0, 100.0);
            staminaCostBreakBlock = b.defineInRange("staminaCostBreakBlock", 1.5, 0.0, 100.0);
            staminaBreakBlockToggle = b.define("staminaBreakBlockToggle", true);
            exhaustedExitPercent = b.defineInRange("exhaustedExitPercent", 0.25, 0.05, 1.0);
            b.pop();

            b.comment("Mana").push("mana");
            manaPerIntelligence = b.defineInRange("manaPerIntelligence", 0.5, 0.0, 10.0);
            manaRegenPerIntelligence = b.defineInRange("manaRegenPerIntelligence", 0.02, 0.0, 1.0);
            b.pop();

            b.comment("Equip Load").push("equip_load");
            equipLoadBase = b.defineInRange("equipLoadBase", 30.0, 0.0, 1000.0);
            equipLoadPerDurability = b.defineInRange("equipLoadPerDurability", 1.5, 0.0, 20.0);
            equipLoadPerStrength = b.defineInRange("equipLoadPerStrength", 0.5, 0.0, 20.0);
            b.pop();

            b.comment("Physical damage reduction").push("dr");
            physicalReductionSoftCap = b.defineInRange("physicalReductionSoftCap", 0.40, 0.0, 0.95);
            b.pop();

            b.pop();
        }
    }
}