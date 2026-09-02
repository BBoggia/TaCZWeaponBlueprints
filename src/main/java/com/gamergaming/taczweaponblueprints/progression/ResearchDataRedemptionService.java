package com.gamergaming.taczweaponblueprints.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.DispatchMode;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDataManager;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardResolver;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardSnapshot;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Explicit, server-authoritative conversion of configured inventory items into
 * Research Points. The client identifies only the action; this service chooses
 * and revalidates the live inventory stack and consumes it after an RP commit.
 */
public final class ResearchDataRedemptionService {
    private ResearchDataRedemptionService() {
    }

    public static Evaluation evaluate(ServerPlayer player) {
        ServerState state = serverState(player);
        if (state == null) {
            return Evaluation.failure(
                    player == null ? Status.INVALID_PLAYER : Status.PLAYER_DATA_UNAVAILABLE,
                    0,
                    0);
        }
        return findCandidate(
                inventory(player),
                state.data(),
                ResearchPointAwardDataManager.INSTANCE.snapshot(),
                state.config(),
                gameTime(player),
                false).evaluation();
    }

    public static Result redeem(ServerPlayer player, boolean bulk) {
        ServerState state = serverState(player);
        if (state == null) {
            return Result.failure(
                    player == null ? Status.INVALID_PLAYER : Status.PLAYER_DATA_UNAVAILABLE,
                    0);
        }

        int maximum = bulk
                ? PlayerProgressionLimits.MAX_RESEARCH_DATA_REDEMPTIONS_PER_ACTION
                : 1;
        Result result = redeem(
                player, inventory(player),
                state.data(),
                ResearchPointAwardDataManager.INSTANCE.snapshot(),
                state.config(),
                gameTime(player),
                maximum);
        publishSuccess(player, result);
        return result;
    }

    /** Evaluates only the physical stack supplied by a workstation slot. */
    public static Evaluation evaluateInput(ServerPlayer player, ItemStack input) {
        Optional<ResourceLocation> inputId = itemId(input);
        int inputCount = input == null ? 0 : input.getCount();
        ServerState state = serverState(player);
        if (state == null) {
            return new Evaluation(
                    player == null ? Status.INVALID_PLAYER : Status.PLAYER_DATA_UNAVAILABLE,
                    inputId,
                    Math.max(0, inputCount),
                    0,
                    0,
                    0);
        }
        if (inputId.isEmpty() || inputCount <= 0) {
            return Evaluation.failure(
                    Status.NO_MATCH,
                    state.data().getResearchPoints(),
                    state.config().pointCap());
        }
        return findCandidate(
                List.of(new DirectStackEntry(input, inputCount)),
                state.data(),
                ResearchPointAwardDataManager.INSTANCE.snapshot(),
                state.config(),
                gameTime(player),
                true).evaluation();
    }

    /**
     * Redeems only the supplied workstation stack. The expected identity and
     * count are checked before evaluation and again through the external commit.
     */
    public static Result redeemInput(
            ServerPlayer player,
            ItemStack input,
            ResourceLocation expectedInputId,
            int expectedInputCount,
            boolean bulk) {
        ServerState state = serverState(player);
        if (state == null) {
            return Result.failure(
                    player == null ? Status.INVALID_PLAYER : Status.PLAYER_DATA_UNAVAILABLE,
                    0);
        }
        Optional<ResourceLocation> actualId = itemId(input);
        if (expectedInputId == null
                || expectedInputCount < 1
                || expectedInputCount
                        > PlayerProgressionLimits.MAX_RESEARCH_DATA_REDEMPTIONS_PER_ACTION
                || actualId.isEmpty()
                || !actualId.filter(expectedInputId::equals).isPresent()
                || input == null
                || input.getCount() != expectedInputCount) {
            return Result.failure(Status.STALE_INVENTORY, state.data().getResearchPoints());
        }

        int maximum = bulk ? expectedInputCount : 1;
        Result result = redeem(
                player,
                List.of(new DirectStackEntry(input, expectedInputCount)),
                state.data(),
                ResearchPointAwardDataManager.INSTANCE.snapshot(),
                state.config(),
                gameTime(player),
                maximum);
        publishSuccess(player, result);
        return result;
    }

    static Evaluation evaluate(
            List<InventoryEntry> inventory,
            IPlayerRecipeData data,
            ResearchPointAwardSnapshot snapshot,
            ResearchPointAwardConfigSnapshot config,
            long gameTime) {
        return findCandidate(inventory, data, snapshot, config, gameTime, false).evaluation();
    }

    static Evaluation evaluateInput(
            List<InventoryEntry> inventory,
            IPlayerRecipeData data,
            ResearchPointAwardSnapshot snapshot,
            ResearchPointAwardConfigSnapshot config,
            long gameTime) {
        return findCandidate(inventory, data, snapshot, config, gameTime, true).evaluation();
    }

    static Result redeem(
            List<InventoryEntry> inventory,
            IPlayerRecipeData data,
            ResearchPointAwardSnapshot snapshot,
            ResearchPointAwardConfigSnapshot config,
            long gameTime,
            int maximum) {
        return redeem(null, inventory, data, snapshot, config, gameTime, maximum);
    }

    private static Result redeem(
            ServerPlayer player,
            List<InventoryEntry> inventory,
            IPlayerRecipeData data,
            ResearchPointAwardSnapshot snapshot,
            ResearchPointAwardConfigSnapshot config,
            long gameTime,
            int maximum) {
        if (maximum < 1
                || maximum > PlayerProgressionLimits.MAX_RESEARCH_DATA_REDEMPTIONS_PER_ACTION) {
            return Result.failure(Status.NO_ELIGIBLE_AWARD,
                    data == null ? 0 : data.getResearchPoints());
        }
        int consumed = 0;
        int points = 0;
        Status terminal = Status.NO_MATCH;
        for (int index = 0; index < maximum; index++) {
            Candidate candidate = findCandidate(
                    inventory, data, snapshot, config, gameTime, false);
            terminal = candidate.evaluation().status();
            if (!candidate.evaluation().redeemable() || candidate.entry().isEmpty()) {
                break;
            }

            InventoryEntry entry = candidate.entry().orElseThrow();
            // Reject an already-stale candidate before callbacks. The external
            // transaction repeats this check after cancellable pre-award events.
            if (!entry.matches(
                    candidate.evaluation().itemId().orElseThrow(),
                    candidate.context().targetTags())) {
                terminal = Status.STALE_INVENTORY;
                break;
            }
            ResearchPointAwardService.BatchResult award = player == null
                    ? ResearchPointAwardService.awardResolved(
                            data,
                            candidate.resolution(),
                            candidate.context(),
                            config,
                            gameTime,
                            externalTransaction(entry, candidate))
                    : ResearchPointAwardService.awardResolved(
                            player,
                            data,
                            candidate.resolution(),
                            candidate.context(),
                            config,
                            gameTime,
                            externalTransaction(entry, candidate));
            if (!award.pointsChanged()) {
                terminal = award.awards().stream().anyMatch(value ->
                        value.status() == ResearchPointAwardService.Status.EXTERNAL_STATE_CHANGED)
                                ? Status.STALE_INVENTORY
                                : Status.NO_ELIGIBLE_AWARD;
                break;
            }
            consumed++;
            points += award.awardedPoints();
            terminal = Status.SUCCESS;
        }
        return points > 0
                ? new Result(Status.SUCCESS, consumed, points, data.getResearchPoints())
                : Result.failure(terminal, data == null ? 0 : data.getResearchPoints());
    }

    private static Candidate findCandidate(
            List<InventoryEntry> inventory,
            IPlayerRecipeData data,
            ResearchPointAwardSnapshot snapshot,
            ResearchPointAwardConfigSnapshot config,
            long gameTime,
            boolean preserveFailureIdentity) {
        int balance = data == null ? 0 : data.getResearchPoints();
        int cap = config == null ? 0 : config.pointCap();
        if (inventory == null || data == null || snapshot == null || config == null || gameTime < 0L) {
            return Candidate.failure(Status.PLAYER_DATA_UNAVAILABLE, balance, cap);
        }
        boolean matched = false;
        boolean capBlocked = false;
        Candidate firstMatched = null;
        for (InventoryEntry entry : inventory) {
            if (entry == null || entry.count() <= 0 || entry.itemId() == null) {
                continue;
            }
            ResearchPointAwardContext context = context(entry, config);
            ResearchPointAwardResolver.Resolution resolution =
                    ResearchPointAwardResolver.resolve(snapshot, context);
            if (!resolution.successful() || resolution.awards().isEmpty()) {
                continue;
            }
            matched = true;
            ResearchPointAwardService.BatchEvaluation evaluation =
                    ResearchPointAwardService.evaluateResolved(
                            data, resolution, context, config, gameTime);
            int available = inventory.stream()
                    .filter(candidate -> candidate != null
                            && candidate.count() > 0
                            && entry.itemId().equals(candidate.itemId()))
                    .mapToInt(InventoryEntry::count)
                    .sum();
            if (preserveFailureIdentity && firstMatched == null) {
                firstMatched = new Candidate(
                        new Evaluation(
                                Status.NO_ELIGIBLE_AWARD,
                                Optional.of(entry.itemId()),
                                available,
                                0,
                                balance,
                                cap),
                        Optional.of(entry),
                        context,
                        resolution);
            }
            if (evaluation.eligible()) {
                return new Candidate(
                        new Evaluation(
                                Status.SUCCESS,
                                Optional.of(entry.itemId()),
                                available,
                                evaluation.awardablePoints(),
                                balance,
                                cap),
                        Optional.of(entry),
                        context,
                        resolution);
            }
            capBlocked |= evaluation.awards().stream().anyMatch(value ->
                    value.status() == ResearchPointAwardService.Status.POINT_CAP_REACHED);
        }
        Status status = !matched
                ? Status.NO_MATCH
                : capBlocked ? Status.POINT_CAP_REACHED : Status.NO_ELIGIBLE_AWARD;
        if (firstMatched != null) {
            Evaluation first = firstMatched.evaluation();
            return new Candidate(
                    new Evaluation(
                            status,
                            first.itemId(),
                            first.availableItems(),
                            first.pointValue(),
                            first.pointBalance(),
                            first.pointCap()),
                    firstMatched.entry(),
                    firstMatched.context(),
                    firstMatched.resolution());
        }
        return Candidate.failure(status, balance, cap);
    }

    private static ResearchPointAwardContext context(
            InventoryEntry entry,
            ResearchPointAwardConfigSnapshot config) {
        return new ResearchPointAwardContext(
                ResearchPointAwardTrigger.Type.INVENTORY_TURN_IN,
                config.activeProfileId(),
                DispatchMode.LIVE,
                Optional.of(entry.itemId()),
                entry.tags(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                0,
                Optional.empty());
    }

    private static List<InventoryEntry> inventory(ServerPlayer player) {
        List<InventoryEntry> entries = new ArrayList<>(player.getInventory().items.size());
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            entries.add(new StackEntry(player.getInventory().items, slot));
        }
        return entries;
    }

    private static ResearchPointAwardService.ExternalTransaction externalTransaction(
            InventoryEntry entry,
            Candidate candidate) {
        ResourceLocation expectedId = candidate.evaluation().itemId().orElseThrow();
        Set<ResourceLocation> expectedTags = candidate.context().targetTags();
        return new ResearchPointAwardService.ExternalTransaction() {
            @Override
            public boolean valid() {
                return entry.matches(expectedId, expectedTags);
            }

            @Override
            public void commit() {
                entry.consumeOne();
            }
        };
    }

    private static ServerState serverState(ServerPlayer player) {
        if (player == null || !player.isAlive()) {
            return null;
        }
        IPlayerRecipeData data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        ResearchPointAwardConfigSnapshot config = ModConfigs.BLUEPRINT.awardSnapshot();
        return data == null || config == null ? null : new ServerState(data, config);
    }

    private static long gameTime(ServerPlayer player) {
        return Math.max(0L, player.serverLevel().getGameTime());
    }

    private static Optional<ResourceLocation> itemId(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? Optional.empty()
                : Optional.ofNullable(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    private static void publishSuccess(ServerPlayer player, Result result) {
        if (player == null || result == null || !result.successful()) {
            return;
        }
        NetworkHandler.syncPlayerPointBalance(player);
        NetworkHandler.sendResearchPointFeedback(
                player,
                new ResearchPointPresentationService.Feedback(
                        result.awardedPoints(),
                        result.consumedItems(),
                        false,
                        List.of()));
        ResearchPointPresentationService.syncHelp(player);
    }

    interface InventoryEntry {
        ResourceLocation itemId();

        Set<ResourceLocation> tags();

        int count();

        void consumeOne();

        default boolean matches(
                ResourceLocation expectedId,
                Set<ResourceLocation> expectedTags) {
            return count() > 0
                    && expectedId != null
                    && expectedId.equals(itemId())
                    && expectedTags != null
                    && expectedTags.equals(tags());
        }
    }

    private static final class StackEntry implements InventoryEntry {
        private final List<ItemStack> inventory;
        private final int slot;

        private StackEntry(List<ItemStack> inventory, int slot) {
            this.inventory = inventory;
            this.slot = slot;
        }

        private ItemStack stack() {
            return inventory.get(slot);
        }

        @Override
        public ResourceLocation itemId() {
            return ForgeRegistries.ITEMS.getKey(stack().getItem());
        }

        @Override
        public Set<ResourceLocation> tags() {
            return stack().getTags().map(TagKey<Item>::location)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        @Override
        public int count() {
            return stack().getCount();
        }

        @Override
        public void consumeOne() {
            stack().shrink(1);
        }
    }

    private static final class DirectStackEntry implements InventoryEntry {
        private final ItemStack stack;
        private int expectedCount;

        private DirectStackEntry(ItemStack stack, int expectedCount) {
            this.stack = stack;
            this.expectedCount = expectedCount;
        }

        @Override
        public ResourceLocation itemId() {
            return ForgeRegistries.ITEMS.getKey(stack.getItem());
        }

        @Override
        public Set<ResourceLocation> tags() {
            return stack.getTags().map(TagKey<Item>::location)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        @Override
        public int count() {
            return stack.getCount();
        }

        @Override
        public void consumeOne() {
            stack.shrink(1);
            expectedCount--;
        }

        @Override
        public boolean matches(
                ResourceLocation expectedId,
                Set<ResourceLocation> expectedTags) {
            return stack.getCount() == expectedCount
                    && InventoryEntry.super.matches(expectedId, expectedTags);
        }
    }

    public enum Status {
        SUCCESS,
        INVALID_PLAYER,
        PLAYER_DATA_UNAVAILABLE,
        NO_MATCH,
        NO_ELIGIBLE_AWARD,
        POINT_CAP_REACHED,
        STALE_INVENTORY
    }

    public record Evaluation(
            Status status,
            Optional<ResourceLocation> itemId,
            int availableItems,
            int pointValue,
            int pointBalance,
            int pointCap) {
        public Evaluation {
            itemId = itemId == null ? Optional.empty() : itemId;
            if (status == null || availableItems < 0 || pointValue < 0
                    || pointValue > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || pointBalance < 0 || pointBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || pointCap < 0 || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
                throw new IllegalArgumentException("invalid Research Data evaluation");
            }
            if (status == Status.SUCCESS
                    && (itemId.isEmpty() || availableItems <= 0 || pointValue <= 0)) {
                throw new IllegalArgumentException("redeemable Research Data evaluation is incomplete");
            }
        }

        public boolean redeemable() {
            return status == Status.SUCCESS;
        }

        /**
         * True only when the active award snapshot actually classified this
         * physical input. Preserved identity during capability/configuration
         * outages is deliberately not a Research Data match.
         */
        public boolean matchedInput() {
            boolean matchedStatus = switch (status) {
                case SUCCESS, NO_ELIGIBLE_AWARD, POINT_CAP_REACHED -> true;
                case INVALID_PLAYER, PLAYER_DATA_UNAVAILABLE, NO_MATCH, STALE_INVENTORY -> false;
            };
            return matchedStatus && itemId.isPresent() && availableItems > 0;
        }

        private static Evaluation failure(Status status, int balance, int cap) {
            return new Evaluation(status, Optional.empty(), 0, 0,
                    Math.max(0, balance), Math.max(0, cap));
        }
    }

    public record Result(Status status, int consumedItems, int awardedPoints, int newBalance) {
        public Result {
            if (status == null || consumedItems < 0 || awardedPoints < 0 || newBalance < 0
                    || (status == Status.SUCCESS && (consumedItems <= 0 || awardedPoints <= 0))) {
                throw new IllegalArgumentException("invalid Research Data redemption result");
            }
        }

        public boolean successful() {
            return status == Status.SUCCESS;
        }

        private static Result failure(Status status, int balance) {
            return new Result(status, 0, 0, Math.max(0, balance));
        }
    }

    private record ServerState(IPlayerRecipeData data, ResearchPointAwardConfigSnapshot config) {
    }

    private record Candidate(
            Evaluation evaluation,
            Optional<InventoryEntry> entry,
            ResearchPointAwardContext context,
            ResearchPointAwardResolver.Resolution resolution) {
        private static Candidate failure(Status status, int balance, int cap) {
            return new Candidate(
                    Evaluation.failure(status, balance, cap),
                    Optional.empty(),
                    null,
                    null);
        }
    }
}
