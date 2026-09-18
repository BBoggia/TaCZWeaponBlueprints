package com.gamergaming.taczweaponblueprints.villager.trade;

import java.util.*;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.villager.VillagerTradePricing;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeFormatTest.*;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.*;

class TradeResolverTest {
    static class Content implements TradeResolver.Content {
        boolean ready=true; Set<ResourceLocation> blocked=new HashSet<>(); int limit=64;
        public boolean ready(){return ready;} public boolean modLoaded(String id){return false;}
        public int stackLimit(ResourceLocation id){return limit;}
        public String unavailable(Output o,ResourceLocation id){return blocked.contains(id)?"content_removed":"";}
        public List<Choice> magazineCandidates(Output o){return List.of();}
    }
    private TradeResolver.Resolution resolve(TradeSettings settings, Content content, TradeDefinition... definitions) {
        var defs=new HashMap<ResourceLocation,TradeDefinition>();for(var d:definitions)defs.put(d.id(),d);
        return TradeResolver.resolve(new TradeSnapshot(defs),settings,id("minecraft:iron_ingot"),content);
    }
    static TradeSettings defaults(){return new BlueprintConfig().villagerSettings();}
    @Test void overlappingPricesRetainDenominationsAndProtectedMinimum() throws Exception {
        var d=edit("test:d",o->{o.getAsJsonObject("price").addProperty("count",24);o.add("secondary",JsonParser.parseString("{\"item\":\"minecraft:iron_ingot\",\"count\":4}"));});
        var row=resolve(defaults(),new Content(),d).slots().get(d.slot());assertTrue(row.ready());
        var price=row.price().orElseThrow().amounts();assertEquals(24,price.originalPrimary());assertEquals(4,price.protectedSecondary());
        assertEquals(new VillagerTradePricing.Counts(28,0,22),price.charged());
        assertEquals(id("minecraft:iron_ingot"),row.price().orElseThrow().secondaryItem().orElseThrow());
    }
    @Test void lowStackCurrencyAndOverflowAreUnavailableWithoutClamping() throws Exception {
        var content=new Content();content.limit=1;var d=read("test:d",JSON);
        assertFalse(resolve(defaults(),content,d).slots().get(d.slot()).ready());
        content.limit=64;var huge=edit("test:huge",o->{o.getAsJsonObject("price").addProperty("count",64);o.add("secondary",JsonParser.parseString("{\"item\":\"minecraft:iron_ingot\",\"count\":12}"));});
        assertFalse(resolve(defaults(),content,huge).slots().get(huge.slot()).ready());
    }
    @Test void missingOptionalContentCanUseOrderedFallbackButPendingCatalogCannot() throws Exception {
        var primary=edit("test:primary",o->{o.add("required_mods",JsonParser.parseString("[\"optional_mod\"]"));o.add("fallbacks",JsonParser.parseString("[\"test:fallback\"]"));});
        var fallback=edit("test:fallback",o->o.addProperty("role","fallback"));var content=new Content();
        var row=resolve(defaults(),content,primary,fallback).slots().get(primary.slot());assertEquals(fallback.id(),row.definition());assertTrue(row.ready());
        primary=edit("test:primary",o->o.add("fallbacks",JsonParser.parseString("[\"test:fallback\"]")));content.ready=false;
        row=resolve(defaults(),content,primary,fallback).slots().get(primary.slot());assertEquals(TradeResolver.Status.PENDING,row.status());assertEquals(primary.id(),row.definition());
    }
    @Test void establishedTargetsCannotSwitchToFallbacksAndCurrentRestrictionsOverrideRetainedDefinitions() throws Exception {
        var original=edit("test:primary",o->o.add("fallbacks",JsonParser.parseString("[\"test:fallback\"]")));
        var fallback=edit("test:fallback",o->{o.addProperty("role","fallback");o.getAsJsonObject("output").getAsJsonArray("candidates").get(0).getAsJsonObject().addProperty("id","test:other");});
        var content=new Content();assertTrue(resolve(defaults(),content,original,fallback).retains(original.slot(),original.id(),id("test:round")));
        content.blocked.add(id("test:round"));var after=resolve(defaults(),content,original,fallback);
        assertTrue(after.slots().get(original.slot()).ready());assertFalse(after.retains(original.slot(),original.id(),id("test:round")));
        assertFalse(after.retains(original.slot(),original.id(),id("test:other")));
    }
    @Test void suppressedSelfCurrencyPurchasesAndDisabledCategoriesDiagnoseNoviceDeadEnds() throws Exception {
        var d=edit("test:material",o->{o.getAsJsonObject("price").addProperty("item","minecraft:iron_ingot");o.getAsJsonObject("output").addProperty("kind","currency");o.getAsJsonObject("output").remove("candidates");});
        var result=resolve(defaults(),new Content(),d);assertEquals("material_is_currency",result.slots().get(d.slot()).reason());
        assertEquals("no_positive_xp_at_level_1",result.acquisition().get(Profession.GUNSMITH));
        var config=new BlueprintConfig();config.villagerAmmoSales.validateAndSet(false);config.initializeLoadedSnapshots();d=read("test:d",JSON);
        assertEquals("category_disabled",resolve(config.villagerSettings(),new Content(),d).slots().get(d.slot()).reason());
        var expert=edit("test:expert",o->{o.addProperty("level",4);o.addProperty("slot","test:expert");});
        assertEquals("no_positive_xp_at_level_1",resolve(defaults(),new Content(),expert).acquisition().get(Profession.GUNSMITH));
        assertEquals("ready",resolve(defaults(),new Content(),d).acquisition().get(Profession.GUNSMITH));
    }
    @Test void professionDisableCannotBeBypassedByFallbackAndFloorCannotBeWeakenedByData() throws Exception {
        var d=edit("test:d",o->o.addProperty("minimum_basis_points",0));var c=new BlueprintConfig();c.enableGunsmith.validateAndSet(false);c.initializeLoadedSnapshots();
        assertEquals("profession_disabled",resolve(c.villagerSettings(),new Content(),d).slots().get(d.slot()).reason());
        assertEquals(3,resolve(defaults(),new Content(),d).slots().get(d.slot()).price().orElseThrow().amounts().charged().minimumPrimary());
    }
    @Test void dynamicallyExpandedCandidatesShareOneAggregateBudget() throws Exception {
        var definitions = new ArrayList<TradeDefinition>();
        for (int i = 0; i < 65; i++) {
            final int n = i;
            definitions.add(edit("test:mag" + i, o -> {
                o.addProperty("slot", "test:mag" + n);
                o.getAsJsonObject("output").addProperty("kind", "magazine");
                o.getAsJsonObject("output").remove("candidates");
            }));
        }
        var content = new Content() {
            @Override public List<Choice> magazineCandidates(Output output) {
                return java.util.stream.IntStream.range(0, 64).mapToObj(i -> new Choice(id("test:f" + i), 1)).toList();
            }
        };
        assertTrue(resolve(defaults(), content, definitions.subList(0, 64).toArray(TradeDefinition[]::new)).slots().values().stream().allMatch(TradeResolver.Row::ready));
        assertTrue(resolve(defaults(), content, definitions.toArray(TradeDefinition[]::new)).slots().values().stream()
                .allMatch(r -> r.reason().equals("aggregate_resolved_candidate_limit") && r.candidates().isEmpty()));
        var all=new HashMap<ResourceLocation,TradeDefinition>();definitions.forEach(d->all.put(d.id(),d));
        assertTrue(TradeResolver.alternatives(new TradeSnapshot(all),defaults(),id("minecraft:iron_ingot"),content).values().stream().flatMap(List::stream)
                .allMatch(r->r.reason().equals("aggregate_resolved_candidate_limit")&&r.candidates().isEmpty()));
    }
    @Test void priceScalingRoundsUpProtectsMaterialsAndRejectsExtremeIntegers() {
        assertEquals(1,VillagerTradePricing.scale(1,1,64));assertEquals(64,VillagerTradePricing.scale(64,10000,64));
        assertThrows(IllegalArgumentException.class,()->VillagerTradePricing.scale(Integer.MAX_VALUE,Integer.MAX_VALUE,64));
        assertThrows(IllegalArgumentException.class,()->VillagerTradePricing.scale(4,0,64));
        assertThrows(IllegalArgumentException.class,()->VillagerTradePricing.scale(-1,10000,64));
        var p=VillagerTradePricing.price(24,4,true,64,64,12500,7500);
        assertEquals(30,p.originalPrimary());assertEquals(4,p.protectedSecondary());assertEquals(new VillagerTradePricing.Counts(34,0,27),p.charged());
        assertThrows(IllegalArgumentException.class,()->VillagerTradePricing.price(1,2,false,64,1,10000,7500));
    }
}
