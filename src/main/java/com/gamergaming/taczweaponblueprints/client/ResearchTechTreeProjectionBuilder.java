package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;

import net.minecraft.resources.ResourceLocation;

/** Derives one immutable public graph projection per published Tech Tree domain. */
public final class ResearchTechTreeProjectionBuilder {
    private ResearchTechTreeProjectionBuilder() {
    }

    public static ResearchTechTreeProjectionCatalog build(ResearchTreePublication publication) {
        if (publication == null) {
            throw new IllegalArgumentException("Research Tech Tree projection publication cannot be null");
        }
        ResearchTechTreePresentation presentation = publication.techTree();
        if (!presentation.available()) {
            return ResearchTechTreeProjectionCatalog.EMPTY;
        }

        Map<ResourceLocation, Domain> domainsByNode = new LinkedHashMap<>();
        for (ResearchTechTreePresentation.DomainView domain : presentation.domains()) {
            domain.lanes().stream()
                    .flatMap(lane -> lane.members().stream())
                    .forEach(member -> {
                        if (domainsByNode.put(member.nodeId(), domain.domain()) != null) {
                            throw new IllegalArgumentException(
                                    "Research Tech Tree projection member belongs to multiple domains");
                        }
                    });
        }

        List<ResearchTechTreeProjection> projections = new ArrayList<>();
        for (ResearchTechTreePresentation.DomainView domain : presentation.domains()) {
            projections.add(buildDomain(
                    publication.graph(),
                    domain,
                    domainsByNode,
                    presentation.bands(),
                    presentation.maxNodesPerLayer()));
        }
        ResearchTechTreeProjectionCatalog catalog = new ResearchTechTreeProjectionCatalog(
                presentation,
                projections);
        validateBoundaryLinks(publication.graph(), catalog);
        return catalog;
    }

    private static ResearchTechTreeProjection buildDomain(
            ResearchTreeGraph sourceGraph,
            ResearchTechTreePresentation.DomainView domain,
            Map<ResourceLocation, Domain> domainsByNode,
            List<ResearchTechTreePresentation.BandLabel> bands,
            int maxNodesPerLayer) {
        LinkedHashMap<ResourceLocation, ResearchTechTreeProjection.Placement> placements =
                new LinkedHashMap<>();
        for (ResearchTechTreePresentation.LaneView lane : domain.lanes()) {
            for (ResearchTechTreePresentation.Member member : lane.members()) {
                placements.put(member.nodeId(), new ResearchTechTreeProjection.Placement(
                        member.nodeId(),
                        lane.id(),
                        member.rank(),
                        lane.order(),
                        member.siblingOrder(),
                        member.bandId(),
                        member.origin(),
                        member.rating(),
                        member.automaticBranch()));
            }
        }
        Set<ResourceLocation> localNodeIds = placements.keySet();
        List<ResearchTechTreeProjection.BoundaryLink> boundaryLinks = new ArrayList<>();
        for (ResearchTreeGraph.Edge edge : sourceGraph.edges()) {
            Domain prerequisiteDomain = domainsByNode.get(edge.prerequisiteId());
            Domain dependentDomain = domainsByNode.get(edge.dependentId());
            if (prerequisiteDomain == domain.domain() && dependentDomain != null
                    && dependentDomain != domain.domain()) {
                boundaryLinks.add(new ResearchTechTreeProjection.BoundaryLink(
                        edge.prerequisiteId(),
                        edge.dependentId(),
                        dependentDomain,
                        ResearchTechTreeProjection.Direction.UNLOCK));
            } else if (dependentDomain == domain.domain() && prerequisiteDomain != null
                    && prerequisiteDomain != domain.domain()) {
                boundaryLinks.add(new ResearchTechTreeProjection.BoundaryLink(
                        edge.dependentId(),
                        edge.prerequisiteId(),
                        prerequisiteDomain,
                        ResearchTechTreeProjection.Direction.REQUIREMENT));
            }
        }

        ResearchTreeGraph graph = sourceGraph.orderedInducedSubgraph(
                List.copyOf(placements.keySet()));
        return new ResearchTechTreeProjection(
                domain.domain(),
                domain,
                graph,
                placements,
                boundaryLinks,
                bands,
                maxNodesPerLayer);
    }

    private static void validateBoundaryLinks(
            ResearchTreeGraph sourceGraph,
            ResearchTechTreeProjectionCatalog catalog) {
        Set<ResearchTreeGraph.Edge> publishedEdges = new LinkedHashSet<>(sourceGraph.edges());
        for (ResearchTechTreeProjection projection : catalog.projections()) {
            for (ResearchTechTreeProjection.BoundaryLink link : projection.boundaryLinks()) {
                if (catalog.domainOf(link.remoteNodeId()).filter(link.remoteDomain()::equals).isEmpty()) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree boundary references an unpublished remote domain");
                }
                ResearchTreeGraph.Edge expected = link.direction()
                        == ResearchTechTreeProjection.Direction.UNLOCK
                                ? new ResearchTreeGraph.Edge(
                                        link.localNodeId(), link.remoteNodeId())
                                : new ResearchTreeGraph.Edge(
                                        link.remoteNodeId(), link.localNodeId());
                if (!publishedEdges.contains(expected)) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree boundary is not an authoritative prerequisite");
                }
            }
        }
    }
}
