package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.client.ResearchTechTreeLayout;
import com.gamergaming.taczweaponblueprints.client.ResearchTechTreeLayoutEngine;
import com.gamergaming.taczweaponblueprints.client.ResearchTechTreeLayoutPolicy;
import com.gamergaming.taczweaponblueprints.client.ResearchTechTreeProjection;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.MechanicalRating;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeEconomyAudit;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchGroupedRouteBaselineAudit;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchGroupedRouteQualityAudit;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeTopologyAudit;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponCandidateClassification;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintCatalogSelector;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardEconomyProjection;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchAutomaticPlacementProfile;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeEntryBundle;

import net.minecraft.resources.ResourceLocation;

/**
 * Phase-0 characterization harness for the pre-taper automatic tree. It runs
 * production planners and audits without granting this fixture any runtime
 * authority.
 */
final class AutomaticWeaponTopologyPhaseZeroFixture {
    static final ResourceLocation PROFILE = id("phase_zero:profile");
    static final ResourceLocation TREE = id("phase_zero:tree");
    static final ResourceLocation LANE = id("phase_zero:weapons");
    private static final ResourceLocation UNUSED_ROOT_A = id("phase_zero:unused_root_a");
    private static final ResourceLocation UNUSED_ROOT_B = id("phase_zero:unused_root_b");
    private static final long CATALOG_REVISION = 5L;
    private static final long RESEARCH_REVISION = 7L;
    private static final int POINT_COST = 8;
    private static final int FINITE_POINT_INCOME = 128;

    private AutomaticWeaponTopologyPhaseZeroFixture() {
    }

    static Scenario small() {
        return new Scenario("small", 7, 8, 2);
    }

    static Scenario medium() {
        return new Scenario("medium", 74, 9, 2);
    }

    /** Matches the packaged TaCZ weapon-population scale. */
    static Scenario packagedScale() {
        return new Scenario("packaged_scale", 53, 14, 2);
    }

    /** Matches the reported 287-node visible weapon population. */
    static Scenario largeAddon() {
        return new Scenario("large_addon", 287, 20, 2);
    }

    static Baseline baseline(Scenario scenario) {
        return baseline(scenario, false);
    }

    static Baseline baseline(Scenario scenario, boolean reverseInput) {
        AutomaticWeaponPlacementPolicy policy = policy(
                scenario.maximumNodesPerRank(), scenario.foundationCount());
        Map<String, AutomaticWeaponPlacementProposal> raw = proposals(
                scenario, reverseInput);
        Map<String, AutomaticWeaponPlacementProposal> assigned =
                new AutomaticWeaponLayerPlanner().assign(raw, policy);
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                new AutomaticWeaponPlacementCandidateSnapshot(
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        policy,
                        CATALOG_REVISION,
                        RESEARCH_REVISION,
                        scenario.weaponCount(),
                        assigned,
                        Map.of(),
                        Set.of(),
                        Set.of());
        Map<ResourceLocation, BlueprintData> catalog = catalog(scenario);
        AutomaticWeaponPrerequisitePlan prerequisites =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        research(scenario.maximumNodesPerRank()),
                        catalog,
                        PROFILE,
                        candidates);
        Published published = publish(candidates, prerequisites);
        ResearchTechTreeTopologyAudit.DomainAudit topology =
                ResearchTechTreeTopologyAudit.audit(
                        published.graph(), published.presentation())
                        .domain(Domain.WEAPONS).orElseThrow();
        ResearchTechTreeEconomyAudit.DomainEconomy economy =
                ResearchTechTreeEconomyAudit.audit(
                        published.graph(), published.presentation(), null)
                        .domain(Domain.WEAPONS).orElseThrow();
        ResearchTechTreeLayout layout = ResearchTechTreeLayoutEngine.layout(
                published.projection(), ResearchTechTreeLayoutPolicy.DEFAULT);
        List<Integer> widths = assigned.values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> value.progressionCoordinate().rank(),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()))
                .values().stream().map(Math::toIntExact).toList();
        long connectedPrerequisites = prerequisites.prerequisites().values().stream()
                .mapToLong(List::size).sum();
        return new Baseline(
                scenario.name(),
                widths,
                prerequisites.omittedCandidates().size(),
                Math.toIntExact(connectedPrerequisites),
                topology.rootIds().size(),
                topology.componentCount(),
                topology.reachableNodeCount(),
                topology.maximumPrerequisiteCount(),
                topology.maximumDependentCount(),
                topology.maximumDepth(),
                topology.mergeCount(),
                topology.crossBranchMergeCount(),
                topology.approximateEdgeCrossingCount(),
                topology.totalEdgeRankSpan(),
                economy.leafCount(),
                economy.maximumLeafSinglePathCost(),
                economy.maximumLeafUnlockClosureCost(),
                layout.graphLayout().width(),
                layout.graphLayout().height(),
                layout.diagnostics().maximumNodesInRow(),
                signature(candidates, prerequisites));
    }

    static GroupedRouteBaseline groupedRouteBaseline(Scenario scenario) {
        return groupedRouteBaseline(scenario, false);
    }

    static GroupedRouteBaseline groupedRouteBaseline(
            Scenario scenario,
            boolean reverseInput) {
        ResearchGroupedRouteBaselineAudit.Audit audit = groupedRouteAudit(
                scenario, reverseInput);
        return new GroupedRouteBaseline(
                scenario.name(),
                audit.automaticTargetCount(),
                audit.generatedReferenceCount(),
                audit.singleParentTargetCount(),
                audit.alternativeGroupCandidateCount(),
                audit.pairGroupCandidateCount(),
                audit.largerGroupCandidateCount(),
                audit.maximumAlternativeCount(),
                audit.maximumSingleParentChain(),
                audit.generatedFanOut(),
                audit.alternativeEvidence(),
                audit.routeCosts(),
                audit.inputFingerprint());
    }

    static ResearchGroupedRouteBaselineAudit.Audit groupedRouteAudit(
            Scenario scenario,
            boolean reverseInput) {
        AutomaticWeaponPlacementPolicy policy = policy(
                scenario.maximumNodesPerRank(), scenario.foundationCount());
        Map<String, AutomaticWeaponPlacementProposal> raw = proposals(
                scenario, reverseInput);
        AutomaticWeaponCandidateClassification classification =
                classification(scenario, raw, policy);
        Map<String, AutomaticWeaponPlacementProposal> assigned =
                new AutomaticWeaponBranchLayerPlanner().assign(
                        raw,
                        classification.roleSignatures(),
                        classification.authoredRoleSignatures(),
                        classification.branchModel(),
                        policy);
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                new AutomaticWeaponPlacementCandidateSnapshot(
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        policy,
                        CATALOG_REVISION,
                        RESEARCH_REVISION,
                        scenario.weaponCount(),
                        assigned,
                        Map.of(),
                        Set.of(),
                        Set.of());
        AutomaticWeaponPrerequisitePlan prerequisites =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        research(scenario.maximumNodesPerRank()),
                        catalog(scenario),
                        PROFILE,
                        candidates,
                        classification);
        Published published = publish(candidates, prerequisites);
        AutomaticWeaponPlacementDiagnostics diagnostics =
                AutomaticWeaponPlacementDiagnostics.create(
                        PROFILE, candidates, prerequisites);
        return ResearchGroupedRouteBaselineAudit.audit(
                published.graph(),
                published.presentation(),
                diagnostics,
                new ResearchPointAwardEconomyProjection.Projection(
                        1,
                        0,
                        FINITE_POINT_INCOME,
                        Map.of(
                                ResearchPointAwardTrigger.Type.INTEGRATION,
                                FINITE_POINT_INCOME)));
    }

    static ResearchGroupedRouteQualityAudit.Audit groupedRouteQualityAudit(
            Scenario scenario,
            boolean reverseInput) {
        return groupedRouteEvidence(scenario, reverseInput).quality();
    }

    static GroupedRouteEvidence groupedRouteEvidence(
            Scenario scenario,
            boolean reverseInput) {
        AutomaticWeaponPlacementPolicy policy = policy(
                scenario.maximumNodesPerRank(),
                scenario.foundationCount(),
                AutomaticWeaponPlacementPolicy.PrerequisiteStrategy.GROUPED_ROUTES_V1);
        Map<String, AutomaticWeaponPlacementProposal> raw = proposals(
                scenario, reverseInput);
        AutomaticWeaponCandidateClassification classification =
                classification(scenario, raw, policy);
        Map<String, AutomaticWeaponPlacementProposal> assigned =
                new AutomaticWeaponBranchLayerPlanner().assign(
                        raw,
                        classification.roleSignatures(),
                        classification.authoredRoleSignatures(),
                        classification.branchModel(),
                        policy);
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                new AutomaticWeaponPlacementCandidateSnapshot(
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        policy,
                        CATALOG_REVISION,
                        RESEARCH_REVISION,
                        scenario.weaponCount(),
                        assigned,
                        Map.of(),
                        Set.of(),
                        Set.of());
        AutomaticWeaponPrerequisitePlan prerequisites =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        research(scenario.maximumNodesPerRank()),
                        catalog(scenario),
                        PROFILE,
                        candidates,
                        classification);
        Published published = publish(candidates, prerequisites);
        AutomaticWeaponPlacementDiagnostics diagnostics =
                AutomaticWeaponPlacementDiagnostics.create(
                        PROFILE, candidates, prerequisites);
        ResearchGroupedRouteQualityAudit.Audit quality =
                ResearchGroupedRouteQualityAudit.audit(
                published.graph(),
                published.presentation(),
                diagnostics,
                new ResearchPointAwardEconomyProjection.Projection(
                        1,
                        0,
                        FINITE_POINT_INCOME,
                        Map.of(
                                ResearchPointAwardTrigger.Type.INTEGRATION,
                                FINITE_POINT_INCOME)));
        ResearchTechTreeTopologyAudit.Audit topology =
                ResearchTechTreeTopologyAudit.audit(
                        published.graph(), published.presentation(), diagnostics);
        return new GroupedRouteEvidence(quality, topology);
    }

    /**
     * Builds the same branch evidence consumed by runtime prerequisite planning.
     * The six deterministic role families keep the scale fixture representative
     * without tying its baseline to a particular external weapon pack.
     */
    private static AutomaticWeaponCandidateClassification classification(
            Scenario scenario,
            Map<String, AutomaticWeaponPlacementProposal> proposals,
            AutomaticWeaponPlacementPolicy policy) {
        Map<String, WeaponMechanicalScore> scores = new LinkedHashMap<>();
        Map<String, String> archetypes = new LinkedHashMap<>();
        proposals.values().stream()
                .sorted(Comparator.comparing(AutomaticWeaponPlacementProposal::blueprintId))
                .forEach(proposal -> {
                    int index = Integer.parseInt(proposal.blueprintId().substring(
                            proposal.blueprintId().lastIndexOf('_') + 1));
                    SyntheticRole role = SyntheticRole.values()[
                            index % SyntheticRole.values().length];
                    scores.put(proposal.blueprintId(), score(
                            proposal.blueprintId(),
                            role.archetype,
                            proposal.mechanicalScore(),
                            proposal.mechanicalScore(),
                            proposal.confidence(),
                            metricScores(
                                    role.damage,
                                    role.range,
                                    role.magazine,
                                    role.handling)));
                    archetypes.put(proposal.blueprintId(), role.archetype);
                });
        Map<String, AutomaticWeaponRoleSignature> signatures =
                new AutomaticWeaponRoleAnalyzer().analyze(
                        proposals, scores, archetypes);
        int branchLimit = AutomaticWeaponBranchAnalyzer.branchLimitForLayerWidth(
                policy.maxNodesPerRank());
        AutomaticWeaponBranchModel branchModel =
                new AutomaticWeaponBranchAnalyzer().discover(
                        signatures, Map.of(), branchLimit);
        return new AutomaticWeaponCandidateClassification(
                TREE,
                AutomaticPlacementMode.CONNECTED,
                policy,
                CATALOG_REVISION,
                RESEARCH_REVISION,
                scenario.weaponCount(),
                proposals,
                signatures,
                Map.of(),
                branchModel,
                Map.of(),
                Set.of(),
                Set.of());
    }

    static LayerBaseline maximumLayerBaseline(boolean reverseInput) {
        Scenario maximum = new Scenario(
                "maximum", ResearchTreeGraph.MAX_NODES, 20, 2);
        AutomaticWeaponPlacementPolicy policy = policy(20, 2);
        Map<String, AutomaticWeaponPlacementProposal> assigned =
                new AutomaticWeaponLayerPlanner().assign(
                        proposals(maximum, reverseInput), policy);
        Map<Integer, Long> widths = assigned.values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> value.progressionCoordinate().rank(),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));
        String canonical = assigned.values().stream()
                .sorted(coordinateOrder())
                .map(value -> value.blueprintId() + "@"
                        + value.progressionCoordinate().rank() + ":"
                        + value.progressionCoordinate().siblingOrder())
                .collect(java.util.stream.Collectors.joining(";"));
        return new LayerBaseline(
                assigned.size(),
                widths.size(),
                Math.toIntExact(widths.values().stream().mapToLong(Long::longValue)
                        .max().orElseThrow()),
                Math.toIntExact(widths.values().stream().mapToLong(Long::longValue)
                        .reduce((first, second) -> second).orElseThrow()),
                sha256(canonical));
    }

    /** Stable mechanical cases reserved for role-signature work in Phase 2. */
    static Map<String, List<WeaponMechanicalScore>> mechanicalCases() {
        LinkedHashMap<String, List<WeaponMechanicalScore>> cases = new LinkedHashMap<>();
        cases.put("equal_power_different_roles", List.of(
                score("phase_zero:equal_close", "smg", 60, 60, 100,
                        metricScores(88, 24, 84, 72)),
                score("phase_zero:equal_range", "sniper", 60, 60, 100,
                        metricScores(42, 92, 28, 46))));
        cases.put("same_role_different_power", List.of(
                score("phase_zero:role_weak", "rifle", 28, 32, 100,
                        metricScores(32, 36, 40, 44)),
                score("phase_zero:role_strong", "rifle", 78, 72, 100,
                        metricScores(82, 86, 90, 94))));
        cases.put("terminal_ties", java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> score(
                        "phase_zero:tied_" + index,
                        "rifle",
                        82,
                        74,
                        100,
                        metricScores(84, 86, 78, 80)))
                .toList());
        cases.put("low_confidence", List.of(
                score("phase_zero:incomplete", "special", 55, 45, 25,
                        metricScores(50, 50, 50, 50),
                        List.of("missing_metric:effective_range")),
                score("phase_zero:scripted", "special", 70, 50,
                        WeaponMechanicalScorer.SCRIPT_CONFIDENCE_CAP,
                        metricScores(68, 62, 48, 40),
                        List.of("script_controlled"), true)));
        cases.put("skewed_roles", java.util.stream.IntStream.range(0, 37)
                .mapToObj(index -> {
                    if (index < 30) {
                        return score("phase_zero:skewed_rifle_" + index, "rifle",
                                45 + index % 20, 50, 100,
                                metricScores(60, 62, 58, 55));
                    }
                    if (index < 35) {
                        return score("phase_zero:skewed_sniper_" + index, "sniper",
                                65 + index % 10, 40, 100,
                                metricScores(40, 92, 30, 38));
                    }
                    return score("phase_zero:skewed_launcher_" + index, "launcher",
                            72, 25, 100,
                            metricScores(92, 35, 10, 20));
                })
                .toList());
        return Collections.unmodifiableMap(cases);
    }

    static Map<String, AutomaticWeaponRoleSignature> roleSignatures(
            String caseName,
            boolean reverseInput) {
        List<WeaponMechanicalScore> scores = new ArrayList<>(
                mechanicalCases().getOrDefault(caseName, List.of()));
        if (reverseInput) {
            Collections.reverse(scores);
        }
        Map<String, AutomaticWeaponPlacementProposal> proposals = new LinkedHashMap<>();
        Map<String, WeaponMechanicalScore> scoreMap = new LinkedHashMap<>();
        Map<String, String> archetypes = new LinkedHashMap<>();
        for (WeaponMechanicalScore score : scores) {
            String blueprintId = score.evidence().blueprintId();
            List<String> reviewReasons = new ArrayList<>();
            if (score.rating().confidence()
                    < AutomaticWeaponRoleAnalyzer.MIN_BRANCH_SEED_CONFIDENCE) {
                reviewReasons.add("low_confidence");
            }
            if (score.evidence().scriptControlled()) {
                reviewReasons.add("script_controlled");
            }
            proposals.put(blueprintId, new AutomaticWeaponPlacementProposal(
                    blueprintId,
                    score.score(),
                    score.rating().confidence(),
                    new ProgressionPosition(
                            Tier.forScore(score.score()),
                            ResearchTechTreeContract.levelForScore(score.score(), 3),
                            Integer.toUnsignedLong(blueprintId.hashCode())),
                    3,
                    ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                    ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                    ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                    reviewReasons));
            scoreMap.put(blueprintId, score);
            archetypes.put(blueprintId, score.evidence().archetype());
        }
        return new AutomaticWeaponRoleAnalyzer().analyze(
                proposals, scoreMap, archetypes);
    }

    private static AutomaticWeaponPlacementPolicy policy(
            int width,
            int foundationCount) {
        return policy(
                width,
                foundationCount,
                AutomaticWeaponPlacementPolicy.PrerequisiteStrategy.LEGACY_AND);
    }

    private static AutomaticWeaponPlacementPolicy policy(
            int width,
            int foundationCount,
            AutomaticWeaponPlacementPolicy.PrerequisiteStrategy strategy) {
        return new AutomaticWeaponPlacementPolicy(
                3,
                0,
                AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                2,
                4,
                AutomaticWeaponPlacementPolicy.LayeringStrategy.DYNAMIC_STAT_LAYERS,
                width,
                List.of(),
                foundationCount,
                strategy);
    }

    private static Map<String, AutomaticWeaponPlacementProposal> proposals(
            Scenario scenario,
            boolean reverseInput) {
        List<Integer> indexes = java.util.stream.IntStream.range(0, scenario.weaponCount())
                .boxed().collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (reverseInput) {
            Collections.reverse(indexes);
        }
        Map<String, AutomaticWeaponPlacementProposal> result = new LinkedHashMap<>();
        for (int index : indexes) {
            int score = index * ResearchTechTreeContract.SCORE_MAX
                    / Math.max(1, scenario.weaponCount() - 1);
            String blueprintId = "phase_zero:" + scenario.name() + "_" + index;
            long siblingOrder = Math.addExact(
                    Math.multiplyExact(score, 1L << 56), index);
            result.put(blueprintId, new AutomaticWeaponPlacementProposal(
                    blueprintId,
                    score,
                    100,
                    new ProgressionPosition(
                            Tier.forScore(score),
                            ResearchTechTreeContract.levelForScore(score, 3),
                            siblingOrder),
                    3,
                    ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                    ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                    ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                    List.of()));
        }
        return result;
    }

    private static Map<ResourceLocation, BlueprintData> catalog(Scenario scenario) {
        Map<ResourceLocation, BlueprintData> result = new LinkedHashMap<>();
        for (int index = 0; index < scenario.weaponCount(); index++) {
            ResourceLocation blueprintId = id(
                    "phase_zero:" + scenario.name() + "_" + index);
            result.put(blueprintId, new BlueprintData(
                    blueprintId.toString(),
                    "name." + blueprintId.getPath(),
                    "tooltip." + blueprintId.getPath(),
                    id("phase_zero:recipe/" + scenario.name() + "_" + index),
                    null,
                    "rifle",
                    id("phase_zero:slot/" + scenario.name() + "_" + index),
                    BlueprintKind.GUN));
        }
        return Map.copyOf(result);
    }

    private static BlueprintResearchSnapshot research(int width) {
        ResearchTechTreeEntryBundle entries = new ResearchTechTreeEntryBundle(
                1,
                TREE,
                0,
                List.of(
                        exactEntry(UNUSED_ROOT_A, 10),
                        exactEntry(UNUSED_ROOT_B, 20),
                        new ResearchTechTreeEntryBundle.Entry(
                                new BlueprintResearchTarget(
                                        List.of(),
                                        List.of(),
                                        Optional.of(new BlueprintCatalogSelector(
                                                List.of("phase_zero"),
                                                List.of(),
                                                List.of(),
                                                List.of(),
                                                List.of(BlueprintKind.GUN),
                                                1.0F))),
                                Domain.WEAPONS,
                                LANE,
                                Tier.BASIC,
                                900_000,
                                Optional.empty(),
                                Optional.empty(),
                                true)));
        ResearchAutomaticPlacementProfile automatic =
                new ResearchAutomaticPlacementProfile(
                        2,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        3,
                        0,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                        2,
                        4,
                        width,
                        List.of());
        return BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile()),
                Map.of(),
                Map.of(),
                Map.of(TREE, tree()),
                Map.of(id("phase_zero:entries"), entries),
                Map.of(id("phase_zero:automatic"), automatic));
    }

    private static ResearchTechTreeEntryBundle.Entry exactEntry(
            ResourceLocation blueprintId,
            int order) {
        return new ResearchTechTreeEntryBundle.Entry(
                new BlueprintResearchTarget(
                        List.of(blueprintId), List.of(), Optional.empty()),
                Domain.WEAPONS,
                LANE,
                Tier.STARTER,
                order,
                Optional.empty(),
                Optional.empty());
    }

    private static BlueprintResearchProfile profile() {
        return new BlueprintResearchProfile(
                1,
                true,
                JournalVisibility.FULL,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(POINT_COST, List.of()),
                false,
                false,
                true,
                List.of(),
                Optional.of(TREE));
    }

    private static ResearchTechTreeDefinition tree() {
        return new ResearchTechTreeDefinition(
                ResearchTechTreeDefinition.CURRENT_FORMAT,
                "Phase zero",
                Optional.empty(),
                Optional.empty(),
                ResearchTechTreeDefinition.WeaponPlacementMode.AUTOMATIC,
                new ResearchTechTreeDefinition.LayoutDefinition(20),
                ResearchTechTreeDefinition.BandPolicyDefinition.NONE,
                Arrays.stream(Tier.values())
                        .map(tier -> new ResearchTechTreeDefinition.TierDefinition(
                                tier, tier.name(), Optional.empty()))
                        .toList(),
                List.of(new ResearchTechTreeDefinition.DomainDefinition(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.empty(),
                        Optional.empty(),
                        LANE,
                        Tier.STARTER,
                        List.of(new ResearchTechTreeDefinition.LaneDefinition(
                                LANE,
                                "Weapons",
                                Optional.empty(),
                                Optional.empty(),
                                0)))));
    }

    private static Published publish(
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            AutomaticWeaponPrerequisitePlan prerequisites) {
        List<AutomaticWeaponPlacementProposal> ordered =
                candidates.eligibleProposals().values().stream()
                        .sorted(coordinateOrder()).toList();
        Map<ResourceLocation, Integer> prerequisiteCounts = new LinkedHashMap<>();
        prerequisites.prerequisites().forEach((dependent, values) ->
                prerequisiteCounts.put(dependent, values.size()));
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>();
        List<ResearchTechTreePresentation.Member> members = new ArrayList<>();
        Map<ResourceLocation, ResearchTechTreeProjection.Placement> placements =
                new LinkedHashMap<>();
        for (int ordinal = 0; ordinal < ordered.size(); ordinal++) {
            AutomaticWeaponPlacementProposal proposal = ordered.get(ordinal);
            ResourceLocation blueprintId = id(proposal.blueprintId());
            int prerequisiteCount = prerequisiteCounts.getOrDefault(blueprintId, 0);
            nodes.add(new ResearchTreeGraph.Node(
                    ordinal,
                    blueprintId,
                    "name." + blueprintId.getPath(),
                    "rifle",
                    id("phase_zero:slot/" + blueprintId.getPath()),
                    JournalVisibility.FULL,
                    false,
                    true,
                    false,
                    POINT_COST,
                    0,
                    prerequisiteCount,
                    0,
                    ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED));
            var coordinate = proposal.progressionCoordinate();
            members.add(new ResearchTechTreePresentation.Member(
                    blueprintId,
                    coordinate.rank(),
                    coordinate.siblingOrder(),
                    coordinate.bandId(),
                    PlacementOrigin.AUTOMATIC,
                    Optional.empty()));
            placements.put(blueprintId, new ResearchTechTreeProjection.Placement(
                    blueprintId,
                    LANE,
                    coordinate.rank(),
                    0,
                    coordinate.siblingOrder(),
                    coordinate.bandId(),
                    PlacementOrigin.AUTOMATIC,
                    Optional.empty()));
        }
        List<ResearchTreeGraph.RequirementGroup> requirementGroups = new ArrayList<>();
        prerequisites.requirementGroups().forEach((dependent, requirements) -> {
            for (int ordinal = 0; ordinal < requirements.allOf().size(); ordinal++) {
                requirementGroups.add(new ResearchTreeGraph.RequirementGroup(
                        dependent,
                        ordinal,
                        requirements.allOf().get(ordinal).anyOf(),
                        0,
                        false));
            }
        });
        ResearchTreeGraph graph = ResearchTreeGraph.withRequirementGroups(
                nodes, requirementGroups);
        ResearchTechTreePresentation.DomainView domain =
                new ResearchTechTreePresentation.DomainView(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.empty(),
                        Optional.of(nodes.get(0).blueprintId()),
                        List.of(new ResearchTechTreePresentation.LaneView(
                                LANE,
                                "Weapons",
                                Optional.empty(),
                                Optional.of(nodes.get(0).blueprintId()),
                                0,
                                members)));
        ResearchTechTreePresentation presentation =
                new ResearchTechTreePresentation(
                        Optional.of(TREE),
                        "Phase zero",
                        Optional.empty(),
                        Optional.of(nodes.get(0).blueprintId()),
                        List.of(),
                        List.of(),
                        candidates.policy().maxNodesPerRank(),
                        List.of(domain));
        ResearchTechTreeProjection projection = new ResearchTechTreeProjection(
                Domain.WEAPONS,
                domain,
                graph,
                placements,
                List.of(),
                List.of(),
                candidates.policy().maxNodesPerRank());
        return new Published(graph, presentation, projection);
    }

    private static Comparator<AutomaticWeaponPlacementProposal> coordinateOrder() {
        return Comparator
                .comparingInt((AutomaticWeaponPlacementProposal value) ->
                        value.progressionCoordinate().rank())
                .thenComparingLong(value ->
                        value.progressionCoordinate().siblingOrder())
                .thenComparing(AutomaticWeaponPlacementProposal::blueprintId);
    }

    private static String signature(
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            AutomaticWeaponPrerequisitePlan prerequisites) {
        String canonical = candidates.eligibleProposals().values().stream()
                .sorted(coordinateOrder())
                .map(value -> {
                    ResourceLocation blueprintId = id(value.blueprintId());
                    return value.blueprintId() + "@"
                            + value.progressionCoordinate().rank() + ":"
                            + value.progressionCoordinate().siblingOrder() + "<-"
                            + prerequisites.prerequisitesFor(blueprintId).stream()
                                    .map(ResourceLocation::toString)
                                    .collect(java.util.stream.Collectors.joining(","));
                })
                .collect(java.util.stream.Collectors.joining(";"));
        return sha256(canonical);
    }

    private static WeaponMechanicalScore score(
            String blueprintId,
            String archetype,
            int combat,
            int utility,
            int confidence,
            Map<String, Integer> metricScores) {
        return score(
                blueprintId, archetype, combat, utility, confidence,
                metricScores, List.of(), false);
    }

    private static WeaponMechanicalScore score(
            String blueprintId,
            String archetype,
            int combat,
            int utility,
            int confidence,
            Map<String, Integer> metricScores,
            List<String> warnings) {
        return score(
                blueprintId, archetype, combat, utility, confidence,
                metricScores, warnings, false);
    }

    private static WeaponMechanicalScore score(
            String blueprintId,
            String archetype,
            int combat,
            int utility,
            int confidence,
            Map<String, Integer> metricScores,
            List<String> warnings,
            boolean scriptControlled) {
        WeaponStatEvidence evidence = new WeaponStatEvidence(
                blueprintId,
                archetype,
                8.0,
                0.0,
                600.0,
                20,
                2.0,
                100.0,
                50.0,
                0.0,
                1.0,
                0,
                0.3,
                0.4,
                3.0,
                0.2,
                0.5,
                -0.2,
                2,
                3,
                null,
                "magazine",
                false,
                scriptControlled,
                warnings);
        return new WeaponMechanicalScore(
                evidence,
                MechanicalRating.current(combat, utility, confidence),
                Map.of(),
                Map.of(),
                metricScores,
                warnings);
    }

    private static Map<String, Integer> metricScores(
            int damage,
            int range,
            int magazine,
            int handling) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            int value = switch (metric) {
                case SUSTAINED_DPS, EFFECTIVE_DAMAGE, HEADSHOT_MULTIPLIER,
                        ARMOR_EFFECTIVENESS -> damage;
                case EFFECTIVE_RANGE, PROJECTILE_SPEED -> range;
                case MAGAZINE_CAPACITY, FIRE_MODE_COUNT, ATTACHMENT_TYPE_COUNT -> magazine;
                case AIMED_INACCURACY, RECOIL_MAGNITUDE, RELOAD_SECONDS, AIM_TIME,
                        DRAW_TIME, WEIGHT, AIM_MOVEMENT -> handling;
            };
            result.put(metric.serializedName(), value);
        }
        return Map.copyOf(result);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte element : digest) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", element & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }

    private enum SyntheticRole {
        RIFLE("rifle", 82, 58, 66, 62),
        SMG("smg", 64, 24, 86, 82),
        SNIPER("sniper", 76, 96, 18, 34),
        SHOTGUN("shotgun", 92, 28, 32, 46),
        LMG("lmg", 80, 54, 96, 22),
        LAUNCHER("launcher", 100, 42, 8, 14);

        private final String archetype;
        private final int damage;
        private final int range;
        private final int magazine;
        private final int handling;

        SyntheticRole(
                String archetype,
                int damage,
                int range,
                int magazine,
                int handling) {
            this.archetype = archetype;
            this.damage = damage;
            this.range = range;
            this.magazine = magazine;
            this.handling = handling;
        }
    }

    record Scenario(
            String name,
            int weaponCount,
            int maximumNodesPerRank,
            int foundationCount) {
        Scenario {
            if (name == null || name.isBlank()
                    || weaponCount < 1 || weaponCount > ResearchTreeGraph.MAX_NODES
                    || maximumNodesPerRank < 1
                    || foundationCount < 1 || foundationCount > weaponCount) {
                throw new IllegalArgumentException("Invalid Phase-0 topology scenario");
            }
        }
    }

    record Baseline(
            String scenario,
            List<Integer> rankWidths,
            int omittedCandidateCount,
            int edgeCount,
            int rootCount,
            int componentCount,
            int reachableNodeCount,
            int maximumPrerequisiteCount,
            int maximumDependentCount,
            int maximumDepth,
            int mergeCount,
            int crossBranchMergeCount,
            long approximateCrossingCount,
            long totalEdgeRankSpan,
            int leafCount,
            long maximumLeafSinglePathCost,
            long maximumLeafUnlockClosureCost,
            int layoutWidth,
            int layoutHeight,
            int maximumVisualRowPopulation,
            String topologySignature) {
        Baseline {
            rankWidths = List.copyOf(rankWidths);
        }
    }

    record LayerBaseline(
            int nodeCount,
            int occupiedRankCount,
            int maximumRankWidth,
            int finalRankWidth,
            String placementSignature) {
    }

    record GroupedRouteBaseline(
            String scenario,
            int automaticTargetCount,
            int generatedReferenceCount,
            int singleParentTargetCount,
            int alternativeGroupCandidateCount,
            int pairGroupCandidateCount,
            int largerGroupCandidateCount,
            int maximumAlternativeCount,
            int maximumSingleParentChain,
            ResearchGroupedRouteBaselineAudit.IntDistribution generatedFanOut,
            ResearchGroupedRouteBaselineAudit.AlternativeEvidence alternativeEvidence,
            ResearchGroupedRouteBaselineAudit.RouteCostComparison routeCosts,
            String inputFingerprint) {
    }

    record GroupedRouteEvidence(
            ResearchGroupedRouteQualityAudit.Audit quality,
            ResearchTechTreeTopologyAudit.Audit topology) {
    }

    private record Published(
            ResearchTreeGraph graph,
            ResearchTechTreePresentation presentation,
            ResearchTechTreeProjection projection) {
    }
}
