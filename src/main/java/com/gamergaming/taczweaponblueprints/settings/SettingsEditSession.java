package com.gamergaming.taczweaponblueprints.settings;

import java.util.*;
import com.gamergaming.taczweaponblueprints.progression.ResearchProgressionPreset;

/** One draft shared by future Quick Setup and Advanced views; never writes to a Config. */
public final class SettingsEditSession {
    private final SettingsSnapshot baseline;
    private final Map<String, String> changes = new LinkedHashMap<>();
    private boolean closed;
    private boolean frozen;
    public SettingsEditSession(SettingsSnapshot baseline) { this.baseline = Objects.requireNonNull(baseline); }
    public SettingsSnapshot baseline() { return baseline; }
    public void set(String field, String document) {
        if (closed || frozen || !baseline.editable()) throw new IllegalStateException("Settings session is not editable");
        if (!SettingsCatalog.entry(field).supported()) throw new IllegalArgumentException("Unavailable integration");
        String canonical = SettingsValues.canonical(document);
        if (canonical.equals(baseline.values().get(field))) changes.remove(field); else changes.put(field, canonical);
    }
    public void setBoolean(String field, boolean value) { set(field, SettingsValues.bool(value)); }
    public void setChoice(String field, Enum<?> value) { set(field, SettingsValues.token(value)); }
    /** Validate the entire proposal before queuing any of its fields. Server validation still occurs on Apply. */
    public void setAll(Map<String, String> values) {
        if (closed || frozen || !baseline.editable()) throw new IllegalStateException("Settings session is not editable");
        Map<String, String> canonical = new LinkedHashMap<>();
        values.forEach((field, value) -> {
            if (!SettingsCatalog.entry(field).supported()) throw new IllegalArgumentException("Unavailable integration");
            canonical.put(field, SettingsValues.canonical(value));
        });
        canonical.forEach(this::set);
    }
    public Map<String, String> preview() {
        Map<String, String> preview = new LinkedHashMap<>(baseline.values()); preview.putAll(changes);
        return Collections.unmodifiableMap(preview);
    }
    public void setCraftingTiers(boolean enabled) {
        Map<String, String> values = preview();
        var preset = ResearchProgressionPreset.valueOf(SettingsValues.scalar(values.get("progressionPreset")));
        if (preset.enforcesCrafting(SettingsValues.booleanValue(values.get("customEnforceCraftingTiers"))) == enabled) return;
        boolean research = preset.enforcesResearch(SettingsValues.booleanValue(values.get("customEnforceResearchTiers")));
        setChoice("progressionPreset", ResearchProgressionPreset.CUSTOM);
        setBoolean("customEnforceResearchTiers", research);
        setBoolean("customEnforceCraftingTiers", enabled);
    }
    public SettingsChangePlan plan() {
        if (closed) throw new IllegalStateException("Settings session is closed");
        return new SettingsChangePlan(baseline.session(), baseline.revision(), changes);
    }
    public boolean dirty() { return !changes.isEmpty(); }
    public void freeze() { if (closed) throw new IllegalStateException("Settings session is closed"); frozen = true; }
    /** Returned proposals can be shown alongside a refreshed snapshot; never silently rebase them. */
    public Map<String, String> proposedChanges() { return Map.copyOf(changes); }
    /** Explicit review handoff, never called automatically on a stale reply. */
    public SettingsEditSession reviewedAgainst(SettingsSnapshot refreshed) {
        if (closed) throw new IllegalStateException("Settings session is closed");
        SettingsEditSession reviewed = new SettingsEditSession(refreshed);
        changes.forEach(reviewed::set);
        return reviewed;
    }
    public void discard() { changes.clear(); closed = true; }
}
