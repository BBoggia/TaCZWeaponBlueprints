package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchGroupedRouteQualityAudit;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardEconomyProjection;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;

import net.minecraft.resources.ResourceLocation;

class ResearchGroupedRouteQualityBoundaryTest {
    @Test
    void largeAddonReportIsCompleteAndIterationOrderIndependent() {
        var scenario = AutomaticWeaponTopologyPhaseZeroFixture.largeAddon();
        var forward = AutomaticWeaponTopologyPhaseZeroFixture.groupedRouteQualityAudit(
                scenario, false);
        var reversed = AutomaticWeaponTopologyPhaseZeroFixture.groupedRouteQualityAudit(
                scenario, true);

        assertTrue(forward.available());
        assertEquals(287, forward.weaponNodeCount());
        assertEquals(forward, reversed);
        assertTrue(forward.alternativeGroupCount() > 0);
        assertEquals(
                forward.alternativeGroupCount(),
                forward.alternatives().mandatoryAncestryOverlapBasisPoints().sampleCount());
        assertEquals(
                forward.terminalRoutes().size(),
                forward.affordableTerminalCount()
                        + forward.unaffordableTerminalCount()
                        + forward.indeterminateTerminalCount());
    }

    @Test
    void auditRemainsBoundedAtMaximumCatalogPopulation() {
        assertTimeout(Duration.ofSeconds(25), () -> {
            var audit = maximumCatalogAudit();

            assertTrue(audit.available());
            assertEquals(4096, audit.weaponNodeCount());
            assertEquals(1, audit.automaticTargetCount());
            assertEquals(1, audit.matchedAutomaticTargetCount());
            assertTrue(audit.terminalRoutes().stream().allMatch(route -> route.exact()));
            assertEquals(4095, audit.singleRouteChainLengths().maximum());
        });
    }

    private static ResearchGroupedRouteQualityAudit.Audit maximumCatalogAudit() {
        int size = 4096;
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>(size);
        List<ResearchTreeGraph.RequirementGroup> groups = new ArrayList<>(size - 1);
        List<ResearchTechTreePresentation.Member> members = new ArrayList<>(size);
        Map<ResourceLocation, AutomaticWeaponPlacementDiagnostics.Entry> entries =
                new LinkedHashMap<>();
        ResourceLocation previous = null;
        for (int index = 0; index < size; index++) {
            ResourceLocation id = new ResourceLocation("phase_five", "weapon_" + index);
            nodes.add(new ResearchTreeGraph.Node(
                    index,
                    id,
                    "name.phase_five.weapon_" + index,
                    "rifle",
                    new ResourceLocation("phase_five", "slot/weapon_" + index),
                    JournalVisibility.FULL,
                    false,
                    true,
                    true,
                    1,
                    0,
                    index == 0 ? 0 : 1,
                    0,
                    ResearchTreeGraph.Availability.AVAILABLE));
            members.add(new ResearchTechTreePresentation.Member(
                    id,
                    index,
                    index,
                    Optional.empty(),
                    PlacementOrigin.AUTOMATIC,
                    Optional.empty()));
            if (previous != null) {
                groups.add(new ResearchTreeGraph.RequirementGroup(
                        id, 0, List.of(previous), 0, false));
            }
            entries.put(id, new AutomaticWeaponPlacementDiagnostics.Entry(
                    id,
                    AutomaticWeaponPlacementDiagnostics.State.AUTHORED,
                    Optional.empty(),
                    List.of(),
                    Optional.empty()));
            previous = id;
        }
        ResourceLocation terminal = previous;
        ResourceLocation parent = new ResourceLocation(
                "phase_five", "weapon_" + (size - 2));
        AutomaticWeaponPrerequisiteDecision decision =
                new AutomaticWeaponPrerequisiteDecision(
                        terminal,
                        AutomaticWeaponPrerequisiteDecision.Strategy.SPECIALIZATION,
                        Optional.of(0),
                        size - 1,
                        1,
                        1,
                        1,
                        0,
                        false,
                        Map.of(
                                parent,
                                AutomaticWeaponPrerequisiteDecision.ParentRelation.SAME_FAMILY),
                        Optional.empty(),
                        false,
                        true,
                        Optional.of(size - 1));
        entries.put(terminal, new AutomaticWeaponPlacementDiagnostics.Entry(
                terminal,
                AutomaticWeaponPlacementDiagnostics.State.AUTOMATIC,
                Optional.of(new AutomaticWeaponPlacementProposal(
                        terminal.toString(),
                        100,
                        100,
                        new ProgressionPosition(
                                Tier.forScore(100),
                                ResearchTechTreeContract.levelForScore(100, 3),
                                size - 1),
                        new ResearchTechTreeContract.ProgressionCoordinate(
                                size - 1, size - 1, Optional.empty()),
                        3,
                        ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                        ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                        ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                        List.of())),
                List.of(parent),
                new ResearchRequirements(List.of(
                        new ResearchPrerequisiteGroup(List.of(parent)))),
                Optional.empty(),
                Optional.of(decision)));
        ResearchTreeGraph graph = ResearchTreeGraph.withRequirementGroups(nodes, groups);
        ResearchTechTreePresentation presentation = new ResearchTechTreePresentation(
                Optional.of(new ResourceLocation("phase_five", "tree")),
                "Maximum",
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                20,
                List.of(new ResearchTechTreePresentation.DomainView(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.empty(),
                        Optional.empty(),
                        List.of(new ResearchTechTreePresentation.LaneView(
                                new ResourceLocation("phase_five", "weapons"),
                                "Weapons",
                                Optional.empty(),
                                Optional.empty(),
                                0,
                                members)))));
        AutomaticWeaponPlacementDiagnostics diagnostics =
                new AutomaticWeaponPlacementDiagnostics(
                        new ResourceLocation("phase_five", "profile"),
                        new ResourceLocation("phase_five", "tree"),
                        AutomaticPlacementMode.CONNECTED,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                        AutomaticWeaponPlacementPolicy.LayeringStrategy.DYNAMIC_STAT_LAYERS,
                        AutomaticWeaponPlacementPolicy.PrerequisiteStrategy.GROUPED_ROUTES_V1,
                        2,
                        4,
                        1L,
                        1L,
                        size,
                        size,
                        20,
                        new AutomaticWeaponPlacementDiagnostics.PublicationSummary(
                                true, 1, 0, 1, 1),
                        entries);
        return ResearchGroupedRouteQualityAudit.audit(
                graph,
                presentation,
                diagnostics,
                ResearchPointAwardEconomyProjection.Projection.EMPTY);
    }
}
