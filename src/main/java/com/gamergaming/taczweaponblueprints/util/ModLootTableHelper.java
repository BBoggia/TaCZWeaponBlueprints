package com.gamergaming.taczweaponblueprints.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.init.ModLootTables;

import net.minecraft.resources.ResourceLocation;

public class ModLootTableHelper {

    // public static Map<String, Map<String, String>> getAllLootTableLists() {
    //     List<ResourceLocation> lootTableLists = getResourceLocationsFromClassFields(ModLootTables.BlueprintLootTableLists.class);
    //     Map<String, Map<String, String>> lootTableMap = new HashMap<>();
    //     for (ResourceLocation lootTableList : lootTableLists) {

    //         String[] pathParts = lootTableList.getPath().split("/");
    //         String lootTableListName = pathParts[pathParts.length - 1];
    //         lootTableMap.put(lootTableListName, new HashMap<>());
            
    //     }
    // }

    public static List<ResourceLocation> getResourceLocationsFromClassFields(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        List<ResourceLocation> resourceLocations = new ArrayList<>();

        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType().equals(ResourceLocation.class)) {
                try {
                    ResourceLocation resourceLocation = (ResourceLocation) field.get(null);
                    resourceLocations.add(resourceLocation);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        return resourceLocations;
    }

    public static List<ResourceLocation> getAllLootTables() {
        List<ResourceLocation> lootTables = new ArrayList<>();
        Field[] fields = ModLootTables.BlueprintLootTableLists.class.getDeclaredFields();

        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType().equals(ResourceLocation.class)) {
                try {
                    ResourceLocation lootTable = (ResourceLocation) field.get(null);
                    lootTables.add(lootTable);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        return lootTables;
    }

    public static List<ResourceLocation> getLootTablesFromNamespace(String namespace) {
        List<ResourceLocation> lootTables = new ArrayList<>();
        Field[] fields = ModLootTables.class.getDeclaredFields();

        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType().equals(ResourceLocation.class)) {
                try {
                    ResourceLocation lootTable = (ResourceLocation) field.get(null);
                    if (lootTable.getNamespace().equals(namespace)) {
                        lootTables.add(lootTable);
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        return lootTables;
    }
}
