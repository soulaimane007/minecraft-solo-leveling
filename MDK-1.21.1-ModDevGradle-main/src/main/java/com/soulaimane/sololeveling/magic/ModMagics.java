package com.soulaimane.sololeveling.magic;

import com.soulaimane.sololeveling.power.PowerDamage;
import com.soulaimane.sololeveling.power.Requirements;
import com.soulaimane.sololeveling.util.Rays;
import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * ALL THE MAGICS OF THE MOD ARE REGISTERED HERE.
 *
 * To add a magic: copy the FLAME_BOLT block, change the id, the numbers and the effect.
 * That is all: the Magic Paper, the creative tab entry, the commands, the action bar and the saving
 * work for every magic in this list by themselves.
 *
 * A magic is learned by burning its Magic Paper while the requirements are met (level, rank, stats, skills,
 * other magics). It has nothing to do with the skill tree.
 */
public final class ModMagics {
    private ModMagics() {}

    /** TEST MAGIC. Hit-scan fire bolt: hits what you look at, sets it on fire. Damage grows with Intelligence. */
    public static final Magic FLAME_BOLT = MagicRegistry.register(
            Magic.builder("flame_bolt")
                    .name("Flame Bolt")
                    .description("A bolt of fire hits what you look at (up to 24 blocks) and sets it ablaze. "
                            + "Its damage grows with Intelligence.")
                    .color(0xFFFF8A2B)
                    .icon(() -> Items.BLAZE_POWDER)
                    .requires(Requirements.builder().level(1).stat("intelligence", 2).build())
                    .mana(8)
                    .cooldownSeconds(1.5)
                    .cast(ModMagics::castFlameBolt)
                    .build());

    private static boolean castFlameBolt(MagicContext ctx) {
        ServerPlayer p = ctx.player();
        ServerLevel level = ctx.level();

        LivingEntity target = Rays.firstTarget(p, 24.0);
        if (target == null) {
            SLUtil.actionBar(p, "§7No target in sight");
            return false;                        // nothing happened: no mana spent, no cooldown
        }

        Vec3 from = p.getEyePosition().add(0.0, -0.25, 0.0);
        Vec3 to = target.getBoundingBox().getCenter();
        Rays.trail(level, ParticleTypes.FLAME, from, to, 0.5);

        float damage = (float) (4.0 + ctx.power() * 0.6);
        target.hurt(PowerDamage.magic(p), damage);
        target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 80));

        level.sendParticles(ParticleTypes.LAVA, to.x, to.y, to.z, 6, 0.3, 0.3, 0.3, 0.0);
        SLUtil.playSound(p, "minecraft:entity.blaze.shoot", SoundSource.PLAYERS, 0.8f, 1.3f);
        return true;
    }

    /** Called once while the mod starts. Add new magics above. */
    public static void init() {
        MagicRegistry.validate();
    }
}
