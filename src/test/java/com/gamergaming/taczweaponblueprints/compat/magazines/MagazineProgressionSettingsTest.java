package com.gamergaming.taczweaponblueprints.compat.magazines;

import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier.*;
import static com.gamergaming.taczweaponblueprints.progression.CraftingEligibilityService.Status.*;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.progression.CraftingEligibilityService.Status;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.progression.workbench.CraftingWorkbenchTierResolver;
import com.gamergaming.taczweaponblueprints.testing.AnalysisTestItems;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class MagazineProgressionSettingsTest {
    static final ResourceLocation A = id("pack:rifle_a"), B = id("pack:rifle_b"), OTHER = id("pack:same_caliber_isolated");
    @BeforeAll static void bootstrap() { AnalysisTestItems.initialize(); }
    static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    static MagazineDescriptor magazine(int extension) {
        String family = extension == 0 ? "9mm_17" : "9mm_33_ext" + extension;
        var recipe = id("taczmagazines:magazine/0010_" + family);
        return new MagazineDescriptor(TaCZMagazinesAdapter.familyKey(family), family, id("tacz:9mm"),
                extension == 0 ? 17 : 33, extension, Set.of(A,B), recipe, new ItemStack(Items.PAPER),
                List.of(new MagazineDescriptor.IngredientCost("{\"item\":\"minecraft:iron_ingot\"}", 5)));
    }
    @Test void defaultsAndOlderSettingsKeepMaterialOnlyAccessAtAllFourWorkbenches() {
        var config = ConfigApi.deserializeConfig(new BlueprintConfig(), "version = 5\n").get();
        config.onUpdateClient();
        assertEquals(MagazineProgressionSettings.DEFAULT, config.magazineSettings());
        for (var station : List.of(CraftingWorkbenchTierResolver.TIER_1_ID, CraftingWorkbenchTierResolver.TIER_2_ID,
                CraftingWorkbenchTierResolver.TIER_3_ID, MagazineCraftingAccess.AMMO_WORKBENCH))
            for (int extension = 0; extension <= 3; extension++)
                assertEquals(ALLOWED, config.magazineSettings().evaluate(magazine(extension),
                        MagazineProgressionSettings.workstationTier(station,config.researchFeatureSnapshot()),false,id -> false));
    }
    @Test void anyCompatibleLearnedGunQualifiesButUnrelatedAndIsolatedGunsDoNot() {
        var strict = new MagazineProgressionSettings(MagazineProgressionSettings.Mode.LINKED_WEAPON,false,TIER_1,TIER_1);
        for (int extension = 0; extension <= 3; extension++) {
            assertEquals(ALLOWED, strict.evaluate(magazine(extension),TIER_1,false,A::equals));
            assertEquals(ALLOWED, strict.evaluate(magazine(extension),TIER_1,false,B::equals));
            assertEquals(RECIPE_NOT_LEARNED, strict.evaluate(magazine(extension),TIER_3,false,OTHER::equals));
            assertEquals(RECIPE_NOT_LEARNED, strict.evaluate(magazine(extension),TIER_3,false,null));
        }
    }
    @Test void basicFallbackProtectsFoundGunsButDoesNotUnlockExtendedMagazines() {
        var linked = new MagazineProgressionSettings(MagazineProgressionSettings.Mode.LINKED_WEAPON,true,TIER_2,TIER_3);
        assertEquals(ALLOWED,linked.evaluate(magazine(0),TIER_2,false,id -> false));
        assertEquals(WORKBENCH_TIER_REQUIRED,linked.evaluate(magazine(0),TIER_1,false,id -> true));
        assertEquals(RECIPE_NOT_LEARNED,linked.evaluate(magazine(1),TIER_3,false,id -> false));
        assertEquals(ALLOWED,linked.evaluate(magazine(1),TIER_3,false,B::equals));
    }
    @ParameterizedTest @CsvSource({"0,1,WORKBENCH_TIER_REQUIRED", "0,2,ALLOWED", "0,3,ALLOWED", "1,1,WORKBENCH_TIER_REQUIRED", "1,2,WORKBENCH_TIER_REQUIRED", "1,3,ALLOWED", "2,2,WORKBENCH_TIER_REQUIRED", "2,3,ALLOWED", "3,2,WORKBENCH_TIER_REQUIRED", "3,3,ALLOWED"})
    void higherBenchesInheritBasicAndExtendedMinimums(int extension,int tier,Status expected) {
        var settings = new MagazineProgressionSettings(MagazineProgressionSettings.Mode.COMPATIBILITY,true,TIER_2,TIER_3);
        assertEquals(expected,settings.evaluate(magazine(extension),ResearchWorkbenchTier.fromLevel(tier),false,id -> false));
    }
    @Test void creativeTierBypassDoesNotGrantUnlearnedWeapons() {
        var settings = new MagazineProgressionSettings(MagazineProgressionSettings.Mode.LINKED_WEAPON,false,TIER_3,TIER_3);
        assertEquals(ALLOWED,settings.evaluate(magazine(1),TIER_1,true,B::equals));
        assertEquals(RECIPE_NOT_LEARNED,settings.evaluate(magazine(1),TIER_1,true,id -> false));
    }
    @Test void originalAmmoBenchCannotSilentlyBypassTiersThroughUnknownExternalFallback() {
        var config = new BlueprintConfig();
        config.unknownExternalWorkstationsUnrestricted.validateAndSet(true);
        config.unknownWorkstationFallbackTier.validateAndSet(TIER_3);
        config.onUpdateClient();
        assertEquals(TIER_1,MagazineProgressionSettings.workstationTier(MagazineCraftingAccess.AMMO_WORKBENCH,config.researchFeatureSnapshot()));
        config.externalWorkstationTiers.validateAndSet(Map.of("tacz:ammo_workbench",TIER_2)); config.onUpdateClient();
        assertEquals(TIER_2,MagazineProgressionSettings.workstationTier(MagazineCraftingAccess.AMMO_WORKBENCH,config.researchFeatureSnapshot()));
        assertEquals(TIER_1,MagazineProgressionSettings.workstationTier(CraftingWorkbenchTierResolver.TIER_1_ID,config.researchFeatureSnapshot()));
    }
    @Test void modeAndDormantCustomValuesRoundTripAndPublishWithoutRewritingOtherRules() {
        var config = new BlueprintConfig();
        config.magazineProgressionMode.validateAndSet(MagazineProgressionSettings.Mode.LINKED_WEAPON);
        config.magazineKeepBasicCrafting.validateAndSet(false);
        config.magazineBasicWorkbenchTier.validateAndSet(TIER_2);
        config.magazineExtendedWorkbenchTier.validateAndSet(TIER_3); config.onUpdateClient();
        var expected = config.magazineSettings();
        config.magazineProgressionMode.validateAndSet(MagazineProgressionSettings.Mode.COMPATIBILITY);
        var restored = ConfigApi.deserializeConfig(new BlueprintConfig(),ConfigApi.serializeConfig(config).get()).get();
        restored.magazineProgressionMode.validateAndSet(MagazineProgressionSettings.Mode.LINKED_WEAPON); restored.onUpdateClient();
        assertEquals(expected,restored.magazineSettings());
        assertEquals(config.progressionSnapshot(),restored.progressionSnapshot());
        assertEquals(config.accessSnapshot(),restored.accessSnapshot());
    }
}
