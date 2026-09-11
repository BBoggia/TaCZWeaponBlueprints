package com.gamergaming.taczweaponblueprints.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** One modal focus stop for the inspector's two camera actions. */
final class ResearchTreeViewMenu extends AbstractWidget {
    private static final String PREFIX = "gui.taczweaponblueprints.research_bench.tree.inspector.view.";
    private final Font font;
    private final Button[] actions;
    private final Runnable dismiss;
    private int selected;

    ResearchTreeViewMenu(Font font, Runnable focusTarget, Runnable focusBranch, Runnable dismiss) {
        super(0, 0, 1, 1, Component.translatable(PREFIX + "title"));
        this.font = font; this.dismiss = dismiss;
        actions = new Button[]{Button.builder(Component.translatable(PREFIX + "target"), b -> focusTarget.run()).build(),
                Button.builder(Component.translatable(PREFIX + "branch"), b -> focusBranch.run()).build()};
        visible = false;
    }
    void open(ResearchTreeWorkbenchLayout layout, boolean branchAvailable) {
        int measured = Math.max(font.width(actions[0].getMessage()), font.width(actions[1].getMessage())) + 16;
        width = Math.min(layout.inspectorContent().width(), Math.max(112, measured)); height = 48;
        setX(Math.max(layout.inspectorContent().x(), layout.target().right() - width)); setY(layout.target().y() - height - 4);
        for (int i = 0; i < actions.length; i++) {
            actions[i].setX(getX()+4); actions[i].setY(getY()+4+i*20); actions[i].setWidth(width-8);
        }
        actions[1].active = branchAvailable; selected = 0; visible = true;
    }
    @Override public void onClick(double x, double y) {
        for (int i = 0; i < actions.length; i++) if (actions[i].isMouseOver(x,y) && actions[i].active) {
            selected = i; actions[i].onPress(); return;
        }
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!visible) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) dismiss.run();
        else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) actions[selected].onPress();
        else if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_TAB) {
            if (actions[1].active) selected = 1-selected;
        }
        return true;
    }
    @Override protected void renderWidget(GuiGraphics g, int x, int y, float tick) {
        g.fill(getX(),getY(),getX()+width,getY()+height,0xff18232e);
        g.renderOutline(getX(),getY(),width,height,0xff85a2a5);
        for (int i = 0; i < actions.length; i++) { actions[i].setFocused(isFocused() && i == selected); actions[i].render(g,x,y,tick); }
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, getMessage().copy().append(": ").append(actions[selected].getMessage()));
        out.add(NarratedElementType.USAGE, Component.translatable(PREFIX + "keyboard"));
    }
}
