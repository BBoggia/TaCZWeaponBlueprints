package com.gamergaming.taczweaponblueprints.research.tree.automatic;

/**
 * Normalized capability evidence for one supported firing mode. Values already
 * include TaCZ's additive {@code fire_mode_adjust} modifiers.
 */
public record WeaponFireModeEvidence(
        String mode,
        Double impactDamage,
        Double roundsPerMinute,
        Integer shotsPerTrigger,
        Double intraBurstRoundsPerMinute,
        Double triggerIntervalSeconds,
        Double initialChargeSeconds,
        Double repeatChargeSeconds,
        boolean chargeDuringCooldown,
        Double projectileSpeed,
        Double armorIgnore,
        Double headshotMultiplier,
        Double aimedInaccuracy) {
    public WeaponFireModeEvidence {
        if (mode == null || mode.isBlank() || !mode.equals(mode.trim())) {
            throw new IllegalArgumentException("Weapon fire-mode name is invalid");
        }
        validateNonNegative(impactDamage, "impact damage");
        validatePositive(roundsPerMinute, "rounds per minute");
        validatePositive(shotsPerTrigger, "shots per trigger");
        validatePositive(intraBurstRoundsPerMinute, "intra-burst rounds per minute");
        validateNonNegative(triggerIntervalSeconds, "trigger interval");
        validateNonNegative(initialChargeSeconds, "initial charge time");
        validateNonNegative(repeatChargeSeconds, "repeat charge time");
        validateNonNegative(projectileSpeed, "projectile speed");
        validateNonNegative(armorIgnore, "armor ignore");
        validateNonNegative(headshotMultiplier, "headshot multiplier");
        validateNonNegative(aimedInaccuracy, "aimed inaccuracy");
        if (shotsPerTrigger != null && shotsPerTrigger > 1
                && (intraBurstRoundsPerMinute == null || triggerIntervalSeconds == null)) {
            throw new IllegalArgumentException(
                    "Multi-shot fire modes require burst cadence evidence");
        }
    }

    public boolean burst() {
        return "burst".equalsIgnoreCase(mode) && triggerIntervalSeconds != null;
    }

    private static void validatePositive(Double value, String field) {
        if (value != null && (!Double.isFinite(value) || value <= 0.0)) {
            throw new IllegalArgumentException(
                    "Weapon fire-mode " + field + " must be finite and positive");
        }
    }

    private static void validatePositive(Integer value, String field) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(
                    "Weapon fire-mode " + field + " must be positive");
        }
    }

    private static void validateNonNegative(Double value, String field) {
        if (value != null && (!Double.isFinite(value) || value < 0.0)) {
            throw new IllegalArgumentException(
                    "Weapon fire-mode " + field + " must be finite and non-negative");
        }
    }
}
