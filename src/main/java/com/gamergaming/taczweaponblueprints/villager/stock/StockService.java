package com.gamergaming.taczweaponblueprints.villager.stock;

import java.util.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModVillagers;
import com.gamergaming.taczweaponblueprints.villager.*;
import com.gamergaming.taczweaponblueprints.villager.trade.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.core.registries.BuiltInRegistries;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.*;

/** Native factories own core selection; this layer only supplies and validates our marked offers. */
public final class StockService {
    private StockService(){}
    public static boolean owns(Villager villager){return !villager.level().isClientSide&&ModVillagers.owns(villager.getVillagerData().getProfession());}
    public static Profession profession(Villager villager){return villager.getVillagerData().getProfession()==ModVillagers.GUNSMITH.get()?Profession.GUNSMITH:Profession.BALLISTICS_RESEARCHER;}
    public static StockState prepare(Villager villager,MerchantOffers offers){
        var bridge=(StockVillagerBridge)villager;var state=bridge.taczweaponblueprints$stock();
        if(state==null){state=((StockOffersBridge)offers).taczweaponblueprints$stock();
            if(state==null){
                state=new StockState(profession(villager),villager.getRandom().nextLong());
                // A detached saved offer cannot authorize a new randomized career plan.
                state.invalid=offers.stream().anyMatch(o->((OwnedVillagerOffer)o).taczweaponblueprints$binding()!=null);
            }
            bridge.taczweaponblueprints$stock(state);}
        ((StockOffersBridge)offers).taczweaponblueprints$stock(state);
        if(state.profession!=profession(villager))state.invalid=true;
        var catalog=StockCatalog.current();
        if(!catalog.coherent())return state;
        if(!StockPublicationCoordinator.pending()&&!state.invalid&&(state.plan==null||!state.plan.reachable()&&state.issued.isEmpty())&&state.checkedPublication!=catalog){
            var result=StockPlanner.create(state.profession,catalog.alternatives,catalog,state.seed);
            if(!result.pending()){state.plan=result.plan();catalog.reportUnreachable(state.plan);}
            state.checkedPublication=catalog;
        }
        return state;
    }
    public static MerchantOffer factory(Villager villager,int level,int index){
        var offers=((StockVillagerBridge)villager).taczweaponblueprints$rawOffers();
        if(!owns(villager)||offers==null)return null;
        var state=prepare(villager,offers);state.requested|=1<<((level-1)*2+index);
        if(StockPublicationCoordinator.pending()||state.invalid||state.plan==null||!state.plan.reachable())return null;
        var entries=state.plan.core(level);if(index>=entries.size())return null;
        var entry=entries.get(index);if(state.issued.contains(entry.slot()))return null;
        var catalog=StockCatalog.current();if(!catalog.coherent())return null;
        var offer=create(entry,catalog);
        if(offer!=null)state.issued.add(entry.slot());return offer;
    }
    public static void refresh(Villager villager,MerchantOffers offers,boolean restock){
        if(StockPublicationCoordinator.pending()||!owns(villager)||((StockVillagerBridge)villager).taczweaponblueprints$generating())return;
        var state=prepare(villager,offers);var catalog=StockCatalog.current();
        if(!catalog.coherent())return;
        if(offers.size()>TradeLimits.OWNED_OFFERS||state.invalid){suspendAll(offers);return;}
        if(state.plan==null)return;
        boolean changed=state.checkedPublication!=catalog;
        if(changed)refreshCurrencyOutputs(state,offers,catalog,villager.registryAccess());
        if(state.attemptedPublication!=catalog){
            state.attemptedPublication=catalog;state.attemptedFactories=0;state.attemptedSupport.clear();
        }
        // A factory called while catalogs were pending gets exactly one retry for its reserved slot.
        if(state.plan.reachable())for(int level=1;level<=villager.getVillagerData().getLevel();level++)for(int index=0;index<2;index++){
            int bit=1<<((level-1)*2+index);
            if((state.requested&bit)==0||(state.attemptedFactories&bit)!=0)continue;
            state.attemptedFactories|=bit;
            var core=state.plan.core(level);if(index>=core.size())continue;
            var entry=core.get(index);if(state.issued.contains(entry.slot())||offers.size()>=TradeLimits.OWNED_OFFERS)continue;
            var offer=create(entry,catalog);if(offer!=null){offers.add(offer);state.issued.add(entry.slot());}
        }
        Map<ResourceLocation,MerchantOffer> owned=owned(offers);
        // Only append support for guns vanilla actually selected, once per saved family identity.
        for(var entry:state.plan.entries().values()){
            if(entry.kind()!=Kind.GUN||!owned.containsKey(entry.slot()))continue;
            for(var dependency:entry.dependencies()){
                var support=state.plan.entries().get(dependency);
                if(support.role()!=Role.SUPPORT||state.issued.contains(dependency)||offers.size()>=TradeLimits.OWNED_OFFERS)continue;
                if(!state.attemptedSupport.add(dependency))continue;
                var offer=create(support,catalog);if(offer!=null){offers.add(offer);state.issued.add(dependency);owned.put(dependency,offer);}
            }
        }
        if(changed||restock){
            for(int i=0;i<offers.size();i++){
                var offer=offers.get(i);var binding=((OwnedVillagerOffer)offer).taczweaponblueprints$binding();if(binding==null)continue;
                var row=catalog.row(binding.entry());
                if(row!=null&&binding.entry().equals(state.plan.entries().get(binding.entry().slot())))
                    offers.set(i,reprice(offer,row,restock,villager.registryAccess()));
            }
            state.checkedPublication=catalog;owned=owned(offers);
        }
        // First base validity, then supply dependencies; temporary sold-out stock is not missing content.
        Map<ResourceLocation,Boolean> available=new HashMap<>();
        for(var e:owned.entrySet()){
            var binding=((OwnedVillagerOffer)e.getValue()).taczweaponblueprints$binding();var entry=binding.entry();
            boolean valid=entry.equals(state.plan.entries().get(entry.slot()))&&catalog.row(entry)!=null&&entry.level()<=villager.getVillagerData().getLevel();
            if(valid)valid=catalog.row(entry).profession()==state.profession && ItemStack.matches(e.getValue().getResult(),catalog.output(entry));
            available.put(e.getKey(),valid);
        }
        for(var entry:state.plan.entries().values()){
            var offer=owned.get(entry.slot());if(offer==null)continue;
            boolean valid=available.getOrDefault(entry.slot(),false);
            if(entry.kind()==Kind.GUN){
                var magazine=catalog.magazine(entry.target());
                valid&=magazine.status()!=StockPlanner.SupplyStatus.UNAVAILABLE&&magazine.status()!=StockPlanner.SupplyStatus.PENDING;
                if(magazine.status()==StockPlanner.SupplyStatus.READY)valid&=entry.dependencies().stream().map(state.plan.entries()::get)
                        .anyMatch(d->d.kind()==Kind.MAGAZINE&&magazine.family().orElseThrow().equals(d.target()));
                for(var dependency:entry.dependencies())valid&=available.getOrDefault(dependency,false);
                valid&=entry.dependencies().stream().map(state.plan.entries()::get).filter(d->d.kind()==Kind.AMMO)
                        .anyMatch(d->catalog.ammo(entry.target()).filter(d.target()::equals).isPresent());
            }
            available.put(entry.slot(),valid);
        }
        // Guns must be fully validated before dependent attachment rows, regardless of slot ordering.
        for(var entry:state.plan.entries().values()){
            var offer=owned.get(entry.slot());if(offer==null)continue;
            boolean valid=available.getOrDefault(entry.slot(),false);
            if(entry.kind()==Kind.ATTACHMENT)valid&=state.plan.entries().values().stream()
                    .anyMatch(g->g.kind()==Kind.GUN&&available.getOrDefault(g.slot(),false)&&catalog.attachmentFits(g.target(),entry.target()));
            availability(offer,valid);
        }
        if(!state.interferenceLogged){
            var factories=VillagerTrades.TRADES.get(villager.getVillagerData().getProfession());
            for(int level=1;level<=5;level++){
                var listings=factories==null?null:factories.get(level);
                if(listings==null||listings.length!=2||Arrays.stream(listings).filter(l->l instanceof StockTradeEvents.Factory).count()!=2){
                    state.interferenceLogged=true;TaCZWeaponBlueprints.LOGGER.warn("Villager {} has modified {} trade factories; preserving native selection and suspending owned guns with missing supplies",villager.getUUID(),state.profession);break;
                }
            }
        }
    }
    /** Currency is a configured denomination, not a randomized merchandise choice. */
    private static void refreshCurrencyOutputs(StockState state,MerchantOffers offers,StockCatalog catalog,net.minecraft.core.HolderLookup.Provider registries){
        var entries=new TreeMap<ResourceLocation,StockPlan.Entry>(state.plan.entries());boolean changed=false;
        for(var old:state.plan.entries().values()){
            if(old.kind()!=Kind.CURRENCY)continue;
            var row=catalog.alternatives.getOrDefault(old.template(),List.of()).stream().filter(r->r.ready()
                    &&r.definition().equals(old.definition())&&r.profession()==state.profession&&r.role()==old.role()
                    &&r.level()==old.level()&&r.output().kind()==Kind.CURRENCY&&r.output().count()==old.count()).findFirst().orElse(null);
            if(row==null)continue;var target=row.candidates().get(0).id();if(target.equals(old.target()))continue;
            var next=new StockPlan.Entry(old.slot(),old.template(),old.definition(),target,old.kind(),old.count(),old.level(),old.role(),old.selection(),old.dependencies());
            entries.put(next.slot(),next);changed=true;
            for(int n=0;n<offers.size();n++){
                var offer=offers.get(n);var binding=((OwnedVillagerOffer)offer).taczweaponblueprints$binding();
                if(binding==null||!binding.entry().equals(old))continue;
                var previousItem=BuiltInRegistries.ITEM.get(old.target());
                if(previousItem==null||!ItemStack.matches(offer.getResult(),new ItemStack(previousItem,old.count())))continue;
                var tag=OwnedOfferSerialization.save(offer,registries);tag.put("sell",catalog.output(next).saveOptional(registries));
                tag.put(StockOfferBinding.TAG,new StockOfferBinding(next,binding.primary(),binding.protectedSecondary()).save());
                var terms=((OwnedVillagerOffer)offer).taczweaponblueprints$terms();
                tag.put(VillagerOfferTerms.TAG,new VillagerOfferTerms(terms.slot(),terms.minimumPrimary(),terms.available(),Math.incrementExact(terms.revision())).save());
                offers.set(n,OwnedOfferSerialization.read(tag,registries));
            }
        }
        if(changed)state.plan=new StockPlan(state.profession,entries,state.plan.excluded(),state.plan.reachable());
    }
    private static MerchantOffer create(StockPlan.Entry entry,StockCatalog catalog){
        var row=catalog.row(entry);if(row==null)return null;var output=catalog.output(entry);if(output.isEmpty())return null;
        var price=row.price().orElseThrow();var counts=price.amounts().charged();
        var a=new ItemStack(BuiltInRegistries.ITEM.get(price.primaryItem()),counts.primary());
        var b=counts.secondary()==0?ItemStack.EMPTY:new ItemStack(BuiltInRegistries.ITEM.get(price.secondaryItem().orElseThrow()),counts.secondary());
        var offer=new MerchantOffer(OwnedOfferSerialization.cost(a),b.isEmpty()?Optional.empty():Optional.of(OwnedOfferSerialization.cost(b)),output,row.uses(),row.xp(),row.demandMultiplier());var owned=(OwnedVillagerOffer)offer;
        owned.taczweaponblueprints$terms(new VillagerOfferTerms(entry.slot(),counts.minimumPrimary(),true,0));
        owned.taczweaponblueprints$binding(new StockOfferBinding(entry,price.amounts().originalPrimary(),price.amounts().protectedSecondary()));return offer;
    }
    private static MerchantOffer reprice(MerchantOffer old,TradeResolver.Row row,boolean restock,net.minecraft.core.HolderLookup.Provider registries){
        var binding=((OwnedVillagerOffer)old).taczweaponblueprints$binding();var fresh=create(binding.entry(),StockCatalog.current());if(fresh==null)return old;
        int capacity=restock?row.uses():Math.min(old.getMaxUses(),row.uses());var oldTerms=((OwnedVillagerOffer)old).taczweaponblueprints$terms();var freshTerms=((OwnedVillagerOffer)fresh).taczweaponblueprints$terms();
        if(ItemStack.matches(old.getBaseCostA(),fresh.getBaseCostA())&&ItemStack.matches(old.getCostB(),fresh.getCostB())
                &&old.getMaxUses()==capacity&&old.getXp()==row.xp()&&old.getPriceMultiplier()==row.demandMultiplier()
                &&oldTerms.minimumPrimary()==freshTerms.minimumPrimary()
                &&binding.primary()==((OwnedVillagerOffer)fresh).taczweaponblueprints$binding().primary()
                &&binding.protectedSecondary()==((OwnedVillagerOffer)fresh).taczweaponblueprints$binding().protectedSecondary())return old;
        var tag=OwnedOfferSerialization.save(old,registries);var freshTag=OwnedOfferSerialization.save(fresh,registries);tag.put("buy",freshTag.get("buy"));if(freshTag.contains("buyB"))tag.put("buyB",freshTag.get("buyB"));else tag.remove("buyB");
        tag.putInt("maxUses",capacity);tag.putInt("xp",row.xp());tag.putFloat("priceMultiplier",row.demandMultiplier());
        tag.put(VillagerOfferTerms.TAG,new VillagerOfferTerms(oldTerms.slot(),freshTerms.minimumPrimary(),oldTerms.available(),Math.incrementExact(oldTerms.revision())).save());
        tag.put(StockOfferBinding.TAG,((OwnedVillagerOffer)fresh).taczweaponblueprints$binding().save());return OwnedOfferSerialization.read(tag,registries);
    }
    private static Map<ResourceLocation,MerchantOffer> owned(MerchantOffers offers){
        var result=new HashMap<ResourceLocation,MerchantOffer>();var duplicate=new HashSet<ResourceLocation>();
        for(var offer:offers){var binding=((OwnedVillagerOffer)offer).taczweaponblueprints$binding();if(binding==null)continue;
            if(result.putIfAbsent(binding.entry().slot(),offer)!=null)duplicate.add(binding.entry().slot());}
        for(var offer:offers){var binding=((OwnedVillagerOffer)offer).taczweaponblueprints$binding();if(binding!=null&&duplicate.contains(binding.entry().slot()))availability(offer,false);}
        duplicate.forEach(result::remove);return result;
    }
    private static void availability(MerchantOffer offer,boolean available){var owner=(OwnedVillagerOffer)offer;var terms=owner.taczweaponblueprints$terms();
        if(terms!=null&&terms.available()!=available)owner.taczweaponblueprints$terms(new VillagerOfferTerms(terms.slot(),terms.minimumPrimary(),available,Math.incrementExact(terms.revision())));}
    private static void suspendAll(MerchantOffers offers){for(var offer:offers)if(((OwnedVillagerOffer)offer).taczweaponblueprints$binding()!=null)availability(offer,false);}
}
