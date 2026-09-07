package com.gamergaming.taczweaponblueprints.progression.analyzer;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnalyzerOutcomeTableTest {
    @Test void everyBoundaryUsesHalfOpenIntervalsAndInclusiveFragmentQuantities() {
        var table = new AnalyzerOutcomeTable(2, 3, 5, 2, 4);
        for (int draw = 0; draw < 10; draw++) {
            for (int quantity : List.of(0, 2)) {
                var inputs = new ArrayDeque<Long>(List.of((long) draw, (long) quantity));
                var bounds = new ArrayDeque<Long>();
                var result = table.select(bound -> { bounds.add(bound); return inputs.remove(); });
                assertEquals(10L, bounds.remove());
                if (draw < 2) {
                    assertEquals(new AnalyzerOutcomeTable.Result(AnalyzerOutcomeTable.Kind.BLUEPRINT, 1), result);
                    assertTrue(bounds.isEmpty());
                } else if (draw < 5) {
                    assertEquals(new AnalyzerOutcomeTable.Result(AnalyzerOutcomeTable.Kind.FRAGMENTS, 2 + quantity), result);
                    assertEquals(3L, bounds.remove());
                } else {
                    assertEquals(new AnalyzerOutcomeTable.Result(AnalyzerOutcomeTable.Kind.NO_RESULT, 0), result);
                    assertTrue(bounds.isEmpty());
                }
            }
        }
    }
    @Test void deterministicResultsDoNotTouchRandomnessAndVariableQuantityDrawsOnce() {
        for (var table : List.of(new AnalyzerOutcomeTable(1,0,0,0,0), new AnalyzerOutcomeTable(0,1,0,64,64), new AnalyzerOutcomeTable(0,0,1,0,0))) {
            assertNotNull(table.select(bound -> { throw new AssertionError("unexpected random draw"); }));
        }
        var calls = new AtomicInteger();
        assertEquals(6, new AnalyzerOutcomeTable(0,1,0,4,6).select(bound -> {
            calls.incrementAndGet(); assertEquals(3, bound); return 2;
        }).count());
        assertEquals(1, calls.get());
    }
    @Test void longTotalsCannotOverflowAndFractionalPercentagesRetainTheirMeaning() {
        var table = new AnalyzerOutcomeTable(1_000_000_000,1_000_000_000,1_000_000_000,1,1);
        assertEquals(3_000_000_000L, table.totalWeight());
        assertEquals(AnalyzerOutcomeTable.Kind.NO_RESULT, table.select(bound -> {
            assertEquals(3_000_000_000L, bound); return bound - 1;
        }).kind());
        assertEquals(0.125, new AnalyzerOutcomeTable(1,7,0,1,1).blueprintProbability());
        assertTrue(new AnalyzerOutcomeTable(1,1_000_000_000,0,1,1).blueprintProbability() > 0);
        assertThrows(IllegalArgumentException.class, () -> table.select(bound -> -1));
        assertThrows(IllegalArgumentException.class, () -> table.select(bound -> bound));
    }
    @ParameterizedTest
    @ValueSource(strings={"-1", "1.5", "1000000001", "1e100", "1.00000000000000000001", "true", "\"1\"", "null"})
    void malformedWeightsAreRejectedWithoutTruncation(String weight) {
        var json = JsonParser.parseString("{\"blueprint_weight\":" + weight + ",\"fragment_weight\":1,\"no_result_weight\":0,\"min_fragments\":1,\"max_fragments\":1}");
        assertTrue(AnalyzerOutcomeTable.CODEC.parse(JsonOps.INSTANCE,json).error().isPresent());
    }
    @Test void invalidRangesWeightsAndUnknownFieldsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AnalyzerOutcomeTable(0,0,0,0,0));
        assertThrows(IllegalArgumentException.class, () -> new AnalyzerOutcomeTable(1,1,0,2,1));
        assertThrows(IllegalArgumentException.class, () -> new AnalyzerOutcomeTable(1,1,0,1,65));
        assertThrows(IllegalArgumentException.class, () -> new AnalyzerOutcomeTable(1,0,0,1,1));
        assertThrows(IllegalArgumentException.class, () -> new AnalyzerOutcomeTable(1,1,0,0,1));
        var json=JsonParser.parseString("{\"blueprint_weight\":1,\"fragment_weight\":0,\"no_result_weight\":0,\"min_fragments\":0,\"max_fragments\":0,\"seed\":12}");
        assertTrue(AnalyzerOutcomeTable.CODEC.parse(JsonOps.INSTANCE,json).error().isPresent());
    }
}
