package com.soulaimane.sololeveling.dungeon.engine;

import com.soulaimane.sololeveling.SoloLeveling;
import com.soulaimane.sololeveling.power.PowerIds;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * All the dungeons of the mod. Filled while the mod starts (see {@link ModDungeons}), read by the engine,
 * the Dungeon Key item, the creative tab and the commands.
 */
public final class DungeonRegistry {
    private DungeonRegistry() {}

    private static final Map<String, DungeonDefinition> BY_ID = new LinkedHashMap<>();

    /** Adds a dungeon. Throws if its id is already used. */
    public static DungeonDefinition register(DungeonDefinition dungeon) {
        if (BY_ID.containsKey(dungeon.id())) {
            throw new IllegalStateException("Dungeon registered twice: " + dungeon.id());
        }
        if (dungeon.slot() < 0) {
            dungeon.assignSlot(firstFreeSlot(dungeon));
        }
        BY_ID.put(dungeon.id(), dungeon);
        return dungeon;
    }

    /** Smallest slot not used by another dungeon of the same world. */
    private static int firstFreeSlot(DungeonDefinition dungeon) {
        int slot = 0;
        while (isSlotTaken(dungeon, slot)) {
            slot++;
        }
        return slot;
    }

    private static boolean isSlotTaken(DungeonDefinition dungeon, int slot) {
        for (DungeonDefinition other : BY_ID.values()) {
            if (other.dimension().equals(dungeon.dimension()) && other.slot() == slot) return true;
        }
        return false;
    }

    /** @param id "goblin_cave" or "sololeveling:goblin_cave" */
    public static DungeonDefinition get(String id) {
        return BY_ID.get(PowerIds.normalize(id));
    }

    /** Same as {@link #get} but the id is already complete. */
    public static DungeonDefinition byId(String fullId) {
        return BY_ID.get(fullId);
    }

    public static Collection<DungeonDefinition> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    public static boolean isEmpty() {
        return BY_ID.isEmpty();
    }

    /** Is this world run by the dungeon engine? (The shared world always is.) */
    public static boolean isEngineDimension(ResourceKey<Level> dimension) {
        if (DungeonDefinition.DEFAULT_DIMENSION.equals(dimension)) return true;
        for (DungeonDefinition dungeon : BY_ID.values()) {
            if (dungeon.dimension().equals(dimension)) return true;
        }
        return false;
    }

    /** Every world run by the engine. */
    public static Set<ResourceKey<Level>> dimensions() {
        Set<ResourceKey<Level>> out = new HashSet<>();
        out.add(DungeonDefinition.DEFAULT_DIMENSION);
        for (DungeonDefinition dungeon : BY_ID.values()) {
            out.add(dungeon.dimension());
        }
        return out;
    }

    /** Logs the mistakes that can be seen without running anything. Called once at startup. */
    public static void validate() {
        for (DungeonDefinition dungeon : BY_ID.values()) {
            for (DungeonDefinition other : BY_ID.values()) {
                if (other != dungeon && other.dimension().equals(dungeon.dimension()) && other.slot() == dungeon.slot()) {
                    SoloLeveling.LOGGER.error("Dungeons {} and {} use the same slot {}: their areas overlap",
                            dungeon.id(), other.id(), dungeon.slot());
                }
            }
            for (int i = 0; i < dungeon.levelCount(); i++) {
                if (dungeon.level(i) == null) {
                    SoloLeveling.LOGGER.error("Dungeon {} has a null level at position {}", dungeon.id(), i + 1);
                }
            }
        }
    }
}
