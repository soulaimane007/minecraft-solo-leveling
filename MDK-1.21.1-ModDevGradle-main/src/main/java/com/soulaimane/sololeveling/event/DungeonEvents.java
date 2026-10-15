package com.soulaimane.sololeveling.event;

import com.soulaimane.sololeveling.Config;
import com.soulaimane.sololeveling.ModItems;
import com.soulaimane.sololeveling.SoloLeveling;
import com.soulaimane.sololeveling.dungeon.DungeonLayout;
import com.soulaimane.sololeveling.dungeon.DungeonManager;
import com.soulaimane.sololeveling.dungeon.engine.DungeonEngine;
import com.soulaimane.sololeveling.item.KeyRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Collection;
import java.util.Set;

/**
 * Game-bus hooks of the Tutorial Dungeon. All the logic lives in {@link DungeonManager};
 * this class only translates Minecraft events into calls to it.
 */
public class DungeonEvents {

    private static int tickErrors = 0;

    // ═════════════════════════════════════════════════════════════════
    // Tick
    // ═════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != DungeonManager.DIMENSION) return;

        try {
            DungeonManager.tick(level);
        } catch (RuntimeException ex) {
            // A bug in the dungeon must never take the whole server down
            if (tickErrors++ % 200 == 0) {
                SoloLeveling.LOGGER.error("Tutorial Dungeon tick failed ({} errors so far)", tickErrors, ex);
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
            DungeonManager.onPlayerDeath(player);
        } else {
            DungeonManager.onMobDeath(dead);
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;

        Set<String> tags = dead.getTags();
        Collection<ItemEntity> drops = event.getDrops();

        // ── Dungeon mobs: no rotten flesh, no equipment - only crystals ──
        if (level.dimension() == DungeonManager.DIMENSION && !tags.isEmpty()
                && (tags.contains(DungeonManager.TAG_ZOMBIE)
                || tags.contains(DungeonManager.TAG_MINION)
                || tags.contains(DungeonManager.TAG_BOSS))) {
            drops.clear();

            if (tags.contains(DungeonManager.TAG_ZOMBIE)) {
                int a = Config.SERVER.crystalsPerZombieMin.get();
                int b = Config.SERVER.crystalsPerZombieMax.get();
                int min = Math.min(a, b);
                int max = Math.max(a, b);
                int count = min + level.random.nextInt(max - min + 1);
                if (count > 0) {
                    drops.add(crystalDrop(level, dead, count));
                }
            }
            return;                      // the boss pays its crystals in DungeonManager
        }

        // ── Ordinary zombies killed by a player sometimes drop a crystal too ──
        double chance = Config.SERVER.overworldZombieCrystalChance.get();
        if (chance > 0.0
                && dead instanceof Zombie
                && !(dead instanceof ZombifiedPiglin)
                && !tags.contains(DungeonEngine.TAG_MOB)             // mobs of the dungeon engine drop what their MobSpec says
                && event.getSource().getEntity() instanceof ServerPlayer
                && level.random.nextDouble() < chance) {
            drops.add(crystalDrop(level, dead, 1));
        }
    }

    private static ItemEntity crystalDrop(ServerLevel level, LivingEntity at, int count) {
        return new ItemEntity(level, at.getX(), at.getY() + 0.5, at.getZ(),
                new ItemStack(ModItems.ZOMBIE_CRYSTAL.get(), count));
    }

    // ═════════════════════════════════════════════════════════════════
    // Entities appearing in the dungeon
    // ═════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Level level = event.getLevel();
        if (level.isClientSide || level.dimension() != DungeonManager.DIMENSION) return;

        Entity entity = event.getEntity();

        // Merchant trades and kills must never feed System XP through experience orbs
        if (entity instanceof ExperienceOrb) {
            event.setCanceled(true);
            return;
        }

        // Dungeon mobs saved in an old run that no longer exists
        if (event.loadedFromDisk() && level instanceof ServerLevel serverLevel
                && DungeonManager.isOrphan(serverLevel, entity)) {
            event.setCanceled(true);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // Protecting the room
    // ═════════════════════════════════════════════════════════════════

    private static boolean inDungeon(Object levelAccessor) {
        return levelAccessor instanceof Level level && level.dimension() == DungeonManager.DIMENSION;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (inDungeon(event.getLevel()) && !event.getPlayer().isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!inDungeon(event.getLevel())) return;
        if (event.getEntity() instanceof Player p && p.isCreative()) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (inDungeon(event.getLevel())) {
            event.getAffectedBlocks().clear();
        }
    }

    /** No player-versus-player damage inside the dungeon (it is a co-op place). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player victim
                && victim.level().dimension() == DungeonManager.DIMENSION
                && event.getSource().getEntity() instanceof Player) {
            event.setCanceled(true);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // Interactions: exit gate, bonfire, Rift Stone, and a few things that would break the room
    // ═════════════════════════════════════════════════════════════════

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.dimension() != DungeonManager.DIMENSION) return;

        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        boolean mainHand = event.getHand() == InteractionHand.MAIN_HAND;

        // Exit gate: the lodestone next to the spawn
        if (pos.equals(DungeonLayout.GATE) && state.is(Blocks.LODESTONE)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (mainHand && !level.isClientSide && player instanceof ServerPlayer sp) {
                DungeonManager.leave(sp, false);
            }
            return;
        }

        // The bonfire: rest
        if (pos.equals(DungeonLayout.BONFIRE) && state.is(Blocks.CAMPFIRE)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (mainHand && !level.isClientSide && player instanceof ServerPlayer sp) {
                DungeonManager.rest(sp);
            }
            return;
        }

        // The Rift Stone (crying obsidian on the dais behind the bonfire): a System Key: Replay starts the dungeon again.
        // Checking the block as well as the position means nothing happens once the arena is rebuilt.
        if (pos.equals(DungeonLayout.RIFT_STONE) && state.is(Blocks.CRYING_OBSIDIAN)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (mainHand && !level.isClientSide && player instanceof ServerPlayer sp) {
                DungeonManager.useRiftStone(sp);
            }
            return;
        }

        if (player.isCreative()) return;

        // Anvils chip and break when used: keep the blacksmith's stall intact
        if (state.is(BlockTags.ANVIL)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        // No fire, no fluids
        ItemStack held = event.getItemStack();
        if (held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE) || held.getItem() instanceof BucketItem) {
            event.setUseItem(TriState.FALSE);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().dimension() != DungeonManager.DIMENSION) return;
        if (event.getEntity().isCreative()) return;

        if (event.getItemStack().getItem() instanceof BucketItem) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // Keys: one per person
    // ═════════════════════════════════════════════════════════════════

    /**
     * You cannot pick up a second key of a kind you already carry (for example the key you dropped
     * when you died, after the System gave you a new one). It stays on the ground and disappears.
     */
    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        ItemStack stack = event.getItemEntity().getItem();
        if (!KeyRules.isKey(stack)) return;

        Player player = event.getPlayer();
        if (KeyRules.has(player, stack.getItem())) {
            event.setCanPickup(TriState.FALSE);
            if (player instanceof ServerPlayer sp && sp.tickCount % 40 == 0) {
                // the item name stays a translatable component: the client puts the right language in
                sp.displayClientMessage(Component.literal("§c[System] You can only carry one ")
                        .append(stack.getHoverName())
                        .append(Component.literal("§c.")), true);
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // Players
    // ═════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonManager.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonManager.onLogout(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isEndConquered()) {
            DungeonManager.onRespawn(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (event.getTo() == DungeonManager.DIMENSION) {
            DungeonManager.onEnterDungeon(player);
        } else if (event.getFrom() == DungeonManager.DIMENSION) {
            DungeonManager.onLeaveDungeon(player);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // Server lifecycle
    // ═════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DungeonManager.onServerStopped();
        tickErrors = 0;
    }
}
