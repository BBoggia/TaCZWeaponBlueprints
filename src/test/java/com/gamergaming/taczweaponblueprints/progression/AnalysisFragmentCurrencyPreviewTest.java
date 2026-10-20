package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintFragmentItem;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerPreview;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.progression.currency.*;
import com.gamergaming.taczweaponblueprints.testing.AnalysisTestItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

class AnalysisFragmentCurrencyPreviewTest {
    @BeforeAll static void bootstrap() { AnalysisTestItems.initialize(); }

    @ParameterizedTest @ValueSource(ints = {1, 5, 7})
    void partialAndCompleteSetsUseCarriedCurrencyWithoutChangingReconstruction(int offered) {
        var target = ResourceLocation.parse("test:rifle");
        var data = new PlayerRecipeData(); data.setResearchPoints(137);
        var inventory = new ArrayList<ItemStack>(Collections.nCopies(36, ItemStack.EMPTY));
        inventory.set(0, new ItemStack(Items.IRON_INGOT, 12));
        var physical = ResearchCurrencyPolicy.physical(ResourceLocation.parse("minecraft:iron_ingot"), 1);
        var input = BlueprintFragmentItem.createAnalysis(AnalysisTestItems.fragment, target, AnalysisRewardClass.PROTECTED_PREREQUISITES);
        input.setCount(offered);
        var policy = new AnalysisFragmentReconstructionService.Policy(target, true, 5, 1000, false,
                PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES, 1);
        var evaluated = AnalysisFragmentReconstructionService.evaluate(input, ItemStack.EMPTY, data, policy);
        var saved = data.serializeNBT().copy();
        for (var currency : List.of(physical, ResearchCurrencyPolicy.banked(10000, 2), physical)) {
            var preview = BlueprintAnalysisFragmentService.preview(evaluated, data, currency, inventory, 10000);
            assertEquals(currency.kind() == ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM ? 12 : 137, preview.pointBalance());
            assertEquals(offered >= 5, preview.createsBlueprint());
            assertEquals(offered, preview.accepted());
            assertEquals(0, preview.awardedPoints());
            assertEquals(preview.pointBalance(), BlueprintRecyclerPreview.fragment(preview).pointBalance());
        }
        inventory.set(0, ItemStack.EMPTY);
        assertEquals(0, BlueprintAnalysisFragmentService.preview(evaluated, data, physical, inventory, 10000).pointBalance());
        assertEquals(saved, data.serializeNBT());
        assertEquals(offered, input.getCount());
    }
}
