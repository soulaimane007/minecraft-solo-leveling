package com.soulaimane.sololeveling.skill;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.data.SoftCap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** What a skill effect or damage hook receives: who uses the skill, which skill, and at which rank. */
public final class SkillContext {
    private final ServerPlayer player;
    private final Skill skill;
    private final int rank;

    public SkillContext(ServerPlayer player, Skill skill, int rank) {
        this.player = player;
        this.skill = skill;
        this.rank = rank;
    }

    public ServerPlayer player() { return player; }

    public ServerLevel level() { return player.serverLevel(); }

    public Skill skill() { return skill; }

    /** The rank the player learned (1 = first rank). */
    public int rank() { return rank; }

    public PlayerSystemData data() { return player.getData(ModAttachments.PLAYER_SYSTEM_DATA); }

    /** The player's value of a stat: "strength", "agility", "vitality", "intelligence", "sense", "dexterity", "durability". */
    public double stat(String name) {
        PlayerSystemData d = data();
        return SoftCap.effective(d.getStatValue(name), d.rank());
    }

    /** {@code base} at rank 1, plus {@code perRank} for every rank above: scaled(5, 2) = 5, 7, 9... */
    public double scaled(double base, double perRank) {
        return base + perRank * (rank - 1);
    }
}
