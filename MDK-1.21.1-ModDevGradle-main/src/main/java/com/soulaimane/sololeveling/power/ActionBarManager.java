package com.soulaimane.sololeveling.power;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.PlayerActionBar;
import com.soulaimane.sololeveling.magic.Magic;
import com.soulaimane.sololeveling.magic.MagicManager;
import com.soulaimane.sololeveling.magic.MagicRegistry;
import com.soulaimane.sololeveling.skill.Skill;
import com.soulaimane.sololeveling.skill.SkillManager;
import com.soulaimane.sololeveling.skill.SkillRegistry;
import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Server side of the action bar: which skill or magic sits on which slot, and what happens
 * when the player presses a slot key. Skills and magics share the same bar.
 */
public final class ActionBarManager {
    private ActionBarManager() {}

    public static PlayerActionBar bar(ServerPlayer p) {
        return p.getData(ModAttachments.PLAYER_ACTION_BAR);
    }

    /** Is this entry learned and usable, so that it may sit on the bar? */
    public static boolean canHold(ServerPlayer p, String entry) {
        if (entry.startsWith(PlayerActionBar.SKILL_PREFIX)) {
            Skill s = SkillRegistry.byId(entry.substring(PlayerActionBar.SKILL_PREFIX.length()));
            return s != null && s.isActive() && SkillManager.rank(p, s) > 0;
        }
        if (entry.startsWith(PlayerActionBar.MAGIC_PREFIX)) {
            Magic m = MagicRegistry.byId(entry.substring(PlayerActionBar.MAGIC_PREFIX.length()));
            return m != null && MagicManager.knows(p, m);
        }
        return false;
    }

    /** Puts {@code entry} on {@code slot} ("" empties it). Refuses anything the player has not learned. */
    public static boolean set(ServerPlayer p, int slot, String entry) {
        if (slot < 0 || slot >= PlayerActionBar.SIZE) return false;
        if (!entry.isEmpty() && !canHold(p, entry)) return false;
        p.setData(ModAttachments.PLAYER_ACTION_BAR, bar(p).with(slot, entry));
        PowerSync.sync(p);
        return true;
    }

    /**
     * Puts a freshly learned skill or magic on the first free slot (nothing happens if it is already on the bar
     * or the bar is full). Does not sync: the caller does.
     * @return the slot used (0-based), or -1
     */
    public static int autoAssign(ServerPlayer p, String entry) {
        PlayerActionBar bar = bar(p);
        if (bar.indexOf(entry) >= 0) return -1;
        int free = bar.firstFree();
        if (free < 0) return -1;
        p.setData(ModAttachments.PLAYER_ACTION_BAR, bar.with(free, entry));
        return free;
    }

    /** Removes everything the player can no longer use (after a respec, or when something was deleted from the mod). */
    public static void cleanUp(ServerPlayer p) {
        PlayerActionBar bar = bar(p);
        List<String> kept = new ArrayList<>(bar.slots());
        boolean changed = false;
        for (int i = 0; i < kept.size(); i++) {
            String entry = kept.get(i);
            if (!entry.isEmpty() && !canHold(p, entry)) {
                kept.set(i, "");
                changed = true;
            }
        }
        if (changed) {
            p.setData(ModAttachments.PLAYER_ACTION_BAR, PlayerActionBar.normalized(kept));
        }
    }

    /** The player pressed the key of a slot. */
    public static void use(ServerPlayer p, int slot) {
        if (!p.isAlive() || p.isSpectator()) return;
        if (slot < 0 || slot >= PlayerActionBar.SIZE) return;

        String entry = bar(p).get(slot);
        if (entry.isEmpty()) {
            SLUtil.actionBar(p, "§7Slot " + (slot + 1) + " is empty. Open the skill tree to fill it.");
            return;
        }

        if (entry.startsWith(PlayerActionBar.SKILL_PREFIX)) {
            Skill s = SkillRegistry.byId(entry.substring(PlayerActionBar.SKILL_PREFIX.length()));
            if (s == null) return;
            SkillManager.use(p, s);
        } else if (entry.startsWith(PlayerActionBar.MAGIC_PREFIX)) {
            Magic m = MagicRegistry.byId(entry.substring(PlayerActionBar.MAGIC_PREFIX.length()));
            if (m == null) return;
            MagicManager.cast(p, m);
        }
    }
}
