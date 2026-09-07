package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService;

import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import com.gamergaming.taczweaponblueprints.menu.AnalyzerPresentation;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerPreview;
import com.gamergaming.taczweaponblueprints.network.BlueprintRecyclerActionPacket;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/** Two-slot Analyzer with a scalable, localization-safe information panel. */
public final class BlueprintRecyclerScreen
        extends AbstractContainerScreen<BlueprintRecyclerMenu>
        implements BlueprintRecyclerActionResultListener {
    private static final int PANEL = 0xF0141920;
    private static final int SECTION = 0xE0202730;
    private static final int SLOT = 0xFF0B0F14;
    private static final int BORDER = 0xFF68798C;
    private static final int MUTED = 0xFF9FAAB5;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int ACCENT = 0xFFE4C56A;
    private static final int GOOD = 0xFF70C98B;
    private static final int BAD = 0xFFFF7777;
    private final BlueprintRecyclerRequestTracker requests =
            new BlueprintRecyclerRequestTracker();
    private final BlueprintRecyclerFeedbackState feedback =
            new BlueprintRecyclerFeedbackState();
    private Button primaryButton;
    private Button secondaryButton;
    private Optional<BlueprintRecyclerActionContract.Action> primaryAction = Optional.empty();
    private Optional<BlueprintRecyclerActionContract.Action> secondaryAction = Optional.empty();
    private Optional<BlueprintRecyclerActionContract.Action> confirmationAction = Optional.empty();
    private int confirmationTicks;
    private int detailScroll;
    private static final int DETAIL_LINES = 6;
    private static final String ANALYZER = "gui.taczweaponblueprints.analyzer.";
    private BlueprintRecyclerPreview observedPreview = BlueprintRecyclerPreview.EMPTY;

    public BlueprintRecyclerScreen(
            BlueprintRecyclerMenu menu,
            Inventory inventory,
            Component title) {
        super(menu, inventory, title);
        imageWidth = BlueprintRecyclerMenu.Layout.PANEL_WIDTH;
        imageHeight = BlueprintRecyclerMenu.Layout.PANEL_HEIGHT;
        titleLabelX = 10;
        titleLabelY = 7;
        inventoryLabelX = BlueprintRecyclerMenu.Layout.PLAYER_X;
        inventoryLabelY = BlueprintRecyclerMenu.Layout.PLAYER_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();
        observedPreview = menu.preview();
        primaryButton = addRenderableWidget(Button.builder(
                Component.empty(), ignored -> primaryAction.ifPresent(this::request))
                .bounds(
                        leftPos + BlueprintRecyclerMenu.Layout.DETAIL_X,
                        topPos + BlueprintRecyclerMenu.Layout.ACTION_Y,
                        BlueprintRecyclerMenu.Layout.DETAIL_WIDTH,
                        BlueprintRecyclerMenu.Layout.ACTION_HEIGHT)
                .build());
        int splitButtonWidth = splitButtonWidth();
        secondaryButton = addRenderableWidget(Button.builder(
                Component.empty(), ignored -> secondaryAction.ifPresent(this::request))
                .bounds(
                        leftPos + BlueprintRecyclerMenu.Layout.DETAIL_X
                                + splitButtonWidth
                                + BlueprintRecyclerMenu.Layout.ACTION_GAP,
                        topPos + BlueprintRecyclerMenu.Layout.ACTION_Y,
                        splitButtonWidth,
                        BlueprintRecyclerMenu.Layout.ACTION_HEIGHT)
                .build());
        primaryButton.setTabOrderGroup(0);
        secondaryButton.setTabOrderGroup(1);
        updateControls();
        setFocused(null);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        BlueprintRecyclerPreview currentPreview = menu.preview();
        if (!currentPreview.equals(observedPreview)) {
            clearConfirmation();
            if (!currentPreview.inputId().equals(observedPreview.inputId())) detailScroll = 0;
            feedback.reconcile(currentPreview);
            observedPreview = currentPreview;
            triggerImmediateNarration(true);
        }
        if (requests.tick()) {
            feedback.show(
                    BlueprintRecyclerActionContract.ResultCode.TRANSACTION_FAILED,
                    currentPreview);
            triggerImmediateNarration(true);
        } else if (feedback.tick()) {
            triggerImmediateNarration(true);
        }
        if (confirmationTicks > 0 && --confirmationTicks == 0) {
            confirmationAction = Optional.empty();
        }
        updateControls();
    }

    @Override
    public void onClose() {
        requests.clear();
        feedback.clear();
        clearConfirmation();
        super.onClose();
    }

    @Override
    public void acceptRecyclerActionResult(
            int requestId,
            BlueprintRecyclerActionContract.ActionResult result) {
        if (!requests.accept(requestId, result)) {
            return;
        }
        feedback.accept(result, menu.preview());
        detailScroll = 0;
        updateControls();
        triggerImmediateNarration(true);
    }

    private void request(BlueprintRecyclerActionContract.Action action) {
        BlueprintRecyclerPreview preview = menu.preview();
        if (action == BlueprintRecyclerActionContract.Action.RECOVER_POINTS
                && !preview.alreadyKnown()
                && confirmationAction.filter(action::equals).isEmpty()) {
            confirmationAction = Optional.of(action);
            confirmationTicks = 100;
            updateControls();
            triggerImmediateNarration(true);
            return;
        }
        clearConfirmation();
        ResourceLocation inputId = preview.inputId().orElse(null);
        Optional<BlueprintRecyclerRequestTracker.Request> started =
                requests.begin(
                        action,
                        inputId,
                        preview.inputCount(),
                        preview.stateToken());
        if (started.isEmpty()) {
            return;
        }
        BlueprintRecyclerRequestTracker.Request request = started.orElseThrow();
        feedback.clear();
        detailScroll = 0;
        try {
            NetworkHandler.INSTANCE.sendToServer(new BlueprintRecyclerActionPacket(
                    menu.containerId,
                    request.requestId(),
                    request.action(),
                    request.inputId(),
                    request.inputCount(),
                    request.stateToken()));
        } catch (RuntimeException exception) {
            requests.clear();
            feedback.show(
                    BlueprintRecyclerActionContract.ResultCode.TRANSACTION_FAILED,
                    menu.preview());
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Failed to send Blueprint Recycler request {} for {}",
                    request.requestId(), request.inputId(), exception);
            triggerImmediateNarration(true);
        }
        updateControls();
    }

    private void updateControls() {
        if (primaryButton == null || secondaryButton == null) {
            return;
        }
        BlueprintRecyclerScreenModel model =
                BlueprintRecyclerScreenModel.from(menu.preview(), requests.pending());
        primaryAction = model.primaryAction();
        secondaryAction = model.secondaryAction();

        boolean split = secondaryAction.isPresent();
        primaryButton.setWidth(split
                ? splitButtonWidth()
                : BlueprintRecyclerMenu.Layout.DETAIL_WIDTH);
        primaryButton.visible = primaryAction.isPresent();
        primaryButton.active = primaryButton.visible && model.controlsEnabled();
        primaryButton.setMessage(primaryAction
                .map(action -> Component.translatable(
                        actionMessageKey(action)))
                .orElse(Component.empty()));
        primaryButton.setTooltip(primaryAction
                .map(action -> Tooltip.create(actionTooltip(action)))
                .orElse(null));

        secondaryButton.visible = secondaryAction.isPresent();
        secondaryButton.active = secondaryButton.visible && model.controlsEnabled();
        secondaryButton.setMessage(secondaryAction
                .map(action -> Component.translatable(
                        actionMessageKey(action)))
                .orElse(Component.empty()));
        secondaryButton.setTooltip(secondaryAction
                .map(action -> Tooltip.create(actionTooltip(action)))
                .orElse(null));
        primaryButton.setMessage(clipped(primaryButton.getMessage(), primaryButton.getWidth() - 8));
        secondaryButton.setMessage(clipped(secondaryButton.getMessage(), secondaryButton.getWidth() - 8));
        if (getFocused() == primaryButton && !primaryButton.visible
                || getFocused() == secondaryButton && !secondaryButton.visible) {
            setFocused(null);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderDetailsTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, BORDER);
        graphics.fill(
                leftPos + BlueprintRecyclerMenu.Layout.SECTION_X,
                topPos + BlueprintRecyclerMenu.Layout.SECTION_Y,
                leftPos + BlueprintRecyclerMenu.Layout.SECTION_X
                        + BlueprintRecyclerMenu.Layout.SECTION_WIDTH,
                topPos + BlueprintRecyclerMenu.Layout.SECTION_Y
                        + BlueprintRecyclerMenu.Layout.SECTION_HEIGHT,
                SECTION);
        graphics.renderOutline(
                leftPos + BlueprintRecyclerMenu.Layout.SECTION_X,
                topPos + BlueprintRecyclerMenu.Layout.SECTION_Y,
                BlueprintRecyclerMenu.Layout.SECTION_WIDTH,
                BlueprintRecyclerMenu.Layout.SECTION_HEIGHT,
                BORDER);
        graphics.fill(
                leftPos + BlueprintRecyclerMenu.Layout.DIVIDER_X,
                topPos + BlueprintRecyclerMenu.Layout.SECTION_Y + 1,
                leftPos + BlueprintRecyclerMenu.Layout.DIVIDER_X + 1,
                topPos + BlueprintRecyclerMenu.Layout.SECTION_Y
                        + BlueprintRecyclerMenu.Layout.SECTION_HEIGHT - 1,
                BORDER);
        drawSlot(graphics, BlueprintRecyclerMenu.Layout.INPUT_X,
                BlueprintRecyclerMenu.Layout.INPUT_Y, ACCENT);
        drawSlot(graphics, BlueprintRecyclerMenu.Layout.OUTPUT_X,
                BlueprintRecyclerMenu.Layout.OUTPUT_Y, GOOD);
        graphics.drawCenteredString(
                font,
                Component.literal("→"),
                leftPos + (BlueprintRecyclerMenu.Layout.INPUT_X
                        + BlueprintRecyclerMenu.Layout.OUTPUT_X) / 2 + 8,
                topPos + BlueprintRecyclerMenu.Layout.INPUT_Y + 4,
                MUTED);
        drawPlayerInventory(graphics);
    }

    private void drawPlayerInventory(GuiGraphics graphics) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(
                        graphics,
                        BlueprintRecyclerMenu.Layout.PLAYER_X
                                + column * BlueprintRecyclerMenu.Layout.PLAYER_SPACING,
                        BlueprintRecyclerMenu.Layout.PLAYER_Y
                                + row * BlueprintRecyclerMenu.Layout.PLAYER_SPACING,
                        BORDER);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(
                    graphics,
                    BlueprintRecyclerMenu.Layout.PLAYER_X
                            + column * BlueprintRecyclerMenu.Layout.PLAYER_SPACING,
                    BlueprintRecyclerMenu.Layout.HOTBAR_Y,
                    BORDER);
        }
    }

    private void drawSlot(GuiGraphics graphics, int x, int y, int borderColor) {
        graphics.fill(leftPos + x - 1, topPos + y - 1, leftPos + x + 17, topPos + y + 17, SLOT);
        graphics.renderOutline(leftPos + x - 1, topPos + y - 1, 18, 18, borderColor);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        BlueprintRecyclerPreview preview = menu.preview();
        BlueprintRecyclerScreenModel model =
                BlueprintRecyclerScreenModel.from(preview, requests.pending());
        graphics.drawString(font, clipped(title, imageWidth - 20), titleLabelX, titleLabelY, TEXT, false);
        if (preview.presentation().stationTier() > 0) graphics.drawString(font, clipped(tr("tier", preview.presentation().stationTier()), 92), 14, 24, MUTED, false);
        graphics.drawCenteredString(
                font,
                clipped(
                        Component.translatable(
                                "gui.taczweaponblueprints.blueprint_recycler.input"),
                        42),
                BlueprintRecyclerMenu.Layout.INPUT_X + 8,
                35,
                MUTED);
        graphics.drawCenteredString(
                font,
                clipped(
                        Component.translatable(
                                "gui.taczweaponblueprints.blueprint_recycler.output"),
                        42),
                BlueprintRecyclerMenu.Layout.OUTPUT_X + 8,
                35,
                MUTED);
        if (detailed(preview)) {
            renderAnalyzerDetails(graphics, preview, model);
            graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
            return;
        }
        graphics.drawString(
                font,
                clipped(
                        (feedback.visibleOutcome().isPresent() || feedback.visibleFragments().isPresent()) ? tr("result.title") : Component.translatable(model.headingKey()),
                        BlueprintRecyclerMenu.Layout.DETAIL_WIDTH),
                BlueprintRecyclerMenu.Layout.DETAIL_X,
                30,
                TEXT,
                false);
        Optional<BlueprintRecyclerActionContract.ResultCode> visibleFeedback =
                visibleFeedback(preview);
        Component status = status(model, visibleFeedback);
        if (visibleFeedback.isEmpty()
                && model.statusEmphasis()
                        == BlueprintRecyclerScreenModel.StatusEmphasis.NOTICE) {
            graphics.fill(
                    BlueprintRecyclerMenu.Layout.DETAIL_X - 4,
                    40,
                    BlueprintRecyclerMenu.Layout.DETAIL_X
                            + BlueprintRecyclerMenu.Layout.DETAIL_WIDTH,
                    71,
                    0x60362E17);
            graphics.renderOutline(
                    BlueprintRecyclerMenu.Layout.DETAIL_X - 4,
                    40,
                    BlueprintRecyclerMenu.Layout.DETAIL_WIDTH + 4,
                    31,
                    ACCENT);
        }
        int statusColor = requests.pending() || feedback.visibleOutcome().filter(o -> o.kind() == com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable.Kind.NO_RESULT).isPresent()
                ? ACCENT
                : visibleFeedback.isPresent()
                        ? visibleFeedback.orElseThrow()
                                == BlueprintRecyclerActionContract.ResultCode.SUCCESS ? GOOD : BAD
                        : switch (model.statusEmphasis()) {
                            case POSITIVE -> GOOD;
                            case NOTICE -> ACCENT;
                            case MUTED -> MUTED;
                        };
        drawLimitedLines(
                graphics,
                status,
                BlueprintRecyclerMenu.Layout.DETAIL_X,
                44,
                BlueprintRecyclerMenu.Layout.DETAIL_WIDTH,
                3,
                statusColor);
        if (model.summaryVisible()) {
            drawLimitedLines(
                    graphics,
                    summary(preview),
                    BlueprintRecyclerMenu.Layout.DETAIL_X,
                    76,
                    BlueprintRecyclerMenu.Layout.DETAIL_WIDTH,
                    2,
                    ACCENT);
        }
        graphics.drawString(
                font,
                playerInventoryTitle,
                inventoryLabelX,
                inventoryLabelY,
                MUTED,
                false);
    }

    private void drawLimitedLines(
            GuiGraphics graphics,
            Component text,
            int x,
            int y,
            int width,
            int maximumLines,
            int color) {
        int lineNumber = 0;
        for (FormattedCharSequence line : font.split(text, width)) {
            graphics.drawString(
                    font,
                    line,
                    x,
                    y + lineNumber * (font.lineHeight + 1),
                    color,
                    false);
            if (++lineNumber == maximumLines) {
                break;
            }
        }
    }

    private static int splitButtonWidth() {
        return (BlueprintRecyclerMenu.Layout.DETAIL_WIDTH
                - BlueprintRecyclerMenu.Layout.ACTION_GAP) / 2;
    }

    private Component clipped(Component component, int width) {
        String value = component.getString();
        if (font.width(value) <= width) {
            return component;
        }
        String suffix = "…";
        return Component.literal(
                font.plainSubstrByWidth(value, Math.max(0, width - font.width(suffix))) + suffix);
    }

    private Optional<BlueprintRecyclerActionContract.ResultCode> visibleFeedback(
            BlueprintRecyclerPreview preview) {
        feedback.reconcile(preview);
        return feedback.visibleCode();
    }

    private Component status(
            BlueprintRecyclerScreenModel model,
            Optional<BlueprintRecyclerActionContract.ResultCode> visibleFeedback) {
        if (requests.pending()) {
            return Component.translatable(
                    "gui.taczweaponblueprints.blueprint_recycler.processing");
        }
        if (visibleFeedback.isPresent() && feedback.visibleOutcome().isPresent()) {
            var outcome = feedback.visibleOutcome().orElseThrow();
            return tr("result." + outcome.kind().name().toLowerCase(java.util.Locale.ROOT), outcome.count());
        }
        if (visibleFeedback.isPresent() && feedback.visibleFragments().isPresent()) {
            var f = feedback.visibleFragments().orElseThrow();
            return tr(f.blueprintCreated() ? "result.reconstructed" : "result.archived", f.archived(), f.retained(), f.saved());
        }
        return visibleFeedback
                .map(result -> Component.translatable(
                        BlueprintRecyclerScreenModel.resultKey(result)))
                .orElseGet(() -> Component.translatable(model.statusKey()));
    }

    private Component summary(BlueprintRecyclerPreview preview) {
        if (preview.inputKind() == BlueprintRecyclerPreview.InputKind.EMPTY && feedback.visibleTarget().isPresent())
            return targetName(feedback.visibleTarget().orElseThrow());
        if (preview.inputKind() == BlueprintRecyclerPreview.InputKind.BLUEPRINT_FRAGMENT) {
            var fragment = preview.fragmentEvaluation().orElseThrow();
            if (fragment.awardedPoints() > 0) {
                return Component.translatable(
                        "gui.taczweaponblueprints.blueprint_recycler.fragment.summary_rp",
                        fragment.archivedBefore(),
                        fragment.archivedAfterAction(),
                        fragment.threshold(),
                        fragment.awardedPoints());
            }
            return Component.translatable(
                    fragment.createsBlueprint()
                            ? "gui.taczweaponblueprints.blueprint_recycler.fragment.summary_reconstruct"
                            : "gui.taczweaponblueprints.blueprint_recycler.fragment.summary",
                    fragment.archivedBefore(),
                    fragment.archivedAfterDeposit(),
                    fragment.threshold());
        }
        if (preview.inputKind() == BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM) {
            if (preview.recoveryPointValue() > 0) {
                return Component.translatable(
                        preview.customizationWillBeLost()
                                ? "gui.taczweaponblueprints.blueprint_recycler.reverse.summary_recovery_modified"
                                : "gui.taczweaponblueprints.blueprint_recycler.reverse.summary_recovery",
                        preview.pointCost(),
                        preview.recoveryPointValue(),
                        preview.pointBalance(),
                        preview.pointCap());
            }
            return Component.translatable(
                    preview.customizationWillBeLost()
                            ? "gui.taczweaponblueprints.blueprint_recycler.reverse.summary_modified"
                            : "gui.taczweaponblueprints.blueprint_recycler.reverse.summary",
                    preview.requiredInputCount(),
                    preview.pointCost());
        }
        if (preview.pointValue() > 0) {
            return Component.translatable(
                    preview.inputKind() == BlueprintRecyclerPreview.InputKind.RESEARCH_DATA
                            ? "gui.taczweaponblueprints.blueprint_recycler.summary_next"
                            : "gui.taczweaponblueprints.blueprint_recycler.summary",
                    preview.pointValue(), preview.pointBalance(), preview.pointCap());
        }
        return Component.translatable(
                "gui.taczweaponblueprints.blueprint_recycler.balance",
                preview.pointBalance(),
                preview.pointCap());
    }

    private Component detailsNarration() {
        BlueprintRecyclerPreview preview = menu.preview();
        BlueprintRecyclerScreenModel model =
                BlueprintRecyclerScreenModel.from(preview, requests.pending());
        if (detailed(preview)) {
            MutableComponent result = title.copy();
            for (Component line : analyzerLines(preview, model)) result.append("\n").append(line);
            for (Component line : sidebarLines(preview)) result.append("\n").append(line);
            return result.append("\n").append(actionSummary(model));
        }
        MutableComponent details = model.summaryVisible()
                ? Component.translatable(
                        "gui.taczweaponblueprints.blueprint_recycler.narration",
                        Component.translatable(model.headingKey()),
                        status(model, visibleFeedback(preview)),
                        summary(preview),
                        actionSummary(model))
                : Component.translatable(
                        "gui.taczweaponblueprints.blueprint_recycler.narration.compact",
                        Component.translatable(model.headingKey()),
                        status(model, visibleFeedback(preview)),
                        actionSummary(model));
        if (model.summaryVisible()
                && preview.inputKind()
                        == BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM) {
            for (BlueprintRecyclerPreview.IngredientPreview ingredient
                    : preview.ingredients()) {
                details.append("\n").append(Component.translatable(
                        "gui.taczweaponblueprints.blueprint_recycler.reverse.material",
                        ingredientLabel(ingredient),
                        ingredient.inventoryAvailable(),
                        ingredient.required()));
            }
            details.append("\n").append(Component.translatable(
                    "gui.taczweaponblueprints.blueprint_recycler.reverse.origin."
                            + preview.weaponOrigin().name().toLowerCase(java.util.Locale.ROOT)));
            if (confirmationAction.isPresent()) {
                details.append("\n").append(Component.translatable(
                        "gui.taczweaponblueprints.blueprint_recycler.action.recover_points.confirm_hint"));
            }
        }
        return details;
    }

    private String actionMessageKey(BlueprintRecyclerActionContract.Action action) {
        if (action == BlueprintRecyclerActionContract.Action.ARCHIVE_FRAGMENTS)
            return ANALYZER + (menu.preview().fragmentEvaluation().filter(f -> f.createsBlueprint()).isPresent() ? "action.reconstruct" : "action.archive");
        if (action == BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER && menu.preview().presentation().weapon().isPresent())
            return ANALYZER + "action.analyze";
        String base = BlueprintRecyclerScreenModel.actionKey(action);
        return confirmationAction.filter(action::equals).isPresent()
                ? base + ".confirm"
                : base;
    }

    private String actionTooltipKey(BlueprintRecyclerActionContract.Action action) {
        String base = BlueprintRecyclerScreenModel.actionKey(action);
        if (action == BlueprintRecyclerActionContract.Action.RECOVER_POINTS
                && !menu.preview().alreadyKnown()) {
            return base + ".warning";
        }
        return base + ".tooltip";
    }

    private void clearConfirmation() {
        confirmationAction = Optional.empty();
        confirmationTicks = 0;
    }

    private Component ingredientLabel(
            BlueprintRecyclerPreview.IngredientPreview ingredient) {
        if (ingredient.tag().isPresent()) {
            return Component.literal("#" + ingredient.tag().orElseThrow());
        }
        MutableComponent label = Component.empty();
        int shown = Math.min(3, ingredient.items().size());
        for (int index = 0; index < shown; index++) {
            if (index > 0) {
                label.append(" / ");
            }
            ResourceLocation id = ingredient.items().get(index);
            Item item = ForgeRegistries.ITEMS.getValue(id);
            label.append(item == null ? Component.literal(id.toString()) : item.getDescription());
        }
        if (ingredient.items().size() > shown) {
            label.append(Component.translatable(
                    "gui.taczweaponblueprints.blueprint_recycler.reverse.alternatives_more",
                    ingredient.items().size() - shown));
        }
        return label;
    }

    private Component actionSummary(BlueprintRecyclerScreenModel model) {
        if (model.primaryAction().isEmpty()) {
            return Component.translatable(
                    "gui.taczweaponblueprints.blueprint_recycler.narration.no_action");
        }
        if (!model.controlsEnabled()) {
            return Component.translatable(
                    "gui.taczweaponblueprints.blueprint_recycler.narration.waiting");
        }
        Component primary = Component.translatable(
                actionMessageKey(model.primaryAction().orElseThrow()));
        return model.secondaryAction()
                .<Component>map(action -> Component.translatable(
                        "gui.taczweaponblueprints.blueprint_recycler.narration.actions",
                        primary,
                        Component.translatable(actionMessageKey(action))))
                .orElse(primary);
    }

    private void renderDetailsTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {
        if (detailed(menu.preview())) {
            int x = mouseX - leftPos, y = mouseY - topPos;
            Component hover = null;
            if (x >= BlueprintRecyclerMenu.Layout.DETAIL_X && x < imageWidth - 12 && y >= 29 && y < 95) {
                var lines = analyzerLines(menu.preview(), BlueprintRecyclerScreenModel.from(menu.preview(), requests.pending()));
                int index = detailScroll + (y - 29) / 11;
                if (index < lines.size()) hover = lines.get(index);
            } else if (x >= 12 && x < BlueprintRecyclerMenu.Layout.DIVIDER_X - 2 && y >= 80 && y < 120) {
                var lines = sidebarLines(menu.preview());
                int index = (y - 80) / 10;
                if (index < lines.size()) hover = lines.get(index);
            } else if (y >= 5 && y < 19 && x >= 8 && x < imageWidth - 8) hover = title;
            if (hover != null) graphics.renderTooltip(font, font.split(hover, Math.max(80, Math.min(220, width - 32))), mouseX, mouseY);
            return;
        }
        if (mouseX < leftPos + BlueprintRecyclerMenu.Layout.DETAIL_X
                || mouseX >= leftPos + BlueprintRecyclerMenu.Layout.DETAIL_X
                        + BlueprintRecyclerMenu.Layout.DETAIL_WIDTH
                || mouseY < topPos + 28
                || mouseY >= topPos + BlueprintRecyclerMenu.Layout.ACTION_Y) {
            return;
        }
        int maximumWidth = Math.max(80, Math.min(220, width - 32));
        graphics.renderTooltip(
                font,
                font.split(detailsNarration(), maximumWidth),
                mouseX,
                mouseY);
    }

    private static Component tr(String key, Object... values) { return Component.translatable(ANALYZER + key, values); }

    private boolean detailed(BlueprintRecyclerPreview p) {
        return p.inputKind() == BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM
                || p.inputKind() == BlueprintRecyclerPreview.InputKind.BLUEPRINT_FRAGMENT;
    }

    private Component targetName(BlueprintRecyclerPreview p) {
        return p.inputId().<Component>map(this::targetName).orElse(Component.empty());
    }
    private Component targetName(ResourceLocation id) {
        var data = BlueprintDataManager.presentationCatalog().getBlueprintData(id.toString());
        if (data == null) return Component.literal(id.toString());
        var name = Component.translatable(data.getNameKey());
        return name.getString().equals(data.getNameKey()) ? Component.literal(id.toString()) : name;
    }

    private List<Component> sidebarLines(BlueprintRecyclerPreview p) {
        var lines = new ArrayList<Component>();
        if (p.inputKind() == BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM) {
            boolean recoveryFocused = BlueprintRecyclerScreenModel.focusesRecovery(p);
            var c = p.presentation().recoveryCost();
            lines.add(tr("consumes", recoveryFocused && c.isPresent() ? c.orElseThrow().inputCount() : p.requiredInputCount()));
            lines.add(tr(recoveryFocused && c.isPresent() ? (c.orElseThrow().ingredients().isEmpty() ? "cost" : "cost_materials")
                    : (p.ingredients().isEmpty() ? "cost" : "cost_materials"), recoveryFocused && c.isPresent() ? c.orElseThrow().points() : p.pointCost()));
            if (p.presentation().recoveryCost().isPresent() && !recoveryFocused) {
                var cost = p.presentation().recoveryCost().orElseThrow();
                lines.add(cost.status() != com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService.Status.READY
                        ? tr("recovery_cost_pending", cost.points())
                        : tr(cost.ingredients().isEmpty() ? "recovery_cost" : "recovery_cost_materials", cost.points(), p.recoveryPointValue()));
            }
            if (recoveryFocused && c.filter(cost -> cost.status() == com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService.Status.READY).isPresent())
                lines.add(tr("recovery_award", p.recoveryPointValue()));
            if (!recoveryFocused) p.presentation().weapon().filter(w -> w.reward().isPresent() && w.threshold() > 0).ifPresent(w -> lines.add(tr("saved", w.saved(), w.threshold())));
        } else {
            var f = p.fragmentEvaluation().orElseThrow();
            lines.add(tr("accepts", f.accepted()));
            lines.add(tr("keeps", f.rejected()));
            lines.add(f.status() == com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentAnalysisService.Status.FRAGMENTS_DISABLED
                    ? tr("saved_count", f.archivedBefore()) : tr("saved", f.archivedBefore(), f.threshold()));
        }
        if (lines.size() < 4) lines.add(tr("balance", p.pointBalance()));
        return List.copyOf(lines);
    }

    private List<Component> analyzerLines(BlueprintRecyclerPreview p, BlueprintRecyclerScreenModel model) {
        var lines = new ArrayList<Component>();
        boolean recoveryFocused = BlueprintRecyclerScreenModel.focusesRecovery(p);
        lines.add(targetName(p));
        var weapon = p.presentation().weapon();
        lines.add(weapon.isPresent() ? tr("tiers", p.presentation().stationTier(),
                weapon.orElseThrow().tier() == 0 ? "?" : weapon.orElseThrow().tier()) : tr("station", p.presentation().stationTier()));
        Component decision = status(model, visibleFeedback(p));
        if (!requests.pending() && visibleFeedback(p).isEmpty() && p.fragmentEvaluation().filter(f -> f.ready() && f.createsBlueprint()).isPresent())
            decision = tr("ready_reconstruct");
        lines.add(decision);
        if (recoveryFocused && p.reverseEngineeringStatus().filter(s -> s != BlueprintReverseEngineeringService.Status.RECOVERY_MODE_DISABLED).isPresent())
            lines.add(tr("analysis_status", Component.translatable(BlueprintRecyclerScreenModel.analysisStatusKey(p))));
        if (weapon.isPresent() && !recoveryFocused) {
            var w = weapon.orElseThrow();
            w.outcomes().ifPresent(o -> {
                lines.add(tr("odds.blueprint", AnalyzerProbabilityText.format(o.blueprintWeight(), o.totalWeight())));
                lines.add(tr("odds.fragments", o.minimumFragments() == o.maximumFragments() ? Integer.toString(o.minimumFragments())
                        : o.minimumFragments() + "–" + o.maximumFragments(), AnalyzerProbabilityText.format(o.fragmentWeight(), o.totalWeight())));
                lines.add(tr("odds.no_result", AnalyzerProbabilityText.format(o.noResultWeight(), o.totalWeight())));
            });
            if (w.reward().isPresent() && w.threshold() > 0) {
                lines.add(tr("saved", w.saved(), w.threshold()));
                if (w.saved() >= w.threshold()) lines.add(tr("stored_set"));
                if (w.totalSaved() >= w.cap()) lines.add(tr("archive_full"));
                lines.add(tr("archive", w.totalSaved(), w.cap()));
            }
            w.reward().ifPresent(reward -> { lines.add(tr("learning." + (reward.learningMode() == com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES ? "bypass" : "prerequisites")));
                lines.add(tr(reward.recyclable() ? "recyclable" : "protected")); });
            if (w.blueprintLearningDisabled()) lines.add(tr("learning.disabled"));
        }
        if (p.inputKind() == BlueprintRecyclerPreview.InputKind.BLUEPRINT_FRAGMENT) {
            var f = p.fragmentEvaluation().orElseThrow();
            lines.add(f.status() == com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentAnalysisService.Status.FRAGMENTS_DISABLED
                    ? tr("saved_count", f.archivedBefore()) : tr("fragment.saved", f.archivedBefore(), f.threshold()));
            lines.add(tr("fragment.transfer", f.accepted(), f.rejected()));
            lines.add(tr(f.createsBlueprint() ? "fragment.reconstruct" : "fragment.after", f.archivedAfterAction()));
            if (f.awardedPoints() > 0) lines.add(tr("fragment.rp", f.awardedPoints()));
            p.presentation().fragments().ifPresent(a -> {
                lines.add(tr("archive", a.totalSaved(), a.cap()));
                a.outputReward().ifPresent(reward -> {
                    lines.add(tr(reward.recyclable() ? "recyclable" : "protected"));
                    lines.add(tr("learning." + (reward.learningMode() == com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES ? "bypass" : "prerequisites")));
                });
                lines.add(tr("fragment.separate"));
            });
        } else {
            if (!recoveryFocused) {
                lines.add(tr("analysis_status", Component.translatable(BlueprintRecyclerScreenModel.analysisStatusKey(p))));
                lines.add(tr("consumption", p.requiredInputCount(), p.pointCost()));
                for (var i : p.ingredients()) lines.add(materialLine(i));
            }
            if (p.customizationWillBeLost()) lines.add(tr("modified"));
            lines.add(Component.translatable("gui.taczweaponblueprints.blueprint_recycler.reverse.origin." + p.weaponOrigin().name().toLowerCase(java.util.Locale.ROOT)));
            p.presentation().recoveryCost().ifPresent(c -> {
                lines.add(c.status() != com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService.Status.READY
                        ? tr("recovery_pending", c.inputCount(), c.points()) : tr("recovery", c.inputCount(), c.points(), p.recoveryPointValue()));
                p.recoveryStatus().ifPresent(s -> lines.add(Component.translatable("gui.taczweaponblueprints.blueprint_recycler.recovery.status." + s.name().toLowerCase(java.util.Locale.ROOT))));
                if (c.status() != com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService.Status.READY)
                    lines.add(Component.translatable("gui.taczweaponblueprints.blueprint_recycler.reverse.status." + c.status().name().toLowerCase(java.util.Locale.ROOT)));
                for (var i : c.ingredients()) lines.add(materialLine(i));
            });
        }
        return List.copyOf(lines);
    }

    private Component materialLine(BlueprintRecyclerPreview.IngredientPreview i) {
        return Component.translatable("gui.taczweaponblueprints.blueprint_recycler.reverse.material", ingredientLabel(i), i.inventoryAvailable(), i.required());
    }

    private Component actionTooltip(BlueprintRecyclerActionContract.Action action) {
        MutableComponent text = Component.translatable(actionMessageKey(action)).append("\n")
                .append(Component.translatable(actionTooltipKey(action)));
        var p = menu.preview();
        if (action == BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER) {
            text.append("\n").append(tr("consumption", p.requiredInputCount(), p.pointCost()));
            if (!p.ingredients().isEmpty()) text.append("\n").append(tr("materials_help"));
            if (p.presentation().weapon().isPresent()) text.append("\n").append(tr("attempt_warning"));
        } else if (action == BlueprintRecyclerActionContract.Action.RECOVER_POINTS) p.presentation().recoveryCost().ifPresent(c -> {
            text.append("\n").append(tr("recovery", c.inputCount(), c.points(), p.recoveryPointValue()));
            if (!c.ingredients().isEmpty()) text.append("\n").append(tr("materials_help"));
        });
        return text;
    }

    private void renderAnalyzerDetails(GuiGraphics g, BlueprintRecyclerPreview p, BlueprintRecyclerScreenModel model) {
        var lines = analyzerLines(p, model);
        detailScroll = Math.max(0, Math.min(detailScroll, lines.size() - Math.min(DETAIL_LINES, lines.size())));
        for (int row = 0; row < DETAIL_LINES && row + detailScroll < lines.size(); row++) {
            int index = row + detailScroll;
            int decisionColor = requests.pending() ? ACCENT : feedback.visibleCode().isPresent()
                    ? feedback.visibleCode().orElseThrow() != BlueprintRecyclerActionContract.ResultCode.SUCCESS ? BAD
                        : feedback.visibleOutcome().filter(o -> o.kind() == com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable.Kind.NO_RESULT).isPresent() ? ACCENT : GOOD
                    : model.controlsEnabled() ? GOOD : model.statusEmphasis() == BlueprintRecyclerScreenModel.StatusEmphasis.NOTICE ? ACCENT : MUTED;
            int color = index == 0 ? TEXT : index == 2 ? decisionColor : index >= 3 && index <= 5 ? ACCENT : MUTED;
            g.drawString(font, clipped(lines.get(index), BlueprintRecyclerMenu.Layout.DETAIL_WIDTH - 6), BlueprintRecyclerMenu.Layout.DETAIL_X, 29 + row * 11, color, false);
        }
        if (lines.size() > DETAIL_LINES) {
            int x = BlueprintRecyclerMenu.Layout.DETAIL_X + BlueprintRecyclerMenu.Layout.DETAIL_WIDTH - 2;
            g.fill(x, 29, x + 2, 94, BORDER);
            int thumb = Math.max(6, 65 * DETAIL_LINES / lines.size());
            int offset = (65 - thumb) * detailScroll / (lines.size() - DETAIL_LINES);
            g.fill(x, 29 + offset, x + 2, 29 + offset + thumb, ACCENT);
        }
        var side = sidebarLines(p);
        for (int i = 0; i < Math.min(4, side.size()); i++) g.drawString(font, clipped(side.get(i), 92), 14, 80 + i * 10, i == 1 ? ACCENT : MUTED, false);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (detailed(menu.preview()) && mouseX >= leftPos + BlueprintRecyclerMenu.Layout.DETAIL_X
                && mouseX < leftPos + imageWidth - 8 && mouseY >= topPos + 22 && mouseY < topPos + BlueprintRecyclerMenu.Layout.ACTION_Y) {
            scrollDetails(amount > 0 ? -1 : 1); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (detailed(menu.preview()) && (key == 266 || key == 267)) { scrollDetails(key == 266 ? -DETAIL_LINES : DETAIL_LINES); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    private void scrollDetails(int amount) {
        int size = analyzerLines(menu.preview(), BlueprintRecyclerScreenModel.from(menu.preview(), requests.pending())).size();
        detailScroll = Math.max(0, Math.min(Math.max(0, size - DETAIL_LINES), detailScroll + amount));
        triggerImmediateNarration(true);
    }

    @Override
    protected void updateNarrationState(NarrationElementOutput output) {
        super.updateNarrationState(output);
        output.add(NarratedElementType.HINT, detailsNarration());
    }
}
