package com.gamergaming.taczweaponblueprints.item;

import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintReverseEngineeringPolicy;

import net.minecraft.resources.ResourceLocation;

public class BlueprintData {
    private final String bpId;
    private final String nameKey;
    private final String tooltipKey;
    private final ResourceLocation recipeId;
    private final GunSmithTableRecipe recipe;
    private final String itemType;
    private final ResourceLocation displaySlotKey;
    private final BlueprintKind kind;
    private final int canonicalOutputCount;

    public BlueprintData(
            String bpId,
            String nameKey,
            String tooltipKey,
            ResourceLocation recipeId,
            GunSmithTableRecipe recipe,
            String itemType,
            ResourceLocation displaySlotKey,
            BlueprintKind kind,
            int canonicalOutputCount) {
        if (kind == null) {
            throw new IllegalArgumentException("blueprint kind cannot be null");
        }
        if (canonicalOutputCount < 1
                || canonicalOutputCount > BlueprintReverseEngineeringPolicy.MAX_INPUT_COUNT) {
            throw new IllegalArgumentException("canonical recipe output count is outside the supported range");
        }
        this.bpId = bpId;
        this.nameKey = nameKey;
        this.tooltipKey = tooltipKey;
        this.recipeId = recipeId;
        this.recipe = recipe;
        this.itemType = itemType;
        this.displaySlotKey = displaySlotKey;
        this.kind = kind;
        this.canonicalOutputCount = canonicalOutputCount;
    }

    public BlueprintData(
            String bpId,
            String nameKey,
            String tooltipKey,
            ResourceLocation recipeId,
            GunSmithTableRecipe recipe,
            String itemType,
            ResourceLocation displaySlotKey,
            BlueprintKind kind) {
        this(
                bpId,
                nameKey,
                tooltipKey,
                recipeId,
                recipe,
                itemType,
                displaySlotKey,
                kind,
                canonicalOutputCount(recipe));
    }

    /** Backwards-compatible constructor for programmatic fixtures. */
    public BlueprintData(
            String bpId,
            String nameKey,
            String tooltipKey,
            ResourceLocation recipeId,
            GunSmithTableRecipe recipe,
            String itemType,
            ResourceLocation displaySlotKey) {
        this(
                bpId,
                nameKey,
                tooltipKey,
                recipeId,
                recipe,
                itemType,
                displaySlotKey,
                inferKind(itemType));
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

    public BlueprintKind getKind() {
        return kind;
    }

    public int getCanonicalOutputCount() {
        return canonicalOutputCount;
    }

    private static int canonicalOutputCount(GunSmithTableRecipe recipe) {
        if (recipe == null || recipe.getOutput() == null || recipe.getOutput().isEmpty()) {
            return 1;
        }
        return Math.max(
                1,
                Math.min(
                        recipe.getOutput().getCount(),
                        BlueprintReverseEngineeringPolicy.MAX_INPUT_COUNT));
    }

    private static BlueprintKind inferKind(String itemType) {
        if ("ammo".equalsIgnoreCase(itemType)) {
            return BlueprintKind.AMMO;
        }
        if (itemType != null && switch (itemType.toLowerCase(java.util.Locale.ROOT)) {
            case "scope", "muzzle", "stock", "grip", "extended_mag" -> true;
            default -> false;
        }) {
            return BlueprintKind.ATTACHMENT;
        }
        return BlueprintKind.GUN;
    }
}
