package com.soulaimane.sololeveling.client;

import com.soulaimane.sololeveling.SoloLeveling;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * The action bar: the six slots (skills and magics) in a column on the right edge of the screen.
 * Each slot shows its icon, the key that uses it (left of the slot) and a cooldown that drains while it recharges.
 * The bar stays hidden until something is put on it.
 *
 * To move it: change SLOT_SIZE / GAP / MARGIN, or the x and y computed in render.
 */
public class ActionBarHud implements LayeredDraw.Layer {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            SoloLeveling.MOD_ID, "action_bar");

    private static final int SLOT_SIZE = 22;
    private static final int GAP = 2;
    private static final int MARGIN = 6;

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;

        boolean anything = false;
        for (String entry : ClientPowerCache.slots) {
            if (!entry.isEmpty()) {
                anything = true;
                break;
            }
        }
        if (!anything) return;

        int count = ClientPowerCache.slots.length;
        int totalHeight = count * SLOT_SIZE + (count - 1) * GAP;
        int x = g.guiWidth() - MARGIN - SLOT_SIZE;
        int top = (g.guiHeight() - totalHeight) / 2;

        for (int i = 0; i < count; i++) {
            drawSlot(g, mc, i, x, top + i * (SLOT_SIZE + GAP));
        }
    }

    private static void drawSlot(GuiGraphics g, Minecraft mc, int index, int x, int y) {
        String entry = ClientPowerCache.slots[index];
        PowerEntry view = PowerEntry.of(entry);

        g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xAA000000);
        border(g, x, y, SLOT_SIZE, SLOT_SIZE, view != null ? view.color() : 0xFF3A3A3A);

        // The key that uses this slot, to the left of it
        Component key = SLKeybinds.SLOT_KEYS[index].getTranslatedKeyMessage();
        g.drawString(mc.font, key, x - 4 - mc.font.width(key), y + (SLOT_SIZE - 8) / 2,
                view != null ? 0xFFDDDDDD : 0xFF666666);

        if (view == null) return;
        g.renderItem(view.icon(), x + (SLOT_SIZE - 16) / 2, y + (SLOT_SIZE - 16) / 2);

        // Cooldown: a dark veil that shrinks downwards, with the seconds left on top.
        // Items are drawn "in front" (z 150), so the veil and the number are lifted above them.
        float fraction = ClientPowerCache.cooldownFraction(view.entry());
        if (fraction > 0f) {
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 200f);
            int inner = SLOT_SIZE - 2;
            int top = y + 1 + Math.round(inner * (1f - fraction));
            g.fill(x + 1, top, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xB0000000);

            double seconds = ClientPowerCache.cooldownSecondsLeft(view.entry());
            String text = seconds >= 10.0 ? Long.toString(Math.round(seconds)) : String.format(Locale.ROOT, "%.1f", seconds);
            g.drawString(mc.font, text, x + (SLOT_SIZE - mc.font.width(text)) / 2, y + (SLOT_SIZE - 8) / 2, 0xFFFFFFFF);
            g.pose().popPose();
        }
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
