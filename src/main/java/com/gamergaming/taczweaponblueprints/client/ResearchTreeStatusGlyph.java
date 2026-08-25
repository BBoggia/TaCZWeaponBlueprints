package com.gamergaming.taczweaponblueprints.client;

import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;

/** Crisp color-independent 7x7 pixel symbols for Research Tree node states. */
public final class ResearchTreeStatusGlyph {
    public static final int SIZE = 7;

    private static final Map<ResearchTreePresentationContract.StatusSymbol, Glyph> GLYPHS = Map.ofEntries(
            Map.entry(ResearchTreePresentationContract.StatusSymbol.UNKNOWN, glyph(
                    "..###..",
                    ".#...#.",
                    ".....#.",
                    "...##..",
                    "...#...",
                    ".......",
                    "...#...")),
            Map.entry(ResearchTreePresentationContract.StatusSymbol.PREVIEW, glyph(
                    ".......",
                    "..###..",
                    ".#...#.",
                    "#..#..#",
                    ".#...#.",
                    "..###..",
                    ".......")),
            Map.entry(ResearchTreePresentationContract.StatusSymbol.LEARNED, glyph(
                    ".......",
                    ".....#.",
                    "....#..",
                    ".#.#...",
                    "..#....",
                    ".......",
                    ".......")),
            Map.entry(ResearchTreePresentationContract.StatusSymbol.AVAILABLE, glyph(
                    "...#...",
                    "...#...",
                    ".#.#.#.",
                    "..###..",
                    ".#.#.#.",
                    "...#...",
                    "...#...")),
            Map.entry(ResearchTreePresentationContract.StatusSymbol.POINTS_REQUIRED, glyph(
                    ".####..",
                    ".#...#.",
                    ".####..",
                    ".#.#...",
                    ".#..#..",
                    ".......",
                    ".....#.")),
            Map.entry(ResearchTreePresentationContract.StatusSymbol.DISCOVERY_REQUIRED, glyph(
                    "...#...",
                    "..#.#..",
                    ".#...#.",
                    "#..#..#",
                    ".#...#.",
                    "..#.#..",
                    "...#...")),
            Map.entry(ResearchTreePresentationContract.StatusSymbol.PREREQUISITES_REQUIRED, glyph(
                    ".###...",
                    "#...#..",
                    "...##..",
                    "..##...",
                    ".##....",
                    "..#...#",
                    "...###.")),
            Map.entry(ResearchTreePresentationContract.StatusSymbol.RESEARCH_DISABLED, glyph(
                    ".#####.",
                    "#....##",
                    "#...#.#",
                    "#..#..#",
                    "##....#",
                    ".#####.",
                    ".......")),
            Map.entry(ResearchTreePresentationContract.StatusSymbol.COST_ABOVE_CAP, glyph(
                    "..###..",
                    "..#.#..",
                    "..#.#..",
                    "..#.#..",
                    ".......",
                    "..#.#..",
                    "..###..")),
            Map.entry(ResearchTreePresentationContract.StatusSymbol.CONTENT_UNAVAILABLE, glyph(
                    "#.....#",
                    ".#...#.",
                    "..#.#..",
                    "...#...",
                    "..#.#..",
                    ".#...#.",
                    "#.....#")));

    private ResearchTreeStatusGlyph() {
    }

    public static Glyph forSymbol(ResearchTreePresentationContract.StatusSymbol symbol) {
        if (symbol == null) {
            throw new IllegalArgumentException("Research Tree status symbol cannot be null");
        }
        Glyph glyph = GLYPHS.get(symbol);
        if (glyph == null) {
            throw new IllegalStateException("Research Tree status symbol has no glyph: " + symbol);
        }
        return glyph;
    }

    public static void render(GuiGraphics graphics, int x, int y, int color, Glyph glyph) {
        if (graphics == null || glyph == null) {
            throw new IllegalArgumentException("Research Tree status glyph render inputs cannot be null");
        }
        for (int glyphY = 0; glyphY < SIZE; glyphY++) {
            int glyphX = 0;
            while (glyphX < SIZE) {
                while (glyphX < SIZE && !glyph.filled(glyphX, glyphY)) {
                    glyphX++;
                }
                int start = glyphX;
                while (glyphX < SIZE && glyph.filled(glyphX, glyphY)) {
                    glyphX++;
                }
                if (start < glyphX) {
                    graphics.fill(x + start, y + glyphY, x + glyphX, y + glyphY + 1, color);
                }
            }
        }
    }

    private static Glyph glyph(String... rows) {
        if (rows == null || rows.length != SIZE) {
            throw new IllegalArgumentException("Research Tree status glyph must be 7x7");
        }
        java.util.ArrayList<Integer> masks = new java.util.ArrayList<>(SIZE);
        for (String row : rows) {
            if (row == null || row.length() != SIZE) {
                throw new IllegalArgumentException("Research Tree status glyph must be 7x7");
            }
            int mask = 0;
            for (int x = 0; x < SIZE; x++) {
                char pixel = row.charAt(x);
                if (pixel == '#') {
                    mask |= 1 << x;
                } else if (pixel != '.') {
                    throw new IllegalArgumentException("Research Tree status glyph contains an invalid pixel");
                }
            }
            masks.add(mask);
        }
        return new Glyph(masks);
    }

    public record Glyph(List<Integer> rows) {
        public Glyph {
            rows = rows == null ? List.of() : List.copyOf(rows);
            if (rows.size() != SIZE
                    || rows.stream().anyMatch(mask -> mask == null || mask < 0 || mask >= 1 << SIZE)) {
                throw new IllegalArgumentException("invalid Research Tree status glyph");
            }
        }

        public boolean filled(int x, int y) {
            if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) {
                return false;
            }
            return (rows.get(y) & 1 << x) != 0;
        }

        public int pixelCount() {
            return rows.stream().mapToInt(Integer::bitCount).sum();
        }
    }
}
