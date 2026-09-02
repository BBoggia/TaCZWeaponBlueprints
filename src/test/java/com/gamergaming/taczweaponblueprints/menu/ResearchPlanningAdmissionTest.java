package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

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

    @Test
    void queuedPlanningRotatesFairlyAcrossRequesters() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ResearchPlanningAdmission.registerQueued(first);
        ResearchPlanningAdmission.registerQueued(second);

        assertTrue(ResearchPlanningAdmission.admitQueued(100L, first));
        assertFalse(ResearchPlanningAdmission.admitQueued(100L, second));
        assertFalse(ResearchPlanningAdmission.admitQueued(101L, first));
        assertTrue(ResearchPlanningAdmission.admitQueued(101L, second));
        assertTrue(ResearchPlanningAdmission.admitQueued(102L, first));

        ResearchPlanningAdmission.unregisterQueued(first);
        assertTrue(ResearchPlanningAdmission.admitQueued(103L, second));
    }

    @Test
    void queuedWorkReceivesAReservedTickUnderContinuousInteractiveLoad() {
        UUID background = UUID.randomUUID();
        ResearchPlanningAdmission.registerQueued(background);

        assertTrue(ResearchPlanningAdmission.admit(101L));
        assertTrue(ResearchPlanningAdmission.admit(102L));
        assertTrue(ResearchPlanningAdmission.admit(103L));
        assertFalse(ResearchPlanningAdmission.admit(104L));
        assertTrue(ResearchPlanningAdmission.admitQueued(104L, background));
    }

    @Test
    void reservedBackgroundTickDoesNotRejectAPlayerAction() {
        UUID background = UUID.randomUUID();
        ResearchPlanningAdmission.registerQueued(background);

        assertFalse(ResearchPlanningAdmission.admit(104L));
        assertTrue(ResearchPlanningAdmission.admitInteractive(104L));
        assertFalse(ResearchPlanningAdmission.admitInteractive(104L));
        assertTrue(ResearchPlanningAdmission.admitQueued(104L, background));
        assertFalse(ResearchPlanningAdmission.admitQueued(104L, background));
    }
}
