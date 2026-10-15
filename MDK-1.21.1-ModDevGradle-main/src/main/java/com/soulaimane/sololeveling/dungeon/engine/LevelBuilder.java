package com.soulaimane.sololeveling.dungeon.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Builds the blocks of one level and says where things are (see {@link LevelLayout}).
 * It is called every time the level starts, so it must clear whatever an earlier run left behind
 * ({@link ArenaKit} does that for you).
 */
@FunctionalInterface
public interface LevelBuilder {

    /**
     * @param world  the dungeon world
     * @param origin the corner of this level's own area (different for every level of every dungeon)
     */
    LevelLayout build(ServerLevel world, BlockPos origin);
}
