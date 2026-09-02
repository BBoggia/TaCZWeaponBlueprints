package com.gamergaming.taczweaponblueprints.research.tree.authoring;

import java.util.List;

import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponStatEvidence;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponFireModeEvidence;

/** Normalized, non-authoritative evidence extracted from one recipe-backed TaCZ gun. */
public record TaCZGunStats(
        String blueprintId,
        String gunType,
        String dataId,
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
        String scriptId,
        String reloadType,
        boolean explosive,
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
        String sourceHash,
        List<String> missingFields) {

    /** Backward-compatible construction path from before mode-aware extraction. */
    public TaCZGunStats(
            String blueprintId, String gunType, String dataId, Double baseDamage,
            Double explosionDamage, Double roundsPerMinute, Integer magazineCapacity,
            Double reloadSeconds, Double projectileSpeed, Double effectiveRange,
            Double armorIgnore, Double headshotMultiplier, Integer pierce,
            Double aimTimeSeconds, Double drawTimeSeconds, Double weight,
            Double aimedInaccuracy, Double recoilMagnitude,
            Double movementSpeedWhileAiming, Integer fireModeCount,
            Integer attachmentTypeCount, Double boltActionSeconds, String scriptId,
            String reloadType, boolean explosive, Integer projectileCount,
            Double damageRetention, Double explosionRadius,
            Double explosionDelaySeconds, boolean explosionKnockback,
            boolean projectileIgnitesEntities, Double igniteEntitySeconds,
            Double projectileGravity, Double tacticalReloadSeconds, Integer burstCount,
            Double burstRoundsPerMinute, Double heatCapacityShots, Double chargeSeconds,
            String sourceHash, List<String> missingFields) {
        this(
                blueprintId, gunType, dataId, baseDamage, explosionDamage,
                roundsPerMinute, magazineCapacity, reloadSeconds, projectileSpeed,
                effectiveRange, armorIgnore, headshotMultiplier, pierce,
                aimTimeSeconds, drawTimeSeconds, weight, aimedInaccuracy,
                recoilMagnitude, movementSpeedWhileAiming, fireModeCount,
                attachmentTypeCount, boltActionSeconds, scriptId, reloadType,
                explosive, projectileCount, damageRetention, explosionRadius,
                explosionDelaySeconds, explosionKnockback, projectileIgnitesEntities,
                igniteEntitySeconds, projectileGravity, tacticalReloadSeconds,
                burstCount, burstRoundsPerMinute, heatCapacityShots, chargeSeconds,
                List.of(), null, null, null, null, null, sourceHash, missingFields);
    }

    /** Source-compatible construction path for the reviewed v2 authoring tests. */
    public TaCZGunStats(
            String blueprintId,
            String gunType,
            String dataId,
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
            String scriptId,
            String reloadType,
            boolean explosive,
            String sourceHash,
            List<String> missingFields) {
        this(
                blueprintId, gunType, dataId, baseDamage, explosionDamage,
                roundsPerMinute, magazineCapacity, reloadSeconds, projectileSpeed,
                effectiveRange, armorIgnore, headshotMultiplier, pierce,
                aimTimeSeconds, drawTimeSeconds, weight, aimedInaccuracy,
                recoilMagnitude, movementSpeedWhileAiming, fireModeCount,
                attachmentTypeCount, boltActionSeconds, scriptId, reloadType,
                explosive, 1, null, null, null, false, false, null, null, null,
                1, null, null, 0.0, List.of(), null, null, null, null, null,
                sourceHash, missingFields);
    }

    public TaCZGunStats {
        blueprintId = requireText(blueprintId, "blueprintId");
        gunType = requireText(gunType, "gunType");
        dataId = requireText(dataId, "dataId");
        reloadType = requireText(reloadType, "reloadType");
        sourceHash = requireText(sourceHash, "sourceHash");
        if (scriptId != null && scriptId.isBlank()) {
            throw new IllegalArgumentException("scriptId cannot be blank");
        }
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        if (missingFields.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("missingFields cannot contain blank values");
        }
        validateNonNegative(baseDamage, "baseDamage");
        validateNonNegative(explosionDamage, "explosionDamage");
        validatePositive(roundsPerMinute, "roundsPerMinute");
        validateNonNegative(magazineCapacity, "magazineCapacity");
        validateNonNegative(reloadSeconds, "reloadSeconds");
        validateNonNegative(projectileSpeed, "projectileSpeed");
        validateNonNegative(effectiveRange, "effectiveRange");
        validateNonNegative(armorIgnore, "armorIgnore");
        validateNonNegative(headshotMultiplier, "headshotMultiplier");
        validateNonNegative(pierce, "pierce");
        validateNonNegative(aimTimeSeconds, "aimTimeSeconds");
        validateNonNegative(drawTimeSeconds, "drawTimeSeconds");
        validateNonNegative(weight, "weight");
        validateNonNegative(aimedInaccuracy, "aimedInaccuracy");
        validateNonNegative(recoilMagnitude, "recoilMagnitude");
        validateNonNegative(fireModeCount, "fireModeCount");
        validateNonNegative(attachmentTypeCount, "attachmentTypeCount");
        validateNonNegative(boltActionSeconds, "boltActionSeconds");
        validatePositive(projectileCount, "projectileCount");
        validateUnitInterval(damageRetention, "damageRetention");
        validateNonNegative(explosionRadius, "explosionRadius");
        validateNonNegative(explosionDelaySeconds, "explosionDelaySeconds");
        validateNonNegative(igniteEntitySeconds, "igniteEntitySeconds");
        validateNonNegative(projectileGravity, "projectileGravity");
        validateNonNegative(tacticalReloadSeconds, "tacticalReloadSeconds");
        validatePositive(burstCount, "burstCount");
        validatePositive(burstRoundsPerMinute, "burstRoundsPerMinute");
        validatePositive(heatCapacityShots, "heatCapacityShots");
        validateNonNegative(chargeSeconds, "chargeSeconds");
        fireModes = fireModes == null ? List.of() : List.copyOf(fireModes);
        if (fireModes.stream().anyMatch(java.util.Objects::isNull)
                || fireModes.stream().map(WeaponFireModeEvidence::mode).distinct().count()
                        != fireModes.size()) {
            throw new IllegalArgumentException("fireModes are invalid");
        }
        validateNonNegative(heatRecoverySeconds, "heatRecoverySeconds");
        validatePositive(heatMinimumRpmMultiplier, "heatMinimumRpmMultiplier");
        validatePositive(heatMaximumRpmMultiplier, "heatMaximumRpmMultiplier");
        validatePositive(heatMinimumInaccuracyMultiplier,
                "heatMinimumInaccuracyMultiplier");
        validatePositive(heatMaximumInaccuracyMultiplier,
                "heatMaximumInaccuracyMultiplier");
        if (movementSpeedWhileAiming != null && !Double.isFinite(movementSpeedWhileAiming)) {
            throw new IllegalArgumentException("movementSpeedWhileAiming must be finite");
        }
    }

    /** Bridges offline pack extraction into the production-safe mechanical scoring core. */
    public WeaponStatEvidence mechanicalEvidence() {
        return new WeaponStatEvidence(
                blueprintId,
                gunType,
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
                scriptId != null,
                projectileCount,
                damageRetention,
                explosionRadius,
                explosionDelaySeconds,
                explosionKnockback,
                projectileIgnitesEntities,
                igniteEntitySeconds,
                projectileGravity,
                tacticalReloadSeconds,
                burstCount,
                burstRoundsPerMinute,
                heatCapacityShots,
                chargeSeconds,
                fireModes,
                heatRecoverySeconds,
                heatMinimumRpmMultiplier,
                heatMaximumRpmMultiplier,
                heatMinimumInaccuracyMultiplier,
                heatMaximumInaccuracyMultiplier,
                missingFields);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }

    private static void validatePositive(Double value, String field) {
        if (value != null && (!Double.isFinite(value) || value <= 0.0)) {
            throw new IllegalArgumentException(field + " must be finite and positive");
        }
    }

    private static void validatePositive(Integer value, String field) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void validateUnitInterval(Double value, String field) {
        if (value != null && (!Double.isFinite(value) || value < 0.0 || value > 1.0)) {
            throw new IllegalArgumentException(field + " must be between zero and one");
        }
    }

    private static void validateNonNegative(Double value, String field) {
        if (value != null && (!Double.isFinite(value) || value < 0.0)) {
            throw new IllegalArgumentException(field + " must be finite and non-negative");
        }
    }

    private static void validateNonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }
}
