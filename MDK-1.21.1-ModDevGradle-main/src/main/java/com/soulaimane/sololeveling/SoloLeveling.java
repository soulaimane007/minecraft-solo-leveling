package com.soulaimane.sololeveling;

import com.mojang.logging.LogUtils;
import com.soulaimane.sololeveling.command.SLCommands;
import com.soulaimane.sololeveling.data.ModDataMaps;
import com.soulaimane.sololeveling.dungeon.engine.ModDungeons;
import com.soulaimane.sololeveling.event.*;
import com.soulaimane.sololeveling.magic.ModMagics;
import com.soulaimane.sololeveling.registry.ModAttributes;
import com.soulaimane.sololeveling.skill.ModSkills;
import com.soulaimane.sololeveling.skill.SkillEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(SoloLeveling.MOD_ID)
public class SoloLeveling {
    public static final String MOD_ID = "sololeveling";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SoloLeveling(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        // DeferredRegisters
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modBus);
        ModAttachments.ATTACHMENT_TYPES.register(modBus);
        ModAttributes.ATTRIBUTES.register(modBus);

        // Skills, magics and dungeons are plain Java registries: they are filled right now, before anything reads them
        ModSkills.init();
        ModMagics.init();
        ModDungeons.init();

        // Mod bus listeners
        modBus.addListener(ModPayloadHandlers::register);
        modBus.addListener(ModDataMaps::register);

        // Game bus
        NeoForge.EVENT_BUS.register(SLCommands.class);
        NeoForge.EVENT_BUS.register(PlayerDataEvents.class);
        NeoForge.EVENT_BUS.register(AttributeEvents.class);
        NeoForge.EVENT_BUS.register(StaminaEvents.class);
        NeoForge.EVENT_BUS.register(WeaponDamageEvents.class);
        NeoForge.EVENT_BUS.register(EquipmentLoadEvents.class);
        NeoForge.EVENT_BUS.register(DexterityEvents.class);
        NeoForge.EVENT_BUS.register(PoiseEvents.class);
        NeoForge.EVENT_BUS.register(MobXpEvents.class);
        NeoForge.EVENT_BUS.register(ManaEvents.class);      // mana regeneration + sync
        NeoForge.EVENT_BUS.register(DungeonEvents.class);   // Tutorial Dungeon hooks
        NeoForge.EVENT_BUS.register(SkillEvents.class);     // skill passives, skill/magic saving and cooldown clean-up
        NeoForge.EVENT_BUS.register(GenericDungeonEvents.class); // dungeon engine hooks (the dungeons you add)

        LOGGER.info("Solo Leveling loaded");
    }
}