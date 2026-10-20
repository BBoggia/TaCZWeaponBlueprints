package com.gamergaming.taczweaponblueprints.research.tree;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.mojang.serialization.*;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import net.minecraft.resources.ResourceLocation;

/** The shipped example is also the server/client integration fixture. */
public final class AuthoredBranchFixture {
    public static final ResourceLocation TREE = id("example:nested");
    public static final Path ROOT = Path.of("examples/authored-branches-datapack/data/example/taczweaponblueprints");
    public static ResourceLocation id(String s) { return ResourceLocation.parse(s); }
    public static <T> T read(Codec<T> codec,String file) {
        try { return codec.parse(JsonOps.INSTANCE,JsonParser.parseString(Files.readString(ROOT.resolve(file)))).getOrThrow(); }
        catch (Exception e) { throw new IllegalArgumentException(file + ": " + e.getMessage(),e); }
    }
    public static ResearchTechTreeDefinition definition() { return read(ResearchTechTreeDefinition.CODEC,"research_tech_trees/nested.json"); }
    public static ResearchTechTreeEntryBundle bundle() { return read(ResearchTechTreeEntryBundle.CODEC,"research_tech_tree_entries/nested.json"); }
    public static BlueprintResearchSnapshot snapshot(boolean branches) {
        var tree = definition();var entries = bundle();
        if (!branches) {
            tree = new ResearchTechTreeDefinition(2,tree.title(),tree.translationKey(),tree.icon(),tree.weaponPlacementMode(),tree.layout(),tree.bandPolicy(),tree.tiers(),tree.domains());
            entries = new ResearchTechTreeEntryBundle(2,entries.tree(),entries.priority(),entries.entries());
        }
        Map<ResourceLocation,BlueprintResearchRule> rules = new LinkedHashMap<>();
        for (var entry : entries.entries()) {
            var target = entry.target().blueprints().get(0);
            rules.put(id("example:"+target.getPath()),read(BlueprintResearchRule.CODEC,"research_rules/"+target.getPath()+".json"));
        }
        return BlueprintResearchSnapshot.create(Map.of(),Map.of(TREE,read(BlueprintResearchProfile.CODEC,"research_profiles/nested.json")),rules,Map.of(),Map.of(TREE,tree),Map.of(TREE,entries));
    }
    public static Map<ResourceLocation,BlueprintData> catalog() {
        Map<ResourceLocation,BlueprintData> result = new LinkedHashMap<>();
        for (var entry : bundle().entries()) {
            var id=entry.target().blueprints().get(0);
            result.put(id,new BlueprintData(id.toString(),"name."+id.getPath(),"tooltip."+id.getPath(),id,null,"pistol",id,BlueprintKind.GUN));
        }
        return result;
    }
    public static ResearchTreePublication publication(boolean branches) {
        var snapshot=snapshot(branches); var catalog=catalog();
        ResearchTechTreeCatalogValidator.validate(snapshot,catalog);
        return ResearchTreeBuilder.buildPublication(catalog,snapshot,
                new BlueprintProgressionConfigSnapshot(true,true,true,JournalVisibility.FULL,true,
                        DuplicateBlueprintPolicy.MANUAL_RECYCLING,false,100,false,TREE),new PlayerRecipeData(),ignored -> false);
    }
}
