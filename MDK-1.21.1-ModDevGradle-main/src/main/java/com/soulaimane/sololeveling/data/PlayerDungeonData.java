package com.soulaimane.sololeveling.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-player dungeon bookkeeping (saved with the player, kept after death):
 *  - whether the starting System Key was already handed out
 *  - where the player stood when they opened the gate, so the key can bring them back
 */
public record PlayerDungeonData(
        boolean startingKeyGiven,
        boolean hasReturn,
        String returnDimension,
        double returnX, double returnY, double returnZ,
        float returnYaw, float returnPitch
) {
    public static final PlayerDungeonData DEFAULT = new PlayerDungeonData(
            false, false, "minecraft:overworld", 0, 0, 0, 0f, 0f);

    public static final Codec<PlayerDungeonData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("starting_key_given", false).forGetter(PlayerDungeonData::startingKeyGiven),
            Codec.BOOL.optionalFieldOf("has_return", false).forGetter(PlayerDungeonData::hasReturn),
            Codec.STRING.optionalFieldOf("return_dimension", "minecraft:overworld").forGetter(PlayerDungeonData::returnDimension),
            Codec.DOUBLE.optionalFieldOf("return_x", 0.0).forGetter(PlayerDungeonData::returnX),
            Codec.DOUBLE.optionalFieldOf("return_y", 0.0).forGetter(PlayerDungeonData::returnY),
            Codec.DOUBLE.optionalFieldOf("return_z", 0.0).forGetter(PlayerDungeonData::returnZ),
            Codec.FLOAT.optionalFieldOf("return_yaw", 0f).forGetter(PlayerDungeonData::returnYaw),
            Codec.FLOAT.optionalFieldOf("return_pitch", 0f).forGetter(PlayerDungeonData::returnPitch)
    ).apply(i, PlayerDungeonData::new));

    public PlayerDungeonData withStartingKeyGiven(boolean v) {
        return new PlayerDungeonData(v, hasReturn, returnDimension, returnX, returnY, returnZ, returnYaw, returnPitch);
    }

    public PlayerDungeonData withReturn(String dimension, double x, double y, double z, float yaw, float pitch) {
        return new PlayerDungeonData(startingKeyGiven, true, dimension, x, y, z, yaw, pitch);
    }

    public PlayerDungeonData withoutReturn() {
        return new PlayerDungeonData(startingKeyGiven, false, returnDimension, returnX, returnY, returnZ, returnYaw, returnPitch);
    }
}
