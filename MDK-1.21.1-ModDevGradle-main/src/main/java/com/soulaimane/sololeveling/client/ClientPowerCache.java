package com.soulaimane.sololeveling.client;

import com.soulaimane.sololeveling.data.PlayerActionBar;
import com.soulaimane.sololeveling.network.SyncPowerPayload;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Client-side mirror of the skill / magic / action bar data (filled by the sync packets).
 * Plain data on purpose (no Minecraft client classes), so item tooltips can read it too.
 */
public final class ClientPowerCache {
    private ClientPowerCache() {}

    /** Skill points the player can still spend (counted by the server). */
    public static int skillPoints = 0;

    /** Learned skills: id -> rank. */
    public static final Map<String, Integer> ranks = new HashMap<>();

    /** Learned magics, in the order they were learned. */
    public static final Set<String> magics = new LinkedHashSet<>();

    /** The action bar: "" = empty slot, otherwise "skill:<id>" / "magic:<id>". */
    public static final String[] slots = new String[PlayerActionBar.SIZE];

    /** entry -> {start time in ms, duration in ms} */
    private static final Map<String, long[]> COOLDOWNS = new HashMap<>();

    static {
        Arrays.fill(slots, "");
    }

    public static void reset() {
        skillPoints = 0;
        ranks.clear();
        magics.clear();
        Arrays.fill(slots, "");
        COOLDOWNS.clear();
    }

    public static void updateFrom(SyncPowerPayload p) {
        skillPoints = p.skillPoints();
        ranks.clear();
        ranks.putAll(p.ranks());
        magics.clear();
        magics.addAll(p.magics());
        Arrays.fill(slots, "");
        for (int i = 0; i < slots.length && i < p.slots().size(); i++) {
            slots[i] = p.slots().get(i);
        }
    }

    public static int rank(String skillId) {
        return ranks.getOrDefault(skillId, 0);
    }

    // ─── Cooldowns ───────────────────────────────────────────────────

    public static void startCooldown(String entry, int ticks) {
        if (ticks <= 0) return;
        COOLDOWNS.put(entry, new long[]{System.currentTimeMillis(), ticks * 50L});
    }

    /** 0 = ready, 1 = just started. */
    public static float cooldownFraction(String entry) {
        long[] c = COOLDOWNS.get(entry);
        if (c == null) return 0f;
        long left = c[0] + c[1] - System.currentTimeMillis();
        if (left <= 0L) {
            COOLDOWNS.remove(entry);
            return 0f;
        }
        return Math.min(1f, left / (float) c[1]);
    }

    public static double cooldownSecondsLeft(String entry) {
        long[] c = COOLDOWNS.get(entry);
        if (c == null) return 0.0;
        long left = c[0] + c[1] - System.currentTimeMillis();
        return left <= 0L ? 0.0 : left / 1000.0;
    }
}
