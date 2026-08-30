package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionCoordinate;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.WeaponRating;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeEntryBundle;

import net.minecraft.resources.ResourceLocation;

/**
 * One client-only domain view derived strictly from the synchronized public
 * graph and Tech Tree presentation.
 */
public record ResearchTechTreeProjection(
        Domain domain,
        ResearchTechTreePresentation.DomainView presentation,
        ResearchTreeGraph graph,
        Map<ResourceLocation, Placement> placements,
        List<BoundaryLink> boundaryLinks,
        List<ResearchTechTreePresentation.BandLabel> bands,
        int maxNodesPerLayer) {
    private static final Comparator<BoundaryLink> BOUNDARY_ORDER = Comparator
            .comparing((BoundaryLink value) -> value.localNodeId().toString())
            .thenComparingInt(value -> value.remoteDomain().ordinal())
            .thenComparing(value -> value.remoteNodeId().toString())
            .thenComparingInt(value -> value.direction().ordinal());

    public ResearchTechTreeProjection {
        if (domain == null || presentation == null || graph == null
                || placements == null || boundaryLinks == null || bands == null
                || domain != presentation.domain()
                || maxNodesPerLayer
                        < ResearchTechTreeDefinition.LayoutDefinition.MIN_NODES_PER_LAYER
                || maxNodesPerLayer
                        > ResearchTechTreeDefinition.LayoutDefinition.MAX_NODES_PER_LAYER) {
            throw new IllegalArgumentException("invalid Research Tech Tree projection fields");
        }
        if (placements.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException(
                    "Research Tech Tree projection placements cannot contain nulls");
        }
        placements = Collections.unmodifiableMap(new LinkedHashMap<>(placements));
        bands = List.copyOf(bands);
        if (bands.stream().anyMatch(java.util.Objects::isNull)
                || bands.stream().map(ResearchTechTreePresentation.BandLabel::id)
                        .distinct().count() != bands.size()) {
            throw new IllegalArgumentException(
                    "invalid Research Tech Tree projection bands");
        }
        ArrayList<BoundaryLink> sortedLinks = new ArrayList<>(boundaryLinks);
        if (sortedLinks.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Research Tech Tree projection links cannot be null");
        }
        sortedLinks.sort(BOUNDARY_ORDER);
        boundaryLinks = List.copyOf(sortedLinks);

        List<ResourceLocation> graphNodeIds = graph.nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .toList();
        if (!graphNodeIds.equals(List.copyOf(placements.keySet()))
                || graph.nodes().stream().anyMatch(node -> !node.visibility().revealsIdentity())) {
            throw new IllegalArgumentException(
                    "Research Tech Tree projection nodes and placements do not match");
        }
        Set<ResourceLocation> authoredMembers = new LinkedHashSet<>();
        for (ResearchTechTreePresentation.LaneView lane : presentation.lanes()) {
            for (ResearchTechTreePresentation.Member member : lane.members()) {
                authoredMembers.add(member.nodeId());
                Placement placement = placements.get(member.nodeId());
                if (placement == null
                        || !placement.laneId().equals(lane.id())
                        || placement.rank() != member.rank()
                        || placement.laneOrder() != lane.order()
                        || placement.siblingOrder() != member.siblingOrder()
                        || !placement.bandId().equals(member.bandId())
                        || placement.origin() != member.origin()
                        || !placement.rating().equals(member.rating())
                        || !placement.automaticBranch().equals(
                                member.automaticBranch())) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree projection placement contradicts its presentation");
                }
            }
        }
        if (!authoredMembers.equals(placements.keySet())) {
            throw new IllegalArgumentException(
                    "Research Tech Tree projection does not cover its domain members");
        }
        Set<BoundaryLink> uniqueLinks = new LinkedHashSet<>();
        for (BoundaryLink link : boundaryLinks) {
            if (!uniqueLinks.add(link)
                    || graph.node(link.localNodeId()).isEmpty()
                    || graph.node(link.remoteNodeId()).isPresent()
                    || link.remoteDomain() == domain) {
                throw new IllegalArgumentException("invalid Research Tech Tree boundary link");
            }
        }
    }

    /** Compatibility constructor for projections without custom band labels. */
    public ResearchTechTreeProjection(
            Domain domain,
            ResearchTechTreePresentation.DomainView presentation,
            ResearchTreeGraph graph,
            Map<ResourceLocation, Placement> placements,
            List<BoundaryLink> boundaryLinks) {
        this(
                domain,
                presentation,
                graph,
                placements,
                boundaryLinks,
                List.of(),
                ResearchTechTreeDefinition.LayoutDefinition.DEFAULT_NODES_PER_LAYER);
    }

    /** Compatibility constructor for projections predating tree-owned width. */
    public ResearchTechTreeProjection(
            Domain domain,
            ResearchTechTreePresentation.DomainView presentation,
            ResearchTreeGraph graph,
            Map<ResourceLocation, Placement> placements,
            List<BoundaryLink> boundaryLinks,
            List<ResearchTechTreePresentation.BandLabel> bands) {
        this(
                domain,
                presentation,
                graph,
                placements,
                boundaryLinks,
                bands,
                ResearchTechTreeDefinition.LayoutDefinition.DEFAULT_NODES_PER_LAYER);
    }

    public Optional<Placement> placement(ResourceLocation nodeId) {
        return Optional.ofNullable(nodeId == null ? null : placements.get(nodeId));
    }

    public boolean containsBoundaryLink(BoundaryLink link) {
        return link != null && Collections.binarySearch(boundaryLinks, link, BOUNDARY_ORDER) >= 0;
    }

    /** True when state-only graph updates can reuse this domain's future layout. */
    public boolean hasSameTopology(ResearchTechTreeProjection other) {
        if (other == null || domain != other.domain()
                || maxNodesPerLayer != other.maxNodesPerLayer()
                || !graph.hasSameLayoutTopology(other.graph())
                || !boundaryLinks.equals(other.boundaryLinks())
                || !bands.equals(other.bands())
                || placements.size() != other.placements().size()) {
            return false;
        }
        var left = placements.values().iterator();
        var right = other.placements().values().iterator();
        while (left.hasNext()) {
            Placement a = left.next();
            Placement b = right.next();
            if (!a.nodeId().equals(b.nodeId())
                    || !a.laneId().equals(b.laneId())
                    || a.rank() != b.rank()
                    || a.laneOrder() != b.laneOrder()
                    || a.siblingOrder() != b.siblingOrder()
                    || !a.bandId().equals(b.bandId())
                    || a.origin() != b.origin()
                    || !a.automaticBranch().equals(b.automaticBranch())) {
                return false;
            }
        }
        return true;
    }

    public record Placement(
            ResourceLocation nodeId,
            ResourceLocation laneId,
            int rank,
            int laneOrder,
            long siblingOrder,
            Optional<ResourceLocation> bandId,
            PlacementOrigin origin,
            Optional<WeaponRating> rating,
            Optional<ResearchTechTreePresentation.AutomaticBranchPlacement>
                    automaticBranch) {
        /** Compatibility constructor for placements without branch metadata. */
        public Placement(
                ResourceLocation nodeId,
                ResourceLocation laneId,
                int rank,
                int laneOrder,
                long siblingOrder,
                Optional<ResourceLocation> bandId,
                PlacementOrigin origin,
                Optional<WeaponRating> rating) {
            this(
                    nodeId,
                    laneId,
                    rank,
                    laneOrder,
                    siblingOrder,
                    bandId,
                    origin,
                    rating,
                    Optional.empty());
        }

        /** Compatibility constructor for legacy tier/level fixtures. */
        public Placement(
                ResourceLocation nodeId,
                ResourceLocation laneId,
                Tier tier,
                int level,
                int laneOrder,
                long siblingOrder,
                PlacementOrigin origin,
                Optional<WeaponRating> rating) {
            this(
                    nodeId,
                    laneId,
                    ResearchTechTreeContract.legacyProgressionCoordinate(
                            new ProgressionPosition(tier, level, siblingOrder)).rank(),
                    laneOrder,
                    siblingOrder,
                    Optional.of(ResearchTechTreeContract.legacyBandId(tier)),
                    origin,
                    rating,
                    Optional.empty());
        }

        /** Compatibility constructor for legacy tier/order fixtures. */
        public Placement(
                ResourceLocation nodeId,
                ResourceLocation laneId,
                Tier tier,
                int laneOrder,
                int siblingOrder,
                Optional<WeaponRating> rating) {
            this(
                    nodeId,
                    laneId,
                    tier,
                    0,
                    laneOrder,
                    siblingOrder,
                    PlacementOrigin.EXACT,
                    rating);
        }

        public Placement {
            bandId = bandId == null ? Optional.empty() : bandId;
            rating = rating == null ? Optional.empty() : rating;
            automaticBranch = automaticBranch == null
                    ? Optional.empty() : automaticBranch;
            if (nodeId == null || laneId == null || origin == null
                    || rank < 0 || rank > ResearchTechTreeContract.MAX_PROGRESSION_RANK
                    || laneOrder < 0 || laneOrder > ResearchTechTreeDefinition.MAX_ORDER
                    || siblingOrder < 0
                    || bandId.stream().anyMatch(java.util.Objects::isNull)
                    || origin != PlacementOrigin.AUTOMATIC && automaticBranch.isPresent()
                    || (origin != PlacementOrigin.AUTOMATIC
                            && siblingOrder > ResearchTechTreeEntryBundle.MAX_ORDER)) {
                throw new IllegalArgumentException("invalid Research Tech Tree projected placement");
            }
        }

        public ProgressionCoordinate position() {
            return new ProgressionCoordinate(rank, siblingOrder, bandId);
        }

        public Optional<Tier> legacyTier() {
            return java.util.Arrays.stream(Tier.values())
                    .filter(value -> bandId.filter(
                            ResearchTechTreeContract.legacyBandId(value)::equals).isPresent())
                    .findFirst();
        }

        /** @deprecated Rank is progression authority; use {@link #legacyTier()} for labels. */
        @Deprecated(forRemoval = false)
        public Tier tier() {
            return legacyTier().orElseGet(() -> Tier.values()[Math.min(
                    Tier.values().length - 1,
                    rank / ResearchTechTreeContract.LEGACY_RANK_STRIDE)]);
        }

        /** @deprecated Rank is progression authority. */
        @Deprecated(forRemoval = false)
        public int level() {
            return rank % ResearchTechTreeContract.LEGACY_RANK_STRIDE;
        }
    }

    public record BoundaryLink(
            ResourceLocation localNodeId,
            ResourceLocation remoteNodeId,
            Domain remoteDomain,
            Direction direction) {
        public BoundaryLink {
            if (localNodeId == null || remoteNodeId == null || remoteDomain == null
                    || direction == null || localNodeId.equals(remoteNodeId)) {
                throw new IllegalArgumentException("invalid Research Tech Tree domain boundary link");
            }
        }
    }

    public enum Direction {
        REQUIREMENT,
        UNLOCK
    }
}
