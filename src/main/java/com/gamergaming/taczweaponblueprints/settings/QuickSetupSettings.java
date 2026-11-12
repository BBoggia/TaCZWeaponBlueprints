package com.gamergaming.taczweaponblueprints.settings;

import java.util.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.settings.SettingsCatalog.QuickControl;

/** Small, explicit edits of canonical fields. No preset is applied merely by viewing this model. */
public final class QuickSetupSettings {
    private QuickSetupSettings() {}

    public static List<String> choices(QuickControl control) {
        return switch (control) {
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
        return control == QuickControl.CRAFTING_TIERS ? SettingsValues.bool(summary.craftingTiers())
                : summary.controls().get(control.presentationField()).stored();
    }

    public static String labelKey(QuickControl control) {
        return "settings.taczweaponblueprints.quick." + control.name().toLowerCase(Locale.ROOT);
    }

    public static String choiceKey(QuickControl control, String value) {
        String token = SettingsValues.scalar(value);
        return "settings.taczweaponblueprints.choice." + switch (control) {
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
        if (control == QuickControl.CRAFTING_TIERS) draft.setCraftingTiers(SettingsValues.booleanValue(canonical));
        else draft.set(control.presentationField(), canonical);
    }
}
