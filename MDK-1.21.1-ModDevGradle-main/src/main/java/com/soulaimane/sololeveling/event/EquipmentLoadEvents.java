package com.soulaimane.sololeveling.event;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.ModDataMaps;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class EquipmentLoadEvents {

    private static final EquipmentSlot[] TRACKED = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    @SubscribeEvent
    public static void onEquipChange(LivingEquipmentChangeEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        recalculate(p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) recalculate(p);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        if (e.getEntity() instanceof ServerPlayer p) recalculate(p);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) recalculate(p);
    }

    public static void recalculate(ServerPlayer p) {
        double load = 0;
        for (EquipmentSlot slot : TRACKED) {
            ItemStack stack = p.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            Double w = stack.getItemHolder().getData(ModDataMaps.WEIGHT);
            if (w != null) load += w;
        }
        PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        if (Math.abs(d.currentEquipLoad() - load) > 0.01) {
            p.setData(ModAttachments.PLAYER_SYSTEM_DATA, d.withEquipLoad(load));
            // the new load may move the player into another load state (speed penalty)
            AttributeEvents.applyLoadSpeed(p);
            PlayerDataEvents.syncToClient(p);
        }
    }
}