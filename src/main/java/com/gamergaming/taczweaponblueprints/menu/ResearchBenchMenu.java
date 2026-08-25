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
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

public final class ResearchBenchMenu extends AbstractContainerMenu {
    public static final int RECYCLING_SLOT = 0;
    public static final int FIRST_INGREDIENT_SLOT = 1;
    public static final int RESULT_SLOT = 7;
    public static final int FIRST_PLAYER_SLOT = 8;

    private final SimpleContainer recyclingInput = new SimpleContainer(1);
    private final SimpleContainer researchInputs = new SimpleContainer(BlueprintResearchService.INGREDIENT_SLOT_COUNT);
    private final ResultContainer result = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player owner;
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

        addSlot(new Slot(recyclingInput, 0, 108, 54) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return BlueprintItem.getBlueprintId(stack).isPresent();
            }
        });
        for (int index = 0; index < BlueprintResearchService.INGREDIENT_SLOT_COUNT; index++) {
            int x = 108 + (index % 3) * 20;
            int y = 84 + (index / 3) * 20;
            addSlot(new Slot(researchInputs, index, x, y));
        }
        addSlot(new Slot(result, 0, 108, 25) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        });
        addPlayerSlots(inventory);

        recyclingInput.addListener(ignored -> inputsChanged());
        researchInputs.addListener(ignored -> inputsChanged());
    }

    public ResearchBenchPreview preview() {
        return preview;
    }

    public Optional<ResourceLocation> selectedBlueprint() {
        return Optional.ofNullable(selectedBlueprint);
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
        switch (action) {
            case SELECT -> select(player, requestedId.orElse(null));
            case RESEARCH -> research(player, requestedId.orElse(null));
            case RECYCLE -> recycle(player, requestedId);
        }
    }

    private void select(ServerPlayer player, ResourceLocation blueprintId) {
        if (blueprintId == null) {
            selectedBlueprint = null;
        } else {
            BlueprintResearchPolicy policy = resolvePolicy(player, blueprintId).orElse(null);
            selectedBlueprint = policy != null
                    && policy.visibility().ordinal() >= JournalVisibility.PREVIEW.ordinal()
                    ? blueprintId
                    : null;
        }
        refreshPreview(player);
    }

    private void research(ServerPlayer player, ResourceLocation requestedId) {
        BlueprintResearchService.Result transaction;
        if (requestedId == null || !requestedId.equals(selectedBlueprint)) {
            transaction = new BlueprintResearchService.Result(
                    BlueprintResearchService.Status.INVALID_INPUT,
                    Optional.ofNullable(requestedId), 0,
                    player.getCapability(com.gamergaming.taczweaponblueprints.init.ModCapabilities.PLAYER_RECIPE_DATA)
                            .map(data -> data.getResearchPoints()).orElse(0),
                    false);
        } else {
            transaction = BlueprintResearchService.research(player, requestedId, researchInputs);
        }
        player.displayClientMessage(Component.translatable(researchMessage(transaction.status())), true);
        refreshPreview(player);
    }

    private void recycle(ServerPlayer player, Optional<ResourceLocation> requestedId) {
        ItemStack physical = recyclingInput.getItem(0);
        Optional<ResourceLocation> physicalId = BlueprintItem.getBlueprintId(physical);
        BlueprintRecyclingService.Result transaction;
        if (requestedId == null || requestedId.isEmpty() || !requestedId.equals(physicalId)) {
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
        if (owner instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu == this) {
            refreshPreview(serverPlayer);
        }
    }

    private void refreshPreview(ServerPlayer player) {
        ResearchBenchPreview next = buildPreview(player);
        preview = next;
        result.setItem(0, next.researchable() && next.blueprintId().isPresent()
                ? BlueprintItem.createBlueprint(next.blueprintId().orElseThrow().toString())
                : ItemStack.EMPTY);
        broadcastChanges();
        NetworkHandler.sendResearchBenchPreview(player, containerId, next);
    }

    private ResearchBenchPreview buildPreview(ServerPlayer player) {
        if (selectedBlueprint == null) {
            return ResearchBenchPreview.EMPTY;
        }
        var data = player.getCapability(com.gamergaming.taczweaponblueprints.init.ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        if (data == null) {
            return ResearchBenchPreview.EMPTY;
        }
        BlueprintResearchPolicy policy = resolvePolicy(player, selectedBlueprint).orElse(null);
        if (policy == null) {
            return ResearchBenchPreview.EMPTY;
        }
        if (policy.visibility().ordinal() < JournalVisibility.PREVIEW.ordinal()) {
            return ResearchBenchPreview.EMPTY;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < researchInputs.getContainerSize(); slot++) {
            stacks.add(researchInputs.getItem(slot));
        }
        List<ResearchBenchPreview.IngredientPreview> ingredients = new ArrayList<>();
        for (BlueprintResearchIngredient ingredient : policy.researchCost().ingredients()) {
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
                    ResearchIngredientPlanner.matchingCount(stacks, ingredient)));
        }
        boolean bypass = player.isCreative() && policy.creativeBypassesCost();
        boolean ingredientsSatisfied = bypass
                || ResearchIngredientPlanner.plan(stacks, policy.researchCost()).isPresent();
        boolean outputSpace = player.getInventory().getFreeSlot() >= 0;
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
                ingredients);
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
        if (index == RESULT_SLOT) {
            return ItemStack.EMPTY;
        }
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            if (index < FIRST_PLAYER_SLOT) {
                if (!moveItemStackTo(stack, FIRST_PLAYER_SLOT, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (BlueprintItem.getBlueprintId(stack).isPresent()) {
                if (!moveItemStackTo(stack, RECYCLING_SLOT, RECYCLING_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, FIRST_INGREDIENT_SLOT, RESULT_SLOT, false)) {
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
            clearContainer(player, researchInputs);
        }
    }

    private void addPlayerSlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 44 + column * 18, 139 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 44 + column * 18, 197));
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
        RECYCLE
    }
}
