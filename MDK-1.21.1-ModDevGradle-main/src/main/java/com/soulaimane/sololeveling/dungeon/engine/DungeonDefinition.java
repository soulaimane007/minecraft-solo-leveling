package com.soulaimane.sololeveling.dungeon.engine;

import com.soulaimane.sololeveling.power.PowerIds;
import com.soulaimane.sololeveling.power.Requirements;
import com.soulaimane.sololeveling.util.SLResource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One dungeon: a name, a list of levels, who may enter, and what it pays. Build it with {@link #builder}
 * and register it in {@link ModDungeons}. A Dungeon Key item exists for every registered dungeon.
 *
 * Where it lives: all dungeons share the world "sololeveling:dungeon_realm". Each dungeon has its own slot
 * (its areas are 2048 blocks apart on the x axis) and each level its own area (256 blocks apart on the z axis),
 * so nothing ever overlaps.
 *
 * Only one run of a dungeon exists at a time: a player who opens the gate while a run is going joins it.
 */
public final class DungeonDefinition {

    /** The world shared by all dungeons (data/sololeveling/dimension/dungeon_realm.json). */
    public static final ResourceKey<Level> DEFAULT_DIMENSION =
            ResourceKey.create(Registries.DIMENSION, SLResource.id("dungeon_realm"));

    /** Distance between the areas of two dungeons (x axis). */
    public static final int SLOT_SPACING = 2048;
    /** Distance between the areas of two levels of one dungeon (z axis). */
    public static final int LEVEL_SPACING = 256;
    /** Height of the floor of every level. */
    public static final int BASE_Y = 64;

    private final String id;
    private final ResourceLocation location;
    private final String englishName;
    private final String englishDescription;
    private final int color;
    private final ResourceKey<Level> dimension;
    private int slot;
    private final Requirements requirements;
    private final List<DungeonLevel> levels;
    private final boolean consumeKey;
    private final boolean resumeFromProgress;
    private final boolean autoExit;
    private final boolean allowPeaceful;
    private final LevelRewards completionRewards;
    private final Consumer<ServerPlayer> onEnter;

    private DungeonDefinition(Builder b) {
        this.location = PowerIds.location(b.path);
        this.id = location.toString();
        this.englishName = b.name != null ? b.name : PowerIds.prettyName(b.path);
        this.englishDescription = b.description;
        this.color = b.color;
        this.dimension = b.dimension;
        this.slot = b.slot;
        this.requirements = b.requirements;
        this.levels = List.copyOf(b.levels);
        this.consumeKey = b.consumeKey;
        this.resumeFromProgress = b.resumeFromProgress;
        this.autoExit = b.autoExit;
        this.allowPeaceful = b.allowPeaceful;
        this.completionRewards = b.completionRewards;
        this.onEnter = b.onEnter;
    }

    // ─── Identity ────────────────────────────────────────────────────

    /** Full id, e.g. "sololeveling:goblin_cave". */
    public String id() { return id; }

    public ResourceLocation location() { return location; }

    private String translationKey() {
        return "dungeon." + location.getNamespace() + "." + location.getPath().replace('/', '.');
    }

    public Component displayName() {
        return Component.translatableWithFallback(translationKey(), englishName);
    }

    public Component description() {
        return Component.translatableWithFallback(translationKey() + ".desc", englishDescription);
    }

    /** ARGB colour used for the key and the titles. */
    public int color() { return color; }

    // ─── Rules ───────────────────────────────────────────────────────

    public Requirements entryRequirements() { return requirements; }

    public List<DungeonLevel> levels() { return levels; }

    public int levelCount() { return levels.size(); }

    /** @param index 0-based */
    public DungeonLevel level(int index) { return levels.get(index); }

    /** Is the key used up when the gate opens? */
    public boolean consumesKey() { return consumeKey; }

    /** Does a player who opens the gate with a plain key start at the first level he has not cleared? */
    public boolean resumesFromProgress() { return resumeFromProgress; }

    /** After the last level: are the players taken out by themselves? */
    public boolean autoExit() { return autoExit; }

    /** May the gate be opened on Peaceful difficulty (where hostile mobs cannot exist)? */
    public boolean allowsPeaceful() { return allowPeaceful; }

    public LevelRewards completionRewards() { return completionRewards; }

    void runOnEnter(ServerPlayer player) { onEnter.accept(player); }

    // ─── Place in the world ──────────────────────────────────────────

    public ResourceKey<Level> dimension() { return dimension; }

    public int slot() { return slot; }

    void assignSlot(int slot) { this.slot = slot; }

    /** The corner of the area of a level (0-based index). */
    public BlockPos levelOrigin(int levelIndex) {
        return new BlockPos(slot * SLOT_SPACING, BASE_Y, levelIndex * LEVEL_SPACING);
    }

    // ─── Builder ─────────────────────────────────────────────────────

    /** Starts a dungeon. {@code path} is its id without the namespace: "goblin_cave". */
    public static Builder builder(String path) {
        return new Builder(path);
    }

    public static final class Builder {
        private final String path;
        private String name;
        private String description = "";
        private int color = 0xFFFFAA00;
        private ResourceKey<Level> dimension = DEFAULT_DIMENSION;
        private int slot = -1;
        private Requirements requirements = Requirements.NONE;
        private final List<DungeonLevel> levels = new ArrayList<>();
        private boolean consumeKey = true;
        private boolean resumeFromProgress = true;
        private boolean autoExit = true;
        private boolean allowPeaceful = false;
        private LevelRewards completionRewards = LevelRewards.NONE;
        private Consumer<ServerPlayer> onEnter = player -> { };

        private Builder(String path) {
            this.path = path;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder color(int argb) {
            this.color = argb;
            return this;
        }

        /** What a player needs (level, rank, stats, skills, magics) to open the gate. */
        public Builder requires(Requirements requirements) {
            this.requirements = requirements;
            return this;
        }

        /** Adds the next level. The first call adds level 1. */
        public Builder level(DungeonLevel level) {
            this.levels.add(level);
            return this;
        }

        /** The key is used up when the gate opens (default), or stays in the inventory. */
        public Builder consumeKey(boolean consume) {
            this.consumeKey = consume;
            return this;
        }

        /**
         * true (default): a plain key starts at the first level the player has not cleared yet.
         * false: a plain key always starts at level 1. (A key written for a level always starts there.)
         */
        public Builder resumeFromProgress(boolean resume) {
            this.resumeFromProgress = resume;
            return this;
        }

        /** After the last level the players stay (false) instead of being taken out after 15 seconds (true, default). */
        public Builder autoExit(boolean autoExit) {
            this.autoExit = autoExit;
            return this;
        }

        /** Lets the gate open on Peaceful difficulty. Only for dungeons without hostile mobs. */
        public Builder allowPeaceful() {
            this.allowPeaceful = true;
            return this;
        }

        /** Extra rewards for clearing the LAST level, on top of that level's own rewards. */
        public Builder completionRewards(LevelRewards rewards) {
            this.completionRewards = rewards;
            return this;
        }

        /** Runs for every player who arrives in the dungeon (give a starter item, a message...). */
        public Builder onEnter(Consumer<ServerPlayer> hook) {
            this.onEnter = hook;
            return this;
        }

        /** Another world than the shared one (needs its own dimension JSON files). */
        public Builder dimension(ResourceKey<Level> dimension) {
            this.dimension = dimension;
            return this;
        }

        /** Chooses the slot by hand. By default dungeons get 0, 1, 2... in registration order. */
        public Builder slot(int slot) {
            this.slot = Math.max(0, slot);
            return this;
        }

        public DungeonDefinition build() {
            if (levels.isEmpty()) {
                throw new IllegalStateException("The dungeon '" + path + "' has no level: add at least one .level(...)");
            }
            return new DungeonDefinition(this);
        }
    }
}
