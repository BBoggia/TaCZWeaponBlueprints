package com.gamergaming.taczweaponblueprints.research.tree.authoring;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

/** Reads recipe-backed guns from an unpacked TaCZ gun pack without loading Minecraft. */
public final class TaCZGunPackExtractor {
    public static final int MAX_GUNS = 4096;
    public static final long MAX_JSON_BYTES = 1024L * 1024L;
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public List<TaCZGunStats> extract(Path packRoot) throws IOException {
        Path root = packRoot == null ? null : packRoot.toAbsolutePath().normalize();
        if (root == null || !Files.isDirectory(root.resolve("data"))) {
            throw new IOException("TaCZ gun pack must contain a data directory: " + root);
        }

        List<Path> recipes;
        try (var paths = Files.walk(root.resolve("data"))) {
            recipes = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(this::isGunRecipePath)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        if (recipes.size() > MAX_GUNS) {
            throw new IOException("TaCZ gun pack exceeds the " + MAX_GUNS + " gun authoring limit");
        }

        List<TaCZGunStats> result = new ArrayList<>(recipes.size());
        Set<String> seen = new HashSet<>();
        for (Path recipePath : recipes) {
            ParsedJson recipe = readObject(recipePath);
            JsonObject recipeResult = requiredObject(recipe.object(), "result", recipePath);
            if (!"gun".equals(requiredString(recipeResult, "type", recipePath))) {
                continue;
            }
            String blueprintId = resourceId(requiredString(recipeResult, "id", recipePath), recipePath);
            if (!seen.add(blueprintId)) {
                throw new IOException("Duplicate recipe-backed gun " + blueprintId);
            }
            result.add(extractGun(root, blueprintId, recipePath, recipe));
        }
        result.sort(Comparator.comparing(TaCZGunStats::blueprintId));
        return List.copyOf(result);
    }

    private TaCZGunStats extractGun(
            Path root,
            String blueprintId,
            Path recipePath,
            ParsedJson recipe) throws IOException {
        ResourceId gunId = split(blueprintId);
        Path indexPath = resourcePath(root, gunId, "index/guns", gunId.path() + ".json");
        ParsedJson index = readObject(indexPath);
        String dataId = resourceId(requiredString(index.object(), "data", indexPath), indexPath);
        ResourceId gunDataId = split(dataId);
        Path dataPath = resourcePath(root, gunDataId, "data/guns", gunDataId.path() + ".json");
        ParsedJson data = readObject(dataPath);
        JsonObject gun = data.object();
        JsonObject bullet = object(gun, "bullet");
        JsonObject extraDamage = object(bullet, "extra_damage");
        JsonObject explosion = object(bullet, "explosion");
        JsonObject reload = object(gun, "reload");

        List<String> missing = new ArrayList<>();
        Double damage = directDamage(bullet, extraDamage, missing);
        Double rpm = number(gun, "rpm", "rpm", missing);
        Integer capacity = integer(gun, "ammo_amount", "ammo_amount", missing);
        Double projectileSpeed = number(bullet, "speed", "bullet.speed", missing);
        Double range = effectiveRange(bullet, extraDamage, missing);
        String scriptId = optionalString(gun, "script", null);
        Double reloadSeconds = reloadSeconds(gun, reload, capacity, scriptId, missing);
        Double aimTime = number(gun, "aim_time", "aim_time", missing);
        Double drawTime = number(gun, "draw_time", "draw_time", missing);
        Double weight = number(gun, "weight", "weight", missing);
        Double aimedInaccuracy = number(object(gun, "inaccuracy"), "aim", "inaccuracy.aim", missing);
        Double recoil = recoilMagnitude(object(gun, "recoil"), missing);
        Double movementWhileAiming = number(
                object(gun, "movement_speed"),
                "aim",
                "movement_speed.aim",
                missing);
        Integer fireModes = arraySize(gun, "fire_mode", "fire_mode", missing);
        int attachmentTypes = optionalArraySize(gun, "allow_attachment_types");
        Double armorIgnore = optionalNumber(extraDamage, "armor_ignore");
        Double headshot = optionalNumber(extraDamage, "head_shot_multiplier");
        Integer pierce = optionalInteger(bullet, "pierce");
        Double boltAction = optionalNumber(gun, "bolt_action_time");
        if (boltAction == null) {
            boltAction = optionalNumber(object(gun, "script_param"), "bolt_time");
        }
        String reloadType = optionalString(reload, "type", "unknown");
        if (scriptId != null && !"tacz:xmag_reload_logic".equals(scriptId)) {
            missing.add("scripted_behavior_requires_review:" + scriptId);
        }
        boolean explosive = explosion != null && optionalBoolean(explosion, "explode", false);
        Double explosionDamage = explosive ? optionalNumber(explosion, "damage") : 0.0;
        if (explosive && explosionDamage == null) {
            missing.add("bullet.explosion.damage");
        }

        String sourceHash = sha256(recipe.bytes(), index.bytes(), data.bytes());
        return new TaCZGunStats(
                blueprintId,
                requiredString(index.object(), "type", indexPath),
                dataId,
                damage,
                explosionDamage,
                rpm,
                capacity,
                reloadSeconds,
                projectileSpeed,
                range,
                armorIgnore,
                headshot,
                pierce,
                aimTime,
                drawTime,
                weight,
                aimedInaccuracy,
                recoil,
                movementWhileAiming,
                fireModes,
                attachmentTypes,
                boltAction,
                scriptId,
                reloadType,
                explosive,
                sourceHash,
                missing.stream().distinct().sorted().toList());
    }

    private boolean isGunRecipePath(Path path) {
        int count = path.getNameCount();
        return count >= 3
                && "gun".equals(path.getName(count - 2).toString())
                && "recipes".equals(path.getName(count - 3).toString());
    }

    private static Path resourcePath(
            Path root,
            ResourceId id,
            String category,
            String file) throws IOException {
        Path namespaceRoot = root.resolve("data").resolve(id.namespace()).normalize();
        Path resolved = namespaceRoot.resolve(category).resolve(file).normalize();
        if (!resolved.startsWith(namespaceRoot)) {
            throw new IOException("Resource path escapes namespace root: " + id);
        }
        return resolved;
    }

    private static ParsedJson readObject(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Missing TaCZ authoring input: " + path);
        }
        long size = Files.size(path);
        if (size > MAX_JSON_BYTES) {
            throw new IOException("TaCZ authoring input exceeds " + MAX_JSON_BYTES + " bytes: " + path);
        }
        byte[] bytes = Files.readAllBytes(path);
        try {
            JsonReader reader = new JsonReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
            reader.setLenient(true);
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IOException("TaCZ authoring input must be an object: " + path);
            }
            return new ParsedJson(parsed.getAsJsonObject(), bytes);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid TaCZ authoring JSON: " + path, exception);
        }
    }

    private static JsonObject requiredObject(JsonObject object, String field, Path source) throws IOException {
        JsonObject result = object(object, field);
        if (result == null) {
            throw new IOException("Missing object '" + field + "' in " + source);
        }
        return result;
    }

    private static JsonObject object(JsonObject object, String field) {
        if (object == null || !object.has(field) || !object.get(field).isJsonObject()) {
            return null;
        }
        return object.getAsJsonObject(field);
    }

    private static String requiredString(JsonObject object, String field, Path source) throws IOException {
        if (object == null || !object.has(field) || !object.get(field).isJsonPrimitive()
                || !object.getAsJsonPrimitive(field).isString()) {
            throw new IOException("Missing string '" + field + "' in " + source);
        }
        return object.get(field).getAsString();
    }

    private static String optionalString(JsonObject object, String field, String fallback) {
        if (object == null || !object.has(field) || !object.get(field).isJsonPrimitive()
                || !object.getAsJsonPrimitive(field).isString()) {
            return fallback;
        }
        return object.get(field).getAsString();
    }

    private static Double number(JsonObject object, String field, String name, List<String> missing) {
        Double result = optionalNumber(object, field);
        if (result == null) {
            missing.add(name);
        }
        return result;
    }

    private static Double optionalNumber(JsonObject object, String field) {
        if (object == null || !object.has(field) || !object.get(field).isJsonPrimitive()
                || !object.getAsJsonPrimitive(field).isNumber()) {
            return null;
        }
        double value = object.get(field).getAsDouble();
        return Double.isFinite(value) ? value : null;
    }

    private static Integer integer(JsonObject object, String field, String name, List<String> missing) {
        Integer result = optionalInteger(object, field);
        if (result == null) {
            missing.add(name);
        }
        return result;
    }

    private static Integer optionalInteger(JsonObject object, String field) {
        Double value = optionalNumber(object, field);
        if (value == null || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE
                || Math.rint(value) != value) {
            return null;
        }
        return value.intValue();
    }

    private static Integer arraySize(JsonObject object, String field, String name, List<String> missing) {
        if (object == null || !object.has(field) || !object.get(field).isJsonArray()) {
            missing.add(name);
            return null;
        }
        return object.getAsJsonArray(field).size();
    }

    private static int optionalArraySize(JsonObject object, String field) {
        return object != null && object.has(field) && object.get(field).isJsonArray()
                ? object.getAsJsonArray(field).size()
                : 0;
    }

    private static boolean optionalBoolean(JsonObject object, String field, boolean fallback) {
        if (object == null || !object.has(field) || !object.get(field).isJsonPrimitive()
                || !object.getAsJsonPrimitive(field).isBoolean()) {
            return fallback;
        }
        return object.get(field).getAsBoolean();
    }

    private static Double effectiveRange(
            JsonObject bullet,
            JsonObject extraDamage,
            List<String> missing) {
        Double maximum = null;
        if (extraDamage != null && extraDamage.has("damage_adjust")
                && extraDamage.get("damage_adjust").isJsonArray()) {
            for (JsonElement element : extraDamage.getAsJsonArray("damage_adjust")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                Double distance = optionalNumber(element.getAsJsonObject(), "distance");
                if (distance != null) {
                    maximum = maximum == null ? distance : Math.max(maximum, distance);
                }
            }
        }
        if (maximum != null) {
            return maximum;
        }
        Double speed = optionalNumber(bullet, "speed");
        Double life = optionalNumber(bullet, "life");
        if (speed != null && life != null) {
            missing.add("bullet.extra_damage.damage_adjust (used speed * life fallback)");
            return speed * life;
        }
        missing.add("effective_range");
        return null;
    }

    /** TaCZ applies the first distance curve entry at point-blank range. */
    private static Double directDamage(
            JsonObject bullet,
            JsonObject extraDamage,
            List<String> missing) {
        if (extraDamage != null && extraDamage.has("damage_adjust")
                && extraDamage.get("damage_adjust").isJsonArray()) {
            for (JsonElement element : extraDamage.getAsJsonArray("damage_adjust")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                Double damage = optionalNumber(element.getAsJsonObject(), "damage");
                if (damage != null && damage >= 0) {
                    return damage;
                }
            }
            missing.add("bullet.extra_damage.damage_adjust (used base damage fallback)");
        }
        return number(bullet, "damage", "bullet.damage", missing);
    }

    private static Double reloadSeconds(
            JsonObject gun,
            JsonObject reload,
            Integer capacity,
            String scriptId,
            List<String> missing) {
        if (isTubeReloadScript(scriptId) && capacity != null && capacity > 0) {
            JsonObject scriptParameters = object(gun, "script_param");
            JsonObject feed = object(reload, "feed");
            JsonObject cooldown = object(reload, "cooldown");
            Double intro = firstPresent(
                    optionalNumber(scriptParameters, "intro_empty"),
                    optionalNumber(feed, "empty"));
            Double loop = firstPresent(
                    optionalNumber(scriptParameters, "loop"),
                    optionalNumber(cooldown, "empty"));
            Double pairLoop = optionalNumber(scriptParameters, "loop_2");
            Double ending = firstPresent(
                    optionalNumber(scriptParameters, "ending"),
                    optionalNumber(cooldown, "tactical"));
            if (intro != null && loop != null && ending != null) {
                if ("tacz:m1014_gun_logic".equals(scriptId) && pairLoop != null) {
                    return intro
                            + (capacity / 2) * pairLoop
                            + (capacity % 2) * loop
                            + ending;
                }
                return intro + capacity * loop + ending;
            }
            missing.add("scripted_full_reload_duration:" + scriptId);
        }
        JsonObject cooldown = object(reload, "cooldown");
        Double empty = optionalNumber(cooldown, "empty");
        Double tactical = optionalNumber(cooldown, "tactical");
        if (tactical != null || empty != null) {
            return empty != null ? empty : tactical;
        }
        JsonObject feed = object(reload, "feed");
        tactical = optionalNumber(feed, "tactical");
        empty = optionalNumber(feed, "empty");
        if (tactical != null || empty != null) {
            missing.add("reload.cooldown (used feed fallback)");
            return empty != null ? empty : tactical;
        }
        missing.add("reload.duration");
        return null;
    }

    private static boolean isTubeReloadScript(String scriptId) {
        return "tacz:m870_gun_logic".equals(scriptId)
                || "tacz:m1014_gun_logic".equals(scriptId)
                || "tacz:spas_12_gun_logic".equals(scriptId);
    }

    private static Double firstPresent(Double preferred, Double fallback) {
        return preferred == null ? fallback : preferred;
    }

    private static Double recoilMagnitude(JsonObject recoil, List<String> missing) {
        if (recoil == null) {
            missing.add("recoil");
            return null;
        }
        double maximum = 0.0;
        boolean found = false;
        for (String axis : List.of("pitch", "yaw")) {
            if (!recoil.has(axis) || !recoil.get(axis).isJsonArray()) {
                continue;
            }
            for (JsonElement keyframe : recoil.getAsJsonArray(axis)) {
                if (!keyframe.isJsonObject()) {
                    continue;
                }
                JsonElement value = keyframe.getAsJsonObject().get("value");
                if (value == null || !value.isJsonArray()) {
                    continue;
                }
                for (JsonElement component : value.getAsJsonArray()) {
                    if (component.isJsonPrimitive() && component.getAsJsonPrimitive().isNumber()) {
                        maximum = Math.max(maximum, Math.abs(component.getAsDouble()));
                        found = true;
                    }
                }
            }
        }
        if (!found) {
            missing.add("recoil keyframes");
            return null;
        }
        return maximum;
    }

    private static String resourceId(String value, Path source) throws IOException {
        int separator = value.indexOf(':');
        boolean unsafeSegment = separator >= 0 && java.util.Arrays.stream(
                        value.substring(separator + 1).split("/", -1))
                .anyMatch(segment -> segment.isEmpty() || ".".equals(segment) || "..".equals(segment));
        if (!RESOURCE_ID.matcher(value).matches() || unsafeSegment) {
            throw new IOException("Invalid resource ID '" + value + "' in " + source);
        }
        return value;
    }

    private static ResourceId split(String id) {
        int separator = id.indexOf(':');
        return new ResourceId(id.substring(0, separator), id.substring(separator + 1));
    }

    private static String sha256(byte[]... inputs) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] input : inputs) {
                digest.update(input);
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record ParsedJson(JsonObject object, byte[] bytes) {
    }

    private record ResourceId(String namespace, String path) {
    }
}
