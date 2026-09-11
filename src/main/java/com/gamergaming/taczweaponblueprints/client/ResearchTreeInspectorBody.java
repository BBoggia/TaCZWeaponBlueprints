package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/** One keyboard stop for a scrolling semantic list. Arrow keys visit links; Enter follows. */
final class ResearchTreeInspectorBody extends AbstractWidget {
    private final Font font;
    private final Consumer<ResourceLocation> follow;
    private final IntConsumer scrollChanged;
    private List<ResearchTreeInspectorRow> rows = List.of();
    private List<Line> lines = List.of();
    private final ResearchTreeScrollAccumulator wheel = new ResearchTreeScrollAccumulator();
    private boolean draggingScrollbar;
    private double scrollbarGrab;
    private int scroll, activeRow = -1, wrappedWidth = -1;
    ResearchTreeInspectorBody(Font font, Consumer<ResourceLocation> follow, IntConsumer scrollChanged) {
        super(0, 0, 1, 1, Component.translatable("gui.taczweaponblueprints.research_bench.tree.inspector.details"));
        this.font = font; this.follow = follow; this.scrollChanged = scrollChanged;
    }
    void content(List<ResearchTreeInspectorRow> rows, int scroll) {
        if (!this.rows.equals(rows) || wrappedWidth != width) {
            var previousTarget = activeRow >= 0 && activeRow < this.rows.size() ? this.rows.get(activeRow).target() : java.util.Optional.<ResourceLocation>empty();
            this.rows = List.copyOf(rows); wrappedWidth = width; activeRow = -1;
            for (int i = 0; i < rows.size(); i++) if (previousTarget.isPresent() && rows.get(i).target().equals(previousTarget)) { activeRow = i; break; }
            var next = new ArrayList<Line>();
            for (int i = 0; i < rows.size(); i++) {
                for (var text : font.split(rows.get(i).text(), Math.max(1, width - 12))) next.add(new Line(i, text));
            }
            lines = List.copyOf(next);
            if (activeRow >= rows.size() || activeRow >= 0 && rows.get(activeRow).target().isEmpty()) activeRow = -1;
        }
        scrollTo(scroll);
    }
    void bounds(ResearchTreeScreenLayout.Rect b) { setX(b.x()); setY(b.y()); width = b.width(); height = b.height(); }
    void reset() { activeRow = -1; wheel.reset(); cancelDrag(); scrollTo(0); }
    void wheel(double delta) { scrollBy(wheel.consume(-delta * 30)); }
    void cancelDrag() { draggingScrollbar = false; }
    boolean draggingScrollbar() { return draggingScrollbar; }
    private int thumbHeight() { return Math.max(6, height * height / (height + maximumScroll())); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!draggingScrollbar || button != 0) return false;
        scrollTo((int) Math.round((y - getY() - scrollbarGrab) * maximumScroll() / Math.max(1, height - thumbHeight())));
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button != 0 || !draggingScrollbar) return false;
        cancelDrag(); return true;
    }
    int maximumScroll() { return Math.max(0, lines.size() * lineHeight() - height); }
    int scroll() { return scroll; }
    int activeRow() { return activeRow; }
    List<ResearchTreeInspectorRow> rows() { return rows; }
    void scrollTo(int value) {
        scroll = Math.max(0, Math.min(value, maximumScroll())); scrollChanged.accept(scroll);
    }
    void scrollBy(int delta) { scrollTo(scroll + delta); }
    private int lineHeight() { return font.lineHeight + 4; }
    private int rowAt(double y) {
        int line = (int) ((y - getY() + scroll) / lineHeight());
        return line >= 0 && line < lines.size() ? lines.get(line).row() : -1;
    }
    @Override public void onClick(double x, double y) {
        if (x >= getX() + width - 8 && maximumScroll() > 0) {
            int thumb = thumbHeight();
            double top = getY() + (height - thumb) * (double) scroll / maximumScroll();
            scrollbarGrab = y >= top && y < top + thumb ? y - top : thumb / 2.0;
            draggingScrollbar = true;
            mouseDragged(x, y, 0, 0, 0);
            return;
        }
        int row = rowAt(y);
        if (row >= 0) { activeRow = row; rows.get(row).target().ifPresent(follow); }
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!visible || !isFocused()) return false;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            if (activeRow >= 0) rows.get(activeRow).target().ifPresent(follow);
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            scrollBy((key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1) * Math.max(lineHeight(), height - lineHeight())); return true;
        }
        if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) { scrollTo(key == GLFW.GLFW_KEY_HOME ? 0 : maximumScroll()); return true; }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            int direction = key == GLFW.GLFW_KEY_UP ? -1 : 1;
            int start = activeRow < 0 ? (direction > 0 ? -1 : rows.size()) : activeRow;
            for (int i = start + direction; i >= 0 && i < rows.size(); i += direction) {
                if (rows.get(i).target().isEmpty()) continue;
                activeRow = i;
                for (int line = 0; line < lines.size(); line++) if (lines.get(line).row() == i) {
                    int y = line * lineHeight();
                    if (y < scroll) scrollTo(y); else if (y + lineHeight() > scroll + height) scrollTo(y + lineHeight() - height);
                    break;
                }
                return true;
            }
            scrollBy(direction * lineHeight()); return true;
        }
        return false;
    }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int hovered = isMouseOver(mouseX, mouseY) ? rowAt(mouseY) : -1;
        setTooltip(hovered >= 0 ? Tooltip.create(rows.get(hovered).text()) : null);
        g.enableScissor(getX(), getY(), getX() + width, getY() + height);
        try {
            for (int i = scroll / lineHeight(); i < lines.size(); i++) {
                int y = getY() + i * lineHeight() - scroll;
                if (y >= getY() + height) break;
                var line = lines.get(i); var row = rows.get(line.row());
                if (row.target().isPresent() && (line.row() == hovered || isFocused() && line.row() == activeRow))
                    g.fill(getX(), y - 1, getX() + width - 7, y + lineHeight() - 1, 0xFF34495C);
                g.drawString(font, line.text(), getX() + 2, y, row.color(), false);
                if (row.target().isPresent()) g.fill(getX() + 2, y + font.lineHeight, getX() + 2 + font.width(line.text()), y + font.lineHeight + 1, row.color());
            }
            g.flush();
        } finally { g.disableScissor(); }
        if (maximumScroll() > 0) {
            int thumb = Math.max(6, height * height / (height + maximumScroll()));
            int y = getY() + (height - thumb) * scroll / maximumScroll();
            g.fill(getX() + width - 4, y, getX() + width - 2, y + thumb, 0xFFABB8C6);
        }
        if (isFocused()) g.renderOutline(getX(), getY(), width, height, 0xFFA0B9D0);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, activeRow >= 0 ? rows.get(activeRow).text() : getMessage());
        output.add(NarratedElementType.USAGE, Component.translatable("gui.taczweaponblueprints.research_bench.tree.inspector.keyboard"));
    }
    private record Line(int row, FormattedCharSequence text) { }
}
