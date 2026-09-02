package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintCatalogSelector;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootTag;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record BlueprintResearchTarget(
        List<ResourceLocation> blueprints,
        List<ResourceLocation> tags,
        Optional<BlueprintCatalogSelector> selector) {
    public static final int MAX_TERMS = 256;

    private static final Codec<BlueprintResearchTarget> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    optionalList("blueprints", BlueprintResearchCodecs.RESOURCE_LOCATION)
                            .forGetter(BlueprintResearchTarget::blueprints),
                    optionalList("tags", BlueprintResearchCodecs.RESOURCE_LOCATION)
                            .forGetter(BlueprintResearchTarget::tags),
                    new StrictOptionalFieldCodec<>("selector", BlueprintCatalogSelector.RESEARCH_CODEC)
                            .forGetter(BlueprintResearchTarget::selector))
                    .apply(instance, BlueprintResearchTarget::new));

    public static final Codec<BlueprintResearchTarget> CODEC = StrictRecordCodec.wrap(
            "blueprint research target",
            RAW_CODEC.flatXmap(BlueprintResearchTarget::validateTarget, BlueprintResearchTarget::validateTarget),
            "blueprints",
            "tags",
            "selector");

    public BlueprintResearchTarget {
        blueprints = blueprints == null ? List.of() : List.copyOf(new LinkedHashSet<>(blueprints));
        tags = tags == null ? List.of() : List.copyOf(new LinkedHashSet<>(tags));
        selector = selector == null ? Optional.empty() : selector;
    }

    public MatchSpecificity match(
            ResourceLocation blueprintId,
            BlueprintData data,
            Map<ResourceLocation, BlueprintLootTag> availableTags) {
        if (blueprintId == null) {
            return MatchSpecificity.NONE;
        }
        if (blueprints.contains(blueprintId)) {
            return MatchSpecificity.EXACT;
        }
        for (ResourceLocation tagId : tags) {
            BlueprintLootTag tag = availableTags.get(tagId);
            if (tag != null && tag.values().contains(blueprintId)) {
                return MatchSpecificity.TAG;
            }
        }
        return selector.filter(value -> value.matches(blueprintId, data)).isPresent()
                ? MatchSpecificity.SELECTOR
                : MatchSpecificity.NONE;
    }

    public boolean exactOnly() {
        return !blueprints.isEmpty() && tags.isEmpty() && selector.isEmpty();
    }

    private static DataResult<BlueprintResearchTarget> validateTarget(BlueprintResearchTarget target) {
        int termCount = target.blueprints().size() + target.tags().size();
        if (termCount == 0 && target.selector().isEmpty()) {
            return DataResult.error(() -> "research target must define at least one target");
        }
        return termCount <= MAX_TERMS
                ? DataResult.success(target)
                : DataResult.error(() -> "research target cannot contain more than " + MAX_TERMS + " terms");
    }

    private static boolean isOversized(ResourceLocation id) {
        return id == null || id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;
    }

    void validateForSnapshot() {
        int termCount = blueprints.size() + tags.size();
        if (termCount == 0 && selector.isEmpty()) {
            throw new IllegalArgumentException("research target must define at least one target");
        }
        if (termCount > MAX_TERMS) {
            throw new IllegalArgumentException("research target cannot contain more than " + MAX_TERMS + " terms");
        }
        if (blueprints.stream().anyMatch(BlueprintResearchTarget::isOversized)
                || tags.stream().anyMatch(BlueprintResearchTarget::isOversized)) {
            throw new IllegalArgumentException("research target contains an oversized resource ID");
        }
        selector.ifPresent(BlueprintCatalogSelector::validateForUse);
    }

    private static <T> com.mojang.serialization.MapCodec<List<T>> optionalList(
            String name,
            Codec<T> elementCodec) {
        return new StrictOptionalFieldCodec<>(name, elementCodec.listOf())
                .xmap(
                        value -> value.orElse(List.of()),
                        value -> value == null || value.isEmpty() ? Optional.empty() : Optional.of(value));
    }

    public enum MatchSpecificity {
        NONE(0),
        SELECTOR(1),
        TAG(2),
        EXACT(3);

        private final int rank;

        MatchSpecificity(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }
    }
}
