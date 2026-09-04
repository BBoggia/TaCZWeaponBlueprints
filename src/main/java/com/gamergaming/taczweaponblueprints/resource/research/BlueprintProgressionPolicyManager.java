package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.progression.ResearchFeatureConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchPolicyShapeSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceManager;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateManager;
import com.gamergaming.taczweaponblueprints.resource.PublicationRevision;

import net.minecraft.resources.ResourceLocation;

/** Owns the last valid atomic research-and-crafting policy publication. */
public final class BlueprintProgressionPolicyManager {
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 512;
    public static final BlueprintProgressionPolicyManager INSTANCE =
            new BlueprintProgressionPolicyManager();

    private volatile Publication publication = Publication.EMPTY;
    private volatile Optional<String> lastFailure = Optional.empty();
    private volatile Optional<RebuildKey> lastFailedKey = Optional.empty();

    BlueprintProgressionPolicyManager() {
    }

    public synchronized boolean rebuild(
            BlueprintResearchSnapshot research,
            long researchRevision,
            Map<ResourceLocation, BlueprintData> catalog,
            long catalogRevision,
            AutomaticWeaponPlacementCandidateManager.Publication automatic,
            AutomaticWeaponEvidenceManager.Publication evidence,
            BlueprintAmmoAssociationManager.Publication associations,
            ResearchFeatureConfigSnapshot config) {
        if (research == null || catalog == null || automatic == null
                || evidence == null || associations == null || config == null) {
            throw new IllegalArgumentException("progression policy manager inputs cannot be null");
        }
        RebuildKey key = new RebuildKey(
                catalogRevision,
                researchRevision,
                automatic.revision(),
                automatic.health().state().name(),
                evidence.revision(),
                evidence.state().name(),
                associations.revision(),
                associations.state().name(),
                config.policyShape());
        if (lastFailedKey.filter(key::equals).isPresent()) {
            return false;
        }
        try {
            Map<ResourceLocation, com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz
                    .AutomaticWeaponPlacementCandidateSnapshot> automaticSnapshots =
                    research.automaticPlacementProfiles().isEmpty()
                            ? Map.of()
                            : automatic.catalogRevision() == catalogRevision
                                    && automatic.researchRevision() == researchRevision
                                    && automatic.health().state()
                                            == AutomaticWeaponPlacementCandidateManager.PublicationState.READY
                                            ? automatic.snapshotsByTree()
                                            : throwUnavailableAutomatic();
            BlueprintProgressionPolicySnapshot snapshot = BlueprintProgressionPolicyResolver.resolve(
                    research,
                    catalog,
                    catalogRevision,
                    researchRevision,
                    automaticSnapshots,
                    config);
            BlueprintCraftingPolicySnapshot craftingSnapshot =
                    BlueprintCraftingPolicyResolver.resolve(
                            research,
                            catalog,
                            catalogRevision,
                            researchRevision,
                            automatic.revision(),
                            automaticSnapshots,
                            evidence,
                            associations,
                            config);
            validateAggregateCoverage(
                    research, catalog, snapshot, craftingSnapshot);
            publication = new Publication(
                    snapshot,
                    craftingSnapshot,
                    Optional.of(config.policyShape()),
                    automatic.revision(),
                    evidence.revision(),
                    associations.revision(),
                    PublicationRevision.next(publication.revision()));
            lastFailure = Optional.empty();
            lastFailedKey = Optional.empty();
            int included = snapshot.diagnosticsByProfile().values().stream()
                    .mapToInt(BlueprintProgressionPolicySnapshot.ProfileDiagnostics::includedCount)
                    .sum();
            int review = snapshot.diagnosticsByProfile().values().stream()
                    .mapToInt(BlueprintProgressionPolicySnapshot.ProfileDiagnostics::reviewFallbackCount)
                    .sum();
            int craftingAssignments = craftingSnapshot.diagnosticsByProfile().values().stream()
                    .mapToInt(BlueprintCraftingPolicySnapshot.ProfileDiagnostics::assignedCount)
                    .sum();
            TaCZWeaponBlueprints.LOGGER.info(
                    "Published blueprint progression policy revision {} for {} profiles: "
                            + "{} research entries, {} crafting assignments, and {} review fallbacks",
                    publication.revision(),
                    snapshot.policiesByProfile().size(),
                    included,
                    craftingAssignments,
                    review);
            return true;
        } catch (RuntimeException exception) {
            String message = boundedMessage(exception);
            lastFailure = Optional.of(message);
            lastFailedKey = Optional.of(key);
            TaCZWeaponBlueprints.LOGGER.error(
                    "Unable to publish blueprint progression policy; preserving valid revision {}: {}",
                    publication.revision(),
                    message,
                    exception);
            return false;
        }
    }

    private static <T> T throwUnavailableAutomatic() {
        throw new IllegalStateException("automatic placement publication is not ready for progression tiers");
    }

    private static void validateAggregateCoverage(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            BlueprintProgressionPolicySnapshot progression,
            BlueprintCraftingPolicySnapshot crafting) {
        if (!progression.matches(crafting.catalogRevision(), crafting.researchRevision())
                || !progression.policiesByProfile().keySet().equals(research.profiles().keySet())
                || !crafting.policiesByProfile().keySet().equals(research.profiles().keySet())
                || !crafting.catalogBlueprintIds().equals(catalog.keySet())) {
            throw new IllegalStateException(
                    "aggregate research and crafting policy coverage is inconsistent");
        }
        for (ResourceLocation profileId : research.profiles().keySet()) {
            java.util.Set<ResourceLocation> researchCoverage = new java.util.HashSet<>(
                    progression.policiesByProfile().get(profileId).keySet());
            researchCoverage.addAll(
                    progression.omissionsByProfile().get(profileId).keySet());
            if (!researchCoverage.equals(crafting.catalogBlueprintIds())) {
                throw new IllegalStateException(
                        "research policy does not account for the complete catalog in profile "
                                + profileId);
            }
        }
    }

    public Publication publication() {
        return publication;
    }

    public Optional<String> lastFailure() {
        return lastFailure;
    }

    public synchronized void clear() {
        publication = Publication.EMPTY;
        lastFailure = Optional.empty();
        lastFailedKey = Optional.empty();
    }

    private static String boundedMessage(RuntimeException exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) {
            value = exception.getClass().getSimpleName();
        }
        value = value.replaceAll("\\s+", " ").trim();
        return value.length() <= MAX_FAILURE_MESSAGE_LENGTH
                ? value
                : value.substring(0, MAX_FAILURE_MESSAGE_LENGTH - 3) + "...";
    }

    public record Publication(
            BlueprintProgressionPolicySnapshot snapshot,
            BlueprintCraftingPolicySnapshot craftingSnapshot,
            Optional<ResearchPolicyShapeSnapshot> policyShape,
            long automaticRevision,
            long evidenceRevision,
            long ammoAssociationRevision,
            long revision) {
        private static final Publication EMPTY = new Publication(
                BlueprintProgressionPolicySnapshot.EMPTY,
                BlueprintCraftingPolicySnapshot.EMPTY,
                Optional.empty(),
                0L,
                0L,
                0L,
                0L);

        public Publication {
            policyShape = policyShape == null ? Optional.empty() : policyShape;
            if (snapshot == null || craftingSnapshot == null
                    || automaticRevision < 0L || evidenceRevision < 0L
                    || ammoAssociationRevision < 0L || revision < 0L
                    || (revision == 0L) != (snapshot == BlueprintProgressionPolicySnapshot.EMPTY)
                    || (revision == 0L) != (craftingSnapshot == BlueprintCraftingPolicySnapshot.EMPTY)
                    || (revision == 0L) != policyShape.isEmpty()
                    || revision == 0L
                            && (automaticRevision != 0L || evidenceRevision != 0L
                                    || ammoAssociationRevision != 0L)
                    || revision > 0L
                            && (evidenceRevision == 0L || ammoAssociationRevision == 0L)
                    || revision > 0L
                            && (!craftingSnapshot.matches(
                                    snapshot.catalogRevision(),
                                    snapshot.researchRevision(),
                                    automaticRevision)
                                    || !snapshot.policiesByProfile().keySet().equals(
                                            craftingSnapshot.policiesByProfile().keySet()))) {
                throw new IllegalArgumentException("progression policy publication is invalid");
            }
            if (revision > 0L) {
                for (ResourceLocation profileId : snapshot.policiesByProfile().keySet()) {
                    java.util.Set<ResourceLocation> coverage = new java.util.HashSet<>(
                            snapshot.policiesByProfile().get(profileId).keySet());
                    coverage.addAll(snapshot.omissionsByProfile().get(profileId).keySet());
                    if (!coverage.equals(craftingSnapshot.catalogBlueprintIds())) {
                        throw new IllegalArgumentException(
                                "published research and crafting catalog coverage differs");
                    }
                }
            }
        }

        public RevisionIdentity identity() {
            if (revision == 0L) {
                throw new IllegalStateException(
                        "empty policy publication has no active revision identity");
            }
            return new RevisionIdentity(
                    snapshot.catalogRevision(),
                    snapshot.researchRevision(),
                    automaticRevision,
                    evidenceRevision,
                    ammoAssociationRevision,
                    policyShape.orElseThrow(),
                    revision);
        }
    }

    /** Revision fingerprint shared by every policy map in one access context. */
    public record RevisionIdentity(
            long catalogRevision,
            long researchRevision,
            long automaticRevision,
            long evidenceRevision,
            long ammoAssociationRevision,
            ResearchPolicyShapeSnapshot policyShape,
            long publicationRevision) {
        public RevisionIdentity {
            if (catalogRevision <= 0L || researchRevision <= 0L
                    || automaticRevision < 0L || evidenceRevision <= 0L
                    || ammoAssociationRevision <= 0L || policyShape == null
                    || publicationRevision <= 0L) {
                throw new IllegalArgumentException(
                        "policy revision identity is invalid");
            }
        }
    }

    private record RebuildKey(
            long catalogRevision,
            long researchRevision,
            long automaticRevision,
            String automaticState,
            long evidenceRevision,
            String evidenceState,
            long ammoAssociationRevision,
            String ammoAssociationState,
            ResearchPolicyShapeSnapshot policyShape) {
        private RebuildKey {
            if (catalogRevision < 0L || researchRevision < 0L
                    || automaticRevision < 0L || automaticState == null
                    || evidenceRevision < 0L || evidenceState == null
                    || ammoAssociationRevision < 0L || ammoAssociationState == null
                    || policyShape == null) {
                throw new IllegalArgumentException("progression rebuild key is invalid");
            }
        }
    }
}
