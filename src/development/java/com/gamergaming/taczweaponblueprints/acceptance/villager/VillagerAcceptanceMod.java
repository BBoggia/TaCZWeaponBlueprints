package com.gamergaming.taczweaponblueprints.acceptance.villager;

import com.gamergaming.taczweaponblueprints.villagerqa.VillagerPrototypeStock;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** Separate opt-in harness: loads beside the unchanged production JAR. */
@Mod("taczblueprintsqa")
public final class VillagerAcceptanceMod {
    public VillagerAcceptanceMod() {
        if (VillagerPrototypeStock.root() == null)
            throw new IllegalStateException("Villager acceptance requires a marked disposable QA root");
        if (!System.getProperty("tacz.villagerQA.upgrade", "").isEmpty()) {
            NeoForge.EVENT_BUS.register(VillagerUpgradeProbe.class);
            return;
        }
        for (String name : new String[]{"VillagerStockProbe", "VillagerEconomyProbe",
                "VillagerLifecycleProbe", "VillagerPersistenceProbe", "VillagerReloadRecoveryProbe",
                "VillagerBalanceProbe", "VillagerFallbackRegressionProbe", "VillagerLateReloadProbe"}) register(name);
        if (FMLEnvironment.dist.isClient()) {
            register("VillagerLifecycleClientProbe");
            register("VillagerPresentationProbe");
        }
    }
    private static void register(String name) {
        try {
            NeoForge.EVENT_BUS.register(Class.forName(
                    "com.gamergaming.taczweaponblueprints.villagerqa." + name));
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("Missing acceptance probe " + name, failure);
        }
    }
}
