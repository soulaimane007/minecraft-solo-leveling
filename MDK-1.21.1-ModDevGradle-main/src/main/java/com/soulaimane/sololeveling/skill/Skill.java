package com.soulaimane.sololeveling.skill;

import com.soulaimane.sololeveling.power.PowerIds;
import com.soulaimane.sololeveling.power.Requirements;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntToDoubleFunction;
import java.util.function.Supplier;

/**
 * One skill of the skill tree. Build it with {@link #builder} and register it in {@link ModSkills}.
 *
 * A skill can be a passive (attribute bonuses, damage hooks), an active (an effect on a key press) or both.
 * Every rank costs skill points and may need a higher level.
 */
public final class Skill {

    /** An attribute bonus that grows with the rank: amount = perRank * rank. */
    public record AttributeBonus(Supplier<Holder<Attribute>> attribute, double perRank,
                                 AttributeModifier.Operation operation) {}

    private final String id;                       // "sololeveling:power_strike"
    private final ResourceLocation location;
    private final SkillBranch branch;
    private final String englishName;
    private final String englishDescription;
    private final Supplier<? extends ItemLike> icon;
    private final int maxRank;
    private final int[] pointCosts;                // cost of rank 1, 2, 3...
    private final List<String> parents;
    private final Requirements requirements;
    private final int[] rankLevels;                // minimum level for rank 1, 2, 3... (may be shorter than maxRank)
    private final int tier;                        // -1 = automatic
    private final int column;                      // -1 = automatic
    private final List<AttributeBonus> bonuses;
    private final SkillEffect effect;              // null = passive
    private final IntToDoubleFunction staminaCost;
    private final IntToDoubleFunction manaCost;
    private final IntToDoubleFunction cooldownSeconds;
    private final SkillDamageHook onDealDamage;
    private final SkillDamageHook onTakeDamage;

    private ItemStack iconStack;

    private Skill(Builder b) {
        this.location = PowerIds.location(b.path);
        this.id = location.toString();
        this.branch = b.branch;
        this.englishName = b.name != null ? b.name : PowerIds.prettyName(b.path);
        this.englishDescription = b.description;
        this.icon = b.icon != null ? b.icon : () -> b.branch.icon();
        this.maxRank = b.maxRank;
        this.pointCosts = padded(b.pointCosts, b.maxRank);
        this.parents = List.copyOf(b.parents);
        this.requirements = b.requirements;
        this.rankLevels = b.rankLevels;
        this.tier = b.tier;
        this.column = b.column;
        this.bonuses = List.copyOf(b.bonuses);
        this.effect = b.effect;
        this.staminaCost = b.staminaCost;
        this.manaCost = b.manaCost;
        this.cooldownSeconds = b.cooldownSeconds;
        this.onDealDamage = b.onDealDamage;
        this.onTakeDamage = b.onTakeDamage;
    }

    private static int[] padded(int[] costs, int length) {
        int[] out = new int[length];
        for (int i = 0; i < length; i++) {
            out[i] = costs.length == 0 ? 1 : costs[Math.min(i, costs.length - 1)];
        }
        return out;
    }

    // ─── Identity ────────────────────────────────────────────────────

    /** Full id, e.g. "sololeveling:power_strike". */
    public String id() { return id; }

    public ResourceLocation location() { return location; }

    public SkillBranch branch() { return branch; }

    private String translationKey() {
        return "skill." + location.getNamespace() + "." + location.getPath().replace('/', '.');
    }

    public Component displayName() {
        return Component.translatableWithFallback(translationKey(), englishName);
    }

    public Component description() {
        return Component.translatableWithFallback(translationKey() + ".desc", englishDescription);
    }

    /** The icon shown in the tree and on the action bar. Do not modify the returned stack. */
    public ItemStack iconStack() {
        if (iconStack == null) {
            iconStack = new ItemStack(icon.get());
        }
        return iconStack;
    }

    // ─── Tree rules ──────────────────────────────────────────────────

    public int maxRank() { return maxRank; }

    /** Skill points needed to buy {@code rank} (1 = the first rank). */
    public int pointCost(int rank) {
        return pointCosts[Math.max(0, Math.min(rank, maxRank) - 1)];
    }

    /** Skills (ids) that must be learned first. They are also the lines drawn in the tree. */
    public List<String> parents() { return parents; }

    /** Conditions for buying {@code rank}: the skill's requirements, with the level that rank needs. */
    public Requirements requirementsFor(int rank) {
        int level = 0;
        if (rank >= 1 && rankLevels.length > 0) {
            level = rankLevels[Math.min(rank, rankLevels.length) - 1];
        }
        return requirements.withMinLevel(level);
    }

    /** Fixed row in the tree, or -1 to let the tree place the skill under its parents. */
    public int tier() { return tier; }

    /** Fixed column in the tree, or -1 for automatic. */
    public int column() { return column; }

    // ─── Passives ────────────────────────────────────────────────────

    public List<AttributeBonus> bonuses() { return bonuses; }

    public SkillDamageHook onDealDamage() { return onDealDamage; }

    public SkillDamageHook onTakeDamage() { return onTakeDamage; }

    // ─── Active use ──────────────────────────────────────────────────

    public boolean isActive() { return effect != null; }

    public SkillEffect effect() { return effect; }

    public float staminaCost(int rank) { return (float) staminaCost.applyAsDouble(rank); }

    public float manaCost(int rank) { return (float) manaCost.applyAsDouble(rank); }

    public int cooldownTicks(int rank) {
        return Math.max(0, (int) Math.round(cooldownSeconds.applyAsDouble(rank) * 20.0));
    }

    // ─── Builder ─────────────────────────────────────────────────────

    /**
     * Starts a skill. {@code path} is its id without the namespace: "power_strike".
     * Everything except the path and the branch is optional.
     */
    public static Builder builder(String path, SkillBranch branch) {
        return new Builder(path, branch);
    }

    public static final class Builder {
        private final String path;
        private final SkillBranch branch;
        private String name;
        private String description = "";
        private Supplier<? extends ItemLike> icon;
        private int maxRank = 1;
        private int[] pointCosts = {1};
        private final List<String> parents = new ArrayList<>();
        private Requirements requirements = Requirements.NONE;
        private int[] rankLevels = new int[0];
        private int tier = -1;
        private int column = -1;
        private final List<AttributeBonus> bonuses = new ArrayList<>();
        private SkillEffect effect;
        private IntToDoubleFunction staminaCost = r -> 0.0;
        private IntToDoubleFunction manaCost = r -> 0.0;
        private IntToDoubleFunction cooldownSeconds = r -> 0.0;
        private SkillDamageHook onDealDamage;
        private SkillDamageHook onTakeDamage;

        private Builder(String path, SkillBranch branch) {
            this.path = path;
            this.branch = branch;
        }

        /** The name shown to the player (English). A lang file entry "skill.sololeveling.<path>" overrides it. */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /** What the skill does, in a few words. A lang file entry "skill.sololeveling.<path>.desc" overrides it. */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /** Any item can be the icon: {@code .icon(() -> Items.IRON_SWORD)}. */
        public Builder icon(Supplier<? extends ItemLike> icon) {
            this.icon = icon;
            return this;
        }

        public Builder maxRank(int maxRank) {
            this.maxRank = Math.max(1, maxRank);
            return this;
        }

        /** Skill points per rank, the same for every rank. */
        public Builder pointCost(int perRank) {
            this.pointCosts = new int[]{Math.max(1, perRank)};
            return this;
        }

        /** Skill points for rank 1, rank 2, rank 3... (the last number is used for any rank after it). */
        public Builder pointCosts(int... perRank) {
            this.pointCosts = perRank.length == 0 ? new int[]{1} : Arrays.copyOf(perRank, perRank.length);
            return this;
        }

        /** This skill is locked until {@code skillId} is learned. Can be used several times. Draws a line in the tree. */
        public Builder parent(String skillId) {
            this.parents.add(PowerIds.normalize(skillId));
            return this;
        }

        /** Extra conditions (level, rank, stats, other skills, magics) for every rank. */
        public Builder requires(Requirements requirements) {
            this.requirements = requirements;
            return this;
        }

        /** Minimum level for rank 1, rank 2, rank 3...: {@code .rankLevels(5, 15, 30)}. */
        public Builder rankLevels(int... levels) {
            this.rankLevels = Arrays.copyOf(levels, levels.length);
            return this;
        }

        /** Places the skill by hand: tier = row (0 = top), column = position in the row. Otherwise it is automatic. */
        public Builder position(int tier, int column) {
            this.tier = Math.max(0, tier);
            this.column = Math.max(0, column);
            return this;
        }

        /**
         * A passive bonus while the skill is learned: amount = perRank x rank.
         * {@code .attribute(() -> Attributes.MAX_HEALTH, 2.0, AttributeModifier.Operation.ADD_VALUE)}
         */
        public Builder attribute(Supplier<Holder<Attribute>> attribute, double perRank,
                                 AttributeModifier.Operation operation) {
            this.bonuses.add(new AttributeBonus(attribute, perRank, operation));
            return this;
        }

        /** Makes the skill usable: it can be put on the action bar and runs {@code effect} when its key is pressed. */
        public Builder active(SkillEffect effect) {
            this.effect = effect;
            return this;
        }

        public Builder stamina(double cost) {
            this.staminaCost = r -> cost;
            return this;
        }

        /** Stamina cost by rank: {@code .stamina(rank -> 20 + 5 * rank)}. */
        public Builder stamina(IntToDoubleFunction costByRank) {
            this.staminaCost = costByRank;
            return this;
        }

        public Builder mana(double cost) {
            this.manaCost = r -> cost;
            return this;
        }

        public Builder mana(IntToDoubleFunction costByRank) {
            this.manaCost = costByRank;
            return this;
        }

        public Builder cooldownSeconds(double seconds) {
            this.cooldownSeconds = r -> seconds;
            return this;
        }

        public Builder cooldownSeconds(IntToDoubleFunction secondsByRank) {
            this.cooldownSeconds = secondsByRank;
            return this;
        }

        /** Passive: runs whenever the player hits something (change the damage in the hook). */
        public Builder onDealDamage(SkillDamageHook hook) {
            this.onDealDamage = hook;
            return this;
        }

        /** Passive: runs whenever the player is about to take damage. */
        public Builder onTakeDamage(SkillDamageHook hook) {
            this.onTakeDamage = hook;
            return this;
        }

        public Skill build() {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("A skill needs an id");
            }
            return new Skill(this);
        }
    }
}
