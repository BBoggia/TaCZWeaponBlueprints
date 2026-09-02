package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ResearchPlanningAdmissionTest {
    @AfterEach
    void clearAdmission() {
        ResearchPlanningAdmission.clear();
    }

    @Test
    void planningIsBoundedAcrossMenusWithinOneServerTick() {
        for (int index = 0;
                index < ResearchPlanningAdmission.MAX_PLANS_PER_SERVER_TICK;
                index++) {
            assertTrue(ResearchPlanningAdmission.admit(100L));
        }
        assertFalse(ResearchPlanningAdmission.admit(100L));
        assertTrue(ResearchPlanningAdmission.admit(101L));
    }

    @Test
    void restartAndTickRollbackResetTheFuse() {
        assertTrue(ResearchPlanningAdmission.admit(50L));
        ResearchPlanningAdmission.clear();
        assertTrue(ResearchPlanningAdmission.admit(0L));
        assertTrue(ResearchPlanningAdmission.admit(10L));
        assertTrue(ResearchPlanningAdmission.admit(5L));
        assertFalse(ResearchPlanningAdmission.admit(-1L));
    }
}
