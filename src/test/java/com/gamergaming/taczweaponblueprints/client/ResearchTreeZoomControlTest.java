package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ResearchTreeZoomControlTest {
    @Test void sceneAndLegacyControlsRespectTheirOwnFloors() {
        var viewport = new ResearchTreeViewport();
        viewport.configure(320,240,10000,10000);
        viewport.restore(new ResearchTreeViewport.Snapshot(0,0,.05));
        assertFalse(viewport.canZoom(-1,false));
        assertTrue(viewport.canZoom(-1,true));
        viewport.zoomProportional(-1,160,120);
        assertTrue(viewport.scale()<.05);
        viewport.restore(new ResearchTreeViewport.Snapshot(0,0,Double.MIN_NORMAL));
        assertFalse(viewport.canZoom(-1,true));
        assertTrue(viewport.canZoom(1,true));
        assertFalse(viewport.canZoom(0,true));
    }

    @Test void controlsUsePendingZoomWhileAnimationIsStillRunning() {
        var viewport = new ResearchTreeViewport();
        viewport.configure(320,240,10000,10000);
        viewport.setAnimated(true);
        viewport.zoomProportional(8,160,120);
        assertTrue(viewport.isAnimating());
        assertTrue(viewport.scale()<ResearchTreeViewport.MAX_SCALE);
        assertFalse(viewport.canZoom(1,true));
        assertTrue(viewport.canZoom(-1,true));
        viewport.finishAnimation();
        assertEquals(ResearchTreeViewport.MAX_SCALE,viewport.scale());
    }

    @Test void invalidLegacyPointerInputCannotPoisonTheCamera() {
        var viewport = new ResearchTreeViewport();
        viewport.configure(320,240,1000,1000);
        var before = viewport.snapshot();
        assertThrows(IllegalArgumentException.class,()->viewport.zoomAt(Double.NaN,10,10));
        assertThrows(IllegalArgumentException.class,()->viewport.zoomAt(1,Double.POSITIVE_INFINITY,10));
        assertThrows(IllegalArgumentException.class,()->viewport.zoomAt(1,10,Double.NaN));
        assertEquals(before,viewport.snapshot());
    }
}
