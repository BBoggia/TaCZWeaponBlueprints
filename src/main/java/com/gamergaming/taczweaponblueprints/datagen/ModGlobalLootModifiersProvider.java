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
import com.gamergaming.taczweaponblueprints.loot.ResearchDataLootModifier;
import com.gamergaming.taczweaponblueprints.init.ModItems;
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

    private static final List<ResourceLocation> NOTE_LOOT_TABLES = List.of(
            new ResourceLocation("minecraft:chests/abandoned_mineshaft"),
            new ResourceLocation("minecraft:chests/simple_dungeon"),
            new ResourceLocation("minecraft:chests/pillager_outpost"));
    private static final List<ResourceLocation> REPORT_LOOT_TABLES = List.of(
            new ResourceLocation("minecraft:chests/stronghold_library"),
            new ResourceLocation("minecraft:chests/woodland_mansion"),
            new ResourceLocation("minecraft:chests/bastion_treasure"));
    private static final List<ResourceLocation> DOSSIER_LOOT_TABLES = List.of(
            new ResourceLocation("minecraft:chests/ancient_city"),
            new ResourceLocation("minecraft:chests/end_city_treasure"));

    public ModGlobalLootModifiersProvider(PackOutput output) {
        super(output, TaCZWeaponBlueprints.MODID);
        TaCZWeaponBlueprints.LOGGER.info("Creating global loot modifiers");
    }

    @Override
    protected void start() {
        add("dynamic_blueprints", new DynamicBlueprintLootModifier(new LootItemCondition[0]));

        addResearchData("note", ModItems.RESEARCH_NOTE.get().getDefaultInstance(), 0.12f, NOTE_LOOT_TABLES);
        addResearchData("report", ModItems.RESEARCH_REPORT.get().getDefaultInstance(), 0.08f, REPORT_LOOT_TABLES);
        addResearchData("dossier", ModItems.RESEARCH_DOSSIER.get().getDefaultInstance(), 0.05f, DOSSIER_LOOT_TABLES);

        Map<String, JsonObject> lootTableLists = LootTableResourceLoader.getAllLootTableLists();
        Map<String, JsonArray> spawnRates = LootTableResourceLoader.getAllLootTableSpawnRates();
        int generatedModifiers = 0;

        for (Map.Entry<String, JsonObject> tierEntry : lootTableLists.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
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

            for (Map.Entry<String, JsonElement> namespaceEntry : tierEntry.getValue().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .toList()) {
                String namespace = namespaceEntry.getKey();
                if (!ModList.get().isLoaded(namespace)) {
                    TaCZWeaponBlueprints.LOGGER.debug(
                            "Skipping {} blueprint loot tables because mod {} is not loaded during data generation",
                            tier,
                            namespace);
                    continue;
                }

                List<String> sortedLootTables = new ArrayList<>();
                namespaceEntry.getValue().getAsJsonArray().forEach(
                        element -> sortedLootTables.add(element.getAsString()));
                sortedLootTables.sort(String::compareTo);
                for (String lootTableValue : sortedLootTables) {
                    ResourceLocation lootTableId = ResourceLocation.tryParse(lootTableValue);
                    if (lootTableId == null || !namespace.equals(lootTableId.getNamespace())) {
                        throw new IllegalStateException(
                                "Invalid or incorrectly grouped loot table ID in tier " + tier + ": "
                                        + lootTableValue);
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
                "Generated 1 dynamic, {} Research Data, and {} legacy blueprint global loot modifiers",
                NOTE_LOOT_TABLES.size() + REPORT_LOOT_TABLES.size() + DOSSIER_LOOT_TABLES.size(),
                generatedModifiers);
    }

    private void addResearchData(
            String name,
            ItemStack item,
            float chance,
            List<ResourceLocation> lootTables) {
        for (ResourceLocation lootTable : lootTables) {
            add(
                    "research_data/" + name + "_" + lootTable.getPath().replace('/', '_'),
                    new ResearchDataLootModifier(
                            new LootItemCondition[]{
                                new LootTableIdCondition.Builder(lootTable).build()
                            },
                            item,
                            chance));
        }
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
