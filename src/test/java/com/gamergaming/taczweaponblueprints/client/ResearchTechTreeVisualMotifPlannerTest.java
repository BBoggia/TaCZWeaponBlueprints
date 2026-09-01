package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.resources.ResourceLocation;

class ResearchTechTreeVisualMotifPlannerTest {
    @Test
    void responsiveWrappingUsesCleanBranchBoundariesBeforeSplittingFamilies() {
        List<ResourceLocation> nodes = nodes("a", 3, "b", 3, "c", 3);
        Map<ResourceLocation, Integer> families = Map.ofEntries(
                Map.entry(nodes.get(0), 0), Map.entry(nodes.get(1), 0),
                Map.entry(nodes.get(2), 0), Map.entry(nodes.get(3), 1),
                Map.entry(nodes.get(4), 1), Map.entry(nodes.get(5), 1),
                Map.entry(nodes.get(6), 2), Map.entry(nodes.get(7), 2),
                Map.entry(nodes.get(8), 2));

        ResearchTechTreeVisualMotifPlanner.Plan plan =
                ResearchTechTreeVisualMotifPlanner.partition(
                        nodes, 6, nodeId -> Optional.ofNullable(families.get(nodeId)));

        assertEquals(List.of(nodes.subList(0, 6), nodes.subList(6, 9)), plan.rows());
        assertEquals(0, plan.splitFamilyBoundaries());
        assertEquals(1, plan.mixedFamilyRows());
        assertEquals(0, plan.severelyUnderfilledRows());
    }

    @Test
    void avoidsPathologicallySparseRowsEvenWhenACleanFamilyBoundaryExists() {
        List<ResourceLocation> nodes = nodes("small", 1, "large", 10);
        Map<ResourceLocation, Integer> families = new java.util.LinkedHashMap<>();
        families.put(nodes.get(0), 0);
        nodes.subList(1, nodes.size()).forEach(nodeId -> families.put(nodeId, 1));

        ResearchTechTreeVisualMotifPlanner.Plan plan =
                ResearchTechTreeVisualMotifPlanner.partition(
                        nodes, 10, nodeId -> Optional.ofNullable(families.get(nodeId)));

        assertEquals(List.of(6, 5), plan.rows().stream().map(List::size).toList());
        assertEquals(1, plan.splitFamilyBoundaries());
        assertEquals(1, plan.mixedFamilyRows());
        assertEquals(0, plan.severelyUnderfilledRows());
    }

    @Test
    void oversizedFamilySplitsOnlyAsOftenAsCapacityRequires() {
        List<ResourceLocation> nodes = java.util.stream.IntStream.range(0, 13)
                .mapToObj(index -> id("test:oversized/" + index))
                .toList();

        ResearchTechTreeVisualMotifPlanner.Plan plan =
                ResearchTechTreeVisualMotifPlanner.partition(
                        nodes, 5, ignored -> Optional.of(0));

        assertEquals(3, plan.rows().size());
        assertEquals(2, plan.splitFamilyBoundaries());
        assertEquals(0, plan.severelyUnderfilledRows());
        assertTrue(plan.rows().stream().allMatch(row -> row.size() <= 5));
        assertEquals(nodes, plan.rows().stream().flatMap(List::stream).toList());
    }

    @Test
    void sharedBaseRetainsDenseBalancedWrapping() {
        List<ResourceLocation> nodes = java.util.stream.IntStream.range(0, 9)
                .mapToObj(index -> id("test:shared/" + index))
                .toList();

        ResearchTechTreeVisualMotifPlanner.Plan plan =
                ResearchTechTreeVisualMotifPlanner.partition(
                        nodes, 5, ignored -> Optional.empty());

        assertEquals(List.of(5, 4), plan.rows().stream().map(List::size).toList());
        assertEquals(0, plan.splitFamilyBoundaries());
        assertEquals(0, plan.mixedFamilyRows());
        assertEquals(0, plan.severelyUnderfilledRows());
    }

    @Test
    void maximumCatalogPartitionIsBoundedDeterministicAndOrderPreserving() {
        assertTimeout(Duration.ofSeconds(5), () -> {
            List<ResourceLocation> nodes = java.util.stream.IntStream.range(0, 4_096)
                    .mapToObj(index -> id("test:maximum/" + index))
                    .toList();
            ResearchTechTreeVisualMotifPlanner.Plan first =
                    ResearchTechTreeVisualMotifPlanner.partition(
                            nodes, 28, nodeId -> Optional.of(
                                    Integer.parseInt(nodeId.getPath().substring(8)) / 512));
            ResearchTechTreeVisualMotifPlanner.Plan second =
                    ResearchTechTreeVisualMotifPlanner.partition(
                            nodes, 28, nodeId -> Optional.of(
                                    Integer.parseInt(nodeId.getPath().substring(8)) / 512));

            assertEquals(first, second);
            assertEquals(147, first.rows().size());
            assertEquals(0, first.severelyUnderfilledRows());
            assertTrue(first.rows().stream().allMatch(row -> row.size() <= 28));
            assertEquals(nodes, first.rows().stream().flatMap(List::stream).toList());
        });
    }

    @Test
    void minimumCapacityMaximumCatalogUsesBoundedState() {
        assertTimeout(Duration.ofSeconds(5), () -> {
            List<ResourceLocation> nodes = java.util.stream.IntStream.range(
                            0, ResearchTreeGraph.MAX_NODES)
                    .mapToObj(index -> id("test:minimum_capacity/" + index))
                    .toList();

            ResearchTechTreeVisualMotifPlanner.Plan plan =
                    ResearchTechTreeVisualMotifPlanner.partition(
                            nodes, 1, ignored -> Optional.of(0));

            assertEquals(ResearchTreeGraph.MAX_NODES, plan.rows().size());
            assertTrue(plan.rows().stream().allMatch(row -> row.size() == 1));
            assertEquals(ResearchTreeGraph.MAX_NODES - 1, plan.splitFamilyBoundaries());
            assertEquals(0, plan.severelyUnderfilledRows());
            assertEquals(nodes, plan.rows().stream().flatMap(List::stream).toList());
        });
    }

    @Test
    void rejectsInvalidInputs() {
        ResourceLocation node = id("test:node");
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTechTreeVisualMotifPlanner.partition(
                        List.of(), 1, ignored -> Optional.empty()));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTechTreeVisualMotifPlanner.partition(
                        List.of(node, node), 1, ignored -> Optional.empty()));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTechTreeVisualMotifPlanner.partition(
                        List.of(node), 0, ignored -> Optional.empty()));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTechTreeVisualMotifPlanner.partition(
                        List.of(node), 29, ignored -> Optional.empty()));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTechTreeVisualMotifPlanner.partition(
                        List.of(node), 1, ignored -> null));
        List<ResourceLocation> oversizedCatalog = java.util.stream.IntStream.range(
                        0, ResearchTreeGraph.MAX_NODES + 1)
                .mapToObj(index -> id("test:too_many/" + index))
                .toList();
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTechTreeVisualMotifPlanner.partition(
                        oversizedCatalog, 28, ignored -> Optional.empty()));
    }

    private static List<ResourceLocation> nodes(Object... specifications) {
        List<ResourceLocation> result = new ArrayList<>();
        for (int index = 0; index < specifications.length; index += 2) {
            String prefix = (String) specifications[index];
            int count = (Integer) specifications[index + 1];
            for (int ordinal = 0; ordinal < count; ordinal++) {
                result.add(id("test:" + prefix + "/" + ordinal));
            }
        }
        return List.copyOf(result);
    }

    private static ResourceLocation id(String raw) {
        return new ResourceLocation(raw);
    }
}
