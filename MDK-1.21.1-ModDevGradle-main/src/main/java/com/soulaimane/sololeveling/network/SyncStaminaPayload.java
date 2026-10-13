package com.soulaimane.sololeveling.network;

import com.soulaimane.sololeveling.SoloLeveling;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncStaminaPayload(float stamina, float maxStamina, boolean exhausted)
        implements CustomPacketPayload {

    public static final Type<SyncStaminaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloLeveling.MOD_ID, "sync_stamina"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncStaminaPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeFloat(p.stamina());
                        buf.writeFloat(p.maxStamina());
                        buf.writeBoolean(p.exhausted());
                    },
                    buf -> new SyncStaminaPayload(buf.readFloat(), buf.readFloat(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}