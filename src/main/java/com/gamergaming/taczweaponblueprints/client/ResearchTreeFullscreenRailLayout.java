package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry for the virtualized fullscreen navigation rail. */
public final class ResearchTreeFullscreenRailLayout {
    public static final int ENTRY_SIZE = ResearchTreeFullscreenLayout.CONTROL_SIZE;
    public static final int ENTRY_GAP = 2;
    public static final int MAX_VISIBLE_ENTRIES = 24;
    private static final int INNER_PADDING = 2;
    private static final int SECTION_GAP = 4;

    private ResearchTreeFullscreenRailLayout() {
    }

    public static Layout forLayout(ResearchTreeFullscreenLayout.Layout fullscreen) {
        if (fullscreen == null) {
            throw new IllegalArgumentException("fullscreen rail layout cannot be null");
        }
        ResearchTreeScreenLayout.Rect rail = fullscreen.rail();
        int x = rail.x() + (rail.width() - ENTRY_SIZE) / 2;
        ResearchTreeScreenLayout.Rect fit = actionAt(x, rail.bottom() - INNER_PADDING, 0);
        ResearchTreeScreenLayout.Rect zoomIn = actionAt(x, rail.bottom() - INNER_PADDING, 1);
        ResearchTreeScreenLayout.Rect zoomOut = actionAt(x, rail.bottom() - INNER_PADDING, 2);

        int entriesY = fullscreen.searchButton().bottom() + SECTION_GAP;
        int entriesBottom = zoomOut.y() - SECTION_GAP;
        int entryCount = Math.min(
                MAX_VISIBLE_ENTRIES,
                Math.max(
                        1,
                        (entriesBottom - entriesY + ENTRY_GAP) / (ENTRY_SIZE + ENTRY_GAP)));
        ArrayList<ResearchTreeScreenLayout.Rect> entries = new ArrayList<>(entryCount);
        for (int index = 0; index < entryCount; index++) {
            entries.add(new ResearchTreeScreenLayout.Rect(
                    x,
                    entriesY + index * (ENTRY_SIZE + ENTRY_GAP),
                    ENTRY_SIZE,
                    ENTRY_SIZE));
        }
        return new Layout(List.copyOf(entries), zoomOut, zoomIn, fit);
    }

    private static ResearchTreeScreenLayout.Rect actionAt(
            int x,
            int railBottom,
            int indexFromBottom) {
        return new ResearchTreeScreenLayout.Rect(
                x,
                railBottom - ENTRY_SIZE - indexFromBottom * (ENTRY_SIZE + ENTRY_GAP),
                ENTRY_SIZE,
                ENTRY_SIZE);
    }

    public record Layout(
            List<ResearchTreeScreenLayout.Rect> entries,
            ResearchTreeScreenLayout.Rect zoomOut,
            ResearchTreeScreenLayout.Rect zoomIn,
            ResearchTreeScreenLayout.Rect fit) {
        public Layout {
            entries = entries == null ? List.of() : List.copyOf(entries);
            if (entries.isEmpty() || entries.stream().anyMatch(java.util.Objects::isNull)
                    || zoomOut == null || zoomIn == null || fit == null) {
                throw new IllegalArgumentException("invalid fullscreen rail geometry");
            }
            ArrayList<ResearchTreeScreenLayout.Rect> regions = new ArrayList<>(entries);
            regions.add(zoomOut);
            regions.add(zoomIn);
            regions.add(fit);
            for (int left = 0; left < regions.size(); left++) {
                for (int right = left + 1; right < regions.size(); right++) {
                    if (regions.get(left).overlaps(regions.get(right))) {
                        throw new IllegalArgumentException("fullscreen rail controls overlap");
                    }
                }
            }
        }
    }
}
