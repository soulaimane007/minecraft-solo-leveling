package com.soulaimane.sololeveling.client;

import com.soulaimane.sololeveling.ModItems;
import com.soulaimane.sololeveling.data.PlayerActionBar;
import com.soulaimane.sololeveling.magic.Magic;
import com.soulaimane.sololeveling.magic.MagicRegistry;
import com.soulaimane.sololeveling.network.SetSlotPayload;
import com.soulaimane.sololeveling.network.UnlockSkillPayload;
import com.soulaimane.sololeveling.power.PowerIds;
import com.soulaimane.sololeveling.power.Requirements;
import com.soulaimane.sololeveling.skill.Skill;
import com.soulaimane.sololeveling.skill.SkillBranch;
import com.soulaimane.sololeveling.skill.SkillRegistry;
import com.soulaimane.sololeveling.skill.SkillRules;
import com.soulaimane.sololeveling.skill.SkillTreeLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The skill tree (J).
 *
 * Left: one tab per class (Tank, Support, Warrior, Marksman, Mage, Assassin) and a Magic tab.
 * Middle: the tree of the selected class, or the list of learned magics. Scroll with the mouse wheel, drag to move.
 * Bottom: the six action bar slots.
 *
 *   Left-click a skill ....... learn it / upgrade it (the server checks the rules again)
 *   Right-click a skill ...... put the learned active skill on the action bar (the selected slot, or the first free one)
 *   Right-click a magic ...... same
 *   Click a slot ............. select it, so the next right-click goes there. Right-click a slot to empty it.
 *
 * Nothing in here knows a particular skill: it draws whatever is in the SkillRegistry / MagicRegistry.
 * The panel is not scaled (it adapts to the window size), because the scissor used for scrolling ignores scaling.
 */
public class SkillTreeScreen extends Screen {

    // ── Sizes ──
    private static final int NODE = 26;                 // skill frame
    private static final int COL_W = 44;                // distance between two columns
    private static final int TIER_H = 48;               // distance between two rows
    private static final int TREE_PAD = 8;
    private static final int SLOT = 24;                 // action bar slot in the screen
    private static final int SLOT_GAP = 6;
    private static final int ROW_H = 30;                // one magic in the Magic tab
    private static final int TIP_WIDTH = 200;
    private static final int MAGIC_COLOR = 0xFFB27BFF;

    private static final SkillBranch[] BRANCHES = SkillBranch.values();
    private static final int MAGIC_TAB = BRANCHES.length;

    /** Reopening the screen shows the tab you were on. */
    private static int lastTab = 0;

    // ── Layout of each class (computed once) ──
    private final List<Map<Skill, SkillTreeLayout.Cell>> layouts = new ArrayList<>();

    // ── Geometry (computed in init) ──
    private int px, py, pw, ph;                          // the panel
    private int tabX, tabY, tabW, tabH;                  // the tab list
    private int contentX, contentW, headerY;
    private int viewX0, viewY0, viewX1, viewY1;          // the scrolling area
    private int slotX, slotY;                            // the slot bar

    // ── State ──
    private int tab;
    private double scrollX, scrollY;
    private boolean dragging;
    private int selectedSlot = -1;
    private String flash = "";
    private long flashUntil;
    private final List<FormattedCharSequence> tip = new ArrayList<>();

    public SkillTreeScreen() {
        super(Component.literal("Skill Tree"));
        for (SkillBranch branch : BRANCHES) {
            layouts.add(SkillTreeLayout.compute(SkillRegistry.branch(branch),
                    Skill::id, Skill::parents, Skill::tier, Skill::column));
        }
        this.tab = Mth.clamp(lastTab, 0, MAGIC_TAB);
    }

    @Override
    protected void init() {
        super.init();

        pw = Math.min(this.width - 12, 640);
        ph = Math.min(this.height - 12, 380);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        tabX = px + 6;
        tabY = py + 30;
        tabW = Math.max(60, Math.min(92, pw / 5));
        tabH = Mth.clamp((ph - 38) / (MAGIC_TAB + 1) - 2, 14, 22);

        contentX = tabX + tabW + 8;
        contentW = px + pw - 6 - contentX;
        headerY = py + 28;

        viewX0 = contentX;
        viewX1 = contentX + contentW;
        viewY0 = headerY + 32;
        viewY1 = Math.max(viewY0 + 40, py + ph - 6 - (SLOT + 14) - 6);

        slotX = contentX;
        slotY = viewY1 + 6;

        clampScroll();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ─────────────────────────────────────────────────────────────────
    // RENDERING
    // ─────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        tip.clear();

        g.fill(0, 0, this.width, this.height, 0xE6000000);
        g.fill(px, py, px + pw, py + ph, 0xFF101010);
        border(g, px, py, pw, ph, 0xFFFFAA00);
        border(g, px + 2, py + 2, pw - 4, ph - 4, 0xFF333333);

        // Title bar
        g.fill(px + 2, py + 2, px + pw - 2, py + 22, 0xFF1A1A1A);
        g.fill(px + 2, py + 22, px + pw - 2, py + 23, 0xFFFFAA00);
        g.drawString(font, "§6S K I L L   T R E E", px + 10, py + 8, 0xFFFFFFFF);
        String points = "Skill Points: " + ClientPowerCache.skillPoints;
        g.drawString(font, points, px + pw - 10 - font.width(points), py + 8,
                ClientPowerCache.skillPoints > 0 ? 0xFF7CFF7C : 0xFFAAAAAA);

        renderTabs(g, mouseX, mouseY);
        if (tab == MAGIC_TAB) {
            renderMagicHeader(g);
            renderMagicList(g, mouseX, mouseY);
        } else {
            renderBranchHeader(g, BRANCHES[tab]);
            renderTree(g, mouseX, mouseY);
        }
        renderSlots(g, mouseX, mouseY);
        renderHint(g);
        renderFlash(g);

        // Tooltips last, so nothing is drawn over them
        if (!tip.isEmpty()) {
            g.renderTooltip(font, tip, mouseX, mouseY);
        }
    }

    // ── Tabs ──

    private void renderTabs(GuiGraphics g, int mx, int my) {
        for (int i = 0; i <= MAGIC_TAB; i++) {
            int y = tabY + i * (tabH + 2);
            boolean selected = i == tab;
            boolean hover = inside(mx, my, tabX, y, tabW, tabH);
            int color = i == MAGIC_TAB ? MAGIC_COLOR : BRANCHES[i].color();

            g.fill(tabX, y, tabX + tabW, y + tabH, selected ? 0xFF2A2A2A : (hover ? 0xFF202020 : 0xFF151515));
            g.fill(tabX, y, tabX + 3, y + tabH, selected ? color : scale(color, 0.45f));

            int textX = tabX + 8;
            if (tabH >= 18) {
                g.renderItem(tabIcon(i), tabX + 6, y + (tabH - 16) / 2);
                textX = tabX + 26;
            }
            String name = i == MAGIC_TAB ? "Magic" : BRANCHES[i].displayName().getString();
            g.drawString(font, name, textX, y + (tabH - 8) / 2, selected ? 0xFFFFFFFF : 0xFFAAAAAA);

            if (hasSomethingToLearn(i)) {
                g.fill(tabX + tabW - 8, y + tabH / 2 - 2, tabX + tabW - 4, y + tabH / 2 + 2, 0xFF55FF55);
            }
        }
    }

    private static ItemStack tabIcon(int index) {
        if (index == MAGIC_TAB) {
            return ModItems.MAGIC_PAPER.get().getDefaultInstance();
        }
        return new ItemStack(BRANCHES[index].icon());
    }

    private static boolean hasSomethingToLearn(int index) {
        if (index >= BRANCHES.length) return false;
        for (Skill skill : SkillRegistry.branch(BRANCHES[index])) {
            if (statusOf(skill) == SkillRules.Status.AVAILABLE) return true;
        }
        return false;
    }

    private static SkillRules.Status statusOf(Skill skill) {
        return SkillRules.status(skill, ClientPowerView.INSTANCE, ClientPowerCache.skillPoints);
    }

    // ── Header ──

    private void renderBranchHeader(GuiGraphics g, SkillBranch branch) {
        int color = branch.color() & 0xFFFFFF;
        g.drawString(font, branch.displayName().copy().withStyle(s -> s.withColor(color).withBold(true)),
                contentX, headerY, 0xFFFFFFFF);

        List<Skill> skills = SkillRegistry.branch(branch);
        int learned = 0;
        for (Skill skill : skills) {
            if (ClientPowerCache.rank(skill.id()) > 0) learned++;
        }
        String count = learned + " / " + skills.size() + " learned";
        g.drawString(font, count, viewX1 - font.width(count), headerY, 0xFFAAAAAA);

        drawWrapped(g, branch.tagline().copy().withStyle(ChatFormatting.GRAY), contentX, headerY + 12, contentW, 2);
    }

    private void renderMagicHeader(GuiGraphics g) {
        g.drawString(font, Component.literal("Magic").withStyle(s -> s.withColor(MAGIC_COLOR & 0xFFFFFF).withBold(true)),
                contentX, headerY, 0xFFFFFFFF);
        String count = learnedMagics().size() + " learned";
        g.drawString(font, count, viewX1 - font.width(count), headerY, 0xFFAAAAAA);
        drawWrapped(g, Component.literal("A second source of power, apart from skills. "
                + "Burn a Magic Paper (right-click it) when you meet its requirements.").withStyle(ChatFormatting.GRAY),
                contentX, headerY + 12, contentW, 2);
    }

    private void drawWrapped(GuiGraphics g, Component text, int x, int y, int width, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(40, width));
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            g.drawString(font, lines.get(i), x, y + i * 10, 0xFFFFFFFF);
        }
    }

    // ── The tree of a class ──

    private void renderTree(GuiGraphics g, int mx, int my) {
        g.fill(viewX0, viewY0, viewX1, viewY1, 0xFF0A0A0A);
        border(g, viewX0 - 1, viewY0 - 1, viewX1 - viewX0 + 2, viewY1 - viewY0 + 2, 0xFF2E2E2E);

        Map<Skill, SkillTreeLayout.Cell> cells = layouts.get(tab);
        if (cells.isEmpty()) {
            g.drawCenteredString(font, "§8No skills in this class yet.",
                    (viewX0 + viewX1) / 2, (viewY0 + viewY1) / 2 - 4, 0xFFFFFFFF);
            return;
        }

        g.enableScissor(viewX0, viewY0, viewX1, viewY1);

        // 1. the lines between a skill and its parents
        for (Map.Entry<Skill, SkillTreeLayout.Cell> entry : cells.entrySet()) {
            Skill skill = entry.getKey();
            int color = ClientPowerCache.rank(skill.id()) > 0 ? skill.branch().color() : 0xFF3A3A3A;
            for (String parentId : skill.parents()) {
                Skill parent = SkillRegistry.byId(parentId);
                SkillTreeLayout.Cell parentCell = parent == null ? null : cells.get(parent);
                if (parentCell == null) continue;                       // a parent in another class: no line
                drawLink(g, nodeX(parentCell), nodeY(parentCell), nodeX(entry.getValue()), nodeY(entry.getValue()), color);
            }
        }

        // 2. the skills
        Skill hovered = skillAt(mx, my);
        for (Map.Entry<Skill, SkillTreeLayout.Cell> entry : cells.entrySet()) {
            drawNode(g, entry.getKey(), nodeX(entry.getValue()), nodeY(entry.getValue()), entry.getKey() == hovered);
        }

        g.disableScissor();

        if (hovered != null) {
            buildSkillTip(hovered);
        }
    }

    private void drawNode(GuiGraphics g, Skill skill, int x, int y, boolean hovered) {
        int rank = ClientPowerCache.rank(skill.id());
        SkillRules.Status status = statusOf(skill);
        int branchColor = skill.branch().color();

        // Frame colour: gold = maxed, pulsing green = you can learn it now, amber = only the points are missing,
        // branch colour = learned, grey = locked
        int frame;
        switch (status) {
            case MAXED -> frame = 0xFFFFD34D;
            case AVAILABLE -> frame = scale(0xFF55FF55, 0.7f + 0.3f * (float) Math.sin(System.currentTimeMillis() / 250.0));
            case NO_POINTS -> frame = 0xFFC8A040;
            default -> frame = rank > 0 ? branchColor : 0xFF444444;
        }

        int back = rank > 0 ? scale(branchColor, 0.28f) : 0xFF191919;
        if (hovered) back = lighten(back);
        g.fill(x, y, x + NODE, y + NODE, back);
        border(g, x, y, NODE, NODE, frame);

        g.renderItem(skill.iconStack(), x + (NODE - 16) / 2, y + 3);

        // Locked skills are veiled (lifted above the item, which is drawn "in front")
        boolean locked = rank == 0 && (status == SkillRules.Status.NEEDS_PARENT || status == SkillRules.Status.NEEDS_REQUIREMENTS);
        if (locked) {
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 200f);
            g.fill(x + 1, y + 1, x + NODE - 1, y + NODE - 1, 0xB4101010);
            g.pose().popPose();
        }

        // Rank: one pip per rank (a thin bar when there are many)
        int max = skill.maxRank();
        int barY = y + NODE - 6;
        if (max <= 6) {
            int total = max * 3 + (max - 1);
            int startX = x + (NODE - total) / 2;
            for (int i = 0; i < max; i++) {
                int pipX = startX + i * 4;
                g.fill(pipX, barY, pipX + 3, barY + 3, i < rank ? (status == SkillRules.Status.MAXED ? 0xFFFFD34D : branchColor) : 0xFF3A3A3A);
            }
        } else {
            int barW = NODE - 8;
            g.fill(x + 4, barY, x + 4 + barW, barY + 3, 0xFF3A3A3A);
            g.fill(x + 4, barY, x + 4 + Math.round(barW * rank / (float) max), barY + 3,
                    status == SkillRules.Status.MAXED ? 0xFFFFD34D : branchColor);
        }
    }

    private void drawLink(GuiGraphics g, int parentX, int parentY, int childX, int childY, int color) {
        int sx = parentX + NODE / 2;
        int sy = parentY + NODE;
        int ex = childX + NODE / 2;
        int ey = childY;
        int midY = sy + (ey - sy) / 2;

        g.fill(sx - 1, Math.min(sy, midY), sx + 1, Math.max(sy, midY) + 1, color);
        g.fill(Math.min(sx, ex) - 1, midY - 1, Math.max(sx, ex) + 1, midY + 1, color);
        g.fill(ex - 1, Math.min(midY, ey), ex + 1, Math.max(midY, ey), color);
    }

    // ── The Magic tab ──

    private void renderMagicList(GuiGraphics g, int mx, int my) {
        g.fill(viewX0, viewY0, viewX1, viewY1, 0xFF0A0A0A);
        border(g, viewX0 - 1, viewY0 - 1, viewX1 - viewX0 + 2, viewY1 - viewY0 + 2, 0xFF2E2E2E);

        List<Magic> magics = learnedMagics();
        if (magics.isEmpty()) {
            g.drawCenteredString(font, "§8You have not learned any magic yet.",
                    (viewX0 + viewX1) / 2, (viewY0 + viewY1) / 2 - 4, 0xFFFFFFFF);
            return;
        }

        g.enableScissor(viewX0, viewY0, viewX1, viewY1);
        Magic hovered = magicAt(mx, my);
        for (int i = 0; i < magics.size(); i++) {
            Magic magic = magics.get(i);
            int x = viewX0 + 4;
            int y = magicRowY(i);
            int w = viewX1 - viewX0 - 8;
            int h = ROW_H - 4;

            g.fill(x, y, x + w, y + h, magic == hovered ? 0xFF222222 : 0xFF151515);
            border(g, x, y, w, h, scale(magic.color(), 0.6f));
            g.renderItem(magic.iconStack(), x + 6, y + (h - 16) / 2);

            g.drawString(font, magic.displayName().copy().withStyle(s -> s.withColor(magic.color() & 0xFFFFFF)),
                    x + 28, y + 4, 0xFFFFFFFF);
            g.drawString(font, costLine(magic), x + 28, y + 15, 0xFF9A9A9A);

            int onBar = slotOf(PlayerActionBar.magicEntry(magic.id()));
            if (onBar >= 0) {
                String where = "Slot " + (onBar + 1);
                g.drawString(font, where, x + w - 6 - font.width(where), y + 4, 0xFF7CFF7C);
            }
        }
        g.disableScissor();

        if (hovered != null) {
            buildMagicTip(hovered);
        }
    }

    private static String costLine(Magic magic) {
        List<String> parts = new ArrayList<>();
        if (magic.manaCost() > 0f) parts.add("Mana " + fmt(magic.manaCost()));
        if (magic.staminaCost() > 0f) parts.add("Stamina " + fmt(magic.staminaCost()));
        if (magic.cooldownTicks() > 0) parts.add("Cooldown " + fmt(magic.cooldownTicks() / 20.0) + "s");
        return parts.isEmpty() ? "Free" : String.join("  ·  ", parts);
    }

    private int magicRowY(int index) {
        return viewY0 + 4 + index * ROW_H - (int) scrollY;
    }

    private static List<Magic> learnedMagics() {
        List<Magic> out = new ArrayList<>();
        for (String id : ClientPowerCache.magics) {
            Magic magic = MagicRegistry.byId(id);
            if (magic != null) out.add(magic);
        }
        return out;
    }

    private Magic magicAt(double mx, double my) {
        if (tab != MAGIC_TAB || !insideView(mx, my)) return null;
        List<Magic> magics = learnedMagics();
        for (int i = 0; i < magics.size(); i++) {
            int y = magicRowY(i);
            if (my >= y && my < y + ROW_H - 4 && mx >= viewX0 + 4 && mx < viewX1 - 4) {
                return magics.get(i);
            }
        }
        return null;
    }

    // ── Action bar slots ──

    private void renderSlots(GuiGraphics g, int mx, int my) {
        for (int i = 0; i < PlayerActionBar.SIZE; i++) {
            int x = slotX + i * (SLOT + SLOT_GAP);
            boolean selected = i == selectedSlot;
            boolean hover = inside(mx, my, x, slotY, SLOT, SLOT);
            PowerEntry entry = PowerEntry.of(ClientPowerCache.slots[i]);

            g.fill(x, slotY, x + SLOT, slotY + SLOT, hover ? 0xFF222222 : 0xFF151515);
            border(g, x, slotY, SLOT, SLOT, selected ? 0xFFFFD34D : (entry != null ? entry.color() : 0xFF3A3A3A));
            if (entry != null) {
                g.renderItem(entry.icon(), x + (SLOT - 16) / 2, slotY + (SLOT - 16) / 2);
            }

            Component key = SLKeybinds.SLOT_KEYS[i].getTranslatedKeyMessage();
            g.drawString(font, key, x + (SLOT - font.width(key)) / 2, slotY + SLOT + 3,
                    selected ? 0xFFFFD34D : 0xFF999999);

            if (hover) {
                if (entry != null) {
                    tip.add(entry.name().copy().withStyle(s -> s.withColor(entry.color() & 0xFFFFFF).withBold(true)).getVisualOrderText());
                } else {
                    tip.add(Component.literal("Empty slot " + (i + 1)).withStyle(ChatFormatting.GRAY).getVisualOrderText());
                }
                tip.add(Component.literal("Key: ").withStyle(ChatFormatting.DARK_GRAY).append(key.copy().withStyle(ChatFormatting.GRAY)).getVisualOrderText());
                tip.add(Component.literal(selected ? "Click: unselect" : "Click: select this slot").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
                if (entry != null) {
                    tip.add(Component.literal("Right-click: empty this slot").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
                }
            }
        }
    }

    private void renderHint(GuiGraphics g) {
        int x = slotX + PlayerActionBar.SIZE * (SLOT + SLOT_GAP) + 4;
        int width = viewX1 - x;
        if (width < 90) return;
        Component hint = Component.literal(
                "Left-click: learn / upgrade.  Right-click a learned skill or magic: put it on the bar "
                        + "(select a slot first to choose which one).  Wheel: scroll.  J: close.")
                .withStyle(ChatFormatting.DARK_GRAY);
        drawWrapped(g, hint, x, slotY, width, 3);
    }

    private void renderFlash(GuiGraphics g) {
        if (flash.isEmpty() || System.currentTimeMillis() > flashUntil) return;
        int w = font.width(flash) + 12;
        int x = (viewX0 + viewX1 - w) / 2;
        int y = viewY1 - 16;
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);
        g.fill(x, y, x + w, y + 13, 0xE0000000);
        border(g, x, y, w, 13, 0xFF555555);
        g.drawString(font, flash, x + 6, y + 3, 0xFFFFFFFF);
        g.pose().popPose();
    }

    // ── Tooltips ──

    private void buildSkillTip(Skill skill) {
        int rank = ClientPowerCache.rank(skill.id());
        boolean maxed = rank >= skill.maxRank();
        int shown = Math.max(1, Math.min(rank + 1, skill.maxRank()));
        int color = skill.branch().color() & 0xFFFFFF;

        tip.add(skill.displayName().copy().withStyle(s -> s.withColor(color).withBold(true)).getVisualOrderText());
        tip.add(Component.literal((skill.isActive() ? "Active skill" : "Passive skill") + "  ·  Rank " + rank + " / " + skill.maxRank())
                .withStyle(skill.isActive() ? ChatFormatting.GOLD : ChatFormatting.AQUA).getVisualOrderText());
        tip.addAll(font.split(skill.description().copy().withStyle(ChatFormatting.GRAY), TIP_WIDTH));

        // What this rank gives
        tip.add(FormattedCharSequence.EMPTY);
        tip.add(Component.literal(maxed ? "Rank " + rank + " (max):" : "Rank " + shown + ":")
                .withStyle(ChatFormatting.WHITE).getVisualOrderText());
        for (Skill.AttributeBonus bonus : skill.bonuses()) {
            tip.add(Component.literal("  " + bonusLine(bonus, shown)).withStyle(ChatFormatting.GREEN).getVisualOrderText());
        }
        if (skill.isActive()) {
            tip.add(Component.literal("  " + activeLine(skill, shown)).withStyle(ChatFormatting.AQUA).getVisualOrderText());
        }

        if (maxed) {
            tip.add(FormattedCharSequence.EMPTY);
            tip.add(Component.literal("Maxed").withStyle(ChatFormatting.GOLD).getVisualOrderText());
        } else {
            int cost = skill.pointCost(rank + 1);
            boolean enough = ClientPowerCache.skillPoints >= cost;
            tip.add(FormattedCharSequence.EMPTY);
            tip.add(Component.literal("Cost: " + cost + (cost == 1 ? " skill point" : " skill points"))
                    .withStyle(enough ? ChatFormatting.GREEN : ChatFormatting.RED).getVisualOrderText());

            List<Component> needs = new ArrayList<>();
            for (String parentId : skill.parents()) {
                boolean met = ClientPowerCache.rank(parentId) >= 1;
                Skill parent = SkillRegistry.byId(parentId);
                String name = parent != null ? parent.displayName().getString() : PowerIds.prettyName(parentId);
                needs.add(Component.literal((met ? " ✔ " : " ✘ ") + "Skill: " + name)
                        .withStyle(met ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            for (Requirements.Line line : skill.requirementsFor(rank + 1).lines(ClientPowerView.INSTANCE)) {
                needs.add(Component.literal((line.met() ? " ✔ " : " ✘ ") + line.text())
                        .withStyle(line.met() ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            if (!needs.isEmpty()) {
                tip.add(Component.literal("Requires:").withStyle(ChatFormatting.GOLD).getVisualOrderText());
                for (Component need : needs) {
                    tip.add(need.getVisualOrderText());
                }
            }
        }

        tip.add(FormattedCharSequence.EMPTY);
        if (!maxed) {
            tip.add(Component.literal("Left-click: " + (rank == 0 ? "learn" : "upgrade"))
                    .withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        }
        if (rank > 0 && skill.isActive()) {
            tip.add(Component.literal("Right-click: put on the action bar").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        }
    }

    private void buildMagicTip(Magic magic) {
        tip.add(magic.displayName().copy().withStyle(s -> s.withColor(magic.color() & 0xFFFFFF).withBold(true)).getVisualOrderText());
        tip.add(Component.literal("Magic").withStyle(ChatFormatting.LIGHT_PURPLE).getVisualOrderText());
        tip.addAll(font.split(magic.description().copy().withStyle(ChatFormatting.GRAY), TIP_WIDTH));
        tip.add(FormattedCharSequence.EMPTY);
        tip.add(Component.literal(costLine(magic)).withStyle(ChatFormatting.AQUA).getVisualOrderText());
        tip.add(FormattedCharSequence.EMPTY);
        tip.add(Component.literal("Right-click: put on the action bar").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
    }

    private static String bonusLine(Skill.AttributeBonus bonus, int rank) {
        Holder<Attribute> holder = bonus.attribute().get();
        String name = Component.translatable(holder.value().getDescriptionId()).getString();
        double total = bonus.perRank() * rank;
        boolean percent = bonus.operation() != AttributeModifier.Operation.ADD_VALUE;
        String amount = percent ? fmt(total * 100.0) + "%" : fmt(total);
        return (total >= 0 ? "+" : "") + amount + " " + name;
    }

    private static String activeLine(Skill skill, int rank) {
        List<String> parts = new ArrayList<>();
        if (skill.staminaCost(rank) > 0f) parts.add("Stamina " + fmt(skill.staminaCost(rank)));
        if (skill.manaCost(rank) > 0f) parts.add("Mana " + fmt(skill.manaCost(rank)));
        if (skill.cooldownTicks(rank) > 0) parts.add("Cooldown " + fmt(skill.cooldownTicks(rank) / 20.0) + "s");
        return parts.isEmpty() ? "Free to use" : String.join("  ·  ", parts);
    }

    // ─────────────────────────────────────────────────────────────────
    // INPUT
    // ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (SLKeybinds.OPEN_SKILLS.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Tabs
        if (button == 0) {
            for (int i = 0; i <= MAGIC_TAB; i++) {
                if (inside(mx, my, tabX, tabY + i * (tabH + 2), tabW, tabH)) {
                    setTab(i);
                    return true;
                }
            }
        }

        // Action bar slots
        for (int i = 0; i < PlayerActionBar.SIZE; i++) {
            if (inside(mx, my, slotX + i * (SLOT + SLOT_GAP), slotY, SLOT, SLOT)) {
                if (button == 0) {
                    selectedSlot = selectedSlot == i ? -1 : i;
                } else if (button == 1 && !ClientPowerCache.slots[i].isEmpty()) {
                    PacketDistributor.sendToServer(new SetSlotPayload(i, ""));
                    flash("Slot " + (i + 1) + " emptied");
                }
                return true;
            }
        }

        // The scrolling area
        if (insideView(mx, my)) {
            if (tab == MAGIC_TAB) {
                Magic magic = magicAt(mx, my);
                if (magic != null) {
                    if (button == 1) {
                        putOnBar(PlayerActionBar.magicEntry(magic.id()), magic.displayName().getString());
                    }
                    return true;
                }
            } else {
                Skill skill = skillAt(mx, my);
                if (skill != null) {
                    if (button == 0) {
                        tryLearn(skill);
                    } else if (button == 1) {
                        tryPutOnBar(skill);
                    }
                    return true;
                }
            }
            if (button == 0) {
                dragging = true;                     // empty space: drag the tree around
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            scrollX -= dragX;
            scrollY -= dragY;
            clampScroll();
            return true;
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollDeltaX, double scrollDeltaY) {
        if (insideView(mx, my)) {
            if (Screen.hasShiftDown() || scrollDeltaY == 0.0) {
                scrollX -= (scrollDeltaY != 0.0 ? scrollDeltaY : scrollDeltaX) * 24.0;
            } else {
                scrollY -= scrollDeltaY * 24.0;
            }
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollDeltaX, scrollDeltaY);
    }

    // ── Actions ──

    private void setTab(int index) {
        tab = index;
        lastTab = index;
        scrollX = 0;
        scrollY = 0;
        dragging = false;
        clampScroll();
    }

    private void tryLearn(Skill skill) {
        int rank = ClientPowerCache.rank(skill.id());
        SkillRules.Status status = statusOf(skill);
        switch (status) {
            case AVAILABLE -> PacketDistributor.sendToServer(new UnlockSkillPayload(skill.id()));
            case MAXED -> flash(skill.displayName().getString() + " is at its maximum rank");
            case NO_POINTS -> flash("Not enough skill points (needs " + skill.pointCost(rank + 1) + ")");
            case NEEDS_PARENT -> flash("Learn the skill before it first");
            case NEEDS_REQUIREMENTS -> {
                List<String> unmet = skill.requirementsFor(rank + 1).unmet(ClientPowerView.INSTANCE);
                flash(unmet.isEmpty() ? "Requirements not met" : "Requires " + unmet.get(0));
            }
        }
    }

    private void tryPutOnBar(Skill skill) {
        if (ClientPowerCache.rank(skill.id()) < 1) {
            flash("Learn it first");
        } else if (!skill.isActive()) {
            flash("A passive skill is always on: it has no key");
        } else {
            putOnBar(PlayerActionBar.skillEntry(skill.id()), skill.displayName().getString());
        }
    }

    private void putOnBar(String entry, String name) {
        int current = slotOf(entry);
        int target = selectedSlot >= 0 ? selectedSlot : firstFreeSlot();

        if (selectedSlot < 0 && current >= 0) {
            flash(name + " is on slot " + (current + 1) + ". Select another slot to move it");
            return;
        }
        if (target < 0) {
            flash("The action bar is full: select a slot first");
            return;
        }
        PacketDistributor.sendToServer(new SetSlotPayload(target, entry));
        selectedSlot = -1;
        flash(name + " -> slot " + (target + 1));
    }

    private static int slotOf(String entry) {
        for (int i = 0; i < ClientPowerCache.slots.length; i++) {
            if (ClientPowerCache.slots[i].equals(entry)) return i;
        }
        return -1;
    }

    private static int firstFreeSlot() {
        for (int i = 0; i < ClientPowerCache.slots.length; i++) {
            if (ClientPowerCache.slots[i].isEmpty()) return i;
        }
        return -1;
    }

    private void flash(String text) {
        this.flash = text;
        this.flashUntil = System.currentTimeMillis() + 3000L;
    }

    // ─────────────────────────────────────────────────────────────────
    // GEOMETRY HELPERS
    // ─────────────────────────────────────────────────────────────────

    private int contentWidth() {
        if (tab == MAGIC_TAB) return 0;
        return (SkillTreeLayout.maxColumn(layouts.get(tab)) + 1) * COL_W + TREE_PAD * 2;
    }

    private int contentHeight() {
        if (tab == MAGIC_TAB) return learnedMagics().size() * ROW_H + TREE_PAD;
        return (SkillTreeLayout.maxTier(layouts.get(tab)) + 1) * TIER_H + TREE_PAD * 2;
    }

    private void clampScroll() {
        double maxX = Math.max(0, contentWidth() - (viewX1 - viewX0));
        double maxY = Math.max(0, contentHeight() - (viewY1 - viewY0));
        scrollX = Mth.clamp(scrollX, 0.0, maxX);
        scrollY = Mth.clamp(scrollY, 0.0, maxY);
    }

    /** Left edge of the tree: centred when it fits the window, otherwise it follows the horizontal scroll. */
    private int treeOriginX() {
        int viewW = viewX1 - viewX0;
        int contentW = contentWidth();
        return contentW <= viewW
                ? viewX0 + (viewW - contentW) / 2 + TREE_PAD
                : viewX0 + TREE_PAD - (int) scrollX;
    }

    private int nodeX(SkillTreeLayout.Cell cell) {
        return treeOriginX() + cell.column() * COL_W + (COL_W - NODE) / 2;
    }

    private int nodeY(SkillTreeLayout.Cell cell) {
        return viewY0 + TREE_PAD - (int) scrollY + cell.tier() * TIER_H;
    }

    private Skill skillAt(double mx, double my) {
        if (tab == MAGIC_TAB || !insideView(mx, my)) return null;
        for (Map.Entry<Skill, SkillTreeLayout.Cell> entry : layouts.get(tab).entrySet()) {
            int x = nodeX(entry.getValue());
            int y = nodeY(entry.getValue());
            if (mx >= x && mx < x + NODE && my >= y && my < y + NODE) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean insideView(double mx, double my) {
        return mx >= viewX0 && mx < viewX1 && my >= viewY0 && my < viewY1;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ─────────────────────────────────────────────────────────────────
    // DRAWING HELPERS
    // ─────────────────────────────────────────────────────────────────

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Multiplies the brightness of an ARGB colour (alpha stays opaque). */
    private static int scale(int argb, float factor) {
        float f = Mth.clamp(factor, 0f, 1f);
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int gr = (int) (((argb >> 8) & 0xFF) * f);
        int b = (int) ((argb & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    private static int lighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 24);
        int gr = Math.min(255, ((argb >> 8) & 0xFF) + 24);
        int b = Math.min(255, (argb & 0xFF) + 24);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.round(v)) < 0.05 ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }
}
