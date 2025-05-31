package com.gamergaming.taczweaponblueprints.datagen;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

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

        try {
            for (Map.Entry<String, JsonObject> entry : lootTableLists.entrySet()) {
                String tableGroup = entry.getKey(); // easy, medium, hard, nether, village, water
                JsonObject groupListSet = entry.getValue();

                JsonArray currentTierSpawnRates = spawnRates.get(tableGroup);
                if (currentTierSpawnRates == null) {
                    TaCZWeaponBlueprints.LOGGER.warn("No spawn rates found for tier: {}", tableGroup);
                    continue; // Skips tier if no spawn rates defined
                }

//            List<Pair<ItemStack, Float>> bpItemsWithChances = getAllBlueprintItemSpawnChancePairList(spawnRates.get(tableGroup));
                List<Pair<ItemStack, Float>> bpItemsWithChances;

                try {
                    bpItemsWithChances = getAllBlueprintItemSpawnChancePairList(currentTierSpawnRates);
                } catch (Exception e) {
                    TaCZWeaponBlueprints.LOGGER.error("Failed to load spawn rates for tier: " + tableGroup + " - " + e.getMessage());
                    continue;
                }

                if (bpItemsWithChances.isEmpty()) {
                    TaCZWeaponBlueprints.LOGGER.warn("No valid/non-blacklisted blueprint items generated for tier: {}", tableGroup);
                }

                try {
                    for (Map.Entry<String, JsonElement> groupEntry : groupListSet.entrySet()) {
                        String namespace = groupEntry.getKey();
                        if (ModList.get().isLoaded(namespace)) {
                            TaCZWeaponBlueprints.LOGGER.info("!!!Loading " + namespace + " blueprint spawn rates");
                            JsonArray groupList = groupEntry.getValue().getAsJsonArray();
                            for (JsonElement element : groupList) {
                                try {
                                    String recourceLocation = element.getAsString();
                                    String tmpPartName;
                                    try {
                                        tmpPartName = recourceLocation.replace("/", "_").split(":")[1];
                                        TaCZWeaponBlueprints.LOGGER.info("Loading recource location!!!!: " + tmpPartName);
                                    } catch (Exception e) {
                                        TaCZWeaponBlueprints.LOGGER.error("Failed to parse resource location: " + recourceLocation + " - " + e.getMessage());
                                        continue;
                                    }
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
                                } catch (Exception e) {
                                    TaCZWeaponBlueprints.LOGGER.error("Failed to load loot table list: " + e.getMessage());
                                    continue;
                                }
                            }
                        } else {
                            TaCZWeaponBlueprints.LOGGER.info("Mod not loaded: " + namespace);
                            continue;
                        }
                    }
                } catch (Exception e) {
                    TaCZWeaponBlueprints.LOGGER.error("Failed to load loot table list inner loop: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            TaCZWeaponBlueprints.LOGGER.error("Failed to load loot table lists: " + e.getMessage());
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

    TaCZWeaponBlueprints.LOGGER.info("Processing " + spawnRatesList.size() + " blueprint spawn rates for data generation");

    for (BlueprintSpawnRate spawnRate : spawnRatesList) {
        try {
            if (spawnRate == null) {
                TaCZWeaponBlueprints.LOGGER.error("Blueprint spawn rate object is null.");
                continue;
            }

            String fullBlueprintId = spawnRate.id();
            if (fullBlueprintId == null || fullBlueprintId.isEmpty()) {
                TaCZWeaponBlueprints.LOGGER.warn("Blueprint ID is null or empty in spawn rate data.");
                continue;
            }

            if (ModConfigs.BLUEPRINT.gunBlacklist.contains(fullBlueprintId)) {
                TaCZWeaponBlueprints.LOGGER.info("Skipping blueprint spawn rate for " + fullBlueprintId + " due to blacklist for data generation.");
                continue;
            }
            
            ItemStack bpItem = BlueprintItem.createBlueprint(fullBlueprintId);

            CompoundTag nbtTag = bpItem.getOrCreateTag();
            nbtTag.putString("bpId", fullBlueprintId);
            bpItem.setTag(nbtTag);

            bpItemsWithChances.add(Pair.of(bpItem, Math.round(spawnRate.score() * 100 * 1000) / 1000.0f));

        } catch (Exception e) {
            TaCZWeaponBlueprints.LOGGER.error("Failed to process blueprint spawn rate for ID '" + (spawnRate != null && spawnRate.id() != null ? spawnRate.id() : "unknown") + "' during data generation: " + e.getMessage(), e);
        }
    }
    return bpItemsWithChances;
}
    
}
