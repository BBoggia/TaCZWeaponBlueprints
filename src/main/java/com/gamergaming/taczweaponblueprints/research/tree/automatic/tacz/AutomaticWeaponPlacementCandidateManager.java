package com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisitePlan;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisitePlanner;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponRankFinalizer;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementDiagnostics;
import com.gamergaming.taczweaponblueprints.resource.PublicationRevision;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreePlacementResolver;

import net.minecraft.resources.ResourceLocation;

/** Owns the last complete, revision-coupled automatic-placement eligibility publication. */
public final class AutomaticWeaponPlacementCandidateManager {
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 512;
    public static final AutomaticWeaponPlacementCandidateManager INSTANCE =
            new AutomaticWeaponPlacementCandidateManager();

    private final RebuildStageHook rebuildStageHook;
    private volatile Publication publication = Publication.EMPTY;

    AutomaticWeaponPlacementCandidateManager() {
        this(RebuildStageHook.NONE);
    }

    AutomaticWeaponPlacementCandidateManager(RebuildStageHook rebuildStageHook) {
        if (rebuildStageHook == null) {
            throw new IllegalArgumentException("Automatic placement rebuild hook is invalid");
        }
        this.rebuildStageHook = rebuildStageHook;
    }

    public boolean rebuild(
            BlueprintResearchSnapshot research,
            long researchRevision,
            Map<ResourceLocation, BlueprintData> catalog,
            long catalogRevision,
            AutomaticWeaponEvidenceSnapshot evidence) {
        if (research == null || catalog == null || evidence == null
                || researchRevision <= 0L || catalogRevision <= 0L) {
            throw new IllegalArgumentException("Automatic placement publication inputs are invalid");
        }
        RebuildStage stage = RebuildStage.CLASSIFICATION;
        try {
            rebuildStageHook.before(stage);
            Map<ResourceLocation, AutomaticWeaponCandidateClassification> classifications =
                    new LinkedHashMap<>();
            research.automaticPlacementProfiles().values().stream()
                    .sorted(java.util.Comparator.comparing(value -> value.tree().toString()))
                    .forEach(profile -> classifications.put(
                            profile.tree(),
                            AutomaticWeaponPlacementCandidateClassifier.classify(
                                    research,
                                    researchRevision,
                                    catalog,
                                    catalogRevision,
                                    evidence,
                                    profile)));
            stage = RebuildStage.POSITIONING;
            rebuildStageHook.before(stage);
            Map<ResourceLocation, AutomaticWeaponPlacementCandidateSnapshot> positioned =
                    new LinkedHashMap<>();
            classifications.forEach((treeId, classification) -> positioned.put(
                    treeId,
                    AutomaticWeaponCandidatePositioner.position(
                            classification, research.techTrees().get(treeId))));
            stage = RebuildStage.PREREQUISITE_PLANNING;
            rebuildStageHook.before(stage);
            Map<ResourceLocation, AutomaticWeaponPrerequisitePlan> prerequisitePlans =
                    new LinkedHashMap<>();
            AutomaticWeaponPrerequisitePlanner prerequisitePlanner =
                    new AutomaticWeaponPrerequisitePlanner();
            research.profiles().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            java.util.Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> entry.getValue().techTree().ifPresent(treeId -> {
                        AutomaticWeaponPlacementCandidateSnapshot candidates = positioned.get(treeId);
                        if (candidates != null) {
                            prerequisitePlans.put(entry.getKey(), prerequisitePlanner.plan(
                                    research,
                                    catalog,
                                    entry.getKey(),
                                    candidates,
                                    classifications.get(treeId)));
                        }
                    }));
            stage = RebuildStage.RANK_FINALIZATION;
            rebuildStageHook.before(stage);
            AutomaticWeaponRankFinalizer rankFinalizer =
                    new AutomaticWeaponRankFinalizer();
            Map<ResourceLocation, AutomaticWeaponPlacementCandidateSnapshot> finalized =
                    new LinkedHashMap<>();
            positioned.forEach((treeId, candidates) -> {
                var treePlans = prerequisitePlans.values().stream()
                        .filter(plan -> plan.treeId().equals(treeId))
                        .toList();
                finalized.put(
                        treeId,
                        rankFinalizer.finalizeRanks(
                                candidates,
                                treePlans,
                                authoredRanksForProfiles(
                                        research, catalog, candidates, treePlans)));
            });
            stage = RebuildStage.RANK_RECONCILIATION;
            rebuildStageHook.before(stage);
            prerequisitePlans.replaceAll((profileId, plan) ->
                    plan.withPublishedRanks(finalized.get(plan.treeId())));
            Map<ResourceLocation, AutomaticWeaponPlacementCandidateSnapshot> rebuilt = finalized;
            stage = RebuildStage.PUBLICATION;
            rebuildStageHook.before(stage);
            Publication previous = publication;
            long nextRevision = nextPublicationRevision(previous.revision());
            publication = new Publication(
                    catalogRevision,
                    researchRevision,
                    classifications,
                    rebuilt,
                    prerequisitePlans,
                    nextRevision,
                    PublicationHealth.ready());
            int eligible = rebuilt.values().stream()
                    .mapToInt(snapshot -> snapshot.eligibleProposals().size())
                    .sum();
            int excluded = rebuilt.values().stream()
                    .mapToInt(snapshot -> snapshot.excludedAutomaticCandidates().size())
                    .sum();
            int connected = prerequisitePlans.values().stream()
                    .mapToInt(plan -> plan.prerequisites().values().stream()
                            .mapToInt(java.util.List::size)
                            .sum())
                    .sum();
            int canonicalCoordinates = prerequisitePlans.values().stream()
                    .mapToInt(plan -> plan.branchCoordinates().size())
                    .sum();
            int publishedRanks = prerequisitePlans.values().stream()
                    .mapToInt(plan -> Math.toIntExact(plan.decisions().values().stream()
                            .filter(decision -> decision.publishedRank().isPresent())
                            .count()))
                    .sum();
            TaCZWeaponBlueprints.LOGGER.info(
                    "Published automatic-placement eligibility revision {} for {} trees: "
                            + "{} eligible, {} excluded automatic candidates, {} generated "
                            + "prerequisites, {} canonical branch coordinates, and {} finalized ranks",
                    publication.revision(),
                    rebuilt.size(),
                    eligible,
                    excluded,
                    connected,
                    canonicalCoordinates,
                    publishedRanks);
            return true;
        } catch (RuntimeException exception) {
            invalidateForFailure(catalogRevision, researchRevision, stage, exception);
            Failure failure = publication.health().failure().orElseThrow();
            TaCZWeaponBlueprints.LOGGER.error(
                    "Unable to publish automatic-placement eligibility during {}; "
                            + "invalidated revision {}: {}",
                    failure.stage().serializedName(),
                    publication.revision(),
                    failure.message(),
                    exception);
            return false;
        }
    }

    public Optional<AutomaticWeaponPlacementCandidateSnapshot> snapshotFor(
            ResourceLocation treeId,
            long catalogRevision,
            long researchRevision) {
        Publication current = publication;
        if (treeId == null
                || current.catalogRevision() != catalogRevision
                || current.researchRevision() != researchRevision) {
            return Optional.empty();
        }
        return Optional.ofNullable(current.snapshotsByTree().get(treeId));
    }

    /** Returns the immutable pre-topology evidence behind a published snapshot. */
    public Optional<AutomaticWeaponCandidateClassification> classificationFor(
            ResourceLocation treeId,
            long catalogRevision,
            long researchRevision) {
        Publication current = publication;
        if (treeId == null
                || current.catalogRevision() != catalogRevision
                || current.researchRevision() != researchRevision) {
            return Optional.empty();
        }
        return Optional.ofNullable(current.classificationsByTree().get(treeId));
    }

    private static Map<ResourceLocation, Map<String, Integer>> authoredRanksForProfiles(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            java.util.Collection<AutomaticWeaponPrerequisitePlan> plans) {
        Map<ResourceLocation, Map<String, Integer>> result = new LinkedHashMap<>();
        plans.stream()
                .sorted(java.util.Comparator.comparing(plan -> plan.profileId().toString()))
                .forEach(plan -> {
                    Map<String, Integer> ranks = new LinkedHashMap<>();
                    candidates.authoredBlueprintIds().stream().sorted().forEach(value -> {
                        ResourceLocation blueprintId = ResourceLocation.tryParse(value);
                        BlueprintData data = blueprintId == null ? null : catalog.get(blueprintId);
                        var placement = blueprintId == null || data == null
                                ? Optional.<ResearchTechTreePlacementResolver.Placement>empty()
                                : ResearchTechTreePlacementResolver.resolveForProfile(
                                        research,
                                        plan.profileId(),
                                        candidates.treeId(),
                                        blueprintId,
                                        data).placement();
                        if (placement.filter(valuePlacement ->
                                valuePlacement.origin().authored()).isEmpty()) {
                            throw new IllegalArgumentException(
                                    "Automatic placement authored rank context is incomplete for "
                                            + value);
                        }
                        ranks.put(
                                value,
                                placement.orElseThrow().progressionCoordinate().rank());
                    });
                    result.put(plan.profileId(), Map.copyOf(ranks));
                });
        return Map.copyOf(result);
    }

    public Optional<AutomaticWeaponPrerequisitePlan> prerequisitePlanFor(
            ResourceLocation profileId,
            ResourceLocation treeId,
            long catalogRevision,
            long researchRevision) {
        Publication current = publication;
        if (profileId == null || treeId == null
                || current.catalogRevision() != catalogRevision
                || current.researchRevision() != researchRevision) {
            return Optional.empty();
        }
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                current.snapshotsByTree().get(treeId);
        return Optional.ofNullable(current.prerequisitePlansByProfile().get(profileId))
                .filter(plan -> plan.matches(profileId, candidates));
    }

    /** Returns placement eligibility and prerequisite authority from one volatile read. */
    public Context contextFor(
            ResourceLocation profileId,
            ResourceLocation treeId,
            long catalogRevision,
            long researchRevision) {
        Publication current = publication;
        if (profileId == null || treeId == null
                || current.catalogRevision() != catalogRevision
                || current.researchRevision() != researchRevision) {
            return Context.EMPTY;
        }
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                current.snapshotsByTree().get(treeId);
        AutomaticWeaponPrerequisitePlan prerequisitePlan =
                current.prerequisitePlansByProfile().get(profileId);
        if (candidates == null || prerequisitePlan == null
                || !prerequisitePlan.matches(profileId, candidates)) {
            return Context.EMPTY;
        }
        return new Context(
                Optional.of(candidates), Optional.of(prerequisitePlan));
    }

    /** Returns a read-only explanation derived from one atomic publication read. */
    public Optional<AutomaticWeaponPlacementDiagnostics> diagnosticsFor(
            ResourceLocation profileId,
            ResourceLocation treeId,
            long catalogRevision,
            long researchRevision) {
        Context context = contextFor(
                profileId, treeId, catalogRevision, researchRevision);
        if (context.candidates().isEmpty()
                || context.prerequisitePlan().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(AutomaticWeaponPlacementDiagnostics.create(
                profileId,
                context.candidates().orElseThrow(),
                context.prerequisitePlan().orElseThrow()));
    }

    public Publication publication() {
        return publication;
    }

    private static boolean generatedTargetAllowed(
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            String blueprintId) {
        var proposal = candidates.eligibleProposals().get(blueprintId);
        return proposal != null
                && (!proposal.reviewRequired()
                        || candidates.policy().reviewHandling().createsPrerequisite());
    }

    private static boolean generatedAnchorAllowed(
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            String blueprintId) {
        return candidates.authoredBlueprintIds().contains(blueprintId)
                || generatedTargetAllowed(candidates, blueprintId);
    }

    public void invalidateForRevisions(long catalogRevision, long researchRevision) {
        if (catalogRevision < 0L || researchRevision < 0L) {
            throw new IllegalArgumentException("Automatic placement revisions cannot be negative");
        }
        Publication previous = publication;
        publication = new Publication(
                catalogRevision,
                researchRevision,
                Map.of(),
                Map.of(),
                Map.of(),
                nextPublicationRevision(previous.revision()),
                PublicationHealth.invalidated());
    }

    public void clear() {
        publication = Publication.EMPTY;
    }

    void invalidateForFailure(
            long catalogRevision,
            long researchRevision,
            RebuildStage stage,
            RuntimeException exception) {
        if (catalogRevision < 0L || researchRevision < 0L
                || stage == null || exception == null) {
            throw new IllegalArgumentException(
                    "Automatic placement failure evidence is invalid");
        }
        Publication previous = publication;
        publication = new Publication(
                catalogRevision,
                researchRevision,
                Map.of(),
                Map.of(),
                Map.of(),
                nextPublicationRevision(previous.revision()),
                PublicationHealth.failed(new Failure(
                        stage, boundedFailureMessage(exception))));
    }

    /** Advances the local publication generation while reserving zero for EMPTY. */
    static long nextPublicationRevision(long currentRevision) {
        return PublicationRevision.next(currentRevision);
    }

    private static String boundedFailureMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        message = message.replaceAll("\\s+", " ").trim();
        return message.length() <= MAX_FAILURE_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_FAILURE_MESSAGE_LENGTH - 3) + "...";
    }

    public record Publication(
            long catalogRevision,
            long researchRevision,
            Map<ResourceLocation, AutomaticWeaponCandidateClassification> classificationsByTree,
            Map<ResourceLocation, AutomaticWeaponPlacementCandidateSnapshot> snapshotsByTree,
            Map<ResourceLocation, AutomaticWeaponPrerequisitePlan> prerequisitePlansByProfile,
            long revision,
            PublicationHealth health) {
        private static final Publication EMPTY = new Publication(
                0L, 0L, Map.of(), Map.of(), Map.of(), 0L,
                PublicationHealth.empty());

        /** Compatibility constructor for publications predating health evidence. */
        public Publication(
                long catalogRevision,
                long researchRevision,
                Map<ResourceLocation, AutomaticWeaponCandidateClassification>
                        classificationsByTree,
                Map<ResourceLocation, AutomaticWeaponPlacementCandidateSnapshot>
                        snapshotsByTree,
                Map<ResourceLocation, AutomaticWeaponPrerequisitePlan>
                        prerequisitePlansByProfile,
                long revision) {
            this(
                    catalogRevision,
                    researchRevision,
                    classificationsByTree,
                    snapshotsByTree,
                    prerequisitePlansByProfile,
                    revision,
                    compatibilityHealth(
                            revision,
                            classificationsByTree,
                            snapshotsByTree,
                            prerequisitePlansByProfile));
        }

        /** Compatibility constructor for direct prerequisite-publication fixtures. */
        public Publication(
                long catalogRevision,
                long researchRevision,
                Map<ResourceLocation, AutomaticWeaponPlacementCandidateSnapshot> snapshotsByTree,
                Map<ResourceLocation, AutomaticWeaponPrerequisitePlan> prerequisitePlansByProfile,
                long revision) {
            this(
                    catalogRevision,
                    researchRevision,
                    Map.of(),
                    snapshotsByTree,
                    prerequisitePlansByProfile,
                    revision);
        }

        public Publication {
            if (catalogRevision < 0L || researchRevision < 0L || revision < 0L
                    || classificationsByTree == null || snapshotsByTree == null
                    || prerequisitePlansByProfile == null || health == null) {
                throw new IllegalArgumentException("Automatic placement publication is invalid");
            }
            Map<ResourceLocation, AutomaticWeaponCandidateClassification> classificationCopy =
                    new LinkedHashMap<>();
            classificationsByTree.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            java.util.Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> {
                        AutomaticWeaponCandidateClassification classification = entry.getValue();
                        if (entry.getKey() == null || classification == null
                                || !entry.getKey().equals(classification.treeId())
                                || classification.catalogRevision() != catalogRevision
                                || classification.researchRevision() != researchRevision) {
                            throw new IllegalArgumentException(
                                    "Automatic placement publication classification is inconsistent");
                        }
                        classificationCopy.put(entry.getKey(), classification);
                    });
            Map<ResourceLocation, AutomaticWeaponCandidateClassification>
                    immutableClassifications = Collections.unmodifiableMap(classificationCopy);
            classificationsByTree = immutableClassifications;
            Map<ResourceLocation, AutomaticWeaponPlacementCandidateSnapshot> copy =
                    new LinkedHashMap<>();
            snapshotsByTree.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            java.util.Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> {
                        AutomaticWeaponCandidateClassification classification =
                                immutableClassifications.get(entry.getKey());
                        if (entry.getKey() == null || entry.getValue() == null
                                || !entry.getKey().equals(entry.getValue().treeId())
                                || entry.getValue().catalogRevision() != catalogRevision
                                || entry.getValue().researchRevision() != researchRevision
                                || classification != null
                                        && (entry.getValue().catalogWeaponCount()
                                                        != classification.catalogWeaponCount()
                                                || !entry.getValue().eligibleProposals().keySet()
                                                        .equals(classification
                                                                .eligibleProposals().keySet())
                                                || !entry.getValue()
                                                        .excludedAutomaticCandidates().equals(
                                                                classification
                                                                        .excludedAutomaticCandidates())
                                                || !entry.getValue().authoredBlueprintIds().equals(
                                                        classification.authoredBlueprintIds()))) {
                            throw new IllegalArgumentException(
                                    "Automatic placement publication snapshot is inconsistent");
                        }
                        copy.put(entry.getKey(), entry.getValue());
                    });
            Map<ResourceLocation, AutomaticWeaponPlacementCandidateSnapshot> immutableSnapshots =
                    Collections.unmodifiableMap(copy);
            if (!immutableClassifications.isEmpty()
                    && !immutableSnapshots.keySet().equals(immutableClassifications.keySet())) {
                throw new IllegalArgumentException(
                        "Automatic placement publication stages do not cover the same trees");
            }
            snapshotsByTree = immutableSnapshots;
            Map<ResourceLocation, AutomaticWeaponPrerequisitePlan> planCopy =
                    new LinkedHashMap<>();
            prerequisitePlansByProfile.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            java.util.Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> {
                        AutomaticWeaponPrerequisitePlan plan = entry.getValue();
                        AutomaticWeaponPlacementCandidateSnapshot candidates = plan == null
                                ? null
                                : immutableSnapshots.get(plan.treeId());
                        if (entry.getKey() == null || plan == null || candidates == null
                                || !plan.matches(entry.getKey(), candidates)
                                || plan.prerequisites().values().stream()
                                        .anyMatch(values -> values.size()
                                                > candidates.policy()
                                                        .maxGeneratedPrerequisites())
                                || !plan.prerequisites().keySet().stream()
                                        .map(ResourceLocation::toString)
                                        .allMatch(value -> generatedTargetAllowed(
                                                candidates, value))
                                || !plan.omittedCandidates().keySet().stream()
                                        .map(ResourceLocation::toString)
                                        .allMatch(candidates.eligibleProposals()::containsKey)
                                || !plan.prerequisites().values().stream()
                                        .flatMap(java.util.Collection::stream)
                                        .map(ResourceLocation::toString)
                                        .allMatch(value -> generatedAnchorAllowed(
                                                candidates, value))
                                || !completeCurrentConnectedPublication(
                                        immutableClassifications,
                                        entry.getKey(),
                                        candidates,
                                        plan)) {
                            throw new IllegalArgumentException(
                                    "Automatic prerequisite publication plan is inconsistent");
                        }
                        planCopy.put(entry.getKey(), plan);
                    });
            prerequisitePlansByProfile = Collections.unmodifiableMap(planCopy);
            boolean emptyPayload = classificationsByTree.isEmpty()
                    && snapshotsByTree.isEmpty()
                    && prerequisitePlansByProfile.isEmpty();
            if (health.state() == PublicationState.EMPTY
                    ? revision != 0L || !emptyPayload
                    : revision == 0L
                            || (health.state() == PublicationState.INVALIDATED
                                    || health.state() == PublicationState.FAILED)
                                    && !emptyPayload) {
                throw new IllegalArgumentException(
                        "Automatic placement publication health is inconsistent");
            }
        }
    }

    private static PublicationHealth compatibilityHealth(
            long revision,
            Map<?, ?> classifications,
            Map<?, ?> snapshots,
            Map<?, ?> plans) {
        if (revision == 0L) {
            return PublicationHealth.empty();
        }
        return classifications != null && snapshots != null && plans != null
                && classifications.isEmpty() && snapshots.isEmpty() && plans.isEmpty()
                        ? PublicationHealth.invalidated()
                        : PublicationHealth.ready();
    }

    public enum RebuildStage {
        CLASSIFICATION("classification"),
        POSITIONING("positioning"),
        PREREQUISITE_PLANNING("prerequisite_planning"),
        RANK_FINALIZATION("rank_finalization"),
        RANK_RECONCILIATION("rank_reconciliation"),
        PUBLICATION("publication");

        private final String serializedName;

        RebuildStage(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    @FunctionalInterface
    interface RebuildStageHook {
        RebuildStageHook NONE = stage -> {
        };

        void before(RebuildStage stage);
    }

    public enum PublicationState {
        EMPTY("empty"),
        INVALIDATED("awaiting_rebuild"),
        READY("ready"),
        FAILED("failed");

        private final String serializedName;

        PublicationState(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    public record Failure(RebuildStage stage, String message) {
        public Failure {
            if (stage == null || message == null || message.isBlank()
                    || !message.equals(message.trim())
                    || message.length() > MAX_FAILURE_MESSAGE_LENGTH) {
                throw new IllegalArgumentException(
                        "Automatic placement failure is invalid");
            }
        }
    }

    public record PublicationHealth(
            PublicationState state,
            Optional<Failure> failure) {
        public PublicationHealth {
            failure = failure == null ? Optional.empty() : failure;
            if (state == null
                    || (state == PublicationState.FAILED) != failure.isPresent()) {
                throw new IllegalArgumentException(
                        "Automatic placement publication health is invalid");
            }
        }

        private static PublicationHealth empty() {
            return new PublicationHealth(PublicationState.EMPTY, Optional.empty());
        }

        private static PublicationHealth invalidated() {
            return new PublicationHealth(
                    PublicationState.INVALIDATED, Optional.empty());
        }

        private static PublicationHealth ready() {
            return new PublicationHealth(PublicationState.READY, Optional.empty());
        }

        private static PublicationHealth failed(Failure failure) {
            return new PublicationHealth(
                    PublicationState.FAILED, Optional.of(failure));
        }
    }

    private static boolean completeCurrentConnectedPublication(
            Map<ResourceLocation, AutomaticWeaponCandidateClassification> classifications,
            ResourceLocation profileId,
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            AutomaticWeaponPrerequisitePlan plan) {
        if (classifications.isEmpty()
                || !plan.mode().createsPrerequisite()
                || !candidates.policy().usesDynamicLayers()) {
            return true;
        }
        return AutomaticWeaponPlacementDiagnostics.create(
                profileId, candidates, plan).publicationSummary().complete();
    }

    public record Context(
            Optional<AutomaticWeaponPlacementCandidateSnapshot> candidates,
            Optional<AutomaticWeaponPrerequisitePlan> prerequisitePlan) {
        private static final Context EMPTY = new Context(Optional.empty(), Optional.empty());

        public Context {
            candidates = candidates == null ? Optional.empty() : candidates;
            prerequisitePlan = prerequisitePlan == null
                    ? Optional.empty()
                    : prerequisitePlan;
            if (candidates.isPresent() != prerequisitePlan.isPresent()
                    || candidates.isPresent()
                            && !prerequisitePlan.orElseThrow().matches(
                                    prerequisitePlan.orElseThrow().profileId(),
                                    candidates.orElseThrow())) {
                throw new IllegalArgumentException(
                        "Automatic placement context is inconsistent");
            }
        }
    }
}
