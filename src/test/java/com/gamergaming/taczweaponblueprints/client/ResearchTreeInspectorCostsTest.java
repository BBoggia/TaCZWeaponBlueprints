package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

class ResearchTreeInspectorCostsTest {
    private static final ResourceLocation ID = ResourceLocation.parse("test:target");
    private static final int TEXT = 1, MUTED = 2, GOOD = 3, BAD = 4;
    private static final ResearchSelectionPreview.IngredientPreview INGREDIENT = new ResearchSelectionPreview.IngredientPreview(
            List.of(ResourceLocation.parse("minecraft:iron_ingot")), Optional.of(ResourceLocation.parse("forge:ingots/iron")), 18, 3);

    private ResearchSelectionPreview preview(ResearchCostMode mode, boolean bypass, int extra, boolean emptyMaterials) {
        var materials = mode.itemsEnabled() && !emptyMaterials ? List.of(INGREDIENT) : List.<ResearchSelectionPreview.IngredientPreview>of();
        return new ResearchSelectionPreview(Optional.of(ID), mode.pointsEnabled() ? 62 : 0, mode.pointsEnabled() ? 7 : 0,
                true, bypass || materials.isEmpty(), true, bypass, bypass, materials, 8, materials.size() + extra,
                ResearchSelectionPreview.PathPlanningState.NONE, mode);
    }
    private ResearchTreeSelectedNodePresenter.Presentation details(ResearchSelectionPreview preview, boolean matched, boolean learned) {
        var node = new ResearchTreeGraph.Node(0, ID, "test.target", "rifle", ResourceLocation.parse("minecraft:paper"),
                JournalVisibility.FULL, learned, true, !learned, 62, 1, 0, 0,
                learned ? ResearchTreeGraph.Availability.LEARNED : ResearchTreeGraph.Availability.AVAILABLE);
        return ResearchTreeSelectedNodePresenter.present(new ResearchTreeSelectedNodePresenter.Input(node, false,
                matched ? Optional.of(ID) : Optional.empty(), preview, 0, 0, preview.costMode()));
    }
    private List<ResearchTreeInspectorRow> rows(ResearchTreeSelectedNodePresenter.Presentation details) {
        return ResearchTreeInspectorCosts.rows(details, Component.literal("Custom Scrap"),
                ingredient -> Component.literal("Iron ingots"), TEXT, MUTED, GOOD, BAD);
    }
    @Test void tableUsesQuotedBalancesAndAllocatedMaterialsWithCustomCurrency() {
        var rows = rows(details(preview(ResearchCostMode.POINTS_AND_ITEMS, false, 0, false), true, false));
        var costs = rows.stream().filter(r -> r.kind() == ResearchTreeInspectorRow.Kind.COST).toList();
        assertEquals(2, costs.size());
        assertEquals("Custom Scrap", costs.get(0).text().getString());
        assertEquals("7", costs.get(0).costCells().orElseThrow().have().getString());
        assertEquals("62", costs.get(0).costCells().orElseThrow().required().getString());
        assertEquals("3", costs.get(1).costCells().orElseThrow().have().getString());
        assertEquals("18", costs.get(1).costCells().orElseThrow().required().getString());
        assertEquals(BAD, costs.get(1).costCells().orElseThrow().haveColor());
        assertEquals(1, rows.stream().filter(r -> r.kind() == ResearchTreeInspectorRow.Kind.COST_HEADER).count());
        assertEquals("gui.taczweaponblueprints.research_bench.tree.cost_table.narration",
                ((TranslatableContents) costs.get(1).narration().getContents()).getKey());
    }
    @Test void disabledChannelsDoNotBecomeRowsAndCreativeCostsAreNotShortages() {
        for (var mode : ResearchCostMode.values()) {
            var costs = rows(details(preview(mode, true, 0, false), true, false)).stream()
                    .filter(r -> r.kind() == ResearchTreeInspectorRow.Kind.COST).toList();
            assertEquals((mode.pointsEnabled() ? 1 : 0) + (mode.itemsEnabled() ? 1 : 0), costs.size());
            assertTrue(costs.stream().allMatch(r -> r.costCells().orElseThrow().haveColor() == MUTED));
        }
    }
    @Test void staleLearnedAndFailedQuotesNeverExposeCostRows() {
        var preview = preview(ResearchCostMode.POINTS_AND_ITEMS, false, 0, false);
        assertTrue(rows(details(preview, false, false)).isEmpty());
        assertTrue(rows(details(preview, true, true)).isEmpty());
        var failed = new ResearchSelectionPreview(Optional.of(ID), 0, 0, false, true, true, false, false,
                List.of(), 1, 0, ResearchSelectionPreview.PathPlanningState.UNSATISFIABLE);
        assertTrue(rows(details(failed, true, false)).isEmpty());
    }
    @Test void emptyAndTruncatedQuotesRemainExplicit() {
        var free = rows(details(preview(ResearchCostMode.ITEMS_ONLY, false, 0, true), true, false));
        assertTrue(free.stream().noneMatch(r -> r.costCells().isPresent()));
        assertTrue(free.stream().anyMatch(r -> r.text().getContents() instanceof TranslatableContents t && t.getKey().endsWith(".free")));
        var limited = rows(details(preview(ResearchCostMode.POINTS_AND_ITEMS, false, 2, false), true, false));
        assertTrue(limited.stream().anyMatch(r -> r.text().getContents() instanceof TranslatableContents t && t.getKey().endsWith(".more_materials")));
    }
    @Test void largeAmountsKeepTheirColumnsAndLongTranslatedHeadersWrapWithinThem() {
        var row = ResearchTreeInspectorRow.cost(ResearchTreeInspectorRow.Kind.COST, Component.literal("Long resource name"),
                Component.literal("2147483647"), Component.literal("2147483647"), TEXT, BAD);
        var header = ResearchTreeInspectorRow.cost(ResearchTreeInspectorRow.Kind.COST_HEADER, Component.literal("Resource"),
                Component.literal("Very long translated have header"), Component.literal("Very long required header"), MUTED, MUTED);
        for (int width : new int[]{202,244,260,440}) {
            var columns = ResearchTreeCostColumns.measure(width, List.of(row,header), c -> c.getString().length()*6);
            assertTrue(columns.resourceWidth() >= 48);
            assertTrue(columns.haveWidth() >= 60); assertTrue(columns.requiredWidth() >= 60);
            assertTrue(2 + columns.resourceWidth() < columns.haveX());
            assertTrue(columns.haveX() + columns.haveWidth() < columns.requiredX());
            assertTrue(columns.requiredX() + columns.requiredWidth() <= width - 10);
        }
    }
    @Test void materialPagesExposeOnlyValidNavigationAndPreserveTheCurrencyRow() {
        for (int offset : new int[]{0, 6, 12}) {
            var quote = new ResearchSelectionPreview(Optional.of(ID), 62, 7, true, false, true, false, false,
                    java.util.Collections.nCopies(offset == 12 ? 2 : 6, INGREDIENT), 8, 14,
                    ResearchSelectionPreview.PathPlanningState.NONE, ResearchCostMode.POINTS_AND_ITEMS,
                    Optional.empty(), com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchAccessSummary.NONE,
                    com.gamergaming.taczweaponblueprints.menu.ResearchSelectionProgressionPreview.EMPTY, offset);
            var rows = ResearchTreeInspectorCosts.rows(details(quote, true, false), quote, Component.literal("Scrap"),
                    i -> Component.literal("Iron"), TEXT, MUTED, GOOD, BAD);
            assertEquals(offset > 0, rows.stream().anyMatch(r -> r.materialPageDirection() == -1));
            assertEquals(offset < 12, rows.stream().anyMatch(r -> r.materialPageDirection() == 1));
            assertTrue(rows.stream().filter(r -> r.materialPageDirection() != 0).allMatch(ResearchTreeInspectorRow::interactive));
            assertEquals(1 + quote.ingredients().size(), rows.stream().filter(r -> r.kind() == ResearchTreeInspectorRow.Kind.COST).count());
        }
    }
    @Test void hiddenAndExternalAlternativesDoNotImplyASingleRequiredBlueprint() {
        var other = ResourceLocation.parse("test:other");
        for (int[] counts : new int[][]{{0,0},{1,0},{0,1}}) {
            var group = new ResearchTreeGraph.RequirementGroup(ID,0,List.of(other),counts[0],counts[1],true,false);
            var text = (TranslatableContents) ResearchTreeInspectorRequirements.groupHeading(group).getContents();
            assertTrue(text.getKey().endsWith(counts[0]+counts[1] == 0 ? ".single" : ".any"));
        }
    }
}
