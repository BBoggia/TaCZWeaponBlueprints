package com.gamergaming.taczweaponblueprints.datagen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.api.BlueprintSpawnRate;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.loot.AddItemsModifier;
import com.gamergaming.taczweaponblueprints.loot.DynamicBlueprintLootModifier;
import com.gamergaming.taczweaponblueprints.resource.LootTableResourceLoader;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.data.GlobalLootModifierProvider;
import net.minecraftforge.common.loot.LootTableIdCondition;
import net.minecraftforge.fml.ModList;


public class ModGlobalLootModifiersProvider extends GlobalLootModifierProvider {
    private static final int LEGACY_MIN_ROLLS = 1;
    private static final int LEGACY_MAX_ROLLS = 2;
    private static final float LEGACY_POOL_PROBABILITY = 0.2f;

    public ModGlobalLootModifiersProvider(PackOutput output) {
        super(output, TaCZWeaponBlueprints.MODID);
        TaCZWeaponBlueprints.LOGGER.info("Creating global loot modifiers");
    }

    @Override
    protected void start() {
        add("dynamic_blueprints", new DynamicBlueprintLootModifier(new LootItemCondition[0]));

        Map<String, JsonObject> lootTableLists = LootTableResourceLoader.getAllLootTableLists();
        Map<String, JsonArray> spawnRates = LootTableResourceLoader.getAllLootTableSpawnRates();
        int generatedModifiers = 0;

        for (Map.Entry<String, JsonObject> tierEntry : lootTableLists.entrySet()) {
            String tier = tierEntry.getKey();
            JsonArray tierSpawnRates = spawnRates.get(tier);
            if (tierSpawnRates == null) {
                throw new IllegalStateException("No blueprint spawn-rate pool exists for tier " + tier);
            }

            List<Pair<ItemStack, Float>> blueprintPool = createBlueprintPool(tierSpawnRates);
            if (blueprintPool.isEmpty()) {
                TaCZWeaponBlueprints.LOGGER.warn("No eligible blueprint entries were generated for tier {}", tier);
                continue;
            }

            for (Map.Entry<String, JsonElement> namespaceEntry : tierEntry.getValue().entrySet()) {
                String namespace = namespaceEntry.getKey();
                if (!ModList.get().isLoaded(namespace)) {
                    TaCZWeaponBlueprints.LOGGER.debug(
                            "Skipping {} blueprint loot tables because mod {} is not loaded during data generation",
                            tier,
                            namespace);
                    continue;
                }

                for (JsonElement element : namespaceEntry.getValue().getAsJsonArray()) {
                    ResourceLocation lootTableId = ResourceLocation.tryParse(element.getAsString());
                    if (lootTableId == null || !namespace.equals(lootTableId.getNamespace())) {
                        throw new IllegalStateException(
                                "Invalid or incorrectly grouped loot table ID in tier " + tier + ": " + element);
                    }

                    String modifierPath = namespace + "/" + tier + "_"
                            + lootTableId.getPath().replace('/', '_')
                            + "_blueprint_spawn_rates";
                    add(
                            modifierPath,
                            new AddItemsModifier(
                                    new LootItemCondition[]{
                                            new LootTableIdCondition.Builder(lootTableId).build()
                                    },
                                    blueprintPool,
                                    LEGACY_MIN_ROLLS,
                                    LEGACY_MAX_ROLLS,
                                    LEGACY_POOL_PROBABILITY));
                    generatedModifiers++;
                }
            }
        }

        TaCZWeaponBlueprints.LOGGER.info(
                "Generated 1 dynamic and {} legacy blueprint global loot modifiers",
                generatedModifiers);
    }

    private static List<Pair<ItemStack, Float>> createBlueprintPool(JsonArray spawnRates) {
        List<Pair<ItemStack, Float>> blueprintPool = new ArrayList<>();
        for (JsonElement element : spawnRates) {
            BlueprintSpawnRate spawnRate = BlueprintSpawnRate.fromJson(element.getAsJsonObject());
            // Preserve the historical float operation order so regeneration does not
            // introduce one-thousandth rounding churn in existing modifier files.
            float weight = Math.round(spawnRate.score() * 100 * 1000) / 1000.0f;
            blueprintPool.add(Pair.of(BlueprintItem.createBlueprint(spawnRate.id()), weight));
        }
        return List.copyOf(blueprintPool);
    }
}
