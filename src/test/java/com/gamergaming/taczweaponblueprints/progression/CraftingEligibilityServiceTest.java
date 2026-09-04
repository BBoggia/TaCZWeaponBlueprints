package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.workbench.CraftingWorkbenchTierResolver;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintCraftingDisposition;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintCraftingPolicySource;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintCraftingPolicy;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

class CraftingEligibilityServiceTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation BLUEPRINT = id("test:blueprint");

    @Test
    void tieredPoliciesFollowTheCompleteWorkbenchMatrix() {
        for (ResearchWorkbenchTier required : ResearchWorkbenchTier.values()) {
            ResolvedBlueprintCraftingPolicy policy = tiered(required);
            for (ResearchWorkbenchTier available : ResearchWorkbenchTier.values()) {
                CraftingEligibilityService.Status expected = available.satisfies(required)
                        ? CraftingEligibilityService.Status.ALLOWED
                        : CraftingEligibilityService.Status.WORKBENCH_TIER_REQUIRED;
                assertEquals(expected, access(policy, true, available, false, false),
                        () -> "required=" + required + ", available=" + available);
            }
        }
    }

    @Test
    void explicitDispositionIsIndependentFromTierBypasses() {
        ResolvedBlueprintCraftingPolicy unrestricted = policy(
                BlueprintCraftingDisposition.UNRESTRICTED, Optional.empty());
        ResolvedBlueprintCraftingPolicy disabled = policy(
                BlueprintCraftingDisposition.DISABLED, Optional.empty());

        for (ResearchWorkbenchTier available : ResearchWorkbenchTier.values()) {
            assertEquals(CraftingEligibilityService.Status.ALLOWED,
                    access(unrestricted, true, available, false, false));
            assertEquals(CraftingEligibilityService.Status.CRAFTING_DISABLED,
                    access(disabled, true, available, false, false));
            assertEquals(CraftingEligibilityService.Status.CRAFTING_DISABLED,
                    access(disabled, false, available, true, true));
        }
    }

    @Test
    void namedTierBypassesOnlyBypassTheOrdinaryTierRequirement() {
        ResolvedBlueprintCraftingPolicy tierThree = tiered(ResearchWorkbenchTier.TIER_3);

        assertEquals(CraftingEligibilityService.Status.WORKBENCH_TIER_REQUIRED,
                access(tierThree, true, ResearchWorkbenchTier.TIER_1, false, false));
        assertEquals(CraftingEligibilityService.Status.ALLOWED,
                access(tierThree, false, ResearchWorkbenchTier.TIER_1, false, false));
        assertEquals(CraftingEligibilityService.Status.ALLOWED,
                access(tierThree, true, ResearchWorkbenchTier.TIER_1, true, false));
        assertEquals(CraftingEligibilityService.Status.ALLOWED,
                access(tierThree, true, ResearchWorkbenchTier.TIER_1, false, true));
    }

    @Test
    void missingCraftingPolicyFailsClosedWithItsOwnStatus() {
        assertEquals(CraftingEligibilityService.Status.CRAFTING_POLICY_MISSING,
                access(null, false, ResearchWorkbenchTier.TIER_3, true, true));
    }

    @Test
    void onlyTheCanonicalCatalogRecipeCanReachKnowledgeAndPolicyChecks() {
        ResourceLocation canonical = id("test:recipe/canonical");
        ResourceLocation alias = id("legacy:recipe/alias");

        assertEquals(CraftingEligibilityService.Status.ALLOWED,
                CraftingEligibilityService.evaluateRecipeIdentity(
                        canonical, canonical, BLUEPRINT));
        assertEquals(CraftingEligibilityService.Status.UNKNOWN_RECIPE,
                CraftingEligibilityService.evaluateRecipeIdentity(
                        alias, canonical, BLUEPRINT));
        assertEquals(CraftingEligibilityService.Status.UNKNOWN_RECIPE,
                CraftingEligibilityService.evaluateRecipeIdentity(
                        id("test:recipe/missing"), null, null));
    }

    @Test
    void everyProgressionPresetProducesTheExpectedWorkbenchDecision() {
        ResolvedBlueprintCraftingPolicy tierThree = tiered(ResearchWorkbenchTier.TIER_3);
        for (ResearchProgressionPreset preset : ResearchProgressionPreset.values()) {
            for (boolean customCrafting : new boolean[] {false, true}) {
                boolean enforce = preset.enforcesCrafting(customCrafting);
                CraftingEligibilityService.Status expected = enforce
                        ? CraftingEligibilityService.Status.WORKBENCH_TIER_REQUIRED
                        : CraftingEligibilityService.Status.ALLOWED;
                assertEquals(expected, access(
                        tierThree,
                        enforce,
                        ResearchWorkbenchTier.TIER_1,
                        false,
                        false),
                        () -> "preset=" + preset + ", customCrafting=" + customCrafting);
            }
        }
    }

    @Test
    void creativeTierBypassMatrixNeverOverridesAnExplicitDisabledPolicy() {
        ResolvedBlueprintCraftingPolicy tierThree = tiered(ResearchWorkbenchTier.TIER_3);
        ResolvedBlueprintCraftingPolicy disabled = policy(
                BlueprintCraftingDisposition.DISABLED, Optional.empty());

        for (boolean creative : new boolean[] {false, true}) {
            for (boolean configuredBypass : new boolean[] {false, true}) {
                boolean bypassTier = creative && configuredBypass;
                CraftingEligibilityService.Status expected = bypassTier
                        ? CraftingEligibilityService.Status.ALLOWED
                        : CraftingEligibilityService.Status.WORKBENCH_TIER_REQUIRED;
                assertEquals(expected, access(
                        tierThree,
                        true,
                        ResearchWorkbenchTier.TIER_1,
                        false,
                        bypassTier));
                assertEquals(CraftingEligibilityService.Status.CRAFTING_DISABLED,
                        access(disabled,
                                true,
                                ResearchWorkbenchTier.TIER_3,
                                true,
                                bypassTier));
            }
        }
    }

    @Test
    void dedicatedServerWorkbenchSmokeMatrixAllowsOnlySufficientNativeLevels() {
        ResourceLocation[] workstationIds = {
                CraftingWorkbenchTierResolver.TIER_1_ID,
                CraftingWorkbenchTierResolver.TIER_2_ID,
                CraftingWorkbenchTierResolver.TIER_3_ID
        };
        ResearchWorkbenchTier[] levels = ResearchWorkbenchTier.values();
        var config = new com.gamergaming.taczweaponblueprints.compat.fzzy_config
                .BlueprintConfig().researchFeatureSnapshot();

        for (int workstationIndex = 0;
                workstationIndex < workstationIds.length;
                workstationIndex++) {
            var workstation = CraftingWorkbenchTierResolver.resolve(
                    workstationIds[workstationIndex], config);
            assertEquals(levels[workstationIndex], workstation.tier());
            assertFalse(workstation.unrestricted());
            for (ResearchWorkbenchTier required : levels) {
                CraftingEligibilityService.Status expected =
                        workstation.tier().satisfies(required)
                                ? CraftingEligibilityService.Status.ALLOWED
                                : CraftingEligibilityService.Status.WORKBENCH_TIER_REQUIRED;
                assertEquals(expected, access(
                        tiered(required),
                        true,
                        workstation.tier(),
                        workstation.unrestricted(),
                        false));
            }
        }
    }

    @Test
    void mappedFallbackAndUnrestrictedExternalWorkstationsUseDistinctAccessModes() {
        ResourceLocation mappedId = id("example:mapped_workbench");
        ResourceLocation unknownId = id("example:unknown_workbench");
        var config = new com.gamergaming.taczweaponblueprints.compat.fzzy_config
                .BlueprintConfig();
        config.externalWorkstationTiers.validateAndSet(
                java.util.Map.of(mappedId.toString(), ResearchWorkbenchTier.TIER_2));
        config.unknownWorkstationFallbackTier.validateAndSet(
                ResearchWorkbenchTier.TIER_1);
        config.unknownExternalWorkstationsUnrestricted.validateAndSet(false);
        config.update(4);

        var mapped = CraftingWorkbenchTierResolver.resolve(
                mappedId, config.researchFeatureSnapshot());
        var fallback = CraftingWorkbenchTierResolver.resolve(
                unknownId, config.researchFeatureSnapshot());
        assertEquals(CraftingEligibilityService.Status.WORKBENCH_TIER_REQUIRED,
                access(tiered(ResearchWorkbenchTier.TIER_3),
                        true, mapped.tier(), mapped.unrestricted(), false));
        assertEquals(CraftingEligibilityService.Status.WORKBENCH_TIER_REQUIRED,
                access(tiered(ResearchWorkbenchTier.TIER_2),
                        true, fallback.tier(), fallback.unrestricted(), false));

        config.unknownExternalWorkstationsUnrestricted.validateAndSet(true);
        config.update(4);
        var unrestricted = CraftingWorkbenchTierResolver.resolve(
                unknownId, config.researchFeatureSnapshot());
        assertTrue(unrestricted.unrestricted());
        assertEquals(CraftingEligibilityService.Status.ALLOWED,
                access(tiered(ResearchWorkbenchTier.TIER_3),
                        true,
                        unrestricted.tier(),
                        unrestricted.unrestricted(),
                        false));
    }

    @Test
    void authoritativeSnapshotsRequireTheIdentityThatProducedTheirDecision() {
        assertThrows(IllegalArgumentException.class,
                () -> new CraftingEligibilityService.Snapshot(
                        CraftingEligibilityService.Status.ALLOWED,
                        Set.of("test:recipe"),
                        Optional.empty()));

        CraftingEligibilityService.AccessIdentity identity = activeIdentity();
        var snapshot = new CraftingEligibilityService.Snapshot(
                CraftingEligibilityService.Status.ALLOWED,
                Set.of("test:recipe"),
                Optional.of(identity));

        assertEquals(Optional.of(identity), snapshot.accessIdentity());
    }

    @Test
    void classicIdentityCannotClaimPolicyOrBypassInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new CraftingEligibilityService.AccessIdentity(
                        1L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        PROFILE,
                        id("test:workbench"),
                        ResearchWorkbenchTier.TIER_1,
                        false,
                        true,
                        false,
                        false,
                        false));
    }

    @Test
    void everyServerDecisionHasPlayerFacingCopy() throws IOException {
        JsonObject translations = JsonParser.parseString(Files.readString(Path.of(
                System.getProperty("user.dir"),
                "src/main/resources/assets/taczweaponblueprints/lang/en_us.json")))
                .getAsJsonObject();

        for (CraftingEligibilityService.Status status
                : CraftingEligibilityService.Status.values()) {
            assertTrue(translations.has(status.translationKey()), status.translationKey());
        }
    }

    private static CraftingEligibilityService.Status access(
            ResolvedBlueprintCraftingPolicy policy,
            boolean enforceTiers,
            ResearchWorkbenchTier available,
            boolean unrestrictedWorkbench,
            boolean bypassTier) {
        return CraftingEligibilityService.evaluateWorkbenchAccess(
                policy,
                enforceTiers,
                available,
                unrestrictedWorkbench,
                bypassTier);
    }

    private static ResolvedBlueprintCraftingPolicy tiered(ResearchWorkbenchTier tier) {
        return policy(BlueprintCraftingDisposition.TIERED, Optional.of(tier));
    }

    private static ResolvedBlueprintCraftingPolicy policy(
            BlueprintCraftingDisposition disposition,
            Optional<ResearchWorkbenchTier> tier) {
        return new ResolvedBlueprintCraftingPolicy(
                PROFILE,
                BLUEPRINT,
                disposition,
                tier,
                ProgressionGateRequirements.EMPTY,
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                "test_assignment",
                Set.of());
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }

    private static CraftingEligibilityService.AccessIdentity activeIdentity() {
        return new CraftingEligibilityService.AccessIdentity(
                1L,
                2L,
                3L,
                4L,
                5L,
                6L,
                PROFILE,
                id("test:workbench"),
                ResearchWorkbenchTier.TIER_2,
                false,
                true,
                false,
                false,
                true);
    }
}
