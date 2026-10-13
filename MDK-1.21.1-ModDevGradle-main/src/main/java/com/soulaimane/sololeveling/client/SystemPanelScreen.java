package com.soulaimane.sololeveling.client;

import com.soulaimane.sololeveling.data.EquipmentLoadState;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.data.Rank;
import com.soulaimane.sololeveling.data.SoftCap;
import com.soulaimane.sololeveling.event.WeaponDamageEvents;
import com.soulaimane.sololeveling.network.AllocateStatsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class SystemPanelScreen extends Screen {

    // ── Panel dimensions ──
    private static final int WIDTH  = 400;
    private static final int HEIGHT = 285;

    private static final String[] STAT_KEYS = {
            "strength", "agility", "vitality", "intelligence",
            "sense", "dexterity", "durability"
    };

    private static final String[] STAT_LABELS = {
            "Strength", "Agility", "Vitality", "Intelligence",
            "Sense", "Dexterity", "Durability"
    };

    // Layout constants
    private static final int ROW_START_OFFSET = 74;
    private static final int ROW_H            = 19;
    private static final int PLUS_X_OFFSET    = WIDTH - 44;
    private static final int PLUS_W           = 24;
    private static final int PLUS_H           = 17;
    private static final int LINE_H           = 10;

    private int left, top;

    public SystemPanelScreen() {
        super(Component.literal("System"));
    }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width  - WIDTH)  / 2;
        this.top  = (this.height - HEIGHT) / 2;
    }

    // ─────────────────────────────────────────────────────────────────
    // CLICK HANDLING
    // ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int rowStartY = top + ROW_START_OFFSET;
        int plusX1 = left + PLUS_X_OFFSET;
        int plusX2 = plusX1 + PLUS_W;

        for (int i = 0; i < STAT_KEYS.length; i++) {
            int ry = rowStartY + i * ROW_H;
            if (mouseX >= plusX1 && mouseX <= plusX2
                    && mouseY >= ry && mouseY <= ry + PLUS_H) {
                allocate(STAT_KEYS[i]);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void allocate(String stat) {
        if (ClientSystemDataCache.statPoints <= 0) return;

        int dStr = 0, dAgi = 0, dVit = 0, dInt = 0, dSen = 0, dDex = 0, dDur = 0;
        switch (stat) {
            case "strength"     -> dStr = 1;
            case "agility"      -> dAgi = 1;
            case "vitality"     -> dVit = 1;
            case "intelligence" -> dInt = 1;
            case "sense"        -> dSen = 1;
            case "dexterity"    -> dDex = 1;
            case "durability"   -> dDur = 1;
        }

        PacketDistributor.sendToServer(new AllocateStatsPayload(
                dStr, dAgi, dVit, dInt, dSen, dDex, dDur));

        ClientSystemDataCache.statPoints -= 1;
        ClientSystemDataCache.depositStat(stat, 1);
    }

    // ─────────────────────────────────────────────────────────────────
    // RENDERING
    // ─────────────────────────────────────────────────────────────────

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Fully opaque background — no world bleed-through
        g.fill(0, 0, this.width, this.height, 0xFF000000);
        g.fill(left, top, left + WIDTH, top + HEIGHT, 0xFF101010);

        // Border
        drawBorder(g, left, top, WIDTH, HEIGHT, 0xFFFFAA00);
        drawBorder(g, left + 2, top + 2, WIDTH - 4, HEIGHT - 4, 0xFF333333);

        // Divider
        int divX = left + 210;
        g.fill(divX, top + 28, divX + 1, top + HEIGHT - 12, 0xFF444444);

        // Title bar
        g.fill(left + 2, top + 2, left + WIDTH - 2, top + 24, 0xFF1A1A1A);
        g.fill(left + 2, top + 24, left + WIDTH - 2, top + 25, 0xFFFFAA00);
        g.drawCenteredString(font, "§6═══  S Y S T E M  ═══",
                left + WIDTH / 2, top + 8, 0xFFFFFF);

        renderCharacterPanel(g);
        renderStatsPanel(g, mouseX, mouseY);

        // Footer hint
        g.drawCenteredString(font, "§8Press ESC to close",
                left + WIDTH / 2, top + HEIGHT - 12, 0xFFFFFF);
    }

    // ─────────────────────────────────────────────────────────────────
    // LEFT PANEL — character details
    // ─────────────────────────────────────────────────────────────────

    private void renderCharacterPanel(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Rank rank = Rank.byOrdinal(ClientSystemDataCache.rankOrdinal);

        int x = left + 12;
        int y = top + 32;

        // ── Header ──
        g.drawString(font, "§6▸ Character", x, y, 0xFFFFFF);
        y += 12;

        g.drawString(font, "§eLv: §f" + ClientSystemDataCache.level
                + "   §eRank: §f" + rank.getSerializedName(), x, y, 0xFFFFFF);
        y += LINE_H;

        int needed = PlayerSystemData.xpForNextLevel(ClientSystemDataCache.level);
        g.drawString(font, "§eXP: §f" + ClientSystemDataCache.xp + " / " + needed,
                x, y, 0xFFFFFF);
        y += LINE_H + 2;

        // ── HP ──
        double curHp    = mc.player.getHealth();
        double curMaxHp = mc.player.getMaxHealth();
        g.drawString(font, "§c❤ HP: §f" + fmt(curHp) + " §7/ §f" + fmt(curMaxHp),
                x, y, 0xFFFFFF);
        y += 9;
        drawBar(g, x, y, 185, 4, curHp / Math.max(0.01, curMaxHp), 0xFFAA0000);
        y += 9;

        // ── Stamina ──
        int staColor = ClientSystemDataCache.exhausted ? 0xFFFF0000 : 0xFF00AA00;
        g.drawString(font, "§a⚡ Stamina: §f" + fmt(ClientSystemDataCache.stamina)
                + " §7/ §f" + fmt(ClientSystemDataCache.maxStamina), x, y, 0xFFFFFF);
        y += 9;
        drawBar(g, x, y, 185, 4,
                ClientSystemDataCache.stamina / Math.max(0.01, ClientSystemDataCache.maxStamina),
                staColor);
        y += 9;

        EquipmentLoadState loadState =
                EquipmentLoadState.values()[ClientSystemDataCache.loadStateOrdinal];
        double regen = StatFormulas.staminaRegen(
                ClientSystemDataCache.agility, rank, loadState);
        g.drawString(font, "§7  Regen: §f" + fmt2(regen) + "§7/s", x, y, 0xFFFFFF);
        y += LINE_H;

        // ── Mana ──
        double curMaxMana = StatFormulas.maxMana(ClientSystemDataCache.intelligence, rank);
        g.drawString(font, "§b✦ Mana: §f" + fmt(ClientSystemDataCache.mana)
                + " §7/ §f" + fmt(curMaxMana), x, y, 0xFFFFFF);
        y += 9;
        drawBar(g, x, y, 185, 4,
                ClientSystemDataCache.mana / Math.max(0.01, curMaxMana), 0xFF4444FF);
        y += 9;

        double manaRegen = StatFormulas.manaRegen(ClientSystemDataCache.intelligence, rank);
        g.drawString(font, "§7  Regen: §f" + fmt2(manaRegen) + "§7/s", x, y, 0xFFFFFF);
        y += LINE_H + 2;

        // ── Combat ──
        g.drawString(font, "§6▸ Combat", x, y, 0xFFFFFF); y += LINE_H;

        // Weapon-aware attack damage
        ItemStack held = mc.player.getMainHandItem();
        double weaponBase = WeaponDamageEvents.getWeaponBase(held);
        double atk = WeaponDamageEvents.computeDamage(
                ClientSystemDataCache.strength, rank, weaponBase);

        double atkSpd = mc.player.getAttributeValue(Attributes.ATTACK_SPEED);
        double move   = mc.player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double knock  = StatFormulas.knockback(ClientSystemDataCache.strength, rank);
        double range  = StatFormulas.interactionRange(ClientSystemDataCache.sense, rank);
        double dr     = StatFormulas.damageReduction(ClientSystemDataCache.durability, rank);
        double magic  = StatFormulas.magicDamage(ClientSystemDataCache.intelligence, rank);

        g.drawString(font, "§eAttack Damage: §f" + fmt2(atk)
                + " §7(wpn " + fmt2(weaponBase) + ")", x, y, 0xFFFFFF); y += LINE_H;
        g.drawString(font, "§eAttack Speed: §f"  + fmt2(atkSpd), x, y, 0xFFFFFF); y += LINE_H;
        g.drawString(font, "§eMove Speed: §f"    + fmt2(move),   x, y, 0xFFFFFF); y += LINE_H;
        g.drawString(font, "§d✦ Magic Damage: §f" + fmt2(magic), x, y, 0xFFFFFF); y += LINE_H;
        g.drawString(font, "§eKnockback: §f"     + fmt2(knock),  x, y, 0xFFFFFF); y += LINE_H;
        g.drawString(font, "§eRange Bonus: §f"   + fmt2(range),  x, y, 0xFFFFFF); y += LINE_H;
        g.drawString(font, "§eDR: §f" + String.format("%.1f%%", dr * 100),
                x, y, 0xFFFFFF); y += LINE_H + 2;

        // ── Equip Load ──
        g.drawString(font, "§6▸ Equip Load", x, y, 0xFFFFFF); y += LINE_H;
        g.drawString(font, "§7  Load: §f" + fmt2(ClientSystemDataCache.equipLoad)
                        + " §7/ §f" + fmt2(ClientSystemDataCache.maxEquipLoad),
                x, y, 0xFFFFFF); y += LINE_H;
        g.drawString(font, "§7  State: §f[" + loadState.name() + "]", x, y, 0xFFFFFF);
    }

    // ─────────────────────────────────────────────────────────────────
    // RIGHT PANEL — stat allocation
    // ─────────────────────────────────────────────────────────────────

    private void renderStatsPanel(GuiGraphics g, int mouseX, int mouseY) {
        Rank rank = Rank.byOrdinal(ClientSystemDataCache.rankOrdinal);

        int x = left + 222;
        int y = top + 32;

        g.drawString(font, "§6▸ Stats", x, y, 0xFFFFFF);
        y += 12;

        g.drawString(font, "§eSP: §f" + ClientSystemDataCache.statPoints,
                x, y, 0xFFFFFF);

        boolean canAllocate = ClientSystemDataCache.statPoints > 0;
        String hint = canAllocate ? "§8Click [+] to spend." : "§8No points.";
        g.drawString(font, hint, x + 60, y, 0xFFFFFF);

        int rowStartY = top + ROW_START_OFFSET;
        int plusX = left + PLUS_X_OFFSET;

        for (int i = 0; i < STAT_KEYS.length; i++) {
            String key   = STAT_KEYS[i];
            String label = STAT_LABELS[i];
            int cur = ClientSystemDataCache.getStat(key);

            int ry = rowStartY + i * ROW_H;

            // Row stripe
            g.fill(left + 214, ry - 1, left + WIDTH - 4, ry + 16, 0xFF151515);

            // Hover highlight for the [+] area
            boolean hovered = canAllocate
                    && mouseX >= plusX && mouseX <= plusX + PLUS_W
                    && mouseY >= ry      && mouseY <= ry + PLUS_H;
            if (hovered) {
                g.fill(plusX, ry, plusX + PLUS_W, ry + PLUS_H, 0xFF2A2A00);
                drawBorder(g, plusX, ry, PLUS_W, PLUS_H, 0xFFAA0000);
            }

            // Label
            g.drawString(font, "§e" + label, x, ry + 5, 0xFFFFFF);

            // Value
            g.drawString(font, "§f" + cur, x + 72, ry + 5, 0xFFFFFF);

            // Soft-cap hint


            // [+] glyph
            String plusColor = canAllocate ? (hovered ? "§a" : "§2") : "§8";
            g.drawString(font, plusColor + "[ + ]", plusX + 2, ry + 5, 0xFFFFFF);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────

    private String fmt(double v)  { return String.format("%.1f", v); }
    private String fmt2(double v) { return String.format("%.2f", v); }

    private void drawBar(GuiGraphics g, int x, int y, int w, int h, double frac, int color) {
        frac = Math.max(0.0, Math.min(1.0, frac));
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + 1 + (int)((w - 2) * frac), y + h - 1, color);
        drawBorder(g, x, y, w, h, 0xFF555555);
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x,         y,         x + w, y + 1, color);
        g.fill(x,         y + h - 1, x + w, y + h, color);
        g.fill(x,         y,         x + 1, y + h, color);
        g.fill(x + w - 1, y,         x + w, y + h, color);
    }
}