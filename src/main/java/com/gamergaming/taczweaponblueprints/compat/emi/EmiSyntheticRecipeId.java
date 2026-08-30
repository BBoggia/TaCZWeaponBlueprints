package com.gamergaming.taczweaponblueprints.compat.emi;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.compat.recipeviewer.BlueprintRecipeViewerInfo.Topic;

import net.minecraft.resources.ResourceLocation;

/** Constructs IDs that EMI recognizes as synthetic rather than data-pack recipes. */
final class EmiSyntheticRecipeId {
    private EmiSyntheticRecipeId() {
    }

    static ResourceLocation forTopic(Topic topic) {
        if (topic == null) {
            throw new IllegalArgumentException("EMI information topic cannot be null");
        }
        // EMI reserves paths beginning with '/' for recipes that intentionally
        // do not exist in Minecraft's recipe manager.
        return TaCZWeaponBlueprints.loc("/emi_info/" + topic.path());
    }

    static ResourceLocation forBlueprint(ResourceLocation blueprintId) {
        if (blueprintId == null) {
            throw new IllegalArgumentException("EMI blueprint information ID cannot be null");
        }
        return TaCZWeaponBlueprints.loc(
                "/emi_info/blueprint/"
                        + blueprintId.getNamespace()
                        + "/"
                        + blueprintId.getPath());
    }
}
