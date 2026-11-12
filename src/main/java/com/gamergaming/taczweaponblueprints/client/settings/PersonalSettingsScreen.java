package com.gamergaming.taczweaponblueprints.client.settings;

import java.util.*;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.PersonalSettingsSession;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.settings.SettingsTransaction.Status;
import me.fzzyhmstrs.fzzy_config.screen.PopupWidgetScreen;
import me.fzzyhmstrs.fzzy_config.validation.misc.ChoiceValidator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;

/** Only the seven active client preferences. Compatibility fields remain in the original config. */
public final class PersonalSettingsScreen extends PopupWidgetScreen {
    private final Screen parent;
    private PersonalSettingsSession session;
    private Status status = Status.READY;
    private int page;
    private Button apply;
    private final Map<AbstractWidget, String> editors = new LinkedHashMap<>();
    public PersonalSettingsScreen(Screen parent) { super(SettingsLandingScreen.text("personal")); this.parent = parent; }
    private static Component text(String key) { return SettingsLandingScreen.text(key); }
    private static Component label(String field) { return Component.translatable("config.taczweaponblueprints.research_tree_client." + field); }
    private Button button(Component text, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(text, b -> action.run()).bounds(x, y, width, 20).build());
    }
    private void read() {
        try {
            session = new PersonalSettingsSession(ModConfigs.RESEARCH_TREE_CLIENT, PersonalSettingsSession.fzzyStore(ModConfigs.RESEARCH_TREE_CLIENT));
            status = Status.READY;
        } catch (Exception error) { session = null; status = Status.UNAVAILABLE; }
    }
    @Override protected void init() { if (session == null) read(); rebuild(); }
    private boolean editable(String field) {
        return session != null && status == Status.READY && (!field.equals("holdDurationMillis") || session.editor().holdToResearch.get());
    }
    private void rebuild() {
        clearWidgets(); editors.clear();
        int rows = Math.max(1, (height - 146) / 36);
        page = Math.min(page, (PersonalSettingsSession.VISIBLE.size() - 1) / rows);
        if (session != null) for (int i = page * rows; i < Math.min((page + 1) * rows, PersonalSettingsSession.VISIBLE.size()); i++) {
            String id = PersonalSettingsSession.VISIBLE.get(i); int y = 54 + (i - page * rows) * 36;
            var name = button(Component.literal(font.plainSubstrByWidth(label(id).getString(), width - 194)), 16, y, width - 182, () -> {});
            name.setTooltip(Tooltip.create(label(id).copy().append("\n").append(label(id + ".desc"))));
            var field = PersonalSettingsSession.field(session.editor(), id);
            field.setTranslatableEntryKey("config.taczweaponblueprints.research_tree_client." + id);
            var child = field.widgetEntry(ChoiceValidator.Companion.any());
            child.setX(width - 160); child.setY(y); child.setWidth(144);
            var guarded = new SettingsFieldWidget(child, () -> editable(id), label(id));
            addRenderableWidget(guarded); editors.put(guarded, id);
        }
        button(text("previous"), 16, height - 86, 80, () -> { page--; rebuild(); }).active = page > 0;
        button(text("next"), width - 96, height - 86, 80, () -> { page++; rebuild(); }).active = (page + 1) * rows < PersonalSettingsSession.VISIBLE.size();
        button(label("resetTreeAppearance.button"), 16, height - 60, width - 32, () -> {
            if (session == null || status != Status.READY) return;
            minecraft.setScreen(new ConfirmScreen(ok -> {
                if (ok) session.resetAppearance();
                minecraft.setScreen(this);
            }, label("resetTreeAppearance.button"), text("personal_reset_scope"), text("queue_reset"), Component.translatable("gui.cancel")));
        }).active = session != null && status == Status.READY;
        button(Component.translatable("gui.back"), 16, height - 29, 80, this::onClose);
        apply = button(text("apply"), width - 180, height - 29, 80, () -> {
            if (session == null || status != Status.READY || !session.dirty()) return;
            status = session.apply();
            if (status == Status.APPLIED || status == Status.NO_CHANGES) read();
            rebuild();
        });
        button(text("discard"), width - 96, height - 29, 80, () -> discard(false));
        update();
    }
    private void update() {
        apply.active = session != null && status == Status.READY && session.dirty();
        editors.forEach((widget, field) -> widget.active = editable(field));
    }
    @Override public void tick() { update(); }
    private void discard(boolean close) {
        Runnable action = () -> { if (close) minecraft.setScreen(parent); else { read(); rebuild(); } };
        if (session == null || !session.dirty()) { action.run(); return; }
        minecraft.setScreen(new ConfirmScreen(ok -> { minecraft.setScreen(this); if (ok) action.run(); },
                text("discard_title"), text("discard_body"), text("discard"), Component.translatable("gui.cancel")));
    }
    @Override public void onClose() { discard(true); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderContents(GuiGraphics g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0xFF101820); super.renderContents(g, mx, my, dt);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        g.drawCenteredString(font, text("personal_scope"), width / 2, 24, 0xBCCDD8);
        var state = status == Status.READY ? text(session != null && session.dirty() ? "personal_pending" : "applied_snapshot")
                : text("personal_status." + status.name().toLowerCase(Locale.ROOT));
        g.drawCenteredString(font, Component.literal(font.plainSubstrByWidth(state.getString(), width - 32)), width / 2, 38, 0xCCCCCC);
        g.drawCenteredString(font, Integer.toString(page + 1), width / 2, height - 80, 0xCCCCCC);
        editors.forEach((widget, field) -> {
            if (field.equals("holdDurationMillis") && session != null && !session.editor().holdToResearch.get())
                g.drawString(font, font.plainSubstrByWidth(label("condition.holdToResearch").getString(), width - 32), 16, widget.getY() + 23, 0xAAAAAA);
        });
    }
}
