package com.soulaimane.sololeveling.skill;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.PlayerSkillData;
import com.soulaimane.sololeveling.power.ActionBarManager;
import com.soulaimane.sololeveling.power.PowerCooldowns;
import com.soulaimane.sololeveling.power.PowerSync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Game-bus hooks of the skill and magic systems:
 * - the passive bonuses of the learned skills are put back on the player when he logs in, respawns or changes dimension
 * - the damage hooks of passive skills
 * - clean-up of cooldowns
 */
public class SkillEvents {

    // ─── Passives and saved data ─────────────────────────────────────

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ActionBarManager.cleanUp(p);
            SkillManager.applyPassives(p);
            PowerSync.sync(p);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Player original = event.getOriginal();

        // The attachments are marked copyOnDeath; copying again by hand makes sure nothing depends on event order
        copy(original, p, ModAttachments.PLAYER_SKILL_DATA);
        copy(original, p, ModAttachments.PLAYER_MAGIC_DATA);
        copy(original, p, ModAttachments.PLAYER_ACTION_BAR);
        copy(original, p, ModAttachments.PLAYER_DUNGEON_PROGRESS);

        SkillManager.applyPassives(p);
        PowerSync.sync(p);
    }

    private static <T> void copy(Player from, ServerPlayer to, Supplier<AttachmentType<T>> type) {
        if (from.hasData(type)) {
            to.setData(type, from.getData(type));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            SkillManager.applyPassives(p);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            SkillManager.applyPassives(p);
        }
    }

    // ─── Damage hooks ────────────────────────────────────────────────

    /** Runs after the mod's own melee formula (NORMAL priority), so a hook sees the final melee number. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            runHooks(attacker, event, true);
        }
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer victim) {
            runHooks(victim, event, false);
        }
    }

    private static void runHooks(ServerPlayer p, LivingIncomingDamageEvent event, boolean dealing) {
        PlayerSkillData data = p.getData(ModAttachments.PLAYER_SKILL_DATA);
        if (data.ranks().isEmpty()) return;

        for (Map.Entry<String, Integer> learned : data.ranks().entrySet()) {
            Skill skill = SkillRegistry.byId(learned.getKey());
            if (skill == null) continue;

            SkillDamageHook hook = dealing ? skill.onDealDamage() : skill.onTakeDamage();
            if (hook == null) continue;

            hook.apply(new SkillContext(p, skill, learned.getValue()), event);
            if (event.isCanceled()) return;
        }
    }

    // ─── Clean-up ────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PowerCooldowns.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PowerCooldowns.clearAll();
    }
}
