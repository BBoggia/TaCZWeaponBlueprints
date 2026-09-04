package com.gamergaming.taczweaponblueprints.resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.network.BlueprintSyncLimits;
import com.gamergaming.taczweaponblueprints.progression.BlueprintLearningService;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceManager;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintAmmoAssociationManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintProgressionPolicyManager;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeCatalogValidator;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModRecipe;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

public class BlueprintDataManager {

    private static final String BLUEPRINT_TOOLTIP_KEY = "item.taczweaponblueprints.blueprint.tooltip";
    private static final int MAX_DIAGNOSTIC_SAMPLES = 12;
    public static final int MAX_CATALOG_ENTRIES = 4096;

    public static final BlueprintDataManager SERVER = new BlueprintDataManager();
    public static final BlueprintDataManager CLIENT = new BlueprintDataManager();

    /**
     * @deprecated Use {@link #SERVER} or {@link #CLIENT} explicitly so integrated
     *             servers cannot have their authoritative catalog overwritten by
     *             a client synchronization packet.
     */
    @Deprecated
    public static final BlueprintDataManager INSTANCE = SERVER;

    /**
     * Catalog snapshots are replaced only after a complete rebuild so readers never
     * observe a partially populated map during a login or resource reload.
     */
    private volatile CatalogSnapshot catalogSnapshot = CatalogSnapshot.EMPTY;

    private BlueprintDataManager() { }

    public static BlueprintDataManager presentationCatalog() {
        return FMLEnvironment.dist == Dist.CLIENT ? CLIENT : SERVER;
    }

    public boolean initialize(MinecraftServer server) {
        TaCZWeaponBlueprints.LOGGER.info("Initializing blueprint catalog from TaCZ recipes");

        CommonAssetsManager assetsManager = CommonAssetsManager.getInstance();
        if (assetsManager == null || assetsManager.recipeManager == null) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Unable to rebuild blueprint catalog because the TaCZ recipe manager is unavailable; "
                            + "preserving the previous {}-entry snapshot",
                    catalogSnapshot.blueprints().size());
            return false;
        }

        try {
            CatalogRebuild rebuild = rebuildCatalog(assetsManager);
            CatalogSnapshot rebuilt = rebuild.snapshot();
            BlueprintResearchDataManager.Publication research =
                    BlueprintResearchDataManager.INSTANCE.publication();
            ResearchTechTreeCatalogValidator.validate(
                    research.snapshot(), rebuilt.blueprints());
            catalogSnapshot = rebuilt;
            rebuild.diagnostics().log(rebuilt.blueprints().size());
            boolean ammoAssociationsReady = BlueprintAmmoAssociationManager.INSTANCE.rebuild(
                    assetsManager,
                    rebuilt.blueprints(),
                    rebuilt.revision());
            if (!ammoAssociationsReady) {
                return false;
            }
            boolean evidenceReady = AutomaticWeaponEvidenceManager.INSTANCE.rebuild(
                    assetsManager,
                    rebuilt.blueprints(),
                    rebuilt.revision());
            if (!evidenceReady) {
                return false;
            }
            if (research.revision() > 0L) {
                boolean automaticReady = AutomaticWeaponPlacementCandidateManager.INSTANCE.rebuild(
                        research.snapshot(),
                        research.revision(),
                        rebuilt.blueprints(),
                        rebuilt.revision(),
                        AutomaticWeaponEvidenceManager.INSTANCE.snapshotForCatalogRevision(
                                rebuilt.revision()));
                if (!automaticReady) {
                    return false;
                }
            } else {
                AutomaticWeaponPlacementCandidateManager.INSTANCE.invalidateForRevisions(
                        rebuilt.revision(), research.revision());
            }
            return rebuildProgressionPolicy();
        } catch (RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Unable to finish blueprint publication rebuild; retaining the latest complete "
                            + "{}-entry catalog snapshot",
                    catalogSnapshot.blueprints().size(),
                    exception);
            return false;
        }
    }

    /** Rebuilds config-sensitive progression policy from already-published immutable inputs. */
    public boolean rebuildProgressionPolicy() {
        if (this != SERVER) {
            return false;
        }
        CatalogPublication catalog = catalogPublication();
        BlueprintResearchDataManager.Publication research =
                BlueprintResearchDataManager.INSTANCE.publication();
        if (catalog.revision() <= 0L || research.revision() <= 0L) {
            return false;
        }
        return BlueprintProgressionPolicyManager.INSTANCE.rebuild(
                research.snapshot(),
                research.revision(),
                catalog.blueprints(),
                catalog.revision(),
                AutomaticWeaponPlacementCandidateManager.INSTANCE.publication(),
                AutomaticWeaponEvidenceManager.INSTANCE.publication(),
                BlueprintAmmoAssociationManager.INSTANCE.publication(),
                ModConfigs.BLUEPRINT.researchFeatureSnapshot());
    }

    /**
     * Rebuilds research-revision-sensitive derived publications from the current
     * complete catalog snapshot. This is the recovery path used when a normal
     * catalog initialization cannot finish every dependent publication.
     */
    public boolean rebuildDerivedPublicationsFromRetainedCatalog() {
        if (this != SERVER) {
            return false;
        }
        CatalogPublication catalog = catalogPublication();
        BlueprintResearchDataManager.Publication research =
                BlueprintResearchDataManager.INSTANCE.publication();
        if (catalog.revision() <= 0L || research.revision() <= 0L) {
            return false;
        }
        try {
            CommonAssetsManager assetsManager = null;
            if (BlueprintAmmoAssociationManager.INSTANCE
                    .snapshotForCatalogRevision(catalog.revision())
                    .isEmpty()) {
                assetsManager = CommonAssetsManager.getInstance();
                if (assetsManager == null
                        || !BlueprintAmmoAssociationManager.INSTANCE.rebuild(
                                assetsManager,
                                catalog.blueprints(),
                                catalog.revision())) {
                    return false;
                }
            }
            if (!AutomaticWeaponEvidenceManager.INSTANCE.publication()
                    .readyForCatalogRevision(catalog.revision())) {
                if (assetsManager == null) {
                    assetsManager = CommonAssetsManager.getInstance();
                }
                if (assetsManager == null
                        || !AutomaticWeaponEvidenceManager.INSTANCE.rebuild(
                                assetsManager,
                                catalog.blueprints(),
                                catalog.revision())) {
                    return false;
                }
            }
            if (!research.snapshot().automaticPlacementProfiles().isEmpty()) {
                boolean automaticReady = AutomaticWeaponPlacementCandidateManager.INSTANCE.rebuild(
                        research.snapshot(),
                        research.revision(),
                        catalog.blueprints(),
                        catalog.revision(),
                        AutomaticWeaponEvidenceManager.INSTANCE.snapshotForCatalogRevision(
                                catalog.revision()));
                if (!automaticReady) {
                    return false;
                }
            }
            return rebuildProgressionPolicy();
        } catch (RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Unable to rebuild derived blueprint publications from retained catalog revision {}",
                    catalog.revision(),
                    exception);
            return false;
        }
    }

    private CatalogRebuild rebuildCatalog(CommonAssetsManager assetsManager) {
        List<GunSmithTableRecipe> recipes = new ArrayList<>(
                assetsManager.recipeManager.getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get()));
        recipes.sort(Comparator.comparing(recipe -> recipe.getId().toString()));

        Map<ResourceLocation, BlueprintData> rebuiltCatalog = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> recipeToBlueprint = new LinkedHashMap<>();
        CatalogDiagnostics diagnostics = new CatalogDiagnostics(recipes.size());

        for (GunSmithTableRecipe recipe : recipes) {
            try {
                BlueprintResolution resolution = resolveBlueprint(recipe, assetsManager);
                if (!resolution.isSuccess()) {
                    diagnostics.recordSkip(recipe.getId(), resolution.skipReason(), resolution.detail());
                    continue;
                }

                BlueprintCandidate candidate = resolution.candidate();
                recipeToBlueprint.put(recipe.getId(), candidate.itemId());
                BlueprintData existing = rebuiltCatalog.putIfAbsent(candidate.itemId(), candidate.data());
                if (existing != null) {
                    diagnostics.recordSkip(
                            recipe.getId(),
                            SkipReason.DUPLICATE_OUTPUT,
                            "output " + candidate.itemId() + " is already provided by " + existing.getRecipeId());
                    continue;
                }

                diagnostics.recordRegistered(candidate.kind());
            } catch (RuntimeException exception) {
                diagnostics.recordSkip(
                        recipe.getId(),
                        SkipReason.RESOLUTION_ERROR,
                        exception.getClass().getSimpleName() + formatExceptionMessage(exception));
            }
        }

        if (rebuiltCatalog.size() > MAX_CATALOG_ENTRIES) {
            throw new IllegalStateException(
                    "blueprint catalog contains " + rebuiltCatalog.size() + " entries; maximum is "
                            + MAX_CATALOG_ENTRIES);
        }

        BlueprintSyncLimits.validateCatalog(rebuiltCatalog);
        CatalogSnapshot rebuilt = new CatalogSnapshot(
                immutableCatalog(rebuiltCatalog),
                immutableAliases(recipeToBlueprint),
                PublicationRevision.next(catalogSnapshot.revision()));
        return new CatalogRebuild(rebuilt, diagnostics);
    }

    private BlueprintResolution resolveBlueprint(GunSmithTableRecipe recipe, CommonAssetsManager assetsManager) {
        ItemStack output = recipe.getOutput();
        if (output == null || output.isEmpty()) {
            return BlueprintResolution.skipped(SkipReason.EMPTY_OUTPUT, "recipe result is empty or unresolved");
        }

        IGun gun = IGun.getIGunOrNull(output);
        if (gun != null) {
            ResourceLocation itemId = gun.getGunId(output);
            if (itemId == null) {
                return BlueprintResolution.skipped(SkipReason.MISSING_ITEM_ID, "gun result has no TaCZ gun ID");
            }

            CommonGunIndex index = assetsManager.getGunIndex(itemId);
            if (index == null || index.getPojo() == null) {
                return BlueprintResolution.skipped(SkipReason.MISSING_INDEX, "no gun index exists for " + itemId);
            }

            return createCandidate(
                    recipe,
                    itemId,
                    index.getPojo().getName(),
                    index.getType(),
                    index.getPojo().getDisplay(),
                    BlueprintKind.GUN);
        }

        IAmmo ammo = IAmmo.getIAmmoOrNull(output);
        if (ammo != null) {
            ResourceLocation itemId = ammo.getAmmoId(output);
            if (itemId == null) {
                return BlueprintResolution.skipped(SkipReason.MISSING_ITEM_ID, "ammo result has no TaCZ ammo ID");
            }

            CommonAmmoIndex index = assetsManager.getAmmoIndex(itemId);
            if (index == null || index.getPojo() == null) {
                return BlueprintResolution.skipped(SkipReason.MISSING_INDEX, "no ammo index exists for " + itemId);
            }

            return createCandidate(
                    recipe,
                    itemId,
                    index.getPojo().getName(),
                    "ammo",
                    index.getPojo().getDisplay(),
                    BlueprintKind.AMMO);
        }

        IAttachment attachment = IAttachment.getIAttachmentOrNull(output);
        if (attachment != null) {
            ResourceLocation itemId = attachment.getAttachmentId(output);
            if (itemId == null) {
                return BlueprintResolution.skipped(SkipReason.MISSING_ITEM_ID, "attachment result has no TaCZ attachment ID");
            }

            CommonAttachmentIndex index = assetsManager.getAttachmentIndex(itemId);
            if (index == null || index.getPojo() == null || index.getType() == null) {
                return BlueprintResolution.skipped(SkipReason.MISSING_INDEX, "no attachment index exists for " + itemId);
            }

            return createCandidate(
                    recipe,
                    itemId,
                    index.getPojo().getName(),
                    index.getType().name(),
                    index.getPojo().getDisplay(),
                    BlueprintKind.ATTACHMENT);
        }

        return BlueprintResolution.skipped(
                SkipReason.UNSUPPORTED_OUTPUT,
                "result item " + output.getItem().getClass().getSimpleName() + " is not a gun, ammo, or attachment");
    }

    private BlueprintResolution createCandidate(
            GunSmithTableRecipe recipe,
            ResourceLocation itemId,
            String nameKey,
            String itemType,
            ResourceLocation displaySlotKey,
            BlueprintKind kind) {
        if (nameKey == null || nameKey.isBlank()) {
            return BlueprintResolution.skipped(SkipReason.MISSING_NAME, "index " + itemId + " has no name translation key");
        }
        if (itemType == null || itemType.isBlank()) {
            return BlueprintResolution.skipped(SkipReason.MISSING_ITEM_TYPE, "index " + itemId + " has no blueprint category");
        }
        if (displaySlotKey == null) {
            return BlueprintResolution.skipped(SkipReason.MISSING_DISPLAY_SLOT, "index " + itemId + " has no display slot");
        }

        String normalizedItemType = itemType.toLowerCase(Locale.ROOT);
        BlueprintData data = new BlueprintData(
                itemId.toString(),
                nameKey,
                BLUEPRINT_TOOLTIP_KEY,
                recipe.getId(),
                recipe,
                normalizedItemType,
                displaySlotKey,
                kind);
        return BlueprintResolution.success(new BlueprintCandidate(itemId, data, kind));
    }

    private static String formatExceptionMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "" : ": " + message;
    }

    private static Map<ResourceLocation, BlueprintData> immutableCatalog(Map<ResourceLocation, BlueprintData> catalog) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(catalog));
    }

    public Map<ResourceLocation, BlueprintData> getBlueprintDataMap() {
        return catalogSnapshot.blueprints();
    }

    public void setBlueprintDataMap(Map<ResourceLocation, BlueprintData> blueprintDataMap) {
        if (blueprintDataMap == null || blueprintDataMap.isEmpty()) {
            this.catalogSnapshot = new CatalogSnapshot(
                    Map.of(),
                    Map.of(),
                    PublicationRevision.next(catalogSnapshot.revision()));
            invalidateServerEvidenceAfterDirectCatalogReplacement();
            return;
        }
        BlueprintSyncLimits.validateCatalog(blueprintDataMap);
        Map<ResourceLocation, ResourceLocation> canonicalRecipes = new LinkedHashMap<>();
        blueprintDataMap.forEach((blueprintId, data) -> canonicalRecipes.put(data.getRecipeId(), blueprintId));
        this.catalogSnapshot = new CatalogSnapshot(
                immutableCatalog(blueprintDataMap),
                immutableAliases(canonicalRecipes),
                PublicationRevision.next(catalogSnapshot.revision()));
        invalidateServerEvidenceAfterDirectCatalogReplacement();
    }

    /** Clears this side's catalog at a connection or server-lifecycle boundary. */
    public void clear() {
        catalogSnapshot = CatalogSnapshot.EMPTY;
        invalidateServerEvidenceAfterDirectCatalogReplacement();
    }

    private void invalidateServerEvidenceAfterDirectCatalogReplacement() {
        if (this == SERVER) {
            BlueprintAmmoAssociationManager.INSTANCE.invalidateForCatalogRevision(
                    catalogSnapshot.revision());
            AutomaticWeaponEvidenceManager.INSTANCE.invalidateForCatalogRevision(
                    catalogSnapshot.revision());
            AutomaticWeaponPlacementCandidateManager.INSTANCE.invalidateForRevisions(
                    catalogSnapshot.revision(), BlueprintResearchDataManager.INSTANCE.revision());
        }
    }

    /**
     * @deprecated Prefer carrying the blueprint output ID directly. TaCZ recipe
     *             IDs do not reliably encode their output IDs.
     */
    @Deprecated
    public static String getBlueprintIdFromResourceLocation(ResourceLocation recipeId) {
        if (recipeId == null) {
            return null;
        }
        ResourceLocation blueprintId = SERVER.getBlueprintIdForRecipe(recipeId);
        return blueprintId == null ? null : blueprintId.toString();
    }

    public BlueprintData getBlueprintData(String bpId) {
        ResourceLocation blueprintId = bpId == null ? null : ResourceLocation.tryParse(bpId);
        if (blueprintId == null) {
            return null;
        }
        return catalogSnapshot.blueprints().get(blueprintId);
    }

    public Collection<BlueprintData> getAllBlueprints() {
        return catalogSnapshot.blueprints().values();
    }

    public Map<ResourceLocation, ResourceLocation> getRecipeToBlueprintMap() {
        return catalogSnapshot.recipeToBlueprint();
    }

    public long catalogRevision() {
        return catalogSnapshot.revision();
    }

    /** Returns the immutable catalog and its revision from one volatile read. */
    public CatalogPublication catalogPublication() {
        CatalogSnapshot current = catalogSnapshot;
        return new CatalogPublication(current.blueprints(), current.revision());
    }

    public ResourceLocation getBlueprintIdForRecipe(ResourceLocation recipeId) {
        return recipeId == null ? null : catalogSnapshot.recipeToBlueprint().get(recipeId);
    }

    public ResourceLocation getCanonicalRecipeId(ResourceLocation recipeId) {
        ResourceLocation blueprintId = getBlueprintIdForRecipe(recipeId);
        BlueprintData data = blueprintId == null ? null : catalogSnapshot.blueprints().get(blueprintId);
        return data == null ? null : data.getRecipeId();
    }

    public int migrateLegacyUnlocks(IPlayerRecipeData recipeData) {
        return BlueprintLearningService.migrateLegacyUnlocks(this, recipeData);
    }

    public Collection<BlueprintData> getRifleBlueprints() {
        return getBlueprintsByType("rifle");
    }

    public Collection<BlueprintData> getPistolBlueprints() {
        return getBlueprintsByType("pistol");
    }

    public Collection<BlueprintData> getSniperBlueprints() {
        return getBlueprintsByType("sniper");
    }

    public Collection<BlueprintData> getShotgunBlueprints() {
        return getBlueprintsByType("shotgun");
    }

    public Collection<BlueprintData> getSmgBlueprints() {
        return getBlueprintsByType("smg");
    }

    public Collection<BlueprintData> getRpgBlueprints() {
        return getBlueprintsByType("rpg");
    }

    public Collection<BlueprintData> getMgBlueprints() {
        return getBlueprintsByType("mg");
    }

    public Collection<BlueprintData> getAmmoBlueprints() {
        return getBlueprintsByType("ammo");
    }

    public Collection<BlueprintData> getExtendedMagBlueprints() {
        return getBlueprintsByType("extended_mag");
    }

    public Collection<BlueprintData> getScopeBlueprints() {
        return getBlueprintsByType("scope");
    }

    public Collection<BlueprintData> getMuzzleBlueprints() {
        return getBlueprintsByType("muzzle");
    }

    public Collection<BlueprintData> getStockBlueprints() {
        return getBlueprintsByType("stock");
    }

    public Collection<BlueprintData> getGripBlueprints() {
        return getBlueprintsByType("grip");
    }

    public Collection<BlueprintData> getBlueprintsByType(String itemType) {
        if (itemType == null) {
            return List.of();
        }

        List<BlueprintData> blueprints = new ArrayList<>();
        for (BlueprintData data : catalogSnapshot.blueprints().values()) {
            if (data.getItemType().equals(itemType)) {
                blueprints.add(data);
            }
        }
        return List.copyOf(blueprints);
    }

    private static Map<ResourceLocation, ResourceLocation> immutableAliases(
            Map<ResourceLocation, ResourceLocation> aliases) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(aliases));
    }

    private record CatalogSnapshot(
            Map<ResourceLocation, BlueprintData> blueprints,
            Map<ResourceLocation, ResourceLocation> recipeToBlueprint,
            long revision) {
        private static final CatalogSnapshot EMPTY = new CatalogSnapshot(Map.of(), Map.of(), 0L);

        private CatalogSnapshot {
            if (blueprints == null || recipeToBlueprint == null || revision < 0) {
                throw new IllegalArgumentException("Blueprint catalog snapshot is invalid");
            }
        }
    }

    private record CatalogRebuild(
            CatalogSnapshot snapshot,
            CatalogDiagnostics diagnostics) {
        private CatalogRebuild {
            if (snapshot == null || diagnostics == null) {
                throw new IllegalArgumentException("Blueprint catalog rebuild is invalid");
            }
        }
    }

    public record CatalogPublication(
            Map<ResourceLocation, BlueprintData> blueprints,
            long revision) {
        public CatalogPublication {
            if (blueprints == null || revision < 0L) {
                throw new IllegalArgumentException("Blueprint catalog publication is invalid");
            }
        }
    }

    private enum SkipReason {
        EMPTY_OUTPUT("empty output"),
        MISSING_ITEM_ID("missing item ID"),
        MISSING_INDEX("missing TaCZ index"),
        MISSING_NAME("missing name"),
        MISSING_ITEM_TYPE("missing item type"),
        MISSING_DISPLAY_SLOT("missing display slot"),
        UNSUPPORTED_OUTPUT("unsupported output"),
        DUPLICATE_OUTPUT("duplicate output"),
        RESOLUTION_ERROR("resolution error");

        private final String description;

        SkipReason(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    private record BlueprintCandidate(ResourceLocation itemId, BlueprintData data, BlueprintKind kind) { }

    private record BlueprintResolution(BlueprintCandidate candidate, SkipReason skipReason, String detail) {
        private static BlueprintResolution success(BlueprintCandidate candidate) {
            return new BlueprintResolution(candidate, null, "");
        }

        private static BlueprintResolution skipped(SkipReason skipReason, String detail) {
            return new BlueprintResolution(null, skipReason, detail);
        }

        private boolean isSuccess() {
            return candidate != null;
        }
    }

    private static final class CatalogDiagnostics {
        private final int recipeCount;
        private final EnumMap<BlueprintKind, Integer> registeredByKind = new EnumMap<>(BlueprintKind.class);
        private final EnumMap<SkipReason, Integer> skippedByReason = new EnumMap<>(SkipReason.class);
        private final List<String> invalidSamples = new ArrayList<>();
        private final List<String> duplicateSamples = new ArrayList<>();

        private CatalogDiagnostics(int recipeCount) {
            this.recipeCount = recipeCount;
        }

        private void recordRegistered(BlueprintKind kind) {
            registeredByKind.merge(kind, 1, Integer::sum);
        }

        private void recordSkip(ResourceLocation recipeId, SkipReason reason, String detail) {
            skippedByReason.merge(reason, 1, Integer::sum);
            List<String> samples = reason == SkipReason.DUPLICATE_OUTPUT ? duplicateSamples : invalidSamples;
            if (samples.size() < MAX_DIAGNOSTIC_SAMPLES) {
                samples.add(recipeId + " (" + reason + ": " + detail + ")");
            }
        }

        private void log(int registeredCount) {
            int duplicateCount = skippedByReason.getOrDefault(SkipReason.DUPLICATE_OUTPUT, 0);
            int invalidCount = skippedByReason.entrySet().stream()
                    .filter(entry -> entry.getKey() != SkipReason.DUPLICATE_OUTPUT)
                    .mapToInt(Map.Entry::getValue)
                    .sum();
            TaCZWeaponBlueprints.LOGGER.info(
                    "Blueprint catalog initialized: {} registered from {} TaCZ recipes ({} guns, {} ammo, {} attachments)",
                    registeredCount,
                    recipeCount,
                    registeredByKind.getOrDefault(BlueprintKind.GUN, 0),
                    registeredByKind.getOrDefault(BlueprintKind.AMMO, 0),
                    registeredByKind.getOrDefault(BlueprintKind.ATTACHMENT, 0));

            if (duplicateCount > 0) {
                TaCZWeaponBlueprints.LOGGER.info(
                        "Ignored {} duplicate TaCZ recipe aliases. First {} examples: {}",
                        duplicateCount,
                        duplicateSamples.size(),
                        duplicateSamples);
            }

            if (invalidCount > 0) {
                EnumMap<SkipReason, Integer> invalidReasons = new EnumMap<>(skippedByReason);
                invalidReasons.remove(SkipReason.DUPLICATE_OUTPUT);
                TaCZWeaponBlueprints.LOGGER.warn(
                        "Skipped {} invalid TaCZ blueprint recipes. Reasons: {}. First {} examples: {}",
                        invalidCount,
                        invalidReasons,
                        invalidSamples.size(),
                        invalidSamples);
            }
        }
    }
}
