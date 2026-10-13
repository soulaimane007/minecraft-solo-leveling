package com.soulaimane.sololeveling.client;

import com.soulaimane.sololeveling.SoloLeveling;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = SoloLeveling.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SLClientSetup {

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        // Register our HUD above the vanilla hotbar layer
        event.registerAbove(VanillaGuiLayers.HOTBAR, SLHudOverlay.ID, new SLHudOverlay());
    }
}