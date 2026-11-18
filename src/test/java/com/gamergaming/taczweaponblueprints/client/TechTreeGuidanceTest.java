package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.client.TechTreeGuidance.Entry;
import com.gamergaming.taczweaponblueprints.client.TechTreeGuidance.Kind;
import com.gamergaming.taczweaponblueprints.client.ui.UiSheet.TileState;

import net.minecraft.resources.ResourceLocation;

class TechTreeGuidanceTest {
    @Test
    void aNewPlayerIsPointedAtTheCheapestBlueprintTheyCanResearch() {
        var advice = TechTreeGuidance.advise(List.of(
                entry("dear", TileState.READY, 8), entry("cheap", TileState.READY, 4),
                entry("also_cheap", TileState.READY, 4), entry("later", TileState.LOCKED, 6)));
        assertEquals(Kind.START, advice.kind());
        assertEquals(Optional.of(id("cheap")), advice.target(), "the first of the cheapest in reading order");
        assertEquals(4, advice.cost());
        assertEquals(3, advice.readyCount());
    }

    @Test
    void oncePlayIsUnderWayTheAdviceCountsWhatIsReady() {
        var advice = TechTreeGuidance.advise(List.of(
                entry("done", TileState.LEARNED, 4), entry("a", TileState.READY, 8), entry("b", TileState.READY, 4)));
        assertEquals(Kind.READY, advice.kind());
        assertEquals(2, advice.readyCount());
        assertEquals(Optional.of(id("a")), advice.target(), "the first ready blueprint in reading order");
    }

    @Test
    void withNothingAffordableTheCheapestReachableBlueprintIsNamed() {
        var advice = TechTreeGuidance.advise(List.of(
                entry("done", TileState.LEARNED, 4), entry("far", TileState.OPEN, 20),
                entry("near", TileState.OPEN, 12), benchLocked("gated", 2, 2)));
        assertEquals(Kind.SHORT, advice.kind());
        assertEquals(Optional.of(id("near")), advice.target());
        assertEquals(12, advice.cost());
    }

    @Test
    void whenOnlyTheBenchIsInTheWayTheAdviceNamesTheLowestBenchThatHelps() {
        var advice = TechTreeGuidance.advise(List.of(
                entry("done", TileState.LEARNED, 4), benchLocked("top", 3, 30), benchLocked("mid", 2, 16)));
        assertEquals(Kind.BENCH, advice.kind());
        assertEquals(2, advice.benchTier());
        assertEquals(Optional.of(id("mid")), advice.target());
    }

    @Test
    void aFinishedTreeAndAStalledTreeSayDifferentThings() {
        assertEquals(Kind.COMPLETE, TechTreeGuidance.advise(List.of(
                entry("a", TileState.LEARNED, 4), entry("b", TileState.LEARNED, 4))).kind());
        assertEquals(Kind.WAITING, TechTreeGuidance.advise(List.of(
                entry("a", TileState.LEARNED, 4), entry("b", TileState.LOCKED, 4), entry("c", TileState.HIDDEN, 0))).kind());
        assertEquals(Kind.WAITING, TechTreeGuidance.advise(List.of()).kind(), "an empty tree has nothing to finish");
    }

    @Test
    void nextStepsThroughReadyBlueprintsAndWrapsAround() {
        List<Entry> entries = List.of(entry("a", TileState.READY, 4), entry("skip", TileState.LOCKED, 4),
                entry("b", TileState.READY, 4));
        assertEquals(Optional.of(id("a")), TechTreeGuidance.next(entries, Optional.empty()));
        assertEquals(Optional.of(id("b")), TechTreeGuidance.next(entries, Optional.of(id("a"))));
        assertEquals(Optional.of(id("a")), TechTreeGuidance.next(entries, Optional.of(id("b"))), "wraps to the start");
        assertEquals(Optional.of(id("a")), TechTreeGuidance.next(entries, Optional.of(id("skip"))),
                "from a blueprint that is not ready, start at the first ready one");
    }

    @Test
    void nextFallsBackToWhatTheAdvicePointsAt() {
        List<Entry> entries = List.of(entry("done", TileState.LEARNED, 4), entry("near", TileState.OPEN, 12));
        assertEquals(Optional.of(id("near")), TechTreeGuidance.next(entries, Optional.empty()));
        assertTrue(TechTreeGuidance.next(List.of(entry("done", TileState.LEARNED, 4)), Optional.empty()).isEmpty());
    }

    private static Entry entry(String name, TileState state, int cost) {
        return new Entry(id(name), state, false, cost, 1);
    }

    private static Entry benchLocked(String name, int benchTier, int cost) {
        return new Entry(id(name), TileState.OPEN, true, cost, benchTier);
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation("test:" + name);
    }
}
