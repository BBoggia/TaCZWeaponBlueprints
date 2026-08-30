package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeHoldActivationControllerTest {
    private static final ResourceLocation NODE = new ResourceLocation("test:node");

    @Test
    void holdActivatesExactlyOnceAtTheConfiguredThreshold() {
        ResearchTreeHoldActivationController hold =
                new ResearchTreeHoldActivationController();
        hold.begin(NODE, 1_000L, 700);

        assertEquals(0.5D, hold.snapshot(1_350L).progress());
        assertEquals(ResearchTreeHoldActivationController.Outcome.NONE, hold.advance(1_699L));
        assertEquals(ResearchTreeHoldActivationController.Outcome.ACTIVATE, hold.advance(1_700L));
        assertEquals(ResearchTreeHoldActivationController.Outcome.NONE, hold.advance(2_000L));
        assertTrue(hold.release());
        assertEquals(ResearchTreeHoldActivationController.Status.IDLE,
                hold.snapshot(2_000L).status());
    }

    @Test
    void earlyReleaseAndCancellationNeverActivate() {
        ResearchTreeHoldActivationController hold =
                new ResearchTreeHoldActivationController();
        hold.begin(NODE, 100L, 700);
        assertFalse(hold.release());

        hold.begin(NODE, 1_000L, 700);
        hold.cancel();
        assertEquals(ResearchTreeHoldActivationController.Outcome.NONE, hold.advance(2_000L));
        assertFalse(hold.release());
    }

    @Test
    void malformedHoldInputsFailClosed() {
        ResearchTreeHoldActivationController hold =
                new ResearchTreeHoldActivationController();
        assertThrows(IllegalArgumentException.class, () -> hold.begin(null, 0L, 700));
        assertThrows(IllegalArgumentException.class, () -> hold.begin(NODE, -1L, 700));
        assertThrows(IllegalArgumentException.class, () -> hold.begin(NODE, 0L, 0));
        assertThrows(IllegalArgumentException.class, () -> hold.snapshot(-1L));
    }
}
