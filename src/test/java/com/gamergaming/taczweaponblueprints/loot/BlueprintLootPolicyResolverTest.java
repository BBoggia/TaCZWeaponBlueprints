package com.gamergaming.taczweaponblueprints.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootEntry;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootPool;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootRolls;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootRule;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootRulePredicate;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootSnapshot;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class BlueprintLootPolicyResolverTest {
    private static final ResourceLocation TABLE = id("minecraft:chests/simple_dungeon");

    @Test
    void resolvesExactWeightsProbabilitiesAndSelections() {
        BlueprintLootSnapshot snapshot = snapshot(rule(Optional.empty(), Optional.empty(), Optional.empty()));
        BlueprintLootPolicyResolver.EffectiveRule policy = BlueprintLootPolicyResolver.resolve(
                snapshot,
                binding(snapshot),
                catalog(),
                id("minecraft:overworld"),
                0.0f,
                defaults(true, 0.5, 1, 3, ignored -> false));

        assertTrue(policy.active());
        assertEquals(0.5f, policy.chance());
        assertEquals(new BlueprintLootPolicyResolver.RollRange(1, 3), policy.rolls());
        assertEquals(2, policy.catalogCandidateCount());
        assertEquals(2, policy.candidates().size());
        assertEquals(4.0, policy.totalWeight());
        assertEquals(1.0, policy.expectedAdditions());
        assertEquals(0.25, policy.candidates().get(0).probability());
        assertEquals(0.75, policy.candidates().get(1).probability());
        assertEquals(id("test:first"), policy.select(0.249).orElseThrow());
        assertEquals(id("test:second"), policy.select(0.25).orElseThrow());
    }

    @Test
    void ruleOverridesDefaultsAndPredicatesControlActivity() {
        BlueprintLootRulePredicate predicate = new BlueprintLootRulePredicate(
                List.of(id("minecraft:the_nether")), Optional.of(1.0f), Optional.of(3.0f));
        BlueprintLootSnapshot snapshot = snapshot(rule(
                Optional.of(0.75f),
                Optional.of(new BlueprintLootRolls(2, 4)),
                Optional.of(predicate)));

        BlueprintLootPolicyResolver.EffectiveRule matching = BlueprintLootPolicyResolver.resolve(
                snapshot,
                binding(snapshot),
                catalog(),
                id("minecraft:the_nether"),
                3.0f,
                defaults(true, 0.1, 1, 1, ignored -> false));
        assertTrue(matching.active());
        assertEquals(0.75f, matching.chance());
        assertEquals(new BlueprintLootPolicyResolver.RollRange(2, 4), matching.rolls());
        assertEquals(2.25, matching.expectedAdditions());

        BlueprintLootPolicyResolver.EffectiveRule wrongDimension = BlueprintLootPolicyResolver.resolve(
                snapshot,
                binding(snapshot),
                catalog(),
                id("minecraft:overworld"),
                3.0f,
                defaults(true, 0.1, 1, 1, ignored -> false));
        assertFalse(wrongDimension.predicateMatches());
        assertFalse(wrongDimension.active());
        assertEquals(0.0, wrongDimension.expectedAdditions());
        assertEquals(2, wrongDimension.candidates().size());
    }

    @Test
    void globalDisableAndLiveExclusionsMakePolicyInactive() {
        BlueprintLootSnapshot snapshot = snapshot(rule(Optional.empty(), Optional.empty(), Optional.empty()));
        BlueprintLootPolicyResolver.EffectiveRule disabled = BlueprintLootPolicyResolver.resolve(
                snapshot,
                binding(snapshot),
                catalog(),
                id("minecraft:overworld"),
                0.0f,
                defaults(false, 1.0, 2, 2, ignored -> false));
        assertFalse(disabled.active());
        assertFalse(disabled.blueprintsEnabled());
        assertEquals(0.0, disabled.expectedAdditions());

        BlueprintLootPolicyResolver.EffectiveRule excluded = BlueprintLootPolicyResolver.resolve(
                snapshot,
                binding(snapshot),
                catalog(),
                id("minecraft:overworld"),
                0.0f,
                defaults(true, 1.0, 2, 2, ignored -> true));
        assertEquals(2, excluded.catalogCandidateCount());
        assertTrue(excluded.candidates().isEmpty());
        assertFalse(excluded.active());
        assertTrue(excluded.select(0.5).isEmpty());
    }

    @Test
    void settingsSanitizeUnsafeDefaultsBeforeCandidateWork() {
        BlueprintLootSnapshot snapshot = snapshot(rule(Optional.empty(), Optional.empty(), Optional.empty()));
        BlueprintLootPolicyResolver.RuleSettings settings = BlueprintLootPolicyResolver.resolveSettings(
                binding(snapshot),
                id("minecraft:overworld"),
                0.0f,
                defaults(true, Double.NaN, 4, 2, ignored -> false));

        assertEquals(0.0f, settings.chance());
        assertEquals(new BlueprintLootPolicyResolver.RollRange(4, 4), settings.rolls());
        assertFalse(settings.canAttempt());
        assertTrue(settings.shouldEvaluateChance());
    }

    @Test
    void candidateResolutionRejectsSettingsFromAnotherRule() {
        BlueprintLootSnapshot snapshot = snapshot(rule(Optional.empty(), Optional.empty(), Optional.empty()));
        BlueprintLootPolicyResolver.RuleSettings mismatched = new BlueprintLootPolicyResolver.RuleSettings(
                id("test:other_rule"),
                id("test:pool"),
                true,
                true,
                1.0f,
                new BlueprintLootPolicyResolver.RollRange(1, 1));

        assertThrows(
                IllegalArgumentException.class,
                () -> BlueprintLootPolicyResolver.resolveCandidates(
                        snapshot, binding(snapshot), catalog(), mismatched));
    }

    private static BlueprintLootSnapshot snapshot(BlueprintLootRule rule) {
        BlueprintLootPool pool = new BlueprintLootPool(
                1,
                List.of(
                        new BlueprintLootEntry(id("test:first"), 1.0f),
                        new BlueprintLootEntry(id("test:second"), 3.0f),
                        new BlueprintLootEntry(id("test:not_installed"), 2.0f)));
        return BlueprintLootSnapshot.create(
                Map.of(id("test:pool"), pool),
                Map.of(id("test:rule"), rule));
    }

    private static BlueprintLootRule rule(
            Optional<Float> chance,
            Optional<BlueprintLootRolls> rolls,
            Optional<BlueprintLootRulePredicate> predicate) {
        return new BlueprintLootRule(
                2,
                true,
                id("test:pool"),
                List.of(TABLE),
                chance,
                rolls,
                Optional.empty(),
                predicate);
    }

    private static BlueprintLootSnapshot.RuleBinding binding(BlueprintLootSnapshot snapshot) {
        return snapshot.rulesFor(TABLE).get(0);
    }

    private static Map<ResourceLocation, BlueprintData> catalog() {
        return Map.of(
                id("test:first"), blueprint(id("test:first")),
                id("test:second"), blueprint(id("test:second")));
    }

    private static BlueprintLootPolicyResolver.RuntimeDefaults defaults(
            boolean enabled,
            double chance,
            int min,
            int max,
            java.util.function.Predicate<ResourceLocation> excluded) {
        return new BlueprintLootPolicyResolver.RuntimeDefaults(enabled, chance, min, max, excluded);
    }

    private static BlueprintData blueprint(ResourceLocation id) {
        return new BlueprintData(
                id.toString(),
                "item.test.name",
                "item.test.tooltip",
                id("test:recipe/" + id.getPath()),
                null,
                "rifle",
                id("test:display/rifle"));
    }

    private static ResourceLocation id(String value) {
        ResourceLocation result = ResourceLocation.tryParse(value);
        if (result == null) {
            throw new IllegalArgumentException(value);
        }
        return result;
    }
}
