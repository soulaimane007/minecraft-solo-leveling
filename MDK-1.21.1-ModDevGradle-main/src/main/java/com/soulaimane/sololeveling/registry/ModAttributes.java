package com.soulaimane.sololeveling.registry;

import com.soulaimane.sololeveling.SoloLeveling;
import com.soulaimane.sololeveling.util.SLResource;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, SoloLeveling.MOD_ID);

    public static final DeferredHolder<Attribute, Attribute> MAX_MANA =
            ATTRIBUTES.register("max_mana", () -> new RangedAttribute(
                    "attribute.sololeveling.max_mana", 20.0, 0.0, 100000.0).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> MANA_REGEN =
            ATTRIBUTES.register("mana_regen", () -> new RangedAttribute(
                    "attribute.sololeveling.mana_regen", 1.0, 0.0, 10000.0).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> MAX_STAMINA =
            ATTRIBUTES.register("max_stamina", () -> new RangedAttribute(
                    "attribute.sololeveling.max_stamina", 100.0, 0.0, 100000.0).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> STAMINA_REGEN =
            ATTRIBUTES.register("stamina_regen", () -> new RangedAttribute(
                    "attribute.sololeveling.stamina_regen", 15.0, 0.0, 10000.0).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> MAX_EQUIP_LOAD =
            ATTRIBUTES.register("max_equip_load", () -> new RangedAttribute(
                    "attribute.sololeveling.max_equip_load", 30.0, 0.0, 100000.0).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> POISE =
            ATTRIBUTES.register("poise", () -> new RangedAttribute(
                    "attribute.sololeveling.poise", 0.0, 0.0, 10000.0).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> EFFECT_RESISTANCE =
            ATTRIBUTES.register("effect_resistance", () -> new RangedAttribute(
                    "attribute.sololeveling.effect_resistance", 0.0, 0.0, 100.0).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> PHYSICAL_DAMAGE_REDUCTION =
            ATTRIBUTES.register("physical_damage_reduction", () -> new RangedAttribute(
                    "attribute.sololeveling.physical_damage_reduction", 0.0, 0.0, 1.0).setSyncable(true));
}