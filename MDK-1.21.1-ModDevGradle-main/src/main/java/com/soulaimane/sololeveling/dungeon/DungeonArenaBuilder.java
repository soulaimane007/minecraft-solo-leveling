package com.soulaimane.sololeveling.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static com.soulaimane.sololeveling.dungeon.DungeonLayout.*;

/**
 * Builds the Tutorial Dungeon with plain setBlock calls (no structure files needed):
 *
 *  - {@link #buildCombatArena}: a sealed crypt with a stone floor, deepslate walls and ceiling,
 *    eight pillars, a boss dais in the middle and an exit gate next to the spawn.
 *  - {@link #buildPeaceHub}: the same room turned into the peaceful Bonfire camp - the roof is
 *    gone (night sky), warm lanterns, a campfire plaza with benches, two merchant stalls and the
 *    Rift Stone dais ({@link #buildRiftAltar}) that starts the dungeon again.
 *
 * Everything is deterministic, so rebuilding gives the same result.
 */
public final class DungeonArenaBuilder {
    private DungeonArenaBuilder() {}

    /** UPDATE_CLIENTS only: no neighbour updates, so nothing pops off or cascades while building. */
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    // ═════════════════════════════════════════════════════════════════
    // COMBAT ARENA
    // ═════════════════════════════════════════════════════════════════

    public static void buildCombatArena(ServerLevel level) {
        clearVolume(level);

        final int outer = RADIUS + WALL;
        final int ceilingY = FLOOR_Y + HEIGHT + 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > outer * outer) continue;

                int x = CX + dx;
                int z = CZ + dz;

                // Foundation (so the floor never looks like it floats over the void)
                set(level, pos, x, FLOOR_Y - 2, z, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                set(level, pos, x, FLOOR_Y - 1, z, Blocks.DEEPSLATE_BRICKS.defaultBlockState());

                if (d2 <= RADIUS * RADIUS) {
                    // Interior: floor + ceiling
                    set(level, pos, x, FLOOR_Y, z, floorBlock(x, z));
                    set(level, pos, x, ceilingY, z, Blocks.DEEPSLATE_TILES.defaultBlockState());
                    set(level, pos, x, ceilingY + 1, z, Blocks.DEEPSLATE_TILES.defaultBlockState());
                } else {
                    // Wall ring: from the floor up through the ceiling
                    for (int y = FLOOR_Y; y <= ceilingY + 1; y++) {
                        set(level, pos, x, y, z, wallBlock(x, y, z));
                    }
                }
            }
        }

        buildPillars(level, pos, false);
        buildDais(level, pos);
        buildGate(level, pos);

        for (BlockPos p : lightPositions()) {
            level.setBlock(p, Blocks.SOUL_LANTERN.defaultBlockState(), FLAGS);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // PEACE HUB (after the boss is dead)
    // ═════════════════════════════════════════════════════════════════

    public static void buildPeaceHub(ServerLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final int ceilingY = FLOOR_Y + HEIGHT + 1;

        // 1) Open the roof: night sky over the camp
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                set(level, pos, CX + dx, ceilingY, CZ + dz, Blocks.AIR.defaultBlockState());
                set(level, pos, CX + dx, ceilingY + 1, CZ + dz, Blocks.AIR.defaultBlockState());
            }
        }

        // 2) Pillars become short ruined columns topped with a lantern
        buildPillars(level, pos, true);

        // 3) Warm light instead of cold soul fire
        for (BlockPos p : lightPositions()) {
            level.setBlock(p, Blocks.LANTERN.defaultBlockState(), FLAGS);
        }

        // 4) Bonfire plaza in the centre (replaces the boss dais)
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > 36) continue;
                BlockState floor = d2 >= 25
                        ? Blocks.STONE_BRICKS.defaultBlockState()
                        : Blocks.POLISHED_ANDESITE.defaultBlockState();
                set(level, pos, CX + dx, FLOOR_Y, CZ + dz, floor);
            }
        }
        set(level, pos, CX, FLOOR_Y, CZ, Blocks.COAL_BLOCK.defaultBlockState());
        set(level, pos, CX, FLOOR_Y + 1, CZ, Blocks.CAMPFIRE.defaultBlockState());   // lit by default

        // Benches (stairs whose tall back faces away from the fire) — west, east, north
        placeBench(level, pos, CX - 4, CZ, Direction.WEST);
        placeBench(level, pos, CX + 4, CZ, Direction.EAST);
        placeBench(level, pos, CX, CZ - 4, Direction.NORTH);

        // 5) Two merchant stalls
        buildStall(level, pos, ALCHEMIST_STALL, +1, true);
        buildStall(level, pos, BLACKSMITH_STALL, -1, false);

        // 5b) The Rift Stone (right-click it with a System Key: Replay to play the dungeon again)
        buildRiftAltar(level);

        // 6) Gate stays the same, but swap its cold lantern for a warm one
        level.setBlock(GATE.above(2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), FLAGS);
    }

    // ═════════════════════════════════════════════════════════════════
    // PARTS
    // ═════════════════════════════════════════════════════════════════

    /** Removes everything in (and around) the arena so a rebuild starts from air. */
    private static void clearVolume(ServerLevel level) {
        final int r = RADIUS + WALL + 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;
                for (int y = FLOOR_Y - 3; y <= FLOOR_Y + HEIGHT + 4; y++) {
                    set(level, pos, CX + dx, y, CZ + dz, air);
                }
            }
        }
    }

    private static void buildPillars(ServerLevel level, BlockPos.MutableBlockPos pos, boolean ruined) {
        int topY = ruined ? FLOOR_Y + 6 : FLOOR_Y + HEIGHT;
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int i = 0; i < PILLAR_COUNT; i++) {
            double a = 2 * Math.PI * i / PILLAR_COUNT + Math.PI / PILLAR_COUNT;
            int px = CX + (int) Math.round(Math.cos(a) * PILLAR_RADIUS);
            int pz = CZ + (int) Math.round(Math.sin(a) * PILLAR_RADIUS);

            for (int y = FLOOR_Y + 1; y <= FLOOR_Y + HEIGHT; y++) {
                if (y > topY) {
                    set(level, pos, px, y, pz, air);
                } else if (y == FLOOR_Y + 1 || y == topY) {
                    set(level, pos, px, y, pz, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
                } else {
                    set(level, pos, px, y, pz, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                }
            }
            if (ruined) {
                // a lantern on top of each broken column
                set(level, pos, px, topY + 1, pz, Blocks.LANTERN.defaultBlockState());
            }
        }
    }

    private static void buildDais(ServerLevel level, BlockPos.MutableBlockPos pos) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > 25) continue;
                BlockState s;
                if (d2 == 0) {
                    s = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
                } else if (d2 >= 12 && d2 <= 20 && hash(CX + dx, 1, CZ + dz) < 55) {
                    s = Blocks.GILDED_BLACKSTONE.defaultBlockState();
                } else {
                    s = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                }
                set(level, pos, CX + dx, FLOOR_Y, CZ + dz, s);
            }
        }
    }

    /** Obsidian arch with a lodestone at its foot: right-click the lodestone to leave. */
    private static void buildGate(ServerLevel level, BlockPos.MutableBlockPos pos) {
        int gz = GATE.getZ();
        for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 4; y++) {
            set(level, pos, CX - 2, y, gz, Blocks.OBSIDIAN.defaultBlockState());
            set(level, pos, CX + 2, y, gz, Blocks.OBSIDIAN.defaultBlockState());
        }
        for (int x = CX - 2; x <= CX + 2; x++) {
            boolean accent = (x == CX - 1 || x == CX + 1);
            set(level, pos, x, FLOOR_Y + 4, gz,
                    (accent ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).defaultBlockState());
        }
        level.setBlock(GATE, Blocks.LODESTONE.defaultBlockState(), FLAGS);
        level.setBlock(GATE.above(2), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), FLAGS);
    }

    /**
     * The Rift Stone: a 5x5 dais of blackstone with a block of crying obsidian in the middle (the stone)
     * and a soul lantern on each corner. Part of the camp. It is public because DungeonManager places it
     * again when a camp built by an older version of the mod has no stone yet.
     */
    public static void buildRiftAltar(ServerLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                int x = RIFT_STONE.getX() + dx;
                int z = RIFT_STONE.getZ() + dz;

                set(level, pos, x, FLOOR_Y, z, (edge
                        ? Blocks.POLISHED_BLACKSTONE_BRICKS
                        : Blocks.CHISELED_POLISHED_BLACKSTONE).defaultBlockState());
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 3; y++) {
                    set(level, pos, x, y, z, air);
                }
            }
        }

        level.setBlock(RIFT_STONE, Blocks.CRYING_OBSIDIAN.defaultBlockState(), FLAGS);

        BlockState lantern = Blocks.SOUL_LANTERN.defaultBlockState();
        for (int sx : new int[]{-2, 2}) {
            for (int sz : new int[]{-2, 2}) {
                level.setBlock(RIFT_STONE.offset(sx, 0, sz), lantern, FLAGS);
            }
        }
    }

    private static void placeBench(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int z, Direction outward) {
        set(level, pos, x, FLOOR_Y + 1, z,
                Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, outward));
    }

    /**
     * A small market stall around a villager position.
     * towardCentre = +1 if the bonfire lies to the +x side of the stall, -1 if to the -x side.
     */
    private static void buildStall(ServerLevel level, BlockPos.MutableBlockPos pos, BlockPos npc,
                                   int towardCentre, boolean alchemist) {
        int ax = npc.getX();
        int az = npc.getZ();
        int y0 = FLOOR_Y + 1;
        int front = ax + towardCentre;   // counter column
        int back  = ax - towardCentre;   // shelf column

        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState wool = (alchemist ? Blocks.PURPLE_WOOL : Blocks.GRAY_WOOL).defaultBlockState();

        // Floor of the stall + clear the space (the dais / plaza never reaches this far, but be safe)
        for (int x = ax - 1; x <= ax + 1; x++) {
            for (int z = az - 2; z <= az + 2; z++) {
                set(level, pos, x, FLOOR_Y, z, planks);
                for (int y = y0; y <= y0 + 2; y++) {
                    set(level, pos, x, y, z, Blocks.AIR.defaultBlockState());
                }
                set(level, pos, x, y0 + 3, z, wool);   // roof
            }
        }

        // Corner posts
        for (int x : new int[]{front, back}) {
            for (int z : new int[]{az - 2, az + 2}) {
                for (int y = y0; y <= y0 + 2; y++) {
                    set(level, pos, x, y, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                }
            }
        }

        // Counter: three barrels in front of the merchant
        for (int z = az - 1; z <= az + 1; z++) {
            set(level, pos, front, y0, z, Blocks.BARREL.defaultBlockState());
        }

        if (alchemist) {
            set(level, pos, front, y0 + 1, az - 1, Blocks.BREWING_STAND.defaultBlockState());
            set(level, pos, front, y0 + 1, az + 1, Blocks.POTTED_RED_MUSHROOM.defaultBlockState());
            for (int z = az - 1; z <= az + 1; z++) {
                set(level, pos, back, y0, z, Blocks.BOOKSHELF.defaultBlockState());
                set(level, pos, back, y0 + 1, z, Blocks.BOOKSHELF.defaultBlockState());
            }
        } else {
            set(level, pos, front, y0 + 1, az - 1, Blocks.ANVIL.defaultBlockState());
            set(level, pos, front, y0 + 1, az + 1, Blocks.SMITHING_TABLE.defaultBlockState());
            set(level, pos, back, y0, az - 1, Blocks.BLAST_FURNACE.defaultBlockState());
            set(level, pos, back, y0, az,     Blocks.SMITHING_TABLE.defaultBlockState());
            set(level, pos, back, y0, az + 1, Blocks.BLAST_FURNACE.defaultBlockState());
        }

        // Two hanging lanterns under the roof (either side of the merchant's head)
        BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        set(level, pos, ax, y0 + 2, az - 1, hanging);
        set(level, pos, ax, y0 + 2, az + 1, hanging);
    }

    // ═════════════════════════════════════════════════════════════════
    // LIGHTS
    // ═════════════════════════════════════════════════════════════════

    /** Floor positions of the lanterns (soul lanterns in the arena, normal lanterns in the hub). */
    public static List<BlockPos> lightPositions() {
        List<BlockPos> list = new ArrayList<>();

        // Ring along the wall
        for (int i = 0; i < 16; i++) {
            double a = 2 * Math.PI * i / 16;
            int x = CX + (int) Math.round(Math.cos(a) * (RADIUS - 1));
            int z = CZ + (int) Math.round(Math.sin(a) * (RADIUS - 1));
            if (Math.abs(x - CX) <= 3 && z - CZ >= 14) continue;   // keep the entrance clear
            list.add(new BlockPos(x, FLOOR_Y + 1, z));
        }

        // One next to each pillar, towards the centre
        for (int i = 0; i < PILLAR_COUNT; i++) {
            double a = 2 * Math.PI * i / PILLAR_COUNT + Math.PI / PILLAR_COUNT;
            int x = CX + (int) Math.round(Math.cos(a) * (PILLAR_RADIUS - 2));
            int z = CZ + (int) Math.round(Math.sin(a) * (PILLAR_RADIUS - 2));
            list.add(new BlockPos(x, FLOOR_Y + 1, z));
        }
        return list;
    }

    // ═════════════════════════════════════════════════════════════════
    // BLOCK PALETTES
    // ═════════════════════════════════════════════════════════════════

    private static BlockState floorBlock(int x, int z) {
        int h = hash(x, 0, z);
        if (h < 55) return Blocks.STONE_BRICKS.defaultBlockState();
        if (h < 75) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        if (h < 92) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        return Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    private static BlockState wallBlock(int x, int y, int z) {
        int h = hash(x, y, z);
        if (h < 65) return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        if (h < 85) return Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        return Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    // ═════════════════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════════════════

    private static void set(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState state) {
        pos.set(x, y, z);
        level.setBlock(pos, state, FLAGS);
    }

    /** Deterministic pseudo-random number 0..99 for a position. */
    private static int hash(int x, int y, int z) {
        long h = x * 3129871L ^ z * 116129781L ^ y * 42317861L;
        h = h * h * 42317861L + h * 11L;
        return (int) (((h >> 16) & 0x7FFFFFFFL) % 100L);
    }
}
