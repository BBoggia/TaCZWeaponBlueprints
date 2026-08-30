package com.gamergaming.taczweaponblueprints.command.sub;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;

import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDataManager;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDiagnostics;
import com.gamergaming.taczweaponblueprints.api.ResearchPointAwards;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Permission-gated diagnostics for the independent RP award publication. */
public final class BlueprintResearchPointAwardCommand {
    private static final String DEFINITION_ARGUMENT = "definition";

    private BlueprintResearchPointAwardCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        return Commands.literal("awards")
                .then(Commands.literal("status")
                        .executes(BlueprintResearchPointAwardCommand::status))
                .then(Commands.literal("sources")
                        .executes(BlueprintResearchPointAwardCommand::sources))
                .then(Commands.literal("trigger")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("source", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider
                                                .suggestResource(
                                                        knownSources(), builder))
                                        .executes(BlueprintResearchPointAwardCommand::trigger))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument(DEFINITION_ARGUMENT, ResourceLocationArgument.id())
                                .executes(BlueprintResearchPointAwardCommand::inspect)));
    }

    private static int sources(CommandContext<CommandSourceStack> context) {
        var sources = ResearchPointAwards.registeredSources();
        String values = sources.stream().map(ResourceLocation::toString)
                .collect(Collectors.joining(", "));
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.awards.sources",
                sources.size(), values.isEmpty() ? "none" : values), false);
        var configured = ResearchPointAwardDiagnostics.integrationSourceIds(
                ResearchPointAwardDataManager.INSTANCE.snapshot());
        String configuredValues = configured.stream().map(ResourceLocation::toString)
                .collect(Collectors.joining(", "));
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.awards.sources.configured",
                configured.size(), configuredValues.isEmpty() ? "none" : configuredValues), false);
        return Command.SINGLE_SUCCESS;
    }

    private static java.util.List<ResourceLocation> knownSources() {
        return java.util.stream.Stream.concat(
                        ResearchPointAwards.registeredSources().stream(),
                        ResearchPointAwardDiagnostics.integrationSourceIds(
                                ResearchPointAwardDataManager.INSTANCE.snapshot()).stream())
                .distinct()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    private static int trigger(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<net.minecraft.server.level.ServerPlayer> players =
                EntityArgument.getPlayers(context, "targets");
        ResourceLocation sourceId = ResourceLocationArgument.getId(context, "source");
        int successful = 0;
        long awardedPoints = 0L;
        java.util.Map<ResearchPointAwards.Status, Integer> outcomes =
                new java.util.EnumMap<>(ResearchPointAwards.Status.class);
        for (var player : players) {
            ResearchPointAwards.Result result = ResearchPointAwards.triggerFromCommand(
                    context.getSource(), player, sourceId);
            if (result.successful()) {
                successful++;
                awardedPoints += result.awardedPoints();
            }
            outcomes.merge(result.status(), 1, Integer::sum);
        }
        int successes = successful;
        long points = awardedPoints;
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.awards.trigger",
                sourceId, successes, players.size(), points), true);
        String outcomeSummary = outcomes.entrySet().stream()
                .map(entry -> entry.getKey().serializedName() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.awards.trigger.outcomes",
                outcomeSummary), false);
        return successful;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        ResearchPointAwardDataManager manager = ResearchPointAwardDataManager.INSTANCE;
        ResearchPointAwardDiagnostics.Summary summary =
                ResearchPointAwardDiagnostics.summarize(manager.snapshot());
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.awards.status",
                manager.revision(),
                summary.enabledDefinitionCount(),
                summary.definitionCount(),
                summary.awardGroupCount(),
                summary.budgetCount(),
                summary.targetBindingCount()), false);
        String triggers = summary.triggerCounts().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().serializedName() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.awards.triggers",
                triggers.isEmpty() ? "none" : triggers), false);
        var config = ModConfigs.BLUEPRINT.awardSnapshot();
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.awards.config",
                config.awardsEnabled(),
                config.combatAwardsEnabled(),
                config.pointCap(),
                config.activeProfileId()), false);
        manager.lastFailure().ifPresent(failure -> context.getSource().sendFailure(
                Component.translatable(
                        "commands.taczweaponblueprints.research.awards.last_failure",
                        failure.message())));
        return Command.SINGLE_SUCCESS;
    }

    private static int inspect(CommandContext<CommandSourceStack> context) {
        ResourceLocation definitionId = ResourceLocationArgument.getId(context, DEFINITION_ARGUMENT);
        ResearchPointAwardDiagnostics.Inspection inspection = ResearchPointAwardDiagnostics.inspect(
                ResearchPointAwardDataManager.INSTANCE.snapshot(), definitionId).orElse(null);
        if (inspection == null) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.research.awards.inspect.missing",
                    definitionId));
            return 0;
        }
        String profiles = inspection.profiles().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .map(ResourceLocation::toString)
                .collect(Collectors.joining(","));
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.awards.inspect",
                inspection.definitionId(),
                inspection.enabled(),
                inspection.triggerType().serializedName(),
                inspection.awardGroup(),
                inspection.priority(),
                inspection.points(),
                inspection.overflow().name().toLowerCase(Locale.ROOT),
                inspection.repeat().name().toLowerCase(Locale.ROOT)), false);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.awards.inspect.scope",
                profiles.isEmpty() ? "all" : profiles,
                inspection.budgetId().map(ResourceLocation::toString).orElse("none"),
                inspection.targetTerms()), false);
        return Command.SINGLE_SUCCESS;
    }
}
