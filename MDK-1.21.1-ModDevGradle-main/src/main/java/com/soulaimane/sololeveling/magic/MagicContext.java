package com.soulaimane.sololeveling.magic;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.data.SoftCap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** What a magic effect receives: who casts it and which magic. */
public final class MagicContext {
    private final ServerPlayer player;
    private final Magic magic;

    public MagicContext(ServerPlayer player, Magic magic) {
        this.player = player;
        this.magic = magic;
    }

    public ServerPlayer player() { return player; }

    public ServerLevel level() { return player.serverLevel(); }

    public Magic magic() { return magic; }

    /** The caster's magic power (grows with Intelligence). A typical spell damage is "base + power x factor". */
    public double power() { return MagicPower.of(player); }

    /** The caster's value of a stat: "strength", "agility", "vitality", "intelligence", "sense", "dexterity", "durability". */
    public double stat(String name) {
        PlayerSystemData d = player.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        return SoftCap.effective(d.getStatValue(name), d.rank());
    }
}
