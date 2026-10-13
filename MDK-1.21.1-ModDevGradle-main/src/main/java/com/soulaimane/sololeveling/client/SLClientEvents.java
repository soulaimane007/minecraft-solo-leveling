package com.soulaimane.sololeveling.client;

import com.soulaimane.sololeveling.SoloLeveling;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = SoloLeveling.MOD_ID, value = Dist.CLIENT)
public class SLClientEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Open the panel on key press, but only if no other screen is open
        while (SLKeybinds.OPEN_PANEL.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new SystemPanelScreen());
            }
        }
    }
}