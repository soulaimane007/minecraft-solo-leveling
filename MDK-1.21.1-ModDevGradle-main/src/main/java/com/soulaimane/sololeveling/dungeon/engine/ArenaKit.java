package com.soulaimane.sololeveling.dungeon.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Building tools for levels.
 *
 * {@link #box} is a ready-made room (floor, walls, ceiling, lights, an exit stone, places for mobs and a boss).
 * With the small helpers below ({@link #fill}, {@link #clear}, {@link #set}) you can build anything else
 * in your own {@link LevelBuilder}.
 *
 * Blocks are placed with UPDATE_CLIENTS only (no neighbour updates), so nothing pops off or cascades while building.
 */
public final class ArenaKit {
    private ArenaKit() {}

    private static final int FLAGS = Block.UPDATE_CLIENTS;

    /** The look of a room. Suppliers, so the blocks are only looked up when the room is built. */
    public record Style(Supplier<? extends Block> floor, Supplier<? extends Block> wall,
                        Supplier<? extends Block> ceiling, Supplier<? extends Block> light) {

        /** Dark stone crypt, cold light. */
        public static final Style CRYPT = new Style(() -> Blocks.STONE_BRICKS, () -> Blocks.DEEPSLATE_BRICKS,
                () -> Blocks.DEEPSLATE_TILES, () -> Blocks.SEA_LANTERN);

        /** Rough cave, warm light. */
        public static final Style CAVE = new Style(() -> Blocks.COBBLESTONE, () -> Blocks.STONE,
                () -> Blocks.STONE, () -> Blocks.GLOWSTONE);

        /** Nether fortress. */
        public static final Style NETHER = new Style(() -> Blocks.NETHER_BRICKS, () -> Blocks.RED_NETHER_BRICKS,
                () -> Blocks.NETHER_BRICKS, () -> Blocks.SHROOMLIGHT);
    }

    // ─── The stock room ──────────────────────────────────────────────

    /**
     * A closed rectangular room. The players appear at the south end next to an exit stone (a lodestone),
     * the mobs appear on a grid across the room and the boss in the middle.
     *
     * @param width  size east-west in blocks (at least 9)
     * @param depth  size north-south in blocks (at least 13)
     * @param height free height (at least 4)
     */
    public static LevelBuilder box(int width, int depth, int height, Style style) {
        return (world, origin) -> buildBox(world, origin, width, depth, height, style);
    }

    public static LevelLayout buildBox(ServerLevel world, BlockPos origin, int width, int depth, int height, Style style) {
        width = Math.max(9, width);
        depth = Math.max(13, depth);
        height = Math.max(4, height);

        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();

        BlockState floor = style.floor().get().defaultBlockState();
        BlockState wall = style.wall().get().defaultBlockState();
        BlockState ceiling = style.ceiling().get().defaultBlockState();
        BlockState light = style.light().get().defaultBlockState();

        // 1. empty the whole volume (an earlier run may have built something else here)
        clear(world, ox - 3, oy - 3, oz - 3, ox + width + 2, oy + height + 4, oz + depth + 2);

        // 2. foundation, floor, walls, ceiling
        fill(world, ox - 1, oy - 2, oz - 1, ox + width, oy - 1, oz + depth, wall);
        fill(world, ox, oy, oz, ox + width - 1, oy, oz + depth - 1, floor);
        fill(world, ox - 1, oy, oz - 1, ox + width, oy, oz - 1, wall);
        fill(world, ox - 1, oy, oz + depth, ox + width, oy, oz + depth, wall);
        fill(world, ox - 1, oy, oz, ox - 1, oy, oz + depth - 1, wall);
        fill(world, ox + width, oy, oz, ox + width, oy, oz + depth - 1, wall);

        int top = oy + height + 1;
        fill(world, ox - 1, oy + 1, oz - 1, ox + width, top, oz - 1, wall);                 // north wall
        fill(world, ox - 1, oy + 1, oz + depth, ox + width, top, oz + depth, wall);         // south wall
        fill(world, ox - 1, oy + 1, oz, ox - 1, top, oz + depth - 1, wall);                 // west wall
        fill(world, ox + width, oy + 1, oz, ox + width, top, oz + depth - 1, wall);         // east wall
        fill(world, ox, top, oz, ox + width - 1, top, oz + depth - 1, ceiling);             // ceiling

        // 3. lights set into the ceiling, every 6 blocks
        for (int x = ox + 3; x < ox + width - 1; x += 6) {
            for (int z = oz + 3; z < oz + depth - 1; z += 6) {
                set(world, x, top, z, light);
            }
        }

        // 4. the exit stone against the south wall, the players appear a few blocks in front of it
        BlockPos exit = new BlockPos(ox + width / 2, oy + 1, oz + depth - 1);
        set(world, exit.getX(), exit.getY(), exit.getZ(), Blocks.LODESTONE.defaultBlockState());
        Vec3 spawn = new Vec3(exit.getX() + 0.5, oy + 1.0, exit.getZ() - 2.0);

        // 5. where mobs may appear: a grid over the room, never right at the entrance
        List<Vec3> mobSpawns = new ArrayList<>();
        for (int x = ox + 3; x <= ox + width - 4; x += 5) {
            for (int z = oz + 3; z <= oz + depth - 10; z += 5) {
                mobSpawns.add(new Vec3(x + 0.5, oy + 1.0, z + 0.5));
            }
        }
        if (mobSpawns.isEmpty()) {
            mobSpawns.add(new Vec3(ox + width / 2.0, oy + 1.0, oz + 3.5));
        }
        Vec3 boss = new Vec3(ox + width / 2.0, oy + 1.0, oz + depth / 2.0 - 2.0);

        AABB bounds = new AABB(ox - 3, oy - 3, oz - 3, ox + width + 3, oy + height + 5, oz + depth + 3);
        return new LevelLayout(spawn, 180.0f, exit, mobSpawns, boss, bounds);
    }

    // ─── Small helpers for your own builders ─────────────────────────

    /** Fills a box of blocks, both corners included. The corners may be given in any order. */
    public static void fill(ServerLevel world, int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    world.setBlock(pos.set(x, y, z), state, FLAGS);
                }
            }
        }
    }

    /** Replaces a box with air. */
    public static void clear(ServerLevel world, int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(world, x1, y1, z1, x2, y2, z2, Blocks.AIR.defaultBlockState());
    }

    /** One block. */
    public static void set(ServerLevel world, int x, int y, int z, BlockState state) {
        world.setBlock(new BlockPos(x, y, z), state, FLAGS);
    }
}
