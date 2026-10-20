package com.gamergaming.taczweaponblueprints.client;

import java.nio.file.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import me.fzzyhmstrs.fzzy_config.validation.misc.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;

/** Runs only within the explicitly marked disposable currency QA world. */
final class ResearchDataTransactionProbe {
    static void verify(ServerPlayer player, BlueprintRecyclerMenu menu, Path output) throws Exception {
        var config = ModConfigs.BLUEPRINT; var oldMode = config.researchDataBehavior;
        var oldLearned = config.researchDataAllowLearned.getUnconditional(); var original = BlueprintResearchDataManager.INSTANCE.snapshot();
        var checks = new ArrayList<String>();
        var target = ResourceLocation.parse("tacz:ak47"); var item = ResourceLocation.parse("taczweaponblueprints:research_report");
        var data = com.gamergaming.taczweaponblueprints.init.ModAttachments.recipeData(player).orElseThrow();
        try {
            config.researchDataBehavior = new ValidatedEnum<>(ResearchDataPolicy.Behavior.ANALYSIS);
            config.researchDataAllowLearned.validateAndSet(true); publishConfig();
            for (boolean fragments : List.of(false, true)) {
                install(original, target, fragments); player.getInventory().clearContent();
                data.replaceProgression(Set.of(), Set.of(target.toString()), 137); data.addBlueprint(target.toString());
                menu.getSlot(1).set(ItemStack.EMPTY); menu.getSlot(0).set(new ItemStack(ModItems.RESEARCH_REPORT.get(), 2));
                menu.refreshAuthoritativePreview(player);
                check(menu.preview().inputKind() == BlueprintRecyclerPreview.InputKind.DATA_ANALYSIS && menu.preview().actionable(), "native analysis preview " + fragments, checks);
                check(!ResearchDataRedemptionService.redeemInput(player,menu.inputStack(),item,2,true).successful(), "legacy slot API cannot redeem analysis " + fragments,checks);
                check(ResearchDataRedemptionService.redeem(player,true).status() == ResearchDataRedemptionService.Status.STATION_REQUIRED,"inventory API requires station " + fragments,checks);
                long token=menu.preview().stateToken(); int request=fragments?102:101;
                var result=menu.handleRecyclerRequest(player,request,BlueprintRecyclerActionContract.Action.ANALYZE_DATA,item,2,token);
                check(result.successful() && menu.inputStack().getCount()==1,"one native Data item consumed " + fragments,checks);
                check(data.getResearchPoints()==137 && data.getArchivedAnalysisFragments().isEmpty(),"Data neither mints RP nor archives its output " + fragments,checks);
                check(fragments ? BlueprintFragmentItem.identity(menu.outputStack()).flatMap(BlueprintFragmentItem.Identity::analysisClass).isPresent()
                        : BlueprintItem.getBlueprintId(menu.outputStack()).filter(target::equals).isPresent(),"physical native result " + fragments,checks);
                check(!menu.handleRecyclerRequest(player,request,BlueprintRecyclerActionContract.Action.ANALYZE_DATA,item,2,token).successful(),"duplicate request rejected " + fragments,checks);
                menu.refreshAuthoritativePreview(player); check(!menu.preview().actionable(),"occupied output blocks next action " + fragments,checks);
                menu.getSlot(1).set(ItemStack.EMPTY); menu.refreshAuthoritativePreview(player); token=menu.preview().stateToken();
                config.researchDataAllowLearned.validateAndSet(false);publishConfig();menu.refreshAuthoritativePreview(player);
                check(menu.preview().researchDataStatus().orElseThrow()==ResearchDataRedemptionService.Status.ALL_LEARNED,"learned exclusion exhausts native pool " + fragments,checks);
                check(!menu.handleRecyclerAction(player,BlueprintRecyclerActionContract.Action.ANALYZE_DATA,item,1,token).successful() && menu.inputStack().getCount()==1,"changed settings reject old token " + fragments,checks);
                config.researchDataAllowLearned.validateAndSet(true);publishConfig();
            }
            Files.writeString(output.resolve("data-complete.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed",true,"assertions",checks.size(),"checks",checks)));
        } finally {
            config.researchDataBehavior=oldMode; config.researchDataAllowLearned.validateAndSet(oldLearned);publishConfig();apply(original);
        }
    }
    static void install(BlueprintResearchSnapshot base, ResourceLocation target, boolean fragments) throws Exception {
        String outcome=fragments?"{\"blueprint_weight\":0,\"fragment_weight\":1,\"no_result_weight\":0,\"min_fragments\":2,\"max_fragments\":2}":"{\"blueprint_weight\":1,\"fragment_weight\":0,\"no_result_weight\":0,\"min_fragments\":0,\"max_fragments\":0}";
        String json="{\"format\":1,\"input\":{\"item\":\"taczweaponblueprints:research_report\"},\"behavior\":\"analysis\",\"reconstruction\":\"qa:shared\",\"pool\":{\"tier_weights\":{\"tier_1\":1},\"include\":{\"blueprints\":[\""+target+"\"]},\"tier_overrides\":{\""+target+"\":\"tier_1\"}},\"rows\":{\"tier_1\":{\"default\":"+outcome+"},\"tier_2\":{\"default\":"+outcome+"},\"tier_3\":{\"default\":"+outcome+"}}}";
        var policy=ResearchDataPolicy.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(json)).getOrThrow();
        var shared=AnalysisReconstructionDefinition.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString("{\"format\":1,\"fragments\":{\"thresholds\":{\"tier_1\":2,\"tier_2\":2,\"tier_3\":2},\"retained_progress_cap\":1000}}")).getOrThrow();
        apply(base.withResearchData(new ResearchDataSnapshot(Map.of(ResourceLocation.parse("qa:data"),policy),Map.of(ResourceLocation.parse("qa:shared"),shared))));
    }
    static void apply(BlueprintResearchSnapshot snapshot) throws Exception {
        var method=BlueprintResearchDataManager.class.getDeclaredMethod("applySnapshot",BlueprintResearchSnapshot.class,BlueprintDataManager.CatalogPublication.class,ResourceLocation.class,java.util.function.Predicate.class);
        method.setAccessible(true);method.invoke(BlueprintResearchDataManager.INSTANCE,snapshot,BlueprintDataManager.SERVER.catalogPublication(),ModConfigs.BLUEPRINT.progressionSnapshot().activeProfileId(),(java.util.function.Predicate<ResourceLocation>)id->true);
        if (!BlueprintDataManager.SERVER.rebuildDerivedPublicationsFromRetainedCatalog()) throw new IllegalStateException("QA derived publications unavailable");
        ProgressionPolicyAccessService.acquire(ProgressionPolicyAccessService.Mode.ENSURE_CURRENT).orElseThrow();
    }
    static void publishConfig() throws Exception {var method=ModConfigs.BLUEPRINT.getClass().getDeclaredMethod("publishProgressionSnapshot");method.setAccessible(true);method.invoke(ModConfigs.BLUEPRINT);}
    private static void check(boolean valid,String message,List<String> checks){if(!valid)throw new IllegalStateException(message);checks.add(message);}
}
