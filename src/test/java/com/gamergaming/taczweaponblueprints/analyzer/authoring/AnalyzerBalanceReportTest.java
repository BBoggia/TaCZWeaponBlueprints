package com.gamergaming.taczweaponblueprints.analyzer.authoring;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.Optional;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyzerBalanceReportTest {
    @TempDir Path temp;
    private static JsonObject catalog() {
        var root=JsonParser.parseString("""
                {"format":22,"profile":"test:profile","entries":[{"blueprint":"test:gun","blueprint_kind":"gun",
                "analyzer_policy":{"scope":"policy_only","found_weapon_recovery_mode":"protected_blueprint_only",
                "recovery_mode_scope":"protected_mode_baseline","quotes":[]}}]}
                """).getAsJsonObject();
        for(int tier=1;tier<=3;tier++) for(String origin:new String[]{"found","crafted","unknown"}) {
            var q=JsonParser.parseString("""
                    {"status":"configured","fragment_threshold":2,"retained_progress_cap":10,
                    "attempt":{"input_count":2,"cost":{"points":5,"ingredients":[{"items":["minecraft:iron_ingot","minecraft:gold_ingot"],"count":3}]}},
                    "outcomes":{"blueprint_weight":1,"fragment_weight":1,"no_result_weight":0,"min_fragments":1,"max_fragments":1}}
                    """).getAsJsonObject();
            q.addProperty("analyzer_tier","tier_"+tier);q.addProperty("origin",origin);quotes(root).add(q);
        }
        return root;
    }
    private static JsonArray quotes(JsonObject root) {return root.getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonObject("analyzer_policy").getAsJsonArray("quotes");}
    private static JsonObject row(JsonObject root) {return AnalyzerBalanceReport.create(root,Optional.empty()).getAsJsonArray("rows").get(0).getAsJsonObject();}
    @Test void costsMultiplyPaidAttemptsAndAlternativeIngredientsStayOneTerm() {
        var input=catalog();var original=input.deepCopy();var r=row(input);
        assertEquals(1.5,r.get("expected_attempts").getAsDouble());
        assertEquals(3,r.get("expected_weapons_consumed").getAsDouble());assertEquals(7.5,r.get("expected_rp_consumed").getAsDouble());
        var terms=r.getAsJsonArray("expected_material_terms");assertEquals(1,terms.size());
        assertEquals(4.5,terms.get(0).getAsJsonObject().get("expected_consumed").getAsDouble());
        assertEquals(2,terms.get(0).getAsJsonObject().getAsJsonArray("items").size());assertEquals(original,input);
        assertEquals(2,AnalyzerBalanceReport.create(input,Optional.empty()).get("recurrence_states_evaluated").getAsInt());
    }
    @Test void disabledAndUnreachableRowsNeverPretendToHaveFiniteCost() {
        var c=catalog();for(var q:quotes(c))q.getAsJsonObject().addProperty("status","station_tier_required");
        var r=row(c);assertEquals("not_configured",r.get("estimate_status").getAsString());assertFalse(r.has("expected_attempts"));
        c=catalog();for(var q:quotes(c))q.getAsJsonObject().add("outcomes",JsonParser.parseString("{\"blueprint_weight\":0,\"fragment_weight\":0,\"no_result_weight\":1,\"min_fragments\":0,\"max_fragments\":0}"));
        r=row(c);assertEquals("unreachable",r.get("estimate_status").getAsString());assertTrue(r.get("maximum_attempts").isJsonNull());
        assertFalse(r.has("expected_rp_consumed"));
    }
    @Test void malformedVersionsNumbersQuotesAndTargetsAreRejected() {
        var version=catalog();version.addProperty("format",21);assertThrows(IllegalArgumentException.class,()->row(version));
        var duplicate=catalog();quotes(duplicate).set(1,quotes(duplicate).get(0).deepCopy());assertThrows(IllegalArgumentException.class,()->row(duplicate));
        var fractional=catalog();quotes(fractional).get(0).getAsJsonObject().getAsJsonObject("outcomes").addProperty("blueprint_weight",0.5);
        assertThrows(IllegalArgumentException.class,()->row(fractional));
        var badCost=catalog();quotes(badCost).get(0).getAsJsonObject().getAsJsonObject("attempt").getAsJsonObject("cost").addProperty("points","5");
        assertThrows(IllegalArgumentException.class,()->row(badCost));
        var cap=catalog();quotes(cap).get(0).getAsJsonObject().addProperty("retained_progress_cap",1);assertThrows(IllegalArgumentException.class,()->row(cap));
        assertThrows(IllegalArgumentException.class,()->AnalyzerBalanceReport.create(catalog(),Optional.of("test:missing")));
        var ammo=catalog();ammo.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("blueprint_kind","ammo");
        assertThrows(IllegalArgumentException.class,()->AnalyzerBalanceReport.create(ammo,Optional.of("test:gun")));
    }
    @Test void cliIsDeterministicAndFailedValidationDoesNotOverwriteOutputOrInput() throws Exception {
        var input=temp.resolve("catalog.json");var output=temp.resolve("report.json");var source=catalog().toString();Files.writeString(input,source);
        var args=new String[]{"--catalog",input.toString(),"--output",output.toString()};
        AnalyzerBalanceTool.main(args);var first=Files.readString(output);AnalyzerBalanceTool.main(args);assertEquals(first,Files.readString(output));
        assertEquals(source,Files.readString(input));
        assertThrows(IllegalArgumentException.class,()->AnalyzerBalanceTool.main(new String[]{"--catalog",input.toString(),"--output",input.toString()}));
        var invalid=catalog();invalid.addProperty("format",23);Files.writeString(input,invalid.toString());
        assertThrows(IllegalArgumentException.class,()->AnalyzerBalanceTool.main(args));assertEquals(first,Files.readString(output));
    }
}
