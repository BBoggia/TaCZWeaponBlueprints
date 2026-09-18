package com.gamergaming.taczweaponblueprints.compat.magazines;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;

import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Captures upstream recipes and refreshes open Workbenches after native recipe synchronization. */
@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MagazineDiscoveryEvents {
    private static TaCZMagazinesAdapter adapter;
    private static boolean refreshAccess;
    private static boolean resendRecipes;

    private MagazineDiscoveryEvents() { }
    /** Resolves the actual base-family requirement even when its recipe has been disabled. */
    public static java.util.Optional<net.minecraft.resources.ResourceLocation> requiredFamily(net.minecraft.resources.ResourceLocation gun) {
        if (adapter == null) throw new IllegalStateException("Magazine discovery has not started");
        return adapter.familyForGun(gun);
    }

    @SubscribeEvent
    public static void starting(ServerAboutToStartEvent event) {
        MagazineCatalog.INSTANCE.clear();
        refreshAccess = false;
        resendRecipes = false;
        String version = ModList.get().getModContainerById(TaCZMagazinesAdapter.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse(null);
        adapter = TaCZMagazinesAdapter.create(version, ReflectiveMagazineSource::new);
        MagazineCatalog.INSTANCE.initialize(adapter);
        TaCZWeaponBlueprints.LOGGER.info("Optional TaCZ Magazines discovery: {} (installed version: {})",
                adapter.status(), version == null ? "absent" : version);
    }

    // 0.2.0-Beta generates recipes at NORMAL priority on both login and reload.
    // Capture at LOWEST while still inside the event, before Forge sends recipes.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void synchronizedRecipes(OnDatapackSyncEvent event) {
        if (adapter == null || adapter.status() == TaCZMagazinesAdapter.Status.ABSENT) return;
        var previous = MagazineCatalog.INSTANCE.publication().catalog();
        var publication = MagazineCatalog.INSTANCE.rebuild(adapter,
                event.getPlayerList().getServer().getRecipeManager().getRecipes());
        var capture = publication.catalog();
        refreshAccess = true;
        // Forge normally synchronizes recipes only to the joining player. If upstream
        // generation changed the shared manager, existing players also need that update.
        boolean existingPlayersNeedSync = event.getPlayer() != null
                && event.getPlayerList().getPlayers().stream().anyMatch(player -> player != event.getPlayer());
        resendRecipes |= shouldResendRecipes(existingPlayersNeedSync, previous, capture);
        if (!MagazineCraftingAccess.fitsAccessSnapshot(
                BlueprintDataManager.SERVER.catalogPublication().blueprints().size(), capture.families().size())) {
            TaCZWeaponBlueprints.LOGGER.warn("TaCZ Magazines crafting disabled: the combined blueprint and magazine "
                    + "catalog exceeds the Workbench access limit of {} recipes",
                    com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits.MAX_IDS_PER_COLLECTION);
        }
        TaCZWeaponBlueprints.LOGGER.info(
                "TaCZ Magazines discovery revision {}: {}, {} families, {} recipes, {} rejected",
                publication.revision(), capture.status(), capture.families().size(),
                capture.familyByRecipe().size(), capture.rejected().size());
        capture.rejected().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).limit(8)
                .forEach(entry -> TaCZWeaponBlueprints.LOGGER.warn(
                        "TaCZ Magazines discovery rejected {}: {}", entry.getKey(), entry.getValue()));
    }

    static boolean shouldResendRecipes(boolean existingPlayersNeedSync,
            TaCZMagazinesAdapter.Capture previous, TaCZMagazinesAdapter.Capture current) {
        // An unavailable capture contains no recipes to compare. Recovering from
        // it still requires recipes to reach existing clients before new access.
        return existingPlayersNeedSync && !MagazineCatalog.sameRecipes(previous, current);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !refreshAccess) return;
        refreshAccess = false;
        boolean sendRecipes = resendRecipes;
        resendRecipes = false;
        var server = event.getServer();
        var recipePacket = sendRecipes ? new ClientboundUpdateRecipesPacket(server.getRecipeManager().getRecipes()) : null;
        for (var player : server.getPlayerList().getPlayers()) {
            if (recipePacket != null) player.connection.send(recipePacket);
            if (player.containerMenu instanceof GunSmithTableMenu menu) NetworkHandler.sendCraftingAccess(player, menu);
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        adapter = null;
        refreshAccess = false;
        resendRecipes = false;
        MagazineCatalog.INSTANCE.clear();
    }
}
