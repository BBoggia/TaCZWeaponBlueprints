package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;

/** Responsive overlay geometry for committed Research Tree search results. */
public final class ResearchTreeSearchResultLayout {
    public static final int MAX_VISIBLE_RESULTS = 5;
    public static final int ROW_HEIGHT = 20;
    public static final int GAP = 2;
    private static final int MIN_WIDTH = 140;
    private static final int SCREEN_MARGIN = 4;

    private ResearchTreeSearchResultLayout() {
    }

    public static Layout below(
            ResearchTreeScreenLayout.Rect searchField,
            int screenWidth,
            int screenHeight) {
        if (searchField == null || screenWidth <= 0 || screenHeight <= 0
                || !searchField.inside(screenWidth, screenHeight)) {
            throw new IllegalArgumentException("invalid Research Tree search-result bounds");
        }
        int x = searchField.x();
        int y = searchField.bottom() + GAP;
        int availableWidth = screenWidth - SCREEN_MARGIN - x;
        int width = Math.min(
                Math.max(searchField.width(), MIN_WIDTH),
                availableWidth);
        int availableRows = Math.max(0,
                (screenHeight - SCREEN_MARGIN - y) / ROW_HEIGHT);
        int rowCount = Math.min(MAX_VISIBLE_RESULTS, availableRows);
        if (width <= 0 || rowCount <= 0) {
            throw new IllegalArgumentException("Research Tree search results have no usable space");
        }
        ArrayList<ResearchTreeScreenLayout.Rect> rows = new ArrayList<>(rowCount);
        for (int row = 0; row < rowCount; row++) {
            rows.add(new ResearchTreeScreenLayout.Rect(
                    x, y + row * ROW_HEIGHT, width, ROW_HEIGHT));
        }
        return new Layout(
                new ResearchTreeScreenLayout.Rect(x, y, width, rowCount * ROW_HEIGHT),
                rows);
    }

    public record Layout(
            ResearchTreeScreenLayout.Rect panel,
            List<ResearchTreeScreenLayout.Rect> rows) {
        public Layout {
            if (panel == null || rows == null || rows.isEmpty()
                    || rows.size() > MAX_VISIBLE_RESULTS
                    || rows.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("invalid Research Tree search-result layout");
            }
            rows = List.copyOf(rows);
            for (int index = 0; index < rows.size(); index++) {
                ResearchTreeScreenLayout.Rect row = rows.get(index);
                if (!panel.contains(row)
                        || row.x() != panel.x()
                        || row.width() != panel.width()
                        || row.height() != ROW_HEIGHT
                        || row.y() != panel.y() + index * ROW_HEIGHT) {
                    throw new IllegalArgumentException("invalid Research Tree search-result row");
                }
            }
        }
    }
}
