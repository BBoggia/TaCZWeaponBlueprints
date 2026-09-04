package com.gamergaming.taczweaponblueprints.compat.tacz;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintAmmoAssociationSnapshot;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;

import net.minecraft.resources.ResourceLocation;

/** Reads canonical ammunition IDs from TaCZ's validated common gun data. */
public final class TaCZAmmoAssociationAdapter {
    public Capture capture(
            Map<ResourceLocation, BlueprintData> recipeBackedCatalog,
            CommonAssetsManager assetsManager) {
        if (assetsManager == null) {
            throw new IllegalArgumentException("TaCZ common assets manager cannot be null");
        }
        return capture(
                recipeBackedCatalog,
                id -> gunData(assetsManager.getGunIndex(id)));
    }

    Capture capture(
            Map<ResourceLocation, BlueprintData> recipeBackedCatalog,
            Function<ResourceLocation, GunData> lookup) {
        if (recipeBackedCatalog == null || lookup == null
                || recipeBackedCatalog.entrySet().stream().anyMatch(entry ->
                        entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException("TaCZ ammo association inputs are invalid");
        }
        var guns = recipeBackedCatalog.entrySet().stream()
                .filter(entry -> entry.getValue().getKind() == BlueprintKind.GUN)
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ResourceLocation::toString)))
                .toList();
        if (guns.size() > BlueprintAmmoAssociationSnapshot.MAX_CATALOG_ENTRIES) {
            throw new IllegalArgumentException(
                    "TaCZ ammo association exceeds the catalog size limit");
        }

        Map<ResourceLocation, Set<ResourceLocation>> candidateAmmo = new LinkedHashMap<>();
        Map<ResourceLocation, String> rejected = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, BlueprintData> entry : guns) {
            ResourceLocation gunId = entry.getKey();
            if (!gunId.toString().equals(entry.getValue().getBpId())) {
                rejected.put(gunId, "catalog_identity_mismatch");
                continue;
            }
            try {
                GunData data = lookup.apply(gunId);
                if (data == null) {
                    rejected.put(gunId, "missing_tacz_gun_index");
                    continue;
                }
                ResourceLocation ammoId = data.getAmmoId();
                if (ammoId == null || DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
                    rejected.put(gunId, "missing_tacz_ammo_id");
                    continue;
                }
                candidateAmmo.put(gunId, Set.of(ammoId));
            } catch (RuntimeException exception) {
                rejected.put(
                        gunId,
                        boundedReason("invalid_tacz_ammo_link:"
                                + exception.getClass().getSimpleName()));
            }
        }
        return new Capture(guns.size(), candidateAmmo, rejected);
    }

    private static GunData gunData(CommonGunIndex index) {
        return index == null ? null : index.getGunData();
    }

    private static String boundedReason(String value) {
        if (value.length() <= BlueprintAmmoAssociationSnapshot.MAX_DIAGNOSTIC_LENGTH) {
            return value;
        }
        return value.substring(
                0, BlueprintAmmoAssociationSnapshot.MAX_DIAGNOSTIC_LENGTH - 3) + "...";
    }

    /**
     * Raw source facts. A set is retained so conflicting future data sources can
     * be represented and rejected instead of silently choosing one ammo ID.
     */
    public record Capture(
            int candidateGunCount,
            Map<ResourceLocation, Set<ResourceLocation>> candidateAmmoByGun,
            Map<ResourceLocation, String> rejectedGunLinks) {
        public Capture {
            if (candidateGunCount < 0
                    || candidateGunCount > BlueprintAmmoAssociationSnapshot.MAX_CATALOG_ENTRIES
                    || candidateAmmoByGun == null || rejectedGunLinks == null) {
                throw new IllegalArgumentException("TaCZ ammo association capture is invalid");
            }
            candidateAmmoByGun = immutableCandidates(candidateAmmoByGun);
            rejectedGunLinks = immutableRejections(rejectedGunLinks);
            Set<ResourceLocation> classified = new LinkedHashSet<>(
                    candidateAmmoByGun.keySet());
            for (ResourceLocation gunId : rejectedGunLinks.keySet()) {
                if (!classified.add(gunId)) {
                    throw new IllegalArgumentException(
                            "TaCZ ammo association capture classifications overlap");
                }
            }
            long linkCount = candidateAmmoByGun.values().stream()
                    .mapToLong(Set::size)
                    .sum();
            if (classified.size() != candidateGunCount
                    || linkCount > BlueprintAmmoAssociationSnapshot.MAX_TOTAL_CANDIDATE_LINKS) {
                throw new IllegalArgumentException(
                        "TaCZ ammo association capture coverage is inconsistent");
            }
        }

        private static Map<ResourceLocation, Set<ResourceLocation>> immutableCandidates(
                Map<ResourceLocation, Set<ResourceLocation>> source) {
            Map<ResourceLocation, Set<ResourceLocation>> copy = new LinkedHashMap<>();
            source.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            java.util.Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> {
                        requireId(entry.getKey());
                        if (entry.getValue() == null || entry.getValue().isEmpty()) {
                            throw new IllegalArgumentException(
                                    "TaCZ ammo candidate links cannot be empty");
                        }
                        Set<ResourceLocation> ids = new LinkedHashSet<>();
                        entry.getValue().stream()
                                .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                                .forEach(id -> {
                                    requireId(id);
                                    ids.add(id);
                                });
                        copy.put(entry.getKey(), Collections.unmodifiableSet(ids));
                    });
            return Collections.unmodifiableMap(copy);
        }

        private static Map<ResourceLocation, String> immutableRejections(
                Map<ResourceLocation, String> source) {
            Map<ResourceLocation, String> copy = new LinkedHashMap<>();
            source.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            java.util.Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> {
                        requireId(entry.getKey());
                        String reason = entry.getValue();
                        if (reason == null || reason.isBlank()
                                || reason.length()
                                        > BlueprintAmmoAssociationSnapshot.MAX_DIAGNOSTIC_LENGTH) {
                            throw new IllegalArgumentException(
                                    "TaCZ ammo association rejection is invalid");
                        }
                        copy.put(entry.getKey(), reason);
                    });
            return Collections.unmodifiableMap(copy);
        }

        private static void requireId(ResourceLocation id) {
            if (id == null) {
                throw new IllegalArgumentException(
                        "TaCZ ammo association capture contains a null ID");
            }
        }
    }
}
