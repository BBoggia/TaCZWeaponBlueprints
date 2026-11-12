package com.gamergaming.taczweaponblueprints.client.settings;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class SettingsLandingScreen extends Screen {
    private final Screen parent;
    public SettingsLandingScreen(Screen parent) { super(text("title")); this.parent = parent; }
    static Component text(String key) { return Component.translatable("settings.taczweaponblueprints.ui." + key); }
    @Override protected void init() {
        int x = width / 2 - 140, y = Math.max(48, height / 2 - 45);
        addRenderableWidget(Button.builder(text("personal"), b -> SettingsScreens.nativeScreen("research_tree_client")).bounds(x,y,280,20).build());
        boolean connected = minecraft.getConnection() != null;
        addRenderableWidget(Button.builder(text(connected ? "world" : "defaults"), b -> {
            if (!connected) SettingsScreens.nativeScreen("blueprint");
            else { if(ClientSettingsAccess.draft().isEmpty() && !ClientSettingsAccess.pending()) ClientSettingsAccess.open(); minecraft.setScreen(new QuickSetupScreen(this)); }
        }).bounds(x,y+28,280,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(x,y+68,280,20).build());
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void render(GuiGraphics g,int mx,int my,float dt) {
        super.render(g,mx,my,dt); g.drawCenteredString(font,title,width/2,20,0xFFFFFF);
        g.drawCenteredString(font,text(minecraft.getConnection()==null ? "defaults_hint" : minecraft.hasSingleplayerServer() ? "integrated_hint" : "server_hint"),width/2,34,0xAAAAAA);
    }
}
