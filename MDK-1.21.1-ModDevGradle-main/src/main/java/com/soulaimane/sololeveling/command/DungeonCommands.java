package com.soulaimane.sololeveling.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.dungeon.DungeonManager;
import com.soulaimane.sololeveling.dungeon.engine.DungeonDefinition;
import com.soulaimane.sololeveling.dungeon.engine.DungeonEngine;
import com.soulaimane.sololeveling.dungeon.engine.DungeonRegistry;
import com.soulaimane.sololeveling.item.DungeonKeyItem;
import com.soulaimane.sololeveling.power.Requirements;
import com.soulaimane.sololeveling.power.ServerPowerView;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.concurrent.CompletableFuture;

/**
 * /sl dungeon ...   (the permission level 2 requirement comes from the /sl root command)
 *
 * The Tutorial Dungeon (its own system, one fixed dungeon):
 *   status    phase, kills, boss health, players inside (and the runs of the dungeon engine)
 *   enter     go to the Tutorial Dungeon without a key
 *   leave     come back from any dungeon (works during a boss fight too)
 *   key       give yourself the Basic key (key lostcave: the Lost Cave key, key replay: a Replay key);
 *             one Basic and one Lost Cave key per person, as many Replay keys as you like
 *   boss      skip the zombie waves: the Zombie King appears
 *   clear     skip straight to the Bonfire camp (no rewards)
 *   rebuild   rebuild the blocks and put everybody at the gate
 *   reset     back to a fresh, unbeaten dungeon
 *
 * The dungeon engine (the dungeons registered in ModDungeons):
 *   list                      every dungeon, its levels, its requirements and your progress
 *   give <dungeon> [level]    a Dungeon Key (written for that level if you give one)
 *   enter <dungeon> [level]   go in without a key and without the requirements (starts at that level if you give one)
 *   skip                      win the level you are in, as if it was beaten (rewards and progress included)
 *   end <dungeon>             send everybody out of that dungeon and drop its run
 *   progress                  how many levels you cleared in each dungeon
 *   progress <dungeon> <n>    set the number of levels you cleared (0 = none)
 *
 * A dungeon can be written with or without the namespace: "example_crypt".
 */
public final class DungeonCommands {
    private DungeonCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("dungeon")
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))

                // "enter" alone = the Tutorial Dungeon, "enter <dungeon> [level]" = a dungeon of the engine
                .then(Commands.literal("enter")
                        .executes(ctx -> enter(ctx.getSource()))
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(DungeonCommands::suggestDungeons)
                                .executes(ctx -> enterEngine(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "dungeon"), 0))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 99))
                                        .executes(ctx -> enterEngine(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "dungeon"),
                                                IntegerArgumentType.getInteger(ctx, "level"))))))

                .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource())))

                // Tutorial keys
                .then(Commands.literal("key")
                        .executes(ctx -> key(ctx.getSource(), "basic"))
                        .then(Commands.literal("basic").executes(ctx -> key(ctx.getSource(), "basic")))
                        .then(Commands.literal("lostcave").executes(ctx -> key(ctx.getSource(), "lostcave")))
                        .then(Commands.literal("replay").executes(ctx -> key(ctx.getSource(), "replay"))))
                .then(Commands.literal("boss").executes(ctx -> boss(ctx.getSource())))
                .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())))
                .then(Commands.literal("rebuild").executes(ctx -> rebuild(ctx.getSource())))
                .then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource())))

                // Dungeon engine
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("give")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(DungeonCommands::suggestDungeons)
                                .executes(ctx -> give(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "dungeon"), 0))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 99))
                                        .executes(ctx -> give(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "dungeon"),
                                                IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("skip").executes(ctx -> skip(ctx.getSource())))
                .then(Commands.literal("end")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(DungeonCommands::suggestDungeons)
                                .executes(ctx -> end(ctx.getSource(), StringArgumentType.getString(ctx, "dungeon")))))
                .then(Commands.literal("progress")
                        .executes(ctx -> progress(ctx.getSource()))
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(DungeonCommands::suggestDungeons)
                                .then(Commands.argument("cleared", IntegerArgumentType.integer(0, 99))
                                        .executes(ctx -> setProgress(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "dungeon"),
                                                IntegerArgumentType.getInteger(ctx, "cleared"))))));
    }

    private static CompletableFuture<Suggestions> suggestDungeons(CommandContext<CommandSourceStack> ctx,
                                                                  SuggestionsBuilder builder) {
        for (DungeonDefinition dungeon : DungeonRegistry.all()) {
            builder.suggest(dungeon.location().getPath());
        }
        return builder.buildFuture();
    }

    private static DungeonDefinition find(CommandSourceStack src, String id) {
        DungeonDefinition dungeon = DungeonRegistry.get(id);
        if (dungeon == null) {
            src.sendFailure(Component.literal("Unknown dungeon: " + id + " (see /sl dungeon list)"));
        }
        return dungeon;
    }

    // ═════════════════════════════════════════════════════════════════
    // Both kinds
    // ═════════════════════════════════════════════════════════════════

    private static int status(CommandSourceStack src) {
        for (String line : DungeonManager.statusLines(src.getServer())) {
            src.sendSuccess(() -> Component.literal(line), false);
        }
        src.sendSuccess(() -> Component.literal("§6═══ Dungeon engine ═══"), false);
        for (String line : DungeonEngine.describeRuns()) {
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int leave(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();

        // A dungeon of the engine (or a left-over of one)
        if (DungeonEngine.isInside(p) || DungeonRegistry.isEngineDimension(p.level().dimension())) {
            if (DungeonEngine.leave(p, true)) {
                return 1;
            }
            src.sendFailure(Component.literal("Could not leave the dungeon."));
            return 0;
        }

        // The Tutorial Dungeon
        if (!DungeonManager.isInDungeon(p)) {
            src.sendFailure(Component.literal("You are not in a dungeon."));
            return 0;
        }
        DungeonManager.leave(p, true);
        return 1;
    }

    // ═════════════════════════════════════════════════════════════════
    // The Tutorial Dungeon
    // ═════════════════════════════════════════════════════════════════

    private static int enter(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (DungeonEngine.isInside(p) || DungeonRegistry.isEngineDimension(p.level().dimension())) {
            src.sendFailure(Component.literal("Leave the dungeon you are in first (/sl dungeon leave)."));
            return 0;
        }
        if (DungeonManager.enter(p, null)) {
            return 1;
        }
        src.sendFailure(Component.literal("Could not enter the dungeon (see the message in the chat)."));
        return 0;
    }

    private static int key(CommandSourceStack src, String kind) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        String name = switch (kind) {
            case "lostcave" -> "System Key: Lost Cave";
            case "replay" -> "System Key: Replay";
            default -> "System Key: Basic";
        };
        if (DungeonManager.giveKey(p, kind)) {
            src.sendSuccess(() -> Component.literal("[System] You received the " + name + "."), false);
            return 1;
        }
        src.sendFailure(Component.literal("You already carry the " + name + " (one key of each kind per person)."));
        return 0;
    }

    private static int boss(CommandSourceStack src) {
        if (DungeonManager.forceBoss(src.getServer())) {
            src.sendSuccess(() -> Component.literal(
                    "[System] The zombie waves are skipped: the Zombie King is coming."), true);
            return 1;
        }
        src.sendFailure(Component.literal(
                "The Zombie King can only be called while the dungeon is idle or in the zombie phase."));
        return 0;
    }

    private static int clear(CommandSourceStack src) {
        if (DungeonManager.forceClear(src.getServer())) {
            src.sendSuccess(() -> Component.literal(
                    "[System] Skipping to the Bonfire camp (no rewards)."), true);
            return 1;
        }
        src.sendFailure(Component.literal("The dungeon is already cleared (use /sl dungeon reset to replay it)."));
        return 0;
    }

    private static int rebuild(CommandSourceStack src) {
        if (DungeonManager.rebuild(src.getServer())) {
            src.sendSuccess(() -> Component.literal("[System] Dungeon blocks rebuilt."), true);
            return 1;
        }
        src.sendFailure(Component.literal("The dungeon dimension is not loaded."));
        return 0;
    }

    private static int reset(CommandSourceStack src) {
        if (DungeonManager.resetDungeon(src.getServer())) {
            src.sendSuccess(() -> Component.literal(
                    "[System] The dungeon was reset. Walk in with a System Key to start a new run."), true);
            return 1;
        }
        src.sendFailure(Component.literal("The dungeon dimension is not loaded."));
        return 0;
    }

    // ═════════════════════════════════════════════════════════════════
    // The dungeon engine
    // ═════════════════════════════════════════════════════════════════

    private static int list(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        src.sendSuccess(() -> Component.literal("§6═══ Dungeons ═══"), false);

        if (DungeonRegistry.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7No dungeon is registered yet (see ModDungeons and ExampleDungeon)."), false);
            return 1;
        }

        ServerPowerView view = new ServerPowerView(p);
        for (DungeonDefinition dungeon : DungeonRegistry.all()) {
            int cleared = p.getData(ModAttachments.PLAYER_DUNGEON_PROGRESS).clearedLevels(dungeon.id());
            src.sendSuccess(() -> Component.literal("§e" + dungeon.location().getPath()
                    + " §8(" + dungeon.displayName().getString() + ")  §f" + dungeon.levelCount() + " §7level(s), cleared §f"
                    + Math.min(cleared, dungeon.levelCount()) + "§7/" + dungeon.levelCount()), false);

            Requirements need = dungeon.entryRequirements();
            if (!need.isEmpty()) {
                StringBuilder sb = new StringBuilder("   §7needs:");
                for (Requirements.Line line : need.lines(view)) {
                    sb.append(line.met() ? " §a✔ " : " §c✘ ").append(line.text());
                }
                src.sendSuccess(() -> Component.literal(sb.toString()), false);
            }
        }
        return 1;
    }

    private static int give(CommandSourceStack src, String id, int level) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        DungeonDefinition dungeon = find(src, id);
        if (dungeon == null) return 0;

        if (level > dungeon.levelCount()) {
            src.sendFailure(Component.literal(dungeon.displayName().getString() + " has only "
                    + dungeon.levelCount() + " level(s)."));
            return 0;
        }

        ItemHandlerHelper.giveItemToPlayer(p, DungeonKeyItem.create(dungeon, level));
        src.sendSuccess(() -> Component.literal("[System] You received the key of "
                + dungeon.displayName().getString() + (level > 0 ? " (level " + level + ")" : "") + "."), false);
        return 1;
    }

    private static int enterEngine(CommandSourceStack src, String id, int level) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        DungeonDefinition dungeon = find(src, id);
        if (dungeon == null) return 0;

        if (level > dungeon.levelCount()) {
            src.sendFailure(Component.literal(dungeon.displayName().getString() + " has only "
                    + dungeon.levelCount() + " level(s)."));
            return 0;
        }
        if (DungeonEngine.enter(p, dungeon, null, level, true)) {
            return 1;
        }
        src.sendFailure(Component.literal("Could not enter the dungeon (see the message in the chat)."));
        return 0;
    }

    private static int skip(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (DungeonEngine.skipLevel(p)) {
            src.sendSuccess(() -> Component.literal("[System] The level is completed."), true);
            return 1;
        }
        src.sendFailure(Component.literal("You are not in a level that is running "
                + "(the fight starts a few seconds after you arrive)."));
        return 0;
    }

    private static int end(CommandSourceStack src, String id) {
        DungeonDefinition dungeon = find(src, id);
        if (dungeon == null) return 0;

        if (DungeonEngine.endRun(src.getServer(), dungeon)) {
            src.sendSuccess(() -> Component.literal("[System] The run of " + dungeon.displayName().getString()
                    + " was ended. Everybody was sent out."), true);
            return 1;
        }
        src.sendFailure(Component.literal("No run of " + dungeon.displayName().getString() + " is going."));
        return 0;
    }

    private static int progress(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        src.sendSuccess(() -> Component.literal("§6═══ Your dungeon progress ═══"), false);

        if (DungeonRegistry.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7No dungeon is registered yet."), false);
            return 1;
        }
        for (DungeonDefinition dungeon : DungeonRegistry.all()) {
            int cleared = p.getData(ModAttachments.PLAYER_DUNGEON_PROGRESS).clearedLevels(dungeon.id());
            src.sendSuccess(() -> Component.literal("  §e" + dungeon.location().getPath() + " §7cleared §f"
                    + Math.min(cleared, dungeon.levelCount()) + "§7/" + dungeon.levelCount()), false);
        }
        return 1;
    }

    private static int setProgress(CommandSourceStack src, String id, int cleared) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        DungeonDefinition dungeon = find(src, id);
        if (dungeon == null) return 0;

        DungeonEngine.setProgress(p, dungeon, cleared);
        int now = Math.min(cleared, dungeon.levelCount());
        src.sendSuccess(() -> Component.literal("[System] " + dungeon.displayName().getString()
                + ": " + now + "/" + dungeon.levelCount() + " level(s) cleared."), false);
        return 1;
    }
}
