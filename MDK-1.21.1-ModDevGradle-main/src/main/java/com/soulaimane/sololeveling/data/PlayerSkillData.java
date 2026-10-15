package com.soulaimane.sololeveling.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * What a player learned in the skill tree (saved with the player, kept after death).
 *
 * @param ranks the rank of every learned skill, by skill id ("sololeveling:power_strike" -> 2)
 * @param spent skill points already spent
 * @param bonus extra skill points granted by rewards or commands (on top of the points a level gives)
 */
public record PlayerSkillData(Map<String, Integer> ranks, int spent, int bonus) {

    public static final PlayerSkillData DEFAULT = new PlayerSkillData(Map.of(), 0, 0);

    public static final Codec<PlayerSkillData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("ranks", Map.of()).forGetter(PlayerSkillData::ranks),
            Codec.INT.optionalFieldOf("spent", 0).forGetter(PlayerSkillData::spent),
            Codec.INT.optionalFieldOf("bonus", 0).forGetter(PlayerSkillData::bonus)
    ).apply(i, PlayerSkillData::new));

    public int rank(String skillId) {
        return ranks.getOrDefault(skillId, 0);
    }

    /** Sets the rank of one skill (0 removes it) and changes the number of spent points by {@code spentDelta}. */
    public PlayerSkillData withRank(String skillId, int rank, int spentDelta) {
        Map<String, Integer> copy = new HashMap<>(ranks);
        if (rank <= 0) {
            copy.remove(skillId);
        } else {
            copy.put(skillId, rank);
        }
        return new PlayerSkillData(Map.copyOf(copy), Math.max(0, spent + spentDelta), bonus);
    }

    public PlayerSkillData withBonus(int newBonus) {
        return new PlayerSkillData(ranks, spent, newBonus);
    }

    /** Respec: every skill forgotten, every point refunded (the bonus points stay). */
    public PlayerSkillData cleared() {
        return new PlayerSkillData(Map.of(), 0, bonus);
    }
}
