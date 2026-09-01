package com.gamergaming.taczweaponblueprints.client;

import java.util.List;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

/** Semantic UI contract kept independent from colors, textures and widgets. */
public final class ResearchTreePresentationContract {
    public static final String UNDISCLOSED_CATEGORY_LANE = "undisclosed";
    public static final double MIN_COMPACT_CARD_SCALE = 0.10D;
    public static final double MIN_DETAILED_CARD_SCALE = 0.30D;
    public static final double MIN_READABLE_OVERVIEW_FIT_SCALE = 0.25D;
    /**
     * Fullscreen graph content is deliberately translated behind ordinary GUI
     * surfaces. Minecraft item rendering raises models within the current pose,
     * so a generous negative offset is required instead of relying on call order.
     */
    public static final int FULLSCREEN_GRAPH_Z_OFFSET = -300;
    public static final int FULLSCREEN_OVERLAY_Z_OFFSET = 0;
    /**
     * Player-facing browse views. Branches and All Weapons remain implemented
     * as dormant compatibility projections, but are intentionally absent from
     * ordinary navigation until a future product decision exposes them again.
     */
    public static final List<BrowseView> PLAYER_BROWSE_VIEWS = List.of(BrowseView.TECH_TREE);
    public static final BrowseView DEFAULT_BROWSE_VIEW = BrowseView.TECH_TREE;
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

    /**
     * Small player-facing state vocabulary used by the graph. AVAILABLE means
     * that the node is worth inspecting; only a matching server preview may call
     * the selected blueprint ready to research.
     */
    public static PlayerStateFamily playerStateFamily(
            ResearchTreeGraph.Node node,
            boolean canAffordPoints) {
        if (node == null) {
            throw new IllegalArgumentException("Research Tree node cannot be null");
        }
        return switch (node.availability()) {
            case LEARNED -> PlayerStateFamily.LEARNED;
            case PREVIEW -> PlayerStateFamily.AVAILABLE;
            case AVAILABLE -> canAffordPoints
                    ? PlayerStateFamily.AVAILABLE
                    : PlayerStateFamily.LOCKED;
            case DISCOVERY_REQUIRED, PREREQUISITES_REQUIRED,
                    RESEARCH_DISABLED, COST_ABOVE_CAP -> PlayerStateFamily.LOCKED;
            case REDACTED, CONTENT_UNAVAILABLE ->
                    PlayerStateFamily.HIDDEN_OR_UNAVAILABLE;
        };
    }

    /**
     * The untouched graph teaches one stable glyph per major state family.
     * Detailed causes remain available to hover and the selected context.
     */
    public static StatusSymbol graphStatusSymbol(
            ResearchTreeGraph.Node node,
            boolean canAffordPoints) {
        return switch (playerStateFamily(node, canAffordPoints)) {
            case LEARNED -> StatusSymbol.LEARNED;
            case AVAILABLE -> StatusSymbol.AVAILABLE;
            case LOCKED -> StatusSymbol.LOCKED;
            case HIDDEN_OR_UNAVAILABLE -> StatusSymbol.UNKNOWN;
        };
    }

    /** Minimum surface on which ordinary players need each kind of information. */
    public static InformationSurface informationSurface(PlayerInformation information) {
        if (information == null) {
            throw new IllegalArgumentException("Research Tree information cannot be null");
        }
        return switch (information) {
            case PUBLIC_IDENTITY, MAJOR_STATE, CONNECTIONS -> InformationSurface.GRAPH;
            case NAME, ONE_LINE_STATUS -> InformationSurface.HOVER;
            case EXACT_LOCK_REASON, POINT_COST, MATERIAL_REQUIREMENTS,
                    DIRECT_REQUIREMENTS, IMMEDIATE_UNLOCKS, PRIMARY_ACTION ->
                    InformationSurface.SELECTED_CARD;
            case STATUS_LEGEND, ADVANCED_LAYOUT_CONTROLS ->
                    InformationSurface.HELP_OR_SETTINGS;
        };
    }

    public static EscapeAction escapeAction(boolean fullscreen) {
        return fullscreen ? EscapeAction.EXIT_FULLSCREEN : EscapeAction.CLOSE_BENCH;
    }

    /** Compact rendering stays on the ordinary GUI layer. */
    public static int graphZOffset(boolean fullscreen) {
        return fullscreen ? FULLSCREEN_GRAPH_Z_OFFSET : FULLSCREEN_OVERLAY_Z_OFFSET;
    }

    /** Compatibility overload for callers that do not expose the optional Tech Tree view. */
    public static FullscreenViewAction fullscreenViewAction(BrowseView currentView) {
        return fullscreenViewAction(currentView, false);
    }

    /** The first fullscreen rail entry advances through every currently available browse view. */
    public static FullscreenViewAction fullscreenViewAction(
            BrowseView currentView,
            boolean techTreeAvailable) {
        if (currentView == null) {
            throw new IllegalArgumentException("Research Tree browse view cannot be null");
        }
        return switch (currentView) {
            case BRANCHES -> FullscreenViewAction.SHOW_ALL_WEAPONS;
            case ALL_WEAPONS -> techTreeAvailable
                    ? FullscreenViewAction.SHOW_TECH_TREE
                    : FullscreenViewAction.SHOW_BRANCHES;
            case TECH_TREE -> FullscreenViewAction.SHOW_BRANCHES;
        };
    }

    /** Whether ordinary UI should expose a control for changing projections. */
    public static boolean browseViewSelectorVisible() {
        return PLAYER_BROWSE_VIEWS.size() > 1;
    }

    /** Whether dormant legacy projections are reachable through ordinary UI. */
    public static boolean legacyBrowseViewsVisible() {
        return PLAYER_BROWSE_VIEWS.contains(BrowseView.BRANCHES)
                || PLAYER_BROWSE_VIEWS.contains(BrowseView.ALL_WEAPONS);
    }

    /** Keeps restored client state inside the current player-facing view set. */
    public static BrowseView retainPlayerBrowseView(BrowseView candidate) {
        return PLAYER_BROWSE_VIEWS.contains(candidate) ? candidate : DEFAULT_BROWSE_VIEW;
    }

    /** Stable cycle through only the views currently exposed to players. */
    public static BrowseView nextBrowseView(BrowseView currentView, boolean techTreeAvailable) {
        BrowseView retained = retainPlayerBrowseView(currentView);
        int current = PLAYER_BROWSE_VIEWS.indexOf(retained);
        return PLAYER_BROWSE_VIEWS.get((current + 1) % PLAYER_BROWSE_VIEWS.size());
    }

    /** Keeps a restored per-view camera authoritative across projection changes. */
    public static CameraArrivalAction cameraArrivalAction(
            boolean cameraRestored,
            boolean preferredFocusVisible) {
        if (cameraRestored) {
            return preferredFocusVisible
                    ? CameraArrivalAction.RETAIN_CAMERA_AND_FOCUS
                    : CameraArrivalAction.RETAIN_CAMERA;
        }
        return preferredFocusVisible
                ? CameraArrivalAction.FOCUS_PREFERRED
                : CameraArrivalAction.FOCUS_FALLBACK;
    }

    /** Distinguishes an empty curated overview from an empty server publication. */
    public static EmptyTreeState emptyTreeState(BrowseView view, boolean publicationEmpty) {
        if (view == null) {
            throw new IllegalArgumentException("Research Tree browse view cannot be null");
        }
        if (publicationEmpty) {
            return EmptyTreeState.EMPTY_PUBLICATION;
        }
        return switch (view) {
            case BRANCHES -> EmptyTreeState.EMPTY_PUBLICATION;
            case ALL_WEAPONS -> EmptyTreeState.EMPTY_OVERVIEW;
            case TECH_TREE -> EmptyTreeState.EMPTY_TECH_TREE;
        };
    }

    /**
     * Authored and generated identity-visible weapon groups form All Weapons by
     * default. Undisclosed groups remain Branches-only so overview membership
     * cannot become an identity side channel.
     */
    public static boolean includedInOverviewByDefault(ResearchTreePresentation.Kind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("Research Tree group kind cannot be null");
        }
        return kind.includedInOverviewByDefault();
    }

    /**
     * Selecting a sidebar group changes the projection in Branches and only
     * moves the camera in All Weapons. Both views retain one authoritative graph.
     */
    public static GroupSelectionAction groupSelectionAction(BrowseView view) {
        return groupSelectionAction(view, true);
    }

    /** Excluded overview groups open their complete Branches projection. */
    public static GroupSelectionAction groupSelectionAction(
            BrowseView view,
            boolean includedInOverview) {
        if (view == null) {
            throw new IllegalArgumentException("Research Tree browse view cannot be null");
        }
        if (view == BrowseView.TECH_TREE) {
            throw new IllegalArgumentException(
                    "Tech Tree domain selection is not branch group selection");
        }
        return view == BrowseView.BRANCHES || !includedInOverview
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

    /** Controls connector density without hiding a focused relationship path. */
    public static boolean edgeVisible(CardDetail detail, RelationshipRole role) {
        if (detail == null || role == null) {
            throw new IllegalArgumentException("Research Tree edge detail cannot be null");
        }
        return switch (detail) {
            case DETAILED -> true;
            case COMPACT -> role != RelationshipRole.UNRELATED;
            case OVERVIEW -> role != RelationshipRole.UNRELATED
                    && role != RelationshipRole.NEUTRAL;
        };
    }

    /** Graph-space labels progressively reveal structure before individual tiers. */
    public static GraphLabels graphLabels(CardDetail detail) {
        if (detail == null) {
            throw new IllegalArgumentException("Research Tree graph detail cannot be null");
        }
        return switch (detail) {
            case OVERVIEW -> GraphLabels.NONE;
            case COMPACT -> GraphLabels.STRUCTURE;
            case DETAILED -> GraphLabels.FULL;
        };
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
        /** One viable member of an inclusive any-of requirement group. */
        ALTERNATIVE_REQUIREMENT,
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
        LOCKED,
        POINTS_REQUIRED,
        DISCOVERY_REQUIRED,
        PREREQUISITES_REQUIRED,
        RESEARCH_DISABLED,
        COST_ABOVE_CAP,
        CONTENT_UNAVAILABLE
    }

    public enum PlayerStateFamily {
        LEARNED,
        AVAILABLE,
        LOCKED,
        HIDDEN_OR_UNAVAILABLE
    }

    public enum PlayerInformation {
        PUBLIC_IDENTITY,
        MAJOR_STATE,
        CONNECTIONS,
        NAME,
        ONE_LINE_STATUS,
        EXACT_LOCK_REASON,
        POINT_COST,
        MATERIAL_REQUIREMENTS,
        DIRECT_REQUIREMENTS,
        IMMEDIATE_UNLOCKS,
        PRIMARY_ACTION,
        STATUS_LEGEND,
        ADVANCED_LAYOUT_CONTROLS
    }

    public enum InformationSurface {
        GRAPH,
        HOVER,
        SELECTED_CARD,
        HELP_OR_SETTINGS
    }

    public enum CardDetail {
        OVERVIEW,
        COMPACT,
        DETAILED
    }

    public enum GraphLabels {
        NONE,
        STRUCTURE,
        FULL
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
        ALL_WEAPONS,
        TECH_TREE
    }

    public enum FullscreenViewAction {
        SHOW_ALL_WEAPONS,
        SHOW_BRANCHES,
        SHOW_TECH_TREE
    }

    public enum CameraArrivalAction {
        RETAIN_CAMERA,
        RETAIN_CAMERA_AND_FOCUS,
        FOCUS_PREFERRED,
        FOCUS_FALLBACK
    }

    public enum EmptyTreeState {
        EMPTY_PUBLICATION,
        EMPTY_OVERVIEW,
        EMPTY_TECH_TREE
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
