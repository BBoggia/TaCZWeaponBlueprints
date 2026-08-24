package com.gamergaming.taczweaponblueprints.capabilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class PlayerRecipeDataTest {

    @Test
    void validatesAndDeduplicatesRecipeIds() {
        PlayerRecipeData data = new PlayerRecipeData();

        assertTrue(data.addRecipe("tacz:gun/ak47"));
        assertFalse(data.addRecipe("tacz:gun/ak47"));
        assertFalse(data.addRecipe("not a resource location"));
        assertFalse(data.addRecipe(null));
        assertEquals(Set.of("tacz:gun/ak47"), data.getLearnedRecipes());
    }

    @Test
    void exposesReadOnlyStateAndCanReplaceFromItsOwnView() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.replaceRecipes(List.of("tacz:gun/m4a1", "tacz:gun/ak47"));
        Set<String> view = data.getLearnedRecipes();

        assertThrows(UnsupportedOperationException.class, () -> view.add("tacz:gun/scar_l"));
        data.replaceRecipes(view);

        assertEquals(Set.of("tacz:gun/m4a1", "tacz:gun/ak47"), data.getLearnedRecipes());
    }

    @Test
    void serializesDeterministicallyAndRoundTrips() {
        PlayerRecipeData original = new PlayerRecipeData();
        original.addRecipe("tacz:gun/m4a1");
        original.addRecipe("tacz:gun/ak47");
        original.addBlueprint("tacz:ak47");
        original.discoverBlueprint("tacz:m4a1");
        assertTrue(original.setResearchPoints(125));

        CompoundTag serialized = original.serializeNBT();
        assertEquals(PlayerProgressionLimits.DATA_VERSION, serialized.getInt("DataVersion"));
        ListTag recipes = serialized.getList("Recipes", Tag.TAG_STRING);
        assertEquals("tacz:gun/ak47", recipes.getString(0));
        assertEquals("tacz:gun/m4a1", recipes.getString(1));
        assertEquals("tacz:ak47", serialized.getList("Blueprints", Tag.TAG_STRING).getString(0));
        assertEquals(
                List.of("tacz:ak47", "tacz:m4a1"),
                serialized.getList("DiscoveredBlueprints", Tag.TAG_STRING).stream()
                        .map(Tag::getAsString)
                        .toList());
        assertEquals(125, serialized.getInt("ResearchPoints"));

        PlayerRecipeData restored = new PlayerRecipeData();
        restored.deserializeNBT(serialized);
        assertEquals(original.getLearnedRecipes(), restored.getLearnedRecipes());
        assertEquals(original.getLearnedBlueprints(), restored.getLearnedBlueprints());
        assertEquals(original.getDiscoveredBlueprints(), restored.getDiscoveredBlueprints());
        assertEquals(original.getResearchPoints(), restored.getResearchPoints());
    }

    @Test
    void learningDiscoversAndResetPreservesDiscoveryAndPoints() {
        PlayerRecipeData data = new PlayerRecipeData();
        assertTrue(data.addBlueprint("tacz:ak47"));
        assertFalse(data.addBlueprint("tacz:ak47"));
        assertFalse(data.addBlueprint("invalid id"));
        assertTrue(data.hasBlueprint("tacz:ak47"));
        assertTrue(data.hasDiscoveredBlueprint("tacz:ak47"));
        assertTrue(data.setResearchPoints(50));

        data.addRecipe("tacz:gun/ak47");
        data.clearRecipes();

        assertTrue(data.getLearnedRecipes().isEmpty());
        assertTrue(data.getLearnedBlueprints().isEmpty());
        assertEquals(Set.of("tacz:ak47"), data.getDiscoveredBlueprints());
        assertEquals(50, data.getResearchPoints());
    }

    @Test
    void filtersInvalidIdsWhenLoadingLegacyData() {
        CompoundTag serialized = new CompoundTag();
        ListTag recipes = new ListTag();
        recipes.add(StringTag.valueOf("tacz:gun/ak47"));
        recipes.add(StringTag.valueOf("invalid id"));
        recipes.add(StringTag.valueOf("tacz:gun/ak47"));
        serialized.put("Recipes", recipes);

        PlayerRecipeData restored = new PlayerRecipeData();
        restored.deserializeNBT(serialized);

        assertEquals(Set.of("tacz:gun/ak47"), restored.getLearnedRecipes());
    }

    @Test
    void migratesVersionZeroAndRepairsLearnedDiscoveryInvariant() {
        CompoundTag versionZero = new CompoundTag();
        ListTag recipes = new ListTag();
        recipes.add(StringTag.valueOf("tacz:gun/ak47"));
        versionZero.put("Recipes", recipes);
        ListTag blueprints = new ListTag();
        blueprints.add(StringTag.valueOf("tacz:ak47"));
        versionZero.put("Blueprints", blueprints);
        versionZero.putInt("ResearchPoints", 999);

        PlayerRecipeData restored = new PlayerRecipeData();
        restored.deserializeNBT(versionZero);

        assertEquals(Set.of("tacz:gun/ak47"), restored.getLearnedRecipes());
        assertEquals(Set.of("tacz:ak47"), restored.getLearnedBlueprints());
        assertEquals(Set.of("tacz:ak47"), restored.getDiscoveredBlueprints());
        assertEquals(0, restored.getResearchPoints());
        assertEquals(PlayerProgressionLimits.DATA_VERSION, restored.serializeNBT().getInt("DataVersion"));
    }

    @Test
    void researchPointTransactionsAreValidatedAndAtomic() {
        PlayerRecipeData data = new PlayerRecipeData();

        assertTrue(data.addResearchPoints(40, 100));
        assertEquals(40, data.getResearchPoints());
        assertFalse(data.addResearchPoints(61, 100));
        assertFalse(data.addResearchPoints(-1, 100));
        assertFalse(data.addResearchPoints(1, PlayerProgressionLimits.MAX_RESEARCH_POINTS + 1));
        assertEquals(40, data.getResearchPoints());

        assertTrue(data.spendResearchPoints(15));
        assertEquals(25, data.getResearchPoints());
        assertFalse(data.spendResearchPoints(26));
        assertFalse(data.spendResearchPoints(-1));
        assertEquals(25, data.getResearchPoints());

        assertFalse(data.setResearchPoints(-1));
        assertFalse(data.setResearchPoints(PlayerProgressionLimits.MAX_RESEARCH_POINTS + 1));
        assertEquals(25, data.getResearchPoints());
        assertTrue(data.setResearchPoints(PlayerProgressionLimits.MAX_RESEARCH_POINTS));
        assertFalse(data.addResearchPoints(1, PlayerProgressionLimits.MAX_RESEARCH_POINTS));
        assertEquals(PlayerProgressionLimits.MAX_RESEARCH_POINTS, data.getResearchPoints());
    }

    @Test
    void corruptedPointBalancesAreClampedOnLoad() {
        CompoundTag negative = new CompoundTag();
        negative.putInt("DataVersion", 1);
        negative.putInt("ResearchPoints", -10);
        PlayerRecipeData data = new PlayerRecipeData();
        data.deserializeNBT(negative);
        assertEquals(0, data.getResearchPoints());

        CompoundTag oversized = new CompoundTag();
        oversized.putInt("DataVersion", 1);
        oversized.putInt("ResearchPoints", Integer.MAX_VALUE);
        data.deserializeNBT(oversized);
        assertEquals(PlayerProgressionLimits.MAX_RESEARCH_POINTS, data.getResearchPoints());
    }

    @Test
    void progressionReplacementCommitsAsOneValidatedSnapshot() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.addBlueprint("test:old");
        data.setResearchPoints(10);

        assertFalse(data.replaceProgression(
                List.of("test:new"),
                List.of("test:new"),
                -1));
        assertEquals(Set.of("test:old"), data.getLearnedBlueprints());
        assertEquals(Set.of("test:old"), data.getDiscoveredBlueprints());
        assertEquals(10, data.getResearchPoints());

        assertFalse(data.replaceProgression(
                List.of("test:new", "not a resource location"),
                List.of("test:new"),
                25));
        assertEquals(Set.of("test:old"), data.getLearnedBlueprints());
        assertEquals(Set.of("test:old"), data.getDiscoveredBlueprints());
        assertEquals(10, data.getResearchPoints());

        assertTrue(data.replaceProgression(
                List.of("test:new"),
                List.of("test:history"),
                25));
        assertEquals(Set.of("test:new"), data.getLearnedBlueprints());
        assertEquals(Set.of("test:new", "test:history"), data.getDiscoveredBlueprints());
        assertEquals(25, data.getResearchPoints());
    }

    @Test
    void allProgressionCollectionsEnforceTheSharedHardLimit() {
        PlayerRecipeData data = new PlayerRecipeData();
        for (int index = 0; index < PlayerProgressionLimits.MAX_IDS_PER_COLLECTION; index++) {
            assertTrue(data.addRecipe("test:recipe_" + index));
            assertTrue(data.discoverBlueprint("test:discovered_" + index));
        }

        assertFalse(data.addRecipe("test:recipe_overflow"));
        assertFalse(data.discoverBlueprint("test:discovered_overflow"));
        assertEquals(PlayerProgressionLimits.MAX_IDS_PER_COLLECTION, data.getLearnedRecipes().size());
        assertEquals(PlayerProgressionLimits.MAX_IDS_PER_COLLECTION, data.getDiscoveredBlueprints().size());
    }

    @Test
    void loadingPrioritizesLearnedIdsWhileBoundingDiscovery() {
        CompoundTag serialized = new CompoundTag();
        serialized.putInt("DataVersion", 1);
        ListTag learned = new ListTag();
        ListTag discovered = new ListTag();
        for (int index = 0; index < PlayerProgressionLimits.MAX_IDS_PER_COLLECTION; index++) {
            learned.add(StringTag.valueOf("learned:blueprint_" + index));
            discovered.add(StringTag.valueOf("discovered:blueprint_" + index));
        }
        serialized.put("Blueprints", learned);
        serialized.put("DiscoveredBlueprints", discovered);

        PlayerRecipeData restored = new PlayerRecipeData();
        restored.deserializeNBT(serialized);

        assertEquals(PlayerProgressionLimits.MAX_IDS_PER_COLLECTION, restored.getLearnedBlueprints().size());
        assertEquals(PlayerProgressionLimits.MAX_IDS_PER_COLLECTION, restored.getDiscoveredBlueprints().size());
        assertTrue(restored.getDiscoveredBlueprints().containsAll(restored.getLearnedBlueprints()));
    }
}
