package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionCoordinate;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.WeaponRating;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementProposal;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot.TechTreeEntryBinding;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;

import net.minecraft.resources.ResourceLocation;

/** Deterministically resolves additive Tech Tree entry bundles for one blueprint. */
public final class ResearchTechTreePlacementResolver {
    private static final Comparator<MatchedBinding> MATCH_ORDER = Comparator
            .comparingInt((MatchedBinding value) -> value.specificity().rank()).reversed()
            .thenComparing(value -> value.binding().entry().fallback())
            .thenComparing(Comparator.comparingInt(
                    (MatchedBinding value) -> value.binding().bundle().priority()).reversed())
            .thenComparing(value -> value.binding().bundleId().toString())
            .thenComparingInt(value -> value.binding().entryIndex());

    private ResearchTechTreePlacementResolver() {
    }

    public static Selection resolve(
            BlueprintResearchSnapshot snapshot,
            ResourceLocation treeId,
            ResourceLocation blueprintId,
            BlueprintData blueprintData) {
        BlueprintResearchSnapshot stableSnapshot = snapshot == null
                ? BlueprintResearchSnapshot.EMPTY
                : snapshot;
        if (treeId == null || blueprintId == null) {
            return Selection.NONE;
        }

        List<MatchedBinding> matches = matches(
                stableSnapshot.exactTechTreeEntriesFor(treeId, blueprintId),
                MatchSpecificity.EXACT);
        if (matches.isEmpty()) {
            matches = matches(
                    stableSnapshot.tagTechTreeEntriesFor(treeId, blueprintId),
                    MatchSpecificity.TAG);
        }
        if (matches.isEmpty() && blueprintData != null) {
            matches = stableSnapshot.selectorTechTreeEntriesFor(treeId).stream()
                    .filter(binding -> binding.entry().target().selector()
                            .filter(selector -> selector.matches(blueprintId, blueprintData))
                            .isPresent())
                    .map(binding -> new MatchedBinding(binding, MatchSpecificity.SELECTOR))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
        if (matches.isEmpty()) {
            return Selection.NONE;
        }
        matches.sort(MATCH_ORDER);
        MatchedBinding selected = matches.get(0);
        validateKind(blueprintId, blueprintData, selected.binding());

        List<Source> ties = matches.stream()
                .filter(value -> value.specificity() == selected.specificity()
                        && value.binding().entry().fallback()
                                == selected.binding().entry().fallback()
                        && value.binding().bundle().priority()
                                == selected.binding().bundle().priority())
                .map(value -> Source.from(value.binding()))
                .toList();
        ResearchTechTreeEntryBundle.Entry entry = selected.binding().entry();
        return new Selection(
                Optional.of(new Placement(
                        treeId,
                        blueprintId,
                        entry.domain(),
                        entry.lane(),
                        entry.tier(),
                        entry.level(),
                        entry.order(),
                        entry.rating(),
                        entry.tierOverrideReason(),
                        selected.specificity(),
                        originFor(entry, selected.specificity()),
                        selected.binding().bundle().priority(),
                        Source.from(selected.binding()),
                        entry.initialProgressionCoordinate(
                                selected.binding().bundle().format()),
                        selected.binding().bundle().format()
                                == ResearchTechTreeEntryBundle.CURRENT_FORMAT)),
                ties.size() > 1 ? ties : List.of());
    }

    /** Resolves the profile-specific, topologically normalized progression rank. */
    public static Selection resolveForProfile(
            BlueprintResearchSnapshot snapshot,
            ResourceLocation profileId,
            ResourceLocation treeId,
            ResourceLocation blueprintId,
            BlueprintData blueprintData) {
        if (profileId == null || treeId == null) {
            throw new IllegalArgumentException(
                    "Research Tech Tree placement profile and tree cannot be null");
        }
        BlueprintResearchSnapshot stableSnapshot = snapshot == null
                ? BlueprintResearchSnapshot.EMPTY
                : snapshot;
        BlueprintResearchProfile profile = stableSnapshot.profiles().get(profileId);
        if (profile == null
                || profile.techTree().filter(treeId::equals).isEmpty()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree placement profile does not select tree " + treeId);
        }
        Selection base = resolve(stableSnapshot, treeId, blueprintId, blueprintData);
        if (base.placement().isEmpty()) {
            return base;
        }
        Placement placement = base.placement().orElseThrow();
        ProgressionCoordinate coordinate = stableSnapshot
                .techTreeProgressionFor(profileId, blueprintId)
                .orElse(placement.progressionCoordinate());
        return new Selection(
                Optional.of(placement.withProgressionCoordinate(coordinate)),
                base.competingSources());
    }

    /**
     * Applies a revision-validated automatic eligibility snapshot without changing
     * the established authored/fallback resolver. Automatic authority can replace
     * a legacy fallback or place a non-authored gun that has no base placement.
     * The caller remains responsible
     * for obtaining the snapshot from the revision-coupled candidate manager.
     */
    public static EffectiveSelection resolveWithAutomatic(
            BlueprintResearchSnapshot snapshot,
            ResourceLocation treeId,
            ResourceLocation blueprintId,
            BlueprintData blueprintData,
            AutomaticWeaponPlacementCandidateSnapshot candidates) {
        Selection base = resolve(snapshot, treeId, blueprintId, blueprintData);
        return applyAutomatic(treeId, blueprintId, blueprintData, base, candidates);
    }

    /**
     * Profile-aware automatic resolution used by publication. Authored entries use
     * the snapshot's compiled rank, while an eligible automatic proposal supplies
     * the generated presentation coordinate.
     */
    public static EffectiveSelection resolveWithAutomaticForProfile(
            BlueprintResearchSnapshot snapshot,
            ResourceLocation profileId,
            ResourceLocation treeId,
            ResourceLocation blueprintId,
            BlueprintData blueprintData,
            AutomaticWeaponPlacementCandidateSnapshot candidates) {
        Selection base = resolveForProfile(
                snapshot, profileId, treeId, blueprintId, blueprintData);
        return applyAutomatic(treeId, blueprintId, blueprintData, base, candidates);
    }

    private static EffectiveSelection applyAutomatic(
            ResourceLocation treeId,
            ResourceLocation blueprintId,
            BlueprintData blueprintData,
            Selection base,
            AutomaticWeaponPlacementCandidateSnapshot candidates) {
        if (candidates == null) {
            return new EffectiveSelection(base, Optional.empty());
        }
        if (!candidates.treeId().equals(treeId)) {
            throw new IllegalArgumentException(
                    "Automatic placement candidate snapshot belongs to a different tree");
        }
        Optional<AutomaticWeaponPlacementProposal> proposal = candidates.eligibleProposal(blueprintId);
        if (proposal.isEmpty()) {
            return new EffectiveSelection(base, Optional.empty());
        }
        if (blueprintData == null || blueprintData.getKind() != BlueprintKind.GUN) {
            throw new IllegalStateException(
                    "Automatic weapon placement proposal targets a non-gun blueprint");
        }
        Optional<Placement> placement = base.placement();
        if (placement.isPresent()
                && (placement.orElseThrow().origin() != PlacementOrigin.LEGACY_FALLBACK
                        || !PlacementOrigin.AUTOMATIC.outranks(
                                placement.orElseThrow().origin()))) {
            throw new IllegalStateException(
                    "Automatic placement proposal attempted to replace a non-fallback placement");
        }
        return new EffectiveSelection(base, proposal);
    }

    private static PlacementOrigin originFor(
            ResearchTechTreeEntryBundle.Entry entry,
            MatchSpecificity specificity) {
        if (entry.fallback()) {
            return PlacementOrigin.LEGACY_FALLBACK;
        }
        return switch (specificity) {
            case EXACT -> PlacementOrigin.EXACT;
            case TAG -> PlacementOrigin.TAG;
            case SELECTOR -> PlacementOrigin.SELECTOR;
            case NONE -> throw new IllegalArgumentException(
                    "Research Tech Tree placement cannot originate from a non-match");
        };
    }

    private static List<MatchedBinding> matches(
            List<TechTreeEntryBinding> bindings,
            MatchSpecificity specificity) {
        return bindings.stream()
                .map(binding -> new MatchedBinding(binding, specificity))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static void validateKind(
            ResourceLocation blueprintId,
            BlueprintData blueprintData,
            TechTreeEntryBinding binding) {
        if (blueprintData == null) {
            return;
        }
        Domain expected = Domain.forKind(blueprintData.getKind());
        if (binding.entry().domain() != expected) {
            throw new IllegalArgumentException(
                    "Research Tech Tree entry " + Source.from(binding)
                            + " places blueprint " + blueprintId + " in " + binding.entry().domain()
                            + " but its catalog kind requires " + expected);
        }
    }

    public record Selection(
            Optional<Placement> placement,
            List<Source> competingSources) {
        private static final Selection NONE = new Selection(Optional.empty(), List.of());

        public Selection {
            placement = placement == null ? Optional.empty() : placement;
            competingSources = competingSources == null ? List.of() : List.copyOf(competingSources);
        }

        public boolean hasCompetition() {
            return competingSources.size() > 1;
        }
    }

    public record EffectiveSelection(
            Selection base,
            Optional<AutomaticWeaponPlacementProposal> automaticProposal) {
        public EffectiveSelection {
            if (base == null) {
                throw new IllegalArgumentException("Base Research Tech Tree selection cannot be null");
            }
            automaticProposal = automaticProposal == null ? Optional.empty() : automaticProposal;
            if (automaticProposal.isPresent()
                    && base.placement().map(Placement::origin)
                            .filter(origin -> origin != PlacementOrigin.LEGACY_FALLBACK)
                            .isPresent()) {
                throw new IllegalArgumentException(
                        "Automatic placement cannot replace an authored selection");
            }
        }

        public Optional<PlacementOrigin> effectiveOrigin() {
            return automaticProposal.isPresent()
                    ? Optional.of(PlacementOrigin.AUTOMATIC)
                    : base.placement().map(Placement::origin);
        }
    }

    public record Placement(
            ResourceLocation treeId,
            ResourceLocation blueprintId,
            Domain domain,
            ResourceLocation lane,
            Tier tier,
            int level,
            int order,
            Optional<WeaponRating> rating,
            Optional<String> tierOverrideReason,
            MatchSpecificity specificity,
            PlacementOrigin origin,
            int priority,
            Source source,
            ProgressionCoordinate progressionCoordinate,
            boolean explicitRank) {
        public Placement(
                ResourceLocation treeId,
                ResourceLocation blueprintId,
                Domain domain,
                ResourceLocation lane,
                Tier tier,
                int order,
                Optional<WeaponRating> rating,
                Optional<String> tierOverrideReason,
                MatchSpecificity specificity,
                PlacementOrigin origin,
                int priority,
                Source source) {
            this(
                    treeId,
                    blueprintId,
                    domain,
                    lane,
                    tier,
                    0,
                    order,
                    rating,
                    tierOverrideReason,
                    specificity,
                    origin,
                    priority,
                    source,
                    ResearchTechTreeContract.legacyProgressionCoordinate(
                            new ResearchTechTreeContract.ProgressionPosition(
                                    tier, 0, order)),
                    false);
        }

        public Placement {
            if (treeId == null || blueprintId == null || domain == null || lane == null
                    || tier == null || specificity == null || origin == null || source == null
                    || progressionCoordinate == null) {
                throw new IllegalArgumentException("Research Tech Tree placement values cannot be null");
            }
            rating = rating == null ? Optional.empty() : rating;
            tierOverrideReason = tierOverrideReason == null ? Optional.empty() : tierOverrideReason;
            if (level < 0 || level >= ResearchTechTreeContract.MAX_LEVELS_PER_TIER) {
                throw new IllegalArgumentException(
                        "Research Tech Tree placement level is outside the supported range");
            }
        }

        public Placement withProgressionCoordinate(ProgressionCoordinate coordinate) {
            return new Placement(
                    treeId,
                    blueprintId,
                    domain,
                    lane,
                    tier,
                    level,
                    order,
                    rating,
                    tierOverrideReason,
                    specificity,
                    origin,
                    priority,
                    source,
                    coordinate,
                    explicitRank);
        }
    }

    public record Source(ResourceLocation bundleId, int entryIndex) {
        public Source {
            if (bundleId == null || entryIndex < 0) {
                throw new IllegalArgumentException("Research Tech Tree entry source is invalid");
            }
        }

        private static Source from(TechTreeEntryBinding binding) {
            return new Source(binding.bundleId(), binding.entryIndex());
        }

        @Override
        public String toString() {
            return bundleId + "#" + entryIndex;
        }
    }

    private record MatchedBinding(
            TechTreeEntryBinding binding,
            MatchSpecificity specificity) {
    }
}
