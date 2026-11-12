package com.gamergaming.taczweaponblueprints.settings;

import java.util.*;
import com.gamergaming.taczweaponblueprints.progression.*;

/** Explicit edit recipes, never a saved preset layer. Targets are intentionally exhaustive. */
public final class SettingsRecipes {
    public enum Recipe {
        RECOMMENDED, SIMPLER_STATIONS;
        public String key() { return "settings.taczweaponblueprints.recipe." + name().toLowerCase(Locale.ROOT); }
    }
    public record Change(String field, String before, String after, boolean replacesPending) {}
    public record Preview(Recipe recipe, UUID session, String revision, Map<String, String> before, List<Change> changes) {
        public Preview { before = Map.copyOf(before); changes = List.copyOf(changes); }
    }
    private static final Map<Recipe, Map<String, String>> TARGETS = Map.of(
            Recipe.RECOMMENDED, buildTargets(Recipe.RECOMMENDED),
            Recipe.SIMPLER_STATIONS, buildTargets(Recipe.SIMPLER_STATIONS));
    private SettingsRecipes() {}
    public static Map<String, String> targets(Recipe recipe) {
        return TARGETS.get(Objects.requireNonNull(recipe));
    }
    private static Map<String, String> buildTargets(Recipe recipe) {
        Map<String, String> values = new LinkedHashMap<>();
        if (recipe == Recipe.SIMPLER_STATIONS) {
            values.put("researchAccess", SettingsValues.token(StationSetup.ResearchAccess.SINGLE));
            values.put("analyzerSetup", SettingsValues.token(StationSetup.AnalyzerMode.SINGLE));
        } else {
            values.put("enableBlueprints", SettingsValues.bool(true));
            values.put("enableResearch", SettingsValues.bool(true));
            values.put("researchAccess", SettingsValues.token(StationSetup.ResearchAccess.TIERED));
            values.put("analyzerSetup", SettingsValues.token(StationSetup.AnalyzerMode.TIERED));
            values.put("progressionPreset", SettingsValues.token(ResearchProgressionPreset.TIERED_RESEARCH_AND_CRAFTING));
            values.put("ammoResearch", SettingsValues.token(ResearchDomainMode.PROFILE));
            values.put("attachmentResearch", SettingsValues.token(ResearchDomainMode.PROFILE));
            values.put("balancePreset", SettingsValues.token(BlueprintBalancePreset.BALANCED));
            values.put("researchCostMode", SettingsValues.token(ResearchCostMode.POINTS_AND_ITEMS));
            values.put("enableGunsmith", SettingsValues.bool(true));
            values.put("enableBallisticsResearcher", SettingsValues.bool(true));
        }
        return Collections.unmodifiableMap(values);
    }
    public static Optional<Recipe> matching(Map<String, String> values) {
        return Arrays.stream(Recipe.values()).filter(recipe -> targets(recipe).entrySet().stream()
                .allMatch(entry -> entry.getValue().equals(values.get(entry.getKey())))).findFirst();
    }
    public static Preview preview(SettingsEditSession draft, Recipe recipe) {
        var before = draft.preview(); var pending = draft.proposedChanges();
        List<Change> changes = new ArrayList<>();
        targets(recipe).forEach((field, value) -> {
            if (!value.equals(before.get(field))) changes.add(new Change(field, before.get(field), value, pending.containsKey(field)));
        });
        return new Preview(recipe, draft.baseline().session(), draft.baseline().revision(), before, changes);
    }
    public static void queue(SettingsEditSession draft, Preview reviewed) {
        if (!preview(draft, reviewed.recipe()).equals(reviewed)) throw new IllegalStateException("Recipe preview is no longer current");
        Map<String, String> changes = new LinkedHashMap<>();
        reviewed.changes().forEach(change -> changes.put(change.field(), change.after()));
        draft.setAll(changes);
    }
}
