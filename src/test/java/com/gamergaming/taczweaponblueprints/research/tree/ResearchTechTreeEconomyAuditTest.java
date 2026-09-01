package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardEconomyProjection;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCatalogExporter;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

class ResearchTechTreeEconomyAuditTest {
    private static final ResourceLocation ROOT = id("test:root");
    private static final ResourceLocation LEFT = id("test:left");
    private static final ResourceLocation RIGHT = id("test:right");
    private static final ResourceLocation MERGE = id("test:merge");

    @Test
    void reportsFullTreePathClosureIncomeAndAndMergeCosts() {
        ResearchTreeGraph graph = graph();
        ResearchPointAwardEconomyProjection.Projection income =
                new ResearchPointAwardEconomyProjection.Projection(
                        1,
                        0,
                        7,
                        Map.of(ResearchPointAwardTrigger.Type.INTEGRATION, 7));
        ResearchTechTreeEconomyAudit.Audit audit = ResearchTechTreeEconomyAudit.audit(
                graph, presentation(0, 1, 2), income);
        ResearchTechTreeEconomyAudit.DomainEconomy weapons =
                audit.domain(Domain.WEAPONS).orElseThrow();

        assertEquals("research_policy", audit.costAuthority());
        assertFalse(audit.automaticCostCurveEnabled());
        assertEquals(14, weapons.fullTreeCost());
        assertEquals(1, weapons.foundationCount());
        assertEquals(2, weapons.foundationCost());
        assertEquals(1, weapons.leafCount());
        assertEquals(11, weapons.maximumLeafSinglePathCost());
        assertEquals(14, weapons.maximumLeafUnlockClosureCost());
        assertEquals(1, weapons.andMergeCount());
        assertEquals(1, weapons.additionalMergePrerequisiteCount());
        assertEquals(5_000, weapons.finiteIncomeCoverageBasisPoints());
    }

    @Test
    void rankAndBandChangesCannotAlterEconomyEvidence() {
        ResearchTreeGraph graph = graph();
        var income = ResearchPointAwardEconomyProjection.Projection.EMPTY;

        assertEquals(
                ResearchTechTreeEconomyAudit.audit(graph, presentation(0, 1, 2), income)
                        .domain(Domain.WEAPONS).orElseThrow(),
                ResearchTechTreeEconomyAudit.audit(graph, presentation(3, 8, 14), income)
                        .domain(Domain.WEAPONS).orElseThrow());
    }

    @Test
    void itemsOnlyModeMarksPointCoverageAsNotApplicable() {
        ResearchTechTreeEconomyAudit.Audit audit = ResearchTechTreeEconomyAudit.audit(
                graph(),
                presentation(0, 1, 2),
                ResearchPointAwardEconomyProjection.Projection.EMPTY,
                ResearchCostMode.ITEMS_ONLY);

        assertEquals(ResearchCostMode.ITEMS_ONLY, audit.researchCostMode());
        assertFalse(audit.pointCoverageApplicable());
    }

    @Test
    void oneAnyOfGroupIsAChoiceRatherThanAnAndMerge() {
        ResearchTreeGraph graph = ResearchTreeGraph.withRequirementGroups(
                List.of(
                        node(0, ROOT, 2, 0),
                        node(1, LEFT, 3, 1),
                        node(2, RIGHT, 4, 1),
                        node(3, MERGE, 5, 2)),
                List.of(
                        requirement(LEFT, 0, ROOT),
                        requirement(RIGHT, 0, ROOT),
                        requirement(MERGE, 0, LEFT, RIGHT)));

        ResearchTechTreeEconomyAudit.DomainEconomy weapons =
                ResearchTechTreeEconomyAudit.audit(
                        graph,
                        presentation(0, 1, 2),
                        ResearchPointAwardEconomyProjection.Projection.EMPTY)
                        .domain(Domain.WEAPONS).orElseThrow();

        assertEquals(10, weapons.minimumLeafUnlockClosureCost());
        assertEquals(10, weapons.maximumLeafUnlockClosureCost());
        assertEquals(0, weapons.andMergeCount());
        assertEquals(0, weapons.additionalMergePrerequisiteCount());
    }

    @Test
    void largeCatalogCostsRemainExactBeyondTheIntegerRange() {
        ResourceLocation first = id("test:expensive_first");
        ResourceLocation second = id("test:expensive_second");
        ResourceLocation third = id("test:expensive_third");
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, first, 1_000_000_000, 0),
                        node(1, second, 1_000_000_000, 0),
                        node(2, third, 1_000_000_000, 0)),
                List.of());

        ResearchTechTreeEconomyAudit.DomainEconomy weapons =
                ResearchTechTreeEconomyAudit.audit(
                        graph,
                        presentationByDomain(List.of(
                                domain(Domain.WEAPONS, first, second, third))),
                        ResearchPointAwardEconomyProjection.Projection.EMPTY)
                        .domain(Domain.WEAPONS).orElseThrow();

        assertEquals(3_000_000_000L, weapons.fullTreeCost());
        assertEquals(3_000_000_000L, weapons.foundationCost());
        assertEquals(1_000_000_000L, weapons.maximumLeafSinglePathCost());
        assertEquals(1_000_000_000L, weapons.maximumLeafUnlockClosureCost());
    }

    @Test
    void crossDomainAlternativeDoesNotBecomeAMandatoryLocalCost() {
        ResourceLocation local = id("test:local_weapon");
        ResourceLocation remote = id("test:remote_attachment");
        ResourceLocation target = id("test:target_weapon");
        ResearchTreeGraph graph = ResearchTreeGraph.withRequirementGroups(
                List.of(
                        node(0, local, 3, 0),
                        node(1, remote, 7, 0),
                        node(2, target, 5, 2)),
                List.of(new ResearchTreeGraph.RequirementGroup(
                        target, 0, List.of(local, remote), 0, false)));

        ResearchTechTreeEconomyAudit.DomainEconomy weapons =
                ResearchTechTreeEconomyAudit.audit(
                        graph,
                        presentationByDomain(List.of(
                                domain(Domain.WEAPONS, local, target),
                                domain(Domain.ATTACHMENTS, remote))),
                        ResearchPointAwardEconomyProjection.Projection.EMPTY)
                        .domain(Domain.WEAPONS).orElseThrow();

        assertEquals(2, weapons.foundationCount());
        assertEquals(8L, weapons.foundationCost());
        assertEquals(5L, weapons.minimumLeafUnlockClosureCost());
        assertEquals(5L, weapons.maximumLeafUnlockClosureCost());
    }

    @Test
    void formatEightExportIncludesTopologyRetentionAndEconomyAuthority() {
        ResearchTreeGraph graph = graph();
        ResearchTechTreePresentation presentation = presentation(0, 1, 2);
        ResearchTechTreeTopologyAudit.ParentFixture fixture =
                ResearchTechTreeTopologyAudit.ParentFixture.capture(graph, presentation);
        ResearchTechTreeTopologyAudit.Audit topology = ResearchTechTreeTopologyAudit.audit(
                graph, presentation, null, fixture);
        ResearchTechTreeEconomyAudit.Audit economy = ResearchTechTreeEconomyAudit.audit(
                graph,
                presentation,
                new ResearchPointAwardEconomyProjection.Projection(
                        1,
                        0,
                        7,
                        Map.of(ResearchPointAwardTrigger.Type.INTEGRATION, 7)));
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                ROOT, data(ROOT),
                LEFT, data(LEFT),
                RIGHT, data(RIGHT),
                MERGE, data(MERGE));
        ResourceLocation profile = id("test:profile");
        ResearchTechTreeAuthoringReport authoring = ResearchTechTreeAuthoringReport.create(
                BlueprintResearchSnapshot.EMPTY,
                catalog,
                profile,
                null,
                AutomaticWeaponEvidenceSnapshot.EMPTY);
        var root = JsonParser.parseString(BlueprintResearchCatalogExporter.exportWithDiagnostics(
                BlueprintResearchSnapshot.EMPTY,
                catalog,
                profile,
                null,
                authoring,
                topology,
                economy)).getAsJsonObject();

        assertEquals(BlueprintResearchCatalogExporter.CURRENT_FORMAT,
                root.get("format").getAsInt());
        assertEquals(1, root.getAsJsonObject("topology_audit")
                .getAsJsonArray("domains").get(0).getAsJsonObject()
                .get("cross_branch_merge_count").getAsInt());
        assertEquals(10_000, root.getAsJsonObject("topology_audit")
                .getAsJsonObject("parent_retention")
                .get("retention_basis_points").getAsInt());
        assertEquals("research_policy", root.getAsJsonObject("economy_review")
                .get("cost_authority").getAsString());
        assertFalse(root.getAsJsonObject("economy_review")
                .get("automatic_cost_curve_enabled").getAsBoolean());
        assertEquals(14, root.getAsJsonObject("economy_review")
                .getAsJsonArray("domains").get(0).getAsJsonObject()
                .get("full_tree_cost").getAsInt());
    }

    private static ResearchTreeGraph graph() {
        return new ResearchTreeGraph(
                List.of(
                        node(0, ROOT, 2, 0),
                        node(1, LEFT, 3, 1),
                        node(2, RIGHT, 4, 1),
                        node(3, MERGE, 5, 2)),
                List.of(
                        new ResearchTreeGraph.Edge(ROOT, LEFT),
                        new ResearchTreeGraph.Edge(ROOT, RIGHT),
                        new ResearchTreeGraph.Edge(LEFT, MERGE),
                        new ResearchTreeGraph.Edge(RIGHT, MERGE)));
    }

    private static ResearchTreeGraph.RequirementGroup requirement(
            ResourceLocation dependent,
            int ordinal,
            ResourceLocation... alternatives) {
        return new ResearchTreeGraph.RequirementGroup(
                dependent,
                ordinal,
                List.of(alternatives),
                0,
                false);
    }

    private static ResearchTechTreePresentation presentation(
            int rootRank,
            int branchRank,
            int mergeRank) {
        return new ResearchTechTreePresentation(
                Optional.of(id("test:tree")),
                "Tree",
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(new ResearchTechTreePresentation.DomainView(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.empty(),
                        Optional.empty(),
                        List.of(new ResearchTechTreePresentation.LaneView(
                                id("test:weapons"),
                                "Weapons",
                                Optional.empty(),
                                Optional.empty(),
                                0,
                                List.of(
                                        member(ROOT, rootRank, 0),
                                        member(LEFT, branchRank, 0),
                                        member(RIGHT, branchRank, 1),
                                        member(MERGE, mergeRank, 0)))))));
    }

    private static ResearchTechTreePresentation presentationByDomain(
            List<ResearchTechTreePresentation.DomainView> domains) {
        return new ResearchTechTreePresentation(
                Optional.of(id("test:tree")),
                "Tree",
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                domains);
    }

    private static ResearchTechTreePresentation.DomainView domain(
            Domain domain,
            ResourceLocation... members) {
        List<ResearchTechTreePresentation.Member> placements =
                java.util.stream.IntStream.range(0, members.length)
                        .mapToObj(index -> member(members[index], index, 0))
                        .toList();
        String name = domain.name().toLowerCase(java.util.Locale.ROOT);
        return new ResearchTechTreePresentation.DomainView(
                domain,
                domain.name(),
                Optional.empty(),
                Optional.empty(),
                List.of(new ResearchTechTreePresentation.LaneView(
                        id("test:" + name),
                        domain.name(),
                        Optional.empty(),
                        Optional.empty(),
                        0,
                        placements)));
    }

    private static ResearchTechTreePresentation.Member member(
            ResourceLocation id,
            int rank,
            long order) {
        return new ResearchTechTreePresentation.Member(
                id, rank, order, Optional.empty(), PlacementOrigin.EXACT, Optional.empty());
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            ResourceLocation id,
            int cost,
            int prerequisiteCount) {
        return new ResearchTreeGraph.Node(
                ordinal,
                id,
                "item." + id.getPath(),
                "pistol",
                id,
                JournalVisibility.FULL,
                false,
                true,
                true,
                cost,
                0,
                prerequisiteCount,
                0,
                ResearchTreeGraph.Availability.AVAILABLE);
    }

    private static BlueprintData data(ResourceLocation id) {
        return new BlueprintData(
                id.toString(),
                "name." + id.getPath(),
                "tooltip." + id.getPath(),
                new ResourceLocation(id.getNamespace(), "recipe/" + id.getPath()),
                null,
                "pistol",
                new ResourceLocation(id.getNamespace(), "slot/" + id.getPath()),
                BlueprintKind.GUN);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
