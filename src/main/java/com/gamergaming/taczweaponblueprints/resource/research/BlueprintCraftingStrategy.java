package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Locale;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Bounded strategy used by automatic, omitted-gun, and ammo profile defaults. */
public record BlueprintCraftingStrategy(
        Mode mode,
        Optional<ResearchWorkbenchTier> workbenchTier,
        Optional<BlueprintCraftingAccessPolicy> fallback) {
    public static final BlueprintCraftingStrategy AUTOMATIC_DEFAULT = automaticTier(
            BlueprintCraftingAccessPolicy.TIER_2);
    public static final BlueprintCraftingStrategy OMITTED_DEFAULT = disabled();
    public static final BlueprintCraftingStrategy AMMO_DEFAULT = linkedWeapon(
            BlueprintCraftingAccessPolicy.TIER_1);
    public static final BlueprintCraftingStrategy LEGACY = unrestricted();

    private static final Codec<Mode> MODE_CODEC = Codec.STRING.flatXmap(
            Mode::parse,
            value -> DataResult.success(value.serializedName()));
    private static final Codec<Fields> FIELDS_CODEC = StrictRecordCodec.wrap(
            "blueprint crafting strategy",
            RecordCodecBuilder.create(instance -> instance.group(
                    MODE_CODEC.fieldOf("mode").forGetter(Fields::mode),
                    new StrictOptionalFieldCodec<>(
                            "workbench_tier",
                            BlueprintProgressionCodecs.WORKBENCH_TIER)
                            .forGetter(Fields::workbenchTier),
                    new StrictOptionalFieldCodec<>("fallback", BlueprintCraftingAccessPolicy.CODEC)
                            .forGetter(Fields::fallback))
                    .apply(instance, Fields::new)),
            "mode",
            "workbench_tier",
            "fallback");
    public static final Codec<BlueprintCraftingStrategy> CODEC = FIELDS_CODEC.flatXmap(
            BlueprintCraftingStrategy::fromFields,
            value -> DataResult.success(new Fields(
                    value.mode(), value.workbenchTier(), value.fallback())));

    public BlueprintCraftingStrategy {
        workbenchTier = workbenchTier == null ? Optional.empty() : workbenchTier;
        fallback = fallback == null ? Optional.empty() : fallback;
        if (mode == null) {
            throw new IllegalArgumentException("crafting strategy mode cannot be null");
        }
        boolean fixed = mode == Mode.FIXED;
        boolean derived = mode == Mode.AUTOMATIC_TIER || mode == Mode.LINKED_WEAPON;
        if (fixed != workbenchTier.isPresent()) {
            throw new IllegalArgumentException(
                    "workbench_tier is required only when crafting strategy mode is fixed");
        }
        if (derived != fallback.isPresent()) {
            throw new IllegalArgumentException(
                    "fallback is required only for automatic_tier or linked_weapon crafting strategies");
        }
    }

    public static BlueprintCraftingStrategy fixed(ResearchWorkbenchTier tier) {
        if (tier == null) {
            throw new IllegalArgumentException("Crafting Workbench level cannot be null");
        }
        return new BlueprintCraftingStrategy(Mode.FIXED, Optional.of(tier), Optional.empty());
    }

    public static BlueprintCraftingStrategy automaticTier(BlueprintCraftingAccessPolicy fallback) {
        return derived(Mode.AUTOMATIC_TIER, fallback);
    }

    public static BlueprintCraftingStrategy linkedWeapon(BlueprintCraftingAccessPolicy fallback) {
        return derived(Mode.LINKED_WEAPON, fallback);
    }

    public static BlueprintCraftingStrategy unrestricted() {
        return new BlueprintCraftingStrategy(Mode.UNRESTRICTED, Optional.empty(), Optional.empty());
    }

    public static BlueprintCraftingStrategy disabled() {
        return new BlueprintCraftingStrategy(Mode.DISABLED, Optional.empty(), Optional.empty());
    }

    private static BlueprintCraftingStrategy derived(
            Mode mode,
            BlueprintCraftingAccessPolicy fallback) {
        if (fallback == null) {
            throw new IllegalArgumentException("derived crafting strategy fallback cannot be null");
        }
        return new BlueprintCraftingStrategy(mode, Optional.empty(), Optional.of(fallback));
    }

    private static DataResult<BlueprintCraftingStrategy> fromFields(Fields fields) {
        try {
            return DataResult.success(new BlueprintCraftingStrategy(
                    fields.mode(), fields.workbenchTier(), fields.fallback()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> exception.getMessage());
        }
    }

    public enum Mode {
        FIXED,
        UNRESTRICTED,
        DISABLED,
        AUTOMATIC_TIER,
        LINKED_WEAPON;

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        private static DataResult<Mode> parse(String value) {
            try {
                return DataResult.success(valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (RuntimeException exception) {
                return DataResult.error(() -> "unknown crafting strategy mode " + value);
            }
        }
    }

    private record Fields(
            Mode mode,
            Optional<ResearchWorkbenchTier> workbenchTier,
            Optional<BlueprintCraftingAccessPolicy> fallback) {
    }
}
