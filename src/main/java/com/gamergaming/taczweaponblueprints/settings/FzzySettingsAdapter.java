package com.gamergaming.taczweaponblueprints.settings;

import java.lang.reflect.Field;
import java.util.*;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import me.fzzyhmstrs.fzzy_config.entry.EntryValidator.ValidationType;
import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition;

/** Uses public Fzzy APIs and our own public config fields; no private GUI or registry access. */
public final class FzzySettingsAdapter {
    private static final Map<String, Field> FIELDS = fields();
    private FzzySettingsAdapter() {}
    private static Map<String, Field> fields() {
        Map<String, Field> result = new LinkedHashMap<>();
        for (String name : SettingsCatalog.entries().keySet()) {
            try {
                Field field = BlueprintConfig.class.getField(name);
                if (!ValidatedField.class.isAssignableFrom(field.getType())) throw new IllegalStateException(name);
                result.put(name, field);
            } catch (NoSuchFieldException error) { throw new ExceptionInInitializerError(error); }
        }
        return Collections.unmodifiableMap(result);
    }
    @SuppressWarnings("unchecked")
    private static ValidatedField<Object> field(BlueprintConfig config, String id) {
        Field field = FIELDS.get(id);
        if (field == null) throw new IllegalArgumentException("Unknown settings field: " + id);
        try { return (ValidatedField<Object>) field.get(config); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }
    private static Object stored(ValidatedField<?> field) {
        return field instanceof ValidatedCondition<?> condition ? condition.getUnconditional() : field.get();
    }
    public static Map<String, String> capture(BlueprintConfig config) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String id : FIELDS.keySet()) {
            result.put(id, captureField(config, id));
        }
        return Collections.unmodifiableMap(result);
    }
    public static String captureField(BlueprintConfig config, String id) {
        var field = field(config, id);
        var serialized = field.serialize(stored(field));
        if (!serialized.isValid()) throw new IllegalArgumentException("Unable to read " + id);
        return SettingsValues.encode(serialized.get());
    }
    /** A detached copy retains dormant and unavailable values without validating them as new edits. */
    public static BlueprintConfig copy(BlueprintConfig source) {
        BlueprintConfig copy = new BlueprintConfig();
        for (String id : FIELDS.keySet()) {
            var from = field(source, id);
            field(copy, id).validateAndSet(from.copyValue(stored(from)));
        }
        if (!capture(source).equals(capture(copy))) throw new IllegalStateException("Settings copy changed stored values");
        return copy;
    }
    public static BlueprintConfig fromValues(Map<String, String> values) {
        BlueprintConfig config = new BlueprintConfig();
        if (!values.keySet().equals(FIELDS.keySet())) throw new IllegalArgumentException("Incomplete settings values");
        for (var entry : values.entrySet()) {
            var field = field(config, entry.getKey());
            var parsed = field.deserialize(SettingsValues.decode(entry.getValue()), entry.getKey());
            if (!parsed.isValid()) throw new IllegalArgumentException("Invalid stored value: " + entry.getKey());
            field.validateAndSet(parsed.get());
        }
        if (!capture(config).equals(values)) throw new IllegalArgumentException("Stored values did not round trip");
        return config;
    }
    public static BlueprintConfig prepare(BlueprintConfig live, Map<String, String> changes) {
        BlueprintConfig candidate = copy(live);
        for (var entry : changes.entrySet()) {
            if (!SettingsCatalog.entry(entry.getKey()).supported()) throw new IllegalArgumentException("Unavailable integration");
            var field = field(candidate, entry.getKey());
            var parsed = field.deserialize(SettingsValues.decode(entry.getValue()), entry.getKey());
            if (!parsed.isValid() || !field.validateEntry(parsed.get(), ValidationType.STRONG).isValid())
                throw new IllegalArgumentException("Invalid settings value: " + entry.getKey());
            field.validateAndSet(parsed.get());
            if (!SettingsValues.decode(entry.getValue()).equals(field.serialize(stored(field)).get()))
                throw new IllegalArgumentException("Settings value requires correction: " + entry.getKey());
        }
        // Reject a bad coordinated edit; normalizing it would change fields the operator did not submit.
        if (candidate.maxBlueprints.getUnconditional() < candidate.minBlueprints.getUnconditional()
                || candidate.tierTwoUpperPercent.get() <= candidate.tierOneUpperPercent.get()
                || candidate.fragmentRetainedProgressCap.getUnconditional() < Math.max(
                        candidate.tierOneFragmentThreshold.getUnconditional(), Math.max(
                        candidate.tierTwoFragmentThreshold.getUnconditional(), candidate.tierThreeFragmentThreshold.getUnconditional())))
            throw new IllegalArgumentException("Inconsistent settings range");
        candidate.initializeLoadedSnapshots();
        Map<String, String> expected = new LinkedHashMap<>(capture(live));
        changes.forEach((id, value) -> expected.put(id, SettingsValues.canonical(value)));
        if (!capture(candidate).equals(expected)) throw new IllegalArgumentException("Settings normalization would affect other fields");
        return candidate;
    }
    /** Retain registered field wrappers and their UI listeners. Call only after complete validation/persistence. */
    public static void install(BlueprintConfig live, BlueprintConfig candidate, Set<String> changes) {
        for (String id : changes) {
            var from = field(candidate, id);
            field(live, id).validateAndSet(from.copyValue(stored(from)));
        }
    }
    public static String serialize(BlueprintConfig config) {
        var result = ConfigApi.serializeConfig(config);
        if (!result.isValid()) throw new IllegalArgumentException("Unable to serialize settings");
        return result.get();
    }
}
