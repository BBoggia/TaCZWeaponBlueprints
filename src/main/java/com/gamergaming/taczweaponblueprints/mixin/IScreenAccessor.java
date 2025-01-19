package com.gamergaming.taczweaponblueprints.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.google.common.collect.Lists;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;

@Mixin(Screen.class)
public interface IScreenAccessor {

    @Accessor("font")
    Font getFont();

    @Accessor("children")
    List<GuiEventListener> getChildren();

    @Accessor("narratables")
    List<NarratableEntry> getNarratables();

    @Accessor("renderables")
    List<Renderable> getRenderables();

    // @Invoker("addRenderableWidget")
    // <T extends GuiEventListener & Renderable> T invokeAddRenderableWidget(T
    // widget);

    // @Invoker("addRenderableWidget")
    // <T extends GuiEventListener & Renderable & NarratableEntry> T
    // invokeAddRenderableWidget(T widget);
}
