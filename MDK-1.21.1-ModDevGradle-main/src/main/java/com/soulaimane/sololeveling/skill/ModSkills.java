package com.soulaimane.sololeveling.skill;

import com.soulaimane.sololeveling.power.PowerDamage;
import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * ALL THE SKILLS OF THE MOD ARE REGISTERED HERE.
 *
 * To add a skill: copy the POWER_STRIKE block, change the branch, the id and the numbers.
 * That is all: the skill tree screen, the action bar, the commands and the saving work for every
 * skill in this list by themselves.
 *
 *   branch     TANK, SUPPORT, WARRIOR, MARKSMAN, MAGE or ASSASSIN (the Mage branch is for ordinary skills: spells are magics)
 *   .parent    the skill that must be learned first (draws a line in the tree). Skills without parent are at the top.
 *   .attribute a passive bonus (per rank) on any attribute
 *   .active    makes it usable from the action bar: what it does, and what it costs (.stamina / .mana / .cooldownSeconds)
 *   .onDealDamage / .onTakeDamage   passives that change damage
 */
public final class ModSkills {
    private ModSkills() {}

    /**
     * TEST SKILL (Warrior). Active: a slam that hurts and pushes back every enemy around you.
     * Passive part: +1 max health per rank (half a heart), so you can see it work as soon as you learn it.
     */
    public static final Skill POWER_STRIKE = SkillRegistry.register(
            Skill.builder("power_strike", SkillBranch.WARRIOR)
                    .name("Power Strike")
                    .description("Slam the ground: every enemy around you takes damage and is thrown back. "
                            + "Damage grows with rank and Strength. Each rank also gives +1 max health.")
                    .icon(() -> Items.IRON_SWORD)
                    .maxRank(3)
                    .pointCost(1)
                    .rankLevels(1, 5, 10)                       // level needed for rank 1, 2, 3
                    .attribute(() -> Attributes.MAX_HEALTH, 1.0, AttributeModifier.Operation.ADD_VALUE)
                    .active(ModSkills::usePowerStrike)
                    .stamina(rank -> 15 + 5 * rank)
                    .cooldownSeconds(8)
                    .build());

    private static boolean usePowerStrike(SkillContext ctx) {
        ServerPlayer p = ctx.player();
        ServerLevel level = ctx.level();

        double radius = ctx.scaled(3.5, 0.5);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(radius, 1.5, radius),
                e -> e != p && e.isAlive() && e instanceof Enemy && e.distanceToSqr(p) <= radius * radius);

        if (targets.isEmpty()) {
            SLUtil.actionBar(p, "§7No enemy in range");
            return false;                        // nothing happened: no stamina spent, no cooldown
        }

        float damage = (float) (ctx.scaled(6.0, 2.0) + ctx.stat("strength") * 0.25);
        DamageSource source = PowerDamage.skill(p);
        for (LivingEntity target : targets) {
            target.hurt(source, damage);
            target.knockback(0.9, p.getX() - target.getX(), p.getZ() - target.getZ());
        }

        level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.3, p.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        for (int i = 0; i < 16; i++) {
            double angle = i * (Math.PI * 2.0 / 16.0);
            level.sendParticles(ParticleTypes.CRIT,
                    p.getX() + Math.cos(angle) * radius, p.getY() + 0.2, p.getZ() + Math.sin(angle) * radius,
                    2, 0.05, 0.05, 0.05, 0.1);
        }
        SLUtil.playSound(p, "minecraft:entity.generic.explode", SoundSource.PLAYERS, 0.6f, 1.4f);
        return true;
    }

    /** Called once while the mod starts. Add new skills above. */
    public static void init() {
        SkillRegistry.validate();
    }
}
