package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ResearchTreeViewportTest {
    private static final double TOLERANCE = 0.0001D;

    @Test
    void centersCanvasesSmallerThanTheViewport() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();

        viewport.configure(300, 120, 100, 40);

        assertEquals(-100.0D, viewport.panX(), TOLERANCE);
        assertEquals(-40.0D, viewport.panY(), TOLERANCE);
        assertEquals(100, viewport.viewportX(0));
        assertEquals(40, viewport.viewportY(0));
    }

    @Test
    void panningCannotMovePastCanvasBounds() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(100, 100, 300, 250);

        viewport.panByScreenDelta(-1_000, -1_000);
        assertEquals(200.0D, viewport.panX(), TOLERANCE);
        assertEquals(150.0D, viewport.panY(), TOLERANCE);

        viewport.panByScreenDelta(1_000, 1_000);
        assertEquals(0.0D, viewport.panX(), TOLERANCE);
        assertEquals(0.0D, viewport.panY(), TOLERANCE);
    }

    @Test
    void zoomKeepsThePointUnderTheCursorStable() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(100, 100, 500, 500);
        viewport.panByScreenDelta(-80, -60);
        double beforeX = viewport.canvasX(35);
        double beforeY = viewport.canvasY(45);

        viewport.zoomAt(1.0D, 35, 45);

        assertEquals(1.25D, viewport.scale(), TOLERANCE);
        assertEquals(beforeX, viewport.canvasX(35), TOLERANCE);
        assertEquals(beforeY, viewport.canvasY(45), TOLERANCE);
    }

    @Test
    void focusCentersANodeAndFitUsesSupportedScaleBounds() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(100, 80, 400, 320);

        viewport.focus(180, 130, 20, 20);
        assertEquals(50, viewport.viewportX(190));
        assertEquals(40, viewport.viewportY(140));

        viewport.fit();
        assertEquals(ResearchTreeViewport.MIN_SCALE, viewport.scale(), TOLERANCE);
        assertEquals(50, viewport.viewportX(200));
        assertEquals(40, viewport.viewportY(160));
    }

    @Test
    void fitShowsTheCompletePackagedTreeCanvas() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(294, 116, 584, 360);

        viewport.fit();

        assertEquals(116.0D / 360.0D, viewport.scale(), TOLERANCE);
        assertTrue(viewport.viewportX(0) >= 0);
        assertTrue(viewport.viewportY(0) >= 0);
        assertTrue(viewport.viewportX(584) <= 294);
        assertTrue(viewport.viewportY(360) <= 116);
    }

    @Test
    void fitRegionFramesOneCategoryWithoutLeavingCanvasBounds() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(300, 180, 900, 600);

        viewport.fit(300, 0, 200, 600);

        assertEquals(0.3D, viewport.scale(), TOLERANCE);
        assertTrue(viewport.viewportX(300) >= 0);
        assertTrue(viewport.viewportX(500) <= 300);
        assertThrows(IllegalArgumentException.class, () ->
                viewport.fit(0, 0, 0, 100));
        assertThrows(IllegalArgumentException.class, () ->
                viewport.fit(850, 0, 100, 100));
    }

    @Test
    void fitCanUseAnOverviewScaleBelowTheInteractiveZoomFloor() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(294, 116, 244, 592);

        viewport.fit();

        assertEquals(116.0D / 592.0D, viewport.scale(), TOLERANCE);
        assertTrue(viewport.viewportX(0) >= 0);
        assertTrue(viewport.viewportY(0) >= 0);
        assertTrue(viewport.viewportX(244) <= 294);
        assertTrue(viewport.viewportY(592) <= 116);

        viewport.zoomAt(-1.0D, 147, 58);
        assertEquals(116.0D / 592.0D, viewport.scale(), TOLERANCE);
        viewport.zoomAt(1.0D, 147, 58);
        assertEquals(ResearchTreeViewport.MIN_SCALE, viewport.scale(), TOLERANCE);
    }

    @Test
    void intersectionUsesTheCurrentVisibleCanvasRectangle() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(100, 100, 300, 300);
        viewport.panByScreenDelta(-100, -100);

        assertTrue(viewport.intersects(120, 120, 10, 10));
        assertFalse(viewport.intersects(10, 10, 10, 10));
        assertFalse(viewport.intersects(220, 220, 10, 10));
    }

    @Test
    void replacingCanvasCanFitNewTopologyWithoutChangingViewportDimensions() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(200, 100, 300, 200);
        viewport.zoomAt(1.0D, 100, 50);

        viewport.replaceCanvas(800, 400, true);

        assertEquals(0.25D, viewport.scale(), TOLERANCE);
        assertTrue(viewport.viewportX(0) >= 0);
        assertTrue(viewport.viewportY(0) >= 0);
        assertTrue(viewport.viewportX(800) <= 200);
        assertTrue(viewport.viewportY(400) <= 100);
    }

    @Test
    void snapshotRestoresProjectionSpecificPanAndZoomWithinCurrentBounds() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(120, 80, 600, 400);
        viewport.panByScreenDelta(-140, -90);
        viewport.zoomAt(1.0D, 60, 40);
        ResearchTreeViewport.Snapshot saved = viewport.snapshot();

        viewport.fit();
        viewport.restore(saved);

        assertEquals(saved.scale(), viewport.scale(), TOLERANCE);
        assertEquals(saved.panX(), viewport.panX(), TOLERANCE);
        assertEquals(saved.panY(), viewport.panY(), TOLERANCE);
        assertThrows(IllegalArgumentException.class, () -> viewport.restore(null));
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchTreeViewport.Snapshot(0.0D, 0.0D, Double.NaN));
    }
}
