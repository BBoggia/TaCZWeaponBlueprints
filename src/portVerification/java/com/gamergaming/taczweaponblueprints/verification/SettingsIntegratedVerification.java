package com.gamergaming.taczweaponblueprints.verification;

/** Client-class references stay out of the dedicated fixture's executed path. */
final class SettingsIntegratedVerification {
    static void publishLan() {
        var server = (net.minecraft.client.server.IntegratedServer) CoreVerification.server;
        server.setUsesAuthentication(false); // Disposable offline QA accounts only.
        CoreVerification.require(server.publishServer(net.minecraft.world.level.GameType.SURVIVAL, false, 25594), "disposable integrated server opens LAN port");
    }
}
