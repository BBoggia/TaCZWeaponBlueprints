package com.gamergaming.taczweaponblueprints.journal;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.network.BlueprintSyncLimits;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchRule;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.DisclosedCraftingAccess;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintCraftingPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintProgressionPolicy;
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
        int recyclingValue,
        Optional<FragmentProgress> fragmentProgress,
        Optional<DisclosedCraftingAccess> craftingAccess) {

    public BlueprintJournalEntry {
        blueprintId = optional(blueprintId);
        nameKey = optional(nameKey);
        itemType = optional(itemType);
        displaySlotId = optional(displaySlotId);
        fragmentProgress = optional(fragmentProgress);
        craftingAccess = optional(craftingAccess);
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
                    researchPointCost, ingredientTypeCount, prerequisiteCount, recyclingValue,
                    fragmentProgress, craftingAccess);
        } else if (visibility == JournalVisibility.NAME) {
            if (nameKey.isEmpty() || blueprintId.isPresent() || itemType.isPresent() || displaySlotId.isPresent()
                    || learned || discovered || researchable || recyclable || canAffordPoints
                    || researchPointCost != 0 || ingredientTypeCount != 0
                    || prerequisiteCount != 0 || recyclingValue != 0
                    || fragmentProgress.isPresent() || craftingAccess.isPresent()) {
                throw new IllegalArgumentException("name-only Journal entries contain disallowed metadata");
            }
        } else if (visibility == JournalVisibility.PREVIEW
                && (learned || discovered || researchable || recyclable || canAffordPoints
                || recyclingValue != 0 || craftingAccess.isPresent())) {
            throw new IllegalArgumentException("preview Journal entry contains full policy state");
        } else if (blueprintId.isEmpty() || nameKey.isEmpty() || itemType.isEmpty() || displaySlotId.isEmpty()) {
            throw new IllegalArgumentException("preview and full Journal entries require presentation metadata");
        }
    }

    /** Compatibility constructor for Journal entries without fragment metadata. */
    public BlueprintJournalEntry(
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
        this(
                ordinal, visibility, blueprintId, nameKey, itemType, displaySlotId,
                learned, discovered, researchable, recyclable, canAffordPoints,
                researchPointCost, ingredientTypeCount, prerequisiteCount, recyclingValue,
                Optional.empty(), Optional.empty());
    }

    /** Compatibility constructor for Journal entries without crafting access metadata. */
    public BlueprintJournalEntry(
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
            int recyclingValue,
            Optional<FragmentProgress> fragmentProgress) {
        this(
                ordinal, visibility, blueprintId, nameKey, itemType, displaySlotId,
                learned, discovered, researchable, recyclable, canAffordPoints,
                researchPointCost, ingredientTypeCount, prerequisiteCount, recyclingValue,
                fragmentProgress, Optional.empty());
    }

    public static BlueprintJournalEntry create(
            int ordinal,
            BlueprintData data,
            BlueprintResearchPolicy policy) {
        return create(ordinal, data, policy, null, null, 0);
    }

    public static BlueprintJournalEntry create(
            int ordinal,
            BlueprintData data,
            BlueprintResearchPolicy policy,
            ResolvedBlueprintProgressionPolicy progressionPolicy,
            int archivedFragments) {
        return create(
                ordinal, data, policy, progressionPolicy, null, archivedFragments);
    }

    public static BlueprintJournalEntry create(
            int ordinal,
            BlueprintData data,
            BlueprintResearchPolicy policy,
            ResolvedBlueprintProgressionPolicy progressionPolicy,
            ResolvedBlueprintCraftingPolicy craftingPolicy,
            int archivedFragments) {
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
                showResearch ? policy.requirements().allOf().size() : 0,
                visibility.revealsExactPolicy() && policy.recyclingEnabled()
                        ? policy.recyclingValue()
                        : 0,
                fragmentProgress(progressionPolicy, archivedFragments),
                showExactPolicy && craftingPolicy != null
                        ? Optional.of(DisclosedCraftingAccess.from(craftingPolicy))
                        : Optional.empty());
    }

    private static BlueprintJournalEntry anonymous(
            int ordinal,
            JournalVisibility visibility,
            Optional<String> nameKey) {
        return new BlueprintJournalEntry(
                ordinal, visibility, Optional.empty(), nameKey, Optional.empty(), Optional.empty(),
                false, false, false, false, false, 0, 0, 0, 0,
                Optional.empty(), Optional.empty());
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
            int recyclingValue,
            Optional<FragmentProgress> fragmentProgress,
            Optional<DisclosedCraftingAccess> craftingAccess) {
        if (blueprintId.isPresent() || nameKey.isPresent() || itemType.isPresent() || displaySlotId.isPresent()
                || learned || discovered || researchable || recyclable || canAffordPoints
                || researchPointCost != 0 || ingredientTypeCount != 0
                || prerequisiteCount != 0 || recyclingValue != 0
                || fragmentProgress.isPresent() || craftingAccess.isPresent()) {
            throw new IllegalArgumentException("silhouette Journal entries contain disallowed metadata");
        }
    }

    private static Optional<FragmentProgress> fragmentProgress(
            ResolvedBlueprintProgressionPolicy progressionPolicy,
            int archivedFragments) {
        if (progressionPolicy == null || !progressionPolicy.fragments().enabled()) {
            return Optional.empty();
        }
        if (archivedFragments < 0
                || archivedFragments > BlueprintFragmentPolicy.MAX_ARCHIVED_FRAGMENTS) {
            return Optional.empty();
        }
        BlueprintFragmentPolicy fragments = progressionPolicy.fragments();
        return Optional.of(new FragmentProgress(
                archivedFragments,
                fragments.threshold(),
                fragments.completionMode()));
    }

    public record FragmentProgress(
            int archived,
            int threshold,
            BlueprintFragmentPolicy.CompletionMode completionMode) {
        public FragmentProgress {
            if (archived < 0
                    || archived > BlueprintFragmentPolicy.MAX_ARCHIVED_FRAGMENTS
                    || threshold < 1
                    || threshold > BlueprintFragmentPolicy.MAX_THRESHOLD
                    || completionMode == null
                    || completionMode == BlueprintFragmentPolicy.CompletionMode.DISABLED) {
                throw new IllegalArgumentException("invalid Journal fragment progress");
            }
        }

        public int displayedArchived() {
            return Math.min(archived, threshold);
        }

        public boolean complete() {
            return archived >= threshold;
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
