package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeBranchLayoutComposer;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGroupSkeleton;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGroupSkeletonBuilder;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGroupSkeletonCatalog;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayeredLayoutEngine;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeOverviewLayoutComposer;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;

import net.minecraft.resources.ResourceLocation;

/** Lazily builds curated overview and Branches projections with reusable layouts. */
public final class ResearchTreeProjectionCache {
    private static final ResearchTreeLayoutPolicy DEFAULT_LAYOUT_POLICY =
            ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW;
    private static final ProjectionKey ALL_WEAPONS = new ProjectionKey(
            ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
            Optional.empty());

    private ResearchTreePublication publication = ResearchTreePublication.EMPTY;
    private ResearchTreePublication legacyPublication = ResearchTreePublication.EMPTY;
    private ResearchTreeOverviewBuilder.Result overview =
            ResearchTreeOverviewBuilder.build(ResearchTreePublication.EMPTY);
    private ResearchTreeGroupSkeletonCatalog groupSkeletons =
            ResearchTreeGroupSkeletonCatalog.EMPTY;
    private ResearchTechTreeProjectionCatalog techTreeProjections =
            ResearchTechTreeProjectionCatalog.EMPTY;
    private ResearchTechTreeLayoutCatalog techTreeLayouts =
            ResearchTechTreeLayoutCatalog.EMPTY;
    private ResearchTreeUnlockIndex unlocks = ResearchTreeUnlockIndex.EMPTY;
    private final Map<ProjectionKey, ResearchTreeProjection> projections = new LinkedHashMap<>();
    private final Map<ProjectionKey, ResearchTreeLayout> layouts = new LinkedHashMap<>();
    private final Map<TechViewportKey, ResearchTechTreeLayout> responsiveTechLayouts =
            new LinkedHashMap<>();
    private final OverviewLayoutFactory overviewLayoutFactory;
    private final FallbackLayoutFactory fallbackLayoutFactory;
    private final BranchLayoutFactory branchLayoutFactory;
    private final TechLayoutFactory techLayoutFactory;
    private ResearchTreeLayoutPolicy layoutPolicy = DEFAULT_LAYOUT_POLICY;
    private ResearchTechTreeLayoutPolicy techLayoutPolicy =
            ResearchTechTreeLayoutPolicy.fromShared(DEFAULT_LAYOUT_POLICY);
    private boolean overviewFallbackActive;

    public ResearchTreeProjectionCache() {
        this(
                ResearchTreeOverviewLayoutComposer::compose,
                ResearchTreeLayeredLayoutEngine::layout,
                ResearchTreeBranchLayoutComposer::compose,
                ResearchTechTreeLayoutEngine::layoutCatalog);
    }

    ResearchTreeProjectionCache(OverviewLayoutFactory overviewLayoutFactory) {
        this(
                overviewLayoutFactory,
                ResearchTreeLayeredLayoutEngine::layout,
                ResearchTreeBranchLayoutComposer::compose,
                ResearchTechTreeLayoutEngine::layoutCatalog);
    }

    ResearchTreeProjectionCache(
            OverviewLayoutFactory overviewLayoutFactory,
            BranchLayoutFactory branchLayoutFactory) {
        this(
                overviewLayoutFactory,
                ResearchTreeLayeredLayoutEngine::layout,
                branchLayoutFactory,
                ResearchTechTreeLayoutEngine::layoutCatalog);
    }

    ResearchTreeProjectionCache(
            OverviewLayoutFactory overviewLayoutFactory,
            FallbackLayoutFactory fallbackLayoutFactory) {
        this(
                overviewLayoutFactory,
                fallbackLayoutFactory,
                ResearchTreeBranchLayoutComposer::compose,
                ResearchTechTreeLayoutEngine::layoutCatalog);
    }

    ResearchTreeProjectionCache(
            OverviewLayoutFactory overviewLayoutFactory,
            BranchLayoutFactory branchLayoutFactory,
            TechLayoutFactory techLayoutFactory) {
        this(
                overviewLayoutFactory,
                ResearchTreeLayeredLayoutEngine::layout,
                branchLayoutFactory,
                techLayoutFactory);
    }

    ResearchTreeProjectionCache(
            OverviewLayoutFactory overviewLayoutFactory,
            FallbackLayoutFactory fallbackLayoutFactory,
            BranchLayoutFactory branchLayoutFactory,
            TechLayoutFactory techLayoutFactory) {
        if (overviewLayoutFactory == null) {
            throw new IllegalArgumentException("Research Tree overview layout factory cannot be null");
        }
        if (fallbackLayoutFactory == null) {
            throw new IllegalArgumentException("Research Tree fallback layout factory cannot be null");
        }
        if (branchLayoutFactory == null) {
            throw new IllegalArgumentException("Research Tree branch layout factory cannot be null");
        }
        if (techLayoutFactory == null) {
            throw new IllegalArgumentException("Research Tech Tree layout factory cannot be null");
        }
        this.overviewLayoutFactory = overviewLayoutFactory;
        this.fallbackLayoutFactory = fallbackLayoutFactory;
        this.branchLayoutFactory = branchLayoutFactory;
        this.techLayoutFactory = techLayoutFactory;
    }

    /** Uses the stable built-in visual defaults for compatibility callers. */
    public boolean update(ResearchTreePublication nextPublication) {
        return update(nextPublication, DEFAULT_LAYOUT_POLICY);
    }

    /**
     * Returns true when legacy or Tech Tree geometry changed and saved camera
     * state must be invalidated. Legacy and typed layouts remain independently
     * reusable when only player state changes.
     */
    public boolean update(
            ResearchTreePublication nextPublication,
            ResearchTreeLayoutPolicy nextLayoutPolicy) {
        if (nextPublication == null) {
            throw new IllegalArgumentException("Research Tree projection publication cannot be null");
        }
        if (nextLayoutPolicy == null) {
            throw new IllegalArgumentException("Research Tree layout policy cannot be null");
        }

        ResearchTreePublication nextLegacyPublication = nextPublication.legacyView();
        boolean topologyChanged = !legacyPublication.hasSamePresentationTopology(
                nextLegacyPublication);
        boolean policyChanged = !layoutPolicy.equals(nextLayoutPolicy);
        boolean geometryChanged = topologyChanged || policyChanged;
        ResearchTreeOverviewBuilder.Result nextOverview =
                ResearchTreeOverviewBuilder.build(nextLegacyPublication);
        ResearchTechTreeProjectionCatalog nextTechTreeProjections =
                ResearchTechTreeProjectionBuilder.build(nextPublication);
        ResearchTreeUnlockIndex nextUnlocks = ResearchTreeUnlockIndex.create(
                nextPublication.graph());
        ResearchTechTreeLayoutPolicy nextTechLayoutPolicy =
                ResearchTechTreeLayoutPolicy.fromShared(nextLayoutPolicy);
        boolean techGeometryChanged = !techTreeProjections.hasSameTopology(
                nextTechTreeProjections)
                || !techLayoutPolicy.equals(nextTechLayoutPolicy);
        ResearchTechTreeLayoutCatalog nextTechTreeLayouts = techGeometryChanged
                ? techLayoutFactory.layout(
                        nextTechTreeProjections, nextTechLayoutPolicy)
                : techTreeLayouts;
        ResearchTreeGroupSkeletonCatalog nextGroupSkeletons = geometryChanged
                ? ResearchTreeGroupSkeletonBuilder.build(
                        nextLegacyPublication, nextLayoutPolicy)
                : groupSkeletons;
        boolean seedMissingOverview = !geometryChanged && !layouts.containsKey(ALL_WEAPONS);
        OverviewLayoutBuild nextOverviewLayout = geometryChanged || seedMissingOverview
                ? buildOverviewLayout(nextOverview, nextGroupSkeletons, nextLayoutPolicy)
                : null;

        // Commit only after every derived topology object has been prepared.
        // Search, navigation, layouts, and the canvas must never observe halves
        // of different publications if a layout implementation rejects input.
        publication = nextPublication;
        legacyPublication = nextLegacyPublication;
        overview = nextOverview;
        groupSkeletons = nextGroupSkeletons;
        techTreeProjections = nextTechTreeProjections;
        techTreeLayouts = nextTechTreeLayouts;
        unlocks = nextUnlocks;
        layoutPolicy = nextLayoutPolicy;
        techLayoutPolicy = nextTechLayoutPolicy;
        projections.clear();
        if (techGeometryChanged) {
            responsiveTechLayouts.clear();
        }
        if (geometryChanged) {
            layouts.clear();
            layouts.put(ALL_WEAPONS, nextOverviewLayout.layout());
            overviewFallbackActive = nextOverviewLayout.fallbackUsed();
        } else {
            // A freshly cleared empty cache has no prior derived layout. All
            // non-empty state-only updates necessarily follow a topology
            // commit and therefore retain the existing immutable instance.
            if (seedMissingOverview) {
                layouts.put(ALL_WEAPONS, nextOverviewLayout.layout());
                overviewFallbackActive = nextOverviewLayout.fallbackUsed();
            }
        }
        return geometryChanged || techGeometryChanged;
    }

    /**
     * Applies a requested client layout and recovers with the built-in Balanced
     * policy when only that visual policy is rejected. If both attempts fail,
     * the original failure is rethrown with the fallback failure suppressed;
     * {@link #update(ResearchTreePublication, ResearchTreeLayoutPolicy)} keeps
     * the previously committed cache intact in either case.
     */
    UpdateOutcome updateWithBalancedFallback(
            ResearchTreePublication nextPublication,
            ResearchTreeLayoutPolicy requestedLayoutPolicy) {
        if (nextPublication == null || requestedLayoutPolicy == null) {
            throw new IllegalArgumentException(
                    "Research Tree fallback update inputs cannot be null");
        }
        try {
            return new UpdateOutcome(
                    update(nextPublication, requestedLayoutPolicy),
                    Optional.empty());
        } catch (RuntimeException requestedFailure) {
            if (DEFAULT_LAYOUT_POLICY.equals(requestedLayoutPolicy)) {
                throw requestedFailure;
            }
            try {
                return new UpdateOutcome(
                        update(nextPublication, DEFAULT_LAYOUT_POLICY),
                        Optional.of(requestedFailure));
            } catch (RuntimeException fallbackFailure) {
                requestedFailure.addSuppressed(fallbackFailure);
                throw requestedFailure;
            }
        }
    }

    public ResearchTreePublication publication() {
        return publication;
    }

    public ResearchTechTreeProjectionCatalog techTreeProjections() {
        return techTreeProjections;
    }

    public ResearchTechTreeLayoutCatalog techTreeLayouts() {
        return techTreeLayouts;
    }

    /**
     * Returns cached default geometry for ordinary compact/fullscreen widths,
     * and a capacity-keyed responsive layout only for an unusually narrow
     * embedding. Responsive wrapping remains client presentation state.
     */
    public Optional<ResearchTechTreeLayout> techTreeLayout(
            Domain domain,
            int viewportWidth) {
        if (domain == null || viewportWidth < 1) {
            throw new IllegalArgumentException(
                    "Research Tech Tree viewport lookup is invalid");
        }
        Optional<ResearchTechTreeProjection> projection =
                techTreeProjections.projection(domain);
        if (projection.isEmpty()) {
            return Optional.empty();
        }
        int effectiveCapacity = techLayoutPolicy.effectiveNodesPerRow(
                projection.orElseThrow().maxNodesPerLayer(), viewportWidth);
        int defaultCapacity = techLayoutPolicy.effectiveNodesPerRow(
                projection.orElseThrow().maxNodesPerLayer(), Integer.MAX_VALUE);
        if (effectiveCapacity == defaultCapacity) {
            return techTreeLayouts.layout(domain);
        }
        TechViewportKey key = new TechViewportKey(domain, effectiveCapacity);
        return Optional.of(responsiveTechLayouts.computeIfAbsent(
                key,
                ignored -> ResearchTechTreeLayoutEngine.layout(
                        projection.orElseThrow(), techLayoutPolicy, viewportWidth)));
    }

    public ResearchTreeUnlockIndex unlocks() {
        return unlocks;
    }

    /** True only when the current publication required its bounded emergency overview. */
    public boolean overviewFallbackActive() {
        return overviewFallbackActive;
    }

    public ResearchTreeProjection projection(
            ResearchTreePresentationContract.BrowseView view,
            ResourceLocation groupId) {
        ProjectionKey key = key(view, groupId);
        return projections.computeIfAbsent(key, this::build);
    }

    public void clear() {
        publication = ResearchTreePublication.EMPTY;
        legacyPublication = ResearchTreePublication.EMPTY;
        overview = ResearchTreeOverviewBuilder.build(ResearchTreePublication.EMPTY);
        groupSkeletons = ResearchTreeGroupSkeletonCatalog.EMPTY;
        techTreeProjections = ResearchTechTreeProjectionCatalog.EMPTY;
        techTreeLayouts = ResearchTechTreeLayoutCatalog.EMPTY;
        layoutPolicy = DEFAULT_LAYOUT_POLICY;
        techLayoutPolicy = ResearchTechTreeLayoutPolicy.fromShared(DEFAULT_LAYOUT_POLICY);
        overviewFallbackActive = false;
        projections.clear();
        layouts.clear();
        responsiveTechLayouts.clear();
    }

    int cachedProjectionCount() {
        return projections.size();
    }

    int cachedLayoutCount() {
        return layouts.size();
    }

    ResearchTreeGroupSkeletonCatalog groupSkeletons() {
        return groupSkeletons;
    }

    /**
     * Revalidates a portal against the active authoritative publication before
     * it is allowed to change branch or camera state.
     */
    public boolean isPublishedCrossGroupLink(ResearchTreeProjection.CrossGroupLink link) {
        if (link == null
                || legacyPublication.graph().node(link.localNodeId()).isEmpty()
                || legacyPublication.graph().node(link.remoteNodeId()).isEmpty()) {
            return false;
        }
        Optional<ResearchTreePresentation.Membership> localMembership =
                publication.presentation().membership(link.localNodeId());
        Optional<ResearchTreePresentation.Membership> remoteMembership =
                publication.presentation().membership(link.remoteNodeId());
        if (localMembership.isEmpty() || remoteMembership.isEmpty()
                || localMembership.orElseThrow().groupId()
                        .equals(remoteMembership.orElseThrow().groupId())
                || !remoteMembership.orElseThrow().groupId().equals(link.remoteGroupId())) {
            return false;
        }
        ResourceLocation prerequisiteId = link.direction() == ResearchTreeProjection.Direction.UNLOCK
                ? link.localNodeId() : link.remoteNodeId();
        ResourceLocation dependentId = link.direction() == ResearchTreeProjection.Direction.UNLOCK
                ? link.remoteNodeId() : link.localNodeId();
        return groupSkeletons.containsCrossGroupEdge(
                new ResearchTreeGraph.Edge(prerequisiteId, dependentId));
    }

    private ProjectionKey key(
            ResearchTreePresentationContract.BrowseView view,
            ResourceLocation groupId) {
        if (view == null) {
            throw new IllegalArgumentException("Research Tree projection view cannot be null");
        }
        if (view == ResearchTreePresentationContract.BrowseView.TECH_TREE) {
            throw new IllegalArgumentException(
                    "Tech Tree projections must use the typed domain catalog");
        }
        if (view == ResearchTreePresentationContract.BrowseView.ALL_WEAPONS) {
            return ALL_WEAPONS;
        }
        if (legacyPublication.graph().nodes().isEmpty() && groupId == null) {
            return new ProjectionKey(view, Optional.empty());
        }
        if (groupId == null || publication.presentation().group(groupId).isEmpty()) {
            throw new IllegalArgumentException("unknown Research Tree projection group");
        }
        return new ProjectionKey(view, Optional.of(groupId));
    }

    private ResearchTreeProjection build(ProjectionKey key) {
        if (key.view() == ResearchTreePresentationContract.BrowseView.ALL_WEAPONS) {
            // Empty client state and a screen rebuild are both valid entry
            // points. Lazily recover the deterministic overview layout if a
            // prior publication has not seeded this cache yet.
            ResearchTreeLayout layout = layouts.get(key);
            if (layout == null) {
                OverviewLayoutBuild built = buildOverviewLayout(
                        overview, groupSkeletons, layoutPolicy);
                layout = built.layout();
                overviewFallbackActive = built.fallbackUsed();
                layouts.put(key, layout);
            }
            return new ResearchTreeProjection(
                    key.view(),
                    Optional.empty(),
                    overview.publication().graph(),
                    layout,
                    overview.boundaryLinks());
        }
        if (key.groupId().isEmpty()) {
            return new ResearchTreeProjection(
                    key.view(),
                    Optional.empty(),
                    ResearchTreeGraph.EMPTY,
                    ResearchTreeLayout.EMPTY,
                    List.of());
        }

        ResourceLocation groupId = key.groupId().orElseThrow();
        ResearchTreeGroupSkeleton skeleton = groupSkeletons.group(groupId)
                .orElseThrow();

        List<ResearchTreeProjection.CrossGroupLink> crossGroupLinks = new ArrayList<>();
        for (ResearchTreeGroupSkeletonCatalog.CrossGroupEdge edge
                : groupSkeletons.incidentEdges(groupId)) {
            if (edge.prerequisiteGroupId().equals(groupId)) {
                crossGroupLinks.add(new ResearchTreeProjection.CrossGroupLink(
                        edge.prerequisiteId(),
                        edge.dependentId(),
                        edge.dependentGroupId(),
                        ResearchTreeProjection.Direction.UNLOCK));
            } else if (edge.dependentGroupId().equals(groupId)) {
                crossGroupLinks.add(new ResearchTreeProjection.CrossGroupLink(
                        edge.dependentId(),
                        edge.prerequisiteId(),
                        edge.prerequisiteGroupId(),
                        ResearchTreeProjection.Direction.REQUIREMENT));
            }
        }

        ResearchTreeGraph graph = legacyPublication.graph().orderedInducedSubgraph(
                skeleton.nodes().stream()
                        .map(ResearchTreeGroupSkeleton.PositionedNode::nodeId)
                        .toList());
        int minimumPortalWidth = Math.addExact(
                ResearchTreeCanvas.maximumPortalBankWidth(crossGroupLinks),
                Math.multiplyExact(2, ResearchTreeLayout.PORTAL_BANK_SIDE_PADDING));
        ResearchTreeLayout layout = layouts.computeIfAbsent(
                key,
                ignored -> branchLayoutFactory.compose(
                        skeleton, minimumPortalWidth, layoutPolicy));
        return new ResearchTreeProjection(
                key.view(),
                key.groupId(),
                graph,
                layout,
                crossGroupLinks);
    }

    private static ResearchTreeLayout ensurePortalWidth(
            ResearchTreeLayout layout,
            List<ResearchTreeProjection.CrossGroupLink> links) {
        if (layout.nodes().isEmpty() || links.isEmpty()) {
            return layout;
        }
        int minimumWidth = Math.addExact(
                ResearchTreeCanvas.maximumPortalBankWidth(links),
                Math.multiplyExact(2, ResearchTreeLayout.PORTAL_BANK_SIDE_PADDING));
        if (layout.width() >= minimumWidth) {
            return layout;
        }
        return new ResearchTreeLayout(
                minimumWidth,
                layout.height(),
                layout.tierCount(),
                layout.nodes(),
                layout.hiddenAnchors(),
                layout.categoryLanes(),
                layout.groupRegions(),
                layout.edgeRouteHints());
    }

    private OverviewLayoutBuild buildOverviewLayout(
            ResearchTreeOverviewBuilder.Result overview,
            ResearchTreeGroupSkeletonCatalog skeletons,
            ResearchTreeLayoutPolicy policy) {
        try {
            return new OverviewLayoutBuild(
                    ensurePortalWidth(
                            overviewLayoutFactory.compose(
                                    overview.publication(), skeletons, policy),
                            overview.boundaryLinks()),
                    false);
        } catch (RuntimeException primaryFailure) {
            try {
                ResearchTreeLayout fallback = addPortalEnvelope(
                        fallbackLayoutFactory.compose(overview.publication(), policy),
                        overview.boundaryLinks(),
                        policy);
                TaCZWeaponBlueprints.LOGGER.warn(
                        "Research Tree overview composition failed; using the bounded "
                                + "same-publication fallback layout",
                        primaryFailure);
                return new OverviewLayoutBuild(fallback, true);
            } catch (RuntimeException fallbackFailure) {
                primaryFailure.addSuppressed(fallbackFailure);
                throw primaryFailure;
            }
        }
    }

    private static ResearchTreeLayout addPortalEnvelope(
            ResearchTreeLayout layout,
            List<ResearchTreeProjection.CrossGroupLink> links,
            ResearchTreeLayoutPolicy policy) {
        if (layout.nodes().isEmpty() || links.isEmpty()) {
            return layout;
        }
        int minimumWidth = Math.addExact(
                ResearchTreeCanvas.maximumPortalBankWidth(links),
                Math.multiplyExact(2, ResearchTreeLayout.PORTAL_BANK_SIDE_PADDING));
        int width = Math.max(layout.width(), minimumWidth);
        int offsetX = (width - layout.width()) / 2;
        int offsetY = policy.portalClearance();
        int height = Math.addExact(layout.height(), Math.multiplyExact(2, offsetY));
        List<ResearchTreeLayout.PositionedNode> nodes = layout.nodes().stream()
                .map(node -> new ResearchTreeLayout.PositionedNode(
                        node.nodeOrdinal(),
                        node.blueprintId(),
                        node.component(),
                        node.tier(),
                        node.orderInTier(),
                        Math.addExact(node.x(), offsetX),
                        Math.addExact(node.y(), offsetY)))
                .toList();
        List<ResearchTreeLayout.HiddenAnchor> hiddenAnchors = layout.hiddenAnchors().stream()
                .map(anchor -> new ResearchTreeLayout.HiddenAnchor(
                        anchor.dependentId(),
                        anchor.hiddenCount(),
                        Math.addExact(anchor.x(), offsetX),
                        Math.addExact(anchor.y(), offsetY)))
                .toList();
        List<ResearchTreeLayout.CategoryLane> categoryLanes = layout.categoryLanes().stream()
                .map(lane -> new ResearchTreeLayout.CategoryLane(
                        lane.key(),
                        Math.addExact(lane.x(), offsetX),
                        lane.width()))
                .toList();
        List<ResearchTreeLayout.GroupRegion> groupRegions = layout.groupRegions().stream()
                .map(region -> new ResearchTreeLayout.GroupRegion(
                        region.groupId(),
                        Math.addExact(region.x(), offsetX),
                        Math.addExact(region.y(), offsetY),
                        region.width(),
                        region.height()))
                .toList();
        List<ResearchTreeLayout.EdgeRouteHint> routeHints = layout.edgeRouteHints().stream()
                .map(hint -> new ResearchTreeLayout.EdgeRouteHint(
                        hint.prerequisiteId(),
                        hint.dependentId(),
                        hint.waypoints().stream()
                                .map(waypoint -> new ResearchTreeLayout.RouteWaypoint(
                                        waypoint.rank(),
                                        Math.addExact(waypoint.x(), offsetX),
                                        Math.addExact(waypoint.y(), offsetY)))
                                .toList()))
                .toList();
        return new ResearchTreeLayout(
                width,
                height,
                layout.tierCount(),
                nodes,
                hiddenAnchors,
                categoryLanes,
                groupRegions,
                routeHints);
    }

    @FunctionalInterface
    interface OverviewLayoutFactory {
        ResearchTreeLayout compose(
                ResearchTreePublication publication,
                ResearchTreeGroupSkeletonCatalog skeletons,
                ResearchTreeLayoutPolicy policy);
    }

    @FunctionalInterface
    interface FallbackLayoutFactory {
        ResearchTreeLayout compose(
                ResearchTreePublication publication,
                ResearchTreeLayoutPolicy policy);
    }

    @FunctionalInterface
    interface BranchLayoutFactory {
        ResearchTreeLayout compose(
                ResearchTreeGroupSkeleton skeleton,
                int minimumPortalWidth,
                ResearchTreeLayoutPolicy policy);
    }

    @FunctionalInterface
    interface TechLayoutFactory {
        ResearchTechTreeLayoutCatalog layout(
                ResearchTechTreeProjectionCatalog projections,
                ResearchTechTreeLayoutPolicy policy);
    }

    record UpdateOutcome(
            boolean geometryChanged,
            Optional<RuntimeException> recoveredLayoutFailure) {
        UpdateOutcome {
            if (recoveredLayoutFailure == null) {
                throw new IllegalArgumentException(
                        "Research Tree fallback outcome cannot contain null state");
            }
        }

        boolean usedBalancedFallback() {
            return recoveredLayoutFailure.isPresent();
        }
    }

    private record OverviewLayoutBuild(ResearchTreeLayout layout, boolean fallbackUsed) {
        private OverviewLayoutBuild {
            if (layout == null) {
                throw new IllegalArgumentException("Research Tree overview build cannot be null");
            }
        }
    }

    private record ProjectionKey(
            ResearchTreePresentationContract.BrowseView view,
            Optional<ResourceLocation> groupId) {
        private ProjectionKey {
            groupId = groupId == null ? Optional.empty() : groupId;
        }
    }

    private record TechViewportKey(
            Domain domain,
            int effectiveCapacity) {
        private TechViewportKey {
            if (domain == null || effectiveCapacity < 1
                    || effectiveCapacity
                            > ResearchTechTreeLayoutPolicy.MAXIMUM_NODES_PER_ROW) {
                throw new IllegalArgumentException(
                        "invalid Research Tech Tree viewport cache key");
            }
        }
    }
}
