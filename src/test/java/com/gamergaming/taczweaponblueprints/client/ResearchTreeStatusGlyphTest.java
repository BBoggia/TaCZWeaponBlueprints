package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ResearchTreeStatusGlyphTest {
    @Test
    void everySemanticStatusHasANonEmptyUniqueSevenPixelGlyph() {
        Set<ResearchTreeStatusGlyph.Glyph> glyphs = new HashSet<>();
        for (ResearchTreePresentationContract.StatusSymbol symbol
                : ResearchTreePresentationContract.StatusSymbol.values()) {
            ResearchTreeStatusGlyph.Glyph glyph = ResearchTreeStatusGlyph.forSymbol(symbol);
            assertEquals(ResearchTreeStatusGlyph.SIZE, glyph.rows().size());
            assertTrue(glyph.pixelCount() > 0);
            assertTrue(glyphs.add(glyph), () -> symbol + " reuses another status glyph");
        }
        assertEquals(ResearchTreePresentationContract.StatusSymbol.values().length, glyphs.size());
    }

    @Test
    void pixelQueriesAreBoundsSafeAndGlyphRowsAreImmutable() {
        ResearchTreeStatusGlyph.Glyph glyph = ResearchTreeStatusGlyph.forSymbol(
                ResearchTreePresentationContract.StatusSymbol.AVAILABLE);

        assertTrue(glyph.filled(3, 0));
        assertFalse(glyph.filled(-1, 0));
        assertFalse(glyph.filled(0, ResearchTreeStatusGlyph.SIZE));
        assertThrows(UnsupportedOperationException.class, () -> glyph.rows().add(0));
        assertThrows(IllegalArgumentException.class, () -> ResearchTreeStatusGlyph.forSymbol(null));
    }
}
