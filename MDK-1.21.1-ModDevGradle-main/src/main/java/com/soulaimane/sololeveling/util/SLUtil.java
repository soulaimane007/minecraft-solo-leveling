package com.soulaimane.sololeveling.util;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.Level;

/**
 * Small helpers shared by the whole mod (sounds, titles, attributes).
 *
 * Sounds are looked up by their registry name ("minecraft:entity.player.levelup")
 * instead of through the SoundEvents constants: if a name does not exist the call
 * silently does nothing instead of crashing the game.
 */
public final class SLUtil {
    private SLUtil() {}

    // ─────────────────────────────────────────────────────────────────
    // Sounds
    // ─────────────────────────────────────────────────────────────────

    /** Returns the sound with that registry name, or null if it does not exist. */
    public static SoundEvent sound(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        return BuiltInRegistries.SOUND_EVENT.getOptional(rl).orElse(null);
    }

    public static void playSound(Level level, double x, double y, double z,
                                 String id, SoundSource source, float volume, float pitch) {
        SoundEvent ev = sound(id);
        if (ev != null) level.playSound(null, x, y, z, ev, source, volume, pitch);
    }

    public static void playSound(Entity at, String id, SoundSource source, float volume, float pitch) {
        playSound(at.level(), at.getX(), at.getY(), at.getZ(), id, source, volume, pitch);
    }

    // ─────────────────────────────────────────────────────────────────
    // Screen text
    // ─────────────────────────────────────────────────────────────────

    /** Big centred title + subtitle (times are in ticks). */
    public static void title(ServerPlayer p, Component title, Component subtitle,
                             int fadeIn, int stay, int fadeOut) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    public static void title(ServerPlayer p, String title, String subtitle) {
        title(p, Component.literal(title), Component.literal(subtitle), 10, 50, 20);
    }

    public static void actionBar(ServerPlayer p, String text) {
        p.displayClientMessage(Component.literal(text), true);
    }

    public static void chat(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text));
    }

    // ─────────────────────────────────────────────────────────────────
    // Attributes
    // ─────────────────────────────────────────────────────────────────

    /** Sets the base value of an attribute only when it actually changes. */
    public static void setBase(LivingEntity entity, Holder<Attribute> attribute, double value) {
        AttributeInstance inst = entity.getAttribute(attribute);
        if (inst != null && inst.getBaseValue() != value) {
            inst.setBaseValue(value);
        }
    }
}
