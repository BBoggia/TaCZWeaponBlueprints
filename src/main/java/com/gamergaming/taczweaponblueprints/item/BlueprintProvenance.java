package com.gamergaming.taczweaponblueprints.item;

import java.util.Locale;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Additive item provenance; legacy blueprints intentionally have no value. */
public record BlueprintProvenance(
        int format,
        Source source,
        boolean recyclable,
        PhysicalBlueprintLearningMode learningMode) {
    public static final int CURRENT_FORMAT = 1;
    public static final String TAG_KEY = "taczweaponblueprints:provenance";

    public BlueprintProvenance {
        if (format != CURRENT_FORMAT || source == null || learningMode == null) {
            throw new IllegalArgumentException("blueprint provenance is invalid");
        }
    }

    public static BlueprintProvenance reverseEngineered(
            boolean recyclable,
            PhysicalBlueprintLearningMode learningMode) {
        return new BlueprintProvenance(
                CURRENT_FORMAT,
                Source.REVERSE_ENGINEERING,
                recyclable,
                learningMode);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("format", format);
        tag.putString("source", source.name().toLowerCase(Locale.ROOT));
        tag.putBoolean("recyclable", recyclable);
        tag.putString("learning_mode", learningMode.name().toLowerCase(Locale.ROOT));
        return tag;
    }

    public static Optional<BlueprintProvenance> fromTag(CompoundTag root) {
        if (root == null || !root.contains(TAG_KEY, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag tag = root.getCompound(TAG_KEY);
        if (!tag.contains("format", Tag.TAG_INT)
                || !tag.contains("source", Tag.TAG_STRING)
                || !tag.contains("recyclable", Tag.TAG_BYTE)
                || !tag.contains("learning_mode", Tag.TAG_STRING)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BlueprintProvenance(
                    tag.getInt("format"),
                    Source.valueOf(tag.getString("source").toUpperCase(Locale.ROOT)),
                    tag.getBoolean("recyclable"),
                    PhysicalBlueprintLearningMode.valueOf(
                            tag.getString("learning_mode").toUpperCase(Locale.ROOT))));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /** Legacy roots are allowed; a present malformed provenance fails closed. */
    public static boolean allowsRecycling(CompoundTag root) {
        if (root == null || !root.contains(TAG_KEY)) {
            return true;
        }
        return fromTag(root).map(BlueprintProvenance::recyclable).orElse(false);
    }

    public enum Source {
        REVERSE_ENGINEERING
    }
}
