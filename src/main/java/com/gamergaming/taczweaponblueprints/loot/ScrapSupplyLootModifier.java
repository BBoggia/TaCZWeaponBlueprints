package com.gamergaming.taczweaponblueprints.loot;

import java.util.*;
import java.util.function.Supplier;
import com.google.common.base.Suppliers;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;

/** One table-indexed supply modifier. Existing currency in the roll counts toward its budget. */
public final class ScrapSupplyLootModifier extends LootModifier {
    public record Row(float chance, int minimum, int maximum, boolean playerKill) {
        public static final Codec<Row> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("chance").forGetter(Row::chance),
                Codec.intRange(1,64).fieldOf("minimum").forGetter(Row::minimum),
                Codec.intRange(1,64).fieldOf("maximum").forGetter(Row::maximum),
                Codec.BOOL.optionalFieldOf("player_kill",false).forGetter(Row::playerKill)).apply(i,Row::new));
        public Row { if(!Float.isFinite(chance)||chance<=0||chance>1||minimum<1||maximum<minimum||maximum>64)throw new IllegalArgumentException("invalid Scrap supply row"); }
    }
    public static final Supplier<MapCodec<ScrapSupplyLootModifier>> CODEC=Suppliers.memoize(() -> RecordCodecBuilder.mapCodec(i ->
            codecStart(i).and(Codec.unboundedMap(ResourceLocation.CODEC,Row.CODEC).fieldOf("tables").forGetter(v->v.tables)).apply(i,ScrapSupplyLootModifier::new)));
    private final Map<ResourceLocation,Row> tables;
    public ScrapSupplyLootModifier(LootItemCondition[] conditions,Map<ResourceLocation,Row> tables) {
        super(conditions); if(tables.size()>1024)throw new IllegalArgumentException("too many supply tables");this.tables=Map.copyOf(tables);
    }
    @Override public MapCodec<ScrapSupplyLootModifier> codec(){return CODEC.get();}
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot,LootContext context) {
        var config=ModConfigs.BLUEPRINT;var row=tables.get(context.getQueriedLootTableId());
        if(!config.enableScrapLoot.get()||row==null||row.playerKill()&&!context.hasParam(LootContextParams.LAST_DAMAGE_PLAYER))return loot;
        var unit=config.currencyPolicy().physicalUnit().orElse(ItemStack.EMPTY);
        if(unit.isEmpty())return loot;
        int room=remaining(loot,unit,config.scrapLootBudget.get());
        if(room==0||context.getRandom().nextFloat()>=row.chance())return loot;
        int amount=Math.min(room,row.minimum()+context.getRandom().nextInt(row.maximum()-row.minimum()+1));
        while(amount>0){var stack=unit.copy();int count=Math.min(amount,stack.getMaxStackSize());stack.setCount(count);loot.add(stack);amount-=count;}
        return loot;
    }
    public static int remaining(List<ItemStack> loot,ItemStack unit,int budget) {
        long existing=loot.stream().filter(s->s.is(unit.getItem())).mapToLong(ItemStack::getCount).sum();
        return (int)Math.max(0,Math.min(64,budget-existing));
    }
    public static Map<ResourceLocation,Row> defaults(){
        var tables=new TreeMap<ResourceLocation,Row>();
        for(String id:List.of("chests/village/village_toolsmith","chests/village/village_weaponsmith","chests/abandoned_mineshaft","chests/simple_dungeon","chests/shipwreck_supply","chests/pillager_outpost"))tables.put(ResourceLocation.fromNamespaceAndPath("minecraft",id),new Row(1,8,16,false));
        for(String id:List.of("chests/stronghold_library","chests/woodland_mansion","chests/bastion_treasure","chests/ancient_city","chests/end_city_treasure"))tables.put(ResourceLocation.fromNamespaceAndPath("minecraft",id),new Row(1,16,32,false));
        for(String id:List.of("entities/zombie","entities/husk","entities/skeleton","entities/stray","entities/pillager"))tables.put(ResourceLocation.fromNamespaceAndPath("minecraft",id),new Row(.25f,1,2,true));
        return Map.copyOf(tables);
    }
}
