package com.gamergaming.taczweaponblueprints.verification;

import com.gamergaming.taczweaponblueprints.block.*;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.capabilities.*;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.progression.fragment.*;
import com.gamergaming.taczweaponblueprints.villager.*;
import com.gamergaming.taczweaponblueprints.villager.stock.*;
import com.tacz.guns.api.item.IGun;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import static com.gamergaming.taczweaponblueprints.verification.CoreVerification.*;

/** Feature acceptance uses production server menus and a connected NeoForge client. */
final class FeatureVerification {
    static boolean enabled() { return Boolean.getBoolean("wrb.verifyFeatures"); }
    static final BlockPos STATION = new BlockPos(0, 85, 0);
    static List<ResourceLocation> targets;
    static int price, unlocks;
    static BlueprintRecyclerPreview analysis;
    static Villager merchant;
    static MerchantOffer trade;
    static final List<Villager> fixtures = new ArrayList<>();
    static com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData data() { return ModAttachments.recipeData(player).orElseThrow(); }
    static void tick() {
        try {
            if (stage == 0) {
                player.setGameMode(GameType.SURVIVAL);
                player.teleportTo(server.overworld(), .5, 85, 2.5, Set.of(), 0, 0);
                player.setNoGravity(true);
                server.getPlayerList().deop(player.getGameProfile());
                targets = new ArrayList<>();
                var context = BlueprintWeaponAnalysisService.context();
                var config = ModConfigs.BLUEPRINT.progressionSnapshot();
                java.nio.file.Files.writeString(java.nio.file.Path.of("feature-catalog.json"),BlueprintResearchCatalogExporter.export(context.research(),context.catalog(),config.activeProfileId()));
                for (int tier=1;tier<=3;tier++) {
                    final int wanted=tier;
                    targets.add(context.catalog().keySet().stream().sorted().filter(id -> {
                        var quote=BlueprintAnalyzerPolicyResolver.resolve(context,config.activeProfileId(),id,AnalyzerTier.TIER_1,
                                com.gamergaming.taczweaponblueprints.resource.research.BlueprintAnalyzerPolicy.Origin.FOUND,config.foundWeaponRecoveryMode());
                        return quote.status()==AnalyzerPolicyQuote.Status.CONFIGURED && quote.weaponTier().orElseThrow().level()==wanted;
                    }).findFirst().orElseThrow());
                }
                analyzerChecks();
                villagerChecks();
                reset();
                // Isolate path/currency behavior from the separately tested item-material planner.
                ModConfigs.BLUEPRINT.enableResearchPointAwards=new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition<>(new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean(false),()->false);
                ModConfigs.BLUEPRINT.researchCostMode=new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition<>(
                        new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum<>(ResearchCostMode.POINTS_ONLY),()->ResearchCostMode.POINTS_ONLY);
                ModConfigs.BLUEPRINT.ammoResearch.validateAndSet(ResearchDomainMode.ENABLED);
                ModConfigs.BLUEPRINT.attachmentResearch.validateAndSet(ResearchDomainMode.ENABLED);
                ModConfigs.BLUEPRINT.onUpdateServer(new me.fzzyhmstrs.fzzy_config.event.api.ServerUpdateContext(server,player));
                data().replaceProgression(Set.of(),context.catalog().keySet().stream().map(Object::toString).toList(),137);
                for(int i=0;i<30;i++)player.getInventory().setItem(i,new ItemStack(ModItems.SCRAP.get(),64));
                player.inventoryMenu.broadcastFullState();
                NetworkHandler.syncPlayerProgressionData(player);
                placeResearch();send(101);
            } else if (stage == 102) {
                var menu=(ResearchBenchMenu)player.containerMenu;
                require(menu.selectedBlueprint().filter(TARGET::equals).isPresent(),"network selection reaches authoritative Research Bench");
                require(menu.preview().researchable(),"funded path is researchable: "+menu.preview());
                price=menu.preview().pointCost();unlocks=menu.preview().unlockCount();
                require(unlocks>1 && price>0,"research preview prices a multi-blueprint path");send(103);
            } else if (stage == 104) {
                require(data().getLearnedBlueprints().contains(TARGET.toString()),"network path purchase learns target");
                require(data().getLearnedBlueprints().size()==unlocks,"path purchase learns complete quoted route");
                require(player.getInventory().countItem(ModItems.SCRAP.get())==1920-price && data().getResearchPoints()==137,"path purchase deducts exact Scrap without touching banked RP: Scrap="+player.getInventory().countItem(ModItems.SCRAP.get())+", RP="+data().getResearchPoints());
                var menu=(ResearchBenchMenu)player.containerMenu;
                require(!menu.handleAction(player,ResearchBenchResearchAction.RESEARCH,Optional.of(TARGET)).orElseThrow().successful(),"replayed learned route rejected");
                require(player.getInventory().countItem(ModItems.SCRAP.get())==1920-price,"replay does not charge twice");
                reset();openAnalyzer(2,gun(targets.getFirst(),true));analysis=((BlueprintRecyclerMenu)player.containerMenu).preview();send(105);
            } else if(stage==106) {
                var menu=(BlueprintRecyclerMenu)player.containerMenu;
                require(menu.inputStack().isEmpty() && menu.outputStack().getItem() instanceof BlueprintItem,"network analysis consumes gun and produces blueprint");
                require(player.getInventory().countItem(ModItems.SCRAP.get())==100-analysis.pointCost() && player.getInventory().countItem(Items.IRON_INGOT)==6,"network analysis pays quoted currency and materials");
                require(player.getInventory().items.stream().filter(s->s.getItem() instanceof com.tacz.guns.api.item.IAmmo).mapToInt(ItemStack::getCount).sum()==6,"analysis returns five loaded rounds and chambered round");
                require(!menu.handleRecyclerAction(player,BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER,analysis.inputId().orElseThrow(),analysis.inputCount(),analysis.stateToken()).successful(),"stale analysis token cannot repeat output or ammo refund");
                player.closeContainer();player.getInventory().clearContent();
                merchant=villager(ModVillagers.BALLISTICS_RESEARCHER.get(),1);
                trade=merchant.getOffers().stream().filter(o->!o.isOutOfStock()).findFirst().orElseThrow();
                merchant.setTradingPlayer(player);
                merchant.openTradingScreen(player,net.minecraft.network.chat.Component.literal("Ballistics Researcher QA"),1);
                var trading=(MerchantMenu)player.containerMenu;trading.setSelectionHint(merchant.getOffers().indexOf(trade));
                trading.getSlot(0).set(trade.getCostA().copy());trading.getSlot(1).set(trade.getCostB().copy());trading.broadcastChanges();send(107);
            } else if(stage==108) {
                var menu=(MerchantMenu)player.containerMenu;
                require(ItemStack.matches(menu.getCarried(),trade.getResult()) && trade.getUses()==1,"native client merchant click delivers owned offer exactly once");
                require(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(),"native merchant click consumes both required payments");
                menu.setCarried(ItemStack.EMPTY);player.closeContainer();
                server.getPlayerList().op(player.getGameProfile());send(109);
            } else if(stage==110) {
                server.getPlayerList().deop(player.getGameProfile());send(111);
            } else if(stage==112) {
                var active=com.gamergaming.taczweaponblueprints.research.designer.server.DesignerCaptureRequests.class.getDeclaredField("ACTIVE");active.setAccessible(true);
                require(!((Map<?,?>)active.get(null)).containsKey(player.getUUID()),"server rejects raw Designer capture from non-operator");
                fixtures.forEach(Villager::discard);
                pass();
            }
        } catch(Throwable error) { fail(error); }
    }
    static void acknowledge(int value) {
        try { require(value==stage && value>0,"feature client acknowledged stage "+value);stage=value+1;delay=30; }
        catch(Throwable error){fail(error);}
    }
    static void reset() {
        player.closeContainer();player.getInventory().clearContent();
        data().deserializeNBT(new CompoundTag());
        data().replaceProgression(Set.of(),targets.stream().map(Object::toString).toList(),137);
        player.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT,8));
        player.getInventory().setItem(1,new ItemStack(ModItems.SCRAP.get(),50));
        player.getInventory().setItem(2,new ItemStack(ModItems.SCRAP.get(),50));
    }
    static ItemStack gun(ResourceLocation id,boolean loaded) {
        var item=BuiltInRegistries.ITEM.stream().filter(i->i instanceof IGun).findFirst().orElseThrow();
        var stack=new ItemStack(item);var gun=(IGun)item;gun.setGunId(stack,id);
        PhysicalWeaponProvenance.stampLootGenerated(stack,ResourceLocation.parse("minecraft:chests/simple_dungeon"));
        if(loaded){gun.setCurrentAmmoCount(stack,5);gun.setBulletInBarrel(stack,true);}return stack;
    }
    static void clearStation() {
        player.closeContainer();
        for(int x=-1;x<=2;x++)for(int z=-1;z<=1;z++)server.overworld().removeBlock(STATION.offset(x,0,z),false);
    }
    static BlueprintRecyclerMenu openAnalyzer(int tier,ItemStack input) {
        clearStation();
        var block=switch(tier){case 1->ModBlocks.BLUEPRINT_RECYCLER.get();case 2->ModBlocks.ADVANCED_BLUEPRINT_ANALYZER.get();default->ModBlocks.EXPERIMENTAL_BLUEPRINT_ANALYZER.get();};
        require(BlueprintRecyclerBlock.placeCompleteStructure(server.overworld(),STATION,block.defaultBlockState()),"Analyzer structure tier "+tier);
        useStation();var menu=(BlueprintRecyclerMenu)player.containerMenu;
        menu.getSlot(0).set(input);menu.refreshAuthoritativePreview(player);menu.broadcastChanges();return menu;
    }
    static void placeResearch() {
        clearStation();require(ResearchBenchBlock.placeCompleteStructure(server.overworld(),STATION,ModBlocks.EXPERIMENTAL_RESEARCH_BENCH.get().defaultBlockState()),"T3 Research Bench structure");useStation();
        require(player.containerMenu instanceof ResearchBenchMenu && player.containerMenu.stillValid(player),"Research Bench opens valid physical menu");
    }
    static void useStation(){server.overworld().getBlockState(STATION).useWithoutItem(server.overworld(),player,new BlockHitResult(Vec3.atCenterOf(STATION),Direction.UP,STATION,false));}
    static void analyzerChecks() {
        int[][] chances={{70,30,10},{100,70,30},{100,100,80}};
        for(int station=1;station<=3;station++)for(int weapon=1;weapon<=3;weapon++){
            reset();var menu=openAnalyzer(station,gun(targets.get(weapon-1),false));var preview=menu.preview();
            var odds=preview.presentation().weapon().orElseThrow().outcomes().orElseThrow();
            require(preview.actionable() && preview.presentation().stationTier()==station && preview.presentation().weapon().orElseThrow().tier()==weapon,"Analyzer tier/weapon preview "+station+"/"+weapon);
            require(odds.blueprintWeight()*100==chances[station-1][weapon-1]*(odds.blueprintWeight()+odds.fragmentWeight()+odds.noResultWeight()),"Analyzer odds preserved "+station+"/"+weapon);
        }
        reset();var menu=openAnalyzer(2,gun(targets.getFirst(),false));var preview=menu.preview();
        menu.getSlot(1).set(new ItemStack(Items.STONE));menu.refreshAuthoritativePreview(player);
        require(!act(menu,BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER).successful() && menu.inputStack().getCount()==1 && player.getInventory().countItem(ModItems.SCRAP.get())==100,"occupied Analyzer output preserves input and payment");
        reset();menu=openAnalyzer(1,new ItemStack(ModItems.RESEARCH_NOTE.get(),3));
        var analyzed=act(menu,BlueprintRecyclerActionContract.Action.ANALYZE_DATA);
        require(analyzed.successful() && menu.inputStack().getCount()==2 && !menu.outputStack().isEmpty(),"default Research Data analysis consumes one note and creates a reward: "+analyzed);
        var behavior=ModConfigs.BLUEPRINT.researchDataBehavior.get();
        try {
            ModConfigs.BLUEPRINT.researchDataBehavior.validateAndSet(ResearchDataPolicy.Behavior.LEGACY_CURRENCY);
            ModConfigs.BLUEPRINT.initializeLoadedSnapshots();
            reset();menu=openAnalyzer(1,new ItemStack(ModItems.RESEARCH_NOTE.get(),3));
            require(act(menu,BlueprintRecyclerActionContract.Action.REDEEM_STACK).successful() && menu.inputStack().isEmpty() && menu.outputStack().is(ModItems.SCRAP.get()),"legacy Research Data stack redeems to physical Scrap");
        } finally {ModConfigs.BLUEPRINT.researchDataBehavior.validateAndSet(behavior);ModConfigs.BLUEPRINT.initializeLoadedSnapshots();}
        reset();menu=openAnalyzer(1,BlueprintFragmentItem.createAnalysis(targets.get(2),AnalysisRewardClass.PROTECTED_PREREQUISITES));
        data().applyAnalysisFragmentMutation(new AnalysisFragmentMutation(PlayerProgressValueMutation.Operation.COMMIT,targets.get(2).toString(),AnalysisFragmentCounts.EMPTY,new AnalysisFragmentCounts(14,0,0,0)));
        menu.refreshAuthoritativePreview(player);
        require(act(menu,BlueprintRecyclerActionContract.Action.ARCHIVE_FRAGMENTS).successful() && menu.outputStack().getItem() instanceof BlueprintItem && data().getArchivedAnalysisFragments().isEmpty(),"protected analysis fragments reconstruct blueprint and empty archive");
        reset();data().replaceProgression(Set.of(targets.getFirst().toString()),Set.of(targets.getFirst().toString()),137);
        menu=openAnalyzer(1,BlueprintItem.createBlueprint(targets.getFirst().toString()));
        require(act(menu,BlueprintRecyclerActionContract.Action.RECYCLE).successful() && menu.outputStack().is(ModItems.SCRAP.get()),"duplicate blueprint recycles to Scrap");
        reset();var protectedGun=gun(targets.getFirst(),false);
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,protectedGun,t->t.put("TaCZMag_StoredMagazine",new CompoundTag()));
        menu=openAnalyzer(2,protectedGun);
        require(!act(menu,BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER).successful() && ItemStack.matches(menu.inputStack(),protectedGun),"stored magazine prevents destruction even without optional addon");
    }
    static BlueprintRecyclerActionContract.ActionResult act(BlueprintRecyclerMenu menu,BlueprintRecyclerActionContract.Action action){
        var p=menu.preview();return menu.handleRecyclerAction(player,action,p.inputId().orElseThrow(),p.inputCount(),p.stateToken());
    }
    static Villager villager(net.minecraft.world.entity.npc.VillagerProfession profession,int level)throws Exception {
        var v=new Villager(EntityType.VILLAGER,server.overworld());v.moveTo(2.5,85,2.5,0,0);v.setNoAi(true);v.setNoGravity(true);
        v.setVillagerData(v.getVillagerData().setProfession(profession));server.overworld().addFreshEntity(v);fixtures.add(v);v.getOffers();
        var increase=Villager.class.getDeclaredMethod("increaseMerchantCareer");increase.setAccessible(true);
        for(int i=1;i<level;i++)increase.invoke(v);return v;
    }
    static void villagerChecks() throws Exception {
        for(boolean gunsmith:List.of(true,false)) {
            var pos=STATION.offset(4,0,gunsmith?0:3);
            var block=gunsmith?ModBlocks.GUNSMITH_STATION.get():ModBlocks.BALLISTICS_DESK.get();
            server.overworld().setBlockAndUpdate(pos,block.defaultBlockState());
            var novice=new Villager(EntityType.VILLAGER,server.overworld());novice.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);novice.setNoAi(true);novice.setNoGravity(true);
            server.overworld().addFreshEntity(novice);fixtures.add(novice);
            novice.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.POTENTIAL_JOB_SITE,GlobalPos.of(server.overworld().dimension(),pos));
            require(net.minecraft.world.entity.ai.behavior.AssignProfessionFromJobSite.create().tryStart(server.overworld(),novice,server.overworld().getGameTime())
                    && novice.getVillagerData().getProfession()==(gunsmith?ModVillagers.GUNSMITH.get():ModVillagers.BALLISTICS_RESEARCHER.get()),"native AI assigns workstation profession: "+gunsmith);
        }
        require(StockCatalog.current().coherent() && !StockPublicationCoordinator.pending(),"villager catalog published coherently");
        for(var profession:List.of(ModVillagers.GUNSMITH.get(),ModVillagers.BALLISTICS_RESEARCHER.get())){
            var v=villager(profession,5);var offers=v.getOffers();
            require(offers.size()>=10 && offers.stream().noneMatch(MerchantOffer::isOutOfStock),"five-level career produces active offers: "+profession.name());
            for(int tier=1;tier<=5;tier++){final int level=tier;require(offers.stream().anyMatch(o->((OwnedVillagerOffer)o).taczweaponblueprints$binding().entry().level()==level),"career contains tier "+tier+": "+profession.name());}
            var tag=new CompoundTag();v.saveWithoutId(tag);var restored=new Villager(EntityType.VILLAGER,server.overworld());restored.load(tag);
            require(restored.getOffers().size()==offers.size(),"saved villager retains native offers: "+profession.name());
            for(int i=0;i<offers.size();i++)require(ItemStack.matches(offers.get(i).getResult(),restored.getOffers().get(i).getResult()) && Objects.equals(((OwnedVillagerOffer)offers.get(i)).taczweaponblueprints$binding(),((OwnedVillagerOffer)restored.getOffers().get(i)).taczweaponblueprints$binding()),"saved stock identity "+profession.name()+"/"+i);
            offers.getFirst().setToOutOfStock();v.restock();
            require(v.getOffers().stream().noneMatch(MerchantOffer::isOutOfStock),"native restock restores owned offers: "+profession.name());
        }
        require(net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(ModBlocks.GUNSMITH_STATION.get().defaultBlockState()).filter(h->h.is(ModVillagers.GUNSMITH_POI.getKey())).isPresent(),"gunsmith jobsite registered");
        require(net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(ModBlocks.BALLISTICS_DESK.get().defaultBlockState()).filter(h->h.is(ModVillagers.BALLISTICS_RESEARCHER_POI.getKey())).isPresent(),"ballistics researcher jobsite registered");
    }
}
