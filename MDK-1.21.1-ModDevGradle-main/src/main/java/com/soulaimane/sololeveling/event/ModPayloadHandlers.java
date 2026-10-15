package com.soulaimane.sololeveling.event;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.SoloLeveling;
import com.soulaimane.sololeveling.client.ClientPowerCache;
import com.soulaimane.sololeveling.client.ClientSystemDataCache;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.network.AllocateStatsPayload;
import com.soulaimane.sololeveling.network.SetSlotPayload;
import com.soulaimane.sololeveling.network.SyncCooldownPayload;
import com.soulaimane.sololeveling.network.SyncManaPayload;
import com.soulaimane.sololeveling.network.SyncPlayerDataPayload;
import com.soulaimane.sololeveling.network.SyncPowerPayload;
import com.soulaimane.sololeveling.network.SyncStaminaPayload;
import com.soulaimane.sololeveling.network.UnlockSkillPayload;
import com.soulaimane.sololeveling.network.UseSlotPayload;
import com.soulaimane.sololeveling.power.ActionBarManager;
import com.soulaimane.sololeveling.skill.SkillManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Consumer;

public class ModPayloadHandlers {

    /** Bumped to "3" because the skill tree / magic / action bar payloads were added ("2" added the mana payload). */
    private static final String PROTOCOL_VERSION = "3";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // S → C
        registrar.playToClient(SyncPlayerDataPayload.TYPE, SyncPlayerDataPayload.STREAM_CODEC,
                ModPayloadHandlers::handleFullSync);
        registrar.playToClient(SyncStaminaPayload.TYPE, SyncStaminaPayload.STREAM_CODEC,
                ModPayloadHandlers::handleStamina);
        registrar.playToClient(SyncManaPayload.TYPE, SyncManaPayload.STREAM_CODEC,
                ModPayloadHandlers::handleMana);
        registrar.playToClient(SyncPowerPayload.TYPE, SyncPowerPayload.STREAM_CODEC,
                ModPayloadHandlers::handlePower);
        registrar.playToClient(SyncCooldownPayload.TYPE, SyncCooldownPayload.STREAM_CODEC,
                ModPayloadHandlers::handleCooldown);

        // C → S
        registrar.playToServer(AllocateStatsPayload.TYPE, AllocateStatsPayload.STREAM_CODEC,
                ModPayloadHandlers::handleAllocateStats);
        registrar.playToServer(UnlockSkillPayload.TYPE, UnlockSkillPayload.STREAM_CODEC,
                ModPayloadHandlers::handleUnlockSkill);
        registrar.playToServer(SetSlotPayload.TYPE, SetSlotPayload.STREAM_CODEC,
                ModPayloadHandlers::handleSetSlot);
        registrar.playToServer(UseSlotPayload.TYPE, UseSlotPayload.STREAM_CODEC,
                ModPayloadHandlers::handleUseSlot);
    }

    private static void handleFullSync(SyncPlayerDataPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSystemDataCache.updateFrom(p));
    }

    private static void handleStamina(SyncStaminaPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSystemDataCache.updateStamina(
                p.stamina(), p.maxStamina(), p.exhausted()));
    }

    private static void handleMana(SyncManaPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSystemDataCache.updateMana(p.mana(), p.maxMana()));
    }

    private static void handlePower(SyncPowerPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPowerCache.updateFrom(p));
    }

    private static void handleCooldown(SyncCooldownPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPowerCache.startCooldown(p.entry(), p.ticks()));
    }

    // ─── Server-authoritative skill tree and action bar ───────────────
    // The client only ASKS. The server checks every rule again; a forged packet just gets refused.
    // A bug in a skill must never kick the player, so the work is wrapped (see run).

    private static void handleUnlockSkill(UnlockSkillPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> run("unlock skill", ctx, player -> SkillManager.tryUnlock(player, p.skillId())));
    }

    private static void handleSetSlot(SetSlotPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> run("set action bar slot", ctx, player -> ActionBarManager.set(player, p.slot(), p.entry())));
    }

    private static void handleUseSlot(UseSlotPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> run("use action bar slot", ctx, player -> ActionBarManager.use(player, p.slot())));
    }

    private static void run(String what, IPayloadContext ctx, Consumer<ServerPlayer> action) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        try {
            action.accept(player);
        } catch (RuntimeException e) {
            SoloLeveling.LOGGER.error("Could not {} for {}", what, player.getGameProfile().getName(), e);
        }
    }

    // ─── Server-authoritative stat allocation ─────────────────────────

    private static void handleAllocateStats(AllocateStatsPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            // The panel only ever sends positive increments — anything else is a forged packet.
            if (p.dStrength() < 0 || p.dAgility() < 0 || p.dVitality() < 0
                    || p.dIntelligence() < 0 || p.dSense() < 0
                    || p.dDexterity() < 0 || p.dDurability() < 0) return;

            PlayerSystemData d = player.getData(ModAttachments.PLAYER_SYSTEM_DATA);

            // Cost check (long math: a forged packet cannot overflow it)
            long cost = p.totalCost();
            if (cost <= 0 || cost > d.statPoints()) return;

            int spent = (int) cost;
            d = d
                    .withStat("strength",     d.strength()     + p.dStrength())
                    .withStat("agility",      d.agility()      + p.dAgility())
                    .withStat("vitality",     d.vitality()     + p.dVitality())
                    .withStat("intelligence", d.intelligence() + p.dIntelligence())
                    .withStat("sense",        d.sense()        + p.dSense())
                    .withStat("dexterity",    d.dexterity()    + p.dDexterity())
                    .withStat("durability",   d.durability()   + p.dDurability())
                    .withStatPoints(d.statPoints() - spent);

            player.setData(ModAttachments.PLAYER_SYSTEM_DATA, d);
            AttributeEvents.applyAllModifiers(player);
            PlayerDataEvents.syncToClient(player);
        });
    }
}
