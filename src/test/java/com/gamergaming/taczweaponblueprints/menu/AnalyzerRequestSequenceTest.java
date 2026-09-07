package com.gamergaming.taczweaponblueprints.menu;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class AnalyzerRequestSequenceTest {
    @Test void repeatedOutOfOrderAndWrappedRequestsNeverExecuteAgain() {
        var requests=new AnalyzerRequestSequence();
        assertFalse(requests.accept(0));assertFalse(requests.accept(-1));assertTrue(requests.accept(1));
        assertFalse(requests.accept(1));assertTrue(requests.accept(5));assertFalse(requests.accept(4));
        assertTrue(requests.accept(Integer.MAX_VALUE));assertFalse(requests.accept(1));
        assertFalse(requests.accept(Integer.MAX_VALUE)); assertTrue(new AnalyzerRequestSequence().accept(1));
    }
}
