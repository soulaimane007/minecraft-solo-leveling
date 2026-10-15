package com.soulaimane.sololeveling.event;

import com.soulaimane.sololeveling.Config;
import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.EquipmentLoadState;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.data.SoftCap;
import com.soulaimane.sololeveling.registry.ModAttributes;
import com.soulaimane.sololeveling.util.SLResource;
import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class AttributeEvents {

    private static final ResourceLocation ID_BASE_HEALTH   = SLResource.id("base_health_penalty");
    private static final ResourceLocation ID_STR_KNOCKBACK = SLResource.id("strength_knockback");
    private static final ResourceLocation ID_STR_EQUIP     = SLResource.id("strength_equip");
    private static final ResourceLocation ID_AGI_SPEED     = SLResource.id("agility_speed");
    private static final ResourceLocation ID_AGI_ATKSPD    = SLResource.id("agility_attack_speed");
    private static final ResourceLocation ID_AGI_REGEN     = SLResource.id("agility_stamina_regen");
    private static final ResourceLocation ID_AGI_STEP      = SLResource.id("agility_step_height");
    private static final ResourceLocation ID_VIT_HEALTH    = SLResource.id("vitality_health");
    private static final ResourceLocation ID_INT_MANA      = SLResource.id("intelligence_mana");
    private static final ResourceLocation ID_INT_REG       = SLResource.id("intelligence_mana_regen");
    private static final ResourceLocation ID_SEN_RANGE_B   = SLResource.id("sense_block_range");
    private static final ResourceLocation ID_SEN_RANGE_E   = SLResource.id("sense_entity_range");
    private static final ResourceLocation ID_DUR_DR        = SLResource.id("durability_damage_reduction");
    private static final ResourceLocation ID_DUR_EQUIP     = SLResource.id("durability_equip");
    private static final ResourceLocation ID_DUR_STAMINA   = SLResource.id("durability_stamina");
    private static final ResourceLocation ID_LOAD_SPEED    = SLResource.id("equip_load_speed");

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) applyAllModifiers(p);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer p) applyAllModifiers(p);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) applyAllModifiers(p);
    }

    public static void applyAllModifiers(Player player) {
        PlayerSystemData d = player.getData(ModAttachments.PLAYER_SYSTEM_DATA);

        // Base values come from the config, so the .toml really controls them
        SLUtil.setBase(player, ModAttributes.MAX_MANA,     Config.SERVER.baseMana.get());
        SLUtil.setBase(player, ModAttributes.MANA_REGEN,   Config.SERVER.manaRegenBase.get());
        SLUtil.setBase(player, ModAttributes.MAX_STAMINA,  Config.SERVER.baseStamina.get());
        SLUtil.setBase(player, ModAttributes.STAMINA_REGEN, Config.SERVER.staminaRegenBase.get());
        SLUtil.setBase(player, ModAttributes.MAX_EQUIP_LOAD, Config.SERVER.equipLoadBase.get());

        // Base HP penalty
        addMod(player, Attributes.MAX_HEALTH, ID_BASE_HEALTH,
                -Config.SERVER.baseHealthPenalty.get(), AttributeModifier.Operation.ADD_VALUE);

        double effStr = SoftCap.effective(d.strength(), d.rank());
        double effAgi = SoftCap.effective(d.agility(), d.rank());
        double effVit = SoftCap.effective(d.vitality(), d.rank());
        double effInt = SoftCap.effective(d.intelligence(), d.rank());
        double effSen = SoftCap.effective(d.sense(), d.rank());
        double effDur = SoftCap.effective(d.durability(), d.rank());

        // STR: no ATTACK_DAMAGE modifier — handled in WeaponDamageEvents.
        // STR still adds knockback + equip load.
        addMod(player, Attributes.ATTACK_KNOCKBACK, ID_STR_KNOCKBACK,
                effStr * 0.02, AttributeModifier.Operation.ADD_VALUE);
        addMod(player, ModAttributes.MAX_EQUIP_LOAD, ID_STR_EQUIP,
                effStr * Config.SERVER.equipLoadPerStrength.get(), AttributeModifier.Operation.ADD_VALUE);

        // AGI
        addMod(player, Attributes.MOVEMENT_SPEED, ID_AGI_SPEED,
                effAgi * 0.002, AttributeModifier.Operation.ADD_VALUE);
        addMod(player, Attributes.ATTACK_SPEED, ID_AGI_ATKSPD,
                effAgi * 0.005, AttributeModifier.Operation.ADD_VALUE);
        addMod(player, ModAttributes.STAMINA_REGEN, ID_AGI_REGEN,
                effAgi * 0.05, AttributeModifier.Operation.ADD_VALUE);
        if (effAgi > 30) {
            addMod(player, Attributes.STEP_HEIGHT, ID_AGI_STEP,
                    Math.min(0.5, (effAgi - 30) * 0.01), AttributeModifier.Operation.ADD_VALUE);
        } else {
            removeMod(player, Attributes.STEP_HEIGHT, ID_AGI_STEP);
        }

        // VIT — 5 points = 1 heart
        addMod(player, Attributes.MAX_HEALTH, ID_VIT_HEALTH,
                effVit * Config.SERVER.healthPerVitality.get(), AttributeModifier.Operation.ADD_VALUE);

        // INT
        addMod(player, ModAttributes.MAX_MANA, ID_INT_MANA,
                effInt * Config.SERVER.manaPerIntelligence.get(), AttributeModifier.Operation.ADD_VALUE);
        addMod(player, ModAttributes.MANA_REGEN, ID_INT_REG,
                effInt * Config.SERVER.manaRegenPerIntelligence.get(), AttributeModifier.Operation.ADD_VALUE);

        // SEN
        addMod(player, Attributes.BLOCK_INTERACTION_RANGE, ID_SEN_RANGE_B,
                effSen * 0.05, AttributeModifier.Operation.ADD_VALUE);
        addMod(player, Attributes.ENTITY_INTERACTION_RANGE, ID_SEN_RANGE_E,
                effSen * 0.05, AttributeModifier.Operation.ADD_VALUE);

        // DUR
        double dr = Math.min(effDur * 0.01, Config.SERVER.physicalReductionSoftCap.get());
        addMod(player, ModAttributes.PHYSICAL_DAMAGE_REDUCTION, ID_DUR_DR,
                dr, AttributeModifier.Operation.ADD_VALUE);
        addMod(player, ModAttributes.MAX_EQUIP_LOAD, ID_DUR_EQUIP,
                effDur * Config.SERVER.equipLoadPerDurability.get(), AttributeModifier.Operation.ADD_VALUE);
        addMod(player, ModAttributes.MAX_STAMINA, ID_DUR_STAMINA,
                effDur * 0.25, AttributeModifier.Operation.ADD_VALUE);

        // Heavy gear slows you down (depends on max load, so it is refreshed here too)
        applyLoadSpeed(player);

        // Max health may have dropped (base penalty) — never stay above it
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * Movement-speed penalty from equipment load:
     * Light/Medium 0%, Heavy -10%, Overweight -40% (see EquipmentLoadState).
     */
    public static void applyLoadSpeed(Player player) {
        PlayerSystemData d = player.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        AttributeInstance maxLoadAttr = player.getAttribute(ModAttributes.MAX_EQUIP_LOAD);
        double maxLoad = maxLoadAttr != null ? maxLoadAttr.getValue() : 30.0;
        double ratio = maxLoad > 0 ? d.currentEquipLoad() / maxLoad : 0;
        double mult = EquipmentLoadState.fromRatio(ratio).moveSpeedMultiplier;

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;

        AttributeModifier existing = speed.getModifier(ID_LOAD_SPEED);
        if (Math.abs(mult) < 1e-6) {
            if (existing != null) speed.removeModifier(ID_LOAD_SPEED);
            return;
        }
        if (existing != null && Math.abs(existing.amount() - mult) < 1e-9) return;

        speed.removeModifier(ID_LOAD_SPEED);
        speed.addPermanentModifier(new AttributeModifier(
                ID_LOAD_SPEED, mult, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void addMod(Player player, Holder<Attribute> attr, ResourceLocation id,
                               double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (Math.abs(amount) < 1e-6) return;
        inst.addPermanentModifier(new AttributeModifier(id, amount, op));
    }

    private static void removeMod(Player player, Holder<Attribute> attr, ResourceLocation id) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst != null) inst.removeModifier(id);
    }
}