package com.gamergaming.taczweaponblueprints.progression;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable synchronized policy for recipes that bypass blueprint progression
 * and exact blueprints that new/existing players should learn durably.
 *
 * <p>Exempt selectors are intentionally live and additive: matching any exact
 * ID, coarse kind, or TaCZ item type makes the recipe accessible without
 * writing fake knowledge to every player. Starting grants are exact IDs so a
 * configuration typo cannot unexpectedly unlock an entire add-on namespace.</p>
 */
public record BlueprintAccessConfigSnapshot(
        Set<ResourceLocation> progressionExemptBlueprints,
        Set<BlueprintKind> progressionExemptKinds,
        Set<String> progressionExemptItemTypes,
        Set<ResourceLocation> startingBlueprints) {

    public static final BlueprintAccessConfigSnapshot EMPTY = new BlueprintAccessConfigSnapshot(
            Set.of(), Set.of(), Set.of(), Set.of());

    public BlueprintAccessConfigSnapshot {
        progressionExemptBlueprints = immutableIds(progressionExemptBlueprints);
        progressionExemptKinds = progressionExemptKinds == null || progressionExemptKinds.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(progressionExemptKinds));
        progressionExemptItemTypes = immutableTypes(progressionExemptItemTypes);
        startingBlueprints = immutableIds(startingBlueprints);
        if (startingBlueprints.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
            throw new IllegalArgumentException("too many configured starting blueprints");
        }
    }

    public static BlueprintAccessConfigSnapshot from(BlueprintConfig config) {
        if (config == null) {
            return EMPTY;
        }
        return new BlueprintAccessConfigSnapshot(
                parseIds(config.progressionExemptBlueprints),
                parseKinds(config.progressionExemptKinds),
                normalizeTypes(config.progressionExemptItemTypes),
                parseIds(config.startingBlueprints));
    }

    public boolean isProgressionExempt(ResourceLocation blueprintId, BlueprintData data) {
        if (blueprintId == null || data == null) {
            return false;
        }
        String type = data.getItemType() == null
                ? ""
                : data.getItemType().toLowerCase(Locale.ROOT);
        return progressionExemptBlueprints.contains(blueprintId)
                || progressionExemptKinds.contains(data.getKind())
                || progressionExemptItemTypes.contains(type);
    }

    public boolean hasProgressionExemptions() {
        return !progressionExemptBlueprints.isEmpty()
                || !progressionExemptKinds.isEmpty()
                || !progressionExemptItemTypes.isEmpty();
    }

    private static Set<ResourceLocation> parseIds(Iterable<String> values) {
        TreeSet<ResourceLocation> parsed = new TreeSet<>((left, right) ->
                left.toString().compareTo(right.toString()));
        if (values != null) {
            for (String value : values) {
                ResourceLocation id = ResourceLocation.tryParse(value);
                if (id != null
                        && id.toString().length()
                                <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
                    parsed.add(id);
                }
            }
        }
        return Set.copyOf(parsed);
    }

    private static Set<BlueprintKind> parseKinds(Iterable<String> values) {
        EnumSet<BlueprintKind> parsed = EnumSet.noneOf(BlueprintKind.class);
        if (values != null) {
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                try {
                    parsed.add(BlueprintKind.valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // Validated configuration should prevent this. Fail closed if
                    // an older hand-edited file still contains an unknown value.
                }
            }
        }
        return parsed.isEmpty() ? Set.of() : Set.copyOf(parsed);
    }

    private static Set<String> normalizeTypes(Iterable<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    normalized.add(value.toLowerCase(Locale.ROOT));
                }
            }
        }
        return Set.copyOf(normalized);
    }

    private static Set<ResourceLocation> immutableIds(Collection<ResourceLocation> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        if (ids.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("configured blueprint IDs cannot contain null");
        }
        return Set.copyOf(ids);
    }

    private static Set<String> immutableTypes(Collection<String> types) {
        if (types == null || types.isEmpty()) {
            return Set.of();
        }
        if (types.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("configured blueprint item types cannot be blank");
        }
        return Set.copyOf(types);
    }
}
