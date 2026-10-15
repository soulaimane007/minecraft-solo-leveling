package com.soulaimane.sololeveling.client;

import com.soulaimane.sololeveling.network.SyncPlayerDataPayload;

/** Client-side mirror of the server's PlayerSystemData (filled by the sync packets). */
public final class ClientSystemDataCache {
    private ClientSystemDataCache() {}

    public static int level = 0;
    public static int xp = 0;
    public static int rankOrdinal = 0;
    public static int statPoints = 0;

    public static int strength = 1;
    public static int agility = 1;
    public static int vitality = 1;
    public static int intelligence = 1;
    public static int sense = 1;
    public static int dexterity = 1;
    public static int durability = 1;

    public static float stamina = 100f;
    public static float maxStamina = 100f;
    public static float mana = 20f;
    public static float maxMana = 20f;
    public static double equipLoad = 0;
    public static double maxEquipLoad = 30;
    public static int loadStateOrdinal = 0;
    public static boolean exhausted = false;

    /** Back to defaults — called when leaving a world so nothing leaks into the next one. */
    public static void reset() {
        level = 0; xp = 0; rankOrdinal = 0; statPoints = 0;
        strength = 1; agility = 1; vitality = 1; intelligence = 1;
        sense = 1; dexterity = 1; durability = 1;
        stamina = 100f; maxStamina = 100f;
        mana = 20f; maxMana = 20f;
        equipLoad = 0; maxEquipLoad = 30;
        loadStateOrdinal = 0;
        exhausted = false;
    }

    public static int getStat(String name) {
        return switch (name.toLowerCase()) {
            case "strength"     -> strength;
            case "agility"      -> agility;
            case "vitality"     -> vitality;
            case "intelligence" -> intelligence;
            case "sense"        -> sense;
            case "dexterity"    -> dexterity;
            case "durability"   -> durability;
            default -> 0;
        };
    }

    /** Optimistic local bump so the UI reacts instantly, before the server syncs back. */
    public static void depositStat(String name, int amount) {
        switch (name.toLowerCase()) {
            case "strength"     -> strength     += amount;
            case "agility"      -> agility      += amount;
            case "vitality"     -> vitality     += amount;
            case "intelligence" -> intelligence += amount;
            case "sense"        -> sense        += amount;
            case "dexterity"    -> dexterity    += amount;
            case "durability"   -> durability   += amount;
        }
    }

    public static void updateFrom(SyncPlayerDataPayload p) {
        level = p.level();
        xp = p.xp();
        rankOrdinal = p.rankOrdinal();
        statPoints = p.statPoints();
        strength     = p.strength();
        agility      = p.agility();
        vitality     = p.vitality();
        intelligence = p.intelligence();
        sense        = p.sense();
        dexterity    = p.dexterity();
        durability   = p.durability();
        stamina = p.stamina();
        mana = p.mana();
        equipLoad = p.equipLoad();
        maxEquipLoad = p.maxEquipLoad();
        loadStateOrdinal = p.loadStateOrdinal();
        exhausted = p.exhausted();
    }

    public static void updateStamina(float s, float max, boolean e) {
        stamina = s;
        maxStamina = max;
        exhausted = e;
    }

    public static void updateMana(float m, float max) {
        mana = m;
        maxMana = max;
    }
}
