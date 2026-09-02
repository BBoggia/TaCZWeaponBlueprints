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
    private static final String RESEARCH_POINT_AWARDS_TAG = "ResearchPointAwards";

    private final Set<String> learnedRecipes = new LinkedHashSet<>();
    private final Set<String> learnedBlueprints = new LinkedHashSet<>();
    private final Set<String> discoveredBlueprints = new LinkedHashSet<>();
    private final ResearchPointAwardLedger researchPointAwardLedger =
            new ResearchPointAwardLedger();
    private int researchPoints;

    /**
     * Creates a detached, progression-free copy of the RP balance and award
     * ledger for sequential, non-mutating transaction simulation.
     */
    public static PlayerRecipeData copyResearchPointState(IPlayerRecipeData source) {
        if (source == null
                || source.getResearchPoints() < 0
                || source.getResearchPoints() > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || source.getResearchPointAwardLedger() == null) {
            return null;
        }
        PlayerRecipeData copy = new PlayerRecipeData();
        copy.researchPoints = source.getResearchPoints();
        copy.researchPointAwardLedger.replaceWith(source.getResearchPointAwardLedger());
        return copy;
    }

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
    public ResearchPointAwardLedger getResearchPointAwardLedger() {
        return researchPointAwardLedger;
    }

    @Override
    public synchronized BlueprintLearningMutation.Result applyBlueprintLearning(
            BlueprintLearningMutation.Request request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "blueprint learning request cannot be null");
        }
        String blueprintId = normalizeResourceId(request.blueprintId());
        String recipeId = normalizeResourceId(request.legacyRecipeId());
        if (blueprintId == null || recipeId == null) {
            return BlueprintLearningMutation.Result.unchanged(
                    BlueprintLearningMutation.Status.INVALID_IDENTITY,
                    request.operation());
        }

        boolean learnedChanged = !learnedBlueprints.contains(blueprintId);
        boolean discoveredChanged = !discoveredBlueprints.contains(blueprintId);
        boolean recipeChanged = !learnedRecipes.contains(recipeId);
        if (!learnedChanged && !discoveredChanged && !recipeChanged) {
            return BlueprintLearningMutation.Result.unchanged(
                    BlueprintLearningMutation.Status.ALREADY_LEARNED,
                    request.operation());
        }
        if ((learnedChanged
                && learnedBlueprints.size()
                        >= PlayerProgressionLimits.MAX_IDS_PER_COLLECTION)
                || (discoveredChanged
                && discoveredBlueprints.size()
                        >= PlayerProgressionLimits.MAX_IDS_PER_COLLECTION)
                || (recipeChanged
                && learnedRecipes.size()
                        >= PlayerProgressionLimits.MAX_IDS_PER_COLLECTION)) {
            return BlueprintLearningMutation.Result.unchanged(
                    BlueprintLearningMutation.Status.CAPACITY_REACHED,
                    request.operation());
        }

        if (request.operation() == BlueprintLearningMutation.Operation.PREFLIGHT) {
            return BlueprintLearningMutation.Result.ready(
                    learnedChanged,
                    discoveredChanged,
                    recipeChanged);
        }

        // Every rejecting condition is resolved above. Roll back any
        // unexpected unchecked collection failure before exposing a result.
        boolean discoveryAdded = false;
        boolean recipeAdded = false;
        boolean learnedAdded = false;
        try {
            if (discoveredChanged) {
                discoveryAdded = discoveredBlueprints.add(blueprintId);
            }
            if (recipeChanged) {
                recipeAdded = learnedRecipes.add(recipeId);
            }
            if (learnedChanged) {
                learnedAdded = learnedBlueprints.add(blueprintId);
            }
            if (discoveryAdded != discoveredChanged
                    || recipeAdded != recipeChanged
                    || learnedAdded != learnedChanged) {
                throw new IllegalStateException(
                        "blueprint progression changed during atomic learning");
            }
        } catch (RuntimeException exception) {
            if (learnedAdded) {
                learnedBlueprints.remove(blueprintId);
            }
            if (recipeAdded) {
                learnedRecipes.remove(recipeId);
            }
            if (discoveryAdded) {
                discoveredBlueprints.remove(blueprintId);
            }
            throw exception;
        }
        return BlueprintLearningMutation.Result.applied(
                learnedChanged,
                discoveredChanged,
                recipeChanged);
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
        return containsResourceId(learnedRecipes, recipeId);
    }

    @Override
    public boolean hasBlueprint(String blueprintId) {
        return containsResourceId(learnedBlueprints, blueprintId);
    }

    @Override
    public boolean hasDiscoveredBlueprint(String blueprintId) {
        return containsResourceId(discoveredBlueprints, blueprintId);
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
        return applyResearchPointTransaction(
                amount, pointCap, ResearchPointAwardLedger.Mutation.empty());
    }

    @Override
    public boolean applyResearchPointTransaction(
            int amount,
            int pointCap,
            ResearchPointAwardLedger.Mutation ledgerMutation) {
        if (amount < 0
                || pointCap < 0
                || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || (amount > 0 && researchPoints > pointCap - amount)
                || ledgerMutation == null) {
            return false;
        }
        if (!researchPointAwardLedger.apply(ledgerMutation)) {
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
    public void clearResearchPointAwardLedger() {
        researchPointAwardLedger.clear();
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
        nbt.put(RESEARCH_POINT_AWARDS_TAG, researchPointAwardLedger.serializeNBT());
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        learnedRecipes.clear();
        learnedBlueprints.clear();
        discoveredBlueprints.clear();
        researchPointAwardLedger.clear();
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
        if (dataVersion >= 2 && nbt.contains(RESEARCH_POINT_AWARDS_TAG, Tag.TAG_COMPOUND)) {
            researchPointAwardLedger.deserializeNBT(nbt.getCompound(RESEARCH_POINT_AWARDS_TAG));
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

    private static boolean containsResourceId(Set<String> values, String resourceId) {
        if (resourceId == null || resourceId.length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            return false;
        }
        // Persisted and item IDs are normally canonical already. This fast path
        // avoids reparsing a ResourceLocation on every inventory fallback tick.
        if (values.contains(resourceId)) {
            return true;
        }
        String normalizedId = normalizeResourceId(resourceId);
        return normalizedId != null && values.contains(normalizedId);
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
                if (normalizedId == null) {
                    return null;
                }
                normalized.add(normalizedId);
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
