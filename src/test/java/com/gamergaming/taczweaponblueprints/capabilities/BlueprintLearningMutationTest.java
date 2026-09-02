package com.gamergaming.taczweaponblueprints.capabilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class BlueprintLearningMutationTest {
    private static final String BLUEPRINT = "test:phase_two_rifle";
    private static final String RECIPE = "test:gun/phase_two_rifle";

    @Test
    void preflightIsNonMutatingAndCommitPublishesAllThreeInvariants() {
        PlayerRecipeData data = new PlayerRecipeData();

        BlueprintLearningMutation.Result preview = data.applyBlueprintLearning(
                BlueprintLearningMutation.Request.preflight(BLUEPRINT, RECIPE));

        assertEquals(BlueprintLearningMutation.Status.READY, preview.status());
        assertTrue(preview.ready());
        assertFalse(preview.committed());
        assertTrue(preview.learnedChanged());
        assertTrue(preview.discoveredChanged());
        assertTrue(preview.legacyRecipeChanged());
        assertTrue(data.getLearnedBlueprints().isEmpty());
        assertTrue(data.getDiscoveredBlueprints().isEmpty());
        assertTrue(data.getLearnedRecipes().isEmpty());

        BlueprintLearningMutation.Result commit = data.applyBlueprintLearning(
                BlueprintLearningMutation.Request.commit(BLUEPRINT, RECIPE));

        assertEquals(BlueprintLearningMutation.Status.APPLIED, commit.status());
        assertTrue(commit.committed());
        assertEquals(Set.of(BLUEPRINT), data.getLearnedBlueprints());
        assertEquals(Set.of(BLUEPRINT), data.getDiscoveredBlueprints());
        assertEquals(Set.of(RECIPE), data.getLearnedRecipes());

        BlueprintLearningMutation.Result duplicate = data.applyBlueprintLearning(
                BlueprintLearningMutation.Request.commit(BLUEPRINT, RECIPE));
        assertEquals(
                BlueprintLearningMutation.Status.ALREADY_LEARNED,
                duplicate.status());
        assertFalse(duplicate.committed());
    }

    @Test
    void commitReportsOnlyTransitionsThatWereActuallyNeeded() {
        PlayerRecipeData discovered = new PlayerRecipeData();
        assertTrue(discovered.discoverBlueprint(BLUEPRINT));
        assertTrue(discovered.addRecipe(RECIPE));

        BlueprintLearningMutation.Result learnedOnly = discovered.applyBlueprintLearning(
                BlueprintLearningMutation.Request.commit(BLUEPRINT, RECIPE));
        assertTrue(learnedOnly.learnedChanged());
        assertFalse(learnedOnly.discoveredChanged());
        assertFalse(learnedOnly.legacyRecipeChanged());

        PlayerRecipeData missingLegacyRecipe = new PlayerRecipeData();
        assertTrue(missingLegacyRecipe.addBlueprint(BLUEPRINT));
        BlueprintLearningMutation.Result repair = missingLegacyRecipe.applyBlueprintLearning(
                BlueprintLearningMutation.Request.commit(BLUEPRINT, RECIPE));
        assertTrue(repair.committed());
        assertFalse(repair.learnedChanged());
        assertFalse(repair.discoveredChanged());
        assertTrue(repair.legacyRecipeChanged());
        assertTrue(missingLegacyRecipe.hasRecipe(RECIPE));
    }

    @Test
    void invalidIdentityChangesNothing() {
        PlayerRecipeData data = new PlayerRecipeData();

        for (BlueprintLearningMutation.Request request : Set.of(
                BlueprintLearningMutation.Request.commit("not an id", RECIPE),
                BlueprintLearningMutation.Request.commit(BLUEPRINT, "not an id"),
                BlueprintLearningMutation.Request.commit(null, RECIPE),
                BlueprintLearningMutation.Request.commit(BLUEPRINT, null))) {
            BlueprintLearningMutation.Result result =
                    data.applyBlueprintLearning(request);
            assertEquals(
                    BlueprintLearningMutation.Status.INVALID_IDENTITY,
                    result.status());
        }
        assertTrue(data.getLearnedBlueprints().isEmpty());
        assertTrue(data.getDiscoveredBlueprints().isEmpty());
        assertTrue(data.getLearnedRecipes().isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> data.applyBlueprintLearning(null));
    }

    @Test
    void everyCollectionCapacityFailureIsAtomic() {
        PlayerRecipeData recipesFull = new PlayerRecipeData();
        for (int index = 0;
                index < PlayerProgressionLimits.MAX_IDS_PER_COLLECTION;
                index++) {
            assertTrue(recipesFull.addRecipe("full_recipes:value_" + index));
        }
        assertCapacityFailureWithoutTarget(recipesFull);

        PlayerRecipeData discoveryFull = new PlayerRecipeData();
        for (int index = 0;
                index < PlayerProgressionLimits.MAX_IDS_PER_COLLECTION;
                index++) {
            assertTrue(discoveryFull.discoverBlueprint(
                    "full_discovery:value_" + index));
        }
        assertCapacityFailureWithoutTarget(discoveryFull);

        PlayerRecipeData learnedFull = new PlayerRecipeData();
        for (int index = 0;
                index < PlayerProgressionLimits.MAX_IDS_PER_COLLECTION;
                index++) {
            assertTrue(learnedFull.addBlueprint("full_learned:value_" + index));
        }
        assertCapacityFailureWithoutTarget(learnedFull);
    }

    @Test
    void typedResultRejectsImpossibleStateCombinations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BlueprintLearningMutation.Result.ready(false, false, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BlueprintLearningMutation.Result(
                        BlueprintLearningMutation.Status.APPLIED,
                        BlueprintLearningMutation.Operation.PREFLIGHT,
                        true,
                        true,
                        true));
        assertThrows(
                IllegalArgumentException.class,
                () -> BlueprintLearningMutation.Result.unchanged(
                        BlueprintLearningMutation.Status.APPLIED,
                        BlueprintLearningMutation.Operation.COMMIT));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BlueprintLearningMutation.Request(null, BLUEPRINT, RECIPE));
    }

    private static void assertCapacityFailureWithoutTarget(PlayerRecipeData data) {
        Set<String> learnedBefore = Set.copyOf(data.getLearnedBlueprints());
        Set<String> discoveredBefore = Set.copyOf(data.getDiscoveredBlueprints());
        Set<String> recipesBefore = Set.copyOf(data.getLearnedRecipes());

        BlueprintLearningMutation.Result preview = data.applyBlueprintLearning(
                BlueprintLearningMutation.Request.preflight(BLUEPRINT, RECIPE));
        BlueprintLearningMutation.Result commit = data.applyBlueprintLearning(
                BlueprintLearningMutation.Request.commit(BLUEPRINT, RECIPE));

        assertEquals(BlueprintLearningMutation.Status.CAPACITY_REACHED, preview.status());
        assertEquals(BlueprintLearningMutation.Status.CAPACITY_REACHED, commit.status());
        assertEquals(learnedBefore, data.getLearnedBlueprints());
        assertEquals(discoveredBefore, data.getDiscoveredBlueprints());
        assertEquals(recipesBefore, data.getLearnedRecipes());
        assertFalse(data.hasBlueprint(BLUEPRINT));
        assertFalse(data.hasDiscoveredBlueprint(BLUEPRINT));
        assertFalse(data.hasRecipe(RECIPE));
    }
}
