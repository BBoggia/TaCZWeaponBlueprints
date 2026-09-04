package com.gamergaming.taczweaponblueprints.compat.recipeviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.compat.recipeviewer.BlueprintRecipeViewerInfo.Topic;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalEntry;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class BlueprintRecipeViewerInfoTest {
    @Test
    void topicsUseUniqueBoundedGenericTranslationKeys() {
        Set<String> keys = new HashSet<>();
        for (Topic topic : Topic.all()) {
            assertTrue(topic.translationKeys().size() >= 2);
            assertTrue(topic.translationKeys().size() <= 4);
            for (String key : topic.translationKeys()) {
                assertTrue(key.startsWith("recipe_viewer.taczweaponblueprints."));
                assertTrue(keys.add(key));
            }
            assertEquals(topic.translationKeys().size(),
                    BlueprintRecipeViewerInfo.components(topic).size());
        }
    }

    @Test
    void concreteBlueprintPagesUseOnlySortedServerDisclosedIds() {
        ResourceLocation later = new ResourceLocation("zeta", "rifle");
        ResourceLocation earlier = new ResourceLocation("alpha", "pistol");
        BlueprintJournalSnapshot journal = new BlueprintJournalSnapshot(
                List.of(
                        namedEntry(0),
                        disclosedEntry(1, later),
                        disclosedEntry(2, earlier)),
                List.of(),
                0,
                100,
                0,
                0,
                0);

        assertEquals(
                List.of(earlier, later),
                BlueprintRecipeViewerInfo.disclosedBlueprintIds(journal));
        assertEquals(
                List.of(),
                BlueprintRecipeViewerInfo.disclosedBlueprintIds(null));
    }

    private static BlueprintJournalEntry namedEntry(int ordinal) {
        return new BlueprintJournalEntry(
                ordinal,
                JournalVisibility.NAME,
                Optional.empty(),
                Optional.of("test.hidden_name"),
                Optional.empty(),
                Optional.empty(),
                false, false, false, false, false,
                0, 0, 0, 0);
    }

    private static BlueprintJournalEntry disclosedEntry(
            int ordinal,
            ResourceLocation blueprintId) {
        return new BlueprintJournalEntry(
                ordinal,
                JournalVisibility.FULL,
                Optional.of(blueprintId),
                Optional.of("test." + blueprintId.getPath()),
                Optional.of("gun"),
                Optional.of(new ResourceLocation("tacz", "gun")),
                false, false, false, false, false,
                0, 0, 0, 0);
    }
}
