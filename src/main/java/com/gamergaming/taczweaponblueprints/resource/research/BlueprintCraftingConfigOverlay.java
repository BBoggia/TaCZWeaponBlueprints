package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.AmmoCraftingStrategy;
import com.gamergaming.taczweaponblueprints.progression.AttachmentCraftingStrategy;
import com.gamergaming.taczweaponblueprints.progression.CraftingPolicyConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;

import net.minecraft.resources.ResourceLocation;

/** Applies the server settings overlay without changing research inclusion or placement. */
final class BlueprintCraftingConfigOverlay {
    private BlueprintCraftingConfigOverlay() {
    }

    static BlueprintCraftingPolicySnapshot apply(
            BlueprintCraftingPolicySnapshot base,
            Map<ResourceLocation, BlueprintData> catalog,
            BlueprintAmmoAssociationSnapshot associations,
            CraftingPolicyConfigSnapshot config) {
        if (base == null || catalog == null || associations == null || config == null
                || !base.catalogBlueprintIds().equals(catalog.keySet())) {
            throw new IllegalArgumentException("crafting config overlay inputs are invalid");
        }
        if (config.equals(CraftingPolicyConfigSnapshot.PROFILE_DEFAULTS)) {
            return base;
        }

        Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>> resolved =
                new LinkedHashMap<>();
        base.policiesByProfile().forEach((profileId, basePolicies) -> {
            Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> profile =
                    new LinkedHashMap<>();

            catalog.forEach((blueprintId, data) -> {
                if (data.getKind() == BlueprintKind.GUN) {
                    profile.put(blueprintId, applySelectors(
                            basePolicies.get(blueprintId), data, config, Optional.empty()));
                }
            });
            catalog.forEach((blueprintId, data) -> {
                if (data.getKind() == BlueprintKind.AMMO) {
                    Optional<BlueprintCraftingAccessPolicy> category = ammoAccess(
                            blueprintId, profile, associations, config);
                    profile.put(blueprintId, applySelectors(
                            basePolicies.get(blueprintId), data, config, category));
                } else if (data.getKind() == BlueprintKind.ATTACHMENT) {
                    Optional<BlueprintCraftingAccessPolicy> category = config
                            .attachmentStrategy().fixedAccess();
                    profile.put(blueprintId, applySelectors(
                            basePolicies.get(blueprintId), data, config, category));
                }
            });
            resolved.put(profileId, profile);
        });

        return BlueprintCraftingPolicySnapshot.create(
                base.catalogRevision(),
                base.researchRevision(),
                base.automaticRevision(),
                base.catalogBlueprintIds(),
                resolved);
    }

    private static Optional<BlueprintCraftingAccessPolicy> ammoAccess(
            ResourceLocation ammoId,
            Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> resolvedGuns,
            BlueprintAmmoAssociationSnapshot associations,
            CraftingPolicyConfigSnapshot config) {
        AmmoCraftingStrategy strategy = config.ammoStrategy();
        Optional<BlueprintCraftingAccessPolicy> fixed = strategy.fixedAccess();
        if (fixed.isPresent() || strategy == AmmoCraftingStrategy.PROFILE) {
            return fixed;
        }
        Optional<ResearchWorkbenchTier> linkedTier = associations.gunsForAmmo(ammoId).stream()
                .map(resolvedGuns::get)
                .filter(java.util.Objects::nonNull)
                .filter(policy -> policy.disposition() == BlueprintCraftingDisposition.TIERED)
                .map(policy -> policy.requiredWorkbenchTier().orElseThrow())
                .min(java.util.Comparator.comparingInt(ResearchWorkbenchTier::level));
        return Optional.of(BlueprintCraftingAccessPolicy.tiered(
                linkedTier.orElse(config.linkedAmmoFallbackTier())));
    }

    private static ResolvedBlueprintCraftingPolicy applySelectors(
            ResolvedBlueprintCraftingPolicy base,
            BlueprintData data,
            CraftingPolicyConfigSnapshot config,
            Optional<BlueprintCraftingAccessPolicy> categoryAccess) {
        if (base == null || data == null) {
            throw new IllegalStateException("crafting config overlay is missing a catalog policy");
        }
        var exact = config.exactOverrides().get(base.blueprintId());
        if (exact != null) {
            return override(base, exact.accessPolicy(), "config_exact_override");
        }
        BlueprintKind kind = data.getKind();
        String itemType = normalizedItemType(data.getItemType());
        if (config.disabledKinds().contains(kind)
                || config.disabledItemTypes().contains(itemType)) {
            return override(base, BlueprintCraftingAccessPolicy.DISABLED,
                    "config_disabled_selector");
        }
        if (config.unrestrictedKinds().contains(kind)
                || config.unrestrictedItemTypes().contains(itemType)) {
            return override(base, BlueprintCraftingAccessPolicy.UNRESTRICTED,
                    "config_unrestricted_selector");
        }
        return categoryAccess
                .map(access -> override(base, access, "config_category_strategy"))
                .orElse(base);
    }

    private static ResolvedBlueprintCraftingPolicy override(
            ResolvedBlueprintCraftingPolicy base,
            BlueprintCraftingAccessPolicy access,
            String reason) {
        return new ResolvedBlueprintCraftingPolicy(
                base.profileId(),
                base.blueprintId(),
                access.disposition(),
                access.workbenchTier(),
                base.gates(),
                BlueprintCraftingPolicySource.CONFIG_OVERRIDE,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                reason,
                Set.of());
    }

    private static String normalizedItemType(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
