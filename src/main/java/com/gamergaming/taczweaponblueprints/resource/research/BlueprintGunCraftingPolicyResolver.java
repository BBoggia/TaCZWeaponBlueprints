package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPlanner;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementProposal;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;

import net.minecraft.resources.ResourceLocation;

/**
 * Resolves the complete gun projection of the independent crafting policy.
 * Ammo and attachments deliberately remain outside this Phase 3 resolver.
 */
public final class BlueprintGunCraftingPolicyResolver {
    private BlueprintGunCraftingPolicyResolver() {
    }

    public static Resolution resolve(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            long catalogRevision,
            long researchRevision,
            long automaticRevision,
            Map<ResourceLocation, AutomaticWeaponPlacementCandidateSnapshot> automaticByTree,
            AutomaticWeaponEvidenceSnapshot evidence,
            AutomaticWorkbenchTierPercentiles percentiles) {
        if (research == null || catalog == null || automaticByTree == null
                || evidence == null || percentiles == null
                || catalogRevision <= 0L || researchRevision <= 0L
                || automaticRevision < 0L
                || !evidence.matchesCatalogRevision(catalogRevision)) {
            throw new IllegalArgumentException(
                    "gun crafting policy publication inputs are invalid or stale");
        }

        Map<ResourceLocation, BlueprintData> guns = sortedGuns(catalog);
        long assignmentCount = (long) research.profiles().size() * guns.size();
        if (assignmentCount > BlueprintCraftingPolicySnapshot.MAX_TOTAL_POLICY_ASSIGNMENTS) {
            throw new IllegalArgumentException(
                    "gun crafting policy requires " + assignmentCount
                            + " profile-gun assignments; maximum is "
                            + BlueprintCraftingPolicySnapshot.MAX_TOTAL_POLICY_ASSIGNMENTS);
        }

        validateCandidateSnapshots(
                automaticByTree,
                catalogRevision,
                researchRevision,
                guns.keySet());
        Map<ResourceLocation, Map<String, TieAwareWorkbenchTierAllocator.Assignment>>
                automaticAllocations = automaticAllocations(automaticByTree, percentiles);
        EvidenceAutomaticAssignments evidenceAssignments =
                evidenceAutomaticAssignments(guns.keySet(), evidence, percentiles);

        Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>> policies =
                new LinkedHashMap<>();
        research.profiles().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(profileEntry -> {
                    ResourceLocation profileId = profileEntry.getKey();
                    BlueprintResearchProfile profile = profileEntry.getValue();
                    boolean automaticAuthority = research.usesAutomaticWeaponPlacement(profileId);
                    AutomaticWeaponPlacementCandidateSnapshot candidates = profile.techTree()
                            .map(automaticByTree::get)
                            .orElse(null);
                    if (automaticAuthority && candidates == null) {
                        throw new IllegalStateException(
                                "automatic Research Tech Tree has no revision-valid gun candidates: "
                                        + profileId);
                    }
                    Map<String, TieAwareWorkbenchTierAllocator.Assignment> profileAllocations =
                            profile.techTree().map(automaticAllocations::get).orElse(Map.of());
                    Map<ResourceLocation, BlueprintResearchPolicyResolver.RuleSelection>
                            craftingRules = BlueprintResearchPolicyResolver.craftingRuleSelections(
                                    research, profileId, guns);
                    Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> profilePolicies =
                            new LinkedHashMap<>();
                    guns.forEach((blueprintId, data) -> profilePolicies.put(
                            blueprintId,
                            resolveOne(
                                    research,
                                    catalog,
                                    profileId,
                                    profile,
                                    blueprintId,
                                    data,
                                    automaticAuthority,
                                    candidates,
                                    profileAllocations,
                                    evidenceAssignments,
                                    craftingRules.get(blueprintId))));
                    policies.put(profileId, Collections.unmodifiableMap(profilePolicies));
                });

        return new Resolution(
                catalogRevision,
                researchRevision,
                automaticRevision,
                guns.keySet(),
                policies);
    }

    private static ResolvedBlueprintCraftingPolicy resolveOne(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            BlueprintResearchProfile profile,
            ResourceLocation blueprintId,
            BlueprintData data,
            boolean automaticAuthority,
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            Map<String, TieAwareWorkbenchTierAllocator.Assignment> automaticAllocations,
            EvidenceAutomaticAssignments evidenceAssignments,
            BlueprintResearchPolicyResolver.RuleSelection craftingRule) {
        if (craftingRule == null) {
            throw new IllegalStateException(
                    "gun crafting rule selection is missing for " + blueprintId);
        }
        BlueprintResearchPolicyDefinition researchDefinition =
                BlueprintResearchPolicyResolver.definitionFor(
                        research, catalog, profileId, blueprintId);
        BaseAssignment base;

        if (profile.techTree().isEmpty()) {
            base = profile.format() < BlueprintResearchProfile.CRAFTING_FORMAT
                    ? legacyWithoutTree(profile, researchDefinition)
                    : fromAccess(
                            profile.crafting().other(),
                            BlueprintCraftingPolicyResolutionSupport.craftingGates(
                                    profile.progression().gates()),
                            BlueprintCraftingPolicySource.CATEGORY_DEFAULT,
                            "gun_without_tree_default",
                            Set.of());
        } else if (automaticAuthority) {
            base = profile.format() < BlueprintResearchProfile.CRAFTING_FORMAT
                    ? legacyAutomatic(
                            profile,
                            researchDefinition,
                            blueprintId,
                            candidates,
                            automaticAllocations)
                    : automatic(
                            profile,
                            blueprintId,
                            candidates,
                            automaticAllocations);
        } else {
            ResourceLocation treeId = profile.techTree().orElseThrow();
            ResearchTechTreePlacementResolver.EffectiveSelection placement =
                    ResearchTechTreePlacementResolver.resolveWithAutomaticForProfile(
                            research,
                            profileId,
                            treeId,
                            blueprintId,
                            data,
                            null);
            Optional<ResearchTechTreePlacementResolver.Placement> authoredPlacement =
                    placement.base().placement()
                            .filter(value -> value.origin().authored());
            boolean included = researchDefinition.treeEnabled()
                    && authoredPlacement.isPresent();
            base = profile.format() < BlueprintResearchProfile.CRAFTING_FORMAT
                    ? legacyAuthored(profile, included, authoredPlacement)
                    : authored(
                            profile,
                            included,
                            authoredPlacement,
                            blueprintId,
                            evidenceAssignments);
        }

        return applyCraftingRule(
                research,
                profileId,
                blueprintId,
                craftingRule,
                base);
    }

    private static BaseAssignment automatic(
            BlueprintResearchProfile profile,
            ResourceLocation blueprintId,
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            Map<String, TieAwareWorkbenchTierAllocator.Assignment> allocations) {
        BlueprintCraftingStrategy strategy = profile.crafting().automaticGuns();
        ProgressionGateRequirements gates = BlueprintCraftingPolicyResolutionSupport
                .craftingGates(profile.progression().gates());
        if (strategy.mode() != BlueprintCraftingStrategy.Mode.AUTOMATIC_TIER) {
            return fromStrategy(
                    strategy,
                    gates,
                    BlueprintCraftingPolicySource.CATEGORY_DEFAULT,
                    "automatic_gun_category_default",
                    Set.of());
        }

        Optional<AutomaticWeaponPlacementProposal> proposal =
                candidates.eligibleProposal(blueprintId);
        if (proposal.isEmpty()) {
            return fromAccess(
                    strategy.fallback().orElseThrow(),
                    gates,
                    BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                    "automatic_gun_missing_evidence_fallback",
                    Set.of());
        }
        AutomaticWeaponPlacementProposal value = proposal.orElseThrow();
        if (value.reviewRequired()) {
            return fromAccess(
                    strategy.fallback().orElseThrow(),
                    gates,
                    BlueprintCraftingPolicySource.AUTOMATIC_REVIEW_FALLBACK,
                    "automatic_review_fallback",
                    Set.of(BlueprintCraftingPolicyWarning.AUTOMATIC_REVIEW_FALLBACK),
                    Optional.of(value.mechanicalScore()),
                    Optional.empty(),
                    true);
        }
        TieAwareWorkbenchTierAllocator.Assignment assignment =
                allocations.get(blueprintId.toString());
        if (assignment == null) {
            throw new IllegalStateException(
                    "trusted automatic gun has no percentile crafting tier: " + blueprintId);
        }
        return automaticPercentile(assignment, gates, "automatic_percentile");
    }

    private static BaseAssignment authored(
            BlueprintResearchProfile profile,
            boolean included,
            Optional<ResearchTechTreePlacementResolver.Placement> placement,
            ResourceLocation blueprintId,
            EvidenceAutomaticAssignments evidenceAssignments) {
        ProgressionGateRequirements gates = BlueprintCraftingPolicyResolutionSupport
                .craftingGates(profile.progression().gates());
        if (included) {
            var authored = profile.crafting().authoredGuns();
            var visualTier = placement.orElseThrow().tier();
            BlueprintCraftingAccessPolicy access = authored.forTier(visualTier);
            boolean mapped = authored.tierBands().containsKey(visualTier);
            return fromAccess(
                    access,
                    gates,
                    mapped
                            ? BlueprintCraftingPolicySource.AUTHORED_BAND
                            : BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                    mapped ? "authored_tier_band" : "authored_tier_fallback",
                    Set.of());
        }

        BlueprintCraftingStrategy omitted = profile.crafting().authoredOmittedGuns();
        if (omitted.mode() == BlueprintCraftingStrategy.Mode.AUTOMATIC_TIER) {
            AutomaticWeaponPlacementProposal proposal =
                    evidenceAssignments.proposals().get(blueprintId.toString());
            if (proposal == null) {
                return fromAccess(
                        omitted.fallback().orElseThrow(),
                        gates,
                        BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                        "authored_omitted_evidence_fallback",
                        Set.of(BlueprintCraftingPolicyWarning.AUTHORED_OMITTED_FALLBACK));
            }
            if (proposal.reviewRequired()) {
                return fromAccess(
                        omitted.fallback().orElseThrow(),
                        gates,
                        BlueprintCraftingPolicySource.AUTOMATIC_REVIEW_FALLBACK,
                        "authored_omitted_automatic_review_fallback",
                        Set.of(BlueprintCraftingPolicyWarning.AUTOMATIC_REVIEW_FALLBACK),
                        Optional.of(proposal.mechanicalScore()),
                        Optional.empty(),
                        true);
            }
            TieAwareWorkbenchTierAllocator.Assignment assignment =
                    evidenceAssignments.allocations().get(blueprintId.toString());
            if (assignment != null) {
                return automaticPercentile(
                        assignment,
                        gates,
                        "authored_omitted_automatic_percentile");
            }
            return fromAccess(
                    omitted.fallback().orElseThrow(),
                    gates,
                    BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                    "authored_omitted_evidence_fallback",
                    Set.of(BlueprintCraftingPolicyWarning.AUTHORED_OMITTED_FALLBACK));
        }
        return fromStrategy(
                omitted,
                gates,
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                "authored_omitted_profile_policy",
                Set.of(BlueprintCraftingPolicyWarning.AUTHORED_OMITTED_FALLBACK));
    }

    private static BaseAssignment legacyWithoutTree(
            BlueprintResearchProfile profile,
            BlueprintResearchPolicyDefinition definition) {
        if (!definition.treeEnabled()) {
            return migratedCompatibility();
        }
        return tiered(
                profile.progression().fallbackTiers().craftingTier(),
                BlueprintCraftingPolicyResolutionSupport.craftingGates(
                        profile.progression().gates()),
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                "legacy_profile_fallback");
    }

    private static BaseAssignment legacyAutomatic(
            BlueprintResearchProfile profile,
            BlueprintResearchPolicyDefinition definition,
            ResourceLocation blueprintId,
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            Map<String, TieAwareWorkbenchTierAllocator.Assignment> allocations) {
        if (!definition.treeEnabled()) {
            return migratedCompatibility();
        }
        Optional<AutomaticWeaponPlacementProposal> proposal =
                candidates.eligibleProposal(blueprintId);
        if (proposal.isEmpty()) {
            return migratedCompatibility();
        }
        AutomaticWeaponPlacementProposal value = proposal.orElseThrow();
        ProgressionGateRequirements gates = BlueprintCraftingPolicyResolutionSupport
                .craftingGates(profile.progression().gates());
        if (value.reviewRequired()) {
            return fromAccess(
                    BlueprintCraftingAccessPolicy.tiered(
                            profile.progression().fallbackTiers().craftingTier()),
                    gates,
                    BlueprintCraftingPolicySource.AUTOMATIC_REVIEW_FALLBACK,
                    "legacy_automatic_review_fallback",
                    Set.of(BlueprintCraftingPolicyWarning.AUTOMATIC_REVIEW_FALLBACK),
                    Optional.of(value.mechanicalScore()),
                    Optional.empty(),
                    true);
        }
        TieAwareWorkbenchTierAllocator.Assignment assignment =
                allocations.get(blueprintId.toString());
        if (assignment == null) {
            throw new IllegalStateException(
                    "trusted legacy automatic gun has no percentile crafting tier: "
                            + blueprintId);
        }
        return automaticPercentile(assignment, gates, "legacy_automatic_percentile");
    }

    private static BaseAssignment legacyAuthored(
            BlueprintResearchProfile profile,
            boolean included,
            Optional<ResearchTechTreePlacementResolver.Placement> placement) {
        if (!included) {
            return migratedCompatibility();
        }
        ResearchWorkbenchTier tier = profile.progression()
                .forAuthoredTier(placement.orElseThrow().tier())
                .craftingTier();
        return tiered(
                tier,
                BlueprintCraftingPolicyResolutionSupport.craftingGates(
                        profile.progression().gates()),
                BlueprintCraftingPolicySource.AUTHORED_BAND,
                "legacy_authored_tier_band");
    }

    private static ResolvedBlueprintCraftingPolicy applyCraftingRule(
            BlueprintResearchSnapshot research,
            ResourceLocation profileId,
            ResourceLocation blueprintId,
            BlueprintResearchPolicyResolver.RuleSelection selection,
            BaseAssignment base) {
        Optional<BlueprintCraftingRuleOverride> rule = selection.selectedRuleId()
                .map(research.rules()::get)
                .flatMap(BlueprintResearchRule::crafting);
        if (rule.isPresent()) {
            BlueprintCraftingRuleOverride override = rule.orElseThrow();
            BlueprintCraftingAccessPolicy access = override.accessPolicy().orElse(base.access());
            ProgressionGateRequirements gates = override.gates().orElse(base.gates());
            return base.withRule(access, gates, selection).toPolicy(profileId, blueprintId);
        }
        if (base.source() == BlueprintCraftingPolicySource.MIGRATED_COMPATIBILITY) {
            return base.toPolicy(profileId, blueprintId);
        }
        Optional<BlueprintProgressionRuleOverride> progression = selection.selectedRuleId()
                .map(research.rules()::get)
                .flatMap(BlueprintResearchRule::progression);
        if (progression.isEmpty()) {
            return base.toPolicy(profileId, blueprintId);
        }
        BlueprintProgressionRuleOverride override = progression.orElseThrow();
        BlueprintCraftingAccessPolicy access = override.craftingTier()
                .map(BlueprintCraftingAccessPolicy::tiered)
                .orElse(base.access());
        ProgressionGateRequirements gates = BlueprintCraftingPolicyResolutionSupport
                .craftingGatesOrElse(override.gates(), base.gates());
        return base.withRule(access, gates, selection).toPolicy(profileId, blueprintId);
    }

    private static BaseAssignment automaticPercentile(
            TieAwareWorkbenchTierAllocator.Assignment assignment,
            ProgressionGateRequirements gates,
            String reason) {
        return fromAccess(
                BlueprintCraftingAccessPolicy.tiered(assignment.tier()),
                gates,
                BlueprintCraftingPolicySource.AUTOMATIC_PERCENTILE,
                reason,
                Set.of(),
                Optional.of(assignment.score()),
                Optional.of(assignment.percentileBasisPoints()),
                false);
    }

    private static BaseAssignment migratedCompatibility() {
        return fromAccess(
                BlueprintCraftingAccessPolicy.UNRESTRICTED,
                ProgressionGateRequirements.EMPTY,
                BlueprintCraftingPolicySource.MIGRATED_COMPATIBILITY,
                "migrated_compatibility",
                Set.of(BlueprintCraftingPolicyWarning.MIGRATED_COMPATIBILITY));
    }

    private static BaseAssignment tiered(
            ResearchWorkbenchTier tier,
            ProgressionGateRequirements gates,
            BlueprintCraftingPolicySource source,
            String reason) {
        return fromAccess(
                BlueprintCraftingAccessPolicy.tiered(tier),
                gates,
                source,
                reason,
                Set.of());
    }

    private static BaseAssignment fromStrategy(
            BlueprintCraftingStrategy strategy,
            ProgressionGateRequirements gates,
            BlueprintCraftingPolicySource source,
            String reason,
            Set<BlueprintCraftingPolicyWarning> warnings) {
        BlueprintCraftingAccessPolicy access = switch (strategy.mode()) {
            case FIXED -> BlueprintCraftingAccessPolicy.tiered(
                    strategy.workbenchTier().orElseThrow());
            case UNRESTRICTED -> BlueprintCraftingAccessPolicy.UNRESTRICTED;
            case DISABLED -> BlueprintCraftingAccessPolicy.DISABLED;
            case AUTOMATIC_TIER, LINKED_WEAPON -> throw new IllegalArgumentException(
                    "derived crafting strategy requires category evidence");
        };
        return fromAccess(access, gates, source, reason, warnings);
    }

    private static BaseAssignment fromAccess(
            BlueprintCraftingAccessPolicy access,
            ProgressionGateRequirements gates,
            BlueprintCraftingPolicySource source,
            String reason,
            Set<BlueprintCraftingPolicyWarning> warnings) {
        return fromAccess(
                access,
                gates,
                source,
                reason,
                warnings,
                Optional.empty(),
                Optional.empty(),
                false);
    }

    private static BaseAssignment fromAccess(
            BlueprintCraftingAccessPolicy access,
            ProgressionGateRequirements gates,
            BlueprintCraftingPolicySource source,
            String reason,
            Set<BlueprintCraftingPolicyWarning> warnings,
            Optional<Integer> score,
            Optional<Integer> percentile,
            boolean reviewRequired) {
        return new BaseAssignment(
                access,
                gates,
                source,
                Optional.empty(),
                MatchSpecificity.NONE,
                score,
                percentile,
                reviewRequired,
                reason,
                warnings);
    }

    private static Map<ResourceLocation, BlueprintData> sortedGuns(
            Map<ResourceLocation, BlueprintData> catalog) {
        catalog.forEach((blueprintId, data) -> {
            if (blueprintId == null || data == null) {
                throw new IllegalArgumentException("blueprint catalog contains null");
            }
            if (data.getKind() == BlueprintKind.GUN
                    && !blueprintId.toString().equals(data.getBpId())) {
                throw new IllegalArgumentException(
                        "gun catalog identity does not match its blueprint data: "
                                + blueprintId);
            }
        });
        Map<ResourceLocation, BlueprintData> guns = new LinkedHashMap<>();
        catalog.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    if (entry.getValue().getKind() == BlueprintKind.GUN) {
                        guns.put(entry.getKey(), entry.getValue());
                    }
                });
        return Collections.unmodifiableMap(guns);
    }

    private static void validateCandidateSnapshots(
            Map<ResourceLocation, AutomaticWeaponPlacementCandidateSnapshot> snapshots,
            long catalogRevision,
            long researchRevision,
            Set<ResourceLocation> gunIds) {
        Set<String> expected = gunIds.stream()
                .map(ResourceLocation::toString)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        snapshots.forEach((treeId, snapshot) -> {
            if (treeId == null || snapshot == null
                    || !snapshot.matches(treeId, catalogRevision, researchRevision)) {
                throw new IllegalArgumentException(
                        "automatic gun candidates are stale or inconsistent");
            }
            Set<String> actual = new LinkedHashSet<>();
            actual.addAll(snapshot.eligibleProposals().keySet());
            actual.addAll(snapshot.excludedAutomaticCandidates().keySet());
            actual.addAll(snapshot.authoredBlueprintIds());
            actual.addAll(snapshot.unplacedBlueprintIds());
            if (!actual.equals(expected)) {
                throw new IllegalArgumentException(
                        "automatic gun candidates do not completely cover the gun catalog for "
                                + treeId);
            }
        });
    }

    private static Map<ResourceLocation, Map<String, TieAwareWorkbenchTierAllocator.Assignment>>
            automaticAllocations(
                    Map<ResourceLocation, AutomaticWeaponPlacementCandidateSnapshot> snapshots,
                    AutomaticWorkbenchTierPercentiles percentiles) {
        Map<ResourceLocation, Map<String, TieAwareWorkbenchTierAllocator.Assignment>> result =
                new LinkedHashMap<>();
        snapshots.forEach((treeId, candidates) -> {
            Map<String, Integer> trustedScores = new LinkedHashMap<>();
            candidates.eligibleProposals().forEach((id, proposal) -> {
                if (!proposal.reviewRequired()) {
                    trustedScores.put(id, proposal.mechanicalScore());
                }
            });
            result.put(
                    treeId,
                    TieAwareWorkbenchTierAllocator.allocate(trustedScores, percentiles));
        });
        return Collections.unmodifiableMap(result);
    }

    private static EvidenceAutomaticAssignments evidenceAutomaticAssignments(
            Set<ResourceLocation> gunIds,
            AutomaticWeaponEvidenceSnapshot evidence,
            AutomaticWorkbenchTierPercentiles percentiles) {
        Set<String> capabilityIds = new LinkedHashSet<>();
        Set<String> mechanicalIds = new LinkedHashSet<>();
        gunIds.forEach(id -> {
            String key = id.toString();
            if (evidence.capabilityScoresByBlueprint().containsKey(key)) {
                capabilityIds.add(key);
            } else if (evidence.scoresByBlueprint().containsKey(key)) {
                mechanicalIds.add(key);
            }
        });
        AutomaticWeaponPlacementPlanner planner = new AutomaticWeaponPlacementPlanner();
        Map<String, AutomaticWeaponPlacementProposal> proposals = new LinkedHashMap<>();
        AutomaticWeaponPlacementPolicy capabilityPolicy = new AutomaticWeaponPlacementPolicy(
                evidence.capabilityPlacementPlan().levelsPerTier(),
                evidence.capabilityPlacementPlan().reviewConfidenceThreshold());
        AutomaticWeaponPlacementPolicy mechanicalPolicy = new AutomaticWeaponPlacementPolicy(
                evidence.placementPlan().levelsPerTier(),
                evidence.placementPlan().reviewConfidenceThreshold());
        var capabilityPlan = planner.planCapabilities(
                evidence.capabilityScoresByBlueprint(),
                capabilityIds,
                capabilityPolicy);
        var mechanicalPlan = planner.plan(
                evidence.scoresByBlueprint(),
                mechanicalIds,
                mechanicalPolicy);
        if (!capabilityPlan.rejectedCandidates().isEmpty()
                || !mechanicalPlan.rejectedCandidates().isEmpty()) {
            throw new IllegalStateException(
                    "validated weapon evidence produced rejected crafting proposals");
        }
        proposals.putAll(capabilityPlan.proposals());
        proposals.putAll(mechanicalPlan.proposals());
        Map<String, Integer> trustedScores = new LinkedHashMap<>();
        proposals.forEach((id, proposal) -> {
            if (!proposal.reviewRequired()) {
                trustedScores.put(id, proposal.mechanicalScore());
            }
        });
        return new EvidenceAutomaticAssignments(
                Collections.unmodifiableMap(proposals),
                TieAwareWorkbenchTierAllocator.allocate(trustedScores, percentiles));
    }

    private record EvidenceAutomaticAssignments(
            Map<String, AutomaticWeaponPlacementProposal> proposals,
            Map<String, TieAwareWorkbenchTierAllocator.Assignment> allocations) {
        private EvidenceAutomaticAssignments {
            proposals = Collections.unmodifiableMap(new LinkedHashMap<>(proposals));
            allocations = Collections.unmodifiableMap(new LinkedHashMap<>(allocations));
        }
    }

    private record BaseAssignment(
            BlueprintCraftingAccessPolicy access,
            ProgressionGateRequirements gates,
            BlueprintCraftingPolicySource source,
            Optional<ResourceLocation> ruleId,
            MatchSpecificity specificity,
            Optional<Integer> score,
            Optional<Integer> percentile,
            boolean reviewRequired,
            String reason,
            Set<BlueprintCraftingPolicyWarning> warnings) {
        private BaseAssignment {
            if (access == null || gates == null || source == null || ruleId == null
                    || specificity == null || score == null || percentile == null
                    || reason == null || warnings == null) {
                throw new IllegalArgumentException("gun crafting assignment cannot contain null");
            }
            warnings = warnings.isEmpty()
                    ? Set.of()
                    : Set.copyOf(EnumSet.copyOf(warnings));
        }

        private BaseAssignment withRule(
                BlueprintCraftingAccessPolicy resolvedAccess,
                ProgressionGateRequirements resolvedGates,
                BlueprintResearchPolicyResolver.RuleSelection selection) {
            ResourceLocation selected = selection.selectedRuleId().orElseThrow();
            return new BaseAssignment(
                    resolvedAccess,
                    resolvedGates,
                    selection.specificity() == MatchSpecificity.EXACT
                            ? BlueprintCraftingPolicySource.EXACT_RULE
                            : BlueprintCraftingPolicySource.AUTHORED_RULE,
                    Optional.of(selected),
                    selection.specificity(),
                    score,
                    percentile,
                    reviewRequired,
                    selection.specificity() == MatchSpecificity.EXACT
                            ? "exact_crafting_rule"
                            : "authored_crafting_rule",
                    Set.of());
        }

        private ResolvedBlueprintCraftingPolicy toPolicy(
                ResourceLocation profileId,
                ResourceLocation blueprintId) {
            return new ResolvedBlueprintCraftingPolicy(
                    profileId,
                    blueprintId,
                    access.disposition(),
                    access.workbenchTier(),
                    gates,
                    source,
                    ruleId,
                    specificity,
                    score,
                    percentile,
                    reviewRequired,
                    reason,
                    warnings);
        }
    }

    /** Immutable and exhaustively validated Phase 3 gun-policy result. */
    public record Resolution(
            long catalogRevision,
            long researchRevision,
            long automaticRevision,
            Set<ResourceLocation> gunBlueprintIds,
            Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>>
                    policiesByProfile,
            Map<ResourceLocation, BlueprintCraftingPolicySnapshot.ProfileDiagnostics>
                    diagnosticsByProfile) {
        public Resolution(
                long catalogRevision,
                long researchRevision,
                long automaticRevision,
                Set<ResourceLocation> gunBlueprintIds,
                Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>>
                        policiesByProfile) {
            this(
                    catalogRevision,
                    researchRevision,
                    automaticRevision,
                    gunBlueprintIds,
                    policiesByProfile,
                    diagnostics(policiesByProfile));
        }

        public Resolution {
            BlueprintCraftingPolicySnapshot validated = new BlueprintCraftingPolicySnapshot(
                    catalogRevision,
                    researchRevision,
                    automaticRevision,
                    gunBlueprintIds,
                    policiesByProfile,
                    diagnosticsByProfile);
            gunBlueprintIds = validated.catalogBlueprintIds();
            policiesByProfile = validated.policiesByProfile();
            diagnosticsByProfile = validated.diagnosticsByProfile();
        }

        public Optional<ResolvedBlueprintCraftingPolicy> policy(
                ResourceLocation profileId,
                ResourceLocation blueprintId) {
            return Optional.ofNullable(
                    policiesByProfile.getOrDefault(profileId, Map.of()).get(blueprintId));
        }

        public boolean matches(long catalog, long research, long automatic) {
            return catalogRevision == catalog
                    && researchRevision == research
                    && automaticRevision == automatic;
        }

        private static Map<ResourceLocation, BlueprintCraftingPolicySnapshot.ProfileDiagnostics>
                diagnostics(
                        Map<ResourceLocation,
                                Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>> policies) {
            if (policies == null) {
                throw new IllegalArgumentException("gun crafting policies cannot be null");
            }
            Map<ResourceLocation, BlueprintCraftingPolicySnapshot.ProfileDiagnostics> result =
                    new LinkedHashMap<>();
            policies.forEach((profileId, assignments) -> result.put(
                    profileId,
                    BlueprintCraftingPolicySnapshot.ProfileDiagnostics.from(assignments)));
            return result;
        }
    }
}
