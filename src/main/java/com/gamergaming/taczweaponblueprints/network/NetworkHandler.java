package com.gamergaming.taczweaponblueprints.network;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionSyncScheduler;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionAccess;
import com.gamergaming.taczweaponblueprints.progression.PlayerSupplementalProgressionView;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.Feedback;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.HelpSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.ProgressionPolicyAccessService;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintProgressionPolicy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    public static final String PROTOCOL_VERSION = "55";
    // A random per-server seed prevents a partial chunk set from an earlier
    // connection being mistaken for a new sync after reconnecting.
    private static final AtomicLong SYNC_SEQUENCE =
            new AtomicLong(ThreadLocalRandom.current().nextLong());
    private static final Map<UUID, ResearchTreePublication> LAST_SENT_RESEARCH_TREES =
            new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SENT_RESEARCH_GENERATIONS =
            new ConcurrentHashMap<>();
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(TaCZWeaponBlueprints.MODID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    public static void registerPackets() {
        int id = 0;

        INSTANCE.registerMessage(id++, SyncPlayerRecipeDataPacket.class,
                SyncPlayerRecipeDataPacket::toBytes, SyncPlayerRecipeDataPacket::new,
                SyncPlayerRecipeDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, SyncBlueprintDataPacket.class,
                SyncBlueprintDataPacket::toBytes, SyncBlueprintDataPacket::new,
                SyncBlueprintDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, SyncPlayerProgressionPacket.class,
                SyncPlayerProgressionPacket::toBytes, SyncPlayerProgressionPacket::new,
                SyncPlayerProgressionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, SyncBlueprintJournalPacket.class,
                SyncBlueprintJournalPacket::toBytes, SyncBlueprintJournalPacket::new,
                SyncBlueprintJournalPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, ResearchBenchActionPacket.class,
                ResearchBenchActionPacket::toBytes, ResearchBenchActionPacket::new,
                ResearchBenchActionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++, SyncResearchBenchPreviewPacket.class,
                SyncResearchBenchPreviewPacket::toBytes, SyncResearchBenchPreviewPacket::new,
                SyncResearchBenchPreviewPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, ResearchBenchActionResultPacket.class,
                ResearchBenchActionResultPacket::toBytes,
                ResearchBenchActionResultPacket::new,
                ResearchBenchActionResultPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, SyncResearchTreePacket.class,
                SyncResearchTreePacket::toBytes, SyncResearchTreePacket::new,
                SyncResearchTreePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, ResearchPointFeedbackPacket.class,
                ResearchPointFeedbackPacket::toBytes, ResearchPointFeedbackPacket::new,
                ResearchPointFeedbackPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, SyncResearchPointHelpPacket.class,
                SyncResearchPointHelpPacket::toBytes, SyncResearchPointHelpPacket::new,
                SyncResearchPointHelpPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // Phase 1 workstation split: append-only IDs keep the live packet table stable.
        INSTANCE.registerMessage(id++, BlueprintRecyclerActionPacket.class,
                BlueprintRecyclerActionPacket::toBytes, BlueprintRecyclerActionPacket::new,
                BlueprintRecyclerActionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++, SyncBlueprintRecyclerPreviewPacket.class,
                SyncBlueprintRecyclerPreviewPacket::toBytes,
                SyncBlueprintRecyclerPreviewPacket::new,
                SyncBlueprintRecyclerPreviewPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, BlueprintRecyclerActionResultPacket.class,
                BlueprintRecyclerActionResultPacket::toBytes,
                BlueprintRecyclerActionResultPacket::new,
                BlueprintRecyclerActionResultPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // Appended in the preset-sync correction; existing discriminators remain stable.
        INSTANCE.registerMessage(id++, SyncBalancePresetPacket.class,
                SyncBalancePresetPacket::toBytes,
                SyncBalancePresetPacket::new,
                SyncBalancePresetPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, ResearchGuidanceRequestPacket.class,
                ResearchGuidanceRequestPacket::toBytes,
                ResearchGuidanceRequestPacket::new,
                ResearchGuidanceRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++, ResearchGuidanceResultPacket.class,
                ResearchGuidanceResultPacket::toBytes,
                ResearchGuidanceResultPacket::new,
                ResearchGuidanceResultPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, ResearchAffordabilityRequestPacket.class,
                ResearchAffordabilityRequestPacket::toBytes,
                ResearchAffordabilityRequestPacket::new,
                ResearchAffordabilityRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++, ResearchAffordabilityResultPacket.class,
                ResearchAffordabilityResultPacket::toBytes,
                ResearchAffordabilityResultPacket::new,
                ResearchAffordabilityResultPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // Phase 3 supplemental progression is append-only so existing packet
        // discriminators retain their established ordering.
        INSTANCE.registerMessage(id++, SyncPlayerSupplementalProgressionPacket.class,
                SyncPlayerSupplementalProgressionPacket::toBytes,
                SyncPlayerSupplementalProgressionPacket::new,
                SyncPlayerSupplementalProgressionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, ResearchWorkbenchModePacket.class,
                ResearchWorkbenchModePacket::toBytes,
                ResearchWorkbenchModePacket::new,
                ResearchWorkbenchModePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++, CraftingAccessRequestPacket.class,
                CraftingAccessRequestPacket::toBytes,
                CraftingAccessRequestPacket::new,
                CraftingAccessRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++, SyncCraftingAccessPacket.class,
                SyncCraftingAccessPacket::toBytes,
                SyncCraftingAccessPacket::new,
                SyncCraftingAccessPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void syncBalancePreset(
            ServerPlayer player,
            com.gamergaming.taczweaponblueprints.progression.BlueprintBalancePreset preset) {
        if (player != null && preset != null) {
            INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new SyncBalancePresetPacket(preset));
        }
    }

    public static void syncPlayerRecipeData(ServerPlayer player) {
        syncPlayerRecipeData(player, true);
    }

    private static void syncPlayerRecipeData(
            ServerPlayer player,
            boolean refreshCraftingWorkbench) {
        if (refreshCraftingWorkbench) {
            refreshOpenCraftingWorkbench(player);
        }
        player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(recipeData -> {
            BlueprintDataManager.SERVER.migrateLegacyUnlocks(recipeData);
            var activeRecipes = RecipeSyncFilter.activeLearnedRecipes(
                    recipeData.getLearnedRecipes(),
                    recipeData.getLearnedBlueprints(),
                    BlueprintDataManager.SERVER.getBlueprintDataMap(),
                    BlueprintDataManager.SERVER.getRecipeToBlueprintMap(),
                    BlueprintProgressionAccess.exemptRecipeIds(
                            com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT
                                    .accessSnapshot(),
                            BlueprintDataManager.SERVER.getBlueprintDataMap()));
            SyncPlayerRecipeDataPacket.split(activeRecipes, SYNC_SEQUENCE.incrementAndGet())
                    .forEach(packet -> INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet));
            sendPlayerProgressionData(player, recipeData, true, false, true);
        });
    }

    public static void syncPlayerProgressionData(ServerPlayer player) {
        refreshOpenCraftingWorkbench(player);
        player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .ifPresent(recipeData -> sendPlayerProgressionData(
                        player, recipeData, false, false, true));
    }

    /**
     * Synchronizes a point-only change without rebuilding or transferring an
     * unchanged tree or supplemental state. Callers that changed fragments or
     * criteria must use {@link #syncPlayerProgressionData(ServerPlayer)}.
     */
    public static void syncPlayerPointBalance(ServerPlayer player) {
        // A queued complete publication already contains the current balance.
        // Let it win so this narrow path cannot publish around an older tree.
        if (BlueprintProgressionSyncScheduler.hasPendingFullSync(player)) {
            return;
        }
        player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .ifPresent(recipeData -> sendPlayerProgressionData(
                        player, recipeData, false, true, false));
    }

    public static void syncJournalData(ServerPlayer player) {
        refreshOpenCraftingWorkbench(player);
        player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .ifPresent(recipeData -> sendJournalData(player, recipeData, false, false, true));
        refreshOpenWorkstation(player);
    }

    public static void syncBlueprintData(ServerPlayer player) {
        SyncBlueprintDataPacket.split(
                        BlueprintDataManager.SERVER.getBlueprintDataMap(),
                        SYNC_SEQUENCE.incrementAndGet())
                .forEach(packet -> INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet));
    }

    public static void syncAllPlayerData(ServerPlayer player) {
        refreshOpenCraftingWorkbench(player);
        syncBlueprintData(player);
        syncPlayerRecipeData(player, false);
    }

    public static void sendResearchBenchPreview(
            ServerPlayer player,
            int containerId,
            com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview preview) {
        INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncResearchBenchPreviewPacket(containerId, preview));
    }

    /** Clears stale server/client selection state before publishing an admin reset. */
    public static void clearOpenResearchBenchSelection(ServerPlayer player) {
        if (player != null
                && player.containerMenu
                        instanceof com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu menu) {
            menu.clearAuthoritativeSelection(player);
        }
    }

    public static void sendResearchBenchActionResult(
            ServerPlayer player,
            int containerId,
            int requestId,
            com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu.ActionResult result) {
        INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ResearchBenchActionResultPacket(containerId, requestId, result));
    }

    public static void sendResearchGuidanceResult(
            ServerPlayer player,
            int containerId,
            int requestId,
            long publicationGeneration,
            com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu.GuidanceResult result) {
        INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ResearchGuidanceResultPacket(
                        containerId,
                        requestId,
                        publicationGeneration,
                        result));
    }

    public static void sendResearchAffordabilityResult(
            ServerPlayer player,
            int containerId,
            int requestId,
            long publicationGeneration,
            com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu
                    .AffordabilityResult result) {
        INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ResearchAffordabilityResultPacket(
                        containerId,
                        requestId,
                        publicationGeneration,
                        result));
    }

    public static void sendResearchPointFeedback(ServerPlayer player, Feedback feedback) {
        if (player != null && feedback != null && feedback.present()) {
            INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ResearchPointFeedbackPacket(feedback));
        }
    }

    public static void sendResearchPointHelp(ServerPlayer player, HelpSnapshot snapshot) {
        if (player != null && snapshot != null) {
            INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new SyncResearchPointHelpPacket(snapshot));
        }
    }

    public static void sendBlueprintRecyclerPreview(
            ServerPlayer player,
            int containerId,
            com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerPreview preview) {
        INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncBlueprintRecyclerPreviewPacket(containerId, preview));
    }

    public static void sendBlueprintRecyclerActionResult(
            ServerPlayer player,
            int containerId,
            int requestId,
            com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract.ActionResult result) {
        INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new BlueprintRecyclerActionResultPacket(containerId, requestId, result));
    }

    public static void sendCraftingAccess(
            ServerPlayer player,
            com.tacz.guns.inventory.GunSmithTableMenu menu) {
        if (player != null && menu != null && player.containerMenu == menu
                && menu instanceof com.gamergaming.taczweaponblueprints.compat.tacz
                        .TaCZWorkbenchMenuBridge bridge) {
            long requestId = bridge.taczweaponblueprints$craftingAccessRequestId();
            if (requestId < 1L) {
                return;
            }
            SyncCraftingAccessPacket.split(
                            menu.containerId,
                            requestId,
                            bridge.taczweaponblueprints$nextCraftingAccessSnapshotId(),
                            com.gamergaming.taczweaponblueprints.progression
                                    .CraftingEligibilityService.snapshot(player, menu))
                    .forEach(packet -> INSTANCE.send(
                            PacketDistributor.PLAYER.with(() -> player), packet));
        }
    }

    public static void clearPlayerSyncState(ServerPlayer player) {
        if (player != null) {
            LAST_SENT_RESEARCH_TREES.remove(player.getUUID());
            LAST_SENT_RESEARCH_GENERATIONS.remove(player.getUUID());
        }
    }

    public static boolean matchesResearchGeneration(ServerPlayer player, long generation) {
        return player != null
                && Long.valueOf(generation).equals(
                        LAST_SENT_RESEARCH_GENERATIONS.get(player.getUUID()));
    }

    /** Clears publication state that belongs to the server instance being stopped. */
    public static void clearServerSyncState() {
        LAST_SENT_RESEARCH_TREES.clear();
        LAST_SENT_RESEARCH_GENERATIONS.clear();
        com.gamergaming.taczweaponblueprints.menu.ResearchPlanningAdmission.clear();
        com.gamergaming.taczweaponblueprints.progression.ResearchPathUnlockPlanner
                .clearComplexityMemo();
        com.gamergaming.taczweaponblueprints.progression.ResearchRouteFailureReporter.clear();
    }

    private static void sendPlayerProgressionData(
            ServerPlayer player,
            IPlayerRecipeData recipeData,
            boolean forceTree,
            boolean treeKnownUnchanged,
            boolean includeSupplementalProgression) {
        if (!treeKnownUnchanged) {
            BlueprintProgressionSyncScheduler.clear(player);
        }
        SyncPlayerProgressionPacket.split(
                        recipeData.getLearnedBlueprints(),
                        recipeData.getDiscoveredBlueprints(),
                        recipeData.getResearchPoints(),
                        SYNC_SEQUENCE.incrementAndGet())
                .forEach(packet -> INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet));
        sendJournalData(
                player,
                recipeData,
                forceTree,
                treeKnownUnchanged,
                includeSupplementalProgression);
        if (!treeKnownUnchanged || includeSupplementalProgression) {
            refreshOpenWorkstation(player);
        }
    }

    private static void sendJournalData(
            ServerPlayer player,
            IPlayerRecipeData recipeData,
            boolean forceTree,
            boolean treeKnownUnchanged,
            boolean includeSupplementalProgression) {
        var playerPublication =
                BlueprintResearchDataManager.INSTANCE.playerPublicationFor(recipeData);
        var snapshot = playerPublication.journal();
        if (includeSupplementalProgression) {
            sendSupplementalProgressionData(player, recipeData, playerPublication);
        }
        var previousTree = LAST_SENT_RESEARCH_TREES.get(player.getUUID());
        boolean reuseKnownTree = treeKnownUnchanged && previousTree != null && !forceTree;
        var tree = reuseKnownTree
                ? previousTree
                : playerPublication.tree();
        boolean sendTree = !reuseKnownTree && (forceTree || !tree.equals(previousTree));
        long generation = SYNC_SEQUENCE.incrementAndGet();
        SyncBlueprintJournalPacket.split(snapshot, generation, !sendTree)
                .forEach(packet -> INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet));
        if (sendTree) {
            SyncResearchTreePacket.split(tree, generation)
                    .forEach(packet -> INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet));
            LAST_SENT_RESEARCH_TREES.put(player.getUUID(), tree);
        }
        LAST_SENT_RESEARCH_GENERATIONS.put(player.getUUID(), generation);
    }

    private static void sendSupplementalProgressionData(
            ServerPlayer player,
            IPlayerRecipeData recipeData,
            BlueprintResearchDataManager.PlayerResearchPublication playerPublication) {
        var policyAccess = ProgressionPolicyAccessService.acquire(
                ProgressionPolicyAccessService.Mode.CURRENT_ONLY).orElse(null);
        var policies = policyAccess != null
                        && policyAccess.catalog().revision()
                                == playerPublication.catalogRevision()
                        && policyAccess.research().revision()
                                == playerPublication.researchRevision()
                ? policyAccess.profilePolicies()
                : Map.<ResourceLocation, ResolvedBlueprintProgressionPolicy>of();
        var disclosedBlueprintIds = PlayerSupplementalProgressionView.disclosedBlueprintIds(
                playerPublication.journal(), playerPublication.tree());
        var view = PlayerSupplementalProgressionView.create(
                        recipeData,
                        disclosedBlueprintIds,
                        policies);
        SyncPlayerSupplementalProgressionPacket.split(
                        view,
                        SYNC_SEQUENCE.incrementAndGet())
                .forEach(packet -> INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> player), packet));
    }

    private static void refreshOpenWorkstation(ServerPlayer player) {
        if (player.containerMenu instanceof com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu menu) {
            menu.refreshAuthoritativePreview(player);
        } else if (player.containerMenu
                instanceof com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu menu) {
            menu.refreshAuthoritativePreview(player);
        }
    }

    /** Publishes Workbench access before the larger progression streams that follow it. */
    private static void refreshOpenCraftingWorkbench(ServerPlayer player) {
        if (player != null
                && player.containerMenu instanceof com.tacz.guns.inventory.GunSmithTableMenu menu) {
            sendCraftingAccess(player, menu);
        }
    }

}
