package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable, revision-coupled projection of TaCZ's canonical gun-to-ammo data.
 * Shared ammo is represented by the reverse map; ambiguous source facts remain
 * visible but never participate in automatic tier assignment.
 */
public record BlueprintAmmoAssociationSnapshot(
        long catalogRevision,
        long sourceRevision,
        Set<ResourceLocation> gunBlueprintIds,
        Set<ResourceLocation> ammoBlueprintIds,
        Map<ResourceLocation, ResourceLocation> ammoByGun,
        Map<ResourceLocation, Set<ResourceLocation>> gunsByAmmo,
        Set<ResourceLocation> ambiguousGunBlueprintIds,
        Map<ResourceLocation, Set<ResourceLocation>> ambiguousGunsByAmmo,
        Map<ResourceLocation, String> rejectedGunLinks) {
    public static final int MAX_CATALOG_ENTRIES = 4_096;
    public static final int MAX_TOTAL_CANDIDATE_LINKS = 262_144;
    public static final int MAX_DIAGNOSTIC_LENGTH = 160;
    public static final BlueprintAmmoAssociationSnapshot EMPTY =
            new BlueprintAmmoAssociationSnapshot(
                    0L,
                    0L,
                    Set.of(),
                    Set.of(),
                    Map.of(),
                    Map.of(),
                    Set.of(),
                    Map.of(),
                    Map.of());

    public BlueprintAmmoAssociationSnapshot {
        if (catalogRevision < 0L || sourceRevision < 0L
                || gunBlueprintIds == null || ammoBlueprintIds == null
                || ammoByGun == null || gunsByAmmo == null
                || ambiguousGunBlueprintIds == null || ambiguousGunsByAmmo == null
                || rejectedGunLinks == null) {
            throw new IllegalArgumentException("ammo association snapshot is invalid");
        }
        boolean emptyIdentity = catalogRevision == 0L && sourceRevision == 0L;
        if ((catalogRevision == 0L) != (sourceRevision == 0L)
                || (emptyIdentity && (!gunBlueprintIds.isEmpty()
                        || !ammoBlueprintIds.isEmpty()
                        || !ammoByGun.isEmpty()
                        || !gunsByAmmo.isEmpty()
                        || !ambiguousGunBlueprintIds.isEmpty()
                        || !ambiguousGunsByAmmo.isEmpty()
                        || !rejectedGunLinks.isEmpty()))) {
            throw new IllegalArgumentException(
                    "ammo association revisions and empty state are inconsistent");
        }

        gunBlueprintIds = immutableIds(gunBlueprintIds, "gun");
        ammoBlueprintIds = immutableIds(ammoBlueprintIds, "ammo");
        if (!Collections.disjoint(gunBlueprintIds, ammoBlueprintIds)) {
            throw new IllegalArgumentException(
                    "gun and ammo catalog partitions must be disjoint");
        }
        if ((long) gunBlueprintIds.size() + ammoBlueprintIds.size()
                > MAX_CATALOG_ENTRIES) {
            throw new IllegalArgumentException("ammo association catalog exceeds its size limit");
        }
        ammoByGun = immutableLinks(ammoByGun);
        gunsByAmmo = immutableReverseLinks(gunsByAmmo, "linked");
        ambiguousGunBlueprintIds = immutableIds(
                ambiguousGunBlueprintIds, "ambiguous gun");
        ambiguousGunsByAmmo = immutableReverseLinks(
                ambiguousGunsByAmmo, "ambiguous");
        rejectedGunLinks = immutableDiagnostics(rejectedGunLinks);

        if (!gunBlueprintIds.containsAll(ammoByGun.keySet())
                || !ammoBlueprintIds.containsAll(ammoByGun.values())
                || !ammoBlueprintIds.containsAll(gunsByAmmo.keySet())
                || !ammoBlueprintIds.containsAll(ambiguousGunsByAmmo.keySet())
                || !gunBlueprintIds.containsAll(ambiguousGunBlueprintIds)
                || !gunBlueprintIds.containsAll(rejectedGunLinks.keySet())) {
            throw new IllegalArgumentException(
                    "ammo association references an entry outside the catalog partition");
        }
        Set<ResourceLocation> classified = new LinkedHashSet<>(ammoByGun.keySet());
        addDisjoint(classified, ambiguousGunBlueprintIds);
        addDisjoint(classified, rejectedGunLinks.keySet());
        if (!classified.equals(gunBlueprintIds)) {
            throw new IllegalArgumentException(
                    "ammo association does not classify every catalog gun exactly once");
        }

        Map<ResourceLocation, Set<ResourceLocation>> expectedReverse = reverse(ammoByGun);
        if (!expectedReverse.equals(gunsByAmmo)) {
            throw new IllegalArgumentException(
                    "ammo association reverse links do not match canonical gun links");
        }
        Set<ResourceLocation> ambiguousValues = ambiguousGunsByAmmo.values().stream()
                .flatMap(Set::stream)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!ambiguousValues.equals(ambiguousGunBlueprintIds)) {
            throw new IllegalArgumentException(
                    "ambiguous ammo links do not cover the ambiguous gun partition");
        }
        long linkCount = (long) ammoByGun.size()
                + ambiguousGunsByAmmo.values().stream().mapToLong(Set::size).sum();
        if (linkCount > MAX_TOTAL_CANDIDATE_LINKS) {
            throw new IllegalArgumentException("ammo association exceeds its link limit");
        }
    }

    public Set<ResourceLocation> gunsForAmmo(ResourceLocation ammoId) {
        return gunsByAmmo.getOrDefault(ammoId, Set.of());
    }

    public Set<ResourceLocation> ambiguousGunsForAmmo(ResourceLocation ammoId) {
        return ambiguousGunsByAmmo.getOrDefault(ammoId, Set.of());
    }

    public boolean matches(long catalog, long source) {
        return catalogRevision == catalog && sourceRevision == source;
    }

    private static Set<ResourceLocation> immutableIds(
            Set<ResourceLocation> source,
            String label) {
        TreeSet<ResourceLocation> copy = new TreeSet<>(
                java.util.Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation id : source) {
            validateId(id, label);
            copy.add(id);
        }
        return Collections.unmodifiableSet(copy);
    }

    private static Map<ResourceLocation, ResourceLocation> immutableLinks(
            Map<ResourceLocation, ResourceLocation> source) {
        Map<ResourceLocation, ResourceLocation> copy = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    validateId(entry.getKey(), "gun link");
                    validateId(entry.getValue(), "ammo link");
                    copy.put(entry.getKey(), entry.getValue());
                });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<ResourceLocation, Set<ResourceLocation>> immutableReverseLinks(
            Map<ResourceLocation, Set<ResourceLocation>> source,
            String label) {
        Map<ResourceLocation, Set<ResourceLocation>> copy = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    validateId(entry.getKey(), label + " ammo");
                    if (entry.getValue() == null || entry.getValue().isEmpty()) {
                        throw new IllegalArgumentException(
                                label + " ammo reverse links cannot be empty");
                    }
                    copy.put(entry.getKey(), immutableIds(entry.getValue(), label + " gun"));
                });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<ResourceLocation, String> immutableDiagnostics(
            Map<ResourceLocation, String> source) {
        Map<ResourceLocation, String> copy = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    validateId(entry.getKey(), "rejected gun");
                    String reason = entry.getValue();
                    if (reason == null || reason.isBlank()
                            || reason.length() > MAX_DIAGNOSTIC_LENGTH) {
                        throw new IllegalArgumentException(
                                "ammo association rejection reason is invalid");
                    }
                    copy.put(entry.getKey(), reason);
                });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<ResourceLocation, Set<ResourceLocation>> reverse(
            Map<ResourceLocation, ResourceLocation> links) {
        Map<ResourceLocation, Set<ResourceLocation>> mutable = new LinkedHashMap<>();
        links.forEach((gun, ammo) -> mutable.computeIfAbsent(
                ammo, ignored -> new LinkedHashSet<>()).add(gun));
        return immutableReverseLinks(mutable, "linked");
    }

    private static void addDisjoint(
            Set<ResourceLocation> target,
            Set<ResourceLocation> values) {
        for (ResourceLocation value : values) {
            if (!target.add(value)) {
                throw new IllegalArgumentException(
                        "ammo association gun classifications overlap at " + value);
            }
        }
    }

    private static void validateId(ResourceLocation id, String label) {
        if (id == null
                || id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("ammo association " + label + " ID is invalid");
        }
    }
}
