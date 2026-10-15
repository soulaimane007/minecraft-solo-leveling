package com.soulaimane.sololeveling.power;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.PlayerMagicData;
import com.soulaimane.sololeveling.data.PlayerSkillData;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import net.minecraft.server.level.ServerPlayer;

/** The real numbers of a player, read from his saved data (server side). */
public final class ServerPowerView implements PowerView {
    private final PlayerSystemData system;
    private final PlayerSkillData skills;
    private final PlayerMagicData magics;

    public ServerPowerView(ServerPlayer player) {
        this.system = player.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        this.skills = player.getData(ModAttachments.PLAYER_SKILL_DATA);
        this.magics = player.getData(ModAttachments.PLAYER_MAGIC_DATA);
    }

    @Override public int level() { return system.level(); }

    @Override public int rankOrdinal() { return system.rank().ordinal(); }

    @Override public int stat(String name) { return system.getStatValue(name); }

    @Override public int skillRank(String skillId) { return skills.rank(skillId); }

    @Override public boolean knowsMagic(String magicId) { return magics.knows(magicId); }
}
