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
        public final ModConfigSpec.DoubleValue manaRegenBase;
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

        // Skill tree (v0.5.0)
        public final ModConfigSpec.IntValue skillPointsPerLevel;

        // ── Dungeon: access ──
        public final ModConfigSpec.BooleanValue dungeonGiveStartingKey;
        public final ModConfigSpec.IntValue dungeonEntryPotions;
        public final ModConfigSpec.BooleanValue dungeonConsumeKey;
        public final ModConfigSpec.BooleanValue dungeonLockDuringBoss;
        public final ModConfigSpec.IntValue dungeonReplayKeyPrice;

        // ── Dungeon: zombies ──
        public final ModConfigSpec.IntValue dungeonKillTarget;
        public final ModConfigSpec.IntValue dungeonMaxAliveZombies;
        public final ModConfigSpec.DoubleValue dungeonZombieHealth;
        public final ModConfigSpec.DoubleValue dungeonZombieDamage;
        public final ModConfigSpec.IntValue dungeonZombieXp;

        // ── Dungeon: boss ──
        public final ModConfigSpec.DoubleValue dungeonBossHealth;
        public final ModConfigSpec.DoubleValue dungeonBossDamage;
        public final ModConfigSpec.DoubleValue dungeonBossScale;
        public final ModConfigSpec.IntValue dungeonBossXp;
        public final ModConfigSpec.IntValue dungeonClearStatPoints;

        // ── Crystals (currency) ──
        public final ModConfigSpec.IntValue crystalsPerZombieMin;
        public final ModConfigSpec.IntValue crystalsPerZombieMax;
        public final ModConfigSpec.IntValue bossCrystals;
        public final ModConfigSpec.IntValue bossKingCrystals;
        public final ModConfigSpec.DoubleValue overworldZombieCrystalChance;

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

            // The section is called "stamina_balance" (it used to be "stamina") on purpose: NeoForge keeps the values
            // already written in an existing sololeveling-server.toml, so the old section with its too generous
            // numbers (25/s, no delay) is dropped and the balanced defaults below apply by themselves.
            b.comment("Stamina tuning (balance pass).",
                            "A melee swing costs 16.7 x item weight / staminaCostMeleeWeightDivisor",
                            "(stone sword = 12.5, so about 8 swings empty a full bar).",
                            "Nothing regenerates until staminaRegenDelaySeconds have passed without spending stamina.")
                    .push("stamina_balance");
            staminaRegenBase = b.comment("Stamina per second once regeneration starts (before the agility bonus and the equip-load multiplier).")
                    .defineInRange("staminaRegenBase", 15.0, 0.0, 100.0);
            // The key is called "regenDelaySeconds" (it was "staminaRegenDelaySeconds") on purpose: NeoForge keeps the value
            // already written in an existing sololeveling-server.toml, so an old 1.0 would hide this default.
            // A new key name makes every world use the default below.
            staminaRegenDelaySeconds = b.comment("Seconds without spending stamina before it starts to regenerate. 0 = at once.")
                    .defineInRange("regenDelaySeconds", 0.0, 0.0, 10.0);
            staminaCostMeleeWeightDivisor = b.comment("Higher = cheaper melee swings.")
                    .defineInRange("staminaCostMeleeWeightDivisor", 4.0, 0.1, 20.0);
            staminaCostBlockMultiplier = b.defineInRange("staminaCostBlockMultiplier", 11.1, 0.0, 100.0);
            staminaCostSprintPerSecond = b.defineInRange("staminaCostSprintPerSecond", 4.0, 0.0, 100.0);
            staminaCostJump = b.defineInRange("staminaCostJump", 4.0, 0.0, 100.0);
            staminaCostBreakBlock = b.defineInRange("staminaCostBreakBlock", 0.5, 0.0, 100.0);
            staminaBreakBlockToggle = b.define("staminaBreakBlockToggle", true);
            exhaustedExitPercent = b.comment("Share of the bar you must have again before you can act after being exhausted.")
                    .defineInRange("exhaustedExitPercent", 0.25, 0.05, 1.0);
            b.pop();

            b.comment("Mana").push("mana");
            manaRegenBase = b.comment("Mana regenerated per second before the Intelligence bonus.")
                    .defineInRange("manaRegenBase", 1.0, 0.0, 100.0);
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

            b.comment("Skill tree").push("skills");
            skillPointsPerLevel = b.comment("Skill Points earned for every level of the player (Lv.10 = 10 x this).",
                            "Skill Points are spent in the skill tree (key J). Stat points (key K) are a separate currency.",
                            "Points are counted from the level, so changing this number is safe: nothing already learned is lost.")
                    .defineInRange("skillPointsPerLevel", 1, 0, 20);
            b.pop();

            b.comment("Tutorial Dungeon — access").push("dungeon_access");
            dungeonGiveStartingKey = b.comment("Give every player a starter kit (1 System Key: Basic + 3 Health Potions) the first time they join.",
                            "Keys are one per person and never stack. A key is an ordinary item: if you die with it, it drops.",
                            "If you die inside the dungeon and carry no System Key: Basic when you respawn, a new one is returned to you.")
                    .define("giveStartingKey", true);
            dungeonEntryPotions = b.comment("Every time you walk into the dungeon (zombie and boss phases) the System tops your Health Potions up to this number.",
                            "It only fills the gap: you never end up with more than this from the dungeon, and it gives nothing if you already carry this many.",
                            "0 turns the supply off.")
                    .defineInRange("entryPotions", 3, 0, 16);
            dungeonConsumeKey = b.comment("Consume the System Key: Basic when entering a dungeon that is not cleared yet.")
                    .define("consumeKey", false);
            dungeonLockDuringBoss = b.comment("Players cannot leave with the key while the boss fight is running.")
                    .define("lockDuringBoss", true);
            dungeonReplayKeyPrice = b.comment("Zombie King Crystals the Blacksmith asks for one System Key: Replay.",
                            "The key lets you start the Tutorial Dungeon again from the Rift Stone in the Bonfire camp.",
                            "The price is written into a merchant when it is created, so a change shows up the next time the camp is built",
                            "(leave and re-enter the dungeon after a rebuild, or use /sl dungeon rebuild).")
                    .defineInRange("replayKeyPrice", 2, 1, 64);
            b.pop();

            b.comment("Tutorial Dungeon — zombies").push("dungeon_zombies");
            dungeonKillTarget = b.comment("Zombies that must be slain before the boss appears.")
                    .defineInRange("killTarget", 15, 1, 500);
            dungeonMaxAliveZombies = b.comment("Maximum dungeon zombies alive at the same time (+2 for every extra player inside).")
                    .defineInRange("maxAliveZombies", 4, 1, 40);
            dungeonZombieHealth = b.defineInRange("zombieHealth", 12.0, 1.0, 1000.0);
            dungeonZombieDamage = b.defineInRange("zombieDamage", 2.0, 0.0, 100.0);
            dungeonZombieXp = b.comment("System XP per dungeon zombie.")
                    .defineInRange("zombieXp", 10, 0, 100000);
            b.pop();

            b.comment("Tutorial Dungeon — boss (Zombie King)").push("dungeon_boss");
            dungeonBossHealth = b.defineInRange("bossHealth", 120.0, 10.0, 100000.0);
            dungeonBossDamage = b.defineInRange("bossDamage", 4.0, 0.0, 1000.0);
            dungeonBossScale = b.comment("Visual and hitbox size multiplier of the boss.")
                    .defineInRange("bossScale", 2.4, 1.0, 6.0);
            // Renamed from "bossXp" (old default 400) so that existing configs pick up the new default.
            dungeonBossXp = b.comment("System XP given to every player in the dungeon when the boss dies.",
                            "1200 takes a level 1 player to level 4 (level 1 -> 4 needs 175 + 350 + 575 = 1100 XP).")
                    .defineInRange("bossXpReward", 1200, 0, 1000000);
            dungeonClearStatPoints = b.comment("Bonus stat points for clearing the dungeon.")
                    .defineInRange("clearStatPoints", 5, 0, 1000);
            b.pop();

            b.comment("Crystals — the currency of the Bonfire merchants").push("crystals");
            crystalsPerZombieMin = b.defineInRange("perDungeonZombieMin", 1, 0, 64);
            crystalsPerZombieMax = b.defineInRange("perDungeonZombieMax", 2, 0, 64);
            bossCrystals = b.comment("Zombie Crystals dropped by the boss.")
                    .defineInRange("bossZombieCrystals", 15, 0, 1000);
            bossKingCrystals = b.comment("Zombie King Crystals dropped by the boss.")
                    .defineInRange("bossKingCrystals", 3, 0, 64);
            overworldZombieCrystalChance = b.comment("Chance that a normal zombie (any dimension) killed by a player drops a Zombie Crystal. 0 disables.")
                    .defineInRange("overworldZombieChance", 0.25, 0.0, 1.0);
            b.pop();

            b.pop();
        }
    }
}