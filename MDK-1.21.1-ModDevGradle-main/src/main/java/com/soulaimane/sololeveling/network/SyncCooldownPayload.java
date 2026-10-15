package com.soulaimane.sololeveling.network;

import com.soulaimane.sololeveling.SoloLeveling;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server → Client: a skill or magic was used and is cooling down (for the action bar). */
public record SyncCooldownPayload(String entry, int ticks) implements CustomPacketPayload {

    public static final Type<SyncCooldownPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloLeveling.MOD_ID, "sync_cooldown"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCooldownPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUtf(p.entry());
                        buf.writeVarInt(p.ticks());
                    },
                    buf -> new SyncCooldownPayload(buf.readUtf(), buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
