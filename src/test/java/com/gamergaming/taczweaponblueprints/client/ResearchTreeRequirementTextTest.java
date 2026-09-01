package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

class ResearchTreeRequirementTextTest {
    @Test
    void partiallyPublishedAnyOfGroupNamesOnlyVisibleAlternatives() {
        ResearchTreeGraph graph = ResearchTreeGraph.withRequirementGroups(
                List.of(
                        node(0, "test:visible", 0, 0),
                        node(1, "test:target", 1, 1)),
                List.of(new ResearchTreeGraph.RequirementGroup(
                        id("test:target"),
                        0,
                        List.of(id("test:visible")),
                        1,
                        1,
                        true,
                        false)));
        List<ResourceLocation> named = new ArrayList<>();

        List<Component> details = ResearchTreeRequirementText.details(
                graph,
                id("test:target"),
                node -> {
                    named.add(node.blueprintId());
                    return Component.literal(node.blueprintId().toString());
                });

        assertEquals(List.of(id("test:visible")), named);
        assertEquals(4, details.size());
        assertEquals(
                "gui.taczweaponblueprints.research_bench.tree.requirement.any_of.unsatisfied",
                translationKey(details.get(0)));
        assertEquals(
                "gui.taczweaponblueprints.research_bench.tree.requirement.undiscovered",
                translationKey(details.get(2)));
        assertEquals(
                "gui.taczweaponblueprints.research_bench.tree.requirement.outside_view",
                translationKey(details.get(3)));
        assertEquals(
                "gui.taczweaponblueprints.research_bench.tree.card.requirement_any_of",
                translationKey(ResearchTreeRequirementText.summary(
                        graph, id("test:target")).orElseThrow()));
    }

    @Test
    void singletonOnlyRequirementsDoNotClaimToBeChoices() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, "test:source", 0, 0),
                        node(1, "test:target", 1, 0)),
                List.of(new ResearchTreeGraph.Edge(
                        id("test:source"), id("test:target"))));

        assertTrue(ResearchTreeRequirementText.summary(
                graph, id("test:target")).isEmpty());
        assertTrue(ResearchTreeRequirementText.details(
                graph, id("test:target"), ignored -> Component.empty()).isEmpty());
    }

    @Test
    void previewVisibilityDoesNotRevealGroupSatisfaction() {
        ResearchTreeGraph.Node previewTarget = new ResearchTreeGraph.Node(
                2,
                id("test:preview_target"),
                "name.preview_target",
                "rifle",
                id("test:slot/preview"),
                JournalVisibility.PREVIEW,
                false,
                false,
                false,
                8,
                0,
                2,
                0,
                ResearchTreeGraph.Availability.PREVIEW);
        ResearchTreeGraph graph = ResearchTreeGraph.withRequirementGroups(
                List.of(
                        node(0, "test:a", 0, 0),
                        node(1, "test:b", 0, 0),
                        previewTarget),
                List.of(new ResearchTreeGraph.RequirementGroup(
                        previewTarget.blueprintId(),
                        0,
                        List.of(id("test:a"), id("test:b")),
                        0,
                        false,
                        false)));

        assertEquals(
                "gui.taczweaponblueprints.research_bench.tree.requirement.any_of.undisclosed",
                translationKey(ResearchTreeRequirementText.details(
                        graph,
                        previewTarget.blueprintId(),
                        node -> Component.literal(node.nameKey())).get(0)));
        assertEquals(
                "gui.taczweaponblueprints.research_bench.tree.card.requirement_any_of.undisclosed",
                translationKey(ResearchTreeRequirementText.summary(
                        graph, previewTarget.blueprintId()).orElseThrow()));
    }

    @Test
    void hiddenMandatorySingletonRemainsVisibleAsAnAnonymousRequirement() {
        ResearchTreeGraph graph = ResearchTreeGraph.withRequirementGroups(
                List.of(node(0, "test:target", 0, 1)),
                List.of(new ResearchTreeGraph.RequirementGroup(
                        id("test:target"),
                        0,
                        List.of(),
                        1,
                        true,
                        false)));

        List<Component> details = ResearchTreeRequirementText.details(
                graph,
                id("test:target"),
                ignored -> {
                    throw new AssertionError("a hidden alternative has no public node name");
                });

        assertEquals(2, details.size());
        assertEquals(
                "gui.taczweaponblueprints.research_bench.tree.requirement.mandatory.unsatisfied",
                translationKey(details.get(0)));
        assertEquals(
                "gui.taczweaponblueprints.research_bench.tree.requirement.undiscovered",
                translationKey(details.get(1)));
        assertEquals(
                "gui.taczweaponblueprints.research_bench.tree.card.requirement_opaque",
                translationKey(ResearchTreeRequirementText.summary(
                        graph, id("test:target")).orElseThrow()));
    }

    private static String translationKey(Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            String value,
            int visiblePrerequisites,
            int hiddenPrerequisites) {
        ResourceLocation blueprintId = id(value);
        return new ResearchTreeGraph.Node(
                ordinal,
                blueprintId,
                "name." + blueprintId.getPath(),
                "rifle",
                id("test:slot/" + ordinal),
                JournalVisibility.FULL,
                false,
                false,
                visiblePrerequisites == 0 && hiddenPrerequisites == 0,
                8,
                0,
                visiblePrerequisites,
                hiddenPrerequisites,
                visiblePrerequisites == 0 && hiddenPrerequisites == 0
                        ? ResearchTreeGraph.Availability.AVAILABLE
                        : ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
