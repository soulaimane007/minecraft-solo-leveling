package com.soulaimane.sololeveling.client;

import com.soulaimane.sololeveling.SoloLeveling;
import com.soulaimane.sololeveling.network.UseSlotPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

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

        // Skill tree (J)
        while (SLKeybinds.OPEN_SKILLS.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new SkillTreeScreen());
            }
        }

        // Action bar: the key of a slot asks the server to use what is on it. The server decides (cooldown, cost...).
        for (int slot = 0; slot < SLKeybinds.SLOT_KEYS.length; slot++) {
            while (SLKeybinds.SLOT_KEYS[slot].consumeClick()) {
                if (mc.screen == null) {
                    PacketDistributor.sendToServer(new UseSlotPayload(slot));
                }
            }
        }
    }

    /** Forget the previous world's numbers when leaving it. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSystemDataCache.reset();
        ClientPowerCache.reset();
    }
}
