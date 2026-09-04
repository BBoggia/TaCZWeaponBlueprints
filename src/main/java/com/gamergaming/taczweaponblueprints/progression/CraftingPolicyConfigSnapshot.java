package com.gamergaming.taczweaponblueprints.progression;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

import net.minecraft.resources.ResourceLocation;

/** Immutable server-owned configuration overlay for resolved crafting policy. */
public record CraftingPolicyConfigSnapshot(
        AmmoCraftingStrategy ammoStrategy,
        AttachmentCraftingStrategy attachmentStrategy,
        ResearchWorkbenchTier linkedAmmoFallbackTier,
        Set<BlueprintKind> unrestrictedKinds,
        Set<String> unrestrictedItemTypes,
        Set<BlueprintKind> disabledKinds,
        Set<String> disabledItemTypes,
        Map<ResourceLocation, CraftingAccessOverride> exactOverrides) {
    public static final int MAX_SELECTORS = 4_096;
    public static final CraftingPolicyConfigSnapshot PROFILE_DEFAULTS =
            new CraftingPolicyConfigSnapshot(
                    AmmoCraftingStrategy.PROFILE,
                    AttachmentCraftingStrategy.PROFILE,
                    ResearchWorkbenchTier.TIER_1,
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Map.of());

    public CraftingPolicyConfigSnapshot {
        if (ammoStrategy == null || attachmentStrategy == null
                || linkedAmmoFallbackTier == null || unrestrictedKinds == null
                || unrestrictedItemTypes == null || disabledKinds == null
                || disabledItemTypes == null || exactOverrides == null) {
            throw new IllegalArgumentException("crafting policy config cannot contain null");
        }
        unrestrictedKinds = immutableKinds(unrestrictedKinds);
        unrestrictedItemTypes = immutableItemTypes(unrestrictedItemTypes);
        disabledKinds = immutableKinds(disabledKinds);
        disabledItemTypes = immutableItemTypes(disabledItemTypes);
        exactOverrides = immutableOverrides(exactOverrides);
        long selectorCount = (long) unrestrictedKinds.size()
                + unrestrictedItemTypes.size()
                + disabledKinds.size()
                + disabledItemTypes.size()
                + exactOverrides.size();
        if (selectorCount > MAX_SELECTORS) {
            throw new IllegalArgumentException("crafting policy config has too many selectors");
        }
    }

    public static CraftingPolicyConfigSnapshot from(BlueprintConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("blueprint config cannot be null");
        }
        AmmoCraftingStrategy ammoStrategy = config.ammoCraftingStrategy.get();
        return new CraftingPolicyConfigSnapshot(
                ammoStrategy,
                config.attachmentCraftingStrategy.get(),
                ammoStrategy == AmmoCraftingStrategy.LINKED_WEAPON
                        ? config.linkedAmmoFallbackTier.get()
                        : ResearchWorkbenchTier.TIER_1,
                parseKinds(config.craftingUnrestrictedKinds.get()),
                Set.copyOf(config.craftingUnrestrictedItemTypes.get()),
                parseKinds(config.craftingDisabledKinds.get()),
                Set.copyOf(config.craftingDisabledItemTypes.get()),
                parseOverrides(config.exactCraftingOverrides));
    }

    private static Set<BlueprintKind> parseKinds(Iterable<? extends String> values) {
        EnumSet<BlueprintKind> result = EnumSet.noneOf(BlueprintKind.class);
        values.forEach(value -> result.add(BlueprintKind.valueOf(
                value.toUpperCase(Locale.ROOT))));
        return Collections.unmodifiableSet(result);
    }

    private static Map<ResourceLocation, CraftingAccessOverride> parseOverrides(
            Map<String, CraftingAccessOverride> source) {
        Map<ResourceLocation, CraftingAccessOverride> result = new LinkedHashMap<>();
        source.forEach((value, override) -> {
            ResourceLocation id = value == null ? null : ResourceLocation.tryParse(value);
            if (id == null || override == null
                    || id.toString().length()
                            > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
                throw new IllegalArgumentException(
                        "configured crafting override contains an invalid entry");
            }
            result.put(id, override);
        });
        return result;
    }

    private static Set<BlueprintKind> immutableKinds(Set<BlueprintKind> source) {
        if (source.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("crafting kind selector contains null");
        }
        return source.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(source));
    }

    private static Set<String> immutableItemTypes(Set<String> source) {
        TreeSet<String> result = new TreeSet<>();
        source.forEach(value -> {
            String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
            if (!normalized.matches("[a-z0-9_.-]{1,256}")) {
                throw new IllegalArgumentException("crafting item-type selector is invalid");
            }
            result.add(normalized);
        });
        return Collections.unmodifiableSet(result);
    }

    private static Map<ResourceLocation, CraftingAccessOverride> immutableOverrides(
            Map<ResourceLocation, CraftingAccessOverride> source) {
        Map<ResourceLocation, CraftingAccessOverride> result = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    if (entry.getKey() == null || entry.getValue() == null
                            || entry.getKey().toString().length()
                                    > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
                        throw new IllegalArgumentException(
                                "crafting exact override contains an invalid entry");
                    }
                    result.put(entry.getKey(), entry.getValue());
                });
        return Collections.unmodifiableMap(result);
    }
}
