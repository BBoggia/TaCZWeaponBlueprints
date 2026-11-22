package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.client.ui.UiButton;
import com.gamergaming.taczweaponblueprints.client.ui.UiDraw;
import com.gamergaming.taczweaponblueprints.client.ui.UiSheet;
import com.gamergaming.taczweaponblueprints.client.ui.UiTheme;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchResearchAction;
import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import com.gamergaming.taczweaponblueprints.network.ResearchBenchActionPacket;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.util.ItemNameFilterHelper;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Tech Tree drawn as a drafting sheet: family lanes across, tiers down, a gate bar above each tier.
 * The server still owns selection, pricing and research; this screen only presents them.
 */
public final class TechTreeSheetScreen extends AbstractContainerScreen<ResearchBenchMenu>
        implements ResearchBenchActionResultListener, ResearchBenchPreviewListener, ResearchAffordabilityListener {
    private static final String BENCH = "gui.taczweaponblueprints.research_bench.";
    private static final String TREE = BENCH + "tree.";
    private static final String SHEET = "gui.taczweaponblueprints.tech_tree.";
    private static final int BAR_HEIGHT = 24;
    private static final int INSPECTOR_WIDTH = 150;
    private static final int DRAG_THRESHOLD = 3;
    private static final int FEEDBACK_TICKS = 80;
    /** The strip under the sheet that says what to do next. */
    private static final int GUIDE_HEIGHT = 20;

    private final Inventory inventory;
    private final ResearchTreeNodeArt art = new ResearchTreeNodeArt();
    private final Map<ResourceLocation, String> names = new HashMap<>();
    private final List<UiButton> domainButtons = new ArrayList<>();
    private TechTreeSheetLayout layout = TechTreeSheetLayout.EMPTY;
    private ResearchTreeGraph graph = ResearchTreeGraph.EMPTY;
    private List<ResearchTechTreePresentation.DomainView> domains = List.of();
    private final Map<ResourceLocation, Domain> domainIndex = new HashMap<>();
    private List<Object> inspectorContentKey = List.of();
    private long builtGeneration = Long.MIN_VALUE;
    private Domain domain = Domain.WEAPONS;
    private final ResearchSheetSelection selection = new ResearchSheetSelection();
    private UiButton researchButton;
    private UiButton nextButton;
    private List<TechTreeGuidance.Entry> guideEntries = List.of();
    private TechTreeGuidance.Advice advice = TechTreeGuidance.advise(List.of());
    private boolean legendOpen;
    /** False on a narrow window, where the balance moves down to the guide strip. */
    private boolean balanceInBar = true;
    private int legendX, legendY, legendWidth, legendHeight;
    private boolean showTitle = true, showTag = true;
    private boolean rowsAreBenchTiers;
    /** The lane grouping is a personal setting, so it survives closing the bench and restarting the game. */
    private TechTreeSheetLayout.Arrangement arrangement = TechTreeSheetLayout.Arrangement.BRANCHES;
    private final ResearchPurchaseConfirmation confirmation = new ResearchPurchaseConfirmation();
    private ResearchTreeInspectorBody inspectorBody;
    private int inspectorScroll;
    private PendingMaterialPage materialPage;
    private int materialPageTicks;
    private record PendingMaterialPage(ResourceLocation target, int offset, long generation) { }
    private record InventoryEntry(net.minecraft.world.item.Item item, int count) { }
    private List<InventoryEntry> affordabilityInventory = List.of();
    private int affordabilityBalance = -1, affordabilityDelay;
    private long affordabilityGeneration = Long.MIN_VALUE;
    private Object affordabilityCurrency;
    private boolean affordabilityInitialized, affordabilityCreative, affordabilityMode;

    private int sheetX, sheetY, sheetWidth, sheetHeight;
    private int scrollX, scrollY, barRight;
    private net.minecraft.client.gui.components.EditBox searchBox;
    private boolean searchOpen;
    private String searchText = "";
    private List<ResourceLocation> matches = List.of();
    /** Ticks until a keyboard selection is sent, so holding an arrow key does not flood the server. */
    private int selectDelay;
    private boolean pressedOnSheet, dragging, centerOnSelection = true;
    private double pressX, pressY;

    private Component feedback = Component.empty();
    private int feedbackColor = UiTheme.TEXT_MUTED;
    private int feedbackTicks;

    public TechTreeSheetScreen(ResearchBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.inventory = inventory;
    }

    @Override
    protected void init() {
        imageWidth = width;
        imageHeight = height;
        super.init();
        boolean inspector = width >= 300;
        sheetX = 0;
        sheetY = BAR_HEIGHT;
        sheetWidth = width - (inspector ? INSPECTOR_WIDTH : 0);
        sheetHeight = height - BAR_HEIGHT - GUIDE_HEIGHT;
        cancelHold(false);
        selection.restore(menu.selectedBlueprint());
        arrangement = com.gamergaming.taczweaponblueprints.init.ModConfigs.RESEARCH_TREE_CLIENT.techTreeLanes.get() == TechTreeLaneMode.TYPES
                ? TechTreeSheetLayout.Arrangement.FAMILIES : TechTreeSheetLayout.Arrangement.BRANCHES;
        // The key to the sheet opens by itself once, then waits behind the ? button.
        legendOpen |= ResearchTreeGuidancePreference.client().claimTechTreeLegend();
        rebuild(true);
        buildWidgets();
    }

    private void buildWidgets() {
        // Rebuilding must not interrupt typing: without focus, the next letter would act as a hotkey.
        boolean inspectorFocused = inspectorBody != null && inspectorBody.isFocused();
        boolean typing = searchBox != null && searchBox.isFocused();
        int cursor = typing ? searchBox.getCursorPosition() : 0;
        clearWidgets();
        domainButtons.clear();
        int close = width - 20;
        UiButton closeButton = addRenderableWidget(UiButton.secondary(close, 4, 16, 16, Component.literal("×"),
                ignored -> onClose()));
        closeButton.setTooltip(Tooltip.create(Component.translatable(BENCH + "close")));
        UiButton legendButton = addRenderableWidget(UiButton.secondary(
                close - 54, 4, 16, 16, Component.literal("?"), ignored -> {
                    legendOpen = !legendOpen;
                    buildWidgets();
                }));
        legendButton.setSelected(legendOpen);
        legendButton.setTooltip(Tooltip.create(Component.translatable(SHEET + "legend.tooltip")));
        UiButton overviewButton = addRenderableWidget(UiButton.secondary(close - 36,
                4, 16, 16, Component.literal("□"), ignored -> setOverview(!overview)));
        overviewButton.setSelected(overview);
        overviewButton.setTooltip(Tooltip.create(Component.translatable(SHEET + "overview.tooltip")));
        UiButton searchButton = addRenderableWidget(UiButton.secondary(close - 18, 4, 16, 16,
                Component.literal("⌕"), ignored -> toggleSearch()));
        searchButton.setSelected(searchOpen);
        searchButton.setTooltip(Tooltip.create(Component.translatable(
                TREE + (searchOpen ? "search.close" : "search.toggle"))));
        searchBox = null;
        if (searchOpen) {
            searchBox = addRenderableWidget(new net.minecraft.client.gui.components.EditBox(font,
                    sheetX + 13, sheetY + TechTreeSheetLayout.HEADER_HEIGHT + 10, 104, 10,
                    Component.translatable(TREE + "search.toggle")));
            searchBox.setBordered(false);
            searchBox.setTextColor(UiTheme.TEXT);
            searchBox.setMaxLength(48);
            searchBox.setHint(Component.translatable("gui.taczweaponblueprints.journal.search"));
            searchBox.setValue(searchText);
            searchBox.setResponder(value -> {
                searchText = value;
                refreshMatches();
            });
        }
        refreshMatches();

        Component grouping = Component.translatable(SHEET + (arrangement == TechTreeSheetLayout.Arrangement.BRANCHES
                ? "lanes.branches" : "lanes.families"));
        int domainWidth = domains.stream()
                .mapToInt(view -> font.width(title(view.translationKey(), view.title())) + 14).sum();
        var toolbar = TechTreeToolbar.fit(close - 56, font.width(grouping) + 12, domainWidth, domains.size());
        int groupingWidth = toolbar.groupingWidth();
        barRight = close - 56 - groupingWidth;
        UiButton groupingButton = addRenderableWidget(UiButton.secondary(barRight, 4, groupingWidth, 16,
                toolbar.compactGrouping() ? Component.literal("≡") : grouping,
                ignored -> {
                    arrangement = arrangement == TechTreeSheetLayout.Arrangement.BRANCHES
                            ? TechTreeSheetLayout.Arrangement.FAMILIES : TechTreeSheetLayout.Arrangement.BRANCHES;
                    com.gamergaming.taczweaponblueprints.init.ModConfigs.RESEARCH_TREE_CLIENT.chooseTechTreeLanes(arrangement == TechTreeSheetLayout.Arrangement.FAMILIES
                            ? TechTreeLaneMode.TYPES : TechTreeLaneMode.BRANCHES);
                    scrollX = 0;
                    scrollY = 0;
                    rebuild(true);
                    buildWidgets();
                }));
        groupingButton.setTooltip(Tooltip.create(grouping.copy().append("\n").append(Component.translatable(SHEET + "lanes.tooltip"))));
        // On a narrow window the bench name gives way first, then its tier tag.
        int room = toolbar.compactDomains() ? 0 : barRight - 16 - domainWidth;
        showTag = !menu.portable() && room >= font.width(tierLabel()) + 14;
        showTitle = room >= font.width(title) + 8 + (showTag ? font.width(tierLabel()) + 14 : 0);
        int x = 8 + (showTitle ? font.width(title) + 8 : 0) + (showTag ? font.width(tierLabel()) + 14 : 0);
        for (var view : domains) {
            Component fullLabel = title(view.translationKey(), view.title());
            Component label = toolbar.compactDomains()
                    ? Component.translatable(TREE + "domain." + view.domain().name().toLowerCase(Locale.ROOT) + ".short")
                    : fullLabel;
            int buttonWidth = toolbar.compactDomains()
                    ? Math.min(font.width(label) + 12, toolbar.maxDomainWidth()) : font.width(label) + 12;
            UiButton button = UiButton.secondary(x, 4, buttonWidth, 16, label, ignored -> showDomain(view.domain()));
            if (toolbar.compactDomains()) button.setTooltip(Tooltip.create(fullLabel));
            button.setSelected(view.domain() == domain);
            domainButtons.add(addRenderableWidget(button));
            x += buttonWidth + 2;
        }
        researchButton = addRenderableWidget(UiButton.primary(width - INSPECTOR_WIDTH + 10, height - 30,
                INSPECTOR_WIDTH - 20, 20, Component.empty(), ignored -> {
                    // With hold-to-confirm on, only a completed hold researches; a plain press does nothing.
                    if (selection.failed()) sendSelection();
                    else if (!holdRequired()) requestResearch();
                }));
        if (holdRequired()) {
            researchButton.setTooltip(Tooltip.create(Component.translatable(SHEET + "hold.tooltip")));
        }
        if (inspectorBody == null) inspectorBody = new ResearchTreeInspectorBody(font, this::followRequirement,
                value -> inspectorScroll = value, this::requestMaterialPage);
        inspectorBody.bounds(new ResearchTreeScreenLayout.Rect(Math.max(0, width - INSPECTOR_WIDTH + 9), BAR_HEIGHT + 10,
                INSPECTOR_WIDTH - 18, Math.max(1, researchButton.getY() - BAR_HEIGHT - 16)));
        addRenderableWidget(inspectorBody);
        refreshInspector();
        updateResearchButton();
        if (inspectorFocused && inspectorBody.visible) setFocused(inspectorBody);
        Component next = Component.translatable(SHEET + "guide.next");
        int nextWidth = font.width(next) + 12;
        nextButton = addRenderableWidget(UiButton.secondary(sheetX + sheetWidth - nextWidth - 4,
                height - GUIDE_HEIGHT + 2, nextWidth, 16, next, ignored -> jumpToNextReady()));
        nextButton.setTooltip(Tooltip.create(Component.translatable(SHEET + "guide.next.tooltip")));
        refreshGuidance();
        if (typing && searchBox != null) {
            setFocused(searchBox);
            searchBox.setCursorPosition(Math.min(cursor, searchBox.getValue().length()));
            searchBox.setHighlightPos(searchBox.getCursorPosition());
        }
    }

    // ---- hold to confirm ----

    private boolean holdRequired() {
        return com.gamergaming.taczweaponblueprints.init.ModConfigs.RESEARCH_TREE_CLIENT.holdToResearchEnabled();
    }

    private void beginHold(boolean byKey) {
        if (researchButton.active && !selection.failed()) {
            confirmation.begin(currentPreview(), byKey, net.minecraft.Util.getMillis(), Math.max(1,
                    com.gamergaming.taczweaponblueprints.init.ModConfigs.RESEARCH_TREE_CLIENT.holdDurationMillis()));
        }
    }

    private void cancelHold(boolean released) {
        boolean early = confirmation.holding(net.minecraft.Util.getMillis());
        if (released) confirmation.release(); else confirmation.cancel();
        if (released && early) showFeedback(Component.translatable(SHEET + "hold.keep_holding"),
                UiTheme.TEXT_MUTED, FEEDBACK_TICKS);
    }

    private ResearchSelectionPreview currentPreview() {
        return selection.selected().flatMap(graph::node).flatMap(this::previewFor).orElse(ResearchSelectionPreview.EMPTY);
    }

    private void updateHold(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!confirmation.pressed()) return;
        if (!minecraft.isWindowActive()) { confirmation.cancel(); return; }
        long window = minecraft.getWindow().getWindow();
        boolean down = confirmation.byKey()
                ? com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, 257)
                    || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, 335)
                : org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, 0) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (!down) { confirmation.release(); return; }
        if (!researchButton.active || !confirmation.byKey() && !researchButton.isMouseOver(mouseX, mouseY)) {
            confirmation.cancel(); return;
        }
        long now = net.minecraft.Util.getMillis();
        confirmation.advance(currentPreview(), now).ifPresent(this::requestResearch);
        float progress = confirmation.progress(now);
        if (progress < 0) return;
        int left = researchButton.getX() + 2, bottom = researchButton.getY() + researchButton.getHeight() - 3;
        int filled = Math.round((researchButton.getWidth() - 4) * progress);
        graphics.fill(left, bottom - 2, left + filled, bottom, UiTheme.AMBER_TEXT);
    }

    // ---- guidance ----

    /** Re-reads what the player can do, in reading order, and picks the one line worth showing. */
    private void refreshGuidance() {
        List<TechTreeSheetLayout.Tile> ordered = new ArrayList<>(layout.tiles());
        ordered.sort(java.util.Comparator.comparingInt(TechTreeSheetLayout.Tile::y)
                .thenComparingInt(TechTreeSheetLayout.Tile::x));
        List<TechTreeGuidance.Entry> entries = new ArrayList<>();
        for (var tile : ordered) {
            graph.node(tile.nodeId()).ifPresent(node -> {
                var ready = readiness(node);
                int tier = previewFor(node).flatMap(p -> p.accessSummary().requiredTier())
                        .map(com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier::level)
                        .orElse(node.benchTier());
                entries.add(new TechTreeGuidance.Entry(node.blueprintId(), ready.tile(),
                        ready.message() == ResearchTreeSelectedNodePresenter.Message.WORKBENCH_TIER_REQUIRED,
                        ready.quotedCost(), tier, ready.message()));
            });
        }
        guideEntries = entries;
        advice = TechTreeGuidance.advise(entries);
        if (nextButton != null) {
            nextButton.visible = advice.target().isPresent();
        }
    }

    private void jumpToNextReady() {
        TechTreeGuidance.next(guideEntries, selection.selected()).ifPresent(id -> {
            setFocused(null);
            select(id);
            setOverview(false);
            layout.tile(id).ifPresent(this::reveal);
        });
    }

    private Component adviceText() {
        String targetName = advice.target().flatMap(graph::node).map(this::name).orElse("");
        return switch (advice.kind()) {
            case START -> advice.cost() < 0 ? Component.translatable(SHEET + "guide.start_check", targetName)
                    : advice.cost() > 0
                    ? ClientResearchCurrency.text(SHEET + "guide.start", targetName, advice.cost())
                    : Component.translatable(SHEET + "guide.start_free", targetName);
            case READY -> advice.readyCount() == 1
                    ? Component.translatable(SHEET + "guide.ready_one", targetName)
                    : Component.translatable(SHEET + "guide.ready", advice.readyCount());
            case SHORT -> advice.cost() < 0 ? Component.translatable(SHEET + "guide.short_check", targetName)
                    : ClientResearchCurrency.text(SHEET + "guide.short", targetName, advice.cost(),
                    currencyBalance());
            case BENCH -> Component.translatable(SHEET + "guide.bench", benchName(advice.benchTier()));
            case WAITING -> Component.translatable(SHEET + "guide.waiting");
            case COMPLETE -> Component.translatable(SHEET + "guide.complete");
            case CHECKING -> Component.translatable(SHEET + "guide.checking", targetName);
            case BLOCKED -> Component.translatable(SHEET + "guide.blocked", targetName);
        };
    }

    private boolean adviceIsActionable() {
        return advice.kind() == TechTreeGuidance.Kind.START || advice.kind() == TechTreeGuidance.Kind.READY;
    }

    private void renderGuide(GuiGraphics graphics) {
        int y = height - GUIDE_HEIGHT;
        graphics.fill(sheetX, y, sheetX + sheetWidth, height, UiTheme.STEEL);
        graphics.fill(sheetX, y, sheetX + sheetWidth, y + 1, UiTheme.STEEL_DARKEST);
        if (layout.tiles().isEmpty()) {
            return;
        }
        boolean actionable = adviceIsActionable();
        graphics.fill(sheetX + 8, y + 8, sheetX + 12, y + 12, actionable ? UiTheme.AMBER : UiTheme.TEXT_MUTED);
        int right = nextButton != null && nextButton.visible ? nextButton.getX() - 6 : sheetX + sheetWidth - 6;
        String text = adviceText().getString();
        Component balance = ClientResearchCurrency.text(TREE + "balance",
                currencyBalance());
        // The advice comes first: the balance only joins it here when the bar had no room and both fit.
        if (!balanceInBar && sheetX + 18 + font.width(text) + 8 + font.width(balance) <= right) {
            right -= font.width(balance);
            graphics.drawString(font, balance, right, y + 6, UiTheme.MANILA, false);
            right -= 8;
        }
        graphics.drawString(font, clip(text, right - (sheetX + 18)), sheetX + 18, y + 6,
                actionable ? UiTheme.TEXT : UiTheme.TEXT_MUTED, false);
    }

    // ---- legend ----

    private static final String[] LEGEND_ROWS = {
        "ready", "open", "learned", "locked", "required", "either", "cross", "gate"
    };

    private void renderLegend(GuiGraphics graphics) {
        int rowHeight = 13;
        int textWidth = font.width(Component.translatable(SHEET + "legend.title"));
        for (String row : LEGEND_ROWS) {
            textWidth = Math.max(textWidth, 24 + font.width(Component.translatable(SHEET + "legend." + row)));
        }
        legendWidth = Math.min(sheetWidth - 8, Math.max(150, textWidth + 16));
        int inner = legendWidth - 16;
        List<FormattedCharSequence> keys = font.split(Component.translatable(SHEET + "legend.keys"), inner);
        List<FormattedCharSequence> close = font.split(Component.translatable(SHEET + "legend.close"), inner);
        legendHeight = 8 + font.lineHeight + 5 + LEGEND_ROWS.length * rowHeight + 6
                + (keys.size() + close.size()) * (font.lineHeight + 1) + 9;
        legendX = sheetX + sheetWidth - legendWidth - 4;
        legendY = sheetY + TechTreeSheetLayout.HEADER_HEIGHT + 4;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        UiDraw.raisedPanel(graphics, legendX, legendY, legendWidth, legendHeight);
        int left = legendX + 8;
        int line = legendY + 8;
        graphics.drawString(font, Component.translatable(SHEET + "legend.title"), left, line, UiTheme.MANILA, false);
        line += font.lineHeight + 5;
        for (String row : LEGEND_ROWS) {
            // Each sample sits on a scrap of sheet so it looks the way it does on the tree.
            graphics.fill(left, line - 1, left + 20, line + 11, UiTheme.SHEET);
            int midY = line + 5;
            switch (row) {
                case "ready" -> UiSheet.tile(graphics, left + 3, line + 1, 14, 8, UiSheet.TileState.READY, false, false);
                case "open" -> UiSheet.tile(graphics, left + 3, line + 1, 14, 8, UiSheet.TileState.OPEN, false, false);
                case "learned" -> UiSheet.tile(graphics, left + 3, line, 14, 10, UiSheet.TileState.LEARNED, false,
                        false);
                case "locked" -> UiSheet.tile(graphics, left + 3, line + 1, 14, 8, UiSheet.TileState.LOCKED, false,
                        false);
                case "required" -> UiSheet.wire(graphics, left + 2, midY, left + 17, midY, UiTheme.INK);
                case "either" -> UiSheet.dashedWire(graphics, left + 2, midY, left + 17, midY, UiTheme.INK);
                case "cross" -> {
                    UiSheet.wire(graphics, left + 2, midY, left + 14, midY, UiTheme.WIRE_CROSS | 0xFF000000);
                    UiSheet.crossPoint(graphics, left + 15, midY, UiTheme.WIRE_CROSS | 0xFF000000);
                }
                default -> UiSheet.padlock(graphics, left + 6, line + 1);
            }
            graphics.drawString(font, clip(Component.translatable(SHEET + "legend." + row).getString(), inner - 24),
                    left + 24, line + 1, UiTheme.TEXT, false);
            line += rowHeight;
        }
        line += 1;
        UiDraw.rule(graphics, left, line, inner);
        line += 5;
        for (var text : keys) {
            graphics.drawString(font, text, left, line, UiTheme.TEXT_MUTED, false);
            line += font.lineHeight + 1;
        }
        for (var text : close) {
            graphics.drawString(font, text, left, line, UiTheme.TEXT_DISABLED, false);
            line += font.lineHeight + 1;
        }
        graphics.pose().popPose();
    }

    private boolean overLegend(double x, double y) {
        return legendOpen && x >= legendX && x < legendX + legendWidth && y >= legendY && y < legendY + legendHeight;
    }

    private Component tierLabel() {
        return Component.translatable(BENCH + "workbench_tier", menu.workbenchTier().level());
    }

    /** Reads the current published tree. The view only moves when the domain or the selection asks it to. */
    private void rebuild(boolean recenter) {
        var publication = ClientResearchState.publication();
        builtGeneration = publication.generation();
        graph = publication.graph();
        domains = publication.techTree().domains();
        domainIndex.clear();
        domains.forEach(view -> view.lanes().forEach(lane -> lane.members().forEach(member ->
                domainIndex.put(member.nodeId(), view.domain()))));
        names.clear();
        if (recenter && selection.selected().isPresent()) {
            for (var view : domains) {
                if (view.lanes().stream().flatMap(lane -> lane.members().stream())
                        .anyMatch(member -> member.nodeId().equals(selection.selected().orElseThrow()))) {
                    domain = view.domain();
                }
            }
        }
        if (!domains.isEmpty() && domains.stream().noneMatch(view -> view.domain() == domain)) {
            domain = domains.get(0).domain();
        }
        var view = domains.stream().filter(candidate -> candidate.domain() == domain).findFirst();
        layout = view.map(value -> TechTreeSheetLayout.build(value, publication.techTree().bands(), graph,
                arrangement)).orElse(TechTreeSheetLayout.EMPTY);
        if (selection.selected().filter(id -> layout.tile(id).isEmpty()).isPresent()
                && !domains.isEmpty()) {
            clearDomainSelection();
        }
        rowsAreBenchTiers = layout.tiers().stream().anyMatch(tier -> tier.benchTier() != TechTreeSheetLayout.BAND_ROW);
        // Only this domain's blueprints: weapon art lookups would fail for ammo and attachments.
        art.prepare(domain, graph.inducedSubgraph(layout.tiles().stream()
                .map(TechTreeSheetLayout.Tile::nodeId).collect(java.util.stream.Collectors.toSet())));
        centerOnSelection |= recenter;
        clampScroll();
        refreshGuidance();
    }

    private void clearDomainSelection() {
        cancelHold(false);
        selection.clear();
        selectDelay = 0;
        materialPage = null;
        inspectorScroll = 0;
        if (inspectorBody != null) inspectorBody.reset();
        feedback = Component.empty();
        feedbackTicks = 0;
    }

    private void showDomain(Domain next) {
        if (next == domain) {
            return;
        }
        clearDomainSelection();
        domain = next;
        scrollX = 0;
        scrollY = 0;
        rebuild(false);
        buildWidgets();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshAffordability();
        if (materialPage != null && (--materialPageTicks <= 0
                || materialPage.generation() != ClientResearchState.publication().generation())) materialPage = null;
        if (ClientResearchState.publication().generation() != builtGeneration) {
            rebuild(false);
            buildWidgets();
        }
        if (feedbackTicks > 0 && --feedbackTicks == 0) {
            feedback = Component.empty();
        }
        if (selectDelay > 0 && --selectDelay == 0) {
            sendSelection();
        }
        updateResearchButton();
        refreshGuidance();
        refreshInspector();
    }

    @Override
    public void removed() {
        confirmation.release();
        ClientResearchAffordabilityState.setEnabled(false, graph, builtGeneration);
        super.removed();
    }

    private void refreshAffordability() {
        var publication = ClientResearchState.publication();
        boolean mode = menu.routeGuidanceAvailable();
        var latest = inventory.items.stream().map(stack -> new InventoryEntry(stack.getItem(), stack.getCount())).toList();
        int balance = currencyBalance();
        boolean changed = !affordabilityInitialized || !latest.equals(affordabilityInventory)
                || balance != affordabilityBalance || creative() != affordabilityCreative
                || mode != affordabilityMode || publication.generation() != affordabilityGeneration
                || !ClientResearchCurrency.policy().equals(affordabilityCurrency);
        ClientResearchAffordabilityState.setEnabled(mode && publication.generation() != Long.MIN_VALUE,
                publication.graph(), publication.generation());
        if (changed) {
            ClientResearchAffordabilityState.invalidateResources(publication.graph(), publication.generation());
            affordabilityInventory = latest;
            affordabilityBalance = balance;
            affordabilityCreative = creative();
            affordabilityMode = mode;
            affordabilityCurrency = ClientResearchCurrency.policy();
            affordabilityGeneration = publication.generation();
            affordabilityInitialized = true;
            affordabilityDelay = 3;
        }
        var timeout = ClientResearchAffordabilityState.expirePending(net.minecraft.Util.getMillis(), 60_000L);
        if (timeout == ClientResearchAffordabilityState.ResponseOutcome.RETRY) affordabilityDelay = 20;
        if (affordabilityDelay > 0) { affordabilityDelay--; return; }
        if (mode) ClientResearchAffordabilityState.beginNext(publication.graph(), publication.generation())
                .ifPresent(request -> PacketDistributor.sendToServer(
                        new com.gamergaming.taczweaponblueprints.network.ResearchAffordabilityRequestPacket(
                                menu.containerId, request.requestId(), request.publicationGeneration(), request.targetIds())));
    }

    @Override public void refreshAuthoritativeAffordability() { refreshGuidance(); }
    @Override public void scheduleAffordabilityRetry() { affordabilityDelay = 20; refreshGuidance(); }

    @Override
    public void acceptPreview(ResearchSelectionPreview preview) {
        menu.acceptPreview(preview);
        if (preview.blueprintId().isEmpty()) selection.clearedPreview();
        if (materialPage != null && preview.blueprintId().filter(materialPage.target()::equals).isPresent()
                && preview.ingredientOffset() == materialPage.offset()) materialPage = null;
        confirmation.validate(currentPreview());
        updateResearchButton();
        refreshInspector();
        refreshGuidance();
    }

    // ---- search ----

    private void toggleSearch() {
        searchOpen = !searchOpen;
        if (!searchOpen) {
            searchText = "";
        }
        buildWidgets();
        if (searchOpen) {
            setFocused(searchBox);
        }
    }

    /** Blueprints in this tree whose name contains the search text, in reading order. */
    private void refreshMatches() {
        String query = searchText.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            matches = List.of();
            return;
        }
        List<TechTreeSheetLayout.Tile> found = new ArrayList<>();
        for (var tile : layout.tiles()) {
            if (graph.node(tile.nodeId()).filter(node -> node.visibility().revealsName()
                    && name(node).toLowerCase(Locale.ROOT).contains(query)).isPresent()) {
                found.add(tile);
            }
        }
        found.sort(java.util.Comparator.comparingInt(TechTreeSheetLayout.Tile::y)
                .thenComparingInt(TechTreeSheetLayout.Tile::x));
        matches = found.stream().map(TechTreeSheetLayout.Tile::nodeId).toList();
    }

    /** Selects the match after the current selection, wrapping at the end. */
    private void jumpToNextMatch() {
        if (matches.isEmpty()) {
            return;
        }
        int current = selection.selected().map(matches::indexOf).orElse(-1);
        ResourceLocation next = matches.get((current + 1) % matches.size());
        select(next);
        layout.tile(next).ifPresent(this::reveal);
    }

    // ---- view ----

    /** Canvas pixels per screen pixel: 1 normally, smaller in the overview so the whole tree fits. */
    private double scale = 1.0D;
    private boolean overview;
    private static final double MIN_OVERVIEW_SCALE = 0.3D;

    private void updateScale() {
        if (!overview || layout.width() == 0 || layout.height() == 0) {
            scale = 1.0D;
            return;
        }
        scale = Math.max(MIN_OVERVIEW_SCALE, Math.min(1.0D, Math.min(
                sheetWidth / (double) layout.width(), sheetHeight / (double) layout.height())));
    }

    private int viewWidth() {
        return (int) Math.ceil(sheetWidth / scale);
    }

    private int viewHeight() {
        return (int) Math.ceil((sheetHeight - TechTreeSheetLayout.HEADER_HEIGHT * (1.0D - scale)) / scale);
    }

    private int originX() {
        return sheetX - (int) Math.round(scrollX * scale)
                + Math.max(0, (int) ((sheetWidth - layout.width() * scale) / 2));
    }

    private int originY() {
        // The pinned lane names keep their full height, so a scaled-down tree starts just below them.
        return sheetY - (int) Math.round(scrollY * scale)
                + (int) Math.round(TechTreeSheetLayout.HEADER_HEIGHT * (1.0D - scale));
    }

    /** Screen position of a canvas coordinate. */
    private int sx(int canvasX) {
        return originX() + (int) Math.round(canvasX * scale);
    }

    private int sy(int canvasY) {
        return originY() + (int) Math.round(canvasY * scale);
    }

    private int scaled(int canvasLength) {
        return Math.max(1, (int) Math.round(canvasLength * scale));
    }

    private void clampScroll() {
        updateScale();
        scrollX = Math.max(0, Math.min(scrollX, Math.max(0, layout.width() - viewWidth())));
        scrollY = Math.max(0, Math.min(scrollY, Math.max(0, layout.height() - viewHeight())));
    }

    private void reveal(TechTreeSheetLayout.Tile tile) {
        updateScale();
        scrollX = tile.x() + TechTreeSheetLayout.TILE_WIDTH / 2 - viewWidth() / 2;
        scrollY = tile.y() + TechTreeSheetLayout.TILE_HEIGHT / 2 - viewHeight() / 2;
        clampScroll();
    }

    private void setOverview(boolean enabled) {
        if (overview == enabled) {
            return;
        }
        overview = enabled;
        scrollX = 0;
        scrollY = 0;
        clampScroll();
        if (!overview) {
            selection.selected().flatMap(layout::tile).ifPresent(this::reveal);
        }
        buildWidgets();
    }

    private boolean overSheet(double x, double y) {
        return x >= sheetX && x < sheetX + sheetWidth && y >= sheetY && y < sheetY + sheetHeight;
    }

    private Optional<TechTreeSheetLayout.Tile> tileUnder(double x, double y) {
        if (!overSheet(x, y) || y < sheetY + TechTreeSheetLayout.HEADER_HEIGHT) {
            return Optional.empty();
        }
        return layout.tileAt((x - originX()) / scale, (y - originY()) / scale);
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        updateHold(graphics, mouseX, mouseY);
        if (!dragging) {
            tileUnder(mouseX, mouseY).filter(tile -> !overLegend(mouseX, mouseY))
                    .flatMap(tile -> graph.node(tile.nodeId())).ifPresent(node -> {
                Component name = Component.literal(name(node));
                Component detail = hoverReason(node);
                int tipWidth = Math.max(font.width(name), font.width(detail)) + 10;
                UiDraw.tooltip(graphics, font, name, detail,
                        Math.max(2, Math.min(mouseX + 10, width - tipWidth - 2)), Math.min(mouseY + 12, height - 32));
            });
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // The bar and inspector draw their own text.
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (centerOnSelection) {
            centerOnSelection = false;
            selection.selected().flatMap(layout::tile).ifPresent(this::reveal);
        }
        renderSheet(graphics, mouseX, mouseY);
        renderBar(graphics);
        renderGuide(graphics);
        if (legendOpen && !layout.tiles().isEmpty()) {
            renderLegend(graphics);
        }
        if (sheetWidth < width) {
            renderInspector(graphics);
        }
    }

    private void renderSheet(GuiGraphics graphics, int mouseX, int mouseY) {
        UiSheet.paper(graphics, sheetX, sheetY, sheetWidth, sheetHeight, overview ? 0 : scrollX, overview ? 0 : scrollY);
        if (layout.tiles().isEmpty()) {
            Component empty = Component.translatable(TREE + "domain.unavailable");
            graphics.drawString(font, empty, sheetX + (sheetWidth - font.width(empty)) / 2,
                    sheetY + sheetHeight / 2, UiTheme.INK_DIM, false);
            return;
        }
        graphics.enableScissor(sheetX, sheetY, sheetX + sheetWidth, sheetY + sheetHeight);
        // The lane's own wires go down first so links between lanes stay readable on top of them.
        for (var wire : layout.wires()) {
            if (!wire.crossLane()) {
                drawWire(graphics, wire, wireColor(wire));
            }
        }
        for (var wire : layout.wires()) {
            if (wire.crossLane()) {
                drawWire(graphics, wire, wireColor(wire));
            }
        }
        for (var tier : layout.tiers()) {
            int y = sy(tier.gateY());
            if (y + TechTreeSheetLayout.GATE_HEIGHT < sheetY || y > sheetY + sheetHeight) {
                continue;
            }
            boolean shut = gateShut(tier);
            if (overview) {
                // At overview scale a full bar would cover the tiles, so the gate is a line with its name on it.
                graphics.fill(sheetX, y, sheetX + sheetWidth, y + 1, shut ? UiTheme.INK_DIM : UiTheme.PENCIL);
                Component name = tierName(tier);
                graphics.fill(sheetX + 4, y - 4, sheetX + 10 + font.width(name), y + 6, UiTheme.SHEET_GATE);
                graphics.drawString(font, name, sheetX + 7, y - 3, shut ? UiTheme.INK : UiTheme.INK_DIM, false);
                continue;
            }
            Component count = Component.translatable(SHEET + "progress", tier.learned(), tier.total());
            UiSheet.gateBar(graphics, font, sheetX, y, sheetWidth, TechTreeSheetLayout.GATE_HEIGHT,
                    sheetX + 8, tierName(tier), tier.benchTier() > 1 ? Component.translatable(
                            SHEET + (shut ? "gate.needs" : "gate.open"), benchName(tier.benchTier()))
                            : Component.empty(), shut, sheetX + sheetWidth - 8, count);
        }
        Optional<ResourceLocation> hovered = dragging ? Optional.empty()
                : tileUnder(mouseX, mouseY).map(TechTreeSheetLayout.Tile::nodeId);
        int tileWidth = scaled(TechTreeSheetLayout.TILE_WIDTH);
        int tileHeight = scaled(TechTreeSheetLayout.TILE_HEIGHT);
        for (var tile : layout.tiles()) {
            int x = sx(tile.x());
            int y = sy(tile.y());
            if (x + tileWidth < sheetX || x > sheetX + sheetWidth || y + tileHeight < sheetY || y > sheetY + sheetHeight) {
                continue;
            }
            boolean isSelected = selection.selected().filter(tile.nodeId()::equals).isPresent();
            boolean isHovered = hovered.filter(tile.nodeId()::equals).isPresent();
            graph.node(tile.nodeId()).ifPresent(node -> {
                if (overview) {
                    drawMiniTile(graphics, node, x, y, tileWidth, tileHeight, isSelected, isHovered);
                } else {
                    drawTile(graphics, node, x, y, isSelected, isHovered);
                    if (advice.kind() == TechTreeGuidance.Kind.START
                            && advice.target().filter(node.blueprintId()::equals).isPresent() && !isSelected) {
                        UiSheet.marker(graphics, x, y, TechTreeSheetLayout.TILE_WIDTH, TechTreeSheetLayout.TILE_HEIGHT,
                                UiTheme.AMBER_HIGHLIGHT);
                    }
                }
            });
        }
        selection.selected().ifPresent(id -> {
            for (var wire : layout.wiresInto(id)) {
                drawWire(graphics, wire, UiTheme.INK);
            }
        });
        // Lane names stay pinned to the top of the sheet while the tiers scroll under them.
        graphics.fill(sheetX, sheetY, sheetX + sheetWidth, sheetY + TechTreeSheetLayout.HEADER_HEIGHT, UiTheme.SHEET_GATE);
        graphics.fill(sheetX, sheetY + TechTreeSheetLayout.HEADER_HEIGHT - 1, sheetX + sheetWidth,
                sheetY + TechTreeSheetLayout.HEADER_HEIGHT, UiTheme.GRID_MAJOR);
        for (var lane : layout.lanes()) {
            int laneWidth = scaled(lane.width());
            Component count = Component.translatable(SHEET + "progress", lane.learned(), lane.total());
            boolean showCount = font.width(laneName(lane)) + 5 + font.width(count) <= laneWidth;
            String label = clip(laneName(lane).getString(), laneWidth);
            int total = font.width(label) + (showCount ? 5 + font.width(count) : 0);
            int x = sx(lane.x()) + (laneWidth - total) / 2;
            graphics.drawString(font, label, x, sheetY + 4, UiTheme.INK, false);
            if (showCount) {
                graphics.drawString(font, count, x + font.width(label) + 5, sheetY + 4, UiTheme.INK_DIM, false);
            }
        }
        graphics.disableScissor();
        if (searchOpen) {
            int fieldY = sheetY + TechTreeSheetLayout.HEADER_HEIGHT + 4;
            Component found = Component.translatable(TREE + "search.results", matches.size());
            int foundWidth = searchText.isBlank() ? 0 : font.width(found) + 8;
            UiDraw.raisedPanel(graphics, sheetX + 4, fieldY - 2, 122 + foundWidth, 24);
            UiDraw.field(graphics, sheetX + 8, fieldY + 2, 114, 16);
            if (foundWidth > 0) {
                graphics.drawString(font, found, sheetX + 126, fieldY + 6,
                        matches.isEmpty() ? UiTheme.TEXT_MUTED : UiTheme.TEXT, false);
            }
        }
    }

    /** A tile at overview scale: state colours and a small picture, no name. Click it to zoom back in. */
    private void drawMiniTile(GuiGraphics graphics, ResearchTreeGraph.Node node, int x, int y, int tileWidth,
            int tileHeight, boolean isSelected, boolean isHovered) {
        UiSheet.TileState state = state(node);
        boolean quiet = state == UiSheet.TileState.LOCKED || state == UiSheet.TileState.HIDDEN;
        graphics.fill(x, y, x + tileWidth, y + tileHeight, isHovered ? UiTheme.SHEET_LEARNED : UiSheet.tileFill(state));
        graphics.renderOutline(x, y, tileWidth, tileHeight, UiSheet.tileBorder(state));
        if (state != UiSheet.TileState.HIDDEN && tileHeight >= 10) {
            if (quiet) {
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.45F);
            }
            art.render(graphics, node, new ResearchTreeScene.Bounds(x + 2, y + 2, tileWidth - 4, tileHeight - 4));
            graphics.flush();
            if (quiet) {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
        if (isSelected) {
            graphics.renderOutline(x - 2, y - 2, tileWidth + 4, tileHeight + 4, UiTheme.INK);
        }
        if (!searchText.isBlank() && !matches.contains(node.blueprintId()) && !isSelected) {
            graphics.fill(x, y, x + tileWidth, y + tileHeight, (UiTheme.SHEET & 0xFFFFFF) | 0xB8000000);
        }
    }

    private void drawWire(GuiGraphics graphics, TechTreeSheetLayout.Wire wire, int color) {
        for (var segment : wire.segments()) {
            if (wire.alternative()) {
                UiSheet.dashedWire(graphics, sx(segment.x1()), sy(segment.y1()), sx(segment.x2()), sy(segment.y2()),
                        color);
            } else {
                UiSheet.wire(graphics, sx(segment.x1()), sy(segment.y1()), sx(segment.x2()), sy(segment.y2()), color);
            }
        }
        if (!overview && wire.crossLane() && !wire.segments().isEmpty()) {
            var last = wire.segments().get(wire.segments().size() - 1);
            UiSheet.crossPoint(graphics, sx(last.x2()), sy(last.y2()) - 2, color | 0xFF000000);
        }
    }

    private int wireColor(TechTreeSheetLayout.Wire wire) {
        if (learned(wire.from()) && learned(wire.to())) {
            return UiTheme.INK;
        }
        return wire.crossLane() ? UiTheme.WIRE_CROSS : UiTheme.WIRE;
    }

    private static Component laneName(TechTreeSheetLayout.Lane lane) {
        if (lane.nameKeys().isEmpty()) {
            return title(lane.translationKey(), lane.title());
        }
        MutableComponent name = Component.empty();
        for (int index = 0; index < lane.nameKeys().size(); index++) {
            if (index > 0) {
                name.append(" · ");
            }
            name.append(Component.translatable(lane.nameKeys().get(index)));
        }
        return name;
    }

    private void drawTile(GuiGraphics graphics, ResearchTreeGraph.Node node, int x, int y, boolean isSelected,
            boolean isHovered) {
        UiSheet.TileState state = state(node);
        UiSheet.tile(graphics, x, y, TechTreeSheetLayout.TILE_WIDTH, TechTreeSheetLayout.TILE_HEIGHT, state,
                isSelected, isHovered);
        if (state == UiSheet.TileState.HIDDEN) {
            graphics.drawString(font, "?", x + (TechTreeSheetLayout.TILE_WIDTH - font.width("?")) / 2, y + 10,
                    UiTheme.PENCIL, false);
        } else {
            boolean quiet = state == UiSheet.TileState.LOCKED;
            if (quiet) {
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.45F);
            }
            art.render(graphics, node, new ResearchTreeScene.Bounds(x + 5, y + 3, TechTreeSheetLayout.TILE_WIDTH - 10, 22));
            graphics.flush();
            if (quiet) {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
        if (node.benchTier() > 0 && !rowsAreBenchTiers) {
            // Rows follow depth here, so each tile says which bench it needs; brighter when that bench is missing.
            graphics.drawString(font, Component.translatable(TREE + "tier", node.benchTier()), x + 3, y + 3,
                    node.benchTier() > benchLevel() ? UiTheme.INK : UiTheme.PENCIL, false);
        }
        String label = clip(name(node), TechTreeSheetLayout.TILE_WIDTH - 6);
        graphics.drawString(font, label, x + (TechTreeSheetLayout.TILE_WIDTH - font.width(label)) / 2,
                y + TechTreeSheetLayout.TILE_HEIGHT - 11, UiSheet.tileText(state), false);
        if (!searchText.isBlank() && !matches.contains(node.blueprintId()) && !isSelected) {
            UiSheet.dim(graphics, x, y, TechTreeSheetLayout.TILE_WIDTH, TechTreeSheetLayout.TILE_HEIGHT);
        }
    }

    private void renderBar(GuiGraphics graphics) {
        graphics.fill(0, 0, width, BAR_HEIGHT, UiTheme.STEEL);
        graphics.fill(0, BAR_HEIGHT - 1, width, BAR_HEIGHT, UiTheme.STEEL_DARKEST);
        graphics.fill(0, BAR_HEIGHT - 2, width, BAR_HEIGHT - 1, UiTheme.RULE);
        if (showTitle) {
            graphics.drawString(font, title, 8, 8, UiTheme.TEXT, false);
        }
        if (showTag) {
            UiDraw.tag(graphics, font, tierLabel(), 8 + (showTitle ? font.width(title) + 8 : 0), 7);
        }
        Component balance = ClientResearchCurrency.text(TREE + "balance",
                currencyBalance());
        int right = barRight - 8;
        int left = domainButtons.isEmpty() ? 0 : domainButtons.get(domainButtons.size() - 1).getX()
                + domainButtons.get(domainButtons.size() - 1).getWidth() + 8;
        balanceInBar = right - font.width(balance) >= left;
        if (balanceInBar) {
            graphics.drawString(font, balance, right - font.width(balance), 8, UiTheme.MANILA, false);
        }
    }

    private void renderInspector(GuiGraphics graphics) {
        int x = width - INSPECTOR_WIDTH + 2;
        int y = BAR_HEIGHT + 2;
        int panelWidth = INSPECTOR_WIDTH - 4;
        int panelHeight = height - BAR_HEIGHT - 6;
        graphics.fill(width - INSPECTOR_WIDTH, BAR_HEIGHT, width, height, UiTheme.STEEL_DARKEST);
        UiDraw.raisedPanel(graphics, x, y, panelWidth, panelHeight);
        int left = x + 8;
        int inner = panelWidth - 16;
        Optional<ResearchTreeGraph.Node> node = selection.selected().flatMap(graph::node);
        if (node.isEmpty()) {
            int line = y + 10;
            if (!layout.tiles().isEmpty()) {
                line = wrapped(graphics, adviceText(), left, line, inner,
                        adviceIsActionable() ? UiTheme.TEXT : UiTheme.TEXT_MUTED, 6) + 6;
            }
            graphics.drawWordWrap(font, Component.translatable(BENCH + "select_hint"), left, line, inner,
                    UiTheme.TEXT_MUTED);
            return;
        }
    }

    private static int currencyBalance() {
        return ClientResearchCurrency.current().map(com.gamergaming.taczweaponblueprints.network.SyncResearchCurrencyPacket::available)
                .orElseGet(() -> ClientResearchState.publication().journal().researchPoints());
    }

    private Optional<Domain> domainOf(ResourceLocation id) { return Optional.ofNullable(domainIndex.get(id)); }

    private void followRequirement(ResourceLocation id) {
        domainOf(id).ifPresent(next -> {
            showDomain(next);
            select(id);
            setOverview(false);
            layout.tile(id).ifPresent(this::reveal);
        });
    }

    private void refreshInspector() {
        if (inspectorBody == null) return;
        var node = selection.selected().flatMap(graph::node);
        inspectorBody.visible = sheetWidth < width && node.isPresent();
        if (node.isEmpty()) { inspectorBody.content(List.of(), 0); inspectorContentKey = List.of(); return; }
        var value = node.orElseThrow();
        var status = status(value);
        var key = List.<Object>of(value, currentPreview(), status, graph, materialPage != null,
                net.minecraft.locale.Language.getInstance(), ClientResearchCurrency.policy());
        if (key.equals(inspectorContentKey)) { inspectorBody.scrollTo(inspectorScroll); return; }
        inspectorContentKey = key;
        var rows = new ArrayList<ResearchTreeInspectorRow>();
        rows.add(ResearchTreeInspectorRow.text(ResearchTreeInspectorRow.Kind.SECTION,
                Component.literal(name(value)), UiTheme.TEXT));
        if (value.benchTier() > 0) rows.add(ResearchTreeInspectorRow.text(ResearchTreeInspectorRow.Kind.EXPLANATION,
                Component.translatable(SHEET + "bench_tier", value.benchTier()), UiTheme.TEXT_MUTED));
        rows.add(ResearchTreeInspectorRow.text(ResearchTreeInspectorRow.Kind.EXPLANATION, status.text(), status.color()));
        var quote = previewFor(value);
        if (quote.isPresent() && !value.learned()) {
            var preview = quote.orElseThrow();
            var details = ResearchTreeSelectedNodePresenter.present(new ResearchTreeSelectedNodePresenter.Input(
                    value, false, preview.blueprintId(), preview, graph.requirementGroupsOf(value.blueprintId()).size(),
                    0, preview.costMode()));
            var costs = ResearchTreeInspectorCosts.rows(details, preview, com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyText.name(ClientResearchCurrency.policy()),
                    this::ingredientName, UiTheme.TEXT, UiTheme.TEXT_MUTED, UiTheme.GREEN, UiTheme.RED);
            for (var row : costs) {
                rows.add(materialPage != null && row.materialPageDirection() != 0
                        ? ResearchTreeInspectorRow.text(ResearchTreeInspectorRow.Kind.EXPLANATION, row.text(), UiTheme.TEXT_DISABLED)
                        : row);
            }
        }
        if (!value.learned() && !graph.requirementGroupsOf(value.blueprintId()).isEmpty()) {
            rows.add(ResearchTreeInspectorRow.text(ResearchTreeInspectorRow.Kind.SECTION,
                    Component.translatable(SHEET + "requires"), UiTheme.MANILA));
            rows.addAll(ResearchTreeInspectorRequirements.rows(graph, value.blueprintId(), n -> Component.literal(name(n)),
                    this::domainOf, d -> domains.stream().filter(v -> v.domain() == d).findFirst()
                            .map(v -> title(v.translationKey(), v.title())).orElse(Component.empty()),
                    id -> domainOf(id).isPresent() && graph.node(id).filter(n -> n.visibility().allowsServerSelection()).isPresent(),
                    UiTheme.TEXT, UiTheme.MANILA, UiTheme.TEXT_MUTED, UiTheme.GREEN));
        }
        inspectorBody.content(rows, inspectorScroll);
    }

    private void requestMaterialPage(int direction) {
        var preview = currentPreview();
        int to = preview.ingredientOffset() + direction
                * com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost.MAX_INGREDIENT_TYPES;
        if (materialPage != null || selection.selected().isEmpty()
                || !preview.acceptsMaterialPage(selection.selected().orElseThrow(), preview.ingredientOffset(), to)) return;
        materialPage = new PendingMaterialPage(selection.selected().orElseThrow(), to, builtGeneration);
        materialPageTicks = 100;
        PacketDistributor.sendToServer(new com.gamergaming.taczweaponblueprints.network.ResearchMaterialPageRequestPacket(
                menu.containerId, builtGeneration, materialPage.target(), preview.ingredientOffset(), to));
        refreshInspector();
    }

    private int wrapped(GuiGraphics graphics, Component text, int x, int y, int wrapWidth, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, wrapWidth);
        int shown = Math.min(maxLines, lines.size());
        for (int index = 0; index < shown; index++) {
            graphics.drawString(font, lines.get(index), x, y + index * (font.lineHeight + 1), color, false);
        }
        return y + Math.max(1, shown) * (font.lineHeight + 1);
    }

    private String clip(String text, int room) {
        if (font.width(text) <= room) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, room - font.width("…"))) + "…";
    }

    // ---- what each blueprint says ----

    private record Status(Component text, int color) { }

    private boolean learned(ResourceLocation id) {
        return graph.node(id).map(ResearchTreeGraph.Node::learned).orElse(false);
    }

    private ResearchSheetReadiness.Result readiness(ResearchTreeGraph.Node node) {
        var cache = ClientResearchAffordabilityState.snapshot();
        var evidence = cache.enabled() && cache.publicationGeneration() == builtGeneration
                ? Optional.ofNullable(cache.results().get(node.blueprintId()))
                : Optional.<com.gamergaming.taczweaponblueprints.progression.ResearchAffordabilitySnapshot.Entry>empty();
        return ResearchSheetReadiness.evaluate(node, previewFor(node), evidence, benchTooLow(node), menu.routeGuidanceAvailable());
    }

    private UiSheet.TileState state(ResearchTreeGraph.Node node) { return readiness(node).tile(); }

    private boolean benchTooLow(ResearchTreeGraph.Node node) {
        return node.benchTier() > benchLevel();
    }

    private boolean creative() {
        return minecraft != null && minecraft.player != null && minecraft.player.isCreative();
    }

    /** The bench tier the server will accept: a creative player skips tiers when the server allows it. */
    private int benchLevel() {
        return creative() && com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.researchFeatureSnapshot()
                .creativeBypassesWorkbenchTiers() ? ResearchTreeGraph.MAX_BENCH_TIER : menu.workbenchTier().level();
    }

    private String name(ResearchTreeGraph.Node node) {
        return names.computeIfAbsent(node.blueprintId(), ignored -> {
            if (!node.visibility().revealsName()) {
                return Component.translatable(ResearchTreeGraph.REDACTED_NAME_KEY).getString();
            }
            String plain = ChatFormatting.stripFormatting(ClientResearchCurrency.text(node.nameKey()).getString());
            if (plain == null) {
                return "";
            }
            return switch (domain) {
                case WEAPONS -> ItemNameFilterHelper.filterGunName(plain);
                case AMMO -> ItemNameFilterHelper.filterAmmoName(plain);
                case ATTACHMENTS -> plain;
            };
        });
    }

    private static Component tierName(TechTreeSheetLayout.Tier tier) {
        if (tier.benchTier() == TechTreeSheetLayout.UNKNOWN_ROW) {
            return Component.translatable("gui.taczweaponblueprints.journal.category.undisclosed");
        }
        return tier.benchTier() > 0 ? Component.translatable(SHEET + "bench_tier", tier.benchTier())
                : title(tier.translationKey(), tier.title());
    }

    private static Component benchName(int benchTier) {
        return Component.translatable("block.taczweaponblueprints." + switch (benchTier) {
            case 2 -> "advanced_research_bench";
            case 3 -> "experimental_research_bench";
            default -> "research_bench";
        });
    }

    /** A tier's gate is shut while the bench in use is below the tier it needs. */
    private boolean gateShut(TechTreeSheetLayout.Tier tier) {
        return tier.benchTier() > benchLevel();
    }

    private static Component title(Optional<String> translationKey, String fallback) {
        return translationKey.<Component>map(key -> Component.translatableWithFallback(key, fallback))
                .orElseGet(() -> Component.literal(fallback));
    }

    private Component hoverReason(ResearchTreeGraph.Node node) {
        var ready = readiness(node);
        if (ready.message() == ResearchTreeSelectedNodePresenter.Message.READY) {
            return ready.quotedCost() < 0 ? Component.translatable(SHEET + "hover.ready_check")
                    : ready.quotedCost() > 0 ? ClientResearchCurrency.text(SHEET + "hover.ready", ready.quotedCost())
                    : Component.translatable(SHEET + "hover.ready_free");
        }
        return message(node, ready.message());
    }

    /** The server's preview, but only once it describes the blueprint the player is looking at. */
    private Optional<ResearchSelectionPreview> previewFor(ResearchTreeGraph.Node node) {
        if (!selection.confirmed() || selection.selected().filter(node.blueprintId()::equals).isEmpty()) return Optional.empty();
        ResearchSelectionPreview preview = menu.preview();
        return preview.blueprintId().filter(node.blueprintId()::equals).isPresent()
                ? Optional.of(preview) : Optional.empty();
    }

    private Status status(ResearchTreeGraph.Node node) {
        if (!feedback.getString().isEmpty()) return new Status(feedback, feedbackColor);
        if (selection.failed()) return new Status(Component.translatable(SHEET + "selection.retry_hint"), UiTheme.RED);
        var ready = readiness(node);
        return new Status(message(node, ready.message()), ready.tile() == UiSheet.TileState.READY
                ? UiTheme.AMBER_HIGHLIGHT : ready.tile() == UiSheet.TileState.LEARNED ? UiTheme.GREEN : UiTheme.TEXT);
    }

    private Component message(ResearchTreeGraph.Node node, ResearchTreeSelectedNodePresenter.Message reason) {
        var preview = previewFor(node);
        if (reason == ResearchTreeSelectedNodePresenter.Message.WORKBENCH_TIER_REQUIRED) {
            int tier = preview.flatMap(p -> p.accessSummary().requiredTier())
                    .map(com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier::level)
                    .orElse(node.benchTier());
            return Component.translatable(SHEET + "gate.needs", benchName(tier));
        }
        if (reason == ResearchTreeSelectedNodePresenter.Message.PROGRESSION_GATE_REQUIRED) {
            return preview.flatMap(p -> p.accessSummary().messageKey()).<Component>map(key ->
                    Component.translatableWithFallback(key, Component.translatable(TREE + "selection.progression_gate_required").getString()))
                    .orElseGet(() -> Component.translatable(TREE + "selection.progression_gate_required"));
        }
        return ClientResearchCurrency.text(TREE + "selection." + reason.name().toLowerCase(Locale.ROOT));
    }

    private Component ingredientName(ResearchSelectionPreview.IngredientPreview ingredient) {
        if (ingredient.items().isEmpty()) {
            return Component.literal("#" + ingredient.tag().orElseThrow());
        }
        MutableComponent label = BuiltInRegistries.ITEM.get(ingredient.items().get(0)).getDescription().copy();
        return ingredient.items().size() > 1 ? label.append(" +" + (ingredient.items().size() - 1)) : label;
    }

    // ---- actions ----

    private void updateResearchButton() {
        if (researchButton == null) {
            return;
        }
        Optional<ResearchTreeGraph.Node> node = selection.selected().flatMap(graph::node);
        Optional<ResearchSelectionPreview> preview = node.flatMap(this::previewFor);
        researchButton.visible = sheetWidth < width && node.filter(value -> !value.learned()).isPresent();
        researchButton.active = researchButton.visible && !selection.researching()
                && (selection.failed() || preview.flatMap(ResearchPurchaseConfirmation.Intent::from).isPresent());
        if (selection.failed()) {
            researchButton.setMessage(Component.translatable(SHEET + "selection.retry"));
            researchButton.setTooltip(null);
            return;
        }
        researchButton.setTooltip(holdRequired() ? Tooltip.create(Component.translatable(SHEET + "hold.tooltip")) : null);
        researchButton.setMessage(preview.filter(ResearchSelectionPreview::pathPurchase)
                .<Component>map(value -> Component.translatable(BENCH + "research_path.compact", value.unlockCount()))
                .orElseGet(() -> Component.translatable(BENCH + "research.compact")));
    }

    private void select(ResourceLocation id) {
        cancelHold(false);
        selection.choose(id);
        materialPage = null;
        inspectorScroll = 0;
        if (inspectorBody != null) inspectorBody.reset();
        feedback = Component.empty();
        selectDelay = 0;
        sendSelection();
        triggerImmediateNarration(true);
    }

    /** Moves the selection now and asks the server for its details shortly after the last key press. */
    private void selectSoon(ResourceLocation id) {
        cancelHold(false);
        selection.choose(id);
        materialPage = null;
        inspectorScroll = 0;
        if (inspectorBody != null) inspectorBody.reset();
        feedback = Component.empty();
        selectDelay = 4;
        updateResearchButton();
        triggerImmediateNarration(true);
    }

    private void sendSelection() {
        if (selection.selected().isEmpty()) return;
        int request = menu.nextClientActionRequestId();
        selection.sentSelection(request);
        feedback = Component.empty();
        PacketDistributor.sendToServer(new ResearchBenchActionPacket(menu.containerId, request,
                ResearchBenchResearchAction.SELECT, selection.selected()));
        updateResearchButton();
        refreshInspector();
    }

    private void requestResearch() {
        ResearchPurchaseConfirmation.Intent.from(currentPreview()).ifPresent(this::requestResearch);
    }

    private void requestResearch(ResearchPurchaseConfirmation.Intent intent) {
        if (selection.researching() || !intent.matches(currentPreview())) return;
        int request = menu.nextClientActionRequestId();
        selection.sentResearch(request);
        showFeedback(Component.translatable(TREE + "selection.research_pending"), UiTheme.TEXT_MUTED, 0);
        PacketDistributor.sendToServer(new ResearchBenchActionPacket(menu.containerId, request,
                ResearchBenchResearchAction.RESEARCH, Optional.of(intent.target()), Optional.of(intent.quote())));
        updateResearchButton();
    }

    private void showFeedback(Component text, int color, int ticks) {
        feedback = text;
        feedbackColor = color;
        feedbackTicks = ticks;
        triggerImmediateNarration(true);
    }

    @Override
    public void acceptActionResult(int requestId, ResearchBenchMenu.ActionResult result) {
        if (result == null) return;
        if (result.action() == ResearchBenchResearchAction.SELECT) {
            if (selection.acceptSelection(requestId, result.blueprintId(), result.successful())) {
                if (!result.successful()) showFeedback(Component.translatable(SHEET + "selection.retry_hint"), UiTheme.RED, FEEDBACK_TICKS);
                updateResearchButton(); refreshInspector(); refreshGuidance();
            }
            return;
        }
        var completed = selection.acceptResearch(requestId, result.blueprintId(), result.successful());
        if (completed.isEmpty()) return;
        confirmation.cancel();
        if (completed.orElseThrow()) {
            showFeedback(result.successful() ? Component.translatable(TREE + "selection.research_success")
                    : ClientResearchCurrency.text(TREE + "selection." + failureKey(result.code())),
                    result.successful() ? UiTheme.GREEN : UiTheme.RED, FEEDBACK_TICKS);
        }
        ClientResearchAffordabilityState.invalidateResources(graph, builtGeneration);
        affordabilityDelay = 3;
        minecraft.getSoundManager().play(result.successful()
                ? SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.15F)
                : SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.75F));
        updateResearchButton(); refreshInspector(); refreshGuidance();
    }

    /** Maps a refusal to wording the player can act on. Anything unexpected asks them to try again. */
    static String failureKey(ResearchBenchMenu.ActionResultCode code) {
        return switch (code) {
            case ALREADY_LEARNED -> "learned";
            case DISCOVERY_REQUIRED -> "discovery_required";
            case PREREQUISITES_REQUIRED -> "prerequisites_required";
            case POINTS_REQUIRED -> "points_required";
            case INGREDIENTS_REQUIRED -> "materials_required";
            case OUTPUT_FULL -> "inventory_space_required";
            case PROGRESSION_CAPACITY_EXHAUSTED -> "progression_capacity_exhausted";
            case WORKBENCH_TIER_REQUIRED -> "workbench_tier_required";
            case PROGRESSION_GATE_REQUIRED -> "progression_gate_required";
            case RESEARCH_DISABLED -> "research_disabled";
            case CONTENT_UNAVAILABLE -> "content_unavailable";
            case PATH_TOO_LARGE -> "path_too_large";
            case ROUTE_TOO_COMPLEX -> "route_too_complex";
            case TECH_TREE_UNAVAILABLE -> "tech_tree_unavailable";
            case UNSATISFIABLE -> "unsatisfiable";
            default -> "requirements_unavailable";
        };
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && holdRequired() && !selection.failed() && researchButton.visible && researchButton.active
                && researchButton.isMouseOver(mouseX, mouseY)) {
            setFocused(null);
            beginHold(false);
            return true;
        }
        // The container screen claims every click for item slots, and this menu has none. Give the
        // click to the buttons and the search box first, then to the sheet.
        for (var child : children()) {
            if (child.mouseClicked(mouseX, mouseY, button)) {
                // Next hands focus back to the sheet so Enter researches the blueprint it just selected.
                setFocused(child == nextButton ? null : child);
                if (button == 0) {
                    setDragging(true);
                }
                return true;
            }
        }
        if (overLegend(mouseX, mouseY)) {
            legendOpen = false;
            buildWidgets();
            return true;
        }
        if (button == 0 && overSheet(mouseX, mouseY)) {
            setFocused(null);
            pressedOnSheet = true;
            dragging = false;
            pressX = mouseX;
            pressY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (inspectorBody != null && inspectorBody.draggingScrollbar())
            return inspectorBody.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        if (pressedOnSheet) {
            if (!dragging && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > DRAG_THRESHOLD) {
                dragging = true;
            }
            if (dragging) {
                scrollX -= (int) Math.round((mouseX - pressX) / scale);
                scrollY -= (int) Math.round((mouseY - pressY) / scale);
                pressX = mouseX;
                pressY = mouseY;
                clampScroll();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) setDragging(false);
        if (button == 0 && confirmation.pressed() && !confirmation.byKey()) {
            cancelHold(true);
            return true;
        }
        if (inspectorBody != null && inspectorBody.draggingScrollbar())
            return inspectorBody.mouseReleased(mouseX, mouseY, button);
        if (pressedOnSheet) {
            pressedOnSheet = false;
            if (!dragging) {
                tileUnder(mouseX, mouseY).ifPresent(tile -> {
                    select(tile.nodeId());
                    // In the overview a click means "take me there".
                    setOverview(false);
                });
            }
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inspectorBody != null && inspectorBody.visible && inspectorBody.isFocused()
                && inspectorBody.keyPressed(keyCode, scanCode, modifiers)) return true;
        boolean typing = searchBox != null && searchBox.isFocused();
        if (keyCode == 256 && legendOpen) {
            legendOpen = false;
            buildWidgets();
            return true;
        }
        if (keyCode == 256 && searchOpen) {
            toggleSearch();
            return true;
        }
        if (typing) {
            if (keyCode == 257 || keyCode == 335) {
                jumpToNextMatch();
                return true;
            }
            // While typing, letters must not trigger the inventory key and close the bench.
            return searchBox.keyPressed(keyCode, scanCode, modifiers) || searchBox.canConsumeInput();
        }
        if (keyCode == 70 && hasControlDown()) {
            if (!searchOpen) {
                toggleSearch();
            } else {
                setFocused(searchBox);
            }
            return true;
        }
        if (keyCode == 70) {
            setOverview(!overview);
            return true;
        }
        if (keyCode == 78) {
            jumpToNextReady();
            return true;
        }
        int dx = keyCode == 262 ? 1 : keyCode == 263 ? -1 : 0;
        int dy = keyCode == 264 ? 1 : keyCode == 265 ? -1 : 0;
        if (dx != 0 || dy != 0) {
            layout.neighbor(selection.selected().orElse(null), dx, dy).ifPresent(tile -> {
                setFocused(null);
                selectSoon(tile.nodeId());
                reveal(tile);
            });
            return true;
        }
        if ((keyCode == 257 || keyCode == 335) && researchButton.active
                && (getFocused() == null || getFocused() == researchButton)) {
            if (selection.failed()) {
                sendSelection();
            } else if (holdRequired()) {
                // Key repeat keeps calling this while Enter is down; the hold started on the first press.
                beginHold(true);
            } else {
                requestResearch();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && confirmation.pressed() && confirmation.byKey()) {
            cancelHold(true);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (inspectorBody != null && inspectorBody.visible && inspectorBody.isMouseOver(mouseX, mouseY)) {
            inspectorBody.wheel(vertical);
            return true;
        }
        if (!overSheet(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        }
        if (hasControlDown() && vertical != 0) {
            setOverview(vertical < 0);
            return true;
        }
        boolean sideways = hasShiftDown() && horizontal == 0;
        scrollX -= (int) Math.round((sideways ? vertical : horizontal) * 28);
        scrollY -= sideways ? 0 : (int) Math.round(vertical * 28);
        clampScroll();
        return true;
    }

    @Override
    protected void updateNarrationState(NarrationElementOutput output) {
        super.updateNarrationState(output);
        selection.selected().flatMap(graph::node).ifPresent(node -> output.add(NarratedElementType.HINT,
                Component.literal(name(node)).append(". ").append(status(node).text())));
        if (!layout.tiles().isEmpty()) {
            output.add(NarratedElementType.USAGE, adviceText());
        }
    }
}
