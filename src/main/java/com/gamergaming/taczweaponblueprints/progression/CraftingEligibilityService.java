package com.gamergaming.taczweaponblueprints.progression;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.compat.tacz.TaCZWorkbenchMenuBridge;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateEvaluation;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateEvaluator;
import com.gamergaming.taczweaponblueprints.progression.workbench.CraftingWorkbenchAuthority;
import com.gamergaming.taczweaponblueprints.progression.workbench.CraftingWorkbenchTierResolver;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchInteractionMode;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchContext;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintCraftingDisposition;
import com.gamergaming.taczweaponblueprints.resource.research.ProgressionPolicyAccessService;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintCraftingPolicy;
import com.tacz.guns.inventory.GunSmithTableMenu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** One fail-closed authority for native TaCZ recipe visibility and crafting. */
public final class CraftingEligibilityService {
    private static final int DIAGNOSTIC_SAMPLE_LIMIT = 8;

    private CraftingEligibilityService() {
    }

    public static Evaluation evaluate(
            ServerPlayer player,
            GunSmithTableMenu menu,
            ResourceLocation requestedRecipeId) {
        if (player == null || menu == null || requestedRecipeId == null
                || requestedRecipeId.toString().length()
                        > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                || player.containerMenu != menu) {
            return Evaluation.blocked(Status.INVALID_REQUEST);
        }
        boolean blueprintsEnabled = ModConfigs.BLUEPRINT.enableBlueprints.get();
        boolean nativeWorkbench = CraftingWorkbenchTierResolver
                .isNativeCraftingWorkbench(menu.getBlockId());
        ResearchWorkbenchContext workbench = null;
        if (blueprintsEnabled || nativeWorkbench) {
            workbench = authenticatedWorkbench(player, menu);
            if (workbench == null) {
                return Evaluation.blocked(Status.INVALID_WORKSTATION);
            }
        }
        if (!blueprintsEnabled) {
            return Evaluation.permitted();
        }

        ResourceLocation canonicalRecipe = BlueprintDataManager.SERVER
                .getCanonicalRecipeId(requestedRecipeId);
        ResourceLocation blueprintId = BlueprintDataManager.SERVER
                .getBlueprintIdForRecipe(requestedRecipeId);
        Status recipeIdentity = evaluateRecipeIdentity(
                requestedRecipeId, canonicalRecipe, blueprintId);
        if (recipeIdentity != Status.ALLOWED) {
            return Evaluation.blocked(recipeIdentity);
        }
        boolean exempt = BlueprintProgressionAccess.isProgressionExempt(blueprintId);
        IPlayerRecipeData playerData = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        if (!exempt && !learned(playerData, blueprintId, requestedRecipeId)) {
            return Evaluation.blocked(Status.RECIPE_NOT_LEARNED);
        }

        PolicyContext policyContext = preparePolicyContext(player, workbench);
        return policyContext == null
                ? Evaluation.blocked(Status.POLICY_UNAVAILABLE)
                : evaluatePolicy(player, blueprintId, policyContext);
    }

    /** Returns only already-known recipe IDs that this exact open menu may craft. */
    public static Snapshot snapshot(ServerPlayer player, GunSmithTableMenu menu) {
        if (player == null || menu == null || player.containerMenu != menu) {
            TaCZWeaponBlueprints.LOGGER.info(
                    "Workbench recipe diagnostics [server]: snapshot rejected before "
                            + "evaluation (playerPresent={}, menuPresent={}, menuIsOpen={})",
                    player != null,
                    menu != null,
                    player != null && menu != null && player.containerMenu == menu);
            return Snapshot.unavailable();
        }
        BlueprintDataManager.CatalogPublication catalogPublication =
                BlueprintDataManager.SERVER.catalogPublication();
        Map<ResourceLocation, BlueprintData> catalog = catalogPublication.blueprints();
        boolean blueprintsEnabled = ModConfigs.BLUEPRINT.enableBlueprints.get();
        boolean nativeWorkbench = CraftingWorkbenchTierResolver
                .isNativeCraftingWorkbench(menu.getBlockId());
        ResearchWorkbenchContext workbench = null;
        if (blueprintsEnabled || nativeWorkbench) {
            workbench = authenticatedWorkbench(player, menu);
            if (workbench == null) {
                TaCZWeaponBlueprints.LOGGER.info(
                        "Workbench recipe diagnostics [server]: player={}, container={}, "
                                + "workstation={}, nativeWorkbench={}, blueprintsEnabled={}, "
                                + "result=INVALID_WORKSTATION",
                        player.getGameProfile().getName(),
                        menu.containerId,
                        menu.getBlockId(),
                        nativeWorkbench,
                        blueprintsEnabled);
                return Snapshot.unavailable();
            }
        }
        if (!blueprintsEnabled) {
            ResearchFeatureConfigSnapshot currentConfig = ModConfigs.BLUEPRINT
                    .researchFeatureSnapshot();
            CraftingWorkbenchTierResolver.Resolution resolution =
                    CraftingWorkbenchTierResolver.resolve(menu.getBlockId(), currentConfig);
            AccessIdentity identity = AccessIdentity.classic(
                    catalogPublication.revision(),
                    ModConfigs.BLUEPRINT.progressionSnapshot().activeProfileId(),
                    menu.getBlockId(),
                    resolution);
            // Disabled blueprint progression restores TaCZ's complete native
            // recipe view. The classic identity is an explicit unrestricted
            // grant, so this path does not need to approximate that view from
            // the blueprint catalog and accidentally omit a native recipe.
            TaCZWeaponBlueprints.LOGGER.info(
                    "Workbench recipe diagnostics [server]: player={}, container={}, "
                            + "workstation={}, blueprintsEnabled=false, catalog={}, "
                            + "result=ALLOWED_UNRESTRICTED",
                    player.getGameProfile().getName(),
                    menu.containerId,
                    menu.getBlockId(),
                    catalog.size());
            return new Snapshot(Status.ALLOWED, Set.of(), Optional.of(identity));
        }

        PolicyContext policyContext = preparePolicyContext(player, workbench);
        if (policyContext == null) {
            TaCZWeaponBlueprints.LOGGER.info(
                    "Workbench recipe diagnostics [server]: player={}, container={}, "
                            + "workstation={}, catalog={}, result=POLICY_UNAVAILABLE",
                    player.getGameProfile().getName(),
                    menu.containerId,
                    menu.getBlockId(),
                    catalog.size());
            return Snapshot.policyUnavailable();
        }
        catalog = policyContext.catalog();
        TreeSet<ResourceLocation> candidates = new TreeSet<>(
                java.util.Comparator.comparing(ResourceLocation::toString));
        IPlayerRecipeData playerData = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        KnowledgeCollection knowledge = collectKnownRecipes(candidates, playerData, catalog);
        int knownCandidates = candidates.size();
        TreeSet<ResourceLocation> exemptCandidates = new TreeSet<>(
                java.util.Comparator.comparing(ResourceLocation::toString));
        BlueprintProgressionAccess.exemptRecipeIds(
                        ModConfigs.BLUEPRINT.accessSnapshot(), catalog)
                .stream()
                .map(ResourceLocation::tryParse)
                .filter(java.util.Objects::nonNull)
                .forEach(exemptCandidates::add);
        candidates.addAll(exemptCandidates);
        if (candidates.isEmpty()) {
            logSnapshotDiagnostics(
                    player,
                    menu,
                    policyContext,
                    knowledge,
                    knownCandidates,
                    exemptCandidates.size(),
                    candidates,
                    Set.of(),
                    Map.of(),
                    List.of());
            return new Snapshot(
                    Status.ALLOWED, Set.of(), Optional.of(policyContext.identity()));
        }

        TreeSet<String> allowed = new TreeSet<>();
        EnumSet<Status> denialKinds = EnumSet.noneOf(Status.class);
        EnumMap<Status, Integer> denialCounts = new EnumMap<>(Status.class);
        List<String> denialSamples = new ArrayList<>();
        for (ResourceLocation recipeId : candidates) {
            ResourceLocation blueprintId = BlueprintDataManager.SERVER
                    .getBlueprintIdForRecipe(recipeId);
            Evaluation evaluation = blueprintId == null
                    ? Evaluation.blocked(Status.UNKNOWN_RECIPE)
                    : evaluatePolicy(player, blueprintId, policyContext);
            if (evaluation.status() == Status.POLICY_UNAVAILABLE
                    || evaluation.status() == Status.CRAFTING_POLICY_MISSING) {
                denialCounts.merge(evaluation.status(), 1, Integer::sum);
                addDiagnosticSample(
                        denialSamples, recipeId + "=" + evaluation.status());
                logSnapshotDiagnostics(
                        player,
                        menu,
                        policyContext,
                        knowledge,
                        knownCandidates,
                        exemptCandidates.size(),
                        candidates,
                        Set.of(),
                        denialCounts,
                        denialSamples);
                return new Snapshot(
                        evaluation.status(), Set.of(), Optional.of(policyContext.identity()));
            }
            if (evaluation.allowed()) {
                allowed.add(recipeId.toString());
            } else {
                denialKinds.add(evaluation.status());
                denialCounts.merge(evaluation.status(), 1, Integer::sum);
                addDiagnosticSample(
                        denialSamples, recipeId + "=" + evaluation.status());
            }
        }
        logSnapshotDiagnostics(
                player,
                menu,
                policyContext,
                knowledge,
                knownCandidates,
                exemptCandidates.size(),
                candidates,
                allowed,
                denialCounts,
                denialSamples);
        if (allowed.isEmpty() && denialKinds.size() == 1) {
            return new Snapshot(
                    denialKinds.iterator().next(), Set.of(), Optional.of(policyContext.identity()));
        }
        return new Snapshot(Status.ALLOWED, allowed, Optional.of(policyContext.identity()));
    }

    private static ResearchWorkbenchContext authenticatedWorkbench(
            ServerPlayer player,
            GunSmithTableMenu menu) {
        if (player == null || menu == null || player.containerMenu != menu
                || !(menu instanceof TaCZWorkbenchMenuBridge bridge)) {
            return null;
        }
        ResearchWorkbenchContext context = bridge.taczweaponblueprints$workbenchContext()
                .orElse(null);
        return context != null && CraftingWorkbenchAuthority.valid(player, menu, context)
                ? context
                : null;
    }

    private static boolean learned(
            IPlayerRecipeData playerData,
            ResourceLocation blueprintId,
            ResourceLocation recipeId) {
        return playerData != null && (playerData.hasBlueprint(blueprintId.toString())
                || playerData.hasRecipe(recipeId.toString()));
    }

    private static KnowledgeCollection collectKnownRecipes(
            Set<ResourceLocation> destination,
            IPlayerRecipeData playerData,
            Map<ResourceLocation, BlueprintData> catalog) {
        if (playerData == null) {
            return KnowledgeCollection.missingCapability();
        }
        int resolvedBlueprints = 0;
        int resolvedRecipes = 0;
        List<String> unresolvedSamples = new ArrayList<>();
        for (String rawBlueprintId : playerData.getLearnedBlueprints()) {
            ResourceLocation blueprintId = ResourceLocation.tryParse(rawBlueprintId);
            BlueprintData data = blueprintId == null ? null : catalog.get(blueprintId);
            if (data != null) {
                destination.add(data.getRecipeId());
                resolvedBlueprints++;
            } else {
                addDiagnosticSample(unresolvedSamples, "blueprint:" + rawBlueprintId);
            }
        }
        for (String rawRecipeId : playerData.getLearnedRecipes()) {
            ResourceLocation recipeId = ResourceLocation.tryParse(rawRecipeId);
            ResourceLocation canonical = recipeId == null
                    ? null
                    : BlueprintDataManager.SERVER.getCanonicalRecipeId(recipeId);
            if (canonical != null) {
                destination.add(canonical);
                resolvedRecipes++;
            } else {
                addDiagnosticSample(unresolvedSamples, "recipe:" + rawRecipeId);
            }
        }
        return new KnowledgeCollection(
                true,
                playerData.getLearnedBlueprints().size(),
                playerData.getLearnedRecipes().size(),
                resolvedBlueprints,
                resolvedRecipes,
                unresolvedSamples);
    }

    private static void logSnapshotDiagnostics(
            ServerPlayer player,
            GunSmithTableMenu menu,
            PolicyContext context,
            KnowledgeCollection knowledge,
            int knownCandidates,
            int exemptCandidates,
            Set<ResourceLocation> candidates,
            Set<String> allowed,
            Map<Status, Integer> denials,
            List<String> denialSamples) {
        TaCZWeaponBlueprints.LOGGER.info(
                "Workbench recipe diagnostics [server]: player={}, container={}, "
                        + "workstation={}, contextTier={}, resolvedTier={}, "
                        + "unrestricted={}, enforceTiers={}, creative={}, bypassTier={}, "
                        + "bypassGates={}, capabilityPresent={}, learnedBlueprints={} "
                        + "(resolved={}), learnedRecipes={} (resolved={}), catalog={}, "
                        + "policies={}, knownCandidates={}, exemptCandidates={}, candidates={}, "
                        + "allowed={}, denials={}, unresolvedKnowledgeSample={}, "
                        + "candidateSample={}, allowedSample={}, denialSample={}",
                player.getGameProfile().getName(),
                menu.containerId,
                menu.getBlockId(),
                context.workbench().tier(),
                context.workstation().tier(),
                context.workstation().unrestricted(),
                context.config().enforceCraftingTiers(),
                player.isCreative(),
                context.bypassTier(),
                context.bypassGates(),
                knowledge.capabilityPresent(),
                knowledge.learnedBlueprints(),
                knowledge.resolvedBlueprints(),
                knowledge.learnedRecipes(),
                knowledge.resolvedRecipes(),
                context.catalog().size(),
                context.policies().size(),
                knownCandidates,
                exemptCandidates,
                candidates.size(),
                allowed.size(),
                denials,
                knowledge.unresolvedSamples(),
                diagnosticSample(candidates),
                diagnosticSample(allowed),
                denialSamples);
    }

    private static void addDiagnosticSample(List<String> samples, String value) {
        if (samples.size() < DIAGNOSTIC_SAMPLE_LIMIT) {
            samples.add(value);
        }
    }

    private static List<String> diagnosticSample(Iterable<?> values) {
        List<String> samples = new ArrayList<>();
        for (Object value : values) {
            addDiagnosticSample(samples, String.valueOf(value));
        }
        return List.copyOf(samples);
    }

    private record KnowledgeCollection(
            boolean capabilityPresent,
            int learnedBlueprints,
            int learnedRecipes,
            int resolvedBlueprints,
            int resolvedRecipes,
            List<String> unresolvedSamples) {
        private KnowledgeCollection {
            unresolvedSamples = List.copyOf(unresolvedSamples);
        }

        private static KnowledgeCollection missingCapability() {
            return new KnowledgeCollection(false, 0, 0, 0, 0, List.of());
        }
    }

    private static PolicyContext preparePolicyContext(
            ServerPlayer player,
            ResearchWorkbenchContext workbench) {
        var policyAccess = ProgressionPolicyAccessService.acquireCrafting(
                ProgressionPolicyAccessService.Mode.ENSURE_CURRENT).orElse(null);
        if (policyAccess == null) {
            return null;
        }
        ResearchFeatureConfigSnapshot currentConfig = policyAccess.config();
        CraftingWorkbenchTierResolver.Resolution workstation =
                CraftingWorkbenchTierResolver.resolve(
                        workbench.workstationId(), currentConfig);
        boolean bypassTier = player.isCreative()
                && currentConfig.creativeBypassesWorkbenchTiers();
        boolean bypassGates = player.isCreative()
                && currentConfig.creativeBypassesProgressionGates();
        return new PolicyContext(
                policyAccess.profileCraftingPolicies(),
                currentConfig,
                workstation,
                workbench,
                bypassTier,
                bypassGates,
                policyAccess.catalog().blueprints(),
                AccessIdentity.active(
                        policyAccess,
                        workbench.workstationId(),
                        workstation,
                        bypassTier,
                        bypassGates));
    }

    private static Evaluation evaluatePolicy(
            ServerPlayer player,
            ResourceLocation blueprintId,
            PolicyContext context) {
        ResolvedBlueprintCraftingPolicy policy = context.policies().get(blueprintId);
        Status workbenchAccess = evaluateWorkbenchAccess(
                policy,
                context.config().enforceCraftingTiers(),
                context.workbench().tier(),
                context.workstation().unrestricted(),
                context.bypassTier());
        if (workbenchAccess != Status.ALLOWED) {
            return Evaluation.blocked(workbenchAccess);
        }
        if (!context.bypassGates()) {
            ProgressionGateEvaluation gates = ProgressionGateEvaluator.evaluateRequirements(
                    player,
                    blueprintId,
                    policy.gates(),
                    ResearchInteractionMode.CRAFTING,
                    Optional.of(context.workbench()));
            if (gates.status() != ProgressionGateEvaluation.Status.EVALUATED) {
                return Evaluation.blocked(Status.POLICY_UNAVAILABLE);
            }
            if (!gates.satisfied()) {
                return Evaluation.blocked(Status.PROGRESSION_GATE_REQUIRED);
            }
        }
        return Evaluation.permitted();
    }

    /**
     * Pure disposition and ordinary Workbench-level decision shared by the
     * visibility and final craft paths. Progression Gates are evaluated only
     * after this decision succeeds.
     */
    static Status evaluateWorkbenchAccess(
            ResolvedBlueprintCraftingPolicy policy,
            boolean enforceCraftingTiers,
            ResearchWorkbenchTier availableTier,
            boolean unrestrictedWorkbench,
            boolean bypassTier) {
        if (policy == null) {
            return Status.CRAFTING_POLICY_MISSING;
        }
        if (policy.disposition() == BlueprintCraftingDisposition.DISABLED) {
            return Status.CRAFTING_DISABLED;
        }
        if (policy.disposition() == BlueprintCraftingDisposition.UNRESTRICTED
                || !enforceCraftingTiers || unrestrictedWorkbench || bypassTier) {
            return Status.ALLOWED;
        }
        return policy.permitsWorkbench(availableTier)
                ? Status.ALLOWED
                : Status.WORKBENCH_TIER_REQUIRED;
    }

    /**
     * Accepts only the catalog's canonical recipe for an existing blueprint.
     * Duplicate-output aliases may migrate old knowledge, but cannot be used as
     * an alternate direct-packet crafting route.
     */
    static Status evaluateRecipeIdentity(
            ResourceLocation requestedRecipeId,
            ResourceLocation canonicalRecipeId,
            ResourceLocation blueprintId) {
        return requestedRecipeId != null
                        && blueprintId != null
                        && requestedRecipeId.equals(canonicalRecipeId)
                ? Status.ALLOWED
                : Status.UNKNOWN_RECIPE;
    }

    private record PolicyContext(
            Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> policies,
            ResearchFeatureConfigSnapshot config,
            CraftingWorkbenchTierResolver.Resolution workstation,
            ResearchWorkbenchContext workbench,
            boolean bypassTier,
            boolean bypassGates,
            Map<ResourceLocation, BlueprintData> catalog,
            AccessIdentity identity) {
        private PolicyContext {
            if (catalog == null || identity == null) {
                throw new IllegalArgumentException("crafting policy context is incomplete");
            }
        }
    }

    public record Evaluation(Status status) {
        public Evaluation {
            if (status == null) {
                throw new IllegalArgumentException("crafting eligibility status cannot be null");
            }
        }

        public static Evaluation permitted() {
            return new Evaluation(Status.ALLOWED);
        }

        public static Evaluation blocked(Status status) {
            if (status == null || status == Status.ALLOWED) {
                throw new IllegalArgumentException("blocked crafting status is invalid");
            }
            return new Evaluation(status);
        }

        public boolean allowed() {
            return status == Status.ALLOWED;
        }
    }

    public record Snapshot(
            Status status,
            Set<String> allowedRecipeIds,
            Optional<AccessIdentity> accessIdentity) {
        public Snapshot {
            accessIdentity = accessIdentity == null ? Optional.empty() : accessIdentity;
            if (status == null || allowedRecipeIds == null
                    || status != Status.ALLOWED && !allowedRecipeIds.isEmpty()
                    || requiresIdentity(status) && accessIdentity.isEmpty()) {
                throw new IllegalArgumentException("crafting access snapshot is invalid");
            }
            allowedRecipeIds = Set.copyOf(allowedRecipeIds);
        }

        public static Snapshot unavailable() {
            return new Snapshot(Status.INVALID_WORKSTATION, Set.of(), Optional.empty());
        }

        public static Snapshot policyUnavailable() {
            return new Snapshot(Status.POLICY_UNAVAILABLE, Set.of(), Optional.empty());
        }

        public boolean available() {
            return status == Status.ALLOWED;
        }

        private static boolean requiresIdentity(Status status) {
            return status != Status.INVALID_REQUEST
                    && status != Status.INVALID_WORKSTATION
                    && status != Status.POLICY_UNAVAILABLE;
        }
    }

    /** Exact server inputs that produced one crafting allow-list snapshot. */
    public record AccessIdentity(
            long catalogRevision,
            long researchRevision,
            long automaticRevision,
            long evidenceRevision,
            long ammoAssociationRevision,
            long policyPublicationRevision,
            ResourceLocation profileId,
            ResourceLocation workstationId,
            ResearchWorkbenchTier workstationTier,
            boolean unrestrictedWorkbench,
            boolean enforceCraftingTiers,
            boolean bypassTier,
            boolean bypassGates,
            boolean blueprintsEnabled) {
        public AccessIdentity {
            if (catalogRevision < 0L || researchRevision < 0L
                    || automaticRevision < 0L || evidenceRevision < 0L
                    || ammoAssociationRevision < 0L || policyPublicationRevision < 0L
                    || profileId == null || workstationId == null || workstationTier == null
                    || profileId.toString().length()
                            > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                    || workstationId.toString().length()
                            > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                    || blueprintsEnabled && (catalogRevision <= 0L
                            || researchRevision <= 0L || evidenceRevision <= 0L
                            || ammoAssociationRevision <= 0L
                            || policyPublicationRevision <= 0L)
                    || !blueprintsEnabled && (researchRevision != 0L
                            || automaticRevision != 0L || evidenceRevision != 0L
                            || ammoAssociationRevision != 0L
                            || policyPublicationRevision != 0L
                            || unrestrictedWorkbench || enforceCraftingTiers
                            || bypassTier || bypassGates)) {
                throw new IllegalArgumentException("crafting access identity is invalid");
            }
        }

        private static AccessIdentity active(
                ProgressionPolicyAccessService.CraftingContext context,
                ResourceLocation workstationId,
                CraftingWorkbenchTierResolver.Resolution workstation,
                boolean bypassTier,
                boolean bypassGates) {
            var revision = context.revisionIdentity();
            return new AccessIdentity(
                    revision.catalogRevision(),
                    revision.researchRevision(),
                    revision.automaticRevision(),
                    revision.evidenceRevision(),
                    revision.ammoAssociationRevision(),
                    revision.publicationRevision(),
                    context.profileId(),
                    workstationId,
                    workstation.tier(),
                    workstation.unrestricted(),
                    context.config().enforceCraftingTiers(),
                    bypassTier,
                    bypassGates,
                    true);
        }

        private static AccessIdentity classic(
                long catalogRevision,
                ResourceLocation profileId,
                ResourceLocation workstationId,
                CraftingWorkbenchTierResolver.Resolution workstation) {
            return new AccessIdentity(
                    Math.max(0L, catalogRevision),
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    profileId,
                    workstationId,
                    workstation.tier(),
                    false,
                    false,
                    false,
                    false,
                    false);
        }
    }

    public enum Status {
        ALLOWED("message.taczweaponblueprints.crafting.allowed"),
        INVALID_REQUEST("message.taczweaponblueprints.crafting.invalid_request"),
        INVALID_WORKSTATION("message.taczweaponblueprints.crafting.invalid_workstation"),
        UNKNOWN_RECIPE("message.taczweaponblueprints.crafting.unknown_recipe"),
        RECIPE_NOT_LEARNED("message.taczweaponblueprints.crafting.recipe_not_learned"),
        POLICY_UNAVAILABLE("message.taczweaponblueprints.crafting.policy_unavailable"),
        WORKBENCH_TIER_REQUIRED(
                "message.taczweaponblueprints.crafting.workbench_tier_required"),
        PROGRESSION_GATE_REQUIRED(
                "message.taczweaponblueprints.crafting.progression_gate_required"),
        CRAFTING_POLICY_MISSING(
                "message.taczweaponblueprints.crafting.policy_missing"),
        CRAFTING_DISABLED("message.taczweaponblueprints.crafting.disabled");

        private final String translationKey;

        Status(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }
}
