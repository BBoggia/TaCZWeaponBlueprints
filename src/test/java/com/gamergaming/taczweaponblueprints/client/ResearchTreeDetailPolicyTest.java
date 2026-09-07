package com.gamergaming.taczweaponblueprints.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.taczweaponblueprints.client.ResearchTreeDetailPolicy.Detail.*;

class ResearchTreeDetailPolicyTest {
    @Test void artworkSurvivesTheFormerCutoffAndMuchWiderZoom() {
        assertEquals(ICONS, ResearchTreeDetailPolicy.resolve(BROWSE, 96 * .47, 44 * .47));
        assertEquals(ICONS, ResearchTreeDetailPolicy.resolve(ICONS, 96 * .20, 44 * .20));
        assertEquals(OVERVIEW, ResearchTreeDetailPolicy.resolve(ICONS, 96 * .15, 44 * .15));
    }
    @Test void iconTransitionHasABufferInBothDimensions() {
        assertEquals(ICONS, ResearchTreeDetailPolicy.resolve(ICONS, 16, 7));
        assertEquals(OVERVIEW, ResearchTreeDetailPolicy.resolve(ICONS, 15.9, 7));
        assertEquals(OVERVIEW, ResearchTreeDetailPolicy.resolve(OVERVIEW, 17, 7.5));
        assertEquals(ICONS, ResearchTreeDetailPolicy.resolve(OVERVIEW, 18, 8));
        assertEquals(OVERVIEW, ResearchTreeDetailPolicy.resolve(ICONS, 50, 6));
    }
    @Test void labelsRequireRoomAndDoNotFlickerNearBoundary() {
        assertEquals(BROWSE, ResearchTreeDetailPolicy.resolve(BROWSE, 64, 28));
        assertEquals(ICONS, ResearchTreeDetailPolicy.resolve(BROWSE, 63, 28));
        assertEquals(ICONS, ResearchTreeDetailPolicy.resolve(ICONS, 66, 29));
        assertEquals(BROWSE, ResearchTreeDetailPolicy.resolve(ICONS, 68, 30));
        assertEquals(ICONS, ResearchTreeDetailPolicy.resolve(BROWSE, 100, 27));
    }
    @Test void directZoomJumpsDoNotNeedIntermediateFrames() {
        assertEquals(OVERVIEW, ResearchTreeDetailPolicy.resolve(BROWSE, 8, 4));
        assertEquals(BROWSE, ResearchTreeDetailPolicy.resolve(OVERVIEW, 96, 44));
    }
    @Test void displaySizeRatherThanZoomControlsTheDetail() {
        assertEquals(ICONS, ResearchTreeDetailPolicy.resolve(BROWSE, 96 * .4, 44 * .4));
        assertEquals(BROWSE, ResearchTreeDetailPolicy.resolve(BROWSE, 192 * .4, 88 * .4));
    }
    @Test void hoverPreviewStaysInsideScreenAtEveryCornerAndGuiSize() {
        for (int width : new int[]{320, 366, 427, 854, 1920}) {
            for (int height : new int[]{240, 273, 480, 1080}) {
                for (int x : new int[]{0, width / 2, width - 1}) for (int y : new int[]{0, height / 2, height - 1}) {
                    var box = ResearchTreeDetailPolicy.hoverBounds(width, height, x, y, 500);
                    assertTrue(box.inside(width, height));
                    assertTrue(box.x() >= 8 && box.y() >= 8);
                    assertTrue(box.right() <= width - 8 && box.bottom() <= height - 8);
                }
            }
        }
    }
}
