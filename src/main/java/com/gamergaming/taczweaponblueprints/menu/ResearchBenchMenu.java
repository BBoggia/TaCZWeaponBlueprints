package com.gamergaming.taczweaponblueprints.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModBlocks;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.init.ModMenus;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.BlueprintLearningService;
import com.gamergaming.taczweaponblueprints.progression.BlueprintResearchService;
import com.gamergaming.taczweaponblueprints.progression.ResearchAffordabilitySnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.progression.ResearchGuidanceSnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchIngredientPlanner;
import com.gamergaming.taczweaponblueprints.progression.ResearchPathAuthority;
import com.gamergaming.taczweaponblueprints.progression.ResearchPathUnlockPlanner;
import com.gamergaming.taczweaponblueprints.progression.ResearchRouteEvaluationService;
import com.gamergaming.taczweaponblueprints.progression.ResearchRouteFailureReporter;
import com.gamergaming.taczweaponblueprints.progression.ResearchRouteFingerprint;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/** Research-only, slotless server menu for the permanent Research Tree. */
public final class ResearchBenchMenu extends AbstractContainerMenu {
    private static final long AFFORDABILITY_HEARTBEAT_INTERVAL_TICKS = 100L;
    private final ContainerLevelAccess access;
    private final Player owner;
    private final Inventory playerInventory;
    private ResourceLocation selectedBlueprint;
    private ResearchSelectionPreview preview = ResearchSelectionPreview.EMPTY;
    private ResearchInventorySnapshot previewInventory = ResearchInventorySnapshot.EMPTY;
    private final ResearchBenchRequestLimiter requestLimiter =
            new ResearchBenchRequestLimiter();
    private PendingAffordabilityBatch pendingAffordabilityBatch;
    private boolean suppressAuthoritativePreviewRefresh;
    private boolean authoritativePreviewRefreshPending;
    private final DataSlot routeGuidanceAvailable = DataSlot.standalone();

    public ResearchBenchMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, ContainerLevelAccess.create(
                inventory.player.level(), buffer.readBlockPos()));
    }

    public static ResearchBenchMenu server(
            int containerId,
            Inventory inventory,
            Level level,
            BlockPos pos) {
        return new ResearchBenchMenu(
                containerId, inventory, ContainerLevelAccess.create(level, pos));
    }

    private ResearchBenchMenu(
            int containerId,
            Inventory inventory,
            ContainerLevelAccess access) {
        super(ModMenus.RESEARCH_BENCH.get(), containerId);
        this.access = access;
        this.owner = inventory.player;
        this.playerInventory = inventory;
        if (!inventory.player.level().isClientSide) {
            refreshRouteGuidanceAvailability();
        }
        addDataSlot(routeGuidanceAvailable);
    }

    public ResearchSelectionPreview preview() {
        return preview;
    }

    public Optional<ResourceLocation> selectedBlueprint() {
        return Optional.ofNullable(selectedBlueprint);
    }

    /** Whether the server supports exact path pricing for this open Bench. */
    public boolean routeGuidanceAvailable() {
        return routeGuidanceAvailable.get() != 0;
    }

    public void acceptPreview(ResearchSelectionPreview preview) {
        this.preview = preview == null ? ResearchSelectionPreview.EMPTY : preview;
        this.selectedBlueprint = this.preview.blueprintId().orElse(null);
    }

    public void refreshAuthoritativePreview(ServerPlayer player) {
        if (suppressAuthoritativePreviewRefresh
                || player == null || player.containerMenu != this || !stillValid(player)) {
            return;
        }
        if (selectedBlueprint == null
                || ResearchPlanningAdmission.admit(serverTick(player))) {
            refreshPreview(player);
        } else {
            authoritativePreviewRefreshPending = true;
        }
    }

    /**
     * Invalidates a pre-reset selection before the reset publishes new progression.
     * This prevents synchronization from planning a now-missing prerequisite closure
     * for the node that happened to be selected before the operator reset.
     */
    public void clearAuthoritativeSelection(ServerPlayer player) {
        if (player != null && player.containerMenu == this && stillValid(player)) {
            selectedBlueprint = null;
            refreshPreview(player);
        }
    }

    public Optional<ActionResult> handleAction(
            ServerPlayer player,
            ResearchBenchResearchAction action,
            Optional<ResourceLocation> requestedId) {
        Optional<ResearchRouteFingerprint> compatibilityFingerprint =
                action == ResearchBenchResearchAction.RESEARCH
                        ? preview.routeFingerprint()
                        : Optional.empty();
        return handleAction(
                player, action, requestedId, compatibilityFingerprint);
    }

    public Optional<ActionResult> handleAction(
            ServerPlayer player,
            ResearchBenchResearchAction action,
            Optional<ResourceLocation> requestedId,
            Optional<ResearchRouteFingerprint> routeFingerprint) {
        if (player == null || action == null
                || player.containerMenu != this || !stillValid(player)) {
            return Optional.empty();
        }
        if (!ResearchBenchResearchActionValidator.accepts(
                action, selectedBlueprint(), requestedId)) {
            return Optional.of(new ActionResult(
                    action,
                    requestedId,
                    ActionResultCode.INVALID_INPUT));
        }
        return Optional.of(switch (action) {
            case SELECT -> select(player, requestedId == null
                    ? null
                    : requestedId.orElse(null));
            case RESEARCH -> research(
                    player,
                    requestedId.orElseThrow(),
                    routeFingerprint == null ? Optional.empty() : routeFingerprint);
        });
    }

    private ActionResult select(ServerPlayer player, ResourceLocation blueprintId) {
        ResearchBenchRequestLimiter.Decision admission = requestLimiter.admitSelection(
                blueprintId, selectedBlueprint, player.tickCount);
        if (admission == ResearchBenchRequestLimiter.Decision.DUPLICATE) {
            return new ActionResult(
                    ResearchBenchResearchAction.SELECT,
                    Optional.of(blueprintId),
                    ActionResultCode.ACCEPTED);
        }
        if (admission == ResearchBenchRequestLimiter.Decision.THROTTLE) {
            return new ActionResult(
                    ResearchBenchResearchAction.SELECT,
                    Optional.of(blueprintId),
                    ActionResultCode.REQUEST_THROTTLED);
        }
        if (blueprintId != null
                && !ResearchPlanningAdmission.admitInteractive(serverTick(player))) {
            return new ActionResult(
                    ResearchBenchResearchAction.SELECT,
                    Optional.of(blueprintId),
                    ActionResultCode.REQUEST_THROTTLED);
        }
        if (blueprintId == null) {
            selectedBlueprint = null;
        } else {
            BlueprintResearchPolicy policy = resolvePolicy(player, blueprintId).orElse(null);
            selectedBlueprint = policy != null
                    && policy.visibility().allowsServerSelection()
                    ? blueprintId
                    : null;
        }
        refreshPreview(player);
        boolean accepted = blueprintId == null
                ? selectedBlueprint == null
                : blueprintId.equals(selectedBlueprint);
        return new ActionResult(
                ResearchBenchResearchAction.SELECT,
                Optional.ofNullable(blueprintId),
                accepted ? ActionResultCode.ACCEPTED : ActionResultCode.REJECTED);
    }

    private ActionResult research(
            ServerPlayer player,
            ResourceLocation requestedId,
            Optional<ResearchRouteFingerprint> requestedFingerprint) {
        if (!requestedId.equals(selectedBlueprint)) {
            return researchResult(
                    player,
                    requestedId,
                    new BlueprintResearchService.Result(
                            BlueprintResearchService.Status.INVALID_INPUT,
                            Optional.of(requestedId),
                            0,
                            player.getCapability(
                                    com.gamergaming.taczweaponblueprints.init.ModCapabilities
                                            .PLAYER_RECIPE_DATA)
                                    .map(data -> data.getResearchPoints()).orElse(0),
                            false),
                    preview);
        }
        if (requestLimiter.admitResearch(player.tickCount)
                == ResearchBenchRequestLimiter.Decision.THROTTLE
                || !ResearchPlanningAdmission.admitInteractive(serverTick(player))) {
            return new ActionResult(
                    ResearchBenchResearchAction.RESEARCH,
                    Optional.of(requestedId),
                    ActionResultCode.REQUEST_THROTTLED);
        }

        MigrationAttempt migration = migrateLegacyKnowledge(player);
        if (migration.failed()) {
            synchronizeMigratedKnowledge(player);
            return researchResult(
                    player,
                    requestedId,
                    new BlueprintResearchService.Result(
                            BlueprintResearchService.Status.POLICY_UNAVAILABLE,
                            Optional.of(requestedId),
                            0,
                            player.getCapability(
                                            com.gamergaming.taczweaponblueprints.init
                                                    .ModCapabilities.PLAYER_RECIPE_DATA)
                                    .map(data -> data.getResearchPoints()).orElse(0),
                            false),
                    preview);
        }
        PreparedPreview current = buildPreparedPreview(player);
        if (!ResearchPreviewCommitGuard.accepts(
                current.directPathResearch(),
                current.preview(),
                requestedFingerprint)) {
            publishPreview(player, current.preview());
            if (migration.changed()) {
                synchronizeMigratedKnowledge(player);
            }
            player.displayClientMessage(Component.translatable(
                    "message.taczweaponblueprints.research.stale_preview"), true);
            return new ActionResult(
                    ResearchBenchResearchAction.RESEARCH,
                    Optional.of(requestedId),
                    ActionResultCode.STALE_PREVIEW);
        }

        BlueprintResearchService.Result transaction;
        suppressAuthoritativePreviewRefresh = true;
        try {
            transaction = current.directPathResearch()
                    ? current.path()
                            .map(path -> BlueprintResearchService
                                    .researchPreparedPathFromInventory(
                                            player, requestedId, path))
                            .orElseGet(() -> new BlueprintResearchService.Result(
                                    BlueprintResearchService.Status.STALE_POLICY,
                                    Optional.of(requestedId),
                                    0,
                                    current.preview().pointBalance(),
                                    false,
                                    com.gamergaming.taczweaponblueprints.progression
                                            .TreeResearchResultMode.DIRECT_LEARN,
                                    false,
                                    false,
                                    false))
                    : BlueprintResearchService.researchFromInventory(player, requestedId);
        } finally {
            suppressAuthoritativePreviewRefresh = false;
        }
        if (migration.changed() && !transaction.successful()) {
            synchronizeMigratedKnowledge(player);
        }
        return researchResult(player, requestedId, transaction, current.preview());
    }

    private ActionResult researchResult(
            ServerPlayer player,
            ResourceLocation requestedId,
            BlueprintResearchService.Result transaction) {
        return researchResult(player, requestedId, transaction, null);
    }

    private ActionResult researchResult(
            ServerPlayer player,
            ResourceLocation requestedId,
            BlueprintResearchService.Result transaction,
            ResearchSelectionPreview failedPreview) {
        player.displayClientMessage(
                transaction.successful() && transaction.transitions().size() > 1
                        ? Component.translatable(
                                "message.taczweaponblueprints.research.success_path",
                                transaction.transitions().size())
                        : Component.translatable(researchMessage(transaction.status())),
                true);
        if (transaction.successful()) {
            selectedBlueprint = null;
            refreshPreview(player);
        } else if (failedPreview != null
                && transaction.status() != BlueprintResearchService.Status.ROLLBACK_FAILED) {
            publishPreview(player, failedPreview);
        } else {
            refreshPreview(player);
        }
        return new ActionResult(
                ResearchBenchResearchAction.RESEARCH,
                Optional.of(requestedId),
                ActionResultCode.valueOf(transaction.status().name()));
    }

    /**
     * Menus are broadcast every server tick. Refresh only when allocation-relevant
     * inventory contents changed while a blueprint is selected.
     */
    @Override
    public void broadcastChanges() {
        if (owner instanceof ServerPlayer) {
            refreshRouteGuidanceAvailability();
        }
        super.broadcastChanges();
        if (selectedBlueprint != null
                && owner instanceof ServerPlayer serverPlayer
                && serverPlayer.containerMenu == this
                && stillValid(serverPlayer)
                && (authoritativePreviewRefreshPending
                        || !previewInventory.matches(playerInventory.items))
                && ResearchPlanningAdmission.admit(serverTick(serverPlayer))) {
            refreshPreview(serverPlayer);
        }
        processAffordabilityBatch();
    }

    /** Queues one bounded sweep batch; one target is evaluated per admitted server tick. */
    public Optional<AffordabilityResult> beginAffordabilityRequest(
            ServerPlayer player,
            int requestId,
            long publicationGeneration,
            List<ResourceLocation> targetIds) {
        if (player == null || requestId < 1 || publicationGeneration == Long.MIN_VALUE
                || targetIds == null || targetIds.isEmpty()
                || targetIds.size() > ResearchAffordabilitySnapshot.MAX_TARGETS_PER_BATCH
                || targetIds.stream().anyMatch(java.util.Objects::isNull)
                || targetIds.stream().distinct().count() != targetIds.size()
                || player.containerMenu != this || !stillValid(player)) {
            return Optional.of(AffordabilityResult.rejected());
        }
        if (!ModConfigs.BLUEPRINT.progressionSnapshot()
                .treeResearchResultMode().learnsDirectly()) {
            return Optional.of(AffordabilityResult.rejected());
        }
        if (pendingAffordabilityBatch != null
                || requestLimiter.admitAffordability(serverTick(player))
                        == ResearchBenchRequestLimiter.Decision.THROTTLE) {
            return Optional.of(AffordabilityResult.throttled());
        }
        IPlayerRecipeData data = player.getCapability(
                        com.gamergaming.taczweaponblueprints.init.ModCapabilities
                                .PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        ResearchTreeGraph graph = data == null
                ? null
                : BlueprintResearchDataManager.INSTANCE.treeFor(data);
        if (graph == null || targetIds.stream().anyMatch(id -> graph.node(id)
                .filter(node -> node.visibility().revealsIdentity()).isEmpty())) {
            return Optional.of(AffordabilityResult.rejected());
        }
        pendingAffordabilityBatch = new PendingAffordabilityBatch(
                player,
                requestId,
                publicationGeneration,
                targetIds,
                graph,
                new ArrayList<>());
        ResearchPlanningAdmission.registerQueued(player.getUUID());
        return Optional.empty();
    }

    private void processAffordabilityBatch() {
        PendingAffordabilityBatch batch = pendingAffordabilityBatch;
        if (batch == null) {
            return;
        }
        ServerPlayer player = batch.player();
        if (player.containerMenu != this
                || !stillValid(player)
                || !NetworkHandler.matchesResearchGeneration(
                        player, batch.publicationGeneration())) {
            cancelAffordabilityBatch();
            NetworkHandler.sendResearchAffordabilityResult(
                    player,
                    containerId,
                    batch.requestId(),
                    batch.publicationGeneration(),
                    AffordabilityResult.rejected());
            return;
        }
        long currentServerTick = serverTick(player);
        if (Math.floorMod(currentServerTick, AFFORDABILITY_HEARTBEAT_INTERVAL_TICKS) == 0L) {
            NetworkHandler.sendResearchAffordabilityResult(
                    player,
                    containerId,
                    batch.requestId(),
                    batch.publicationGeneration(),
                    AffordabilityResult.queued());
        }
        if (!ResearchPlanningAdmission.admitQueued(currentServerTick, player.getUUID())) {
            return;
        }
        ResourceLocation targetId = batch.targetIds().get(batch.entries().size());
        ResearchAffordabilitySnapshot.Entry entry;
        try {
            PreparedRouteEvaluation prepared = prepareRouteEvaluation(player, targetId)
                    .orElse(null);
            ResearchGuidanceSnapshot guidance = prepared == null
                    ? null
                    : ResearchRouteEvaluationService.guidanceSnapshot(
                            prepared.evaluation(), batch.publicGraph(), prepared.costMode())
                            .orElse(null);
            entry = guidance == null
                    ? new ResearchAffordabilitySnapshot.Entry(
                            targetId,
                            ResearchGuidanceSnapshot.State.ROUTE_UNAVAILABLE,
                            true)
                    : new ResearchAffordabilitySnapshot.Entry(
                            targetId,
                            guidance.state(),
                            guidance.transactionCapacityAvailable());
        } catch (RuntimeException exception) {
            ResearchRouteFailureReporter.report("Affordable Now batch evaluation", exception);
            entry = new ResearchAffordabilitySnapshot.Entry(
                    targetId,
                    ResearchGuidanceSnapshot.State.ROUTE_UNAVAILABLE,
                    true);
        }
        batch.entries().add(entry);
        if (batch.entries().size() < batch.targetIds().size()) {
            return;
        }
        cancelAffordabilityBatch();
        NetworkHandler.sendResearchAffordabilityResult(
                player,
                containerId,
                batch.requestId(),
                batch.publicationGeneration(),
                AffordabilityResult.success(
                        new ResearchAffordabilitySnapshot(batch.entries())));
    }

    private void refreshPreview(ServerPlayer player) {
        publishPreview(player, buildPreview(player));
    }

    private void publishPreview(ServerPlayer player, ResearchSelectionPreview next) {
        preview = next;
        authoritativePreviewRefreshPending = false;
        previewInventory = ResearchInventorySnapshot.capture(playerInventory.items);
        super.broadcastChanges();
        NetworkHandler.sendResearchBenchPreview(player, containerId, next);
    }

    private ResearchSelectionPreview buildPreview(ServerPlayer player) {
        return buildPreparedPreview(player).preview();
    }

    private PreparedPreview buildPreparedPreview(ServerPlayer player) {
        boolean directPathResearch = ModConfigs.BLUEPRINT.progressionSnapshot()
                .treeResearchResultMode().learnsDirectly();
        if (selectedBlueprint == null) {
            return PreparedPreview.empty(directPathResearch);
        }
        PreparedRouteEvaluation prepared = prepareRouteEvaluation(
                player, selectedBlueprint).orElse(null);
        if (prepared == null) {
            return PreparedPreview.empty(directPathResearch);
        }
        ResearchRouteEvaluationService.Evaluation evaluation = prepared.evaluation();
        List<ResearchIngredientPlanner.Requirement> combinedIngredients =
                evaluation.requirements();
        ResearchIngredientPlanner.Allocation inventoryAllocation = evaluation.allocation();
        List<ResearchSelectionPreview.IngredientPreview> ingredients = new ArrayList<>();
        for (int ingredientIndex = 0;
                ingredientIndex < Math.min(
                        combinedIngredients.size(),
                        com.gamergaming.taczweaponblueprints.resource.research
                                .BlueprintResearchCost.MAX_INGREDIENT_TYPES);
                ingredientIndex++) {
            ResearchIngredientPlanner.Requirement ingredient =
                    combinedIngredients.get(ingredientIndex);
            List<ResourceLocation> items = ingredient.items();
            if (items.isEmpty() && ingredient.tag().isPresent()) {
                items = ForgeRegistries.ITEMS.tags()
                        .getTag(TagKey.create(
                                Registries.ITEM, ingredient.tag().orElseThrow()))
                        .stream()
                        .map(ForgeRegistries.ITEMS::getKey)
                        .filter(java.util.Objects::nonNull)
                        .limit(BlueprintResearchIngredient.MAX_ITEMS)
                        .toList();
            }
            ingredients.add(new ResearchSelectionPreview.IngredientPreview(
                    items,
                    ingredient.tag(),
                    ingredient.count(),
                    inventoryAllocation.allocatedForIngredient(ingredientIndex)));
        }
        return new PreparedPreview(
                new ResearchSelectionPreview(
                        Optional.of(selectedBlueprint),
                        evaluation.pointCost(),
                        evaluation.pointBalance(),
                        evaluation.policyEligible(),
                        evaluation.ingredientsSatisfied(),
                        evaluation.transactionCapacityAvailable(),
                        evaluation.ready(),
                        evaluation.costBypassed(),
                        ingredients,
                        evaluation.unlockCount(),
                        combinedIngredients.size(),
                        ResearchSelectionPreview.PathPlanningState.fromStatus(
                                evaluation.planningStatus()),
                        prepared.costMode(),
                        evaluation.routeFingerprint()),
                directPathResearch,
                evaluation.path());
    }

    /** Evaluates a public target without changing the menu's current selection. */
    public GuidanceResult handleGuidanceRequest(
            ServerPlayer player,
            ResourceLocation targetId) {
        if (player == null || targetId == null || player.containerMenu != this
                || !stillValid(player)) {
            return GuidanceResult.rejected();
        }
        if (!ModConfigs.BLUEPRINT.progressionSnapshot()
                .treeResearchResultMode().learnsDirectly()) {
            return GuidanceResult.rejected();
        }
        if (requestLimiter.admitGuidance(serverTick(player))
                        == ResearchBenchRequestLimiter.Decision.THROTTLE
                || !ResearchPlanningAdmission.admit(serverTick(player))) {
            return GuidanceResult.throttled();
        }
        PreparedRouteEvaluation prepared = prepareRouteEvaluation(player, targetId)
                .orElse(null);
        if (prepared == null) {
            return GuidanceResult.rejected();
        }
        try {
            var graph = BlueprintResearchDataManager.INSTANCE.treeFor(prepared.playerData());
            return ResearchRouteEvaluationService.guidanceSnapshot(
                            prepared.evaluation(), graph, prepared.costMode())
                    .map(GuidanceResult::success)
                    .orElseGet(GuidanceResult::rejected);
        } catch (RuntimeException exception) {
            ResearchRouteFailureReporter.report("guidance response construction", exception);
            return GuidanceResult.rejected();
        }
    }

    private Optional<PreparedRouteEvaluation> prepareRouteEvaluation(
            ServerPlayer player,
            ResourceLocation targetId) {
        if (player == null || targetId == null) {
            return Optional.empty();
        }
        var config = ModConfigs.BLUEPRINT.progressionSnapshot();
        boolean directPathResearch = config.treeResearchResultMode().learnsDirectly();
        var data = player.getCapability(
                com.gamergaming.taczweaponblueprints.init.ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        if (data == null) {
            return Optional.empty();
        }
        try {
            BlueprintResearchDataManager.ResearchPlanningAccess planningAccess =
                    directPathResearch
                            ? BlueprintResearchDataManager.INSTANCE.planningAccessFor(data)
                            : null;
            BlueprintResearchPolicy policy = planningAccess == null
                    ? resolvePolicy(player, targetId).orElse(null)
                    : planningAccess.policyResolver().apply(targetId);
            boolean progressionExempt = planningAccess == null
                    ? com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionAccess
                            .isProgressionExempt(targetId)
                    : planningAccess.progressionExempt().test(targetId);
            if (progressionExempt || policy == null
                    || !policy.visibility().allowsServerSelection()) {
                return Optional.empty();
            }
            var policyResolver = planningAccess == null
                    ? (java.util.function.Function<ResourceLocation, BlueprintResearchPolicy>)
                            ignored -> policy
                    : planningAccess.policyResolver();
            var exemptionResolver = planningAccess == null
                    ? (java.util.function.Predicate<ResourceLocation>)
                            com.gamergaming.taczweaponblueprints.progression
                                    .BlueprintProgressionAccess::isProgressionExempt
                    : planningAccess.progressionExempt();
            ResearchRouteEvaluationService.Evaluation evaluation =
                    ResearchRouteEvaluationService.evaluate(
                            new ResearchRouteEvaluationService.Request(
                                    targetId,
                                    data,
                                    policy,
                                    policyResolver,
                                    exemptionResolver,
                                    planningAccess == null
                                            ? ResearchPathAuthority.authored()
                                            : planningAccess.authority(),
                                    planningAccess == null
                                            ? ResearchRouteFingerprint.Context.EMPTY
                                            : planningAccess.fingerprintContext(),
                                    id -> BlueprintLearningService.targetFromCatalog(
                                            BlueprintDataManager.SERVER, id),
                                    playerInventory.items,
                                    player.isCreative(),
                                    directPathResearch,
                                    config.blueprintsEnabled()))
                            .orElse(null);
            return evaluation == null
                    ? Optional.empty()
                    : Optional.of(new PreparedRouteEvaluation(
                            evaluation, data, config.researchCostMode()));
        } catch (RuntimeException exception) {
            ResearchRouteFailureReporter.report("Bench route preparation", exception);
            return Optional.empty();
        }
    }

    private static MigrationAttempt migrateLegacyKnowledge(
            ServerPlayer player) {
        try {
            return player.getCapability(
                            com.gamergaming.taczweaponblueprints.init.ModCapabilities
                                    .PLAYER_RECIPE_DATA)
                    .map(data -> {
                        BlueprintLearningService.MigrationResult result =
                                BlueprintLearningService.migrateLegacyUnlocksDetailed(
                                        BlueprintDataManager.SERVER, data);
                        return new MigrationAttempt(result.changed(), false);
                    })
                    .orElseGet(() -> new MigrationAttempt(false, false));
        } catch (RuntimeException exception) {
            // A custom capability implementation could fail after applying
            // part of a repair. Reject the purchase and publish knowledge so
            // client and server cannot proceed from different ancestry state.
            ResearchRouteFailureReporter.report("legacy knowledge migration", exception);
            return new MigrationAttempt(false, true);
        }
    }

    private void synchronizeMigratedKnowledge(ServerPlayer player) {
        suppressAuthoritativePreviewRefresh = true;
        try {
            BlueprintResearchService.syncMigratedKnowledgeBestEffort(player);
        } finally {
            suppressAuthoritativePreviewRefresh = false;
        }
    }

    private static long serverTick(ServerPlayer player) {
        return player == null || player.getServer() == null
                ? -1L
                : player.getServer().getTickCount();
    }

    private void refreshRouteGuidanceAvailability() {
        routeGuidanceAvailable.set(ModConfigs.BLUEPRINT.progressionSnapshot()
                .treeResearchResultMode().learnsDirectly() ? 1 : 0);
    }

    private record PreparedPreview(
            ResearchSelectionPreview preview,
            boolean directPathResearch,
            Optional<ResearchPathUnlockPlanner.Plan> path) {
        private PreparedPreview {
            preview = preview == null ? ResearchSelectionPreview.EMPTY : preview;
            path = path == null ? Optional.empty() : path;
            if (!directPathResearch && path.isPresent()) {
                throw new IllegalArgumentException(
                        "physical-blueprint preview cannot contain a direct research path");
            }
        }

        private static PreparedPreview empty(boolean directPathResearch) {
            return new PreparedPreview(
                    ResearchSelectionPreview.EMPTY,
                    directPathResearch,
                    Optional.empty());
        }
    }

    private record PreparedRouteEvaluation(
            ResearchRouteEvaluationService.Evaluation evaluation,
            IPlayerRecipeData playerData,
            ResearchCostMode costMode) {
        private PreparedRouteEvaluation {
            if (evaluation == null || playerData == null || costMode == null) {
                throw new IllegalArgumentException("prepared route evaluation is invalid");
            }
        }
    }

    public record GuidanceResult(
            GuidanceResultCode code,
            Optional<ResearchGuidanceSnapshot> snapshot) {
        public GuidanceResult {
            snapshot = snapshot == null ? Optional.empty() : snapshot;
            if (code == null || (code == GuidanceResultCode.SUCCESS) != snapshot.isPresent()) {
                throw new IllegalArgumentException("research guidance result is invalid");
            }
        }

        private static GuidanceResult success(ResearchGuidanceSnapshot snapshot) {
            return new GuidanceResult(GuidanceResultCode.SUCCESS, Optional.of(snapshot));
        }

        private static GuidanceResult rejected() {
            return new GuidanceResult(GuidanceResultCode.REJECTED, Optional.empty());
        }

        private static GuidanceResult throttled() {
            return new GuidanceResult(GuidanceResultCode.THROTTLED, Optional.empty());
        }
    }

    public enum GuidanceResultCode {
        SUCCESS,
        REJECTED,
        THROTTLED
    }

    public record AffordabilityResult(
            AffordabilityResultCode code,
            Optional<ResearchAffordabilitySnapshot> snapshot) {
        public AffordabilityResult {
            snapshot = snapshot == null ? Optional.empty() : snapshot;
            if (code == null
                    || (code == AffordabilityResultCode.SUCCESS) != snapshot.isPresent()) {
                throw new IllegalArgumentException("research affordability result is invalid");
            }
        }

        private static AffordabilityResult success(ResearchAffordabilitySnapshot snapshot) {
            return new AffordabilityResult(
                    AffordabilityResultCode.SUCCESS, Optional.of(snapshot));
        }

        public static AffordabilityResult rejected() {
            return new AffordabilityResult(
                    AffordabilityResultCode.REJECTED, Optional.empty());
        }

        public static AffordabilityResult throttled() {
            return new AffordabilityResult(
                    AffordabilityResultCode.THROTTLED, Optional.empty());
        }

        public static AffordabilityResult queued() {
            return new AffordabilityResult(
                    AffordabilityResultCode.QUEUED, Optional.empty());
        }
    }

    public enum AffordabilityResultCode {
        SUCCESS,
        REJECTED,
        THROTTLED,
        QUEUED
    }

    private record PendingAffordabilityBatch(
            ServerPlayer player,
            int requestId,
            long publicationGeneration,
            List<ResourceLocation> targetIds,
            ResearchTreeGraph publicGraph,
            ArrayList<ResearchAffordabilitySnapshot.Entry> entries) {
        private PendingAffordabilityBatch {
            targetIds = targetIds == null ? List.of() : List.copyOf(targetIds);
            if (player == null || requestId < 1 || publicationGeneration == Long.MIN_VALUE
                    || targetIds.isEmpty()
                    || targetIds.size()
                            > ResearchAffordabilitySnapshot.MAX_TARGETS_PER_BATCH
                    || publicGraph == null || entries == null || !entries.isEmpty()) {
                throw new IllegalArgumentException(
                        "pending research affordability batch is invalid");
            }
        }
    }

    private record MigrationAttempt(boolean changed, boolean failed) {
    }

    private static Optional<BlueprintResearchPolicy> resolvePolicy(
            ServerPlayer player,
            ResourceLocation blueprintId) {
        try {
            var data = player.getCapability(
                    com.gamergaming.taczweaponblueprints.init.ModCapabilities.PLAYER_RECIPE_DATA)
                    .resolve().orElse(null);
            return data == null
                    ? Optional.empty()
                    : Optional.ofNullable(
                            BlueprintResearchDataManager.INSTANCE.policyFor(blueprintId, data));
        } catch (RuntimeException exception) {
            ResearchRouteFailureReporter.report("Bench policy resolution", exception);
            return Optional.empty();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.RESEARCH_BENCH.get());
    }

    @Override
    public void removed(Player player) {
        cancelAffordabilityBatch();
        super.removed(player);
    }

    private void cancelAffordabilityBatch() {
        if (pendingAffordabilityBatch != null) {
            ResearchPlanningAdmission.unregisterQueued(
                    pendingAffordabilityBatch.player().getUUID());
            pendingAffordabilityBatch = null;
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private static String researchMessage(BlueprintResearchService.Status status) {
        return "message.taczweaponblueprints.research."
                + status.name().toLowerCase(java.util.Locale.ROOT);
    }

    public enum ActionResultCode {
        ACCEPTED,
        REJECTED,
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
        STALE_PREVIEW,
        REQUEST_THROTTLED
    }

    public record ActionResult(
            ResearchBenchResearchAction action,
            Optional<ResourceLocation> blueprintId,
            ActionResultCode code) {
        public ActionResult {
            blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
            if (action == null || code == null
                    || blueprintId.filter(id -> id.toString().length()
                            > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH).isPresent()
                    || (action == ResearchBenchResearchAction.SELECT
                            && code != ActionResultCode.ACCEPTED
                            && code != ActionResultCode.REJECTED
                            && code != ActionResultCode.INVALID_INPUT
                            && code != ActionResultCode.REQUEST_THROTTLED)
                    || (action == ResearchBenchResearchAction.RESEARCH
                            && (code == ActionResultCode.ACCEPTED
                                    || code == ActionResultCode.REJECTED))) {
                throw new IllegalArgumentException("invalid Research Bench action result");
            }
        }

        public boolean successful() {
            return code == ActionResultCode.ACCEPTED || code == ActionResultCode.SUCCESS;
        }
    }
}
