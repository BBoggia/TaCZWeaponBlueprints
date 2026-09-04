package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

import net.minecraft.resources.ResourceLocation;

/** Complete immutable tier, fragment, and gate publication for a catalog/research revision pair. */
public record BlueprintProgressionPolicySnapshot(
        long catalogRevision,
        long researchRevision,
        AutomaticWorkbenchTierPercentiles automaticPercentiles,
        Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintProgressionPolicy>> policiesByProfile,
        Map<ResourceLocation, Map<ResourceLocation, String>> omissionsByProfile,
        Map<ResourceLocation, ProfileDiagnostics> diagnosticsByProfile) {
    /**
     * Aggregate reload budget for the eager profile-by-catalog publication.
     * Individual source limits remain useful, but cannot safely be multiplied
     * without a separate bound.
     */
    public static final int MAX_TOTAL_POLICY_ASSIGNMENTS = 262_144;
    public static final BlueprintProgressionPolicySnapshot EMPTY = new BlueprintProgressionPolicySnapshot(
            0L, 0L, AutomaticWorkbenchTierPercentiles.DEFAULT, Map.of(), Map.of(), Map.of());

    public BlueprintProgressionPolicySnapshot {
        if (catalogRevision < 0L || researchRevision < 0L || automaticPercentiles == null
                || policiesByProfile == null || omissionsByProfile == null
                || diagnosticsByProfile == null) {
            throw new IllegalArgumentException("blueprint progression policy snapshot is invalid");
        }
        policiesByProfile = immutableNested(policiesByProfile);
        omissionsByProfile = immutableNested(omissionsByProfile);
        diagnosticsByProfile = immutableMap(diagnosticsByProfile);
        if (!policiesByProfile.keySet().equals(omissionsByProfile.keySet())
                || !policiesByProfile.keySet().equals(diagnosticsByProfile.keySet())) {
            throw new IllegalArgumentException("blueprint progression policy profile coverage is inconsistent");
        }
        long assignmentCount = 0L;
        for (ResourceLocation profileId : policiesByProfile.keySet()) {
            Map<ResourceLocation, ResolvedBlueprintProgressionPolicy> policies =
                    policiesByProfile.get(profileId);
            Map<ResourceLocation, String> omissions = omissionsByProfile.get(profileId);
            ProfileDiagnostics diagnostics = diagnosticsByProfile.get(profileId);
            if (!Collections.disjoint(policies.keySet(), omissions.keySet())
                    || diagnostics.includedCount() != policies.size()
                    || diagnostics.omittedCount() != omissions.size()) {
                throw new IllegalArgumentException(
                        "blueprint progression policy diagnostics or assignments are inconsistent");
            }
            assignmentCount += (long) policies.size() + omissions.size();
        }
        if (assignmentCount > MAX_TOTAL_POLICY_ASSIGNMENTS) {
            throw new IllegalArgumentException(
                    "blueprint progression policy exceeds the aggregate assignment limit");
        }
    }

    public Optional<ResolvedBlueprintProgressionPolicy> policy(
            ResourceLocation profileId,
            ResourceLocation blueprintId) {
        return Optional.ofNullable(policiesByProfile.getOrDefault(profileId, Map.of()).get(blueprintId));
    }

    public Optional<String> omissionReason(
            ResourceLocation profileId,
            ResourceLocation blueprintId) {
        return Optional.ofNullable(omissionsByProfile.getOrDefault(profileId, Map.of()).get(blueprintId));
    }

    /**
     * An explicit omission is outside workstation-tier enforcement. Unknown
     * entries are not treated as omissions so a stale or incomplete
     * publication cannot accidentally grant access.
     */
    public boolean explicitlyOutsideTieredProgression(
            ResourceLocation profileId,
            ResourceLocation blueprintId) {
        return omissionReason(profileId, blueprintId).isPresent();
    }

    public boolean matches(long catalog, long research) {
        return catalogRevision == catalog && researchRevision == research;
    }

    private static <V> Map<ResourceLocation, V> immutableMap(Map<ResourceLocation, V> source) {
        Map<ResourceLocation, V> copy = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        throw new IllegalArgumentException("progression policy map contains null");
                    }
                    copy.put(entry.getKey(), entry.getValue());
                });
        return Collections.unmodifiableMap(copy);
    }

    private static <V> Map<ResourceLocation, Map<ResourceLocation, V>> immutableNested(
            Map<ResourceLocation, Map<ResourceLocation, V>> source) {
        Map<ResourceLocation, Map<ResourceLocation, V>> copy = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> copy.put(entry.getKey(), immutableMap(entry.getValue())));
        return Collections.unmodifiableMap(copy);
    }

    public record ProfileDiagnostics(
            int includedCount,
            int omittedCount,
            Map<ResearchWorkbenchTier, Integer> researchTierCounts,
            int reviewFallbackCount,
            int gateGroupCount,
            int gateConditionCount,
            Map<Integer, Integer> fragmentThresholdCounts) {
        public ProfileDiagnostics {
            if (includedCount < 0 || omittedCount < 0 || reviewFallbackCount < 0
                    || gateGroupCount < 0 || gateConditionCount < 0
                    || researchTierCounts == null
                    || fragmentThresholdCounts == null) {
                throw new IllegalArgumentException("progression policy diagnostics are invalid");
            }
            researchTierCounts = immutableTierCounts(researchTierCounts);
            fragmentThresholdCounts = Collections.unmodifiableMap(new java.util.TreeMap<>(fragmentThresholdCounts));
            if (researchTierCounts.values().stream().mapToInt(Integer::intValue).sum()
                    != includedCount) {
                throw new IllegalArgumentException("progression policy tier counts are inconsistent");
            }
        }

        private static Map<ResearchWorkbenchTier, Integer> immutableTierCounts(
                Map<ResearchWorkbenchTier, Integer> source) {
            EnumMap<ResearchWorkbenchTier, Integer> copy = new EnumMap<>(ResearchWorkbenchTier.class);
            for (ResearchWorkbenchTier tier : ResearchWorkbenchTier.values()) {
                int count = source.getOrDefault(tier, 0);
                if (count < 0) {
                    throw new IllegalArgumentException("progression policy tier count is negative");
                }
                copy.put(tier, count);
            }
            return Collections.unmodifiableMap(copy);
        }
    }
}
