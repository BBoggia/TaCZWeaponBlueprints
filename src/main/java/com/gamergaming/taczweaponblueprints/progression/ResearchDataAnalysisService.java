package com.gamergaming.taczweaponblueprints.progression;

import java.util.*;
import java.util.function.*;
import com.gamergaming.taczweaponblueprints.capabilities.*;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService.WorkstationTransaction;
import com.gamergaming.taczweaponblueprints.progression.ResearchDataRedemptionService.Status;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.progression.currency.*;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** One-item workstation analysis. Neither previews nor rejected requests sample a reward. */
public final class ResearchDataAnalysisService {
    private ResearchDataAnalysisService() { }
    private static volatile ResearchDataPoolResolver.Context cached;
    public static ResearchDataPoolResolver.Context context() {
        var source = BlueprintWeaponAnalysisService.context();
        var value = cached;
        if (value == null || value.source() != source) cached = value = new ResearchDataPoolResolver.Context(source);
        return value;
    }
    public static Optional<ResourceLocation> owner(ItemStack input) {
        if (input == null || input.isEmpty()) return Optional.empty();
        var source = context().source();
        return source.research().researchData().select(ForgeRegistries.ITEMS.getKey(input.getItem()),
                ModConfigs.BLUEPRINT.progressionSnapshot().activeProfileId(),
                (item, tag) -> input.is(TagKey.create(Registries.ITEM, tag))).selected();
    }
    /** Legacy mode preserves old turn-ins. In analysis mode a selected disabled owner never falls through. */
    public static boolean legacyAllowed(ItemStack input) {
        return legacyAllowed(input == null || input.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(input.getItem()));
    }
    public static boolean legacyAllowed(ResourceLocation item) {
        var settings = ModConfigs.BLUEPRINT.dataSettings();
        if (settings.behavior() == ResearchDataPolicy.Behavior.LEGACY_CURRENCY) return true;
        if (settings.behavior() == ResearchDataPolicy.Behavior.DISABLED || item == null) return false;
        var source = context().source();
        var selected = source.research().researchData().select(item, ModConfigs.BLUEPRINT.progressionSnapshot().activeProfileId(),
                (id, tag) -> ForgeRegistries.ITEMS.containsKey(id) && ForgeRegistries.ITEMS.getValue(id).builtInRegistryHolder()
                        .is(TagKey.create(Registries.ITEM, tag))).selected();
        return settings.behaviorFor(selected.map(source.research().researchData().policies()::get)) == ResearchDataPolicy.Behavior.LEGACY_CURRENCY;
    }
    public static Optional<Prepared> prepare(ServerPlayer player, WorkstationTransaction station) {
        if (player == null || station == null || !player.isAlive()) return Optional.empty();
        var settings = ModConfigs.BLUEPRINT.dataSettings();
        if (settings.behavior() == ResearchDataPolicy.Behavior.LEGACY_CURRENCY) return Optional.empty();
        var owner = owner(station.physicalInput());
        if (owner.isEmpty() || legacyAllowed(station.physicalInput())) return Optional.empty();
        var data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve();
        if (data.isEmpty()) return Optional.empty();
        var context = context(); var config = ModConfigs.BLUEPRINT.progressionSnapshot();
        var access = ModConfigs.BLUEPRINT.accessSnapshot(); var tier = station.analyzerTier();
        Supplier<ResearchDataPoolResolver.Pool> pool = () -> ResearchDataPoolResolver.evaluate(context, owner.orElseThrow(),
                config.activeProfileId(), tier.orElse(AnalyzerTier.TIER_1), settings.defaults(),
                ResearchDataPoolResolver.PlayerView.from(data.orElseThrow()),
                id -> config.blueprintsEnabled() && !ModConfigs.BLUEPRINT.isItemBlacklisted(id.toString())
                        && !BlueprintProgressionAccess.isProgressionExempt(id));
        BooleanSupplier current = () -> player.isAlive() && station.stationValid() && tier.isPresent()
                && tier.equals(station.analyzerTier()) && context() == context
                && settings.equals(ModConfigs.BLUEPRINT.dataSettings())
                && config.equals(ModConfigs.BLUEPRINT.progressionSnapshot()) && access.equals(ModConfigs.BLUEPRINT.accessSnapshot());
        Supplier<ResearchDataPoolResolver.Pool> selectedPool = () -> {
            var value = pool.get();
            return settings.behavior() == ResearchDataPolicy.Behavior.DISABLED
                    ? new ResearchDataPoolResolver.Pool(ResearchDataPoolResolver.Status.DISABLED, value.definition(), value.policy(), List.of(), Map.of(), 0, 0, 0)
                    : value;
        };
        return Optional.of(new Prepared(selectedPool.get(), data.orElseThrow(), station, current, selectedPool));
    }
    public static com.gamergaming.taczweaponblueprints.menu.ResearchDataPresentation presentation(ServerPlayer player, Prepared prepared) {
        var policy=prepared.pool().policy(); var settings=ModConfigs.BLUEPRINT.dataSettings();
        var data=player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve().orElseThrow();
        var config=ModConfigs.BLUEPRINT.progressionSnapshot(); var source=context();
        var rows=new ArrayList<com.gamergaming.taczweaponblueprints.menu.ResearchDataPresentation.Station>(3);
        int configured=0;
        for (var entry:policy.pool().tierWeights().entrySet()) if(entry.getValue()>0) configured |= 1 << (entry.getKey().level()-1);
        for (var tier:AnalyzerTier.values()) {
            var pool=ResearchDataPoolResolver.evaluate(source,prepared.pool().definition(),config.activeProfileId(),tier,settings.defaults(),
                    ResearchDataPoolResolver.PlayerView.from(data),id->config.blueprintsEnabled()&&!ModConfigs.BLUEPRINT.isItemBlacklisted(id.toString())&&!BlueprintProgressionAccess.isProgressionExempt(id));
            if(settings.behavior()==ResearchDataPolicy.Behavior.DISABLED) pool=new ResearchDataPoolResolver.Pool(ResearchDataPoolResolver.Status.DISABLED,pool.definition(),policy,List.of(),Map.of(),0,0,0);
            int mask=0; boolean full=false,fragments=false;
            for(var group:pool.tiers()) {mask |= 1 << (group.tier().level()-1);for(var candidate:group.candidates()) { full |= candidate.outcomes().blueprintWeight()>0; fragments |= candidate.outcomes().fragmentWeight()>0; }}
            rows.add(new com.gamergaming.taczweaponblueprints.menu.ResearchDataPresentation.Station(tier.level(),mask,
                    !full?0:!fragments?1:Math.max(0,Math.min(1,pool.blueprintProbability())),full,fragments,pool.minimumFragments(),pool.maximumFragments()));
        }
        return new com.gamergaming.taczweaponblueprints.menu.ResearchDataPresentation(policy.minimumAnalyzerTier().level(),configured,
                policy.allowLearned().orElse(settings.defaults().allowLearned()),policy.allowUndiscovered().orElse(settings.defaults().allowUndiscovered()),policy.progressBonus()>0,policy.rewardClass(),rows);
    }

    public record Result(Status status, Optional<ResearchDataPoolResolver.Outcome> outcome) {
        public Result { Objects.requireNonNull(status); Objects.requireNonNull(outcome);
            if ((status == Status.SUCCESS) != outcome.isPresent()) throw new IllegalArgumentException("only committed Data reveals a result"); }
        public boolean successful() { return status == Status.SUCCESS; }
    }
    public static Result analyze(ServerPlayer player, WorkstationTransaction station) {
        var prepared = prepare(player, station);
        if (prepared.isEmpty()) return failure(Status.NO_MATCH);
        var data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve().orElseThrow();
        // Transition publication occurs only after the entire workstation commit.
        var before = Set.copyOf(data.getDiscoveredBlueprints());
        var result = commit(prepared.orElseThrow(), data, station, bound -> java.util.concurrent.ThreadLocalRandom.current().nextLong(bound));
        if (result.successful()) {
            var target = result.outcome().orElseThrow().target(); boolean discoveredBefore = before.contains(target.toString());
            try { ResearchPointAwardDispatcher.blueprintTransitions(player, data, target, !discoveredBefore, false); }
            catch (RuntimeException failure) { com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.LOGGER.error("Data committed but discovery awards failed", failure); }
            finally { BlueprintProgressionSyncScheduler.markKnowledgeDirty(player); }
            try { NetworkHandler.syncPlayerRecipeData(player); } catch (RuntimeException ignored) { }
        }
        return result;
    }
    public static final class Prepared {
        private final ResearchDataPoolResolver.Pool pool;
        private final IPlayerRecipeData dataOwner;
        private final WorkstationTransaction stationOwner;
        private final ResearchCurrencyPolicy currency;
        private final ItemStack input, output;
        private final List<ItemStack> inventory;
        private final CompoundTag progression;
        private final BooleanSupplier current;
        private final Supplier<ResearchDataPoolResolver.Pool> reevaluate;
        private boolean used;
        public Prepared(ResearchDataPoolResolver.Pool pool, IPlayerRecipeData data, WorkstationTransaction station,
                        BooleanSupplier current, Supplier<ResearchDataPoolResolver.Pool> reevaluate) {
            this.pool = Objects.requireNonNull(pool); this.dataOwner = data; this.stationOwner = station;
            this.currency = station.currencyPolicy(); this.current = current; this.reevaluate = reevaluate;
            input = station.physicalInput().copy(); output = station.outputStack().copy();
            inventory = station.inventoryStacks().stream().map(ItemStack::copy).toList(); progression = data.serializeNBT().copy();
        }
        public ResearchDataPoolResolver.Pool pool() { return pool; }
        public Status status() {
            if (!pool.ready()) return switch (pool.status()) {
                case ALL_LEARNED -> Status.ALL_LEARNED;
                case EMPTY_POOL -> Status.EMPTY_POOL;
                case STATION_TIER_REQUIRED -> Status.STATION_TIER_REQUIRED;
                case STALE_PROGRESSION -> Status.STALE_INVENTORY;
                default -> Status.DATA_DISABLED;
            };
            if (input.isEmpty() || input.getCount() > input.getMaxStackSize()) return Status.NO_MATCH;
            if (!stationOwner.stationValid() || stationOwner.analyzerTier().isEmpty()) return Status.STATION_REQUIRED;
            if (!output.isEmpty()) return Status.OUTPUT_OCCUPIED;
            if (ResearchCurrencyPayments.balance(currency, inventory, dataOwner) < pool.policy().cost().points()) return Status.POINTS_REQUIRED;
            if (ResearchCurrencyPayments.plan(currency, inventory, dataOwner, pool.policy().cost().points(), pool.policy().cost()).isEmpty()) return Status.INGREDIENTS_REQUIRED;
            if (dataOwner.getDiscoveredBlueprints().size() >= PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                    && pool.tiers().stream().flatMap(t -> t.candidates().stream()).anyMatch(c -> !dataOwner.hasDiscoveredBlueprint(c.target().toString())))
                return Status.PROGRESSION_CAPACITY_EXHAUSTED;
            return Status.SUCCESS;
        }
        private boolean matches(IPlayerRecipeData data, WorkstationTransaction station) {
            return data == dataOwner && station == stationOwner && current.getAsBoolean() && currency.equals(station.currencyPolicy())
                    && progression.equals(data.serializeNBT()) && ItemStack.matches(input, station.physicalInput())
                    && ItemStack.matches(output, station.outputStack()) && stacksMatch(inventory, station.inventoryStacks());
        }
    }
    private record Outputs(ItemStack blueprint, ItemStack fragments) { }
    public static Result commit(Prepared p, IPlayerRecipeData data, WorkstationTransaction station, LongUnaryOperator random) {
        if (p == null || data == null || station == null || random == null) return failure(Status.INVALID_PLAYER);
        if (p.used) return failure(Status.STALE_INVENTORY);
        p.used = true;
        var outputs = new HashMap<ResourceLocation, Outputs>();
        ResearchIngredientPlanner.Plan payment;
        try {
            if (!p.matches(data, station)) return failure(Status.STALE_INVENTORY);
            var status = p.status(); if (status != Status.SUCCESS) return failure(status);
            var supported = new PlayerRecipeData(); supported.deserializeNBT(p.progression.copy());
            if (!p.progression.equals(supported.serializeNBT())) return failure(Status.TRANSACTION_FAILED);
            payment = ResearchCurrencyPayments.plan(p.currency, p.inventory, data, p.pool.policy().cost().points(), p.pool.policy().cost()).orElseThrow();
            // Reserve all possible outputs, including target-specific builders, before any random draw.
            var reward = p.pool.policy().rewardClass(); var provenance = reward.provenance(BlueprintProvenance.Source.REVERSE_ENGINEERING);
            for (var tier : p.pool.tiers()) for (var c : tier.candidates()) {
                ItemStack blueprint = ItemStack.EMPTY, fragments = ItemStack.EMPTY;
                if (c.outcomes().blueprintWeight() > 0) {
                    blueprint = station.createOutput(c.target(), provenance);
                    if (blueprint == null || blueprint.isEmpty() || blueprint.getCount() != 1 || blueprint.getMaxStackSize() < 1
                            || BlueprintItem.getBlueprintId(blueprint).filter(c.target()::equals).isEmpty()
                            || BlueprintItem.getProvenance(blueprint).filter(provenance::equals).isEmpty()) return failure(Status.TRANSACTION_FAILED);
                    blueprint = blueprint.copy();
                }
                if (c.outcomes().fragmentWeight() > 0) {
                    fragments = station.createFragments(c.target(), reward, c.outcomes().maximumFragments());
                    if (fragments == null || fragments.isEmpty() || fragments.getCount() != c.outcomes().maximumFragments()
                            || fragments.getCount() > fragments.getMaxStackSize()
                            || BlueprintFragmentItem.identity(fragments).filter(id -> id.target().equals(c.target())
                                    && id.analysisClass().filter(reward::equals).isPresent()).isEmpty()) return failure(Status.TRANSACTION_FAILED);
                    fragments = fragments.copy();
                }
                outputs.put(c.target(), new Outputs(blueprint, fragments));
            }
            if (!p.matches(data, station) || !p.pool.equals(p.reevaluate.get()) || !p.matches(data, station)) return failure(Status.STALE_INVENTORY);
        } catch (RuntimeException failure) { return failure(Status.TRANSACTION_FAILED); }
        // A faulty random source is outside the write phase: don't restore over its unrelated changes.
        ResearchDataPoolResolver.Outcome selected;
        try { selected = p.pool.select(random); }
        catch (RuntimeException failure) { return failure(Status.TRANSACTION_FAILED); }
        try {
            if (!p.matches(data, station) || !p.pool.equals(p.reevaluate.get()) || !p.matches(data, station)) return failure(Status.STALE_INVENTORY);
        } catch (RuntimeException failure) { return failure(Status.TRANSACTION_FAILED); }
        try {
            var templates = outputs.get(selected.target());
            var output = (selected.kind() == AnalyzerOutcomeTable.Kind.BLUEPRINT ? templates.blueprint() : templates.fragments()).copy();
            output.setCount(selected.count());
            if (output.isEmpty()) throw new IllegalStateException("Data must produce an item");
            if (!ResearchCurrencyPayments.debitBank(p.currency, data, p.pool.policy().cost().points())
                    || !station.consumeMaterials(payment, p.inventory) || !station.consumePhysical(p.input, 1)
                    || !station.placeOutput(output.copy(), p.output)) throw new IllegalStateException("Data write failed");
            var target = selected.target().toString();
            if (!data.hasDiscoveredBlueprint(target) && !data.discoverBlueprint(target)) throw new IllegalStateException("Data discovery failed");
            var expected = new PlayerRecipeData(); expected.deserializeNBT(p.progression.copy());
            ResearchCurrencyPayments.debitBank(p.currency, expected, p.pool.policy().cost().points());
            expected.discoverBlueprint(target);
            var remaining = p.input.copy(); remaining.shrink(1);
            // Recheck publications and the bound station after all writes.
            if (!p.current.getAsBoolean() || !p.currency.equals(station.currencyPolicy()) || !station.stationValid()
                    || !expected.serializeNBT().equals(data.serializeNBT()) || !ItemStack.matches(remaining, station.physicalInput())
                    || !ItemStack.matches(output, station.outputStack()) || !stacksMatch(payment.applyToCopies(p.inventory), station.inventoryStacks()))
                throw new IllegalStateException("Data postcondition failed");
            return new Result(Status.SUCCESS, Optional.of(selected));
        } catch (RuntimeException failure) {
            boolean inventoryRestored = false, progressionRestored = false;
            try { inventoryRestored = station.restore(p.input.copy(), p.inventory.stream().map(ItemStack::copy).toList(), p.output.copy())
                    && ItemStack.matches(p.input, station.physicalInput()) && ItemStack.matches(p.output, station.outputStack())
                    && stacksMatch(p.inventory, station.inventoryStacks()); } catch (RuntimeException ignored) { }
            try { data.deserializeNBT(p.progression.copy()); progressionRestored = p.progression.equals(data.serializeNBT()); } catch (RuntimeException ignored) { }
            return failure(inventoryRestored && progressionRestored ? Status.TRANSACTION_FAILED : Status.ROLLBACK_FAILED);
        }
    }
    private static boolean stacksMatch(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        return true;
    }
    private static Result failure(Status status) { return new Result(status, Optional.empty()); }
}
