package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalEntry;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchPreview;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.network.ResearchBenchActionPacket;
import com.gamergaming.taczweaponblueprints.progression.BlueprintRecyclingService;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

/** Browse-first Research Bench UI backed entirely by the open server menu. */
public final class ResearchBenchScreen extends AbstractContainerScreen<ResearchBenchMenu> {
    private static final ResearchTreeGuidancePreference GUIDANCE_PREFERENCE =
            new ResearchTreeGuidancePreference(FMLPaths.CONFIGDIR.get());
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
    private static final int SIDEBAR_HEADER_HEIGHT = 16;
    private static final int SIDEBAR_ROW_HEIGHT = 20;
    private static final int SIDEBAR_ROW_GAP = 2;

    private final ResearchTreeCanvas treeCanvas = new ResearchTreeCanvas(
            new ResearchTreeViewState(),
            new ResearchTreeCanvas.Style(
                    SLOT, GRID, BORDER, MUTED, TEXT, ACCENT, EDGE,
                    0xFF183023, 0xFF111820,
                    0xFFE4C56A, 0xFF9B874E,
                    0xFF62C7D9, 0xFF477E89,
                    0xFF394552, TEXT, 0x700B0F14,
                    0x181D2A35, 0x90141920,
                    0x10283846, 0x50475869, 0xC018222C));
    private final ResearchTreeActivationTracker nodeActivation =
            new ResearchTreeActivationTracker(350L);
    private final ResearchTreeProjectionCache treeProjections =
            new ResearchTreeProjectionCache();
    private final ResearchTreeNavigationState treeNavigation =
            new ResearchTreeNavigationState();
    private final ResearchTreeCameraStore cameraStates = new ResearchTreeCameraStore();
    private Map<ResourceLocation, BlueprintJournalEntry> journalEntries = Map.of();
    private Map<ResourceLocation, ItemStack> researchTreeIcons = Map.of();
    private Set<ResourceLocation> globalTreeSearchMatches = Set.of();
    private int researchPoints;
    private Object researchPublicationIdentity;
    private ResearchBenchMenu.Mode visibleMode = ResearchBenchMenu.Mode.BROWSE;
    private ResearchTreeScreenLayout.Layout activeTreeLayout = COMPACT_TREE_LAYOUT;
    private boolean fullscreen;
    private ResearchTreeCameraStore.Key activeCameraKey;
    private boolean lastProjectionCameraRestored;
    private ResourceLocation pinnedDetailsId;
    private boolean guidanceInitialized;
    private boolean guidanceVisible;
    private boolean restoreSearchFocus;
    private int sidebarScroll;
    private EditBox pendingSearchFocus;
    private EditBox searchBox;
    private Button researchModeButton;
    private Button recycleModeButton;
    private Button primaryResearchButton;
    private Button recycleButton;
    private Button zoomOutButton;
    private Button zoomInButton;
    private Button browseViewButton;
    private Button groupButton;
    private Button fitButton;
    private Button fullscreenButton;
    private Button guidanceDismissButton;
    private Button helpButton;
    private List<RelationCardButton> relationButtons = List.of();
    private List<Button> sidebarButtons = List.of();

    public ResearchBenchScreen(ResearchBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = ResearchTreeScreenLayout.COMPACT_WIDTH;
        imageHeight = ResearchTreeScreenLayout.COMPACT_HEIGHT;
        inventoryLabelX = 74;
        inventoryLabelY = 140;
    }

    @Override
    protected void init() {
        String retainedSearch = searchBox == null ? "" : searchBox.getValue();
        boolean retainedSearchFocus = restoreSearchFocus
                || searchBox != null && searchBox.isFocused();
        restoreSearchFocus = false;
        if (fullscreen && (width < ResearchTreeScreenLayout.MIN_FULLSCREEN_WIDTH
                || height < ResearchTreeScreenLayout.MIN_FULLSCREEN_HEIGHT)) {
            fullscreen = false;
        }
        imageWidth = fullscreen ? width : ResearchTreeScreenLayout.COMPACT_WIDTH;
        imageHeight = fullscreen ? height : ResearchTreeScreenLayout.COMPACT_HEIGHT;
        super.init();
        visibleMode = menu.mode();
        if (visibleMode != ResearchBenchMenu.Mode.BROWSE) {
            fullscreen = false;
            imageWidth = ResearchTreeScreenLayout.COMPACT_WIDTH;
            imageHeight = ResearchTreeScreenLayout.COMPACT_HEIGHT;
            leftPos = (width - imageWidth) / 2;
            topPos = (height - imageHeight) / 2;
        }
        activeTreeLayout = fullscreen
                ? ResearchTreeScreenLayout.fullscreen(width, height, false)
                : COMPACT_TREE_LAYOUT;
        if (!guidanceInitialized) {
            guidanceVisible = GUIDANCE_PREFERENCE.shouldShow();
            guidanceInitialized = true;
        }
        treeCanvas.setBounds(
                activeTreeLayout.mode(),
                offset(activeTreeLayout.canvas()));

        researchModeButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.taczweaponblueprints.research_bench.mode.research"),
                ignored -> changeMode(ResearchBenchMenu.Mode.BROWSE))
                .bounds(leftPos + 8, topPos + 20, 72, 18)
                .build());
        recycleModeButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.taczweaponblueprints.research_bench.mode.recycle"),
                ignored -> changeMode(ResearchBenchMenu.Mode.RECYCLE))
                .bounds(leftPos + 82, topPos + 20, 72, 18)
                .build());

        searchBox = addRenderableWidget(new EditBox(
                font,
                leftPos + activeTreeLayout.search().x(),
                topPos + activeTreeLayout.search().y(),
                activeTreeLayout.search().width(),
                activeTreeLayout.search().height(),
                Component.translatable("gui.taczweaponblueprints.research_bench.search.narration")));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.translatable("gui.taczweaponblueprints.research_bench.search"));
        searchBox.setValue(retainedSearch);
        searchBox.setResponder(ignored -> applyTreeSearch(true));
        zoomOutButton = addRenderableWidget(Button.builder(
                Component.literal("−"), ignored -> zoomTree(-1.0D))
                .bounds(
                        leftPos + activeTreeLayout.zoomOut().x(),
                        topPos + activeTreeLayout.zoomOut().y(),
                        activeTreeLayout.zoomOut().width(),
                        activeTreeLayout.zoomOut().height())
                .createNarration(ignored -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.zoom_out"))
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.zoom_out")))
                .build());
        zoomInButton = addRenderableWidget(Button.builder(
                Component.literal("+"), ignored -> zoomTree(1.0D))
                .bounds(
                        leftPos + activeTreeLayout.zoomIn().x(),
                        topPos + activeTreeLayout.zoomIn().y(),
                        activeTreeLayout.zoomIn().width(),
                        activeTreeLayout.zoomIn().height())
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
                        "gui.taczweaponblueprints.research_bench.tree.group.tooltip",
                        currentGroupName()))
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.group.tooltip",
                        currentGroupName())))
                .build());
        fitButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.taczweaponblueprints.research_bench.tree.fit"),
                ignored -> fitTree())
                .bounds(
                        leftPos + activeTreeLayout.showAll().x(),
                        topPos + activeTreeLayout.showAll().y(),
                        activeTreeLayout.showAll().width(),
                        activeTreeLayout.showAll().height())
                .build());
        fullscreenButton = addRenderableWidget(Button.builder(
                Component.literal(fullscreen ? "×" : "⛶"),
                ignored -> setFullscreen(!fullscreen))
                .bounds(
                        leftPos + activeTreeLayout.expand().x(),
                        topPos + activeTreeLayout.expand().y(),
                        activeTreeLayout.expand().width(),
                        activeTreeLayout.expand().height())
                .createNarration(ignored -> Component.translatable(fullscreen
                        ? "gui.taczweaponblueprints.research_bench.tree.fullscreen.exit"
                        : "gui.taczweaponblueprints.research_bench.tree.fullscreen.enter"))
                .tooltip(Tooltip.create(Component.translatable(fullscreen
                        ? "gui.taczweaponblueprints.research_bench.tree.fullscreen.exit"
                        : "gui.taczweaponblueprints.research_bench.tree.fullscreen.enter")))
                .build());
        ResearchTreeScreenLayout.Rect actionBounds = ResearchTreeDetailLayout
                .primaryAction(activeTreeLayout)
                .orElseGet(() -> new ResearchTreeScreenLayout.Rect(
                        activeTreeLayout.details().right() - 74,
                        activeTreeLayout.details().bottom() - 22,
                        64,
                        20));
        primaryResearchButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.taczweaponblueprints.research_bench.research"),
                ignored -> requestResearch())
                .bounds(
                        leftPos + actionBounds.x(),
                        topPos + actionBounds.y(),
                        actionBounds.width(),
                        actionBounds.height())
                .build());
        ResearchTreeGuidanceLayout.Guide guidance =
                ResearchTreeGuidanceLayout.forLayout(activeTreeLayout);
        guidanceDismissButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.taczweaponblueprints.research_bench.tree.guide.dismiss"),
                ignored -> dismissGuidance())
                .bounds(
                        leftPos + guidance.dismiss().x(),
                        topPos + guidance.dismiss().y(),
                        guidance.dismiss().width(),
                        guidance.dismiss().height())
                .build());
        helpButton = addRenderableWidget(Button.builder(
                Component.literal("?"),
                ignored -> showGuidance())
                .bounds(leftPos + Math.min(280, imageWidth - 30), topPos + 20, 22, 18)
                .createNarration(ignored -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.guide.help"))
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.guide.help")))
                .build());
        recycleButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.taczweaponblueprints.research_bench.recycle"),
                ignored -> requestRecycle())
                .bounds(leftPos + 48, topPos + 72, 96, 20)
                .build());

        configureTabOrder();
        ArrayList<RelationCardButton> nextRelationButtons = new ArrayList<>();
        List<ResearchTreeDetailLayout.RelationSlot> relationSlots = activeRelationSlots();
        for (int index = 0; index < relationSlots.size(); index++) {
            RelationCardButton button = addRenderableWidget(
                    new RelationCardButton(relationSlots.get(index)));
            button.setTabOrderGroup(40 + index);
            nextRelationButtons.add(button);
        }
        relationButtons = List.copyOf(nextRelationButtons);
        createSidebarButtons();

        if (researchPublicationIdentity == null
                || ClientResearchState.publication() != researchPublicationIdentity) {
            reloadResearchTree(researchPublicationIdentity == null);
        } else {
            applyActiveProjection(treeCanvas.focusedId().orElse(null));
        }
        applyTreeSearch(false);
        updateWidgets();
        if (retainedSearchFocus && visibleMode == ResearchBenchMenu.Mode.BROWSE) {
            setInitialFocus(searchBox);
            pendingSearchFocus = searchBox;
        } else {
            pendingSearchFocus = null;
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        saveActiveCamera();
        activeCameraKey = null;
        restoreSearchFocus = searchBox != null && getFocused() == searchBox;
        super.resize(minecraft, width, height);
    }

    private void configureTabOrder() {
        searchBox.setTabOrderGroup(0);
        zoomOutButton.setTabOrderGroup(1);
        zoomInButton.setTabOrderGroup(2);
        fitButton.setTabOrderGroup(3);
        browseViewButton.setTabOrderGroup(4);
        groupButton.setTabOrderGroup(5);
        fullscreenButton.setTabOrderGroup(6);
        primaryResearchButton.setTabOrderGroup(23);
        guidanceDismissButton.setTabOrderGroup(24);
        helpButton.setTabOrderGroup(24);
        researchModeButton.setTabOrderGroup(30);
        recycleModeButton.setTabOrderGroup(31);
        recycleButton.setTabOrderGroup(0);
    }

    private void createSidebarButtons() {
        Optional<ResearchTreeScreenLayout.Rect> sidebar = activeTreeLayout.sidebar();
        if (!fullscreen || sidebar.isEmpty()) {
            sidebarButtons = List.of();
            return;
        }
        ResearchTreeScreenLayout.Rect bounds = sidebar.orElseThrow();
        int rows = Math.max(
                1,
                (bounds.height() - SIDEBAR_HEADER_HEIGHT)
                        / (SIDEBAR_ROW_HEIGHT + SIDEBAR_ROW_GAP));
        ArrayList<Button> buttons = new ArrayList<>(rows);
        for (int slot = 0; slot < rows; slot++) {
            int slotIndex = slot;
            Button button = addRenderableWidget(Button.builder(
                    Component.empty(),
                    ignored -> activateSidebarSlot(slotIndex))
                    .bounds(
                            leftPos + bounds.x() + 2,
                            topPos + bounds.y() + SIDEBAR_HEADER_HEIGHT
                                    + slot * (SIDEBAR_ROW_HEIGHT + SIDEBAR_ROW_GAP),
                            bounds.width() - 4,
                            SIDEBAR_ROW_HEIGHT)
                    .build());
            button.setTabOrderGroup(7 + slot);
            buttons.add(button);
        }
        sidebarButtons = List.copyOf(buttons);
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
                && menu.mode() == ResearchBenchMenu.Mode.BROWSE
                && (getFocused() == null || getFocused() == searchBox)) {
            setInitialFocus(searchBox);
        }
        Object latest = ClientResearchState.publication();
        if (latest != researchPublicationIdentity) {
            reloadResearchTree(false);
            applyTreeSearch(false);
        }
        if (menu.mode() != visibleMode) {
            visibleMode = menu.mode();
            if (fullscreen && visibleMode != ResearchBenchMenu.Mode.BROWSE) {
                fullscreen = false;
                rebuildPresentation();
                return;
            }
        }
        treeCanvas.setAuthoritativeSelection(menu.selectedBlueprint().orElse(null));
        updateWidgets();
    }

    private void reloadResearchTree(boolean initial) {
        ClientResearchState.Publication publication = ClientResearchState.publication();
        researchPublicationIdentity = publication;
        researchPoints = publication.journal().researchPoints();
        ResourceLocation previousFocus = treeCanvas.focusedId().orElse(null);

        Map<ResourceLocation, BlueprintJournalEntry> nextEntries = new LinkedHashMap<>();
        for (BlueprintJournalEntry entry : publication.journal().entries()) {
            entry.blueprintId().ifPresent(id -> nextEntries.put(id, entry));
        }
        journalEntries = Map.copyOf(nextEntries);

        Map<ResourceLocation, ItemStack> nextIcons = new LinkedHashMap<>();
        for (ResearchTreeGraph.Node node : publication.graph().nodes()) {
            if (node.visibility().revealsIcon()) {
                nextIcons.put(node.blueprintId(), BlueprintItem.createBlueprint(node.blueprintId().toString()));
            }
        }
        researchTreeIcons = Map.copyOf(nextIcons);
        boolean presentationTopologyChanged = treeProjections.update(
                new ResearchTreePublication(publication.graph(), publication.presentation()),
                publication.layout());
        if (presentationTopologyChanged) {
            cameraStates.clear();
            activeCameraKey = null;
        }
        ResourceLocation preferredFocus = previousFocus != null
                ? previousFocus
                : menu.selectedBlueprint().orElse(null);
        treeNavigation.retain(publication.presentation(), preferredFocus);
        boolean topologyChanged = applyActiveProjection(preferredFocus);
        if (!treeCanvas.graph().nodes().isEmpty()) {
            if (initial) {
                treeCanvas.fit();
                focusUsefulNode();
            } else if (topologyChanged) {
                treeCanvas.focusedId().ifPresent(treeCanvas::focusNode);
            }
        }
    }

    private boolean applyActiveProjection(ResourceLocation preferredFocus) {
        saveActiveCamera();
        ResearchTreeProjection projection = treeProjections.projection(
                treeNavigation.browseView(),
                treeNavigation.selectedGroupId().orElse(null));
        Map<ResourceLocation, ItemStack> projectedIcons = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, ItemStack> entry : researchTreeIcons.entrySet()) {
            if (projection.graph().node(entry.getKey()).isPresent()) {
                projectedIcons.put(entry.getKey(), entry.getValue());
            }
        }
        boolean topologyChanged = treeCanvas.setContent(
                projection.graph(),
                projection.layout(),
                projectedIcons,
                preferredFocus,
                menu.selectedBlueprint().orElse(null),
                projection.crossGroupLinks());
        if (projection.graph().node(pinnedDetailsId).isEmpty()) {
            pinnedDetailsId = null;
        }
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

    private ResearchTreeCameraStore.Key cameraKey() {
        Optional<ResourceLocation> groupId = treeNavigation.browseView()
                == ResearchTreePresentationContract.BrowseView.BRANCHES
                        ? treeNavigation.selectedGroupId()
                        : Optional.empty();
        return new ResearchTreeCameraStore.Key(
                activeTreeLayout.mode(), treeNavigation.browseView(), groupId);
    }

    private void saveActiveCamera() {
        if (activeCameraKey != null) {
            cameraStates.save(activeCameraKey, treeCanvas.viewport());
        }
    }

    private void updateVisibleSearchMatches() {
        LinkedHashSet<ResourceLocation> visibleMatches = new LinkedHashSet<>();
        for (ResourceLocation match : globalTreeSearchMatches) {
            if (treeCanvas.graph().node(match).isPresent()) {
                visibleMatches.add(match);
            }
        }
        treeCanvas.setSearchMatches(visibleMatches);
    }

    private void applyTreeSearch(boolean focusFirst) {
        String query = searchBox == null ? "" : searchBox.getValue().strip().toLowerCase(Locale.ROOT);
        LinkedHashSet<ResourceLocation> matches = new LinkedHashSet<>();
        if (query.isEmpty()) {
            globalTreeSearchMatches = Set.of();
        } else {
            for (ResearchTreeGraph.Node node : treeProjections.publication().graph().nodes()) {
                if (searchableText(node).contains(query)) {
                    matches.add(node.blueprintId());
                }
            }
            globalTreeSearchMatches = java.util.Collections.unmodifiableSet(matches);
        }
        updateVisibleSearchMatches();
        if (!globalTreeSearchMatches.isEmpty()
                && treeCanvas.focusedId().filter(globalTreeSearchMatches::contains).isEmpty()) {
            navigateToPublicNode(globalTreeSearchMatches.iterator().next(), focusFirst);
        } else if (focusFirst && !globalTreeSearchMatches.isEmpty()) {
            navigateToPublicNode(treeCanvas.focusedId().orElseThrow(), true);
        }
        updateWidgets();
    }

    private void navigateToPublicNode(ResourceLocation blueprintId, boolean center) {
        if (blueprintId == null
                || treeProjections.publication().graph().node(blueprintId).isEmpty()) {
            return;
        }
        if (treeNavigation.browseView()
                == ResearchTreePresentationContract.BrowseView.BRANCHES) {
            ResourceLocation targetGroup = treeProjections.publication().presentation()
                    .membership(blueprintId)
                    .orElseThrow()
                    .groupId();
            if (treeNavigation.selectedGroupId().filter(targetGroup::equals).isEmpty()) {
                treeNavigation.selectGroup(
                        targetGroup,
                        treeProjections.publication().presentation());
                applyActiveProjection(blueprintId);
                ensureSelectedSidebarVisible();
            }
        }
        updateVisibleSearchMatches();
        if (center) {
            treeCanvas.focusNode(blueprintId);
        } else {
            treeCanvas.setFocusedNode(blueprintId);
        }
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
        boolean browseMode = visibleMode == ResearchBenchMenu.Mode.BROWSE;
        boolean recycleMode = visibleMode == ResearchBenchMenu.Mode.RECYCLE;
        if (researchModeButton == null) {
            return;
        }
        researchModeButton.active = true;
        recycleModeButton.active = true;
        researchModeButton.visible = !fullscreen;
        recycleModeButton.visible = !fullscreen;
        searchBox.visible = browseMode;
        searchBox.setEditable(browseMode);
        if (!browseMode && getFocused() == searchBox) {
            setFocused(null);
        }
        browseViewButton.visible = browseMode;
        groupButton.visible = browseMode;
        fitButton.visible = browseMode;
        zoomOutButton.visible = browseMode;
        zoomInButton.visible = browseMode;
        fullscreenButton.visible = browseMode;
        guidanceDismissButton.visible = browseMode && guidanceVisible;
        helpButton.visible = browseMode && !fullscreen && !guidanceVisible;
        primaryResearchButton.visible = browseMode;
        recycleButton.visible = recycleMode;

        boolean hasGroups = !treeProjections.publication().presentation().groups().isEmpty();
        browseViewButton.active = browseMode && hasGroups;
        groupButton.visible = browseMode && !fullscreen;
        groupButton.active = groupButton.visible && hasGroups;
        browseViewButton.setMessage(browseViewShortName());
        browseViewButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.view.tooltip",
                currentBrowseViewName())));
        Component groupName = currentGroupName();
        groupButton.setMessage(clipped(groupName, activeTreeLayout.groupSelector().width() - 6));
        groupButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.group.tooltip",
                groupName)));
        fitButton.active = browseMode && !treeCanvas.graph().nodes().isEmpty();
        zoomOutButton.active = browseMode
                && !treeCanvas.graph().nodes().isEmpty()
                && treeCanvas.viewport().scale() > ResearchTreeViewport.MIN_SCALE;
        zoomInButton.active = browseMode
                && !treeCanvas.graph().nodes().isEmpty()
                && treeCanvas.viewport().scale() < ResearchTreeViewport.MAX_SCALE;
        fullscreenButton.active = browseMode
                && (fullscreen
                        || (width >= ResearchTreeScreenLayout.MIN_FULLSCREEN_WIDTH
                                && height >= ResearchTreeScreenLayout.MIN_FULLSCREEN_HEIGHT));
        primaryResearchButton.active = browseMode
                && menu.preview().researchable()
                && menu.selectedBlueprint().filter(id -> treeCanvas.focusedId().filter(id::equals).isPresent())
                        .flatMap(treeCanvas.graph()::node)
                        .filter(node -> node.visibility().allowsServerSelection())
                        .isPresent();
        if (browseMode && menu.preview().blueprintId().isPresent()) {
            primaryResearchButton.setTooltip(Tooltip.create(readiness(menu.preview())));
        } else {
            primaryResearchButton.setTooltip(null);
        }
        recycleButton.active = recycleMode && menu.preview().recycling().recyclable();
        relationButtons.forEach(button -> button.refresh(browseMode));
        updateSidebarButtons(browseMode);
        if (getFocused() instanceof RelationCardButton relationButton
                && !relationButton.visible) {
            setFocused(null);
        }
    }

    private void updateSidebarButtons(boolean browseMode) {
        if (sidebarButtons.isEmpty()) {
            return;
        }
        int entryCount = sidebarEntryCount();
        int maximumScroll = Math.max(0, entryCount - sidebarButtons.size());
        sidebarScroll = Math.max(0, Math.min(sidebarScroll, maximumScroll));
        for (int slot = 0; slot < sidebarButtons.size(); slot++) {
            Button button = sidebarButtons.get(slot);
            int entryIndex = sidebarScroll + slot;
            button.visible = browseMode && fullscreen && entryIndex < entryCount;
            button.active = button.visible;
            if (!button.visible) {
                continue;
            }
            Component name = sidebarEntryName(entryIndex);
            boolean selected = sidebarEntrySelected(entryIndex);
            Component message = selected
                    ? Component.literal("• ").append(name)
                    : name;
            ResearchTreeScreenLayout.Rect sidebar = activeTreeLayout.sidebar().orElseThrow();
            button.setMessage(clipped(message, sidebar.width() - 12));
            button.setTooltip(Tooltip.create(name));
        }
    }

    private int sidebarEntryCount() {
        return 1 + treeProjections.publication().presentation().groups().size();
    }

    private Component sidebarEntryName(int entryIndex) {
        if (entryIndex == 0) {
            return Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.view.all_weapons");
        }
        List<ResearchTreePresentation.Group> groups =
                treeProjections.publication().presentation().groups();
        int groupIndex = entryIndex - 1;
        return groupIndex >= 0 && groupIndex < groups.size()
                ? groupName(groups.get(groupIndex))
                : Component.empty();
    }

    private boolean sidebarEntrySelected(int entryIndex) {
        if (entryIndex == 0) {
            return treeNavigation.browseView()
                    == ResearchTreePresentationContract.BrowseView.ALL_WEAPONS;
        }
        List<ResearchTreePresentation.Group> groups =
                treeProjections.publication().presentation().groups();
        int groupIndex = entryIndex - 1;
        return groupIndex >= 0 && groupIndex < groups.size()
                && treeNavigation.browseView()
                        == ResearchTreePresentationContract.BrowseView.BRANCHES
                && treeNavigation.selectedGroupId().filter(groups.get(groupIndex).id()::equals).isPresent();
    }

    private void activateSidebarSlot(int slot) {
        int entryIndex = sidebarScroll + slot;
        if (entryIndex <= 0) {
            showAllWeapons();
            return;
        }
        List<ResearchTreePresentation.Group> groups =
                treeProjections.publication().presentation().groups();
        int groupIndex = entryIndex - 1;
        if (groupIndex >= 0 && groupIndex < groups.size()) {
            selectResearchGroup(groups.get(groupIndex).id());
        }
    }

    private void showAllWeapons() {
        ResourceLocation preferredFocus = treeCanvas.focusedId().orElse(null);
        treeNavigation.setBrowseView(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                treeProjections.publication().presentation());
        applyActiveProjection(preferredFocus);
        sidebarScroll = 0;
        if (lastProjectionCameraRestored
                && preferredFocus != null
                && treeCanvas.graph().node(preferredFocus).isPresent()) {
            treeCanvas.setFocusedNode(preferredFocus);
        } else if (preferredFocus != null && treeCanvas.graph().node(preferredFocus).isPresent()) {
            treeCanvas.focusNode(preferredFocus);
        } else {
            treeCanvas.fit();
        }
        updateWidgets();
    }

    private void selectResearchGroup(ResourceLocation groupId) {
        ResearchTreePresentation presentation = treeProjections.publication().presentation();
        ResearchTreePresentation.Group group = presentation.group(groupId).orElseThrow();
        ResearchTreePresentationContract.GroupSelectionAction action =
                treeNavigation.selectGroup(groupId, presentation);
        ResourceLocation preferred = group.iconNodeId()
                .orElse(group.members().get(0).nodeId());
        if (action == ResearchTreePresentationContract.GroupSelectionAction.SHOW_GROUP) {
            applyActiveProjection(preferred);
            if (lastProjectionCameraRestored) {
                treeCanvas.setFocusedNode(preferred);
            } else {
                treeCanvas.focusNode(preferred);
            }
        } else {
            if (!treeCanvas.focusGroup(groupId)) {
                treeCanvas.focusNodes(group.members().stream()
                        .map(ResearchTreePresentation.Member::nodeId)
                        .toList());
            }
            treeCanvas.setFocusedNode(preferred);
        }
        ensureSelectedSidebarVisible();
        updateWidgets();
    }

    private void ensureSelectedSidebarVisible() {
        if (sidebarButtons.isEmpty()
                || treeNavigation.browseView()
                        == ResearchTreePresentationContract.BrowseView.ALL_WEAPONS) {
            return;
        }
        int selectedEntry = treeNavigation.selectedGroupId()
                .flatMap(id -> treeProjections.publication().presentation().group(id))
                .map(group -> group.order() + 1)
                .orElse(0);
        if (selectedEntry < sidebarScroll) {
            sidebarScroll = selectedEntry;
        } else if (selectedEntry >= sidebarScroll + sidebarButtons.size()) {
            sidebarScroll = selectedEntry - sidebarButtons.size() + 1;
        }
    }

    private void changeMode(ResearchBenchMenu.Mode mode) {
        if (mode == null || mode == visibleMode) {
            return;
        }
        visibleMode = mode;
        menu.setClientMode(mode);
        boolean rebuildCompact = fullscreen && mode != ResearchBenchMenu.Mode.BROWSE;
        if (rebuildCompact) {
            fullscreen = false;
        }
        ResearchBenchMenu.Action action = switch (mode) {
            case BROWSE -> ResearchBenchMenu.Action.SHOW_BROWSE;
            case RECYCLE -> ResearchBenchMenu.Action.SHOW_RECYCLE;
        };
        send(action, Optional.empty());
        if (rebuildCompact) {
            rebuildPresentation();
        } else {
            updateWidgets();
        }
    }

    private void requestResearch() {
        send(ResearchBenchMenu.Action.RESEARCH, menu.selectedBlueprint());
    }

    private void requestResearch(ResourceLocation blueprintId) {
        send(ResearchBenchMenu.Action.RESEARCH, Optional.ofNullable(blueprintId));
    }

    private void requestRecycle() {
        send(
                ResearchBenchMenu.Action.RECYCLE,
                BlueprintItem.getBlueprintId(menu.getSlot(ResearchBenchMenu.RECYCLING_SLOT).getItem()));
    }

    private void send(ResearchBenchMenu.Action action, Optional<ResourceLocation> id) {
        NetworkHandler.INSTANCE.sendToServer(new ResearchBenchActionPacket(menu.containerId, action, id));
    }

    private void zoomTree(double direction) {
        treeCanvas.zoomAtCenter(direction);
        updateWidgets();
    }

    private void fitTree() {
        treeCanvas.fit();
        updateWidgets();
    }

    private void toggleBrowseView() {
        ResearchTreePresentationContract.BrowseView next =
                treeNavigation.browseView()
                        == ResearchTreePresentationContract.BrowseView.BRANCHES
                                ? ResearchTreePresentationContract.BrowseView.ALL_WEAPONS
                                : ResearchTreePresentationContract.BrowseView.BRANCHES;
        ResourceLocation preferredFocus = treeCanvas.focusedId().orElse(null);
        ResearchTreePresentation presentation = treeProjections.publication().presentation();
        treeNavigation.setBrowseView(next, presentation);
        if (next == ResearchTreePresentationContract.BrowseView.BRANCHES
                && preferredFocus != null) {
            presentation.membership(preferredFocus).ifPresent(membership ->
                    treeNavigation.selectGroup(membership.groupId(), presentation));
        }
        applyActiveProjection(preferredFocus);
        ensureSelectedSidebarVisible();
        if (lastProjectionCameraRestored
                && preferredFocus != null
                && treeCanvas.graph().node(preferredFocus).isPresent()) {
            treeCanvas.setFocusedNode(preferredFocus);
        } else if (preferredFocus != null && treeCanvas.graph().node(preferredFocus).isPresent()) {
            treeCanvas.focusNode(preferredFocus);
        } else {
            focusUsefulNode();
        }
        updateWidgets();
    }

    private void cycleResearchGroup() {
        ResearchTreePresentation presentation = treeProjections.publication().presentation();
        treeNavigation.nextGroup(presentation, 1).ifPresent(this::selectResearchGroup);
    }

    private Component browseViewShortName() {
        return Component.literal(treeNavigation.browseView()
                == ResearchTreePresentationContract.BrowseView.BRANCHES ? "B" : "A");
    }

    private Component currentBrowseViewName() {
        return Component.translatable(treeNavigation.browseView()
                == ResearchTreePresentationContract.BrowseView.BRANCHES
                        ? "gui.taczweaponblueprints.research_bench.tree.view.branches"
                        : "gui.taczweaponblueprints.research_bench.tree.view.all_weapons");
    }

    private Component currentGroupName() {
        return treeNavigation.selectedGroupId()
                .flatMap(treeProjections.publication().presentation()::group)
                .map(this::groupName)
                .orElseGet(() -> Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.group.none"));
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
        GUIDANCE_PREFERENCE.dismiss();
        updateWidgets();
    }

    private void showGuidance() {
        guidanceVisible = true;
        updateWidgets();
    }

    private void setFullscreen(boolean next) {
        if (next == fullscreen
                || next && (visibleMode != ResearchBenchMenu.Mode.BROWSE
                        || width < ResearchTreeScreenLayout.MIN_FULLSCREEN_WIDTH
                        || height < ResearchTreeScreenLayout.MIN_FULLSCREEN_HEIGHT)) {
            return;
        }
        fullscreen = next;
        rebuildPresentation();
    }

    private void rebuildPresentation() {
        if (minecraft != null) {
            resize(minecraft, width, height);
        }
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
        boolean guidanceHovered = guidanceContains(mouseX, mouseY);
        if (visibleMode == ResearchBenchMenu.Mode.BROWSE && !guidanceHovered) {
            treeCanvas.updateHover(mouseX, mouseY);
        } else {
            treeCanvas.clearHover();
        }
        if (!fullscreen || visibleMode != ResearchBenchMenu.Mode.BROWSE) {
            renderBackground(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderSelectedTabIndicator(graphics);
        renderTooltip(graphics, mouseX, mouseY);
        if (visibleMode == ResearchBenchMenu.Mode.BROWSE && !guidanceHovered) {
            renderTreeTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (fullscreen && visibleMode == ResearchBenchMenu.Mode.BROWSE) {
            drawBrowseBackground(graphics);
            ResearchTreeScreenLayout.Rect toolbar = activeTreeLayout.toolbar();
            graphics.fill(
                    0,
                    0,
                    width,
                    toolbar.bottom() + 3,
                    0xE8111820);
            graphics.fill(0, toolbar.bottom() + 2, width, toolbar.bottom() + 3, BORDER);
            renderGuidanceAtScreenCoordinates(graphics);
            return;
        }
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, BORDER);
        graphics.fill(leftPos + 1, topPos + 18, leftPos + imageWidth - 1, topPos + 40, 0xD0182029);
        switch (visibleMode) {
            case BROWSE -> {
                drawBrowseBackground(graphics);
                renderGuidanceAtScreenCoordinates(graphics);
            }
            case RECYCLE -> drawRecycleBackground(graphics);
        }
    }

    private void drawBrowseBackground(GuiGraphics graphics) {
        ResearchTreeScreenLayout.Rect details = activeTreeLayout.details();
        if (activeTreeLayout.detailsPlacement()
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
        treeCanvas.render(
                graphics,
                font,
                this::nodeName,
                this::nodeBorderColor,
                this::nodeStatusSymbol,
                this::groupName);
        if (fullscreen) {
            renderSidebarBackground(graphics);
        }
    }

    private void renderSidebarBackground(GuiGraphics graphics) {
        ResearchTreeScreenLayout.Rect sidebar = activeTreeLayout.sidebar().orElseThrow();
        int x = leftPos + sidebar.x();
        int y = topPos + sidebar.y();
        graphics.fill(x, y, x + sidebar.width(), y + sidebar.height(), 0xE8111820);
        graphics.renderOutline(x, y, sidebar.width(), sidebar.height(), BORDER);
        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.sidebar"),
                x + sidebar.width() / 2,
                y + 4,
                MUTED);
    }

    private void drawRecycleBackground(GuiGraphics graphics) {
        graphics.fill(leftPos + 8, topPos + 45, leftPos + 302, topPos + 126, SECTION);
        graphics.fill(leftPos + 68, topPos + 151, leftPos + 242, topPos + 238, SECTION);
        drawSlot(
                graphics,
                ResearchBenchMenu.Layout.RECYCLING_X,
                ResearchBenchMenu.Layout.RECYCLING_Y,
                ACCENT);
        drawPlayerInventory(graphics);
    }

    private void drawPlayerInventory(GuiGraphics graphics) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(
                        graphics,
                        ResearchBenchMenu.Layout.PLAYER_X
                                + column * ResearchBenchMenu.Layout.PLAYER_SPACING,
                        ResearchBenchMenu.Layout.PLAYER_Y
                                + row * ResearchBenchMenu.Layout.PLAYER_SPACING,
                        BORDER);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(
                    graphics,
                    ResearchBenchMenu.Layout.PLAYER_X
                            + column * ResearchBenchMenu.Layout.PLAYER_SPACING,
                    ResearchBenchMenu.Layout.HOTBAR_Y,
                    BORDER);
        }
    }

    private void renderSelectedTabIndicator(GuiGraphics graphics) {
        if (fullscreen) {
            return;
        }
        boolean researchTab = visibleMode != ResearchBenchMenu.Mode.RECYCLE;
        int x = leftPos + (researchTab ? 8 : 82);
        graphics.renderOutline(x - 1, topPos + 19, 74, 20, ACCENT);
        graphics.fill(x, topPos + 38, x + 72, topPos + 40, ACCENT);
    }

    private void drawSlot(GuiGraphics graphics, int x, int y, int borderColor) {
        graphics.fill(leftPos + x - 1, topPos + y - 1, leftPos + x + 17, topPos + y + 17, SLOT);
        graphics.renderOutline(leftPos + x - 1, topPos + y - 1, 18, 18, borderColor);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (fullscreen && visibleMode == ResearchBenchMenu.Mode.BROWSE) {
            renderFullscreenBrowseLabels(graphics);
            return;
        }
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        if (visibleMode != ResearchBenchMenu.Mode.BROWSE) {
            graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
        }
        switch (visibleMode) {
            case BROWSE -> renderBrowseLabels(graphics);
            case RECYCLE -> renderRecyclingLabels(graphics);
        }
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
        if (!guidanceVisible || visibleMode != ResearchBenchMenu.Mode.BROWSE) {
            return;
        }
        ResearchTreeScreenLayout.Rect panel =
                ResearchTreeGuidanceLayout.forLayout(activeTreeLayout).panel();
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xFA111820);
        graphics.renderOutline(panel.x(), panel.y(), panel.width(), panel.height(), ACCENT);
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
    }

    private boolean guidanceContains(double mouseX, double mouseY) {
        return guidanceVisible
                && visibleMode == ResearchBenchMenu.Mode.BROWSE
                && ResearchTreeGuidanceLayout.forLayout(activeTreeLayout).panel()
                        .contains(mouseX - leftPos, mouseY - topPos);
    }

    private void renderFullscreenBrowseLabels(GuiGraphics graphics) {
        ResearchTreeScreenLayout.Rect canvas = activeTreeLayout.canvas();
        ResearchTreeScreenLayout.Rect details = activeTreeLayout.details();
        boolean overlayDetails = activeTreeLayout.detailsPlacement()
                == ResearchTreeScreenLayout.DetailsPlacement.OVERLAY;
        int stateMessageY = overlayDetails
                ? activeTreeLayout.toolbar().bottom() + 8
                : canvas.y() + ResearchTreeCanvas.STICKY_HEADER_HEIGHT + 4;
        if (!guidanceVisible && treeCanvas.graph().nodes().isEmpty()) {
            graphics.drawWordWrap(
                    font,
                    Component.translatable("gui.taczweaponblueprints.research_bench.search.unavailable"),
                    canvas.x() + 8,
                    stateMessageY,
                    canvas.width() - 16,
                    MUTED);
        }
        if (!guidanceVisible
                && searchBox != null
                && !searchBox.getValue().isBlank()
                && globalTreeSearchMatches.isEmpty()) {
            graphics.drawString(
                    font,
                    Component.translatable("gui.taczweaponblueprints.research_bench.search.empty"),
                    canvas.x() + ResearchTreeCanvas.STICKY_GUTTER_WIDTH + 4,
                    stateMessageY,
                    WARN,
                    false);
        }

        if (overlayDetails) {
            return;
        }

        Optional<ResearchTreeGraph.Node> selected = focusedTreeNode();
        if (selected.isEmpty()) {
            graphics.drawWordWrap(
                    font,
                    Component.translatable("gui.taczweaponblueprints.research_bench.select_hint"),
                    details.x() + 10,
                    details.y() + 10,
                    Math.max(80, details.width() - 20),
                    MUTED);
            return;
        }

        ResearchTreeGraph.Node node = selected.orElseThrow();
        switch (activeTreeLayout.detailsPlacement()) {
            case RIGHT -> renderRightFullscreenDetails(graphics, details, node);
            case BOTTOM -> renderBottomFullscreenDetails(graphics, details, node);
            case DRAWER -> renderDrawerFullscreenDetails(graphics, details, node);
            case OVERLAY -> {
                // Fullscreen details are rendered contextually in the node tooltip.
            }
        }
    }

    private void renderRightFullscreenDetails(
            GuiGraphics graphics,
            ResearchTreeScreenLayout.Rect details,
            ResearchTreeGraph.Node node) {
        drawFocusedNodeIcon(graphics, node, details.x() + 10, details.y() + 10);
        graphics.drawString(
                font, clipped(nodeName(node), details.width() - 54),
                details.x() + 34, details.y() + 11, TEXT, false);
        drawStatusAndAction(
                graphics, node, details.x() + 10, details.y() + 31, details.width() - 20);
        graphics.drawString(
                font, clipped(nodeCostOrVisibility(node), details.width() - 20),
                details.x() + 10, details.y() + 49, MUTED, false);
        drawFullscreenBalance(graphics, details.x() + 10, details.y() + 62, details.width() - 20);
        drawRelationshipLabels(
                graphics, node,
                details.x() + 10, details.y() + 77,
                details.x() + 10, details.y() + 117,
                details.width() - 20);
    }

    private void renderBottomFullscreenDetails(
            GuiGraphics graphics,
            ResearchTreeScreenLayout.Rect details,
            ResearchTreeGraph.Node node) {
        drawFocusedNodeIcon(graphics, node, details.x() + 10, details.y() + 9);
        graphics.drawString(
                font, clipped(nodeName(node), 176),
                details.x() + 34, details.y() + 10, TEXT, false);
        drawStatusAndAction(graphics, node, details.x() + 10, details.y() + 29, 200);
        graphics.drawString(
                font, clipped(nodeCostOrVisibility(node), 96),
                details.x() + 10, details.y() + 45, MUTED, false);
        drawFullscreenBalance(graphics, details.x() + 112, details.y() + 45, 98);
        drawRelationshipLabels(
                graphics, node,
                details.x() + 220, details.y() + 8,
                details.x() + 220, details.y() + 44,
                details.width() - 230);
    }

    private void renderDrawerFullscreenDetails(
            GuiGraphics graphics,
            ResearchTreeScreenLayout.Rect details,
            ResearchTreeGraph.Node node) {
        drawFocusedNodeIcon(graphics, node, details.x() + 8, details.y() + 8);
        graphics.drawString(
                font, clipped(nodeName(node), 84),
                details.x() + 30, details.y() + 9, TEXT, false);
        drawStatusAndAction(graphics, node, details.x() + 8, details.y() + 29, 104);
        graphics.drawString(
                font, clipped(nodeCostOrVisibility(node), 104),
                details.x() + 8, details.y() + 45, MUTED, false);
        drawRelationshipLabels(
                graphics, node,
                details.x() + 120, details.y() + 7,
                details.x() + 120, details.y() + 31,
                Math.max(36, details.width() - 204));
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

    private void drawStatusAndAction(
            GuiGraphics graphics,
            ResearchTreeGraph.Node node,
            int x,
            int y,
            int width) {
        ResearchTreeStatusGlyph.render(
                graphics,
                x,
                y + 1,
                nodeBorderColor(node),
                ResearchTreeStatusGlyph.forSymbol(nodeStatusSymbol(node)));
        graphics.drawString(
                font,
                clipped(nodeNextAction(node), Math.max(0, width - 10)),
                x + 10,
                y,
                nodeBorderColor(node),
                false);
    }

    private Component nodeCostOrVisibility(ResearchTreeGraph.Node node) {
        return node.visibility().revealsResearchSummary()
                ? Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.cost", node.pointCost())
                : Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.visibility."
                                + node.visibility().serializedName());
    }

    private void drawFullscreenBalance(GuiGraphics graphics, int x, int y, int width) {
        graphics.drawString(
                font,
                clipped(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.balance", researchPoints), width),
                x,
                y,
                ACCENT,
                false);
    }

    private void drawRelationshipLabels(
            GuiGraphics graphics,
            ResearchTreeGraph.Node node,
            int requirementsX,
            int requirementsY,
            int unlocksX,
            int unlocksY,
            int width) {
        graphics.drawString(
                font,
                clipped(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.needs_short",
                        node.prerequisiteCount()), width),
                requirementsX,
                requirementsY,
                ACCENT,
                false);
        graphics.drawString(
                font,
                clipped(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.unlocks_short",
                        treeCanvas.directUnlocks(node.blueprintId()).size()), width),
                unlocksX,
                unlocksY,
                0xFF62C7D9,
                false);
    }

    private void renderBrowseLabels(GuiGraphics graphics) {
        if (!guidanceVisible && treeCanvas.graph().nodes().isEmpty()) {
            graphics.drawWordWrap(
                    font,
                    Component.translatable("gui.taczweaponblueprints.research_bench.search.unavailable"),
                    TREE_X + 8,
                    TREE_Y + 10,
                    TREE_WIDTH - 16,
                    MUTED);
        }
        if (!guidanceVisible
                && searchBox != null
                && !searchBox.getValue().isBlank()
                && globalTreeSearchMatches.isEmpty()) {
            graphics.drawString(
                    font,
                    Component.translatable("gui.taczweaponblueprints.research_bench.search.empty"),
                    TREE_X + ResearchTreeCanvas.STICKY_GUTTER_WIDTH + 4,
                    TREE_Y + ResearchTreeCanvas.STICKY_HEADER_HEIGHT + 4,
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
                clipped(nodeNextAction(node), 176),
                DETAIL_X + 38,
                DETAIL_Y + 18,
                nodeBorderColor(node),
                false);
        ResearchTreeStatusGlyph.render(
                graphics,
                DETAIL_X + 28,
                DETAIL_Y + 19,
                nodeBorderColor(node),
                ResearchTreeStatusGlyph.forSymbol(nodeStatusSymbol(node)));
        if (node.visibility().revealsResearchSummary()) {
            graphics.drawString(
                    font,
                    clipped(Component.translatable(
                            "gui.taczweaponblueprints.research_bench.tree.cost",
                            node.pointCost()), 64),
                    DETAIL_X + 28,
                    DETAIL_Y + 30,
                    node.visibility().revealsExactPolicy()
                            ? canAfford(node) ? MUTED : BAD
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

    private void renderRelationSummary(GuiGraphics graphics, ResearchTreeGraph.Node node) {
        List<ResourceLocation> unlocks = treeCanvas.directUnlocks(node.blueprintId());
        graphics.drawString(
                font,
                clipped(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.needs_short",
                        node.prerequisiteCount()), 36),
                102,
                DETAIL_Y + 30,
                MUTED,
                false);
        graphics.drawString(
                font,
                clipped(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.unlocks_short",
                        unlocks.size()), 32),
                174,
                DETAIL_Y + 30,
                MUTED,
                false);
    }

    private List<ResearchTreeDetailLayout.RelationSlot> activeRelationSlots() {
        return fullscreen
                ? ResearchTreeDetailLayout.fullscreen(activeTreeLayout)
                : COMPACT_RELATION_SLOTS;
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

    private void renderRecyclingLabels(GuiGraphics graphics) {
        ResearchBenchPreview.RecyclingPreview recycling = menu.preview().recycling();
        graphics.drawString(
                font,
                Component.translatable("gui.taczweaponblueprints.research_bench.recycling.title"),
                18, 50, ACCENT, false);
        if (recycling.blueprintId().isEmpty()) {
            graphics.drawWordWrap(
                    font,
                    Component.translatable("gui.taczweaponblueprints.research_bench.recycling.empty"),
                    152, 72, 140, MUTED);
            return;
        }
        Component status = recyclingStatus(recycling.status());
        graphics.drawString(font, clipped(status, 140), 152, 73,
                recycling.recyclable() ? GOOD : BAD, false);
        if (recycling.pointValue() > 0) {
            graphics.drawString(
                    font,
                    Component.translatable(
                            "gui.taczweaponblueprints.research_bench.recycling.reward",
                            recycling.pointValue()),
                    152, 85, recycling.recyclable() ? GOOD : WARN, false);
        }
        Component balance = Component.translatable(
                "gui.taczweaponblueprints.research_bench.recycling.balance",
                recycling.pointBalance(), recycling.pointCap());
        graphics.drawString(font, clipped(balance, 78), 220, 85, MUTED, false);
    }

    private Component clipped(Component component, int maxWidth) {
        String value = component.getString();
        if (font.width(value) <= maxWidth) {
            return component;
        }
        return Component.literal(
                font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("..."))) + "...");
    }

    private ItemStack ingredientIcon(ResearchBenchPreview.IngredientPreview ingredient) {
        if (!ingredient.items().isEmpty()) {
            Item item = ForgeRegistries.ITEMS.getValue(ingredient.items().get(0));
            if (item != null) {
                return new ItemStack(item);
            }
        }
        return ItemStack.EMPTY;
    }

    private Component ingredientName(ResearchBenchPreview.IngredientPreview ingredient) {
        ItemStack icon = ingredientIcon(ingredient);
        if (!icon.isEmpty()) {
            return icon.getHoverName();
        }
        return ingredient.tag()
                .<Component>map(id -> Component.literal("#" + id))
                .orElseGet(() -> Component.literal("?"));
    }

    private void renderTreeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Optional<ResearchTreeProjection.CrossGroupLink> portal =
                treeCanvas.portalAt(mouseX, mouseY);
        if (portal.isPresent()) {
            ResearchTreeProjection.CrossGroupLink link = portal.orElseThrow();
            Component destination = groupName(link.remoteGroupId());
            graphics.renderComponentTooltip(
                    font,
                    List.of(
                            Component.translatable(
                                    link.direction() == ResearchTreeProjection.Direction.REQUIREMENT
                                            ? "gui.taczweaponblueprints.research_bench.tree.portal.requirement"
                                            : "gui.taczweaponblueprints.research_bench.tree.portal.unlock",
                                    destination),
                            Component.translatable(
                                    "gui.taczweaponblueprints.research_bench.tree.portal.open")),
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
            if (fullscreen && pinnedDetailsId != null
                    && treeCanvas.contains(mouseX, mouseY)
                    && (primaryResearchButton == null || !primaryResearchButton.isMouseOver(mouseX, mouseY))) {
                treeCanvas.graph().node(pinnedDetailsId).ifPresent(node -> {
                    ResearchTreeScreenLayout.Rect canvas = activeTreeLayout.canvas();
                    graphics.renderComponentTooltip(
                            font,
                            treeTooltipLines(node, true),
                            leftPos + canvas.right() - 12,
                            topPos + canvas.y() + ResearchTreeCanvas.STICKY_HEADER_HEIGHT + 8);
                });
            }
            return;
        }
        ResearchTreeGraph.Node node = hovered.orElseThrow();
        graphics.renderComponentTooltip(
                font, treeTooltipLines(node, false), mouseX, mouseY);
    }

    private List<Component> treeTooltipLines(
            ResearchTreeGraph.Node node,
            boolean pinned) {
        List<Component> lines = new ArrayList<>();
        lines.add(nodeName(node));
        lines.add(nodeStatus(node));
        nodeRelationship(node).ifPresent(lines::add);
        if (treeCanvas.authoritativeSelectedId().filter(node.blueprintId()::equals).isPresent()
                && treeCanvas.focusedId().filter(node.blueprintId()::equals).isEmpty()) {
            lines.add(Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.chosen_for_research"));
        }
        if (node.visibility().revealsResearchSummary()
                && node.availability() != ResearchTreeGraph.Availability.RESEARCH_DISABLED) {
            lines.add(Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.tooltip.cost",
                    node.pointCost(), node.ingredientTypeCount()));
        }
        if (node.prerequisiteCount() > 0) {
            lines.add(Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.tooltip.prerequisites",
                    node.prerequisiteCount()));
        }
        ResearchBenchPreview preview = menu.preview();
        if (preview.blueprintId().filter(node.blueprintId()::equals).isPresent()) {
            lines.add(Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.tooltip.balance",
                    preview.pointBalance()));
            for (ResearchBenchPreview.IngredientPreview ingredient : preview.ingredients()) {
                lines.add(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.tooltip.ingredient",
                        ingredientName(ingredient),
                        ingredient.totalAvailable(),
                        ingredient.required()));
            }
            lines.add(readiness(preview));
            if (node.availability() == ResearchTreeGraph.Availability.AVAILABLE) {
                lines.add(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.tooltip.research_action"));
            }
        }
        if (pinned) {
            lines.add(Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.tooltip.unpin"));
        }
        return List.copyOf(lines);
    }

    private Optional<Component> nodeRelationship(ResearchTreeGraph.Node node) {
        ResearchTreePresentationContract.RelationshipRole role =
                treeCanvas.relationshipRole(node.blueprintId());
        if (role == ResearchTreePresentationContract.RelationshipRole.NEUTRAL) {
            return Optional.empty();
        }
        return Optional.of(Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.relationship."
                        + role.name().toLowerCase(Locale.ROOT)));
    }

    private int nodeBorderColor(ResearchTreeGraph.Node node) {
        return switch (node.availability()) {
            case REDACTED -> MUTED;
            case PREVIEW -> ACCENT;
            case LEARNED -> GOOD;
            case AVAILABLE -> canAfford(node) ? ACCENT : BAD;
            case PREREQUISITES_REQUIRED -> WARN;
            case DISCOVERY_REQUIRED -> MUTED;
            case RESEARCH_DISABLED, COST_ABOVE_CAP, CONTENT_UNAVAILABLE -> BAD;
        };
    }

    private ResearchTreePresentationContract.StatusSymbol nodeStatusSymbol(
            ResearchTreeGraph.Node node) {
        return ResearchTreePresentationContract.statusSymbol(node, canAfford(node));
    }

    private Component nodeStatus(ResearchTreeGraph.Node node) {
        if (node.availability() == ResearchTreeGraph.Availability.AVAILABLE && !canAfford(node)) {
            return Component.translatable("gui.taczweaponblueprints.research_bench.tree.status.points");
        }
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.status."
                        + node.availability().name().toLowerCase(Locale.ROOT));
    }

    private Component nodeNextAction(ResearchTreeGraph.Node node) {
        ResearchTreePresentationContract.NextAction action =
                ResearchTreePresentationContract.nextAction(node, canAfford(node));
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.next."
                        + action.name().toLowerCase(Locale.ROOT));
    }

    private boolean canAfford(ResearchTreeGraph.Node node) {
        if (node.learned() || node.pointCost() == 0) {
            return true;
        }
        BlueprintJournalEntry entry = journalEntries.get(node.blueprintId());
        return entry != null && entry.canAffordPoints();
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
            if (super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }
        boolean browseMode = visibleMode == ResearchBenchMenu.Mode.BROWSE;
        // Fullscreen deliberately places controls over the canvas. Widgets
        // must receive the click before the underlying pan/select surface.
        if (browseMode && super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (browseMode
                && (button == 0 || button == 1)
                && treeCanvas.contains(mouseX, mouseY)) {
            Optional<ResearchTreeProjection.CrossGroupLink> clickedPortal = button == 0
                    ? treeCanvas.portalAt(mouseX, mouseY)
                    : Optional.empty();
            if (clickedPortal.isPresent()) {
                pinnedDetailsId = null;
                navigateThroughPortal(clickedPortal.orElseThrow());
                return true;
            }
            Optional<ResearchTreeGraph.Node> clickedNode = button == 0
                    ? treeCanvas.nodeAt(mouseX, mouseY)
                    : Optional.empty();
            long now = Util.getMillis();
            boolean doubleClick = clickedNode
                    .map(node -> nodeActivation.click(node.blueprintId(), now))
                    .orElse(false);
            if (clickedNode.isPresent()) {
                pinnedDetailsId = clickedNode.orElseThrow().blueprintId();
            } else if (button == 0) {
                pinnedDetailsId = null;
            }
            setFocused(null);
            if (treeCanvas.mouseClicked(mouseX, mouseY, button, this::selectTreeNode)) {
                if (clickedNode.isPresent()) {
                    ResearchTreeGraph.Node node = clickedNode.orElseThrow();
                    if (doubleClick
                            && node.availability() == ResearchTreeGraph.Availability.AVAILABLE
                            && ResearchTreeInteractionPolicy.allowsServerSelection(node)) {
                        requestResearch(node.blueprintId());
                    }
                } else {
                    nodeActivation.reset();
                }
                return true;
            }
        }
        return browseMode ? false : super.mouseClicked(mouseX, mouseY, button);
    }

    private void navigateThroughPortal(ResearchTreeProjection.CrossGroupLink portal) {
        ResearchTreePresentation presentation = treeProjections.publication().presentation();
        treeNavigation.selectGroup(portal.remoteGroupId(), presentation);
        applyActiveProjection(portal.remoteNodeId());
        ensureSelectedSidebarVisible();
        treeCanvas.focusNode(portal.remoteNodeId());
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
        if (treeCanvas.mouseDragged(button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (treeCanvas.mouseReleased(button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (guidanceContains(mouseX, mouseY)) {
            return true;
        }
        if (visibleMode == ResearchBenchMenu.Mode.BROWSE
                && fullscreen
                && activeTreeLayout.sidebar().map(sidebar ->
                        sidebar.contains(mouseX - leftPos, mouseY - topPos)).orElse(false)) {
            int maximumScroll = Math.max(0, sidebarEntryCount() - sidebarButtons.size());
            if (delta != 0.0D) {
                sidebarScroll = Math.max(
                        0,
                        Math.min(maximumScroll, sidebarScroll + (delta < 0.0D ? 1 : -1)));
            }
            updateSidebarButtons(true);
            return true;
        }
        if (visibleMode == ResearchBenchMenu.Mode.BROWSE
                && treeCanvas.mouseScrolled(mouseX, mouseY, delta)) {
            updateWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                && ResearchTreePresentationContract.escapeAction(fullscreen)
                        == ResearchTreePresentationContract.EscapeAction.EXIT_FULLSCREEN) {
            setFullscreen(false);
            return true;
        }
        if (visibleMode == ResearchBenchMenu.Mode.BROWSE && searchBox != null) {
            ResearchTreeInteractionPolicy.KeyIntent intent = keyIntent(keyCode);
            ResearchTreeInteractionPolicy.KeyboardTarget target =
                    ResearchTreeInteractionPolicy.route(
                            searchBox.isFocused(),
                            !globalTreeSearchMatches.isEmpty(),
                            intent);
            if (target == ResearchTreeInteractionPolicy.KeyboardTarget.SEARCH_RESULTS) {
                if (intent == ResearchTreeInteractionPolicy.KeyIntent.UP
                        || intent == ResearchTreeInteractionPolicy.KeyIntent.DOWN) {
                    cycleSearch(keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1);
                    return true;
                }
            } else if (target == ResearchTreeInteractionPolicy.KeyboardTarget.SEARCH_SELECTION) {
                ResourceLocation result = treeCanvas.focusedId()
                        .filter(globalTreeSearchMatches::contains)
                        .orElseGet(() -> globalTreeSearchMatches.iterator().next());
                navigateToPublicNode(result, true);
                selectTreeNode(result);
                return true;
            } else if (target == ResearchTreeInteractionPolicy.KeyboardTarget.TREE) {
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
        if (visibleMode != ResearchBenchMenu.Mode.BROWSE) {
            return;
        }
        if (guidanceVisible) {
            output.add(
                    NarratedElementType.HINT,
                    Component.translatable(
                            "gui.taczweaponblueprints.research_bench.tree.guide.narration"));
        }
        ResourceLocation narratedId = treeCanvas.focusedId()
                .orElseGet(() -> menu.selectedBlueprint().orElse(null));
        treeCanvas.graph().node(narratedId).ifPresent(node -> output.add(
                NarratedElementType.HINT,
                node.visibility().revealsResearchSummary()
                        ? Component.translatable(
                                "gui.taczweaponblueprints.research_bench.tree.narration",
                                nodeName(node),
                                nodeStatus(node),
                                node.prerequisiteCount(),
                                treeCanvas.directUnlocks(node.blueprintId()).size(),
                                nodeNextAction(node),
                                node.pointCost())
                        : Component.translatable(
                                "gui.taczweaponblueprints.research_bench.tree.narration.redacted",
                                nodeName(node),
                                nodeStatus(node),
                                node.prerequisiteCount(),
                                treeCanvas.directUnlocks(node.blueprintId()).size(),
                                nodeNextAction(node))));
        output.add(
                NarratedElementType.USAGE,
                Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.keyboard_usage"));
    }

    private boolean moveKeyboardCursor(ResearchTreeNavigator.Direction direction) {
        ResourceLocation current = treeCanvas.focusedId()
                .orElseGet(() -> menu.selectedBlueprint().orElse(null));
        Optional<ResourceLocation> next = ResearchTreeNavigator.move(
                treeCanvas.graph(), treeCanvas.layout(), current, direction);
        next.ifPresent(this::selectTreeNode);
        return next.isPresent();
    }

    private void cycleSearch(int delta) {
        List<ResourceLocation> matches = List.copyOf(globalTreeSearchMatches);
        if (matches.isEmpty()) {
            return;
        }
        int current = treeCanvas.focusedId().map(matches::indexOf).orElse(-1);
        int next = Math.floorMod(current + delta, matches.size());
        navigateToPublicNode(matches.get(next), true);
    }

    private void selectTreeNode(ResourceLocation blueprintId) {
        Optional<ResearchTreeGraph.Node> selected = treeCanvas.graph().node(blueprintId);
        if (blueprintId == null || selected.isEmpty()) {
            return;
        }
        treeCanvas.focusNode(blueprintId);
        ResearchTreeGraph.Node node = selected.orElseThrow();
        if (ResearchTreeInteractionPolicy.allowsServerSelection(node)) {
            send(ResearchBenchMenu.Action.SELECT, Optional.of(blueprintId));
        }
        updateWidgets();
    }

    private Optional<ResearchTreeGraph.Node> focusedTreeNode() {
        return treeCanvas.focusedNode(menu.selectedBlueprint().orElse(null));
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

    private Component recyclingStatus(BlueprintRecyclingService.Status status) {
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.recycling.status."
                        + status.name().toLowerCase(Locale.ROOT));
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
