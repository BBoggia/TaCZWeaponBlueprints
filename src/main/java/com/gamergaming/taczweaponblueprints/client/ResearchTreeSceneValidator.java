package com.gamergaming.taczweaponblueprints.client;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/** Final structural validation shared by compatibility, fallback, and future scene engines. */
public final class ResearchTreeSceneValidator {
    private ResearchTreeSceneValidator() { }

    public static ResearchTreeScene validate(ResearchTreeSceneInput input, String engineRevision,
            ResearchTreeScene scene) {
        require(scene != null && scene.key().equals(input.key(engineRevision)), "scene input/revision mismatch");
        var topology = input.topology();
        var metrics = input.policy().nodeMetrics();
        Map<ResourceLocation, ResearchTreeScene.Node> nodes = new HashMap<>();
        for (var node : scene.nodes()) {
            require(nodes.put(node.id(), node) == null, "duplicate scene node");
            require(node.body().width() == metrics.width() && node.body().height() == metrics.height(),
                    "scene body does not match measured metrics");
            require(node.paint().equals(node.body().inflate(metrics.paintPadding())), "scene paint envelope mismatch");
            require(scene.extent().contains(node.paint()), "node outside scene extent");
        }
        require(nodes.keySet().equals(new HashSet<>(topology.nodes().stream().map(p -> p.nodeId()).toList())),
                "scene node membership mismatch");

        // At most 4096 nodes: bounded pair comparisons, with an early vertical separation exit.
        var ordered = scene.nodes().stream().sorted(Comparator.comparingDouble(n -> n.body().y())).toList();
        for (int i = 0; i < ordered.size(); i++) {
            var a = ordered.get(i).body();
            for (int j = i + 1; j < ordered.size(); j++) {
                var b = ordered.get(j).body();
                if (b.y() >= a.bottom()) break;
                require(a.right() <= b.x() || b.right() <= a.x(), "overlapping scene node bodies");
            }
        }

        Set<com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph.Edge> edges = new HashSet<>();
        for (var route : scene.routes()) {
            require(edges.add(route.edge()), "duplicate scene edge");
            var source = nodes.get(route.edge().prerequisiteId());
            var target = nodes.get(route.edge().dependentId());
            require(source != null && target != null, "route endpoint absent from scene");
            require(source.body().y() >= target.body().bottom(), "route contradicts bottom-to-top progression");
            require(source.paint().contains(route.points().get(0))
                    && target.paint().contains(route.points().get(route.points().size() - 1)),
                    "route is not attached to its blueprint endpoints");
            ResearchTreeScene.Point previous = null;
            for (var point : route.points()) {
                require(scene.extent().contains(point), "route outside scene extent");
                if (previous != null) require(previous.x() == point.x() || previous.y() == point.y(),
                        "non-orthogonal scene route");
                previous = point;
            }
        }
        require(edges.equals(new HashSet<>(topology.edges())), "scene edge membership mismatch");
        var groups = new HashMap<ResearchTreeSceneInput.GroupId, ResearchTreeSceneInput.Group>();
        for (var requirement : scene.requirements()) {
            require(groups.put(requirement.group().id(), requirement.group()) == null, "duplicate scene group");
            requirement.junction().ifPresent(p -> require(scene.extent().contains(
                    new ResearchTreeScene.Bounds(p.x() - 4, p.y() - 4, 8, 8)), "junction outside scene extent"));
        }
        require(groups.size() == topology.groups().size()
                && topology.groups().stream().allMatch(g -> g.equals(groups.get(g.id()))),
                "scene AND-of-OR requirements mismatch");
        var links = new HashSet<ResearchTechTreeProjection.BoundaryLink>();
        for (var boundary : scene.boundaries()) {
            require(scene.extent().contains(boundary.bounds()), "boundary outside scene extent");
            var first = boundary.links().get(0);
            for (var link : boundary.links()) {
                require(links.add(link), "duplicate scene boundary link");
                require(link.localNodeId().equals(first.localNodeId())
                        && link.remoteDomain() == first.remoteDomain() && link.direction() == first.direction(),
                        "unrelated boundary links aggregated");
            }
        }
        require(links.equals(new HashSet<>(topology.boundaries())), "scene boundary membership mismatch");
        Set<ResourceLocation> bands = new HashSet<>();
        for (var band : scene.bands()) {
            require(bands.add(band.id()), "duplicate scene band");
            require(scene.extent().contains(band.bounds()), "band outside scene extent");
        }
        require(bands.equals(new HashSet<>(topology.bands())), "scene band membership mismatch");
        if (nodes.isEmpty()) require(scene.extent().width() == 0 && scene.extent().height() == 0,
                "empty domain must have an empty extent");
        if (engineRevision.equals(ResearchTreeBranchSceneEngine.REVISION)
                || !scene.regions().isEmpty() || !scene.occupiedRanks().isEmpty()) {
            ResearchTreeBranchSceneValidator.validate(input, scene);
        }
        if (engineRevision.equals(ResearchTreeBranchSceneEngine.REVISION) || scene.routing().isPresent()) {
            ResearchTreeRoutingValidator.validate(input, scene);
        }
        return scene;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
