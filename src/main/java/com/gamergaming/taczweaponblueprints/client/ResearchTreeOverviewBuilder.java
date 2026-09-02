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

        List<ResearchTreeProjection.CrossGroupLink> boundaryLinks = new ArrayList<>();
        for (ResearchTreeGraph.Edge edge : publication.graph().edges()) {
            boolean prerequisiteIncluded = includedNodeIds.contains(edge.prerequisiteId());
            boolean dependentIncluded = includedNodeIds.contains(edge.dependentId());
            if (prerequisiteIncluded != dependentIncluded) {
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

        ResearchTreeGraph overviewGraph = publication.graph().orderedInducedSubgraph(
                publication.graph().nodes().stream()
                        .map(ResearchTreeGraph.Node::blueprintId)
                        .filter(includedNodeIds::contains)
                        .toList());
        ResearchTreePublication overview = new ResearchTreePublication(
                overviewGraph,
                new ResearchTreePresentation(includedGroups));
        return new Result(
                overview,
                boundaryLinks,
                includedNodeIds.size() == publication.graph().nodes().size());
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
