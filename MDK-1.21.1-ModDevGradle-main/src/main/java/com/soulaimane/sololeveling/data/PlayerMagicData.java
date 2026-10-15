package com.soulaimane.sololeveling.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/** The magics a player learned by burning Magic Papers (saved with the player, kept after death). */
public record PlayerMagicData(List<String> learned) {

    public static final PlayerMagicData DEFAULT = new PlayerMagicData(List.of());

    public static final Codec<PlayerMagicData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("learned", List.of()).forGetter(PlayerMagicData::learned)
    ).apply(i, PlayerMagicData::new));

    public boolean knows(String magicId) {
        return learned.contains(magicId);
    }

    public PlayerMagicData with(String magicId) {
        if (learned.contains(magicId)) return this;
        List<String> copy = new ArrayList<>(learned);
        copy.add(magicId);
        return new PlayerMagicData(List.copyOf(copy));
    }

    public PlayerMagicData without(String magicId) {
        if (!learned.contains(magicId)) return this;
        List<String> copy = new ArrayList<>(learned);
        copy.remove(magicId);
        return new PlayerMagicData(List.copyOf(copy));
    }
}
