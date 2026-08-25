package com.gamergaming.taczweaponblueprints.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.init.ModBlocks;
import com.gamergaming.taczweaponblueprints.init.ModMenus;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.BlueprintRecyclingService;
import com.gamergaming.taczweaponblueprints.progression.BlueprintResearchService;
import com.gamergaming.taczweaponblueprints.progression.ResearchIngredientPlanner;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

public final class ResearchBenchMenu extends AbstractContainerMenu {
    public static final int RECYCLING_SLOT = 0;
    public static final int FIRST_PLAYER_SLOT = 1;

    /** Shared slot coordinates so the server menu and client presentation cannot drift apart. */
    public static final class Layout {
        public static final int RECYCLING_X = 22;
        public static final int RECYCLING_Y = 73;
        public static final int PLAYER_X = 74;
        public static final int PLAYER_Y = 157;
        public static final int PLAYER_SPACING = 18;
        public static final int HOTBAR_Y = 215;

        private Layout() {
        }
    }

    private final SimpleContainer recyclingInput = new SimpleContainer(1);
    private final ContainerLevelAccess access;
    private final Player owner;
    private final Inventory playerInventory;
    private final DataSlot modeData = DataSlot.standalone();
    private ResourceLocation selectedBlueprint;
    private ResearchBenchPreview preview = ResearchBenchPreview.EMPTY;

    public ResearchBenchMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, ContainerLevelAccess.create(
                inventory.player.level(), buffer.readBlockPos()));
    }

    public static ResearchBenchMenu server(
            int containerId,
            Inventory inventory,
            Level level,
            BlockPos pos) {
        return new ResearchBenchMenu(containerId, inventory, ContainerLevelAccess.create(level, pos));
    }

    private ResearchBenchMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.RESEARCH_BENCH.get(), containerId);
        this.access = access;
        this.owner = inventory.player;
        this.playerInventory = inventory;
        modeData.set(Mode.BROWSE.ordinal());
        addDataSlot(modeData);

        addSlot(new Slot(recyclingInput, 0, Layout.RECYCLING_X, Layout.RECYCLING_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return BlueprintItem.getBlueprintId(stack).isPresent();
            }

            @Override
            public boolean isActive() {
                return mode() == Mode.RECYCLE;
            }
        });
        addPlayerSlots(inventory);

        recyclingInput.addListener(ignored -> inputsChanged());
    }

    public ResearchBenchPreview preview() {
        return preview;
    }

    public Optional<ResourceLocation> selectedBlueprint() {
        return Optional.ofNullable(selectedBlueprint);
    }

    public Mode mode() {
        int ordinal = modeData.get();
        return ordinal >= 0 && ordinal < Mode.values().length
                ? Mode.values()[ordinal]
                : Mode.BROWSE;
    }

    /** Applies immediately on the client; the matching action remains server-authoritative. */
    public void setClientMode(Mode mode) {
        if (owner.level().isClientSide && mode != null) {
            modeData.set(mode.ordinal());
        }
    }

    public void acceptPreview(ResearchBenchPreview preview) {
        this.preview = preview == null ? ResearchBenchPreview.EMPTY : preview;
        this.selectedBlueprint = this.preview.blueprintId().orElse(null);
    }

    public void refreshAuthoritativePreview(ServerPlayer player) {
        if (player != null && player.containerMenu == this && stillValid(player)) {
            refreshPreview(player);
        }
    }

    public void handleAction(ServerPlayer player, Action action, Optional<ResourceLocation> requestedId) {
        if (player == null || action == null || player.containerMenu != this || !stillValid(player)) {
            return;
        }
        Optional<ResourceLocation> physicalRecyclingBlueprint =
                BlueprintItem.getBlueprintId(recyclingInput.getItem(0));
        if (!ResearchBenchActionValidator.accepts(
                mode(),
                action,
                selectedBlueprint(),
                requestedId,
                physicalRecyclingBlueprint)) {
            return;
        }
        switch (action) {
            case SELECT -> select(player, requestedId.orElse(null));
            case RESEARCH -> research(player, requestedId.orElse(null));
            case RECYCLE -> recycle(player, requestedId);
            case SHOW_BROWSE -> setMode(player, Mode.BROWSE);
            case SHOW_RECYCLE -> setMode(player, Mode.RECYCLE);
        }
    }

    private void setMode(ServerPlayer player, Mode mode) {
        modeData.set(mode.ordinal());
        refreshPreview(player);
    }

    private void select(ServerPlayer player, ResourceLocation blueprintId) {
        if (mode() != Mode.BROWSE) {
            return;
        }
        if (blueprintId == null) {
            selectedBlueprint = null;
        } else {
            BlueprintResearchPolicy policy = resolvePolicy(player, blueprintId).orElse(null);
            selectedBlueprint = policy != null
                    && policy.visibility().allowsServerSelection()
                    ? blueprintId
                    : null;
        }
        refreshPreview(player);
    }

    private void research(ServerPlayer player, ResourceLocation requestedId) {
        BlueprintResearchService.Result transaction;
        if (mode() != Mode.BROWSE
                || requestedId == null
                || !requestedId.equals(selectedBlueprint)) {
            transaction = new BlueprintResearchService.Result(
                    BlueprintResearchService.Status.INVALID_INPUT,
                    Optional.ofNullable(requestedId), 0,
                    player.getCapability(com.gamergaming.taczweaponblueprints.init.ModCapabilities.PLAYER_RECIPE_DATA)
                            .map(data -> data.getResearchPoints()).orElse(0),
                    false);
        } else {
            transaction = BlueprintResearchService.researchFromInventory(player, requestedId);
        }
        player.displayClientMessage(Component.translatable(researchMessage(transaction.status())), true);
        if (transaction.successful()) {
            selectedBlueprint = null;
            modeData.set(Mode.BROWSE.ordinal());
        }
        refreshPreview(player);
    }

    private void recycle(ServerPlayer player, Optional<ResourceLocation> requestedId) {
        ItemStack physical = recyclingInput.getItem(0);
        Optional<ResourceLocation> physicalId = BlueprintItem.getBlueprintId(physical);
        BlueprintRecyclingService.Result transaction;
        if (mode() != Mode.RECYCLE
                || requestedId == null
                || requestedId.isEmpty()
                || !requestedId.equals(physicalId)) {
            transaction = new BlueprintRecyclingService.Result(
                    BlueprintRecyclingService.Status.INVALID_INPUT,
                    physicalId,
                    0,
                    player.getCapability(com.gamergaming.taczweaponblueprints.init.ModCapabilities.PLAYER_RECIPE_DATA)
                            .map(data -> data.getResearchPoints()).orElse(0));
        } else {
            transaction = BlueprintRecyclingService.recycle(player, physical);
        }
        recyclingInput.setChanged();
        player.displayClientMessage(Component.translatable(recyclingMessage(transaction.status())), true);
        refreshPreview(player);
    }

    private void inputsChanged() {
        if (owner instanceof ServerPlayer serverPlayer
                && serverPlayer.containerMenu == this) {
            refreshPreview(serverPlayer);
        }
    }

    private void refreshPreview(ServerPlayer player) {
        ResearchBenchPreview next = buildPreview(player);
        preview = next;
        broadcastChanges();
        NetworkHandler.sendResearchBenchPreview(player, containerId, next);
    }

    private ResearchBenchPreview buildPreview(ServerPlayer player) {
        BlueprintRecyclingService.Evaluation recycling = BlueprintRecyclingService.evaluate(
                player, recyclingInput.getItem(0));
        ResearchBenchPreview.RecyclingPreview recyclingPreview =
                new ResearchBenchPreview.RecyclingPreview(
                        recycling.blueprintId(),
                        recycling.status(),
                        recycling.pointValue(),
                        recycling.currentBalance(),
                        recycling.pointCap());
        if (selectedBlueprint == null) {
            return ResearchBenchPreview.EMPTY.withRecycling(recyclingPreview);
        }
        var data = player.getCapability(com.gamergaming.taczweaponblueprints.init.ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        if (data == null) {
            return ResearchBenchPreview.EMPTY.withRecycling(recyclingPreview);
        }
        BlueprintResearchPolicy policy = resolvePolicy(player, selectedBlueprint).orElse(null);
        if (policy == null) {
            return ResearchBenchPreview.EMPTY.withRecycling(recyclingPreview);
        }
        if (!policy.visibility().allowsServerSelection()) {
            return ResearchBenchPreview.EMPTY.withRecycling(recyclingPreview);
        }
        List<ItemStack> inventoryStacks = playerInventory.items.stream().map(ItemStack::copy).toList();
        ResearchIngredientPlanner.Allocation inventoryAllocation =
                ResearchIngredientPlanner.allocation(inventoryStacks, policy.researchCost()).orElseThrow();
        List<ResearchBenchPreview.IngredientPreview> ingredients = new ArrayList<>();
        for (int ingredientIndex = 0;
                ingredientIndex < policy.researchCost().ingredients().size();
                ingredientIndex++) {
            BlueprintResearchIngredient ingredient = policy.researchCost().ingredients().get(ingredientIndex);
            List<ResourceLocation> items = ingredient.items();
            if (items.isEmpty() && ingredient.tag().isPresent()) {
                items = ForgeRegistries.ITEMS.tags()
                        .getTag(TagKey.create(Registries.ITEM, ingredient.tag().orElseThrow()))
                        .stream()
                        .map(ForgeRegistries.ITEMS::getKey)
                        .filter(java.util.Objects::nonNull)
                        .limit(BlueprintResearchIngredient.MAX_ITEMS)
                        .toList();
            }
            ingredients.add(new ResearchBenchPreview.IngredientPreview(
                    items,
                    ingredient.tag(),
                    ingredient.count(),
                    inventoryAllocation.allocatedForIngredient(ingredientIndex)));
        }
        boolean bypass = player.isCreative() && policy.creativeBypassesCost();
        boolean ingredientsSatisfied = bypass
                || inventoryAllocation.complete();
        boolean outputSpace = true;
        boolean policyEligible = policy.researchable();
        boolean ready = policyEligible
                && (bypass || policy.canAffordPoints())
                && ingredientsSatisfied
                && outputSpace;
        return new ResearchBenchPreview(
                Optional.of(selectedBlueprint),
                policy.researchCost().points(),
                data.getResearchPoints(),
                policyEligible,
                ingredientsSatisfied,
                outputSpace,
                ready,
                bypass,
                ingredients,
                recyclingPreview);
    }

    private static Optional<BlueprintResearchPolicy> resolvePolicy(
            ServerPlayer player,
            ResourceLocation blueprintId) {
        try {
            var data = player.getCapability(
                    com.gamergaming.taczweaponblueprints.init.ModCapabilities.PLAYER_RECIPE_DATA)
                    .resolve().orElse(null);
            return data == null
                    ? Optional.empty()
                    : Optional.ofNullable(BlueprintResearchDataManager.INSTANCE.policyFor(blueprintId, data));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.RESEARCH_BENCH.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.isActive() && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            if (index < FIRST_PLAYER_SLOT) {
                if (!moveItemStackTo(stack, FIRST_PLAYER_SLOT, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (mode() == Mode.RECYCLE
                    && BlueprintItem.getBlueprintId(stack).isPresent()) {
                if (!moveItemStackTo(stack, RECYCLING_SLOT, RECYCLING_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == moved.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return moved;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            clearContainer(player, recyclingInput);
        }
    }

    private void addPlayerSlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        inventory,
                        column + row * 9 + 9,
                        Layout.PLAYER_X + column * Layout.PLAYER_SPACING,
                        Layout.PLAYER_Y + row * Layout.PLAYER_SPACING) {
                    @Override
                    public boolean isActive() {
                        return ResearchBenchMenu.this.mode() == Mode.RECYCLE;
                    }
                });
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    inventory,
                    column,
                    Layout.PLAYER_X + column * Layout.PLAYER_SPACING,
                    Layout.HOTBAR_Y) {
                @Override
                public boolean isActive() {
                    return ResearchBenchMenu.this.mode() == Mode.RECYCLE;
                }
            });
        }
    }

    private static String researchMessage(BlueprintResearchService.Status status) {
        return "message.taczweaponblueprints.research." + status.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String recyclingMessage(BlueprintRecyclingService.Status status) {
        return "message.taczweaponblueprints.recycling." + status.name().toLowerCase(java.util.Locale.ROOT);
    }

    public enum Action {
        SELECT,
        RESEARCH,
        RECYCLE,
        SHOW_BROWSE,
        SHOW_RECYCLE
    }

    public enum Mode {
        BROWSE,
        RECYCLE
    }
}
