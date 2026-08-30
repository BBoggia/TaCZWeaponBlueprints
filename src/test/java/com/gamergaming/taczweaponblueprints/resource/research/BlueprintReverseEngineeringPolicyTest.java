package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode;

import net.minecraft.resources.ResourceLocation;

class BlueprintReverseEngineeringPolicyTest {
    private static final ResourceLocation PROFILE = id("test:profile");

    @AfterEach
    void clearResolverCache() {
        BlueprintResearchPolicyResolver.clearCache();
    }

    @Test
    void existingRulePrecedenceAppliesFieldLevelReverseOverlay() {
        ResourceLocation blueprintId = id("addon_namespace:rifle");
        BlueprintReverseEngineeringOverride override = new BlueprintReverseEngineeringOverride(
                Optional.empty(),
                Optional.of(2),
                Optional.of(new BlueprintResearchCost(3, List.of())),
                Optional.empty(),
                Optional.of(false),
                Optional.of(PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES),
                Optional.empty(),
                Optional.empty());
        BlueprintResearchRule rule = new BlueprintResearchRule(
                1,
                PROFILE,
                20,
                new BlueprintResearchTarget(List.of(blueprintId), List.of(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(override));
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile(BlueprintReverseEngineeringPolicy.DEFAULT)),
                Map.of(id("test:reverse_override"), rule));
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                blueprintId,
                data(blueprintId));

        BlueprintReverseEngineeringPolicy resolved =
                BlueprintResearchPolicyResolver.reverseEngineeringPolicyFor(
                        snapshot,
                        catalog,
                        PROFILE,
                        blueprintId);

        assertEquals(2, resolved.inputCount().orElseThrow());
        assertEquals(3, resolved.cost().points());
        assertEquals(false, resolved.allowModified());
        assertEquals(
                PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES,
                resolved.physicalBlueprintLearningMode());
        assertEquals(false, resolved.outputRecyclable());
    }

    @Test
    void reverseAuditReportsUnmatchedSelectorsBlockedTargetsAndExpertLoops() {
        ResourceLocation liveId = id("addon_namespace:live_rifle");
        ResourceLocation missingId = id("removed_pack:missing_rifle");
        BlueprintReverseEngineeringPolicy expert = new BlueprintReverseEngineeringPolicy(
                true,
                Optional.empty(),
                new BlueprintResearchCost(0, List.of()),
                true,
                true,
                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                true,
                true);
        BlueprintResearchRule unmatched = new BlueprintResearchRule(
                1,
                PROFILE,
                0,
                new BlueprintResearchTarget(List.of(missingId), List.of(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new BlueprintReverseEngineeringOverride(
                        Optional.of(false),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty())));
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile(expert)),
                Map.of(id("test:missing_reverse_target"), unmatched));

        BlueprintResearchDiagnostics.ReverseEngineeringAudit audit =
                BlueprintResearchDiagnostics.auditReverseEngineering(
                        snapshot,
                        Map.of(liveId, data(liveId)),
                        PROFILE,
                        value -> value.equals(liveId.toString()),
                        ignored -> false);

        assertEquals(List.of(liveId), audit.eligibleBlueprintIds());
        assertEquals(List.of(id("test:missing_reverse_target")), audit.unmatchedRuleIds());
        assertEquals(List.of(liveId), audit.blockedTargetIds());
        assertEquals(List.of(liveId), audit.expertEconomyLoopIds());
        assertTrue(audit.hasProblems());
    }

    @Test
    void unsafeProfileAndShadowedUnsafeRuleBothFailSnapshotPublication() {
        BlueprintReverseEngineeringPolicy unsafe = new BlueprintReverseEngineeringPolicy(
                true,
                Optional.empty(),
                new BlueprintResearchCost(0, List.of()),
                true,
                true,
                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                true,
                false);
        assertThrows(IllegalArgumentException.class, () -> BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile(unsafe)),
                Map.of()));

        BlueprintReverseEngineeringOverride unsafeOverride = new BlueprintReverseEngineeringOverride(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(true),
                Optional.empty(),
                Optional.empty(),
                Optional.of(true),
                Optional.empty());
        BlueprintResearchRule rule = new BlueprintResearchRule(
                1,
                PROFILE,
                -100,
                new BlueprintResearchTarget(List.of(id("test:item")), List.of(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(unsafeOverride));
        assertThrows(IllegalArgumentException.class, () -> BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile(BlueprintReverseEngineeringPolicy.DEFAULT)),
                Map.of(id("test:unsafe_shadowed"), rule)));
    }

    @Test
    void reverseCostsParticipateInRegistryAndTagValidation() {
        ResourceLocation missingItem = id("missing_mod:component");
        ResourceLocation missingTag = id("missing_mod:components");
        BlueprintReverseEngineeringPolicy reverse = new BlueprintReverseEngineeringPolicy(
                true,
                Optional.empty(),
                new BlueprintResearchCost(
                        1,
                        List.of(
                                new BlueprintResearchIngredient(
                                        List.of(missingItem),
                                        Optional.empty(),
                                        1),
                                new BlueprintResearchIngredient(
                                        List.of(),
                                        Optional.of(missingTag),
                                        1))),
                false,
                true,
                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                false,
                false);
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile(reverse)),
                Map.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> BlueprintResearchIngredientValidator.validateExactItems(
                        snapshot,
                        ignored -> false));
        assertEquals(
                java.util.Set.of(missingTag),
                BlueprintResearchIngredientValidator.unresolvedTags(
                        snapshot,
                        ignored -> false));
    }

    private static BlueprintResearchProfile profile(BlueprintReverseEngineeringPolicy reverse) {
        return new BlueprintResearchProfile(
                1,
                true,
                JournalVisibility.FULL,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(8, List.of()),
                false,
                false,
                true,
                List.of(),
                Map.of(),
                Optional.empty(),
                reverse);
    }

    private static BlueprintData data(ResourceLocation id) {
        return new BlueprintData(
                id.toString(),
                "item.test",
                "tooltip.test",
                new ResourceLocation(id.getNamespace(), "recipe/" + id.getPath()),
                null,
                "rifle",
                id("tacz:slot"),
                BlueprintKind.GUN,
                1);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
