package com.gamergaming.taczweaponblueprints.compat.magazines;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.gson.JsonParser;
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

class TaCZMagazinesAdapterTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void absenceAndUnsupportedVersionsNeverLoadOptionalClassesOrReadRecipes() {
        for (String version : new String[] {null, "0.1.6-Beta", "0.3.0"}) {
            var adapter = TaCZMagazinesAdapter.create(version, () -> {
                throw new AssertionError("optional code must not be loaded");
            });
            assertEquals(version == null ? TaCZMagazinesAdapter.Status.ABSENT
                    : TaCZMagazinesAdapter.Status.UNSUPPORTED_VERSION, adapter.status());
            assertTrue(adapter.capture(null).families().isEmpty());
        }
    }

    @Test
    void missingOrIncompatibleApiDoesNotCrashTheMod() {
        assertEquals(TaCZMagazinesAdapter.Status.UNAVAILABLE,
                TaCZMagazinesAdapter.create(TaCZMagazinesAdapter.SUPPORTED_VERSION,
                        () -> { throw new NoClassDefFoundError("missing optional API"); }).status());
        assertEquals(TaCZMagazinesAdapter.Status.UNAVAILABLE,
                TaCZMagazinesAdapter.create(TaCZMagazinesAdapter.SUPPORTED_VERSION,
                        () -> { throw new IllegalStateException("missing method"); }).status());
    }

    @Test
    void capturesBaseExtendedAndIsolatedFamiliesUsingResolvedUpstreamGunMembership() {
        var source = new FakeSource();
        source.add("9mm_17", 17, 0, "pack:pistol_a", "pack:pistol_b");
        source.add("9mm_30_ext1", 30, 1, "pack:pistol_a");
        source.add("solo_pack_pistol_c", 19, 0, "pack:pistol_c");
        var capture = adapter(source).capture(List.of(recipe("9mm_17", 2, 17),
                recipe("9mm_30_ext1", 1, 30), recipe("solo_pack_pistol_c", 0, 19)));
        assertEquals(3, capture.families().size());
        assertTrue(capture.rejected().isEmpty());
        assertEquals(Set.of(id("pack:pistol_a"), id("pack:pistol_b")),
                capture.families().get(key("9mm_17")).compatibleGuns());
        assertEquals(1, capture.families().get(key("9mm_30_ext1")).extensionLevel());
        assertEquals(id("pack:9mm"), capture.families().get(key("solo_pack_pistol_c")).ammoId());
    }

    @Test
    void recipeOrderingChangesDoNotChangeFamilyIdentityOrKeepOldRecipeAliases() {
        var source = base();
        var catalog = new MagazineCatalog();
        var oldRecipe = recipe("9mm_17", 0, 17);
        var newRecipe = recipe("9mm_17", 24, 17);
        catalog.rebuild(adapter(source), List.of(oldRecipe));
        var current = catalog.rebuild(adapter(source), List.of(newRecipe));
        assertEquals(2, current.revision());
        assertEquals(Set.of(key("9mm_17")), current.catalog().families().keySet());
        assertEquals(Map.of(newRecipe.getId(), key("9mm_17")), current.catalog().familyByRecipe());
        assertFalse(current.catalog().familyByRecipe().containsKey(oldRecipe.getId()));
    }

    @Test
    void keepsCustomItemAndTagCostsWithoutMutatingTheUpstreamRecipe() {
        var source = base();
        var recipe = recipe("9mm_17", 0, 17);
        var tagJson = JsonParser.parseString("{\"tag\":\"forge:ingots/iron\"}");
        recipe.getInputs().clear();
        recipe.getInputs().add(new GunSmithTableIngredient(Ingredient.of(net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, id("forge:ingots/iron"))), 12));
        recipe.getInputs().add(new GunSmithTableIngredient(Ingredient.of(Items.COPPER_INGOT), 3));
        var before = recipe.getOutput().copy();
        var descriptor = adapter(source).capture(List.of(recipe)).families().get(key("9mm_17"));
        assertEquals(tagJson, JsonParser.parseString(descriptor.ingredients().get(0).ingredientJson()));
        assertEquals(12, descriptor.ingredients().get(0).count());
        assertEquals(3, descriptor.ingredients().get(1).count());
        assertTrue(ItemStack.matches(before, recipe.getOutput()));
        recipe.getInputs().clear();
        recipe.getOutput().getOrCreateTag().putInt("AmmoCount", 9);
        descriptor.output().getOrCreateTag().putInt("AmmoCount", 12);
        source.facts.clear();
        assertEquals(0, descriptor.output().getOrCreateTag().getInt("AmmoCount"));
        assertEquals(2, descriptor.ingredients().size());
        assertThrows(UnsupportedOperationException.class, () -> descriptor.compatibleGuns().clear());
    }

    @Test
    void disabledRecipesAndRemovedFamiliesDisappearOnTheNextPublication() {
        var source = base();
        var recipe = recipe("9mm_17", 0, 17);
        var catalog = new MagazineCatalog();
        assertEquals(1, catalog.rebuild(adapter(source), List.of(recipe)).catalog().families().size());
        source.disabled.add("9mm_17");
        assertTrue(catalog.rebuild(adapter(source), List.of(recipe)).catalog().families().isEmpty());
        source.disabled.clear();
        assertTrue(catalog.rebuild(adapter(source), List.of()).catalog().families().isEmpty());
        source.facts.clear();
        assertTrue(catalog.rebuild(adapter(source), List.of(recipe)).catalog().families().isEmpty());
    }

    @Test
    void namespaceAloneCannotAdmitOtherItemsOrUnknownFamilies() {
        var source = base();
        var unrelated = new GunSmithTableRecipe(id("taczmagazines:magazine/fake"),
                new GunSmithTableResult(new ItemStack(Items.DIAMOND), TaCZMagazinesAdapter.MAGAZINE_TAB), List.of());
        var wrongTab = new GunSmithTableRecipe(id("taczmagazines:magazine/wrong_tab"),
                new GunSmithTableResult(output("9mm_17", 17), id("tacz:ammo")), List.of());
        var foreign = new GunSmithTableRecipe(id("anothermod:magazine/fake"),
                new GunSmithTableResult(output("9mm_17", 17), TaCZMagazinesAdapter.MAGAZINE_TAB), List.of());
        var capture = adapter(source).capture(List.of(unrelated, wrongTab, foreign, recipe("unknown", 0, 17)));
        assertTrue(capture.families().isEmpty());
        assertEquals(3, capture.rejected().size());
    }

    @Test
    void rejectsLoadedMismatchedAndMalformedOutputsWithoutDroppingValidFamilies() {
        for (String corruption : List.of("loaded", "capacity", "count", "family")) {
            var source = base();
            source.add("9mm_20", 20, 0, "pack:pistol_c");
            var invalid = recipe("9mm_17", 0, 17);
            switch (corruption) {
                case "loaded" -> invalid.getOutput().getOrCreateTag().putInt("AmmoCount", 1);
                case "capacity" -> invalid.getOutput().getOrCreateTag().putInt("MaxCapacity", 99);
                case "count" -> invalid.getOutput().setCount(2);
                case "family" -> invalid.getOutput().getOrCreateTag().putString("MagazineFamily", "bad:family");
            }
            var capture = adapter(source).capture(List.of(invalid, recipe("9mm_20", 1, 20)));
            assertEquals(Set.of(key("9mm_20")), capture.families().keySet(), corruption);
            assertEquals(1, capture.rejected().size());
        }
    }

    @Test
    void ambiguousFamilyRecipesAreRejectedRegardlessOfIterationOrder() {
        var a = recipe("9mm_17", 0, 17);
        var b = recipe("9mm_17", 1, 17);
        var c = recipe("9mm_17", 2, 17);
        for (var recipes : List.of(List.of(a, b, c), List.of(c, a, b))) {
            var capture = adapter(base()).capture(recipes);
            assertTrue(capture.families().isEmpty());
            assertTrue(capture.familyByRecipe().isEmpty());
            assertEquals("multiple recipes for magazine family", capture.rejected().get(key("9mm_17").toString()));
        }
    }

    @Test
    void failedCaptureInvalidatesOldDataAndARecoveryRebuildCanSucceed() {
        var source = base();
        var catalog = new MagazineCatalog();
        var recipes = List.of(recipe("9mm_17", 0, 17));
        catalog.initialize(adapter(source));
        assertEquals(TaCZMagazinesAdapter.Status.WAITING_FOR_RECIPES, catalog.publication().catalog().status());
        catalog.rebuild(adapter(source), recipes);
        source.broken = true;
        var failed = catalog.rebuild(adapter(source), recipes);
        assertEquals(TaCZMagazinesAdapter.Status.UNAVAILABLE, failed.catalog().status());
        assertTrue(failed.catalog().families().isEmpty());
        source.broken = false;
        assertEquals(1, catalog.rebuild(adapter(source), recipes).catalog().families().size());
        catalog.clear();
        assertEquals(0, catalog.publication().revision());
        assertTrue(catalog.publication().catalog().families().isEmpty());
    }

    @Test
    void boundsExcessiveDiscoveryAndPreservesNamespacedPathComponentsInFamilyKeys() {
        var source = base();
        source.extraFamilies = IntStream.range(0, TaCZMagazinesAdapter.MAX_FAMILIES + 1)
                .mapToObj(i -> "ammo_" + i).collect(Collectors.toSet());
        assertEquals(TaCZMagazinesAdapter.Status.UNAVAILABLE, adapter(source).capture(List.of()).status());
        assertEquals(id("taczmagazines:family/bullets/9mm_17"), key("bullets/9mm_17"));
        assertNotEquals(key("bullets/9mm_17"), key("bullets_9mm_17"));
    }

    private static TaCZMagazinesAdapter adapter(FakeSource source) {
        return TaCZMagazinesAdapter.create(TaCZMagazinesAdapter.SUPPORTED_VERSION, () -> source);
    }

    private static FakeSource base() {
        var source = new FakeSource();
        source.add("9mm_17", 17, 0, "pack:pistol_a");
        return source;
    }

    private static GunSmithTableRecipe recipe(String family, int ordinal, int capacity) {
        return new GunSmithTableRecipe(id("taczmagazines:magazine/" + String.format("%04d_", ordinal) + family),
                new GunSmithTableResult(output(family, capacity), TaCZMagazinesAdapter.MAGAZINE_TAB),
                new ArrayList<>(List.of(new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT), 4))));
    }

    private static ItemStack output(String family, int capacity) {
        var stack = new ItemStack(Items.PAPER);
        stack.getOrCreateTag().putString("MagazineFamily", family);
        stack.getOrCreateTag().putInt("MaxCapacity", capacity);
        stack.getOrCreateTag().putInt("AmmoCount", 0);
        return stack;
    }

    private static ResourceLocation id(String value) { return new ResourceLocation(value); }
    private static ResourceLocation key(String family) { return TaCZMagazinesAdapter.familyKey(family); }

    private static class FakeSource implements TaCZMagazinesAdapter.Source {
        final Map<String, TaCZMagazinesAdapter.Family> facts = new HashMap<>();
        final Set<String> disabled = new HashSet<>();
        Set<String> extraFamilies = Set.of();
        boolean broken;
        void add(String family, int capacity, int extension, String... guns) {
            facts.put(family, new TaCZMagazinesAdapter.Family(id("pack:9mm"), capacity, extension,
                    java.util.Arrays.stream(guns).map(TaCZMagazinesAdapterTest::id).collect(Collectors.toSet())));
        }
        @Override public Set<String> families() {
            if (broken) throw new NoSuchMethodError("changed API");
            return extraFamilies.isEmpty() ? facts.keySet() : extraFamilies;
        }
        @Override public boolean isMagazine(ItemStack stack) { return stack.is(Items.PAPER); }
        @Override public String family(ItemStack stack) { return stack.getOrCreateTag().getString("MagazineFamily"); }
        @Override public int capacity(ItemStack stack) { return stack.getOrCreateTag().getInt("MaxCapacity"); }
        @Override public int ammoCount(ItemStack stack) { return stack.getOrCreateTag().getInt("AmmoCount"); }
        @Override public boolean recipeEnabled(String family) { return !disabled.contains(family); }
        @Override public TaCZMagazinesAdapter.Family describe(String family) { return facts.get(family); }
    }
}
