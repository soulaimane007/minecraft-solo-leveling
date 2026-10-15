package com.soulaimane.sololeveling.power;

import com.soulaimane.sololeveling.data.Rank;
import com.soulaimane.sololeveling.magic.Magic;
import com.soulaimane.sololeveling.magic.MagicRegistry;
import com.soulaimane.sololeveling.skill.Skill;
import com.soulaimane.sololeveling.skill.SkillRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A set of conditions: level, rank, stats, learned skills, learned magics.
 * The same class is used by skills (to unlock them), magics (to learn them from a paper)
 * and dungeons (to walk in).
 *
 * <pre>
 *   Requirements.builder().level(10).stat("intelligence", 15).skill("power_strike", 2).build()
 * </pre>
 */
public final class Requirements {

    public static final List<String> STATS = List.of(
            "strength", "agility", "vitality", "intelligence", "sense", "dexterity", "durability");

    public static final Requirements NONE = new Builder().build();

    /** One line of the checklist: "Level 10", true / false. */
    public record Line(String text, boolean met) {}

    private final int level;
    private final Rank rank;                       // null = no rank needed
    private final Map<String, Integer> stats;      // "strength" -> 10
    private final Map<String, Integer> skills;     // "sololeveling:power_strike" -> 2
    private final List<String> magics;             // "sololeveling:flame_bolt"

    private Requirements(int level, Rank rank, Map<String, Integer> stats,
                         Map<String, Integer> skills, List<String> magics) {
        this.level = level;
        this.rank = rank;
        this.stats = stats;
        this.skills = skills;
        this.magics = magics;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Requirements atLevel(int level) {
        return new Builder().level(level).build();
    }

    // ─── Reading ─────────────────────────────────────────────────────

    public int minLevel() { return level; }

    /** Magics (full ids) that must already be known. */
    public List<String> magicIds() { return magics; }

    /** Skills (full ids) that must be learned, with the rank each one needs. */
    public Map<String, Integer> skillRanks() { return skills; }

    public boolean isEmpty() {
        return level <= 0 && rank == null && stats.isEmpty() && skills.isEmpty() && magics.isEmpty();
    }

    /** The same conditions, but the level is at least {@code minLevel} (used for "rank 2 needs level 10"). */
    public Requirements withMinLevel(int minLevel) {
        if (minLevel <= level) return this;
        return new Requirements(minLevel, rank, stats, skills, magics);
    }

    public boolean isMet(PowerView v) {
        if (level > 0 && v.level() < level) return false;
        if (rank != null && v.rankOrdinal() < rank.ordinal()) return false;
        for (Map.Entry<String, Integer> e : stats.entrySet()) {
            if (v.stat(e.getKey()) < e.getValue()) return false;
        }
        for (Map.Entry<String, Integer> e : skills.entrySet()) {
            if (v.skillRank(e.getKey()) < e.getValue()) return false;
        }
        for (String m : magics) {
            if (!v.knowsMagic(m)) return false;
        }
        return true;
    }

    /** Every condition with a met / not met flag, ready to be shown as a checklist. */
    public List<Line> lines(PowerView v) {
        List<Line> out = new ArrayList<>();
        if (level > 0) {
            out.add(new Line("Level " + level, v.level() >= level));
        }
        if (rank != null) {
            out.add(new Line("Rank " + rank.getSerializedName(), v.rankOrdinal() >= rank.ordinal()));
        }
        for (Map.Entry<String, Integer> e : stats.entrySet()) {
            out.add(new Line(capitalize(e.getKey()) + " " + e.getValue(), v.stat(e.getKey()) >= e.getValue()));
        }
        for (Map.Entry<String, Integer> e : skills.entrySet()) {
            String text = "Skill: " + skillName(e.getKey()) + (e.getValue() > 1 ? " (rank " + e.getValue() + ")" : "");
            out.add(new Line(text, v.skillRank(e.getKey()) >= e.getValue()));
        }
        for (String m : magics) {
            out.add(new Line("Magic: " + magicName(m), v.knowsMagic(m)));
        }
        return out;
    }

    /** Only the conditions that are not met yet. */
    public List<String> unmet(PowerView v) {
        List<String> out = new ArrayList<>();
        for (Line line : lines(v)) {
            if (!line.met()) out.add(line.text());
        }
        return out;
    }

    private static String skillName(String id) {
        Skill s = SkillRegistry.get(id);
        return s != null ? s.displayName().getString() : PowerIds.prettyName(id);
    }

    private static String magicName(String id) {
        Magic m = MagicRegistry.get(id);
        return m != null ? m.displayName().getString() : PowerIds.prettyName(id);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ─── Builder ─────────────────────────────────────────────────────

    public static final class Builder {
        private int level = 0;
        private Rank rank = null;
        private final Map<String, Integer> stats = new LinkedHashMap<>();
        private final Map<String, Integer> skills = new LinkedHashMap<>();
        private final List<String> magics = new ArrayList<>();

        private Builder() {}

        public Builder level(int level) {
            this.level = Math.max(0, level);
            return this;
        }

        public Builder rank(Rank rank) {
            this.rank = rank;
            return this;
        }

        /** "strength", "agility", "vitality", "intelligence", "sense", "dexterity" or "durability". */
        public Builder stat(String name, int minimum) {
            String key = name.toLowerCase(Locale.ROOT);
            if (!STATS.contains(key)) {
                throw new IllegalArgumentException("Unknown stat '" + name + "' (use one of " + STATS + ")");
            }
            stats.put(key, minimum);
            return this;
        }

        /** The player must know this skill at (at least) this rank. */
        public Builder skill(String skillId, int minimumRank) {
            skills.put(PowerIds.normalize(skillId), Math.max(1, minimumRank));
            return this;
        }

        /** The player must already know this magic. */
        public Builder magic(String magicId) {
            magics.add(PowerIds.normalize(magicId));
            return this;
        }

        public Requirements build() {
            // Collections.unmodifiableMap keeps the order the conditions were added in (Map.copyOf would shuffle it)
            return new Requirements(level, rank,
                    Collections.unmodifiableMap(new LinkedHashMap<>(stats)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(skills)),
                    List.copyOf(magics));
        }
    }
}
