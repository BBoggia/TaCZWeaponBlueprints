package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutEngine;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;

/** One atomic client publication for the Journal and its matching research tree. */
public final class ClientResearchState {
    private static volatile Publication publication = Publication.EMPTY;
    private static long pendingJournalGeneration = Long.MIN_VALUE;
    private static long pendingTreeGeneration = Long.MIN_VALUE;
    private static BlueprintJournalSnapshot pendingJournal;
    private static ResearchTreePublication pendingTree;

    private ClientResearchState() {
    }

    public static Publication publication() {
        return publication;
    }

    public static synchronized void acceptJournal(
            long generation,
            BlueprintJournalSnapshot journal,
            boolean reuseExistingTree) {
        if (journal == null) {
            throw new IllegalArgumentException("completed Journal snapshot cannot be null");
        }
        if (publicationAtOrAfter(generation)
                || (pendingJournal != null
                && Long.compare(generation, pendingJournalGeneration) < 0)) {
            return;
        }
        if (reuseExistingTree) {
            ResearchTreePublication reusableTree = pendingTree != null
                    && Long.compare(pendingTreeGeneration, publication.generation()) > 0
                    && Long.compare(pendingTreeGeneration, generation) <= 0
                    ? pendingTree
                    : new ResearchTreePublication(publication.graph(), publication.presentation());
            publish(generation, journal, reusableTree);
            discardPendingThrough(generation);
            return;
        }
        pendingJournalGeneration = generation;
        pendingJournal = journal;
        if (pendingTree != null && Long.compare(pendingTreeGeneration, generation) < 0) {
            clearPendingTree();
        }
        publishPendingPair();
    }

    public static synchronized void acceptTree(long generation, ResearchTreePublication tree) {
        if (tree == null) {
            throw new IllegalArgumentException("completed research tree publication cannot be null");
        }
        if (publicationAtOrAfter(generation)
                || (pendingTree != null && Long.compare(generation, pendingTreeGeneration) < 0)) {
            return;
        }
        pendingTreeGeneration = generation;
        pendingTree = tree;
        if (pendingJournal != null && Long.compare(pendingJournalGeneration, generation) < 0) {
            clearPendingJournal();
        }
        publishPendingPair();
    }

    static synchronized void publishJournalOnly(BlueprintJournalSnapshot journal) {
        publish(publication.generation(), journal, new ResearchTreePublication(
                publication.graph(), publication.presentation()));
    }

    static synchronized void publishTreeOnly(ResearchTreePublication tree) {
        publish(publication.generation(), publication.journal(), tree);
    }

    public static synchronized void clear() {
        publication = Publication.EMPTY;
        clearPending();
    }

    private static void publishPendingPair() {
        if (pendingJournal != null && pendingTree != null
                && pendingJournalGeneration == pendingTreeGeneration) {
            long generation = pendingJournalGeneration;
            publish(generation, pendingJournal, pendingTree);
            discardPendingThrough(generation);
        }
    }

    private static boolean publicationAtOrAfter(long generation) {
        return publication != Publication.EMPTY
                && Long.compare(generation, publication.generation()) <= 0;
    }

    private static void publish(
            long generation,
            BlueprintJournalSnapshot journal,
            ResearchTreePublication tree) {
        ResearchTreePublication currentTree = new ResearchTreePublication(
                publication.graph(), publication.presentation());
        ResearchTreeLayout layout = currentTree.hasSamePresentationTopology(tree)
                ? publication.layout()
                : ResearchTreeLayoutEngine.layout(tree);
        publication = new Publication(
                generation,
                journal,
                tree.graph(),
                tree.presentation(),
                layout);
    }

    private static void clearPending() {
        clearPendingJournal();
        clearPendingTree();
    }

    private static void discardPendingThrough(long generation) {
        if (pendingJournal != null && Long.compare(pendingJournalGeneration, generation) <= 0) {
            clearPendingJournal();
        }
        if (pendingTree != null && Long.compare(pendingTreeGeneration, generation) <= 0) {
            clearPendingTree();
        }
    }

    private static void clearPendingJournal() {
        pendingJournalGeneration = Long.MIN_VALUE;
        pendingJournal = null;
    }

    private static void clearPendingTree() {
        pendingTreeGeneration = Long.MIN_VALUE;
        pendingTree = null;
    }

    public record Publication(
            long generation,
            BlueprintJournalSnapshot journal,
            ResearchTreeGraph graph,
            ResearchTreePresentation presentation,
            ResearchTreeLayout layout) {
        private static final Publication EMPTY = new Publication(
                Long.MIN_VALUE,
                BlueprintJournalSnapshot.EMPTY,
                ResearchTreeGraph.EMPTY,
                ResearchTreePresentation.EMPTY,
                ResearchTreeLayout.EMPTY);

        public Publication {
            if (journal == null || graph == null || presentation == null || layout == null
                    || graph.nodes().size() != layout.nodes().size()) {
                throw new IllegalArgumentException("invalid combined research publication");
            }
            new ResearchTreePublication(graph, presentation);
            for (int ordinal = 0; ordinal < graph.nodes().size(); ordinal++) {
                if (!graph.nodes().get(ordinal).blueprintId()
                        .equals(layout.nodes().get(ordinal).blueprintId())) {
                    throw new IllegalArgumentException("research graph and layout do not match");
                }
            }
            for (ResearchTreeLayout.HiddenAnchor anchor : layout.hiddenAnchors()) {
                ResearchTreeGraph.Node node = graph.node(anchor.dependentId()).orElseThrow(() ->
                        new IllegalArgumentException("hidden anchor references an unknown node"));
                if (node.hiddenPrerequisiteCount() != anchor.hiddenCount()) {
                    throw new IllegalArgumentException("hidden anchor count does not match its node");
                }
            }
        }
    }
}
