package com.soulaimane.sololeveling.power;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cooldowns of skills and magics (server side, in memory: they reset when the player logs out).
 * The key is the action bar entry: "skill:sololeveling:power_strike" or "magic:sololeveling:flame_bolt".
 */
public final class PowerCooldowns {
    private PowerCooldowns() {}

    /** player -> entry -> server tick at which the entry can be used again */
    private static final Map<UUID, Map<String, Integer>> READY_AT = new HashMap<>();

    private static int now(ServerPlayer p) {
        return p.getServer() != null ? p.getServer().getTickCount() : 0;
    }

    /** Ticks left before {@code entry} can be used again (0 = ready). */
    public static int remaining(ServerPlayer p, String entry) {
        Map<String, Integer> map = READY_AT.get(p.getUUID());
        if (map == null) return 0;
        Integer at = map.get(entry);
        if (at == null) return 0;
        int left = at - now(p);
        if (left <= 0) {
            map.remove(entry);
            return 0;
        }
        return left;
    }

    public static void start(ServerPlayer p, String entry, int ticks) {
        if (ticks <= 0) return;
        READY_AT.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).put(entry, now(p) + ticks);
    }

    public static void clear(UUID player) {
        READY_AT.remove(player);
    }

    public static void clearAll() {
        READY_AT.clear();
    }
}
