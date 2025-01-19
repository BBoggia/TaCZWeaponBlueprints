package com.gamergaming.taczweaponblueprints.capabilities;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

public class PlayerRecipeData implements IPlayerRecipeData {
    private final Set<String> learnedRecipes = new HashSet<>();

    @Override
    public Set<String> getLearnedRecipes() {
        return learnedRecipes;
    }

    @Override
    public void addRecipe(String recipeId) {
        learnedRecipes.add(recipeId);
    }

    @Override
    public void removeRecipe(String recipeId) {
        learnedRecipes.remove(recipeId);
    }

    @Override
    public boolean hasRecipe(String recipeId) {
        return learnedRecipes.contains(recipeId);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        ListTag list = new ListTag();
        for (String recipeId : learnedRecipes) {
            list.add(StringTag.valueOf(recipeId));
        }
        nbt.put("Recipes", list);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        learnedRecipes.clear();
        if (nbt.contains("Recipes"))
        {
            ListTag list = nbt.getList("Recipes", 8); // 8 is the ID for StringTag
            for (int i = 0; i < list.size(); i++) {
                learnedRecipes.add(list.getString(i));
            }
        }
    }
}