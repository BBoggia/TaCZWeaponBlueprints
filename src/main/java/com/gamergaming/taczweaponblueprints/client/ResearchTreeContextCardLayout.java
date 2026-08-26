package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;

/** Pure adaptive geometry for the pinned fullscreen Research Tree node card. */
public final class ResearchTreeContextCardLayout {
    public static final int MAX_INGREDIENTS = 6;
    public static final int CARD_MAX_WIDTH = 224;
    public static final int CARD_MIN_WIDTH = 176;
    public static final int CARD_GAP = 8;
    public static final int SCREEN_PADDING = 4;
    public static final int ACTION_HEIGHT = 20;
    public static final int RETURN_CHIP_WIDTH = 116;
    public static final int RETURN_CHIP_HEIGHT = 20;
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

        Candidate best = null;
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
            int clampedX = clamp(x, SCREEN_PADDING, screenWidth - SCREEN_PADDING - cardWidth);
            int clampedY = clamp(y, SCREEN_PADDING, screenHeight - SCREEN_PADDING - cardHeight);
            ResearchTreeScreenLayout.Rect bounds = new ResearchTreeScreenLayout.Rect(
                    clampedX, clampedY, cardWidth, cardHeight);
            long displacement = Math.abs((long) clampedX - x) + Math.abs((long) clampedY - y);
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

        ResearchTreeScreenLayout.Rect card = best.bounds();
        ResearchTreeScreenLayout.Rect icon = new ResearchTreeScreenLayout.Rect(
                card.x() + CARD_PADDING, card.y() + CARD_PADDING, 16, 16);
        ResearchTreeScreenLayout.Rect name = new ResearchTreeScreenLayout.Rect(
                icon.right() + 5, card.y() + CARD_PADDING, card.width() - 35, 10);
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
                card, icon, name, status, summary, balance,
                ingredientSlots, readiness, action, best.placement(), columns);
    }

    public static boolean isAnchorVisible(
            Anchor anchor,
            int screenWidth,
            int screenHeight,
            List<ResearchTreeScreenLayout.Rect> covered) {
        validate(screenWidth, screenHeight, anchor, covered, 0);
        boolean intersectsScreen = anchor.right() > 0 && anchor.bottom() > 0
                && anchor.x() < screenWidth && anchor.y() < screenHeight;
        if (!intersectsScreen) {
            return false;
        }
        return covered.stream().noneMatch(rectangle ->
                rectangle.contains(anchor.centerX(), anchor.centerY()));
    }

    public static ResearchTreeScreenLayout.Rect returnChip(
            int screenWidth,
            int screenHeight,
            List<ResearchTreeScreenLayout.Rect> avoided) {
        if (avoided == null || avoided.stream().anyMatch(java.util.Objects::isNull)
                || screenWidth < RETURN_CHIP_WIDTH + SCREEN_PADDING * 2
                || screenHeight < RETURN_CHIP_HEIGHT + SCREEN_PADDING * 2) {
            throw new IllegalArgumentException("invalid Research Tree return chip bounds");
        }
        List<ResearchTreeScreenLayout.Rect> candidates = List.of(
                new ResearchTreeScreenLayout.Rect(
                        screenWidth - SCREEN_PADDING - RETURN_CHIP_WIDTH,
                        screenHeight - SCREEN_PADDING - RETURN_CHIP_HEIGHT,
                        RETURN_CHIP_WIDTH, RETURN_CHIP_HEIGHT),
                new ResearchTreeScreenLayout.Rect(
                        SCREEN_PADDING,
                        screenHeight - SCREEN_PADDING - RETURN_CHIP_HEIGHT,
                        RETURN_CHIP_WIDTH, RETURN_CHIP_HEIGHT),
                new ResearchTreeScreenLayout.Rect(
                        screenWidth - SCREEN_PADDING - RETURN_CHIP_WIDTH,
                        SCREEN_PADDING,
                        RETURN_CHIP_WIDTH, RETURN_CHIP_HEIGHT),
                new ResearchTreeScreenLayout.Rect(
                        SCREEN_PADDING, SCREEN_PADDING,
                        RETURN_CHIP_WIDTH, RETURN_CHIP_HEIGHT));
        ResearchTreeScreenLayout.Rect best = candidates.get(0);
        long bestOverlap = Long.MAX_VALUE;
        for (ResearchTreeScreenLayout.Rect candidate : candidates) {
            long overlap = avoided.stream()
                    .mapToLong(obstacle -> overlapArea(candidate, obstacle))
                    .sum();
            if (overlap < bestOverlap) {
                best = candidate;
                bestOverlap = overlap;
            }
        }
        return best;
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
            List<ResearchTreeScreenLayout.Rect> ingredients,
            ResearchTreeScreenLayout.Rect readiness,
            ResearchTreeScreenLayout.Rect action,
            Placement placement,
            int columns) {
        public Layout {
            ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
            if (card == null || icon == null || name == null || status == null || summary == null
                    || ingredients.stream().anyMatch(java.util.Objects::isNull)
                    || placement == null || columns < 1 || columns > 2
                    || !card.contains(icon) || !card.contains(name)
                    || !card.contains(status) || !card.contains(summary)
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
}
