package com.gamergaming.taczweaponblueprints.journal;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.network.BlueprintSyncLimits;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchRule;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;

import net.minecraft.resources.ResourceLocation;

/** Disclosure-filtered, client-presentable Journal entry. */
public record BlueprintJournalEntry(
        int ordinal,
        JournalVisibility visibility,
        Optional<ResourceLocation> blueprintId,
        Optional<String> nameKey,
        Optional<String> itemType,
        Optional<ResourceLocation> displaySlotId,
        boolean learned,
        boolean discovered,
        boolean researchable,
        boolean recyclable,
        boolean canAffordPoints,
        int researchPointCost,
        int ingredientTypeCount,
        int prerequisiteCount,
        int recyclingValue) {

    public BlueprintJournalEntry {
        blueprintId = optional(blueprintId);
        nameKey = optional(nameKey);
        itemType = optional(itemType);
        displaySlotId = optional(displaySlotId);
        if (ordinal < 0 || ordinal >= PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
            throw new IllegalArgumentException("Journal entry ordinal is outside the supported range");
        }
        if (visibility == null || visibility == JournalVisibility.HIDDEN) {
            throw new IllegalArgumentException("hidden Journal entries must not be synchronized");
        }
        validateOptionalText(nameKey, BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH, "name key");
        validateOptionalText(itemType, BlueprintSyncLimits.MAX_ITEM_TYPE_LENGTH, "item type");
        blueprintId.ifPresent(id -> validateId(id, "blueprint ID"));
        displaySlotId.ifPresent(id -> validateId(id, "display slot ID"));
        if (researchPointCost < 0
                || researchPointCost > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || recyclingValue < 0
                || recyclingValue > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || ingredientTypeCount < 0
                || ingredientTypeCount > BlueprintResearchCost.MAX_INGREDIENT_TYPES
                || prerequisiteCount < 0
                || prerequisiteCount > BlueprintResearchRule.MAX_PREREQUISITES) {
            throw new IllegalArgumentException("Journal entry policy summary is outside the supported range");
        }
        if (visibility == JournalVisibility.SILHOUETTE) {
            requireAnonymousState(blueprintId, nameKey, itemType, displaySlotId,
                    learned, discovered, researchable, recyclable, canAffordPoints,
                    researchPointCost, ingredientTypeCount, prerequisiteCount, recyclingValue);
        } else if (visibility == JournalVisibility.NAME) {
            if (nameKey.isEmpty() || blueprintId.isPresent() || itemType.isPresent() || displaySlotId.isPresent()
                    || learned || discovered || researchable || recyclable || canAffordPoints
                    || researchPointCost != 0 || ingredientTypeCount != 0
                    || prerequisiteCount != 0 || recyclingValue != 0) {
                throw new IllegalArgumentException("name-only Journal entries contain disallowed metadata");
            }
        } else if (visibility == JournalVisibility.PREVIEW
                && (learned || discovered || researchable || recyclable || canAffordPoints
                || recyclingValue != 0)) {
            throw new IllegalArgumentException("preview Journal entry contains full policy state");
        } else if (blueprintId.isEmpty() || nameKey.isEmpty() || itemType.isEmpty() || displaySlotId.isEmpty()) {
            throw new IllegalArgumentException("preview and full Journal entries require presentation metadata");
        }
    }

    public static BlueprintJournalEntry create(
            int ordinal,
            BlueprintData data,
            BlueprintResearchPolicy policy) {
        JournalVisibility visibility = policy.visibility();
        if (visibility == JournalVisibility.HIDDEN) {
            throw new IllegalArgumentException("hidden policies cannot create Journal entries");
        }
        if (visibility == JournalVisibility.SILHOUETTE) {
            return anonymous(ordinal, visibility, Optional.empty());
        }
        if (visibility == JournalVisibility.NAME) {
            return anonymous(ordinal, visibility, Optional.of(data.getNameKey()));
        }
        boolean showResearch = policy.researchEnabled();
        boolean showExactPolicy = visibility.revealsExactPolicy();
        BlueprintResearchCost cost = policy.researchCost();
        return new BlueprintJournalEntry(
                ordinal,
                visibility,
                Optional.of(policy.blueprintId()),
                Optional.of(data.getNameKey()),
                Optional.of(data.getItemType()),
                Optional.of(data.getDisplaySlotKey()),
                showExactPolicy && policy.learned(),
                showExactPolicy && policy.discovered(),
                showExactPolicy && policy.researchable(),
                showExactPolicy && policy.recyclable(),
                showExactPolicy && showResearch && policy.canAffordPoints(),
                showResearch ? cost.points() : 0,
                showResearch ? cost.ingredients().size() : 0,
                showResearch ? policy.prerequisites().size() : 0,
                visibility.revealsExactPolicy() && policy.recyclingEnabled()
                        ? policy.recyclingValue()
                        : 0);
    }

    private static BlueprintJournalEntry anonymous(
            int ordinal,
            JournalVisibility visibility,
            Optional<String> nameKey) {
        return new BlueprintJournalEntry(
                ordinal, visibility, Optional.empty(), nameKey, Optional.empty(), Optional.empty(),
                false, false, false, false, false, 0, 0, 0, 0);
    }

    private static void requireAnonymousState(
            Optional<ResourceLocation> blueprintId,
            Optional<String> nameKey,
            Optional<String> itemType,
            Optional<ResourceLocation> displaySlotId,
            boolean learned,
            boolean discovered,
            boolean researchable,
            boolean recyclable,
            boolean canAffordPoints,
            int researchPointCost,
            int ingredientTypeCount,
            int prerequisiteCount,
            int recyclingValue) {
        if (blueprintId.isPresent() || nameKey.isPresent() || itemType.isPresent() || displaySlotId.isPresent()
                || learned || discovered || researchable || recyclable || canAffordPoints
                || researchPointCost != 0 || ingredientTypeCount != 0
                || prerequisiteCount != 0 || recyclingValue != 0) {
            throw new IllegalArgumentException("silhouette Journal entries contain disallowed metadata");
        }
    }

    private static <T> Optional<T> optional(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }

    private static void validateOptionalText(Optional<String> value, int maximum, String field) {
        if (value.filter(text -> text.isBlank() || text.length() > maximum).isPresent()) {
            throw new IllegalArgumentException("Journal " + field + " is blank or oversized");
        }
    }

    private static void validateId(ResourceLocation id, String field) {
        if (id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("Journal " + field + " is oversized");
        }
    }
}
