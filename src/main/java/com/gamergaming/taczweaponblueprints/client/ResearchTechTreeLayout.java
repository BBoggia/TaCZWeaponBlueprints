package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;

import net.minecraft.resources.ResourceLocation;

/** Immutable logical-canvas geometry for one public Tech Tree domain. */
public record ResearchTechTreeLayout(
        Domain domain,
        ResearchTreeLayout graphLayout,
        List<TierBand> tiers,
        List<BoundaryPortal> portals,
        List<ProgressionBand> bands) {
    /** Compatibility constructor for layouts with legacy bands or no bands. */
    public ResearchTechTreeLayout(
            Domain domain,
            ResearchTreeLayout graphLayout,
            List<TierBand> tiers,
            List<BoundaryPortal> portals) {
        this(domain, graphLayout, tiers, portals, List.of());
    }

    public ResearchTechTreeLayout {
        if (domain == null || graphLayout == null || tiers == null
                || portals == null || bands == null
                || tiers.stream().anyMatch(java.util.Objects::isNull)
                || portals.stream().anyMatch(java.util.Objects::isNull)
                || bands.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("invalid Research Tech Tree layout fields");
        }
        tiers = List.copyOf(tiers);
        portals = List.copyOf(portals);
        bands = List.copyOf(bands);
        validateStructure(domain, graphLayout, tiers, portals, bands);
    }

    public Optional<TierBand> tier(Tier tier) {
        return tier == null
                ? Optional.empty()
                : tiers.stream().filter(band -> band.tier() == tier).findFirst();
    }

    public Optional<BoundaryPortal> portal(
            ResourceLocation localNodeId,
            Domain remoteDomain,
            ResearchTechTreeProjection.Direction direction) {
        return portals.stream().filter(portal ->
                portal.target().localNodeId().equals(localNodeId)
                        && portal.target().remoteDomain() == remoteDomain
                        && portal.target().direction() == direction).findFirst();
    }

    /**
     * Reports weakly disconnected public graph regions without moving them into
     * a space-filling component grid. A healthy generated tree returns one
     * component; authored trees remain renderable while exposing every extra
     * component to diagnostics.
     */
    public List<LayoutComponent> layoutComponents() {
        Map<Integer, List<ResourceLocation>> members = new LinkedHashMap<>();
        graphLayout.nodes().stream()
                .sorted(Comparator
                        .comparingInt(ResearchTreeLayout.PositionedNode::component)
                        .thenComparing(node -> node.blueprintId().toString()))
                .forEach(node -> members.computeIfAbsent(
                        node.component(), ignored -> new ArrayList<>())
                        .add(node.blueprintId()));
        return members.entrySet().stream()
                .map(entry -> new LayoutComponent(
                        entry.getKey(), entry.getValue().get(0), entry.getValue()))
                .toList();
    }

    public boolean hasDisconnectedComponents() {
        return diagnostics().disconnected();
    }

    public LayoutDiagnostics diagnostics() {
        int maximumNodesInRow = graphLayout.nodes().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ResearchTreeLayout.PositionedNode::y,
                        java.util.stream.Collectors.counting()))
                .values().stream()
                .mapToInt(Math::toIntExact)
                .max()
                .orElse(0);
        return new LayoutDiagnostics(
                graphLayout.tierCount(), maximumNodesInRow, layoutComponents());
    }

    private static void validateStructure(
            Domain domain,
            ResearchTreeLayout graphLayout,
            List<TierBand> tiers,
            List<BoundaryPortal> portals,
            List<ProgressionBand> bands) {
        if (graphLayout.nodes().isEmpty()
                || tiers.size() > Tier.values().length
                || !tiers.isEmpty() && !bands.isEmpty()
                || !graphLayout.groupRegions().isEmpty()
                || !graphLayout.categoryLanes().isEmpty()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree layout must be one canvas with legacy or progression bands");
        }
        int previousTierY = Integer.MAX_VALUE;
        int previousOrdinal = -1;
        for (TierBand tier : tiers) {
            if (tier.tier().ordinal() <= previousOrdinal
                    || tier.y() >= previousTierY
                    || tier.bottom() > previousTierY
                    || tier.bottom() > graphLayout.height()) {
                throw new IllegalArgumentException(
                        "Research Tech Tree tiers are not stable bottom-to-top bands");
            }
            previousOrdinal = tier.tier().ordinal();
            previousTierY = tier.y();
        }
        int previousBandY = Integer.MAX_VALUE;
        int previousIndex = -1;
        Set<ResourceLocation> bandIds = new LinkedHashSet<>();
        for (ProgressionBand band : bands) {
            if (band.index() <= previousIndex
                    || !bandIds.add(band.id())
                    || band.y() >= previousBandY
                    || band.bottom() > previousBandY
                    || band.bottom() > graphLayout.height()
                    || band.iconNodeId().filter(icon ->
                            graphLayout.position(icon).isEmpty()).isPresent()) {
                throw new IllegalArgumentException(
                        "Research Tech Tree progression bands are not stable bottom-to-top bands");
            }
            previousIndex = band.index();
            previousBandY = band.y();
        }

        Set<PortalKey> portalKeys = new LinkedHashSet<>();
        Set<ResearchTechTreeProjection.BoundaryLink> boundaryLinks = new LinkedHashSet<>();
        for (BoundaryPortal portal : portals) {
            PortalTarget target = portal.target();
            ResearchTreeLayout.PositionedNode local = graphLayout
                    .position(target.localNodeId()).orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Research Tech Tree portal has no local node"));
            if (target.remoteDomain() == domain
                    || !portalKeys.add(new PortalKey(
                            target.localNodeId(), target.remoteDomain(), target.direction()))
                    || portal.x() + ResearchTreeLayout.PORTAL_SIZE > graphLayout.width()
                    || portal.y() + ResearchTreeLayout.PORTAL_SIZE > graphLayout.height()
                    || target.direction() == ResearchTechTreeProjection.Direction.UNLOCK
                            && portal.y() + ResearchTreeLayout.PORTAL_SIZE >= local.y()
                    || target.direction() == ResearchTechTreeProjection.Direction.REQUIREMENT
                            && portal.y() <= local.y() + ResearchTreeLayout.NODE_HEIGHT) {
                throw new IllegalArgumentException(
                        "invalid Research Tech Tree boundary portal geometry");
            }
            for (ResearchTechTreeProjection.BoundaryLink link : target.links()) {
                if (!boundaryLinks.add(link)) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree boundary link is represented more than once");
                }
            }
        }
    }

    public record TierBand(Tier tier, int y, int height) {
        public TierBand {
            if (tier == null || y < 0 || height < ResearchTreeLayout.NODE_HEIGHT
                    || y > ResearchTreeLayout.MAX_DIMENSION - height) {
                throw new IllegalArgumentException("invalid Research Tech Tree tier band");
            }
        }

        public int bottom() {
            return y + height;
        }
    }

    public record ProgressionBand(
            ResourceLocation id,
            int index,
            int y,
            int height,
            Optional<Integer> color,
            Optional<ResourceLocation> iconNodeId) {
        /** Compatibility constructor for bands predating optional authored color. */
        public ProgressionBand(ResourceLocation id, int index, int y, int height) {
            this(id, index, y, height, Optional.empty(), Optional.empty());
        }

        /** Compatibility constructor for bands predating optional icons. */
        public ProgressionBand(
                ResourceLocation id,
                int index,
                int y,
                int height,
                Optional<Integer> color) {
            this(id, index, y, height, color, Optional.empty());
        }

        public ProgressionBand {
            color = color == null ? Optional.empty() : color;
            iconNodeId = iconNodeId == null ? Optional.empty() : iconNodeId;
            if (id == null || index < 0 || index >= 32 || y < 0
                    || height < ResearchTreeLayout.NODE_HEIGHT
                    || y > ResearchTreeLayout.MAX_DIMENSION - height
                    || color.filter(value -> value < 0 || value > 0xFFFFFF).isPresent()
                    || iconNodeId.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException(
                        "invalid Research Tech Tree progression band");
            }
        }

        public int bottom() {
            return y + height;
        }
    }

    public record PortalTarget(
            ResourceLocation localNodeId,
            Domain remoteDomain,
            ResearchTechTreeProjection.Direction direction,
            List<ResearchTechTreeProjection.BoundaryLink> links) {
        public PortalTarget {
            if (localNodeId == null || remoteDomain == null || direction == null
                    || links == null || links.isEmpty()
                    || links.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("invalid Research Tech Tree portal target");
            }
            links = List.copyOf(links);
            Set<ResearchTechTreeProjection.BoundaryLink> unique = new LinkedHashSet<>();
            for (ResearchTechTreeProjection.BoundaryLink link : links) {
                if (!unique.add(link)
                        || !link.localNodeId().equals(localNodeId)
                        || link.remoteDomain() != remoteDomain
                        || link.direction() != direction) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree portal target combines unrelated links");
                }
            }
        }

        public ResearchTechTreeProjection.BoundaryLink primaryLink() {
            return links.get(0);
        }

        public int connectionCount() {
            return links.size();
        }
    }

    public record BoundaryPortal(PortalTarget target, int x, int y) {
        public BoundaryPortal {
            if (target == null || x < 0 || y < 0) {
                throw new IllegalArgumentException("invalid Research Tech Tree boundary portal");
            }
        }
    }

    public record LayoutComponent(
            int index,
            ResourceLocation stableRootId,
            List<ResourceLocation> nodeIds) {
        public LayoutComponent {
            if (index < 0 || stableRootId == null || nodeIds == null || nodeIds.isEmpty()
                    || nodeIds.stream().anyMatch(java.util.Objects::isNull)
                    || !nodeIds.contains(stableRootId)
                    || nodeIds.stream().distinct().count() != nodeIds.size()) {
                throw new IllegalArgumentException(
                        "invalid Research Tech Tree layout component diagnostic");
            }
            nodeIds = List.copyOf(nodeIds);
        }
    }

    public record LayoutDiagnostics(
            int visualRowCount,
            int maximumNodesInRow,
            List<LayoutComponent> components) {
        public LayoutDiagnostics {
            if (visualRowCount < 1
                    || maximumNodesInRow < 1
                    || components == null
                    || components.isEmpty()
                    || components.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException(
                        "invalid Research Tech Tree layout diagnostics");
            }
            components = List.copyOf(components);
            for (int index = 0; index < components.size(); index++) {
                if (components.get(index).index() != index) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree component diagnostics are not contiguous");
                }
            }
        }

        public boolean disconnected() {
            return components.size() > 1;
        }
    }

    private record PortalKey(
            ResourceLocation localNodeId,
            Domain remoteDomain,
            ResearchTechTreeProjection.Direction direction) {
    }
}
