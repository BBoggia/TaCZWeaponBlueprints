package com.gamergaming.taczweaponblueprints.progression;

import java.util.Locale;
import java.util.regex.Pattern;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;

import net.minecraft.resources.ResourceLocation;

/** Shared canonicalization and bounds for progression-domain identifiers. */
public final class ProgressionIds {
    public static final int MAX_MESSAGE_KEY_LENGTH = 256;

    private static final Pattern MESSAGE_KEY = Pattern.compile("[a-z0-9_.-]+");

    private ProgressionIds() {
    }

    public static ResourceLocation parse(String value, String fieldName) {
        if (value == null) {
            throw invalid(fieldName, "cannot be null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw invalid(fieldName, "cannot be blank");
        }
        if (normalized.length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw invalid(fieldName, "is not a bounded resource ID");
        }
        ResourceLocation parsed = ResourceLocation.tryParse(normalized);
        if (parsed == null) {
            throw invalid(fieldName, "is not a valid resource ID");
        }
        return require(parsed, fieldName);
    }

    public static ResourceLocation require(ResourceLocation value, String fieldName) {
        if (value == null
                || value.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw invalid(fieldName, "is not a bounded resource ID");
        }
        return value;
    }

    public static String messageKey(String value, String fieldName) {
        if (value == null) {
            throw invalid(fieldName, "cannot be null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()
                || normalized.length() > MAX_MESSAGE_KEY_LENGTH
                || !MESSAGE_KEY.matcher(normalized).matches()) {
            throw invalid(fieldName, "is not a valid translation key");
        }
        return normalized;
    }

    private static IllegalArgumentException invalid(String fieldName, String reason) {
        String label = fieldName == null || fieldName.isBlank() ? "progression value" : fieldName;
        return new IllegalArgumentException(label + " " + reason);
    }
}
