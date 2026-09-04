package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Locale;

import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentDiscount;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateGroup;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateScope;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTierRequirement;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictMapCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Strict, bounded JSON codecs for the pure progression domain introduced in Phase 1. */
final class BlueprintProgressionCodecs {
    static final Codec<ResearchWorkbenchTier> WORKBENCH_TIER = enumCodec(
            "Research Bench tier",
            ResearchWorkbenchTier::parse,
            ResearchWorkbenchTier::serializedName);
    static final Codec<ProgressionGateScope> GATE_SCOPE = enumCodec(
            "Progression Gate scope",
            value -> ProgressionGateScope.valueOf(normalizeEnum(value)),
            value -> value.name().toLowerCase(Locale.ROOT));
    static final Codec<ProgressionGateCondition.Disclosure> DISCLOSURE = enumCodec(
            "Progression Gate disclosure",
            value -> ProgressionGateCondition.Disclosure.valueOf(normalizeEnum(value)),
            value -> value.name().toLowerCase(Locale.ROOT));
    private static final Codec<ProgressionGateCondition.Type> GATE_TYPE = enumCodec(
            "Progression Gate type",
            value -> ProgressionGateCondition.Type.valueOf(normalizeEnum(value)),
            value -> value.name().toLowerCase(Locale.ROOT));
    static final Codec<BlueprintFragmentPolicy.CompletionMode> FRAGMENT_MODE = enumCodec(
            "Blueprint Fragment mode",
            value -> BlueprintFragmentPolicy.CompletionMode.valueOf(normalizeEnum(value)),
            value -> value.name().toLowerCase(Locale.ROOT));
    static final Codec<BlueprintFragmentDiscount.Mode> DISCOUNT_MODE = enumCodec(
            "Blueprint Fragment discount mode",
            value -> BlueprintFragmentDiscount.Mode.valueOf(normalizeEnum(value)),
            value -> value.name().toLowerCase(Locale.ROOT));

    static final Codec<ResearchWorkbenchTierRequirement> WORKBENCH_REQUIREMENT =
            StrictRecordCodec.wrap(
                    "Research Bench tier requirement",
                    RecordCodecBuilder.create(instance -> instance.group(
                            WORKBENCH_TIER.fieldOf("research")
                                    .forGetter(ResearchWorkbenchTierRequirement::researchTier),
                            WORKBENCH_TIER.fieldOf("crafting")
                                    .forGetter(ResearchWorkbenchTierRequirement::craftingTier))
                            .apply(instance, ResearchWorkbenchTierRequirement::new)),
                    "research",
                    "crafting");

    static final Codec<BlueprintFragmentDiscount> FRAGMENT_DISCOUNT =
            StrictRecordCodec.wrap(
                    "Blueprint Fragment discount",
                    RecordCodecBuilder.create(instance -> instance.group(
                            DISCOUNT_MODE.fieldOf("mode")
                                    .forGetter(BlueprintFragmentDiscount::mode),
                            Codec.INT.fieldOf("value")
                                    .forGetter(BlueprintFragmentDiscount::value))
                            .apply(instance, BlueprintProgressionCodecs::discount)),
                    "mode",
                    "value");

    private static final Codec<ProgressionGateCondition.Criterion> CRITERION =
            StrictMapCodec.wrap(
                    "criterion Progression Gate",
                    RecordCodecBuilder.<ProgressionGateCondition.Criterion>mapCodec(instance -> instance.group(
                            BlueprintResearchCodecs.RESOURCE_LOCATION.fieldOf("id")
                                    .forGetter(ProgressionGateCondition.Criterion::criterionId),
                            Codec.INT.fieldOf("value")
                                    .forGetter(ProgressionGateCondition.Criterion::requiredValue),
                            GATE_SCOPE.fieldOf("scope")
                                    .forGetter(ProgressionGateCondition.Criterion::scope),
                            Codec.STRING.fieldOf("message")
                                    .forGetter(ProgressionGateCondition.Criterion::messageKey),
                            new StrictOptionalFieldCodec<>("disclosure", DISCLOSURE)
                                    .xmap(value -> value.orElse(ProgressionGateCondition.Disclosure.PUBLIC),
                                            java.util.Optional::of)
                                    .forGetter(ProgressionGateCondition.Criterion::disclosure))
                            .apply(instance, ProgressionGateCondition.Criterion::new)),
                    "type", "id", "value", "scope", "message", "disclosure").codec();

    private static final Codec<ProgressionGateCondition.Advancement> ADVANCEMENT =
            StrictMapCodec.wrap(
                    "advancement Progression Gate",
                    RecordCodecBuilder.<ProgressionGateCondition.Advancement>mapCodec(instance -> instance.group(
                            BlueprintResearchCodecs.RESOURCE_LOCATION.fieldOf("id")
                                    .forGetter(ProgressionGateCondition.Advancement::advancementId),
                            GATE_SCOPE.fieldOf("scope")
                                    .forGetter(ProgressionGateCondition.Advancement::scope),
                            Codec.STRING.fieldOf("message")
                                    .forGetter(ProgressionGateCondition.Advancement::messageKey),
                            new StrictOptionalFieldCodec<>("disclosure", DISCLOSURE)
                                    .xmap(value -> value.orElse(ProgressionGateCondition.Disclosure.PUBLIC),
                                            java.util.Optional::of)
                                    .forGetter(ProgressionGateCondition.Advancement::disclosure))
                            .apply(instance, ProgressionGateCondition.Advancement::new)),
                    "type", "id", "scope", "message", "disclosure").codec();

    private static final Codec<ProgressionGateCondition.WorkbenchTier> WORKBENCH_GATE =
            StrictMapCodec.wrap(
                    "workbench-tier Progression Gate",
                    RecordCodecBuilder.<ProgressionGateCondition.WorkbenchTier>mapCodec(instance -> instance.group(
                            WORKBENCH_TIER.fieldOf("tier")
                                    .forGetter(ProgressionGateCondition.WorkbenchTier::requiredTier),
                            GATE_SCOPE.fieldOf("scope")
                                    .forGetter(ProgressionGateCondition.WorkbenchTier::scope),
                            Codec.STRING.fieldOf("message")
                                    .forGetter(ProgressionGateCondition.WorkbenchTier::messageKey),
                            new StrictOptionalFieldCodec<>("disclosure", DISCLOSURE)
                                    .xmap(value -> value.orElse(ProgressionGateCondition.Disclosure.PUBLIC),
                                            java.util.Optional::of)
                                    .forGetter(ProgressionGateCondition.WorkbenchTier::disclosure))
                            .apply(instance, ProgressionGateCondition.WorkbenchTier::new)),
                    "type", "tier", "scope", "message", "disclosure").codec();

    static final Codec<ProgressionGateCondition> GATE_CONDITION = GATE_TYPE
            .dispatch(
                    "type",
                    ProgressionGateCondition::type,
                    BlueprintProgressionCodecs::gateCodec);

    private static final Codec<GateGroupFields> GATE_GROUP_FIELDS = StrictRecordCodec.wrap(
            "Progression Gate group",
            RecordCodecBuilder.create(instance -> instance.group(
                    GATE_CONDITION.listOf().fieldOf("any_of")
                            .forGetter(GateGroupFields::anyOf))
                    .apply(instance, GateGroupFields::new)),
            "any_of");
    static final Codec<ProgressionGateGroup> GATE_GROUP = GATE_GROUP_FIELDS.flatXmap(
            fields -> construct("Progression Gate group", () -> new ProgressionGateGroup(fields.anyOf())),
            group -> DataResult.success(new GateGroupFields(group.anyOf())));

    private static final Codec<GateRequirementFields> GATE_REQUIREMENT_FIELDS = StrictRecordCodec.wrap(
            "Progression Gate requirements",
            RecordCodecBuilder.create(instance -> instance.group(
                    GATE_GROUP.listOf().fieldOf("all_of")
                            .forGetter(GateRequirementFields::allOf))
                    .apply(instance, GateRequirementFields::new)),
            "all_of");
    static final Codec<ProgressionGateRequirements> GATE_REQUIREMENTS = GATE_REQUIREMENT_FIELDS.flatXmap(
            fields -> construct(
                    "Progression Gate requirements",
                    () -> new ProgressionGateRequirements(fields.allOf())),
            requirements -> DataResult.success(new GateRequirementFields(requirements.allOf())));

    private BlueprintProgressionCodecs() {
    }

    private static Codec<? extends ProgressionGateCondition> gateCodec(
            ProgressionGateCondition.Type type) {
        return switch (type) {
            case CRITERION -> CRITERION;
            case ADVANCEMENT -> ADVANCEMENT;
            case WORKBENCH_TIER -> WORKBENCH_GATE;
        };
    }

    private static BlueprintFragmentDiscount discount(
            BlueprintFragmentDiscount.Mode mode,
            int value) {
        return new BlueprintFragmentDiscount(mode, value);
    }

    private static <E> Codec<E> enumCodec(
            String description,
            java.util.function.Function<String, E> parser,
            java.util.function.Function<E, String> serializer) {
        return Codec.STRING.flatXmap(
                value -> parseEnum(description, value, parser),
                value -> DataResult.success(serializer.apply(value)));
    }

    private static <E> DataResult<E> parseEnum(
            String description,
            String value,
            java.util.function.Function<String, E> parser) {
        try {
            return DataResult.success(parser.apply(value));
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "unknown " + description + " " + value);
        }
    }

    private static String normalizeEnum(String value) {
        if (value == null) {
            throw new IllegalArgumentException("enum value cannot be null");
        }
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static <T> DataResult<T> construct(
            String description,
            java.util.function.Supplier<T> constructor) {
        try {
            return DataResult.success(constructor.get());
        } catch (RuntimeException exception) {
            return DataResult.error(() -> description + " is invalid: " + exception.getMessage());
        }
    }

    private record GateGroupFields(java.util.List<ProgressionGateCondition> anyOf) {
    }

    private record GateRequirementFields(java.util.List<ProgressionGateGroup> allOf) {
    }
}
