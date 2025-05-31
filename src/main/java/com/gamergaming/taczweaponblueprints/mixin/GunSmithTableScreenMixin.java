package com.gamergaming.taczweaponblueprints.mixin;

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
import com.tacz.guns.resource.pojo.data.block.TabConfig; // Import TabConfig
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
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap; // Import LinkedHashMap
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mixin(GunSmithTableScreen.class)
public abstract class GunSmithTableScreenMixin {

    @Shadow(remap = false)
    @Final
    private Map<ResourceLocation, List<ResourceLocation>> recipes;

    @Shadow(remap = false)
    @Final
    private LinkedHashMap<ResourceLocation, TabConfig> recipeKeys; // Corrected type

    @Shadow(remap = false)
    private List<ResourceLocation> selectedRecipeList;

    @Shadow(remap = false)
    private GunSmithTableRecipe selectedRecipe;

    @Shadow(remap = false)
    @Nullable
    private ResourceLocation selectedType;

    @Shadow(remap = false)
    private int typePage;

    @Shadow(remap = false)
    private int indexPage;

    @Shadow
    private void init() {
    }

    @Shadow(remap = false)
    private void getPlayerIngredientCount(GunSmithTableRecipe recipe) {
    }

    // Removed the @Shadow for putRecipeType since we'll populate the maps directly
    // based on the original classifyRecipes logic.
    // private void putRecipeType(ResourceLocation tabId) { }

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
            // Mixin injects after constructor, so these should already be initialized
            // by the target class. However, if they were shadowed without @Final and not initialized
            // by the target, this could be a fallback. For @Final, they are initialized.
            // This block might be redundant or indicative of a deeper issue if they are truly null here.
            // Leaving it for now as a defensive check.
            // Assuming they are initialized by the target, as they are @Final.
        }
        if (this.recipeKeys == null) {
            // Same as above for recipes.
        }
    }

    @Inject(method = "classifyRecipes", at = @At("HEAD"), cancellable = true, remap = false)
    private void onClassifyRecipes(CallbackInfo ci) {
        // Clear existing recipes and keys
        this.recipes.clear();
        this.recipeKeys.clear();

        // Directly add creative categories
        // We need to create TabConfig objects for each creative tab.
        // The TabConfig constructor is TabConfig(ResourceLocation id, String name, ItemStack icon)
        addCreativeTabToRecipeKeys(ModCreativeTabs.AMMO_TAB);
        addCreativeTabToRecipeKeys(ModCreativeTabs.ATTACHMENT_EXTENDED_MAG_TAB);
        addCreativeTabToRecipeKeys(ModCreativeTabs.ATTACHMENT_SCOPE_TAB);
        addCreativeTabToRecipeKeys(ModCreativeTabs.ATTACHMENT_MUZZLE_TAB);
        addCreativeTabToRecipeKeys(ModCreativeTabs.ATTACHMENT_STOCK_TAB);
        addCreativeTabToRecipeKeys(ModCreativeTabs.ATTACHMENT_GRIP_TAB);
        addCreativeTabToRecipeKeys(ModCreativeTabs.GUN_PISTOL_TAB);
        addCreativeTabToRecipeKeys(ModCreativeTabs.GUN_SNIPER_TAB);
        addCreativeTabToRecipeKeys(ModCreativeTabs.GUN_RIFLE_TAB);
        addCreativeTabToRecipeKeys(ModCreativeTabs.GUN_SHOTGUN_TAB);
        addCreativeTabToRecipeKeys(ModCreativeTabs.GUN_SMG_TAB);
        addCreativeTabToRecipeKeys(ModCreativeTabs.GUN_RPG_TAB);
        addCreativeTabToRecipeKeys(ModCreativeTabs.GUN_MG_TAB);

        for (ResourceLocation key : this.recipeKeys.keySet()) {
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
            availableRecipes = recipeManager.getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get());
        } else if (recipeData.isPresent()) {
            availableRecipes = recipeManager.getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get()).stream()
                    .filter(recipe -> recipeData.orElseThrow(() -> new IllegalStateException("Player recipe data not present")).hasRecipe(recipe.getId().toString()))
                    .collect(Collectors.toList());
        } else {
            availableRecipes = new ArrayList<>();
        }

        recipeManager.getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get()).forEach((recipe) -> {
            final ResourceLocation[] groupNameHolder = {recipe.getResult().getGroup()};
            if (groupNameHolder[0] == null) {
                String kind = recipe.getId().toString().split(":")[1];
                kind = kind.split("/")[0];
                switch (kind) {
                    case "ammo":
                        groupNameHolder[0] = new ResourceLocation("tacz", "ammo");
                        break;
                    case "attachment":
                        TimelessAPI.getCommonAttachmentIndex(new ResourceLocation(recipe.getId().toString().split(":")[0] + ":" + recipe.getId().toString().split("/")[1])).ifPresent(attachmentIndex -> {
                            AttachmentIndexPOJO pojo = attachmentIndex.getPojo();
                            groupNameHolder[0] = new ResourceLocation("tacz", pojo.getType().name());
                        });
                        break;
                    case "gun":
                        TimelessAPI.getCommonGunIndex(new ResourceLocation(recipe.getId().toString().split(":")[0] + ":" + recipe.getId().toString().split("/")[1])).ifPresent(gunIndex -> {
                            GunIndexPOJO pojo = gunIndex.getPojo();
                            groupNameHolder[0] = new ResourceLocation("tacz", pojo.getType());
                        });
                        break;
                    default:
                        // If no specific group is found, it's possible to fall back to a "misc" or "empty" group
                        // Or simply not add the recipe if it doesn't fit a defined category.
                        // For now, we'll assume it needs to fit one of the defined categories.
                        break;
                }
            }
            ResourceLocation groupName = groupNameHolder[0];

            if (groupName != null && this.recipeKeys.containsKey(groupName) && availableRecipes.stream().anyMatch(r -> r.getId().equals(recipe.getId()))) {
                recipes.computeIfAbsent(groupName, g -> new ArrayList<>()).add(recipe.getId());
            } else if (groupName == null) {
                TaCZWeaponBlueprints.LOGGER.warn("Recipe {} has a null group name and cannot be classified.", recipe.getId());
            }
            else {
                TaCZWeaponBlueprints.LOGGER.warn("Group name {} for recipe {} not found in recipeKeys: {}", groupName, recipe.getId(), this.recipeKeys.keySet());
            }
        });

        // Ensure selectedType and selectedRecipeList are set after classification
        if (!this.recipeKeys.isEmpty() && this.selectedType == null) {
            this.selectedType = this.recipeKeys.keySet().iterator().next();
        }
        if (this.selectedType != null) {
            this.selectedRecipeList = this.recipes.get(this.selectedType);
            if (this.selectedRecipeList != null && !this.selectedRecipeList.isEmpty() && this.selectedRecipe == null) {
                this.selectedRecipe = this.getSelectedRecipe(this.selectedRecipeList.get(0));
                this.getPlayerIngredientCount(this.selectedRecipe);
            }
        } else {
            this.selectedRecipeList = new ArrayList<>();
            this.selectedRecipe = null;
        }

        ci.cancel(); // Cancel original method
    }

    private void addCreativeTabToRecipeKeys(RegistryObject<CreativeModeTab> tabRegistryObject) {
        ResourceLocation tabId = tabRegistryObject.getId();
        // Create a TabConfig from the CreativeModeTab
        // This might need adjustment if TabConfig requires more complex data than just ID, name, and icon.
        // For now, we'll use the tab's ID for the name and its icon.
        CreativeModeTab tab = tabRegistryObject.get();
        if (tab != null) {
            // Using translatable component to ensure names match original logic if possible
            // The name parameter of TabConfig is a String representing a translation key.
            // For creative tabs, their names are often derived from their ID path, or have specific translation keys.
            // A simple approach is to use the ID's path as the name.
            String translationKey = "itemGroup." + tabId.getNamespace() + "." + tabId.getPath(); // Common pattern for item group translation keys
            TabConfig tabConfig = new TabConfig(tabId, translationKey, tab.getIconItem());
            this.recipeKeys.put(tabId, tabConfig);
            this.recipes.putIfAbsent(tabId, new ArrayList<>()); // Ensure the list is also initialized
        }
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
            // Assuming IAbstractContainerScreenAccessor is a mixin accessor for AbstractContainerScreen to get protected fields
            // You'll need to define this accessor if you haven't already.
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
                // Assuming IScreenAccessor is a mixin accessor for Screen to get protected fields/methods
                // You'll need to define this accessor if you haven't already.
                ((IScreenAccessor) this).getRenderables().add(button);
                ((IScreenAccessor) this).getChildren().add(button);
                ((IScreenAccessor) this).getNarratables().add(button);
            }
        }
        ci.cancel();
    }


    @Inject(method = "addTypeButtons", at = @At("HEAD"), cancellable = true, remap = false)
    private void onAddTypeButtons(CallbackInfo ci) {
        for (int i = 0; i < 7; i++) {
            int typeIndex = typePage * 7 + i;
            List<ResourceLocation> recipeKeysList = new ArrayList<>(recipeKeys.keySet());
            if (typeIndex >= recipeKeysList.size()) {
                break;
            }
            ResourceLocation type = recipeKeysList.get(typeIndex);
            int xOffset = ((IAbstractContainerScreenAccessor) this).getLeftPos() + 157 + 24 * i;

            ItemStack icon = ItemStack.EMPTY;
            // The original TabConfig stores its icon directly, so we should retrieve it from there.
            TabConfig tabConfig = this.recipeKeys.get(type);
            if (tabConfig != null) {
                icon = tabConfig.icon();
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
            if (this.selectedType != null && this.selectedType.equals(type)) {
                typeButton.setSelected(true);
            }
            ((IScreenAccessor) this).getRenderables().add(typeButton);
            ((IScreenAccessor) this).getChildren().add(typeButton);
            ((IScreenAccessor) this).getNarratables().add(typeButton);
        }
        ci.cancel();
    }


    @Inject(method = "updateIngredientCount", at = @At("TAIL"), remap = false)
    private void onUpdateIngredientCount(CallbackInfo ci) {
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
        if (this.selectedRecipeList == null || this.selectedRecipeList.isEmpty()) {
            int xPos = ((IAbstractContainerScreenAccessor) this).getLeftPos() + 191;
            int yPos = ((IAbstractContainerScreenAccessor) this).getTopPos() + 105;
            Font font = ((IScreenAccessor) this).getFont();
            String line1 = "No recipes";
            String line2 = "available";
            graphics.drawCenteredString(font, line1, xPos, yPos, 0xFF5555);
            graphics.drawCenteredString(font, line2, xPos, yPos + font.lineHeight, 0xFF5555);
        }
    }


    @Inject(method = "getSelectedRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void onGetSelectedRecipe(ResourceLocation recipeId, CallbackInfoReturnable<GunSmithTableRecipe> cir) {
        if (recipeId == null) {
            cir.setReturnValue(null);
            cir.cancel();
        }
    }

    // Removed the custom putRecipeType @Inject, as we're now directly populating the maps in classifyRecipes.
    // The original GunSmithTableScreen also directly populates them.
    // @Inject(method = "putRecipeType", at = @At("HEAD"), cancellable = true, remap = false)
    // private void onPutRecipeType(ResourceLocation tabId, CallbackInfo ci) {
    //     if (!this.recipeKeys.containsKey(tabId)) {
    //         this.recipeKeys.put(tabId, null); // Add directly, as the original putRecipeType doesn't seem to use the TabConfig directly
    //     }
    //     ci.cancel();
    // }


    // You will need to define these accessor interfaces if they are not already present in your project.
    // They are used to access protected/private members of the GunSmithTableScreen and AbstractContainerScreen/Screen classes.
    // Example:
    // @Mixin(AbstractContainerScreen.class)
    // public interface IAbstractContainerScreenAccessor {
    //     @Shadow(remap = false) int getLeftPos();
    //     @Shadow(remap = false) int getTopPos();
    // }

    // @Mixin(Screen.class)
    // public interface IScreenAccessor {
    //     @Shadow(remap = false) void addRenderableWidget(net.minecraft.client.gui.components.Widget widget);
    //     @Shadow(remap = false) java.util.List<net.minecraft.client.gui.components.Renderable> getRenderables();
    //     @Shadow(remap = false) java.util.List<? extends net.minecraft.client.gui.components.NarratableEntry> getNarratables();
    //     @Shadow(remap = false) java.util.List<? extends net.minecraft.client.gui.components.Widget> getChildren();
    //     @Shadow(remap = false) Font getFont();
    // }
}