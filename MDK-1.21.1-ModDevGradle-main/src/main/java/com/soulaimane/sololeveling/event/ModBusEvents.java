package com.soulaimane.sololeveling.event;

import com.soulaimane.sololeveling.Config;
import com.soulaimane.sololeveling.SoloLeveling;
import com.soulaimane.sololeveling.registry.ModAttributes;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

@EventBusSubscriber(modid = SoloLeveling.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModBusEvents {

    @SubscribeEvent
    public static void attachAttributesToPlayer(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, ModAttributes.MAX_MANA);
        event.add(EntityType.PLAYER, ModAttributes.MANA_REGEN);
        event.add(EntityType.PLAYER, ModAttributes.MAX_STAMINA);
        event.add(EntityType.PLAYER, ModAttributes.STAMINA_REGEN);
        event.add(EntityType.PLAYER, ModAttributes.MAX_EQUIP_LOAD);
        event.add(EntityType.PLAYER, ModAttributes.POISE);
        event.add(EntityType.PLAYER, ModAttributes.EFFECT_RESISTANCE);
        event.add(EntityType.PLAYER, ModAttributes.PHYSICAL_DAMAGE_REDUCTION);
    }

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            SoloLeveling.LOGGER.debug("[SL] Server config loaded.");
        }
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            SoloLeveling.LOGGER.debug("[SL] Server config reloaded.");
        }
    }
}