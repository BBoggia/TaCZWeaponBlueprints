package com.gamergaming.taczweaponblueprints.research.tree.authoring;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Strict loader for optional, reviewable appeal scores used by the authoring tool. */
public final class AppealRatings {
    public static final int CURRENT_FORMAT = 1;

    private AppealRatings() {
    }

    public static Map<String, AppealRating> load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IOException("Appeal rating file must be a JSON object: " + path);
            }
            JsonObject root = parsed.getAsJsonObject();
            rejectUnknown(root, path, "format", "ratings");
            if (!root.has("format") || !root.get("format").isJsonPrimitive()
                    || root.get("format").getAsInt() != CURRENT_FORMAT) {
                throw new IOException("Appeal rating format must be " + CURRENT_FORMAT + ": " + path);
            }
            if (!root.has("ratings") || !root.get("ratings").isJsonObject()) {
                throw new IOException("Appeal rating file requires a ratings object: " + path);
            }

            Map<String, AppealRating> ratings = new LinkedHashMap<>();
            root.getAsJsonObject("ratings").entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> ratings.put(entry.getKey(), parse(entry.getKey(), entry.getValue(), path)));
            return Map.copyOf(ratings);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid appeal rating file: " + path, exception);
        }
    }

    private static AppealRating parse(String id, JsonElement element, Path path) {
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || !element.isJsonObject()) {
            throw new IllegalArgumentException("Invalid appeal rating entry '" + id + "' in " + path);
        }
        JsonObject object = element.getAsJsonObject();
        rejectUnknown(object, path, "score", "reason");
        if (!object.has("score") || !object.get("score").isJsonPrimitive()
                || !object.getAsJsonPrimitive("score").isNumber()
                || !object.has("reason") || !object.get("reason").isJsonPrimitive()
                || !object.getAsJsonPrimitive("reason").isString()) {
            throw new IllegalArgumentException("Appeal rating requires numeric score and string reason for " + id);
        }
        return new AppealRating(object.get("score").getAsInt(), object.get("reason").getAsString());
    }

    private static void rejectUnknown(JsonObject object, Path path, String... allowed) {
        var allowedFields = java.util.Arrays.stream(allowed).collect(java.util.stream.Collectors.toSet());
        String unknown = object.keySet().stream()
                .filter(field -> !allowedFields.contains(field))
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (unknown != null) {
            throw new IllegalArgumentException("Unknown field '" + unknown + "' in " + path);
        }
    }
}
