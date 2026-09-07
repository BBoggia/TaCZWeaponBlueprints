package com.gamergaming.taczweaponblueprints.progression.currency;

import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPlannerTest.*;
import static com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyTransaction.Status.*;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPlanner.Destination;
import net.minecraft.world.item.*;

class ResearchCurrencyTransactionTest {
    @BeforeAll static void initialize() { bootstrap(); }
    @Test void preparedAttemptIsOneUseEvenWhenCallerRestoresInventoryToOriginalContents() {
        var before=state(0,new ItemStack(Items.IRON_INGOT,10));
        var p=ResearchCurrencyPlanner.prepare(PHYSICAL,before,request(3,2,Destination.MANUAL_OUTPUT));
        var c=new Context(before); var participant=new Participant();
        assertEquals(SUCCESS,ResearchCurrencyTransaction.commit(p,c,participant).status());
        assertEquals(7,ironCount(c.state)); assertEquals(2,c.state.output().getCount()); assertTrue(participant.claim);
        c.state=before;
        assertEquals(ALREADY_USED,ResearchCurrencyTransaction.commit(p,c,participant).status());
    }

    @ParameterizedTest @ValueSource(strings={"lease","policy","inventory","output","preflight"})
    void staleInputsAreRejectedBeforeAnyWrites(String mutation) {
        var before=state(0,new ItemStack(Items.IRON_INGOT,5));
        var p=ResearchCurrencyPlanner.prepare(PHYSICAL,before,request(1,1,Destination.EVENT));
        var c=new Context(before); var a=new Participant();
        switch(mutation) {
            case "lease" -> c.valid=false;
            case "policy" -> c.policy=ResearchCurrencyPolicy.physical(IRON,2);
            case "inventory" -> c.state=state(0,new ItemStack(Items.IRON_INGOT,4));
            case "output" -> c.state=new ResearchCurrencyState(before.inventory(),new ItemStack(Items.STONE),0);
            case "preflight" -> a.ready=false;
        }
        assertEquals(STALE,ResearchCurrencyTransaction.commit(p,c,a).status()); assertEquals(0,c.writes); assertFalse(a.claim);
    }

    @ParameterizedTest @ValueSource(strings={"payment","participant","delivery","lease_after_payment","participant_throw","restore"})
    void failuresRestoreBothCurrencyAndTheAssociatedClaim(String failure) {
        var before=state(17,new ItemStack(Items.IRON_INGOT,5));
        var p=ResearchCurrencyPlanner.prepare(PHYSICAL,before,request(2,1,Destination.MANUAL_OUTPUT));
        var c=new Context(before); var a=new Participant();
        switch(failure) {
            case "payment" -> c.failWrite=1;
            case "delivery" -> c.failWrite=2;
            case "participant" -> a.fail=true;
            case "participant_throw" -> a.throwAfterClaim=true;
            case "lease_after_payment" -> c.invalidateAfterWrite=true;
            case "restore" -> { a.fail=true; c.failRestore=true; }
        }
        assertEquals(failure.equals("restore") ? ROLLBACK_FAILED : FAILED,ResearchCurrencyTransaction.commit(p,c,a).status());
        assertFalse(a.claim);
        if (!failure.equals("restore")) assertTrue(before.matches(c.state));
    }

    @ParameterizedTest @ValueSource(strings={"cancel","throw","changed","late_changed"})
    void failedDropPublicationDiscardsEveryOwnedEntityAndRestoresClaim(String failure) {
        var inv=inventory(); for(int i=0;i<36;i++) inv.set(i,new ItemStack(Items.STONE,64));
        var before=new ResearchCurrencyState(inv,ItemStack.EMPTY,0);
        var p=ResearchCurrencyPlanner.prepare(PHYSICAL,before,request(0,129,Destination.EVENT));
        var c=new Context(before); c.dropFailure=failure;
        var a=new Participant();
        assertEquals(FAILED,ResearchCurrencyTransaction.commit(p,c,a).status());
        assertFalse(a.claim); assertTrue(before.matches(c.state)); assertTrue(c.drops.stream().allMatch(d -> d.removed));
    }

    @Test void unavailableCleanupIsReportedAsRollbackFailure() {
        var inv=inventory(); for(int i=0;i<36;i++) inv.set(i,new ItemStack(Items.STONE,64));
        var before=new ResearchCurrencyState(inv,ItemStack.EMPTY,0);
        var p=ResearchCurrencyPlanner.prepare(PHYSICAL,before,request(0,1,Destination.EVENT));
        var c=new Context(before); c.dropFailure="cancel"; c.failDiscard=true;
        assertEquals(ROLLBACK_FAILED,ResearchCurrencyTransaction.commit(p,c,new Participant()).status());
    }

    @Test void participantPreflightCannotChangeInventoryAfterTheInitialCheck() {
        var before=state(0,new ItemStack(Items.IRON_INGOT,5));
        var p=ResearchCurrencyPlanner.prepare(PHYSICAL,before,request(1,0,Destination.EVENT));
        var c=new Context(before); var a=new Participant(); a.onReady=()->c.state=state(0);
        assertEquals(STALE,ResearchCurrencyTransaction.commit(p,c,a).status()); assertEquals(0,c.writes);
    }

    @Test void failedPreflightQuoteNeverCallsMutationContext() {
        var before=state(0);
        var p=ResearchCurrencyPlanner.prepare(PHYSICAL,before,request(1,0,Destination.EVENT));
        var c=new Context(before);
        assertEquals(NOT_READY,ResearchCurrencyTransaction.commit(p,c,new Participant()).status()); assertEquals(0,c.writes);
    }

    @Test void bankedPaymentAndRewardUseTheSameCommitAndRollbackBoundary() {
        var bank=ResearchCurrencyPolicy.banked(20,1); var before=state(10);
        var p=ResearchCurrencyPlanner.prepare(bank,before,request(3,4,Destination.MANUAL_OUTPUT));
        var c=new Context(before); c.policy=bank;
        assertEquals(SUCCESS,ResearchCurrencyTransaction.commit(p,c,new Participant()).status()); assertEquals(11,c.state.bankedPoints());
        p=ResearchCurrencyPlanner.prepare(bank,c.state,request(1,1,Destination.EVENT));
        var a=new Participant(); a.fail=true;
        assertEquals(FAILED,ResearchCurrencyTransaction.commit(p,c,a).status()); assertEquals(11,c.state.bankedPoints());
    }

    @Test void publicationGuardRejectsAPolicyChangeDuringParticipantPreflight() {
        var before = state(17, new ItemStack(Items.IRON_INGOT, 5));
        var plan = ResearchCurrencyPlanner.prepare(PHYSICAL, before, request(2, 1, Destination.MANUAL_OUTPUT));
        var context = new Context(before); var current = new java.util.concurrent.atomic.AtomicBoolean(true);
        var participant = new Participant(); participant.onReady = () -> current.set(false);
        var guarded = ResearchCurrencyTransaction.guarded(context, current::get);
        assertEquals(STALE, ResearchCurrencyTransaction.commit(plan, guarded, participant).status());
        assertTrue(before.matches(context.state)); assertEquals(0, context.writes); assertFalse(participant.claim);
    }

    static class Participant implements ResearchCurrencyTransaction.Participant {
        boolean ready=true, claim, fail, throwAfterClaim; Runnable onReady=()->{};
        public boolean ready() { onReady.run(); return ready; }
        public boolean commit() { claim=true; if(throwAfterClaim) throw new IllegalStateException("injected claim failure"); return !fail; }
        public boolean committed() { return claim; }
        public boolean rollback() { claim=false; return true; }
    }
    static class Context implements ResearchCurrencyTransaction.Context {
        ResearchCurrencyState state; ResearchCurrencyPolicy policy=PHYSICAL;
        boolean valid=true, invalidateAfterWrite, failRestore, failDiscard; int writes, failWrite;
        String dropFailure=""; List<Drop> drops=new ArrayList<>();
        Context(ResearchCurrencyState state) { this.state=state; }
        public boolean valid() { return valid; }
        public ResearchCurrencyPolicy policy() { return policy; }
        public ResearchCurrencyState read() { return state; }
        public boolean replace(ResearchCurrencyState expected, ResearchCurrencyState replacement) {
            if(!state.matches(expected)) return false;
            state=replacement; writes++; if(invalidateAfterWrite) valid=false; return writes!=failWrite;
        }
        public boolean restore(ResearchCurrencyState before, ResearchCurrencyState paid, ResearchCurrencyState after) {
            if(failRestore) return false;
            var restored=ResearchCurrencyState.restoration(before,paid,after,state);
            if(restored.isEmpty()) return false;
            state=restored.orElseThrow(); return true;
        }
        public ResearchCurrencyTransaction.StagedDrop stageDrop(ItemStack stack) {
            var d=new Drop(stack,this,drops.size()); drops.add(d); return d;
        }
    }
    static class Drop implements ResearchCurrencyTransaction.StagedDrop {
        ItemStack stack; Context context; int index; boolean published,removed;
        Drop(ItemStack stack,Context context,int index) { this.stack=stack; this.context=context; this.index=index; }
        public boolean publish() {
            published=true;
            if(index==0 || context.drops.size()==1) {
                if(context.dropFailure.equals("cancel")) return false;
                if(context.dropFailure.equals("throw")) throw new IllegalStateException("injected spawn failure");
                if(context.dropFailure.equals("changed")) stack=new ItemStack(Items.DIRT);
            }
            if(index==1 && context.dropFailure.equals("late_changed")) context.drops.get(0).stack=new ItemStack(Items.DIRT);
            return true;
        }
        public boolean matches(ItemStack expected) { return published && !removed && ItemStack.matches(stack,expected); }
        public void discard() { if(!context.failDiscard) removed=true; }
        public boolean removed() { return removed; }
    }
}
