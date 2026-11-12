package com.gamergaming.taczweaponblueprints.verification;

import static com.gamergaming.taczweaponblueprints.verification.ClientVerification.*;
import com.gamergaming.taczweaponblueprints.client.settings.ClientSettingsAccess;
import com.gamergaming.taczweaponblueprints.settings.*;
import com.gamergaming.taczweaponblueprints.network.SettingsReplyPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;

final class SettingsInterruptionsClientVerification {
    private static int phase, step;
    static void tick() {
        if (phase != expected) { phase = expected; step = 0; }
        if (wait < 10) return;
        if (phase == 431) {
            var config = com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT;
            require(!net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer(), "LAN guest uses a separate remote client");
            require(!config.enableGunsmith.get() && !config.customEnforceCraftingTiers.getUnconditional()
                    && config.customEnforceResearchTiers.getUnconditional() && !config.enableJournal.getUnconditional(),
                    "LAN guest receives complete simple, compound and Journal settings updates");
            int stage = expected; expected = 0; PacketDistributor.sendToServer(new CoreVerification.Probe(stage)); return;
        }
        boolean opening = phase == 412 || phase == 414 || phase == 420 || phase == 421;
        if (step == 0) {
            if (opening) ClientSettingsAccess.open(); else ClientSettingsAccess.apply();
            step = 1; return;
        }
        if (ClientSettingsAccess.pending()) return;
        if (opening) {
            require(ClientSettingsAccess.status().orElseThrow() == SettingsTransaction.Status.READY, "fresh session for interrupted/concurrent edit " + phase);
            var draft = ClientSettingsAccess.draft().orElseThrow();
            require(draft.baseline().editable(), "concurrent operator has actual server permission " + phase);
            draft.setBoolean(phase == 420 ? "enableGunsmith" : "enableBallisticsResearcher", phase == 420);
            ClientSettingsAccess.accept(new SettingsReplyPacket(UUID.randomUUID(), SettingsTransaction.Status.APPLIED, null));
            require(ClientSettingsAccess.draft().orElseThrow() == draft && draft.dirty(), "uncorrelated reply cannot replace a pending proposal");
        } else {
            var wanted = phase == 422 ? SettingsTransaction.Status.APPLIED : SettingsTransaction.Status.STALE;
            require(ClientSettingsAccess.status().orElseThrow() == wanted, "interrupted/concurrent apply returns " + wanted + " at " + phase);
            if (wanted == SettingsTransaction.Status.STALE) require(ClientSettingsAccess.draft().orElseThrow().dirty(), "interrupted proposal retained until explicit discard");
            ClientSettingsAccess.discard();
        }
        int stage = expected; expected = 0; PacketDistributor.sendToServer(new CoreVerification.Probe(stage));
    }
}
