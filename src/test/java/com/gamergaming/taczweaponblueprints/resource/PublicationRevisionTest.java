package com.gamergaming.taczweaponblueprints.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PublicationRevisionTest {
    @Test
    void reservesZeroAndRollsMaximumBackToOne() {
        assertEquals(1L, PublicationRevision.next(0L));
        assertEquals(2L, PublicationRevision.next(1L));
        assertEquals(1L, PublicationRevision.next(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> PublicationRevision.next(-1L));
    }
}
