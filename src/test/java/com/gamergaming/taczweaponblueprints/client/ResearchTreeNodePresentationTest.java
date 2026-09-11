package com.gamergaming.taczweaponblueprints.client;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchTreeNodePresentationTest {
    private static final ResearchTreeScene.Bounds BODY = new ResearchTreeScene.Bounds(10,20,96,44);
    private static ResearchTreeNodePresentation resolve(ResearchTreeNodePresentation previous, double scale, int lines) {
        return ResearchTreeNodePresentation.resolve(previous == null ? null : previous.state(), BODY, scale, 9,
                (width, capacity) -> java.util.Collections.nCopies(Math.min(lines,capacity),"Name"));
    }

    @Test void artworkSurvivesTheFormerCutoffAndDistantZoom() {
        var near = resolve(null,1,1);
        var middle = resolve(near,.47,1);
        assertTrue(middle.channels().artwork()); assertFalse(middle.named());
        assertTrue(resolve(middle,.20,1).channels().artwork());
        assertFalse(resolve(middle,.15,1).channels().artwork());
    }

    @Test void iconTransitionHasABufferInBothDimensions() {
        var present = ResearchTreeDetailPolicy.channels(ResearchTreeDetailPolicy.Channels.EMPTY,18,8);
        assertTrue(ResearchTreeDetailPolicy.channels(present,16,7).artwork());
        assertFalse(ResearchTreeDetailPolicy.channels(present,15.9,7).artwork());
        assertFalse(ResearchTreeDetailPolicy.channels(present,50,6).artwork());
        assertFalse(ResearchTreeDetailPolicy.channels(ResearchTreeDetailPolicy.Channels.EMPTY,17,7.5).artwork());
    }

    @Test void aHiddenOrEmptyNameReservesNoArtworkSpace() {
        for (double scale : new double[]{.2,.5,.8,1,1.5,2}) {
            var empty = resolve(null,scale,0);
            assertFalse(empty.named()); assertEquals(33,empty.artwork().height());
            assertEquals(86,empty.artwork().width()); assertTrue(BODY.contains(empty.artwork()));
        }
        assertEquals(resolve(null,1,0).artwork(),resolve(null,.5,1).artwork());
    }

    @Test void nameUsesActualLineCountAndKeepsMostOfTheImageArea() {
        var shortName = resolve(null,1.5,1); var longName = resolve(null,1.5,2);
        assertEquals(1,shortName.nameLines().size()); assertEquals(2,longName.nameLines().size());
        assertTrue(shortName.artwork().height()>longName.artwork().height());
        assertTrue(longName.artwork().height()>33*.57);
        assertEquals(2,(longName.nameBounds().y()-longName.artwork().bottom())*1.5,1e-9);
    }

    @Test void firstAndSecondNameLinesHaveTransitionBuffers() {
        assertFalse(resolve(null,.8,1).named());
        var one = resolve(null,.84,2); assertEquals(1,one.nameLines().size());
        assertTrue(resolve(one,.8,1).named());
        assertFalse(resolve(one,.76,1).named());
        assertEquals(1,resolve(null,1.42,2).nameLines().size());
        var two = resolve(null,1.45,2); assertEquals(2,two.nameLines().size());
        assertEquals(2,resolve(two,1.42,2).nameLines().size());
        assertEquals(1,resolve(two,1.35,2).nameLines().size());
    }

    @Test void directZoomJumpsResolveImmediately() {
        var near = resolve(null,2,2); var distant = resolve(near,.05,2);
        assertFalse(distant.channels().artwork()); assertFalse(distant.named());
        assertEquals(2,resolve(distant,2,2).nameLines().size());
    }

    @Test void eachNodeUsesItsOwnProjectedSize() {
        assertFalse(resolve(null,.5,1).named());
        var large = ResearchTreeNodePresentation.resolve(null,new ResearchTreeScene.Bounds(0,0,192,88),.5,9,
                (width,capacity) -> List.of("Name"));
        assertTrue(large.named());
    }

    @Test void nameAndCornerMarksHaveSeparateSpaceAtEveryReadableZoom() {
        ResearchTreeNodePresentation previous = null;
        for (int step=20;step<=200;step++) {
            var layout = resolve(previous,step/100.,2); previous=layout;
            assertTrue(BODY.contains(layout.artwork()));
            for (var mark : List.of(layout.marks().status(),layout.marks().progression(),layout.marks().inspected(),layout.marks().tracked())) {
                assertTrue(BODY.contains(mark));
                if (layout.named()) assertFalse(ResearchTreeRoutingIndex.intersects(layout.nameBounds(),mark));
            }
            if (layout.named()) {
                assertTrue(BODY.contains(layout.nameBounds()));
                assertTrue(layout.artwork().bottom()<layout.nameBounds().y());
                assertEquals(layout.nameLines().size()*9,layout.nameBounds().height()*step/100.,1e-9);
            }
            var fitted=ResearchTreeSceneView.fitArt(400,80,layout.artwork());
            assertTrue(layout.artwork().contains(fitted)); assertEquals(5,fitted.width()/fitted.height(),1e-9);
        }
    }

    @Test void wrappingReceivesTheAvailableWidthAndLineBudget() {
        var layout=ResearchTreeNodePresentation.resolve(null,BODY,1,9,(width,capacity) -> {
            assertEquals(72,width); assertEquals(1,capacity); return List.of("Short");
        });
        assertTrue(layout.named());
    }

    @Test void smallCustomTilesKeepTheirAllocationsInsideTheBodyAndOmitCrowdedMarks() {
        for (int width : new int[]{1,8,16,24,96}) for (int height : new int[]{1,8,16,24,44}) {
            var body = new ResearchTreeScene.Bounds(-20,-30,width,height);
            var layout = ResearchTreeNodePresentation.resolve(null,body,1.5,9,(w,n) -> List.of("Name"));
            assertTrue(body.contains(layout.artwork())); assertTrue(body.contains(layout.nameBounds()));
            for (var mark : List.of(layout.marks().status(),layout.marks().progression(),layout.marks().inspected(),layout.marks().tracked()))
                assertTrue(body.contains(mark));
            if (width<24 || height<24) assertFalse(layout.channels().stateMarks());
            if (layout.artwork().width()==0 || layout.artwork().height()==0) assertFalse(layout.channels().artwork());
        }
    }

    @Test void wrappedPackNamesCarryColorAndStylesUntilResetOrReplaced() {
        assertEquals("\u00a73",ResearchTreeSceneRenderer.continuationFormat("\u00a73SAI | GRY LITE"));
        assertEquals("\u00a73\u00a7l",ResearchTreeSceneRenderer.continuationFormat("\u00a73SAI \u00a7lGRY"));
        assertEquals("\u00a7a",ResearchTreeSceneRenderer.continuationFormat("\u00a73\u00a7lSAI \u00a7aGRY"));
        assertEquals("",ResearchTreeSceneRenderer.continuationFormat("\u00a73SAI \u00a7rGRY"));
        assertEquals("",ResearchTreeSceneRenderer.continuationFormat("plain name"));
    }
}
