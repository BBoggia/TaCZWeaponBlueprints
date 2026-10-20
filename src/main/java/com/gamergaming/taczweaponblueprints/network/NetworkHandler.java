package com.gamergaming.taczweaponblueprints.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.init.ModAttachments;
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
import net.neoforged.neoforge.network.PacketDistributor;

public class NetworkHandler {
    public static final String PROTOCOL_VERSION = "neoforge-1";
    // A random per-server seed prevents a partial chunk set from an earlier
    // connection being mistaken for a new sync after reconnecting.
    private static final AtomicLong SYNC_SEQUENCE =
            new AtomicLong(ThreadLocalRandom.current().nextLong());
    private static final Map<UUID, ResearchTreePublication> LAST_SENT_RESEARCH_TREES =
            new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SENT_RESEARCH_GENERATIONS =
            new ConcurrentHashMap<>();
    /** Direction-specific play payloads are mandatory on both ends. */
    public static void registerPackets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(OpenPortableResearchPacket.TYPE, OpenPortableResearchPacket.STREAM_CODEC, OpenPortableResearchPacket::handle);
        registrar.playToClient(SyncPlayerRecipeDataPacket.TYPE, SyncPlayerRecipeDataPacket.STREAM_CODEC, SyncPlayerRecipeDataPacket::handle);
        registrar.playToClient(SyncBlueprintDataPacket.TYPE, SyncBlueprintDataPacket.STREAM_CODEC, SyncBlueprintDataPacket::handle);
        registrar.playToClient(SyncPlayerProgressionPacket.TYPE, SyncPlayerProgressionPacket.STREAM_CODEC, SyncPlayerProgressionPacket::handle);
        registrar.playToClient(SyncBlueprintJournalPacket.TYPE, SyncBlueprintJournalPacket.STREAM_CODEC, SyncBlueprintJournalPacket::handle);
        registrar.playToServer(ResearchBenchActionPacket.TYPE, ResearchBenchActionPacket.STREAM_CODEC, ResearchBenchActionPacket::handle);
        registrar.playToClient(SyncResearchBenchPreviewPacket.TYPE, SyncResearchBenchPreviewPacket.STREAM_CODEC, SyncResearchBenchPreviewPacket::handle);
        registrar.playToClient(ResearchBenchActionResultPacket.TYPE, ResearchBenchActionResultPacket.STREAM_CODEC, ResearchBenchActionResultPacket::handle);
        registrar.playToClient(SyncResearchTreePacket.TYPE, SyncResearchTreePacket.STREAM_CODEC, SyncResearchTreePacket::handle);
        registrar.playToClient(ResearchPointFeedbackPacket.TYPE, ResearchPointFeedbackPacket.STREAM_CODEC, ResearchPointFeedbackPacket::handle);
        registrar.playToClient(SyncResearchPointHelpPacket.TYPE, SyncResearchPointHelpPacket.STREAM_CODEC, SyncResearchPointHelpPacket::handle);
        registrar.playToServer(BlueprintRecyclerActionPacket.TYPE, BlueprintRecyclerActionPacket.STREAM_CODEC, BlueprintRecyclerActionPacket::handle);
        registrar.playToClient(SyncBlueprintRecyclerPreviewPacket.TYPE, SyncBlueprintRecyclerPreviewPacket.STREAM_CODEC, SyncBlueprintRecyclerPreviewPacket::handle);
        registrar.playToClient(BlueprintRecyclerActionResultPacket.TYPE, BlueprintRecyclerActionResultPacket.STREAM_CODEC, BlueprintRecyclerActionResultPacket::handle);
        registrar.playToClient(SyncBalancePresetPacket.TYPE, SyncBalancePresetPacket.STREAM_CODEC, SyncBalancePresetPacket::handle);
        registrar.playToServer(ResearchGuidanceRequestPacket.TYPE, ResearchGuidanceRequestPacket.STREAM_CODEC, ResearchGuidanceRequestPacket::handle);
        registrar.playToClient(ResearchGuidanceResultPacket.TYPE, ResearchGuidanceResultPacket.STREAM_CODEC, ResearchGuidanceResultPacket::handle);
        registrar.playToServer(ResearchAffordabilityRequestPacket.TYPE, ResearchAffordabilityRequestPacket.STREAM_CODEC, ResearchAffordabilityRequestPacket::handle);
        registrar.playToClient(ResearchAffordabilityResultPacket.TYPE, ResearchAffordabilityResultPacket.STREAM_CODEC, ResearchAffordabilityResultPacket::handle);
        registrar.playToClient(SyncPlayerSupplementalProgressionPacket.TYPE, SyncPlayerSupplementalProgressionPacket.STREAM_CODEC, SyncPlayerSupplementalProgressionPacket::handle);
        registrar.playToServer(ResearchWorkbenchModePacket.TYPE, ResearchWorkbenchModePacket.STREAM_CODEC, ResearchWorkbenchModePacket::handle);
        registrar.playToServer(CraftingAccessRequestPacket.TYPE, CraftingAccessRequestPacket.STREAM_CODEC, CraftingAccessRequestPacket::handle);
        registrar.playToClient(SyncResearchCurrencyPacket.TYPE, SyncResearchCurrencyPacket.STREAM_CODEC, SyncResearchCurrencyPacket::handle);
        registrar.playToClient(SyncCraftingAccessPacket.TYPE, SyncCraftingAccessPacket.STREAM_CODEC, SyncCraftingAccessPacket::handle);
        registrar.playToServer(ResearchRouteReviewRequestPacket.TYPE, ResearchRouteReviewRequestPacket.STREAM_CODEC, ResearchRouteReviewRequestPacket::handle);
        registrar.playToClient(ResearchRouteReviewResultPacket.TYPE, ResearchRouteReviewResultPacket.STREAM_CODEC, ResearchRouteReviewResultPacket::handle);
        registrar.playToServer(DesignerCaptureRequestPacket.TYPE, DesignerCaptureRequestPacket.STREAM_CODEC, DesignerCaptureRequestPacket::handle);
        registrar.playToClient(DesignerCaptureReplyPacket.TYPE, DesignerCaptureReplyPacket.STREAM_CODEC, DesignerCaptureReplyPacket::handle);
        registrar.playToServer(ResearchMaterialPageRequestPacket.TYPE, ResearchMaterialPageRequestPacket.STREAM_CODEC, ResearchMaterialPageRequestPacket::handle);
        registrar.playToClient(SyncVillagerTradeStatusPacket.TYPE, SyncVillagerTradeStatusPacket.STREAM_CODEC, SyncVillagerTradeStatusPacket::handle);
    }

    static ServerPlayer sender(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        return context.player() instanceof ServerPlayer player ? player : null;
    }

    public static void syncBalancePreset(
            ServerPlayer player,
            com.gamergaming.taczweaponblueprints.progression.BlueprintBalancePreset preset) {
        if (player != null && preset != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new SyncBalancePresetPacket(preset));
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
        ModAttachments.recipeData(player).ifPresent(recipeData -> {
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
                    .forEach(packet -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet));
            sendPlayerProgressionData(player, recipeData, true, false, true);
        });
    }

    public static void syncPlayerProgressionData(ServerPlayer player) {
        refreshOpenCraftingWorkbench(player);
        ModAttachments.recipeData(player)
                .ifPresent(recipeData -> sendPlayerProgressionData(
                        player, recipeData, false, false, true));
    }

    /**
     * Synchronizes a point-only change without rebuilding or transferring an
     * unchanged tree or supplemental state. Callers that changed fragments or
     * criteria must use {@link #syncPlayerProgressionData(ServerPlayer)}.
     */
    public static void syncResearchCurrency(ServerPlayer player) {
        ModAttachments.recipeData(player).ifPresent(data -> {
            var policy = com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.currencyPolicy();
            int amount = com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPayments.balance(policy, player.getInventory().items, data);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new SyncResearchCurrencyPacket(policy, amount, com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.dataSettings()));
        });
    }

    public static void syncPlayerPointBalance(ServerPlayer player) {
        // A queued complete publication already contains the current balance.
        // Let it win so this narrow path cannot publish around an older tree.
        if (BlueprintProgressionSyncScheduler.hasPendingFullSync(player)) {
            return;
        }
        ModAttachments.recipeData(player)
                .ifPresent(recipeData -> sendPlayerProgressionData(
                        player, recipeData, false, true, false));
    }

    public static void syncJournalData(ServerPlayer player) {
        refreshOpenCraftingWorkbench(player);
        ModAttachments.recipeData(player)
                .ifPresent(recipeData -> sendJournalData(player, recipeData, false, false, true));
        refreshOpenWorkstation(player);
    }

    public static void syncBlueprintData(ServerPlayer player) {
        SyncBlueprintDataPacket.split(
                        BlueprintDataManager.SERVER.getBlueprintDataMap(),
                        SYNC_SEQUENCE.incrementAndGet())
                .forEach(packet -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet));
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
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new SyncResearchBenchPreviewPacket(containerId, preview));
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
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new ResearchBenchActionResultPacket(containerId, requestId, result));
    }

    public static void sendResearchGuidanceResult(
            ServerPlayer player,
            int containerId,
            int requestId,
            long publicationGeneration,
            com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu.GuidanceResult result) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new ResearchGuidanceResultPacket(
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
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new ResearchAffordabilityResultPacket(
                        containerId,
                        requestId,
                        publicationGeneration,
                        result));
    }

    public static void sendResearchPointFeedback(ServerPlayer player, Feedback feedback) {
        sendResearchPointFeedback(player, feedback, com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.currencyPolicy());
    }

    public static void sendResearchPointFeedback(ServerPlayer player, Feedback feedback,
            com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy currency) {
        if (player != null && feedback != null && feedback.present()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new ResearchPointFeedbackPacket(feedback, currency));
        }
    }

    public static void sendResearchPointHelp(ServerPlayer player, HelpSnapshot snapshot) {
        if (player != null && snapshot != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new SyncResearchPointHelpPacket(snapshot));
        }
    }

    public static void sendBlueprintRecyclerPreview(
            ServerPlayer player,
            int containerId,
            com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerPreview preview) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new SyncBlueprintRecyclerPreviewPacket(containerId, preview));
    }

    public static void sendBlueprintRecyclerActionResult(
            ServerPlayer player,
            int containerId,
            int requestId,
            com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract.ActionResult result) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new BlueprintRecyclerActionResultPacket(containerId, requestId, result));
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
                    .forEach(packet -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet));
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
        syncResearchCurrency(player);
        if (!treeKnownUnchanged) {
            BlueprintProgressionSyncScheduler.clear(player);
        }
        SyncPlayerProgressionPacket.split(
                        recipeData.getLearnedBlueprints(),
                        recipeData.getDiscoveredBlueprints(),
                        recipeData.getResearchPoints(),
                        SYNC_SEQUENCE.incrementAndGet())
                .forEach(packet -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet));
        sendJournalData(
                player,
                recipeData,
                forceTree,
                treeKnownUnchanged,
                includeSupplementalProgression);
        if (!treeKnownUnchanged || includeSupplementalProgression) {
            refreshOpenWorkstation(player);
        } else if (player.containerMenu instanceof com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu menu) {
            // A point-only publication still invalidates the inspected research quote.
            // Use the menu's bounded admission/queued refresh without rebuilding crafting access.
            menu.refreshAuthoritativePreview(player);
        }
    }

    private static void sendJournalData(
            ServerPlayer player,
            IPlayerRecipeData recipeData,
            boolean forceTree,
            boolean treeKnownUnchanged,
            boolean includeSupplementalProgression) {
        IPlayerRecipeData presentationData = recipeData;
        var currency = com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.currencyPolicy();
        if (com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPayments.physical(currency)) {
            presentationData = com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyManual.eligibility(recipeData);
            presentationData.setResearchPoints(com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPayments.balance(currency, player.getInventory().items, recipeData));
        }
        var playerPublication =
                BlueprintResearchDataManager.INSTANCE.playerPublicationFor(presentationData);
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
                .forEach(packet -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet));
        if (sendTree) {
            SyncResearchTreePacket.split(tree, generation)
                    .forEach(packet -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet));
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
                .forEach(packet -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet));
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
