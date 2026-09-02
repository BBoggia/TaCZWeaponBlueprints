package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.Arrays;

/** Stable metric vocabulary and within-component weights for automatic weapon scoring. */
public enum MechanicalMetric {
    SUSTAINED_DPS("sustained_dps", Component.COMBAT, 27, false),
    EFFECTIVE_DAMAGE("effective_damage", Component.COMBAT, 18, false),
    HEADSHOT_MULTIPLIER("headshot_multiplier", Component.COMBAT, 5, false),
    EFFECTIVE_RANGE("effective_range", Component.COMBAT, 15, false),
    ARMOR_EFFECTIVENESS("armor_effectiveness", Component.COMBAT, 10, false),
    PROJECTILE_SPEED("projectile_speed", Component.COMBAT, 10, false),
    AIMED_INACCURACY("aimed_inaccuracy", Component.COMBAT, 10, true),
    RECOIL_MAGNITUDE("recoil_magnitude", Component.COMBAT, 5, true),
    MAGAZINE_CAPACITY("magazine_capacity", Component.UTILITY, 15, false),
    RELOAD_SECONDS("reload_seconds", Component.UTILITY, 15, true),
    AIM_TIME("aim_time", Component.UTILITY, 15, true),
    DRAW_TIME("draw_time", Component.UTILITY, 10, true),
    WEIGHT("weight", Component.UTILITY, 15, true),
    AIM_MOVEMENT("aim_movement", Component.UTILITY, 10, false),
    FIRE_MODE_COUNT("fire_mode_count", Component.UTILITY, 8, false),
    ATTACHMENT_TYPE_COUNT("attachment_type_count", Component.UTILITY, 12, false);

    private final String serializedName;
    private final Component component;
    private final int weight;
    private final boolean lowerIsBetter;

    MechanicalMetric(
            String serializedName,
            Component component,
            int weight,
            boolean lowerIsBetter) {
        this.serializedName = serializedName;
        this.component = component;
        this.weight = weight;
        this.lowerIsBetter = lowerIsBetter;
    }

    static {
        for (Component component : Component.values()) {
            int total = Arrays.stream(values())
                    .filter(metric -> metric.component == component)
                    .mapToInt(MechanicalMetric::weight)
                    .sum();
            if (total != 100) {
                throw new IllegalStateException(
                        "Weapon mechanical " + component + " weights must total 100");
            }
        }
    }

    public String serializedName() {
        return serializedName;
    }

    public Component component() {
        return component;
    }

    public int weight() {
        return weight;
    }

    public boolean lowerIsBetter() {
        return lowerIsBetter;
    }

    public enum Component {
        COMBAT,
        UTILITY
    }
}
