package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTechTreeTopologyAuditTest {
    private static final ResourceLocation A = id("test:a");
    private static final ResourceLocation B = id("test:b");
    private static final ResourceLocation C = id("test:c");
    private static final ResourceLocation D = id("test:d");

    @Test
    void auditsInternalConnectivityAndTruthfulBoundaryPrerequisitesSeparately() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(node(0, A, 0), node(1, B, 1), node(2, C, 1)),
                List.of(
                        new ResearchTreeGraph.Edge(A, B),
                        new ResearchTreeGraph.Edge(A, C)));
        ResearchTechTreeTopologyAudit.Audit audit = ResearchTechTreeTopologyAudit.audit(
                graph,
                presentation(
                        domain(Domain.WEAPONS, "test:weapons", member(A, Tier.STARTER, 0),
                                member(B, Tier.BASIC, 1)),
                        domain(Domain.AMMO, "test:ammo", member(C, Tier.BASIC, 1))));

        assertTrue(audit.allDomainsUnified());
        ResearchTechTreeTopologyAudit.DomainAudit weapons =
                audit.domain(Domain.WEAPONS).orElseThrow();
        assertEquals(2, weapons.nodeCount());
        assertEquals(1, weapons.internalEdgeCount());
        assertEquals(Set.of(A), weapons.rootIds());
        assertEquals(1, weapons.componentCount());
        assertEquals(2, weapons.reachableNodeCount());

        ResearchTechTreeTopologyAudit.DomainAudit ammo =
                audit.domain(Domain.AMMO).orElseThrow();
        assertEquals(0, ammo.internalEdgeCount());
        assertEquals(1, ammo.boundaryPrerequisiteCount());
        assertEquals(0, ammo.unplacedPrerequisiteCount());
        assertEquals(Set.of(C), ammo.rootIds());
        assertTrue(ammo.singleEntryUnified());
    }

    @Test
    void reportsDisconnectedDomainWithoutManufacturingAnEdge() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(node(0, A, 0), node(1, B, 0)),
                List.of());
        ResearchTechTreeTopologyAudit.Audit audit = ResearchTechTreeTopologyAudit.audit(
                graph,
                presentation(domain(
                        Domain.WEAPONS,
                        "test:weapons",
                        member(A, Tier.STARTER, 0),
                        member(B, Tier.BASIC, 1))));

        ResearchTechTreeTopologyAudit.DomainAudit domain =
                audit.domain(Domain.WEAPONS).orElseThrow();
        assertEquals(Set.of(A, B), domain.rootIds());
        assertEquals(2, domain.componentCount());
        assertEquals(2, domain.reachableNodeCount());
        assertFalse(domain.singleEntryUnified());
        assertFalse(audit.allDomainsUnified());
    }

    @Test
    void reportsDepthCapacityMergesCrossingsAndParentRetention() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(node(0, A, 0), node(1, B, 0), node(2, C, 1), node(3, D, 1)),
                List.of(
                        new ResearchTreeGraph.Edge(A, D),
                        new ResearchTreeGraph.Edge(B, C)));
        ResearchTechTreePresentation presentation = rankPresentation(
                rankMember(A, 0, 0),
                rankMember(B, 0, 1),
                rankMember(C, 2, 0),
                rankMember(D, 2, 1));
        ResearchTechTreeTopologyAudit.ParentFixture baseline =
                ResearchTechTreeTopologyAudit.ParentFixture.capture(graph, presentation);
        ResearchTechTreeTopologyAudit.DomainAudit domain =
                ResearchTechTreeTopologyAudit.audit(graph, presentation, null, baseline)
                        .domain(Domain.WEAPONS).orElseThrow();
        ResearchTechTreeTopologyAudit.ParentRetention retention =
                ResearchTechTreeTopologyAudit.audit(graph, presentation, null, baseline)
                        .parentRetention();

        assertEquals(2, domain.rootIds().size());
        assertEquals(1, domain.maximumPrerequisiteCount());
        assertEquals(1, domain.maximumDependentCount());
        assertEquals(1, domain.maximumDepth());
        assertEquals(2, domain.maximumRankPopulation());
        assertEquals(1, domain.emptyRankCount());
        assertEquals(1, domain.approximateEdgeCrossingCount());
        assertEquals(4, domain.totalEdgeRankSpan());
        assertEquals(2, domain.maximumEdgeRankSpan());
        assertEquals(4, domain.manualNodeCount());
        assertEquals(0, domain.automaticNodeCount());
        assertTrue(retention.available());
        assertEquals(4, retention.comparedNodeCount());
        assertEquals(10_000, retention.retentionBasisPoints());
        assertTrue(retention.changedNodeIds().isEmpty());

        ResearchTreeGraph changedGraph = new ResearchTreeGraph(
                List.of(node(0, A, 0), node(1, B, 0), node(2, C, 1), node(3, D, 1)),
                List.of(
                        new ResearchTreeGraph.Edge(A, C),
                        new ResearchTreeGraph.Edge(B, D)));
        ResearchTechTreeTopologyAudit.ParentRetention changed =
                ResearchTechTreeTopologyAudit.audit(
                        changedGraph, presentation, null, baseline).parentRetention();
        assertEquals(4, changed.comparedNodeCount());
        assertEquals(2, changed.retainedParentSetCount());
        assertEquals(2, changed.changedParentSetCount());
        assertEquals(5_000, changed.retentionBasisPoints());
        assertEquals(List.of(C, D), changed.changedNodeIds());
    }

    @Test
    void identifiesAMergeAcrossBranchesBelowOneFoundation() {
        ResourceLocation left = id("test:left");
        ResourceLocation right = id("test:right");
        ResourceLocation merge = id("test:merge");
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, A, 0),
                        node(1, left, 1),
                        node(2, right, 1),
                        node(3, merge, 2)),
                List.of(
                        new ResearchTreeGraph.Edge(A, left),
                        new ResearchTreeGraph.Edge(A, right),
                        new ResearchTreeGraph.Edge(left, merge),
                        new ResearchTreeGraph.Edge(right, merge)));
        ResearchTechTreeTopologyAudit.DomainAudit domain =
                ResearchTechTreeTopologyAudit.audit(
                        graph,
                        rankPresentation(
                                rankMember(A, 0, 0),
                                rankMember(left, 1, 0),
                                rankMember(right, 1, 1),
                                rankMember(merge, 2, 0)))
                        .domain(Domain.WEAPONS).orElseThrow();

        assertEquals(1, domain.rootIds().size());
        assertEquals(1, domain.mergeCount());
        assertEquals(1, domain.crossBranchMergeCount());
        assertEquals(2, domain.maximumPrerequisiteCount());
        assertEquals(2, domain.maximumDependentCount());
        assertEquals(2, domain.maximumDepth());
    }

    @Test
    void emptyPresentationHasAnEmptyAuditAndNullInputsAreRejected() {
        assertEquals(
                ResearchTechTreeTopologyAudit.Audit.EMPTY,
                ResearchTechTreeTopologyAudit.audit(
                        ResearchTreeGraph.EMPTY, ResearchTechTreePresentation.EMPTY));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTechTreeTopologyAudit.audit(null, ResearchTechTreePresentation.EMPTY));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTechTreeTopologyAudit.audit(ResearchTreeGraph.EMPTY, null));
    }

    private static ResearchTechTreePresentation presentation(
            ResearchTechTreePresentation.DomainView... domains) {
        return new ResearchTechTreePresentation(
                Optional.of(id("test:tree")),
                "Tree",
                Optional.empty(),
                Optional.empty(),
                Arrays.stream(Tier.values())
                        .map(tier -> new ResearchTechTreePresentation.TierLabel(
                                tier, tier.name(), Optional.empty()))
                        .toList(),
                List.of(domains));
    }

    private static ResearchTechTreePresentation rankPresentation(
            ResearchTechTreePresentation.Member... members) {
        return new ResearchTechTreePresentation(
                Optional.of(id("test:tree")),
                "Tree",
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(domain(Domain.WEAPONS, "test:weapons", members)));
    }

    private static ResearchTechTreePresentation.Member rankMember(
            ResourceLocation id,
            int rank,
            long siblingOrder) {
        return new ResearchTechTreePresentation.Member(
                id,
                rank,
                siblingOrder,
                Optional.empty(),
                ResearchTechTreeContract.PlacementOrigin.EXACT,
                Optional.empty());
    }

    private static ResearchTechTreePresentation.DomainView domain(
            Domain domain,
            String laneId,
            ResearchTechTreePresentation.Member... members) {
        return new ResearchTechTreePresentation.DomainView(
                domain,
                domain.name(),
                Optional.empty(),
                Optional.empty(),
                List.of(new ResearchTechTreePresentation.LaneView(
                        id(laneId),
                        domain.name(),
                        Optional.empty(),
                        Optional.empty(),
                        0,
                        List.of(members))));
    }

    private static ResearchTechTreePresentation.Member member(
            ResourceLocation id,
            Tier tier,
            int order) {
        return new ResearchTechTreePresentation.Member(id, tier, order, Optional.empty());
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            ResourceLocation id,
            int prerequisiteCount) {
        return new ResearchTreeGraph.Node(
                ordinal,
                id,
                "item." + id.getPath(),
                "fixture",
                id,
                JournalVisibility.FULL,
                false,
                true,
                true,
                2,
                0,
                prerequisiteCount,
                0,
                ResearchTreeGraph.Availability.AVAILABLE);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
