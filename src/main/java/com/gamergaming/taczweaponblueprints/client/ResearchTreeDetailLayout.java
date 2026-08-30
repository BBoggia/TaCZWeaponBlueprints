package com.gamergaming.taczweaponblueprints.client;

import java.util.List;
import java.util.Optional;

/** Pure geometry for the compact focused-node relationship cards. */
public final class ResearchTreeDetailLayout {
    public static final int SLOT_SIZE = 16;
    public static final int COMPACT_MAX_REQUIREMENTS = 2;
    public static final int COMPACT_MAX_UNLOCKS = 1;

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

    public static Optional<ResearchTreeScreenLayout.Rect> primaryAction(
            ResearchTreeScreenLayout.Layout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("Research Tree layout cannot be null");
        }
        if (layout.mode() == ResearchTreeScreenLayout.ViewMode.COMPACT) {
            return Optional.of(new ResearchTreeScreenLayout.Rect(232, 199, 64, 20));
        }
        // Fullscreen actions belong to the adaptive contextual card.
        return Optional.empty();
    }

    /** Header hover owns only pixels not already assigned to a compact action. */
    public static boolean compactDetailsTooltipAt(
            ResearchTreeScreenLayout.Layout layout,
            double x,
            double y) {
        if (layout == null || layout.mode() != ResearchTreeScreenLayout.ViewMode.COMPACT) {
            return false;
        }
        ResearchTreeScreenLayout.Rect details = layout.details();
        ResearchTreeScreenLayout.Rect header = new ResearchTreeScreenLayout.Rect(
                details.x(), details.y(), details.width(), 28);
        return header.contains(x, y)
                && primaryAction(layout).filter(rect -> rect.contains(x, y)).isEmpty()
                && compact(details).stream()
                        .map(RelationSlot::bounds)
                        .noneMatch(rect -> rect.contains(x, y));
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

    public record RelationSlot(
            RelationKind kind,
            int index,
            ResearchTreeScreenLayout.Rect bounds) {
        public RelationSlot {
            if (kind == null || index < 0 || bounds == null
                    || bounds.width() != SLOT_SIZE || bounds.height() != SLOT_SIZE
                    || index >= Math.max(COMPACT_MAX_REQUIREMENTS, COMPACT_MAX_UNLOCKS)) {
                throw new IllegalArgumentException("invalid Research Tree relationship slot");
            }
        }
    }

    public enum RelationKind {
        REQUIREMENT,
        UNLOCK
    }
}
