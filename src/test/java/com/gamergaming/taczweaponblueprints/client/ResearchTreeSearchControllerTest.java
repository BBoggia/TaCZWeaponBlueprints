package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeSearchControllerTest {
    @Test
    void normalizedSearchRetainsPublicationOrderAndVisibleSubset() {
        ResearchTreeGraph graph = ResearchTreeUxPhaseZeroFixture.everyAvailability();
        ResearchTreeSearchController search = new ResearchTreeSearchController();

        search.update(
                "  REQUIRED  ",
                graph.nodes(),
                node -> node.blueprintId().getPath().toUpperCase(java.util.Locale.ROOT));

        assertEquals("required", search.query());
        assertEquals(
                List.of(
                        new ResourceLocation("phase_zero:discovery_required"),
                        new ResourceLocation("phase_zero:prerequisites_required")),
                List.copyOf(search.matches()));
        assertEquals(
                List.copyOf(search.matches()),
                List.copyOf(search.visibleMatches(graph)));
    }

    @Test
    void resultCursorChangesWithoutImplicitlyCommittingNavigation() {
        ResearchTreeGraph graph = ResearchTreeUxPhaseZeroFixture.everyAvailability();
        ResearchTreeSearchController search = new ResearchTreeSearchController();
        ResourceLocation first = new ResourceLocation("phase_zero:discovery_required");
        ResourceLocation second = new ResourceLocation("phase_zero:prerequisites_required");

        search.update("required", graph.nodes(), node -> node.blueprintId().getPath());
        assertEquals(first, search.activeMatch().orElseThrow());
        assertEquals(first, search.commit().orElseThrow());

        assertEquals(second, search.selectNext(1).orElseThrow());
        assertEquals(second, search.activeMatch().orElseThrow());
        assertEquals(second, search.commit().orElseThrow());

        search.update("required", graph.nodes(), node -> node.blueprintId().getPath());
        assertEquals(second, search.activeMatch().orElseThrow());
        search.update("discovery", graph.nodes(), node -> node.blueprintId().getPath());
        assertEquals(first, search.activeMatch().orElseThrow());
        assertTrue(search.select(new ResourceLocation("test:missing")).isEmpty());
    }

    @Test
    void visibleWindowTracksTheCursorAcrossLargeResultSets() {
        ResearchTreeGraph graph = ResearchTreeUxPhaseZeroFixture.everyAvailability();
        ResearchTreeSearchController search = new ResearchTreeSearchController();
        search.update("weapon", graph.nodes(), node -> "weapon");
        for (int index = 0; index < 5; index++) {
            search.selectNext(1);
        }

        List<ResearchTreeSearchController.Result> window = search.window(3);

        assertEquals(3, window.size());
        assertEquals(List.of(4, 5, 6), window.stream()
                .map(ResearchTreeSearchController.Result::index)
                .toList());
        assertEquals(1L, window.stream()
                .filter(ResearchTreeSearchController.Result::active)
                .count());
        assertThrows(IllegalArgumentException.class, () -> search.window(0));
    }

    @Test
    void malformedSearchInputsAreRejected() {
        ResearchTreeSearchController search = new ResearchTreeSearchController();
        ResearchTreeGraph graph = ResearchTreeUxPhaseZeroFixture.everyAvailability();

        assertThrows(IllegalArgumentException.class,
                () -> search.update(null, graph.nodes(), node -> ""));
        assertThrows(IllegalArgumentException.class,
                () -> search.update("test", graph.nodes(), node -> null));
        assertThrows(IllegalArgumentException.class,
                () -> search.visibleMatches(null));
        search.update("", graph.nodes(), node -> node.blueprintId().getPath());
        assertTrue(search.matches().isEmpty());
        assertTrue(search.activeMatch().isEmpty());
        assertTrue(search.commit().isEmpty());
    }
}
