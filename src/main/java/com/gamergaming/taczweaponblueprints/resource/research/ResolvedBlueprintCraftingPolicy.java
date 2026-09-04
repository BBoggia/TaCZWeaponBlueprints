package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;

import net.minecraft.resources.ResourceLocation;

/** Immutable and explainable crafting policy for one canonical catalog entry. */
public record ResolvedBlueprintCraftingPolicy(
        ResourceLocation profileId,
        ResourceLocation blueprintId,
        BlueprintCraftingDisposition disposition,
        Optional<ResearchWorkbenchTier> requiredWorkbenchTier,
        ProgressionGateRequirements gates,
        BlueprintCraftingPolicySource source,
        Optional<ResourceLocation> selectedRuleId,
        MatchSpecificity ruleSpecificity,
        Optional<Integer> automaticScore,
        Optional<Integer> automaticPercentileBasisPoints,
        boolean reviewRequired,
        String reasonCode,
        Set<BlueprintCraftingPolicyWarning> warnings) {
    public static final int MAX_REASON_CODE_LENGTH = 128;
    private static final Pattern REASON_CODE_PATTERN = Pattern.compile("[a-z0-9_.:-]+");

    public ResolvedBlueprintCraftingPolicy {
        requiredWorkbenchTier = optional(requiredWorkbenchTier);
        selectedRuleId = optional(selectedRuleId);
        automaticScore = optional(automaticScore);
        automaticPercentileBasisPoints = optional(automaticPercentileBasisPoints);
        warnings = immutableWarnings(warnings);
        if (profileId == null || blueprintId == null || disposition == null
                || gates == null || source == null || ruleSpecificity == null
                || oversized(profileId) || oversized(blueprintId)
                || reasonCode == null || reasonCode.isBlank()
                || reasonCode.length() > MAX_REASON_CODE_LENGTH
                || !REASON_CODE_PATTERN.matcher(reasonCode).matches()) {
            throw new IllegalArgumentException("resolved crafting policy is invalid");
        }
        if ((disposition == BlueprintCraftingDisposition.TIERED)
                != requiredWorkbenchTier.isPresent()) {
            throw new IllegalArgumentException(
                    "only a tiered crafting policy may declare a required Workbench level");
        }
        selectedRuleId.ifPresent(ruleId -> {
            if (oversized(ruleId)) {
                throw new IllegalArgumentException("crafting policy rule ID is oversized");
            }
        });
        if (selectedRuleId.isPresent()
                != (ruleSpecificity != MatchSpecificity.NONE)) {
            throw new IllegalArgumentException(
                    "crafting rule identity and specificity must be present together");
        }
        boolean ruleSource = source == BlueprintCraftingPolicySource.EXACT_RULE
                || source == BlueprintCraftingPolicySource.AUTHORED_RULE;
        if (selectedRuleId.isPresent() != ruleSource) {
            throw new IllegalArgumentException(
                    "crafting rule identity is allowed only for a rule assignment source");
        }
        if (source == BlueprintCraftingPolicySource.EXACT_RULE
                && (selectedRuleId.isEmpty()
                        || ruleSpecificity != MatchSpecificity.EXACT)) {
            throw new IllegalArgumentException(
                    "an exact crafting-rule source requires an exact selected rule");
        }
        if (source == BlueprintCraftingPolicySource.AUTHORED_RULE
                && (selectedRuleId.isEmpty()
                        || ruleSpecificity == MatchSpecificity.NONE
                        || ruleSpecificity == MatchSpecificity.EXACT)) {
            throw new IllegalArgumentException(
                    "an authored crafting-rule source requires a tag or selector rule");
        }
        automaticScore.ifPresent(score -> {
            if (score < 0 || score > 100) {
                throw new IllegalArgumentException("automatic crafting score is out of bounds");
            }
        });
        automaticPercentileBasisPoints.ifPresent(percentile -> {
            if (percentile < 0
                    || percentile > AutomaticWorkbenchTierPercentiles.BASIS_POINTS) {
                throw new IllegalArgumentException(
                        "automatic crafting percentile is out of bounds");
            }
        });
        if (automaticPercentileBasisPoints.isPresent() && automaticScore.isEmpty()) {
            throw new IllegalArgumentException(
                    "automatic crafting percentile requires a score");
        }
        if (source == BlueprintCraftingPolicySource.AUTOMATIC_PERCENTILE
                && (disposition != BlueprintCraftingDisposition.TIERED
                        || automaticScore.isEmpty()
                        || automaticPercentileBasisPoints.isEmpty()
                        || reviewRequired)) {
            throw new IllegalArgumentException(
                    "automatic percentile assignment requires tiered score evidence");
        }
        if (source == BlueprintCraftingPolicySource.AUTOMATIC_REVIEW_FALLBACK
                && (!reviewRequired || automaticScore.isEmpty()
                        || automaticPercentileBasisPoints.isPresent())) {
            throw new IllegalArgumentException(
                    "automatic review fallback must remain visible in diagnostics");
        }
        if ((source == BlueprintCraftingPolicySource.AUTHORED_BAND
                        || source == BlueprintCraftingPolicySource.LINKED_WEAPON)
                && disposition != BlueprintCraftingDisposition.TIERED) {
            throw new IllegalArgumentException(
                    "authored-band and linked-weapon assignments must be tiered");
        }
        if (source == BlueprintCraftingPolicySource.MIGRATED_COMPATIBILITY
                && disposition != BlueprintCraftingDisposition.UNRESTRICTED) {
            throw new IllegalArgumentException(
                    "migrated compatibility assignments must remain explicitly unrestricted");
        }
        if (warnings.contains(BlueprintCraftingPolicyWarning.AUTOMATIC_REVIEW_FALLBACK)
                != (source == BlueprintCraftingPolicySource.AUTOMATIC_REVIEW_FALLBACK)) {
            throw new IllegalArgumentException(
                    "automatic review warning and source must agree");
        }
        if (warnings.contains(BlueprintCraftingPolicyWarning.MIGRATED_COMPATIBILITY)
                != (source == BlueprintCraftingPolicySource.MIGRATED_COMPATIBILITY)) {
            throw new IllegalArgumentException(
                    "migration warning and source must agree");
        }
        if (warnings.contains(BlueprintCraftingPolicyWarning.AUTHORED_OMITTED_FALLBACK)
                && source != BlueprintCraftingPolicySource.PROFILE_FALLBACK) {
            throw new IllegalArgumentException(
                    "authored omission warning requires a profile fallback assignment");
        }
    }

    /** Evaluates only the ordinary Workbench-level disposition. Gates remain separate. */
    public boolean permitsWorkbench(ResearchWorkbenchTier availableTier) {
        if (availableTier == null) {
            return false;
        }
        return switch (disposition) {
            case TIERED -> availableTier.satisfies(requiredWorkbenchTier.orElseThrow());
            case UNRESTRICTED -> true;
            case DISABLED -> false;
        };
    }

    private static boolean oversized(ResourceLocation id) {
        return id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;
    }

    private static <T> Optional<T> optional(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }

    private static Set<BlueprintCraftingPolicyWarning> immutableWarnings(
            Set<BlueprintCraftingPolicyWarning> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return Set.of();
        }
        if (warnings.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("crafting policy warnings contain null");
        }
        return Set.copyOf(EnumSet.copyOf(warnings));
    }
}
