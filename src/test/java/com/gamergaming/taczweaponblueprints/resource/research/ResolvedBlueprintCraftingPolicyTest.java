package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;

import net.minecraft.resources.ResourceLocation;

class ResolvedBlueprintCraftingPolicyTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation BLUEPRINT = id("test:blueprint");

    @Test
    void tieredDispositionRequiresExactlyOneWorkbenchLevel() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                BlueprintCraftingDisposition.TIERED,
                Optional.empty(),
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                Set.of()));
        assertThrows(IllegalArgumentException.class, () -> policy(
                BlueprintCraftingDisposition.UNRESTRICTED,
                Optional.of(ResearchWorkbenchTier.TIER_1),
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                Set.of()));
        assertThrows(IllegalArgumentException.class, () -> policy(
                BlueprintCraftingDisposition.DISABLED,
                Optional.of(ResearchWorkbenchTier.TIER_3),
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                Set.of()));
    }

    @Test
    void workbenchEvaluationImplementsTheFrozenMonotonicMatrix() {
        ResolvedBlueprintCraftingPolicy tierOne = tiered(ResearchWorkbenchTier.TIER_1);
        ResolvedBlueprintCraftingPolicy tierTwo = tiered(ResearchWorkbenchTier.TIER_2);
        ResolvedBlueprintCraftingPolicy tierThree = tiered(ResearchWorkbenchTier.TIER_3);
        ResolvedBlueprintCraftingPolicy unrestricted = policy(
                BlueprintCraftingDisposition.UNRESTRICTED,
                Optional.empty(),
                BlueprintCraftingPolicySource.CATEGORY_DEFAULT,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                Set.of());
        ResolvedBlueprintCraftingPolicy disabled = policy(
                BlueprintCraftingDisposition.DISABLED,
                Optional.empty(),
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                Set.of());

        assertTrue(tierOne.permitsWorkbench(ResearchWorkbenchTier.TIER_1));
        assertTrue(tierOne.permitsWorkbench(ResearchWorkbenchTier.TIER_2));
        assertTrue(tierOne.permitsWorkbench(ResearchWorkbenchTier.TIER_3));
        assertFalse(tierTwo.permitsWorkbench(ResearchWorkbenchTier.TIER_1));
        assertTrue(tierTwo.permitsWorkbench(ResearchWorkbenchTier.TIER_2));
        assertTrue(tierTwo.permitsWorkbench(ResearchWorkbenchTier.TIER_3));
        assertFalse(tierThree.permitsWorkbench(ResearchWorkbenchTier.TIER_1));
        assertFalse(tierThree.permitsWorkbench(ResearchWorkbenchTier.TIER_2));
        assertTrue(tierThree.permitsWorkbench(ResearchWorkbenchTier.TIER_3));
        assertTrue(unrestricted.permitsWorkbench(ResearchWorkbenchTier.TIER_1));
        assertFalse(disabled.permitsWorkbench(ResearchWorkbenchTier.TIER_3));
        assertFalse(tierOne.permitsWorkbench(null));
    }

    @Test
    void automaticPercentileRequiresTieredScoreAndPercentileEvidence() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                BlueprintCraftingDisposition.TIERED,
                Optional.of(ResearchWorkbenchTier.TIER_2),
                BlueprintCraftingPolicySource.AUTOMATIC_PERCENTILE,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.of(64),
                Optional.empty(),
                false,
                Set.of()));
        assertThrows(IllegalArgumentException.class, () -> policy(
                BlueprintCraftingDisposition.UNRESTRICTED,
                Optional.empty(),
                BlueprintCraftingPolicySource.AUTOMATIC_PERCENTILE,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.of(64),
                Optional.of(7_500),
                false,
                Set.of()));
        assertThrows(IllegalArgumentException.class, () -> policy(
                BlueprintCraftingDisposition.TIERED,
                Optional.of(ResearchWorkbenchTier.TIER_2),
                BlueprintCraftingPolicySource.AUTOMATIC_PERCENTILE,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.of(64),
                Optional.of(7_500),
                true,
                Set.of()));

        policy(
                BlueprintCraftingDisposition.TIERED,
                Optional.of(ResearchWorkbenchTier.TIER_2),
                BlueprintCraftingPolicySource.AUTOMATIC_PERCENTILE,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.of(64),
                Optional.of(7_500),
                false,
                Set.of());
    }

    @Test
    void reviewAndMigrationWarningsMustAgreeWithTheirSources() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                BlueprintCraftingDisposition.TIERED,
                Optional.of(ResearchWorkbenchTier.TIER_2),
                BlueprintCraftingPolicySource.AUTOMATIC_REVIEW_FALLBACK,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                true,
                Set.of()));
        assertThrows(IllegalArgumentException.class, () -> policy(
                BlueprintCraftingDisposition.UNRESTRICTED,
                Optional.empty(),
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                Set.of(BlueprintCraftingPolicyWarning.MIGRATED_COMPATIBILITY)));
        assertThrows(IllegalArgumentException.class, () -> policy(
                BlueprintCraftingDisposition.TIERED,
                Optional.of(ResearchWorkbenchTier.TIER_1),
                BlueprintCraftingPolicySource.MIGRATED_COMPATIBILITY,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                Set.of(BlueprintCraftingPolicyWarning.MIGRATED_COMPATIBILITY)));

        policy(
                BlueprintCraftingDisposition.TIERED,
                Optional.of(ResearchWorkbenchTier.TIER_2),
                BlueprintCraftingPolicySource.AUTOMATIC_REVIEW_FALLBACK,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.of(51),
                Optional.empty(),
                true,
                Set.of(BlueprintCraftingPolicyWarning.AUTOMATIC_REVIEW_FALLBACK));
        policy(
                BlueprintCraftingDisposition.UNRESTRICTED,
                Optional.empty(),
                BlueprintCraftingPolicySource.MIGRATED_COMPATIBILITY,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                Set.of(BlueprintCraftingPolicyWarning.MIGRATED_COMPATIBILITY));
    }

    @Test
    void derivedTierSourcesCannotRepresentUnrestrictedOrDisabledAccess() {
        for (BlueprintCraftingPolicySource source : Set.of(
                BlueprintCraftingPolicySource.AUTHORED_BAND,
                BlueprintCraftingPolicySource.LINKED_WEAPON)) {
            assertThrows(IllegalArgumentException.class, () -> policy(
                    BlueprintCraftingDisposition.UNRESTRICTED,
                    Optional.empty(),
                    source,
                    Optional.empty(),
                    MatchSpecificity.NONE,
                    Optional.empty(),
                    Optional.empty(),
                    false,
                    Set.of()));
        }
    }

    @Test
    void automaticReviewFallbackSupportsEveryExplicitFallbackDisposition() {
        for (BlueprintCraftingDisposition disposition : BlueprintCraftingDisposition.values()) {
            Optional<ResearchWorkbenchTier> tier = disposition == BlueprintCraftingDisposition.TIERED
                    ? Optional.of(ResearchWorkbenchTier.TIER_2)
                    : Optional.empty();
            policy(
                    disposition,
                    tier,
                    BlueprintCraftingPolicySource.AUTOMATIC_REVIEW_FALLBACK,
                    Optional.empty(),
                    MatchSpecificity.NONE,
                    Optional.of(51),
                    Optional.empty(),
                    true,
                    Set.of(BlueprintCraftingPolicyWarning.AUTOMATIC_REVIEW_FALLBACK));
        }
    }

    @Test
    void ruleIdentitySpecificityAndSourceRemainConsistent() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                BlueprintCraftingDisposition.TIERED,
                Optional.of(ResearchWorkbenchTier.TIER_1),
                BlueprintCraftingPolicySource.EXACT_RULE,
                Optional.of(id("test:rule")),
                MatchSpecificity.SELECTOR,
                Optional.empty(),
                Optional.empty(),
                false,
                Set.of()));
        assertThrows(IllegalArgumentException.class, () -> policy(
                BlueprintCraftingDisposition.TIERED,
                Optional.of(ResearchWorkbenchTier.TIER_1),
                BlueprintCraftingPolicySource.CATEGORY_DEFAULT,
                Optional.of(id("test:impossible_rule")),
                MatchSpecificity.EXACT,
                Optional.empty(),
                Optional.empty(),
                false,
                Set.of()));
        assertThrows(IllegalArgumentException.class, () -> policy(
                BlueprintCraftingDisposition.TIERED,
                Optional.of(ResearchWorkbenchTier.TIER_1),
                BlueprintCraftingPolicySource.AUTHORED_RULE,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                Set.of()));

        policy(
                BlueprintCraftingDisposition.TIERED,
                Optional.of(ResearchWorkbenchTier.TIER_3),
                BlueprintCraftingPolicySource.EXACT_RULE,
                Optional.of(id("test:exact_rule")),
                MatchSpecificity.EXACT,
                Optional.empty(),
                Optional.empty(),
                false,
                Set.of());
        policy(
                BlueprintCraftingDisposition.DISABLED,
                Optional.empty(),
                BlueprintCraftingPolicySource.AUTHORED_RULE,
                Optional.of(id("test:selector_rule")),
                MatchSpecificity.SELECTOR,
                Optional.empty(),
                Optional.empty(),
                false,
                Set.of());
    }

    @Test
    void automaticReviewFallbackRequiresRetainedScoreEvidence() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                BlueprintCraftingDisposition.DISABLED,
                Optional.empty(),
                BlueprintCraftingPolicySource.AUTOMATIC_REVIEW_FALLBACK,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                true,
                Set.of(BlueprintCraftingPolicyWarning.AUTOMATIC_REVIEW_FALLBACK)));
    }

    @Test
    void invalidAutomaticBoundsReasonCodesAndWarningCollectionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                BlueprintCraftingDisposition.TIERED,
                Optional.of(ResearchWorkbenchTier.TIER_1),
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.of(101),
                Optional.empty(),
                false,
                Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new ResolvedBlueprintCraftingPolicy(
                PROFILE,
                BLUEPRINT,
                BlueprintCraftingDisposition.TIERED,
                Optional.of(ResearchWorkbenchTier.TIER_1),
                ProgressionGateRequirements.EMPTY,
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                "Invalid Reason",
                Set.of()));
        assertThrows(IllegalArgumentException.class, () -> policy(
                BlueprintCraftingDisposition.TIERED,
                Optional.of(ResearchWorkbenchTier.TIER_1),
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                java.util.Collections.singleton(null)));
    }

    private static ResolvedBlueprintCraftingPolicy tiered(ResearchWorkbenchTier tier) {
        return policy(
                BlueprintCraftingDisposition.TIERED,
                Optional.of(tier),
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                Set.of());
    }

    private static ResolvedBlueprintCraftingPolicy policy(
            BlueprintCraftingDisposition disposition,
            Optional<ResearchWorkbenchTier> tier,
            BlueprintCraftingPolicySource source,
            Optional<ResourceLocation> ruleId,
            MatchSpecificity specificity,
            Optional<Integer> score,
            Optional<Integer> percentile,
            boolean review,
            Set<BlueprintCraftingPolicyWarning> warnings) {
        return new ResolvedBlueprintCraftingPolicy(
                PROFILE,
                BLUEPRINT,
                disposition,
                tier,
                ProgressionGateRequirements.EMPTY,
                source,
                ruleId,
                specificity,
                score,
                percentile,
                review,
                "test_assignment",
                warnings);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
