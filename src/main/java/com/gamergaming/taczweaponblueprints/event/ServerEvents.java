package com.gamergaming.taczweaponblueprints.event;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootDataManager;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionSyncScheduler;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardReconciliationScheduler;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointCombatTracker;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceManager;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateManager;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardBlueprintFacts;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintProgressionPolicyManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintAmmoAssociationManager;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();

        TaCZWeaponBlueprints.LOGGER.info("Server starting, initializing BlueprintDataManager...");
        if (BlueprintDataManager.SERVER.initialize(server)) {
            TaCZWeaponBlueprints.LOGGER.info("BlueprintDataManager initialized.");
            BlueprintResearchDataManager.INSTANCE.logActiveProfileAudit();
        } else {
            TaCZWeaponBlueprints.LOGGER.error("BlueprintDataManager initialization failed.");
        }

        TaCZWeaponBlueprints.LOGGER.info("Research economy: currency={}, Data={}. Upgrades preserve saved RP and do not convert items. Inspect with /gg progression currency inspect <player>; use currency legacy apply for banked redemption or currency withdraw preview <player> <amount> for an explicit physical withdrawal.",
                com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.currencyPolicy().kind(),
                com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.dataSettings().behavior());
        BlueprintLootSnapshot lootSnapshot = BlueprintLootDataManager.INSTANCE.snapshot();
        if (lootSnapshot.active()) {
            TaCZWeaponBlueprints.LOGGER.info(
                    "Dynamic blueprint loot distribution active: {} tags, {} pools, {} rules, "
                            + "{} exact bindings, {} selector rules",
                    lootSnapshot.tags().size(),
                    lootSnapshot.pools().size(),
                    lootSnapshot.rules().size(),
                    lootSnapshot.bindingCount(),
                    lootSnapshot.selectorBindings().size());
        } else if (lootSnapshot.globallyDisablesDistribution()) {
            TaCZWeaponBlueprints.LOGGER.info(
                    "Dynamic blueprint loot distribution owns the system with no enabled bindings; "
                            + "blueprint loot is disabled by datapack data");
        } else if (lootSnapshot.ownsDistribution()) {
            TaCZWeaponBlueprints.LOGGER.info(
                    "Only targeted disabled blueprint loot rules are present; "
                            + "legacy fallback remains active for untargeted loot tables");
        } else {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "No dynamic blueprint loot rules are active; retaining legacy modifier behavior");
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BlueprintProgressionSyncScheduler.clearAll();
        ResearchPointAwardReconciliationScheduler.clearAll();
        ResearchPointCombatTracker.clear();
        ResearchPointAwardBlueprintFacts.clearCurrentPublication();
        NetworkHandler.clearServerSyncState();
        BlueprintDataManager.SERVER.clear();
        BlueprintResearchDataManager.INSTANCE.clear();
        BlueprintLootDataManager.INSTANCE.clear();
        ResearchPointAwardDataManager.INSTANCE.clear();
        com.gamergaming.taczweaponblueprints.villager.trade.TradeDataManager.INSTANCE.clear();
        BlueprintAmmoAssociationManager.INSTANCE.clear();
        AutomaticWeaponEvidenceManager.INSTANCE.clear();
        com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencySyncTracker.clear();
            com.gamergaming.taczweaponblueprints.command.sub.ResearchCurrencyMigrationCommand.PENDING.clear();
        AutomaticWeaponPlacementCandidateManager.INSTANCE.clear();
        BlueprintProgressionPolicyManager.INSTANCE.clear();
    }
}
