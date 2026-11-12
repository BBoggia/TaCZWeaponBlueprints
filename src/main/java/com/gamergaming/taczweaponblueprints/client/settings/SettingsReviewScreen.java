package com.gamergaming.taczweaponblueprints.client.settings;

import java.util.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.gamergaming.taczweaponblueprints.settings.*;

/** Explicit comparison before a stale proposal is carried to the refreshed session. */
final class SettingsReviewScreen extends Screen {
    private final Screen parent;
    private final List<Map.Entry<String,String>> changes;
    private final SettingsSnapshot fresh;
    private final SettingsEditSession draft;
    private int page;
    SettingsReviewScreen(Screen parent) {
        super(SettingsLandingScreen.text("review")); this.parent=parent;
        draft=ClientSettingsAccess.draft().orElseThrow();
        changes=List.copyOf(draft.proposedChanges().entrySet());
        fresh=ClientSettingsAccess.latest().orElseThrow();
    }
    @Override protected void init() {
        if(changes.isEmpty()){onClose();return;}
        var entry=changes.get(page);
        addRenderableWidget(Button.builder(Component.translatable(SettingsCatalog.entry(entry.getKey()).labelKey()),b->{}).bounds(16,42,width-32,20).build());
        value("server_value",fresh.values().get(entry.getKey()),72);
        value("proposed_value",entry.getValue(),104);
        addRenderableWidget(Button.builder(SettingsLandingScreen.text("previous"),b->{page--;rebuildWidgets();}).bounds(16,height-56,80,20).build()).active=page>0;
        addRenderableWidget(Button.builder(SettingsLandingScreen.text("next"),b->{page++;rebuildWidgets();}).bounds(width-96,height-56,80,20).build()).active=page+1<changes.size();
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(16,height-30,80,20).build());
        addRenderableWidget(Button.builder(SettingsLandingScreen.text("keep_proposal"),b->{if(current()){ClientSettingsAccess.acceptReviewedChanges();onClose();}}).bounds(width-200,height-30,184,20).build()).active=fresh.editable() && current() && page==changes.size()-1;
    }
    private void value(String key,String value,int y) {
        String message=SettingsLandingScreen.text(key).getString()+" "+value.strip().replaceFirst("^value\\s*=\\s*","");
        var row=addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(message,width-48)),b->{}).bounds(16,y,width-32,20).build());
        row.setTooltip(Tooltip.create(Component.literal(message)));
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    private boolean current(){return ClientSettingsAccess.draft().orElse(null)==draft && ClientSettingsAccess.latest().orElse(null)==fresh && ClientSettingsAccess.needsReview() && !ClientSettingsAccess.pending() && ClientSettingsAccess.status().orElse(null)==SettingsTransaction.Status.READY && fresh.editable();}
    @Override public void tick(){if(minecraft.getConnection()==null)minecraft.setScreen(null);else if(ClientSettingsAccess.draft().orElse(null)!=draft || ClientSettingsAccess.latest().orElse(null)!=fresh)onClose();}
    @Override public void render(GuiGraphics g,int x,int y,float dt){super.render(g,x,y,dt);g.drawCenteredString(font,title,width/2,16,0xFFFFFF);g.drawCenteredString(font,(page+1)+" / "+changes.size(),width/2,height-50,0xAAAAAA);}
}
