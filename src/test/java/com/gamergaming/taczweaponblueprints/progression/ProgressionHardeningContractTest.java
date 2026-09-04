package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Source-boundary regressions for server authority and publication recovery seams. */
class ProgressionHardeningContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));

    @Test
    void researchAuthorityRequiresTheExactOpenMenuSession() throws IOException {
        String authority = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/progression/workbench/"
                        + "ResearchWorkbenchAuthority.java");
        String menu = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/menu/ResearchBenchMenu.java");

        assertTrue(authority.contains("context.hasSession()"));
        assertTrue(authority.contains("player.containerMenu instanceof ResearchBenchMenu menu"));
        assertTrue(authority.contains("menu.authorizesResearchContext(player, context)"));
        assertTrue(menu.contains("filter(context::equals)"));
    }

    @Test
    void routeEligibilityRejectsOversizedPublicInputsBeforeAllocation() throws IOException {
        String eligibility = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/progression/eligibility/"
                        + "ResearchRouteEligibilityService.java");

        int limit = eligibility.indexOf(
                "> ResearchPathUnlockPlanner.MAX_UNLOCKS_PER_PURCHASE");
        int allocation = eligibility.indexOf(
                "new ArrayList<>(pendingBlueprintIds.size())");
        assertTrue(limit >= 0 && limit < allocation);
    }

    @Test
    void failedCatalogRefreshReconcilesDerivedPublicationsBeforePlayerSync()
            throws IOException {
        String events = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/event/"
                        + "BlueprintResourceEvents.java");
        String catalog = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/resource/"
                        + "BlueprintDataManager.java");

        assertTrue(events.indexOf("rebuildDerivedPublicationsFromRetainedCatalog()")
                < events.indexOf("event.getPlayers().forEach(player -> {", events.indexOf("} else {")));
        assertTrue(catalog.contains("AutomaticWeaponPlacementCandidateManager.INSTANCE.rebuild("));
        assertTrue(catalog.contains("return rebuildProgressionPolicy();"));
    }

    @Test
    void advancementCriterionGrantsAndRevocationsBothInvalidateGatePreviews()
            throws IOException {
        String events = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/event/"
                        + "ResearchPointAwardEvents.java");

        assertTrue(events.contains("AdvancementEvent.AdvancementProgressEvent event"));
        assertTrue(events.contains("BlueprintProgressionSyncScheduler.markDirty(player)"));
    }

    @Test
    void multiAlternativeGatesUseAGroupLevelPlayerMessage() throws IOException {
        String eligibility = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/progression/eligibility/"
                        + "ResearchRouteEligibilityService.java");
        String language = source(
                "src/main/resources/assets/taczweaponblueprints/lang/en_us.json");

        assertTrue(eligibility.contains("applicable.size() > 1"));
        assertTrue(eligibility.contains("ANY_GATE_ALTERNATIVE_MESSAGE_KEY"));
        assertTrue(language.contains(
                "gui.taczweaponblueprints.research_bench.tree.selection.progression_gate_any_of"));
    }

    @Test
    void pointOnlySynchronizationDoesNotRebuildCraftingAccess() throws IOException {
        String network = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/network/NetworkHandler.java");
        int method = network.indexOf("private static void sendPlayerProgressionData(");
        int nextMethod = network.indexOf("private static void sendJournalData(", method);
        String body = network.substring(method, nextMethod);

        assertTrue(body.contains("if (!treeKnownUnchanged || includeSupplementalProgression)"));
        assertTrue(body.indexOf("if (!treeKnownUnchanged || includeSupplementalProgression)")
                < body.indexOf("refreshOpenWorkstation(player)"));
    }

    @Test
    void craftingAccessRefreshesBeforeBroaderPublicationsAndAfterGameModeChanges()
            throws IOException {
        String network = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/network/NetworkHandler.java");
        String events = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/event/ModEventHandler.java");

        int progression = network.indexOf("public static void syncPlayerProgressionData(");
        int pointBalance = network.indexOf("public static void syncPlayerPointBalance(");
        String progressionBody = network.substring(progression, pointBalance);
        assertTrue(progressionBody.indexOf("refreshOpenCraftingWorkbench(player)")
                < progressionBody.indexOf("player.getCapability("));

        int allData = network.indexOf("public static void syncAllPlayerData(");
        int nextMethod = network.indexOf("public static void sendResearchBenchPreview(", allData);
        String allDataBody = network.substring(allData, nextMethod);
        assertTrue(allDataBody.indexOf("refreshOpenCraftingWorkbench(player)")
                < allDataBody.indexOf("syncBlueprintData(player)"));

        assertTrue(events.contains("PlayerEvent.PlayerChangeGameModeEvent"));
        assertTrue(events.contains("BlueprintProgressionSyncScheduler.markDirty(serverPlayer)"));
    }

    @Test
    void liveServerConfigUpdatesClassifyPolicyChangesAndActionsCanRepairAMissedCallback()
            throws IOException {
        String config = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/compat/fzzy_config/"
                        + "BlueprintConfig.java");
        String crafting = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/progression/"
                        + "CraftingEligibilityService.java");

        assertTrue(config.contains("onUpdateServer(ServerUpdateContext context)"));
        assertTrue(config.contains("policyShapeChanged"));
        assertTrue(config.contains("BlueprintDataManager.SERVER.rebuildProgressionPolicy()"));
        assertTrue(config.contains("refreshOnlinePlayers(context.getServer())"));
        assertTrue(crafting.contains("ProgressionPolicyAccessService.acquireCrafting("));
        assertTrue(crafting.contains("ProgressionPolicyAccessService.Mode.ENSURE_CURRENT"));
        int policyPreparation = crafting.indexOf("private static PolicyContext preparePolicyContext(");
        int policyEvaluation = crafting.indexOf("private static Evaluation evaluatePolicy(");
        String preparationBody = crafting.substring(policyPreparation, policyEvaluation);
        assertTrue(preparationBody.indexOf("ProgressionPolicyAccessService.acquireCrafting(")
                < preparationBody.indexOf("CraftingWorkbenchTierResolver.resolve("));
    }

    @Test
    void craftingScreenRefreshesAreDeferredCoalescedAndWidgetSafe() throws IOException {
        String screen = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/mixin/"
                        + "GunSmithTableScreenMixin.java");

        assertTrue(screen.contains("taczweaponblueprints$recipeRefreshQueued = true"));
        assertTrue(screen.contains("taczweaponblueprints$rebuildingRecipes"));
        assertTrue(screen.contains("this.rebuildWidgets()"));
        assertTrue(!screen.contains("this.init()"));
        assertTrue(screen.indexOf("taczweaponblueprints$rebuildRecipesIfQueued();")
                > screen.indexOf("private void refreshRecipesWhenUnlockStateChanges("));
    }

    @Test
    void logoutClearsEveryChunkAccumulatorAndTheClientCatalog() throws IOException {
        String events = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/client/"
                        + "ClientConnectionEvents.java");

        assertTrue(events.contains("SyncBlueprintDataPacket.clearClientState()"));
        assertTrue(events.contains("SyncPlayerRecipeDataPacket.clearClientState()"));
        assertTrue(events.contains("SyncPlayerProgressionPacket.clearClientState()"));
        assertTrue(events.contains("SyncPlayerSupplementalProgressionPacket.clearClientState()"));
        assertTrue(events.contains("SyncBlueprintJournalPacket.clearClientState()"));
        assertTrue(events.contains("SyncResearchTreePacket.clearClientState()"));
        assertTrue(events.contains("SyncCraftingAccessPacket.clearClientState()"));
        assertTrue(events.contains("BlueprintDataManager.CLIENT.clear()"));
        assertTrue(events.contains("ClientBlueprintCatalog.invalidateCreativeTabs()"));
    }

    @Test
    void allRuntimePolicyConsumersUseTheSharedFreshnessAuthority() throws IOException {
        for (String path : java.util.List.of(
                "src/main/java/com/gamergaming/taczweaponblueprints/progression/"
                        + "CraftingEligibilityService.java",
                "src/main/java/com/gamergaming/taczweaponblueprints/progression/eligibility/"
                        + "ResearchRouteEligibilityService.java",
                "src/main/java/com/gamergaming/taczweaponblueprints/progression/fragment/"
                        + "BlueprintFragmentAnalysisService.java",
                "src/main/java/com/gamergaming/taczweaponblueprints/progression/fragment/"
                        + "BlueprintFragmentResearchService.java",
                "src/main/java/com/gamergaming/taczweaponblueprints/loot/"
                        + "BlueprintFragmentLootResolver.java",
                "src/main/java/com/gamergaming/taczweaponblueprints/menu/ResearchBenchMenu.java",
                "src/main/java/com/gamergaming/taczweaponblueprints/network/NetworkHandler.java")) {
            String policyAccess = source(path);
            assertTrue(policyAccess.contains("ProgressionPolicyAccessService.acquire(")
                    || policyAccess.contains("ProgressionPolicyAccessService.acquireCrafting("), path);
        }
    }

    @Test
    void policyRecoveryRereadsInputsAndSuppressesRepeatedFailedKeys() throws IOException {
        String access = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/resource/research/"
                        + "ProgressionPolicyAccessService.java");
        String manager = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/resource/research/"
                        + "BlueprintProgressionPolicyManager.java");

        assertTrue(access.contains("CURRENT_ONLY"));
        assertTrue(access.contains("ENSURE_CURRENT"));
        assertTrue(access.contains(
                "policy.automaticRevision() != automatic.revision()"));
        assertTrue(access.contains(
                "policy.evidenceRevision() != evidence.revision()"));
        assertTrue(access.contains(
                "policy.ammoAssociationRevision() != associations.revision()"));
        assertTrue(access.contains("policy.craftingSnapshot().matches("));
        assertTrue(access.contains("profileCraftingPolicies"));
        assertTrue(access.indexOf("BlueprintDataManager.SERVER.rebuildProgressionPolicy()")
                < access.lastIndexOf("return captureCurrent();"));
        assertTrue(manager.contains("lastFailedKey.filter(key::equals).isPresent()"));
        assertTrue(manager.contains("lastFailedKey = Optional.empty()"));
    }

    @Test
    void craftingPolicyAccessDoesNotRequireAResolvedResearchPolicyMap()
            throws IOException {
        String access = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/resource/research/"
                        + "ProgressionPolicyAccessService.java");
        String crafting = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/progression/"
                        + "CraftingEligibilityService.java");
        int method = access.indexOf("private static Optional<CraftingContext> captureCurrentCrafting()");
        int next = access.indexOf("public enum Mode", method);
        String body = access.substring(method, next);

        assertTrue(body.contains("policy.craftingSnapshot().matches("));
        assertTrue(body.contains("policy.craftingSnapshot().policiesByProfile()"));
        assertFalse(body.contains("policy.snapshot().policiesByProfile()"));
        assertTrue(crafting.contains("ProgressionPolicyAccessService.acquireCrafting("));
    }

    @Test
    void externalWorkbenchRemapsCloseOnlyAffectedExternalSessions() throws IOException {
        String config = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/compat/fzzy_config/"
                        + "BlueprintConfig.java");

        assertTrue(config.contains("closeChangedExternalWorkbenchMenus("));
        assertTrue(config.contains("CraftingWorkbenchTierResolver.isNativeCraftingWorkbench("));
        assertTrue(config.contains("if (before.tier() != after.tier()"));
        assertTrue(config.contains("player.closeContainer()"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath));
    }
}
