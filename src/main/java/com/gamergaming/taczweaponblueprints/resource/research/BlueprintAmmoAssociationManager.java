package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.compat.tacz.TaCZAmmoAssociationAdapter;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.resource.PublicationRevision;
import com.tacz.guns.resource.CommonAssetsManager;

import net.minecraft.resources.ResourceLocation;

/** Owns the latest complete, revision-matched TaCZ ammo-association snapshot. */
public final class BlueprintAmmoAssociationManager {
    public static final BlueprintAmmoAssociationManager INSTANCE =
            new BlueprintAmmoAssociationManager();

    private final TaCZAmmoAssociationAdapter adapter = new TaCZAmmoAssociationAdapter();
    private volatile Publication publication = Publication.EMPTY;

    BlueprintAmmoAssociationManager() {
    }

    public boolean rebuild(
            CommonAssetsManager assetsManager,
            Map<ResourceLocation, BlueprintData> recipeBackedCatalog,
            long catalogRevision) {
        if (catalogRevision <= 0L) {
            throw new IllegalArgumentException("Blueprint catalog revision must be positive");
        }
        try {
            return publish(
                    adapter.capture(recipeBackedCatalog, assetsManager),
                    recipeBackedCatalog,
                    catalogRevision);
        } catch (RuntimeException | LinkageError exception) {
            invalidateForCatalogRevision(catalogRevision);
            TaCZWeaponBlueprints.LOGGER.error(
                    "Unable to rebuild TaCZ ammo associations; invalidated publication revision {}",
                    publication.revision(),
                    exception);
            return false;
        }
    }

    boolean publish(
            TaCZAmmoAssociationAdapter.Capture capture,
            Map<ResourceLocation, BlueprintData> catalog,
            long catalogRevision) {
        if (capture == null || catalog == null) {
            return false;
        }
        if (catalogRevision <= 0L) {
            throw new IllegalArgumentException("Blueprint catalog revision must be positive");
        }
        Set<ResourceLocation> guns = idsForKind(catalog, BlueprintKind.GUN);
        Set<ResourceLocation> ammo = idsForKind(catalog, BlueprintKind.AMMO);
        Set<ResourceLocation> capturedGuns = new LinkedHashSet<>(
                capture.candidateAmmoByGun().keySet());
        capturedGuns.addAll(capture.rejectedGunLinks().keySet());
        if (capture.candidateGunCount() != guns.size() || !capturedGuns.equals(guns)) {
            throw new IllegalArgumentException(
                    "TaCZ ammo association capture does not match the catalog gun partition");
        }

        Map<ResourceLocation, ResourceLocation> trusted = new LinkedHashMap<>();
        Set<ResourceLocation> ambiguousGuns = new LinkedHashSet<>();
        Map<ResourceLocation, Set<ResourceLocation>> ambiguousByAmmo = new LinkedHashMap<>();
        Map<ResourceLocation, String> rejected = new LinkedHashMap<>(
                capture.rejectedGunLinks());
        capture.candidateAmmoByGun().forEach((gunId, candidateIds) -> {
            if (candidateIds.size() == 1) {
                ResourceLocation ammoId = candidateIds.iterator().next();
                if (ammo.contains(ammoId)) {
                    trusted.put(gunId, ammoId);
                } else {
                    rejected.put(
                            gunId,
                            boundedReason("ammo_not_recipe_backed:" + ammoId));
                }
                return;
            }
            Set<ResourceLocation> catalogCandidates = candidateIds.stream()
                    .filter(ammo::contains)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (catalogCandidates.isEmpty()) {
                rejected.put(gunId, "ambiguous_ammo_links_not_recipe_backed");
                return;
            }
            ambiguousGuns.add(gunId);
            catalogCandidates.forEach(ammoId -> ambiguousByAmmo
                    .computeIfAbsent(ammoId, ignored -> new LinkedHashSet<>())
                    .add(gunId));
        });

        Map<ResourceLocation, Set<ResourceLocation>> reverse = new LinkedHashMap<>();
        trusted.forEach((gunId, ammoId) -> reverse
                .computeIfAbsent(ammoId, ignored -> new LinkedHashSet<>())
                .add(gunId));
        long nextRevision = PublicationRevision.next(publication.revision());
        BlueprintAmmoAssociationSnapshot snapshot = new BlueprintAmmoAssociationSnapshot(
                catalogRevision,
                nextRevision,
                guns,
                ammo,
                trusted,
                reverse,
                ambiguousGuns,
                ambiguousByAmmo,
                rejected);
        publication = new Publication(
                snapshot,
                nextRevision,
                catalogRevision,
                PublicationState.READY);
        TaCZWeaponBlueprints.LOGGER.info(
                "Captured TaCZ ammo association revision {}: {}/{} guns linked to {} recipe-backed ammo entries, {} ambiguous, and {} rejected",
                nextRevision,
                trusted.size(),
                guns.size(),
                reverse.size(),
                ambiguousGuns.size(),
                rejected.size());
        return true;
    }

    public Publication publication() {
        return publication;
    }

    public Optional<BlueprintAmmoAssociationSnapshot> snapshotForCatalogRevision(
            long catalogRevision) {
        Publication current = publication;
        return current.state() == PublicationState.READY
                        && current.catalogRevision() == catalogRevision
                        && current.snapshot().matches(catalogRevision, current.revision())
                ? Optional.of(current.snapshot())
                : Optional.empty();
    }

    public void invalidateForCatalogRevision(long catalogRevision) {
        if (catalogRevision < 0L) {
            throw new IllegalArgumentException("Blueprint catalog revision cannot be negative");
        }
        long nextRevision = PublicationRevision.next(publication.revision());
        publication = new Publication(
                BlueprintAmmoAssociationSnapshot.EMPTY,
                nextRevision,
                catalogRevision,
                PublicationState.INVALIDATED);
    }

    public void clear() {
        publication = Publication.EMPTY;
    }

    private static Set<ResourceLocation> idsForKind(
            Map<ResourceLocation, BlueprintData> catalog,
            BlueprintKind kind) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        catalog.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation id = entry.getKey();
                    BlueprintData data = entry.getValue();
                    if (id == null || data == null) {
                        throw new IllegalArgumentException("blueprint catalog contains null");
                    }
                    if ((data.getKind() == BlueprintKind.GUN
                                    || data.getKind() == BlueprintKind.AMMO)
                            && !id.toString().equals(data.getBpId())) {
                        throw new IllegalArgumentException(
                                "blueprint catalog identity does not match its data: " + id);
                    }
                    if (data.getKind() == kind) {
                        result.add(id);
                    }
                });
        if ((long) result.size() > BlueprintAmmoAssociationSnapshot.MAX_CATALOG_ENTRIES) {
            throw new IllegalArgumentException(
                    "blueprint catalog kind exceeds the ammo association size limit");
        }
        return Set.copyOf(result);
    }

    private static String boundedReason(String value) {
        if (value.length() <= BlueprintAmmoAssociationSnapshot.MAX_DIAGNOSTIC_LENGTH) {
            return value;
        }
        return value.substring(
                0, BlueprintAmmoAssociationSnapshot.MAX_DIAGNOSTIC_LENGTH - 3) + "...";
    }

    public enum PublicationState {
        EMPTY,
        READY,
        INVALIDATED
    }

    public record Publication(
            BlueprintAmmoAssociationSnapshot snapshot,
            long revision,
            long catalogRevision,
            PublicationState state) {
        public static final Publication EMPTY = new Publication(
                BlueprintAmmoAssociationSnapshot.EMPTY,
                0L,
                0L,
                PublicationState.EMPTY);

        public Publication {
            if (snapshot == null || revision < 0L || catalogRevision < 0L
                    || state == null
                    || (state == PublicationState.EMPTY
                            && (revision != 0L || catalogRevision != 0L
                                    || !snapshot.equals(BlueprintAmmoAssociationSnapshot.EMPTY)))
                    || (state == PublicationState.READY
                            && (revision == 0L || catalogRevision == 0L
                                    || !snapshot.matches(catalogRevision, revision)))
                    || (state == PublicationState.INVALIDATED
                            && (revision == 0L
                                    || !snapshot.equals(
                                            BlueprintAmmoAssociationSnapshot.EMPTY)))) {
                throw new IllegalArgumentException(
                        "ammo association publication is inconsistent");
            }
        }
    }
}
