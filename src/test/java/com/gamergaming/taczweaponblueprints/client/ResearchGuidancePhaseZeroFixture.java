package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

/** Shared characterization fixtures for the research-guidance quality-of-life work. */
final class ResearchGuidancePhaseZeroFixture {
    static final ResourceLocation LEFT_CHEAP = id("guidance:left_cheap");
    static final ResourceLocation LEFT_EXPENSIVE = id("guidance:left_expensive");
    static final ResourceLocation RIGHT_CHEAP = id("guidance:right_cheap");
    static final ResourceLocation RIGHT_EXPENSIVE = id("guidance:right_expensive");
    static final ResourceLocation MERGE_TARGET = id("guidance:merge_target");
    static final ResourceLocation ALPHA = id("guidance:alpha");
    static final ResourceLocation BETA = id("guidance:beta");
    static final ResourceLocation TIED_TARGET = id("guidance:tied_target");

    private static final ResourceLocation PAPER = id("minecraft:paper");

    private ResearchGuidancePhaseZeroFixture() {
    }

    /** Two independent any-of requirements that must both reach one target. */
    static ResearchTreeGraph andOfAnyOfRoute() {
        return ResearchTreeGraph.withRequirementGroups(
                List.of(
                        availableNode(0, LEFT_CHEAP, 2, 1),
                        availableNode(1, LEFT_EXPENSIVE, 7, 1),
                        availableNode(2, RIGHT_CHEAP, 3, 1),
                        availableNode(3, RIGHT_EXPENSIVE, 9, 1),
                        lockedNode(4, MERGE_TARGET, 5, 1, 4)),
                List.of(
                        new ResearchTreeGraph.RequirementGroup(
                                MERGE_TARGET,
                                0,
                                List.of(LEFT_EXPENSIVE, LEFT_CHEAP),
                                0,
                                false),
                        new ResearchTreeGraph.RequirementGroup(
                                MERGE_TARGET,
                                1,
                                List.of(RIGHT_EXPENSIVE, RIGHT_CHEAP),
                                0,
                                false)));
    }

    /** Same disclosed cost and shape; the current estimate falls back to stable ID order. */
    static ResearchTreeGraph equalCostAlternatives() {
        return ResearchTreeGraph.withRequirementGroups(
                List.of(
                        availableNode(0, BETA, 3, 1),
                        availableNode(1, ALPHA, 3, 1),
                        lockedNode(2, TIED_TARGET, 5, 1, 2)),
                List.of(new ResearchTreeGraph.RequirementGroup(
                        TIED_TARGET,
                        0,
                        List.of(BETA, ALPHA),
                        0,
                        false)));
    }

    static Map<ResearchCostMode, ResearchSelectionPreview> previewsByCostMode() {
        EnumMap<ResearchCostMode, ResearchSelectionPreview> previews =
                new EnumMap<>(ResearchCostMode.class);
        previews.put(
                ResearchCostMode.POINTS_AND_ITEMS,
                preview(
                        "points_and_items",
                        6,
                        8,
                        List.of(ingredient(4, 4)),
                        1,
                        true,
                        true,
                        ResearchCostMode.POINTS_AND_ITEMS));
        previews.put(
                ResearchCostMode.POINTS_ONLY,
                preview(
                        "points_only",
                        6,
                        8,
                        List.of(),
                        0,
                        true,
                        true,
                        ResearchCostMode.POINTS_ONLY));
        previews.put(
                ResearchCostMode.ITEMS_ONLY,
                preview(
                        "items_only",
                        0,
                        8,
                        List.of(ingredient(4, 3)),
                        1,
                        false,
                        false,
                        ResearchCostMode.ITEMS_ONLY));
        return Map.copyOf(previews);
    }

    static ResearchSelectionPreview creativeBypassPreview() {
        return new ResearchSelectionPreview(
                Optional.of(id("guidance:creative")),
                6,
                0,
                true,
                true,
                true,
                true,
                true,
                List.of(ingredient(4, 0)),
                3,
                1,
                ResearchSelectionPreview.PathPlanningState.NONE,
                ResearchCostMode.POINTS_AND_ITEMS);
    }

    static ResearchTreeGraph maximumIndependentGraph() {
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>(ResearchTreeGraph.MAX_NODES);
        for (int ordinal = 0; ordinal < ResearchTreeGraph.MAX_NODES; ordinal++) {
            nodes.add(availableNode(
                    ordinal,
                    id("guidance:maximum/" + ordinal),
                    1,
                    0));
        }
        return new ResearchTreeGraph(nodes, List.of());
    }

    private static ResearchSelectionPreview preview(
            String path,
            int pointCost,
            int pointBalance,
            List<ResearchSelectionPreview.IngredientPreview> ingredients,
            int ingredientTypeCount,
            boolean ingredientsSatisfied,
            boolean researchable,
            ResearchCostMode costMode) {
        return new ResearchSelectionPreview(
                Optional.of(id("guidance:" + path)),
                pointCost,
                pointBalance,
                true,
                ingredientsSatisfied,
                true,
                researchable,
                false,
                ingredients,
                3,
                ingredientTypeCount,
                ResearchSelectionPreview.PathPlanningState.NONE,
                costMode);
    }

    private static ResearchSelectionPreview.IngredientPreview ingredient(
            int required,
            int allocated) {
        return new ResearchSelectionPreview.IngredientPreview(
                List.of(PAPER), Optional.empty(), required, allocated);
    }

    private static ResearchTreeGraph.Node availableNode(
            int ordinal,
            ResourceLocation blueprintId,
            int points,
            int ingredientTypes) {
        return node(
                ordinal,
                blueprintId,
                points,
                ingredientTypes,
                0,
                ResearchTreeGraph.Availability.AVAILABLE);
    }

    private static ResearchTreeGraph.Node lockedNode(
            int ordinal,
            ResourceLocation blueprintId,
            int points,
            int ingredientTypes,
            int prerequisiteCount) {
        return node(
                ordinal,
                blueprintId,
                points,
                ingredientTypes,
                prerequisiteCount,
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            ResourceLocation blueprintId,
            int points,
            int ingredientTypes,
            int prerequisiteCount,
            ResearchTreeGraph.Availability availability) {
        return new ResearchTreeGraph.Node(
                ordinal,
                blueprintId,
                "fixture.guidance." + blueprintId.getPath().replace('/', '.'),
                "gun",
                id("guidance:slot/" + ordinal),
                JournalVisibility.FULL,
                false,
                true,
                availability == ResearchTreeGraph.Availability.AVAILABLE,
                points,
                ingredientTypes,
                prerequisiteCount,
                0,
                availability);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}

