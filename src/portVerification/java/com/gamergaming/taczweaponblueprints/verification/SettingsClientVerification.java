package com.gamergaming.taczweaponblueprints.verification;

import static com.gamergaming.taczweaponblueprints.verification.ClientVerification.*;
import com.gamergaming.taczweaponblueprints.client.settings.ClientSettingsAccess;
import com.gamergaming.taczweaponblueprints.settings.*;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;

final class SettingsClientVerification {
    private static int phase, step;
    private static UUID oldSession;
    private static int readOnlyTicks;
    static void tick() {
        if (expected >= 412) { SettingsInterruptionsClientVerification.tick(); return; }
        if (phase != expected) { phase = expected; step = 0; }
        if (wait < 10) return; // Also leaves room for the production request rate limit.
        if (phase == 401 || phase == 402 || phase == 403 || phase == 405 || phase == 406 || phase == 407 || phase == 410) {
            if (step == 0) {
                if (phase == 410) require(ClientSettingsAccess.draft().isEmpty(), "disconnect clears the previous server draft");
                ClientSettingsAccess.open(); step = 1; return;
            }
            if (ClientSettingsAccess.pending()) return;
            if (step == 1) {
                if(phase==402 && !SettingsScreenVerification.tick())return;
                require(ClientSettingsAccess.status().orElseThrow() == SettingsTransaction.Status.READY, "authoritative settings read " + phase);
                var draft = ClientSettingsAccess.draft().orElseThrow();
                if (phase == 401) {
                    require(!draft.baseline().editable(), "non-operator sees read-only server settings");
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (readOnlyTicks++ == 0) { mc.setScreen(new com.gamergaming.taczweaponblueprints.client.settings.QuickSetupScreen(null)); return; }
                    if (readOnlyTicks < 5) return;
                    require(SettingsUsabilityVerification.descendants(mc.screen).stream().filter(net.minecraft.client.gui.components.CycleButton.class::isInstance)
                            .map(net.minecraft.client.gui.components.CycleButton.class::cast).noneMatch(w -> w.active), "non-operator rendered Quick controls are disabled");
                    var apply = mc.screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast)
                            .filter(b -> b.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("settings.taczweaponblueprints.ui.apply").getString())).findFirst().orElseThrow();
                    apply.onPress(); apply.onPress();
                    require(!ClientSettingsAccess.pending() && !draft.dirty(), "read-only Apply action cannot submit even before the next UI tick");
                    mc.setScreen(null);
                    ClientSettingsAccess.discard(); done();
                } else if (phase == 402) {
                    require(draft.baseline().editable(), "operator receives an editable server session");
                    draft.setBoolean("enableResearch", false);
                    require(ModConfigs.BLUEPRINT.enableResearch.getUnconditional(), "draft change leaves Fzzy client config untouched");
                    ClientSettingsAccess.discard(); done();
                } else if (phase == 403) { draft.setBoolean("enableResearch", false); done(); }
                else if (phase == 407) {
                    draft.setBoolean("enableBallisticsResearcher", false);
                    require(!com.gamergaming.taczweaponblueprints.client.ClientBlueprintJournal.snapshot().entries().isEmpty(), "Journal initially contains the synchronized catalog");
                    net.minecraft.client.Minecraft.getInstance().setScreen(new com.gamergaming.taczweaponblueprints.client.BlueprintJournalScreen());
                    done();
                }
                else if (phase == 410) {
                    require(!draft.baseline().session().equals(oldSession), "reconnect gets a new settings session");
                    require(!SettingsValues.booleanValue(draft.preview().get("enableGunsmith"))
                            && !SettingsValues.booleanValue(draft.preview().get("customEnforceCraftingTiers")), "reconnect sees persisted simple and compound settings");
                    ClientSettingsAccess.discard(); done();
                } else {
                    if (phase == 405) draft.setBoolean("enableGunsmith", false); else draft.setCraftingTiers(false);
                    oldSession = draft.baseline().session(); step = 2; wait = 0;
                }
                return;
            }
        }
        if ((phase == 404 || phase == 408) && step == 0 || (phase == 405 || phase == 406) && step == 2) {
            ClientSettingsAccess.apply(); step = 3; return;
        }
        if (step == 3 && !ClientSettingsAccess.pending()) {
            var expectedStatus = phase == 404 ? SettingsTransaction.Status.DENIED : phase == 408 ? SettingsTransaction.Status.STALE : SettingsTransaction.Status.APPLIED;
            require(ClientSettingsAccess.status().orElseThrow() == expectedStatus, "authoritative apply outcome " + expectedStatus);
            if (phase == 404 || phase == 408) require(ClientSettingsAccess.draft().orElseThrow().dirty(), "rejected proposal retained for review");
            if (phase == 405) require(!ModConfigs.BLUEPRINT.enableGunsmith.get(), "public Fzzy sync publishes simple edit to client");
            if (phase == 406) require(!ModConfigs.BLUEPRINT.customEnforceCraftingTiers.getUnconditional()
                    && ModConfigs.BLUEPRINT.customEnforceResearchTiers.getUnconditional(), "public Fzzy sync publishes complete compound edit to client");
            if (phase == 408) {
                require(com.gamergaming.taczweaponblueprints.client.ClientBlueprintJournal.snapshot().entries().isEmpty(), "disabling Journal clears its synchronized entries while its screen is open");
                net.minecraft.client.Minecraft.getInstance().setScreen(null);
                step = 4; wait = 0; return;
            }
            ClientSettingsAccess.discard(); done();
        }
        if (phase == 408 && step == 4) { ClientSettingsAccess.refresh(); step = 5; return; }
        if (phase == 408 && step == 5 && !ClientSettingsAccess.pending()) {
            require(ClientSettingsAccess.needsReview() && ClientSettingsAccess.draft().orElseThrow().dirty(), "refresh retains the stale proposal for explicit review");
            require(!SettingsValues.booleanValue(ClientSettingsAccess.latest().orElseThrow().values().get("enableJournal")), "refreshed comparison contains the other operator's change");
            ClientSettingsAccess.acceptReviewedChanges();
            require(!ClientSettingsAccess.needsReview() && ClientSettingsAccess.draft().orElseThrow().plan().changes().keySet().equals(java.util.Set.of("enableBallisticsResearcher")), "explicit review carries only the proposed field to the fresh session");
            // Leave this proposal pending so the real logout event, rather than this fixture, must clear it.
            oldSession = ClientSettingsAccess.draft().orElseThrow().baseline().session(); done();
        }
    }
    private static void done() { int stage = expected; expected = 0; PacketDistributor.sendToServer(new CoreVerification.Probe(stage)); }
}
