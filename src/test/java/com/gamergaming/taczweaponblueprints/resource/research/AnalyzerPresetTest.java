package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.analyzer.authoring.*;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.progression.FoundWeaponRecoveryMode;
import com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootTag;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class AnalyzerPresetTest {
    private static final Path ROOT = Path.of("examples/blueprint-analyzer");
    private static final String PROFILE_PATH = "/data/taczweaponblueprints/taczweaponblueprints/research_profiles/duplicate_recovery.json";
    private static final ResourceLocation PROFILE = id("taczweaponblueprints:duplicate_recovery"), AK = id("tacz:ak47"), M4 = id("tacz:m4a1");
    private static final Map<ResourceLocation, BlueprintData> CATALOG = Map.of(AK, gun(AK), M4, gun(M4));
    private static BlueprintData gun(ResourceLocation id) {
        return new BlueprintData(id.toString(), "test.gun", "test.tooltip", id("test:recipe"), null, "rifle", id("tacz:slot"), BlueprintKind.GUN, 1);
    }
    private static ResourceLocation id(String value) { return new ResourceLocation(value); }
    private static JsonObject json(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    private static <T> T decode(Codec<T> codec, JsonElement value) { return codec.parse(JsonOps.INSTANCE, value).getOrThrow(false, m -> { throw new AssertionError(m); }); }
    private static BlueprintAnalyzerPolicyResolver.Context context(BlueprintResearchSnapshot snapshot) {
        return new BlueprintAnalyzerPolicyResolver.Context(snapshot, CATALOG, 1, 1, BlueprintProgressionPolicySnapshot.EMPTY);
    }
    private static AnalyzerPolicyQuote quote(BlueprintResearchSnapshot snapshot, ResourceLocation target, int tier) {
        return BlueprintAnalyzerPolicyResolver.resolve(context(snapshot), PROFILE, target, AnalyzerTier.fromLevel(tier),
                BlueprintAnalyzerPolicy.Origin.FOUND, FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY);
    }
    private static BlueprintResearchSnapshot snapshot(BlueprintResearchProfile profile,
            Map<ResourceLocation, BlueprintResearchRule> rules, Map<ResourceLocation, BlueprintLootTag> tags) throws Exception {
        var resources=Path.of("src/main/resources/data/taczweaponblueprints/taczweaponblueprints");
        return BlueprintResearchSnapshot.create(tags,Map.of(PROFILE,profile),rules,Map.of(),
                Map.of(id("taczweaponblueprints:default"),decode(ResearchTechTreeDefinition.CODEC,json(resources.resolve("research_tech_trees/default.json")))),
                Map.of(),Map.of(id("taczweaponblueprints:default"),decode(ResearchAutomaticPlacementProfile.CODEC,json(resources.resolve("research_automatic_placement_profiles/default.json")))));
    }
    @Test void bundledTieredPolicyMatchesExampleAndLegacyPreservesUnrelatedSettings() throws Exception {
        var shipped = json(Path.of("src/main/resources" + PROFILE_PATH));
        assertEquals(shipped, json(ROOT.resolve("tiered" + PROFILE_PATH)));
        var analyzer = shipped.remove("analyzer"); shipped.addProperty("format", 4);
        assertEquals(shipped, json(ROOT.resolve("legacy" + PROFILE_PATH)));
        assertEquals(BlueprintAnalyzerPolicy.Mode.TIERED, decode(BlueprintAnalyzerPolicy.CODEC, analyzer).mode());
    }
    @Test void allNineStockPairsApplyMismatchRiskAndKeepIndependentLegacyPolicy() throws Exception {
        int[][] odds = {{70,30,10},{100,70,30},{100,100,80}};
        var profile = decode(BlueprintResearchProfile.CODEC, json(Path.of("src/main/resources" + PROFILE_PATH)));
        for (int weapon = 1; weapon <= 3; weapon++) {
            var rule = decode(BlueprintResearchRule.CODEC, JsonParser.parseString("{\"format\":5,\"profile\":\""+PROFILE+"\",\"target\":{\"blueprints\":[\""+AK+"\"]},\"analyzer\":{\"weapon_tier\":\"tier_"+weapon+"\"}}"));
            var snapshot = snapshot(profile, Map.of(id("test:tier"),rule), Map.of());
            for (int station = 1; station <= 3; station++) {
                var q = quote(snapshot,AK,station);
                assertEquals(AnalyzerPolicyQuote.Status.CONFIGURED,q.status());
                assertEquals(odds[station-1][weapon-1]/100d,q.outcomes().orElseThrow().blueprintProbability());
                int empty = weapon > station ? (weapon - station == 2 ? 50 : 20) : 0;
                assertEquals(empty,q.outcomes().orElseThrow().noResultWeight());
                assertEquals(100-odds[station-1][weapon-1]-empty,q.outcomes().orElseThrow().fragmentWeight());
                assertEquals(q.outcomes().orElseThrow().fragmentWeight()>0 ? 1 : 0,q.outcomes().orElseThrow().minimumFragments());
                assertEquals(weapon*5,q.fragmentThreshold());
                var a=q.attempt().orElseThrow();assertEquals(3,a.cost().points());assertEquals(1,a.inputCount().orElseThrow());
                assertFalse(a.allowKnown());assertFalse(a.outputRecyclable());
                assertEquals(PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES,a.physicalBlueprintLearningMode());
                assertEquals(weapon <= station,AnalyzerBalanceModel.estimate(q.outcomes().orElseThrow(),q.fragmentThreshold()).maximumAttempts().isPresent());
            }
            assertEquals(0,BlueprintResearchPolicyResolver.reverseEngineeringPolicyFor(snapshot,CATALOG,PROFILE,AK).cost().points());
        }
    }
    private static BlueprintResearchSnapshot overrides() throws Exception {
        var resources=ROOT.resolve("overrides/data/analyzer_example/taczweaponblueprints");
        return snapshot(decode(BlueprintResearchProfile.CODEC,json(ROOT.resolve("tiered"+PROFILE_PATH))),
                Map.of(id("analyzer_example:rifles"),decode(BlueprintResearchRule.CODEC,json(resources.resolve("research_rules/rifles.json"))),
                        id("analyzer_example:ak47"),decode(BlueprintResearchRule.CODEC,json(resources.resolve("research_rules/ak47.json")))),
                Map.of(id("analyzer_example:rifles"),decode(BlueprintLootTag.CODEC,json(resources.resolve("blueprint_tags/rifles.json")))));
    }
    @Test void installedOverridesResolveTagPartialsExactGuaranteeAndStationBlock() throws Exception {
        var snapshot=overrides();
        assertEquals(AnalyzerPolicyQuote.Status.STATION_TIER_REQUIRED,quote(snapshot,AK,1).status());
        var exact=quote(snapshot,AK,2);assertEquals(Optional.of(id("analyzer_example:ak47")),exact.selectedRuleId());
        assertEquals(1,exact.outcomes().orElseThrow().blueprintProbability());assertEquals(4,exact.attempt().orElseThrow().cost().points());
        var tag=quote(snapshot,M4,1);assertEquals(Optional.of(id("analyzer_example:rifles")),tag.selectedRuleId());
        assertEquals(0,tag.outcomes().orElseThrow().blueprintProbability());assertEquals(6,tag.attempt().orElseThrow().cost().points());
        assertEquals(8,AnalyzerBalanceModel.estimate(tag.outcomes().orElseThrow(),tag.fragmentThreshold()).maximumAttempts().orElseThrow());
    }
    @Test void legacyRollbackKeepsDeterministicPolicyAndRejectsOrphanedTieredRuleOverlays() throws Exception {
        var legacy=decode(BlueprintResearchProfile.CODEC,json(ROOT.resolve("legacy"+PROFILE_PATH)));
        var restored=snapshot(legacy,Map.of(),Map.of());
        for(int tier=1;tier<=3;tier++) assertEquals(AnalyzerPolicyQuote.Status.LEGACY,quote(restored,AK,tier).status());
        var enabled=overrides();
        assertThrows(IllegalArgumentException.class,()->snapshot(legacy,enabled.rules(),enabled.tags()));
    }
    @Test void realCatalogExportFeedsReportAndPreservesSelectedCosts() throws Exception {
        var catalog=JsonParser.parseString(BlueprintResearchCatalogExporter.export(overrides(),CATALOG,PROFILE)).getAsJsonObject();
        var report=AnalyzerBalanceReport.create(catalog,Optional.empty());
        assertEquals(24,report.getAsJsonArray("rows").size());
        var row=report.getAsJsonArray("rows").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(r->r.get("blueprint").getAsString().equals(AK.toString()) && r.getAsJsonObject("quote").get("analyzer_tier").getAsString().equals("tier_2")
                        && r.getAsJsonObject("quote").get("origin").getAsString().equals("found")).findFirst().orElseThrow();
        assertEquals(1,row.get("expected_attempts").getAsDouble());assertEquals(4,row.get("expected_rp_consumed").getAsDouble());
        assertEquals(1,row.getAsJsonArray("expected_material_terms").get(0).getAsJsonObject().get("expected_consumed").getAsDouble());
        assertEquals("protected_mode_baseline",row.get("recovery_mode_scope").getAsString());
        assertEquals(report,AnalyzerBalanceReport.create(catalog,Optional.empty()));
        var output=Path.of("build/reports/analyzer");Files.createDirectories(output);
        Files.writeString(output.resolve("example-catalog.json"),new GsonBuilder().setPrettyPrinting().create().toJson(catalog));
    }
}
