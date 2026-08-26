package com.gamergaming.taczweaponblueprints.client;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutEngine;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Reusable rendering and interaction surface for one published Research Tree. */
public final class ResearchTreeCanvas {
    static final int STICKY_HEADER_HEIGHT = 14;
    static final int STICKY_GUTTER_WIDTH = 24;
    static final int PORTAL_SIZE = 9;
    private static final int PORTAL_GAP = 2;
    private final ResearchTreeViewState state;
    private final Style style;
    private ResearchTreeScreenLayout.ViewMode viewMode = ResearchTreeScreenLayout.ViewMode.COMPACT;
    private ResearchTreeScreenLayout.Rect bounds = ResearchTreeScreenLayout.compact().canvas();
    private ResearchTreeGraph graph = ResearchTreeGraph.EMPTY;
    private ResearchTreeLayout layout = ResearchTreeLayout.EMPTY;
    private ResearchTreeEdgeIndex edgeIndex = ResearchTreeEdgeIndex.EMPTY;
    private ResearchTreeRelations relations = ResearchTreeRelations.EMPTY;
    private ResearchTreeRelations.FocusPath focusPath = ResearchTreeRelations.FocusPath.EMPTY;
    private ResearchTreeRelations.FocusPath hoverPath = ResearchTreeRelations.FocusPath.EMPTY;
    private Map<ResourceLocation, ItemStack> icons = Map.of();
    private List<ResearchTreeProjection.CrossGroupLink> crossGroupLinks = List.of();
    private List<PortalPlacement> portals = List.of();
    private ResourceLocation hoveredId;
    private ResourceLocation authoritativeSelectedId;
    private String categoryFilter;
    private boolean dragging;

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
        dragging = false;
        viewport().setAnimated(viewMode == ResearchTreeScreenLayout.ViewMode.FULLSCREEN);
        configureViewport();
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
        boolean topologyChanged = !this.graph.hasSameLayoutTopology(graph) || this.layout != layout;
        this.graph = graph;
        this.layout = layout;
        this.icons = Map.copyOf(icons);
        this.crossGroupLinks = List.copyOf(crossGroupLinks);
        this.portals = placePortals(graph, layout, this.crossGroupLinks);
        setAuthoritativeSelection(authoritativeSelection);
        if (topologyChanged) {
            dragging = false;
            edgeIndex = ResearchTreeEdgeIndex.create(graph, layout);
            relations = ResearchTreeRelations.create(graph);
        }
        state.retainVisibleNodes(graph, preferredFocus);
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
                    viewport.fit();
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

    public void render(
            GuiGraphics graphics,
            Font font,
            Function<ResearchTreeGraph.Node, Component> nodeName,
            ToIntFunction<ResearchTreeGraph.Node> nodeBorderColor,
            Function<ResearchTreeGraph.Node, ResearchTreePresentationContract.StatusSymbol> statusSymbol,
            Function<ResourceLocation, Component> groupName) {
        if (graphics == null || font == null || nodeName == null
                || nodeBorderColor == null || statusSymbol == null || groupName == null) {
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
                drawGroupRegions(graphics);
                drawCategoryLanes(graphics);
                drawTierGuides(graphics);
                double minimumX = viewport.canvasX(0.0D);
                double minimumY = viewport.canvasY(0.0D);
                double maximumX = viewport.canvasX(bounds.width());
                double maximumY = viewport.canvasY(bounds.height());
                List<ResearchTreeEdgeIndex.PositionedEdge> visibleEdges =
                        edgeIndex.visible(minimumX, minimumY, maximumX, maximumY);
                for (int layer = 0; layer < 3; layer++) {
                    for (ResearchTreeEdgeIndex.PositionedEdge edge : visibleEdges) {
                        ResearchTreePresentationContract.RelationshipRole role = edgeRole(edge);
                        if (edgeLayer(role) == layer) {
                            drawEdge(graphics, edge, role, nodeBorderColor);
                        }
                    }
                }
                for (ResearchTreeLayout.HiddenAnchor anchor : layout.hiddenAnchors()) {
                    if (isHiddenAnchorVisible(anchor)) {
                        drawHiddenAnchor(graphics, font, anchor);
                    }
                }
                drawPortals(graphics);
                for (ResearchTreeLayout.PositionedNode position : layout.nodes()) {
                    if (viewport.intersects(
                            position.x(), position.y(),
                            ResearchTreeLayout.NODE_WIDTH, ResearchTreeLayout.NODE_HEIGHT)) {
                        graph.node(position.blueprintId()).ifPresent(node ->
                                drawNode(
                                        graphics, font, node, position,
                                        nodeName, nodeBorderColor, statusSymbol));
                    }
                }
            } finally {
                graphics.pose().popPose();
            }
            if (viewMode == ResearchTreeScreenLayout.ViewMode.COMPACT) {
                drawStickyGroupHeaders(graphics, font, groupName);
                drawStickyCategoryHeaders(graphics, font);
                drawStickyTierLabels(graphics, font);
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
        Optional<ResearchTreeGraph.Node> clicked = nodeAt(mouseX, mouseY);
        if (button == 0 && clicked.isPresent()) {
            if (nodeSelection != null) {
                nodeSelection.accept(clicked.orElseThrow().blueprintId());
            }
            return true;
        }
        if (button == 0 || button == 1) {
            dragging = true;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(int button, double dragX, double dragY) {
        if (!dragging || (button != 0 && button != 1)) {
            return false;
        }
        viewport().panByScreenDelta(dragX, dragY);
        return true;
    }

    public boolean mouseReleased(int button) {
        if (!dragging || (button != 0 && button != 1)) {
            return false;
        }
        dragging = false;
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
        if (!contains(mouseX, mouseY) || layout.nodes().isEmpty()
                || isCoveredByCompactChrome(mouseX, mouseY)) {
            return Optional.empty();
        }
        ResearchTreeViewport viewport = viewport();
        double canvasX = viewport.canvasX(mouseX - bounds.x());
        double canvasY = viewport.canvasY(mouseY - bounds.y());
        for (ResearchTreeLayout.PositionedNode position : layout.nodes()) {
            if (canvasX >= position.x()
                    && canvasX < position.x() + ResearchTreeLayout.NODE_WIDTH
                    && canvasY >= position.y()
                    && canvasY < position.y() + ResearchTreeLayout.NODE_HEIGHT) {
                return graph.node(position.blueprintId());
            }
        }
        return Optional.empty();
    }

    public Optional<ResearchTreeProjection.CrossGroupLink> portalAt(
            double mouseX,
            double mouseY) {
        if (!contains(mouseX, mouseY) || portals.isEmpty()
                || isCoveredByCompactChrome(mouseX, mouseY)) {
            return Optional.empty();
        }
        ResearchTreeViewport viewport = viewport();
        double canvasX = viewport.canvasX(mouseX - bounds.x());
        double canvasY = viewport.canvasY(mouseY - bounds.y());
        return portals.stream()
                .filter(portal -> canvasX >= portal.x()
                        && canvasX < portal.x() + PORTAL_SIZE
                        && canvasY >= portal.y()
                        && canvasY < portal.y() + PORTAL_SIZE)
                .map(PortalPlacement::link)
                .findFirst();
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
        viewport().fit();
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
        int padding = ResearchTreeLayoutEngine.PADDING;
        int x = Math.max(0, minimumX - padding);
        int y = Math.max(0, minimumY - padding);
        int right = Math.min(layout.width(), maximumX + padding);
        int bottom = Math.min(layout.height(), maximumY + padding);
        viewport().fit(x, y, Math.max(1, right - x), Math.max(1, bottom - y));
        return true;
    }

    /** Fits one complete published group region without filtering the graph. */
    public boolean focusGroup(ResourceLocation groupId) {
        if (groupId == null) {
            throw new IllegalArgumentException("Research Tree group focus cannot be null");
        }
        Optional<ResearchTreeLayout.GroupRegion> region = layout.groupRegions().stream()
                .filter(candidate -> candidate.groupId().equals(groupId))
                .findFirst();
        if (region.isEmpty()) {
            return false;
        }
        ResearchTreeLayout.GroupRegion target = region.orElseThrow();
        viewport().fit(target.x(), target.y(), target.width(), target.height());
        return true;
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

    public void focusNode(ResourceLocation blueprintId) {
        setFocusedNode(blueprintId);
        layout.position(blueprintId).ifPresent(position -> viewport().focus(
                position.x(),
                position.y(),
                ResearchTreeLayout.NODE_WIDTH,
                ResearchTreeLayout.NODE_HEIGHT));
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
        ResearchTreeViewport viewport = viewport();
        int spacing = 16;
        int offsetX = Math.floorMod((int) Math.round(-viewport.panX() * viewport.scale()), spacing);
        int offsetY = Math.floorMod((int) Math.round(-viewport.panY() * viewport.scale()), spacing);
        for (int x = bounds.x() + offsetX; x < bounds.right(); x += spacing) {
            graphics.fill(x, bounds.y(), x + 1, bounds.bottom(), style.grid());
        }
        for (int y = bounds.y() + offsetY; y < bounds.bottom(); y += spacing) {
            graphics.fill(bounds.x(), y, bounds.right(), y + 1, style.grid());
        }
        if (viewMode == ResearchTreeScreenLayout.ViewMode.COMPACT) {
            graphics.renderOutline(
                    bounds.x(), bounds.y(), bounds.width(), bounds.height(), style.border());
        }
    }

    private void drawCategoryLanes(GuiGraphics graphics) {
        ResearchTreeViewport viewport = viewport();
        double minimumX = viewport.canvasX(0.0D);
        double maximumX = viewport.canvasX(bounds.width());
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

    private void drawGroupRegions(GuiGraphics graphics) {
        for (int index = 0; index < layout.groupRegions().size(); index++) {
            ResearchTreeLayout.GroupRegion region = layout.groupRegions().get(index);
            int background = index % 2 == 0 ? style.laneEven() : style.laneOdd();
            graphics.fill(
                    region.x(), region.y(), region.right(), region.bottom(), background);
            graphics.renderOutline(
                    region.x(), region.y(), region.width(), region.height(), style.laneBorder());
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

    private void drawStickyTierLabels(GuiGraphics graphics, Font font) {
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
        for (int tier = 0; tier < layout.tierCount(); tier++) {
            List<ResearchTreeLayout.PositionedNode> tierNodes = layout.tier(tier);
            if (tierNodes.isEmpty()) {
                continue;
            }
            int minimumY = tierNodes.stream()
                    .mapToInt(ResearchTreeLayout.PositionedNode::y)
                    .min()
                    .orElse(0);
            int maximumY = tierNodes.stream()
                    .mapToInt(node -> node.y() + ResearchTreeLayout.NODE_HEIGHT)
                    .max()
                    .orElse(minimumY);
            int y = bounds.y() + viewport.viewportY((minimumY + maximumY) / 2.0D)
                    - font.lineHeight / 2;
            if (y < bounds.y() + STICKY_HEADER_HEIGHT || y + font.lineHeight > bounds.bottom()) {
                continue;
            }
            Component label = Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.tier", tier + 1);
            graphics.drawCenteredString(
                    font, label, bounds.x() + STICKY_GUTTER_WIDTH / 2, y, style.muted());
        }
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

    private void drawTierGuides(GuiGraphics graphics) {
        for (int tier = 0; tier < layout.tierCount(); tier++) {
            List<ResearchTreeLayout.PositionedNode> tierNodes = layout.tier(tier);
            if (tierNodes.isEmpty()) {
                continue;
            }
            int minimumY = tierNodes.stream()
                    .mapToInt(ResearchTreeLayout.PositionedNode::y)
                    .min()
                    .orElse(0);
            int maximumBottom = tierNodes.stream()
                    .mapToInt(node -> node.y() + ResearchTreeLayout.NODE_HEIGHT)
                    .max()
                    .orElse(minimumY);
            int separatorY = tier == 0
                    ? maximumBottom
                    : maximumBottom + ResearchTreeLayoutEngine.VERTICAL_GAP / 2;
            graphics.fill(
                    ResearchTreeLayoutEngine.PADDING,
                    separatorY,
                    layout.width() - ResearchTreeLayoutEngine.PADDING,
                    separatorY + 1,
                    style.laneBorder());
        }
    }

    private void drawPortals(GuiGraphics graphics) {
        for (PortalPlacement portal : portals) {
            ResearchTreeLayout.PositionedNode local = layout.position(portal.link().localNodeId())
                    .orElseThrow();
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
            drawArrowhead(graphics, centerX, baseY, tipY, color);
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
        List<PortalPlacement> result = new java.util.ArrayList<>(links.size());
        for (Map.Entry<PortalGroup, List<ResearchTreeProjection.CrossGroupLink>> entry
                : grouped.entrySet()) {
            ResearchTreeLayout.PositionedNode local = layout.position(entry.getKey().localNodeId())
                    .orElseThrow();
            List<ResearchTreeProjection.CrossGroupLink> localLinks = entry.getValue();
            int portalStep = PORTAL_SIZE + PORTAL_GAP;
            int portalWidth = portalBankWidth(localLinks.size());
            ResearchTreeLayout.GroupRegion region = layout.groupRegions().stream()
                    .filter(candidate -> local.x() >= candidate.x()
                            && local.x() + ResearchTreeLayout.NODE_WIDTH <= candidate.right())
                    .findFirst()
                    .orElse(null);
            int minimumX = region == null ? 0 : region.x() + 2;
            int maximumX = region == null
                    ? layout.width() - portalWidth
                    : region.right() - 2 - portalWidth;
            int firstX = Math.max(
                    minimumX,
                    Math.min(maximumX, local.centerX() - portalWidth / 2));
            for (int index = 0; index < localLinks.size(); index++) {
                int x = firstX + index * portalStep;
                int y = entry.getKey().direction() == ResearchTreeProjection.Direction.UNLOCK
                        ? local.y() - PORTAL_SIZE - 5
                        : local.y() + ResearchTreeLayout.NODE_HEIGHT + 5;
                if (x < 0 || x > layout.width() - PORTAL_SIZE
                        || y < 0 || y > layout.height() - PORTAL_SIZE) {
                    throw new IllegalArgumentException("Research Tree portal lies outside its layout");
                }
                result.add(new PortalPlacement(localLinks.get(index), x, y));
            }
        }
        return List.copyOf(result);
    }

    record PortalPlacement(
            ResearchTreeProjection.CrossGroupLink link,
            int x,
            int y) {
    }

    private record PortalGroup(
            ResourceLocation localNodeId,
            ResearchTreeProjection.Direction direction) {
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
        fillVerticalLine(
                graphics, positioned.startX(), positioned.startY(), positioned.sourceExitY(), color);
        fillHorizontalLine(
                graphics, positioned.startX(), positioned.trackX(), positioned.sourceExitY(), color);
        fillVerticalLine(
                graphics, positioned.trackX(), positioned.sourceExitY(), positioned.targetApproachY(), color);
        fillHorizontalLine(
                graphics, positioned.trackX(), positioned.endX(), positioned.targetApproachY(), color);
        fillVerticalLine(
                graphics, positioned.endX(), positioned.targetApproachY(), positioned.arrowBaseY(), color);
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
        ResearchTreePresentationContract.CardDetail cardDetail =
                ResearchTreePresentationContract.cardDetail(viewport().scale());
        int fill = node.availability() == ResearchTreeGraph.Availability.LEARNED
                ? style.learnedFill() : style.defaultFill();
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

    public record Style(
            int background,
            int grid,
            int border,
            int muted,
            int text,
            int accent,
            int edge,
            int learnedFill,
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
