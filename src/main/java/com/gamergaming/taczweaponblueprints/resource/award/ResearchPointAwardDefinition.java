package com.gamergaming.taczweaponblueprints.resource.award;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardRepeat.Scope;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardRepeat.Type;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardReward.Overflow;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record ResearchPointAwardDefinition(
        int format,
        boolean enabled,
        List<ResourceLocation> profiles,
        ResourceLocation awardGroup,
        int priority,
        ResearchPointAwardTrigger trigger,
        ResearchPointAwardReward reward,
        ResearchPointAwardRepeat repeat,
        Optional<ResearchPointAwardBudget> budget,
        ResearchPointAwardPresentation presentation) {
    public static final int CURRENT_FORMAT = 1;

    private static final Codec<Integer> FORMAT_CODEC = Codec.INT.flatXmap(
            ResearchPointAwardDefinition::validateFormat,
            ResearchPointAwardDefinition::validateFormat);
    private static final Codec<Integer> PRIORITY_CODEC = Codec.INT.flatXmap(
            ResearchPointAwardDefinition::validatePriority,
            ResearchPointAwardDefinition::validatePriority);
    private static final Codec<ResearchPointAwardDefinition> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    FORMAT_CODEC.fieldOf("format").forGetter(ResearchPointAwardDefinition::format),
                    new StrictOptionalFieldCodec<>("enabled", Codec.BOOL)
                            .xmap(value -> value.orElse(true), Optional::of)
                            .forGetter(ResearchPointAwardDefinition::enabled),
                    ResearchPointAwardCodecs.optionalList(
                            "profiles", ResearchPointAwardCodecs.RESOURCE_LOCATION)
                            .forGetter(ResearchPointAwardDefinition::profiles),
                    ResearchPointAwardCodecs.RESOURCE_LOCATION.fieldOf("award_group")
                            .forGetter(ResearchPointAwardDefinition::awardGroup),
                    new StrictOptionalFieldCodec<>("priority", PRIORITY_CODEC)
                            .xmap(value -> value.orElse(0), value ->
                                    value == 0 ? Optional.empty() : Optional.of(value))
                            .forGetter(ResearchPointAwardDefinition::priority),
                    ResearchPointAwardTrigger.CODEC.fieldOf("trigger")
                            .forGetter(ResearchPointAwardDefinition::trigger),
                    ResearchPointAwardReward.CODEC.fieldOf("reward")
                            .forGetter(ResearchPointAwardDefinition::reward),
                    ResearchPointAwardRepeat.CODEC.fieldOf("repeat")
                            .forGetter(ResearchPointAwardDefinition::repeat),
                    new StrictOptionalFieldCodec<>("budget", ResearchPointAwardBudget.CODEC)
                            .forGetter(ResearchPointAwardDefinition::budget),
                    ResearchPointAwardPresentation.CODEC.fieldOf("presentation")
                            .forGetter(ResearchPointAwardDefinition::presentation))
                    .apply(instance, ResearchPointAwardDefinition::new));

    public static final Codec<ResearchPointAwardDefinition> CODEC = StrictRecordCodec.wrap(
            "Research Point award definition",
            RAW_CODEC.flatXmap(
                    ResearchPointAwardDefinition::validate,
                    ResearchPointAwardDefinition::validate),
            "format",
            "enabled",
            "profiles",
            "award_group",
            "priority",
            "trigger",
            "reward",
            "repeat",
            "budget",
            "presentation");

    public ResearchPointAwardDefinition {
        if (format != CURRENT_FORMAT || awardGroup == null || trigger == null
                || reward == null || repeat == null || presentation == null) {
            throw new IllegalArgumentException("invalid Research Point award definition");
        }
        profiles = profiles == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(profiles.stream()
                        .sorted(Comparator.comparing(ResourceLocation::toString))
                        .toList()));
        budget = budget == null ? Optional.empty() : budget;
    }

    public boolean appliesToProfile(ResourceLocation profileId) {
        return profileId != null && (profiles.isEmpty() || profiles.contains(profileId));
    }

    public ResourceLocation effectiveClaimId(ResourceLocation definitionId) {
        if (definitionId == null) {
            throw new IllegalArgumentException("definition ID cannot be null");
        }
        return repeat.claimId().orElse(definitionId);
    }

    void validateForSnapshot() {
        DataResult<com.google.gson.JsonElement> encoded = CODEC.encodeStart(JsonOps.INSTANCE, this);
        encoded.error().ifPresent(error -> {
            throw new IllegalArgumentException(error.message());
        });
    }

    private static DataResult<ResearchPointAwardDefinition> validate(
            ResearchPointAwardDefinition definition) {
        if (definition.profiles().size()
                > PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_PROFILES) {
            return DataResult.error(() -> "Research Point award cannot target more than "
                    + PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_PROFILES + " profiles");
        }
        if (definition.trigger().type() == ResearchPointAwardTrigger.Type.INVENTORY_TURN_IN
                && definition.reward().overflow() != Overflow.REQUIRE_FULL) {
            return DataResult.error(() -> "inventory_turn_in awards must use require_full overflow");
        }
        if (definition.trigger().retroactive() && !definition.repeat().finite()) {
            return DataResult.error(() -> "retroactive awards must use once or once_per_target repeat");
        }
        if (definition.repeat().type() == Type.ONCE_PER_TARGET
                && definition.trigger().type() == ResearchPointAwardTrigger.Type.BLUEPRINT_MILESTONE) {
            return DataResult.error(() -> "blueprint milestones cannot use once_per_target repeat");
        }
        if (definition.trigger().type() == ResearchPointAwardTrigger.Type.BLUEPRINT_MILESTONE
                && definition.repeat().type() != Type.ONCE) {
            return DataResult.error(() -> "blueprint milestones must use once repeat");
        }
        if (definition.repeat().scope() == Scope.TARGET
                && definition.trigger().type() == ResearchPointAwardTrigger.Type.BLUEPRINT_MILESTONE) {
            return DataResult.error(() -> "blueprint milestones cannot use target-scoped repeat state");
        }
        if (definition.repeat().finite()
                && definition.reward().overflow() == Overflow.REQUIRE_FULL
                && definition.trigger().type() != ResearchPointAwardTrigger.Type.INVENTORY_TURN_IN
                && !definition.trigger().retroactive()) {
            return DataResult.error(() ->
                    "finite require_full awards require a retroactive reconciliation path");
        }
        return DataResult.success(definition);
    }

    private static DataResult<Integer> validateFormat(int value) {
        return value == CURRENT_FORMAT
                ? DataResult.success(value)
                : DataResult.error(() -> "unsupported Research Point award format " + value);
    }

    private static DataResult<Integer> validatePriority(int value) {
        return Math.abs((long) value)
                <= PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_ABSOLUTE_PRIORITY
                ? DataResult.success(value)
                : DataResult.error(() -> "Research Point award priority is outside the supported range");
    }
}
