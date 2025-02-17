package com.gamergaming.taczweaponblueprints.datagen;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.api.BlueprintSpawnRate;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.loot.AddItemsModifier;
import com.gamergaming.taczweaponblueprints.resource.LootTableResourceLoader;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.data.GlobalLootModifierProvider;
import net.minecraftforge.common.loot.LootTableIdCondition;
import net.minecraftforge.fml.ModList;


public class ModGlobalLootModifiersProvider extends GlobalLootModifierProvider {

    public ModGlobalLootModifiersProvider(PackOutput output) {
        super(output, TaCZWeaponBlueprints.MODID);
        TaCZWeaponBlueprints.LOGGER.info("Creating global loot modifiers");
    }

    @Override
    protected void start() {
        // TaCZWeaponBlueprints.LOGGER.info("!!!CREATING GLOBAL LOOT MODIFIERS!!!");
        Map<String, JsonObject> lootTableLists = LootTableResourceLoader.getAllLootTableLists();
        Map<String, JsonArray> spawnRates = LootTableResourceLoader.getAllLootTableSpawnRates();

        for (Map.Entry<String, JsonObject> entry : lootTableLists.entrySet()) {
            String tableGroup = entry.getKey(); // easy, medium, hard, nether, village, water
            JsonObject groupListSet = entry.getValue();

            List<Pair<ItemStack, Float>> bpItemsWithChances = getAllBlueprintItemSpawnChancePairList(spawnRates.get(tableGroup));
            
            
            for (Map.Entry<String, JsonElement> groupEntry : groupListSet.entrySet()) {
                String namespace = groupEntry.getKey();
                if (ModList.get().isLoaded(namespace)) {
                    TaCZWeaponBlueprints.LOGGER.info("!!!Loading " + namespace + " blueprint spawn rates");
                    JsonArray groupList = groupEntry.getValue().getAsJsonArray();
                    for (JsonElement element : groupList) {
                        String recourceLocation = element.getAsString();
                        String tmpPartName = recourceLocation.replace("/", "_").split(":")[1];
                        String modifier = namespace + "/" + tableGroup + "_" + tmpPartName + "_blueprint_spawn_rates";
                        add(modifier,
                            new AddItemsModifier(
                                new LootItemCondition[]{
                                    new LootTableIdCondition.Builder(new ResourceLocation(recourceLocation)).build()
                                },
                                bpItemsWithChances,
                                getMinBlueprintSpawnBound(),
                                getMaxBlueprintSpawnBound(),
                                getBlueprintSpawnChance()
                            )
                        );
                    }
                } else {
                    TaCZWeaponBlueprints.LOGGER.info("Mod not loaded: " + namespace);
                    continue;
                }
            }
        }
    }

    private Integer getMinBlueprintSpawnBound() {
        return ModConfigs.BLUEPRINT.minBlueprints.get();
    }

    private Integer getMaxBlueprintSpawnBound() {
        return ModConfigs.BLUEPRINT.maxBlueprints.get();
    }

    private Float getBlueprintSpawnChance() {
        return ModConfigs.BLUEPRINT.blueprintSpawnChance.get().floatValue();
    }

    private List<BlueprintSpawnRate> getAllBlueprintSpawnRates() {
        TaCZWeaponBlueprints.LOGGER.info("Loading blueprint spawn rates");
        ResourceLocation blueprintSpawnRatesJson = new ResourceLocation(TaCZWeaponBlueprints.MODID, "/data/taczweaponblueprints/gun_rebalancing_data/blueprint_spawn_rates.json");
        
        InputStream stream = ModGlobalLootModifiersProvider.class.getResourceAsStream(blueprintSpawnRatesJson.getPath());
        if (stream == null) {
            TaCZWeaponBlueprints.LOGGER.error("Resource not found: " + blueprintSpawnRatesJson.getPath());
            return new ArrayList<>();
        }
        InputStreamReader reader = new InputStreamReader(stream);
        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
        List<BlueprintSpawnRate> spawnRates = new ArrayList<>();
        // Iterate over each key in JSON object
        // Each keys value is an array of json objects which map to a BlueprintSpawnRate
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            JsonArray array = entry.getValue().getAsJsonArray();
            TaCZWeaponBlueprints.LOGGER.info("Loading " + array.size() + " blueprint spawn rates for " + entry.getKey());
            for (JsonElement element : array) {
                spawnRates.add(BlueprintSpawnRate.fromJson(element.getAsJsonObject()));
            }
        }
        
        if (spawnRates == null || spawnRates.isEmpty()) {
            TaCZWeaponBlueprints.LOGGER.error("Blueprint spawn rates are empty or unable to be loaded");
            return new ArrayList<>();
        } else {
            TaCZWeaponBlueprints.LOGGER.info("Loaded " + spawnRates.size() + " blueprint spawn rates");
        }

        return spawnRates;
    }

    private static List<Pair<ItemStack, Float>> getAllBlueprintItemSpawnChancePairList(JsonArray spawnRates) {

        List<BlueprintSpawnRate> spawnRatesList = new ArrayList<>();
        for (JsonElement element : spawnRates) {
            JsonObject obj = element.getAsJsonObject();

            spawnRatesList.add(BlueprintSpawnRate.fromJson(obj));
        }
        
        List<Pair<ItemStack, Float>> bpItemsWithChances = new ArrayList<>();

        for (BlueprintSpawnRate spawnRate : spawnRatesList) {
            try {

                if (spawnRate == null) {
                    TaCZWeaponBlueprints.LOGGER.error("Blueprint spawn rate is null");
                    continue;
                } else if (ModConfigs.BLUEPRINT.gunBlacklist.contains(spawnRate.id())) {
                    TaCZWeaponBlueprints.LOGGER.info("Skipping blueprint spawn rate: " + spawnRate.id());
                    continue;
                }

                ItemStack bpItem = BlueprintItem.createBlueprint(spawnRate.id());

                // Add NBT data to the ItemStack
                CompoundTag nbtTag = bpItem.getOrCreateTag();
                nbtTag.putString("bpId", spawnRate.id());
                bpItem.setTag(nbtTag);

                bpItemsWithChances.add(Pair.of(bpItem, Math.round(spawnRate.score() * 100 * 1000) / 1000.0f));

            } catch (Exception e) {
                TaCZWeaponBlueprints.LOGGER.error("Failed to add blueprint spawn rate: " + e.getMessage());
            }
        }
        return bpItemsWithChances;

    }
    
}












    // @Override
    // protected void start() {
    //     List<BlueprintSpawnRate> spawnRates = getBlueprintSpawnRates();
    //     List<Pair<ItemStack, Float>> bpItemsWithChances = new ArrayList<>();

    //     for (BlueprintSpawnRate spawnRate : spawnRates) {
    //         try {
    //             ItemStack bpItem = BlueprintItem.createBlueprint(spawnRate.id());

    //             // Add NBT data to the ItemStack
    //             CompoundTag nbtTag = bpItem.getOrCreateTag();
    //             nbtTag.putString("bpId", spawnRate.id());
    //             bpItem.setTag(nbtTag);

    //             bpItemsWithChances.add(Pair.of(bpItem, spawnRate.score()));

    //         } catch (Exception e) {
    //             TaCZWeaponBlueprints.LOGGER.error("Failed to add blueprint spawn rate: " + e.getMessage());
    //         }
    //     }

    //     add("blueprint_spawn_rates",
    //         new AddItemsModifier(
    //             new LootItemCondition[]{
    //                 new LootTableIdCondition.Builder(new ResourceLocation("chests/village/village_armorer")).build()
    //             },
    //             bpItemsWithChances
    //         )
    //     );
    // }