package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import net.minecraft.resources.ResourceLocation;

/** Containment, deliberately independent of the blueprint dependency DAG and its region connections. */
public final class ResearchTreeBranchStructure {
    public static final int MAX_REGIONS = 8192;
    public static final int MAX_DEPTH = 64;
    public static final Comparator<Seed> ORDER = Comparator.comparingInt(Seed::order).thenComparing(Seed::id);
    public static final Comparator<ResearchTechTreeProjection.Placement> NODE_ORDER = Comparator
            .comparingInt(ResearchTechTreeProjection.Placement::laneOrder)
            .thenComparingLong(ResearchTechTreeProjection.Placement::siblingOrder)
            .thenComparing(p -> p.nodeId().toString());

    /** ID is topology-scoped internal identity, never an automatically generated player-facing title. */
    public record Seed(String id, Optional<String> parentId, int order, List<ResourceLocation> directMembers) {
        public Seed {
            if (!validId(id) || parentId == null || parentId.filter(p -> !validId(p)).isPresent()
                    || order < 0 || order > 1_000_000 || directMembers.size() > 4096) {
                throw new IllegalArgumentException("invalid branch seed");
            }
            directMembers = directMembers.stream().sorted(Comparator.comparing(Object::toString)).toList();
        }
        private static boolean validId(String value) {
            return value != null && !value.isBlank() && value.length() <= 512
                    && value.chars().noneMatch(Character::isISOControl);
        }
    }

    private final List<Seed> seeds;
    private final Map<String, Seed> byId;
    private final Map<String, List<Seed>> children;
    private final Map<ResourceLocation, String> owners;
    private final Map<String, Integer> depths;
    private final List<Seed> roots;

    private ResearchTreeBranchStructure(List<Seed> seeds, Set<ResourceLocation> permitted) {
        this.seeds = canonicalSeeds(seeds);
        Map<String, Seed> byId = new LinkedHashMap<>();
        Map<ResourceLocation, String> owners = new LinkedHashMap<>();
        Map<String, List<Seed>> children = new LinkedHashMap<>();
        for (var seed : this.seeds) {
            if (byId.put(seed.id(), seed) != null) throw new IllegalArgumentException("duplicate branch identity");
            for (var member : seed.directMembers()) {
                if (!permitted.contains(member) || owners.put(member, seed.id()) != null) {
                    throw new IllegalArgumentException("branch ownership is duplicate or outside the public projection");
                }
            }
        }
        if (!owners.keySet().equals(permitted)) throw new IllegalArgumentException("branch ownership is incomplete");
        Map<String, Integer> depths = new LinkedHashMap<>();
        for (var seed : this.seeds) {
            int depth = 1;
            var cursor = seed;
            var visited = new HashSet<String>();
            while (cursor.parentId().isPresent()) {
                if (!visited.add(cursor.id()) || ++depth > MAX_DEPTH) {
                    throw new IllegalArgumentException("branch containment cycle or depth budget exceeded");
                }
                cursor = byId.get(cursor.parentId().orElseThrow());
                if (cursor == null) throw new IllegalArgumentException("branch parent is absent");
            }
            depths.put(seed.id(), depth);
            seed.parentId().ifPresent(p -> children.computeIfAbsent(p, ignored -> new ArrayList<>()).add(seed));
        }
        // Empty containers are useful; empty subtrees may not manufacture visible regions.
        var populated = new HashSet<String>();
        for (var seed : this.seeds) {
            if (seed.directMembers().isEmpty()) continue;
            var cursor = seed;
            while (populated.add(cursor.id()) && cursor.parentId().isPresent()) {
                cursor = byId.get(cursor.parentId().orElseThrow());
            }
        }
        if (populated.size() != byId.size()) throw new IllegalArgumentException("empty branch subtree");
        children.replaceAll((id, values) -> values.stream().sorted(ORDER).toList());
        this.byId = Collections.unmodifiableMap(byId);
        this.owners = Collections.unmodifiableMap(owners);
        this.children = Collections.unmodifiableMap(children);
        this.depths = Collections.unmodifiableMap(depths);
        this.roots = this.seeds.stream().filter(s -> s.parentId().isEmpty()).sorted(ORDER).toList();
    }

    public static List<Seed> canonicalSeeds(List<Seed> seeds) {
        if (seeds.size() > MAX_REGIONS) throw new IllegalArgumentException("branch count budget exceeded");
        long memberships = seeds.stream().mapToLong(s -> s.directMembers().size()).sum();
        if (memberships > 4096) throw new IllegalArgumentException("branch membership budget exceeded");
        return seeds.stream().sorted(Comparator.comparing(Seed::id)).toList();
    }

    public static ResearchTreeBranchStructure resolve(ResearchTreeSceneInput input) {
        var t = input.topology();
        var permitted = new HashSet<>(t.nodes().stream().map(p -> p.nodeId()).toList());
        if (!t.branchSeeds().isEmpty()) return new ResearchTreeBranchStructure(t.branchSeeds(), permitted);
        return new ResearchTreeBranchStructure(infer(input), permitted);
    }

    private static List<Seed> infer(ResearchTreeSceneInput input) {
        var nodes = input.topology().nodes();
        if (nodes.isEmpty()) return List.of();
        var hints = nodes.stream().flatMap(p -> p.automaticBranch().stream()).toList();
        boolean coherent = !hints.isEmpty() && hints.stream().allMatch(h ->
                h.familyStartIndex() == hints.get(0).familyStartIndex()
                        && h.transitionEndIndex() == hints.get(0).transitionEndIndex());
        if (input.topology().domain() == Domain.WEAPONS && coherent) {
            Map<Integer, List<ResourceLocation>> families = new TreeMap<>();
            var shared = new ArrayList<ResourceLocation>();
            for (var node : nodes) {
                var hint = node.automaticBranch();
                if (hint.isPresent() && hint.orElseThrow().rankIndex() > hint.orElseThrow().transitionEndIndex()) {
                    families.computeIfAbsent(hint.orElseThrow().branchIndex(), ignored -> new ArrayList<>()).add(node.nodeId());
                } else shared.add(node.nodeId());
            }
            if (families.size() > 1) {
                var seeds = new ArrayList<Seed>();
                seeds.add(new Seed("automatic/shared", Optional.empty(), 0, shared));
                families.forEach((index, members) -> seeds.add(new Seed("automatic/family/" + index,
                        Optional.of("automatic/shared"), index, members)));
                return seeds;
            }
        }
        // No invented classification branches. Give each weak component a neutral region.
        Map<ResourceLocation, List<ResourceLocation>> adjacent = new HashMap<>();
        nodes.forEach(n -> adjacent.put(n.nodeId(), new ArrayList<>()));
        input.topology().edges().forEach(e -> {
            adjacent.get(e.prerequisiteId()).add(e.dependentId());
            adjacent.get(e.dependentId()).add(e.prerequisiteId());
        });
        var seen = new HashSet<ResourceLocation>();
        var result = new ArrayList<Seed>();
        for (var node : nodes.stream().sorted(NODE_ORDER).toList()) {
            if (!seen.add(node.nodeId())) continue;
            var queue = new ArrayDeque<ResourceLocation>();
            var members = new ArrayList<ResourceLocation>();
            queue.add(node.nodeId());
            while (!queue.isEmpty()) {
                var member = queue.removeFirst();
                members.add(member);
                for (var next : adjacent.get(member)) if (seen.add(next)) queue.addLast(next);
            }
            String minimum = members.stream().map(Object::toString).min(String::compareTo).orElseThrow();
            result.add(new Seed("component/" + minimum, Optional.empty(), result.size(), members));
        }
        return result;
    }

    public List<Seed> seeds() { return seeds; }
    public List<Seed> roots() { return roots; }
    public Seed seed(String id) { return byId.get(id); }
    public List<Seed> children(String id) { return children.getOrDefault(id, List.of()); }
    public String owner(ResourceLocation id) { return owners.get(id); }
    public int depth(String id) { return depths.get(id); }
    public boolean ancestor(String ancestor, String descendant) {
        for (var cursor = seed(descendant); cursor.parentId().isPresent();) {
            cursor = seed(cursor.parentId().orElseThrow());
            if (cursor.id().equals(ancestor)) return true;
        }
        return false;
    }
}
