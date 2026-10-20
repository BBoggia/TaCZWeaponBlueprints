package com.gamergaming.taczweaponblueprints.villagerqa;

import java.nio.file.Files;
import java.nio.file.Path;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModItems;
import com.gamergaming.taczweaponblueprints.init.ModVillagers;
import com.gamergaming.taczweaponblueprints.villager.VillagerTradeOffers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Controlled examples only; this source set is absent from the distributable mod. */
@EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID)
public final class VillagerPrototypeStock {
    public static Path root() {
        String value = System.getProperty("tacz.villagerQA.root", "");
        if (value.isEmpty()) return null;
        Path root = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve(".disposable-villager-qa"))) throw new IllegalStateException("Missing disposable villager QA marker");
        return root;
    }
    public static MerchantOffer offer(String slot, ItemStack a, ItemStack b, ItemStack output, int uses, int xp) {
        return VillagerTradeOffers.create(ResourceLocation.fromNamespaceAndPath(TaCZWeaponBlueprints.MODID, "prototype/" + slot), a, b, output, uses, xp, .05F);
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void trades(VillagerTradesEvent event) {
        if (root() == null || !ModVillagers.owns(event.getType()) || Boolean.getBoolean("tacz.villagerQA.trades") || (Boolean.getBoolean("tacz.villagerQA.stock") || Boolean.getBoolean("tacz.villagerQA.economy") || Boolean.getBoolean("tacz.villagerQA.lifecycle") || !System.getProperty("tacz.villagerQA.persistence", "").isEmpty())) return;
        event.getTrades().values().forEach(rows -> rows.removeIf(row -> row instanceof com.gamergaming.taczweaponblueprints.villager.stock.StockTradeEvents.Factory));
        var novice = event.getTrades().get(1);
        novice.add((entity, random) -> offer("material", new ItemStack(Items.IRON_INGOT, 8), ItemStack.EMPTY, new ItemStack(ModItems.SCRAP.get()), 4, 2));
        novice.add((entity, random) -> offer("note", new ItemStack(ModItems.SCRAP.get(), 4), ItemStack.EMPTY, new ItemStack(ModItems.RESEARCH_NOTE.get()), 4, 1));
    }
}
