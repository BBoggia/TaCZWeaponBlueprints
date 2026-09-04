package com.gamergaming.taczweaponblueprints.progression.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.progression.ResearchFeatureConfigSnapshot;

import net.minecraft.resources.ResourceLocation;

class CraftingWorkbenchTierResolverTest {
    @Test
    void nativeBenchTiersCannotBeOverriddenByExternalMappings() {
        ResearchFeatureConfigSnapshot config = withExternalPolicy(
                Map.of(CraftingWorkbenchTierResolver.TIER_1_ID,
                        ResearchWorkbenchTier.TIER_3),
                ResearchWorkbenchTier.TIER_2,
                true);

        var result = CraftingWorkbenchTierResolver.resolve(
                CraftingWorkbenchTierResolver.TIER_1_ID, config);

        assertEquals(ResearchWorkbenchTier.TIER_1, result.tier());
        assertEquals(CraftingWorkbenchTierResolver.Source.NATIVE_CRAFTING_WORKBENCH,
                result.source());
        assertFalse(result.unrestricted());
    }

    @Test
    void exactExternalMappingWinsBeforeFallback() {
        ResourceLocation workstation = new ResourceLocation("example:assembler");
        ResearchFeatureConfigSnapshot config = withExternalPolicy(
                Map.of(workstation, ResearchWorkbenchTier.TIER_2),
                ResearchWorkbenchTier.TIER_1,
                true);

        var result = CraftingWorkbenchTierResolver.resolve(workstation, config);

        assertEquals(ResearchWorkbenchTier.TIER_2, result.tier());
        assertEquals(CraftingWorkbenchTierResolver.Source.EXACT_EXTERNAL_MAPPING,
                result.source());
        assertFalse(result.unrestricted());
    }

    @Test
    void unknownExternalPolicyIsExplicitAndDeterministic() {
        ResourceLocation workstation = new ResourceLocation("example:unknown");
        var fallback = CraftingWorkbenchTierResolver.resolve(
                workstation,
                withExternalPolicy(Map.of(), ResearchWorkbenchTier.TIER_2, false));
        var unrestricted = CraftingWorkbenchTierResolver.resolve(
                workstation,
                withExternalPolicy(Map.of(), ResearchWorkbenchTier.TIER_1, true));

        assertEquals(ResearchWorkbenchTier.TIER_2, fallback.tier());
        assertFalse(fallback.unrestricted());
        assertEquals(ResearchWorkbenchTier.TIER_3, unrestricted.tier());
        assertTrue(unrestricted.unrestricted());
    }

    private static ResearchFeatureConfigSnapshot withExternalPolicy(
            Map<ResourceLocation, ResearchWorkbenchTier> mappings,
            ResearchWorkbenchTier fallback,
            boolean unrestricted) {
        ResearchFeatureConfigSnapshot base = ResearchFeatureConfigSnapshot.from(
                new BlueprintConfig());
        return new ResearchFeatureConfigSnapshot(
                base.progressionPreset(),
                base.enforceResearchTiers(),
                base.enforceCraftingTiers(),
                base.fragmentPreset(),
                base.automaticTierPercentiles(),
                mappings,
                fallback,
                unrestricted,
                base.creativeBypassesWorkbenchTiers(),
                base.creativeBypassesProgressionGates(),
                base.exactFragmentThresholds(),
                base.customFragmentMode(),
                base.customFragmentThresholds(),
                base.customFragmentRetainedCap(),
                base.customFragmentDiscount(),
                base.customLearnedTargetResearchPoints(),
                base.fragmentLootReplacementBasisPoints(),
                base.craftingPolicy());
    }
}
