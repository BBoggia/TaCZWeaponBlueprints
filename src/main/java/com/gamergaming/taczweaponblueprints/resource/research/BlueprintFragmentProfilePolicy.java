package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentDiscount;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Profile defaults from which one blueprint's bounded fragment policy is derived. */
public record BlueprintFragmentProfilePolicy(
        BlueprintFragmentPolicy.CompletionMode mode,
        Map<ResearchWorkbenchTier, Integer> thresholds,
        int retainedProgressCap,
        BlueprintFragmentDiscount researchDiscount,
        int learnedTargetResearchPoints) {
    public static final BlueprintFragmentProfilePolicy DISABLED = new BlueprintFragmentProfilePolicy(
            BlueprintFragmentPolicy.CompletionMode.DISABLED,
            Map.of(),
            0,
            BlueprintFragmentDiscount.NONE,
            0);
    public static final BlueprintFragmentProfilePolicy DEFAULT = new BlueprintFragmentProfilePolicy(
            BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST,
            Map.of(
                    ResearchWorkbenchTier.TIER_1, 5,
                    ResearchWorkbenchTier.TIER_2, 10,
                    ResearchWorkbenchTier.TIER_3, 15),
            1_000,
            BlueprintFragmentDiscount.percentage(2_500),
            1);

    private static final Codec<Map<ResearchWorkbenchTier, Integer>> THRESHOLDS_CODEC =
            Codec.unboundedMap(BlueprintProgressionCodecs.WORKBENCH_TIER, Codec.INT);
    private static final Codec<BlueprintFragmentProfilePolicy> RAW_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    BlueprintProgressionCodecs.FRAGMENT_MODE.fieldOf("mode")
                            .forGetter(BlueprintFragmentProfilePolicy::mode),
                    THRESHOLDS_CODEC.fieldOf("thresholds")
                            .forGetter(BlueprintFragmentProfilePolicy::thresholds),
                    Codec.INT.fieldOf("retained_progress_cap")
                            .forGetter(BlueprintFragmentProfilePolicy::retainedProgressCap),
                    BlueprintProgressionCodecs.FRAGMENT_DISCOUNT.fieldOf("research_discount")
                            .forGetter(BlueprintFragmentProfilePolicy::researchDiscount),
                    BlueprintResearchCodecs.POINTS.fieldOf("learned_target_rp")
                            .forGetter(BlueprintFragmentProfilePolicy::learnedTargetResearchPoints))
                    .apply(instance, BlueprintFragmentProfilePolicy::new));
    public static final Codec<BlueprintFragmentProfilePolicy> CODEC = StrictRecordCodec.wrap(
            "Blueprint Fragment profile policy",
            RAW_CODEC,
            "mode",
            "thresholds",
            "retained_progress_cap",
            "research_discount",
            "learned_target_rp");

    public BlueprintFragmentProfilePolicy {
        if (mode == null || thresholds == null || researchDiscount == null) {
            throw new IllegalArgumentException("Blueprint Fragment profile policy fields cannot be null");
        }
        EnumMap<ResearchWorkbenchTier, Integer> normalized =
                new EnumMap<>(ResearchWorkbenchTier.class);
        thresholds.forEach((tier, threshold) -> {
            if (tier == null || threshold == null) {
                throw new IllegalArgumentException("Blueprint Fragment thresholds cannot contain null");
            }
            normalized.put(tier, threshold);
        });
        thresholds = Collections.unmodifiableMap(normalized);
        if (mode == BlueprintFragmentPolicy.CompletionMode.DISABLED) {
            if (!thresholds.isEmpty() || retainedProgressCap != 0
                    || !researchDiscount.equals(BlueprintFragmentDiscount.NONE)
                    || learnedTargetResearchPoints != 0) {
                throw new IllegalArgumentException("disabled Blueprint Fragment profile contains active values");
            }
        } else {
            if (!thresholds.keySet().equals(java.util.EnumSet.allOf(ResearchWorkbenchTier.class))) {
                throw new IllegalArgumentException("enabled Blueprint Fragment profile requires all three tier thresholds");
            }
            for (int threshold : thresholds.values()) {
                // Reuse the domain policy as the single numeric invariant authority.
                new BlueprintFragmentPolicy(
                        mode,
                        threshold,
                        retainedProgressCap,
                        researchDiscount,
                        learnedTargetResearchPoints);
            }
        }
    }

    public BlueprintFragmentPolicy resolve(
            ResearchWorkbenchTier tier,
            Optional<Integer> exactThreshold) {
        if (mode == BlueprintFragmentPolicy.CompletionMode.DISABLED) {
            return BlueprintFragmentPolicy.DISABLED;
        }
        if (tier == null) {
            throw new IllegalArgumentException("resolved fragment tier cannot be null");
        }
        int threshold = exactThreshold == null
                ? thresholds.get(tier)
                : exactThreshold.orElse(thresholds.get(tier));
        return new BlueprintFragmentPolicy(
                mode,
                threshold,
                Math.max(retainedProgressCap, threshold),
                researchDiscount,
                learnedTargetResearchPoints);
    }
}
