package com.gamergaming.taczweaponblueprints.villager.stock;

import java.util.*;
import com.gamergaming.taczweaponblueprints.villager.trade.*;
import net.minecraft.resources.ResourceLocation;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.*;

/** One bounded plan is selected before either of the level's native factories runs. */
public final class StockPlanner {
    private StockPlanner() {}
    public enum SupplyStatus { NONE, READY, PENDING, UNAVAILABLE }
    public record Supply(SupplyStatus status, Optional<ResourceLocation> family) {
        public Supply { if ((status == SupplyStatus.READY) != family.isPresent()) throw new IllegalArgumentException("Invalid magazine requirement"); }
        public static Supply none() { return new Supply(SupplyStatus.NONE, Optional.empty()); }
    }
    public interface Facts {
        Optional<ResourceLocation> ammo(ResourceLocation gun);
        Supply magazine(ResourceLocation gun);
        boolean attachmentFits(ResourceLocation gun, ResourceLocation attachment);
    }
    public record Result(boolean pending, StockPlan plan) {}
    public static Result create(Profession profession, Map<ResourceLocation, List<TradeResolver.Row>> alternatives,
            Facts facts, long seed) {
        var core = alternatives.entrySet().stream().filter(e -> !e.getValue().isEmpty()
                && e.getValue().get(0).profession() == profession && e.getValue().get(0).role() == Role.CORE)
                .sorted(Map.Entry.comparingByKey()).toList();
        Map<ResourceLocation, StockPlan.Entry> chosen = new TreeMap<>(); Map<ResourceLocation, String> excluded = new TreeMap<>();
        for (int level=1;level<=5;level++) {
            final int l=level;
            if (core.stream().filter(e -> e.getValue().get(0).level()==l).count()>2)
                return new Result(false, new StockPlan(profession, Map.of(), Map.of(core.get(0).getKey(), "more_than_two_core_slots_per_level"), false));
        }
        // Guns choose valid ammo and magazine supply together. Lower-level ammo may be planned first in the UI.
        for (var slot : core.stream().sorted(Comparator.comparingInt(e -> e.getValue().get(0).level())).toList()) {
            if (chosen.containsKey(slot.getKey())) continue;
            if (slot.getValue().stream().noneMatch(r -> r.output().kind() == Kind.GUN)) continue;
            boolean selected=false;
            for (var row : slot.getValue()) {
                if (row.status()==TradeResolver.Status.PENDING) return new Result(true,null);
                if (!row.ready()) continue;
                // Resolve shared study targets after the preferred equipment is known.
                // Do not skip a study preference to take a later gun fallback.
                if (isStudy(row)) break;
                if (row.output().kind()!=Kind.GUN) {
                    var target=ordinaryTarget(row,facts,chosen,Map.of(),seed);
                    if(target.isPresent()){chosen.put(row.slot(),entry(row,row.slot(),target.orElseThrow(),List.of()));selected=true;break;}
                    continue;
                }
                var result=selectGun(row,core,alternatives,facts,chosen,seed);
                if(result==GunSelection.PENDING)return new Result(true,null);
                if(result==GunSelection.SELECTED){selected=true;break;}
            }
            if(!selected) excluded.put(slot.getKey(),"no_complete_gun_supply");
        }
        var studies = studyTargets(core, alternatives, facts, chosen, seed);
        // Remaining ammo, attachments, studies and ordinary items use the saved equipment choices.
        for(var slot:core){
            if(chosen.containsKey(slot.getKey()))continue;
            for(var row:slot.getValue()){
                if(row.status()==TradeResolver.Status.PENDING)return new Result(true,null);
                if(!row.ready())continue;
                if(row.output().kind()==Kind.GUN){
                    var result=selectGun(row,core,alternatives,facts,chosen,seed);
                    if(result==GunSelection.PENDING)return new Result(true,null);
                    if(result==GunSelection.SELECTED){excluded.remove(row.slot());break;}
                    continue;
                }
                var target=ordinaryTarget(row,facts,chosen,studies,seed);if(target.isEmpty())continue;
                // Attachment validity is rechecked against any actually stocked compatible gun after native selection.
                chosen.put(row.slot(),entry(row,row.slot(),target.orElseThrow(),List.of()));excluded.remove(row.slot());break;
            }
            if(!chosen.containsKey(slot.getKey()))excluded.putIfAbsent(slot.getKey(),"no_compatible_candidate_or_fallback");
        }
        excluded.keySet().removeAll(chosen.keySet());
        boolean reachable=true;
        for(int level=1;level<5;level++){
            final int at=level;
            if(chosen.values().stream().noneMatch(e -> e.role()==Role.CORE && e.level()<=at
                    && alternatives.get(e.template()).stream().anyMatch(r -> r.definition().equals(e.definition()) && r.xp()>0)))reachable=false;
        }
        return new Result(false,new StockPlan(profession,chosen,excluded,reachable));
    }
    /** Choose one shared target using effective, ordered alternatives rather than unavailable roots. */
    private static Map<Selection, ResourceLocation> studyTargets(
            List<Map.Entry<ResourceLocation,List<TradeResolver.Row>>> core,
            Map<ResourceLocation,List<TradeResolver.Row>> alternatives,Facts facts,
            Map<ResourceLocation,StockPlan.Entry> chosen,long seed) {
        Map<Selection,ResourceLocation> result=new EnumMap<>(Selection.class);
        for(var study:List.of(Selection.STUDY_T1,Selection.STUDY_T2)) {
            List<List<TradeResolver.Row>> slots=new ArrayList<>();
            for(var slot:core) {
                if(chosen.containsKey(slot.getKey()))continue;
                List<TradeResolver.Row> ready=new ArrayList<>();
                for(var row:slot.getValue()) {
                    if(row.status()==TradeResolver.Status.PENDING)break;
                    if(!row.ready())continue;
                    if(row.output().kind()==Kind.GUN){
                        var candidates=gunCandidates(row,core,alternatives,facts,chosen);
                        if(candidates.pending())break;
                        if(candidates.choices().isEmpty())continue;
                    }else if(!isStudy(row)&&ordinaryTarget(row,facts,chosen,Map.of(),seed).isEmpty())continue;
                    ready.add(row);
                }
                // Only a usable non-study preference can make its study fallback irrelevant.
                if(!ready.isEmpty()&&ready.get(0).output().selection()==study)slots.add(ready);
            }
            if(slots.isEmpty())continue;
            var candidates=new TreeSet<ResourceLocation>();
            slots.forEach(rows->rows.stream().filter(r->r.output().selection()==study)
                    .forEach(r->r.candidates().forEach(c->candidates.add(c.id()))));
            List<Integer> best=null;List<Choice> choices=new ArrayList<>();
            for(var target:candidates) {
                List<Integer> rank=new ArrayList<>();Choice weight=null;
                for(var rows:slots) {
                    int selected=-1;
                    for(int i=0;i<rows.size();i++) {
                        var row=rows.get(i);
                        if(row.output().selection()!=study||has(row,target)) {
                            selected=i;
                            if(weight==null&&row.output().selection()==study)
                                weight=row.candidates().stream().filter(c->c.id().equals(target)).findFirst().orElseThrow();
                            break;
                        }
                    }
                    if(selected<0)break;
                    rank.add(selected);
                }
                if(rank.size()!=slots.size()||weight==null)continue;
                int order=best==null?-1:compareRanks(rank,best);
                if(order<0){best=rank;choices.clear();}
                if(order<=0)choices.add(weight);
            }
            choose(choices,seed,slots.get(0).get(0).slot()).ifPresent(id->result.put(study,id));
        }
        return result;
    }
    private static boolean isStudy(TradeResolver.Row row){
        return row.output().selection()==Selection.STUDY_T1||row.output().selection()==Selection.STUDY_T2;
    }
    private enum GunSelection { SELECTED, UNAVAILABLE, PENDING }
    private record GunCandidates(boolean pending,List<Choice> choices) {}
    private static GunCandidates gunCandidates(TradeResolver.Row row,
            List<Map.Entry<ResourceLocation,List<TradeResolver.Row>>> core,
            Map<ResourceLocation,List<TradeResolver.Row>> alternatives,Facts facts,
            Map<ResourceLocation,StockPlan.Entry> chosen){
        var choices=new ArrayList<Choice>();
        for(var candidate:row.candidates()){
            var gun=candidate.id();var supply=facts.magazine(gun);
            if(supply.status()==SupplyStatus.PENDING)return new GunCandidates(true,List.of());
            if(supply.status()==SupplyStatus.UNAVAILABLE||facts.ammo(gun).isEmpty())continue;
            if(chosen.values().stream().anyMatch(e->e.kind()==Kind.GUN&&e.target().equals(gun)))continue;
            if(ammoRow(core,chosen,row,facts.ammo(gun).orElseThrow())==null)continue;
            if(supply.status()==SupplyStatus.READY&&supportRow(alternatives,row,supply.family().orElseThrow())==null
                    &&familySlot(chosen,supply.family().orElseThrow())==null)continue;
            choices.add(candidate);
        }
        return new GunCandidates(false,choices);
    }
    private static GunSelection selectGun(TradeResolver.Row row,
            List<Map.Entry<ResourceLocation,List<TradeResolver.Row>>> core,
            Map<ResourceLocation,List<TradeResolver.Row>> alternatives,Facts facts,
            Map<ResourceLocation,StockPlan.Entry> chosen,long seed){
        var candidates=gunCandidates(row,core,alternatives,facts,chosen);
        if(candidates.pending())return GunSelection.PENDING;
        var target=choose(candidates.choices(),seed,row.slot());if(target.isEmpty())return GunSelection.UNAVAILABLE;
        var gun=target.orElseThrow();var ammoId=facts.ammo(gun).orElseThrow();var ammo=ammoRow(core,chosen,row,ammoId);
        if(!chosen.containsKey(ammo.slot()))chosen.put(ammo.slot(),entry(ammo,ammo.slot(),ammoId,List.of()));
        List<ResourceLocation> dependencies=new ArrayList<>();dependencies.add(ammo.slot());
        var supply=facts.magazine(gun);
        if(supply.status()==SupplyStatus.READY){
            var family=supply.family().orElseThrow();var existing=familySlot(chosen,family);
            if(existing==null){var support=supportRow(alternatives,row,family);var key=supportSlot(support.slot(),family);
                chosen.put(key,entry(support,key,family,List.of()));existing=key;}
            dependencies.add(existing);
        }
        chosen.put(row.slot(),entry(row,row.slot(),gun,dependencies));return GunSelection.SELECTED;
    }
    private static int compareRanks(List<Integer> a,List<Integer> b) {
        for(int i=0;i<a.size();i++){int order=Integer.compare(a.get(i),b.get(i));if(order!=0)return order;}
        return 0;
    }
    private static Optional<ResourceLocation> ordinaryTarget(TradeResolver.Row row,Facts facts,
            Map<ResourceLocation,StockPlan.Entry> chosen,Map<Selection,ResourceLocation> studies,long seed){
        List<Choice> candidates=row.candidates();
        if(row.output().kind()==Kind.AMMO && row.output().selection()!=Selection.NONE){
            Set<ResourceLocation> ammo=new HashSet<>();
            chosen.values().stream().filter(e -> e.kind()==Kind.GUN
                    && (row.output().selection()==Selection.STOCKED || row.output().selection()==e.selection()))
                    .forEach(e -> facts.ammo(e.target()).ifPresent(ammo::add));
            if(!ammo.isEmpty())candidates=candidates.stream().filter(c -> ammo.contains(c.id())).toList();
            else if(!row.reason().startsWith("fallback:") && row.output().selection()!=Selection.STOCKED)candidates=List.of();
        }
        if(row.output().kind()==Kind.ATTACHMENT){
            var guns=chosen.values().stream().filter(e -> e.kind()==Kind.GUN && e.level()<=row.level()).toList();
            candidates=candidates.stream().filter(c -> guns.stream().anyMatch(g -> facts.attachmentFits(g.target(),c.id()))).toList();
        }
        if(row.output().selection()==Selection.STUDY_T1 || row.output().selection()==Selection.STUDY_T2){
            var target=studies.get(row.output().selection());candidates=candidates.stream().filter(c -> c.id().equals(target)).toList();
        }
        return choose(candidates,seed,row.slot());
    }
    private static StockPlan.Entry entry(TradeResolver.Row row,ResourceLocation slot,ResourceLocation target,List<ResourceLocation> dependencies){
        return new StockPlan.Entry(slot,row.slot(),row.definition(),target,row.output().kind(),row.output().count(),row.level(),row.role(),row.output().selection(),dependencies);
    }
    private static TradeResolver.Row ammoRow(List<Map.Entry<ResourceLocation,List<TradeResolver.Row>>> core,
            Map<ResourceLocation,StockPlan.Entry> chosen,TradeResolver.Row gun,ResourceLocation ammo){
        if(gun.output().selection()==Selection.STOCKED){
            for(var e:chosen.values())if(e.kind()==Kind.AMMO && e.target().equals(ammo) && e.level()<=gun.level())
                return core.stream().filter(s->s.getKey().equals(e.template())).flatMap(s->s.getValue().stream()).filter(r->r.definition().equals(e.definition()) && r.ready()).findFirst().orElse(null);
            return null;
        }
        return core.stream().flatMap(s->s.getValue().stream()).filter(r->r.ready() && r.output().kind()==Kind.AMMO
                && !r.slot().equals(gun.slot()) && r.output().selection()==gun.output().selection() && r.level()<=gun.level() && has(r,ammo)
                && (!chosen.containsKey(r.slot()) || chosen.get(r.slot()).target().equals(ammo)))
                .min(Comparator.comparingInt(TradeResolver.Row::level).thenComparing(TradeResolver.Row::slot)).orElse(null);
    }
    private static TradeResolver.Row supportRow(Map<ResourceLocation,List<TradeResolver.Row>> alternatives,TradeResolver.Row gun,ResourceLocation family){
        return alternatives.values().stream().flatMap(List::stream).filter(r->r.ready() && r.profession()==gun.profession()
                && r.role()==Role.SUPPORT && r.level()==gun.level() && has(r,family))
                .min(Comparator.comparing(TradeResolver.Row::slot)).filter(r -> supportSlot(r.slot(),family).toString().length()<=TradeLimits.ID_LENGTH).orElse(null);
    }
    private static ResourceLocation supportSlot(ResourceLocation slot,ResourceLocation family){return ResourceLocation.fromNamespaceAndPath(slot.getNamespace(),slot.getPath()+"/"+family.getNamespace()+"/"+family.getPath());}
    private static ResourceLocation familySlot(Map<ResourceLocation,StockPlan.Entry> chosen,ResourceLocation family){return chosen.values().stream().filter(e->e.kind()==Kind.MAGAZINE&&e.target().equals(family)).map(StockPlan.Entry::slot).findFirst().orElse(null);}
    private static boolean has(TradeResolver.Row row,ResourceLocation id){return row.candidates().stream().anyMatch(c->c.id().equals(id));}
    private static Optional<ResourceLocation> choose(List<Choice> choices,long seed,ResourceLocation slot){
        var sorted=choices.stream().sorted(Comparator.comparing(Choice::id)).toList();
        if(sorted.isEmpty())return Optional.empty();
        double total=sorted.stream().mapToDouble(Choice::weight).sum();
        double draw=new java.util.Random(seed ^ ((long)slot.toString().hashCode()<<32) ^ slot.toString().hashCode()).nextDouble()*total;
        for(var c:sorted){draw-=c.weight();if(draw<0)return Optional.of(c.id());}
        return Optional.of(sorted.get(sorted.size()-1).id());
    }
}
