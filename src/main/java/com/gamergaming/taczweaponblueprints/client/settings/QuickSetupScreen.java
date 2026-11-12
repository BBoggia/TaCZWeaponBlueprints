package com.gamergaming.taczweaponblueprints.client.settings;

import java.util.*;
import com.gamergaming.taczweaponblueprints.settings.*;
import com.gamergaming.taczweaponblueprints.settings.SettingsCatalog.QuickControl;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;

/** Everyday controls and an effective preview over the same server draft as Advanced. */
public final class QuickSetupScreen extends Screen {
    private final Screen parent;
    private AdvancedSettingsScreen advanced;
    private SettingsEditSession session;
    private SettingsPresentation.Summary preview, applied;
    private Map<String, String> seenChanges = Map.of();
    private final Map<QuickControl, CycleButton<String>> choices = new EnumMap<>(QuickControl.class);
    private final Map<QuickControl, SettingsCardList.Card> cards = new EnumMap<>(QuickControl.class);
    private SettingsCardList list;
    private Button apply, refresh;
    private boolean summaryView, showApplied;
    private double controlsScroll, summaryScroll;
    private String searchQuery = "";
    private QuickControl focusedControl;

    public QuickSetupScreen(Screen parent) { super(SettingsLandingScreen.text("quick")); this.parent = parent; }
    private static Component text(String key) { return SettingsLandingScreen.text(key); }
    private Button button(Component label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(label, b -> action.run()).bounds(x, y, w, 20).build());
    }
    @Override protected void init() {
        rememberPosition();
        capture();
        rebuild();
    }
    private void capture() {
        session = ClientSettingsAccess.draft().orElse(null);
        if (session == null) { preview = null; applied = null; seenChanges = Map.of(); return; }
        // Resolve only on entry or draft changes, never while drawing frames.
        seenChanges = session.proposedChanges();
        applied = SettingsPresentation.describe(session.baseline(), session.baseline().values());
        preview = SettingsPresentation.describe(session.baseline(), session.preview());
    }
    private void rememberPosition() {
        if (list == null) return;
        if (summaryView) summaryScroll = list.getScrollAmount();
        else {
            controlsScroll = list.getScrollAmount();
            choices.forEach((c, w) -> { if (w.isFocused()) focusedControl = c; });
        }
    }
    @Override public void removed() { rememberPosition(); }
    private void rebuild() {
        clearWidgets(); choices.clear(); cards.clear();
        var search = new EditBox(font, 16, 55, width - 208, 20, text("search"));
        search.setMaxLength(128); search.setHint(text("search")); search.setValue(searchQuery);
        search.setResponder(value -> searchQuery = value);
        addRenderableWidget(search);
        button(text("find"), width - 188, 55, 64, () -> openAdvanced(null, searchQuery));
        button(text("recipes"), width - 120, 55, 104, () -> {
            if (ClientSettingsAccess.draft().isPresent()) minecraft.setScreen(new SettingsRecipesScreen(this));
        }).active = session != null;
        list = addRenderableWidget(new SettingsCardList(minecraft, width, height - 145, 81));
        if (session != null) {
            if (summaryView) buildSummary();
            else for (var control : QuickControl.values()) {
                var widget = CycleButton.<String>builder(value -> QuickSetupText.choice(control, value, preview))
                        .withValues(QuickSetupSettings.choices(control))
                        .withInitialValue(QuickSetupSettings.selected(control, preview))
                        .create(Component.translatable(QuickSetupSettings.labelKey(control)), (b, value) -> {
                            if (!editable(control)) { updateControls(true); return; }
                            QuickSetupSettings.select(session, control, value);
                            capture(); updateControls(true);
                        });
                choices.put(control, widget);
                var card = list.add(widget, QuickSetupText.hint(control, preview)); cards.put(control, card);
                if (control == focusedControl) { list.setFocused(card); card.setFocused(widget); }
            }
        }
        list.setScrollAmount(summaryView ? summaryScroll : controlsScroll);
        if (!summaryView && focusedControl != null) setFocused(list);
        int half = (width - 36) / 2;
        button(text(summaryView ? "quick" : "summary"), 16, height - 54, half, () -> {
            rememberPosition(); summaryView = !summaryView; rebuild();
        });
        button(text("advanced"), 20 + half, height - 54, half, () -> openAdvanced(null, ""));
        button(Component.translatable("gui.back"), 16, height - 29, 64, this::onClose);
        refresh = button(text(ClientSettingsAccess.needsReview() ? "review" : "refresh"), 84, height - 29, width - 268, () -> {
            if (ClientSettingsAccess.pending()) return;
            if (ClientSettingsAccess.needsReview()) minecraft.setScreen(new SettingsReviewScreen(this));
            else ClientSettingsAccess.refresh();
        });
        apply = button(text("apply"), width - 180, height - 29, 80, () -> {
            if (writable() && session.dirty()) ClientSettingsAccess.apply();
        });
        button(text("discard"), width - 96, height - 29, 80, () -> discard(false));
        updateControls(true);
    }
    private void buildSummary() {
        var summary = showApplied ? applied : preview;
        var toggle = Button.builder(text(showApplied ? "applied_snapshot" : session.dirty() ? "pending_preview" : "draft_preview"), b -> {
            rememberPosition(); showApplied = !showApplied; rebuild();
        }).build();
        list.add(toggle, text("summary_snapshot_hint"));
        summaryCard(QuickSetupText.text("weapons", QuickSetupText.effective(summary.weaponsResearch())),
                QuickSetupText.text("profile_hint", SettingsValues.scalar((showApplied ? session.baseline().values() : session.preview()).get("activeResearchProfile"))),
                SettingsCatalog.Category.INTEGRATIONS);
        for (var control : QuickControl.values()) {
            Component heading = Component.translatable(QuickSetupSettings.labelKey(control)).append(": ")
                    .append(QuickSetupText.choice(control, QuickSetupSettings.selected(control, summary), summary));
            summaryCard(heading, QuickSetupText.hint(control, summary), control == QuickControl.COSTS
                    ? SettingsCatalog.Category.ECONOMY : summary.controls().get(control.presentationField()).destination());
        }
    }
    private void summaryCard(Component heading, Component description, SettingsCatalog.Category destination) {
        list.add(Button.builder(heading, b -> openAdvanced(destination, "")).build(), description);
    }
    private void openAdvanced(SettingsCatalog.Category category, String query) {
        rememberPosition();
        if (advanced == null) advanced = new AdvancedSettingsScreen(this);
        advanced.navigate(category, query);
        minecraft.setScreen(advanced);
    }
    private boolean writable() {
        return session != null && session == ClientSettingsAccess.draft().orElse(null) && session.baseline().editable()
                && !ClientSettingsAccess.pending() && !ClientSettingsAccess.needsReview()
                && ClientSettingsAccess.status().orElse(null) == SettingsTransaction.Status.READY;
    }
    private boolean editable(QuickControl control) {
        return writable() && preview.controls().get(control.presentationField()).availability() == SettingsPresentation.Availability.EDITABLE;
    }
    private void updateControls(boolean valuesChanged) {
        choices.forEach((control, widget) -> {
            if (valuesChanged) {
                widget.setValue(QuickSetupSettings.selected(control, preview));
                cards.get(control).describe(QuickSetupText.hint(control, preview));
            }
            widget.active = editable(control);
        });
        apply.active = writable() && session.dirty();
        refresh.active = !ClientSettingsAccess.pending();
        refresh.setMessage(text(ClientSettingsAccess.needsReview() ? "review" : "refresh"));
    }
    @Override public void tick() {
        if (minecraft.getConnection() == null) { minecraft.setScreen(parent); return; }
        var current = ClientSettingsAccess.draft().orElse(null);
        if (current != session || current != null && !seenChanges.equals(current.proposedChanges())) {
            rememberPosition(); capture(); rebuild();
        }
        ClientSettingsAccess.acknowledgeApplied();
        updateControls(false);
    }
    private void discard(boolean close) {
        if (ClientSettingsAccess.pending()) return;
        Runnable discard = () -> {
            ClientSettingsAccess.discard();
            if (close) minecraft.setScreen(parent);
            else { ClientSettingsAccess.open(); capture(); rebuild(); }
        };
        if (session == null || !session.dirty()) { discard.run(); return; }
        minecraft.setScreen(new ConfirmScreen(ok -> { minecraft.setScreen(this); if (ok) discard.run(); },
                text("discard_title"), text("discard_body"), text("discard"), Component.translatable("gui.cancel")));
    }
    @Override public void onClose() {
        if (summaryView) { rememberPosition(); summaryView = false; rebuild(); }
        else discard(true);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if ((key == 257 || key == 335) && getFocused() instanceof EditBox) {
            openAdvanced(null, searchQuery); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0xFF101820);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float dt) {
        super.render(g, mx, my, dt);
        g.drawCenteredString(font, summaryView ? text("summary") : title, width / 2, 10, 0xFFFFFF);
        Component state = ClientSettingsAccess.pending() ? text("loading")
                : ClientSettingsAccess.status().<Component>map(s -> Component.translatable(s.translationKey())).orElse(text("loading"));
        if (!ClientSettingsAccess.pending() && ClientSettingsAccess.status().orElse(null) == SettingsTransaction.Status.READY && session != null)
            state = !session.baseline().editable() ? text("read_only") : session.dirty()
                    ? Component.translatable("settings.taczweaponblueprints.ui.pending", session.proposedChanges().size()) : text("applied_snapshot");
        g.drawCenteredString(font, Component.literal(font.plainSubstrByWidth(state.getString(), width - 24)), width / 2, 24, 0xCCCCCC);
        if (my >= 24 && my < 35 && font.width(state) > width - 32) g.renderTooltip(font, font.split(state, width - 40), mx, my);
        if (preview != null) g.drawCenteredString(font, Component.literal(font.plainSubstrByWidth(
                QuickSetupText.compact(summaryView && showApplied ? applied : preview).getString(), width - 24)), width / 2, 39, 0xBCCDD8);
    }
}
