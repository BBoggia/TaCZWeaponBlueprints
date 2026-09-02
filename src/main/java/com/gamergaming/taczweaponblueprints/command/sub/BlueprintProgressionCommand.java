package com.gamergaming.taczweaponblueprints.command.sub;

import java.util.Collection;
import java.util.List;

import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.PlayerProgressionAdminService;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardReconciliationScheduler;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.gamergaming.taczweaponblueprints.progression.PlayerProgressionAdminService.ResetState;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Explicit operator recovery tools for durable blueprint progression. */
public final class BlueprintProgressionCommand {
    private static final List<String> RESET_STATES =
            List.of("learned", "discovered", "points", "awards", "all");

    private BlueprintProgressionCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        return Commands.literal("progression")
                .then(Commands.literal("inspect")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(BlueprintProgressionCommand::inspect)))
                .then(Commands.literal("reset")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("state", StringArgumentType.word())
                                        .suggests(BlueprintProgressionCommand::suggestStates)
                                        .executes(BlueprintProgressionCommand::reset))))
                .then(Commands.literal("points")
                        .then(Commands.literal("give")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument(
                                                "amount",
                                                IntegerArgumentType.integer(
                                                        1,
                                                        PlayerProgressionLimits.MAX_RESEARCH_POINTS))
                                                .executes(BlueprintProgressionCommand::givePoints)))));
    }

    private static int inspect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        return player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).map(data -> {
            var snapshot = PlayerProgressionAdminService.inspect(data);
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.progression.inspect",
                    player.getDisplayName(),
                    snapshot.learnedBlueprints(),
                    snapshot.discoveredBlueprints(),
                    snapshot.legacyRecipes(),
                    snapshot.researchPoints()), false);
            return 1;
        }).orElseGet(() -> {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.progression.data_unavailable",
                    player.getDisplayName()));
            return 0;
        });
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        String stateName = StringArgumentType.getString(context, "state");
        ResetState state = ResetState.parse(stateName).orElse(null);
        if (state == null) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.progression.reset.invalid_state"));
            return 0;
        }

        int changed = 0;
        for (ServerPlayer player : players) {
            boolean updated = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                    .map(data -> PlayerProgressionAdminService.reset(data, state))
                    .orElse(false);
            if (updated) {
                changed++;
                NetworkHandler.clearOpenResearchBenchSelection(player);
                NetworkHandler.syncPlayerRecipeData(player);
                ResearchPointPresentationService.syncHelp(player);
                // Replace any queued retroactive work captured from the old
                // progression or ledger state. A points reset also wakes
                // legitimately unclaimed require-full awards immediately.
                ResearchPointAwardReconciliationScheduler.schedule(player);
            }
        }

        int result = changed;
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.progression.reset.success",
                state.serializedName(),
                result), true);
        return changed;
    }

    private static int givePoints(
            CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        int pointCap = ModConfigs.BLUEPRINT.progressionSnapshot().pointCap();
        int changed = 0;
        int capped = 0;
        int unavailable = 0;
        for (ServerPlayer player : players) {
            var data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve();
            if (data.isEmpty()) {
                unavailable++;
                continue;
            }
            if (!PlayerProgressionAdminService.giveResearchPoints(
                    data.orElseThrow(), amount, pointCap)) {
                capped++;
                continue;
            }
            changed++;
            NetworkHandler.syncPlayerPointBalance(player);
        }

        int granted = changed;
        if (granted > 0) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.progression.points.give.success",
                    amount,
                    granted), true);
        }
        if (capped > 0) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.progression.points.give.capped",
                    capped,
                    amount,
                    pointCap));
        }
        if (unavailable > 0) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.progression.points.give.unavailable",
                    unavailable));
        }
        return changed;
    }

    private static java.util.concurrent.CompletableFuture<Suggestions> suggestStates(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        RESET_STATES.stream()
                .filter(value -> value.startsWith(remaining))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }
}
