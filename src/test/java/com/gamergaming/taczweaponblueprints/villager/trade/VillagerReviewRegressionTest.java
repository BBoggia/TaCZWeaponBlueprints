package com.gamergaming.taczweaponblueprints.villager.trade;
import java.util.*;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;
import com.google.gson.JsonParser;
import com.gamergaming.taczweaponblueprints.villager.stock.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.*;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeFormatTest.*;
class VillagerReviewRegressionTest {
 static final StockPlanner.Facts FACTS=new StockPlanner.Facts(){
  public Optional<ResourceLocation> ammo(ResourceLocation gun){return Optional.empty();}
  public StockPlanner.Supply magazine(ResourceLocation gun){return StockPlanner.Supply.none();}
  public boolean attachmentFits(ResourceLocation gun,ResourceLocation attachment){return false;}
 };
 @Test void disabledIntermediateFallbackMustNotProduceLeafStock()throws Exception {
  var root=edit("test:root",o->{o.add("required_mods",JsonParser.parseString("[\"missing_mod\"]"));o.add("fallbacks",JsonParser.parseString("[\"test:middle\"]"));});
  var middle=edit("test:middle",o->{o.addProperty("role","fallback");o.addProperty("enabled",false);o.add("fallbacks",JsonParser.parseString("[\"test:leaf\"]"));});
  var leaf=edit("test:leaf",o->o.addProperty("role","fallback"));
  var snapshot=new TradeSnapshot(Map.of(root.id(),root,middle.id(),middle,leaf.id(),leaf));var content=new TradeResolverTest.Content();var settings=TradeResolverTest.defaults();var currency=id("minecraft:emerald");
  var visible=TradeResolver.resolve(snapshot,settings,currency,content).slots().get(root.slot());
  assertFalse(visible.ready());
  var plan=StockPlanner.create(Profession.GUNSMITH,TradeResolver.alternatives(snapshot,settings,currency,content),FACTS,0).plan();
  assertFalse(plan.entries().containsKey(root.slot()),"A disabled intermediate fallback should block its descendants just as the operator report does");
 }
 @Test void validStudyFallbackMustRemainSelectable()throws Exception {
  var root=edit("test:root",o->{o.add("required_mods",JsonParser.parseString("[\"missing_mod\"]"));o.add("fallbacks",JsonParser.parseString("[\"test:study\"]"));o.getAsJsonObject("output").addProperty("kind","analysis_fragments");o.getAsJsonObject("output").addProperty("selection","study_t1");o.getAsJsonObject("output").addProperty("tier",1);});
  var study=edit("test:study",o->{o.addProperty("role","fallback");o.getAsJsonObject("output").addProperty("kind","analysis_fragments");o.getAsJsonObject("output").addProperty("selection","study_t1");o.getAsJsonObject("output").addProperty("tier",1);});
  var snapshot=new TradeSnapshot(Map.of(root.id(),root,study.id(),study));var content=new TradeResolverTest.Content();var settings=TradeResolverTest.defaults();var currency=id("minecraft:emerald");
  var visible=TradeResolver.resolve(snapshot,settings,currency,content).slots().get(root.slot());assertTrue(visible.ready());
  var plan=StockPlanner.create(Profession.GUNSMITH,TradeResolver.alternatives(snapshot,settings,currency,content),FACTS,0).plan();
  assertTrue(plan.entries().containsKey(root.slot()),"A valid selected study fallback should produce research stock");
 }
 @Test void acceptedCurrencyIdMustNotThrowWhenReadingTradeSettings(){
  var config=new com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig();
  String value="test:"+"a".repeat(195);
  config.villagerFixedCurrency.validateAndSet(value);
  assertEquals(value,config.villagerFixedCurrency.get(),"The config accepted this canonical 200-character currency ID");
  assertDoesNotThrow(config::initializeLoadedSnapshots,"An accepted setting must not throw while publishing config snapshots, even while fixed currency is unused");
 }

 @Test void sharedFallbackRemainsReachableThroughAnEnabledParent()throws Exception {
  var root=edit("test:root",o->{o.add("required_mods",JsonParser.parseString("[\"missing_mod\"]"));o.add("fallbacks",JsonParser.parseString("[\"test:middle\",\"test:leaf\"]"));});
  var middle=edit("test:middle",o->{o.addProperty("role","fallback");o.addProperty("enabled",false);o.add("fallbacks",JsonParser.parseString("[\"test:leaf\"]"));});
  var leaf=edit("test:leaf",o->o.addProperty("role","fallback"));
  var snapshot=new TradeSnapshot(Map.of(root.id(),root,middle.id(),middle,leaf.id(),leaf));
  var table=TradeResolver.alternatives(snapshot,TradeResolverTest.defaults(),id("minecraft:emerald"),new TradeResolverTest.Content());
  assertEquals(leaf.id(),StockPlanner.create(Profession.GUNSMITH,table,FACTS,0).plan().entries().get(root.slot()).definition());
 }
 @Test void acceptedCurrencyBoundariesPublishAndSurviveSavedStock(){
  for(var mode:TradeSettings.CurrencyMode.values())for(int length:new int[]{192,193,255,256}) {
   var config=new com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig();String value="test:"+"a".repeat(length-5);
   config.villagerFixedCurrency.validateAndSet(value);config.villagerCurrencyMode.validateAndSet(mode);
   assertDoesNotThrow(config::initializeLoadedSnapshots);assertEquals(value,config.villagerSettings().fixedCurrency().toString());
   var entry=new StockPlan.Entry(id("test:slot"),id("test:slot"),id("test:def"),id(value),Kind.CURRENCY,1,1,Role.CORE,Selection.NONE,List.of());
   assertEquals(entry,StockState.readEntry(StockState.saveEntry(entry)));
  }
  var config=new com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig();config.villagerFixedCurrency.validateAndSet("test:"+"a".repeat(252));
  assertTrue(config.villagerFixedCurrency.get().length()<=256);assertDoesNotThrow(config::initializeLoadedSnapshots);
 }

}
