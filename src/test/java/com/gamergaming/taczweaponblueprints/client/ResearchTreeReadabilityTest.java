package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.client.ResearchTreePresentationContract.RelationshipRole;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeScreenLayout.Rect;

class ResearchTreeReadabilityTest {
    @Test void detailChannelsHaveIndependentThresholdsAndHysteresis() {
        var empty = ResearchTreeDetailPolicy.Channels.EMPTY;
        var icons = ResearchTreeDetailPolicy.channels(empty, 20, 9);
        assertTrue(icons.artwork()); assertFalse(icons.names()); assertFalse(icons.stateMarks()); assertFalse(icons.junctions());
        var marks = ResearchTreeDetailPolicy.channels(icons, 40, 18);
        assertTrue(marks.stateMarks()); assertFalse(marks.names()); assertFalse(marks.junctions());
        var junctions = ResearchTreeDetailPolicy.channels(marks, 65, 29);
        assertTrue(junctions.junctions()); assertFalse(junctions.names());
        var names = ResearchTreeDetailPolicy.channels(junctions, 80, 37);
        assertTrue(names.names());
        assertTrue(ResearchTreeDetailPolicy.channels(names, 74, 35).names());
        assertFalse(ResearchTreeDetailPolicy.channels(empty, 74, 35).names());
        assertFalse(ResearchTreeDetailPolicy.channels(names, 70, 33).names());
    }
    @Test void annotationsStayInsideSmallCanvasAndYieldToPriorities() {
        var view = new Rect(36,48,276,184);
        var first = ResearchTreeAnnotationPlacement.place(view,-500,900,160,18,List.of()).orElseThrow();
        var second = ResearchTreeAnnotationPlacement.place(view,-500,900,160,18,List.of(first)).orElseThrow();
        assertTrue(view.contains(first)); assertTrue(view.contains(second)); assertFalse(first.overlaps(second));
        assertTrue(ResearchTreeAnnotationPlacement.place(view,40,50,400,18,List.of()).isEmpty());
        assertTrue(ResearchTreeAnnotationPlacement.place(view,40,50,160,18,List.of(view)).isEmpty());
    }
    @Test void branchLabelsStayInTheirBranchAndAvoidTreeContent() {
        var view = new Rect(0,0,400,300);
        var branch = new Rect(20,20,160,240);
        var node = new Rect(20,20,160,32);
        var label = ResearchTreeAnnotationPlacement.landmark(view,branch,100,15,List.of(node)).orElseThrow();
        assertTrue(branch.contains(label)); assertFalse(label.overlaps(node));
        assertTrue(ResearchTreeAnnotationPlacement.landmark(view,new Rect(20,20,50,240),100,15,List.of()).isEmpty());
        assertTrue(ResearchTreeAnnotationPlacement.landmark(view,branch,100,15,List.of(branch)).isEmpty());
    }
    @Test void partiallyOffscreenProjectionCanHaveNegativeCoordinates() {
        var box = new ResearchTreeSceneRenderer.ScreenBox(-300,-200,800,600);
        assertTrue(box.overlaps(new Rect(36,48,276,184)));
        assertFalse(new ResearchTreeSceneRenderer.ScreenBox(-300,-200,40,40).overlaps(new Rect(36,48,276,184)));
        var view = new Rect(0,0,100,30);
        assertTrue(ResearchTreeAnnotationPlacement.place(view,0,0,90,18,List.of(view)).isEmpty());
    }
    @Test void reviewOverridesInspectionWhichOverridesTrackingWithoutRemovingItsMarker() {
        assertEquals(ResearchTreeConnectorStyle.REVIEW,ResearchTreeConnectorStyle.resolve(true,true,RelationshipRole.DIRECT_REQUIREMENT));
        assertEquals(ResearchTreeConnectorStyle.DIRECT,ResearchTreeConnectorStyle.resolve(false,true,RelationshipRole.DIRECT_REQUIREMENT));
        assertEquals(ResearchTreeConnectorStyle.TRACKED,ResearchTreeConnectorStyle.resolve(false,true,RelationshipRole.REQUIREMENT_PATH));
        assertEquals(ResearchTreeConnectorStyle.ANCESTRY,ResearchTreeConnectorStyle.resolve(false,false,RelationshipRole.REQUIREMENT_PATH));
        assertEquals(ResearchTreeConnectorStyle.QUIET,ResearchTreeConnectorStyle.resolve(false,false,RelationshipRole.UNRELATED));
        assertTrue(ResearchTreeConnectorStyle.REVIEW.thickness>ResearchTreeConnectorStyle.DIRECT.thickness);
        assertEquals(ResearchTreeConnectorStyle.REVIEW,ResearchTreeConnectorStyle.DIRECT.strongest(ResearchTreeConnectorStyle.REVIEW));
    }
}
