package com.gamergaming.taczweaponblueprints.settings;

import java.util.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.settings.SettingsCatalog.QuickControl;

/** Small, explicit edits of canonical fields. No preset is applied merely by viewing this model. */
public final class QuickSetupSettings {
    private QuickSetupSettings() {}

    public static List<String> choices(QuickControl control) {
        return switch (control) {
            case PACE -> SettingsPace.choices().stream().map(SettingsValues::token).toList();
            case AMMO_RESEARCH, ATTACHMENT_RESEARCH -> tokens(ResearchDomainMode.values());
            case RESEARCH_ACCESS -> tokens(StationSetup.ResearchAccess.values());
            case DISCOVERY -> tokens(BlueprintBalancePreset.values());
            case COSTS -> tokens(ResearchCostMode.values());
            default -> List.of(SettingsValues.bool(true), SettingsValues.bool(false));
        };
    }

    private static List<String> tokens(Enum<?>[] values) {
        return Arrays.stream(values).map(SettingsValues::token).toList();
    }

    public static String selected(QuickControl control, SettingsPresentation.Summary summary) {
        if (control == QuickControl.PACE) return SettingsValues.token(pace(summary));
        return control == QuickControl.CRAFTING_TIERS ? SettingsValues.bool(summary.craftingTiers())
                : summary.controls().get(control.presentationField()).stored();
    }

    /** The pace the stored values add up to. Custom means they match none of the three. */
    public static SettingsPace.Pace pace(SettingsPresentation.Summary summary) {
        Map<String, String> values = new HashMap<>();
        for (String field : SettingsPace.FIELDS) values.put(field, summary.controls().get(field).stored());
        return SettingsPace.matching(values);
    }

    public static String labelKey(QuickControl control) {
        return "settings.taczweaponblueprints.quick." + control.name().toLowerCase(Locale.ROOT);
    }

    public static String choiceKey(QuickControl control, String value) {
        String token = SettingsValues.scalar(value);
        return "settings.taczweaponblueprints.choice." + switch (control) {
            case PACE -> "pace." + token;
            case AMMO_RESEARCH, ATTACHMENT_RESEARCH -> "domain." + token;
            case RESEARCH_ACCESS -> "access." + token;
            case DISCOVERY -> "discovery." + token;
            case COSTS -> "costs." + token;
            default -> token;
        };
    }

    public static void select(SettingsEditSession draft, QuickControl control, String value) {
        String canonical = SettingsValues.canonical(value);
        if (!choices(control).contains(canonical)) throw new IllegalArgumentException("Unknown Quick Setup choice");
        var presentation = SettingsPresentation.describe(draft.baseline(), draft.preview());
        if (presentation.controls().get(control.presentationField()).availability() != SettingsPresentation.Availability.EDITABLE)
            throw new IllegalStateException("Quick Setup control is not editable");
        if (control == QuickControl.PACE) SettingsPace.apply(draft, SettingsPace.Pace.valueOf(SettingsValues.scalar(canonical)));
        else if (control == QuickControl.CRAFTING_TIERS) draft.setCraftingTiers(SettingsValues.booleanValue(canonical));
        else draft.set(control.presentationField(), canonical);
    }
}
