package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeViewStateTest {
    @Test
    void compactAndFullscreenKeepIndependentViewportState() {
        ResearchTreeViewState state = new ResearchTreeViewState();
        ResearchTreeViewport compact = state.viewport(ResearchTreeScreenLayout.ViewMode.COMPACT);
        ResearchTreeViewport fullscreen = state.viewport(ResearchTreeScreenLayout.ViewMode.FULLSCREEN);
        compact.configure(294, 116, 800, 600);
        fullscreen.configure(700, 400, 800, 600);

        compact.zoomAt(1.0D, 100, 50);

        assertNotEquals(compact.scale(), fullscreen.scale());
        assertEquals(1.0D, fullscreen.scale());
    }

    @Test
    void publicationRetainsValidFocusAndFiltersStaleSearchMatches() {
        ResearchTreeViewState state = new ResearchTreeViewState();
        state.focus(id("test:removed"));
        state.setSearchMatches(new LinkedHashSet<>(List.of(id("test:b"), id("test:removed"))));
        ResearchTreeGraph graph = graph("test:a", "test:b");

        state.retainVisibleNodes(graph, id("test:b"));

        assertEquals(id("test:b"), state.focusedId().orElseThrow());
        assertEquals(Set.of(id("test:b")), state.searchMatches());
    }

    @Test
    void emptyPublicationClearsFocusAndMatches() {
        ResearchTreeViewState state = new ResearchTreeViewState();
        state.focus(id("test:a"));
        state.setSearchMatches(Set.of(id("test:a")));

        state.retainVisibleNodes(ResearchTreeGraph.EMPTY, id("test:a"));

        assertTrue(state.focusedId().isEmpty());
        assertTrue(state.searchMatches().isEmpty());
    }

    private static ResearchTreeGraph graph(String... values) {
        java.util.ArrayList<ResearchTreeGraph.Node> nodes = new java.util.ArrayList<>();
        for (int ordinal = 0; ordinal < values.length; ordinal++) {
            ResourceLocation id = id(values[ordinal]);
            nodes.add(new ResearchTreeGraph.Node(
                    ordinal,
                    id,
                    "name." + id.getPath(),
                    "rifle",
                    id("test:slot/" + ordinal),
                    JournalVisibility.FULL,
                    false, false, true, 8, 0, 0, 0,
                    ResearchTreeGraph.Availability.AVAILABLE));
        }
        return new ResearchTreeGraph(nodes, List.of());
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
