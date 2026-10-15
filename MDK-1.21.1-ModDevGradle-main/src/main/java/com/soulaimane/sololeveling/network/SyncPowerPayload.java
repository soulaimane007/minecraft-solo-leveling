package com.soulaimane.sololeveling.network;

import com.soulaimane.sololeveling.SoloLeveling;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server → Client: everything the skill tree and the action bar need.
 *
 * @param skillPoints skill points the player can still spend (the server counts them: the client never does)
 * @param ranks       learned skills and their rank, by skill id
 * @param magics      learned magics, by magic id
 * @param slots       the action bar entries ("" = empty slot)
 */
public record SyncPowerPayload(int skillPoints, Map<String, Integer> ranks,
                               List<String> magics, List<String> slots) implements CustomPacketPayload {

    public static final Type<SyncPowerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloLeveling.MOD_ID, "sync_power"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPowerPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.skillPoints());
                        buf.writeVarInt(p.ranks().size());
                        for (Map.Entry<String, Integer> e : p.ranks().entrySet()) {
                            buf.writeUtf(e.getKey());
                            buf.writeVarInt(e.getValue());
                        }
                        buf.writeVarInt(p.magics().size());
                        for (String m : p.magics()) {
                            buf.writeUtf(m);
                        }
                        buf.writeVarInt(p.slots().size());
                        for (String s : p.slots()) {
                            buf.writeUtf(s);
                        }
                    },
                    buf -> {
                        int points = buf.readVarInt();
                        int rankCount = buf.readVarInt();
                        Map<String, Integer> ranks = new HashMap<>();
                        for (int i = 0; i < rankCount; i++) {
                            String id = buf.readUtf();
                            ranks.put(id, buf.readVarInt());
                        }
                        int magicCount = buf.readVarInt();
                        List<String> magics = new ArrayList<>();
                        for (int i = 0; i < magicCount; i++) {
                            magics.add(buf.readUtf());
                        }
                        int slotCount = buf.readVarInt();
                        List<String> slots = new ArrayList<>();
                        for (int i = 0; i < slotCount; i++) {
                            slots.add(buf.readUtf());
                        }
                        return new SyncPowerPayload(points, ranks, magics, slots);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
