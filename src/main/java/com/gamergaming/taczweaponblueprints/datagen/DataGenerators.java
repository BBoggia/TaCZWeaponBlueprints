package com.gamergaming.taczweaponblueprints.datagen;

import java.util.concurrent.CompletableFuture;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, bus = EventBusSubscriber.Bus.MOD)
public class DataGenerators {
    
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output =generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();
        
        if (event.includeServer()) {
            generator.addProvider(event.includeServer(), new ModGlobalLootModifiersProvider(output));
        }
    }
}
