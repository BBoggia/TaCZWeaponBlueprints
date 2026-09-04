package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;

import net.minecraft.resources.ResourceLocation;

class BlueprintCraftingPolicySnapshotTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation TIERED = id("test:tiered");
    private static final ResourceLocation UNRESTRICTED = id("test:unrestricted");
    private static final ResourceLocation DISABLED = id("test:disabled");

    @Test
    void completeSnapshotComputesImmutableExplainableDiagnostics() {
        Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> policies = Map.of(
                TIERED, policy(
                        TIERED,
                        BlueprintCraftingDisposition.TIERED,
                        Optional.of(ResearchWorkbenchTier.TIER_2),
                        BlueprintCraftingPolicySource.AUTOMATIC_REVIEW_FALLBACK,
                        true,
                        Set.of(BlueprintCraftingPolicyWarning.AUTOMATIC_REVIEW_FALLBACK)),
                UNRESTRICTED, policy(
                        UNRESTRICTED,
                        BlueprintCraftingDisposition.UNRESTRICTED,
                        Optional.empty(),
                        BlueprintCraftingPolicySource.CATEGORY_DEFAULT,
                        false,
                        Set.of()),
                DISABLED, policy(
                        DISABLED,
                        BlueprintCraftingDisposition.DISABLED,
                        Optional.empty(),
                        BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                        false,
                        Set.of(BlueprintCraftingPolicyWarning.AUTHORED_OMITTED_FALLBACK)));

        BlueprintCraftingPolicySnapshot snapshot = BlueprintCraftingPolicySnapshot.create(
                4L,
                7L,
                9L,
                Set.of(TIERED, UNRESTRICTED, DISABLED),
                Map.of(PROFILE, policies));

        assertTrue(snapshot.matches(4L, 7L, 9L));
        assertFalse(snapshot.matches(4L, 7L, 10L));
        assertEquals(policies.get(TIERED), snapshot.policy(PROFILE, TIERED).orElseThrow());
        var diagnostics = snapshot.diagnosticsByProfile().get(PROFILE);
        assertEquals(3, diagnostics.assignedCount());
        assertEquals(1, diagnostics.dispositionCounts().get(
                BlueprintCraftingDisposition.TIERED));
        assertEquals(1, diagnostics.dispositionCounts().get(
                BlueprintCraftingDisposition.UNRESTRICTED));
        assertEquals(1, diagnostics.dispositionCounts().get(
                BlueprintCraftingDisposition.DISABLED));
        assertEquals(1, diagnostics.tierCounts().get(ResearchWorkbenchTier.TIER_2));
        assertEquals(1, diagnostics.sourceCounts().get(
                BlueprintCraftingPolicySource.AUTOMATIC_REVIEW_FALLBACK));
        assertEquals(1, diagnostics.warningCounts().get(
                BlueprintCraftingPolicyWarning.AUTHORED_OMITTED_FALLBACK));
        assertEquals(1, diagnostics.reviewRequiredCount());
        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.catalogBlueprintIds().add(id("test:another")));
        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.policiesByProfile().get(PROFILE).clear());
    }

    @Test
    void snapshotRejectsMissingOrExtraCatalogAssignments() {
        ResolvedBlueprintCraftingPolicy tiered = policy(
                TIERED,
                BlueprintCraftingDisposition.TIERED,
                Optional.of(ResearchWorkbenchTier.TIER_1),
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                false,
                Set.of());

        assertThrows(IllegalArgumentException.class, () ->
                BlueprintCraftingPolicySnapshot.create(
                        1L,
                        1L,
                        1L,
                        Set.of(TIERED, DISABLED),
                        Map.of(PROFILE, Map.of(TIERED, tiered))));
        assertThrows(IllegalArgumentException.class, () ->
                BlueprintCraftingPolicySnapshot.create(
                        1L,
                        1L,
                        1L,
                        Set.of(TIERED),
                        Map.of(PROFILE, Map.of(
                                TIERED, tiered,
                                DISABLED, policy(
                                        DISABLED,
                                        BlueprintCraftingDisposition.DISABLED,
                                        Optional.empty(),
                                        BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                                        false,
                                        Set.of())))));
    }

    @Test
    void snapshotRejectsMapIdentityAndDiagnosticDrift() {
        ResolvedBlueprintCraftingPolicy wrongBlueprint = policy(
                DISABLED,
                BlueprintCraftingDisposition.TIERED,
                Optional.of(ResearchWorkbenchTier.TIER_1),
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                false,
                Set.of());
        assertThrows(IllegalArgumentException.class, () ->
                BlueprintCraftingPolicySnapshot.create(
                        1L,
                        1L,
                        1L,
                        Set.of(TIERED),
                        Map.of(PROFILE, Map.of(TIERED, wrongBlueprint))));

        ResolvedBlueprintCraftingPolicy valid = policy(
                TIERED,
                BlueprintCraftingDisposition.TIERED,
                Optional.of(ResearchWorkbenchTier.TIER_1),
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                false,
                Set.of());
        var correct = BlueprintCraftingPolicySnapshot.ProfileDiagnostics.from(
                Map.of(TIERED, valid));
        var drifted = new BlueprintCraftingPolicySnapshot.ProfileDiagnostics(
                correct.assignedCount(),
                correct.dispositionCounts(),
                Map.of(
                        ResearchWorkbenchTier.TIER_1, 0,
                        ResearchWorkbenchTier.TIER_2, 1,
                        ResearchWorkbenchTier.TIER_3, 0),
                correct.sourceCounts(),
                correct.warningCounts(),
                correct.reviewRequiredCount(),
                correct.gateGroupCount(),
                correct.gateConditionCount());
        assertThrows(IllegalArgumentException.class, () ->
                new BlueprintCraftingPolicySnapshot(
                        1L,
                        1L,
                        1L,
                        Set.of(TIERED),
                        Map.of(PROFILE, Map.of(TIERED, valid)),
                        Map.of(PROFILE, drifted)));
    }

    @Test
    void snapshotRejectsProfileCatalogCrossProductAboveAggregateBudget() {
        Set<ResourceLocation> catalog = new LinkedHashSet<>();
        for (int index = 0; index < 1_021; index++) {
            catalog.add(id("test:blueprint_" + index));
        }
        Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>> profiles =
                new LinkedHashMap<>();
        for (int index = 0; index < 257; index++) {
            profiles.put(id("test:profile_" + index), Map.of());
        }

        assertThrows(IllegalArgumentException.class, () ->
                new BlueprintCraftingPolicySnapshot(
                        1L,
                        1L,
                        1L,
                        catalog,
                        profiles,
                        Map.of()));
    }

    @Test
    void snapshotBoundsEachDimensionEvenWhenTheOtherIsEmpty() {
        Set<ResourceLocation> oversizedCatalog = new LinkedHashSet<>();
        for (int index = 0; index <= BlueprintCraftingPolicySnapshot.MAX_CATALOG_ENTRIES;
                index++) {
            oversizedCatalog.add(id("test:catalog_" + index));
        }
        assertThrows(IllegalArgumentException.class, () ->
                new BlueprintCraftingPolicySnapshot(
                        1L,
                        1L,
                        0L,
                        oversizedCatalog,
                        Map.of(),
                        Map.of()));

        Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>>
                oversizedProfiles = new LinkedHashMap<>();
        for (int index = 0; index <= BlueprintCraftingPolicySnapshot.MAX_PROFILES; index++) {
            oversizedProfiles.put(id("test:profile_" + index), Map.of());
        }
        assertThrows(IllegalArgumentException.class, () ->
                new BlueprintCraftingPolicySnapshot(
                        1L,
                        1L,
                        0L,
                        Set.of(),
                        oversizedProfiles,
                        Map.of()));
    }

    @Test
    void diagnosticsRejectImpossibleTotals() {
        assertThrows(IllegalArgumentException.class, () ->
                new BlueprintCraftingPolicySnapshot.ProfileDiagnostics(
                        1,
                        Map.of(BlueprintCraftingDisposition.TIERED, 1),
                        Map.of(ResearchWorkbenchTier.TIER_1, 0),
                        Map.of(BlueprintCraftingPolicySource.PROFILE_FALLBACK, 1),
                        Map.of(),
                        0,
                        0,
                        0));
        assertThrows(IllegalArgumentException.class, () ->
                new BlueprintCraftingPolicySnapshot.ProfileDiagnostics(
                        1,
                        Map.of(BlueprintCraftingDisposition.TIERED, 1),
                        Map.of(ResearchWorkbenchTier.TIER_1, 1),
                        Map.of(BlueprintCraftingPolicySource.PROFILE_FALLBACK, 1),
                        Map.of(),
                        0,
                        ProgressionGateRequirements.MAX_GROUPS + 1,
                        0));
    }

    private static ResolvedBlueprintCraftingPolicy policy(
            ResourceLocation blueprintId,
            BlueprintCraftingDisposition disposition,
            Optional<ResearchWorkbenchTier> tier,
            BlueprintCraftingPolicySource source,
            boolean review,
            Set<BlueprintCraftingPolicyWarning> warnings) {
        return new ResolvedBlueprintCraftingPolicy(
                PROFILE,
                blueprintId,
                disposition,
                tier,
                ProgressionGateRequirements.EMPTY,
                source,
                Optional.empty(),
                MatchSpecificity.NONE,
                review ? Optional.of(50) : Optional.empty(),
                Optional.empty(),
                review,
                "test_assignment",
                warnings);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
