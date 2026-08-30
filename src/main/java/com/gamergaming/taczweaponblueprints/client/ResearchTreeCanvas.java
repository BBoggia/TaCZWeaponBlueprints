package com.gamergaming.taczweaponblueprints.client;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Reusable rendering and interaction surface for one published Research Tree. */
public final class ResearchTreeCanvas {
    private static final int FOCUS_PADDING = 16;
    private static final int TIER_GUIDE_PADDING = 16;
    private static final int TIER_GUIDE_GAP = 32;
    private static final int TIER_LABEL_GUTTER_WIDTH = 28;
    static final int STICKY_HEADER_HEIGHT = 14;
    static final int STICKY_GUTTER_WIDTH = 24;
    static final int PORTAL_SIZE = ResearchTreeLayout.PORTAL_SIZE;
    static final int MIN_NODE_HIT_SIZE = 16;
    static final int MIN_PORTAL_HIT_SIZE = 14;
    static final int MAX_VISIBLE_PORTALS_PER_BANK = 7;
    private static final int PORTAL_GAP = ResearchTreeLayout.PORTAL_GAP;
    private final ResearchTreeViewState state;
    private final Style style;
    private ResearchTreeScreenLayout.ViewMode viewMode = ResearchTreeScreenLayout.ViewMode.COMPACT;
    private ResearchTreeScreenLayout.Rect bounds = ResearchTreeScreenLayout.compact().canvas();
    private ResearchTreeDisplayPolicy displayPolicy = ResearchTreeDisplayPolicy.DEFAULT;
    private ResearchTreeGraph graph = ResearchTreeGraph.EMPTY;
    private ResearchTreeLayout layout = ResearchTreeLayout.EMPTY;
    private ResearchTreeEdgeIndex edgeIndex = ResearchTreeEdgeIndex.EMPTY;
    private ResearchTreeEdgeIndex.RoutingProfile edgeRoutingProfile =
            ResearchTreeEdgeIndex.RoutingProfile.AUTO;
    private ResearchTreeEdgeIndex.RoutingProfile indexedEdgeRoutingProfile =
            ResearchTreeEdgeIndex.RoutingProfile.AUTO;
    private ResearchTreeNodeIndex nodeIndex = ResearchTreeNodeIndex.EMPTY;
    private ResearchTreeRelations relations = ResearchTreeRelations.EMPTY;
    private ResearchTreeRelations.FocusPath focusPath = ResearchTreeRelations.FocusPath.EMPTY;
    private ResearchTreeRelations.FocusPath hoverPath = ResearchTreeRelations.FocusPath.EMPTY;
    private ResourceLocation trackedTargetId;
    private Set<ResourceLocation> trackedPathNodeIds = Set.of();
    private Set<ResearchTreeGraph.Edge> trackedPathEdges = Set.of();
    private Map<ResourceLocation, ItemStack> icons = Map.of();
    private Map<ResourceLocation, Integer> boundaryRequirementCounts = Map.of();
    private Map<ResourceLocation, Integer> boundaryUnlockCounts = Map.of();
    private List<HiddenAnchorSpan> hiddenAnchorSpans = List.of();
    private List<PortalPlacement> portals = List.of();
    private ResearchTechTreeLayout techTreeLayout;
    private List<ResearchTechTreeLayout.BoundaryPortal> techTreePortals = List.of();
    private ResourceLocation hoveredId;
    private ResourceLocation authoritativeSelectedId;
    private ResourceLocation activeSearchMatch;
    private String categoryFilter;
    private boolean unifiedOverview;
    private boolean dragging;
    private int dragButton = -1;

    public ResearchTreeCanvas(ResearchTreeViewState state, Style style) {
        if (state == null || style == null) {
            throw new IllegalArgumentException("Research Tree canvas requires state and style");
        }
        this.state = state;
        this.style = style;
        configureViewport();
    }

    public void setBounds(
            ResearchTreeScreenLayout.ViewMode viewMode,
            ResearchTreeScreenLayout.Rect bounds) {
        if (viewMode == null || bounds == null) {
            throw new IllegalArgumentException("Research Tree canvas bounds cannot be null");
        }
        this.viewMode = viewMode;
        this.bounds = bounds;
        cancelInteraction();
        applyCameraAnimationPolicy();
        configureViewport();
    }

    /** Applies client-only motion and decoration preferences without rebuilding topology. */
    public void setDisplayPolicy(ResearchTreeDisplayPolicy displayPolicy) {
        if (displayPolicy == null) {
            throw new IllegalArgumentException("Research Tree display policy cannot be null");
        }
        this.displayPolicy = displayPolicy;
        applyCameraAnimationPolicy();
    }

    public ResearchTreeDisplayPolicy displayPolicy() {
        return displayPolicy;
    }

    private void applyCameraAnimationPolicy() {
        viewport().setAnimated(
                viewMode == ResearchTreeScreenLayout.ViewMode.FULLSCREEN
                        && displayPolicy.cameraAnimationEnabled());
    }

    /** Returns true when the newly published graph requires a different layout topology. */
    public boolean setContent(
            ResearchTreeGraph graph,
            ResearchTreeLayout layout,
            Map<ResourceLocation, ItemStack> icons,
            ResourceLocation preferredFocus) {
        return setContent(graph, layout, icons, preferredFocus, preferredFocus);
    }

    public boolean setContent(
            ResearchTreeGraph graph,
            ResearchTreeLayout layout,
            Map<ResourceLocation, ItemStack> icons,
            ResourceLocation preferredFocus,
            ResourceLocation authoritativeSelection) {
        return setContent(
                graph, layout, icons, preferredFocus, authoritativeSelection, List.of());
    }

    public boolean setContent(
            ResearchTreeGraph graph,
            ResearchTreeLayout layout,
            Map<ResourceLocation, ItemStack> icons,
            ResourceLocation preferredFocus,
            ResourceLocation authoritativeSelection,
            List<ResearchTreeProjection.CrossGroupLink> crossGroupLinks) {
        if (graph == null || layout == null || icons == null
                || crossGroupLinks == null
                || crossGroupLinks.stream().anyMatch(java.util.Objects::isNull)
                || graph.nodes().size() != layout.nodes().size()) {
            throw new IllegalArgumentException("invalid Research Tree canvas content");
        }
        for (int ordinal = 0; ordinal < graph.nodes().size(); ordinal++) {
            if (!graph.nodes().get(ordinal).blueprintId()
                    .equals(layout.nodes().get(ordinal).blueprintId())) {
                throw new IllegalArgumentException("Research Tree graph and layout do not match");
            }
        }
        for (Map.Entry<ResourceLocation, ItemStack> entry : icons.entrySet()) {
            ResearchTreeGraph.Node node = graph.node(entry.getKey()).orElseThrow(() ->
                    new IllegalArgumentException("Research Tree icon references an unknown node"));
            if (entry.getValue() == null || !node.visibility().revealsIcon()) {
                throw new IllegalArgumentException("Research Tree icon violates node disclosure");
            }
        }
        Map<ResourceLocation, ItemStack> nextIcons = Map.copyOf(icons);
        List<ResearchTreeProjection.CrossGroupLink> nextCrossGroupLinks =
                List.copyOf(crossGroupLinks);
        List<PortalPlacement> nextPortals = placePortals(
                graph, layout, nextCrossGroupLinks);
        BoundaryRelationshipCounts nextBoundaryCounts =
                indexBoundaryRelationships(nextCrossGroupLinks);
        List<HiddenAnchorSpan> nextHiddenAnchorSpans = indexHiddenAnchors(layout);
        boolean topologyChanged = !this.graph.hasSameLayoutTopology(graph)
                || this.layout != layout
                || edgeRoutingProfile != indexedEdgeRoutingProfile;
        ResearchTreeEdgeIndex nextEdgeIndex = topologyChanged
                ? ResearchTreeEdgeIndex.create(graph, layout, edgeRoutingProfile) : edgeIndex;
        ResearchTreeNodeIndex nextNodeIndex = topologyChanged
                ? ResearchTreeNodeIndex.create(layout) : nodeIndex;
        ResearchTreeRelations nextRelations = topologyChanged
                ? ResearchTreeRelations.create(graph) : relations;
        ResourceLocation nextAuthoritativeSelection = graph.node(authoritativeSelection)
                .filter(node -> node.visibility().allowsServerSelection())
                .map(ResearchTreeGraph.Node::blueprintId)
                .orElse(null);

        // Commit only after every fallible derived object has been prepared.
        this.graph = graph;
        this.layout = layout;
        this.icons = nextIcons;
        this.boundaryRequirementCounts = nextBoundaryCounts.requirements();
        this.boundaryUnlockCounts = nextBoundaryCounts.unlocks();
        this.hiddenAnchorSpans = nextHiddenAnchorSpans;
        this.portals = nextPortals;
        this.techTreeLayout = null;
        this.techTreePortals = List.of();
        this.authoritativeSelectedId = nextAuthoritativeSelection;
        if (graph.node(activeSearchMatch).isEmpty()) {
            activeSearchMatch = null;
        }
        if (topologyChanged) {
            cancelInteraction();
            edgeIndex = nextEdgeIndex;
            indexedEdgeRoutingProfile = edgeRoutingProfile;
            nodeIndex = nextNodeIndex;
            relations = nextRelations;
        }
        state.retainVisibleNodes(graph, preferredFocus);
        if (activeSearchMatch != null && !state.searchMatches().contains(activeSearchMatch)) {
            activeSearchMatch = null;
        }
        refreshFocusPath();
        if (graph.node(hoveredId).isEmpty()) {
            clearHover();
        } else {
            hoverPath = relations.directFocus(hoveredId);
        }
        if (categoryFilter != null && layout.categoryLanes().stream()
                .noneMatch(lane -> lane.key().equals(categoryFilter))) {
            categoryFilter = null;
        }
        if (topologyChanged) {
            for (ResearchTreeScreenLayout.ViewMode mode
                    : ResearchTreeScreenLayout.ViewMode.values()) {
                ResearchTreeViewport viewport = state.viewport(mode);
                viewport.replaceCanvas(layout.width(), layout.height(), false);
                if (categoryFilter == null) {
                    fitViewport(viewport);
                } else {
                    ResearchTreeLayout.CategoryLane lane = layout.categoryLanes().stream()
                            .filter(candidate -> candidate.key().equals(categoryFilter))
                            .findFirst()
                            .orElseThrow();
                    viewport.fit(lane.x(), 0, lane.width(), layout.height());
                }
            }
        }
        configureViewport();
        return topologyChanged;
    }

    /**
     * Binds one already validated Tech Tree domain to the reusable graph canvas.
     * Typed boundary portals retain their authored domain target and exact unified geometry.
     */
    public boolean setTechContent(
            ResearchTechTreeProjection projection,
            ResearchTechTreeLayout techLayout,
            Map<ResourceLocation, ItemStack> icons,
            ResourceLocation preferredFocus,
            ResourceLocation authoritativeSelection) {
        if (projection == null || techLayout == null
                || projection.domain() != techLayout.domain()
                || !projection.graph().nodes().stream()
                        .map(ResearchTreeGraph.Node::blueprintId)
                        .toList()
                        .equals(techLayout.graphLayout().nodes().stream()
                                .map(ResearchTreeLayout.PositionedNode::blueprintId)
                                .toList())) {
            throw new IllegalArgumentException(
                    "Research Tech Tree projection and layout do not match");
        }
        Set<ResearchTechTreeProjection.BoundaryLink> expectedLinks =
                new java.util.LinkedHashSet<>(projection.boundaryLinks());
        Set<ResearchTechTreeProjection.BoundaryLink> positionedLinks =
                new java.util.LinkedHashSet<>();
        techLayout.portals().forEach(portal -> positionedLinks.addAll(portal.target().links()));
        if (!positionedLinks.equals(expectedLinks)) {
            throw new IllegalArgumentException(
                    "Research Tech Tree layout boundary portals do not match its projection");
        }
        for (ResearchTechTreeProjection.Placement placement : projection.placements().values()) {
            ResearchTreeLayout.PositionedNode position = techLayout.graphLayout()
                    .position(placement.nodeId()).orElseThrow();
            if (position.tier() < 0
                    || position.tier() >= techLayout.graphLayout().tierCount()) {
                throw new IllegalArgumentException(
                        "Research Tech Tree node leaves its rank layout");
            }
        }
        boolean topologyChanged = setContent(
                projection.graph(),
                techLayout.graphLayout(),
                icons,
                preferredFocus,
                authoritativeSelection,
                List.of());
        BoundaryRelationshipCounts boundaryCounts =
                indexTechBoundaryRelationships(techLayout.portals());
        this.techTreeLayout = techLayout;
        this.techTreePortals = techLayout.portals();
        this.boundaryRequirementCounts = boundaryCounts.requirements();
        this.boundaryUnlockCounts = boundaryCounts.unlocks();
        return topologyChanged;
    }

    public void render(
            GuiGraphics graphics,
            Font font,
            Function<ResearchTreeGraph.Node, Component> nodeName,
            ToIntFunction<ResearchTreeGraph.Node> nodeBorderColor,
            Function<ResearchTreeGraph.Node, ResearchTreePresentationContract.StatusSymbol> statusSymbol,
            Function<ResourceLocation, Component> groupName) {
        render(
                graphics,
                font,
                nodeName,
                nodeBorderColor,
                statusSymbol,
                groupName,
                tier -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.tier", tier + 1));
    }

    public void render(
            GuiGraphics graphics,
            Font font,
            Function<ResearchTreeGraph.Node, Component> nodeName,
            ToIntFunction<ResearchTreeGraph.Node> nodeBorderColor,
            Function<ResearchTreeGraph.Node, ResearchTreePresentationContract.StatusSymbol> statusSymbol,
            Function<ResourceLocation, Component> groupName,
            IntFunction<Component> tierName) {
        if (graphics == null || font == null || nodeName == null
                || nodeBorderColor == null || statusSymbol == null || groupName == null
                || tierName == null) {
            throw new IllegalArgumentException("Research Tree canvas render inputs cannot be null");
        }
        drawBackground(graphics);
        if (layout.nodes().isEmpty()) {
            return;
        }
        ResearchTreeViewport viewport = viewport();
        graphics.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
        try {
            graphics.pose().pushPose();
            float scale = (float) viewport.scale();
            graphics.pose().translate(
                    bounds.x() - viewport.panX() * scale,
                    bounds.y() - viewport.panY() * scale,
                    0.0D);
            graphics.pose().scale(scale, scale, 1.0F);
            try {
                double minimumX = viewport.canvasX(0.0D);
                double minimumY = viewport.canvasY(0.0D);
                double maximumX = viewport.canvasX(bounds.width());
                double maximumY = viewport.canvasY(bounds.height());
                ResearchTreePresentationContract.CardDetail detail =
                        ResearchTreePresentationContract.cardDetail(viewport.scale());
                ResearchTreePresentationContract.GraphLabels graphLabels =
                        ResearchTreePresentationContract.graphLabels(detail);
                boolean fullscreen =
                        viewMode == ResearchTreeScreenLayout.ViewMode.FULLSCREEN;
                drawGroupRegions(graphics, minimumX, minimumY, maximumX, maximumY);
                drawCategoryLanes(graphics, minimumX, maximumX);
                if (!fullscreen
                        || detail != ResearchTreePresentationContract.CardDetail.OVERVIEW) {
                    drawTierGuides(graphics, minimumY, maximumY);
                }
                if (fullscreen
                        && graphLabels != ResearchTreePresentationContract.GraphLabels.NONE) {
                    drawGraphGroupLabels(
                            graphics, font, groupName,
                            minimumX, minimumY, maximumX, maximumY);
                    drawGraphCategoryLabels(
                            graphics, font, minimumX, minimumY, maximumX, maximumY);
                    if (graphLabels == ResearchTreePresentationContract.GraphLabels.FULL) {
                        drawGraphTierLabels(graphics, font, tierName, minimumY, maximumY);
                    }
                }
                List<ResearchTreeEdgeIndex.PositionedEdge> visibleEdges =
                        edgeIndex.visible(minimumX, minimumY, maximumX, maximumY);
                for (int layer = 0; layer < 3; layer++) {
                    for (ResearchTreeEdgeIndex.PositionedEdge edge : visibleEdges) {
                        ResearchTreePresentationContract.RelationshipRole role = edgeRole(edge);
                        if (edgeLayer(role) == layer
                                && (!fullscreen
                                || ResearchTreePresentationContract.edgeVisible(detail, role))) {
                            drawEdge(graphics, edge, role, nodeBorderColor);
                        }
                    }
                }
                for (ResearchTreeLayout.HiddenAnchor anchor : visibleHiddenAnchors(
                        minimumX, minimumY, maximumX, maximumY)) {
                    drawHiddenAnchor(graphics, font, anchor);
                }
                drawPortals(graphics, minimumX, minimumY, maximumX, maximumY);
                drawTechTreePortals(graphics, minimumX, minimumY, maximumX, maximumY);
                for (ResearchTreeLayout.PositionedNode position
                        : nodeIndex.visible(minimumX, minimumY, maximumX, maximumY)) {
                    graph.node(position.blueprintId()).ifPresent(node ->
                            drawNode(
                                    graphics, font, node, position, detail,
                                    nodeName, nodeBorderColor, statusSymbol));
                }
            } finally {
                graphics.pose().popPose();
            }
            if (viewMode == ResearchTreeScreenLayout.ViewMode.COMPACT) {
                drawStickyGroupHeaders(graphics, font, groupName);
                drawStickyCategoryHeaders(graphics, font);
                drawStickyTierLabels(graphics, font, tierName);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button,
            Consumer<ResourceLocation> nodeSelection) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (dragging) {
            return false;
        }
        Optional<ResearchTreeGraph.Node> clicked = nodeAt(mouseX, mouseY);
        if (button == 0 && clicked.isPresent()) {
            if (nodeSelection != null) {
                nodeSelection.accept(clicked.orElseThrow().blueprintId());
            }
            return true;
        }
        if (button == 0 || button == 1) {
            dragging = true;
            dragButton = button;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(int button, double dragX, double dragY) {
        if (!dragging || button != dragButton) {
            return false;
        }
        viewport().panByScreenDelta(dragX, dragY);
        return true;
    }

    public boolean mouseReleased(int button) {
        if (!dragging || button != dragButton) {
            return false;
        }
        cancelInteraction();
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        viewport().zoomAt(delta, mouseX - bounds.x(), mouseY - bounds.y());
        return true;
    }

    public Optional<ResearchTreeGraph.Node> nodeAt(double mouseX, double mouseY) {
        return graphElementAt(mouseX, mouseY)
                .map(GraphElementHit::node)
                .filter(java.util.Objects::nonNull);
    }

    public Optional<GraphElementHit> graphElementAt(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY) || layout.nodes().isEmpty()
                || isCoveredByCompactChrome(mouseX, mouseY)) {
            return Optional.empty();
        }
        ResearchTreeViewport viewport = viewport();
        double canvasX = viewport.canvasX(mouseX - bounds.x());
        double canvasY = viewport.canvasY(mouseY - bounds.y());
        double nodePadding = canvasHitPadding(
                ResearchTreeLayout.NODE_WIDTH, MIN_NODE_HIT_SIZE, viewport.scale());
        Optional<ResearchTreeLayout.PositionedNode> positionedNode =
                nodeIndex.at(canvasX, canvasY, nodePadding);
        double nodeDistance = positionedNode.map(position -> {
            double deltaX = canvasX - position.centerX();
            double deltaY = canvasY - (position.y() + ResearchTreeLayout.NODE_HEIGHT / 2.0D);
            return deltaX * deltaX + deltaY * deltaY;
        }).orElse(Double.POSITIVE_INFINITY);

        double portalPadding = canvasHitPadding(
                PORTAL_SIZE, MIN_PORTAL_HIT_SIZE, viewport.scale());
        PortalPlacement portalMatch = null;
        double portalDistance = Double.POSITIVE_INFINITY;
        for (PortalPlacement portal : portals) {
            if (canvasX < portal.x() - portalPadding
                    || canvasX >= portal.x() + PORTAL_SIZE + portalPadding
                    || canvasY < portal.y() - portalPadding
                    || canvasY >= portal.y() + PORTAL_SIZE + portalPadding) {
                continue;
            }
            double deltaX = canvasX - (portal.x() + PORTAL_SIZE / 2.0D);
            double deltaY = canvasY - (portal.y() + PORTAL_SIZE / 2.0D);
            double distance = deltaX * deltaX + deltaY * deltaY;
            if (distance < portalDistance) {
                portalMatch = portal;
                portalDistance = distance;
            }
        }

        if (positionedNode.isEmpty() && portalMatch == null) {
            return Optional.empty();
        }
        if (positionedNode.isPresent() && nodeDistance <= portalDistance) {
            ResearchTreeGraph.Node node = graph.node(
                    positionedNode.orElseThrow().blueprintId()).orElseThrow();
            return Optional.of(new GraphElementHit(node, null));
        }
        return Optional.of(new GraphElementHit(null, portalMatch.target()));
    }

    public Optional<ResearchTreeProjection.CrossGroupLink> portalAt(
            double mouseX,
            double mouseY) {
        return portalTargetAt(mouseX, mouseY).map(PortalTarget::primaryLink);
    }

    public Optional<PortalTarget> portalTargetAt(double mouseX, double mouseY) {
        return graphElementAt(mouseX, mouseY)
                .map(GraphElementHit::portal)
                .filter(java.util.Objects::nonNull);
    }

    /** Returns a typed cross-domain target without weakening it to a branch group link. */
    public Optional<ResearchTechTreeLayout.PortalTarget> techTreePortalTargetAt(
            double mouseX,
            double mouseY) {
        if (techTreeLayout == null || !contains(mouseX, mouseY)
                || isCoveredByCompactChrome(mouseX, mouseY)) {
            return Optional.empty();
        }
        ResearchTreeViewport viewport = viewport();
        double canvasX = viewport.canvasX(mouseX - bounds.x());
        double canvasY = viewport.canvasY(mouseY - bounds.y());
        double padding = canvasHitPadding(
                PORTAL_SIZE, MIN_PORTAL_HIT_SIZE, viewport.scale());
        ResearchTechTreeLayout.BoundaryPortal match = null;
        double matchDistance = Double.POSITIVE_INFINITY;
        for (ResearchTechTreeLayout.BoundaryPortal portal : techTreePortals) {
            if (canvasX < portal.x() - padding
                    || canvasX >= portal.x() + PORTAL_SIZE + padding
                    || canvasY < portal.y() - padding
                    || canvasY >= portal.y() + PORTAL_SIZE + padding) {
                continue;
            }
            double deltaX = canvasX - (portal.x() + PORTAL_SIZE / 2.0D);
            double deltaY = canvasY - (portal.y() + PORTAL_SIZE / 2.0D);
            double distance = deltaX * deltaX + deltaY * deltaY;
            if (distance < matchDistance) {
                match = portal;
                matchDistance = distance;
            }
        }
        if (match != null) {
            double nodePadding = canvasHitPadding(
                    ResearchTreeLayout.NODE_WIDTH,
                    MIN_NODE_HIT_SIZE,
                    viewport.scale());
            Optional<ResearchTreeLayout.PositionedNode> node =
                    nodeIndex.at(canvasX, canvasY, nodePadding);
            if (node.isPresent()) {
                ResearchTreeLayout.PositionedNode position = node.orElseThrow();
                double deltaX = canvasX - position.centerX();
                double deltaY = canvasY - position.centerY();
                if (deltaX * deltaX + deltaY * deltaY <= matchDistance) {
                    return Optional.empty();
                }
            }
        }
        return Optional.ofNullable(match).map(ResearchTechTreeLayout.BoundaryPortal::target);
    }

    static double canvasHitPadding(int visualSize, int minimumScreenSize, double scale) {
        if (visualSize <= 0 || minimumScreenSize <= 0
                || !Double.isFinite(scale) || scale <= 0.0D) {
            throw new IllegalArgumentException("invalid Research Tree hit-target geometry");
        }
        return Math.max(0.0D, (minimumScreenSize / scale - visualSize) / 2.0D);
    }

    /** Full label for a visible sticky category header, used by the screen tooltip. */
    public Optional<Component> categoryHeaderAt(double mouseX, double mouseY) {
        if (viewMode != ResearchTreeScreenLayout.ViewMode.COMPACT
                || !contains(mouseX, mouseY)
                || mouseY >= bounds.y() + STICKY_HEADER_HEIGHT) {
            return Optional.empty();
        }
        ResearchTreeViewport viewport = viewport();
        for (ResearchTreeLayout.CategoryLane lane : layout.categoryLanes()) {
            int left = bounds.x() + viewport.viewportX(lane.x());
            int right = bounds.x() + viewport.viewportX(lane.right());
            if (mouseX >= Math.max(bounds.x(), left)
                    && mouseX < Math.min(bounds.right(), right)) {
                return Optional.of(categoryLabel(lane));
            }
        }
        return Optional.empty();
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= bounds.x() && mouseX < bounds.right()
                && mouseY >= bounds.y() && mouseY < bounds.bottom();
    }

    public void setSafeInsets(ResearchTreeViewport.Insets safeInsets) {
        viewport().setSafeInsets(safeInsets);
    }

    public void panByScreenDelta(double deltaX, double deltaY) {
        viewport().panByScreenDelta(deltaX, deltaY);
    }

    public boolean tickCamera() {
        return viewport().tick();
    }

    public boolean tickCamera(double deltaSeconds) {
        return viewport().tick(deltaSeconds);
    }

    public void cancelInteraction() {
        dragging = false;
        dragButton = -1;
    }

    private boolean isCoveredByCompactChrome(double mouseX, double mouseY) {
        return viewMode == ResearchTreeScreenLayout.ViewMode.COMPACT
                && (mouseY < bounds.y() + STICKY_HEADER_HEIGHT
                        || mouseX < bounds.x() + STICKY_GUTTER_WIDTH);
    }

    public void zoomAtCenter(double direction) {
        viewport().zoomAt(direction, bounds.width() / 2.0D, bounds.height() / 2.0D);
    }

    public void fit() {
        fitViewport(viewport());
    }

    /** Selects the camera policy for the curated All Weapons projection. */
    public void setUnifiedOverview(boolean unifiedOverview) {
        this.unifiedOverview = unifiedOverview;
    }

    /** Selects edge geometry independently from camera and visual-region metadata. */
    public void setEdgeRoutingProfile(ResearchTreeEdgeIndex.RoutingProfile edgeRoutingProfile) {
        if (edgeRoutingProfile == null) {
            throw new IllegalArgumentException("Research Tree routing profile cannot be null");
        }
        this.edgeRoutingProfile = edgeRoutingProfile;
    }

    ResearchTreeEdgeIndex.RoutingProfile edgeRoutingProfile() {
        return edgeRoutingProfile;
    }

    public boolean unifiedOverview() {
        return unifiedOverview;
    }

    private void fitViewport(ResearchTreeViewport viewport) {
        if (unifiedOverview) {
            viewport.fitReadable(
                    ResearchTreePresentationContract.MIN_READABLE_OVERVIEW_FIT_SCALE);
        } else {
            viewport.fit();
        }
    }

    /** Fits a disclosed set of nodes without changing the active graph projection. */
    public boolean focusNodes(Collection<ResourceLocation> blueprintIds) {
        if (blueprintIds == null || blueprintIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Research Tree focus IDs cannot be null");
        }
        int minimumX = Integer.MAX_VALUE;
        int minimumY = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumY = Integer.MIN_VALUE;
        for (ResourceLocation blueprintId : blueprintIds) {
            Optional<ResearchTreeLayout.PositionedNode> position = layout.position(blueprintId);
            if (position.isEmpty()) {
                continue;
            }
            ResearchTreeLayout.PositionedNode node = position.orElseThrow();
            minimumX = Math.min(minimumX, node.x());
            minimumY = Math.min(minimumY, node.y());
            maximumX = Math.max(maximumX, node.x() + ResearchTreeLayout.NODE_WIDTH);
            maximumY = Math.max(maximumY, node.y() + ResearchTreeLayout.NODE_HEIGHT);
        }
        if (minimumX == Integer.MAX_VALUE) {
            return false;
        }
        int padding = FOCUS_PADDING;
        int x = Math.max(0, minimumX - padding);
        int y = Math.max(0, minimumY - padding);
        int right = Math.min(layout.width(), maximumX + padding);
        int bottom = Math.min(layout.height(), maximumY + padding);
        int width = Math.max(1, right - x);
        int height = Math.max(1, bottom - y);
        if (unifiedOverview) {
            viewport().fitReadable(
                    x,
                    y,
                    width,
                    height,
                    ResearchTreePresentationContract.MIN_READABLE_OVERVIEW_FIT_SCALE);
        } else {
            viewport().fit(x, y, width, height);
        }
        return true;
    }

    /** Fits one complete published group region without filtering the graph. */
    public boolean focusGroup(ResourceLocation groupId) {
        return focusGroup(groupId, List.of());
    }

    /** Frames a configured group even when a unified layout has no group rectangle. */
    public boolean focusGroup(
            ResourceLocation groupId,
            Collection<ResourceLocation> memberIds) {
        if (groupId == null) {
            throw new IllegalArgumentException("Research Tree group focus cannot be null");
        }
        if (memberIds == null || memberIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Research Tree group members cannot be null");
        }
        Optional<ResearchTreeLayout.GroupRegion> region = layout.groupRegions().stream()
                .filter(candidate -> candidate.groupId().equals(groupId))
                .findFirst();
        if (region.isPresent()) {
            ResearchTreeLayout.GroupRegion target = region.orElseThrow();
            viewport().fit(target.x(), target.y(), target.width(), target.height());
            return true;
        }
        return !memberIds.isEmpty() && focusNodes(memberIds);
    }

    List<PortalPlacement> portalPlacements() {
        return portals;
    }

    static int portalBankWidth(int portalCount) {
        if (portalCount < 0 || portalCount > ResearchTreeGraph.MAX_EDGES) {
            throw new IllegalArgumentException("invalid Research Tree portal count");
        }
        return portalCount == 0
                ? 0
                : Math.addExact(
                        Math.multiplyExact(portalCount, PORTAL_SIZE),
                        Math.multiplyExact(portalCount - 1, PORTAL_GAP));
    }

    static int maximumPortalBankWidth(
            List<ResearchTreeProjection.CrossGroupLink> links) {
        if (links == null || links.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("invalid Research Tree portal links");
        }
        Map<PortalGroup, Set<ResourceLocation>> destinationGroups =
                new java.util.LinkedHashMap<>();
        for (ResearchTreeProjection.CrossGroupLink link : links) {
            destinationGroups.computeIfAbsent(
                    new PortalGroup(link.localNodeId(), link.direction()),
                    ignored -> new java.util.LinkedHashSet<>()).add(link.remoteGroupId());
        }
        return destinationGroups.values().stream()
                .mapToInt(groups -> portalBankWidth(Math.min(
                        groups.size(), MAX_VISIBLE_PORTALS_PER_BANK)))
                .max()
                .orElse(0);
    }

    public void focusNode(ResourceLocation blueprintId) {
        setFocusedNode(blueprintId);
        layout.position(blueprintId).ifPresent(position -> viewport().focus(
                position.x(),
                position.y(),
                ResearchTreeLayout.NODE_WIDTH,
                ResearchTreeLayout.NODE_HEIGHT));
    }

    /** Keeps a locally focused node visible without unnecessarily recentering the tree. */
    public boolean revealNode(ResourceLocation blueprintId, int screenPadding) {
        if (screenPadding < 0) {
            throw new IllegalArgumentException("Research Tree reveal padding cannot be negative");
        }
        Optional<ResearchTreeLayout.PositionedNode> position = layout.position(blueprintId);
        if (position.isEmpty()) {
            return false;
        }
        ResearchTreeLayout.PositionedNode node = position.orElseThrow();
        return viewport().reveal(
                node.x(),
                node.y(),
                ResearchTreeLayout.NODE_WIDTH,
                ResearchTreeLayout.NODE_HEIGHT,
                screenPadding);
    }

    /** Changes local focus without moving the active viewport. */
    public void setFocusedNode(ResourceLocation blueprintId) {
        if (blueprintId != null && graph.node(blueprintId).isEmpty()) {
            throw new IllegalArgumentException("cannot focus an unknown Research Tree node");
        }
        state.focus(blueprintId);
        refreshFocusPath();
    }

    public void updateHover(double mouseX, double mouseY) {
        ResourceLocation next = nodeAt(mouseX, mouseY)
                .map(ResearchTreeGraph.Node::blueprintId)
                .orElse(null);
        if (java.util.Objects.equals(hoveredId, next)) {
            return;
        }
        hoveredId = next;
        hoverPath = relations.directFocus(next);
    }

    public void clearHover() {
        hoveredId = null;
        hoverPath = ResearchTreeRelations.FocusPath.EMPTY;
    }

    public Optional<ResearchTreeGraph.Node> focusedNode(ResourceLocation fallback) {
        Optional<ResearchTreeGraph.Node> focused = state.focusedId().flatMap(graph::node);
        return focused.isPresent() ? focused : graph.node(fallback);
    }

    public void setSearchMatches(Set<ResourceLocation> matches) {
        state.setSearchMatches(matches);
        if (activeSearchMatch != null && !state.searchMatches().contains(activeSearchMatch)) {
            activeSearchMatch = null;
        }
    }

    public void setActiveSearchMatch(ResourceLocation blueprintId) {
        if (blueprintId != null && (graph.node(blueprintId).isEmpty()
                || !state.searchMatches().contains(blueprintId))) {
            throw new IllegalArgumentException(
                    "cannot activate a non-matching Research Tree search result");
        }
        activeSearchMatch = blueprintId;
    }

    public ResearchTreeGraph graph() {
        return graph;
    }

    public ResearchTreeLayout layout() {
        return layout;
    }

    public Optional<ResourceLocation> focusedId() {
        return state.focusedId();
    }

    public Set<ResourceLocation> searchMatches() {
        return state.searchMatches();
    }

    /** Applies one full-publication plan to the currently projected canvas. */
    public void setTrackedPlan(ResearchTreePlanner.Plan plan) {
        if (plan == null) {
            trackedTargetId = null;
            trackedPathNodeIds = Set.of();
            trackedPathEdges = Set.of();
            return;
        }
        trackedTargetId = plan.targetId();
        trackedPathNodeIds = plan.pathNodeIds().stream()
                .filter(id -> graph.node(id).isPresent())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<ResearchTreeGraph.Edge> visibleEdges = Set.copyOf(graph.edges());
        trackedPathEdges = plan.pathEdges().stream()
                .filter(visibleEdges::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    boolean isTrackedPathNode(ResourceLocation blueprintId) {
        return trackedPathNodeIds.contains(blueprintId);
    }

    boolean isTrackedPathEdge(ResearchTreeGraph.Edge edge) {
        return trackedPathEdges.contains(edge);
    }

    Optional<ResourceLocation> trackedTargetId() {
        return Optional.ofNullable(trackedTargetId);
    }

    public Optional<ResourceLocation> activeSearchMatch() {
        return Optional.ofNullable(activeSearchMatch);
    }

    /** Server-authoritative Preview/Full selection, separate from local tree focus. */
    public void setAuthoritativeSelection(ResourceLocation blueprintId) {
        authoritativeSelectedId = graph.node(blueprintId)
                .filter(node -> node.visibility().allowsServerSelection())
                .map(ResearchTreeGraph.Node::blueprintId)
                .orElse(null);
    }

    public Optional<ResourceLocation> authoritativeSelectedId() {
        return Optional.ofNullable(authoritativeSelectedId);
    }

    public ItemStack icon(ResourceLocation blueprintId) {
        return icons.getOrDefault(blueprintId, ItemStack.EMPTY);
    }

    public ResearchTreeViewport viewport() {
        return state.viewport(viewMode);
    }

    public ResearchTreeScreenLayout.Rect bounds() {
        return bounds;
    }

    public ResearchTreePresentationContract.RelationshipRole relationshipRole(
            ResourceLocation blueprintId) {
        return focusPath.role(blueprintId);
    }

    public ResearchTreePresentationContract.RelationshipRole hoverRelationshipRole(
            ResourceLocation blueprintId) {
        return hoverPath.role(blueprintId);
    }

    public List<ResourceLocation> directRequirements(ResourceLocation blueprintId) {
        return relations.directRequirements(blueprintId);
    }

    public List<ResourceLocation> directUnlocks(ResourceLocation blueprintId) {
        return relations.directUnlocks(blueprintId);
    }

    /** Counts internal and cross-group requirements without rendering remote nodes. */
    public int totalRequirementCount(ResourceLocation blueprintId) {
        return Math.addExact(
                directRequirements(blueprintId).size(),
                boundaryRelationshipCount(
                        blueprintId, ResearchTreeProjection.Direction.REQUIREMENT));
    }

    /** Counts internal and cross-group unlocks without rendering remote nodes. */
    public int totalUnlockCount(ResourceLocation blueprintId) {
        return Math.addExact(
                directUnlocks(blueprintId).size(),
                boundaryRelationshipCount(
                        blueprintId, ResearchTreeProjection.Direction.UNLOCK));
    }

    private int boundaryRelationshipCount(
            ResourceLocation blueprintId,
            ResearchTreeProjection.Direction direction) {
        if (blueprintId == null || graph.node(blueprintId).isEmpty()) {
            return 0;
        }
        return (direction == ResearchTreeProjection.Direction.REQUIREMENT
                ? boundaryRequirementCounts : boundaryUnlockCounts)
                .getOrDefault(blueprintId, 0);
    }

    public List<String> categoryKeys() {
        return layout.categoryLanes().stream()
                .map(ResearchTreeLayout.CategoryLane::key)
                .toList();
    }

    public Optional<String> categoryFilter() {
        return Optional.ofNullable(categoryFilter);
    }

    public Component categoryName(String category) {
        ResearchTreeLayout.CategoryLane lane = layout.categoryLanes().stream()
                .filter(candidate -> candidate.key().equals(category))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown published Research Tree category"));
        return categoryLabel(lane);
    }

    /** Keeps all topology visible while fitting and emphasizing one published lane. */
    public void focusCategory(String category) {
        if (category == null) {
            categoryFilter = null;
            viewport().fit();
            return;
        }
        ResearchTreeLayout.CategoryLane lane = layout.categoryLanes().stream()
                .filter(candidate -> candidate.key().equals(category))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown published Research Tree category"));
        categoryFilter = lane.key();
        viewport().fit(lane.x(), 0, lane.width(), layout.height());
    }

    private void configureViewport() {
        viewport().configure(bounds.width(), bounds.height(), layout.width(), layout.height());
    }

    private void refreshFocusPath() {
        focusPath = relations.focus(state.focusedId().orElse(null));
    }

    private void drawBackground(GuiGraphics graphics) {
        int background = viewMode == ResearchTreeScreenLayout.ViewMode.FULLSCREEN
                ? 0xA00B0F14
                : style.background();
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), background);
        if (displayPolicy.showBackgroundGrid()) {
            ResearchTreeViewport viewport = viewport();
            int spacing = 16;
            int offsetX = Math.floorMod(
                    (int) Math.round(-viewport.panX() * viewport.scale()), spacing);
            int offsetY = Math.floorMod(
                    (int) Math.round(-viewport.panY() * viewport.scale()), spacing);
            for (int x = bounds.x() + offsetX; x < bounds.right(); x += spacing) {
                graphics.fill(x, bounds.y(), x + 1, bounds.bottom(), style.grid());
            }
            for (int y = bounds.y() + offsetY; y < bounds.bottom(); y += spacing) {
                graphics.fill(bounds.x(), y, bounds.right(), y + 1, style.grid());
            }
        }
        if (viewMode == ResearchTreeScreenLayout.ViewMode.COMPACT) {
            graphics.renderOutline(
                    bounds.x(), bounds.y(), bounds.width(), bounds.height(), style.border());
        }
    }

    private void drawCategoryLanes(
            GuiGraphics graphics,
            double minimumX,
            double maximumX) {
        for (int index = 0; index < layout.categoryLanes().size(); index++) {
            ResearchTreeLayout.CategoryLane lane = layout.categoryLanes().get(index);
            if (lane.right() < minimumX || lane.x() > maximumX) {
                continue;
            }
            int background = index % 2 == 0 ? style.laneEven() : style.laneOdd();
            graphics.fill(lane.x(), 0, lane.right(), layout.height(), background);
            graphics.renderOutline(
                    lane.x(), 0, lane.width(), layout.height(), style.laneBorder());
        }
    }

    private void drawGroupRegions(
            GuiGraphics graphics,
            double minimumX,
            double minimumY,
            double maximumX,
            double maximumY) {
        for (int index = 0; index < layout.groupRegions().size(); index++) {
            ResearchTreeLayout.GroupRegion region = layout.groupRegions().get(index);
            if (!intersects(
                    region.x(), region.y(), region.right(), region.bottom(),
                    minimumX, minimumY, maximumX, maximumY)) {
                continue;
            }
            int background = index % 2 == 0 ? style.laneEven() : style.laneOdd();
            graphics.fill(
                    region.x(), region.y(), region.right(), region.bottom(), background);
            graphics.renderOutline(
                    region.x(), region.y(), region.width(), region.height(), style.laneBorder());
        }
    }

    private void drawGraphGroupLabels(
            GuiGraphics graphics,
            Font font,
            Function<ResourceLocation, Component> groupName,
            double minimumX,
            double minimumY,
            double maximumX,
            double maximumY) {
        for (ResearchTreeLayout.GroupRegion region : layout.groupRegions()) {
            if (!intersects(
                    region.x(), region.y(), region.right(), region.bottom(),
                    minimumX, minimumY, maximumX, maximumY)) {
                continue;
            }
            Component name = groupName.apply(region.groupId());
            if (name == null) {
                throw new IllegalArgumentException("Research Tree group name cannot be null");
            }
            int labelY = region.y() + 4;
            if (labelY + font.lineHeight < minimumY || labelY > maximumY) {
                continue;
            }
            String text = ellipsize(font, name.getString(), region.width() - 8);
            if (!text.isEmpty()) {
                graphics.drawCenteredString(
                        font, text, region.x() + region.width() / 2, labelY, style.muted());
            }
        }
    }

    private void drawGraphCategoryLabels(
            GuiGraphics graphics,
            Font font,
            double minimumX,
            double minimumY,
            double maximumX,
            double maximumY) {
        int labelY = 3;
        if (labelY + font.lineHeight < minimumY || labelY > maximumY) {
            return;
        }
        for (ResearchTreeLayout.CategoryLane lane : layout.categoryLanes()) {
            if (lane.right() < minimumX || lane.x() > maximumX) {
                continue;
            }
            String text = ellipsize(font, categoryLabel(lane).getString(), lane.width() - 8);
            if (!text.isEmpty()) {
                graphics.drawCenteredString(
                        font, text, lane.x() + lane.width() / 2, labelY,
                        lane.key().equals(categoryFilter) ? style.accent() : style.muted());
            }
        }
    }

    private void drawGraphTierLabels(
            GuiGraphics graphics,
            Font font,
            IntFunction<Component> tierName,
            double minimumY,
            double maximumY) {
        if (layout.categoryLanes().isEmpty() && techTreeLayout == null) {
            return;
        }
        int x = TIER_GUIDE_PADDING + TIER_LABEL_GUTTER_WIDTH / 2;
        for (TierLabelPosition tier : tierLabelPositions()) {
            ItemStack icon = tierIcon(tier.tier());
            int y = icon.isEmpty()
                    ? tier.centerY() - font.lineHeight / 2
                    : tier.centerY() + 3;
            int top = icon.isEmpty() ? y : tier.centerY() - 15;
            if (y + font.lineHeight < minimumY || top > maximumY) {
                continue;
            }
            Component label = tierName.apply(tier.tier());
            if (label == null) {
                throw new IllegalArgumentException("Research Tree tier name cannot be null");
            }
            Component shortLabel = styledSubstring(
                    font, label, TIER_LABEL_GUTTER_WIDTH - 4);
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, x - 8, tier.centerY() - 15);
            }
            graphics.drawCenteredString(font, shortLabel, x, y, style.muted());
        }
    }

    private void drawStickyGroupHeaders(
            GuiGraphics graphics,
            Font font,
            Function<ResourceLocation, Component> groupName) {
        ResearchTreeViewport viewport = viewport();
        for (ResearchTreeLayout.GroupRegion region : layout.groupRegions()) {
            int left = Math.max(bounds.x(), bounds.x() + viewport.viewportX(region.x()));
            int right = Math.min(bounds.right(), bounds.x() + viewport.viewportX(region.right()));
            if (right <= left) {
                continue;
            }
            graphics.fill(
                    left, bounds.y(), right, bounds.y() + STICKY_HEADER_HEIGHT, style.laneHeader());
            graphics.renderOutline(
                    left,
                    bounds.y(),
                    right - left,
                    STICKY_HEADER_HEIGHT,
                    style.laneBorder());
            Component name = groupName.apply(region.groupId());
            if (name == null) {
                throw new IllegalArgumentException("Research Tree group name cannot be null");
            }
            String text = ellipsize(font, name.getString(), right - left - 4);
            if (!text.isEmpty()) {
                graphics.drawCenteredString(
                        font, text, left + (right - left) / 2, bounds.y() + 3, style.muted());
            }
        }
    }

    private void drawStickyCategoryHeaders(GuiGraphics graphics, Font font) {
        ResearchTreeViewport viewport = viewport();
        for (ResearchTreeLayout.CategoryLane lane : layout.categoryLanes()) {
            int left = Math.max(bounds.x(), bounds.x() + viewport.viewportX(lane.x()));
            int right = Math.min(bounds.right(), bounds.x() + viewport.viewportX(lane.right()));
            if (right <= left) {
                continue;
            }
            graphics.fill(left, bounds.y(), right, bounds.y() + STICKY_HEADER_HEIGHT, style.laneHeader());
            graphics.renderOutline(
                    left,
                    bounds.y(),
                    right - left,
                    STICKY_HEADER_HEIGHT,
                    lane.key().equals(categoryFilter) ? style.accent() : style.laneBorder());
            String text = ellipsize(font, categoryLabel(lane).getString(), right - left - 4);
            if (!text.isEmpty()) {
                graphics.drawCenteredString(
                        font,
                        text,
                        left + (right - left) / 2,
                        bounds.y() + 3,
                        lane.key().equals(categoryFilter) ? style.accent() : style.muted());
            }
        }
    }

    private void drawStickyTierLabels(
            GuiGraphics graphics,
            Font font,
            IntFunction<Component> tierName) {
        int gutterRight = Math.min(bounds.right(), bounds.x() + STICKY_GUTTER_WIDTH);
        graphics.fill(
                bounds.x(), bounds.y() + STICKY_HEADER_HEIGHT,
                gutterRight, bounds.bottom(), style.laneHeader());
        if (gutterRight < bounds.right()) {
            graphics.fill(
                    gutterRight - 1, bounds.y() + STICKY_HEADER_HEIGHT,
                    gutterRight, bounds.bottom(), style.laneBorder());
        }
        ResearchTreeViewport viewport = viewport();
        for (TierLabelPosition tier : tierLabelPositions()) {
            ItemStack icon = tierIcon(tier.tier());
            int centerY = bounds.y() + viewport.viewportY(tier.centerY());
            int y = icon.isEmpty() ? centerY - font.lineHeight / 2 : centerY + 3;
            int top = icon.isEmpty() ? y : centerY - 15;
            if (top < bounds.y() + STICKY_HEADER_HEIGHT
                    || y + font.lineHeight > bounds.bottom()) {
                continue;
            }
            Component label = tierName.apply(tier.tier());
            if (label == null) {
                throw new IllegalArgumentException("Research Tree tier name cannot be null");
            }
            Component shortLabel = styledSubstring(
                    font, label, STICKY_GUTTER_WIDTH - 4);
            if (!icon.isEmpty()) {
                graphics.renderItem(
                        icon,
                        bounds.x() + STICKY_GUTTER_WIDTH / 2 - 8,
                        centerY - 15);
            }
            graphics.drawCenteredString(
                    font, shortLabel, bounds.x() + STICKY_GUTTER_WIDTH / 2, y, style.muted());
        }
    }

    private ItemStack tierIcon(int labelIndex) {
        if (techTreeLayout == null) {
            return ItemStack.EMPTY;
        }
        return techTreeLayout.bands().stream()
                .filter(band -> band.index() == labelIndex)
                .findFirst()
                .flatMap(ResearchTechTreeLayout.ProgressionBand::iconNodeId)
                .map(this::icon)
                .orElse(ItemStack.EMPTY);
    }

    private static Component styledSubstring(Font font, Component label, int maximumWidth) {
        return Component.literal(font.plainSubstrByWidth(label.getString(), maximumWidth))
                .setStyle(label.getStyle());
    }

    private List<TierLabelPosition> tierLabelPositions() {
        if (techTreeLayout != null) {
            if (!techTreeLayout.bands().isEmpty()) {
                return techTreeLayout.bands().stream()
                        .map(band -> new TierLabelPosition(
                                band.index(), band.y() + band.height() / 2))
                        .toList();
            }
            return techTreeLayout.tiers().stream()
                    .map(tier -> new TierLabelPosition(
                            tier.tier().ordinal(), tier.y() + tier.height() / 2))
                    .toList();
        }
        return layout.tierBounds().stream()
                .map(tier -> new TierLabelPosition(tier.tier(), tier.centerY()))
                .toList();
    }

    private static String ellipsize(Font font, String value, int maximumWidth) {
        if (maximumWidth <= 0 || value.isEmpty()) {
            return "";
        }
        if (font.width(value) <= maximumWidth) {
            return value;
        }
        String suffix = "...";
        int suffixWidth = font.width(suffix);
        if (maximumWidth <= suffixWidth) {
            return font.plainSubstrByWidth(value, maximumWidth);
        }
        return font.plainSubstrByWidth(value, maximumWidth - suffixWidth) + suffix;
    }

    private static Component categoryLabel(ResearchTreeLayout.CategoryLane lane) {
        return Component.translatableWithFallback(
                "gui.taczweaponblueprints.journal.category." + lane.key(),
                readableCategory(lane.key()));
    }

    private static String readableCategory(String key) {
        String[] words = key.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.isEmpty() ? "Other" : result.toString();
    }

    private void drawTierGuides(
            GuiGraphics graphics,
            double minimumY,
            double maximumY) {
        if (techTreeLayout != null) {
            for (ResearchTechTreeLayout.ProgressionBand band : techTreeLayout.bands()) {
                int separatorY = band.y();
                if (separatorY < minimumY || separatorY > maximumY) {
                    continue;
                }
                int color = band.color()
                        .map(value -> 0xAA000000 | value)
                        .orElse(style.laneBorder());
                graphics.fill(
                        TIER_GUIDE_PADDING,
                        separatorY,
                        layout.width() - TIER_GUIDE_PADDING,
                        separatorY + 1,
                        color);
            }
            for (ResearchTechTreeLayout.TierBand tier : techTreeLayout.tiers()) {
                int separatorY = tier.y();
                if (separatorY < minimumY || separatorY > maximumY) {
                    continue;
                }
                graphics.fill(
                        TIER_GUIDE_PADDING,
                        separatorY,
                        layout.width() - TIER_GUIDE_PADDING,
                        separatorY + 1,
                        style.laneBorder());
            }
            return;
        }
        for (ResearchTreeLayout.TierBounds tier : layout.tierBounds()) {
            int separatorY = tier.tier() == 0
                    ? tier.maximumBottom()
                    : tier.maximumBottom() + TIER_GUIDE_GAP / 2;
            if (separatorY < minimumY || separatorY > maximumY) {
                continue;
            }
            graphics.fill(
                    TIER_GUIDE_PADDING,
                    separatorY,
                    layout.width() - TIER_GUIDE_PADDING,
                    separatorY + 1,
                    style.laneBorder());
        }
    }

    private void drawPortals(
            GuiGraphics graphics,
            double minimumX,
            double minimumY,
            double maximumX,
            double maximumY) {
        for (PortalPlacement portal : portals) {
            ResearchTreeLayout.PositionedNode local = layout.position(portal.link().localNodeId())
                    .orElseThrow();
            int portalMinimumY = Math.min(portal.y(), local.y());
            int portalMaximumY = Math.max(
                    portal.y() + PORTAL_SIZE,
                    local.y() + ResearchTreeLayout.NODE_HEIGHT);
            if (!intersects(
                    portal.x(), portalMinimumY,
                    portal.x() + PORTAL_SIZE, portalMaximumY,
                    minimumX, minimumY, maximumX, maximumY)) {
                continue;
            }
            int color = portal.link().direction() == ResearchTreeProjection.Direction.REQUIREMENT
                    ? style.directRequirement()
                    : style.directUnlock();
            int centerX = portal.x() + PORTAL_SIZE / 2;
            if (portal.link().direction() == ResearchTreeProjection.Direction.UNLOCK) {
                fillVerticalLine(graphics, centerX, local.y(), portal.y() + PORTAL_SIZE, color);
            } else {
                fillVerticalLine(
                        graphics,
                        centerX,
                        local.y() + ResearchTreeLayout.NODE_HEIGHT,
                        portal.y(),
                        color);
            }
            graphics.fill(
                    portal.x(), portal.y(),
                    portal.x() + PORTAL_SIZE, portal.y() + PORTAL_SIZE,
                    style.background());
            graphics.renderOutline(portal.x(), portal.y(), PORTAL_SIZE, PORTAL_SIZE, color);
            int tipY = portal.link().direction() == ResearchTreeProjection.Direction.UNLOCK
                    ? portal.y() + 2 : portal.y() + PORTAL_SIZE - 3;
            int baseY = portal.link().direction() == ResearchTreeProjection.Direction.UNLOCK
                    ? portal.y() + PORTAL_SIZE - 3 : portal.y() + 2;
            if (portal.target().connectionCount() > 1) {
                int centerY = portal.y() + PORTAL_SIZE / 2;
                graphics.fill(centerX, portal.y() + 2, centerX + 1,
                        portal.y() + PORTAL_SIZE - 2, color);
                graphics.fill(portal.x() + 2, centerY,
                        portal.x() + PORTAL_SIZE - 2, centerY + 1, color);
            } else {
                drawArrowhead(graphics, centerX, baseY, tipY, color);
            }
        }
    }

    private void drawTechTreePortals(
            GuiGraphics graphics,
            double minimumX,
            double minimumY,
            double maximumX,
            double maximumY) {
        for (ResearchTechTreeLayout.BoundaryPortal portal : techTreePortals) {
            ResearchTechTreeLayout.PortalTarget target = portal.target();
            ResearchTreeLayout.PositionedNode local = layout.position(target.localNodeId())
                    .orElseThrow();
            int portalMinimumY = Math.min(portal.y(), local.y());
            int portalMaximumY = Math.max(
                    portal.y() + PORTAL_SIZE,
                    local.y() + ResearchTreeLayout.NODE_HEIGHT);
            if (!intersects(
                    portal.x(), portalMinimumY,
                    portal.x() + PORTAL_SIZE, portalMaximumY,
                    minimumX, minimumY, maximumX, maximumY)) {
                continue;
            }
            boolean unlock = target.direction()
                    == ResearchTechTreeProjection.Direction.UNLOCK;
            int color = unlock ? style.directUnlock() : style.directRequirement();
            int centerX = portal.x() + PORTAL_SIZE / 2;
            if (unlock) {
                fillVerticalLine(graphics, centerX, local.y(), portal.y() + PORTAL_SIZE, color);
            } else {
                fillVerticalLine(
                        graphics,
                        centerX,
                        local.y() + ResearchTreeLayout.NODE_HEIGHT,
                        portal.y(),
                        color);
            }
            graphics.fill(
                    portal.x(), portal.y(),
                    portal.x() + PORTAL_SIZE, portal.y() + PORTAL_SIZE,
                    style.background());
            graphics.renderOutline(portal.x(), portal.y(), PORTAL_SIZE, PORTAL_SIZE, color);
            int tipY = unlock ? portal.y() + 2 : portal.y() + PORTAL_SIZE - 3;
            int baseY = unlock ? portal.y() + PORTAL_SIZE - 3 : portal.y() + 2;
            if (target.connectionCount() > 1) {
                int centerY = portal.y() + PORTAL_SIZE / 2;
                graphics.fill(
                        centerX, portal.y() + 2,
                        centerX + 1, portal.y() + PORTAL_SIZE - 2,
                        color);
                graphics.fill(
                        portal.x() + 2, centerY,
                        portal.x() + PORTAL_SIZE - 2, centerY + 1,
                        color);
            } else {
                drawArrowhead(graphics, centerX, baseY, tipY, color);
            }
        }
    }

    private static List<PortalPlacement> placePortals(
            ResearchTreeGraph graph,
            ResearchTreeLayout layout,
            List<ResearchTreeProjection.CrossGroupLink> links) {
        if (links.isEmpty()) {
            return List.of();
        }
        Map<PortalGroup, List<ResearchTreeProjection.CrossGroupLink>> grouped =
                new java.util.LinkedHashMap<>();
        for (ResearchTreeProjection.CrossGroupLink link : links) {
            if (graph.node(link.localNodeId()).isEmpty()
                    || graph.node(link.remoteNodeId()).isPresent()) {
                throw new IllegalArgumentException("invalid Research Tree canvas portal");
            }
            grouped.computeIfAbsent(
                    new PortalGroup(link.localNodeId(), link.direction()),
                    ignored -> new java.util.ArrayList<>()).add(link);
        }
        List<PortalPlacement> result = new java.util.ArrayList<>(Math.min(
                links.size(), grouped.size() * MAX_VISIBLE_PORTALS_PER_BANK));
        for (Map.Entry<PortalGroup, List<ResearchTreeProjection.CrossGroupLink>> entry
                : grouped.entrySet()) {
            ResearchTreeLayout.PositionedNode local = layout.position(entry.getKey().localNodeId())
                    .orElseThrow();
            List<PortalTarget> localTargets = portalTargets(entry.getValue());
            int portalStep = PORTAL_SIZE + PORTAL_GAP;
            int portalWidth = portalBankWidth(localTargets.size());
            ResearchTreeLayout.GroupRegion region = layout.groupRegions().stream()
                    .filter(candidate -> local.x() >= candidate.x()
                            && local.x() + ResearchTreeLayout.NODE_WIDTH <= candidate.right())
                    .findFirst()
                    .orElse(null);
            int minimumX = region == null
                    ? 0
                    : region.x() + ResearchTreeLayout.PORTAL_BANK_SIDE_PADDING;
            int maximumX = region == null
                    ? layout.width() - portalWidth
                    : region.right() - ResearchTreeLayout.PORTAL_BANK_SIDE_PADDING - portalWidth;
            int firstX = Math.max(
                    minimumX,
                    Math.min(maximumX, local.centerX() - portalWidth / 2));
            for (int index = 0; index < localTargets.size(); index++) {
                int x = firstX + index * portalStep;
                int y = entry.getKey().direction() == ResearchTreeProjection.Direction.UNLOCK
                        ? local.y() - PORTAL_SIZE - ResearchTreeLayout.PORTAL_NODE_GAP
                        : local.y() + ResearchTreeLayout.NODE_HEIGHT
                                + ResearchTreeLayout.PORTAL_NODE_GAP;
                if (x < 0 || x > layout.width() - PORTAL_SIZE
                        || y < 0 || y > layout.height() - PORTAL_SIZE) {
                    throw new IllegalArgumentException("Research Tree portal lies outside its layout");
                }
                result.add(new PortalPlacement(localTargets.get(index), x, y));
            }
        }
        return List.copyOf(result);
    }

    private static BoundaryRelationshipCounts indexBoundaryRelationships(
            List<ResearchTreeProjection.CrossGroupLink> links) {
        Map<ResourceLocation, Integer> requirements = new java.util.HashMap<>();
        Map<ResourceLocation, Integer> unlocks = new java.util.HashMap<>();
        for (ResearchTreeProjection.CrossGroupLink link : links) {
            Map<ResourceLocation, Integer> target =
                    link.direction() == ResearchTreeProjection.Direction.REQUIREMENT
                            ? requirements : unlocks;
            target.merge(link.localNodeId(), 1, Math::addExact);
        }
        return new BoundaryRelationshipCounts(
                Map.copyOf(requirements), Map.copyOf(unlocks));
    }

    private static BoundaryRelationshipCounts indexTechBoundaryRelationships(
            List<ResearchTechTreeLayout.BoundaryPortal> portals) {
        Map<ResourceLocation, Integer> requirements = new java.util.HashMap<>();
        Map<ResourceLocation, Integer> unlocks = new java.util.HashMap<>();
        for (ResearchTechTreeLayout.BoundaryPortal portal : portals) {
            ResearchTechTreeLayout.PortalTarget target = portal.target();
            Map<ResourceLocation, Integer> counts = target.direction()
                    == ResearchTechTreeProjection.Direction.REQUIREMENT
                            ? requirements : unlocks;
            counts.merge(target.localNodeId(), target.connectionCount(), Math::addExact);
        }
        return new BoundaryRelationshipCounts(
                Map.copyOf(requirements), Map.copyOf(unlocks));
    }

    private static List<HiddenAnchorSpan> indexHiddenAnchors(ResearchTreeLayout layout) {
        return layout.hiddenAnchors().stream()
                .map(anchor -> {
                    ResearchTreeLayout.PositionedNode dependent = layout
                            .position(anchor.dependentId())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Research Tree hidden anchor has no dependent node"));
                    return new HiddenAnchorSpan(
                            anchor,
                            anchor.x() - 5,
                            Math.min(anchor.y() - 4, dependent.y()),
                            anchor.x() + 5,
                            Math.max(anchor.y() + 5, dependent.y()));
                })
                .sorted(java.util.Comparator
                        .comparingInt(HiddenAnchorSpan::minimumY)
                        .thenComparingInt(HiddenAnchorSpan::minimumX)
                        .thenComparing(span -> span.anchor().dependentId()))
                .toList();
    }

    private static List<PortalTarget> portalTargets(
            List<ResearchTreeProjection.CrossGroupLink> links) {
        Map<ResourceLocation, List<ResearchTreeProjection.CrossGroupLink>> byDestination =
                new java.util.LinkedHashMap<>();
        for (ResearchTreeProjection.CrossGroupLink link : links) {
            byDestination.computeIfAbsent(
                    link.remoteGroupId(), ignored -> new java.util.ArrayList<>()).add(link);
        }
        List<PortalTarget> targets = byDestination.values().stream()
                .map(PortalTarget::new)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        if (targets.size() <= MAX_VISIBLE_PORTALS_PER_BANK) {
            return List.copyOf(targets);
        }
        List<PortalTarget> result = new java.util.ArrayList<>(MAX_VISIBLE_PORTALS_PER_BANK);
        result.addAll(targets.subList(0, MAX_VISIBLE_PORTALS_PER_BANK - 1));
        List<ResearchTreeProjection.CrossGroupLink> overflow = new java.util.ArrayList<>();
        targets.subList(MAX_VISIBLE_PORTALS_PER_BANK - 1, targets.size())
                .forEach(target -> overflow.addAll(target.links()));
        result.add(new PortalTarget(overflow));
        return List.copyOf(result);
    }

    public record GraphElementHit(
            ResearchTreeGraph.Node node,
            PortalTarget portal) {
        public GraphElementHit {
            if ((node == null) == (portal == null)) {
                throw new IllegalArgumentException(
                        "Research Tree graph hit must identify exactly one element");
            }
        }
    }

    public record PortalTarget(
            List<ResearchTreeProjection.CrossGroupLink> links) {
        public PortalTarget {
            if (links == null || links.isEmpty()
                    || links.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Research Tree portal target cannot be empty");
            }
            links = List.copyOf(links);
            ResearchTreeProjection.CrossGroupLink first = links.get(0);
            if (links.stream().anyMatch(link ->
                    !link.localNodeId().equals(first.localNodeId())
                            || link.direction() != first.direction())) {
                throw new IllegalArgumentException(
                        "Research Tree portal target must share a local endpoint");
            }
        }

        public ResearchTreeProjection.CrossGroupLink primaryLink() {
            return links.get(0);
        }

        public int connectionCount() {
            return links.size();
        }

        public int destinationGroupCount() {
            return Math.toIntExact(links.stream()
                    .map(ResearchTreeProjection.CrossGroupLink::remoteGroupId)
                    .distinct()
                    .count());
        }
    }

    record PortalPlacement(
            PortalTarget target,
            int x,
            int y) {
        ResearchTreeProjection.CrossGroupLink link() {
            return target.primaryLink();
        }
    }

    private record PortalGroup(
            ResourceLocation localNodeId,
            ResearchTreeProjection.Direction direction) {
    }

    private record BoundaryRelationshipCounts(
            Map<ResourceLocation, Integer> requirements,
            Map<ResourceLocation, Integer> unlocks) {
    }

    private record TierLabelPosition(int tier, int centerY) {
    }

    private record HiddenAnchorSpan(
            ResearchTreeLayout.HiddenAnchor anchor,
            int minimumX,
            int minimumY,
            int maximumX,
            int maximumY) {
    }

    List<ResearchTreeLayout.HiddenAnchor> visibleHiddenAnchors(
            double minimumX,
            double minimumY,
            double maximumX,
            double maximumY) {
        if (maximumX < minimumX || maximumY < minimumY) {
            throw new IllegalArgumentException("invalid Research Tree visible bounds");
        }
        List<ResearchTreeLayout.HiddenAnchor> visible = new java.util.ArrayList<>();
        for (HiddenAnchorSpan span : hiddenAnchorSpans) {
            if (span.minimumY() > maximumY) {
                break;
            }
            if (span.maximumY() >= minimumY
                    && span.maximumX() >= minimumX
                    && span.minimumX() <= maximumX) {
                visible.add(span.anchor());
            }
        }
        return List.copyOf(visible);
    }

    boolean isHiddenAnchorVisible(ResearchTreeLayout.HiddenAnchor anchor) {
        Optional<ResearchTreeLayout.PositionedNode> dependent = layout.position(anchor.dependentId());
        if (dependent.isEmpty()) {
            return false;
        }
        ResearchTreeLayout.PositionedNode node = dependent.orElseThrow();
        int minimumY = Math.min(anchor.y() - 4, node.y());
        int maximumY = Math.max(anchor.y() + 5, node.y());
        return viewport().intersects(anchor.x() - 5, minimumY, 10, maximumY - minimumY + 1);
    }

    private void drawEdge(
            GuiGraphics graphics,
            ResearchTreeEdgeIndex.PositionedEdge positioned,
            ResearchTreePresentationContract.RelationshipRole role,
            ToIntFunction<ResearchTreeGraph.Node> nodeBorderColor) {
        int color = graph.node(positioned.edge().dependentId())
                .map(nodeBorderColor::applyAsInt)
                .orElse(style.edge());
        color = relationshipColor(role, color);
        for (int index = 1; index < positioned.points().size(); index++) {
            ResearchTreeEdgeIndex.RoutePoint start = positioned.points().get(index - 1);
            ResearchTreeEdgeIndex.RoutePoint end = positioned.points().get(index);
            if (start.x() == end.x()) {
                fillVerticalLine(graphics, start.x(), start.y(), end.y(), color);
            } else if (start.y() == end.y()) {
                fillHorizontalLine(graphics, start.x(), end.x(), start.y(), color);
            } else {
                throw new IllegalStateException("Research Tree route contains a diagonal segment");
            }
        }
        drawArrowhead(graphics, positioned.endX(), positioned.arrowBaseY(), positioned.endY(), color);
    }

    private ResearchTreePresentationContract.RelationshipRole edgeRole(
            ResearchTreeEdgeIndex.PositionedEdge positioned) {
        ResearchTreePresentationContract.RelationshipRole hoverRole =
                hoverPath.role(positioned.edge());
        ResearchTreePresentationContract.RelationshipRole role =
                hoverRole == ResearchTreePresentationContract.RelationshipRole.DIRECT_REQUIREMENT
                        || hoverRole == ResearchTreePresentationContract.RelationshipRole.DIRECT_UNLOCK
                        ? hoverRole
                        : focusPath.role(positioned.edge());
        if ((role == ResearchTreePresentationContract.RelationshipRole.UNRELATED
                || role == ResearchTreePresentationContract.RelationshipRole.NEUTRAL)
                && trackedPathEdges.contains(positioned.edge())) {
            return ResearchTreePresentationContract.RelationshipRole.UNLOCK_PATH;
        }
        return role;
    }

    private static int edgeLayer(ResearchTreePresentationContract.RelationshipRole role) {
        return switch (role) {
            case UNRELATED, NEUTRAL -> 0;
            case REQUIREMENT_PATH, UNLOCK_PATH -> 1;
            case SELECTED, DIRECT_REQUIREMENT, DIRECT_UNLOCK -> 2;
        };
    }

    private static void drawArrowhead(
            GuiGraphics graphics,
            int x,
            int baseY,
            int tipY,
            int color) {
        int height = Math.max(1, Math.abs(tipY - baseY));
        int minimumY = Math.min(baseY, tipY);
        int maximumY = Math.max(baseY, tipY);
        for (int y = minimumY; y <= maximumY; y++) {
            int halfWidth = (int) Math.ceil(3.0D * Math.abs(tipY - y) / height);
            graphics.fill(x - halfWidth, y, x + halfWidth + 1, y + 1, color);
        }
    }

    private void drawHiddenAnchor(
            GuiGraphics graphics,
            Font font,
            ResearchTreeLayout.HiddenAnchor anchor) {
        Optional<ResearchTreeLayout.PositionedNode> dependent = layout.position(anchor.dependentId());
        if (dependent.isEmpty()) {
            return;
        }
        ResearchTreeLayout.PositionedNode node = dependent.orElseThrow();
        int x = anchor.x();
        int y = anchor.y();
        fillVerticalLine(graphics, x, y + 5, node.y(), style.muted());
        graphics.fill(x - 2, y - 4, x + 3, y + 5, style.background());
        graphics.fill(x - 4, y - 2, x + 5, y + 3, style.background());
        graphics.renderOutline(x - 3, y - 3, 7, 7, style.muted());
        graphics.drawCenteredString(font, "?", x, y - 4, style.muted());
    }

    private void drawNode(
            GuiGraphics graphics,
            Font font,
            ResearchTreeGraph.Node node,
            ResearchTreeLayout.PositionedNode position,
            ResearchTreePresentationContract.CardDetail cardDetail,
            Function<ResearchTreeGraph.Node, Component> nodeName,
            ToIntFunction<ResearchTreeGraph.Node> nodeBorderColor,
            Function<ResearchTreeGraph.Node, ResearchTreePresentationContract.StatusSymbol> statusSymbol) {
        int x = position.x();
        int y = position.y();
        ResearchTreePresentationContract.RelationshipRole role = focusPath.role(node.blueprintId());
        ResearchTreePresentationContract.RelationshipRole hoverRole = hoverPath.role(node.blueprintId());
        int statusColor = nodeBorderColor.applyAsInt(node);
        ResearchTreePresentationContract.StatusSymbol symbol = statusSymbol.apply(node);
        if (symbol == null) {
            throw new IllegalArgumentException("Research Tree node status symbol cannot be null");
        }
        int fill = switch (symbol) {
            case LEARNED -> style.learnedFill();
            case AVAILABLE -> style.availableFill();
            case LOCKED -> style.lockedFill();
            case UNKNOWN -> style.hiddenFill();
            default -> style.defaultFill();
        };
        graphics.fill(x, y, x + ResearchTreeLayout.NODE_WIDTH, y + ResearchTreeLayout.NODE_HEIGHT, fill);
        graphics.fill(
                x + 1, y + 1,
                x + ResearchTreeLayout.NODE_WIDTH - 1, y + 3,
                statusColor);
        if (cardDetail == ResearchTreePresentationContract.CardDetail.DETAILED) {
            ItemStack icon = icon(node.blueprintId());
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, x + 4, y + 4);
            } else if (node.visibility() == JournalVisibility.SILHOUETTE) {
                graphics.drawCenteredString(
                        font, "?", x + ResearchTreeLayout.NODE_WIDTH / 2, y + 8, style.muted());
            } else if (node.visibility() == JournalVisibility.NAME) {
                String label = nodeName.apply(node).getString();
                String initial = label.isBlank()
                        ? "?"
                        : label.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
                graphics.drawCenteredString(
                        font, initial, x + ResearchTreeLayout.NODE_WIDTH / 2, y + 8, style.text());
            }
        } else if (cardDetail == ResearchTreePresentationContract.CardDetail.COMPACT) {
            ResearchTreeStatusGlyph.render(
                    graphics,
                    x + (ResearchTreeLayout.NODE_WIDTH - ResearchTreeStatusGlyph.SIZE) / 2,
                    y + (ResearchTreeLayout.NODE_HEIGHT - ResearchTreeStatusGlyph.SIZE) / 2,
                    statusColor,
                    ResearchTreeStatusGlyph.forSymbol(symbol));
        } else {
            graphics.fill(x + 9, y + 9, x + 15, y + 15, statusColor);
        }
        boolean hoverRelated = hoverRole != ResearchTreePresentationContract.RelationshipRole.NEUTRAL
                && hoverRole != ResearchTreePresentationContract.RelationshipRole.UNRELATED;
        if (role == ResearchTreePresentationContract.RelationshipRole.UNRELATED
                && !hoverRelated
                && !trackedPathNodeIds.contains(node.blueprintId())
                && !state.searchMatches().contains(node.blueprintId())) {
            graphics.fill(
                    x, y,
                    x + ResearchTreeLayout.NODE_WIDTH,
                    y + ResearchTreeLayout.NODE_HEIGHT,
                    style.unrelatedOverlay());
        }
        if (!ResearchTreeCategoryFilter.matches(node, categoryFilter)
                && state.focusedId().filter(node.blueprintId()::equals).isEmpty()
                && !state.searchMatches().contains(node.blueprintId())) {
            graphics.fill(
                    x, y,
                    x + ResearchTreeLayout.NODE_WIDTH,
                    y + ResearchTreeLayout.NODE_HEIGHT,
                    style.categoryOverlay());
        }
        int border = statusColor;
        if (hoverRole == ResearchTreePresentationContract.RelationshipRole.SELECTED) {
            border = style.hover();
        } else if (hoverRole == ResearchTreePresentationContract.RelationshipRole.DIRECT_REQUIREMENT
                || hoverRole == ResearchTreePresentationContract.RelationshipRole.DIRECT_UNLOCK) {
            border = relationshipColor(hoverRole, border);
        } else {
            border = relationshipColor(role, border);
        }
        if ((role == ResearchTreePresentationContract.RelationshipRole.UNRELATED
                || role == ResearchTreePresentationContract.RelationshipRole.NEUTRAL)
                && !hoverRelated
                && trackedPathNodeIds.contains(node.blueprintId())) {
            border = style.unlockPath();
        }
        graphics.renderOutline(
                x, y, ResearchTreeLayout.NODE_WIDTH, ResearchTreeLayout.NODE_HEIGHT, border);
        if (node.blueprintId().equals(authoritativeSelectedId)) {
            drawAuthoritativeSelectionMarker(graphics, x, y);
        }
        if (cardDetail == ResearchTreePresentationContract.CardDetail.DETAILED) {
            drawStatusBadge(graphics, x, y, statusColor, symbol);
        }
        if (state.focusedId().filter(node.blueprintId()::equals).isPresent()) {
            graphics.renderOutline(
                    x - 2, y - 2,
                    ResearchTreeLayout.NODE_WIDTH + 4,
                    ResearchTreeLayout.NODE_HEIGHT + 4,
                    style.accent());
        }
        if (state.searchMatches().contains(node.blueprintId())) {
            graphics.renderOutline(
                    x - 3, y - 3,
                    ResearchTreeLayout.NODE_WIDTH + 6,
                    ResearchTreeLayout.NODE_HEIGHT + 6,
                    style.text());
        }
        if (node.blueprintId().equals(activeSearchMatch)) {
            graphics.renderOutline(
                    x - 4, y - 4,
                    ResearchTreeLayout.NODE_WIDTH + 8,
                    ResearchTreeLayout.NODE_HEIGHT + 8,
                    style.accent());
        }
        if (node.blueprintId().equals(trackedTargetId)) {
            graphics.renderOutline(
                    x - 4, y - 4,
                    ResearchTreeLayout.NODE_WIDTH + 8,
                    ResearchTreeLayout.NODE_HEIGHT + 8,
                    style.directUnlock());
        }
    }

    private void drawAuthoritativeSelectionMarker(GuiGraphics graphics, int x, int y) {
        int bottom = y + ResearchTreeLayout.NODE_HEIGHT - 1;
        graphics.fill(x + 1, bottom - 5, x + 3, bottom + 1, style.text());
        graphics.fill(x + 1, bottom - 1, x + 9, bottom + 1, style.text());
    }

    private void drawStatusBadge(
            GuiGraphics graphics,
            int x,
            int y,
            int statusColor,
            ResearchTreePresentationContract.StatusSymbol symbol) {
        int badgeX = x + ResearchTreeLayout.NODE_WIDTH - 10;
        int badgeY = y + ResearchTreeLayout.NODE_HEIGHT - 10;
        graphics.fill(badgeX, badgeY, badgeX + 10, badgeY + 10, style.background());
        graphics.renderOutline(badgeX, badgeY, 10, 10, statusColor);
        ResearchTreeStatusGlyph.Glyph glyph = ResearchTreeStatusGlyph.forSymbol(symbol);
        ResearchTreeStatusGlyph.render(graphics, badgeX + 2, badgeY + 2, statusColor, glyph);
    }

    private int relationshipColor(
            ResearchTreePresentationContract.RelationshipRole role,
            int fallback) {
        return switch (role) {
            case SELECTED -> style.accent();
            case DIRECT_REQUIREMENT -> style.directRequirement();
            case REQUIREMENT_PATH -> style.requirementPath();
            case DIRECT_UNLOCK -> style.directUnlock();
            case UNLOCK_PATH -> style.unlockPath();
            case UNRELATED -> style.unrelated();
            case NEUTRAL -> fallback;
        };
    }

    private static void fillVerticalLine(
            GuiGraphics graphics,
            int x,
            int fromY,
            int toY,
            int color) {
        int minimum = Math.min(fromY, toY);
        int maximum = Math.max(fromY, toY);
        graphics.fill(x - 1, minimum, x + 1, maximum + 1, color);
    }

    private static void fillHorizontalLine(
            GuiGraphics graphics,
            int fromX,
            int toX,
            int y,
            int color) {
        int minimum = Math.min(fromX, toX);
        int maximum = Math.max(fromX, toX);
        graphics.fill(minimum, y - 1, maximum + 1, y + 1, color);
    }

    private static boolean intersects(
            double left,
            double top,
            double right,
            double bottom,
            double visibleLeft,
            double visibleTop,
            double visibleRight,
            double visibleBottom) {
        return right >= visibleLeft && left <= visibleRight
                && bottom >= visibleTop && top <= visibleBottom;
    }

    public record Style(
            int background,
            int grid,
            int border,
            int muted,
            int text,
            int accent,
            int edge,
            int learnedFill,
            int availableFill,
            int lockedFill,
            int hiddenFill,
            int defaultFill,
            int directRequirement,
            int requirementPath,
            int directUnlock,
            int unlockPath,
            int unrelated,
            int hover,
            int unrelatedOverlay,
            int categoryOverlay,
            int laneEven,
            int laneOdd,
            int laneBorder,
            int laneHeader) {
    }
}
