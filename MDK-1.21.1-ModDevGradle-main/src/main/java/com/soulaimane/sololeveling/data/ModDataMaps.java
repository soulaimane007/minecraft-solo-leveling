package com.soulaimane.sololeveling.data;

import com.mojang.serialization.Codec;
import com.soulaimane.sololeveling.util.SLResource;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

public class ModDataMaps {

    public static final DataMapType<Item, Double> WEIGHT = DataMapType.builder(
            SLResource.id("weight"), Registries.ITEM, Codec.DOUBLE
    ).synced(Codec.DOUBLE, false).build();

    public static final DataMapType<Item, Integer> DEXTERITY_REQUIREMENT = DataMapType.builder(
            SLResource.id("dexterity_requirement"), Registries.ITEM, Codec.INT
    ).synced(Codec.INT, false).build();

    public static final DataMapType<Item, Double> POISE = DataMapType.builder(
            SLResource.id("poise"), Registries.ITEM, Codec.DOUBLE
    ).synced(Codec.DOUBLE, false).build();

    /** Base weapon damage (before STR scaling). */
    public static final DataMapType<Item, Double> WEAPON_DAMAGE = DataMapType.builder(
            SLResource.id("weapon_damage"), Registries.ITEM, Codec.DOUBLE
    ).synced(Codec.DOUBLE, false).build();

    public static final DataMapType<EntityType<?>, Integer> MOB_XP = DataMapType.builder(
            SLResource.id("mob_xp"), Registries.ENTITY_TYPE, Codec.INT
    ).synced(Codec.INT, false).build();

    @SubscribeEvent
    public static void register(RegisterDataMapTypesEvent event) {
        event.register(WEIGHT);
        event.register(DEXTERITY_REQUIREMENT);
        event.register(POISE);
        event.register(WEAPON_DAMAGE);
        event.register(MOB_XP);
    }
}