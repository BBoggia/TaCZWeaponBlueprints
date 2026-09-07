package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import com.gamergaming.taczweaponblueprints.capabilities.*;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.progression.currency.*;
import com.gamergaming.taczweaponblueprints.progression.ResearchDataRedemptionService.Status;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.gamergaming.taczweaponblueprints.testing.AnalysisTestItems;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class ResearchDataAnalysisServiceTest {
    static final ResourceLocation TARGET = new ResourceLocation("test:weapon"), OTHER = new ResourceLocation("test:other");
    @BeforeAll static void bootstrap() { AnalysisTestItems.initialize(); }
    static ResearchDataPolicy policy(AnalysisRewardClass reward) {
        try (var in = ResearchDataAnalysisServiceTest.class.getResourceAsStream("/research-data-policy/report.json")) {
            var json = JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            json.addProperty("reward_class", reward.serializedName());
            json.add("cost", JsonParser.parseString("{\"points\":3,\"ingredients\":[{\"items\":[\"minecraft:iron_ingot\"],\"count\":2}]}"));
            return ResearchDataPolicy.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, ignored -> {});
        } catch (Exception failure) { throw new AssertionError(failure); }
    }
    static ResearchDataPoolResolver.Pool pool(AnalysisRewardClass reward, AnalyzerOutcomeTable table) {
        var candidates = List.of(new ResearchDataPoolResolver.Candidate(TARGET, AnalyzerTier.TIER_1, 1, table, Optional.empty()),
                new ResearchDataPoolResolver.Candidate(OTHER, AnalyzerTier.TIER_1, 1, table, Optional.empty()));
        return new ResearchDataPoolResolver.Pool(ResearchDataPoolResolver.Status.READY, new ResourceLocation("test:data"), policy(reward),
                List.of(new ResearchDataPoolResolver.TierPool(AnalyzerTier.TIER_1, 1, candidates, 2)), Map.of(), table.blueprintProbability(), table.minimumFragments(), table.maximumFragments());
    }
    static ResearchDataPoolResolver.Pool mixed() { return pool(AnalysisRewardClass.PROTECTED_PREREQUISITES, new AnalyzerOutcomeTable(1,1,0,1,64)); }
    static BlueprintWeaponAnalysisServiceTest.FaultData data() { var d = new BlueprintWeaponAnalysisServiceTest.FaultData(); d.setResearchPoints(137); return d; }
    static ResearchDataAnalysisService.Prepared prepare(Tx tx, IPlayerRecipeData data, ResearchDataPoolResolver.Pool pool) {
        return new ResearchDataAnalysisService.Prepared(pool, data, tx, () -> tx.current, () -> pool);
    }
    @ParameterizedTest @CsvSource({"0,0,0,BLUEPRINT,1", "1,0,0,BLUEPRINT,1", "0,1,0,FRAGMENTS,1", "1,1,63,FRAGMENTS,64"})
    void forcedTargetsAndQuantities(long target, long outcome, long quantity, AnalyzerOutcomeTable.Kind kind, int count) {
        var tx = new Tx(); var data = data(); var p = prepare(tx, data, mixed()); var draws = new AtomicInteger();
        var result = ResearchDataAnalysisService.commit(p, data, tx, bound -> switch(draws.getAndIncrement()) { case 0 -> target; case 1 -> outcome; default -> quantity; });
        assertTrue(result.successful(), result.toString()); var selected = result.outcome().orElseThrow();
        assertEquals(target == 0 ? TARGET : OTHER, selected.target()); assertEquals(kind, selected.kind()); assertEquals(count, selected.count());
        assertEquals(1, tx.input.getCount()); assertEquals(6, tx.inventory.get(0).getCount()); assertEquals(134, data.getResearchPoints());
        assertEquals(count, tx.output.getCount()); assertTrue(data.hasDiscoveredBlueprint(selected.target().toString()));
        assertTrue(data.getLearnedBlueprints().isEmpty()); assertTrue(data.getArchivedAnalysisFragments().isEmpty());
        assertEquals(Status.STALE_INVENTORY, ResearchDataAnalysisService.commit(p, data, tx, b -> fail("duplicate rolled")).status());
    }
    @ParameterizedTest @EnumSource(AnalysisRewardClass.class)
    void eachPermissionClassSurvivesBothPhysicalOutputs(AnalysisRewardClass reward) {
        for (boolean fragment : List.of(false, true)) {
            var tx = new Tx(); var data = data(); data.addBlueprint(TARGET.toString());
            var p = prepare(tx, data, pool(reward, new AnalyzerOutcomeTable(fragment ? 0 : 1, fragment ? 1 : 0,0,fragment ? 2 : 0,fragment ? 2 : 0)));
            var result = ResearchDataAnalysisService.commit(p, data, tx, bound -> 0);
            assertTrue(result.successful());
            if (fragment) assertEquals(reward, BlueprintFragmentItem.identity(tx.output).orElseThrow().analysisClass().orElseThrow());
            else assertEquals(reward.provenance(BlueprintProvenance.Source.REVERSE_ENGINEERING), BlueprintItem.getProvenance(tx.output).orElseThrow());
            assertTrue(data.hasBlueprint(TARGET.toString())); assertTrue(data.getArchivedAnalysisFragments().isEmpty());
        }
    }
    @ParameterizedTest @ValueSource(strings={"output", "input", "inventory", "knowledge", "currency", "station", "policy"})
    void stalePreparedRequestsNeverDraw(String changed) {
        var tx = new Tx(); var data = data(); var p = prepare(tx, data, mixed());
        switch(changed) {
            case "output" -> tx.output = new ItemStack(Items.PAPER);
            case "input" -> tx.input.shrink(1);
            case "inventory" -> tx.inventory.get(0).shrink(1);
            case "knowledge" -> data.addBlueprint(TARGET.toString());
            case "currency" -> tx.currency = ResearchCurrencyPolicy.physical(new ResourceLocation("minecraft:iron_ingot"),2);
            default -> tx.current = false;
        }
        assertEquals(Status.STALE_INVENTORY, ResearchDataAnalysisService.commit(p,data,tx,b -> fail("stale request rolled")).status());
    }
    @ParameterizedTest @ValueSource(strings={"materials_false","physical_throw","output_false","output_nowrite","lease_after_write","spend_throw","spend_false","discover_throw","discover_false","unexpected_learn"})
    void failedWritesRestoreInputBillOutputAndProgression(String fault) {
        for (boolean physical : List.of(false, true)) {
            if (physical && fault.startsWith("spend")) continue;
            var tx = new Tx(); var data = data(); tx.fault = fault; data.fault = fault;
            if (physical) tx.currency = ResearchCurrencyPolicy.physical(new ResourceLocation("minecraft:iron_ingot"),1);
            var before = data.serializeNBT().copy();
            var result = ResearchDataAnalysisService.commit(prepare(tx,data,mixed()),data,tx,b -> 0);
            assertEquals(Status.TRANSACTION_FAILED,result.status(),fault); assertTrue(result.outcome().isEmpty());
            assertEquals(before,data.serializeNBT()); assertEquals(2,tx.input.getCount()); assertEquals(8,tx.inventory.get(0).getCount()); assertTrue(tx.output.isEmpty());
        }
    }
    @Test void physicalCostAndMaterialOverlapConsumeFiveUnitsAndKeepDormantBank() {
        var tx=new Tx(); var data=data(); tx.currency=ResearchCurrencyPolicy.physical(new ResourceLocation("minecraft:iron_ingot"),1);
        assertTrue(ResearchDataAnalysisService.commit(prepare(tx,data,mixed()),data,tx,b -> 0).successful());
        assertEquals(3,tx.inventory.get(0).getCount()); assertEquals(137,data.getResearchPoints());
    }
    @ParameterizedTest @ValueSource(strings={"occupied","insufficient_materials","insufficient_currency","builder","capacity","empty_pool","all_learned"})
    void completePreflightRejectsWithoutDrawingOrConsuming(String reason) {
        var tx=new Tx(); var data=data(); var pool=mixed();
        switch(reason) {
            case "occupied" -> tx.output=new ItemStack(Items.PAPER);
            case "insufficient_materials" -> tx.inventory.get(0).setCount(1);
            case "insufficient_currency" -> data.setResearchPoints(0);
            case "builder" -> tx.fault="bad_second_builder";
            case "capacity" -> { for(int i=0;i<PlayerProgressionLimits.MAX_IDS_PER_COLLECTION;i++)data.discoverBlueprint("test:known"+i); }
            default -> pool=new ResearchDataPoolResolver.Pool(reason.equals("empty_pool") ? ResearchDataPoolResolver.Status.EMPTY_POOL : ResearchDataPoolResolver.Status.ALL_LEARNED,
                    pool.definition(),pool.policy(),List.of(),Map.of(),0,0,0);
        }
        var before=data.serializeNBT().copy();
        assertFalse(ResearchDataAnalysisService.commit(prepare(tx,data,pool),data,tx,b -> fail("preflight rolled")).successful());
        assertEquals(before,data.serializeNBT()); assertEquals(2,tx.input.getCount());
    }
    @Test void poolIsReevaluatedAfterOutputBuilders() {
        var tx=new Tx(); var data=data(); var pool=mixed();
        Supplier<ResearchDataPoolResolver.Pool> changed=()->new ResearchDataPoolResolver.Pool(ResearchDataPoolResolver.Status.ALL_LEARNED,pool.definition(),pool.policy(),List.of(),Map.of(),0,0,0);
        var p=new ResearchDataAnalysisService.Prepared(pool,data,tx,()->true,changed);
        assertEquals(Status.STALE_INVENTORY,ResearchDataAnalysisService.commit(p,data,tx,b->fail("changed pool rolled")).status());
    }
    @Test void throwingRollbackReportsFailureButStillRestoresPlayerData() {
        var tx=new Tx(); var data=data(); tx.fault="rollback_throw"; var before=data.serializeNBT().copy();
        assertEquals(Status.ROLLBACK_FAILED,ResearchDataAnalysisService.commit(prepare(tx,data,mixed()),data,tx,b->0).status());
        assertEquals(before,data.serializeNBT());
    }
    @Test void invalidRandomAndChangedStateDuringSamplingNeverWriteOrRestoreForeignState() {
        var tx = new Tx(); var data = data();
        var result = ResearchDataAnalysisService.commit(prepare(tx, data, mixed()), data, tx, bound -> bound);
        assertEquals(Status.TRANSACTION_FAILED, result.status()); assertEquals(2, tx.input.getCount());
        result = ResearchDataAnalysisService.commit(prepare(tx, data, mixed()), data, tx, bound -> { data.addBlueprint(OTHER.toString()); return 0; });
        assertEquals(Status.STALE_INVENTORY, result.status()); assertTrue(data.hasBlueprint(OTHER.toString()));
        assertEquals(2, tx.input.getCount()); assertTrue(tx.output.isEmpty());
    }
    @Test void preparedTransactionCannotBeAppliedToAnotherWorkstationOrPlayer() {
        var tx = new Tx(); var data = data(); var prepared = prepare(tx, data, mixed());
        assertEquals(Status.STALE_INVENTORY, ResearchDataAnalysisService.commit(prepared, data, new Tx(), b -> fail("foreign station rolled")).status());
        prepared = prepare(tx, data, mixed());
        assertEquals(Status.STALE_INVENTORY, ResearchDataAnalysisService.commit(prepared, data(), tx, b -> fail("foreign player rolled")).status());
    }
    @ParameterizedTest @EnumSource(ResearchDataPolicy.Behavior.class)
    void globalModeAndInputOwnershipAreIndependentOfCurrency(ResearchDataPolicy.Behavior mode) {
        var settings = new ResearchDataSettings(mode, ResearchDataPoolResolver.Defaults.STANDARD);
        assertEquals(mode == ResearchDataPolicy.Behavior.ANALYSIS ? ResearchDataPolicy.Behavior.DISABLED : mode, settings.behaviorFor(Optional.empty()));
        var owner = policy(AnalysisRewardClass.PROTECTED_PREREQUISITES);
        assertEquals(mode, settings.behaviorFor(Optional.of(owner)));
        var disabled = new ResearchDataPolicy(owner.format(),false,owner.priority(),owner.input(),owner.profile(),owner.behavior(),owner.minimumAnalyzerTier(),
                owner.cost(),owner.pool(),owner.allowLearned(),owner.allowUndiscovered(),owner.progressBonus(),owner.rewardClass(),owner.reconstruction(),owner.rows());
        assertEquals(mode == ResearchDataPolicy.Behavior.ANALYSIS ? ResearchDataPolicy.Behavior.DISABLED : mode, settings.behaviorFor(Optional.of(disabled)));
    }
    static final class Tx implements BlueprintReverseEngineeringService.WorkstationTransaction {
        ItemStack input=new ItemStack(Items.PAPER,2),output=ItemStack.EMPTY;
        List<ItemStack> inventory=new ArrayList<>(Collections.nCopies(36,ItemStack.EMPTY));
        ResearchCurrencyPolicy currency=ResearchCurrencyPolicy.banked(1000,1);
        boolean current=true; String fault="";
        Tx(){inventory.set(0,new ItemStack(Items.IRON_INGOT,8));}
        public ResearchCurrencyPolicy currencyPolicy(){return currency;}
        public boolean stationValid(){return current;}
        public Optional<AnalyzerTier> analyzerTier(){return current?Optional.of(AnalyzerTier.TIER_1):Optional.empty();}
        public ItemStack physicalInput(){return input;} public ItemStack outputStack(){return output;}
        public List<ItemStack> inventoryStacks(){return inventory.stream().map(ItemStack::copy).toList();}
        public ItemStack createOutput(ResourceLocation id,BlueprintProvenance provenance){
            if(fault.equals("bad_second_builder")&&id.equals(OTHER))return ItemStack.EMPTY;
            var stack = new ItemStack(AnalysisTestItems.blueprint); stack.getOrCreateTag().putString("bpId",id.toString());
            stack.getOrCreateTag().put(BlueprintProvenance.TAG_KEY,provenance.toTag()); return stack;
        }
        public ItemStack createFragments(ResourceLocation id,AnalysisRewardClass reward,int count){var stack = BlueprintFragmentItem.createAnalysis(AnalysisTestItems.fragment,id,reward); stack.setCount(count); return stack;}
        public boolean consumeMaterials(ResearchIngredientPlanner.Plan p,List<ItemStack> expected){inventory=new ArrayList<>(p.applyToCopies(inventory));return !fault.equals("materials_false");}
        public boolean consumePhysical(ItemStack expected,int count){input.shrink(count);if(fault.equals("physical_throw"))throw new IllegalStateException();return true;}
        public boolean placeOutput(ItemStack stack,ItemStack expected){if(!fault.equals("output_nowrite"))output=stack.copy();if(fault.equals("lease_after_write"))current=false;return !fault.equals("output_false")&&!fault.equals("rollback_throw");}
        public boolean restore(ItemStack in,List<ItemStack> items,ItemStack out){if(fault.equals("rollback_throw"))throw new IllegalStateException();input=in.copy();inventory=new ArrayList<>(items);output=out.copy();return true;}
    }
}
