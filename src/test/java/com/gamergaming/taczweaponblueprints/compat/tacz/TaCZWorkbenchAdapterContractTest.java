package com.gamergaming.taczweaponblueprints.compat.tacz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarFile;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.manager.RecipeFilterManager;
import com.tacz.guns.resource.pojo.BlockIndexPOJO;
import com.tacz.guns.resource.pojo.data.block.BlockData;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.crafting.ConditionalRecipe;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.FalseCondition;

/** Pins the TaCZ 1.1.8 and Forge 47 seams selected for tiered workstations. */
class TaCZWorkbenchAdapterContractTest {
    private static final String FIXTURE_ROOT = "/fixtures/tiered-workbench/";

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try {
            CraftingHelper.register(FalseCondition.Serializer.INSTANCE);
        } catch (IllegalStateException duplicateRegistration) {
            // A fuller Forge test bootstrap may already have installed it.
            assertTrue(duplicateRegistration.getMessage().contains("Duplicate"));
        }
    }

    @Test
    void nativeMenuKeepsAWorkstationResourceIdAndCraftingEntryPoint()
            throws ReflectiveOperationException, IOException {
        Constructor<GunSmithTableMenu> constructor = GunSmithTableMenu.class
                .getConstructor(int.class, Inventory.class, ResourceLocation.class);
        Method getBlockId = GunSmithTableMenu.class.getMethod("getBlockId");
        Method doCraft = GunSmithTableMenu.class.getMethod(
                "doCraft", ResourceLocation.class, Player.class);
        Method stillValid = GunSmithTableMenu.class.getMethod("stillValid", Player.class);
        Method getRecipe = GunSmithTableMenu.class.getDeclaredMethod(
                "getRecipe", ResourceLocation.class, RecipeManager.class);
        Field blockId = GunSmithTableMenu.class.getDeclaredField("blockId");

        assertNotNull(constructor);
        assertEquals(ResourceLocation.class, getBlockId.getReturnType());
        assertEquals(void.class, doCraft.getReturnType());
        assertEquals(boolean.class, stillValid.getReturnType());
        assertEquals(GunSmithTableRecipe.class, getRecipe.getReturnType());
        assertTrue(Modifier.isPrivate(getRecipe.getModifiers()));
        assertTrue(Modifier.isPrivate(blockId.getModifiers()));
        assertTrue(Modifier.isFinal(blockId.getModifiers()));
        assertTrue(classBytecode(GunSmithTableMenu.class).contains("readResourceLocation"),
                "the native menu factory must still synchronize its workstation ID");
    }

    @Test
    void nativeClientScreenAcceptsTheNativeMenuType() throws ReflectiveOperationException {
        assertNotNull(GunSmithTableScreen.class.getConstructor(
                GunSmithTableMenu.class,
                Inventory.class,
                net.minecraft.network.chat.Component.class));
    }

    @Test
    void nativeBlockEntityExposesThePhysicalMenuSourceSeam()
            throws ReflectiveOperationException {
        Method createMenu = GunSmithTableBlockEntity.class.getMethod(
                "createMenu", int.class, Inventory.class, Player.class);
        Method getId = GunSmithTableBlockEntity.class.getMethod("getId");
        Method getRootPos = AbstractGunSmithTableBlock.class.getMethod(
                "getRootPos",
                net.minecraft.core.BlockPos.class,
                net.minecraft.world.level.block.state.BlockState.class);

        assertEquals(AbstractContainerMenu.class, createMenu.getReturnType());
        assertEquals(ResourceLocation.class, getId.getReturnType());
        assertEquals(net.minecraft.core.BlockPos.class, getRootPos.getReturnType());
    }

    @Test
    void taczReloadManagersScanStandardDataResourceDirectories() throws IOException {
        String commonAssetsBytecode = classBytecode(CommonAssetsManager.class);
        String filterManagerBytecode = classBytecode(RecipeFilterManager.class);

        assertTrue(commonAssetsBytecode.contains("data/blocks"));
        assertTrue(commonAssetsBytecode.contains("index/blocks"));
        assertTrue(filterManagerBytecode.contains("recipe_filters"));
    }

    @Test
    void dedicatedWorkbenchDataUsesTaCZNativeBlockAndFilterShapes()
            throws IOException, ReflectiveOperationException {
        BlockData data = CommonAssetsManager.GSON.fromJson(
                fixtureJson("data/taczweaponblueprints/data/blocks/workbench_lvl1.json"),
                BlockData.class);
        RecipeFilter filter = CommonAssetsManager.GSON.fromJson(
                fixtureJson("data/taczweaponblueprints/recipe_filters/workbench_all.json"),
                RecipeFilter.class);
        BlockIndexPOJO index = CommonAssetsManager.GSON.fromJson(
                fixtureJson("data/taczweaponblueprints/index/blocks/workbench_lvl1.json"),
                BlockIndexPOJO.class);
        Field tabs = BlockData.class.getDeclaredField("tabs");
        tabs.setAccessible(true);

        assertEquals(id("taczweaponblueprints:workbench_all"), data.getFilter());
        assertTrue(((java.util.List<?>) tabs.get(data)).isEmpty());
        assertTrue(classBytecode(BlockData.class).contains("DEFAULT_TABS"),
                "an empty tab list must retain TaCZ's default-tab fallback");
        assertTrue(filter.contains(id("thirdparty:any_recipe")));
        assertEquals(id("taczweaponblueprints:workbench_lvl1"), index.getId());
        assertEquals(id("taczweaponblueprints:workbench_lvl1"), index.getData());
        assertEquals(id("tacz:gun_smith_table"), index.getDisplay());
    }

    @Test
    void productionPublishesAllThreeDedicatedCraftingWorkbenchIds() throws IOException {
        for (String path : java.util.List.of(
                "workbench_lvl1",
                "workbench_lvl2",
                "workbench_lvl3")) {
            JsonObject data = classpathJson(
                    "/data/taczweaponblueprints/data/blocks/" + path + ".json");
            JsonObject index = classpathJson(
                    "/data/taczweaponblueprints/index/blocks/" + path + ".json");
            assertEquals(
                    "taczweaponblueprints:workbench_all",
                    data.get("filter").getAsString());
            assertTrue(data.getAsJsonArray("tabs").isEmpty());
            assertEquals("taczweaponblueprints:" + path, index.get("id").getAsString());
            assertEquals("taczweaponblueprints:" + path, index.get("data").getAsString());
        }
        assertTrue(classpathJson(
                "/data/taczweaponblueprints/recipe_filters/workbench_all.json")
                .entrySet().isEmpty());
    }

    @Test
    void productionSuppressesOnlyTheLegacyTableRecipe() throws IOException {
        JsonObject override = classpathJson("/data/tacz/recipes/gun_smith_table.json");
        Recipe<?> parsed = new ConditionalRecipe.Serializer<Recipe<?>>().fromJson(
                id("tacz:gun_smith_table"),
                override,
                net.minecraftforge.common.crafting.conditions.ICondition.IContext.EMPTY);
        assertNull(parsed);
    }

    @Test
    void falseConditionalOverrideSkipsTheLegacyRecipeCleanly() throws IOException {
        JsonObject override = fixtureJson("data/tacz/recipes/gun_smith_table.json");

        assertEquals("forge:conditional", override.get("type").getAsString());
        Recipe<?> parsed = new ConditionalRecipe.Serializer<Recipe<?>>().fromJson(
                id("tacz:gun_smith_table"),
                override,
                net.minecraftforge.common.crafting.conditions.ICondition.IContext.EMPTY);
        assertNull(parsed, "forge:false must omit the overridden recipe without a placeholder");
    }

    @Test
    void overrideTargetsTheExactBundledTaCZRecipe() throws Exception {
        JsonObject original;
        Path dependency = Path.of(GunSmithTableMenu.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        try (JarFile jar = new JarFile(dependency.toFile())) {
            var entry = jar.getJarEntry("data/tacz/recipes/gun_smith_table.json");
            assertNotNull(entry, "TaCZ's bundled Gun Smith Table recipe moved");
            try (InputStream stream = jar.getInputStream(entry)) {
                original = JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }

        assertEquals("minecraft:crafting_shaped", original.get("type").getAsString());
        assertEquals(
                "tacz:gun_smith_table",
                original.getAsJsonObject("result").get("item").getAsString());
    }

    private static JsonObject fixtureJson(String relativePath) throws IOException {
        try (InputStream stream = TaCZWorkbenchAdapterContractTest.class.getResourceAsStream(
                FIXTURE_ROOT + relativePath)) {
            assertNotNull(stream, relativePath);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    private static JsonObject classpathJson(String path) throws IOException {
        try (InputStream stream = TaCZWorkbenchAdapterContractTest.class
                .getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(
                    stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static String classBytecode(Class<?> type) throws IOException {
        String path = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
