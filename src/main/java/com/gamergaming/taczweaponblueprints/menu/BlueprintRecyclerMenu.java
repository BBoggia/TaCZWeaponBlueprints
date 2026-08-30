package com.gamergaming.taczweaponblueprints.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.init.ModBlocks;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModMenus;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.item.BlueprintProvenance;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.BlueprintRecyclingService;
import com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService;
import com.gamergaming.taczweaponblueprints.progression.ResearchIngredientPlanner;
import com.gamergaming.taczweaponblueprints.progression.ResearchDataRedemptionService;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.tags.TagKey;
import net.minecraftforge.registries.ForgeRegistries;

/** Server-authoritative two-slot transaction menu for the Blueprint Analyzer. */
public final class BlueprintRecyclerMenu extends AbstractContainerMenu
        implements BlueprintRecyclerMenuBridge {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int FIRST_PLAYER_SLOT = 2;

    public static final class Layout {
        public static final int PANEL_WIDTH = 304;
        public static final int PANEL_HEIGHT = 224;
        public static final int SECTION_X = 8;
        public static final int SECTION_Y = 22;
        public static final int SECTION_WIDTH = PANEL_WIDTH - SECTION_X * 2;
        public static final int SECTION_HEIGHT = 100;
        public static final int DIVIDER_X = 112;
        public static final int DETAIL_X = 122;
        public static final int DETAIL_WIDTH = 166;
        public static final int ACTION_Y = 99;
        public static final int ACTION_HEIGHT = 20;
        public static final int ACTION_GAP = 4;
        public static final int INPUT_X = 24;
        public static final int INPUT_Y = 59;
        public static final int OUTPUT_X = 72;
        public static final int OUTPUT_Y = 59;
        public static final int PLAYER_X = 71;
        public static final int PLAYER_LABEL_Y = 128;
        public static final int PLAYER_Y = 140;
        public static final int PLAYER_SPACING = 18;
        public static final int HOTBAR_Y = 198;

        private Layout() {
        }
    }

    private final SimpleContainer workstation = new SimpleContainer(2);
    private final ContainerLevelAccess access;
    private final Player owner;
    private final Inventory playerInventory;
    private BlueprintRecyclerPreview preview = BlueprintRecyclerPreview.EMPTY;
    private boolean previewInitialized;
    private boolean closed;
    private long stateToken;
    private WorkstationState observedState;
    private boolean mutationInProgress;

    public BlueprintRecyclerMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(
                containerId,
                inventory,
                ContainerLevelAccess.create(inventory.player.level(), buffer.readBlockPos()));
    }

    public static BlueprintRecyclerMenu server(
            int containerId,
            Inventory inventory,
            Level level,
            BlockPos pos) {
        return new BlueprintRecyclerMenu(
                containerId, inventory, ContainerLevelAccess.create(level, pos));
    }

    private BlueprintRecyclerMenu(
            int containerId,
            Inventory inventory,
            ContainerLevelAccess access) {
        super(ModMenus.BLUEPRINT_RECYCLER.get(), containerId);
        this.access = access;
        this.owner = inventory.player;
        this.playerInventory = inventory;
        addSlot(new Slot(workstation, INPUT_SLOT, Layout.INPUT_X, Layout.INPUT_Y));
        addSlot(new Slot(workstation, OUTPUT_SLOT, Layout.OUTPUT_X, Layout.OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                super.onTake(player, stack);
                workstation.setChanged();
            }
        });
        addPlayerSlots(inventory);
        workstation.addListener(ignored -> inputsChanged());
    }

    public BlueprintRecyclerPreview preview() {
        return preview;
    }

    public ItemStack inputStack() {
        return workstation.getItem(INPUT_SLOT);
    }

    public ItemStack outputStack() {
        return workstation.getItem(OUTPUT_SLOT);
    }

    @Override
    public void acceptRecyclerPreview(BlueprintRecyclerPreview preview) {
        this.preview = preview == null ? BlueprintRecyclerPreview.EMPTY : preview;
        this.previewInitialized = true;
    }

    public void refreshAuthoritativePreview(ServerPlayer player) {
        if (!closed && player != null && player.containerMenu == this && stillValid(player)) {
            refreshPreview(player);
        }
    }

    @Override
    public boolean isRecyclerMenuValid(Player player) {
        return !closed && player != null && player.containerMenu == this && stillValid(player);
    }

    @Override
    public BlueprintRecyclerActionContract.ActionResult handleRecyclerAction(
            ServerPlayer player,
            BlueprintRecyclerActionContract.Action action,
            ResourceLocation expectedInputId,
            int expectedInputCount,
            long expectedStateToken) {
        if (player == null || action == null || !isRecyclerMenuValid(player)) {
            return failure(action, expectedInputId,
                    BlueprintRecyclerActionContract.ResultCode.INVALID_INPUT);
        }

        ItemStack physicalInput = workstation.getItem(INPUT_SLOT);
        Optional<ResourceLocation> blueprintId = BlueprintItem.getBlueprintId(physicalInput);
        BlueprintReverseEngineeringService.Evaluation reverse = blueprintId.isEmpty()
                ? BlueprintReverseEngineeringService.evaluate(player, workstationTransaction())
                : null;
        ResearchDataRedemptionService.Evaluation researchData = blueprintId.isEmpty()
                        && (reverse == null || reverse.blueprintId().isEmpty())
                ? ResearchDataRedemptionService.evaluateInput(player, physicalInput)
                : null;
        Optional<ResourceLocation> physicalId = blueprintId.isPresent()
                ? blueprintId
                : reverse != null && reverse.blueprintId().isPresent()
                        ? reverse.blueprintId()
                        : itemId(physicalInput);
        if (!BlueprintRecyclerActionValidator.matchesInput(
                expectedInputId,
                expectedInputCount,
                expectedStateToken,
                stateToken,
                physicalId,
                physicalInput.getCount())) {
            refreshPreview(player);
            return failure(action, expectedInputId,
                    BlueprintRecyclerActionContract.ResultCode.STALE_INPUT);
        }

        BlueprintRecyclerPreview.InputKind inputKind =
                classifyInput(blueprintId, researchData, reverse);
        if (!BlueprintRecyclerActionValidator.supports(inputKind, action)) {
            refreshPreview(player);
            return failure(action, expectedInputId,
                    BlueprintRecyclerActionContract.ResultCode.INVALID_INPUT);
        }

        BlueprintRecyclerActionContract.ResultCode resultCode;
        if (action == BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER) {
            mutationInProgress = true;
            try {
                BlueprintReverseEngineeringService.Result result =
                        BlueprintReverseEngineeringService.reverseEngineer(
                                player, workstationTransaction());
                resultCode = BlueprintRecyclerActionContract.ResultCode.from(result.status());
            } finally {
                mutationInProgress = false;
            }
        } else if (blueprintId.isPresent()) {
            BlueprintRecyclingService.Result result =
                    BlueprintRecyclingService.recycle(player, physicalInput);
            resultCode = BlueprintRecyclerActionContract.ResultCode.from(result.status());
        } else {
            ResearchDataRedemptionService.Result result =
                    ResearchDataRedemptionService.redeemInput(
                            player,
                            physicalInput,
                            expectedInputId,
                            expectedInputCount,
                            action == BlueprintRecyclerActionContract.Action.REDEEM_STACK);
            resultCode = BlueprintRecyclerActionContract.ResultCode.from(result.status());
        }

        workstation.setChanged();
        refreshPreview(player);
        return new BlueprintRecyclerActionContract.ActionResult(
                action, Optional.of(expectedInputId), resultCode);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!closed
                && (!previewInitialized || !WorkstationState.capture(
                        workstation,
                        playerInventory,
                        owner).matches(observedState))
                && owner instanceof ServerPlayer serverPlayer
                && serverPlayer.containerMenu == this
                && stillValid(serverPlayer)) {
            refreshPreview(serverPlayer);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.BLUEPRINT_RECYCLER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Optional<TransferPlan> transfer = transferPlan(index, slots.size());
        if (transfer.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        TransferPlan target = transfer.orElseThrow();
        if (!moveItemStackTo(
                stack,
                target.startInclusive(),
                target.endExclusive(),
                target.reverse())) {
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
        return moved;
    }

    static Optional<TransferPlan> transferPlan(int sourceIndex, int slotCount) {
        if (slotCount <= FIRST_PLAYER_SLOT
                || sourceIndex < INPUT_SLOT
                || sourceIndex >= slotCount) {
            return Optional.empty();
        }
        return sourceIndex == INPUT_SLOT || sourceIndex == OUTPUT_SLOT
                ? Optional.of(new TransferPlan(FIRST_PLAYER_SLOT, slotCount, true))
                : Optional.of(new TransferPlan(INPUT_SLOT, INPUT_SLOT + 1, false));
    }

    @Override
    public void removed(Player player) {
        closed = true;
        super.removed(player);
        if (!player.level().isClientSide) {
            clearContainer(player, workstation);
        }
    }

    private void inputsChanged() {
        if (!closed && !mutationInProgress
                && owner instanceof ServerPlayer serverPlayer
                && serverPlayer.containerMenu == this
                && stillValid(serverPlayer)) {
            refreshPreview(serverPlayer);
        }
    }

    private void refreshPreview(ServerPlayer player) {
        BlueprintRecyclerPreview next = buildPreview(player);
        boolean changed = !previewInitialized
                || !next.equals(preview.withStateToken(0L));
        if (changed) {
            stateToken = stateToken == Long.MAX_VALUE ? 1L : stateToken + 1L;
        }
        preview = next.withStateToken(stateToken);
        previewInitialized = true;
        observedState = WorkstationState.capture(workstation, playerInventory, owner);
        if (changed) {
            super.broadcastChanges();
            NetworkHandler.sendBlueprintRecyclerPreview(player, containerId, preview);
        }
    }

    private BlueprintRecyclerPreview buildPreview(ServerPlayer player) {
        ItemStack physicalInput = workstation.getItem(INPUT_SLOT);
        ResearchDataRedemptionService.Evaluation researchData =
                ResearchDataRedemptionService.evaluateInput(player, physicalInput);
        if (physicalInput.isEmpty()) {
            return BlueprintRecyclerPreview.empty(
                    researchData.pointBalance(), researchData.pointCap());
        }

        Optional<ResourceLocation> blueprintId = BlueprintItem.getBlueprintId(physicalInput);
        if (blueprintId.isPresent()) {
            BlueprintRecyclingService.Evaluation recycling =
                    BlueprintRecyclingService.evaluate(player, physicalInput);
            return new BlueprintRecyclerPreview(
                    BlueprintRecyclerPreview.InputKind.BLUEPRINT,
                    recycling.blueprintId().isPresent() ? recycling.blueprintId() : blueprintId,
                    physicalInput.getCount(),
                    recycling.pointValue(),
                    recycling.currentBalance(),
                    recycling.pointCap(),
                    Optional.of(recycling.status()),
                    Optional.empty());
        }
        BlueprintReverseEngineeringService.Evaluation reverse =
                BlueprintReverseEngineeringService.evaluate(player, workstationTransaction());
        if (reverse.blueprintId().isPresent()) {
            List<BlueprintRecyclerPreview.IngredientPreview> ingredients =
                    reverseIngredients(reverse);
            return new BlueprintRecyclerPreview(
                    BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM,
                    reverse.blueprintId(),
                    physicalInput.getCount(),
                    0,
                    reverse.pointBalance(),
                    reverse.pointCap(),
                    Optional.empty(),
                    Optional.empty(),
                    0L,
                    reverse.blueprintId(),
                    reverse.requiredInputCount(),
                    reverse.cost().points(),
                    reverse.ingredientsSatisfied(),
                    reverse.outputAvailable(),
                    reverse.customizationWillBeLost(),
                    reverse.alreadyKnown(),
                    Optional.of(reverse.status()),
                    ingredients);
        }
        if (researchData.matchedInput()) {
            return new BlueprintRecyclerPreview(
                    BlueprintRecyclerPreview.InputKind.RESEARCH_DATA,
                    researchData.itemId(),
                    physicalInput.getCount(),
                    researchData.pointValue(),
                    researchData.pointBalance(),
                    researchData.pointCap(),
                    Optional.empty(),
                    Optional.of(researchData.status()));
        }
        return BlueprintRecyclerPreview.invalid(
                itemId(physicalInput),
                physicalInput.getCount(),
                researchData.pointBalance(),
                researchData.pointCap());
    }

    private void addPlayerSlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        inventory,
                        column + row * 9 + 9,
                        Layout.PLAYER_X + column * Layout.PLAYER_SPACING,
                        Layout.PLAYER_Y + row * Layout.PLAYER_SPACING));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    inventory,
                    column,
                    Layout.PLAYER_X + column * Layout.PLAYER_SPACING,
                    Layout.HOTBAR_Y));
        }
    }

    private static Optional<ResourceLocation> itemId(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? Optional.empty()
                : Optional.ofNullable(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    static BlueprintRecyclerPreview.InputKind classifyInput(
            Optional<ResourceLocation> blueprintId,
            ResearchDataRedemptionService.Evaluation researchData) {
        return classifyInput(blueprintId, researchData, null);
    }

    static BlueprintRecyclerPreview.InputKind classifyInput(
            Optional<ResourceLocation> blueprintId,
            ResearchDataRedemptionService.Evaluation researchData,
            BlueprintReverseEngineeringService.Evaluation reverse) {
        if (blueprintId != null && blueprintId.isPresent()) {
            return BlueprintRecyclerPreview.InputKind.BLUEPRINT;
        }
        if (reverse != null && reverse.blueprintId().isPresent()) {
            return BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM;
        }
        if (researchData != null && researchData.matchedInput()) {
            return BlueprintRecyclerPreview.InputKind.RESEARCH_DATA;
        }
        return BlueprintRecyclerPreview.InputKind.INVALID;
    }

    private List<BlueprintRecyclerPreview.IngredientPreview> reverseIngredients(
            BlueprintReverseEngineeringService.Evaluation reverse) {
        List<BlueprintRecyclerPreview.IngredientPreview> ingredients = new ArrayList<>();
        for (int index = 0; index < reverse.cost().ingredients().size(); index++) {
            int ingredientIndex = index;
            BlueprintResearchIngredient ingredient = reverse.cost().ingredients().get(index);
            List<ResourceLocation> items = ingredient.items();
            if (items.isEmpty() && ingredient.tag().isPresent()) {
                items = ForgeRegistries.ITEMS.tags()
                        .getTag(TagKey.create(
                                Registries.ITEM, ingredient.tag().orElseThrow()))
                        .stream()
                        .map(ForgeRegistries.ITEMS::getKey)
                        .filter(java.util.Objects::nonNull)
                        .limit(BlueprintResearchIngredient.MAX_ITEMS)
                        .toList();
            }
            int available = reverse.allocation()
                    .map(value -> value.allocatedForIngredient(ingredientIndex))
                    .orElse(0);
            ingredients.add(new BlueprintRecyclerPreview.IngredientPreview(
                    items,
                    ingredient.tag(),
                    ingredient.count(),
                    available));
        }
        return List.copyOf(ingredients);
    }

    private BlueprintReverseEngineeringService.WorkstationTransaction workstationTransaction() {
        return new BlueprintReverseEngineeringService.WorkstationTransaction() {
            @Override
            public ItemStack physicalInput() {
                return workstation.getItem(INPUT_SLOT);
            }

            @Override
            public ItemStack outputStack() {
                return workstation.getItem(OUTPUT_SLOT);
            }

            @Override
            public List<ItemStack> inventoryStacks() {
                return playerInventory.items.stream().map(ItemStack::copy).toList();
            }

            @Override
            public ItemStack createOutput(
                    ResourceLocation blueprintId,
                    BlueprintProvenance provenance) {
                return BlueprintItem.createBlueprint(blueprintId.toString(), provenance);
            }

            @Override
            public boolean consumeMaterials(
                    ResearchIngredientPlanner.Plan plan,
                    List<ItemStack> expectedInventory) {
                List<ItemStack> items = playerInventory.items;
                if (plan == null || expectedInventory == null
                        || plan.slotCount() != items.size()
                        || expectedInventory.size() != items.size()) {
                    return false;
                }
                for (int index = 0; index < items.size(); index++) {
                    if (!ItemStack.matches(expectedInventory.get(index), items.get(index))
                            || plan.decrement(index) > items.get(index).getCount()) {
                        return false;
                    }
                }
                for (int index = 0; index < items.size(); index++) {
                    items.get(index).shrink(plan.decrement(index));
                }
                playerInventory.setChanged();
                return true;
            }

            @Override
            public boolean consumePhysical(ItemStack expectedInput, int count) {
                ItemStack current = workstation.getItem(INPUT_SLOT);
                if (expectedInput == null || count < 1
                        || !ItemStack.matches(expectedInput, current)
                        || current.getCount() < count) {
                    return false;
                }
                current.shrink(count);
                workstation.setChanged();
                return true;
            }

            @Override
            public boolean placeOutput(ItemStack output, ItemStack expectedOutput) {
                ItemStack current = workstation.getItem(OUTPUT_SLOT);
                if (output == null || output.isEmpty() || output.getCount() != 1
                        || expectedOutput == null
                        || !ItemStack.matches(expectedOutput, current)
                        || !current.isEmpty()) {
                    return false;
                }
                workstation.setItem(OUTPUT_SLOT, output.copy());
                return ItemStack.matches(output, workstation.getItem(OUTPUT_SLOT));
            }

            @Override
            public boolean restore(
                    ItemStack physicalInput,
                    List<ItemStack> inventory,
                    ItemStack output) {
                if (physicalInput == null || inventory == null || output == null
                        || inventory.size() != playerInventory.items.size()) {
                    return false;
                }
                workstation.setItem(INPUT_SLOT, physicalInput.copy());
                workstation.setItem(OUTPUT_SLOT, output.copy());
                for (int index = 0; index < inventory.size(); index++) {
                    playerInventory.items.set(index, inventory.get(index).copy());
                }
                playerInventory.setChanged();
                workstation.setChanged();
                return ItemStack.matches(physicalInput, workstation.getItem(INPUT_SLOT))
                        && ItemStack.matches(output, workstation.getItem(OUTPUT_SLOT));
            }
        };
    }

    private static BlueprintRecyclerActionContract.ActionResult failure(
            BlueprintRecyclerActionContract.Action action,
            ResourceLocation inputId,
            BlueprintRecyclerActionContract.ResultCode code) {
        BlueprintRecyclerActionContract.Action safeAction = action == null
                ? BlueprintRecyclerActionContract.Action.RECYCLE
                : action;
        ResourceLocation safeInputId = inputId == null
                ? new ResourceLocation("taczweaponblueprints:invalid_recycler_input")
                : inputId;
        return new BlueprintRecyclerActionContract.ActionResult(
                safeAction, Optional.of(safeInputId), code);
    }

    record TransferPlan(int startInclusive, int endExclusive, boolean reverse) {
        TransferPlan {
            if (startInclusive < 0 || endExclusive <= startInclusive) {
                throw new IllegalArgumentException("invalid Blueprint Recycler transfer plan");
            }
        }
    }

    private record WorkstationState(
            List<ItemStack> stacks,
            int pointBalance,
            long researchRevision,
            long catalogRevision) {
        private WorkstationState {
            stacks = stacks.stream().map(ItemStack::copy).toList();
        }

        static WorkstationState capture(
                SimpleContainer workstation,
                Inventory inventory,
                Player owner) {
            List<ItemStack> stacks = new ArrayList<>(2 + inventory.items.size());
            stacks.add(workstation.getItem(INPUT_SLOT).copy());
            stacks.add(workstation.getItem(OUTPUT_SLOT).copy());
            inventory.items.stream().map(ItemStack::copy).forEach(stacks::add);
            int points = owner.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                    .map(data -> data.getResearchPoints())
                    .orElse(0);
            return new WorkstationState(
                    stacks,
                    points,
                    BlueprintResearchDataManager.INSTANCE.revision(),
                    BlueprintDataManager.SERVER.catalogRevision());
        }

        boolean matches(WorkstationState other) {
            if (other == null
                    || pointBalance != other.pointBalance
                    || researchRevision != other.researchRevision
                    || catalogRevision != other.catalogRevision
                    || stacks.size() != other.stacks.size()) {
                return false;
            }
            for (int index = 0; index < stacks.size(); index++) {
                if (!ItemStack.matches(stacks.get(index), other.stacks.get(index))) {
                    return false;
                }
            }
            return true;
        }
    }
}
