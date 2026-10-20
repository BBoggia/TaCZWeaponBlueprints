package com.gamergaming.taczweaponblueprints.verification;

import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.resources.ResourceLocation;

final class EmiFeatureVerification {
    static boolean ready(){return EmiApi.getRecipeManager().getRecipe(ResourceLocation.parse("taczweaponblueprints:/emi_info/research_bench"))!=null;}
    static void verify(){
        for(var name:java.util.List.of("research_bench","crafting_workbench","blueprint_analyzer","research_data"))
            ClientVerification.require(EmiApi.getRecipeManager().getRecipe(ResourceLocation.parse("taczweaponblueprints:/emi_info/"+name))!=null,"EMI runtime contains production information page: "+name);
        ClientVerification.require(EmiApi.getRecipeManager().getRecipes().stream().anyMatch(r->r.getId()!=null && r.getId().toString().startsWith("taczweaponblueprints:/emi_info/blueprint")),"EMI runtime contains blueprint information");
        var a=BlueprintItem.createBlueprint("tacz:ak47");var b=a.copy();CustomData.update(DataComponents.CUSTOM_DATA,b,tag->tag.putString("fixture_provenance","irrelevant"));
        ClientVerification.require(EmiStack.of(a).isEqual(EmiStack.of(b)),"EMI blueprint identity ignores unrelated component metadata");
        ClientVerification.require(!EmiStack.of(a).isEqual(EmiStack.of(BlueprintItem.createBlueprint("tacz:glock_17"))),"EMI blueprint identity distinguishes target IDs");
    }
    static void analyzer() throws Exception {
        var type=Class.forName("dev.emi.emi.screen.EmiScreenBase");
        var current=type.getMethod("of",net.minecraft.client.gui.screens.Screen.class).invoke(null,net.minecraft.client.Minecraft.getInstance().screen);
        ClientVerification.require((boolean)type.getMethod("isEmpty").invoke(current),"EMI disables overlay and input capture in Analyzer");
    }
}
