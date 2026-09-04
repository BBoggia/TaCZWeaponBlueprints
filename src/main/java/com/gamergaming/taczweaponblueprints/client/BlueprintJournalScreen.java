package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalEntry;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.util.ItemNameFilterHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Read-only presentation of the server-authored, disclosure-filtered Journal. */
public final class BlueprintJournalScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 680;
    private static final int ROW_HEIGHT = 22;
    private static final int CONTROL_HEIGHT = 20;
    private static final int PANEL_PADDING = 10;
    private static final int WIDE_THRESHOLD = 520;
    private static final int MAX_RECENT_DETAIL_MEMBERS = 8;
    private static final ResearchTreeGuidancePreference ONBOARDING_PREFERENCE =
            ResearchTreeGuidancePreference.client();

    private BlueprintJournalSnapshot snapshot = BlueprintJournalSnapshot.EMPTY;
    private BlueprintJournalQuery.Result result;
    private BlueprintJournalQuery.HistoryResult historyResult;
    private BlueprintJournalQuery.RecentResult recentResult;
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();

    private EditBox searchBox;
    private Button statusButton;
    private Button categoryButton;
    private Button sortButton;
    private Button viewButton;
    private Button previousButton;
    private Button nextButton;
    private Button backButton;
    private Button guideButton;
    private Button onboardingDismissButton;
    private BlueprintJournalQuery.StatusFilter status = BlueprintJournalQuery.StatusFilter.ALL;
    private BlueprintJournalQuery.SortOrder sort = BlueprintJournalQuery.SortOrder.CATALOG;
    private List<String> categories = List.of();
    private int categoryIndex = -1;
    private int page;
    private JournalView view = JournalView.CURRENT;
    private BlueprintJournalEntry selectedEntry;
    private BlueprintJournalSnapshot.HistoryEntry selectedHistory;
    private BlueprintJournalSnapshot.RecentUnlockBatch selectedRecent;
    private boolean onboardingView;
    private boolean onboardingInitialized;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelBottom;
    private int listWidth;
    private int listTop;
    private int listBottom;

    public BlueprintJournalScreen() {
        super(Component.translatable("gui.taczweaponblueprints.journal.title"));
    }

    @Override
    protected void init() {
        String previousSearch = searchBox == null ? "" : searchBox.getValue();
        snapshot = ClientBlueprintJournal.snapshot();
        String previousCategory = selectedCategory();
        categories = BlueprintJournalQuery.categories(snapshot.entries());
        categoryIndex = categories.indexOf(previousCategory);
        if (!onboardingInitialized) {
            onboardingView = ONBOARDING_PREFERENCE.shouldShowOnboarding();
            onboardingInitialized = true;
        }
        calculateLayout();
        buildControls(previousSearch);
        refreshRows();
        setInitialFocus(compactDetailsOpen() ? backButton : searchBox);
    }

    private void calculateLayout() {
        panelWidth = Math.max(300, Math.min(MAX_PANEL_WIDTH, width - 20));
        panelX = (width - panelWidth) / 2;
        panelY = 10;
        panelBottom = height - 10;
        boolean wide = panelWidth >= WIDE_THRESHOLD;
        listWidth = wide ? Math.min(310, (panelWidth - PANEL_PADDING * 3) / 2) : panelWidth - PANEL_PADDING * 2;
        listTop = panelY + 109;
        listBottom = panelBottom - 34;
    }

    private void buildControls(String initialSearch) {
        int left = panelX + PANEL_PADDING;
        int half = (listWidth - 4) / 2;
        searchBox = addRenderableWidget(new EditBox(
                font, left, panelY + 39, listWidth, CONTROL_HEIGHT,
                Component.translatable("gui.taczweaponblueprints.journal.search.narration")));
        searchBox.setMaxLength(BlueprintJournalQuery.MAX_SEARCH_LENGTH);
        searchBox.setHint(Component.translatable("gui.taczweaponblueprints.journal.search"));
        searchBox.setValue(initialSearch);
        searchBox.setResponder(ignored -> resetAndRefresh());

        statusButton = addRenderableWidget(Button.builder(statusLabel(), ignored -> {
            status = status.next();
            statusButton.setMessage(statusLabel());
            resetAndRefresh();
        }).bounds(left, panelY + 63, half, CONTROL_HEIGHT).build());
        categoryButton = addRenderableWidget(Button.builder(categoryLabel(), ignored -> {
            cycleCategory();
            categoryButton.setMessage(categoryLabel());
            resetAndRefresh();
        }).bounds(left + half + 4, panelY + 63, half, CONTROL_HEIGHT).build());
        sortButton = addRenderableWidget(Button.builder(sortLabel(), ignored -> {
            sort = sort.next();
            sortButton.setMessage(sortLabel());
            resetAndRefresh();
        }).bounds(left, panelY + 85, half, CONTROL_HEIGHT).build());
        viewButton = addRenderableWidget(Button.builder(viewLabel(), ignored -> {
            view = view.next(snapshot);
            page = 0;
            selectedEntry = null;
            selectedHistory = null;
            selectedRecent = null;
            updateControlState();
            refreshRows();
        }).bounds(left + half + 4, panelY + 85, half, CONTROL_HEIGHT).build());

        int pagerWidth = Math.min(90, (listWidth - 70) / 2);
        previousButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.taczweaponblueprints.journal.previous"), ignored -> changePage(-1))
                .bounds(left, panelBottom - 28, pagerWidth, CONTROL_HEIGHT).build());
        nextButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.taczweaponblueprints.journal.next"), ignored -> changePage(1))
                .bounds(left + listWidth - pagerWidth, panelBottom - 28, pagerWidth, CONTROL_HEIGHT).build());
        backButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.taczweaponblueprints.journal.back"), ignored -> closeCompactDetails())
                .bounds(left, panelBottom - 28, Math.min(100, listWidth), CONTROL_HEIGHT).build());
        guideButton = addRenderableWidget(Button.builder(Component.literal("?"), ignored -> toggleOnboarding())
                .bounds(panelX + panelWidth - PANEL_PADDING - 20, panelY + 5, 20, 20)
                .build());
        guideButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.taczweaponblueprints.journal.onboarding.help")));
        onboardingDismissButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.taczweaponblueprints.journal.onboarding.got_it"),
                ignored -> dismissOnboarding())
                .bounds(panelX + panelWidth / 2 - 50, panelBottom - 31, 100, CONTROL_HEIGHT)
                .build());
        updateControlState();
    }

    private void updateControlState() {
        statusButton.active = view == JournalView.CURRENT;
        categoryButton.active = view == JournalView.CURRENT && !categories.isEmpty();
        sortButton.active = view == JournalView.CURRENT;
        viewButton.active = view.availableViewCount(snapshot) > 1;
        viewButton.setMessage(viewLabel());
        boolean compactDetails = compactDetailsOpen();
        boolean browseVisible = !onboardingView && !compactDetails;
        searchBox.visible = browseVisible;
        statusButton.visible = browseVisible;
        categoryButton.visible = browseVisible;
        sortButton.visible = browseVisible;
        viewButton.visible = browseVisible;
        previousButton.visible = browseVisible;
        nextButton.visible = browseVisible;
        backButton.visible = !onboardingView && compactDetails;
        backButton.active = !onboardingView && compactDetails;
        guideButton.visible = true;
        onboardingDismissButton.visible = onboardingView;
        onboardingDismissButton.active = onboardingView;
        for (AbstractWidget row : rowWidgets) {
            row.visible = browseVisible;
            row.active = browseVisible;
        }
    }

    private void resetAndRefresh() {
        page = 0;
        selectedEntry = null;
        selectedHistory = null;
        selectedRecent = null;
        refreshRows();
    }

    private void cycleCategory() {
        if (categories.isEmpty()) {
            categoryIndex = -1;
            return;
        }
        categoryIndex++;
        if (categoryIndex >= categories.size()) {
            categoryIndex = -1;
        }
    }

    private void refreshRows() {
        for (AbstractWidget widget : rowWidgets) {
            removeWidget(widget);
        }
        rowWidgets.clear();
        rows.clear();

        int pageSize = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        String search = searchBox == null ? "" : searchBox.getValue();
        if (view == JournalView.UNAVAILABLE) {
            historyResult = BlueprintJournalQuery.queryHistory(
                    snapshot.unavailableHistory(), search, page, pageSize);
            page = historyResult.page();
            for (BlueprintJournalSnapshot.HistoryEntry entry : historyResult.entries()) {
                addHistoryRow(entry);
            }
        } else if (view == JournalView.RECENT) {
            recentResult = BlueprintJournalQuery.queryRecent(
                    snapshot.recentUnlocks(), search, page, pageSize, this::recentName);
            page = recentResult.page();
            for (BlueprintJournalSnapshot.RecentUnlockBatch entry : recentResult.entries()) {
                addRecentRow(entry);
            }
        } else {
            result = BlueprintJournalQuery.query(
                    snapshot.entries(), search, status, selectedCategory(), sort,
                    page, pageSize, this::resolvedName);
            page = result.page();
            for (BlueprintJournalEntry entry : result.entries()) {
                addEntryRow(entry);
            }
        }
        updatePagerState();
        updateControlState();
    }

    private void addEntryRow(BlueprintJournalEntry entry) {
        int y = listTop + rows.size() * ROW_HEIGHT;
        Component name = entryName(entry);
        Component message = Component.literal(entry.blueprintId().isPresent() ? "     " : "")
                .append(Component.literal("["))
                .append(statusName(entry))
                .append(Component.literal("] "))
                .append(name)
                .append(journalFragmentRowSuffix(entry));
        Button button = Button.builder(message, ignored -> {
            selectedEntry = entry;
            selectedHistory = null;
            selectedRecent = null;
            updateControlState();
            if (compactDetailsOpen()) {
                setInitialFocus(backButton);
            }
            triggerImmediateNarration(true);
        }).bounds(panelX + PANEL_PADDING, y, listWidth, ROW_HEIGHT - 2).build();
        addRenderableWidget(button);
        rowWidgets.add(button);
        rows.add(new Row(button, entry));
    }

    private void addHistoryRow(BlueprintJournalSnapshot.HistoryEntry entry) {
        int y = listTop + rows.size() * ROW_HEIGHT;
        Component status = Component.translatable(entry.learned()
                ? "gui.taczweaponblueprints.journal.status.learned"
                : "gui.taczweaponblueprints.journal.status.discovered");
        Component message = Component.literal("[").append(status).append("] ")
                .append(Component.literal(entry.blueprintId().toString()));
        Button button = Button.builder(message, ignored -> {
            selectedHistory = entry;
            selectedEntry = null;
            selectedRecent = null;
            updateControlState();
            if (compactDetailsOpen()) {
                setInitialFocus(backButton);
            }
            triggerImmediateNarration(true);
        }).bounds(panelX + PANEL_PADDING, y, listWidth, ROW_HEIGHT - 2).build();
        addRenderableWidget(button);
        rowWidgets.add(button);
        rows.add(new Row(button, null));
    }

    private void addRecentRow(BlueprintJournalSnapshot.RecentUnlockBatch entry) {
        int y = listTop + rows.size() * ROW_HEIGHT;
        Component message = Component.translatable(
                "gui.taczweaponblueprints.journal.recent.row",
                recentName(entry.targetBlueprintId()),
                entry.totalMemberCount());
        Button button = Button.builder(message, ignored -> {
            selectedRecent = entry;
            selectedEntry = null;
            selectedHistory = null;
            updateControlState();
            if (compactDetailsOpen()) {
                setInitialFocus(backButton);
            }
            triggerImmediateNarration(true);
        }).bounds(panelX + PANEL_PADDING, y, listWidth, ROW_HEIGHT - 2).build();
        addRenderableWidget(button);
        rowWidgets.add(button);
        rows.add(new Row(button, null));
    }

    private void updatePagerState() {
        int pageCount = activePageCount();
        previousButton.active = page > 0;
        nextButton.active = page + 1 < pageCount;
    }

    private void changePage(int direction) {
        int pageCount = activePageCount();
        page = Math.max(0, Math.min(page + direction, pageCount - 1));
        selectedEntry = null;
        selectedHistory = null;
        selectedRecent = null;
        refreshRows();
    }

    @Override
    public void tick() {
        if (searchBox.visible) {
            searchBox.tick();
        }
        BlueprintJournalSnapshot latest = ClientBlueprintJournal.snapshot();
        if (latest != snapshot) {
            snapshot = latest;
            String previousCategory = selectedCategory();
            categories = BlueprintJournalQuery.categories(snapshot.entries());
            categoryIndex = categories.indexOf(previousCategory);
            if (!view.available(snapshot)) {
                view = JournalView.CURRENT;
            }
            selectedEntry = null;
            selectedHistory = null;
            selectedRecent = null;
            updateControlState();
            categoryButton.setMessage(categoryLabel());
            refreshRows();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelBottom, 0xE510151C);
        graphics.renderOutline(panelX, panelY, panelWidth, panelBottom - panelY, 0xFF6B7C8F);
        renderHeader(graphics);
        if (onboardingView) {
            renderOnboarding(graphics);
        } else {
            if (!compactDetailsOpen()) {
                renderListBackground(graphics);
            }
            renderDetails(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!onboardingView && !compactDetailsOpen()) {
            renderRowIcons(graphics);
            renderPagerLabel(graphics);
        }
    }

    private void renderHeader(GuiGraphics graphics) {
        int left = panelX + PANEL_PADDING;
        graphics.drawString(font, title, left, panelY + 10, 0xFFFFFFFF);
        if (onboardingView) {
            return;
        }
        Component points = Component.translatable(
                "gui.taczweaponblueprints.journal.points",
                snapshot.researchPoints(), snapshot.pointCap());
        int pointsRight = guideButton == null ? left + listWidth : guideButton.getX() - 4;
        if (left + font.width(title) + 6 > pointsRight - font.width(points)) {
            points = Component.translatable(
                    "gui.taczweaponblueprints.journal.points.compact",
                    snapshot.researchPoints(), snapshot.pointCap());
        }
        graphics.drawString(font, points, pointsRight - font.width(points), panelY + 10, 0xFFE4C56A);

        int total = snapshot.completionTotal();
        int learned = snapshot.learnedCount();
        int barY = panelY + 25;
        graphics.fill(left, barY, left + listWidth, barY + 8, 0xFF222A33);
        int filled = total == 0 ? 0 : (int) ((long) listWidth * learned / total);
        graphics.fill(left, barY, left + filled, barY + 8, 0xFF4EAF70);
        Component completion = Component.translatable(
                "gui.taczweaponblueprints.journal.completion", learned, total);
        graphics.drawCenteredString(font, completion, left + listWidth / 2, barY, 0xFFFFFFFF);
    }

    private void renderOnboarding(GuiGraphics graphics) {
        int x = panelX + PANEL_PADDING;
        int y = panelY + 39;
        int contentWidth = panelWidth - PANEL_PADDING * 2;
        int contentBottom = panelBottom - 38;
        graphics.fill(x, y, x + contentWidth, contentBottom, 0xC0202730);
        graphics.enableScissor(x + 1, y + 1, x + contentWidth - 1, contentBottom - 1);

        int textX = x + 10;
        int textWidth = contentWidth - 20;
        int line = y + 9;
        Component heading = Component.translatable(
                "gui.taczweaponblueprints.journal.onboarding.title");
        graphics.drawString(font, heading, textX, line, 0xFFE4C56A);
        line += font.lineHeight + 5;
        Component intro = Component.translatable(
                "gui.taczweaponblueprints.journal.onboarding.intro");
        graphics.drawWordWrap(font, intro, textX, line, textWidth, 0xFFCCCCCC);
        line += Math.max(1, font.split(intro, textWidth).size()) * font.lineHeight + 5;

        BlueprintOnboardingPlan plan = BlueprintOnboardingPlan.from(
                snapshot, ClientResearchPointPresentationState.help());
        for (BlueprintOnboardingPlan.Step step : plan.steps()) {
            Component label = Component.translatable(
                    "gui.taczweaponblueprints.journal.onboarding.step." + step.key());
            Component rendered = Component.literal(onboardingPrefix(step.state()) + " ").append(label);
            int color = onboardingColor(step.state());
            graphics.drawWordWrap(font, rendered, textX, line, textWidth, color);
            line += Math.max(1, font.split(rendered, textWidth).size()) * font.lineHeight + 2;
        }

        if (!plan.earningHelp().isEmpty() && line + font.lineHeight * 2 < contentBottom) {
            line += 3;
            graphics.drawString(font, Component.translatable(
                    "gui.taczweaponblueprints.journal.onboarding.earning_title"),
                    textX, line, 0xFFE4C56A);
            line += font.lineHeight + 3;
            for (var help : plan.earningHelp()) {
                Component earning = Component.translatable(
                        "gui.taczweaponblueprints.journal.onboarding.earning",
                        Component.translatable(help.nameKey()), help.points());
                graphics.drawWordWrap(font, earning, textX + 6, line, textWidth - 6, 0xFFCCCCCC);
                line += Math.max(1, font.split(earning, textWidth - 6).size()) * font.lineHeight + 3;
                if (line >= contentBottom) {
                    break;
                }
            }
        }
        graphics.disableScissor();
    }

    private static String onboardingPrefix(BlueprintOnboardingPlan.State state) {
        return switch (state) {
            case COMPLETE -> "[x]";
            case CURRENT -> "->";
            case LATER -> "[ ]";
            case OPTIONAL -> "[+]";
        };
    }

    private static int onboardingColor(BlueprintOnboardingPlan.State state) {
        return switch (state) {
            case COMPLETE -> 0xFF65D58A;
            case CURRENT -> 0xFFE4C56A;
            case LATER -> 0xFFAAAAAA;
            case OPTIONAL -> 0xFF63C5DA;
        };
    }

    private void renderListBackground(GuiGraphics graphics) {
        graphics.fill(
                panelX + PANEL_PADDING, listTop - 2,
                panelX + PANEL_PADDING + listWidth, listBottom,
                0x80202730);
        int matches = activeTotalMatches();
        if (matches == 0) {
            String emptyKey;
            if (view == JournalView.UNAVAILABLE) {
                emptyKey = "gui.taczweaponblueprints.journal.history.empty";
            } else if (view == JournalView.RECENT) {
                emptyKey = "gui.taczweaponblueprints.journal.recent.empty";
            } else if (snapshot.entries().isEmpty()) {
                emptyKey = "gui.taczweaponblueprints.journal.unavailable";
            } else {
                emptyKey = "gui.taczweaponblueprints.journal.empty";
            }
            Component empty = Component.translatable(emptyKey);
            graphics.drawCenteredString(
                    font, empty,
                    panelX + PANEL_PADDING + listWidth / 2,
                    listTop + 12,
                    0xFFAAAAAA);
        }
    }

    private void renderRowIcons(GuiGraphics graphics) {
        for (Row row : rows) {
            if (row.entry == null || row.entry.blueprintId().isEmpty()) {
                continue;
            }
            ItemStack stack = BlueprintItem.createBlueprint(row.entry.blueprintId().orElseThrow().toString());
            graphics.renderItem(stack, row.button.getX() + 2, row.button.getY() + 2);
        }
    }

    private void renderPagerLabel(GuiGraphics graphics) {
        int pageCount = activePageCount();
        Component label = Component.translatable(
                "gui.taczweaponblueprints.journal.page", page + 1, pageCount);
        graphics.drawCenteredString(
                font, label,
                panelX + PANEL_PADDING + listWidth / 2,
                panelBottom - 22,
                0xFFCCCCCC);
    }

    private void renderDetails(GuiGraphics graphics) {
        boolean compact = panelWidth < WIDE_THRESHOLD;
        if (compact && !compactDetailsOpen()) {
            return;
        }
        int x = compact ? panelX + PANEL_PADDING : panelX + PANEL_PADDING * 2 + listWidth;
        int detailWidth = compact ? listWidth : panelX + panelWidth - PANEL_PADDING - x;
        graphics.fill(x, panelY + 39, x + detailWidth, panelBottom - PANEL_PADDING, 0x80202730);
        graphics.drawString(
                font,
                Component.translatable("gui.taczweaponblueprints.journal.details"),
                x + 8, panelY + 48, 0xFFFFFFFF);
        int contentBottom = compact ? panelBottom - 34 : panelBottom - PANEL_PADDING - 1;
        graphics.enableScissor(x + 1, panelY + 62, x + detailWidth - 1, contentBottom);
        if (selectedEntry != null) {
            renderEntryDetails(graphics, x + 8, panelY + 66, detailWidth - 16, selectedEntry);
        } else if (selectedHistory != null) {
            renderHistoryDetails(graphics, x + 8, panelY + 66, detailWidth - 16, selectedHistory);
        } else if (selectedRecent != null) {
            renderRecentDetails(graphics, x + 8, panelY + 66, detailWidth - 16, selectedRecent);
        } else {
            graphics.drawWordWrap(
                    font,
                    Component.translatable("gui.taczweaponblueprints.journal.details.hint"),
                    x + 8, panelY + 66, detailWidth - 16, 0xFFAAAAAA);
        }
        graphics.disableScissor();
    }

    private void renderEntryDetails(
            GuiGraphics graphics,
            int x,
            int y,
            int detailWidth,
            BlueprintJournalEntry entry) {
        int textX = x;
        if (entry.visibility().revealsIcon() && entry.blueprintId().isPresent()) {
            ItemStack stack = BlueprintItem.createBlueprint(entry.blueprintId().orElseThrow().toString());
            graphics.renderItem(stack, x, y);
            textX += 22;
        }
        Component name = entryName(entry);
        int nameWidth = detailWidth - (textX - x);
        graphics.drawWordWrap(font, name, textX, y + 2, nameWidth, 0xFFFFFFFF);
        int nameHeight = Math.max(18, font.split(name, nameWidth).size() * font.lineHeight);
        int line = y + nameHeight + 5;
        line = detailLine(graphics, x, line, detailWidth,
                Component.translatable("gui.taczweaponblueprints.journal.detail.status"), statusName(entry));
        line = detailLine(graphics, x, line, detailWidth,
                Component.translatable("gui.taczweaponblueprints.journal.detail.visibility"), visibilityName(entry.visibility()));
        if (entry.itemType().isPresent()) {
            line = detailLine(graphics, x, line, detailWidth,
                    Component.translatable("gui.taczweaponblueprints.journal.detail.category"),
                    categoryName(entry.itemType().orElseThrow()));
        }
        if (entry.blueprintId().isPresent()) {
            line = detailLine(graphics, x, line, detailWidth,
                    Component.translatable("gui.taczweaponblueprints.journal.detail.id"),
                    Component.literal(entry.blueprintId().orElseThrow().toString()));
        }
        if (entry.craftingAccess().isPresent()) {
            line = detailLine(
                    graphics,
                    x,
                    line,
                    detailWidth,
                    Component.translatable(
                            "gui.taczweaponblueprints.journal.detail.crafting"),
                    craftingAccessText(entry.craftingAccess().orElseThrow()));
        }
        if (entry.fragmentProgress().isPresent()) {
            BlueprintJournalEntry.FragmentProgress progress =
                    entry.fragmentProgress().orElseThrow();
            line = detailLine(
                    graphics,
                    x,
                    line,
                    detailWidth,
                    Component.translatable(
                            "gui.taczweaponblueprints.journal.detail.fragments"),
                    Component.translatable(
                            progress.complete()
                                    ? "gui.taczweaponblueprints.journal.detail.fragments_complete"
                                    : "gui.taczweaponblueprints.journal.detail.fragments_progress",
                            progress.displayedArchived(),
                            progress.threshold()));
        }
        if (entry.researchPointCost() > 0 || entry.researchable()) {
            line += 4;
            graphics.drawString(font,
                    Component.translatable("gui.taczweaponblueprints.journal.research"),
                    x, line, 0xFFE4C56A);
            line += font.lineHeight + 2;
            line = detailLine(graphics, x, line, detailWidth,
                    Component.translatable("gui.taczweaponblueprints.journal.detail.point_cost"),
                    Component.literal(Integer.toString(entry.researchPointCost())));
            line = detailLine(graphics, x, line, detailWidth,
                    Component.translatable("gui.taczweaponblueprints.journal.detail.ingredients"),
                    Component.literal(Integer.toString(entry.ingredientTypeCount())));
            line = detailLine(graphics, x, line, detailWidth,
                    Component.translatable("gui.taczweaponblueprints.journal.detail.prerequisites"),
                    Component.literal(Integer.toString(entry.prerequisiteCount())));
            if (entry.visibility().revealsExactPolicy()) {
                line = detailLine(graphics, x, line, detailWidth,
                        Component.translatable("gui.taczweaponblueprints.journal.detail.points_available"),
                        yesNo(entry.canAffordPoints()));
            }
        }
        if (entry.recyclingValue() > 0) {
            line += font.lineHeight + 6;
            detailLine(graphics, x, line, detailWidth,
                    Component.translatable("gui.taczweaponblueprints.journal.detail.recycling_value"),
                    Component.literal(Integer.toString(entry.recyclingValue())));
        }
    }

    private void renderHistoryDetails(
            GuiGraphics graphics,
            int x,
            int y,
            int detailWidth,
            BlueprintJournalSnapshot.HistoryEntry entry) {
        Component unavailable = Component.translatable("gui.taczweaponblueprints.journal.history.unavailable");
        graphics.drawWordWrap(font, unavailable, x, y, detailWidth, 0xFFFFAA55);
        int line = y + Math.max(1, font.split(unavailable, detailWidth).size()) * font.lineHeight + 4;
        line = detailLine(graphics, x, line, detailWidth,
                Component.translatable("gui.taczweaponblueprints.journal.detail.id"),
                Component.literal(entry.blueprintId().toString()));
        detailLine(graphics, x, line, detailWidth,
                Component.translatable("gui.taczweaponblueprints.journal.detail.status"),
                Component.translatable(entry.learned()
                        ? "gui.taczweaponblueprints.journal.status.learned"
                        : "gui.taczweaponblueprints.journal.status.discovered"));
    }

    private void renderRecentDetails(
            GuiGraphics graphics,
            int x,
            int y,
            int detailWidth,
            BlueprintJournalSnapshot.RecentUnlockBatch entry) {
        Component target = Component.translatable(
                "gui.taczweaponblueprints.journal.recent.target",
                recentName(entry.targetBlueprintId()));
        graphics.drawWordWrap(font, target, x, y, detailWidth, 0xFFE4C56A);
        int line = y + Math.max(1, font.split(target, detailWidth).size()) * font.lineHeight + 4;
        line = detailLine(graphics, x, line, detailWidth,
                Component.translatable("gui.taczweaponblueprints.journal.recent.source"),
                recentSourceName(entry));
        line = detailLine(graphics, x, line, detailWidth,
                Component.translatable("gui.taczweaponblueprints.journal.recent.unlocked"),
                Component.literal(Integer.toString(entry.totalMemberCount())));
        line += 4;
        graphics.drawString(font,
                Component.translatable("gui.taczweaponblueprints.journal.recent.members"),
                x, line, 0xFFCCCCCC);
        line += font.lineHeight + 2;
        List<ResourceLocation> displayedMembers = entry.memberBlueprintIds().stream()
                .limit(MAX_RECENT_DETAIL_MEMBERS)
                .toList();
        for (ResourceLocation member : displayedMembers) {
            Component memberName = Component.literal("• ").append(Component.literal(recentName(member)));
            graphics.drawWordWrap(font, memberName, x, line, detailWidth, 0xFFFFFFFF);
            line += Math.max(1, font.split(memberName, detailWidth).size()) * font.lineHeight + 1;
        }
        int undisplayed = entry.totalMemberCount() - displayedMembers.size();
        if (undisplayed > 0) {
            Component more = Component.translatable(
                    "gui.taczweaponblueprints.journal.recent.more",
                    undisplayed);
            graphics.drawWordWrap(font, more, x, line, detailWidth, 0xFFAAAAAA);
        }
    }

    private int detailLine(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            Component label,
            Component value) {
        Component line = label.copy().withStyle(ChatFormatting.GRAY)
                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                .append(value.copy().withStyle(ChatFormatting.WHITE));
        graphics.drawWordWrap(font, line, x, y, width, 0xFFFFFFFF);
        int lines = Math.max(1, font.split(line, width).size());
        return y + lines * font.lineHeight + 2;
    }

    private Component entryName(BlueprintJournalEntry entry) {
        if (entry.visibility() == JournalVisibility.SILHOUETTE) {
            return Component.translatable(
                    "gui.taczweaponblueprints.journal.undiscovered", entry.ordinal() + 1);
        }
        return Component.literal(resolvedName(entry));
    }

    private Component journalFragmentRowSuffix(BlueprintJournalEntry entry) {
        return entry.fragmentProgress()
                .filter(progress -> progress.archived() > 0)
                .<Component>map(progress -> Component.translatable(
                        "gui.taczweaponblueprints.journal.row.fragments",
                        progress.displayedArchived(),
                        progress.threshold()))
                .orElse(Component.empty());
    }

    private String resolvedName(BlueprintJournalEntry entry) {
        String resolved = entry.nameKey().map(key -> Component.translatable(key).getString()).orElse("");
        if (entry.itemType().isEmpty()) {
            return resolved;
        }
        return switch (entry.itemType().orElseThrow()) {
            case "rifle", "shotgun", "pistol", "sniper", "smg", "mg", "rpg" ->
                    ItemNameFilterHelper.filterGunName(resolved);
            case "ammo" -> ItemNameFilterHelper.filterAmmoName(resolved);
            default -> resolved;
        };
    }

    private Component statusName(BlueprintJournalEntry entry) {
        String suffix;
        if (entry.learned()) {
            suffix = "learned";
        } else if (entry.researchable()) {
            suffix = "researchable";
        } else if (entry.discovered()) {
            suffix = "discovered";
        } else if (entry.recyclable()) {
            suffix = "recyclable";
        } else if (entry.visibility() == JournalVisibility.SILHOUETTE
                || entry.visibility() == JournalVisibility.NAME) {
            suffix = "unrevealed";
        } else if (entry.visibility() == JournalVisibility.PREVIEW) {
            suffix = "preview";
        } else {
            suffix = "locked";
        }
        return Component.translatable("gui.taczweaponblueprints.journal.status." + suffix);
    }

    private Component visibilityName(JournalVisibility visibility) {
        return Component.translatable(
                "gui.taczweaponblueprints.journal.visibility." + visibility.name().toLowerCase(Locale.ROOT));
    }

    private Component statusLabel() {
        return Component.translatable(
                "gui.taczweaponblueprints.journal.filter",
                Component.translatable("gui.taczweaponblueprints.journal.filter."
                        + status.name().toLowerCase(Locale.ROOT)));
    }

    private Component categoryLabel() {
        Component value = categoryIndex < 0
                ? Component.translatable("gui.taczweaponblueprints.journal.category.all")
                : categoryName(categories.get(categoryIndex));
        return Component.translatable("gui.taczweaponblueprints.journal.category", value);
    }

    private Component sortLabel() {
        return Component.translatable(
                "gui.taczweaponblueprints.journal.sort",
                Component.translatable("gui.taczweaponblueprints.journal.sort."
                        + sort.name().toLowerCase(Locale.ROOT)));
    }

    private Component viewLabel() {
        return Component.translatable(
                "gui.taczweaponblueprints.journal.view",
                Component.translatable("gui.taczweaponblueprints.journal.view."
                        + view.name().toLowerCase(Locale.ROOT)));
    }

    private String recentName(ResourceLocation id) {
        if (id == null) {
            return "";
        }
        return snapshot.entries().stream()
                .filter(entry -> entry.blueprintId().filter(id::equals).isPresent())
                .findFirst()
                .map(this::resolvedName)
                .filter(name -> !name.isBlank())
                .orElse(id.toString());
    }

    private Component recentSourceName(BlueprintJournalSnapshot.RecentUnlockBatch batch) {
        return Component.translatable("gui.taczweaponblueprints.journal.recent.source."
                + batch.source().name().toLowerCase(Locale.ROOT));
    }

    private int activePageCount() {
        return switch (view) {
            case CURRENT -> result.pageCount();
            case RECENT -> recentResult.pageCount();
            case UNAVAILABLE -> historyResult.pageCount();
        };
    }

    private int activeTotalMatches() {
        return switch (view) {
            case CURRENT -> result.totalMatches();
            case RECENT -> recentResult.totalMatches();
            case UNAVAILABLE -> historyResult.totalMatches();
        };
    }

    private Component categoryName(String category) {
        String normalized = category.toLowerCase(Locale.ROOT);
        String fallback = normalized.isEmpty()
                ? normalized
                : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1).replace('_', ' ');
        return Component.translatableWithFallback(
                "gui.taczweaponblueprints.journal.category." + normalized, fallback);
    }

    private String selectedCategory() {
        return categoryIndex < 0 || categoryIndex >= categories.size() ? "" : categories.get(categoryIndex);
    }

    private Component yesNo(boolean value) {
        return Component.translatable(value ? "gui.yes" : "gui.no");
    }

    @Override
    public Component getNarrationMessage() {
        if (onboardingView) {
            return onboardingNarration();
        }
        if (selectedEntry != null) {
            Component narration = Component.translatable(
                    "gui.taczweaponblueprints.journal.detail.narration",
                    entryName(selectedEntry),
                    statusName(selectedEntry),
                    visibilityName(selectedEntry.visibility()));
            if (selectedEntry.itemType().isPresent()) {
                narration = narration.copy().append(Component.literal(" ")).append(Component.translatable(
                        "gui.taczweaponblueprints.journal.detail.category_narration",
                        categoryName(selectedEntry.itemType().orElseThrow())));
            }
            if (selectedEntry.researchPointCost() > 0 || selectedEntry.researchable()) {
                narration = narration.copy().append(Component.literal(" ")).append(Component.translatable(
                        "gui.taczweaponblueprints.journal.detail.research_narration",
                        selectedEntry.researchPointCost(),
                        selectedEntry.ingredientTypeCount(),
                        selectedEntry.prerequisiteCount(),
                        yesNo(selectedEntry.canAffordPoints())));
            }
            if (selectedEntry.fragmentProgress().isPresent()) {
                BlueprintJournalEntry.FragmentProgress progress =
                        selectedEntry.fragmentProgress().orElseThrow();
                narration = narration.copy().append(Component.literal(" ")).append(
                        Component.translatable(
                                "gui.taczweaponblueprints.journal.detail.fragments_narration",
                                progress.displayedArchived(),
                                progress.threshold(),
                                yesNo(progress.complete())));
            }
            if (selectedEntry.craftingAccess().isPresent()) {
                narration = narration.copy().append(Component.literal(" ")).append(
                        Component.translatable(
                                "gui.taczweaponblueprints.journal.detail.crafting_narration",
                                craftingAccessText(
                                        selectedEntry.craftingAccess().orElseThrow())));
            }
            return narration;
        }
        if (selectedHistory != null) {
            return Component.translatable(
                    "gui.taczweaponblueprints.journal.history.narration",
                    selectedHistory.blueprintId().toString(),
                    Component.translatable(selectedHistory.learned()
                            ? "gui.taczweaponblueprints.journal.status.learned"
                            : "gui.taczweaponblueprints.journal.status.discovered"));
        }
        if (selectedRecent != null) {
            return Component.translatable(
                    "gui.taczweaponblueprints.journal.recent.narration",
                    recentName(selectedRecent.targetBlueprintId()),
                    selectedRecent.totalMemberCount(),
                    recentSourceName(selectedRecent));
        }
        return super.getNarrationMessage();
    }

    private Component craftingAccessText(
            com.gamergaming.taczweaponblueprints.progression.DisclosedCraftingAccess access) {
        return switch (access.disposition()) {
            case TIERED -> Component.translatable(
                    "gui.taczweaponblueprints.crafting_access.level",
                    access.requiredWorkbenchTier().orElseThrow().level());
            case UNRESTRICTED -> Component.translatable(
                    "gui.taczweaponblueprints.crafting_access.any_workbench");
            case DISABLED -> Component.translatable(
                    "gui.taczweaponblueprints.crafting_access.disabled");
        };
    }

    private Component onboardingNarration() {
        BlueprintOnboardingPlan plan = BlueprintOnboardingPlan.from(
                snapshot, ClientResearchPointPresentationState.help());
        var narration = Component.translatable(
                "gui.taczweaponblueprints.journal.onboarding.narration");
        for (BlueprintOnboardingPlan.Step step : plan.steps()) {
            narration.append(Component.literal(" ")).append(Component.translatable(
                    "gui.taczweaponblueprints.journal.onboarding.step.narration",
                    Component.translatable("gui.taczweaponblueprints.journal.onboarding.state."
                            + step.state().name().toLowerCase(Locale.ROOT)),
                    Component.translatable("gui.taczweaponblueprints.journal.onboarding.step."
                            + step.key())));
        }
        return narration;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!compactDetailsOpen()
                && mouseX >= panelX + PANEL_PADDING
                && mouseX <= panelX + PANEL_PADDING + listWidth
                && mouseY >= listTop
                && mouseY <= listBottom
                && delta != 0.0D) {
            changePage(delta > 0.0D ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && onboardingView) {
            onboardingView = false;
            updateControlState();
            setInitialFocus(searchBox);
            return true;
        }
        if (keyCode == 256 && compactDetailsOpen()) {
            closeCompactDetails();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean compactDetailsOpen() {
        return panelWidth < WIDE_THRESHOLD
                && (selectedEntry != null || selectedHistory != null || selectedRecent != null);
    }

    private void closeCompactDetails() {
        selectedEntry = null;
        selectedHistory = null;
        selectedRecent = null;
        updateControlState();
        setInitialFocus(searchBox);
    }

    private void toggleOnboarding() {
        onboardingView = !onboardingView;
        updateControlState();
        setInitialFocus(onboardingView ? onboardingDismissButton : searchBox);
        triggerImmediateNarration(true);
    }

    private void dismissOnboarding() {
        ONBOARDING_PREFERENCE.dismissOnboarding();
        onboardingView = false;
        updateControlState();
        setInitialFocus(searchBox);
        triggerImmediateNarration(true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Row(Button button, BlueprintJournalEntry entry) {
    }

    private enum JournalView {
        CURRENT,
        RECENT,
        UNAVAILABLE;

        private JournalView next(BlueprintJournalSnapshot snapshot) {
            JournalView candidate = this;
            do {
                candidate = values()[(candidate.ordinal() + 1) % values().length];
            } while (!candidate.available(snapshot) && candidate != this);
            return candidate;
        }

        private boolean available(BlueprintJournalSnapshot snapshot) {
            return switch (this) {
                case CURRENT -> true;
                case RECENT -> snapshot != null && !snapshot.recentUnlocks().isEmpty();
                case UNAVAILABLE -> snapshot != null && !snapshot.unavailableHistory().isEmpty();
            };
        }

        private int availableViewCount(BlueprintJournalSnapshot snapshot) {
            int count = 1;
            if (snapshot != null && !snapshot.recentUnlocks().isEmpty()) {
                count++;
            }
            if (snapshot != null && !snapshot.unavailableHistory().isEmpty()) {
                count++;
            }
            return count;
        }
    }
}
