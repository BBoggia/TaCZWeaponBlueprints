package com.gamergaming.taczweaponblueprints.villager.stock;

import java.util.*;
import com.gamergaming.taczweaponblueprints.villager.trade.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.*;

/** Mutable server-owned lifecycle, carried once by the native MerchantOffers compound through curing. */
public final class StockState {
    public static final String TAG = "taczweaponblueprints:stock";
    public final Profession profession;
    public final long seed;
    public StockPlan plan;
    public int requested;
    public final Set<ResourceLocation> issued = new HashSet<>();
    public boolean invalid;
    public transient Object checkedPublication;
    public transient boolean interferenceLogged;
    public transient Object attemptedPublication;
    public transient int attemptedFactories;
    public transient Set<ResourceLocation> attemptedSupport = new HashSet<>();
    public StockState(Profession profession,long seed){this.profession=Objects.requireNonNull(profession);this.seed=seed;}
    public CompoundTag save(){
        var tag=new CompoundTag();tag.putInt("version",1);tag.putString("profession",profession.name());tag.putLong("seed",seed);
        tag.putInt("requested",requested);tag.putBoolean("invalid",invalid);var minted=new ListTag();
        issued.stream().sorted().forEach(id->minted.add(StringTag.valueOf(id.toString())));tag.put("issued",minted);
        if(plan!=null){var entries=new ListTag();plan.entries().values().forEach(e->entries.add(saveEntry(e)));tag.put("entries",entries);
            tag.putBoolean("reachable",plan.reachable());var excluded=new CompoundTag();plan.excluded().forEach((id,reason)->excluded.putString(id.toString(),reason));tag.put("excluded",excluded);}
        return tag;
    }
    public static StockState read(CompoundTag tag){
        if(!tag.contains("version",Tag.TAG_INT) || tag.getInt("version")!=1 || !tag.contains("seed",Tag.TAG_LONG) || !tag.contains("requested",Tag.TAG_INT)
                || !tag.contains("issued",Tag.TAG_LIST) || !tag.contains("invalid",Tag.TAG_BYTE))throw new IllegalArgumentException("Invalid stock state version or fields");
        var state=new StockState(Profession.valueOf(tag.getString("profession")),tag.getLong("seed"));
        state.requested=tag.getInt("requested");state.invalid=tag.getBoolean("invalid");
        if(state.requested<0 || state.requested>1023)throw new IllegalArgumentException("Invalid factory request mask");
        var issued=list(tag,"issued",Tag.TAG_STRING);if(issued.size()>StockPlan.MAX_ENTRIES)throw new IllegalArgumentException("Too many issued slots");
        for(int i=0;i<issued.size();i++)if(!state.issued.add(TradeDefinition.parseId(issued.getString(i))))throw new IllegalArgumentException("Duplicate issued slot");
        if(tag.contains("entries")){
            if(!tag.contains("entries",Tag.TAG_LIST)||!tag.contains("reachable",Tag.TAG_BYTE))throw new IllegalArgumentException("Invalid saved plan");
            var list=list(tag,"entries",Tag.TAG_COMPOUND);if(list.size()>StockPlan.MAX_ENTRIES)throw new IllegalArgumentException("Too many stock entries");
            Map<ResourceLocation,StockPlan.Entry> entries=new TreeMap<>();
            for(int i=0;i<list.size();i++){var entry=readEntry(list.getCompound(i));if(entries.put(entry.slot(),entry)!=null)throw new IllegalArgumentException("Duplicate stock slot");}
            if(!tag.contains("excluded",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Missing exclusions");
            var excluded=new TreeMap<ResourceLocation,String>();var reasons=tag.getCompound("excluded");
            if(reasons.size()>StockPlan.MAX_ENTRIES)throw new IllegalArgumentException("Too many excluded slots");
            for(var id:reasons.getAllKeys()){if(!reasons.contains(id,Tag.TAG_STRING))throw new IllegalArgumentException("Invalid exclusion reason");excluded.put(TradeDefinition.parseId(id),reasons.getString(id));}
            state.plan=new StockPlan(state.profession,entries,excluded,tag.getBoolean("reachable"));
            if(!entries.keySet().containsAll(state.issued))throw new IllegalArgumentException("Issued slot missing from plan");
        }else if(!state.issued.isEmpty())throw new IllegalArgumentException("Issued stock requires a plan");
        return state;
    }
    public static CompoundTag saveEntry(StockPlan.Entry entry){
        var tag=new CompoundTag();tag.putString("slot",entry.slot().toString());tag.putString("template",entry.template().toString());
        tag.putString("definition",entry.definition().toString());tag.putString("target",entry.target().toString());
        tag.putString("kind",entry.kind().name());tag.putInt("count",entry.count());tag.putInt("level",entry.level());
        tag.putString("role",entry.role().name());tag.putString("selection",entry.selection().name());var deps=new ListTag();
        entry.dependencies().forEach(id->deps.add(StringTag.valueOf(id.toString())));tag.put("dependencies",deps);return tag;
    }
    public static StockPlan.Entry readEntry(CompoundTag tag){
        if(!tag.contains("count",Tag.TAG_INT)||!tag.contains("level",Tag.TAG_INT))throw new IllegalArgumentException("Invalid stock count/level");
        var deps=list(tag,"dependencies",Tag.TAG_STRING);if(deps.size()>4)throw new IllegalArgumentException("Too many stock dependencies");
        List<ResourceLocation> dependencies=new ArrayList<>();for(int i=0;i<deps.size();i++)dependencies.add(TradeDefinition.parseId(deps.getString(i)));
        return new StockPlan.Entry(id(tag,"slot"),id(tag,"template"),id(tag,"definition"),id(tag,"target"),Kind.valueOf(tag.getString("kind")),
                tag.getInt("count"),tag.getInt("level"),Role.valueOf(tag.getString("role")),Selection.valueOf(tag.getString("selection")),dependencies);
    }
    private static ListTag list(CompoundTag tag,String name,int type){
        if(!(tag.get(name) instanceof ListTag list) || (!list.isEmpty() && list.getElementType()!=type))
            throw new IllegalArgumentException("Invalid stock list: "+name);
        return list;
    }
    private static ResourceLocation id(CompoundTag tag,String name){return TradeDefinition.parseId(tag.getString(name));}
}
