package com.soulaimane.sololeveling.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * How far a player got in each dungeon of the dungeon framework (saved with the player, kept after death).
 * The value is the number of levels cleared: 3 means levels 1, 2 and 3 are done.
 */
public record PlayerDungeonProgress(Map<String, Integer> cleared) {

    public static final PlayerDungeonProgress DEFAULT = new PlayerDungeonProgress(Map.of());

    public static final Codec<PlayerDungeonProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("cleared", Map.of()).forGetter(PlayerDungeonProgress::cleared)
    ).apply(i, PlayerDungeonProgress::new));

    public int clearedLevels(String dungeonId) {
        return cleared.getOrDefault(dungeonId, 0);
    }

    /** Remembers that {@code levelsCleared} levels of this dungeon are done (never goes down). */
    public PlayerDungeonProgress withCleared(String dungeonId, int levelsCleared) {
        if (levelsCleared <= clearedLevels(dungeonId)) return this;
        Map<String, Integer> copy = new HashMap<>(cleared);
        copy.put(dungeonId, levelsCleared);
        return new PlayerDungeonProgress(Map.copyOf(copy));
    }

    public PlayerDungeonProgress without(String dungeonId) {
        if (!cleared.containsKey(dungeonId)) return this;
        Map<String, Integer> copy = new HashMap<>(cleared);
        copy.remove(dungeonId);
        return new PlayerDungeonProgress(Map.copyOf(copy));
    }
}
