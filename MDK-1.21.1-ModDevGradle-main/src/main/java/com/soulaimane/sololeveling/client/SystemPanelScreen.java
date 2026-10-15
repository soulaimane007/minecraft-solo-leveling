package com.soulaimane.sololeveling.client;

import com.soulaimane.sololeveling.data.EquipmentLoadState;
import com.soulaimane.sololeveling.data.ModDataMaps;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.data.Rank;
import com.soulaimane.sololeveling.event.WeaponDamageEvents;
import com.soulaimane.sololeveling.network.AllocateStatsPayload;
import com.soulaimane.sololeveling.registry.ModAttributes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The System panel (K).
 *
 * Everything is drawn in a fixed 400x285 "panel space" and the whole panel is scaled
 * down to fit the window, so it never overflows at high GUI scales. Mouse positions are
 * converted back into panel space before hit-testing.
 */
public class SystemPanelScreen extends Screen {

    // ── Panel dimensions (panel space) ──
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

    private static final String[] STAT_DESC = {
            "Weapon damage, knockback and carry load.",
            "Move & attack speed, stamina regen, step height (30+).",
            "+0.4 max HP per point (5 points = 1 heart).",
            "Max mana, mana regeneration and magic power.",
            "Block and entity reach.",
            "Required to wield weapons and wear armor.",
            "Damage reduction, poise, max stamina and load."
    };

    // Layout constants
    private static final int ROW_START_Y   = 74;
    private static final int ROW_H         = 19;
    private static final int PLUS_X        = WIDTH - 44;
    private static final int PLUS_W        = 24;
    private static final int PLUS_H        = 17;
    private static final int LINE_H        = 10;
    private static final int LEFT_COL_X    = 12;
    private static final int RIGHT_COL_X   = 222;
    private static final int LEFT_BAR_W    = 185;

    private float scale = 1f;
    private int originX, originY;

    public SystemPanelScreen() {
        super(Component.literal("System"));
    }

    @Override
    protected void init() {
        super.init();
        float sx = (this.width  - 8) / (float) WIDTH;
        float sy = (this.height - 8) / (float) HEIGHT;
        this.scale = Math.max(0.3f, Math.min(1f, Math.min(sx, sy)));
        this.originX = Math.round((this.width  - WIDTH  * scale) / 2f);
        this.originY = Math.round((this.height - HEIGHT * scale) / 2f);
    }

    private double panelX(double screenX) { return (screenX - originX) / scale; }
    private double panelY(double screenY) { return (screenY - originY) / scale; }

    // ─────────────────────────────────────────────────────────────────
    // INPUT
    // ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Pressing K again closes the panel
        if (SLKeybinds.OPEN_PANEL.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        double px = panelX(mouseX);
        double py = panelY(mouseY);

        for (int i = 0; i < STAT_KEYS.length; i++) {
            int ry = ROW_START_Y + i * ROW_H;
            if (px >= PLUS_X && px <= PLUS_X + PLUS_W && py >= ry && py <= ry + PLUS_H) {
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
            default -> { return; }
        }

        PacketDistributor.sendToServer(new AllocateStatsPayload(
                dStr, dAgi, dVit, dInt, dSen, dDex, dDur));

        // Optimistic local update — the server's sync overwrites it a moment later
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
        // Fully opaque background — no world bleed-through (screen space)
        g.fill(0, 0, this.width, this.height, 0xFF000000);

        int mx = (int) Math.floor(panelX(mouseX));
        int my = (int) Math.floor(panelY(mouseY));

        g.pose().pushPose();
        g.pose().translate(originX, originY, 0);
        g.pose().scale(scale, scale, 1f);

        renderPanel(g, mx, my);

        g.pose().popPose();
    }

    private void renderPanel(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, WIDTH, HEIGHT, 0xFF101010);

        // Border
        drawBorder(g, 0, 0, WIDTH, HEIGHT, 0xFFFFAA00);
        drawBorder(g, 2, 2, WIDTH - 4, HEIGHT - 4, 0xFF333333);

        // Divider
        g.fill(210, 28, 211, HEIGHT - 14, 0xFF444444);

        // Title bar
        g.fill(2, 2, WIDTH - 2, 24, 0xFF1A1A1A);
        g.fill(2, 24, WIDTH - 2, 25, 0xFFFFAA00);
        g.drawCenteredString(font, "§6═══  S Y S T E M  ═══", WIDTH / 2, 8, 0xFFFFFF);

        renderCharacterPanel(g);
        renderStatsPanel(g, mx, my);

        // Footer hint
        g.drawCenteredString(font, "§8Press K or ESC to close", WIDTH / 2, HEIGHT - 13, 0xFFFFFF);
    }

    // ─────────────────────────────────────────────────────────────────
    // LEFT PANEL — character details
    // ─────────────────────────────────────────────────────────────────

    private void renderCharacterPanel(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        Rank rank = Rank.byOrdinal(ClientSystemDataCache.rankOrdinal);
        int ordinal = Math.max(0, Math.min(EquipmentLoadState.values().length - 1,
                ClientSystemDataCache.loadStateOrdinal));
        EquipmentLoadState loadState = EquipmentLoadState.values()[ordinal];

        int x = LEFT_COL_X;
        int y = 32;

        // ── Header ──
        g.drawString(font, "§6▸ Character", x, y, 0xFFFFFF);
        y += 12;

        g.drawString(font, "§eLv: §f" + ClientSystemDataCache.level
                + "   §eRank: §f" + rank.getSerializedName(), x, y, 0xFFFFFF);
        y += LINE_H;

        int needed = PlayerSystemData.xpForNextLevel(ClientSystemDataCache.level);
        boolean maxed = ClientSystemDataCache.level >= PlayerSystemData.MAX_LEVEL;
        g.drawString(font, "§eXP: §f" + (maxed ? "MAX" : ClientSystemDataCache.xp + " / " + needed),
                x, y, 0xFFFFFF);
        y += LINE_H;
        drawBar(g, x, y, LEFT_BAR_W, 4,
                maxed ? 1.0 : ClientSystemDataCache.xp / (double) Math.max(1, needed), 0xFF55FF55);
        y += 8;

        // ── HP ──
        double curHp    = player.getHealth();
        double curMaxHp = player.getMaxHealth();
        g.drawString(font, "§c❤ HP: §f" + fmt(curHp) + " §7/ §f" + fmt(curMaxHp), x, y, 0xFFFFFF);
        y += 9;
        drawBar(g, x, y, LEFT_BAR_W, 4, curHp / Math.max(0.01, curMaxHp), 0xFFAA0000);
        y += 8;

        // ── Stamina ──
        int staColor = ClientSystemDataCache.exhausted ? 0xFFFF0000 : 0xFF00AA00;
        g.drawString(font, "§a⚡ Stamina: §f" + fmt(ClientSystemDataCache.stamina)
                + " §7/ §f" + fmt(ClientSystemDataCache.maxStamina)
                + (ClientSystemDataCache.exhausted ? " §c(exhausted)" : ""), x, y, 0xFFFFFF);
        y += 9;
        drawBar(g, x, y, LEFT_BAR_W, 4,
                ClientSystemDataCache.stamina / Math.max(0.01, ClientSystemDataCache.maxStamina),
                staColor);
        y += 8;

        double staRegen = attr(player, ModAttributes.STAMINA_REGEN,
                StatFormulas.staminaRegen(ClientSystemDataCache.agility, rank, loadState))
                * loadState.staminaRegenMultiplier;
        g.drawString(font, "§7  Regen: §f" + fmt2(staRegen) + "§7/s", x, y, 0xFFFFFF);
        y += LINE_H;

        // ── Mana ──
        double maxMana = Math.max(0.01, ClientSystemDataCache.maxMana);
        g.drawString(font, "§b✦ Mana: §f" + fmt(ClientSystemDataCache.mana)
                + " §7/ §f" + fmt(maxMana), x, y, 0xFFFFFF);
        y += 9;
        drawBar(g, x, y, LEFT_BAR_W, 4, ClientSystemDataCache.mana / maxMana, 0xFF4A6CFF);
        y += 8;

        double manaRegen = attr(player, ModAttributes.MANA_REGEN,
                StatFormulas.manaRegen(ClientSystemDataCache.intelligence, rank));
        g.drawString(font, "§7  Regen: §f" + fmt2(manaRegen) + "§7/s", x, y, 0xFFFFFF);
        y += LINE_H + 2;

        // ── Combat ──
        g.drawString(font, "§6▸ Combat", x, y, 0xFFFFFF);
        y += LINE_H;

        ItemStack held = player.getMainHandItem();
        double weaponBase = WeaponDamageEvents.getWeaponBase(held);
        double atk = weaponDamage(rank, weaponBase);

        double atkSpd = player.getAttributeValue(Attributes.ATTACK_SPEED);
        double move   = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double knock  = attr(player, Attributes.ATTACK_KNOCKBACK,
                StatFormulas.knockback(ClientSystemDataCache.strength, rank));
        double range  = StatFormulas.interactionRange(ClientSystemDataCache.sense, rank);
        double dr     = attr(player, ModAttributes.PHYSICAL_DAMAGE_REDUCTION,
                StatFormulas.damageReduction(ClientSystemDataCache.durability, rank));
        double magic  = StatFormulas.magicDamage(ClientSystemDataCache.intelligence, rank);
        double poise  = clientPoise(player);

        g.drawString(font, "§eAttack Damage: §f" + fmt2(atk) + " §7(weapon " + fmt2(weaponBase) + ")",
                x, y, 0xFFFFFF); y += LINE_H;
        g.drawString(font, "§eAttack Speed: §f" + fmt2(atkSpd), x, y, 0xFFFFFF); y += LINE_H;
        g.drawString(font, "§eMove Speed: §f" + String.format("%.0f%%", move / 0.1 * 100.0),
                x, y, 0xFFFFFF); y += LINE_H;
        g.drawString(font, "§eKnockback: §f" + fmt2(knock), x, y, 0xFFFFFF); y += LINE_H;
        g.drawString(font, "§eReach Bonus: §f+" + fmt2(range), x, y, 0xFFFFFF); y += LINE_H;
        g.drawString(font, "§eDamage Reduction: §f" + String.format("%.1f%%", dr * 100.0),
                x, y, 0xFFFFFF); y += LINE_H;
        g.drawString(font, "§ePoise: §f" + fmt2(poise), x, y, 0xFFFFFF); y += LINE_H;
        g.drawString(font, "§d✦ Magic Power: §f" + fmt2(magic), x, y, 0xFFFFFF); y += LINE_H + 2;

        // ── Equip Load ──
        g.drawString(font, "§6▸ Equip Load", x, y, 0xFFFFFF);
        y += LINE_H;
        g.drawString(font, "§7  Load: §f" + fmt2(ClientSystemDataCache.equipLoad)
                        + " §7/ §f" + fmt2(ClientSystemDataCache.maxEquipLoad)
                        + "  §7[" + loadColorCode(loadState) + loadState.name() + "§7]",
                x, y, 0xFFFFFF);
    }

    // ─────────────────────────────────────────────────────────────────
    // RIGHT PANEL — stat allocation
    // ─────────────────────────────────────────────────────────────────

    private void renderStatsPanel(GuiGraphics g, int mx, int my) {
        int x = RIGHT_COL_X;
        int y = 32;

        g.drawString(font, "§6▸ Stats", x, y, 0xFFFFFF);
        y += 12;

        g.drawString(font, "§eSP: §f" + ClientSystemDataCache.statPoints, x, y, 0xFFFFFF);

        boolean canAllocate = ClientSystemDataCache.statPoints > 0;
        g.drawString(font, canAllocate ? "§8Click [+] to spend." : "§8No points.",
                x + 60, y, 0xFFFFFF);

        int hoveredRow = -1;

        for (int i = 0; i < STAT_KEYS.length; i++) {
            int cur = ClientSystemDataCache.getStat(STAT_KEYS[i]);
            int ry = ROW_START_Y + i * ROW_H;

            // Row stripe
            g.fill(214, ry - 1, WIDTH - 4, ry + 16, 0xFF151515);

            boolean rowHover = mx >= 214 && mx <= WIDTH - 4 && my >= ry - 1 && my <= ry + 16;
            if (rowHover) hoveredRow = i;

            // Hover highlight for the [+] area
            boolean plusHover = canAllocate
                    && mx >= PLUS_X && mx <= PLUS_X + PLUS_W
                    && my >= ry     && my <= ry + PLUS_H;
            if (plusHover) {
                g.fill(PLUS_X, ry, PLUS_X + PLUS_W, ry + PLUS_H, 0xFF2A2A00);
                drawBorder(g, PLUS_X, ry, PLUS_W, PLUS_H, 0xFFAA0000);
            }

            g.drawString(font, "§e" + STAT_LABELS[i], x, ry + 5, 0xFFFFFF);
            g.drawString(font, "§f" + cur, x + 72, ry + 5, 0xFFFFFF);

            String plusColor = canAllocate ? (plusHover ? "§a" : "§2") : "§8";
            g.drawString(font, plusColor + "[ + ]", PLUS_X + 2, ry + 5, 0xFFFFFF);
        }

        // ── Description of the hovered stat ──
        int boxY = ROW_START_Y + STAT_KEYS.length * ROW_H + 6;
        g.fill(214, boxY, WIDTH - 4, boxY + 44, 0xFF151515);
        drawBorder(g, 214, boxY, WIDTH - 218, 44, 0xFF333333);
        if (hoveredRow >= 0) {
            g.drawString(font, "§6" + STAT_LABELS[hoveredRow], 220, boxY + 5, 0xFFFFFF);
            g.drawWordWrap(font, Component.literal("§7" + STAT_DESC[hoveredRow]),
                    220, boxY + 17, WIDTH - 232, 0xFFFFFF);
        } else {
            g.drawWordWrap(font, Component.literal("§8Hover a stat to see what it does."),
                    220, boxY + 5, WIDTH - 232, 0xFFFFFF);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────

    private static double attr(Player p, Holder<Attribute> attribute, double fallback) {
        AttributeInstance inst = p.getAttribute(attribute);
        return inst != null ? inst.getValue() : fallback;
    }

    /** Weapon damage as the server computes it; falls back to the built-in formula if the
     *  server config is not available on this client. */
    private static double weaponDamage(Rank rank, double weaponBase) {
        try {
            return WeaponDamageEvents.computeDamage(ClientSystemDataCache.strength, rank, weaponBase);
        } catch (RuntimeException ex) {
            return StatFormulas.weaponDamage(ClientSystemDataCache.strength, rank, weaponBase);
        }
    }

    /** Poise = armour poise (data map, synced to clients) + Durability * 0.5. */
    private static double clientPoise(Player p) {
        double total = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = p.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            Double poise = stack.getItemHolder().getData(ModDataMaps.POISE);
            if (poise != null) total += poise;
        }
        return total + ClientSystemDataCache.durability * 0.5;
    }

    private static String loadColorCode(EquipmentLoadState state) {
        return switch (state) {
            case LIGHT      -> "§a";
            case MEDIUM     -> "§e";
            case HEAVY      -> "§6";
            case OVERWEIGHT -> "§c";
        };
    }

    private String fmt(double v)  { return String.format("%.1f", v); }
    private String fmt2(double v) { return String.format("%.2f", v); }

    private void drawBar(GuiGraphics g, int x, int y, int w, int h, double frac, int color) {
        frac = Math.max(0.0, Math.min(1.0, frac));
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + 1 + (int) ((w - 2) * frac), y + h - 1, color);
        drawBorder(g, x, y, w, h, 0xFF555555);
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x,         y,         x + w, y + 1, color);
        g.fill(x,         y + h - 1, x + w, y + h, color);
        g.fill(x,         y,         x + 1, y + h, color);
        g.fill(x + w - 1, y,         x + w, y + h, color);
    }
}
