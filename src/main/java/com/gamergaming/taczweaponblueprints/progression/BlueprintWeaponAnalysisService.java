package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.compat.magazines.InsertedMagazineGuard;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPayments;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.LongUnaryOperator;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.init.ModAttachments;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintFragmentItem;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.item.BlueprintProvenance;
import com.gamergaming.taczweaponblueprints.item.PhysicalWeaponProvenance;
import com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Tiered weapon transaction. Preview/preflight never select an outcome. */
public final class BlueprintWeaponAnalysisService {
    private BlueprintWeaponAnalysisService() { }
    private static volatile BlueprintAnalyzerPolicyResolver.Context cachedContext;

    public static BlueprintAnalyzerPolicyResolver.Context context() {
        var research = BlueprintResearchDataManager.INSTANCE.snapshot();
        long revision = BlueprintResearchDataManager.INSTANCE.revision();
        long catalogRevision = BlueprintDataManager.SERVER.catalogRevision();
        var progression = BlueprintProgressionPolicyManager.INSTANCE.publication().snapshot();
        var stations = ModConfigs.BLUEPRINT.researchFeatureSnapshot().stations();
        var cached = cachedContext;
        if (cached != null && cached.research() == research && cached.researchRevision() == revision
                && cached.catalogRevision() == catalogRevision && cached.progression() == progression && cached.stations().equals(stations)) return cached;
        cached = new BlueprintAnalyzerPolicyResolver.Context(research, BlueprintDataManager.SERVER.getBlueprintDataMap(),
                revision, catalogRevision, progression, stations);
        cachedContext = cached;
        return cached;
    }

    public static BlueprintAnalyzerPolicy.Origin origin(ItemStack stack) {
        return PhysicalWeaponProvenance.from(stack).map(p -> switch (p.origin()) {
                    case LOOT_GENERATED -> BlueprintAnalyzerPolicy.Origin.FOUND;
                    case CRAFTED_SURVIVAL -> BlueprintAnalyzerPolicy.Origin.CRAFTED;
                    case TRADED -> BlueprintAnalyzerPolicy.Origin.TRADED;
                })
                .orElse(BlueprintAnalyzerPolicy.Origin.UNKNOWN);
    }

    /** Empty means the legacy evaluator owns this input; blocked tiered inputs never fall back. */
    public static Optional<Prepared> prepare(ServerPlayer player, WorkstationTransaction station) {
        if (player == null || !player.isAlive() || station == null) return Optional.empty();
        var data = ModAttachments.recipeData(player);
        if (data.isEmpty()) return Optional.empty();
        var context = context();
        var physical = PhysicalItemBlueprintResolver.resolve(station.physicalInput(), context.catalog());
        if (!physical.resolved() || physical.data().orElseThrow().getKind() != BlueprintKind.GUN) return Optional.empty();
        var config = ModConfigs.BLUEPRINT.progressionSnapshot();
        var accessConfig = ModConfigs.BLUEPRINT.accessSnapshot();
        var tier = station.analyzerTier();
        var quote = BlueprintAnalyzerPolicyResolver.resolve(context, config.activeProfileId(), physical.blueprintId().orElseThrow(),
                tier.orElse(AnalyzerTier.TIER_1), origin(station.physicalInput()), config.foundWeaponRecoveryMode());
        if (quote.status() == AnalyzerPolicyQuote.Status.LEGACY) return Optional.empty();
        var evaluation = BlueprintReverseEngineeringService.evaluate(station.physicalInput(), station.inventoryStacks(),
                station.outputStack().isEmpty(), context.research(), context.catalog(), config.activeProfileId(), data.orElseThrow(),
                ModConfigs.BLUEPRINT::isItemBlacklisted, BlueprintProgressionAccess::isProgressionExempt,
                config.blueprintsEnabled(), config.pointCap(), null, false, quote.attempt().orElse(null), station.currencyPolicy());
        if (quote.status() != AnalyzerPolicyQuote.Status.CONFIGURED
                && evaluation.status() != Status.MAGAZINE_INSERTED) {
            evaluation = evaluation.withStatus(switch (quote.status()) {
                case STALE_PROGRESSION -> Status.STALE_POLICY;
                case CONTENT_UNAVAILABLE -> Status.CONTENT_UNAVAILABLE;
                case DIRECT_RECOVERY_ONLY -> Status.RECOVERY_MODE_DISABLED;
                default -> Status.POLICY_INELIGIBLE;
            });
        }
        if (tier.isEmpty() || !station.stationValid()) evaluation = evaluation.withStatus(Status.STALE_POLICY);
        BooleanSupplier current = () -> player.isAlive() && station.stationValid() && station.analyzerTier().equals(tier)
                && ModConfigs.BLUEPRINT.progressionSnapshot().equals(config)
                && ModConfigs.BLUEPRINT.accessSnapshot().equals(accessConfig)
                && !ModConfigs.BLUEPRINT.isItemBlacklisted(quote.blueprintId().toString())
                && !BlueprintProgressionAccess.isProgressionExempt(quote.blueprintId())
                && context() == context;
        return Optional.of(new Prepared(evaluation, quote, data.orElseThrow(), station, current));
    }

    /** One-use server quote plus a complete physical/progression snapshot and policy/station lease. */
    public static final class Prepared {
        private final ResearchCurrencyPolicy currency;
        private final Evaluation evaluation;
        private final AnalyzerPolicyQuote quote;
        private final ItemStack input, output;
        private final List<ItemStack> inventory;
        private final CompoundTag progression;
        private final BooleanSupplier current;
        private final java.util.Set<String> learned, recipes, discovered;
        private final java.util.Map<String, Integer> ordinary, criteria;
        private final java.util.Map<String, AnalysisFragmentCounts> analysis;
        private final CompoundTag ledger;
        private final java.util.List<com.gamergaming.taczweaponblueprints.capabilities.RecentBlueprintUnlockBatch> history;
        private boolean used;
        public Prepared(Evaluation evaluation, AnalyzerPolicyQuote quote, IPlayerRecipeData data,
                        WorkstationTransaction station, BooleanSupplier current) {
            this.currency = station.currencyPolicy();
            this.evaluation = java.util.Objects.requireNonNull(evaluation);
            this.quote = java.util.Objects.requireNonNull(quote);
            this.current = java.util.Objects.requireNonNull(current);
            input = station.physicalInput().copy(); output = station.outputStack().copy();
            inventory = station.inventoryStacks().stream().map(ItemStack::copy).toList();
            progression = data.serializeNBT().copy();
            learned = java.util.Set.copyOf(data.getLearnedBlueprints()); recipes = java.util.Set.copyOf(data.getLearnedRecipes());
            discovered = java.util.Set.copyOf(data.getDiscoveredBlueprints());
            ordinary = java.util.Map.copyOf(data.getArchivedBlueprintFragments()); criteria = java.util.Map.copyOf(data.getProgressionCriteria());
            analysis = java.util.Map.copyOf(data.getArchivedAnalysisFragments());
            ledger = data.getResearchPointAwardLedger().serializeNBT().copy(); history = List.copyOf(data.getRecentUnlockBatches());
        }
        private boolean progressionMatchesResult(IPlayerRecipeData data, String target) {
            var expected = new java.util.HashSet<>(discovered); expected.add(target);
            return expected.equals(data.getDiscoveredBlueprints()) && learned.equals(data.getLearnedBlueprints())
                    && recipes.equals(data.getLearnedRecipes()) && ordinary.equals(data.getArchivedBlueprintFragments())
                    && criteria.equals(data.getProgressionCriteria()) && analysis.equals(data.getArchivedAnalysisFragments())
                    && ledger.equals(data.getResearchPointAwardLedger().serializeNBT()) && history.equals(data.getRecentUnlockBatches());
        }
        public Evaluation evaluation() { return evaluation; }
        public AnalyzerPolicyQuote quote() { return quote; }
        private boolean matches(IPlayerRecipeData data, WorkstationTransaction station) {
            return currency.equals(station.currencyPolicy()) && current.getAsBoolean() && progression.equals(data.serializeNBT())
                    && ItemStack.matches(input, station.physicalInput()) && ItemStack.matches(output, station.outputStack())
                    && stacksMatch(inventory, station.inventoryStacks());
        }
    }

    /** Failed results never disclose the selected outcome or its quantity. */
    public record AttemptResult(Result transaction, Optional<AnalyzerOutcomeTable.Result> outcome) {
        public AttemptResult {
            java.util.Objects.requireNonNull(transaction); java.util.Objects.requireNonNull(outcome);
            if (transaction.successful() != outcome.isPresent()) throw new IllegalArgumentException("outcome requires committed analysis");
        }
    }

    public static AttemptResult commit(Prepared prepared, IPlayerRecipeData data, WorkstationTransaction station,
                                       LongUnaryOperator random) {
        if (prepared == null || data == null || station == null || random == null) {
            return failure(Status.INVALID_PLAYER, prepared, data);
        }
        if (prepared.used) return failure(Status.STALE_INPUT, prepared, data);
        prepared.used = true;
        var e = prepared.evaluation;
        if (!e.ready()) return failure(e.status(), prepared, data);
        if (InsertedMagazineGuard.hasInsertedMagazine(station.physicalInput()))
            return failure(Status.MAGAZINE_INSERTED, prepared, data);
        if (prepared.quote.status() != AnalyzerPolicyQuote.Status.CONFIGURED
                || !prepared.quote.blueprintId().equals(e.blueprintId().orElse(null))
                || !prepared.quote.attempt().equals(e.base().reversePolicy())) return failure(Status.STALE_POLICY, prepared, data);
        ItemStack blueprint = ItemStack.EMPTY, fragments = ItemStack.EMPTY;
        ResearchIngredientPlanner.Plan materials;
        var target = e.blueprintId().orElseThrow();
        var attempt = prepared.quote.attempt().orElseThrow();
        var outcomes = prepared.quote.outcomes().orElseThrow();
        try {
            if (!prepared.matches(data, station) || !prepared.output.isEmpty()) return failure(Status.STALE_INPUT, prepared, data);
            materials = ResearchCurrencyPayments.plan(prepared.currency, prepared.inventory, data, e.cost().points(), e.cost()).orElse(null);
            if (materials == null || prepared.input.getCount() < e.requiredInputCount()
                    || ResearchCurrencyPayments.balance(prepared.currency, prepared.inventory, data) < e.cost().points()) return failure(Status.STALE_INPUT, prepared, data);
            var provenance = BlueprintProvenance.reverseEngineered(attempt.outputRecyclable(), attempt.physicalBlueprintLearningMode());
            if (outcomes.blueprintWeight() > 0) {
                blueprint = station.createOutput(target, provenance);
                if (blueprint == null || blueprint.isEmpty() || blueprint.getCount() != 1 || blueprint.getMaxStackSize() < 1
                        || BlueprintItem.getBlueprintId(blueprint).filter(target::equals).isEmpty()
                        || BlueprintItem.getProvenance(blueprint).filter(provenance::equals).isEmpty()) {
                    return failure(Status.TRANSACTION_FAILED, prepared, data);
                }
                blueprint = blueprint.copy();
            }
            if (outcomes.fragmentWeight() > 0) {
                var reward = AnalysisRewardClass.of(attempt.outputRecyclable(), attempt.physicalBlueprintLearningMode());
                fragments = station.createFragments(target, reward, outcomes.maximumFragments());
                if (prepared.quote.fragmentThreshold() < 1 || fragments == null || fragments.isEmpty()
                        || fragments.getCount() != outcomes.maximumFragments() || fragments.getMaxStackSize() < outcomes.maximumFragments()
                        || BlueprintFragmentItem.identity(fragments).filter(id -> id.target().equals(target)
                            && id.analysisClass().filter(reward::equals).isPresent()).isEmpty()) {
                    return failure(Status.TRANSACTION_FAILED, prepared, data);
                }
                fragments = fragments.copy();
            }
            // Output builders, policy hooks, and inventory access must not silently change the quote.
            if (!prepared.matches(data, station)) return failure(Status.STALE_INPUT, prepared, data);
        } catch (RuntimeException error) { return failure(Status.TRANSACTION_FAILED, prepared, data); }

        boolean discovered = !data.hasDiscoveredBlueprint(target.toString());
        int points = data.getResearchPoints();
        try {
            var selected = outcomes.select(random);
            // No candidate is exposed until every write and postcondition has succeeded.
            ItemStack output = switch (selected.kind()) {
                case BLUEPRINT -> blueprint;
                case FRAGMENTS -> { fragments.setCount(selected.count()); yield fragments; }
                case NO_RESULT -> ItemStack.EMPTY;
            };
            if (!prepared.matches(data, station)) throw new IllegalStateException("analysis state changed during selection");
            if (!ResearchCurrencyPayments.debitBank(prepared.currency, data, e.cost().points())) throw new IllegalStateException("RP write failed");
            if (!station.consumeMaterials(materials, prepared.inventory)
                    || !station.consumePhysical(prepared.input, e.requiredInputCount())
                    || (!output.isEmpty() && !station.placeOutput(output.copy(), prepared.output))) throw new IllegalStateException("inventory write failed");
            if (discovered && !data.discoverBlueprint(target.toString())) throw new IllegalStateException("discovery write failed");
            var expectedInput = prepared.input.copy(); expectedInput.shrink(e.requiredInputCount());
            if (!prepared.current.getAsBoolean() || data.getResearchPoints() != points - (ResearchCurrencyPayments.physical(prepared.currency) ? 0 : e.cost().points())
                    || !prepared.currency.equals(station.currencyPolicy())
                    || !prepared.progressionMatchesResult(data, target.toString())
                    || !ItemStack.matches(expectedInput, station.physicalInput()) || !ItemStack.matches(output, station.outputStack())
                    || !stacksMatch(materials.applyToCopies(prepared.inventory), station.inventoryStacks())) {
                throw new IllegalStateException("analysis postcondition failed");
            }
            return new AttemptResult(new Result(Status.SUCCESS, Optional.of(target), e.requiredInputCount(), e.cost().points(),
                    ResearchCurrencyPayments.balance(prepared.currency, station.inventoryStacks(), data), discovered), Optional.of(selected));
        } catch (RuntimeException error) {
            boolean restoredInventory = false, restoredData = false;
            try {
                restoredInventory = station.restore(prepared.input.copy(), prepared.inventory.stream().map(ItemStack::copy).toList(), prepared.output.copy())
                        && ItemStack.matches(prepared.input, station.physicalInput()) && ItemStack.matches(prepared.output, station.outputStack())
                        && stacksMatch(prepared.inventory, station.inventoryStacks());
            } catch (RuntimeException ignored) { }
            try { data.deserializeNBT(prepared.progression.copy()); restoredData = prepared.progression.equals(data.serializeNBT()); }
            catch (RuntimeException ignored) { }
            return failure(restoredInventory && restoredData ? Status.TRANSACTION_FAILED : Status.ROLLBACK_FAILED, prepared, data);
        }
    }

    private static boolean stacksMatch(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) if (!ItemStack.matches(left.get(i), right.get(i))) return false;
        return true;
    }
    private static AttemptResult failure(Status status, Prepared prepared, IPlayerRecipeData data) {
        return new AttemptResult(Result.failure(status, prepared == null ? Optional.empty() : prepared.evaluation.blueprintId(),
                data == null ? 0 : data.getResearchPoints()), Optional.empty());
    }
}
