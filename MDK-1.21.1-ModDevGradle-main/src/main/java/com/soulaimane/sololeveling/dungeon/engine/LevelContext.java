package com.soulaimane.sololeveling.dungeon.engine;

import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a {@link DungeonLevel} can see and do while it runs: the players, the mobs, messages, boss bars
 * and a small memory (numbers and flags) that belongs to the current run of the level.
 */
public final class LevelContext {

    private final MinecraftServer server;
    private final ServerLevel world;
    private final DungeonRun run;

    LevelContext(MinecraftServer server, ServerLevel world, DungeonRun run) {
        this.server = server;
        this.world = world;
        this.run = run;
    }

    // ─── Where we are ────────────────────────────────────────────────

    public ServerLevel world() { return world; }

    public DungeonDefinition dungeon() { return run.def; }

    /** 1 for the first level. */
    public int levelNumber() { return run.levelIndex + 1; }

    public int levelCount() { return run.def.levelCount(); }

    public LevelLayout layout() { return run.layout; }

    /** Ticks since the fight began. */
    public long age() { return run.levelAge; }

    /** The players of this run who are alive and in the dungeon (spectators are left out). */
    public List<ServerPlayer> players() {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : run.players) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p.isAlive() && !p.isSpectator() && p.level() == world) {
                out.add(p);
            }
        }
        return out;
    }

    // ─── A small memory for the level ────────────────────────────────

    /** A number stored under {@code key} for this run of the level (0 if never set). */
    public int getInt(String key) {
        return run.ints.getOrDefault(key, 0);
    }

    public void setInt(String key, int value) {
        run.ints.put(key, value);
    }

    /** Adds to a stored number and returns the new value. */
    public int addInt(String key, int delta) {
        return run.ints.merge(key, delta, Integer::sum);
    }

    public boolean getFlag(String key) {
        return run.flags.contains(key);
    }

    public void setFlag(String key, boolean value) {
        if (value) {
            run.flags.add(key);
        } else {
            run.flags.remove(key);
        }
    }

    // ─── Mobs ────────────────────────────────────────────────────────

    /** Spawns a mob in group "mob". */
    public Mob spawn(MobSpec spec, Vec3 pos) {
        return spawn(spec, pos, "mob");
    }

    /**
     * Spawns a mob of this run. {@code group} is a name of your choice ("wave", "boss", "guard"...) used to count
     * and find mobs later.
     * @return the mob, or null when it could not appear (a wall in the way)
     */
    public Mob spawn(MobSpec spec, Vec3 pos, String group) {
        Mob mob = spec.create(world, pos);
        if (mob == null || !world.noCollision(mob)) {
            return null;
        }

        mob.addTag(DungeonEngine.TAG_MOB);
        mob.addTag(DungeonEngine.TAG_RUN_PREFIX + run.runId);
        if (spec.xp() >= 0) {
            mob.addTag(DungeonEngine.TAG_XP_PREFIX + spec.xp());
        }

        ServerPlayer target = nearestPlayer(mob);
        if (target != null) {
            mob.setTarget(target);
        }

        if (!world.addFreshEntity(mob)) {
            return null;
        }
        run.mobs.put(mob.getUUID(), new DungeonRun.TrackedMob(spec, group));
        world.sendParticles(ParticleTypes.SOUL, pos.x, pos.y + 0.2, pos.z, 16, 0.3, 0.6, 0.3, 0.02);
        return mob;
    }

    private ServerPlayer nearestPlayer(Entity from) {
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer p : players()) {
            if (p.isCreative()) continue;                       // like every other monster
            double d = p.distanceToSqr(from);
            if (d < bestDistance) {
                bestDistance = d;
                best = p;
            }
        }
        return best;
    }

    /** How many mobs of this run are alive. */
    public int aliveMobs() {
        return aliveMobs(null);
    }

    /** How many mobs of that group are alive (null = every group). */
    public int aliveMobs(String group) {
        int count = 0;
        for (Map.Entry<UUID, DungeonRun.TrackedMob> entry : run.mobs.entrySet()) {
            if (group != null && !group.equals(entry.getValue().group())) continue;
            if (world.getEntity(entry.getKey()) instanceof LivingEntity living && living.isAlive()) {
                count++;
            }
        }
        return count;
    }

    /** The first living mob of that group, or null. */
    public Mob firstAlive(String group) {
        for (Map.Entry<UUID, DungeonRun.TrackedMob> entry : run.mobs.entrySet()) {
            if (group != null && !group.equals(entry.getValue().group())) continue;
            if (world.getEntity(entry.getKey()) instanceof Mob mob && mob.isAlive()) {
                return mob;
            }
        }
        return null;
    }

    /** The group a mob of this run was spawned in, or null if it is not one of ours. */
    public String groupOf(Entity entity) {
        DungeonRun.TrackedMob tracked = run.mobs.get(entity.getUUID());
        return tracked == null ? null : tracked.group();
    }

    /** Removes every mob of this run, without death, drops or credit. */
    public void discardMobs() {
        run.discardMobs(world, null);
    }

    /** Removes the mobs of one group. */
    public void discardMobs(String group) {
        run.discardMobs(world, group);
    }

    // ─── Messages ────────────────────────────────────────────────────

    /** A big title for every player. Colour codes work ("§c§lBOSS"). */
    public void title(String title, String subtitle) {
        for (ServerPlayer p : players()) {
            SLUtil.title(p, title, subtitle);
        }
    }

    public void chat(String message) {
        for (ServerPlayer p : players()) {
            SLUtil.chat(p, message);
        }
    }

    public void actionBar(String message) {
        for (ServerPlayer p : players()) {
            SLUtil.actionBar(p, message);
        }
    }

    /** A sound for every player, at his own position. */
    public void sound(String soundId, float volume, float pitch) {
        for (ServerPlayer p : players()) {
            SLUtil.playSound(p, soundId, SoundSource.HOSTILE, volume, pitch);
        }
    }

    // ─── Boss bars ───────────────────────────────────────────────────

    /**
     * A bar at the top of the screen for the players of the run. The first call with a {@code key} creates it,
     * the next calls give the same bar back (change it with setName / setProgress).
     */
    public ServerBossEvent bar(String key, Component name, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
        ServerBossEvent existing = run.bars.get(key);
        if (existing != null) {
            return existing;
        }
        ServerBossEvent created = new ServerBossEvent(name, color, overlay);
        run.bars.put(key, created);
        return created;
    }

    public void removeBar(String key) {
        ServerBossEvent bar = run.bars.remove(key);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }
}
