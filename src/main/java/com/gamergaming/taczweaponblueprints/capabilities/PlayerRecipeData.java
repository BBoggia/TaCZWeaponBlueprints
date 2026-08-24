package com.gamergaming.taczweaponblueprints.capabilities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public class PlayerRecipeData implements IPlayerRecipeData {
    private static final String RECIPES_TAG = "Recipes";
    private static final String BLUEPRINTS_TAG = "Blueprints";
    public static final int MAX_RESOURCE_ID_LENGTH = 256;

    private final Set<String> learnedRecipes = new LinkedHashSet<>();
    private final Set<String> learnedBlueprints = new LinkedHashSet<>();

    @Override
    public Set<String> getLearnedRecipes() {
        return Collections.unmodifiableSet(learnedRecipes);
    }

    @Override
    public Set<String> getLearnedBlueprints() {
        return Collections.unmodifiableSet(learnedBlueprints);
    }

    @Override
    public boolean addRecipe(String recipeId) {
        String normalizedId = normalizeResourceId(recipeId);
        return normalizedId != null && learnedRecipes.add(normalizedId);
    }

    @Override
    public boolean addBlueprint(String blueprintId) {
        String normalizedId = normalizeResourceId(blueprintId);
        return normalizedId != null && learnedBlueprints.add(normalizedId);
    }

    @Override
    public boolean removeRecipe(String recipeId) {
        String normalizedId = normalizeResourceId(recipeId);
        return normalizedId != null && learnedRecipes.remove(normalizedId);
    }

    @Override
    public boolean hasRecipe(String recipeId) {
        String normalizedId = normalizeResourceId(recipeId);
        return normalizedId != null && learnedRecipes.contains(normalizedId);
    }

    @Override
    public boolean hasBlueprint(String blueprintId) {
        String normalizedId = normalizeResourceId(blueprintId);
        return normalizedId != null && learnedBlueprints.contains(normalizedId);
    }

    @Override
    public void replaceRecipes(Collection<String> recipeIds) {
        Collection<String> snapshot = recipeIds == null ? Collections.emptyList() : new ArrayList<>(recipeIds);
        learnedRecipes.clear();
        snapshot.forEach(this::addRecipe);
    }

    @Override
    public void clearRecipes() {
        learnedRecipes.clear();
        learnedBlueprints.clear();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        ListTag list = new ListTag();
        for (String recipeId : new TreeSet<>(learnedRecipes)) {
            list.add(StringTag.valueOf(recipeId));
        }
        nbt.put(RECIPES_TAG, list);
        ListTag blueprints = new ListTag();
        for (String blueprintId : new TreeSet<>(learnedBlueprints)) {
            blueprints.add(StringTag.valueOf(blueprintId));
        }
        nbt.put(BLUEPRINTS_TAG, blueprints);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        learnedRecipes.clear();
        learnedBlueprints.clear();
        if (nbt != null && nbt.contains(RECIPES_TAG, Tag.TAG_LIST)) {
            ListTag list = nbt.getList(RECIPES_TAG, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                addRecipe(list.getString(i));
            }
        }
        if (nbt != null && nbt.contains(BLUEPRINTS_TAG, Tag.TAG_LIST)) {
            ListTag list = nbt.getList(BLUEPRINTS_TAG, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                addBlueprint(list.getString(i));
            }
        }
    }

    public static String normalizeRecipeId(String recipeId) {
        return normalizeResourceId(recipeId);
    }

    public static String normalizeResourceId(String resourceId) {
        if (resourceId == null || resourceId.length() > MAX_RESOURCE_ID_LENGTH) {
            return null;
        }
        ResourceLocation parsedId = ResourceLocation.tryParse(resourceId);
        return parsedId == null ? null : parsedId.toString();
    }
}
