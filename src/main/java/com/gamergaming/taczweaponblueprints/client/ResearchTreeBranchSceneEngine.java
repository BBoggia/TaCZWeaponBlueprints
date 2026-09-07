package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import net.minecraft.resources.ResourceLocation;

/** Measures a containment forest bottom-up and places it top-down, without a later compaction pass. */
public final class ResearchTreeBranchSceneEngine implements ResearchTreeSceneEngine {
    public static final String REVISION = "branches-v2";
    @Override public String revision() { return REVISION; }
    @Override public boolean requiresGroupedRouting() { return true; }

    @Override public ResearchTreeScene layout(ResearchTreeSceneInput input) {
        var budget = new ResearchTreeSceneRouter.Budget();
        return withSpacingRetries(retry -> candidate(input, retry, budget));
    }

    static ResearchTreeScene candidate(ResearchTreeSceneInput input, int retry, ResearchTreeSceneRouter.Budget budget) {
        if (retry < 0 || retry > 2) throw new IllegalArgumentException("invalid spacing retry");
        return new Work(input, retry, budget).layout();
    }

    static ResearchTreeScene withSpacingRetries(java.util.function.IntFunction<ResearchTreeScene> attempt) {
        ResearchTreeSceneRouter.CapacityException last = null;
        for (int retry = 0; retry <= 2; retry++) {
            try { return attempt.apply(retry); }
            catch (ResearchTreeSceneRouter.CapacityException failure) { last = failure; }
        }
        throw new IllegalArgumentException("group routing exhausted initial allocation and two spacing retries", last);
    }

    private static final class Measure {
        final Map<Integer, List<ResearchTechTreeProjection.Placement>> rows = new TreeMap<>();
        int minimum = Integer.MAX_VALUE;
        int maximum = -1;
        double width;
        double ownedStrip;
        double childrenWidth;
        double x;
    }

    private static final class Work {
        final ResearchTreeSceneInput input;
        final ResearchTreeBranchStructure forest;
        final ResearchTreeScenePolicy.NodeMetrics nodeMetrics;
        final ResearchTreeScenePolicy.RegionMetrics regionMetrics;
        final ResearchTreeRoutingPlan routingPlan;
        final int retry;
        final ResearchTreeSceneRouter.Budget budget;
        final Map<ResourceLocation, ResearchTechTreeProjection.Placement> placements = new HashMap<>();
        final Map<ResourceLocation, Integer> groups = new HashMap<>();
        final Map<ResourceLocation, Integer> portals = new HashMap<>();
        final Map<String, Measure> measured = new LinkedHashMap<>();
        final Map<Integer, ResearchTreeScene.OccupiedRank> ranks = new TreeMap<>();
        final Map<ResourceLocation, ResearchTreeScene.Node> nodes = new HashMap<>();
        final Map<String, ResearchTreeScene.Region> regions = new HashMap<>();
        final Map<ResourceLocation, List<ResearchTreeScene.Bounds>> boundaryBounds = new HashMap<>();

        Work(ResearchTreeSceneInput input, int retry, ResearchTreeSceneRouter.Budget budget) {
            this.input = input;
            this.retry = retry;
            this.budget = budget;
            routingPlan = new ResearchTreeRoutingPlan(input);
            forest = ResearchTreeBranchStructure.resolve(input);
            nodeMetrics = input.policy().nodeMetrics();
            regionMetrics = input.policy().regionMetrics();
            input.topology().nodes().forEach(p -> placements.put(p.nodeId(), p));
            input.topology().groups().forEach(g -> groups.merge(g.id().dependentId(), 1, Integer::sum));
            input.topology().boundaries().stream().map(ResearchTreeSceneGeometry::portalKey)
                    .distinct().forEach(t -> portals.merge(t.local(), 1, Integer::sum));
        }

        ResearchTreeScene layout() {
            measure();
            occupiedRanks();
            double x = input.policy().spacing().canvasPadding();
            for (var root : forest.roots()) {
                place(root, x);
                x = bounded(x + measured.get(root.id()).width + regionMetrics.gap());
            }
            var orderedNodes = input.topology().nodes().stream().map(p -> nodes.get(p.nodeId())).toList();
            var boundaries = ResearchTreeSceneGeometry.outsideBoundaries(input, orderedNodes);
            for (var boundary : boundaries) boundaryBounds.computeIfAbsent(boundary.links().get(0).localNodeId(),
                    ignored -> new ArrayList<>()).add(boundary.bounds());
            var junctions = routingPlan.junctions(orderedNodes);
            for (var junction : junctions) boundaryBounds.computeIfAbsent(junction.group().id().dependentId(),
                    ignored -> new ArrayList<>()).add(junction.bounds());
            for (var seed : bottomUp()) finishRegion(seed);
            var orderedRegions = forest.seeds().stream().map(s -> regions.get(s.id())).toList();
            var routing = ResearchTreeSceneRouter.route(input, routingPlan, orderedNodes, junctions, boundaries, orderedRegions, retry, budget);
            var requirements = junctions.stream().map(j -> new ResearchTreeScene.Requirement(j.group(), Optional.of(
                    new ResearchTreeScene.Point(j.bounds().centerX(), j.bounds().y() + j.bounds().height() / 2)))).toList();
            var base = ResearchTreeSceneGeometry.assemble(input, REVISION, orderedNodes, requirements,
                    ResearchTreeRoutingValidator.canonicalRoutes(input, routing), boundaries);
            var extent = base.extent();
            for (var region : orderedRegions) extent = extent.union(region.bounds());
            for (var junction : junctions) extent = extent.union(junction.bounds());
            for (var segment : new ResearchTreeRoutingIndex(routing).segments()) extent = extent.union(segment.bounds());
            return new ResearchTreeScene(base.key(), extent, base.nodes(), base.requirements(), base.routes(),
                    base.boundaries(), base.bands(), orderedRegions, List.copyOf(ranks.values()), Optional.of(routing));
        }

        private List<ResearchTreeBranchStructure.Seed> bottomUp() {
            return forest.seeds().stream().sorted(Comparator
                    .comparingInt((ResearchTreeBranchStructure.Seed s) -> forest.depth(s.id())).reversed()
                    .thenComparing(ResearchTreeBranchStructure.Seed::id)).toList();
        }

        private double side() { return regionMetrics.padding() + routingPlan.gutter(input, forest.seeds().size(), retry); }

        private double slotWidth(ResearchTechTreeProjection.Placement node) {
            double paint = nodeMetrics.width() + 2 * nodeMetrics.paintPadding();
            int targets = portals.getOrDefault(node.nodeId(), 0);
            // Markers currently sit to the right. Reserve both halves around the centered body.
            double markerEnvelope = targets == 0 ? paint : paint + 2 * (12 + targets * 16);
            return Math.max(markerEnvelope, Math.max(routingPlan.width(node.nodeId()),
                    groups.getOrDefault(node.nodeId(), 0) * (double) regionMetrics.junctionSpacing()));
        }

        private double rowWidth(List<ResearchTechTreeProjection.Placement> row) {
            return row.stream().mapToDouble(this::slotWidth).sum()
                    + Math.max(0, row.size() - 1) * (double) input.policy().spacing().nodeGap();
        }

        private boolean childActive(ResearchTreeBranchStructure.Seed seed, int rank) {
            return forest.children(seed.id()).stream().map(s -> measured.get(s.id()))
                    .anyMatch(m -> rank >= m.minimum && rank <= m.maximum);
        }

        private void measure() {
            for (var seed : bottomUp()) {
                var m = new Measure();
                measured.put(seed.id(), m);
                for (var id : seed.directMembers()) {
                    var p = placements.get(id);
                    m.rows.computeIfAbsent(p.rank(), ignored -> new ArrayList<>()).add(p);
                    m.minimum = Math.min(m.minimum, p.rank());
                    m.maximum = Math.max(m.maximum, p.rank());
                }
                m.rows.replaceAll((rank, members) -> members.stream().sorted(ResearchTreeBranchStructure.NODE_ORDER).toList());
                for (var child : forest.children(seed.id())) {
                    var c = measured.get(child.id());
                    m.minimum = Math.min(m.minimum, c.minimum);
                    m.maximum = Math.max(m.maximum, c.maximum);
                    m.childrenWidth += c.width;
                }
                m.childrenWidth += Math.max(0, forest.children(seed.id()).size() - 1) * (double) regionMetrics.gap();
                double directWidth = 0;
                for (var row : m.rows.entrySet()) {
                    double width = rowWidth(row.getValue());
                    directWidth = Math.max(directWidth, width);
                    if (childActive(seed, row.getKey())) m.ownedStrip = Math.max(m.ownedStrip, width);
                }
                double childrenAndOwned = m.childrenWidth + (m.ownedStrip == 0 ? 0 : m.ownedStrip + regionMetrics.gap());
                m.width = bounded(2 * side() + Math.max(regionMetrics.labelWidth(), Math.max(directWidth, childrenAndOwned)));
            }
        }

        private void occupiedRanks() {
            var rows = input.topology().nodes().stream().collect(Collectors.groupingBy(
                    ResearchTechTreeProjection.Placement::rank, TreeMap::new, Collectors.toList()));
            double cursor = input.policy().spacing().canvasPadding();
            Set<ResourceLocation> previousBands = null;
            for (var entry : rows.descendingMap().entrySet()) {
                var members = entry.getValue();
                int depth = members.stream().mapToInt(p -> forest.depth(forest.owner(p.nodeId()))).max().orElse(1);
                int groupCount = members.stream().mapToInt(p -> groups.getOrDefault(p.nodeId(), 0)).max().orElse(0);
                int portalCount = members.stream().mapToInt(p -> portals.getOrDefault(p.nodeId(), 0)).max().orElse(0);
                var bands = members.stream().flatMap(p -> p.bandId().stream()).collect(Collectors.toSet());
                if (previousBands != null && !previousBands.equals(bands)) {
                    cursor = bounded(cursor + input.policy().spacing().tierGap());
                }
                double above = depth * (double) (regionMetrics.padding() + regionMetrics.headerHeight())
                        + nodeMetrics.paintPadding() + 4
                        + routingPlan.track * (6 + routingPlan.above.getOrDefault(entry.getKey(), 0));
                double below = depth * (double) regionMetrics.padding() + nodeMetrics.paintPadding()
                        + Math.max(groupCount * (double) regionMetrics.junctionSpacing(), portalCount > 0 ? 16 : 0)
                        + Math.max(8, input.policy().spacing().sameTierStepGap())
                        + members.stream().mapToDouble(p -> routingPlan.height(p.nodeId())).max().orElse(0)
                        + routingPlan.track * routingPlan.below.getOrDefault(entry.getKey(), 0);
                double y = bounded(cursor + above);
                ranks.put(entry.getKey(), new ResearchTreeScene.OccupiedRank(entry.getKey(), y, above, below));
                cursor = bounded(y + nodeMetrics.height() + below);
                previousBands = bands;
            }
        }

        private void place(ResearchTreeBranchStructure.Seed seed, double x) {
            var m = measured.get(seed.id());
            m.x = x;
            for (var row : m.rows.entrySet()) {
                double area = childActive(seed, row.getKey()) ? m.ownedStrip : m.width - 2 * side();
                double column = x + side() + (area - rowWidth(row.getValue())) / 2;
                for (var p : row.getValue()) {
                    double slot = slotWidth(p);
                    nodes.put(p.nodeId(), ResearchTreeSceneGeometry.node(p.nodeId(),
                            column + (slot - nodeMetrics.width()) / 2, ranks.get(p.rank()).bodyY(), nodeMetrics));
                    column += slot + input.policy().spacing().nodeGap();
                }
            }
            double occupied = m.ownedStrip == 0 ? 0 : m.ownedStrip + regionMetrics.gap();
            double childX = x + side() + occupied + (m.width - 2 * side() - occupied - m.childrenWidth) / 2;
            for (var child : forest.children(seed.id())) {
                place(child, childX);
                childX += measured.get(child.id()).width + regionMetrics.gap();
            }
        }

        private void finishRegion(ResearchTreeBranchStructure.Seed seed) {
            var m = measured.get(seed.id());
            double top = Double.POSITIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY;
            for (var id : seed.directMembers()) {
                var paint = nodes.get(id).paint();
                top = Math.min(top, routingPlan.contentTop(nodes.get(id))); bottom = Math.max(bottom, paint.bottom());
                for (var boundary : boundaryBounds.getOrDefault(id, List.of())) {
                    top = Math.min(top, boundary.y()); bottom = Math.max(bottom, boundary.bottom());
                }
            }
            for (var child : forest.children(seed.id())) {
                var bounds = regions.get(child.id()).bounds();
                top = Math.min(top, bounds.y()); bottom = Math.max(bottom, bounds.bottom());
            }
            top -= regionMetrics.padding() + regionMetrics.headerHeight();
            bottom += regionMetrics.padding();
            var bounds = new ResearchTreeScene.Bounds(m.x, top, m.width, bottom - top);
            regions.put(seed.id(), new ResearchTreeScene.Region(seed, bounds, m.minimum, m.maximum));
        }

        private double bounded(double value) {
            if (!Double.isFinite(value) || value < 0 || value > ResearchTreeScenePolicy.MAX_DIMENSION) {
                throw new IllegalArgumentException("branch allocation exceeds the scene coordinate budget");
            }
            return value;
        }
    }
}
