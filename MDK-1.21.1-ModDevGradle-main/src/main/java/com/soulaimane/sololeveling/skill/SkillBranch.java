package com.soulaimane.sololeveling.skill;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * The six branches of the skill tree, one per class. They are fixed: a skill belongs to exactly one branch.
 *
 * Note: the Mage branch holds ordinary skills (mana, focus, intellect...). Spells are a different
 * system: they are learned by burning a Magic Paper, see the magic package.
 */
public enum SkillBranch {
    TANK("tank", "Tank", 0xFF5E8BFF, "Endure. Armor, health and holding the line.", () -> Items.SHIELD),
    SUPPORT("support", "Support", 0xFF55E08A, "Sustain. Healing, buffs and keeping allies alive.", () -> Items.GOLDEN_APPLE),
    WARRIOR("warrior", "Warrior", 0xFFFF5B4A, "Strike. Raw melee power and combat technique.", () -> Items.IRON_SWORD),
    MARKSMAN("marksman", "Marksman", 0xFFFFB23F, "Aim. Bows, range and precision.", () -> Items.BOW),
    MAGE("mage", "Mage", 0xFFB27BFF, "Intellect. Mana, focus and mastery of the mind. Spells are not learned here: burn a Magic Paper.", () -> Items.AMETHYST_SHARD),
    ASSASSIN("assassin", "Assassin", 0xFF9AA0B5, "Shadow. Speed, critical hits and ambush.", () -> Items.FEATHER);

    private final String id;
    private final String englishName;
    private final int color;
    private final String englishTagline;
    private final Supplier<Item> icon;

    SkillBranch(String id, String englishName, int color, String englishTagline, Supplier<Item> icon) {
        this.id = id;
        this.englishName = englishName;
        this.color = color;
        this.englishTagline = englishTagline;
        this.icon = icon;
    }

    public String id() { return id; }

    /** ARGB colour of the branch (tab, frames). */
    public int color() { return color; }

    public Item icon() { return icon.get(); }

    public Component displayName() {
        return Component.translatableWithFallback("sololeveling.branch." + id, englishName);
    }

    public Component tagline() {
        return Component.translatableWithFallback("sololeveling.branch." + id + ".desc", englishTagline);
    }

    public static SkillBranch byId(String id) {
        for (SkillBranch b : values()) {
            if (b.id.equalsIgnoreCase(id)) return b;
        }
        return null;
    }
}
