package com.soulaimane.sololeveling.power;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.PlayerActionBar;
import com.soulaimane.sololeveling.data.PlayerMagicData;
import com.soulaimane.sololeveling.data.PlayerSkillData;
import com.soulaimane.sololeveling.network.SyncCooldownPayload;
import com.soulaimane.sololeveling.network.SyncPowerPayload;
import com.soulaimane.sololeveling.skill.SkillManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;

/** Sends the skill / magic / action bar state to the player's client. */
public final class PowerSync {
    private PowerSync() {}

    /** Call after anything changed: a skill learned, a magic burned, a slot moved, a level gained. */
    public static void sync(ServerPlayer p) {
        PlayerSkillData skills = p.getData(ModAttachments.PLAYER_SKILL_DATA);
        PlayerMagicData magics = p.getData(ModAttachments.PLAYER_MAGIC_DATA);
        PlayerActionBar bar = p.getData(ModAttachments.PLAYER_ACTION_BAR);

        PacketDistributor.sendToPlayer(p, new SyncPowerPayload(
                SkillManager.pointsAvailable(p),
                new HashMap<>(skills.ranks()),
                List.copyOf(magics.learned()),
                List.copyOf(bar.slots())));
    }

    /** Tells the client that {@code entry} is cooling down for {@code ticks} (the action bar shows it). */
    public static void sendCooldown(ServerPlayer p, String entry, int ticks) {
        PacketDistributor.sendToPlayer(p, new SyncCooldownPayload(entry, ticks));
    }
}
