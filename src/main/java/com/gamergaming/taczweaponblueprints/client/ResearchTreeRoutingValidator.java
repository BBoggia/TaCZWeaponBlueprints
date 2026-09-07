package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

/** Independent final checks for grouped connection membership, clearance and crossing declarations. */
final class ResearchTreeRoutingValidator {
    private ResearchTreeRoutingValidator() { }

    static void validate(ResearchTreeSceneInput input, ResearchTreeScene scene) {
        var routing = scene.routing().orElseThrow(() -> new IllegalArgumentException("group routing is absent"));
        var plan = new ResearchTreeRoutingPlan(input);
        require(plan.junctions(scene.nodes()).equals(routing.junctions()), "group marker footprint or ports changed");
        var expected = new HashSet<ResearchTreeRouting.Owner>();
        for (var group : input.topology().groups()) {
            expected.add(ResearchTreeRouting.Owner.trunk(group.id()));
            group.alternatives().forEach(id -> expected.add(ResearchTreeRouting.Owner.alternative(group.id(), id)));
        }
        for (int i = 0; i < scene.boundaries().size(); i++) expected.add(ResearchTreeRouting.Owner.boundary(i));
        var actual = new HashSet<ResearchTreeRouting.Owner>();
        var nodes = new HashMap<net.minecraft.resources.ResourceLocation, ResearchTreeScene.Node>();
        scene.nodes().forEach(n -> nodes.put(n.id(), n));
        var groups = new HashMap<ResearchTreeSceneInput.GroupId, ResearchTreeRouting.Junction>();
        routing.junctions().forEach(j -> groups.put(j.group().id(), j));
        var headers = plan.headers(input, scene.regions(), routing.statistics().spacingRetries());
        for (int i = 0; i < headers.size(); i++) require(scene.regions().get(i).bounds().contains(headers.get(i)),
                "region label is outside its reserved envelope");
        var obstacles = new Obstacles(scene.nodes(), routing.junctions(), scene.boundaries(), headers);
        obstacles.checkOverlap();
        var budget = new ResearchTreeSceneRouter.Budget();
        var reservations = new Reservations();
        for (var c : routing.connections()) {
            require(actual.add(c.owner()) && expected.contains(c.owner()), "duplicate or unexpected grouped connection");
            var first = c.points().get(0); var last = c.points().get(c.points().size() - 1);
            switch (c.owner().kind()) {
                case ALTERNATIVE -> {
                    var j = groups.get(c.owner().group().orElseThrow());
                    var id = c.owner().prerequisite().orElseThrow();
                    var node = nodes.get(id);
                    int ordinal = j.group().alternatives().indexOf(id);
                    require(ordinal >= 0 && last.equals(j.inputPorts().get(ordinal)), "alternative is attached to the wrong OR port");
                    require(first.y() == node.body().y() && node.body().contains(first)
                            && c.points().get(1).x() == first.x() && c.points().get(1).y() < first.y(), "invalid prerequisite output port");
                    require(c.points().get(c.points().size() - 2).x() == last.x()
                            && c.points().get(c.points().size() - 2).y() > last.y(), "invalid group input approach");
                }
                case GROUP_TRUNK -> {
                    var j = groups.get(c.owner().group().orElseThrow());
                    var node = nodes.get(j.group().id().dependentId());
                    require(first.equals(j.outputPort()) && c.points().get(1).x() == first.x()
                            && c.points().get(1).y() < first.y(), "invalid group trunk exit");
                    require(last.y() == node.body().bottom() && node.body().contains(last)
                            && c.points().get(c.points().size() - 2).x() == last.x()
                            && c.points().get(c.points().size() - 2).y() > last.y(), "invalid dependent input port");
                }
                case BOUNDARY -> {
                    var b = scene.boundaries().get(c.owner().boundaryIndex());
                    var n = nodes.get(b.links().get(0).localNodeId());
                    require(n.body().contains(first) && first.x() == n.body().right()
                            && c.points().get(1).y() == first.y() && c.points().get(1).x() > first.x(), "invalid boundary node port");
                    require(b.bounds().contains(last) && last.y() == b.bounds().y()
                            && c.points().get(c.points().size() - 2).x() == last.x()
                            && c.points().get(c.points().size() - 2).y() < last.y(), "invalid boundary marker port");
                }
            }
            require(obstacles.clear(c, budget), "connector intersects node, marker, or junction paint");
            require(reservations.clear(c, budget), "unrelated collinear connections overlap");
            reservations.add(c);
        }
        require(actual.equals(expected), "grouped connections omit a dependency");
        require(canonicalRoutes(input, routing).equals(scene.routes()), "legacy edge paths disagree with the grouped network");
        for (var r : scene.requirements()) {
            var b = groups.get(r.group().id()).bounds();
            require(r.junction().equals(java.util.Optional.of(new ResearchTreeScene.Point(b.centerX(), b.y() + b.height() / 2))),
                    "requirement junction disagrees with its group marker");
        }
        for (var j : routing.junctions()) require(scene.extent().contains(j.bounds()), "group marker outside scene extent");
        var index = new ResearchTreeRoutingIndex(routing);
        for (var segment : index.segments()) require(scene.extent().contains(segment.bounds()), "connector ink outside scene extent");
        require(crossings(routing.connections(), budget).equals(routing.crossings()), "crossings are missing, invented, or reordered");
        require(routing.statistics().bends() == routing.connections().stream().mapToInt(c -> c.points().size() - 2).sum(),
                "routing bend count mismatch");
    }

    /** Compatibility access only: the first group occurrence represents each edge; rendering uses connections. */
    static List<ResearchTreeScene.Route> canonicalRoutes(ResearchTreeSceneInput input, ResearchTreeRouting routing) {
        var connections = new HashMap<ResearchTreeRouting.Owner, ResearchTreeRouting.Connection>();
        routing.connections().forEach(c -> connections.put(c.owner(), c));
        var paths = new LinkedHashMap<ResearchTreeGraph.Edge, List<ResearchTreeScene.Point>>();
        for (var j : routing.junctions()) {
            var trunk = connections.get(ResearchTreeRouting.Owner.trunk(j.group().id())).points();
            for (var id : j.group().alternatives()) {
                var edge = new ResearchTreeGraph.Edge(id, j.group().id().dependentId());
                var branch = connections.get(ResearchTreeRouting.Owner.alternative(j.group().id(), id)).points();
                var path = new ArrayList<>(branch);
                path.add(new ResearchTreeScene.Point(branch.get(branch.size() - 1).x(), j.bounds().y()));
                path.addAll(trunk);
                paths.putIfAbsent(edge, ResearchTreeSceneRouter.compact(path));
            }
        }
        return input.topology().edges().stream().map(e -> new ResearchTreeScene.Route(e, paths.get(e))).toList();
    }

    record Obstacle(Object key, ResearchTreeScene.Bounds bounds) { }
    static final class Obstacles {
        final List<Obstacle> values = new ArrayList<>();
        final Map<Integer, Object> boundaryNodes = new HashMap<>();
        final ResearchTreeRoutingIndex.BoundsIndex<Obstacle> index;
        Obstacles(List<ResearchTreeScene.Node> nodes, List<ResearchTreeRouting.Junction> junctions,
                List<ResearchTreeScene.Boundary> boundaries, List<ResearchTreeScene.Bounds> headers) {
            nodes.forEach(n -> values.add(new Obstacle(n.id(), n.paint())));
            junctions.forEach(j -> values.add(new Obstacle(j.group().id(), j.bounds())));
            for (int i = 0; i < boundaries.size(); i++) {
                values.add(new Obstacle(i, boundaries.get(i).bounds()));
                boundaryNodes.put(i, boundaries.get(i).links().get(0).localNodeId());
            }
            for (int i = 0; i < headers.size(); i++) values.add(new Obstacle("header/" + i, headers.get(i)));
            index = new ResearchTreeRoutingIndex.BoundsIndex<>(values.stream()
                    .map(o -> new ResearchTreeRoutingIndex.Entry<>(o.bounds(), o)).toList());
        }
        void checkOverlap() {
            for (var obstacle : values) {
                var hits = new ArrayList<Obstacle>(); index.query(obstacle.bounds(), hits);
                for (var other : hits) require(other == obstacle || !positiveOverlap(obstacle.bounds(), other.bounds()),
                        "node, marker, or junction footprints overlap");
            }
        }
        boolean clear(ResearchTreeRouting.Connection connection, ResearchTreeSceneRouter.Budget budget) {
            var points = connection.points();
            for (int i = 1; i < points.size(); i++) {
                var hits = new ArrayList<Obstacle>();
                index.query(ResearchTreeRoutingIndex.bounds(points.get(i - 1), points.get(i)).inflate(ResearchTreeRouting.INK_PADDING), hits);
                budget.use(1L + hits.size());
                for (var hit : hits) {
                    Object first, last;
                    var owner = connection.owner();
                    switch (owner.kind()) {
                        case ALTERNATIVE -> { first = owner.prerequisite().orElseThrow(); last = owner.group().orElseThrow(); }
                        case GROUP_TRUNK -> { first = owner.group().orElseThrow(); last = owner.group().orElseThrow().dependentId(); }
                        case BOUNDARY -> { first = boundaryNodes.get(owner.boundaryIndex()); last = owner.boundaryIndex(); }
                        default -> throw new IllegalStateException();
                    }
                    if (i == 1 && hit.key().equals(first) || i == points.size() - 1 && hit.key().equals(last)) continue;
                    return false;
                }
            }
            return true;
        }
    }

    static final class Reservations {
        private record Reserved(ResearchTreeRouting.Owner owner, ResearchTreeScene.Bounds bounds) { }
        private final Map<Double, List<Reserved>> horizontal = new HashMap<>(), vertical = new HashMap<>();
        boolean clear(ResearchTreeRouting.Connection connection, ResearchTreeSceneRouter.Budget budget) {
            var points = connection.points();
            for (int i = 1; i < points.size(); i++) {
                var a = points.get(i - 1); var b = points.get(i);
                boolean h = a.y() == b.y(); var bounds = ResearchTreeRoutingIndex.bounds(a, b);
                var occupied = (h ? horizontal : vertical).getOrDefault(h ? a.y() : a.x(), List.of());
                budget.use(1L + occupied.size());
                for (var existing : occupied) if (!existing.owner().equals(connection.owner())
                        && ResearchTreeRoutingIndex.intersects(bounds, existing.bounds())) return false;
            }
            return true;
        }
        void add(ResearchTreeRouting.Connection connection) {
            var points = connection.points();
            for (int i = 1; i < points.size(); i++) {
                var a = points.get(i - 1); var b = points.get(i); boolean h = a.y() == b.y();
                (h ? horizontal : vertical).computeIfAbsent(h ? a.y() : a.x(), ignored -> new ArrayList<>())
                        .add(new Reserved(connection.owner(), ResearchTreeRoutingIndex.bounds(a, b)));
            }
        }
    }

    static List<ResearchTreeRouting.Crossing> crossings(List<ResearchTreeRouting.Connection> connections, ResearchTreeSceneRouter.Budget budget) {
        record Segment(int connection, int ordinal, ResearchTreeScene.Bounds bounds) { }
        var horizontal = new ArrayList<Segment>(); var vertical = new ArrayList<Segment>();
        for (int c = 0; c < connections.size(); c++) {
            var points = connections.get(c).points();
            for (int p = 1; p < points.size(); p++) {
                var a = points.get(p - 1); var b = points.get(p);
                (a.y() == b.y() ? horizontal : vertical).add(new Segment(c, p - 1, ResearchTreeRoutingIndex.bounds(a, b)));
            }
        }
        var index = new ResearchTreeRoutingIndex.BoundsIndex<>(vertical.stream()
                .map(v -> new ResearchTreeRoutingIndex.Entry<>(v.bounds(), v)).toList());
        var result = new ArrayList<ResearchTreeRouting.Crossing>();
        for (var h : horizontal) {
            var hits = new ArrayList<Segment>(); index.query(h.bounds(), hits); budget.use(1L + hits.size());
            for (var v : hits) {
                if (h.connection() == v.connection()) {
                    require(Math.abs(h.ordinal() - v.ordinal()) == 1, "self-crossing connector");
                    continue;
                }
                if (result.size() >= ResearchTreeRouting.MAX_CROSSINGS) throw new IllegalArgumentException("crossing budget exhausted");
                result.add(new ResearchTreeRouting.Crossing(new ResearchTreeScene.Point(v.bounds().x(), h.bounds().y()),
                        h.connection(), h.ordinal(), v.connection(), v.ordinal()));
            }
        }
        result.sort(Comparator.comparingInt(ResearchTreeRouting.Crossing::horizontalConnection)
                .thenComparingInt(ResearchTreeRouting.Crossing::horizontalSegment)
                .thenComparingInt(ResearchTreeRouting.Crossing::verticalConnection)
                .thenComparingInt(ResearchTreeRouting.Crossing::verticalSegment));
        return List.copyOf(result);
    }

    private static boolean positiveOverlap(ResearchTreeScene.Bounds a, ResearchTreeScene.Bounds b) {
        return a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y();
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
