package com.gamergaming.taczweaponblueprints.client;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;

/** Immutable, projection-validated geometry for every published Tech domain. */
public final class ResearchTechTreeLayoutCatalog {
    public static final ResearchTechTreeLayoutCatalog EMPTY =
            new ResearchTechTreeLayoutCatalog(
                    ResearchTechTreeProjectionCatalog.EMPTY,
                    List.of());

    private final List<ResearchTechTreeLayout> layouts;
    private final Map<Domain, ResearchTechTreeLayout> byDomain;

    ResearchTechTreeLayoutCatalog(
            ResearchTechTreeProjectionCatalog projections,
            List<ResearchTechTreeLayout> layouts) {
        if (projections == null || layouts == null
                || layouts.stream().anyMatch(java.util.Objects::isNull)
                || projections.projections().size() != layouts.size()) {
            throw new IllegalArgumentException("invalid Research Tech Tree layout catalog");
        }
        this.layouts = List.copyOf(layouts);
        EnumMap<Domain, ResearchTechTreeLayout> domains = new EnumMap<>(Domain.class);
        for (int index = 0; index < this.layouts.size(); index++) {
            ResearchTechTreeProjection projection = projections.projections().get(index);
            ResearchTechTreeLayout layout = this.layouts.get(index);
            validateProjection(projection, layout);
            if (domains.put(layout.domain(), layout) != null) {
                throw new IllegalArgumentException(
                        "Research Tech Tree layout catalog contains a duplicate domain");
            }
        }
        byDomain = Collections.unmodifiableMap(domains);
    }

    public boolean available() {
        return !layouts.isEmpty();
    }

    public List<ResearchTechTreeLayout> layouts() {
        return layouts;
    }

    public List<Domain> domains() {
        return layouts.stream().map(ResearchTechTreeLayout::domain).toList();
    }

    public Optional<ResearchTechTreeLayout> layout(Domain domain) {
        return Optional.ofNullable(domain == null ? null : byDomain.get(domain));
    }

    public boolean matches(ResearchTechTreeProjectionCatalog projections) {
        if (projections == null || projections.projections().size() != layouts.size()) {
            return false;
        }
        try {
            for (int index = 0; index < layouts.size(); index++) {
                validateProjection(projections.projections().get(index), layouts.get(index));
            }
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static void validateProjection(
            ResearchTechTreeProjection projection,
            ResearchTechTreeLayout layout) {
        if (projection.domain() != layout.domain()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree projection and layout domains differ");
        }
        ResearchTreeLayout graphLayout = layout.graphLayout();
        if (projection.graph().nodes().size() != graphLayout.nodes().size()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree projection and layout sizes differ");
        }
        for (int ordinal = 0; ordinal < projection.graph().nodes().size(); ordinal++) {
            if (!projection.graph().nodes().get(ordinal).blueprintId()
                    .equals(graphLayout.nodes().get(ordinal).blueprintId())) {
                throw new IllegalArgumentException(
                        "Research Tech Tree projection and layout nodes differ");
            }
        }

        if (!graphLayout.groupRegions().isEmpty()
                || !graphLayout.categoryLanes().isEmpty()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree layout exposes authored classification regions");
        }
        for (ResearchTreeGraph.Node node : projection.graph().nodes()) {
            ResearchTreeLayout.PositionedNode positioned = graphLayout
                    .position(node.blueprintId()).orElseThrow();
            if (positioned.tier() < 0
                    || positioned.tier() >= graphLayout.tierCount()) {
                throw new IllegalArgumentException(
                        "Research Tech Tree node leaves its rank layout");
            }
        }
        for (ResearchTreeGraph.Edge edge : projection.graph().edges()) {
            if (graphLayout.position(edge.prerequisiteId()).orElseThrow().y()
                    <= graphLayout.position(edge.dependentId()).orElseThrow().y()) {
                throw new IllegalArgumentException(
                        "Research Tech Tree edge does not progress bottom-to-top");
            }
        }

        Set<ResearchTechTreeProjection.BoundaryLink> expected =
                new LinkedHashSet<>(projection.boundaryLinks());
        Set<ResearchTechTreeProjection.BoundaryLink> actual = new LinkedHashSet<>();
        for (ResearchTechTreeLayout.BoundaryPortal portal : layout.portals()) {
            ResearchTreeLayout.PositionedNode local = graphLayout
                    .position(portal.target().localNodeId()).orElseThrow();
            if (portal.x() < 0
                    || portal.x() + ResearchTreeLayout.PORTAL_SIZE > graphLayout.width()
                    || portal.y() < 0
                    || portal.y() + ResearchTreeLayout.PORTAL_SIZE > graphLayout.height()) {
                throw new IllegalArgumentException(
                        "Research Tech Tree portal leaves its canvas");
            }
            for (ResearchTechTreeProjection.BoundaryLink link : portal.target().links()) {
                if (!actual.add(link) || !link.localNodeId().equals(local.blueprintId())) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree layout duplicates a boundary link");
                }
            }
        }
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(
                    "Research Tech Tree layout does not represent every boundary link");
        }
    }
}
