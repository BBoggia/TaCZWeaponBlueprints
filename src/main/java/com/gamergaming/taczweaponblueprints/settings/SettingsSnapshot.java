package com.gamergaming.taczweaponblueprints.settings;

import java.util.*;

/** An authoritative read, bound to an edit session and its config/catalog revision. */
public record SettingsSnapshot(UUID session, String revision, boolean editable, Map<String, String> values,
                               SettingsPresentation.Profile profile) {
    public SettingsSnapshot {
        Objects.requireNonNull(session); Objects.requireNonNull(revision); Objects.requireNonNull(profile);
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        if (!values.keySet().equals(SettingsCatalog.entries().keySet())) throw new IllegalArgumentException("Incomplete settings snapshot");
    }
}
