package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.ResearchFeatureConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementProposal;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintProgressionPolicy.TierSource;

import net.minecraft.resources.ResourceLocation;

/** Builds the canonical immutable research-tier, fragment, and gate publication. */
public final class BlueprintProgressionPolicyResolver {
    private BlueprintProgressionPolicyResolver() {
    }

    public static BlueprintProgressionPolicySnapshot resolve(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            long catalogRevision,
            long researchRevision,
            Map<ResourceLocation, AutomaticWeaponPlacementCandidateSnapshot> automaticByTree,
            AutomaticWorkbenchTierPercentiles percentiles) {
        return resolve(
                research,
                catalog,
                catalogRevision,
                researchRevision,
                automaticByTree,
                percentiles,
                null);
    }

    public static BlueprintProgressionPolicySnapshot resolve(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            long catalogRevision,
            long researchRevision,
            Map<ResourceLocation, AutomaticWeaponPlacementCandidateSnapshot> automaticByTree,
            ResearchFeatureConfigSnapshot config) {
        if (config == null) {
            throw new IllegalArgumentException("research feature config cannot be null");
        }
        return resolve(
                research,
                catalog,
                catalogRevision,
                researchRevision,
                automaticByTree,
                config.automaticTierPercentiles(),
                config);
    }

    private static BlueprintProgressionPolicySnapshot resolve(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            long catalogRevision,
            long researchRevision,
            Map<ResourceLocation, AutomaticWeaponPlacementCandidateSnapshot> automaticByTree,
            AutomaticWorkbenchTierPercentiles percentiles,
            ResearchFeatureConfigSnapshot config) {
        if (research == null || catalog == null || automaticByTree == null || percentiles == null
                || catalogRevision <= 0L || researchRevision <= 0L) {
            throw new IllegalArgumentException("progression policy publication inputs are invalid");
        }
        long assignmentCount = (long) research.profiles().size() * catalog.size();
        if (assignmentCount > BlueprintProgressionPolicySnapshot.MAX_TOTAL_POLICY_ASSIGNMENTS) {
            throw new IllegalArgumentException(
                    "progression policy publication requires " + assignmentCount
                            + " profile-catalog assignments; maximum is "
                            + BlueprintProgressionPolicySnapshot.MAX_TOTAL_POLICY_ASSIGNMENTS);
        }

        Map<ResourceLocation, Map<String, TieAwareWorkbenchTierAllocator.Assignment>>
                allocationsByTree = new LinkedHashMap<>();
        automaticByTree.forEach((treeId, candidates) -> {
            if (treeId == null || candidates == null
                    || !candidates.matches(treeId, catalogRevision, researchRevision)) {
                throw new IllegalArgumentException("automatic tier evidence is stale or inconsistent");
            }
            Map<String, Integer> trustedScores = new LinkedHashMap<>();
            candidates.eligibleProposals().forEach((id, proposal) -> {
                if (!proposal.reviewRequired()) {
                    trustedScores.put(id, proposal.mechanicalScore());
                }
            });
            allocationsByTree.put(
                    treeId,
                    TieAwareWorkbenchTierAllocator.allocate(trustedScores, percentiles));
        });

        Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintProgressionPolicy>> policies =
                new LinkedHashMap<>();
        Map<ResourceLocation, Map<ResourceLocation, String>> omissions = new LinkedHashMap<>();
        Map<ResourceLocation, BlueprintProgressionPolicySnapshot.ProfileDiagnostics> diagnostics =
                new LinkedHashMap<>();

        research.profiles().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(profileEntry -> {
                    ResourceLocation profileId = profileEntry.getKey();
                    BlueprintResearchProfile profile = profileEntry.getValue();
                    Optional<ResourceLocation> treeId = profile.techTree();
                    AutomaticWeaponPlacementCandidateSnapshot candidates = treeId
                            .map(automaticByTree::get)
                            .orElse(null);
                    if (research.usesAutomaticWeaponPlacement(profileId) && candidates == null) {
                        throw new IllegalStateException(
                                "automatic Research Tech Tree has no revision-valid placement publication: "
                                        + profileId);
                    }
                    Map<String, TieAwareWorkbenchTierAllocator.Assignment> allocations = treeId
                            .map(allocationsByTree::get)
                            .orElse(Map.of());
                    Map<ResourceLocation, ResolvedBlueprintProgressionPolicy> profilePolicies =
                            new LinkedHashMap<>();
                    Map<ResourceLocation, String> profileOmissions = new LinkedHashMap<>();
                    catalog.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey(
                                    java.util.Comparator.comparing(ResourceLocation::toString)))
                            .forEach(catalogEntry -> resolveOne(
                                    research,
                                    catalog,
                                    profileId,
                                    profile,
                                    catalogEntry.getKey(),
                                    catalogEntry.getValue(),
                                    candidates,
                                    allocations,
                                    config,
                                    profilePolicies,
                                    profileOmissions));
                    policies.put(profileId, Map.copyOf(profilePolicies));
                    omissions.put(profileId, Map.copyOf(profileOmissions));
                    diagnostics.put(profileId, diagnostics(profilePolicies, profileOmissions));
                });

        return new BlueprintProgressionPolicySnapshot(
                catalogRevision,
                researchRevision,
                percentiles,
                policies,
                omissions,
                diagnostics);
    }

    private static void resolveOne(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            BlueprintResearchProfile profile,
            ResourceLocation blueprintId,
            BlueprintData data,
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            Map<String, TieAwareWorkbenchTierAllocator.Assignment> allocations,
            ResearchFeatureConfigSnapshot config,
            Map<ResourceLocation, ResolvedBlueprintProgressionPolicy> policies,
            Map<ResourceLocation, String> omissions) {
        BlueprintResearchPolicyDefinition definition = BlueprintResearchPolicyResolver.definitionFor(
                research, catalog, profileId, blueprintId);
        if (!definition.treeEnabled()) {
            omissions.put(blueprintId, "tree_policy_disabled");
            return;
        }

        ResearchWorkbenchTier researchTier = profile.progression().fallbackTiers().researchTier();
        TierSource source = TierSource.FALLBACK;
        Optional<Integer> automaticScore = Optional.empty();
        Optional<Integer> automaticPercentile = Optional.empty();
        boolean reviewRequired = false;

        if (profile.techTree().isPresent()) {
            ResourceLocation treeId = profile.techTree().orElseThrow();
            ResearchTechTreePlacementResolver.EffectiveSelection placement =
                    ResearchTechTreePlacementResolver.resolveWithAutomaticForProfile(
                            research,
                            profileId,
                            treeId,
                            blueprintId,
                            data,
                            candidates);
            if (placement.automaticProposal().isPresent()) {
                AutomaticWeaponPlacementProposal proposal = placement.automaticProposal().orElseThrow();
                automaticScore = Optional.of(proposal.mechanicalScore());
                reviewRequired = proposal.reviewRequired();
                if (reviewRequired) {
                    researchTier = profile.progression().fallbackTiers().researchTier();
                    source = TierSource.REVIEW_FALLBACK;
                } else {
                    TieAwareWorkbenchTierAllocator.Assignment assignment = allocations.get(
                            blueprintId.toString());
                    if (assignment == null) {
                        throw new IllegalStateException(
                                "trusted automatic blueprint has no percentile tier: " + blueprintId);
                    }
                    researchTier = assignment.tier();
                    automaticPercentile = Optional.of(assignment.percentileBasisPoints());
                    source = TierSource.AUTOMATIC_PERCENTILE;
                }
            } else if (placement.base().placement().isPresent()) {
                researchTier = profile.progression().forAuthoredTier(
                        placement.base().placement().orElseThrow().tier()).researchTier();
                source = TierSource.AUTHORED_BAND;
            } else {
                String reason = data.getKind() == BlueprintKind.GUN && candidates != null
                        ? candidates.excludedAutomaticCandidates().getOrDefault(
                                blueprintId.toString(), "not_in_effective_tree")
                        : "not_in_effective_tree";
                omissions.put(blueprintId, reason);
                return;
            }
        }

        BlueprintResearchPolicyResolver.RuleSelection selection =
                BlueprintResearchPolicyResolver.researchProgressionRuleSelection(
                        research, profileId, blueprintId, data);
        Optional<BlueprintProgressionRuleOverride> selectedProgressionOverride =
                selection.selectedRuleId()
                        .map(research.rules()::get)
                        .flatMap(BlueprintResearchRule::progression)
                        .filter(override -> !BlueprintProgressionRuleOverride.EMPTY.equals(override));
        Optional<ResourceLocation> progressionRuleId = selectedProgressionOverride.isPresent()
                ? selection.selectedRuleId()
                : Optional.empty();
        BlueprintProgressionRuleOverride ruleOverride = selectedProgressionOverride
                .orElse(BlueprintProgressionRuleOverride.EMPTY);
        if (ruleOverride.researchTier().isPresent()) {
            researchTier = ruleOverride.researchTier().orElseThrow();
            source = selection.specificity() == MatchSpecificity.EXACT
                    ? TierSource.EXACT_RULE
                    : TierSource.AUTHORED_RULE;
        }
        ProgressionGateRequirements gates = BlueprintCraftingPolicyResolutionSupport
                .researchGatesOrElse(
                        ruleOverride.gates(), profile.progression().gates());
        var fragments = config == null
                ? profile.progression().fragments().resolve(
                        researchTier,
                        ruleOverride.fragmentThreshold())
                : config.fragmentPolicy(
                        profile.progression().fragments(),
                        blueprintId,
                        researchTier,
                        ruleOverride.fragmentThreshold());

        policies.put(blueprintId, new ResolvedBlueprintProgressionPolicy(
                profileId,
                blueprintId,
                researchTier,
                fragments,
                gates,
                source,
                progressionRuleId,
                progressionRuleId.isPresent() ? selection.specificity() : MatchSpecificity.NONE,
                automaticScore,
                automaticPercentile,
                reviewRequired,
                fragments.enabled()
                        && (ruleOverride.fragmentThreshold().isPresent()
                                || config != null
                                        && config.exactFragmentThresholds()
                                                .containsKey(blueprintId))));
    }

    private static BlueprintProgressionPolicySnapshot.ProfileDiagnostics diagnostics(
            Map<ResourceLocation, ResolvedBlueprintProgressionPolicy> policies,
            Map<ResourceLocation, String> omissions) {
        EnumMap<ResearchWorkbenchTier, Integer> researchCounts =
                new EnumMap<>(ResearchWorkbenchTier.class);
        Map<Integer, Integer> fragmentThresholds = new java.util.TreeMap<>();
        int reviews = 0;
        int gateGroups = 0;
        int gateConditions = 0;
        for (ResolvedBlueprintProgressionPolicy policy : policies.values()) {
            researchCounts.merge(policy.researchWorkbenchTier(), 1, Integer::sum);
            if (policy.reviewRequired()) {
                reviews++;
            }
            gateGroups += policy.gates().allOf().size();
            gateConditions += policy.gates().conditionCount();
            if (policy.fragments().enabled()) {
                fragmentThresholds.merge(policy.fragments().threshold(), 1, Integer::sum);
            }
        }
        return new BlueprintProgressionPolicySnapshot.ProfileDiagnostics(
                policies.size(),
                omissions.size(),
                researchCounts,
                reviews,
                gateGroups,
                gateConditions,
                fragmentThresholds);
    }
}
