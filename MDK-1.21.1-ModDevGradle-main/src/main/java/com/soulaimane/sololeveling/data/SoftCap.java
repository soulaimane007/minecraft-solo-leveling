package com.soulaimane.sololeveling.data;

/**
 * Formerly applied rank-based diminishing returns.
 * Now: every point of a stat counts at full value, regardless of rank or level.
 * Kept as a utility so call sites don't need to change.
 */
public final class SoftCap {
    private SoftCap() {}

    /** Returns the raw stat value — no cap, no rank dependency. */
    public static double effective(int points, Rank rank) {
        return points;
    }

    public static double effective(int points, int rankOrdinal) {
        return points;
    }
}