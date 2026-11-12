package com.gamergaming.taczweaponblueprints.client.settings;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

/** Vanilla scrolling, clipping, keyboard focus and narration for full-width settings cards. */
final class SettingsCardList extends ContainerObjectSelectionList<SettingsCardList.Card> {
    SettingsCardList(Minecraft minecraft, int width, int height, int top) {
        super(minecraft, width, height, top, width >= 600 ? 58 : 88);
    }
    @Override public int getRowWidth() { return width - 32; }
    @Override protected int getScrollbarPosition() { return getX() + width - 10; }
    @Override protected void renderListBackground(GuiGraphics graphics) {}
    @Override protected void renderListSeparators(GuiGraphics graphics) {}
    Card add(AbstractWidget widget, Component description) {
        var card = new Card(widget, description); addEntry(card); return card;
    }
    final class Card extends ContainerObjectSelectionList.Entry<Card> {
        private final AbstractWidget widget;
        private Component description;
        Card(AbstractWidget widget, Component description) { this.widget = widget; describe(description); }
        void describe(Component value) {
            description = value;
            widget.setTooltip(Tooltip.create(widget.getMessage().copy().append("\n").append(description)));
        }
        @Override public List<? extends GuiEventListener> children() { return List.of(widget); }
        @Override public List<? extends NarratableEntry> narratables() { return List.of(widget); }
        @Override public void render(GuiGraphics g, int index, int y, int x, int rowWidth, int rowHeight, int mx, int my, boolean hovered, float dt) {
            g.fill(x, y, x + rowWidth, y + rowHeight - 3, 0xAA1D2A35);
            widget.setX(x + 4); widget.setY(y + 4); widget.setWidth(rowWidth - 8);
            widget.render(g, mx, my, dt);
            var lines = minecraft.font.split(description, rowWidth - 12);
            for (int i = 0; i < Math.min((rowHeight - 30) / 10, lines.size()); i++)
                g.drawString(minecraft.font, lines.get(i), x + 6, y + 28 + i * 10, 0xC1CDD5);
        }
    }
}
