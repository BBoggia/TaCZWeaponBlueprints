package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeAuthoringReport;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementDiagnostics.State;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.PrerequisiteStrategy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCatalogExporter;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

class AutomaticWeaponPlacementDiagnosticsTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation TREE = id("test:tree");
    private static final ResourceLocation AUTOMATIC = id("addon:auto");
    private static final ResourceLocation STRAY = id("addon:stray");
    private static final ResourceLocation EXCLUDED = id("addon:excluded");
    private static final ResourceLocation AUTHORED = id("tacz:authored");
    private static final ResourceLocation UNPLACED = id("addon:unplaced");

    @Test
    void reportPartitionsAndExplainsEveryCatalogWeaponDeterministically() {
        AutomaticWeaponPlacementDiagnostics diagnostics = diagnostics();

        assertEquals(4, diagnostics.entries().size());
        assertEquals(List.of(AUTOMATIC, EXCLUDED, UNPLACED, AUTHORED),
                diagnostics.entries().keySet().stream().toList());
        assertEquals(1L, diagnostics.count(State.AUTHORED));
        assertEquals(1L, diagnostics.count(State.AUTOMATIC));
        assertEquals(1L, diagnostics.count(State.EXCLUDED_AUTOMATIC));
        assertEquals(1L, diagnostics.excludedAutomaticCount());
        assertEquals(1L, diagnostics.count(State.UNPLACED));
        assertEquals(1, diagnostics.generatedPrerequisiteCount());
        assertEquals(1, diagnostics.generatedRequirementGroupCount());
        assertEquals(0, diagnostics.generatedAlternativeGroupCount());
        assertEquals(AUTHORED, diagnostics.entry(AUTOMATIC).orElseThrow()
                .generatedPrerequisite().orElseThrow());
        assertEquals("review_required", diagnostics.entry(EXCLUDED).orElseThrow()
                .reason().orElseThrow());
        assertTrue(diagnostics.entry(AUTHORED).orElseThrow().proposal().isEmpty());
        assertTrue(diagnostics.publicationSummary().applicable());
        assertEquals(1, diagnostics.publicationSummary()
                .canonicalBranchCoordinateCount());
        assertEquals(1, diagnostics.publicationSummary().publishedRankCount());
        assertTrue(diagnostics.publicationSummary().complete());

        AutomaticWeaponPrerequisitePlan stale = new AutomaticWeaponPrerequisitePlan(
                PROFILE,
                TREE,
                AutomaticPlacementMode.CONNECTED,
                6L,
                7L,
                1,
                Map.of(AUTOMATIC, List.of(AUTHORED)),
                Map.of());
        assertThrows(IllegalArgumentException.class, () ->
                AutomaticWeaponPlacementDiagnostics.create(
                        PROFILE, candidates(), stale));
    }

    @Test
    void publicationRejectsUnexpectedParentlessNodesAboveTheFoundationRank() {
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                new AutomaticWeaponPlacementCandidateSnapshot(
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        AutomaticWeaponPlacementPolicy.DEFAULT,
                        5L,
                        7L,
                        2,
                        Map.of(
                                AUTOMATIC.toString(), proposal(AUTOMATIC),
                                STRAY.toString(), proposal(STRAY)),
                        Map.of(),
                        Set.of(),
                        Set.of());
        AutomaticWeaponPrerequisiteDecision foundationDecision =
                new AutomaticWeaponPrerequisiteDecision(
                        AUTOMATIC,
                        AutomaticWeaponPrerequisiteDecision.Strategy.FOUNDATION,
                        Optional.of(0),
                        0,
                        0,
                        0,
                        1,
                        Map.of(),
                        false,
                        false).withPublishedRank(0);
        AutomaticWeaponPrerequisiteDecision strayDecision =
                new AutomaticWeaponPrerequisiteDecision(
                        STRAY,
                        AutomaticWeaponPrerequisiteDecision.Strategy.SPECIALIZATION,
                        Optional.of(0),
                        1,
                        0,
                        0,
                        1,
                        Map.of(),
                        false,
                        false).withPublishedRank(1);
        AutomaticWeaponPrerequisitePlan plan =
                new AutomaticWeaponPrerequisitePlan(
                        PROFILE,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        5L,
                        7L,
                        2,
                        Map.of(),
                        Map.of(
                                AUTOMATIC, "generated_root",
                                STRAY, "generated_root"),
                        Map.of(
                                AUTOMATIC, foundationDecision,
                                STRAY, strayDecision),
                        Map.of(
                                AUTOMATIC,
                                new AutomaticWeaponPrerequisitePlan.BranchCoordinate(
                                        0, 0, 0, 0),
                                STRAY,
                                new AutomaticWeaponPrerequisitePlan.BranchCoordinate(
                                        0, 1, 0, 0)));

        AutomaticWeaponPlacementDiagnostics diagnostics =
                AutomaticWeaponPlacementDiagnostics.create(
                        PROFILE, candidates, plan);

        assertEquals(1, diagnostics.publicationSummary()
                .unexpectedParentlessCandidateCount());
        assertFalse(diagnostics.publicationSummary().connectedTopologyComplete());
        assertFalse(diagnostics.publicationSummary().complete());
        var root = JsonParser.parseString(BlueprintResearchCatalogExporter.export(
                BlueprintResearchSnapshot.EMPTY,
                Map.of(AUTOMATIC, data(AUTOMATIC), STRAY, data(STRAY)),
                PROFILE,
                diagnostics)).getAsJsonObject();
        var publication = root.getAsJsonObject("automatic_placement")
                .getAsJsonObject("publication");
        assertEquals(1, publication.get(
                "unexpected_parentless_candidate_count").getAsInt());
        assertFalse(publication.get("connected_topology_complete").getAsBoolean());
    }

    @Test
    void formatFourteenExportIncludesExactBranchPrerequisiteProvenance() {
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                AUTOMATIC, data(AUTOMATIC),
                EXCLUDED, data(EXCLUDED),
                AUTHORED, data(AUTHORED),
                UNPLACED, data(UNPLACED));
        String exported = BlueprintResearchCatalogExporter.export(
                BlueprintResearchSnapshot.EMPTY,
                catalog,
                PROFILE,
                diagnostics());
        Map<ResourceLocation, BlueprintData> reversed = new LinkedHashMap<>();
        reversed.put(UNPLACED, catalog.get(UNPLACED));
        reversed.put(AUTHORED, catalog.get(AUTHORED));
        reversed.put(EXCLUDED, catalog.get(EXCLUDED));
        reversed.put(AUTOMATIC, catalog.get(AUTOMATIC));
        assertEquals(exported, BlueprintResearchCatalogExporter.export(
                BlueprintResearchSnapshot.EMPTY,
                reversed,
                PROFILE,
                diagnostics()));
        var root = JsonParser.parseString(exported).getAsJsonObject();

        assertEquals(BlueprintResearchCatalogExporter.CURRENT_FORMAT,
                root.get("format").getAsInt());
        assertEquals("connected", root.getAsJsonObject("automatic_placement")
                .get("mode").getAsString());
        assertEquals("exclude", root.getAsJsonObject("automatic_placement")
                .get("review_handling").getAsString());
        assertEquals(2, root.getAsJsonObject("automatic_placement")
                .get("topology_weapon_count").getAsInt());
        assertEquals(9, root.getAsJsonObject("automatic_placement")
                .get("resolved_nodes_per_layer").getAsInt());
        assertEquals(1, root.getAsJsonObject("automatic_placement")
                .get("planned_prerequisite_count").getAsInt());
        assertEquals("legacy_and", root.getAsJsonObject("automatic_placement")
                .get("prerequisite_strategy").getAsString());
        assertEquals(1, root.getAsJsonObject("automatic_placement")
                .get("planned_requirement_group_count").getAsInt());
        assertEquals(0, root.getAsJsonObject("automatic_placement")
                .get("planned_alternative_group_count").getAsInt());
        assertEquals(1, root.getAsJsonObject("automatic_placement")
                .get("excluded_automatic_count").getAsInt());
        assertEquals(1, root.getAsJsonObject("automatic_placement")
                .get("excluded_fallback_count").getAsInt());
        var branchSummary = root.getAsJsonObject("automatic_placement")
                .getAsJsonObject("branch_prerequisites");
        assertTrue(branchSummary.get("available").getAsBoolean());
        assertEquals(1, branchSummary.get("branch_count").getAsInt());
        assertEquals(1, branchSummary.get("foundation_node_count").getAsInt());
        assertEquals(1, branchSummary.get("same_family_edge_count").getAsInt());
        var publication = root.getAsJsonObject("automatic_placement")
                .getAsJsonObject("publication");
        assertTrue(publication.get("applicable").getAsBoolean());
        assertEquals(1, publication.get("canonical_branch_coordinate_count").getAsInt());
        assertEquals(10_000,
                publication.get("canonical_branch_coverage_basis_points").getAsInt());
        assertEquals(1, publication.get("prerequisite_decision_count").getAsInt());
        assertEquals(1, publication.get("published_rank_count").getAsInt());
        assertTrue(publication.get("rank_reconciliation_complete").getAsBoolean());
        assertTrue(publication.get("complete").getAsBoolean());
        var automaticEntry = root.getAsJsonArray("entries").asList().stream()
                .map(value -> value.getAsJsonObject())
                .filter(value -> AUTOMATIC.toString().equals(
                        value.get("blueprint").getAsString()))
                .findFirst().orElseThrow()
                .getAsJsonObject("automatic_placement");
        assertEquals("automatic", automaticEntry.get("state").getAsString());
        assertEquals(17, automaticEntry.get("mechanical_score").getAsInt());
        assertEquals(
                diagnostics().entry(AUTOMATIC).orElseThrow().proposal().orElseThrow()
                        .progressionCoordinate().rank(),
                automaticEntry.get("rank").getAsInt());
        assertTrue(automaticEntry.getAsJsonArray("review_reasons").isEmpty());
        assertEquals(AUTHORED.toString(),
                automaticEntry.get("planned_prerequisite").getAsString());
        assertEquals(List.of(AUTHORED.toString()),
                automaticEntry.getAsJsonArray("planned_prerequisites").asList().stream()
                        .map(value -> value.getAsString())
                        .toList());
        assertEquals(List.of(AUTHORED.toString()),
                automaticEntry.getAsJsonArray("planned_prerequisite_groups")
                        .get(0).getAsJsonObject().getAsJsonArray("any_of")
                        .asList().stream().map(value -> value.getAsString()).toList());
        var prerequisiteDecision = automaticEntry.getAsJsonObject(
                "prerequisite_decision");
        assertEquals("foundation", prerequisiteDecision.get("strategy").getAsString());
        assertEquals(0, prerequisiteDecision
                .get("second_parent_quota_basis_points").getAsInt());
        assertEquals(4, prerequisiteDecision.get("published_rank").getAsInt());
        assertFalse(prerequisiteDecision.get("second_parent_eligible").getAsBoolean());
        assertEquals("authored_same_family", prerequisiteDecision
                .getAsJsonArray("parent_relationships").get(0).getAsJsonObject()
                .get("relationship").getAsString());
        var authoring = root.getAsJsonArray("entries").asList().stream()
                .map(value -> value.getAsJsonObject())
                .filter(value -> AUTOMATIC.toString().equals(
                        value.get("blueprint").getAsString()))
                .findFirst().orElseThrow()
                .getAsJsonObject("authoring");
        assertEquals(17, authoring.get("mechanical_score").getAsInt());
        assertEquals(automaticEntry.get("rank").getAsInt(),
                authoring.get("assigned_rank").getAsInt());
        assertEquals("foundation_selection",
                authoring.get("parent_choice_reason").getAsString());
        assertEquals("none", authoring.get("merge_reason").getAsString());
        assertEquals("authored_same_family", authoring
                .getAsJsonArray("parent_choices").get(0).getAsJsonObject()
                .get("relationship").getAsString());
        assertEquals("foundation", authoring.getAsJsonObject("prerequisite_decision")
                .get("strategy").getAsString());
        assertEquals(25, authoring.get("fan_out_penalty").getAsInt());
        var excludedEntry = root.getAsJsonArray("entries").asList().stream()
                .map(value -> value.getAsJsonObject())
                .filter(value -> EXCLUDED.toString().equals(
                        value.get("blueprint").getAsString()))
                .findFirst().orElseThrow()
                .getAsJsonObject("automatic_placement");
        assertEquals("excluded_automatic", excludedEntry.get("state").getAsString());

        assertThrows(IllegalArgumentException.class, () ->
                BlueprintResearchCatalogExporter.export(
                        BlueprintResearchSnapshot.EMPTY,
                        catalog,
                        id("test:other_profile"),
                        diagnostics()));
        assertThrows(IllegalArgumentException.class, () ->
                BlueprintResearchCatalogExporter.export(
                        BlueprintResearchSnapshot.EMPTY,
                        Map.of(AUTOMATIC, data(AUTOMATIC)),
                        PROFILE,
                        diagnostics()));
    }

    @Test
    void groupedRouteDiagnosticsAndExportExposeOneAlternativeGroup() {
        AutomaticWeaponPlacementPolicy groupedPolicy =
                new AutomaticWeaponPlacementPolicy(
                        AutomaticWeaponPlacementPolicy.DEFAULT.levelsPerTier(),
                        AutomaticWeaponPlacementPolicy.DEFAULT
                                .reviewConfidenceThreshold(),
                        AutomaticWeaponPlacementPolicy.DEFAULT.reviewHandling(),
                        2,
                        AutomaticWeaponPlacementPolicy.DEFAULT.mergeInterval(),
                        AutomaticWeaponPlacementPolicy.DEFAULT.layeringStrategy(),
                        AutomaticWeaponPlacementPolicy.DEFAULT.maxNodesPerRank(),
                        AutomaticWeaponPlacementPolicy.DEFAULT.progressionBands(),
                        AutomaticWeaponPlacementPolicy.DEFAULT.foundationCount(),
                        PrerequisiteStrategy.GROUPED_ROUTES_V1);
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                new AutomaticWeaponPlacementCandidateSnapshot(
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        groupedPolicy,
                        5L,
                        7L,
                        3,
                        Map.of(AUTOMATIC.toString(), proposal(AUTOMATIC)),
                        Map.of(),
                        Set.of(AUTHORED.toString(), EXCLUDED.toString()),
                        Set.of());
        ResearchRequirements groupedRequirements = new ResearchRequirements(List.of(
                new ResearchPrerequisiteGroup(List.of(AUTHORED, EXCLUDED))));
        AutomaticWeaponPrerequisiteDecision decision =
                new AutomaticWeaponPrerequisiteDecision(
                        AUTOMATIC,
                        AutomaticWeaponPrerequisiteDecision.Strategy.SHARED_TRUNK,
                        Optional.of(0),
                        1,
                        2,
                        2,
                        2,
                        10_000,
                        true,
                        Map.of(
                                AUTHORED,
                                AutomaticWeaponPrerequisiteDecision.ParentRelation
                                        .AUTHORED_SAME_FAMILY,
                                EXCLUDED,
                                AutomaticWeaponPrerequisiteDecision.ParentRelation
                                        .AUTHORED_CROSS_FAMILY),
                        Optional.empty(),
                        false,
                        false,
                        Optional.empty(),
                        Optional.of(new AutomaticWeaponPrerequisiteDecision
                                .AlternativeRouteReview(
                                        EXCLUDED,
                                        AutomaticWeaponPrerequisiteDecision
                                                .AlternativeRouteOutcome.ACCEPTED_EXACT,
                                        20L,
                                        20L,
                                        30L,
                                        30L,
                                        15_000L,
                                        15_000L,
                                        2_500,
                                        3,
                                        true)),
                        AutomaticWeaponPrerequisiteDecision.GeneratedRequirementShape
                                .ALTERNATIVE_ROUTES);
        AutomaticWeaponPrerequisitePlan plan = new AutomaticWeaponPrerequisitePlan(
                PROFILE,
                TREE,
                AutomaticPlacementMode.CONNECTED,
                PrerequisiteStrategy.GROUPED_ROUTES_V1,
                5L,
                7L,
                1,
                Map.of(AUTOMATIC, List.of(AUTHORED, EXCLUDED)),
                Map.of(AUTOMATIC, groupedRequirements),
                Map.of(),
                Map.of(AUTOMATIC, decision),
                Map.of());
        AutomaticWeaponPlacementDiagnostics diagnostics =
                AutomaticWeaponPlacementDiagnostics.create(
                        PROFILE, candidates, plan);
        assertEquals(2, diagnostics.generatedPrerequisiteCount());
        assertEquals(1, diagnostics.generatedRequirementGroupCount());
        assertEquals(1, diagnostics.generatedAlternativeGroupCount());
        assertEquals(
                AutomaticWeaponPlacementPolicy.MergeIntervalBehavior
                        .IGNORED_GROUPED_ROUTES_V1,
                diagnostics.mergeIntervalBehavior());
        assertEquals(
                AutomaticWeaponAlternativeRouteGuard.CONTRACT,
                diagnostics.generatedParentCostGuard());
        assertEquals(
                groupedRequirements,
                diagnostics.entry(AUTOMATIC).orElseThrow().generatedRequirements());
        assertEquals(1,
                diagnostics.branchTopologySummary().alternativeRouteReviewCount());
        assertEquals(1,
                diagnostics.branchTopologySummary().acceptedAlternativeRouteCount());
        assertEquals(0, diagnostics.branchTopologySummary()
                .rejectedAlternativeRouteCostImbalanceCount());

        var root = JsonParser.parseString(BlueprintResearchCatalogExporter.export(
                BlueprintResearchSnapshot.EMPTY,
                Map.of(
                        AUTOMATIC, data(AUTOMATIC),
                        AUTHORED, data(AUTHORED),
                        EXCLUDED, data(EXCLUDED)),
                PROFILE,
                diagnostics)).getAsJsonObject();
        var automaticSummary = root.getAsJsonObject("automatic_placement");
        assertEquals("grouped_routes_v1",
                automaticSummary.get("prerequisite_strategy").getAsString());
        assertEquals(2, automaticSummary
                .get("planned_prerequisite_count").getAsInt());
        assertEquals(1, automaticSummary
                .get("planned_requirement_group_count").getAsInt());
        assertEquals(1, automaticSummary
                .get("planned_alternative_group_count").getAsInt());
        var branchSummary = automaticSummary.getAsJsonObject("branch_prerequisites");
        assertEquals(1, branchSummary.get("alternative_route_review_count").getAsInt());
        assertEquals(1, branchSummary.get("accepted_alternative_route_count").getAsInt());
        var automaticEntry = root.getAsJsonArray("entries").asList().stream()
                .map(value -> value.getAsJsonObject())
                .filter(value -> AUTOMATIC.toString().equals(
                        value.get("blueprint").getAsString()))
                .findFirst().orElseThrow()
                .getAsJsonObject("automatic_placement");
        assertEquals(
                groupedRequirements.allOf().get(0).anyOf().stream()
                        .map(ResourceLocation::toString).toList(),
                automaticEntry.getAsJsonArray("planned_prerequisite_groups")
                        .get(0).getAsJsonObject().getAsJsonArray("any_of")
                        .asList().stream().map(value -> value.getAsString()).toList());
        var review = automaticEntry.getAsJsonObject("prerequisite_decision")
                .getAsJsonObject("alternative_route_review");
        assertEquals("accepted_exact", review.get("outcome").getAsString());
        assertEquals(15_000L,
                review.get("route_cost_ratio_upper_bound_basis_points").getAsLong());
        assertEquals(2_500,
                review.get("mandatory_ancestry_overlap_basis_points").getAsInt());
        assertEquals("alternative_routes",
                automaticEntry.getAsJsonObject("prerequisite_decision")
                        .get("requirement_shape").getAsString());

        AutomaticWeaponPrerequisitePlan legacyIdentity =
                new AutomaticWeaponPrerequisitePlan(
                        PROFILE,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        5L,
                        7L,
                        1,
                        Map.of(AUTOMATIC, List.of(AUTHORED, EXCLUDED)),
                        Map.of(),
                        Map.of(),
                        Map.of());
        assertThrows(IllegalArgumentException.class, () ->
                AutomaticWeaponPlacementDiagnostics.create(
                        PROFILE, candidates, legacyIdentity));
    }

    @Test
    void hybridDiagnosticsExportExplicitMixedRelationshipAuthority() {
        AutomaticWeaponPlacementPolicy hybridPolicy =
                new AutomaticWeaponPlacementPolicy(
                        3,
                        0,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                        3,
                        1,
                        AutomaticWeaponPlacementPolicy.LayeringStrategy
                                .DYNAMIC_STAT_LAYERS,
                        9,
                        List.of(),
                        2,
                        PrerequisiteStrategy.HYBRID_ROUTES_V1);
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                new AutomaticWeaponPlacementCandidateSnapshot(
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        hybridPolicy,
                        5L,
                        7L,
                        4,
                        Map.of(
                                AUTOMATIC.toString(),
                                proposal(AUTOMATIC).withProgressionCoordinate(
                                        new ResearchTechTreeContract
                                                .ProgressionCoordinate(
                                                        2,
                                                        17L << 56,
                                                        Optional.empty()))),
                        Map.of(),
                        Set.of(
                                AUTHORED.toString(),
                                EXCLUDED.toString(),
                                UNPLACED.toString()),
                        Set.of());
        List<ResourceLocation> parents = List.of(AUTHORED, EXCLUDED, UNPLACED);
        ResearchRequirements mixed = new ResearchRequirements(List.of(
                new ResearchPrerequisiteGroup(parents.subList(0, 2)),
                ResearchPrerequisiteGroup.singleton(parents.get(2))));
        AutomaticWeaponPrerequisiteDecision decision =
                new AutomaticWeaponPrerequisiteDecision(
                        AUTOMATIC,
                        AutomaticWeaponPrerequisiteDecision.Strategy.TRANSITION_LOCAL,
                        Optional.of(0),
                        2,
                        1,
                        3,
                        3,
                        10_000,
                        true,
                        Map.of(
                                AUTHORED,
                                AutomaticWeaponPrerequisiteDecision.ParentRelation
                                        .AUTHORED_SAME_FAMILY,
                                EXCLUDED,
                                AutomaticWeaponPrerequisiteDecision.ParentRelation
                                        .AUTHORED_SAME_FAMILY,
                                UNPLACED,
                                AutomaticWeaponPrerequisiteDecision.ParentRelation
                                        .AUTHORED_CROSS_FAMILY),
                        Optional.empty(),
                        false,
                        false,
                        Optional.empty(),
                        Optional.empty(),
                        AutomaticWeaponPrerequisiteDecision.GeneratedRequirementShape
                                .ALTERNATIVE_ROUTES_WITH_MANDATORY_GATEWAY);
        AutomaticWeaponPrerequisitePlan plan = new AutomaticWeaponPrerequisitePlan(
                PROFILE,
                TREE,
                AutomaticPlacementMode.CONNECTED,
                PrerequisiteStrategy.HYBRID_ROUTES_V1,
                5L,
                7L,
                1,
                Map.of(AUTOMATIC, parents),
                Map.of(AUTOMATIC, mixed),
                Map.of(),
                Map.of(AUTOMATIC, decision),
                Map.of());
        AutomaticWeaponPrerequisiteDecision mislabeled =
                new AutomaticWeaponPrerequisiteDecision(
                        decision.blueprintId(),
                        decision.strategy(),
                        decision.branchIndex(),
                        decision.rankIndex(),
                        decision.familyStartIndex(),
                        decision.transitionEndIndex(),
                        decision.desiredParentCount(),
                        decision.secondParentQuotaBasisPoints(),
                        decision.secondParentEligible(),
                        decision.selectedParentRelations(),
                        decision.mergeRejection(),
                        decision.depthShortcut(),
                        decision.terminalPeer(),
                        decision.publishedRank(),
                        decision.alternativeRouteReview(),
                        AutomaticWeaponPrerequisiteDecision.GeneratedRequirementShape
                                .MANDATORY_SINGLETONS);
        assertThrows(IllegalArgumentException.class, () ->
                new AutomaticWeaponPrerequisitePlan(
                        PROFILE,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        PrerequisiteStrategy.HYBRID_ROUTES_V1,
                        5L,
                        7L,
                        1,
                        Map.of(AUTOMATIC, parents),
                        Map.of(AUTOMATIC, mixed),
                        Map.of(),
                        Map.of(AUTOMATIC, mislabeled),
                        Map.of()));

        AutomaticWeaponPlacementDiagnostics diagnostics =
                AutomaticWeaponPlacementDiagnostics.create(PROFILE, candidates, plan);
        assertEquals(0, diagnostics.generatedAlternativeRouteDecisionCount());
        assertEquals(0, diagnostics.generatedMandatoryConvergenceCount());
        assertEquals(1, diagnostics.generatedMixedRequirementCount());
        assertEquals(
                AutomaticWeaponPlacementPolicy.MergeIntervalBehavior
                        .HYBRID_MANDATORY_GATEWAY_SCHEDULE,
                diagnostics.mergeIntervalBehavior());

        var root = JsonParser.parseString(BlueprintResearchCatalogExporter.export(
                BlueprintResearchSnapshot.EMPTY,
                Map.of(
                        AUTOMATIC, data(AUTOMATIC),
                        AUTHORED, data(AUTHORED),
                        EXCLUDED, data(EXCLUDED),
                        UNPLACED, data(UNPLACED)),
                PROFILE,
                diagnostics)).getAsJsonObject();
        var summary = root.getAsJsonObject("automatic_placement");
        assertEquals("hybrid_routes_v1",
                summary.get("prerequisite_strategy").getAsString());
        assertEquals(1, summary.get("planned_mixed_requirement_count").getAsInt());
        var exportedDecision = root.getAsJsonArray("entries").asList().stream()
                .map(value -> value.getAsJsonObject())
                .filter(value -> AUTOMATIC.toString().equals(
                        value.get("blueprint").getAsString()))
                .findFirst().orElseThrow()
                .getAsJsonObject("automatic_placement")
                .getAsJsonObject("prerequisite_decision");
        assertEquals("alternative_routes_with_mandatory_gateway",
                exportedDecision.get("requirement_shape").getAsString());
    }

    @Test
    void formatFourteenExportsClosureInflationRejectionEvidence() {
        ResourceLocation rejected = id("tacz:other_authored");
        AutomaticWeaponPrerequisiteDecision decision =
                new AutomaticWeaponPrerequisiteDecision(
                        AUTOMATIC,
                        AutomaticWeaponPrerequisiteDecision.Strategy.SHARED_TRUNK,
                        Optional.of(0),
                        1,
                        2,
                        2,
                        2,
                        5_000,
                        true,
                        Map.of(
                                AUTHORED,
                                AutomaticWeaponPrerequisiteDecision.ParentRelation
                                        .AUTHORED_SAME_FAMILY,
                                EXCLUDED,
                                AutomaticWeaponPrerequisiteDecision.ParentRelation
                                        .AUTHORED_CROSS_FAMILY),
                        Optional.of(new AutomaticWeaponPrerequisiteDecision.MergeRejection(
                                rejected,
                                AutomaticWeaponPrerequisiteDecision.MergeRejectionReason
                                        .CLOSURE_INFLATION,
                                40L,
                                35L,
                                70L,
                                60L)),
                        false,
                        false);
        AutomaticWeaponPlacementDiagnostics diagnostics =
                new AutomaticWeaponPlacementDiagnostics(
                        PROFILE,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        5L,
                        7L,
                        3,
                        Map.of(
                                AUTHORED,
                                new AutomaticWeaponPlacementDiagnostics.Entry(
                                        AUTHORED,
                                        State.AUTHORED,
                                        Optional.empty(),
                                        List.of(),
                                        Optional.empty()),
                                EXCLUDED,
                                new AutomaticWeaponPlacementDiagnostics.Entry(
                                        EXCLUDED,
                                        State.AUTHORED,
                                        Optional.empty(),
                                        List.of(),
                                        Optional.empty()),
                                AUTOMATIC,
                                new AutomaticWeaponPlacementDiagnostics.Entry(
                                        AUTOMATIC,
                                        State.AUTOMATIC,
                                        Optional.of(proposal(AUTOMATIC)),
                                        List.of(AUTHORED, EXCLUDED),
                                        Optional.empty(),
                                        Optional.of(decision))));
        assertFalse(diagnostics.publicationSummary().rankReconciliationComplete());
        assertFalse(diagnostics.publicationSummary().complete());
        var root = JsonParser.parseString(BlueprintResearchCatalogExporter.export(
                BlueprintResearchSnapshot.EMPTY,
                Map.of(
                        AUTHORED, data(AUTHORED),
                        EXCLUDED, data(EXCLUDED),
                        AUTOMATIC, data(AUTOMATIC)),
                PROFILE,
                diagnostics)).getAsJsonObject();
        var summary = root.getAsJsonObject("automatic_placement")
                .getAsJsonObject("branch_prerequisites");
        assertEquals(1, summary.get("closure_inflation_rejection_count").getAsInt());
        assertEquals(1,
                summary.get("closure_rejected_additional_parent_count").getAsInt());
        assertEquals(
                summary.get("same_family_merge_count").getAsInt(),
                summary.get("same_family_multi_parent_set_count").getAsInt());
        assertEquals(
                summary.get("cross_family_merge_count").getAsInt(),
                summary.get("cross_family_multi_parent_set_count").getAsInt());
        var automatic = root.getAsJsonArray("entries").asList().stream()
                .map(value -> value.getAsJsonObject())
                .filter(value -> AUTOMATIC.toString().equals(
                        value.get("blueprint").getAsString()))
                .findFirst().orElseThrow();
        var rejection = automatic.getAsJsonObject("automatic_placement")
                .getAsJsonObject("prerequisite_decision")
                .getAsJsonObject("merge_rejection");
        assertEquals("merge_rejected_closure_inflation",
                rejection.get("reason").getAsString());
        assertEquals(70L, rejection.get("union_closure_cost").getAsLong());
        assertEquals(60L,
                rejection.get("maximum_allowed_closure_cost").getAsLong());
        assertEquals("shared_trunk_interconnection",
                automatic.getAsJsonObject("authoring")
                        .get("merge_reason").getAsString());
    }

    @Test
    void diagnosticsAcceptTheMaximumCatalogAndRejectAnythingLarger() {
        Set<String> authored = java.util.stream.IntStream.range(0, 4096)
                .mapToObj(index -> "large_pack:weapon_" + index)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                new AutomaticWeaponPlacementCandidateSnapshot(
                        TREE,
                        AutomaticPlacementMode.INDEPENDENT,
                        AutomaticWeaponPlacementPolicy.DEFAULT,
                        11L,
                        13L,
                        4096,
                        Map.of(),
                        Map.of(),
                        authored,
                        Set.of());
        AutomaticWeaponPlacementDiagnostics diagnostics =
                AutomaticWeaponPlacementDiagnostics.create(
                        PROFILE,
                        candidates,
                        new AutomaticWeaponPrerequisitePlan(
                                PROFILE,
                                TREE,
                                AutomaticPlacementMode.INDEPENDENT,
                                11L,
                                13L,
                                0,
                                Map.of(),
                                Map.of()));

        assertEquals(4096, diagnostics.entries().size());
        assertEquals(4096L, diagnostics.count(State.AUTHORED));
        assertFalse(diagnostics.publicationSummary().applicable());
        assertTrue(diagnostics.publicationSummary().complete());
        assertEquals("large_pack:weapon_0",
                diagnostics.entries().keySet().iterator().next().toString());

        assertThrows(IllegalArgumentException.class, () ->
                new AutomaticWeaponPlacementCandidateSnapshot(
                        TREE,
                        AutomaticPlacementMode.INDEPENDENT,
                        AutomaticWeaponPlacementPolicy.DEFAULT,
                        11L,
                        13L,
                        4097,
                        Map.of(),
                        Map.of(),
                        authored,
                        Set.of()));

        Map<ResourceLocation, AutomaticWeaponPlacementDiagnostics.Entry> oversized =
                new LinkedHashMap<>();
        java.util.stream.IntStream.range(0, 4097).forEach(index -> {
            ResourceLocation id = id("large_pack:oversized_" + index);
            oversized.put(id, new AutomaticWeaponPlacementDiagnostics.Entry(
                    id,
                    State.AUTHORED,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()));
        });
        assertThrows(IllegalArgumentException.class, () ->
                new AutomaticWeaponPlacementDiagnostics(
                        PROFILE,
                        TREE,
                        AutomaticPlacementMode.INDEPENDENT,
                        11L,
                        13L,
                        4097,
                        oversized));
    }

    @Test
    void compatibilityPlanReportsMissingCanonicalCoordinatesWithoutInventingThem() {
        AutomaticWeaponPrerequisiteDecision decision =
                new AutomaticWeaponPrerequisiteDecision(
                        AUTOMATIC,
                        AutomaticWeaponPrerequisiteDecision.Strategy.FOUNDATION,
                        Optional.of(0),
                        0,
                        0,
                        0,
                        1,
                        Map.of(AUTHORED, AutomaticWeaponPrerequisiteDecision
                                .ParentRelation.AUTHORED_SAME_FAMILY),
                        false,
                        false).withPublishedRank(4);
        AutomaticWeaponPlacementDiagnostics diagnostics =
                AutomaticWeaponPlacementDiagnostics.create(
                        PROFILE,
                        candidates(),
                        new AutomaticWeaponPrerequisitePlan(
                                PROFILE,
                                TREE,
                                AutomaticPlacementMode.CONNECTED,
                                5L,
                                7L,
                                1,
                                Map.of(AUTOMATIC, List.of(AUTHORED)),
                                Map.of(),
                                Map.of(AUTOMATIC, decision)));

        assertTrue(diagnostics.publicationSummary().applicable());
        assertFalse(diagnostics.publicationSummary()
                .canonicalBranchCoordinatesAvailable());
        assertFalse(diagnostics.publicationSummary()
                .canonicalBranchCoordinatesComplete());
        assertTrue(diagnostics.publicationSummary().rankReconciliationComplete());
        assertFalse(diagnostics.publicationSummary().complete());
    }

    @Test
    void authoringReportExplainsMechanicalSimilarityAndFanOut() {
        WeaponStatEvidence parentEvidence = evidence(AUTHORED.toString(), "pistol");
        WeaponStatEvidence targetEvidence = evidence(AUTOMATIC.toString(), "pistol");
        WeaponMechanicalScore parentScore = score(parentEvidence, 70);
        WeaponMechanicalScore targetScore = score(targetEvidence, 80);
        AutomaticWeaponEvidenceSnapshot evidence = new AutomaticWeaponEvidenceSnapshot(
                5L,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                "fixture",
                2,
                2,
                2,
                Set.of(AUTHORED.toString(), AUTOMATIC.toString()),
                Map.of(
                        AUTHORED.toString(), parentEvidence,
                        AUTOMATIC.toString(), targetEvidence),
                Map.of(
                        AUTHORED.toString(), parentScore,
                        AUTOMATIC.toString(), targetScore),
                Map.of(),
                AutomaticWeaponPlacementPlan.EMPTY);
        AutomaticWeaponPlacementDiagnostics diagnostics =
                new AutomaticWeaponPlacementDiagnostics(
                        PROFILE,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        5L,
                        7L,
                        2,
                        Map.of(
                                AUTHORED,
                                new AutomaticWeaponPlacementDiagnostics.Entry(
                                        AUTHORED,
                                        State.AUTHORED,
                                        Optional.empty(),
                                        List.of(),
                                        Optional.empty()),
                                AUTOMATIC,
                                new AutomaticWeaponPlacementDiagnostics.Entry(
                                        AUTOMATIC,
                                        State.AUTOMATIC,
                                        Optional.of(proposal(AUTOMATIC)),
                                        List.of(AUTHORED),
                                        Optional.empty())));
        ResearchTechTreeAuthoringReport.Entry entry =
                ResearchTechTreeAuthoringReport.create(
                        BlueprintResearchSnapshot.EMPTY,
                        Map.of(AUTHORED, data(AUTHORED), AUTOMATIC, data(AUTOMATIC)),
                        PROFILE,
                        diagnostics,
                        evidence)
                        .entries().get(AUTOMATIC);

        assertEquals(80, entry.mechanicalScore().orElseThrow());
        assertEquals(98, entry.similarityScore().orElseThrow());
        assertEquals(1, entry.parentChoices().size());
        assertEquals(1, entry.parentChoices().get(0).dependentLoad());
        assertEquals(25, entry.fanOutPenalty());
        assertEquals("generated_rank_anchor_selection", entry.parentChoiceReason());
        assertEquals("none", entry.mergeReason());
    }

    private static AutomaticWeaponPlacementDiagnostics diagnostics() {
        return AutomaticWeaponPlacementDiagnostics.create(
                PROFILE,
                candidates(),
                new AutomaticWeaponPrerequisitePlan(
                        PROFILE,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        5L,
                        7L,
                        1,
                        Map.of(AUTOMATIC, List.of(AUTHORED)),
                        Map.of(),
                        Map.of(AUTOMATIC, new AutomaticWeaponPrerequisiteDecision(
                                AUTOMATIC,
                                AutomaticWeaponPrerequisiteDecision.Strategy.FOUNDATION,
                                Optional.of(0),
                                0,
                                0,
                                0,
                                1,
                                Map.of(AUTHORED, AutomaticWeaponPrerequisiteDecision
                                        .ParentRelation.AUTHORED_SAME_FAMILY),
                                false,
                                false).withPublishedRank(4)),
                        Map.of(AUTOMATIC, new AutomaticWeaponPrerequisitePlan.BranchCoordinate(
                                0,
                                0,
                                0,
                                0))));
    }

    private static AutomaticWeaponPlacementProposal proposal(ResourceLocation id) {
        return new AutomaticWeaponPlacementProposal(
                id.toString(),
                17,
                91,
                new ProgressionPosition(Tier.BASIC, 0, 17L << 56),
                3,
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                List.of());
    }

    private static WeaponStatEvidence evidence(String blueprintId, String archetype) {
        return new WeaponStatEvidence(
                blueprintId,
                archetype,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "unknown",
                false,
                false,
                List.of());
    }

    private static WeaponMechanicalScore score(WeaponStatEvidence evidence, int metricScore) {
        return new WeaponMechanicalScore(
                evidence,
                ResearchTechTreeContract.MechanicalRating.current(
                        metricScore, metricScore, 100),
                Map.of(),
                Map.of(),
                Map.of(MechanicalMetric.SUSTAINED_DPS.serializedName(), metricScore),
                List.of());
    }

    private static AutomaticWeaponPlacementCandidateSnapshot candidates() {
        AutomaticWeaponPlacementProposal proposal = new AutomaticWeaponPlacementProposal(
                AUTOMATIC.toString(),
                17,
                91,
                new ProgressionPosition(Tier.BASIC, 0, 17L << 56),
                3,
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                List.of());
        return new AutomaticWeaponPlacementCandidateSnapshot(
                TREE,
                AutomaticPlacementMode.CONNECTED,
                AutomaticWeaponPlacementPolicy.DEFAULT,
                5L,
                7L,
                4,
                Map.of(AUTOMATIC.toString(), proposal),
                Map.of(EXCLUDED.toString(), "review_required"),
                Set.of(AUTHORED.toString()),
                Set.of(UNPLACED.toString()));
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
