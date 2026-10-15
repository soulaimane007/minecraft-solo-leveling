package com.soulaimane.sololeveling.skill;

import com.soulaimane.sololeveling.SoloLeveling;
import com.soulaimane.sololeveling.power.PowerIds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every skill of the mod, in the order they were registered. Filled once, while the mod starts
 * (see {@link ModSkills#init()}); only read afterwards.
 */
public final class SkillRegistry {
    private SkillRegistry() {}

    private static final Map<String, Skill> SKILLS = new LinkedHashMap<>();

    /** Adds a skill. Registering the same id twice is a mistake and stops the game with a clear message. */
    public static Skill register(Skill skill) {
        if (SKILLS.containsKey(skill.id())) {
            throw new IllegalStateException("Skill registered twice: " + skill.id());
        }
        SKILLS.put(skill.id(), skill);
        return skill;
    }

    /** The skill with this id ("power_strike" or "sololeveling:power_strike"), or null. */
    public static Skill get(String id) {
        return SKILLS.get(PowerIds.normalize(id));
    }

    /** Exact lookup with a full id ("sololeveling:power_strike"). Faster than {@link #get}, no normalising. */
    public static Skill byId(String fullId) {
        return SKILLS.get(fullId);
    }

    public static Collection<Skill> all() {
        return Collections.unmodifiableCollection(SKILLS.values());
    }

    public static boolean isEmpty() {
        return SKILLS.isEmpty();
    }

    /** The skills of one branch, in registration order. */
    public static List<Skill> branch(SkillBranch branch) {
        List<Skill> out = new ArrayList<>();
        for (Skill s : SKILLS.values()) {
            if (s.branch() == branch) out.add(s);
        }
        return out;
    }

    /** Looks for mistakes (unknown parents...) and writes them in the log. Called once after ModSkills. */
    public static void validate() {
        for (Skill s : SKILLS.values()) {
            for (String parent : s.parents()) {
                Skill p = SKILLS.get(parent);
                if (p == null) {
                    SoloLeveling.LOGGER.warn("Skill {} has an unknown parent {}", s.id(), parent);
                } else if (p.branch() != s.branch()) {
                    SoloLeveling.LOGGER.warn("Skill {} (branch {}) has a parent in another branch: {} (branch {}). "
                            + "Use requires(...) for a cross-branch condition.", s.id(), s.branch(), p.id(), p.branch());
                }
                if (parent.equals(s.id())) {
                    SoloLeveling.LOGGER.warn("Skill {} is its own parent", s.id());
                }
            }
        }
        SoloLeveling.LOGGER.info("Skill tree: {} skill(s) registered", SKILLS.size());
    }
}
