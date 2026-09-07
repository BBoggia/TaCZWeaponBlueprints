package com.gamergaming.taczweaponblueprints.client;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Group-owned connector geometry. Render this network once, rather than the legacy edge paths. */
public record ResearchTreeRouting(List<Junction> junctions, List<Connection> connections,
        List<Crossing> crossings, Statistics statistics) {
    public static final double TRACK = 3;
    public static final double INK_PADDING = 1;
    public static final int MAX_CONNECTIONS = 16_384;
    public static final int MAX_CROSSINGS = 524_288;
    public static final long MAX_WORK = 32_000_000;

    public ResearchTreeRouting {
        junctions = List.copyOf(junctions);
        connections = List.copyOf(connections);
        crossings = List.copyOf(crossings);
        Objects.requireNonNull(statistics);
        if (junctions.size() > MAX_CONNECTIONS || connections.size() > MAX_CONNECTIONS
                || crossings.size() > MAX_CROSSINGS
                || connections.stream().mapToLong(c -> c.points().size()).sum() > ResearchTreeScenePolicy.MAX_TOTAL_ROUTE_POINTS) {
            throw new IllegalArgumentException("group routing count budget exceeded");
        }
    }

    /** Counts remain on the public Group; anonymous alternatives never become ports with invented identities. */
    public record Junction(ResearchTreeSceneInput.Group group, ResearchTreeScene.Bounds bounds,
            List<ResearchTreeScene.Point> inputPorts, ResearchTreeScene.Point outputPort) {
        public Junction {
            Objects.requireNonNull(group); Objects.requireNonNull(bounds); Objects.requireNonNull(outputPort);
            inputPorts = List.copyOf(inputPorts);
            if (inputPorts.size() != group.alternatives().size() || !bounds.contains(outputPort)
                    || inputPorts.stream().anyMatch(p -> !bounds.contains(p))) {
                throw new IllegalArgumentException("junction ports do not match visible alternatives");
            }
        }
    }

    public enum Kind { ALTERNATIVE, GROUP_TRUNK, BOUNDARY }

    /** Boundary index addresses the scene marker, without claiming membership in an undisclosed group. */
    public record Owner(Kind kind, Optional<ResearchTreeSceneInput.GroupId> group,
            Optional<ResourceLocation> prerequisite, int boundaryIndex) {
        public Owner {
            Objects.requireNonNull(kind); Objects.requireNonNull(group); Objects.requireNonNull(prerequisite);
            if (kind == Kind.BOUNDARY ? group.isPresent() || prerequisite.isPresent() || boundaryIndex < 0
                    : group.isEmpty() || boundaryIndex != -1 || (kind == Kind.ALTERNATIVE) != prerequisite.isPresent()) {
                throw new IllegalArgumentException("invalid connector ownership");
            }
        }
        public static Owner alternative(ResearchTreeSceneInput.GroupId group, ResourceLocation id) {
            return new Owner(Kind.ALTERNATIVE, Optional.of(group), Optional.of(id), -1);
        }
        public static Owner trunk(ResearchTreeSceneInput.GroupId group) {
            return new Owner(Kind.GROUP_TRUNK, Optional.of(group), Optional.empty(), -1);
        }
        public static Owner boundary(int index) {
            return new Owner(Kind.BOUNDARY, Optional.empty(), Optional.empty(), index);
        }
    }

    public record Connection(Owner owner, List<ResearchTreeScene.Point> points) {
        public Connection {
            Objects.requireNonNull(owner);
            points = List.copyOf(points);
            if (points.size() < 2 || points.size() > ResearchTreeScenePolicy.MAX_ROUTE_POINTS) {
                throw new IllegalArgumentException("invalid connector point count");
            }
            for (int i = 1; i < points.size(); i++) {
                var a = points.get(i - 1); var b = points.get(i);
                if (a.equals(b) || a.x() != b.x() && a.y() != b.y()) {
                    throw new IllegalArgumentException("zero-length or non-orthogonal connector segment");
                }
                if (i > 1) {
                    var before = points.get(i - 2);
                    if (before.x() == a.x() && a.x() == b.x() && (a.y() - before.y()) * (b.y() - a.y()) < 0
                            || before.y() == a.y() && a.y() == b.y() && (a.x() - before.x()) * (b.x() - a.x()) < 0) {
                        throw new IllegalArgumentException("connector doubles back on itself");
                    }
                }
            }
        }
    }

    /** Horizontal ink is interrupted at crossings; only a junction box represents a logical join. */
    public record Crossing(ResearchTreeScene.Point point, int horizontalConnection, int horizontalSegment,
            int verticalConnection, int verticalSegment) { }

    public record Statistics(int spacingRetries, int bends, int usedVerticalTracks, int congestedCandidates,
            double usedWidth, long work) {
        public Statistics {
            if (spacingRetries < 0 || spacingRetries > 2 || bends < 0 || usedVerticalTracks < 0
                    || congestedCandidates < 0 || !Double.isFinite(usedWidth) || usedWidth < 0
                    || work < 0 || work > MAX_WORK) throw new IllegalArgumentException("invalid routing statistics");
        }
    }
}
