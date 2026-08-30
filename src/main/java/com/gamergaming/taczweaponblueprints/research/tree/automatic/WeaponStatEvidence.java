package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.List;

/**
 * Loader-independent, normalized mechanical evidence for one researchable weapon.
 * Nullable boxed values mean that the source did not provide trustworthy evidence.
 */
public record WeaponStatEvidence(
        String blueprintId,
        String archetype,
        Double baseDamage,
        Double explosionDamage,
        Double roundsPerMinute,
        Integer magazineCapacity,
        Double reloadSeconds,
        Double projectileSpeed,
        Double effectiveRange,
        Double armorIgnore,
        Double headshotMultiplier,
        Integer pierce,
        Double aimTimeSeconds,
        Double drawTimeSeconds,
        Double weight,
        Double aimedInaccuracy,
        Double recoilMagnitude,
        Double movementSpeedWhileAiming,
        Integer fireModeCount,
        Integer attachmentTypeCount,
        Double boltActionSeconds,
        String reloadType,
        boolean explosive,
        boolean scriptControlled,
        List<String> warnings) {
    public WeaponStatEvidence {
        blueprintId = requireText(blueprintId, "blueprint ID");
        archetype = requireText(archetype, "archetype");
        reloadType = requireText(reloadType, "reload type");
        List<String> sourceWarnings = warnings == null
                ? List.of()
                : new java.util.ArrayList<>(warnings);
        if (sourceWarnings.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(
                    "Weapon mechanical evidence warnings cannot contain blank values");
        }
        warnings = sourceWarnings.stream().distinct().sorted().toList();
        validateNonNegative(baseDamage, "base damage");
        validateNonNegative(explosionDamage, "explosion damage");
        validatePositive(roundsPerMinute, "rounds per minute");
        validateNonNegative(magazineCapacity, "magazine capacity");
        validateNonNegative(reloadSeconds, "reload seconds");
        validateNonNegative(projectileSpeed, "projectile speed");
        validateNonNegative(effectiveRange, "effective range");
        validateNonNegative(armorIgnore, "armor ignore");
        validateNonNegative(headshotMultiplier, "headshot multiplier");
        validateNonNegative(pierce, "pierce");
        validateNonNegative(aimTimeSeconds, "aim time");
        validateNonNegative(drawTimeSeconds, "draw time");
        validateNonNegative(weight, "weight");
        validateNonNegative(aimedInaccuracy, "aimed inaccuracy");
        validateNonNegative(recoilMagnitude, "recoil magnitude");
        validateFinite(movementSpeedWhileAiming, "aim movement speed");
        validateNonNegative(fireModeCount, "fire-mode count");
        validateNonNegative(attachmentTypeCount, "attachment-type count");
        validateNonNegative(boltActionSeconds, "bolt-action seconds");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(
                    "Weapon mechanical evidence " + field + " is invalid");
        }
        return value;
    }

    private static void validatePositive(Double value, String field) {
        if (value != null && (!Double.isFinite(value) || value <= 0.0)) {
            throw new IllegalArgumentException(
                    "Weapon mechanical evidence " + field + " must be finite and positive");
        }
    }

    private static void validateNonNegative(Double value, String field) {
        if (value != null && (!Double.isFinite(value) || value < 0.0)) {
            throw new IllegalArgumentException(
                    "Weapon mechanical evidence " + field + " must be finite and non-negative");
        }
    }

    private static void validateFinite(Double value, String field) {
        if (value != null && !Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "Weapon mechanical evidence " + field + " must be finite");
        }
    }

    private static void validateNonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(
                    "Weapon mechanical evidence " + field + " must be non-negative");
        }
    }
}
