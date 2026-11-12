package com.gamergaming.taczweaponblueprints.settings;

import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.network.SettingsConfigSyncPacket;
import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Uses Fzzy's normal dedicated sync; integrated hosts must not deserialize their own older publications. */
public final class SettingsSynchronization {
    private SettingsSynchronization() {}
    public static void publish(BlueprintConfig config, MinecraftServer server) {
        if (server.isDedicatedServer()) {
            ConfigApiJava.network().syncConfig(config, server);
            return;
        }
        var guests = server.getPlayerList().getPlayers().stream()
                .filter(player -> !player.connection.getConnection().isMemoryConnection()).toList();
        if (guests.isEmpty()) return;
        var packet = new SettingsConfigSyncPacket(FzzySettingsAdapter.capture(config));
        guests.forEach(player -> PacketDistributor.sendToPlayer(player, packet));
    }
}
