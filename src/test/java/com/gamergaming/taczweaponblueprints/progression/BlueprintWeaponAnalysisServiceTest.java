package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.progression.currency.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.gamergaming.taczweaponblueprints.capabilities.*;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.gamergaming.taczweaponblueprints.testing.AnalysisTestItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class BlueprintWeaponAnalysisServiceTest {
    private static final ResourceLocation TARGET = new ResourceLocation("test:weapon"), PROFILE = new ResourceLocation("test:profile");
    @BeforeAll static void bootstrap() { AnalysisTestItems.initialize(); }
    static BlueprintData weapon() { return new BlueprintData(TARGET.toString(), "test.weapon", "test.tooltip",
            new ResourceLocation("test:weapon_recipe"), null, "rifle", new ResourceLocation("tacz:slot"), BlueprintKind.GUN, 1); }
    static BlueprintReverseEngineeringPolicy policy() {
        return new BlueprintReverseEngineeringPolicy(true, Optional.of(1), new BlueprintResearchCost(3,
                List.of(new BlueprintResearchIngredient(List.of(new ResourceLocation("minecraft:iron_ingot")), Optional.empty(), 2))),
                false, false, PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES, false, false);
    }
    static BlueprintResearchSnapshot snapshot() {
        var profile = new BlueprintResearchProfile(1, true, JournalVisibility.FULL, true, true, false, 1,
                new BlueprintResearchCost(8, List.of()), false, false, true, List.of(), Map.of(), Optional.empty(), policy());
        return BlueprintResearchSnapshot.create(Map.of(), Map.of(PROFILE, profile), Map.of());
    }
    static BlueprintReverseEngineeringService.Evaluation evaluate(Tx tx, PlayerRecipeData data) {
        return BlueprintReverseEngineeringService.evaluate(tx.input, tx.inventory, tx.output.isEmpty(), snapshot(), Map.of(TARGET, weapon()),
                PROFILE, data, id -> false, id -> false, true, 100,
                stack -> new PhysicalItemBlueprintResolver.InspectedIdentity(TARGET, BlueprintKind.GUN, tx.loaded, tx.attached, false), false, policy(), tx.currency);
    }
    static BlueprintWeaponAnalysisService.Prepared prepare(Tx tx, PlayerRecipeData data, AnalyzerOutcomeTable table) {
        return prepare(tx, data, table, AnalyzerTier.TIER_1, AnalyzerTier.TIER_3);
    }
    static BlueprintWeaponAnalysisService.Prepared prepare(Tx tx, PlayerRecipeData data, AnalyzerOutcomeTable table, AnalyzerTier station, AnalyzerTier weapon) {
        var quote = new AnalyzerPolicyQuote(PROFILE, TARGET, station, BlueprintAnalyzerPolicy.Origin.FOUND,
                AnalyzerPolicyQuote.Status.CONFIGURED, Optional.of(weapon), "fixture", false, Optional.empty(),
                BlueprintResearchTarget.MatchSpecificity.NONE, Optional.of(policy()), Optional.of(table), 5, 100);
        return new BlueprintWeaponAnalysisService.Prepared(evaluate(tx, data), quote, data, tx, () -> tx.current);
    }
    static FaultData data() { var data = new FaultData(); data.setResearchPoints(20); return data; }
    static AnalyzerOutcomeTable mixed() { return new AnalyzerOutcomeTable(3,5,2,2,4); }

    @ParameterizedTest @CsvSource({"0,BLUEPRINT,1", "2,BLUEPRINT,1", "3,FRAGMENTS,4", "7,FRAGMENTS,4", "8,NO_RESULT,0", "9,NO_RESULT,0"})
    void selectionBoundariesCommitExactlyOneOutcome(long draw, AnalyzerOutcomeTable.Kind kind, int count) {
        var tx = new Tx(); var data = data(); var quote = prepare(tx, data, mixed()); var draws = new AtomicInteger();
        assertEquals(0, draws.get());
        var result = BlueprintWeaponAnalysisService.commit(quote, data, tx, bound -> { draws.incrementAndGet(); return bound == 10 ? draw : bound - 1; });
        assertTrue(result.transaction().successful()); assertEquals(kind, result.outcome().orElseThrow().kind());
        assertEquals(count, result.outcome().orElseThrow().count());
        assertEquals(1, tx.input.getCount()); assertEquals(2, tx.inventory.get(0).getCount()); assertEquals(17, data.getResearchPoints());
        assertTrue(data.hasDiscoveredBlueprint(TARGET.toString())); assertFalse(data.hasBlueprint(TARGET.toString()));
        assertTrue(data.getArchivedAnalysisFragments().isEmpty()); assertTrue(data.getArchivedBlueprintFragments().isEmpty());
        assertEquals(count, tx.output.getCount());
        if (kind == AnalyzerOutcomeTable.Kind.FRAGMENTS) assertEquals(AnalysisRewardClass.PROTECTED_PREREQUISITES,
                BlueprintFragmentItem.identity(tx.output).orElseThrow().analysisClass().orElseThrow());
        if (kind == AnalyzerOutcomeTable.Kind.BLUEPRINT) assertEquals(BlueprintProvenance.reverseEngineered(false,
                PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES), BlueprintItem.getProvenance(tx.output).orElseThrow());
        assertEquals(BlueprintReverseEngineeringService.Status.STALE_INPUT,
                BlueprintWeaponAnalysisService.commit(quote,data,tx,b -> fail("same quote rerolled")).transaction().status());
    }

    @ParameterizedTest @CsvSource({"1,1", "1,2", "1,3", "2,1", "2,2", "2,3", "3,1", "3,2", "3,3"})
    void everyStationWeaponPairUsesItsQuotedTable(int station, int weapon) {
        var data=data(); var tx=new Tx(); var table=new AnalyzerOutcomeTable(0,1,0,weapon,weapon);
        var prepared=prepare(tx,data,table,AnalyzerTier.fromLevel(station),AnalyzerTier.fromLevel(weapon));
        var result=BlueprintWeaponAnalysisService.commit(prepared,data,tx,b->fail("fixed outcome drew RNG"));
        assertEquals(weapon, result.outcome().orElseThrow().count());
    }

    @ParameterizedTest @ValueSource(strings={"points", "input_nbt", "input_count", "inventory_nbt", "output", "discovery", "learned", "ordinary", "analysis", "criterion", "station_or_policy"})
    void changedQuotedStateRejectsBeforeDrawing(String change) {
        var data=data(); var tx=new Tx(); var prepared=prepare(tx,data,mixed());
        switch(change) {
            case "points" -> data.setResearchPoints(19);
            case "input_nbt" -> tx.input.getOrCreateTag().putString("changed","yes");
            case "input_count" -> tx.input.shrink(1);
            case "inventory_nbt" -> tx.inventory.get(0).getOrCreateTag().putInt("quality",2);
            case "output" -> tx.output=new ItemStack(Items.DIRT);
            case "discovery" -> data.discoverBlueprint("test:other");
            case "learned" -> data.addBlueprint(TARGET.toString());
            case "ordinary" -> data.applyArchivedFragmentMutation(PlayerProgressValueMutation.Request.commit(TARGET.toString(),0,1));
            case "analysis" -> data.applyAnalysisFragmentMutation(new AnalysisFragmentMutation(PlayerProgressValueMutation.Operation.COMMIT,
                    TARGET.toString(),AnalysisFragmentCounts.EMPTY,new AnalysisFragmentCounts(1,0,0,0)));
            case "criterion" -> data.applyProgressionCriterionMutation(PlayerProgressValueMutation.Request.commit("test:criterion",0,1));
            case "station_or_policy" -> tx.current=false;
        }
        var before=data.serializeNBT().copy(); var input=tx.input.copy();
        assertFalse(BlueprintWeaponAnalysisService.commit(prepared,data,tx,b->fail("stale request drew RNG")).transaction().successful());
        assertEquals(before,data.serializeNBT()); assertTrue(ItemStack.matches(input,tx.input));
    }

    @ParameterizedTest @ValueSource(strings={"bad_blueprint", "bad_blueprint_count", "bad_blueprint_protection", "bad_fragments", "bad_fragment_count", "bad_fragment_protection", "builder_throw"})
    void everyPositiveWeightOutputMustPassBeforeAnyDraw(String fault) {
        var data=data(); var tx=new Tx(); tx.fault=fault; var prepared=prepare(tx,data,mixed()); var original=data.serializeNBT();
        assertFalse(BlueprintWeaponAnalysisService.commit(prepared,data,tx,b->fail("invalid output filtered odds")).transaction().successful());
        assertEquals(2,tx.input.getCount()); assertEquals(original,data.serializeNBT()); assertTrue(tx.output.isEmpty());
    }

    @ParameterizedTest @ValueSource(strings={"spend_false", "spend_throw", "materials_false", "physical_throw", "output_false", "output_nowrite", "discover_false", "discover_throw", "unexpected_learn", "lease_after_write"})
    void failuresAfterPartialWritesRestoreEveryOwnedValueAndHideCandidate(String fault) {
        var data=data(); var tx=new Tx(); data.fault=fault; tx.fault=fault;
        data.applyAnalysisFragmentMutation(new AnalysisFragmentMutation(PlayerProgressValueMutation.Operation.COMMIT,"test:other",
                AnalysisFragmentCounts.EMPTY,new AnalysisFragmentCounts(1,2,3,4)));
        var before=data.serializeNBT().copy(); var prepared=prepare(tx,data,mixed());
        var result=BlueprintWeaponAnalysisService.commit(prepared,data,tx,b->0);
        assertEquals(BlueprintReverseEngineeringService.Status.TRANSACTION_FAILED,result.transaction().status(),fault);
        assertTrue(result.outcome().isEmpty()); assertEquals(before,data.serializeNBT());
        assertEquals(2,tx.input.getCount()); assertEquals(4,tx.inventory.get(0).getCount()); assertTrue(tx.output.isEmpty());
    }

    @Test void rollbackFailureIsExplicitWithoutRevealingOutcome() {
        var data=data(); var tx=new Tx(); tx.fault="restore_false"; var prepared=prepare(tx,data,mixed());
        var result=BlueprintWeaponAnalysisService.commit(prepared,data,tx,b->0);
        assertEquals(BlueprintReverseEngineeringService.Status.ROLLBACK_FAILED,result.transaction().status()); assertTrue(result.outcome().isEmpty());
    }
    @Test void noResultStillRequiresAnEmptyOutputAndPaysTheAttemptCost() {
        var data=data(); var tx=new Tx(); tx.output=new ItemStack(Items.DIRT);
        var prepared=prepare(tx,data,new AnalyzerOutcomeTable(0,0,1,0,0));
        assertEquals(BlueprintReverseEngineeringService.Status.OUTPUT_OCCUPIED,
                BlueprintWeaponAnalysisService.commit(prepared,data,tx,b->fail("blocked no-result drew RNG")).transaction().status());
        assertEquals(20,data.getResearchPoints());
    }
    @Test void loadedOrAttachedGunsCannotUseTieredPolicyToBypassPhysicalGates() {
        var data=data(); var tx=new Tx(); tx.loaded=true;
        assertEquals(BlueprintReverseEngineeringService.Status.LOADED_GUN,evaluate(tx,data).status());
        tx.loaded=false; tx.attached=true;
        assertEquals(BlueprintReverseEngineeringService.Status.GUN_HAS_ATTACHMENTS,evaluate(tx,data).status());
    }

    @Test void randomSourceFailureCannotExposeOrRetryACandidate() {
        var data=data(); var tx=new Tx(); var quote=prepare(tx,data,mixed()); var before=data.serializeNBT();
        var result=BlueprintWeaponAnalysisService.commit(quote,data,tx,b->{throw new IllegalStateException("RNG failure");});
        assertFalse(result.transaction().successful()); assertTrue(result.outcome().isEmpty());
        assertEquals(before,data.serializeNBT()); assertEquals(2,tx.input.getCount());
        assertEquals(BlueprintReverseEngineeringService.Status.STALE_INPUT,
                BlueprintWeaponAnalysisService.commit(quote,data,tx,b->fail("reused failed quote")).transaction().status());
    }

    @ParameterizedTest @ValueSource(strings={"", "materials_false", "physical_throw", "output_false", "lease_after_write"})
    void physicalAttemptsPreserveOddsAndCompensateInputCurrencyAndDiscovery(String fault) {
        var data = data(); data.setResearchPoints(137); var tx = new Tx();
        tx.currency = ResearchCurrencyPolicy.physical(new ResourceLocation("minecraft:iron_ingot"), 1);
        tx.inventory.get(0).setCount(8); while (tx.inventory.size() < 36) tx.inventory.add(ItemStack.EMPTY);
        tx.fault = fault; var before = data.serializeNBT().copy();
        var prepared = prepare(tx, data, new AnalyzerOutcomeTable(0, 1, 0, 2, 2));
        var result = BlueprintWeaponAnalysisService.commit(prepared, data, tx, bound -> fail("fixed outcome must not draw"));
        assertEquals(137, data.getResearchPoints());
        if (fault.isEmpty()) {
            assertTrue(result.transaction().successful()); assertEquals(3, tx.inventory.get(0).getCount());
            assertEquals(2, result.outcome().orElseThrow().count()); assertTrue(data.hasDiscoveredBlueprint(TARGET.toString()));
        } else {
            assertFalse(result.transaction().successful()); assertTrue(result.outcome().isEmpty());
            assertEquals(before, data.serializeNBT()); assertEquals(8, tx.inventory.get(0).getCount()); assertEquals(2, tx.input.getCount()); assertTrue(tx.output.isEmpty());
        }
    }

    @Test void physicalQuoteIsInvalidatedByChangingCurrencyIdentityBeforeRandomness() {
        var data = data(); var tx = new Tx(); tx.currency = ResearchCurrencyPolicy.physical(new ResourceLocation("minecraft:iron_ingot"), 1);
        tx.inventory.get(0).setCount(8); while (tx.inventory.size() < 36) tx.inventory.add(ItemStack.EMPTY);
        var prepared = prepare(tx, data, mixed());
        tx.currency = ResearchCurrencyPolicy.physical(new ResourceLocation("minecraft:paper"), 2);
        assertFalse(BlueprintWeaponAnalysisService.commit(prepared, data, tx, bound -> fail("stale quote rolled")).transaction().successful());
        assertEquals(8, tx.inventory.get(0).getCount());
    }

    static final class FaultData extends PlayerRecipeData {
        String fault="";
        @Override public boolean spendResearchPoints(int points) {
            boolean result=super.spendResearchPoints(points);
            if(fault.equals("spend_throw")) throw new IllegalStateException("injected RP failure");
            return !fault.equals("spend_false") && result;
        }
        @Override public boolean discoverBlueprint(String target) {
            boolean result=super.discoverBlueprint(target);
            if(fault.equals("discover_throw")) throw new IllegalStateException("injected discovery failure");
            if(fault.equals("unexpected_learn")) super.addBlueprint(target);
            return !fault.equals("discover_false") && result;
        }
    }
    static final class Tx implements BlueprintReverseEngineeringService.WorkstationTransaction {
        ItemStack input=new ItemStack(Items.STICK,2), output=ItemStack.EMPTY;
        List<ItemStack> inventory=new ArrayList<>(List.of(new ItemStack(Items.IRON_INGOT,4)));
        ResearchCurrencyPolicy currency = ResearchCurrencyPolicy.banked(100, 1);
        public ResearchCurrencyPolicy currencyPolicy() { return currency; }
        String fault=""; boolean current=true, loaded, attached;
        public ItemStack physicalInput(){return input;} public ItemStack outputStack(){return output;}
        public List<ItemStack> inventoryStacks(){return inventory;}
        public ItemStack createOutput(ResourceLocation target, BlueprintProvenance provenance) {
            if(fault.equals("builder_throw")) throw new IllegalStateException("builder");
            if(fault.equals("bad_blueprint")) return new ItemStack(Items.PAPER);
            var stack=new ItemStack(AnalysisTestItems.blueprint); stack.getOrCreateTag().putString("bpId",target.toString());
            stack.getOrCreateTag().put(BlueprintProvenance.TAG_KEY, (fault.equals("bad_blueprint_protection") ? BlueprintProvenance.reverseEngineered(true,
                    PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES) : provenance).toTag());
            if(fault.equals("bad_blueprint_count")) stack.setCount(2);
            return stack;
        }
        public ItemStack createFragments(ResourceLocation target, AnalysisRewardClass reward,int count) {
            if(fault.equals("bad_fragments")) return new ItemStack(Items.PAPER,count);
            var stack=BlueprintFragmentItem.createAnalysis(AnalysisTestItems.fragment,target,
                    fault.equals("bad_fragment_protection")?AnalysisRewardClass.RECYCLABLE_BYPASS:reward);
            stack.setCount(fault.equals("bad_fragment_count")?65:count); return stack;
        }
        public boolean consumeMaterials(ResearchIngredientPlanner.Plan plan,List<ItemStack> expected){
            inventory=new ArrayList<>(plan.applyToCopies(inventory)); return !fault.equals("materials_false");
        }
        public boolean consumePhysical(ItemStack expected,int count){input.shrink(count); if(fault.equals("physical_throw"))throw new IllegalStateException("physical"); return true;}
        public boolean placeOutput(ItemStack created,ItemStack expected){
            if(!fault.equals("output_nowrite")) output=created.copy();
            if(fault.equals("lease_after_write"))current=false;
            return !fault.equals("output_false") && !fault.equals("restore_false");
        }
        public boolean restore(ItemStack original,List<ItemStack> materials,ItemStack previous){
            input=original.copy();inventory=new ArrayList<>(materials.stream().map(ItemStack::copy).toList());output=previous.copy(); return !fault.equals("restore_false");
        }
    }
}
