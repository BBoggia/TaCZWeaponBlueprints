package com.gamergaming.taczweaponblueprints.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AddItemsModifierTest {

    @Test
    void filtersByCatalogStyleEligibilityInsteadOfForgeModNamespace() {
        List<BlueprintLootSelector.WeightedEntry<String>> candidates = List.of(
                entry("classicr:mgl_40mm", 1.0f),
                entry("lradd:m202", 2.0f),
                entry("blocked:gun", 3.0f));

        assertTrue(BlueprintLootSelector.createEntry("invalid id", "invalid", 4.0f).isEmpty());
        assertTrue(BlueprintLootSelector.createEntry("tacz:zero_weight", "zero", 0.0f).isEmpty());
        assertTrue(BlueprintLootSelector.createEntry("tacz:not_a_number", "nan", Float.NaN).isEmpty());

        List<BlueprintLootSelector.WeightedEntry<String>> eligible = BlueprintLootSelector.filterEligible(
                candidates,
                id -> Set.of("classicr:mgl_40mm", "lradd:m202").contains(id.toString()));

        assertEquals(
                List.of("classicr:mgl_40mm", "lradd:m202"),
                eligible.stream().map(entry -> entry.blueprintId().toString()).toList());
    }

    @Test
    void weightedSelectionUsesStableHalfOpenBoundaries() {
        List<BlueprintLootSelector.WeightedEntry<String>> candidates = List.of(
                entry("test:first", 1.0f),
                entry("test:second", 3.0f));

        assertEquals("first", BlueprintLootSelector.selectWeighted(candidates, 0.0).orElseThrow().value());
        assertEquals("first", BlueprintLootSelector.selectWeighted(candidates, 0.249).orElseThrow().value());
        assertEquals("second", BlueprintLootSelector.selectWeighted(candidates, 0.25).orElseThrow().value());
        assertEquals("second", BlueprintLootSelector.selectWeighted(candidates, 1.0).orElseThrow().value());
    }

    @Test
    void sanitizesProbabilityAndRollBounds() {
        assertEquals(0.0f, BlueprintLootSelector.sanitizeProbability(-1.0));
        assertEquals(0.2f, BlueprintLootSelector.sanitizeProbability(0.2));
        assertEquals(1.0f, BlueprintLootSelector.sanitizeProbability(2.0));
        assertEquals(0.0f, BlueprintLootSelector.sanitizeProbability(Double.NaN));

        assertEquals(new BlueprintLootSelector.RollRange(0, 0), BlueprintLootSelector.sanitizeRollRange(-5, -1));
        assertEquals(new BlueprintLootSelector.RollRange(4, 4), BlueprintLootSelector.sanitizeRollRange(4, 2));
        assertEquals(
                new BlueprintLootSelector.RollRange(64, 64),
                BlueprintLootSelector.sanitizeRollRange(Integer.MAX_VALUE, Integer.MAX_VALUE));

        assertEquals(64, BlueprintLootSelector.remainingBlueprintBudget(-1));
        assertEquals(4, BlueprintLootSelector.remainingBlueprintBudget(60));
        assertEquals(0, BlueprintLootSelector.remainingBlueprintBudget(64));
        assertEquals(0, BlueprintLootSelector.remainingBlueprintBudget(100));
        assertEquals(4, BlueprintLootSelector.constrainRollsToBudget(10, 4));
        assertEquals(0, BlueprintLootSelector.constrainRollsToBudget(10, -1));
    }

    private static BlueprintLootSelector.WeightedEntry<String> entry(String blueprintId, float weight) {
        String value = blueprintId.substring(blueprintId.indexOf(':') + 1);
        return BlueprintLootSelector.createEntry(blueprintId, value, weight).orElseThrow();
    }
}
