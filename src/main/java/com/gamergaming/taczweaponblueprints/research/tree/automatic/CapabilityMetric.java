package com.gamergaming.taczweaponblueprints.research.tree.automatic;

/** Stable v3 capability vocabulary. Package weights live in the v3 scorer. */
public enum CapabilityMetric {
    IMPACT_DAMAGE("impact_damage", false),
    SUSTAINED_DPS("sustained_dps", false),
    DAMAGE_RETENTION("damage_retention", false),
    HEADSHOT_MULTIPLIER("headshot_multiplier", false),
    EFFECTIVE_RANGE("effective_range", false),
    ARMOR_IGNORE("armor_ignore", false),
    TARGET_PENETRATION("target_penetration", false),
    PROJECTILE_SPEED("projectile_speed", false),
    PROJECTILE_GRAVITY("projectile_gravity", true),
    AIMED_INACCURACY("aimed_inaccuracy", true),
    RECOIL_MAGNITUDE("recoil_magnitude", true),
    EXPLOSION_DAMAGE("explosion_damage", false),
    EXPLOSION_RADIUS("explosion_radius", false),
    CONTROL_EFFECTS("control_effects", false),
    MAGAZINE_CAPACITY("magazine_capacity", false),
    EMPTY_RELOAD_SECONDS("empty_reload_seconds", true),
    TACTICAL_RELOAD_SECONDS("tactical_reload_seconds", true),
    AIM_TIME("aim_time", true),
    DRAW_TIME("draw_time", true),
    WEIGHT("weight", true),
    AIM_MOVEMENT("aim_movement", false),
    FIRE_MODE_COUNT("fire_mode_count", false),
    ATTACHMENT_TYPE_COUNT("attachment_type_count", false),
    PROJECTILE_COUNT("projectile_count", false),
    CHARGE_SECONDS("charge_seconds", true);

    private final String serializedName;
    private final boolean lowerIsBetter;

    CapabilityMetric(String serializedName, boolean lowerIsBetter) {
        this.serializedName = serializedName;
        this.lowerIsBetter = lowerIsBetter;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean lowerIsBetter() {
        return lowerIsBetter;
    }
}
