package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementDiagnostics;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.PrerequisiteStrategy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementProposal;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardEconomyProjection;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;

import net.minecraft.resources.ResourceLocation;

class ResearchGroupedRouteBaselineAuditTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation TREE = id("test:tree");
    private static final ResourceLocation LANE = id("test:weapons");
    private static final ResourceLocation A = id("test:a");
    private static final ResourceLocation B = id("test:b");
    private static final ResourceLocation C = id("test:c");
    private static final ResourceLocation D = id("test:d");

    @Test
    void estimatesGeneratedAnyOfWithoutChangingPublishedAndAuthority() {
        ResearchTreeGraph graph = graph();
        List<ResearchTreeGraph.Edge> publishedEdges = graph.edges();
        ResearchPointAwardEconomyProjection.Projection income =
                new ResearchPointAwardEconomyProjection.Projection(
                        1,
                        0,
                        18,
                        Map.of(ResearchPointAwardTrigger.Type.INTEGRATION, 18));

        ResearchGroupedRouteBaselineAudit.Audit audit =
                ResearchGroupedRouteBaselineAudit.audit(
                        graph, presentation(), diagnostics(), income);

        assertTrue(audit.available());
        assertEquals("generated_multi_parent_any_of", audit.interpretation());
        assertEquals(4, audit.weaponNodeCount());
        assertEquals(1, audit.automaticTargetCount());
        assertEquals(1, audit.matchedGeneratedTargetCount());
        assertEquals(0, audit.unmatchedGeneratedTargetCount());
        assertEquals(2, audit.generatedReferenceCount());
        assertEquals(0, audit.singleParentTargetCount());
        assertEquals(1, audit.alternativeGroupCandidateCount());
        assertEquals(1, audit.pairGroupCandidateCount());
        assertEquals(0, audit.largerGroupCandidateCount());
        assertEquals(2, audit.maximumAlternativeCount());
        assertEquals(0, audit.maximumSingleParentChain());
        assertEquals(new ResearchGroupedRouteBaselineAudit.IntDistribution(
                2, 1, 1, 1, 1, 1), audit.generatedFanOut());

        ResearchGroupedRouteBaselineAudit.AlternativeEvidence alternatives =
                audit.alternativeEvidence();
        assertEquals(1, alternatives.groupCount());
        assertEquals(0, alternatives.dependentAlternativePairCount());
        assertEquals(0, alternatives.sharedAncestryBasisPoints().median());
        assertEquals(10_000, alternatives.ancestryDivergenceBasisPoints().median());
        assertEquals(7_143, alternatives.routeCostBalanceBasisPoints().median());

        ResearchGroupedRouteBaselineAudit.RouteCostComparison routes = audit.routeCosts();
        assertEquals(1, routes.leafCount());
        assertEquals(25L, routes.currentMandatoryClosureCosts().minimum());
        assertEquals(25L, routes.currentMandatoryClosureCosts().maximum());
        assertEquals(18L, routes.counterfactualMinimumRouteEstimates().minimum());
        assertEquals(25L, routes.counterfactualMaximumRouteEstimates().maximum());
        assertEquals(0, routes.currentAffordableLeafCount());
        assertEquals(1, routes.counterfactualAffordableLeafCount());
        assertFalse(routes.estimateExact(),
                "The authored A-and-C merge remains a simultaneous requirement");
        assertTrue(audit.inputFingerprint().matches("[0-9a-f]{64}"));

        assertEquals(publishedEdges, graph.edges(),
                "The counterfactual must not mutate authoritative edges");
        assertEquals(audit, ResearchGroupedRouteBaselineAudit.audit(
                graph, presentation(), diagnostics(), income));
    }

    @Test
    void reportsPublicationMismatchesInsteadOfInventingAnAlternativeGroup() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, A, 5, 0),
                        node(1, B, 7, 0),
                        node(2, C, 10, 1),
                        node(3, D, 3, 1)),
                List.of(
                        new ResearchTreeGraph.Edge(A, C),
                        new ResearchTreeGraph.Edge(C, D)));

        ResearchGroupedRouteBaselineAudit.Audit audit =
                ResearchGroupedRouteBaselineAudit.audit(
                        graph, presentation(), diagnostics());

        assertTrue(audit.available());
        assertEquals(0, audit.matchedGeneratedTargetCount());
        assertEquals(1, audit.unmatchedGeneratedTargetCount());
        assertEquals(0, audit.alternativeGroupCandidateCount());
        assertTrue(audit.routeCosts().estimateExact());
    }

    @Test
    void unavailableAndInvalidInputsAreHandledWithoutFabricatingEvidence() {
        assertEquals(
                ResearchGroupedRouteBaselineAudit.Audit.EMPTY,
                ResearchGroupedRouteBaselineAudit.audit(
                        graph(), presentation(), null));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchGroupedRouteBaselineAudit.audit(
                        null, presentation(), diagnostics()));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchGroupedRouteBaselineAudit.audit(
                        graph(), null, diagnostics()));

        AutomaticWeaponPlacementDiagnostics legacy = diagnostics();
        Map<ResourceLocation, AutomaticWeaponPlacementDiagnostics.Entry> groupedEntries =
                new LinkedHashMap<>();
        legacy.entries().forEach((id, entry) -> {
            ResearchRequirements groupedRequirements =
                    entry.generatedPrerequisites().isEmpty()
                            ? ResearchRequirements.EMPTY
                            : new ResearchRequirements(List.of(
                                    new ResearchPrerequisiteGroup(
                                            entry.generatedPrerequisites())));
            groupedEntries.put(id, new AutomaticWeaponPlacementDiagnostics.Entry(
                    entry.blueprintId(),
                    entry.state(),
                    entry.proposal(),
                    entry.generatedPrerequisites(),
                    groupedRequirements,
                    entry.reason(),
                    entry.prerequisiteDecision()));
        });
        AutomaticWeaponPlacementDiagnostics grouped =
                new AutomaticWeaponPlacementDiagnostics(
                        legacy.profileId(),
                        legacy.treeId(),
                        legacy.mode(),
                        legacy.reviewHandling(),
                        legacy.layeringStrategy(),
                        PrerequisiteStrategy.GROUPED_ROUTES_V1,
                        legacy.maxGeneratedPrerequisites(),
                        legacy.mergeInterval(),
                        legacy.catalogRevision(),
                        legacy.researchRevision(),
                        legacy.catalogWeaponCount(),
                        legacy.topologyWeaponCount(),
                        legacy.resolvedNodesPerLayer(),
                        legacy.publicationSummary(),
                        groupedEntries);
        assertEquals(ResearchGroupedRouteBaselineAudit.Audit.EMPTY,
                ResearchGroupedRouteBaselineAudit.audit(
                        graph(), presentation(), grouped));
    }

    @Test
    void maximumPopulationUsesBoundedTraversal() {
        int nodeCount = ResearchTreeGraph.MAX_NODES;
        int rankWidth = 20;
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>(nodeCount);
        List<ResearchTreeGraph.Edge> edges = new ArrayList<>(nodeCount - rankWidth);
        List<ResearchTechTreePresentation.Member> members = new ArrayList<>(nodeCount);
        Map<ResourceLocation, AutomaticWeaponPlacementDiagnostics.Entry> entries =
                new LinkedHashMap<>();
        for (int index = 0; index < nodeCount; index++) {
            ResourceLocation id = id("maximum:weapon_" + index);
            boolean foundation = index < rankWidth;
            ResourceLocation parent = foundation
                    ? null : id("maximum:weapon_" + (index - rankWidth));
            nodes.add(node(index, id, 1, foundation ? 0 : 1));
            members.add(member(id, index / rankWidth, index % rankWidth));
            if (parent != null) {
                edges.add(new ResearchTreeGraph.Edge(parent, id));
            }
            entries.put(id, new AutomaticWeaponPlacementDiagnostics.Entry(
                    id,
                    AutomaticWeaponPlacementDiagnostics.State.AUTOMATIC,
                    Optional.of(proposal(id)),
                    foundation ? List.of() : List.of(parent),
                    foundation ? Optional.of("foundation") : Optional.empty()));
        }
        ResearchTreeGraph graph = new ResearchTreeGraph(nodes, edges);
        ResearchTechTreePresentation presentation = new ResearchTechTreePresentation(
                Optional.of(TREE),
                "Maximum",
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
                                LANE,
                                "Weapons",
                                Optional.empty(),
                                Optional.empty(),
                                0,
                                members)))));
        AutomaticWeaponPlacementDiagnostics diagnostics =
                new AutomaticWeaponPlacementDiagnostics(
                        PROFILE,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        1L,
                        1L,
                        nodeCount,
                        entries);

        ResearchGroupedRouteBaselineAudit.Audit audit =
                ResearchGroupedRouteBaselineAudit.audit(
                        graph, presentation, diagnostics);

        assertTrue(audit.available());
        assertEquals(nodeCount, audit.weaponNodeCount());
        assertEquals(nodeCount, audit.matchedGeneratedTargetCount());
        assertEquals(nodeCount - rankWidth, audit.generatedReferenceCount());
        assertEquals(nodeCount - rankWidth, audit.singleParentTargetCount());
        assertEquals(0, audit.alternativeGroupCandidateCount());
        assertEquals(204, audit.maximumSingleParentChain());
        assertEquals(20, audit.routeCosts().leafCount());
        assertEquals(205L, audit.routeCosts().currentMandatoryClosureCosts().maximum());
        assertTrue(audit.routeCosts().estimateExact());
    }

    private static ResearchTreeGraph graph() {
        return new ResearchTreeGraph(
                List.of(
                        node(0, A, 5, 0),
                        node(1, B, 7, 0),
                        node(2, C, 10, 2),
                        node(3, D, 3, 2)),
                List.of(
                        new ResearchTreeGraph.Edge(A, C),
                        new ResearchTreeGraph.Edge(B, C),
                        new ResearchTreeGraph.Edge(A, D),
                        new ResearchTreeGraph.Edge(C, D)));
    }

    private static ResearchTechTreePresentation presentation() {
        return new ResearchTechTreePresentation(
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
                                LANE,
                                "Weapons",
                                Optional.empty(),
                                Optional.empty(),
                                0,
                                List.of(
                                        member(A, 0, 0),
                                        member(B, 0, 1),
                                        member(C, 1, 0),
                                        member(D, 2, 0)))))));
    }

    private static AutomaticWeaponPlacementDiagnostics diagnostics() {
        return new AutomaticWeaponPlacementDiagnostics(
                PROFILE,
                TREE,
                AutomaticPlacementMode.CONNECTED,
                1L,
                1L,
                4,
                Map.of(
                        A, authored(A),
                        B, authored(B),
                        C, new AutomaticWeaponPlacementDiagnostics.Entry(
                                C,
                                AutomaticWeaponPlacementDiagnostics.State.AUTOMATIC,
                                Optional.of(proposal(C)),
                                List.of(A, B),
                                Optional.empty()),
                        D, authored(D)));
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
        int score = 50;
        int levelsPerTier = 3;
        return new AutomaticWeaponPlacementProposal(
                id.toString(),
                score,
                100,
                new ProgressionPosition(
                        Tier.forScore(score),
                        ResearchTechTreeContract.levelForScore(score, levelsPerTier),
                        1L),
                levelsPerTier,
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                List.of());
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

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
