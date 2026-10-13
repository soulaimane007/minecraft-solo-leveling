package com.soulaimane.sololeveling.event;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.client.ClientSystemDataCache;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.network.AllocateStatsPayload;
import com.soulaimane.sololeveling.network.SyncPlayerDataPayload;
import com.soulaimane.sololeveling.network.SyncStaminaPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModPayloadHandlers {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // S → C
        registrar.playToClient(SyncPlayerDataPayload.TYPE, SyncPlayerDataPayload.STREAM_CODEC,
                ModPayloadHandlers::handleFullSync);
        registrar.playToClient(SyncStaminaPayload.TYPE, SyncStaminaPayload.STREAM_CODEC,
                ModPayloadHandlers::handleStamina);

        // C → S
        registrar.playToServer(AllocateStatsPayload.TYPE, AllocateStatsPayload.STREAM_CODEC,
                ModPayloadHandlers::handleAllocateStats);
    }

    private static void handleFullSync(SyncPlayerDataPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSystemDataCache.updateFrom(p));
    }

    private static void handleStamina(SyncStaminaPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSystemDataCache.updateStamina(
                p.stamina(), p.maxStamina(), p.exhausted()));
    }

    // ─── Server-authoritative stat allocation ─────────────────────────

    private static void handleAllocateStats(AllocateStatsPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            PlayerSystemData d = player.getData(ModAttachments.PLAYER_SYSTEM_DATA);

            // Cost check
            int cost = p.totalCost();
            if (cost > d.statPoints()) return;

            // Bounds check — no stat may go below 1
            if (d.strength()     + p.dStrength()     < 1) return;
            if (d.agility()      + p.dAgility()      < 1) return;
            if (d.vitality()     + p.dVitality()     < 1) return;
            if (d.intelligence() + p.dIntelligence() < 1) return;
            if (d.sense()        + p.dSense()        < 1) return;
            if (d.dexterity()    + p.dDexterity()    < 1) return;
            if (d.durability()   + p.dDurability()   < 1) return;

            // Apply
            d = d
                    .withStat("strength",     d.strength()     + p.dStrength())
                    .withStat("agility",      d.agility()      + p.dAgility())
                    .withStat("vitality",     d.vitality()     + p.dVitality())
                    .withStat("intelligence", d.intelligence() + p.dIntelligence())
                    .withStat("sense",        d.sense()        + p.dSense())
                    .withStat("dexterity",    d.dexterity()    + p.dDexterity())
                    .withStat("durability",   d.durability()   + p.dDurability())
                    .withStatPoints(d.statPoints() - cost);

            player.setData(ModAttachments.PLAYER_SYSTEM_DATA, d);
            AttributeEvents.applyAllModifiers(player);
            PlayerDataEvents.syncToClient(player);
        });
    }
}