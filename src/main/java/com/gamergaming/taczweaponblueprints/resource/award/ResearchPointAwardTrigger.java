package com.gamergaming.taczweaponblueprints.resource.award;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.CombatFacts;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.CreditType;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.Difficulty;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.DispatchMode;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.SpawnProvenance;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/** Trigger identity, selector, and trigger-owned typed conditions. */
public record ResearchPointAwardTrigger(
        Type type,
        Optional<ResearchPointAwardTarget> target,
        boolean retroactive,
        Optional<Milestone> milestone,
        Optional<CombatConditions> combat) {
    private static final Codec<ResearchPointAwardTrigger> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Type.CODEC.fieldOf("type").forGetter(ResearchPointAwardTrigger::type),
                    new StrictOptionalFieldCodec<>("target", ResearchPointAwardTarget.CODEC)
                            .forGetter(ResearchPointAwardTrigger::target),
                    new StrictOptionalFieldCodec<>("retroactive", Codec.BOOL)
                            .xmap(value -> value.orElse(false), ResearchPointAwardTrigger::optionalTrue)
                            .forGetter(ResearchPointAwardTrigger::retroactive),
                    new StrictOptionalFieldCodec<>("milestone", Milestone.CODEC)
                            .forGetter(ResearchPointAwardTrigger::milestone),
                    new StrictOptionalFieldCodec<>("combat", CombatConditions.CODEC)
                            .forGetter(ResearchPointAwardTrigger::combat))
                    .apply(instance, ResearchPointAwardTrigger::new));

    public static final Codec<ResearchPointAwardTrigger> CODEC = StrictRecordCodec.wrap(
            "Research Point award trigger",
            RAW_CODEC.flatXmap(ResearchPointAwardTrigger::validate, ResearchPointAwardTrigger::validate),
            "type",
            "target",
            "retroactive",
            "milestone",
            "combat");

    public ResearchPointAwardTrigger {
        if (type == null) {
            throw new IllegalArgumentException("Research Point award trigger type cannot be null");
        }
        target = target == null ? Optional.empty() : target;
        milestone = milestone == null ? Optional.empty() : milestone;
        combat = combat == null ? Optional.empty() : combat;
    }

    public boolean conditionsMatch(ResearchPointAwardContext context) {
        if (context == null || context.triggerType() != type) {
            return false;
        }
        if (context.dispatchMode() == DispatchMode.RETROACTIVE && !retroactive) {
            return false;
        }
        if (milestone.isPresent() && !milestone.orElseThrow().matches(context)) {
            return false;
        }
        if (type == Type.ENTITY_KILLED) {
            CombatConditions effective = combat.orElse(CombatConditions.SAFE_DEFAULTS);
            return context.combatFacts().filter(effective::matches).isPresent();
        }
        return true;
    }

    public ResearchPointAwardTarget.Specificity targetSpecificity(ResearchPointAwardContext context) {
        return target.filter(value -> !value.isGeneric()).map(value -> value.match(context))
                .orElse(ResearchPointAwardTarget.Specificity.GENERIC);
    }

    private static DataResult<ResearchPointAwardTrigger> validate(ResearchPointAwardTrigger trigger) {
        if (trigger.type() == Type.BLUEPRINT_MILESTONE && trigger.milestone().isEmpty()) {
            return DataResult.error(() -> "blueprint_milestone trigger requires milestone conditions");
        }
        if (trigger.type() != Type.BLUEPRINT_MILESTONE && trigger.milestone().isPresent()) {
            return DataResult.error(() -> "milestone conditions require blueprint_milestone trigger");
        }
        if (trigger.type() == Type.ENTITY_KILLED && trigger.retroactive()) {
            return DataResult.error(() -> "entity_killed trigger cannot be retroactive");
        }
        if (trigger.type() == Type.INTEGRATION && trigger.retroactive()) {
            return DataResult.error(() -> "integration trigger cannot be retroactive");
        }
        if (trigger.type() != Type.ENTITY_KILLED && trigger.combat().isPresent()) {
            return DataResult.error(() -> "combat conditions require entity_killed trigger");
        }
        if (!trigger.type().supportsCatalogSelector()
                && trigger.target().flatMap(ResearchPointAwardTarget::catalogSelector).isPresent()) {
            return DataResult.error(() -> trigger.type().serializedName()
                    + " trigger cannot use a blueprint catalog selector");
        }
        if (trigger.type() == Type.INVENTORY_TURN_IN
                && trigger.target().map(ResearchPointAwardTarget::isGeneric).orElse(true)) {
            return DataResult.error(() -> "inventory_turn_in trigger requires a non-generic target");
        }
        if (trigger.type() == Type.INTEGRATION
                && trigger.target().filter(value -> !value.tags().isEmpty()
                        || value.catalogSelector().isPresent()).isPresent()) {
            return DataResult.error(() ->
                    "integration trigger supports only exact IDs and namespaces");
        }
        return DataResult.success(trigger);
    }

    private static Optional<Boolean> optionalTrue(boolean value) {
        return value ? Optional.of(true) : Optional.empty();
    }

    public enum Type {
        ADVANCEMENT_COMPLETED,
        BLUEPRINT_DISCOVERED,
        BLUEPRINT_LEARNED,
        BLUEPRINT_MILESTONE,
        ENTITY_KILLED,
        INVENTORY_TURN_IN,
        INTEGRATION;

        public static final Codec<Type> CODEC = Codec.STRING.flatXmap(
                Type::parse,
                value -> DataResult.success(value.serializedName()));

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean supportsCatalogSelector() {
            return this == BLUEPRINT_DISCOVERED
                    || this == BLUEPRINT_LEARNED
                    || this == BLUEPRINT_MILESTONE;
        }

        private static DataResult<Type> parse(String value) {
            if (value != null) {
                try {
                    return DataResult.success(valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // Report the stable serialized values below.
                }
            }
            return DataResult.error(() -> "unknown Research Point award trigger " + value);
        }
    }

    public enum MilestoneState {
        DISCOVERED,
        LEARNED;

        public static final Codec<MilestoneState> CODEC = Codec.STRING.flatXmap(
                MilestoneState::parse,
                value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));

        private static DataResult<MilestoneState> parse(String value) {
            if (value != null) {
                try {
                    return DataResult.success(valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return DataResult.error(() -> "unknown blueprint milestone state " + value);
        }
    }

    public record Milestone(MilestoneState state, int threshold) {
        private static final Codec<Milestone> RAW_CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        MilestoneState.CODEC.fieldOf("state").forGetter(Milestone::state),
                        ResearchPointAwardCodecs.POSITIVE_INT.fieldOf("threshold")
                                .forGetter(Milestone::threshold))
                        .apply(instance, Milestone::new));
        public static final Codec<Milestone> CODEC = StrictRecordCodec.wrap(
                "Research Point blueprint milestone",
                RAW_CODEC,
                "state",
                "threshold");

        public Milestone {
            if (state == null || threshold <= 0) {
                throw new IllegalArgumentException("invalid Research Point blueprint milestone");
            }
        }

        public boolean matches(ResearchPointAwardContext context) {
            return context.milestoneState().filter(value -> value == state).isPresent()
                    && context.previousCount() < threshold
                    && context.currentCount() >= threshold;
        }
    }

    public record CombatConditions(
            boolean allowOwnedProjectile,
            boolean allowIndirect,
            boolean allowFakePlayer,
            boolean allowPetCredit,
            boolean allowPvp,
            boolean allowBaby,
            boolean allowNamed,
            boolean allowTamed,
            boolean allowSpawner,
            boolean allowBred,
            boolean allowSummoned,
            boolean requireSpawnProvenance,
            long minimumLifetimeTicks,
            List<ResourceLocation> dimensions,
            List<Difficulty> difficulties,
            BossMode boss) {
        public static final CombatConditions SAFE_DEFAULTS = new CombatConditions(
                true,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                0L,
                List.of(),
                List.of(),
                BossMode.ANY);

        private static final Codec<CombatConditions> RAW_CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        optionalBoolean("allow_owned_projectile", true)
                                .forGetter(CombatConditions::allowOwnedProjectile),
                        optionalBoolean("allow_indirect", false).forGetter(CombatConditions::allowIndirect),
                        optionalBoolean("allow_fake_player", false).forGetter(CombatConditions::allowFakePlayer),
                        optionalBoolean("allow_pet_credit", false).forGetter(CombatConditions::allowPetCredit),
                        optionalBoolean("allow_pvp", false).forGetter(CombatConditions::allowPvp),
                        optionalBoolean("allow_baby", false).forGetter(CombatConditions::allowBaby),
                        optionalBoolean("allow_named", true).forGetter(CombatConditions::allowNamed),
                        optionalBoolean("allow_tamed", false).forGetter(CombatConditions::allowTamed),
                        optionalBoolean("allow_spawner", false).forGetter(CombatConditions::allowSpawner),
                        optionalBoolean("allow_bred", false).forGetter(CombatConditions::allowBred),
                        optionalBoolean("allow_summoned", false).forGetter(CombatConditions::allowSummoned),
                        optionalBoolean("require_spawn_provenance", true)
                                .forGetter(CombatConditions::requireSpawnProvenance),
                        new StrictOptionalFieldCodec<>("minimum_lifetime_ticks", Codec.LONG)
                                .xmap(value -> value.orElse(0L), value -> optionalLong(value, 0L))
                                .forGetter(CombatConditions::minimumLifetimeTicks),
                        ResearchPointAwardCodecs.optionalList(
                                "dimensions", ResearchPointAwardCodecs.RESOURCE_LOCATION)
                                .forGetter(CombatConditions::dimensions),
                        ResearchPointAwardCodecs.optionalList("difficulties", DifficultyCodec.CODEC)
                                .forGetter(CombatConditions::difficulties),
                        new StrictOptionalFieldCodec<>("boss", BossMode.CODEC)
                                .xmap(value -> value.orElse(BossMode.ANY), value ->
                                        value == BossMode.ANY ? Optional.empty() : Optional.of(value))
                                .forGetter(CombatConditions::boss))
                        .apply(instance, CombatConditions::new));

        public static final Codec<CombatConditions> CODEC = StrictRecordCodec.wrap(
                "Research Point combat conditions",
                RAW_CODEC.flatXmap(CombatConditions::validate, CombatConditions::validate),
                "allow_owned_projectile",
                "allow_indirect",
                "allow_fake_player",
                "allow_pet_credit",
                "allow_pvp",
                "allow_baby",
                "allow_named",
                "allow_tamed",
                "allow_spawner",
                "allow_bred",
                "allow_summoned",
                "require_spawn_provenance",
                "minimum_lifetime_ticks",
                "dimensions",
                "difficulties",
                "boss");

        public CombatConditions {
            dimensions = immutableUnique(dimensions);
            difficulties = immutableUnique(difficulties);
            if (boss == null) {
                throw new IllegalArgumentException("combat boss mode cannot be null");
            }
        }

        public boolean matches(CombatFacts facts) {
            if (facts.creditType() == CreditType.OWNED_PROJECTILE && !allowOwnedProjectile
                    || facts.creditType() == CreditType.INDIRECT && !allowIndirect
                    || facts.creditType() == CreditType.PET && !allowPetCredit
                    || facts.fakePlayer() && !allowFakePlayer
                    || facts.petCredit() && !allowPetCredit
                    || facts.pvp() && !allowPvp
                    || facts.baby() && !allowBaby
                    || facts.named() && !allowNamed
                    || facts.tamed() && !allowTamed
                    || facts.lifetimeTicks() < minimumLifetimeTicks
                    || !dimensions.isEmpty() && !dimensions.contains(facts.dimension())
                    || !difficulties.isEmpty() && !difficulties.contains(facts.difficulty())
                    || boss == BossMode.REQUIRED && !facts.boss()
                    || boss == BossMode.EXCLUDED && facts.boss()) {
                return false;
            }
            SpawnProvenance provenance = facts.spawnProvenance().orElse(null);
            if (provenance == null) {
                return !requireSpawnProvenance;
            }
            return switch (provenance) {
                case SPAWNER -> allowSpawner;
                case BRED -> allowBred;
                case SUMMONED -> allowSummoned;
                default -> true;
            };
        }

        private static DataResult<CombatConditions> validate(CombatConditions value) {
            if (value.minimumLifetimeTicks() < 0L) {
                return DataResult.error(() -> "minimum_lifetime_ticks cannot be negative");
            }
            if (value.dimensions().size() + value.difficulties().size() > 256) {
                return DataResult.error(() -> "combat conditions cannot contain more than 256 list terms");
            }
            return DataResult.success(value);
        }

        private static com.mojang.serialization.MapCodec<Boolean> optionalBoolean(
                String name,
                boolean defaultValue) {
            return new StrictOptionalFieldCodec<>(name, Codec.BOOL)
                    .xmap(
                            value -> value.orElse(defaultValue),
                            value -> value == defaultValue ? Optional.empty() : Optional.of(value));
        }

        private static Optional<Long> optionalLong(long value, long defaultValue) {
            return value == defaultValue ? Optional.empty() : Optional.of(value);
        }

        private static <T> List<T> immutableUnique(List<T> values) {
            return values == null ? List.of() : List.copyOf(new LinkedHashSet<>(values));
        }
    }

    public enum BossMode {
        ANY,
        REQUIRED,
        EXCLUDED;

        public static final Codec<BossMode> CODEC = Codec.STRING.flatXmap(
                BossMode::parse,
                value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));

        private static DataResult<BossMode> parse(String value) {
            if (value != null) {
                try {
                    return DataResult.success(valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return DataResult.error(() -> "unknown combat boss mode " + value);
        }
    }

    private static final class DifficultyCodec {
        private static final Codec<Difficulty> CODEC = Codec.STRING.flatXmap(
                DifficultyCodec::parse,
                value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));

        private static DataResult<Difficulty> parse(String value) {
            if (value != null) {
                try {
                    return DataResult.success(Difficulty.valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return DataResult.error(() -> "unknown combat difficulty " + value);
        }
    }
}
