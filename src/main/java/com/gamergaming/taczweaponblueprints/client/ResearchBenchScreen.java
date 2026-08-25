package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalEntry;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchPreview;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.network.ResearchBenchActionPacket;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Compact Research Bench UI backed entirely by the open server menu. */
public final class ResearchBenchScreen extends AbstractContainerScreen<ResearchBenchMenu> {
    private static final int PAGE_SIZE = 5;

    private final List<Button> selectionButtons = new ArrayList<>();
    private List<BlueprintJournalEntry> entries = List.of();
    private Object journalIdentity;
    private int page;
    private Button researchButton;
    private Button recycleButton;
    private Button previousButton;
    private Button nextButton;

    public ResearchBenchScreen(ResearchBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 248;
        imageHeight = 222;
        inventoryLabelX = 44;
        inventoryLabelY = 128;
    }

    @Override
    protected void init() {
        super.init();
        reloadEntries();
        for (int row = 0; row < PAGE_SIZE; row++) {
            int rowIndex = row;
            Button button = addRenderableWidget(Button.builder(Component.empty(), ignored -> selectRow(rowIndex))
                    .bounds(leftPos + 8, topPos + 24 + row * 18, 90, 17)
                    .build());
            selectionButtons.add(button);
        }
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> changePage(-1))
                .bounds(leftPos + 8, topPos + 115, 42, 18)
                .build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> changePage(1))
                .bounds(leftPos + 56, topPos + 115, 42, 18)
                .build());
        researchButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.taczweaponblueprints.research_bench.research"),
                ignored -> requestResearch())
                .bounds(leftPos + 130, topPos + 24, 108, 18)
                .build());
        recycleButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.taczweaponblueprints.research_bench.recycle"),
                ignored -> requestRecycle())
                .bounds(leftPos + 130, topPos + 53, 108, 18)
                .build());
        updateButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        Object latest = ClientBlueprintJournal.snapshot();
        if (latest != journalIdentity) {
            reloadEntries();
        }
        updateButtons();
    }

    private void reloadEntries() {
        journalIdentity = ClientBlueprintJournal.snapshot();
        entries = ClientBlueprintJournal.snapshot().entries().stream()
                .filter(entry -> entry.blueprintId().isPresent())
                .filter(entry -> !entry.learned())
                .filter(entry -> entry.visibility().ordinal() >= JournalVisibility.PREVIEW.ordinal())
                .sorted(Comparator.comparingInt(BlueprintJournalEntry::ordinal))
                .toList();
        page = Math.min(page, pageCount() - 1);
        updateButtons();
    }

    private void updateButtons() {
        for (int row = 0; row < selectionButtons.size(); row++) {
            int index = page * PAGE_SIZE + row;
            Button button = selectionButtons.get(row);
            button.visible = index < entries.size();
            button.active = button.visible;
            if (button.visible) {
                button.setMessage(entryName(entries.get(index)));
            }
        }
        if (previousButton != null) {
            previousButton.active = page > 0;
            nextButton.active = page + 1 < pageCount();
            researchButton.active = menu.preview().researchable()
                    && menu.selectedBlueprint().isPresent();
            recycleButton.active = BlueprintItem.getBlueprintId(
                    menu.getSlot(ResearchBenchMenu.RECYCLING_SLOT).getItem()).isPresent();
        }
    }

    private void selectRow(int row) {
        int index = page * PAGE_SIZE + row;
        if (index >= entries.size()) {
            return;
        }
        ResourceLocation id = entries.get(index).blueprintId().orElseThrow();
        send(ResearchBenchMenu.Action.SELECT, Optional.of(id));
    }

    private void requestResearch() {
        send(ResearchBenchMenu.Action.RESEARCH, menu.selectedBlueprint());
    }

    private void requestRecycle() {
        send(
                ResearchBenchMenu.Action.RECYCLE,
                BlueprintItem.getBlueprintId(menu.getSlot(ResearchBenchMenu.RECYCLING_SLOT).getItem()));
    }

    private void send(ResearchBenchMenu.Action action, Optional<ResourceLocation> id) {
        NetworkHandler.INSTANCE.sendToServer(new ResearchBenchActionPacket(menu.containerId, action, id));
    }

    private void changePage(int direction) {
        page = Math.max(0, Math.min(page + direction, pageCount() - 1));
        updateButtons();
    }

    private int pageCount() {
        return Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private Component entryName(BlueprintJournalEntry entry) {
        return entry.nameKey().map(Component::translatable)
                .orElseGet(() -> Component.literal(entry.blueprintId().orElseThrow().toString()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0141920);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF68798C);
        graphics.fill(leftPos + 6, topPos + 21, leftPos + 100, topPos + 110, 0x80202730);
        graphics.fill(leftPos + 102, topPos + 21, leftPos + 242, topPos + 124, 0x80202730);
        drawSlot(graphics, 108, 25);
        drawSlot(graphics, 108, 54);
        for (int index = 0; index < 6; index++) {
            drawSlot(graphics, 108 + (index % 3) * 20, 84 + (index / 3) * 20);
        }
    }

    private void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(leftPos + x - 1, topPos + y - 1, leftPos + x + 17, topPos + y + 17, 0xFF0B0F14);
        graphics.renderOutline(leftPos + x - 1, topPos + y - 1, 18, 18, 0xFF59697A);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFFFFF, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFB8C2CC, false);
        ResearchBenchPreview preview = menu.preview();
        Component pageLabel = Component.literal((page + 1) + "/" + pageCount());
        graphics.drawCenteredString(font, pageLabel, 53, 119, 0xFF9FAAB5);
        graphics.drawString(font,
                Component.translatable("gui.taczweaponblueprints.research_bench.output"),
                102, 14, 0xFFE4C56A, false);
        graphics.drawString(font,
                Component.translatable("gui.taczweaponblueprints.research_bench.recycling_input"),
                102, 43, 0xFFE4C56A, false);
        if (preview.blueprintId().isPresent()) {
            int pointColor = preview.creativeBypass() || preview.pointBalance() >= preview.pointCost()
                    ? 0xFF70C98B : 0xFFFF7777;
            Component points = preview.creativeBypass()
                    ? Component.translatable("gui.taczweaponblueprints.research_bench.cost_bypassed")
                    : Component.translatable(
                            "gui.taczweaponblueprints.research_bench.points",
                            preview.pointCost(), preview.pointBalance());
            graphics.drawString(font, points, 102, 74, pointColor, false);
            renderIngredientDetails(graphics, preview);
            Component readiness = readiness(preview);
            graphics.drawString(font, readiness, 170, 114,
                    preview.researchable() ? 0xFF70C98B : 0xFFFFA45C, false);
        } else {
            graphics.drawWordWrap(font,
                    Component.translatable("gui.taczweaponblueprints.research_bench.select_hint"),
                    102, 74, 136, 0xFF9FAAB5);
        }
    }

    private void renderIngredientDetails(GuiGraphics graphics, ResearchBenchPreview preview) {
        int y = 84;
        for (ResearchBenchPreview.IngredientPreview ingredient : preview.ingredients()) {
            Component name = ingredientName(ingredient);
            Component line = name.copy().append(Component.literal(
                    " " + Math.min(ingredient.available(), ingredient.required()) + "/" + ingredient.required()));
            int color = ingredient.available() >= ingredient.required() ? 0xFF70C98B : 0xFFFF7777;
            graphics.drawString(font, line, 170, y, color, false);
            y += 10;
        }
    }

    private Component ingredientName(ResearchBenchPreview.IngredientPreview ingredient) {
        if (!ingredient.items().isEmpty()) {
            Item item = ForgeRegistries.ITEMS.getValue(ingredient.items().get(0));
            if (item != null) {
                return new ItemStack(item).getHoverName();
            }
        }
        return ingredient.tag()
                .<Component>map(id -> Component.literal("#" + id))
                .orElseGet(() -> Component.literal("?"));
    }

    private Component readiness(ResearchBenchPreview preview) {
        String suffix;
        if (!preview.policyEligible()) {
            suffix = "locked";
        } else if (!preview.creativeBypass() && preview.pointBalance() < preview.pointCost()) {
            suffix = "points";
        } else if (!preview.ingredientsSatisfied()) {
            suffix = "ingredients";
        } else if (!preview.outputSpace()) {
            suffix = "inventory";
        } else {
            suffix = "ready";
        }
        return Component.translatable("gui.taczweaponblueprints.research_bench.readiness." + suffix);
    }
}
