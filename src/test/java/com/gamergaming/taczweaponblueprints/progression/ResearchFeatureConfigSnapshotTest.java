package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentDiscount;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintFragmentProfilePolicy;

import net.minecraft.resources.ResourceLocation;

class ResearchFeatureConfigSnapshotTest {
    private static final ResourceLocation TARGET = new ResourceLocation("test:rifle");

    @Test
    void everyResearchPresetHasAnExplicitResearchAndCraftingTruthTable() {
        assertPreset(ResearchProgressionPreset.CLASSIC, false, false);
        assertPreset(ResearchProgressionPreset.TIERED_RESEARCH, true, false);
        assertPreset(ResearchProgressionPreset.TIERED_RESEARCH_AND_CRAFTING, true, true);

        assertFalse(ResearchProgressionPreset.CUSTOM.enforcesResearch(false));
        assertTrue(ResearchProgressionPreset.CUSTOM.enforcesResearch(true));
        assertFalse(ResearchProgressionPreset.CUSTOM.enforcesCrafting(false));
        assertTrue(ResearchProgressionPreset.CUSTOM.enforcesCrafting(true));
    }

    @Test
    void disabledResearchCanRetainAnIndependentCraftingTierPolicy() {
        BlueprintConfig config = new BlueprintConfig();
        config.enableResearch.validateAndSet(false);
        config.progressionPreset.validateAndSet(
                ResearchProgressionPreset.TIERED_RESEARCH_AND_CRAFTING);
        config.update(4);

        ResearchFeatureConfigSnapshot snapshot = config.researchFeatureSnapshot();

        assertFalse(config.enableResearch.get());
        assertTrue(snapshot.enforceCraftingTiers());
        assertTrue(snapshot.enforceResearchTiers());
    }

    @Test
    void disabledResearchKeepsIndependentCraftingAndFragmentControlsAvailable() {
        BlueprintConfig config = new BlueprintConfig();
        config.progressionPreset.validateAndSet(ResearchProgressionPreset.CUSTOM);
        config.fragmentPreset.validateAndSet(BlueprintFragmentPreset.CUSTOM);
        config.enableResearch.validateAndSet(false);

        assertTrue(conditionEnabled(config.progressionPreset));
        assertTrue(conditionEnabled(config.fragmentPreset));
        assertTrue(conditionEnabled(config.activeResearchProfile));
        assertTrue(conditionEnabled(config.customEnforceResearchTiers));
        assertTrue(conditionEnabled(config.customEnforceCraftingTiers));
        assertTrue(conditionEnabled(config.customFragmentMode));
        assertTrue(conditionEnabled(config.tierOneFragmentThreshold));

        config.enableBlueprints.validateAndSet(false);

        assertFalse(conditionEnabled(config.progressionPreset));
        assertFalse(conditionEnabled(config.fragmentPreset));
        assertFalse(conditionEnabled(config.activeResearchProfile));
        assertFalse(conditionEnabled(config.customEnforceCraftingTiers));
        assertFalse(conditionEnabled(config.customFragmentMode));
    }

    @Test
    void customPresetSupportsCraftingOnlyTierEnforcement() {
        BlueprintConfig config = new BlueprintConfig();
        config.progressionPreset.validateAndSet(ResearchProgressionPreset.CUSTOM);
        config.customEnforceResearchTiers.validateAndSet(false);
        config.customEnforceCraftingTiers.validateAndSet(true);
        config.update(4);

        ResearchFeatureConfigSnapshot snapshot = config.researchFeatureSnapshot();

        assertFalse(snapshot.enforceResearchTiers());
        assertTrue(snapshot.enforceCraftingTiers());
    }

    @Test
    void fragmentPresetsRemainMutuallyExclusiveAcrossAllWorkbenchTiers() {
        ResearchFeatureConfigSnapshot base = new BlueprintConfig().researchFeatureSnapshot();

        for (ResearchWorkbenchTier tier : ResearchWorkbenchTier.values()) {
            BlueprintFragmentPolicy disabled = withFragmentPreset(
                    base, BlueprintFragmentPreset.DISABLED,
                    BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST)
                    .fragmentPolicy(BlueprintFragmentProfilePolicy.DEFAULT, TARGET, tier, Optional.empty());
            assertFalse(disabled.enabled());

            BlueprintFragmentPolicy boost = withFragmentPreset(
                    base, BlueprintFragmentPreset.TARGETED_RESEARCH_BOOST,
                    BlueprintFragmentPolicy.CompletionMode.RECONSTRUCT_BLUEPRINT)
                    .fragmentPolicy(BlueprintFragmentProfilePolicy.DEFAULT, TARGET, tier, Optional.empty());
            assertEquals(BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST,
                    boost.completionMode());
            assertTrue(boost.researchDiscount().mode()
                    != BlueprintFragmentDiscount.Mode.NONE);

            BlueprintFragmentPolicy reconstruction = withFragmentPreset(
                    base, BlueprintFragmentPreset.RECONSTRUCT_BLUEPRINT,
                    BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST)
                    .fragmentPolicy(BlueprintFragmentProfilePolicy.DEFAULT, TARGET, tier, Optional.empty());
            assertEquals(BlueprintFragmentPolicy.CompletionMode.RECONSTRUCT_BLUEPRINT,
                    reconstruction.completionMode());
            assertEquals(BlueprintFragmentDiscount.NONE, reconstruction.researchDiscount());
        }
    }

    @Test
    void exactFragmentThresholdWinsWithoutChangingTheSelectedCompletionMode() {
        ResearchFeatureConfigSnapshot base = new BlueprintConfig().researchFeatureSnapshot();
        ResearchFeatureConfigSnapshot exact = new ResearchFeatureConfigSnapshot(
                base.progressionPreset(),
                base.enforceResearchTiers(),
                base.enforceCraftingTiers(),
                BlueprintFragmentPreset.RECONSTRUCT_BLUEPRINT,
                base.automaticTierPercentiles(),
                base.externalWorkstationTiers(),
                base.unknownWorkstationFallbackTier(),
                base.unknownExternalWorkstationsUnrestricted(),
                base.creativeBypassesWorkbenchTiers(),
                base.creativeBypassesProgressionGates(),
                java.util.Map.of(TARGET, 37),
                base.customFragmentMode(),
                base.customFragmentThresholds(),
                base.customFragmentRetainedCap(),
                base.customFragmentDiscount(),
                base.customLearnedTargetResearchPoints(),
                base.fragmentLootReplacementBasisPoints(),
                base.craftingPolicy());

        BlueprintFragmentPolicy policy = exact.fragmentPolicy(
                BlueprintFragmentProfilePolicy.DEFAULT,
                TARGET,
                ResearchWorkbenchTier.TIER_1,
                Optional.of(12));

        assertEquals(37, policy.threshold());
        assertEquals(BlueprintFragmentPolicy.CompletionMode.RECONSTRUCT_BLUEPRINT,
                policy.completionMode());
        assertEquals(BlueprintFragmentDiscount.NONE, policy.researchDiscount());
        assertTrue(policy.retainedProgressCap() >= policy.threshold());
    }

    @Test
    void ruleThresholdRaisesTheCustomRetentionCapInsteadOfBreakingPublication() {
        ResearchFeatureConfigSnapshot base = new BlueprintConfig().researchFeatureSnapshot();
        ResearchFeatureConfigSnapshot custom = new ResearchFeatureConfigSnapshot(
                base.progressionPreset(),
                base.enforceResearchTiers(),
                base.enforceCraftingTiers(),
                BlueprintFragmentPreset.CUSTOM,
                base.automaticTierPercentiles(),
                base.externalWorkstationTiers(),
                base.unknownWorkstationFallbackTier(),
                base.unknownExternalWorkstationsUnrestricted(),
                base.creativeBypassesWorkbenchTiers(),
                base.creativeBypassesProgressionGates(),
                java.util.Map.of(),
                BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST,
                base.customFragmentThresholds(),
                20,
                base.customFragmentDiscount(),
                base.customLearnedTargetResearchPoints(),
                base.fragmentLootReplacementBasisPoints(),
                base.craftingPolicy());

        BlueprintFragmentPolicy policy = custom.fragmentPolicy(
                BlueprintFragmentProfilePolicy.DEFAULT,
                TARGET,
                ResearchWorkbenchTier.TIER_1,
                Optional.of(37));

        assertEquals(37, policy.threshold());
        assertEquals(37, policy.retainedProgressCap());
    }

    @Test
    void profileRuleThresholdRaisesTheProfileRetentionCap() {
        BlueprintFragmentProfilePolicy profile = new BlueprintFragmentProfilePolicy(
                BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST,
                java.util.Map.of(
                        ResearchWorkbenchTier.TIER_1, 5,
                        ResearchWorkbenchTier.TIER_2, 10,
                        ResearchWorkbenchTier.TIER_3, 15),
                20,
                BlueprintFragmentProfilePolicy.DEFAULT.researchDiscount(),
                1);

        BlueprintFragmentPolicy policy = profile.resolve(
                ResearchWorkbenchTier.TIER_1, Optional.of(37));

        assertEquals(37, policy.threshold());
        assertEquals(37, policy.retainedProgressCap());
    }

    @Test
    void learnedTargetFragmentPointsAreBoundedAtTheSnapshotBoundary() {
        ResearchFeatureConfigSnapshot base = new BlueprintConfig().researchFeatureSnapshot();

        assertThrows(IllegalArgumentException.class, () -> new ResearchFeatureConfigSnapshot(
                base.progressionPreset(),
                base.enforceResearchTiers(),
                base.enforceCraftingTiers(),
                base.fragmentPreset(),
                base.automaticTierPercentiles(),
                base.externalWorkstationTiers(),
                base.unknownWorkstationFallbackTier(),
                base.unknownExternalWorkstationsUnrestricted(),
                base.creativeBypassesWorkbenchTiers(),
                base.creativeBypassesProgressionGates(),
                base.exactFragmentThresholds(),
                base.customFragmentMode(),
                base.customFragmentThresholds(),
                base.customFragmentRetainedCap(),
                base.customFragmentDiscount(),
                PlayerProgressionLimits.MAX_RESEARCH_POINTS + 1,
                base.fragmentLootReplacementBasisPoints(),
                base.craftingPolicy()));
    }

    @Test
    void runtimeSwitchesDoNotInvalidateTheResolvedPolicyShape() {
        BlueprintConfig config = new BlueprintConfig();
        config.update(3);
        ResearchFeatureConfigSnapshot before = config.researchFeatureSnapshot();

        config.creativeBypassesWorkbenchTiers.validateAndSet(
                !config.creativeBypassesWorkbenchTiers.get());
        config.creativeBypassesProgressionGates.validateAndSet(
                !config.creativeBypassesProgressionGates.get());
        config.fragmentLootReplacementPercent.validateAndSet(
                config.fragmentLootReplacementPercent.get() + 1);
        config.tierOneFragmentThreshold.validateAndSet(
                config.tierOneFragmentThreshold.get() + 1);
        config.update(3);
        ResearchFeatureConfigSnapshot runtimeOnly = config.researchFeatureSnapshot();

        assertEquals(before.policyShape(), runtimeOnly.policyShape());
        assertTrue(before.hasSameExternalWorkbenchResolution(runtimeOnly));

        config.externalWorkstationTiers.validateAndSet(java.util.Map.of(
                "test:external_bench", ResearchWorkbenchTier.TIER_3));
        config.update(3);
        ResearchFeatureConfigSnapshot remapped = config.researchFeatureSnapshot();
        assertEquals(before.policyShape(), remapped.policyShape());
        assertFalse(before.hasSameExternalWorkbenchResolution(remapped));
    }

    @Test
    void disabledFragmentInputsRemainDormantInTheResolvedPolicyShape() {
        BlueprintConfig config = new BlueprintConfig();
        config.fragmentPreset.validateAndSet(BlueprintFragmentPreset.DISABLED);
        config.update(3);
        ResearchPolicyShapeSnapshot disabled = config.researchFeatureSnapshot().policyShape();

        config.exactFragmentThresholds.validateAndSet(java.util.Map.of(
                TARGET.toString(), 37));
        config.tierOneFragmentThreshold.validateAndSet(
                config.tierOneFragmentThreshold.get() + 1);
        config.update(3);

        assertEquals(disabled, config.researchFeatureSnapshot().policyShape());

        config.fragmentPreset.validateAndSet(BlueprintFragmentPreset.CUSTOM);
        config.customFragmentMode.validateAndSet(
                BlueprintFragmentPolicy.CompletionMode.DISABLED);
        config.update(3);
        ResearchPolicyShapeSnapshot customDisabled =
                config.researchFeatureSnapshot().policyShape();

        config.exactFragmentThresholds.validateAndSet(java.util.Map.of(
                TARGET.toString(), 41));
        config.tierTwoFragmentThreshold.validateAndSet(
                config.tierTwoFragmentThreshold.get() + 1);
        config.update(3);

        assertEquals(customDisabled, config.researchFeatureSnapshot().policyShape());
    }

    @Test
    void tierAndFragmentResolutionInputsInvalidateThePolicyShape() {
        BlueprintConfig config = new BlueprintConfig();
        config.update(3);
        ResearchPolicyShapeSnapshot before = config.researchFeatureSnapshot().policyShape();

        config.tierOneUpperPercent.validateAndSet(
                config.tierOneUpperPercent.get() + 1);
        config.update(3);

        assertFalse(before.equals(config.researchFeatureSnapshot().policyShape()));
    }

    @Test
    void craftingAssignmentControlsInvalidateThePolicyShape() {
        BlueprintConfig config = new BlueprintConfig();
        config.update(4);
        ResearchPolicyShapeSnapshot before = config.researchFeatureSnapshot().policyShape();

        config.ammoCraftingStrategy.validateAndSet(AmmoCraftingStrategy.TIER_3);
        config.update(4);

        assertFalse(before.equals(config.researchFeatureSnapshot().policyShape()));
    }

    @Test
    void dormantLinkedAmmoFallbackDoesNotInvalidateThePolicyShape() {
        BlueprintConfig config = new BlueprintConfig();
        config.update(4);
        ResearchPolicyShapeSnapshot before = config.researchFeatureSnapshot().policyShape();

        config.linkedAmmoFallbackTier.validateAndSet(ResearchWorkbenchTier.TIER_3);
        config.update(4);

        assertEquals(before, config.researchFeatureSnapshot().policyShape());

        config.ammoCraftingStrategy.validateAndSet(AmmoCraftingStrategy.LINKED_WEAPON);
        config.update(4);
        assertFalse(before.equals(config.researchFeatureSnapshot().policyShape()));
        assertEquals(
                ResearchWorkbenchTier.TIER_3,
                config.researchFeatureSnapshot().craftingPolicy().linkedAmmoFallbackTier());
    }

    private static void assertPreset(
            ResearchProgressionPreset preset,
            boolean research,
            boolean crafting) {
        assertEquals(research, preset.enforcesResearch(!research));
        assertEquals(crafting, preset.enforcesCrafting(!crafting));
    }

    private static boolean conditionEnabled(
            me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition<?> field) {
        return field.getConditions$fzzy_config().stream()
                .allMatch(condition -> condition.get());
    }

    private static ResearchFeatureConfigSnapshot withFragmentPreset(
            ResearchFeatureConfigSnapshot base,
            BlueprintFragmentPreset preset,
            BlueprintFragmentPolicy.CompletionMode customMode) {
        return new ResearchFeatureConfigSnapshot(
                base.progressionPreset(),
                base.enforceResearchTiers(),
                base.enforceCraftingTiers(),
                preset,
                base.automaticTierPercentiles(),
                base.externalWorkstationTiers(),
                base.unknownWorkstationFallbackTier(),
                base.unknownExternalWorkstationsUnrestricted(),
                base.creativeBypassesWorkbenchTiers(),
                base.creativeBypassesProgressionGates(),
                base.exactFragmentThresholds(),
                customMode,
                base.customFragmentThresholds(),
                base.customFragmentRetainedCap(),
                customMode == BlueprintFragmentPolicy.CompletionMode.RECONSTRUCT_BLUEPRINT
                        ? BlueprintFragmentDiscount.NONE
                        : base.customFragmentDiscount(),
                base.customLearnedTargetResearchPoints(),
                base.fragmentLootReplacementBasisPoints(),
                base.craftingPolicy());
    }
}
