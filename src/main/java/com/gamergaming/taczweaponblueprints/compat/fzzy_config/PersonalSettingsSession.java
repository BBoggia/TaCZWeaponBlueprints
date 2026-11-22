package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition;
import com.gamergaming.taczweaponblueprints.settings.SettingsTransaction.Status;
import com.gamergaming.taczweaponblueprints.settings.SettingsValues;
import net.peanuuutz.tomlkt.Toml;

/** A client-only draft. Hidden legacy values remain intact; only explicit reset changes their defaults. */
public final class PersonalSettingsSession {
    public static final List<String> VISIBLE = List.of("techTreeLanes", "holdToResearch", "holdDurationMillis",
            "showResearchPointNotifications");
    public interface Store {
        String read() throws IOException;
        void save(ResearchTreeClientConfig candidate) throws IOException;
    }
    private final ResearchTreeClientConfig live, editor;
    private final Store store;
    private final Map<String, String> baseline;
    private final String originalFile;
    public PersonalSettingsSession(ResearchTreeClientConfig live, Store store) throws IOException {
        this.live = live; this.store = store; baseline = capture(live); originalFile = store.read();
        editor = fromValues(baseline);
    }
    public static Store fzzyStore(ResearchTreeClientConfig config) {
        Path file = config.getDir().toPath().resolve(config.getName() + ".toml");
        return new Store() {
            public String read() throws IOException { return Files.exists(file) ? Files.readString(file) : ""; }
            public void save(ResearchTreeClientConfig candidate) { candidate.save(); }
        };
    }
    public ResearchTreeClientConfig editor() { return editor; }
    public boolean dirty() { return !baseline.equals(capture(editor)); }
    public void resetAppearance() { editor.restoreTreeAppearanceDefaults(); }
    @SuppressWarnings("unchecked")
    public static ValidatedField<Object> field(ResearchTreeClientConfig config, String id) {
        try { return (ValidatedField<Object>) ResearchTreeClientConfig.class.getField(id).get(config); }
        catch (ReflectiveOperationException error) { throw new IllegalArgumentException(id, error); }
    }
    private static Object stored(ValidatedField<?> field) {
        return field instanceof ValidatedCondition<?> condition ? condition.getUnconditional() : field.get();
    }
    public static Map<String, String> capture(ResearchTreeClientConfig config) {
        Map<String, String> values = new LinkedHashMap<>();
        for (var member : ResearchTreeClientConfig.class.getFields()) {
            if (!ValidatedField.class.isAssignableFrom(member.getType())) continue;
            var field = field(config, member.getName()); var value = field.serialize(stored(field));
            if (!value.isValid()) throw new IllegalArgumentException(member.getName());
            values.put(member.getName(), SettingsValues.encode(value.get()));
        }
        return Collections.unmodifiableMap(values);
    }
    private static ResearchTreeClientConfig fromValues(Map<String, String> values) {
        var config = new ResearchTreeClientConfig();
        values.forEach((id, value) -> {
            var field = field(config, id); var parsed = field.deserialize(SettingsValues.decode(value), id);
            if (!parsed.isValid()) throw new IllegalArgumentException(id);
            field.validateAndSet(parsed.get());
        });
        if (!values.equals(capture(config))) throw new IllegalArgumentException("Personal settings did not round trip");
        return config;
    }
    public static String serialize(ResearchTreeClientConfig config) {
        var value = ConfigApi.serializeConfig(config);
        if (!value.isValid()) throw new IllegalArgumentException("Unable to serialize personal settings");
        return value.get();
    }
    public Status apply() {
        boolean saved = false;
        try {
            if (!baseline.equals(capture(live)) || !originalFile.equals(store.read())) return Status.STALE;
            var proposed = capture(editor);
            if (baseline.equals(proposed)) return Status.NO_CHANGES;
            var candidate = fromValues(proposed);
            candidate.onUpdateClient();
            if (!proposed.equals(capture(candidate))) return Status.INVALID;
            store.save(candidate);
            if (!Toml.Default.parseToTomlTable(store.read()).equals(Toml.Default.parseToTomlTable(serialize(candidate)))) return Status.SAVE_FAILED;
            saved = true;
            proposed.forEach((id, value) -> {
                if (value.equals(baseline.get(id))) return;
                var source = field(candidate, id);
                field(live, id).validateAndSet(source.copyValue(stored(source)));
            });
            live.onUpdateClient();
            return Status.APPLIED;
        } catch (IOException | RuntimeException error) {
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.LOGGER.warn("Could not save personal settings", error);
            return saved ? Status.APPLIED_REFRESH_FAILED : Status.SAVE_FAILED;
        }
    }
}
