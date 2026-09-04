package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Lifecycle regressions for the release-level tiered progression contract. */
class ProgressionLifecyclePhaseTwelveTest {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));

    @Test
    void logoutAndServerShutdownDiscardEveryDeferredPlayerOrPlanningSession()
            throws IOException {
        String discoveryEvents = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/event/BlueprintDiscoveryEvents.java");
        String serverEvents = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/event/ServerEvents.java");
        String network = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/network/NetworkHandler.java");
        String lootManager = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/resource/loot/"
                        + "BlueprintLootDataManager.java");

        assertTrue(discoveryEvents.contains("PlayerLoggedOutEvent"));
        assertTrue(discoveryEvents.contains("BlueprintProgressionSyncScheduler.clear(serverPlayer)"));
        assertTrue(discoveryEvents.contains("NetworkHandler.clearPlayerSyncState(serverPlayer)"));
        assertTrue(serverEvents.contains("BlueprintProgressionSyncScheduler.clearAll()"));
        assertTrue(serverEvents.contains("NetworkHandler.clearServerSyncState()"));
        assertTrue(serverEvents.contains("BlueprintDataManager.SERVER.clear()"));
        assertTrue(serverEvents.contains("BlueprintResearchDataManager.INSTANCE.clear()"));
        assertTrue(serverEvents.contains("BlueprintLootDataManager.INSTANCE.clear()"));
        assertTrue(serverEvents.contains("BlueprintAmmoAssociationManager.INSTANCE.clear()"));
        assertTrue(serverEvents.contains("AutomaticWeaponEvidenceManager.INSTANCE.clear()"));
        assertTrue(serverEvents.contains(
                "AutomaticWeaponPlacementCandidateManager.INSTANCE.clear()"));
        assertTrue(lootManager.contains("BlueprintLootCatalogCache.clear()"));
        assertTrue(serverEvents.contains("ResearchPointAwardDataManager.INSTANCE.clear()"));
        assertTrue(serverEvents.contains("BlueprintProgressionPolicyManager.INSTANCE.clear()"));
        assertTrue(network.contains("ResearchPlanningAdmission.clear()"));
        assertTrue(network.contains("ResearchPathUnlockPlanner\n                .clearComplexityMemo()"));
        assertTrue(network.contains("ResearchRouteFailureReporter.clear()"));
    }

    @Test
    void disconnectDropsCraftingAllowListsAndTheirChunkAccumulator()
            throws IOException {
        String clientEvents = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/client/"
                        + "ClientConnectionEvents.java");

        assertTrue(clientEvents.contains("SyncCraftingAccessPacket.clearClientState()"));
        assertTrue(clientEvents.contains("ClientCraftingAccessState.clear()"));
        assertTrue(clientEvents.indexOf("SyncCraftingAccessPacket.clearClientState()")
                < clientEvents.indexOf("ClientCraftingAccessState.clear()"));
    }

    @Test
    void dimensionMovementDistanceAndBenchReplacementInvalidateResearchAuthority()
            throws IOException {
        String authority = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/progression/workbench/"
                        + "ResearchWorkbenchAuthority.java");
        String menu = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/menu/ResearchBenchMenu.java");

        assertTrue(authority.contains("player.level().dimension().location().equals(context.dimensionId())"));
        assertTrue(authority.contains("player.distanceToSqr("));
        assertTrue(authority.contains("ResearchBenchBlock.isValidRoot("));
        assertTrue(authority.contains("context.workstationId().equals(liveId)"));
        assertTrue(menu.contains("authorizesResearchContext"));
        assertTrue(menu.contains("stillValid(Player player)"));
    }

    @Test
    void reloadPublishesOneRevisionMatchedPolicyOrKeepsThePriorPublication()
            throws IOException {
        String resourceEvents = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/event/BlueprintResourceEvents.java");
        String policyManager = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/resource/research/"
                        + "BlueprintProgressionPolicyManager.java");
        String catalogManager = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/resource/"
                        + "BlueprintDataManager.java");

        assertTrue(resourceEvents.contains("rebuildDerivedPublicationsFromRetainedCatalog()"));
        assertTrue(catalogManager.contains("return rebuildProgressionPolicy()"));
        assertTrue(policyManager.contains("publication = new Publication("));
        assertTrue(policyManager.contains("lastFailure = Optional.of(message)"));
    }

    @Test
    void dormantDomainEntryPointsDoNotPoisonRuntimeSmokeEvidence() throws IOException {
        String dataManager = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/resource/research/"
                        + "BlueprintResearchDataManager.java");

        assertTrue(dataManager.contains("LOGGER.warn(\n"
                + "                        \"No active research entry point candidate"));
        assertFalse(dataManager.contains("LOGGER.error(\n"
                + "                        \"No active research entry point candidate"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath));
    }
}
