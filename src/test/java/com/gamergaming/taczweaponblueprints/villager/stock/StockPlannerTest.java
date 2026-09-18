package com.gamergaming.taczweaponblueprints.villager.stock;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.gamergaming.taczweaponblueprints.villager.trade.*;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.*;

class StockPlannerTest {
    static ResourceLocation id(String s){return new ResourceLocation("test",s);}
    static TradeResolver.Row row(String slot,int level,Kind kind,Selection selection,Role role,String... targets){
        var choices=Arrays.stream(targets).map(t->new Choice(id(t),1)).toList();
        return new TradeResolver.Row(id(slot),id(slot),Profession.GUNSMITH,level,role,new Output(kind,1,selection==Selection.STUDY_T1?1:selection==Selection.STUDY_T2?2:0,selection,kind==Kind.CURRENCY?List.of():choices),.05F,
                TradeResolver.Status.READY,"ready",Optional.empty(),4,2,choices,Map.of(),List.of(id(slot)));
    }
    static final class Facts implements StockPlanner.Facts {
        final Map<ResourceLocation,ResourceLocation> ammo=new HashMap<>();
        final Map<ResourceLocation,StockPlanner.Supply> magazines=new HashMap<>();
        public Optional<ResourceLocation> ammo(ResourceLocation gun){return Optional.ofNullable(ammo.get(gun));}
        public StockPlanner.Supply magazine(ResourceLocation gun){return magazines.getOrDefault(gun,StockPlanner.Supply.none());}
        public boolean attachmentFits(ResourceLocation gun,ResourceLocation attachment){return !attachment.equals(id("wrong"));}
        Facts gun(String gun,String ammo,String family){this.ammo.put(id(gun),id(ammo));if(family!=null)magazines.put(id(gun),new StockPlanner.Supply(StockPlanner.SupplyStatus.READY,Optional.of(id(family))));return this;}
    }
    static Map<ResourceLocation,List<TradeResolver.Row>> rows(TradeResolver.Row... rows){
        Map<ResourceLocation,List<TradeResolver.Row>> result=new LinkedHashMap<>();for(var r:rows)result.computeIfAbsent(r.slot(),k->new ArrayList<>()).add(r);return result;
    }
    static Map<ResourceLocation,List<TradeResolver.Row>> basic(){return rows(
            row("buy",1,Kind.CURRENCY,Selection.NONE,Role.CORE,"coin"),
            row("ammo",1,Kind.AMMO,Selection.STARTER,Role.CORE,"a","b"),
            row("gun",2,Kind.GUN,Selection.STARTER,Role.CORE,"g1","g2"),
            row("mag",2,Kind.MAGAZINE,Selection.STARTER,Role.SUPPORT,"f1","f2"));}
    static StockPlan plan(Map<ResourceLocation,List<TradeResolver.Row>> rows,Facts facts,long seed){return StockPlanner.create(Profession.GUNSMITH,rows,facts,seed).plan();}
    @Test void pairingIsStableAcrossOrderSeedsAndSerialization(){
        var forward=basic();var reverse=new LinkedHashMap<ResourceLocation,List<TradeResolver.Row>>();
        var keys=new ArrayList<>(forward.keySet());Collections.reverse(keys);keys.forEach(k->reverse.put(k,forward.get(k)));
        var facts=new Facts().gun("g1","a","f1").gun("g2","b","f2");
        for(int seed=0;seed<200;seed++){
            var p=plan(forward,facts,seed);assertEquals(p,plan(reverse,facts,seed));assertTrue(p.reachable());
            var gun=p.entries().get(id("gun"));assertEquals(facts.ammo.get(gun.target()),p.entries().get(id("ammo")).target());
            assertEquals(facts.magazine(gun.target()).family().orElseThrow(),p.entries().get(gun.dependencies().get(1)).target());
            var state=new StockState(Profession.GUNSMITH,seed);state.plan=p;state.requested=3;state.issued.add(id("ammo"));
            var copy=StockState.read(state.save());assertEquals(p,copy.plan);assertEquals(state.issued,copy.issued);assertEquals(3,copy.requested);
        }
    }
    @Test void sharedAmmoDoesNotMergeDistinctMagazineFamilies(){
        var rows=basic();rows.put(id("expert_ammo"),List.of(row("expert_ammo",4,Kind.AMMO,Selection.EXPERT,Role.CORE,"a")));
        rows.put(id("expert_gun"),List.of(row("expert_gun",4,Kind.GUN,Selection.EXPERT,Role.CORE,"g2")));
        rows.put(id("expert_mag"),List.of(row("expert_mag",4,Kind.MAGAZINE,Selection.EXPERT,Role.SUPPORT,"f2")));
        rows.put(id("gun"),List.of(row("gun",2,Kind.GUN,Selection.STARTER,Role.CORE,"g1")));
        var p=plan(rows,new Facts().gun("g1","a","f1").gun("g2","a","f2"),1);
        assertEquals(2,p.entries().values().stream().filter(e->e.kind()==Kind.MAGAZINE).count());
        var same=plan(rows,new Facts().gun("g1","a","f1").gun("g2","a","f1"),1);
        assertEquals(1,same.entries().values().stream().filter(e->e.kind()==Kind.MAGAZINE).count());
        assertEquals(p.entries().get(id("ammo")).target(),p.entries().get(id("expert_ammo")).target());
    }
    @Test void absentPendingUnsupportedAndMissingSupplyHaveDifferentOutcomes(){
        var rows=basic();var facts=new Facts().gun("g1","a",null).gun("g2","b",null);
        assertEquals(0,plan(rows,facts,1).entries().values().stream().filter(e->e.kind()==Kind.MAGAZINE).count());
        facts.magazines.put(id("g1"),new StockPlanner.Supply(StockPlanner.SupplyStatus.PENDING,Optional.empty()));
        assertTrue(StockPlanner.create(Profession.GUNSMITH,rows,facts,1).pending());
        facts.magazines.put(id("g1"),new StockPlanner.Supply(StockPlanner.SupplyStatus.UNAVAILABLE,Optional.empty()));
        assertEquals(id("g2"),plan(rows,facts,1).entries().get(id("gun")).target());
        facts.gun("g2","b","missing");assertFalse(plan(rows,facts,1).entries().containsKey(id("gun")));
        assertTrue(plan(rows,facts,1).reachable()); // Material buy remains usable.
    }
    @Test void compatibleFallbackAndReachabilityUseFinalChoices(){
        var rows=basic();rows.put(id("gun"),List.of(rows.get(id("gun")).get(0),
                new TradeResolver.Row(id("gun"),id("fallback"),Profession.GUNSMITH,2,Role.CORE,
                new Output(Kind.AMMO,1,0,Selection.NONE,List.of(new Choice(id("a"),1))),.05F,TradeResolver.Status.READY,
                "fallback:ready",Optional.empty(),4,5,List.of(new Choice(id("a"),1)),Map.of(),List.of(id("fallback")))));
        var p=plan(rows,new Facts(),1);assertEquals(id("fallback"),p.entries().get(id("gun")).definition());
        rows.remove(id("buy"));assertFalse(plan(rows,new Facts(),1).reachable());
        assertFalse(plan(Map.of(),new Facts(),1).reachable());
    }
    @Test void persistentStudiesIntersectAndAttachmentsRequireAStockedGun(){
        var rows=basic();rows.put(id("study1"),List.of(row("study1",4,Kind.ANALYSIS_FRAGMENTS,Selection.STUDY_T2,Role.CORE,"g1","g2")));
        rows.put(id("study2"),List.of(row("study2",5,Kind.ANALYSIS_FRAGMENTS,Selection.STUDY_T2,Role.CORE,"g2")));
        rows.put(id("attachment"),List.of(row("attachment",3,Kind.ATTACHMENT,Selection.STOCKED,Role.CORE,"wrong","right")));
        var p=plan(rows,new Facts().gun("g1","a",null),2);
        assertEquals(id("g2"),p.entries().get(id("study1")).target());assertEquals(id("g2"),p.entries().get(id("study2")).target());
        assertEquals(id("right"),p.entries().get(id("attachment")).target());
        assertFalse(plan(rows,new Facts(),2).entries().containsKey(id("attachment")));
    }
    @Test void fallbackOrderWinsOverOutputKindAndUnusedPendingFallbackDoesNotBlockStock(){
        var table=basic();var primary=table.get(id("gun")).get(0);
        var alternate=row("gun",2,Kind.AMMO,Selection.NONE,Role.CORE,"a");
        var laterGun=row("gun",2,Kind.GUN,Selection.STARTER,Role.CORE,"g3");
        table.put(id("gun"),List.of(primary,alternate,laterGun));
        var p=plan(table,new Facts().gun("g3","a",null),4);
        assertEquals(Kind.AMMO,p.entries().get(id("gun")).kind());
        var pending=new TradeResolver.Row(primary.slot(),primary.definition(),primary.profession(),primary.level(),primary.role(),primary.output(),
                primary.demandMultiplier(),TradeResolver.Status.PENDING,"catalog_pending",Optional.empty(),4,2,List.of(),Map.of(),List.of());
        table.put(id("gun"),List.of(primary,pending));
        assertFalse(StockPlanner.create(Profession.GUNSMITH,table,new Facts().gun("g1","a",null),4).pending());
        assertTrue(StockPlanner.create(Profession.GUNSMITH,table,new Facts(),4).pending());
    }
    @Test void malformedOrOversizedSavedPlansFailClosed(){
        var state=new StockState(Profession.GUNSMITH,1);state.plan=plan(basic(),new Facts().gun("g1","a",null),1);
        var tag=state.save();var wrong=new ListTag();wrong.add(IntTag.valueOf(1));tag.put("issued",wrong);
        assertThrows(IllegalArgumentException.class,()->StockState.read(tag));
        var mask=state.save();mask.putInt("requested",1024);assertThrows(IllegalArgumentException.class,()->StockState.read(mask));
        var version=state.save();version.putInt("version",2);assertThrows(IllegalArgumentException.class,()->StockState.read(version));
        var dep=StockState.saveEntry(state.plan.entries().get(id("gun")));dep.put("dependencies",wrong);
        assertThrows(IllegalArgumentException.class,()->StockState.readEntry(dep));
        var huge=state.save();var entries=new ListTag();for(int n=0;n<=StockPlan.MAX_ENTRIES;n++)entries.add(dep.copy());huge.put("entries",entries);
        assertThrows(IllegalArgumentException.class,()->StockState.read(huge));
        var missing=state.save();var issued=new ListTag();issued.add(StringTag.valueOf("test:missing"));missing.put("issued",issued);
        assertThrows(IllegalArgumentException.class,()->StockState.read(missing));
    }
    @Test void moreThanTwoCoreFactoriesIsDiagnosedAndFullCandidatePoolIsStable(){
        var rows=basic();rows.put(id("third"),List.of(row("third",1,Kind.AMMO,Selection.NONE,Role.CORE,"a")));
        assertFalse(plan(rows,new Facts(),1).reachable());
        rows=basic();String[] ids=java.util.stream.IntStream.range(0,64).mapToObj(i->"g"+i).toArray(String[]::new);
        rows.put(id("gun"),List.of(row("gun",2,Kind.GUN,Selection.STARTER,Role.CORE,ids)));var facts=new Facts();for(String gun:ids)facts.gun(gun,"a",null);
        var p=plan(rows,facts,89);assertNotNull(p.entries().get(id("gun")));assertEquals(p,plan(rows,facts,89));
    }
    @Test void studyAlternativesShareACompatibleTargetWithoutSkippingEarlierFallbacks(){
        var first=row("study1",1,Kind.ANALYSIS_FRAGMENTS,Selection.STUDY_T1,Role.CORE,"g1");
        var alternate=row("study1",1,Kind.ANALYSIS_FRAGMENTS,Selection.STUDY_T1,Role.CORE,"g2");
        var second=row("study2",2,Kind.ANALYSIS_FRAGMENTS,Selection.STUDY_T1,Role.CORE,"g2");
        var table=rows(first,alternate,second);var p=plan(table,new Facts(),0);
        assertEquals(id("g2"),p.entries().get(id("study1")).target());assertEquals(id("g2"),p.entries().get(id("study2")).target());
        // A preferred non-study fallback wins before a later study fallback.
        table.put(first.slot(),List.of(first,row("study1",1,Kind.RESEARCH_DATA,Selection.NONE,Role.CORE,"note"),alternate));
        p=plan(table,new Facts(),0);assertEquals(Kind.RESEARCH_DATA,p.entries().get(first.slot()).kind());assertEquals(id("g2"),p.entries().get(second.slot()).target());
    }
    @Test void unusedAndPendingStudyFallbacksDoNotOverridePreferredStock(){
        var first=row("study1",1,Kind.ANALYSIS_FRAGMENTS,Selection.STUDY_T1,Role.CORE,"g1");
        var fallback=row("data",2,Kind.ANALYSIS_FRAGMENTS,Selection.STUDY_T1,Role.CORE,"g2");
        var table=rows(first,row("data",2,Kind.RESEARCH_DATA,Selection.NONE,Role.CORE,"note"),fallback);
        var p=plan(table,new Facts(),0);assertEquals(id("g1"),p.entries().get(first.slot()).target());assertEquals(Kind.RESEARCH_DATA,p.entries().get(id("data")).kind());
        var pending=new TradeResolver.Row(first.slot(),first.definition(),first.profession(),first.level(),first.role(),first.output(),first.demandMultiplier(),TradeResolver.Status.PENDING,"catalog_pending",Optional.empty(),4,2,List.of(),Map.of(),List.of());
        table.put(first.slot(),List.of(pending,first));assertTrue(StockPlanner.create(Profession.GUNSMITH,table,new Facts(),0).pending());
    }


    @Test void incompatibleAttachmentUsesItsStudyFallbackAndKeepsSharedTargets(){
        var table=rows(row("study1",1,Kind.ATTACHMENT,Selection.STOCKED,Role.CORE,"wrong"),
                row("study1",1,Kind.ANALYSIS_FRAGMENTS,Selection.STUDY_T1,Role.CORE,"g1","g2"),
                row("study2",2,Kind.ANALYSIS_FRAGMENTS,Selection.STUDY_T1,Role.CORE,"g2"));
        var p=plan(table,new Facts(),7);
        assertTrue(p.reachable());assertEquals(id("g2"),p.entries().get(id("study1")).target());
        assertEquals(p.entries().get(id("study1")).target(),p.entries().get(id("study2")).target());
    }
    @Test void usableAttachmentDoesNotConstrainTheUnusedStudyFallback(){
        var table=basic();table.put(id("attachment"),List.of(
                row("attachment",3,Kind.ATTACHMENT,Selection.STOCKED,Role.CORE,"right"),
                row("attachment",3,Kind.ANALYSIS_FRAGMENTS,Selection.STUDY_T1,Role.CORE,"g1")));
        table.put(id("study"),List.of(row("study",4,Kind.ANALYSIS_FRAGMENTS,Selection.STUDY_T1,Role.CORE,"g2")));
        var p=plan(table,new Facts().gun("g1","a",null),3);
        assertEquals(Kind.ATTACHMENT,p.entries().get(id("attachment")).kind());
        assertEquals(id("g2"),p.entries().get(id("study")).target());
    }
    @Test void missingGunUsesAStudyBeforeALaterUsableGunFallback(){
        var table=basic();table.put(id("gun"),List.of(
                row("gun",2,Kind.GUN,Selection.STARTER,Role.CORE,"missing"),
                row("gun",2,Kind.ANALYSIS_FRAGMENTS,Selection.STUDY_T1,Role.CORE,"g1"),
                row("gun",2,Kind.GUN,Selection.STARTER,Role.CORE,"g2")));
        var p=plan(table,new Facts().gun("g2","a",null),1);
        assertEquals(Kind.ANALYSIS_FRAGMENTS,p.entries().get(id("gun")).kind());
        assertEquals(id("g1"),p.entries().get(id("gun")).target());
    }
    @Test void incompatibleStudyCanStillFallBackToACompleteGun(){
        var table=basic();table.put(id("gun"),List.of(
                row("gun",2,Kind.ANALYSIS_FRAGMENTS,Selection.STUDY_T1,Role.CORE,"g1"),
                row("gun",2,Kind.GUN,Selection.STARTER,Role.CORE,"g2")));
        table.put(id("study"),List.of(row("study",3,Kind.ANALYSIS_FRAGMENTS,Selection.STUDY_T1,Role.CORE,"g3")));
        var p=plan(table,new Facts().gun("g2","a",null),1);
        assertEquals(Kind.GUN,p.entries().get(id("gun")).kind());
        assertEquals(id("g3"),p.entries().get(id("study")).target());
        assertEquals(id("a"),p.entries().get(id("ammo")).target());
    }
    @Test void PendingRequiredGunSupplyDoesNotFallThroughToStudy(){
        var table=basic();table.put(id("gun"),List.of(table.get(id("gun")).get(0),
                row("gun",2,Kind.ANALYSIS_FRAGMENTS,Selection.STUDY_T1,Role.CORE,"g1")));
        var facts=new Facts().gun("g1","a",null);
        facts.magazines.put(id("g1"),new StockPlanner.Supply(StockPlanner.SupplyStatus.PENDING,Optional.empty()));
        assertTrue(StockPlanner.create(Profession.GUNSMITH,table,facts,1).pending());
    }
}
