package com.soulaimane.sololeveling.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.ModDataMaps;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.event.AttributeEvents;
import com.soulaimane.sololeveling.event.DexterityEvents;
import com.soulaimane.sololeveling.event.PlayerDataEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class SLCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("sl")
                .requires(s -> s.hasPermission(2))

                // ── /sl level set <n> ──
                .then(Commands.literal("level")
                        .then(Commands.literal("set")
                                .then(Commands.argument("n", IntegerArgumentType.integer(0, PlayerSystemData.MAX_LEVEL))
                                        .executes(ctx -> {
                                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                                            int target = IntegerArgumentType.getInteger(ctx, "n");
                                            PlayerSystemData old = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);

                                            int diff = target - old.level();
                                            int spDelta = diff * PlayerSystemData.STAT_POINTS_PER_LEVEL;
                                            int newStatPoints = Math.max(0, old.statPoints() + spDelta);

                                            PlayerSystemData updated = old
                                                    .withLevel(target)
                                                    .withStatPoints(newStatPoints);

                                            p.setData(ModAttachments.PLAYER_SYSTEM_DATA, updated);
                                            AttributeEvents.applyAllModifiers(p);
                                            PlayerDataEvents.syncToClient(p);

                                            final int granted = spDelta;
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                            "[System] Level " + old.level() + " → " + target
                                                                    + "  (Rank: " + updated.rank().getSerializedName() + ")"
                                                                    + (granted != 0
                                                                    ? "  SP " + (granted > 0 ? "+" : "") + granted
                                                                    + " → " + newStatPoints
                                                                    : "")),
                                                    false);
                                            return 1;
                                        }))))

                // ── /sl xp add <n> ──
                .then(Commands.literal("xp")
                        .then(Commands.literal("add")
                                .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                                            PlayerDataEvents.giveSystemXp(p, IntegerArgumentType.getInteger(ctx, "n"));
                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("[System] XP added."), false);
                                            return 1;
                                        }))))

                // ── /sl xpcurve ──
                .then(Commands.literal("xpcurve")
                        .executes(ctx -> {
                            StringBuilder sb = new StringBuilder("§6═══ XP Curve ═══\n");
                            for (int i = 0; i <= 10; i++) {
                                sb.append("§eLv.").append(i).append(" → ").append(i + 1)
                                        .append(": §f").append(PlayerSystemData.xpForNextLevel(i)).append("\n");
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                            return 1;
                        }))

                // ── /sl stats ──
                .then(Commands.literal("stats")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            PlayerSystemData x = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "§6═══ Solo Leveling ═══\n" +
                                            "§eLevel: §f" + x.level() + "  §eXP: §f" + x.xp() + "/" + PlayerSystemData.xpForNextLevel(x.level()) + "\n" +
                                            "§eRank: §f" + x.rank().getSerializedName() + "  §eSP: §f" + x.statPoints() + "\n" +
                                            "§eSTR: §f" + x.strength() + "  §eAGI: §f" + x.agility() + "  §eVIT: §f" + x.vitality() + "\n" +
                                            "§eINT: §f" + x.intelligence() + "  §eSEN: §f" + x.sense() + "\n" +
                                            "§eDEX: §f" + x.dexterity() + "  §eDUR: §f" + x.durability() + "\n" +
                                            "§eStamina: §f" + String.format("%.1f", x.stamina()) + "/" + PlayerSystemData.MAX_STAMINA + "\n" +
                                            "§eMana: §f" + String.format("%.1f", x.mana()) + "/" + PlayerSystemData.MAX_MANA + "\n" +
                                            "§eLoad: §f" + String.format("%.1f", x.currentEquipLoad()) + "\n" +
                                            "§eExhausted: §f" + x.exhausted()
                            ), false);
                            return 1;
                        }))

                // ── /sl dex ──
                .then(Commands.literal("dex")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ItemStack held = p.getMainHandItem();
                            PlayerSystemData x = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);

                            int req = DexterityEvents.getRequirement(held);
                            Integer fromMap = held.isEmpty()
                                    ? null
                                    : held.getItemHolder().getData(ModDataMaps.DEXTERITY_REQUIREMENT);

                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "§6═══ Dexterity Debug ═══\n" +
                                            "§ePlayer DEX: §f" + x.dexterity() + "\n" +
                                            "§eHeld item: §f" + (held.isEmpty() ? "<empty>" : held.getHoverName().getString()) + "\n" +
                                            "§eData-map value: §f" + (fromMap == null ? "§cnull (data map not loaded)" : fromMap) + "\n" +
                                            "§eEffective requirement: §f" + req + "\n" +
                                            "§eRestricted: §f" + (DexterityEvents.isRestricted(p, held) ? "§cYES" : "§aNO")
                            ), false);
                            return 1;
                        }))

                // ── /sl stat set <stat> <n> ──
                .then(Commands.literal("stat")
                        .then(Commands.literal("set")
                                .then(Commands.argument("stat", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            for (String s : new String[]{"strength","agility","vitality",
                                                    "intelligence","sense","dexterity","durability"})
                                                b.suggest(s);
                                            return b.buildFuture();
                                        })
                                        .then(Commands.argument("n", IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                                    String st = StringArgumentType.getString(ctx, "stat");
                                                    int n = IntegerArgumentType.getInteger(ctx, "n");
                                                    PlayerSystemData old = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
                                                    p.setData(ModAttachments.PLAYER_SYSTEM_DATA, old.withStat(st, n));
                                                    AttributeEvents.applyAllModifiers(p);
                                                    PlayerDataEvents.syncToClient(p);
                                                    ctx.getSource().sendSuccess(() ->
                                                            Component.literal("[System] " + st + " → " + n), false);
                                                    return 1;
                                                })))))

                // ── /sl stamina set <n> ──
                .then(Commands.literal("stamina")
                        .then(Commands.literal("set")
                                .then(Commands.argument("n", FloatArgumentType.floatArg(0, PlayerSystemData.MAX_STAMINA))
                                        .executes(ctx -> {
                                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                                            float n = FloatArgumentType.getFloat(ctx, "n");
                                            PlayerSystemData old = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
                                            p.setData(ModAttachments.PLAYER_SYSTEM_DATA,
                                                    old.withStamina(n).withExhausted(false));
                                            PlayerDataEvents.syncToClient(p);
                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("[System] Stamina → " + n), false);
                                            return 1;
                                        }))))

                // ── /sl mana set <n> ──
                .then(Commands.literal("mana")
                        .then(Commands.literal("set")
                                .then(Commands.argument("n", FloatArgumentType.floatArg(0, PlayerSystemData.MAX_MANA))
                                        .executes(ctx -> {
                                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                                            float n = FloatArgumentType.getFloat(ctx, "n");
                                            PlayerSystemData old = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
                                            p.setData(ModAttachments.PLAYER_SYSTEM_DATA, old.withMana(n));
                                            PlayerDataEvents.syncToClient(p);
                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("[System] Mana → " + n), false);
                                            return 1;
                                        }))))

                // ── /sl reset ──
                .then(Commands.literal("reset")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.setData(ModAttachments.PLAYER_SYSTEM_DATA, PlayerSystemData.DEFAULT);
                            AttributeEvents.applyAllModifiers(p);
                            PlayerDataEvents.syncToClient(p);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("[System] Reset."), false);
                            return 1;
                        })));
    }
}