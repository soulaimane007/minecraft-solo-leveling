package com.soulaimane.sololeveling.magic;

import com.soulaimane.sololeveling.power.PowerIds;
import com.soulaimane.sololeveling.power.Requirements;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * One magic (spell). Build it with {@link #builder} and register it in {@link ModMagics}.
 *
 * Magics are not learned in the skill tree: the player burns a Magic Paper of that magic
 * (an item, see MagicPaperItem) while he meets the requirements. A learned magic sits on the action bar like a skill.
 */
public final class Magic {
    private final String id;                       // "sololeveling:flame_bolt"
    private final ResourceLocation location;
    private final String englishName;
    private final String englishDescription;
    private final int color;
    private final Supplier<? extends ItemLike> icon;
    private final Requirements requirements;
    private final float manaCost;
    private final float staminaCost;
    private final double cooldownSeconds;
    private final MagicEffect effect;

    private ItemStack iconStack;

    private Magic(Builder b) {
        this.location = PowerIds.location(b.path);
        this.id = location.toString();
        this.englishName = b.name != null ? b.name : PowerIds.prettyName(b.path);
        this.englishDescription = b.description;
        this.color = b.color;
        this.icon = b.icon != null ? b.icon : () -> Items.PAPER;
        this.requirements = b.requirements;
        this.manaCost = b.manaCost;
        this.staminaCost = b.staminaCost;
        this.cooldownSeconds = b.cooldownSeconds;
        this.effect = b.effect;
    }

    /** Full id, e.g. "sololeveling:flame_bolt". */
    public String id() { return id; }

    public ResourceLocation location() { return location; }

    private String translationKey() {
        return "magic." + location.getNamespace() + "." + location.getPath().replace('/', '.');
    }

    public Component displayName() {
        return Component.translatableWithFallback(translationKey(), englishName);
    }

    public Component description() {
        return Component.translatableWithFallback(translationKey() + ".desc", englishDescription);
    }

    /** ARGB colour of the magic (name, paper tooltip). */
    public int color() { return color; }

    /** The icon shown on the action bar and in the Magic tab. Do not modify the returned stack. */
    public ItemStack iconStack() {
        if (iconStack == null) {
            iconStack = new ItemStack(icon.get());
        }
        return iconStack;
    }

    /** What the player needs to burn the paper. */
    public Requirements requirements() { return requirements; }

    public float manaCost() { return manaCost; }

    public float staminaCost() { return staminaCost; }

    public int cooldownTicks() { return Math.max(0, (int) Math.round(cooldownSeconds * 20.0)); }

    public MagicEffect effect() { return effect; }

    // ─── Builder ─────────────────────────────────────────────────────

    /** Starts a magic. {@code path} is its id without the namespace: "flame_bolt". */
    public static Builder builder(String path) {
        return new Builder(path);
    }

    public static final class Builder {
        private final String path;
        private String name;
        private String description = "";
        private int color = 0xFFB27BFF;
        private Supplier<? extends ItemLike> icon;
        private Requirements requirements = Requirements.NONE;
        private float manaCost = 0f;
        private float staminaCost = 0f;
        private double cooldownSeconds = 0.0;
        private MagicEffect effect;

        private Builder(String path) {
            this.path = path;
        }

        /** The name shown to the player (English). A lang file entry "magic.sololeveling.<path>" overrides it. */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /** What the magic does, in a few words. A lang file entry "magic.sololeveling.<path>.desc" overrides it. */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /** ARGB colour, e.g. 0xFFFF8A2B (orange). */
        public Builder color(int argb) {
            this.color = argb;
            return this;
        }

        /** Any item can be the icon: {@code .icon(() -> Items.BLAZE_POWDER)}. */
        public Builder icon(Supplier<? extends ItemLike> icon) {
            this.icon = icon;
            return this;
        }

        /** What the player needs to learn it from the paper: level, rank, stats, skills, other magics. */
        public Builder requires(Requirements requirements) {
            this.requirements = requirements;
            return this;
        }

        public Builder mana(double cost) {
            this.manaCost = (float) Math.max(0.0, cost);
            return this;
        }

        public Builder stamina(double cost) {
            this.staminaCost = (float) Math.max(0.0, cost);
            return this;
        }

        public Builder cooldownSeconds(double seconds) {
            this.cooldownSeconds = Math.max(0.0, seconds);
            return this;
        }

        /** What happens when the player casts it. */
        public Builder cast(MagicEffect effect) {
            this.effect = effect;
            return this;
        }

        public Magic build() {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("A magic needs an id");
            }
            if (effect == null) {
                throw new IllegalArgumentException("Magic " + path + " has no cast(...) effect");
            }
            return new Magic(this);
        }
    }
}
