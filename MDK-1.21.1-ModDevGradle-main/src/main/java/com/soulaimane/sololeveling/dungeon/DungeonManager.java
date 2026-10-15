package com.soulaimane.sololeveling.dungeon;

import com.soulaimane.sololeveling.Config;
import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.ModItems;
import com.soulaimane.sololeveling.SoloLeveling;
import com.soulaimane.sololeveling.data.PlayerDungeonData;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.event.ManaEvents;
import com.soulaimane.sololeveling.event.PlayerDataEvents;
import com.soulaimane.sololeveling.event.StaminaEvents;
import com.soulaimane.sololeveling.item.KeyRules;
import com.soulaimane.sololeveling.util.SLResource;
import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The brain of the Tutorial Dungeon.
 *
 * <pre>
 *   IDLE ──(a player is inside)──▶ FIGHT ──(kills ≥ target)──▶ BOSS_PENDING ──▶ BOSS
 *                                                                                 │
 *                       CLEARED (bonfire camp)  ◀──── CLEARING (4 s) ◀── boss dies ┘
 * </pre>
 *
 *  FIGHT        zombies keep spawning (never more than "maxAlive" at once) until enough were slain
 *  BOSS_PENDING 5 s of dread, the remaining zombies vanish
 *  BOSS         the Zombie King fights (see {@link BossController})
 *  CLEARING     rewards were paid, the room is about to change
 *  CLEARED      the arena became the peaceful Bonfire camp with two merchants. It stays like that until a
 *               player uses a System Key: Replay (sold by the Blacksmith) on the Rift Stone: the dungeon
 *               is reset and the next run starts (back to IDLE, see {@link #startNewRun})
 *
 * The phase and the kill counter live in {@link DungeonSavedData}, so they survive a restart.
 * Everything is driven by {@link #tick(ServerLevel)}, which is only called for the dungeon dimension.
 */
public final class DungeonManager {
    private DungeonManager() {}

    // ═════════════════════════════════════════════════════════════════
    // Identity
    // ═════════════════════════════════════════════════════════════════

    public static final ResourceKey<Level> DIMENSION =
            ResourceKey.create(Registries.DIMENSION, SLResource.id("tutorial_dungeon"));

    public static final String TAG_ZOMBIE = "sl_dungeon_zombie";
    public static final String TAG_MINION = "sl_dungeon_minion";
    public static final String TAG_BOSS   = "sl_dungeon_boss";
    public static final String TAG_NPC    = "sl_dungeon_npc";

    /**
     * Every merchant carries a tag "sl_shop_<version>_rk<price>": the version of the price list (bump it when
     * DungeonTrades changes) and the price of the Replay key. A merchant whose tag is not the current one gets
     * its offers rebuilt, so a new mod version or a new key price in the config shows up by itself.
     */
    private static final String TAG_SHOP_PREFIX  = "sl_shop_";
    private static final String TAG_SHOP_VERSION = TAG_SHOP_PREFIX + "v3";

    // ═════════════════════════════════════════════════════════════════
    // Timings (in ticks) and small constants
    // ═════════════════════════════════════════════════════════════════

    private static final int START_DELAY          = 80;    // before the first zombie appears
    private static final int BOSS_INTRO           = 100;   // between "all zombies slain" and the king
    private static final int CLEARING_DELAY       = 80;    // between the king's death and the camp
    private static final int EMPTY_GRACE          = 100;   // nobody left alive in the fight -> the boss resets
    private static final int BOSS_MISSING_GRACE   = 40;    // boss entity not found for this long -> reset
    private static final int ARRIVAL_SETTLE       = 60;    // wait for chunks/entities before checking the NPCs
    private static final int REST_COOLDOWN        = 100;
    private static final double BONFIRE_AURA_SQR  = 36.0;  // 6 blocks
    private static final double RIFT_HINT_SQR     = 16.0;  // 4 blocks: the Rift Stone's reminder

    // ═════════════════════════════════════════════════════════════════
    // Boss bars (one set for the whole dungeon)
    // ═════════════════════════════════════════════════════════════════

    private static final ServerBossEvent PROGRESS_BAR = new ServerBossEvent(
            Component.literal("Zombies slain"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_20);

    private static final ServerBossEvent BOSS_BAR = new ServerBossEvent(
            Component.literal("§4§lZombie King"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    // ═════════════════════════════════════════════════════════════════
    // Transient (not saved) state
    // ═════════════════════════════════════════════════════════════════

    private static int  spawnCooldown = 40;
    private static int  emptyTicks = 0;
    private static int  bossMissingTicks = 0;
    private static long lastArrival = -1000;

    private static final Map<UUID, Long> LAST_REST = new HashMap<>();
    private static final Set<UUID> DIED_IN_DUNGEON = new HashSet<>();

    // ═════════════════════════════════════════════════════════════════
    // Entering and leaving
    // ═════════════════════════════════════════════════════════════════

    /** Right-click with the System Key: opens the gate, or brings you back when you are inside. */
    public static void useKey(ServerPlayer player, ItemStack keyStack) {
        if (player.level().dimension() == DIMENSION) {
            leave(player, false);
        } else {
            enter(player, keyStack);
        }
    }

    /**
     * Sends the player to the dungeon.
     * @param keyStack the key used (consumed if the config says so), or null (commands)
     */
    public static boolean enter(ServerPlayer player, ItemStack keyStack) {
        MinecraftServer server = player.getServer();
        if (server == null || !player.isAlive()) return false;

        ServerLevel dungeon = server.getLevel(DIMENSION);
        if (dungeon == null) {
            SLUtil.chat(player, "§c[System] The Tutorial Dungeon world is missing. "
                    + "Check that data/sololeveling/dimension/tutorial_dungeon.json was loaded (see the game log).");
            return false;
        }

        // Already inside: just walk back to the gate
        if (player.level().dimension() == DIMENSION) {
            player.connection.teleport(DungeonLayout.SPAWN.x, DungeonLayout.SPAWN.y, DungeonLayout.SPAWN.z,
                    DungeonLayout.SPAWN_YAW, 0.0f);
            return true;
        }

        DungeonSavedData data = DungeonSavedData.get(dungeon);

        if (dungeon.getDifficulty() == Difficulty.PEACEFUL && data.phase != DungeonPhase.CLEARED) {
            SLUtil.chat(player, "§c[System] Zombies cannot exist on Peaceful difficulty. "
                    + "Raise the difficulty to open the gate.");
            return false;
        }

        ensureBuilt(dungeon, data);

        // Remember where the player came from so the key / exit gate can bring them back
        ServerLevel from = player.serverLevel();
        PlayerDungeonData pd = player.getData(ModAttachments.PLAYER_DUNGEON_DATA);
        player.setData(ModAttachments.PLAYER_DUNGEON_DATA, pd.withReturn(
                from.dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));

        if (keyStack != null && !keyStack.isEmpty() && !player.isCreative()
                && Config.SERVER.dungeonConsumeKey.get() && data.phase != DungeonPhase.CLEARED) {
            keyStack.shrink(1);
        }

        from.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
                40, 0.4, 0.8, 0.4, 0.3);
        SLUtil.playSound(player, "minecraft:entity.enderman.teleport", SoundSource.PLAYERS, 1.0f, 0.6f);

        Entity moved = player.changeDimension(new DimensionTransition(
                dungeon, DungeonLayout.SPAWN, Vec3.ZERO, DungeonLayout.SPAWN_YAW, 0.0f,
                DimensionTransition.DO_NOTHING));
        return moved != null;
    }

    /**
     * Brings the player back to where they opened the gate (or to the world spawn).
     * @param force true = ignore the "sealed during the boss fight" rule (commands)
     */
    public static boolean leave(ServerPlayer player, boolean force) {
        MinecraftServer server = player.getServer();
        if (server == null || !player.isAlive()) return false;
        if (player.level().dimension() != DIMENSION) return false;

        if (!force && !player.isCreative() && Config.SERVER.dungeonLockDuringBoss.get()) {
            ServerLevel dungeon = server.getLevel(DIMENSION);
            if (dungeon != null) {
                DungeonPhase phase = DungeonSavedData.get(dungeon).phase;
                if (phase == DungeonPhase.BOSS || phase == DungeonPhase.BOSS_PENDING) {
                    SLUtil.actionBar(player, "§c[System] The gate is sealed while the Zombie King lives.");
                    return false;
                }
            }
        }

        PlayerDungeonData pd = player.getData(ModAttachments.PLAYER_DUNGEON_DATA);

        ServerLevel dest = null;
        Vec3 pos = Vec3.ZERO;
        float yaw = 0.0f;
        float pitch = 0.0f;

        if (pd.hasReturn()) {
            ResourceLocation rl = ResourceLocation.tryParse(pd.returnDimension());
            if (rl != null) {
                ServerLevel candidate = server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
                if (candidate != null && candidate.dimension() != DIMENSION) {
                    dest = candidate;
                    pos = new Vec3(pd.returnX(), pd.returnY(), pd.returnZ());
                    yaw = pd.returnYaw();
                    pitch = pd.returnPitch();
                }
            }
        }
        if (dest == null) {
            dest = server.overworld();
            BlockPos spawn = dest.getSharedSpawnPos();
            int y = dest.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
            pos = new Vec3(spawn.getX() + 0.5, y, spawn.getZ() + 0.5);
        }

        ServerLevel here = player.serverLevel();
        here.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
                40, 0.4, 0.8, 0.4, 0.3);
        SLUtil.playSound(player, "minecraft:entity.enderman.teleport", SoundSource.PLAYERS, 1.0f, 1.2f);

        player.setData(ModAttachments.PLAYER_DUNGEON_DATA, pd.withoutReturn());
        Entity moved = player.changeDimension(new DimensionTransition(
                dest, pos, Vec3.ZERO, yaw, pitch, DimensionTransition.DO_NOTHING));
        return moved != null;
    }

    public static boolean isInDungeon(Entity entity) {
        return entity.level().dimension() == DIMENSION;
    }

    // ═════════════════════════════════════════════════════════════════
    // Player events (called from DungeonEvents)
    // ═════════════════════════════════════════════════════════════════

    /** First join with this version of the mod: starting dexterity + starter kit. */
    public static void onLogin(ServerPlayer p) {
        // Worlds from the first version may hold stacks of keys: one per person from now on
        KeyRules.cleanUp(p);

        PlayerDungeonData dd = p.getData(ModAttachments.PLAYER_DUNGEON_DATA);
        if (dd.startingKeyGiven()) return;

        // 1) The "startingDexterity" config option (only for characters that never gained anything)
        int dex = Config.SERVER.startingDexterity.get();
        PlayerSystemData sys = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        if (sys.level() == 0 && sys.xp() == 0 && sys.dexterity() == 1 && dex != 1) {
            p.setData(ModAttachments.PLAYER_SYSTEM_DATA, sys.withStat("dexterity", dex));
            PlayerDataEvents.syncToClient(p);
        }

        // 2) Starter kit: one Basic key (never a second one) + the Health Potions
        if (Config.SERVER.dungeonGiveStartingKey.get()) {
            KeyRules.giveIfMissing(p, ModItems.SYSTEM_KEY.get());
            topUpPotions(p);
            SLUtil.chat(p, "§6[System] §7You received the §eSystem Key: Basic §7and §aHealth Potions§7. "
                    + "Right-click the key to open the Tutorial Dungeon.");
        }

        p.setData(ModAttachments.PLAYER_DUNGEON_DATA,
                p.getData(ModAttachments.PLAYER_DUNGEON_DATA).withStartingKeyGiven(true));
    }

    public static void onLogout(ServerPlayer p) {
        PROGRESS_BAR.removePlayer(p);
        BOSS_BAR.removePlayer(p);
        LAST_REST.remove(p.getUUID());
    }

    /** A player died: if it happened inside the dungeon, the System returns a key on respawn. */
    public static void onPlayerDeath(ServerPlayer p) {
        if (p.level().dimension() == DIMENSION) {
            DIED_IN_DUNGEON.add(p.getUUID());
        }
    }

    public static void onRespawn(ServerPlayer p) {
        if (!DIED_IN_DUNGEON.remove(p.getUUID())) return;
        if (!Config.SERVER.dungeonGiveStartingKey.get()) return;

        // The key you died with dropped like any item. You only get a new one if you carry none:
        // a player never holds two Basic keys.
        if (KeyRules.giveIfMissing(p, ModItems.SYSTEM_KEY.get())) {
            SLUtil.chat(p, "§6[System] §7You fell in the dungeon. A new §eSystem Key: Basic §7was returned to you.");
        }
    }

    /** The player arrived in the dungeon dimension (by key, gate, command, anything). */
    public static void onEnterDungeon(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        lastArrival = level.getGameTime();

        KeyRules.cleanUp(p);

        DungeonSavedData data = DungeonSavedData.get(level);
        int target = Config.SERVER.dungeonKillTarget.get();

        // A new attempt at the king (everybody who fought him is dead or gone): he starts over at
        // full health. Without this, a quick return after dying met the king with his old damage.
        boolean regenerated = false;
        if (data.phase == DungeonPhase.BOSS && !anotherPlayerAlive(level, p)) {
            resetBossFight(level, data);
            regenerated = true;
        }

        // Fight phases only (the camp is safe and has its own shop): the System tops you up to the
        // configured number of Health Potions, 3 by default. It never gives more than that.
        if (data.phase != DungeonPhase.CLEARED && data.phase != DungeonPhase.CLEARING) {
            int given = topUpPotions(p);
            if (given > 0) {
                SLUtil.chat(p, "§6[System] §7The System supplies you with §a" + given + " Health Potion"
                        + (given > 1 ? "s" : "") + "§7. You carry "
                        + countItem(p, ModItems.HEALTH_POTION.get()) + ".");
            }
        }

        switch (data.phase) {
            case CLEARED, CLEARING -> {
                SLUtil.title(p, Component.literal("§6§lBONFIRE CAMP"),
                        Component.literal("§7A place of peace. Rest, trade and prepare."), 10, 60, 20);
                SLUtil.chat(p, "§6[System] §7Right-click the §ebonfire §7to rest. The §5Alchemist §7and the "
                        + "§6Blacksmith §7trade supplies for crystals.");
                SLUtil.chat(p, replayHint());
            }
            case BOSS, BOSS_PENDING -> {
                SLUtil.title(p, Component.literal("§4§lTHE ZOMBIE KING"),
                        Component.literal(regenerated
                                ? "§7He has regenerated. Prepare yourself."
                                : "§7Defeat him to claim the dungeon"), 10, 60, 20);
            }
            default -> {
                SLUtil.title(p, Component.literal("§c§lTUTORIAL DUNGEON"),
                        Component.literal("§7Slay §f" + target + " §7zombies to awaken the Zombie King"), 10, 60, 20);
                SLUtil.chat(p, "§6[System] §7Zombies drop §bZombie Crystals§7. Keep some: they are the currency "
                        + "of the Bonfire merchants.");
            }
        }
        SLUtil.chat(p, "§8Leave at any time (except during the boss fight): right-click the lodestone "
                + "next to the gate, or use your System Key.");
    }

    public static void onLeaveDungeon(ServerPlayer p) {
        PROGRESS_BAR.removePlayer(p);
        BOSS_BAR.removePlayer(p);
    }

    /** Is somebody other than {@code except} alive (and not a spectator) in the dungeon? */
    private static boolean anotherPlayerAlive(ServerLevel level, ServerPlayer except) {
        for (ServerPlayer other : level.players()) {
            if (other != except && other.isAlive() && !other.isSpectator()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Brings the player up to the configured number of Health Potions (3 by default).
     * Potions you already carry count, so it never goes above that number.
     * @return how many potions were given
     */
    private static int topUpPotions(ServerPlayer p) {
        int want = Config.SERVER.dungeonEntryPotions.get();
        int missing = want - countItem(p, ModItems.HEALTH_POTION.get());
        if (missing <= 0) return 0;

        ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(ModItems.HEALTH_POTION.get(), missing));
        return missing;
    }

    private static int countItem(ServerPlayer p, Item item) {
        Inventory inv = p.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static void onServerStopped() {
        PROGRESS_BAR.removeAllPlayers();
        BOSS_BAR.removeAllPlayers();
        LAST_REST.clear();
        DIED_IN_DUNGEON.clear();
        BossController.reset();
        spawnCooldown = 40;
        emptyTicks = 0;
        bossMissingTicks = 0;
        lastArrival = -1000;
    }

    // ═════════════════════════════════════════════════════════════════
    // Tick
    // ═════════════════════════════════════════════════════════════════

    /** Called once per server tick for the dungeon dimension only. */
    public static void tick(ServerLevel level) {
        DungeonSavedData data = DungeonSavedData.get(level);
        List<ServerPlayer> all = level.players();
        List<ServerPlayer> active = activePlayers(all);

        if (data.phase == DungeonPhase.CLEARING) {
            tickClearing(level, data);            // runs even when nobody watches
        } else if (active.isEmpty()) {
            tickEmpty(level, data);
        } else {
            emptyTicks = 0;
            switch (data.phase) {
                case IDLE         -> startRun(level, data);
                case FIGHT        -> tickFight(level, data, active);
                case BOSS_PENDING -> tickBossPending(level, data);
                case BOSS         -> tickBoss(level, data, active);
                case CLEARED      -> tickCleared(level, data, active);
                default           -> { }
            }
        }
        updateBars(data, all);
    }

    private static List<ServerPlayer> activePlayers(List<ServerPlayer> all) {
        if (all.isEmpty()) return List.of();
        List<ServerPlayer> out = new ArrayList<>(all.size());
        for (ServerPlayer p : all) {
            if (p.isAlive() && !p.isSpectator()) out.add(p);
        }
        return out;
    }

    // ── nobody alive inside ──────────────────────────────────────────

    private static void tickEmpty(ServerLevel level, DungeonSavedData data) {
        if (data.phase == DungeonPhase.BOSS) {
            if (++emptyTicks >= EMPTY_GRACE) {
                emptyTicks = 0;
                resetBossFight(level, data);
            }
        } else {
            emptyTicks = 0;
        }
    }

    /** The party is gone: the king vanishes and will return at full health on the next visit. */
    private static void resetBossFight(ServerLevel level, DungeonSavedData data) {
        discardTagged(level, true, true, true, false);
        data.bossUuid = null;
        data.timer = BOSS_INTRO;
        data.setPhase(DungeonPhase.BOSS_PENDING);
        BossController.reset();
        bossMissingTicks = 0;
    }

    // ── IDLE -> FIGHT ────────────────────────────────────────────────

    private static void startRun(ServerLevel level, DungeonSavedData data) {
        data.kills = 0;
        data.bossUuid = null;
        data.timer = START_DELAY;
        data.setPhase(DungeonPhase.FIGHT);
        spawnCooldown = 20;
        BossController.reset();
    }

    // ── FIGHT ────────────────────────────────────────────────────────

    private static void tickFight(ServerLevel level, DungeonSavedData data, List<ServerPlayer> active) {
        int target = Config.SERVER.dungeonKillTarget.get();

        if (data.kills >= target) {
            beginBossIntro(level, data);
            return;
        }
        if (data.timer > 0) {
            data.timer--;
            return;
        }

        if (level.getGameTime() % 40 == 0) {
            retarget(level, active);
        }

        if (--spawnCooldown <= 0) {
            int alive = countAlive(level, TAG_ZOMBIE);
            int maxAlive = Math.min(40,
                    Config.SERVER.dungeonMaxAliveZombies.get() + 2 * Math.max(0, active.size() - 1));
            // never spawn more zombies than the kill target still needs
            if (alive < maxAlive && data.kills + alive < target) {
                spawnZombie(level, active);
                spawnCooldown = 25 + level.random.nextInt(35);
            } else {
                spawnCooldown = 10;
            }
        }
    }

    private static void spawnZombie(ServerLevel level, List<ServerPlayer> active) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double radius = 9.0 + level.random.nextDouble() * 8.0;
            double x = DungeonLayout.CX + 0.5 + Math.cos(angle) * radius;
            double z = DungeonLayout.CZ + 0.5 + Math.sin(angle) * radius;

            if (!DungeonLayout.isInside(x, z, 2.0)) continue;
            if (tooCloseToPlayer(active, x, z, 7.0)) continue;

            Zombie zombie = createZombie(level, x, DungeonLayout.FLOOR_Y + 1.0, z, TAG_ZOMBIE, 1.0);
            if (zombie == null) return;
            if (!level.noCollision(zombie)) continue;      // inside a pillar or a lantern

            level.addFreshEntity(zombie);
            level.sendParticles(ParticleTypes.SOUL, x, DungeonLayout.FLOOR_Y + 1.2, z, 16, 0.3, 0.6, 0.3, 0.02);
            SLUtil.playSound(level, x, DungeonLayout.FLOOR_Y + 1.0, z,
                    "minecraft:entity.zombie.ambient", SoundSource.HOSTILE, 0.8f, 0.6f);
            return;
        }
    }

    private static boolean tooCloseToPlayer(List<ServerPlayer> players, double x, double z, double minDist) {
        double min2 = minDist * minDist;
        for (ServerPlayer p : players) {
            double dx = p.getX() - x;
            double dz = p.getZ() - z;
            if (dx * dx + dz * dz < min2) return true;
        }
        return false;
    }

    private static void beginBossIntro(ServerLevel level, DungeonSavedData data) {
        discardTagged(level, true, false, false, false);
        data.timer = BOSS_INTRO;
        data.setPhase(DungeonPhase.BOSS_PENDING);

        for (ServerPlayer p : level.players()) {
            SLUtil.title(p, Component.literal("§4§lSOMETHING AWAKENS..."),
                    Component.literal("§7The ground trembles. Prepare yourself."), 10, 70, 20);
        }
        SLUtil.playSound(level, DungeonLayout.BOSS_SPAWN.x, DungeonLayout.BOSS_SPAWN.y, DungeonLayout.BOSS_SPAWN.z,
                "minecraft:entity.ender_dragon.growl", SoundSource.HOSTILE, 1.5f, 0.6f);
    }

    // ── BOSS_PENDING -> BOSS ─────────────────────────────────────────

    private static void tickBossPending(ServerLevel level, DungeonSavedData data) {
        if (data.timer > 0) {
            data.timer--;
            if (data.timer % 10 == 0) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        DungeonLayout.BOSS_SPAWN.x, DungeonLayout.BOSS_SPAWN.y + 0.1, DungeonLayout.BOSS_SPAWN.z,
                        24, 2.5, 0.1, 2.5, 0.03);
            }
            if (data.timer == 50) {
                SLUtil.playSound(level, DungeonLayout.BOSS_SPAWN.x, DungeonLayout.BOSS_SPAWN.y, DungeonLayout.BOSS_SPAWN.z,
                        "minecraft:entity.generic.explode", SoundSource.HOSTILE, 1.0f, 0.4f);
            }
            return;
        }
        spawnBoss(level, data);
    }

    private static void spawnBoss(ServerLevel level, DungeonSavedData data) {
        Zombie boss = createBoss(level);
        if (boss == null) {
            SoloLeveling.LOGGER.error("Could not create the Zombie King entity");
            data.timer = BOSS_INTRO;       // try again in a few seconds
            return;
        }
        level.addFreshEntity(boss);

        data.bossUuid = boss.getUUID();
        data.setPhase(DungeonPhase.BOSS);
        bossMissingTicks = 0;
        BossController.reset();

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(DungeonLayout.BOSS_SPAWN.x, DungeonLayout.BOSS_SPAWN.y, DungeonLayout.BOSS_SPAWN.z, 0.0f, 0.0f);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        SLUtil.playSound(level, DungeonLayout.BOSS_SPAWN.x, DungeonLayout.BOSS_SPAWN.y, DungeonLayout.BOSS_SPAWN.z,
                "minecraft:entity.zombie_villager.converted", SoundSource.HOSTILE, 2.0f, 0.5f);

        for (ServerPlayer p : level.players()) {
            SLUtil.title(p, Component.literal("§4§lTHE ZOMBIE KING"),
                    Component.literal("§7Defeat him to claim the dungeon"), 10, 60, 20);
        }
    }

    // ── BOSS ─────────────────────────────────────────────────────────

    private static void tickBoss(ServerLevel level, DungeonSavedData data, List<ServerPlayer> active) {
        Entity found = data.bossUuid == null ? null : level.getEntity(data.bossUuid);

        if (found instanceof Zombie boss && boss.isAlive()) {
            bossMissingTicks = 0;
            BossController.tick(level, boss, active);
            BOSS_BAR.setProgress(Mth.clamp(boss.getHealth() / Math.max(1.0f, boss.getMaxHealth()), 0.0f, 1.0f));
            if (level.getGameTime() % 40 == 0) {
                retarget(level, active);
            }
            return;
        }

        // The king is gone without a death event (command, void, unloaded...): start the intro again
        if (++bossMissingTicks >= BOSS_MISSING_GRACE) {
            SoloLeveling.LOGGER.warn("Zombie King entity not found - restarting the boss intro");
            resetBossFight(level, data);
        }
    }

    // ── CLEARING -> CLEARED ──────────────────────────────────────────

    private static void tickClearing(ServerLevel level, DungeonSavedData data) {
        if (data.timer > 0) {
            data.timer--;
            if (data.timer % 20 == 0) {
                level.sendParticles(ParticleTypes.END_ROD,
                        DungeonLayout.BOSS_SPAWN.x, DungeonLayout.BOSS_SPAWN.y + 0.5, DungeonLayout.BOSS_SPAWN.z,
                        30, 4.0, 1.0, 4.0, 0.02);
            }
            return;
        }

        DungeonArenaBuilder.buildPeaceHub(level);
        data.hubBuilt = true;
        data.setPhase(DungeonPhase.CLEARED);

        SLUtil.playSound(level, DungeonLayout.BOSS_SPAWN.x, DungeonLayout.BOSS_SPAWN.y, DungeonLayout.BOSS_SPAWN.z,
                "minecraft:block.beacon.activate", SoundSource.AMBIENT, 1.5f, 1.0f);
        for (ServerPlayer p : level.players()) {
            SLUtil.title(p, Component.literal("§6§lTHE BONFIRE IS LIT"),
                    Component.literal("§7Peace has returned to the dungeon"), 10, 70, 30);
            SLUtil.chat(p, "§6[System] §7Right-click the §ebonfire §7to rest. The merchants accept §bZombie Crystals§7.");
            SLUtil.chat(p, replayHint());
        }

        if (level.getGameTime() - lastArrival >= ARRIVAL_SETTLE) {
            ensureNpcs(level);
        }
    }

    // ── CLEARED (the camp) ───────────────────────────────────────────

    private static void tickCleared(ServerLevel level, DungeonSavedData data, List<ServerPlayer> active) {
        long now = level.getGameTime();

        if (now % 100 == 0 && now - lastArrival >= ARRIVAL_SETTLE) {
            ensureNpcs(level);
        }

        if (now % 10 == 0) {
            riftAmbience(level, active, now);
        }

        if (now % 20 == 0) {
            // leftovers of a previous run never survive in the camp
            discardTagged(level, true, true, true, false);

            // the bonfire's warmth
            double bx = DungeonLayout.BONFIRE.getX() + 0.5;
            double bz = DungeonLayout.BONFIRE.getZ() + 0.5;
            for (ServerPlayer p : active) {
                double dx = p.getX() - bx;
                double dz = p.getZ() - bz;
                if (dx * dx + dz * dz <= BONFIRE_AURA_SQR) {
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false, true));
                }
            }
        }
    }

    /**
     * Called every half second in the camp: purple sparks over the Rift Stone and a reminder for anybody who
     * walks up to it. It also puts the dais back if it is missing: a camp that was built by an older
     * version of the mod (v0.3.0 or earlier) has no stone yet.
     */
    private static void riftAmbience(ServerLevel level, List<ServerPlayer> active, long now) {
        BlockPos stone = DungeonLayout.RIFT_STONE;
        if (!level.hasChunkAt(stone)) return;

        if (!level.getBlockState(stone).is(Blocks.CRYING_OBSIDIAN)) {
            DungeonArenaBuilder.buildRiftAltar(level);
        }

        double x = stone.getX() + 0.5;
        double y = stone.getY() + 1.0;
        double z = stone.getZ() + 0.5;
        level.sendParticles(ParticleTypes.PORTAL, x, y, z, 8, 0.3, 0.4, 0.3, 0.3);

        if (now % 40 == 0) {
            for (ServerPlayer p : active) {
                if (p.distanceToSqr(x, y, z) <= RIFT_HINT_SQR) {
                    SLUtil.actionBar(p, "§5Rift Stone §7- right-click it with a §eSystem Key: Replay");
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // Mob creation
    // ═════════════════════════════════════════════════════════════════

    /**
     * A dungeon zombie (not yet added to the level).
     * @param power 1.0 = normal zombie, 0.7 = weaker minion (scales health and damage)
     */
    public static Zombie createZombie(ServerLevel level, double x, double y, double z, String tag, double power) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) return null;

        zombie.moveTo(x, y, z, level.random.nextFloat() * 360.0f, 0.0f);
        zombie.addTag(tag);
        zombie.setPersistenceRequired();
        zombie.setCanPickUpLoot(false);

        double hp = Math.max(1.0, Config.SERVER.dungeonZombieHealth.get() * power);
        SLUtil.setBase(zombie, Attributes.MAX_HEALTH, hp);
        zombie.setHealth((float) hp);
        SLUtil.setBase(zombie, Attributes.ATTACK_DAMAGE, Config.SERVER.dungeonZombieDamage.get() * power);
        SLUtil.setBase(zombie, Attributes.FOLLOW_RANGE, 48.0);

        Player nearest = level.getNearestPlayer(zombie, 64.0);
        if (nearest != null) {
            zombie.setTarget(nearest);
        }
        return zombie;
    }

    private static Zombie createBoss(ServerLevel level) {
        Zombie boss = EntityType.ZOMBIE.create(level);
        if (boss == null) return null;

        boss.moveTo(DungeonLayout.BOSS_SPAWN.x, DungeonLayout.BOSS_SPAWN.y, DungeonLayout.BOSS_SPAWN.z, 0.0f, 0.0f);
        boss.addTag(TAG_BOSS);
        boss.setPersistenceRequired();
        boss.setCanPickUpLoot(false);
        boss.setCustomName(Component.literal("§4§lZombie King"));

        double hp = Config.SERVER.dungeonBossHealth.get();
        SLUtil.setBase(boss, Attributes.MAX_HEALTH, hp);
        boss.setHealth((float) hp);
        SLUtil.setBase(boss, Attributes.ATTACK_DAMAGE, Config.SERVER.dungeonBossDamage.get());
        SLUtil.setBase(boss, Attributes.SCALE, Config.SERVER.dungeonBossScale.get());
        SLUtil.setBase(boss, Attributes.KNOCKBACK_RESISTANCE, 0.6);
        SLUtil.setBase(boss, Attributes.FOLLOW_RANGE, 48.0);
        SLUtil.setBase(boss, Attributes.MOVEMENT_SPEED, 0.25);

        // The crown. Head armour only: a held weapon would add its own damage to the boss.
        boss.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));

        Player nearest = level.getNearestPlayer(boss, 64.0);
        if (nearest != null) {
            boss.setTarget(nearest);
        }
        return boss;
    }

    // ═════════════════════════════════════════════════════════════════
    // Deaths, XP and rewards
    // ═════════════════════════════════════════════════════════════════

    /** System XP override for dungeon mobs; -1 = not a dungeon mob. */
    public static int xpOverride(LivingEntity entity) {
        Set<String> tags = entity.getTags();
        if (tags.isEmpty()) return -1;
        if (tags.contains(TAG_BOSS)) return 0;                                        // paid when the boss falls
        if (tags.contains(TAG_ZOMBIE)) return Config.SERVER.dungeonZombieXp.get();
        if (tags.contains(TAG_MINION)) return Config.SERVER.dungeonZombieXp.get() / 2;
        return -1;
    }

    /** A living entity died (called at the end of LivingDeathEvent, only if the death was not cancelled). */
    public static void onMobDeath(LivingEntity dead) {
        if (!(dead.level() instanceof ServerLevel level) || level.dimension() != DIMENSION) return;

        Set<String> tags = dead.getTags();
        if (tags.isEmpty()) return;

        DungeonSavedData data = DungeonSavedData.get(level);

        if (tags.contains(TAG_BOSS)) {
            onBossDeath(level, data, dead);
        } else if (tags.contains(TAG_ZOMBIE) && data.phase == DungeonPhase.FIGHT) {
            data.kills++;
            data.setDirty();
        }
    }

    private static void onBossDeath(ServerLevel level, DungeonSavedData data, LivingEntity boss) {
        // A leftover king from an earlier fight does not count
        if (data.phase != DungeonPhase.BOSS) return;
        if (data.bossUuid != null && !data.bossUuid.equals(boss.getUUID())) return;

        data.bossUuid = null;
        data.clears++;
        data.timer = CLEARING_DELAY;
        data.setPhase(DungeonPhase.CLEARING);

        discardTagged(level, true, true, false, false);      // the king's army falls with him
        BOSS_BAR.removeAllPlayers();

        for (ServerPlayer p : level.players()) {
            SLUtil.title(p, Component.literal("§6§lDUNGEON CLEARED"),
                    Component.literal("§7The Zombie King has fallen"), 10, 80, 30);
            if (p.isAlive() && !p.isSpectator()) {
                rewardPlayer(p);
            }
        }
        SLUtil.playSound(level, boss.getX(), boss.getY(), boss.getZ(),
                "minecraft:ui.toast.challenge_complete", SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void rewardPlayer(ServerPlayer p) {
        int xp = Config.SERVER.dungeonBossXp.get();
        if (xp > 0) {
            PlayerDataEvents.giveSystemXp(p, xp);
        }

        int sp = Config.SERVER.dungeonClearStatPoints.get();
        if (sp > 0) {
            PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
            p.setData(ModAttachments.PLAYER_SYSTEM_DATA, d.withStatPoints(d.statPoints() + sp));
            PlayerDataEvents.syncToClient(p);
        }

        int zc = Config.SERVER.bossCrystals.get();
        int kc = Config.SERVER.bossKingCrystals.get();
        giveStacks(p, ModItems.ZOMBIE_CRYSTAL.get(), zc);
        giveStacks(p, ModItems.ZOMBIE_KING_CRYSTAL.get(), kc);

        SLUtil.chat(p, "§6[System] §aDungeon cleared!  §e+" + xp + " XP  §b+" + zc + " Zombie Crystals  §d+"
                + kc + " Zombie King Crystals  §a+" + sp + " SP");

        // The next dungeon's key (one per person; the Basic key stays: it leads back to the camp)
        if (KeyRules.giveIfMissing(p, ModItems.SYSTEM_KEY_LOST_CAVE.get())) {
            SLUtil.chat(p, "§6[System] §dYou received the §lSystem Key: Lost Cave§r§d. "
                    + "Its gate will open in a future update.");
        }
    }

    private static void giveStacks(ServerPlayer p, Item item, int total) {
        int max = new ItemStack(item).getMaxStackSize();
        int left = total;
        while (left > 0) {
            int n = Math.min(left, Math.max(1, max));
            ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(item, n));
            left -= n;
        }
    }

    /** True for entities that must not exist any more (used when entities load from disk). */
    public static boolean isOrphan(ServerLevel level, Entity entity) {
        Set<String> tags = entity.getTags();
        if (tags.isEmpty()) return false;

        DungeonSavedData data = DungeonSavedData.get(level);
        if (tags.contains(TAG_BOSS)) {
            return data.phase != DungeonPhase.BOSS
                    || data.bossUuid == null
                    || !data.bossUuid.equals(entity.getUUID());
        }
        if (tags.contains(TAG_ZOMBIE)) return data.phase != DungeonPhase.FIGHT;
        if (tags.contains(TAG_MINION)) return data.phase != DungeonPhase.BOSS;
        return false;
    }

    // ═════════════════════════════════════════════════════════════════
    // The camp: merchants and the bonfire
    // ═════════════════════════════════════════════════════════════════

    /** Makes sure each merchant exists exactly once, with the current price list. */
    public static void ensureNpcs(ServerLevel level) {
        for (DungeonTrades.Role role : DungeonTrades.Role.values()) {
            BlockPos stall = role == DungeonTrades.Role.ALCHEMIST
                    ? DungeonLayout.ALCHEMIST_STALL : DungeonLayout.BLACKSMITH_STALL;

            if (!level.hasChunkAt(stall)) continue;     // not loaded: look again later

            AABB box = new AABB(stall).inflate(5.0, 4.0, 5.0);
            List<Villager> found = level.getEntitiesOfClass(Villager.class, box,
                    v -> v.isAlive() && v.getTags().contains(role.tag));

            if (found.isEmpty()) {
                spawnNpc(level, role, stall);
                continue;
            }
            for (int i = 1; i < found.size(); i++) {
                found.get(i).discard();
            }
            Villager keep = found.get(0);
            String shop = shopTag();
            if (!keep.getTags().contains(shop)) {
                DungeonTrades.fill(keep, role);
                for (String old : new ArrayList<>(keep.getTags())) {
                    if (old.startsWith(TAG_SHOP_PREFIX)) {
                        keep.removeTag(old);        // the tag of an older price list
                    }
                }
                keep.addTag(shop);
            }
        }
    }

    /** The tag of the current price list: the shop version plus the Replay key price from the config. */
    private static String shopTag() {
        return TAG_SHOP_VERSION + "_rk" + Config.SERVER.dungeonReplayKeyPrice.get();
    }

    private static void spawnNpc(ServerLevel level, DungeonTrades.Role role, BlockPos stall) {
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null) return;

        double x = stall.getX() + 0.5;
        double z = stall.getZ() + 0.5;

        // face the bonfire
        double dx = (DungeonLayout.CX + 0.5) - x;
        double dz = (DungeonLayout.CZ + 0.5) - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        v.moveTo(x, stall.getY(), z, yaw, 0.0f);
        v.setYHeadRot(yaw);
        v.setYBodyRot(yaw);

        v.setNoAi(true);
        v.setInvulnerable(true);
        v.setPersistenceRequired();
        v.setCustomName(Component.literal(role.displayName));
        v.setCustomNameVisible(true);

        if (role == DungeonTrades.Role.ALCHEMIST) {
            v.setVillagerData(new VillagerData(VillagerType.SWAMP, VillagerProfession.CLERIC, 5));
        } else {
            v.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.WEAPONSMITH, 5));
        }

        v.addTag(TAG_NPC);
        v.addTag(role.tag);
        v.addTag(shopTag());
        DungeonTrades.fill(v, role);

        level.addFreshEntity(v);
    }

    /** Right-click on the bonfire: full restore (with a short cooldown). */
    public static void rest(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();

        Long last = LAST_REST.get(p.getUUID());
        if (last != null && now - last < REST_COOLDOWN) {
            SLUtil.actionBar(p, "§7The bonfire still warms you...");
            return;
        }
        LAST_REST.put(p.getUUID(), now);

        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(10.0f);
        p.clearFire();

        for (MobEffectInstance effect : new ArrayList<>(p.getActiveEffects())) {
            if (!effect.getEffect().value().isBeneficial()) {
                p.removeEffect(effect.getEffect());
            }
        }

        ManaEvents.fill(p);
        PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        PlayerSystemData refreshed = d.withStamina(StaminaEvents.getMaxStamina(p))
                .withExhausted(false)
                .withDelayTicks(0);
        p.setData(ModAttachments.PLAYER_SYSTEM_DATA, refreshed);
        StaminaEvents.sendStaminaSync(p, refreshed);

        level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.6, p.getZ(), 6, 0.4, 0.3, 0.4, 0.02);
        level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.2, p.getZ(), 12, 0.3, 0.1, 0.3, 0.02);
        SLUtil.playSound(p, "minecraft:block.campfire.crackle", SoundSource.BLOCKS, 1.0f, 1.0f);
        SLUtil.playSound(p, "minecraft:block.beacon.power_select", SoundSource.PLAYERS, 0.6f, 1.4f);
        SLUtil.actionBar(p, "§6You rest by the bonfire. §aHealth, stamina and mana restored.");
    }

    // ═════════════════════════════════════════════════════════════════
    // The Rift Stone: play the dungeon again
    // ═════════════════════════════════════════════════════════════════

    /** The chat line that tells players how to run the dungeon again. */
    private static String replayHint() {
        int price = Config.SERVER.dungeonReplayKeyPrice.get();
        return "§6[System] §7Another run? The §6Blacksmith §7sells a §eSystem Key: Replay §7for §b" + price
                + " Zombie King Crystal" + (price > 1 ? "s" : "")
                + "§7. Right-click the §5Rift Stone §7behind the bonfire (opposite the gate) with it.";
    }

    /** Right-click on the Rift Stone of the camp (called from DungeonEvents). Needs a System Key: Replay. */
    public static void useRiftStone(ServerPlayer player) {
        ItemStack key = player.getMainHandItem();
        if (!key.is(ModItems.SYSTEM_KEY_REPLAY.get())) {
            key = player.getOffhandItem();
        }
        if (!key.is(ModItems.SYSTEM_KEY_REPLAY.get())) {
            SLUtil.actionBar(player, "§c[System] The Rift Stone needs a §eSystem Key: Replay§c. The Blacksmith sells it.");
            return;
        }
        startNewRun(player, key);
    }

    /**
     * Starts the dungeon again: the camp turns back into the zombie arena, the zombie count starts from zero and
     * the Zombie King sleeps until it is reached. Only possible in the camp (the zombies and the king are gone).
     * Everybody who stands in the dungeon is put back at the gate and joins the new run.
     *
     * @param keyStack the System Key: Replay that pays for the run (one is used up, except in creative mode), or null
     * @return true if the new run started
     */
    public static boolean startNewRun(ServerPlayer player, ItemStack keyStack) {
        MinecraftServer server = player.getServer();
        if (server == null || !player.isAlive()) return false;
        if (player.level().dimension() != DIMENSION) return false;

        ServerLevel level = server.getLevel(DIMENSION);
        if (level == null) return false;

        DungeonSavedData data = DungeonSavedData.get(level);
        if (data.phase != DungeonPhase.CLEARED) {
            SLUtil.actionBar(player, "§7[System] The Rift is dormant. It only opens in the Bonfire camp.");
            return false;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            SLUtil.chat(player, "§c[System] Zombies cannot exist on Peaceful difficulty. "
                    + "Raise the difficulty to open the Rift.");
            return false;
        }

        // A trade window must not stay open on a merchant that is about to vanish
        for (ServerPlayer p : level.players()) {
            if (p.containerMenu != p.inventoryMenu) {
                p.closeContainer();
            }
        }

        if (!resetDungeon(server)) return false;

        // The key is only used up now that the Rift really opened
        if (keyStack != null && !keyStack.isEmpty() && !player.isCreative()) {
            keyStack.shrink(1);
        }

        BlockPos stone = DungeonLayout.RIFT_STONE;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, stone.getX() + 0.5, stone.getY() + 1.0, stone.getZ() + 0.5,
                80, 0.5, 0.8, 0.5, 0.2);
        SLUtil.playSound(level, stone.getX() + 0.5, stone.getY() + 0.5, stone.getZ() + 0.5,
                "minecraft:block.respawn_anchor.charge", SoundSource.AMBIENT, 1.5f, 0.7f);

        String opener = player.getName().getString();
        for (ServerPlayer p : activePlayers(level.players())) {
            SLUtil.chat(p, "§6[System] §e" + opener + " §7opened the Rift. The dungeon begins again!");
            onEnterDungeon(p);      // title, Health Potion top-up, key cleanup: same as walking in
        }
        return true;
    }

    // ═════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════

    public static boolean isDungeonHostile(Zombie z) {
        if (!z.isAlive()) return false;
        Set<String> tags = z.getTags();
        return tags.contains(TAG_ZOMBIE) || tags.contains(TAG_MINION) || tags.contains(TAG_BOSS);
    }

    private static int countAlive(ServerLevel level, String tag) {
        return level.getEntitiesOfClass(Zombie.class, DungeonLayout.arenaBox(),
                z -> z.isAlive() && z.getTags().contains(tag)).size();
    }

    private static void retarget(ServerLevel level, List<ServerPlayer> active) {
        List<Zombie> mobs = level.getEntitiesOfClass(Zombie.class, DungeonLayout.arenaBox(),
                DungeonManager::isDungeonHostile);

        for (Zombie z : mobs) {
            LivingEntity current = z.getTarget();
            if (current instanceof Player pl && pl.isAlive() && !pl.isCreative() && !pl.isSpectator()) {
                continue;
            }
            ServerPlayer best = null;
            double bestDist = Double.MAX_VALUE;
            for (ServerPlayer p : active) {
                if (p.isCreative()) continue;           // like every other monster
                double d = p.distanceToSqr(z);
                if (d < bestDist) {
                    bestDist = d;
                    best = p;
                }
            }
            if (best != null) {
                z.setTarget(best);
            }
        }
    }

    /** Removes dungeon mobs (no death event, no drops, no kill credit). */
    private static void discardTagged(ServerLevel level, boolean zombies, boolean minions,
                                      boolean boss, boolean npcs) {
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, DungeonLayout.arenaBox(), m -> {
            Set<String> t = m.getTags();
            if (t.isEmpty()) return false;
            return (zombies && t.contains(TAG_ZOMBIE))
                    || (minions && t.contains(TAG_MINION))
                    || (boss && t.contains(TAG_BOSS))
                    || (npcs && t.contains(TAG_NPC));
        });
        for (Mob m : mobs) {
            level.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + m.getBbHeight() * 0.5, m.getZ(),
                    8, 0.3, 0.5, 0.3, 0.02);
            m.discard();
        }
    }

    // ── boss bars ────────────────────────────────────────────────────

    private static void updateBars(DungeonSavedData data, List<ServerPlayer> all) {
        boolean fight = data.phase == DungeonPhase.FIGHT && !all.isEmpty();
        boolean boss  = data.phase == DungeonPhase.BOSS && !all.isEmpty();

        if (fight) {
            int target = Math.max(1, Config.SERVER.dungeonKillTarget.get());
            int kills = Math.min(data.kills, target);
            PROGRESS_BAR.setName(Component.literal("§fZombies slain: §e" + kills + " §7/ §e" + target));
            PROGRESS_BAR.setProgress(Mth.clamp(kills / (float) target, 0.0f, 1.0f));
        }
        syncBar(PROGRESS_BAR, fight, all);
        syncBar(BOSS_BAR, boss, all);
    }

    private static void syncBar(ServerBossEvent bar, boolean show, List<ServerPlayer> players) {
        if (!show) {
            if (!bar.getPlayers().isEmpty()) bar.removeAllPlayers();
            return;
        }
        bar.setVisible(true);
        for (ServerPlayer p : players) {
            bar.addPlayer(p);                                    // ignored if already added
        }
        for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
            if (!players.contains(p)) bar.removePlayer(p);       // left the dungeon / logged out
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // World geometry
    // ═════════════════════════════════════════════════════════════════

    /** Builds the arena the first time (or after a mod update changed the geometry). */
    private static void ensureBuilt(ServerLevel level, DungeonSavedData data) {
        boolean wantHub = data.phase == DungeonPhase.CLEARED;
        boolean upToDate = data.arenaBuilt
                && data.geometryVersion == DungeonSavedData.GEOMETRY_VERSION
                && (!wantHub || data.hubBuilt);
        if (!upToDate) {
            buildGeometry(level, data);
        }
    }

    private static void buildGeometry(ServerLevel level, DungeonSavedData data) {
        DungeonArenaBuilder.buildCombatArena(level);
        boolean hub = data.phase == DungeonPhase.CLEARED;
        if (hub) {
            DungeonArenaBuilder.buildPeaceHub(level);
        }
        data.arenaBuilt = true;
        data.hubBuilt = hub;
        data.geometryVersion = DungeonSavedData.GEOMETRY_VERSION;
        data.setDirty();
    }

    // ═════════════════════════════════════════════════════════════════
    // Admin tools (used by /sl dungeon ...)
    // ═════════════════════════════════════════════════════════════════

    public static List<String> statusLines(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        ServerLevel level = server.getLevel(DIMENSION);
        if (level == null) {
            out.add("§cThe dungeon dimension is not loaded (data/sololeveling/dimension/tutorial_dungeon.json).");
            return out;
        }
        DungeonSavedData d = DungeonSavedData.get(level);
        int target = Config.SERVER.dungeonKillTarget.get();

        out.add("§6═══ Tutorial Dungeon ═══");
        out.add("§ePhase: §f" + d.phase + "   §eKills: §f" + d.kills + "/" + target + "   §eClears: §f" + d.clears);

        String bossText = "none";
        if (d.bossUuid != null) {
            Entity e = level.getEntity(d.bossUuid);
            if (e instanceof LivingEntity le && le.isAlive()) {
                bossText = String.format("%.0f / %.0f HP", le.getHealth(), le.getMaxHealth());
            } else {
                bossText = "registered, but not loaded";
            }
        }
        out.add("§eBoss: §f" + bossText);
        out.add("§ePlayers inside: §f" + level.players().size()
                + "   §eZombies alive: §f" + countAlive(level, TAG_ZOMBIE)
                + "   §eMinions: §f" + countAlive(level, TAG_MINION));
        out.add("§eArena built: §f" + d.arenaBuilt + " (v" + d.geometryVersion + ")   §eCamp built: §f" + d.hubBuilt);
        return out;
    }

    /** Back to a fresh, unbeaten dungeon (the clear counter is kept). */
    public static boolean resetDungeon(MinecraftServer server) {
        ServerLevel level = server.getLevel(DIMENSION);
        if (level == null) return false;
        DungeonSavedData d = DungeonSavedData.get(level);

        discardTagged(level, true, true, true, true);

        d.phase = DungeonPhase.IDLE;
        d.kills = 0;
        d.timer = 0;
        d.bossUuid = null;
        d.hubBuilt = false;
        buildGeometry(level, d);       // phase is IDLE -> combat arena only
        d.setDirty();

        PROGRESS_BAR.removeAllPlayers();
        BOSS_BAR.removeAllPlayers();
        BossController.reset();
        spawnCooldown = 40;
        emptyTicks = 0;
        bossMissingTicks = 0;

        movePlayersToSpawn(level);
        return true;
    }

    /** Skips the zombie waves: the Zombie King appears after the usual countdown. */
    public static boolean forceBoss(MinecraftServer server) {
        ServerLevel level = server.getLevel(DIMENSION);
        if (level == null) return false;
        DungeonSavedData d = DungeonSavedData.get(level);
        if (d.phase != DungeonPhase.IDLE && d.phase != DungeonPhase.FIGHT) return false;

        ensureBuilt(level, d);
        discardTagged(level, true, false, false, false);
        d.kills = Config.SERVER.dungeonKillTarget.get();
        d.timer = 40;
        d.setPhase(DungeonPhase.BOSS_PENDING);
        return true;
    }

    /** Skips straight to the camp (no rewards). */
    public static boolean forceClear(MinecraftServer server) {
        ServerLevel level = server.getLevel(DIMENSION);
        if (level == null) return false;
        DungeonSavedData d = DungeonSavedData.get(level);
        if (d.phase == DungeonPhase.CLEARED || d.phase == DungeonPhase.CLEARING) return false;

        ensureBuilt(level, d);
        discardTagged(level, true, true, true, false);
        BOSS_BAR.removeAllPlayers();
        PROGRESS_BAR.removeAllPlayers();
        d.bossUuid = null;
        d.clears++;
        d.timer = 20;
        d.setPhase(DungeonPhase.CLEARING);
        return true;
    }

    /** Rebuilds the blocks (keeps the phase) and puts everybody back at the gate. */
    public static boolean rebuild(MinecraftServer server) {
        ServerLevel level = server.getLevel(DIMENSION);
        if (level == null) return false;
        DungeonSavedData d = DungeonSavedData.get(level);

        buildGeometry(level, d);
        movePlayersToSpawn(level);
        if (d.phase == DungeonPhase.CLEARED) {
            lastArrival = level.getGameTime() - ARRIVAL_SETTLE;
            ensureNpcs(level);
        }
        return true;
    }

    private static void movePlayersToSpawn(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            p.connection.teleport(DungeonLayout.SPAWN.x, DungeonLayout.SPAWN.y, DungeonLayout.SPAWN.z,
                    DungeonLayout.SPAWN_YAW, 0.0f);
        }
    }

    /**
     * Gives a key (command). "basic" and "lostcave": unless the player already carries that kind.
     * "replay": always one more, the Replay key has no one-per-person rule (you pay for each run).
     * @return true if a key was given
     */
    public static boolean giveKey(ServerPlayer p, String kind) {
        return switch (kind) {
            case "lostcave" -> KeyRules.giveIfMissing(p, ModItems.SYSTEM_KEY_LOST_CAVE.get());
            case "replay" -> {
                ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(ModItems.SYSTEM_KEY_REPLAY.get()));
                yield true;
            }
            default -> KeyRules.giveIfMissing(p, ModItems.SYSTEM_KEY.get());
        };
    }
}
