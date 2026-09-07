package com.gamergaming.taczweaponblueprints.client;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class AnalyzerProbabilityTextTest {
    @Test void preservesImpossibleGuaranteedFractionalAndTinyProbabilities() {
        assertEquals("0%",AnalyzerProbabilityText.format(0,3));
        assertEquals("100%",AnalyzerProbabilityText.format(3,3));
        assertEquals("12.5%",AnalyzerProbabilityText.format(1,8));
        assertEquals("≈33.33%",AnalyzerProbabilityText.format(1,3));
        assertEquals("<0.01%",AnalyzerProbabilityText.format(1,3_000_000_000L));
        assertEquals(">99.99%",AnalyzerProbabilityText.format(999_999_999,1_000_000_000L));
        assertEquals("0.01%",AnalyzerProbabilityText.format(1,10000));
        assertEquals("99.99%",AnalyzerProbabilityText.format(9999,10000));
    }
}
