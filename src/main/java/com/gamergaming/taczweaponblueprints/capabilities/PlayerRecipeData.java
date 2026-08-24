package com.gamergaming.taczweaponblueprints.capabilities;

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
    /** @deprecated Use {@link PlayerProgressionLimits#MAX_RESOURCE_ID_LENGTH}. */
    @Deprecated
    public static final int MAX_RESOURCE_ID_LENGTH = PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;

    private static final String DATA_VERSION_TAG = "DataVersion";
    private static final String RECIPES_TAG = "Recipes";
    private static final String BLUEPRINTS_TAG = "Blueprints";
    private static final String DISCOVERED_BLUEPRINTS_TAG = "DiscoveredBlueprints";
    private static final String RESEARCH_POINTS_TAG = "ResearchPoints";

    private final Set<String> learnedRecipes = new LinkedHashSet<>();
    private final Set<String> learnedBlueprints = new LinkedHashSet<>();
    private final Set<String> discoveredBlueprints = new LinkedHashSet<>();
    private int researchPoints;

    @Override
    public Set<String> getLearnedRecipes() {
        return Collections.unmodifiableSet(learnedRecipes);
    }

    @Override
    public Set<String> getLearnedBlueprints() {
        return Collections.unmodifiableSet(learnedBlueprints);
    }

    @Override
    public Set<String> getDiscoveredBlueprints() {
        return Collections.unmodifiableSet(discoveredBlueprints);
    }

    @Override
    public int getResearchPoints() {
        return researchPoints;
    }

    @Override
    public boolean addRecipe(String recipeId) {
        String normalizedId = normalizeResourceId(recipeId);
        return addBounded(learnedRecipes, normalizedId);
    }

    @Override
    public boolean addBlueprint(String blueprintId) {
        String normalizedId = normalizeResourceId(blueprintId);
        if (normalizedId == null || learnedBlueprints.contains(normalizedId)) {
            return false;
        }
        if (learnedBlueprints.size() >= PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
            return false;
        }
        if (!discoveredBlueprints.contains(normalizedId)
                && discoveredBlueprints.size() >= PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
            return false;
        }
        discoveredBlueprints.add(normalizedId);
        learnedBlueprints.add(normalizedId);
        return true;
    }

    @Override
    public boolean discoverBlueprint(String blueprintId) {
        return addBounded(discoveredBlueprints, normalizeResourceId(blueprintId));
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
    public boolean hasDiscoveredBlueprint(String blueprintId) {
        String normalizedId = normalizeResourceId(blueprintId);
        return normalizedId != null && discoveredBlueprints.contains(normalizedId);
    }

    @Override
    public boolean setResearchPoints(int points) {
        if (points < 0 || points > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            return false;
        }
        researchPoints = points;
        return true;
    }

    @Override
    public boolean addResearchPoints(int amount, int pointCap) {
        if (amount < 0
                || pointCap < 0
                || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || researchPoints > pointCap - amount) {
            return false;
        }
        researchPoints += amount;
        return true;
    }

    @Override
    public boolean spendResearchPoints(int amount) {
        if (amount < 0 || amount > researchPoints) {
            return false;
        }
        researchPoints -= amount;
        return true;
    }

    @Override
    public void replaceRecipes(Collection<String> recipeIds) {
        Collection<String> snapshot = boundedNormalizedSnapshot(recipeIds);
        learnedRecipes.clear();
        learnedRecipes.addAll(snapshot);
    }

    @Override
    public boolean replaceProgression(
            Collection<String> learnedBlueprintIds,
            Collection<String> discoveredBlueprintIds,
            int points) {
        if (points < 0 || points > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            return false;
        }
        Set<String> learnedSnapshot = normalizedSnapshot(learnedBlueprintIds);
        Set<String> discoveredSnapshot = normalizedSnapshot(discoveredBlueprintIds);
        if (learnedSnapshot == null || discoveredSnapshot == null) {
            return false;
        }

        TreeSet<String> completeDiscovery = new TreeSet<>(learnedSnapshot);
        for (String discoveredId : discoveredSnapshot) {
            if (completeDiscovery.size() >= PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
                break;
            }
            completeDiscovery.add(discoveredId);
        }

        learnedBlueprints.clear();
        learnedBlueprints.addAll(learnedSnapshot);
        discoveredBlueprints.clear();
        discoveredBlueprints.addAll(completeDiscovery);
        researchPoints = points;
        return true;
    }

    @Override
    public void clearRecipes() {
        learnedRecipes.clear();
        learnedBlueprints.clear();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt(DATA_VERSION_TAG, PlayerProgressionLimits.DATA_VERSION);
        nbt.put(RECIPES_TAG, writeSortedIds(learnedRecipes));
        nbt.put(BLUEPRINTS_TAG, writeSortedIds(learnedBlueprints));
        nbt.put(DISCOVERED_BLUEPRINTS_TAG, writeSortedIds(discoveredBlueprints));
        nbt.putInt(RESEARCH_POINTS_TAG, researchPoints);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        learnedRecipes.clear();
        learnedBlueprints.clear();
        discoveredBlueprints.clear();
        researchPoints = 0;
        if (nbt == null) {
            return;
        }

        learnedRecipes.addAll(readSortedIds(nbt, RECIPES_TAG));
        learnedBlueprints.addAll(readSortedIds(nbt, BLUEPRINTS_TAG));

        int dataVersion = nbt.contains(DATA_VERSION_TAG, Tag.TAG_ANY_NUMERIC)
                ? Math.max(0, nbt.getInt(DATA_VERSION_TAG))
                : 0;
        // Version 0 had no discovery list. Seeding discovery from learned
        // blueprints for every known version also repairs the invariant if a
        // save was manually edited or partially written.
        discoveredBlueprints.addAll(learnedBlueprints);
        if (dataVersion >= 1) {
            for (String discoveredBlueprint : readSortedIds(nbt, DISCOVERED_BLUEPRINTS_TAG)) {
                addBounded(discoveredBlueprints, discoveredBlueprint);
            }
            if (nbt.contains(RESEARCH_POINTS_TAG, Tag.TAG_ANY_NUMERIC)) {
                long loadedPoints = nbt.getLong(RESEARCH_POINTS_TAG);
                if (loadedPoints > 0) {
                    researchPoints = loadedPoints >= PlayerProgressionLimits.MAX_RESEARCH_POINTS
                            ? PlayerProgressionLimits.MAX_RESEARCH_POINTS
                            : (int) loadedPoints;
                }
            }
        }

    }

    public static String normalizeRecipeId(String recipeId) {
        return normalizeResourceId(recipeId);
    }

    public static String normalizeResourceId(String resourceId) {
        if (resourceId == null || resourceId.length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            return null;
        }
        ResourceLocation parsedId = ResourceLocation.tryParse(resourceId);
        return parsedId == null ? null : parsedId.toString();
    }

    private static boolean addBounded(Set<String> values, String normalizedId) {
        if (normalizedId == null || values.contains(normalizedId)) {
            return false;
        }
        if (values.size() >= PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
            return false;
        }
        return values.add(normalizedId);
    }

    private static ListTag writeSortedIds(Set<String> values) {
        ListTag list = new ListTag();
        for (String value : new TreeSet<>(values)) {
            list.add(StringTag.valueOf(value));
        }
        return list;
    }

    private static Set<String> readSortedIds(CompoundTag nbt, String key) {
        if (!nbt.contains(key, Tag.TAG_LIST)) {
            return Collections.emptySet();
        }
        TreeSet<String> normalized = new TreeSet<>();
        ListTag list = nbt.getList(key, Tag.TAG_STRING);
        int entriesToInspect = Math.min(list.size(), PlayerProgressionLimits.MAX_IDS_PER_COLLECTION);
        for (int i = 0; i < entriesToInspect; i++) {
            String normalizedId = normalizeResourceId(list.getString(i));
            if (normalizedId != null) {
                normalized.add(normalizedId);
            }
        }
        return normalized;
    }

    private static Set<String> normalizedSnapshot(Collection<String> values) {
        TreeSet<String> normalized = new TreeSet<>();
        if (values != null) {
            if (values.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
                return null;
            }
            for (String value : values) {
                String normalizedId = normalizeResourceId(value);
                if (normalizedId != null) {
                    normalized.add(normalizedId);
                }
            }
        }
        return normalized;
    }

    private static Set<String> boundedNormalizedSnapshot(Collection<String> values) {
        TreeSet<String> normalized = new TreeSet<>();
        if (values == null) {
            return normalized;
        }
        int inspected = 0;
        for (String value : values) {
            if (inspected++ >= PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
                break;
            }
            String normalizedId = normalizeResourceId(value);
            if (normalizedId != null) {
                normalized.add(normalizedId);
            }
        }
        return normalized;
    }
}
