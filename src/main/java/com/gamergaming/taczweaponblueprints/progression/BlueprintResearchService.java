package com.gamergaming.taczweaponblueprints.progression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.BlueprintLearningMutation;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressValueMutation;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.BlueprintLearningService.LearningTarget;
import com.gamergaming.taczweaponblueprints.progression.BlueprintLearningService.Preparation;
import com.gamergaming.taczweaponblueprints.progression.BlueprintLearningService.PreparedLearning;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchAccessSummary;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchRouteEligibilityService;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentResearchService;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchAuthority;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchContext;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Atomic server authority for one Research Bench target or prerequisite path. */
public final class BlueprintResearchService {
    private BlueprintResearchService() {
    }

    /** Researches directly from the player's main inventory and hotbar. */
    public static Result researchFromInventory(ServerPlayer player, ResourceLocation blueprintId) {
        return Result.failure(
                Status.WORKBENCH_TIER_REQUIRED,
                Optional.ofNullable(blueprintId),
                player == null ? 0 : player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                        .map(IPlayerRecipeData::getResearchPoints).orElse(0),
                TreeResearchResultMode.DIRECT_LEARN);
    }

    /** Researches with a live, server-derived Research Bench context. */
    public static Result researchFromInventory(
            ServerPlayer player,
            ResourceLocation blueprintId,
            ResearchWorkbenchContext workbenchContext) {
        if (player == null || !player.isAlive() || !validId(blueprintId)) {
            return Result.failure(Status.INVALID_INPUT, Optional.ofNullable(blueprintId), 0);
        }
        if (!ResearchWorkbenchAuthority.validForResearch(player, workbenchContext)) {
            return Result.failure(
                    Status.WORKBENCH_TIER_REQUIRED,
                    Optional.of(blueprintId),
                    player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                            .map(IPlayerRecipeData::getResearchPoints).orElse(0),
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        return research(
                player,
                blueprintId,
                playerInventoryInput(player),
                workbenchContext);
    }

    /**
     * Commits the exact direct-learning path prepared and fingerprinted by the
     * open Research Bench. Economic state and learning capacity are still
     * rechecked atomically, including route selection against current policy.
     */
    public static Result researchPreparedPathFromInventory(
            ServerPlayer player,
            ResourceLocation blueprintId,
            ResearchPathUnlockPlanner.Plan preparedPath) {
        return Result.failure(
                Status.WORKBENCH_TIER_REQUIRED,
                Optional.ofNullable(blueprintId),
                player == null ? 0 : player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                        .map(IPlayerRecipeData::getResearchPoints).orElse(0),
                TreeResearchResultMode.DIRECT_LEARN);
    }

    public static Result researchPreparedPathFromInventory(
            ServerPlayer player,
            ResourceLocation blueprintId,
            ResearchPathUnlockPlanner.Plan preparedPath,
            ResearchWorkbenchContext workbenchContext) {
        if (player == null || !player.isAlive() || !validId(blueprintId)
                || preparedPath == null
                || preparedPath.nodes().isEmpty()
                || !blueprintId.equals(
                        preparedPath.nodes().get(preparedPath.nodes().size() - 1).blueprintId())
                || !preparedPath.solution().supportIds().contains(blueprintId)) {
            return Result.failure(
                    Status.INVALID_INPUT,
                    Optional.ofNullable(blueprintId),
                    player == null ? 0 : player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                            .map(IPlayerRecipeData::getResearchPoints).orElse(0),
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        Optional<IPlayerRecipeData> resolvedData =
                player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve();
        if (resolvedData.isEmpty()) {
            return Result.failure(
                    Status.PLAYER_DATA_UNAVAILABLE,
                    Optional.of(blueprintId),
                    0,
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        IPlayerRecipeData playerData = resolvedData.orElseThrow();
        if (!ResearchWorkbenchAuthority.validForResearch(player, workbenchContext)) {
            return Result.failure(
                    Status.WORKBENCH_TIER_REQUIRED,
                    Optional.of(blueprintId),
                    playerData.getResearchPoints(),
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        BlueprintProgressionConfigSnapshot config =
                ModConfigs.BLUEPRINT.progressionSnapshot();
        if (!config.treeResearchResultMode().learnsDirectly()) {
            return Result.failure(
                    Status.STALE_POLICY,
                    Optional.of(blueprintId),
                    playerData.getResearchPoints(),
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        ResearchPathUnlockPlanner.Plan currentPath;
        try {
            BlueprintResearchDataManager.ResearchPlanningAccess planningAccess =
                    BlueprintResearchDataManager.INSTANCE.planningAccessFor(playerData);
            ResearchPathUnlockPlanner.Result replanned = ResearchPathUnlockPlanner.plan(
                    blueprintId,
                    playerData,
                    planningAccess.policyResolver(),
                    planningAccess.progressionExempt(),
                    player.isCreative(),
                    playerInventoryInput(player).stacks(),
                    planningAccess.authority(),
                    pendingNodeEligibility(player, playerData, workbenchContext));
            if (!replanned.successful()) {
                return Result.failure(
                        replanned.status(),
                        Optional.of(blueprintId),
                        playerData.getResearchPoints(),
                        TreeResearchResultMode.DIRECT_LEARN);
            }
            currentPath = BlueprintFragmentResearchService.adjustRuntimePlan(
                    replanned.plan().orElseThrow(), playerData);
        } catch (RuntimeException exception) {
            return Result.failure(
                    Status.POLICY_UNAVAILABLE,
                    Optional.of(blueprintId),
                    playerData.getResearchPoints(),
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        if (!currentPath.equals(preparedPath)) {
            return Result.failure(
                    Status.STALE_POLICY,
                    Optional.of(blueprintId),
                    playerData.getResearchPoints(),
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        ResearchRouteEligibilityService.Evaluation access =
                ResearchRouteEligibilityService.evaluate(
                        player, currentPath, workbenchContext);
        if (!access.eligible()) {
            return Result.failure(
                    accessFailure(access.summary()),
                    Optional.of(blueprintId),
                    playerData.getResearchPoints(),
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        Result result = commitPreparedPath(
                blueprintId,
                playerData,
                currentPath,
                id -> BlueprintLearningService.targetFromCatalog(
                        BlueprintDataManager.SERVER, id),
                playerInventoryInput(player),
                config.blueprintsEnabled());
        if (result.successful()) {
            ResearchPointAwardDispatcher.blueprintTransitionBatch(
                    player,
                    playerData,
                    result.transitions().stream()
                            .map(transition -> new ResearchPointAwardDispatcher.BlueprintTransition(
                                    transition.blueprintId(),
                                    transition.discoveredChanged(),
                                    transition.learnedChanged()))
                            .toList());
            syncPostCommitBestEffort(player, true);
        }
        return result;
    }

    static Result commitPreparedPath(
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData,
            ResearchPathUnlockPlanner.Plan preparedPath,
            Function<ResourceLocation, LearningTarget> targetResolver,
            ResearchInput input,
            boolean blueprintsEnabled) {
        Optional<ResourceLocation> id = Optional.ofNullable(blueprintId)
                .filter(BlueprintResearchService::validId);
        if (id.isEmpty() || playerData == null || preparedPath == null
                || targetResolver == null || input == null
                || preparedPath.nodes().isEmpty()
                || !blueprintId.equals(
                        preparedPath.nodes().get(preparedPath.nodes().size() - 1).blueprintId())
                || !preparedPath.solution().supportIds().contains(blueprintId)) {
            return Result.failure(
                    Status.INVALID_INPUT,
                    id,
                    playerData == null ? 0 : playerData.getResearchPoints(),
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        List<ItemStack> inputSnapshot;
        try {
            inputSnapshot = input.stacks();
        } catch (RuntimeException exception) {
            return Result.failure(
                    Status.TRANSACTION_FAILED,
                    id,
                    playerData.getResearchPoints(),
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        return commitPath(
                blueprintId,
                playerData,
                preparedPath,
                targetResolver,
                input,
                inputSnapshot,
                blueprintsEnabled);
    }

    /** Publishes legacy knowledge migrated while preparing an authoritative preview. */
    public static void syncMigratedKnowledgeBestEffort(ServerPlayer player) {
        if (player != null) {
            syncPostCommitBestEffort(player, true);
        }
    }

    private static Result research(
            ServerPlayer player,
            ResourceLocation blueprintId,
            ResearchInput input,
            ResearchWorkbenchContext workbenchContext) {
        Optional<IPlayerRecipeData> resolvedData =
                player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve();
        if (resolvedData.isEmpty()) {
            return Result.failure(Status.PLAYER_DATA_UNAVAILABLE, Optional.of(blueprintId), 0);
        }

        IPlayerRecipeData playerData = resolvedData.orElseThrow();
        BlueprintLearningService.MigrationResult migration =
                BlueprintLearningService.migrateLegacyUnlocksDetailed(
                        BlueprintDataManager.SERVER, playerData);
        BlueprintProgressionConfigSnapshot config =
                ModConfigs.BLUEPRINT.progressionSnapshot();
        TreeResearchResultMode resultMode = config.treeResearchResultMode();
        Function<ResourceLocation, BlueprintResearchPolicy> policyResolver;
        Predicate<ResourceLocation> pathProgressionExempt;
        ResearchPathAuthority pathAuthority;
        try {
            if (resultMode.learnsDirectly()) {
                BlueprintResearchDataManager.ResearchPlanningAccess planningAccess =
                        BlueprintResearchDataManager.INSTANCE.planningAccessFor(playerData);
                policyResolver = planningAccess.policyResolver();
                pathProgressionExempt = planningAccess.progressionExempt();
                pathAuthority = planningAccess.authority();
            } else {
                policyResolver = BlueprintResearchDataManager.INSTANCE.policyResolverFor(playerData);
                pathProgressionExempt = BlueprintProgressionAccess::isProgressionExempt;
                pathAuthority = ResearchPathAuthority.authored();
            }
        } catch (RuntimeException exception) {
            return Result.failure(
                    Status.POLICY_UNAVAILABLE,
                    Optional.of(blueprintId),
                    playerData.getResearchPoints(),
                    resultMode);
        }
        try {
            if (pathProgressionExempt.test(blueprintId)) {
                return Result.failure(
                        Status.POLICY_INELIGIBLE,
                        Optional.of(blueprintId),
                        playerData.getResearchPoints(),
                        resultMode);
            }
        } catch (RuntimeException exception) {
            return Result.failure(
                    Status.POLICY_UNAVAILABLE,
                    Optional.of(blueprintId),
                    playerData.getResearchPoints(),
                    resultMode);
        }
        Function<ResourceLocation, LearningTarget> targetResolver =
                id -> BlueprintLearningService.targetFromCatalog(
                        BlueprintDataManager.SERVER, id);
        Result result = resultMode.learnsDirectly()
                ? researchPath(
                        blueprintId,
                        playerData,
                        policyResolver,
                        targetResolver,
                        input,
                        player.isCreative(),
                        config.blueprintsEnabled(),
                        pathProgressionExempt,
                        pathAuthority,
                        player,
                        workbenchContext)
                : researchSingleWithAccess(
                        player,
                        workbenchContext,
                        blueprintId,
                        playerData,
                        policyResolver,
                        targetResolver,
                        input,
                        player.isCreative(),
                        config.blueprintsEnabled(),
                        resultMode);
        if (result.successful() && resultMode.learnsDirectly()) {
            ResearchPointAwardDispatcher.blueprintTransitionBatch(
                    player,
                    playerData,
                    result.transitions().stream()
                            .map(transition -> new ResearchPointAwardDispatcher.BlueprintTransition(
                                    transition.blueprintId(),
                                    transition.discoveredChanged(),
                                    transition.learnedChanged()))
                            .toList());
        } else if (result.successful()) {
            BlueprintDiscoveryService.discoverInventoryBlueprint(
                    player, BlueprintItem.createBlueprint(blueprintId.toString()));
        }
        if (result.successful() || migration.changed()) {
            syncPostCommitBestEffort(
                    player,
                    resultMode.learnsDirectly() || migration.changed());
        }
        return result;
    }

    static Result researchPath(
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            Function<ResourceLocation, LearningTarget> targetResolver,
            ResearchInput input,
            boolean creativePlayer,
            boolean blueprintsEnabled,
            Predicate<ResourceLocation> progressionExempt) {
        return researchPath(
                blueprintId,
                playerData,
                policyResolver,
                targetResolver,
                input,
                creativePlayer,
                blueprintsEnabled,
                progressionExempt,
                ResearchPathAuthority.authored());
    }

    static Result researchPath(
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            Function<ResourceLocation, LearningTarget> targetResolver,
            ResearchInput input,
            boolean creativePlayer,
            boolean blueprintsEnabled,
            Predicate<ResourceLocation> progressionExempt,
            ResearchPathAuthority authority) {
        return researchPath(
                blueprintId,
                playerData,
                policyResolver,
                targetResolver,
                input,
                creativePlayer,
                blueprintsEnabled,
                progressionExempt,
                authority,
                null,
                null);
    }

    private static Result researchPath(
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            Function<ResourceLocation, LearningTarget> targetResolver,
            ResearchInput input,
            boolean creativePlayer,
            boolean blueprintsEnabled,
            Predicate<ResourceLocation> progressionExempt,
            ResearchPathAuthority authority,
            ServerPlayer player,
            ResearchWorkbenchContext workbenchContext) {
        Optional<ResourceLocation> id = Optional.ofNullable(blueprintId)
                .filter(BlueprintResearchService::validId);
        if (id.isEmpty() || input == null || progressionExempt == null || authority == null) {
            return Result.failure(
                    Status.INVALID_INPUT,
                    id,
                    playerData == null ? 0 : playerData.getResearchPoints(),
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        if (playerData == null) {
            return Result.failure(
                    Status.PLAYER_DATA_UNAVAILABLE,
                    id,
                    0,
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        if (policyResolver == null || targetResolver == null) {
            return Result.failure(
                    Status.POLICY_UNAVAILABLE,
                    id,
                    playerData.getResearchPoints(),
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        List<ItemStack> inputSnapshot;
        try {
            inputSnapshot = input.stacks();
        } catch (RuntimeException exception) {
            return Result.failure(
                    Status.TRANSACTION_FAILED,
                    id,
                    playerData.getResearchPoints(),
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        if (inputSnapshot == null
                || inputSnapshot.stream().anyMatch(java.util.Objects::isNull)) {
            return Result.failure(
                    Status.INVALID_INPUT,
                    id,
                    playerData.getResearchPoints(),
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        ResearchPathUnlockPlanner.Result planned = ResearchPathUnlockPlanner.plan(
                blueprintId,
                playerData,
                policyResolver,
                progressionExempt,
                creativePlayer,
                inputSnapshot,
                authority,
                pendingNodeEligibility(player, playerData, workbenchContext));
        if (!planned.successful()) {
            return Result.failure(
                    planned.status(),
                    id,
                    playerData.getResearchPoints(),
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        ResearchPathUnlockPlanner.Plan adjustedPath = player == null
                ? planned.plan().orElseThrow()
                : BlueprintFragmentResearchService.adjustRuntimePlan(
                        planned.plan().orElseThrow(), playerData);
        if (player != null) {
            if (!ResearchWorkbenchAuthority.validForResearch(player, workbenchContext)) {
                return Result.failure(
                        Status.WORKBENCH_TIER_REQUIRED,
                        id,
                        playerData.getResearchPoints(),
                        TreeResearchResultMode.DIRECT_LEARN);
            }
            ResearchRouteEligibilityService.Evaluation access =
                    ResearchRouteEligibilityService.evaluate(
                            player, adjustedPath, workbenchContext);
            if (!access.eligible()) {
                return Result.failure(
                        accessFailure(access.summary()),
                        id,
                        playerData.getResearchPoints(),
                        TreeResearchResultMode.DIRECT_LEARN);
            }
        }
        return commitPath(
                blueprintId,
                playerData,
                adjustedPath,
                targetResolver,
                input,
                inputSnapshot,
                blueprintsEnabled);
    }

    private static Result commitPath(
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData,
            ResearchPathUnlockPlanner.Plan path,
            Function<ResourceLocation, LearningTarget> targetResolver,
            ResearchInput input,
            List<ItemStack> inputSnapshot,
            boolean blueprintsEnabled) {
        int currentPoints = playerData.getResearchPoints();
        Optional<ResourceLocation> id = Optional.of(blueprintId);
        if (!blueprintsEnabled) {
            return Result.failure(
                    Status.CONTENT_UNAVAILABLE,
                    id,
                    currentPoints,
                    TreeResearchResultMode.DIRECT_LEARN);
        }

        if (inputSnapshot == null
                || inputSnapshot.stream().anyMatch(java.util.Objects::isNull)) {
            return Result.failure(
                    Status.INVALID_INPUT,
                    id,
                    currentPoints,
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        ResearchPathUnlockPlanner.RouteQuote quote = path.quote();
        if (currentPoints < quote.pointCost()) {
            return Result.failure(
                    Status.POINTS_REQUIRED,
                    id,
                    currentPoints,
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        Optional<ResearchPathUnlockPlanner.TransactionPlan> prepared =
                ResearchPathUnlockPlanner.prepareTransaction(path, inputSnapshot);
        if (prepared.isEmpty()) {
            return Result.failure(
                    Status.INGREDIENTS_REQUIRED,
                    id,
                    currentPoints,
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        ResearchPathUnlockPlanner.TransactionPlan transaction = prepared.orElseThrow();

        KnowledgeSnapshot knowledgeSnapshot;
        try {
            knowledgeSnapshot = KnowledgeSnapshot.capture(playerData);
        } catch (RuntimeException exception) {
            return Result.failure(
                    Status.TRANSACTION_FAILED,
                    id,
                    currentPoints,
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        List<LearningTarget> targets = new ArrayList<>(transaction.unlockCount());
        Set<String> learnedAfter = new LinkedHashSet<>(knowledgeSnapshot.learnedBlueprints());
        Set<String> discoveredAfter = new LinkedHashSet<>(knowledgeSnapshot.discoveredBlueprints());
        Set<String> recipesAfter = new LinkedHashSet<>(knowledgeSnapshot.learnedRecipes());
        for (ResearchPathUnlockPlanner.PlannedNode node : transaction.solution().nodes()) {
            LearningTarget target;
            try {
                target = targetResolver.apply(node.blueprintId());
            } catch (RuntimeException exception) {
                return Result.failure(
                        Status.POLICY_UNAVAILABLE,
                        id,
                        currentPoints,
                        TreeResearchResultMode.DIRECT_LEARN);
            }
            if (target == null) {
                return Result.failure(
                        Status.CONTENT_UNAVAILABLE,
                        id,
                        currentPoints,
                        TreeResearchResultMode.DIRECT_LEARN);
            }
            if (!node.blueprintId().equals(target.blueprintId())
                    || !validId(target.blueprintId())
                    || !validId(target.legacyRecipeId())) {
                return Result.failure(
                        Status.INVALID_INPUT,
                        id,
                        currentPoints,
                        TreeResearchResultMode.DIRECT_LEARN);
            }
            BlueprintLearningMutation.Result preflight;
            try {
                preflight = playerData.applyBlueprintLearning(
                        BlueprintLearningMutation.Request.preflight(
                                target.blueprintId().toString(),
                                target.legacyRecipeId().toString()));
            } catch (RuntimeException exception) {
                return Result.failure(
                        Status.TRANSACTION_FAILED,
                        id,
                        currentPoints,
                        TreeResearchResultMode.DIRECT_LEARN);
            }
            if (preflight.status() == BlueprintLearningMutation.Status.CAPACITY_REACHED) {
                return Result.failure(
                        Status.PROGRESSION_CAPACITY_EXHAUSTED,
                        id,
                        currentPoints,
                        TreeResearchResultMode.DIRECT_LEARN);
            }
            if (!preflight.ready()) {
                return Result.failure(
                        preflight.status() == BlueprintLearningMutation.Status.INVALID_IDENTITY
                                ? Status.INVALID_INPUT
                                : Status.STALE_POLICY,
                        id,
                        currentPoints,
                        TreeResearchResultMode.DIRECT_LEARN);
            }
            learnedAfter.add(target.blueprintId().toString());
            discoveredAfter.add(target.blueprintId().toString());
            recipesAfter.add(target.legacyRecipeId().toString());
            if (learnedAfter.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                    || discoveredAfter.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                    || recipesAfter.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
                return Result.failure(
                        Status.PROGRESSION_CAPACITY_EXHAUSTED,
                        id,
                        currentPoints,
                        TreeResearchResultMode.DIRECT_LEARN);
            }
            targets.add(target);
        }

        for (ResearchPathUnlockPlanner.FragmentSetUse setUse
                : transaction.fragmentSetUses()) {
            PlayerProgressValueMutation.Result preflight;
            try {
                preflight = playerData.applyArchivedFragmentMutation(
                        PlayerProgressValueMutation.Request.preflight(
                                setUse.blueprintId().toString(),
                                setUse.archivedBefore(),
                                setUse.archivedAfter()));
            } catch (RuntimeException exception) {
                return Result.failure(
                        Status.TRANSACTION_FAILED,
                        id,
                        currentPoints,
                        TreeResearchResultMode.DIRECT_LEARN);
            }
            if (!preflight.successful()) {
                return Result.failure(
                        preflight.status() == PlayerProgressValueMutation.Status.CAPACITY_REACHED
                                ? Status.PROGRESSION_CAPACITY_EXHAUSTED
                                : Status.STALE_POLICY,
                        id,
                        currentPoints,
                        TreeResearchResultMode.DIRECT_LEARN);
            }
        }

        if (quote.pointCost() > 0 && !playerData.spendResearchPoints(quote.pointCost())) {
            return Result.failure(
                    Status.STALE_POLICY,
                    id,
                    playerData.getResearchPoints(),
                    TreeResearchResultMode.DIRECT_LEARN);
        }
        try {
            input.consume(transaction.ingredientPlan());
        } catch (RuntimeException exception) {
            boolean restored = rollbackPath(
                    playerData, input, inputSnapshot, knowledgeSnapshot);
            return Result.failure(
                    restored ? Status.TRANSACTION_FAILED : Status.ROLLBACK_FAILED,
                    id,
                    playerData.getResearchPoints(),
                    TreeResearchResultMode.DIRECT_LEARN);
        }

        for (ResearchPathUnlockPlanner.FragmentSetUse setUse
                : transaction.fragmentSetUses()) {
            PlayerProgressValueMutation.Result committed;
            try {
                committed = playerData.applyArchivedFragmentMutation(
                        PlayerProgressValueMutation.Request.commit(
                                setUse.blueprintId().toString(),
                                setUse.archivedBefore(),
                                setUse.archivedAfter()));
            } catch (RuntimeException exception) {
                committed = null;
            }
            if (committed == null || !committed.successful()) {
                boolean restored = rollbackPath(
                        playerData, input, inputSnapshot, knowledgeSnapshot);
                return Result.failure(
                        restored ? Status.STALE_POLICY : Status.ROLLBACK_FAILED,
                        id,
                        playerData.getResearchPoints(),
                        TreeResearchResultMode.DIRECT_LEARN);
            }
        }

        List<LearningTransition> transitions = new ArrayList<>(targets.size());
        for (LearningTarget target : targets) {
            BlueprintLearningMutation.Result committed;
            try {
                committed = playerData.applyBlueprintLearning(
                        BlueprintLearningMutation.Request.commit(
                                target.blueprintId().toString(),
                                target.legacyRecipeId().toString()));
            } catch (RuntimeException exception) {
                committed = null;
            }
            if (committed == null || !committed.committed() || !committed.learnedChanged()) {
                boolean restored = rollbackPath(
                        playerData, input, inputSnapshot, knowledgeSnapshot);
                Status failure = committed != null
                                && committed.status()
                                        == BlueprintLearningMutation.Status.CAPACITY_REACHED
                        ? Status.PROGRESSION_CAPACITY_EXHAUSTED
                        : Status.TRANSACTION_FAILED;
                return Result.failure(
                        restored ? failure : Status.ROLLBACK_FAILED,
                        id,
                        playerData.getResearchPoints(),
                        TreeResearchResultMode.DIRECT_LEARN);
            }
            transitions.add(new LearningTransition(
                    target.blueprintId(),
                    committed.learnedChanged(),
                    committed.discoveredChanged(),
                    committed.legacyRecipeChanged()));
        }
        RecentBlueprintUnlockHistory.record(
                playerData,
                BlueprintUnlockOrigin.TREE_RESEARCH,
                blueprintId,
                transitions.stream().map(LearningTransition::blueprintId).toList());
        return new Result(
                Status.SUCCESS,
                id,
                quote.pointCost(),
                playerData.getResearchPoints(),
                quote.costBypassed(),
                TreeResearchResultMode.DIRECT_LEARN,
                transitions.stream().anyMatch(LearningTransition::learnedChanged),
                transitions.stream().anyMatch(LearningTransition::discoveredChanged),
                transitions.stream().anyMatch(LearningTransition::legacyRecipeChanged),
                transitions);
    }

    private static void syncPostCommitBestEffort(
            ServerPlayer player,
            boolean recipeKnowledgeChanged) {
        try {
            if (recipeKnowledgeChanged) {
                NetworkHandler.syncPlayerRecipeData(player);
            } else {
                NetworkHandler.syncPlayerProgressionData(player);
            }
        } catch (RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Research committed for {}, but immediate progression sync failed; scheduling a retry",
                    player == null ? "unknown player" : player.getGameProfile().getName(),
                    exception);
            if (recipeKnowledgeChanged) {
                BlueprintProgressionSyncScheduler.markKnowledgeDirty(player);
            } else {
                BlueprintProgressionSyncScheduler.markDirty(player);
            }
        }
    }

    static Result research(
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            ResearchInput input,
            boolean creativePlayer) {
        return research(
                blueprintId,
                playerData,
                policyResolver,
                ignored -> null,
                input,
                creativePlayer,
                true,
                TreeResearchResultMode.CREATE_BLUEPRINT);
    }

    private static Result researchSingleWithAccess(
            ServerPlayer player,
            ResearchWorkbenchContext workbenchContext,
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            Function<ResourceLocation, LearningTarget> targetResolver,
            ResearchInput input,
            boolean creativePlayer,
            boolean blueprintsEnabled,
            TreeResearchResultMode resultMode) {
        if (!ResearchWorkbenchAuthority.validForResearch(player, workbenchContext)) {
            return Result.failure(
                    Status.WORKBENCH_TIER_REQUIRED,
                    Optional.of(blueprintId),
                    playerData.getResearchPoints(),
                    resultMode);
        }
        ResearchRouteEligibilityService.Evaluation access =
                ResearchRouteEligibilityService.evaluate(
                        player, List.of(blueprintId), workbenchContext);
        if (!access.eligible()) {
            return Result.failure(
                    accessFailure(access.summary()),
                    Optional.of(blueprintId),
                    playerData.getResearchPoints(),
                    resultMode);
        }
        return research(
                blueprintId,
                playerData,
                policyResolver,
                targetResolver,
                input,
                creativePlayer,
                blueprintsEnabled,
                resultMode);
    }

    private static Status accessFailure(ResearchAccessSummary summary) {
        return switch (summary.kind()) {
            case WORKBENCH_TIER -> Status.WORKBENCH_TIER_REQUIRED;
            case PROGRESSION_GATE -> Status.PROGRESSION_GATE_REQUIRED;
            case POLICY_UNAVAILABLE, NONE -> Status.POLICY_UNAVAILABLE;
        };
    }

    private static Predicate<ResourceLocation> pendingNodeEligibility(
            ServerPlayer player,
            IPlayerRecipeData playerData,
            ResearchWorkbenchContext workbenchContext) {
        if (player == null || workbenchContext == null) {
            return ignored -> true;
        }
        Map<ResourceLocation, Boolean> cached = new LinkedHashMap<>();
        return blueprintId -> playerData.hasBlueprint(blueprintId.toString())
                || cached.computeIfAbsent(
                        blueprintId,
                        id -> ResearchRouteEligibilityService.evaluate(
                                player, List.of(id), workbenchContext).eligible());
    }

    static Result research(
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            Function<ResourceLocation, LearningTarget> targetResolver,
            ResearchInput input,
            boolean creativePlayer,
            boolean blueprintsEnabled,
            TreeResearchResultMode resultMode) {
        Optional<ResourceLocation> id = Optional.ofNullable(blueprintId).filter(BlueprintResearchService::validId);
        if (id.isEmpty() || input == null) {
            return Result.failure(
                    Status.INVALID_INPUT,
                    id,
                    playerData == null ? 0 : playerData.getResearchPoints(),
                    resultMode);
        }
        if (playerData == null) {
            return Result.failure(Status.PLAYER_DATA_UNAVAILABLE, id, 0, resultMode);
        }
        if (policyResolver == null || resultMode == null
                || (resultMode.learnsDirectly() && targetResolver == null)) {
            return Result.failure(
                    Status.POLICY_UNAVAILABLE,
                    id,
                    playerData.getResearchPoints(),
                    resultMode);
        }

        BlueprintResearchPolicy policy;
        try {
            policy = policyResolver.apply(blueprintId);
        } catch (RuntimeException exception) {
            return Result.failure(
                    Status.POLICY_UNAVAILABLE,
                    id,
                    playerData.getResearchPoints(),
                    resultMode);
        }
        return commit(
                blueprintId,
                playerData,
                policy,
                targetResolver,
                input,
                creativePlayer,
                blueprintsEnabled,
                resultMode);
    }

    private static Result commit(
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData,
            BlueprintResearchPolicy policy,
            Function<ResourceLocation, LearningTarget> targetResolver,
            ResearchInput input,
            boolean creativePlayer,
            boolean blueprintsEnabled,
            TreeResearchResultMode resultMode) {
        int currentPoints = playerData.getResearchPoints();
        Optional<ResourceLocation> id = Optional.of(blueprintId);
        if (policy == null) {
            return Result.failure(Status.POLICY_UNAVAILABLE, id, currentPoints, resultMode);
        }
        if (!blueprintId.equals(policy.blueprintId())) {
            return Result.failure(Status.POLICY_MISMATCH, id, currentPoints, resultMode);
        }
        if (!policy.playerDataAvailable() || policy.researchPoints() != currentPoints) {
            return Result.failure(Status.STALE_POLICY, id, currentPoints, resultMode);
        }
        if (!policy.available()) {
            return Result.failure(Status.CONTENT_UNAVAILABLE, id, currentPoints, resultMode);
        }
        if (policy.blocked()) {
            return Result.failure(Status.BLOCKED, id, currentPoints, resultMode);
        }
        if (!policy.researchEnabled()) {
            return Result.failure(Status.RESEARCH_DISABLED, id, currentPoints, resultMode);
        }
        if (policy.learned()) {
            return Result.failure(Status.ALREADY_LEARNED, id, currentPoints, resultMode);
        }
        if (policy.requiresDiscovery() && !policy.discovered()) {
            return Result.failure(Status.DISCOVERY_REQUIRED, id, currentPoints, resultMode);
        }
        if (!policy.prerequisitesSatisfied()) {
            return Result.failure(Status.PREREQUISITES_REQUIRED, id, currentPoints, resultMode);
        }
        if (policy.researchCost().points() > policy.pointCap() || !policy.researchable()) {
            return Result.failure(Status.POLICY_INELIGIBLE, id, currentPoints, resultMode);
        }

        boolean bypassCost = creativePlayer && policy.creativeBypassesCost();
        List<ItemStack> inputSnapshot = input.stacks();
        if (inputSnapshot == null || inputSnapshot.stream().anyMatch(java.util.Objects::isNull)) {
            return Result.failure(Status.INVALID_INPUT, id, currentPoints, resultMode);
        }
        ResearchIngredientPlanner.Plan plan = new ResearchIngredientPlanner.Plan(new int[inputSnapshot.size()]);
        if (!bypassCost) {
            if (currentPoints < policy.researchCost().points()) {
                return Result.failure(Status.POINTS_REQUIRED, id, currentPoints, resultMode);
            }
            Optional<ResearchIngredientPlanner.Plan> resolvedPlan =
                    ResearchIngredientPlanner.plan(inputSnapshot, policy.researchCost());
            if (resolvedPlan.isEmpty()) {
                return Result.failure(Status.INGREDIENTS_REQUIRED, id, currentPoints, resultMode);
            }
            plan = resolvedPlan.orElseThrow();
        }

        PreparedLearning preparedLearning = null;
        ItemStack output = ItemStack.EMPTY;
        if (resultMode.learnsDirectly()) {
            Preparation preparation = BlueprintLearningService.prepare(
                    new BlueprintLearningService.Request(
                            BlueprintUnlockOrigin.TREE_RESEARCH,
                            blueprintId,
                            blueprintsEnabled,
                            PhysicalBlueprintLearningMode.DISABLED,
                            // The live ServerPlayer entry point rejects current
                            // exemptions before entering this pure transaction.
                            // Do not re-enter Forge config from the injectable
                            // commit boundary used by deterministic tests/tools.
                            false),
                    playerData,
                    targetResolver,
                    ignored -> policy);
            if (!preparation.ready()) {
                return Result.failure(
                        mapLearningFailure(
                                preparation.failure().orElseThrow().status()),
                        id,
                        currentPoints,
                        resultMode);
            }
            preparedLearning = preparation.prepared().orElseThrow();
        } else {
            if (!input.canAcceptOutput()) {
                return Result.failure(Status.OUTPUT_FULL, id, currentPoints, resultMode);
            }
            try {
                output = input.createOutput(blueprintId);
            } catch (RuntimeException exception) {
                return Result.failure(Status.TRANSACTION_FAILED, id, currentPoints, resultMode);
            }
            if (output == null || output.isEmpty()) {
                return Result.failure(Status.TRANSACTION_FAILED, id, currentPoints, resultMode);
            }
        }

        int pointsSpent = bypassCost ? 0 : policy.researchCost().points();
        if (pointsSpent > 0 && !playerData.spendResearchPoints(pointsSpent)) {
            return Result.failure(
                    Status.STALE_POLICY,
                    id,
                    playerData.getResearchPoints(),
                    resultMode);
        }
        try {
            input.consume(plan);
            if (resultMode.createsPhysicalBlueprint() && !input.deliver(output)) {
                boolean restored = rollback(
                        playerData, input, inputSnapshot, currentPoints);
                return Result.failure(
                        restored ? Status.TRANSACTION_FAILED : Status.ROLLBACK_FAILED,
                        id,
                        playerData.getResearchPoints(),
                        resultMode);
            }
        } catch (RuntimeException exception) {
            boolean restored = rollback(
                    playerData, input, inputSnapshot, currentPoints);
            return Result.failure(
                    restored ? Status.TRANSACTION_FAILED : Status.ROLLBACK_FAILED,
                    id,
                    playerData.getResearchPoints(),
                    resultMode);
        }

        BlueprintLearningService.Result learning = null;
        if (resultMode.learnsDirectly()) {
            learning = BlueprintLearningService.commitPrepared(
                    preparedLearning, playerData);
            if (!learning.successful()) {
                boolean restored = rollback(
                        playerData, input, inputSnapshot, currentPoints);
                return Result.failure(
                        restored
                                ? mapLearningFailure(learning.status())
                                : Status.ROLLBACK_FAILED,
                        id,
                        playerData.getResearchPoints(),
                        resultMode);
            }
        }
        return new Result(
                Status.SUCCESS,
                id,
                pointsSpent,
                playerData.getResearchPoints(),
                bypassCost,
                resultMode,
                learning != null && learning.learnedChanged(),
                learning != null && learning.discoveredChanged(),
                learning != null && learning.legacyRecipeChanged());
    }

    private static Status mapLearningFailure(BlueprintLearningService.Status status) {
        return switch (status) {
            case SUCCESS -> throw new IllegalArgumentException(
                    "successful learning cannot map to research failure");
            case INVALID_INPUT, INVALID_IDENTITY -> Status.INVALID_INPUT;
            case PLAYER_DATA_UNAVAILABLE -> Status.PLAYER_DATA_UNAVAILABLE;
            case CONTENT_UNAVAILABLE, BLUEPRINTS_DISABLED, PROGRESSION_EXEMPT ->
                    Status.CONTENT_UNAVAILABLE;
            case POLICY_UNAVAILABLE -> Status.POLICY_UNAVAILABLE;
            case POLICY_MISMATCH -> Status.POLICY_MISMATCH;
            case STALE_POLICY -> Status.STALE_POLICY;
            case BLOCKED -> Status.BLOCKED;
            case ALREADY_LEARNED -> Status.ALREADY_LEARNED;
            case PREREQUISITES_REQUIRED -> Status.PREREQUISITES_REQUIRED;
            case PROGRESSION_CAPACITY_EXHAUSTED ->
                    Status.PROGRESSION_CAPACITY_EXHAUSTED;
            case PHYSICAL_BLUEPRINT_LEARNING_DISABLED, TRANSACTION_FAILED ->
                    Status.TRANSACTION_FAILED;
        };
    }

    private static boolean rollback(
            IPlayerRecipeData playerData,
            ResearchInput input,
            List<ItemStack> inputSnapshot,
            int originalPoints) {
        boolean inventoryRestored = true;
        try {
            input.restore(inputSnapshot);
        } catch (RuntimeException exception) {
            inventoryRestored = false;
            TaCZWeaponBlueprints.LOGGER.error(
                    "Research transaction failed and its inventory snapshot could not be restored",
                    exception);
        }
        boolean pointsRestored;
        try {
            pointsRestored = playerData.setResearchPoints(originalPoints);
        } catch (RuntimeException exception) {
            pointsRestored = false;
            TaCZWeaponBlueprints.LOGGER.error(
                    "Research transaction failed and restoring its RP balance threw",
                    exception);
        }
        if (!pointsRestored) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Research transaction failed and its RP balance could not be restored to {}",
                    originalPoints);
        }
        return inventoryRestored && pointsRestored;
    }

    private static boolean rollbackPath(
            IPlayerRecipeData playerData,
            ResearchInput input,
            List<ItemStack> inputSnapshot,
            KnowledgeSnapshot knowledgeSnapshot) {
        boolean inventoryRestored = true;
        try {
            input.restore(inputSnapshot);
        } catch (RuntimeException exception) {
            inventoryRestored = false;
            TaCZWeaponBlueprints.LOGGER.error(
                    "Research path transaction failed and its inventory snapshot could not be restored",
                    exception);
        }
        boolean progressionRestored;
        try {
            progressionRestored = playerData.replaceProgression(
                    knowledgeSnapshot.learnedBlueprints(),
                    knowledgeSnapshot.discoveredBlueprints(),
                    knowledgeSnapshot.researchPoints());
        } catch (RuntimeException exception) {
            progressionRestored = false;
            TaCZWeaponBlueprints.LOGGER.error(
                    "Research path transaction failed and restoring progression threw",
                    exception);
        }
        boolean recipesRestored = true;
        try {
            playerData.replaceRecipes(knowledgeSnapshot.learnedRecipes());
        } catch (RuntimeException exception) {
            recipesRestored = false;
            TaCZWeaponBlueprints.LOGGER.error(
                    "Research path transaction failed and restoring recipes threw",
                    exception);
        }
        boolean supplementalRestored;
        try {
            supplementalRestored = playerData.replaceSupplementalProgression(
                    knowledgeSnapshot.archivedFragments(),
                    knowledgeSnapshot.progressionCriteria());
        } catch (RuntimeException exception) {
            supplementalRestored = false;
            TaCZWeaponBlueprints.LOGGER.error(
                    "Research path transaction failed and restoring supplemental progress threw",
                    exception);
        }
        if (!progressionRestored) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Research path transaction failed and its progression snapshot could not be restored");
        }
        return inventoryRestored && progressionRestored && recipesRestored
                && supplementalRestored;
    }

    private static ResearchInput playerInventoryInput(ServerPlayer player) {
        return new ResearchInput() {
            @Override
            public List<ItemStack> stacks() {
                return player.getInventory().items.stream()
                        .map(ItemStack::copy)
                        .toList();
            }

            @Override
            public boolean canAcceptOutput() {
                // Ingredient consumption normally opens a slot. If it does not,
                // deliver() safely drops the researched blueprint at the player.
                return true;
            }

            @Override
            public void consume(ResearchIngredientPlanner.Plan plan) {
                List<ItemStack> items = player.getInventory().items;
                if (plan.slotCount() != items.size()) {
                    throw new IllegalStateException("player inventory changed before research commit");
                }
                for (int slot = 0; slot < items.size(); slot++) {
                    int amount = plan.decrement(slot);
                    if (amount < 0 || items.get(slot).getCount() < amount) {
                        throw new IllegalStateException("player inventory changed before research commit");
                    }
                    items.get(slot).shrink(amount);
                }
                player.getInventory().setChanged();
            }

            @Override
            public void restore(List<ItemStack> snapshot) {
                List<ItemStack> items = player.getInventory().items;
                if (snapshot.size() != items.size()) {
                    throw new IllegalStateException("cannot restore a resized player inventory");
                }
                for (int slot = 0; slot < items.size(); slot++) {
                    items.set(slot, snapshot.get(slot).copy());
                }
                player.getInventory().setChanged();
            }

            @Override
            public ItemStack createOutput(ResourceLocation id) {
                return BlueprintItem.createBlueprint(id.toString());
            }

            @Override
            public boolean deliver(ItemStack output) {
                return deliverOutput(
                        output,
                        player.getInventory()::add,
                        remainder -> player.drop(remainder, false) != null);
            }
        };
    }

    /**
     * Delivers the single transaction output and verifies the fallback entity was actually
     * created. Keeping this boundary explicit prevents a cancelled Forge toss event from being
     * reported as a successful research transaction.
     */
    static boolean deliverOutput(
            ItemStack output,
            Consumer<ItemStack> inventoryInsertion,
            Predicate<ItemStack> overflowDrop) {
        if (output == null || output.isEmpty() || output.getCount() != 1
                || inventoryInsertion == null || overflowDrop == null) {
            return false;
        }
        inventoryInsertion.accept(output);
        return output.isEmpty() || overflowDrop.test(output);
    }

    private static boolean validId(ResourceLocation id) {
        return id != null && id.toString().length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;
    }

    interface ResearchInput {
        List<ItemStack> stacks();

        boolean canAcceptOutput();

        void consume(ResearchIngredientPlanner.Plan plan);

        void restore(List<ItemStack> snapshot);

        ItemStack createOutput(ResourceLocation blueprintId);

        /** Returns false only when no part of the output was delivered. */
        boolean deliver(ItemStack output);
    }

    public enum Status {
        SUCCESS,
        INVALID_INPUT,
        PLAYER_DATA_UNAVAILABLE,
        POLICY_UNAVAILABLE,
        POLICY_MISMATCH,
        STALE_POLICY,
        CONTENT_UNAVAILABLE,
        BLOCKED,
        RESEARCH_DISABLED,
        ALREADY_LEARNED,
        DISCOVERY_REQUIRED,
        PREREQUISITES_REQUIRED,
        POINTS_REQUIRED,
        INGREDIENTS_REQUIRED,
        OUTPUT_FULL,
        POLICY_INELIGIBLE,
        TRANSACTION_FAILED,
        PROGRESSION_CAPACITY_EXHAUSTED,
        ROLLBACK_FAILED,
        PATH_TOO_LARGE,
        ROUTE_TOO_COMPLEX,
        TECH_TREE_UNAVAILABLE,
        UNSATISFIABLE,
        WORKBENCH_TIER_REQUIRED,
        PROGRESSION_GATE_REQUIRED
    }

    /**
     * Completed transaction result.
     *
     * @param balanceAfterCost RP balance immediately after paying the research
     *     cost, before live discovery or learning awards are dispatched
     */
    public record Result(
            Status status,
            Optional<ResourceLocation> blueprintId,
            int spentPoints,
            int balanceAfterCost,
            boolean costBypassed,
            TreeResearchResultMode resultMode,
            boolean learnedChanged,
            boolean discoveredChanged,
            boolean legacyRecipeChanged,
            List<LearningTransition> transitions) {
        public Result {
            if (status == null || resultMode == null
                    || spentPoints < 0 || balanceAfterCost < 0
                    || spentPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || balanceAfterCost > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
                throw new IllegalArgumentException("invalid blueprint research result");
            }
            blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
            transitions = transitions == null ? List.of() : List.copyOf(transitions);
            if (blueprintId.filter(id -> id.toString().length()
                    > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH).isPresent()
                    || transitions.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                    || transitions.stream().anyMatch(java.util.Objects::isNull)
                    || transitions.stream().map(LearningTransition::blueprintId).distinct().count()
                            != transitions.size()) {
                throw new IllegalArgumentException("research result contains invalid transitions");
            }
            if (status == Status.SUCCESS) {
                if (blueprintId.isEmpty()
                        || (costBypassed && spentPoints != 0)
                        || spentPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                                - balanceAfterCost
                        || (resultMode.learnsDirectly() && !learnedChanged)
                        || (resultMode.learnsDirectly() && transitions.isEmpty())
                        || (resultMode.learnsDirectly()
                                && (!blueprintId.orElseThrow().equals(
                                        transitions.get(transitions.size() - 1).blueprintId())
                                        || learnedChanged != transitions.stream()
                                                .anyMatch(LearningTransition::learnedChanged)
                                        || discoveredChanged != transitions.stream()
                                                .anyMatch(LearningTransition::discoveredChanged)
                                        || legacyRecipeChanged != transitions.stream()
                                                .anyMatch(LearningTransition::legacyRecipeChanged)))
                        || (resultMode.createsPhysicalBlueprint()
                                && (learnedChanged
                                        || discoveredChanged
                                        || legacyRecipeChanged
                                        || !transitions.isEmpty()))) {
                    throw new IllegalArgumentException("successful research result is inconsistent");
                }
            } else if (spentPoints != 0 || costBypassed
                    || learnedChanged || discoveredChanged || legacyRecipeChanged
                    || !transitions.isEmpty()) {
                throw new IllegalArgumentException("failed research cannot spend or bypass costs");
            }
        }

        /** Compatibility constructor for the original single-node result shape. */
        public Result(
                Status status,
                Optional<ResourceLocation> blueprintId,
                int spentPoints,
                int balanceAfterCost,
                boolean costBypassed,
                TreeResearchResultMode resultMode,
                boolean learnedChanged,
                boolean discoveredChanged,
                boolean legacyRecipeChanged) {
            this(
                    status,
                    blueprintId,
                    spentPoints,
                    balanceAfterCost,
                    costBypassed,
                    resultMode,
                    learnedChanged,
                    discoveredChanged,
                    legacyRecipeChanged,
                    status == Status.SUCCESS && resultMode != null
                                    && resultMode.learnsDirectly()
                                    && learnedChanged && blueprintId != null
                                    && blueprintId.isPresent()
                            ? List.of(new LearningTransition(
                                    blueprintId.orElseThrow(),
                                    learnedChanged,
                                    discoveredChanged,
                                    legacyRecipeChanged))
                            : List.of());
        }

        /** Compatibility constructor for menu-generated failures. */
        public Result(
                Status status,
                Optional<ResourceLocation> blueprintId,
                int spentPoints,
                int balanceAfterCost,
                boolean costBypassed) {
            this(
                    status,
                    blueprintId,
                    spentPoints,
                    balanceAfterCost,
                    costBypassed,
                    TreeResearchResultMode.CREATE_BLUEPRINT,
                    false,
                    false,
                    false);
        }

        public boolean successful() {
            return status == Status.SUCCESS;
        }

        private static Result failure(
                Status status,
                Optional<ResourceLocation> blueprintId,
                int currentBalance) {
            return failure(
                    status,
                    blueprintId,
                    currentBalance,
                    TreeResearchResultMode.CREATE_BLUEPRINT);
        }

        private static Result failure(
                Status status,
                Optional<ResourceLocation> blueprintId,
                int currentBalance,
                TreeResearchResultMode resultMode) {
            int boundedBalance = Math.max(
                    0,
                    Math.min(PlayerProgressionLimits.MAX_RESEARCH_POINTS, currentBalance));
            return new Result(
                    status,
                    blueprintId,
                    0,
                    boundedBalance,
                    false,
                    resultMode == null
                            ? TreeResearchResultMode.DIRECT_LEARN
                            : resultMode,
                    false,
                    false,
                    false);
        }
    }

    public record LearningTransition(
            ResourceLocation blueprintId,
            boolean learnedChanged,
            boolean discoveredChanged,
            boolean legacyRecipeChanged) {
        public LearningTransition {
            if (!validId(blueprintId) || !learnedChanged) {
                throw new IllegalArgumentException("invalid research learning transition");
            }
        }
    }

    private record KnowledgeSnapshot(
            Set<String> learnedBlueprints,
            Set<String> discoveredBlueprints,
            Set<String> learnedRecipes,
            int researchPoints,
            Map<String, Integer> archivedFragments,
            Map<String, Integer> progressionCriteria) {
        private static KnowledgeSnapshot capture(IPlayerRecipeData playerData) {
            return new KnowledgeSnapshot(
                    Set.copyOf(playerData.getLearnedBlueprints()),
                    Set.copyOf(playerData.getDiscoveredBlueprints()),
                    Set.copyOf(playerData.getLearnedRecipes()),
                    playerData.getResearchPoints(),
                    Map.copyOf(playerData.getArchivedBlueprintFragments()),
                    Map.copyOf(playerData.getProgressionCriteria()));
        }
    }
}
