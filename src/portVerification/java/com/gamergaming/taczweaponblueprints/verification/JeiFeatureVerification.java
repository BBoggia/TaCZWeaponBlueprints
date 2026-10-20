package com.gamergaming.taczweaponblueprints.verification;

import com.gamergaming.taczweaponblueprints.init.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Observer only: the production plugin must register all information pages itself. */
@JeiPlugin
public final class JeiFeatureVerification implements IModPlugin {
    private static IJeiRuntime runtime;
    @Override public ResourceLocation getPluginUid(){return ResourceLocation.fromNamespaceAndPath(CoreVerification.ID,"jei_observer");}
    @Override public void onRuntimeAvailable(IJeiRuntime value){runtime=value;}
    @Override public void onRuntimeUnavailable(){runtime=null;}
    static boolean ready(){return runtime!=null;}
    static void verify(){
        var recipes=runtime.getRecipeManager().createRecipeLookup(RecipeTypes.INFORMATION).get().toList();
        for(var item:java.util.List.of(ModItems.RESEARCH_BENCH_ITEM.get(),ModItems.WORKBENCH_LVL1_ITEM.get(),ModItems.BLUEPRINT_RECYCLER_ITEM.get(),ModItems.BLUEPRINT_ITEM.get(),ModItems.RESEARCH_NOTE.get())){
            ClientVerification.require(recipes.stream().anyMatch(r->r.getIngredients().stream().anyMatch(i->i.getIngredient() instanceof ItemStack stack && stack.is(item)) && !r.getDescription().isEmpty()),"JEI runtime contains production information page for "+item);
        }
    }
}
