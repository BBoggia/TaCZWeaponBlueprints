package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ResearchTreeBaselineCaptureContextTest {
    private static final Object CANVAS = new Object(), GRAPH = new Object(), LAYOUT = new Object();

    static IntStream changes() { return IntStream.rangeClosed(1, 11); }

    @ParameterizedTest(name = "capture context change {0}")
    @MethodSource("changes")
    void changingViewOrMeasurementConditionsInvalidatesTheSample(int change) {
        assertFalse(context(0).matches(context(change)));
    }

    @Test
    void sameViewAndEqualMeasuredBoundsAllowCameraOnlyMotion() {
        assertTrue(context(0).matches(context(0)));
        assertFalse(context(0).matches(null));
    }

    private static ResearchTreeBaselineCapture.Context context(int change) {
        return new ResearchTreeBaselineCapture.Context(change == 1 ? new Object() : CANVAS,
                change == 2 ? new Object() : GRAPH, change == 3 ? new Object() : LAYOUT,
                new ResearchTreeScreenLayout.Rect(change == 4 ? 11 : 10, 10, 800, 450),
                change == 5 ? 855 : 854, change == 6 ? 481 : 480, change == 7 ? 1709 : 1708,
                change == 8 ? 961 : 960, change == 9 ? 3 : 2, change == 10 ? 30 : 60, change != 11);
    }
}
