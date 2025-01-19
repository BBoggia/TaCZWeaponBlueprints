package com.gamergaming.taczweaponblueprints.capabilities;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;

public interface IPlayerRecipeData {
    Set<String> getLearnedRecipes();
    void addRecipe(String recipeId);
    void removeRecipe(String recipeId);
    boolean hasRecipe(String recipeId);
    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag nbt);
}