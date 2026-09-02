package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record BlueprintResearchProfile(
        int format,
        boolean journalEnabled,
        JournalVisibility visibility,
        boolean researchEnabled,
        boolean recyclingEnabled,
        boolean allowUnlearnedRecycling,
        int recyclingValue,
        BlueprintResearchCost researchCost,
        boolean requiresDiscovery,
        boolean creativeBypassesCost,
        boolean treeEnabled,
        Map<Domain, DomainPolicy> domainPolicies,
        List<ResourceLocation> entryPointCandidates,
        Map<Domain, List<ResourceLocation>> techEntryPointCandidates,
        Optional<ResourceLocation> techTree,
        BlueprintReverseEngineeringPolicy reverseEngineering) {
    public static final int LEGACY_FORMAT = 1;
    public static final int CURRENT_FORMAT = 2;

    private static final Codec<Integer> FORMAT_CODEC = Codec.INT.flatXmap(
            BlueprintResearchProfile::validateFormat,
            BlueprintResearchProfile::validateFormat);
    private static final Codec<Domain> DOMAIN_CODEC = Codec.STRING.flatXmap(
            BlueprintResearchProfile::parseDomain,
            value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));
    private static final Codec<Map<Domain, List<ResourceLocation>>> TECH_ENTRY_POINTS_CODEC =
            Codec.unboundedMap(
                    DOMAIN_CODEC,
                    BlueprintResearchCodecs.RESOURCE_LOCATION.listOf());
    private static final Codec<Map<Domain, DomainPolicy>> DOMAIN_POLICIES_CODEC =
            Codec.unboundedMap(DOMAIN_CODEC, DomainPolicy.CODEC);

    private static final Codec<BlueprintResearchProfile> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    FORMAT_CODEC.fieldOf("format").forGetter(BlueprintResearchProfile::format),
                    Codec.BOOL.fieldOf("journal_enabled").forGetter(BlueprintResearchProfile::journalEnabled),
                    JournalVisibility.CODEC.fieldOf("visibility").forGetter(BlueprintResearchProfile::visibility),
                    Codec.BOOL.fieldOf("research_enabled").forGetter(BlueprintResearchProfile::researchEnabled),
                    Codec.BOOL.fieldOf("recycling_enabled").forGetter(BlueprintResearchProfile::recyclingEnabled),
                    Codec.BOOL.fieldOf("allow_unlearned_recycling")
                            .forGetter(BlueprintResearchProfile::allowUnlearnedRecycling),
                    BlueprintResearchCodecs.POINTS.fieldOf("recycling_value")
                            .forGetter(BlueprintResearchProfile::recyclingValue),
                    BlueprintResearchCost.CODEC.fieldOf("research_cost")
                            .forGetter(BlueprintResearchProfile::researchCost),
                    Codec.BOOL.fieldOf("requires_discovery")
                            .forGetter(BlueprintResearchProfile::requiresDiscovery),
                    Codec.BOOL.fieldOf("creative_bypasses_cost")
                            .forGetter(BlueprintResearchProfile::creativeBypassesCost),
                    new StrictOptionalFieldCodec<>("tree_enabled", Codec.BOOL)
                            .xmap(value -> value.orElse(true), Optional::of)
                            .forGetter(BlueprintResearchProfile::treeEnabled),
                    new StrictOptionalFieldCodec<>("domain_policies", DOMAIN_POLICIES_CODEC)
                            .xmap(value -> value.orElse(Map.of()), value -> value.isEmpty()
                                    ? Optional.empty()
                                    : Optional.of(value))
                            .forGetter(BlueprintResearchProfile::domainPolicies),
                    new StrictOptionalFieldCodec<>(
                            "entry_point_candidates",
                            BlueprintResearchCodecs.RESOURCE_LOCATION.listOf())
                            .xmap(value -> value.orElse(List.of()), value -> value.isEmpty()
                                    ? Optional.empty()
                                    : Optional.of(value))
                            .forGetter(BlueprintResearchProfile::entryPointCandidates),
                    new StrictOptionalFieldCodec<>(
                            "tech_entry_point_candidates",
                            TECH_ENTRY_POINTS_CODEC)
                            .xmap(value -> value.orElse(Map.of()), value -> value.isEmpty()
                                    ? Optional.empty()
                                    : Optional.of(value))
                            .forGetter(BlueprintResearchProfile::techEntryPointCandidates),
                    new StrictOptionalFieldCodec<>(
                            "tech_tree",
                            BlueprintResearchCodecs.RESOURCE_LOCATION)
                            .forGetter(BlueprintResearchProfile::techTree),
                    new StrictOptionalFieldCodec<>(
                            "reverse_engineering",
                            BlueprintReverseEngineeringPolicy.CODEC)
                            .xmap(
                                    value -> value.orElse(BlueprintReverseEngineeringPolicy.DEFAULT),
                                    Optional::of)
                            .forGetter(BlueprintResearchProfile::reverseEngineering))
                    .apply(instance, BlueprintResearchProfile::new));

    public static final Codec<BlueprintResearchProfile> CODEC = StrictRecordCodec.wrap(
            "blueprint research profile",
            RAW_CODEC.flatXmap(BlueprintResearchProfile::validateProfile, BlueprintResearchProfile::validateProfile),
            "format",
            "journal_enabled",
            "visibility",
            "research_enabled",
            "recycling_enabled",
            "allow_unlearned_recycling",
            "recycling_value",
            "research_cost",
            "requires_discovery",
            "creative_bypasses_cost",
            "tree_enabled",
            "domain_policies",
            "entry_point_candidates",
            "tech_entry_point_candidates",
            "tech_tree",
            "reverse_engineering");

    /** Backwards-compatible constructor for profiles authored before domain policies. */
    public BlueprintResearchProfile(
            int format,
            boolean journalEnabled,
            JournalVisibility visibility,
            boolean researchEnabled,
            boolean recyclingEnabled,
            boolean allowUnlearnedRecycling,
            int recyclingValue,
            BlueprintResearchCost researchCost,
            boolean requiresDiscovery,
            boolean creativeBypassesCost,
            boolean treeEnabled,
            List<ResourceLocation> entryPointCandidates,
            Map<Domain, List<ResourceLocation>> techEntryPointCandidates,
            Optional<ResourceLocation> techTree,
            BlueprintReverseEngineeringPolicy reverseEngineering) {
        this(
                format,
                journalEnabled,
                visibility,
                researchEnabled,
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                creativeBypassesCost,
                treeEnabled,
                defaultDomainPolicies(format),
                entryPointCandidates,
                techEntryPointCandidates,
                techTree,
                reverseEngineering);
    }

    /** Backwards-compatible constructor for profiles authored before reverse engineering. */
    public BlueprintResearchProfile(
            int format,
            boolean journalEnabled,
            JournalVisibility visibility,
            boolean researchEnabled,
            boolean recyclingEnabled,
            boolean allowUnlearnedRecycling,
            int recyclingValue,
            BlueprintResearchCost researchCost,
            boolean requiresDiscovery,
            boolean creativeBypassesCost,
            boolean treeEnabled,
            List<ResourceLocation> entryPointCandidates,
            Map<Domain, List<ResourceLocation>> techEntryPointCandidates,
            Optional<ResourceLocation> techTree) {
        this(
                format,
                journalEnabled,
                visibility,
                researchEnabled,
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                creativeBypassesCost,
                treeEnabled,
                defaultDomainPolicies(format),
                entryPointCandidates,
                techEntryPointCandidates,
                techTree,
                BlueprintReverseEngineeringPolicy.DEFAULT);
    }

    /** Backwards-compatible constructor for profiles authored before per-domain entries. */
    public BlueprintResearchProfile(
            int format,
            boolean journalEnabled,
            JournalVisibility visibility,
            boolean researchEnabled,
            boolean recyclingEnabled,
            boolean allowUnlearnedRecycling,
            int recyclingValue,
            BlueprintResearchCost researchCost,
            boolean requiresDiscovery,
            boolean creativeBypassesCost,
            boolean treeEnabled,
            List<ResourceLocation> entryPointCandidates,
            Optional<ResourceLocation> techTree) {
        this(
                format,
                journalEnabled,
                visibility,
                researchEnabled,
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                creativeBypassesCost,
                treeEnabled,
                defaultDomainPolicies(format),
                entryPointCandidates,
                Map.of(),
                techTree,
                BlueprintReverseEngineeringPolicy.DEFAULT);
    }

    /** Backwards-compatible constructor for profiles authored before Tech Tree selection. */
    public BlueprintResearchProfile(
            int format,
            boolean journalEnabled,
            JournalVisibility visibility,
            boolean researchEnabled,
            boolean recyclingEnabled,
            boolean allowUnlearnedRecycling,
            int recyclingValue,
            BlueprintResearchCost researchCost,
            boolean requiresDiscovery,
            boolean creativeBypassesCost,
            boolean treeEnabled,
            List<ResourceLocation> entryPointCandidates) {
        this(
                format,
                journalEnabled,
                visibility,
                researchEnabled,
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                creativeBypassesCost,
                treeEnabled,
                defaultDomainPolicies(format),
                entryPointCandidates,
                Map.of(),
                Optional.empty(),
                BlueprintReverseEngineeringPolicy.DEFAULT);
    }

    /** Backwards-compatible constructor for profiles authored before tree controls. */
    public BlueprintResearchProfile(
            int format,
            boolean journalEnabled,
            JournalVisibility visibility,
            boolean researchEnabled,
            boolean recyclingEnabled,
            boolean allowUnlearnedRecycling,
            int recyclingValue,
            BlueprintResearchCost researchCost,
            boolean requiresDiscovery,
            boolean creativeBypassesCost) {
        this(
                format,
                journalEnabled,
                visibility,
                researchEnabled,
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                creativeBypassesCost,
                true,
                defaultDomainPolicies(format),
                List.of(),
                Map.of(),
                Optional.empty(),
                BlueprintReverseEngineeringPolicy.DEFAULT);
    }

    public BlueprintResearchProfile {
        if (format < LEGACY_FORMAT || format > CURRENT_FORMAT) {
            throw new IllegalArgumentException("unsupported blueprint research-profile format " + format);
        }
        if (visibility == null || researchCost == null || reverseEngineering == null) {
            throw new IllegalArgumentException(
                    "profile visibility, research cost, and reverse-engineering policy cannot be null");
        }
        if (recyclingValue < 0 || recyclingValue > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            throw new IllegalArgumentException("profile recycling value is outside the supported range");
        }
        EnumMap<Domain, DomainPolicy> normalizedDomainPolicies = new EnumMap<>(Domain.class);
        if (domainPolicies != null) {
            domainPolicies.forEach((domain, policy) -> {
                if (domain == null || policy == null) {
                    throw new IllegalArgumentException("profile domain policies are invalid");
                }
                normalizedDomainPolicies.put(domain, policy);
            });
        }
        domainPolicies = Collections.unmodifiableMap(normalizedDomainPolicies);
        entryPointCandidates = entryPointCandidates == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(entryPointCandidates));
        EnumMap<Domain, List<ResourceLocation>> normalizedTechEntries = new EnumMap<>(Domain.class);
        if (techEntryPointCandidates != null) {
            techEntryPointCandidates.forEach((domain, candidates) -> {
                if (domain == null || candidates == null || candidates.isEmpty()) {
                    throw new IllegalArgumentException(
                            "profile Tech Tree entry-point candidates are invalid");
                }
                normalizedTechEntries.put(
                        domain,
                        List.copyOf(new LinkedHashSet<>(candidates)));
            });
        }
        techEntryPointCandidates = Map.copyOf(normalizedTechEntries);
        techTree = techTree == null ? Optional.empty() : techTree;
        if (entryPointCandidates.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                || entryPointCandidates.stream().anyMatch(id -> id == null
                        || id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH)) {
            throw new IllegalArgumentException("profile entry-point candidates are invalid or oversized");
        }
        long techEntryPointCount = techEntryPointCandidates.values().stream()
                .mapToLong(List::size)
                .sum();
        if (techEntryPointCount > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                || techEntryPointCandidates.values().stream()
                        .flatMap(List::stream)
                        .anyMatch(id -> id == null || id.toString().length()
                                > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH)
                || (!entryPointCandidates.isEmpty()
                        && techEntryPointCandidates.containsKey(Domain.WEAPONS))
                || (!techEntryPointCandidates.isEmpty() && techTree.isEmpty())) {
            throw new IllegalArgumentException(
                    "profile Tech Tree entry-point candidates are invalid or oversized");
        }
        if (techTree.filter(id -> id.toString().length()
                > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH).isPresent()) {
            throw new IllegalArgumentException("profile Tech Tree ID is oversized");
        }
    }

    private static DataResult<Integer> validateFormat(int value) {
        return value >= LEGACY_FORMAT && value <= CURRENT_FORMAT
                ? DataResult.success(value)
                : DataResult.error(() -> "unsupported blueprint research-profile format " + value);
    }

    /** Returns the final tree/research gate for a blueprint domain. */
    public DomainPolicy domainPolicy(Domain domain) {
        if (domain == null) {
            throw new IllegalArgumentException("Research Tech Tree domain cannot be null");
        }
        return format == LEGACY_FORMAT
                ? DomainPolicy.ENABLED
                : domainPolicies.get(domain);
    }

    private static Map<Domain, DomainPolicy> defaultDomainPolicies(int format) {
        if (format != CURRENT_FORMAT) {
            return Map.of();
        }
        EnumMap<Domain, DomainPolicy> defaults = new EnumMap<>(Domain.class);
        for (Domain domain : Domain.values()) {
            defaults.put(domain, DomainPolicy.ENABLED);
        }
        return defaults;
    }

    private static DataResult<Domain> parseDomain(String value) {
        if (value != null) {
            try {
                return DataResult.success(Domain.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Report the bounded domain set below.
            }
        }
        return DataResult.error(() -> "unknown Research Tech Tree domain " + value);
    }

    private static DataResult<BlueprintResearchProfile> validateProfile(BlueprintResearchProfile profile) {
        Optional<String> error = profile.domainPolicyValidationError();
        return error.isEmpty()
                ? DataResult.success(profile)
                : DataResult.error(error::orElseThrow);
    }

    void validateForSnapshot() {
        domainPolicyValidationError().ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });
    }

    private Optional<String> domainPolicyValidationError() {
        if (format == LEGACY_FORMAT && !domainPolicies.isEmpty()) {
            return Optional.of("format-1 profiles cannot declare domain policies");
        }
        if (format == CURRENT_FORMAT
                && !domainPolicies.keySet().equals(EnumSet.allOf(Domain.class))) {
            return Optional.of(
                    "format-2 profiles must declare weapons, attachments, and ammo domain policies");
        }
        return Optional.empty();
    }

    public record DomainPolicy(boolean treeEnabled, boolean researchEnabled) {
        public static final DomainPolicy ENABLED = new DomainPolicy(true, true);

        private static final Codec<DomainPolicy> RAW_CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.BOOL.fieldOf("tree_enabled").forGetter(DomainPolicy::treeEnabled),
                        Codec.BOOL.fieldOf("research_enabled").forGetter(DomainPolicy::researchEnabled))
                        .apply(instance, DomainPolicy::new));

        public static final Codec<DomainPolicy> CODEC = StrictRecordCodec.wrap(
                "blueprint research domain policy",
                RAW_CODEC,
                "tree_enabled",
                "research_enabled");
    }
}
