package com.gamergaming.taczweaponblueprints.settings;

import java.util.*;
import com.gamergaming.taczweaponblueprints.progression.BlueprintBalancePreset;

/**
 * One choice that sets how fast a world progresses: blueprint loot, Scrap supply and research
 * prices together. Like the setup presets, a pace is a set of ordinary edits and is never stored
 * itself. The current pace is whichever one the stored values match, or {@link Pace#CUSTOM}.
 */
public final class SettingsPace {
    public enum Pace { RELAXED, NORMAL, HARD, CUSTOM }

    /** The settings a pace owns, in the order they are shown. */
    public static final List<String> FIELDS = List.of("balancePreset", "scrapLootPercent", "researchCostPercent",
            "tierTwoResearchCostPercent", "tierThreeResearchCostPercent");

    private static final Map<Pace, Map<String, String>> TARGETS = Map.of(
            Pace.RELAXED, targets(BlueprintBalancePreset.ACCESSIBLE, 150, 75, 150, 200),
            Pace.NORMAL, targets(BlueprintBalancePreset.BALANCED, 100, 100, 200, 300),
            Pace.HARD, targets(BlueprintBalancePreset.SCARCE, 75, 150, 250, 400));

    private SettingsPace() {}

    private static Map<String, String> targets(BlueprintBalancePreset loot, int scrap, int price, int tierTwo, int tierThree) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("balancePreset", SettingsValues.token(loot));
        values.put("scrapLootPercent", number(scrap));
        values.put("researchCostPercent", number(price));
        values.put("tierTwoResearchCostPercent", number(tierTwo));
        values.put("tierThreeResearchCostPercent", number(tierThree));
        return Collections.unmodifiableMap(values);
    }

    private static String number(int value) { return SettingsValues.canonical("value = " + value); }

    /** The selectable paces. Custom is a description of the current values, not a choice. */
    public static List<Pace> choices() { return List.of(Pace.RELAXED, Pace.NORMAL, Pace.HARD); }

    public static Map<String, String> targets(Pace pace) {
        Map<String, String> values = TARGETS.get(Objects.requireNonNull(pace));
        if (values == null) throw new IllegalArgumentException("Custom is not a selectable pace");
        return values;
    }

    public static Pace matching(Map<String, String> values) {
        for (Pace pace : choices()) {
            if (targets(pace).entrySet().stream().allMatch(entry -> entry.getValue().equals(values.get(entry.getKey())))) return pace;
        }
        return Pace.CUSTOM;
    }

    public static void apply(SettingsEditSession draft, Pace pace) {
        targets(pace).forEach(draft::set);
    }
}
