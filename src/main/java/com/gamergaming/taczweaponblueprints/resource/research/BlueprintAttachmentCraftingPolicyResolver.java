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

/** Resolves every attachment without inferring a policy from mechanical stats. */
public final class BlueprintAttachmentCraftingPolicyResolver {
    private BlueprintAttachmentCraftingPolicyResolver() {
    }

    public static Resolution resolve(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            long catalogRevision,
            long researchRevision,
            long automaticRevision) {
        if (research == null || catalog == null || catalogRevision <= 0L
                || researchRevision <= 0L || automaticRevision < 0L) {
            throw new IllegalArgumentException(
                    "attachment crafting policy inputs are invalid or stale");
        }

        Map<ResourceLocation, BlueprintData> attachments = sortedAttachments(catalog);
        long assignmentCount = (long) research.profiles().size() * attachments.size();
        if (assignmentCount > BlueprintCraftingPolicySnapshot.MAX_TOTAL_POLICY_ASSIGNMENTS) {
            throw new IllegalArgumentException(
                    "attachment crafting policy requires " + assignmentCount
                            + " profile-attachment assignments; maximum is "
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
                    Map<ResourceLocation, BlueprintResearchPolicyResolver.RuleSelection>
                            craftingRules = BlueprintResearchPolicyResolver
                                    .craftingRuleSelections(research, profileId, attachments);
                    Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> profilePolicies =
                            new LinkedHashMap<>();
                    attachments.forEach((attachmentId, data) -> profilePolicies.put(
                            attachmentId,
                            resolveOne(
                                    research,
                                    catalog,
                                    profileId,
                                    profile,
                                    attachmentId,
                                    data,
                                    craftingRules.get(attachmentId))));
                    policies.put(profileId, Collections.unmodifiableMap(profilePolicies));
                });

        return new Resolution(
                catalogRevision,
                researchRevision,
                automaticRevision,
                attachments.keySet(),
                policies);
    }

    private static ResolvedBlueprintCraftingPolicy resolveOne(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            BlueprintResearchProfile profile,
            ResourceLocation attachmentId,
            BlueprintData data,
            BlueprintResearchPolicyResolver.RuleSelection craftingRule) {
        if (craftingRule == null) {
            throw new IllegalStateException(
                    "attachment crafting rule selection is missing for " + attachmentId);
        }
        BaseAssignment base = profile.format() < BlueprintResearchProfile.CRAFTING_FORMAT
                ? legacyAssignment(
                        research, catalog, profileId, profile, attachmentId, data)
                : currentAssignment(profile, data);
        return applyCraftingRule(
                research, profileId, attachmentId, craftingRule, base);
    }

    private static BaseAssignment currentAssignment(
            BlueprintResearchProfile profile,
            BlueprintData data) {
        BlueprintAttachmentCraftingPolicy policy = profile.crafting().attachments();
        ProgressionGateRequirements gates = BlueprintCraftingPolicyResolutionSupport
                .craftingGates(profile.progression().gates());
        if (policy.mode() != BlueprintAttachmentCraftingPolicy.Mode.TYPE_MAPPED) {
            return fromAccess(
                    policy.fallback(),
                    gates,
                    BlueprintCraftingPolicySource.CATEGORY_DEFAULT,
                    "attachment_category_default",
                    Set.of());
        }

        String itemType = data.getItemType();
        BlueprintCraftingAccessPolicy mapped = BlueprintAttachmentCraftingPolicy
                .isCanonicalItemType(itemType)
                        ? policy.itemTypePolicies().get(itemType)
                        : null;
        if (mapped != null) {
            return fromAccess(
                    mapped,
                    gates,
                    BlueprintCraftingPolicySource.CATEGORY_DEFAULT,
                    "attachment_item_type_mapping",
                    Set.of());
        }
        return fromAccess(
                policy.fallback(),
                gates,
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                "unknown_attachment_type_fallback",
                Set.of(BlueprintCraftingPolicyWarning.UNKNOWN_ATTACHMENT_TYPE));
    }

    private static BaseAssignment legacyAssignment(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            BlueprintResearchProfile profile,
            ResourceLocation attachmentId,
            BlueprintData data) {
        BlueprintResearchPolicyDefinition definition =
                BlueprintResearchPolicyResolver.definitionFor(
                        research, catalog, profileId, attachmentId);
        if (!definition.treeEnabled()) {
            return migratedCompatibility();
        }
        ProgressionGateRequirements gates = BlueprintCraftingPolicyResolutionSupport
                .craftingGates(profile.progression().gates());
        if (profile.techTree().isEmpty()) {
            return tiered(
                    profile.progression().fallbackTiers().craftingTier(),
                    gates,
                    BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                    "legacy_attachment_profile_fallback");
        }

        var placement = ResearchTechTreePlacementResolver
                .resolveWithAutomaticForProfile(
                        research,
                        profileId,
                        profile.techTree().orElseThrow(),
                        attachmentId,
                        data,
                        null)
                .base()
                .placement();
        if (placement.isEmpty()) {
            return migratedCompatibility();
        }
        return tiered(
                profile.progression()
                        .forAuthoredTier(placement.orElseThrow().tier())
                        .craftingTier(),
                gates,
                BlueprintCraftingPolicySource.AUTHORED_BAND,
                "legacy_authored_attachment_tier_band");
    }

    private static ResolvedBlueprintCraftingPolicy applyCraftingRule(
            BlueprintResearchSnapshot research,
            ResourceLocation profileId,
            ResourceLocation attachmentId,
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
                    .toPolicy(profileId, attachmentId);
        }
        if (base.source() == BlueprintCraftingPolicySource.MIGRATED_COMPATIBILITY) {
            return base.toPolicy(profileId, attachmentId);
        }
        Optional<BlueprintProgressionRuleOverride> progression = selection.selectedRuleId()
                .map(research.rules()::get)
                .flatMap(BlueprintResearchRule::progression);
        if (progression.isEmpty()) {
            return base.toPolicy(profileId, attachmentId);
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
                .toPolicy(profileId, attachmentId);
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

    private static Map<ResourceLocation, BlueprintData> sortedAttachments(
            Map<ResourceLocation, BlueprintData> catalog) {
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
                    if (data.getKind() == BlueprintKind.ATTACHMENT) {
                        if (!id.toString().equals(data.getBpId())) {
                            throw new IllegalArgumentException(
                                    "attachment catalog identity does not match its data: " + id);
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
                        "attachment crafting assignment cannot contain null");
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
                ResourceLocation attachmentId) {
            return new ResolvedBlueprintCraftingPolicy(
                    profileId,
                    attachmentId,
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

    /** Immutable and exhaustively validated Phase 5 attachment-policy result. */
    public record Resolution(
            long catalogRevision,
            long researchRevision,
            long automaticRevision,
            Set<ResourceLocation> attachmentBlueprintIds,
            Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>>
                    policiesByProfile,
            Map<ResourceLocation, BlueprintCraftingPolicySnapshot.ProfileDiagnostics>
                    diagnosticsByProfile) {
        public Resolution(
                long catalogRevision,
                long researchRevision,
                long automaticRevision,
                Set<ResourceLocation> attachmentBlueprintIds,
                Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>>
                        policiesByProfile) {
            this(
                    catalogRevision,
                    researchRevision,
                    automaticRevision,
                    attachmentBlueprintIds,
                    policiesByProfile,
                    diagnostics(policiesByProfile));
        }

        public Resolution {
            BlueprintCraftingPolicySnapshot validated = new BlueprintCraftingPolicySnapshot(
                    catalogRevision,
                    researchRevision,
                    automaticRevision,
                    attachmentBlueprintIds,
                    policiesByProfile,
                    diagnosticsByProfile);
            attachmentBlueprintIds = validated.catalogBlueprintIds();
            policiesByProfile = validated.policiesByProfile();
            diagnosticsByProfile = validated.diagnosticsByProfile();
        }

        public Optional<ResolvedBlueprintCraftingPolicy> policy(
                ResourceLocation profileId,
                ResourceLocation attachmentId) {
            return Optional.ofNullable(
                    policiesByProfile.getOrDefault(profileId, Map.of()).get(attachmentId));
        }

        public boolean matches(long catalog, long research, long automatic) {
            return catalogRevision == catalog
                    && researchRevision == research
                    && automaticRevision == automatic;
        }

        private static Map<ResourceLocation, BlueprintCraftingPolicySnapshot.ProfileDiagnostics>
                diagnostics(
                        Map<ResourceLocation,
                                Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>> policies) {
            if (policies == null) {
                throw new IllegalArgumentException(
                        "attachment crafting policies cannot be null");
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
