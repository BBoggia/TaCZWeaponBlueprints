package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ResearchTreeCameraInputTest {
    private ResearchTreeViewport viewport() {
        var v = new ResearchTreeViewport();
        v.configure(500, 300, 10000, 10000);
        v.restore(new ResearchTreeViewport.Snapshot(2000, 2000, .8));
        v.setAnimated(true);
        return v;
    }
    @Test void zoomAnchorStaysExactAcrossFrameRatesReversalsAndRetargeting() {
        for (double dt : new double[]{1.0/144, 1.0/60, 1.0/24, .1}) {
            var v = viewport();
            for (double direction : new double[]{1, 1, -.25, -1, 2, -2}) {
                double x = v.canvasX(137.25), y = v.canvasY(91.75);
                double before = v.scale();
                v.zoomProportional(direction, 137.25, 91.75);
                assertEquals(before * Math.pow(1.18, direction), v.snapshot().scale(), 1e-9);
                for (int frame = 0; frame < 7; frame++) {
                    v.tick(dt);
                    assertEquals(137.25, (x - v.panX()) * v.scale(), 1e-8);
                    assertEquals(91.75, (y - v.panY()) * v.scale(), 1e-8);
                }
            }
        }
    }
    @Test void dragCancelsZoomAndReducedMotionCompletesImmediately() {
        var v = viewport(); v.zoomProportional(1, 80, 90); v.tick(.02);
        v.panByScreenDelta(12, -4);
        var rendered = v.renderedSnapshot(); v.tick(.1);
        assertEquals(rendered, v.renderedSnapshot()); assertFalse(v.isAnimating());
        v.zoomProportional(-1, 80, 90); v.setAnimated(false);
        assertEquals(v.snapshot(), v.renderedSnapshot()); assertFalse(v.isAnimating());
    }
    @Test void absoluteScreenAnchorSurvivesChangedOriginAndSize() {
        var v = viewport(); double x = v.canvasX(145), y = v.canvasY(99);
        v.configureScreenAnchored(36,48,52,60,280,220,10000,10000);
        assertEquals(181, 52 + v.viewportX(x)); assertEquals(147, 60 + v.viewportY(y));
        v.configureScreenAnchored(52,60,36,48,500,300,10000,10000);
        assertEquals(181,36+v.viewportX(x)); assertEquals(147,48+v.viewportY(y));
    }
    @Test void scrollRetainsFractionsAndReversals() {
        var a = new ResearchTreeScrollAccumulator();
        assertEquals(0,a.consume(.3)); assertEquals(0,a.consume(.3));
        assertEquals(0,a.consume(-.2)); assertEquals(1,a.consume(.7));
        assertEquals(0,a.consume(-.3)); assertEquals(-1,a.consume(-.9));
        assertThrows(IllegalArgumentException.class,()->a.consume(Double.NaN));
    }
    @Test void resizeDuringZoomKeepsTheShiftedPointerAnchorAndFinishes() {
        var v = viewport();
        double worldX = v.canvasX(137), worldY = v.canvasY(91);
        v.zoomProportional(1, 137, 91); v.tick(.02);
        v.configureAnchored(400, 240, 10000, 10000);
        for (int frame = 0; frame < 120; frame++) {
            v.tick(1.0 / 60);
            assertEquals(87, (worldX - v.panX()) * v.scale(), 1e-8);
            assertEquals(61, (worldY - v.panY()) * v.scale(), 1e-8);
        }
        assertFalse(v.isAnimating());
    }
    @Test void changedSearchQueryKeepsTheEnterTargetVisible() {
        var search = new ResearchTreeSearchController();
        var nodes = ResearchTreeUxPhaseZeroFixture.everyAvailability().nodes();
        search.update("node", nodes, n -> "node");
        search.selectNext(5); search.window(3);
        var selected = search.commit();
        search.update("nod", nodes, n -> "node");
        assertEquals(selected, search.commit());
        assertTrue(search.window(3).stream().anyMatch(ResearchTreeSearchController.Result::active));
    }
    @Test void scrollingSearchWindowDoesNotChangeCommittedTarget() {
        var search = new ResearchTreeSearchController();
        search.update("node", ResearchTreeUxPhaseZeroFixture.everyAvailability().nodes(), n -> "node");
        var target = search.commit(); search.window(3);
        for(int i=0;i<4;i++) search.scroll(-.25,3);
        assertEquals(1,search.window(3).get(0).index()); assertEquals(target,search.commit());
        search.page(1,3); assertEquals(target,search.commit());
        search.page(-1,3); assertEquals(1,search.window(3).get(0).index());
    }
}
