package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public enum JournalVisibility {
    HIDDEN,
    SILHOUETTE,
    NAME,
    PREVIEW,
    FULL;

    public static final Codec<JournalVisibility> CODEC = Codec.STRING.flatXmap(
            JournalVisibility::parse,
            value -> DataResult.success(value.serializedName()));

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public JournalVisibility atLeast(JournalVisibility minimum) {
        return ordinal() >= minimum.ordinal() ? this : minimum;
    }

    public JournalVisibility atMost(JournalVisibility maximum) {
        return ordinal() <= maximum.ordinal() ? this : maximum;
    }

    public boolean appearsInJournal() {
        return this != HIDDEN;
    }

    public boolean appearsInTree() {
        return this != HIDDEN;
    }

    public boolean revealsName() {
        return ordinal() >= NAME.ordinal();
    }

    public boolean revealsIdentity() {
        return ordinal() >= PREVIEW.ordinal();
    }

    public boolean revealsIcon() {
        return revealsIdentity();
    }

    public boolean revealsResearchSummary() {
        return ordinal() >= PREVIEW.ordinal();
    }

    public boolean revealsExactPolicy() {
        return this == FULL;
    }

    public boolean allowsServerSelection() {
        return revealsIdentity();
    }

    private static DataResult<JournalVisibility> parse(String value) {
        if (value != null) {
            try {
                return DataResult.success(valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Report the bounded set of supported values below.
            }
        }
        return DataResult.error(() -> "unknown Journal visibility " + value);
    }
}
