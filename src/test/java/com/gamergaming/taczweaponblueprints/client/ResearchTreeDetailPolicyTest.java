package com.gamergaming.taczweaponblueprints.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchTreeDetailPolicyTest {
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
