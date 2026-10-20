package com.gamergaming.taczweaponblueprints.client.designer;

import net.neoforged.fml.common.EventBusSubscriber;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

@EventBusSubscriber(modid="taczweaponblueprints",value=Dist.CLIENT)
public final class ResearchDesignerEntry {
    public static final KeyMapping OPEN = new KeyMapping("key.taczweaponblueprints.open_designer",InputConstants.UNKNOWN.getValue(),"key.categories.taczweaponblueprints");
    public static void open() { var mc = Minecraft.getInstance(); mc.setScreen(new ResearchDesignerChooserScreen(mc.screen)); }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("researchdesigner")
                .executes(context -> { Minecraft.getInstance().execute(ResearchDesignerEntry::open); return 1; }));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        while (OPEN.consumeClick()) if (Minecraft.getInstance().screen == null) open();
    }
    @SubscribeEvent public static void menu(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen || event.getScreen() instanceof PauseScreen)
            event.addListener(Button.builder(Component.translatable("gui.taczweaponblueprints.designer.open"),b -> open()).bounds(6,6,114,20).build());
    }
    @EventBusSubscriber(modid="taczweaponblueprints",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) { event.register(OPEN); }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> {
                var mc=Minecraft.getInstance();mc.execute(()->{if(mc.screen instanceof ResearchDesignerWorkspaceScreen screen)screen.refreshResources();});
            });
        }
    }
    private ResearchDesignerEntry() {}
}
