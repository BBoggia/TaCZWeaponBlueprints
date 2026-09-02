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
        Integer projectileCount,
        Double damageRetention,
        Double explosionRadius,
        Double explosionDelaySeconds,
        boolean explosionKnockback,
        boolean projectileIgnitesEntities,
        Double igniteEntitySeconds,
        Double projectileGravity,
        Double tacticalReloadSeconds,
        Integer burstCount,
        Double burstRoundsPerMinute,
        Double heatCapacityShots,
        Double chargeSeconds,
        List<WeaponFireModeEvidence> fireModes,
        Double heatRecoverySeconds,
        Double heatMinimumRpmMultiplier,
        Double heatMaximumRpmMultiplier,
        Double heatMinimumInaccuracyMultiplier,
        Double heatMaximumInaccuracyMultiplier,
        List<String> warnings) {
    /** Backward-compatible v3 construction path from before mode-aware evidence. */
    public WeaponStatEvidence(
            String blueprintId, String archetype, Double baseDamage,
            Double explosionDamage, Double roundsPerMinute, Integer magazineCapacity,
            Double reloadSeconds, Double projectileSpeed, Double effectiveRange,
            Double armorIgnore, Double headshotMultiplier, Integer pierce,
            Double aimTimeSeconds, Double drawTimeSeconds, Double weight,
            Double aimedInaccuracy, Double recoilMagnitude,
            Double movementSpeedWhileAiming, Integer fireModeCount,
            Integer attachmentTypeCount, Double boltActionSeconds, String reloadType,
            boolean explosive, boolean scriptControlled, Integer projectileCount,
            Double damageRetention, Double explosionRadius,
            Double explosionDelaySeconds, boolean explosionKnockback,
            boolean projectileIgnitesEntities, Double igniteEntitySeconds,
            Double projectileGravity, Double tacticalReloadSeconds, Integer burstCount,
            Double burstRoundsPerMinute, Double heatCapacityShots, Double chargeSeconds,
            List<String> warnings) {
        this(
                blueprintId, archetype, baseDamage, explosionDamage, roundsPerMinute,
                magazineCapacity, reloadSeconds, projectileSpeed, effectiveRange,
                armorIgnore, headshotMultiplier, pierce, aimTimeSeconds, drawTimeSeconds,
                weight, aimedInaccuracy, recoilMagnitude, movementSpeedWhileAiming,
                fireModeCount, attachmentTypeCount, boltActionSeconds, reloadType,
                explosive, scriptControlled, projectileCount, damageRetention,
                explosionRadius, explosionDelaySeconds, explosionKnockback,
                projectileIgnitesEntities, igniteEntitySeconds, projectileGravity,
                tacticalReloadSeconds, burstCount, burstRoundsPerMinute,
                heatCapacityShots, chargeSeconds, List.of(), null, null, null, null,
                null, warnings);
    }

    /**
     * Source-compatible v2 construction path. New capability evidence is deliberately
     * absent rather than guessed, except for the universal one-projectile/one-shot
     * defaults.
     */
    public WeaponStatEvidence(
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
        this(
                blueprintId,
                archetype,
                baseDamage,
                explosionDamage,
                roundsPerMinute,
                magazineCapacity,
                reloadSeconds,
                projectileSpeed,
                effectiveRange,
                armorIgnore,
                headshotMultiplier,
                pierce,
                aimTimeSeconds,
                drawTimeSeconds,
                weight,
                aimedInaccuracy,
                recoilMagnitude,
                movementSpeedWhileAiming,
                fireModeCount,
                attachmentTypeCount,
                boltActionSeconds,
                reloadType,
                explosive,
                scriptControlled,
                1,
                null,
                null,
                null,
                false,
                false,
                null,
                null,
                null,
                1,
                null,
                null,
                0.0,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                warnings);
    }

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
        validatePositive(projectileCount, "projectile count");
        validateUnitInterval(damageRetention, "damage retention");
        validateNonNegative(explosionRadius, "explosion radius");
        validateNonNegative(explosionDelaySeconds, "explosion delay");
        validateNonNegative(igniteEntitySeconds, "ignite duration");
        validateNonNegative(projectileGravity, "projectile gravity");
        validateNonNegative(tacticalReloadSeconds, "tactical reload seconds");
        validatePositive(burstCount, "burst count");
        validatePositive(burstRoundsPerMinute, "burst rounds per minute");
        validatePositive(heatCapacityShots, "heat capacity shots");
        validateNonNegative(chargeSeconds, "charge seconds");
        fireModes = fireModes == null ? List.of() : List.copyOf(fireModes);
        if (fireModes.stream().anyMatch(java.util.Objects::isNull)
                || fireModes.stream().map(WeaponFireModeEvidence::mode).distinct().count()
                        != fireModes.size()) {
            throw new IllegalArgumentException(
                    "Weapon mechanical evidence fire modes are invalid");
        }
        validateNonNegative(heatRecoverySeconds, "heat recovery seconds");
        validatePositive(heatMinimumRpmMultiplier, "minimum heat RPM multiplier");
        validatePositive(heatMaximumRpmMultiplier, "maximum heat RPM multiplier");
        validatePositive(heatMinimumInaccuracyMultiplier,
                "minimum heat inaccuracy multiplier");
        validatePositive(heatMaximumInaccuracyMultiplier,
                "maximum heat inaccuracy multiplier");
        boolean anyHeatDetail = heatRecoverySeconds != null
                || heatMinimumRpmMultiplier != null || heatMaximumRpmMultiplier != null
                || heatMinimumInaccuracyMultiplier != null
                || heatMaximumInaccuracyMultiplier != null;
        if (anyHeatDetail && heatCapacityShots == null) {
            throw new IllegalArgumentException(
                    "Weapon heat details require heat-capacity evidence");
        }
        if (!explosive && (explosionRadius != null || explosionDelaySeconds != null
                || explosionKnockback)) {
            throw new IllegalArgumentException(
                    "Non-explosive weapon evidence cannot declare explosion capabilities");
        }
        if (!projectileIgnitesEntities && igniteEntitySeconds != null
                && igniteEntitySeconds > 0.0) {
            throw new IllegalArgumentException(
                    "Non-igniting weapon evidence cannot declare an ignite duration");
        }
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

    private static void validatePositive(Integer value, String field) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(
                    "Weapon mechanical evidence " + field + " must be positive");
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

    private static void validateUnitInterval(Double value, String field) {
        if (value != null && (!Double.isFinite(value) || value < 0.0 || value > 1.0)) {
            throw new IllegalArgumentException(
                    "Weapon mechanical evidence " + field + " must be between zero and one");
        }
    }

    private static void validateNonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(
                    "Weapon mechanical evidence " + field + " must be non-negative");
        }
    }
}
