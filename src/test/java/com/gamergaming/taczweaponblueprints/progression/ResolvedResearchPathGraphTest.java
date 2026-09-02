package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;

import net.minecraft.resources.ResourceLocation;

class ResolvedResearchPathGraphTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation TARGET = id("test:target");
    private static final ResourceLocation A = id("test:a");
    private static final ResourceLocation B = id("test:b");
    private static final ResourceLocation C = id("test:c");

    @Test
    void canonicalIndicesAreIndependentOfDiscoveryAndTopologyIsSeparate() {
        PlayerRecipeData data = data();
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(B, ResearchRequirements.EMPTY),
                spec(A, ResearchRequirements.EMPTY),
                spec(TARGET, grouped(singleton(B), singleton(A))));

        ResolvedResearchPathGraph.Graph graph = graph(data, policies, TARGET);

        assertEquals(List.of(A, B, TARGET), graph.nodes().stream()
                .map(ResolvedResearchPathGraph.Node::blueprintId).toList());
        assertEquals(List.of(0, 1, 2), graph.topologicalOrder());
        assertEquals(ResolvedResearchPathGraph.GraphShape.MANDATORY_DAG, graph.shape());
        assertEquals(List.of(0, 1), graph.target().groups().stream()
                .map(group -> group.alternatives().get(0).nodeIndex()).toList());
        assertEquals(3, graph.diagnostics().nodeCount());
        assertEquals(2, graph.diagnostics().edgeCount());
    }

    @Test
    void normalizedStatesDistinguishConnectedAndDisconnectedKnowledge() {
        PlayerRecipeData data = data();
        data.addBlueprint(A.toString());
        data.addBlueprint(B.toString());
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, ResearchRequirements.EMPTY),
                spec(C, ResearchRequirements.EMPTY),
                spec(B, singleton(C)),
                spec(TARGET, anyOf(A, B)));

        ResolvedResearchPathGraph.Graph graph = graph(data, policies, TARGET);

        assertEquals(
                ResolvedResearchPathGraph.NodeState.LEARNED_CONNECTED,
                graph.node(A).orElseThrow().state());
        assertEquals(
                ResolvedResearchPathGraph.NodeState.LEARNED_DISCONNECTED,
                graph.node(B).orElseThrow().state());
        assertEquals(
                ResolvedResearchPathGraph.NodeState.PURCHASABLE,
                graph.node(C).orElseThrow().state());
        assertEquals(
                ResolvedResearchPathGraph.GroupState.SATISFIED_BY_CONNECTED_SUPPORT,
                graph.target().groups().get(0).state());
    }

    @Test
    void exemptionTerminatesTheGroupWithoutResolvingUnusedAlternatives() {
        ResourceLocation exempt = id("test:exempt");
        ResourceLocation unused = id("test:unused");
        PlayerRecipeData data = data();
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(unused, ResearchRequirements.EMPTY),
                spec(TARGET, anyOf(unused, exempt)));
        AtomicInteger lookups = new AtomicInteger();

        ResolvedResearchPathGraph.Result result = ResolvedResearchPathGraph.build(
                TARGET,
                data,
                id -> {
                    lookups.incrementAndGet();
                    return policies.get(id);
                },
                exempt::equals,
                ResearchPathAuthority.authored());
        ResolvedResearchPathGraph.Graph graph = result.graph().orElseThrow();

        assertEquals(1, lookups.get());
        assertEquals(
                ResolvedResearchPathGraph.GroupState.SATISFIED_BY_EXEMPTION,
                graph.target().groups().get(0).state());
        assertEquals(
                ResolvedResearchPathGraph.NodeState.PROGRESSION_EXEMPT,
                graph.node(exempt).orElseThrow().state());
        assertTrue(graph.node(unused).isEmpty());
        assertEquals(
                Optional.of(ResearchPathAuthority.RootProvenance
                        .PROGRESSION_EXEMPT_BOUNDARY),
                graph.node(exempt).orElseThrow().rootProvenance());
    }

    @Test
    void classifierSelectsEachMutuallyExclusiveSolverShape() {
        PlayerRecipeData data = data();

        Map<ResourceLocation, BlueprintResearchPolicy> mandatory = policies(data,
                spec(A, ResearchRequirements.EMPTY),
                spec(B, ResearchRequirements.EMPTY),
                spec(TARGET, grouped(singleton(A), singleton(B))));
        assertEquals(
                ResolvedResearchPathGraph.GraphShape.MANDATORY_DAG,
                graph(data, mandatory, TARGET).shape());

        Map<ResourceLocation, BlueprintResearchPolicy> orPath = policies(data,
                spec(A, ResearchRequirements.EMPTY),
                spec(B, ResearchRequirements.EMPTY),
                spec(TARGET, anyOf(A, B)));
        assertEquals(
                ResolvedResearchPathGraph.GraphShape.OR_PATH_DAG,
                graph(data, orPath, TARGET).shape());

        ResourceLocation left = id("test:left");
        ResourceLocation right = id("test:right");
        ResourceLocation a2 = id("test:a2");
        ResourceLocation b2 = id("test:b2");
        Map<ResourceLocation, BlueprintResearchPolicy> separable = policies(data,
                spec(A, ResearchRequirements.EMPTY),
                spec(a2, ResearchRequirements.EMPTY),
                spec(B, ResearchRequirements.EMPTY),
                spec(b2, ResearchRequirements.EMPTY),
                spec(left, anyOf(A, a2)),
                spec(right, anyOf(B, b2)),
                spec(TARGET, grouped(singleton(left), singleton(right))));
        ResolvedResearchPathGraph.Graph separableGraph = graph(data, separable, TARGET);
        assertEquals(
                ResolvedResearchPathGraph.GraphShape.SEPARABLE_AND_OR_DAG,
                separableGraph.shape());
        assertEquals(1, separableGraph.diagnostics().maximumGroupOverlapWidth());

        Map<ResourceLocation, BlueprintResearchPolicy> interacting = policies(data,
                spec(A, ResearchRequirements.EMPTY),
                spec(B, ResearchRequirements.EMPTY),
                spec(C, ResearchRequirements.EMPTY),
                spec(left, anyOf(A, B)),
                spec(right, anyOf(A, C)),
                spec(TARGET, grouped(singleton(left), singleton(right))));
        ResolvedResearchPathGraph.Graph interactingGraph = graph(
                data, interacting, TARGET);
        assertEquals(
                ResolvedResearchPathGraph.GraphShape.GENERAL_AND_OR_DAG,
                interactingGraph.shape());
        assertEquals(2, interactingGraph.diagnostics().maximumGroupOverlapWidth());
        assertEquals(1, interactingGraph.diagnostics().generalSearchNodeCount());
    }

    @Test
    void unusableAlternativeKeepsItsExactCauseWithoutInvalidatingAnotherRoute() {
        PlayerRecipeData data = data();
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, ResearchRequirements.EMPTY, true),
                spec(B, ResearchRequirements.EMPTY),
                spec(TARGET, anyOf(A, B)));

        ResolvedResearchPathGraph.Graph graph = graph(data, policies, TARGET);
        ResolvedResearchPathGraph.RequirementGroup group = graph.target().groups().get(0);
        ResolvedResearchPathGraph.Alternative blocked = group.alternatives().stream()
                .filter(alternative -> graph.nodes().get(alternative.nodeIndex())
                        .blueprintId().equals(A))
                .findFirst().orElseThrow();

        assertEquals(ResolvedResearchPathGraph.GroupState.REQUIRES_ALTERNATIVE_SELECTION,
                group.state());
        assertEquals(ResolvedResearchPathGraph.NodeState.UNUSABLE, blocked.state());
        assertEquals(Optional.of(BlueprintResearchService.Status.BLOCKED), blocked.failure());
        assertEquals(ResolvedResearchPathGraph.GraphShape.MANDATORY_DAG, graph.shape());
    }

    @Test
    void automaticAuthorityFailureCannotBecomeAnIndexedRoot() {
        PlayerRecipeData data = data();
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(TARGET, ResearchRequirements.EMPTY));

        ResolvedResearchPathGraph.Result unavailable = ResolvedResearchPathGraph.build(
                TARGET,
                data,
                policies::get,
                ignored -> false,
                ResearchPathAuthority.automaticUnavailable(Set.of(TARGET)));
        assertEquals(
                BlueprintResearchService.Status.TECH_TREE_UNAVAILABLE,
                unavailable.status());

        ResolvedResearchPathGraph.Result filtered = ResolvedResearchPathGraph.build(
                TARGET,
                data,
                policies::get,
                ignored -> false,
                ResearchPathAuthority.automaticReady(
                        Set.of(TARGET),
                        Map.of(TARGET, ResearchPathAuthority.NodeExpectation.requirements(
                                List.of(Set.of(A))))));
        assertEquals(BlueprintResearchService.Status.UNSATISFIABLE, filtered.status());

        ResourceLocation root = id("test:root");
        Map<ResourceLocation, BlueprintResearchPolicy> connected = policies(data,
                spec(root, ResearchRequirements.EMPTY),
                spec(TARGET, singleton(root)));
        ResearchPathAuthority ready = ResearchPathAuthority.automaticReady(
                Set.of(root, TARGET),
                Map.of(
                        root, ResearchPathAuthority.NodeExpectation.root(
                                ResearchPathAuthority.RootProvenance.GENERATED_FOUNDATION),
                        TARGET, ResearchPathAuthority.NodeExpectation.requirements(
                                List.of(Set.of(root)))));
        ResolvedResearchPathGraph.Graph graph = ResolvedResearchPathGraph.build(
                TARGET, data, connected::get, ignored -> false, ready)
                .graph().orElseThrow();
        assertEquals(
                Optional.of(ResearchPathAuthority.RootProvenance.GENERATED_FOUNDATION),
                graph.node(root).orElseThrow().rootProvenance());
    }

    @Test
    void iterativeValidationDetectsCyclesAndDepthBoundaries() {
        PlayerRecipeData data = data();
        Map<ResourceLocation, BlueprintResearchPolicy> cycle = policies(data,
                spec(A, singleton(TARGET)),
                spec(TARGET, singleton(A)));
        ResolvedResearchPathGraph.Result cyclic = ResolvedResearchPathGraph.build(
                TARGET,
                data,
                cycle::get,
                ignored -> false,
                ResearchPathAuthority.authored());
        assertEquals(BlueprintResearchService.Status.POLICY_INELIGIBLE, cyclic.status());
        assertTrue(cyclic.diagnostics().cycleDetected());

        Map<ResourceLocation, BlueprintResearchPolicy> valid = chain(data, 64);
        assertTrue(ResolvedResearchPathGraph.build(
                id("test:chain_63"),
                data,
                valid::get,
                ignored -> false,
                ResearchPathAuthority.authored()).successful());

        Map<ResourceLocation, BlueprintResearchPolicy> oversized = chain(data, 65);
        ResolvedResearchPathGraph.Result oversizedResult =
                ResolvedResearchPathGraph.build(
                        id("test:chain_64"),
                        data,
                        oversized::get,
                        ignored -> false,
                        ResearchPathAuthority.authored());
        assertEquals(
                BlueprintResearchService.Status.PATH_TOO_LARGE,
                oversizedResult.status());
        assertEquals(65, oversizedResult.diagnostics().maximumDepth());
    }

    @Test
    void deterministicBudgetsAndEmergencyFuseFailClosed() {
        PlayerRecipeData data = data();
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, ResearchRequirements.EMPTY),
                spec(TARGET, singleton(A)));
        ResolvedResearchPathGraph.BuildLimits oneLookup =
                new ResolvedResearchPathGraph.BuildLimits(
                        100, 100, 1, 10_000, 64, Long.MAX_VALUE);
        assertEquals(
                BlueprintResearchService.Status.ROUTE_TOO_COMPLEX,
                ResolvedResearchPathGraph.buildWithControls(
                        TARGET,
                        data,
                        policies::get,
                        ignored -> false,
                        ResearchPathAuthority.authored(),
                        oneLookup,
                        () -> 0L).status());

        Map<ResourceLocation, BlueprintResearchPolicy> broad = broadMandatory(data, 300);
        AtomicInteger clockReads = new AtomicInteger();
        ResolvedResearchPathGraph.BuildLimits shortFuse =
                new ResolvedResearchPathGraph.BuildLimits(
                        1_000, 10_000, 1_000, 10_000_000, 64, 1L);
        assertEquals(
                BlueprintResearchService.Status.ROUTE_TOO_COMPLEX,
                ResolvedResearchPathGraph.buildWithControls(
                        TARGET,
                        data,
                        broad::get,
                        ignored -> false,
                        ResearchPathAuthority.authored(),
                        shortFuse,
                        () -> clockReads.getAndIncrement() == 0 ? 0L : 2L).status());
    }

    @Test
    void emergencyFuseIsCheckedBeforeReturningASmallCompletedGraph() {
        PlayerRecipeData data = data();
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, ResearchRequirements.EMPTY),
                spec(TARGET, singleton(A)));
        ResolvedResearchPathGraph.BuildLimits shortFuse =
                new ResolvedResearchPathGraph.BuildLimits(
                        100, 100, 100, 10_000, 64, 1L);
        AtomicInteger clockReads = new AtomicInteger();

        ResolvedResearchPathGraph.Result result =
                ResolvedResearchPathGraph.buildWithControls(
                        TARGET,
                        data,
                        policies::get,
                        ignored -> false,
                        ResearchPathAuthority.authored(),
                        shortFuse,
                        () -> clockReads.getAndIncrement() == 0 ? 0L : 2L);

        assertEquals(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX, result.status());
        assertTrue(clockReads.get() >= 2);
    }

    @Test
    void maximumCatalogFixtureBuildsWithinTheIndexedBounds() {
        PlayerRecipeData data = data();
        Map<ResourceLocation, BlueprintResearchPolicy> policies = broadMandatory(data, 4_096);

        ResolvedResearchPathGraph.Graph graph = graph(data, policies, TARGET);

        assertEquals(4_096, graph.nodes().size());
        assertEquals(4_095, graph.diagnostics().edgeCount());
        assertEquals(3, graph.diagnostics().maximumDepth());
        assertEquals(ResolvedResearchPathGraph.GraphShape.MANDATORY_DAG, graph.shape());
        assertTrue(graph.diagnostics().classificationBitWords()
                <= ResolvedResearchPathGraph.MAX_CLASSIFICATION_BIT_WORDS);
    }

    private static ResolvedResearchPathGraph.Graph graph(
            PlayerRecipeData data,
            Map<ResourceLocation, BlueprintResearchPolicy> policies,
            ResourceLocation target) {
        ResolvedResearchPathGraph.Result result = ResolvedResearchPathGraph.build(
                target,
                data,
                policies::get,
                ignored -> false,
                ResearchPathAuthority.authored());
        assertTrue(result.successful(), () -> "graph failed with " + result.status());
        return result.graph().orElseThrow();
    }

    private static Map<ResourceLocation, BlueprintResearchPolicy> chain(
            PlayerRecipeData data,
            int count) {
        List<Spec> specs = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            ResourceLocation node = id("test:chain_" + index);
            specs.add(spec(
                    node,
                    index == 0
                            ? ResearchRequirements.EMPTY
                            : singleton(id("test:chain_" + (index - 1)))));
        }
        return policies(data, specs.toArray(Spec[]::new));
    }

    private static Map<ResourceLocation, BlueprintResearchPolicy> broadMandatory(
            PlayerRecipeData data,
            int nodeCount) {
        if (nodeCount < 3) {
            throw new IllegalArgumentException("broad fixture needs at least three nodes");
        }
        int aggregatorCount = Math.min(64, Math.max(1, (nodeCount - 2 + 62) / 63));
        int leafCount = nodeCount - aggregatorCount - 1;
        List<Spec> specs = new ArrayList<>(nodeCount);
        List<ResourceLocation> leaves = new ArrayList<>(leafCount);
        for (int index = 0; index < leafCount; index++) {
            ResourceLocation leaf = id("test:broad_" + index);
            leaves.add(leaf);
            specs.add(spec(leaf, ResearchRequirements.EMPTY));
        }
        List<ResourceLocation> aggregators = new ArrayList<>(aggregatorCount);
        for (int index = 0; index < aggregatorCount; index++) {
            int from = index * leafCount / aggregatorCount;
            int to = (index + 1) * leafCount / aggregatorCount;
            ResourceLocation aggregator = id("test:aggregate_" + index);
            aggregators.add(aggregator);
            specs.add(spec(
                    aggregator,
                    mandatory(leaves.subList(from, to))));
        }
        specs.add(spec(TARGET, mandatory(aggregators)));
        return policies(data, specs.toArray(Spec[]::new));
    }

    private static Map<ResourceLocation, BlueprintResearchPolicy> policies(
            PlayerRecipeData data,
            Spec... specs) {
        Map<ResourceLocation, BlueprintResearchPolicy> policies = new LinkedHashMap<>();
        for (Spec spec : specs) {
            boolean learned = data.hasBlueprint(spec.id().toString());
            policies.put(spec.id(), new BlueprintResearchPolicy(
                    spec.id(),
                    PROFILE,
                    true,
                    spec.blocked(),
                    true,
                    learned,
                    data.hasDiscoveredBlueprint(spec.id().toString()),
                    data.getResearchPoints(),
                    100,
                    false,
                    true,
                    true,
                    JournalVisibility.FULL,
                    true,
                    true,
                    false,
                    1,
                    new BlueprintResearchCost(1, List.of()),
                    false,
                    spec.requirements(),
                    spec.requirements().conservativeAlternatives(),
                    true,
                    false,
                    Optional.empty(),
                    MatchSpecificity.NONE));
        }
        return Map.copyOf(policies);
    }

    private static Spec spec(ResourceLocation id, ResearchRequirements requirements) {
        return spec(id, requirements, false);
    }

    private static Spec spec(
            ResourceLocation id,
            ResearchRequirements requirements,
            boolean blocked) {
        return new Spec(id, requirements, blocked);
    }

    private static ResearchRequirements singleton(ResourceLocation prerequisite) {
        return ResearchRequirements.fromLegacy(List.of(prerequisite));
    }

    private static ResearchRequirements anyOf(ResourceLocation... alternatives) {
        return new ResearchRequirements(List.of(
                new ResearchPrerequisiteGroup(List.of(alternatives))));
    }

    private static ResearchRequirements grouped(ResearchRequirements... requirements) {
        return new ResearchRequirements(java.util.Arrays.stream(requirements)
                .flatMap(value -> value.allOf().stream())
                .toList());
    }

    private static ResearchRequirements mandatory(List<ResourceLocation> prerequisites) {
        return ResearchRequirements.fromLegacy(prerequisites);
    }

    private static PlayerRecipeData data() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(100);
        return data;
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }

    private record Spec(
            ResourceLocation id,
            ResearchRequirements requirements,
            boolean blocked) {
    }
}
