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
        assertEquals(0.25D, viewport.scale(), TOLERANCE);
        assertEquals(50, viewport.viewportX(200));
        assertEquals(40, viewport.viewportY(160));
    }

    @Test
    void manualZoomCanReachTheFifteenPercentOverviewFloor() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(300, 180, 2_000, 1_200);

        for (int index = 0; index < 10; index++) {
            viewport.zoomAt(-1.0D, 150, 90);
        }

        assertEquals(0.15D, ResearchTreeViewport.MIN_SCALE, TOLERANCE);
        assertEquals(ResearchTreeViewport.MIN_SCALE, viewport.scale(), TOLERANCE);
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
        viewport.configure(294, 116, 244, 1_160);

        viewport.fit();

        assertEquals(0.1D, viewport.scale(), TOLERANCE);
        assertTrue(viewport.viewportX(0) >= 0);
        assertTrue(viewport.viewportY(0) >= 0);
        assertTrue(viewport.viewportX(244) <= 294);
        assertTrue(viewport.viewportY(1_160) <= 116);

        viewport.zoomAt(-1.0D, 147, 58);
        assertEquals(0.1D, viewport.scale(), TOLERANCE);
        viewport.zoomAt(1.0D, 147, 58);
        assertEquals(ResearchTreeViewport.MIN_SCALE, viewport.scale(), TOLERANCE);
    }

    @Test
    void readableFitCentersWideContentWithoutShrinkingBelowItsPolicyFloor() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(300, 200, 2_000, 400);

        viewport.fitReadable(0.25D);

        assertEquals(0.25D, viewport.scale(), TOLERANCE);
        assertEquals(150, viewport.viewportX(1_000));
        assertEquals(100, viewport.viewportY(200));
        assertThrows(IllegalArgumentException.class, () -> viewport.fitReadable(-0.1D));
        assertThrows(IllegalArgumentException.class, () -> viewport.fitReadable(1.1D));
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
    void minimapCameraContractUsesTheUnobscuredClippedCanvasRectangle() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(300, 180, 900, 600);
        viewport.setSafeInsets(new ResearchTreeViewport.Insets(50, 20, 30, 10));

        assertEquals(new ResearchTreeViewport.ViewportSize(220, 150),
                viewport.unobscuredSize());
        viewport.focus(700, 450, 1, 1);
        ResearchTreeViewport.CanvasBounds visible = viewport.visibleCanvasBounds();

        assertTrue(visible.x() >= 0.0D);
        assertTrue(visible.y() >= 0.0D);
        assertTrue(visible.x() + visible.width() <= 900.0D);
        assertTrue(visible.y() + visible.height() <= 600.0D);
        assertEquals(220.0D, visible.width(), TOLERANCE);
        assertEquals(150.0D, visible.height(), TOLERANCE);
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

    @Test
    void fullscreenTargetsEaseToFocusWithoutChangingStaticViewportBehavior() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(300, 180, 900, 600);
        viewport.setAnimated(true);

        double initialPanX = viewport.panX();
        viewport.focus(600, 300, 32, 32);

        assertEquals(initialPanX, viewport.panX(), TOLERANCE);
        assertTrue(viewport.isAnimating());
        viewport.tick(1.0D / 120.0D);
        viewport.cancelAnimation();
        assertFalse(viewport.isAnimating());
        viewport.focus(600, 300, 32, 32);
        for (int tick = 0; tick < 48; tick++) {
            viewport.tick();
        }
        assertFalse(viewport.isAnimating());
        assertEquals(150, viewport.viewportX(616));
        assertEquals(90, viewport.viewportY(316));
    }

    @Test
    void safeInsetsFrameEdgeNodesAndBoundIntentionalOverscroll() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(300, 200, 600, 400);
        viewport.setSafeInsets(new ResearchTreeViewport.Insets(40, 30, 50, 20));

        viewport.focus(0, 0, 32, 32);
        assertEquals(40, viewport.viewportX(0));
        assertTrue(viewport.viewportY(0) >= 30);

        viewport.focus(568, 368, 32, 32);
        assertEquals(250, viewport.viewportX(600));
        assertEquals(180, viewport.viewportY(400));

        viewport.panByScreenDelta(10_000, 10_000);
        assertEquals(40, viewport.viewportX(0));
        assertEquals(30, viewport.viewportY(0));
        viewport.panByScreenDelta(-10_000, -10_000);
        assertEquals(250, viewport.viewportX(600));
        assertEquals(180, viewport.viewportY(400));
    }

    @Test
    void animatedFitAndZoomStoreTheirFinalProjectionCamera() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(300, 200, 600, 400);
        viewport.setSafeInsets(new ResearchTreeViewport.Insets(40, 30, 50, 20));
        viewport.setAnimated(true);

        viewport.fit();
        ResearchTreeViewport.Snapshot fitTarget = viewport.snapshot();
        assertEquals(0.35D, fitTarget.scale(), TOLERANCE);
        assertTrue(viewport.isAnimating());

        double cursorCanvasX = viewport.canvasX(40);
        double cursorCanvasY = viewport.canvasY(40);
        viewport.zoomAt(1.0D, 40, 40);
        ResearchTreeViewport.Snapshot zoomTarget = viewport.snapshot();
        assertEquals(cursorCanvasX, zoomTarget.panX() + 40 / zoomTarget.scale(), TOLERANCE);
        assertEquals(cursorCanvasY, zoomTarget.panY() + 40 / zoomTarget.scale(), TOLERANCE);
        assertThrows(IllegalArgumentException.class, () ->
                viewport.setSafeInsets(null));
        assertThrows(IllegalArgumentException.class, () ->
                viewport.tick(Double.NaN));
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchTreeViewport.Insets(-1, 0, 0, 0));
    }

    @Test
    void revealMovesOnlyEnoughToKeepKeyboardFocusInsideTheSafeArea() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(300, 200, 900, 600);
        viewport.setSafeInsets(new ResearchTreeViewport.Insets(40, 20, 60, 30));

        assertFalse(viewport.reveal(100, 80, 32, 32, 18));
        assertTrue(viewport.reveal(400, 250, 32, 32, 18));
        assertEquals(190, viewport.viewportX(400));
        assertEquals(120, viewport.viewportY(250));
        assertEquals(222, viewport.viewportX(432));
        assertEquals(152, viewport.viewportY(282));

        ResearchTreeViewport.Snapshot stable = viewport.snapshot();
        assertFalse(viewport.reveal(400, 250, 32, 32, 18));
        assertEquals(stable, viewport.snapshot());
        assertThrows(IllegalArgumentException.class, () ->
                viewport.reveal(0, 0, 10, 10, -1));
    }

    @Test
    void repeatedAnimatedRevealUsesTheDestinationCamera() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(200, 120, 800, 400);
        viewport.setAnimated(true);

        assertTrue(viewport.reveal(350, 100, 32, 32, 12));
        ResearchTreeViewport.Snapshot firstTarget = viewport.snapshot();
        assertTrue(viewport.reveal(600, 100, 32, 32, 12));
        ResearchTreeViewport.Snapshot secondTarget = viewport.snapshot();

        assertTrue(secondTarget.panX() > firstTarget.panX());
        viewport.finishAnimation();
        assertTrue(viewport.viewportX(600) >= 12);
        assertTrue(viewport.viewportX(632) <= 188);
    }
}
