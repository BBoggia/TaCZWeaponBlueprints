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
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.Feedback;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.HelpSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    public static final String PROTOCOL_VERSION = "40";
    // A random per-server seed prevents a partial chunk set from an earlier
    // connection being mistaken for a new sync after reconnecting.
    private static final AtomicLong SYNC_SEQUENCE =
            new AtomicLong(ThreadLocalRandom.current().nextLong());
    private static final Map<UUID, ResearchTreePublication> LAST_SENT_RESEARCH_TREES =
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
            sendPlayerProgressionData(player, recipeData, true, false);
        });
    }

    public static void syncPlayerProgressionData(ServerPlayer player) {
        player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .ifPresent(recipeData -> sendPlayerProgressionData(player, recipeData, false, false));
    }

    /** Synchronizes a point-only change without rebuilding or transferring an unchanged tree. */
    public static void syncPlayerPointBalance(ServerPlayer player) {
        // A queued complete publication already contains the current balance.
        // Let it win so this narrow path cannot publish around an older tree.
        if (BlueprintProgressionSyncScheduler.hasPendingFullSync(player)) {
            return;
        }
        player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .ifPresent(recipeData -> sendPlayerProgressionData(player, recipeData, false, true));
    }

    public static void syncJournalData(ServerPlayer player) {
        player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .ifPresent(recipeData -> sendJournalData(player, recipeData, false, false));
        refreshOpenWorkstation(player);
    }

    public static void syncBlueprintData(ServerPlayer player) {
        SyncBlueprintDataPacket.split(
                        BlueprintDataManager.SERVER.getBlueprintDataMap(),
                        SYNC_SEQUENCE.incrementAndGet())
                .forEach(packet -> INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet));
    }

    public static void syncAllPlayerData(ServerPlayer player) {
        syncBlueprintData(player);
        syncPlayerRecipeData(player);
    }

    public static void sendResearchBenchPreview(
            ServerPlayer player,
            int containerId,
            com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview preview) {
        INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncResearchBenchPreviewPacket(containerId, preview));
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

    public static void clearPlayerSyncState(ServerPlayer player) {
        if (player != null) {
            LAST_SENT_RESEARCH_TREES.remove(player.getUUID());
        }
    }

    /** Clears publication state that belongs to the server instance being stopped. */
    public static void clearServerSyncState() {
        LAST_SENT_RESEARCH_TREES.clear();
    }

    private static void sendPlayerProgressionData(
            ServerPlayer player,
            IPlayerRecipeData recipeData,
            boolean forceTree,
            boolean treeKnownUnchanged) {
        if (!treeKnownUnchanged) {
            BlueprintProgressionSyncScheduler.clear(player);
        }
        SyncPlayerProgressionPacket.split(
                        recipeData.getLearnedBlueprints(),
                        recipeData.getDiscoveredBlueprints(),
                        recipeData.getResearchPoints(),
                        SYNC_SEQUENCE.incrementAndGet())
                .forEach(packet -> INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet));
        sendJournalData(player, recipeData, forceTree, treeKnownUnchanged);
        refreshOpenWorkstation(player);
    }

    private static void sendJournalData(
            ServerPlayer player,
            IPlayerRecipeData recipeData,
            boolean forceTree,
            boolean treeKnownUnchanged) {
        var playerPublication =
                BlueprintResearchDataManager.INSTANCE.playerPublicationFor(recipeData);
        var snapshot = playerPublication.journal();
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
    }

    private static void refreshOpenWorkstation(ServerPlayer player) {
        if (player.containerMenu instanceof com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu menu) {
            menu.refreshAuthoritativePreview(player);
        } else if (player.containerMenu
                instanceof com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu menu) {
            menu.refreshAuthoritativePreview(player);
        }
    }

}
