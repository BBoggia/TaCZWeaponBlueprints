package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGroupedLayoutEngine;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;

import net.minecraft.resources.ResourceLocation;

/** Lazily builds Branches projections while retaining layouts across state-only publications. */
public final class ResearchTreeProjectionCache {
    private static final ProjectionKey ALL_WEAPONS = new ProjectionKey(
            ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
            Optional.empty());

    private ResearchTreePublication publication = ResearchTreePublication.EMPTY;
    private final Map<ProjectionKey, ResearchTreeProjection> projections = new LinkedHashMap<>();
    private final Map<ProjectionKey, ResearchTreeLayout> layouts = new LinkedHashMap<>();

    /** Returns true only when projection topology and cached layouts were invalidated. */
    public boolean update(
            ResearchTreePublication nextPublication,
            ResearchTreeLayout allWeaponsLayout) {
        if (nextPublication == null || allWeaponsLayout == null) {
            throw new IllegalArgumentException("Research Tree projection publication cannot be null");
        }
        // The projection record performs the same ordinal/ID pairing check used
        // by every lazily built branch.
        new ResearchTreeProjection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                Optional.empty(),
                nextPublication.graph(),
                allWeaponsLayout,
                List.of());

        boolean topologyChanged = !publication.hasSamePresentationTopology(nextPublication);
        publication = nextPublication;
        projections.clear();
        if (topologyChanged) {
            layouts.clear();
            layouts.put(ALL_WEAPONS, allWeaponsLayout);
        } else {
            // ClientResearchState owns the canonical global layout. Keep the
            // existing instance for a state-only publication, but seed a
            // freshly cleared cache from the supplied publication layout.
            layouts.putIfAbsent(ALL_WEAPONS, allWeaponsLayout);
        }
        return topologyChanged;
    }

    public ResearchTreePublication publication() {
        return publication;
    }

    public ResearchTreeProjection projection(
            ResearchTreePresentationContract.BrowseView view,
            ResourceLocation groupId) {
        ProjectionKey key = key(view, groupId);
        return projections.computeIfAbsent(key, this::build);
    }

    public void clear() {
        publication = ResearchTreePublication.EMPTY;
        projections.clear();
        layouts.clear();
    }

    int cachedProjectionCount() {
        return projections.size();
    }

    int cachedLayoutCount() {
        return layouts.size();
    }

    /**
     * Revalidates a portal against the active authoritative publication before
     * it is allowed to change branch or camera state.
     */
    public boolean isPublishedCrossGroupLink(ResearchTreeProjection.CrossGroupLink link) {
        if (link == null
                || publication.graph().node(link.localNodeId()).isEmpty()
                || publication.graph().node(link.remoteNodeId()).isEmpty()) {
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
        return publication.graph().edges().contains(
                new ResearchTreeGraph.Edge(prerequisiteId, dependentId));
    }

    private ProjectionKey key(
            ResearchTreePresentationContract.BrowseView view,
            ResourceLocation groupId) {
        if (view == null) {
            throw new IllegalArgumentException("Research Tree projection view cannot be null");
        }
        if (view == ResearchTreePresentationContract.BrowseView.ALL_WEAPONS) {
            return ALL_WEAPONS;
        }
        if (publication.graph().nodes().isEmpty() && groupId == null) {
            return new ProjectionKey(view, Optional.empty());
        }
        if (groupId == null || publication.presentation().group(groupId).isEmpty()) {
            throw new IllegalArgumentException("unknown Research Tree projection group");
        }
        return new ProjectionKey(view, Optional.of(groupId));
    }

    private ResearchTreeProjection build(ProjectionKey key) {
        if (key.view() == ResearchTreePresentationContract.BrowseView.ALL_WEAPONS) {
            ResearchTreeLayout layout = layouts.get(key);
            if (layout == null) {
                throw new IllegalStateException(
                        "All Weapons layout was not initialized for the active publication");
            }
            return new ResearchTreeProjection(
                    key.view(),
                    Optional.empty(),
                    publication.graph(),
                    layout,
                    List.of());
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
        ResearchTreePresentation.Group group = publication.presentation()
                .group(groupId)
                .orElseThrow();
        Set<ResourceLocation> memberIds = new LinkedHashSet<>();
        group.members().forEach(member -> memberIds.add(member.nodeId()));

        Map<ResourceLocation, Integer> internalPrerequisiteCounts = new LinkedHashMap<>();
        memberIds.forEach(id -> internalPrerequisiteCounts.put(id, 0));
        List<ResearchTreeGraph.Edge> internalEdges = new ArrayList<>();
        List<ResearchTreeProjection.CrossGroupLink> crossGroupLinks = new ArrayList<>();
        for (ResearchTreeGraph.Edge edge : publication.graph().edges()) {
            boolean prerequisiteLocal = memberIds.contains(edge.prerequisiteId());
            boolean dependentLocal = memberIds.contains(edge.dependentId());
            if (prerequisiteLocal && dependentLocal) {
                internalEdges.add(edge);
                internalPrerequisiteCounts.compute(
                        edge.dependentId(),
                        (ignored, count) -> count == null ? 1 : count + 1);
            } else if (prerequisiteLocal != dependentLocal) {
                ResourceLocation localId = prerequisiteLocal
                        ? edge.prerequisiteId() : edge.dependentId();
                ResourceLocation remoteId = prerequisiteLocal
                        ? edge.dependentId() : edge.prerequisiteId();
                ResourceLocation remoteGroupId = publication.presentation()
                        .membership(remoteId)
                        .orElseThrow()
                        .groupId();
                crossGroupLinks.add(new ResearchTreeProjection.CrossGroupLink(
                        localId,
                        remoteId,
                        remoteGroupId,
                        prerequisiteLocal
                                ? ResearchTreeProjection.Direction.UNLOCK
                                : ResearchTreeProjection.Direction.REQUIREMENT));
            }
        }

        List<ResearchTreeGraph.Node> nodes = new ArrayList<>(memberIds.size());
        for (ResearchTreeGraph.Node node : publication.graph().nodes()) {
            if (memberIds.contains(node.blueprintId())) {
                nodes.add(copyNode(
                        node,
                        nodes.size(),
                        internalPrerequisiteCounts.getOrDefault(node.blueprintId(), 0)));
            }
        }
        ResearchTreeGraph graph = new ResearchTreeGraph(nodes, internalEdges);
        Map<PortalBank, Integer> portalCounts = new LinkedHashMap<>();
        for (ResearchTreeProjection.CrossGroupLink link : crossGroupLinks) {
            portalCounts.merge(
                    new PortalBank(link.localNodeId(), link.direction()), 1, Integer::sum);
        }
        int minimumPortalWidth = portalCounts.values().stream()
                .mapToInt(ResearchTreeCanvas::portalBankWidth)
                .max()
                .orElse(0);
        ResearchTreeLayout layout = layouts.computeIfAbsent(
                key,
                ignored -> ResearchTreeGroupedLayoutEngine.branch(
                        graph, group, minimumPortalWidth));
        return new ResearchTreeProjection(
                key.view(),
                key.groupId(),
                graph,
                layout,
                crossGroupLinks);
    }

    private static ResearchTreeGraph.Node copyNode(
            ResearchTreeGraph.Node node,
            int ordinal,
            int prerequisiteCount) {
        return new ResearchTreeGraph.Node(
                ordinal,
                node.blueprintId(),
                node.nameKey(),
                node.itemType(),
                node.displaySlotId(),
                node.visibility(),
                node.learned(),
                node.discovered(),
                node.policyEligible(),
                node.pointCost(),
                node.ingredientTypeCount(),
                prerequisiteCount,
                0,
                node.availability());
    }

    private record ProjectionKey(
            ResearchTreePresentationContract.BrowseView view,
            Optional<ResourceLocation> groupId) {
        private ProjectionKey {
            groupId = groupId == null ? Optional.empty() : groupId;
        }
    }

    private record PortalBank(
            ResourceLocation localNodeId,
            ResearchTreeProjection.Direction direction) {
    }
}
