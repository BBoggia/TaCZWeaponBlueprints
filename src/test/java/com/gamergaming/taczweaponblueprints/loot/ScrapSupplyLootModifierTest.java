package com.gamergaming.taczweaponblueprints.loot;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;
class ScrapSupplyLootModifierTest {
    @BeforeAll static void init(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test void budgetsIncludeExistingCurrencyAndBoundInjectedQuantity(){
        var unit=new ItemStack(Items.IRON_INGOT);
        assertEquals(12,ScrapSupplyLootModifier.remaining(List.of(new ItemStack(Items.IRON_INGOT,20)),unit,32));
        assertEquals(0,ScrapSupplyLootModifier.remaining(List.of(new ItemStack(Items.IRON_INGOT,64)),unit,32));
        assertEquals(0,ScrapSupplyLootModifier.remaining(List.of(),unit,0));
        assertEquals(64,ScrapSupplyLootModifier.remaining(List.of(),unit,1000));
    }
    @Test void invalidRowsCannotProduceUnboundedOrNonfiniteRewards(){
        for(float p:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY,1.1f})assertThrows(IllegalArgumentException.class,()->new ScrapSupplyLootModifier.Row(p,1,2,false));
        assertThrows(IllegalArgumentException.class,()->new ScrapSupplyLootModifier.Row(1,3,2,false));
        assertThrows(IllegalArgumentException.class,()->new ScrapSupplyLootModifier.Row(1,1,65,false));
        var rows=ScrapSupplyLootModifier.defaults();assertEquals(16,rows.size());assertTrue(rows.entrySet().stream().filter(e->e.getKey().getPath().startsWith("entities/")).allMatch(e->e.getValue().playerKill()));
    }
}
