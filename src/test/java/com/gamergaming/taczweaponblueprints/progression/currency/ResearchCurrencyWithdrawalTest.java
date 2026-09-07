package com.gamergaming.taczweaponblueprints.progression.currency;

import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPlannerTest.*;
import static com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyTransaction.Status.*;
import java.util.*;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResearchCurrencyWithdrawalTest {
    @BeforeAll static void init() { bootstrap(); }
    @Test void partialWithdrawalConservesValueAndRetryCannotDeliverTwice() {
        var before = state(137, new ItemStack(Items.IRON_INGOT, 60));
        var plan = ResearchCurrencyPlanner.prepareWithdrawal(PHYSICAL, before, 70);
        assertTrue(before.matches(state(137, new ItemStack(Items.IRON_INGOT,60))));
        var context = new ResearchCurrencyTransactionTest.Context(before);
        assertEquals(SUCCESS, ResearchCurrencyTransaction.commit(plan, context, ResearchCurrencyTransaction.NONE).status());
        assertEquals(67, context.state.bankedPoints()); assertEquals(130, ironCount(context.state));
        assertEquals(197, context.state.bankedPoints()+ironCount(context.state));
        assertEquals(ALREADY_USED, ResearchCurrencyTransaction.commit(plan, context, ResearchCurrencyTransaction.NONE).status());
        assertTrue(context.drops.isEmpty());
    }
    @ParameterizedTest @ValueSource(ints={0,-1,2305,Integer.MAX_VALUE})
    void requestBounds(int amount) { assertThrows(IllegalArgumentException.class, () -> ResearchCurrencyPlanner.prepareWithdrawal(PHYSICAL,state(10000),amount)); }
    @Test void fullInventoryAndInsufficientBankLeaveOriginalState() {
        var inv=inventory();Collections.fill(inv,new ItemStack(Items.STONE,64));
        var before=new ResearchCurrencyState(inv,ItemStack.EMPTY,137);
        var full=ResearchCurrencyPlanner.prepareWithdrawal(PHYSICAL,before,1);
        assertEquals(ResearchCurrencyQuote.Status.DELIVERY_FULL,full.quote().status());assertTrue(before.matches(full.after()));
        assertEquals(ResearchCurrencyQuote.Status.INSUFFICIENT_CURRENCY,ResearchCurrencyPlanner.prepareWithdrawal(PHYSICAL,before,138).quote().status());
        assertEquals(ResearchCurrencyQuote.Status.INVALID_CURRENCY,ResearchCurrencyPlanner.prepareWithdrawal(ResearchCurrencyPolicy.banked(1000,1),before,1).quote().status());
    }
    @ParameterizedTest @ValueSource(strings={"balance","inventory","policy","offline"})
    void changedPreviewCannotCommit(String change) {
        var before=state(137);var plan=ResearchCurrencyPlanner.prepareWithdrawal(PHYSICAL,before,10);
        var c=new ResearchCurrencyTransactionTest.Context(before);
        switch(change) {
            case "balance" -> c.state=state(136);
            case "inventory" -> c.state=state(137,new ItemStack(Items.STONE));
            case "policy" -> c.policy=ResearchCurrencyPolicy.physical(IRON,2);
            case "offline" -> c.valid=false;
        }
        assertEquals(STALE,ResearchCurrencyTransaction.commit(plan,c,ResearchCurrencyTransaction.NONE).status());assertEquals(0,c.writes);
    }
    @ParameterizedTest @ValueSource(ints={1,2})
    void failedDebitOrDeliveryRestoresBothSides(int failure) {
        var before=state(137);var plan=ResearchCurrencyPlanner.prepareWithdrawal(PHYSICAL,before,10);
        var c=new ResearchCurrencyTransactionTest.Context(before);c.failWrite=failure;
        assertEquals(FAILED,ResearchCurrencyTransaction.commit(plan,c,ResearchCurrencyTransaction.NONE).status());
        assertTrue(before.matches(c.state));
    }
    @Test void confirmationsAreBoundedOwnedExpiredAndCleared() {
        var registry=new ResearchCurrencyWithdrawals();var player=UUID.randomUUID();var plan=ResearchCurrencyPlanner.prepareWithdrawal(PHYSICAL,state(137),10);
        var c=new ResearchCurrencyTransactionTest.Context(plan.before());
        var token=registry.preview("owner",player,1,plan,c);
        assertTrue(registry.take(token,"someone else",2).isEmpty());assertTrue(registry.take(token,"owner",2).isPresent());
        assertTrue(registry.take(token,"owner",2).isEmpty());
        token=registry.preview("owner",player,1,plan,c);assertTrue(registry.take(token,"owner",1201).isEmpty());
        token=registry.preview("owner",player,1,plan,c);registry.clear(player);assertTrue(registry.take(token,"owner",2).isEmpty());
        token=registry.preview("owner",player,1,plan,c);registry.preview("owner",player,1,plan,c);assertTrue(registry.take(token,"owner",2).isEmpty());
        registry.clear();token=registry.preview("owner",player,1,plan,c);
        for(int i=0;i<256;i++)registry.preview("owner",UUID.randomUUID(),1,plan,c);
        assertTrue(registry.take(token,"owner",2).isEmpty());
    }
    @Test void restoredBackupRestoresValueButOldInMemoryPlanRemainsUsed() {
        var before=state(137);var plan=ResearchCurrencyPlanner.prepareWithdrawal(PHYSICAL,before,137);
        var c=new ResearchCurrencyTransactionTest.Context(before);
        assertEquals(SUCCESS,ResearchCurrencyTransaction.commit(plan,c,ResearchCurrencyTransaction.NONE).status());
        c.state=before;assertEquals(ALREADY_USED,ResearchCurrencyTransaction.commit(plan,c,ResearchCurrencyTransaction.NONE).status());
        var fresh=ResearchCurrencyPlanner.prepareWithdrawal(PHYSICAL,c.state,137);
        assertEquals(SUCCESS,ResearchCurrencyTransaction.commit(fresh,c,ResearchCurrencyTransaction.NONE).status());
        // An intentional administrator backup restore is not a durable anti-duplication claim.
    }
}
