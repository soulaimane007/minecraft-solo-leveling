package com.soulaimane.sololeveling.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.soulaimane.sololeveling.SoloLeveling;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = SoloLeveling.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SLKeybinds {

    public static final String CATEGORY = "key.categories.sololeveling";

    public static final KeyMapping OPEN_PANEL = new KeyMapping(
            "key.sololeveling.open_panel",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY
    );

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PANEL);
    }
}