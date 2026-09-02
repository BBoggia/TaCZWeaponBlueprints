package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ResearchPathComplexityMemoTest {
    @AfterEach
    void clearMemo() {
        ResearchPathComplexityMemo.clear();
    }

    @Test
    void failedGeneralRouteIsRememberedOnlyForItsBriefWindow() {
        ResearchPathComplexityMemo.Key key =
                new ResearchPathComplexityMemo.Key(1L, 2L);

        assertFalse(ResearchPathComplexityMemo.contains(key, 100L));
        ResearchPathComplexityMemo.remember(key, 100L);
        assertTrue(ResearchPathComplexityMemo.contains(
                key, 100L + ResearchPathComplexityMemo.RETENTION_NANOS - 1L));
        assertFalse(ResearchPathComplexityMemo.contains(
                key, 100L + ResearchPathComplexityMemo.RETENTION_NANOS));
    }

    @Test
    void memoIsBoundedAndSuccessfulRetryCanForgetItsFailure() {
        for (int index = 0; index <= ResearchPathComplexityMemo.MAX_ENTRIES; index++) {
            ResearchPathComplexityMemo.remember(
                    new ResearchPathComplexityMemo.Key(index, index + 1L),
                    1_000L);
        }
        assertFalse(ResearchPathComplexityMemo.contains(
                new ResearchPathComplexityMemo.Key(0L, 1L), 1_001L));

        ResearchPathComplexityMemo.Key retained = new ResearchPathComplexityMemo.Key(
                ResearchPathComplexityMemo.MAX_ENTRIES,
                ResearchPathComplexityMemo.MAX_ENTRIES + 1L);
        assertTrue(ResearchPathComplexityMemo.contains(retained, 1_001L));
        ResearchPathComplexityMemo.forget(retained);
        assertFalse(ResearchPathComplexityMemo.contains(retained, 1_001L));
    }
}
