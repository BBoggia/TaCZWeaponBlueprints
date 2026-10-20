package com.gamergaming.taczweaponblueprints.research.designer.server;

import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.research.designer.*;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import java.util.*;
import java.lang.reflect.Modifier;
import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import static com.gamergaming.taczweaponblueprints.research.designer.DesignerValues.*;

/** Server-thread adapter. Caller must authorize before invoking; reads never refresh live publications. */
public final class ResearchDesignerCaptureService {
    private ResearchDesignerCaptureService() {}
    public static ResearchDesignerCapture capture(MinecraftServer server, UUID session) {
        if (!server.isSameThread()) throw new IllegalStateException("Capture must run on the server thread");
        for (int attempt = 0; attempt < 2; attempt++) {
            var c = ProgressionPolicyAccessService.acquire(ProgressionPolicyAccessService.Mode.CURRENT_ONLY)
                    .orElseThrow(() -> new IllegalStateException("Research data is reloading; retry capture"));
            if (c.research().snapshot().usesAutomaticWeaponPlacement(c.profileId())
                    && c.automatic().health().state()!=com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateManager.PublicationState.READY)
                throw new IllegalStateException("Automatic placement is not ready; retry capture");
            var config = ModConfigs.BLUEPRINT;
            var settings = settings(); var progression = config.progressionSnapshot(); var access = config.accessSnapshot();
            var dependencies = new JsonObject(); var mods = new JsonObject();
            net.neoforged.fml.ModList.get().getMods().forEach(mod -> mods.addProperty(mod.getModId(), mod.getVersion().toString()));
            dependencies.add("mods", mods);
            var context = DesignerCaptureAssembly.assemble(c, progression, access, config::isItemBlacklisted,
                    settings, new JsonObject(), dependencies, (kind,id) -> server.getResourceManager()
                            .getResource(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "taczweaponblueprints/" + kind + "/" + id.getPath() + ".json"))
                            .map(r -> safePackName(r.sourcePackId())).orElse("runtime"));
            var facts = registryFacts(server, context, c);
            context.add("registry_facts", facts);
            var after = ProgressionPolicyAccessService.acquire(ProgressionPolicyAccessService.Mode.CURRENT_ONLY);
            if (after.isEmpty() || !after.get().revisionIdentity().equals(c.revisionIdentity())
                    || !after.get().profileId().equals(c.profileId()) || !settings.equals(settings())
                    || !facts.equals(registryFacts(server, context, c))) continue;
            var revisions = new JsonObject(); revisions.addProperty("catalog", c.catalog().revision());
            revisions.addProperty("research", c.research().revision()); revisions.addProperty("automatic", c.automatic().revision());
            revisions.addProperty("config", DesignerFingerprint.of(settings)); revisions.addProperty("access", DesignerFingerprint.of(evidence(access)));
            revisions.addProperty("tags", DesignerFingerprint.of(facts));
            return ResearchDesignerCapture.create(session, revisions, context);
        }
        throw new IllegalStateException("Research data changed during capture; retry capture");
    }
    private static String safePackName(String name) {
        // Resource pack identifiers are labels, never a local path disclosure channel.
        if (name == null || name.contains("/") || name.contains("\\") || name.length() > 128) return "external_pack";
        return name;
    }
    public static JsonObject settings() {
        var config = ModConfigs.BLUEPRINT;
        if (!com.gamergaming.taczweaponblueprints.progression.ResearchFeatureConfigSnapshot.from(config).equals(config.researchFeatureSnapshot())
                || !com.gamergaming.taczweaponblueprints.progression.BlueprintAccessConfigSnapshot.from(config).equals(config.accessSnapshot())
                || !com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot.from(config).equals(config.progressionSnapshot()))
            throw new IllegalStateException("Configuration publication is changing");
        var result = new JsonObject(); var raw = new JsonObject();
        for (var field : config.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || !ValidatedField.class.isAssignableFrom(field.getType())) continue;
            try { raw.add(field.getName(), evidence(((ValidatedField<?>)field.get(config)).get())); }
            catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }
        result.add("raw", raw); var effective = new JsonObject();
        effective.add("progression", evidence(config.progressionSnapshot()));
        effective.add("features", evidence(config.researchFeatureSnapshot()));
        effective.add("access", evidence(config.accessSnapshot())); effective.add("balance", evidence(config.balanceSettings()));
        effective.add("awards", evidence(config.awardSnapshot())); result.add("effective", effective); return result;
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static JsonObject registryFacts(MinecraftServer server, JsonObject context, ProgressionPolicyAccessService.Context c) {
        var facts = new JsonObject(); var recipes = new JsonObject();
        for (var entry : new TreeMap<>(c.catalog().blueprints()).entrySet()) {
            var data = entry.getValue(); var recipe = data.getRecipe();
            if (recipe == null || data.getRecipeId() == null) throw new IllegalStateException("Catalog recipe is unavailable");
            if (server.getRecipeManager().byKey(data.getRecipeId()).filter(r -> r.value() == recipe).isEmpty())
                throw new IllegalStateException("Catalog recipe revision changed");
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
            try {
                ((net.minecraft.world.item.crafting.RecipeSerializer)recipe.getSerializer()).streamCodec().encode(buffer, recipe);
                if (buffer.readableBytes() > 1024 * 1024) throw new IllegalStateException("Recipe exceeds capture limit");
                byte[] payload = new byte[buffer.readableBytes()]; buffer.readBytes(payload);
                var record = new JsonObject(); record.addProperty("serializer", BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).toString());
                record.addProperty("network_payload", Base64.getEncoder().encodeToString(payload));
                // A recipe batch can exceed the 1.21 ItemStack codec's count limit.
                // Capture item identity/components and the full batch quantity separately.
                var output = recipe.getOutput();
                record.addProperty("output_nbt", output.copyWithCount(1).save(server.registryAccess()).toString());
                record.addProperty("output_count", output.getCount());
                recipes.add(data.getRecipeId().toString(),record);
            } finally { buffer.release(); }
        }
        facts.add("recipes", recipes);
        var associations=BlueprintAmmoAssociationManager.INSTANCE.publication();
        if(associations.revision()!=c.policy().ammoAssociationRevision() || associations.catalogRevision()!=c.catalog().revision())
            throw new IllegalStateException("Ammo associations changed during capture");
        var ammo=evidence(associations.snapshot()).getAsJsonObject();ammo.remove("catalogRevision");ammo.remove("sourceRevision");
        facts.add("ammo_associations",ammo);
        var weaponEvidence=com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceManager.INSTANCE.publication();
        if(weaponEvidence.revision()!=c.policy().evidenceRevision() || !weaponEvidence.readyForCatalogRevision(c.catalog().revision()))
            throw new IllegalStateException("Weapon evidence changed during capture");
        var evidenceValue=evidence(weaponEvidence.snapshot()).getAsJsonObject();evidenceValue.remove("catalogRevision");
        facts.add("weapon_evidence",evidenceValue);
        var activeTree=c.research().snapshot().profiles().get(c.profileId()).techTree().orElseThrow();
        var positioned=c.automatic().snapshotsByTree().get(activeTree);
        if(positioned!=null){var value=evidence(positioned).getAsJsonObject();value.remove("catalogRevision");value.remove("researchRevision");facts.add("automatic_candidates",value);}
        var classification=c.automatic().classificationsByTree().get(activeTree);
        if(classification!=null){var value=evidence(classification).getAsJsonObject();value.remove("catalogRevision");value.remove("researchRevision");facts.add("automatic_classification",value);}
        var plan=c.automatic().prerequisitePlansByProfile().get(c.profileId());
        if(plan!=null){var value=evidence(plan).getAsJsonObject();value.remove("catalogRevision");value.remove("researchRevision");facts.add("automatic_prerequisite_plan",value);}

        Set<String> strings = new TreeSet<>(); collect(context.get("definitions"), strings); collect(context.get("settings"), strings);
        var items = new JsonObject(); var tags = new JsonObject(); var advancements = new JsonObject();
        for (String value : strings) {
            var id = ResourceLocation.tryParse(value.startsWith("#") ? value.substring(1) : value);
            if (id == null || !value.contains(":")) continue;
            if (BuiltInRegistries.ITEM.containsKey(id)) items.addProperty(id.toString(), BuiltInRegistries.ITEM.get(id).getDefaultInstance().getMaxStackSize());
            var members = new JsonArray();
            BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).ifPresent(holders -> holders.stream()
                    .map(h -> BuiltInRegistries.ITEM.getKey(h.value()).toString()).sorted().forEach(members::add));
            tags.add(id.toString(), members);
            var advancement = server.getAdvancements().get(id);
            if (advancement != null) advancements.add(id.toString(), evidence(new TreeSet<>(advancement.value().criteria().keySet())));
        }
        facts.add("items", items); facts.add("item_tags", tags); facts.add("advancements", advancements);
        return facts;
    }
    private static void collect(JsonElement value, Set<String> values) {
        if (value.isJsonObject()) value.getAsJsonObject().entrySet().forEach(e -> { values.add(e.getKey()); collect(e.getValue(),values); });
        else if (value.isJsonArray()) value.getAsJsonArray().forEach(v -> collect(v,values));
        else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) values.add(value.getAsString());
    }
}
