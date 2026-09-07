package com.gamergaming.taczweaponblueprints.client;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable geometry input extracted only from a validated, typed public projection.
 * It deliberately contains no names, item handles, prices, ratings, or player state.
 */
public final class ResearchTreeSceneInput {
    private final Topology topology;
    private final ResearchTreeScenePolicy policy;

    private ResearchTreeSceneInput(Topology topology, ResearchTreeScenePolicy policy) {
        this.topology = topology;
        this.policy = java.util.Objects.requireNonNull(policy);
    }

    public static ResearchTreeSceneInput from(ResearchTechTreeProjection projection,
            ResearchTreeScenePolicy policy) {
        java.util.Objects.requireNonNull(projection);
        var placements = projection.placements().values().stream()
                .sorted(Comparator.comparing(p -> p.nodeId().toString()))
                .map(p -> new ResearchTechTreeProjection.Placement(p.nodeId(), p.laneId(), p.rank(),
                        p.laneOrder(), p.siblingOrder(), p.bandId(), p.origin(), Optional.empty(),
                        p.automaticBranch())).toList();
        var groups = projection.graph().requirementGroups().stream()
                .map(g -> new Group(new GroupId(g.dependentId(), g.ordinal()),
                        g.visibleAlternativeIds(), g.hiddenAlternativeCount(), g.externalAlternativeCount()))
                .toList();
        for (var edge : projection.graph().edges()) {
            if (projection.placements().get(edge.prerequisiteId()).rank()
                    >= projection.placements().get(edge.dependentId()).rank()) {
                throw new IllegalArgumentException("scene ranks contradict a prerequisite edge");
            }
        }
        return new ResearchTreeSceneInput(new Topology(projection.domain(), placements,
                projection.graph().edges(), groups, projection.boundaryLinks(),
                projection.bands().stream().map(b -> b.id()).toList(), projection.maxNodesPerLayer(), authoredSeeds(projection)), policy);
    }

    private static List<ResearchTreeBranchStructure.Seed> authoredSeeds(ResearchTechTreeProjection projection) {
        if (projection.presentation().branches().isEmpty()) return List.of();
        var seeds = new java.util.ArrayList<ResearchTreeBranchStructure.Seed>();
        var owned = new java.util.HashSet<ResourceLocation>();
        for (var branch : projection.presentation().branches()) {
            seeds.add(new ResearchTreeBranchStructure.Seed("authored/" + branch.id(),
                    branch.parent().map(id -> "authored/" + id), branch.order(), branch.members()));
            owned.addAll(branch.members());
        }
        var remaining = projection.placements().keySet().stream().filter(id -> !owned.contains(id)).toList();
        if (!remaining.isEmpty()) seeds.add(new ResearchTreeBranchStructure.Seed("unassigned",Optional.empty(),1_000_000,remaining));
        return ResearchTreeBranchStructure.canonicalSeeds(seeds);
    }

    /** An absent typed domain is an empty scene, never a synthetic blueprint. */
    public static ResearchTreeSceneInput empty(Domain domain, ResearchTreeScenePolicy policy) {
        return new ResearchTreeSceneInput(new Topology(java.util.Objects.requireNonNull(domain),
                List.of(), List.of(), List.of(), List.of(), List.of(), 8, List.of()), policy);
    }

    /** Internal engine/fixture seam. This does not add a datapack or wire authoring format. */
    public ResearchTreeSceneInput withBranchSeeds(List<ResearchTreeBranchStructure.Seed> seeds) {
        var canonical = ResearchTreeBranchStructure.canonicalSeeds(seeds);
        var t = topology;
        var result = new ResearchTreeSceneInput(new Topology(t.domain(), t.nodes(), t.edges(), t.groups(),
                t.boundaries(), t.bands(), t.maxNodesPerLayer(), canonical), policy);
        if (!canonical.isEmpty()) ResearchTreeBranchStructure.resolve(result);
        return result;
    }

    public Topology topology() { return topology; }
    public ResearchTreeScenePolicy policy() { return policy; }
    public Key key(String engineRevision) { return new Key(topology, policy, engineRevision); }

    public record Key(Topology topology, ResearchTreeScenePolicy policy, String engineRevision) {
        public Key {
            if (topology == null || policy == null || engineRevision == null
                    || engineRevision.isBlank() || engineRevision.length() > 128) {
                throw new IllegalArgumentException("invalid scene key");
            }
        }
    }

    /** Lane and automatic placement hints are seeds, not an inferred branch ownership model. */
    public record Topology(Domain domain, List<ResearchTechTreeProjection.Placement> nodes,
            List<ResearchTreeGraph.Edge> edges, List<Group> groups,
            List<ResearchTechTreeProjection.BoundaryLink> boundaries,
            List<ResourceLocation> bands, int maxNodesPerLayer, List<ResearchTreeBranchStructure.Seed> branchSeeds) {
        public Topology {
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
            groups = List.copyOf(groups);
            boundaries = List.copyOf(boundaries);
            bands = List.copyOf(bands);
            branchSeeds = List.copyOf(branchSeeds);
        }
    }

    public record GroupId(ResourceLocation dependentId, int ordinal) {
        public GroupId {
            if (dependentId == null || ordinal < 0 || ordinal >= 64) {
                throw new IllegalArgumentException("invalid scene group identity");
            }
        }
    }

    /** Groups are ANDed; alternatives within a group are ORed. Anonymous alternatives remain counts. */
    public record Group(GroupId id, List<ResourceLocation> alternatives, int hiddenCount, int externalCount) {
        public Group {
            if (id == null || alternatives.size() > 64 || hiddenCount < 0 || externalCount < 0
                    || (long) alternatives.size() + hiddenCount + externalCount > 64
                    || (long) alternatives.size() + hiddenCount + externalCount == 0) {
                throw new IllegalArgumentException("invalid scene requirement group");
            }
            alternatives = List.copyOf(alternatives);
        }
    }
}
