package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeScreenLayout.Rect;

class ResearchTreeLandmarkPlacementTest {
    private final Rect view = new Rect(0,0,500,400);
    @Test void validAnchorFollowsPanAndZoomInsteadOfReturningToTheFirstSlot() {
        var placement = new ResearchTreeLandmarkPlacement();
        var region = new Rect(20,20,300,300);
        var obstacle = new Rect(20,20,110,50);
        var first = placement.place("branch",view,region,20,20,1,100,15,List.of(obstacle)).orElseThrow();
        var panned = placement.place("branch",view,new Rect(30,30,300,300),30,30,1,100,15,List.of()).orElseThrow();
        assertEquals(first.x()+10,panned.x()); assertEquals(first.y()+10,panned.y());
        var zoomed = placement.place("branch",view,new Rect(30,30,360,360),30,30,1.2,100,15,List.of()).orElseThrow();
        assertEquals(30+Math.round((first.x()-20)*1.2),zoomed.x());
        assertEquals(30+Math.round((first.y()-20)*1.2),zoomed.y());
    }
    @Test void occludedAnchorMovesOnlyToASafeSlotAndDisappearsWhenNoneExists() {
        var placement = new ResearchTreeLandmarkPlacement(); var region = new Rect(20,20,300,300);
        var first = placement.place("branch",view,region,20,20,1,100,15,List.of()).orElseThrow();
        var next = placement.place("branch",view,region,20,20,1,100,15,List.of(first)).orElseThrow();
        assertFalse(first.overlaps(next)); assertTrue(region.contains(next));
        assertTrue(placement.place("branch",view,region,20,20,1,100,15,List.of(region)).isEmpty());
    }
    @Test void hiddenLabelsNeedMoreSpaceToReturnThanVisibleLabelsNeedToRemain() {
        var placement = new ResearchTreeLandmarkPlacement();
        var wide = new Rect(20,20,114,60); var narrow = new Rect(20,20,108,60);
        assertTrue(placement.place("branch",view,wide,20,20,1,100,15,List.of()).isPresent());
        // A fresh or hidden title cannot squeeze into a gap at its exact fit threshold.
        assertTrue(new ResearchTreeLandmarkPlacement().place("branch",view,narrow,20,20,1,100,15,List.of()).isEmpty());
        var near = new Rect(20,20,110,60);
        assertTrue(placement.place("branch",view,near,20,20,1,100,15,List.of()).isPresent());
        placement.hideAll();
        assertTrue(placement.place("branch",view,near,20,20,1,100,15,List.of()).isEmpty());
        assertTrue(placement.place("branch",view,wide,20,20,1,100,15,List.of()).isPresent());
    }
    @Test void offscreenAndResizedLabelsRemainWithinTheirOwnVisibleFootprint() {
        var placement = new ResearchTreeLandmarkPlacement();
        var footprint = new Rect(0,20,200,240);
        var first = placement.place("branch",view,footprint,-200,20,.5,100,15,List.of()).orElseThrow();
        assertTrue(footprint.contains(first));
        var translated = placement.place("branch",view,footprint,-200,20,.5,160,15,List.of()).orElseThrow();
        assertTrue(footprint.contains(translated));
        assertTrue(placement.place("branch",view,footprint,-200,20,.5,220,15,List.of()).isEmpty());
    }
}
