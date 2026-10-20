package com.gamergaming.taczweaponblueprints.progression;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.testing.AnalysisTestItems;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import net.minecraft.world.item.*;

class AnalyzerAmmoRecoveryTest {
    @BeforeAll static void bootstrap() { AnalysisTestItems.initialize(); }
    @Test void closedBoltMagazineAndChamberAreBothReturned() {
        assertEquals(31,AnalyzerAmmoRecovery.roundCount(30,true,Bolt.CLOSED_BOLT,false));
        assertEquals(1,AnalyzerAmmoRecovery.roundCount(0,true,Bolt.CLOSED_BOLT,false));
        assertEquals(30,AnalyzerAmmoRecovery.roundCount(30,false,Bolt.CLOSED_BOLT,false));
    }
    @Test void openBoltStaleChamberFlagDoesNotCreateARound() {
        assertEquals(30,AnalyzerAmmoRecovery.roundCount(30,true,Bolt.OPEN_BOLT,false));
        assertEquals(0,AnalyzerAmmoRecovery.roundCount(0,true,Bolt.OPEN_BOLT,false));
    }
    @Test void virtualAmmoNeverCreatesPhysicalItems() {
        assertEquals(0,AnalyzerAmmoRecovery.roundCount(30,true,Bolt.CLOSED_BOLT,true));
    }
    @Test void invalidAndOverflowingCountsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->AnalyzerAmmoRecovery.roundCount(-1,false,Bolt.CLOSED_BOLT,false));
        assertThrows(IllegalArgumentException.class,()->AnalyzerAmmoRecovery.roundCount(Integer.MAX_VALUE,true,Bolt.CLOSED_BOLT,false));
        assertThrows(IllegalArgumentException.class,()->AnalyzerAmmoRecovery.roundCount(65536,true,Bolt.CLOSED_BOLT,false));
        assertThrows(IllegalArgumentException.class,()->AnalyzerAmmoRecovery.roundCount(1,false,null,false));
    }
    @Test void stacksSplitExactlyWithoutMutatingPrototype() {
        var ammo=new ItemStack(Items.ARROW);var stacks=AnalyzerAmmoRecovery.split(ammo,130);
        assertEquals(List.of(64,64,2),stacks.stream().map(ItemStack::getCount).toList());assertEquals(1,ammo.getCount());
    }
    @Test void mergeUsesExistingStacksBeforeEmptySlotsAndPreservesOverflow() {
        var slots=new ArrayList<>(List.of(ItemStack.EMPTY,new ItemStack(Items.ARROW,60),new ItemStack(Items.STONE,64)));
        var ammo=new ItemStack(Items.ARROW,80);AnalyzerAmmoRecovery.insert(slots,ammo,64);
        assertEquals(64,slots.get(0).getCount());assertEquals(64,slots.get(1).getCount());assertEquals(12,ammo.getCount());
    }
    @Test void ammunitionWithDifferentTagsDoesNotMerge() {
        var existing=new ItemStack(Items.ARROW,60);net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, existing, customTag -> customTag.putString("AmmoId","test:a"));
        var incoming=new ItemStack(Items.ARROW,4);net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, incoming, customTag -> customTag.putString("AmmoId","test:b"));
        AnalyzerAmmoRecovery.insert(new ArrayList<>(List.of(existing)),incoming,64);
        assertEquals(4,incoming.getCount());assertEquals(60,existing.getCount());
    }
    @Test void fullInventoryLeavesAllAmmoForDropEvenWithoutEmptySlots() {
        var slots=new ArrayList<>(Collections.nCopies(36,new ItemStack(Items.STONE,64)));
        var ammo=new ItemStack(Items.ARROW,31);AnalyzerAmmoRecovery.insert(slots,ammo,64);assertEquals(31,ammo.getCount());
    }
    @Test void failedActionDoesNotDeliverOrChangeLoadedInput() {
        var tx=new BlueprintWeaponAnalysisServiceTest.Tx();var data=new PlayerRecipeData();var input=tx.input.copy();
        var calls=new AtomicInteger();String result=run(tx,data,()->"failed",new AnalyzerAmmoRecovery.Delivery(){
            public void give(List<ItemStack> s){calls.incrementAndGet();}public void rollback(){calls.incrementAndGet();}});
        assertEquals("failed",result);assertEquals(0,calls.get());assertTrue(ItemStack.matches(input,tx.input));
    }
    @Test void rejectedDropRollsBackPaymentWeaponOutputAndProgression() {
        var tx=new BlueprintWeaponAnalysisServiceTest.Tx();var data=new PlayerRecipeData();data.setResearchPoints(30);
        var original=tx.input.copy();var before=data.serializeNBT().copy();var revoked=new AtomicInteger();
        String result=run(tx,data,()->{tx.input=ItemStack.EMPTY;tx.output=new ItemStack(Items.PAPER);data.setResearchPoints(5);return "ok";},
                new AnalyzerAmmoRecovery.Delivery(){public void give(List<ItemStack> s){tx.inventory.set(0,new ItemStack(Items.ARROW));throw new IllegalStateException("cancelled spawn");}
                    public void rollback(){revoked.incrementAndGet();}});
        assertEquals("TRANSACTION_FAILED",result);assertTrue(ItemStack.matches(original,tx.input));assertTrue(tx.output.isEmpty());
        assertEquals(before,data.serializeNBT());assertEquals(1,revoked.get());assertFalse(tx.inventory.get(0).is(Items.ARROW));
    }
    @Test void successfulDeliveryRunsOnlyAfterConsumptionAndCanUseFreedSlots() {
        var tx=new BlueprintWeaponAnalysisServiceTest.Tx();var data=new PlayerRecipeData();var calls=new AtomicInteger();
        assertEquals("ok",run(tx,data,()->{tx.input=ItemStack.EMPTY;tx.inventory.set(0,ItemStack.EMPTY);return "ok";},
                new AnalyzerAmmoRecovery.Delivery(){public void give(List<ItemStack> s){assertTrue(tx.input.isEmpty());assertTrue(tx.inventory.get(0).isEmpty());calls.incrementAndGet();}
                    public void rollback(){fail("unexpected rollback");}}));assertEquals(1,calls.get());
    }
    @Test void refundIsNotDeliveredWhenActionReportsSuccessWithoutConsumingWeapon() {
        var tx=new BlueprintWeaponAnalysisServiceTest.Tx();var data=new PlayerRecipeData();
        assertEquals("TRANSACTION_FAILED",run(tx,data,()->"ok",new AnalyzerAmmoRecovery.Delivery(){
            public void give(List<ItemStack> s){fail("must not mint ammo");}public void rollback(){}}));
    }
    @Test void failureToRestoreIsReportedExplicitly() {
        var tx=new BlueprintWeaponAnalysisServiceTest.Tx();tx.fault="restore_false";var data=new PlayerRecipeData();
        assertEquals("ROLLBACK_FAILED",run(tx,data,()->{tx.input=ItemStack.EMPTY;return "ok";},new AnalyzerAmmoRecovery.Delivery(){
            public void give(List<ItemStack> s){throw new IllegalStateException("drop failed");}public void rollback(){}}));
    }
    private static String run(BlueprintWeaponAnalysisServiceTest.Tx tx,PlayerRecipeData data,java.util.function.Supplier<String> action,AnalyzerAmmoRecovery.Delivery delivery) {
        return AnalyzerAmmoRecovery.execute(new AnalyzerAmmoRecovery.Plan(null,1,new ItemStack(Items.ARROW)),tx,data,action,"ok"::equals,Enum::name,delivery);
    }
}
