package com.gamergaming.taczweaponblueprints.client;

import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import net.minecraft.resources.ResourceLocation;

/** Geometry only. A candidate must pass ResearchTreeSceneValidator before publication. */
public record ResearchTreeScene(ResearchTreeSceneInput.Key key, Bounds extent,
        List<Node> nodes, List<Requirement> requirements, List<Route> routes,
        List<Boundary> boundaries, List<Band> bands, List<Region> regions, List<OccupiedRank> occupiedRanks,
        Optional<ResearchTreeRouting> routing) {
    public ResearchTreeScene(ResearchTreeSceneInput.Key key, Bounds extent, List<Node> nodes,
            List<Requirement> requirements, List<Route> routes, List<Boundary> boundaries, List<Band> bands,
            List<Region> regions, List<OccupiedRank> occupiedRanks) {
        this(key, extent, nodes, requirements, routes, boundaries, bands, regions, occupiedRanks, Optional.empty());
    }
    public ResearchTreeScene(ResearchTreeSceneInput.Key key, Bounds extent, List<Node> nodes,
            List<Requirement> requirements, List<Route> routes, List<Boundary> boundaries, List<Band> bands) {
        this(key, extent, nodes, requirements, routes, boundaries, bands, List.of(), List.of());
    }
    public ResearchTreeScene {
        java.util.Objects.requireNonNull(key);
        java.util.Objects.requireNonNull(extent);
        java.util.Objects.requireNonNull(routing);
        requireCount(nodes.size(), ResearchTreeGraph.MAX_NODES);
        requireCount(requirements.size(), ResearchTreeGraph.MAX_REQUIREMENT_GROUPS);
        requireCount(routes.size(), ResearchTreeGraph.MAX_EDGES);
        requireCount(boundaries.size(), ResearchTreeGraph.MAX_EDGES);
        requireCount(bands.size(), 32);
        requireCount(regions.size(), ResearchTreeBranchStructure.MAX_REGIONS);
        requireCount(occupiedRanks.size(), ResearchTreeGraph.MAX_NODES);
        nodes = List.copyOf(nodes);
        requirements = List.copyOf(requirements);
        routes = List.copyOf(routes);
        boundaries = List.copyOf(boundaries);
        bands = List.copyOf(bands);
        regions = List.copyOf(regions);
        occupiedRanks = List.copyOf(occupiedRanks);
        long points = routes.stream().mapToLong(r -> r.points().size()).sum();
        long links = boundaries.stream().mapToLong(b -> b.links().size()).sum();
        if (points > ResearchTreeScenePolicy.MAX_TOTAL_ROUTE_POINTS || links > ResearchTreeGraph.MAX_EDGES) {
            throw new IllegalArgumentException("scene route work budget exceeded");
        }
    }

    private static void requireCount(int actual, int maximum) {
        if (actual > maximum) throw new IllegalArgumentException("scene count budget exceeded");
    }

    public record Point(double x, double y) {
        public Point { coordinate(x); coordinate(y); }
    }

    // Only derived containment/gap arithmetic gets tolerance. Identity, metrics and topology stay exact.
    // This is far below a painted unit, but covers accumulated rounding within the coordinate budget.
    static boolean atMost(double a, double b) { return a <= b + 0.000001; }
    static boolean close(double a, double b) { return atMost(a, b) && atMost(b, a); }

    /** Coordinates are logical canvas units; empty extents are permitted for empty domains. */
    public record Bounds(double x, double y, double width, double height) {
        public Bounds {
            coordinate(x); coordinate(y); coordinate(width); coordinate(height);
            coordinate(x + width); coordinate(y + height);
            if (width < 0 || height < 0) throw new IllegalArgumentException("negative scene bounds");
        }
        public double right() { return x + width; }
        public double bottom() { return y + height; }
        public double centerX() { return x + width / 2; }
        public double centerY() { return y + height / 2; }
        public boolean contains(Point p) {
            return atMost(x, p.x) && atMost(p.x, right()) && atMost(y, p.y) && atMost(p.y, bottom());
        }
        public boolean contains(Bounds other) {
            return atMost(x, other.x) && atMost(y, other.y)
                    && atMost(other.right(), right()) && atMost(other.bottom(), bottom());
        }
        public Bounds inflate(double padding) {
            if (!Double.isFinite(padding) || padding < 0) throw new IllegalArgumentException("invalid padding");
            return new Bounds(x - padding, y - padding, width + 2 * padding, height + 2 * padding);
        }
        public Bounds union(Bounds other) {
            double left = Math.min(x, other.x), top = Math.min(y, other.y);
            return new Bounds(left, top, Math.max(right(), other.right()) - left,
                    Math.max(bottom(), other.bottom()) - top);
        }
    }

    private static void coordinate(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > ResearchTreeScenePolicy.MAX_DIMENSION) {
            throw new IllegalArgumentException("non-finite or out-of-range scene coordinate");
        }
    }

    public record Node(ResourceLocation id, Bounds body, Bounds paint) {
        public Node {
            if (id == null || body == null || paint == null || body.width <= 0 || body.height <= 0
                    || !paint.contains(body)) throw new IllegalArgumentException("invalid scene node bounds");
        }
        /** Hit padding is a view decision and is never included in the reusable geometry key. */
        public Bounds hitBounds(double padding) { return body.inflate(padding); }
    }
    public record Requirement(ResearchTreeSceneInput.Group group, Optional<Point> junction) {
        public Requirement { java.util.Objects.requireNonNull(group); java.util.Objects.requireNonNull(junction); }
    }
    public record Route(ResearchTreeGraph.Edge edge, List<Point> points) {
        public Route {
            java.util.Objects.requireNonNull(edge);
            if (points.size() < 2 || points.size() > ResearchTreeScenePolicy.MAX_ROUTE_POINTS) {
                throw new IllegalArgumentException("invalid scene route point count");
            }
            points = List.copyOf(points);
        }
    }
    public record Boundary(List<ResearchTechTreeProjection.BoundaryLink> links, Bounds bounds) {
        public Boundary {
            if (links.isEmpty() || links.size() > ResearchTreeGraph.MAX_EDGES || bounds == null) {
                throw new IllegalArgumentException("invalid scene boundary");
            }
            links = List.copyOf(links);
        }
    }
    /** A reference to a current public band label, not cached label text or an icon. */
    public record Band(ResourceLocation id, Bounds bounds) {
        public Band { java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(bounds); }
    }

    public record Region(ResearchTreeBranchStructure.Seed seed, Bounds bounds, int minimumRank, int maximumRank) {
        public Region {
            if (seed == null || bounds == null || minimumRank < 0 || maximumRank < minimumRank
                    || maximumRank > 1_000_000) throw new IllegalArgumentException("invalid scene region");
        }
    }

    /** A shared occupied semantic rank; its body Y is never derived from the numeric rank distance. */
    public record OccupiedRank(int rank, double bodyY, double above, double below) {
        public OccupiedRank {
            coordinate(bodyY); coordinate(above); coordinate(below);
            if (rank < 0 || rank > 1_000_000 || above < 0 || below < 0) {
                throw new IllegalArgumentException("invalid occupied rank");
            }
        }
    }
}
