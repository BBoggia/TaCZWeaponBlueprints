package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateGroup;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateScope;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

class BlueprintCraftingAuthoringSchemaTest {
    @Test
    void formatFourProfileExpressesEveryCategoryWithoutChangingResearchFields() {
        BlueprintResearchProfile profile = decode(
                BlueprintResearchProfile.CODEC,
                fixture().get("profile"));

        assertEquals(BlueprintResearchProfile.CRAFTING_FORMAT, profile.format());
        assertEquals(BlueprintCraftingStrategy.Mode.AUTOMATIC_TIER,
                profile.crafting().authoredOmittedGuns().mode());
        assertEquals(BlueprintCraftingStrategy.Mode.FIXED,
                profile.crafting().automaticGuns().mode());
        assertEquals(BlueprintCraftingStrategy.Mode.LINKED_WEAPON,
                profile.crafting().ammo().mode());
        assertEquals(BlueprintAttachmentCraftingPolicy.Mode.TYPE_MAPPED,
                profile.crafting().attachments().mode());
        assertEquals(BlueprintCraftingDisposition.DISABLED,
                profile.crafting().attachments().itemTypePolicies().get("muzzle").disposition());
        assertEquals(ResearchWorkbenchTier.TIER_3,
                profile.crafting().authoredGuns().forTier(
                        com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier.APEX)
                        .workbenchTier().orElseThrow());
    }

    @Test
    void allSupportedAuthoredOmissionModesDecode() {
        JsonArray values = fixture().getAsJsonArray("omission_modes");
        assertEquals(4, values.size());
        for (JsonElement value : values) {
            BlueprintCraftingStrategy strategy = decode(BlueprintCraftingStrategy.CODEC, value);
            new BlueprintCraftingProfilePolicy(
                    BlueprintAuthoredGunCraftingPolicy.DEFAULT,
                    strategy,
                    BlueprintCraftingStrategy.AUTOMATIC_DEFAULT,
                    BlueprintCraftingStrategy.AMMO_DEFAULT,
                    BlueprintAttachmentCraftingPolicy.DEFAULT,
                    BlueprintCraftingAccessPolicy.TIER_1);
        }
    }

    @Test
    void ruleOverridesExpressTieredUnrestrictedDisabledAndGateOnlyPolicies() {
        JsonArray rules = fixture().getAsJsonArray("rules");
        assertEquals(4, rules.size());
        assertEquals(BlueprintCraftingDisposition.TIERED,
                decode(BlueprintResearchRule.CODEC, rules.get(0))
                        .crafting().orElseThrow().disposition().orElseThrow());
        assertEquals(BlueprintCraftingDisposition.UNRESTRICTED,
                decode(BlueprintResearchRule.CODEC, rules.get(1))
                        .crafting().orElseThrow().disposition().orElseThrow());
        assertEquals(BlueprintCraftingDisposition.DISABLED,
                decode(BlueprintResearchRule.CODEC, rules.get(2))
                        .crafting().orElseThrow().disposition().orElseThrow());
        assertTrue(decode(BlueprintResearchRule.CODEC, rules.get(3))
                .crafting().orElseThrow().gates().isPresent());
    }

    @Test
    void oldFormatsDecodeToExplicitCompatibilityPolicy() {
        JsonObject oldProfile = fixture().getAsJsonObject("profile").deepCopy();
        oldProfile.addProperty("format", BlueprintResearchProfile.PROGRESSION_FORMAT);
        oldProfile.remove("crafting");
        BlueprintResearchProfile profile = decode(BlueprintResearchProfile.CODEC, oldProfile);
        assertSame(BlueprintCraftingProfilePolicy.LEGACY, profile.crafting());

        oldProfile.addProperty("format", BlueprintResearchProfile.DOMAIN_POLICY_FORMAT);
        oldProfile.remove("progression");
        assertSame(BlueprintCraftingProfilePolicy.LEGACY,
                decode(BlueprintResearchProfile.CODEC, oldProfile).crafting());

        oldProfile.addProperty("format", BlueprintResearchProfile.LEGACY_FORMAT);
        oldProfile.remove("domain_policies");
        assertSame(BlueprintCraftingProfilePolicy.LEGACY,
                decode(BlueprintResearchProfile.CODEC, oldProfile).crafting());

        JsonObject oldRule = fixture().getAsJsonArray("rules").get(0).getAsJsonObject().deepCopy();
        oldRule.addProperty("format", BlueprintResearchRule.PROGRESSION_FORMAT);
        oldRule.remove("crafting");
        assertTrue(decode(BlueprintResearchRule.CODEC, oldRule).crafting().isEmpty());
    }

    @Test
    void newCraftingBlocksRequireFormatFour() {
        JsonObject profile = fixture().getAsJsonObject("profile").deepCopy();
        profile.addProperty("format", BlueprintResearchProfile.PROGRESSION_FORMAT);
        assertDecodeError(BlueprintResearchProfile.CODEC, profile, "crafting requires");

        JsonObject rule = fixture().getAsJsonArray("rules").get(0).getAsJsonObject().deepCopy();
        rule.addProperty("format", BlueprintResearchRule.PROGRESSION_FORMAT);
        assertDecodeError(BlueprintResearchRule.CODEC, rule, "crafting requires");
    }

    @Test
    void dispositionAndWorkbenchTierConflictsFailWithActionableErrors() {
        assertDecodeError(
                BlueprintCraftingRuleOverride.CODEC,
                JsonParser.parseString("{\"disposition\":\"tiered\"}"),
                "workbench_tier");
        assertDecodeError(
                BlueprintCraftingRuleOverride.CODEC,
                JsonParser.parseString(
                        "{\"disposition\":\"unrestricted\",\"workbench_tier\":\"tier_2\"}"),
                "workbench_tier");
        assertDecodeError(
                BlueprintCraftingRuleOverride.CODEC,
                JsonParser.parseString("{}"),
                "cannot be empty");
    }

    @Test
    void legacyAndIndependentCraftingOverridesCannotCompete() {
        JsonObject rule = fixture().getAsJsonArray("rules").get(0).getAsJsonObject().deepCopy();
        rule.add("progression", JsonParser.parseString("{\"crafting_tier\":\"tier_2\"}"));
        assertDecodeError(BlueprintResearchRule.CODEC, rule, "conflicts");
    }

    @Test
    void craftingGatesRejectResearchOnlyScope() {
        JsonObject rule = fixture().getAsJsonArray("rules").get(3).getAsJsonObject().deepCopy();
        rule.getAsJsonObject("crafting")
                .getAsJsonObject("gates")
                .getAsJsonArray("all_of")
                .get(0).getAsJsonObject()
                .getAsJsonArray("any_of")
                .get(0).getAsJsonObject()
                .addProperty("scope", "research");
        assertDecodeError(BlueprintResearchRule.CODEC, rule, "crafting or both scope");
    }

    @Test
    void legacyGateOverridesCannotEraseTheOtherActionsFallback() {
        ProgressionGateRequirements researchFallback = gates(
                "test:research_fallback", ProgressionGateScope.RESEARCH);
        ProgressionGateRequirements craftingFallback = gates(
                "test:crafting_fallback", ProgressionGateScope.CRAFTING);
        ProgressionGateRequirements researchOverride = gates(
                "test:research_override", ProgressionGateScope.RESEARCH);
        ProgressionGateRequirements craftingOverride = gates(
                "test:crafting_override", ProgressionGateScope.CRAFTING);

        assertEquals(researchFallback,
                BlueprintCraftingPolicyResolutionSupport.researchGatesOrElse(
                        Optional.of(craftingOverride), researchFallback));
        assertEquals(craftingFallback,
                BlueprintCraftingPolicyResolutionSupport.craftingGatesOrElse(
                        Optional.of(researchOverride), craftingFallback));
        assertEquals(researchOverride,
                BlueprintCraftingPolicyResolutionSupport.researchGatesOrElse(
                        Optional.of(researchOverride), researchFallback));
        assertEquals(craftingOverride,
                BlueprintCraftingPolicyResolutionSupport.craftingGatesOrElse(
                        Optional.of(craftingOverride), craftingFallback));
    }

    @Test
    void unknownFieldsAreRejectedAtEveryNewSchemaBoundary() {
        JsonObject profile = fixture().getAsJsonObject("profile").deepCopy();
        profile.getAsJsonObject("crafting").addProperty("unknown", true);
        assertDecodeError(BlueprintResearchProfile.CODEC, profile, "unknown");

        JsonObject rule = fixture().getAsJsonArray("rules").get(0).getAsJsonObject().deepCopy();
        rule.getAsJsonObject("crafting").addProperty("unknown", true);
        assertDecodeError(BlueprintResearchRule.CODEC, rule, "unknown");
    }

    private static ProgressionGateRequirements gates(
            String criterionId,
            ProgressionGateScope scope) {
        return new ProgressionGateRequirements(List.of(new ProgressionGateGroup(List.of(
                ProgressionGateCondition.Criterion.of(
                        criterionId,
                        1,
                        scope,
                        "gate.test.requirement",
                        ProgressionGateCondition.Disclosure.PUBLIC)))));
    }

    @Test
    void categoryStrategiesRejectModesFromAnotherResolverDomain() {
        JsonObject profile = fixture().getAsJsonObject("profile").deepCopy();
        JsonObject crafting = profile.getAsJsonObject("crafting");
        crafting.add("authored_omitted_guns", JsonParser.parseString("""
                {
                  "mode": "linked_weapon",
                  "fallback": {"disposition": "tiered", "workbench_tier": "tier_1"}
                }
                """));
        assertDecodeError(BlueprintResearchProfile.CODEC, profile, "authored_omitted_guns");

        profile = fixture().getAsJsonObject("profile").deepCopy();
        crafting = profile.getAsJsonObject("crafting");
        crafting.add("ammo", JsonParser.parseString("""
                {
                  "mode": "automatic_tier",
                  "fallback": {"disposition": "tiered", "workbench_tier": "tier_1"}
                }
                """));
        assertDecodeError(BlueprintResearchProfile.CODEC, profile, "ammo");
    }

    @Test
    void formatFourProfileAndRuleRoundTripWithoutLosingCraftingFields() {
        BlueprintResearchProfile profile = decode(
                BlueprintResearchProfile.CODEC,
                fixture().get("profile"));
        JsonElement encodedProfile = encode(BlueprintResearchProfile.CODEC, profile);
        assertTrue(encodedProfile.getAsJsonObject().has("crafting"));
        assertEquals(profile, decode(BlueprintResearchProfile.CODEC, encodedProfile));

        BlueprintResearchRule rule = decode(
                BlueprintResearchRule.CODEC,
                fixture().getAsJsonArray("rules").get(0));
        JsonElement encodedRule = encode(BlueprintResearchRule.CODEC, rule);
        assertTrue(encodedRule.getAsJsonObject().has("crafting"));
        assertEquals(rule, decode(BlueprintResearchRule.CODEC, encodedRule));
    }

    private static JsonObject fixture() {
        try (var reader = new InputStreamReader(
                BlueprintCraftingAuthoringSchemaTest.class.getResourceAsStream(
                        "/fixtures/crafting-policy-authoring-v4.json"),
                StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) {
            throw new AssertionError("could not load crafting authoring fixture", exception);
        }
    }

    private static <T> T decode(Codec<T> codec, JsonElement value) {
        var result = codec.parse(JsonOps.INSTANCE, value);
        return result.result().orElseThrow(() -> new AssertionError(
                result.error().map(error -> error.message()).orElse("missing codec result")));
    }

    private static <T> JsonElement encode(Codec<T> codec, T value) {
        var result = codec.encodeStart(JsonOps.INSTANCE, value);
        return result.result().orElseThrow(() -> new AssertionError(
                result.error().map(error -> error.message()).orElse("missing codec result")));
    }

    private static <T> void assertDecodeError(
            Codec<T> codec,
            JsonElement value,
            String expectedMessage) {
        var result = codec.parse(JsonOps.INSTANCE, value);
        String message = result.error().map(error -> error.message()).orElse("");
        assertTrue(result.error().isPresent(), "expected codec error");
        assertTrue(message.contains(expectedMessage), () ->
                "expected error containing '" + expectedMessage + "' but got: " + message);
    }
}
