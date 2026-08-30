package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalDouble;

/** Pure derivation of comparable mechanical values from normalized weapon evidence. */
public final class WeaponMechanicalMetrics {
    private final Map<MechanicalMetric, Double> values;

    private WeaponMechanicalMetrics(Map<MechanicalMetric, Double> values) {
        EnumMap<MechanicalMetric, Double> copy = new EnumMap<>(MechanicalMetric.class);
        copy.putAll(values);
        if (copy.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null
                        || entry.getValue() == null
                        || !Double.isFinite(entry.getValue()))) {
            throw new IllegalArgumentException("Weapon mechanical metrics are invalid");
        }
        this.values = Collections.unmodifiableMap(copy);
    }

    public static WeaponMechanicalMetrics derive(WeaponStatEvidence evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("Weapon mechanical evidence cannot be null");
        }
        EnumMap<MechanicalMetric, Double> values = new EnumMap<>(MechanicalMetric.class);
        Double effectiveDamage = addAvailable(
                evidence.baseDamage(), evidence.explosionDamage());
        put(values, MechanicalMetric.EFFECTIVE_DAMAGE, effectiveDamage);
        put(values, MechanicalMetric.HEADSHOT_MULTIPLIER, evidence.headshotMultiplier());
        put(values, MechanicalMetric.SUSTAINED_DPS, sustainedDps(evidence, effectiveDamage));
        put(values, MechanicalMetric.EFFECTIVE_RANGE, evidence.effectiveRange());
        put(values, MechanicalMetric.ARMOR_EFFECTIVENESS, armorEffectiveness(evidence));
        put(values, MechanicalMetric.PROJECTILE_SPEED, evidence.projectileSpeed());
        put(values, MechanicalMetric.AIMED_INACCURACY, evidence.aimedInaccuracy());
        put(values, MechanicalMetric.RECOIL_MAGNITUDE, evidence.recoilMagnitude());
        put(values, MechanicalMetric.MAGAZINE_CAPACITY, asDouble(evidence.magazineCapacity()));
        put(values, MechanicalMetric.RELOAD_SECONDS, evidence.reloadSeconds());
        put(values, MechanicalMetric.AIM_TIME, evidence.aimTimeSeconds());
        put(values, MechanicalMetric.DRAW_TIME, evidence.drawTimeSeconds());
        put(values, MechanicalMetric.WEIGHT, evidence.weight());
        put(values, MechanicalMetric.AIM_MOVEMENT, evidence.movementSpeedWhileAiming());
        put(values, MechanicalMetric.FIRE_MODE_COUNT, asDouble(evidence.fireModeCount()));
        put(values, MechanicalMetric.ATTACHMENT_TYPE_COUNT,
                asDouble(evidence.attachmentTypeCount()));
        return new WeaponMechanicalMetrics(values);
    }

    public OptionalDouble value(MechanicalMetric metric) {
        if (metric == null) {
            throw new IllegalArgumentException("Weapon mechanical metric cannot be null");
        }
        Double value = values.get(metric);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    public Map<MechanicalMetric, Double> observedValues() {
        return values;
    }

    private static Double sustainedDps(WeaponStatEvidence evidence, Double damage) {
        if (damage == null || evidence.roundsPerMinute() == null) {
            return null;
        }
        double shotInterval = 60.0 / evidence.roundsPerMinute();
        if (evidence.boltActionSeconds() != null) {
            shotInterval = Math.max(shotInterval, evidence.boltActionSeconds());
        }
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

    private static Double armorEffectiveness(WeaponStatEvidence evidence) {
        if (evidence.armorIgnore() == null && evidence.pierce() == null) {
            return null;
        }
        return (evidence.armorIgnore() == null ? 0.0 : evidence.armorIgnore() * 100.0)
                + (evidence.pierce() == null ? 0.0 : evidence.pierce() * 10.0);
    }

    private static void put(
            EnumMap<MechanicalMetric, Double> values,
            MechanicalMetric metric,
            Double value) {
        if (value != null) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Derived weapon mechanical metric is not finite: "
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
}
