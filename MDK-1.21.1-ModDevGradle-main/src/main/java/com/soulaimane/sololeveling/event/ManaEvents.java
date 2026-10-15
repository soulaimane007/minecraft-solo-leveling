package com.soulaimane.sololeveling.event;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.network.SyncManaPayload;
import com.soulaimane.sololeveling.registry.ModAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mana: regeneration, clamping to the real maximum (MAX_MANA attribute) and a
 * throttled client sync.  Nothing in the mod spends mana yet (there are no skills),
 * but {@link #tryConsume} / {@link #restore} are ready for them.
 */
public class ManaEvents {

    private static final int   SYNC_INTERVAL_TICKS = 10;
    private static final float SYNC_EPSILON        = 0.25f;

    private record Synced(float mana, float max, int tick) {}

    /** Last values sent to each client (server side only). */
    private static final Map<UUID, Synced> LAST_SYNCED = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────
    // Tick: regen + clamp + sync
    // ─────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (!p.isAlive()) return;

        PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        float max  = getMaxMana(p);
        float mana = d.mana();
        float next = mana;

        if (mana > max) {
            next = max;                                   // max mana dropped → clamp
        } else if (mana < max) {
            float regen = getManaRegen(p);                // per second
            if (regen > 0f) next = Math.min(max, mana + regen / 20f);
        }
        if (next < 0f) next = 0f;

        if (next != mana) {
            p.setData(ModAttachments.PLAYER_SYSTEM_DATA, d.withMana(next));
        }

        // ── throttled sync ──
        Synced last = LAST_SYNCED.get(p.getUUID());
        int now = p.tickCount;
        boolean first      = last == null;
        boolean maxChanged = first || Math.abs(last.max() - max) > 0.01f;
        boolean due        = first || now - last.tick() >= SYNC_INTERVAL_TICKS;
        boolean moved      = !first && Math.abs(last.mana() - next) >= SYNC_EPSILON;
        boolean hitEdge    = !first && ((next >= max && last.mana() < max) || (next <= 0f && last.mana() > 0f));

        if (maxChanged || (due && (moved || hitEdge))) {
            sendManaSync(p, next, max);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        LAST_SYNCED.remove(e.getEntity().getUUID());
    }

    // ─────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────

    /** Removes mana if the player has enough. Returns false (and changes nothing) otherwise. */
    public static boolean tryConsume(ServerPlayer p, float amount) {
        if (amount <= 0f) return true;
        PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        if (d.mana() < amount) return false;
        p.setData(ModAttachments.PLAYER_SYSTEM_DATA, d.withMana(d.mana() - amount));
        sendManaSync(p);
        return true;
    }

    /** Adds mana (never above the maximum). */
    public static void restore(ServerPlayer p, float amount) {
        if (amount <= 0f) return;
        PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        float next = Math.min(getMaxMana(p), d.mana() + amount);
        p.setData(ModAttachments.PLAYER_SYSTEM_DATA, d.withMana(next));
        sendManaSync(p);
    }

    public static void fill(ServerPlayer p) {
        PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        p.setData(ModAttachments.PLAYER_SYSTEM_DATA, d.withMana(getMaxMana(p)));
        sendManaSync(p);
    }

    public static float getMaxMana(ServerPlayer p) {
        AttributeInstance a = p.getAttribute(ModAttributes.MAX_MANA);
        return a != null ? (float) a.getValue() : 20f;
    }

    public static float getManaRegen(ServerPlayer p) {
        AttributeInstance a = p.getAttribute(ModAttributes.MANA_REGEN);
        return a != null ? (float) a.getValue() : 0f;
    }

    /** Sends the current mana from the player's data. */
    public static void sendManaSync(ServerPlayer p) {
        PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        sendManaSync(p, d.mana(), getMaxMana(p));
    }

    public static void sendManaSync(ServerPlayer p, float mana, float max) {
        PacketDistributor.sendToPlayer(p, new SyncManaPayload(mana, max));
        LAST_SYNCED.put(p.getUUID(), new Synced(mana, max, p.tickCount));
    }
}
