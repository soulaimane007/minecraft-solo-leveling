package com.soulaimane.sololeveling.skill;

import com.soulaimane.sololeveling.Config;
import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.PlayerActionBar;
import com.soulaimane.sololeveling.data.PlayerSkillData;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.power.ActionBarManager;
import com.soulaimane.sololeveling.power.PowerCosts;
import com.soulaimane.sololeveling.power.PowerSync;
import com.soulaimane.sololeveling.power.ServerPowerView;
import com.soulaimane.sololeveling.util.SLResource;
import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;

/**
 * Server side of the skill tree: skill points, learning, passive bonuses and using active skills.
 *
 * Skill points = (level x pointsPerLevel from the config) + bonus points - points spent.
 */
public final class SkillManager {
    private SkillManager() {}

    public enum UnlockResult { OK, UNKNOWN_SKILL, MAXED, NEEDS_PARENT, NEEDS_REQUIREMENTS, NO_POINTS }

    // ─────────────────────────────────────────────────────────────────
    // Points and ranks
    // ─────────────────────────────────────────────────────────────────

    public static PlayerSkillData data(ServerPlayer p) {
        return p.getData(ModAttachments.PLAYER_SKILL_DATA);
    }

    public static int rank(ServerPlayer p, Skill skill) {
        return data(p).rank(skill.id());
    }

    /** All the skill points the player earned so far (levels + bonus), spent or not. */
    public static int pointsEarned(ServerPlayer p) {
        PlayerSystemData sys = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        long earned = (long) sys.level() * Config.SERVER.skillPointsPerLevel.get() + data(p).bonus();
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, earned));
    }

    public static int pointsAvailable(ServerPlayer p) {
        return Math.max(0, pointsEarned(p) - data(p).spent());
    }

    /** Gives (or with a negative number takes away) bonus skill points: rewards, commands. */
    public static void grantPoints(ServerPlayer p, int amount) {
        PlayerSkillData d = data(p);
        p.setData(ModAttachments.PLAYER_SKILL_DATA, d.withBonus(d.bonus() + amount));
        PowerSync.sync(p);
    }

    // ─────────────────────────────────────────────────────────────────
    // Learning
    // ─────────────────────────────────────────────────────────────────

    /** Learns the next rank of a skill if every rule allows it (this is what the tree's click ends up calling). */
    public static UnlockResult tryUnlock(ServerPlayer p, String skillId) {
        Skill skill = SkillRegistry.byId(skillId);
        if (skill == null) {
            return UnlockResult.UNKNOWN_SKILL;
        }

        SkillRules.Status status = SkillRules.status(skill, new ServerPowerView(p), pointsAvailable(p));
        switch (status) {
            case MAXED: return UnlockResult.MAXED;
            case NEEDS_PARENT: return UnlockResult.NEEDS_PARENT;
            case NEEDS_REQUIREMENTS: return UnlockResult.NEEDS_REQUIREMENTS;
            case NO_POINTS: return UnlockResult.NO_POINTS;
            default: break;
        }

        PlayerSkillData d = data(p);
        int newRank = d.rank(skill.id()) + 1;
        p.setData(ModAttachments.PLAYER_SKILL_DATA, d.withRank(skill.id(), newRank, skill.pointCost(newRank)));
        applyPassives(p);

        int slot = -1;
        if (skill.isActive() && newRank == 1) {
            slot = ActionBarManager.autoAssign(p, PlayerActionBar.skillEntry(skill.id()));
        }
        PowerSync.sync(p);

        String name = skill.displayName().getString();
        SLUtil.chat(p, "§6[System] §a" + name + " §7rank §e" + newRank + "§7/§e" + skill.maxRank()
                + (slot >= 0 ? " §7- placed on action bar slot §e" + (slot + 1) : ""));
        p.serverLevel().sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.0, p.getZ(),
                30, 0.5, 0.8, 0.5, 0.3);
        SLUtil.playSound(p, "minecraft:block.enchantment_table.use", SoundSource.PLAYERS, 0.8f, 1.3f);
        return UnlockResult.OK;
    }

    /** Admin: sets the rank of a skill without any rule and without spending points (0 removes it). */
    public static void setRank(ServerPlayer p, Skill skill, int rank) {
        int clamped = Math.max(0, Math.min(skill.maxRank(), rank));
        PlayerSkillData d = data(p);
        p.setData(ModAttachments.PLAYER_SKILL_DATA, d.withRank(skill.id(), clamped, 0));
        if (clamped > 0 && skill.isActive()) {
            ActionBarManager.autoAssign(p, PlayerActionBar.skillEntry(skill.id()));
        }
        applyPassives(p);
        ActionBarManager.cleanUp(p);
        PowerSync.sync(p);
    }

    /** Respec: forget every skill and get all the spent points back. */
    public static void reset(ServerPlayer p) {
        p.setData(ModAttachments.PLAYER_SKILL_DATA, data(p).cleared());
        applyPassives(p);
        ActionBarManager.cleanUp(p);
        PowerSync.sync(p);
    }

    // ─────────────────────────────────────────────────────────────────
    // Passives
    // ─────────────────────────────────────────────────────────────────

    private static ResourceLocation modifierId(Skill skill, int index) {
        return SLResource.id("skill/" + skill.location().getNamespace() + "/" + skill.location().getPath() + "/" + index);
    }

    /**
     * Puts the attribute bonuses of the learned skills on the player (and takes away those of skills he does not have).
     * They are "transient": never saved, so nothing is left behind if a skill is removed from the mod later.
     * Called on login, respawn, dimension change and whenever a rank changes.
     */
    public static void applyPassives(ServerPlayer p) {
        PlayerSkillData d = data(p);
        for (Skill skill : SkillRegistry.all()) {
            List<Skill.AttributeBonus> bonuses = skill.bonuses();
            if (bonuses.isEmpty()) continue;

            int rank = d.rank(skill.id());
            for (int i = 0; i < bonuses.size(); i++) {
                Skill.AttributeBonus bonus = bonuses.get(i);
                AttributeInstance instance = p.getAttribute(bonus.attribute().get());
                if (instance == null) continue;

                ResourceLocation id = modifierId(skill, i);
                instance.removeModifier(id);
                if (rank > 0 && bonus.perRank() != 0.0) {
                    instance.addTransientModifier(new AttributeModifier(id, bonus.perRank() * rank, bonus.operation()));
                }
            }
        }
        // Max health may have dropped: never stay above it
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Active use
    // ─────────────────────────────────────────────────────────────────

    /** Uses an active skill (cooldown, stamina and mana are checked and charged). */
    public static boolean use(ServerPlayer p, Skill skill) {
        if (!p.isAlive() || p.isSpectator() || !skill.isActive()) return false;

        int rank = rank(p, skill);
        if (rank <= 0) {
            SLUtil.actionBar(p, "§c[System] You have not learned " + skill.displayName().getString());
            return false;
        }

        SkillContext ctx = new SkillContext(p, skill, rank);
        return PowerCosts.execute(p, PlayerActionBar.skillEntry(skill.id()),
                skill.staminaCost(rank), skill.manaCost(rank), skill.cooldownTicks(rank),
                () -> skill.effect().use(ctx));
    }
}
