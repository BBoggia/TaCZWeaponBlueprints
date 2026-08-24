package com.gamergaming.taczweaponblueprints.capabilities;

import java.util.Collection;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;

public interface IPlayerRecipeData {
    Set<String> getLearnedRecipes();
    Set<String> getLearnedBlueprints();
    boolean addRecipe(String recipeId);
    boolean addBlueprint(String blueprintId);
    boolean removeRecipe(String recipeId);
    boolean hasRecipe(String recipeId);
    boolean hasBlueprint(String blueprintId);
    void replaceRecipes(Collection<String> recipeIds);
    void clearRecipes();
    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag nbt);
}
