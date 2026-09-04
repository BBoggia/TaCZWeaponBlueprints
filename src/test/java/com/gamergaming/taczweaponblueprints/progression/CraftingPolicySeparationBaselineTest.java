package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Characterizes the boundary that the independent crafting-policy work replaces. */
class CraftingPolicySeparationBaselineTest {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));
    private static final Path FIXTURE = PROJECT.resolve(
            "src/test/resources/fixtures/crafting-policy-separation-baseline.json");

    @Test
    void baselineCoversEveryRequiredDecisionPath() throws IOException {
        JsonObject root = fixture();
        assertEquals(1, root.get("format").getAsInt());

        JsonArray scenarios = root.getAsJsonArray("scenarios");
        Map<String, JsonObject> byId = new LinkedHashMap<>();
        scenarios.forEach(value -> {
            JsonObject scenario = value.getAsJsonObject();
            String id = scenario.get("id").getAsString();
            assertFalse(byId.containsKey(id), "duplicate baseline scenario " + id);
            byId.put(id, scenario);
        });

        assertEquals(Set.of(
                "baseline:automatic_gun",
                "baseline:authored_gun",
                "baseline:authored_omitted_gun",
                "baseline:linked_ammo",
                "baseline:attachment",
                "baseline:blueprint_free_gun",
                "baseline:unrestricted_entry",
                "baseline:disabled_entry"), byId.keySet());
        assertEquals(Set.of("gun", "ammo", "attachment"), values(byId, "kind"));
        assertEquals(Set.of("tiered", "unrestricted", "disabled"),
                values(byId, "target_disposition"));
        assertEquals(Set.of("automatic", "authored_only", "not_applicable"),
                values(byId, "tree_authority"));
        assertEquals("knowledge_only",
                root.getAsJsonObject("fresh_defaults")
                        .get("blueprint_free_bypasses").getAsString());
    }

    @Test
    void targetWorkbenchMatrixIsMonotonicAndExplicit() throws IOException {
        JsonObject matrix = fixture().getAsJsonObject("workbench_matrix");

        assertEquals(Set.of("tier_1", "tier_2", "tier_3"),
                strings(matrix.getAsJsonArray("tier_1")));
        assertEquals(Set.of("tier_2", "tier_3"),
                strings(matrix.getAsJsonArray("tier_2")));
        assertEquals(Set.of("tier_3"), strings(matrix.getAsJsonArray("tier_3")));
        assertEquals(Set.of("tier_1", "tier_2", "tier_3"),
                strings(matrix.getAsJsonArray("unrestricted")));
        assertTrue(matrix.getAsJsonArray("disabled").isEmpty());
    }

    @Test
    void historicalMissingResearchPolicyPermitIsClosedByCraftingAuthority()
            throws IOException {
        JsonObject current = fixture().getAsJsonObject("current_behavior");
        assertEquals("permitted", current.get("missing_research_policy").getAsString());

        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/gamergaming/taczweaponblueprints/progression/"
                        + "CraftingEligibilityService.java"));
        int evaluation = source.indexOf("private static Evaluation evaluatePolicy(");
        int craftingMap = source.indexOf(
                "ResolvedBlueprintCraftingPolicy policy = context.policies().get", evaluation);
        int decision = source.indexOf("evaluateWorkbenchAccess(", craftingMap);
        int missingPolicy = source.indexOf("if (policy == null)", decision);
        int deny = source.indexOf("return Status.CRAFTING_POLICY_MISSING", missingPolicy);
        int tierCheck = source.indexOf("policy.permitsWorkbench", deny);

        assertTrue(evaluation >= 0);
        assertTrue(craftingMap > evaluation);
        assertTrue(decision > craftingMap);
        assertTrue(missingPolicy > decision);
        assertTrue(deny > missingPolicy);
        assertTrue(tierCheck > deny);
        assertTrue(current.get("known_gap").getAsString().contains("no crafting-level authority"));
    }

    @Test
    void automaticAndAuthoredWeaponAuthorityRemainMutuallyExclusive() throws IOException {
        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/gamergaming/taczweaponblueprints/resource/research/"
                        + "ResearchTechTreePlacementResolver.java"));
        int apply = source.indexOf("private static EffectiveSelection applyAutomatic(");
        int authoredOnly = source.indexOf("if (!automaticAuthority)", apply);
        int preserveAuthored = source.indexOf(
                "return new EffectiveSelection(base, Optional.empty())", authoredOnly);
        int suppressAuthored = source.indexOf(
                "return new EffectiveSelection(Selection.NONE, proposal)", preserveAuthored);

        assertTrue(apply >= 0);
        assertTrue(authoredOnly > apply);
        assertTrue(preserveAuthored > authoredOnly);
        assertTrue(suppressAuthored > preserveAuthored);

        JsonArray scenarios = fixture().getAsJsonArray("scenarios");
        scenarios.forEach(value -> {
            JsonObject scenario = value.getAsJsonObject();
            if ("authored_only".equals(scenario.get("tree_authority").getAsString())) {
                assertFalse("automatic_percentile".equals(
                        scenario.get("target_source").getAsString()));
            }
        });
    }

    @Test
    void migrationBaselinePreservesExistingWorldsWithoutRewritingProgress() throws IOException {
        JsonObject migration = fixture().getAsJsonObject("migration_contract");

        assertEquals(3, migration.get("existing_config_version").getAsInt());
        assertEquals(3, migration.get("existing_profile_format").getAsInt());
        assertEquals("explicit_unrestricted",
                migration.get("omitted_entry_compatibility").getAsString());
        assertFalse(migration.get("rewrite_player_progress").getAsBoolean());
    }

    private static JsonObject fixture() throws IOException {
        JsonObject result = JsonParser.parseString(Files.readString(FIXTURE)).getAsJsonObject();
        assertNotNull(result);
        return result;
    }

    private static Set<String> values(Map<String, JsonObject> scenarios, String field) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        scenarios.values().forEach(scenario -> result.add(scenario.get(field).getAsString()));
        return Set.copyOf(result);
    }

    private static Set<String> strings(JsonArray values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        values.forEach(value -> result.add(value.getAsString()));
        return Set.copyOf(result);
    }
}
