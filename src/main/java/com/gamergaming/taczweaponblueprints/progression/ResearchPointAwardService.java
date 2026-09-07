package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.progression.currency.*;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.api.event.ResearchCurrencyAwardEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.api.event.ResearchPointAwardEvent;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.BudgetUpdate;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.ClaimKey;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.CooldownUpdate;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.Mutation;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.ScopeKey;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.Usage;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.WindowEntry;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.WindowUpdate;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardBudget;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDefinition;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardRepeat;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardResolver;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardResolver.ResolvedAward;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardReward;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardSnapshot.Binding;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

/**
 * Validates repeat and shared-budget history and atomically commits one or more
 * already-resolved datapack awards. External inventory remains source-owned
 * and participates through a narrow validate/commit transaction hook.
 */
public final class ResearchPointAwardService {
    private ResearchPointAwardService() {
    }

    public static BatchResult awardResolved(
            IPlayerRecipeData playerData,
            ResearchPointAwardResolver.Resolution resolution,
            ResearchPointAwardContext context,
            ResearchPointAwardConfigSnapshot config,
            long gameTime) {
        return awardResolved(playerData, resolution, context, config, gameTime, null);
    }

    static BatchResult awardResolved(
            IPlayerRecipeData playerData,
            ResearchPointAwardResolver.Resolution resolution,
            ResearchPointAwardContext context,
            ResearchPointAwardConfigSnapshot config,
            long gameTime,
            ExternalTransaction externalTransaction) {
        if (config != null && ResearchCurrencyPayments.physical(config.currencyPolicy())) return BatchResult.rejected(Status.INVALID_CONTEXT);
        if (resolution == null || !resolution.successful()) {
            return BatchResult.rejected(Status.RESOLUTION_REJECTED);
        }
        List<AwardResult> results = new ArrayList<>(resolution.awards().size());
        boolean externalCommitted = false;
        for (ResolvedAward award : resolution.awards()) {
            AwardResult result = awardOne(
                    playerData,
                    award,
                    context,
                    config,
                    gameTime,
                    externalCommitted ? null : externalTransaction);
            results.add(result);
            externalCommitted |= result.status().awardedPoints() && externalTransaction != null;
        }
        return new BatchResult(Status.PROCESSED, results);
    }

    /**
     * Sequentially runs the same eligibility, repeat, budget, and point-cap
     * checks as a live batch on detached RP state without mutating the player
     * capability. Callers must still invoke
     * {@link #awardResolved} at commit time; this result is a preview, not a
     * reservation.
     */
    public static BatchEvaluation evaluateResolved(
            IPlayerRecipeData playerData,
            ResearchPointAwardResolver.Resolution resolution,
            ResearchPointAwardContext context,
            ResearchPointAwardConfigSnapshot config,
            long gameTime) {
        if (resolution == null || !resolution.successful()) {
            return BatchEvaluation.rejected(Status.RESOLUTION_REJECTED);
        }
        PlayerRecipeData simulation = PlayerRecipeData.copyResearchPointState(playerData);
        if (simulation == null) {
            return BatchEvaluation.rejected(Status.INVALID_CONTEXT);
        }
        boolean physical = ResearchCurrencyPayments.physical(config.currencyPolicy());
        if (physical) config = new ResearchPointAwardConfigSnapshot(config.awardsEnabled(), config.combatAwardsEnabled(),
                PlayerProgressionLimits.MAX_RESEARCH_POINTS, config.activeProfileId());
        List<AwardEvaluation> evaluations = new ArrayList<>(resolution.awards().size());
        for (ResolvedAward award : resolution.awards()) {
            if (physical) simulation.setResearchPoints(0);
            PreparedAward prepared = prepareOne(simulation, award, context, config, gameTime);
            AwardEvaluation evaluation = prepared.evaluation();
            if (evaluation.eligible()) {
                AwardResult simulated = commitPrepared(simulation, prepared, config);
                if (!simulated.committed()) {
                    evaluation = new AwardEvaluation(
                            evaluation.definitionId(), simulated.status(), 0, 0);
                }
            }
            evaluations.add(evaluation);
        }
        return new BatchEvaluation(Status.PROCESSED, evaluations);
    }

    public static AwardResult awardOne(
            IPlayerRecipeData playerData,
            ResolvedAward award,
            ResearchPointAwardContext context,
            ResearchPointAwardConfigSnapshot config,
            long gameTime) {
        return awardOne(playerData, award, context, config, gameTime, null);
    }

    private static AwardResult awardOne(
            IPlayerRecipeData playerData,
            ResolvedAward award,
            ResearchPointAwardContext context,
            ResearchPointAwardConfigSnapshot config,
            long gameTime,
            ExternalTransaction externalTransaction) {
        if (config != null && ResearchCurrencyPayments.physical(config.currencyPolicy())) return AwardResult.failure(award == null ? null : award.binding().definitionId(), Status.INVALID_CONTEXT);
        PreparedAward prepared = prepareOne(playerData, award, context, config, gameTime);
        AwardEvaluation evaluation = prepared.evaluation();
        ResourceLocation definitionId = evaluation.definitionId();
        if (!evaluation.eligible()) {
            return AwardResult.failure(definitionId, evaluation.status());
        }
        if (externalTransaction != null && !externalTransaction.valid()) {
            return AwardResult.failure(definitionId, Status.EXTERNAL_STATE_CHANGED);
        }
        return commitWithExternal(playerData, prepared, config, externalTransaction);
    }

    /**
     * Server-aware commit path. It exposes immutable Forge pre/post events while
     * retaining this service's exact evaluation and atomic transaction rules.
     */
    public static AwardResult awardOne(
            ServerPlayer player,
            IPlayerRecipeData playerData,
            ResolvedAward award,
            ResearchPointAwardContext context,
            ResearchPointAwardConfigSnapshot config,
            long gameTime) {
        if (award == null) return AwardResult.failure(null, Status.INVALID_CONTEXT);
        if (ResearchCurrencyPayments.physical(ModConfigs.BLUEPRINT.currencyPolicy())) {
            return awardPhysical(player, playerData, List.of(award), context, config, gameTime, null).awards().stream()
                    .findFirst().orElseGet(() -> AwardResult.failure(award.binding().definitionId(), Status.COMMIT_REJECTED));
        }
        return awardOne(player, playerData, award, context, config, gameTime, null);
    }

    private static AwardResult awardOne(
            ServerPlayer player,
            IPlayerRecipeData playerData,
            ResolvedAward award,
            ResearchPointAwardContext context,
            ResearchPointAwardConfigSnapshot config,
            long gameTime,
            ExternalTransaction externalTransaction) {
        PreparedAward prepared = prepareOne(playerData, award, context, config, gameTime);
        AwardEvaluation evaluation = prepared.evaluation();
        ResourceLocation definitionId = evaluation.definitionId();
        if (!evaluation.eligible()) {
            return AwardResult.failure(definitionId, evaluation.status());
        }
        if (player == null || player.server == null || !player.server.isSameThread()) {
            return AwardResult.failure(definitionId, Status.INVALID_CONTEXT);
        }
        long awardRevision = com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDataManager.INSTANCE.revision();
        if (!livePublicationCurrent(config, awardRevision, context)) return AwardResult.failure(definitionId, Status.STALE_RESOLUTION);
        ResearchPointAwardEvent.Pre pre = new ResearchPointAwardEvent.Pre(
                player, definitionId, context, evaluation.requestedPoints());
        if (MinecraftForge.EVENT_BUS.post(pre)) {
            return AwardResult.failure(definitionId, Status.CANCELLED);
        }
        // Event listeners run on the same server thread but may still change
        // progression state. Re-evaluate after the callback so a stale balance,
        // claim, cooldown, window, or budget snapshot is never committed.
        if (!livePublicationCurrent(config, awardRevision, context)) return AwardResult.failure(definitionId, Status.STALE_RESOLUTION);
        PreparedAward revalidated = prepareOne(playerData, award, context, config, gameTime);
        if (!revalidated.evaluation().eligible()) {
            return AwardResult.failure(
                    definitionId, revalidated.evaluation().status());
        }
        if (externalTransaction != null && !externalTransaction.valid()) {
            return AwardResult.failure(definitionId, Status.EXTERNAL_STATE_CHANGED);
        }
        if (!livePublicationCurrent(config, awardRevision, context)) return AwardResult.failure(definitionId, Status.STALE_RESOLUTION);
        AwardResult result = commitWithExternal(playerData, revalidated, config, externalTransaction);
        if (result.committed()) {
            try { MinecraftForge.EVENT_BUS.post(new ResearchPointAwardEvent.Post(
                    player,
                    definitionId,
                    context,
                    result.requestedPoints(),
                    result.status(),
                    result.awardedPoints())); }
            catch (RuntimeException exception) { com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.LOGGER.warn("Banked post-award listener failed", exception); }
        }
        return result;
    }

    public static BatchResult awardResolved(
            ServerPlayer player,
            IPlayerRecipeData playerData,
            ResearchPointAwardResolver.Resolution resolution,
            ResearchPointAwardContext context,
            ResearchPointAwardConfigSnapshot config,
            long gameTime) {
        return awardResolved(player, playerData, resolution, context, config, gameTime, null);
    }

    static BatchResult awardResolved(
            ServerPlayer player,
            IPlayerRecipeData playerData,
            ResearchPointAwardResolver.Resolution resolution,
            ResearchPointAwardContext context,
            ResearchPointAwardConfigSnapshot config,
            long gameTime,
            ExternalTransaction externalTransaction) {
        if (resolution == null || !resolution.successful()) {
            return BatchResult.rejected(Status.RESOLUTION_REJECTED);
        }
        if (ResearchCurrencyPayments.physical(ModConfigs.BLUEPRINT.currencyPolicy()))
            return awardPhysical(player, playerData, resolution.awards(), context, config, gameTime, externalTransaction);
        List<AwardResult> results = new ArrayList<>(resolution.awards().size());
        boolean externalCommitted = false;
        for (ResolvedAward award : resolution.awards()) {
            AwardResult result = awardOne(
                    player,
                    playerData,
                    award,
                    context,
                    config,
                    gameTime,
                    externalCommitted ? null : externalTransaction);
            results.add(result);
            externalCommitted |= result.status().awardedPoints() && externalTransaction != null;
        }
        return new BatchResult(Status.PROCESSED, results);
    }

    private static boolean livePublicationCurrent(ResearchPointAwardConfigSnapshot config, long revision, ResearchPointAwardContext context) {
        return config != null && turnInAllowed(context) && config.equals(ModConfigs.BLUEPRINT.awardSnapshot())
                && revision == com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDataManager.INSTANCE.revision();
    }

    private static boolean turnInAllowed(ResearchPointAwardContext context) {
        return context.triggerType() != com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.Type.INVENTORY_TURN_IN
                || context.targetId().filter(ResearchDataAnalysisService::legacyAllowed).isPresent();
    }

    private static BatchResult awardPhysical(ServerPlayer player, IPlayerRecipeData data,
            List<ResolvedAward> awards, ResearchPointAwardContext context, ResearchPointAwardConfigSnapshot config,
            long gameTime, ExternalTransaction external) {
        if (player == null || data == null || config == null || context == null || awards == null
                || player.server == null || !player.server.isSameThread())
            return BatchResult.rejected(Status.INVALID_CONTEXT);
        long awardRevision = com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDataManager.INSTANCE.revision();
        if (!livePublicationCurrent(config, awardRevision, context)) return BatchResult.rejected(Status.STALE_RESOLUTION);
        var currency = ModConfigs.BLUEPRINT.currencyPolicy();
        var allowed = new ArrayList<ResolvedAward>();
        var rejected = new ArrayList<AwardResult>();
        var physicalConfig = new ResearchPointAwardConfigSnapshot(config.awardsEnabled(), config.combatAwardsEnabled(),
                PlayerProgressionLimits.MAX_RESEARCH_POINTS, config.activeProfileId());
        for (var award : awards) {
            var copy = PlayerRecipeData.copyResearchPointState(data); copy.setResearchPoints(0);
            var e = prepareOne(copy, award, context, physicalConfig, gameTime).evaluation();
            if (!e.eligible()) { rejected.add(AwardResult.failure(e.definitionId(), e.status())); continue; }
            if (MinecraftForge.EVENT_BUS.post(new ResearchCurrencyAwardEvent.Pre(player, e.definitionId(), context, currency, e.requestedPoints())))
                rejected.add(AwardResult.failure(e.definitionId(), Status.CANCELLED));
            else allowed.add(award);
        }
        if (!turnInAllowed(context) || !currency.equals(ModConfigs.BLUEPRINT.currencyPolicy()) || !config.equals(ModConfigs.BLUEPRINT.awardSnapshot())
                || awardRevision != com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDataManager.INSTANCE.revision())
            return BatchResult.rejected(Status.STALE_RESOLUTION);
        var live = external != null && external.currencyContext() != null ? external.currencyContext()
                : PlayerResearchCurrencyContext.inventory(player, data);
        var guarded = ResearchCurrencyTransaction.guarded(live, () -> turnInAllowed(context) && config.equals(ModConfigs.BLUEPRINT.awardSnapshot())
                && awardRevision == com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDataManager.INSTANCE.revision());
        var committed = commitPhysicalAwards(data, allowed, context, config, gameTime, guarded, external);
        for (var value : committed.awards()) if (value.committed()) {
            try { MinecraftForge.EVENT_BUS.post(new ResearchCurrencyAwardEvent.Post(player, value.definitionId(), context, currency, value.awardedPoints())); }
            catch (RuntimeException exception) { com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.LOGGER.warn("Currency post-award listener failed", exception); }
        }
        rejected.addAll(committed.awards());
        return orderedBatch(committed.status(), awards, rejected);
    }

    /** Same complete delivery/ledger transaction used by the live dispatcher, injectable for regression checks. */
    static BatchResult commitPhysicalAwards(IPlayerRecipeData data, List<ResolvedAward> allowed,
            ResearchPointAwardContext context, ResearchPointAwardConfigSnapshot config, long gameTime,
            ResearchCurrencyTransaction.Context live, ExternalTransaction external) {
        if (data == null || config == null || context == null || allowed == null || live == null || !live.valid() || !config.currencyPolicy().equals(live.policy())
                || !ResearchCurrencyPayments.physical(live.policy())) return BatchResult.rejected(Status.INVALID_CONTEXT);
        var currency = live.policy();
        var physicalConfig = new ResearchPointAwardConfigSnapshot(config.awardsEnabled(), config.combatAwardsEnabled(),
                PlayerProgressionLimits.MAX_RESEARCH_POINTS, config.activeProfileId());
        var rejected = new ArrayList<AwardResult>();
        var beforeLedger = data.getResearchPointAwardLedger().serializeNBT().copy();
        var simulation = PlayerRecipeData.copyResearchPointState(data);
        var successful = new ArrayList<AwardResult>();
        long units = 0;
        for (var award : allowed) {
            simulation.setResearchPoints(0);
            var prepared = prepareOne(simulation, award, context, physicalConfig, gameTime);
            if (!prepared.evaluation().eligible()) {
                rejected.add(AwardResult.failure(prepared.evaluation().definitionId(), prepared.evaluation().status())); continue;
            }
            var result = commitPrepared(simulation, prepared, physicalConfig);
            if (!result.committed()) { rejected.add(result); continue; }
            units += result.awardedPoints(); successful.add(result);
        }
        if (successful.isEmpty()) return orderedBatch(Status.PROCESSED, allowed, rejected);
        var afterLedger = simulation.getResearchPointAwardLedger().serializeNBT().copy();
        var destination = external != null ? external.destination() : ResearchCurrencyPlanner.Destination.EVENT;
        var request = new ResearchCurrencyPlanner.Request(0, List.of(), units, destination, ResearchPointTransactionService.OverflowPolicy.REQUIRE_FULL);
        boolean inventoryInput = external != null && external.inventorySlot() >= 0;
        var plan = inventoryInput ? ResearchCurrencyPlanner.prepareWithInput(currency, live.read(), request, external.inventorySlot(), 1)
                : ResearchCurrencyPlanner.prepare(currency, live.read(), request);
        ResearchCurrencyTransaction.Participant ledger = new ResearchCurrencyTransaction.Participant() {
            public boolean ready() { return beforeLedger.equals(data.getResearchPointAwardLedger().serializeNBT()) && (external == null || external.valid()); }
            public boolean commit() {
                if (!beforeLedger.equals(data.getResearchPointAwardLedger().serializeNBT())) return false;
                data.getResearchPointAwardLedger().deserializeNBT(afterLedger.copy());
                if (external != null && !inventoryInput) external.commit();
                return committed();
            }
            public boolean committed() { return afterLedger.equals(data.getResearchPointAwardLedger().serializeNBT()) && (external == null || inventoryInput || external.committed()); }
            public boolean rollback() {
                boolean restored = true;
                if (external != null && !inventoryInput) {
                    try { restored = external.rollback(); } catch (RuntimeException failure) { restored = false; }
                }
                var current = data.getResearchPointAwardLedger().serializeNBT();
                if (!current.equals(beforeLedger) && !current.equals(afterLedger)) return false;
                data.getResearchPointAwardLedger().deserializeNBT(beforeLedger.copy());
                return restored && beforeLedger.equals(data.getResearchPointAwardLedger().serializeNBT());
            }
        };
        var committed = ResearchCurrencyTransaction.commit(plan, live, ledger);
        if (!committed.successful()) {
            for (var value : successful) rejected.add(AwardResult.failure(value.definitionId(),
                    committed.status() == ResearchCurrencyTransaction.Status.ROLLBACK_FAILED ? Status.ROLLBACK_FAILED : Status.DELIVERY_FAILED));
            return orderedBatch(Status.PROCESSED, allowed, rejected);
        }
        rejected.addAll(successful);
        return orderedBatch(Status.PROCESSED, allowed, rejected);
    }

    private static BatchResult orderedBatch(Status status, List<ResolvedAward> requested, List<AwardResult> results) {
        var byId = new java.util.HashMap<ResourceLocation, AwardResult>();
        results.forEach(result -> byId.put(result.definitionId(), result));
        return new BatchResult(status, requested.stream().map(award -> byId.getOrDefault(award.binding().definitionId(),
                AwardResult.failure(award.binding().definitionId(), Status.COMMIT_REJECTED))).toList());
    }

    private static AwardResult commitWithExternal(IPlayerRecipeData data, PreparedAward prepared,
            ResearchPointAwardConfigSnapshot config, ExternalTransaction external) {
        if (external == null) return commitPrepared(data, prepared, config);
        int points = data.getResearchPoints();
        var ledger = data.getResearchPointAwardLedger().serializeNBT().copy();
        boolean touchedInput = false;
        try {
            if (!external.valid()) return AwardResult.failure(prepared.evaluation().definitionId(), Status.EXTERNAL_STATE_CHANGED);
            var result = commitPrepared(data, prepared, config);
            if (result.status().awardedPoints()) {
                touchedInput = true; external.commit();
                if (external.verifiable() && !external.committed()) throw new IllegalStateException("turn-in input was not consumed");
            }
            return result;
        } catch (RuntimeException exception) {
            boolean restored = true;
            if (touchedInput) try { restored = external.rollback(); } catch (RuntimeException error) { restored = false; }
            try { restored &= data.setResearchPoints(points); } catch (RuntimeException error) { restored = false; }
            try { data.getResearchPointAwardLedger().deserializeNBT(ledger.copy()); restored &= ledger.equals(data.getResearchPointAwardLedger().serializeNBT()); }
            catch (RuntimeException error) { restored = false; }
            return AwardResult.failure(prepared.evaluation().definitionId(), restored ? Status.COMMIT_REJECTED : Status.ROLLBACK_FAILED);
        }
    }

    private static AwardResult commitPrepared(
            IPlayerRecipeData playerData,
            PreparedAward prepared,
            ResearchPointAwardConfigSnapshot config) {
        AwardEvaluation evaluation = prepared.evaluation();
        ResourceLocation definitionId = evaluation.definitionId();
        if (prepared.ledgerOnlyAtCap()) {
            ResearchPointTransactionService.Result committed = ResearchPointTransactionService.credit(
                    playerData,
                    evaluation.requestedPoints(),
                    config.pointCap(),
                    prepared.overflow(),
                    prepared.mutation());
            return committed.status() == ResearchPointTransactionService.Status.LEDGER_RECORDED_AT_CAP
                    ? new AwardResult(definitionId, Status.LEDGER_RECORDED_AT_CAP,
                            evaluation.requestedPoints(), 0)
                    : AwardResult.failure(definitionId, mapTransactionFailure(committed.status()));
        }
        ResearchPointTransactionService.Result committed = ResearchPointTransactionService.credit(
                playerData,
                evaluation.requestedPoints(),
                config.pointCap(),
                prepared.overflow(),
                prepared.mutation());
        if (!committed.successful()) {
            return AwardResult.failure(definitionId, mapTransactionFailure(committed.status()));
        }
        Status status = committed.status() == ResearchPointTransactionService.Status.PARTIALLY_AWARDED
                ? Status.PARTIALLY_AWARDED
                : Status.AWARDED;
        return new AwardResult(
                definitionId, status, evaluation.requestedPoints(), committed.awardedPoints());
    }

    private static PreparedAward prepareOne(
            IPlayerRecipeData playerData,
            ResolvedAward award,
            ResearchPointAwardContext context,
            ResearchPointAwardConfigSnapshot config,
            long gameTime) {
        ResourceLocation definitionId = award == null ? null : award.binding().definitionId();
        if (playerData == null || award == null || context == null || config == null || gameTime < 0L) {
            return PreparedAward.failure(definitionId, Status.INVALID_CONTEXT);
        }
        if (!config.awardsEnabled()) {
            return PreparedAward.failure(definitionId, Status.DISABLED);
        }
        if (context.triggerType() == ResearchPointAwardTrigger.Type.ENTITY_KILLED
                && !config.combatAwardsEnabled()) {
            return PreparedAward.failure(definitionId, Status.COMBAT_DISABLED);
        }

        Binding binding = award.binding();
        ResearchPointAwardDefinition definition = binding.definition();
        if (!context.activeProfile().equals(config.activeProfileId())
                || !definition.appliesToProfile(config.activeProfileId())
                || !definition.trigger().conditionsMatch(context)
                || definition.trigger().targetSpecificity(context) != award.specificity()) {
            return PreparedAward.failure(definitionId, Status.STALE_RESOLUTION);
        }

        ResearchPointAwardLedger ledger = playerData.getResearchPointAwardLedger();
        RepeatDecision repeat = evaluateRepeat(ledger, binding, context, gameTime);
        if (!repeat.allowed()) {
            return PreparedAward.failure(definitionId, repeat.status());
        }

        ResearchPointTransactionService.OverflowPolicy overflow =
                definition.reward().overflow() == ResearchPointAwardReward.Overflow.CLAMP
                        ? ResearchPointTransactionService.OverflowPolicy.CLAMP
                        : ResearchPointTransactionService.OverflowPolicy.REQUIRE_FULL;
        ResearchPointTransactionService.Evaluation transaction = ResearchPointTransactionService.evaluate(
                playerData, definition.reward().points(), config.pointCap(), overflow);
        if (!transaction.successful()) {
            if (transaction.status() == ResearchPointTransactionService.Status.POINT_CAP_REACHED
                    && overflow == ResearchPointTransactionService.OverflowPolicy.CLAMP
                    && repeat.claim().isPresent()) {
                Mutation mutation = new Mutation(
                        repeat.claim(), Optional.empty(), Optional.empty(), Optional.empty());
                return PreparedAward.eligible(
                        definitionId, definition.reward().points(), 0, overflow, mutation, true);
            }
            return PreparedAward.failure(definitionId, mapTransactionFailure(transaction.status()));
        }

        int awardedPoints = transaction.awardedPoints();
        if (!withinUsage(repeat.windowUsage(), awardedPoints)) {
            return PreparedAward.failure(definitionId, Status.RATE_LIMITED);
        }
        Optional<BudgetUpdate> budgetUpdate = Optional.empty();
        if (definition.budget().isPresent()) {
            ResearchPointAwardBudget budget = definition.budget().orElseThrow();
            Usage usage = ledger.budgetUsage(budget.id(), minimumGameTime(gameTime, budget.windowTicks()));
            if (usage.awards() >= budget.maximumAwards()
                    || usage.points() > (long) budget.maximumPoints() - awardedPoints) {
                return PreparedAward.failure(definitionId, Status.RATE_LIMITED);
            }
            budgetUpdate = Optional.of(new BudgetUpdate(
                    budget.id(), new WindowEntry(gameTime, awardedPoints)));
        }
        Mutation mutation = new Mutation(
                repeat.claim(),
                repeat.cooldownScope().map(scope -> new CooldownUpdate(scope, gameTime)),
                repeat.windowScope().map(scope -> new WindowUpdate(
                        scope, new WindowEntry(gameTime, awardedPoints))),
                budgetUpdate);
        return PreparedAward.eligible(
                definitionId,
                definition.reward().points(),
                awardedPoints,
                overflow,
                mutation,
                false);
    }

    private static RepeatDecision evaluateRepeat(
            ResearchPointAwardLedger ledger,
            Binding binding,
            ResearchPointAwardContext context,
            long gameTime) {
        if (ledger == null) {
            return RepeatDecision.rejected(Status.INVALID_CONTEXT);
        }
        ResearchPointAwardDefinition definition = binding.definition();
        ResearchPointAwardRepeat repeat = definition.repeat();
        Optional<ResourceLocation> target = context.targetId();
        Optional<ClaimKey> claim = Optional.empty();
        Optional<ScopeKey> cooldownScope = Optional.empty();
        Optional<ScopeKey> windowScope = Optional.empty();
        Optional<UsageLimit> windowUsage = Optional.empty();

        switch (repeat.type()) {
            case ONCE -> claim = Optional.of(ClaimKey.once(
                    definition.effectiveClaimId(binding.definitionId())));
            case ONCE_PER_TARGET -> {
                if (target.isEmpty()) {
                    return RepeatDecision.rejected(Status.INVALID_CONTEXT);
                }
                claim = Optional.of(ClaimKey.targeted(
                        definition.effectiveClaimId(binding.definitionId()), target.orElseThrow()));
            }
            case COOLDOWN -> {
                ScopeKey scope = rateScope(binding.definitionId(), repeat.scope(), target);
                if (scope == null) {
                    return RepeatDecision.rejected(Status.INVALID_CONTEXT);
                }
                OptionalLong last = ledger.lastAwardGameTime(scope);
                long cooldownTicks = repeat.cooldownTicks().orElseThrow();
                if (last.isPresent()
                        && (last.getAsLong() > gameTime
                                || gameTime - last.getAsLong() < cooldownTicks)) {
                    return RepeatDecision.rejected(Status.COOLDOWN_ACTIVE);
                }
                cooldownScope = Optional.of(scope);
            }
            case WINDOWED -> {
                ScopeKey scope = rateScope(binding.definitionId(), repeat.scope(), target);
                if (scope == null) {
                    return RepeatDecision.rejected(Status.INVALID_CONTEXT);
                }
                Usage usage = ledger.windowUsage(
                        scope,
                        minimumGameTime(gameTime, repeat.windowTicks().orElseThrow()));
                if (usage.awards() >= repeat.maximumAwards().orElseThrow()) {
                    return RepeatDecision.rejected(Status.RATE_LIMITED);
                }
                windowScope = Optional.of(scope);
                windowUsage = Optional.of(new UsageLimit(
                        usage,
                        repeat.maximumAwards().orElseThrow(),
                        repeat.maximumPoints().orElseThrow()));
            }
            case UNLIMITED -> {
                // Intentionally no definition-local state.
            }
        }
        if (claim.filter(ledger::hasClaim).isPresent()) {
            return RepeatDecision.rejected(Status.ALREADY_CLAIMED);
        }
        return new RepeatDecision(
                true, Status.ELIGIBLE, claim, cooldownScope, windowScope, windowUsage);
    }

    private static ScopeKey rateScope(
            ResourceLocation definitionId,
            ResearchPointAwardRepeat.Scope scope,
            Optional<ResourceLocation> target) {
        if (scope == ResearchPointAwardRepeat.Scope.DEFINITION) {
            return ScopeKey.global(definitionId);
        }
        return target.map(value -> ScopeKey.targeted(definitionId, value)).orElse(null);
    }

    private static boolean withinUsage(Optional<UsageLimit> limit, int points) {
        if (limit.isEmpty()) {
            return true;
        }
        UsageLimit value = limit.orElseThrow();
        return value.usage().awards() < value.maximumAwards()
                && value.usage().points() <= (long) value.maximumPoints() - points;
    }

    private static long minimumGameTime(long gameTime, long windowTicks) {
        long spanBeforeCurrentTick = windowTicks - 1L;
        return spanBeforeCurrentTick > gameTime
                ? 0L
                : gameTime - spanBeforeCurrentTick;
    }

    private static Status mapTransactionFailure(ResearchPointTransactionService.Status status) {
        return switch (status) {
            case POINT_CAP_REACHED -> Status.POINT_CAP_REACHED;
            case COMMIT_REJECTED -> Status.COMMIT_REJECTED;
            default -> Status.INVALID_CONTEXT;
        };
    }

    public enum Status {
        PROCESSED,
        ELIGIBLE,
        AWARDED,
        PARTIALLY_AWARDED,
        LEDGER_RECORDED_AT_CAP,
        ALREADY_CLAIMED,
        COOLDOWN_ACTIVE,
        RATE_LIMITED,
        POINT_CAP_REACHED,
        DISABLED,
        COMBAT_DISABLED,
        CANCELLED,
        STALE_RESOLUTION,
        RESOLUTION_REJECTED,
        INVALID_CONTEXT,
        EXTERNAL_STATE_CHANGED,
        COMMIT_REJECTED,
        DELIVERY_FAILED,
        ROLLBACK_FAILED;

        public boolean committed() {
            return this == AWARDED || this == PARTIALLY_AWARDED
                    || this == LEDGER_RECORDED_AT_CAP;
        }

        public boolean awardedPoints() {
            return this == AWARDED || this == PARTIALLY_AWARDED;
        }
    }

    public record AwardResult(
            ResourceLocation definitionId,
            Status status,
            int requestedPoints,
            int awardedPoints) {
        public AwardResult {
            if (status == null || requestedPoints < 0 || awardedPoints < 0
                    || awardedPoints > requestedPoints) {
                throw new IllegalArgumentException("invalid Research Point award result");
            }
        }

        public boolean committed() {
            return status.committed();
        }

        private static AwardResult failure(ResourceLocation definitionId, Status status) {
            return new AwardResult(definitionId, status, 0, 0);
        }
    }

    public record BatchResult(Status status, List<AwardResult> awards) {
        public BatchResult {
            if (status == null || awards == null) {
                throw new IllegalArgumentException("invalid Research Point award batch");
            }
            awards = List.copyOf(awards);
        }

        public int awardedPoints() {
            return awards.stream().mapToInt(AwardResult::awardedPoints).sum();
        }

        public boolean changed() {
            return awards.stream().anyMatch(AwardResult::committed);
        }

        public boolean pointsChanged() {
            return awards.stream().anyMatch(value -> value.status().awardedPoints());
        }

        private static BatchResult rejected(Status status) {
            return new BatchResult(status, List.of());
        }
    }

    public record AwardEvaluation(
            ResourceLocation definitionId,
            Status status,
            int requestedPoints,
            int awardablePoints) {
        public AwardEvaluation {
            if (status == null || requestedPoints < 0 || awardablePoints < 0
                    || awardablePoints > requestedPoints) {
                throw new IllegalArgumentException("invalid Research Point award evaluation");
            }
        }

        public boolean eligible() {
            return status == Status.ELIGIBLE;
        }
    }

    public record BatchEvaluation(Status status, List<AwardEvaluation> awards) {
        public BatchEvaluation {
            if (status == null || awards == null) {
                throw new IllegalArgumentException("invalid Research Point award evaluation batch");
            }
            awards = List.copyOf(awards);
        }

        public int awardablePoints() {
            long points = awards.stream().filter(AwardEvaluation::eligible)
                    .mapToLong(AwardEvaluation::awardablePoints).sum();
            return (int) Math.min(PlayerProgressionLimits.MAX_RESEARCH_POINTS, points);
        }

        public boolean eligible() {
            return awardablePoints() > 0;
        }

        private static BatchEvaluation rejected(Status status) {
            return new BatchEvaluation(status, List.of());
        }
    }

    private record PreparedAward(
            AwardEvaluation evaluation,
            ResearchPointTransactionService.OverflowPolicy overflow,
            Mutation mutation,
            boolean ledgerOnlyAtCap) {
        private PreparedAward {
            if (evaluation == null || overflow == null || mutation == null) {
                throw new IllegalArgumentException("invalid prepared Research Point award");
            }
        }

        private static PreparedAward failure(ResourceLocation definitionId, Status status) {
            return new PreparedAward(
                    new AwardEvaluation(definitionId, status, 0, 0),
                    ResearchPointTransactionService.OverflowPolicy.REQUIRE_FULL,
                    Mutation.empty(),
                    false);
        }

        private static PreparedAward eligible(
                ResourceLocation definitionId,
                int requestedPoints,
                int awardablePoints,
                ResearchPointTransactionService.OverflowPolicy overflow,
                Mutation mutation,
                boolean ledgerOnlyAtCap) {
            return new PreparedAward(
                    new AwardEvaluation(
                            definitionId, Status.ELIGIBLE, requestedPoints, awardablePoints),
                    overflow,
                    mutation,
                    ledgerOnlyAtCap);
        }
    }

    private record UsageLimit(Usage usage, int maximumAwards, int maximumPoints) {
    }

    interface ExternalTransaction {
        boolean valid();

        void commit();
        default boolean verifiable() { return false; }
        default int inventorySlot() { return -1; }
        default boolean committed() { return false; }
        default boolean rollback() { return false; }
        default ResearchCurrencyTransaction.Context currencyContext() { return null; }
        default ResearchCurrencyPlanner.Destination destination() { return ResearchCurrencyPlanner.Destination.INVENTORY_ONLY; }
    }

    private record RepeatDecision(
            boolean allowed,
            Status status,
            Optional<ClaimKey> claim,
            Optional<ScopeKey> cooldownScope,
            Optional<ScopeKey> windowScope,
            Optional<UsageLimit> windowUsage) {
        private static RepeatDecision rejected(Status status) {
            return new RepeatDecision(
                    false,
                    status,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());
        }
    }
}
