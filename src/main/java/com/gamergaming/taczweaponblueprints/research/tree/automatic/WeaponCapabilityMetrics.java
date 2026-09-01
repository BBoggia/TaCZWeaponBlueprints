package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalDouble;

/** Pure v3 derivation. TaCZ lookup remains confined to the runtime adapter. */
public final class WeaponCapabilityMetrics {
    private final Map<CapabilityMetric, Double> values;

    private WeaponCapabilityMetrics(Map<CapabilityMetric, Double> values) {
        EnumMap<CapabilityMetric, Double> copy = new EnumMap<>(CapabilityMetric.class);
        copy.putAll(values);
        if (copy.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || entry.getValue() == null || !Double.isFinite(entry.getValue()))) {
            throw new IllegalArgumentException("Weapon capability metrics are invalid");
        }
        this.values = Collections.unmodifiableMap(copy);
    }

    public static WeaponCapabilityMetrics derive(WeaponStatEvidence evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("Weapon capability evidence cannot be null");
        }
        EnumMap<CapabilityMetric, Double> values = new EnumMap<>(CapabilityMetric.class);
        Double impact = strongestModeValue(
                evidence, WeaponFireModeEvidence::impactDamage,
                addAvailable(evidence.baseDamage(), evidence.explosionDamage()), false);
        put(values, CapabilityMetric.IMPACT_DAMAGE, impact);
        put(values, CapabilityMetric.SUSTAINED_DPS, sustainedDps(evidence, impact));
        put(values, CapabilityMetric.DAMAGE_RETENTION, evidence.damageRetention());
        put(values, CapabilityMetric.HEADSHOT_MULTIPLIER, strongestModeValue(
                evidence, WeaponFireModeEvidence::headshotMultiplier,
                evidence.headshotMultiplier(), false));
        put(values, CapabilityMetric.EFFECTIVE_RANGE, evidence.effectiveRange());
        put(values, CapabilityMetric.ARMOR_IGNORE, strongestModeValue(
                evidence, WeaponFireModeEvidence::armorIgnore,
                evidence.armorIgnore(), false));
        put(values, CapabilityMetric.TARGET_PENETRATION, asDouble(evidence.pierce()));
        put(values, CapabilityMetric.PROJECTILE_SPEED, strongestModeValue(
                evidence, WeaponFireModeEvidence::projectileSpeed,
                evidence.projectileSpeed(), false));
        put(values, CapabilityMetric.PROJECTILE_GRAVITY, evidence.projectileGravity());
        put(values, CapabilityMetric.AIMED_INACCURACY, aimedInaccuracy(evidence));
        put(values, CapabilityMetric.RECOIL_MAGNITUDE, evidence.recoilMagnitude());
        if (evidence.explosive()) {
            put(values, CapabilityMetric.EXPLOSION_DAMAGE, evidence.explosionDamage());
            put(values, CapabilityMetric.EXPLOSION_RADIUS, evidence.explosionRadius());
            put(values, CapabilityMetric.CONTROL_EFFECTS, controlEffects(evidence));
        } else if (evidence.projectileIgnitesEntities()) {
            put(values, CapabilityMetric.CONTROL_EFFECTS, controlEffects(evidence));
        }
        put(values, CapabilityMetric.MAGAZINE_CAPACITY,
                asDouble(evidence.magazineCapacity()));
        put(values, CapabilityMetric.EMPTY_RELOAD_SECONDS, evidence.reloadSeconds());
        put(values, CapabilityMetric.TACTICAL_RELOAD_SECONDS,
                evidence.tacticalReloadSeconds());
        put(values, CapabilityMetric.AIM_TIME, evidence.aimTimeSeconds());
        put(values, CapabilityMetric.DRAW_TIME, evidence.drawTimeSeconds());
        put(values, CapabilityMetric.WEIGHT, evidence.weight());
        put(values, CapabilityMetric.AIM_MOVEMENT, evidence.movementSpeedWhileAiming());
        put(values, CapabilityMetric.FIRE_MODE_COUNT, asDouble(evidence.fireModeCount()));
        put(values, CapabilityMetric.ATTACHMENT_TYPE_COUNT,
                asDouble(evidence.attachmentTypeCount()));
        put(values, CapabilityMetric.PROJECTILE_COUNT, asDouble(evidence.projectileCount()));
        put(values, CapabilityMetric.CHARGE_SECONDS, strongestModeValue(
                evidence, WeaponFireModeEvidence::initialChargeSeconds,
                evidence.chargeSeconds(), true));
        return new WeaponCapabilityMetrics(values);
    }

    public OptionalDouble value(CapabilityMetric metric) {
        if (metric == null) {
            throw new IllegalArgumentException("Weapon capability metric cannot be null");
        }
        Double value = values.get(metric);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    public Map<CapabilityMetric, Double> observedValues() {
        return values;
    }

    private static Double sustainedDps(WeaponStatEvidence evidence, Double damage) {
        if (!evidence.fireModes().isEmpty()) {
            Double strongest = null;
            for (WeaponFireModeEvidence mode : evidence.fireModes()) {
                Double modeDps = sustainedDps(evidence, mode);
                if (modeDps != null) {
                    strongest = strongest == null ? modeDps : Math.max(strongest, modeDps);
                }
            }
            return strongest;
        }
        if (damage == null || evidence.roundsPerMinute() == null) {
            return null;
        }
        double shotInterval = 60.0 / evidence.roundsPerMinute();
        if (evidence.boltActionSeconds() != null) {
            shotInterval = Math.max(shotInterval, evidence.boltActionSeconds());
        }
        if (evidence.chargeSeconds() != null) {
            shotInterval = Math.max(shotInterval, evidence.chargeSeconds());
        }
        // Legacy evidence has no trigger interval or mode ownership. Do not
        // apply an unprovable burst transform to the base firing cadence.
        if (evidence.magazineCapacity() == null || evidence.magazineCapacity() <= 0
                || evidence.reloadSeconds() == null
                || "inventory".equals(evidence.reloadType())) {
            return damage / shotInterval;
        }
        double firingSeconds = Math.max(0, evidence.magazineCapacity() - 1) * shotInterval;
        double cycleSeconds = firingSeconds + evidence.reloadSeconds();
        return cycleSeconds <= 0.0
                ? null
                : damage * evidence.magazineCapacity() / cycleSeconds;
    }

    private static Double sustainedDps(
            WeaponStatEvidence evidence,
            WeaponFireModeEvidence mode) {
        if (mode.impactDamage() == null) {
            return null;
        }
        Double shotInterval = shotInterval(evidence, mode);
        if (shotInterval == null || shotInterval <= 0.0) {
            return null;
        }
        double ordinaryDps;
        if (evidence.magazineCapacity() == null || evidence.magazineCapacity() <= 0
                || evidence.reloadSeconds() == null
                || "inventory".equals(evidence.reloadType())) {
            ordinaryDps = mode.impactDamage() / shotInterval;
        } else {
            double firingSeconds = Math.max(0, evidence.magazineCapacity() - 1)
                    * shotInterval;
            double cycleSeconds = firingSeconds + evidence.reloadSeconds();
            if (cycleSeconds <= 0.0) {
                return null;
            }
            ordinaryDps = mode.impactDamage() * evidence.magazineCapacity()
                    / cycleSeconds;
        }
        if (evidence.heatCapacityShots() == null
                || evidence.heatRecoverySeconds() == null) {
            return ordinaryDps;
        }
        double shots = Math.max(1.0, evidence.heatCapacityShots());
        double firingSeconds = Math.max(0.0, shots - 1.0) * shotInterval;
        double heatCycle = firingSeconds + evidence.heatRecoverySeconds();
        if (heatCycle <= 0.0) {
            return ordinaryDps;
        }
        double heatLimitedDps = mode.impactDamage() * shots / heatCycle;
        return Math.min(ordinaryDps, heatLimitedDps);
    }

    private static Double shotInterval(
            WeaponStatEvidence evidence,
            WeaponFireModeEvidence mode) {
        final double base;
        if (mode.burst()) {
            if (mode.shotsPerTrigger() == null || mode.intraBurstRoundsPerMinute() == null
                    || mode.triggerIntervalSeconds() == null) {
                return null;
            }
            int shots = Math.max(1, mode.shotsPerTrigger());
            double burstSpan = Math.max(0, shots - 1)
                    * 60.0 / mode.intraBurstRoundsPerMinute();
            base = Math.max(mode.triggerIntervalSeconds(), burstSpan) / shots;
        } else if (mode.roundsPerMinute() != null) {
            double multiplier = averageHeatRpmMultiplier(evidence);
            base = 60.0 / (mode.roundsPerMinute() * multiplier);
        } else {
            return null;
        }
        double interval = base;
        if (evidence.boltActionSeconds() != null) {
            interval = Math.max(interval, evidence.boltActionSeconds());
        }
        if (mode.repeatChargeSeconds() != null) {
            interval = mode.chargeDuringCooldown()
                    ? Math.max(interval, mode.repeatChargeSeconds())
                    // TaCZ permits charging during the final sub-tick (<50 ms)
                    // remainder even when overlap is otherwise disabled.
                    : interval + Math.max(0.0, mode.repeatChargeSeconds() - 0.05);
        }
        return interval;
    }

    private static double averageHeatRpmMultiplier(WeaponStatEvidence evidence) {
        if (evidence.heatMinimumRpmMultiplier() == null
                || evidence.heatMaximumRpmMultiplier() == null) {
            return 1.0;
        }
        // Average cadence is the reciprocal mean of per-shot intervals while heat
        // moves linearly from zero to its cap.
        double inverseTotal = 0.0;
        int samples = 64;
        for (int index = 0; index < samples; index++) {
            double fraction = index / (double) (samples - 1);
            double multiplier = evidence.heatMinimumRpmMultiplier()
                    + fraction * (evidence.heatMaximumRpmMultiplier()
                            - evidence.heatMinimumRpmMultiplier());
            inverseTotal += 1.0 / multiplier;
        }
        return samples / inverseTotal;
    }

    private static Double aimedInaccuracy(WeaponStatEvidence evidence) {
        Double base = strongestModeValue(
                evidence,
                WeaponFireModeEvidence::aimedInaccuracy,
                evidence.aimedInaccuracy(),
                true);
        if (base == null) {
            return null;
        }
        if (evidence.heatMinimumInaccuracyMultiplier() == null
                || evidence.heatMaximumInaccuracyMultiplier() == null) {
            return base;
        }
        double averageMultiplier = (evidence.heatMinimumInaccuracyMultiplier()
                + evidence.heatMaximumInaccuracyMultiplier()) / 2.0;
        return base * averageMultiplier;
    }

    private static double controlEffects(WeaponStatEvidence evidence) {
        double result = evidence.explosionKnockback() ? 1.0 : 0.0;
        if (evidence.projectileIgnitesEntities()) {
            double duration = evidence.igniteEntitySeconds() == null
                    ? 0.0
                    : Math.min(10.0, evidence.igniteEntitySeconds()) / 10.0;
            result += 1.0 + duration;
        }
        return result;
    }

    private static void put(
            EnumMap<CapabilityMetric, Double> values,
            CapabilityMetric metric,
            Double value) {
        if (value != null) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Derived weapon capability metric is not finite: "
                                + metric.serializedName());
            }
            values.put(metric, value);
        }
    }

    private static Double addAvailable(Double first, Double second) {
        if (first == null && second == null) {
            return null;
        }
        return (first == null ? 0.0 : first) + (second == null ? 0.0 : second);
    }

    private static Double asDouble(Integer value) {
        return value == null ? null : value.doubleValue();
    }

    private static Double strongestModeValue(
            WeaponStatEvidence evidence,
            java.util.function.Function<WeaponFireModeEvidence, Double> getter,
            Double fallback,
            boolean lowerIsBetter) {
        Double strongest = null;
        for (WeaponFireModeEvidence mode : evidence.fireModes()) {
            Double value = getter.apply(mode);
            if (value != null) {
                strongest = strongest == null
                        ? value
                        : (lowerIsBetter ? Math.min(strongest, value)
                                : Math.max(strongest, value));
            }
        }
        return strongest == null ? fallback : strongest;
    }
}
