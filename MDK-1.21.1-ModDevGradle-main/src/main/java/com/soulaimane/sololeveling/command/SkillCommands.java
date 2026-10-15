package com.soulaimane.sololeveling.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.soulaimane.sololeveling.data.PlayerSkillData;
import com.soulaimane.sololeveling.skill.Skill;
import com.soulaimane.sololeveling.skill.SkillBranch;
import com.soulaimane.sololeveling.skill.SkillManager;
import com.soulaimane.sololeveling.skill.SkillRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /sl skill ...   (the permission level 2 requirement comes from the /sl root command)
 *
 *   list                  every skill with your rank, grouped by branch, and your skill points
 *   unlock <skill>        learn the next rank like a click in the tree does (costs points, rules apply)
 *   set <skill> <rank>    admin: set the rank directly, no cost, no rules (0 removes the skill)
 *   reset                 forget every skill and get all the spent points back
 *   points                show your skill points
 *   points add <n>        give (or with a negative number take away) bonus skill points
 *
 * A skill can be written with or without the namespace: "power_strike".
 */
public final class SkillCommands {
    private SkillCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("skill")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests(SkillCommands::suggestSkills)
                                .executes(ctx -> unlock(ctx.getSource(), StringArgumentType.getString(ctx, "skill")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests(SkillCommands::suggestSkills)
                                .then(Commands.argument("rank", IntegerArgumentType.integer(0, 99))
                                        .executes(ctx -> set(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "skill"),
                                                IntegerArgumentType.getInteger(ctx, "rank"))))))
                .then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource())))
                .then(Commands.literal("points")
                        .executes(ctx -> points(ctx.getSource()))
                        .then(Commands.literal("add")
                                .then(Commands.argument("n", IntegerArgumentType.integer(-1000, 1000))
                                        .executes(ctx -> addPoints(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "n"))))));
    }

    private static CompletableFuture<Suggestions> suggestSkills(CommandContext<CommandSourceStack> ctx,
                                                                SuggestionsBuilder builder) {
        for (Skill skill : SkillRegistry.all()) {
            builder.suggest(skill.location().getPath());
        }
        return builder.buildFuture();
    }

    // ─────────────────────────────────────────────────────────────────

    private static int list(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        PlayerSkillData data = SkillManager.data(p);

        src.sendSuccess(() -> Component.literal("§6═══ Skills ═══  §7skill points: §e"
                + SkillManager.pointsAvailable(p) + " §7free, " + SkillManager.pointsEarned(p) + " earned"), false);

        if (SkillRegistry.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7No skill is registered (see ModSkills)."), false);
            return 1;
        }

        for (SkillBranch branch : SkillBranch.values()) {
            List<Skill> skills = SkillRegistry.branch(branch);
            if (skills.isEmpty()) continue;

            src.sendSuccess(() -> Component.literal("§e" + branch.displayName().getString()), false);
            for (Skill skill : skills) {
                int rank = data.rank(skill.id());
                src.sendSuccess(() -> Component.literal("  " + (rank > 0 ? "§a" : "§7") + skill.location().getPath()
                        + " §8(" + skill.displayName().getString() + ")  §f" + rank + "§7/" + skill.maxRank()
                        + (skill.isActive() ? "  §bactive" : "  §dpassive")), false);
            }
        }
        return 1;
    }

    private static int unlock(CommandSourceStack src, String id) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Skill skill = SkillRegistry.get(id);
        if (skill == null) {
            src.sendFailure(Component.literal("Unknown skill: " + id + " (see /sl skill list)"));
            return 0;
        }

        SkillManager.UnlockResult result = SkillManager.tryUnlock(p, skill.id());
        if (result == SkillManager.UnlockResult.OK) {
            return 1;                                            // the learn message was already sent to the player
        }
        src.sendFailure(Component.literal(explain(result)));
        return 0;
    }

    private static String explain(SkillManager.UnlockResult result) {
        return switch (result) {
            case OK -> "Learned.";
            case UNKNOWN_SKILL -> "Unknown skill.";
            case MAXED -> "That skill is already at its highest rank.";
            case NEEDS_PARENT -> "Learn the skill before it in the tree first.";
            case NEEDS_REQUIREMENTS -> "You do not meet its requirements (open the skill tree with J to see them).";
            case NO_POINTS -> "You have no skill point left (/sl skill points add <n> gives some).";
        };
    }

    private static int set(CommandSourceStack src, String id, int rank) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Skill skill = SkillRegistry.get(id);
        if (skill == null) {
            src.sendFailure(Component.literal("Unknown skill: " + id + " (see /sl skill list)"));
            return 0;
        }

        int clamped = Math.min(rank, skill.maxRank());
        SkillManager.setRank(p, skill, clamped);
        src.sendSuccess(() -> Component.literal("[System] " + skill.displayName().getString()
                + " is now rank " + clamped + "/" + skill.maxRank() + "."), false);
        return 1;
    }

    private static int reset(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        SkillManager.reset(p);
        src.sendSuccess(() -> Component.literal("[System] Every skill was forgotten. You have "
                + SkillManager.pointsAvailable(p) + " skill points to spend."), false);
        return 1;
    }

    private static int points(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        src.sendSuccess(() -> Component.literal("[System] Skill points: " + SkillManager.pointsAvailable(p)
                + " free (" + SkillManager.pointsEarned(p) + " earned)."), false);
        return 1;
    }

    private static int addPoints(CommandSourceStack src, int amount) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        SkillManager.grantPoints(p, amount);
        src.sendSuccess(() -> Component.literal("[System] Skill points " + (amount >= 0 ? "+" : "") + amount
                + " -> " + SkillManager.pointsAvailable(p) + " free."), false);
        return 1;
    }
}
