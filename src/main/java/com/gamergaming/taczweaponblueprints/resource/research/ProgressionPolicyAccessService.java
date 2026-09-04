package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.progression.ResearchFeatureConfigSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceManager;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateManager;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;

import net.minecraft.resources.ResourceLocation;

/** One revision- and config-consistent access point for resolved progression policy. */
public final class ProgressionPolicyAccessService {
    private ProgressionPolicyAccessService() {
    }

    public static Optional<Context> acquire(Mode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("progression policy access mode cannot be null");
        }
        Optional<Context> current = captureCurrent();
        if (current.isPresent() || mode == Mode.CURRENT_ONLY) {
            return current;
        }
        if (!BlueprintDataManager.SERVER.rebuildProgressionPolicy()) {
            return Optional.empty();
        }
        // A rebuild may overlap a reload or config update. Re-read every input
        // and accept the result only when the newly captured set agrees.
        return captureCurrent();
    }

    /**
     * Acquires only the complete crafting projection. This path intentionally
     * does not require an included research-policy entry for the active
     * profile.
     */
    public static Optional<CraftingContext> acquireCrafting(Mode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("progression policy access mode cannot be null");
        }
        Optional<CraftingContext> current = captureCurrentCrafting();
        if (current.isPresent() || mode == Mode.CURRENT_ONLY) {
            return current;
        }
        if (!BlueprintDataManager.SERVER.rebuildProgressionPolicy()) {
            return Optional.empty();
        }
        return captureCurrentCrafting();
    }

    private static Optional<Context> captureCurrent() {
        ResearchFeatureConfigSnapshot config = ModConfigs.BLUEPRINT
                .researchFeatureSnapshot();
        BlueprintDataManager.CatalogPublication catalog =
                BlueprintDataManager.SERVER.catalogPublication();
        BlueprintResearchDataManager.Publication research =
                BlueprintResearchDataManager.INSTANCE.publication();
        AutomaticWeaponPlacementCandidateManager.Publication automatic =
                AutomaticWeaponPlacementCandidateManager.INSTANCE.publication();
        AutomaticWeaponEvidenceManager.Publication evidence =
                AutomaticWeaponEvidenceManager.INSTANCE.publication();
        BlueprintAmmoAssociationManager.Publication associations =
                BlueprintAmmoAssociationManager.INSTANCE.publication();
        BlueprintProgressionPolicyManager.Publication policy =
                BlueprintProgressionPolicyManager.INSTANCE.publication();
        if (catalog.revision() <= 0L || research.revision() <= 0L
                || policy.automaticRevision() != automatic.revision()
                || policy.evidenceRevision() != evidence.revision()
                || !evidence.readyForCatalogRevision(catalog.revision())
                || policy.ammoAssociationRevision() != associations.revision()
                || associations.state()
                        != BlueprintAmmoAssociationManager.PublicationState.READY
                || associations.catalogRevision() != catalog.revision()
                || policy.policyShape().filter(config.policyShape()::equals).isEmpty()
                || !policy.snapshot().matches(catalog.revision(), research.revision())
                || !policy.craftingSnapshot().matches(
                        catalog.revision(), research.revision(), automatic.revision())
                || !policy.craftingSnapshot().catalogBlueprintIds().equals(
                        catalog.blueprints().keySet())) {
            return Optional.empty();
        }
        ResourceLocation profileId = ModConfigs.BLUEPRINT.progressionSnapshot()
                .activeProfileId();
        if (!policy.snapshot().policiesByProfile().containsKey(profileId)
                || !policy.craftingSnapshot().policiesByProfile().containsKey(profileId)) {
            return Optional.empty();
        }
        return Optional.of(new Context(
                catalog,
                research,
                automatic,
                policy,
                config,
                profileId,
                policy.snapshot().policiesByProfile().getOrDefault(profileId, Map.of()),
                policy.craftingSnapshot().policiesByProfile()
                        .getOrDefault(profileId, Map.of()),
                policy.identity()));
    }

    private static Optional<CraftingContext> captureCurrentCrafting() {
        ResearchFeatureConfigSnapshot config = ModConfigs.BLUEPRINT
                .researchFeatureSnapshot();
        BlueprintDataManager.CatalogPublication catalog =
                BlueprintDataManager.SERVER.catalogPublication();
        BlueprintResearchDataManager.Publication research =
                BlueprintResearchDataManager.INSTANCE.publication();
        AutomaticWeaponPlacementCandidateManager.Publication automatic =
                AutomaticWeaponPlacementCandidateManager.INSTANCE.publication();
        AutomaticWeaponEvidenceManager.Publication evidence =
                AutomaticWeaponEvidenceManager.INSTANCE.publication();
        BlueprintAmmoAssociationManager.Publication associations =
                BlueprintAmmoAssociationManager.INSTANCE.publication();
        BlueprintProgressionPolicyManager.Publication policy =
                BlueprintProgressionPolicyManager.INSTANCE.publication();
        if (catalog.revision() <= 0L || research.revision() <= 0L
                || policy.automaticRevision() != automatic.revision()
                || policy.evidenceRevision() != evidence.revision()
                || !evidence.readyForCatalogRevision(catalog.revision())
                || policy.ammoAssociationRevision() != associations.revision()
                || associations.state()
                        != BlueprintAmmoAssociationManager.PublicationState.READY
                || associations.catalogRevision() != catalog.revision()
                || policy.policyShape().filter(config.policyShape()::equals).isEmpty()
                || !policy.craftingSnapshot().matches(
                        catalog.revision(), research.revision(), automatic.revision())
                || !policy.craftingSnapshot().catalogBlueprintIds().equals(
                        catalog.blueprints().keySet())) {
            return Optional.empty();
        }
        ResourceLocation profileId = ModConfigs.BLUEPRINT.progressionSnapshot()
                .activeProfileId();
        if (!policy.craftingSnapshot().policiesByProfile().containsKey(profileId)) {
            return Optional.empty();
        }
        return Optional.of(new CraftingContext(
                catalog,
                policy,
                config,
                profileId,
                policy.craftingSnapshot().policiesByProfile()
                        .getOrDefault(profileId, Map.of()),
                policy.identity()));
    }

    public enum Mode {
        /** Never performs work; suitable for loot generation and presentation. */
        CURRENT_ONLY,
        /** Repairs a missed publication before an authoritative interaction fails. */
        ENSURE_CURRENT
    }

    public record Context(
            BlueprintDataManager.CatalogPublication catalog,
            BlueprintResearchDataManager.Publication research,
            AutomaticWeaponPlacementCandidateManager.Publication automatic,
            BlueprintProgressionPolicyManager.Publication policy,
            ResearchFeatureConfigSnapshot config,
            ResourceLocation profileId,
            Map<ResourceLocation, ResolvedBlueprintProgressionPolicy> profilePolicies,
            Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> profileCraftingPolicies,
            BlueprintProgressionPolicyManager.RevisionIdentity revisionIdentity) {
        public Context {
            if (catalog == null || research == null || automatic == null
                    || policy == null || config == null
                    || profileId == null || profilePolicies == null
                    || profileCraftingPolicies == null || revisionIdentity == null
                    || policy.automaticRevision() != automatic.revision()
                    || policy.policyShape().filter(config.policyShape()::equals).isEmpty()
                    || !policy.snapshot().matches(catalog.revision(), research.revision())
                    || !policy.craftingSnapshot().matches(
                            catalog.revision(), research.revision(), automatic.revision())
                    || !policy.identity().equals(revisionIdentity)
                    || !policy.snapshot().policiesByProfile().containsKey(profileId)
                    || !policy.craftingSnapshot().policiesByProfile()
                            .containsKey(profileId)) {
                throw new IllegalArgumentException("progression policy access context is stale");
            }
            Map<ResourceLocation, ResolvedBlueprintProgressionPolicy> canonicalPolicies =
                    policy.snapshot().policiesByProfile().getOrDefault(profileId, Map.of());
            Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> canonicalCraftingPolicies =
                    policy.craftingSnapshot().policiesByProfile()
                            .getOrDefault(profileId, Map.of());
            if ((profilePolicies != canonicalPolicies
                            && !canonicalPolicies.equals(profilePolicies))
                    || (profileCraftingPolicies != canonicalCraftingPolicies
                            && !canonicalCraftingPolicies.equals(profileCraftingPolicies))) {
                throw new IllegalArgumentException(
                        "progression policy context does not match its active profile");
            }
            // The policy snapshot already owns an immutable map. Retain that
            // canonical instance so every access does not recopy the profile.
            profilePolicies = canonicalPolicies;
            profileCraftingPolicies = canonicalCraftingPolicies;
        }

        public Optional<ResolvedBlueprintProgressionPolicy> policyFor(
                ResourceLocation blueprintId) {
            return Optional.ofNullable(profilePolicies.get(blueprintId));
        }

        public Optional<ResolvedBlueprintCraftingPolicy> craftingPolicyFor(
                ResourceLocation blueprintId) {
            return Optional.ofNullable(profileCraftingPolicies.get(blueprintId));
        }
    }

    /** Revision-consistent authority containing no resolved research policy map. */
    public record CraftingContext(
            BlueprintDataManager.CatalogPublication catalog,
            BlueprintProgressionPolicyManager.Publication policy,
            ResearchFeatureConfigSnapshot config,
            ResourceLocation profileId,
            Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> profileCraftingPolicies,
            BlueprintProgressionPolicyManager.RevisionIdentity revisionIdentity) {
        public CraftingContext {
            if (catalog == null || policy == null || config == null || profileId == null
                    || profileCraftingPolicies == null || revisionIdentity == null
                    || catalog.revision() != revisionIdentity.catalogRevision()
                    || !catalog.blueprints().keySet().equals(
                            policy.craftingSnapshot().catalogBlueprintIds())
                    || policy.policyShape().filter(config.policyShape()::equals).isEmpty()
                    || !policy.craftingSnapshot().matches(
                            revisionIdentity.catalogRevision(),
                            revisionIdentity.researchRevision(),
                            revisionIdentity.automaticRevision())
                    || !policy.identity().equals(revisionIdentity)
                    || !policy.craftingSnapshot().policiesByProfile()
                            .containsKey(profileId)) {
                throw new IllegalArgumentException("crafting policy access context is stale");
            }
            Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> canonicalPolicies =
                    policy.craftingSnapshot().policiesByProfile()
                            .getOrDefault(profileId, Map.of());
            if (profileCraftingPolicies != canonicalPolicies
                    && !canonicalPolicies.equals(profileCraftingPolicies)) {
                throw new IllegalArgumentException(
                        "crafting policy context does not match its active profile");
            }
            profileCraftingPolicies = canonicalPolicies;
        }

        public Optional<ResolvedBlueprintCraftingPolicy> craftingPolicyFor(
                ResourceLocation blueprintId) {
            return Optional.ofNullable(profileCraftingPolicies.get(blueprintId));
        }
    }
}
