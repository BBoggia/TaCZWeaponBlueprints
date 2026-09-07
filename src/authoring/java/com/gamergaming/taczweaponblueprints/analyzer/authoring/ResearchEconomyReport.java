package com.gamergaming.taczweaponblueprints.analyzer.authoring;

import java.util.*;
import com.google.gson.*;

/** Seeded, bounded simulation of finite Data supplies and pooling. Uses resolved catalog probabilities. */
public final class ResearchEconomyReport {
    private ResearchEconomyReport() { }
    public static JsonObject create(JsonObject catalog) {
        return create(catalog,com.gamergaming.taczweaponblueprints.loot.ScrapSupplyLootModifier.defaults(),32);
    }
    public static JsonObject create(JsonObject catalog,Map<net.minecraft.resources.ResourceLocation,com.gamergaming.taczweaponblueprints.loot.ScrapSupplyLootModifier.Row> supply,int budget) {
        if(budget<0||budget>64||supply.size()>1024)throw new IllegalArgumentException("invalid supply budget or table count");
        var out=new JsonObject();out.addProperty("format",1);out.addProperty("seed",731);
        out.addProperty("assumptions","100 Data items per player; 100 trials; archives pooled and freely reconstructed; initial known targets fixed; no automatic learning; same pool for each participant; no archived initial progress; ignores material supply and prerequisites");
        long cost=0;int largest=0;int weapons=0;
        for(var raw:catalog.getAsJsonArray("entries")) {
            var e=raw.getAsJsonObject();if(!"gun".equals(e.get("blueprint_kind").getAsString())||!e.get("research_enabled").getAsBoolean())continue;
            int value=e.get("research_points").getAsInt();cost+=value;largest=Math.max(largest,value);weapons++;
        }
        out.addProperty("researchable_weapons",weapons);out.addProperty("sum_base_research_currency",cost);
        out.addProperty("base_currency_stacks_64",(cost+63)/64);out.addProperty("largest_base_node_cost",largest);
        out.addProperty("single_node_fits_empty_inventory",largest<=2304);
        out.addProperty("cost_scope","base currency only; excludes discounts, materials, already learned targets and route prerequisites");
        var sources=new JsonObject();
        supply.forEach((id,row)->{
            var s=new JsonObject();s.addProperty("chance",row.chance());s.addProperty("minimum",row.minimum());s.addProperty("maximum",row.maximum());
            double expected=0;for(int n=row.minimum();n<=row.maximum();n++)expected+=Math.min(n,budget);
            s.addProperty("expected_units_per_roll",row.chance()*expected/(row.maximum()-row.minimum()+1));s.addProperty("player_kill",row.playerKill());sources.add(id.toString(),s);
        });
        out.add("supply",sources);out.addProperty("supply_budget",budget);out.addProperty("supply_scope","provided tables or bundled defaults; physical mode; no preexisting currency; eligible player kills only");
        var simulation=new JsonArray();
        if(catalog.has("research_data_economy")) {
            var rows=catalog.getAsJsonObject("research_data_economy").getAsJsonArray("rows");
            if(rows.size()>2304)throw new IllegalArgumentException("too many Data rows");
            long work=0;
            for(var raw:rows)for(int players:new int[]{1,4}) {
                var row=raw.getAsJsonObject();var targets=row.getAsJsonArray("targets");
                var result=new JsonObject();result.addProperty("definition",row.get("definition").getAsString());result.add("station",row.get("station"));result.add("scenario",row.get("scenario"));result.addProperty("players_pooling",players);result.add("duplicate_probability",row.get("duplicate_probability"));
                result.addProperty("input_data",100*players);result.addProperty("configured_currency_cost",row.getAsJsonObject("policy").has("cost")?row.getAsJsonObject("policy").getAsJsonObject("cost").get("points").getAsInt()*100L*players:0);
                if(targets.isEmpty()){result.addProperty("status",row.get("status").getAsString());simulation.add(result);continue;}
                if(targets.size()>65536)throw new IllegalArgumentException("too many targets");
                double[] cumulative=new double[targets.size()];double sum=0;
                for(int i=0;i<targets.size();i++){double p=targets.get(i).getAsJsonObject().get("probability").getAsDouble();if(!Double.isFinite(p)||p<0||p>1)throw new IllegalArgumentException("invalid target probability");sum+=p;cumulative[i]=sum;}
                if(!Double.isFinite(sum)||Math.abs(sum-1)>1e-6)throw new IllegalArgumentException("invalid target probabilities");
                var random=new Random(731);double full=0,sets=0,duplicates=0,fragments=0;
                for(int run=0;run<100;run++) {
                    var progress=new int[targets.size()];
                    for(int draw=0;draw<100*players;draw++) {
                        if(++work>20_000_000)throw new IllegalArgumentException("economy simulation work limit exceeded");
                        double roll=random.nextDouble();int index=Arrays.binarySearch(cumulative,roll);if(index<0)index=-index-1;index=Math.min(index,targets.size()-1);
                        var t=targets.get(index).getAsJsonObject();var o=t.getAsJsonObject("outcomes");
                        int bw=o.get("blueprint_weight").getAsInt(),fw=o.get("fragment_weight").getAsInt();
                        boolean duplicate=t.get("learned").getAsBoolean();
                        if(random.nextDouble()*(bw+(double)fw)<bw){full++;if(duplicate)duplicates++;}
                        else {int n=o.get("min_fragments").getAsInt()+random.nextInt(o.get("max_fragments").getAsInt()-o.get("min_fragments").getAsInt()+1);fragments+=n;progress[index]+=n;
                            int threshold=t.get("threshold").getAsInt();if(threshold<1)throw new IllegalArgumentException("fragment outcome lacks threshold");
                            int completed=progress[index]/threshold;sets+=completed;if(duplicate)duplicates+=completed;progress[index]%=threshold;}
                    }
                }
                result.addProperty("expected_direct_blueprints",full/100);result.addProperty("expected_fragment_sets",sets/100);
                result.addProperty("expected_duplicate_blueprints",duplicates/100);result.addProperty("expected_fragments",fragments/100);
                result.addProperty("blueprints_per_player",(full+sets)/100/players);result.addProperty("status","simulated");simulation.add(result);
            }
        }
        out.add("data_simulations",simulation);
        if(catalog.has("research_data_economy") && catalog.getAsJsonObject("research_data_economy").has("weapon_recycling"))out.add("weapon_recycling",catalog.getAsJsonObject("research_data_economy").get("weapon_recycling").deepCopy());
        out.addProperty("recycling_review","Data output is protected by default. Ordinary blueprint recycling consumes its input. Crafted/unknown weapon direct recovery must remain blocked. Custom recyclable or reduced-cost policies require review against recipe costs; this report cannot prove arbitrary mod recipes unprofitable.");
        return out;
    }
}
