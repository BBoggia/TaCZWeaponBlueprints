package com.gamergaming.taczweaponblueprints.villagerqa;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.villager.*;
import com.gamergaming.taczweaponblueprints.villager.stock.*;
import com.gamergaming.taczweaponblueprints.villager.trade.*;
import com.gamergaming.taczweaponblueprints.compat.magazines.MagazineCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Drives actual connected players through native packet actions in disposable environments. */
@EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID)
public final class VillagerLifecycleProbe {
    private static final List<String> checks=new ArrayList<>();
    private static int phase,tick,since,action;
    private static boolean done,reloading;
    private static Villager v;
    private static MerchantMenu open;
    private static MerchantOffer offer;
    private static StockPlan plan;
    private static long magazineRevision;
    private static int beforePayment,uses,firstCustomerPrice;
    private static Path root;
    private static boolean integrated;
    private static String client(){return integrated?"client-integrated":"client-a";}
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
        if(Boolean.getBoolean("tacz.villagerQA.presentation")||Boolean.getBoolean("tacz.villagerQA.balance")||!Boolean.getBoolean("tacz.villagerQA.lifecycle")||done||VillagerPrototypeStock.root()==null)return;
        var server=event.getServer();integrated=!server.isDedicatedServer();root=Path.of(System.getProperty("tacz.villagerQA.control"));tick++;
        if(tick%5!=0||reloading)return;
        try{
            var a=integrated?server.getPlayerList().getPlayers().stream().findFirst().orElse(null):server.getPlayerList().getPlayerByName("VillagerQA_A");
            var b=server.getPlayerList().getPlayerByName("VillagerQA_B");
            if(phase>0&&tick-since>2400)throw new AssertionError("Timeout in native client phase "+phase);
            if(a==null&&phase!=11)return;
            switch(phase){
                case 0->{
                    if(StockPublicationCoordinator.pending())return;
                    var level=server.overworld();for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)level.setBlockAndUpdate(new BlockPos(x,84,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    a.teleportTo(level,.5,85,2.5,0,0);a.setHealth(20);a.getInventory().clearContent();a.getInventory().add(new ItemStack(ModItems.SCRAP.get(),64));
                    v=new Villager(EntityType.VILLAGER,level);v.moveTo(.5,85,.5);v.setNoAi(true);v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.GUNSMITH.get()));level.addFreshEntity(v);
                    offer=v.getOffers().stream().filter(o->((OwnedVillagerOffer)o).taczweaponblueprints$binding().entry().kind()==TradeDefinition.Kind.AMMO).findFirst().orElseThrow();plan=state().plan;
                    v.getGossips().add(a.getUUID(),net.minecraft.world.entity.ai.gossip.GossipType.MAJOR_POSITIVE,100);a.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE,12000,3));
                    open(a);firstCustomerPrice=offer.getCostA().getCount();magazineRevision=MagazineCatalog.INSTANCE.publication().revision();next(1);
                }
                case 1->{
                    if(!status(client()))return;require(true,"Real client receives bounded status for its native MerchantMenu");
                    if(!integrated){Files.writeString(root.resolve("ready-client-b"),"join");next(2);}else next(3);
                }
                case 2->{if(b==null||!stateFile("client-b").map(j->j.get("connected").getAsBoolean()).orElse(false))return;
                    require(a.containerMenu==open,"Second real client login leaves unchanged merchant session open");require(MagazineCatalog.INSTANCE.publication().revision()==magazineRevision,"Login recipe regeneration retains semantic magazine publication");b.teleportTo(server.overworld(),2.5,85,2.5,0,0);next(3);}
                case 3->{uses=offer.getUses();beforePayment=a.getInventory().countItem(ModItems.SCRAP.get());send(client(),"take",open.containerId,v.getOffers().indexOf(offer));next(4);}
                case 4->{if(offer.getUses()==uses)return;
                    require(offer.getUses()==uses+1&&ItemStack.matches(open.getCarried(),offer.getResult()),"Real client result-click delivers one output and spends one use");
                    require(a.getInventory().countItem(ModItems.SCRAP.get())+open.getSlot(0).getItem().getCount()==beforePayment-offer.getCostA().getCount(),"Native packet purchase charges exactly its displayed price");
                    a.closeContainer();open(a);send(client(),"fill",open.containerId,v.getOffers().indexOf(offer));next(5);
                }
                case 5->{if(open.getSlot(0).getItem().isEmpty()||!actionDone(client()))return;
                    beforePayment=a.getInventory().countItem(ModItems.SCRAP.get())+open.getSlot(0).getItem().getCount();uses=offer.getUses();
                    var menu=open;reloading=true;server.reloadResources(server.getPackRepository().getSelectedIds()).whenCompleteAsync((ignored,error)->{
                        reloading=false;try{if(error!=null)throw new AssertionError(error);StockPublicationCoordinator.settle();require(a.containerMenu==menu&&((VillagerMerchantMenuBridge)menu).taczweaponblueprints$validateQuote(a),"Unchanged reload while paying preserves native inputs and quote");invalidReload(server,a,menu);}catch(Throwable e){fail(server,e);}},server);
                }
                case 6->{ModConfigs.BLUEPRINT.villagerPricePercent.validateAndSet(125);ModConfigs.BLUEPRINT.onSyncServer();next(7);}
                case 7->{if(a.containerMenu instanceof MerchantMenu)return;
                    require(a.getInventory().countItem(ModItems.SCRAP.get())==beforePayment,"Changed prices close stale menu and return all payment once");require(state().plan.equals(plan)&&v.getOffers().get(0).getUses()>=0&&offer.getUses()==uses,"Refresh preserves target identities and spent stock");
                    ModConfigs.BLUEPRINT.villagerPricePercent.validateAndSet(100);ModConfigs.BLUEPRINT.onSyncServer();offer=v.getOffers().stream().filter(o->((OwnedVillagerOffer)o).taczweaponblueprints$binding().entry().kind()==TradeDefinition.Kind.AMMO).findFirst().orElseThrow();
                    open(a);send(client(),"fill",open.containerId,v.getOffers().indexOf(offer));next(8);
                }
                case 8->{if(open.getSlot(0).getItem().isEmpty()||!actionDone(client()))return;
                    beforePayment=a.getInventory().countItem(ModItems.SCRAP.get())+open.getSlot(0).getItem().getCount();
                    if(!integrated){b.getInventory().clearContent();b.getInventory().add(new ItemStack(ModItems.SCRAP.get(),64));open(b);require(offer.getCostA().getCount()>firstCustomerPrice,"Customer-specific Hero/reputation discounts do not leak to the second player");}
                    else v.discard();next(9);
                }
                case 9->{if(a.containerMenu instanceof MerchantMenu)return;
                    require(a.getInventory().countItem(ModItems.SCRAP.get())==beforePayment,"Stale customer/entity removal returns inputs once");
                    if(!integrated){require(b.containerMenu instanceof MerchantMenu&&v.getTradingPlayer()==b,"Closing old customer cannot clear the new customer's native merchant session");send("client-b","take",b.containerMenu.containerId,v.getOffers().indexOf(offer));uses=offer.getUses();next(10);}else finish(server);
                }
                case 10->{if(offer.getUses()==uses)return;
                    require(offer.getUses()==uses+1,"Second real player shares spent stock and completes native purchase");send("client-b","stop",0,0);next(11);}
                case 11->{if(b!=null)return;require(v.getTradingPlayer()==null,"Disconnect releases merchant ownership");finish(server);}
                default->throw new AssertionError("Unexpected lifecycle phase");
            }
            Files.writeString(root.resolve(integrated?"integrated-progress.txt":"multiplayer-progress.txt"),"phase="+phase+" tick="+tick+"\n"+String.join("\n",checks));
        }catch(Throwable e){fail(server,e);}
    }
    private static void invalidReload(MinecraftServer server,ServerPlayer player,MerchantMenu menu)throws Exception{
        reloading=true;var original=TradeDataManager.INSTANCE.publication();
        var pack=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("lifecycle-invalid-qa");Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":15,\"description\":\"Disposable invalid reload check\"}}");
        var file=pack.resolve("data/qa/taczweaponblueprints/villager_trades/invalid.json");Files.createDirectories(file.getParent());Files.writeString(file,"{ invalid json");
        server.getPackRepository().reload();var packs=new ArrayList<>(server.getPackRepository().getSelectedIds());if(!packs.contains("file/lifecycle-invalid-qa"))packs.add("file/lifecycle-invalid-qa");
        server.reloadResources(packs).whenCompleteAsync((unused,error)->{
            try{
                if(error!=null)throw new AssertionError(error);StockPublicationCoordinator.settle();
                require(TradeDataManager.INSTANCE.publication().failure().isPresent()&&TradeDataManager.INSTANCE.publication().snapshot()==original.snapshot(),"Invalid trade reload retains the complete previous definition snapshot");
                require(player.containerMenu==menu&&((VillagerMerchantMenuBridge)menu).taczweaponblueprints$validateQuote(player),"Invalid definition reload cannot consume payment or interrupt unchanged valid stock");
                Files.delete(file);
                server.reloadResources(packs).whenCompleteAsync((ignored,recovery)->{
                    reloading=false;try{if(recovery!=null)throw new AssertionError(recovery);StockPublicationCoordinator.settle();require(TradeDataManager.INSTANCE.publication().failure().isEmpty()&&((VillagerMerchantMenuBridge)menu).taczweaponblueprints$validateQuote(player),"Valid reload recovers without rerolling the waiting quote");next(6);}catch(Throwable e){fail(server,e);}
                },server);
            }catch(Throwable e){reloading=false;fail(server,e);}
        },server);
    }
    private static StockState state(){return ((StockVillagerBridge)v).taczweaponblueprints$stock();}
    private static void open(ServerPlayer player)throws Exception{v.setTradingPlayer(null);net.neoforged.fml.util.ObfuscationReflectionHelper.findMethod(Villager.class,"updateSpecialPrices",net.minecraft.world.entity.player.Player.class).invoke(v,player);v.setTradingPlayer(player);v.openTradingScreen(player,v.getDisplayName(),v.getVillagerData().getLevel());open=(MerchantMenu)player.containerMenu;}
    private static void send(String client,String kind,int container,int index)throws Exception{var j=new JsonObject();j.addProperty("id",++action);j.addProperty("kind",kind);j.addProperty("container",container);j.addProperty("index",index);Files.writeString(root.resolve(client+"-action.json"),j.toString());}
    private static Optional<JsonObject> stateFile(String client){try{return Optional.of(JsonParser.parseString(Files.readString(root.resolve(client+"-state.json"))).getAsJsonObject());}catch(Exception e){return Optional.empty();}}
    private static boolean status(String client){return stateFile(client).filter(j->j.has("status")&&j.get("status").getAsBoolean()).isPresent();}
    private static boolean actionDone(String client){return stateFile(client).filter(j->j.get("last_action").getAsInt()==action).isPresent();}
    private static void next(int value){phase=value;since=tick;}
    private static void require(boolean ok,String text){if(!ok)throw new AssertionError(text);checks.add(text);}
    private static void fail(MinecraftServer server,Throwable error){checks.add("FAIL: "+error);TaCZWeaponBlueprints.LOGGER.error("Native lifecycle QA failed",error);finish(server);}
    private static void finish(MinecraftServer server){done=true;try{if(checks.stream().noneMatch(s->s.startsWith("FAIL:")))checks.add("PASS");Files.write(root.resolve(integrated?"integrated-result.txt":"multiplayer-result.txt"),checks);send(client(),"stop",0,0);}catch(Exception e){throw new RuntimeException(e);}if(!integrated)server.halt(false);}
}
