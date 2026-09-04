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
import com.gamergaming.taczweaponblueprints.research.tree.ResearchGroupedRouteBaselineAudit;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchGroupedRouteMotifAssessment;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchGroupedRouteQualityAudit;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementDiagnostics;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementProposal;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.PrerequisiteStrategy;
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
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintProgressionPolicyManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintProgressionPolicySnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintCraftingPolicySnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.ProgressionPolicyAccessService;
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
                tree.graph(), tree.techTree(), pointIncome,
                manager.progressionConfig().researchCostMode());
        ResearchGroupedRouteBaselineAudit.Audit groupedRouteBaseline =
                ResearchGroupedRouteBaselineAudit.audit(
                        tree.graph(), tree.techTree(), automatic.orElse(null), pointIncome);
        ResearchGroupedRouteQualityAudit.Audit groupedRouteQuality =
                ResearchGroupedRouteQualityAudit.audit(
                        tree.graph(), tree.techTree(), automatic.orElse(null), pointIncome);
        ResearchGroupedRouteMotifAssessment.Assessment motifAssessment =
                ResearchGroupedRouteMotifAssessment.assess(
                        groupedRouteQuality, topology);
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
        var progressionAccess = ProgressionPolicyAccessService.acquire(
                ProgressionPolicyAccessService.Mode.CURRENT_ONLY).orElse(null);
        var progressionPublication = progressionAccess == null
                ? BlueprintProgressionPolicyManager.INSTANCE.publication()
                : progressionAccess.policy();
        Optional<com.gamergaming.taczweaponblueprints.resource.research
                .BlueprintProgressionPolicySnapshot.ProfileDiagnostics> progressionDiagnostics =
                progressionAccess != null
                        ? Optional.ofNullable(progressionPublication.snapshot()
                                .diagnosticsByProfile().get(profileId))
                        : Optional.empty();
        progressionDiagnostics.ifPresentOrElse(value ->
                        context.getSource().sendSuccess(() -> Component.translatable(
                                "commands.taczweaponblueprints.research.progression_policy",
                                value.includedCount(),
                                value.omittedCount(),
                                value.researchTierCounts(),
                                value.reviewFallbackCount(),
                                value.gateGroupCount(),
                                value.gateConditionCount(),
                                value.fragmentThresholdCounts(),
                                progressionAccess == null ? 0
                                        : progressionAccess.config()
                                                .externalWorkstationTiers().size(),
                                progressionPublication.revision()), false),
                () -> context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.taczweaponblueprints.research.progression_policy.unavailable"), false));
        Optional<BlueprintCraftingPolicySnapshot.ProfileDiagnostics> craftingDiagnostics =
                progressionAccess != null
                        ? Optional.ofNullable(progressionPublication.craftingSnapshot()
                                .diagnosticsByProfile().get(profileId))
                        : Optional.empty();
        craftingDiagnostics.ifPresentOrElse(value ->
                        context.getSource().sendSuccess(() -> Component.translatable(
                                "commands.taczweaponblueprints.research.crafting_policy",
                                value.assignedCount(),
                                value.dispositionCounts(),
                                value.tierCounts(),
                                value.sourceCounts(),
                                value.reviewRequiredCount(),
                                value.gateGroupCount(),
                                value.gateConditionCount(),
                                value.warningCounts(),
                                progressionPublication.revision()), false),
                () -> context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.taczweaponblueprints.research.crafting_policy.unavailable"),
                        false));
        BlueprintProgressionPolicyManager.INSTANCE.lastFailure().ifPresent(message ->
                context.getSource().sendFailure(Component.translatable(
                        "commands.taczweaponblueprints.research.progression_policy.last_failure",
                        message)));
        automatic.ifPresentOrElse(diagnostics ->
                context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.taczweaponblueprints.research.automatic",
                        diagnostics.mode().name().toLowerCase(java.util.Locale.ROOT),
                        research.snapshot().automaticPlacementProfileForTree(
                                diagnostics.treeId())
                                .map(value -> value.scoringModel().serializedName())
                                .orElse("unknown"),
                        diagnostics.reviewHandling().serializedName(),
                        diagnostics.prerequisiteStrategy().serializedName(),
                        diagnostics.treeId(),
                        diagnostics.count(AutomaticWeaponPlacementDiagnostics.State.AUTOMATIC),
                        diagnostics.excludedAutomaticCount(),
                        diagnostics.generatedPrerequisiteCount(),
                        diagnostics.generatedRequirementGroupCount(),
                        diagnostics.generatedAlternativeGroupCount(),
                        diagnostics.entries().values().stream()
                                .filter(entry -> entry.state()
                                        == AutomaticWeaponPlacementDiagnostics.State.AUTOMATIC)
                                .filter(entry -> entry.reason().isPresent())
                                .count(),
                        diagnostics.topologyWeaponCount(),
                        diagnostics.resolvedNodesPerLayer(),
                        diagnostics.mergeInterval(),
                        diagnostics.mergeIntervalBehavior().serializedName(),
                        diagnostics.generatedParentCostGuard(),
                        diagnostics.catalogRevision(),
                        diagnostics.researchRevision()), false),
                () -> context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.taczweaponblueprints.research.automatic.unavailable"), false));
        automatic.filter(diagnostics -> diagnostics.prerequisiteStrategy()
                        == PrerequisiteStrategy.HYBRID_ROUTES_V1)
                .ifPresent(diagnostics -> context.getSource().sendSuccess(() ->
                        Component.translatable(
                                "commands.taczweaponblueprints.research.automatic.hybrid",
                                diagnostics.generatedAlternativeRouteDecisionCount(),
                                diagnostics.generatedMandatoryConvergenceCount(),
                                diagnostics.generatedMixedRequirementCount()), false));
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
                                branches.alternativeRouteReviewCount(),
                                branches.acceptedAlternativeRouteCount(),
                                branches.rejectedAlternativeRouteCostImbalanceCount(),
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
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.cost_mode",
                Component.translatable(
                        economy.researchCostMode().translationKey())), false);
        economy.domain(com.gamergaming.taczweaponblueprints.research.tree
                        .ResearchTechTreeContract.Domain.WEAPONS)
                .ifPresent(domain -> context.getSource().sendSuccess(() ->
                        economy.pointCoverageApplicable()
                                ? Component.translatable(
                                        "commands.taczweaponblueprints.research.economy",
                                        domain.fullTreeCost(),
                                        domain.minimumLeafUnlockClosureCost(),
                                        domain.maximumLeafUnlockClosureCost(),
                                        pointIncome.maximumFinitePoints(),
                                        domain.finiteIncomeCoverageBasisPoints() / 100.0,
                                        domain.andMergeCount(),
                                        economy.costAuthority())
                                : Component.translatable(
                                        "commands.taczweaponblueprints.research.economy.items_only",
                                        domain.andMergeCount(),
                                        economy.costAuthority()), false));
        if (groupedRouteBaseline.available()) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.research.automatic.grouped_baseline",
                    groupedRouteBaseline.automaticTargetCount(),
                    groupedRouteBaseline.matchedGeneratedTargetCount(),
                    groupedRouteBaseline.unmatchedGeneratedTargetCount(),
                    groupedRouteBaseline.generatedReferenceCount(),
                    groupedRouteBaseline.alternativeGroupCandidateCount(),
                    groupedRouteBaseline.pairGroupCandidateCount(),
                    groupedRouteBaseline.largerGroupCandidateCount(),
                    groupedRouteBaseline.maximumAlternativeCount(),
                    groupedRouteBaseline.maximumSingleParentChain(),
                    String.format(
                            java.util.Locale.ROOT,
                            "%.2f",
                            groupedRouteBaseline.alternativeEvidence()
                                    .sharedAncestryBasisPoints().median() / 100.0)), false);
            var routes = groupedRouteBaseline.routeCosts();
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.research.automatic.grouped_economy",
                    routes.currentMandatoryClosureCosts().minimum(),
                    routes.currentMandatoryClosureCosts().maximum(),
                    routes.counterfactualMinimumRouteEstimates().minimum(),
                    routes.counterfactualMinimumRouteEstimates().maximum(),
                    routes.counterfactualMaximumRouteEstimates().minimum(),
                    routes.counterfactualMaximumRouteEstimates().maximum(),
                    routes.currentAffordableLeafCount(),
                    routes.leafCount(),
                    routes.counterfactualAffordableLeafCount(),
                    routes.leafCount(),
                    routes.estimateExact()), false);
        }
        if (groupedRouteQuality.available()) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.research.automatic.grouped_quality",
                    groupedRouteQuality.effectiveAlternativeGroupCount(),
                    groupedRouteQuality.alternativeGroupCount(),
                    String.format(
                            java.util.Locale.ROOT,
                            "%.2f",
                            groupedRouteQuality.alternatives()
                                    .routeCostRatioUpperBoundBasisPoints()
                                    .median() / 100.0),
                    String.format(
                            java.util.Locale.ROOT,
                            "%.2f",
                            groupedRouteQuality.mandatoryAncestorSharesBasisPoints()
                                    .median() / 100.0),
                    groupedRouteQuality.singleRouteChainLengths().percentile95(),
                    groupedRouteQuality.singleRouteChainLengths().maximum(),
                    groupedRouteQuality.affordableTerminalCount(),
                    groupedRouteQuality.terminalRoutes().size(),
                    groupedRouteQuality.unaffordableTerminalCount(),
                    groupedRouteQuality.indeterminateTerminalCount(),
                    groupedRouteQuality.warningOccurrenceCount()), false);
            groupedRouteQuality.phases().forEach(phase ->
                    context.getSource().sendSuccess(() -> Component.translatable(
                            "commands.taczweaponblueprints.research.automatic.grouped_quality_phase",
                            phase.phase().serializedName(),
                            phase.effectiveAlternativeGroupCount(),
                            phase.alternativeGroupCount(),
                            phase.targetCount(),
                            phase.sameFamilyAlternativeGroupCount(),
                            phase.crossFamilyAlternativeGroupCount(),
                            phase.parentFanOut().percentile95(),
                            phase.parentFanOut().maximum()), false));
        }
        if (groupedRouteQuality.available()) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.research.automatic.motif_assessment",
                    motifAssessment.decision().serializedName(),
                    motifAssessment.decisiveSignalCount(),
                    motifAssessment.recommendedMotifs().isEmpty()
                            ? "-"
                            : motifAssessment.recommendedMotifs().stream()
                                    .map(ResearchGroupedRouteMotifAssessment.Motif::serializedName)
                                    .collect(java.util.stream.Collectors.joining(",")),
                    motifAssessment.signal(
                            ResearchGroupedRouteMotifAssessment.SignalCode
                                    .SINGLE_ROUTE_LADDER_P95).observed(),
                    motifAssessment.ladderP95ReviewLimit(),
                    String.format(
                            java.util.Locale.ROOT,
                            "%.2f",
                            motifAssessment.signal(
                                    ResearchGroupedRouteMotifAssessment.SignalCode
                                            .ROUTE_COST_RATIO_P95).observed() / 10_000.0),
                    String.format(
                            java.util.Locale.ROOT,
                            "%.2f",
                            motifAssessment.routeCostRatioP95ReviewLimitBasisPoints()
                                    / 10_000.0),
                    motifAssessment.visualEvidence()
                            .preJunctionApproximateCrossingCount(),
                    motifAssessment.visualEvidence().manualReviewRequired()), false);
        }
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
                policy.requirements().allOf().size(),
                selection.tiedRuleIds().size()), false);
        for (int groupIndex = 0;
                groupIndex < policy.requirements().allOf().size();
                groupIndex++) {
            int displayedGroup = groupIndex + 1;
            String alternatives = policy.requirements().allOf().get(groupIndex).anyOf()
                    .stream().map(ResourceLocation::toString)
                    .collect(java.util.stream.Collectors.joining(" OR "));
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.research.inspect.requirement_group",
                    displayedGroup,
                    alternatives), false);
        }
        java.util.Optional<ResearchTreeGroupPlacement> placement = research.snapshot()
                .placementFor(profileId, blueprintId);
        boolean includedInOverview = placement
                .map(value -> research.snapshot().groups().get(value.groupId()))
                .flatMap(ResearchTreeGroupDefinition::includeInOverview)
                .orElse(placement.isPresent());
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.inspect.presentation",
                placement.map(value -> value.groupId().toString()).orElse("automatic grouping"),
                placement.map(value -> Integer.toString(value.rank())).orElse("-"),
                placement.map(value -> Integer.toString(value.orderInRank())).orElse("-"),
                includedInOverview), false);
        ProgressionPolicyAccessService.Context policyAccess =
                ProgressionPolicyAccessService.acquire(
                        ProgressionPolicyAccessService.Mode.ENSURE_CURRENT).orElse(null);
        Optional.ofNullable(policyAccess)
                .flatMap(access -> access.policyFor(blueprintId))
                .ifPresentOrElse(resolved -> context.getSource().sendSuccess(() ->
                                Component.translatable(
                                        "commands.taczweaponblueprints.research.inspect.research_policy",
                                        resolved.researchWorkbenchTier().serializedName(),
                                        resolved.tierSource().name().toLowerCase(java.util.Locale.ROOT),
                                        resolved.reviewRequired(),
                                        resolved.fragments().completionMode().name().toLowerCase(
                                                java.util.Locale.ROOT),
                                        resolved.fragments().threshold(),
                                        resolved.gates().allOf().size(),
                                        resolved.gates().conditionCount()), false),
                        () -> context.getSource().sendSuccess(() -> Component.translatable(
                                "commands.taczweaponblueprints.research.inspect.research_policy.omitted"),
                                false));
        Optional.ofNullable(policyAccess)
                .flatMap(access -> access.craftingPolicyFor(blueprintId))
                .ifPresentOrElse(resolved -> context.getSource().sendSuccess(() ->
                                Component.translatable(
                                        "commands.taczweaponblueprints.research.inspect.crafting_policy",
                                        resolved.disposition().serializedName(),
                                        resolved.requiredWorkbenchTier()
                                                .map(value -> Integer.toString(value.level()))
                                                .orElse("-"),
                                        resolved.source().serializedName(),
                                        resolved.selectedRuleId()
                                                .map(ResourceLocation::toString).orElse("-"),
                                        resolved.ruleSpecificity().name().toLowerCase(
                                                java.util.Locale.ROOT),
                                        resolved.reviewRequired(),
                                        resolved.gates().allOf().size(),
                                        resolved.gates().conditionCount(),
                                        resolved.reasonCode(),
                                        resolved.warnings().isEmpty()
                                                ? "-"
                                                : resolved.warnings().stream()
                                                        .map(value -> value.serializedName())
                                                        .sorted()
                                                        .collect(java.util.stream.Collectors
                                                                .joining(","))), false),
                        () -> context.getSource().sendSuccess(() -> Component.translatable(
                                "commands.taczweaponblueprints.research.inspect.crafting_policy.unavailable"),
                                false));
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
                            entry.reason().orElse("-"),
                            proposal.map(AutomaticWeaponPlacementProposal::formulaVersion)
                                    .orElse("-"),
                            proposal.map(AutomaticWeaponPlacementProposal::referenceVersion)
                                    .orElse("-"));
                    }, false);
                    entry.prerequisiteDecision().ifPresent(decision -> {
                        context.getSource().sendSuccess(() -> Component.translatable(
                                    "commands.taczweaponblueprints.research.inspect.prerequisite",
                                    decision.strategy().serializedName(),
                                    decision.generatedRequirementShape().serializedName(),
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
                                    decision.terminalPeer()), false);
                        decision.alternativeRouteReview().ifPresent(review ->
                                context.getSource().sendSuccess(() -> Component.translatable(
                                        "commands.taczweaponblueprints.research.inspect.route_review",
                                        review.parentId(),
                                        review.outcome().serializedName(),
                                        review.existingRouteCostLowerBound(),
                                        review.existingRouteCostUpperBound(),
                                        review.candidateRouteCostLowerBound(),
                                        review.candidateRouteCostUpperBound(),
                                        String.format(
                                                java.util.Locale.ROOT,
                                                "%.2f",
                                                review.routeCostRatioLowerBoundBasisPoints()
                                                        / 10_000.0),
                                        String.format(
                                                java.util.Locale.ROOT,
                                                "%.2f",
                                                review.routeCostRatioUpperBoundBasisPoints()
                                                        / 10_000.0),
                                        String.format(
                                                java.util.Locale.ROOT,
                                                "%.2f",
                                                review.mandatoryAncestryOverlapBasisPoints()
                                                        / 100.0),
                                        review.divergentMandatoryNodeCount(),
                                        review.exact()), false));
                    });
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
                tree.graph(), tree.techTree(), pointIncome,
                manager.progressionConfig().researchCostMode());
        ResearchGroupedRouteQualityAudit.Audit groupedRouteQuality =
                ResearchGroupedRouteQualityAudit.audit(
                        tree.graph(), tree.techTree(), automatic.orElse(null), pointIncome);
        ResearchGroupedRouteMotifAssessment.Assessment motifAssessment =
                ResearchGroupedRouteMotifAssessment.assess(
                        groupedRouteQuality, topology);
        ResearchTechTreeAuthoringReport authoring = ResearchTechTreeAuthoringReport.create(
                research.snapshot(),
                catalog.blueprints(),
                profileId,
                automatic.orElse(null),
                AutomaticWeaponEvidenceManager.INSTANCE.snapshotForCatalogRevision(
                        catalog.revision()));
        ProgressionPolicyAccessService.Context progressionAccess =
                ProgressionPolicyAccessService.acquire(
                        ProgressionPolicyAccessService.Mode.CURRENT_ONLY).orElse(null);
        BlueprintProgressionPolicySnapshot progressionPolicy = progressionAccess == null
                ? BlueprintProgressionPolicySnapshot.EMPTY
                : progressionAccess.policy().snapshot();
        BlueprintCraftingPolicySnapshot craftingPolicy = progressionAccess == null
                ? BlueprintCraftingPolicySnapshot.EMPTY
                : progressionAccess.policy().craftingSnapshot();
        String json = BlueprintResearchCatalogExporter.exportWithDiagnostics(
                research.snapshot(),
                catalog.blueprints(),
                profileId,
                automatic.orElse(null),
                authoring,
                topology,
                economy,
                groupedRouteQuality,
                motifAssessment,
                progressionPolicy,
                craftingPolicy,
                progressionAccess == null ? null : progressionAccess.config());
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
