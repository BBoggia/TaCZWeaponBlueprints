package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ClientResearchStateTest {
    @AfterEach
    void clearPublication() {
        ClientResearchState.clear();
    }

    @Test
    void journalAndTreePublishOnlyAsOneMatchingGeneration() {
        BlueprintJournalSnapshot journal = journal(4);
        ResearchTreeGraph graph = graph(ResearchTreeGraph.Availability.AVAILABLE);
        ResearchTreePublication tree = publication(graph);

        ClientResearchState.acceptJournal(10L, journal, false);
        assertEquals(BlueprintJournalSnapshot.EMPTY, ClientResearchState.publication().journal());
        assertEquals(ResearchTreeGraph.EMPTY, ClientResearchState.publication().graph());

        ClientResearchState.acceptTree(10L, tree);
        assertEquals(10L, ClientResearchState.publication().generation());
        assertEquals(journal, ClientResearchState.publication().journal());
        assertEquals(graph, ClientResearchState.publication().graph());
        assertEquals(tree.presentation(), ClientResearchState.publication().presentation());
    }

    @Test
    void unchangedTreesAndStateOnlyTreesReuseTheExistingLayout() {
        BlueprintJournalSnapshot firstJournal = journal(4);
        ResearchTreeGraph firstGraph = graph(ResearchTreeGraph.Availability.AVAILABLE);
        ResearchTreePublication firstTree = publication(firstGraph);
        ClientResearchState.acceptJournal(1L, firstJournal, false);
        ClientResearchState.acceptTree(1L, firstTree);
        var originalLayout = ClientResearchState.publication().layout();
        var originalPresentation = ClientResearchState.publication().presentation();

        BlueprintJournalSnapshot pointOnlyJournal = journal(9);
        ClientResearchState.acceptJournal(2L, pointOnlyJournal, true);
        assertEquals(pointOnlyJournal, ClientResearchState.publication().journal());
        assertSame(originalLayout, ClientResearchState.publication().layout());
        assertSame(originalPresentation, ClientResearchState.publication().presentation());

        ResearchTreeGraph stateOnlyGraph = graph(ResearchTreeGraph.Availability.COST_ABOVE_CAP);
        ClientResearchState.acceptJournal(3L, journal(10), false);
        ClientResearchState.acceptTree(3L, publication(stateOnlyGraph));
        assertEquals(stateOnlyGraph, ClientResearchState.publication().graph());
        assertSame(originalLayout, ClientResearchState.publication().layout());
        assertEquals(originalPresentation, ClientResearchState.publication().presentation());
    }

    @Test
    void pointOnlyJournalUsesTheNewestCompletedPendingTree() {
        ResearchTreeGraph originalGraph = graph("test:original", ResearchTreeGraph.Availability.AVAILABLE);
        ClientResearchState.acceptJournal(9L, journal(4), false);
        ClientResearchState.acceptTree(9L, publication(originalGraph));

        ResearchTreeGraph replacementGraph = graph(
                "test:replacement", ResearchTreeGraph.Availability.AVAILABLE);
        ClientResearchState.acceptTree(10L, publication(replacementGraph));
        assertEquals(originalGraph, ClientResearchState.publication().graph());

        BlueprintJournalSnapshot pointOnlyJournal = journal(12);
        ClientResearchState.acceptJournal(11L, pointOnlyJournal, true);

        assertEquals(11L, ClientResearchState.publication().generation());
        assertEquals(pointOnlyJournal, ClientResearchState.publication().journal());
        assertEquals(replacementGraph, ClientResearchState.publication().graph());
    }

    @Test
    void staleCompletedInputsCannotRegressAnAtomicPublication() {
        ResearchTreeGraph currentGraph = graph("test:current", ResearchTreeGraph.Availability.AVAILABLE);
        BlueprintJournalSnapshot currentJournal = journal(20);
        ClientResearchState.acceptJournal(20L, currentJournal, false);
        ClientResearchState.acceptTree(20L, publication(currentGraph));

        ClientResearchState.acceptTree(
                19L,
                publication(graph("test:stale", ResearchTreeGraph.Availability.AVAILABLE)));
        ClientResearchState.acceptJournal(19L, journal(1), false);

        assertEquals(20L, ClientResearchState.publication().generation());
        assertEquals(currentJournal, ClientResearchState.publication().journal());
        assertEquals(currentGraph, ClientResearchState.publication().graph());
    }

    @Test
    void anOlderReusePublicationDoesNotDiscardAFuturePendingTree() {
        ResearchTreeGraph originalGraph = graph("test:original", ResearchTreeGraph.Availability.AVAILABLE);
        ClientResearchState.acceptJournal(5L, journal(5), false);
        ClientResearchState.acceptTree(5L, publication(originalGraph));

        ResearchTreeGraph futureGraph = graph("test:future", ResearchTreeGraph.Availability.AVAILABLE);
        ClientResearchState.acceptTree(7L, publication(futureGraph));
        ClientResearchState.acceptJournal(6L, journal(6), true);
        assertEquals(6L, ClientResearchState.publication().generation());
        assertEquals(originalGraph, ClientResearchState.publication().graph());

        ClientResearchState.acceptJournal(7L, journal(7), false);
        assertEquals(7L, ClientResearchState.publication().generation());
        assertEquals(futureGraph, ClientResearchState.publication().graph());
    }

    @Test
    void disconnectClearDropsPublishedAndPartiallyCompletedState() {
        ClientResearchState.acceptJournal(30L, journal(30), false);
        ClientResearchState.clear();
        ClientResearchState.acceptTree(
                30L,
                publication(graph("test:orphaned_tree", ResearchTreeGraph.Availability.AVAILABLE)));

        assertEquals(Long.MIN_VALUE, ClientResearchState.publication().generation());
        assertEquals(BlueprintJournalSnapshot.EMPTY, ClientResearchState.publication().journal());
        assertEquals(ResearchTreeGraph.EMPTY, ClientResearchState.publication().graph());
    }

    @Test
    void firstPublicationAcceptsTheFullSignedGenerationRange() {
        ResearchTreeGraph graph = graph("test:minimum_generation", ResearchTreeGraph.Availability.AVAILABLE);

        ClientResearchState.acceptJournal(Long.MIN_VALUE, journal(1), false);
        ClientResearchState.acceptTree(Long.MIN_VALUE, publication(graph));

        assertEquals(Long.MIN_VALUE, ClientResearchState.publication().generation());
        assertEquals(graph, ClientResearchState.publication().graph());
    }

    private static BlueprintJournalSnapshot journal(int points) {
        return new BlueprintJournalSnapshot(List.of(), List.of(), points, 100, 0, 0, 0);
    }

    private static ResearchTreeGraph graph(ResearchTreeGraph.Availability availability) {
        return graph("test:a", availability);
    }

    private static ResearchTreeGraph graph(
            String blueprintId,
            ResearchTreeGraph.Availability availability) {
        boolean available = availability == ResearchTreeGraph.Availability.AVAILABLE;
        return new ResearchTreeGraph(List.of(new ResearchTreeGraph.Node(
                0,
                id(blueprintId),
                "name.a",
                "rifle",
                id("test:slot/" + id(blueprintId).getPath()),
                JournalVisibility.FULL,
                false,
                true,
                available,
                8,
                0,
                0,
                0,
                availability)), List.of());
    }

    private static ResearchTreePublication publication(ResearchTreeGraph graph) {
        ResourceLocation nodeId = graph.nodes().get(0).blueprintId();
        ResearchTreePresentation presentation = new ResearchTreePresentation(List.of(
                new ResearchTreePresentation.Group(
                        id("test:published"),
                        "Published",
                        Optional.of("group.test.published"),
                        Optional.of(nodeId),
                        0,
                        ResearchTreePresentation.Kind.AUTHORED,
                        List.of(new ResearchTreePresentation.Member(nodeId, 0, 0)))));
        return new ResearchTreePublication(graph, presentation);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
