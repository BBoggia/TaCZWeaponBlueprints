package com.gamergaming.taczweaponblueprints.progression;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.ClaimKey;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardBlueprintFacts;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.DispatchMode;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDataManager;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardMilestoneResolver;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardResolver;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardResolver.ResolvedAward;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardSnapshot;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.MilestoneState;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.Type;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bounded login/reload reconciliation for definitions that explicitly opt into
 * retroactive finite claims. Both planning and commits share one per-tick work
 * budget; point-cap failures are parked until the balance decreases.
 */
public final class ResearchPointAwardReconciliationScheduler {
    public static final int MAX_EVALUATIONS_PER_PLAYER_TICK = 32;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private ResearchPointAwardReconciliationScheduler() {
    }

    /** Captures a cheap immutable session seed; expensive resolution happens per tick. */
    public static void schedule(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            Session session = createSession(player);
            if (session == null) {
                SESSIONS.remove(player.getUUID());
            } else {
                SESSIONS.put(player.getUUID(), session);
            }
        } catch (RuntimeException exception) {
            SESSIONS.remove(player.getUUID());
            TaCZWeaponBlueprints.LOGGER.error(
                    "Failed closed while preparing retroactive RP awards for {}",
                    player.getGameProfile().getName(),
                    exception);
        }
    }

    public static void process(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            processSession(player);
        } catch (RuntimeException exception) {
            SESSIONS.remove(player.getUUID());
            TaCZWeaponBlueprints.LOGGER.error(
                    "Failed closed while processing retroactive RP awards for {}",
                    player.getGameProfile().getName(),
                    exception);
        }
    }

    private static void processSession(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        IPlayerRecipeData data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        if (data == null) {
            SESSIONS.remove(player.getUUID());
            return;
        }

        PlayerState state = PlayerState.capture(data);
        if (session.stale() || session.playerStateInvalidated(state)) {
            schedule(player);
            session = SESSIONS.get(player.getUUID());
            if (session == null) {
                return;
            }
            state = session.playerState();
        }

        session.wakeRetryable(data.getResearchPoints());
        int operations = 0;
        boolean pointsChanged = false;
        boolean helpChanged = false;
        List<ResearchPointPresentationService.Feedback> feedback = new ArrayList<>();
        long gameTime = Math.max(0L, player.server.overworld().getGameTime());
        while (operations < MAX_EVALUATIONS_PER_PLAYER_TICK) {
            WorkItem work = session.pollReady();
            if (work != null) {
                if (!sourceStillValid(player, work, state, session.facts())) {
                    session.complete(work);
                    operations++;
                    continue;
                }
                ResearchPointAwardService.AwardResult result = ResearchPointAwardService.awardOne(
                        player, data, work.award(), work.context(), session.config(), gameTime);
                pointsChanged |= result.status().awardedPoints();
                helpChanged |= result.committed();
                feedback.add(ResearchPointPresentationService.feedback(
                        player, work.award(), result, work.context()));
                session.recordResult(work, result, data.getResearchPoints());
                operations++;
                continue;
            }

            if (!session.canPlan(data) || !session.planNext(player, data, state)) {
                break;
            }
            operations++;
        }

        if (session.finished(data)) {
            SESSIONS.remove(player.getUUID(), session);
        }
        if (pointsChanged) {
            NetworkHandler.syncPlayerPointBalance(player);
        }
        if (helpChanged) {
            ResearchPointPresentationService.syncHelp(player);
        }
        ResearchPointPresentationService.Feedback combined =
                ResearchPointPresentationService.combine(feedback);
        if (combined.present()) {
            NetworkHandler.sendResearchPointFeedback(player, combined);
        }
    }

    public static void clear(ServerPlayer player) {
        if (player != null) {
            SESSIONS.remove(player.getUUID());
        }
    }

    public static void clearAll() {
        SESSIONS.clear();
    }

    static int pendingWork(UUID playerId) {
        Session session = playerId == null ? null : SESSIONS.get(playerId);
        return session == null ? 0 : session.pendingWork();
    }

    private static Session createSession(ServerPlayer player) {
        ResearchPointAwardConfigSnapshot config = ModConfigs.BLUEPRINT.awardSnapshot();
        ResearchPointAwardDataManager.Publication awards = ResearchPointAwardDataManager.INSTANCE.publication();
        if (config == null || !config.awardsEnabled() || !hasRetroactiveDefinitions(awards.snapshot())) {
            return null;
        }
        IPlayerRecipeData data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        if (data == null) {
            return null;
        }

        ResearchPointAwardBlueprintFacts.Publication facts =
                ResearchPointAwardBlueprintFacts.currentPublication();
        return new Session(
                awards.revision(),
                facts.catalogRevision(),
                facts.researchRevision(),
                config,
                awards.snapshot(),
                facts.facts(),
                PlayerState.capture(data),
                player.server.getAdvancements().getAllAdvancements().iterator());
    }

    private static boolean hasRetroactiveDefinitions(ResearchPointAwardSnapshot snapshot) {
        return snapshot != null && snapshot.bindingsByTrigger().values().stream()
                .flatMap(List::stream)
                .anyMatch(binding -> binding.definition().trigger().retroactive());
    }

    private static boolean sourceStillValid(
            ServerPlayer player,
            WorkItem work,
            PlayerState state,
            Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts) {
        ResourceLocation target = work.context().targetId().orElse(null);
        return switch (work.context().triggerType()) {
            case ADVANCEMENT_COMPLETED -> {
                Advancement advancement = target == null
                        ? null
                        : player.server.getAdvancements().getAdvancement(target);
                yield advancement != null && player.getAdvancements()
                        .getOrStartProgress(advancement).isDone();
            }
            case BLUEPRINT_DISCOVERED -> target != null && state.discovered().contains(target);
            case BLUEPRINT_LEARNED -> target != null && state.learned().contains(target);
            case BLUEPRINT_MILESTONE -> {
                MilestoneState milestoneState = work.award().binding().definition()
                        .trigger().milestone().orElseThrow().state();
                Set<ResourceLocation> ids = milestoneState == MilestoneState.DISCOVERED
                        ? state.discovered()
                        : state.learned();
                yield ResearchPointAwardMilestoneResolver.currentlySatisfied(
                        work.award().binding().definition(),
                        work.context().activeProfile(),
                        DispatchMode.RETROACTIVE,
                        ids,
                        facts);
            }
            case ENTITY_KILLED, INVENTORY_TURN_IN, INTEGRATION -> false;
        };
    }

    private static ClaimKey claimFor(ResolvedAward award, ResearchPointAwardContext context) {
        var definition = award.binding().definition();
        return switch (definition.repeat().type()) {
            case ONCE -> ClaimKey.once(definition.effectiveClaimId(award.binding().definitionId()));
            case ONCE_PER_TARGET -> context.targetId()
                    .map(target -> ClaimKey.targeted(
                            definition.effectiveClaimId(award.binding().definitionId()), target))
                    .orElse(null);
            default -> null;
        };
    }

    private static ResearchPointAwardContext simpleContext(
            Type type,
            ResourceLocation profile,
            ResourceLocation target) {
        return new ResearchPointAwardContext(
                type,
                profile,
                DispatchMode.RETROACTIVE,
                Optional.of(target),
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                0,
                Optional.empty());
    }

    private static Set<ResourceLocation> parseIds(Set<String> values) {
        Set<ResourceLocation> parsed = new LinkedHashSet<>();
        if (values != null) {
            values.stream().sorted().map(ResourceLocation::tryParse)
                    .filter(java.util.Objects::nonNull).forEach(parsed::add);
        }
        return Set.copyOf(parsed);
    }

    private record WorkItem(
            ClaimKey claim,
            ResolvedAward award,
            ResearchPointAwardContext context) {
        private WorkItem {
            if (claim == null || award == null || context == null) {
                throw new IllegalArgumentException("invalid retroactive RP work item");
            }
        }
    }

    private record PlayerState(
            Set<ResourceLocation> discovered,
            Set<ResourceLocation> learned) {
        private PlayerState {
            discovered = Set.copyOf(discovered);
            learned = Set.copyOf(learned);
        }

        private static PlayerState capture(IPlayerRecipeData data) {
            return new PlayerState(
                    parseIds(data.getDiscoveredBlueprints()),
                    parseIds(data.getLearnedBlueprints()));
        }
    }

    private enum PlanningStage {
        ADVANCEMENT_SCAN,
        ADVANCEMENTS,
        DISCOVERED,
        LEARNED,
        DISCOVERED_MILESTONES,
        LEARNED_MILESTONES,
        COMPLETE
    }

    private static final class Session {
        private final long awardRevision;
        private final long catalogRevision;
        private final long researchRevision;
        private final ResearchPointAwardConfigSnapshot config;
        private final ResearchPointAwardSnapshot awards;
        private final Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts;
        private final PlayerState playerState;
        private final Iterator<Advancement> advancements;
        private final PriorityQueue<Advancement> sortedAdvancements = new PriorityQueue<>(
                Comparator.comparing(value -> value.getId().toString()));
        private final List<ResourceLocation> discovered;
        private final List<ResourceLocation> learned;
        private final Deque<WorkItem> ready = new ArrayDeque<>();
        private final RetryableWorkQueue<ClaimKey, WorkItem> deferred = new RetryableWorkQueue<>();
        private final Set<ClaimKey> reservedClaims = new LinkedHashSet<>();
        private PlanningStage stage = PlanningStage.ADVANCEMENT_SCAN;
        private int discoveredIndex;
        private int learnedIndex;
        private ResearchPointAwardMilestoneResolver.RetroactivePlan discoveredMilestones;
        private ResearchPointAwardMilestoneResolver.RetroactivePlan learnedMilestones;

        private Session(
                long awardRevision,
                long catalogRevision,
                long researchRevision,
                ResearchPointAwardConfigSnapshot config,
                ResearchPointAwardSnapshot awards,
                Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts,
                PlayerState playerState,
                Iterator<Advancement> advancements) {
            this.awardRevision = awardRevision;
            this.catalogRevision = catalogRevision;
            this.researchRevision = researchRevision;
            this.config = config;
            this.awards = awards;
            this.facts = Map.copyOf(facts);
            this.playerState = playerState;
            this.advancements = advancements;
            this.discovered = playerState.discovered().stream()
                    .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
            this.learned = playerState.learned().stream()
                    .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
        }

        private boolean stale() {
            return awardRevision != ResearchPointAwardDataManager.INSTANCE.revision()
                    || catalogRevision != BlueprintDataManager.SERVER.catalogRevision()
                    || researchRevision != BlueprintResearchDataManager.INSTANCE.revision()
                    || !config.equals(ModConfigs.BLUEPRINT.awardSnapshot());
        }

        private boolean playerStateInvalidated(PlayerState current) {
            // Additions have already passed through the live dispatcher and do
            // not invalidate older catch-up work. Removals make queued source
            // assertions stale and require a fresh session.
            return !current.discovered().containsAll(playerState.discovered())
                    || !current.learned().containsAll(playerState.learned());
        }

        private PlayerState playerState() {
            return playerState;
        }

        private ResearchPointAwardConfigSnapshot config() {
            return config;
        }

        private Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts() {
            return facts;
        }

        private WorkItem pollReady() {
            return ready.pollFirst();
        }

        private void complete(WorkItem work) {
            reservedClaims.remove(work.claim());
        }

        private void recordResult(
                WorkItem work,
                ResearchPointAwardService.AwardResult result,
                int currentBalance) {
            if (result.status() == ResearchPointAwardService.Status.POINT_CAP_REACHED) {
                deferred.defer(work.claim(), work, currentBalance);
                return;
            }
            complete(work);
        }

        private void wakeRetryable(int currentBalance) {
            ready.addAll(deferred.wakeAfterBalanceDecrease(currentBalance));
        }

        private boolean canPlan(IPlayerRecipeData data) {
            int remainingClaims = PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_CLAIMS
                    - data.getResearchPointAwardLedger().claimCount();
            return stage != PlanningStage.COMPLETE
                    && remainingClaims > 0
                    && reservedClaims.size() < remainingClaims;
        }

        /** Plans at most one advancement, blueprint transition, or milestone state. */
        private boolean planNext(ServerPlayer player, IPlayerRecipeData data, PlayerState currentState) {
            while (stage != PlanningStage.COMPLETE) {
                switch (stage) {
                    case ADVANCEMENT_SCAN -> {
                        if (!hasRetroactive(Type.ADVANCEMENT_COMPLETED)) {
                            stage = PlanningStage.DISCOVERED;
                            continue;
                        }
                        if (advancements.hasNext()) {
                            sortedAdvancements.add(advancements.next());
                            return true;
                        }
                        stage = PlanningStage.ADVANCEMENTS;
                        continue;
                    }
                    case ADVANCEMENTS -> {
                        if (sortedAdvancements.isEmpty()) {
                            stage = PlanningStage.DISCOVERED;
                            continue;
                        }
                        Advancement advancement = sortedAdvancements.remove();
                        if (player.getAdvancements().getOrStartProgress(advancement).isDone()) {
                            addResolution(data, simpleContext(
                                    Type.ADVANCEMENT_COMPLETED,
                                    config.activeProfileId(),
                                    advancement.getId()));
                        }
                        return true;
                    }
                    case DISCOVERED -> {
                        if (!hasRetroactive(Type.BLUEPRINT_DISCOVERED)
                                || discoveredIndex >= discovered.size()) {
                            stage = PlanningStage.LEARNED;
                            continue;
                        }
                        ResourceLocation id = discovered.get(discoveredIndex++);
                        ResearchPointAwardBlueprintFacts value = facts.get(id);
                        if (value != null && currentState.discovered().contains(id)) {
                            addResolution(data, value.context(
                                    Type.BLUEPRINT_DISCOVERED,
                                    config.activeProfileId(),
                                    DispatchMode.RETROACTIVE));
                        }
                        return true;
                    }
                    case LEARNED -> {
                        if (!hasRetroactive(Type.BLUEPRINT_LEARNED)
                                || learnedIndex >= learned.size()) {
                            stage = PlanningStage.DISCOVERED_MILESTONES;
                            continue;
                        }
                        ResourceLocation id = learned.get(learnedIndex++);
                        ResearchPointAwardBlueprintFacts value = facts.get(id);
                        if (value != null && currentState.learned().contains(id)) {
                            addResolution(data, value.context(
                                    Type.BLUEPRINT_LEARNED,
                                    config.activeProfileId(),
                                    DispatchMode.RETROACTIVE));
                        }
                        return true;
                    }
                    case DISCOVERED_MILESTONES -> {
                        if (!hasRetroactive(Type.BLUEPRINT_MILESTONE)) {
                            stage = PlanningStage.LEARNED_MILESTONES;
                            continue;
                        }
                        if (discoveredMilestones == null) {
                            discoveredMilestones = ResearchPointAwardMilestoneResolver.retroactivePlan(
                                    awards,
                                    config.activeProfileId(),
                                    MilestoneState.DISCOVERED,
                                    currentState.discovered(),
                                    facts);
                        }
                        if (discoveredMilestones.step()) {
                            return true;
                        }
                        addMilestones(data, discoveredMilestones.finish());
                        stage = PlanningStage.LEARNED_MILESTONES;
                        return true;
                    }
                    case LEARNED_MILESTONES -> {
                        if (!hasRetroactive(Type.BLUEPRINT_MILESTONE)) {
                            stage = PlanningStage.COMPLETE;
                            continue;
                        }
                        if (learnedMilestones == null) {
                            learnedMilestones = ResearchPointAwardMilestoneResolver.retroactivePlan(
                                    awards,
                                    config.activeProfileId(),
                                    MilestoneState.LEARNED,
                                    currentState.learned(),
                                    facts);
                        }
                        if (learnedMilestones.step()) {
                            return true;
                        }
                        addMilestones(data, learnedMilestones.finish());
                        stage = PlanningStage.COMPLETE;
                        return true;
                    }
                    case COMPLETE -> {
                        return false;
                    }
                }
            }
            return false;
        }

        private boolean hasRetroactive(Type type) {
            return awards.bindingsByTrigger().getOrDefault(type, List.of()).stream()
                    .anyMatch(binding -> binding.definition().trigger().retroactive());
        }

        private void addResolution(IPlayerRecipeData data, ResearchPointAwardContext context) {
            ResearchPointAwardResolver.Resolution resolution =
                    ResearchPointAwardResolver.resolve(awards, context);
            if (resolution.successful()) {
                resolution.awards().forEach(award -> addWork(data, award, context));
            }
        }

        private void addMilestones(
                IPlayerRecipeData data,
                ResearchPointAwardMilestoneResolver.Resolution resolution) {
            if (resolution.successful()) {
                resolution.awards().forEach(value ->
                        addWork(data, value.award(), value.context()));
            }
        }

        private void addWork(
                IPlayerRecipeData data,
                ResolvedAward award,
                ResearchPointAwardContext context) {
            ClaimKey claim = claimFor(award, context);
            if (claim != null
                    && !data.getResearchPointAwardLedger().hasClaim(claim)
                    && reservedClaims.add(claim)) {
                ready.addLast(new WorkItem(claim, award, context));
            }
        }

        private boolean finished(IPlayerRecipeData data) {
            return data.getResearchPointAwardLedger().claimCount()
                            >= PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_CLAIMS
                    || stage == PlanningStage.COMPLETE && ready.isEmpty() && deferred.isEmpty();
        }

        private int pendingWork() {
            return ready.size() + deferred.size();
        }
    }

    /** Small deterministic policy object kept package-visible for boundary tests. */
    static final class RetryableWorkQueue<K, V> {
        private final Map<K, V> values = new LinkedHashMap<>();
        private int highestFailureBalance = -1;

        void defer(K key, V value, int balance) {
            if (key == null || value == null || balance < 0) {
                throw new IllegalArgumentException("invalid retryable reconciliation work");
            }
            values.putIfAbsent(key, value);
            highestFailureBalance = Math.max(highestFailureBalance, balance);
        }

        List<V> wakeAfterBalanceDecrease(int currentBalance) {
            if (currentBalance < 0 || values.isEmpty() || currentBalance >= highestFailureBalance) {
                return List.of();
            }
            List<V> ready = List.copyOf(values.values());
            values.clear();
            highestFailureBalance = -1;
            return ready;
        }

        boolean isEmpty() {
            return values.isEmpty();
        }

        int size() {
            return values.size();
        }
    }
}
