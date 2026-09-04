package com.gamergaming.taczweaponblueprints.mixin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.client.BlueprintRecipeFilter;
import com.gamergaming.taczweaponblueprints.client.ClientCraftingAccessState;
import com.gamergaming.taczweaponblueprints.client.IBlueprintRecipeScreen;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.network.CraftingAccessRequestPacket;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.pojo.data.block.TabConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

@Mixin(GunSmithTableScreen.class)
public abstract class GunSmithTableScreenMixin
        extends AbstractContainerScreen<com.tacz.guns.inventory.GunSmithTableMenu>
        implements IBlueprintRecipeScreen {
    @Unique
    private static final int taczweaponblueprints$DIAGNOSTIC_SAMPLE_LIMIT = 8;

    protected GunSmithTableScreenMixin(
            com.tacz.guns.inventory.GunSmithTableMenu menu,
            Inventory inventory,
            Component title) {
        super(menu, inventory, title);
    }

    @Unique
    private boolean taczweaponblueprints$lastEnabled;

    @Unique
    private Set<String> taczweaponblueprints$lastLearnedRecipes = Set.of();

    @Unique
    private long taczweaponblueprints$lastCraftingAccessRevision = Long.MIN_VALUE;

    @Unique
    private long taczweaponblueprints$lastDiagnosticAccessRevision = Long.MIN_VALUE;

    /**
     * Network snapshots can arrive back-to-back on the client thread. Defer
     * their shared UI rebuild to the next render pass so one packet burst
     * produces one widget rebuild rather than repeatedly rebuilding the same
     * screen.
     */
    @Unique
    private boolean taczweaponblueprints$recipeRefreshQueued;

    @Unique
    private boolean taczweaponblueprints$rebuildingRecipes;

    @Shadow(remap = false)
    @Final
    private Map<ResourceLocation, List<ResourceLocation>> recipes;

    @Shadow(remap = false)
    @Final
    private LinkedHashMap<ResourceLocation, TabConfig> recipeKeys;

    @Shadow(remap = false)
    private List<ResourceLocation> selectedRecipeList;

    @Shadow(remap = false)
    private GunSmithTableRecipe selectedRecipe;

    @Shadow(remap = false)
    private ResourceLocation selectedType;

    @Override
    public void taczweaponblueprints$refreshRecipes() {
        // Drop the selected output immediately. The widget rebuild is deferred
        // to the next render pass, but stale access must not remain actionable
        // during that short interval.
        this.selectedRecipe = null;
        this.selectedRecipeList = new java.util.ArrayList<>();
        this.taczweaponblueprints$recipeRefreshQueued = true;
    }

    @Inject(method = "classifyRecipes", at = @At("RETURN"), remap = false)
    private void filterRecipesByLearnedBlueprints(CallbackInfo ci) {
        boolean enabled = ModConfigs.BLUEPRINT.enableBlueprints.get();
        Set<String> learnedRecipes = taczweaponblueprints$getLearnedRecipes();
        this.taczweaponblueprints$lastEnabled = enabled;
        this.taczweaponblueprints$lastLearnedRecipes = learnedRecipes;
        ClientCraftingAccessState.Snapshot access = ClientCraftingAccessState.snapshot(
                this.menu.containerId, this.menu);
        this.taczweaponblueprints$lastCraftingAccessRevision = access.revision();
        taczweaponblueprints$logRecipeDiagnostics(access, enabled, learnedRecipes);
        BlueprintRecipeFilter.Result filtered = BlueprintRecipeFilter.filterForCraftingAccessState(
                this.recipes,
                this.recipeKeys,
                learnedRecipes,
                access.receivedForMode(enabled),
                access.allowedRecipeIds(),
                access.unrestrictedCrafting(enabled),
                this.selectedType);
        this.selectedType = filtered.selectedType();
        this.selectedRecipeList = filtered.selectedRecipeList();

        if (this.selectedRecipe != null) {
            String selectedRecipeId = this.selectedRecipe.getId().toString();
            if (!BlueprintRecipeFilter.isVisibleForCraftingAccessState(
                    selectedRecipeId,
                    learnedRecipes,
                    access.receivedForMode(enabled),
                    access.allowedRecipeIds(),
                    access.unrestrictedCrafting(enabled))) {
                this.selectedRecipe = null;
            }
        }
    }

    @Unique
    private void taczweaponblueprints$logRecipeDiagnostics(
            ClientCraftingAccessState.Snapshot access,
            boolean blueprintsEnabled,
            Set<String> learnedRecipes) {
        if (access.revision() == this.taczweaponblueprints$lastDiagnosticAccessRevision) {
            return;
        }
        this.taczweaponblueprints$lastDiagnosticAccessRevision = access.revision();

        TreeSet<String> nativeRecipeIds = new TreeSet<>();
        this.recipes.values().forEach(recipeIds -> recipeIds.stream()
                .map(ResourceLocation::toString)
                .forEach(nativeRecipeIds::add));
        TreeSet<String> allowedRecipeIds = new TreeSet<>(access.allowedRecipeIds());
        TreeSet<String> overlap = new TreeSet<>(nativeRecipeIds);
        overlap.retainAll(allowedRecipeIds);
        TreeSet<String> allowedWithoutNative = new TreeSet<>(allowedRecipeIds);
        allowedWithoutNative.removeAll(nativeRecipeIds);
        TreeSet<String> nativeWithoutAllowed = new TreeSet<>(nativeRecipeIds);
        nativeWithoutAllowed.removeAll(allowedRecipeIds);
        TreeSet<String> learnedNative = new TreeSet<>(nativeRecipeIds);
        learnedNative.retainAll(learnedRecipes);

        String workstation = access.accessIdentity()
                .map(identity -> identity.workstationId().toString())
                .orElse("unavailable");
        String workstationTier = access.accessIdentity()
                .map(identity -> identity.workstationTier().name())
                .orElse("unavailable");
        boolean bypassTier = access.accessIdentity()
                .map(identity -> identity.bypassTier())
                .orElse(false);
        TaCZWeaponBlueprints.LOGGER.info(
                "Workbench recipe diagnostics [client]: container={}, workstation={}, tier={}, "
                        + "blueprintsEnabled={}, accessRevision={}, request={}, snapshot={}, "
                        + "received={}, status={}, unrestricted={}, bypassTier={}, groups={}, "
                        + "nativeRecipes={}, learnedRecipes={}, learnedNative={}, serverAllowed={}, "
                        + "overlap={}, allowedWithoutNative={}, nativeWithoutAllowed={}, "
                        + "groupSample={}, nativeSample={}, allowedSample={}, overlapSample={}, "
                        + "allowedWithoutNativeSample={}",
                this.menu.containerId,
                workstation,
                workstationTier,
                blueprintsEnabled,
                access.revision(),
                access.requestId(),
                access.snapshotId(),
                access.receivedForMode(blueprintsEnabled),
                access.status(),
                access.unrestrictedCrafting(blueprintsEnabled),
                bypassTier,
                this.recipes.size(),
                nativeRecipeIds.size(),
                learnedRecipes.size(),
                learnedNative.size(),
                allowedRecipeIds.size(),
                overlap.size(),
                allowedWithoutNative.size(),
                nativeWithoutAllowed.size(),
                taczweaponblueprints$diagnosticSample(this.recipes.keySet()),
                taczweaponblueprints$diagnosticSample(nativeRecipeIds),
                taczweaponblueprints$diagnosticSample(allowedRecipeIds),
                taczweaponblueprints$diagnosticSample(overlap),
                taczweaponblueprints$diagnosticSample(allowedWithoutNative));
    }

    @Unique
    private static List<String> taczweaponblueprints$diagnosticSample(Iterable<?> values) {
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            if (result.size() >= taczweaponblueprints$DIAGNOSTIC_SAMPLE_LIMIT) {
                break;
            }
            result.add(String.valueOf(value));
        }
        return List.copyOf(result);
    }

    @Inject(method = "init", at = @At("RETURN"), remap = false)
    private void requestCraftingAccess(CallbackInfo ci) {
        long requestId = ClientCraftingAccessState.beginRequest(menu.containerId, menu);
        if (requestId > 0L) {
            NetworkHandler.INSTANCE.sendToServer(
                    new CraftingAccessRequestPacket(menu.containerId, requestId));
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void refreshRecipesWhenUnlockStateChanges(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        boolean enabled = ModConfigs.BLUEPRINT.enableBlueprints.get();
        Set<String> learnedRecipes = taczweaponblueprints$getLearnedRecipes();
        long accessRevision = ClientCraftingAccessState.snapshot(
                menu.containerId, menu).revision();
        long retryRequestId = ClientCraftingAccessState.retryRequestIfTimedOut(
                menu.containerId, menu, System.nanoTime());
        if (retryRequestId > 0L) {
            NetworkHandler.INSTANCE.sendToServer(
                    new CraftingAccessRequestPacket(menu.containerId, retryRequestId));
        }
        if (enabled != this.taczweaponblueprints$lastEnabled
                || !learnedRecipes.equals(this.taczweaponblueprints$lastLearnedRecipes)
                || accessRevision != taczweaponblueprints$lastCraftingAccessRevision) {
            this.taczweaponblueprints$recipeRefreshQueued = true;
        }
        taczweaponblueprints$rebuildRecipesIfQueued();
    }

    @Unique
    private void taczweaponblueprints$rebuildRecipesIfQueued() {
        if (!this.taczweaponblueprints$recipeRefreshQueued
                || this.taczweaponblueprints$rebuildingRecipes) {
            return;
        }
        this.taczweaponblueprints$recipeRefreshQueued = false;
        this.taczweaponblueprints$rebuildingRecipes = true;
        try {
            this.rebuildWidgets();
        } finally {
            this.taczweaponblueprints$rebuildingRecipes = false;
        }
    }

    @Unique
    private static Set<String> taczweaponblueprints$getLearnedRecipes() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null
                ? Set.of()
                : player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                        .resolve()
                        .map(recipeData -> Set.copyOf(recipeData.getLearnedRecipes()))
                        .orElse(Set.of());
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderEmptyRecipeState(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        if (this.selectedRecipeList == null || this.selectedRecipeList.isEmpty()) {
            int xPos = ((IAbstractContainerScreenAccessor) this).getLeftPos() + 191;
            int yPos = ((IAbstractContainerScreenAccessor) this).getTopPos() + 105;
            Font font = Minecraft.getInstance().font;
            ClientCraftingAccessState.Snapshot access = ClientCraftingAccessState.snapshot(
                    menu.containerId, menu);
            boolean enabled = ModConfigs.BLUEPRINT.enableBlueprints.get();
            graphics.drawCenteredString(
                    font,
                    Component.translatable(taczweaponblueprints$emptyStateKey(access, enabled)),
                    xPos,
                    yPos,
                    0xFF5555);
            if (access.availableForMode(enabled)) {
                graphics.drawCenteredString(
                        font,
                        Component.translatable(
                                "gui.taczweaponblueprints.gun_smith_table.access_hint"),
                        xPos,
                        yPos + font.lineHeight,
                        0xFF5555);
            }
        }
    }

    @Unique
    private static String taczweaponblueprints$emptyStateKey(
            ClientCraftingAccessState.Snapshot access,
            boolean blueprintsEnabled) {
        if (!access.receivedForMode(blueprintsEnabled)) {
            return "gui.taczweaponblueprints.gun_smith_table.checking_access";
        }
        return switch (access.status()) {
            case ALLOWED -> "gui.taczweaponblueprints.gun_smith_table.no_accessible_recipes";
            case WORKBENCH_TIER_REQUIRED ->
                    "gui.taczweaponblueprints.gun_smith_table.workbench_tier_required";
            case PROGRESSION_GATE_REQUIRED ->
                    "gui.taczweaponblueprints.gun_smith_table.progression_gate_required";
            case CRAFTING_DISABLED ->
                    "gui.taczweaponblueprints.gun_smith_table.crafting_disabled";
            case INVALID_WORKSTATION ->
                    "gui.taczweaponblueprints.gun_smith_table.invalid_workstation";
            case CRAFTING_POLICY_MISSING, POLICY_UNAVAILABLE, INVALID_REQUEST,
                    UNKNOWN_RECIPE, RECIPE_NOT_LEARNED ->
                    "gui.taczweaponblueprints.gun_smith_table.access_unavailable";
        };
    }

    @Inject(method = "getSelectedRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void handleEmptyRecipeSelection(
            ResourceLocation recipeId,
            CallbackInfoReturnable<GunSmithTableRecipe> cir) {
        ClientCraftingAccessState.Snapshot access = ClientCraftingAccessState.snapshot(
                menu.containerId, menu);
        boolean enabled = ModConfigs.BLUEPRINT.enableBlueprints.get();
        if (recipeId == null || !access.allows(recipeId.toString(), enabled)) {
            cir.setReturnValue(null);
        }
    }
}
