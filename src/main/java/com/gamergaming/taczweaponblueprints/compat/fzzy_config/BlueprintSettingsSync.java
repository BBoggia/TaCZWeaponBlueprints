package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import me.fzzyhmstrs.fzzy_config.networking.ConfigSyncS2CCustomPayload;
import net.minecraft.server.level.ServerPlayer;

/** Publishes the server's settings through Fzzy's normal receive/GUI refresh path. */
public final class BlueprintSettingsSync {
    private BlueprintSettingsSync() {}

    public static void sendTo(ServerPlayer player) {
        if (player.server.isSingleplayer()) return;
        var network = ConfigApi.INSTANCE.network();
        if (!network.canSend(ConfigSyncS2CCustomPayload.Companion.getId(), player)) return;
        // Fzzy 0.7.6 registers its Forge login gather listener on the global bus,
        // but Forge dispatches that event to network channels. Send the initial
        // snapshot once in the play phase, to the joining player only.
        var serialized = ConfigApi.serializeConfig(ModConfigs.BLUEPRINT,
                "Could not synchronize blueprint settings", (byte) 0);
        if (!serialized.isValid()) {
            TaCZWeaponBlueprints.LOGGER.error("Could not synchronize blueprint settings: {}", serialized.getError());
            return;
        }
        network.send(new ConfigSyncS2CCustomPayload(
                ModConfigs.BLUEPRINT.getId().toLanguageKey(), serialized.get()), player);
    }
}
