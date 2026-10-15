package com.soulaimane.sololeveling.dungeon;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/**
 * Persistent state of the Tutorial Dungeon. Stored in the dungeon dimension's own
 * data folder, so it survives restarts and is deleted together with the dimension.
 */
public class DungeonSavedData extends SavedData {

    public static final String NAME = "sololeveling_tutorial_dungeon";

    /** Bump when the arena geometry changes so old worlds rebuild it. (2 = the Rift Stone dais of the Bonfire camp.) */
    public static final int GEOMETRY_VERSION = 2;

    public DungeonPhase phase = DungeonPhase.IDLE;
    /** Zombies slain in the current run. */
    public int kills = 0;
    /** Generic countdown (ticks) used by BOSS_PENDING and CLEARING. */
    public int timer = 0;
    /** Times the boss has been beaten. */
    public int clears = 0;

    public boolean arenaBuilt = false;
    public boolean hubBuilt = false;
    public int geometryVersion = 0;

    /** The living boss (null when there is none). */
    public UUID bossUuid = null;

    // ─────────────────────────────────────────────────────────────────

    public static SavedData.Factory<DungeonSavedData> factory() {
        return new SavedData.Factory<>(DungeonSavedData::new, DungeonSavedData::load);
    }

    public static DungeonSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static DungeonSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonSavedData d = new DungeonSavedData();
        try {
            d.phase = DungeonPhase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException ex) {
            d.phase = DungeonPhase.IDLE;
        }
        d.kills = tag.getInt("kills");
        d.timer = tag.getInt("timer");
        d.clears = tag.getInt("clears");
        d.arenaBuilt = tag.getBoolean("arena_built");
        d.hubBuilt = tag.getBoolean("hub_built");
        d.geometryVersion = tag.getInt("geometry_version");
        if (tag.hasUUID("boss")) {
            d.bossUuid = tag.getUUID("boss");
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("phase", phase.name());
        tag.putInt("kills", kills);
        tag.putInt("timer", timer);
        tag.putInt("clears", clears);
        tag.putBoolean("arena_built", arenaBuilt);
        tag.putBoolean("hub_built", hubBuilt);
        tag.putInt("geometry_version", geometryVersion);
        if (bossUuid != null) {
            tag.putUUID("boss", bossUuid);
        }
        return tag;
    }

    /** Convenience: change the phase and mark the data dirty. */
    public void setPhase(DungeonPhase newPhase) {
        this.phase = newPhase;
        this.setDirty();
    }
}
