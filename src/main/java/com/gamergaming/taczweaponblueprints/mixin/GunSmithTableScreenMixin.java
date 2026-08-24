package com.gamergaming.taczweaponblueprints.mixin;

import java.util.ArrayList;
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

import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.client.IBlueprintRecipeScreen;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.gui.components.smith.ResultButton;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.pojo.data.block.TabConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
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
    private ResourceLocation selectedType = null;

    @Shadow(remap = false)
    private int typePage;

    @Shadow(remap = false)
    private int indexPage;

    @Shadow
    private void init() {}

    @Shadow(remap = false)
    private void getPlayerIngredientCount(GunSmithTableRecipe recipe) {}

    @Shadow(remap = false)
    private GunSmithTableRecipe getSelectedRecipe(ResourceLocation recipeId) {
        return null;
    }

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

        this.recipes.values().forEach(recipeIds ->
                recipeIds.removeIf(recipeId -> !learnedRecipes.contains(recipeId.toString())));
        this.recipes.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        this.recipeKeys.keySet().removeIf(recipeType -> !this.recipes.containsKey(recipeType));

        if (this.selectedType == null || !this.recipes.containsKey(this.selectedType)) {
            this.selectedType = this.recipeKeys.keySet().stream().findFirst().orElse(null);
        }
        this.selectedRecipeList = this.selectedType == null
                ? new ArrayList<>()
                : this.recipes.getOrDefault(this.selectedType, new ArrayList<>());

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

    @Inject(method = "addIndexButtons", at = @At("HEAD"), cancellable = true, remap = false)
    private void onAddIndexButtons(CallbackInfo ci) {
        if (selectedRecipeList == null || selectedRecipeList.isEmpty()) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            int finalIndex = i + indexPage * 6;
            if (finalIndex >= selectedRecipeList.size()) {
                break;
            }
            int yOffset = ((IAbstractContainerScreenAccessor) this).getTopPos() + 66 + 17 * i;
            ResourceLocation recipeId = selectedRecipeList.get(finalIndex);
            GunSmithTableRecipe recipe = getSelectedRecipe(recipeId);
            if (recipe != null) {
                ResultButton button = new ResultButton(((IAbstractContainerScreenAccessor) this).getLeftPos() + 144, yOffset, recipe.getOutput(), b -> {
                    this.selectedRecipe = recipe;
                    this.getPlayerIngredientCount(this.selectedRecipe);
                    this.init();
                });
                if (this.selectedRecipe != null && recipe.getId().equals(this.selectedRecipe.getId())) {
                    button.setSelected(true);
                }
                // ((IScreenAccessor) this).invokeAddRenderableWidget(button);
                ((IScreenAccessor) this).getRenderables().add(button);
                ((IScreenAccessor) this).getChildren().add(button);
                ((IScreenAccessor) this).getNarratables().add(button);
            }
        }
        ci.cancel();
    }
    
    
    // private void addIndexButtons(CallbackInfo ci) {
    //     for (int i = 0; i < 6; i++) {
    //         int finalIndex = i + indexPage * 6;
    //         if (finalIndex >= selectedRecipeList.size()) {
    //             break;
    //         }
    //         int yOffset = ((AbstractContainerScreenAccessor) this).getTopPos() + 66 + 17 * i;
    //         TimelessAPI.getRecipe(selectedRecipeList.get(finalIndex)).ifPresent(recipe -> {
    //             ResultButton button = ((ScreenAccessor) this).invokeAddRenderableWidget(new ResultButton(((AbstractContainerScreenAccessor) this).getLeftPos() + 144, yOffset, recipe.getOutput(), b -> {
    //                 this.selectedRecipe = recipe;
    //                 this.getPlayerIngredientCount(this.selectedRecipe);
    //                 this.init();
    //             }));
    //             if (this.selectedRecipe != null && recipe.getId().equals(this.selectedRecipe.getId())) {
    //                 button.setSelected(true);
    //             }
    //         });
    //     }
    //     ci.cancel();
    // }

    // @Inject(method = "addTypeButtons", at = @At("HEAD"), cancellable = true, remap = false)
    // private void onAddTypeButtons(CallbackInfo ci) {
    //     var list = Arrays.asList(recipeKeys.values().toArray(new TabConfig[0]));
    //     for (int i = 0; i < 7; i++) {
    //         int typeIndex = typePage * 7 + i;
    //         if (typeIndex >= recipes.size()) {
    //             return;
    //         }
    //         TabConfig tabConfig = list.get(typeIndex);
    //         ResourceLocation type = tabConfig.id();
    //         int xOffset = ((IAbstractContainerScreenAccessor) this).getLeftPos() + 157 + 24 * i;

    //         ItemStack icon = tabConfig.icon();
            
    //         TypeButton typeButton = new TypeButton(xOffset, ((IAbstractContainerScreenAccessor) this).getTopPos() + 2, icon, b -> {
    //             this.selectedType = type;
    //             this.selectedRecipeList = recipes.get(type);
    //             this.indexPage = 0;
    //             this.selectedRecipe = getSelectedRecipe(this.selectedRecipeList.isEmpty() ? null : this.selectedRecipeList.get(0));
    //             this.getPlayerIngredientCount(this.selectedRecipe);
    //             this.init();
    //         });
    //         typeButton.setTooltip(Tooltip.create(tabConfig.getName(), tabConfig.getName()));
    //         if (this.selectedType.equals(type)) {
    //             typeButton.setSelected(true);
    //         }

    //         // ((IScreenAccessor) this).invokeAddRenderableWidget(typeButton);
    //         ((IScreenAccessor) this).getRenderables().add(typeButton);
    //         ((IScreenAccessor) this).getChildren().add(typeButton);
    //         ((IScreenAccessor) this).getNarratables().add(typeButton);
    //     }
    //     ci.cancel();
    // }
    
    
    // private void addTypeButtons(CallbackInfo ci) {
    //     for (int i = 0; i < 7; i++) {
    //         int typeIndex = typePage * 7 + i;
    //         if (typeIndex >= recipes.size()) {
    //             return;
    //         }
    //         String type = recipeKeys.get(typeIndex);
    //         int xOffset = ((AbstractContainerScreenAccessor) this).getLeftPos() + 157 + 24 * i;
    //         List<ResourceLocation> recipeIdGroups = recipes.get(type);
    //         if (recipeIdGroups.isEmpty()) {
    //             continue;
    //         }
    //         ItemStack icon = ItemStack.EMPTY;
    //         ResourceLocation tabId = new ResourceLocation(GunMod.MOD_ID, type);
    //         CreativeModeTab modTab = BuiltInRegistries.CREATIVE_MODE_TAB.get(tabId);
    //         if (modTab != null) {
    //             icon = modTab.getIconItem();
    //         }
    //         TypeButton typeButton = new TypeButton(xOffset, ((AbstractContainerScreenAccessor) this).getTopPos() + 2, icon, b -> {
    //             this.selectedType = type;
    //             this.selectedRecipeList = recipes.get(type);
    //             this.indexPage = 0;

    //             this.selectedRecipe = getSelectedRecipe(this.selectedRecipeList.get(0));
    //             this.getPlayerIngredientCount(this.selectedRecipe);
    //             this.init();
    //         });
    //         if (this.selectedType.equals(type)) {
    //             typeButton.setSelected(true);
    //         }
    //         ((ScreenAccessor) this).invokeAddRenderableWidget(typeButton);
    //     }
    //     ci.cancel();
    // }

    @Inject(method = "getPlayerIngredientCount", at = @At("HEAD"), cancellable = true, remap = false)
    private void onGetPlayerIngredientCount(GunSmithTableRecipe recipe, CallbackInfo ci) {
        if (recipe == null) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        // TaCZWeaponBlueprints.LOGGER.info("\n\nRECIPE KEYS: " + this.recipeKeys);
        // TaCZWeaponBlueprints.LOGGER.info("RECIPES: " + this.recipes + "\n\n");
        if (this.selectedRecipeList == null || this.selectedRecipeList.isEmpty()) {
            int xPos = ((IAbstractContainerScreenAccessor) this).getLeftPos() + 191;
            int yPos = ((IAbstractContainerScreenAccessor) this).getTopPos() + 105;
            Font font = ((IScreenAccessor) this).getFont();
            String line1 = "No recipes";  // Component.translatable("gui.tacz.gun_smith_table.no_recipes");
            String line2 = "available";   // Component.translatable("gui.tacz.gun_smith_table.available");
            graphics.drawCenteredString(font, line1, xPos, yPos, 0xFF5555);
            graphics.drawCenteredString(font, line2, xPos, yPos + font.lineHeight, 0xFF5555);

            // graphics.drawCenteredString(((ScreenAccessor) this).getFont(), "No recipes available", ((AbstractContainerScreenAccessor) this).getLeftPos() + 190, ((AbstractContainerScreenAccessor) this).getTopPos() + 110, 0xFF5555);
        }
    }
    
    
    // private void getPlayerIngredientCount(GunSmithTableRecipe recipe, CallbackInfo ci) {
    //     if (this.selectedRecipe == null || this.selectedRecipeList == null || recipe == null) {
    //         ci.cancel();
    //     }
    // }

    @Inject(method = "getSelectedRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void onGetSelectedRecipe(ResourceLocation recipeId, CallbackInfoReturnable<GunSmithTableRecipe> cir) {
        if (recipeId == null) {
            cir.setReturnValue(null);
            cir.cancel();
        }
    }
    
    
    // private void getSelectedRecipeInject(ResourceLocation recipeId, CallbackInfoReturnable<GunSmithTableRecipe> ci) {
    //     if (this.selectedRecipeList == null) {
    //         ci.setReturnValue(null);
    //         ci.cancel();
    //     }
    // }

    // @Inject(method = "putRecipeType", at = @At("HEAD"), cancellable = true, remap = false)
    // private void onPutRecipeType(RegistryObject<CreativeModeTab> tab, CallbackInfo ci) {
    //     String name = tab.getId().getPath();
    //     if (!this.recipeKeys.contains(name)) {
    //         this.recipeKeys.add(name);
    //     }
    //     ci.cancel();
    // }
}
