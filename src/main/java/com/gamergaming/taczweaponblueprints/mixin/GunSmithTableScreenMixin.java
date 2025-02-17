package com.gamergaming.taczweaponblueprints.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.gui.components.smith.ResultButton;
import com.tacz.guns.client.gui.components.smith.TypeButton;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModCreativeTabs;
import com.tacz.guns.init.ModRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.AttachmentIndexPOJO;
import com.tacz.guns.resource.pojo.GunIndexPOJO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.registries.RegistryObject;

@Mixin(GunSmithTableScreen.class)
public abstract class GunSmithTableScreenMixin  {

    @Shadow(remap = false)
    @Final
    private Map<String, List<ResourceLocation>> recipes;

    @Shadow(remap = false)
    @Final
    private List<String> recipeKeys;

    @Shadow(remap = false)
    private List<ResourceLocation> selectedRecipeList;

    @Shadow(remap = false)
    private GunSmithTableRecipe selectedRecipe;

    @Shadow(remap = false)
    private String selectedType;

    @Shadow(remap = false)
    private int typePage;

    @Shadow(remap = false)
    private int indexPage;

    @Shadow
    private void init() {}

    @Shadow(remap = false)
    private void getPlayerIngredientCount(GunSmithTableRecipe recipe) {}

    @Shadow(remap = false)
    private void putRecipeType(RegistryObject<CreativeModeTab> tab) {}

    @Shadow(remap = false)
    private GunSmithTableRecipe getSelectedRecipe(ResourceLocation recipeId) {
        return null;
    }


    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;", ordinal = 0))
    private Object redirectSelectedRecipeListGetInit(List<ResourceLocation> list, int index) {
        if (list == null) {
            return null;
        } else if (list == this.selectedRecipeList && index == 0) {
            if (list.isEmpty()) {
                return null;
            } else {
                return list.get(index);
            }
        } else {
            return list.get(index);
        }
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void onInit(GunSmithTableMenu menu, Inventory inventory, Component title, CallbackInfo ci) {
        // Init recipes and recipeKeys if theyre null
        if (this.recipes == null) {
            this.recipes = new HashMap<>();
        }
        if (this.recipeKeys == null) {
            this.recipeKeys = new ArrayList<>();
        }
    }

    @Inject(method = "classifyRecipes", at = @At("HEAD"), cancellable = true, remap = false)
    private void onClassifyRecipes(CallbackInfo ci) {
        if (this.recipes == null) {
            this.recipes = new HashMap<>();
        }
        if (this.recipeKeys == null) {
            this.recipeKeys = new ArrayList<>();
        }

        // Add creative categories 
        putRecipeType(ModCreativeTabs.AMMO_TAB);
        putRecipeType(ModCreativeTabs.ATTACHMENT_EXTENDED_MAG_TAB);
        putRecipeType(ModCreativeTabs.ATTACHMENT_SCOPE_TAB);
        putRecipeType(ModCreativeTabs.ATTACHMENT_MUZZLE_TAB);
        putRecipeType(ModCreativeTabs.ATTACHMENT_STOCK_TAB);
        putRecipeType(ModCreativeTabs.ATTACHMENT_GRIP_TAB);
        putRecipeType(ModCreativeTabs.GUN_PISTOL_TAB);
        putRecipeType(ModCreativeTabs.GUN_SNIPER_TAB);
        putRecipeType(ModCreativeTabs.GUN_RIFLE_TAB);
        putRecipeType(ModCreativeTabs.GUN_SHOTGUN_TAB);
        putRecipeType(ModCreativeTabs.GUN_SMG_TAB);
        putRecipeType(ModCreativeTabs.GUN_RPG_TAB);
        putRecipeType(ModCreativeTabs.GUN_MG_TAB);

        for (String key : this.recipeKeys) {
            this.recipes.putIfAbsent(key, new ArrayList<>());
        }
        LocalPlayer player = Minecraft.getInstance().player;

        final List<GunSmithTableRecipe> availableRecipes;

        LazyOptional<IPlayerRecipeData> recipeData = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA);
        RecipeManager recipeManager;

        if (Minecraft.getInstance().level != null) {
            recipeManager = Minecraft.getInstance().level.getRecipeManager();
        } else if (Minecraft.getInstance().getConnection() != null) {
            recipeManager = Minecraft.getInstance().getConnection().getRecipeManager();
        } else {
            recipeManager = CommonAssetsManager.getInstance().recipeManager;
            
        }
   
        if (!ModConfigs.BLUEPRINT.enableBlueprints.get()) {
            // availableRecipes = CommonAssetsManager.getInstance().recipeManager.getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get());
            availableRecipes = recipeManager.getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get());
        } else if (recipeData.isPresent()) {
            availableRecipes = recipeManager.getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get()).stream()
                .filter(recipe -> recipeData.orElseThrow(() -> new IllegalStateException("Player recipe data not present")).hasRecipe(recipe.getId().toString()))
                .collect(Collectors.toList());
        } else {
            // TaCZWeaponBlueprints.LOGGER.info("INSIDE ELSE STATEMENT: " + ModConfigs.BLUEPRINT.startingBlueprints.get().toString());
            // availableRecipes = CommonAssetManager.INSTANCE.getAllRecipes().entrySet().stream()
            //     .filter(entry -> ModConfigs.BLUEPRINT.startingBlueprints.get().contains(entry.getKey().toString()))
            //     .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            availableRecipes = new ArrayList<>();
        }

        // if (ModConfigs.BLUEPRINT.startingBlueprints.get().size() > 0 && !availableRecipes.keySet().containsAll(ModConfigs.BLUEPRINT.startingBlueprints.get())) {
        //     Map<ResourceLocation, GunSmithTableRecipe> defaultRecipesToAdd = CommonAssetManager.INSTANCE.getAllRecipes().entrySet().stream()
        //         .filter(entry -> ModConfigs.BLUEPRINT.startingBlueprints.get().contains(entry.getKey().toString()))
        //         .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                
        //     availableRecipes.putAll(defaultRecipesToAdd);
        // }

        recipeManager.getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get()).forEach((recipe) -> {
            final String[] groupNameHolder = { recipe.getResult().getGroup() };
            if (groupNameHolder[0] == null || groupNameHolder[0].isEmpty()) {
                String kind = recipe.getId().toString().split(":")[1];
                kind = kind.split("/")[0];
                switch (kind) {
                    case "ammo":
                        groupNameHolder[0] = "ammo";
                        break;
                    case "attachment":
                        TimelessAPI.getCommonAttachmentIndex(new ResourceLocation(recipe.getId().toString().split(":")[0] + ":" + recipe.getId().toString().split("/")[1])).ifPresent(attachmentIndex -> {
                            AttachmentIndexPOJO pojo = attachmentIndex.getPojo();
                            groupNameHolder[0] = pojo.getType().name();
                        });
                        break;
                    case "gun":
                        TimelessAPI.getCommonGunIndex(new ResourceLocation(recipe.getId().toString().split(":")[0] + ":" + recipe.getId().toString().split("/")[1])).ifPresent(gunIndex -> {
                            GunIndexPOJO pojo = gunIndex.getPojo();
                            groupNameHolder[0] = pojo.getType();
                        });
                        break;
                    default:
                        break;
                }
            }
            //TaCZWeaponBlueprints.LOGGER.info("GroupName: " + groupNameHolder[0]);
            String groupName = groupNameHolder[0];
            
            // TaCZWeaponBlueprints.LOGGER.info("Classifying recipe " + id + " with groupName: " + groupName);
            if (this.recipeKeys.contains(groupName) && availableRecipes.stream().anyMatch(r -> r.getId().equals(recipe.getId()))) {
                recipes.computeIfAbsent(groupName, g -> new ArrayList<>()).add(recipe.getId());
                // TaCZWeaponBlueprints.LOGGER.info("Added recipe " + id + " to group " + groupName);
            } else {
                TaCZWeaponBlueprints.LOGGER.warn("Group name " + groupName + " not found in recipeKeys: {}", this.recipeKeys);
            }
        });

        // Proceed to classify recipes normally
        // TimelessAPI.getAllRecipes().forEach((id, recipe) -> {
        //     String groupName = recipe.getResult().getGroup();
        //     TaCZWeaponBlueprints.LOGGER.info("RECIPE: " + id + " GROUP: " + groupName);
        //     if (this.recipeKeys.contains(groupName)) {
        //         recipes.computeIfAbsent(groupName, g -> new ArrayList<>()).add(id);
        //     }
        // });

        ci.cancel(); // Cancel original method 
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

    @Inject(method = "addTypeButtons", at = @At("HEAD"), cancellable = true, remap = false)
    private void onAddTypeButtons(CallbackInfo ci) {
        for (int i = 0; i < 7; i++) {
            int typeIndex = typePage * 7 + i;
            if (typeIndex >= recipeKeys.size()) {
                break;
            }
            String type = recipeKeys.get(typeIndex);
            int xOffset = ((IAbstractContainerScreenAccessor) this).getLeftPos() + 157 + 24 * i;

            ItemStack icon = ItemStack.EMPTY;
            ResourceLocation tabId = new ResourceLocation(GunMod.MOD_ID, type);
            CreativeModeTab modTab = BuiltInRegistries.CREATIVE_MODE_TAB.get(tabId);
            if (modTab != null) {
                icon = modTab.getIconItem();
            }

            TypeButton typeButton = new TypeButton(xOffset, ((IAbstractContainerScreenAccessor) this).getTopPos() + 2, icon, b -> {
                this.selectedType = type;
                this.selectedRecipeList = recipes.get(type);
                this.indexPage = 0;
                if (this.selectedRecipeList != null && !this.selectedRecipeList.isEmpty()) {
                    this.selectedRecipe = getSelectedRecipe(this.selectedRecipeList.get(0));
                    this.getPlayerIngredientCount(this.selectedRecipe);
                } else {
                    this.selectedRecipe = null;
                }
                this.init();
            });
            if (this.selectedType.equals(type)) {
                typeButton.setSelected(true);
            }
            // ((IScreenAccessor) this).invokeAddRenderableWidget(typeButton);
            ((IScreenAccessor) this).getRenderables().add(typeButton);
            ((IScreenAccessor) this).getChildren().add(typeButton);
            ((IScreenAccessor) this).getNarratables().add(typeButton);
        }
        ci.cancel();
    }
    
    
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

    @Inject(method = "updateIngredientCount", at = @At("TAIL"), remap = false)
    private void onUpdateIngredientCount(CallbackInfo ci) {
        // After updating ingredient counts, re-initialize the GUI
        this.init();
    }

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
    //     // If the selected recipe list is null, return null else let the method continue
    //     if (this.selectedRecipeList == null) {
    //         ci.setReturnValue(null);
    //         ci.cancel();
    //     }
    // }

    @Inject(method = "putRecipeType", at = @At("HEAD"), cancellable = true, remap = false)
    private void onPutRecipeType(RegistryObject<CreativeModeTab> tab, CallbackInfo ci) {
        String name = tab.getId().getPath();
        if (!this.recipeKeys.contains(name)) {
            this.recipeKeys.add(name);
        }
        ci.cancel();
    }
}