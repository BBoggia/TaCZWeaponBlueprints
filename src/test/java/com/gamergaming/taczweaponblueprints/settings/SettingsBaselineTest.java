package com.gamergaming.taczweaponblueprints.settings;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import net.peanuuutz.tomlkt.Toml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SettingsBaselineTest {
    static com.google.gson.JsonObject effective(BlueprintConfig c) {
        var value = new com.google.gson.JsonObject(); var gson = new com.google.gson.Gson();
        value.add("discovery", gson.toJsonTree(c.balanceSettings()));
        value.add("stations", gson.toJsonTree(c.stationSetup()));
        value.addProperty("researchTiers", c.researchFeatureSnapshot().enforceResearchTiers());
        value.addProperty("craftingTiers", c.researchFeatureSnapshot().enforceCraftingTiers());
        value.addProperty("pointCap", c.progressionSnapshot().pointCap());
        value.add("domains", gson.toJsonTree(c.progressionSnapshot().domainSettings()));
        value.addProperty("currencyKind", c.researchCurrencyKind.get().name());
        value.addProperty("currencyItem", c.researchCurrencyItem.getUnconditional());
        value.addProperty("costMode", c.researchCostMode.getUnconditional().name());
        return value;
    }
    static String fixture(String name) throws Exception {
        try (var input = Objects.requireNonNull(SettingsBaselineTest.class.getResourceAsStream("/settings/" + name + ".toml"))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    static BlueprintConfig load(String name) throws Exception {
        String text = fixture(name);
        var config = ConfigApi.deserializeConfig(new BlueprintConfig(), text).get();
        int version = Integer.parseInt(((net.peanuuutz.tomlkt.TomlLiteral) Toml.Default.parseToTomlTable(text).get("version")).getContent());
        if (version < 5) config.update(version); else config.initializeLoadedSnapshots();
        return config;
    }
    @ParameterizedTest @ValueSource(strings = {"defaults", "custom-current", "banked-rp", "custom-currency", "legacy-zero", "legacy-two", "legacy-three"})
    void readCancelAndAnUnrelatedTogglePreserveTheLoadedBaseline(String name) throws Exception {
        var config = load(name); String serialized = FzzySettingsAdapter.serialize(config);
        var stored = FzzySettingsAdapter.capture(config);
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/settings/baseline.json"))) {
            var baseline = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject(name);
            assertEquals(baseline.get("stored"), new com.google.gson.Gson().toJsonTree(stored));
            assertEquals(baseline.get("effective"), effective(config));
        }
        var snapshot = new SettingsSnapshot(UUID.randomUUID(), "baseline", true, stored, SettingsPresentation.Profile.unavailable(config.activeResearchProfile.getUnconditional()));
        var edit = new SettingsEditSession(snapshot); SettingsPresentation.describe(snapshot, edit.preview());
        edit.setBoolean("enableJournal", !config.enableJournal.getUnconditional()); edit.discard();
        assertEquals(serialized, FzzySettingsAdapter.serialize(config));
        assertEquals(stored, FzzySettingsAdapter.capture(FzzySettingsAdapter.copy(config)));
        var prepared = FzzySettingsAdapter.prepare(config, Map.of("enableJournal", SettingsValues.bool(!config.enableJournal.getUnconditional())));
        var expected = new LinkedHashMap<>(stored); expected.put("enableJournal", SettingsValues.bool(!config.enableJournal.getUnconditional()));
        var restored = ConfigApi.deserializeConfig(new BlueprintConfig(), FzzySettingsAdapter.serialize(prepared)).get(); restored.initializeLoadedSnapshots();
        assertEquals(expected, FzzySettingsAdapter.capture(restored));
        assertEquals(prepared.researchFeatureSnapshot(), restored.researchFeatureSnapshot());
        assertEquals(prepared.progressionSnapshot(), restored.progressionSnapshot());
        assertEquals(prepared.balanceSettings(), restored.balanceSettings());
    }
    @Test void allConstructorDefaultsMatchTheFixed139Snapshots() throws Exception {
        assertEquals(Toml.Default.parseToTomlTable(fixture("defaults")), Toml.Default.parseToTomlTable(ConfigApi.serializeConfig(new BlueprintConfig()).get()));
        assertEquals(Toml.Default.parseToTomlTable(fixture("personal-defaults")), Toml.Default.parseToTomlTable(ConfigApi.serializeConfig(new ResearchTreeClientConfig()).get()));
    }
    @Test void authoredValuesAndDormantSettingsMatchThe139Baseline() throws Exception {
        var custom = load("custom-current");
        assertEquals(.731, custom.blueprintSpawnChance.getUnconditional());
        assertEquals(.2, custom.balanceSettings().lootChance());
        assertFalse(custom.researchFeatureSnapshot().enforceResearchTiers());
        assertTrue(custom.customEnforceResearchTiers.getUnconditional());
        assertEquals("example:custom_profile", custom.activeResearchProfile.getUnconditional());
        assertEquals(List.of("missing_addon_type"), custom.progressionExemptItemTypes.get());
        assertEquals(3, custom.exactCraftingOverrides.get().size());
        assertEquals(19, custom.exactFragmentThresholds.get().get("missing:weapon"));
        assertEquals("minecraft:copper_ingot", load("banked-rp").researchCurrencyItem.getUnconditional());
        assertEquals(8675, load("banked-rp").progressionSnapshot().pointCap());
        assertEquals("minecraft:diamond", load("custom-currency").villagerFixedCurrency.get());
    }
    @Test void legacyMigrationBehaviorRemainsUnchanged() throws Exception {
        var zero = load("legacy-zero"); var two = load("legacy-two"); var three = load("legacy-three");
        assertEquals(BlueprintBalancePreset.CUSTOM, zero.balancePreset.get()); assertEquals(.42, zero.balanceSettings().lootChance());
        for (var config : List.of(zero, two)) {
            assertEquals(ResearchProgressionPreset.CLASSIC, config.progressionPreset.getUnconditional());
            assertEquals(BlueprintFragmentPreset.DISABLED, config.fragmentPreset.getUnconditional());
            assertTrue(config.customEnforceCraftingTiers.getUnconditional());
        }
        for (var config : List.of(zero, two, three)) assertEquals(StationSetup.ResearchAccess.PRESET, config.researchAccess.get());
        assertEquals(ResearchProgressionPreset.TIERED_RESEARCH, three.progressionPreset.getUnconditional());
        assertEquals(AmmoCraftingStrategy.PROFILE, three.ammoCraftingStrategy.get());
    }
    @Test void personalCurrentAndLegacyValuesRemainFlatAndUnchanged() throws Exception {
        var config = ConfigApi.deserializeConfig(new ResearchTreeClientConfig(), fixture("personal")).get(); config.onUpdateClient();
        String serialized = ConfigApi.serializeConfig(config).get();
        var restored = ConfigApi.deserializeConfig(new ResearchTreeClientConfig(), serialized).get();
        assertEquals(Toml.Default.parseToTomlTable(serialized), Toml.Default.parseToTomlTable(ConfigApi.serializeConfig(restored).get()));
        assertEquals(77, restored.nodeGap.getUnconditional()); assertEquals(83, restored.canvasPadding.getUnconditional());
        assertEquals(1200, restored.holdDurationMillis()); assertFalse(restored.holdToResearchEnabled());
        assertTrue(serialized.contains("version = 3")); assertFalse(serialized.contains("[interaction]"));
    }
}
