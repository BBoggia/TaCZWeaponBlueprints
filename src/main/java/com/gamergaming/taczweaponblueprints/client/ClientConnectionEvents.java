package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.network.SyncResearchTreePacket;
import com.gamergaming.taczweaponblueprints.network.SyncBlueprintDataPacket;
import com.gamergaming.taczweaponblueprints.network.SyncBlueprintJournalPacket;
import com.gamergaming.taczweaponblueprints.network.SyncCraftingAccessPacket;
import com.gamergaming.taczweaponblueprints.network.SyncPlayerProgressionPacket;
import com.gamergaming.taczweaponblueprints.network.SyncPlayerRecipeDataPacket;
import com.gamergaming.taczweaponblueprints.network.SyncPlayerSupplementalProgressionPacket;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;

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

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onRecipesUpdated(net.minecraftforge.client.event.RecipesUpdatedEvent event) {
        ClientBlueprintCatalog.refreshOpenGunSmithScreen();
    }

    @SubscribeEvent
    public static void onItemTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        var stack=event.getItemStack();
        if (com.gamergaming.taczweaponblueprints.item.PhysicalWeaponProvenance.from(stack)
                .filter(p -> p.origin() == com.gamergaming.taczweaponblueprints.item.PhysicalWeaponProvenance.Origin.TRADED).isPresent())
            event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.taczweaponblueprints.traded_weapon").withStyle(net.minecraft.ChatFormatting.GRAY));
        if (com.gamergaming.taczweaponblueprints.item.BlueprintItem.getProvenance(stack)
                .filter(p -> p.source() == com.gamergaming.taczweaponblueprints.item.BlueprintProvenance.Source.TRADED).isPresent())
            event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.taczweaponblueprints.traded_blueprint").withStyle(net.minecraft.ChatFormatting.GRAY));
        if(stack.is(com.gamergaming.taczweaponblueprints.init.ModItems.RESEARCH_NOTE.get())
                ||stack.is(com.gamergaming.taczweaponblueprints.init.ModItems.RESEARCH_REPORT.get())
                ||stack.is(com.gamergaming.taczweaponblueprints.init.ModItems.RESEARCH_DOSSIER.get())) {
            event.getToolTip().add(ClientResearchCurrency.text("tooltip.taczweaponblueprints.data."+ClientResearchCurrency.dataMode())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if(ClientResearchCurrency.policy().item().filter(id->id.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()))).isPresent())
            event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.taczweaponblueprints.currency").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBlueprintJournal.clear();
        ClientResearchTree.clear();
        ClientResearchPlannerState.clear();
        ClientResearchGuidanceState.clear();
        ClientResearchAffordabilityState.clear();
        ClientResearchPointPresentationState.clear();
        ClientResearchCurrency.clear();
        SyncBlueprintDataPacket.clearClientState();
        SyncPlayerRecipeDataPacket.clearClientState();
        SyncPlayerProgressionPacket.clearClientState();
        SyncPlayerSupplementalProgressionPacket.clearClientState();
        SyncBlueprintJournalPacket.clearClientState();
        SyncResearchTreePacket.clearClientState();
        SyncCraftingAccessPacket.clearClientState();
        ClientCraftingAccessState.clear();
        BlueprintDataManager.CLIENT.clear();
        ClientBlueprintCatalog.invalidateCreativeTabs();
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
            if (com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.stationSetup().research()
                    == com.gamergaming.taczweaponblueprints.progression.StationSetup.ResearchAccess.PORTABLE
                    && com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.progressionSnapshot().researchEnabled()
                    && com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.progressionSnapshot().blueprintsEnabled()) {
                com.gamergaming.taczweaponblueprints.network.NetworkHandler.INSTANCE.sendToServer(
                        new com.gamergaming.taczweaponblueprints.network.OpenPortableResearchPacket());
            } else minecraft.setScreen(new BlueprintJournalScreen());
        }
    }
}
