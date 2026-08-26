package com.gamergaming.taczweaponblueprints.client;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;

import net.minecraft.resources.ResourceLocation;

/**
 * One client-only view of the authoritative public graph. Branch projections
 * retain cross-group edges as disclosure-safe links for the later portal UI.
 */
public record ResearchTreeProjection(
        ResearchTreePresentationContract.BrowseView view,
        Optional<ResourceLocation> groupId,
        ResearchTreeGraph graph,
        ResearchTreeLayout layout,
        List<CrossGroupLink> crossGroupLinks) {
    public ResearchTreeProjection {
        if (view == null || graph == null || layout == null || crossGroupLinks == null
                || crossGroupLinks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Research Tree projection fields cannot be null");
        }
        groupId = groupId == null ? Optional.empty() : groupId;
        crossGroupLinks = List.copyOf(crossGroupLinks);
        if (new LinkedHashSet<>(crossGroupLinks).size() != crossGroupLinks.size()) {
            throw new IllegalArgumentException("Research Tree projection contains duplicate links");
        }
        if (view == ResearchTreePresentationContract.BrowseView.ALL_WEAPONS) {
            if (groupId.isPresent() || !crossGroupLinks.isEmpty()
                    || !layout.groupRegions().isEmpty()) {
                throw new IllegalArgumentException(
                        "All Weapons must use the unpartitioned global layout");
            }
        } else if (groupId.isEmpty() && !graph.nodes().isEmpty()) {
            throw new IllegalArgumentException("non-empty Branches projection requires a group");
        } else if (groupId.isPresent()
                && (layout.groupRegions().size() != 1
                || !layout.groupRegions().get(0).groupId().equals(groupId.orElseThrow()))) {
            throw new IllegalArgumentException(
                    "Branches layout must contain exactly its selected group region");
        }
        validateLayout(graph, layout);
        for (CrossGroupLink link : crossGroupLinks) {
            if (graph.node(link.localNodeId()).isEmpty()
                    || graph.node(link.remoteNodeId()).isPresent()
                    || groupId.filter(link.remoteGroupId()::equals).isPresent()) {
                throw new IllegalArgumentException(
                        "Research Tree cross-group link does not cross its projection boundary");
            }
        }
    }

    private static void validateLayout(ResearchTreeGraph graph, ResearchTreeLayout layout) {
        if (graph.nodes().size() != layout.nodes().size()) {
            throw new IllegalArgumentException("Research Tree projection graph and layout differ in size");
        }
        for (int ordinal = 0; ordinal < graph.nodes().size(); ordinal++) {
            if (!graph.nodes().get(ordinal).blueprintId()
                    .equals(layout.nodes().get(ordinal).blueprintId())) {
                throw new IllegalArgumentException(
                        "Research Tree projection graph and layout do not match");
            }
        }
    }

    public record CrossGroupLink(
            ResourceLocation localNodeId,
            ResourceLocation remoteNodeId,
            ResourceLocation remoteGroupId,
            Direction direction) {
        public CrossGroupLink {
            if (localNodeId == null || remoteNodeId == null || remoteGroupId == null
                    || direction == null || localNodeId.equals(remoteNodeId)) {
                throw new IllegalArgumentException("invalid Research Tree cross-group link");
            }
        }
    }

    public enum Direction {
        REQUIREMENT,
        UNLOCK
    }
}
