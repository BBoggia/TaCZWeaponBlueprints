package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
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

        assertEquals(12, root.get("format").getAsInt());
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
