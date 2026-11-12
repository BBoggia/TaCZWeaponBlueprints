package com.gamergaming.taczweaponblueprints.settings;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile.DomainPolicy;
import com.gamergaming.taczweaponblueprints.network.*;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import me.fzzyhmstrs.fzzy_config.annotations.WithPerms;
import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class SettingsFoundationTest {
    @TempDir Path temporary;
    BlueprintConfig config;
    AtomicInteger saves, callbacks;
    AtomicReference<String> policy;
    SettingsTransaction transaction;
    Path file;
    @BeforeEach void setup() throws Exception {
        config = new BlueprintConfig(); saves = new AtomicInteger(); callbacks = new AtomicInteger(); policy = new AtomicReference<>("policy:1");
        file = temporary.resolve("blueprint.toml"); Files.writeString(file, FzzySettingsAdapter.serialize(config));
        transaction = service(new SettingsTransaction.Store() {
            public String read() throws IOException { return Files.readString(file); }
            public void save(BlueprintConfig candidate) throws IOException { saves.incrementAndGet(); Files.writeString(file, FzzySettingsAdapter.serialize(candidate)); }
        });
    }
    SettingsTransaction service(SettingsTransaction.Store store) { return new SettingsTransaction(config, store, policy::get, () -> { callbacks.incrementAndGet(); config.initializeLoadedSnapshots(); }); }
    SettingsSnapshot snapshot() throws Exception { return transaction.capture(UUID.randomUUID(), true, SettingsPresentation.Profile.unavailable(config.activeResearchProfile.getUnconditional())); }
    @Test void catalogAccountsForEveryCanonicalServerFieldAndPermission() {
        Set<String> fields = Arrays.stream(BlueprintConfig.class.getFields()).filter(f -> ValidatedField.class.isAssignableFrom(f.getType())).map(f -> f.getName()).collect(Collectors.toSet());
        assertEquals(fields, SettingsCatalog.entries().keySet());
        SettingsCatalog.entries().forEach((id, entry) -> {
            try { assertEquals(entry.permissionLevel(), BlueprintConfig.class.getField(id).getAnnotation(WithPerms.class).opLevel()); }
            catch (NoSuchFieldException error) { throw new AssertionError(error); }
        });
        var serialized = net.peanuuutz.tomlkt.Toml.Default.parseToTomlTable(FzzySettingsAdapter.serialize(config));
        fields.forEach(id -> assertTrue(serialized.containsKey(id), id));
    }
    @Test void openPreviewCancelAndNoOpNeverPublishOrSave() throws Exception {
        var before = FzzySettingsAdapter.capture(config); String disk = Files.readString(file);
        var snapshot = snapshot(); var edit = new SettingsEditSession(snapshot);
        edit.setBoolean("enableBlueprints", false);
        assertFalse(SettingsValues.booleanValue(edit.preview().get("enableBlueprints")));
        assertEquals(before, FzzySettingsAdapter.capture(config));
        edit.discard(); assertThrows(IllegalStateException.class, edit::plan);
        var noop = new SettingsEditSession(snapshot); noop.setBoolean("enableBlueprints", true);
        assertFalse(noop.dirty()); assertEquals(SettingsTransaction.Status.NO_CHANGES, transaction.apply(snapshot, noop.plan(), true));
        assertEquals(0, saves.get()); assertEquals(0, callbacks.get()); assertEquals(disk, Files.readString(file));
    }
    @Test void simpleToggleKeepsWrappersAndDormantValuesAndReloads() throws Exception {
        config.customEnforceResearchTiers.validateAndSet(false);
        config.blueprintSpawnChance.validateAndSet(.731);
        var before = FzzySettingsAdapter.capture(config); var wrapper = config.enableResearch;
        var snapshot = snapshot(); var edit = new SettingsEditSession(snapshot); edit.setBoolean("enableResearch", false);
        assertEquals(SettingsTransaction.Status.APPLIED, transaction.apply(snapshot, edit.plan(), true));
        assertSame(wrapper, config.enableResearch); assertEquals(1, saves.get()); assertEquals(1, callbacks.get());
        var expected = new LinkedHashMap<>(before); expected.put("enableResearch", SettingsValues.bool(false));
        assertEquals(expected, FzzySettingsAdapter.capture(config));
        var loaded = ConfigApi.deserializeConfig(new BlueprintConfig(), Files.readString(file)).get(); loaded.initializeLoadedSnapshots();
        assertEquals(expected, FzzySettingsAdapter.capture(loaded));
    }
    @ParameterizedTest @EnumSource(ResearchProgressionPreset.class)
    void independentCraftingChangePreservesPresetResearchAndExplicitStationMode(ResearchProgressionPreset preset) throws Exception {
        config.progressionPreset.validateAndSet(preset); config.customEnforceResearchTiers.validateAndSet(false);
        config.researchAccess.validateAndSet(StationSetup.ResearchAccess.SINGLE);
        var snapshot = snapshot(); var edit = new SettingsEditSession(snapshot);
        boolean oldCrafting = preset.enforcesCrafting(config.customEnforceCraftingTiers.getUnconditional());
        edit.setCraftingTiers(!oldCrafting);
        assertEquals(SettingsTransaction.Status.APPLIED, transaction.apply(snapshot, edit.plan(), true));
        assertEquals(ResearchProgressionPreset.CUSTOM, config.progressionPreset.getUnconditional());
        assertEquals(preset.enforcesResearch(false), config.customEnforceResearchTiers.getUnconditional());
        assertEquals(!oldCrafting, config.customEnforceCraftingTiers.getUnconditional());
        assertEquals(StationSetup.ResearchAccess.SINGLE, config.researchAccess.get());
        assertFalse(config.researchFeatureSnapshot().enforceResearchTiers());
        assertEquals(1, callbacks.get());
    }
    @Test void invalidCompoundEditIsAllOrNothing() throws Exception {
        var snapshot = snapshot(); var before = FzzySettingsAdapter.capture(config);
        var edit = new SettingsEditSession(snapshot); edit.setBoolean("enableResearch", false); edit.set("tierOneUpperPercent", "value = 90");
        assertEquals(SettingsTransaction.Status.INVALID, transaction.apply(snapshot, edit.plan(), true));
        assertEquals(before, FzzySettingsAdapter.capture(config)); assertEquals(0, saves.get()); assertEquals(0, callbacks.get());
    }
    @ParameterizedTest @ValueSource(strings = {"value = -1", "value = 100001", "value = \"false\"", "value = 4\nextra = 9"})
    void invalidAndCorrectedValuesCannotApply(String value) throws Exception {
        var snapshot = snapshot(); var plan = new SettingsChangePlan(snapshot.session(), snapshot.revision(), Map.of("researchPointCap", value));
        assertEquals(SettingsTransaction.Status.INVALID, transaction.apply(snapshot, plan, true)); assertEquals(0, saves.get());
    }
    @Test void permissionLossAndWrongSessionAreRejected() throws Exception {
        var snapshot = snapshot(); var edit = new SettingsEditSession(snapshot); edit.setBoolean("enableResearch", false);
        assertEquals(SettingsTransaction.Status.DENIED, transaction.apply(snapshot, edit.plan(), false));
        assertEquals(SettingsTransaction.Status.STALE, transaction.apply(snapshot, new SettingsChangePlan(UUID.randomUUID(), snapshot.revision(), edit.plan().changes()), true));
        assertEquals(0, saves.get()); assertTrue(config.enableResearch.getUnconditional());
    }
    @Test void otherOperatorFileEditAndPolicyReloadEachInvalidateDraft() throws Exception {
        var snapshot = snapshot(); var edit = new SettingsEditSession(snapshot); edit.setBoolean("enableResearch", false);
        config.enableJournal.validateAndSet(false);
        assertEquals(SettingsTransaction.Status.STALE, transaction.apply(snapshot, edit.plan(), true));
        snapshot = snapshot(); edit = new SettingsEditSession(snapshot); edit.setBoolean("enableResearch", false);
        Files.writeString(file, "\n# external operator edit", StandardOpenOption.APPEND);
        assertEquals(SettingsTransaction.Status.STALE, transaction.apply(snapshot, edit.plan(), true));
        snapshot = snapshot(); edit = new SettingsEditSession(snapshot); edit.setBoolean("enableResearch", false); policy.set("policy:2");
        assertEquals(SettingsTransaction.Status.STALE, transaction.apply(snapshot, edit.plan(), true));
        assertTrue(edit.dirty()); assertEquals(0, saves.get()); assertEquals(0, callbacks.get());
    }
    @Test void failedAndSilentlyFailedSaveDoNotPublish() throws Exception {
        for (boolean silent : List.of(false, true)) {
            transaction = service(new SettingsTransaction.Store() {
                public String read() throws IOException { return Files.readString(file); }
                public void save(BlueprintConfig candidate) throws IOException { if (!silent) throw new IOException("fixture unwritable"); }
            });
            var snapshot = snapshot(); var edit = new SettingsEditSession(snapshot); edit.setBoolean("enableResearch", false);
            assertEquals(SettingsTransaction.Status.SAVE_FAILED, transaction.apply(snapshot, edit.plan(), true));
            assertTrue(config.enableResearch.getUnconditional()); assertEquals(0, callbacks.get());
        }
    }
    @Test void persistedEditWithFailedRefreshIsNotReportedAsUnapplied() throws Exception {
        transaction = new SettingsTransaction(config, new SettingsTransaction.Store() {
            public String read() throws IOException { return Files.readString(file); }
            public void save(BlueprintConfig candidate) throws IOException { Files.writeString(file, FzzySettingsAdapter.serialize(candidate)); }
        }, policy::get, () -> { throw new IllegalStateException("fixture refresh failure"); });
        var snapshot = snapshot(); var edit = new SettingsEditSession(snapshot); edit.setBoolean("enableResearch", false);
        assertEquals(SettingsTransaction.Status.APPLIED_REFRESH_FAILED, transaction.apply(snapshot, edit.plan(), true));
        assertFalse(config.enableResearch.getUnconditional());
        assertEquals(SettingsTransaction.Status.STALE, transaction.apply(snapshot, edit.plan(), true));
    }
    @Test void unavailableIntegrationAndUnknownOrPersonalFieldsCannotBePatched() throws Exception {
        var snapshot = snapshot();
        for (String id : List.of("magazineKeepBasicCrafting", "showBranchLabels", "madeUpField")) {
            assertEquals(SettingsTransaction.Status.INVALID, transaction.apply(snapshot,
                    new SettingsChangePlan(snapshot.session(), snapshot.revision(), Map.of(id, "value = false")), true));
        }
        assertEquals(0, saves.get());
    }
    @Test void profileStateIsNeverGuessedAndEffectiveDomainUsesExistingResolver() throws Exception {
        var snapshot = snapshot();
        var summary = SettingsPresentation.describe(snapshot, snapshot.values());
        assertTrue(summary.controls().get("ammoResearch").effective().isEmpty());
        var no = new DomainPolicy(false, false); var yes = new DomainPolicy(true, true);
        snapshot = new SettingsSnapshot(snapshot.session(), snapshot.revision(), true, snapshot.values(),
                new SettingsPresentation.Profile(config.activeResearchProfile.getUnconditional(), true, yes, no, yes));
        summary = SettingsPresentation.describe(snapshot, snapshot.values());
        assertEquals(SettingsValues.bool(false), summary.controls().get("ammoResearch").effective().orElseThrow());
        assertEquals(SettingsValues.bool(true), summary.controls().get("attachmentResearch").effective().orElseThrow());
        var edit = new SettingsEditSession(snapshot); edit.setChoice("ammoResearch", ResearchDomainMode.ENABLED);
        assertEquals(SettingsValues.bool(true), SettingsPresentation.describe(snapshot, edit.preview()).controls().get("ammoResearch").effective().orElseThrow());
        edit.set("activeResearchProfile", "value = \"example:missing\"");
        assertTrue(SettingsPresentation.describe(snapshot, edit.preview()).controls().get("ammoResearch").effective().isEmpty());
        assertEquals(0, saves.get()); assertEquals(0, callbacks.get());
    }
    @Test void requestBoundsRejectOversizeBeforeSend() throws Exception {
        var snapshot = snapshot();
        assertThrows(IllegalArgumentException.class, () -> new SettingsRequestPacket(UUID.randomUUID(), SettingsRequestPacket.Action.APPLY,
                new SettingsChangePlan(snapshot.session(), snapshot.revision(), Map.of("startingBlueprints", "x".repeat(24000)))));
    }
    @Test void submittedDraftIsFrozenAndPreservesItsProposalForFailureReview() throws Exception {
        var edit = new SettingsEditSession(snapshot()); edit.setBoolean("enableResearch", false);
        var proposal = edit.plan(); edit.freeze();
        assertThrows(IllegalStateException.class, () -> edit.setBoolean("enableJournal", false));
        assertEquals(proposal, edit.plan()); assertEquals(proposal.changes(), edit.proposedChanges());
    }
    @Test void reviewAgainstFreshStatePreservesProposalAndOtherOperatorsChanges() throws Exception {
        var edit = new SettingsEditSession(snapshot()); edit.setBoolean("enableResearch", false); edit.freeze();
        config.enableJournal.validateAndSet(false);
        var fresh = snapshot();
        assertTrue(edit.dirty()); assertTrue(SettingsValues.booleanValue(edit.preview().get("enableJournal")));
        var reviewed = edit.reviewedAgainst(fresh);
        assertFalse(SettingsValues.booleanValue(reviewed.preview().get("enableJournal")));
        assertEquals(Map.of("enableResearch", SettingsValues.bool(false)), reviewed.plan().changes());
        assertEquals(SettingsTransaction.Status.APPLIED, transaction.apply(fresh, reviewed.plan(), true));
        assertFalse(config.enableResearch.getUnconditional()); assertFalse(config.enableJournal.getUnconditional());
    }
    @Test void presentationConditionAndResultKeysHaveTranslations() throws Exception {
        var json = com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/taczweaponblueprints/lang/en_us.json"))).getAsJsonObject();
        for (var entry : SettingsCatalog.entries().values()) {
            assertTrue(json.has(entry.labelKey()), entry.field());
            if (entry.condition() != SettingsCondition.ALWAYS) assertTrue(json.has(entry.condition().reasonKey()), entry.field());
        }
        for (var status : SettingsTransaction.Status.values()) assertTrue(json.has(status.translationKey()), status.name());
    }
    @Test void dormantAndUnsupportedControlsDoNotClaimToBeActive() throws Exception {
        var snapshot = snapshot(); var summary = SettingsPresentation.describe(snapshot, snapshot.values());
        assertEquals(SettingsPresentation.Availability.INACTIVE, summary.controls().get("blueprintSpawnChance").availability());
        assertTrue(summary.controls().get("blueprintSpawnChance").effective().isEmpty());
        assertEquals(SettingsPresentation.Availability.UNAVAILABLE, summary.controls().get("magazineProgressionMode").availability());
        assertTrue(summary.controls().get("magazineProgressionMode").effective().isEmpty());
        assertTrue(summary.weaponsResearch().isEmpty());
    }
    @Test void valuesSupportMapsListsAndEscapedStringsWithoutSharedMutation() {
        for (String input : List.of("value = { \"missing:bench\" = \"TIER_3\" }", "value = [\"missing:gun\", \"test:other\"]", "value = \"quoted\\\" value\""))
            assertEquals(SettingsValues.decode(input), SettingsValues.decode(SettingsValues.canonical(input)));
    }
}
