package com.gamergaming.taczweaponblueprints.compat.emi;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Stable, provenance-independent identities for EMI blueprint stack matching. */
final class EmiBlueprintStackIdentity {
    static final String BLANK_TARGET_TAG = "taczweaponblueprints:emi_blueprint_target";

    private EmiBlueprintStackIdentity() {
    }

    static String blueprintId(CompoundTag tag) {
        return canonicalId(tag, "bpId");
    }

    static String blankTarget(CompoundTag tag) {
        return canonicalId(tag, BLANK_TARGET_TAG);
    }

    static void targetBlankBlueprint(CompoundTag tag, ResourceLocation blueprintId) {
        if (tag == null || blueprintId == null) {
            throw new IllegalArgumentException("EMI blank-blueprint target cannot be null");
        }
        tag.putString(BLANK_TARGET_TAG, blueprintId.toString());
    }

    private static String canonicalId(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key, Tag.TAG_STRING)) {
            return "";
        }
        String value = tag.getString(key);
        if (value.isBlank()
                || value.length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            return "";
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id == null ? "" : id.toString();
    }
}
