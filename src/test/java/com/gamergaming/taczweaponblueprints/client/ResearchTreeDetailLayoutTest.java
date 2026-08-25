package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ResearchTreeDetailLayoutTest {
    @Test
    void compactRelationshipSlotsStayInsideDetailsAndOutsidePrepareButton() {
        ResearchTreeScreenLayout.Rect details = ResearchTreeScreenLayout.compact().details();
        List<ResearchTreeDetailLayout.RelationSlot> slots = ResearchTreeDetailLayout.compact(details);

        assertEquals(3, slots.size());
        assertEquals(2, slots.stream()
                .filter(slot -> slot.kind() == ResearchTreeDetailLayout.RelationKind.REQUIREMENT)
                .count());
        assertEquals(1, slots.stream()
                .filter(slot -> slot.kind() == ResearchTreeDetailLayout.RelationKind.UNLOCK)
                .count());
        ResearchTreeScreenLayout.Rect prepare = new ResearchTreeScreenLayout.Rect(
                232, 199, 64, 20);
        for (int left = 0; left < slots.size(); left++) {
            assertTrue(details.contains(slots.get(left).bounds()));
            assertFalse(slots.get(left).bounds().overlaps(prepare));
            for (int right = left + 1; right < slots.size(); right++) {
                assertFalse(slots.get(left).bounds().overlaps(slots.get(right).bounds()));
            }
        }
    }

    @Test
    void compactHitTestingUsesHalfOpenSlotBounds() {
        ResearchTreeScreenLayout.Rect details = ResearchTreeScreenLayout.compact().details();
        ResearchTreeDetailLayout.RelationSlot first =
                ResearchTreeDetailLayout.compact(details).get(0);

        assertEquals(first, ResearchTreeDetailLayout.compactSlotAt(
                details, first.bounds().x(), first.bounds().y()).orElseThrow());
        assertTrue(ResearchTreeDetailLayout.compactSlotAt(
                details, first.bounds().right(), first.bounds().y()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ResearchTreeDetailLayout.compact(
                new ResearchTreeScreenLayout.Rect(0, 0, 200, 43)));
    }

    @Test
    void relationshipTargetsUseTheSameSlotsAsRenderingAndHitTesting() {
        List<ResearchTreeDetailLayout.RelationSlot> slots =
                ResearchTreeDetailLayout.compact(ResearchTreeScreenLayout.compact().details());
        List<String> requirements = List.of("first", "second", "overflow");
        List<String> unlocks = List.of("next", "later");

        assertEquals("first", ResearchTreeDetailLayout.relationTarget(
                slots.get(0), requirements, unlocks).orElseThrow());
        assertEquals("second", ResearchTreeDetailLayout.relationTarget(
                slots.get(1), requirements, unlocks).orElseThrow());
        assertEquals("next", ResearchTreeDetailLayout.relationTarget(
                slots.get(2), requirements, unlocks).orElseThrow());
        assertTrue(ResearchTreeDetailLayout.relationTarget(
                slots.get(1), List.of("only"), unlocks).isEmpty());
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeDetailLayout.relationTarget(null, requirements, unlocks));
    }

    @Test
    void fullscreenUsesTooltipsInsteadOfPermanentRelationshipRows() {
        for (ResearchTreeScreenLayout.Layout layout : List.of(
                ResearchTreeScreenLayout.fullscreen(854, 480, true),
                ResearchTreeScreenLayout.fullscreen(640, 360, true),
                ResearchTreeScreenLayout.fullscreen(320, 240, true))) {
            List<ResearchTreeDetailLayout.RelationSlot> slots =
                    ResearchTreeDetailLayout.fullscreen(layout);

            assertTrue(slots.isEmpty());
            ResearchTreeScreenLayout.Rect action =
                    ResearchTreeDetailLayout.primaryAction(layout).orElseThrow();
            assertTrue(action.inside(layout.screenWidth(), layout.screenHeight()));
            assertFalse(action.overlaps(layout.toolbar()));
            assertTrue(ResearchTreeDetailLayout.drawerToggle(layout).isEmpty());
        }
        ResearchTreeScreenLayout.Layout collapsed =
                ResearchTreeScreenLayout.fullscreen(320, 240, false);
        assertTrue(ResearchTreeDetailLayout.fullscreen(collapsed).isEmpty());
        assertTrue(ResearchTreeDetailLayout.primaryAction(collapsed).isPresent());
        assertTrue(ResearchTreeDetailLayout.drawerToggle(collapsed).isEmpty());
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeDetailLayout.fullscreen(ResearchTreeScreenLayout.compact()));
    }

    @Test
    void fullscreenHasNoRelationshipSlotHitTargets() {
        ResearchTreeScreenLayout.Layout layout =
                ResearchTreeScreenLayout.fullscreen(854, 480, true);
        List<ResearchTreeDetailLayout.RelationSlot> slots =
                ResearchTreeDetailLayout.fullscreen(layout);

        assertTrue(slots.isEmpty());
        assertTrue(ResearchTreeDetailLayout.slotAt(slots, 100, 100).isEmpty());
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeDetailLayout.slotAt(null, 0, 0));
    }

    @Test
    void primaryActionsAndDrawerTogglesFollowPresentationMode() {
        assertEquals(
                new ResearchTreeScreenLayout.Rect(232, 199, 64, 20),
                ResearchTreeDetailLayout.primaryAction(
                        ResearchTreeScreenLayout.compact()).orElseThrow());
        assertTrue(ResearchTreeDetailLayout.drawerToggle(
                ResearchTreeScreenLayout.compact()).isEmpty());
        assertTrue(ResearchTreeDetailLayout.drawerToggle(
                ResearchTreeScreenLayout.fullscreen(640, 360, true)).isEmpty());
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeDetailLayout.primaryAction(null));
    }
}
