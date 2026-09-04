package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;

import net.minecraft.resources.ResourceLocation;

/** Resolves ammo only after the complete gun crafting projection is available. */
public final class BlueprintAmmoCraftingPolicyResolver {
    private BlueprintAmmoCraftingPolicyResolver() {
    }

    public static Resolution resolve(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            long catalogRevision,
            long researchRevision,
            long automaticRevision,
            BlueprintGunCraftingPolicyResolver.Resolution gunPolicies,
            BlueprintAmmoAssociationSnapshot associations,
            long associationRevision) {
        if (research == null || catalog == null || gunPolicies == null
                || associations == null || catalogRevision <= 0L
                || researchRevision <= 0L || automaticRevision < 0L
                || associationRevision <= 0L
                || !gunPolicies.matches(
                        catalogRevision, researchRevision, automaticRevision)
                || !associations.matches(catalogRevision, associationRevision)) {
            throw new IllegalArgumentException(
                    "ammo crafting policy inputs are invalid or stale");
        }

        Map<ResourceLocation, BlueprintData> guns = sortedKind(catalog, BlueprintKind.GUN);
        Map<ResourceLocation, BlueprintData> ammo = sortedKind(catalog, BlueprintKind.AMMO);
        if (!gunPolicies.gunBlueprintIds().equals(guns.keySet())
                || !associations.gunBlueprintIds().equals(guns.keySet())
                || !associations.ammoBlueprintIds().equals(ammo.keySet())
                || !gunPolicies.policiesByProfile().keySet()
                        .equals(research.profiles().keySet())) {
            throw new IllegalArgumentException(
                    "ammo crafting policy inputs do not cover the current catalog and profiles");
        }
        long assignmentCount = (long) research.profiles().size() * ammo.size();
        if (assignmentCount > BlueprintCraftingPolicySnapshot.MAX_TOTAL_POLICY_ASSIGNMENTS) {
            throw new IllegalArgumentException(
                    "ammo crafting policy requires " + assignmentCount
                            + " profile-ammo assignments; maximum is "
                            + BlueprintCraftingPolicySnapshot.MAX_TOTAL_POLICY_ASSIGNMENTS);
        }

        Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>> policies =
                new LinkedHashMap<>();
        research.profiles().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(profileEntry -> {
                    ResourceLocation profileId = profileEntry.getKey();
                    BlueprintResearchProfile profile = profileEntry.getValue();
                    Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> resolvedGuns =
                            gunPolicies.policiesByProfile().get(profileId);
                    if (resolvedGuns == null
                            || !resolvedGuns.keySet().equals(guns.keySet())) {
                        throw new IllegalArgumentException(
                                "gun crafting policy profile coverage is incomplete");
                    }
                    Map<ResourceLocation, BlueprintResearchPolicyResolver.RuleSelection>
                            craftingRules = BlueprintResearchPolicyResolver
                                    .craftingRuleSelections(research, profileId, ammo);
                    Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> profilePolicies =
                            new LinkedHashMap<>();
                    ammo.forEach((ammoId, data) -> profilePolicies.put(
                            ammoId,
                            resolveOne(
                                    research,
                                    catalog,
                                    profileId,
                                    profile,
                                    ammoId,
                                    data,
                                    resolvedGuns,
                                    associations,
                                    craftingRules.get(ammoId))));
                    policies.put(profileId, Collections.unmodifiableMap(profilePolicies));
                });

        return new Resolution(
                catalogRevision,
                researchRevision,
                automaticRevision,
                associationRevision,
                ammo.keySet(),
                policies);
    }

    private static ResolvedBlueprintCraftingPolicy resolveOne(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            BlueprintResearchProfile profile,
            ResourceLocation ammoId,
            BlueprintData data,
            Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> gunPolicies,
            BlueprintAmmoAssociationSnapshot associations,
            BlueprintResearchPolicyResolver.RuleSelection craftingRule) {
        if (craftingRule == null) {
            throw new IllegalStateException(
                    "ammo crafting rule selection is missing for " + ammoId);
        }
        BaseAssignment base;
        if (profile.format() < BlueprintResearchProfile.CRAFTING_FORMAT) {
            base = legacyAssignment(
                    research, catalog, profileId, profile, ammoId, data);
        } else {
            BlueprintCraftingStrategy strategy = profile.crafting().ammo();
            ProgressionGateRequirements gates = BlueprintCraftingPolicyResolutionSupport
                    .craftingGates(profile.progression().gates());
            base = strategy.mode() == BlueprintCraftingStrategy.Mode.LINKED_WEAPON
                    ? linkedAssignment(
                            strategy,
                            gates,
                            ammoId,
                            gunPolicies,
                            associations)
                    : fromStrategy(
                            strategy,
                            gates,
                            BlueprintCraftingPolicySource.CATEGORY_DEFAULT,
                            "ammo_category_default",
                            Set.of());
        }
        return applyCraftingRule(
                research, profileId, ammoId, craftingRule, base);
    }

    private static BaseAssignment linkedAssignment(
            BlueprintCraftingStrategy strategy,
            ProgressionGateRequirements gates,
            ResourceLocation ammoId,
            Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> gunPolicies,
            BlueprintAmmoAssociationSnapshot associations) {
        Set<ResourceLocation> linkedGuns = associations.gunsForAmmo(ammoId);
        Set<ResourceLocation> ambiguousGuns = associations.ambiguousGunsForAmmo(ammoId);
        Optional<ResearchWorkbenchTier> earliest = linkedGuns.stream()
                .map(gunPolicies::get)
                .map(policy -> {
                    if (policy == null) {
                        throw new IllegalStateException(
                                "linked ammo references a gun without a crafting policy");
                    }
                    return policy;
                })
                .filter(policy -> policy.disposition()
                        == BlueprintCraftingDisposition.TIERED)
                .map(policy -> policy.requiredWorkbenchTier().orElseThrow())
                .min(java.util.Comparator.comparingInt(ResearchWorkbenchTier::level));
        if (earliest.isPresent()) {
            Set<BlueprintCraftingPolicyWarning> warnings = ambiguousGuns.isEmpty()
                    ? Set.of()
                    : Set.of(BlueprintCraftingPolicyWarning.AMBIGUOUS_AMMO_LINK);
            return fromAccess(
                    BlueprintCraftingAccessPolicy.tiered(earliest.orElseThrow()),
                    gates,
                    BlueprintCraftingPolicySource.LINKED_WEAPON,
                    "lowest_tiered_linked_weapon",
                    warnings);
        }

        EnumSet<BlueprintCraftingPolicyWarning> warnings = EnumSet.noneOf(
                BlueprintCraftingPolicyWarning.class);
        if (linkedGuns.isEmpty()) {
            warnings.add(BlueprintCraftingPolicyWarning.AMMO_WITHOUT_LINKED_WEAPON);
        } else {
            warnings.add(
                    BlueprintCraftingPolicyWarning.AMMO_WITHOUT_TIERED_LINKED_WEAPON);
        }
        if (!ambiguousGuns.isEmpty()) {
            warnings.add(BlueprintCraftingPolicyWarning.AMBIGUOUS_AMMO_LINK);
        }
        return fromAccess(
                strategy.fallback().orElseThrow(),
                gates,
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                linkedGuns.isEmpty()
                        ? "ammo_without_trusted_link_fallback"
                        : "ammo_without_tiered_link_fallback",
                warnings);
    }

    private static BaseAssignment legacyAssignment(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            BlueprintResearchProfile profile,
            ResourceLocation ammoId,
            BlueprintData data) {
        BlueprintResearchPolicyDefinition definition =
                BlueprintResearchPolicyResolver.definitionFor(
                        research, catalog, profileId, ammoId);
        if (!definition.treeEnabled()) {
            return migratedCompatibility();
        }
        ProgressionGateRequirements gates = BlueprintCraftingPolicyResolutionSupport
                .craftingGates(profile.progression().gates());
        BaseAssignment base;
        if (profile.techTree().isEmpty()) {
            base = tiered(
                    profile.progression().fallbackTiers().craftingTier(),
                    gates,
                    BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                    "legacy_ammo_profile_fallback");
        } else {
            var placement = ResearchTechTreePlacementResolver
                    .resolveWithAutomaticForProfile(
                            research,
                            profileId,
                            profile.techTree().orElseThrow(),
                            ammoId,
                            data,
                            null)
                    .base()
                    .placement();
            if (placement.isEmpty()) {
                return migratedCompatibility();
            }
            base = tiered(
                    profile.progression()
                            .forAuthoredTier(placement.orElseThrow().tier())
                            .craftingTier(),
                    gates,
                    BlueprintCraftingPolicySource.AUTHORED_BAND,
                    "legacy_authored_ammo_tier_band");
        }
        return base;
    }

    private static ResolvedBlueprintCraftingPolicy applyCraftingRule(
            BlueprintResearchSnapshot research,
            ResourceLocation profileId,
            ResourceLocation ammoId,
            BlueprintResearchPolicyResolver.RuleSelection selection,
            BaseAssignment base) {
        Optional<BlueprintCraftingRuleOverride> rule = selection.selectedRuleId()
                .map(research.rules()::get)
                .flatMap(BlueprintResearchRule::crafting);
        if (rule.isPresent()) {
            BlueprintCraftingRuleOverride override = rule.orElseThrow();
            Optional<BlueprintCraftingAccessPolicy> accessOverride = override.accessPolicy();
            BlueprintCraftingAccessPolicy access = accessOverride.orElse(base.access());
            ProgressionGateRequirements gates = override.gates().orElse(base.gates());
            return base.withRule(
                    access,
                    gates,
                    selection,
                    accessOverride.isPresent())
                    .toPolicy(profileId, ammoId);
        }
        if (base.source() == BlueprintCraftingPolicySource.MIGRATED_COMPATIBILITY) {
            return base.toPolicy(profileId, ammoId);
        }
        Optional<BlueprintProgressionRuleOverride> progression = selection.selectedRuleId()
                .map(research.rules()::get)
                .flatMap(BlueprintResearchRule::progression);
        if (progression.isEmpty()) {
            return base.toPolicy(profileId, ammoId);
        }
        BlueprintProgressionRuleOverride override = progression.orElseThrow();
        Optional<ResearchWorkbenchTier> craftingTier = override.craftingTier();
        BlueprintCraftingAccessPolicy access = craftingTier
                .map(BlueprintCraftingAccessPolicy::tiered)
                .orElse(base.access());
        ProgressionGateRequirements gates = BlueprintCraftingPolicyResolutionSupport
                .craftingGatesOrElse(override.gates(), base.gates());
        return base.withRule(
                access,
                gates,
                selection,
                craftingTier.isPresent())
                .toPolicy(profileId, ammoId);
    }

    private static BaseAssignment fromStrategy(
            BlueprintCraftingStrategy strategy,
            ProgressionGateRequirements gates,
            BlueprintCraftingPolicySource source,
            String reason,
            Set<BlueprintCraftingPolicyWarning> warnings) {
        BlueprintCraftingAccessPolicy access = switch (strategy.mode()) {
            case FIXED -> BlueprintCraftingAccessPolicy.tiered(
                    strategy.workbenchTier().orElseThrow());
            case UNRESTRICTED -> BlueprintCraftingAccessPolicy.UNRESTRICTED;
            case DISABLED -> BlueprintCraftingAccessPolicy.DISABLED;
            case AUTOMATIC_TIER, LINKED_WEAPON -> throw new IllegalArgumentException(
                    "derived ammo crafting strategy requires association evidence");
        };
        return fromAccess(access, gates, source, reason, warnings);
    }

    private static BaseAssignment tiered(
            ResearchWorkbenchTier tier,
            ProgressionGateRequirements gates,
            BlueprintCraftingPolicySource source,
            String reason) {
        return fromAccess(
                BlueprintCraftingAccessPolicy.tiered(tier),
                gates,
                source,
                reason,
                Set.of());
    }

    private static BaseAssignment migratedCompatibility() {
        return fromAccess(
                BlueprintCraftingAccessPolicy.UNRESTRICTED,
                ProgressionGateRequirements.EMPTY,
                BlueprintCraftingPolicySource.MIGRATED_COMPATIBILITY,
                "migrated_compatibility",
                Set.of(BlueprintCraftingPolicyWarning.MIGRATED_COMPATIBILITY));
    }

    private static BaseAssignment fromAccess(
            BlueprintCraftingAccessPolicy access,
            ProgressionGateRequirements gates,
            BlueprintCraftingPolicySource source,
            String reason,
            Set<BlueprintCraftingPolicyWarning> warnings) {
        return new BaseAssignment(
                access,
                gates,
                source,
                Optional.empty(),
                MatchSpecificity.NONE,
                reason,
                warnings);
    }

    private static Map<ResourceLocation, BlueprintData> sortedKind(
            Map<ResourceLocation, BlueprintData> catalog,
            BlueprintKind kind) {
        Map<ResourceLocation, BlueprintData> result = new LinkedHashMap<>();
        catalog.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation id = entry.getKey();
                    BlueprintData data = entry.getValue();
                    if (id == null || data == null) {
                        throw new IllegalArgumentException("blueprint catalog contains null");
                    }
                    if (data.getKind() == kind) {
                        if (!id.toString().equals(data.getBpId())) {
                            throw new IllegalArgumentException(
                                    kind.serializedName()
                                            + " catalog identity does not match its data: " + id);
                        }
                        result.put(id, data);
                    }
                });
        return Collections.unmodifiableMap(result);
    }

    private record BaseAssignment(
            BlueprintCraftingAccessPolicy access,
            ProgressionGateRequirements gates,
            BlueprintCraftingPolicySource source,
            Optional<ResourceLocation> ruleId,
            MatchSpecificity specificity,
            String reason,
            Set<BlueprintCraftingPolicyWarning> warnings) {
        private BaseAssignment {
            if (access == null || gates == null || source == null || ruleId == null
                    || specificity == null || reason == null || warnings == null) {
                throw new IllegalArgumentException(
                        "ammo crafting assignment cannot contain null");
            }
            warnings = warnings.isEmpty()
                    ? Set.of()
                    : Set.copyOf(EnumSet.copyOf(warnings));
        }

        private BaseAssignment withRule(
                BlueprintCraftingAccessPolicy resolvedAccess,
                ProgressionGateRequirements resolvedGates,
                BlueprintResearchPolicyResolver.RuleSelection selection,
                boolean accessWasExplicit) {
            ResourceLocation selected = selection.selectedRuleId().orElseThrow();
            return new BaseAssignment(
                    resolvedAccess,
                    resolvedGates,
                    selection.specificity() == MatchSpecificity.EXACT
                            ? BlueprintCraftingPolicySource.EXACT_RULE
                            : BlueprintCraftingPolicySource.AUTHORED_RULE,
                    Optional.of(selected),
                    selection.specificity(),
                    selection.specificity() == MatchSpecificity.EXACT
                            ? "exact_crafting_rule"
                            : "authored_crafting_rule",
                    accessWasExplicit ? Set.of() : warnings);
        }

        private ResolvedBlueprintCraftingPolicy toPolicy(
                ResourceLocation profileId,
                ResourceLocation ammoId) {
            return new ResolvedBlueprintCraftingPolicy(
                    profileId,
                    ammoId,
                    access.disposition(),
                    access.workbenchTier(),
                    gates,
                    source,
                    ruleId,
                    specificity,
                    Optional.empty(),
                    Optional.empty(),
                    false,
                    reason,
                    warnings);
        }
    }

    /** Immutable and exhaustively validated Phase 4 ammo-policy result. */
    public record Resolution(
            long catalogRevision,
            long researchRevision,
            long automaticRevision,
            long associationRevision,
            Set<ResourceLocation> ammoBlueprintIds,
            Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>>
                    policiesByProfile,
            Map<ResourceLocation, BlueprintCraftingPolicySnapshot.ProfileDiagnostics>
                    diagnosticsByProfile) {
        public Resolution(
                long catalogRevision,
                long researchRevision,
                long automaticRevision,
                long associationRevision,
                Set<ResourceLocation> ammoBlueprintIds,
                Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>>
                        policiesByProfile) {
            this(
                    catalogRevision,
                    researchRevision,
                    automaticRevision,
                    associationRevision,
                    ammoBlueprintIds,
                    policiesByProfile,
                    diagnostics(policiesByProfile));
        }

        public Resolution {
            if (associationRevision <= 0L) {
                throw new IllegalArgumentException(
                        "ammo crafting association revision must be positive");
            }
            BlueprintCraftingPolicySnapshot validated = new BlueprintCraftingPolicySnapshot(
                    catalogRevision,
                    researchRevision,
                    automaticRevision,
                    ammoBlueprintIds,
                    policiesByProfile,
                    diagnosticsByProfile);
            ammoBlueprintIds = validated.catalogBlueprintIds();
            policiesByProfile = validated.policiesByProfile();
            diagnosticsByProfile = validated.diagnosticsByProfile();
        }

        public Optional<ResolvedBlueprintCraftingPolicy> policy(
                ResourceLocation profileId,
                ResourceLocation ammoId) {
            return Optional.ofNullable(
                    policiesByProfile.getOrDefault(profileId, Map.of()).get(ammoId));
        }

        public boolean matches(
                long catalog,
                long research,
                long automatic,
                long association) {
            return catalogRevision == catalog
                    && researchRevision == research
                    && automaticRevision == automatic
                    && associationRevision == association;
        }

        private static Map<ResourceLocation, BlueprintCraftingPolicySnapshot.ProfileDiagnostics>
                diagnostics(
                        Map<ResourceLocation,
                                Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>> policies) {
            if (policies == null) {
                throw new IllegalArgumentException("ammo crafting policies cannot be null");
            }
            Map<ResourceLocation, BlueprintCraftingPolicySnapshot.ProfileDiagnostics> result =
                    new LinkedHashMap<>();
            policies.forEach((profileId, assignments) -> result.put(
                    profileId,
                    BlueprintCraftingPolicySnapshot.ProfileDiagnostics.from(assignments)));
            return result;
        }
    }
}
