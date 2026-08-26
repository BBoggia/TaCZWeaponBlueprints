package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreePresentationContractTest {
    @Test
    void categoryLanesCannotRecoverRedactedItemTypes() {
        assertEquals(
                ResearchTreePresentationContract.UNDISCLOSED_CATEGORY_LANE,
                ResearchTreePresentationContract.categoryLane(redactedNode()));
        assertEquals("rifle", ResearchTreePresentationContract.categoryLane(
                node(ResearchTreeGraph.Availability.PREVIEW, false)));
    }

    @Test
    void everyAvailabilityHasAColorIndependentStatusSymbol() {
        for (ResearchTreeGraph.Availability availability : ResearchTreeGraph.Availability.values()) {
            ResearchTreePresentationContract.StatusSymbol symbol =
                    ResearchTreePresentationContract.statusSymbol(
                            availability == ResearchTreeGraph.Availability.REDACTED
                                    ? redactedNode()
                                    : node(availability, availability == ResearchTreeGraph.Availability.LEARNED),
                            true);
            assertEquals(expected(availability), symbol);
        }
        assertEquals(
                ResearchTreePresentationContract.StatusSymbol.POINTS_REQUIRED,
                ResearchTreePresentationContract.statusSymbol(
                        node(ResearchTreeGraph.Availability.AVAILABLE, false), false));
    }

    @Test
    void everyAvailabilityHasAPlayerFacingNextAction() {
        assertEquals(
                ResearchTreePresentationContract.NextAction.FOLLOW_PATH,
                ResearchTreePresentationContract.nextAction(redactedNode(), false));
        assertEquals(
                ResearchTreePresentationContract.NextAction.INSPECT_REQUIREMENTS,
                ResearchTreePresentationContract.nextAction(
                        node(ResearchTreeGraph.Availability.PREVIEW, false), false));
        assertEquals(
                ResearchTreePresentationContract.NextAction.ALREADY_LEARNED,
                ResearchTreePresentationContract.nextAction(
                        node(ResearchTreeGraph.Availability.LEARNED, true), true));
        assertEquals(
                ResearchTreePresentationContract.NextAction.PREPARE_MATERIALS,
                ResearchTreePresentationContract.nextAction(
                        node(ResearchTreeGraph.Availability.AVAILABLE, false), true));
        assertEquals(
                ResearchTreePresentationContract.NextAction.EARN_POINTS,
                ResearchTreePresentationContract.nextAction(
                        node(ResearchTreeGraph.Availability.AVAILABLE, false), false));
        assertEquals(
                ResearchTreePresentationContract.NextAction.COMPLETE_REQUIREMENTS,
                ResearchTreePresentationContract.nextAction(
                        node(ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED, false), true));
        assertEquals(
                ResearchTreePresentationContract.NextAction.DISCOVER_WEAPON,
                ResearchTreePresentationContract.nextAction(
                        node(ResearchTreeGraph.Availability.DISCOVERY_REQUIRED, false), true));
        assertEquals(
                ResearchTreePresentationContract.NextAction.RESEARCH_DISABLED,
                ResearchTreePresentationContract.nextAction(
                        node(ResearchTreeGraph.Availability.RESEARCH_DISABLED, false), true));
        assertEquals(
                ResearchTreePresentationContract.NextAction.COST_UNAVAILABLE,
                ResearchTreePresentationContract.nextAction(
                        node(ResearchTreeGraph.Availability.COST_ABOVE_CAP, false), true));
        assertEquals(
                ResearchTreePresentationContract.NextAction.CONTENT_UNAVAILABLE,
                ResearchTreePresentationContract.nextAction(
                        node(ResearchTreeGraph.Availability.CONTENT_UNAVAILABLE, false), true));
        assertThrows(
                IllegalArgumentException.class,
                () -> ResearchTreePresentationContract.nextAction(null, true));
    }

    @Test
    void escapeLeavesFullscreenBeforeClosingTheBench() {
        assertEquals(
                ResearchTreePresentationContract.EscapeAction.EXIT_FULLSCREEN,
                ResearchTreePresentationContract.escapeAction(true));
        assertEquals(
                ResearchTreePresentationContract.EscapeAction.CLOSE_BENCH,
                ResearchTreePresentationContract.escapeAction(false));
    }

    @Test
    void branchesAreTheDefaultAndSidebarSelectionHasViewSpecificMeaning() {
        assertEquals(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                ResearchTreePresentationContract.DEFAULT_BROWSE_VIEW);
        assertEquals(
                ResearchTreePresentationContract.GroupSelectionAction.SHOW_GROUP,
                ResearchTreePresentationContract.groupSelectionAction(
                        ResearchTreePresentationContract.BrowseView.BRANCHES));
        assertEquals(
                ResearchTreePresentationContract.GroupSelectionAction.FOCUS_GROUP_REGION,
                ResearchTreePresentationContract.groupSelectionAction(
                        ResearchTreePresentationContract.BrowseView.ALL_WEAPONS));
        assertThrows(
                IllegalArgumentException.class,
                () -> ResearchTreePresentationContract.groupSelectionAction(null));
    }

    @Test
    void higherProgressionTiersAppearAboveTheirPrerequisites() {
        assertEquals(
                ResearchTreePresentationContract.ProgressionDirection.BOTTOM_TO_TOP,
                ResearchTreePresentationContract.PROGRESSION_DIRECTION);
        assertTrue(ResearchTreePresentationContract.tierAppearsAbove(1, 0));
        assertFalse(ResearchTreePresentationContract.tierAppearsAbove(0, 1));
        assertFalse(ResearchTreePresentationContract.tierAppearsAbove(2, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> ResearchTreePresentationContract.tierAppearsAbove(-1, 0));
    }

    @Test
    void authoredGroupsFollowTheExistingIdentityDisclosureBoundary() {
        assertEquals(
                ResearchTreePresentationContract.GroupDisclosure.OMITTED,
                ResearchTreePresentationContract.groupDisclosure(JournalVisibility.HIDDEN));
        assertEquals(
                ResearchTreePresentationContract.GroupDisclosure.UNDISCLOSED,
                ResearchTreePresentationContract.groupDisclosure(JournalVisibility.SILHOUETTE));
        assertEquals(
                ResearchTreePresentationContract.GroupDisclosure.UNDISCLOSED,
                ResearchTreePresentationContract.groupDisclosure(JournalVisibility.NAME));
        assertEquals(
                ResearchTreePresentationContract.GroupDisclosure.AUTHORED,
                ResearchTreePresentationContract.groupDisclosure(JournalVisibility.PREVIEW));
        assertEquals(
                ResearchTreePresentationContract.GroupDisclosure.AUTHORED,
                ResearchTreePresentationContract.groupDisclosure(JournalVisibility.FULL));
        assertThrows(
                IllegalArgumentException.class,
                () -> ResearchTreePresentationContract.groupDisclosure(null));
    }

    @Test
    void detailsUseHoverUntilThePlayerPinsTheSameCompactCard() {
        assertEquals(
                ResearchTreePresentationContract.DetailSurface.HOVER_TOOLTIP,
                ResearchTreePresentationContract.DEFAULT_DETAIL_SURFACE);
        assertEquals(
                ResearchTreePresentationContract.DetailSurface.HOVER_TOOLTIP,
                ResearchTreePresentationContract.detailSurface(false));
        assertEquals(
                ResearchTreePresentationContract.DetailSurface.PINNED_CARD,
                ResearchTreePresentationContract.detailSurface(true));
    }

    @Test
    void cardDetailAdaptsAtStableZoomThresholds() {
        assertEquals(
                ResearchTreePresentationContract.CardDetail.OVERVIEW,
                ResearchTreePresentationContract.cardDetail(0.01D));
        assertEquals(
                ResearchTreePresentationContract.CardDetail.COMPACT,
                ResearchTreePresentationContract.cardDetail(
                        ResearchTreePresentationContract.MIN_COMPACT_CARD_SCALE));
        assertEquals(
                ResearchTreePresentationContract.CardDetail.DETAILED,
                ResearchTreePresentationContract.cardDetail(
                        ResearchTreePresentationContract.MIN_DETAILED_CARD_SCALE));
        assertThrows(
                IllegalArgumentException.class,
                () -> ResearchTreePresentationContract.cardDetail(Double.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () -> ResearchTreePresentationContract.cardDetail(0.0D));
    }

    @Test
    void semanticZoomRetainsFocusedPathsWhileReducingGraphClutter() {
        for (ResearchTreePresentationContract.RelationshipRole role
                : ResearchTreePresentationContract.RelationshipRole.values()) {
            assertTrue(ResearchTreePresentationContract.edgeVisible(
                    ResearchTreePresentationContract.CardDetail.DETAILED, role));
        }
        assertFalse(ResearchTreePresentationContract.edgeVisible(
                ResearchTreePresentationContract.CardDetail.COMPACT,
                ResearchTreePresentationContract.RelationshipRole.UNRELATED));
        assertTrue(ResearchTreePresentationContract.edgeVisible(
                ResearchTreePresentationContract.CardDetail.COMPACT,
                ResearchTreePresentationContract.RelationshipRole.NEUTRAL));
        assertFalse(ResearchTreePresentationContract.edgeVisible(
                ResearchTreePresentationContract.CardDetail.OVERVIEW,
                ResearchTreePresentationContract.RelationshipRole.NEUTRAL));
        assertTrue(ResearchTreePresentationContract.edgeVisible(
                ResearchTreePresentationContract.CardDetail.OVERVIEW,
                ResearchTreePresentationContract.RelationshipRole.REQUIREMENT_PATH));
        assertTrue(ResearchTreePresentationContract.edgeVisible(
                ResearchTreePresentationContract.CardDetail.OVERVIEW,
                ResearchTreePresentationContract.RelationshipRole.DIRECT_UNLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTreePresentationContract.edgeVisible(
                        null, ResearchTreePresentationContract.RelationshipRole.NEUTRAL));
    }

    @Test
    void graphSpaceLabelsRevealStructureBeforeIndividualTiers() {
        assertEquals(
                ResearchTreePresentationContract.GraphLabels.NONE,
                ResearchTreePresentationContract.graphLabels(
                        ResearchTreePresentationContract.CardDetail.OVERVIEW));
        assertEquals(
                ResearchTreePresentationContract.GraphLabels.STRUCTURE,
                ResearchTreePresentationContract.graphLabels(
                        ResearchTreePresentationContract.CardDetail.COMPACT));
        assertEquals(
                ResearchTreePresentationContract.GraphLabels.FULL,
                ResearchTreePresentationContract.graphLabels(
                        ResearchTreePresentationContract.CardDetail.DETAILED));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTreePresentationContract.graphLabels(null));
    }

    private static ResearchTreePresentationContract.StatusSymbol expected(
            ResearchTreeGraph.Availability availability) {
        return switch (availability) {
            case REDACTED -> ResearchTreePresentationContract.StatusSymbol.UNKNOWN;
            case PREVIEW -> ResearchTreePresentationContract.StatusSymbol.PREVIEW;
            case LEARNED -> ResearchTreePresentationContract.StatusSymbol.LEARNED;
            case AVAILABLE -> ResearchTreePresentationContract.StatusSymbol.AVAILABLE;
            case DISCOVERY_REQUIRED -> ResearchTreePresentationContract.StatusSymbol.DISCOVERY_REQUIRED;
            case PREREQUISITES_REQUIRED ->
                    ResearchTreePresentationContract.StatusSymbol.PREREQUISITES_REQUIRED;
            case RESEARCH_DISABLED -> ResearchTreePresentationContract.StatusSymbol.RESEARCH_DISABLED;
            case COST_ABOVE_CAP -> ResearchTreePresentationContract.StatusSymbol.COST_ABOVE_CAP;
            case CONTENT_UNAVAILABLE -> ResearchTreePresentationContract.StatusSymbol.CONTENT_UNAVAILABLE;
        };
    }

    private static ResearchTreeGraph.Node redactedNode() {
        return new ResearchTreeGraph.Node(
                0,
                ResearchTreeGraph.redactedNodeId(0),
                ResearchTreeGraph.REDACTED_NAME_KEY,
                ResearchTreeGraph.REDACTED_ITEM_TYPE,
                ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                JournalVisibility.SILHOUETTE,
                false, false, false, 0, 0, 0, 0,
                ResearchTreeGraph.Availability.REDACTED);
    }

    private static ResearchTreeGraph.Node node(
            ResearchTreeGraph.Availability availability,
            boolean learned) {
        boolean policyEligible = availability == ResearchTreeGraph.Availability.AVAILABLE;
        JournalVisibility visibility = availability == ResearchTreeGraph.Availability.PREVIEW
                ? JournalVisibility.PREVIEW
                : JournalVisibility.FULL;
        return new ResearchTreeGraph.Node(
                0,
                id("test:target"),
                "name.target",
                "rifle",
                id("test:slot/target"),
                visibility,
                learned,
                false,
                policyEligible,
                availability == ResearchTreeGraph.Availability.RESEARCH_DISABLED ? 0 : 8,
                0,
                0,
                0,
                availability);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
