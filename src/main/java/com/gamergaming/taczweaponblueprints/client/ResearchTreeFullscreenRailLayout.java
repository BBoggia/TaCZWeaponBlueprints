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
        ResearchTreeScreenLayout.Rect pin = actionAt(x, rail.bottom() - INNER_PADDING, 3);
        ResearchTreeScreenLayout.Rect help = actionAt(x, rail.bottom() - INNER_PADDING, 4);
        ResearchTreeScreenLayout.Rect recommendation =
                actionAt(x, rail.bottom() - INNER_PADDING, 5);

        int entriesY = fullscreen.searchButton().bottom() + SECTION_GAP;
        int entriesBottom = recommendation.y() - SECTION_GAP;
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
        return new Layout(
                List.copyOf(entries), recommendation, help, pin, zoomOut, zoomIn, fit);
    }

    /**
     * Keeps the view action in slot zero while virtualizing only the published
     * branch entries below it. Returned entry indexes use the screen's existing
     * convention: zero is the view action and one-based values are groups.
     */
    public static int entryIndexForSlot(
            int slot,
            int visibleEntryCount,
            int groupScroll,
            int groupCount) {
        validateWindowInputs(visibleEntryCount, groupScroll, groupCount);
        if (slot < 0 || slot >= visibleEntryCount) {
            return -1;
        }
        if (slot == 0) {
            return 0;
        }
        int groupIndex = groupScroll + slot - 1;
        return groupIndex < groupCount ? groupIndex + 1 : -1;
    }

    public static int maximumGroupScroll(int visibleEntryCount, int groupCount) {
        if (visibleEntryCount < 1 || groupCount < 0) {
            throw new IllegalArgumentException("invalid fullscreen rail window");
        }
        return Math.max(0, groupCount - Math.max(0, visibleEntryCount - 1));
    }

    private static void validateWindowInputs(
            int visibleEntryCount,
            int groupScroll,
            int groupCount) {
        if (visibleEntryCount < 1 || groupScroll < 0 || groupCount < 0
                || groupScroll > maximumGroupScroll(visibleEntryCount, groupCount)) {
            throw new IllegalArgumentException("invalid fullscreen rail window");
        }
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
            ResearchTreeScreenLayout.Rect recommendation,
            ResearchTreeScreenLayout.Rect help,
            ResearchTreeScreenLayout.Rect pin,
            ResearchTreeScreenLayout.Rect zoomOut,
            ResearchTreeScreenLayout.Rect zoomIn,
            ResearchTreeScreenLayout.Rect fit) {
        public Layout {
            entries = entries == null ? List.of() : List.copyOf(entries);
            if (entries.isEmpty() || entries.stream().anyMatch(java.util.Objects::isNull)
                    || recommendation == null || help == null || pin == null
                    || zoomOut == null || zoomIn == null || fit == null) {
                throw new IllegalArgumentException("invalid fullscreen rail geometry");
            }
            ArrayList<ResearchTreeScreenLayout.Rect> regions = new ArrayList<>(entries);
            regions.add(recommendation);
            regions.add(help);
            regions.add(pin);
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
