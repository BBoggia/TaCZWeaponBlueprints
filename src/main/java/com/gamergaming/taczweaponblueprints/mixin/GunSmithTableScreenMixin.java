package com.gamergaming.taczweaponblueprints.mixin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gamergaming.taczweaponblueprints.client.BlueprintRecipeFilter;
import com.gamergaming.taczweaponblueprints.client.IBlueprintRecipeScreen;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.pojo.data.block.TabConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

@Mixin(GunSmithTableScreen.class)
public abstract class GunSmithTableScreenMixin implements IBlueprintRecipeScreen {

    @Unique
    private boolean taczweaponblueprints$lastEnabled;

    @Unique
    private Set<String> taczweaponblueprints$lastLearnedRecipes = Set.of();

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

    @Shadow
    private void init() {}

    @Override
    public void taczweaponblueprints$refreshRecipes() {
        this.init();
    }

    @Inject(method = "classifyRecipes", at = @At("RETURN"), remap = false)
    private void filterRecipesByLearnedBlueprints(CallbackInfo ci) {
        boolean enabled = ModConfigs.BLUEPRINT.enableBlueprints.get();
        Set<String> learnedRecipes = taczweaponblueprints$getLearnedRecipes();
        this.taczweaponblueprints$lastEnabled = enabled;
        this.taczweaponblueprints$lastLearnedRecipes = learnedRecipes;
        if (!enabled) {
            return;
        }

        BlueprintRecipeFilter.Result filtered = BlueprintRecipeFilter.filterInPlace(
                this.recipes,
                this.recipeKeys,
                learnedRecipes,
                this.selectedType);
        this.selectedType = filtered.selectedType();
        this.selectedRecipeList = filtered.selectedRecipeList();

        if (this.selectedRecipe != null && !learnedRecipes.contains(this.selectedRecipe.getId().toString())) {
            this.selectedRecipe = null;
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
        if (enabled != this.taczweaponblueprints$lastEnabled
                || !learnedRecipes.equals(this.taczweaponblueprints$lastLearnedRecipes)) {
            this.taczweaponblueprints$lastEnabled = enabled;
            this.taczweaponblueprints$lastLearnedRecipes = learnedRecipes;
            this.init();
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
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.taczweaponblueprints.gun_smith_table.no_recipes"),
                    xPos,
                    yPos,
                    0xFF5555);
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.taczweaponblueprints.gun_smith_table.available"),
                    xPos,
                    yPos + font.lineHeight,
                    0xFF5555);
        }
    }

    @Inject(method = "getSelectedRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void handleEmptyRecipeSelection(
            ResourceLocation recipeId,
            CallbackInfoReturnable<GunSmithTableRecipe> cir) {
        if (recipeId == null) {
            cir.setReturnValue(null);
        }
    }
}
