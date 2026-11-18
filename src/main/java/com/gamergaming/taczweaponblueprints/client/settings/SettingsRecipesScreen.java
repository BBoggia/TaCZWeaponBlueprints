package com.gamergaming.taczweaponblueprints.client.settings;

import java.util.*;
import com.gamergaming.taczweaponblueprints.settings.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A recipe changes only the shared draft after the displayed before/after proposal is confirmed. */
public final class SettingsRecipesScreen extends Screen {
    private final Screen parent;
    private final SettingsEditSession draft;
    private SettingsRecipes.Preview proposal;
    private SettingsCardList list;
    private Button queue;
    public SettingsRecipesScreen(Screen parent) {
        super(text("recipes")); this.parent = parent; draft = ClientSettingsAccess.draft().orElseThrow();
    }
    private static Component text(String key, Object... args) { return Component.translatable("settings.taczweaponblueprints.ui." + key, args); }
    @Override protected void init() {
        list = addRenderableWidget(new SettingsCardList(minecraft, width, height - 95, 53));
        var summary = SettingsPresentation.describe(draft.baseline(), proposal == null ? draft.preview() : proposal.before());
        if (proposal == null) {
            for (var recipe : SettingsRecipes.Recipe.values()) {
                list.add(Button.builder(Component.translatable(recipe.key()), b -> {
                    proposal = SettingsRecipes.preview(draft, recipe); rebuildWidgets();
                }).build(), Component.translatable(recipe.key() + ".desc"));
            }
        } else {
            info(Component.translatable(proposal.recipe().key()), Component.translatable(proposal.recipe().key() + ".desc"));
            if (proposal.changes().isEmpty()) info(text("recipe_no_changes"), text("recipe_scope_hint"));
            for (var change : proposal.changes()) {
                var name = Component.translatable(SettingsCatalog.entry(change.field()).labelKey());
                Component before = value(change.field(), change.before(), summary), after = value(change.field(), change.after(), summary);
                info(name, text(change.replacesPending() ? "recipe_overwrites" : "recipe_change", before, after));
            }
        }
        info(text("recipe_retained"), text("recipe_retained_hint", QuickSetupText.currency(summary),
                SettingsValues.scalar((proposal == null ? draft.preview() : proposal.before()).get("activeResearchProfile"))));
        info(text("recipe_item_rules"), text(summary.specialCraftingRules() ? "recipe_custom_rules" : "recipe_scope_hint"));
        if (proposal != null && proposal.recipe() == SettingsRecipes.Recipe.SIMPLER_STATIONS) {
            info(text("recipe_research_kept"), text("recipe_research_kept_hint", QuickSetupText.category(summary.controls().get("enableResearch")), QuickSetupText.onOff(summary.craftingTiers())));
            info(text("recipe_analyzer"), text("recipe_analyzer_hint", value("singleAnalyzerDataTier", proposal.before().get("singleAnalyzerDataTier"), summary)));
        }
        addRenderableWidget(Button.builder(Component.translatable(proposal == null ? "gui.back" : "gui.cancel"), b -> onClose()).bounds(16, height - 29, 80, 20).build());
        queue = addRenderableWidget(Button.builder(text("queue_recipe"), b -> {
            if (!canQueue()) return;
            SettingsRecipes.queue(draft, proposal); minecraft.setScreen(parent);
        }).bounds(width - 216, height - 29, 200, 20).build());
        queue.visible = proposal != null; queue.active = canQueue();
    }
    private void info(Component name, Component description) { list.add(Button.builder(name, b -> {}).build(), description); }
    private Component value(String field, String document, SettingsPresentation.Summary summary) {
        String scalar = SettingsValues.scalar(document);
        if (scalar.equals("true") || scalar.equals("false")) return QuickSetupText.onOff(Boolean.parseBoolean(scalar));
        for (var control : SettingsCatalog.QuickControl.values()) {
            if (control != SettingsCatalog.QuickControl.CRAFTING_TIERS && control != SettingsCatalog.QuickControl.PACE
                    && control.presentationField().equals(field))
                return QuickSetupText.choice(control, document, summary);
        }
        return Component.translatable("config.taczweaponblueprints.blueprint." + field + "." + scalar);
    }
    private boolean canQueue() {
        return proposal != null && !proposal.changes().isEmpty() && draft == ClientSettingsAccess.draft().orElse(null)
                && draft.baseline().editable() && !ClientSettingsAccess.pending() && !ClientSettingsAccess.needsReview()
                && ClientSettingsAccess.status().orElse(null) == SettingsTransaction.Status.READY
                && proposal.equals(SettingsRecipes.preview(draft, proposal.recipe()));
    }
    @Override public void tick() {
        if (minecraft.getConnection() == null) { minecraft.setScreen(null); return; }
        if (draft != ClientSettingsAccess.draft().orElse(null)) { minecraft.setScreen(parent); return; }
        queue.active = canQueue();
    }
    @Override public void onClose() {
        if (proposal != null) { proposal = null; rebuildWidgets(); }
        else minecraft.setScreen(parent);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float dt) { g.fill(0, 0, width, height, 0xFF101820); }
    @Override public void render(GuiGraphics g, int mx, int my, float dt) {
        super.render(g, mx, my, dt);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        Component state = proposal == null ? text("recipe_match", SettingsRecipes.matching(draft.preview())
                .<Component>map(r -> Component.translatable(r.key())).orElse(text("recipe_custom"))) : text("recipe_preview_hint", proposal.changes().size());
        g.drawCenteredString(font, Component.literal(font.plainSubstrByWidth(state.getString(), width - 32)), width / 2, 28, 0xBCCDD8);
    }
}
