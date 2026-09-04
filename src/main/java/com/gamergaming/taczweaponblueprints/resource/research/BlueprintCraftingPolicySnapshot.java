package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;

import net.minecraft.resources.ResourceLocation;

/** Complete immutable crafting policy for one catalog and research revision set. */
public record BlueprintCraftingPolicySnapshot(
        long catalogRevision,
        long researchRevision,
        long automaticRevision,
        Set<ResourceLocation> catalogBlueprintIds,
        Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>> policiesByProfile,
        Map<ResourceLocation, ProfileDiagnostics> diagnosticsByProfile) {
    public static final int MAX_TOTAL_POLICY_ASSIGNMENTS = 262_144;
    public static final int MAX_PROFILES = BlueprintResearchSnapshot.MAX_DEFINITIONS_PER_TYPE;
    public static final int MAX_CATALOG_ENTRIES = BlueprintDataManager.MAX_CATALOG_ENTRIES;
    public static final BlueprintCraftingPolicySnapshot EMPTY = new BlueprintCraftingPolicySnapshot(
            0L, 0L, 0L, Set.of(), Map.of(), Map.of());

    public BlueprintCraftingPolicySnapshot {
        if (catalogRevision < 0L || researchRevision < 0L || automaticRevision < 0L
                || catalogBlueprintIds == null || policiesByProfile == null
                || diagnosticsByProfile == null) {
            throw new IllegalArgumentException("crafting policy snapshot is invalid");
        }
        if (policiesByProfile.size() > MAX_PROFILES
                || catalogBlueprintIds.size() > MAX_CATALOG_ENTRIES) {
            throw new IllegalArgumentException(
                    "crafting policy profile or catalog count exceeds its size limit");
        }
        long expectedAssignments = (long) policiesByProfile.size()
                * catalogBlueprintIds.size();
        if (expectedAssignments > MAX_TOTAL_POLICY_ASSIGNMENTS) {
            throw new IllegalArgumentException(
                    "crafting policy exceeds the aggregate assignment limit");
        }

        catalogBlueprintIds = immutableIds(catalogBlueprintIds);
        policiesByProfile = immutableNestedPolicies(policiesByProfile);
        diagnosticsByProfile = immutableDiagnostics(diagnosticsByProfile);
        if (!policiesByProfile.keySet().equals(diagnosticsByProfile.keySet())) {
            throw new IllegalArgumentException(
                    "crafting policy profile diagnostics coverage is inconsistent");
        }

        for (Map.Entry<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>>
                profileEntry : policiesByProfile.entrySet()) {
            ResourceLocation profileId = profileEntry.getKey();
            Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> policies =
                    profileEntry.getValue();
            if (!policies.keySet().equals(catalogBlueprintIds)) {
                throw new IllegalArgumentException(
                        "crafting policy does not completely cover the catalog for " + profileId);
            }
            policies.forEach((blueprintId, policy) -> {
                if (!profileId.equals(policy.profileId())
                        || !blueprintId.equals(policy.blueprintId())) {
                    throw new IllegalArgumentException(
                            "crafting policy map identity does not match its policy");
                }
            });
            ProfileDiagnostics expected = ProfileDiagnostics.from(policies);
            if (!expected.equals(diagnosticsByProfile.get(profileId))) {
                throw new IllegalArgumentException(
                        "crafting policy diagnostics do not match their assignments");
            }
        }
    }

    public static BlueprintCraftingPolicySnapshot create(
            long catalogRevision,
            long researchRevision,
            long automaticRevision,
            Set<ResourceLocation> catalogBlueprintIds,
            Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>>
                    policiesByProfile) {
        Map<ResourceLocation, ProfileDiagnostics> diagnostics = new LinkedHashMap<>();
        if (policiesByProfile != null) {
            policiesByProfile.forEach((profileId, policies) -> {
                if (profileId == null || policies == null) {
                    throw new IllegalArgumentException(
                            "crafting policy profile assignments contain null");
                }
                diagnostics.put(profileId, ProfileDiagnostics.from(policies));
            });
        }
        return new BlueprintCraftingPolicySnapshot(
                catalogRevision,
                researchRevision,
                automaticRevision,
                catalogBlueprintIds,
                policiesByProfile,
                diagnostics);
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

    private static Set<ResourceLocation> immutableIds(Set<ResourceLocation> source) {
        TreeSet<ResourceLocation> copy = new TreeSet<>(
                java.util.Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation id : source) {
            if (id == null
                    || id.toString().length()
                            > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
                throw new IllegalArgumentException(
                        "crafting policy catalog contains an invalid blueprint ID");
            }
            copy.add(id);
        }
        return Collections.unmodifiableSet(copy);
    }

    private static Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>>
            immutableNestedPolicies(
                    Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>>
                            source) {
        Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>> copy =
                new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        throw new IllegalArgumentException(
                                "crafting policy profile map contains null");
                    }
                    if (entry.getKey().toString().length()
                            > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
                        throw new IllegalArgumentException(
                                "crafting policy profile ID is oversized");
                    }
                    Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> profile =
                            new LinkedHashMap<>();
                    entry.getValue().entrySet().stream()
                            .sorted(Map.Entry.comparingByKey(
                                    java.util.Comparator.comparing(ResourceLocation::toString)))
                            .forEach(policyEntry -> {
                                if (policyEntry.getKey() == null
                                        || policyEntry.getValue() == null) {
                                    throw new IllegalArgumentException(
                                            "crafting policy assignment contains null");
                                }
                                profile.put(policyEntry.getKey(), policyEntry.getValue());
                            });
                    copy.put(entry.getKey(), Collections.unmodifiableMap(profile));
                });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<ResourceLocation, ProfileDiagnostics> immutableDiagnostics(
            Map<ResourceLocation, ProfileDiagnostics> source) {
        Map<ResourceLocation, ProfileDiagnostics> copy = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        throw new IllegalArgumentException(
                                "crafting policy diagnostics contain null");
                    }
                    copy.put(entry.getKey(), entry.getValue());
                });
        return Collections.unmodifiableMap(copy);
    }

    public record ProfileDiagnostics(
            int assignedCount,
            Map<BlueprintCraftingDisposition, Integer> dispositionCounts,
            Map<ResearchWorkbenchTier, Integer> tierCounts,
            Map<BlueprintCraftingPolicySource, Integer> sourceCounts,
            Map<BlueprintCraftingPolicyWarning, Integer> warningCounts,
            int reviewRequiredCount,
            int gateGroupCount,
            int gateConditionCount) {
        public ProfileDiagnostics {
            if (assignedCount < 0 || dispositionCounts == null || tierCounts == null
                    || sourceCounts == null || warningCounts == null
                    || reviewRequiredCount < 0 || reviewRequiredCount > assignedCount
                    || gateGroupCount < 0 || gateConditionCount < 0
                    || (long) gateGroupCount
                            > (long) assignedCount * ProgressionGateRequirements.MAX_GROUPS
                    || (long) gateConditionCount
                            > (long) assignedCount
                                    * ProgressionGateRequirements.MAX_TOTAL_CONDITIONS) {
                throw new IllegalArgumentException(
                        "crafting policy profile diagnostics are invalid");
            }
            dispositionCounts = immutableCounts(
                    BlueprintCraftingDisposition.class, dispositionCounts, assignedCount);
            tierCounts = immutableCounts(
                    ResearchWorkbenchTier.class, tierCounts, assignedCount);
            sourceCounts = immutableCounts(
                    BlueprintCraftingPolicySource.class, sourceCounts, assignedCount);
            warningCounts = immutableCounts(
                    BlueprintCraftingPolicyWarning.class, warningCounts, assignedCount);
            if (sum(dispositionCounts) != assignedCount
                    || sum(sourceCounts) != assignedCount
                    || sum(tierCounts) != dispositionCounts.get(
                            BlueprintCraftingDisposition.TIERED)) {
                throw new IllegalArgumentException(
                        "crafting policy profile diagnostics counts are inconsistent");
            }
        }

        public static ProfileDiagnostics from(
                Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> policies) {
            if (policies == null) {
                throw new IllegalArgumentException("crafting policy assignments cannot be null");
            }
            EnumMap<BlueprintCraftingDisposition, Integer> dispositions =
                    zeroCounts(BlueprintCraftingDisposition.class);
            EnumMap<ResearchWorkbenchTier, Integer> tiers =
                    zeroCounts(ResearchWorkbenchTier.class);
            EnumMap<BlueprintCraftingPolicySource, Integer> sources =
                    zeroCounts(BlueprintCraftingPolicySource.class);
            EnumMap<BlueprintCraftingPolicyWarning, Integer> warnings =
                    zeroCounts(BlueprintCraftingPolicyWarning.class);
            int reviews = 0;
            int gateGroups = 0;
            int gateConditions = 0;
            for (ResolvedBlueprintCraftingPolicy policy : policies.values()) {
                if (policy == null) {
                    throw new IllegalArgumentException(
                            "crafting policy assignments contain null");
                }
                dispositions.merge(policy.disposition(), 1, Integer::sum);
                policy.requiredWorkbenchTier().ifPresent(tier ->
                        tiers.merge(tier, 1, Integer::sum));
                sources.merge(policy.source(), 1, Integer::sum);
                policy.warnings().forEach(warning ->
                        warnings.merge(warning, 1, Integer::sum));
                if (policy.reviewRequired()) {
                    reviews++;
                }
                gateGroups += policy.gates().allOf().size();
                gateConditions += policy.gates().conditionCount();
            }
            return new ProfileDiagnostics(
                    policies.size(),
                    dispositions,
                    tiers,
                    sources,
                    warnings,
                    reviews,
                    gateGroups,
                    gateConditions);
        }

        private static <E extends Enum<E>> EnumMap<E, Integer> zeroCounts(
                Class<E> type) {
            EnumMap<E, Integer> result = new EnumMap<>(type);
            for (E value : type.getEnumConstants()) {
                result.put(value, 0);
            }
            return result;
        }

        private static <E extends Enum<E>> Map<E, Integer> immutableCounts(
                Class<E> type,
                Map<E, Integer> source,
                int assignedCount) {
            EnumMap<E, Integer> copy = zeroCounts(type);
            for (Map.Entry<E, Integer> entry : source.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null
                        || entry.getValue() < 0 || entry.getValue() > assignedCount) {
                    throw new IllegalArgumentException(
                            "crafting policy diagnostic count is invalid");
                }
                copy.put(entry.getKey(), entry.getValue());
            }
            return Collections.unmodifiableMap(copy);
        }

        private static int sum(Map<?, Integer> counts) {
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}
