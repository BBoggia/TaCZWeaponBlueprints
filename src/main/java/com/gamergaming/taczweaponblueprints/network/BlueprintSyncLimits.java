package com.gamergaming.taczweaponblueprints.network;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;

import net.minecraft.resources.ResourceLocation;

/** Shared publication and wire-format invariants for blueprint synchronization. */
public final class BlueprintSyncLimits {
    /** Leaves headroom below Minecraft 1.20.1's one-megabyte custom-payload cap. */
    public static final int MAX_CHUNK_BYTES = 900_000;
    public static final int MAX_TRANSLATION_KEY_LENGTH = 256;
    public static final int MAX_ITEM_TYPE_LENGTH = 64;
    public static final int MAX_RESOURCE_ID_LENGTH = 256;
    static final int CHUNK_HEADER_RESERVE = 32;

    private BlueprintSyncLimits() {
    }

    public static void validateCatalog(Map<ResourceLocation, BlueprintData> catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("Blueprint catalog cannot be null");
        }
        if (catalog.size() > BlueprintDataManager.MAX_CATALOG_ENTRIES) {
            throw new IllegalArgumentException("Too many blueprints to synchronize: " + catalog.size());
        }
        catalog.forEach(BlueprintSyncLimits::validateEntry);
    }

    public static void validateEntry(ResourceLocation blueprintId, BlueprintData data) {
        if (blueprintId == null || data == null) {
            throw new IllegalArgumentException("Blueprint catalog entries cannot contain null keys or values");
        }
        validateResourceId("blueprint ID", blueprintId);
        validateText("name translation key", data.getNameKey(), MAX_TRANSLATION_KEY_LENGTH);
        validateText("tooltip translation key", data.getTooltipKey(), MAX_TRANSLATION_KEY_LENGTH);
        validateText("item type", data.getItemType(), MAX_ITEM_TYPE_LENGTH);
        validateResourceId("recipe ID", data.getRecipeId());
        validateResourceId("display slot ID", data.getDisplaySlotKey());
        if (encodedBlueprintEntryBytes(blueprintId, data) + CHUNK_HEADER_RESERVE > MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Blueprint " + blueprintId + " cannot fit in one synchronization chunk");
        }
    }

    static int encodedBlueprintEntryBytes(ResourceLocation blueprintId, BlueprintData data) {
        return encodedUtfBytes(blueprintId.toString())
                + encodedUtfBytes(data.getNameKey())
                + encodedUtfBytes(data.getTooltipKey())
                + encodedUtfBytes(data.getRecipeId().toString())
                + encodedUtfBytes(data.getItemType())
                + encodedUtfBytes(data.getDisplaySlotKey().toString());
    }

    static int encodedUtfBytes(String value) {
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        return varIntBytes(bytes) + bytes;
    }

    static int varIntBytes(int value) {
        int bytes = 1;
        while ((value & -128) != 0) {
            value >>>= 7;
            bytes++;
        }
        return bytes;
    }

    private static void validateResourceId(String field, ResourceLocation value) {
        if (value == null || value.toString().length() > MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Blueprint " + field + " must contain at most " + MAX_RESOURCE_ID_LENGTH + " characters");
        }
    }

    private static void validateText(String field, String value, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(
                    "Blueprint " + field + " must be non-blank and contain at most "
                            + maximumLength + " characters");
        }
    }
}
