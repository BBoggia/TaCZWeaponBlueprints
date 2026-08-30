package com.gamergaming.taczweaponblueprints.compat.emi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.compat.recipeviewer.BlueprintRecipeViewerInfo.Topic;

import net.minecraft.resources.ResourceLocation;

class EmiSyntheticRecipeIdTest {
    @Test
    void informationPagesUseEmiSyntheticRecipeIds() {
        var id = EmiSyntheticRecipeId.forTopic(Topic.RESEARCH_BENCH);

        assertEquals("taczweaponblueprints", id.getNamespace());
        assertEquals("/emi_info/research_bench", id.getPath());
    }

    @Test
    void syntheticRecipeIdRejectsMissingTopic() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EmiSyntheticRecipeId.forTopic(null));
    }

    @Test
    void concreteBlueprintPagesHaveUniqueSyntheticIds() {
        var id = EmiSyntheticRecipeId.forBlueprint(
                new ResourceLocation("addon", "weapons/carbine"));

        assertEquals("taczweaponblueprints", id.getNamespace());
        assertEquals("/emi_info/blueprint/addon/weapons/carbine", id.getPath());
    }

    @Test
    void concreteBlueprintIdRejectsMissingBlueprint() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EmiSyntheticRecipeId.forBlueprint(null));
    }
}
