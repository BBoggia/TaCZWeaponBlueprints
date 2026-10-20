package com.gamergaming.taczweaponblueprints.villagerqa;

import java.nio.file.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.villager.*;
import com.gamergaming.taczweaponblueprints.villager.stock.*;
import com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.Kind;
import net.minecraft.nbt.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.entity.ai.behavior.AssignProfessionFromJobSite;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.util.ObfuscationReflectionHelper;

@EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID)
public final class VillagerPersistenceProbe {
    private static boolean done;
    private static final List<String> checks=new ArrayList<>();
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
        var mode=System.getProperty("tacz.villagerQA.persistence","");if(mode.isEmpty()||done||VillagerPrototypeStock.root()==null)return;
        done=true;var server=event.getServer();var root=Path.of(System.getProperty("tacz.villagerQA.control"));
        try{
            NeoForge.EVENT_BUS.post(new OnDatapackSyncEvent(server.getPlayerList(),null));StockPublicationCoordinator.settle();
            var level=server.overworld();var v=new Villager(EntityType.VILLAGER,level);v.moveTo(.5,85,.5);v.setNoAi(true);v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.GUNSMITH.get()));
            var file=root.resolve("persistent-villager.nbt");
            if(mode.equals("create")){
                level.addFreshEntity(v);v.getOffers();for(int i=2;i<=5;i++)ObfuscationReflectionHelper.findMethod(Villager.class,"increaseMerchantCareer").invoke(v);
                require(v.getOffers().stream().anyMatch(o->entry(o).kind()==Kind.MAGAZINE),"Reference villager owns magazine stock");
                v.getOffers().forEach(o->o.increaseUses());v.setVillagerXp(37);var tag=new CompoundTag();v.saveWithoutId(tag);NbtIo.writeCompressed(tag,file);
                Files.writeString(root.resolve("persistent-plan.txt"),((StockVillagerBridge)v).taczweaponblueprints$stock().plan.toString());
                checkLiveLifecycle(level,v);
            }else{
                var tag=NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap());v.load(tag);level.addFreshEntity(v);var offers=v.getOffers();
                require(((StockVillagerBridge)v).taczweaponblueprints$stock().plan.toString().equals(Files.readString(root.resolve("persistent-plan.txt"))),"Restart preserves every selected identity and dependency");
                require(v.getVillagerXp()==37&&offers.stream().allMatch(o->o.getUses()==1),"Restart preserves XP and spent capacity");
                var magazines=offers.stream().filter(o->entry(o).kind()==Kind.MAGAZINE).toList();require(!magazines.isEmpty(),"Removed addon slots remain owned");
                if(mode.equals("absent")){
                    require(magazines.stream().allMatch(o->!((OwnedVillagerOffer)o).taczweaponblueprints$terms().available()&&o.getResult().isEmpty()),"Actual addon removal suspends unavailable magazine items");
                    require(magazines.stream().allMatch(o->com.gamergaming.taczweaponblueprints.villager.OwnedOfferSerialization.save(o, level.registryAccess()).getCompound("sell").getString("id").startsWith("taczmagazines:")),"Native saves retain absent addon item payloads");
                    tag=new CompoundTag();v.saveWithoutId(tag);NbtIo.writeCompressed(tag,file);
                }else require(magazines.stream().allMatch(o->((OwnedVillagerOffer)o).taczweaponblueprints$terms().available()&&!o.getResult().isEmpty()),"Restoring the addon restores the same magazines without a refill");
            }
            checks.add("PASS");
        }catch(Throwable e){checks.add("FAIL: "+e);TaCZWeaponBlueprints.LOGGER.error("Persistence QA failed",e);}
        finally{try{Files.write(root.resolve("persistence-"+mode+"-result.txt"),checks);}catch(Exception e){throw new RuntimeException(e);}server.halt(false);}
    }
    private static StockPlan.Entry entry(MerchantOffer offer){return ((OwnedVillagerOffer)offer).taczweaponblueprints$binding().entry();}
    private static void require(boolean value,String text){if(!value)throw new AssertionError(text);checks.add(text);}
    private static void semanticDependencyChanges(ServerLevel level,Villager v)throws Exception{
        var catalog=com.gamergaming.taczweaponblueprints.compat.magazines.MagazineCatalog.INSTANCE;var original=catalog.publication();var field=catalog.getClass().getDeclaredField("publication");field.setAccessible(true);
        var selected=v.getOffers().stream().filter(o->entry(o).kind()==Kind.MAGAZINE).map(o->entry(o).target()).collect(java.util.stream.Collectors.toSet());
        var unrelated=original.catalog().families().keySet().stream().filter(id->!selected.contains(id)).findFirst().orElseThrow();
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"DependencyQA"));player.moveTo(.5,85,2.5);player.setHealth(20);v.setTradingPlayer(player);var menu=new MerchantMenu(92,player.getInventory(),v);player.containerMenu=menu;
        try{
            int counter=0;for(var target:List.of(unrelated,selected.iterator().next())){
                var families=new HashMap<>(original.catalog().families());var old=families.get(target);
                families.put(target,new com.gamergaming.taczweaponblueprints.compat.magazines.MagazineDescriptor(old.familyKey(),old.upstreamFamily(),old.ammoId(),old.capacity()+1,old.extensionLevel(),old.compatibleGuns(),old.recipeId(),old.output(),old.ingredients()));
                var capture=new com.gamergaming.taczweaponblueprints.compat.magazines.TaCZMagazinesAdapter.Capture(original.catalog().status(),families,original.catalog().familyByRecipe(),original.catalog().rejected());
                field.set(catalog,new com.gamergaming.taczweaponblueprints.compat.magazines.MagazineCatalog.Publication(original.revision()+ ++counter,capture));
                boolean valid=((VillagerMerchantMenuBridge)menu).taczweaponblueprints$validateQuote(player);
                require(valid==(counter==1),counter==1?"Unrelated magazine changes leave the merchant quote open":"Changed selected magazine capacity invalidates a quote even when output NBT is unchanged");
            }
        }finally{field.set(catalog,original);player.closeContainer();v.getOffers();}
    }
    private static void currencyAndDeath(ServerLevel level,Villager v){
        var c=ModConfigs.BLUEPRINT;var original=c.researchCurrencyItem.get();
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"CurrencyQA"));
        player.moveTo(.5,85,2.5);player.setHealth(20);player.getInventory().clearContent();v.setTradingPlayer(player);
        var offer=v.getOffers().stream().filter(o->entry(o).kind()==Kind.AMMO).findFirst().orElseThrow();int uses=offer.getUses();var plan=((StockVillagerBridge)v).taczweaponblueprints$stock().plan;
        var menu=new MerchantMenu(93,player.getInventory(),v);player.containerMenu=menu;menu.setSelectionHint(v.getOffers().indexOf(offer));menu.getSlot(0).set(offer.getCostA().copy());
        var cost=offer.getCostA().copy();c.researchCurrencyItem.validateAndSet("minecraft:emerald");c.onSyncServer();menu.clicked(2,0,ClickType.PICKUP,player);
        require(player.containerMenu!=menu&&player.getInventory().countItem(cost.getItem())==cost.getCount()&&menu.getCarried().isEmpty(),"Changing currency rejects the old result and returns the original payment once");
        var slot=entry(offer).slot();var current=v.getOffers().stream().filter(o->entry(o).slot().equals(slot)).findFirst().orElseThrow();
        require(current.getCostA().is(Items.EMERALD)&&current.getUses()==uses&&plan.entries().values().stream().filter(e->e.kind()!=Kind.CURRENCY).allMatch(e->e.equals(((StockVillagerBridge)v).taczweaponblueprints$stock().plan.entries().get(e.slot()))),"Currency changes preserve selected merchandise and spent capacity");
        c.researchCurrencyItem.validateAndSet(original);c.onSyncServer();player.closeContainer();
        var doomed=new Villager(EntityType.VILLAGER,level);doomed.moveTo(.5,85,.5);doomed.setNoAi(true);doomed.setVillagerData(doomed.getVillagerData().setProfession(ModVillagers.GUNSMITH.get()));level.addFreshEntity(doomed);
        player.getInventory().clearContent();doomed.setTradingPlayer(player);offer=doomed.getOffers().get(0);menu=new MerchantMenu(94,player.getInventory(),doomed);player.containerMenu=menu;menu.setSelectionHint(0);cost=offer.getCostA().copy();menu.getSlot(0).set(cost.copy());uses=offer.getUses();
        doomed.setHealth(0);menu.clicked(2,0,ClickType.PICKUP,player);
        require(player.containerMenu!=menu&&player.getInventory().countItem(cost.getItem())==cost.getCount()&&offer.getUses()==uses&&menu.getCarried().isEmpty(),"Dead merchant rejects output and returns input without consuming stock");doomed.discard();
    }
    private static void checkLiveLifecycle(ServerLevel level,Villager v)throws Exception{
        var plan=((StockVillagerBridge)v).taczweaponblueprints$stock().plan;var c=ModConfigs.BLUEPRINT;
        c.enableGunsmith.validateAndSet(false);c.onSyncServer();require(v.getOffers().stream().allMatch(o->!((OwnedVillagerOffer)o).taczweaponblueprints$terms().available()),"Profession disable suspends existing owned offers");
        var pos=new BlockPos(2,85,0);level.setBlockAndUpdate(pos,ModBlocks.GUNSMITH_STATION.get().defaultBlockState());
        // Reset only this disposable fixture reservation when a previous failed run left it occupied.
        if(level.getPoiManager().getFreeTickets(pos)==0)level.getPoiManager().release(pos);
        var recruit=new Villager(EntityType.VILLAGER,level);recruit.moveTo(2.5,85,.5);recruit.setNoAi(true);level.addFreshEntity(recruit);level.getPoiManager().take(h->h.is(ModVillagers.GUNSMITH_POI.getKey()),(h,p)->p.equals(pos),pos,1).orElseThrow();recruit.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE,GlobalPos.of(level.dimension(),pos));
        AssignProfessionFromJobSite.create().tryStart(level,recruit,level.getGameTime());require(recruit.getVillagerData().getProfession()==VillagerProfession.NONE,"Disabled profession cannot be assigned by native job AI");
        require(!recruit.getBrain().hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE)&&!recruit.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE)&&level.getPoiManager().getFreeTickets(pos)==1,"Rejected assignment releases its workstation ticket and job memories");
        c.enableGunsmith.validateAndSet(true);c.onSyncServer();level.getPoiManager().take(h->h.is(ModVillagers.GUNSMITH_POI.getKey()),(h,p)->p.equals(pos),pos,1).orElseThrow();recruit.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE,GlobalPos.of(level.dimension(),pos));AssignProfessionFromJobSite.create().tryStart(level,recruit,level.getGameTime());
        require(recruit.getVillagerData().getProfession()==ModVillagers.GUNSMITH.get(),"Re-enabled profession can be assigned without registry replacement");recruit.releasePoi(MemoryModuleType.JOB_SITE);recruit.discard();
        require(v.getOffers().stream().allMatch(o->((OwnedVillagerOffer)o).taczweaponblueprints$terms().available())&&((StockVillagerBridge)v).taczweaponblueprints$stock().plan.equals(plan),"Re-enable restores old merchandise and spent capacity");
        var before=v.getOffers().get(0);int capacity=before.getMaxUses();c.villagerStockPercent.validateAndSet(200);c.onSyncServer();require(v.getOffers().get(0).getMaxUses()==capacity&&v.getOffers().get(0).getUses()==1,"Capacity increases cannot refill on refresh");v.restock();require(v.getOffers().get(0).getMaxUses()==capacity*2&&v.getOffers().get(0).getUses()==0,"Native restock alone applies increased capacity");c.villagerStockPercent.validateAndSet(100);c.onSyncServer();
        semanticDependencyChanges(level,v);
        currencyAndDeath(level,v);
        var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"UnloadCustomer"));p.moveTo(.5,85,2.5);p.setHealth(20);v.setTradingPlayer(p);var menu=new MerchantMenu(91,p.getInventory(),v);p.containerMenu=menu;
        var o=v.getOffers().get(0);menu.setSelectionHint(0);menu.getSlot(0).set(o.getCostA().copy());int count=menu.getSlot(0).getItem().getCount();var item=menu.getSlot(0).getItem().getItem();
        v.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);menu.clicked(2,0,ClickType.PICKUP,p);require(p.containerMenu!=menu&&p.getInventory().countItem(item)==count&&menu.getCarried().isEmpty(),"Unloaded merchant rejects stale result transfer and returns input once");
        var raw=com.gamergaming.taczweaponblueprints.villager.OwnedOfferSerialization.save(o, level.registryAccess());var missing=raw.getCompound("buy");missing.putString("id","qa:removed_currency");raw.put("buy",missing);var absent=com.gamergaming.taczweaponblueprints.villager.OwnedOfferSerialization.read(raw, level.registryAccess());
        require(((OwnedVillagerOffer)absent).taczweaponblueprints$binding()!=null&&absent.isOutOfStock()&&com.gamergaming.taczweaponblueprints.villager.OwnedOfferSerialization.save(absent, level.registryAccess()).getCompound("buy").getString("id").equals("qa:removed_currency"),"Missing custom currency retains binding and raw input through native saves");
    }
}
