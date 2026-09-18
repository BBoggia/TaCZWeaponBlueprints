package com.gamergaming.taczweaponblueprints.villagerqa;

import java.nio.file.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.compat.magazines.*;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.item.BlueprintFragmentItem;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisRewardClass;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.villager.*;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Native inventory/AI/persistence checks. Never runs without an explicit marked QA root. */
@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID)
public final class VillagerMerchantProbe {
    private static final List<String> RESULTS = new ArrayList<>();
    private static ServerLevel level;
    private static ServerPlayer player;
    private static int sequence;
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        var root = VillagerPrototypeStock.root();
        if (root == null || (Boolean.getBoolean("tacz.villagerQA.stock") || Boolean.getBoolean("tacz.villagerQA.economy") || Boolean.getBoolean("tacz.villagerQA.lifecycle") || !System.getProperty("tacz.villagerQA.persistence", "").isEmpty()) || !Boolean.getBoolean("tacz.villagerQA.probe") || Boolean.getBoolean("tacz.villagerQA.trades")) return;
        event.getServer().execute(() -> {
            try {
                level = event.getServer().overworld();
                event.getServer().setDifficulty(Difficulty.HARD, true);
                player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("80d0e7a1-63c5-4d6b-91f5-d7f48381d871"), "VillagerProbe"));
                player.setHealth(20); player.moveTo(0.5, 80, 0.5);
                for (int x=-4; x<36; x++) for(int z=-4;z<8;z++) level.setBlockAndUpdate(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState());
                foundation(); pricing(); transfers(); outputCapacity(); lifecycle(); actualContents();
                RESULTS.add("PASS");
            } catch (Throwable failure) {
                RESULTS.add("FAIL: " + failure);
                TaCZWeaponBlueprints.LOGGER.error("Villager native prototype failed", failure);
            } finally {
                try { Files.write(root.resolve("merchant-probe-result.txt"), RESULTS); }
                catch (Exception error) { TaCZWeaponBlueprints.LOGGER.error("Could not save villager probe", error); }
                event.getServer().halt(false);
            }
        });
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        RESULTS.add(message);
    }
    private static Villager villager(VillagerProfession profession) {
        Villager v = new Villager(EntityType.VILLAGER, level); v.moveTo(.5,80,2.5); v.setNoAi(true); v.setOnGround(true);
        v.setVillagerData(v.getVillagerData().setProfession(profession)); level.addFreshEntity(v); return v;
    }
    private static MerchantOffer priced() {
        return VillagerPrototypeStock.offer("price", new ItemStack(Items.EMERALD,24), new ItemStack(Items.IRON_INGOT,4), new ItemStack(Items.PAPER),4,5);
    }
    private static MerchantOffers offers(MerchantOffer... offers) {
        MerchantOffers list = new MerchantOffers(); Collections.addAll(list, offers); return list;
    }
    private static MerchantMenu open(Villager v, MerchantOffer offer) {
        if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        player.getInventory().clearContent(); player.inventoryMenu.setCarried(ItemStack.EMPTY);
        v.setOffers(offers(offer)); v.setTradingPlayer(player);
        MerchantMenu menu = new MerchantMenu(++sequence,player.getInventory(),v); player.containerMenu=menu; return menu;
    }
    private static void pay(MerchantMenu menu, MerchantOffer offer) {
        menu.getSlot(0).set(offer.getCostA().copy()); menu.getSlot(1).set(offer.getCostB().copy());
    }
    private static void foundation() {
        var blocks=List.of(ModBlocks.GUNSMITH_STATION.get(),ModBlocks.BALLISTICS_DESK.get());
        var professions=List.of(ModVillagers.GUNSMITH.get(),ModVillagers.BALLISTICS_RESEARCHER.get());
        for(int i=0;i<blocks.size();i++) for(Direction facing:Direction.Plane.HORIZONTAL) {
            var state=blocks.get(i).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,facing);
            var type=PoiTypes.forState(state).orElseThrow();
            require(professions.get(i).heldJobSite().test(type) && type.is(PoiTypeTags.ACQUIRABLE_JOB_SITE),"POI and tag: "+professions.get(i).name()+" "+facing);
            BlockPos pos=new BlockPos(1,80,2); level.setBlockAndUpdate(pos,state);
            var v=villager(VillagerProfession.NONE);
            v.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE,GlobalPos.of(level.dimension(),pos));
            require(AssignProfessionFromJobSite.create().tryStart(level,v,level.getGameTime()) && v.getVillagerData().getProfession()==professions.get(i),"Native profession assignment: "+facing);
            var path=v.getNavigation().createPath(new BlockPos(0,80,1),0);
            require(path!=null && path.canReach(),"Native navigation reaches work area");
            v.getOffers().get(0).increaseUses();
            new WorkProbe().work(level,v);
            require(v.getOffers().get(0).getUses()==0 && v.getBrain().hasMemoryValue(MemoryModuleType.LAST_WORKED_AT_POI),"Native WorkAtPoi restocks and records work");
            v.discard();
        }
        var farmer=villager(VillagerProfession.FARMER);
        require(!farmer.getOffers().isEmpty(),"Vanilla farmer still generates native offers");farmer.discard();
    }
    private static final class WorkProbe extends WorkAtPoi {
        void work(ServerLevel world,Villager v) { super.start(world,v,world.getGameTime()); }
    }
    private static void pricing() throws Exception {
        var o=priced(); o.setSpecialPriceDiff(-30);
        CompoundTag tag=o.createTag();tag.putInt("demand",20);o=new MerchantOffer(tag);
        require(o.getCostA().getCount()==18,"Demand bonus plus reputation respects minimum 18");
        tag=o.createTag();tag.putInt("demand",0);o=new MerchantOffer(tag);
        require(o.getCostA().getCount()==18,"Demand decrease recalculates floor instead of reusing correction");
        for(int demand:new int[]{0,1,20,Integer.MAX_VALUE,Integer.MIN_VALUE}) {
            tag=o.createTag();tag.putInt("demand",demand);var current=new MerchantOffer(tag);
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try { offers(current).writeToStream(buffer);var client=MerchantOffers.createFromStream(buffer).get(0);
                require(ItemStack.matches(current.getCostA(),client.getCostA()),"Vanilla packet displays charged price at demand "+demand);
                require(((OwnedVillagerOffer)client).taczweaponblueprints$terms()==null,"Native packet leaves ownership off merchandise/client state");
            } finally {buffer.release();}
        }
        var v=villager(ModVillagers.GUNSMITH.get());o=priced();v.setOffers(offers(o));
        v.getGossips().add(player.getUUID(),GossipType.MAJOR_POSITIVE,100);
        player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE,600,3));
        var update=ObfuscationReflectionHelper.findMethod(Villager.class,"m_35540_",Player.class);
        update.invoke(v,player);require(o.getCostA().getCount()==18,"Native Hero and reputation cannot cross floor");
        v.setTradingPlayer(player);v.setTradingPlayer(null);player.removeAllEffects();
        var other=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("76475c01-081d-42a4-aebb-42ba75ad4635"),"VillagerProbe2"));
        update.invoke(v,other);require(o.getCostA().getCount()==24,"New customer has no accumulated floor correction");
        o.increaseUses();v.restock();require(o.getCostA().getCount()>=18,"Restock retains floor");
        var overlap=VillagerPrototypeStock.offer("overlap",new ItemStack(Items.IRON_INGOT,24),new ItemStack(Items.IRON_INGOT,4),new ItemStack(Items.PAPER),4,5);
        var menu=open(v,overlap);player.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT,28));menu.tryMoveItems(0);
        require(menu.getSlot(0).getItem().getCount()==28 && menu.getSlot(1).getItem().isEmpty() && !menu.getSlot(2).getItem().isEmpty(),"Exactly 28 iron autofills normalized trade");
        menu.clicked(2,0,ClickType.PICKUP,player);
        require(menu.getCarried().is(Items.PAPER) && menu.getSlot(0).getItem().isEmpty() && overlap.getUses()==1,"Normalized native purchase pays once");
        overlap.setSpecialPriceDiff(-100);require(overlap.getCostA().getCount()==22,"Overlap protects full material surcharge: minimum 22");
        try {VillagerPrototypeStock.offer("overflow",new ItemStack(Items.IRON_INGOT,64),new ItemStack(Items.IRON_INGOT,12),new ItemStack(Items.PAPER),4,5);throw new AssertionError("76 iron accepted");}
        catch(IllegalArgumentException expected){RESULTS.add("Unrepresentable 64+12 input rejected");}
        player.closeContainer();v.discard();
    }
    private static void transfers() {
        for(var click:List.of(ClickType.PICKUP,ClickType.QUICK_MOVE,ClickType.SWAP,ClickType.THROW)) {
            var v=villager(ModVillagers.GUNSMITH.get());var o=priced();var menu=open(v,o);pay(menu,o);
            int entities=level.getEntitiesOfClass(ItemEntity.class,new AABB(-8,70,-8,8,90,8)).size();
            menu.clicked(2,0,click,player);
            int outputs=menu.getCarried().getCount()+player.getInventory().countItem(Items.PAPER);
            int drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(-8,70,-8,8,90,8)).size()-entities;
            require(o.getUses()==1 && v.getVillagerXp()==5 && menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty() && outputs+drops==1,"Native successful "+click+": one payment, output, use and XP award");
            player.closeContainer();v.discard();
            v=villager(ModVillagers.GUNSMITH.get());o=priced();menu=open(v,o);pay(menu,o);
            var before=o.createTag();var terms=((OwnedVillagerOffer)o).taczweaponblueprints$terms();
            ((OwnedVillagerOffer)o).taczweaponblueprints$terms(new VillagerOfferTerms(terms.slot(),terms.minimumPrimary(),false,terms.revision()+1));
            entities=level.getEntitiesOfClass(ItemEntity.class,new AABB(-8,70,-8,8,90,8)).size();
            menu.clicked(2,0,click,player);
            require(player.containerMenu==player.inventoryMenu && player.getInventory().countItem(Items.EMERALD)==24
                    && player.getInventory().countItem(Items.IRON_INGOT)==4 && player.getInventory().countItem(Items.PAPER)==0
                    && menu.getCarried().isEmpty() && menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty()
                    && o.getUses()==0 && v.getVillagerXp()==0
                    && level.getEntitiesOfClass(ItemEntity.class,new AABB(-8,70,-8,8,90,8)).size()==entities,"Rejected stale "+click+": closed, inputs returned once, no output/use/XP/drop");
            menu.clicked(2,0,click,player);
            require(player.getInventory().countItem(Items.EMERALD)==24 && player.getInventory().countItem(Items.IRON_INGOT)==4,"Repeated stale click cannot return inputs twice");
            v.discard();
        }
        var v=villager(ModVillagers.GUNSMITH.get());var o=priced();var menu=open(v,o);pay(menu,o);o.setSpecialPriceDiff(-4);
        require(!((VillagerMerchantMenuBridge)menu).taczweaponblueprints$validateQuote(player) && player.containerMenu==player.inventoryMenu,"Price change closes stale quote before next input");
        v.setTradingPlayer(player);menu=new MerchantMenu(++sequence,player.getInventory(),v);player.containerMenu=menu;
        require(((VillagerMerchantMenuBridge)menu).taczweaponblueprints$validateQuote(player),"Reopened menu quotes current terms");player.closeContainer();v.discard();
        v=villager(VillagerProfession.FARMER);o=new MerchantOffer(new ItemStack(Items.EMERALD,24),new ItemStack(Items.PAPER),4,1,.05F);o.setSpecialPriceDiff(-100);
        menu=open(v,o);pay(menu,o);menu.clicked(2,0,ClickType.PICKUP,player);
        require(o.getCostA().getCount()==1 && o.getUses()==1,"Unowned vanilla profession pricing/trading untouched");player.closeContainer();v.discard();
    }
    private static void lifecycle() throws Exception {
        var v=villager(ModVillagers.GUNSMITH.get());var o=priced();o.increaseUses();v.setOffers(offers(o));v.setVillagerXp(12);
        var saved=new CompoundTag();v.saveWithoutId(saved);var restored=villager(VillagerProfession.NONE);restored.load(saved);
        require(restored.getOffers().get(0).getUses()==1 && ((OwnedVillagerOffer)restored.getOffers().get(0)).taczweaponblueprints$terms().equals(((OwnedVillagerOffer)o).taczweaponblueprints$terms()),"Native entity save/load preserves owned choices and used stock");restored.discard();
        v.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        ResetProfession.create().tryStart(level,v,level.getGameTime());
        require(v.getVillagerData().getProfession()==ModVillagers.GUNSMITH.get(),"Traded profession survives native job reset check");
        var novice=villager(ModVillagers.GUNSMITH.get());var old=novice.getOffers();novice.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        ResetProfession.create().tryStart(level,novice,level.getGameTime());
        require(novice.getVillagerData().getProfession()==VillagerProfession.NONE && novice.getOffers()!=old,"Viewing an untraded novice does not prevent native profession reset");novice.discard();
        var ids=new HashSet<UUID>();level.getEntitiesOfClass(ZombieVillager.class,v.getBoundingBox().inflate(2)).forEach(e->ids.add(e.getUUID()));
        var zombie=new Zombie(EntityType.ZOMBIE,level);zombie.killedEntity(level,v);
        var infected=level.getEntitiesOfClass(ZombieVillager.class,v.getBoundingBox().inflate(2)).stream().filter(e->!ids.contains(e.getUUID())).findFirst().orElseThrow();
        var beforeIds=new HashSet<UUID>();level.getEntitiesOfClass(Villager.class,infected.getBoundingBox().inflate(2)).forEach(e->beforeIds.add(e.getUUID()));
        ObfuscationReflectionHelper.findMethod(ZombieVillager.class,"m_34398_",ServerLevel.class).invoke(infected,level);
        var cured=level.getEntitiesOfClass(Villager.class,infected.getBoundingBox().inflate(2)).stream().filter(e->!beforeIds.contains(e.getUUID())).findFirst().orElseThrow();
        require(cured.getVillagerData().getProfession()==ModVillagers.GUNSMITH.get() && cured.getVillagerXp()==12
                && cured.getOffers().get(0).getUses()==1 && ((OwnedVillagerOffer)cured.getOffers().get(0)).taczweaponblueprints$terms().equals(((OwnedVillagerOffer)o).taczweaponblueprints$terms()),"Native zombification and curing preserve profession, XP, choices and spent stock");cured.discard();
    }
    private static void outputCapacity() {
        var v=villager(ModVillagers.GUNSMITH.get());
        var o=VillagerPrototypeStock.offer("bulk",new ItemStack(Items.EMERALD,24),ItemStack.EMPTY,new ItemStack(Items.PAPER,16),4,5);
        var menu=open(v,o);pay(menu,o);
        for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        player.getInventory().setItem(0,new ItemStack(Items.PAPER,62));
        menu.clicked(2,0,ClickType.QUICK_MOVE,player);
        require(o.getUses()==0 && v.getVillagerXp()==0 && player.getInventory().countItem(Items.PAPER)==62
                && menu.getSlot(0).getItem().getCount()==24 && menu.getSlot(2).getItem().getCount()==16,"Insufficient room for entire bulk output rejects quick-move without partial payment/output");
        menu.setCarried(new ItemStack(Items.PAPER,62));menu.clicked(2,0,ClickType.PICKUP,player);
        require(menu.getCarried().getCount()==62 && o.getUses()==0,"Native full-cursor check rejects partial result pickup");menu.setCarried(ItemStack.EMPTY);
        player.getInventory().setItem(0,new ItemStack(Items.PAPER,48));menu.clicked(2,0,ClickType.QUICK_MOVE,player);
        require(o.getUses()==1 && player.getInventory().countItem(Items.PAPER)==64 && v.getVillagerXp()==5,"Exact remaining inventory capacity delivers all 16 output items");
        player.closeContainer();v.discard();
        // Direct calls to the native quick-move entry point receive the same stale-quote guard.
        v=villager(ModVillagers.GUNSMITH.get());o=priced();menu=open(v,o);pay(menu,o);
        o.getBaseCostA().setCount(32);
        require(menu.quickMoveStack(player,2).isEmpty() && o.getUses()==0 && player.getInventory().countItem(Items.EMERALD)==24,
                "Direct quick-move rejects changed base price and returns inputs");
        v.discard();
    }
    private static void actualContents() {
        if(MagazineCatalog.INSTANCE.publication().catalog().status()==TaCZMagazinesAdapter.Status.WAITING_FOR_RECIPES) {
            RESULTS.add("Installed magazine adapter waits for native recipe synchronization before stock can resolve");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.OnDatapackSyncEvent(level.getServer().getPlayerList(),null));
            require(MagazineCatalog.INSTANCE.publication().catalog().status()==TaCZMagazinesAdapter.Status.READY,
                    "Native upstream datapack-sync handler generates and publishes magazine recipes");
        }
        var data=Objects.requireNonNull(BlueprintDataManager.SERVER.getBlueprintData("tacz:glock_17"));
        var gun=data.getRecipe().getOutput().copy();
        var fragment=BlueprintFragmentItem.createAnalysis(new ResourceLocation("tacz:glock_17"),AnalysisRewardClass.PROTECTED_PREREQUISITES);
        var v=villager(ModVillagers.BALLISTICS_RESEARCHER.get());
        var o=VillagerPrototypeStock.offer("fragment",new ItemStack(ModItems.SCRAP.get(),8),ItemStack.EMPTY,fragment,3,5);
        var menu=open(v,o);pay(menu,o);menu.clicked(2,0,ClickType.PICKUP,player);
        require(ItemStack.matches(menu.getCarried(),fragment) && BlueprintFragmentItem.identity(menu.getCarried()).orElseThrow().analysisClass().orElseThrow()==AnalysisRewardClass.PROTECTED_PREREQUISITES,"Native protected fragment sale preserves strict item NBT and reward class");player.closeContainer();v.discard();
        v=villager(ModVillagers.GUNSMITH.get());v.setVillagerData(v.getVillagerData().setLevel(2));
        o=VillagerPrototypeStock.offer("gun",new ItemStack(ModItems.SCRAP.get(),24),new ItemStack(Items.IRON_INGOT,4),gun,2,5);
        menu=open(v,o);pay(menu,o);menu.clicked(2,0,ClickType.PICKUP,player);
        require(ItemStack.matches(menu.getCarried(),gun),"Native two-input sale returns exact TaCZ gun");player.closeContainer();
        var capture=MagazineCatalog.INSTANCE.publication().catalog();
        if(capture.status()==TaCZMagazinesAdapter.Status.READY) {
            var family=capture.families().values().stream().filter(d->d.extensionLevel()==0 && d.compatibleGuns().contains(new ResourceLocation("tacz:glock_17"))).findFirst().orElseThrow();
            var support=VillagerPrototypeStock.offer("magazine",new ItemStack(ModItems.SCRAP.get(),6),new ItemStack(Items.IRON_INGOT,2),family.output(),4,5);
            v.setOffers(offers(o,priced(),support));
            require(v.getVillagerData().getLevel()==2 && v.getOffers().size()==3 && ItemStack.matches(v.getOffers().get(2).getResult(),family.output()),"Controlled same-level magazine support is added after two core offers");
            menu=open(v,support);pay(menu,support);menu.clicked(2,0,ClickType.PICKUP,player);
            require(ItemStack.matches(menu.getCarried(),family.output()),"Native compatible empty magazine purchase");player.closeContainer();
        } else require(capture.status()==TaCZMagazinesAdapter.Status.ABSENT,"Optional magazines absent: no linkage or required stock");
        v.discard();
    }
}
