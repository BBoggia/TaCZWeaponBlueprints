package com.gamergaming.taczweaponblueprints.settings;

import java.util.*;
import net.peanuuutz.tomlkt.*;

/** Immutable, canonical TOML values. Each value is a single-key document, including maps/lists. */
public final class SettingsValues {
    private SettingsValues() {}
    public static String encode(TomlElement value) {
        return Toml.Default.encodeToString(TomlTable.Companion.serializer(), new TomlTable(Map.of("value", value), Map.of()));
    }
    public static TomlElement decode(String document) {
        TomlTable table = Toml.Default.parseToTomlTable(document);
        if (!table.keySet().equals(Set.of("value"))) throw new IllegalArgumentException("Expected one settings value");
        return table.get("value");
    }
    public static String canonical(String document) { return encode(decode(document)); }
    public static String bool(boolean value) { return canonical("value = " + value); }
    public static String token(Enum<?> value) { return canonical("value = \"" + value.name() + "\""); }
    public static String scalar(String document) { return ((TomlLiteral) decode(document)).getContent(); }
    public static boolean booleanValue(String document) {
        String value = scalar(document);
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Expected boolean");
        return Boolean.parseBoolean(value);
    }
}
