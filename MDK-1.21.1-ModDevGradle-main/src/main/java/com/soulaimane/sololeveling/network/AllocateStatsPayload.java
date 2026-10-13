package com.soulaimane.sololeveling.network;

import com.soulaimane.sololeveling.SoloLeveling;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → Server: batch request to allocate stat points. */
public record AllocateStatsPayload(
        int dStrength, int dAgility, int dVitality,
        int dIntelligence, int dSense, int dDexterity, int dDurability
) implements CustomPacketPayload {

    public static final Type<AllocateStatsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloLeveling.MOD_ID, "allocate_stats"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AllocateStatsPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.dStrength());
                        buf.writeVarInt(p.dAgility());
                        buf.writeVarInt(p.dVitality());
                        buf.writeVarInt(p.dIntelligence());
                        buf.writeVarInt(p.dSense());
                        buf.writeVarInt(p.dDexterity());
                        buf.writeVarInt(p.dDurability());
                    },
                    buf -> new AllocateStatsPayload(
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                            buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public int totalCost() {
        return Math.max(0, dStrength) + Math.max(0, dAgility) + Math.max(0, dVitality)
                + Math.max(0, dIntelligence) + Math.max(0, dSense)
                + Math.max(0, dDexterity) + Math.max(0, dDurability);
    }
}