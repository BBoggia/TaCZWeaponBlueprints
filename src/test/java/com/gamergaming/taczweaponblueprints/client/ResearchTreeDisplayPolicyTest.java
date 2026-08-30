package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResearchTreeDisplayPolicyTest {
    @Test
    void defaultsFavorSmoothMotionAndACleanCanvas() {
        assertFalse(ResearchTreeDisplayPolicy.DEFAULT.reduceMotion());
        assertTrue(ResearchTreeDisplayPolicy.DEFAULT.cameraAnimationEnabled());
        assertFalse(ResearchTreeDisplayPolicy.DEFAULT.showBackgroundGrid());
    }

    @Test
    void reducedMotionDisablesOnlyCameraAnimation() {
        ResearchTreeDisplayPolicy policy = new ResearchTreeDisplayPolicy(true, true);

        assertFalse(policy.cameraAnimationEnabled());
        assertTrue(policy.showBackgroundGrid());
    }
}
