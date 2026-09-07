package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPolicyTest.*;
import java.util.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ShippedResearchEconomyTest {
    static JsonObject resource(String path) throws Exception {
        try(var in=Objects.requireNonNull(ShippedResearchEconomyTest.class.getResourceAsStream("/data/taczweaponblueprints/taczweaponblueprints/"+path+".json"))) {
            return JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    static BlueprintAnalyzerPolicyResolver.Context shipped(String name,Map<net.minecraft.resources.ResourceLocation,Integer> tiers) throws Exception {
        var policy=decode(ResearchDataPolicy.CODEC,resource("research_data/"+name));
        var shared=decode(AnalysisReconstructionDefinition.CODEC,resource("analysis_reconstruction/research_data"));
        return context(policy,tiers,Map.of(id("taczweaponblueprints:research_data"),shared),false);
    }
    @ParameterizedTest @CsvSource({"note,1","report,2","dossier,3"})
    void shippedPoolsAreReachableProtectedAndImproveByStation(String name,int maximum) throws Exception {
        var source=shipped(name,threeTiers());var policy=source.research().researchData().policies().get(POLICY);
        assertEquals(0,policy.cost().points());assertFalse(policy.rewardClass().recyclable());
        double previous=-1;
        for(var tier:AnalyzerTier.values()) {
            var pool=ResearchDataPoolResolver.evaluate(new ResearchDataPoolResolver.Context(source),POLICY,PROFILE,tier,ResearchDataPoolResolver.Defaults.STANDARD,EMPTY_PLAYER,id->true);
            assertTrue(pool.ready());assertEquals(maximum,pool.tiers().size());
            assertTrue(pool.blueprintProbability()>=previous);previous=pool.blueprintProbability();
            assertEquals(tier.level(),pool.minimumFragments());
            for(var group:pool.tiers())for(var candidate:group.candidates())assertFalse(candidate.reconstruction().orElseThrow().recyclable());
            if(name.equals("note"))assertEquals(0,pool.blueprintProbability());
        }
    }
    @Test void absentHigherTiersRenormalizeAndKnownTargetsAreAllowedByDefault() throws Exception {
        var source=shipped("dossier",Map.of(id("test:one"),1));
        var player=new ResearchDataPoolResolver.PlayerView(Set.of(id("test:one")),Set.of(id("test:one")),Map.of());
        var pool=ResearchDataPoolResolver.evaluate(new ResearchDataPoolResolver.Context(source),POLICY,PROFILE,AnalyzerTier.TIER_1,ResearchDataPoolResolver.Defaults.STANDARD,player,id->true);
        assertTrue(pool.ready());assertEquals(1,pool.tiers().size());
    }
    @Test void exportAndSimulationAreRepeatableAndModelPooling() throws Exception {
        var source=shipped("dossier",threeTiers());var data=ResearchDataEconomyExport.create(source,PROFILE);
        assertEquals(9,data.getAsJsonArray("rows").size());
        var catalog=new JsonObject();var entries=new JsonArray();
        for(var id:threeTiers().keySet()) {var e=new JsonObject();e.addProperty("blueprint_kind","gun");e.addProperty("research_enabled",true);e.addProperty("research_points",8);entries.add(e);}
        catalog.add("entries",entries);catalog.add("research_data_economy",data);
        var report=com.gamergaming.taczweaponblueprints.analyzer.authoring.ResearchEconomyReport.create(catalog);
        assertEquals(report,com.gamergaming.taczweaponblueprints.analyzer.authoring.ResearchEconomyReport.create(catalog));
        assertEquals(24,report.get("sum_base_research_currency").getAsInt());
        assertEquals(18,report.getAsJsonArray("data_simulations").size());
        assertTrue(report.getAsJsonArray("data_simulations").get(0).getAsJsonObject().get("expected_fragment_sets").getAsDouble()>0);
    }
    @Test void examplePoliciesMergeWithShippedSharedAuthority() throws Exception {
        for(String name:List.of("unlearned-only","weighted-pool","recyclable-data")) {
            var root=java.nio.file.Path.of("examples/research-economy",name,"data/taczweaponblueprints/taczweaponblueprints");
            var policies=new HashMap<net.minecraft.resources.ResourceLocation,ResearchDataPolicy>();
            try(var files=java.nio.file.Files.list(root.resolve("research_data"))) {
                for(var file:files.toList())policies.put(id("taczweaponblueprints:"+file.getFileName().toString().replace(".json","")),decode(ResearchDataPolicy.CODEC,JsonParser.parseString(java.nio.file.Files.readString(file))));
            }
            var sharedFile=root.resolve("analysis_reconstruction/research_data.json");
            var shared=decode(AnalysisReconstructionDefinition.CODEC,java.nio.file.Files.exists(sharedFile)?JsonParser.parseString(java.nio.file.Files.readString(sharedFile)):resource("analysis_reconstruction/research_data"));
            assertDoesNotThrow(()->new ResearchDataSnapshot(policies,Map.of(id("taczweaponblueprints:research_data"),shared)));
        }
    }
}
