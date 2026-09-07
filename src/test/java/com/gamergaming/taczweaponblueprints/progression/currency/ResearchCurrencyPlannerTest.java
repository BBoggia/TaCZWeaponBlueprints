package com.gamergaming.taczweaponblueprints.progression.currency;

import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyQuote.Status.*;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.gamergaming.taczweaponblueprints.progression.ResearchIngredientPlanner.Requirement;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointTransactionService.OverflowPolicy;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPlanner.Destination;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPlanner.Request;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;

class ResearchCurrencyPlannerTest {
    static final ResourceLocation IRON = new ResourceLocation("minecraft:iron_ingot");
    static final ResearchCurrencyPolicy PHYSICAL = ResearchCurrencyPolicy.physical(IRON,1);
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    static List<ItemStack> inventory(ItemStack... stacks) {
        var result = new ArrayList<ItemStack>(Collections.nCopies(36, ItemStack.EMPTY));
        for (int i=0;i<stacks.length;i++) result.set(i,stacks[i]);
        return result;
    }
    static ResearchCurrencyState state(int points, ItemStack... stacks) { return new ResearchCurrencyState(inventory(stacks),ItemStack.EMPTY,points); }
    static Request request(long debit, long reward, Destination destination, Requirement... materials) {
        return new Request(debit,List.of(materials),reward,destination,OverflowPolicy.REQUIRE_FULL);
    }
    static Requirement iron(int count) { return new Requirement(List.of(IRON),Optional.empty(),count); }
    static long ironCount(ResearchCurrencyState s) { return s.inventory().stream().filter(v -> v.is(Items.IRON_INGOT)).mapToLong(ItemStack::getCount).sum(); }

    @Test void currencyAndOverlappingMaterialCannotSpendTheSameUnitsTwice() {
        var request = request(70,0,Destination.INVENTORY_ONLY,iron(3));
        var enough = ResearchCurrencyPlanner.prepare(PHYSICAL,state(0,new ItemStack(Items.IRON_INGOT,64),new ItemStack(Items.IRON_INGOT,9)),request);
        assertTrue(enough.quote().ready()); assertEquals(0,ironCount(enough.after()));
        var shortByOne = ResearchCurrencyPlanner.prepare(PHYSICAL,state(0,new ItemStack(Items.IRON_INGOT,64),new ItemStack(Items.IRON_INGOT,8)),request);
        assertEquals(INSUFFICIENT_MATERIALS,shortByOne.quote().status()); assertEquals(72,ironCount(shortByOne.after()));
    }

    @Test void overlappingTagAndAlternativeMaterialUseExactAllocation() {
        var tag = TagKey.create(Registries.ITEM,new ResourceLocation("currency_test:payment_material"));
        var holder = Items.IRON_INGOT.builtInRegistryHolder();
        var old = holder.tags().toList();
        try {
            holder.bindTags(List.of(tag));
            var tagged = new Requirement(List.of(),Optional.of(tag.location()),3);
            var alternative = new Requirement(List.of(IRON,new ResourceLocation("minecraft:gold_ingot")),Optional.empty(),2);
            var plan = ResearchCurrencyPlanner.prepare(PHYSICAL,state(0,new ItemStack(Items.IRON_INGOT,5),new ItemStack(Items.GOLD_INGOT,2)),
                    request(2,0,Destination.INVENTORY_ONLY,tagged,alternative));
            assertTrue(plan.quote().ready()); assertTrue(plan.after().inventory().stream().allMatch(ItemStack::isEmpty));
        } finally { holder.bindTags(old); }
    }

    @Test void grossPaymentIsRequiredBeforeAwardAndPostPaymentCapacityIsUsed() {
        assertEquals(INSUFFICIENT_CURRENCY,ResearchCurrencyPlanner.prepare(PHYSICAL,state(0,new ItemStack(Items.IRON_INGOT,2)),
                request(3,100,Destination.MANUAL_OUTPUT)).quote().status());
        var full = new ArrayList<ItemStack>(); for(int i=0;i<36;i++) full.add(new ItemStack(Items.STONE,64));
        full.set(0,new ItemStack(Items.IRON_INGOT,3));
        var before = new ResearchCurrencyState(full,ItemStack.EMPTY,9999);
        var plan = ResearchCurrencyPlanner.prepare(PHYSICAL,before,request(3,65,Destination.MANUAL_OUTPUT));
        assertTrue(plan.quote().ready()); assertEquals(64,plan.quote().outputUnits()); assertEquals(1,plan.quote().inventoryUnits());
        assertEquals(9999,plan.after().bankedPoints()); assertEquals(1,ironCount(plan.after()));
        assertEquals(3,ironCount(before));
    }

    @Test void manualOutputBlocksIncompatibleItemsAndPreservesTaggedCurrency() {
        var named = new ItemStack(Items.IRON_INGOT,3); named.getOrCreateTag().putString("test","keep");
        var before = new ResearchCurrencyState(inventory(),named,0);
        assertEquals(OUTPUT_BLOCKED,ResearchCurrencyPlanner.prepare(PHYSICAL,before,request(0,1,Destination.MANUAL_OUTPUT)).quote().status());
        // Item identity determines debit value; tags still determine safe stacking for newly minted output.
        var plan = ResearchCurrencyPlanner.prepare(PHYSICAL,state(0,named),request(1,1,Destination.INVENTORY_ONLY));
        assertTrue(plan.quote().ready()); assertEquals(2,plan.after().inventory().get(0).getCount());
        assertEquals("keep",plan.after().inventory().get(0).getTag().getString("test"));
        assertFalse(plan.after().inventory().get(1).hasTag());
    }

    @ParameterizedTest @ValueSource(ints={1,63,64,65,128,2304,2368})
    void manualDeliveryConservesUnitsAcrossOutputAndInventory(int amount) {
        var p = ResearchCurrencyPlanner.prepare(PHYSICAL,state(0),request(0,amount,Destination.MANUAL_OUTPUT));
        assertTrue(p.quote().ready()); assertEquals(amount,ironCount(p.after())+p.after().output().getCount());
        assertEquals(amount,p.quote().outputUnits()+p.quote().inventoryUnits()); assertTrue(p.drops().isEmpty());
    }

    @Test void oversizeManualAndInventoryOnlyAwardsRejectWithoutPartialDelivery() {
        var p = ResearchCurrencyPlanner.prepare(PHYSICAL,state(0),request(0,2369,Destination.MANUAL_OUTPUT));
        assertEquals(DELIVERY_FULL,p.quote().status()); assertTrue(p.before().matches(p.after()));
        p = ResearchCurrencyPlanner.prepare(PHYSICAL,state(0),request(0,2305,Destination.INVENTORY_ONLY));
        assertEquals(DELIVERY_FULL,p.quote().status());
    }

    @Test void eventBatchLimitsApplyToAggregateAndPartialExistingStacks() {
        var full = new ArrayList<ItemStack>(); for(int i=0;i<36;i++) full.add(new ItemStack(Items.STONE,64));
        var s = new ResearchCurrencyState(full,ItemStack.EMPTY,0);
        var p = ResearchCurrencyPlanner.prepare(PHYSICAL,s,request(0,1024,Destination.EVENT));
        assertEquals(16,p.drops().size()); assertEquals(1024,p.quote().droppedUnits());
        assertEquals(DELIVERY_LIMIT,ResearchCurrencyPlanner.prepare(PHYSICAL,s,request(0,1025,Destination.EVENT)).quote().status());
        assertEquals(DELIVERY_LIMIT,ResearchCurrencyPlanner.prepare(PHYSICAL,state(0),request(0,4097,Destination.EVENT)).quote().status());
        var partial=ResearchCurrencyPlanner.prepare(PHYSICAL,state(0,new ItemStack(Items.IRON_INGOT,63)),request(0,65,Destination.EVENT));
        assertEquals(64,partial.after().inventory().get(0).getCount()); assertEquals(64,partial.after().inventory().get(1).getCount());
    }

    @Test void replacementCurrencyUsesItsRealStackSize() {
        var pearl = ResearchCurrencyPolicy.physical(new ResourceLocation("minecraft:ender_pearl"),1);
        var plan = ResearchCurrencyPlanner.prepare(pearl,state(0),request(0,17,Destination.MANUAL_OUTPUT));
        assertEquals(16,plan.quote().outputUnits()); assertEquals(1,plan.quote().inventoryUnits());
        assertEquals(DELIVERY_LIMIT,ResearchCurrencyPlanner.prepare(pearl,state(0),request(0,1025,Destination.EVENT)).quote().status());
    }

    @Test void bankedGrossPaymentCapsClampingAndDormantOverCapBalancesArePreserved() {
        var policy = ResearchCurrencyPolicy.banked(10,1);
        var request=request(3,5,Destination.MANUAL_OUTPUT);
        var plan=ResearchCurrencyPlanner.prepare(policy,state(10),request);
        assertEquals(POINT_CAP_REACHED,plan.quote().status());
        plan=ResearchCurrencyPlanner.prepare(policy,state(10),new Request(3,List.of(),5,Destination.MANUAL_OUTPUT,OverflowPolicy.CLAMP));
        assertTrue(plan.quote().ready()); assertEquals(3,plan.quote().deliveredReward()); assertEquals(10,plan.after().bankedPoints());
        plan=ResearchCurrencyPlanner.prepare(policy,state(10),new Request(0,List.of(),5,Destination.EVENT,OverflowPolicy.CLAMP));
        assertTrue(plan.quote().ready()); assertEquals(0,plan.quote().deliveredReward()); // permits the caller's ledger-only claim
        assertEquals(INSUFFICIENT_CURRENCY,ResearchCurrencyPlanner.prepare(policy,state(2),request).quote().status());
        assertEquals(19,ResearchCurrencyPlanner.prepare(policy,state(20),request(1,0,Destination.INVENTORY_ONLY)).after().bankedPoints());
        assertEquals(READY,ResearchCurrencyPlanner.prepare(ResearchCurrencyPolicy.banked(0,1),state(0),request(0,0,Destination.EVENT)).quote().status());
        assertEquals(POINT_CAP_REACHED,ResearchCurrencyPlanner.prepare(ResearchCurrencyPolicy.banked(0,1),state(0),request(0,1,Destination.EVENT)).quote().status());
    }

    @Test void hugeCountsCannotOverflowLoopOrBecomeFree() {
        assertEquals(INSUFFICIENT_CURRENCY,ResearchCurrencyPlanner.prepare(PHYSICAL,state(0),request(Long.MAX_VALUE,0,Destination.EVENT)).quote().status());
        assertEquals(DELIVERY_FULL,ResearchCurrencyPlanner.prepare(PHYSICAL,state(0),request(0,Long.MAX_VALUE,Destination.MANUAL_OUTPUT)).quote().status());
        var many = Collections.nCopies(6144,iron(1000000));
        assertEquals(INSUFFICIENT_MATERIALS,ResearchCurrencyPlanner.prepare(PHYSICAL,state(0),new Request(0,many,0,Destination.EVENT,OverflowPolicy.REQUIRE_FULL)).quote().status());
        assertThrows(IllegalArgumentException.class,()->new Request(0,Collections.nCopies(6145,iron(1)),0,Destination.EVENT,OverflowPolicy.REQUIRE_FULL));
        assertThrows(IllegalArgumentException.class,()->request(-1,0,Destination.EVENT));
    }

    @Test void unknownCurrencyInvalidInventoryAndMutatingReadbackCannotCorruptPlans() {
        assertEquals(INVALID_CURRENCY,ResearchCurrencyPlanner.prepare(ResearchCurrencyPolicy.physical(new ResourceLocation("missing:item"),1),state(0),request(0,0,Destination.EVENT)).quote().status());
        assertThrows(IllegalArgumentException.class,()->new ResearchCurrencyState(Collections.nCopies(37,ItemStack.EMPTY),ItemStack.EMPTY,0));
        assertThrows(IllegalArgumentException.class,()->state(0,new ItemStack(Items.IRON_INGOT,65)));
        var input=new ItemStack(Items.IRON_INGOT,3); var s=state(0,input); input.setCount(1);
        var p=ResearchCurrencyPlanner.prepare(PHYSICAL,s,request(2,0,Destination.EVENT));
        p.before().inventory().get(0).setCount(64); p.after().inventory().get(0).setCount(64);
        assertEquals(3,ironCount(p.before())); assertEquals(1,ironCount(p.after()));
    }

    @Test void compensationPreservesUntouchedSlotsAndRejectsAnUnrecognizedOwnedSlot() {
        var before=state(15,new ItemStack(Items.IRON_INGOT,4));
        var p=ResearchCurrencyPlanner.prepare(PHYSICAL,before,request(2,1,Destination.MANUAL_OUTPUT));
        var actual=p.after().inventory();
        var edited=new ArrayList<>(actual); edited.set(5,new ItemStack(Items.DIAMOND));
        var restored=ResearchCurrencyState.restoration(before,p.afterPayment(),p.after(),new ResearchCurrencyState(edited,p.after().output(),15)).orElseThrow();
        assertEquals(4,ironCount(restored)); assertTrue(restored.output().isEmpty()); assertTrue(restored.inventory().get(5).is(Items.DIAMOND));
        edited.set(0,new ItemStack(Items.DIRT));
        assertTrue(ResearchCurrencyState.restoration(before,p.afterPayment(),p.after(),new ResearchCurrencyState(edited,p.after().output(),15)).isEmpty());
    }

    @Test void fullCompatibleOutputSpillsToInventoryButBankedDeliveryDoesNotRequireOutput() {
        var before=new ResearchCurrencyState(inventory(),new ItemStack(Items.IRON_INGOT,64),10);
        var p=ResearchCurrencyPlanner.prepare(PHYSICAL,before,request(0,2,Destination.MANUAL_OUTPUT));
        assertTrue(p.quote().ready()); assertEquals(0,p.quote().outputUnits()); assertEquals(2,p.quote().inventoryUnits());
        before=new ResearchCurrencyState(inventory(),new ItemStack(Items.STONE,64),10);
        p=ResearchCurrencyPlanner.prepare(ResearchCurrencyPolicy.banked(20,1),before,request(2,1,Destination.MANUAL_OUTPUT));
        assertTrue(p.quote().ready()); assertEquals(9,p.after().bankedPoints()); assertTrue(ItemStack.matches(before.output(),p.after().output()));
    }

    @Test void generatedInventoriesConserveCurrencyAndMaterialsWithoutMutatingInputs() {
        var random=new Random(7102026L);
        for(int attempt=0;attempt<150;attempt++) {
            var inv=inventory();
            for(int i=0;i<36;i++) if(random.nextBoolean()) inv.set(i,new ItemStack(random.nextBoolean()?Items.IRON_INGOT:Items.GOLD_INGOT,1+random.nextInt(64)));
            var before=new ResearchCurrencyState(inv,ItemStack.EMPTY,42);
            var saved=new ResearchCurrencyState(inv,ItemStack.EMPTY,42);
            int debit=random.nextInt(700), reward=random.nextInt(300), materials=random.nextInt(50);
            var terms=materials==0 ? new Requirement[0] : new Requirement[]{iron(materials)};
            var p=ResearchCurrencyPlanner.prepare(PHYSICAL,before,request(debit,reward,Destination.EVENT,terms));
            assertTrue(before.matches(saved));
            if(p.quote().ready()) {
                assertEquals(ironCount(before)-debit-materials+reward,ironCount(p.after())+p.quote().droppedUnits());
                assertEquals(42,p.after().bankedPoints());
                long goldBefore=before.inventory().stream().filter(s->s.is(Items.GOLD_INGOT)).mapToLong(ItemStack::getCount).sum();
                long goldAfter=p.after().inventory().stream().filter(s->s.is(Items.GOLD_INGOT)).mapToLong(ItemStack::getCount).sum();
                assertEquals(goldBefore,goldAfter);
            } else assertTrue(before.matches(p.after()));
        }
    }
}
