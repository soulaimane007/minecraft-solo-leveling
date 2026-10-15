package com.soulaimane.sololeveling.dungeon;

/** Where the Tutorial Dungeon currently is in its single playthrough. */
public enum DungeonPhase {
    /** Nobody has opened the gate yet. */
    IDLE,
    /** Zombie waves — the kill counter is running. */
    FIGHT,
    /** Kill target reached — short countdown before the Zombie King appears. */
    BOSS_PENDING,
    /** The Zombie King is alive. */
    BOSS,
    /** The boss just died — short pause, then the arena becomes the Bonfire hub. */
    CLEARING,
    /** Peace area: bonfire, merchants, no monsters. */
    CLEARED
}
