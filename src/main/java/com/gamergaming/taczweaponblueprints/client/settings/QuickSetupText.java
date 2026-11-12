package com.gamergaming.taczweaponblueprints.client.settings;

import java.util.*;
import com.gamergaming.taczweaponblueprints.settings.*;
import com.gamergaming.taczweaponblueprints.settings.SettingsCatalog.QuickControl;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Translates an already resolved summary; no config mutation or gameplay resolution in rendering. */
final class QuickSetupText {
    private QuickSetupText() {}
    static MutableComponent text(String suffix, Object... args) {
        return Component.translatable("settings.taczweaponblueprints.quick." + suffix, args);
    }
    static Component onOff(boolean value) {
        return Component.translatable("settings.taczweaponblueprints.choice." + value);
    }
    static Component effective(Optional<Boolean> value) { return value.map(QuickSetupText::onOff).orElse(text("unknown")); }
    static Component category(SettingsPresentation.Control control) {
        return effective(control.effective().map(SettingsValues::booleanValue));
    }
    static Component currency(SettingsPresentation.Summary summary) {
        if (summary.currencyKind().equals("BANKED_RP")) return text("rp");
        var id = ResourceLocation.tryParse(summary.currencyItem());
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.get(id).getDescription();
        return text("missing_currency", summary.currencyItem());
    }
    static Component choice(QuickControl control, String value, SettingsPresentation.Summary summary) {
        return Component.translatable(QuickSetupSettings.choiceKey(control, value), currency(summary));
    }
    static Component hint(QuickControl control, SettingsPresentation.Summary summary) {
        var state = summary.controls().get(control.presentationField());
        Component details = switch (control) {
            case AMMO_RESEARCH, ATTACHMENT_RESEARCH -> text("domain_hint", category(state));
            case RESEARCH_ACCESS -> text("access_hint", onOff(summary.researchTiers()));
            case CRAFTING_TIERS -> text(summary.specialCraftingRules() ? "crafting_custom_hint" : "crafting_hint");
            case DISCOVERY -> text("discovery_hint", String.format(Locale.ROOT, "%.1f", summary.discovery().lootChance() * 100),
                    summary.discovery().minimumLootRolls(), summary.discovery().maximumLootRolls(),
                    Component.translatable("config.taczweaponblueprints.blueprint.maximumUndiscoveredVisibility." + summary.discovery().maximumUndiscoveredVisibility().name()));
            case COSTS -> text("costs_hint", currency(summary));
            default -> Component.translatable(QuickSetupSettings.labelKey(control) + ".hint");
        };
        if (control == QuickControl.RESEARCH_ACCESS && !SettingsValues.booleanValue(summary.controls().get("enableResearch").effective().orElseThrow()))
            details = text("access_dormant").append(" ").append(details);
        if (control == QuickControl.DISCOVERY && !SettingsValues.booleanValue(summary.controls().get("enableBlueprints").stored()))
            details = text("progression_off").append(" ").append(details);
        return state.reasonKey().isEmpty() ? details
                : Component.translatable(state.reasonKey()).append(" ").append(details);
    }
    static Component compact(SettingsPresentation.Summary summary) {
        if (!SettingsValues.booleanValue(summary.controls().get("enableBlueprints").stored())) return text("progression_off");
        return text("compact", category(summary.controls().get("enableResearch")), onOff(summary.craftingTiers()));
    }
}
