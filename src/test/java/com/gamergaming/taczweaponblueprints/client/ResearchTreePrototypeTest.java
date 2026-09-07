package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ResearchTreePrototypeTest {
    @Test void routingTrackSpacingPreservesTheDefaultAndRejectsUnsafeClearance() {
        assertEquals(3, new ResearchTreeScenePolicy.RegionMetrics(40, 12, 12, 80, 16, 10).routingTrackSpacing());
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeScenePolicy.RegionMetrics(40, 12, 12, 80, 16, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeScenePolicy.RegionMetrics(40, 12, 12, 80, 16, 10, 7));
    }

    static Stream<Arguments> sizes() {
        return Stream.of(320, 360, 494, 854, 1600).flatMap(w -> Stream.of(240, 273, 480, 900).map(h -> Arguments.of(w, h)));
    }
    @ParameterizedTest @MethodSource("sizes")
    void inspectorAndControlsLeaveAUsableDisjointCanvas(int width, int height) {
        var layout = ResearchTreePrototypeModel.layout(width, height);
        var screen = new ResearchTreeScreenLayout.Rect(0, 0, width, height);
        assertTrue(screen.contains(layout.canvas())); assertTrue(screen.contains(layout.inspector()));
        assertFalse(layout.canvas().overlaps(layout.inspector()));
        assertTrue(layout.canvas().width() >= 160); assertTrue(layout.canvas().height() >= 24);
        assertTrue(layout.inspector().bottom() <= height - 18);
    }

    @Test void proportionalZoomKeepsThePointerAnchorAndResizeKeepsTheWorldCenter() {
        var camera = new ResearchTreePrototypeModel.Camera();
        camera.configure(new ResearchTreeScreenLayout.Rect(10, 20, 400, 300));
        camera.focus(new ResearchTreeScene.Bounds(-100, 1000, 80, 40), 0.7);
        double x = camera.worldX(80), y = camera.worldY(200);
        camera.zoom(1, 80, 200);
        assertEquals(0.826, camera.scale(), 1e-9);
        assertEquals(x, camera.worldX(80), 1e-9); assertEquals(y, camera.worldY(200), 1e-9);
        double centerX = camera.worldX(210), centerY = camera.worldY(170);
        camera.configure(new ResearchTreeScreenLayout.Rect(20, 30, 200, 180));
        assertEquals(centerX, camera.worldX(120), 1e-9); assertEquals(centerY, camera.worldY(120), 1e-9);
        assertEquals(80, camera.screenX(camera.worldX(80)), 1e-9);
    }

    @Test void extremeZoomAndPanStayInsideTheSceneQueryCoordinateBudget() {
        var camera = new ResearchTreePrototypeModel.Camera();
        camera.configure(new ResearchTreeScreenLayout.Rect(10, 20, 800, 500));
        for (int i = 0; i < 300; i++) { camera.zoom(-8, 10, 20); camera.pan(100_000, -100_000); assertDoesNotThrow(camera::visible); }
    }

    @Test void detailHysteresisPreventsBoundaryFlicker() {
        var detail = ResearchTreePrototypeModel.Detail.BROWSE;
        for (double scale : new double[] {0.52, 0.49, 0.51, 0.48}) assertEquals(detail, ResearchTreePrototypeModel.detail(detail, scale));
        detail = ResearchTreePrototypeModel.detail(detail, 0.47);
        assertEquals(ResearchTreePrototypeModel.Detail.OVERVIEW, detail);
        for (double scale : new double[] {0.49, 0.55, 0.58}) assertEquals(detail, ResearchTreePrototypeModel.detail(detail, scale));
        assertEquals(ResearchTreePrototypeModel.Detail.BROWSE, ResearchTreePrototypeModel.detail(detail, 0.59));
    }

    @Test void realBodiesWinOverExpandedHitPaddingAndArtKeepsItsAspect() {
        var a = ResearchTreeSceneGeometry.node(ResearchTechTreeClientFixture.WEAPON_ROOT, 10, 10, new ResearchTreeScenePolicy.NodeMetrics(80, 40, 3));
        var b = ResearchTreeSceneGeometry.node(ResearchTechTreeClientFixture.WEAPON_UPGRADE, 92, 10, new ResearchTreeScenePolicy.NodeMetrics(80, 40, 3));
        assertEquals(b.id(), ResearchTreePrototypeModel.hit(List.of(a, b), 93, 30, 0.1).orElseThrow().id());
        var fit = ResearchTreePrototypeModel.fitArt(200, 40, new ResearchTreeScene.Bounds(0, 0, 60, 30));
        assertEquals(60, fit.width()); assertEquals(12, fit.height()); assertEquals(9, fit.y());
    }

    static Stream<Arguments> fixtures() {
        return Stream.concat(ResearchTreeBaselineFixtures.structural().stream(), ResearchTreeBaselineFixtures.routing().stream())
                .flatMap(f -> Stream.of(ResearchTreePrototypeModel.Treatment.values()).map(t -> Arguments.of(f, t)));
    }
    @ParameterizedTest(name = "prototype profile {index}") @MethodSource("fixtures")
    void bothMeasuredProfilesRouteTheStructuralFixtures(ResearchTreeBaselineFixtures.Fixture fixture, ResearchTreePrototypeModel.Treatment treatment) {
        var metrics = ResearchTreePrototypeModel.metrics(treatment, 9, 120);
        var policy = new ResearchTreeScenePolicy(new ResearchTechTreeLayoutPolicy(20, 18, 8, 20, 4, 1000, 6, 6), 300, metrics,
                "prototype-test", new ResearchTreeScenePolicy.RegionMetrics(44, 12, 16, 80, 16, 10, 2));
        var original = ResearchTreeBranchFixtures.input(fixture, 300);
        var input = fixture.input().projections().isEmpty() ? ResearchTreeSceneInput.empty(original.topology().domain(), policy)
                : ResearchTreeSceneInput.from(fixture.input().projections().get(0).replay(), policy).withBranchSeeds(original.topology().branchSeeds());
        var engine = new ResearchTreeBranchSceneEngine();
        assertDoesNotThrow(() -> ResearchTreeSceneValidator.validate(input, engine.revision(), engine.layout(input)));
    }
}
