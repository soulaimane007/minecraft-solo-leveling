package com.soulaimane.sololeveling.event;

import com.soulaimane.sololeveling.data.ModDataMaps;
import com.soulaimane.sololeveling.dungeon.DungeonManager;
import com.soulaimane.sololeveling.dungeon.engine.DungeonEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

public class MobXpEvents {

    private static final int DEFAULT_MONSTER_XP = 5;
    private static final int DEFAULT_ANIMAL_XP  = 3;
    private static final int DEFAULT_OTHER_XP   = 1;

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        // getAttackingPlayer() returns Player (nullable), not ServerPlayer
        Player attacker = event.getAttackingPlayer();

        // Server-side only — the killer must be a server player
        if (!(attacker instanceof ServerPlayer killer)) return;

        LivingEntity killed = event.getEntity();

        // Never process player deaths
        if (killed instanceof ServerPlayer) return;

        // Cancel vanilla XP drop — we handle XP ourselves
        event.setCanceled(true);

        int xp = resolveXpValue(killed);
        if (xp <= 0) return;

        // Goes through the shared path: level-up message/title/sound + client sync
        PlayerDataEvents.giveSystemXp(killer, xp);
    }

    private static int resolveXpValue(LivingEntity entity) {
        // Dungeon mobs have their own (configurable) values; the boss pays out separately.
        int dungeonXp = DungeonManager.xpOverride(entity);
        if (dungeonXp >= 0) return dungeonXp;

        // Mobs of the dungeon engine (v0.5.0 dungeons) carry their XP in a tag, set by their MobSpec
        int engineXp = DungeonEngine.xpOverride(entity);
        if (engineXp >= 0) return engineXp;

        EntityType<?> type = entity.getType();

        Integer override = type.builtInRegistryHolder().getData(ModDataMaps.MOB_XP);
        if (override != null) return override;

        if (entity instanceof Monster) return DEFAULT_MONSTER_XP;
        if (entity instanceof Animal)  return DEFAULT_ANIMAL_XP;

        if (type.getCategory() == MobCategory.MONSTER)  return DEFAULT_MONSTER_XP;
        if (type.getCategory() == MobCategory.CREATURE) return DEFAULT_ANIMAL_XP;

        return DEFAULT_OTHER_XP;
    }
}
