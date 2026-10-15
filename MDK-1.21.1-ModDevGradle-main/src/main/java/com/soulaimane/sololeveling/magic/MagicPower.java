package com.soulaimane.sololeveling.magic;

import com.soulaimane.sololeveling.Config;
import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.data.SoftCap;
import net.minecraft.server.level.ServerPlayer;

/** How strong a player's magic is: k x Intelligence^exponent, with k and the exponent from the config (the number shown in the System panel). */
public final class MagicPower {
    private MagicPower() {}

    public static double of(ServerPlayer p) {
        PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        double effInt = SoftCap.effective(d.intelligence(), d.rank());
        return Config.SERVER.intMagicCoefficient.get() * Math.pow(effInt, Config.SERVER.intMagicExponent.get());
    }
}
