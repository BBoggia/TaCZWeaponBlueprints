package com.gamergaming.taczweaponblueprints.research.designer;

import java.util.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import net.minecraft.resources.ResourceLocation;

/** Changes one property of a complete selected rule, preserving unrelated policy extensions. */
public final class DesignerResearchProperties {
    private DesignerResearchProperties() {}
    public enum Field {
        COST("research_cost"), BENCH("progression/research_tier"), RESEARCH("research_enabled"),
        TREE("tree_enabled"), DISCOVERY("requires_discovery"), VISIBILITY("visibility"), CREATIVE("creative_bypasses_cost");
        public final String path; Field(String path){this.path=path;}
    }
    public record Inspection(String source, String local, String inherited, String effective, List<String> facts) {}
    public static Optional<JsonElement> value(JsonObject policy,Field field){
        JsonElement value=policy;for(String part:field.path.split("/")){if(!value.isJsonObject()||!value.getAsJsonObject().has(part))return Optional.empty();value=value.getAsJsonObject().get(part);}return Optional.of(value.deepCopy());
    }
    public static ResearchDesignerStructure.Proposal change(ResearchDesignerProject project,String node,Field field,Optional<JsonElement> value){
        var policy=ResearchDesignerStructure.node(project,node).policy().json();var parts=field.path.split("/");var owner=policy;
        if(parts.length==2){if(!owner.has(parts[0]))owner.add(parts[0],new JsonObject());owner=owner.getAsJsonObject(parts[0]);}
        if(value.isPresent())owner.add(parts[parts.length-1],value.get().deepCopy());else owner.remove(parts[parts.length-1]);
        if(parts.length==2&&owner.size()==0)policy.remove(parts[0]);
        // Validate with the runtime codec, including all untouched extensions.
        var rule=policy.deepCopy();rule.addProperty("format",5);rule.addProperty("profile",project.draft().profile().id());
        var target=new JsonObject();var targets=new JsonArray();targets.add(ResearchDesignerStructure.node(project,node).blueprintId().orElseThrow());target.add("blueprints",targets);rule.add("target",target);
        BlueprintResearchRule.CODEC.parse(JsonOps.INSTANCE,rule).getOrThrow(false,message->{throw new IllegalArgumentException(message);});
        var next=ResearchDesignerEdits.apply(project,ResearchDesignerEdits.policy(node,policy)).project();
        return new ResearchDesignerStructure.Proposal(project.digest(),"Edit research property",next,List.of(field.path+": "+value.map(JsonElement::toString).orElse("inherit from profile"),"Other selected-rule fields, requirements and placement stay unchanged."));
    }
    public static Inspection inspect(ResearchDesignerProject project,ResearchDesignerCapture capture,String node,Field field){
        var n=ResearchDesignerStructure.node(project,node);String bp=n.blueprintId().orElseThrow();
        var context=new DesignerCapturedContext(capture);var current=evaluate(project,context);var inherited=evaluate(change(project,node,field,Optional.empty()).result(),context);
        var raw=capture.json().getAsJsonObject("resolved_nodes").getAsJsonObject(bp);
        var facts=new ArrayList<String>();
        facts.add("Currency, result mode, undiscovered visibility limits and Creative overrides are captured server settings, not tree properties.");
        var config=DesignerCapturedSettings.progression(capture,current.profile());facts.add("Currency: "+config.currencyPolicy().item().map(Object::toString).orElse("Banked RP"));facts.add("Research result: "+config.treeResearchResultMode()+"; undiscovered visibility limit: "+config.maximumUndiscoveredVisibility());
        var feature=DesignerCapturedSettings.features(capture);facts.add("Server research enabled: "+config.researchEnabled()+"; blueprints enabled: "+config.blueprintsEnabled());facts.add("Server permits Creative cost bypass: "+config.creativeBypassesResearchCost());facts.add("Enforce Research Bench tiers: "+feature.enforceResearchTiers()+"; Creative skips tiers: "+feature.creativeBypassesWorkbenchTiers()+"; Creative skips gates: "+feature.creativeBypassesProgressionGates());
        try{facts.add("Crafting (resolved separately): "+context.crafting(current.snapshot()).policy(current.profile(),new ResourceLocation(bp)).map(p->p.disposition()+p.requiredWorkbenchTier().map(t->", workstation tier "+t.level()).orElse("")+"; source: "+p.source()).orElse("Unavailable"));}catch(RuntimeException e){facts.add("Crafting facts unavailable: "+e.getMessage());}
        var analyzerContext=new BlueprintAnalyzerPolicyResolver.Context(current.snapshot(),context.catalog(),1,1,context.progression(current.snapshot()));
        for(var tier:com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier.values())for(var origin:BlueprintAnalyzerPolicy.Origin.values()){
            var quote=BlueprintAnalyzerPolicyResolver.resolve(analyzerContext,current.profile(),new ResourceLocation(bp),tier,origin,config.foundWeaponRecoveryMode());
            facts.add("Blueprint Analyzer "+tier+" / "+origin+": "+quote.status()+"; weapon tier "+quote.weaponTier().map(t->t.name().substring(5)).orElse("unknown")+quote.outcomes().map(o->{double total=(double)o.blueprintWeight()+o.fragmentWeight()+o.noResultWeight();return String.format(Locale.ROOT,"; blueprint %.1f%%; fragments %.1f%% (%d–%d); nothing %.1f%%",100*o.blueprintWeight()/total,100*o.fragmentWeight()/total,o.minimumFragments(),o.maximumFragments(),100*o.noResultWeight()/total);}).orElse(""));
        }
        facts.add("Saved dependency facts can be stale. Survival supply is unassessed.");
        return new Inspection(raw!=null&&raw.has("selected_rule")?raw.get("selected_rule").toString():"Captured profile/default",value(n.policy().json(),field).map(JsonElement::toString).orElse("Inherited"),resolved(inherited,context,bp,field),resolved(current,context,bp,field),List.copyOf(facts));
    }
    public static ResearchDesignerCompiler.Candidate evaluate(ResearchDesignerProject project,DesignerCapturedContext context){
        var result=ResearchDesignerCompiler.compile(new ResearchDesignerSession(project).checkpoint(),context.catalog(),Optional.of(context.capture()));
        return result.candidate().orElseThrow(()->new IllegalArgumentException(result.diagnostics().stream().filter(d->d.severity()==ResearchDesignerCompiler.Severity.ERROR).map(ResearchDesignerCompiler.Diagnostic::cause).findFirst().orElse("Complete the draft before evaluating properties.")));
    }
    private static String resolved(ResearchDesignerCompiler.Candidate candidate,DesignerCapturedContext context,String bp,Field field){
        var knowledge=new PlayerRecipeData();context.catalog().keySet().forEach(id->knowledge.discoverBlueprint(id.toString()));
        var result=ResearchDesignerPreview.evaluate(candidate,context.catalog(),new ResearchDesignerPreview.Scenario(DesignerCapturedSettings.progression(context.capture(),candidate.profile()),DesignerCapturedSettings.features(context.capture()),knowledge,context::blocked,context::exempt,Optional.empty(),false));
        var policy=result.policies().get(new ResourceLocation(bp));
        return switch(field){case COST->BlueprintResearchCost.CODEC.encodeStart(JsonOps.INSTANCE,policy.researchCost()).getOrThrow(false,s->{}).toString();case BENCH->result.progression().policy(candidate.profile(),new ResourceLocation(bp)).map(p->p.researchWorkbenchTier().serializedName()).orElse("Unavailable");case RESEARCH->""+policy.researchEnabled();case TREE->""+policy.treeEnabled();case DISCOVERY->""+policy.requiresDiscovery();case VISIBILITY->policy.visibility().serializedName();case CREATIVE->""+policy.creativeBypassesCost();};
    }
    public static ResearchDesignerStructure.Proposal domain(ResearchDesignerProject project,String domain,boolean tree,boolean research){
        if(!Set.of("weapons","ammo","attachments").contains(domain))throw new IllegalArgumentException("Unknown domain");
        var p=project.draft().profile().definition().json();if(!p.has("domain_policies"))p.add("domain_policies",new JsonObject());var value=new JsonObject();value.addProperty("tree_enabled",tree);value.addProperty("research_enabled",research);p.getAsJsonObject("domain_policies").add(domain,value);
        var next=ResearchDesignerEdits.apply(project,ResearchDesignerEdits.profileDefinition(p)).project();return new ResearchDesignerStructure.Proposal(project.digest(),"Edit domain",next,List.of(domain+": tree="+tree+", research="+research));
    }
    public static ResearchDesignerStructure.Proposal disclosure(ResearchDesignerProject project,String id,String mode,Optional<String> anchor){
        if(!Set.of("public_when_populated","anchor_revealed").contains(mode))throw new IllegalArgumentException("Choose a supported label rule");
        var b=ResearchDesignerStructure.branch(project,id);b.addProperty("label_visibility",mode);if(anchor.isPresent())b.addProperty("anchor",new ResourceLocation(anchor.get()).toString());else b.remove("anchor");
        var next=ResearchDesignerEdits.apply(project,ResearchDesignerEdits.replaceBranch(id,b)).project();return new ResearchDesignerStructure.Proposal(project.digest(),"Edit branch disclosure",next,List.of("Label visibility: "+mode+"; anchor: "+anchor.orElse("none"),"Validation checks anchor ownership within this branch subtree."));
    }
}
