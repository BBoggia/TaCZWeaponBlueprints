package com.gamergaming.taczweaponblueprints.research.designer;

import java.util.*;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.*;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.*;

/** Captured identities and immutable evidence, independent of installed client recipes, registries or managers. */
public final class DesignerCapturedContext {
    private final ResearchDesignerCapture capture;
    private final JsonObject json;
    private final Map<ResourceLocation, BlueprintData> catalog;
    private final BlueprintResearchSnapshot source;
    public DesignerCapturedContext(ResearchDesignerCapture capture) {
        this.capture = Objects.requireNonNull(capture);
        this.json = capture.json(); var resources = new JsonObject();
        json.getAsJsonObject("definitions").entrySet().forEach(family -> {
            var definitions = new JsonObject(); family.getValue().getAsJsonObject().entrySet().forEach(e ->
                    definitions.add(e.getKey(), e.getValue().getAsJsonObject().get("definition")));
            resources.add(family.getKey(), definitions);
        });
        source = ResearchDesignerCompiler.decodeResources(resources);
        var values = new TreeMap<ResourceLocation, BlueprintData>();
        json.getAsJsonObject("catalog").entrySet().forEach(e -> {
            var fact = e.getValue().getAsJsonObject(); var id = new ResourceLocation(e.getKey());
            if (!fact.get("blueprint_id").getAsString().equals(e.getKey())) throw new IllegalArgumentException("Captured blueprint identity differs");
            // Metadata only. Recipe execution and output-stack construction are deliberately unavailable here.
            values.put(id, new BlueprintData(id.toString(), fact.get("name_key").getAsString(), "",
                    new ResourceLocation(fact.get("recipe_id").getAsString()), null, fact.get("item_type").getAsString(),
                    new ResourceLocation(fact.get("display_slot").getAsString()),
                    BlueprintKind.valueOf(fact.get("kind").getAsString().toUpperCase(Locale.ROOT)),
                    fact.get("output_count").getAsBigDecimal().intValueExact()));
        });
        catalog = Collections.unmodifiableMap(values);
    }
    public ResearchDesignerCapture capture() { return capture; }
    public Map<ResourceLocation, BlueprintData> catalog() { return catalog; }
    public BlueprintResearchSnapshot source() { return source; }
    public ResourceLocation profile() { return new ResourceLocation(json.get("profile_id").getAsString()); }
    public boolean blocked(String id) { return flag(id, "blocked"); }
    public boolean exempt(ResourceLocation id) { return flag(id.toString(), "exempt"); }
    private boolean flag(String id, String key) {
        var value = json.getAsJsonObject("catalog").getAsJsonObject(id);
        return value != null && value.get(key).getAsBoolean();
    }
    public Map<ResourceLocation, AutomaticWeaponPlacementCandidateSnapshot> automatic() {
        var facts = json.getAsJsonObject("registry_facts");
        if (!facts.has("automatic_candidates")) {
            if (source.usesAutomaticWeaponPlacement(profile())) throw new IllegalArgumentException("Capture lacks positioned automatic evidence; capture the source again");
            return Map.of();
        }
        var value = revisions(facts.getAsJsonObject("automatic_candidates"), "catalogRevision", "researchRevision");
        var candidates = (AutomaticWeaponPlacementCandidateSnapshot) DesignerCapturedSettings.read(AutomaticWeaponPlacementCandidateSnapshot.class, value);
        return Map.of(candidates.treeId(), candidates);
    }
    public Optional<AutomaticWeaponPrerequisitePlan> prerequisitePlan() {
        var facts = json.getAsJsonObject("registry_facts");
        if (!facts.has("automatic_prerequisite_plan")) return Optional.empty();
        return Optional.of((AutomaticWeaponPrerequisitePlan) DesignerCapturedSettings.read(AutomaticWeaponPrerequisitePlan.class,
                revisions(facts.getAsJsonObject("automatic_prerequisite_plan"), "catalogRevision", "researchRevision")));
    }
    public BlueprintProgressionPolicySnapshot progression(BlueprintResearchSnapshot snapshot) {
        return BlueprintProgressionPolicyResolver.resolve(snapshot, catalog, 1, 1,
                snapshot == source ? automatic() : Map.of(), DesignerCapturedSettings.features(capture));
    }
    public BlueprintCraftingPolicySnapshot crafting(BlueprintResearchSnapshot snapshot) {
        var facts = json.getAsJsonObject("registry_facts");
        if (!facts.has("weapon_evidence") || !facts.has("ammo_associations"))
            throw new IllegalArgumentException("Capture lacks crafting evidence; capture the source again");
        var evidence = (AutomaticWeaponEvidenceSnapshot) DesignerCapturedSettings.read(AutomaticWeaponEvidenceSnapshot.class,
                revisions(facts.getAsJsonObject("weapon_evidence"), "catalogRevision"));
        var ammo = (BlueprintAmmoAssociationSnapshot) DesignerCapturedSettings.read(BlueprintAmmoAssociationSnapshot.class,
                revisions(facts.getAsJsonObject("ammo_associations"), "catalogRevision", "sourceRevision"));
        return BlueprintCraftingPolicyResolver.resolve(snapshot, catalog, 1, 1, 1, snapshot == source ? automatic() : Map.of(),
                new AutomaticWeaponEvidenceManager.Publication(evidence, 1, AutomaticWeaponEvidenceManager.PublicationState.READY),
                new BlueprintAmmoAssociationManager.Publication(ammo, 1, 1, BlueprintAmmoAssociationManager.PublicationState.READY),
                DesignerCapturedSettings.features(capture));
    }
    private static JsonObject revisions(JsonObject value, String... names) {
        var copy = value.deepCopy(); for (String name : names) copy.addProperty(name, 1); return copy;
    }
}
