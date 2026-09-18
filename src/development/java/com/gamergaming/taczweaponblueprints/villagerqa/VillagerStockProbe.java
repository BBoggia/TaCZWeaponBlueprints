package com.gamergaming.taczweaponblueprints.villagerqa;

import java.nio.file.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.compat.magazines.*;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.item.BlueprintFragmentItem;
import com.gamergaming.taczweaponblueprints.villager.*;
import com.gamergaming.taczweaponblueprints.villager.stock.*;
import com.gamergaming.taczweaponblueprints.villager.trade.*;
import com.tacz.guns.api.item.IGun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.ResetProfession;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.*;

/** Uses production factories and mixins; only the disposable test harness is development-only. */
@Mod.EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID)
public final class VillagerStockProbe {
    private static final List<String> checks=new ArrayList<>();
    private static ServerLevel level;
    private static boolean deferred;
    @SubscribeEvent public static void started(ServerStartedEvent event){
        var root=VillagerPrototypeStock.root();if(root==null||!Boolean.getBoolean("tacz.villagerQA.stock"))return;
        var server=event.getServer();server.execute(()->{
            try{
                level=server.overworld();server.setDifficulty(Difficulty.HARD,true);
                for(var profession:List.of(ModVillagers.GUNSMITH.get(),ModVillagers.BALLISTICS_RESEARCHER.get()))for(int l=1;l<=5;l++){
                    var factories=VillagerTrades.TRADES.get(profession).get(l);
                    require(factories.length==2&&Arrays.stream(factories).allMatch(f->f instanceof StockTradeEvents.Factory),"Exactly two production core factories: "+profession.name()+" level "+l);
                }
                var early=villager(ModVillagers.GUNSMITH.get());var earlyOffers=early.getOffers();
                if(MagazineCatalog.INSTANCE.publication().catalog().status()==TaCZMagazinesAdapter.Status.WAITING_FOR_RECIPES){
                    require(state(early).plan==null&&earlyOffers.isEmpty(),"Unpublished magazine recipes keep the whole equipment plan pending");
                    var foreign=new MerchantOffer(new ItemStack(Items.EMERALD),new ItemStack(Items.APPLE),4,2,.05F);earlyOffers.add(foreign);
                    MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(server.getPlayerList(),null));StockPublicationCoordinator.settle();
                    require(early.getOffers().contains(foreign)&&early.getOffers().size()==3,"Publication retries pending native choices once, preserving foreign offers");
                }else MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(server.getPlayerList(),null));StockPublicationCoordinator.settle();
                var cache=StockCatalog.current();for(int n=0;n<50;n++)early.getOffers();
                require(cache==StockCatalog.current(),"Repeated interaction reuses the shared catalog publication");early.discard();
                var guns=villager(ModVillagers.GUNSMITH.get());guns.getOffers();var savedPlan=state(guns).plan;
                for(int l=2;l<=5;l++)advance(guns);
                verifyGuns(guns);require(savedPlan.equals(state(guns).plan),"Native level-ups preserve the preselected whole-career plan");
                var researcher=villager(ModVillagers.BALLISTICS_RESEARCHER.get());researcher.getOffers();for(int l=2;l<=5;l++)advance(researcher);
                require(researcher.getOffers().size()==10,"Researcher reaches Master with ten core trades");
                var studies=new HashMap<Selection,ResourceLocation>();
                for(var offer:researcher.getOffers()){
                    var e=binding(offer).entry();if(e.kind()!=Kind.ANALYSIS_FRAGMENTS)continue;
                    var prior=studies.putIfAbsent(e.selection(),e.target());require(prior==null||prior.equals(e.target()),"Repeated study rows retain one target");
                    require(BlueprintFragmentItem.identity(offer.getResult()).orElseThrow().analysisClass().orElseThrow()==com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisRewardClass.PROTECTED_PREREQUISITES,"Analysis fragment reward class is protected");
                }
                int size=guns.getOffers().size();for(int n=0;n<100;n++)guns.getOffers();require(guns.getOffers().size()==size,"Magazine support and core offers append once");
                reversedFactories();purchase();persistence(guns);suspension(researcher);interference();reset();
                require(!villager(VillagerProfession.FARMER).getOffers().isEmpty(),"Vanilla profession stock remains native");
                reloadRoundTrip(root);deferred=true;
            }catch(Throwable failure){checks.add("FAIL: "+failure);TaCZWeaponBlueprints.LOGGER.error("Stock QA failed",failure);}
            finally{if(!deferred){try{Files.write(root.resolve("stock-result.txt"),checks);}catch(Exception e){TaCZWeaponBlueprints.LOGGER.error("Stock QA result failed",e);}server.halt(false);}}
        });
    }
    private static void reloadRoundTrip(Path root){
        var v=villager(ModVillagers.GUNSMITH.get());v.getOffers().get(0).increaseUses();v.setVillagerXp(9);
        var plan=state(v).plan;var catalog=StockCatalog.current();var server=level.getServer();
        server.reloadResources(server.getPackRepository().getSelectedIds()).whenCompleteAsync((ignored,failure)->{
            try{
                if(failure!=null)throw new AssertionError("Native reload failed",failure);
                MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(server.getPlayerList(),null));StockPublicationCoordinator.settle();
                require(StockCatalog.current()!=catalog,"Native datapack reload replaces the shared stock snapshot");
                require(v.getOffers().size()==2&&state(v).plan.equals(plan)&&v.getOffers().get(0).getUses()==1&&v.getVillagerXp()==9,
                        "Native datapack reload preserves established targets, uses, XP and offer count");
                require(v.getOffers().stream().allMatch(VillagerStockProbe::available),"Unchanged merchandise remains available after native reload");
                v.discard();checks.add("PASS");
            }catch(Throwable error){checks.add("FAIL: "+error);TaCZWeaponBlueprints.LOGGER.error("Stock reload QA failed",error);}
            finally{try{Files.write(root.resolve("stock-result.txt"),checks);}catch(Exception e){TaCZWeaponBlueprints.LOGGER.error("Stock QA result failed",e);}server.halt(false);}
        },server);
    }
    private static void purchase()throws Exception{
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.fromString("4d46a778-09b6-401c-a1c0-d6b86e0e0578"),"StockCustomer"));
        player.moveTo(.5,85,.5);player.setHealth(20);player.getInventory().clearContent();
        var v=villager(ModVillagers.GUNSMITH.get());v.getOffers();advance(v);var offers=v.getOffers();
        int index=0;while(binding(offers.get(index)).entry().kind()!=Kind.GUN)index++;
        var offer=offers.get(index);var plan=state(v).plan;
        player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve().orElseThrow().addBlueprint(binding(offer).entry().target().toString());
        v.setTradingPlayer(player);var menu=new net.minecraft.world.inventory.MerchantMenu(1,player.getInventory(),v);player.containerMenu=menu;
        menu.setSelectionHint(index);menu.getSlot(0).set(offer.getCostA().copy());menu.getSlot(1).set(offer.getCostB().copy());
        menu.clicked(2,0,net.minecraft.world.inventory.ClickType.PICKUP,player);
        require(ItemStack.matches(menu.getCarried(),offer.getResult())&&offer.getUses()==1&&v.getVillagerXp()==offer.getXp()
                &&menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().isEmpty(),"Production gun purchase delivers exactly one output for one native payment/use/XP award");
        require(state(v).plan.equals(plan),"Already-known customer target does not reroll merchant merchandise");
        player.closeContainer();v.discard();
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);checks.add(message);}
    private static Villager villager(VillagerProfession p){var v=new Villager(EntityType.VILLAGER,level);v.moveTo(.5,85,.5);v.setNoAi(true);v.setVillagerData(v.getVillagerData().setProfession(p));level.addFreshEntity(v);return v;}
    private static StockState state(Villager v){return ((StockVillagerBridge)v).taczweaponblueprints$stock();}
    private static StockOfferBinding binding(MerchantOffer o){return ((OwnedVillagerOffer)o).taczweaponblueprints$binding();}
    private static boolean available(MerchantOffer o){return ((OwnedVillagerOffer)o).taczweaponblueprints$terms().available();}
    private static void advance(Villager v)throws Exception{ObfuscationReflectionHelper.findMethod(Villager.class,"m_35528_").invoke(v);}
    private static Map<ResourceLocation,MerchantOffer> owned(Villager v){var map=new HashMap<ResourceLocation,MerchantOffer>();for(var o:v.getOffers())if(binding(o)!=null)map.put(binding(o).entry().slot(),o);return map;}
    private static void verifyGuns(Villager v){
        var offers=owned(v);var catalog=StockCatalog.current();
        require(offers.values().stream().filter(o->binding(o).entry().role()==Role.CORE).count()==10,"Gunsmith reaches Master with ten core trades");int guns=0;Set<ResourceLocation> families=new HashSet<>();
        for(var o:offers.values()){
            var e=binding(o).entry();require(available(o),"Available generated slot: "+e.slot());
            if(e.kind()==Kind.MAGAZINE)require(families.add(e.target()),"Magazine family is not duplicated");
            if(e.kind()!=Kind.GUN)continue;guns++;var gun=IGun.getIGunOrNull(o.getResult());
            require(gun!=null&&gun.getGunId(o.getResult()).equals(e.target())&&gun.getCurrentAmmoCount(o.getResult())==0&&!gun.hasBulletInBarrel(o.getResult())&&gun.getDummyAmmoAmount(o.getResult())==0&&!InsertedMagazineGuard.hasInsertedMagazine(o.getResult()),"Gun output retains exact identity and no loaded ammunition");
            require(e.dependencies().stream().allMatch(offers::containsKey),"Every sold gun has its actual selected supply offers");
            for(var dep:e.dependencies()){var supplied=binding(offers.get(dep)).entry();if(supplied.kind()==Kind.AMMO)require(catalog.ammo(e.target()).filter(supplied.target()::equals).isPresent(),"Ammo matches selected weapon");}
            var mag=catalog.magazine(e.target());if(mag.status()==StockPlanner.SupplyStatus.READY)require(e.dependencies().stream().map(state(v).plan.entries()::get).anyMatch(d->d.kind()==Kind.MAGAZINE&&d.target().equals(mag.family().orElseThrow())&&d.level()<=e.level()),"Required magazine is available no later than its gun");
        }
        require(guns>=2,"Gunsmith stocks starter and Expert guns on reference content");
    }
    private static void reversedFactories(){
        var a=villager(ModVillagers.GUNSMITH.get());var b=villager(ModVillagers.GUNSMITH.get());
        for(var v:List.of(a,b)){v.setOffers(new MerchantOffers());((StockVillagerBridge)v).taczweaponblueprints$stock(new StockState(Profession.GUNSMITH,422));}
        for(int i:new int[]{0,1}){var o=StockService.factory(a,1,i);if(o!=null)((StockVillagerBridge)a).taczweaponblueprints$rawOffers().add(o);}
        for(int i:new int[]{1,0}){var o=StockService.factory(b,1,i);if(o!=null)((StockVillagerBridge)b).taczweaponblueprints$rawOffers().add(o);}
        require(state(a).plan.equals(state(b).plan)&&owned(a).keySet().equals(owned(b).keySet()),"Reversed native factory invocation preserves pair/study choices");a.discard();b.discard();
    }
    private static void persistence(Villager v)throws Exception{
        var offer=v.getOffers().get(0);offer.increaseUses();v.setVillagerXp(37);var plan=state(v).plan;
        var tag=new CompoundTag();v.saveWithoutId(tag);var restored=villager(VillagerProfession.NONE);restored.load(tag);
        require(state(restored).plan.equals(plan)&&restored.getOffers().get(0).getUses()==1&&restored.getVillagerXp()==37,"Entity save/load preserves complete plan, spent stock and XP");
        var old=restored.getOffers().get(0);var uses=old.getUses();ModConfigs.BLUEPRINT.villagerPricePercent.validateAndSet(125);ModConfigs.BLUEPRINT.onSyncServer();
        require(restored.getOffers().get(0).getUses()==uses&&restored.getVillagerXp()==37&&state(restored).plan.equals(plan),"Price publication does not replenish stock, reset XP or reroll merchandise");
        ModConfigs.BLUEPRINT.villagerPricePercent.validateAndSet(100);ModConfigs.BLUEPRINT.onSyncServer();restored.getOffers();
        int max=restored.getOffers().get(0).getMaxUses();ModConfigs.BLUEPRINT.villagerStockPercent.validateAndSet(200);ModConfigs.BLUEPRINT.onSyncServer();
        require(restored.getOffers().get(0).getMaxUses()==max&&restored.getOffers().get(0).getUses()==uses,"Stock increase waits for native restock instead of replenishing on open");
        restored.restock();require(restored.getOffers().get(0).getMaxUses()==max*2&&restored.getOffers().get(0).getUses()==0,"Native restock applies the new stock capacity");
        ModConfigs.BLUEPRINT.villagerStockPercent.validateAndSet(100);ModConfigs.BLUEPRINT.onSyncServer();
        var beforeIds=new HashSet<UUID>();level.getEntitiesOfClass(ZombieVillager.class,v.getBoundingBox().inflate(2)).forEach(e->beforeIds.add(e.getUUID()));
        new Zombie(EntityType.ZOMBIE,level).killedEntity(level,v);
        var infected=level.getEntitiesOfClass(ZombieVillager.class,v.getBoundingBox().inflate(2)).stream().filter(e->!beforeIds.contains(e.getUUID())).findFirst().orElseThrow();
        var existing=new HashSet<UUID>();level.getEntitiesOfClass(Villager.class,infected.getBoundingBox().inflate(2)).forEach(e->existing.add(e.getUUID()));
        ObfuscationReflectionHelper.findMethod(ZombieVillager.class,"m_34398_",ServerLevel.class).invoke(infected,level);
        var cured=level.getEntitiesOfClass(Villager.class,infected.getBoundingBox().inflate(2)).stream().filter(e->!existing.contains(e.getUUID())).findFirst().orElseThrow();
        require(cured.getOffers().get(0).getUses()==1&&cured.getVillagerXp()==37&&state(cured).plan.equals(plan),"Actual zombification and cure preserve stock, XP and future choices");
        cured.restock();require(cured.getOffers().get(0).getUses()==0&&state(cured).plan.equals(plan),"Only native restock replenishes uses and retains merchandise");cured.discard();restored.discard();
    }
    private static void suspension(Villager researcher){
        var v=villager(ModVillagers.GUNSMITH.get());v.getOffers();try{advance(v);}catch(Exception e){throw new RuntimeException(e);}
        var gun=owned(v).values().stream().filter(o->binding(o).entry().kind()==Kind.GUN).findFirst().orElseThrow();var target=binding(gun).entry().target();
        var plan=state(v).plan;ModConfigs.BLUEPRINT.gunBlacklist.validateAndSet(Set.of(target.toString()));ModConfigs.BLUEPRINT.onSyncServer();
        v.getOffers();require(!available(gun)&&gun.isOutOfStock(),"New blacklist suspends an established gun without replacing its target");
        v.getOffers();require(!available(gun)&&state(v).plan.equals(plan),"Repeated interaction cannot reactivate suspended merchandise");
        ModConfigs.BLUEPRINT.gunBlacklist.validateAndSet(Set.of());ModConfigs.BLUEPRINT.onSyncServer();v.getOffers();require(available(gun),"Restored content resumes the same saved gun");
        // Changed recipe/result NBT must remain rejected across repeated calls in one publication.
        gun.getResult().getOrCreateTag().putBoolean("qa_changed_output",true);v.getOffers();v.getOffers();require(!available(gun),"Unexpected saved output NBT remains suspended on repeated interaction");
        ModConfigs.BLUEPRINT.villagerMaterialPurchases.validateAndSet(false);ModConfigs.BLUEPRINT.villagerDataSales.validateAndSet(false);ModConfigs.BLUEPRINT.onSyncServer();
        var dead=villager(ModVillagers.BALLISTICS_RESEARCHER.get());require(dead.getOffers().isEmpty()&&!state(dead).plan.reachable(),"Disabled Novice paths cannot generate unreachable later-level stock");
        researcher.getOffers();require(researcher.getOffers().stream().filter(o->binding(o).entry().kind()==Kind.RESEARCH_DATA).noneMatch(VillagerStockProbe::available),"Existing Research Data offers follow current category restrictions");
        ModConfigs.BLUEPRINT.villagerMaterialPurchases.validateAndSet(true);ModConfigs.BLUEPRINT.villagerDataSales.validateAndSet(true);ModConfigs.BLUEPRINT.onSyncServer();require(dead.getOffers().size()==2&&state(dead).plan.reachable(),"An unissued exhausted plan retries when configuration becomes viable");dead.discard();v.discard();researcher.discard();
    }
    private static void interference()throws Exception{
        var v=villager(ModVillagers.GUNSMITH.get());v.getOffers();advance(v);
        var rows=v.getOffers();var gun=rows.stream().filter(o->binding(o).entry().kind()==Kind.GUN).findFirst().orElseThrow();var deps=binding(gun).entry().dependencies();
        rows.removeIf(o->binding(o)!=null&&deps.contains(binding(o).entry().slot()));
        var foreign=new MerchantOffer(new ItemStack(Items.EMERALD),new ItemStack(Items.APPLE),4,2,.05F);rows.add(foreign);
        var trades=VillagerTrades.TRADES.get(ModVillagers.GUNSMITH.get());var old=trades.get(1);trades.put(1,new VillagerTrades.ItemListing[]{old[0],(entity,random)->foreign});
        try{v.getOffers();require(!available(gun)&&rows.contains(foreign)&&state(v).interferenceLogged,"Removed supplies suspend owned gun while foreign stock and altered factory list remain intact");
            v.getOffers();require(rows.stream().noneMatch(o->binding(o)!=null&&deps.contains(binding(o).entry().slot())),"Deleted issued supplies are not silently recreated");}
        finally{trades.put(1,old);v.discard();}
    }
    private static void reset(){
        var v=villager(ModVillagers.GUNSMITH.get());v.getOffers();v.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);ResetProfession.create().tryStart(level,v,level.getGameTime());
        require(v.getVillagerData().getProfession()==VillagerProfession.NONE&&state(v)==null,"Untraded native job reset discards the old profession plan");v.discard();
        v=villager(ModVillagers.GUNSMITH.get());v.getOffers().get(0).increaseUses();v.setVillagerXp(2);var p=state(v).plan;v.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);ResetProfession.create().tryStart(level,v,level.getGameTime());
        require(v.getVillagerData().getProfession()==ModVillagers.GUNSMITH.get()&&state(v).plan.equals(p),"First-trade XP retains native profession lock");v.discard();
    }
}
