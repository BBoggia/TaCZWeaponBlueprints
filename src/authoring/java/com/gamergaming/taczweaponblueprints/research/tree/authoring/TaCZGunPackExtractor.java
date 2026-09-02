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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponStatEvidence;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.TaCZRuntimeWeaponEvidenceAdapter;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;

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
        String gunType = requiredString(index.object(), "type", indexPath);
        final GunData gun;
        try {
            gun = CommonAssetsManager.GSON.fromJson(data.object(), GunData.class);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid TaCZ gun data: " + dataPath, exception);
        }
        if (gun == null) {
            throw new IOException("TaCZ gun data resolved to null: " + dataPath);
        }
        WeaponStatEvidence evidence = new TaCZRuntimeWeaponEvidenceAdapter()
                .normalize(blueprintId, gunType, gun);
        String sourceHash = sha256(recipe.bytes(), index.bytes(), data.bytes());
        return new TaCZGunStats(
                blueprintId,
                gunType,
                dataId,
                evidence.baseDamage(), evidence.explosionDamage(),
                evidence.roundsPerMinute(), evidence.magazineCapacity(),
                evidence.reloadSeconds(), evidence.projectileSpeed(),
                evidence.effectiveRange(), evidence.armorIgnore(),
                evidence.headshotMultiplier(), evidence.pierce(),
                evidence.aimTimeSeconds(), evidence.drawTimeSeconds(), evidence.weight(),
                evidence.aimedInaccuracy(), evidence.recoilMagnitude(),
                evidence.movementSpeedWhileAiming(), evidence.fireModeCount(),
                evidence.attachmentTypeCount(), evidence.boltActionSeconds(),
                gun.getScript() == null ? null : gun.getScript().toString(),
                evidence.reloadType(), evidence.explosive(), evidence.projectileCount(),
                evidence.damageRetention(), evidence.explosionRadius(),
                evidence.explosionDelaySeconds(), evidence.explosionKnockback(),
                evidence.projectileIgnitesEntities(), evidence.igniteEntitySeconds(),
                evidence.projectileGravity(), evidence.tacticalReloadSeconds(),
                evidence.burstCount(), evidence.burstRoundsPerMinute(),
                evidence.heatCapacityShots(), evidence.chargeSeconds(),
                evidence.fireModes(), evidence.heatRecoverySeconds(),
                evidence.heatMinimumRpmMultiplier(), evidence.heatMaximumRpmMultiplier(),
                evidence.heatMinimumInaccuracyMultiplier(),
                evidence.heatMaximumInaccuracyMultiplier(),
                sourceHash,
                evidence.warnings());
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
