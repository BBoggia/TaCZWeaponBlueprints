package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;

import net.minecraft.resources.ResourceLocation;

/** Deterministic keyboard navigation over a laid-out research graph. */
public final class ResearchTreeNavigator {
    private ResearchTreeNavigator() {
    }

    public static Optional<ResourceLocation> move(
            ResearchTreeGraph graph,
            ResearchTreeLayout layout,
            ResourceLocation currentId,
            Direction direction) {
        if (graph == null || layout == null || direction == null || graph.nodes().isEmpty()) {
            return Optional.empty();
        }
        ResearchTreeLayout.PositionedNode current = layout.position(currentId)
                .orElseGet(() -> layout.nodes().isEmpty() ? null : layout.nodes().get(0));
        if (current == null) {
            return Optional.empty();
        }

        List<ResearchTreeLayout.PositionedNode> connected = connectedCandidates(
                graph, layout, current.blueprintId(), direction);
        List<ResearchTreeLayout.PositionedNode> candidates = connected.isEmpty()
                ? spatialCandidates(layout, current, direction)
                : connected;
        return candidates.stream()
                .min(candidateComparator(current, direction))
                .map(ResearchTreeLayout.PositionedNode::blueprintId);
    }

    private static List<ResearchTreeLayout.PositionedNode> connectedCandidates(
            ResearchTreeGraph graph,
            ResearchTreeLayout layout,
            ResourceLocation currentId,
            Direction direction) {
        List<ResourceLocation> ids;
        if (direction == Direction.UP) {
            ids = graph.edges().stream()
                    .filter(edge -> edge.prerequisiteId().equals(currentId))
                    .map(ResearchTreeGraph.Edge::dependentId)
                    .toList();
        } else if (direction == Direction.DOWN) {
            ids = graph.prerequisitesOf(currentId);
        } else {
            return List.of();
        }
        List<ResearchTreeLayout.PositionedNode> candidates = new ArrayList<>();
        ids.forEach(id -> layout.position(id).ifPresent(candidates::add));
        return List.copyOf(candidates);
    }

    private static List<ResearchTreeLayout.PositionedNode> spatialCandidates(
            ResearchTreeLayout layout,
            ResearchTreeLayout.PositionedNode current,
            Direction direction) {
        return layout.nodes().stream()
                .filter(candidate -> !candidate.blueprintId().equals(current.blueprintId()))
                .filter(candidate -> switch (direction) {
                    case LEFT -> candidate.centerX() < current.centerX()
                            && candidate.tier() == current.tier();
                    case RIGHT -> candidate.centerX() > current.centerX()
                            && candidate.tier() == current.tier();
                    case UP -> candidate.centerY() < current.centerY();
                    case DOWN -> candidate.centerY() > current.centerY();
                })
                .toList();
    }

    private static Comparator<ResearchTreeLayout.PositionedNode> candidateComparator(
            ResearchTreeLayout.PositionedNode current,
            Direction direction) {
        return Comparator
                .comparingInt((ResearchTreeLayout.PositionedNode candidate) ->
                        direction == Direction.LEFT || direction == Direction.RIGHT
                                ? Math.abs(candidate.centerX() - current.centerX())
                                : Math.abs(candidate.centerY() - current.centerY()))
                .thenComparingInt(candidate ->
                        direction == Direction.LEFT || direction == Direction.RIGHT
                                ? Math.abs(candidate.centerY() - current.centerY())
                                : Math.abs(candidate.centerX() - current.centerX()))
                .thenComparing(candidate -> candidate.blueprintId().toString());
    }

    public enum Direction {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }
}
