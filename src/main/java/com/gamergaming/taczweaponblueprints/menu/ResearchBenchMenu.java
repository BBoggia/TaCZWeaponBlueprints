package com.gamergaming.taczweaponblueprints.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModBlocks;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.init.ModMenus;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.BlueprintLearningService;
import com.gamergaming.taczweaponblueprints.progression.BlueprintUnlockOrigin;
import com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode;
import com.gamergaming.taczweaponblueprints.progression.BlueprintResearchService;
import com.gamergaming.taczweaponblueprints.progression.ResearchIngredientPlanner;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/** Research-only, slotless server menu for the permanent Research Tree. */
public final class ResearchBenchMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final Player owner;
    private final Inventory playerInventory;
    private ResourceLocation selectedBlueprint;
    private ResearchSelectionPreview preview = ResearchSelectionPreview.EMPTY;
    private ResearchInventorySnapshot previewInventory = ResearchInventorySnapshot.EMPTY;

    public ResearchBenchMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, ContainerLevelAccess.create(
                inventory.player.level(), buffer.readBlockPos()));
    }

    public static ResearchBenchMenu server(
            int containerId,
            Inventory inventory,
            Level level,
            BlockPos pos) {
        return new ResearchBenchMenu(
                containerId, inventory, ContainerLevelAccess.create(level, pos));
    }

    private ResearchBenchMenu(
            int containerId,
            Inventory inventory,
            ContainerLevelAccess access) {
        super(ModMenus.RESEARCH_BENCH.get(), containerId);
        this.access = access;
        this.owner = inventory.player;
        this.playerInventory = inventory;
    }

    public ResearchSelectionPreview preview() {
        return preview;
    }

    public Optional<ResourceLocation> selectedBlueprint() {
        return Optional.ofNullable(selectedBlueprint);
    }

    public void acceptPreview(ResearchSelectionPreview preview) {
        this.preview = preview == null ? ResearchSelectionPreview.EMPTY : preview;
        this.selectedBlueprint = this.preview.blueprintId().orElse(null);
    }

    public void refreshAuthoritativePreview(ServerPlayer player) {
        if (player != null && player.containerMenu == this && stillValid(player)) {
            refreshPreview(player);
        }
    }

    public Optional<ActionResult> handleAction(
            ServerPlayer player,
            ResearchBenchResearchAction action,
            Optional<ResourceLocation> requestedId) {
        if (player == null || action == null
                || player.containerMenu != this || !stillValid(player)) {
            return Optional.empty();
        }
        if (!ResearchBenchResearchActionValidator.accepts(
                action, selectedBlueprint(), requestedId)) {
            return Optional.of(new ActionResult(
                    action,
                    requestedId,
                    ActionResultCode.INVALID_INPUT));
        }
        return Optional.of(switch (action) {
            case SELECT -> select(player, requestedId == null
                    ? null
                    : requestedId.orElse(null));
            case RESEARCH -> research(player, requestedId.orElseThrow());
        });
    }

    private ActionResult select(ServerPlayer player, ResourceLocation blueprintId) {
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
        boolean accepted = blueprintId == null
                ? selectedBlueprint == null
                : blueprintId.equals(selectedBlueprint);
        return new ActionResult(
                ResearchBenchResearchAction.SELECT,
                Optional.ofNullable(blueprintId),
                accepted ? ActionResultCode.ACCEPTED : ActionResultCode.REJECTED);
    }

    private ActionResult research(ServerPlayer player, ResourceLocation requestedId) {
        BlueprintResearchService.Result transaction = requestedId.equals(selectedBlueprint)
                ? BlueprintResearchService.researchFromInventory(player, requestedId)
                : new BlueprintResearchService.Result(
                        BlueprintResearchService.Status.INVALID_INPUT,
                        Optional.of(requestedId),
                        0,
                        player.getCapability(
                                com.gamergaming.taczweaponblueprints.init.ModCapabilities.PLAYER_RECIPE_DATA)
                                .map(data -> data.getResearchPoints()).orElse(0),
                        false);
        player.displayClientMessage(
                Component.translatable(researchMessage(transaction.status())), true);
        if (transaction.successful()) {
            selectedBlueprint = null;
        }
        refreshPreview(player);
        return new ActionResult(
                ResearchBenchResearchAction.RESEARCH,
                Optional.of(requestedId),
                ActionResultCode.valueOf(transaction.status().name()));
    }

    /**
     * Menus are broadcast every server tick. Refresh only when allocation-relevant
     * inventory contents changed while a blueprint is selected.
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (selectedBlueprint != null
                && owner instanceof ServerPlayer serverPlayer
                && serverPlayer.containerMenu == this
                && stillValid(serverPlayer)
                && !previewInventory.matches(playerInventory.items)) {
            refreshPreview(serverPlayer);
        }
    }

    private void refreshPreview(ServerPlayer player) {
        ResearchSelectionPreview next = buildPreview(player);
        preview = next;
        previewInventory = ResearchInventorySnapshot.capture(playerInventory.items);
        super.broadcastChanges();
        NetworkHandler.sendResearchBenchPreview(player, containerId, next);
    }

    private ResearchSelectionPreview buildPreview(ServerPlayer player) {
        if (selectedBlueprint == null) {
            return ResearchSelectionPreview.EMPTY;
        }
        if (com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionAccess
                .isProgressionExempt(selectedBlueprint)) {
            return ResearchSelectionPreview.EMPTY;
        }
        var data = player.getCapability(
                com.gamergaming.taczweaponblueprints.init.ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        if (data == null) {
            return ResearchSelectionPreview.EMPTY;
        }
        BlueprintResearchPolicy policy = resolvePolicy(player, selectedBlueprint).orElse(null);
        if (policy == null || !policy.visibility().allowsServerSelection()) {
            return ResearchSelectionPreview.EMPTY;
        }
        List<ItemStack> inventoryStacks = playerInventory.items.stream()
                .map(ItemStack::copy)
                .toList();
        ResearchIngredientPlanner.Allocation inventoryAllocation =
                ResearchIngredientPlanner.allocation(
                        inventoryStacks, policy.researchCost()).orElseThrow();
        List<ResearchSelectionPreview.IngredientPreview> ingredients = new ArrayList<>();
        for (int ingredientIndex = 0;
                ingredientIndex < policy.researchCost().ingredients().size();
                ingredientIndex++) {
            BlueprintResearchIngredient ingredient =
                    policy.researchCost().ingredients().get(ingredientIndex);
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
            ingredients.add(new ResearchSelectionPreview.IngredientPreview(
                    items,
                    ingredient.tag(),
                    ingredient.count(),
                    inventoryAllocation.allocatedForIngredient(ingredientIndex)));
        }
        boolean bypass = player.isCreative() && policy.creativeBypassesCost();
        boolean ingredientsSatisfied = bypass || inventoryAllocation.complete();
        boolean policyEligible = policy.researchable();
        boolean transactionCapacityAvailable = true;
        var config = ModConfigs.BLUEPRINT.progressionSnapshot();
        if (policyEligible && config.treeResearchResultMode().learnsDirectly()) {
            BlueprintLearningService.Preparation preparation =
                    BlueprintLearningService.prepare(
                            new BlueprintLearningService.Request(
                                    BlueprintUnlockOrigin.TREE_RESEARCH,
                                    selectedBlueprint,
                                    config.blueprintsEnabled(),
                                    PhysicalBlueprintLearningMode.DISABLED,
                                    com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionAccess
                                            .isProgressionExempt(selectedBlueprint)),
                            data,
                            id -> BlueprintLearningService.targetFromCatalog(
                                    BlueprintDataManager.SERVER, id),
                            ignored -> policy);
            if (!preparation.ready()) {
                BlueprintLearningService.Status failure = preparation.failure()
                        .orElseThrow().status();
                if (failure == BlueprintLearningService.Status
                        .PROGRESSION_CAPACITY_EXHAUSTED) {
                    // The legacy field remains on the unchanged protocol-25
                    // preview wire. In direct mode it represents capacity for
                    // the transaction's non-economic result rather than a
                    // physical output slot.
                    transactionCapacityAvailable = false;
                } else {
                    policyEligible = false;
                }
            }
        }
        boolean ready = policyEligible
                && (bypass || policy.canAffordPoints())
                && ingredientsSatisfied
                && transactionCapacityAvailable;
        return new ResearchSelectionPreview(
                Optional.of(selectedBlueprint),
                policy.researchCost().points(),
                data.getResearchPoints(),
                policyEligible,
                ingredientsSatisfied,
                transactionCapacityAvailable,
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
                    : Optional.ofNullable(
                            BlueprintResearchDataManager.INSTANCE.policyFor(blueprintId, data));
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
        return ItemStack.EMPTY;
    }

    private static String researchMessage(BlueprintResearchService.Status status) {
        return "message.taczweaponblueprints.research."
                + status.name().toLowerCase(java.util.Locale.ROOT);
    }

    public enum ActionResultCode {
        ACCEPTED,
        REJECTED,
        SUCCESS,
        INVALID_INPUT,
        PLAYER_DATA_UNAVAILABLE,
        POLICY_UNAVAILABLE,
        POLICY_MISMATCH,
        STALE_POLICY,
        CONTENT_UNAVAILABLE,
        BLOCKED,
        RESEARCH_DISABLED,
        ALREADY_LEARNED,
        DISCOVERY_REQUIRED,
        PREREQUISITES_REQUIRED,
        POINTS_REQUIRED,
        INGREDIENTS_REQUIRED,
        OUTPUT_FULL,
        POLICY_INELIGIBLE,
        TRANSACTION_FAILED,
        PROGRESSION_CAPACITY_EXHAUSTED,
        ROLLBACK_FAILED
    }

    public record ActionResult(
            ResearchBenchResearchAction action,
            Optional<ResourceLocation> blueprintId,
            ActionResultCode code) {
        public ActionResult {
            blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
            if (action == null || code == null
                    || blueprintId.filter(id -> id.toString().length()
                            > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH).isPresent()
                    || (action == ResearchBenchResearchAction.SELECT
                            && code != ActionResultCode.ACCEPTED
                            && code != ActionResultCode.REJECTED
                            && code != ActionResultCode.INVALID_INPUT)
                    || (action == ResearchBenchResearchAction.RESEARCH
                            && (code == ActionResultCode.ACCEPTED
                                    || code == ActionResultCode.REJECTED))) {
                throw new IllegalArgumentException("invalid Research Bench action result");
            }
        }

        public boolean successful() {
            return code == ActionResultCode.ACCEPTED || code == ActionResultCode.SUCCESS;
        }
    }
}
