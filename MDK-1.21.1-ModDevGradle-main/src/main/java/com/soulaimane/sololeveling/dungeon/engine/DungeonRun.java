package com.soulaimane.sololeveling.dungeon.engine;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One running dungeon: who is inside, which level, and everything that level keeps in memory.
 * Runs live in memory only (nothing is saved): a restart ends them, and the next gate starts a fresh run.
 */
final class DungeonRun {

    enum Phase {
        /** The level is built; a few seconds before the fight begins. */
        STARTING,
        /** The fight. */
        ACTIVE,
        /** The level is won; a few seconds before the next one. */
        CLEARED,
        /** The last level is won. */
        FINISHED
    }

    /** A mob made by the engine for this run. */
    record TrackedMob(MobSpec spec, String group) {}

    final String runId = UUID.randomUUID().toString().substring(0, 8);
    final DungeonDefinition def;

    final Set<UUID> players = new LinkedHashSet<>();
    int levelIndex;
    Phase phase = Phase.STARTING;
    int phaseTicks;
    long levelAge;
    int emptyTicks;
    boolean skipRequested;
    LevelLayout layout;

    final Map<UUID, TrackedMob> mobs = new LinkedHashMap<>();
    final Map<String, Integer> ints = new HashMap<>();
    final Set<String> flags = new HashSet<>();
    final Map<String, ServerBossEvent> bars = new LinkedHashMap<>();

    DungeonRun(DungeonDefinition def, int levelIndex) {
        this.def = def;
        this.levelIndex = levelIndex;
    }

    DungeonLevel level() {
        return def.level(levelIndex);
    }

    /** Forgets everything the previous level left: mobs, numbers, boss bars. */
    void resetLevelState(ServerLevel world) {
        discardMobs(world, null);
        ints.clear();
        flags.clear();
        removeBars();
        skipRequested = false;
    }

    /**
     * Removes the mobs of this run (no death event, no drops, no kill credit).
     * @param group only the mobs of that group, or null for all of them
     */
    void discardMobs(ServerLevel world, String group) {
        for (Iterator<Map.Entry<UUID, TrackedMob>> it = mobs.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, TrackedMob> entry = it.next();
            if (group != null && !group.equals(entry.getValue().group())) continue;

            Entity entity = world.getEntity(entry.getKey());
            if (entity != null && !entity.isRemoved()) {
                world.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5,
                        entity.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                entity.discard();
            }
            it.remove();
        }

        // Also anything of this run that is not in the list (for example loaded back from a saved chunk)
        if (group == null && layout != null && layout.bounds() != null) {
            String tag = DungeonEngine.TAG_RUN_PREFIX + runId;
            for (Mob mob : world.getEntitiesOfClass(Mob.class, layout.bounds(), m -> m.getTags().contains(tag))) {
                mob.discard();
            }
        }
    }

    void removeBars() {
        for (ServerBossEvent bar : bars.values()) {
            bar.removeAllPlayers();
        }
        bars.clear();
    }
}
