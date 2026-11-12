package com.gamergaming.taczweaponblueprints.settings;

import java.util.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile.DomainPolicy;

/** Screen-independent values and reasons. Renderers translate keys/tokens, never resolve gameplay rules. */
public final class SettingsPresentation {
    public enum Availability { EDITABLE, READ_ONLY, INACTIVE, UNAVAILABLE }
    public enum Source { CONFIG, PRESET, PROFILE, CUSTOM_RULE }
    public record Profile(String id, boolean available, DomainPolicy weapons, DomainPolicy ammo, DomainPolicy attachments) {
        public Profile { Objects.requireNonNull(id); Objects.requireNonNull(weapons); Objects.requireNonNull(ammo); Objects.requireNonNull(attachments); }
        public static Profile unavailable(String id) {
            var absent = new DomainPolicy(false, false);
            return new Profile(id, false, absent, absent, absent);
        }
    }
    public record Control(String field, String stored, Optional<String> effective, Source source,
                          Availability availability, String reasonKey, SettingsCatalog.Category destination) {}
    public record Summary(Map<String, Control> controls, BlueprintBalanceSettings discovery,
                          boolean researchTiers, boolean craftingTiers, boolean specialCraftingRules,
                          String currencyKind, String currencyItem, String costMode, Optional<Boolean> weaponsResearch) {
        public Summary { controls = Collections.unmodifiableMap(new LinkedHashMap<>(controls)); }
    }
    private SettingsPresentation() {}
    public static Summary describe(SettingsSnapshot snapshot, Map<String, String> values) {
        var config = FzzySettingsAdapter.fromValues(values);
        var preset = config.progressionPreset.getUnconditional();
        boolean research = config.enableBlueprints.get() && config.enableResearch.getUnconditional();
        Map<String, Control> controls = new LinkedHashMap<>();
        for (var entry : SettingsCatalog.entries().values()) {
            boolean active = entry.condition().active(config);
            Availability availability = !entry.supported() ? Availability.UNAVAILABLE
                    : !snapshot.editable() ? Availability.READ_ONLY : !active ? Availability.INACTIVE : Availability.EDITABLE;
            String reason = !entry.supported() ? "unsupported" : snapshot.editable() ? "" : "permission";
            controls.put(entry.field(), new Control(entry.field(), values.get(entry.field()),
                    active && entry.supported() ? Optional.of(values.get(entry.field())) : Optional.empty(), Source.CONFIG, availability,
                    reason.isEmpty() && !active ? entry.condition().reasonKey() : reasonKey(reason), entry.category()));
        }
        replace(controls, "enableResearch", SettingsValues.bool(research), Source.CONFIG, null, "");
        boolean researchTiers = config.stationSetup().enforceResearchTiers(preset.enforcesResearch(config.customEnforceResearchTiers.getUnconditional()));
        boolean craftingTiers = preset.enforcesCrafting(config.customEnforceCraftingTiers.getUnconditional());
        replace(controls, "progressionPreset", SettingsValues.bool(craftingTiers), preset == ResearchProgressionPreset.CUSTOM ? Source.CUSTOM_RULE : Source.PRESET, null, "");
        replace(controls, "customEnforceCraftingTiers", SettingsValues.bool(craftingTiers), preset == ResearchProgressionPreset.CUSTOM ? Source.CONFIG : Source.PRESET, null, "");
        replace(controls, "researchAccess", SettingsValues.bool(researchTiers), config.researchAccess.get() == StationSetup.ResearchAccess.PRESET ? Source.PRESET : Source.CONFIG, null, "");
        Profile profile = snapshot.profile();
        boolean profileReady = profile.available() && profile.id().equals(config.activeResearchProfile.getUnconditional());
        domain(controls, "ammoResearch", config.ammoResearch.get(), profile.ammo(), research, profileReady);
        domain(controls, "attachmentResearch", config.attachmentResearch.get(), profile.attachments(), research, profileReady);
        if (!research) replace(controls, "researchCostMode", values.get("researchCostMode"), Source.CONFIG, Availability.INACTIVE, "research_disabled");
        var discovery = BlueprintBalanceSettings.resolve(config.balancePreset.get(), config.maximumUndiscoveredVisibility.getUnconditional(),
                config.blueprintSpawnChance.getUnconditional(), config.minBlueprints.getUnconditional(), config.maxBlueprints.getUnconditional());
        boolean special = !config.exactCraftingOverrides.get().isEmpty() || !config.craftingDisabledKinds.get().isEmpty()
                || !config.craftingDisabledItemTypes.get().isEmpty() || !config.craftingUnrestrictedKinds.get().isEmpty()
                || !config.craftingUnrestrictedItemTypes.get().isEmpty() || !config.progressionExemptBlueprints.get().isEmpty()
                || !config.progressionExemptKinds.get().isEmpty() || !config.progressionExemptItemTypes.get().isEmpty()
                || config.ammoCraftingStrategy.get() != AmmoCraftingStrategy.PROFILE
                || config.attachmentCraftingStrategy.get() != AttachmentCraftingStrategy.PROFILE;
        return new Summary(controls, discovery, researchTiers, craftingTiers, special,
                config.researchCurrencyKind.get().name(), config.researchCurrencyItem.getUnconditional(), config.researchCostMode.getUnconditional().name(),
                profileReady ? Optional.of(research && profile.weapons().researchEnabled()) : Optional.empty());
    }
    private static void domain(Map<String, Control> controls, String field, ResearchDomainMode mode,
                               DomainPolicy profile, boolean research, boolean ready) {
        // Even an explicit override cannot establish a usable category when the server profile is unavailable.
        String effective = ready ? SettingsValues.bool(research && mode.apply(profile).researchEnabled()) : null;
        replace(controls, field, effective, mode == ResearchDomainMode.PROFILE ? Source.PROFILE : Source.CONFIG,
                !ready ? Availability.UNAVAILABLE : !research ? Availability.INACTIVE : null,
                !ready ? "profile_unavailable" : !research ? "research_disabled" : "");
    }
    private static void replace(Map<String, Control> controls, String field, String effective, Source source, Availability availability, String reason) {
        var old = controls.get(field);
        controls.put(field, new Control(field, old.stored(), Optional.ofNullable(effective), source,
                availability == null ? old.availability() : availability, reason.isEmpty() ? old.reasonKey() : reasonKey(reason), old.destination()));
    }
    private static String reasonKey(String suffix) { return suffix.isEmpty() ? "" : "settings.taczweaponblueprints.reason." + suffix; }
}
