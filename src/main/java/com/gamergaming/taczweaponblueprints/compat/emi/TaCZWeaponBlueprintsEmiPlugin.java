package com.gamergaming.taczweaponblueprints.compat.emi;

import java.util.List;

import com.gamergaming.taczweaponblueprints.client.ClientResearchState;
import com.gamergaming.taczweaponblueprints.compat.recipeviewer.BlueprintRecipeViewerInfo;
import com.gamergaming.taczweaponblueprints.compat.recipeviewer.BlueprintRecipeViewerInfo.Topic;
import com.gamergaming.taczweaponblueprints.init.ModItems;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Optional EMI information pages; no recipe tree inputs or transfer handler. */
@EmiEntrypoint
public final class TaCZWeaponBlueprintsEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.setDefaultComparison(
                ModItems.BLUEPRINT_ITEM.get(),
                Comparison.compareData(stack ->
                        EmiBlueprintStackIdentity.blueprintId(stack.getNbt())));
        registry.setDefaultComparison(
                ModItems.EMPTY_BLUEPRINT_ITEM.get(),
                Comparison.compareData(stack ->
                        EmiBlueprintStackIdentity.blankTarget(stack.getNbt())));

        addInfo(registry, Topic.RESEARCH_BENCH,
                List.of(EmiStack.of(ModItems.RESEARCH_BENCH_ITEM.get())));
        addInfo(registry, Topic.BLUEPRINT_ANALYZER,
                List.of(EmiStack.of(ModItems.BLUEPRINT_RECYCLER_ITEM.get())));
        addBlueprintInfo(registry);
        addInfo(registry, Topic.RESEARCH_DATA,
                List.of(
                        EmiStack.of(ModItems.RESEARCH_NOTE.get()),
                        EmiStack.of(ModItems.RESEARCH_REPORT.get()),
                        EmiStack.of(ModItems.RESEARCH_DOSSIER.get())));
    }

    private static void addBlueprintInfo(EmiRegistry registry) {
        List<ResourceLocation> blueprintIds = BlueprintRecipeViewerInfo.disclosedBlueprintIds(
                ClientResearchState.publication().journal());
        if (blueprintIds.isEmpty()) {
            addInfo(registry, Topic.BLUEPRINT,
                    List.of(EmiStack.of(ModItems.EMPTY_BLUEPRINT_ITEM.get())));
            return;
        }
        for (ResourceLocation blueprintId : blueprintIds) {
            ItemStack blankBlueprint = new ItemStack(ModItems.EMPTY_BLUEPRINT_ITEM.get());
            EmiBlueprintStackIdentity.targetBlankBlueprint(
                    blankBlueprint.getOrCreateTag(), blueprintId);
            ItemStack physicalBlueprint = BlueprintItem.createBlueprint(blueprintId.toString());
            registry.addRecipe(new EmiInfoRecipe(
                    List.of(
                            EmiStack.of(blankBlueprint),
                            EmiStack.of(physicalBlueprint)),
                    BlueprintRecipeViewerInfo.components(Topic.BLUEPRINT),
                    EmiSyntheticRecipeId.forBlueprint(blueprintId)));
        }
    }

    private static void addInfo(
            EmiRegistry registry,
            Topic topic,
            List<EmiIngredient> ingredients) {
        registry.addRecipe(new EmiInfoRecipe(
                ingredients,
                BlueprintRecipeViewerInfo.components(topic),
                EmiSyntheticRecipeId.forTopic(topic)));
    }
}
