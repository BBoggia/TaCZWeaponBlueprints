package com.gamergaming.taczweaponblueprints.acceptance;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in verification of the settings fixture actually loaded by a dedicated server. */
@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID)
public final class SettingsServerProbe {
    @SubscribeEvent public static void started(ServerStartedEvent event) throws java.io.IOException {
        String output = System.getProperty("tacz.settingsReviewQA.serverOutput", "");
        if (output.isEmpty()) return;
        java.nio.file.Files.writeString(java.nio.file.Path.of(output),
                "balance=" + ModConfigs.BLUEPRINT.balancePreset.get()
                + "\nfragment=" + ModConfigs.BLUEPRINT.fragmentPreset.get());
    }
}
