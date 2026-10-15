package com.soulaimane.sololeveling.event;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.EquipmentLoadState;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.network.SyncPlayerDataPayload;
import com.soulaimane.sololeveling.power.PowerSync;
import com.soulaimane.sololeveling.registry.ModAttributes;
import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class PlayerDataEvents {

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) syncToClient(p);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        if (e.getOriginal().hasData(ModAttachments.PLAYER_SYSTEM_DATA)
                && e.getEntity() instanceof ServerPlayer p) {
            PlayerSystemData orig = e.getOriginal().getData(ModAttachments.PLAYER_SYSTEM_DATA);
            p.setData(ModAttachments.PLAYER_SYSTEM_DATA, orig);
            syncToClient(p);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) syncToClient(p);
    }

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (e.getAmount() > 0) applySystemXp(p, e.getAmount());
        e.setCanceled(true);
        zeroVanillaXp(p);
    }

    @SubscribeEvent
    public static void onPickupXp(PlayerXpEvent.PickupXp e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        int v = e.getOrb().getValue();
        if (v > 0) applySystemXp(p, v);
        e.getOrb().discard();
        e.setCanceled(true);
    }

    private static void applySystemXp(ServerPlayer p, int amount) {
        PlayerSystemData old = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        PlayerSystemData updated = old.addXp(amount);
        p.setData(ModAttachments.PLAYER_SYSTEM_DATA, updated);

        int gained = updated.level() - old.level();
        if (gained > 0) {
            int sp = gained * PlayerSystemData.STAT_POINTS_PER_LEVEL;
            p.sendSystemMessage(Component.literal(
                    "§6[System] §aLevel Up! §eLv." + old.level() + " §7→ §eLv." + updated.level()
                            + " §7(+" + sp + " SP)"));
            if (updated.rank() != old.rank()) {
                p.sendSystemMessage(Component.literal(
                        "§6[System] §bRank: §f" + old.rank().getSerializedName()
                                + " §7→ §f" + updated.rank().getSerializedName()));
            }
            SLUtil.title(p,
                    Component.literal("§6§lLEVEL UP"),
                    Component.literal("§eLv." + old.level() + " §7→ §eLv." + updated.level()
                            + "   §a+" + sp + " SP  §8(press K)"),
                    5, 40, 15);
            SLUtil.playSound(p, "minecraft:entity.player.levelup", SoundSource.PLAYERS, 1.0f, 1.0f);
            AttributeEvents.applyAllModifiers(p);
        } else if (amount > 0) {
            SLUtil.actionBar(p, "§a+" + amount + " XP");
        }
        syncToClient(p);
    }

    /**
     * Sends the full system data (plus the real max mana) to the player.
     * Also sends the skill / magic / action bar state: the number of skill points depends on the level,
     * so every level-up and every stat change refreshes it too.
     */
    public static void syncToClient(ServerPlayer p) {
        if (!p.hasData(ModAttachments.PLAYER_SYSTEM_DATA)) return;
        PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);

        AttributeInstance maxLoadAttr = p.getAttribute(ModAttributes.MAX_EQUIP_LOAD);
        double maxLoad = maxLoadAttr != null ? maxLoadAttr.getValue() : 30.0;

        int loadState = EquipmentLoadState
                .fromRatio(maxLoad > 0 ? d.currentEquipLoad() / maxLoad : 0)
                .ordinal();

        PacketDistributor.sendToPlayer(p, SyncPlayerDataPayload.from(d, maxLoad, loadState));
        ManaEvents.sendManaSync(p);
        StaminaEvents.sendStaminaSync(p, d);
        PowerSync.sync(p);
        zeroVanillaXp(p);
    }

    private static void zeroVanillaXp(ServerPlayer p) {
        p.setExperienceLevels(0);
        p.setExperiencePoints(0);
    }

    public static void giveSystemXp(ServerPlayer p, int amount) {
        applySystemXp(p, amount);
    }
}
