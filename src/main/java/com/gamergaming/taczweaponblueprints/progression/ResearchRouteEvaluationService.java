package com.gamergaming.taczweaponblueprints.progression;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import com.gamergaming.taczweaponblueprints.capabilities.BlueprintLearningMutation;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchAccessFingerprint;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchAccessSummary;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchRouteEligibilityService;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Produces the authoritative route, quote, allocation, capacity, and readiness
 * result used by Research Bench previews and future research guidance features.
 */
public final class ResearchRouteEvaluationService {
    private ResearchRouteEvaluationService() {
    }

    public static Optional<Evaluation> evaluate(Request request) {
        if (request == null) {
            return Optional.empty();
        }
        try {
            int pointCost = request.selectedPolicy().researchCost().points();
            int unlockCount = 1;
            boolean bypass = request.creativePlayer()
                    && request.selectedPolicy().creativeBypassesCost();
            boolean policyEligible = request.selectedPolicy().researchable();
            List<ResearchIngredientPlanner.Requirement> requirements =
                    request.selectedPolicy().researchCost().ingredients().stream()
                            .map(ingredient -> new ResearchIngredientPlanner.Requirement(
                                    ingredient.items(), ingredient.tag(), ingredient.count()))
                            .toList();
            BlueprintResearchService.Status planningStatus =
                    BlueprintResearchService.Status.SUCCESS;
            ResearchPathUnlockPlanner.Plan path = null;
            Optional<ResearchRouteFingerprint> routeFingerprint = Optional.empty();
            ResearchAccessSummary accessSummary = ResearchAccessSummary.NONE;

            if (!request.directPathResearch() && request.accessEvaluator().isPresent()) {
                ResearchRouteEligibilityService.Evaluation access = request.accessEvaluator()
                        .orElseThrow().apply(List.of(request.targetId()));
                if (access == null) {
                    accessSummary = ResearchAccessSummary.POLICY_UNAVAILABLE;
                    policyEligible = false;
                } else {
                    accessSummary = access.summary();
                    policyEligible = policyEligible && access.eligible();
                }
            }

            if (request.directPathResearch()) {
                AccessCache accessCache = request.accessEvaluator()
                        .map(AccessCache::new).orElse(null);
                ResearchPathUnlockPlanner.Result planned = ResearchPathUnlockPlanner.plan(
                        request.targetId(),
                        request.playerData(),
                        request.policyResolver(),
                        request.progressionExempt(),
                        request.creativePlayer(),
                        request.inventoryStacks(),
                        request.pathAuthority(),
                        accessCache == null
                                ? ignored -> true
                                : accessCache::eligibleForPurchase);
                planningStatus = planned.status();
                if (planned.successful() && request.blueprintsEnabled()) {
                    path = request.pathAdjuster().apply(planned.plan().orElseThrow());
                    pointCost = path.pointCost();
                    unlockCount = path.unlockCount();
                    bypass = path.costBypassed();
                    requirements = path.ingredients();
                    policyEligible = true;
                    ResearchAccessFingerprint accessFingerprint = ResearchAccessFingerprint.EMPTY;
                    if (accessCache != null) {
                        ResearchRouteEligibilityService.Evaluation access =
                                accessCache.evaluate(path.nodes().stream()
                                        .map(ResearchPathUnlockPlanner.PlannedNode::blueprintId)
                                        .toList());
                        if (access == null) {
                            accessSummary = ResearchAccessSummary.POLICY_UNAVAILABLE;
                            policyEligible = false;
                        } else {
                            accessSummary = access.summary();
                            accessFingerprint = access.fingerprint();
                            policyEligible = access.eligible();
                        }
                    }
                    if (policyEligible) {
                        routeFingerprint = Optional.of(ResearchRouteFingerprint.create(
                                request.targetId(),
                                path,
                                request.playerData(),
                                request.creativePlayer(),
                                request.fingerprintContext(),
                                accessFingerprint));
                    }
                } else {
                    policyEligible = false;
                    if (accessCache != null) {
                        accessSummary = accessCache.blockedSummary();
                    }
                    if (isVisiblePlanningFailure(planned.status())) {
                        pointCost = 0;
                        unlockCount = 1;
                        bypass = false;
                        requirements = List.of();
                    }
                }
            }

            ResearchIngredientPlanner.Allocation allocation = path == null
                    ? ResearchIngredientPlanner.allocation(
                            request.inventoryStacks(), requirements).orElseThrow()
                    : ResearchPathUnlockPlanner.allocateInventory(
                            path, request.inventoryStacks()).orElseThrow().allocation();
            boolean ingredientsSatisfied = bypass || allocation.complete();
            boolean transactionCapacityAvailable = true;
            if (policyEligible && path != null) {
                transactionCapacityAvailable = pathCapacityAvailable(
                        request.playerData(), path.solution(), request.targetResolver());
            } else if (policyEligible && request.directPathResearch()) {
                BlueprintLearningService.Preparation preparation =
                        BlueprintLearningService.prepare(
                                new BlueprintLearningService.Request(
                                        BlueprintUnlockOrigin.TREE_RESEARCH,
                                        request.targetId(),
                                        request.blueprintsEnabled(),
                                        PhysicalBlueprintLearningMode.DISABLED,
                                        request.progressionExempt().test(request.targetId())),
                                request.playerData(),
                                request.targetResolver(),
                                ignored -> request.selectedPolicy());
                if (!preparation.ready()) {
                    BlueprintLearningService.Status failure = preparation.failure()
                            .orElseThrow().status();
                    if (failure == BlueprintLearningService.Status
                            .PROGRESSION_CAPACITY_EXHAUSTED) {
                        transactionCapacityAvailable = false;
                    } else {
                        policyEligible = false;
                    }
                }
            }
            boolean ready = policyEligible
                    && (bypass || request.playerData().getResearchPoints() >= pointCost)
                    && ingredientsSatisfied
                    && transactionCapacityAvailable;
            return Optional.of(new Evaluation(
                    request.targetId(),
                    pointCost,
                    request.playerData().getResearchPoints(),
                    policyEligible,
                    ingredientsSatisfied,
                    transactionCapacityAvailable,
                    ready,
                    bypass,
                    requirements,
                    allocation,
                    unlockCount,
                    planningStatus,
                    routeFingerprint,
                    request.directPathResearch(),
                    Optional.ofNullable(path),
                    accessSummary));
        } catch (RuntimeException exception) {
            ResearchRouteFailureReporter.report("route evaluation", exception);
            return Optional.empty();
        }
    }

    /** Converts one evaluation into a bounded result containing public identities only. */
    public static Optional<ResearchGuidanceSnapshot> guidanceSnapshot(
            Evaluation evaluation,
            ResearchTreeGraph publicGraph,
            ResearchCostMode costMode) {
        if (evaluation == null || publicGraph == null || costMode == null) {
            return Optional.empty();
        }
        try {
            ResearchTreeGraph.Node target = publicGraph.node(evaluation.targetId())
                    .filter(node -> node.visibility().revealsIdentity())
                    .orElse(null);
            if (target == null) {
                return Optional.empty();
            }
            if (target.learned()) {
                return Optional.of(new ResearchGuidanceSnapshot(
                        evaluation.targetId(),
                        ResearchGuidanceSnapshot.State.LEARNED,
                        0,
                        costMode.pointsEnabled() ? evaluation.pointBalance() : 0,
                        costMode,
                        false,
                        true,
                        0,
                        0,
                        0,
                        0,
                        List.of(),
                        List.of(evaluation.targetId()),
                        List.of(),
                        List.of(),
                        Optional.empty()));
            }
            if (evaluation.accessSummary().blocked() || evaluation.path().isEmpty()) {
                return Optional.of(unavailableGuidance(evaluation, costMode));
            }
            ResearchPathUnlockPlanner.Plan path = evaluation.path().orElseThrow();
            List<ResourceLocation> supportIds = path.solution().supportIds();
            boolean publicSupport = supportIds.stream().allMatch(id -> publicGraph.node(id)
                    .filter(node -> node.visibility().revealsIdentity()).isPresent());
            boolean publicRequirementProof = path.solution().selectedRequirements().stream()
                    .allMatch(selected -> publicGraph
                            .requirementGroupsOf(selected.dependentId()).stream()
                            .filter(group -> group.ordinal() == selected.groupOrdinal())
                            .anyMatch(group -> group.visibleAlternativeIds()
                                    .contains(selected.prerequisiteId())));
            if (!publicSupport
                    || !publicRequirementProof
                    || (supportIds.size() > 1
                            && path.solution().selectedRequirements().isEmpty())
                    || supportIds.size() > ResearchGuidanceSnapshot.MAX_SUPPORT_IDS
                    || path.nodes().size() > ResearchGuidanceSnapshot.MAX_PURCHASE_IDS
                    || path.solution().selectedRequirements().size()
                            > ResearchGuidanceSnapshot.MAX_SELECTED_REQUIREMENTS) {
                return Optional.of(unavailableGuidance(evaluation, costMode));
            }
            int totalMaterialTypes = costMode.itemsEnabled()
                    ? evaluation.requirements().size()
                    : 0;
            int totalMaterialUnits = costMode.itemsEnabled()
                    ? evaluation.allocation().totalRequired()
                    : 0;
            int allocatedMaterialUnits = costMode.itemsEnabled()
                    ? evaluation.allocation().totalAllocated()
                    : 0;
            int missingMaterialTypes = 0;
            if (costMode.itemsEnabled()) {
                for (int index = 0; index < evaluation.requirements().size(); index++) {
                    if (evaluation.allocation().allocatedForIngredient(index)
                            < evaluation.requirements().get(index).count()) {
                        missingMaterialTypes++;
                    }
                }
            }
            int materialLimit = Math.min(
                    totalMaterialTypes, ResearchGuidanceSnapshot.MAX_MATERIAL_PROGRESS);
            List<ResearchGuidanceSnapshot.MaterialProgress> materials =
                    new java.util.ArrayList<>(materialLimit);
            for (int index = 0; index < materialLimit; index++) {
                ResearchIngredientPlanner.Requirement requirement =
                        evaluation.requirements().get(index);
                materials.add(new ResearchGuidanceSnapshot.MaterialProgress(
                        requirement.items(),
                        requirement.tag(),
                        requirement.count(),
                        evaluation.allocation().allocatedForIngredient(index)));
            }
            boolean pointsMissing = costMode.pointsEnabled()
                    && !evaluation.costBypassed()
                    && evaluation.pointBalance() < evaluation.pointCost();
            boolean materialsMissing = costMode.itemsEnabled()
                    && !evaluation.costBypassed()
                    && !evaluation.ingredientsSatisfied();
            ResearchGuidanceSnapshot.State state = pointsMissing && materialsMissing
                    ? ResearchGuidanceSnapshot.State.MISSING_POINTS_AND_MATERIALS
                    : pointsMissing
                            ? ResearchGuidanceSnapshot.State.MISSING_POINTS
                            : materialsMissing
                                    ? ResearchGuidanceSnapshot.State.MISSING_MATERIALS
                                    : ResearchGuidanceSnapshot.State.AFFORDABLE;
            List<ResourceLocation> purchaseIds = path.nodes().stream()
                    .map(ResearchPathUnlockPlanner.PlannedNode::blueprintId)
                    .toList();
            return Optional.of(new ResearchGuidanceSnapshot(
                    evaluation.targetId(),
                    state,
                    costMode.pointsEnabled() ? evaluation.pointCost() : 0,
                    costMode.pointsEnabled() ? evaluation.pointBalance() : 0,
                    costMode,
                    evaluation.costBypassed(),
                    evaluation.transactionCapacityAvailable(),
                    totalMaterialTypes,
                    totalMaterialUnits,
                    allocatedMaterialUnits,
                    missingMaterialTypes,
                    materials,
                    supportIds,
                    purchaseIds,
                    path.solution().selectedRequirements(),
                    purchaseIds.isEmpty()
                            ? Optional.empty()
                            : Optional.of(purchaseIds.get(0))));
        } catch (RuntimeException exception) {
            ResearchRouteFailureReporter.report("guidance snapshot construction", exception);
            return Optional.empty();
        }
    }

    private static ResearchGuidanceSnapshot unavailableGuidance(
            Evaluation evaluation,
            ResearchCostMode costMode) {
        ResearchGuidanceSnapshot.State state = switch (evaluation.planningStatus()) {
            case CONTENT_UNAVAILABLE, BLOCKED, RESEARCH_DISABLED,
                    DISCOVERY_REQUIRED, POLICY_INELIGIBLE ->
                    ResearchGuidanceSnapshot.State.POLICY_BLOCKED;
            default -> ResearchGuidanceSnapshot.State.ROUTE_UNAVAILABLE;
        };
        if (evaluation.accessSummary().kind()
                == ResearchAccessSummary.Kind.POLICY_UNAVAILABLE) {
            state = ResearchGuidanceSnapshot.State.ROUTE_UNAVAILABLE;
        } else if (evaluation.accessSummary().blocked()) {
            state = ResearchGuidanceSnapshot.State.POLICY_BLOCKED;
        }
        return new ResearchGuidanceSnapshot(
                evaluation.targetId(),
                state,
                0,
                costMode.pointsEnabled() ? evaluation.pointBalance() : 0,
                costMode,
                false,
                evaluation.transactionCapacityAvailable(),
                0,
                0,
                0,
                0,
                List.of(),
                List.of(evaluation.targetId()),
                List.of(),
                List.of(),
                Optional.empty());
    }

    private static boolean isVisiblePlanningFailure(BlueprintResearchService.Status status) {
        return status == BlueprintResearchService.Status.PATH_TOO_LARGE
                || status == BlueprintResearchService.Status.ROUTE_TOO_COMPLEX
                || status == BlueprintResearchService.Status.TECH_TREE_UNAVAILABLE
                || status == BlueprintResearchService.Status.UNSATISFIABLE;
    }

    private static boolean pathCapacityAvailable(
            IPlayerRecipeData data,
            ResearchPathUnlockPlanner.SelectedUnlockSolution solution,
            Function<ResourceLocation, BlueprintLearningService.LearningTarget> targetResolver) {
        if (data.getLearnedBlueprints().size() + solution.unlockCount()
                > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
            return false;
        }
        Set<String> discoveries = new LinkedHashSet<>(data.getDiscoveredBlueprints());
        Set<String> recipes = new LinkedHashSet<>(data.getLearnedRecipes());
        for (ResearchPathUnlockPlanner.PlannedNode node : solution.nodes()) {
            BlueprintLearningService.LearningTarget target;
            BlueprintLearningMutation.Result preflight;
            try {
                target = targetResolver.apply(node.blueprintId());
                if (target == null || !node.blueprintId().equals(target.blueprintId())) {
                    return false;
                }
                preflight = data.applyBlueprintLearning(
                        BlueprintLearningMutation.Request.preflight(
                                target.blueprintId().toString(),
                                target.legacyRecipeId().toString()));
            } catch (RuntimeException exception) {
                ResearchRouteFailureReporter.report("route-capacity preflight", exception);
                return false;
            }
            if (!preflight.ready()) {
                return false;
            }
            discoveries.add(target.blueprintId().toString());
            recipes.add(target.legacyRecipeId().toString());
            if (discoveries.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                    || recipes.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
                return false;
            }
        }
        return true;
    }

    /** Caches bounded single-node access checks used while exploring OR routes. */
    private static final class AccessCache {
        private final Function<List<ResourceLocation>,
                ResearchRouteEligibilityService.Evaluation> evaluator;
        private final Map<ResourceLocation, ResearchRouteEligibilityService.Evaluation>
                nodeEvaluations = new LinkedHashMap<>();

        private AccessCache(Function<List<ResourceLocation>,
                ResearchRouteEligibilityService.Evaluation> evaluator) {
            this.evaluator = evaluator;
        }

        private boolean eligibleForPurchase(ResourceLocation blueprintId) {
            return nodeEvaluations.computeIfAbsent(
                    blueprintId,
                    id -> normalize(evaluator.apply(List.of(id))))
                    .eligible();
        }

        private ResearchRouteEligibilityService.Evaluation evaluate(
                List<ResourceLocation> blueprintIds) {
            return normalize(evaluator.apply(blueprintIds));
        }

        private ResearchAccessSummary blockedSummary() {
            ResearchAccessSummary tier = nodeEvaluations.entrySet().stream()
                    .filter(entry -> !entry.getValue().eligible())
                    .map(Map.Entry::getValue)
                    .map(ResearchRouteEligibilityService.Evaluation::summary)
                    .filter(summary -> summary.kind()
                            == ResearchAccessSummary.Kind.WORKBENCH_TIER)
                    .max(java.util.Comparator.comparingInt(summary -> summary
                            .requiredTier().orElseThrow().level()))
                    .orElse(null);
            if (tier != null) {
                return tier;
            }
            return nodeEvaluations.entrySet().stream()
                    .filter(entry -> !entry.getValue().eligible())
                    .sorted(Map.Entry.comparingByKey(
                            java.util.Comparator.comparing(ResourceLocation::toString)))
                    .map(Map.Entry::getValue)
                    .map(ResearchRouteEligibilityService.Evaluation::summary)
                    .filter(ResearchAccessSummary::blocked)
                    .findFirst()
                    .orElse(ResearchAccessSummary.NONE);
        }

        private static ResearchRouteEligibilityService.Evaluation normalize(
                ResearchRouteEligibilityService.Evaluation evaluation) {
            return evaluation == null
                    ? ResearchRouteEligibilityService.Evaluation.unavailable()
                    : evaluation;
        }
    }

    public record Request(
            ResourceLocation targetId,
            IPlayerRecipeData playerData,
            BlueprintResearchPolicy selectedPolicy,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            Predicate<ResourceLocation> progressionExempt,
            ResearchPathAuthority pathAuthority,
            ResearchRouteFingerprint.Context fingerprintContext,
            Function<ResourceLocation, BlueprintLearningService.LearningTarget> targetResolver,
            List<ItemStack> inventoryStacks,
            boolean creativePlayer,
            boolean directPathResearch,
            boolean blueprintsEnabled,
            Optional<Function<List<ResourceLocation>,
                    ResearchRouteEligibilityService.Evaluation>> accessEvaluator,
            java.util.function.UnaryOperator<ResearchPathUnlockPlanner.Plan> pathAdjuster) {
        public Request(
                ResourceLocation targetId,
                IPlayerRecipeData playerData,
                BlueprintResearchPolicy selectedPolicy,
                Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
                Predicate<ResourceLocation> progressionExempt,
                ResearchPathAuthority pathAuthority,
                ResearchRouteFingerprint.Context fingerprintContext,
                Function<ResourceLocation, BlueprintLearningService.LearningTarget> targetResolver,
                List<ItemStack> inventoryStacks,
                boolean creativePlayer,
                boolean directPathResearch,
                boolean blueprintsEnabled,
                Optional<Function<List<ResourceLocation>,
                        ResearchRouteEligibilityService.Evaluation>> accessEvaluator) {
            this(
                    targetId,
                    playerData,
                    selectedPolicy,
                    policyResolver,
                    progressionExempt,
                    pathAuthority,
                    fingerprintContext,
                    targetResolver,
                    inventoryStacks,
                    creativePlayer,
                    directPathResearch,
                    blueprintsEnabled,
                    accessEvaluator,
                    java.util.function.UnaryOperator.identity());
        }

        public Request(
                ResourceLocation targetId,
                IPlayerRecipeData playerData,
                BlueprintResearchPolicy selectedPolicy,
                Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
                Predicate<ResourceLocation> progressionExempt,
                ResearchPathAuthority pathAuthority,
                ResearchRouteFingerprint.Context fingerprintContext,
                Function<ResourceLocation, BlueprintLearningService.LearningTarget> targetResolver,
                List<ItemStack> inventoryStacks,
                boolean creativePlayer,
                boolean directPathResearch,
                boolean blueprintsEnabled) {
            this(
                    targetId,
                    playerData,
                    selectedPolicy,
                    policyResolver,
                    progressionExempt,
                    pathAuthority,
                    fingerprintContext,
                    targetResolver,
                    inventoryStacks,
                    creativePlayer,
                    directPathResearch,
                    blueprintsEnabled,
                    Optional.empty(),
                    java.util.function.UnaryOperator.identity());
        }

        public Request {
            accessEvaluator = accessEvaluator == null ? Optional.empty() : accessEvaluator;
            if (targetId == null || playerData == null || selectedPolicy == null
                    || !targetId.equals(selectedPolicy.blueprintId())
                    || policyResolver == null || progressionExempt == null
                    || pathAuthority == null || fingerprintContext == null
                    || targetResolver == null || inventoryStacks == null
                    || pathAdjuster == null
                    || inventoryStacks.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("research route evaluation request is invalid");
            }
            inventoryStacks = inventoryStacks.stream().map(ItemStack::copy).toList();
        }
    }

    public record Evaluation(
            ResourceLocation targetId,
            int pointCost,
            int pointBalance,
            boolean policyEligible,
            boolean ingredientsSatisfied,
            boolean transactionCapacityAvailable,
            boolean ready,
            boolean costBypassed,
            List<ResearchIngredientPlanner.Requirement> requirements,
            ResearchIngredientPlanner.Allocation allocation,
            int unlockCount,
            BlueprintResearchService.Status planningStatus,
            Optional<ResearchRouteFingerprint> routeFingerprint,
            boolean directPathResearch,
            Optional<ResearchPathUnlockPlanner.Plan> path,
            ResearchAccessSummary accessSummary) {
        public Evaluation {
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
            routeFingerprint = routeFingerprint == null ? Optional.empty() : routeFingerprint;
            path = path == null ? Optional.empty() : path;
            accessSummary = accessSummary == null ? ResearchAccessSummary.NONE : accessSummary;
            if (targetId == null || pointCost < 0 || pointBalance < 0 || allocation == null
                    || unlockCount < 1 || planningStatus == null
                    || allocation.ingredientCount() != requirements.size()
                    || !directPathResearch && path.isPresent()
                    || path.isPresent() != routeFingerprint.isPresent()
                            && !accessSummary.blocked()
                    || accessSummary.blocked() && policyEligible
                    || ready && (!policyEligible || !ingredientsSatisfied
                            || !transactionCapacityAvailable
                            || !costBypassed && pointBalance < pointCost)) {
                throw new IllegalArgumentException("research route evaluation is invalid");
            }
        }
    }
}
