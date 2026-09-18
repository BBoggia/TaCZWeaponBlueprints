package com.gamergaming.taczweaponblueprints.villager.trade;

import com.google.gson.*;

/** Stable, bounded JSON inspection; no player data or random selections. */
public final class TradeDiagnostics {
    private TradeDiagnostics() {}
    public static JsonObject json(TradeRuntime.Inspection inspection) {
        JsonObject root = new JsonObject(); root.addProperty("format", 1);
        root.addProperty("scope", "global_candidates_before_stock_pairing");
        root.addProperty("definitions_revision", inspection.definitionsRevision());
        root.addProperty("catalog_revision", inspection.catalogRevision());
        root.addProperty("research_revision", inspection.researchRevision());
        root.addProperty("policy_identity", inspection.policyIdentity());
        root.addProperty("magazine_revision", inspection.magazineRevision());
        root.addProperty("magazine_status", inspection.magazineStatus());
        inspection.reloadFailure().ifPresent(f -> root.addProperty("last_reload_failure", f));
        var result = inspection.resolution(); root.addProperty("currency", result.currency().toString());
        var settings = inspection.settings(); var config = new JsonObject();
        config.addProperty("currency_mode", settings.currencyMode().name());
        config.addProperty("fixed_currency", settings.fixedCurrency().toString());
        config.addProperty("price_basis_points", settings.priceBasisPoints());
        config.addProperty("stock_basis_points", settings.stockBasisPoints());
        config.addProperty("minimum_basis_points", settings.minimumBasisPoints());
        config.addProperty("maximum_gun_tier", settings.maximumGunTier());
        config.addProperty("material_purchases", settings.materialPurchases());
        config.addProperty("extended_magazines", settings.extendedMagazines());
        JsonArray kinds = new JsonArray(); settings.categories().stream().sorted().forEach(k -> kinds.add(k.name()));
        config.add("categories", kinds); root.add("settings", config);
        JsonObject acquisition = new JsonObject(); result.acquisition().forEach((k,v) -> acquisition.addProperty(k.name(), v));
        root.add("profession_acquisition", acquisition);
        JsonArray slots = new JsonArray(); result.slots().values().forEach(r -> slots.add(row(r))); root.add("slots", slots);
        return root;
    }
    public static JsonObject row(TradeResolver.Row row) {
        JsonObject out = new JsonObject(); out.addProperty("slot", row.slot().toString());
        out.addProperty("definition", row.definition().toString()); out.addProperty("profession", row.profession().name());
        out.addProperty("level", row.level()); out.addProperty("role", row.role().name()); out.addProperty("status", row.status().name());
        out.addProperty("output_kind", row.output().kind().name()); out.addProperty("output_count", row.output().count());
        out.addProperty("allow_legacy_exchange", row.output().allowLegacyExchange());
        out.addProperty("output_tier", row.output().tier()); out.addProperty("selection", row.output().selection().name());
        out.addProperty("demand_multiplier", row.demandMultiplier());
        out.addProperty("reason", row.reason()); out.addProperty("uses", row.uses()); out.addProperty("xp", row.xp());
        row.price().ifPresent(p -> {
            JsonObject price = new JsonObject(); price.addProperty("primary_item", p.primaryItem().toString());
            p.secondaryItem().ifPresent(i -> price.addProperty("secondary_item", i.toString()));
            price.addProperty("original_primary", p.amounts().originalPrimary());
            price.addProperty("protected_secondary", p.amounts().protectedSecondary());
            price.addProperty("input_a", p.amounts().charged().primary()); price.addProperty("input_b", p.amounts().charged().secondary());
            price.addProperty("minimum_a", p.amounts().charged().minimumPrimary()); out.add("price", price);
        });
        JsonArray candidates = new JsonArray(); row.candidates().forEach(c -> {
            var value = new JsonObject(); value.addProperty("id", c.id().toString()); value.addProperty("weight", c.weight()); candidates.add(value);
        }); out.add("candidates", candidates);
        JsonObject excluded = new JsonObject(); row.excluded().forEach((id, reason) -> excluded.addProperty(id.toString(), reason)); out.add("excluded", excluded);
        JsonArray path = new JsonArray(); row.fallbackPath().forEach(id -> path.add(id.toString())); out.add("fallback_path", path);
        return out;
    }
}
