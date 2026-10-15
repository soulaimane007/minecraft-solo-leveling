package com.soulaimane.sololeveling.event;

import com.soulaimane.sololeveling.SoloLeveling;
import com.soulaimane.sololeveling.dungeon.engine.DungeonEngine;
import com.soulaimane.sololeveling.dungeon.engine.DungeonRegistry;
import com.soulaimane.sololeveling.item.KeyRules;
import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Game-bus hooks of the dungeon engine (the dungeons you add with DungeonRegistry).
 * All the logic lives in {@link DungeonEngine}; this class only translates Minecraft events into calls to it.
 * (The Tutorial Dungeon has its own hooks in {@link DungeonEvents}.)
 */
public class GenericDungeonEvents {

    private static int tickErrors = 0;

    /** Is this world run by the dungeon engine? */
    private static boolean inEngineWorld(Object levelAccessor) {
        return levelAccessor instanceof Level level && DungeonRegistry.isEngineDimension(level.dimension());
    }

    // ═════════════════════════════════════════════════════════════════
    // Tick
    // ═════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        try {
            DungeonEngine.tick(event.getServer());
        } catch (RuntimeException ex) {
            // A bug in a dungeon must never take the whole server down
            if (tickErrors++ % 200 == 0) {
                SoloLeveling.LOGGER.error("Dungeon engine tick failed ({} errors so far)", tickErrors, ex);
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // Deaths and drops
    // ═════════════════════════════════════════════════════════════════

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide) return;

        if (dead instanceof ServerPlayer player) {
            DungeonEngine.onPlayerDeath(player);
        } else {
            DungeonEngine.onMobDeath(dead);
        }
    }

    /** LOW = after the Tutorial hook (NORMAL), so anything that one added to a dungeon mob is cleared as well. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDrops(LivingDropsEvent event) {
        DungeonEngine.onDrops(event);
    }

    // ═════════════════════════════════════════════════════════════════
    // Entities appearing in a dungeon world
    // ═════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Level level = event.getLevel();
        if (level.isClientSide || !DungeonRegistry.isEngineDimension(level.dimension())) return;

        Entity entity = event.getEntity();

        // Kills must not feed System XP through experience orbs: the XP comes from the MobSpec
        if (entity instanceof ExperienceOrb) {
            event.setCanceled(true);
            return;
        }

        // Dungeon mobs saved in a run that no longer exists (a restart): they must not come back
        if (event.loadedFromDisk() && DungeonEngine.isOrphan(entity)) {
            event.setCanceled(true);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // Protecting the rooms
    // ═════════════════════════════════════════════════════════════════

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (inEngineWorld(event.getLevel()) && !event.getPlayer().isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!inEngineWorld(event.getLevel())) return;
        if (event.getEntity() instanceof Player p && p.isCreative()) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (inEngineWorld(event.getLevel())) {
            event.getAffectedBlocks().clear();
        }
    }

    /** No player-versus-player damage inside a dungeon (it is a co-op place). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player victim
                && inEngineWorld(victim.level())
                && event.getSource().getEntity() instanceof Player attacker
                && attacker != victim) {
            event.setCanceled(true);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // Interactions
    // ═════════════════════════════════════════════════════════════════

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (!DungeonRegistry.isEngineDimension(level.dimension())) return;

        Player player = event.getEntity();
        BlockPos pos = event.getPos();

        // The exit stone of the level: leave (when the level allows it)
        if (!level.isClientSide && player instanceof ServerPlayer sp && DungeonEngine.isExit(sp, pos)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                DungeonEngine.leave(sp, false);
            }
            return;
        }

        if (player.isCreative()) return;

        // No fire, no fluids
        ItemStack held = event.getItemStack();
        if (held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE) || held.getItem() instanceof BucketItem) {
            event.setUseItem(TriState.FALSE);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Level level = event.getLevel();
        if (!DungeonRegistry.isEngineDimension(level.dimension())) return;

        ItemStack stack = event.getItemStack();

        // A System Key would open the Tutorial Dungeon from the middle of another dungeon
        if (KeyRules.isKey(stack)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            if (!level.isClientSide && event.getEntity() instanceof ServerPlayer sp) {
                SLUtil.actionBar(sp, "§c[System] This key does not work in here.");
            }
            return;
        }

        if (event.getEntity().isCreative()) return;

        if (stack.getItem() instanceof BucketItem) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // Players
    // ═════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonEngine.onLogout(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonEngine.onDimensionChanged(player, event.getTo());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // Server lifecycle
    // ═════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DungeonEngine.stopAll();
        tickErrors = 0;
    }
}
