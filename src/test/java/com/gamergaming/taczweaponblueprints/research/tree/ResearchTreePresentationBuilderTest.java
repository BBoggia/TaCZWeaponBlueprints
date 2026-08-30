package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeOverviewBuilder;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.DuplicateBlueprintPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchRule;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTreeGroupDefinition;

import net.minecraft.resources.ResourceLocation;

class ResearchTreePresentationBuilderTest {
    private static final ResourceLocation PROFILE = id("test:profile");

    @Test
    void authoritativeGraphAcceptsEveryKindWhileLegacyPresentationStaysWeaponOnly() {
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        catalog.put(id("test:weapon"),
                data("test:weapon", "rifle", BlueprintKind.GUN));
        catalog.put(id("test:attachment"),
                data("test:attachment", "scope", BlueprintKind.ATTACHMENT));
        catalog.put(id("test:ammo"),
                data("test:ammo", "ammo", BlueprintKind.AMMO));
        catalog.put(id("test:hidden_attachment"),
                data("test:hidden_attachment", "scope", BlueprintKind.ATTACHMENT));

        Map<ResourceLocation, BlueprintResearchRule> rules = Map.of(
                id("test:weapon_rule"),
                rule("test:weapon", JournalVisibility.FULL, List.of()),
                id("test:attachment_rule"),
                rule("test:attachment", JournalVisibility.FULL, List.of()),
                id("test:ammo_rule"),
                rule("test:ammo", JournalVisibility.FULL, List.of()),
                id("test:hidden_attachment_rule"),
                rule("test:hidden_attachment", JournalVisibility.SILHOUETTE, List.of()));
        Map<ResourceLocation, ResearchTreeGroupDefinition> groups = Map.of(
                id("test:weapons"), group(
                        "Weapons",
                        id("test:weapon"),
                        10,
                        List.of(List.of(id("test:weapon")))));

        ResearchTreePublication publication = ResearchTreeBuilder.buildPublication(
                catalog,
                snapshot(rules, groups),
                config(),
                new PlayerRecipeData(),
                ignored -> false);

        assertEquals(4, publication.graph().nodes().size());
        assertTrue(publication.graph().node(id("test:attachment")).isPresent());
        assertTrue(publication.graph().node(id("test:ammo")).isPresent());
        assertTrue(publication.graph().nodes().stream().anyMatch(node ->
                !node.visibility().revealsIdentity()));
        assertEquals(List.of(id("test:weapon")), publication.presentation().groups().stream()
                .flatMap(group -> group.members().stream())
                .map(ResearchTreePresentation.Member::nodeId)
                .toList());
        assertTrue(publication.presentation().membership(id("test:attachment")).isEmpty());
        assertTrue(publication.presentation().membership(id("test:ammo")).isEmpty());
        assertEquals(List.of(id("test:weapon")), publication.legacyGraph().nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .toList());
    }

    @Test
    void publishesOnlyMetadataAllowedByEachVisibilityTier() {
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        catalog.put(id("test:hidden"), data("test:hidden", "pistol"));
        catalog.put(id("test:silhouette"), data("test:silhouette", "pistol"));
        catalog.put(id("test:name"), data("test:name", "pistol"));
        catalog.put(id("test:preview"), data("test:preview", "pistol"));
        catalog.put(id("test:full"), data("test:full", "pistol"));
        catalog.put(id("test:addon"), data("test:addon", "launcher"));

        Map<ResourceLocation, BlueprintResearchRule> rules = Map.of(
                id("test:hidden_rule"), rule("test:hidden", JournalVisibility.HIDDEN, List.of()),
                id("test:silhouette_rule"), rule("test:silhouette", JournalVisibility.SILHOUETTE, List.of()),
                id("test:name_rule"), rule("test:name", JournalVisibility.NAME, List.of()),
                id("test:preview_rule"), rule(
                        "test:preview", JournalVisibility.PREVIEW, List.of(id("test:name"))),
                id("test:full_rule"), rule("test:full", JournalVisibility.FULL, List.of()),
                id("test:addon_rule"), rule("test:addon", JournalVisibility.FULL, List.of()));
        Map<ResourceLocation, ResearchTreeGroupDefinition> groups = Map.of(
                id("test:anonymous_branch"), group(
                        "Secret Branch",
                        id("test:silhouette"),
                        10,
                        List.of(List.of(id("test:silhouette")))),
                id("test:mixed_branch"), group(
                        "Mixed Branch",
                        id("test:name"),
                        70,
                        List.of(
                                List.of(id("test:hidden")),
                                List.of(id("test:name")),
                                List.of(id("test:preview"), id("test:full")))));

        ResearchTreePublication publication = ResearchTreeBuilder.buildPublication(
                catalog,
                snapshot(rules, groups),
                config(),
                new PlayerRecipeData(),
                ignored -> false);

        assertEquals(5, publication.graph().nodes().size());
        assertTrue(publication.graph().node(id("test:hidden")).isEmpty());
        assertEquals(3, publication.presentation().groups().size());
        assertTrue(publication.presentation().groups().stream()
                .noneMatch(group -> group.title().equals("Secret Branch")));

        ResearchTreePresentation.Group authored = publication.presentation().groups().get(0);
        assertEquals("Mixed Branch", authored.title());
        assertEquals(ResearchTreePresentation.Kind.AUTHORED, authored.kind());
        assertTrue(authored.includedInOverview());
        assertEquals(0, authored.order());
        assertTrue(authored.iconNodeId().isEmpty());
        assertEquals(List.of(0, 1), authored.members().stream()
                .map(ResearchTreePresentation.Member::rank)
                .toList());
        assertEquals(List.of(0, 0), authored.members().stream()
                .map(ResearchTreePresentation.Member::orderInRank)
                .toList());
        assertEquals(
                List.of(id("test:full"), id("test:preview")),
                authored.members().stream().map(ResearchTreePresentation.Member::nodeId).toList());

        ResearchTreePresentation.Group fallback = publication.presentation().groups().get(1);
        assertEquals(ResearchTreePresentation.Kind.ITEM_TYPE_FALLBACK, fallback.kind());
        assertTrue(fallback.includedInOverview());
        assertEquals("Other: Launcher", fallback.title());
        assertEquals(Optional.of(id("test:addon")), fallback.iconNodeId());

        ResearchTreeOverviewBuilder.Result overview =
                ResearchTreeOverviewBuilder.build(publication);
        assertTrue(overview.publication().graph().node(id("test:addon")).isPresent());
        assertTrue(overview.publication().presentation().group(fallback.id()).isPresent());
        assertTrue(overview.publication().presentation().groups().stream()
                .noneMatch(group -> group.kind() == ResearchTreePresentation.Kind.UNDISCLOSED));

        ResearchTreePresentation.Group undisclosed = publication.presentation().groups().get(2);
        assertEquals(ResearchTreePresentation.Kind.UNDISCLOSED, undisclosed.kind());
        assertFalse(undisclosed.includedInOverview());
        assertEquals(2, undisclosed.members().size());
        assertTrue(undisclosed.iconNodeId().isEmpty());
        assertTrue(undisclosed.members().stream().allMatch(member ->
                member.nodeId().getPath().startsWith("undisclosed/")));
        ResourceLocation publishedNameId = publication.graph().nodes().stream()
                .filter(node -> node.visibility() == JournalVisibility.NAME)
                .findFirst().orElseThrow().blueprintId();
        assertTrue(publication.graph().edges().contains(
                new ResearchTreeGraph.Edge(publishedNameId, id("test:preview"))));
        assertEquals(
                undisclosed.id(),
                publication.presentation().membership(publishedNameId).orElseThrow().groupId());
        assertEquals(0, publication.presentation().membership(publishedNameId).orElseThrow().rank());
        assertEquals(1, publication.presentation().membership(id("test:preview")).orElseThrow().rank());
        assertTrue(publication.presentation().groups().stream()
                .noneMatch(group -> group.members().stream().anyMatch(member ->
                        member.nodeId().equals(id("test:name"))
                                || member.nodeId().equals(id("test:silhouette")))));
    }

    @Test
    void groupLessDatapacksReceiveDeterministicItemTypeFallbackRanks() {
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        catalog.put(id("test:c"), data("test:c", "smg"));
        catalog.put(id("test:b"), data("test:b", "rifle"));
        catalog.put(id("test:a"), data("test:a", "rifle"));
        Map<ResourceLocation, BlueprintResearchRule> rules = Map.of(
                id("test:a_rule"), rule("test:a", JournalVisibility.PREVIEW, List.of()),
                id("test:b_rule"), rule("test:b", JournalVisibility.PREVIEW, List.of(id("test:a"))),
                id("test:c_rule"), rule("test:c", JournalVisibility.PREVIEW, List.of(id("test:b"))));

        ResearchTreePublication publication = ResearchTreeBuilder.buildPublication(
                catalog,
                snapshot(rules, Map.of()),
                config(),
                new PlayerRecipeData(),
                ignored -> false);

        assertEquals(2, publication.presentation().groups().size());
        ResearchTreePresentation.Group rifle = publication.presentation().groups().stream()
                .filter(group -> group.title().equals("Other: Rifle"))
                .findFirst().orElseThrow();
        ResearchTreePresentation.Group smg = publication.presentation().groups().stream()
                .filter(group -> group.title().equals("Other: Smg"))
                .findFirst().orElseThrow();
        assertEquals(List.of(0, 1), rifle.members().stream()
                .map(ResearchTreePresentation.Member::rank)
                .toList());
        assertEquals(2, smg.members().get(0).rank());
        assertEquals(Optional.of(id("test:a")), rifle.iconNodeId());

        ResearchTreePublication repeated = ResearchTreeBuilder.buildPublication(
                new LinkedHashMap<>(Map.of(
                        id("test:a"), data("test:a", "rifle"),
                        id("test:b"), data("test:b", "rifle"),
                        id("test:c"), data("test:c", "smg"))),
                snapshot(rules, Map.of()),
                config(),
                new PlayerRecipeData(),
                ignored -> false);
        assertEquals(publication, repeated);
    }

    @Test
    void authoredOverviewOverrideIsResolvedIntoPublishedMetadata() {
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                id("test:included"), data("test:included", "rifle"),
                id("test:excluded"), data("test:excluded", "rifle"));
        Map<ResourceLocation, BlueprintResearchRule> rules = Map.of(
                id("test:included_rule"), rule(
                        "test:included", JournalVisibility.FULL, List.of()),
                id("test:excluded_rule"), rule(
                        "test:excluded", JournalVisibility.FULL, List.of()));
        Map<ResourceLocation, ResearchTreeGroupDefinition> groups = Map.of(
                id("test:included_group"), group(
                        "Included",
                        id("test:included"),
                        10,
                        Optional.of(true),
                        List.of(List.of(id("test:included")))),
                id("test:excluded_group"), group(
                        "Excluded",
                        id("test:excluded"),
                        20,
                        Optional.of(false),
                        List.of(List.of(id("test:excluded")))));

        ResearchTreePresentation presentation = ResearchTreeBuilder.buildPublication(
                catalog,
                snapshot(rules, groups),
                config(),
                new PlayerRecipeData(),
                ignored -> false).presentation();

        assertTrue(presentation.group(id("test:included_group"))
                .orElseThrow().includedInOverview());
        assertFalse(presentation.group(id("test:excluded_group"))
                .orElseThrow().includedInOverview());
    }

    @Test
    void syntheticGroupsAvoidAuthoredIdsAndHiddenAuthoredOrdersAreCompacted() {
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                id("test:hidden"), data("test:hidden", "rifle"),
                id("test:authored"), data("test:authored", "rifle"),
                id("test:fallback"), data("test:fallback", "rifle"));
        Map<ResourceLocation, BlueprintResearchRule> rules = Map.of(
                id("test:hidden_rule"), rule("test:hidden", JournalVisibility.HIDDEN, List.of()),
                id("test:authored_rule"), rule("test:authored", JournalVisibility.FULL, List.of()),
                id("test:fallback_rule"), rule("test:fallback", JournalVisibility.FULL, List.of()));
        Map<ResourceLocation, ResearchTreeGroupDefinition> groups = Map.of(
                id("test:hidden_group"), group(
                        "Hidden Group", id("test:hidden"), 10, List.of(List.of(id("test:hidden")))),
                id("taczweaponblueprints:published/fallback"), group(
                        "Authored",
                        id("test:authored"),
                        ResearchTreeGroupDefinition.MAX_ORDER,
                        List.of(List.of(id("test:authored")))));

        ResearchTreePresentation presentation = ResearchTreeBuilder.buildPublication(
                catalog,
                snapshot(rules, groups),
                config(),
                new PlayerRecipeData(),
                ignored -> false).presentation();

        assertEquals(2, presentation.groups().size());
        assertEquals(List.of(0, 1), presentation.groups().stream()
                .map(ResearchTreePresentation.Group::order)
                .toList());
        assertTrue(presentation.groups().stream().noneMatch(group -> group.title().equals("Hidden Group")));
        assertEquals(id("taczweaponblueprints:published/fallback"), presentation.groups().get(0).id());
        assertNotEquals(presentation.groups().get(0).id(), presentation.groups().get(1).id());
    }

    @Test
    void publicAuthoredRanksStayComparableAcrossGroupsWithoutMissingRankGaps() {
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                id("test:prerequisite"), data("test:prerequisite", "rifle"),
                id("test:dependent"), data("test:dependent", "smg"));
        Map<ResourceLocation, BlueprintResearchRule> rules = Map.of(
                id("test:prerequisite_rule"), rule(
                        "test:prerequisite", JournalVisibility.FULL, List.of()),
                id("test:dependent_rule"), rule(
                        "test:dependent", JournalVisibility.FULL, List.of(id("test:prerequisite"))));
        Map<ResourceLocation, ResearchTreeGroupDefinition> groups = Map.of(
                id("test:rifles"), group(
                        "Rifles",
                        id("test:prerequisite"),
                        10,
                        List.of(
                                List.of(id("missing:rifle_rank_0")),
                                List.of(id("missing:rifle_rank_1")),
                                List.of(id("test:prerequisite")))),
                id("test:smgs"), group(
                        "SMGs",
                        id("test:dependent"),
                        20,
                        List.of(
                                List.of(id("missing:smg_rank_0")),
                                List.of(id("missing:smg_rank_1")),
                                List.of(id("missing:smg_rank_2")),
                                List.of(id("test:dependent")))));

        ResearchTreePresentation presentation = ResearchTreeBuilder.buildPublication(
                catalog,
                snapshot(rules, groups),
                config(),
                new PlayerRecipeData(),
                ignored -> false).presentation();

        assertEquals(0, presentation.membership(id("test:prerequisite")).orElseThrow().rank());
        assertEquals(1, presentation.membership(id("test:dependent")).orElseThrow().rank());
    }

    @Test
    void normalizesEdgesFromFallbackGroupsIntoAuthoredGroups() {
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                id("test:prerequisite"), data("test:prerequisite", "rifle"),
                id("test:dependent"), data("test:dependent", "smg"));
        Map<ResourceLocation, BlueprintResearchRule> rules = Map.of(
                id("test:prerequisite_rule"), rule(
                        "test:prerequisite", JournalVisibility.FULL, List.of()),
                id("test:dependent_rule"), rule(
                        "test:dependent", JournalVisibility.FULL, List.of(id("test:prerequisite"))));
        Map<ResourceLocation, ResearchTreeGroupDefinition> groups = Map.of(
                id("test:smgs"), group(
                        "SMGs",
                        id("test:dependent"),
                        10,
                        List.of(List.of(id("test:dependent")))));

        ResearchTreePresentation presentation = ResearchTreeBuilder.buildPublication(
                catalog,
                snapshot(rules, groups),
                config(),
                new PlayerRecipeData(),
                ignored -> false).presentation();

        assertEquals(0, presentation.membership(id("test:prerequisite")).orElseThrow().rank());
        assertEquals(1, presentation.membership(id("test:dependent")).orElseThrow().rank());
    }

    @Test
    void publicationAllowsNonLegacyNodesButRejectsMisclassifiedMembersAndAnonymousIcons() {
        ResearchTreeGraph previewGraph = new ResearchTreeGraph(
                List.of(node(0, id("test:preview"), JournalVisibility.PREVIEW)),
                List.of());
        ResearchTreePublication mixedKindPublication = new ResearchTreePublication(
                previewGraph,
                ResearchTreePresentation.EMPTY);
        assertEquals(previewGraph, mixedKindPublication.graph());
        assertTrue(mixedKindPublication.legacyGraph().nodes().isEmpty());

        ResearchTreePresentation.Group wrongGroup = new ResearchTreePresentation.Group(
                ResearchTreePresentation.PREFERRED_UNDISCLOSED_GROUP_ID,
                ResearchTreePresentation.UNDISCLOSED_TITLE,
                Optional.of(ResearchTreePresentation.UNDISCLOSED_TRANSLATION_KEY),
                Optional.empty(),
                0,
                ResearchTreePresentation.Kind.UNDISCLOSED,
                List.of(new ResearchTreePresentation.Member(id("test:preview"), 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreePublication(
                previewGraph,
                new ResearchTreePresentation(List.of(wrongGroup))));

        ResourceLocation redactedId = ResearchTreeGraph.redactedNodeId(0);
        ResearchTreeGraph anonymousGraph = new ResearchTreeGraph(
                List.of(node(0, redactedId, JournalVisibility.NAME)),
                List.of());
        ResearchTreePresentation.Group identifying = new ResearchTreePresentation.Group(
                id("test:identifying"),
                "Identifying",
                Optional.empty(),
                Optional.of(redactedId),
                0,
                ResearchTreePresentation.Kind.AUTHORED,
                List.of(new ResearchTreePresentation.Member(redactedId, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreePublication(
                anonymousGraph,
                new ResearchTreePresentation(List.of(identifying))));

        ResearchTreeGraph rankedGraph = new ResearchTreeGraph(
                List.of(
                        node(0, id("test:prerequisite"), JournalVisibility.PREVIEW),
                        node(1, id("test:dependent"), JournalVisibility.PREVIEW, 1)),
                List.of(new ResearchTreeGraph.Edge(
                        id("test:prerequisite"), id("test:dependent"))));
        ResearchTreePresentation.Group flatGroup = new ResearchTreePresentation.Group(
                id("test:flat"),
                "Flat",
                Optional.empty(),
                Optional.of(id("test:prerequisite")),
                0,
                ResearchTreePresentation.Kind.AUTHORED,
                List.of(
                        new ResearchTreePresentation.Member(id("test:dependent"), 0, 0),
                        new ResearchTreePresentation.Member(id("test:prerequisite"), 0, 1)));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreePublication(
                rankedGraph,
                new ResearchTreePresentation(List.of(flatGroup))));
    }

    @Test
    void playerStateOnlyChangesReusePresentationTopology() {
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                id("test:target"), data("test:target", "rifle"));
        BlueprintResearchSnapshot snapshot = snapshot(
                Map.of(id("test:rule"), rule("test:target", JournalVisibility.FULL, List.of())),
                Map.of(id("test:group"), group(
                        "Rifles", id("test:target"), 10, List.of(List.of(id("test:target"))))));
        PlayerRecipeData before = new PlayerRecipeData();
        before.setResearchPoints(20);
        PlayerRecipeData after = new PlayerRecipeData();
        after.setResearchPoints(20);
        after.addBlueprint("test:target");

        ResearchTreePublication first = ResearchTreeBuilder.buildPublication(
                catalog, snapshot, config(), before, ignored -> false);
        ResearchTreePublication second = ResearchTreeBuilder.buildPublication(
                catalog, snapshot, config(), after, ignored -> false);

        assertFalse(first.graph().equals(second.graph()));
        assertTrue(first.hasSamePresentationTopology(second));
    }

    @Test
    void maximumPublicPopulationCanReceiveOneFallbackGroupPerNode() {
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        for (int index = 0; index < ResearchTreeGraph.MAX_NODES; index++) {
            String blueprintId = "test:node_" + index;
            catalog.put(id(blueprintId), data(blueprintId, "type_" + index));
        }

        ResearchTreePublication publication = assertTimeout(
                Duration.ofSeconds(8),
                () -> ResearchTreeBuilder.buildPublication(
                        catalog,
                        snapshot(Map.of(), Map.of()),
                        config(),
                        new PlayerRecipeData(),
                        ignored -> false));

        assertEquals(ResearchTreeGraph.MAX_NODES, publication.graph().nodes().size());
        assertEquals(ResearchTreePresentation.MAX_GROUPS, publication.presentation().groups().size());
        assertEquals(
                ResearchTreePresentation.MAX_GROUPS - 1,
                publication.presentation().groups().get(
                        ResearchTreePresentation.MAX_GROUPS - 1).order());
    }

    @Test
    void maximumMixedVisibilityPublicationRemainsBoundedAndDisclosureSafe() {
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        Map<ResourceLocation, BlueprintResearchRule> rules = new LinkedHashMap<>();
        List<ResourceLocation> authoredMembers = new ArrayList<>();
        JournalVisibility[] tiers = JournalVisibility.values();
        for (int index = 0; index < ResearchTreeGraph.MAX_NODES; index++) {
            String value = "test:stress_" + index;
            ResourceLocation blueprintId = id(value);
            JournalVisibility visibility = tiers[index % tiers.length];
            catalog.put(blueprintId, data(value, "stress_type"));
            rules.put(
                    id("test:stress_rule_" + index),
                    rule(value, visibility, List.of()));
            authoredMembers.add(blueprintId);
        }
        ResearchTreeGroupDefinition authoredGroup = group(
                "Maximum Mixed",
                id("test:stress_4"),
                10,
                List.of(authoredMembers));

        ResearchTreePublication publication = assertTimeout(
                Duration.ofSeconds(8),
                () -> ResearchTreeBuilder.buildPublication(
                        catalog,
                        snapshot(rules, Map.of(id("test:maximum_mixed"), authoredGroup)),
                        config(),
                        new PlayerRecipeData(),
                        ignored -> false));

        long hiddenCount = countTier(ResearchTreeGraph.MAX_NODES, JournalVisibility.HIDDEN, tiers.length);
        long anonymousCount = countTier(
                ResearchTreeGraph.MAX_NODES, JournalVisibility.SILHOUETTE, tiers.length)
                + countTier(ResearchTreeGraph.MAX_NODES, JournalVisibility.NAME, tiers.length);
        long identifiedCount = countTier(
                ResearchTreeGraph.MAX_NODES, JournalVisibility.PREVIEW, tiers.length)
                + countTier(ResearchTreeGraph.MAX_NODES, JournalVisibility.FULL, tiers.length);

        assertEquals(ResearchTreeGraph.MAX_NODES - hiddenCount, publication.graph().nodes().size());
        assertEquals(2, publication.presentation().groups().size());
        ResearchTreePresentation.Group authored = publication.presentation().groups().get(0);
        ResearchTreePresentation.Group undisclosed = publication.presentation().groups().get(1);
        assertEquals(ResearchTreePresentation.Kind.AUTHORED, authored.kind());
        assertEquals(identifiedCount, authored.members().size());
        assertEquals(Optional.of(id("test:stress_4")), authored.iconNodeId());
        assertEquals(ResearchTreePresentation.Kind.UNDISCLOSED, undisclosed.kind());
        assertEquals(anonymousCount, undisclosed.members().size());
        assertTrue(undisclosed.iconNodeId().isEmpty());

        for (int index = 0; index < ResearchTreeGraph.MAX_NODES; index++) {
            ResourceLocation realId = id("test:stress_" + index);
            JournalVisibility visibility = tiers[index % tiers.length];
            if (visibility == JournalVisibility.HIDDEN) {
                assertTrue(publication.graph().node(realId).isEmpty());
                assertTrue(publication.presentation().membership(realId).isEmpty());
            } else if (!visibility.revealsIdentity()) {
                assertTrue(publication.graph().node(realId).isEmpty());
            } else {
                assertEquals(
                        authored.id(),
                        publication.presentation().membership(realId).orElseThrow().groupId());
            }
        }
        assertTrue(publication.graph().nodes().stream()
                .filter(node -> !node.visibility().revealsIdentity())
                .allMatch(node -> node.blueprintId().getNamespace().equals("taczweaponblueprints")
                        && node.blueprintId().getPath().startsWith("undisclosed/")
                        && node.itemType().equals(ResearchTreeGraph.REDACTED_ITEM_TYPE)
                        && node.displaySlotId().equals(ResearchTreeGraph.REDACTED_DISPLAY_SLOT)
                        && node.availability() == ResearchTreeGraph.Availability.REDACTED
                        && node.pointCost() == 0
                        && node.ingredientTypeCount() == 0));
    }

    @Test
    void fallbackGroupsTrackAddonAdditionRemovalWithoutStaleMetadata() {
        Map<ResourceLocation, BlueprintData> initialCatalog = new LinkedHashMap<>();
        initialCatalog.put(id("test:base"), data("test:base", "rifle"));
        initialCatalog.put(id("addon:weapon"), data("addon:weapon", "launcher"));
        BlueprintResearchSnapshot snapshot = snapshot(Map.of(), Map.of());

        ResearchTreePublication initial = ResearchTreeBuilder.buildPublication(
                initialCatalog, snapshot, config(), new PlayerRecipeData(), ignored -> false);
        assertEquals(2, initial.presentation().groups().size());
        assertTrue(initial.presentation().groups().stream()
                .anyMatch(group -> group.title().equals("Other: Launcher")));

        ResearchTreePublication removed = ResearchTreeBuilder.buildPublication(
                Map.of(id("test:base"), data("test:base", "rifle")),
                snapshot,
                config(),
                new PlayerRecipeData(),
                ignored -> false);
        assertEquals(1, removed.presentation().groups().size());
        assertTrue(removed.graph().node(id("addon:weapon")).isEmpty());
        assertTrue(removed.presentation().groups().stream()
                .noneMatch(group -> group.title().equals("Other: Launcher")));

        Map<ResourceLocation, BlueprintData> restoredCatalog = new LinkedHashMap<>();
        restoredCatalog.put(id("addon:weapon"), data("addon:weapon", "launcher"));
        restoredCatalog.put(id("test:base"), data("test:base", "rifle"));
        ResearchTreePublication restored = ResearchTreeBuilder.buildPublication(
                restoredCatalog, snapshot, config(), new PlayerRecipeData(), ignored -> false);
        assertEquals(initial, restored);
    }

    private static BlueprintResearchSnapshot snapshot(
            Map<ResourceLocation, BlueprintResearchRule> rules,
            Map<ResourceLocation, ResearchTreeGroupDefinition> groups) {
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
        return BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile),
                rules,
                groups);
    }

    private static long countTier(int population, JournalVisibility tier, int tierCount) {
        int first = tier.ordinal();
        return population <= first ? 0L : 1L + (population - 1L - first) / tierCount;
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
                prerequisites.isEmpty() ? Optional.empty() : Optional.of(prerequisites),
                Optional.empty());
    }

    private static ResearchTreeGroupDefinition group(
            String title,
            ResourceLocation icon,
            int order,
            List<List<ResourceLocation>> ranks) {
        return group(title, icon, order, Optional.empty(), ranks);
    }

    private static ResearchTreeGroupDefinition group(
            String title,
            ResourceLocation icon,
            int order,
            Optional<Boolean> includeInOverview,
            List<List<ResourceLocation>> ranks) {
        return new ResearchTreeGroupDefinition(
                1,
                PROFILE,
                title,
                Optional.of("gui.test." + title.toLowerCase(java.util.Locale.ROOT).replace(' ', '_')),
                icon,
                order,
                includeInOverview,
                ranks);
    }

    private static BlueprintProgressionConfigSnapshot config() {
        return new BlueprintProgressionConfigSnapshot(
                true,
                true,
                true,
                JournalVisibility.FULL,
                true,
                DuplicateBlueprintPolicy.MANUAL_RECYCLING,
                false,
                100,
                false,
                PROFILE);
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            ResourceLocation nodeId,
            JournalVisibility visibility) {
        return node(ordinal, nodeId, visibility, 0);
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            ResourceLocation nodeId,
            JournalVisibility visibility,
            int prerequisites) {
        boolean identity = visibility.revealsIdentity();
        return new ResearchTreeGraph.Node(
                ordinal,
                nodeId,
                visibility.revealsName() ? "name.test" : ResearchTreeGraph.REDACTED_NAME_KEY,
                identity ? "rifle" : ResearchTreeGraph.REDACTED_ITEM_TYPE,
                identity ? id("test:slot") : ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                visibility,
                false,
                false,
                false,
                visibility.revealsResearchSummary() ? 8 : 0,
                0,
                prerequisites,
                0,
                identity ? ResearchTreeGraph.Availability.PREVIEW : ResearchTreeGraph.Availability.REDACTED);
    }

    private static BlueprintData data(String value, String itemType) {
        return data(value, itemType, null);
    }

    private static BlueprintData data(
            String value,
            String itemType,
            BlueprintKind kind) {
        ResourceLocation blueprintId = id(value);
        return kind == null
                ? new BlueprintData(
                        value,
                        "name." + blueprintId.getPath(),
                        "tooltip." + blueprintId.getPath(),
                        id("test:recipe/" + blueprintId.getPath()),
                        null,
                        itemType,
                        id("test:slot/" + blueprintId.getPath()))
                : new BlueprintData(
                        value,
                        "name." + blueprintId.getPath(),
                        "tooltip." + blueprintId.getPath(),
                        id("test:recipe/" + blueprintId.getPath()),
                        null,
                        itemType,
                        id("test:slot/" + blueprintId.getPath()),
                        kind);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
