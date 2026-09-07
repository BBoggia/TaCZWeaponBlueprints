package com.gamergaming.taczweaponblueprints.research.designer;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.item.*;
import net.minecraft.resources.ResourceLocation;

class DesignerReleaseLimitsTest {
    record Fixture(JsonObject source,Map<ResourceLocation,BlueprintData> catalog){}
    Fixture make(int count,boolean chain){
        var source=ResearchDesignerProject.create("stress","Stress tree").json();source.getAsJsonObject("draft").getAsJsonObject("tree").getAsJsonObject("definition").add("domains",ResearchDesignerContractTest.project(ResearchDesignerContractTest.named("single")).getAsJsonObject("draft").getAsJsonObject("tree").getAsJsonObject("definition").get("domains").deepCopy());var nodes=new JsonArray();var catalog=new LinkedHashMap<ResourceLocation,BlueprintData>();
        for(int i=0;i<count;i++){
            String bp="stress:gun_"+i;var node=new JsonObject();node.addProperty("id",UUID.nameUUIDFromBytes(bp.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());node.addProperty("blueprint_id",bp);node.add("policy",JsonParser.parseString("{\"tree_enabled\":true,\"research_enabled\":true,\"recycling_enabled\":false,\"recycling_value\":0,\"visibility\":\"full\",\"research_cost\":{\"points\":2}}"));
            var placement=new JsonObject();placement.addProperty("domain","weapons");placement.addProperty("lane","designer_fixture:weapons");placement.addProperty("tier","starter");placement.addProperty("rank",chain?i:0);placement.addProperty("order",i);node.add("placement",placement);var requirements=new JsonArray();
            if(chain&&i>0){var group=new JsonObject();group.addProperty("id",UUID.nameUUIDFromBytes((bp+"group").getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());var alternatives=new JsonArray();alternatives.add("stress:gun_"+(i-1));group.add("any_of",alternatives);requirements.add(group);}node.add("requirements",requirements);nodes.add(node);
            var id=new ResourceLocation(bp);catalog.put(id,new BlueprintData(bp,bp,"",id,null,"pistol",null,BlueprintKind.GUN,1));
        }source.getAsJsonObject("draft").add("nodes",nodes);return new Fixture(source,catalog);
    }
    @Test void maximumSupportedBroadCatalogCompilesWithoutDroppingNodes(){var f=make(4096,false);var p=ResearchDesignerProject.fromJson(f.source());var result=ResearchDesignerCompiler.compile(p,f.catalog(),Optional.empty());assertTrue(result.candidate().isPresent(),result.diagnostics().toString());assertEquals(4096,result.candidate().orElseThrow().resources().json().getAsJsonObject("research_rules").size());}
    @Test void longChainPreservesEveryRequirement(){var f=make(64,true);var p=ResearchDesignerProject.fromJson(f.source());var result=ResearchDesignerCompiler.compile(p,f.catalog(),Optional.empty());assertTrue(result.candidate().isPresent(),result.diagnostics().toString());assertEquals(64,result.candidate().orElseThrow().resources().json().getAsJsonObject("research_rules").size());}
    @Test void overLimitCatalogIsRejectedBeforeCompilation(){var f=make(4097,false);assertThrows(IllegalArgumentException.class,()->ResearchDesignerProject.fromJson(f.source()));}
    @Test void excessivePrerequisiteDepthReportsRuntimeLimit(){var f=make(512,true);var p=ResearchDesignerProject.fromJson(f.source());var result=ResearchDesignerCompiler.compile(p,f.catalog(),Optional.empty());assertTrue(result.candidate().isEmpty());assertTrue(result.diagnostics().stream().anyMatch(d->d.cause().contains("depth 64")));}

    @Test void branchCountLimitRejectsExcessWithoutLosingDraft(){var f=make(1,false);var branches=new JsonArray();for(int i=0;i<129;i++){var branch=new JsonObject();branch.addProperty("id","stress:branch_"+i);branch.addProperty("domain","weapons");branch.addProperty("title","Branch "+i);branch.addProperty("order",i);branches.add(branch);}f.source().getAsJsonObject("draft").getAsJsonObject("tree").getAsJsonObject("definition").add("branches",branches);assertThrows(IllegalArgumentException.class,()->ResearchDesignerProject.fromJson(f.source()));branches.remove(128);assertEquals(128,ResearchDesignerProject.fromJson(f.source()).draft().tree().definition().json().getAsJsonArray("branches").size());}

}
