package com.gamergaming.taczweaponblueprints.client;

import java.util.List;
import java.util.Optional;

/** Pure geometry for the compact focused-node relationship cards. */
public final class ResearchTreeDetailLayout {
    public static final int SLOT_SIZE = 16;
    public static final int COMPACT_MAX_REQUIREMENTS = 2;
    public static final int COMPACT_MAX_UNLOCKS = 1;
    public static final int FULLSCREEN_MAX_PER_KIND = 8;

    private ResearchTreeDetailLayout() {
    }

    public static List<RelationSlot> compact(ResearchTreeScreenLayout.Rect details) {
        if (details == null || details.width() < 220 || details.height() < 44) {
            throw new IllegalArgumentException("compact Research Tree details are too small");
        }
        int y = details.y() + details.height() - SLOT_SIZE - 1;
        return List.of(
                new RelationSlot(
                        RelationKind.REQUIREMENT,
                        0,
                        new ResearchTreeScreenLayout.Rect(details.x() + 132, y, SLOT_SIZE, SLOT_SIZE)),
                new RelationSlot(
                        RelationKind.REQUIREMENT,
                        1,
                        new ResearchTreeScreenLayout.Rect(details.x() + 150, y, SLOT_SIZE, SLOT_SIZE)),
                new RelationSlot(
                        RelationKind.UNLOCK,
                        0,
                        new ResearchTreeScreenLayout.Rect(details.x() + 200, y, SLOT_SIZE, SLOT_SIZE)));
    }

    public static Optional<RelationSlot> compactSlotAt(
            ResearchTreeScreenLayout.Rect details,
            double x,
            double y) {
        return slotAt(compact(details), x, y);
    }

    /** Responsive relationship rows for the active fullscreen details panel. */
    public static List<RelationSlot> fullscreen(ResearchTreeScreenLayout.Layout layout) {
        if (layout == null || layout.mode() != ResearchTreeScreenLayout.ViewMode.FULLSCREEN) {
            throw new IllegalArgumentException("fullscreen relationship layout requires fullscreen bounds");
        }
        ResearchTreeScreenLayout.Rect details = layout.details();
        if (layout.detailsPlacement() == ResearchTreeScreenLayout.DetailsPlacement.OVERLAY) {
            return List.of();
        }
        if (layout.detailsPlacement() == ResearchTreeScreenLayout.DetailsPlacement.DRAWER
                && details.height() <= 24) {
            return List.of();
        }
        return switch (layout.detailsPlacement()) {
            case RIGHT -> rows(details.x() + 10, details.right() - 10,
                    details.y() + 88, details.y() + 128);
            case BOTTOM -> rows(details.x() + 220, details.right() - 10,
                    details.y() + 20, details.y() + 56);
            case DRAWER -> rows(details.x() + 120, details.right() - 84,
                    details.y() + 18, details.y() + 42);
            case OVERLAY -> List.of();
        };
    }

    public static Optional<ResearchTreeScreenLayout.Rect> primaryAction(
            ResearchTreeScreenLayout.Layout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("Research Tree layout cannot be null");
        }
        if (layout.mode() == ResearchTreeScreenLayout.ViewMode.COMPACT) {
            return Optional.of(new ResearchTreeScreenLayout.Rect(232, 199, 64, 20));
        }
        ResearchTreeScreenLayout.Rect details = layout.details();
        if (layout.detailsPlacement() == ResearchTreeScreenLayout.DetailsPlacement.OVERLAY) {
            return Optional.of(new ResearchTreeScreenLayout.Rect(
                    layout.screenWidth() - 100,
                    layout.screenHeight() - 32,
                    92,
                    24));
        }
        if (layout.detailsPlacement() == ResearchTreeScreenLayout.DetailsPlacement.DRAWER) {
            if (details.height() <= 24) {
                return Optional.empty();
            }
            return Optional.of(new ResearchTreeScreenLayout.Rect(
                    details.right() - 74, details.bottom() - 22, 64, 20));
        }
        return Optional.of(new ResearchTreeScreenLayout.Rect(
                details.x() + 10, details.bottom() - 28, 72, 20));
    }

    public static Optional<ResearchTreeScreenLayout.Rect> drawerToggle(
            ResearchTreeScreenLayout.Layout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("Research Tree layout cannot be null");
        }
        if (layout.mode() != ResearchTreeScreenLayout.ViewMode.FULLSCREEN
                || layout.detailsPlacement() != ResearchTreeScreenLayout.DetailsPlacement.DRAWER) {
            return Optional.empty();
        }
        ResearchTreeScreenLayout.Rect details = layout.details();
        return Optional.of(new ResearchTreeScreenLayout.Rect(
                details.right() - 20, details.y() + 2, 18, 18));
    }

    public static Optional<RelationSlot> slotAt(
            List<RelationSlot> slots,
            double x,
            double y) {
        if (slots == null || slots.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("invalid Research Tree relationship slots");
        }
        return slots.stream()
                .filter(slot -> contains(slot.bounds(), x, y))
                .findFirst();
    }

    /** Resolves the public node represented by a relationship slot, if one exists. */
    public static <T> Optional<T> relationTarget(
            RelationSlot slot,
            List<T> requirements,
            List<T> unlocks) {
        if (slot == null || requirements == null || unlocks == null
                || requirements.stream().anyMatch(java.util.Objects::isNull)
                || unlocks.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("invalid Research Tree relationship targets");
        }
        List<T> relations = slot.kind() == RelationKind.REQUIREMENT
                ? requirements
                : unlocks;
        return slot.index() < relations.size()
                ? Optional.of(relations.get(slot.index()))
                : Optional.empty();
    }

    private static boolean contains(ResearchTreeScreenLayout.Rect bounds, double x, double y) {
        return x >= bounds.x() && x < bounds.right()
                && y >= bounds.y() && y < bounds.bottom();
    }

    private static List<RelationSlot> rows(
            int startX,
            int endX,
            int requirementY,
            int unlockY) {
        int count = Math.min(
                FULLSCREEN_MAX_PER_KIND,
                Math.max(0, (endX - startX + 2) / (SLOT_SIZE + 2)));
        if (count == 0) {
            return List.of();
        }
        java.util.ArrayList<RelationSlot> slots = new java.util.ArrayList<>(count * 2);
        for (int index = 0; index < count; index++) {
            int x = startX + index * (SLOT_SIZE + 2);
            slots.add(new RelationSlot(
                    RelationKind.REQUIREMENT,
                    index,
                    new ResearchTreeScreenLayout.Rect(x, requirementY, SLOT_SIZE, SLOT_SIZE)));
        }
        for (int index = 0; index < count; index++) {
            int x = startX + index * (SLOT_SIZE + 2);
            slots.add(new RelationSlot(
                    RelationKind.UNLOCK,
                    index,
                    new ResearchTreeScreenLayout.Rect(x, unlockY, SLOT_SIZE, SLOT_SIZE)));
        }
        return List.copyOf(slots);
    }

    public record RelationSlot(
            RelationKind kind,
            int index,
            ResearchTreeScreenLayout.Rect bounds) {
        public RelationSlot {
            if (kind == null || index < 0 || bounds == null
                    || bounds.width() != SLOT_SIZE || bounds.height() != SLOT_SIZE
                    || index >= FULLSCREEN_MAX_PER_KIND) {
                throw new IllegalArgumentException("invalid Research Tree relationship slot");
            }
        }
    }

    public enum RelationKind {
        REQUIREMENT,
        UNLOCK
    }
}
