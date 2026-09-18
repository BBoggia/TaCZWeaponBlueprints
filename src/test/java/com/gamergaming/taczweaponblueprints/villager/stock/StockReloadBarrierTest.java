package com.gamergaming.taczweaponblueprints.villager.stock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.taczweaponblueprints.villager.stock.StockReloadBarrier.Completion.*;
class StockReloadBarrierTest {
 @Test void successWaitsForEndOfTickAndEveryActiveAttempt(){
  var b=new StockReloadBarrier();long a=b.begin(),c=b.begin();
  b.initialized();b.settle();assertTrue(b.pending());
  assertEquals(WAITING,b.finish(c,false));b.settle();assertTrue(b.pending());
  assertEquals(COMMIT,b.finish(a,false));assertTrue(b.pending());b.settle();assertFalse(b.pending());
 }
 @Test void failureNeedsReconciliationAndOlderRecoveryCannotUnlockNewReload(){
  var b=new StockReloadBarrier();long a=b.begin();assertEquals(RECOVER,b.finish(a,true));
  b.initialized();b.settle();assertTrue(b.pending());
  long newer=b.begin();b.recovered(a);b.settle();assertTrue(b.pending());
  assertEquals(RECOVER,b.finish(newer,false));b.recovered(newer);b.settle();assertFalse(b.pending());
 }
 @Test void failureDuringOverlapReconcilesOnlyAfterLastCompletionAndResetRejectsOldCallbacks(){
  var b=new StockReloadBarrier();long a=b.begin(),c=b.begin();assertEquals(WAITING,b.finish(a,true));
  assertEquals(RECOVER,b.finish(c,false));b.reset(true);b.recovered(c);b.settle();assertTrue(b.pending());
  assertEquals(IGNORED,b.finish(c,true));b.initialized();b.settle();assertFalse(b.pending());
 }
 @Test void syncCannotCommitBeforeALateFailureAndDuplicateCallbacksCannotCommit(){
  var b=new StockReloadBarrier();long attempt=b.begin();
  b.initialized();b.settle();assertTrue(b.pending());
  assertEquals(RECOVER,b.finish(attempt,true));
  assertEquals(IGNORED,b.finish(attempt,false));b.settle();assertTrue(b.pending());
  b.recovered(attempt);b.settle();assertFalse(b.pending());
  long success=b.begin();assertEquals(COMMIT,b.finish(success,false));
  assertEquals(IGNORED,b.finish(success,false));
 }
 @Test void EarlierSuccessfulAttemptCannotCommitWhileNewerAttemptCanStillFail(){
  var b=new StockReloadBarrier();long first=b.begin(),second=b.begin();
  assertEquals(WAITING,b.finish(first,false));b.initialized();b.settle();assertTrue(b.pending());
  assertEquals(RECOVER,b.finish(second,true));
 }
}
