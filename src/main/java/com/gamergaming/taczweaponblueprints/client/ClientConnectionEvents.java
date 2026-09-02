package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.network.SyncResearchTreePacket;
import com.gamergaming.taczweaponblueprints.network.SyncBlueprintJournalPacket;
import com.gamergaming.taczweaponblueprints.network.SyncPlayerProgressionPacket;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Prevents one server's disclosure-filtered Journal from surviving logout. */
@Mod.EventBusSubscriber(
        modid = TaCZWeaponBlueprints.MODID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientConnectionEvents {
    private static final ResearchTreeGuidancePreference ONBOARDING_PREFERENCE =
            ResearchTreeGuidancePreference.client();

    private ClientConnectionEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBlueprintJournal.clear();
        ClientResearchTree.clear();
        ClientResearchPlannerState.clear();
        ClientResearchGuidanceState.clear();
        ClientResearchAffordabilityState.clear();
        ClientResearchPointPresentationState.clear();
        SyncPlayerProgressionPacket.clearClientState();
        SyncBlueprintJournalPacket.clearClientState();
        SyncResearchTreePacket.clearClientState();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        ClientResearchState.Publication publication = ClientResearchState.publication();
        boolean hasPublishedResearch = publication.generation() != Long.MIN_VALUE
                && (!publication.journal().entries().isEmpty()
                        || !publication.graph().nodes().isEmpty());
        if (minecraft.player != null
                && hasPublishedResearch
                && ONBOARDING_PREFERENCE.claimOnboardingHint()) {
            minecraft.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.taczweaponblueprints.onboarding.journal_hint",
                            BlueprintJournalKeyMappings.OPEN_JOURNAL.getTranslatedKeyMessage()),
                    false);
        }
        boolean openRequested = false;
        while (BlueprintJournalKeyMappings.OPEN_JOURNAL.consumeClick()) {
            openRequested = true;
        }
        if (!openRequested) {
            return;
        }
        if (minecraft.player != null && minecraft.level != null && minecraft.screen == null) {
            minecraft.setScreen(new BlueprintJournalScreen());
        }
    }
}
