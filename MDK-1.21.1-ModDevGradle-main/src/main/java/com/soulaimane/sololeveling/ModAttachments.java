package com.soulaimane.sololeveling;

import com.soulaimane.sololeveling.data.PlayerSystemData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SoloLeveling.MOD_ID);

    public static final Supplier<AttachmentType<PlayerSystemData>> PLAYER_SYSTEM_DATA =
            ATTACHMENT_TYPES.register("player_system_data", () ->
                    AttachmentType.builder(() -> PlayerSystemData.DEFAULT)
                            .serialize(PlayerSystemData.CODEC)
                            .copyOnDeath()
                            .build());

    /** Combat state flag — used for poise regen reset. Not persisted. */
    public static final Supplier<AttachmentType<Integer>> LAST_HIT_TICK =
            ATTACHMENT_TYPES.register("last_hit_tick", () ->
                    AttachmentType.builder(() -> 0).build());
}