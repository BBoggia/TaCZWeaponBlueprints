package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementDiagnostics;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementProposal;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisiteDecision;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceSnapshot;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardEconomyProjection;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCatalogExporter;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

class ResearchGroupedRouteQualityAuditTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation TREE = id("test:tree");
    private static final ResourceLocation ROOT = id("test:root");
    private static final ResourceLocation LEFT = id("test:left");
    private static final ResourceLocation RIGHT = id("test:right");
    private static final ResourceLocation CHOICE = id("test:choice");

    @Test
    void measuresLiveAlternativeMeaningCostAncestryBranchEntryAndAffordability() {
        Fixture fixture = fixture();
        ResearchGroupedRouteQualityAudit.Audit audit =
                ResearchGroupedRouteQualityAudit.audit(
                        fixture.graph(),
                        fixture.presentation(),
                        fixture.diagnostics(),
                        income(10));

        assertTrue(audit.available());
        assertEquals(1, audit.automaticTargetCount());
        assertEquals(1, audit.matchedAutomaticTargetCount());
        assertEquals(1, audit.alternativeGroupCount());
        assertEquals(1, audit.effectiveAlternativeGroupCount());
        assertEquals(0, audit.alternatives().dependentAlternativePairCount());
        assertEquals(16_000L,
                audit.alternatives().routeCostRatioLowerBoundBasisPoints().median());
        assertEquals(16_000L,
                audit.alternatives().routeCostRatioUpperBoundBasisPoints().median());
        assertEquals(3_333,
                audit.alternatives().mandatoryAncestryOverlapBasisPoints().median());
        assertEquals(3_333,
                audit.mandatoryAncestorSharesBasisPoints().median());
        assertEquals(0, audit.singleRouteChainLengths().sampleCount());
        assertEquals(1, audit.branchEntries().size());
        assertEquals(2, audit.branchEntries().get(0).distinctEntranceCount());
        assertEquals(1, audit.branchEntries().get(0).redundantEntranceCount());
        assertEquals(1, audit.affordableTerminalCount());
        assertEquals(0, audit.unaffordableTerminalCount());
        assertEquals(9, audit.terminalRoutes().get(0).minimumRouteUpperBound());
        assertTrue(audit.terminalRoutes().get(0).exact());
        assertTrue(audit.warnings().isEmpty());

        ResearchGroupedRouteQualityAudit.PhaseSummary transition = audit.phases().stream()
                .filter(value -> value.phase()
                        == ResearchGroupedRouteQualityAudit.Phase.TRANSITION)
                .findFirst().orElseThrow();
        assertEquals(1, transition.targetCount());
        assertEquals(1, transition.alternativeGroupCount());
        assertEquals(1, transition.sameFamilyAlternativeGroupCount());
        assertEquals(10_000, transition.alternativeDensityBasisPoints());
        assertTrue(audit.phases().stream()
                .filter(value -> value.targetCount() == 0)
                .allMatch(value -> value.alternativeDensityBasisPoints() == 0));
    }

    @Test
    void formatSixteenExportsDeterministicQualityAndMotifEvidence() {
        Fixture fixture = fixture();
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                ROOT, data(ROOT),
                LEFT, data(LEFT),
                RIGHT, data(RIGHT),
                CHOICE, data(CHOICE));
        ResearchTechTreeAuthoringReport authoring = ResearchTechTreeAuthoringReport.create(
                BlueprintResearchSnapshot.EMPTY,
                catalog,
                PROFILE,
                fixture.diagnostics(),
                AutomaticWeaponEvidenceSnapshot.EMPTY);
        ResearchGroupedRouteQualityAudit.Audit audit =
                ResearchGroupedRouteQualityAudit.audit(
                        fixture.graph(),
                        fixture.presentation(),
                        fixture.diagnostics(),
                        income(10));
        ResearchTechTreeTopologyAudit.Audit topology =
                ResearchTechTreeTopologyAudit.audit(
                        fixture.graph(), fixture.presentation(), fixture.diagnostics());
        ResearchGroupedRouteMotifAssessment.Assessment motifAssessment =
                ResearchGroupedRouteMotifAssessment.assess(audit, topology);
        var root = JsonParser.parseString(BlueprintResearchCatalogExporter.exportWithDiagnostics(
                BlueprintResearchSnapshot.EMPTY,
                catalog,
                PROFILE,
                fixture.diagnostics(),
                authoring,
                topology,
                ResearchTechTreeEconomyAudit.Audit.EMPTY,
                audit,
                motifAssessment)).getAsJsonObject();

        assertEquals(BlueprintResearchCatalogExporter.CURRENT_FORMAT,
                root.get("format").getAsInt());
        var quality = root.getAsJsonObject("grouped_route_quality");
        assertTrue(quality.get("available").getAsBoolean());
        assertEquals(1, quality.get("effective_alternative_group_count").getAsInt());
        assertEquals(16_000L, quality.getAsJsonObject("alternatives")
                .getAsJsonObject("route_cost_ratio_upper_bound_basis_points")
                .get("median").getAsLong());
        assertEquals("affordable", quality.getAsJsonArray("terminal_routes")
                .get(0).getAsJsonObject().get("affordability").getAsString());
        assertTrue(quality.getAsJsonArray("warnings").isEmpty());
        var motif = root.getAsJsonObject("grouped_route_motif_assessment");
        assertTrue(motif.get("available").getAsBoolean());
        assertEquals("evidence-gate-v1", motif.get("contract").getAsString());
        assertEquals("retain_current_grouped_routes",
                motif.get("decision").getAsString());
        assertEquals(0, motif.get("decisive_signal_count").getAsInt());
        assertFalse(motif.get("motif_prototype_recommended").getAsBoolean());
        assertEquals(9, motif.getAsJsonArray("signals").size());
        assertTrue(motif.getAsJsonArray("recommended_motifs").isEmpty());
        assertFalse(motif.getAsJsonObject("visual_evidence")
                .get("post_junction_measurement_available").getAsBoolean());
    }

    @Test
    void hybridRoutesReuseTheLiveAndOfOrQualityAudit() {
        Fixture fixture = fixture(
                AutomaticWeaponPlacementPolicy.PrerequisiteStrategy.HYBRID_ROUTES_V1);

        ResearchGroupedRouteQualityAudit.Audit audit =
                ResearchGroupedRouteQualityAudit.audit(
                        fixture.graph(),
                        fixture.presentation(),
                        fixture.diagnostics(),
                        income(10));

        assertTrue(audit.available());
        assertEquals(ResearchGroupedRouteQualityAudit.HYBRID_INTERPRETATION,
                audit.interpretation());
        assertEquals(1, audit.matchedAutomaticTargetCount());
        assertEquals(1, audit.effectiveAlternativeGroupCount());
    }

    private static Fixture fixture() {
        return fixture(
                AutomaticWeaponPlacementPolicy.PrerequisiteStrategy.GROUPED_ROUTES_V1);
    }

    private static Fixture fixture(
            AutomaticWeaponPlacementPolicy.PrerequisiteStrategy strategy) {
        ResearchRequirements choiceRequirements = new ResearchRequirements(List.of(
                new ResearchPrerequisiteGroup(List.of(LEFT, RIGHT))));
        ResearchTreeGraph graph = ResearchTreeGraph.withRequirementGroups(
                List.of(
                        node(0, ROOT, 2, 0),
                        node(1, LEFT, 3, 1),
                        node(2, RIGHT, 6, 1),
                        node(3, CHOICE, 4, 2)),
                List.of(
                        requirement(LEFT, 0, ROOT),
                        requirement(RIGHT, 0, ROOT),
                        requirement(CHOICE, 0, LEFT, RIGHT)));
        ResearchTechTreePresentation presentation = new ResearchTechTreePresentation(
                Optional.of(TREE),
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
                                        member(ROOT, 0, 0),
                                        member(LEFT, 1, 0),
                                        member(RIGHT, 1, 1),
                                        member(CHOICE, 2, 0)))))));
        AutomaticWeaponPrerequisiteDecision decision =
                new AutomaticWeaponPrerequisiteDecision(
                        CHOICE,
                        AutomaticWeaponPrerequisiteDecision.Strategy.TRANSITION_LOCAL,
                        Optional.of(0),
                        2,
                        2,
                        2,
                        2,
                        10_000,
                        true,
                        Map.of(
                                LEFT,
                                AutomaticWeaponPrerequisiteDecision.ParentRelation.SAME_FAMILY,
                                RIGHT,
                                AutomaticWeaponPrerequisiteDecision.ParentRelation.SAME_FAMILY),
                        Optional.empty(),
                        false,
                        true,
                        Optional.of(2),
                        Optional.empty(),
                        AutomaticWeaponPrerequisiteDecision.GeneratedRequirementShape
                                .ALTERNATIVE_ROUTES);
        AutomaticWeaponPlacementDiagnostics diagnostics =
                new AutomaticWeaponPlacementDiagnostics(
                        PROFILE,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                        AutomaticWeaponPlacementPolicy.LayeringStrategy.DYNAMIC_STAT_LAYERS,
                        strategy,
                        strategy == AutomaticWeaponPlacementPolicy.PrerequisiteStrategy
                                .HYBRID_ROUTES_V1 ? 3 : 2,
                        4,
                        1L,
                        1L,
                        4,
                        4,
                        AutomaticWeaponPlacementPolicy.DEFAULT_MAX_NODES_PER_RANK,
                        new AutomaticWeaponPlacementDiagnostics.PublicationSummary(
                                true, 1, 0, 1, 1),
                        Map.of(
                                ROOT, authored(ROOT),
                                LEFT, authored(LEFT),
                                RIGHT, authored(RIGHT),
                                CHOICE, new AutomaticWeaponPlacementDiagnostics.Entry(
                                        CHOICE,
                                        AutomaticWeaponPlacementDiagnostics.State.AUTOMATIC,
                                        Optional.of(proposal(CHOICE)),
                                        List.of(LEFT, RIGHT),
                                        choiceRequirements,
                                        Optional.empty(),
                                        Optional.of(decision))));
        return new Fixture(graph, presentation, diagnostics);
    }

    private static AutomaticWeaponPlacementDiagnostics.Entry authored(
            ResourceLocation id) {
        return new AutomaticWeaponPlacementDiagnostics.Entry(
                id,
                AutomaticWeaponPlacementDiagnostics.State.AUTHORED,
                Optional.empty(),
                List.of(),
                Optional.empty());
    }

    private static AutomaticWeaponPlacementProposal proposal(ResourceLocation id) {
        return new AutomaticWeaponPlacementProposal(
                id.toString(),
                17,
                100,
                new ProgressionPosition(Tier.BASIC, 0, 0),
                3,
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                List.of());
    }

    private static ResearchPointAwardEconomyProjection.Projection income(int points) {
        return new ResearchPointAwardEconomyProjection.Projection(
                1,
                0,
                points,
                Map.of(ResearchPointAwardTrigger.Type.INTEGRATION, points));
    }

    private static ResearchTreeGraph.RequirementGroup requirement(
            ResourceLocation dependent,
            int ordinal,
            ResourceLocation... alternatives) {
        return new ResearchTreeGraph.RequirementGroup(
                dependent, ordinal, List.of(alternatives), 0, false);
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
                "rifle",
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

    private static ResearchTechTreePresentation.Member member(
            ResourceLocation id,
            int rank,
            long order) {
        return new ResearchTechTreePresentation.Member(
                id,
                rank,
                order,
                Optional.empty(),
                PlacementOrigin.EXACT,
                Optional.empty());
    }

    private static BlueprintData data(ResourceLocation id) {
        return new BlueprintData(
                id.toString(),
                "name." + id.getPath(),
                "tooltip." + id.getPath(),
                new ResourceLocation(id.getNamespace(), "recipe/" + id.getPath()),
                null,
                "rifle",
                new ResourceLocation(id.getNamespace(), "slot/" + id.getPath()),
                BlueprintKind.GUN);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }

    private record Fixture(
            ResearchTreeGraph graph,
            ResearchTechTreePresentation presentation,
            AutomaticWeaponPlacementDiagnostics diagnostics) {
    }
}
