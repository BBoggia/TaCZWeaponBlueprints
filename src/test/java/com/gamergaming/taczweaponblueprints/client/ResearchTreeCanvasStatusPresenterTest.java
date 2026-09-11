package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import static com.gamergaming.taczweaponblueprints.client.ResearchTreeCanvasStatusPresenter.*;

class ResearchTreeCanvasStatusPresenterTest {
    @Test void publicationReceptionDistinguishesLoadingFromConfirmedEmpty() {
        ClientResearchState.clear();
        try {
            assertFalse(ClientResearchState.hasPublication());
            assertEquals(State.LOADING, present(new Input(false, false, false, true, false, true, null, false, 0)));
            ClientResearchState.acceptTree(1, ResearchTreePublication.EMPTY);
            assertFalse(ClientResearchState.hasPublication());
            ClientResearchState.acceptJournal(1, BlueprintJournalSnapshot.EMPTY, false);
            assertTrue(ClientResearchState.hasPublication());
            assertEquals(State.EMPTY_PUBLICATION, present(new Input(false, true, false, true, false, true, null, false, 0)));
        } finally { ClientResearchState.clear(); }
    }

    @Test void failuresWinOverEmptyGraphsAndStaleSearchResults() {
        assertEquals(State.REJECTED, present(new Input(true, true, true, true, false, true, null, true, 0)));
        assertEquals(State.UNAVAILABLE, present(new Input(false, true, true, false, true, false, null, true, 0)));
        assertEquals(State.UNAVAILABLE, present(new Input(false, true, false, false, true, false,
                ResearchTreeSceneCache.Status.UNAVAILABLE, true, 0)));
        assertEquals(State.TECH_TREE_UNAVAILABLE, present(new Input(false, true, false, false, false, true, null, true, 0)));
        assertEquals(State.EMPTY_DOMAIN, present(new Input(false, true, false, false, true, true,
                ResearchTreeSceneCache.Status.VALID, true, 0)));
        assertEquals(Action.CLOSE_BENCH, State.REJECTED.action());
        assertEquals(Action.CLOSE_BENCH, State.UNAVAILABLE.action());
    }

    @Test void searchStatusUsesTheQueryEvenWhenSearchOrInspectorIsClosed() {
        // Neither selection nor overlay visibility is an input to canvas status.
        for (var status : new ResearchTreeSceneCache.Status[]{ResearchTreeSceneCache.Status.VALID, ResearchTreeSceneCache.Status.FALLBACK}) {
            assertEquals(State.NO_SEARCH_MATCHES, present(new Input(false, true, false, false, true, false, status, true, 0)));
            assertEquals(State.NONE, present(new Input(false, true, false, false, true, false, status, true, 13)));
            assertEquals(State.NONE, present(new Input(false, true, false, false, true, false, status, false, 0)));
        }
        assertEquals(Action.CLEAR_SEARCH, State.NO_SEARCH_MATCHES.action());
        assertEquals(Action.NONE, State.EMPTY_PUBLICATION.action());
    }
}
