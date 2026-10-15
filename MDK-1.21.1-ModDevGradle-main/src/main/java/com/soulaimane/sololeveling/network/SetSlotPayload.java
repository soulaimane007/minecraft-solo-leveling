package com.soulaimane.sololeveling.network;

import com.soulaimane.sololeveling.SoloLeveling;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → Server: put a skill / magic on an action bar slot ("" empties the slot). */
public record SetSlotPayload(int slot, String entry) implements CustomPacketPayload {

    public static final Type<SetSlotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloLeveling.MOD_ID, "set_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetSlotPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.slot());
                        buf.writeUtf(p.entry());
                    },
                    buf -> new SetSlotPayload(buf.readVarInt(), buf.readUtf(256))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
