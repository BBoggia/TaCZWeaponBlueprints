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
        assertEquals(0, graph.node(id("test:d")).orElseThrow().hiddenPrerequisiteCount());
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
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeGraph.Node(
                0,
                id("test:target"),
                "name.target",
                "rifle",
                id("test:slot/target"),
                JournalVisibility.FULL,
                false, false, false, 8, 0, 1, 1,
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED));
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
