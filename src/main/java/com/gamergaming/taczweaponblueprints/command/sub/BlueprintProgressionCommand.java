package com.gamergaming.taczweaponblueprints.command.sub;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.api.ProgressionCriteria;
import com.gamergaming.taczweaponblueprints.api.ProgressionCriteria.ChangeOperation;
import com.gamergaming.taczweaponblueprints.api.ProgressionCriteria.ChangeResult;
import com.gamergaming.taczweaponblueprints.api.ProgressionCriteria.Status;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.PlayerProgressionAdminService;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardReconciliationScheduler;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateEvaluator;
import com.gamergaming.taczweaponblueprints.progression.workbench.CraftingWorkbenchTierResolver;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchInteractionMode;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.ProgressionPolicyAccessService;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
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
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Explicit operator recovery tools for durable blueprint progression. */
public final class BlueprintProgressionCommand {
    private static final List<String> RESET_STATES =
            List.of("learned", "discovered", "points", "awards", "fragments", "criteria", "all");

    private BlueprintProgressionCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        return Commands.literal("progression")
                .then(Commands.literal("inspect")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(BlueprintProgressionCommand::inspect)
                                .then(Commands.argument(
                                                "blueprint",
                                                ResourceLocationArgument.id())
                                        .executes(BlueprintProgressionCommand::inspectBlueprint))))
                .then(Commands.literal("workstation")
                        .then(Commands.argument(
                                        "workstation",
                                        ResourceLocationArgument.id())
                                .executes(BlueprintProgressionCommand::inspectWorkstation)))
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
                                                .executes(BlueprintProgressionCommand::givePoints)))))
                .then(criteriaCommands());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> criteriaCommands() {
        return Commands.literal("criteria")
                .then(Commands.literal("inspect")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument(
                                                "criterion",
                                                ResourceLocationArgument.id())
                                        .executes(BlueprintProgressionCommand::inspectCriterion))))
                .then(Commands.literal("grant")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument(
                                                "criterion",
                                                ResourceLocationArgument.id())
                                        .executes(context -> changeCriterion(
                                                context, ChangeOperation.GRANT, 1))
                                        .then(Commands.argument(
                                                        "value",
                                                        IntegerArgumentType.integer(
                                                                1,
                                                                PlayerProgressionLimits
                                                                        .MAX_PROGRESS_VALUE))
                                                .executes(context -> changeCriterion(
                                                        context,
                                                        ChangeOperation.GRANT,
                                                        IntegerArgumentType.getInteger(
                                                                context, "value")))))))
                .then(Commands.literal("increment")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument(
                                                "criterion",
                                                ResourceLocationArgument.id())
                                        .then(Commands.argument(
                                                        "amount",
                                                        IntegerArgumentType.integer(
                                                                1,
                                                                PlayerProgressionLimits
                                                                        .MAX_PROGRESS_VALUE))
                                                .executes(context -> changeCriterion(
                                                        context,
                                                        ChangeOperation.INCREMENT,
                                                        IntegerArgumentType.getInteger(
                                                                context, "amount")))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument(
                                                "criterion",
                                                ResourceLocationArgument.id())
                                        .executes(context -> changeCriterion(
                                                context,
                                                ChangeOperation.ADMINISTRATIVE_CLEAR,
                                                0)))));
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
                    snapshot.researchPoints(),
                    snapshot.fragmentTargets(),
                    snapshot.archivedFragments(),
                    snapshot.progressionCriteria()), false);
            return 1;
        }).orElseGet(() -> {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.progression.data_unavailable",
                    player.getDisplayName()));
            return 0;
        });
    }

    private static int inspectBlueprint(
            CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation blueprintId = ResourceLocationArgument.getId(context, "blueprint");
        var policyAccess = ProgressionPolicyAccessService.acquire(
                        ProgressionPolicyAccessService.Mode.ENSURE_CURRENT)
                .orElse(null);
        if (policyAccess == null
                || !policyAccess.catalog().blueprints().containsKey(blueprintId)) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.progression.inspect_blueprint.unavailable",
                    blueprintId));
            return 0;
        }
        return player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).map(data -> {
            var researchPolicy = policyAccess.policyFor(blueprintId);
            var craftingPolicy = policyAccess.craftingPolicyFor(blueprintId).orElse(null);
            if (craftingPolicy == null) {
                context.getSource().sendFailure(Component.translatable(
                        "commands.taczweaponblueprints.progression.inspect_blueprint.unavailable",
                        blueprintId));
                return 0;
            }
            Map<String, Integer> archivedByTarget = data.getArchivedBlueprintFragments();
            Integer archivedValue = archivedByTarget == null
                    ? null
                    : archivedByTarget.getOrDefault(blueprintId.toString(), 0);
            if (archivedValue == null
                    || archivedValue < 0
                    || archivedValue > com.gamergaming.taczweaponblueprints.progression
                            .fragment.BlueprintFragmentPolicy.MAX_ARCHIVED_FRAGMENTS) {
                context.getSource().sendFailure(Component.translatable(
                        "commands.taczweaponblueprints.progression.data_unavailable",
                        player.getDisplayName()));
                return 0;
            }
            int archived = archivedValue;
            researchPolicy.ifPresentOrElse(policy -> {
                var gates = ProgressionGateEvaluator.evaluateBlueprint(
                        player,
                        blueprintId,
                        ResearchInteractionMode.RESEARCH,
                        java.util.Optional.empty());
                context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.taczweaponblueprints.progression.inspect_blueprint.research",
                        player.getDisplayName(),
                        blueprintId,
                        policy.tierSource().name().toLowerCase(java.util.Locale.ROOT),
                        policy.researchWorkbenchTier().serializedName(),
                        archived,
                        policy.fragments().threshold(),
                        policy.fragments().completionMode().name().toLowerCase(
                                java.util.Locale.ROOT),
                        policy.gates().allOf().size(),
                        gates.unmetGroups().size()), false);
            }, () -> context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.progression.inspect_blueprint.research_omitted",
                    player.getDisplayName(), blueprintId), false));
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.progression.inspect_blueprint.crafting",
                    craftingPolicy.disposition().serializedName(),
                    craftingPolicy.requiredWorkbenchTier()
                            .map(value -> Integer.toString(value.level())).orElse("-"),
                    craftingPolicy.source().serializedName(),
                    craftingPolicy.selectedRuleId().map(ResourceLocation::toString).orElse("-"),
                    craftingPolicy.ruleSpecificity().name().toLowerCase(java.util.Locale.ROOT),
                    craftingPolicy.reviewRequired(),
                    craftingPolicy.gates().allOf().size(),
                    craftingPolicy.gates().conditionCount(),
                    craftingPolicy.reasonCode(),
                    craftingPolicy.warnings().isEmpty()
                            ? "-"
                            : craftingPolicy.warnings().stream()
                                    .map(value -> value.serializedName())
                                    .sorted()
                                    .collect(java.util.stream.Collectors.joining(","))), false);
            return 1;
        }).orElseGet(() -> {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.progression.data_unavailable",
                    player.getDisplayName()));
            return 0;
        });
    }

    private static int inspectWorkstation(CommandContext<CommandSourceStack> context) {
        ResourceLocation workstationId = ResourceLocationArgument.getId(
                context, "workstation");
        var resolution = CraftingWorkbenchTierResolver.resolve(
                workstationId, ModConfigs.BLUEPRINT.researchFeatureSnapshot());
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.progression.workstation",
                workstationId,
                resolution.tier().serializedName(),
                resolution.source().name().toLowerCase(java.util.Locale.ROOT),
                resolution.unrestricted()), false);
        return 1;
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

    private static int inspectCriterion(
            CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation criterionId = ResourceLocationArgument.getId(context, "criterion");
        ProgressionCriteria.Inspection result = ProgressionCriteria.inspect(player, criterionId);
        if (!result.successful()) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.progression.criteria.inspect.failed",
                    criterionId,
                    player.getDisplayName(),
                    criterionFailure(result.status())));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.progression.criteria.inspect.success",
                criterionId,
                player.getDisplayName(),
                result.value()), false);
        return 1;
    }

    private static int changeCriterion(
            CommandContext<CommandSourceStack> context,
            ChangeOperation operation,
            int operand) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        ResourceLocation criterionId = ResourceLocationArgument.getId(context, "criterion");
        int changed = 0;
        int unchanged = 0;
        Map<Status, Integer> failures = new EnumMap<>(Status.class);
        for (ServerPlayer player : players) {
            ChangeResult result = switch (operation) {
                case GRANT -> ProgressionCriteria.grant(player, criterionId, operand);
                case INCREMENT -> ProgressionCriteria.increment(player, criterionId, operand);
                case ADMINISTRATIVE_CLEAR -> ProgressionCriteria.clearFromCommand(
                        context.getSource(), player, criterionId);
            };
            if (result.changed()) {
                changed++;
            } else if (result.status() == Status.UNCHANGED) {
                unchanged++;
            } else {
                failures.merge(result.status(), 1, Integer::sum);
            }
        }

        int changedCount = changed;
        int unchangedCount = unchanged;
        Component operationName = Component.translatable(switch (operation) {
            case GRANT -> "commands.taczweaponblueprints.progression.criteria.operation.grant";
            case INCREMENT -> "commands.taczweaponblueprints.progression.criteria.operation.increment";
            case ADMINISTRATIVE_CLEAR ->
                    "commands.taczweaponblueprints.progression.criteria.operation.reset";
        });
        if (changedCount + unchangedCount > 0) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.progression.criteria.change.success",
                    operationName,
                    criterionId,
                    changedCount,
                    unchangedCount), true);
        }
        failures.forEach((status, count) -> context.getSource().sendFailure(Component.translatable(
                "commands.taczweaponblueprints.progression.criteria.change.failed",
                criterionId,
                count,
                criterionFailure(status))));
        return changed;
    }

    private static Component criterionFailure(Status status) {
        return Component.translatable(
                "commands.taczweaponblueprints.progression.criteria.status."
                        + status.serializedName());
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
