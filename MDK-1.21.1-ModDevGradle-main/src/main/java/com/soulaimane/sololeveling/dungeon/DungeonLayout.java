package com.soulaimane.sololeveling.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fixed geometry of the Tutorial Dungeon (all coordinates are in the dungeon dimension).
 *
 * Top view (north is up, the gate / spawn is at the bottom):
 *
 *              . - - - .
 *          /   P     P     \          P = pillar
 *        |  P     [B]     P  |        [B] = boss dais / bonfire (centre)
 *        |      stalls       |        stalls = the two merchants (after the boss)
 *          \   P   [G]   P   /        [G] = gate (lodestone) next to the spawn
 *              ' - - - '
 *
 * After the boss a Rift Stone [R] appears on a small dais on the far (north) side of the bonfire:
 * with a System Key: Replay it starts the dungeon again.
 */
public final class DungeonLayout {
    private DungeonLayout() {}

    public static final int CX = 0;
    public static final int CZ = 0;

    /** Y of the floor blocks; players stand at FLOOR_Y + 1. */
    public static final int FLOOR_Y = 64;

    /** Free interior radius of the arena. */
    public static final int RADIUS = 20;
    /** Thickness of the outer wall ring. */
    public static final int WALL = 2;
    /** Free height above the floor (the ceiling sits right above it). */
    public static final int HEIGHT = 12;

    public static final int PILLAR_RADIUS = 12;
    public static final int PILLAR_COUNT = 8;

    /** Where players appear. */
    public static final Vec3 SPAWN = new Vec3(CX + 0.5, FLOOR_Y + 1.0, CZ + 15.5);
    public static final float SPAWN_YAW = 180f;   // facing north, towards the centre

    /** The Exit Gate: right-click this block to leave the dungeon. */
    public static final BlockPos GATE = new BlockPos(CX, FLOOR_Y + 1, CZ + 18);

    /** Zombie King spawn point and the Bonfire (same spot: the centre of the arena). */
    public static final Vec3 BOSS_SPAWN = new Vec3(CX + 0.5, FLOOR_Y + 1.0, CZ + 0.5);
    public static final BlockPos BONFIRE = new BlockPos(CX, FLOOR_Y + 1, CZ);

    /** Merchant stalls (east / west of the bonfire). Villagers stand at these positions. */
    public static final BlockPos ALCHEMIST_STALL = new BlockPos(CX - 11, FLOOR_Y + 1, CZ + 2);
    public static final BlockPos BLACKSMITH_STALL = new BlockPos(CX + 11, FLOOR_Y + 1, CZ + 2);

    /**
     * The Rift Stone (a crying obsidian block on a small dais, north of the bonfire, after the boss only).
     * Right-click it with a System Key: Replay to start the dungeon again.
     */
    public static final BlockPos RIFT_STONE = new BlockPos(CX, FLOOR_Y + 1, CZ - 14);

    /** Whole arena including walls, ceiling and a bit of margin. */
    public static AABB arenaBox() {
        int r = RADIUS + WALL + 1;
        return new AABB(CX - r, FLOOR_Y - 3, CZ - r, CX + r + 1, FLOOR_Y + HEIGHT + 4, CZ + r + 1);
    }

    /** Interior (walkable air space) only. */
    public static AABB interiorBox() {
        return new AABB(CX - RADIUS, FLOOR_Y + 1, CZ - RADIUS, CX + RADIUS + 1, FLOOR_Y + HEIGHT + 1, CZ + RADIUS + 1);
    }

    /** True if x/z is inside the arena's interior circle (with a margin). */
    public static boolean isInside(double x, double z, double margin) {
        double dx = x - (CX + 0.5);
        double dz = z - (CZ + 0.5);
        double r = RADIUS - margin;
        return dx * dx + dz * dz <= r * r;
    }
}
