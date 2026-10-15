package com.soulaimane.sololeveling.network;

import com.soulaimane.sololeveling.SoloLeveling;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server → Client: current mana and the real (attribute based) maximum mana. */
public record SyncManaPayload(float mana, float maxMana) implements CustomPacketPayload {

    public static final Type<SyncManaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloLeveling.MOD_ID, "sync_mana"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncManaPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeFloat(p.mana());
                        buf.writeFloat(p.maxMana());
                    },
                    buf -> new SyncManaPayload(buf.readFloat(), buf.readFloat())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
