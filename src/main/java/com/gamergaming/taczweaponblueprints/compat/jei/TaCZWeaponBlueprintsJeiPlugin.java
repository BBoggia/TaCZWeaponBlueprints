package com.gamergaming.taczweaponblueprints.compat.jei;

import java.util.List;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.compat.recipeviewer.BlueprintRecipeViewerInfo;
import com.gamergaming.taczweaponblueprints.compat.recipeviewer.BlueprintRecipeViewerInfo.Topic;
import com.gamergaming.taczweaponblueprints.init.ModItems;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Optional JEI information pages; no recipe transfer or catalog enumeration. */
@JeiPlugin
public final class TaCZWeaponBlueprintsJeiPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_ID = TaCZWeaponBlueprints.loc("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addItemStackInfo(
                new ItemStack(ModItems.RESEARCH_BENCH_ITEM.get()), descriptions(Topic.RESEARCH_BENCH));
        registration.addItemStackInfo(
                new ItemStack(ModItems.BLUEPRINT_RECYCLER_ITEM.get()), descriptions(Topic.BLUEPRINT_ANALYZER));
        registration.addItemStackInfo(
                List.of(
                        new ItemStack(ModItems.BLUEPRINT_ITEM.get()),
                        new ItemStack(ModItems.EMPTY_BLUEPRINT_ITEM.get())),
                descriptions(Topic.BLUEPRINT));
        registration.addItemStackInfo(
                List.of(
                        new ItemStack(ModItems.RESEARCH_NOTE.get()),
                        new ItemStack(ModItems.RESEARCH_REPORT.get()),
                        new ItemStack(ModItems.RESEARCH_DOSSIER.get())),
                descriptions(Topic.RESEARCH_DATA));
    }

    private static Component[] descriptions(Topic topic) {
        return BlueprintRecipeViewerInfo.components(topic).toArray(Component[]::new);
    }
}
