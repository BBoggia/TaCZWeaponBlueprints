package com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalReferenceCatalog;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponFireModeEvidence;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponStatEvidence;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.BurstData;
import com.tacz.guns.resource.pojo.data.gun.ChargeData;
import com.tacz.guns.resource.pojo.data.gun.ExplosionData;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunRecoil;
import com.tacz.guns.resource.pojo.data.gun.GunRecoilKeyFrame;
import com.tacz.guns.resource.pojo.data.gun.GunReloadData;
import com.tacz.guns.resource.pojo.data.gun.GunReloadTime;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import com.tacz.guns.resource.pojo.data.gun.GunFireModeAdjustData;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.ChargeType;

import net.minecraft.resources.ResourceLocation;

/**
 * Bounded server-side bridge from TaCZ's validated common data to normalized evidence.
 * It has no placement, prerequisite, config, networking, or player-state responsibility.
 */
public final class TaCZRuntimeWeaponEvidenceAdapter {
    public Capture capture(
            Map<ResourceLocation, BlueprintData> recipeBackedCatalog,
            CommonAssetsManager assetsManager) {
        if (assetsManager == null) {
            throw new IllegalArgumentException("TaCZ common assets manager cannot be null");
        }
        return capture(recipeBackedCatalog, id -> runtimeGun(assetsManager.getGunIndex(id)));
    }

    Capture capture(
            Map<ResourceLocation, BlueprintData> recipeBackedCatalog,
            Function<ResourceLocation, RuntimeGun> lookup) {
        if (recipeBackedCatalog == null || lookup == null
                || recipeBackedCatalog.entrySet().stream().anyMatch(entry ->
                        entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException("TaCZ runtime evidence inputs are invalid");
        }
        List<ResourceLocation> candidateIds = recipeBackedCatalog.entrySet().stream()
                .filter(entry -> entry.getValue().getKind() == BlueprintKind.GUN)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (candidateIds.size() > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS) {
            throw new IllegalArgumentException(
                    "TaCZ runtime evidence exceeds the weapon limit");
        }

        Map<String, WeaponStatEvidence> evidence = new LinkedHashMap<>();
        Map<String, String> rejected = new LinkedHashMap<>();
        for (ResourceLocation blueprintId : candidateIds) {
            try {
                RuntimeGun runtime = lookup.apply(blueprintId);
                if (runtime == null) {
                    rejected.put(blueprintId.toString(), "missing_tacz_gun_index");
                    continue;
                }
                evidence.put(
                        blueprintId.toString(),
                        normalize(blueprintId.toString(), runtime));
            } catch (RuntimeException exception) {
                rejected.put(
                        blueprintId.toString(),
                        "invalid_tacz_runtime_evidence:"
                                + exception.getClass().getSimpleName());
            }
        }
        return new Capture(candidateIds.size(), evidence, rejected);
    }

    WeaponStatEvidence normalize(String blueprintId, RuntimeGun runtime) {
        if (runtime == null) {
            throw new IllegalArgumentException("TaCZ runtime gun cannot be null");
        }
        GunData gun = runtime.data();
        BulletData bullet = gun.getBulletData();
        if (bullet == null) {
            throw new IllegalArgumentException("TaCZ runtime gun has no bullet data");
        }
        List<String> warnings = new ArrayList<>();
        ExtraDamage extraDamage = bullet.getExtraDamage();
        ExplosionData explosion = bullet.getExplosionData();
        boolean explosive = explosion != null && explosion.isExplode();
        ResourceLocation script = gun.getScript();
        if (script != null && !"tacz:xmag_reload_logic".equals(script.toString())) {
            warnings.add("scripted_behavior_requires_review:" + script);
        }

        Double boltAction = positiveOrNull(gun.getBoltActionTime());
        if (boltAction == null) {
            boltAction = positiveOrNull(number(gun.getScriptParam(), "bolt_time"));
        }
        BurstData burst = gun.getBurstData();
        boolean hasBurstMode = gun.getFireModeSet() != null
                && gun.getFireModeSet().contains(FireMode.BURST);
        Integer burstCount = hasBurstMode && burst != null && burst.getCount() > 0
                ? burst.getCount()
                : 1;
        Double burstRoundsPerMinute = hasBurstMode && burst != null
                ? positiveOrNull(burst.getBpm())
                : null;
        GunHeatData heat = gun.getHeatData();
        Double heatCapacityShots = heat == null
                ? null
                : positiveRatio(heat.getHeatMax(), heat.getHeatPerShot());
        boolean ignitesEntities = bullet.getIgnite() != null
                && bullet.getIgnite().isIgniteEntity();
        return new WeaponStatEvidence(
                blueprintId,
                runtime.archetype(),
                directDamage(bullet, extraDamage, warnings),
                explosive ? finite(explosion.getDamage()) : 0.0,
                finite(gun.getRoundsPerMinute()),
                gun.getAmmoAmount(),
                reloadSeconds(gun, script, warnings),
                finite(bullet.getSpeed()),
                effectiveRange(bullet, extraDamage, warnings),
                extraDamage == null ? null : finite(extraDamage.getArmorIgnore()),
                extraDamage == null ? null : finite(extraDamage.getHeadShotMultiplier()),
                bullet.getPierce(),
                finite(gun.getAimTime()),
                finite(gun.getDrawTime()),
                finite(gun.getWeight()),
                aimedInaccuracy(gun, warnings),
                recoilMagnitude(gun.getRecoil(), warnings),
                gun.getMoveSpeed() == null
                        ? missing(warnings, "movement_speed.aim")
                        : finite(gun.getMoveSpeed().getAimMultiplier()),
                gun.getFireModeSet() == null ? 0 : gun.getFireModeSet().size(),
                gun.getAllowAttachments() == null ? 0 : gun.getAllowAttachments().size(),
                boltAction,
                reloadType(gun.getReloadData()),
                explosive,
                script != null,
                Math.max(1, bullet.getBulletAmount()),
                damageRetention(extraDamage),
                explosive ? nonNegativeOrNull(explosion.getRadius()) : null,
                explosive ? nonNegativeOrNull(explosion.getDelay()) : null,
                explosive && explosion.isKnockback(),
                ignitesEntities,
                ignitesEntities ? nonNegativeOrNull(bullet.getIgniteEntityTime()) : null,
                nonNegativeOrNull(bullet.getGravity()),
                tacticalReloadSeconds(gun, script, warnings),
                burstCount,
                burstRoundsPerMinute,
                heatCapacityShots,
                chargeSeconds(gun),
                fireModes(gun, bullet, extraDamage, explosion, aimedInaccuracy(gun, warnings),
                        warnings),
                heatRecoverySeconds(heat),
                heat == null ? null : positiveOrNull(heat.getMinRpmMod()),
                heat == null ? null : positiveOrNull(heat.getMaxRpmMod()),
                heat == null ? null : positiveOrNull(heat.getMinInaccuracy()),
                heat == null ? null : positiveOrNull(heat.getMaxInaccuracy()),
                warnings);
    }

    /** Shared normalization entry point used by the offline reference generator. */
    public WeaponStatEvidence normalize(
            String blueprintId,
            String archetype,
            GunData gun) {
        return normalize(blueprintId, new RuntimeGun(archetype, gun));
    }

    private static RuntimeGun runtimeGun(CommonGunIndex index) {
        return index == null ? null : new RuntimeGun(index.getType(), index.getGunData());
    }

    private static Double effectiveRange(
            BulletData bullet,
            ExtraDamage extraDamage,
            List<String> warnings) {
        if (extraDamage != null && extraDamage.getDamageAdjust() != null) {
            Double maximum = null;
            for (ExtraDamage.DistanceDamagePair pair : extraDamage.getDamageAdjust()) {
                if (pair != null && Float.isFinite(pair.getDistance()) && pair.getDistance() >= 0) {
                    maximum = maximum == null
                            ? (double) pair.getDistance()
                            : Math.max(maximum, pair.getDistance());
                }
            }
            if (maximum != null) {
                return maximum;
            }
        }
        double speed = bullet.getSpeed();
        double life = bullet.getLifeSecond();
        if (Double.isFinite(speed) && speed >= 0
                && Double.isFinite(life) && life >= 0) {
            warnings.add("bullet.extra_damage.damage_adjust (used speed * life fallback)");
            return speed * life;
        }
        warnings.add("effective_range");
        return null;
    }

    /**
     * Mean retained damage across TaCZ's declared distance samples. A missing
     * distance curve means the base damage remains constant for the bullet's life.
     */
    private static Double damageRetention(ExtraDamage extraDamage) {
        if (extraDamage == null || extraDamage.getDamageAdjust() == null
                || extraDamage.getDamageAdjust().isEmpty()) {
            return 1.0;
        }
        double total = 0.0;
        double maximum = 0.0;
        int count = 0;
        for (ExtraDamage.DistanceDamagePair pair : extraDamage.getDamageAdjust()) {
            if (pair == null || !Float.isFinite(pair.getDamage()) || pair.getDamage() < 0) {
                continue;
            }
            total += pair.getDamage();
            maximum = Math.max(maximum, pair.getDamage());
            count++;
        }
        if (count == 0) {
            return null;
        }
        return maximum == 0.0 ? 1.0 : total / count / maximum;
    }

    /** TaCZ applies the first distance curve entry at point-blank range. */
    private static Double directDamage(
            BulletData bullet,
            ExtraDamage extraDamage,
            List<String> warnings) {
        if (extraDamage != null && extraDamage.getDamageAdjust() != null) {
            for (ExtraDamage.DistanceDamagePair pair : extraDamage.getDamageAdjust()) {
                if (pair != null && Float.isFinite(pair.getDamage())
                        && pair.getDamage() >= 0) {
                    return (double) pair.getDamage();
                }
            }
            warnings.add("bullet.extra_damage.damage_adjust (used base damage fallback)");
        }
        return finite(bullet.getDamageAmount());
    }

    private static Double reloadSeconds(
            GunData gun,
            ResourceLocation script,
            List<String> warnings) {
        GunReloadData reload = gun.getReloadData();
        if (reload == null) {
            warnings.add("reload.duration");
            return null;
        }
        if (script != null && isTubeReloadScript(script.toString())
                && gun.getAmmoAmount() > 0) {
            Double intro = firstPresent(
                    number(gun.getScriptParam(), "intro_empty"),
                    empty(reload.getFeed()));
            Double loop = firstPresent(
                    number(gun.getScriptParam(), "loop"),
                    empty(reload.getCooldown()));
            Double pairLoop = number(gun.getScriptParam(), "loop_2");
            Double ending = firstPresent(
                    number(gun.getScriptParam(), "ending"),
                    tactical(reload.getCooldown()));
            if (intro != null && loop != null && ending != null) {
                if ("tacz:m1014_gun_logic".equals(script.toString())
                        && pairLoop != null) {
                    return intro
                            + (gun.getAmmoAmount() / 2) * pairLoop
                            + (gun.getAmmoAmount() % 2) * loop
                            + ending;
                }
                return intro + gun.getAmmoAmount() * loop + ending;
            }
            warnings.add("scripted_full_reload_duration:" + script);
        }
        Double empty = empty(reload.getCooldown());
        Double tactical = tactical(reload.getCooldown());
        if (tactical != null || empty != null) {
            return empty != null ? empty : tactical;
        }
        empty = empty(reload.getFeed());
        tactical = tactical(reload.getFeed());
        if (tactical != null || empty != null) {
            warnings.add("reload.cooldown (used feed fallback)");
            return empty != null ? empty : tactical;
        }
        warnings.add("reload.duration");
        return null;
    }

    private static Double tacticalReloadSeconds(
            GunData gun,
            ResourceLocation script,
            List<String> warnings) {
        GunReloadData reload = gun.getReloadData();
        if (reload == null) {
            return null;
        }
        if (script != null && isTubeReloadScript(script.toString())) {
            warnings.add("scripted_tactical_reload_duration:" + script);
            return null;
        }
        Double tactical = tactical(reload.getCooldown());
        if (tactical != null) {
            return tactical;
        }
        tactical = tactical(reload.getFeed());
        if (tactical != null) {
            warnings.add("reload.cooldown.tactical (used feed fallback)");
        }
        return tactical;
    }

    private static Double chargeSeconds(GunData gun) {
        if (gun.getFireModeSet() == null || gun.getFireModeSet().isEmpty()) {
            return 0.0;
        }
        Double minimum = null;
        for (FireMode mode : gun.getFireModeSet()) {
            ChargeData charge = gun.getChargeData(mode);
            if (charge == null) {
                return 0.0;
            }
            double increase = charge.getIncreasePerTick();
            double threshold = chargeTarget(charge);
            if (!Double.isFinite(increase) || increase <= 0.0
                    || !Double.isFinite(threshold) || threshold < 0.0) {
                return null;
            }
            double seconds = threshold / increase / 20.0;
            minimum = minimum == null ? seconds : Math.min(minimum, seconds);
        }
        return minimum == null ? 0.0 : minimum;
    }

    private static List<WeaponFireModeEvidence> fireModes(
            GunData gun,
            BulletData bullet,
            ExtraDamage extraDamage,
            ExplosionData explosion,
            Double baseAimedInaccuracy,
            List<String> warnings) {
        if (gun.getFireModeSet() == null || gun.getFireModeSet().isEmpty()) {
            warnings.add("fire_mode (no normalized modes)");
            return List.of();
        }
        Double directDamage = directDamage(bullet, extraDamage, warnings);
        Double armorIgnore = extraDamage == null
                ? null
                : finite(extraDamage.getArmorIgnore());
        Double headshot = extraDamage == null
                ? null
                : finite(extraDamage.getHeadShotMultiplier());
        Double speed = finite(bullet.getSpeed());
        double explosionDamage = explosion != null && explosion.isExplode()
                ? explosion.getDamage()
                : 0.0;
        List<WeaponFireModeEvidence> result = new ArrayList<>();
        for (FireMode mode : gun.getFireModeSet()) {
            if (mode == null) {
                continue;
            }
            GunFireModeAdjustData adjust = gun.getFireModeAdjustData(mode);
            BurstData burst = mode == FireMode.BURST ? gun.getBurstData() : null;
            ChargeProfile charge = chargeProfile(gun.getChargeData(mode));
            result.add(new WeaponFireModeEvidence(
                    mode.name().toLowerCase(Locale.ROOT),
                    addAndClamp(directDamage,
                            adjust == null ? 0.0 : adjust.getDamageAmount(),
                            explosionDamage),
                    (double) gun.getRoundsPerMinute(mode),
                    burst == null ? 1 : Math.max(1, burst.getCount()),
                    burst == null ? null : positiveOrNull(burst.getBpm()),
                    burst == null ? null : nonNegativeOrNull(burst.getMinInterval()),
                    charge.initialSeconds(),
                    charge.repeatSeconds(),
                    charge.duringCooldown(),
                    addAndClamp(speed, adjust == null ? 0.0 : adjust.getSpeed(), 0.0),
                    addAndClamp(armorIgnore,
                            adjust == null ? 0.0 : adjust.getArmorIgnore(), 0.0),
                    addAndClamp(headshot,
                            adjust == null ? 0.0 : adjust.getHeadShotMultiplier(), 0.0),
                    addAndClamp(baseAimedInaccuracy,
                            adjust == null ? 0.0 : adjust.getAimInaccuracy(), 0.0)));
        }
        return List.copyOf(result);
    }

    private static ChargeProfile chargeProfile(ChargeData charge) {
        if (charge == null) {
            return new ChargeProfile(0.0, 0.0, true);
        }
        double increase = charge.getIncreasePerTick();
        double target = chargeTarget(charge);
        if (!Double.isFinite(increase) || increase <= 0.0
                || !Double.isFinite(target) || target < 0.0) {
            return new ChargeProfile(null, null, charge.isChargeDuringCooldown());
        }
        double initial = target / increase / 20.0;
        double remaining = charge.getChargeType() == ChargeType.DELAY
                ? 0.0
                : Math.max(0.0, target - charge.getDecreaseOnFire());
        double repeat = Math.max(0.0, target - remaining) / increase / 20.0;
        return new ChargeProfile(initial, repeat, charge.isChargeDuringCooldown());
    }

    private static double chargeTarget(ChargeData charge) {
        return charge.getChargeType() == ChargeType.HOLD
                ? Math.min(charge.getFireThreshold(), charge.getMaxCharge())
                : charge.getMaxCharge();
    }

    private static Double heatRecoverySeconds(GunHeatData heat) {
        if (heat == null) {
            return null;
        }
        Double maximum = positiveOrNull(heat.getHeatMax());
        Double cooling = positiveOrNull(heat.getCoolingMultiplier());
        Double lock = nonNegativeOrNull(heat.getOverHeatTime());
        if (maximum == null || cooling == null || lock == null) {
            return null;
        }
        // TaCZ documents cumulative cooling as cooling_multiplier * seconds^2.
        return lock / 1000.0 + Math.sqrt(maximum / cooling);
    }

    private static Double addAndClamp(Double base, double adjustment, double extra) {
        if (base == null || !Double.isFinite(adjustment) || !Double.isFinite(extra)) {
            return null;
        }
        return Math.max(0.0, base + adjustment + extra);
    }

    private static String reloadType(GunReloadData reload) {
        FeedType type = reload == null ? null : reload.getType();
        return type == null ? "unknown" : type.name().toLowerCase(Locale.ROOT);
    }

    private static Double aimedInaccuracy(GunData gun, List<String> warnings) {
        Map<InaccuracyType, Float> inaccuracy = gun.getInaccuracy();
        Float value = inaccuracy == null ? null : inaccuracy.get(InaccuracyType.AIM);
        if (value == null) {
            value = InaccuracyType.getDefaultInaccuracy().get(InaccuracyType.AIM);
            warnings.add("inaccuracy.aim (used TaCZ runtime default)");
        }
        return value == null ? null : finite(value);
    }

    private static Double recoilMagnitude(GunRecoil recoil, List<String> warnings) {
        if (recoil == null) {
            warnings.add("recoil");
            return null;
        }
        double maximum = 0.0;
        boolean found = false;
        for (GunRecoilKeyFrame[] frames : List.of(
                recoil.getPitch() == null ? new GunRecoilKeyFrame[0] : recoil.getPitch(),
                recoil.getYaw() == null ? new GunRecoilKeyFrame[0] : recoil.getYaw())) {
            for (GunRecoilKeyFrame frame : frames) {
                if (frame == null || frame.getValue() == null) {
                    continue;
                }
                for (float value : frame.getValue()) {
                    if (Float.isFinite(value)) {
                        maximum = Math.max(maximum, Math.abs(value));
                        found = true;
                    }
                }
            }
        }
        if (!found) {
            warnings.add("recoil keyframes");
            return null;
        }
        return maximum;
    }

    private static boolean isTubeReloadScript(String scriptId) {
        return "tacz:m870_gun_logic".equals(scriptId)
                || "tacz:m1014_gun_logic".equals(scriptId)
                || "tacz:spas_12_gun_logic".equals(scriptId);
    }

    private static Double number(Map<String, Object> values, String field) {
        if (values == null || !(values.get(field) instanceof Number number)) {
            return null;
        }
        return finite(number.doubleValue());
    }

    private static Double tactical(GunReloadTime time) {
        return time == null ? null : finite(time.getTacticalTime());
    }

    private static Double empty(GunReloadTime time) {
        return time == null ? null : finite(time.getEmptyTime());
    }

    private static Double firstPresent(Double preferred, Double fallback) {
        return preferred == null ? fallback : preferred;
    }

    private static Double positiveOrNull(Number value) {
        if (value == null || !Double.isFinite(value.doubleValue())
                || value.doubleValue() <= 0.0) {
            return null;
        }
        return value.doubleValue();
    }

    private static Double nonNegativeOrNull(Number value) {
        if (value == null || !Double.isFinite(value.doubleValue())
                || value.doubleValue() < 0.0) {
            return null;
        }
        return value.doubleValue();
    }

    private static Double positiveRatio(Number numerator, Number denominator) {
        Double first = positiveOrNull(numerator);
        Double second = positiveOrNull(denominator);
        return first == null || second == null ? null : first / second;
    }

    private static Double finite(Number value) {
        if (value == null || !Double.isFinite(value.doubleValue())) {
            return null;
        }
        return value.doubleValue();
    }

    private static Double missing(List<String> warnings, String warning) {
        warnings.add(warning);
        return null;
    }

    record RuntimeGun(String archetype, GunData data) {
        RuntimeGun {
            if (archetype == null || archetype.isBlank() || data == null) {
                throw new IllegalArgumentException("TaCZ runtime gun fields are invalid");
            }
        }
    }

    private record ChargeProfile(
            Double initialSeconds,
            Double repeatSeconds,
            boolean duringCooldown) {
    }

    public record Capture(
            int candidateCount,
            Map<String, WeaponStatEvidence> evidenceByBlueprint,
            Map<String, String> rejectedBlueprints) {
        public Capture {
            if (candidateCount < 0
                    || candidateCount > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                    || evidenceByBlueprint == null
                    || rejectedBlueprints == null
                    || candidateCount != evidenceByBlueprint.size() + rejectedBlueprints.size()) {
                throw new IllegalArgumentException("TaCZ runtime evidence capture is invalid");
            }
            evidenceByBlueprint = immutableEvidence(evidenceByBlueprint);
            rejectedBlueprints = immutableText(rejectedBlueprints);
            if (!Collections.disjoint(
                    evidenceByBlueprint.keySet(), rejectedBlueprints.keySet())) {
                throw new IllegalArgumentException(
                        "TaCZ runtime evidence capture has conflicting blueprint IDs");
            }
        }

        private static Map<String, WeaponStatEvidence> immutableEvidence(
                Map<String, WeaponStatEvidence> source) {
            Map<String, WeaponStatEvidence> copy = new LinkedHashMap<>();
            source.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                if (entry.getKey() == null || entry.getKey().isBlank()
                        || entry.getValue() == null
                        || !entry.getKey().equals(entry.getValue().blueprintId())) {
                    throw new IllegalArgumentException(
                            "TaCZ runtime evidence map is invalid");
                }
                copy.put(entry.getKey(), entry.getValue());
            });
            return Collections.unmodifiableMap(copy);
        }

        private static Map<String, String> immutableText(Map<String, String> source) {
            Map<String, String> copy = new LinkedHashMap<>();
            source.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                if (entry.getKey() == null || entry.getKey().isBlank()
                        || entry.getValue() == null || entry.getValue().isBlank()) {
                    throw new IllegalArgumentException(
                            "TaCZ runtime evidence rejection map is invalid");
                }
                copy.put(entry.getKey(), entry.getValue());
            });
            return Collections.unmodifiableMap(copy);
        }
    }
}
