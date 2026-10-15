package com.soulaimane.sololeveling.dungeon.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * One level (floor) of a dungeon. A dungeon is a list of levels played one after the other.
 *
 * Most levels can be made with {@link WaveLevel} (kill some mobs, then maybe a boss) and need no code at all.
 * Implement this interface for something special (a puzzle, a parkour, a timed defence...).
 *
 * Life of a level:
 *   1. {@link #build}      the blocks are built and the players are teleported in
 *   2. {@link #onStart}    a few seconds later: the fight begins
 *   3. {@link #tick}       every tick while it is running; {@link #isCleared} is asked right after
 *   4. {@link #onCleared}  when isCleared returned true; the rewards are paid; the next level starts a few seconds later
 *
 * Anything the level needs to remember goes in the {@link LevelContext} (getInt / setInt / getFlag / setFlag):
 * the same level object is shared by every run, so it must not keep state in its own fields.
 */
public interface DungeonLevel {

    /** Shown to the players: "Level 2/3: The Crypt Lord". */
    String name();

    /** Builds the blocks. See {@link LevelBuilder}. */
    LevelLayout build(ServerLevel world, BlockPos origin);

    /** The fight begins. Spawn the first mobs, create the boss bars. */
    default void onStart(LevelContext ctx) {}

    /** Every server tick while the level runs (at least one player is alive inside). */
    default void tick(LevelContext ctx) {}

    /** A mob spawned with {@link LevelContext#spawn} died. */
    default void onMobDeath(LevelContext ctx, LivingEntity dead) {}

    /** Is the level won? Asked every tick right after {@link #tick}. */
    boolean isCleared(LevelContext ctx);

    /** The level was won (before the rewards are paid). */
    default void onCleared(LevelContext ctx) {}

    /** What the players receive for clearing this level. */
    default LevelRewards rewards() {
        return LevelRewards.NONE;
    }

    /** False while the players must not be allowed to leave (for example during a boss fight). */
    default boolean canLeave(LevelContext ctx) {
        return true;
    }
}
