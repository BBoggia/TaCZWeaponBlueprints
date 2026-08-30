package com.gamergaming.taczweaponblueprints.client;

import java.util.Optional;

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
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        BlueprintRecyclerPreview currentPreview = menu.preview();
        if (!currentPreview.equals(observedPreview)) {
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
        updateControls();
    }

    @Override
    public void onClose() {
        requests.clear();
        feedback.clear();
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
        updateControls();
        triggerImmediateNarration(true);
    }

    private void request(BlueprintRecyclerActionContract.Action action) {
        BlueprintRecyclerPreview preview = menu.preview();
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
                        BlueprintRecyclerScreenModel.actionKey(action)))
                .orElse(Component.empty()));
        primaryButton.setTooltip(primaryAction
                .map(action -> Tooltip.create(Component.translatable(
                        BlueprintRecyclerScreenModel.actionKey(action) + ".tooltip")))
                .orElse(null));

        secondaryButton.visible = secondaryAction.isPresent();
        secondaryButton.active = secondaryButton.visible && model.controlsEnabled();
        secondaryButton.setMessage(secondaryAction
                .map(action -> Component.translatable(
                        BlueprintRecyclerScreenModel.actionKey(action)))
                .orElse(Component.empty()));
        secondaryButton.setTooltip(secondaryAction
                .map(action -> Tooltip.create(Component.translatable(
                        BlueprintRecyclerScreenModel.actionKey(action) + ".tooltip")))
                .orElse(null));
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
                (BlueprintRecyclerMenu.Layout.INPUT_X
                        + BlueprintRecyclerMenu.Layout.OUTPUT_X) / 2 + 8,
                BlueprintRecyclerMenu.Layout.INPUT_Y + 4,
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
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
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
        graphics.drawString(
                font,
                clipped(
                        Component.translatable(model.headingKey()),
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
        int statusColor = requests.pending()
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
        return visibleFeedback
                .map(result -> Component.translatable(
                        BlueprintRecyclerScreenModel.resultKey(result)))
                .orElseGet(() -> Component.translatable(model.statusKey()));
    }

    private Component summary(BlueprintRecyclerPreview preview) {
        if (preview.inputKind() == BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM) {
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
        }
        return details;
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
                BlueprintRecyclerScreenModel.actionKey(model.primaryAction().orElseThrow()));
        return model.secondaryAction()
                .<Component>map(action -> Component.translatable(
                        "gui.taczweaponblueprints.blueprint_recycler.narration.actions",
                        primary,
                        Component.translatable(BlueprintRecyclerScreenModel.actionKey(action))))
                .orElse(primary);
    }

    private void renderDetailsTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {
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

    @Override
    protected void updateNarrationState(NarrationElementOutput output) {
        super.updateNarrationState(output);
        output.add(NarratedElementType.HINT, detailsNarration());
    }
}
