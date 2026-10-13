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

public class SLHudOverlay implements LayeredDraw.Layer {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            SoloLeveling.MOD_ID, "system_hud");

    private static final int BAR_W = 80;
    private static final int BAR_H = 5;

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.player.isSpectator()) return;

        int screenH = g.guiHeight();
        int x = 10;
        int y = screenH - 90;

        // ── Level + Rank ──
        String levelRank = "§6Lv." + ClientSystemDataCache.level
                + " §e" + Rank.byOrdinal(ClientSystemDataCache.rankOrdinal).getSerializedName();
        g.drawString(mc.font, levelRank, x, y, 0xFFFFFF);

        // ── XP bar ──
        int xpY = y + 10;
        int needed = PlayerSystemData.xpForNextLevel(ClientSystemDataCache.level);
        float xpFrac = needed > 0 ? Math.min(1f, ClientSystemDataCache.xp / (float) needed) : 0f;
        g.fill(x, xpY, x + BAR_W, xpY + BAR_H, 0xFF000000);
        g.fill(x, xpY, x + (int)(BAR_W * xpFrac), xpY + BAR_H, 0xFF00FF00);

        // ── Stamina bar ──
        int staY = xpY + 10;
        float maxSta = Math.max(0.01f, ClientSystemDataCache.maxStamina);
        float staFrac = Math.min(1f, ClientSystemDataCache.stamina / maxSta);
        int staColor = ClientSystemDataCache.exhausted ? 0xFFFF0000 : 0xFF00AA00;
        g.fill(x, staY, x + BAR_W, staY + BAR_H, 0xFF000000);
        g.fill(x, staY, x + (int)(BAR_W * staFrac), staY + BAR_H, staColor);
        g.drawString(mc.font,
                String.format("%.0f/%.0f", ClientSystemDataCache.stamina, maxSta),
                x + BAR_W + 3, staY - 2, 0xFFFFFF);

        // ── Mana bar ──
        int manaY = staY + 10;
        float manaFrac = Math.min(1f, ClientSystemDataCache.mana / PlayerSystemData.MAX_MANA);
        g.fill(x, manaY, x + BAR_W, manaY + BAR_H, 0xFF000000);
        g.fill(x, manaY, x + (int)(BAR_W * manaFrac), manaY + BAR_H, 0xFF4444FF);
        g.drawString(mc.font, String.format("%.0f", ClientSystemDataCache.mana),
                x + BAR_W + 3, manaY - 2, 0xFFFFFF);

        // ── Equip load ──
        int loadY = manaY + 10;
        EquipmentLoadState state = EquipmentLoadState.values()[ClientSystemDataCache.loadStateOrdinal];
        String loadText = String.format("Load %.1f / %.1f (%s)",
                ClientSystemDataCache.equipLoad, ClientSystemDataCache.maxEquipLoad,
                state.name().charAt(0) + state.name().substring(1).toLowerCase());
        g.drawString(mc.font, loadText, x, loadY, 0xFFFFFF);
    }
}