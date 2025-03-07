package com.gamergaming.taczweaponblueprints.item;

import com.tacz.guns.crafting.GunSmithTableRecipe;

import net.minecraft.resources.ResourceLocation;

public class BlueprintData {
    private final String bpId;
    private final String nameKey;
    private final String tooltipKey;
    private final ResourceLocation recipeId;
    private final GunSmithTableRecipe recipe;
    private final String itemType;
    private final ResourceLocation displaySlotKey;

    public BlueprintData(String bpId, String nameKey, String tooltipKey, ResourceLocation recipeId, GunSmithTableRecipe recipe, String itemType, ResourceLocation displaySlotKey) {
        this.bpId = bpId;
        this.nameKey = nameKey;
        this.tooltipKey = tooltipKey;
        this.recipeId = recipeId;
        this.recipe = recipe;
        this.itemType = itemType;
        this.displaySlotKey = displaySlotKey;
    }

    // Getters
    public String getBpId() {
        return bpId;
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getTooltipKey() {
        return tooltipKey;
    }

    public ResourceLocation getRecipeId() {
        return recipeId;
    }

    public GunSmithTableRecipe getRecipe() {
        return recipe;
    }

    public String getItemType() {
        return itemType;
    }

    public ResourceLocation getDisplaySlotKey() {
        return displaySlotKey;
    }
}