package com.gamergaming.taczweaponblueprints.client;

import java.util.Map;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchDomainAvailability;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;

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

    public static boolean hasPublication() {
        return publication != Publication.EMPTY;
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
                    : new ResearchTreePublication(
                            publication.graph(), publication.presentation(), publication.techTree(), publication.domainAvailability());
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
                publication.graph(), publication.presentation(), publication.techTree(), publication.domainAvailability()));
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
        publication = new Publication(
                generation,
                journal,
                tree.graph(),
                tree.presentation(),
                tree.techTree(), tree.domainAvailability());
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
            ResearchTechTreePresentation techTree,
            Map<Domain, ResearchDomainAvailability> domainAvailability) {
        public Publication(long generation, BlueprintJournalSnapshot journal, ResearchTreeGraph graph,
                ResearchTreePresentation presentation, ResearchTechTreePresentation techTree) {
            this(generation, journal, graph, presentation, techTree, Map.of());
        }

        private static final Publication EMPTY = new Publication(
                Long.MIN_VALUE,
                BlueprintJournalSnapshot.EMPTY,
                ResearchTreeGraph.EMPTY,
                ResearchTreePresentation.EMPTY,
                ResearchTechTreePresentation.EMPTY);

        public Publication(
                long generation,
                BlueprintJournalSnapshot journal,
                ResearchTreeGraph graph,
                ResearchTreePresentation presentation) {
            this(generation, journal, graph, presentation, ResearchTechTreePresentation.EMPTY);
        }

        public Publication {
            domainAvailability = Map.copyOf(domainAvailability);
            if (journal == null || graph == null || presentation == null || techTree == null) {
                throw new IllegalArgumentException("invalid combined research publication");
            }
            new ResearchTreePublication(graph, presentation, techTree, domainAvailability);
        }
    }
}
