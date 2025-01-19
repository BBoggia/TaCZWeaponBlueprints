package com.gamergaming.taczweaponblueprints.resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Triple;

import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.ibm.icu.impl.Pair;
import com.tacz.guns.crafting.GunSmithTableRecipe;

import net.minecraft.resources.ResourceLocation;

public class BlueprintDataManager {

    public static final BlueprintDataManager INSTANCE = new BlueprintDataManager();

    private final Map<String, BlueprintData> blueprintDataMap = new HashMap<>();

    private BlueprintDataManager() { }

    public void initialize() {
        // Collect blueprint data from loaded recipes
        Map<ResourceLocation, Triple<GunSmithTableRecipe, String, Pair<String, String>>> recipeMap = RecipeResourceLoader.loadRecipes();

        for (Map.Entry<ResourceLocation, Triple<GunSmithTableRecipe, String, Pair<String, String>>> entry : recipeMap.entrySet()) {
            ResourceLocation recipeId = entry.getKey();
            if (ModConfigs.BLUEPRINT.isItemRecipeBlacklisted(recipeId.toString())) {
                continue;
            }
            GunSmithTableRecipe recipe = entry.getValue().getLeft();
            String itemPath = recipeId.getPath().split("/")[1];
            String itemId = recipeId.getNamespace() + ":" + itemPath;
            String itemType = entry.getValue().getRight().first;
            if (itemType == null) {
                itemType = "gun";
            }
            String itemSlotDisplayKey = entry.getValue().getRight().second;
            String bpId = itemId;
            String nameKey = entry.getValue().getMiddle();
            if (nameKey == null) {
                continue;
            // Check if the recipe is in the list of recipes to remove
            } else if (ModConfigs.BLUEPRINT.isItemRecipeBlacklisted(recipeId.toString())) {
                continue;
            }
            
             //recipeId.getNamespace() + "." + itemType + "." + gunPath + ".name";
            String tooltipKey = "item.taczweaponblueprints.blueprint.tooltip";

            BlueprintData data = new BlueprintData(bpId, nameKey, tooltipKey, recipeId, recipe, itemType, itemSlotDisplayKey);
            blueprintDataMap.put(bpId, data);
        }
    }

    public static String getBlueprintIdFromResourceLocation(ResourceLocation recipeId) {
        return recipeId.getNamespace() + ":" + recipeId.getPath().split("/")[1];
    }

    public BlueprintData getBlueprintData(String bpId) {
        return blueprintDataMap.get(bpId);
    }

    public Collection<BlueprintData> getAllBlueprints() {
        return blueprintDataMap.values();
    }

    public Collection<BlueprintData> getRifleBlueprints() { 
        return getBlueprintsByType("rifle");
    }

    public Collection<BlueprintData> getPistolBlueprints() {
        return getBlueprintsByType("pistol");
    }

    public Collection<BlueprintData> getSniperBlueprints() {
        return getBlueprintsByType("sniper");
    }

    public Collection<BlueprintData> getShotgunBlueprints() {
        return getBlueprintsByType("shotgun");
    }

    public Collection<BlueprintData> getSmgBlueprints() {
        return getBlueprintsByType("smg");
    }

    public Collection<BlueprintData> getRpgBlueprints() {
        return getBlueprintsByType("rpg");
    }

    public Collection<BlueprintData> getMgBlueprints() {
        return getBlueprintsByType("mg");
    }

    public Collection<BlueprintData> getAmmoBlueprints() {
        return getBlueprintsByType("ammo");
    }

    public Collection<BlueprintData> getExtendedMagBlueprints() {
        return getBlueprintsByType("extended_mag");
    }

    public Collection<BlueprintData> getScopeBlueprints() {
        return getBlueprintsByType("scope");
    }

    public Collection<BlueprintData> getMuzzleBlueprints() {
        return getBlueprintsByType("muzzle");
    }

    public Collection<BlueprintData> getStockBlueprints() {
        return getBlueprintsByType("stock");
    }

    public Collection<BlueprintData> getGripBlueprints() {
        return getBlueprintsByType("grip");
    }

    public Collection<BlueprintData> getBlueprintsByType(String itemType) {
        List<BlueprintData> blueprints = new ArrayList<>();
        for (BlueprintData data : blueprintDataMap.values()) {
            if (data.getItemType().equals(itemType)) {
                blueprints.add(data);
            }
        }
        return blueprints;
    }
}