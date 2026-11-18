package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation.BandLabel;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation.DomainView;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation.LaneView;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation.Member;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class TechTreeSheetLayoutTest {
    private static final ResourceLocation BASIC = id("test:basic");
    private static final ResourceLocation UNUSED = id("test:unused");
    private static final ResourceLocation ELITE = id("test:elite");
    private static final List<BandLabel> BANDS = List.of(
            new BandLabel(BASIC, "Basic", Optional.empty()),
            new BandLabel(UNUSED, "Unused", Optional.empty()),
            new BandLabel(ELITE, "Elite", Optional.empty()));

    private final List<ResearchTreeGraph.Node> nodes = new ArrayList<>();
    private final List<ResearchTreeGraph.Edge> edges = new ArrayList<>();
    private DomainView fixtureDomain;

    @Test
    void lanesRunLeftToRightAndTiersTopToBottomWithNoOverlap() {
        TechTreeSheetLayout layout = fixture();

        assertEquals(List.of("Handguns", "Rifles"), layout.lanes().stream().map(TechTreeSheetLayout.Lane::title).toList());
        assertTrue(layout.lanes().get(0).x() + layout.lanes().get(0).width() < layout.lanes().get(1).x());
        assertEquals(List.of("Basic", "Elite"), layout.tiers().stream().map(TechTreeSheetLayout.Tier::title).toList(),
                "a tier nobody belongs to takes no room");
        assertTrue(layout.tiers().get(0).y() + layout.tiers().get(0).height() <= layout.tiers().get(1).gateY());
        for (var tile : layout.tiles()) {
            var lane = layout.lanes().get(tile.lane());
            var tier = layout.tiers().get(tile.tier());
            assertTrue(tile.x() >= lane.x() && tile.x() + TechTreeSheetLayout.TILE_WIDTH <= lane.x() + lane.width());
            assertTrue(tile.y() >= tier.y() && tile.y() + TechTreeSheetLayout.TILE_HEIGHT <= tier.y() + tier.height());
            assertTrue(tile.x() + TechTreeSheetLayout.TILE_WIDTH <= layout.width() && tile.y() < layout.height());
            for (var other : layout.tiles()) {
                assertFalse(other != tile && overlaps(tile, other.x(), other.y(),
                        other.x() + TechTreeSheetLayout.TILE_WIDTH, other.y() + TechTreeSheetLayout.TILE_HEIGHT));
            }
        }
    }

    @Test
    void aCrowdedCellWrapsInsteadOfWideningTheLane() {
        TechTreeSheetLayout layout = fixture();
        long rifleRows = layout.tiles().stream().filter(tile -> tile.lane() == 1 && tile.tier() == 0)
                .map(TechTreeSheetLayout.Tile::y).distinct().count();
        assertEquals(2, rifleRows, "five rifles at three per row need two rows");
        assertEquals(3 * TechTreeSheetLayout.TILE_WIDTH + 2 * TechTreeSheetLayout.GAP_X, layout.lanes().get(1).width());
    }

    @Test
    void everyLinkIsDrawnAndLinksBetweenLanesAreMarkedAsCrossPoints() {
        TechTreeSheetLayout layout = fixture();

        // The elite rifle needs a basic rifle and a pistol: one link stays in its lane, one crosses.
        var into = layout.wiresInto(id("test:rifle_elite"));
        assertEquals(2, into.size());
        assertEquals(1, into.stream().filter(TechTreeSheetLayout.Wire::crossLane).count());
        assertEquals(id("test:pistol_a"), into.stream().filter(TechTreeSheetLayout.Wire::crossLane)
                .findFirst().orElseThrow().from());
        assertTrue(layout.wires().stream().noneMatch(TechTreeSheetLayout.Wire::alternative),
                "these are all hard requirements");
        assertTrue(layout.wiresInto(id("test:pistol_a")).isEmpty());
    }

    @Test
    void generatedBranchesBecomeLanesAndEitherOrLinksAreFlagged() {
        typedNode("test:start", "pistol");
        typedNode("test:side", "pistol");
        typedNode("test:long_a", "rifle");
        typedNode("test:long_b", "dmr");
        List<ResearchTreeGraph.RequirementGroup> groups = List.of(
                new ResearchTreeGraph.RequirementGroup(id("test:side"), 0, List.of(id("test:start")), 0, 0, true, false),
                new ResearchTreeGraph.RequirementGroup(id("test:long_a"), 0, List.of(id("test:start")), 0, 0, true, false),
                new ResearchTreeGraph.RequirementGroup(id("test:long_b"), 0,
                        List.of(id("test:long_a"), id("test:side")), 0, 0, true, false));
        List<ResearchTreeGraph.Node> counted = new ArrayList<>();
        int[] prerequisites = {0, 1, 1, 2};
        for (int index = 0; index < nodes.size(); index++) {
            var node = nodes.get(index);
            counted.add(new ResearchTreeGraph.Node(index, node.blueprintId(), node.nameKey(), node.itemType(),
                    node.displaySlotId(), node.visibility(), false, true, true, 2, 0, prerequisites[index], 0,
                    ResearchTreeGraph.Availability.AVAILABLE));
        }
        DomainView domain = new DomainView(Domain.WEAPONS, "Weapons", Optional.empty(), Optional.empty(), List.of(
                new LaneView(id("test:general"), "Other Weapons", Optional.empty(), Optional.empty(), 0, List.of(
                        branchMember("test:start", 0, 0), branchMember("test:long_a", 1, 1),
                        branchMember("test:side", 1, 0), branchMember("test:long_b", 2, 1)))));
        TechTreeSheetLayout layout = TechTreeSheetLayout.build(domain, BANDS,
                ResearchTreeGraph.withRequirementGroups(counted, groups));

        assertEquals(2, layout.lanes().size());
        assertEquals(List.of("gui.taczweaponblueprints.journal.category.pistol"), layout.lanes().get(0).nameKeys());
        assertEquals(List.of("gui.taczweaponblueprints.journal.category.dmr",
                "gui.taczweaponblueprints.journal.category.rifle"), layout.lanes().get(1).nameKeys().stream().sorted().toList(),
                "a branch is named after both types when the second is a quarter of it or more");
        var either = layout.wiresInto(id("test:long_b"));
        assertEquals(2, either.size());
        assertTrue(either.stream().allMatch(TechTreeSheetLayout.Wire::alternative));
        assertEquals(1, either.stream().filter(TechTreeSheetLayout.Wire::crossLane).count());
        assertFalse(layout.wiresInto(id("test:side")).get(0).alternative());
    }

    @Test
    void wiresTravelInTheGapsAndNeverCrossATile() {
        TechTreeSheetLayout layout = fixture();
        List<TechTreeSheetLayout.Wire> all = new ArrayList<>(layout.wires());
        layout.tiles().forEach(tile -> all.addAll(layout.wiresInto(tile.nodeId())));
        assertFalse(all.isEmpty());
        for (var wire : all) {
            for (var segment : wire.segments()) {
                assertTrue(segment.x1() == segment.x2() || segment.y1() == segment.y2(), "wires are right-angled");
                for (var tile : layout.tiles()) {
                    assertFalse(overlaps(tile, Math.min(segment.x1(), segment.x2()), Math.min(segment.y1(), segment.y2()),
                            Math.max(segment.x1(), segment.x2()) + 1, Math.max(segment.y1(), segment.y2()) + 1),
                            wire.from() + " -> " + wire.to() + " crosses " + tile.nodeId());
                }
            }
        }
    }

    @Test
    void progressCountsFollowWhatThePlayerHasLearned() {
        TechTreeSheetLayout layout = fixture();
        assertEquals(1, layout.lanes().get(0).learned());
        assertEquals(2, layout.lanes().get(0).total());
        assertEquals(1, layout.tiers().get(0).learned());
        assertEquals(0, layout.tiers().get(1).learned());
    }

    @Test
    void membersWithoutATierAreSpreadByRank() {
        node("test:low", false, 0);
        node("test:high", false, 0);
        DomainView domain = new DomainView(Domain.WEAPONS, "Weapons", Optional.empty(), Optional.empty(), List.of(
                new LaneView(id("test:lane"), "Lane", Optional.empty(), Optional.empty(), 0, List.of(
                        new Member(id("test:low"), 0, 0, Optional.empty(), PlacementOrigin.EXACT, Optional.empty()),
                        new Member(id("test:high"), 9, 0, Optional.empty(), PlacementOrigin.EXACT, Optional.empty())))));
        TechTreeSheetLayout layout = TechTreeSheetLayout.build(domain, BANDS, new ResearchTreeGraph(nodes, edges));
        assertTrue(layout.tile(id("test:low")).orElseThrow().y() < layout.tile(id("test:high")).orElseThrow().y());
        assertEquals(layout.tile(id("test:low")), layout.tileAt(layout.tile(id("test:low")).orElseThrow().x() + 1,
                layout.tile(id("test:low")).orElseThrow().y() + 1));
    }

    @Test
    void aLaneThatMixesWeaponTypesIsSplitIntoOneLanePerFamily() {
        typedNode("test:big_rifle", "rifle");
        typedNode("test:sidearm", "pistol");
        typedNode("test:oddity", "crossbow");
        DomainView domain = new DomainView(Domain.WEAPONS, "Weapons", Optional.empty(), Optional.empty(), List.of(
                new LaneView(id("test:general"), "Other Weapons", Optional.empty(), Optional.empty(), 0, List.of(
                        member("test:big_rifle", 0, 0, BASIC), member("test:oddity", 0, 1, BASIC),
                        member("test:sidearm", 0, 2, BASIC)))));
        TechTreeSheetLayout layout = TechTreeSheetLayout.build(domain, BANDS, new ResearchTreeGraph(nodes, edges),
                TechTreeSheetLayout.Arrangement.FAMILIES);
        assertEquals(List.of("Pistol", "Rifle", "Crossbow"),
                layout.lanes().stream().map(TechTreeSheetLayout.Lane::title).toList(),
                "sidearms come first and unknown types last");
        assertEquals(Optional.of("gui.taczweaponblueprints.journal.category.pistol"),
                layout.lanes().get(0).translationKey());
        assertEquals(0, layout.tile(id("test:sidearm")).orElseThrow().lane());
    }

    @Test
    void rowsFollowTheRequiredResearchBenchWhenTheServerDisclosesIt() {
        fixture();
        // Tiers cut across the tree's own bands: an "elite band" pistol that only needs the first bench moves up.
        List<ResearchTreeGraph.Node> tiered = nodes.stream().map(node -> node.withBenchTier(
                node.blueprintId().equals(id("test:rifle_elite")) ? 3
                        : node.blueprintId().equals(id("test:rifle_4")) ? 0 : 1)).toList();
        TechTreeSheetLayout layout = TechTreeSheetLayout.build(fixtureDomain, BANDS,
                new ResearchTreeGraph(tiered, edges), TechTreeSheetLayout.Arrangement.FAMILIES);
        assertEquals(layout.tiers().stream().map(TechTreeSheetLayout.Tier::benchTier).toList(),
                TechTreeSheetLayout.build(fixtureDomain, BANDS, new ResearchTreeGraph(tiered, edges)).tiers()
                        .stream().map(TechTreeSheetLayout.Tier::benchTier).toList(),
                "both lane groupings use the same tier rows");
        assertEquals(List.of(1, 3, TechTreeSheetLayout.UNKNOWN_ROW),
                layout.tiers().stream().map(TechTreeSheetLayout.Tier::benchTier).toList(),
                "an unused tier takes no room and undisclosed blueprints get their own last row");
        assertEquals(0, layout.tile(id("test:pistol_elite")).orElseThrow().tier());
        assertEquals(1, layout.tile(id("test:rifle_elite")).orElseThrow().tier());
        assertEquals(2, layout.tile(id("test:rifle_4")).orElseThrow().tier());
        assertTrue(fixture().tiers().stream().allMatch(tier -> tier.benchTier() == TechTreeSheetLayout.BAND_ROW));
    }

    @Test
    void rowsKeepTheActualBenchRequirementEvenBehindAHigherTierPrerequisite() {
        fixture();
        List<ResearchTreeGraph.Node> tiered = nodes.stream().map(node -> node.withBenchTier(
                node.blueprintId().equals(id("test:pistol_a")) ? 3 : 1)).toList();
        TechTreeSheetLayout layout = TechTreeSheetLayout.build(fixtureDomain, BANDS,
                new ResearchTreeGraph(tiered, edges));
        assertEquals(3, layout.tiers().get(layout.tile(id("test:pistol_a")).orElseThrow().tier()).benchTier());
        assertEquals(1, layout.tiers().get(layout.tile(id("test:pistol_elite")).orElseThrow().tier()).benchTier());
        assertEquals(1, layout.tiers().get(layout.tile(id("test:rifle_elite")).orElseThrow().tier()).benchTier());
        for (var wire : layout.wires()) for (var segment : wire.segments()) for (var tile : layout.tiles()) {
            assertFalse(overlaps(tile, Math.min(segment.x1(), segment.x2()), Math.min(segment.y1(), segment.y2()),
                    Math.max(segment.x1(), segment.x2()) + 1, Math.max(segment.y1(), segment.y2()) + 1),
                    "a backward link must stay outside nodes");
        }
        List<ResearchTreeGraph.Node> learned = tiered.stream().map(node -> new ResearchTreeGraph.Node(
                node.ordinal(), node.sourceOrdinal(), node.blueprintId(), node.nameKey(), node.itemType(), node.displaySlotId(),
                node.visibility(), true, true, false, node.pointCost(), node.ingredientTypeCount(), node.prerequisiteCount(),
                node.hiddenPrerequisiteCount(), ResearchTreeGraph.Availability.LEARNED, node.benchTier())).toList();
        var after = TechTreeSheetLayout.build(fixtureDomain, BANDS, new ResearchTreeGraph(learned, edges));
        assertEquals(layout.tiles(), after.tiles(), "learning must not move nodes between bench rows");
    }

    @Test
    void arrowKeysMoveToTheNearestTileInThatDirectionAndStopAtTheEdge() {
        TechTreeSheetLayout layout = fixture();
        var first = layout.neighbor(null, 1, 0).orElseThrow();
        assertEquals(id("test:pistol_a"), first.nodeId(), "with nothing selected, any arrow starts at the first tile");

        var right = layout.neighbor(id("test:rifle_0"), 1, 0).orElseThrow();
        assertEquals(id("test:rifle_1"), right.nodeId());
        assertEquals(id("test:rifle_0"), layout.neighbor(right.nodeId(), -1, 0).orElseThrow().nodeId());
        var down = layout.neighbor(id("test:rifle_0"), 0, 1).orElseThrow();
        assertEquals(id("test:rifle_3"), down.nodeId(), "down stays in the same column");
        assertEquals(id("test:pistol_a"), layout.neighbor(id("test:rifle_0"), -1, 0).orElseThrow().nodeId(),
                "left crosses into the next lane");
        assertTrue(layout.neighbor(id("test:pistol_a"), -1, 0).isEmpty(), "nothing lies past the edge");
        assertTrue(layout.neighbor(id("test:pistol_a"), 0, -1).isEmpty());
    }

    private TechTreeSheetLayout fixture() {
        nodes.clear();
        edges.clear();
        node("test:pistol_a", true, 0);
        node("test:pistol_elite", false, 1);
        List<Member> rifles = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            node("test:rifle_" + index, false, 0);
            rifles.add(member("test:rifle_" + index, 0, index, BASIC));
        }
        node("test:rifle_elite", false, 2);
        rifles.add(member("test:rifle_elite", 5, 0, ELITE));
        edges.add(new ResearchTreeGraph.Edge(id("test:pistol_a"), id("test:pistol_elite")));
        edges.add(new ResearchTreeGraph.Edge(id("test:rifle_0"), id("test:rifle_elite")));
        edges.add(new ResearchTreeGraph.Edge(id("test:pistol_a"), id("test:rifle_elite")));
        fixtureDomain = new DomainView(Domain.WEAPONS, "Weapons", Optional.empty(), Optional.empty(), List.of(
                new LaneView(id("test:handguns"), "Handguns", Optional.empty(), Optional.empty(), 0, List.of(
                        member("test:pistol_a", 0, 0, BASIC), member("test:pistol_elite", 5, 0, ELITE))),
                new LaneView(id("test:rifles"), "Rifles", Optional.empty(), Optional.empty(), 1, rifles)));
        return TechTreeSheetLayout.build(fixtureDomain, BANDS, new ResearchTreeGraph(nodes, edges));
    }

    private static boolean overlaps(TechTreeSheetLayout.Tile tile, int left, int top, int right, int bottom) {
        return left < tile.x() + TechTreeSheetLayout.TILE_WIDTH && right > tile.x()
                && top < tile.y() + TechTreeSheetLayout.TILE_HEIGHT && bottom > tile.y();
    }

    private static Member branchMember(String node, int rank, int branch) {
        return new Member(id(node), rank, 0, Optional.of(BASIC), PlacementOrigin.AUTOMATIC, Optional.empty(),
                Optional.of(new com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation
                        .AutomaticBranchPlacement(branch, rank, 1, 2)));
    }

    private static Member member(String node, int rank, int order, ResourceLocation band) {
        return new Member(id(node), rank, order, Optional.of(band), PlacementOrigin.EXACT, Optional.empty());
    }

    private void node(String name, boolean learned, int prerequisites) {
        nodes.add(new ResearchTreeGraph.Node(nodes.size(), id(name), "item." + name.substring(5), "fixture", id(name),
                JournalVisibility.FULL, learned, true, !learned, 2, 0, prerequisites, 0,
                learned ? ResearchTreeGraph.Availability.LEARNED : ResearchTreeGraph.Availability.AVAILABLE));
    }

    private void typedNode(String name, String type) {
        nodes.add(new ResearchTreeGraph.Node(nodes.size(), id(name), "item." + name.substring(5), type, id(name),
                JournalVisibility.FULL, false, true, true, 2, 0, 0, 0, ResearchTreeGraph.Availability.AVAILABLE));
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
