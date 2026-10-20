package com.gamergaming.taczweaponblueprints.acceptance;

import java.nio.file.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.block.CraftingWorkbenchBlock;
import com.gamergaming.taczweaponblueprints.compat.magazines.*;
import com.gamergaming.taczweaponblueprints.compat.tacz.TaCZWorkbenchMenuBridge;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.progression.CraftingEligibilityService;
import com.gamergaming.taczweaponblueprints.progression.workbench.*;
import com.tacz.guns.crafting.*;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;

/** Opt-in native crafting regression checks, excluded from the distributable jar. */
@EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID)
public final class ReviewFixesServerProbe {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        String output = System.getProperty("tacz.reviewFixesQA", "");
        if (output.isEmpty()) return;
        event.getServer().execute(() -> {
            var results = new ArrayList<String>();
            var server = event.getServer();
            var catalog = MagazineCatalog.INSTANCE;
            var original = catalog.publication();
            boolean enabled = ModConfigs.BLUEPRINT.enableBlueprints.get();
            try {
                var publicationField = MagazineCatalog.class.getDeclaredField("publication");
                publicationField.setAccessible(true);
                var player = FakePlayerFactory.getMinecraft(server.overworld());
                player.setHealth(20); player.moveTo(.5, 100, .5);
                var pos = new BlockPos(1, 100, 0);
                var station = id("taczweaponblueprints:workbench_lvl1");
                require(CraftingWorkbenchBlock.placeCompleteStructure(player.level(), pos,
                        BuiltInRegistries.BLOCK.get(station).defaultBlockState()), "placed crafting Workbench", results);
                var menu = new GunSmithTableMenu(7, player.getInventory(), station);
                player.containerMenu = menu;
                ((TaCZWorkbenchMenuBridge) menu).taczweaponblueprints$attachWorkbenchContext(
                        new ResearchWorkbenchContext(pos, player.level().dimension().location(), station,
                                ResearchWorkbenchTier.TIER_1, ResearchInteractionMode.CRAFTING, 8));
                require(CraftingWorkbenchAuthority.valid(player, menu), "physical menu authority valid", results);
                var recipe = new GunSmithTableRecipe(
                        new GunSmithTableResult(new ItemStack(Items.PAPER), TaCZMagazinesAdapter.MAGAZINE_TAB),
                        new ArrayList<>(List.of(new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT), 5))));
                var recipeIdFixture = id("taczmagazines:magazine/review_fixture");
                var recipes = new ArrayList<net.minecraft.world.item.crafting.RecipeHolder<?>>(server.getRecipeManager().getRecipes());
                recipes.add(new net.minecraft.world.item.crafting.RecipeHolder<>(recipeIdFixture, recipe)); server.getRecipeManager().replaceRecipes(recipes);
                var families = new LinkedHashMap<ResourceLocation, MagazineDescriptor>();
                var ids = new LinkedHashMap<ResourceLocation, ResourceLocation>();
                for (int i = 0; i < 4096; i++) {
                    String family = "review_" + i;
                    var key = TaCZMagazinesAdapter.familyKey(family);
                    var recipeId = i == 0 ? recipeIdFixture : id("taczmagazines:magazine/review_" + i);
                    families.put(key, new MagazineDescriptor(key, family, id("tacz:9mm"), 17, 0,
                            Set.of(id("tacz:glock_17")), recipeId, recipe.getOutput(),
                            List.of(new MagazineDescriptor.IngredientCost(Ingredient.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, Ingredient.of(Items.IRON_INGOT)).getOrThrow().toString(), 5))));
                    ids.put(recipeId, key);
                }
                publicationField.set(catalog, new MagazineCatalog.Publication(99,
                        new TaCZMagazinesAdapter.Capture(TaCZMagazinesAdapter.Status.READY, families, ids, Map.of())));
                ModConfigs.BLUEPRINT.enableBlueprints.validateAndSet(false);
                require(CraftingEligibilityService.snapshot(player, menu).status() == CraftingEligibilityService.Status.ALLOWED,
                        "disabled progression publishes unrestricted access", results);
                require(CraftingEligibilityService.evaluate(player, menu, recipeIdFixture).allowed(),
                        "large catalog does not block disabled progression", results);
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 4));
                menu.doCraft(recipeIdFixture, player);
                require(player.getInventory().getItem(0).getCount() == 4, "native crafting rejects insufficient ingredients", results);
                player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 7));
                menu.doCraft(recipeIdFixture, player);
                require(player.getInventory().getItem(0).getCount() == 2, "native crafting debits exactly five iron", results);
                menu.doCraft(id("taczmagazines:magazine/nonexistent"), player);
                require(player.getInventory().getItem(0).getCount() == 2, "unknown native recipe cannot consume ingredients", results);
                ModConfigs.BLUEPRINT.enableBlueprints.validateAndSet(true);
                require(!CraftingEligibilityService.evaluate(player, menu, recipeIdFixture).allowed(),
                        "enabled progression retains catalog capacity guard", results);
                publicationField.set(catalog, new MagazineCatalog.Publication(100,
                        new TaCZMagazinesAdapter.Capture(TaCZMagazinesAdapter.Status.UNAVAILABLE, Map.of(), Map.of(), Map.of())));
                require(!CraftingEligibilityService.evaluate(player, menu, recipeIdFixture).allowed(),
                        "enabled progression rejects unavailable adapter", results);
                ModConfigs.BLUEPRINT.enableBlueprints.validateAndSet(false);
                require(CraftingEligibilityService.evaluate(player, menu, recipeIdFixture).allowed(),
                        "disabled progression delegates when adapter is unavailable", results);
                player.moveTo(100, 100, 100);
                require(!CraftingEligibilityService.evaluate(player, menu, recipeIdFixture).allowed(),
                        "disabled progression still rejects distant workstation", results);
                results.add("PASS");
            } catch (Throwable failure) {
                results.add("FAIL: " + failure);
                TaCZWeaponBlueprints.LOGGER.error("Review regression probe failed", failure);
            } finally {
                ModConfigs.BLUEPRINT.enableBlueprints.validateAndSet(enabled);
                try {
                    var field = MagazineCatalog.class.getDeclaredField("publication"); field.setAccessible(true); field.set(catalog, original);
                    Files.write(Path.of(output), results);
                } catch (Exception failure) { TaCZWeaponBlueprints.LOGGER.error("Unable to save review probe", failure); }
                server.halt(false);
            }
        });
    }
    private static void require(boolean condition, String message, List<String> results) {
        if (!condition) throw new AssertionError(message);
        results.add(message);
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
}
