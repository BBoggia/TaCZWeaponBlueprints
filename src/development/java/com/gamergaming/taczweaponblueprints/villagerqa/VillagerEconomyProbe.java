package com.gamergaming.taczweaponblueprints.villagerqa;

import java.nio.file.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.ProgressionPolicyAccessService;
import com.gamergaming.taczweaponblueprints.villager.*;
import com.gamergaming.taczweaponblueprints.villager.stock.*;
import com.gamergaming.taczweaponblueprints.villager.trade.*;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.*;
import static com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract.Action.*;

/** Native merchant, Analyzer and player capability checks; excluded from production artifacts. */
@EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID)
public final class VillagerEconomyProbe {
    private static final List<String> checks=new ArrayList<>();
    private static ServerLevel level;
    private static int sequence=10;
    private static boolean deferred;
    @SubscribeEvent public static void started(ServerStartedEvent event){
        var root=VillagerPrototypeStock.root();if(root==null||!Boolean.getBoolean("tacz.villagerQA.economy"))return;
        var server=event.getServer();server.execute(()->{
            try{
                level=server.overworld();NeoForge.EVENT_BUS.post(new OnDatapackSyncEvent(server.getPlayerList(),null));StockPublicationCoordinator.settle();
                var customer=player("MarketBuyer");var recipient=player("ResearchRecipient");
                var v=villager(ModVillagers.GUNSMITH.get(),2);var offer=find(v,Kind.GUN);
                var target=binding(offer).entry().target();var bought=buy(customer,v,offer);
                require(PhysicalWeaponProvenance.from(bought).orElseThrow().origin()==PhysicalWeaponProvenance.Origin.TRADED,"Purchased gun has explicit traded provenance");
                require(!PhysicalWeaponProvenance.stampLootGenerated(bought,ResourceLocation.parse("test:loot")),"Loot stamping cannot launder a traded gun");
                require(!data(customer).hasBlueprint(target.toString()),"Native purchase does not grant knowledge");
                var restored=ItemStack.parse(level.registryAccess(), bought.save(level.registryAccess())).orElseThrow();require(ItemStack.matches(bought,restored),"Traded gun survives item save/load");
                customer.closeContainer();var analyzer=analyzer(recipient,bought.copy());
                require(analyzer.preview().weaponOrigin()==BlueprintRecyclerPreview.WeaponOrigin.TRADED,"Transferred gun previews as purchased for a second player");
                require(!act(analyzer,recipient,RECOVER_POINTS).successful(),"Purchased gun cannot recover verified-found currency");
                require(!act(analyzer,recipient,REVERSE_ENGINEER).successful()&&!analyzer.getSlot(0).getItem().isEmpty(),"Unpaid analysis rejects without consuming the gun");
                fund(recipient);analyzer.refreshAuthoritativePreview(recipient);
                require(act(analyzer,recipient,REVERSE_ENGINEER).successful(),"Paid Tier 3 Analyzer processes a purchased starter gun");
                var blueprint=analyzer.getSlot(1).getItem().copy();analyzer.getSlot(1).set(ItemStack.EMPTY);
                protectedBlueprint(blueprint,"Purchased-gun analysis");
                require(!BlueprintRecyclingService.recycle(recipient,blueprint).successful(),"Analysis reward cannot be recycled");
                var claims=Set.copyOf(data(recipient).getResearchPointAwardLedger().claims());
                require(data(recipient).hasDiscoveredBlueprint(target.toString())&&!claims.isEmpty(),"First analysis records discovery and its finite award claim");
                recipient.closeContainer();var loaded=bought.copy();IGun.getIGunOrNull(loaded).setCurrentAmmoCount(loaded,3);
                loaded.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Modified market weapon"));
                analyzer=analyzer(recipient,loaded);fund(recipient);analyzer.refreshAuthoritativePreview(recipient);
                int paid=analyzer.preview().pointCost();
                require(act(analyzer,recipient,REVERSE_ENGINEER).successful(),"Loaded, renamed purchased gun uses existing paid analysis handling");
                require(data(recipient).getResearchPointAwardLedger().claims().equals(claims),"Repeated purchased-gun analysis cannot repeat discovery award claims");
                require(recipient.getInventory().countItem(ModItems.SCRAP.get())==64-paid&&recipient.getInventory().countItem(Items.IRON_INGOT)==62,"Repeat analysis charges the displayed currency and materials without another discovery payout");
                recipient.closeContainer();var inserted=bought.copy();net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, inserted, tag -> tag.put("TaCZMag_StoredMagazine",new net.minecraft.nbt.CompoundTag()));
                analyzer=analyzer(recipient,inserted);fund(recipient);analyzer.refreshAuthoritativePreview(recipient);
                require(!act(analyzer,recipient,REVERSE_ENGINEER).successful()&&ItemStack.matches(analyzer.getSlot(0).getItem(),inserted),"Inserted magazine rejects analysis without consuming the gun");
                recipient.closeContainer();fragments(customer,recipient);currencies(customer);strict();
                optInFixtures(root);deferred=true;
            }catch(Throwable error){checks.add("FAIL: "+error);TaCZWeaponBlueprints.LOGGER.error("Economy QA failed",error);}
            finally{if(!deferred)finish(root);}
        });
    }
    private static void finish(Path root){try{Files.write(root.resolve("economy-result.txt"),checks);}catch(Exception e){throw new RuntimeException(e);}level.getServer().halt(false);}
    private static com.google.gson.JsonObject bundled(String path)throws Exception{
        try(var reader=new java.io.InputStreamReader(Objects.requireNonNull(TaCZWeaponBlueprints.class.getResourceAsStream("/data/taczweaponblueprints/taczweaponblueprints/"+path+".json")),java.nio.charset.StandardCharsets.UTF_8)){
            return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private static void writeFixture(Path pack,String path,com.google.gson.JsonObject json)throws Exception{
        var file=pack.resolve("data/taczweaponblueprints/taczweaponblueprints/"+path+".json");Files.createDirectories(file.getParent());Files.writeString(file,json.toString());
    }
    private static void candidate(com.google.gson.JsonObject row,ResourceLocation target){var list=new com.google.gson.JsonArray();var entry=new com.google.gson.JsonObject();entry.addProperty("id",target.toString());entry.addProperty("weight",1);list.add(entry);row.getAsJsonObject("output").add("candidates",list);}
    private static void optInFixtures(Path root)throws Exception{
        var server=level.getServer();var pack=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("villager-economy-qa");Files.createDirectories(pack);Files.writeString(pack.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":15,\"description\":\"Disposable villager economy QA\"}}");
        var context=ProgressionPolicyAccessService.acquire(ProgressionPolicyAccessService.Mode.CURRENT_ONLY).orElseThrow();
        var tier3=context.catalog().blueprints().keySet().stream().filter(id->context.catalog().blueprints().get(id).getKind()==BlueprintKind.GUN)
                .filter(id->context.policyFor(id).map(p->p.researchWorkbenchTier().level()).orElse(1)==3).filter(id->StockCatalog.current().ammo(id).isPresent())
                .sorted().findFirst().orElseThrow();
        var full=bundled("villager_trades/ballistics_researcher/dossier");full.getAsJsonObject("output").addProperty("kind","blueprint");candidate(full,tier3);writeFixture(pack,"villager_trades/ballistics_researcher/dossier",full);
        var gun=bundled("villager_trades/gunsmith/expert_gun");gun.getAsJsonObject("output").addProperty("tier",3);candidate(gun,tier3);writeFixture(pack,"villager_trades/gunsmith/expert_gun",gun);
        var ammo=bundled("villager_trades/gunsmith/expert_ammo");candidate(ammo,StockCatalog.current().ammo(tier3).orElseThrow());writeFixture(pack,"villager_trades/gunsmith/expert_ammo",ammo);
        var mag=StockCatalog.current().magazine(tier3);
        if(mag.status()==StockPlanner.SupplyStatus.READY){var support=bundled("villager_trades/gunsmith/magazine_support_4");support.getAsJsonObject("output").addProperty("tier",3);candidate(support,mag.family().orElseThrow());writeFixture(pack,"villager_trades/gunsmith/magazine_support_4",support);}
        var note=bundled("villager_trades/ballistics_researcher/note");note.getAsJsonObject("output").addProperty("allow_legacy_exchange",true);writeFixture(pack,"villager_trades/ballistics_researcher/note",note);
        server.getPackRepository().reload();var packs=new ArrayList<>(server.getPackRepository().getSelectedIds());if(!packs.contains("file/villager-economy-qa"))packs.add("file/villager-economy-qa");
        server.reloadResources(packs).whenCompleteAsync((ignored,failure)->{
            try{
                if(failure!=null)throw new AssertionError(failure);NeoForge.EVENT_BUS.post(new OnDatapackSyncEvent(server.getPlayerList(),null));StockPublicationCoordinator.settle();
                var c=ModConfigs.BLUEPRINT;c.villagerMaterialPurchases.validateAndSet(true);c.onSyncServer();
                require(villager(ModVillagers.BALLISTICS_RESEARCHER.get(),5).getOffers().stream().noneMatch(o->binding(o).entry().kind()==Kind.BLUEPRINT),"Full blueprints remain disabled by default even with authored rows");
                require(villager(ModVillagers.GUNSMITH.get(),4).getOffers().stream().noneMatch(o->binding(o).entry().target().equals(tier3)),"Tier 3 fixture remains excluded at the default tier cap");
                c.villagerBlueprintSales.validateAndSet(true);c.villagerMaximumGunTier.validateAndSet(3);c.onSyncServer();
                var p=player("CompleteBuyer");var v=villager(ModVillagers.BALLISTICS_RESEARCHER.get(),5);var blueprint=buy(p,v,find(v,Kind.BLUEPRINT));protectedBlueprint(blueprint,"Opt-in whole blueprint");
                require(BlueprintItem.getProvenance(blueprint).orElseThrow().source()==BlueprintProvenance.Source.TRADED,"Whole blueprint records explicit traded provenance");
                var receiver=player("CompleteRecipient");var learning=BlueprintLearningService.learnPhysicalBlueprint(receiver,tier3,blueprint);
                require(!learning.successful()&&!blueprint.isEmpty()&&!data(receiver).hasBlueprint(tier3.toString()),"Transferred whole blueprint cannot bypass missing prerequisites");
                require(!BlueprintRecyclingService.recycle(receiver,blueprint).successful(),"Purchased whole blueprint cannot recycle");
                for(var id:BlueprintDataManager.SERVER.getBlueprintDataMap().keySet())if(!id.equals(tier3))data(receiver).addBlueprint(id.toString());
                BlueprintLearningService.migrateLegacyUnlocks(BlueprintDataManager.SERVER,data(receiver));
                require(BlueprintLearningService.learnPhysicalBlueprint(receiver,tier3,blueprint).successful()&&blueprint.isEmpty(),"Purchased whole blueprint learns and consumes after prerequisites are met");
                crafting(receiver,tier3);
                var guns=villager(ModVillagers.GUNSMITH.get(),4);var high=guns.getOffers().stream().filter(o->binding(o).entry().kind()==Kind.GUN&&binding(o).entry().target().equals(tier3)).findFirst().orElseThrow();
                require(!binding(high).entry().dependencies().isEmpty(),"Opt-in Tier 3 gun has compatible supplies");buy(p,guns,high);
                legacyData();legacyAnalyzerFixture(root,pack,packs);return;
            }catch(Throwable error){checks.add("FAIL: "+error);TaCZWeaponBlueprints.LOGGER.error("Opt-in economy QA failed",error);}
            finish(root);
        },server);
    }
    private static void crafting(ServerPlayer p,ResourceLocation target){
        var recipe=BlueprintDataManager.SERVER.getBlueprintDataMap().get(target).getRecipeId();
        for(int tier:new int[]{1,3}){
            p.closeContainer();var pos=new BlockPos(-2,85,tier);var station=ResourceLocation.parse("taczweaponblueprints:workbench_lvl"+tier);
            require(com.gamergaming.taczweaponblueprints.block.CraftingWorkbenchBlock.placeCompleteStructure(level,pos,net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(station).defaultBlockState()),"Complete crafting Workbench placed");
            var menu=new com.tacz.guns.inventory.GunSmithTableMenu(++sequence,p.getInventory(),station);p.containerMenu=menu;
            ((com.gamergaming.taczweaponblueprints.compat.tacz.TaCZWorkbenchMenuBridge)menu).taczweaponblueprints$attachWorkbenchContext(new com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchContext(pos,level.dimension().location(),station,
                    com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier.fromLevel(tier),com.gamergaming.taczweaponblueprints.progression.workbench.ResearchInteractionMode.CRAFTING,(long)sequence+1));
            require(com.gamergaming.taczweaponblueprints.progression.workbench.CraftingWorkbenchAuthority.valid(p,menu),"Crafting menu is bound to a valid physical station");
            var result=CraftingEligibilityService.evaluate(p,menu,recipe);
            require(result.allowed()==(tier==3),"Learned purchased Tier 3 blueprint retains crafting workstation requirements: tier "+tier+" / "+result.status());
        }
        p.closeContainer();
    }
    private static void legacyData(){
        var c=ModConfigs.BLUEPRINT;c.researchDataBehavior.validateAndSet(com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPolicy.Behavior.LEGACY_CURRENCY);c.onSyncServer();
        var rows=TradeRuntime.inspect().resolution().slots();var note=rows.get(ResourceLocation.parse("taczweaponblueprints:ballistics_researcher/note"));
        require(note.ready(),"Explicit same-currency legacy Research Note sale passes conservative pricing");
        require(rows.values().stream().filter(r->r.output().kind()==Kind.RESEARCH_DATA&&!r.output().allowLegacyExchange()).noneMatch(TradeResolver.Row::ready),"Legacy Data rows without explicit opt-in stay unavailable");
        c.villagerPricePercent.validateAndSet(1);c.onSyncServer();require(!TradeRuntime.inspect().resolution().slots().get(note.slot()).ready(),"Cheap legacy exchange is rejected at its minimum discounted price");
        c.villagerPricePercent.validateAndSet(100);c.researchCurrencyKind.validateAndSet(ResearchCurrencyPolicy.Kind.BANKED_RP);c.onSyncServer();require(!TradeRuntime.inspect().resolution().slots().get(note.slot()).ready(),"Legacy redemption never compares banked RP to Emerald prices");
        c.researchCurrencyKind.validateAndSet(ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM);c.researchDataBehavior.validateAndSet(com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPolicy.Behavior.ANALYSIS);c.onSyncServer();
    }
    private static void legacyAnalyzerFixture(Path root,Path pack,List<String> packs)throws Exception{
        var profile=bundled("research_profiles/duplicate_recovery");var analyzer=new com.google.gson.JsonObject();analyzer.addProperty("mode","legacy");profile.add("analyzer",analyzer);profile.getAsJsonObject("reverse_engineering").getAsJsonObject("cost").addProperty("points",3);writeFixture(pack,"research_profiles/duplicate_recovery",profile);
        var server=level.getServer();server.reloadResources(packs).whenCompleteAsync((ignored,failure)->{
            try{
                if(failure!=null)throw new AssertionError(failure);NeoForge.EVENT_BUS.post(new OnDatapackSyncEvent(server.getPlayerList(),null));StockPublicationCoordinator.settle();
                var p=player("LegacyBuyer");var v=villager(ModVillagers.GUNSMITH.get(),2);var gun=buy(p,v,find(v,Kind.GUN));var menu=analyzer(p,gun);fund(p);menu.refreshAuthoritativePreview(p);
                require(act(menu,p,REVERSE_ENGINEER).successful(),"Legacy paid reverse engineering accepts a purchased gun");protectedBlueprint(menu.getSlot(1).getItem(),"Legacy purchased-gun analysis");checks.add("PASS");
            }catch(Throwable error){checks.add("FAIL: "+error);TaCZWeaponBlueprints.LOGGER.error("Legacy economy QA failed",error);}finally{finish(root);}
        },server);
    }
    private static void fragments(ServerPlayer customer,ServerPlayer recipient)throws Exception{
        var v=villager(ModVillagers.BALLISTICS_RESEARCHER.get(),2);var offer=find(v,Kind.ANALYSIS_FRAGMENTS);var target=binding(offer).entry().target();
        ItemStack output=ItemStack.EMPTY;
        for(int n=0;n<3;n++){
            var stack=buy(customer,v,offer);var id=BlueprintFragmentItem.identity(stack).orElseThrow();
            require(id.analysisClass().orElseThrow()==com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisRewardClass.PROTECTED_PREREQUISITES,"Native fragment purchase preserves the strict protected reward schema");
            var menu=analyzer(recipient,stack);require(act(menu,recipient,ARCHIVE_FRAGMENTS).successful(),"Transferred purchased fragments archive through the native Analyzer");
            if(!menu.getSlot(1).getItem().isEmpty()){output=menu.getSlot(1).getItem().copy();menu.getSlot(1).set(ItemStack.EMPTY);}
            recipient.closeContainer();
        }
        protectedBlueprint(output,"Fragment reconstruction");require(!data(recipient).hasBlueprint(target.toString()),"Purchase and archive do not automatically learn the target");
        var attempted=output.copy();var rejected=BlueprintLearningService.learnPhysicalBlueprint(recipient,target,attempted);
        require(rejected.successful()?attempted.isEmpty():ItemStack.matches(attempted,output),"Learning consumes only successful eligible blueprints");
        if(!rejected.successful()){
            for(var id:BlueprintDataManager.SERVER.getBlueprintDataMap().keySet())if(!id.equals(target))data(recipient).addBlueprint(id.toString());
            BlueprintLearningService.migrateLegacyUnlocks(BlueprintDataManager.SERVER,data(recipient));
            require(BlueprintLearningService.learnPhysicalBlueprint(recipient,target,output).successful(),"Protected blueprint learns after prerequisites are met");
        }else require(data(recipient).hasBlueprint(target.toString()),"Eligible root blueprint learns through the normal service");
    }
    private static void currencies(ServerPlayer p)throws Exception{
        var c=ModConfigs.BLUEPRINT;var v=villager(ModVillagers.GUNSMITH.get(),2);
        var copper=v.getOffers().stream().filter(o->binding(o).entry().kind()==Kind.CURRENCY&&o.getBaseCostA().is(Items.COPPER_INGOT)).findFirst().orElseThrow();copper.increaseUses();int uses=copper.getUses();var slot=binding(copper).entry().slot();
        c.researchCurrencyItem.validateAndSet("minecraft:iron_ingot");c.onSyncServer();
        var current=v.getOffers().stream().filter(o->binding(o).entry().slot().equals(slot)).findFirst().orElseThrow();
        require(current.getResult().is(Items.IRON_INGOT)&&current.getUses()==uses,"Currency publication updates material payouts without resetting uses");
        var custom=villager(ModVillagers.GUNSMITH.get(),2);require(custom.getOffers().stream().filter(o->binding(o).entry().kind()==Kind.CURRENCY).noneMatch(o->o.getBaseCostA().is(Items.IRON_INGOT)),"Currency-for-itself material purchases are excluded");
        var gun=find(custom,Kind.GUN);gun.setSpecialPriceDiff(-999);var floor=((OwnedVillagerOffer)gun).taczweaponblueprints$terms().minimumPrimary();
        require(gun.getCostA().is(Items.IRON_INGOT)&&gun.getCostA().getCount()==floor&&gun.getCostB().isEmpty(),"Overlapping currency/material price retains its protected floor under extreme discount");buy(p,custom,gun);
        c.researchCurrencyKind.validateAndSet(ResearchCurrencyPolicy.Kind.BANKED_RP);c.onSyncServer();
        data(p).setResearchPoints(100);var bank=villager(ModVillagers.GUNSMITH.get(),2);var bankGun=find(bank,Kind.GUN);require(bankGun.getCostA().is(Items.EMERALD),"Banked RP mode prices native trades in Emeralds");buy(p,bank,bankGun);require(data(p).getResearchPoints()==100,"Native trade never debits banked RP");
        c.villagerCurrencyMode.validateAndSet(TradeSettings.CurrencyMode.FIXED_ITEM);c.villagerFixedCurrency.validateAndSet("test:missing");c.onSyncServer();
        require(villager(ModVillagers.GUNSMITH.get(),1).getOffers().isEmpty(),"Invalid fixed currency cannot create free or unusable stock");
        c.villagerCurrencyMode.validateAndSet(TradeSettings.CurrencyMode.FOLLOW_RESEARCH_ITEM);c.researchCurrencyKind.validateAndSet(ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM);c.researchCurrencyItem.validateAndSet("taczweaponblueprints:scrap");c.onSyncServer();
    }
    private static void strict(){
        ModConfigs.BLUEPRINT.villagerMaterialPurchases.validateAndSet(false);ModConfigs.BLUEPRINT.onSyncServer();
        for(var profession:List.of(ModVillagers.GUNSMITH.get(),ModVillagers.BALLISTICS_RESEARCHER.get())){
            var v=villager(profession,1);require(!v.getOffers().isEmpty()&&v.getOffers().stream().anyMatch(o->o.getXp()>0),"Strict economy retains a Novice XP path: "+profession.name());
            require(v.getOffers().stream().noneMatch(o->binding(o).entry().kind()==Kind.CURRENCY),"Strict economy disables renewable material payouts");
        }
    }
    private static void protectedBlueprint(ItemStack stack,String name){var p=BlueprintItem.getProvenance(stack).orElseThrow();require(!p.recyclable()&&p.learningMode()==PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES,name+" produces a prerequisite-bound nonrecyclable blueprint");}
    private static void fund(ServerPlayer p){p.getInventory().add(new ItemStack(ModItems.SCRAP.get(),64));p.getInventory().add(new ItemStack(Items.IRON_INGOT,64));}
    private static BlueprintRecyclerMenu analyzer(ServerPlayer p,ItemStack input){p.closeContainer();p.getInventory().clearContent();var pos=new BlockPos(1,85,0);require(com.gamergaming.taczweaponblueprints.block.BlueprintRecyclerBlock.isComplete(level,pos)||com.gamergaming.taczweaponblueprints.block.BlueprintRecyclerBlock.placeCompleteStructure(level,pos,ModBlocks.EXPERIMENTAL_BLUEPRINT_ANALYZER.get().defaultBlockState()),"Complete physical Analyzer placed");var m=BlueprintRecyclerMenu.server(++sequence,p.getInventory(),level,pos);p.containerMenu=m;m.getSlot(0).set(input);m.refreshAuthoritativePreview(p);return m;}
    private static BlueprintRecyclerActionContract.ActionResult act(BlueprintRecyclerMenu m,ServerPlayer p,BlueprintRecyclerActionContract.Action action){m.refreshAuthoritativePreview(p);var q=m.preview();return m.handleRecyclerAction(p,action,q.inputId().orElseThrow(),q.inputCount(),q.stateToken());}
    private static ItemStack buy(ServerPlayer p,Villager v,MerchantOffer offer){p.closeContainer();p.getInventory().clearContent();v.setTradingPlayer(p);var m=new MerchantMenu(++sequence,p.getInventory(),v);p.containerMenu=m;int uses=offer.getUses();m.setSelectionHint(v.getOffers().indexOf(offer));m.getSlot(0).set(offer.getCostA().copy());m.getSlot(1).set(offer.getCostB().copy());m.clicked(2,0,ClickType.PICKUP,p);var out=m.getCarried().copy();require(ItemStack.matches(out,offer.getResult())&&offer.getUses()==uses+1&&m.getSlot(0).getItem().isEmpty()&&m.getSlot(1).getItem().isEmpty(),"Native purchase consumes the displayed payment exactly once");m.setCarried(ItemStack.EMPTY);p.closeContainer();return out;}
    private static com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData data(ServerPlayer p){return com.gamergaming.taczweaponblueprints.init.ModAttachments.recipeData(p).orElseThrow();}
    private static ServerPlayer player(String name){var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));p.moveTo(.5,85,.5);p.setHealth(20);p.getAbilities().instabuild=false;return p;}
    private static Villager villager(VillagerProfession profession,int tier){var v=new Villager(EntityType.VILLAGER,level);v.moveTo(.5,85,.5);v.setNoAi(true);v.setVillagerData(v.getVillagerData().setProfession(profession));level.addFreshEntity(v);v.getOffers();for(int n=2;n<=tier;n++)try{ObfuscationReflectionHelper.findMethod(Villager.class,"increaseMerchantCareer").invoke(v);}catch(Exception e){throw new RuntimeException(e);}return v;}
    private static StockOfferBinding binding(MerchantOffer o){return ((OwnedVillagerOffer)o).taczweaponblueprints$binding();}
    private static MerchantOffer find(Villager v,Kind kind){return v.getOffers().stream().filter(o->binding(o).entry().kind()==kind).findFirst().orElseThrow();}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);checks.add(message);}
}
