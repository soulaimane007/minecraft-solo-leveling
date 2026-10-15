package com.soulaimane.sololeveling.dungeon;

import com.soulaimane.sololeveling.Config;
import com.soulaimane.sololeveling.util.SLResource;
import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Zombie King's behaviour, driven from the dungeon tick (the boss is a normal vanilla
 * zombie with scaled-up stats, so there is no custom entity or renderer to maintain).
 *
 *  - Ground Slam:  every 6-10 s, if a player is close. 1.2 s of warning (a ring of soul fire),
 *                  then everyone inside the ring takes damage and is thrown back.
 *  - Summons:      at 66% and 33% health the king calls zombies and the players get a short
 *                  regeneration ("second wind") so the fight stays fair.
 *  - Enrage:       below 25% health the king is faster and hits harder.
 *
 * Progress flags are stored as entity tags, so they survive a server restart.
 */
public final class BossController {
    private BossController() {}

    public static final String TAG_WAVE_1  = "sl_wave1";
    public static final String TAG_WAVE_2  = "sl_wave2";
    public static final String TAG_ENRAGED = "sl_enraged";

    private static final ResourceLocation ENRAGE_SPEED_ID  = SLResource.id("boss_enrage_speed");
    private static final ResourceLocation ENRAGE_DAMAGE_ID = SLResource.id("boss_enrage_damage");

    private static final double SLAM_RADIUS      = 6.0;
    private static final double SLAM_TRIGGER     = 9.0;
    private static final int    SLAM_WINDUP      = 24;
    private static final int    SLAM_MIN_COOLDOWN = 120;
    private static final int    SLAM_EXTRA_RANDOM = 80;

    // Transient fight state (only one boss exists at a time)
    private static int slamCooldown = 100;
    private static int slamWindup = 0;

    public static void reset() {
        slamCooldown = 100;
        slamWindup = 0;
    }

    // ═════════════════════════════════════════════════════════════════

    public static void tick(ServerLevel level, Zombie boss, List<ServerPlayer> players) {
        float fraction = boss.getHealth() / Math.max(1f, boss.getMaxHealth());

        // ── Phase changes ──
        if (fraction <= 0.66f && !boss.getTags().contains(TAG_WAVE_1)) {
            boss.addTag(TAG_WAVE_1);
            summonWave(level, boss, players, 3);
        }
        if (fraction <= 0.33f && !boss.getTags().contains(TAG_WAVE_2)) {
            boss.addTag(TAG_WAVE_2);
            summonWave(level, boss, players, 4);
        }
        if (fraction <= 0.25f && !boss.getTags().contains(TAG_ENRAGED)) {
            boss.addTag(TAG_ENRAGED);
            enrage(level, boss, players);
        }

        // ── Ground slam ──
        if (slamWindup > 0) {
            slamWindup--;
            if (slamWindup % 4 == 0) {
                telegraph(level, boss);
            }
            if (slamWindup == 0) {
                slam(level, boss, players);
            }
            return;
        }

        if (--slamCooldown <= 0) {
            boolean someoneClose = false;
            for (ServerPlayer p : players) {
                if (!p.isSpectator() && p.isAlive() && p.distanceToSqr(boss) <= SLAM_TRIGGER * SLAM_TRIGGER) {
                    someoneClose = true;
                    break;
                }
            }
            if (someoneClose) {
                slamWindup = SLAM_WINDUP;
                slamCooldown = SLAM_MIN_COOLDOWN + level.random.nextInt(SLAM_EXTRA_RANDOM);
                SLUtil.playSound(boss, "minecraft:entity.ravager.roar", SoundSource.HOSTILE, 2.0f, 0.7f);
            } else {
                slamCooldown = 20;   // look again in a second
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // Slam
    // ═════════════════════════════════════════════════════════════════

    private static void telegraph(ServerLevel level, Zombie boss) {
        final int points = 28;
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    boss.getX() + Math.cos(a) * SLAM_RADIUS,
                    boss.getY() + 0.15,
                    boss.getZ() + Math.sin(a) * SLAM_RADIUS,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void slam(ServerLevel level, Zombie boss, List<ServerPlayer> players) {
        double damage = Config.SERVER.dungeonBossDamage.get() * 1.5;

        for (ServerPlayer p : players) {
            if (p.isSpectator() || !p.isAlive()) continue;
            double dx = p.getX() - boss.getX();
            double dz = p.getZ() - boss.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > SLAM_RADIUS || Math.abs(p.getY() - boss.getY()) > 3.0) continue;

            // fairness rule: one slam never takes more than 60% of a full health bar
            float hit = (float) Math.min(damage, p.getMaxHealth() * 0.6);
            p.hurt(level.damageSources().mobAttack(boss), hit);

            // Throw the player away from the king (ignores poise on purpose: it is a boss move)
            Vec3 push = dist < 0.01
                    ? new Vec3(0.0, 0.0, 1.0)
                    : new Vec3(dx / dist, 0.0, dz / dist);
            p.setDeltaMovement(p.getDeltaMovement().add(push.x * 1.2, 0.6, push.z * 1.2));
            p.hurtMarked = true;
        }

        level.sendParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + 0.2, boss.getZ(),
                6, SLAM_RADIUS / 2.5, 0.1, SLAM_RADIUS / 2.5, 0.0);
        level.sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 0.2, boss.getZ(),
                40, SLAM_RADIUS / 2.0, 0.1, SLAM_RADIUS / 2.0, 0.08);
        SLUtil.playSound(boss, "minecraft:entity.generic.explode", SoundSource.HOSTILE, 1.5f, 0.6f);
        SLUtil.playSound(boss, "minecraft:entity.iron_golem.attack", SoundSource.HOSTILE, 1.5f, 0.5f);
    }

    // ═════════════════════════════════════════════════════════════════
    // Summons & enrage
    // ═════════════════════════════════════════════════════════════════

    private static void summonWave(ServerLevel level, Zombie boss, List<ServerPlayer> players, int count) {
        for (int i = 0; i < count; i++) {
            double a = level.random.nextDouble() * 2 * Math.PI;
            double r = 4.0 + level.random.nextDouble() * 3.0;
            double x = boss.getX() + Math.cos(a) * r;
            double z = boss.getZ() + Math.sin(a) * r;
            if (!DungeonLayout.isInside(x, z, 3.0)) {
                x = boss.getX() - Math.cos(a) * r;
                z = boss.getZ() - Math.sin(a) * r;
            }
            Zombie minion = DungeonManager.createZombie(level, x, DungeonLayout.FLOOR_Y + 1.0, z,
                    DungeonManager.TAG_MINION, 0.7);
            if (minion != null) {
                level.addFreshEntity(minion);
                level.sendParticles(ParticleTypes.POOF, x, DungeonLayout.FLOOR_Y + 1.2, z, 12, 0.3, 0.5, 0.3, 0.03);
            }
        }
        SLUtil.playSound(boss, "minecraft:entity.evoker.prepare_summon", SoundSource.HOSTILE, 2.0f, 0.6f);

        for (ServerPlayer p : players) {
            SLUtil.title(p, Component.literal("§c§lThe King calls his army!"),
                    Component.literal("§7A moment of respite: regeneration"), 5, 30, 10);
            // "second wind" so the fight stays fair
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        }
    }

    private static void enrage(ServerLevel level, Zombie boss, List<ServerPlayer> players) {
        AttributeInstance speed = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(ENRAGE_SPEED_ID) == null) {
            speed.addPermanentModifier(new AttributeModifier(
                    ENRAGE_SPEED_ID, 0.30, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        AttributeInstance dmg = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null && dmg.getModifier(ENRAGE_DAMAGE_ID) == null) {
            dmg.addPermanentModifier(new AttributeModifier(
                    ENRAGE_DAMAGE_ID, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }

        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, boss.getX(), boss.getY() + boss.getBbHeight() * 0.8, boss.getZ(),
                20, 1.0, 1.0, 1.0, 0.1);
        SLUtil.playSound(boss, "minecraft:entity.zombie_villager.converted", SoundSource.HOSTILE, 2.0f, 0.5f);

        for (ServerPlayer p : players) {
            SLUtil.title(p, Component.literal("§4§lENRAGED"),
                    Component.literal("§7The Zombie King is faster and hits harder!"), 5, 30, 10);
        }
    }
}
