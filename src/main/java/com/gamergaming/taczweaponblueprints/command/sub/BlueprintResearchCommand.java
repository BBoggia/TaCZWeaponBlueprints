package com.gamergaming.taczweaponblueprints.command.sub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeAuthoringReport;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeEconomyAudit;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeTopologyAudit;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementDiagnostics;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponCandidateClassification;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceManager;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateManager;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardBlueprintFacts;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDataManager;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardEconomyProjection;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCatalogExporter;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDiagnostics;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicyResolver;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTreeGroupDefinition;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTreeGroupPlacement;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;

/** Operator diagnostics and authoring support for the effective research graph. */
public final class BlueprintResearchCommand {
    private static final String BLUEPRINT_ARGUMENT = "blueprint";
    private static final String EXPORT_FILE = "research-catalog.json";

    private BlueprintResearchCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        return Commands.literal("research")
                .then(Commands.literal("status").executes(BlueprintResearchCommand::status))
                .then(BlueprintSetupCommand.get())
                .then(BlueprintResearchPointAwardCommand.get())
                .then(Commands.literal("inspect")
                        .then(Commands.argument(BLUEPRINT_ARGUMENT, ResourceLocationArgument.id())
                                .executes(BlueprintResearchCommand::inspect)))
                .then(Commands.literal("export").executes(BlueprintResearchCommand::exportCatalog));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        BlueprintResearchDataManager manager = BlueprintResearchDataManager.INSTANCE;
        BlueprintResearchDataManager.Publication research = manager.publication();
        BlueprintDataManager.CatalogPublication catalog =
                BlueprintDataManager.SERVER.catalogPublication();
        ResourceLocation profileId = manager.progressionConfig().activeProfileId();
        BlueprintResearchDiagnostics.Summary summary = BlueprintResearchDiagnostics.summarize(
                research.snapshot());
        BlueprintResearchDiagnostics.Audit audit = BlueprintResearchDiagnostics.audit(
                research.snapshot(),
                catalog.blueprints(),
                profileId);
        BlueprintResearchDiagnostics.GroupAudit groupAudit = BlueprintResearchDiagnostics.auditGroups(
                research.snapshot(),
                catalog.blueprints(),
                profileId);
        Optional<AutomaticWeaponPlacementDiagnostics> automatic = automaticDiagnostics(
                research, catalog, profileId);
        Optional<AutomaticWeaponCandidateClassification> branchAnalysis =
                automaticClassification(research, catalog, profileId);
        ResearchTreePublication tree = authoringTreePublication(manager, catalog);
        ResearchTechTreeTopologyAudit.Audit topology = ResearchTechTreeTopologyAudit.audit(
                tree.graph(), tree.techTree(), automatic.orElse(null));
        ResearchPointAwardEconomyProjection.Projection pointIncome =
                ResearchPointAwardEconomyProjection.project(
                        ResearchPointAwardDataManager.INSTANCE.snapshot(),
                        ResearchPointAwardBlueprintFacts.index(
                                catalog.blueprints(), research.snapshot()),
                        profileId);
        ResearchTechTreeEconomyAudit.Audit economy = ResearchTechTreeEconomyAudit.audit(
                tree.graph(), tree.techTree(), pointIncome);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.status",
                profileId,
                audit.assignedBlueprintCount(),
                audit.catalogSize(),
                audit.treeVisibleBlueprintCount(),
                audit.rootCount(),
                audit.componentCount(),
                audit.independentBlueprintIds().size(),
                research.revision()), false);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.definitions",
                summary.profileCount(),
                summary.ruleCount(),
                summary.exactTargetCount(),
                summary.tagTargetCount(),
                summary.selectorTargetCount(),
                audit.missingPrerequisiteIds().size(),
                audit.hiddenPrerequisiteTargetIds().size(),
                audit.competitions().size()), false);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.groups",
                groupAudit.authoredGroupCount(),
                groupAudit.groupedCatalogCount(),
                groupAudit.catalogSize(),
                groupAudit.fallbackBlueprintIds().size(),
                groupAudit.missingMemberIds().size()), false);
        automatic.ifPresentOrElse(diagnostics ->
                context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.taczweaponblueprints.research.automatic",
                        diagnostics.mode().name().toLowerCase(java.util.Locale.ROOT),
                        diagnostics.reviewHandling().serializedName(),
                        diagnostics.treeId(),
                        diagnostics.count(AutomaticWeaponPlacementDiagnostics.State.AUTOMATIC),
                        diagnostics.excludedAutomaticCount(),
                        diagnostics.generatedPrerequisiteCount(),
                        diagnostics.entries().values().stream()
                                .filter(entry -> entry.state()
                                        == AutomaticWeaponPlacementDiagnostics.State.AUTOMATIC)
                                .filter(entry -> entry.reason().isPresent())
                                .count(),
                        diagnostics.topologyWeaponCount(),
                        diagnostics.resolvedNodesPerLayer(),
                        diagnostics.catalogRevision(),
                        diagnostics.researchRevision()), false),
                () -> context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.taczweaponblueprints.research.automatic.unavailable"), false));
        if (automatic.isEmpty()) {
            var automaticPublication =
                    AutomaticWeaponPlacementCandidateManager.INSTANCE.publication();
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.research.automatic.state",
                    automaticPublication.health().state().serializedName(),
                    automaticPublication.catalogRevision(),
                    automaticPublication.researchRevision(),
                    automaticPublication.revision()), false);
            automaticPublication.health().failure().ifPresent(failure ->
                    context.getSource().sendFailure(Component.translatable(
                            "commands.taczweaponblueprints.research.automatic.last_failure",
                            failure.stage().serializedName(),
                            failure.message())));
        }
        automatic.map(AutomaticWeaponPlacementDiagnostics::branchTopologySummary)
                .filter(AutomaticWeaponPlacementDiagnostics.BranchTopologySummary::available)
                .ifPresent(branches -> context.getSource().sendSuccess(() ->
                        Component.translatable(
                                "commands.taczweaponblueprints.research.automatic.prerequisites",
                                branches.branchCount(),
                                branches.familyStartIndex(),
                                branches.transitionEndIndex(),
                                branches.foundationNodeCount(),
                                branches.sharedTrunkNodeCount(),
                                branches.transitionNodeCount(),
                                branches.specializationNodeCount(),
                                branches.sameFamilyEdgeCount(),
                                branches.crossFamilyEdgeCount(),
                                branches.sameFamilyMergeCount(),
                                branches.crossFamilyMergeCount(),
                                branches.depthShortcutCount(),
                                branches.terminalPeerCount(),
                                branches.closureInflationRejectionCount(),
                                branches.maximumFanOut()), false));
        automatic.map(AutomaticWeaponPlacementDiagnostics::publicationSummary)
                .filter(AutomaticWeaponPlacementDiagnostics.PublicationSummary::applicable)
                .ifPresent(publication -> context.getSource().sendSuccess(() ->
                        Component.translatable(
                                "commands.taczweaponblueprints.research.automatic.publication",
                                publication.canonicalBranchCoordinateCount(),
                                publication.candidateCount(),
                                publication.prerequisiteDecisionCount(),
                                publication.candidateCount(),
                                publication.publishedRankCount(),
                                publication.prerequisiteDecisionCount(),
                                publication.complete()), false));
        branchAnalysis.ifPresent(classification -> {
            var model = classification.branchModel();
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.research.automatic.branches",
                    model.branches().size(),
                    model.branches().stream()
                            .filter(branch -> branch.medoidBlueprintId().isEmpty()).count(),
                    model.branchCapacity(),
                    model.branchLimit(),
                    model.seedSignatureCount(),
                    classification.authoredRoleSignatures().size(),
                    model.branches().stream()
                            .mapToInt(branch -> branch.terminalBlueprintIds().size()).sum(),
                    model.branches().stream()
                            .mapToInt(branch -> branch.layoutStrandCount()).sum(),
                    model.branches().stream()
                            .mapToInt(branch -> branch.memberBlueprintIds().size())
                            .max().orElse(0),
                    model.branches().stream()
                            .filter(branch -> branch.terminalCluster().truncated())
                            .count()), false);
        });
        topology.domain(com.gamergaming.taczweaponblueprints.research.tree
                        .ResearchTechTreeContract.Domain.WEAPONS)
                .ifPresent(domain -> context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.taczweaponblueprints.research.topology",
                        domain.rootIds().size(),
                        domain.componentCount(),
                        domain.reachableNodeCount(),
                        domain.nodeCount(),
                        domain.maximumPrerequisiteCount(),
                        domain.maximumDependentCount(),
                        domain.maximumDepth(),
                        domain.maximumRankPopulation(),
                        domain.emptyRankCount(),
                        domain.crossBranchMergeCount(),
                        domain.approximateEdgeCrossingCount(),
                        domain.totalEdgeRankSpan()), false));
        economy.domain(com.gamergaming.taczweaponblueprints.research.tree
                        .ResearchTechTreeContract.Domain.WEAPONS)
                .ifPresent(domain -> context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.taczweaponblueprints.research.economy",
                        domain.fullTreeCost(),
                        domain.minimumLeafUnlockClosureCost(),
                        domain.maximumLeafUnlockClosureCost(),
                        pointIncome.maximumFinitePoints(),
                        domain.finiteIncomeCoverageBasisPoints() / 100.0,
                        domain.andMergeCount(),
                        economy.costAuthority()), false));
        return Command.SINGLE_SUCCESS;
    }

    private static int inspect(CommandContext<CommandSourceStack> context) {
        ResourceLocation blueprintId = ResourceLocationArgument.getId(context, BLUEPRINT_ARGUMENT);
        BlueprintResearchDataManager manager = BlueprintResearchDataManager.INSTANCE;
        BlueprintResearchDataManager.Publication research = manager.publication();
        BlueprintDataManager.CatalogPublication catalog =
                BlueprintDataManager.SERVER.catalogPublication();
        if (!catalog.blueprints().containsKey(blueprintId)) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.research.inspect.missing", blueprintId));
            return 0;
        }
        ResourceLocation profileId = manager.progressionConfig().activeProfileId();
        BlueprintResearchPolicy policy = manager.policyFor(profileId, blueprintId, null);
        BlueprintResearchPolicyResolver.RuleSelection selection = BlueprintResearchDiagnostics.inspectSelection(
                research.snapshot(), catalog.blueprints(), profileId, blueprintId);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.inspect",
                blueprintId,
                policy.ruleId().map(ResourceLocation::toString).orElse("inherited profile"),
                selection.specificity().name().toLowerCase(java.util.Locale.ROOT),
                policy.visibility().serializedName(),
                policy.researchEnabled(),
                policy.researchCost().points(),
                policy.researchCost().ingredients().size(),
                policy.prerequisites().size(),
                selection.tiedRuleIds().size()), false);
        java.util.Optional<ResearchTreeGroupPlacement> placement = research.snapshot()
                .placementFor(profileId, blueprintId);
        boolean includedInOverview = placement
                .map(value -> research.snapshot().groups().get(value.groupId()))
                .flatMap(ResearchTreeGroupDefinition::includeInOverview)
                .orElse(placement.isPresent());
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.inspect.presentation",
                placement.map(value -> value.groupId().toString()).orElse("automatic fallback"),
                placement.map(value -> Integer.toString(value.rank())).orElse("-"),
                placement.map(value -> Integer.toString(value.orderInRank())).orElse("-"),
                includedInOverview), false);
        automaticDiagnostics(research, catalog, profileId)
                .flatMap(diagnostics -> diagnostics.entry(blueprintId))
                .ifPresent(entry -> {
                    context.getSource().sendSuccess(() -> {
                    var proposal = entry.proposal();
                    return Component.translatable(
                            "commands.taczweaponblueprints.research.inspect.automatic",
                            entry.state().serializedName(),
                            proposal.map(value -> Integer.toString(value.mechanicalScore())).orElse("-"),
                            proposal.map(value -> Integer.toString(value.confidence())).orElse("-"),
                            proposal.map(value -> value.position().tier().name().toLowerCase(
                                    java.util.Locale.ROOT)).orElse("-"),
                            proposal.map(value -> Integer.toString(value.position().level() + 1)).orElse("-"),
                            proposal.map(value -> Long.toString(value.position().siblingOrder())).orElse("-"),
                            proposal.map(value -> value.reviewReasons().isEmpty()
                                    ? "-"
                                    : String.join(",", value.reviewReasons())).orElse("-"),
                            entry.generatedPrerequisites().isEmpty()
                                    ? "-"
                                    : entry.generatedPrerequisites().stream()
                                            .map(ResourceLocation::toString)
                                            .collect(java.util.stream.Collectors.joining(",")),
                            entry.reason().orElse("-"));
                    }, false);
                    entry.prerequisiteDecision().ifPresent(decision ->
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "commands.taczweaponblueprints.research.inspect.prerequisite",
                                    decision.strategy().serializedName(),
                                    decision.branchIndex().map(String::valueOf).orElse("-"),
                                    decision.rankIndex(),
                                    decision.publishedRank().map(String::valueOf).orElse("-"),
                                    decision.familyStartIndex(),
                                    decision.transitionEndIndex(),
                                    decision.desiredParentCount(),
                                    decision.selectedParentRelations().size(),
                                    decision.sameFamilyParentCount(),
                                    decision.crossFamilyParentCount(),
                                    decision.depthShortcut(),
                                    decision.terminalPeer()), false));
                });
        automaticClassification(research, catalog, profileId).ifPresent(classification -> {
            var role = classification.roleSignature(blueprintId)
                    .or(() -> classification.authoredRoleSignature(blueprintId));
            var branch = classification.branchModel().branchFor(blueprintId.toString())
                    .or(() -> classification.branchModel().branches().stream()
                            .filter(value -> value.authoredAnchorBlueprintIds()
                                    .contains(blueprintId.toString()))
                            .findFirst());
            role.ifPresent(signature -> context.getSource().sendSuccess(() ->
                    Component.translatable(
                            "commands.taczweaponblueprints.research.inspect.branch",
                            signature.archetype(),
                            signature.maySeedBranch(),
                            signature.branchSeedBlockReasons().isEmpty()
                                    ? "-"
                                    : String.join(",", signature.branchSeedBlockReasons()),
                            branch.map(value -> Integer.toString(value.index())).orElse("-"),
                            branch.map(value -> value.stableKey()).orElse("-"),
                            branch.flatMap(value -> value.medoidBlueprintId()).orElse("-"),
                            branch.map(value -> Integer.toString(
                                    value.memberBlueprintIds().size())).orElse("-"),
                            branch.map(value -> Integer.toString(
                                    value.terminalBlueprintIds().size())).orElse("-"),
                            branch.map(value -> Integer.toString(
                                    value.layoutStrandCount())).orElse("-"),
                            branch.map(value -> value.terminalCluster().resolution()
                                    .serializedName()).orElse("-"),
                            branch.map(value -> Integer.toString(value.terminalCluster()
                                    .adaptiveScoreTolerance())).orElse("-"),
                            branch.map(value -> Integer.toString(value.terminalCluster()
                                    .equivalentCandidateCount())).orElse("-"),
                            branch.map(value -> Integer.toString(value.terminalCluster()
                                    .deferredEquivalentCount())).orElse("-"),
                            branch.flatMap(value -> value.terminalCluster().diagnostic())
                                    .orElse("-")), false));
        });
        Optional<AutomaticWeaponPlacementDiagnostics> automatic = automaticDiagnostics(
                research, catalog, profileId);
        ResearchTechTreeAuthoringReport.create(
                        research.snapshot(),
                        catalog.blueprints(),
                        profileId,
                        automatic.orElse(null),
                        AutomaticWeaponEvidenceManager.INSTANCE.snapshotForCatalogRevision(
                                catalog.revision()))
                .entries().entrySet().stream()
                .filter(entry -> entry.getKey().equals(blueprintId))
                .map(java.util.Map.Entry::getValue)
                .findFirst()
                .ifPresent(entry -> context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.taczweaponblueprints.research.inspect.authoring",
                        entry.assignedRank().map(String::valueOf).orElse("-"),
                        entry.bandId().map(ResourceLocation::toString).orElse("-"),
                        entry.parentChoices().isEmpty() ? "-" : entry.parentChoices().stream()
                                .map(value -> value.parentId().toString())
                                .collect(java.util.stream.Collectors.joining(",")),
                        entry.similarityScore().map(String::valueOf).orElse("-"),
                        entry.fanOutPenalty(),
                        entry.parentChoiceReason(),
                        entry.mergeReason(),
                        entry.reviewFallbackReasons().isEmpty() ? "-"
                                : String.join(",", entry.reviewFallbackReasons())), false));
        return Command.SINGLE_SUCCESS;
    }

    private static int exportCatalog(CommandContext<CommandSourceStack> context) {
        BlueprintResearchDataManager manager = BlueprintResearchDataManager.INSTANCE;
        BlueprintResearchDataManager.Publication research = manager.publication();
        BlueprintDataManager.CatalogPublication catalog =
                BlueprintDataManager.SERVER.catalogPublication();
        ResourceLocation profileId = manager.progressionConfig().activeProfileId();
        Path directory = context.getSource().getServer().getWorldPath(LevelResource.ROOT)
                .resolve("taczweaponblueprints");
        Path target = directory.resolve(EXPORT_FILE);
        Path temporary = directory.resolve(EXPORT_FILE + ".tmp");
        Optional<AutomaticWeaponPlacementDiagnostics> automatic = automaticDiagnostics(
                research, catalog, profileId);
        ResearchTreePublication tree = authoringTreePublication(manager, catalog);
        ResearchTechTreeTopologyAudit.Audit topology = ResearchTechTreeTopologyAudit.audit(
                tree.graph(), tree.techTree(), automatic.orElse(null));
        ResearchPointAwardEconomyProjection.Projection pointIncome =
                ResearchPointAwardEconomyProjection.project(
                        ResearchPointAwardDataManager.INSTANCE.snapshot(),
                        ResearchPointAwardBlueprintFacts.index(
                                catalog.blueprints(), research.snapshot()),
                        profileId);
        ResearchTechTreeEconomyAudit.Audit economy = ResearchTechTreeEconomyAudit.audit(
                tree.graph(), tree.techTree(), pointIncome);
        ResearchTechTreeAuthoringReport authoring = ResearchTechTreeAuthoringReport.create(
                research.snapshot(),
                catalog.blueprints(),
                profileId,
                automatic.orElse(null),
                AutomaticWeaponEvidenceManager.INSTANCE.snapshotForCatalogRevision(
                        catalog.revision()));
        String json = BlueprintResearchCatalogExporter.exportWithDiagnostics(
                research.snapshot(),
                catalog.blueprints(),
                profileId,
                automatic.orElse(null),
                authoring,
                topology,
                economy);
        try {
            Files.createDirectories(directory);
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.research.export.failed", exception.getMessage()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.export.success", target.toAbsolutePath()), false);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Builds a detached, fully disclosed view so operator topology and cost
     * evidence cannot be changed by the current undiscovered-visibility preset.
     */
    private static ResearchTreePublication authoringTreePublication(
            BlueprintResearchDataManager manager,
            BlueprintDataManager.CatalogPublication catalog) {
        PlayerRecipeData disclosed = new PlayerRecipeData();
        List<String> blueprintIds = catalog.blueprints().keySet().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList();
        if (!disclosed.replaceProgression(blueprintIds, blueprintIds, 0)) {
            throw new IllegalStateException(
                    "The bounded live catalog could not form an authoring tree view");
        }
        return manager.treePublicationFor(disclosed);
    }

    private static Optional<AutomaticWeaponPlacementDiagnostics> automaticDiagnostics(
            BlueprintResearchDataManager.Publication research,
            BlueprintDataManager.CatalogPublication catalog,
            ResourceLocation profileId) {
        return Optional.ofNullable(research.snapshot().profiles().get(profileId))
                .flatMap(profile -> profile.techTree())
                .flatMap(treeId -> AutomaticWeaponPlacementCandidateManager.INSTANCE
                        .diagnosticsFor(
                                profileId,
                                treeId,
                                catalog.revision(),
                                research.revision()));
    }

    private static Optional<AutomaticWeaponCandidateClassification> automaticClassification(
            BlueprintResearchDataManager.Publication research,
            BlueprintDataManager.CatalogPublication catalog,
            ResourceLocation profileId) {
        return Optional.ofNullable(research.snapshot().profiles().get(profileId))
                .flatMap(profile -> profile.techTree())
                .flatMap(treeId -> AutomaticWeaponPlacementCandidateManager.INSTANCE
                        .classificationFor(
                                treeId,
                                catalog.revision(),
                                research.revision()));
    }
}
