package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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
    private final IntConsumer materialPage;
    private List<ResearchTreeInspectorRow> rows = List.of();
    private List<Line> lines = List.of();
    private final ResearchTreeScrollAccumulator wheel = new ResearchTreeScrollAccumulator();
    private boolean draggingScrollbar;
    private double scrollbarGrab;
    private ResearchTreeCostColumns costColumns;
    private int contentHeight;
    private int scroll, activeRow = -1, wrappedWidth = -1;
    ResearchTreeInspectorBody(Font font, Consumer<ResourceLocation> follow, IntConsumer scrollChanged, IntConsumer materialPage) {
        super(0, 0, 1, 1, Component.translatable("gui.taczweaponblueprints.research_bench.tree.inspector.details"));
        this.font = font; this.follow = follow; this.scrollChanged = scrollChanged;
        this.materialPage = materialPage;
    }
    void content(List<ResearchTreeInspectorRow> rows, int scroll) {
        if (!this.rows.equals(rows) || wrappedWidth != width) {
            var previousTarget = activeRow >= 0 && activeRow < this.rows.size() ? this.rows.get(activeRow).target() : java.util.Optional.<ResourceLocation>empty();
            int previousPage = activeRow >= 0 && activeRow < this.rows.size() ? this.rows.get(activeRow).materialPageDirection() : 0;
            this.rows = List.copyOf(rows); wrappedWidth = width; activeRow = -1;
            for (int i = 0; i < rows.size(); i++) if (previousTarget.isPresent() && rows.get(i).target().equals(previousTarget)) { activeRow = i; break; }
            if (previousPage != 0) for (int i = 0; i < rows.size(); i++) if (rows.get(i).materialPageDirection() == previousPage) { activeRow = i; break; }
            costColumns = ResearchTreeCostColumns.measure(width, rows, font::width);
            var next = new ArrayList<Line>();
            int y = 0;
            for (int i = 0; i < rows.size(); i++) {
                var kind = rows.get(i).kind();
                if (i > 0 && kind == ResearchTreeInspectorRow.Kind.SECTION) y += 9;
                else if (i > 0 && kind == ResearchTreeInspectorRow.Kind.GROUP) y += 3;
                int indent = kind == ResearchTreeInspectorRow.Kind.MATERIAL
                        || kind == ResearchTreeInspectorRow.Kind.PREREQUISITE || kind == ResearchTreeInspectorRow.Kind.EXTERNAL_LINK ? 8 : 2;
                var row = rows.get(i);
                var textLines = font.split(row.text(), row.costCells().isPresent()
                        ? costColumns.resourceWidth() : Math.max(1, width - 12 - indent));
                var haveLines = row.costCells().map(c -> font.split(c.have(), costColumns.haveWidth())).orElse(List.of());
                var requiredLines = row.costCells().map(c -> font.split(c.required(), costColumns.requiredWidth())).orElse(List.of());
                int lineCount = Math.max(textLines.size(), Math.max(haveLines.size(), requiredLines.size()));
                for (int n = 0; n < lineCount; n++) {
                    next.add(new Line(i, lineOrEmpty(textLines, n), y, indent,
                            lineOrEmpty(haveLines, n), lineOrEmpty(requiredLines, n)));
                    y += lineHeight();
                }
            }
            contentHeight = y;
            lines = List.copyOf(next);
            if (activeRow >= rows.size() || activeRow >= 0 && !rows.get(activeRow).interactive()) activeRow = -1;
        }
        scrollTo(scroll);
    }
    private static FormattedCharSequence lineOrEmpty(List<FormattedCharSequence> lines, int index) {
        return index < lines.size() ? lines.get(index) : FormattedCharSequence.EMPTY;
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
    int maximumScroll() { return Math.max(0, contentHeight - height); }
    int scroll() { return scroll; }
    int activeRow() { return activeRow; }
    List<ResearchTreeInspectorRow> rows() { return rows; }
    void scrollTo(int value) {
        scroll = Math.max(0, Math.min(value, maximumScroll())); scrollChanged.accept(scroll);
    }
    void scrollBy(int delta) { scrollTo(scroll + delta); }
    private int lineHeight() { return font.lineHeight + 4; }
    private int firstLineAt(int y) {
        int lo = 0, hi = lines.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).y() + lineHeight() <= y) lo = mid + 1; else hi = mid;
        }
        return lo;
    }
    private int rowAt(double y) {
        int contentY = (int) (y - getY() + scroll), index = firstLineAt(contentY);
        return index < lines.size() && contentY >= lines.get(index).y() ? lines.get(index).row() : -1;
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
        if (row >= 0) { activeRow = row; activate(rows.get(row)); }
    }
    private void activate(ResearchTreeInspectorRow row) {
        row.target().ifPresent(follow);
        if (row.materialPageDirection() != 0) materialPage.accept(row.materialPageDirection());
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!visible || !isFocused()) return false;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            if (activeRow >= 0) activate(rows.get(activeRow));
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
                if (!rows.get(i).interactive()) continue;
                activeRow = i;
                for (int line = 0; line < lines.size(); line++) if (lines.get(line).row() == i) {
                    int y = lines.get(line).y();
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
        g.enableScissor(getX(), getY(), getX() + width, getY() + height);
        try {
            for (int i = firstLineAt(scroll); i < lines.size(); i++) {
                int y = getY() + lines.get(i).y() - scroll;
                if (y >= getY() + height) break;
                var line = lines.get(i); var row = rows.get(line.row());
                if (row.interactive() && (line.row() == hovered || isFocused() && line.row() == activeRow))
                    g.fill(getX(), y - 1, getX() + width - 7, y + lineHeight() - 1, 0xFF34495C);
                if (row.kind() == ResearchTreeInspectorRow.Kind.COST_HEADER)
                    g.fill(getX(), y - 1, getX() + width - 8, y + lineHeight() - 1, 0xFF243541);
                if (row.costCells().isPresent()) {
                    var cells = row.costCells().orElseThrow();
                    g.drawString(font, line.have(), getX() + costColumns.haveX() + costColumns.haveWidth() - font.width(line.have()),
                            y, cells.haveColor(), false);
                    g.drawString(font, line.required(), getX() + costColumns.requiredX() + costColumns.requiredWidth() - font.width(line.required()),
                            y, row.color(), false);
                }
                g.drawString(font, line.text(), getX() + line.indent(), y, row.color(), false);
                if (row.interactive()) g.fill(getX() + line.indent(), y + font.lineHeight, getX() + line.indent() + font.width(line.text()), y + font.lineHeight + 1, row.color());
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
        output.add(NarratedElementType.TITLE, activeRow >= 0 ? rows.get(activeRow).narration() : getMessage());
        output.add(NarratedElementType.USAGE, Component.translatable("gui.taczweaponblueprints.research_bench.tree.inspector.keyboard"));
    }
    private record Line(int row, FormattedCharSequence text, int y, int indent,
            FormattedCharSequence have, FormattedCharSequence required) { }
}
