package com.soulaimane.sololeveling.dungeon.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Where things are in one built level.
 *
 * @param spawn      where the players appear
 * @param yaw        the direction they look at (180 = north)
 * @param exit       the exit block (a lodestone placed by the builder: right-click it to leave), or null
 * @param mobSpawns  places where mobs may appear (used by WaveLevel)
 * @param bossSpawn  where the boss appears (used by WaveLevel), or null
 * @param bounds     a box that contains the whole level: the engine removes leftover mobs inside it
 */
public record LevelLayout(Vec3 spawn, float yaw, BlockPos exit, List<Vec3> mobSpawns, Vec3 bossSpawn, AABB bounds) {

    public LevelLayout {
        mobSpawns = mobSpawns == null ? List.of() : List.copyOf(mobSpawns);
    }

    /** A layout with only the essentials; add the rest with the with... methods. */
    public static LevelLayout at(Vec3 spawn, float yaw, AABB bounds) {
        return new LevelLayout(spawn, yaw, null, List.of(), null, bounds);
    }

    public LevelLayout withExit(BlockPos newExit) {
        return new LevelLayout(spawn, yaw, newExit, mobSpawns, bossSpawn, bounds);
    }

    public LevelLayout withMobSpawns(List<Vec3> newSpawns) {
        return new LevelLayout(spawn, yaw, exit, newSpawns, bossSpawn, bounds);
    }

    public LevelLayout withBoss(Vec3 newBossSpawn) {
        return new LevelLayout(spawn, yaw, exit, mobSpawns, newBossSpawn, bounds);
    }
}
