package com.soulaimane.sololeveling.network;

import com.soulaimane.sololeveling.SoloLeveling;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncPlayerDataPayload(
        int level, int xp, int rankOrdinal, int statPoints,
        int strength, int agility, int vitality, int intelligence, int sense,
        int dexterity, int durability,
        float stamina, float mana, double equipLoad, double maxEquipLoad,
        int loadStateOrdinal, boolean exhausted
) implements CustomPacketPayload {

    public static final Type<SyncPlayerDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloLeveling.MOD_ID, "sync_player_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerDataPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.level()); buf.writeVarInt(p.xp());
                        buf.writeVarInt(p.rankOrdinal()); buf.writeVarInt(p.statPoints());
                        buf.writeVarInt(p.strength()); buf.writeVarInt(p.agility());
                        buf.writeVarInt(p.vitality()); buf.writeVarInt(p.intelligence());
                        buf.writeVarInt(p.sense()); buf.writeVarInt(p.dexterity());
                        buf.writeVarInt(p.durability());
                        buf.writeFloat(p.stamina()); buf.writeFloat(p.mana());
                        buf.writeDouble(p.equipLoad()); buf.writeDouble(p.maxEquipLoad());
                        buf.writeVarInt(p.loadStateOrdinal()); buf.writeBoolean(p.exhausted());
                    },
                    buf -> new SyncPlayerDataPayload(
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                            buf.readFloat(), buf.readFloat(), buf.readDouble(), buf.readDouble(),
                            buf.readVarInt(), buf.readBoolean())
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static SyncPlayerDataPayload from(PlayerSystemData d, double maxLoad, int loadState) {
        return new SyncPlayerDataPayload(
                d.level(), d.xp(), d.rank().ordinal(), d.statPoints(),
                d.strength(), d.agility(), d.vitality(), d.intelligence(), d.sense(),
                d.dexterity(), d.durability(),
                d.stamina(), d.mana(), d.currentEquipLoad(), maxLoad,
                loadState, d.exhausted());
    }
}