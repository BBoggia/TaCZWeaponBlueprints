package com.gamergaming.taczweaponblueprints.progression;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;

import net.minecraft.resources.ResourceLocation;

/** One shared matcher for live recipe access that does not create knowledge. */
public final class BlueprintProgressionAccess {
    private BlueprintProgressionAccess() {
    }

    public static boolean isProgressionExempt(ResourceLocation blueprintId) {
        return isProgressionExempt(
                ModConfigs.BLUEPRINT.accessSnapshot(),
                blueprintId,
                BlueprintDataManager.SERVER.getBlueprintData(blueprintId == null
                        ? null
                        : blueprintId.toString()));
    }

    public static boolean isProgressionExempt(
            BlueprintAccessConfigSnapshot config,
            ResourceLocation blueprintId,
            BlueprintData data) {
        return config != null && config.isProgressionExempt(blueprintId, data);
    }

    public static Set<ResourceLocation> exemptBlueprintIds(
            BlueprintAccessConfigSnapshot config,
            Map<ResourceLocation, BlueprintData> catalog) {
        TreeSet<ResourceLocation> matches = new TreeSet<>((left, right) ->
                left.toString().compareTo(right.toString()));
        if (config != null && catalog != null && config.hasProgressionExemptions()) {
            catalog.forEach((id, data) -> {
                if (config.isProgressionExempt(id, data)) {
                    matches.add(id);
                }
            });
        }
        return Set.copyOf(matches);
    }

    public static Set<String> exemptRecipeIds(
            BlueprintAccessConfigSnapshot config,
            Map<ResourceLocation, BlueprintData> catalog) {
        TreeSet<String> recipes = new TreeSet<>();
        for (ResourceLocation id : exemptBlueprintIds(config, catalog)) {
            BlueprintData data = catalog.get(id);
            if (data != null && data.getRecipeId() != null) {
                recipes.add(data.getRecipeId().toString());
            }
        }
        return Set.copyOf(recipes);
    }
}
