package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TechTreeToolbarTest {
    @Test void threeDomainsFitAtMinimumGuiWidthInEitherGroupingAndWithLongTranslations() {
        int controlsLeft = 320 - 20 - 56;
        for (int group : new int[] { 96, 76, 410 }) {
            var bar = TechTreeToolbar.fit(controlsLeft, group, 168, 3);
            int domains = bar.compactDomains() ? 3 * (bar.maxDomainWidth() + 2) : 168;
            assertTrue(8 + domains < controlsLeft - bar.groupingWidth());
            assertTrue(bar.maxDomainWidth() >= 16);
        }
    }

    @Test void wideScreensKeepFullLabels() {
        var bar = TechTreeToolbar.fit(640 - 76, 96, 168, 3);
        assertFalse(bar.compactGrouping());
        assertFalse(bar.compactDomains());
    }
}
