package com.ecolibrarianfinder.command;

import com.ecolibrarianfinder.EcoLibrarianFinderMod;
import com.ecolibrarianfinder.config.EcoFinderConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Registers /ecofind commands:
 *
 *   /ecofind select              – select lectern + villager under crosshair
 *   /ecofind start               – start the search loop
 *   /ecofind stop                – stop the search loop
 *   /ecofind add <id> <lvl> [price]  – add an enchantment goal
 *   /ecofind remove <id> <lvl>   – remove a goal
 *   /ecofind list                – list current goals
 *   /ecofind clear               – clear all goals
 *   /ecofind status              – show current state
 */
public class EcoFinderCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {

        dispatcher.register(literal("ecofind")

                // ── select ────────────────────────────────────────────────────
                .then(literal("select").executes(ctx -> {
                    EcoLibrarianFinderMod.searchState.selectTarget(ctx.getSource().getClient());
                    return 1;
                }))

                // ── start ─────────────────────────────────────────────────────
                .then(literal("start").executes(ctx -> {
                    EcoLibrarianFinderMod.searchState.start(ctx.getSource().getClient());
                    return 1;
                }))

                // ── stop ──────────────────────────────────────────────────────
                .then(literal("stop").executes(ctx -> {
                    EcoLibrarianFinderMod.searchState.stop(ctx.getSource().getClient());
                    return 1;
                }))

                // ── add <enchant_id> <level> [maxPrice] ───────────────────────
                .then(literal("add")
                        .then(argument("enchant", StringArgumentType.word())
                                .then(argument("level", IntegerArgumentType.integer(1, 20))
                                        .executes(ctx -> cmdAdd(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "enchant"),
                                                IntegerArgumentType.getInteger(ctx, "level"),
                                                64))
                                        .then(argument("maxPrice", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> cmdAdd(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "enchant"),
                                                        IntegerArgumentType.getInteger(ctx, "level"),
                                                        IntegerArgumentType.getInteger(ctx, "maxPrice")))))))

                // ── remove <enchant_id> <level> ───────────────────────────────
                .then(literal("remove")
                        .then(argument("enchant", StringArgumentType.word())
                                .then(argument("level", IntegerArgumentType.integer(1, 20))
                                        .executes(ctx -> cmdRemove(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "enchant"),
                                                IntegerArgumentType.getInteger(ctx, "level"))))))

                // ── list ──────────────────────────────────────────────────────
                .then(literal("list").executes(ctx -> {
                    var goals = EcoLibrarianFinderMod.config.goals;
                    if (goals.isEmpty()) {
                        ctx.getSource().sendFeedback(Text.literal("§7No goals set. Use §f/ecofind add§7."));
                    } else {
                        ctx.getSource().sendFeedback(Text.literal("§aGoals:"));
                        for (EcoFinderConfig.GoalEntry g : goals) {
                            ctx.getSource().sendFeedback(Text.literal("  §7- §f" + g));
                        }
                    }
                    return 1;
                }))

                // ── clear ─────────────────────────────────────────────────────
                .then(literal("clear").executes(ctx -> {
                    EcoLibrarianFinderMod.config.clearGoals();
                    ctx.getSource().sendFeedback(Text.literal("§7All goals cleared."));
                    return 1;
                }))

                // ── status ────────────────────────────────────────────────────
                .then(literal("status").executes(ctx -> {
                    var state = EcoLibrarianFinderMod.searchState;
                    String running = state.isRunning() ? "§aRunning" : "§7Idle";
                    String pos = state.getLecternPos() != null ? state.getLecternPos().toString() : "none";
                    ctx.getSource().sendFeedback(Text.literal(
                            "§aEcoLibrarianFinder Status\n" +
                            "  State: " + running + "\n" +
                            "§7  Lectern: §f" + pos + "\n" +
                            "§7  Attempts: §e" + state.getAttempts() + "\n" +
                            "§7  Goals: §e" + EcoLibrarianFinderMod.config.goals.size()
                    ));
                    return 1;
                }))
        );
    }

    private static int cmdAdd(FabricClientCommandSource source, String enchant, int level, int maxPrice) {
        boolean added = EcoLibrarianFinderMod.config.addGoal(enchant, level, maxPrice);
        if (added) {
            source.sendFeedback(Text.literal(
                    "§aAdded goal: §f" + enchant + " " + level + " §7(max price: " + maxPrice + ")"));
        } else {
            source.sendFeedback(Text.literal("§e" + enchant + " level " + level + " is already in your goals."));
        }
        return 1;
    }

    private static int cmdRemove(FabricClientCommandSource source, String enchant, int level) {
        boolean removed = EcoLibrarianFinderMod.config.removeGoal(enchant, level);
        source.sendFeedback(removed
                ? Text.literal("§7Removed §f" + enchant + " " + level + "§7 from goals.")
                : Text.literal("§cGoal not found: " + enchant + " " + level));
        return 1;
    }
}
