package com.gamergaming.taczweaponblueprints.progression;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.BlueprintLearningMutation;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.BlueprintAccessPolicy.LearningDecision;
import com.gamergaming.taczweaponblueprints.progression.BlueprintAccessPolicy.LearningFacts;
import com.gamergaming.taczweaponblueprints.progression.BlueprintAccessPolicy.LearningStatus;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Shared server authority for durable blueprint knowledge mutations. */
public final class BlueprintLearningService {
    public static final PhysicalBlueprintLearningMode DEFAULT_PHYSICAL_BLUEPRINT_MODE =
            PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES;

    private BlueprintLearningService() {
    }

    /**
     * Learns one physical blueprint using current authoritative server state.
     * The supplied physical item is consumed immediately before the atomic
     * capability commit and restored if that commit rejects. Awards and
     * networking are post-commit publication and cannot prevent consumption.
     */
    public static Result learnPhysicalBlueprint(
            ServerPlayer player,
            ResourceLocation blueprintId,
            ItemStack physicalBlueprint) {
        if (player == null || !player.isAlive() || !validId(blueprintId)
                || !BlueprintItem.getBlueprintId(physicalBlueprint)
                        .filter(blueprintId::equals).isPresent()) {
            return Result.failure(
                    Status.INVALID_INPUT,
                    BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                    Optional.ofNullable(blueprintId));
        }
        IPlayerRecipeData playerData = player
                .getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve()
                .orElse(null);
        if (playerData == null) {
            return Result.failure(
                    Status.PLAYER_DATA_UNAVAILABLE,
                    BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                    Optional.of(blueprintId));
        }

        MigrationResult migration = migrateLegacyUnlocksDetailed(
                BlueprintDataManager.SERVER,
                playerData);
        var config = ModConfigs.BLUEPRINT.progressionSnapshot();
        Request request = new Request(
                BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                blueprintId,
                config.blueprintsEnabled(),
                DEFAULT_PHYSICAL_BLUEPRINT_MODE,
                BlueprintProgressionAccess.isProgressionExempt(blueprintId));
        Preparation preparation = prepare(
                request,
                playerData,
                id -> targetFromCatalog(BlueprintDataManager.SERVER, id),
                id -> BlueprintResearchDataManager.INSTANCE.policyFor(id, playerData));
        if (!preparation.ready()) {
            Result failure = preparation.failure().orElseThrow();
            if (migration.changed()) {
                syncKnowledgeBestEffort(player, "physical blueprint migration");
            }
            return failure;
        }

        boolean consumed = !player.getAbilities().instabuild;
        if (consumed) {
            physicalBlueprint.shrink(1);
        }
        Result result = commitPrepared(preparation.prepared().orElseThrow(), playerData);
        if (!result.successful() && consumed) {
            physicalBlueprint.grow(1);
        }

        if (result.successful() && result.liveAwardsEligible()) {
            ResearchPointAwardDispatcher.blueprintTransitions(
                    player,
                    playerData,
                    blueprintId,
                    result.discoveredChanged(),
                    result.learnedChanged());
        }
        if (result.successful() || migration.changed()) {
            syncKnowledgeBestEffort(player, "physical blueprint learning");
        }
        return result;
    }

    private static void syncKnowledgeBestEffort(ServerPlayer player, String operation) {
        try {
            NetworkHandler.syncPlayerRecipeData(player);
        } catch (RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Committed {} for {}, but immediate progression sync failed; scheduling a retry",
                    operation,
                    player == null ? "unknown player" : player.getGameProfile().getName(),
                    exception);
            BlueprintProgressionSyncScheduler.markKnowledgeDirty(player);
        }
    }

    /**
     * Orchestrates resolved policy and the capability's atomic mutation. It
     * performs no item consumption, feedback, award dispatch, or networking.
     */
    public static Result learn(
            Request request,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, LearningTarget> targetResolver,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver) {
        Preparation preparation = prepare(
                request, playerData, targetResolver, policyResolver);
        if (!preparation.ready()) {
            return preparation.failure().orElseThrow();
        }
        return commitPrepared(
                preparation.prepared().orElseThrow(), playerData);
    }

    /**
     * Resolves every rejecting learning check without changing progression.
     * Cost-owning callers such as tree research use the returned opaque plan
     * before spending RP or consuming inventory materials.
     */
    public static Preparation prepare(
            Request request,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, LearningTarget> targetResolver,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver) {
        if (request == null || !validId(request.blueprintId())) {
            return Preparation.rejected(Result.failure(
                    Status.INVALID_INPUT,
                    request == null
                            ? BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT
                            : request.origin(),
                    request == null
                            ? Optional.empty()
                            : Optional.ofNullable(request.blueprintId())));
        }
        Optional<ResourceLocation> id = Optional.of(request.blueprintId());
        if (playerData == null) {
            return Preparation.rejected(Result.failure(
                    Status.PLAYER_DATA_UNAVAILABLE,
                    request.origin(),
                    id));
        }
        if (targetResolver == null || policyResolver == null) {
            return Preparation.rejected(Result.failure(
                    Status.POLICY_UNAVAILABLE, request.origin(), id));
        }

        LearningTarget target;
        BlueprintResearchPolicy policy;
        try {
            target = targetResolver.apply(request.blueprintId());
            policy = policyResolver.apply(request.blueprintId());
        } catch (RuntimeException exception) {
            return Preparation.rejected(Result.failure(
                    Status.POLICY_UNAVAILABLE, request.origin(), id));
        }
        if (target == null) {
            return Preparation.rejected(Result.failure(
                    Status.CONTENT_UNAVAILABLE, request.origin(), id));
        }
        if (!request.blueprintId().equals(target.blueprintId())
                || !validId(target.blueprintId())
                || !validId(target.legacyRecipeId())) {
            return Preparation.rejected(Result.failure(
                    Status.INVALID_IDENTITY, request.origin(), id));
        }
        if (policy == null) {
            return Preparation.rejected(Result.failure(
                    Status.POLICY_UNAVAILABLE, request.origin(), id));
        }
        if (!request.blueprintId().equals(policy.blueprintId())) {
            return Preparation.rejected(Result.failure(
                    Status.POLICY_MISMATCH, request.origin(), id));
        }
        boolean learned = playerData.hasBlueprint(request.blueprintId().toString());
        boolean discovered = playerData.hasDiscoveredBlueprint(
                request.blueprintId().toString());
        if (!policy.playerDataAvailable()
                || policy.learned() != learned
                || policy.discovered() != discovered) {
            return Preparation.rejected(Result.failure(
                    Status.STALE_POLICY, request.origin(), id));
        }

        BlueprintLearningMutation.Result preflight;
        try {
            preflight = playerData.applyBlueprintLearning(
                    BlueprintLearningMutation.Request.preflight(
                            request.blueprintId().toString(),
                            target.legacyRecipeId().toString()));
        } catch (RuntimeException exception) {
            return Preparation.rejected(Result.failure(
                    Status.TRANSACTION_FAILED, request.origin(), id));
        }
        if (preflight.status() == BlueprintLearningMutation.Status.INVALID_IDENTITY) {
            return Preparation.rejected(Result.failure(
                    Status.INVALID_IDENTITY, request.origin(), id));
        }

        LearningDecision access = BlueprintAccessPolicy.evaluateLearning(
                new LearningFacts(
                        request.origin(),
                        request.physicalBlueprintMode(),
                        policy.available(),
                        policy.playerDataAvailable(),
                        request.blueprintsEnabled(),
                        policy.blocked(),
                        request.progressionExempt(),
                        learned,
                        preflight.status()
                                != BlueprintLearningMutation.Status.CAPACITY_REACHED,
                        policy.prerequisitesSatisfied()));
        if (!access.allowed()) {
            return Preparation.rejected(Result.failure(
                    map(access.status()), request.origin(), id));
        }
        if (!preflight.ready() || !preflight.learnedChanged()) {
            return Preparation.rejected(Result.failure(
                    Status.TRANSACTION_FAILED, request.origin(), id));
        }

        return Preparation.ready(new PreparedLearning(
                request, target, access, playerData));
    }

    /**
     * Commits a plan created by {@link #prepare}. The plan cannot be created
     * outside this service, keeping canonical identity and policy resolution
     * inside the shared learning authority.
     */
    public static Result commitPrepared(
            PreparedLearning prepared,
            IPlayerRecipeData playerData) {
        if (prepared == null || playerData == null) {
            return Result.failure(
                    prepared == null
                            ? Status.INVALID_INPUT
                            : Status.PLAYER_DATA_UNAVAILABLE,
                    prepared == null
                            ? BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT
                            : prepared.request.origin(),
                    prepared == null
                            ? Optional.empty()
                            : Optional.of(prepared.request.blueprintId()));
        }
        if (prepared.owner != playerData) {
            return Result.failure(
                    Status.STALE_POLICY,
                    prepared.request.origin(),
                    Optional.of(prepared.request.blueprintId()));
        }
        Request request = prepared.request;
        LearningTarget target = prepared.target;
        Optional<ResourceLocation> id = Optional.of(request.blueprintId());

        BlueprintLearningMutation.Result committed;
        try {
            committed = playerData.applyBlueprintLearning(
                    BlueprintLearningMutation.Request.commit(
                            request.blueprintId().toString(),
                            target.legacyRecipeId().toString()));
        } catch (RuntimeException exception) {
            return Result.failure(Status.TRANSACTION_FAILED, request.origin(), id);
        }
        if (committed.status() == BlueprintLearningMutation.Status.ALREADY_LEARNED) {
            return Result.failure(Status.ALREADY_LEARNED, request.origin(), id);
        }
        if (committed.status() == BlueprintLearningMutation.Status.CAPACITY_REACHED) {
            return Result.failure(
                    Status.PROGRESSION_CAPACITY_EXHAUSTED,
                    request.origin(),
                    id);
        }
        if (committed.status() == BlueprintLearningMutation.Status.INVALID_IDENTITY) {
            return Result.failure(Status.INVALID_IDENTITY, request.origin(), id);
        }
        if (!committed.committed() || !committed.learnedChanged()) {
            return Result.failure(Status.TRANSACTION_FAILED, request.origin(), id);
        }
        Result result = Result.success(
                request,
                prepared.access,
                committed.discoveredChanged(),
                committed.legacyRecipeChanged());
        RecentBlueprintUnlockHistory.record(
                playerData,
                request.origin(),
                request.blueprintId(),
                java.util.List.of(request.blueprintId()));
        return result;
    }

    /**
     * Migrates legacy recipe knowledge and repairs auxiliary invariants through
     * the same atomic capability operation. Migration never dispatches awards.
     */
    public static int migrateLegacyUnlocks(
            BlueprintDataManager catalog,
            IPlayerRecipeData playerData) {
        return migrateLegacyUnlocksDetailed(catalog, playerData)
                .learnedBlueprints();
    }

    public static MigrationResult migrateLegacyUnlocksDetailed(
            BlueprintDataManager catalog,
            IPlayerRecipeData playerData) {
        if (catalog == null || playerData == null) {
            return MigrationResult.EMPTY;
        }
        int migrated = 0;
        int repaired = 0;
        for (String learnedRecipe : Set.copyOf(playerData.getLearnedRecipes())) {
            ResourceLocation recipeId = ResourceLocation.tryParse(learnedRecipe);
            ResourceLocation blueprintId = catalog.getBlueprintIdForRecipe(recipeId);
            BlueprintData data = blueprintId == null
                    ? null
                    : catalog.getBlueprintData(blueprintId.toString());
            if (data == null || data.getRecipeId() == null) {
                continue;
            }
            BlueprintLearningMutation.Result preflight = playerData.applyBlueprintLearning(
                    BlueprintLearningMutation.Request.preflight(
                            blueprintId.toString(),
                            data.getRecipeId().toString()));
            LearningDecision access = BlueprintAccessPolicy.evaluateLearning(
                    new LearningFacts(
                            BlueprintUnlockOrigin.MIGRATION,
                            PhysicalBlueprintLearningMode.DISABLED,
                            true,
                            true,
                            false,
                            true,
                            true,
                            playerData.hasBlueprint(blueprintId.toString()),
                            preflight.status()
                                    != BlueprintLearningMutation.Status.CAPACITY_REACHED,
                            false));
            if (!access.allowed()) {
                continue;
            }
            BlueprintLearningMutation.Result result = playerData.applyBlueprintLearning(
                    BlueprintLearningMutation.Request.commit(
                            blueprintId.toString(),
                            data.getRecipeId().toString()));
            if (result.committed() && result.learnedChanged()) {
                migrated++;
            }
        }

        // Existing blueprint entries may predate discovery or legacy-recipe
        // invariants. Repair those without treating them as new learning.
        for (String learnedBlueprint : Set.copyOf(playerData.getLearnedBlueprints())) {
            BlueprintData data = catalog.getBlueprintData(learnedBlueprint);
            if (data != null && data.getRecipeId() != null) {
                BlueprintLearningMutation.Result repair =
                        playerData.applyBlueprintLearning(
                                BlueprintLearningMutation.Request.commit(
                                        learnedBlueprint,
                                        data.getRecipeId().toString()));
                if (repair.committed()) {
                    repaired++;
                }
            }
        }
        return new MigrationResult(migrated, repaired);
    }

    public static LearningTarget targetFromCatalog(
            BlueprintDataManager catalog,
            ResourceLocation blueprintId) {
        BlueprintData data = catalog == null || blueprintId == null
                ? null
                : catalog.getBlueprintData(blueprintId.toString());
        if (data == null || data.getRecipeId() == null) {
            return null;
        }
        return new LearningTarget(blueprintId, data.getRecipeId());
    }

    static LearningTarget targetFromCatalogMap(
            java.util.Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation blueprintId) {
        BlueprintData data = catalog == null || blueprintId == null
                ? null
                : catalog.get(blueprintId);
        return data == null || data.getRecipeId() == null
                ? null
                : new LearningTarget(blueprintId, data.getRecipeId());
    }

    private static Status map(LearningStatus status) {
        return switch (status) {
            case ALLOWED -> throw new IllegalArgumentException(
                    "allowed access cannot map to a failure");
            case CONTENT_UNAVAILABLE -> Status.CONTENT_UNAVAILABLE;
            case PLAYER_DATA_UNAVAILABLE -> Status.PLAYER_DATA_UNAVAILABLE;
            case BLUEPRINTS_DISABLED -> Status.BLUEPRINTS_DISABLED;
            case BLOCKED -> Status.BLOCKED;
            case PROGRESSION_EXEMPT -> Status.PROGRESSION_EXEMPT;
            case ALREADY_LEARNED -> Status.ALREADY_LEARNED;
            case PHYSICAL_BLUEPRINT_LEARNING_DISABLED ->
                    Status.PHYSICAL_BLUEPRINT_LEARNING_DISABLED;
            case PREREQUISITES_UNSATISFIED -> Status.PREREQUISITES_REQUIRED;
            case PROGRESSION_CAPACITY_EXHAUSTED ->
                    Status.PROGRESSION_CAPACITY_EXHAUSTED;
        };
    }

    private static boolean validId(ResourceLocation id) {
        return id != null
                && id.toString().length()
                        <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;
    }

    public record Request(
            BlueprintUnlockOrigin origin,
            ResourceLocation blueprintId,
            boolean blueprintsEnabled,
            PhysicalBlueprintLearningMode physicalBlueprintMode,
            boolean progressionExempt) {
        public Request {
            if (origin == null || physicalBlueprintMode == null) {
                throw new IllegalArgumentException(
                        "blueprint learning request contains null required state");
            }
        }
    }

    public record LearningTarget(
            ResourceLocation blueprintId,
            ResourceLocation legacyRecipeId) {
    }

    /** Opaque, one-use-style preflight result; only this service constructs it. */
    public static final class PreparedLearning {
        private final Request request;
        private final LearningTarget target;
        private final LearningDecision access;
        private final IPlayerRecipeData owner;

        private PreparedLearning(
                Request request,
                LearningTarget target,
                LearningDecision access,
                IPlayerRecipeData owner) {
            this.request = request;
            this.target = target;
            this.access = access;
            this.owner = owner;
        }

        public ResourceLocation blueprintId() {
            return request.blueprintId();
        }

        public BlueprintUnlockOrigin origin() {
            return request.origin();
        }
    }

    public record Preparation(
            Optional<PreparedLearning> prepared,
            Optional<Result> failure) {
        public Preparation {
            prepared = prepared == null ? Optional.empty() : prepared;
            failure = failure == null ? Optional.empty() : failure;
            if (prepared.isPresent() == failure.isPresent()
                    || failure.filter(Result::successful).isPresent()) {
                throw new IllegalArgumentException(
                        "learning preparation must contain exactly one outcome");
            }
        }

        public boolean ready() {
            return prepared.isPresent();
        }

        private static Preparation ready(PreparedLearning prepared) {
            return new Preparation(Optional.of(prepared), Optional.empty());
        }

        private static Preparation rejected(Result failure) {
            return new Preparation(Optional.empty(), Optional.of(failure));
        }
    }

    public enum Status {
        SUCCESS,
        INVALID_INPUT,
        PLAYER_DATA_UNAVAILABLE,
        CONTENT_UNAVAILABLE,
        INVALID_IDENTITY,
        POLICY_UNAVAILABLE,
        POLICY_MISMATCH,
        STALE_POLICY,
        BLUEPRINTS_DISABLED,
        BLOCKED,
        PROGRESSION_EXEMPT,
        ALREADY_LEARNED,
        PHYSICAL_BLUEPRINT_LEARNING_DISABLED,
        PREREQUISITES_REQUIRED,
        PROGRESSION_CAPACITY_EXHAUSTED,
        TRANSACTION_FAILED
    }

    public record MigrationResult(int learnedBlueprints, int repairedEntries) {
        private static final MigrationResult EMPTY = new MigrationResult(0, 0);

        public MigrationResult {
            if (learnedBlueprints < 0 || repairedEntries < 0) {
                throw new IllegalArgumentException(
                        "invalid blueprint migration result");
            }
        }

        public boolean changed() {
            return learnedBlueprints > 0 || repairedEntries > 0;
        }
    }

    public record Result(
            Status status,
            BlueprintUnlockOrigin origin,
            Optional<ResourceLocation> blueprintId,
            boolean learnedChanged,
            boolean discoveredChanged,
            boolean legacyRecipeChanged,
            boolean prerequisitesBypassed,
            boolean liveAwardsEligible) {
        public Result {
            if (status == null || origin == null) {
                throw new IllegalArgumentException(
                        "blueprint learning result contains null required state");
            }
            blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
            boolean changed = learnedChanged
                    || discoveredChanged
                    || legacyRecipeChanged;
            if (status == Status.SUCCESS) {
                if (blueprintId.isEmpty()
                        || !learnedChanged
                        || liveAwardsEligible != origin.liveAwardsEligible()) {
                    throw new IllegalArgumentException(
                            "successful blueprint learning result is inconsistent");
                }
            } else if (changed || prerequisitesBypassed || liveAwardsEligible) {
                throw new IllegalArgumentException(
                        "failed blueprint learning result cannot contain transitions");
            }
        }

        public boolean successful() {
            return status == Status.SUCCESS;
        }

        private static Result success(
                Request request,
                LearningDecision access,
                boolean discoveredChanged,
                boolean legacyRecipeChanged) {
            return new Result(
                    Status.SUCCESS,
                    request.origin(),
                    Optional.of(request.blueprintId()),
                    true,
                    discoveredChanged,
                    legacyRecipeChanged,
                    access.prerequisitesBypassed(),
                    access.liveAwardsEligible());
        }

        private static Result failure(
                Status status,
                BlueprintUnlockOrigin origin,
                Optional<ResourceLocation> blueprintId) {
            return new Result(
                    status,
                    origin,
                    blueprintId,
                    false,
                    false,
                    false,
                    false,
                    false);
        }
    }
}
