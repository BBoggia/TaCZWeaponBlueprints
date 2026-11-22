package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.gamergaming.taczweaponblueprints.settings.SettingsTransaction.Status;
import com.gamergaming.taczweaponblueprints.settings.SettingsValues;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PersonalSettingsSessionTest {
    @TempDir Path directory;
    ResearchTreeClientConfig live;
    Path file;
    AtomicInteger saves;
    PersonalSettingsSession.Store store;
    @BeforeEach void setup() throws Exception {
        live = ConfigApi.deserializeConfig(new ResearchTreeClientConfig(), Files.readString(Path.of("src/test/resources/settings/personal.toml"))).get();
        live.onUpdateClient(); file = directory.resolve("personal.toml"); saves = new AtomicInteger();
        Files.writeString(file, PersonalSettingsSession.serialize(live));
        store = new PersonalSettingsSession.Store() {
            public String read() throws java.io.IOException { return Files.readString(file); }
            public void save(ResearchTreeClientConfig value) throws java.io.IOException { saves.incrementAndGet(); Files.writeString(file, PersonalSettingsSession.serialize(value)); }
        };
    }
    @Test void openingEditingAndAbandoningDraftDoNotChangeLiveValuesOrDisk() throws Exception {
        var before = PersonalSettingsSession.capture(live); String disk = Files.readString(file);
        var session = new PersonalSettingsSession(live, store);
        session.editor().showBranchLabels.validateAndSet(true);
        assertTrue(session.dirty()); assertEquals(before, PersonalSettingsSession.capture(live));
        assertEquals(disk, Files.readString(file)); assertEquals(0, saves.get());
        var reopened = new PersonalSettingsSession(live, store); assertFalse(reopened.dirty());
        assertEquals(Status.NO_CHANGES, reopened.apply()); assertEquals(0, saves.get());
    }
    @Test void visibleEditSavesOnlyItsValueAndPreservesHiddenCompatibilityFields() throws Exception {
        var before = PersonalSettingsSession.capture(live); var wrapper = live.reduceMotion;
        var session = new PersonalSettingsSession(live, store); session.editor().reduceMotion.validateAndSet(false);
        assertEquals(Status.APPLIED, session.apply()); assertSame(wrapper, live.reduceMotion);
        var expected = new LinkedHashMap<>(before); expected.put("reduceMotion", SettingsValues.bool(false));
        assertEquals(expected, PersonalSettingsSession.capture(live)); assertFalse(live.displayPolicy().reduceMotion());
        var reloaded = ConfigApi.deserializeConfig(new ResearchTreeClientConfig(), Files.readString(file)).get(); reloaded.onUpdateClient();
        assertEquals(PersonalSettingsSession.serialize(live), PersonalSettingsSession.serialize(reloaded));
        assertEquals(77, reloaded.nodeGap.getUnconditional()); assertEquals(1200, reloaded.holdDurationMillis());
        assertEquals(1, saves.get());
    }
    @Test void appearanceResetPreservesConfirmationAndNotificationsAndWaitsForApply() throws Exception {
        var before = PersonalSettingsSession.capture(live); var session = new PersonalSettingsSession(live, store);
        session.resetAppearance(); assertEquals(before, PersonalSettingsSession.capture(live)); assertEquals(0, saves.get());
        assertEquals(Status.APPLIED, session.apply());
        for (String key : List.of("holdToResearch", "holdDurationMillis", "showResearchPointNotifications"))
            assertEquals(before.get(key), PersonalSettingsSession.capture(live).get(key), key);
        var defaults = PersonalSettingsSession.capture(new ResearchTreeClientConfig());
        for (String key : defaults.keySet()) if (!List.of("holdToResearch", "holdDurationMillis", "showResearchPointNotifications").contains(key))
            assertEquals(defaults.get(key), PersonalSettingsSession.capture(live).get(key), key);
    }
    @Test void anotherClientControlOrFileEditCannotBeOverwritten() throws Exception {
        var session = new PersonalSettingsSession(live, store); session.editor().reduceMotion.validateAndSet(false);
        live.showBranchLabels.validateAndSet(true);
        assertEquals(Status.STALE, session.apply()); assertTrue(live.reduceMotion.get()); assertEquals(0, saves.get());
        session = new PersonalSettingsSession(live, store); session.editor().reduceMotion.validateAndSet(false);
        Files.writeString(file, "\n# external edit", StandardOpenOption.APPEND);
        assertEquals(Status.STALE, session.apply()); assertTrue(live.reduceMotion.get());
    }
    @Test void failedAndSilentSavesDoNotInstallTheDraft() throws Exception {
        for (boolean silent : List.of(true, false)) {
            var session = new PersonalSettingsSession(live, new PersonalSettingsSession.Store() {
                public String read() throws java.io.IOException { return Files.readString(file); }
                public void save(ResearchTreeClientConfig value) throws java.io.IOException { if (!silent) throw new java.io.IOException("fixture denied"); }
            });
            session.editor().reduceMotion.validateAndSet(false);
            assertEquals(Status.SAVE_FAILED, session.apply()); assertTrue(live.reduceMotion.get()); assertTrue(session.dirty());
        }
    }
    @Test void projectionIncludesOnlyActiveControlsWithoutDeprecatingStoredFields() throws Exception {
        assertEquals(Set.of("techTreeLanes", "holdToResearch", "holdDurationMillis", "showResearchPointNotifications"), Set.copyOf(PersonalSettingsSession.VISIBLE));
        var table = net.peanuuutz.tomlkt.Toml.Default.parseToTomlTable(PersonalSettingsSession.serialize(live));
        for (String key : List.of("layoutPreset", "canvasPadding", "nodeGap", "tierGap", "portalPadding", "orderingSweeps", "compactionSweeps")) {
            assertTrue(table.containsKey(key));
            assertFalse(ResearchTreeClientConfig.class.getField(key).isAnnotationPresent(me.fzzyhmstrs.fzzy_config.annotations.ConfigDeprecated.class));
        }
    }
}
