package com.gamergaming.taczweaponblueprints.settings;

import java.util.*;

/** Only intended changed fields cross the apply boundary, never a replacement config. */
public record SettingsChangePlan(UUID session, String revision, Map<String, String> changes) {
    public SettingsChangePlan {
        Objects.requireNonNull(session); Objects.requireNonNull(revision);
        changes = Collections.unmodifiableMap(new LinkedHashMap<>(changes));
    }
}
