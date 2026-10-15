package com.soulaimane.sololeveling.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Decides where each skill of a branch sits in the tree: a row ("tier") and a column.
 * No Minecraft code in here, so it is easy to test.
 *
 * Rules:
 * - a skill with a fixed tier ({@code position(tier, column)}) keeps it;
 * - otherwise it sits one row under its lowest parent of the same branch (no parent = the top row);
 * - in a row, a skill with a fixed column keeps it; the others go under the middle of their parents,
 *   or at the first free column when they have no parent. Two skills never share a cell.
 * - the result starts at column 0.
 */
public final class SkillTreeLayout {
    private SkillTreeLayout() {}

    public record Cell(int tier, int column) {}

    /**
     * @param items    the skills of ONE branch, in registration order
     * @param idOf     id of a skill
     * @param parentsOf ids of the skills that must be learned first (parents in other branches are ignored here)
     * @param tierOf   fixed tier, or a negative number for automatic
     * @param columnOf fixed column, or a negative number for automatic
     * @return the cell of every item, in the order of {@code items}
     */
    public static <T> Map<T, Cell> compute(List<T> items,
                                           Function<T, String> idOf,
                                           Function<T, List<String>> parentsOf,
                                           ToIntFunction<T> tierOf,
                                           ToIntFunction<T> columnOf) {
        Map<String, T> byId = new HashMap<>();
        for (T item : items) {
            byId.put(idOf.apply(item), item);
        }

        // ── 1. tiers ──
        Map<T, Integer> tiers = new HashMap<>();
        for (T item : items) {
            resolveTier(item, parentsOf, tierOf, byId, tiers, new HashSet<>());
        }

        int maxTier = 0;
        for (int t : tiers.values()) {
            maxTier = Math.max(maxTier, t);
        }

        // ── 2. columns, row by row ──
        Map<T, Integer> columns = new HashMap<>();
        for (int tier = 0; tier <= maxTier; tier++) {
            Set<Integer> taken = new HashSet<>();
            List<T> row = new ArrayList<>();
            for (T item : items) {
                if (tiers.get(item) == tier) row.add(item);
            }

            // fixed columns first, so automatic skills flow around them
            for (T item : row) {
                int wanted = columnOf.applyAsInt(item);
                if (wanted >= 0) {
                    int column = freeColumnFrom(wanted, taken);
                    taken.add(column);
                    columns.put(item, column);
                }
            }
            for (T item : row) {
                if (columns.containsKey(item)) continue;
                int wanted = middleOfParents(item, parentsOf, byId, columns);
                int column = freeColumnFrom(Math.max(0, wanted), taken);
                taken.add(column);
                columns.put(item, column);
            }
        }

        // ── 3. start at column 0 ──
        int minColumn = Integer.MAX_VALUE;
        for (int c : columns.values()) {
            minColumn = Math.min(minColumn, c);
        }
        if (minColumn == Integer.MAX_VALUE) minColumn = 0;

        Map<T, Cell> result = new LinkedHashMap<>();
        for (T item : items) {
            result.put(item, new Cell(tiers.get(item), columns.get(item) - minColumn));
        }
        return result;
    }

    public static int maxTier(Map<?, Cell> cells) {
        int max = 0;
        for (Cell c : cells.values()) {
            max = Math.max(max, c.tier());
        }
        return max;
    }

    public static int maxColumn(Map<?, Cell> cells) {
        int max = 0;
        for (Cell c : cells.values()) {
            max = Math.max(max, c.column());
        }
        return max;
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private static <T> int resolveTier(T item, Function<T, List<String>> parentsOf,
                                       ToIntFunction<T> fixedTier, Map<String, T> byId,
                                       Map<T, Integer> done, Set<T> visiting) {
        Integer known = done.get(item);
        if (known != null) return known;

        int fixed = fixedTier.applyAsInt(item);
        if (fixed >= 0) {
            done.put(item, fixed);
            return fixed;
        }
        if (!visiting.add(item)) {
            return 0;                               // a loop in the parents: break it instead of crashing
        }

        int tier = 0;
        for (String parentId : parentsOf.apply(item)) {
            T parent = byId.get(parentId);
            if (parent != null && parent != item) {
                tier = Math.max(tier, resolveTier(parent, parentsOf, fixedTier, byId, done, visiting) + 1);
            }
        }
        visiting.remove(item);
        done.put(item, tier);
        return tier;
    }

    /** The middle of the already placed parents of this branch, or -1 when it has none. */
    private static <T> int middleOfParents(T item, Function<T, List<String>> parentsOf,
                                           Map<String, T> byId, Map<T, Integer> columns) {
        int sum = 0;
        int count = 0;
        for (String parentId : parentsOf.apply(item)) {
            T parent = byId.get(parentId);
            Integer column = parent == null ? null : columns.get(parent);
            if (column != null) {
                sum += column;
                count++;
            }
        }
        return count == 0 ? -1 : Math.round(sum / (float) count);
    }

    /** {@code wanted} if it is free, otherwise the first free column to its right. */
    private static int freeColumnFrom(int wanted, Set<Integer> taken) {
        int column = wanted;
        while (taken.contains(column)) {
            column++;
        }
        return column;
    }
}
