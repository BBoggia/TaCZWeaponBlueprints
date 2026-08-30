package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDiagnostics;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class BlueprintSetupAssistantTest {
    @Test
    void standardCatalogRecommendsBalancedAndFlagsMissingStarter() {
        ResourceLocation gun = id("tacz:pistol");
        ResourceLocation ammo = id("addon:round");
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                gun, data(gun, BlueprintKind.GUN),
                ammo, data(ammo, BlueprintKind.AMMO));
        BlueprintAccessConfigSnapshot access = new BlueprintAccessConfigSnapshot(
                Set.of(gun), Set.of(), Set.of(), Set.of(gun, id("missing:start")));
        var assessment = BlueprintSetupAssistant.assess(catalog, healthyAudit(2), access);

        assertEquals(BlueprintSetupAssistant.Status.REVIEW_REQUIRED, assessment.status());
        assertEquals(BlueprintBalancePreset.BALANCED, assessment.recommendedPreset());
        assertEquals(1, assessment.kindCount(BlueprintKind.GUN));
        assertEquals(1, assessment.kindCount(BlueprintKind.AMMO));
        assertEquals(1, assessment.addOnBlueprintCount());
        assertEquals(List.of("addon", "tacz"), assessment.namespaces());
        assertEquals(1, assessment.effectiveExemptionCount());
        assertEquals(0, assessment.unmatchedExemptionSelectorCount());
        assertEquals(2, assessment.configuredStartingCount());
        assertEquals(1, assessment.missingStartingCount());
        assertEquals(1, assessment.effectiveDiscoveryCount());
        assertEquals(1, assessment.effectiveAddOnDiscoveryCount());
        assertTrue(assessment.reasons().contains("missing_starting_blueprints"));
    }

    @Test
    void addonHeavyCatalogRecommendsAccessibleButDoesNotHideStructuralReview() {
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        for (int index = 0; index < BlueprintSetupAssistant.ADDON_HEAVY_THRESHOLD; index++) {
            ResourceLocation id = id("pack:gun_" + index);
            catalog.put(id, data(id, BlueprintKind.GUN));
        }
        BlueprintResearchDiagnostics.Audit audit = new BlueprintResearchDiagnostics.Audit(
                catalog.size(),
                catalog.size(),
                catalog.size(),
                List.of(),
                1,
                1,
                1,
                List.of(),
                List.of(id("pack:missing")),
                List.of(),
                List.of(),
                List.of());
        var assessment = BlueprintSetupAssistant.assess(
                catalog, audit, BlueprintAccessConfigSnapshot.EMPTY);

        assertEquals(BlueprintSetupAssistant.Status.REVIEW_REQUIRED, assessment.status());
        assertEquals(BlueprintBalancePreset.ACCESSIBLE, assessment.recommendedPreset());
        assertTrue(assessment.reasons().contains("large_or_addon_heavy_discovery_workload"));
        assertTrue(assessment.reasons().contains("research_structure_needs_review"));
    }

    @Test
    void exemptionsAndStartingKnowledgeReduceTheRecommendationWorkload() {
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        Set<ResourceLocation> exemptions = new java.util.LinkedHashSet<>();
        for (int index = 0; index < BlueprintSetupAssistant.LARGE_CATALOG_THRESHOLD; index++) {
            ResourceLocation id = id("pack:gun_" + index);
            catalog.put(id, data(id, BlueprintKind.GUN));
            exemptions.add(id);
        }
        BlueprintAccessConfigSnapshot access = new BlueprintAccessConfigSnapshot(
                exemptions, Set.of(), Set.of(), Set.of());

        var assessment = BlueprintSetupAssistant.assess(
                catalog, healthyAudit(catalog.size()), access);

        assertEquals(BlueprintBalancePreset.BALANCED, assessment.recommendedPreset());
        assertEquals(0, assessment.effectiveDiscoveryCount());
        assertEquals(0, assessment.effectiveAddOnDiscoveryCount());
        assertTrue(assessment.reasons().contains("no_remaining_discovery_workload"));
    }

    @Test
    void disabledOrUnavailableRuntimeIsReportedInsteadOfReady() {
        ResourceLocation gun = id("tacz:pistol");
        var assessment = BlueprintSetupAssistant.assess(
                Map.of(gun, data(gun, BlueprintKind.GUN)),
                healthyAudit(1),
                BlueprintAccessConfigSnapshot.EMPTY,
                new BlueprintSetupAssistant.RuntimeReadiness(false, false, false));

        assertEquals(BlueprintSetupAssistant.Status.BLOCKED, assessment.status());
        assertTrue(assessment.reasons().contains("blueprints_disabled"));
        assertTrue(assessment.reasons().contains("research_disabled"));
        assertTrue(assessment.reasons().contains("loot_distribution_unavailable"));
    }

    @Test
    void emptyCatalogBlocksSetupAndExportIsDeterministicAndAuthorityNeutral() {
        var assessment = BlueprintSetupAssistant.assess(
                Map.of(), BlueprintResearchDiagnostics.Audit.empty(), BlueprintAccessConfigSnapshot.EMPTY);
        assertEquals(BlueprintSetupAssistant.Status.BLOCKED, assessment.status());

        String first = BlueprintSetupAssistant.export(
                assessment,
                4,
                9,
                BlueprintBalanceSettings.resolve(
                        BlueprintBalancePreset.CUSTOM,
                        JournalVisibility.FULL,
                        0.2,
                        1,
                        2));
        String second = BlueprintSetupAssistant.export(
                assessment,
                4,
                9,
                BlueprintBalanceSettings.resolve(
                        BlueprintBalancePreset.CUSTOM,
                        JournalVisibility.FULL,
                        0.2,
                        1,
                        2));
        assertEquals(first, second);
        assertTrue(first.contains("\"status\": \"blocked\""));
        assertTrue(first.contains("\"recommended_preset\": \"balanced\""));
        assertTrue(first.contains("\"effective_discovery_entries\": 0"));
        assertTrue(first.contains("\"loot_distribution_available\": true"));
        assertFalse(first.contains("learned_blueprints"));
    }

    private static BlueprintResearchDiagnostics.Audit healthyAudit(int size) {
        return new BlueprintResearchDiagnostics.Audit(
                size, size, size, List.of(), 1, 1, 1, List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    private static BlueprintData data(ResourceLocation id, BlueprintKind kind) {
        return new BlueprintData(
                id.toString(),
                "name." + id.getPath(),
                "tooltip." + id.getPath(),
                id,
                null,
                kind.serializedName(),
                null,
                kind,
                1);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
