package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.gamergaming.taczweaponblueprints.research.tree.*;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import net.minecraft.resources.ResourceLocation;

class ResearchTechTreeBranchesTest {
    @Test void exampleRoundTripsAndPreservesProgressionExactly() {
        var tree=AuthoredBranchFixture.definition();var bundle=AuthoredBranchFixture.bundle();
        assertEquals(tree,ResearchTechTreeDefinition.CODEC.parse(JsonOps.INSTANCE,ResearchTechTreeDefinition.CODEC.encodeStart(JsonOps.INSTANCE,tree).getOrThrow()).getOrThrow());
        assertEquals(bundle,ResearchTechTreeEntryBundle.CODEC.parse(JsonOps.INSTANCE,ResearchTechTreeEntryBundle.CODEC.encodeStart(JsonOps.INSTANCE,bundle).getOrThrow()).getOrThrow());
        var authored=AuthoredBranchFixture.publication(true);var previous=AuthoredBranchFixture.publication(false);
        assertEquals(previous.graph(),authored.graph());assertEquals(previous.presentation(),authored.presentation());
        assertEquals(previous.techTree().domains().get(0).lanes(),authored.techTree().domains().get(0).lanes());
        assertEquals(5,authored.techTree().domain(Domain.WEAPONS).orElseThrow().branches().size());
    }
    @Test void oldFormatsCannotSilentlyAcceptBranchExtensions() {
        var tree=ResearchTechTreeDefinition.CODEC.encodeStart(JsonOps.INSTANCE,AuthoredBranchFixture.definition()).getOrThrow().getAsJsonObject();
        tree.addProperty("format",2);assertTrue(ResearchTechTreeDefinition.CODEC.parse(JsonOps.INSTANCE,tree).error().isPresent());
        var bundle=ResearchTechTreeEntryBundle.CODEC.encodeStart(JsonOps.INSTANCE,AuthoredBranchFixture.bundle()).getOrThrow().getAsJsonObject();
        bundle.addProperty("format",2);assertTrue(ResearchTechTreeEntryBundle.CODEC.parse(JsonOps.INSTANCE,bundle).error().isPresent());
    }
    @Test void oldFormatsRejectEvenEmptyNewFieldsAndLabelsRequireAnExplicitRule() {
        var tree = ResearchTechTreeDefinition.CODEC.encodeStart(JsonOps.INSTANCE, AuthoredBranchFixture.definition())
                .getOrThrow().getAsJsonObject();
        var bundle = ResearchTechTreeEntryBundle.CODEC.encodeStart(JsonOps.INSTANCE, AuthoredBranchFixture.bundle())
                .getOrThrow().getAsJsonObject();
        tree.addProperty("format", 2); tree.add("branches", new JsonArray());
        bundle.addProperty("format", 2); bundle.add("branch_memberships", new JsonArray());
        assertTrue(ResearchTechTreeDefinition.CODEC.parse(JsonOps.INSTANCE, tree).error().isPresent());
        assertTrue(ResearchTechTreeEntryBundle.CODEC.parse(JsonOps.INSTANCE, bundle).error().isPresent());
        var definition = ResearchTechTreeBranchDefinition.CODEC.encodeStart(JsonOps.INSTANCE,
                AuthoredBranchFixture.definition().branches().get(0)).getOrThrow().getAsJsonObject();
        definition.remove("label_visibility");
        assertTrue(ResearchTechTreeBranchDefinition.CODEC.parse(JsonOps.INSTANCE, definition).error().isPresent());
        definition.addProperty("label_visibility", "anchor_revealed"); definition.remove("anchor");
        assertTrue(ResearchTechTreeBranchDefinition.CODEC.parse(JsonOps.INSTANCE, definition).error().isPresent());
    }

    @Test void definitionsRejectCyclesMissingParentsCrossDomainsAndOversizedLabels() {
        for(String change:List.of("cycle","missing","domain","label","unknown")) {
            var json=ResearchTechTreeDefinition.CODEC.encodeStart(JsonOps.INSTANCE,AuthoredBranchFixture.definition()).getOrThrow().getAsJsonObject();
            var branch=json.getAsJsonArray("branches").get(0).getAsJsonObject();
            switch(change){case "cycle" -> branch.addProperty("parent","example:rapid");case "missing" -> branch.addProperty("parent","example:missing");case "domain" -> branch.addProperty("domain","ammo");case "label" -> branch.addProperty("title","x".repeat(81));case "unknown" -> branch.addProperty("coordinates",12);}
            assertTrue(ResearchTechTreeDefinition.CODEC.parse(JsonOps.INSTANCE,json).error().isPresent(),change);
        }
    }
    @Test void membershipsRejectDuplicatesMissingBranchesAndForeignAnchors() {
        var tree=AuthoredBranchFixture.definition();var bundle=AuthoredBranchFixture.bundle();var list=new ArrayList<>(bundle.branchMemberships());
        list.add(list.get(0));var duplicate=new ResearchTechTreeEntryBundle(3,bundle.tree(),0,bundle.entries(),list);
        assertThrows(IllegalArgumentException.class,()->ResearchTechTreeBranches.compile(bundle.tree(),tree,Map.of(bundle.tree(),duplicate)));
        var missing=new ResearchTechTreeEntryBundle(3,bundle.tree(),0,bundle.entries(),List.of(new ResearchTechTreeBranchMembership(ResourceLocation.parse("test:missing"),List.of(ResourceLocation.parse("tacz:uzi")))));
        assertThrows(IllegalArgumentException.class,()->ResearchTechTreeBranches.compile(bundle.tree(),tree,Map.of(bundle.tree(),missing)));
        var declarations=new ArrayList<>(tree.branches());var b=declarations.get(1);
        declarations.set(1,new ResearchTechTreeBranchDefinition(b.id(),b.domain(),b.parent(),b.title(),b.translationKey(),b.order(),Optional.of(ResourceLocation.parse("tacz:ak47")),b.labelVisibility()));
        var invalid=new ResearchTechTreeDefinition(3,tree.title(),tree.translationKey(),tree.icon(),tree.weaponPlacementMode(),tree.layout(),tree.bandPolicy(),tree.tiers(),tree.domains(),declarations);
        assertThrows(IllegalArgumentException.class,()->ResearchTechTreeBranches.compile(bundle.tree(),invalid,Map.of(bundle.tree(),bundle)));
    }
    @Test void publicationPrunesHiddenBranchesAndAnchorLabelsWithoutLeakingCounts() {
        var snapshot=AuthoredBranchFixture.snapshot(true);
        var visible=Set.of(ResourceLocation.parse("tacz:ak47"));
        var branches=ResearchTechTreeBranches.publish(snapshot,AuthoredBranchFixture.TREE,Domain.WEAPONS,visible,Map.of(visible.iterator().next(),visible.iterator().next()),visible);
        assertEquals(Set.of(ResourceLocation.parse("example:foundation"),ResourceLocation.parse("example:rifle")),branches.stream().map(ResearchTechTreeBranch::id).collect(java.util.stream.Collectors.toSet()));
        assertEquals(1,branches.stream().mapToInt(b -> b.members().size()).sum());
        assertTrue(branches.stream().filter(b -> b.id().getPath().equals("foundation")).findFirst().orElseThrow().anchor().isEmpty());
        assertTrue(ResearchTechTreeBranches.publish(snapshot,AuthoredBranchFixture.TREE,Domain.WEAPONS,Set.of(),Map.of(),Set.of()).isEmpty());
    }
    @Test void catalogValidationNamesMissingBlueprintAndItsBranch() {
        var catalog=new HashMap<>(AuthoredBranchFixture.catalog());catalog.remove(ResourceLocation.parse("tacz:uzi"));
        var error=assertThrows(IllegalArgumentException.class,()->ResearchTechTreeCatalogValidator.validate(AuthoredBranchFixture.snapshot(true),catalog));
        assertTrue(error.getMessage().contains("tacz:uzi"));assertTrue(error.getMessage().contains("example:smg"));
    }

    @Test void anchorGatedLabelsStayPrivateForHiddenAndAnonymousAnchors() {
        var original = AuthoredBranchFixture.snapshot(true);
        var tree = AuthoredBranchFixture.definition();
        var declarations = tree.branches().stream().map(b -> new ResearchTechTreeBranchDefinition(
                b.id(), b.domain(), b.parent(), b.title(), Optional.of("secret." + b.id().getPath()),
                b.order(), b.anchor(), ResearchTechTreeBranchDefinition.LabelVisibility.ANCHOR_REVEALED)).toList();
        var gated = new ResearchTechTreeDefinition(3, tree.title(), tree.translationKey(), tree.icon(),
                tree.weaponPlacementMode(), tree.layout(), tree.bandPolicy(), tree.tiers(), tree.domains(), declarations);
        var snapshot = BlueprintResearchSnapshot.create(Map.of(), original.profiles(), original.rules(),
                Map.of(), Map.of(AuthoredBranchFixture.TREE, gated), original.techTreeEntryBundles());
        var gun = ResourceLocation.parse("tacz:uzi");
        var anonymous = ResearchTreeGraph.redactedNodeId(0, 0);
        for (boolean revealed : List.of(false, true)) {
            var publicId = revealed ? gun : anonymous;
            var published = ResearchTechTreeBranches.publish(snapshot, AuthoredBranchFixture.TREE,
                    Domain.WEAPONS, Set.of(publicId), Map.of(gun, publicId), revealed ? Set.of(gun) : Set.of());
            var smg = published.stream().filter(b -> b.id().getPath().equals("smg")).findFirst().orElseThrow();
            assertEquals(revealed, smg.anchor().isPresent());
            assertEquals(revealed, !smg.title().isEmpty());
            assertEquals(revealed, smg.translationKey().isPresent());
            var foundation = published.stream().filter(b -> b.id().getPath().equals("foundation")).findFirst().orElseThrow();
            assertEquals("", foundation.title());
            assertTrue(foundation.anchor().isEmpty());
            assertTrue(foundation.translationKey().isEmpty());
        }
    }

    @Test void publicContainmentEnforcesDepthCountOwnershipAndPopulatedSubtrees() {
        var node = ResourceLocation.parse("test:node");
        var chain = new ArrayList<ResearchTechTreeBranch>();
        for (int n = 0; n < 17; n++) {
            chain.add(new ResearchTechTreeBranch(ResourceLocation.parse("test:b" + n),
                    n == 0 ? Optional.empty() : Optional.of(ResourceLocation.parse("test:b" + (n - 1))),
                    "Branch", Optional.empty(), n, Optional.empty(), List.of(node)));
            var current = new ArrayList<>(chain);
            for (int j = 0; j < current.size() - 1; j++) {
                var b = current.get(j);
                current.set(j, new ResearchTechTreeBranch(b.id(), b.parent(), b.title(), b.translationKey(),
                        b.order(), b.anchor(), List.of()));
            }
            if (n < 16) assertDoesNotThrow(() -> ResearchTechTreeBranch.validate(current, Set.of(node)));
            else assertThrows(IllegalArgumentException.class, () -> ResearchTechTreeBranch.validate(current, Set.of(node)));
        }
        assertThrows(IllegalArgumentException.class, () -> ResearchTechTreeBranch.validate(
                Collections.nCopies(129, chain.get(0)), Set.of(node)));
        var empty = new ResearchTechTreeBranch(ResourceLocation.parse("test:empty"), Optional.empty(), "Empty",
                Optional.empty(), 0, Optional.empty(), List.of());
        assertThrows(IllegalArgumentException.class, () -> ResearchTechTreeBranch.validate(List.of(empty), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> ResearchTechTreeBranch.validate(List.of(chain.get(0)), Set.of()));
    }

    @Test void exportIncludesSortedDeclarationsAndMembershipWithoutChangingEntryPolicy() {
        var authored = JsonParser.parseString(BlueprintResearchCatalogExporter.export(
                AuthoredBranchFixture.snapshot(true), AuthoredBranchFixture.catalog(), AuthoredBranchFixture.TREE)).getAsJsonObject();
        var legacy = JsonParser.parseString(BlueprintResearchCatalogExporter.export(
                AuthoredBranchFixture.snapshot(false), AuthoredBranchFixture.catalog(), AuthoredBranchFixture.TREE)).getAsJsonObject();
        assertEquals(22, authored.get("format").getAsInt());
        assertEquals(5, authored.getAsJsonArray("authored_branches").size());
        assertTrue(legacy.getAsJsonArray("authored_branches").isEmpty());
        authored.remove("authored_branches"); legacy.remove("authored_branches");
        assertEquals(legacy, authored);
    }
}
