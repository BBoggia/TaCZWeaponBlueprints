package com.gamergaming.taczweaponblueprints.acceptance;

import com.gamergaming.taczweaponblueprints.research.designer.*;
import com.gamergaming.taczweaponblueprints.research.designer.server.ResearchDesignerCaptureService;
import com.gamergaming.taczweaponblueprints.resource.research.ProgressionPolicyAccessService;
import com.google.gson.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.nio.file.*;
import java.util.UUID;

/** Development-only, opt-in read-only production capture exercise. */
@EventBusSubscriber(modid="taczweaponblueprints")
public final class DesignerCaptureServerProbe {
    private static boolean finished; private static int ticks;
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) throws Exception {
        String output=System.getProperty("tacz.designerCaptureQA.output","");
        if(output.isEmpty()||finished)return;
        if(++ticks<60)return; finished=true; var report=new JsonObject();
        try {
            var before=ProgressionPolicyAccessService.acquire(ProgressionPolicyAccessService.Mode.CURRENT_ONLY).orElseThrow();
            var capture=ResearchDesignerCaptureService.capture(event.getServer(),UUID.randomUUID());
            var second=ResearchDesignerCaptureService.capture(event.getServer(),UUID.randomUUID());
            if(!capture.sameContent(second))throw new IllegalStateException("Read-only captures differ");
            var config=com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT;
            var p=config.progressionSnapshot();
            var hidden=new com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot(
                    p.blueprintsEnabled(),p.discoveryTrackingEnabled(),p.journalEnabled(),
                    com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility.HIDDEN,
                    p.researchEnabled(),p.duplicatePolicy(),p.allowUnlearnedRecycling(),p.pointCap(),
                    p.creativeBypassesResearchCost(),p.activeProfileId(),p.treeResearchResultMode(),p.researchCostMode(),p.foundWeaponRecoveryMode(),p.currencyPolicy());
            var hiddenContext=com.gamergaming.taczweaponblueprints.resource.research.DesignerCaptureAssembly.assemble(
                    before,hidden,config.accessSnapshot(),config::isItemBlacklisted,new JsonObject(),new JsonObject(),
                    capture.json().getAsJsonObject("dependencies"),(kind,id)->"probe");
            for(var entry:capture.json().getAsJsonObject("resolved_nodes").entrySet()) {
                var node=entry.getValue().getAsJsonObject();
                if(node.has("automatic_evidence") && !node.get("requirements").equals(hiddenContext.getAsJsonObject("resolved_nodes").getAsJsonObject(entry.getKey()).get("requirements")))
                    throw new IllegalStateException("Discovery ceiling changed materialized automatic topology");
            }
            report.addProperty("discovery_independent_topology",true);
            var converted=ResearchDesignerConversion.createCustom(capture,"designer_probe","Captured server tree");
            var project=ResearchDesignerProject.open(converted.project().bytes()).editable().orElseThrow();
            var json=project.json().getAsJsonObject("draft");
            decode(com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile.CODEC,json.getAsJsonObject("profile").get("definition"));
            decode(com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition.CODEC,json.getAsJsonObject("tree").get("definition"));
            for(var element:json.getAsJsonArray("nodes")) {
                var node=element.getAsJsonObject(); var rule=node.getAsJsonObject("policy").deepCopy();
                rule.addProperty("format",5);rule.addProperty("profile",project.draft().profile().id());
                var target=new JsonObject();var ids=new JsonArray();ids.add(node.get("blueprint_id"));target.add("blueprints",ids);rule.add("target",target);
                var groups=new JsonArray();node.getAsJsonArray("requirements").forEach(g -> {var copy=g.getAsJsonObject().deepCopy();copy.remove("id");groups.add(copy);});rule.add("prerequisite_groups",groups);
                decode(com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchRule.CODEC,rule);
            }
            DesignerCompilerServerProbe.verify(capture,project,before,report);
            var after=ProgressionPolicyAccessService.acquire(ProgressionPolicyAccessService.Mode.CURRENT_ONLY).orElseThrow();
            if(!before.revisionIdentity().equals(after.revisionIdentity()))throw new IllegalStateException("Capture changed publication");
            report.addProperty("passed",true); report.addProperty("nodes",project.draft().nodes().size());
            report.addProperty("capture_bytes",capture.bytes().length);report.addProperty("project_bytes",project.bytes().length);
            report.addProperty("fingerprint",capture.fingerprint());report.add("review_notes",DesignerValues.evidence(converted.reviewNotes()));
            Files.write(Path.of(output).resolveSibling("server.capture.json"),capture.bytes());
            Files.write(Path.of(output).resolveSibling("server.project.json"),project.bytes());
        } catch(Throwable error){report.addProperty("passed",false);report.addProperty("error",error.toString());error.printStackTrace();}
        Files.writeString(Path.of(output),report.toString());event.getServer().halt(false);
    }
    private static <T> void decode(com.mojang.serialization.Codec<T> codec,JsonElement json){
        codec.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow();
    }

}
