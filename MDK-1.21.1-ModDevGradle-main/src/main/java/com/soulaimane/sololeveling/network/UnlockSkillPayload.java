package com.soulaimane.sololeveling.network;

import com.soulaimane.sololeveling.SoloLeveling;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → Server: "learn (or upgrade) this skill". The server checks every rule again. */
public record UnlockSkillPayload(String skillId) implements CustomPacketPayload {

    public static final Type<UnlockSkillPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloLeveling.MOD_ID, "unlock_skill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnlockSkillPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUtf(p.skillId()),
                    buf -> new UnlockSkillPayload(buf.readUtf(256))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
