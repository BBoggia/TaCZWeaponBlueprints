package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;

import net.minecraft.resources.ResourceLocation;

/** Derives the curated, disclosure-safe overview without discarding the full publication. */
public final class ResearchTreeOverviewBuilder {
    private ResearchTreeOverviewBuilder() {
    }

    public static Result build(ResearchTreePublication publication) {
        if (publication == null) {
            throw new IllegalArgumentException("Research Tree overview publication cannot be null");
        }
        publication = publication.legacyView();
        Set<ResourceLocation> includedNodeIds = new LinkedHashSet<>();
        List<ResearchTreePresentation.Group> includedGroups = new ArrayList<>();
        for (ResearchTreePresentation.Group group : publication.presentation().groups()) {
            if (!group.includedInOverview()) {
                continue;
            }
            group.members().forEach(member -> includedNodeIds.add(member.nodeId()));
            includedGroups.add(new ResearchTreePresentation.Group(
                    group.id(),
                    group.title(),
                    group.translationKey(),
                    group.iconNodeId(),
                    includedGroups.size(),
                    group.kind(),
                    true,
                    group.members()));
        }

        if (includedNodeIds.isEmpty()) {
            return new Result(
                    ResearchTreePublication.EMPTY,
                    List.of(),
                    publication.graph().nodes().isEmpty());
        }
        if (includedNodeIds.size() == publication.graph().nodes().size()) {
            return new Result(publication, List.of(), true);
        }

        List<ResearchTreeGraph.Edge> internalEdges = new ArrayList<>();
        List<ResearchTreeProjection.CrossGroupLink> boundaryLinks = new ArrayList<>();
        java.util.Map<ResourceLocation, Integer> internalPrerequisiteCounts =
                new java.util.LinkedHashMap<>();
        includedNodeIds.forEach(id -> internalPrerequisiteCounts.put(id, 0));
        for (ResearchTreeGraph.Edge edge : publication.graph().edges()) {
            boolean prerequisiteIncluded = includedNodeIds.contains(edge.prerequisiteId());
            boolean dependentIncluded = includedNodeIds.contains(edge.dependentId());
            if (prerequisiteIncluded && dependentIncluded) {
                internalEdges.add(edge);
                internalPrerequisiteCounts.compute(
                        edge.dependentId(),
                        (ignored, count) -> count == null ? 1 : count + 1);
            } else if (prerequisiteIncluded != dependentIncluded) {
                ResourceLocation localId = prerequisiteIncluded
                        ? edge.prerequisiteId() : edge.dependentId();
                ResourceLocation remoteId = prerequisiteIncluded
                        ? edge.dependentId() : edge.prerequisiteId();
                ResourceLocation remoteGroupId = publication.presentation()
                        .membership(remoteId)
                        .orElseThrow()
                        .groupId();
                boundaryLinks.add(new ResearchTreeProjection.CrossGroupLink(
                        localId,
                        remoteId,
                        remoteGroupId,
                        prerequisiteIncluded
                                ? ResearchTreeProjection.Direction.UNLOCK
                                : ResearchTreeProjection.Direction.REQUIREMENT));
            }
        }

        List<ResearchTreeGraph.Node> nodes = new ArrayList<>(includedNodeIds.size());
        for (ResearchTreeGraph.Node node : publication.graph().nodes()) {
            if (includedNodeIds.contains(node.blueprintId())) {
                nodes.add(copyNode(
                        node,
                        nodes.size(),
                        internalPrerequisiteCounts.getOrDefault(node.blueprintId(), 0)));
            }
        }
        ResearchTreePublication overview = new ResearchTreePublication(
                new ResearchTreeGraph(nodes, internalEdges),
                new ResearchTreePresentation(includedGroups));
        return new Result(
                overview,
                boundaryLinks,
                includedNodeIds.size() == publication.graph().nodes().size());
    }

    private static ResearchTreeGraph.Node copyNode(
            ResearchTreeGraph.Node node,
            int ordinal,
            int prerequisiteCount) {
        return new ResearchTreeGraph.Node(
                ordinal,
                node.sourceOrdinal(),
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

    public record Result(
            ResearchTreePublication publication,
            List<ResearchTreeProjection.CrossGroupLink> boundaryLinks,
            boolean includesCompleteGraph) {
        public Result {
            if (publication == null || boundaryLinks == null
                    || boundaryLinks.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Research Tree overview result cannot be null");
            }
            boundaryLinks = List.copyOf(boundaryLinks);
            Set<ResearchTreeProjection.CrossGroupLink> unique =
                    new LinkedHashSet<>(boundaryLinks);
            if (unique.size() != boundaryLinks.size()
                    || includesCompleteGraph && !boundaryLinks.isEmpty()) {
                throw new IllegalArgumentException("Research Tree overview has invalid boundary links");
            }
            for (ResearchTreeProjection.CrossGroupLink link : boundaryLinks) {
                if (publication.graph().node(link.localNodeId()).isEmpty()
                        || publication.graph().node(link.remoteNodeId()).isPresent()) {
                    throw new IllegalArgumentException("Research Tree overview link does not cross its boundary");
                }
            }
        }
    }
}
