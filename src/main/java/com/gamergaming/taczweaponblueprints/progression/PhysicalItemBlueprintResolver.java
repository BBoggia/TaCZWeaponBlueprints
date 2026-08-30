package com.gamergaming.taczweaponblueprints.progression;

import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.pojo.data.gun.Bolt;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Resolves physical TaCZ content without consulting Forge item registry IDs. */
public final class PhysicalItemBlueprintResolver {
    private PhysicalItemBlueprintResolver() {
    }

    public static Resolution resolve(
            ItemStack stack,
            Map<ResourceLocation, BlueprintData> catalog) {
        return resolve(stack, catalog, PhysicalItemBlueprintResolver::inspectTaCZIdentity);
    }

    static Resolution resolve(
            ItemStack stack,
            Map<ResourceLocation, BlueprintData> catalog,
            IdentityAdapter adapter) {
        if (stack == null || stack.isEmpty()) {
            return Resolution.failure(Status.EMPTY_INPUT);
        }
        if (adapter == null) {
            throw new IllegalArgumentException("physical-item identity adapter cannot be null");
        }
        Map<ResourceLocation, BlueprintData> stableCatalog = catalog == null ? Map.of() : catalog;
        // Several TaCZ accessors use getOrCreateTag even for reads. Inspecting a
        // defensive copy is therefore required for a genuinely pure preview.
        ItemStack inspected = stack.copy();
        try {
            InspectedIdentity identity = adapter.inspect(inspected);
            if (identity == null) {
                return Resolution.failure(Status.UNSUPPORTED_ITEM);
            }
            return resolveIdentity(
                    inspected,
                    identity.blueprintId(),
                    identity.kind(),
                    identity.loadedGun(),
                    identity.containsAttachments(),
                    identity.modified(),
                    stableCatalog);
        } catch (RuntimeException exception) {
            return Resolution.failure(Status.INVALID_ITEM_DATA);
        }
    }

    private static InspectedIdentity inspectTaCZIdentity(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun != null) {
            return inspectGun(stack, gun);
        }
        IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
        if (ammo != null) {
            return new InspectedIdentity(
                    ammo.getAmmoId(stack),
                    BlueprintKind.AMMO,
                    false,
                    false,
                    stack.hasCustomHoverName());
        }
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        if (attachment != null) {
            return new InspectedIdentity(
                    attachment.getAttachmentId(stack),
                    BlueprintKind.ATTACHMENT,
                    false,
                    false,
                    stack.hasCustomHoverName()
                            || attachment.getSkinId(stack) != null
                            || attachment.hasCustomLaserColor(stack));
        }
        return null;
    }

    private static InspectedIdentity inspectGun(ItemStack stack, IGun gun) {
        ResourceLocation gunId = gun.getGunId(stack);
        // TaCZ can retain HasBulletInBarrel on an open-bolt gun even though
        // that flag is not a usable chambered round. Its HUD and firing logic
        // both ignore the flag for OPEN_BOLT weapons, so use the same rule here
        // instead of rejecting a gun which TaCZ correctly displays as 0 ammo.
        // If add-on data cannot be resolved, keep the conservative legacy
        // behavior and treat a reported chambered round as loaded.
        Bolt bolt = Optional.ofNullable(gunId)
                .flatMap(TimelessAPI::getCommonGunIndex)
                .map(index -> index.getGunData())
                .map(data -> data.getBolt())
                .orElse(null);
        boolean loaded = isLoadedGun(
                gun.getCurrentAmmoCount(stack),
                gun.hasBulletInBarrel(stack),
                bolt);
        boolean attachments = false;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                continue;
            }
            ResourceLocation attachmentId = gun.getAttachmentId(stack, type);
            if (attachmentId != null && !DefaultAssets.isEmptyAttachmentId(attachmentId)) {
                attachments = true;
                break;
            }
        }
        ResourceLocation displayId = gun.getGunDisplayId(stack);
        boolean customized = stack.hasCustomHoverName()
                || (displayId != null && !DefaultAssets.DEFAULT_GUN_DISPLAY_ID.equals(displayId))
                || gun.getExp(stack) > 0
                || gun.hasCustomLaserColor(stack)
                || gun.hasHeatData(stack);
        return new InspectedIdentity(
                gunId,
                BlueprintKind.GUN,
                loaded,
                attachments,
                customized);
    }

    static boolean isLoadedGun(
            int currentAmmoCount,
            boolean hasBulletInBarrel,
            Bolt bolt) {
        return currentAmmoCount > 0
                || (hasBulletInBarrel && bolt != Bolt.OPEN_BOLT);
    }

    private static Resolution resolveIdentity(
            ItemStack stack,
            ResourceLocation blueprintId,
            BlueprintKind kind,
            boolean loaded,
            boolean containsAttachments,
            boolean modified,
            Map<ResourceLocation, BlueprintData> catalog) {
        if (blueprintId == null
                || blueprintId.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                || DefaultAssets.EMPTY_GUN_ID.equals(blueprintId)
                || DefaultAssets.EMPTY_AMMO_ID.equals(blueprintId)
                || DefaultAssets.EMPTY_ATTACHMENT_ID.equals(blueprintId)) {
            return Resolution.failure(Status.MISSING_LOGICAL_ID);
        }
        BlueprintData data = catalog.get(blueprintId);
        if (data == null || data.getRecipeId() == null) {
            return new Resolution(
                    Status.NOT_RECIPE_BACKED,
                    Optional.of(blueprintId),
                    Optional.empty(),
                    kind,
                    stack.getCount(),
                    loaded,
                    containsAttachments,
                    modified);
        }
        if (data.getKind() != kind) {
            return new Resolution(
                    Status.CATALOG_KIND_MISMATCH,
                    Optional.of(blueprintId),
                    Optional.of(data),
                    kind,
                    stack.getCount(),
                    loaded,
                    containsAttachments,
                    modified);
        }
        return new Resolution(
                Status.RESOLVED,
                Optional.of(blueprintId),
                Optional.of(data),
                kind,
                stack.getCount(),
                loaded,
                containsAttachments,
                modified);
    }

    public enum Status {
        EMPTY_INPUT,
        UNSUPPORTED_ITEM,
        INVALID_ITEM_DATA,
        MISSING_LOGICAL_ID,
        NOT_RECIPE_BACKED,
        CATALOG_KIND_MISMATCH,
        RESOLVED
    }

    @FunctionalInterface
    interface IdentityAdapter {
        InspectedIdentity inspect(ItemStack stack);
    }

    record InspectedIdentity(
            ResourceLocation blueprintId,
            BlueprintKind kind,
            boolean loadedGun,
            boolean containsAttachments,
            boolean modified) {
    }

    public record Resolution(
            Status status,
            Optional<ResourceLocation> blueprintId,
            Optional<BlueprintData> data,
            BlueprintKind kind,
            int stackCount,
            boolean loadedGun,
            boolean containsAttachments,
            boolean modified) {
        public Resolution {
            if (status == null) {
                throw new IllegalArgumentException("physical-item resolution status cannot be null");
            }
            blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
            data = data == null ? Optional.empty() : data;
            if (stackCount < 0) {
                throw new IllegalArgumentException("physical-item stack count cannot be negative");
            }
            if (status == Status.RESOLVED
                    && (blueprintId.isEmpty() || data.isEmpty() || kind == null)) {
                throw new IllegalArgumentException("resolved physical item is missing canonical identity");
            }
        }

        public static Resolution failure(Status status) {
            return new Resolution(
                    status,
                    Optional.empty(),
                    Optional.empty(),
                    null,
                    0,
                    false,
                    false,
                    false);
        }

        public boolean resolved() {
            return status == Status.RESOLVED;
        }
    }
}
