package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Pure adaptive geometry for the pinned fullscreen Research Tree node card. */
public final class ResearchTreeContextCardLayout {
    public static final int MAX_INGREDIENTS = 6;
    public static final int CARD_MAX_WIDTH = 224;
    public static final int CARD_MIN_WIDTH = 176;
    public static final int CARD_GAP = 8;
    public static final int SCREEN_PADDING = 4;
    public static final int ACTION_HEIGHT = 20;
    public static final int RETURN_ACTION_SIZE = 18;
    private static final int CARD_PADDING = 7;
    private static final int INGREDIENT_ROW_HEIGHT = 20;
    private static final int ACTION_WIDTH = 72;

    private ResearchTreeContextCardLayout() {
    }

    public static Layout place(
            int screenWidth,
            int screenHeight,
            Anchor anchor,
            List<ResearchTreeScreenLayout.Rect> avoided,
            int ingredientCount,
            boolean exactPreview) {
        validate(screenWidth, screenHeight, anchor, avoided, ingredientCount);
        if (!exactPreview && ingredientCount != 0) {
            throw new IllegalArgumentException(
                    "anonymous Research Tree card cannot receive exact ingredient counts");
        }
        int availableWidth = screenWidth - SCREEN_PADDING * 2;
        int cardWidth = Math.min(CARD_MAX_WIDTH, availableWidth);
        if (cardWidth < CARD_MIN_WIDTH) {
            throw new IllegalArgumentException("fullscreen Research Tree card has no usable width");
        }
        int columns = exactPreview && ingredientCount > 1 && cardWidth >= 208 ? 2 : 1;
        int rows = exactPreview ? (ingredientCount + columns - 1) / columns : 0;
        int ingredientStart = 66;
        int footerY = exactPreview
                ? ingredientStart + rows * INGREDIENT_ROW_HEIGHT + 2
                : 0;
        int cardHeight = exactPreview ? footerY + ACTION_HEIGHT + CARD_PADDING : 60;
        cardHeight = Math.min(cardHeight, screenHeight - SCREEN_PADDING * 2);

        List<NaturalPlacement> naturalPlacements = new ArrayList<>(Placement.values().length);
        LinkedHashSet<Integer> candidateXs = new LinkedHashSet<>();
        LinkedHashSet<Integer> candidateYs = new LinkedHashSet<>();
        for (Placement placement : Placement.values()) {
            int x;
            int y;
            switch (placement) {
                case RIGHT -> {
                    x = anchor.right() + CARD_GAP;
                    y = anchor.centerY() - cardHeight / 2;
                }
                case LEFT -> {
                    x = anchor.x() - CARD_GAP - cardWidth;
                    y = anchor.centerY() - cardHeight / 2;
                }
                case BELOW -> {
                    x = anchor.centerX() - cardWidth / 2;
                    y = anchor.bottom() + CARD_GAP;
                }
                case ABOVE -> {
                    x = anchor.centerX() - cardWidth / 2;
                    y = anchor.y() - CARD_GAP - cardHeight;
                }
                default -> throw new IllegalStateException("unknown context card placement");
            }
            naturalPlacements.add(new NaturalPlacement(placement, x, y));
            candidateXs.add(clamp(x, SCREEN_PADDING, screenWidth - SCREEN_PADDING - cardWidth));
            candidateYs.add(clamp(y, SCREEN_PADDING, screenHeight - SCREEN_PADDING - cardHeight));
        }
        candidateXs.add(SCREEN_PADDING);
        candidateXs.add(screenWidth - SCREEN_PADDING - cardWidth);
        candidateYs.add(SCREEN_PADDING);
        candidateYs.add(screenHeight - SCREEN_PADDING - cardHeight);
        for (ResearchTreeScreenLayout.Rect obstacle : avoided) {
            candidateXs.add(clamp(
                    obstacle.right() + CARD_GAP,
                    SCREEN_PADDING,
                    screenWidth - SCREEN_PADDING - cardWidth));
            candidateXs.add(clamp(
                    obstacle.x() - CARD_GAP - cardWidth,
                    SCREEN_PADDING,
                    screenWidth - SCREEN_PADDING - cardWidth));
            candidateYs.add(clamp(
                    obstacle.bottom() + CARD_GAP,
                    SCREEN_PADDING,
                    screenHeight - SCREEN_PADDING - cardHeight));
            candidateYs.add(clamp(
                    obstacle.y() - CARD_GAP - cardHeight,
                    SCREEN_PADDING,
                    screenHeight - SCREEN_PADDING - cardHeight));
        }

        Candidate best = null;
        for (int x : candidateXs) {
            for (int y : candidateYs) {
                ResearchTreeScreenLayout.Rect bounds = new ResearchTreeScreenLayout.Rect(
                        x, y, cardWidth, cardHeight);
                Placement placement = placementFor(bounds, anchor);
                NaturalPlacement natural = naturalPlacements.get(placement.ordinal());
                long displacement = Math.abs((long) x - natural.x())
                        + Math.abs((long) y - natural.y());
                long overlap = overlapArea(bounds, anchor);
                for (ResearchTreeScreenLayout.Rect obstacle : avoided) {
                    overlap += overlapArea(bounds, obstacle);
                }
                Candidate candidate = new Candidate(
                        placement,
                        bounds,
                        overlap * 1_000L + displacement * 10L + placement.ordinal());
                if (best == null || candidate.score() < best.score()) {
                    best = candidate;
                }
            }
        }

        ResearchTreeScreenLayout.Rect card = best.bounds();
        ResearchTreeScreenLayout.Rect icon = new ResearchTreeScreenLayout.Rect(
                card.x() + CARD_PADDING, card.y() + CARD_PADDING, 16, 16);
        ResearchTreeScreenLayout.Rect returnAction = new ResearchTreeScreenLayout.Rect(
                card.right() - CARD_PADDING - RETURN_ACTION_SIZE,
                card.y() + 5,
                RETURN_ACTION_SIZE,
                RETURN_ACTION_SIZE);
        ResearchTreeScreenLayout.Rect name = new ResearchTreeScreenLayout.Rect(
                icon.right() + 5,
                card.y() + CARD_PADDING,
                Math.max(1, returnAction.x() - icon.right() - 9),
                10);
        ResearchTreeScreenLayout.Rect status = new ResearchTreeScreenLayout.Rect(
                card.x() + CARD_PADDING, card.y() + 28, card.width() - CARD_PADDING * 2, 10);
        ResearchTreeScreenLayout.Rect summary = new ResearchTreeScreenLayout.Rect(
                card.x() + CARD_PADDING, card.y() + 42, card.width() - CARD_PADDING * 2, 10);
        ResearchTreeScreenLayout.Rect balance = exactPreview
                ? new ResearchTreeScreenLayout.Rect(
                        card.x() + CARD_PADDING, card.y() + 54,
                        card.width() - CARD_PADDING * 2, 10)
                : null;
        List<ResearchTreeScreenLayout.Rect> ingredientSlots = new ArrayList<>(ingredientCount);
        if (exactPreview) {
            int contentWidth = card.width() - CARD_PADDING * 2;
            int columnGap = columns == 2 ? 4 : 0;
            int columnWidth = (contentWidth - columnGap) / columns;
            for (int index = 0; index < ingredientCount; index++) {
                int column = index % columns;
                int row = index / columns;
                ingredientSlots.add(new ResearchTreeScreenLayout.Rect(
                        card.x() + CARD_PADDING + column * (columnWidth + columnGap),
                        card.y() + ingredientStart + row * INGREDIENT_ROW_HEIGHT,
                        columnWidth,
                        18));
            }
        }
        ResearchTreeScreenLayout.Rect action = exactPreview
                ? new ResearchTreeScreenLayout.Rect(
                        card.right() - CARD_PADDING - ACTION_WIDTH,
                        card.bottom() - CARD_PADDING - ACTION_HEIGHT,
                        ACTION_WIDTH,
                        ACTION_HEIGHT)
                : null;
        ResearchTreeScreenLayout.Rect readiness = exactPreview
                ? new ResearchTreeScreenLayout.Rect(
                        card.x() + CARD_PADDING,
                        action.y() + 5,
                        Math.max(1, action.x() - card.x() - CARD_PADDING * 2),
                        10)
                : null;
        return new Layout(
                card, icon, name, status, summary, balance, returnAction,
                ingredientSlots, readiness, action, best.placement(), columns);
    }

    public static boolean isAnchorVisible(
            Anchor anchor,
            int screenWidth,
            int screenHeight,
            List<ResearchTreeScreenLayout.Rect> covered) {
        validate(screenWidth, screenHeight, anchor, covered, 0);
        if (anchor.x() < 0 || anchor.y() < 0
                || anchor.right() > screenWidth || anchor.bottom() > screenHeight) {
            return false;
        }
        ResearchTreeScreenLayout.Rect anchorBounds = new ResearchTreeScreenLayout.Rect(
                anchor.x(), anchor.y(), anchor.width(), anchor.height());
        return covered.stream().noneMatch(rectangle -> rectangle.overlaps(anchorBounds));
    }

    private static void validate(
            int screenWidth,
            int screenHeight,
            Anchor anchor,
            List<ResearchTreeScreenLayout.Rect> avoided,
            int ingredientCount) {
        if (screenWidth < ResearchTreeScreenLayout.MIN_FULLSCREEN_WIDTH
                || screenHeight < ResearchTreeScreenLayout.MIN_FULLSCREEN_HEIGHT
                || anchor == null || avoided == null
                || avoided.stream().anyMatch(java.util.Objects::isNull)
                || ingredientCount < 0 || ingredientCount > MAX_INGREDIENTS) {
            throw new IllegalArgumentException("invalid Research Tree context card inputs");
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static Placement placementFor(
            ResearchTreeScreenLayout.Rect card,
            Anchor anchor) {
        int horizontal = card.x() + card.width() / 2 - anchor.centerX();
        int vertical = card.y() + card.height() / 2 - anchor.centerY();
        if (Math.abs(horizontal) >= Math.abs(vertical)) {
            return horizontal >= 0 ? Placement.RIGHT : Placement.LEFT;
        }
        return vertical >= 0 ? Placement.BELOW : Placement.ABOVE;
    }

    private static long overlapArea(
            ResearchTreeScreenLayout.Rect first,
            ResearchTreeScreenLayout.Rect second) {
        int width = Math.max(0, Math.min(first.right(), second.right())
                - Math.max(first.x(), second.x()));
        int height = Math.max(0, Math.min(first.bottom(), second.bottom())
                - Math.max(first.y(), second.y()));
        return (long) width * height;
    }

    private static long overlapArea(
            ResearchTreeScreenLayout.Rect first,
            Anchor second) {
        int width = Math.max(0, Math.min(first.right(), second.right())
                - Math.max(first.x(), second.x()));
        int height = Math.max(0, Math.min(first.bottom(), second.bottom())
                - Math.max(first.y(), second.y()));
        return (long) width * height;
    }

    public record Anchor(int x, int y, int width, int height) {
        public Anchor {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("invalid Research Tree context card anchor");
            }
        }

        public int right() {
            return Math.addExact(x, width);
        }

        public int bottom() {
            return Math.addExact(y, height);
        }

        public int centerX() {
            return x + width / 2;
        }

        public int centerY() {
            return y + height / 2;
        }
    }

    public record Layout(
            ResearchTreeScreenLayout.Rect card,
            ResearchTreeScreenLayout.Rect icon,
            ResearchTreeScreenLayout.Rect name,
            ResearchTreeScreenLayout.Rect status,
            ResearchTreeScreenLayout.Rect summary,
            ResearchTreeScreenLayout.Rect balance,
            ResearchTreeScreenLayout.Rect returnAction,
            List<ResearchTreeScreenLayout.Rect> ingredients,
            ResearchTreeScreenLayout.Rect readiness,
            ResearchTreeScreenLayout.Rect action,
            Placement placement,
            int columns) {
        public Layout {
            ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
            if (card == null || icon == null || name == null || status == null || summary == null
                    || returnAction == null
                    || ingredients.stream().anyMatch(java.util.Objects::isNull)
                    || placement == null || columns < 1 || columns > 2
                    || !card.contains(icon) || !card.contains(name)
                    || !card.contains(status) || !card.contains(summary)
                    || !card.contains(returnAction) || returnAction.overlaps(icon)
                    || returnAction.overlaps(name)
                    || (balance != null && !card.contains(balance))
                    || ingredients.stream().anyMatch(slot -> !card.contains(slot))
                    || (readiness != null && !card.contains(readiness))
                    || (action != null && !card.contains(action))
                    || (action == null) != (readiness == null)
                    || (action == null) != (balance == null)) {
                throw new IllegalArgumentException("invalid Research Tree context card layout");
            }
        }

        public boolean exactPreview() {
            return action != null;
        }
    }

    public enum Placement {
        RIGHT,
        LEFT,
        BELOW,
        ABOVE
    }

    private record Candidate(
            Placement placement,
            ResearchTreeScreenLayout.Rect bounds,
            long score) {
    }

    private record NaturalPlacement(Placement placement, int x, int y) {
        private NaturalPlacement {
            if (placement == null) {
                throw new IllegalArgumentException("context card placement cannot be null");
            }
        }
    }
}
