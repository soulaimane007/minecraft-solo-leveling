package com.soulaimane.sololeveling.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.soulaimane.sololeveling.SoloLeveling;
import com.soulaimane.sololeveling.data.PlayerActionBar;
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

    /** Opens the skill tree (stats stay on K). */
    public static final KeyMapping OPEN_SKILLS = new KeyMapping(
            "key.sololeveling.open_skills",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY
    );

    /**
     * The six action bar keys, one per slot (default R G Z V B N: none of them is used by vanilla Minecraft).
     * Skills and magics both sit on the bar; change the keys in Options > Controls.
     */
    public static final KeyMapping[] SLOT_KEYS = {
            slotKey(1, GLFW.GLFW_KEY_R),
            slotKey(2, GLFW.GLFW_KEY_G),
            slotKey(3, GLFW.GLFW_KEY_Z),
            slotKey(4, GLFW.GLFW_KEY_V),
            slotKey(5, GLFW.GLFW_KEY_B),
            slotKey(6, GLFW.GLFW_KEY_N)
    };

    static {
        // The bar size lives in PlayerActionBar; a mismatch here would silently leave a slot without a key.
        if (SLOT_KEYS.length != PlayerActionBar.SIZE) {
            throw new IllegalStateException("SLOT_KEYS must have PlayerActionBar.SIZE (" + PlayerActionBar.SIZE + ") keys");
        }
    }

    private static KeyMapping slotKey(int number, int defaultKey) {
        return new KeyMapping("key.sololeveling.slot_" + number, InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PANEL);
        event.register(OPEN_SKILLS);
        for (KeyMapping key : SLOT_KEYS) {
            event.register(key);
        }
    }
}
