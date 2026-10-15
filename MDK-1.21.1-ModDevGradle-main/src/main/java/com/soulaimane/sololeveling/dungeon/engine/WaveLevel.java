package com.soulaimane.sololeveling.dungeon.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The classic dungeon level: mobs keep appearing until enough of them were killed, then (optionally) a boss
 * appears. When the boss falls - or the last wave mob if there is no boss - the level is cleared.
 *
 *   WaveLevel.builder("The Entrance")
 *           .arena(ArenaKit.box(25, 25, 8, ArenaKit.Style.CRYPT))
 *           .killTarget(15).maxAlive(4)
 *           .mob(MobSpec.of(EntityType.ZOMBIE).health(12).damage(2).xp(10).weight(3))
 *           .mob(MobSpec.of(EntityType.SKELETON).health(10).xp(12).finalizeSpawn())
 *           .boss(MobSpec.of(EntityType.ZOMBIE).name("Crypt Lord").health(120).damage(4).scale(2.4).xp(0))
 *           .rewards(LevelRewards.builder().xp(500).skillPoints(1).build())
 *           .build();
 *
 * The level keeps nothing in its own fields (the same object serves every run): its progress is stored in the
 * LevelContext, so you can reuse one WaveLevel in several dungeons.
 */
public final class WaveLevel implements DungeonLevel {

    private static final String STAGE = "wave.stage";
    private static final String KILLS = "wave.kills";
    private static final String TIMER = "wave.timer";
    private static final String BOSS_MISSING = "wave.bossMissing";

    private static final int WAVES = 0;
    private static final int BOSS_INTRO = 1;
    private static final int BOSS = 2;
    private static final int DONE = 3;

    private static final String GROUP_WAVE = "wave";
    private static final String GROUP_BOSS = "boss";

    private final String name;
    private final LevelBuilder arena;
    private final int killTarget;
    private final int maxAlive;
    private final int spawnInterval;
    private final int bossIntro;
    private final List<MobSpec> pool;
    private final int totalWeight;
    private final MobSpec boss;
    private final LevelRewards rewards;
    private final boolean sealedDuringBoss;

    private WaveLevel(Builder b) {
        this.name = b.name;
        this.arena = b.arena;
        this.killTarget = b.killTarget;
        this.maxAlive = b.maxAlive;
        this.spawnInterval = b.spawnInterval;
        this.bossIntro = b.bossIntro;
        this.pool = List.copyOf(b.pool);
        this.boss = b.boss;
        this.rewards = b.rewards;
        this.sealedDuringBoss = b.sealedDuringBoss;

        int sum = 0;
        for (MobSpec spec : pool) {
            sum += spec.weight();
        }
        this.totalWeight = sum;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    // ─── DungeonLevel ────────────────────────────────────────────────

    @Override
    public String name() {
        return name;
    }

    @Override
    public LevelLayout build(ServerLevel world, BlockPos origin) {
        return arena.build(world, origin);
    }

    @Override
    public void onStart(LevelContext ctx) {
        ctx.setInt(STAGE, WAVES);
        ctx.setInt(KILLS, 0);
        ctx.setInt(TIMER, 40);
        if (killTarget > 0) {
            ctx.bar("progress", Component.literal("Slain: 0 / " + killTarget),
                    BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_20).setProgress(0.0f);
        }
    }

    @Override
    public void tick(LevelContext ctx) {
        switch (ctx.getInt(STAGE)) {
            case WAVES -> tickWaves(ctx);
            case BOSS_INTRO -> tickBossIntro(ctx);
            case BOSS -> tickBoss(ctx);
            default -> { }
        }
    }

    @Override
    public void onMobDeath(LevelContext ctx, LivingEntity dead) {
        String group = ctx.groupOf(dead);
        int stage = ctx.getInt(STAGE);

        if (GROUP_WAVE.equals(group) && stage == WAVES) {
            ctx.addInt(KILLS, 1);
        } else if (GROUP_BOSS.equals(group) && stage == BOSS) {
            ctx.setInt(STAGE, DONE);
            ctx.removeBar("boss");
        }
    }

    @Override
    public boolean isCleared(LevelContext ctx) {
        return ctx.getInt(STAGE) == DONE;
    }

    @Override
    public LevelRewards rewards() {
        return rewards;
    }

    @Override
    public boolean canLeave(LevelContext ctx) {
        if (!sealedDuringBoss) return true;
        int stage = ctx.getInt(STAGE);
        return stage != BOSS_INTRO && stage != BOSS;
    }

    // ─── The waves ───────────────────────────────────────────────────

    private void tickWaves(LevelContext ctx) {
        int kills = ctx.getInt(KILLS);

        if (killTarget > 0) {
            ServerBossEvent bar = ctx.bar("progress", Component.literal(""),
                    BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_20);
            bar.setName(Component.literal("Slain: " + Math.min(kills, killTarget) + " / " + killTarget));
            bar.setProgress(Mth.clamp(kills / (float) killTarget, 0.0f, 1.0f));
        }

        if (kills >= killTarget) {
            finishWaves(ctx);
            return;
        }

        int timer = ctx.getInt(TIMER) - 1;
        int allowed = maxAlive + 2 * Math.max(0, ctx.players().size() - 1);
        int alive = ctx.aliveMobs(GROUP_WAVE);

        // Never more mobs than the kills still missing, never more than "allowed" at once
        if (timer <= 0 && alive < allowed && kills + alive < killTarget) {
            if (spawnWaveMob(ctx)) {
                timer = spawnInterval;
            } else {
                timer = 10;                       // no free place right now: try again soon
            }
        }
        ctx.setInt(TIMER, Math.max(0, timer));
    }

    private boolean spawnWaveMob(LevelContext ctx) {
        if (pool.isEmpty()) return false;
        LevelLayout layout = ctx.layout();
        List<Vec3> places = layout.mobSpawns();
        if (places.isEmpty()) return false;

        List<ServerPlayer> players = ctx.players();
        for (int attempt = 0; attempt < 10; attempt++) {
            Vec3 place = places.get(ctx.world().random.nextInt(places.size()));
            if (tooCloseToPlayer(players, place, 7.0)) continue;

            if (ctx.spawn(pickMob(ctx), place, GROUP_WAVE) != null) {
                return true;
            }
        }
        return false;
    }

    private MobSpec pickMob(LevelContext ctx) {
        int roll = ctx.world().random.nextInt(Math.max(1, totalWeight));
        for (MobSpec spec : pool) {
            roll -= spec.weight();
            if (roll < 0) return spec;
        }
        return pool.get(0);
    }

    private static boolean tooCloseToPlayer(List<ServerPlayer> players, Vec3 place, double minDistance) {
        double min2 = minDistance * minDistance;
        for (ServerPlayer p : players) {
            double dx = p.getX() - place.x;
            double dz = p.getZ() - place.z;
            if (dx * dx + dz * dz < min2) return true;
        }
        return false;
    }

    private void finishWaves(LevelContext ctx) {
        ctx.discardMobs(GROUP_WAVE);
        ctx.removeBar("progress");

        if (boss == null) {
            ctx.setInt(STAGE, DONE);
            return;
        }
        ctx.setInt(STAGE, BOSS_INTRO);
        ctx.setInt(TIMER, bossIntro);
        ctx.title("§4§l" + bossName(), "§7Something stirs in the dark...");
        ctx.sound("minecraft:entity.wither.spawn", 0.5f, 0.6f);
    }

    // ─── The boss ────────────────────────────────────────────────────

    private void tickBossIntro(LevelContext ctx) {
        int timer = ctx.getInt(TIMER) - 1;
        ctx.setInt(TIMER, timer);
        if (timer > 0) return;

        LevelLayout layout = ctx.layout();
        Vec3 place = layout.bossSpawn() != null ? layout.bossSpawn() : layout.spawn();
        Mob spawned = ctx.spawn(boss, place, GROUP_BOSS);
        if (spawned == null) {
            return;                               // try again next tick
        }

        ctx.setInt(STAGE, BOSS);
        ctx.setInt(BOSS_MISSING, 0);
        ctx.bar("boss", Component.literal("§4§l" + bossName()),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS).setProgress(1.0f);
        ctx.sound("minecraft:entity.ender_dragon.growl", 0.6f, 0.8f);
    }

    private void tickBoss(LevelContext ctx) {
        Mob alive = ctx.firstAlive(GROUP_BOSS);
        if (alive == null) {
            // Gone without dying (removed by something else)? After 5 seconds he comes back at full health
            if (ctx.addInt(BOSS_MISSING, 1) > 100) {
                ctx.removeBar("boss");
                ctx.setInt(STAGE, BOSS_INTRO);
                ctx.setInt(TIMER, 0);
            }
            return;
        }
        ctx.setInt(BOSS_MISSING, 0);

        ServerBossEvent bar = ctx.bar("boss", Component.literal("§4§l" + bossName()),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        bar.setProgress(Mth.clamp(alive.getHealth() / Math.max(1.0f, alive.getMaxHealth()), 0.0f, 1.0f));
    }

    private String bossName() {
        return boss.displayName() != null ? boss.displayName() : "BOSS";
    }

    // ─── Builder ─────────────────────────────────────────────────────

    public static final class Builder {
        private final String name;
        private LevelBuilder arena;
        private int killTarget = 10;
        private int maxAlive = 4;
        private int spawnInterval = 30;
        private int bossIntro = 100;
        private final List<MobSpec> pool = new ArrayList<>();
        private MobSpec boss;
        private LevelRewards rewards = LevelRewards.NONE;
        private boolean sealedDuringBoss = true;

        private Builder(String name) {
            this.name = name;
        }

        /** The room: {@code ArenaKit.box(...)} or your own {@link LevelBuilder}. Required. */
        public Builder arena(LevelBuilder arena) {
            this.arena = arena;
            return this;
        }

        /** Mobs to kill before the boss appears (or before the level is won, if there is no boss). Default 10. */
        public Builder killTarget(int kills) {
            this.killTarget = Math.max(0, kills);
            return this;
        }

        /** Mobs alive at the same time (+2 for each extra player). Default 4. */
        public Builder maxAlive(int count) {
            this.maxAlive = Math.max(1, count);
            return this;
        }

        /** Ticks between two spawns (20 = one second). Default 30. */
        public Builder spawnInterval(int ticks) {
            this.spawnInterval = Math.max(1, ticks);
            return this;
        }

        /** Ticks between the last kill and the boss appearing. Default 100. */
        public Builder bossIntro(int ticks) {
            this.bossIntro = Math.max(1, ticks);
            return this;
        }

        /** Adds a kind of mob to the pool (choose how often with MobSpec.weight). */
        public Builder mob(MobSpec spec) {
            this.pool.add(spec);
            return this;
        }

        /** The boss. Without one, the level is won when the kill target is reached. */
        public Builder boss(MobSpec boss) {
            this.boss = boss;
            return this;
        }

        public Builder rewards(LevelRewards rewards) {
            this.rewards = rewards;
            return this;
        }

        /** false = the players may leave during the boss fight. Default true (the exit is sealed). */
        public Builder sealedDuringBoss(boolean sealed) {
            this.sealedDuringBoss = sealed;
            return this;
        }

        public WaveLevel build() {
            if (arena == null) {
                throw new IllegalStateException("The level '" + name + "' needs an .arena(...)");
            }
            if (killTarget > 0 && pool.isEmpty()) {
                throw new IllegalStateException("The level '" + name + "' needs at least one .mob(...) "
                        + "(or .killTarget(0) for a boss room)");
            }
            if (killTarget == 0 && boss == null) {
                throw new IllegalStateException("The level '" + name + "' has no mobs to kill and no boss: it would end at once");
            }
            return new WaveLevel(this);
        }
    }
}
