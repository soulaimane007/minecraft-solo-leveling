package com.soulaimane.sololeveling.magic;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.PlayerActionBar;
import com.soulaimane.sololeveling.data.PlayerMagicData;
import com.soulaimane.sololeveling.power.ActionBarManager;
import com.soulaimane.sololeveling.power.PowerCosts;
import com.soulaimane.sololeveling.power.PowerSync;
import com.soulaimane.sololeveling.power.ServerPowerView;
import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.List;

/**
 * Server side of the magic system: learning a magic (by burning its paper) and casting it.
 * Magic has nothing to do with the skill tree.
 */
public final class MagicManager {
    private MagicManager() {}

    public enum LearnResult { LEARNED, ALREADY_KNOWN, REQUIREMENTS_NOT_MET }

    public static PlayerMagicData data(ServerPlayer p) {
        return p.getData(ModAttachments.PLAYER_MAGIC_DATA);
    }

    public static boolean knows(ServerPlayer p, Magic magic) {
        return data(p).knows(magic.id());
    }

    // ─────────────────────────────────────────────────────────────────
    // Learning
    // ─────────────────────────────────────────────────────────────────

    /**
     * The player burns a paper of {@code magic}. Learns it if he meets the requirements and does not know it yet.
     * The caller (the paper item) uses up the paper only when the result is LEARNED.
     */
    public static LearnResult learnFromPaper(ServerPlayer p, Magic magic) {
        String name = magic.displayName().getString();

        if (knows(p, magic)) {
            SLUtil.chat(p, "§e[System] You already know §f" + name + "§e. The paper stays unburned.");
            return LearnResult.ALREADY_KNOWN;
        }

        List<String> missing = magic.requirements().unmet(new ServerPowerView(p));
        if (!missing.isEmpty()) {
            StringBuilder sb = new StringBuilder("§c[System] The paper of §f" + name + " §cstays sealed. You need:");
            for (String line : missing) {
                sb.append("\n §c✘ ").append(line);
            }
            SLUtil.chat(p, sb.toString());
            return LearnResult.REQUIREMENTS_NOT_MET;
        }

        learn(p, magic);
        return LearnResult.LEARNED;
    }

    /** Teaches the magic (no requirements, no paper). Used by the paper and by commands. */
    public static void learn(ServerPlayer p, Magic magic) {
        if (knows(p, magic)) return;
        p.setData(ModAttachments.PLAYER_MAGIC_DATA, data(p).with(magic.id()));
        int slot = ActionBarManager.autoAssign(p, PlayerActionBar.magicEntry(magic.id()));
        PowerSync.sync(p);

        String name = magic.displayName().getString();
        SLUtil.title(p, Component.literal("§d§lMAGIC LEARNED"), Component.literal("§f" + name), 5, 50, 15);
        SLUtil.chat(p, "§6[System] §dThe paper burns away. You learned §f" + name + "§d."
                + (slot >= 0 ? " §7Placed on action bar slot §e" + (slot + 1) + "§7." : ""));

        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.1, p.getZ(), 40, 0.35, 0.5, 0.35, 0.04);
        level.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.4, p.getZ(), 40, 0.6, 0.6, 0.6, 0.5);
        SLUtil.playSound(p, "minecraft:item.firecharge.use", SoundSource.PLAYERS, 1.0f, 0.8f);
        SLUtil.playSound(p, "minecraft:entity.player.levelup", SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /** Makes the player forget a magic (commands). */
    public static void forget(ServerPlayer p, Magic magic) {
        p.setData(ModAttachments.PLAYER_MAGIC_DATA, data(p).without(magic.id()));
        ActionBarManager.cleanUp(p);
        PowerSync.sync(p);
    }

    public static void forgetAll(ServerPlayer p) {
        p.setData(ModAttachments.PLAYER_MAGIC_DATA, PlayerMagicData.DEFAULT);
        ActionBarManager.cleanUp(p);
        PowerSync.sync(p);
    }

    // ─────────────────────────────────────────────────────────────────
    // Casting
    // ─────────────────────────────────────────────────────────────────

    /** Casts a learned magic (cooldown, mana and stamina are checked and charged). */
    public static boolean cast(ServerPlayer p, Magic magic) {
        if (!p.isAlive() || p.isSpectator()) return false;

        if (!knows(p, magic)) {
            SLUtil.actionBar(p, "§c[System] You have not learned " + magic.displayName().getString());
            return false;
        }

        MagicContext ctx = new MagicContext(p, magic);
        return PowerCosts.execute(p, PlayerActionBar.magicEntry(magic.id()),
                magic.staminaCost(), magic.manaCost(), magic.cooldownTicks(),
                () -> magic.effect().cast(ctx));
    }
}
