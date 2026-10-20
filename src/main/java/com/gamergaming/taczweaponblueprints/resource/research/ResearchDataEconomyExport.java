package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier;
import net.minecraft.resources.ResourceLocation;

/** Operator-only resolved scenarios. This data must never be used as a client preview packet. */
public final class ResearchDataEconomyExport {
    private ResearchDataEconomyExport() { }
    public static JsonObject create(BlueprintAnalyzerPolicyResolver.Context source, ResourceLocation profile) {
        var root=new JsonObject();root.addProperty("format",1);
        root.addProperty("scope","policy_only; standard eligibility defaults; no blacklist or exemption overlay");
        var context=new ResearchDataPoolResolver.Context(source);
        var all=new TreeSet<ResourceLocation>(source.catalog().keySet());
        var known=new HashSet<ResourceLocation>(all.stream().limit(all.size()*9L/10).toList());
        var rows=new JsonArray();int remaining=262144;
        for(var definition:new TreeMap<>(source.research().researchData().policies()).entrySet()) {
            for(var station:AnalyzerTier.values())for(int scenario=0;scenario<3;scenario++) {
                boolean endgame=scenario==1;boolean lowerOnly=scenario==2;
                var pool=ResearchDataPoolResolver.evaluate(context,definition.getKey(),profile,station,ResearchDataPoolResolver.Defaults.STANDARD,
                        new ResearchDataPoolResolver.PlayerView(endgame?known:Set.of(),endgame?all:Set.of(),Map.of()),id->!lowerOnly||source.progression().policy(profile,id).map(p->p.researchWorkbenchTier().level()==1).orElse(false));
                var row=new JsonObject();row.addProperty("definition",definition.getKey().toString());row.addProperty("station",station.level());
                row.addProperty("scenario",endgame?"known_90_percent":lowerOnly?"tier_1_only_catalog":"fresh");row.addProperty("status",pool.status().name());
                row.add("policy",ResearchDataPolicy.CODEC.encodeStart(JsonOps.INSTANCE,definition.getValue()).getOrThrow(IllegalArgumentException::new));
                var targets=new JsonArray();double duplicate=0;
                long total=pool.tiers().stream().mapToLong(ResearchDataPoolResolver.TierPool::weight).sum();
                for(var tier:pool.tiers())for(var candidate:tier.candidates()) {
                    if(--remaining<0)throw new IllegalArgumentException("Data economy export exceeds 262144 target rows");
                    double probability=(double)tier.weight()/total*candidate.weight()/tier.targetWeight();
                    var target=new JsonObject();target.addProperty("target",candidate.target().toString());target.addProperty("tier",candidate.tier().level());
                    target.addProperty("probability",probability);target.addProperty("learned",endgame&&known.contains(candidate.target()));
                    target.addProperty("threshold",candidate.reconstruction().map(p->p.threshold()).orElse(0));
                    target.add("outcomes",com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable.CODEC.encodeStart(JsonOps.INSTANCE,candidate.outcomes()).getOrThrow(IllegalArgumentException::new));
                    targets.add(target);if(endgame&&known.contains(candidate.target()))duplicate+=probability;
                }
                row.addProperty("duplicate_probability",duplicate);row.add("targets",targets);rows.add(row);
            }
        }
        root.add("rows",rows);
        var returns=new JsonArray();
        for(var target:all) {
            if(source.catalog().get(target).getKind()!=com.gamergaming.taczweaponblueprints.item.BlueprintKind.GUN)continue;
            var policy=BlueprintResearchPolicyResolver.definitionFor(source.research(),source.catalog(),profile,target);
            var row=new JsonObject();row.addProperty("target",target.toString());row.addProperty("recycling_enabled",policy.recyclingEnabled());
            row.addProperty("currency_return",policy.recyclingValue());row.addProperty("research_currency_cost",policy.researchCost().points());
            row.addProperty("reverse_engineering_currency_cost",policy.reverseEngineering().cost().points());
            row.addProperty("reverse_engineering_output_recyclable",policy.reverseEngineering().outputRecyclable());
            returns.add(row);
        }
        root.add("weapon_recycling",returns);return root;
    }
}
