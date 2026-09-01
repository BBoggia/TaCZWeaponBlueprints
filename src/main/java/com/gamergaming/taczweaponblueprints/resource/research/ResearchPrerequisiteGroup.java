package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * One canonical any-of prerequisite group. A group is satisfied when at least
 * one listed alternative is satisfied. Phase 2 preserves this identity through
 * policy resolution, public graph publication, synchronization, and clients.
 */
public record ResearchPrerequisiteGroup(List<ResourceLocation> anyOf) {
    public static final int MAX_ALTERNATIVES = 64;

    private static final Codec<List<ResourceLocation>> RAW_CODEC =
            StrictRecordCodec.wrap(
                    "research prerequisite group",
                    RecordCodecBuilder.<List<ResourceLocation>>create(instance -> instance.group(
                            BlueprintResearchCodecs.RESOURCE_LOCATION.listOf()
                                    .fieldOf("any_of")
                                    .forGetter(values -> values))
                            .apply(instance, values -> values)),
                    "any_of");

    public static final Codec<ResearchPrerequisiteGroup> CODEC = RAW_CODEC.flatXmap(
            ResearchPrerequisiteGroup::decode,
            group -> DataResult.success(group.anyOf()));

    public ResearchPrerequisiteGroup {
        if (anyOf == null || anyOf.isEmpty()) {
            throw new IllegalArgumentException(
                    "research prerequisite group cannot be empty");
        }
        if (anyOf.size() > MAX_ALTERNATIVES) {
            throw new IllegalArgumentException(
                    "research prerequisite group cannot contain more than "
                            + MAX_ALTERNATIVES + " alternatives");
        }
        if (anyOf.stream().anyMatch(value -> value == null
                || value.toString().length()
                        > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH)) {
            throw new IllegalArgumentException(
                    "research prerequisite group contains an invalid alternative ID");
        }
        Set<ResourceLocation> unique = new HashSet<>(anyOf);
        if (unique.size() != anyOf.size()) {
            throw new IllegalArgumentException(
                    "research prerequisite group contains a duplicate alternative");
        }
        anyOf = anyOf.stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    public static ResearchPrerequisiteGroup singleton(ResourceLocation prerequisite) {
        if (prerequisite == null) {
            throw new IllegalArgumentException(
                    "research prerequisite alternative cannot be null");
        }
        return new ResearchPrerequisiteGroup(List.of(prerequisite));
    }

    public boolean satisfiedBy(Predicate<ResourceLocation> satisfied) {
        if (satisfied == null) {
            throw new IllegalArgumentException(
                    "research prerequisite satisfaction predicate cannot be null");
        }
        return anyOf.stream().anyMatch(satisfied);
    }

    public void validateFor(ResourceLocation dependentId) {
        if (dependentId == null) {
            throw new IllegalArgumentException(
                    "research prerequisite dependent ID cannot be null");
        }
        if (anyOf.contains(dependentId)) {
            throw new IllegalArgumentException(
                    "research prerequisite self-reference for " + dependentId);
        }
    }

    String canonicalKey() {
        return anyOf.stream().map(ResourceLocation::toString)
                .collect(java.util.stream.Collectors.joining("\u0000"));
    }

    private static DataResult<ResearchPrerequisiteGroup> decode(
            List<ResourceLocation> alternatives) {
        try {
            return DataResult.success(new ResearchPrerequisiteGroup(alternatives));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }
}
