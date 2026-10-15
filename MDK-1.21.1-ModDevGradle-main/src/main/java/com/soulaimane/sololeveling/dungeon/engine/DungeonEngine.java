package com.soulaimane.sololeveling.dungeon.engine;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.SoloLeveling;
import com.soulaimane.sololeveling.data.PlayerDungeonData;
import com.soulaimane.sololeveling.data.PlayerDungeonProgress;
import com.soulaimane.sololeveling.dungeon.DungeonManager;
import com.soulaimane.sololeveling.power.Requirements;
import com.soulaimane.sololeveling.power.ServerPowerView;
import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The dungeon engine: opens the gates, runs the levels one after the other, pays the rewards, brings the players home.
 *
 * <pre>
 *   gate opens ──▶ STARTING (3 s) ──▶ ACTIVE (the level's fight) ──▶ CLEARED (5 s) ──▶ next level ...
 *                                                                          └── last level ──▶ FINISHED ──▶ everybody leaves
 * </pre>
 *
 * Runs are kept in memory only. If every player dies or leaves, the run is dropped after 5 seconds and the next gate
 * builds the level again from scratch (a boss is never found half dead). Only what a player cleared is saved
 * (PlayerDungeonProgress). Everything is driven by {@link #tick}, called once per server tick.
 *
 * The Tutorial Dungeon is separate (DungeonManager) and is not touched by the engine.
 */
public final class DungeonEngine {
    private DungeonEngine() {}

    // Tags written on every mob made by the engine
    public static final String TAG_MOB = "sl_dg";
    public static final String TAG_RUN_PREFIX = "sl_dg_run:";
    public static final String TAG_XP_PREFIX = "sl_dg_xp:";

    private static final int START_DELAY = 60;          // between arriving and the fight
    private static final int NEXT_LEVEL_DELAY = 100;    // between a cleared level and the next one
    private static final int FINISH_DELAY = 300;        // between the last level and being taken out
    private static final int EMPTY_GRACE = 100;         // nobody left in the run: it is dropped after this
    private static final int HOUSEKEEPING_EVERY = 20;

    private static final Map<String, DungeonRun> RUNS = new LinkedHashMap<>();
    private static final Map<UUID, DungeonRun> BY_PLAYER = new HashMap<>();
    private static int tickErrors = 0;

    // ═════════════════════════════════════════════════════════════════
    // Questions
    // ═════════════════════════════════════════════════════════════════

    /** Is this player inside a dungeon run? */
    public static boolean isInside(Entity entity) {
        return entity instanceof ServerPlayer p && BY_PLAYER.containsKey(p.getUUID());
    }

    /** The dungeon this player is in, or null. */
    public static DungeonDefinition dungeonOf(ServerPlayer player) {
        DungeonRun run = BY_PLAYER.get(player.getUUID());
        return run == null ? null : run.def;
    }

    private static DungeonRun findRun(String runId) {
        for (DungeonRun run : RUNS.values()) {
            if (run.runId.equals(runId)) return run;
        }
        return null;
    }

    private static String runIdOf(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith(TAG_RUN_PREFIX)) return tag.substring(TAG_RUN_PREFIX.length());
        }
        return null;
    }

    // ═════════════════════════════════════════════════════════════════
    // Entering
    // ═════════════════════════════════════════════════════════════════

    /**
     * Opens the gate for a player.
     *
     * @param keyStack       the key that was used (used up if the dungeon says so), or null (commands)
     * @param requestedLevel 1-based level written on the key, 0 = no wish. Players cannot skip levels they have not cleared.
     * @param admin          true = ignore the entry requirements and the progress rules (commands)
     * @return true if the player went in
     */
    public static boolean enter(ServerPlayer player, DungeonDefinition def, ItemStack keyStack,
                                int requestedLevel, boolean admin) {
        MinecraftServer server = player.getServer();
        if (server == null || !player.isAlive() || player.isSpectator()) return false;

        if (BY_PLAYER.containsKey(player.getUUID())) {
            SLUtil.actionBar(player, "§c[System] You are already inside a dungeon.");
            return false;
        }
        if (DungeonManager.isInDungeon(player) || DungeonRegistry.isEngineDimension(player.level().dimension())) {
            SLUtil.actionBar(player, "§c[System] Leave this place before you open another gate.");
            return false;
        }

        ServerLevel world = server.getLevel(def.dimension());
        if (world == null) {
            SLUtil.chat(player, "§c[System] The dungeon world is missing. Check that data/sololeveling/dimension/"
                    + def.dimension().location().getPath() + ".json was loaded (see the game log).");
            return false;
        }

        if (!admin) {
            Requirements need = def.entryRequirements();
            ServerPowerView view = new ServerPowerView(player);
            if (!need.isMet(view)) {
                SLUtil.chat(player, "§c[System] You cannot enter §e" + def.displayName().getString() + " §cyet. You need:");
                for (Requirements.Line line : need.lines(view)) {
                    SLUtil.chat(player, (line.met() ? "  §a✔ " : "  §c✘ ") + line.text());
                }
                return false;
            }
            if (!def.allowsPeaceful() && world.getDifficulty() == Difficulty.PEACEFUL) {
                SLUtil.chat(player, "§c[System] Hostile mobs cannot exist on Peaceful difficulty. "
                        + "Raise the difficulty to open the gate.");
                return false;
            }
        }

        // Join the run that is going, or start a new one
        DungeonRun run = RUNS.get(def.id());
        if (run == null) {
            int startIndex = startLevelIndex(player, def, requestedLevel, admin);
            run = new DungeonRun(def, startIndex);
            try {
                prepareLevel(world, run);
            } catch (RuntimeException ex) {
                SoloLeveling.LOGGER.error("Could not build level {} of dungeon {}", startIndex + 1, def.id(), ex);
                run.resetLevelState(world);
                SLUtil.chat(player, "§c[System] The dungeon could not be built (see the server log).");
                return false;
            }
            RUNS.put(def.id(), run);
        }

        // Remember the way back, use up the key
        ServerLevel from = player.serverLevel();
        PlayerDungeonData data = player.getData(ModAttachments.PLAYER_DUNGEON_DATA);
        player.setData(ModAttachments.PLAYER_DUNGEON_DATA, data.withReturn(
                from.dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));

        if (keyStack != null && !keyStack.isEmpty() && def.consumesKey() && !player.isCreative()) {
            keyStack.shrink(1);
        }

        run.players.add(player.getUUID());
        BY_PLAYER.put(player.getUUID(), run);

        from.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
                40, 0.4, 0.8, 0.4, 0.3);
        SLUtil.playSound(player, "minecraft:entity.enderman.teleport", SoundSource.PLAYERS, 1.0f, 0.6f);

        LevelLayout layout = run.layout;
        Entity moved = player.changeDimension(new DimensionTransition(
                world, layout.spawn(), Vec3.ZERO, layout.yaw(), 0.0f, DimensionTransition.DO_NOTHING));
        if (moved == null) {
            removeMember(player, run);
            return false;
        }
        return true;
    }

    /** 0-based index of the level a new run starts at. */
    private static int startLevelIndex(ServerPlayer player, DungeonDefinition def, int requested, boolean admin) {
        int count = def.levelCount();
        int cleared = player.getData(ModAttachments.PLAYER_DUNGEON_PROGRESS).clearedLevels(def.id());
        int reachable = Math.min(cleared + 1, count);              // the highest level this player may start at

        int wanted;
        if (requested > 0) {
            wanted = admin ? requested : Math.min(requested, reachable);
            if (!admin && requested > reachable) {
                SLUtil.chat(player, "§6[System] §7You have not cleared the levels before level " + requested
                        + ". Starting at level " + reachable + ".");
            }
        } else if (def.resumesFromProgress()) {
            wanted = cleared >= count ? 1 : cleared + 1;
        } else {
            wanted = 1;
        }
        return Mth.clamp(wanted, 1, count) - 1;
    }

    /** Builds the blocks of the current level and resets everything the previous level left. */
    private static void prepareLevel(ServerLevel world, DungeonRun run) {
        run.resetLevelState(world);
        BlockPos origin = run.def.levelOrigin(run.levelIndex);
        LevelLayout layout = run.level().build(world, origin);
        if (layout == null) {
            throw new IllegalStateException("The level '" + run.level().name() + "' returned no LevelLayout");
        }
        run.layout = layout;
        run.phase = DungeonRun.Phase.STARTING;
        run.phaseTicks = START_DELAY;
        run.levelAge = 0;
    }

    /** The player arrived in the dungeon world (called from the dimension change event). */
    public static void onArrived(ServerPlayer player) {
        DungeonRun run = BY_PLAYER.get(player.getUUID());
        if (run == null) return;
        announceLevel(player, run);

        SLUtil.chat(player, "§8Leave at any time (when the level allows it): right-click the lodestone near the entrance.");
        try {
            run.def.runOnEnter(player);
        } catch (RuntimeException ex) {
            SoloLeveling.LOGGER.error("The onEnter hook of dungeon {} failed", run.def.id(), ex);
        }
    }

    private static void announceLevel(ServerPlayer player, DungeonRun run) {
        SLUtil.title(player,
                run.def.displayName().copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal("§7Level " + (run.levelIndex + 1) + " / " + run.def.levelCount()
                        + "  -  " + run.level().name()),
                10, 60, 20);
    }

    // ═════════════════════════════════════════════════════════════════
    // Leaving
    // ═════════════════════════════════════════════════════════════════

    /**
     * Takes the player out of the dungeon.
     * @param force true = ignore "the exit is sealed" (commands)
     * @return true if the player left
     */
    public static boolean leave(ServerPlayer player, boolean force) {
        MinecraftServer server = player.getServer();
        if (server == null || !player.isAlive()) return false;

        DungeonRun run = BY_PLAYER.get(player.getUUID());
        if (run == null) {
            // In a dungeon world without a run (a left-over from a restart): just send him home
            if (DungeonRegistry.isEngineDimension(player.level().dimension())) {
                teleportBack(player);
                return true;
            }
            return false;
        }

        if (!force && !player.isCreative() && run.phase == DungeonRun.Phase.ACTIVE) {
            boolean allowed = true;
            try {
                allowed = run.level().canLeave(new LevelContext(server, player.serverLevel(), run));
            } catch (RuntimeException ex) {
                SoloLeveling.LOGGER.error("canLeave of level {} of dungeon {} failed", run.level().name(), run.def.id(), ex);
            }
            if (!allowed) {
                SLUtil.actionBar(player, "§c[System] The exit is sealed while this fight lasts.");
                return false;
            }
        }

        removeMember(player, run);
        teleportBack(player);
        return true;
    }

    /** Sends the player to where he opened the gate (or the world spawn). Does not touch the run. */
    private static void teleportBack(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        PlayerDungeonData data = player.getData(ModAttachments.PLAYER_DUNGEON_DATA);
        ServerLevel dest = null;
        Vec3 pos = Vec3.ZERO;
        float yaw = 0.0f;
        float pitch = 0.0f;

        if (data.hasReturn()) {
            ResourceLocation id = ResourceLocation.tryParse(data.returnDimension());
            if (id != null) {
                ServerLevel candidate = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
                if (candidate != null
                        && !DungeonRegistry.isEngineDimension(candidate.dimension())
                        && candidate.dimension() != DungeonManager.DIMENSION) {
                    dest = candidate;
                    pos = new Vec3(data.returnX(), data.returnY(), data.returnZ());
                    yaw = data.returnYaw();
                    pitch = data.returnPitch();
                }
            }
        }
        if (dest == null) {
            dest = server.overworld();
            BlockPos spawn = dest.getSharedSpawnPos();
            int y = dest.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
            pos = new Vec3(spawn.getX() + 0.5, y, spawn.getZ() + 0.5);
        }

        ServerLevel here = player.serverLevel();
        here.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
                40, 0.4, 0.8, 0.4, 0.3);
        SLUtil.playSound(player, "minecraft:entity.enderman.teleport", SoundSource.PLAYERS, 1.0f, 1.2f);

        player.setData(ModAttachments.PLAYER_DUNGEON_DATA, data.withoutReturn());
        player.changeDimension(new DimensionTransition(
                dest, pos, Vec3.ZERO, yaw, pitch, DimensionTransition.DO_NOTHING));
    }

    private static void removeMember(ServerPlayer player, DungeonRun run) {
        run.players.remove(player.getUUID());
        BY_PLAYER.remove(player.getUUID(), run);
        for (ServerBossEvent bar : run.bars.values()) {
            bar.removePlayer(player);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // The tick
    // ═════════════════════════════════════════════════════════════════

    /** Once per server tick. */
    public static void tick(MinecraftServer server) {
        if (!RUNS.isEmpty()) {
            for (DungeonRun run : new ArrayList<>(RUNS.values())) {
                try {
                    tickRun(server, run);
                } catch (RuntimeException ex) {
                    // A bug in a dungeon must never take the whole server down: stop that run and send the players home
                    if (tickErrors++ % 50 == 0) {
                        SoloLeveling.LOGGER.error("The dungeon {} failed and was stopped ({} errors so far)",
                                run.def.id(), tickErrors, ex);
                    }
                    abort(server, run, "§c[System] A bug stopped the dungeon (see the server log).");
                }
            }
        }
        if (server.getTickCount() % HOUSEKEEPING_EVERY == 0) {
            rescueStrays(server);
        }
    }

    private static void tickRun(MinecraftServer server, DungeonRun run) {
        ServerLevel world = server.getLevel(run.def.dimension());
        if (world == null) {
            destroy(server, run);
            return;
        }

        // Members who logged out, died or left the world by other means are no longer part of the run
        List<ServerPlayer> gone = new ArrayList<>();
        for (UUID id : run.players) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || !p.isAlive() || p.level() != world) {
                if (p != null) gone.add(p);
                BY_PLAYER.remove(id, run);
            }
        }
        run.players.removeIf(id -> BY_PLAYER.get(id) != run);
        for (ServerPlayer p : gone) {
            for (ServerBossEvent bar : run.bars.values()) bar.removePlayer(p);
        }

        if (run.players.isEmpty()) {
            if (++run.emptyTicks >= EMPTY_GRACE) {
                destroy(server, run);
            }
            return;
        }
        run.emptyTicks = 0;

        LevelContext ctx = new LevelContext(server, world, run);
        DungeonLevel level = run.level();

        switch (run.phase) {
            case STARTING -> {
                if (--run.phaseTicks <= 0) {
                    run.phase = DungeonRun.Phase.ACTIVE;
                    level.onStart(ctx);
                }
            }
            case ACTIVE -> tickActive(server, world, run, ctx, level);
            case CLEARED -> {
                if (--run.phaseTicks <= 0) {
                    advance(server, world, run);
                } else if (run.phaseTicks % 20 == 0) {
                    for (ServerPlayer p : ctx.players()) {
                        SLUtil.actionBar(p, "§7Next level in §e" + (run.phaseTicks / 20) + "§7 s");
                    }
                }
            }
            case FINISHED -> tickFinished(server, run, ctx);
        }
        syncBars(run, ctx.players());
    }

    private static void tickActive(MinecraftServer server, ServerLevel world, DungeonRun run,
                                   LevelContext ctx, DungeonLevel level) {
        List<ServerPlayer> active = ctx.players();
        if (active.isEmpty()) return;                              // only spectators inside: wait

        run.levelAge++;
        level.tick(ctx);

        if (run.levelAge % HOUSEKEEPING_EVERY == 0) {
            retarget(world, run, active);
            pruneMobs(world, run);
        }

        if (run.skipRequested || level.isCleared(ctx)) {
            run.skipRequested = false;
            clearLevel(world, run, ctx, level);
        }
    }

    private static void clearLevel(ServerLevel world, DungeonRun run, LevelContext ctx, DungeonLevel level) {
        boolean last = run.levelIndex >= run.def.levelCount() - 1;

        level.onCleared(ctx);
        LevelRewards rewards = level.rewards();

        for (ServerPlayer p : ctx.players()) {
            PlayerDungeonProgress progress = p.getData(ModAttachments.PLAYER_DUNGEON_PROGRESS);
            p.setData(ModAttachments.PLAYER_DUNGEON_PROGRESS, progress.withCleared(run.def.id(), run.levelIndex + 1));

            SLUtil.title(p,
                    Component.literal(last ? "§6§lDUNGEON CLEARED" : "§a§lLEVEL CLEARED"),
                    Component.literal("§7" + (last ? run.def.displayName().getString() : level.name())),
                    10, 70, 20);
            SLUtil.playSound(p, "minecraft:ui.toast.challenge_complete", SoundSource.PLAYERS, 1.0f, 1.0f);

            rewards.give(p);
            if (last) {
                run.def.completionRewards().give(p);
            }
        }

        run.discardMobs(world, null);
        run.removeBars();

        if (last) {
            run.phase = DungeonRun.Phase.FINISHED;
            run.phaseTicks = FINISH_DELAY;
        } else {
            run.phase = DungeonRun.Phase.CLEARED;
            run.phaseTicks = NEXT_LEVEL_DELAY;
        }
    }

    /** Builds the next level and brings everybody there. */
    private static void advance(MinecraftServer server, ServerLevel world, DungeonRun run) {
        run.levelIndex++;
        prepareLevel(world, run);

        LevelLayout layout = run.layout;
        for (ServerPlayer p : onlineMembers(server, run)) {
            p.connection.teleport(layout.spawn().x, layout.spawn().y, layout.spawn().z, layout.yaw(), 0.0f);
            announceLevel(p, run);
        }
    }

    private static void tickFinished(MinecraftServer server, DungeonRun run, LevelContext ctx) {
        if (!run.def.autoExit()) return;                           // the players stay as long as they like

        run.phaseTicks--;
        if (run.phaseTicks > 0) {
            if (run.phaseTicks % 20 == 0) {
                for (ServerPlayer p : ctx.players()) {
                    SLUtil.actionBar(p, "§7You are taken out in §e" + (run.phaseTicks / 20)
                            + "§7 s (or use the exit stone)");
                }
            }
            return;
        }

        for (ServerPlayer p : onlineMembers(server, run)) {
            removeMember(p, run);
            teleportBack(p);
        }
        destroy(server, run);
    }

    // ── mobs ──

    /** Mobs that lost their target (the player died or left) look for a new one. */
    private static void retarget(ServerLevel world, DungeonRun run, List<ServerPlayer> active) {
        for (UUID id : run.mobs.keySet()) {
            if (!(world.getEntity(id) instanceof Mob mob) || !mob.isAlive()) continue;

            LivingEntity current = mob.getTarget();
            if (current instanceof Player pl && pl.isAlive() && !pl.isCreative() && !pl.isSpectator()) continue;

            ServerPlayer best = null;
            double bestDistance = Double.MAX_VALUE;
            for (ServerPlayer p : active) {
                if (p.isCreative()) continue;
                double d = p.distanceToSqr(mob);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = p;
                }
            }
            if (best != null) {
                mob.setTarget(best);
            }
        }
    }

    /** Forgets mobs that no longer exist. */
    private static void pruneMobs(ServerLevel world, DungeonRun run) {
        run.mobs.keySet().removeIf(id -> {
            Entity entity = world.getEntity(id);
            return entity == null || entity.isRemoved();
        });
    }

    private static void syncBars(DungeonRun run, List<ServerPlayer> players) {
        for (ServerBossEvent bar : run.bars.values()) {
            bar.setVisible(true);
            for (ServerPlayer p : players) {
                bar.addPlayer(p);                                  // ignored if already added
            }
            for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
                if (!players.contains(p)) bar.removePlayer(p);
            }
        }
    }

    private static List<ServerPlayer> onlineMembers(MinecraftServer server, DungeonRun run) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : run.players) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    // ── ending a run ──

    private static void destroy(MinecraftServer server, DungeonRun run) {
        ServerLevel world = server.getLevel(run.def.dimension());
        if (world != null) {
            run.resetLevelState(world);
        } else {
            run.removeBars();
        }
        RUNS.remove(run.def.id(), run);
        BY_PLAYER.values().removeIf(r -> r == run);
    }

    private static void abort(MinecraftServer server, DungeonRun run, String message) {
        for (ServerPlayer p : onlineMembers(server, run)) {
            SLUtil.chat(p, message);
            removeMember(p, run);
            if (DungeonRegistry.isEngineDimension(p.level().dimension())) {
                teleportBack(p);
            }
        }
        destroy(server, run);
    }

    /** Players standing in a dungeon world without being in a run (after a restart, a /tp...) are sent home. */
    private static void rescueStrays(MinecraftServer server) {
        for (ResourceKey<Level> key : DungeonRegistry.dimensions()) {
            ServerLevel world = server.getLevel(key);
            if (world == null) continue;

            for (ServerPlayer p : new ArrayList<>(world.players())) {
                if (BY_PLAYER.containsKey(p.getUUID()) || p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
                SLUtil.chat(p, "§6[System] §7The gate closed behind you. You are taken back.");
                teleportBack(p);
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // Events (called from GenericDungeonEvents)
    // ═════════════════════════════════════════════════════════════════

    /** A player died: he is out of the run (he respawns at his spawn point, not in the dungeon). */
    public static void onPlayerDeath(ServerPlayer player) {
        DungeonRun run = BY_PLAYER.get(player.getUUID());
        if (run == null) return;
        removeMember(player, run);
        player.setData(ModAttachments.PLAYER_DUNGEON_DATA,
                player.getData(ModAttachments.PLAYER_DUNGEON_DATA).withoutReturn());
    }

    public static void onLogout(ServerPlayer player) {
        DungeonRun run = BY_PLAYER.get(player.getUUID());
        if (run != null) {
            removeMember(player, run);
        }
    }

    /** A player changed world: arriving in the dungeon world starts the welcome, leaving it by other means ends his run. */
    public static void onDimensionChanged(ServerPlayer player, ResourceKey<Level> to) {
        DungeonRun run = BY_PLAYER.get(player.getUUID());
        if (run == null) return;

        if (to.equals(run.def.dimension())) {
            onArrived(player);
        } else {
            removeMember(player, run);
        }
    }

    /** A mob died. If it belongs to a run, its level hears about it. */
    public static void onMobDeath(LivingEntity dead) {
        if (!(dead.level() instanceof ServerLevel world) || !dead.getTags().contains(TAG_MOB)) return;

        String runId = runIdOf(dead);
        DungeonRun run = runId == null ? null : findRun(runId);
        if (run == null || run.phase != DungeonRun.Phase.ACTIVE || !run.mobs.containsKey(dead.getUUID())) return;

        try {
            run.level().onMobDeath(new LevelContext(world.getServer(), world, run), dead);
        } catch (RuntimeException ex) {
            SoloLeveling.LOGGER.error("onMobDeath of level {} of dungeon {} failed", run.level().name(), run.def.id(), ex);
        }
    }

    /** What a dungeon mob drops: only what its MobSpec lists. */
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !dead.getTags().contains(TAG_MOB)) return;

        String runId = runIdOf(dead);
        DungeonRun run = runId == null ? null : findRun(runId);
        DungeonRun.TrackedMob tracked = run == null ? null : run.mobs.get(dead.getUUID());

        if (tracked == null || !tracked.spec().keepsVanillaDrops()) {
            event.getDrops().clear();
        }
        if (tracked == null) return;

        for (MobSpec.Drop drop : tracked.spec().drops()) {
            ItemStack stack = drop.roll(level.random);
            if (!stack.isEmpty()) {
                event.getDrops().add(new ItemEntity(level, dead.getX(), dead.getY() + 0.5, dead.getZ(), stack));
            }
        }
    }

    /** System XP of a dungeon mob; -1 = not a dungeon mob (or no XP written on it). */
    public static int xpOverride(LivingEntity entity) {
        Set<String> tags = entity.getTags();
        if (tags.isEmpty() || !tags.contains(TAG_MOB)) return -1;

        for (String tag : tags) {
            if (tag.startsWith(TAG_XP_PREFIX)) {
                try {
                    return Math.max(0, Integer.parseInt(tag.substring(TAG_XP_PREFIX.length())));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /** True for a dungeon mob loaded back from disk whose run no longer exists: it must not come back. */
    public static boolean isOrphan(Entity entity) {
        if (!entity.getTags().contains(TAG_MOB)) return false;
        String runId = runIdOf(entity);
        return runId == null || findRun(runId) == null;
    }

    /** Is {@code pos} the exit stone of the level this player is in? */
    public static boolean isExit(ServerPlayer player, BlockPos pos) {
        DungeonRun run = BY_PLAYER.get(player.getUUID());
        return run != null && run.layout != null && pos.equals(run.layout.exit());
    }

    /** The server is stopping: forget everything (the next start has no run). */
    public static void stopAll() {
        for (DungeonRun run : RUNS.values()) {
            run.removeBars();
        }
        RUNS.clear();
        BY_PLAYER.clear();
        tickErrors = 0;
    }

    // ═════════════════════════════════════════════════════════════════
    // Admin tools (used by /sl dungeon ...)
    // ═════════════════════════════════════════════════════════════════

    /** Completes the level the player is in, as if it had been beaten (rewards and progress included). */
    public static boolean skipLevel(ServerPlayer player) {
        DungeonRun run = BY_PLAYER.get(player.getUUID());
        if (run == null || run.phase != DungeonRun.Phase.ACTIVE) return false;
        run.skipRequested = true;
        return true;
    }

    /** Sends everybody out of the run of this dungeon and drops it. */
    public static boolean endRun(MinecraftServer server, DungeonDefinition def) {
        DungeonRun run = RUNS.get(def.id());
        if (run == null) return false;
        for (ServerPlayer p : onlineMembers(server, run)) {
            removeMember(p, run);
            teleportBack(p);
        }
        destroy(server, run);
        return true;
    }

    /** Sets how many levels of a dungeon a player has cleared (commands). */
    public static void setProgress(ServerPlayer player, DungeonDefinition def, int levelsCleared) {
        PlayerDungeonProgress progress = player.getData(ModAttachments.PLAYER_DUNGEON_PROGRESS).without(def.id());
        if (levelsCleared > 0) {
            progress = progress.withCleared(def.id(), Math.min(levelsCleared, def.levelCount()));
        }
        player.setData(ModAttachments.PLAYER_DUNGEON_PROGRESS, progress);
    }

    /** One line per running dungeon. */
    public static List<String> describeRuns() {
        List<String> lines = new ArrayList<>();
        for (DungeonRun run : RUNS.values()) {
            lines.add("§e" + run.def.id() + "§7: level " + (run.levelIndex + 1) + "/" + run.def.levelCount()
                    + " (" + run.phase.name().toLowerCase() + "), " + run.players.size() + " player(s), "
                    + run.mobs.size() + " mob(s)");
        }
        if (lines.isEmpty()) {
            lines.add("§7No dungeon run is active.");
        }
        return lines;
    }
}
