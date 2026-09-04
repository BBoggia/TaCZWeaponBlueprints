package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalEntry;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchResearchAction;
import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.network.ResearchAffordabilityRequestPacket;
import com.gamergaming.taczweaponblueprints.network.ResearchBenchActionPacket;
import com.gamergaming.taczweaponblueprints.network.ResearchGuidanceRequestPacket;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.progression.ResearchGuidanceSnapshot;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Permanent Research-only tree backed entirely by the open server menu. */
public final class ResearchBenchScreen extends AbstractContainerScreen<ResearchBenchMenu> {
    private static final int KEYBOARD_REVEAL_PADDING = 18;
    private static final ResearchTreeGuidancePreference GUIDANCE_PREFERENCE =
            ResearchTreeGuidancePreference.client();
    private static final ResearchTreeScreenLayout.Layout COMPACT_TREE_LAYOUT =
            ResearchTreeScreenLayout.compact();
    private static final int TREE_X = COMPACT_TREE_LAYOUT.canvas().x();
    private static final int TREE_Y = COMPACT_TREE_LAYOUT.canvas().y();
    private static final int TREE_WIDTH = COMPACT_TREE_LAYOUT.canvas().width();
    private static final int DETAIL_X = COMPACT_TREE_LAYOUT.details().x();
    private static final int DETAIL_Y = COMPACT_TREE_LAYOUT.details().y();
    private static final int DETAIL_WIDTH = COMPACT_TREE_LAYOUT.details().width();
    private static final List<ResearchTreeDetailLayout.RelationSlot> COMPACT_RELATION_SLOTS =
            ResearchTreeDetailLayout.compact(COMPACT_TREE_LAYOUT.details());
    private static final int PANEL = 0xF0141920;
    private static final int SECTION = 0xC0202730;
    private static final int SLOT = 0xFF0B0F14;
    private static final int BORDER = 0xFF68798C;
    private static final int MUTED = 0xFF9FAAB5;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int ACCENT = 0xFFE4C56A;
    private static final int GOOD = 0xFF70C98B;
    private static final int BAD = 0xFFFF7777;
    private static final int WARN = 0xFFFFA45C;
    private static final int EDGE = 0xFF536476;
    private static final int GRID = 0x403B4957;
    private static final int RAIL_COLLAPSE_DELAY_TICKS = 60;
    private static final int GUIDANCE_INVENTORY_DEBOUNCE_TICKS = 3;
    private static final int GUIDANCE_RETRY_TICKS = 20;
    private static final int AFFORDABILITY_RETRY_TICKS = 20;
    private static final long REQUEST_TIMEOUT_MILLIS = 5_000L;
    private static final long GUIDANCE_RESPONSE_TIMEOUT_MILLIS = 10_000L;
    private static final long AFFORDABILITY_RESPONSE_TIMEOUT_MILLIS = 60_000L;

    private final ResearchTreeCanvas treeCanvas = new ResearchTreeCanvas(
            new ResearchTreeViewState(),
            new ResearchTreeCanvas.Style(
                    SLOT, GRID, BORDER, MUTED, TEXT, ACCENT, EDGE,
                    0xFF183023, 0xFF292516, 0xFF291B13, 0xFF151A20, 0xFF111820,
                    0xFFE4C56A, 0xFF9B874E,
                    0xFF62C7D9, 0xFF477E89,
                    0xFF394552, TEXT, 0x700B0F14,
                    0x181D2A35, 0x90141920,
                    0x10283846, 0x50475869, 0xC018222C));
    private final ResearchTreeFeedbackState selectionFeedback =
            new ResearchTreeFeedbackState();
    private final ResearchTreeFeedbackState researchFeedback =
            new ResearchTreeFeedbackState();
    private final ResearchTreeSearchController treeSearch =
            new ResearchTreeSearchController();
    private final ResearchTreeSelectionController treeSelection =
            new ResearchTreeSelectionController();
    private final ResearchTreeContextCardPresenter contextCardPresenter =
            new ResearchTreeContextCardPresenter();
    private final ResearchTreeUiUpdateController uiUpdates =
            new ResearchTreeUiUpdateController();
    private final ResearchTreeGestureTracker fullscreenGesture =
            new ResearchTreeGestureTracker();
    private final ResearchTreeHoldActivationController fullscreenHoldActivation =
            new ResearchTreeHoldActivationController();
    private final ResearchTreeProjectionCache treeProjections =
            new ResearchTreeProjectionCache();
    private final ResearchTreeNavigationState treeNavigation =
            new ResearchTreeNavigationState();
    private final ResearchTechTreeViewState techTreeNavigation =
            new ResearchTechTreeViewState();
    private final ResearchTreeCameraStore cameraStates = new ResearchTreeCameraStore();
    private final ResearchTreeFullscreenOverlayState fullscreenOverlayState =
            new ResearchTreeFullscreenOverlayState();
    private final ResearchTreeMinimap treeMinimap = new ResearchTreeMinimap();
    private final Inventory playerInventory;
    private Map<ResourceLocation, BlueprintJournalEntry> journalEntries = Map.of();
    private Map<ResourceLocation, ItemStack> researchTreeIcons = Map.of();
    private int researchPoints;
    private long projectionRevision;
    private Object researchPublicationIdentity;
    private ResearchTreeLayoutPolicy researchLayoutPolicyIdentity;
    private ResearchTreeDisplayPolicy researchDisplayPolicyIdentity;
    private boolean researchTreePublicationRejected;
    private Optional<ResearchTreePlanner.Plan> researchPlan = Optional.empty();
    private ResearchTreeScreenLayout.Layout activeTreeLayout = COMPACT_TREE_LAYOUT;
    private ResearchTreeFullscreenLayout.Layout fullscreenOverlayLayout;
    private ResearchTreeFullscreenRailLayout.Layout fullscreenRailLayout;
    private ResearchTreeContextCardLayout.Layout fullscreenContextCardLayout;
    private FullscreenCardWidgetState fullscreenCardWidgetState;
    private ResearchTreeSearchResultLayout.Layout searchResultLayout;
    private boolean fullscreen = ResearchBenchPresentationPolicy.permanentFullscreen();
    private ResearchTreeCameraStore.Key activeCameraKey;
    private boolean lastProjectionCameraRestored;
    private ResearchTreeProjection.CrossGroupLink pendingPortalActivation;
    private ResearchTechTreeLayout.PortalTarget pendingTechTreePortalActivation;
    private boolean guidanceInitialized;
    private boolean guidanceVisible;
    private boolean restoreSearchFocus;
    private int sidebarScroll;
    private int railIdleTicks;
    private int guidanceInventoryDebounceTicks;
    private int guidanceRetryTicks;
    private int affordabilityRetryTicks;
    private Boolean routeGuidanceAvailabilityIdentity;
    private double lastMouseX = -1.0D;
    private double lastMouseY = -1.0D;
    private long lastCameraFrameMillis;
    private List<InventoryEntry> guidanceInventoryIdentity;
    private int nextActionRequestId = 1;
    private EditBox pendingSearchFocus;
    private EditBox searchBox;
    private Button searchToggleButton;
    private Button primaryResearchButton;
    private Button returnToSelectionButton;
    private Button trackResearchButton;
    private Button researchGoalButton;
    private Button affordabilityButton;
    private Button zoomOutButton;
    private Button zoomInButton;
    private Button browseViewButton;
    private Button groupButton;
    private Button fitButton;
    private Button fullscreenButton;
    private Button guidanceDismissButton;
    private Button recommendationButton;
    private Button helpButton;
    private Button railPinButton;
    private List<RelationCardButton> relationButtons = List.of();
    private List<RailEntryButton> sidebarButtons = List.of();
    private List<TechTreeDomainButton> techTreeDomainButtons = List.of();
    private List<SearchResultButton> searchResultButtons = List.of();

    public ResearchBenchScreen(ResearchBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        playerInventory = inventory;
        guidanceInventoryIdentity = captureGuidanceInventory(inventory);
        imageWidth = ResearchTreeScreenLayout.COMPACT_WIDTH;
        imageHeight = ResearchTreeScreenLayout.COMPACT_HEIGHT;
        inventoryLabelX = 74;
        inventoryLabelY = 140;
    }

    @Override
    protected void init() {
        uiUpdates.invalidateWidgets();
        contextCardPresenter.invalidate();
        fullscreenCardWidgetState = null;
        String retainedSearch = searchBox == null ? "" : searchBox.getValue();
        boolean retainedSearchFocus = restoreSearchFocus
                || searchBox != null && searchBox.isFocused();
        restoreSearchFocus = false;
        fullscreen = ResearchBenchPresentationPolicy.permanentFullscreen();
        imageWidth = width;
        imageHeight = height;
        super.init();
        activeTreeLayout = ResearchTreeScreenLayout.fullscreen(width, height, false);
        fullscreenOverlayLayout = ResearchTreeFullscreenLayout.forScreen(width, height);
        fullscreenRailLayout = ResearchTreeFullscreenRailLayout.forLayout(fullscreenOverlayLayout);
        if (!guidanceInitialized) {
            guidanceVisible = GUIDANCE_PREFERENCE.shouldShow();
            fullscreenOverlayState.setRailPinned(GUIDANCE_PREFERENCE.railPinned());
            guidanceInitialized = true;
        }
        fullscreenOverlayState.setGuidanceVisible(guidanceVisible);
        refreshDisplayPolicy();
        treeCanvas.setBounds(
                activeTreeLayout.mode(),
                offset(activeTreeLayout.canvas()));

        ResearchTreeScreenLayout.Rect searchBounds = fullscreen
                ? fullscreenOverlayLayout.searchField()
                : activeTreeLayout.search();
        searchResultLayout = ResearchTreeSearchResultLayout.below(
                searchBounds, imageWidth, imageHeight);
        ResearchTreeScreenLayout.Rect zoomOutBounds = fullscreen
                ? fullscreenRailLayout.zoomOut()
                : activeTreeLayout.zoomOut();
        ResearchTreeScreenLayout.Rect zoomInBounds = fullscreen
                ? fullscreenRailLayout.zoomIn()
                : activeTreeLayout.zoomIn();
        ResearchTreeScreenLayout.Rect fitBounds = fullscreen
                ? fullscreenRailLayout.fit()
                : activeTreeLayout.showAll();
        ResearchTreeScreenLayout.Rect closeBounds = fullscreen
                ? fullscreenOverlayLayout.close()
                : activeTreeLayout.expand();

        searchBox = addRenderableWidget(new EditBox(
                font,
                leftPos + searchBounds.x(),
                topPos + searchBounds.y(),
                searchBounds.width(),
                searchBounds.height(),
                Component.translatable("gui.taczweaponblueprints.research_bench.search.narration")));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.translatable("gui.taczweaponblueprints.research_bench.search"));
        searchBox.setValue(retainedSearch);
        searchBox.setResponder(ignored -> applyTreeSearch());
        ResearchTreeScreenLayout.Rect searchToggleBounds = fullscreen
                ? fullscreenOverlayLayout.searchButton()
                : new ResearchTreeScreenLayout.Rect(
                        activeTreeLayout.search().x(),
                        activeTreeLayout.search().y(),
                        ResearchTreeFullscreenLayout.CONTROL_SIZE,
                        activeTreeLayout.search().height());
        searchToggleButton = addRenderableWidget(Button.builder(
                Component.literal("⌕"),
                ignored -> toggleFullscreenSearch())
                .bounds(
                        leftPos + searchToggleBounds.x(),
                        topPos + searchToggleBounds.y(),
                        searchToggleBounds.width(),
                        searchToggleBounds.height())
                .createNarration(ignored -> searchToggleNarration())
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.search.toggle")))
                .build());
        zoomOutButton = addRenderableWidget(Button.builder(
                Component.literal("−"), ignored -> useRailAndZoom(-1.0D))
                .bounds(
                        leftPos + zoomOutBounds.x(),
                        topPos + zoomOutBounds.y(),
                        zoomOutBounds.width(),
                        zoomOutBounds.height())
                .createNarration(ignored -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.zoom_out"))
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.zoom_out")))
                .build());
        zoomInButton = addRenderableWidget(Button.builder(
                Component.literal("+"), ignored -> useRailAndZoom(1.0D))
                .bounds(
                        leftPos + zoomInBounds.x(),
                        topPos + zoomInBounds.y(),
                        zoomInBounds.width(),
                        zoomInBounds.height())
                .createNarration(ignored -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.zoom_in"))
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.zoom_in")))
                .build());
        browseViewButton = addRenderableWidget(Button.builder(
                browseViewShortName(),
                ignored -> toggleBrowseView())
                .bounds(
                        leftPos + activeTreeLayout.browseView().x(),
                        topPos + activeTreeLayout.browseView().y(),
                        activeTreeLayout.browseView().width(),
                        activeTreeLayout.browseView().height())
                .createNarration(ignored -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.view.tooltip",
                        currentBrowseViewName()))
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.view.tooltip",
                        currentBrowseViewName())))
                .build());
        groupButton = addRenderableWidget(Button.builder(
                currentGroupName(),
                ignored -> cycleResearchGroup())
                .bounds(
                        leftPos + activeTreeLayout.groupSelector().x(),
                        topPos + activeTreeLayout.groupSelector().y(),
                        activeTreeLayout.groupSelector().width(),
                        activeTreeLayout.groupSelector().height())
                .createNarration(ignored -> Component.translatable(
                        isTechTreeView()
                                ? "gui.taczweaponblueprints.research_bench.tree.domain.tooltip"
                                : "gui.taczweaponblueprints.research_bench.tree.group.tooltip",
                        currentGroupName()))
                .tooltip(Tooltip.create(Component.translatable(
                        isTechTreeView()
                                ? "gui.taczweaponblueprints.research_bench.tree.domain.tooltip"
                                : "gui.taczweaponblueprints.research_bench.tree.group.tooltip",
                        currentGroupName())))
                .build());
        createTechTreeDomainButtons();
        fitButton = addRenderableWidget(Button.builder(
                fullscreen
                        ? Component.literal("□")
                        : Component.translatable("gui.taczweaponblueprints.research_bench.tree.fit"),
                ignored -> useRailAndFit())
                .bounds(
                        leftPos + fitBounds.x(),
                        topPos + fitBounds.y(),
                        fitBounds.width(),
                        fitBounds.height())
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.fit")))
                .build());
        fullscreenButton = addRenderableWidget(Button.builder(
                Component.literal("×"),
                ignored -> closePermanentFullscreen())
                .bounds(
                        leftPos + closeBounds.x(),
                        topPos + closeBounds.y(),
                        closeBounds.width(),
                        closeBounds.height())
                .createNarration(ignored -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.close"))
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.close")))
                .build());
        ResearchTreeScreenLayout.Rect actionBounds = fullscreen
                ? new ResearchTreeScreenLayout.Rect(4, 4, 72, 20)
                : ResearchTreeDetailLayout.primaryAction(activeTreeLayout)
                        .orElseThrow();
        primaryResearchButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.taczweaponblueprints.research_bench.research"),
                ignored -> requestResearch())
                .bounds(
                        leftPos + actionBounds.x(),
                        topPos + actionBounds.y(),
                        actionBounds.width(),
                        actionBounds.height())
                .build());
        returnToSelectionButton = addRenderableWidget(Button.builder(
                Component.literal("↩"),
                ignored -> returnToPinnedNode())
                .bounds(
                        4,
                        4,
                        ResearchTreeContextCardLayout.RETURN_ACTION_SIZE,
                        ResearchTreeContextCardLayout.RETURN_ACTION_SIZE)
                .createNarration(ignored -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.return_selection"))
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.return_selection.tooltip")))
                .build());
        ResearchTreeScreenLayout.Rect trackBounds = fullscreen
                ? new ResearchTreeScreenLayout.Rect(0, 0,
                        ResearchTreeContextCardLayout.RETURN_ACTION_SIZE,
                        ResearchTreeContextCardLayout.RETURN_ACTION_SIZE)
                : new ResearchTreeScreenLayout.Rect(156, 20, 46, 18);
        trackResearchButton = addRenderableWidget(Button.builder(
                Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.plan.track"),
                ignored -> toggleTrackedResearch())
                .bounds(
                        leftPos + trackBounds.x(),
                        topPos + trackBounds.y(),
                        trackBounds.width(),
                        trackBounds.height())
                .createNarration(ignored -> trackResearchNarration())
                .build());
        ResearchTreeScreenLayout.Rect goalBounds = fullscreen
                ? fullscreenOverlayLayout.coachmark()
                : new ResearchTreeScreenLayout.Rect(0, 0, 1, 1);
        researchGoalButton = addRenderableWidget(Button.builder(
                Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.goal.checking"),
                ignored -> focusTrackedResearchGoal())
                .bounds(
                        leftPos + goalBounds.x(),
                        topPos + goalBounds.y(),
                        goalBounds.width(),
                        goalBounds.height())
                .createNarration(ignored -> researchGoalNarration())
                .build());
        ResearchTreeGuidanceLayout.Guide guidance = activeGuidanceLayout();
        guidanceDismissButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.taczweaponblueprints.research_bench.tree.guide.dismiss"),
                ignored -> dismissGuidance())
                .bounds(
                        leftPos + guidance.dismiss().x(),
                        topPos + guidance.dismiss().y(),
                        guidance.dismiss().width(),
                        guidance.dismiss().height())
                .build());
        ResearchTreeScreenLayout.Rect recommendationBounds = fullscreen
                ? fullscreenRailLayout.recommendation()
                : new ResearchTreeScreenLayout.Rect(204, 20, 74, 18);
        recommendationButton = addRenderableWidget(Button.builder(
                fullscreen
                        ? Component.literal("→")
                        : Component.translatable(
                                "gui.taczweaponblueprints.research_bench.tree.recommendation.button"),
                ignored -> focusRecommendedBlueprint())
                .bounds(
                        leftPos + recommendationBounds.x(),
                        topPos + recommendationBounds.y(),
                        recommendationBounds.width(),
                        recommendationBounds.height())
                .createNarration(ignored -> recommendationNarration())
                .build());
        ResearchTreeScreenLayout.Rect affordabilityBounds = fullscreen
                ? fullscreenRailLayout.affordability()
                : new ResearchTreeScreenLayout.Rect(0, 0, 20, 20);
        affordabilityButton = addRenderableWidget(Button.builder(
                Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.affordable.button.off"),
                ignored -> toggleAffordableNow())
                .bounds(
                        leftPos + affordabilityBounds.x(),
                        topPos + affordabilityBounds.y(),
                        affordabilityBounds.width(),
                        affordabilityBounds.height())
                .createNarration(ignored -> affordabilityNarration())
                .build());
        ResearchTreeScreenLayout.Rect helpBounds = fullscreen
                ? fullscreenRailLayout.help()
                : new ResearchTreeScreenLayout.Rect(
                        Math.min(280, imageWidth - 30), 20, 22, 18);
        helpButton = addRenderableWidget(Button.builder(
                Component.literal("?"),
                ignored -> showGuidance())
                .bounds(
                        leftPos + helpBounds.x(),
                        topPos + helpBounds.y(),
                        helpBounds.width(),
                        helpBounds.height())
                .createNarration(ignored -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.guide.help"))
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.guide.help")))
                .build());
        ResearchTreeScreenLayout.Rect pinBounds = fullscreen
                ? fullscreenRailLayout.pin()
                : new ResearchTreeScreenLayout.Rect(0, 0, 20, 20);
        railPinButton = addRenderableWidget(Button.builder(
                Component.literal("○"),
                ignored -> toggleRailPin())
                .bounds(
                        leftPos + pinBounds.x(),
                        topPos + pinBounds.y(),
                        pinBounds.width(),
                        pinBounds.height())
                .createNarration(ignored -> railPinNarration())
                .build());
        configureTabOrder();
        ArrayList<RelationCardButton> nextRelationButtons = new ArrayList<>();
        List<ResearchTreeDetailLayout.RelationSlot> relationSlots = activeRelationSlots();
        for (int index = 0; index < relationSlots.size(); index++) {
            RelationCardButton button = addRenderableWidget(
                    new RelationCardButton(relationSlots.get(index)));
            button.setTabOrderGroup(60 + index);
            nextRelationButtons.add(button);
        }
        relationButtons = List.copyOf(nextRelationButtons);
        createSidebarButtons();
        createSearchResultButtons();
        updateTreeSafeInsets();

        if (researchPublicationIdentity == null
                || ClientResearchState.publication() != researchPublicationIdentity
                || !ModConfigs.RESEARCH_TREE_CLIENT.layoutPolicy()
                        .equals(researchLayoutPolicyIdentity)) {
            reloadResearchTree(researchPublicationIdentity == null);
        } else {
            applyActiveProjection(treeCanvas.focusedId().orElse(null));
        }
        synchronizeRouteGuidanceAvailability();
        applyTreeSearch();
        updateWidgets();
        if ((retainedSearchFocus
                        || fullscreen && fullscreenOverlayState.searchState()
                                == ResearchTreeFullscreenOverlayState.SearchState.FOCUSED)
                && (!fullscreen || fullscreenOverlayState.searchState()
                        != ResearchTreeFullscreenOverlayState.SearchState.CLOSED)) {
            setInitialFocus(searchBox);
            pendingSearchFocus = searchBox;
        } else {
            pendingSearchFocus = null;
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        cancelTreeInteraction();
        lastCameraFrameMillis = 0L;
        saveActiveCamera();
        activeCameraKey = null;
        restoreSearchFocus = searchBox != null && getFocused() == searchBox;
        super.resize(minecraft, width, height);
    }

    @Override
    public void removed() {
        treeMinimap.cancelNavigation();
        ClientResearchAffordabilityState.abandonPending();
        ClientResearchGuidanceState.abandonPending();
        super.removed();
    }

    private void configureTabOrder() {
        searchToggleButton.setTabOrderGroup(0);
        searchBox.setTabOrderGroup(1);
        zoomOutButton.setTabOrderGroup(30);
        zoomInButton.setTabOrderGroup(31);
        fitButton.setTabOrderGroup(32);
        browseViewButton.setTabOrderGroup(33);
        groupButton.setTabOrderGroup(34);
        for (int index = 0; index < techTreeDomainButtons.size(); index++) {
            techTreeDomainButtons.get(index).setTabOrderGroup(34 + index);
        }
        fullscreenButton.setTabOrderGroup(39);
        trackResearchButton.setTabOrderGroup(40);
        researchGoalButton.setTabOrderGroup(41);
        returnToSelectionButton.setTabOrderGroup(42);
        primaryResearchButton.setTabOrderGroup(43);
        guidanceDismissButton.setTabOrderGroup(44);
        recommendationButton.setTabOrderGroup(35);
        affordabilityButton.setTabOrderGroup(36);
        railPinButton.setTabOrderGroup(37);
        helpButton.setTabOrderGroup(38);
    }

    private void createSidebarButtons() {
        if (!fullscreen || fullscreenRailLayout == null) {
            sidebarButtons = List.of();
            return;
        }
        List<ResearchTreeScreenLayout.Rect> slots = fullscreenRailLayout.entries();
        ArrayList<RailEntryButton> buttons = new ArrayList<>(slots.size());
        for (int slot = 0; slot < slots.size(); slot++) {
            int slotIndex = slot;
            ResearchTreeScreenLayout.Rect bounds = slots.get(slot);
            RailEntryButton button = addRenderableWidget(new RailEntryButton(
                    leftPos + bounds.x(),
                    topPos + bounds.y(),
                    slotIndex));
            button.setTabOrderGroup(2 + slot);
            buttons.add(button);
        }
        sidebarButtons = List.copyOf(buttons);
    }

    private void createTechTreeDomainButtons() {
        ArrayList<TechTreeDomainButton> buttons = new ArrayList<>();
        for (ResearchTechTreeDomainSelectorLayout.Entry entry
                : ResearchTechTreeDomainSelectorLayout.forBounds(
                        activeTreeLayout.groupSelector())) {
            ResearchTreeScreenLayout.Rect bounds = entry.bounds();
            buttons.add(addRenderableWidget(new TechTreeDomainButton(
                    leftPos + bounds.x(),
                    topPos + bounds.y(),
                    bounds.width(),
                    bounds.height(),
                    entry.domain())));
        }
        techTreeDomainButtons = List.copyOf(buttons);
    }

    private void createSearchResultButtons() {
        if (searchResultLayout == null) {
            searchResultButtons = List.of();
            return;
        }
        ArrayList<SearchResultButton> buttons =
                new ArrayList<>(searchResultLayout.rows().size());
        for (int index = 0; index < searchResultLayout.rows().size(); index++) {
            ResearchTreeScreenLayout.Rect bounds = searchResultLayout.rows().get(index);
            SearchResultButton button = addRenderableWidget(new SearchResultButton(
                    leftPos + bounds.x(),
                    topPos + bounds.y(),
                    bounds.width(),
                    bounds.height()));
            button.setTabOrderGroup(20 + index);
            buttons.add(button);
        }
        searchResultButtons = List.copyOf(buttons);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (getFocused() != null && !children().contains(getFocused())) {
            setFocused(null);
        }
        EditBox focusToRestore = pendingSearchFocus;
        pendingSearchFocus = null;
        if (focusToRestore == searchBox
                && (getFocused() == null || getFocused() == searchBox)) {
            setInitialFocus(searchBox);
        }
        Object latest = ClientResearchState.publication();
        ResearchTreeLayoutPolicy latestLayoutPolicy =
                ModConfigs.RESEARCH_TREE_CLIENT.layoutPolicy();
        if (latest != researchPublicationIdentity
                || !latestLayoutPolicy.equals(researchLayoutPolicyIdentity)) {
            reloadResearchTree(false);
            applyTreeSearch();
        }
        synchronizeRouteGuidanceAvailability();
        refreshGuidanceAfterInventoryChange();
        long nowMillis = Util.getMillis();
        refreshAuthoritativeRequestTimeouts(nowMillis);
        refreshGuidanceAfterThrottle();
        refreshAffordabilityAfterThrottle();
        treeCanvas.setAuthoritativeSelection(menu.selectedBlueprint().orElse(null));
        boolean feedbackExpired = selectionFeedback.expirePending(
                nowMillis, REQUEST_TIMEOUT_MILLIS, "selection_timeout");
        feedbackExpired |= researchFeedback.expirePending(
                nowMillis, REQUEST_TIMEOUT_MILLIS, "request_timeout");
        if (feedbackExpired) {
            uiUpdates.invalidateWidgets();
        }
        updateFullscreenHoldActivation(nowMillis);
        updateFullscreenOverlayLifecycle();
        updateWidgets();
    }

    private void refreshAuthoritativeRequestTimeouts(long nowMillis) {
        boolean changed = false;
        ClientResearchGuidanceState.TimeoutOutcome guidanceTimeout =
                ClientResearchGuidanceState.expirePending(
                        nowMillis, GUIDANCE_RESPONSE_TIMEOUT_MILLIS);
        switch (guidanceTimeout) {
            case NONE -> {
            }
            case RETRY -> {
                refreshResearchPlan();
                treeCanvas.setTrackedPlan(researchPlan.orElse(null));
                changed = true;
            }
            case UNAVAILABLE -> {
                refreshResearchPlan(false);
                treeCanvas.setTrackedPlan(researchPlan.orElse(null));
                changed = true;
            }
        }

        ClientResearchAffordabilityState.ResponseOutcome affordabilityTimeout =
                ClientResearchAffordabilityState.expirePending(
                        nowMillis, AFFORDABILITY_RESPONSE_TIMEOUT_MILLIS);
        if (affordabilityTimeout
                != ClientResearchAffordabilityState.ResponseOutcome.IGNORED) {
            requestNextAffordabilityBatch();
            applyAffordabilityFilterToCanvas();
            changed = true;
        }
        if (changed) {
            uiUpdates.invalidateWidgets();
        }
    }

    private void updateFullscreenHoldActivation(long nowMillis) {
        ResearchTreeHoldActivationController.Snapshot hold =
                fullscreenHoldActivation.snapshot(nowMillis);
        if (hold.status() != ResearchTreeHoldActivationController.Status.HOLDING) {
            return;
        }
        ResourceLocation blueprintId = hold.blueprintId().orElseThrow();
        if (!canHoldToResearch(blueprintId)) {
            fullscreenHoldActivation.cancel();
            return;
        }
        if (fullscreenHoldActivation.advance(nowMillis)
                == ResearchTreeHoldActivationController.Outcome.ACTIVATE) {
            requestResearch();
        }
    }

    private void updateFullscreenOverlayLifecycle() {
        if (!fullscreen || fullscreenOverlayLayout == null) {
            railIdleTicks = 0;
            return;
        }
        if (getFocused() == searchBox) {
            fullscreenOverlayState.focusSearch();
        } else {
            fullscreenOverlayState.blurSearch();
        }
        boolean pointerOverRail = pointerOverFullscreenRail(lastMouseX, lastMouseY);
        boolean railHasFocus = railHasKeyboardFocus();
        boolean activeQuery = searchBox != null && !searchBox.getValue().isBlank();
        if (pointerOverRail || railHasFocus || activeQuery
                || !fullscreenOverlayState.canAutoCollapse(pointerOverRail, railHasFocus)) {
            railIdleTicks = 0;
            return;
        }
        railIdleTicks++;
        if (railIdleTicks >= RAIL_COLLAPSE_DELAY_TICKS
                && fullscreenOverlayState.autoCollapse(false, false)) {
            railIdleTicks = 0;
        }
    }

    private void reloadResearchTree(boolean initial) {
        saveActiveCamera();
        ClientResearchState.Publication publication = ClientResearchState.publication();
        ResearchTreeLayoutPolicy requestedLayoutPolicy =
                ModConfigs.RESEARCH_TREE_CLIENT.layoutPolicy();
        ResourceLocation previousFocus = treeCanvas.focusedId().orElse(null);

        Map<ResourceLocation, BlueprintJournalEntry> nextEntries = new LinkedHashMap<>();
        for (BlueprintJournalEntry entry : publication.journal().entries()) {
            entry.blueprintId().ifPresent(id -> nextEntries.put(id, entry));
        }
        Map<ResourceLocation, ItemStack> nextIcons = new LinkedHashMap<>();
        for (ResearchTreeGraph.Node node : publication.graph().nodes()) {
            if (node.visibility().revealsIcon()) {
                nextIcons.put(
                        node.blueprintId(),
                        BlueprintItem.createBlueprint(node.blueprintId().toString()));
            }
        }
        ResearchTreePublication nextTreePublication = new ResearchTreePublication(
                publication.graph(),
                publication.presentation(),
                publication.techTree());
        boolean projectionGeometryChanged;
        try {
            ResearchTreeProjectionCache.UpdateOutcome update =
                    treeProjections.updateWithBalancedFallback(
                            nextTreePublication,
                            requestedLayoutPolicy);
            projectionGeometryChanged = update.geometryChanged();
            if (update.usedBalancedFallback()) {
                TaCZWeaponBlueprints.LOGGER.warn(
                        "Unable to apply the requested Research Tree layout; using Balanced layout",
                        update.recoveredLayoutFailure().orElseThrow());
            }
        } catch (RuntimeException exception) {
            // The projection cache prepares all topology objects before it commits anything.
            // Keep the last valid graph visible and remember this rejected publication so the
            // render thread does not retry the same expensive failure every client tick.
            researchPublicationIdentity = publication;
            researchLayoutPolicyIdentity = requestedLayoutPolicy;
            researchTreePublicationRejected = true;
            ClientResearchGuidanceState.invalidateResources();
            ClientResearchAffordabilityState.setEnabled(
                    false,
                    treeProjections.publication().graph(),
                    publication.generation());
            researchPlan = Optional.empty();
            treeCanvas.setTrackedPlan(null);
            applyAffordabilityFilterToCanvas();
            guidanceRetryTicks = 0;
            affordabilityRetryTicks = 0;
            TaCZWeaponBlueprints.LOGGER.error(
                    "Rejected an invalid Research Tree publication; retaining the last valid tree",
                    exception);
            return;
        }
        researchTreePublicationRejected = false;

        // Publish screen-local state only after the projection cache has
        // prepared the matching overview and branch topology.
        researchPublicationIdentity = publication;
        researchLayoutPolicyIdentity = requestedLayoutPolicy;
        researchPoints = publication.journal().researchPoints();
        journalEntries = Map.copyOf(nextEntries);
        researchTreeIcons = Map.copyOf(nextIcons);
        refreshResearchPlan();
        if (projectionGeometryChanged) {
            cameraStates.clear();
            activeCameraKey = null;
        }
        ResourceLocation preferredFocus = previousFocus != null
                ? previousFocus
                : menu.selectedBlueprint().orElse(null);
        treeNavigation.setBrowseView(
                ResearchTreePresentationContract.retainPlayerBrowseView(
                        treeNavigation.browseView()),
                publication.presentation());
        techTreeNavigation.retain(
                treeProjections.techTreeProjections(),
                treeProjections.techTreeLayouts(),
                preferredFocus);
        boolean topologyChanged = applyActiveProjection(preferredFocus);
        if (!treeCanvas.graph().nodes().isEmpty()) {
            if (initial) {
                treeCanvas.fit();
                focusUsefulNode();
            } else if (topologyChanged) {
                treeCanvas.focusedId().ifPresent(treeCanvas::focusNode);
            }
        }
        ClientResearchAffordabilityState.retain(
                treeProjections.publication().graph(), publication.generation());
        applyAffordabilityFilterToCanvas();
        requestNextAffordabilityBatch();
    }

    private boolean applyActiveProjection(ResourceLocation preferredFocus) {
        cancelTreeInteraction();
        // A resize rebuild can run after an empty/replacement publication has
        // invalidated the previously selected branch. Revalidate at the final
        // projection boundary as well as during publication reload so a stale
        // client-only group can never escape into the strict projection cache.
        treeNavigation.retain(
                treeProjections.publication().presentation(), preferredFocus);
        if (isTechTreeView()) {
            return applyActiveTechTreeProjection(preferredFocus);
        }
        ResearchTreeProjection projection = treeProjections.projection(
                treeNavigation.browseView(),
                treeNavigation.selectedGroupId().orElse(null));
        Map<ResourceLocation, ItemStack> projectedIcons = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, ItemStack> entry : researchTreeIcons.entrySet()) {
            if (projection.graph().node(entry.getKey()).isPresent()) {
                projectedIcons.put(entry.getKey(), entry.getValue());
            }
        }
        treeCanvas.setUnifiedOverview(
                projection.view()
                        == ResearchTreePresentationContract.BrowseView.ALL_WEAPONS);
        treeCanvas.setEdgeRoutingProfile(
                projection.view() == ResearchTreePresentationContract.BrowseView.ALL_WEAPONS
                        ? ResearchTreeEdgeIndex.RoutingProfile.UNIFIED_OVERVIEW
                        : ResearchTreeEdgeIndex.RoutingProfile.LOCAL_BRANCH);
        boolean topologyChanged = treeCanvas.setContent(
                projection.graph(),
                projection.layout(),
                projectedIcons,
                preferredFocus,
                menu.selectedBlueprint().orElse(null),
                projection.crossGroupLinks());
        treeCanvas.setTrackedPlan(researchPlan.orElse(null));
        applyAffordabilityFilterToCanvas();
        projectionRevision++;
        fullscreenOverlayState.retainVisibleNodes(projection.graph().nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        ResearchTreeCameraStore.Key nextCameraKey = cameraKey();
        lastProjectionCameraRestored = cameraStates.restore(
                nextCameraKey, treeCanvas.viewport());
        if (!lastProjectionCameraRestored) {
            treeCanvas.fit();
        }
        activeCameraKey = nextCameraKey;
        updateVisibleSearchMatches();
        return topologyChanged;
    }

    private boolean applyActiveTechTreeProjection(ResourceLocation preferredFocus) {
        ResearchTechTreeProjectionCatalog catalog = treeProjections.techTreeProjections();
        if (!catalog.available()) {
            treeCanvas.setUnifiedOverview(true);
            treeCanvas.setEdgeRoutingProfile(
                    ResearchTreeEdgeIndex.RoutingProfile.UNIFIED_OVERVIEW);
            boolean topologyChanged = treeCanvas.setContent(
                    ResearchTreeGraph.EMPTY,
                    ResearchTreeLayout.EMPTY,
                    Map.of(),
                    null,
                    null);
            treeCanvas.setTrackedPlan(null);
            applyAffordabilityFilterToCanvas();
            projectionRevision++;
            fullscreenOverlayState.retainVisibleNodes(Set.of());
            fullscreenOverlayState.clearPinnedNode();
            lastProjectionCameraRestored = false;
            activeCameraKey = null;
            updateVisibleSearchMatches();
            return topologyChanged;
        }
        if (preferredFocus != null) {
            catalog.domainOf(preferredFocus).ifPresent(domain ->
                    techTreeNavigation.selectNode(preferredFocus, catalog));
        }
        Domain domain = techTreeNavigation.selectedDomain().orElseThrow();
        ResearchTechTreeProjection projection = catalog.projection(domain).orElseThrow();
        ResearchTechTreeLayout techLayout = treeProjections.techTreeLayout(
                domain, treeCanvas.bounds().width()).orElseThrow();
        Map<ResourceLocation, ItemStack> projectedIcons = projectedIcons(projection.graph());
        ResourceLocation domainFocus = techTreeNavigation.focusedNode().orElse(preferredFocus);

        treeCanvas.setUnifiedOverview(true);
        treeCanvas.setEdgeRoutingProfile(
                ResearchTreeEdgeIndex.RoutingProfile.UNIFIED_OVERVIEW);
        boolean topologyChanged = treeCanvas.setTechContent(
                projection,
                techLayout,
                projectedIcons,
                domainFocus,
                menu.selectedBlueprint().orElse(null));
        treeCanvas.setTrackedPlan(researchPlan.orElse(null));
        applyAffordabilityFilterToCanvas();
        projectionRevision++;
        fullscreenOverlayState.retainVisibleNodes(projection.graph().nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        techTreeNavigation.pinnedNode().ifPresentOrElse(
                fullscreenOverlayState::pinNode,
                fullscreenOverlayState::clearPinnedNode);
        Optional<ResearchTreeViewport.Snapshot> camera =
                techTreeNavigation.camera(activeTechTreeSurface(), techLayout);
        lastProjectionCameraRestored = camera.isPresent();
        if (camera.isPresent()) {
            treeCanvas.viewport().restore(camera.orElseThrow());
        } else {
            treeCanvas.fit();
        }
        activeCameraKey = null;
        updateVisibleSearchMatches();
        return topologyChanged;
    }

    private Map<ResourceLocation, ItemStack> projectedIcons(ResearchTreeGraph graph) {
        Map<ResourceLocation, ItemStack> projected = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, ItemStack> entry : researchTreeIcons.entrySet()) {
            if (graph.node(entry.getKey()).isPresent()) {
                projected.put(entry.getKey(), entry.getValue());
            }
        }
        return projected;
    }

    private void applyAffordabilityFilterToCanvas() {
        ClientResearchAffordabilityState.Snapshot affordability =
                ClientResearchAffordabilityState.snapshot();
        Map<ResourceLocation, com.gamergaming.taczweaponblueprints.progression
                .ResearchAffordabilitySnapshot.Entry> visible = new LinkedHashMap<>();
        affordability.results().forEach((id, result) -> {
            if (treeCanvas.graph().node(id).isPresent()) {
                visible.put(id, result);
            }
        });
        treeCanvas.setAffordabilityFilter(affordability.enabled(), visible);
    }

    private ResearchTreeCameraStore.Key cameraKey() {
        if (isTechTreeView()) {
            throw new IllegalStateException(
                    "Tech Tree cameras require a domain identity");
        }
        Optional<ResourceLocation> groupId = treeNavigation.browseView()
                == ResearchTreePresentationContract.BrowseView.BRANCHES
                        ? treeNavigation.selectedGroupId()
                        : Optional.empty();
        return new ResearchTreeCameraStore.Key(
                activeTreeLayout.mode(), treeNavigation.browseView(), groupId);
    }

    private void saveActiveCamera() {
        if (isTechTreeView() && techTreeNavigation.selectedDomain().isPresent()) {
            ResearchTechTreeProjectionCatalog catalog = treeProjections.techTreeProjections();
            treeCanvas.focusedId().ifPresent(id -> techTreeNavigation.focus(id, catalog));
            fullscreenOverlayState.pinnedNodeId().ifPresentOrElse(
                    id -> techTreeNavigation.pin(id, catalog),
                    techTreeNavigation::clearPin);
            techTreeNavigation.setSearch(
                    searchBox == null ? "" : searchBox.getValue(),
                    treeSearch.activeMatch()
                            .filter(id -> catalog.domainOf(id)
                                    .filter(techTreeNavigation.selectedDomain()
                                            .orElseThrow()::equals)
                                    .isPresent())
                            .orElse(null),
                    catalog);
            techTreeNavigation.saveCamera(
                    activeTechTreeSurface(),
                    treeProjections.techTreeLayout(
                            techTreeNavigation.selectedDomain().orElseThrow(),
                            treeCanvas.bounds().width()).orElseThrow(),
                    treeCanvas.viewport().snapshot());
            return;
        }
        if (activeCameraKey != null) {
            cameraStates.save(activeCameraKey, treeCanvas.viewport());
        }
    }

    private ResearchTechTreeViewState.Surface activeTechTreeSurface() {
        return activeTreeLayout.mode() == ResearchTreeScreenLayout.ViewMode.FULLSCREEN
                ? ResearchTechTreeViewState.Surface.FULLSCREEN
                : ResearchTechTreeViewState.Surface.COMPACT;
    }

    private boolean isTechTreeView() {
        return treeNavigation.browseView()
                == ResearchTreePresentationContract.BrowseView.TECH_TREE;
    }

    private boolean techTreeAvailable() {
        return treeProjections.techTreeProjections().available();
    }

    private ResearchTechTreeDomainMenu techTreeDomainMenu() {
        return ResearchTechTreeDomainMenu.create(
                treeProjections.techTreeProjections(), techTreeNavigation);
    }

    private void updateVisibleSearchMatches() {
        treeCanvas.setSearchMatches(treeSearch.visibleMatches(treeCanvas.graph()));
        treeCanvas.setActiveSearchMatch(treeSearch.activeMatch()
                .filter(id -> treeCanvas.graph().node(id).isPresent())
                .orElse(null));
    }

    private void applyTreeSearch() {
        treeSearch.update(
                searchBox == null ? "" : searchBox.getValue(),
                treeProjections.publication().graph().nodes().stream()
                        .filter(this::isSearchNavigable)
                        .toList(),
                this::searchableText);
        updateVisibleSearchMatches();
        updateWidgets();
    }

    private void navigateToPublicNode(ResourceLocation blueprintId, boolean center) {
        if (blueprintId == null
                || treeProjections.publication().graph().node(blueprintId).isEmpty()) {
            return;
        }
        Optional<Domain> targetDomain = treeProjections.techTreeProjections().domainOf(blueprintId);
        boolean hasLegacyMembership = treeProjections.publication().presentation()
                .membership(blueprintId)
                .isPresent();
        if (targetDomain.isPresent() && (isTechTreeView() || !hasLegacyMembership)) {
            boolean projectionChanged = !isTechTreeView()
                    || techTreeNavigation.selectedDomain()
                    .filter(targetDomain.orElseThrow()::equals)
                    .isEmpty();
            if (projectionChanged) {
                saveActiveCamera();
                treeNavigation.setBrowseView(
                        ResearchTreePresentationContract.BrowseView.TECH_TREE,
                        treeProjections.publication().presentation());
                techTreeNavigation.selectNode(
                        blueprintId, treeProjections.techTreeProjections());
                techTreeNavigation.setSearch(
                        searchBox == null ? "" : searchBox.getValue(),
                        blueprintId,
                        treeProjections.techTreeProjections());
                applyActiveProjection(blueprintId);
                ensureSelectedSidebarVisible();
            } else {
                techTreeNavigation.focus(
                        blueprintId, treeProjections.techTreeProjections());
            }
            updateVisibleSearchMatches();
            if (center) {
                treeCanvas.focusNode(blueprintId);
            } else {
                treeCanvas.setFocusedNode(blueprintId);
            }
            return;
        }
        if (!ResearchTreePresentationContract.legacyBrowseViewsVisible()) {
            return;
        }
        Optional<ResearchTreePresentation.Membership> targetMembership =
                treeProjections.publication().presentation().membership(blueprintId);
        if (targetMembership.isEmpty()) {
            return;
        }
        ResourceLocation targetGroup = targetMembership.orElseThrow().groupId();
        boolean visibleInActiveProjection = treeCanvas.graph().node(blueprintId).isPresent();
        boolean branchView = treeNavigation.browseView()
                == ResearchTreePresentationContract.BrowseView.BRANCHES;
        boolean projectionChanged = false;
        if (branchView) {
            if (treeNavigation.selectedGroupId().filter(targetGroup::equals).isEmpty()) {
                saveActiveCamera();
                treeNavigation.selectGroup(
                        targetGroup,
                        treeProjections.publication().presentation());
                applyActiveProjection(blueprintId);
                projectionChanged = true;
            }
        } else if (!visibleInActiveProjection) {
            saveActiveCamera();
            treeNavigation.setBrowseView(
                    ResearchTreePresentationContract.BrowseView.BRANCHES,
                    treeProjections.publication().presentation());
            treeNavigation.selectGroup(
                    targetGroup,
                    treeProjections.publication().presentation());
            applyActiveProjection(blueprintId);
            projectionChanged = true;
        }
        if (projectionChanged) {
            ensureSelectedSidebarVisible();
        }
        updateVisibleSearchMatches();
        if (center) {
            treeCanvas.focusNode(blueprintId);
        } else {
            treeCanvas.setFocusedNode(blueprintId);
        }
    }

    private boolean isSearchNavigable(ResearchTreeGraph.Node node) {
        if (node == null) {
            return false;
        }
        if (treeProjections.techTreeProjections().domainOf(node.blueprintId()).isPresent()) {
            return true;
        }
        return ResearchTreePresentationContract.legacyBrowseViewsVisible()
                && treeProjections.publication().presentation()
                        .membership(node.blueprintId()).isPresent();
    }

    private void refreshResearchPlan() {
        refreshResearchPlan(true);
    }

    private void refreshResearchPlan(boolean requestGuidance) {
        ResearchTreeGraph graph = treeProjections.publication().graph();
        ClientResearchPlannerState.retain(graph);
        ClientResearchState.Publication publication = ClientResearchState.publication();
        ClientResearchGuidanceState.retain(graph, publication.generation());
        researchPlan = ClientResearchPlannerState.targetId().flatMap(target ->
                ResearchTreePlanner.presentationPlan(
                        graph,
                        target,
                        researchPoints,
                        ClientResearchGuidanceState.snapshot(),
                        ClientResearchGuidanceState.unavailable()));
        if (!requestGuidance) {
            return;
        }
        if (!menu.routeGuidanceAvailable()) {
            ClientResearchGuidanceState.abandonPending();
            return;
        }
        ClientResearchPlannerState.targetId().ifPresent(target ->
                ClientResearchGuidanceState.begin(
                                graph, target, publication.generation())
                        .ifPresent(request -> NetworkHandler.INSTANCE.sendToServer(
                                new ResearchGuidanceRequestPacket(
                                        menu.containerId,
                                        request.requestId(),
                                        request.publicationGeneration(),
                                request.targetId()))));
    }

    private void synchronizeRouteGuidanceAvailability() {
        boolean available = menu.routeGuidanceAvailable();
        if (routeGuidanceAvailabilityIdentity != null
                && routeGuidanceAvailabilityIdentity == available) {
            return;
        }
        routeGuidanceAvailabilityIdentity = available;
        if (!available) {
            ClientResearchGuidanceState.clear();
            ClientResearchAffordabilityState.setEnabled(
                    false,
                    treeProjections.publication().graph(),
                    ClientResearchState.publication().generation());
            guidanceRetryTicks = 0;
            affordabilityRetryTicks = 0;
            refreshResearchPlan(false);
            treeCanvas.setTrackedPlan(researchPlan.orElse(null));
            applyAffordabilityFilterToCanvas();
        } else {
            refreshResearchPlan();
        }
        uiUpdates.invalidateWidgets();
    }

    private void refreshGuidanceAfterInventoryChange() {
        List<InventoryEntry> latest = captureGuidanceInventory(playerInventory);
        if (!latest.equals(guidanceInventoryIdentity)) {
            guidanceInventoryIdentity = latest;
            boolean guidanceActive = ClientResearchPlannerState.targetId().isPresent();
            boolean affordabilityActive =
                    ClientResearchAffordabilityState.snapshot().enabled();
            if (guidanceActive || affordabilityActive) {
                guidanceInventoryDebounceTicks = GUIDANCE_INVENTORY_DEBOUNCE_TICKS;
                if (guidanceActive) {
                    ClientResearchGuidanceState.invalidateResources();
                    refreshResearchPlan(false);
                    treeCanvas.setTrackedPlan(researchPlan.orElse(null));
                }
                if (affordabilityActive) {
                    ClientResearchState.Publication publication =
                            ClientResearchState.publication();
                    ClientResearchAffordabilityState.invalidateResources(
                            treeProjections.publication().graph(),
                            publication.generation());
                    affordabilityRetryTicks = 0;
                    applyAffordabilityFilterToCanvas();
                }
                uiUpdates.invalidateWidgets();
            }
            return;
        }
        if (guidanceInventoryDebounceTicks <= 0
                || ClientResearchPlannerState.targetId().isEmpty()
                        && !ClientResearchAffordabilityState.snapshot().enabled()) {
            guidanceInventoryDebounceTicks = 0;
            return;
        }
        guidanceInventoryDebounceTicks--;
        if (guidanceInventoryDebounceTicks == 0) {
            if (ClientResearchPlannerState.targetId().isPresent()) {
                refreshResearchPlan();
            }
            requestNextAffordabilityBatch();
            uiUpdates.invalidateWidgets();
        }
    }

    private static List<InventoryEntry> captureGuidanceInventory(Inventory inventory) {
        if (inventory == null) {
            return List.of();
        }
        return inventory.items.stream()
                .map(stack -> stack.isEmpty()
                        ? InventoryEntry.EMPTY
                        : new InventoryEntry(stack.getItem(), stack.getCount()))
                .toList();
    }

    private void refreshGuidanceAfterThrottle() {
        if (guidanceRetryTicks <= 0) {
            return;
        }
        if (ClientResearchPlannerState.targetId().isEmpty()) {
            guidanceRetryTicks = 0;
            return;
        }
        if (guidanceInventoryDebounceTicks > 0) {
            return;
        }
        guidanceRetryTicks--;
        if (guidanceRetryTicks == 0) {
            refreshResearchPlan();
            uiUpdates.invalidateWidgets();
        }
    }

    /** Retries a server-throttled read after the limiter's one-second window. */
    public void scheduleAuthoritativeGuidanceRetry() {
        guidanceRetryTicks = Math.max(
                guidanceRetryTicks,
                GUIDANCE_RETRY_TICKS);
        uiUpdates.invalidateWidgets();
    }

    private void refreshAffordabilityAfterThrottle() {
        if (affordabilityRetryTicks <= 0) {
            return;
        }
        if (!ClientResearchAffordabilityState.snapshot().enabled()) {
            affordabilityRetryTicks = 0;
            return;
        }
        if (guidanceInventoryDebounceTicks > 0) {
            return;
        }
        affordabilityRetryTicks--;
        if (affordabilityRetryTicks == 0) {
            requestNextAffordabilityBatch();
            uiUpdates.invalidateWidgets();
        }
    }

    public void scheduleAffordabilityRetry() {
        affordabilityRetryTicks = Math.max(
                affordabilityRetryTicks, AFFORDABILITY_RETRY_TICKS);
        applyAffordabilityFilterToCanvas();
        uiUpdates.invalidateWidgets();
    }

    public void refreshAuthoritativeAffordability() {
        affordabilityRetryTicks = 0;
        applyAffordabilityFilterToCanvas();
        requestNextAffordabilityBatch();
        uiUpdates.invalidateWidgets();
    }

    private void requestNextAffordabilityBatch() {
        if (!menu.routeGuidanceAvailable() || researchTreePublicationRejected) {
            return;
        }
        ClientResearchState.Publication publication = ClientResearchState.publication();
        ClientResearchAffordabilityState.beginNext(
                        treeProjections.publication().graph(), publication.generation())
                .ifPresent(request -> NetworkHandler.INSTANCE.sendToServer(
                        new ResearchAffordabilityRequestPacket(
                                menu.containerId,
                                request.requestId(),
                                request.publicationGeneration(),
                                request.targetIds())));
    }

    /** Applies a correlated response without waiting for another tree publication. */
    public void refreshAuthoritativeGuidance() {
        guidanceRetryTicks = 0;
        refreshResearchPlan();
        treeCanvas.setTrackedPlan(researchPlan.orElse(null));
        fullscreenCardWidgetState = null;
        updateWidgets();
    }

    private String searchableText(ResearchTreeGraph.Node node) {
        if (!node.visibility().revealsName()) {
            return "";
        }
        StringBuilder searchable = new StringBuilder(
                nodeName(node).getString().toLowerCase(Locale.ROOT));
        if (node.visibility().revealsIdentity()) {
            searchable.append(' ').append(node.blueprintId().toString().toLowerCase(Locale.ROOT));
            searchable.append(' ').append(node.itemType().toLowerCase(Locale.ROOT));
        }
        return searchable.toString();
    }

    private void updateWidgets() {
        if (searchBox == null) {
            return;
        }
        ResearchSelectionPreview preview = menu.preview();
        treeCanvas.setProgressionMarker(
                preview.blueprintId().orElse(null),
                preview.progression().fragments()
                        .map(progress -> progress.archived() > 0)
                        .orElse(false),
                preview.accessSummary().blocked());
        if (uiUpdates.shouldRefreshWidgets(widgetSnapshot())) {
            // Static refresh resets fullscreen-owned widgets to their neutral
            // state, so the contextual widget cache must re-apply its state.
            fullscreenCardWidgetState = null;
            refreshStaticWidgets();
            updateTreeSafeInsets();
        }
        refreshMinimap();
        updateFullscreenContextCardWidgets();
        clearFocusIfHidden();
    }

    /** Prevents an overlay transition from leaving keyboard input on an invisible child. */
    private void clearFocusIfHidden() {
        if (getFocused() instanceof AbstractWidget widget && !widget.visible) {
            setFocused(null);
        }
    }

    private void refreshStaticWidgets() {
        boolean railVisible = !fullscreen || fullscreenOverlayState.railState()
                != ResearchTreeFullscreenOverlayState.RailState.EDGE_HANDLE;
        boolean fullscreenSearchVisible = fullscreen
                && fullscreenOverlayState.searchState()
                        != ResearchTreeFullscreenOverlayState.SearchState.CLOSED;
        searchBox.visible = !fullscreen || fullscreenSearchVisible;
        searchBox.setEditable(searchBox.visible);
        searchToggleButton.visible = fullscreen && railVisible;
        searchToggleButton.active = searchToggleButton.visible;
        if (!searchBox.visible && getFocused() == searchBox) {
            setFocused(null);
        }
        browseViewButton.visible = !fullscreen
                && ResearchTreePresentationContract.browseViewSelectorVisible();
        groupButton.visible = !fullscreen && !isTechTreeView();
        updateTechTreeDomainButtons(true);
        fitButton.visible = railVisible;
        zoomOutButton.visible = railVisible;
        zoomInButton.visible = railVisible;
        fullscreenButton.visible = true;
        guidanceDismissButton.visible = guidanceVisible;
        recommendationButton.visible = !guidanceVisible
                && (!fullscreen || railVisible);
        affordabilityButton.visible = fullscreen && !guidanceVisible && railVisible
                && menu.routeGuidanceAvailable() && !researchTreePublicationRejected;
        affordabilityButton.active = affordabilityButton.visible;
        helpButton.visible = !guidanceVisible && (!fullscreen || railVisible);
        railPinButton.visible = fullscreen && railVisible;
        railPinButton.active = railPinButton.visible;
        primaryResearchButton.visible = !fullscreen;
        returnToSelectionButton.visible = false;
        trackResearchButton.visible = !fullscreen && !guidanceVisible;
        Optional<ResourceLocation> trackedGoal = ClientResearchPlannerState.targetId();
        researchGoalButton.visible = fullscreen && !guidanceVisible && trackedGoal.isPresent()
                && menu.routeGuidanceAvailable() && !researchTreePublicationRejected;
        researchGoalButton.active = researchGoalButton.visible;
        if (researchGoalButton.visible) {
            ResearchGoalProgressPresenter.Presentation goalProgress = researchGoalProgress();
            Component goalName = treeProjections.publication().graph()
                    .node(trackedGoal.orElseThrow())
                    .map(this::nodeName)
                    .orElse(Component.literal("?"));
            researchGoalButton.setMessage(researchGoalButtonLabel(
                    goalName,
                    researchGoalButtonStatus(goalProgress)));
            researchGoalButton.setTooltip(Tooltip.create(
                    researchGoalDescription(goalName, goalProgress)));
        } else {
            researchGoalButton.setTooltip(null);
        }

        boolean hasGroups = !treeProjections.publication().presentation().groups().isEmpty();
        boolean hasSidebarItems = sidebarItemCount() > 0;
        browseViewButton.active = browseViewButton.visible
                && (hasGroups || techTreeAvailable());
        groupButton.active = groupButton.visible && hasSidebarItems;
        browseViewButton.setMessage(browseViewShortName());
        browseViewButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.view.tooltip",
                currentBrowseViewName())));
        Component groupName = currentGroupName();
        groupButton.setMessage(clipped(groupName, activeTreeLayout.groupSelector().width() - 6));
        groupButton.setTooltip(Tooltip.create(Component.translatable(
                isTechTreeView()
                        ? "gui.taczweaponblueprints.research_bench.tree.domain.tooltip"
                        : "gui.taczweaponblueprints.research_bench.tree.group.tooltip",
                groupName)));
        fitButton.active = !treeCanvas.graph().nodes().isEmpty();
        zoomOutButton.active = !treeCanvas.graph().nodes().isEmpty()
                && treeCanvas.viewport().scale() > ResearchTreeViewport.MIN_SCALE;
        zoomInButton.active = !treeCanvas.graph().nodes().isEmpty()
                && treeCanvas.viewport().scale() < ResearchTreeViewport.MAX_SCALE;
        fullscreenButton.active = true;
        Optional<ResearchTreeRecommendationEngine.Recommendation> recommendation =
                recommendedNextBlueprint();
        recommendationButton.active = recommendationButton.visible
                && recommendation.isPresent();
        recommendationButton.setMessage(fullscreen
                ? Component.literal("→")
                : Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.recommendation.button"));
        recommendationButton.setTooltip(Tooltip.create(
                recommendation.map(this::recommendationDescription)
                        .orElseGet(() -> Component.translatable(
                                "gui.taczweaponblueprints.research_bench.tree.recommendation.none"))));
        ClientResearchAffordabilityState.Snapshot affordability =
                ClientResearchAffordabilityState.snapshot();
        affordabilityButton.setMessage(Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.affordable.button."
                        + (affordability.enabled() ? "on" : "off")));
        affordabilityButton.setTooltip(Tooltip.create(affordabilityDescription(affordability)));
        Optional<ResearchTreeGraph.Node> focusedForTracking = focusedTreeNode()
                .filter(node -> node.visibility().revealsIdentity());
        boolean focusedIsTracked = focusedForTracking
                .map(ResearchTreeGraph.Node::blueprintId)
                .flatMap(id -> ClientResearchPlannerState.targetId()
                        .filter(id::equals))
                .isPresent();
        trackResearchButton.active = trackResearchButton.visible
                && focusedForTracking.isPresent();
        trackResearchButton.setMessage(Component.translatable(focusedIsTracked
                ? "gui.taczweaponblueprints.research_bench.tree.plan.untrack"
                : "gui.taczweaponblueprints.research_bench.tree.plan.track"));
        trackResearchButton.setTooltip(Tooltip.create(trackResearchDescription(
                focusedForTracking.map(ResearchTreeGraph.Node::blueprintId).orElse(null))));
        if (fullscreen) {
            boolean railPinned = fullscreenOverlayState.railState()
                    == ResearchTreeFullscreenOverlayState.RailState.PINNED;
            railPinButton.setMessage(Component.literal(railPinned ? "●" : "○"));
            railPinButton.setTooltip(Tooltip.create(Component.translatable(railPinned
                    ? "gui.taczweaponblueprints.research_bench.tree.rail.unpin"
                    : "gui.taczweaponblueprints.research_bench.tree.rail.pin")));
            String query = searchBox.getValue().strip();
            Component searchMessage = query.isEmpty()
                    ? Component.literal("⌕")
                    : Component.literal(treeSearch.matches().size() > 9
                            ? "9+"
                            : Integer.toString(treeSearch.matches().size()));
            searchToggleButton.setMessage(searchMessage);
            searchToggleButton.setTooltip(Tooltip.create(searchToggleNarration()));
        }
        if (!fullscreen) {
            Optional<SelectedNodeUi> selected = focusedTreeNode().map(node ->
                    selectedNodeUi(node, selectedNodePresentation(node)));
            primaryResearchButton.setMessage(focusedTreeNode()
                    .map(this::selectedNodePresentation)
                    .map(this::researchActionLabel)
                    .orElseGet(() -> Component.translatable(
                            "gui.taczweaponblueprints.research_bench.research")));
            primaryResearchButton.active = selected
                    .filter(SelectedNodeUi::actionEnabled)
                    .isPresent();
            primaryResearchButton.setTooltip(selected
                    .map(SelectedNodeUi::message)
                    .map(Tooltip::create)
                    .orElse(null));
        }
        relationButtons.forEach(button -> button.refresh(true));
        updateSidebarButtons(true);
        updateSearchResultButtons(true);
        if (getFocused() instanceof RelationCardButton relationButton
                && !relationButton.visible) {
            setFocused(null);
        }
    }

    private void updateTechTreeDomainButtons(boolean browseMode) {
        ResearchTechTreeDomainMenu menu = techTreeDomainMenu();
        for (TechTreeDomainButton button : techTreeDomainButtons) {
            ResearchTechTreeDomainMenu.Entry entry = menu.entry(button.domain());
            button.visible = browseMode && !fullscreen && isTechTreeView();
            button.active = button.visible && entry.available();
            button.refresh(
                    techTreeDomainName(button.domain()),
                    entry.visibleBlueprintCount(),
                    entry.selected(),
                    techTreeDomainIcon(entry));
        }
    }

    private WidgetSnapshot widgetSnapshot() {
        return new WidgetSnapshot(
                fullscreen,
                fullscreenOverlayState.snapshot(),
                guidanceVisible,
                treeSearch.query(),
                treeSearch.matches().size(),
                treeSearch.activeMatch(),
                treeNavigation.browseView(),
                treeNavigation.selectedGroupId(),
                techTreeNavigation.selectedDomain(),
                researchTreePublicationRejected,
                researchPoints,
                researchPlan,
                ClientResearchGuidanceState.currentSnapshot(),
                ClientResearchGuidanceState.pending(),
                ClientResearchGuidanceState.unavailable(),
                menu.routeGuidanceAvailable(),
                ClientResearchAffordabilityState.snapshot(),
                projectionRevision,
                sidebarScroll,
                treeCanvas.viewport().scale(),
                treeCanvas.focusedId(),
                treeCanvas.authoritativeSelectedId(),
                menu.preview(),
                selectionFeedback.snapshot(),
                researchFeedback.snapshot(),
                width,
                height);
    }

    private void updateTreeSafeInsets() {
        if (!fullscreen || fullscreenOverlayLayout == null || primaryResearchButton == null) {
            treeCanvas.setSafeInsets(ResearchTreeViewport.Insets.NONE);
            return;
        }
        ResearchTreeScreenLayout.Rect safe = fullscreenOverlayLayout.safeFocus();
        int left = fullscreenOverlayState.railState()
                == ResearchTreeFullscreenOverlayState.RailState.EDGE_HANDLE
                        ? fullscreenOverlayLayout.edgeReveal().right() + 8
                        : safe.x();
        int top = safe.y();
        int right = width - safe.right();
        int bottom = height - safe.bottom();
        if (researchGoalButton != null && researchGoalButton.visible) {
            bottom = Math.max(
                    bottom,
                    height - fullscreenOverlayLayout.coachmark().y() + 4);
        }
        treeCanvas.setSafeInsets(new ResearchTreeViewport.Insets(left, top, right, bottom));
    }

    private void refreshMinimap() {
        treeMinimap.prepare(
                ModConfigs.RESEARCH_TREE_CLIENT.minimapMode(),
                treeCanvas,
                fullscreen,
                fullscreenMinimapObstacles());
    }

    private List<ResearchTreeScreenLayout.Rect> fullscreenMinimapObstacles() {
        if (!fullscreen || fullscreenOverlayLayout == null) {
            return List.of();
        }
        ArrayList<ResearchTreeScreenLayout.Rect> obstacles =
                new ArrayList<>(fullscreenContextObstacles());
        ResearchTreeScreenLayout.Rect existing = treeMinimap.panelBounds();
        if (existing != null) {
            obstacles.remove(existing);
        }
        return List.copyOf(obstacles);
    }

    private void updateFullscreenContextCardWidgets() {
        fullscreenContextCardLayout = null;
        if (primaryResearchButton == null || returnToSelectionButton == null
                || trackResearchButton == null
                || !fullscreen
                || fullscreenOverlayLayout == null) {
            hideFullscreenContextCardWidgets();
            return;
        }
        Optional<ResourceLocation> pinned = fullscreenOverlayState.pinnedNodeId();
        if (pinned.isEmpty() || treeCanvas.graph().node(pinned.orElseThrow()).isEmpty()) {
            hideFullscreenContextCardWidgets();
            return;
        }
        ResourceLocation pinnedId = pinned.orElseThrow();
        Optional<ResearchTreeContextCardLayout.Anchor> anchor = fullscreenNodeAnchor(pinnedId);
        if (anchor.isEmpty()) {
            hideFullscreenContextCardWidgets();
            return;
        }
        List<ResearchTreeScreenLayout.Rect> obstacles = fullscreenContextObstacles();
        ResearchTreeContextCardPresenter.Presentation presentation =
                contextCardPresenter.present(new ResearchTreeContextCardPresenter.Input(
                        width,
                        height,
                        pinnedId,
                        treeCanvas.authoritativeSelectedId(),
                        menu.preview(),
                        anchor.orElseThrow(),
                        obstacles));
        fullscreenContextCardLayout = presentation.layout();
        ResearchTreeSelectedNodePresenter.Presentation selected = treeCanvas.graph()
                .node(pinnedId)
                .map(this::selectedNodePresentation)
                .orElseThrow();
        SelectedNodeUi selectedUi = treeCanvas.graph()
                .node(pinnedId)
                .map(node -> selectedNodeUi(node, selected))
                .orElseThrow();
        boolean trackable = nodeIsTrackable(pinnedId);
        boolean tracked = ClientResearchPlannerState.targetId().filter(pinnedId::equals).isPresent();
        primaryResearchButton.setMessage(researchActionLabel(selected));

        FullscreenCardWidgetState nextState = new FullscreenCardWidgetState(
                fullscreenContextCardLayout,
                presentation.actionVisible(),
                presentation.returnActionVisible(),
                selectedUi.actionEnabled(),
                trackable,
                tracked,
                selectedUi.message());
        if (nextState.equals(fullscreenCardWidgetState)) {
            return;
        }
        fullscreenCardWidgetState = nextState;

        ResearchTreeScreenLayout.Rect action = fullscreenContextCardLayout.action();
        primaryResearchButton.visible = presentation.actionVisible();
        if (action != null) {
            primaryResearchButton.setX(action.x());
            primaryResearchButton.setY(action.y());
            primaryResearchButton.setWidth(action.width());
            primaryResearchButton.active = selectedUi.actionEnabled();
            primaryResearchButton.setTooltip(Tooltip.create(selectedUi.message()));
        } else {
            primaryResearchButton.active = false;
            primaryResearchButton.setTooltip(null);
        }

        returnToSelectionButton.visible = presentation.returnActionVisible();
        returnToSelectionButton.active = presentation.returnActionVisible();
        if (presentation.returnActionVisible()) {
            ResearchTreeScreenLayout.Rect returnAction = fullscreenContextCardLayout.returnAction();
            returnToSelectionButton.setX(returnAction.x());
            returnToSelectionButton.setY(returnAction.y());
            returnToSelectionButton.setWidth(returnAction.width());
        }
        ResearchTreeScreenLayout.Rect trackAction = fullscreenContextCardLayout.trackAction();
        trackResearchButton.visible = trackable;
        trackResearchButton.active = trackable;
        trackResearchButton.setX(trackAction.x());
        trackResearchButton.setY(trackAction.y());
        trackResearchButton.setWidth(trackAction.width());
        trackResearchButton.setMessage(Component.literal(tracked ? "◆" : "◇"));
        trackResearchButton.setTooltip(Tooltip.create(trackResearchDescription(pinnedId)));
    }

    private void hideFullscreenContextCardWidgets() {
        if (fullscreenCardWidgetState == null) {
            return;
        }
        fullscreenCardWidgetState = null;
        if (primaryResearchButton != null && fullscreen) {
            primaryResearchButton.visible = false;
        }
        if (returnToSelectionButton != null) {
            returnToSelectionButton.visible = false;
        }
        if (trackResearchButton != null && fullscreen) {
            trackResearchButton.visible = false;
        }
        clearFocusIfHidden();
    }

    private Optional<ResearchTreeContextCardLayout.Anchor> fullscreenNodeAnchor(
            ResourceLocation blueprintId) {
        return treeCanvas.layout().position(blueprintId).map(position -> {
            ResearchTreeViewport viewport = treeCanvas.viewport();
            ResearchTreeScreenLayout.Rect canvas = treeCanvas.bounds();
            int x = canvas.x() + viewport.viewportX(position.x());
            int y = canvas.y() + viewport.viewportY(position.y());
            int right = canvas.x() + viewport.viewportX(
                    position.x() + ResearchTreeLayout.NODE_WIDTH);
            int bottom = canvas.y() + viewport.viewportY(
                    position.y() + ResearchTreeLayout.NODE_HEIGHT);
            return new ResearchTreeContextCardLayout.Anchor(
                    x, y, Math.max(1, right - x), Math.max(1, bottom - y));
        });
    }

    private List<ResearchTreeScreenLayout.Rect> fullscreenContextObstacles() {
        if (fullscreenOverlayLayout == null) {
            return List.of();
        }
        ArrayList<ResearchTreeScreenLayout.Rect> obstacles = new ArrayList<>();
        obstacles.add(fullscreenOverlayState.railState()
                == ResearchTreeFullscreenOverlayState.RailState.EDGE_HANDLE
                        ? fullscreenOverlayLayout.edgeRevealHitTarget()
                        : fullscreenOverlayLayout.rail());
        sidebarButtons.stream()
                .map(RailEntryButton::persistentLabelObstacle)
                .flatMap(Optional::stream)
                .forEach(obstacles::add);
        obstacles.add(fullscreenOverlayLayout.close());
        fullscreenRejectedPublicationWarningBounds().ifPresent(obstacles::add);
        if (searchBox != null && searchBox.visible) {
            obstacles.add(fullscreenOverlayLayout.searchField());
        }
        if (affordabilityButton != null && affordabilityButton.visible) {
            obstacles.add(fullscreenRailLayout.affordability());
        }
        searchResultPanel().ifPresent(obstacles::add);
        if (guidanceVisible) {
            obstacles.add(activeGuidanceLayout().panel());
        } else if (researchGoalButton != null && researchGoalButton.visible) {
            obstacles.add(fullscreenOverlayLayout.coachmark());
        }
        if (treeMinimap.visible() && treeMinimap.panelBounds() != null) {
            obstacles.add(treeMinimap.panelBounds());
        }
        return List.copyOf(obstacles);
    }

    private Optional<ResearchTreeScreenLayout.Rect> searchResultPanel() {
        List<SearchResultButton> visibleResults = searchResultButtons.stream()
                .filter(button -> button.visible)
                .toList();
        if (visibleResults.isEmpty()) {
            return Optional.empty();
        }
        SearchResultButton first = visibleResults.get(0);
        SearchResultButton last = visibleResults.get(visibleResults.size() - 1);
        return Optional.of(new ResearchTreeScreenLayout.Rect(
                first.getX(),
                first.getY(),
                first.getWidth(),
                last.getY() + last.getHeight() - first.getY()));
    }

    private boolean searchOverlayContains(double mouseX, double mouseY) {
        return searchResultButtons.stream().anyMatch(button ->
                button.visible && button.isMouseOver(mouseX, mouseY));
    }

    private void returnToPinnedNode() {
        fullscreenOverlayState.pinnedNodeId().ifPresent(treeCanvas::focusNode);
        updateWidgets();
    }

    private void updateSidebarButtons(boolean browseMode) {
        if (sidebarButtons.isEmpty()) {
            return;
        }
        int groupCount = sidebarItemCount();
        int maximumScroll = ResearchTreeFullscreenRailLayout.maximumGroupScroll(
                sidebarButtons.size(),
                groupCount,
                ResearchTreePresentationContract.browseViewSelectorVisible());
        sidebarScroll = Math.max(0, Math.min(sidebarScroll, maximumScroll));
        for (int slot = 0; slot < sidebarButtons.size(); slot++) {
            RailEntryButton button = sidebarButtons.get(slot);
            int entryIndex = ResearchTreeFullscreenRailLayout.entryIndexForSlot(
                    slot,
                    sidebarButtons.size(),
                    sidebarScroll,
                    groupCount,
                    ResearchTreePresentationContract.browseViewSelectorVisible());
            boolean railVisible = fullscreenOverlayState.railState()
                    != ResearchTreeFullscreenOverlayState.RailState.EDGE_HANDLE;
            button.visible = browseMode && fullscreen && railVisible && entryIndex >= 0;
            if (!button.visible) {
                button.active = false;
                continue;
            }
            Component name = sidebarEntryName(entryIndex);
            boolean selected = sidebarEntrySelected(entryIndex);
            boolean selectable = sidebarEntrySelectable(entryIndex);
            button.active = selectable;
            button.refresh(
                    entryIndex,
                    name,
                    selected,
                    selectable,
                    sidebarEntryBlueprintCount(entryIndex));
        }
    }

    private void updateSearchResultButtons(boolean browseMode) {
        boolean resultsVisible = browseMode
                && !guidanceVisible
                && searchBox != null
                && searchBox.visible
                && !treeSearch.query().isEmpty()
                && !treeSearch.matches().isEmpty();
        List<ResearchTreeSearchController.Result> results = resultsVisible
                ? treeSearch.window(searchResultButtons.size())
                : List.of();
        for (int index = 0; index < searchResultButtons.size(); index++) {
            searchResultButtons.get(index).configure(
                    index < results.size() ? results.get(index) : null,
                    resultsVisible);
        }
        if (getFocused() instanceof SearchResultButton resultButton
                && !resultButton.visible) {
            setFocused(searchBox != null && searchBox.visible ? searchBox : null);
        }
    }

    private Component sidebarEntryName(int entryIndex) {
        if (entryIndex == 0) {
            return fullscreenViewActionName();
        }
        if (isTechTreeView()) {
            int domainIndex = entryIndex - 1;
            ResearchTechTreeDomainMenu menu = techTreeDomainMenu();
            return domainIndex >= 0 && domainIndex < menu.entries().size()
                    ? techTreeDomainName(menu.entryAt(domainIndex).domain())
                    : Component.empty();
        }
        List<ResearchTreePresentation.Group> groups =
                treeProjections.publication().presentation().groups();
        int groupIndex = entryIndex - 1;
        return groupIndex >= 0 && groupIndex < groups.size()
                ? groupName(groups.get(groupIndex))
                : Component.empty();
    }

    private ItemStack sidebarEntryIcon(int entryIndex) {
        if (entryIndex <= 0) {
            return ItemStack.EMPTY;
        }
        if (isTechTreeView()) {
            int domainIndex = entryIndex - 1;
            ResearchTechTreeDomainMenu menu = techTreeDomainMenu();
            if (domainIndex < 0 || domainIndex >= menu.entries().size()) {
                return ItemStack.EMPTY;
            }
            return techTreeDomainIcon(menu.entryAt(domainIndex));
        }
        List<ResearchTreePresentation.Group> groups =
                treeProjections.publication().presentation().groups();
        int groupIndex = entryIndex - 1;
        if (groupIndex < 0 || groupIndex >= groups.size()) {
            return ItemStack.EMPTY;
        }
        return groups.get(groupIndex).iconNodeId()
                .map(id -> researchTreeIcons.getOrDefault(id, ItemStack.EMPTY))
                .orElse(ItemStack.EMPTY);
    }

    private boolean sidebarEntrySelected(int entryIndex) {
        if (entryIndex == 0) {
            // This entry describes the destination view, rather than the active
            // projection, so presenting it as selected would invert its meaning.
            return false;
        }
        if (isTechTreeView()) {
            int domainIndex = entryIndex - 1;
            ResearchTechTreeDomainMenu menu = techTreeDomainMenu();
            return domainIndex >= 0 && domainIndex < menu.entries().size()
                    && menu.entryAt(domainIndex).selected();
        }
        List<ResearchTreePresentation.Group> groups =
                treeProjections.publication().presentation().groups();
        int groupIndex = entryIndex - 1;
        return groupIndex >= 0 && groupIndex < groups.size()
                && treeNavigation.browseView()
                        == ResearchTreePresentationContract.BrowseView.BRANCHES
                && treeNavigation.selectedGroupId().filter(groups.get(groupIndex).id()::equals).isPresent();
    }

    private boolean sidebarEntrySelectable(int entryIndex) {
        if (entryIndex == 0 || !isTechTreeView()) {
            return true;
        }
        int domainIndex = entryIndex - 1;
        ResearchTechTreeDomainMenu menu = techTreeDomainMenu();
        return domainIndex >= 0 && domainIndex < menu.entries().size()
                && menu.entryAt(domainIndex).available();
    }

    private int sidebarEntryBlueprintCount(int entryIndex) {
        if (entryIndex <= 0 || !isTechTreeView()) {
            return -1;
        }
        int domainIndex = entryIndex - 1;
        ResearchTechTreeDomainMenu menu = techTreeDomainMenu();
        return domainIndex >= 0 && domainIndex < menu.entries().size()
                ? menu.entryAt(domainIndex).visibleBlueprintCount()
                : -1;
    }

    private Component sidebarEntryFallbackLabel(int entryIndex) {
        if (entryIndex == 0) {
            return fullscreenViewActionShortName();
        }
        if (!isTechTreeView()) {
            return Component.literal("?");
        }
        int domainIndex = entryIndex - 1;
        ResearchTechTreeDomainMenu menu = techTreeDomainMenu();
        if (domainIndex < 0 || domainIndex >= menu.entries().size()) {
            return Component.literal("?");
        }
        return Component.translatable(switch (menu.entryAt(domainIndex).domain()) {
            case WEAPONS ->
                    "gui.taczweaponblueprints.research_bench.tree.domain.weapons.short";
            case ATTACHMENTS ->
                    "gui.taczweaponblueprints.research_bench.tree.domain.attachments.short";
            case AMMO ->
                    "gui.taczweaponblueprints.research_bench.tree.domain.ammo.short";
        });
    }

    private void activateSidebarSlot(int slot) {
        markFullscreenRailUsed();
        int groupCount = sidebarItemCount();
        int entryIndex = ResearchTreeFullscreenRailLayout.entryIndexForSlot(
                slot,
                sidebarButtons.size(),
                sidebarScroll,
                groupCount,
                ResearchTreePresentationContract.browseViewSelectorVisible());
        if (entryIndex < 0) {
            return;
        }
        if (entryIndex <= 0) {
            toggleBrowseView();
            return;
        }
        if (isTechTreeView()) {
            int domainIndex = entryIndex - 1;
            ResearchTechTreeDomainMenu menu = techTreeDomainMenu();
            if (domainIndex >= 0 && domainIndex < menu.entries().size()) {
                ResearchTechTreeDomainMenu.Entry entry = menu.entryAt(domainIndex);
                if (entry.available()) {
                    selectTechTreeDomain(entry.domain());
                }
            }
            return;
        }
        List<ResearchTreePresentation.Group> groups =
                treeProjections.publication().presentation().groups();
        int groupIndex = entryIndex - 1;
        if (groupIndex >= 0 && groupIndex < groups.size()) {
            selectResearchGroup(groups.get(groupIndex).id());
        }
    }

    private void selectResearchGroup(ResourceLocation groupId) {
        if (!ResearchTreePresentationContract.legacyBrowseViewsVisible()) {
            return;
        }
        saveActiveCamera();
        ResearchTreePresentation presentation = treeProjections.publication().presentation();
        ResearchTreePresentation.Group group = presentation.group(groupId).orElseThrow();
        ResearchTreePresentationContract.GroupSelectionAction action =
                treeNavigation.selectGroup(groupId, presentation);
        ResourceLocation preferred = group.iconNodeId()
                .orElse(group.members().get(0).nodeId());
        if (action == ResearchTreePresentationContract.GroupSelectionAction.SHOW_GROUP) {
            if (treeNavigation.browseView()
                    == ResearchTreePresentationContract.BrowseView.ALL_WEAPONS) {
                treeNavigation.setBrowseView(
                        ResearchTreePresentationContract.BrowseView.BRANCHES,
                        presentation);
            }
            applyActiveProjection(preferred);
            if (lastProjectionCameraRestored) {
                treeCanvas.setFocusedNode(preferred);
            } else {
                treeCanvas.focusNode(preferred);
            }
        } else {
            treeCanvas.focusGroup(
                    groupId,
                    group.members().stream()
                            .map(ResearchTreePresentation.Member::nodeId)
                            .toList());
            treeCanvas.setFocusedNode(preferred);
        }
        ensureSelectedSidebarVisible();
        updateWidgets();
    }

    private void ensureSelectedSidebarVisible() {
        if (sidebarButtons.isEmpty()) {
            return;
        }
        int selectedItem;
        if (isTechTreeView()) {
            selectedItem = techTreeDomainMenu().selectedDomain()
                    .map(Domain::ordinal)
                    .orElse(-1);
        } else if (treeNavigation.browseView()
                == ResearchTreePresentationContract.BrowseView.ALL_WEAPONS) {
            return;
        } else {
            List<ResearchTreePresentation.Group> groups =
                    treeProjections.publication().presentation().groups();
            selectedItem = treeNavigation.selectedGroupId()
                    .map(id -> groups.stream()
                            .map(ResearchTreePresentation.Group::id)
                            .toList()
                            .indexOf(id))
                    .orElse(-1);
        }
        int visibleGroupSlots = Math.max(
                0,
                sidebarButtons.size()
                        - (ResearchTreePresentationContract.browseViewSelectorVisible() ? 1 : 0));
        if (selectedItem < 0 || visibleGroupSlots == 0) {
            return;
        }
        if (selectedItem < sidebarScroll) {
            sidebarScroll = selectedItem;
        } else if (selectedItem >= sidebarScroll + visibleGroupSlots) {
            sidebarScroll = selectedItem - visibleGroupSlots + 1;
        }
    }

    private int sidebarItemCount() {
        return isTechTreeView()
                ? ResearchTechTreeContract.DOMAIN_ORDER.size()
                : treeProjections.publication().presentation().groups().size();
    }

    private void selectTechTreeDomain(Domain domain) {
        ResearchTechTreeProjectionCatalog catalog = treeProjections.techTreeProjections();
        if (domain == null || catalog.projection(domain).isEmpty()) {
            return;
        }
        saveActiveCamera();
        ResourceLocation preferred = techTreeNavigation.snapshot(domain)
                .flatMap(ResearchTechTreeViewState.DomainSnapshot::focusedNodeId)
                .orElseGet(() -> catalog.projection(domain).orElseThrow()
                        .graph().nodes().get(0).blueprintId());
        techTreeNavigation.selectDomain(domain, catalog);
        restoreTechTreeDomainSearch();
        applyActiveProjection(preferred);
        if (lastProjectionCameraRestored) {
            treeCanvas.setFocusedNode(preferred);
        } else {
            treeCanvas.focusNode(preferred);
        }
        ensureSelectedSidebarVisible();
        updateWidgets();
    }

    private void restoreTechTreeDomainSearch() {
        if (searchBox == null) {
            return;
        }
        String query = techTreeNavigation.searchQuery();
        if (!searchBox.getValue().equals(query)) {
            searchBox.setValue(query);
        }
        techTreeNavigation.activeSearchMatch().ifPresent(treeSearch::select);
    }

    private void requestResearch() {
        Optional<ResourceLocation> selected = menu.selectedBlueprint();
        if (researchTreePublicationRejected
                || selected.isEmpty()
                || researchFeedback.snapshot().status()
                        == ResearchTreeFeedbackState.Status.PENDING) {
            return;
        }
        ResourceLocation blueprintId = selected.orElseThrow();
        int requestId = nextActionRequestId();
        researchFeedback.pending(blueprintId, requestId, Util.getMillis());
        sendTracked(requestId, ResearchBenchResearchAction.RESEARCH, selected);
        uiUpdates.invalidateWidgets();
    }

    private void requestSelection(ResourceLocation blueprintId) {
        if (researchTreePublicationRejected || selectionFeedback.pending()) {
            return;
        }
        int requestId = nextActionRequestId();
        selectionFeedback.pending(blueprintId, requestId, Util.getMillis());
        sendTracked(
                requestId,
                ResearchBenchResearchAction.SELECT,
                Optional.of(blueprintId));
    }

    private void sendTracked(
            int requestId,
            ResearchBenchResearchAction action,
            Optional<ResourceLocation> id) {
        NetworkHandler.INSTANCE.sendToServer(
                new ResearchBenchActionPacket(
                        menu.containerId,
                        requestId,
                        action,
                        id,
                        action == ResearchBenchResearchAction.RESEARCH
                                ? menu.preview().routeFingerprint()
                                : Optional.empty()));
    }

    private int nextActionRequestId() {
        int requestId = nextActionRequestId;
        nextActionRequestId = requestId == Integer.MAX_VALUE ? 1 : requestId + 1;
        return requestId;
    }

    public void acceptActionResult(
            int requestId,
            ResearchBenchMenu.ActionResult result) {
        if (requestId < 1 || result == null || result.blueprintId().isEmpty()) {
            return;
        }
        ResourceLocation blueprintId = result.blueprintId().orElseThrow();
        long nowMillis = Util.getMillis();
        boolean changed;
        if (result.action() == ResearchBenchResearchAction.SELECT) {
            changed = result.successful()
                    ? selectionFeedback.acceptsResult(blueprintId, requestId)
                    : selectionFeedback.failed(
                            blueprintId,
                            requestId,
                            result.code()
                                            == ResearchBenchMenu.ActionResultCode
                                                    .REQUEST_THROTTLED
                                    ? "request_throttled"
                                    : "selection_rejected",
                            nowMillis);
            if (changed && result.successful()) {
                selectionFeedback.clear();
            }
        } else if (result.action() == ResearchBenchResearchAction.RESEARCH) {
            changed = result.successful()
                    ? researchFeedback.succeeded(blueprintId, requestId, nowMillis)
                    : researchFeedback.failed(
                            blueprintId,
                            requestId,
                            result.code().name().toLowerCase(Locale.ROOT),
                            nowMillis);
        } else {
            return;
        }
        if (changed) {
            if (result.action() == ResearchBenchResearchAction.RESEARCH) {
                playResearchResultSound(result.successful());
            }
            uiUpdates.invalidateWidgets();
            updateWidgets();
        }
    }

    private void playResearchResultSound(boolean successful) {
        if (minecraft == null) {
            return;
        }
        minecraft.getSoundManager().play(successful
                ? SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.15F)
                : SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.75F));
    }

    private void zoomTree(double direction) {
        cancelTreeInteraction();
        treeCanvas.zoomAtCenter(direction);
        updateWidgets();
    }

    private void useRailAndZoom(double direction) {
        markFullscreenRailUsed();
        zoomTree(direction);
    }

    private void fitTree() {
        cancelTreeInteraction();
        treeCanvas.fit();
        updateWidgets();
    }

    private void useRailAndFit() {
        markFullscreenRailUsed();
        fitTree();
    }

    private void markFullscreenRailUsed() {
        if (fullscreen) {
            fullscreenOverlayState.markRailUsed();
            railIdleTicks = 0;
        }
    }

    private void toggleRailPin() {
        if (!fullscreen) {
            return;
        }
        markFullscreenRailUsed();
        boolean pinned = fullscreenOverlayState.railState()
                != ResearchTreeFullscreenOverlayState.RailState.PINNED;
        fullscreenOverlayState.setRailPinned(pinned);
        GUIDANCE_PREFERENCE.setRailPinned(pinned);
        updateWidgets();
    }

    private MutableComponent railPinNarration() {
        return Component.translatable(fullscreenOverlayState.railState()
                == ResearchTreeFullscreenOverlayState.RailState.PINNED
                        ? "gui.taczweaponblueprints.research_bench.tree.rail.unpin"
                        : "gui.taczweaponblueprints.research_bench.tree.rail.pin");
    }

    private void toggleFullscreenSearch() {
        if (!fullscreen || searchBox == null) {
            return;
        }
        markFullscreenRailUsed();
        if (fullscreenOverlayState.searchState()
                == ResearchTreeFullscreenOverlayState.SearchState.CLOSED) {
            openFullscreenSearch(true);
        } else {
            closeFullscreenSearch();
        }
    }

    private void openFullscreenSearch(boolean focus) {
        cancelTreeInteraction();
        fullscreenOverlayState.openSearch(focus);
        updateWidgets();
        if (focus) {
            setInitialFocus(searchBox);
            pendingSearchFocus = searchBox;
        }
    }

    private void closeFullscreenSearch() {
        fullscreenOverlayState.closeSearch();
        pendingSearchFocus = null;
        if (getFocused() == searchBox) {
            setFocused(null);
        }
        updateWidgets();
    }

    private void toggleBrowseView() {
        if (!ResearchTreePresentationContract.browseViewSelectorVisible()) {
            return;
        }
        saveActiveCamera();
        ResearchTreePresentationContract.BrowseView next =
                ResearchTreePresentationContract.nextBrowseView(
                        treeNavigation.browseView(), techTreeAvailable());
        ResourceLocation preferredFocus = treeCanvas.focusedId().orElse(null);
        ResearchTreePresentation presentation = treeProjections.publication().presentation();
        treeNavigation.setBrowseView(next, presentation);
        if (next == ResearchTreePresentationContract.BrowseView.TECH_TREE
                && preferredFocus != null) {
            techTreeNavigation.selectNode(
                    preferredFocus, treeProjections.techTreeProjections());
        }
        if (next == ResearchTreePresentationContract.BrowseView.TECH_TREE) {
            restoreTechTreeDomainSearch();
        }
        if (next == ResearchTreePresentationContract.BrowseView.BRANCHES
                && preferredFocus != null) {
            presentation.membership(preferredFocus).ifPresent(membership ->
                    treeNavigation.selectGroup(membership.groupId(), presentation));
        }
        applyActiveProjection(preferredFocus);
        ensureSelectedSidebarVisible();
        boolean preferredFocusVisible = preferredFocus != null
                && treeCanvas.graph().node(preferredFocus).isPresent();
        switch (ResearchTreePresentationContract.cameraArrivalAction(
                lastProjectionCameraRestored, preferredFocusVisible)) {
            case RETAIN_CAMERA_AND_FOCUS -> treeCanvas.setFocusedNode(preferredFocus);
            case FOCUS_PREFERRED -> treeCanvas.focusNode(preferredFocus);
            case FOCUS_FALLBACK -> focusUsefulNode();
            case RETAIN_CAMERA -> {
                // setContent already retained the best visible local focus.
            }
        }
        updateWidgets();
    }

    private void cycleResearchGroup() {
        if (isTechTreeView()) {
            cycleTechTreeDomain(1);
            return;
        }
        ResearchTreePresentation presentation = treeProjections.publication().presentation();
        treeNavigation.nextGroup(presentation, 1).ifPresent(this::selectResearchGroup);
    }

    private boolean cycleTechTreeDomain(int delta) {
        Optional<Domain> target = techTreeDomainMenu().cycle(delta);
        if (target.isEmpty()) {
            return false;
        }
        if (techTreeNavigation.selectedDomain().filter(target.orElseThrow()::equals).isEmpty()) {
            selectTechTreeDomain(target.orElseThrow());
        }
        return true;
    }

    private Component browseViewShortName() {
        return Component.translatable(switch (treeNavigation.browseView()) {
            case BRANCHES ->
                    "gui.taczweaponblueprints.research_bench.tree.view.branches.short";
            case ALL_WEAPONS ->
                    "gui.taczweaponblueprints.research_bench.tree.view.all_weapons.short";
            case TECH_TREE ->
                    "gui.taczweaponblueprints.research_bench.tree.view.tech_tree.short";
        });
    }

    private Component fullscreenViewActionName() {
        return Component.translatable(switch (ResearchTreePresentationContract
                .fullscreenViewAction(treeNavigation.browseView(), techTreeAvailable())) {
            case SHOW_ALL_WEAPONS ->
                    "gui.taczweaponblueprints.research_bench.tree.view.action.show_all_weapons";
            case SHOW_BRANCHES ->
                    "gui.taczweaponblueprints.research_bench.tree.view.action.show_branches";
            case SHOW_TECH_TREE ->
                    "gui.taczweaponblueprints.research_bench.tree.view.action.show_tech_tree";
        });
    }

    private Component fullscreenViewActionShortName() {
        return Component.translatable(switch (ResearchTreePresentationContract
                .fullscreenViewAction(treeNavigation.browseView(), techTreeAvailable())) {
            case SHOW_ALL_WEAPONS ->
                    "gui.taczweaponblueprints.research_bench.tree.view.all_weapons.short";
            case SHOW_BRANCHES ->
                    "gui.taczweaponblueprints.research_bench.tree.view.branches.short";
            case SHOW_TECH_TREE ->
                    "gui.taczweaponblueprints.research_bench.tree.view.tech_tree.short";
        });
    }

    private MutableComponent searchToggleNarration() {
        if (fullscreenOverlayState.searchState()
                == ResearchTreeFullscreenOverlayState.SearchState.CLOSED) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.search.toggle");
        }
        String query = searchBox == null ? "" : searchBox.getValue().strip();
        return query.isEmpty()
                ? Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.search.close")
                : Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.search.results_action",
                        treeSearch.matches().size());
    }

    private Component currentBrowseViewName() {
        return Component.translatable(switch (treeNavigation.browseView()) {
            case BRANCHES -> "gui.taczweaponblueprints.research_bench.tree.view.branches";
            case ALL_WEAPONS -> "gui.taczweaponblueprints.research_bench.tree.view.all_weapons";
            case TECH_TREE -> "gui.taczweaponblueprints.research_bench.tree.view.tech_tree";
        });
    }

    private Component currentGroupName() {
        if (isTechTreeView()) {
            return techTreeNavigation.selectedDomain()
                    .map(this::techTreeDomainName)
                    .orElseGet(() -> Component.translatable(
                            "gui.taczweaponblueprints.research_bench.tree.group.none"));
        }
        return treeNavigation.selectedGroupId()
                .flatMap(treeProjections.publication().presentation()::group)
                .map(this::groupName)
                .orElseGet(() -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.group.none"));
    }

    private Component techTreeDomainName(Domain domain) {
        return treeProjections.techTreeProjections().presentation().domain(domain)
                .map(view -> view.translationKey()
                        .<Component>map(key -> Component.translatableWithFallback(
                        key, view.title()))
                        .orElseGet(() -> Component.literal(view.title())))
                .orElseGet(() -> Component.translatable(switch (domain) {
                    case WEAPONS -> "gui.taczweaponblueprints.tech_tree.domain.weapons";
                    case ATTACHMENTS -> "gui.taczweaponblueprints.tech_tree.domain.attachments";
                    case AMMO -> "gui.taczweaponblueprints.tech_tree.domain.ammo";
                }));
    }

    private ItemStack techTreeDomainIcon(ResearchTechTreeDomainMenu.Entry entry) {
        return entry.iconNodeId()
                .map(id -> researchTreeIcons.getOrDefault(id, ItemStack.EMPTY))
                .orElse(ItemStack.EMPTY);
    }

    private MutableComponent techTreeDomainTooltip(Domain domain, int nodeCount) {
        Component name = techTreeDomainName(domain);
        return nodeCount > 0
                ? Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.domain.tooltip.count",
                        name,
                        nodeCount)
                : Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.domain.tooltip.unavailable",
                        name);
    }

    private Component techTreeTierName(int tierOrdinal) {
        List<ResearchTechTreePresentation.BandLabel> bands =
                treeProjections.techTreeProjections().presentation().bands();
        if (!bands.isEmpty() && tierOrdinal >= 0 && tierOrdinal < bands.size()) {
            ResearchTechTreePresentation.BandLabel band = bands.get(tierOrdinal);
            Component label = band.translationKey()
                    .<Component>map(key -> Component.translatableWithFallback(
                            key, band.title()))
                    .orElseGet(() -> Component.literal(band.title()));
            return band.color()
                    .<Component>map(color -> label.copy().withStyle(style ->
                            style.withColor(color)))
                    .orElse(label);
        }
        List<ResearchTechTreePresentation.TierLabel> tiers =
                treeProjections.techTreeProjections().presentation().tiers();
        if (tierOrdinal < 0 || tierOrdinal >= tiers.size()) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.tier", tierOrdinal + 1);
        }
        ResearchTechTreePresentation.TierLabel tier = tiers.get(tierOrdinal);
        return tier.translationKey()
                .<Component>map(key -> Component.translatableWithFallback(key, tier.title()))
                .orElseGet(() -> Component.literal(tier.title()));
    }

    private Component groupName(ResearchTreePresentation.Group group) {
        return group.translationKey()
                .map(key -> Component.translatableWithFallback(key, group.title()))
                .orElseGet(() -> Component.literal(group.title()));
    }

    private Component groupName(ResourceLocation groupId) {
        return treeProjections.publication().presentation().group(groupId)
                .map(this::groupName)
                .orElseGet(() -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.group.unknown"));
    }

    private void dismissGuidance() {
        guidanceVisible = false;
        fullscreenOverlayState.setGuidanceVisible(false);
        GUIDANCE_PREFERENCE.dismiss();
        updateWidgets();
    }

    private void showGuidance() {
        guidanceVisible = true;
        fullscreenOverlayState.setGuidanceVisible(true);
        updateWidgets();
    }

    private void closePermanentFullscreen() {
        cancelTreeInteraction();
        switch (ResearchBenchPresentationPolicy.fullscreenExitAction()) {
            case CLOSE_SCREEN -> onClose();
        }
    }

    private void cancelTreeInteraction() {
        treeMinimap.cancelNavigation();
        fullscreenGesture.cancel();
        fullscreenHoldActivation.cancel();
        pendingPortalActivation = null;
        pendingTechTreePortalActivation = null;
        treeCanvas.cancelInteraction();
    }

    private ResearchTreeScreenLayout.Rect offset(ResearchTreeScreenLayout.Rect bounds) {
        return new ResearchTreeScreenLayout.Rect(
                leftPos + bounds.x(),
                topPos + bounds.y(),
                bounds.width(),
                bounds.height());
    }

    private Component nodeName(ResearchTreeGraph.Node node) {
        if (!node.visibility().revealsName()) {
            return Component.translatable(ResearchTreeGraph.REDACTED_NAME_KEY);
        }
        return Component.translatable(node.nameKey());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshDisplayPolicy();
        long cameraFrameMillis = Util.getMillis();
        double cameraDeltaSeconds = lastCameraFrameMillis == 0L
                ? 1.0D / 60.0D
                : Math.max(0.0D, (cameraFrameMillis - lastCameraFrameMillis) / 1_000.0D);
        lastCameraFrameMillis = cameraFrameMillis;
        treeCanvas.tickCamera(cameraDeltaSeconds);
        treeMinimap.updateViewport(treeCanvas.viewport());
        if (fullscreen) {
            updateFullscreenContextCardWidgets();
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (fullscreen
                && fullscreenOverlayLayout != null
                && fullscreenOverlayState.railState()
                        == ResearchTreeFullscreenOverlayState.RailState.EDGE_HANDLE
                && fullscreenOverlayLayout.edgeRevealHitTarget().contains(mouseX, mouseY)) {
            fullscreenOverlayState.revealRail();
            railIdleTicks = 0;
            updateWidgets();
        }
        boolean guidanceHovered = guidanceContains(mouseX, mouseY);
        boolean searchOverlayHovered = searchOverlayContains(mouseX, mouseY);
        ResearchTreeInteractionPolicy.PointerTarget pointerTarget =
                fullscreenPointerTarget(mouseX, mouseY);
        boolean graphHoverAllowed = !searchOverlayHovered
                && !treeMinimap.contains(mouseX, mouseY)
                && (!fullscreen
                        || ResearchTreeInteractionPolicy.allowsGraphHover(pointerTarget)
                                && !fullscreenGesture.dragging());
        if (!guidanceHovered
                && graphHoverAllowed) {
            treeCanvas.updateHover(mouseX, mouseY);
        } else {
            treeCanvas.clearHover();
        }
        if (!fullscreen) {
            renderBackground(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderCompactSelectedDetailsTooltip(graphics, mouseX, mouseY);
        if (!guidanceHovered
                && graphHoverAllowed) {
            renderTreeTooltip(graphics, mouseX, mouseY);
        }
        if (fullscreen) {
            renderFullscreenContextCardTooltip(graphics, mouseX, mouseY);
        }
    }

    private void refreshDisplayPolicy() {
        ResearchTreeDisplayPolicy current = ModConfigs.RESEARCH_TREE_CLIENT.displayPolicy();
        if (!current.equals(researchDisplayPolicyIdentity)) {
            treeCanvas.setDisplayPolicy(current);
            researchDisplayPolicyIdentity = current;
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawBrowseBackground(graphics);
        renderGuidanceAtScreenCoordinates(graphics);
    }

    private void drawBrowseBackground(GuiGraphics graphics) {
        ResearchTreeScreenLayout.Rect details = activeTreeLayout.details();
        if (!fullscreen && activeTreeLayout.detailsPlacement()
                != ResearchTreeScreenLayout.DetailsPlacement.OVERLAY) {
            graphics.fill(
                    leftPos + details.x(),
                    topPos + details.y(),
                    leftPos + details.right(),
                    topPos + details.bottom(),
                    SECTION);
            graphics.renderOutline(
                    leftPos + details.x(),
                    topPos + details.y(),
                    details.width(),
                    details.height(),
                    0xFF394552);
        }
        renderBrowseCanvas(graphics);
        if (fullscreen) {
            renderFullscreenHoldProgress(graphics);
            renderFullscreenNavigationBackground(graphics);
            treeMinimap.render(graphics);
            renderFullscreenContextCardBackground(graphics);
        }
    }

    private void renderFullscreenHoldProgress(GuiGraphics graphics) {
        ResearchTreeHoldActivationController.Snapshot hold =
                fullscreenHoldActivation.snapshot(Util.getMillis());
        if (hold.status() != ResearchTreeHoldActivationController.Status.HOLDING
                || hold.blueprintId().isEmpty()) {
            return;
        }
        fullscreenNodeAnchor(hold.blueprintId().orElseThrow()).ifPresent(anchor -> {
            int x = anchor.x() - 2;
            int y = anchor.y() + anchor.height() + 2;
            int width = anchor.width() + 4;
            int interiorWidth = Math.max(0, width - 2);
            int completedWidth = (int) Math.ceil(interiorWidth * hold.progress());
            graphics.fill(x, y, x + width, y + 5, 0xE00B0F14);
            if (completedWidth > 0) {
                graphics.fill(x + 1, y + 1, x + 1 + completedWidth, y + 4, ACCENT);
            }
            graphics.renderOutline(x, y, width, 5, TEXT);
        });
    }

    private void renderBrowseCanvas(GuiGraphics graphics) {
        int zOffset = ResearchTreePresentationContract.graphZOffset(fullscreen);
        if (zOffset == ResearchTreePresentationContract.FULLSCREEN_OVERLAY_Z_OFFSET) {
            renderBrowseCanvasContents(graphics);
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, 0.0D, zOffset);
        try {
            renderBrowseCanvasContents(graphics);
        } finally {
            graphics.pose().popPose();
        }
        // GuiGraphics.renderItem flushes models at +150 inside the active pose.
        // Finish every remaining graph primitive before returning to overlay Z=0.
        graphics.flush();
    }

    private void renderBrowseCanvasContents(GuiGraphics graphics) {
        treeCanvas.render(
                graphics,
                font,
                this::nodeName,
                this::nodeBorderColor,
                this::graphNodeStatusSymbol,
                isTechTreeView() ? ignored -> Component.empty() : this::groupName,
                isTechTreeView()
                        ? this::techTreeTierName
                        : tier -> Component.translatable(
                                "gui.taczweaponblueprints.research_bench.tree.tier", tier + 1));
    }

    private void renderFullscreenNavigationBackground(GuiGraphics graphics) {
        if (fullscreenOverlayLayout == null) {
            return;
        }
        if (fullscreenOverlayState.railState()
                == ResearchTreeFullscreenOverlayState.RailState.EDGE_HANDLE) {
            ResearchTreeScreenLayout.Rect handle = fullscreenOverlayLayout.edgeReveal();
            graphics.fill(handle.x(), handle.y(), handle.right(), handle.bottom(), 0xC068798C);
            return;
        }
        ResearchTreeScreenLayout.Rect rail = fullscreenOverlayLayout.rail();
        graphics.fill(rail.x(), rail.y(), rail.right(), rail.bottom(), 0xD8111820);
        graphics.renderOutline(rail.x(), rail.y(), rail.width(), rail.height(), BORDER);
    }

    private void renderFullscreenContextCardBackground(GuiGraphics graphics) {
        if (fullscreenContextCardLayout == null) {
            return;
        }
        ResearchTreeScreenLayout.Rect card = fullscreenContextCardLayout.card();
        graphics.fill(card.x(), card.y(), card.right(), card.bottom(), 0xFF111820);
        graphics.renderOutline(card.x(), card.y(), card.width(), card.height(), BORDER);
        for (ResearchTreeScreenLayout.Rect ingredient : fullscreenContextCardLayout.ingredients()) {
            graphics.fill(
                    ingredient.x(), ingredient.y(), ingredient.right(), ingredient.bottom(), SLOT);
            graphics.renderOutline(
                    ingredient.x(), ingredient.y(), ingredient.width(), ingredient.height(),
                    0xFF394552);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (fullscreen) {
            renderFullscreenBrowseLabels(graphics);
            return;
        }
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        Component tierLabel = workbenchTierLabel();
        graphics.drawString(
                font,
                tierLabel,
                imageWidth - font.width(tierLabel) - 8,
                titleLabelY,
                MUTED,
                false);
        renderBrowseLabels(graphics);
    }

    private void renderGuidanceAtScreenCoordinates(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos, topPos, 0.0D);
        try {
            renderGuidance(graphics);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderGuidance(GuiGraphics graphics) {
        if (!guidanceVisible) {
            return;
        }
        ResearchTreeGuidanceLayout.Guide guide = activeGuidanceLayout();
        ResearchTreeScreenLayout.Rect panel = guide.panel();
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xFA111820);
        graphics.renderOutline(panel.x(), panel.y(), panel.width(), panel.height(), ACCENT);
        if (fullscreen) {
            graphics.drawString(
                    font,
                    clipped(Component.translatable(
                            "gui.taczweaponblueprints.research_bench.tree.guide.fullscreen"),
                            Math.max(1, guide.dismiss().x() - panel.x() - 10)),
                    panel.x() + 6,
                    panel.y() + 8,
                    TEXT,
                    false);
            return;
        }
        graphics.drawString(
                font,
                clipped(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.guide.select"),
                        panel.width() - 12),
                panel.x() + 6,
                panel.y() + 6,
                TEXT,
                false);
        graphics.drawString(
                font,
                clipped(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.guide.relationships"),
                        panel.width() - 12),
                panel.x() + 6,
                panel.y() + 18,
                TEXT,
                false);
        graphics.drawString(
                font,
                clipped(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.guide.navigation"),
                        panel.width() - 12),
                panel.x() + 6,
                panel.y() + 30,
                TEXT,
                false);
        renderGuideLegendEntry(
                graphics,
                panel.x() + 6,
                panel.y() + 42,
                ResearchTreePresentationContract.StatusSymbol.LEARNED,
                GOOD,
                "gui.taczweaponblueprints.research_bench.tree.legend.learned");
        renderGuideLegendEntry(
                graphics,
                panel.x() + 92,
                panel.y() + 42,
                ResearchTreePresentationContract.StatusSymbol.AVAILABLE,
                ACCENT,
                "gui.taczweaponblueprints.research_bench.tree.legend.available");
        renderGuideLegendEntry(
                graphics,
                panel.x() + 6,
                panel.y() + 54,
                ResearchTreePresentationContract.StatusSymbol.LOCKED,
                WARN,
                "gui.taczweaponblueprints.research_bench.tree.legend.locked");
        renderGuideLegendEntry(
                graphics,
                panel.x() + 92,
                panel.y() + 54,
                ResearchTreePresentationContract.StatusSymbol.UNKNOWN,
                MUTED,
                "gui.taczweaponblueprints.research_bench.tree.legend.hidden");
    }

    private void renderGuideLegendEntry(
            GuiGraphics graphics,
            int x,
            int y,
            ResearchTreePresentationContract.StatusSymbol symbol,
            int color,
            String labelKey) {
        ResearchTreeStatusGlyph.render(
                graphics,
                x,
                y + 1,
                color,
                ResearchTreeStatusGlyph.forSymbol(symbol));
        graphics.drawString(
                font,
                Component.translatable(labelKey),
                x + ResearchTreeStatusGlyph.SIZE + 3,
                y,
                TEXT,
                false);
    }

    private boolean guidanceContains(double mouseX, double mouseY) {
        return guidanceVisible
                && activeGuidanceLayout().panel()
                        .contains(mouseX - leftPos, mouseY - topPos);
    }

    private ResearchTreeGuidanceLayout.Guide activeGuidanceLayout() {
        return fullscreen && fullscreenOverlayLayout != null
                ? ResearchTreeGuidanceLayout.forFullscreen(fullscreenOverlayLayout)
                : ResearchTreeGuidanceLayout.forLayout(activeTreeLayout);
    }

    /** Resolves the owner of an intentionally overlapping fullscreen pointer position. */
    private ResearchTreeInteractionPolicy.PointerTarget fullscreenPointerTarget(
            double mouseX,
            double mouseY) {
        if (!fullscreen) {
            return ResearchTreeInteractionPolicy.PointerTarget.NONE;
        }
        boolean guidance = guidanceContains(mouseX, mouseY);
        boolean contextCard = (fullscreenContextCardLayout != null
                && fullscreenContextCardLayout.card().contains(mouseX, mouseY))
                || (returnToSelectionButton != null
                        && returnToSelectionButton.visible
                        && returnToSelectionButton.isMouseOver(mouseX, mouseY))
                || (researchGoalButton != null
                        && researchGoalButton.visible
                        && researchGoalButton.isMouseOver(mouseX, mouseY));
        boolean close = fullscreenButton != null
                && fullscreenButton.visible
                && fullscreenButton.isMouseOver(mouseX, mouseY);
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        sidebarButtons.forEach(button -> button.updatePointerState(mouseX, mouseY));
        boolean sidebar = fullscreenRailPointerRegion().contains(localX, localY)
                || (affordabilityButton != null
                        && affordabilityButton.visible
                        && affordabilityButton.isMouseOver(mouseX, mouseY))
                || sidebarButtons.stream().anyMatch(button ->
                        button.visible && button.ownsPointer(mouseX, mouseY));
        boolean search = searchBox != null
                && searchBox.visible
                && (fullscreenOverlayLayout.searchField().contains(localX, localY)
                        || searchResultButtons.stream().anyMatch(button ->
                                button.visible && button.isMouseOver(mouseX, mouseY)));
        boolean overlayOwned = guidance || contextCard || search || sidebar || close;
        boolean graphCanvas = treeCanvas.contains(mouseX, mouseY);
        boolean graphElement = !overlayOwned
                && graphCanvas
                && (treeCanvas.nodeAt(mouseX, mouseY).isPresent()
                        || treeCanvas.portalAt(mouseX, mouseY).isPresent()
                        || treeCanvas.techTreePortalTargetAt(mouseX, mouseY).isPresent());
        return ResearchTreeInteractionPolicy.route(
                new ResearchTreeInteractionPolicy.PointerLayers(
                        guidance,
                        contextCard,
                        search,
                        sidebar,
                        close,
                        graphElement,
                        graphCanvas));
    }

    private ResearchTreeScreenLayout.Rect fullscreenRailPointerRegion() {
        if (fullscreenOverlayLayout == null) {
            return new ResearchTreeScreenLayout.Rect(0, 0, 1, 1);
        }
        return fullscreenOverlayState.railState()
                == ResearchTreeFullscreenOverlayState.RailState.EDGE_HANDLE
                        ? fullscreenOverlayLayout.edgeRevealHitTarget()
                        : fullscreenOverlayLayout.rail();
    }

    private boolean pointerOverFullscreenRail(double mouseX, double mouseY) {
        sidebarButtons.forEach(button -> button.updatePointerState(mouseX, mouseY));
        return fullscreenRailPointerRegion().contains(mouseX - leftPos, mouseY - topPos)
                || (affordabilityButton != null
                        && affordabilityButton.visible
                        && affordabilityButton.isMouseOver(mouseX, mouseY))
                || sidebarButtons.stream().anyMatch(button ->
                        button.visible && button.ownsPointer(mouseX, mouseY));
    }

    private boolean railHasKeyboardFocus() {
        Object focused = getFocused();
        return focused == searchToggleButton
                || focused == zoomOutButton
                || focused == zoomInButton
                || focused == fitButton
                || focused == affordabilityButton
                || focused == recommendationButton
                || focused == railPinButton
                || focused == helpButton
                || sidebarButtons.stream().anyMatch(button -> button == focused);
    }

    private void renderFullscreenBrowseLabels(GuiGraphics graphics) {
        ResearchTreeScreenLayout.Rect canvas = activeTreeLayout.canvas();
        int messageX = fullscreenOverlayLayout == null
                ? canvas.x() + 8
                : fullscreenOverlayLayout.rail().right() + 8;
        int stateMessageY = fullscreenOverlayLayout == null
                ? canvas.y() + 8
                : fullscreenOverlayLayout.searchField().bottom() + 8;
        graphics.drawString(font, workbenchTierLabel(), messageX, stateMessageY, MUTED, false);
        stateMessageY += font.lineHeight + 3;
        int nextMessageY = stateMessageY;
        if (!guidanceVisible && researchTreePublicationRejected) {
            nextMessageY = renderRejectedPublicationWarning(
                    graphics,
                    messageX,
                    stateMessageY,
                    Math.max(1, canvas.right() - messageX - 8));
        }
        if (!guidanceVisible
                && !researchTreePublicationRejected
                && treeCanvas.graph().nodes().isEmpty()) {
            graphics.drawWordWrap(
                    font,
                    emptyTreeMessage(),
                    messageX,
                    nextMessageY,
                    Math.max(1, canvas.right() - messageX - 8),
                    MUTED);
        }
        if (!guidanceVisible
                && searchBox != null
                && searchBox.visible
                && !searchBox.getValue().isBlank()
                && treeSearch.matches().isEmpty()) {
            graphics.drawString(
                    font,
                    Component.translatable("gui.taczweaponblueprints.research_bench.search.empty"),
                    messageX,
                    nextMessageY,
                    WARN,
                    false);
        }

        renderFullscreenContextCardContent(graphics);
    }

    private Component workbenchTierLabel() {
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.workbench_tier",
                menu.workbenchTier().level());
    }

    private void renderFullscreenContextCardContent(GuiGraphics graphics) {
        if (fullscreenContextCardLayout == null) {
            return;
        }
        Optional<ResourceLocation> pinned = fullscreenOverlayState.pinnedNodeId();
        Optional<ResearchTreeGraph.Node> selected = pinned.flatMap(treeCanvas.graph()::node);
        if (selected.isEmpty()) {
            return;
        }
        ResearchTreeGraph.Node node = selected.orElseThrow();
        ResearchTreeSelectedNodePresenter.Presentation details = selectedNodePresentation(node);
        SelectedNodeUi selectedUi = selectedNodeUi(node, details);
        ResearchTreeContextCardLayout.Layout card = fullscreenContextCardLayout;
        drawFocusedNodeIcon(graphics, node, card.icon().x(), card.icon().y());
        graphics.drawString(
                font,
                clipped(nodeName(node), card.name().width()),
                card.name().x(),
                card.name().y(),
                TEXT,
                false);
        ResearchTreeStatusGlyph.render(
                graphics,
                card.status().x(),
                card.status().y() + 1,
                nodeBorderColor(node),
                ResearchTreeStatusGlyph.forSymbol(nodeStatusSymbol(node)));
        graphics.drawString(
                font,
                clipped(selectedUi.message(), card.status().width() - 10),
                card.status().x() + 10,
                card.status().y(),
                selectedUi.color(),
                false);
        graphics.drawString(
                font,
                clipped(details.exactPreview()
                        ? selectedNodeCostOrVisibility(node, details)
                        : selectedNodeOverviewSummary(node, details),
                        card.summary().width()),
                card.summary().x(),
                card.summary().y(),
                MUTED,
                false);
        if (!card.exactPreview()) {
            return;
        }

        graphics.drawString(
                font,
                clipped(selectedPointBalanceLabel(details), card.balance().width()),
                card.balance().x(),
                card.balance().y(),
                !details.pointsEnabled()
                        ? MUTED
                        : details.pointsSatisfied() ? ACCENT : BAD,
                false);
        for (int index = 0; index < card.ingredients().size(); index++) {
            ResearchTreeScreenLayout.Rect slot = card.ingredients().get(index);
            ResearchSelectionPreview.IngredientPreview ingredient = details.ingredients().get(index);
            ItemStack icon = ingredientIcon(ingredient);
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, slot.x() + 1, slot.y() + 1);
            } else {
                graphics.drawCenteredString(font, "?", slot.x() + 9, slot.y() + 5, MUTED);
            }
            Component count = Component.translatable(details.costBypassed()
                            ? "gui.taczweaponblueprints.research_bench.tree.card.ingredient_bypassed"
                            : "gui.taczweaponblueprints.research_bench.tree.card.ingredient",
                    ingredientName(ingredient),
                    ingredient.inventoryAvailable(),
                    ingredient.required());
            graphics.drawString(
                    font,
                    clipped(count, Math.max(1, slot.width() - 20)),
                    slot.x() + 19,
                    slot.y() + 5,
                    details.costBypassed()
                            || ingredient.inventoryAvailable() >= ingredient.required() ? GOOD : BAD,
                    false);
        }
        graphics.drawString(
                font,
                clipped(selectedProgressionSummary(node).orElseGet(() ->
                                details.pathPurchase()
                                        ? selectedPathDisplaySummary(details)
                                        : selectedRequirementGroupSummary(node)
                                                .orElseGet(() -> selectedRelationshipSummary(details))),
                        card.readiness().width()),
                card.readiness().x(),
                card.readiness().y(),
                MUTED,
                false);
    }

    private void renderFullscreenContextCardTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {
        if (fullscreenContextCardLayout == null
                || !fullscreenContextCardLayout.exactPreview()) {
            return;
        }
        List<ResearchTreeScreenLayout.Rect> slots = fullscreenContextCardLayout.ingredients();
        for (int index = 0; index < slots.size(); index++) {
            if (!slots.get(index).contains(mouseX, mouseY)) {
                continue;
            }
            ResearchSelectionPreview.IngredientPreview ingredient = menu.preview().ingredients().get(index);
            renderWrappedTooltip(
                    graphics,
                    List.of(
                            ingredientName(ingredient),
                            Component.translatable(
                                    "gui.taczweaponblueprints.research_bench.tree.tooltip.ingredient_count",
                                    ingredient.inventoryAvailable(),
                                    ingredient.required())),
                    mouseX,
                    mouseY);
            return;
        }
        if (fullscreenContextCardLayout.readiness().contains(mouseX, mouseY)) {
            Optional<ResearchTreeGraph.Node> selected = fullscreenOverlayState.pinnedNodeId()
                    .flatMap(treeCanvas.graph()::node);
            if (selected.isPresent()) {
                ResearchTreeGraph.Node node = selected.orElseThrow();
                ResearchTreeSelectedNodePresenter.Presentation details =
                        selectedNodePresentation(node);
                ArrayList<Component> lines = new ArrayList<>();
                lines.addAll(selectedProgressionLines(node));
                if (details.pathPurchase()) {
                    lines.add(selectedPathSummary(details));
                    if (details.additionalIngredientTypes() > 0) {
                        lines.add(Component.translatable(
                                "gui.taczweaponblueprints.research_bench.tree.path.more_materials",
                                details.additionalIngredientTypes()));
                    }
                }
                lines.add(selectedRelationshipSummary(selectedNodePresentation(node)));
                selectedRequirementGroupSummary(node).ifPresent(lines::add);
                lines.addAll(ResearchTreeRequirementText.details(
                        treeCanvas.graph(), node.blueprintId(), this::nodeName));
                renderWrappedTooltip(graphics, List.copyOf(lines), mouseX, mouseY);
            }
        }
    }

    private void renderCompactSelectedDetailsTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {
        if (fullscreen || guidanceVisible) {
            return;
        }
        if (!ResearchTreeDetailLayout.compactDetailsTooltipAt(
                activeTreeLayout, mouseX - leftPos, mouseY - topPos)) {
            return;
        }
        Optional<ResearchTreeGraph.Node> selected = focusedTreeNode();
        if (selected.isEmpty()) {
            return;
        }
        ResearchTreeGraph.Node node = selected.orElseThrow();
        ResearchTreeSelectedNodePresenter.Presentation presentation =
                selectedNodePresentation(node);
        SelectedNodeUi selectedUi = selectedNodeUi(node, presentation);
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(nodeName(node));
        selectedTierLine(node).ifPresent(lines::add);
        selectedCraftingLine(node).ifPresent(lines::add);
        lines.add(selectedUi.message());
        selectedFragmentLine(node).ifPresent(lines::add);
        if (node.visibility().revealsResearchSummary() || presentation.exactPreview()) {
            lines.add(selectedNodeCostOrVisibility(node, presentation));
        }
        if (presentation.exactPreview()) {
            if (presentation.pathPurchase()) {
                lines.add(selectedPathSummary(presentation));
            }
            lines.add(selectedPointBalanceLabel(presentation));
            for (ResearchSelectionPreview.IngredientPreview ingredient
                    : presentation.ingredients()) {
                lines.add(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.tooltip.ingredient",
                        ingredientName(ingredient),
                        ingredient.inventoryAvailable(),
                        ingredient.required()));
            }
            if (presentation.additionalIngredientTypes() > 0) {
                lines.add(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.path.more_materials",
                        presentation.additionalIngredientTypes()));
            }
        }
        lines.add(selectedRelationshipSummary(presentation));
        selectedRequirementGroupSummary(node).ifPresent(lines::add);
        lines.addAll(ResearchTreeRequirementText.details(
                treeCanvas.graph(), node.blueprintId(), this::nodeName));
        renderWrappedTooltip(graphics, List.copyOf(lines), mouseX, mouseY);
    }

    private void renderWrappedTooltip(
            GuiGraphics graphics,
            List<Component> lines,
            int mouseX,
            int mouseY) {
        int maximumWidth = Math.max(80, Math.min(240, width - 32));
        List<FormattedCharSequence> wrapped = lines.stream()
                .flatMap(line -> font.split(line, maximumWidth).stream())
                .toList();
        graphics.renderTooltip(font, wrapped, mouseX, mouseY);
    }

    private void drawFocusedNodeIcon(
            GuiGraphics graphics,
            ResearchTreeGraph.Node node,
            int x,
            int y) {
        ItemStack icon = treeCanvas.icon(node.blueprintId());
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, x, y);
        } else {
            graphics.fill(x, y, x + 16, y + 16, SLOT);
            graphics.renderOutline(x, y, 16, 16, nodeBorderColor(node));
            graphics.drawCenteredString(font, "?", x + 8, y + 4, MUTED);
        }
    }

    private ResearchTreeSelectedNodePresenter.Presentation selectedNodePresentation(
            ResearchTreeGraph.Node node) {
        return ResearchTreeSelectedNodePresenter.present(
                new ResearchTreeSelectedNodePresenter.Input(
                        node,
                        canAfford(node),
                        treeCanvas.authoritativeSelectedId(),
                        menu.preview(),
                        treeCanvas.totalRequirementCount(node.blueprintId()),
                        treeProjections.unlocks().immediateUnlockCount(
                                node.blueprintId()),
                        publishedResearchCostMode()));
    }

    private ResearchCostMode publishedResearchCostMode() {
        return ModConfigs.BLUEPRINT.progressionSnapshot().researchCostMode();
    }

    private SelectedNodeUi selectedNodeUi(
            ResearchTreeGraph.Node node,
            ResearchTreeSelectedNodePresenter.Presentation presentation) {
        if (researchTreePublicationRejected) {
            return new SelectedNodeUi(rejectedPublicationMessage(), BAD, false);
        }
        ResearchTreeFeedbackState.Snapshot research = researchFeedback.snapshot();
        if (research.blueprintId().filter(node.blueprintId()::equals).isPresent()) {
            return switch (research.status()) {
                case PENDING -> new SelectedNodeUi(
                        Component.translatable(
                                "gui.taczweaponblueprints.research_bench.tree.selection.research_pending"),
                        ACCENT,
                        false);
                case SUCCESS -> new SelectedNodeUi(
                        Component.translatable(
                                "gui.taczweaponblueprints.research_bench.tree.selection.research_success"),
                        GOOD,
                        false);
                case FAILURE -> new SelectedNodeUi(
                        Component.translatable(
                                "message.taczweaponblueprints.research."
                                        + research.resultKey().orElseThrow()),
                        BAD,
                        presentation.actionEnabled());
                case IDLE -> throw new IllegalStateException("idle feedback has a blueprint");
            };
        }
        ResearchTreeFeedbackState.Snapshot selection = selectionFeedback.snapshot();
        if (selection.blueprintId().filter(node.blueprintId()::equals).isPresent()
                && selection.status() == ResearchTreeFeedbackState.Status.FAILURE) {
            boolean throttled = selection.resultKey()
                    .filter("request_throttled"::equals)
                    .isPresent();
            return new SelectedNodeUi(
                    Component.translatable(
                            throttled
                                    ? "message.taczweaponblueprints.research.request_throttled"
                                    : "gui.taczweaponblueprints.research_bench.tree.selection.selection_failed"),
                    BAD,
                    false);
        }
        Component message = accessRequirementMessage(presentation).orElseGet(() ->
                Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.selection."
                                + presentation.message().name().toLowerCase(Locale.ROOT)));
        return new SelectedNodeUi(
                message,
                selectedNodeMessageColor(presentation.message()),
                presentation.actionEnabled()
                        && researchFeedback.snapshot().status()
                                != ResearchTreeFeedbackState.Status.PENDING);
    }

    private Optional<Component> accessRequirementMessage(
            ResearchTreeSelectedNodePresenter.Presentation presentation) {
        if (!presentation.exactPreview()) {
            return Optional.empty();
        }
        ResearchSelectionPreview current = menu.preview();
        return switch (current.accessSummary().kind()) {
            case WORKBENCH_TIER -> current.accessSummary().requiredTier().map(tier ->
                    Component.translatable(
                            "gui.taczweaponblueprints.research_bench.tree.selection.requires_workbench",
                            Component.translatable(workbenchNameKey(tier))));
            case PROGRESSION_GATE -> current.accessSummary().messageKey()
                    .map(Component::translatable);
            case NONE, POLICY_UNAVAILABLE -> Optional.empty();
        };
    }

    private List<Component> selectedProgressionLines(ResearchTreeGraph.Node node) {
        if (!ResearchTreeContextCardPolicy.hasMatchingAuthoritativePreview(
                node.blueprintId(), treeCanvas.authoritativeSelectedId(), menu.preview())) {
            return List.of();
        }
        ArrayList<Component> lines = new ArrayList<>();
        selectedTierLine(node).ifPresent(lines::add);
        selectedCraftingLine(node).ifPresent(lines::add);
        ResearchTreeSelectedNodePresenter.Presentation presentation =
                selectedNodePresentation(node);
        accessRequirementMessage(presentation).ifPresent(lines::add);
        selectedFragmentLine(node).ifPresent(lines::add);
        return List.copyOf(lines);
    }

    private Optional<Component> selectedTierLine(ResearchTreeGraph.Node node) {
        if (!ResearchTreeContextCardPolicy.hasMatchingAuthoritativePreview(
                node.blueprintId(), treeCanvas.authoritativeSelectedId(), menu.preview())) {
            return Optional.empty();
        }
        var progression = menu.preview().progression();
        return progression.currentTier().isPresent() && progression.requiredTier().isPresent()
                ? Optional.of(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.progression.tier",
                        progression.currentTier().orElseThrow().level(),
                        progression.requiredTier().orElseThrow().level()))
                : Optional.empty();
    }

    private Optional<Component> selectedCraftingLine(ResearchTreeGraph.Node node) {
        if (!ResearchTreeContextCardPolicy.hasMatchingAuthoritativePreview(
                node.blueprintId(), treeCanvas.authoritativeSelectedId(), menu.preview())) {
            return Optional.empty();
        }
        return menu.preview().progression().craftingAccess().map(access -> switch (
                access.disposition()) {
            case TIERED -> Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.progression.crafting_level",
                    access.requiredWorkbenchTier().orElseThrow().level());
            case UNRESTRICTED -> Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.progression.crafting_any");
            case DISABLED -> Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.progression.crafting_disabled");
        });
    }

    private Optional<Component> selectedFragmentLine(ResearchTreeGraph.Node node) {
        if (!ResearchTreeContextCardPolicy.hasMatchingAuthoritativePreview(
                node.blueprintId(), treeCanvas.authoritativeSelectedId(), menu.preview())) {
            return Optional.empty();
        }
        return menu.preview().progression().fragments().map(progress -> Component.translatable(
                progress.discountApplied()
                        ? "gui.taczweaponblueprints.research_bench.tree.progression.fragments_discounted"
                        : progress.complete()
                                ? "gui.taczweaponblueprints.research_bench.tree.progression.fragments_complete"
                                : "gui.taczweaponblueprints.research_bench.tree.progression.fragments",
                progress.displayedArchived(),
                progress.threshold()));
    }

    private Optional<Component> selectedProgressionSummary(ResearchTreeGraph.Node node) {
        if (!ResearchTreeContextCardPolicy.hasMatchingAuthoritativePreview(
                node.blueprintId(), treeCanvas.authoritativeSelectedId(), menu.preview())) {
            return Optional.empty();
        }
        var progression = menu.preview().progression();
        if (progression.currentTier().isEmpty() || progression.requiredTier().isEmpty()) {
            return Optional.empty();
        }
        MutableComponent summary = Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.progression.tier_compact",
                progression.currentTier().orElseThrow().level(),
                progression.requiredTier().orElseThrow().level());
        progression.fragments().ifPresent(progress -> summary.append(" · ").append(
                Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.progression.fragments_compact",
                        progress.displayedArchived(),
                        progress.threshold())));
        return Optional.of(summary);
    }

    private static String workbenchNameKey(ResearchWorkbenchTier tier) {
        return switch (tier) {
            case TIER_1 -> "block.taczweaponblueprints.research_bench";
            case TIER_2 -> "block.taczweaponblueprints.advanced_research_bench";
            case TIER_3 -> "block.taczweaponblueprints.experimental_research_bench";
        };
    }

    private int selectedNodeMessageColor(
            ResearchTreeSelectedNodePresenter.Message message) {
        return switch (message) {
            case READY, LEARNED -> GOOD;
            case CHECKING_REQUIREMENTS -> ACCENT;
            case FOLLOW_PATH -> MUTED;
            case POINTS_REQUIRED, MATERIALS_REQUIRED, INVENTORY_SPACE_REQUIRED,
                    PROGRESSION_CAPACITY_EXHAUSTED,
                    WORKBENCH_TIER_REQUIRED, PROGRESSION_GATE_REQUIRED,
                    DISCOVERY_REQUIRED, PREREQUISITES_REQUIRED -> WARN;
            case LOCKED, RESEARCH_DISABLED, COST_UNAVAILABLE, CONTENT_UNAVAILABLE,
                    REQUIREMENTS_UNAVAILABLE,
                    PATH_TOO_LARGE, ROUTE_TOO_COMPLEX, TECH_TREE_UNAVAILABLE,
                    UNSATISFIABLE -> BAD;
        };
    }

    private Component selectedNodeCostOrVisibility(
            ResearchTreeGraph.Node node,
            ResearchTreeSelectedNodePresenter.Presentation presentation) {
        if (presentation.pathPlanningFailed()) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.path.cost_unavailable");
        }
        return node.visibility().revealsResearchSummary() || presentation.exactPreview()
                ? presentation.costBypassed()
                        ? Component.translatable(
                                "gui.taczweaponblueprints.research_bench.tree.cost_bypassed")
                        : selectedResearchCostSummary(presentation)
                : Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.visibility."
                                + node.visibility().serializedName());
    }

    private Component selectedResearchCostSummary(
            ResearchTreeSelectedNodePresenter.Presentation presentation) {
        if (presentation.pointsEnabled() && presentation.materialsEnabled()
                && presentation.ingredientTypeCount() > 0) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.cost.points_and_items",
                    presentation.pointCost());
        }
        if (presentation.pointsEnabled()) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.cost",
                    presentation.pointCost());
        }
        if (presentation.materialsEnabled() && presentation.ingredientTypeCount() > 0) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.cost.items_only");
        }
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.cost.free");
    }

    private Component selectedPointBalanceLabel(
            ResearchTreeSelectedNodePresenter.Presentation presentation) {
        if (presentation.costBypassed()) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.cost_bypassed");
        }
        if (!presentation.pointsEnabled()) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.cost.points_disabled");
        }
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.tooltip.balance",
                presentation.pointBalance());
    }

    private Component selectedRelationshipSummary(
            ResearchTreeSelectedNodePresenter.Presentation presentation) {
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.card.relationships",
                presentation.directRequirementCount(),
                presentation.immediateUnlockCount());
    }

    private Component selectedPathSummary(
            ResearchTreeSelectedNodePresenter.Presentation presentation) {
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.path.summary",
                presentation.unlockCount());
    }

    private Component selectedPathDisplaySummary(
            ResearchTreeSelectedNodePresenter.Presentation presentation) {
        MutableComponent summary = Component.empty().append(selectedPathSummary(presentation));
        if (presentation.additionalIngredientTypes() > 0) {
            summary.append(" · ").append(Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.path.more_materials",
                    presentation.additionalIngredientTypes()));
        }
        return summary;
    }

    private Component researchActionLabel(
            ResearchTreeSelectedNodePresenter.Presentation presentation) {
        return presentation.pathPurchase()
                ? Component.translatable(
                        "gui.taczweaponblueprints.research_bench.research_path",
                        presentation.unlockCount())
                : Component.translatable(
                        "gui.taczweaponblueprints.research_bench.research");
    }

    private Optional<Component> selectedRequirementGroupSummary(
            ResearchTreeGraph.Node node) {
        return ResearchTreeRequirementText.summary(
                treeCanvas.graph(), node.blueprintId());
    }

    private Component selectedNodeOverviewSummary(
            ResearchTreeGraph.Node node,
            ResearchTreeSelectedNodePresenter.Presentation presentation) {
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.card.summary",
                selectedNodeCostOrVisibility(node, presentation),
                presentation.directRequirementCount(),
                presentation.immediateUnlockCount());
    }

    private Component emptyTreeMessage() {
        ResearchTreePresentationContract.EmptyTreeState state =
                ResearchTreePresentationContract.emptyTreeState(
                        treeNavigation.browseView(),
                        treeProjections.publication().graph().nodes().isEmpty());
        return Component.translatable(switch (state) {
            case EMPTY_OVERVIEW ->
                    "gui.taczweaponblueprints.research_bench.tree.overview.empty";
            case EMPTY_TECH_TREE ->
                    "gui.taczweaponblueprints.research_bench.tree.tech_tree.empty";
            case EMPTY_PUBLICATION ->
                    "gui.taczweaponblueprints.research_bench.search.unavailable";
        });
    }

    private void renderBrowseLabels(GuiGraphics graphics) {
        int nextMessageY = TREE_Y + 10;
        if (!guidanceVisible && researchTreePublicationRejected) {
            nextMessageY = renderRejectedPublicationWarning(
                    graphics,
                    TREE_X + 8,
                    nextMessageY,
                    TREE_WIDTH - 16);
        }
        if (!guidanceVisible
                && !researchTreePublicationRejected
                && treeCanvas.graph().nodes().isEmpty()) {
            graphics.drawWordWrap(
                    font,
                    emptyTreeMessage(),
                    TREE_X + 8,
                    TREE_Y + 10,
                    TREE_WIDTH - 16,
                    MUTED);
        }
        if (!guidanceVisible
                && searchBox != null
                && !searchBox.getValue().isBlank()
                && treeSearch.matches().isEmpty()) {
            graphics.drawString(
                    font,
                    Component.translatable("gui.taczweaponblueprints.research_bench.search.empty"),
                    TREE_X + ResearchTreeCanvas.STICKY_GUTTER_WIDTH + 4,
                    Math.max(
                            TREE_Y + ResearchTreeCanvas.STICKY_HEADER_HEIGHT + 4,
                            nextMessageY),
                    WARN,
                    false);
        }

        Optional<ResearchTreeGraph.Node> selected = focusedTreeNode();
        if (selected.isEmpty()) {
            Component balance = researchPointBalance();
            int balanceWidth = compactBalanceWidth(balance);
            graphics.drawWordWrap(
                    font,
                    Component.translatable("gui.taczweaponblueprints.research_bench.select_hint"),
                    DETAIL_X + 8,
                    DETAIL_Y + 18,
                    210,
                    MUTED);
            drawResearchPointBalance(graphics, balance, balanceWidth);
            return;
        }

        ResearchTreeGraph.Node node = selected.orElseThrow();
        ResearchTreeSelectedNodePresenter.Presentation details = selectedNodePresentation(node);
        SelectedNodeUi selectedUi = selectedNodeUi(node, details);
        Component balance = researchPointBalance();
        int balanceWidth = compactBalanceWidth(balance);
        int balanceX = DETAIL_X + DETAIL_WIDTH - balanceWidth - 6;
        int nameWidth = Math.max(48, balanceX - (DETAIL_X + 28) - 4);
        ItemStack icon = treeCanvas.icon(node.blueprintId());
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, DETAIL_X + 6, DETAIL_Y + 6);
        }
        graphics.drawString(
                font,
                clipped(nodeName(node), nameWidth),
                DETAIL_X + 28,
                DETAIL_Y + 5,
                TEXT,
                false);
        graphics.drawString(
                font,
                clipped(selectedUi.message(), 176),
                DETAIL_X + 38,
                DETAIL_Y + 18,
                selectedUi.color(),
                false);
        ResearchTreeStatusGlyph.render(
                graphics,
                DETAIL_X + 28,
                DETAIL_Y + 19,
                nodeBorderColor(node),
                ResearchTreeStatusGlyph.forSymbol(nodeStatusSymbol(node)));
        if (node.visibility().revealsResearchSummary() || details.exactPreview()) {
            graphics.drawString(
                    font,
                    clipped(selectedNodeCostOrVisibility(node, details), 92),
                    DETAIL_X + 28,
                    DETAIL_Y + 30,
                    node.visibility().revealsExactPolicy()
                            ? details.pointsSatisfied() ? MUTED : BAD
                            : MUTED,
                    false);
        } else {
            graphics.drawString(
                    font,
                    clipped(Component.translatable(
                            "gui.taczweaponblueprints.research_bench.tree.visibility."
                                    + node.visibility().serializedName()), 64),
                    DETAIL_X + 28,
                    DETAIL_Y + 30,
                    MUTED,
                    false);
        }
        renderRelationSummary(graphics, node);
        drawResearchPointBalance(graphics, balance, balanceWidth);
    }

    private int renderRejectedPublicationWarning(
            GuiGraphics graphics,
            int x,
            int y,
            int maximumWidth) {
        Component message = rejectedPublicationMessage();
        int width = Math.max(1, maximumWidth);
        ResearchTreeScreenLayout.Rect panel = rejectedPublicationWarningBounds(
                x, y, width);
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), SECTION);
        graphics.renderOutline(panel.x(), panel.y(), panel.width(), panel.height(), WARN);
        graphics.drawWordWrap(font, message, x, y, width, WARN);
        return panel.bottom() + 4;
    }

    private Optional<ResearchTreeScreenLayout.Rect>
            fullscreenRejectedPublicationWarningBounds() {
        if (!fullscreen || guidanceVisible || !researchTreePublicationRejected
                || fullscreenOverlayLayout == null) {
            return Optional.empty();
        }
        ResearchTreeScreenLayout.Rect canvas = activeTreeLayout.canvas();
        int x = fullscreenOverlayLayout.rail().right() + 8;
        int y = fullscreenOverlayLayout.searchField().bottom() + 8;
        return Optional.of(rejectedPublicationWarningBounds(
                x,
                y,
                Math.max(1, canvas.right() - x - 8)));
    }

    private ResearchTreeScreenLayout.Rect rejectedPublicationWarningBounds(
            int x,
            int y,
            int maximumWidth) {
        int textHeight = Math.max(
                font.lineHeight,
                font.split(rejectedPublicationMessage(), maximumWidth).size()
                        * font.lineHeight);
        return new ResearchTreeScreenLayout.Rect(
                x - 4,
                y - 3,
                maximumWidth + 8,
                textHeight + 6);
    }

    private static Component rejectedPublicationMessage() {
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.display_failed");
    }

    private void renderRelationSummary(GuiGraphics graphics, ResearchTreeGraph.Node node) {
        graphics.drawString(
                font,
                clipped(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.needs_short",
                        treeCanvas.totalRequirementCount(node.blueprintId())), 36),
                102,
                DETAIL_Y + 30,
                MUTED,
                false);
        graphics.drawString(
                font,
                clipped(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.unlocks_short",
                        treeCanvas.totalUnlockCount(node.blueprintId())), 32),
                174,
                DETAIL_Y + 30,
                MUTED,
                false);
    }

    private List<ResearchTreeDetailLayout.RelationSlot> activeRelationSlots() {
        return fullscreen ? List.of() : COMPACT_RELATION_SLOTS;
    }

    private void drawRelationCard(
            GuiGraphics graphics,
            ResearchTreeDetailLayout.RelationSlot slot,
            ResearchTreeGraph.Node relation) {
        ResearchTreeScreenLayout.Rect bounds = slot.bounds();
        int relationshipColor = slot.kind() == ResearchTreeDetailLayout.RelationKind.REQUIREMENT
                ? ACCENT : 0xFF62C7D9;
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), SLOT);
        ItemStack icon = treeCanvas.icon(relation.blueprintId());
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, bounds.x(), bounds.y());
        } else {
            String label = relation.visibility().revealsName()
                    ? nodeName(relation).getString()
                    : "?";
            String initial = label.isBlank()
                    ? "?"
                    : label.substring(0, 1).toUpperCase(Locale.ROOT);
            graphics.drawCenteredString(
                    font, initial, bounds.x() + bounds.width() / 2, bounds.y() + 4, MUTED);
        }
        graphics.fill(bounds.right() - 7, bounds.bottom() - 7, bounds.right(), bounds.bottom(), SLOT);
        ResearchTreeStatusGlyph.render(
                graphics,
                bounds.right() - 7,
                bounds.bottom() - 7,
                nodeBorderColor(relation),
                ResearchTreeStatusGlyph.forSymbol(nodeStatusSymbol(relation)));
        graphics.renderOutline(
                bounds.x(), bounds.y(), bounds.width(), bounds.height(), relationshipColor);
    }

    private Component researchPointBalance() {
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.balance",
                researchPoints);
    }

    private int compactBalanceWidth(Component balance) {
        return Math.min(72, font.width(balance));
    }

    private void drawResearchPointBalance(
            GuiGraphics graphics,
            Component balance,
            int balanceWidth) {
        graphics.drawString(
                font,
                clipped(balance, balanceWidth),
                DETAIL_X + DETAIL_WIDTH - balanceWidth - 6,
                DETAIL_Y + 5,
                ACCENT,
                false);
    }

    private Component clipped(Component component, int maxWidth) {
        String value = component.getString();
        if (font.width(value) <= maxWidth) {
            return component;
        }
        return Component.literal(
                font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("..."))) + "...");
    }

    private ItemStack ingredientIcon(ResearchSelectionPreview.IngredientPreview ingredient) {
        if (!ingredient.items().isEmpty()) {
            Item item = ForgeRegistries.ITEMS.getValue(ingredient.items().get(0));
            if (item != null) {
                return new ItemStack(item);
            }
        }
        return ItemStack.EMPTY;
    }

    private Component ingredientName(ResearchSelectionPreview.IngredientPreview ingredient) {
        ItemStack icon = ingredientIcon(ingredient);
        if (!icon.isEmpty()) {
            return icon.getHoverName();
        }
        return ingredient.tag()
                .<Component>map(id -> Component.literal("#" + id))
                .orElseGet(() -> Component.literal("?"));
    }

    private void renderTreeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Optional<ResearchTechTreeLayout.PortalTarget> techPortal =
                treeCanvas.techTreePortalTargetAt(mouseX, mouseY);
        if (techPortal.isPresent()) {
            ResearchTechTreeLayout.PortalTarget target = techPortal.orElseThrow();
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(
                    target.direction() == ResearchTechTreeProjection.Direction.REQUIREMENT
                            ? "gui.taczweaponblueprints.research_bench.tree.portal.domain_requirement"
                            : "gui.taczweaponblueprints.research_bench.tree.portal.domain_unlock",
                    techTreeDomainName(target.remoteDomain())));
            if (target.connectionCount() > 1) {
                lines.add(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.portal.connections",
                        target.connectionCount()));
            }
            lines.add(Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.portal.open_domain"));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        Optional<ResearchTreeCanvas.PortalTarget> portal =
                treeCanvas.portalTargetAt(mouseX, mouseY);
        if (portal.isPresent()) {
            ResearchTreeCanvas.PortalTarget target = portal.orElseThrow();
            ResearchTreeProjection.CrossGroupLink link = target.primaryLink();
            List<Component> lines = new ArrayList<>();
            if (target.destinationGroupCount() == 1) {
                Component destination = groupName(link.remoteGroupId());
                lines.add(Component.translatable(
                        link.direction() == ResearchTreeProjection.Direction.REQUIREMENT
                                ? "gui.taczweaponblueprints.research_bench.tree.portal.requirement"
                                : "gui.taczweaponblueprints.research_bench.tree.portal.unlock",
                        destination));
            } else {
                lines.add(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.portal.groups",
                        target.destinationGroupCount()));
            }
            if (target.connectionCount() > 1) {
                lines.add(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.portal.connections",
                        target.connectionCount()));
            }
            lines.add(Component.translatable(
                    target.destinationGroupCount() == 1
                            ? "gui.taczweaponblueprints.research_bench.tree.portal.open"
                            : "gui.taczweaponblueprints.research_bench.tree.portal.open_connected"));
            graphics.renderComponentTooltip(
                    font,
                    lines,
                    mouseX,
                    mouseY);
            return;
        }
        Optional<Component> category = treeCanvas.categoryHeaderAt(mouseX, mouseY);
        if (category.isPresent()) {
            graphics.renderComponentTooltip(font, List.of(category.orElseThrow()), mouseX, mouseY);
            return;
        }
        Optional<ResearchTreeGraph.Node> hovered = treeCanvas.nodeAt(mouseX, mouseY);
        if (hovered.isEmpty()) {
            return;
        }
        ResearchTreeGraph.Node node = hovered.orElseThrow();
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(nodeName(node));
        lines.add(nodeStatus(node));
        lines.addAll(selectedProgressionLines(node));
        graphics.renderComponentTooltip(
                font,
                List.copyOf(lines),
                mouseX,
                mouseY);
    }

    private int nodeBorderColor(ResearchTreeGraph.Node node) {
        return switch (ResearchTreePresentationContract.playerStateFamily(
                node, canAfford(node))) {
            case LEARNED -> GOOD;
            case AVAILABLE -> ACCENT;
            case LOCKED -> WARN;
            case HIDDEN_OR_UNAVAILABLE -> MUTED;
        };
    }

    private ResearchTreePresentationContract.StatusSymbol nodeStatusSymbol(
            ResearchTreeGraph.Node node) {
        return ResearchTreePresentationContract.statusSymbol(node, canAfford(node));
    }

    private ResearchTreePresentationContract.StatusSymbol graphNodeStatusSymbol(
            ResearchTreeGraph.Node node) {
        return ResearchTreePresentationContract.graphStatusSymbol(node, canAfford(node));
    }

    private Component nodeStatus(ResearchTreeGraph.Node node) {
        if (node.availability() == ResearchTreeGraph.Availability.AVAILABLE && !canAfford(node)) {
            return Component.translatable("gui.taczweaponblueprints.research_bench.tree.status.points");
        }
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.status."
                        + node.availability().name().toLowerCase(Locale.ROOT));
    }

    private boolean canAfford(ResearchTreeGraph.Node node) {
        if (node.learned() || node.pointCost() == 0) {
            return true;
        }
        if (ResearchTreeContextCardPolicy.hasMatchingAuthoritativePreview(
                node.blueprintId(), treeCanvas.authoritativeSelectedId(), menu.preview())) {
            if (menu.preview().pathPlanningState()
                    != ResearchSelectionPreview.PathPlanningState.NONE) {
                return false;
            }
            return menu.preview().creativeBypass()
                    || menu.preview().pointBalance() >= menu.preview().pointCost();
        }
        BlueprintJournalEntry entry = journalEntries.get(node.blueprintId());
        return entry != null && entry.canAffordPoints();
    }

    private Optional<ResearchTreeRecommendationEngine.Recommendation>
            recommendedNextBlueprint() {
        ResearchTreeGraph publicGraph = treeProjections.publication().graph();
        Set<ResourceLocation> navigable = publicGraph.nodes().stream()
                .filter(this::isSearchNavigable)
                .map(ResearchTreeGraph.Node::blueprintId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<ResourceLocation> preferred = treeCanvas.graph().nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Optional<ResourceLocation> trackedTarget = ClientResearchPlannerState.targetId();
        Optional<ResearchTreePlanner.Plan> tracked = researchPlan
                .filter(plan -> !plan.complete());
        if (trackedTarget.isPresent() && tracked.isEmpty()) {
            return Optional.empty();
        }
        if (tracked.isPresent()) {
            ResearchTreePlanner.Plan plan = tracked.orElseThrow();
            Optional<ResearchGuidanceSnapshot> guidance =
                    ClientResearchGuidanceState.currentSnapshot()
                            .filter(snapshot -> snapshot.targetId().equals(plan.targetId()));
            if (ClientResearchGuidanceState.pending()
                    || ClientResearchGuidanceState.unavailable()
                    || guidance.filter(snapshot -> !snapshot.routeAvailable()).isPresent()) {
                return Optional.empty();
            }
            Set<ResourceLocation> plannedIds = plan.pathNodeIds();
            ResearchTreeGraph plannedGraph = publicGraph.inducedSubgraph(plannedIds);
            Set<ResourceLocation> plannedNavigable = navigable.stream()
                    .filter(plannedIds::contains)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return plan.nextStepId().flatMap(nextStep ->
                    ResearchTreeRecommendationEngine.recommendTrackedStep(
                            plannedGraph, plannedNavigable, nextStep, researchPoints));
        }
        return ResearchTreeRecommendationEngine.recommend(
                publicGraph, navigable, preferred, researchPoints);
    }

    private Component recommendationDescription(
            ResearchTreeRecommendationEngine.Recommendation recommendation) {
        ResearchTreeGraph.Node node = treeProjections.publication().graph()
                .node(recommendation.blueprintId())
                .orElseThrow();
        return switch (publishedResearchCostMode()) {
            case POINTS_ONLY -> switch (recommendation.reason()) {
                case OPENS_PATHS -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.recommendation.opens_paths",
                        nodeName(node),
                        recommendation.immediateUnlockCount(),
                        recommendation.pointCost());
                case WITHIN_POINT_BUDGET -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.recommendation.within_budget",
                        nodeName(node),
                        recommendation.pointCost());
                case LOWEST_COST -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.recommendation.lowest_cost",
                        nodeName(node),
                        recommendation.pointCost());
            };
            case ITEMS_ONLY -> switch (recommendation.reason()) {
                case OPENS_PATHS -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.recommendation.opens_paths.items_only",
                        nodeName(node),
                        recommendation.immediateUnlockCount(),
                        node.ingredientTypeCount());
                case WITHIN_POINT_BUDGET -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.recommendation.within_budget.items_only",
                        nodeName(node),
                        node.ingredientTypeCount());
                case LOWEST_COST -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.recommendation.lowest_cost.items_only",
                        nodeName(node),
                        node.ingredientTypeCount());
            };
            case POINTS_AND_ITEMS -> switch (recommendation.reason()) {
                case OPENS_PATHS -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.recommendation.opens_paths.points_and_items",
                        nodeName(node),
                        recommendation.immediateUnlockCount(),
                        recommendation.pointCost(),
                        node.ingredientTypeCount());
                case WITHIN_POINT_BUDGET -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.recommendation.within_budget.points_and_items",
                        nodeName(node),
                        recommendation.pointCost(),
                        node.ingredientTypeCount());
                case LOWEST_COST -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.recommendation.lowest_cost.points_and_items",
                        nodeName(node),
                        recommendation.pointCost(),
                        node.ingredientTypeCount());
            };
        };
    }

    private MutableComponent recommendationNarration() {
        return recommendedNextBlueprint()
                .map(this::recommendationDescription)
                .map(description -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.recommendation.narration",
                        description))
                .orElseGet(() -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.recommendation.none"));
    }

    private void focusRecommendedBlueprint() {
        recommendedNextBlueprint().ifPresent(recommendation -> {
            cancelTreeInteraction();
            fullscreenOverlayState.clearPinnedNode();
            if (fullscreen) {
                markFullscreenRailUsed();
            }
            navigateToPublicNode(recommendation.blueprintId(), true);
            updateWidgets();
        });
    }

    private void toggleAffordableNow() {
        if (!menu.routeGuidanceAvailable() || researchTreePublicationRejected) {
            return;
        }
        ClientResearchAffordabilityState.Snapshot current =
                ClientResearchAffordabilityState.snapshot();
        ClientResearchState.Publication publication = ClientResearchState.publication();
        ClientResearchAffordabilityState.setEnabled(
                !current.enabled(),
                treeProjections.publication().graph(),
                publication.generation());
        affordabilityRetryTicks = 0;
        applyAffordabilityFilterToCanvas();
        requestNextAffordabilityBatch();
        uiUpdates.invalidateWidgets();
        updateWidgets();
    }

    private Component affordabilityDescription(
            ClientResearchAffordabilityState.Snapshot affordability) {
        if (!affordability.enabled()) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.affordable.off");
        }
        if (affordability.totalTargets() == 0) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.affordable.empty");
        }
        String suffix = affordability.complete() ? "complete" : "checking";
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.affordable." + suffix,
                affordability.affordableTargets(),
                affordability.checkedTargets(),
                affordability.totalTargets());
    }

    private MutableComponent affordabilityNarration() {
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.affordable.narration",
                affordabilityDescription(ClientResearchAffordabilityState.snapshot()));
    }

    private boolean nodeIsTrackable(ResourceLocation blueprintId) {
        return treeProjections.publication().graph().node(blueprintId)
                .filter(node -> node.visibility().revealsIdentity())
                .filter(this::isSearchNavigable)
                .isPresent();
    }

    private void toggleTrackedResearch() {
        Optional<ResourceLocation> target = fullscreen
                ? fullscreenOverlayState.pinnedNodeId()
                : focusedTreeNode().map(ResearchTreeGraph.Node::blueprintId);
        if (target.isEmpty() || !nodeIsTrackable(target.orElseThrow())) {
            return;
        }
        ResourceLocation targetId = target.orElseThrow();
        if (ClientResearchPlannerState.targetId().filter(targetId::equals).isPresent()) {
            ClientResearchPlannerState.clear();
            ClientResearchGuidanceState.clear();
        } else {
            ClientResearchPlannerState.track(
                    treeProjections.publication().graph(), targetId);
        }
        refreshResearchPlan();
        treeCanvas.setTrackedPlan(researchPlan.orElse(null));
        fullscreenCardWidgetState = null;
        updateWidgets();
    }

    private Component trackResearchDescription(ResourceLocation candidateId) {
        if (candidateId == null || !nodeIsTrackable(candidateId)) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.plan.unavailable");
        }
        ResearchTreeGraph.Node candidate = treeProjections.publication().graph()
                .node(candidateId)
                .orElseThrow();
        Optional<ResourceLocation> tracked = ClientResearchPlannerState.targetId();
        if (tracked.filter(candidateId::equals).isPresent()) {
            Component summary = researchPlan
                    .map(this::researchPlanSummary)
                    .orElseGet(() -> Component.translatable(
                            "gui.taczweaponblueprints.research_bench.tree.plan.no_next"));
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.plan.untrack.tooltip",
                    nodeName(candidate),
                    summary);
        }
        if (tracked.isPresent()) {
            Component previous = treeProjections.publication().graph()
                    .node(tracked.orElseThrow())
                    .map(this::nodeName)
                    .orElse(Component.literal("?"));
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.plan.replace.tooltip",
                    previous,
                    nodeName(candidate));
        }
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.plan.track.tooltip",
                nodeName(candidate));
    }

    private Component researchPlanSummary(ResearchTreePlanner.Plan plan) {
        if (plan.complete()) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.plan.complete");
        }
        Optional<ResearchGuidanceSnapshot> guidance =
                ClientResearchGuidanceState.currentSnapshot()
                        .filter(snapshot -> snapshot.targetId().equals(plan.targetId()));
        if (guidance.isPresent()) {
            Component nextStep = plan.nextStepId()
                    .flatMap(treeProjections.publication().graph()::node)
                    .map(this::nodeName)
                    .orElseGet(() -> Component.translatable(
                            "gui.taczweaponblueprints.research_bench.tree.plan.no_next"));
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.goal.plan_summary",
                    plan.remainingSteps(),
                    researchGoalProgressSummary(
                            ResearchGoalProgressPresenter.present(guidance)),
                    nextStep);
        }
        if (ClientResearchGuidanceState.unavailable()) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.goal.route_unavailable");
        }
        if (ClientResearchGuidanceState.pending()
                || ClientResearchGuidanceState.snapshot()
                        .filter(snapshot -> snapshot.targetId().equals(plan.targetId()))
                        .isPresent()) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.goal.plan_checking",
                    plan.remainingSteps());
        }
        Component nextStep = plan.nextStepId()
                .flatMap(treeProjections.publication().graph()::node)
                .map(this::nodeName)
                .orElseGet(() -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.plan.no_next"));
        String partial = plan.costComplete() ? "" : ".partial";
        return switch (publishedResearchCostMode()) {
            case POINTS_ONLY -> Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.plan.summary.points_only"
                            + partial,
                    plan.remainingSteps(),
                    plan.remainingPoints(),
                    nextStep);
            case ITEMS_ONLY -> Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.plan.summary.items_only"
                            + partial,
                    plan.remainingSteps(),
                    plan.remainingIngredientTypes(),
                    nextStep);
            case POINTS_AND_ITEMS -> Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.plan.summary"
                            + partial,
                    plan.remainingSteps(),
                    plan.remainingPoints(),
                    plan.remainingIngredientTypes(),
                    nextStep);
        };
    }

    private ResearchGoalProgressPresenter.Presentation researchGoalProgress() {
        return ResearchGoalProgressPresenter.present(
                ClientResearchGuidanceState.currentSnapshot(),
                ClientResearchGuidanceState.unavailable());
    }

    private Component researchGoalShortStatus(
            ResearchGoalProgressPresenter.Presentation presentation) {
        String suffix = switch (presentation.status()) {
            case CHECKING -> "checking";
            case COMPLETE -> "complete";
            case READY -> "ready";
            case MISSING_POINTS -> "missing_points";
            case MISSING_MATERIALS -> "missing_materials";
            case MISSING_POINTS_AND_MATERIALS -> "missing_both";
            case TRANSACTION_BLOCKED -> "capacity_blocked";
            case POLICY_BLOCKED -> "policy_blocked";
            case ROUTE_UNAVAILABLE -> "route_unavailable";
        };
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.goal." + suffix);
    }

    private Component researchGoalProgressSummary(
            ResearchGoalProgressPresenter.Presentation presentation) {
        if (presentation.costBypassed()) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.goal.costs_bypassed");
        }
        MutableComponent summary = Component.empty();
        presentation.points().ifPresent(progress -> summary.append(Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.goal.progress.points",
                progress.available(),
                progress.required())));
        presentation.materials().ifPresent(progress -> {
            if (!summary.getString().isEmpty()) {
                summary.append(" · ");
            }
            summary.append(Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.goal.progress.materials",
                    progress.available(),
                    progress.required(),
                    presentation.missingMaterialTypes()));
        });
        if (summary.getString().isEmpty()) {
            return researchGoalShortStatus(presentation);
        }
        return summary;
    }

    private Component researchGoalButtonStatus(
            ResearchGoalProgressPresenter.Presentation presentation) {
        if (presentation.costBypassed()) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.goal.costs_bypassed");
        }
        if (presentation.status() == ResearchGoalProgressPresenter.Status.READY
                || presentation.status() == ResearchGoalProgressPresenter.Status.MISSING_POINTS
                || presentation.status()
                        == ResearchGoalProgressPresenter.Status.MISSING_MATERIALS
                || presentation.status()
                        == ResearchGoalProgressPresenter.Status.MISSING_POINTS_AND_MATERIALS) {
            MutableComponent progress = Component.empty();
            presentation.points().ifPresent(value -> progress.append(Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.goal.progress.points",
                    value.available(),
                    value.required())));
            presentation.materials().ifPresent(value -> {
                if (!progress.getString().isEmpty()) {
                    progress.append(" · ");
                }
                progress.append(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.goal.progress.materials_short",
                        value.available(),
                        value.required()));
            });
            if (!progress.getString().isEmpty()) {
                return progress;
            }
        }
        return researchGoalShortStatus(presentation);
    }

    private Component researchGoalButtonLabel(Component goalName, Component status) {
        int contentWidth = Math.max(1, researchGoalButton.getWidth() - 8);
        int decorationWidth = font.width("◆  · ");
        int nameWidth = Math.max(1, contentWidth - decorationWidth - font.width(status));
        return Component.empty()
                .append("◆ ")
                .append(clipped(goalName, nameWidth))
                .append(" · ")
                .append(status);
    }

    private Component researchGoalDescription(
            Component goalName,
            ResearchGoalProgressPresenter.Presentation presentation) {
        MutableComponent description = Component.empty()
                .append(goalName)
                .append(" — ")
                .append(researchGoalShortStatus(presentation));
        if (presentation.status() != ResearchGoalProgressPresenter.Status.CHECKING
                && presentation.status() != ResearchGoalProgressPresenter.Status.COMPLETE
                && presentation.status() != ResearchGoalProgressPresenter.Status.POLICY_BLOCKED
                && presentation.status() != ResearchGoalProgressPresenter.Status.ROUTE_UNAVAILABLE) {
            description.append("\n").append(researchGoalProgressSummary(presentation));
        }
        researchPlan.filter(plan -> !plan.complete()).ifPresent(plan -> {
            Component nextStep = plan.nextStepId()
                    .flatMap(treeProjections.publication().graph()::node)
                    .map(this::nodeName)
                    .orElseGet(() -> Component.translatable(
                            "gui.taczweaponblueprints.research_bench.tree.plan.no_next"));
            description.append("\n").append(Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.goal.route",
                    plan.remainingSteps(),
                    nextStep));
        });
        return description.append("\n").append(Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.goal.focus"));
    }

    private MutableComponent researchGoalNarration() {
        Component name = ClientResearchPlannerState.targetId()
                .flatMap(treeProjections.publication().graph()::node)
                .map(this::nodeName)
                .orElse(Component.literal("?"));
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.goal.narration",
                researchGoalDescription(name, researchGoalProgress()));
    }

    private void focusTrackedResearchGoal() {
        ClientResearchPlannerState.targetId().ifPresent(targetId -> {
            cancelTreeInteraction();
            navigateToPublicNode(targetId, true);
            if (treeCanvas.graph().node(targetId).isPresent()) {
                selectTreeNodeInPlace(targetId);
            }
            updateWidgets();
        });
    }

    private MutableComponent trackResearchNarration() {
        ResourceLocation candidate = fullscreen
                ? fullscreenOverlayState.pinnedNodeId().orElse(null)
                : focusedTreeNode().map(ResearchTreeGraph.Node::blueprintId).orElse(null);
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.plan.narration",
                trackResearchDescription(candidate));
    }

    private void focusUsefulNode() {
        Optional<ResourceLocation> target = menu.selectedBlueprint()
                .filter(id -> treeCanvas.layout().position(id).isPresent());
        if (target.isEmpty() && !treeCanvas.searchMatches().isEmpty()) {
            target = Optional.of(treeCanvas.searchMatches().iterator().next());
        }
        if (target.isEmpty()) {
            target = treeCanvas.graph().nodes().stream()
                    .filter(node -> node.availability() == ResearchTreeGraph.Availability.AVAILABLE)
                    .map(ResearchTreeGraph.Node::blueprintId)
                    .findFirst();
        }
        if (target.isEmpty() && !treeCanvas.graph().nodes().isEmpty()) {
            target = Optional.of(treeCanvas.graph().nodes().get(0).blueprintId());
        }
        target.ifPresent(treeCanvas::focusNode);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (guidanceContains(mouseX, mouseY)) {
            cancelTreeInteraction();
            clickGuidance(mouseX, mouseY, button);
            return true;
        }
        if (!fullscreen && searchOverlayContains(mouseX, mouseY)) {
            cancelTreeInteraction();
            super.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (fullscreen) {
            ResearchTreeInteractionPolicy.PointerTarget pointerTarget =
                    fullscreenPointerTarget(mouseX, mouseY);
            if (!ResearchTreeInteractionPolicy.allowsGraphHover(pointerTarget)) {
                cancelTreeInteraction();
                if (pointerTarget == ResearchTreeInteractionPolicy.PointerTarget.SIDEBAR
                        && fullscreenOverlayState.railState()
                                == ResearchTreeFullscreenOverlayState.RailState.EDGE_HANDLE) {
                    fullscreenOverlayState.revealRail();
                    railIdleTicks = 0;
                    updateWidgets();
                    return true;
                }
                if (pointerTarget != ResearchTreeInteractionPolicy.PointerTarget.NONE) {
                    dispatchFullscreenOverlayClick(pointerTarget, mouseX, mouseY, button);
                    return true;
                }
                return false;
            }
            if (treeMinimap.contains(mouseX, mouseY)) {
                cancelTreeInteraction();
                setFocused(null);
                fullscreenOverlayState.blurSearch();
                treeMinimap.beginNavigation(
                        mouseX, mouseY, button, treeCanvas.viewport());
                return true;
            }
        } else {
            if (super.mouseClicked(mouseX, mouseY, button)) {
                cancelTreeInteraction();
                return true;
            }
        }
        if (fullscreen && treeCanvas.contains(mouseX, mouseY)) {
            Optional<ResearchTechTreeLayout.PortalTarget> techPortal = button == 0
                    ? treeCanvas.techTreePortalTargetAt(mouseX, mouseY)
                    : Optional.empty();
            Optional<ResearchTreeProjection.CrossGroupLink> portal = button == 0
                    && techPortal.isEmpty() ? treeCanvas.portalAt(mouseX, mouseY)
                    : Optional.empty();
            Optional<ResearchTreeGraph.Node> node = button == 0
                    && portal.isEmpty() && techPortal.isEmpty()
                    ? treeCanvas.nodeAt(mouseX, mouseY)
                    : Optional.empty();
            if (fullscreenGesture.press(
                    mouseX,
                    mouseY,
                    button,
                    node.map(ResearchTreeGraph.Node::blueprintId).orElse(null))) {
                pendingPortalActivation = portal.orElse(null);
                pendingTechTreePortalActivation = techPortal.orElse(null);
                treeCanvas.viewport().cancelAnimation();
                setFocused(null);
                fullscreenOverlayState.blurSearch();
                beginFullscreenHold(node, button);
                return true;
            }
        } else if ((button == 0 || button == 1)
                && treeCanvas.contains(mouseX, mouseY)) {
            Optional<ResearchTechTreeLayout.PortalTarget> clickedTechPortal = button == 0
                    ? treeCanvas.techTreePortalTargetAt(mouseX, mouseY)
                    : Optional.empty();
            if (clickedTechPortal.isPresent()) {
                fullscreenOverlayState.clearPinnedNode();
                navigateThroughTechTreePortal(clickedTechPortal.orElseThrow());
                return true;
            }
            Optional<ResearchTreeProjection.CrossGroupLink> clickedPortal = button == 0
                    ? treeCanvas.portalAt(mouseX, mouseY)
                    : Optional.empty();
            if (clickedPortal.isPresent()) {
                fullscreenOverlayState.clearPinnedNode();
                navigateThroughPortal(clickedPortal.orElseThrow());
                return true;
            }
            Optional<ResearchTreeGraph.Node> clickedNode = button == 0
                    ? treeCanvas.nodeAt(mouseX, mouseY)
                    : Optional.empty();
            if (clickedNode.isEmpty() && button == 0) {
                fullscreenOverlayState.clearPinnedNode();
            }
            setFocused(null);
            if (treeCanvas.mouseClicked(mouseX, mouseY, button, this::selectTreeNode)) {
                return true;
            }
        }
        return false;
    }

    private void clickGuidance(double mouseX, double mouseY, int button) {
        if (guidanceDismissButton != null && guidanceDismissButton.visible) {
            clickFullscreenWidget(guidanceDismissButton, mouseX, mouseY, button);
        }
    }

    private boolean clickFullscreenWidget(
            GuiEventListener widget,
            double mouseX,
            double mouseY,
            int button) {
        if (!widget.mouseClicked(mouseX, mouseY, button)) {
            return false;
        }
        setFocused(widget);
        if (button == 0) {
            setDragging(true);
        }
        return true;
    }

    /** Dispatches only to the widget owned by the already-resolved frontmost overlay. */
    private void dispatchFullscreenOverlayClick(
            ResearchTreeInteractionPolicy.PointerTarget target,
            double mouseX,
            double mouseY,
            int button) {
        switch (target) {
            case GUIDANCE -> clickGuidance(mouseX, mouseY, button);
            case CONTEXT_CARD -> {
                if (researchGoalButton.visible
                        && clickFullscreenWidget(
                                researchGoalButton, mouseX, mouseY, button)) {
                    return;
                }
                if (trackResearchButton.visible
                        && clickFullscreenWidget(
                                trackResearchButton, mouseX, mouseY, button)) {
                    return;
                }
                if (returnToSelectionButton.visible
                        && clickFullscreenWidget(
                                returnToSelectionButton, mouseX, mouseY, button)) {
                    return;
                }
                if (primaryResearchButton.visible) {
                    clickFullscreenWidget(primaryResearchButton, mouseX, mouseY, button);
                }
            }
            case SEARCH -> {
                for (SearchResultButton result : searchResultButtons) {
                    if (result.visible
                            && clickFullscreenWidget(result, mouseX, mouseY, button)) {
                        return;
                    }
                }
                clickFullscreenWidget(searchBox, mouseX, mouseY, button);
            }
            case SIDEBAR -> {
                if (searchToggleButton.visible
                        && clickFullscreenWidget(searchToggleButton, mouseX, mouseY, button)) {
                    return;
                }
                if (zoomOutButton.visible
                        && clickFullscreenWidget(zoomOutButton, mouseX, mouseY, button)) {
                    return;
                }
                if (zoomInButton.visible
                        && clickFullscreenWidget(zoomInButton, mouseX, mouseY, button)) {
                    return;
                }
                if (fitButton.visible
                        && clickFullscreenWidget(fitButton, mouseX, mouseY, button)) {
                    return;
                }
                if (affordabilityButton.visible
                        && clickFullscreenWidget(
                                affordabilityButton, mouseX, mouseY, button)) {
                    return;
                }
                if (recommendationButton.visible
                        && clickFullscreenWidget(
                                recommendationButton, mouseX, mouseY, button)) {
                    return;
                }
                if (railPinButton.visible
                        && clickFullscreenWidget(railPinButton, mouseX, mouseY, button)) {
                    return;
                }
                if (helpButton.visible
                        && clickFullscreenWidget(helpButton, mouseX, mouseY, button)) {
                    return;
                }
                for (RailEntryButton entry : sidebarButtons) {
                    if (entry.visible
                            && clickFullscreenWidget(entry, mouseX, mouseY, button)) {
                        return;
                    }
                }
                if (button == 0) {
                    sidebarButtons.stream()
                            .filter(entry -> entry.visible && entry.labelContains(mouseX, mouseY))
                            .findFirst()
                            .ifPresent(entry -> {
                                setFocused(entry);
                                entry.onPress();
                            });
                }
            }
            case CLOSE -> {
                clickFullscreenWidget(fullscreenButton, mouseX, mouseY, button);
            }
            case GRAPH_ELEMENT, GRAPH_BACKGROUND, NONE -> {
                // Graph-owned targets are handled by the gesture path below.
            }
        }
    }

    private void navigateThroughPortal(ResearchTreeProjection.CrossGroupLink portal) {
        if (!ResearchTreePresentationContract.legacyBrowseViewsVisible()
                || !treeProjections.isPublishedCrossGroupLink(portal)
                || treeCanvas.graph().node(portal.localNodeId()).isEmpty()) {
            return;
        }
        ResearchTreePresentation presentation = treeProjections.publication().presentation();
        Optional<ResearchTreePresentation.Membership> remoteMembership =
                presentation.membership(portal.remoteNodeId());
        if (remoteMembership.isEmpty()
                || !remoteMembership.orElseThrow().groupId().equals(portal.remoteGroupId())) {
            return;
        }
        saveActiveCamera();
        treeNavigation.setBrowseView(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                presentation);
        treeNavigation.selectGroup(portal.remoteGroupId(), presentation);
        applyActiveProjection(portal.remoteNodeId());
        ensureSelectedSidebarVisible();
        treeCanvas.focusNode(portal.remoteNodeId());
        updateWidgets();
    }

    private void navigateThroughTechTreePortal(ResearchTechTreeLayout.PortalTarget target) {
        if (!isTechTreeView() || target == null
                || treeCanvas.graph().node(target.localNodeId()).isEmpty()) {
            return;
        }
        Domain currentDomain = techTreeNavigation.selectedDomain().orElse(null);
        if (currentDomain == null) {
            return;
        }
        ResearchTechTreeLayout currentLayout = treeProjections.techTreeLayouts()
                .layout(currentDomain).orElse(null);
        if (currentLayout == null || currentLayout.portals().stream()
                .map(ResearchTechTreeLayout.BoundaryPortal::target)
                .noneMatch(target::equals)) {
            return;
        }
        ResearchTechTreeProjection.BoundaryLink link = target.primaryLink();
        ResearchTechTreeRelationshipIndex.NavigationTarget navigation =
                treeProjections.techTreeProjections().relationships().navigationTo(
                        currentDomain,
                        target.localNodeId(),
                        target.remoteDomain(),
                        link.remoteNodeId()).filter(value ->
                                value.direction() == target.direction()).orElse(null);
        if (navigation == null) {
            return;
        }
        ResearchTechTreeProjection remoteProjection = treeProjections.techTreeProjections()
                .projection(navigation.remoteDomain()).orElse(null);
        if (remoteProjection == null
                || remoteProjection.graph().node(navigation.remoteNodeId()).isEmpty()) {
            return;
        }
        saveActiveCamera();
        techTreeNavigation.selectNode(
                navigation.remoteNodeId(), treeProjections.techTreeProjections());
        applyActiveProjection(navigation.remoteNodeId());
        ensureSelectedSidebarVisible();
        treeCanvas.focusNode(navigation.remoteNodeId());
        updateWidgets();
    }

    private Optional<ResearchTreeGraph.Node> relationNode(
            ResearchTreeDetailLayout.RelationSlot relationSlot) {
        Optional<ResearchTreeGraph.Node> focused = focusedTreeNode();
        if (focused.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation focusedId = focused.orElseThrow().blueprintId();
        return ResearchTreeDetailLayout.relationTarget(
                relationSlot,
                treeCanvas.directRequirements(focusedId),
                treeCanvas.directUnlocks(focusedId)).flatMap(treeCanvas.graph()::node);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY) {
        if (fullscreen && treeMinimap.dragNavigation(
                mouseX, mouseY, button, treeCanvas.viewport())) {
            return true;
        }
        if (fullscreen && fullscreenGesture.ownsButton(button)) {
            ResearchTreeGestureTracker.Movement movement =
                    fullscreenGesture.move(mouseX, mouseY);
            if (movement == ResearchTreeGestureTracker.Movement.STARTED_DRAG
                    || movement == ResearchTreeGestureTracker.Movement.DRAGGING) {
                fullscreenHoldActivation.cancel();
                treeCanvas.panByScreenDelta(dragX, dragY);
            }
            return true;
        }
        if (treeCanvas.mouseDragged(button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (fullscreen && treeMinimap.endNavigation(button)) {
            return true;
        }
        if (fullscreen && fullscreenGesture.active()) {
            ResearchTreeProjection.CrossGroupLink portal = pendingPortalActivation;
            ResearchTechTreeLayout.PortalTarget techPortal =
                    pendingTechTreePortalActivation;
            ResearchTreeGestureTracker.Outcome outcome =
                    fullscreenGesture.release(mouseX, mouseY, button);
            if (fullscreenGesture.active()) {
                return super.mouseReleased(mouseX, mouseY, button);
            }
            pendingPortalActivation = null;
            pendingTechTreePortalActivation = null;
            boolean holdActivated = finishFullscreenHold(outcome, Util.getMillis());
            if (!holdActivated) {
                handleFullscreenGestureOutcome(outcome, portal, techPortal);
            }
            return true;
        }
        if (treeCanvas.mouseReleased(button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void handleFullscreenGestureOutcome(
            ResearchTreeGestureTracker.Outcome outcome,
            ResearchTreeProjection.CrossGroupLink portal,
            ResearchTechTreeLayout.PortalTarget techPortal) {
        switch (outcome.type()) {
            case NODE_CLICK -> outcome.nodeId().ifPresent(this::activateFullscreenNode);
            case BACKGROUND_CLICK -> {
                fullscreenOverlayState.clearPinnedNode();
                if (portal != null) {
                    navigateThroughPortal(portal);
                } else if (techPortal != null) {
                    navigateThroughTechTreePortal(techPortal);
                } else {
                    updateWidgets();
                }
            }
            case PAN_END -> {
                // Camera movement does not activate or purchase a node.
            }
            case NONE -> {
                // A middle-button press without movement intentionally does nothing.
            }
        }
    }

    private void activateFullscreenNode(ResourceLocation blueprintId) {
        Optional<ResearchTreeGraph.Node> selected = treeCanvas.graph().node(blueprintId);
        if (selected.isEmpty()) {
            return;
        }
        fullscreenOverlayState.pinNode(blueprintId);
        selectTreeNodeInPlace(blueprintId);
    }

    private void beginFullscreenHold(
            Optional<ResearchTreeGraph.Node> node,
            int button) {
        fullscreenHoldActivation.cancel();
        if (button != ResearchTreeGestureTracker.LEFT_BUTTON || node.isEmpty()) {
            return;
        }
        ResourceLocation blueprintId = node.orElseThrow().blueprintId();
        if (canHoldToResearch(blueprintId)) {
            fullscreenHoldActivation.begin(
                    blueprintId,
                    Util.getMillis(),
                    ModConfigs.RESEARCH_TREE_CLIENT.holdDurationMillis());
        }
    }

    private boolean finishFullscreenHold(
            ResearchTreeGestureTracker.Outcome gesture,
            long nowMillis) {
        ResearchTreeHoldActivationController.Snapshot hold =
                fullscreenHoldActivation.snapshot(nowMillis);
        if (hold.status() == ResearchTreeHoldActivationController.Status.ACTIVATED) {
            return fullscreenHoldActivation.release();
        }
        if (hold.status() != ResearchTreeHoldActivationController.Status.HOLDING) {
            return false;
        }
        boolean sameNodeClick = gesture.type() == ResearchTreeGestureTracker.Type.NODE_CLICK
                && gesture.nodeId().equals(hold.blueprintId());
        if (!sameNodeClick || !canHoldToResearch(hold.blueprintId().orElseThrow())) {
            fullscreenHoldActivation.cancel();
            return false;
        }
        if (fullscreenHoldActivation.advance(nowMillis)
                == ResearchTreeHoldActivationController.Outcome.ACTIVATE) {
            requestResearch();
        }
        return fullscreenHoldActivation.release();
    }

    private boolean canHoldToResearch(ResourceLocation blueprintId) {
        if (researchTreePublicationRejected
                || !fullscreen
                || !ModConfigs.RESEARCH_TREE_CLIENT.holdToResearchEnabled()
                || fullscreenOverlayState.pinnedNodeId()
                        .filter(blueprintId::equals)
                        .isEmpty()) {
            return false;
        }
        return treeCanvas.graph().node(blueprintId)
                .map(node -> selectedNodeUi(node, selectedNodePresentation(node)).actionEnabled())
                .orElse(false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (searchOverlayContains(mouseX, mouseY)) {
            return true;
        }
        cancelTreeInteraction();
        if (guidanceContains(mouseX, mouseY)) {
            return true;
        }
        if (fullscreen) {
            ResearchTreeInteractionPolicy.PointerTarget pointerTarget =
                    fullscreenPointerTarget(mouseX, mouseY);
            if (treeMinimap.contains(mouseX, mouseY)
                    && ResearchTreeInteractionPolicy.allowsGraphHover(pointerTarget)) {
                return true;
            }
            ResearchTreeInteractionPolicy.ScrollTarget scrollTarget =
                    ResearchTreeInteractionPolicy.scrollTarget(
                            pointerTarget,
                            false);
            if (scrollTarget == ResearchTreeInteractionPolicy.ScrollTarget.SIDEBAR) {
                markFullscreenRailUsed();
                int maximumScroll = ResearchTreeFullscreenRailLayout.maximumGroupScroll(
                        sidebarButtons.size(),
                        sidebarItemCount(),
                        ResearchTreePresentationContract.browseViewSelectorVisible());
                if (delta != 0.0D) {
                    sidebarScroll = Math.max(
                            0,
                            Math.min(maximumScroll, sidebarScroll + (delta < 0.0D ? 1 : -1)));
                }
                updateSidebarButtons(true);
                return true;
            }
            if (scrollTarget == ResearchTreeInteractionPolicy.ScrollTarget.GRAPH) {
                if (treeCanvas.mouseScrolled(mouseX, mouseY, delta)) {
                    updateWidgets();
                    return true;
                }
            } else if (scrollTarget == ResearchTreeInteractionPolicy.ScrollTarget.BLOCKED
                    || scrollTarget == ResearchTreeInteractionPolicy.ScrollTarget.CONTEXT_CARD) {
                return true;
            } else if (scrollTarget == ResearchTreeInteractionPolicy.ScrollTarget.NONE) {
                return super.mouseScrolled(mouseX, mouseY, delta);
            }
        }
        if (treeCanvas.mouseScrolled(mouseX, mouseY, delta)) {
            updateWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean searchKeyboardFocused = getFocused() == searchBox
                || getFocused() instanceof SearchResultButton;
        if (!searchKeyboardFocused) {
            cancelTreeInteraction();
        }
        if (getFocused() instanceof SearchResultButton resultButton
                && (keyCode == GLFW.GLFW_KEY_ENTER
                        || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            resultButton.onPress();
            return true;
        }
        if (isTechTreeView()
                && !searchKeyboardFocused
                && (keyCode == GLFW.GLFW_KEY_PAGE_UP
                        || keyCode == GLFW.GLFW_KEY_PAGE_DOWN)
                && cycleTechTreeDomain(
                        keyCode == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1)) {
            return true;
        }
        if (fullscreen) {
            boolean searchShortcut = keyCode == GLFW.GLFW_KEY_F && hasControlDown()
                    || keyCode == GLFW.GLFW_KEY_SLASH && getFocused() != searchBox;
            if (searchShortcut) {
                openFullscreenSearch(true);
                return true;
            }
            boolean searchFocused = searchKeyboardFocused;
            if (!searchFocused && keyCode == GLFW.GLFW_KEY_F && !hasControlDown()) {
                fitTree();
                return true;
            }
            if (!searchFocused
                    && (keyCode == GLFW.GLFW_KEY_EQUAL
                            || keyCode == GLFW.GLFW_KEY_KP_ADD)) {
                zoomTree(1.0D);
                return true;
            }
            if (!searchFocused
                    && (keyCode == GLFW.GLFW_KEY_MINUS
                            || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT)) {
                zoomTree(-1.0D);
                return true;
            }
            if (!searchFocused
                    && getFocused() == null
                    && (keyCode == GLFW.GLFW_KEY_ENTER
                            || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
                Optional<ResourceLocation> focusedNode = treeCanvas.focusedId();
                if (focusedNode.isPresent()) {
                    ResourceLocation blueprintId = focusedNode.orElseThrow();
                    selectTreeNodeInPlace(blueprintId);
                    return true;
                }
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelTreeInteraction();
                ResearchTreeFullscreenOverlayState.EscapeResult escape =
                        fullscreenOverlayState.escape(true);
                switch (escape) {
                    case CLOSED_SEARCH -> closeFullscreenSearch();
                    case DISMISSED_GUIDANCE -> {
                        guidanceVisible = false;
                        GUIDANCE_PREFERENCE.dismiss();
                        updateWidgets();
                    }
                    case CLOSED_CARD -> {
                        updateWidgets();
                    }
                    case EXIT_FULLSCREEN -> closePermanentFullscreen();
                    case DEFAULT -> {
                        return super.keyPressed(keyCode, scanCode, modifiers);
                    }
                }
                return true;
            }
        }
        if (fullscreen
                && getFocused() != null
                && getFocused() != searchBox
                && !(getFocused() instanceof SearchResultButton)) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (searchBox != null) {
            ResearchTreeInteractionPolicy.KeyIntent intent = keyIntent(keyCode);
            ResearchTreeInteractionPolicy.KeyboardTarget target =
                    ResearchTreeInteractionPolicy.route(
                            searchKeyboardFocused,
                            !treeSearch.matches().isEmpty(),
                            intent);
            if (target == ResearchTreeInteractionPolicy.KeyboardTarget.SEARCH_RESULTS) {
                if (intent == ResearchTreeInteractionPolicy.KeyIntent.UP
                        || intent == ResearchTreeInteractionPolicy.KeyIntent.DOWN) {
                    cycleSearch(keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1);
                    return true;
                }
            } else if (target == ResearchTreeInteractionPolicy.KeyboardTarget.SEARCH_SELECTION) {
                treeSearch.commit().ifPresent(this::commitSearchResult);
                return true;
            } else if (target == ResearchTreeInteractionPolicy.KeyboardTarget.TREE
                    && getFocused() == null) {
                ResearchTreeNavigator.Direction direction = switch (keyCode) {
                    case GLFW.GLFW_KEY_UP -> ResearchTreeNavigator.Direction.UP;
                    case GLFW.GLFW_KEY_DOWN -> ResearchTreeNavigator.Direction.DOWN;
                    case GLFW.GLFW_KEY_LEFT -> ResearchTreeNavigator.Direction.LEFT;
                    case GLFW.GLFW_KEY_RIGHT -> ResearchTreeNavigator.Direction.RIGHT;
                    default -> null;
                };
                if (direction != null && moveKeyboardCursor(direction)) {
                    return true;
                }
            } else if (target
                    == ResearchTreeInteractionPolicy.KeyboardTarget.TREE_SELECTION
                    && getFocused() == null) {
                Optional<ResourceLocation> focusedNode = treeCanvas.focusedId();
                if (focusedNode.isPresent()) {
                    selectTreeNodeInPlace(focusedNode.orElseThrow());
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static ResearchTreeInteractionPolicy.KeyIntent keyIntent(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> ResearchTreeInteractionPolicy.KeyIntent.UP;
            case GLFW.GLFW_KEY_DOWN -> ResearchTreeInteractionPolicy.KeyIntent.DOWN;
            case GLFW.GLFW_KEY_LEFT -> ResearchTreeInteractionPolicy.KeyIntent.LEFT;
            case GLFW.GLFW_KEY_RIGHT -> ResearchTreeInteractionPolicy.KeyIntent.RIGHT;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER ->
                    ResearchTreeInteractionPolicy.KeyIntent.ENTER;
            default -> ResearchTreeInteractionPolicy.KeyIntent.OTHER;
        };
    }

    @Override
    protected void updateNarrationState(NarrationElementOutput output) {
        super.updateNarrationState(output);
        if (researchTreePublicationRejected) {
            output.add(NarratedElementType.HINT, rejectedPublicationMessage());
        }
        if (guidanceVisible) {
            output.add(
                    NarratedElementType.HINT,
                    Component.translatable(
                            "gui.taczweaponblueprints.research_bench.tree.guide.narration"));
            return;
        }
        if (getFocused() == searchBox) {
            output.add(
                    NarratedElementType.HINT,
                    Component.translatable(
                            "gui.taczweaponblueprints.research_bench.tree.search.narration.results",
                            treeSearch.matches().size()));
            return;
        }
        if (getFocused() != null) {
            // Focused widgets provide their own title, state, and usage narration.
            return;
        }
        ResourceLocation narratedId = fullscreenOverlayState.pinnedNodeId()
                .filter(id -> fullscreen)
                .orElseGet(() -> treeCanvas.focusedId()
                        .orElseGet(() -> menu.selectedBlueprint().orElse(null)));
        treeCanvas.graph().node(narratedId).ifPresent(node -> {
            boolean pinned = fullscreen
                    && fullscreenOverlayState.pinnedNodeId()
                            .filter(node.blueprintId()::equals)
                            .isPresent();
            ResearchTreeSelectedNodePresenter.Presentation details =
                    selectedNodePresentation(node);
            SelectedNodeUi selectedUi = selectedNodeUi(node, details);
            Component narration;
            if (details.exactPreview()) {
                MutableComponent exact = Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.card.narration.exact",
                        nodeName(node),
                        selectedUi.message(),
                        exactCostNarration(details),
                        exactMaterialNarration(details),
                        selectedRelationshipSummary(details));
                selectedTierLine(node).ifPresent(value -> exact.append(". ").append(value));
                selectedCraftingLine(node).ifPresent(value ->
                        exact.append(". ").append(value));
                selectedFragmentLine(node).ifPresent(value -> exact.append(". ").append(value));
                narration = exact;
            } else if (pinned) {
                narration = Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.card.narration",
                        nodeName(node),
                        selectedUi.message(),
                        selectedRelationshipSummary(details));
            } else {
                narration = node.visibility().revealsResearchSummary()
                        ? publishedNodeNarration(node, selectedUi, details)
                        : Component.translatable(
                                "gui.taczweaponblueprints.research_bench.tree.narration.redacted",
                                nodeName(node),
                                selectedUi.message(),
                                details.directRequirementCount(),
                                details.immediateUnlockCount());
            }
            output.add(NarratedElementType.HINT, narration);
        });
        output.add(
                NarratedElementType.HINT,
                Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.view.narration",
                        currentBrowseViewName()));
        if (isTechTreeView()) {
            ResearchTechTreeDomainMenu menu = techTreeDomainMenu();
            menu.selectedDomain().ifPresent(domain -> {
                ResearchTechTreeDomainMenu.Entry entry = menu.entry(domain);
                output.add(
                        NarratedElementType.HINT,
                        Component.translatable(
                                "gui.taczweaponblueprints.research_bench.tree.domain.narration",
                                techTreeDomainName(domain),
                                domain.ordinal() + 1,
                                menu.entries().size(),
                                entry.visibleBlueprintCount()));
            });
        }
        output.add(
                NarratedElementType.USAGE,
                Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.keyboard_usage"));
    }

    private Component publishedNodeNarration(
            ResearchTreeGraph.Node node,
            SelectedNodeUi selectedUi,
            ResearchTreeSelectedNodePresenter.Presentation details) {
        return switch (details.costMode()) {
            case POINTS_ONLY -> Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.narration.points_only",
                    nodeName(node),
                    selectedUi.message(),
                    details.directRequirementCount(),
                    details.immediateUnlockCount(),
                    details.pointCost());
            case ITEMS_ONLY -> Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.narration.items_only",
                    nodeName(node),
                    selectedUi.message(),
                    details.directRequirementCount(),
                    details.immediateUnlockCount(),
                    details.ingredientTypeCount());
            case POINTS_AND_ITEMS -> Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.narration.points_and_items",
                    nodeName(node),
                    selectedUi.message(),
                    details.directRequirementCount(),
                    details.immediateUnlockCount(),
                    details.pointCost(),
                    details.ingredientTypeCount());
        };
    }

    private Component exactCostNarration(
            ResearchTreeSelectedNodePresenter.Presentation details) {
        if (details.pathPlanningFailed()) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.path.cost_unavailable");
        }
        return details.costBypassed()
                ? Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.card.narration.cost_bypassed")
                : !details.pointsEnabled()
                        ? Component.translatable(
                                "gui.taczweaponblueprints.research_bench.tree.card.narration.points_disabled")
                : Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.card.narration.cost",
                        details.pointCost(),
                        details.pointBalance());
    }

    private Component exactMaterialNarration(
            ResearchTreeSelectedNodePresenter.Presentation details) {
        if (details.pathPlanningFailed()) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.path.materials_unavailable");
        }
        MutableComponent materials = Component.empty();
        if (!details.materialsEnabled()) {
            materials.append(Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.card.narration.materials_disabled"));
        } else if (details.costBypassed()) {
            materials.append(Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.card.narration.materials_bypassed"));
        } else {
            materials.append(Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.card.narration.materials"));
        }
        for (int index = 0; index < details.ingredients().size(); index++) {
            ResearchSelectionPreview.IngredientPreview ingredient = details.ingredients().get(index);
            materials.append(index == 0 ? " " : "; ");
            materials.append(Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.tooltip.ingredient",
                    ingredientName(ingredient),
                    ingredient.inventoryAvailable(),
                    ingredient.required()));
        }
        if (details.additionalIngredientTypes() > 0) {
            materials.append("; ");
            materials.append(Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.path.more_materials",
                    details.additionalIngredientTypes()));
        }
        return materials;
    }

    private boolean moveKeyboardCursor(ResearchTreeNavigator.Direction direction) {
        ResourceLocation current = treeCanvas.focusedId()
                .orElseGet(() -> menu.selectedBlueprint().orElse(null));
        Optional<ResourceLocation> next = ResearchTreeNavigator.move(
                treeCanvas.graph(), treeCanvas.layout(), current, direction);
        next.ifPresent(blueprintId -> {
            treeCanvas.setFocusedNode(blueprintId);
            fullscreenOverlayState.clearPinnedNode();
            updateWidgets();
            treeCanvas.revealNode(blueprintId, KEYBOARD_REVEAL_PADDING);
        });
        return next.isPresent();
    }

    private void cycleSearch(int delta) {
        treeSearch.selectNext(delta);
        updateVisibleSearchMatches();
        updateWidgets();
        if (searchBox != null && searchBox.visible) {
            setFocused(searchBox);
        }
    }

    private void commitSearchResult(ResourceLocation blueprintId) {
        if (treeSearch.select(blueprintId).isEmpty()) {
            return;
        }
        updateVisibleSearchMatches();
        navigateToPublicNode(blueprintId, true);
        if (fullscreen) {
            fullscreenOverlayState.pinNode(blueprintId);
        }
        selectTreeNode(blueprintId);
    }

    private void selectTreeNode(ResourceLocation blueprintId) {
        Optional<ResearchTreeSelectionController.Decision> selection =
                treeSelection.resolve(treeCanvas.graph(), blueprintId);
        if (selection.isEmpty()) {
            return;
        }
        treeCanvas.focusNode(blueprintId);
        fullscreenOverlayState.pinNode(blueprintId);
        rememberTechTreeSelection(blueprintId);
        clearFeedbackForDifferentNode(blueprintId);
        if (selection.orElseThrow().sendAuthoritativeSelection()) {
            requestSelection(blueprintId);
        }
        updateWidgets();
    }

    private void selectTreeNodeInPlace(ResourceLocation blueprintId) {
        Optional<ResearchTreeSelectionController.Decision> selection =
                treeSelection.resolve(treeCanvas.graph(), blueprintId);
        if (selection.isEmpty()) {
            return;
        }
        treeCanvas.setFocusedNode(blueprintId);
        fullscreenOverlayState.pinNode(blueprintId);
        rememberTechTreeSelection(blueprintId);
        clearFeedbackForDifferentNode(blueprintId);
        if (selection.orElseThrow().sendAuthoritativeSelection()) {
            requestSelection(blueprintId);
        }
        updateWidgets();
    }

    private void rememberTechTreeSelection(ResourceLocation blueprintId) {
        if (!isTechTreeView()) {
            return;
        }
        ResearchTechTreeProjectionCatalog catalog = treeProjections.techTreeProjections();
        techTreeNavigation.focus(blueprintId, catalog);
        techTreeNavigation.pin(blueprintId, catalog);
    }

    private void clearFeedbackForDifferentNode(ResourceLocation blueprintId) {
        if (fullscreenHoldActivation.snapshot(Util.getMillis()).blueprintId()
                .filter(blueprintId::equals).isEmpty()) {
            fullscreenHoldActivation.cancel();
        }
        ResearchTreeFeedbackState.Snapshot research = researchFeedback.snapshot();
        if (research.status() != ResearchTreeFeedbackState.Status.PENDING
                && research.blueprintId().filter(blueprintId::equals).isEmpty()) {
            researchFeedback.clear();
        }
        if (selectionFeedback.snapshot().blueprintId()
                .filter(blueprintId::equals).isEmpty()) {
            selectionFeedback.clear();
        }
    }

    private Optional<ResearchTreeGraph.Node> focusedTreeNode() {
        return treeCanvas.focusedNode(menu.selectedBlueprint().orElse(null));
    }

    private final class SearchResultButton extends AbstractButton {
        private ResearchTreeSearchController.Result result;

        private SearchResultButton(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty());
            visible = false;
            active = false;
        }

        private void configure(
                ResearchTreeSearchController.Result next,
                boolean requestedVisible) {
            result = next;
            visible = requestedVisible && next != null;
            active = visible;
            if (!visible) {
                setMessage(Component.empty());
                setTooltip(null);
                return;
            }
            ResearchTreeGraph.Node node = treeProjections.publication().graph()
                    .node(next.blueprintId())
                    .orElseThrow(() -> new IllegalStateException(
                            "search result is absent from the published Research Tree"));
            Component name = nodeName(node);
            setMessage(name);
            setTooltip(Tooltip.create(Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.search.result.tooltip",
                    name,
                    next.index() + 1,
                    next.total())));
        }

        @Override
        public void onPress() {
            if (result != null) {
                commitSearchResult(result.blueprintId());
            }
        }

        @Override
        protected void renderWidget(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick) {
            if (result == null) {
                return;
            }
            int border = result.active() ? ACCENT : isHoveredOrFocused() ? TEXT : BORDER;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xF8111820);
            graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), border);
            if (result.active()) {
                graphics.fill(getX() + 1, getY() + 1, getX() + 3, getY() + getHeight() - 1, ACCENT);
            }

            ItemStack icon = researchTreeIcons.getOrDefault(
                    result.blueprintId(), ItemStack.EMPTY);
            int textX = getX() + 6;
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, getX() + 3, getY() + 2);
                textX = getX() + 22;
            }
            String position = (result.index() + 1) + "/" + result.total();
            int positionWidth = font.width(position);
            int nameWidth = Math.max(1, getX() + getWidth() - 5 - positionWidth - 5 - textX);
            String name = font.plainSubstrByWidth(getMessage().getString(), nameWidth);
            graphics.drawString(
                    font,
                    name,
                    textX,
                    getY() + (getHeight() - font.lineHeight) / 2,
                    result.active() ? ACCENT : TEXT,
                    false);
            graphics.drawString(
                    font,
                    position,
                    getX() + getWidth() - 5 - positionWidth,
                    getY() + (getHeight() - font.lineHeight) / 2,
                    MUTED,
                    false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
            if (result != null) {
                output.add(
                        NarratedElementType.HINT,
                        Component.translatable(
                                "gui.taczweaponblueprints.research_bench.tree.search.result.narration",
                                result.index() + 1,
                                result.total()));
            }
        }
    }

    private record WidgetSnapshot(
            boolean fullscreen,
            ResearchTreeFullscreenOverlayState.Snapshot overlay,
            boolean guidanceVisible,
            String searchQuery,
            int searchMatchCount,
            Optional<ResourceLocation> activeSearchMatch,
            ResearchTreePresentationContract.BrowseView browseView,
            Optional<ResourceLocation> selectedGroupId,
            Optional<Domain> selectedTechTreeDomain,
            boolean publicationRejected,
            int researchPoints,
            Optional<ResearchTreePlanner.Plan> researchPlan,
            Optional<ResearchGuidanceSnapshot> currentGuidance,
            boolean guidancePending,
            boolean guidanceUnavailable,
            boolean routeGuidanceAvailable,
            ClientResearchAffordabilityState.Snapshot affordability,
            long projectionRevision,
            int sidebarScroll,
            double viewportScale,
            Optional<ResourceLocation> focusedId,
            Optional<ResourceLocation> authoritativeSelection,
            ResearchSelectionPreview preview,
            ResearchTreeFeedbackState.Snapshot selectionFeedback,
            ResearchTreeFeedbackState.Snapshot researchFeedback,
            int screenWidth,
            int screenHeight) {
        private WidgetSnapshot {
            if (overlay == null || searchQuery == null
                    || activeSearchMatch == null
                    || browseView == null || selectedGroupId == null
                    || selectedTechTreeDomain == null
                    || researchPlan == null
                    || currentGuidance == null
                    || affordability == null
                    || focusedId == null || authoritativeSelection == null
                    || preview == null || selectionFeedback == null || researchFeedback == null
                    || searchMatchCount < 0 || researchPoints < 0
                    || !Double.isFinite(viewportScale) || viewportScale <= 0.0D
                    || screenWidth <= 0 || screenHeight <= 0) {
                throw new IllegalArgumentException("invalid Research Tree widget snapshot");
            }
        }
    }

    private record SelectedNodeUi(Component message, int color, boolean actionEnabled) {
        private SelectedNodeUi {
            if (message == null) {
                throw new IllegalArgumentException("selected Research Tree message cannot be null");
            }
        }
    }

    private record InventoryEntry(Item item, int count) {
        private static final InventoryEntry EMPTY = new InventoryEntry(null, 0);

        private InventoryEntry {
            if (count < 0 || (item == null) != (count == 0)) {
                throw new IllegalArgumentException("invalid guidance inventory entry");
            }
        }
    }

    private record FullscreenCardWidgetState(
            ResearchTreeContextCardLayout.Layout layout,
            boolean actionVisible,
            boolean returnVisible,
            boolean actionEnabled,
            boolean trackable,
            boolean tracked,
            Component tooltip) {
        private FullscreenCardWidgetState {
            if (layout == null || tooltip == null) {
                throw new IllegalArgumentException("invalid fullscreen context widget state");
            }
        }
    }

    /** Explicit compact selector; every Tech Tree domain is one mixed canvas. */
    private final class TechTreeDomainButton extends AbstractButton {
        private final Domain domain;
        private int nodeCount;
        private boolean selected;
        private ItemStack icon = ItemStack.EMPTY;

        private TechTreeDomainButton(
                int x,
                int y,
                int width,
                int height,
                Domain domain) {
            super(x, y, width, height, Component.literal(domain.name()));
            this.domain = domain;
        }

        private Domain domain() {
            return domain;
        }

        private void refresh(
                Component name,
                int nodeCount,
                boolean selected,
                ItemStack icon) {
            this.nodeCount = nodeCount;
            this.selected = selected;
            this.icon = icon == null ? ItemStack.EMPTY : icon;
            setMessage(name);
            setTooltip(Tooltip.create(techTreeDomainTooltip(domain, nodeCount)));
        }

        @Override
        public void onPress() {
            if (active) {
                selectTechTreeDomain(domain);
            }
        }

        @Override
        protected void renderWidget(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick) {
            boolean highlighted = selected || isFocused() || isMouseOver(mouseX, mouseY);
            int background = !active
                    ? 0xD0151A20
                    : selected ? 0xE0283840 : highlighted ? 0xE0202A34 : 0xD0111820;
            int border = !active ? 0xFF394552 : selected ? ACCENT : highlighted ? TEXT : BORDER;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
            graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), border);

            if (active && !icon.isEmpty() && getWidth() >= 16 && getHeight() >= 16) {
                graphics.renderItem(
                        icon,
                        getX() + Math.max(0, (getWidth() - 16) / 2),
                        getY() + Math.max(0, (getHeight() - 16) / 2));
            } else {
                graphics.drawCenteredString(
                        font,
                        Component.translatable(switch (domain) {
                            case WEAPONS ->
                                    "gui.taczweaponblueprints.research_bench.tree.domain.weapons.short";
                            case ATTACHMENTS ->
                                    "gui.taczweaponblueprints.research_bench.tree.domain.attachments.short";
                            case AMMO ->
                                    "gui.taczweaponblueprints.research_bench.tree.domain.ammo.short";
                        }),
                        getX() + getWidth() / 2,
                        getY() + Math.max(1, (getHeight() - 8) / 2),
                        active ? selected ? ACCENT : TEXT : MUTED);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
            Component hint = !active
                    ? Component.translatable(
                            "gui.taczweaponblueprints.research_bench.tree.domain.unavailable")
                    : Component.translatable(selected
                                    ? "gui.taczweaponblueprints.research_bench.tree.domain.selected"
                                    : "gui.taczweaponblueprints.research_bench.tree.domain.available",
                            nodeCount);
            output.add(NarratedElementType.HINT, hint);
        }
    }

    private final class RailEntryButton extends AbstractButton {
        private final int slot;
        private final ResearchTreeRailHoverState hoverState =
                new ResearchTreeRailHoverState();
        private int entryIndex = -1;
        private boolean selected;
        private boolean selectable;
        private int visibleBlueprintCount = -1;

        private RailEntryButton(int x, int y, int slot) {
            super(
                    x,
                    y,
                    ResearchTreeFullscreenRailLayout.ENTRY_SIZE,
                    ResearchTreeFullscreenRailLayout.ENTRY_SIZE,
                    Component.empty());
            this.slot = slot;
        }

        private void refresh(
                int entryIndex,
                Component name,
                boolean selected,
                boolean selectable,
                int visibleBlueprintCount) {
            this.entryIndex = entryIndex;
            this.selected = selected;
            this.selectable = selectable;
            this.visibleBlueprintCount = visibleBlueprintCount;
            setMessage(name);
        }

        private void updatePointerState(double mouseX, double mouseY) {
            hoverState.update(
                    visible,
                    selected || isFocused(),
                    isMouseOver(mouseX, mouseY),
                    labelPointerAvailable()
                            && labelPointerBounds().contains(mouseX, mouseY));
        }

        private boolean ownsPointer(double mouseX, double mouseY) {
            return isMouseOver(mouseX, mouseY)
                    || visibleLabelOwns(mouseX, mouseY);
        }

        private boolean labelContains(double mouseX, double mouseY) {
            return visibleLabelOwns(mouseX, mouseY);
        }

        private boolean visibleLabelOwns(double mouseX, double mouseY) {
            boolean requested = hoverState.ownsRevealedLabel(
                    visible,
                    selected || isFocused(),
                    labelPointerBounds().contains(mouseX, mouseY));
            return ResearchTreeInteractionPolicy.railLabelVisible(
                    requested,
                    labelBounds(),
                    contextCardBounds());
        }

        private boolean labelPointerAvailable() {
            return ResearchTreeInteractionPolicy.railLabelVisible(
                    true,
                    labelBounds(),
                    contextCardBounds());
        }

        private Optional<ResearchTreeScreenLayout.Rect> persistentLabelObstacle() {
            return visible && (selected || isFocused())
                    ? Optional.of(labelBounds())
                    : Optional.empty();
        }

        private ResearchTreeScreenLayout.Rect contextCardBounds() {
            return fullscreenContextCardLayout == null
                    ? null
                    : fullscreenContextCardLayout.card();
        }

        private ResearchTreeScreenLayout.Rect labelBounds() {
            int available = Math.max(
                    24,
                    ResearchBenchScreen.this.width - getX() - getWidth() - 12);
            int labelWidth = Math.min(
                    140,
                    Math.min(available, font.width(getMessage()) + 10));
            return new ResearchTreeScreenLayout.Rect(
                    getX() + getWidth() + 4,
                    getY() + 1,
                    labelWidth,
                    getHeight() - 2);
        }

        private ResearchTreeScreenLayout.Rect labelPointerBounds() {
            ResearchTreeScreenLayout.Rect label = labelBounds();
            int x = getX() + getWidth();
            return new ResearchTreeScreenLayout.Rect(
                    x,
                    getY(),
                    label.right() - x,
                    getHeight());
        }

        @Override
        public void onPress() {
            activateSidebarSlot(slot);
        }

        @Override
        protected void renderWidget(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick) {
            boolean labelVisible = ResearchTreeInteractionPolicy.railLabelVisible(
                    hoverState.labelVisible(visible, selected || isFocused()),
                    labelBounds(),
                    contextCardBounds());
            int background = !selectable
                    ? 0xD0151A20
                    : selected ? 0xE0283840 : labelVisible ? 0xE0202A34 : 0xD0111820;
            int border = !selectable
                    ? 0xFF394552
                    : selected ? ACCENT : labelVisible ? TEXT : BORDER;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
            graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), border);

            ItemStack icon = sidebarEntryIcon(entryIndex);
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, getX() + 2, getY() + 2);
            } else {
                graphics.drawCenteredString(
                        font,
                        sidebarEntryFallbackLabel(entryIndex),
                        getX() + getWidth() / 2,
                        getY() + 6,
                        !selectable ? MUTED : selected ? ACCENT : TEXT);
            }
            if (labelVisible) {
                String name = getMessage().getString();
                ResearchTreeScreenLayout.Rect labelBounds = labelBounds();
                int labelWidth = labelBounds.width();
                String label = font.plainSubstrByWidth(name, Math.max(1, labelWidth - 10));
                graphics.fill(
                        labelBounds.x(),
                        labelBounds.y(),
                        labelBounds.right(),
                        labelBounds.bottom(),
                        0xE8111820);
                graphics.renderOutline(
                        labelBounds.x(),
                        labelBounds.y(),
                        labelWidth,
                        labelBounds.height(),
                        border);
                graphics.drawString(
                        font,
                        label,
                        labelBounds.x() + 5,
                        getY() + 6,
                        selected ? ACCENT : TEXT,
                        false);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
            Component hint;
            if (entryIndex == 0) {
                hint = Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.rail.entry.view_action");
            } else if (!selectable) {
                hint = Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.domain.unavailable");
            } else if (isTechTreeView() && visibleBlueprintCount >= 0) {
                hint = Component.translatable(selected
                                ? "gui.taczweaponblueprints.research_bench.tree.domain.selected"
                                : "gui.taczweaponblueprints.research_bench.tree.domain.available",
                        visibleBlueprintCount);
            } else {
                hint = Component.translatable(selected
                        ? "gui.taczweaponblueprints.research_bench.tree.rail.entry.selected"
                        : "gui.taczweaponblueprints.research_bench.tree.rail.entry.available");
            }
            output.add(NarratedElementType.HINT, hint);
            if (selectable) {
                output.add(
                        NarratedElementType.USAGE,
                        Component.translatable(
                                "gui.taczweaponblueprints.research_bench.tree.rail.entry.usage"));
            }
        }
    }

    private final class RelationCardButton extends AbstractButton {
        private final ResearchTreeDetailLayout.RelationSlot slot;

        private RelationCardButton(ResearchTreeDetailLayout.RelationSlot slot) {
            super(
                    leftPos + slot.bounds().x(),
                    topPos + slot.bounds().y(),
                    slot.bounds().width(),
                    slot.bounds().height(),
                    Component.empty());
            this.slot = slot;
        }

        private void refresh(boolean browseMode) {
            Optional<ResearchTreeGraph.Node> relation = browseMode
                    ? relationNode(slot)
                    : Optional.empty();
            visible = relation.isPresent();
            active = visible;
            relation.ifPresent(node -> {
                Component description = Component.translatable(
                        slot.kind() == ResearchTreeDetailLayout.RelationKind.REQUIREMENT
                                ? "gui.taczweaponblueprints.research_bench.tree.relationship.requirement_button"
                                : "gui.taczweaponblueprints.research_bench.tree.relationship.unlock_button",
                        nodeName(node),
                        nodeStatus(node));
                setMessage(description);
                setTooltip(Tooltip.create(description));
            });
        }

        @Override
        public void onPress() {
            relationNode(slot).ifPresent(node -> selectTreeNode(node.blueprintId()));
        }

        @Override
        protected void renderWidget(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick) {
            Optional<ResearchTreeGraph.Node> relation = relationNode(slot);
            if (relation.isEmpty()) {
                return;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(leftPos, topPos, 0.0D);
            try {
                drawRelationCard(graphics, slot, relation.orElseThrow());
                if (isFocused()) {
                    ResearchTreeScreenLayout.Rect bounds = slot.bounds();
                    graphics.renderOutline(
                            bounds.x() - 2,
                            bounds.y() - 2,
                            bounds.width() + 4,
                            bounds.height() + 4,
                            TEXT);
                }
            } finally {
                graphics.pose().popPose();
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

}
