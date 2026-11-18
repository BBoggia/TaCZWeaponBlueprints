package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.resources.ResourceLocation;

/**
 * Places one Tech Tree domain on the drafting sheet: a column per family lane, a row per tier,
 * a gate bar above each tier, and one wire per blueprint to its main prerequisite.
 * Positions are whole GUI pixels on an unscaled canvas so weapon art stays crisp.
 */
final class TechTreeSheetLayout {
    static final int TILE_WIDTH = 54;
    static final int TILE_HEIGHT = 40;
    static final int GAP_X = 6;
    static final int GAP_Y = 10;
    static final int LANE_GAP = 16;
    static final int GATE_HEIGHT = 13;
    static final int GATE_SPACING = 8;
    static final int HEADER_HEIGHT = 16;
    static final int MARGIN = 10;
    static final int MAX_PER_ROW = 3;
    private static final int WIRE_OFFSET = 4;

    static final TechTreeSheetLayout EMPTY = new TechTreeSheetLayout(List.of(), List.of(), List.of(), List.of(), 0, 0);

    /** How weapons are grouped into lanes. */
    enum Arrangement {
        /** The branches the server's tree generator built. Most links stay inside a branch. */
        BRANCHES,
        /** One lane per weapon type. */
        FAMILIES
    }

    /**
     * A column of the sheet. {@code nameKeys} is used when a lane is named after the weapon
     * types it mostly holds; the screen joins their translated names.
     */
    record Lane(ResourceLocation id, String title, Optional<String> translationKey, List<String> nameKeys, int x,
            int width, int learned, int total) { }

    /**
     * One row of the sheet. {@code benchTier} is the Research Bench tier the row needs (1 to 3),
     * {@link #BAND_ROW} when rows follow the tree's own bands, or {@link #UNKNOWN_ROW} for
     * blueprints whose tier is not disclosed.
     */
    record Tier(int index, int benchTier, String title, Optional<String> translationKey, int gateY, int y, int height,
            int learned, int total) { }

    static final int BAND_ROW = 0;
    static final int UNKNOWN_ROW = -1;

    record Tile(ResourceLocation nodeId, int lane, int tier, int x, int y) {
        int centerX() {
            return x + TILE_WIDTH / 2;
        }

        boolean contains(double px, double py) {
            return px >= x && px < x + TILE_WIDTH && py >= y && py < y + TILE_HEIGHT;
        }
    }

    record Segment(int x1, int y1, int x2, int y2) { }

    /**
     * One prerequisite link. {@code alternative} means the blueprint accepts this prerequisite or
     * another one; {@code crossLane} means the link joins two lanes.
     */
    record Wire(ResourceLocation from, ResourceLocation to, List<Segment> segments, boolean alternative,
            boolean crossLane) { }

    private final List<Lane> lanes;
    private final List<Tier> tiers;
    private final List<Tile> tiles;
    private final List<Wire> wires;
    private final Map<ResourceLocation, Tile> tilesById;
    private final int width;
    private final int height;

    private TechTreeSheetLayout(List<Lane> lanes, List<Tier> tiers, List<Tile> tiles, List<Wire> wires, int width,
            int height) {
        this.lanes = List.copyOf(lanes);
        this.tiers = List.copyOf(tiers);
        this.tiles = List.copyOf(tiles);
        this.wires = List.copyOf(wires);
        Map<ResourceLocation, Tile> index = new HashMap<>();
        for (Tile tile : tiles) {
            index.put(tile.nodeId(), tile);
        }
        this.tilesById = Map.copyOf(index);
        this.width = width;
        this.height = height;
    }

    List<Lane> lanes() {
        return lanes;
    }

    List<Tier> tiers() {
        return tiers;
    }

    List<Tile> tiles() {
        return tiles;
    }

    List<Wire> wires() {
        return wires;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    Optional<Tile> tile(ResourceLocation nodeId) {
        return Optional.ofNullable(tilesById.get(nodeId));
    }

    Optional<Tile> tileAt(double x, double y) {
        for (Tile tile : tiles) {
            if (tile.contains(x, y)) {
                return Optional.of(tile);
            }
        }
        return Optional.empty();
    }

    /**
     * The tile an arrow key moves to from {@code fromId}: the nearest one in that direction, preferring
     * tiles in line with the current one. With nothing selected it starts at the first tile.
     */
    Optional<Tile> neighbor(ResourceLocation fromId, int dx, int dy) {
        Tile from = fromId == null ? null : tilesById.get(fromId);
        if (from == null) {
            return tiles.stream().findFirst();
        }
        Tile best = null;
        long bestScore = Long.MAX_VALUE;
        for (Tile tile : tiles) {
            int ahead = dx != 0 ? (tile.x() - from.x()) * dx : (tile.y() - from.y()) * dy;
            if (ahead <= 0) {
                continue;
            }
            long score = ahead + 3L * Math.abs(dx != 0 ? tile.y() - from.y() : tile.x() - from.x());
            if (score < bestScore || score == bestScore && best != null
                    && tile.nodeId().toString().compareTo(best.nodeId().toString()) < 0) {
                best = tile;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The wires that end at one blueprint, for highlighting a selection's requirements. */
    List<Wire> wiresInto(ResourceLocation nodeId) {
        return wires.stream().filter(wire -> wire.to().equals(nodeId)).toList();
    }

    static TechTreeSheetLayout build(ResearchTechTreePresentation.DomainView domain,
            List<ResearchTechTreePresentation.BandLabel> bands, ResearchTreeGraph graph) {
        return build(domain, bands, graph, Arrangement.BRANCHES);
    }

    static TechTreeSheetLayout build(ResearchTechTreePresentation.DomainView domain,
            List<ResearchTechTreePresentation.BandLabel> bands, ResearchTreeGraph graph, Arrangement arrangement) {
        if (domain == null || graph == null) {
            return EMPTY;
        }
        Map<ResourceLocation, Integer> bandIndex = new HashMap<>();
        for (int index = 0; index < bands.size(); index++) {
            bandIndex.put(bands.get(index).id(), index);
        }
        // A row names the node's own server-disclosed bench requirement. Prerequisites can
        // occupy a higher row; the selected route quote explains any remaining higher-tier work.
        boolean byBench = graph.nodes().stream().anyMatch(node -> node.benchTier() > 0
                && domain.lanes().stream().flatMap(lane -> lane.members().stream())
                        .anyMatch(member -> member.nodeId().equals(node.blueprintId())));
        int bandCount = byBench ? ResearchTreeGraph.MAX_BENCH_TIER + 1 : Math.max(1, bands.size());
        int maxRank = 0;
        for (var lane : domain.lanes()) {
            for (var member : lane.members()) {
                maxRank = Math.max(maxRank, member.rank());
            }
        }

        // cells[lane][band] keeps the server's order: rank first, then sibling order.
        List<List<List<ResourceLocation>>> cells = new ArrayList<>();
        boolean[] bandUsed = new boolean[bandCount];
        List<Family> placedLanes = new ArrayList<>();
        for (var lane : arrangement == Arrangement.BRANCHES ? branches(domain, graph) : families(domain, graph)) {
            List<List<ResourceLocation>> laneCells = new ArrayList<>();
            for (int band = 0; band < bandCount; band++) {
                laneCells.add(new ArrayList<>());
            }
            boolean any = false;
            for (var member : lane.members()) {
                var placed = graph.node(member.nodeId());
                if (placed.isEmpty()) {
                    continue;
                }
                if (byBench) {
                    int tier = placed.orElseThrow().benchTier();
                    int row = tier > 0 ? tier - 1 : ResearchTreeGraph.MAX_BENCH_TIER;
                    laneCells.get(row).add(member.nodeId());
                    bandUsed[row] = true;
                    any = true;
                    continue;
                }
                int fallback = (int) Math.min(bandCount - 1, (long) member.rank() * bandCount / (maxRank + 1L));
                int band = member.bandId().map(bandIndex::get).orElse(fallback);
                laneCells.get(band).add(member.nodeId());
                bandUsed[band] = true;
                any = true;
            }
            if (any) {
                cells.add(laneCells);
                placedLanes.add(lane);
            }
        }
        if (placedLanes.isEmpty()) {
            return EMPTY;
        }

        int[] perRow = new int[placedLanes.size()];
        int[] laneX = new int[placedLanes.size()];
        int x = MARGIN;
        for (int lane = 0; lane < placedLanes.size(); lane++) {
            int largest = 1;
            for (List<ResourceLocation> cell : cells.get(lane)) {
                largest = Math.max(largest, cell.size());
            }
            perRow[lane] = Math.min(MAX_PER_ROW, largest);
            laneX[lane] = x;
            x += laneWidth(perRow[lane]) + LANE_GAP;
        }
        int sheetWidth = x - LANE_GAP + MARGIN;

        List<Tile> tiles = new ArrayList<>();
        List<Tier> tiers = new ArrayList<>();
        int[] laneLearned = new int[placedLanes.size()];
        int[] laneTotal = new int[placedLanes.size()];
        int y = HEADER_HEIGHT + 4;
        int tierIndex = 0;
        for (int band = 0; band < bandCount; band++) {
            if (!bandUsed[band]) {
                continue;
            }
            int rows = 1;
            for (int lane = 0; lane < placedLanes.size(); lane++) {
                rows = Math.max(rows, (cells.get(lane).get(band).size() + perRow[lane] - 1) / perRow[lane]);
            }
            int gateY = y;
            int top = gateY + GATE_HEIGHT + GATE_SPACING;
            int tierLearned = 0;
            int tierTotal = 0;
            for (int lane = 0; lane < placedLanes.size(); lane++) {
                List<ResourceLocation> cell = cells.get(lane).get(band);
                for (int index = 0; index < cell.size(); index++) {
                    int row = index / perRow[lane];
                    // Every row uses the lane's own columns, which keeps the gaps between them clear for wires.
                    int rowLeft = laneX[lane];
                    ResourceLocation nodeId = cell.get(index);
                    tiles.add(new Tile(nodeId, lane, tierIndex,
                            rowLeft + (index % perRow[lane]) * (TILE_WIDTH + GAP_X),
                            top + row * (TILE_HEIGHT + GAP_Y)));
                    boolean learned = graph.node(nodeId).map(ResearchTreeGraph.Node::learned).orElse(false);
                    tierTotal++;
                    laneTotal[lane]++;
                    if (learned) {
                        tierLearned++;
                        laneLearned[lane]++;
                    }
                }
            }
            int height = rows * (TILE_HEIGHT + GAP_Y);
            boolean named = !byBench && band < bands.size();
            String title = named ? bands.get(band).title() : "";
            Optional<String> key = named ? bands.get(band).translationKey() : Optional.empty();
            int benchTier = !byBench ? BAND_ROW : band < ResearchTreeGraph.MAX_BENCH_TIER ? band + 1 : UNKNOWN_ROW;
            tiers.add(new Tier(tierIndex, benchTier, title, key, gateY, top, height, tierLearned, tierTotal));
            y = top + height;
            tierIndex++;
        }
        int sheetHeight = y + MARGIN;

        List<Lane> lanes = new ArrayList<>();
        for (int lane = 0; lane < placedLanes.size(); lane++) {
            var view = placedLanes.get(lane);
            lanes.add(new Lane(view.id(), view.title(), view.translationKey(), view.nameKeys(), laneX[lane],
                    laneWidth(perRow[lane]),
                    laneLearned[lane], laneTotal[lane]));
        }

        Map<ResourceLocation, Tile> byId = new LinkedHashMap<>();
        for (Tile tile : tiles) {
            byId.put(tile.nodeId(), tile);
        }
        List<Wire> wires = new ArrayList<>();
        for (Tile tile : tiles) {
            for (var group : graph.requirementGroupsOf(tile.nodeId())) {
                boolean alternative = group.visibleAlternativeIds().size() + group.hiddenAlternativeCount()
                        + group.externalAlternativeCount() > 1;
                for (ResourceLocation prerequisite : group.visibleAlternativeIds()) {
                    Tile source = byId.get(prerequisite);
                    if (source != null) {
                        wires.add(new Wire(prerequisite, tile.nodeId(), route(source, tile), alternative,
                                source.lane() != tile.lane()));
                    }
                }
            }
        }
        return new TechTreeSheetLayout(lanes, tiers, tiles, wires, sheetWidth, sheetHeight);
    }

    private record Family(ResourceLocation id, String title, Optional<String> translationKey, List<String> nameKeys,
            List<ResearchTechTreePresentation.Member> members) { }

    private static final String TYPE_NAME = "gui.taczweaponblueprints.journal.category.";

    private static int familyRank(String type) {
        int index = FAMILY_ORDER.indexOf(type);
        return index < 0 ? FAMILY_ORDER.size() : index;
    }

    private static String typePath(String type) {
        return type.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    /**
     * Lanes that follow the branches the server generated. A lane whose members carry no branch,
     * or only one, is kept as the pack author built it. Each branch is named after the weapon
     * type it mostly holds, plus a second type when that makes up a quarter of it or more.
     */
    private static List<Family> branches(ResearchTechTreePresentation.DomainView domain, ResearchTreeGraph graph) {
        List<Family> result = new ArrayList<>();
        for (var lane : domain.lanes()) {
            Map<Integer, List<ResearchTechTreePresentation.Member>> byBranch = new java.util.TreeMap<>();
            for (var member : lane.members()) {
                if (graph.node(member.nodeId()).isPresent()) {
                    byBranch.computeIfAbsent(member.automaticBranch().map(
                            ResearchTechTreePresentation.AutomaticBranchPlacement::branchIndex).orElse(-1),
                            ignored -> new ArrayList<>()).add(member);
                }
            }
            if (byBranch.size() < 2) {
                result.add(new Family(lane.id(), lane.title(), lane.translationKey(), List.of(), lane.members()));
                continue;
            }
            List<Family> split = new ArrayList<>();
            Map<Family, String> leading = new HashMap<>();
            for (var entry : byBranch.entrySet()) {
                Map<String, Integer> counts = new HashMap<>();
                for (var member : entry.getValue()) {
                    counts.merge(graph.node(member.nodeId()).orElseThrow().itemType(), 1, Integer::sum);
                }
                List<String> types = new ArrayList<>(counts.keySet());
                types.sort(Comparator.<String>comparingInt(type -> -counts.get(type))
                        .thenComparingInt(TechTreeSheetLayout::familyRank).thenComparing(Comparator.naturalOrder()));
                List<String> names = new ArrayList<>();
                names.add(TYPE_NAME + typePath(types.get(0)));
                if (types.size() > 1 && counts.get(types.get(1)) * 4 >= entry.getValue().size()) {
                    names.add(TYPE_NAME + typePath(types.get(1)));
                }
                Family family = new Family(ResourceLocation.fromNamespaceAndPath(lane.id().getNamespace(),
                        lane.id().getPath() + "/branch_" + (entry.getKey() < 0 ? "none" : entry.getKey())),
                        lane.title(), lane.translationKey(), names, entry.getValue());
                leading.put(family, types.get(0));
                split.add(family);
            }
            split.sort(Comparator.<Family>comparingInt(family -> familyRank(leading.get(family)))
                    .thenComparing(family -> family.id().toString()));
            result.addAll(split);
        }
        return result;
    }

    /** Weapon types in the order players meet them, sidearms first. Anything else follows alphabetically. */
    private static final List<String> FAMILY_ORDER = List.of("pistol", "smg", "shotgun", "rifle", "dmr", "sniper", "mg", "rpg");

    /**
     * The lanes to draw. A lane the pack author built is kept as it is. A lane that mixes several
     * weapon types, such as the automatic catch-all lane, is split into one lane per type.
     */
    private static List<Family> families(ResearchTechTreePresentation.DomainView domain, ResearchTreeGraph graph) {
        List<Family> result = new ArrayList<>();
        for (var lane : domain.lanes()) {
            Map<String, List<ResearchTechTreePresentation.Member>> byType = new LinkedHashMap<>();
            for (var member : lane.members()) {
                graph.node(member.nodeId()).ifPresent(node ->
                        byType.computeIfAbsent(node.itemType(), ignored -> new ArrayList<>()).add(member));
            }
            if (domain.domain() != ResearchTechTreeContract.Domain.WEAPONS || byType.size() < 2) {
                result.add(new Family(lane.id(), lane.title(), lane.translationKey(), List.of(), lane.members()));
                continue;
            }
            List<String> types = new ArrayList<>(byType.keySet());
            types.sort(Comparator.comparingInt(TechTreeSheetLayout::familyRank).thenComparing(Comparator.naturalOrder()));
            for (String type : types) {
                String path = typePath(type);
                String fallback = type.isEmpty() ? type
                        : Character.toUpperCase(type.charAt(0)) + type.substring(1).replace('_', ' ');
                result.add(new Family(
                        ResourceLocation.fromNamespaceAndPath(lane.id().getNamespace(), lane.id().getPath() + "/" + path),
                        fallback, Optional.of(TYPE_NAME + path), List.of(), byType.get(type)));
            }
        }
        return result;
    }

    private static int laneWidth(int tilesPerRow) {
        return tilesPerRow * TILE_WIDTH + (tilesPerRow - 1) * GAP_X;
    }

    /** Right-angle path that leaves the bottom of the source, travels in the gaps, and enters the top of the target. */
    static List<Segment> route(Tile source, Tile target) {
        List<Segment> segments = new ArrayList<>();
        int startX = source.centerX();
        int endX = target.centerX();
        int channel = target.x() - GAP_X / 2;
        if (source.y() >= target.y()) {
            // Same row or below: leave from the top and come in over the target through the row gaps.
            int overSource = source.y() - WIRE_OFFSET;
            int overTarget = target.y() - WIRE_OFFSET;
            add(segments, startX, source.y() - 1, startX, overSource);
            if (overSource != overTarget) {
                add(segments, startX, overSource, channel, overSource);
                add(segments, channel, overSource, channel, overTarget);
                add(segments, channel, overTarget, endX, overTarget);
            } else {
                add(segments, startX, overSource, endX, overTarget);
            }
            add(segments, endX, overTarget, endX, target.y() - 1);
            return segments;
        }
        int leave = source.y() + TILE_HEIGHT;
        int below = leave + WIRE_OFFSET;
        int above = target.y() - WIRE_OFFSET;
        if (below >= above) {
            add(segments, startX, leave, startX, above);
            add(segments, startX, above, endX, above);
            add(segments, endX, above, endX, target.y() - 1);
            return segments;
        }
        // Rows lie between the two tiles, so drop down the gap beside the target's column instead of through them.
        add(segments, startX, leave, startX, below);
        add(segments, startX, below, channel, below);
        add(segments, channel, below, channel, above);
        add(segments, channel, above, endX, above);
        add(segments, endX, above, endX, target.y() - 1);
        return segments;
    }

    private static void add(List<Segment> segments, int x1, int y1, int x2, int y2) {
        if (x1 != x2 || y1 != y2) {
            segments.add(new Segment(x1, y1, x2, y2));
        }
    }
}
