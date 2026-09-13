package com.gamergaming.taczweaponblueprints.compat.magazines;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MagazineCraftingAccessTest {
    private static final ResourceLocation RECIPE = id("taczmagazines:magazine/0000_9mm_17");
    private static final ResourceLocation FAMILY = TaCZMagazinesAdapter.familyKey("9mm_17");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void allThreeTiersAndOriginalAmmoWorkbenchAllowValidRecipesWithoutMagazineKnowledge() {
        var recipe = recipe();
        var capture = capture(recipe);
        assertTrue(MagazineCraftingAccess.hasMagazineTab(capture, recipe));
        for (String station : List.of("taczweaponblueprints:workbench_lvl1", "taczweaponblueprints:workbench_lvl2",
                "taczweaponblueprints:workbench_lvl3", "tacz:ammo_workbench")) {
            assertTrue(MagazineCraftingAccess.allows(capture, id(station), RECIPE, ignored -> recipe, 172, d -> true));
            assertEquals(Set.of(RECIPE), MagazineCraftingAccess.allowedRecipes(capture, id(station), ignored -> recipe, 172, d -> true));
        }
    }

    @Test
    void arbitraryWorkstationsAndRecipeIdsDoNotGainAccessFromTheNamespace() {
        var recipe = recipe();
        var capture = capture(recipe);
        for (var station : List.of(id("tacz:workbench"), id("other:ammo_workbench"))) {
            assertFalse(MagazineCraftingAccess.allows(capture, station, RECIPE, ignored -> recipe, 172, d -> true));
            assertTrue(MagazineCraftingAccess.allowedRecipes(capture, station, ignored -> recipe, 172, d -> true).isEmpty());
        }
        assertFalse(MagazineCraftingAccess.supportsWorkbench(null));
        assertFalse(MagazineCraftingAccess.allows(capture, MagazineCraftingAccess.AMMO_WORKBENCH,
                id("taczmagazines:magazine/fake"), ignored -> recipe, 172, d -> true));
        assertFalse(MagazineCraftingAccess.allows(capture, MagazineCraftingAccess.AMMO_WORKBENCH,
                id("tacz:ak47"), ignored -> recipe, 172, d -> true));
    }

    @Test
    void unavailableOrRemovedCatalogNeverKeepsAnEarlierPermission() {
        var recipe = recipe();
        for (var status : TaCZMagazinesAdapter.Status.values()) {
            var empty = TaCZMagazinesAdapter.Capture.empty(status);
            assertFalse(MagazineCraftingAccess.allows(empty, MagazineCraftingAccess.AMMO_WORKBENCH,
                    RECIPE, ignored -> recipe, 172, d -> true));
            assertTrue(MagazineCraftingAccess.allowedRecipes(empty, MagazineCraftingAccess.AMMO_WORKBENCH,
                    ignored -> recipe, 172, d -> true).isEmpty());
        }
        assertFalse(MagazineCraftingAccess.allows(capture(recipe), MagazineCraftingAccess.AMMO_WORKBENCH,
                RECIPE, ignored -> null, 172, d -> true));
    }

    @Test
    void staleLoadedOutputsAndChangedCostsAreRejectedUntilRecaptured() {
        for (String change : List.of("loaded", "capacity", "count", "family", "ingredient", "input_count")) {
            var recipe = recipe();
            var old = capture(recipe);
            switch (change) {
                case "loaded" -> recipe.getOutput().getOrCreateTag().putInt("AmmoCount", 1);
                case "capacity" -> recipe.getOutput().getOrCreateTag().putInt("MaxCapacity", 30);
                case "count" -> recipe.getOutput().setCount(2);
                case "family" -> recipe.getOutput().getOrCreateTag().putString("MagazineFamily", "other");
                case "ingredient" -> recipe.getInputs().set(0, new GunSmithTableIngredient(Ingredient.of(Items.DIAMOND), 5));
                case "input_count" -> recipe.getInputs().set(0, new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT), 6));
            }
            assertFalse(MagazineCraftingAccess.allows(old, MagazineCraftingAccess.AMMO_WORKBENCH,
                    RECIPE, ignored -> recipe, 172, d -> true), change);
            assertFalse(MagazineCraftingAccess.hasMagazineTab(old, recipe), change);
            assertTrue(MagazineCraftingAccess.allowedRecipes(old, MagazineCraftingAccess.AMMO_WORKBENCH,
                    ignored -> recipe, 172, d -> true).isEmpty(), change);
        }
    }

    @Test
    void wrongTabAndAliasCannotReuseAValidDescriptor() {
        var original = recipe();
        var wrongTab = new GunSmithTableRecipe(RECIPE, new GunSmithTableResult(original.getOutput(), id("tacz:ammo")), original.getInputs());
        var alias = new GunSmithTableRecipe(id("taczmagazines:magazine/0001_9mm_17"),
                new GunSmithTableResult(original.getOutput(), TaCZMagazinesAdapter.MAGAZINE_TAB), original.getInputs());
        for (var replacement : List.of(wrongTab, alias)) {
            assertFalse(MagazineCraftingAccess.allows(capture(original), MagazineCraftingAccess.AMMO_WORKBENCH,
                    RECIPE, ignored -> replacement, 172, d -> true));
        }
    }

    @Test
    void preservesTagAndItemCostsAndDetectsRecipeChangesAcrossLoginGeneration() {
        var recipe = recipe();
        recipe.getInputs().clear();
        recipe.getInputs().add(new GunSmithTableIngredient(Ingredient.of(net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, id("forge:ingots/iron"))), 12));
        recipe.getInputs().add(new GunSmithTableIngredient(Ingredient.of(Items.COPPER_INGOT), 3));
        var first = capture(recipe);
        var output = recipe.getOutput().copy();
        assertTrue(MagazineCraftingAccess.allows(first, MagazineCraftingAccess.AMMO_WORKBENCH, RECIPE, ignored -> recipe, 172, d -> true));
        assertTrue(MagazineCatalog.sameRecipes(first, capture(recipe)), "copies must not look like a reload change");
        assertTrue(ItemStack.matches(output, recipe.getOutput()));
        assertEquals(12, recipe.getInputs().get(0).getCount());
        assertEquals(3, recipe.getInputs().get(1).getCount());
        recipe.getInputs().set(1, new GunSmithTableIngredient(Ingredient.of(Items.COPPER_INGOT), 4));
        var changed = capture(recipe);
        assertFalse(MagazineCatalog.sameRecipes(first, changed));
        assertTrue(MagazineCraftingAccess.allows(changed, MagazineCraftingAccess.AMMO_WORKBENCH, RECIPE, ignored -> recipe, 172, d -> true));
        assertFalse(MagazineCatalog.sameRecipes(changed, TaCZMagazinesAdapter.Capture.empty(TaCZMagazinesAdapter.Status.READY)));
    }

    @Test
    void loginRecoverySendsRecipesToExistingPlayersBeforeRefreshingTheirAccess() {
        var recovered = capture(recipe());
        for (var priorStatus : List.of(TaCZMagazinesAdapter.Status.UNAVAILABLE,
                TaCZMagazinesAdapter.Status.WAITING_FOR_RECIPES)) {
            var failed = TaCZMagazinesAdapter.Capture.empty(priorStatus);
            assertTrue(MagazineDiscoveryEvents.shouldResendRecipes(true, failed, recovered),
                    "existing clients need the newly discovered recipes after " + priorStatus);
            assertFalse(MagazineDiscoveryEvents.shouldResendRecipes(false, failed, recovered),
                    "a full reload already sends recipes to everyone");
        }
        assertFalse(MagazineDiscoveryEvents.shouldResendRecipes(true, recovered, capture(recipe())),
                "an ordinary login must not rebroadcast identical recipes");
        var changed = recipe();
        changed.getInputs().set(0, new GunSmithTableIngredient(Ingredient.of(Items.COPPER_INGOT), 2));
        assertTrue(MagazineDiscoveryEvents.shouldResendRecipes(true, recovered, capture(changed)));
        var failed = TaCZMagazinesAdapter.Capture.empty(TaCZMagazinesAdapter.Status.UNAVAILABLE);
        assertTrue(MagazineDiscoveryEvents.shouldResendRecipes(true, recovered, failed));
        assertFalse(MagazineDiscoveryEvents.shouldResendRecipes(true, failed, failed));
    }

    @Test
    void combinedCatalogBoundCannotOverflowTheExistingAccessProtocol() {
        int limit = PlayerProgressionLimits.MAX_IDS_PER_COLLECTION;
        assertTrue(MagazineCraftingAccess.fitsAccessSnapshot(limit - 130, 130));
        assertFalse(MagazineCraftingAccess.fitsAccessSnapshot(limit - 129, 130));
        assertFalse(MagazineCraftingAccess.fitsAccessSnapshot(Integer.MAX_VALUE, 130));
        assertFalse(MagazineCraftingAccess.fitsAccessSnapshot(-1, 0));
        var recipe = recipe();
        var capture = capture(recipe);
        assertTrue(MagazineCraftingAccess.allows(capture, MagazineCraftingAccess.AMMO_WORKBENCH, RECIPE, ignored -> recipe, limit - 1, d -> true));
        assertFalse(MagazineCraftingAccess.allows(capture, MagazineCraftingAccess.AMMO_WORKBENCH, RECIPE, ignored -> recipe, limit, d -> true));
        assertTrue(MagazineCraftingAccess.allowedRecipes(capture, MagazineCraftingAccess.AMMO_WORKBENCH, ignored -> recipe, limit, d -> true).isEmpty());
    }

    @Test
    void changingKnowledgeOrModeRevokesTheSameRecipeUsedInTheOpenMenu() {
        var recipe = recipe(); var capture = capture(recipe);
        var learned = new java.util.HashSet<ResourceLocation>();
        var strict = new MagazineProgressionSettings(MagazineProgressionSettings.Mode.LINKED_WEAPON, false,
                com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier.TIER_1,
                com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier.TIER_1);
        java.util.function.Predicate<MagazineDescriptor> access = d -> strict.evaluate(d,
                com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier.TIER_1,
                false, learned::contains) == com.gamergaming.taczweaponblueprints.progression.CraftingEligibilityService.Status.ALLOWED;
        for (boolean knows : List.of(false, true, false)) {
            if (knows) learned.add(id("tacz:glock_17")); else learned.clear();
            assertEquals(knows, MagazineCraftingAccess.allows(capture, MagazineCraftingAccess.AMMO_WORKBENCH,
                    RECIPE, ignored -> recipe, 172, access));
            assertEquals(knows ? Set.of(RECIPE) : Set.of(), MagazineCraftingAccess.allowedRecipes(capture,
                    MagazineCraftingAccess.AMMO_WORKBENCH, ignored -> recipe, 172, access));
        }
    }

    private static GunSmithTableRecipe recipe() {
        var output = new ItemStack(Items.PAPER);
        output.getOrCreateTag().putString("MagazineFamily", "9mm_17");
        output.getOrCreateTag().putInt("MaxCapacity", 17);
        output.getOrCreateTag().putInt("AmmoCount", 0);
        return new GunSmithTableRecipe(RECIPE, new GunSmithTableResult(output, TaCZMagazinesAdapter.MAGAZINE_TAB),
                new ArrayList<>(List.of(new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT), 5))));
    }

    private static TaCZMagazinesAdapter.Capture capture(GunSmithTableRecipe recipe) {
        var descriptor = new MagazineDescriptor(FAMILY, "9mm_17", id("tacz:9mm"), 17, 0,
                Set.of(id("tacz:glock_17")), recipe.getId(), recipe.getOutput(), recipe.getInputs().stream()
                        .map(input -> new MagazineDescriptor.IngredientCost(input.getIngredient().toJson().toString(), input.getCount())).toList());
        return new TaCZMagazinesAdapter.Capture(TaCZMagazinesAdapter.Status.READY,
                Map.of(FAMILY, descriptor), Map.of(recipe.getId(), FAMILY), Map.of());
    }

    private static ResourceLocation id(String text) { return new ResourceLocation(text); }
}
