package com.soulaimane.sololeveling.util;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Aiming helpers for skills and magics (hit-scan: no projectile entity needed). */
public final class Rays {
    private Rays() {}

    /** The living entity the player is looking at within {@code range} blocks (walls stop the ray), or null. */
    public static LivingEntity firstTarget(ServerPlayer player, double range) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = endPoint(player, range);

        double bestDist = eye.distanceToSqr(end);
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);

        LivingEntity best = null;
        for (Entity e : level.getEntities(player, box,
                ent -> ent instanceof LivingEntity le && le.isAlive() && !le.isSpectator())) {
            AABB bb = e.getBoundingBox().inflate(e.getPickRadius() + 0.2);
            double dist;
            if (bb.contains(eye)) {
                dist = 0.0;
            } else {
                Optional<Vec3> hit = bb.clip(eye, end);
                if (hit.isEmpty()) continue;
                dist = eye.distanceToSqr(hit.get());
            }
            if (dist <= bestDist) {
                best = (LivingEntity) e;
                bestDist = dist;
            }
        }
        return best;
    }

    /** Where a ray from the player's eyes ends: the first block in the way, or {@code range} blocks ahead. */
    public static Vec3 endPoint(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 far = eye.add(player.getLookAngle().scale(range));
        BlockHitResult hit = player.level().clip(
                new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
    }

    /** A line of particles between two points. */
    public static void trail(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double spacing) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 0.01) return;
        Vec3 step = delta.scale(spacing / length);
        int count = Math.min(80, (int) Math.ceil(length / spacing));
        Vec3 at = from;
        for (int i = 0; i < count; i++) {
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            at = at.add(step);
        }
    }
}
