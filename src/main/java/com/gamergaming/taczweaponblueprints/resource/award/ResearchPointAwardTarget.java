package com.gamergaming.taczweaponblueprints.resource.award;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/** Strict trigger-target selector ordered from exact identity to generic. */
public record ResearchPointAwardTarget(
        List<ResourceLocation> ids,
        List<ResourceLocation> tags,
        List<String> namespaces,
        Optional<CatalogSelector> catalogSelector) {
    private static final Codec<ResearchPointAwardTarget> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResearchPointAwardCodecs.optionalList("ids", ResearchPointAwardCodecs.RESOURCE_LOCATION)
                            .forGetter(ResearchPointAwardTarget::ids),
                    ResearchPointAwardCodecs.optionalList("tags", ResearchPointAwardCodecs.RESOURCE_LOCATION)
                            .forGetter(ResearchPointAwardTarget::tags),
                    ResearchPointAwardCodecs.optionalList("namespaces", ResearchPointAwardCodecs.NAMESPACE)
                            .forGetter(ResearchPointAwardTarget::namespaces),
                    new StrictOptionalFieldCodec<>("catalog_selector", CatalogSelector.CODEC)
                            .forGetter(ResearchPointAwardTarget::catalogSelector))
                    .apply(instance, ResearchPointAwardTarget::new));

    public static final Codec<ResearchPointAwardTarget> CODEC = StrictRecordCodec.wrap(
            "Research Point award target",
            RAW_CODEC.flatXmap(ResearchPointAwardTarget::validate, ResearchPointAwardTarget::validate),
            "ids",
            "tags",
            "namespaces",
            "catalog_selector");

    public ResearchPointAwardTarget {
        ids = immutableUnique(ids);
        tags = immutableUnique(tags);
        namespaces = namespaces == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(namespaces.stream()
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .toList()));
        catalogSelector = catalogSelector == null ? Optional.empty() : catalogSelector;
    }

    public Specificity match(ResearchPointAwardContext context) {
        if (context == null) {
            return Specificity.NONE;
        }
        ResourceLocation targetId = context.targetId().orElse(null);
        if (targetId != null && ids.contains(targetId)) {
            return Specificity.EXACT;
        }
        if (context.targetTags().stream().anyMatch(tags::contains)) {
            return Specificity.TAG;
        }
        if (catalogSelector.filter(selector -> selector.matches(context)).isPresent()) {
            return Specificity.CATALOG_SELECTOR;
        }
        if (targetId != null && namespaces.contains(targetId.getNamespace())) {
            return Specificity.NAMESPACE;
        }
        return Specificity.NONE;
    }

    /** Allocation-free match used by bulk milestone counting. */
    public Specificity match(ResearchPointAwardBlueprintFacts facts) {
        if (facts == null) {
            return Specificity.NONE;
        }
        if (ids.contains(facts.id())) {
            return Specificity.EXACT;
        }
        if (facts.tags().stream().anyMatch(tags::contains)) {
            return Specificity.TAG;
        }
        if (catalogSelector.filter(selector -> selector.matches(facts)).isPresent()) {
            return Specificity.CATALOG_SELECTOR;
        }
        if (namespaces.contains(facts.id().getNamespace())) {
            return Specificity.NAMESPACE;
        }
        return Specificity.NONE;
    }

    public int termCount() {
        return ids.size() + tags.size() + namespaces.size()
                + catalogSelector.map(CatalogSelector::termCount).orElse(0);
    }

    public int indexBindingCount() {
        return ids.size() + tags.size() + namespaces.size()
                + (catalogSelector.isPresent() ? 1 : 0);
    }

    public boolean isGeneric() {
        return termCount() == 0;
    }

    private static DataResult<ResearchPointAwardTarget> validate(ResearchPointAwardTarget target) {
        return target.termCount() <= PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_SELECTOR_TERMS
                ? DataResult.success(target)
                : DataResult.error(() -> "Research Point award target cannot contain more than "
                        + PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_SELECTOR_TERMS + " terms");
    }

    private static <T> List<T> immutableUnique(List<T> values) {
        if (values == null) {
            return List.of();
        }
        if (values.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Research Point award target terms cannot be null");
        }
        return List.copyOf(new LinkedHashSet<>(values));
    }

    public enum Specificity {
        NONE(-1),
        GENERIC(0),
        NAMESPACE(1),
        CATALOG_SELECTOR(2),
        TAG(3),
        EXACT(4);

        private final int rank;

        Specificity(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }
    }

    public record CatalogSelector(
            Optional<String> category,
            Optional<BlueprintKind> kind,
            Optional<String> pathPrefix) {
        private static final Codec<CatalogSelector> RAW_CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        new StrictOptionalFieldCodec<>("category", ResearchPointAwardCodecs.BOUNDED_STRING)
                                .forGetter(CatalogSelector::category),
                        new StrictOptionalFieldCodec<>("kind", BlueprintKind.CODEC)
                                .forGetter(CatalogSelector::kind),
                        new StrictOptionalFieldCodec<>("path_prefix", ResearchPointAwardCodecs.PATH_PREFIX)
                                .forGetter(CatalogSelector::pathPrefix))
                        .apply(instance, CatalogSelector::new));

        public static final Codec<CatalogSelector> CODEC = StrictRecordCodec.wrap(
                "Research Point award catalog selector",
                RAW_CODEC.flatXmap(CatalogSelector::validate, CatalogSelector::validate),
                "category",
                "kind",
                "path_prefix");

        public CatalogSelector {
            category = normalize(category);
            kind = kind == null ? Optional.empty() : kind;
            pathPrefix = normalize(pathPrefix);
        }

        public boolean matches(ResearchPointAwardContext context) {
            ResourceLocation id = context.targetId().orElse(null);
            if (category.isPresent()
                    && !category.equals(context.targetCategory().map(value -> value.toLowerCase(Locale.ROOT)))) {
                return false;
            }
            if (kind.isPresent() && !kind.equals(context.targetKind())) {
                return false;
            }
            return pathPrefix.isEmpty()
                    || (id != null && id.getPath().startsWith(pathPrefix.orElseThrow()));
        }

        boolean matches(ResearchPointAwardBlueprintFacts facts) {
            if (facts == null
                    || category.isPresent() && !category.orElseThrow().equals(
                            facts.category().toLowerCase(Locale.ROOT))
                    || kind.isPresent() && kind.orElseThrow() != facts.kind()) {
                return false;
            }
            return pathPrefix.isEmpty()
                    || facts.id().getPath().startsWith(pathPrefix.orElseThrow());
        }

        public int termCount() {
            return (category.isPresent() ? 1 : 0)
                    + (kind.isPresent() ? 1 : 0)
                    + (pathPrefix.isPresent() ? 1 : 0);
        }

        private static DataResult<CatalogSelector> validate(CatalogSelector selector) {
            return selector.termCount() > 0
                    ? DataResult.success(selector)
                    : DataResult.error(() -> "catalog selector must define at least one term");
        }

        private static Optional<String> normalize(Optional<String> value) {
            return value == null
                    ? Optional.empty()
                    : value.map(term -> term.toLowerCase(Locale.ROOT));
        }
    }
}
