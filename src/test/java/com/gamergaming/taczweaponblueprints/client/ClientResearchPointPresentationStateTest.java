package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.Feedback;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.HelpEntry;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.HelpSnapshot;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger;

class ClientResearchPointPresentationStateTest {
    @AfterEach
    void clearConnectionState() {
        ClientResearchPointPresentationState.clear();
    }

    @Test
    void nearbyFeedbackIsAggregatedWithoutDuplicatingNames() {
        var first = ClientResearchPointPresentationState.acceptFeedback(
                new Feedback(2, 0, false, List.of("award.test.zombie")), 1_000L);
        var combined = ClientResearchPointPresentationState.acceptFeedback(
                new Feedback(3, 1, true, List.of("award.test.zombie")), 2_500L);

        assertEquals(2, first.awardedPoints());
        assertEquals(5, combined.awardedPoints());
        assertEquals(1, combined.genericAwardCount());
        assertTrue(combined.claimedAtCap());
        assertEquals(List.of("award.test.zombie"), combined.namedAwards());
    }

    @Test
    void expiredOrBackwardWindowsStartAFreshNotification() {
        ClientResearchPointPresentationState.acceptFeedback(
                new Feedback(2, 0, false, List.of("award.test.first")), 4_000L);
        var expired = ClientResearchPointPresentationState.acceptFeedback(
                new Feedback(7, 1, false, List.of()), 6_001L);
        var backward = ClientResearchPointPresentationState.acceptFeedback(
                new Feedback(1, 0, false, List.of("award.test.new")), 5_000L);

        assertEquals(7, expired.awardedPoints());
        assertEquals(1, backward.awardedPoints());
        assertEquals(List.of("award.test.new"), backward.namedAwards());
    }

    @Test
    void helpRevisionsCannotRollBackAndDisconnectClearsEverything() {
        HelpEntry entry = new HelpEntry(
                "award.test.public", ResearchPointAwardTrigger.Type.ADVANCEMENT_COMPLETED, 4);
        ClientResearchPointPresentationState.acceptHelp(new HelpSnapshot(5L, List.of(entry)));
        ClientResearchPointPresentationState.acceptHelp(new HelpSnapshot(4L, List.of()));

        assertEquals(5L, ClientResearchPointPresentationState.help().revision());
        assertEquals(List.of(entry), ClientResearchPointPresentationState.help().entries());

        ClientResearchPointPresentationState.clear();
        assertEquals(HelpSnapshot.EMPTY, ClientResearchPointPresentationState.help());
        assertFalse(ClientResearchPointPresentationState.acceptFeedback(
                Feedback.EMPTY, 1L).claimedAtCap());
    }
}
