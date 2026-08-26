package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutEngine;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

class ResearchTreeCanvasTest {
    @Test
    void dynamicBoundsDriveHitTestingAndSelectionInAbsoluteScreenCoordinates() {
        ResearchTreeCanvas canvas = canvas();
        ResearchTreeGraph graph = graph("test:a", JournalVisibility.FULL);
        ResearchTreeLayout layout = layout("test:a");
        ResearchTreeScreenLayout.Rect bounds = new ResearchTreeScreenLayout.Rect(100, 50, 200, 100);
        canvas.setBounds(ResearchTreeScreenLayout.ViewMode.COMPACT, bounds);
        canvas.setContent(graph, layout, Map.of(), null);
        ResearchTreeLayout.PositionedNode position = layout.nodes().get(0);
        double screenX = bounds.x() + canvas.viewport().viewportX(position.centerX());
        double screenY = bounds.y() + canvas.viewport().viewportY(position.centerY());

        assertEquals(id("test:a"), canvas.nodeAt(screenX, screenY).orElseThrow().blueprintId());
        AtomicReference<ResourceLocation> selected = new AtomicReference<>();
        canvas.mouseClicked(screenX, screenY, 0, selected::set);
        assertEquals(id("test:a"), selected.get());
    }

    @Test
    void switchingPresentationRestoresIndependentViewportState() {
        ResearchTreeCanvas canvas = canvas();
        canvas.setContent(graph("test:a", JournalVisibility.FULL), layout("test:a"), Map.of(), null);
        canvas.setBounds(
                ResearchTreeScreenLayout.ViewMode.COMPACT,
                new ResearchTreeScreenLayout.Rect(100, 50, 200, 100));
        canvas.zoomAtCenter(1.0D);
        double compactScale = canvas.viewport().scale();

        canvas.setBounds(
                ResearchTreeScreenLayout.ViewMode.FULLSCREEN,
                new ResearchTreeScreenLayout.Rect(8, 34, 600, 360));
        double fullscreenScale = canvas.viewport().scale();
        canvas.setBounds(
                ResearchTreeScreenLayout.ViewMode.COMPACT,
                new ResearchTreeScreenLayout.Rect(100, 50, 200, 100));

        assertNotEquals(compactScale, fullscreenScale);
        assertEquals(compactScale, canvas.viewport().scale());
    }

    @Test
    void reusableBoundaryRejectsMismatchedLayoutsAndDisclosureViolatingIcons() {
        ResearchTreeCanvas canvas = canvas();
        Map<ResourceLocation, ItemStack> unknownIcon = new java.util.HashMap<>();
        unknownIcon.put(id("test:missing"), null);
        Map<ResourceLocation, ItemStack> redactedIcon = new java.util.HashMap<>();
        redactedIcon.put(ResearchTreeGraph.redactedNodeId(0), null);
        assertThrows(IllegalArgumentException.class, () -> canvas.setContent(
                graph("test:a", JournalVisibility.FULL),
                layout("test:b"),
                Map.of(),
                null));
        assertThrows(IllegalArgumentException.class, () -> canvas.setContent(
                graph("test:a", JournalVisibility.FULL),
                layout("test:a"),
                unknownIcon,
                null));
        assertThrows(IllegalArgumentException.class, () -> canvas.setContent(
                graph("test:a", JournalVisibility.NAME),
                layout(ResearchTreeGraph.redactedNodeId(0)),
                redactedIcon,
                null));
        canvas.setContent(
                graph("test:a", JournalVisibility.FULL), layout("test:a"), Map.of(), null);
        assertThrows(IllegalArgumentException.class, () ->
                canvas.setFocusedNode(id("test:missing")));
    }

    @Test
    void focusAndHoverExposeCompleteAndDirectRelationshipRoles() {
        ResearchTreeCanvas canvas = canvas();
        ResearchTreeGraph graph = chainGraph();
        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(graph);
        ResearchTreeScreenLayout.Rect bounds = new ResearchTreeScreenLayout.Rect(50, 30, 200, 160);
        canvas.setBounds(ResearchTreeScreenLayout.ViewMode.COMPACT, bounds);
        canvas.setContent(graph, layout, Map.of(), null);
        canvas.setFocusedNode(id("test:c"));

        assertEquals(
                ResearchTreePresentationContract.RelationshipRole.REQUIREMENT_PATH,
                canvas.relationshipRole(id("test:a")));
        assertEquals(
                ResearchTreePresentationContract.RelationshipRole.DIRECT_REQUIREMENT,
                canvas.relationshipRole(id("test:b")));
        assertEquals(List.of(id("test:b")), canvas.directRequirements(id("test:c")));

        ResearchTreeLayout.PositionedNode hovered = layout.position(id("test:b")).orElseThrow();
        double mouseX = bounds.x() + canvas.viewport().viewportX(hovered.centerX());
        double mouseY = bounds.y() + canvas.viewport().viewportY(hovered.centerY());
        canvas.updateHover(mouseX, mouseY);

        assertEquals(
                ResearchTreePresentationContract.RelationshipRole.DIRECT_REQUIREMENT,
                canvas.hoverRelationshipRole(id("test:a")));
        assertEquals(
                ResearchTreePresentationContract.RelationshipRole.DIRECT_UNLOCK,
                canvas.hoverRelationshipRole(id("test:c")));
        assertEquals(List.of(id("test:c")), canvas.directUnlocks(id("test:b")));
    }

    @Test
    void stickyOverlaysDoNotSelectCoveredNodes() {
        ResearchTreeCanvas canvas = canvas();
        ResearchTreeGraph graph = graph("test:a", JournalVisibility.FULL);
        ResearchTreeLayout layout = new ResearchTreeLayout(
                64,
                64,
                1,
                List.of(new ResearchTreeLayout.PositionedNode(
                        0, id("test:a"), 0, 0, 0, 0, 0)),
                List.of(),
                List.of(new ResearchTreeLayout.CategoryLane("rifle", 0, 64)));
        ResearchTreeScreenLayout.Rect bounds = new ResearchTreeScreenLayout.Rect(100, 50, 64, 64);
        canvas.setBounds(ResearchTreeScreenLayout.ViewMode.COMPACT, bounds);
        canvas.setContent(graph, layout, Map.of(), null);

        assertTrue(canvas.categoryHeaderAt(110, 55).isPresent());
        assertTrue(canvas.nodeAt(110, 55).isEmpty());
        assertTrue(canvas.nodeAt(105, 70).isEmpty());
    }

    @Test
    void fullscreenHasNoStickyDeadZoneOverGraphContent() {
        ResearchTreeCanvas canvas = canvas();
        ResearchTreeGraph graph = graph("test:a", JournalVisibility.FULL);
        ResearchTreeLayout layout = new ResearchTreeLayout(
                64,
                64,
                1,
                List.of(new ResearchTreeLayout.PositionedNode(
                        0, id("test:a"), 0, 0, 0, 0, 0)),
                List.of(),
                List.of(new ResearchTreeLayout.CategoryLane("rifle", 0, 64)));
        ResearchTreeScreenLayout.Rect bounds = new ResearchTreeScreenLayout.Rect(0, 0, 64, 64);
        canvas.setBounds(ResearchTreeScreenLayout.ViewMode.FULLSCREEN, bounds);
        canvas.setContent(graph, layout, Map.of(), null);

        ResearchTreeLayout.PositionedNode position = layout.nodes().get(0);
        double screenX = canvas.viewport().viewportX(position.centerX());
        double screenY = canvas.viewport().viewportY(position.centerY());
        assertEquals(id("test:a"), canvas.nodeAt(screenX, screenY).orElseThrow().blueprintId());
        assertTrue(canvas.categoryHeaderAt(screenX, 2).isEmpty());
    }

    @Test
    void categoryFocusUsesPublishedLanesAndClearsBackToAll() {
        ResearchTreeCanvas canvas = canvas();
        ResearchTreeGraph graph = graph("test:a", JournalVisibility.FULL);
        ResearchTreeLayout layout = new ResearchTreeLayout(
                96,
                96,
                1,
                List.of(new ResearchTreeLayout.PositionedNode(
                        0, id("test:a"), 0, 0, 0, 32, 32)),
                List.of(),
                List.of(new ResearchTreeLayout.CategoryLane("rifle", 16, 64)));
        canvas.setContent(graph, layout, Map.of(), null);

        assertEquals(List.of("rifle"), canvas.categoryKeys());
        canvas.focusCategory("rifle");
        assertEquals("rifle", canvas.categoryFilter().orElseThrow());
        assertEquals("Rifle", canvas.categoryName("rifle").getString());
        canvas.focusCategory(null);
        assertTrue(canvas.categoryFilter().isEmpty());
        canvas.focusCategory("rifle");
        canvas.setContent(graph, layout("test:a"), Map.of(), null);
        assertTrue(canvas.categoryFilter().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> canvas.focusCategory("pistol"));
        assertThrows(IllegalArgumentException.class, () -> canvas.categoryName("pistol"));
    }

    @Test
    void topologyChangesReconcileBothPresentationViewports() {
        ResearchTreeViewState state = new ResearchTreeViewState();
        ResearchTreeCanvas canvas = new ResearchTreeCanvas(state, style());
        canvas.setBounds(
                ResearchTreeScreenLayout.ViewMode.COMPACT,
                new ResearchTreeScreenLayout.Rect(0, 0, 120, 80));
        state.viewport(ResearchTreeScreenLayout.ViewMode.FULLSCREEN)
                .configure(360, 180, 64, 64);
        canvas.setContent(graph("test:a", JournalVisibility.FULL), layout("test:a"), Map.of(), null);
        canvas.viewport().zoomAt(1.0D, 60, 40);
        state.viewport(ResearchTreeScreenLayout.ViewMode.FULLSCREEN)
                .zoomAt(1.0D, 180, 90);

        ResearchTreeGraph replacement = chainGraph();
        ResearchTreeLayout replacementLayout = ResearchTreeLayoutEngine.layout(replacement);
        canvas.setContent(replacement, replacementLayout, Map.of(), null);

        ResearchTreeViewport compact = state.viewport(ResearchTreeScreenLayout.ViewMode.COMPACT);
        ResearchTreeViewport fullscreen = state.viewport(ResearchTreeScreenLayout.ViewMode.FULLSCREEN);
        assertTrue(compact.viewportX(0) >= 0);
        assertTrue(compact.viewportY(0) >= 0);
        assertTrue(compact.viewportX(replacementLayout.width()) <= 120);
        assertTrue(compact.viewportY(replacementLayout.height()) <= 80);
        assertTrue(fullscreen.viewportX(0) >= 0);
        assertTrue(fullscreen.viewportY(0) >= 0);
        assertTrue(fullscreen.viewportX(replacementLayout.width()) <= 360);
        assertTrue(fullscreen.viewportY(replacementLayout.height()) <= 180);
    }

    @Test
    void topologyReplacementCancelsAnActiveCanvasPointerGesture() {
        ResearchTreeCanvas canvas = canvas();
        canvas.setBounds(
                ResearchTreeScreenLayout.ViewMode.COMPACT,
                new ResearchTreeScreenLayout.Rect(0, 0, 120, 80));
        canvas.setContent(
                graph("test:a", JournalVisibility.FULL),
                layout("test:a"),
                Map.of(),
                null);
        assertTrue(canvas.mouseClicked(100, 70, 1, null));

        ResearchTreeGraph replacement = chainGraph();
        canvas.setContent(
                replacement,
                ResearchTreeLayoutEngine.layout(replacement),
                Map.of(),
                null);

        assertFalse(canvas.mouseDragged(1, 10, 10));
        assertFalse(canvas.mouseReleased(1));
    }

    @Test
    void stateOnlyPublicationReusesPositionsWithoutReturningStaleNodes() {
        ResearchTreeCanvas canvas = canvas();
        ResearchTreeScreenLayout.Rect bounds =
                new ResearchTreeScreenLayout.Rect(0, 0, 120, 80);
        ResearchTreeGraph initial = graph("test:a", JournalVisibility.FULL);
        ResearchTreeLayout layout = layout("test:a");
        canvas.setBounds(ResearchTreeScreenLayout.ViewMode.FULLSCREEN, bounds);
        canvas.setContent(initial, layout, Map.of(), null);
        ResearchTreeLayout.PositionedNode position = layout.nodes().get(0);
        double mouseX = canvas.viewport().viewportX(position.centerX());
        double mouseY = canvas.viewport().viewportY(position.centerY());
        assertEquals(
                ResearchTreeGraph.Availability.AVAILABLE,
                canvas.nodeAt(mouseX, mouseY).orElseThrow().availability());

        ResearchTreeGraph.Node previous = initial.nodes().get(0);
        ResearchTreeGraph stateOnly = new ResearchTreeGraph(List.of(
                new ResearchTreeGraph.Node(
                        previous.ordinal(),
                        previous.blueprintId(),
                        previous.nameKey(),
                        previous.itemType(),
                        previous.displaySlotId(),
                        previous.visibility(),
                        true,
                        previous.discovered(),
                        false,
                        previous.pointCost(),
                        previous.ingredientTypeCount(),
                        previous.prerequisiteCount(),
                        previous.hiddenPrerequisiteCount(),
                        ResearchTreeGraph.Availability.LEARNED)),
                List.of());
        assertFalse(canvas.setContent(stateOnly, layout, Map.of(), null));

        assertEquals(
                ResearchTreeGraph.Availability.LEARNED,
                canvas.nodeAt(mouseX, mouseY).orElseThrow().availability());
    }

    @Test
    void compactDragRemainsOwnedByItsInitiatingButton() {
        ResearchTreeCanvas canvas = canvas();
        canvas.setBounds(
                ResearchTreeScreenLayout.ViewMode.COMPACT,
                new ResearchTreeScreenLayout.Rect(0, 0, 120, 80));
        canvas.setContent(
                graph("test:a", JournalVisibility.FULL),
                layout("test:a"),
                Map.of(),
                null);

        assertTrue(canvas.mouseClicked(119, 79, 1, null));
        assertFalse(canvas.mouseDragged(0, 8, 4));
        assertFalse(canvas.mouseReleased(0));
        assertFalse(canvas.mouseClicked(119, 79, 0, null));
        assertTrue(canvas.mouseDragged(1, 8, 4));
        assertTrue(canvas.mouseReleased(1));
        assertFalse(canvas.mouseDragged(1, 8, 4));
    }

    @Test
    void hiddenAnchorsAreCulledWhenTheirWholeConnectionIsOffscreen() {
        ResearchTreeCanvas canvas = canvas();
        ResearchTreeGraph graph = graph("test:a", JournalVisibility.FULL);
        ResearchTreeLayout.HiddenAnchor anchor =
                new ResearchTreeLayout.HiddenAnchor(id("test:a"), 1, 32, 5);
        ResearchTreeLayout layout = new ResearchTreeLayout(
                64,
                600,
                1,
                List.of(new ResearchTreeLayout.PositionedNode(
                        0, id("test:a"), 0, 0, 0, 20, 20)),
                List.of(anchor));
        canvas.setBounds(
                ResearchTreeScreenLayout.ViewMode.COMPACT,
                new ResearchTreeScreenLayout.Rect(0, 0, 100, 100));
        canvas.setContent(graph, layout, Map.of(), null);

        assertTrue(canvas.isHiddenAnchorVisible(anchor));
        canvas.viewport().zoomAt(1.0D, 50, 50);
        canvas.viewport().panByScreenDelta(0, -1_000);
        assertFalse(canvas.isHiddenAnchorVisible(anchor));
    }

    @Test
    void authoritativeSelectionRemainsSeparateFromLocalAnonymousFocus() {
        ResearchTreeCanvas canvas = canvas();
        ResearchTreeGraph fullGraph = graph("test:a", JournalVisibility.FULL);
        canvas.setContent(fullGraph, layout("test:a"), Map.of(), id("test:a"));

        assertEquals(id("test:a"), canvas.authoritativeSelectedId().orElseThrow());
        canvas.setFocusedNode(id("test:a"));
        canvas.setAuthoritativeSelection(id("test:missing"));
        assertTrue(canvas.authoritativeSelectedId().isEmpty());
        assertEquals(id("test:a"), canvas.focusedId().orElseThrow());

        ResearchTreeGraph previewGraph = graph("test:preview", JournalVisibility.PREVIEW);
        canvas.setContent(
                previewGraph,
                layout("test:preview"),
                Map.of(),
                id("test:preview"));
        assertEquals(id("test:preview"), canvas.authoritativeSelectedId().orElseThrow());

        ResearchTreeGraph anonymousGraph = graph("test:hidden", JournalVisibility.NAME);
        ResourceLocation anonymousId = ResearchTreeGraph.redactedNodeId(0);
        canvas.setContent(anonymousGraph, layout(anonymousId), Map.of(), anonymousId);
        canvas.setFocusedNode(anonymousId);

        assertEquals(anonymousId, canvas.focusedId().orElseThrow());
        assertTrue(canvas.authoritativeSelectedId().isEmpty());
    }

    @Test
    void fittingAGroupRegionKeepsTheAllWeaponsProjectionIntact() {
        ResearchTreeCanvas canvas = canvas();
        ResearchTreeGraph graph = chainGraph();
        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(graph);
        canvas.setBounds(
                ResearchTreeScreenLayout.ViewMode.FULLSCREEN,
                new ResearchTreeScreenLayout.Rect(0, 0, 120, 80));
        canvas.setContent(graph, layout, Map.of(), id("test:a"));

        assertTrue(canvas.focusNodes(Set.of(id("test:b"), id("test:c"))));
        assertEquals(3, canvas.graph().nodes().size());
        assertEquals(2, canvas.graph().edges().size());
        assertEquals(id("test:a"), canvas.authoritativeSelectedId().orElseThrow());
        assertFalse(canvas.focusNodes(Set.of(id("test:missing"))));
        assertFalse(canvas.focusNodes(Set.of()));
        assertThrows(IllegalArgumentException.class, () -> canvas.focusNodes(null));
    }

    @Test
    void branchPortalsAreClickableWithoutAddingRemoteNodesToTheProjection() {
        ResearchTreeCanvas canvas = canvas();
        ResearchTreeGraph graph = graph("test:a", JournalVisibility.FULL);
        ResearchTreeLayout layout = layout("test:a");
        ResearchTreeProjection.CrossGroupLink link =
                new ResearchTreeProjection.CrossGroupLink(
                        id("test:a"),
                        id("test:remote"),
                        id("test:remote_group"),
                        ResearchTreeProjection.Direction.UNLOCK);
        ResearchTreeScreenLayout.Rect bounds =
                new ResearchTreeScreenLayout.Rect(100, 50, 200, 100);
        canvas.setBounds(ResearchTreeScreenLayout.ViewMode.COMPACT, bounds);
        canvas.setContent(graph, layout, Map.of(), null, null, List.of(link));
        ResearchTreeCanvas.PortalPlacement portal = canvas.portalPlacements().get(0);
        double mouseX = bounds.x() + canvas.viewport().viewportX(
                portal.x() + ResearchTreeCanvas.PORTAL_SIZE / 2.0D);
        double mouseY = bounds.y() + canvas.viewport().viewportY(
                portal.y() + ResearchTreeCanvas.PORTAL_SIZE / 2.0D);

        assertEquals(link, canvas.portalAt(mouseX, mouseY).orElseThrow());
        assertTrue(canvas.graph().node(link.remoteNodeId()).isEmpty());
        List<ResearchTreeProjection.CrossGroupLink> portalBank = List.of(
                link,
                new ResearchTreeProjection.CrossGroupLink(
                        id("test:a"), id("test:remote_2"), id("test:remote_group"),
                        ResearchTreeProjection.Direction.UNLOCK),
                new ResearchTreeProjection.CrossGroupLink(
                        id("test:a"), id("test:remote_3"), id("test:remote_group"),
                        ResearchTreeProjection.Direction.UNLOCK));
        canvas.setContent(graph, layout, Map.of(), null, null, portalBank);
        assertEquals(31, ResearchTreeCanvas.portalBankWidth(3));
        assertTrue(canvas.portalPlacements().get(0).x() + ResearchTreeCanvas.PORTAL_SIZE
                < canvas.portalPlacements().get(1).x());
        assertTrue(canvas.portalPlacements().get(1).x() + ResearchTreeCanvas.PORTAL_SIZE
                < canvas.portalPlacements().get(2).x());
        assertThrows(IllegalArgumentException.class, () -> canvas.setContent(
                graph,
                layout,
                Map.of(),
                null,
                null,
                List.of(new ResearchTreeProjection.CrossGroupLink(
                        id("test:missing"),
                        id("test:remote"),
                        id("test:remote_group"),
                        ResearchTreeProjection.Direction.UNLOCK))));
    }

    @Test
    void allWeaponsGroupFocusUsesPublishedRegionBounds() {
        ResearchTreeCanvas canvas = canvas();
        ResearchTreeGraph graph = graph("test:a", JournalVisibility.FULL);
        ResourceLocation groupId = id("test:group");
        ResearchTreeLayout layout = new ResearchTreeLayout(
                120,
                80,
                1,
                List.of(new ResearchTreeLayout.PositionedNode(
                        0, id("test:a"), 0, 0, 0, 48, 32)),
                List.of(),
                List.of(),
                List.of(new ResearchTreeLayout.GroupRegion(groupId, 24, 8, 72, 64)));
        canvas.setBounds(
                ResearchTreeScreenLayout.ViewMode.FULLSCREEN,
                new ResearchTreeScreenLayout.Rect(0, 0, 60, 40));
        canvas.setContent(graph, layout, Map.of(), null);

        assertTrue(canvas.focusGroup(groupId));
        assertEquals(1, canvas.graph().nodes().size());
        assertFalse(canvas.focusGroup(id("test:missing")));
        assertThrows(IllegalArgumentException.class, () -> canvas.focusGroup(null));
    }

    private static ResearchTreeCanvas canvas() {
        return new ResearchTreeCanvas(new ResearchTreeViewState(), style());
    }

    private static ResearchTreeCanvas.Style style() {
        return new ResearchTreeCanvas.Style(
                1, 2, 3, 4, 5, 6, 7, 8, 9,
                10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21);
    }

    private static ResearchTreeGraph graph(String value, JournalVisibility visibility) {
        ResourceLocation blueprintId = visibility.revealsIdentity()
                ? id(value)
                : ResearchTreeGraph.redactedNodeId(0);
        return new ResearchTreeGraph(
                List.of(new ResearchTreeGraph.Node(
                        0,
                        blueprintId,
                        visibility.revealsName() ? "name.a" : ResearchTreeGraph.REDACTED_NAME_KEY,
                        visibility.revealsIdentity() ? "rifle" : ResearchTreeGraph.REDACTED_ITEM_TYPE,
                        visibility.revealsIcon()
                                ? id("test:slot/a")
                                : ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                        visibility,
                        false,
                        false,
                        visibility.revealsExactPolicy(),
                        visibility.revealsResearchSummary() ? 8 : 0,
                        0,
                        0,
                        0,
                        visibility.revealsExactPolicy()
                                ? ResearchTreeGraph.Availability.AVAILABLE
                                : visibility.revealsResearchSummary()
                                        ? ResearchTreeGraph.Availability.PREVIEW
                                        : ResearchTreeGraph.Availability.REDACTED)),
                List.of());
    }

    private static ResearchTreeGraph chainGraph() {
        return new ResearchTreeGraph(
                List.of(
                        fullNode(0, "test:a", 0),
                        fullNode(1, "test:b", 1),
                        fullNode(2, "test:c", 1)),
                List.of(
                        new ResearchTreeGraph.Edge(id("test:a"), id("test:b")),
                        new ResearchTreeGraph.Edge(id("test:b"), id("test:c"))));
    }

    private static ResearchTreeGraph.Node fullNode(int ordinal, String value, int prerequisites) {
        ResourceLocation blueprintId = id(value);
        return new ResearchTreeGraph.Node(
                ordinal,
                blueprintId,
                "name." + blueprintId.getPath(),
                "rifle",
                id("test:slot/" + ordinal),
                JournalVisibility.FULL,
                false, false, prerequisites == 0, 8, 0, prerequisites, 0,
                prerequisites == 0
                        ? ResearchTreeGraph.Availability.AVAILABLE
                        : ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
    }

    private static ResearchTreeLayout layout(String value) {
        return layout(id(value));
    }

    private static ResearchTreeLayout layout(ResourceLocation blueprintId) {
        return new ResearchTreeLayout(
                64,
                64,
                1,
                List.of(new ResearchTreeLayout.PositionedNode(
                        0, blueprintId, 0, 0, 0, 20, 20)));
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
