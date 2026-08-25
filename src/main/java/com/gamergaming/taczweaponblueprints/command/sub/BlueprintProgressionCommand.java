package com.gamergaming.taczweaponblueprints.command.sub;

import java.util.Collection;
import java.util.List;

import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.PlayerProgressionAdminService;
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
    private static final List<String> RESET_STATES = List.of("learned", "discovered", "points", "all");

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
                                        .executes(BlueprintProgressionCommand::reset))));
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
                NetworkHandler.syncPlayerRecipeData(player);
            }
        }

        int result = changed;
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.progression.reset.success",
                state.serializedName(),
                result), true);
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
