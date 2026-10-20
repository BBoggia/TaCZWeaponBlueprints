package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import net.minecraft.resources.ResourceLocation;

/** Hand-authored layout inputs: no automatic topology generator participates. */
public final class ResearchTreeBaselineFixtures {
    private ResearchTreeBaselineFixtures() { }

    /** Branch expectations are fixture intent only, not a new production authoring format. */
    public record BranchIntent(String id, Optional<String> parent, List<ResourceLocation> directMembers) {
        public BranchIntent { directMembers = List.copyOf(directMembers); }
    }

    public record Fixture(ResearchTreeBaselineInput input, List<BranchIntent> branches) {
        public Fixture { branches = List.copyOf(branches); }
    }

    public static List<Fixture> structural() {
        var result = new ArrayList<Fixture>();
        result.add(new Fixture(new ResearchTreeBaselineInput(1, "empty", 854,
                ResearchTechTreeLayoutPolicy.DEFAULT, List.of()), List.of()));
        result.add(new Builder("single").node("root", 0, "base").build());
        result.add(new Builder("independent_roots")
                .node("a", 0, "a").node("b", 0, "b").node("c", 2, "c")
                .node("a2", 1, "a").requires("a2", "a").build());
        var unequal = new Builder("unequal_branches").node("base", 0, "base");
        for (int branch = 0; branch < 3; branch++) {
            String previous = "base";
            for (int rank = 1; rank <= 2 + branch * 2; rank++) {
                String key = "b" + branch + "_" + rank;
                unequal.node(key, rank, "branch_" + branch).requires(key, previous);
                previous = key;
            }
        }
        result.add(unequal.build());
        result.add(new Builder("nested_split_merge")
                .parent("left_inner_a", "left").parent("left_inner_b", "left")
                .node("base", 0, "base").node("left", 1, "left").node("right", 1, "right")
                .node("a", 2, "left_inner_a").node("b", 2, "left_inner_b")
                .node("a2", 3, "left_inner_a").node("b2", 3, "left_inner_b")
                .node("r2", 3, "right").node("left_merge", 4, "left")
                .node("shared", 5, "shared")
                .requires("left", "base").requires("right", "base")
                .requires("a", "left").requires("b", "left")
                .requires("a2", "a").requires("b2", "b").requires("r2", "right")
                .requires("left_merge", "a2").requires("left_merge", "b2")
                .requires("shared", "left_merge").requires("shared", "r2").build());
        result.add(new Builder("region_return")
                .node("a1", 0, "a").node("b2", 1, "b").node("a3", 2, "a")
                .requires("b2", "a1").requires("a3", "b2").build());
        result.add(new Builder("sparse_ranks")
                .node("a", 0, "a").node("b", 400_000, "a").node("c", 1_000_000, "a")
                .requires("b", "a").requires("c", "b").build());
        result.add(new Builder("and_of_or")
                .node("a", 0, "a").node("b", 0, "b").node("c", 0, "c")
                .node("d", 1, "d").requires("d", "a", "b").requires("d", "c").build());
        result.add(new Builder("external_unknown")
                .node("a", 0, "a").node("d", 1, "d")
                .group("d", 1, 1, "a").boundary("d", "ammo_external", Domain.AMMO).build());
        var fan = new Builder("fan_in_out").node("root", 0, "base");
        var alternatives = new ArrayList<String>();
        for (int i = 0; i < 16; i++) {
            String key = "arm_" + i;
            fan.node(key, 1, "arm_" + i).requires(key, "root");
            alternatives.add(key);
        }
        fan.node("merge", 2, "merge").requires("merge", alternatives.toArray(String[]::new));
        result.add(fan.build());
        var cross = new Builder("long_crossings");
        for (int rank = 0; rank < 5; rank++) {
            for (int branch = 0; branch < 4; branch++) {
                String key = "n" + rank + "_" + branch;
                cross.node(key, rank, "b" + branch);
                if (rank > 0) cross.requires(key, "n" + (rank - 1) + "_" + branch);
                if (rank == 4) cross.requires(key, "n0_" + (3 - branch));
            }
        }
        result.add(cross.build());
        result.add(layered("connected_512", 16, 32, false));
        result.add(layered("dense_256", 16, 16, true));
        return List.copyOf(result);
    }

    public static Fixture maximumNodes() {
        var builder = new Builder("maximum_nodes_edgeless");
        for (int i = 0; i < ResearchTreeGraph.MAX_NODES; i++) {
            builder.node("n" + i, i / 28, "region_" + i % 8);
        }
        return builder.build();
    }

    /** Valid high-edge input, kept out of the ordinary repeated benchmark loop. */
    public static Fixture maximumEdges() {
        var builder = new Builder("maximum_edges");
        String[] roots = new String[64];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = "root_" + i;
            builder.node(roots[i], 0, "roots");
        }
        for (int i = 0; i < ResearchTreeGraph.MAX_EDGES / roots.length; i++) {
            String key = "dependent_" + i;
            builder.node(key, 1, "targets").requires(key, roots);
        }
        return builder.build();
    }

    private static Fixture layered(String name, int width, int levels, boolean dense) {
        var b = new Builder(name);
        for (int rank = 0; rank < levels; rank++) {
            for (int col = 0; col < width; col++) {
                String key = "n" + rank + "_" + col;
                b.node(key, rank, "b" + col % 4);
                if (rank > 0) {
                    if (dense) {
                        String[] previous = new String[width];
                        for (int i = 0; i < width; i++) previous[i] = "n" + (rank - 1) + "_" + i;
                        b.requires(key, previous);
                    } else {
                        b.requires(key, "n" + (rank - 1) + "_" + col);
                        b.requires(key, "n" + (rank - 1) + "_" + (col + 1) % width);
                    }
                }
            }
        }
        return b.build();
    }

    static List<Fixture> routing() {
        var repeated = new Builder("repeated_alternatives").node("a", 0, "a").node("b", 0, "b")
                .node("c", 0, "c").node("d", 1, "d").requires("d", "a", "b").requires("d", "a", "c").build();
        var or = new Builder("single_or").node("a", 0, "a").node("b", 0, "b").node("c", 0, "c")
                .node("d", 1, "d").requires("d", "a", "b", "c").build();
        var anonymous = new Builder("anonymous_groups").node("d", 1, "d").group("d", 2, 0).group("d", 0, 3).build();
        var portals = new Builder("multiple_boundaries").node("a", 0, "a").node("d", 1, "d")
                .requires("d", "a").boundary("d", "ammo_one", Domain.AMMO).boundary("d", "ammo_two", Domain.AMMO)
                .boundary("d", "attachment", Domain.ATTACHMENTS);
        portals.boundaries.add(new ResearchTechTreeProjection.BoundaryLink(portals.id("d"), portals.id("ammo_unlock"),
                Domain.AMMO, ResearchTechTreeProjection.Direction.UNLOCK));
        var many = new Builder("many_groups").node("a", 0, "a").node("d", 1, "d");
        for (int i = 0; i < 64; i++) many.requires("d", "a");
        return List.of(repeated, or, anonymous, portals.build(), many.build());
    }

    static final class Builder {
        private final String name;
        private final Map<String, Integer> ranks = new LinkedHashMap<>();
        private final Map<String, List<ResourceLocation>> owners = new LinkedHashMap<>();
        private final Map<String, String> parents = new LinkedHashMap<>();
        private final List<ResearchTreeGraph.RequirementGroup> groups = new ArrayList<>();
        private final List<ResearchTechTreeProjection.BoundaryLink> boundaries = new ArrayList<>();

        Builder(String name) { this.name = name; }
        ResourceLocation id(String key) { return ResourceLocation.fromNamespaceAndPath("baseline", name + "/" + key); }
        Builder parent(String child, String parent) { parents.put(child, parent); return this; }
        Builder node(String key, int rank, String owner) {
            if (ranks.putIfAbsent(key, rank) != null) throw new IllegalArgumentException("duplicate fixture node");
            owners.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(id(key));
            return this;
        }
        Builder requires(String dependent, String... alternatives) { return group(dependent, 0, 0, alternatives); }
        Builder group(String dependent, int hidden, int external, String... alternatives) {
            int ordinal = (int) groups.stream().filter(g -> g.dependentId().equals(id(dependent))).count();
            groups.add(new ResearchTreeGraph.RequirementGroup(id(dependent), ordinal,
                    java.util.Arrays.stream(alternatives).map(this::id).toList(), hidden, external, true, false));
            return this;
        }
        Builder boundary(String local, String remote, Domain domain) {
            boundaries.add(new ResearchTechTreeProjection.BoundaryLink(id(local), id(remote), domain,
                    ResearchTechTreeProjection.Direction.REQUIREMENT));
            return this;
        }
        Fixture build() {
            var nodes = new ArrayList<ResearchTreeGraph.Node>();
            var members = new ArrayList<ResearchTechTreePresentation.Member>();
            for (var entry : ranks.entrySet()) {
                var nodeId = id(entry.getKey());
                var required = groups.stream().filter(g -> g.dependentId().equals(nodeId)).toList();
                int visible = (int) required.stream().flatMap(g -> g.visibleAlternativeIds().stream()).distinct().count();
                int hidden = required.stream().mapToInt(ResearchTreeGraph.RequirementGroup::hiddenAlternativeCount).sum();
                var availability = required.isEmpty() ? ResearchTreeGraph.Availability.AVAILABLE
                        : ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED;
                nodes.add(new ResearchTreeGraph.Node(nodes.size(), nodeId, "fixture." + entry.getKey(),
                        "gun", ResourceLocation.fromNamespaceAndPath("minecraft", "paper"), JournalVisibility.FULL,
                        false, true, required.isEmpty(), 8, 0, visible, hidden, availability));
                members.add(new ResearchTechTreePresentation.Member(nodeId, entry.getValue(),
                        nodes.size(), Optional.empty(), PlacementOrigin.EXACT, Optional.empty()));
            }
            members.sort(Comparator.comparingInt(ResearchTechTreePresentation.Member::rank)
                    .thenComparingLong(ResearchTechTreePresentation.Member::siblingOrder));
            var lane = new ResearchTechTreePresentation.LaneView(id("lane"), name, Optional.empty(),
                    Optional.empty(), 0, members);
            var domain = new ResearchTechTreePresentation.DomainView(Domain.WEAPONS, name,
                    Optional.empty(), Optional.empty(), List.of(lane));
            var graph = ResearchTreeGraph.withRequirementGroups(nodes, groups);
            var data = new ResearchTreeBaselineInput.ProjectionData(domain, graph.nodes(), graph.edges(),
                    graph.requirementGroups(), boundaries, List.of(), 20);
            return new Fixture(new ResearchTreeBaselineInput(1, name, 854,
                    ResearchTechTreeLayoutPolicy.DEFAULT, List.of(data)), owners.entrySet().stream()
                    .map(e -> new BranchIntent(e.getKey(), Optional.ofNullable(parents.get(e.getKey())), e.getValue()))
                    .toList());
        }
    }
}
