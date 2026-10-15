package com.soulaimane.sololeveling.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.soulaimane.sololeveling.item.MagicPaperItem;
import com.soulaimane.sololeveling.magic.Magic;
import com.soulaimane.sololeveling.magic.MagicManager;
import com.soulaimane.sololeveling.magic.MagicRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.concurrent.CompletableFuture;

/**
 * /sl magic ...   (the permission level 2 requirement comes from the /sl root command)
 *
 *   list                   every magic, and whether you know it
 *   give <magic> [count]   a Magic Paper of that magic (burn it with a right-click: the requirements apply)
 *   learn <magic>          learn it at once: no paper, no requirements
 *   forget <magic>         forget it
 *   forgetall              forget every magic
 *
 * A magic can be written with or without the namespace: "flame_bolt".
 */
public final class MagicCommands {
    private MagicCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("magic")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("give")
                        .then(Commands.argument("magic", StringArgumentType.word())
                                .suggests(MagicCommands::suggestMagics)
                                .executes(ctx -> give(ctx.getSource(), StringArgumentType.getString(ctx, "magic"), 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                                        .executes(ctx -> give(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "magic"),
                                                IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("learn")
                        .then(Commands.argument("magic", StringArgumentType.word())
                                .suggests(MagicCommands::suggestMagics)
                                .executes(ctx -> learn(ctx.getSource(), StringArgumentType.getString(ctx, "magic")))))
                .then(Commands.literal("forget")
                        .then(Commands.argument("magic", StringArgumentType.word())
                                .suggests(MagicCommands::suggestMagics)
                                .executes(ctx -> forget(ctx.getSource(), StringArgumentType.getString(ctx, "magic")))))
                .then(Commands.literal("forgetall").executes(ctx -> forgetAll(ctx.getSource())));
    }

    private static CompletableFuture<Suggestions> suggestMagics(CommandContext<CommandSourceStack> ctx,
                                                                SuggestionsBuilder builder) {
        for (Magic magic : MagicRegistry.all()) {
            builder.suggest(magic.location().getPath());
        }
        return builder.buildFuture();
    }

    private static Magic find(CommandSourceStack src, String id) {
        Magic magic = MagicRegistry.get(id);
        if (magic == null) {
            src.sendFailure(Component.literal("Unknown magic: " + id + " (see /sl magic list)"));
        }
        return magic;
    }

    // ─────────────────────────────────────────────────────────────────

    private static int list(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        src.sendSuccess(() -> Component.literal("§6═══ Magics ═══"), false);

        if (MagicRegistry.all().isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7No magic is registered (see ModMagics)."), false);
            return 1;
        }
        for (Magic magic : MagicRegistry.all()) {
            boolean known = MagicManager.knows(p, magic);
            src.sendSuccess(() -> Component.literal("  " + (known ? "§a✔ " : "§7✘ ") + magic.location().getPath()
                    + " §8(" + magic.displayName().getString() + ")"
                    + (known ? "  §aknown" : "  §7not learned")), false);
        }
        return 1;
    }

    private static int give(CommandSourceStack src, String id, int count) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Magic magic = find(src, id);
        if (magic == null) return 0;

        ItemHandlerHelper.giveItemToPlayer(p, MagicPaperItem.create(magic, count));
        src.sendSuccess(() -> Component.literal("[System] You received " + count + " Magic Paper of "
                + magic.displayName().getString() + "."), false);
        return 1;
    }

    private static int learn(CommandSourceStack src, String id) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Magic magic = find(src, id);
        if (magic == null) return 0;

        if (MagicManager.knows(p, magic)) {
            src.sendFailure(Component.literal("You already know " + magic.displayName().getString() + "."));
            return 0;
        }
        MagicManager.learn(p, magic);
        return 1;
    }

    private static int forget(CommandSourceStack src, String id) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Magic magic = find(src, id);
        if (magic == null) return 0;

        if (!MagicManager.knows(p, magic)) {
            src.sendFailure(Component.literal("You do not know " + magic.displayName().getString() + "."));
            return 0;
        }
        MagicManager.forget(p, magic);
        src.sendSuccess(() -> Component.literal("[System] You forgot " + magic.displayName().getString() + "."), false);
        return 1;
    }

    private static int forgetAll(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        MagicManager.forgetAll(p);
        src.sendSuccess(() -> Component.literal("[System] Every magic was forgotten."), false);
        return 1;
    }
}
