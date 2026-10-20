package com.gamergaming.taczweaponblueprints.resource.research;

import com.gamergaming.taczweaponblueprints.progression.FoundWeaponRecoveryMode;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;

/** Effective station/origin combinations for pack authors, not a player action preview. */
public final class BlueprintAnalyzerDiagnostics {
    private BlueprintAnalyzerDiagnostics() { }

    public static JsonObject export(BlueprintAnalyzerPolicyResolver.Context context, ResourceLocation profileId,
            ResourceLocation target, FoundWeaponRecoveryMode recovery) {
        JsonObject result = new JsonObject();
        result.addProperty("gameplay_enabled", true);
        result.addProperty("player_eligibility_checked", false);
        result.addProperty("scope", "policy_only");
        result.addProperty("found_weapon_recovery_mode", recovery.name().toLowerCase(java.util.Locale.ROOT));
        // Unconfigured legacy entries remain compact even in very large catalogs.
        var profile = context.research().profiles().get(profileId);
        var selection = context.selection(profileId, target);
        if ((profile == null || profile.analyzer().isEmpty()) && selection.selectedRuleId().isEmpty()) {
            result.addProperty("mode", "legacy");
            return result;
        }
        JsonArray quotes = new JsonArray();
        for (var tier : AnalyzerTier.values()) for (var origin : BlueprintAnalyzerPolicy.Origin.values()) {
            var quote = BlueprintAnalyzerPolicyResolver.resolve(context, profileId, target, tier, origin, recovery);
            JsonObject row = new JsonObject();
            row.addProperty("analyzer_tier", tier.serializedName());
            row.addProperty("origin", origin.name().toLowerCase(java.util.Locale.ROOT));
            row.addProperty("status", quote.status().name().toLowerCase(java.util.Locale.ROOT));
            row.addProperty("tier_source", quote.tierSource());
            row.addProperty("review_required", quote.reviewRequired());
            row.addProperty("specificity", quote.specificity().name().toLowerCase(java.util.Locale.ROOT));
            quote.weaponTier().ifPresent(value -> row.addProperty("weapon_tier", value.serializedName()));
            quote.selectedRuleId().ifPresent(value -> row.addProperty("selected_rule", value.toString()));
            row.addProperty("fragment_threshold", quote.fragmentThreshold());
            row.addProperty("retained_progress_cap", quote.retainedProgressCap());
            quote.attempt().ifPresent(value -> row.add("attempt", BlueprintReverseEngineeringPolicy.CODEC
                    .encodeStart(JsonOps.INSTANCE, value).getOrThrow(IllegalArgumentException::new)));
            quote.outcomes().ifPresent(value -> {
                row.add("outcomes", com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable.CODEC
                        .encodeStart(JsonOps.INSTANCE, value).getOrThrow(IllegalArgumentException::new));
                row.addProperty("total_weight", value.totalWeight());
                row.addProperty("blueprint_probability", value.blueprintProbability());
                row.addProperty("fragment_probability", value.fragmentProbability());
                row.addProperty("no_result_probability", value.noResultProbability());
            });
            quotes.add(row);
        }
        result.add("quotes", quotes);
        return result;
    }
}
