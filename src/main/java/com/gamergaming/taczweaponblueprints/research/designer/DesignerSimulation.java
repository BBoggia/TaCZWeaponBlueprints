package com.gamergaming.taczweaponblueprints.research.designer;

import java.util.*;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import net.minecraft.resources.ResourceLocation;

/** Synthetic session only. No player, level, packets, registries or purchase callbacks. */
public final class DesignerSimulation {
    private DesignerSimulation() {}
    public record State(Set<String> learned,Set<String> discovered,Set<String> exempt,
            List<ResearchIngredientPlanner.SlotFact> inventory,int points,int bench,boolean creative,
            TreeResearchResultMode resultMode,List<String> outputs) {
        public State { learned=Set.copyOf(learned);discovered=Set.copyOf(discovered);exempt=Set.copyOf(exempt);inventory=List.copyOf(inventory);outputs=List.copyOf(outputs);
            if(learned.size()>4096||discovered.size()>4096||exempt.size()>4096||inventory.size()>256||points<0||points>1_000_000_000||bench<1||bench>3||outputs.size()>256||resultMode==null)throw new IllegalArgumentException("Scenario exceeds supported limits");
            learned.forEach(ResourceLocation::new);discovered.forEach(ResourceLocation::new);exempt.forEach(ResourceLocation::new);
        }
        public static State empty(TreeResearchResultMode mode){return new State(Set.of(),Set.of(),Set.of(),List.of(),0,1,false,mode,List.of());}
    }
    /** Bounded synthetic supplies: prioritize currency and authored materials, never real inventory. */
    public static List<ResearchIngredientPlanner.SlotFact> testSupplies(ResearchDesignerProject project,ResearchDesignerCapture capture){
        var facts=capture.json().getAsJsonObject("registry_facts");var items=facts.getAsJsonObject("items");var tags=facts.getAsJsonObject("item_tags");
        var wanted=new LinkedHashSet<String>();
        DesignerCapturedSettings.progression(capture,new ResourceLocation(project.draft().profile().id())).currencyPolicy().item().ifPresent(id->wanted.add(id.toString()));
        var policies=new ArrayList<com.google.gson.JsonObject>();policies.add(project.draft().profile().definition().json());project.draft().nodes().forEach(n->policies.add(n.policy().json()));
        for(var policy:policies){var cost=policy.getAsJsonObject("research_cost");if(cost==null||!cost.has("ingredients"))continue;
            for(var element:cost.getAsJsonArray("ingredients")){var ingredient=element.getAsJsonObject();
                if(ingredient.has("items"))ingredient.getAsJsonArray("items").forEach(id->wanted.add(id.getAsString()));
                else if(ingredient.has("tag")&&tags.has(ingredient.get("tag").getAsString()))tags.getAsJsonArray(ingredient.get("tag").getAsString()).forEach(id->wanted.add(id.getAsString()));
            }
        }
        var slots=new ArrayList<ResearchIngredientPlanner.SlotFact>();
        for(String item:wanted){if(slots.size()==256)break;if(!items.has(item)||items.get(item).getAsInt()<1)continue;
            var membership=new HashSet<ResourceLocation>();tags.entrySet().forEach(e->{if(e.getValue().getAsJsonArray().asList().stream().anyMatch(v->v.getAsString().equals(item)))membership.add(new ResourceLocation(e.getKey()));});
            slots.add(new ResearchIngredientPlanner.SlotFact(new ResourceLocation(item),Math.min(64,items.get(item).getAsInt()),membership));
        }
        return List.copyOf(slots);
    }
    public record Result(ResearchDesignerPreview.Evaluation evaluation,Optional<ResearchPathUnlockPlanner.RouteQuote> quote,
            List<ResourceLocation> unlocks,Optional<ResearchIngredientPlanner.Allocation> allocation,List<String> checks,
            boolean ready,String target,State input,boolean discoverPhysical) {
        public Result{unlocks=List.copyOf(unlocks);checks=List.copyOf(checks);}
    }
    public static Result evaluate(ResearchDesignerCompiler.Candidate candidate,DesignerCapturedContext context,State state,Optional<ResourceLocation> target){
        var knowledge=new PlayerRecipeData();state.learned.forEach(knowledge::addBlueprint);state.discovered.forEach(knowledge::discoverBlueprint);knowledge.setResearchPoints(state.points);
        var config=DesignerCapturedSettings.progression(context.capture(),candidate.profile());var features=DesignerCapturedSettings.features(context.capture());
        java.util.function.Predicate<ResourceLocation> exempt=id->context.exempt(id)||state.exempt.contains(id.toString());
        var evaluation=ResearchDesignerPreview.evaluate(candidate,context.catalog(),new ResearchDesignerPreview.Scenario(config,features,knowledge,context::blocked,exempt,state.resultMode.learnsDirectly()?target:Optional.empty(),state.creative));
        var checks=new ArrayList<String>();checks.add("Valid graph. Runtime preview checked only for this scenario.");checks.add("Survival supply unassessed. Dependency facts describe the saved capture, not current server state.");
        checks.add("Scenario result: "+state.resultMode+"; captured server result: "+config.treeResearchResultMode());
        if(target.isEmpty())return new Result(evaluation,Optional.empty(),List.of(),Optional.empty(),checks,false,"",state,config.blueprintsEnabled()&&config.discoveryTrackingEnabled());
        var id=target.get();var quote=Optional.<ResearchPathUnlockPlanner.RouteQuote>empty();var unlocks=new ArrayList<ResourceLocation>();boolean ready=true;
        if(exempt.test(id)){checks.add("Target is exempt from progression; no research purchase is available.");ready=false;}
        if(state.resultMode.learnsDirectly()){
            var route=evaluation.route().orElseThrow();checks.add("Route: "+route.status());
            if(route.plan().isPresent()){var plan=route.plan().get();quote=Optional.of(plan.quote());plan.nodes().forEach(n->unlocks.add(n.blueprintId()));}else ready=false;
        }else{
            var p=evaluation.policies().get(id);
            if(p==null||!p.available()||p.blocked()||!p.researchEnabled()||p.learned()||p.requiresDiscovery()&&!p.discovered()||!p.prerequisitesSatisfied()||!p.researchable()){
                checks.add("Physical blueprint unavailable: check availability, discovery, learned state and prerequisites.");ready=false;
            }else{boolean bypass=state.creative&&p.creativeBypassesCost();quote=Optional.of(new ResearchPathUnlockPlanner.RouteQuote(bypass?0:p.researchCost().points(),bypass?List.of():p.researchCost().ingredients().stream().map(i->new ResearchIngredientPlanner.Requirement(i.items(),i.tag(),i.count())).toList(),bypass));unlocks.add(id);}
            if(state.outputs.size()>=256){ready=false;checks.add("Synthetic blueprint output storage is full.");}
            checks.add("Physical results create a synthetic blueprint entry; knowledge stays unchanged. World inventory delivery is unassessed.");
        }
        for(var bp:unlocks){var progression=evaluation.progression().policy(candidate.profile(),bp);
            if(progression.isEmpty()){ready=false;checks.add("Research tier unavailable for "+bp);continue;}
            var p=progression.get();if(features.enforceResearchTiers()&&!(state.creative&&features.creativeBypassesWorkbenchTiers())&&!ResearchWorkbenchTier.fromLevel(state.bench).satisfies(p.researchWorkbenchTier())){ready=false;checks.add("Research Bench "+p.researchWorkbenchTier().level()+" required for "+bp);}
            if(!p.gates().allOf().isEmpty()&&!(state.creative&&features.creativeBypassesProgressionGates())){ready=false;checks.add("External progression gates unassessed for "+bp+"; no world provider is called.");}
        }
        var allocation=quote.flatMap(q->ResearchDesignerPreview.allocateQuote(q,config.currencyPolicy(),state.points,state.inventory)).filter(ResearchIngredientPlanner.Allocation::complete);
        if(quote.isPresent()){checks.add("Aggregate payment: "+quote.get().pointCost()+" "+config.currencyPolicy().kind()+" + "+quote.get().ingredients());checks.add("Chosen unlock route: "+unlocks);if(allocation.isEmpty()){ready=false;checks.add("Currency/material inventory cannot pay this route.");}}
        ready&=quote.isPresent();checks.add(ready?"Runtime preview passed for assessed research checks.":"Runtime preview blocked or incomplete; inspect the checks above.");
        return new Result(evaluation,quote,unlocks,allocation,checks,ready,id.toString(),state,config.blueprintsEnabled()&&config.discoveryTrackingEnabled());
    }
    /** Result owns the exact immutable input; consuming a quote cannot debit real inventory. */
    public static State unlock(Result result,ResearchCurrencyPolicy currency){
        if(!result.ready||result.allocation.isEmpty())throw new IllegalStateException("Resolve scenario checks before simulating research.");
        var s=result.input;var inventory=new ArrayList<ResearchIngredientPlanner.SlotFact>();for(int i=0;i<s.inventory.size();i++){var slot=s.inventory.get(i);inventory.add(new ResearchIngredientPlanner.SlotFact(slot.item(),slot.count()-result.allocation.get().decrement(i),slot.tags()));}
        int points=s.points-(currency.kind()==ResearchCurrencyPolicy.Kind.BANKED_RP?result.quote.orElseThrow().pointCost():0);
        var learned=new LinkedHashSet<>(s.learned);var discovered=new LinkedHashSet<>(s.discovered);var outputs=new ArrayList<>(s.outputs);
        if(s.resultMode.learnsDirectly())result.unlocks.forEach(id->{learned.add(id.toString());discovered.add(id.toString());});else{outputs.add(result.target);if(result.discoverPhysical)discovered.add(result.target);}
        return new State(learned,discovered,s.exempt,inventory,points,s.bench,s.creative,s.resultMode,outputs);
    }
}
