package com.gamergaming.taczweaponblueprints.client.settings;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;

/** Enforces permissions around native widgets, including widgets that recompute active during rendering. */
final class SettingsFieldWidget extends AbstractWidget {
    private final AbstractWidget child;
    private final BooleanSupplier enabled;
    SettingsFieldWidget(AbstractWidget child,BooleanSupplier enabled,Component label){super(child.getX(),child.getY(),child.getWidth(),child.getHeight(),label);this.child=child;this.enabled=enabled;}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){
        child.setX(getX());child.setY(getY());child.setWidth(getWidth());child.active=enabled.getAsBoolean();child.render(g,mx,my,dt);
        if(!enabled.getAsBoolean()){g.fill(getX(),getY(),getX()+width,getY()+height,0x88000000);child.active=false;}
    }
    @Override public void setFocused(boolean value){super.setFocused(value);child.setFocused(value);}
    @Override public boolean mouseClicked(double x,double y,int b){return enabled.getAsBoolean()&&child.mouseClicked(x,y,b);}
    @Override public boolean mouseReleased(double x,double y,int b){return enabled.getAsBoolean()&&child.mouseReleased(x,y,b);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return enabled.getAsBoolean()&&child.mouseDragged(x,y,b,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){return enabled.getAsBoolean()&&child.mouseScrolled(x,y,h,v);}
    @Override public boolean keyPressed(int key,int scan,int mods){return enabled.getAsBoolean()&&child.keyPressed(key,scan,mods);}
    @Override public boolean keyReleased(int key,int scan,int mods){return enabled.getAsBoolean()&&child.keyReleased(key,scan,mods);}
    @Override public boolean charTyped(char c,int mods){return enabled.getAsBoolean()&&child.charTyped(c,mods);}
    @Override protected void updateWidgetNarration(NarrationElementOutput output){output.add(NarratedElementType.TITLE,getMessage());child.updateNarration(output.nest());}
}
