package com.soulaimane.sololeveling.power;

/**
 * What the requirement checks need to know about a player.
 * There is one implementation on the server ({@link ServerPowerView}, real data) and one on the
 * client (ClientPowerView, the numbers the server synced), so the skill tree, the Magic Paper
 * tooltip and the server all use the very same rules.
 */
public interface PowerView {
    int level();

    /** Rank.ordinal(): E = 0 ... S = 5. */
    int rankOrdinal();

    /** "strength", "agility", "vitality", "intelligence", "sense", "dexterity" or "durability". */
    int stat(String name);

    /** Rank of a learned skill (0 = not learned). The id is a full id: "sololeveling:power_strike". */
    int skillRank(String skillId);

    boolean knowsMagic(String magicId);
}
