package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/**
 * Legacy grouped layout retained for source compatibility and historical fixtures.
 * Runtime Branches projections use {@link ResearchTreeBranchLayoutComposer}.
 */
@Deprecated(forRemoval = false)
public final class ResearchTreeGroupedLayoutEngine {
    public static final int PADDING = 16;
    public static final int REGION_PADDING = 12;
    public static final int REGION_HEADER_HEIGHT = 18;
    public static final int REGION_GAP = 16;
    public static final int NODE_GAP = 20;
    public static final int ROW_GAP = 10;
    public static final int RANK_GAP = 28;
    public static final int MIN_REGION_WIDTH = 72;

    private ResearchTreeGroupedLayoutEngine() {
    }

    public static ResearchTreeLayout allWeapons(ResearchTreePublication publication) {
        if (publication == null) {
            throw new IllegalArgumentException("research publication cannot be null");
        }
        publication = publication.legacyView();
        return layout(publication.graph(), publication.presentation().groups());
    }

    public static ResearchTreeLayout branch(
            ResearchTreeGraph graph,
            ResearchTreePresentation.Group group) {
        return branch(graph, group, 0);
    }

    public static ResearchTreeLayout branch(
            ResearchTreeGraph graph,
            ResearchTreePresentation.Group group,
            int minimumContentWidth) {
        if (graph == null || group == null) {
            throw new IllegalArgumentException("research branch layout inputs cannot be null");
        }
        if (minimumContentWidth < 0 || minimumContentWidth > ResearchTreeLayout.MAX_DIMENSION) {
            throw new IllegalArgumentException("invalid research branch portal width");
        }
        return layout(
                graph,
                graph.nodes().isEmpty() ? List.of() : List.of(group),
                minimumContentWidth);
    }

    private static ResearchTreeLayout layout(
            ResearchTreeGraph graph,
            List<ResearchTreePresentation.Group> groups) {
        return layout(graph, groups, 0);
    }

    private static ResearchTreeLayout layout(
            ResearchTreeGraph graph,
            List<ResearchTreePresentation.Group> groups,
            int minimumContentWidth) {
        if (graph.nodes().isEmpty()) {
            return ResearchTreeLayout.EMPTY;
        }

        int maximumRank = groups.stream()
                .flatMap(group -> group.members().stream())
                .filter(member -> graph.node(member.nodeId()).isPresent())
                .mapToInt(ResearchTreePresentation.Member::rank)
                .max()
                .orElseThrow(() -> new IllegalArgumentException(
                        "research groups do not contain the projected graph"));

        List<GroupSpec> specs = new ArrayList<>(groups.size());
        int[] maximumRowsByRank = new int[maximumRank + 1];
        for (ResearchTreePresentation.Group group : groups) {
            Map<Integer, List<ResearchTreePresentation.Member>> membersByRank =
                    new LinkedHashMap<>();
            for (ResearchTreePresentation.Member member : group.members()) {
                if (graph.node(member.nodeId()).isPresent()) {
                    membersByRank.computeIfAbsent(member.rank(), ignored -> new ArrayList<>())
                            .add(member);
                }
            }
            if (membersByRank.isEmpty()) {
                continue;
            }
            int contentWidth = 0;
            Map<Integer, Integer> columnsByRank = new LinkedHashMap<>();
            for (Map.Entry<Integer, List<ResearchTreePresentation.Member>> entry
                    : membersByRank.entrySet()) {
                int columns = squareColumns(entry.getValue().size());
                int rows = divideRoundUp(entry.getValue().size(), columns);
                columnsByRank.put(entry.getKey(), columns);
                maximumRowsByRank[entry.getKey()] = Math.max(
                        maximumRowsByRank[entry.getKey()], rows);
                contentWidth = Math.max(contentWidth, occupiedWidth(columns));
            }
            int regionWidth = Math.max(
                    MIN_REGION_WIDTH,
                    Math.addExact(
                            Math.max(contentWidth, minimumContentWidth),
                            REGION_PADDING * 2));
            specs.add(new GroupSpec(group, membersByRank, columnsByRank, regionWidth));
        }
        if (specs.isEmpty()) {
            throw new IllegalArgumentException("research presentation omits projected nodes");
        }

        int[] rankY = new int[maximumRank + 1];
        int currentY = PADDING + REGION_HEADER_HEIGHT;
        for (int rank = maximumRank; rank >= 0; rank--) {
            rankY[rank] = currentY;
            int rows = maximumRowsByRank[rank];
            if (rows > 0) {
                currentY = Math.addExact(
                        currentY,
                        Math.addExact(
                                Math.multiplyExact(rows, ResearchTreeLayout.NODE_HEIGHT),
                                Math.multiplyExact(rows - 1, ROW_GAP)));
                currentY = Math.addExact(currentY, RANK_GAP);
            }
        }
        int canvasHeight = Math.addExact(currentY - RANK_GAP, PADDING);
        ensureDimension(canvasHeight);

        ResearchTreeLayout.PositionedNode[] positions =
                new ResearchTreeLayout.PositionedNode[graph.nodes().size()];
        int[] orderByRank = new int[maximumRank + 1];
        List<ResearchTreeLayout.GroupRegion> regions = new ArrayList<>(specs.size());
        int currentX = PADDING;
        for (GroupSpec spec : specs) {
            int regionX = currentX;
            regions.add(new ResearchTreeLayout.GroupRegion(
                    spec.group().id(),
                    regionX,
                    PADDING,
                    spec.width(),
                    canvasHeight - PADDING * 2));
            for (Map.Entry<Integer, List<ResearchTreePresentation.Member>> entry
                    : spec.membersByRank().entrySet()) {
                int rank = entry.getKey();
                List<ResearchTreePresentation.Member> members = entry.getValue();
                int columns = spec.columnsByRank().get(rank);
                int availableWidth = spec.width() - REGION_PADDING * 2;
                for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
                    int row = memberIndex / columns;
                    int column = memberIndex % columns;
                    int rowSize = Math.min(columns, members.size() - row * columns);
                    int rowWidth = occupiedWidth(rowSize);
                    int rowX = regionX + REGION_PADDING + (availableWidth - rowWidth) / 2;
                    ResearchTreePresentation.Member member = members.get(memberIndex);
                    ResearchTreeGraph.Node node = graph.node(member.nodeId()).orElseThrow();
                    positions[node.ordinal()] = new ResearchTreeLayout.PositionedNode(
                            node.ordinal(),
                            node.blueprintId(),
                            spec.group().order(),
                            rank,
                            orderByRank[rank]++,
                            rowX + column * (ResearchTreeLayout.NODE_WIDTH + NODE_GAP),
                            rankY[rank] + row * (ResearchTreeLayout.NODE_HEIGHT + ROW_GAP));
                }
            }
            currentX = Math.addExact(currentX, spec.width() + REGION_GAP);
        }
        for (ResearchTreeLayout.PositionedNode position : positions) {
            if (position == null) {
                throw new IllegalArgumentException(
                        "research presentation does not place every projected node");
            }
        }
        int canvasWidth = Math.addExact(currentX - REGION_GAP, PADDING);
        ensureDimension(canvasWidth);
        return new ResearchTreeLayout(
                canvasWidth,
                canvasHeight,
                maximumRank + 1,
                List.of(positions),
                List.of(),
                List.of(),
                regions);
    }

    private static int squareColumns(int size) {
        return Math.max(1, (int) Math.ceil(Math.sqrt(size)));
    }

    private static int occupiedWidth(int columns) {
        return Math.addExact(
                Math.multiplyExact(columns, ResearchTreeLayout.NODE_WIDTH),
                Math.multiplyExact(columns - 1, NODE_GAP));
    }

    private static int divideRoundUp(int value, int divisor) {
        return 1 + (value - 1) / divisor;
    }

    private static void ensureDimension(int value) {
        if (value <= 0 || value > ResearchTreeLayout.MAX_DIMENSION) {
            throw new IllegalArgumentException("grouped research layout exceeds its dimension limit");
        }
    }

    private record GroupSpec(
            ResearchTreePresentation.Group group,
            Map<Integer, List<ResearchTreePresentation.Member>> membersByRank,
            Map<Integer, Integer> columnsByRank,
            int width) {
        private GroupSpec {
            Map<Integer, List<ResearchTreePresentation.Member>> immutableMembers =
                    new LinkedHashMap<>();
            membersByRank.forEach((rank, members) ->
                    immutableMembers.put(rank, List.copyOf(members)));
            membersByRank = java.util.Collections.unmodifiableMap(immutableMembers);
            columnsByRank = java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(columnsByRank));
        }
    }
}
