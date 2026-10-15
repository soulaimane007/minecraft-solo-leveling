package com.soulaimane.sololeveling.client;

import com.soulaimane.sololeveling.SoloLeveling;
import com.soulaimane.sololeveling.data.EquipmentLoadState;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.data.Rank;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

/**
 * Always-on status window (top-left corner, so it never overlaps the chat or hotbar):
 *
 *   Lv.12  [D]
 *   XP   ▮▮▮▮▯▯▯▯
 *   STA  ▮▮▮▮▮▮▯▯  80/100
 *   MP   ▮▮▮▮▯▯▯▯  12/20
 *   Load 12.5 / 30.0 (Medium)
 */
public class SLHudOverlay implements LayeredDraw.Layer {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            SoloLeveling.MOD_ID, "system_hud");

    private static final int X       = 6;
    private static final int Y       = 6;
    private static final int LABEL_W = 22;
    private static final int BAR_W   = 84;
    private static final int BAR_H   = 7;
    private static final int ROW_H   = 11;

    // ARGB colours
    private static final int COL_BORDER  = 0xFF3A3A3A;
    private static final int COL_BACK    = 0xAA000000;
    private static final int COL_XP      = 0xFF55FF55;
    private static final int COL_STAMINA = 0xFF3FBF3F;
    private static final int COL_EXHAUST = 0xFFE03030;
    private static final int COL_MANA    = 0xFF4A6CFF;

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.player.isSpectator()) return;

        int y = Y;

        // ── Level + rank ──
        Rank rank = Rank.byOrdinal(ClientSystemDataCache.rankOrdinal);
        g.drawString(mc.font, "Lv." + ClientSystemDataCache.level, X, y, 0xFFFFD34D);
        int rankX = X + mc.font.width("Lv." + ClientSystemDataCache.level) + 6;
        g.drawString(mc.font, "[" + rank.getSerializedName() + "]", rankX, y, rankColor(rank));

        // Unspent points hint (blinks)
        if (ClientSystemDataCache.statPoints > 0 && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            String hint = "+" + ClientSystemDataCache.statPoints + " SP";
            int hintX = rankX + mc.font.width("[" + rank.getSerializedName() + "]") + 8;
            g.drawString(mc.font, hint, hintX, y, 0xFF7CFF7C);
        }
        y += ROW_H + 1;

        // ── XP ──
        int needed = PlayerSystemData.xpForNextLevel(ClientSystemDataCache.level);
        float xpFrac = ClientSystemDataCache.level >= PlayerSystemData.MAX_LEVEL
                ? 1f : (needed > 0 ? ClientSystemDataCache.xp / (float) needed : 0f);
        drawRow(g, mc, y, "XP", xpFrac, COL_XP,
                ClientSystemDataCache.level >= PlayerSystemData.MAX_LEVEL
                        ? "MAX" : Math.round(xpFrac * 100f) + "%");
        y += ROW_H;

        // ── Stamina ──
        float maxSta = Math.max(0.01f, ClientSystemDataCache.maxStamina);
        float staFrac = ClientSystemDataCache.stamina / maxSta;
        drawRow(g, mc, y, "STA", staFrac,
                ClientSystemDataCache.exhausted ? COL_EXHAUST : COL_STAMINA,
                Math.round(ClientSystemDataCache.stamina) + "/" + Math.round(maxSta));
        y += ROW_H;

        // ── Mana ──
        float maxMana = Math.max(0.01f, ClientSystemDataCache.maxMana);
        float manaFrac = ClientSystemDataCache.mana / maxMana;
        drawRow(g, mc, y, "MP", manaFrac, COL_MANA,
                Math.round(ClientSystemDataCache.mana) + "/" + Math.round(maxMana));
        y += ROW_H + 1;

        // ── Equip load ──
        int ordinal = Math.max(0, Math.min(EquipmentLoadState.values().length - 1,
                ClientSystemDataCache.loadStateOrdinal));
        EquipmentLoadState state = EquipmentLoadState.values()[ordinal];
        String stateName = state.name().charAt(0) + state.name().substring(1).toLowerCase();
        String loadText = String.format("Load %.1f / %.1f (%s)",
                ClientSystemDataCache.equipLoad, ClientSystemDataCache.maxEquipLoad, stateName);
        g.drawString(mc.font, loadText, X, y, loadColor(state));
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private static void drawRow(GuiGraphics g, Minecraft mc, int y, String label,
                                float frac, int color, String valueText) {
        g.drawString(mc.font, label, X, y, 0xFFDDDDDD);
        drawBar(g, X + LABEL_W, y, BAR_W, BAR_H, frac, color);
        g.drawString(mc.font, valueText, X + LABEL_W + BAR_W + 4, y, 0xFFFFFFFF);
    }

    private static void drawBar(GuiGraphics g, int x, int y, int w, int h, float frac, int color) {
        frac = Math.max(0f, Math.min(1f, frac));
        g.fill(x, y, x + w, y + h, COL_BORDER);                       // border
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, COL_BACK);         // back
        int fillW = (int) ((w - 2) * frac);
        if (fillW > 0) {
            g.fill(x + 1, y + 1, x + 1 + fillW, y + h - 1, color);    // fill
            g.fill(x + 1, y + 1, x + 1 + fillW, y + 2, 0x55FFFFFF);   // soft highlight on top
        }
    }

    private static int rankColor(Rank rank) {
        return switch (rank) {
            case E -> 0xFFAAAAAA;
            case D -> 0xFF55FF55;
            case C -> 0xFF55FFFF;
            case B -> 0xFF5588FF;
            case A -> 0xFFFFAA00;
            case S -> 0xFFFF5555;
        };
    }

    private static int loadColor(EquipmentLoadState state) {
        return switch (state) {
            case LIGHT      -> 0xFF8CFF8C;
            case MEDIUM     -> 0xFFFFE066;
            case HEAVY      -> 0xFFFFA040;
            case OVERWEIGHT -> 0xFFFF5555;
        };
    }
}
