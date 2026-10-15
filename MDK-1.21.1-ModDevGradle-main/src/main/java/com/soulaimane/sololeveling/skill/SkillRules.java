package com.soulaimane.sololeveling.skill;

import com.soulaimane.sololeveling.power.PowerView;

/**
 * The rules of the skill tree, in one place. The server uses them to accept or refuse an unlock,
 * the skill tree screen uses the very same function to colour its nodes.
 */
public final class SkillRules {
    private SkillRules() {}

    public enum Status {
        /** Already at the highest rank. */
        MAXED,
        /** Can be learned / upgraded right now. */
        AVAILABLE,
        /** Everything is fine except the skill points. */
        NO_POINTS,
        /** A parent skill is not learned yet. */
        NEEDS_PARENT,
        /** Level, rank, stat, skill or magic conditions are not met. */
        NEEDS_REQUIREMENTS
    }

    /**
     * @param view            the player (server data or the client's synced copy)
     * @param availablePoints skill points the player still has
     */
    public static Status status(Skill skill, PowerView view, int availablePoints) {
        int rank = view.skillRank(skill.id());
        if (rank >= skill.maxRank()) {
            return Status.MAXED;
        }
        for (String parent : skill.parents()) {
            if (view.skillRank(parent) < 1) {
                return Status.NEEDS_PARENT;
            }
        }
        if (!skill.requirementsFor(rank + 1).isMet(view)) {
            return Status.NEEDS_REQUIREMENTS;
        }
        if (availablePoints < skill.pointCost(rank + 1)) {
            return Status.NO_POINTS;
        }
        return Status.AVAILABLE;
    }
}
