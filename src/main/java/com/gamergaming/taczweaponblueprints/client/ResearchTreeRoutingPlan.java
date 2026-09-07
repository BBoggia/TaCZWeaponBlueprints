package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/** Measures group markers and connector escape rows before branch allocation. */
final class ResearchTreeRoutingPlan {
    final Map<ResourceLocation, List<ResearchTreeSceneInput.Group>> groups = new HashMap<>();
    final Map<ResourceLocation, Integer> outgoing = new HashMap<>();
    final Map<Integer, Integer> above = new HashMap<>(), below = new HashMap<>();
    final Map<ResourceLocation, Integer> ranks = new HashMap<>();
    private final double gap;
    final double track;

    ResearchTreeRoutingPlan(ResearchTreeSceneInput input) {
        track = input.policy().regionMetrics().routingTrackSpacing();
        gap = Math.max(track, input.policy().regionMetrics().junctionSpacing() - 8);
        input.topology().nodes().forEach(p -> ranks.put(p.nodeId(), p.rank()));
        // One connector per aggregated marker; individual disclosed links have their own graph limit.
        long count = input.topology().groups().size() + input.topology().boundaries().stream()
                .map(ResearchTreeSceneGeometry::portalKey).distinct().count();
        for (var group : input.topology().groups()) {
            groups.computeIfAbsent(group.id().dependentId(), ignored -> new ArrayList<>()).add(group);
            for (var id : group.alternatives()) {
                count++;
                outgoing.merge(id, 1, Integer::sum);
                above.merge(ranks.get(id), 1, Integer::sum);
                below.merge(ranks.get(group.id().dependentId()), 1, Integer::sum);
            }
        }
        if (count > ResearchTreeRouting.MAX_CONNECTIONS) throw new IllegalArgumentException("group routing connection budget exceeded");
    }

    double markerWidth(ResearchTreeSceneInput.Group group) {
        return Math.max(group.hiddenCount() + group.externalCount() > 0 ? 18 : 8,
                (group.alternatives().size() + 1) * track);
    }
    double width(ResourceLocation node) {
        var row = groups.getOrDefault(node, List.of());
        return row.stream().mapToDouble(this::markerWidth).sum() + Math.max(0, row.size() - 1) * gap;
    }
    double height(ResourceLocation node) {
        return groups.containsKey(node) ? 12 + track * (groups.get(node).size() + 2) : 0;
    }
    double gutter(ResearchTreeSceneInput input, int regionCount, int retry) {
        int demand = Math.max(above.values().stream().mapToInt(Integer::intValue).max().orElse(0),
                below.values().stream().mapToInt(Integer::intValue).max().orElse(0));
        double measured = track * (1 + Math.ceil(demand / (2.0 * Math.max(1, regionCount))));
        return Math.max(input.policy().regionMetrics().routingGutter(), measured) * (1 << retry);
    }
    double contentTop(ResearchTreeScene.Node node) {
        // Put region labels above the whole rank's escape channel, including other owners' fan-out.
        return node.paint().y() - track * (7 + above.getOrDefault(ranks.get(node.id()), 0));
    }

    List<ResearchTreeScene.Bounds> headers(ResearchTreeSceneInput input, List<ResearchTreeScene.Region> regions, int retry) {
        var m = input.policy().regionMetrics();
        if (m.headerHeight() == 0 || m.labelWidth() == 0) return List.of();
        double side = m.padding() + gutter(input, regions.size(), retry);
        return regions.stream().map(r -> new ResearchTreeScene.Bounds(r.bounds().x() + side,
                r.bounds().y() + m.padding(), m.labelWidth(), m.headerHeight())).toList();
    }
    List<ResearchTreeRouting.Junction> junctions(List<ResearchTreeScene.Node> nodes) {
        var result = new ArrayList<ResearchTreeRouting.Junction>();
        for (var node : nodes) {
            double x = node.body().centerX() - width(node.id()) / 2;
            double y = node.paint().bottom() + height(node.id()) - 8;
            for (var group : groups.getOrDefault(node.id(), List.of())) {
                double width = markerWidth(group);
                var bounds = new ResearchTreeScene.Bounds(x, y, width, 8);
                var ports = new ArrayList<ResearchTreeScene.Point>();
                for (int i = 0; i < group.alternatives().size(); i++) ports.add(new ResearchTreeScene.Point(
                        x + width * (i + 1) / (group.alternatives().size() + 1), y + 8));
                result.add(new ResearchTreeRouting.Junction(group, bounds, ports, new ResearchTreeScene.Point(bounds.centerX(), y)));
                x += width + gap;
            }
        }
        return List.copyOf(result);
    }
}
