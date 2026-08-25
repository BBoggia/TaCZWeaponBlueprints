package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

/** Semantic UI contract kept independent from colors, textures and widgets. */
public final class ResearchTreePresentationContract {
    public static final String UNDISCLOSED_CATEGORY_LANE = "undisclosed";
    public static final double MIN_COMPACT_CARD_SCALE = 0.10D;
    public static final double MIN_DETAILED_CARD_SCALE = 0.30D;
    public static final BrowseView DEFAULT_BROWSE_VIEW = BrowseView.BRANCHES;
    public static final ProgressionDirection PROGRESSION_DIRECTION =
            ProgressionDirection.BOTTOM_TO_TOP;
    public static final DetailSurface DEFAULT_DETAIL_SURFACE = DetailSurface.HOVER_TOOLTIP;

    private ResearchTreePresentationContract() {
    }

    /** Category grouping must never recover data redacted by the server. */
    public static String categoryLane(ResearchTreeGraph.Node node) {
        if (node == null) {
            throw new IllegalArgumentException("Research Tree node cannot be null");
        }
        return node.visibility().revealsIdentity()
                ? node.itemType()
                : UNDISCLOSED_CATEGORY_LANE;
    }

    /** Color-independent symbol selected from synchronized, disclosure-safe state. */
    public static StatusSymbol statusSymbol(ResearchTreeGraph.Node node, boolean canAffordPoints) {
        if (node == null) {
            throw new IllegalArgumentException("Research Tree node cannot be null");
        }
        return switch (node.availability()) {
            case REDACTED -> StatusSymbol.UNKNOWN;
            case PREVIEW -> StatusSymbol.PREVIEW;
            case LEARNED -> StatusSymbol.LEARNED;
            case AVAILABLE -> canAffordPoints ? StatusSymbol.AVAILABLE : StatusSymbol.POINTS_REQUIRED;
            case DISCOVERY_REQUIRED -> StatusSymbol.DISCOVERY_REQUIRED;
            case PREREQUISITES_REQUIRED -> StatusSymbol.PREREQUISITES_REQUIRED;
            case RESEARCH_DISABLED -> StatusSymbol.RESEARCH_DISABLED;
            case COST_ABOVE_CAP -> StatusSymbol.COST_ABOVE_CAP;
            case CONTENT_UNAVAILABLE -> StatusSymbol.CONTENT_UNAVAILABLE;
        };
    }

    public static EscapeAction escapeAction(boolean fullscreen) {
        return fullscreen ? EscapeAction.EXIT_FULLSCREEN : EscapeAction.CLOSE_BENCH;
    }

    /**
     * Selecting a sidebar group changes the projection in Branches and only
     * moves the camera in All Weapons. Both views retain one authoritative graph.
     */
    public static GroupSelectionAction groupSelectionAction(BrowseView view) {
        if (view == null) {
            throw new IllegalArgumentException("Research Tree browse view cannot be null");
        }
        return view == BrowseView.BRANCHES
                ? GroupSelectionAction.SHOW_GROUP
                : GroupSelectionAction.FOCUS_GROUP_REGION;
    }

    /**
     * Higher progression tiers are drawn above lower tiers without changing
     * the prerequisite-depth meaning of the tier number.
     */
    public static boolean tierAppearsAbove(int candidateTier, int referenceTier) {
        if (candidateTier < 0 || referenceTier < 0) {
            throw new IllegalArgumentException("Research Tree tiers cannot be negative");
        }
        return candidateTier > referenceTier;
    }

    /** Group membership must not become a side channel around Journal visibility. */
    public static GroupDisclosure groupDisclosure(JournalVisibility visibility) {
        if (visibility == null) {
            throw new IllegalArgumentException("Research Tree visibility cannot be null");
        }
        if (!visibility.appearsInTree()) {
            return GroupDisclosure.OMITTED;
        }
        return visibility.revealsIdentity()
                ? GroupDisclosure.AUTHORED
                : GroupDisclosure.UNDISCLOSED;
    }

    /** A click pins the same concise information that hover presents temporarily. */
    public static DetailSurface detailSurface(boolean pinned) {
        return pinned ? DetailSurface.PINNED_CARD : DEFAULT_DETAIL_SURFACE;
    }

    /** Reduces card complexity only when zoom makes its details unreadable anyway. */
    public static CardDetail cardDetail(double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0D) {
            throw new IllegalArgumentException("Research Tree card scale must be positive and finite");
        }
        if (scale >= MIN_DETAILED_CARD_SCALE) {
            return CardDetail.DETAILED;
        }
        return scale >= MIN_COMPACT_CARD_SCALE ? CardDetail.COMPACT : CardDetail.OVERVIEW;
    }

    /** Primary player-facing next step for the focused public node. */
    public static NextAction nextAction(ResearchTreeGraph.Node node, boolean canAffordPoints) {
        if (node == null) {
            throw new IllegalArgumentException("Research Tree node cannot be null");
        }
        return switch (node.availability()) {
            case REDACTED -> NextAction.FOLLOW_PATH;
            case PREVIEW -> NextAction.INSPECT_REQUIREMENTS;
            case LEARNED -> NextAction.ALREADY_LEARNED;
            case AVAILABLE -> canAffordPoints
                    ? NextAction.PREPARE_MATERIALS
                    : NextAction.EARN_POINTS;
            case DISCOVERY_REQUIRED -> NextAction.DISCOVER_WEAPON;
            case PREREQUISITES_REQUIRED -> NextAction.COMPLETE_REQUIREMENTS;
            case RESEARCH_DISABLED -> NextAction.RESEARCH_DISABLED;
            case COST_ABOVE_CAP -> NextAction.COST_UNAVAILABLE;
            case CONTENT_UNAVAILABLE -> NextAction.CONTENT_UNAVAILABLE;
        };
    }

    /** Priority order for relationship-aware emphasis around one focused node. */
    public enum RelationshipRole {
        SELECTED,
        DIRECT_REQUIREMENT,
        REQUIREMENT_PATH,
        DIRECT_UNLOCK,
        UNLOCK_PATH,
        UNRELATED,
        NEUTRAL
    }

    public enum StatusSymbol {
        UNKNOWN,
        PREVIEW,
        LEARNED,
        AVAILABLE,
        POINTS_REQUIRED,
        DISCOVERY_REQUIRED,
        PREREQUISITES_REQUIRED,
        RESEARCH_DISABLED,
        COST_ABOVE_CAP,
        CONTENT_UNAVAILABLE
    }

    public enum CardDetail {
        OVERVIEW,
        COMPACT,
        DETAILED
    }

    public enum NextAction {
        FOLLOW_PATH,
        INSPECT_REQUIREMENTS,
        ALREADY_LEARNED,
        PREPARE_MATERIALS,
        EARN_POINTS,
        DISCOVER_WEAPON,
        COMPLETE_REQUIREMENTS,
        RESEARCH_DISABLED,
        COST_UNAVAILABLE,
        CONTENT_UNAVAILABLE
    }

    public enum EscapeAction {
        EXIT_FULLSCREEN,
        CLOSE_BENCH
    }

    public enum BrowseView {
        BRANCHES,
        ALL_WEAPONS
    }

    public enum ProgressionDirection {
        BOTTOM_TO_TOP
    }

    public enum GroupSelectionAction {
        SHOW_GROUP,
        FOCUS_GROUP_REGION
    }

    public enum GroupDisclosure {
        OMITTED,
        UNDISCLOSED,
        AUTHORED
    }

    public enum DetailSurface {
        HOVER_TOOLTIP,
        PINNED_CARD
    }
}
