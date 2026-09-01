package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.DuplicateBlueprintPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchRule;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeBuilderTest {
    private static final ResourceLocation PROFILE = id("test:profile");

    @Test
    void buildsDeterministicPlayerSpecificTopologyFromExistingPrerequisites() {
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        catalog.put(id("test:d"), data("test:d"));
        catalog.put(id("test:b"), data("test:b"));
        catalog.put(id("test:a"), data("test:a"));
        catalog.put(id("test:c"), data("test:c"));

        BlueprintResearchSnapshot snapshot = snapshot(Map.of(
                id("test:b_rule"), rule("test:b", JournalVisibility.FULL, List.of(id("test:a"))),
                id("test:c_rule"), rule("test:c", JournalVisibility.FULL, List.of(id("test:a"))),
                id("test:d_rule"), rule(
                        "test:d",
                        JournalVisibility.FULL,
                        List.of(id("test:b"), id("test:c"), id("missing:hidden")))));
        PlayerRecipeData player = new PlayerRecipeData();
        player.addBlueprint("test:a");
        player.setResearchPoints(12);

        ResearchTreeGraph graph = ResearchTreeBuilder.build(
                catalog, snapshot, config(), player, ignored -> false);

        assertEquals(
                List.of(id("test:a"), id("test:b"), id("test:c"), id("test:d")),
                graph.nodes().stream().map(ResearchTreeGraph.Node::blueprintId).toList());
        assertEquals(
                List.of(
                        new ResearchTreeGraph.Edge(id("test:a"), id("test:b")),
                        new ResearchTreeGraph.Edge(id("test:a"), id("test:c")),
                        new ResearchTreeGraph.Edge(id("test:b"), id("test:d")),
                        new ResearchTreeGraph.Edge(id("test:c"), id("test:d"))),
                graph.edges());
        assertEquals(ResearchTreeGraph.State.LEARNED, graph.node(id("test:a")).orElseThrow().state());
        assertEquals(ResearchTreeGraph.State.AVAILABLE, graph.node(id("test:b")).orElseThrow().state());
        assertEquals(ResearchTreeGraph.State.LOCKED, graph.node(id("test:d")).orElseThrow().state());
        assertEquals(List.of(id("test:b"), id("test:c")), graph.prerequisitesOf(id("test:d")));
        assertEquals(2, graph.node(id("test:d")).orElseThrow().prerequisiteCount());
        assertEquals(1, graph.node(id("test:d")).orElseThrow().hiddenPrerequisiteCount());
        assertEquals(3, graph.requirementGroupsOf(id("test:d")).size());
    }

    @Test
    void nameOnlyNodesUseOpaqueKeysWithoutLeakingIdentityOrPolicy() {
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                id("test:dependent"), data("test:dependent"),
                id("test:secret"), data("test:secret"));
        BlueprintResearchSnapshot snapshot = snapshot(Map.of(
                id("test:dependent_rule"), rule(
                        "test:dependent", JournalVisibility.FULL, List.of(id("test:secret"))),
                id("test:secret_rule"), rule(
                        "test:secret", JournalVisibility.NAME, List.of())));

        ResearchTreeGraph graph = ResearchTreeBuilder.build(
                catalog, snapshot, config(), new PlayerRecipeData(), ignored -> false);

        assertEquals(2, graph.nodes().size());
        ResearchTreeGraph.Node named = graph.nodes().stream()
                .filter(node -> node.visibility() == JournalVisibility.NAME)
                .findFirst().orElseThrow();
        assertEquals(ResearchTreeGraph.redactedNodeId(named.ordinal()), named.blueprintId());
        assertEquals("name.secret", named.nameKey());
        assertEquals(ResearchTreeGraph.REDACTED_ITEM_TYPE, named.itemType());
        assertEquals(ResearchTreeGraph.REDACTED_DISPLAY_SLOT, named.displaySlotId());
        assertEquals(ResearchTreeGraph.Availability.REDACTED, named.availability());
        assertEquals(0, named.pointCost());
        assertTrue(graph.nodes().stream().noneMatch(node -> node.blueprintId().equals(id("test:secret"))));
        assertEquals(1, graph.edges().size());
        assertEquals(named.blueprintId(), graph.edges().get(0).prerequisiteId());
        assertEquals(id("test:dependent"), graph.edges().get(0).dependentId());
        assertEquals(0, graph.node(id("test:dependent")).orElseThrow().hiddenPrerequisiteCount());
    }

    @Test
    void publishesCanonicalChoiceGroupsAndTheirPlayerSpecificSatisfaction() {
        ResourceLocation advanced = id("test:advanced");
        ResourceLocation routeA = id("test:route_a");
        ResourceLocation routeB = id("test:route_b");
        ResourceLocation support = id("test:support");
        BlueprintResearchRule grouped = groupedRule(
                advanced,
                JournalVisibility.FULL,
                routeA,
                routeB,
                support);
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                advanced, data(advanced.toString()),
                routeA, data(routeA.toString()),
                routeB, data(routeB.toString()),
                support, data(support.toString()));
        PlayerRecipeData player = new PlayerRecipeData();
        player.addBlueprint(routeB.toString());

        ResearchTreeGraph graph = ResearchTreeBuilder.build(
                catalog,
                snapshot(Map.of(id("test:advanced_rule"), grouped)),
                config(),
                player,
                ignored -> false);

        List<ResearchTreeGraph.RequirementGroup> groups =
                graph.requirementGroupsOf(advanced);
        assertEquals(2, groups.size());
        assertEquals(List.of(routeA, routeB), groups.get(0).visibleAlternativeIds());
        assertTrue(groups.get(0).satisfactionDisclosed());
        assertTrue(groups.get(0).satisfied());
        assertEquals(List.of(support), groups.get(1).visibleAlternativeIds());
        assertEquals(3, graph.node(advanced).orElseThrow().prerequisiteCount());
        assertEquals(ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED,
                graph.node(advanced).orElseThrow().availability());
    }

    @Test
    void learnedOutOfOrderNodeDoesNotUnlockDependentsUntilConnectedToRoot() {
        ResourceLocation root = id("test:root");
        ResourceLocation missing = id("test:missing");
        ResourceLocation outOfOrder = id("test:out_of_order");
        ResourceLocation target = id("test:target");
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                root, data(root.toString()),
                missing, data(missing.toString()),
                outOfOrder, data(outOfOrder.toString()),
                target, data(target.toString()));
        BlueprintResearchSnapshot snapshot = snapshot(Map.of(
                id("test:missing_rule"),
                        rule(missing.toString(), JournalVisibility.FULL, List.of(root)),
                id("test:out_of_order_rule"),
                        rule(outOfOrder.toString(), JournalVisibility.FULL, List.of(missing)),
                id("test:target_rule"),
                        rule(target.toString(), JournalVisibility.FULL, List.of(outOfOrder))));
        PlayerRecipeData player = new PlayerRecipeData();
        player.addBlueprint(root.toString());
        player.addBlueprint(outOfOrder.toString());

        ResearchTreeGraph disconnected = ResearchTreeBuilder.build(
                catalog, snapshot, config(), player, ignored -> false);

        assertEquals(ResearchTreeGraph.State.LEARNED,
                disconnected.node(outOfOrder).orElseThrow().state());
        assertEquals(ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED,
                disconnected.node(target).orElseThrow().availability());
        assertTrue(disconnected.requirementGroupsOf(target).stream()
                .noneMatch(ResearchTreeGraph.RequirementGroup::satisfied));

        player.addBlueprint(missing.toString());
        ResearchTreeGraph repaired = ResearchTreeBuilder.build(
                catalog, snapshot, config(), player, ignored -> false);

        assertEquals(ResearchTreeGraph.Availability.AVAILABLE,
                repaired.node(target).orElseThrow().availability());
        assertTrue(repaired.requirementGroupsOf(target).stream()
                .allMatch(ResearchTreeGraph.RequirementGroup::satisfied));
    }

    @Test
    void previewGroupsDoNotDiscloseHiddenPlayerSatisfaction() {
        ResourceLocation advanced = id("test:advanced");
        ResourceLocation routeA = id("test:route_a");
        ResourceLocation routeB = id("test:route_b");
        ResourceLocation support = id("test:support");
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                advanced, data(advanced.toString()),
                routeA, data(routeA.toString()),
                routeB, data(routeB.toString()),
                support, data(support.toString()));
        PlayerRecipeData player = new PlayerRecipeData();
        player.addBlueprint(routeB.toString());
        player.addBlueprint(support.toString());

        ResearchTreeGraph graph = ResearchTreeBuilder.build(
                catalog,
                snapshot(Map.of(
                        id("test:advanced_rule"),
                        groupedRule(
                                advanced,
                                JournalVisibility.PREVIEW,
                                routeA,
                                routeB,
                                support))),
                config(),
                player,
                ignored -> false);

        List<ResearchTreeGraph.RequirementGroup> groups =
                graph.requirementGroupsOf(advanced);
        assertEquals(2, groups.size());
        assertTrue(groups.stream().noneMatch(
                ResearchTreeGraph.RequirementGroup::satisfactionDisclosed));
        assertTrue(groups.stream().noneMatch(
                ResearchTreeGraph.RequirementGroup::satisfied));
        assertEquals(ResearchTreeGraph.Availability.PREVIEW,
                graph.node(advanced).orElseThrow().availability());
    }

    @Test
    void everyVisibilityTierProducesItsOwnDisclosureShape() {
        Map<ResourceLocation, BlueprintData> catalog = Map.of(id("test:target"), data("test:target"));
        for (JournalVisibility visibility : JournalVisibility.values()) {
            BlueprintResearchSnapshot snapshot = snapshot(Map.of(
                    id("test:target_rule"), rule("test:target", visibility, List.of())));
            ResearchTreeGraph graph = ResearchTreeBuilder.build(
                    catalog, snapshot, config(), new PlayerRecipeData(), ignored -> false);

            if (visibility == JournalVisibility.HIDDEN) {
                assertTrue(graph.nodes().isEmpty());
                continue;
            }
            ResearchTreeGraph.Node node = graph.nodes().get(0);
            assertEquals(visibility, node.visibility());
            if (visibility.revealsIdentity()) {
                assertEquals(id("test:target"), node.blueprintId());
                assertEquals("rifle", node.itemType());
                assertEquals(8, node.pointCost());
            } else {
                assertEquals(ResearchTreeGraph.redactedNodeId(0), node.blueprintId());
                assertEquals(ResearchTreeGraph.REDACTED_ITEM_TYPE, node.itemType());
                assertEquals(0, node.pointCost());
                assertEquals(
                        visibility.revealsName() ? "name.target" : ResearchTreeGraph.REDACTED_NAME_KEY,
                        node.nameKey());
            }
        }
    }

    @Test
    void globalCeilingProducesFiveDistinctTreesFromAFullRule() {
        Map<ResourceLocation, BlueprintData> catalog = Map.of(id("test:target"), data("test:target"));
        BlueprintResearchSnapshot snapshot = snapshot(Map.of(
                id("test:target_rule"), rule("test:target", JournalVisibility.FULL, List.of())));

        for (JournalVisibility ceiling : JournalVisibility.values()) {
            ResearchTreeGraph graph = ResearchTreeBuilder.build(
                    catalog, snapshot, config(ceiling), new PlayerRecipeData(), ignored -> false);
            if (ceiling == JournalVisibility.HIDDEN) {
                assertTrue(graph.nodes().isEmpty());
            } else {
                assertEquals(ceiling, graph.nodes().get(0).visibility());
            }
        }
    }

    @Test
    void disabledResearchMasksEconomyAndTopologyAndCarriesAnExplicitReason() {
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                id("test:disabled"), data("test:disabled"),
                id("test:parent"), data("test:parent"));
        BlueprintResearchRule disabled = new BlueprintResearchRule(
                1,
                PROFILE,
                0,
                new BlueprintResearchTarget(List.of(id("test:disabled")), List.of(), Optional.empty()),
                Optional.of(JournalVisibility.FULL),
                Optional.of(false),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new BlueprintResearchCost(50, List.of())),
                Optional.empty(),
                Optional.of(List.of(id("test:parent"))),
                Optional.empty());

        ResearchTreeGraph graph = ResearchTreeBuilder.build(
                catalog,
                snapshot(Map.of(id("test:disabled_rule"), disabled)),
                config(),
                new PlayerRecipeData(),
                ignored -> false);

        ResearchTreeGraph.Node node = graph.node(id("test:disabled")).orElseThrow();
        assertEquals(ResearchTreeGraph.Availability.RESEARCH_DISABLED, node.availability());
        assertEquals(0, node.pointCost());
        assertEquals(0, node.ingredientTypeCount());
        assertEquals(0, node.prerequisiteCount());
        assertEquals(0, node.hiddenPrerequisiteCount());
        assertTrue(graph.prerequisitesOf(id("test:disabled")).isEmpty());
    }

    @Test
    void graphRejectsUnknownDuplicateAndCyclicEdges() {
        ResearchTreeGraph.Node a = node(0, "test:a", 1);
        ResearchTreeGraph.Node b = node(1, "test:b", 1);
        ResearchTreeGraph.Edge aToB = new ResearchTreeGraph.Edge(id("test:a"), id("test:b"));
        ResearchTreeGraph.Edge bToA = new ResearchTreeGraph.Edge(id("test:b"), id("test:a"));

        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeGraph(
                List.of(a, b), List.of(aToB, bToA)));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeGraph(
                List.of(node(0, "test:a", 2), b), List.of(aToB, aToB, bToA)));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeGraph(
                List.of(node(0, "test:a", 1)),
                List.of(new ResearchTreeGraph.Edge(id("test:missing"), id("test:a")))));
    }

    @Test
    void inducedProjectionPreservesCrossViewChoiceGroupsAsLinkedAlternatives() {
        ResearchTreeGraph.Node routeA = node(0, "test:route_a", 0);
        ResearchTreeGraph.Node routeB = node(1, "test:route_b", 0);
        ResearchTreeGraph.Node dependent = node(2, "test:dependent", 2);
        ResearchTreeGraph graph = ResearchTreeGraph.withRequirementGroups(
                List.of(routeA, routeB, dependent),
                List.of(new ResearchTreeGraph.RequirementGroup(
                        dependent.blueprintId(),
                        0,
                        List.of(routeA.blueprintId(), routeB.blueprintId()),
                        0,
                        false)));

        ResearchTreeGraph partial = graph.orderedInducedSubgraph(
                List.of(routeA.blueprintId(), dependent.blueprintId()));

        ResearchTreeGraph.RequirementGroup partialGroup =
                partial.requirementGroupsOf(dependent.blueprintId()).get(0);
        assertEquals(List.of(routeA.blueprintId()), partialGroup.visibleAlternativeIds());
        assertEquals(0, partialGroup.hiddenAlternativeCount());
        assertEquals(1, partialGroup.externalAlternativeCount());
        assertEquals(
                List.of(new ResearchTreeGraph.Edge(
                        routeA.blueprintId(), dependent.blueprintId())),
                partial.edges());
        assertEquals(1, partial.node(dependent.blueprintId()).orElseThrow().prerequisiteCount());
        assertEquals(0,
                partial.node(dependent.blueprintId()).orElseThrow().hiddenPrerequisiteCount());

        ResearchTreeGraph dependentOnly = graph.orderedInducedSubgraph(
                List.of(dependent.blueprintId()));
        ResearchTreeGraph.RequirementGroup externalOnly =
                dependentOnly.requirementGroupsOf(dependent.blueprintId()).get(0);
        assertTrue(externalOnly.visibleAlternativeIds().isEmpty());
        assertEquals(2, externalOnly.externalAlternativeCount());
        assertTrue(dependentOnly.edges().isEmpty());
        assertEquals(0,
                dependentOnly.node(dependent.blueprintId()).orElseThrow().prerequisiteCount());
    }

    @Test
    void graphRejectsIdentityAndPolicyLeaksFromRestrictedNodes() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeGraph.Node(
                0,
                id("taczweaponblueprints:undisclosed/0/test_secret"),
                ResearchTreeGraph.REDACTED_NAME_KEY,
                ResearchTreeGraph.REDACTED_ITEM_TYPE,
                ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                JournalVisibility.SILHOUETTE,
                false, false, false, 0, 0, 0, 0,
                ResearchTreeGraph.Availability.REDACTED));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeGraph.Node(
                0,
                ResearchTreeGraph.redactedNodeId(0),
                "name.secret",
                "rifle",
                ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                JournalVisibility.NAME,
                false, false, false, 0, 0, 0, 0,
                ResearchTreeGraph.Availability.REDACTED));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeGraph.Node(
                0,
                id("test:target"),
                "name.target",
                "rifle",
                id("test:slot/target"),
                JournalVisibility.PREVIEW,
                false, true, false, 8, 0, 0, 0,
                ResearchTreeGraph.Availability.PREVIEW));
        ResearchTreeGraph.Node mismatchedHiddenCount = new ResearchTreeGraph.Node(
                0,
                id("test:target"),
                "name.target",
                "rifle",
                id("test:slot/target"),
                JournalVisibility.FULL,
                false, false, false, 8, 0, 0, 1,
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeGraph(
                List.of(mismatchedHiddenCount), List.of(), List.of()));
    }

    @Test
    void unavailableInputsAndDisabledJournalProduceEmptyGraph() {
        assertEquals(ResearchTreeGraph.EMPTY, ResearchTreeBuilder.build(
                Map.of(), snapshot(Map.of()), config(), null, ignored -> false));
        BlueprintProgressionConfigSnapshot disabled = new BlueprintProgressionConfigSnapshot(
                true,
                true,
                false,
                JournalVisibility.FULL,
                true,
                DuplicateBlueprintPolicy.MANUAL_RECYCLING,
                false,
                100,
                false,
                PROFILE);
        assertEquals(ResearchTreeGraph.EMPTY, ResearchTreeBuilder.build(
                Map.of(id("test:a"), data("test:a")),
                snapshot(Map.of()),
                disabled,
                new PlayerRecipeData(),
                ignored -> false));
    }

    private static BlueprintResearchSnapshot snapshot(Map<ResourceLocation, BlueprintResearchRule> rules) {
        BlueprintResearchProfile profile = new BlueprintResearchProfile(
                1,
                true,
                JournalVisibility.FULL,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(8, List.of()),
                false,
                false);
        return BlueprintResearchSnapshot.create(Map.of(), Map.of(PROFILE, profile), rules);
    }

    private static BlueprintResearchRule rule(
            String target,
            JournalVisibility visibility,
            List<ResourceLocation> prerequisites) {
        return new BlueprintResearchRule(
                1,
                PROFILE,
                0,
                new BlueprintResearchTarget(List.of(id(target)), List.of(), Optional.empty()),
                Optional.of(visibility),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(prerequisites),
                Optional.empty());
    }

    private static BlueprintResearchRule groupedRule(
            ResourceLocation target,
            JournalVisibility visibility,
            ResourceLocation routeA,
            ResourceLocation routeB,
            ResourceLocation support) {
        return new BlueprintResearchRule(
                BlueprintResearchRule.CURRENT_FORMAT,
                PROFILE,
                0,
                new BlueprintResearchTarget(
                        List.of(target), List.of(), Optional.empty()),
                Optional.of(visibility),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new ResearchRequirements(List.of(
                        new ResearchPrerequisiteGroup(List.of(routeA, routeB)),
                        ResearchPrerequisiteGroup.singleton(support)))),
                Optional.empty(),
                Optional.empty());
    }

    private static BlueprintProgressionConfigSnapshot config() {
        return config(JournalVisibility.FULL);
    }

    private static BlueprintProgressionConfigSnapshot config(JournalVisibility maximum) {
        return new BlueprintProgressionConfigSnapshot(
                true,
                true,
                true,
                maximum,
                true,
                DuplicateBlueprintPolicy.MANUAL_RECYCLING,
                false,
                100,
                false,
                PROFILE);
    }

    private static ResearchTreeGraph.Node node(int ordinal, String id, int prerequisites) {
        return new ResearchTreeGraph.Node(
                ordinal,
                id(id),
                "name." + ordinal,
                "rifle",
                id("test:slot_" + ordinal),
                JournalVisibility.FULL,
                false,
                false,
                false,
                8,
                0,
                prerequisites,
                0,
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
    }

    private static BlueprintData data(String value) {
        ResourceLocation blueprintId = id(value);
        return new BlueprintData(
                value,
                "name." + blueprintId.getPath(),
                "tooltip." + blueprintId.getPath(),
                id("test:recipe/" + blueprintId.getPath()),
                null,
                "rifle",
                id("test:slot/" + blueprintId.getPath()));
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
