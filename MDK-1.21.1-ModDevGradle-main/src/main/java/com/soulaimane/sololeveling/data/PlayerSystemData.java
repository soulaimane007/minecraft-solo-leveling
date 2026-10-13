package com.soulaimane.sololeveling.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PlayerSystemData(
        int level, int xp, Rank rank, int statPoints,
        int strength, int agility, int vitality, int intelligence, int sense,
        int dexterity, int durability,
        float stamina, float mana, int staminaDelayTicks, boolean exhausted,
        double currentEquipLoad
) {
    public static final int MAX_LEVEL = 100;
    public static final int STAT_POINTS_PER_LEVEL = 10;

    public static final int XP_BASE_COST        = 50;
    public static final int XP_LINEAR_GROWTH    = 100;
    public static final int XP_QUADRATIC_GROWTH = 25;

    public static final float MAX_STAMINA = 100f;
    public static final float MAX_MANA = 100f;

    public static int xpForNextLevel(int level) {
        long cost = XP_BASE_COST
                + ((long) level * XP_LINEAR_GROWTH)
                + ((long) level * level * XP_QUADRATIC_GROWTH);
        return cost > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cost;
    }

    public static Rank getRankForLevel(int level) {
        if (level >= 75) return Rank.S;
        if (level >= 60) return Rank.A;
        if (level >= 45) return Rank.B;
        if (level >= 30) return Rank.C;
        if (level >= 15) return Rank.D;
        return Rank.E;
    }

    public static final PlayerSystemData DEFAULT = new PlayerSystemData(
            0, 0, Rank.E, 0,
            1, 1, 1, 1, 1, 1, 1,
            MAX_STAMINA, MAX_MANA, 0, false, 0.0
    );

    // ─── Codec ────────────────────────────────────────────────────────

    private record PersistentData(
            int level, int xp, Rank rank, int statPoints,
            int strength, int agility, int vitality, int intelligence, int sense,
            int dexterity, int durability,
            float stamina, float mana, double currentEquipLoad
    ) {
        static final Codec<PersistentData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("level").forGetter(PersistentData::level),
                Codec.INT.fieldOf("xp").forGetter(PersistentData::xp),
                Rank.CODEC.fieldOf("rank").forGetter(PersistentData::rank),
                Codec.INT.fieldOf("stat_points").forGetter(PersistentData::statPoints),
                Codec.INT.fieldOf("strength").forGetter(PersistentData::strength),
                Codec.INT.fieldOf("agility").forGetter(PersistentData::agility),
                Codec.INT.fieldOf("vitality").forGetter(PersistentData::vitality),
                Codec.INT.fieldOf("intelligence").forGetter(PersistentData::intelligence),
                Codec.INT.fieldOf("sense").forGetter(PersistentData::sense),
                Codec.INT.fieldOf("dexterity").forGetter(PersistentData::dexterity),
                Codec.INT.fieldOf("durability").forGetter(PersistentData::durability),
                Codec.FLOAT.fieldOf("stamina").forGetter(PersistentData::stamina),
                Codec.FLOAT.fieldOf("mana").forGetter(PersistentData::mana),
                Codec.DOUBLE.fieldOf("current_equip_load").forGetter(PersistentData::currentEquipLoad)
        ).apply(i, PersistentData::new));
    }

    public static final Codec<PlayerSystemData> CODEC = PersistentData.CODEC.xmap(
            p -> new PlayerSystemData(
                    p.level(), p.xp(), p.rank(), p.statPoints(),
                    p.strength(), p.agility(), p.vitality(), p.intelligence(), p.sense(),
                    p.dexterity(), p.durability(),
                    p.stamina(), p.mana(), 0, false, p.currentEquipLoad()
            ),
            d -> new PersistentData(
                    d.level(), d.xp(), d.rank(), d.statPoints(),
                    d.strength(), d.agility(), d.vitality(), d.intelligence(), d.sense(),
                    d.dexterity(), d.durability(),
                    d.stamina(), d.mana(), d.currentEquipLoad()
            )
    );

    // ─── With-X helpers ──────────────────────────────────────────────

    public PlayerSystemData withLevel(int v) {
        return new PlayerSystemData(v, xp, getRankForLevel(v), statPoints,
                strength, agility, vitality, intelligence, sense, dexterity, durability,
                stamina, mana, staminaDelayTicks, exhausted, currentEquipLoad);
    }

    public PlayerSystemData withXp(int v) {
        return new PlayerSystemData(level, v, rank, statPoints,
                strength, agility, vitality, intelligence, sense, dexterity, durability,
                stamina, mana, staminaDelayTicks, exhausted, currentEquipLoad);
    }

    public PlayerSystemData withStatPoints(int v) {
        return new PlayerSystemData(level, xp, rank, v,
                strength, agility, vitality, intelligence, sense, dexterity, durability,
                stamina, mana, staminaDelayTicks, exhausted, currentEquipLoad);
    }

    public PlayerSystemData withStat(String name, int v) {
        return switch (name.toLowerCase()) {
            case "strength" -> new PlayerSystemData(level, xp, rank, statPoints, v, agility, vitality, intelligence, sense, dexterity, durability, stamina, mana, staminaDelayTicks, exhausted, currentEquipLoad);
            case "agility" -> new PlayerSystemData(level, xp, rank, statPoints, strength, v, vitality, intelligence, sense, dexterity, durability, stamina, mana, staminaDelayTicks, exhausted, currentEquipLoad);
            case "vitality" -> new PlayerSystemData(level, xp, rank, statPoints, strength, agility, v, intelligence, sense, dexterity, durability, stamina, mana, staminaDelayTicks, exhausted, currentEquipLoad);
            case "intelligence" -> new PlayerSystemData(level, xp, rank, statPoints, strength, agility, vitality, v, sense, dexterity, durability, stamina, mana, staminaDelayTicks, exhausted, currentEquipLoad);
            case "sense" -> new PlayerSystemData(level, xp, rank, statPoints, strength, agility, vitality, intelligence, v, dexterity, durability, stamina, mana, staminaDelayTicks, exhausted, currentEquipLoad);
            case "dexterity" -> new PlayerSystemData(level, xp, rank, statPoints, strength, agility, vitality, intelligence, sense, v, durability, stamina, mana, staminaDelayTicks, exhausted, currentEquipLoad);
            case "durability" -> new PlayerSystemData(level, xp, rank, statPoints, strength, agility, vitality, intelligence, sense, dexterity, v, stamina, mana, staminaDelayTicks, exhausted, currentEquipLoad);
            default -> this;
        };
    }

    public PlayerSystemData withStamina(float v) {
        return new PlayerSystemData(level, xp, rank, statPoints, strength, agility, vitality, intelligence, sense, dexterity, durability, v, mana, staminaDelayTicks, exhausted, currentEquipLoad);
    }

    public PlayerSystemData withMana(float v) {
        return new PlayerSystemData(level, xp, rank, statPoints, strength, agility, vitality, intelligence, sense, dexterity, durability, stamina, v, staminaDelayTicks, exhausted, currentEquipLoad);
    }

    public PlayerSystemData withDelayTicks(int v) {
        return new PlayerSystemData(level, xp, rank, statPoints, strength, agility, vitality, intelligence, sense, dexterity, durability, stamina, mana, v, exhausted, currentEquipLoad);
    }

    public PlayerSystemData withExhausted(boolean v) {
        return new PlayerSystemData(level, xp, rank, statPoints, strength, agility, vitality, intelligence, sense, dexterity, durability, stamina, mana, staminaDelayTicks, v, currentEquipLoad);
    }

    public PlayerSystemData withEquipLoad(double v) {
        return new PlayerSystemData(level, xp, rank, statPoints, strength, agility, vitality, intelligence, sense, dexterity, durability, stamina, mana, staminaDelayTicks, exhausted, v);
    }

    public PlayerSystemData addXp(int amount) {
        if (amount <= 0) return this;
        int nl = level, nx = xp + amount, ns = statPoints;
        while (nl < MAX_LEVEL) {
            int need = xpForNextLevel(nl);
            if (nx < need) break;
            nx -= need; nl++; ns += STAT_POINTS_PER_LEVEL;
        }
        if (nl >= MAX_LEVEL) { nl = MAX_LEVEL; nx = 0; }
        return new PlayerSystemData(nl, nx, getRankForLevel(nl), ns,
                strength, agility, vitality, intelligence, sense, dexterity, durability,
                stamina, mana, staminaDelayTicks, exhausted, currentEquipLoad);
    }

    public int getStatValue(String name) {
        return switch (name.toLowerCase()) {
            case "strength" -> strength;
            case "agility" -> agility;
            case "vitality" -> vitality;
            case "intelligence" -> intelligence;
            case "sense" -> sense;
            case "dexterity" -> dexterity;
            case "durability" -> durability;
            default -> 0;
        };
    }
}