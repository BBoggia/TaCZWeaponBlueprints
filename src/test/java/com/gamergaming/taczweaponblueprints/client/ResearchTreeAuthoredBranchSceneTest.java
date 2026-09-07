package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.research.tree.*;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import java.util.*;

class ResearchTreeAuthoredBranchSceneTest {
    private static ResearchTreeScenePolicy policy() {
        return new ResearchTreeScenePolicy(new ResearchTechTreeLayoutPolicy(20,18,8,20,4,1000,6,6),1024,
                new ResearchTreeScenePolicy.NodeMetrics(96,44,3),"authored",new ResearchTreeScenePolicy.RegionMetrics(44,12,16,80,16,10,2));
    }
    @Test void publishedNestedOwnershipDrivesContainedNonOverlappingRegions() throws Exception {
        var publication=AuthoredBranchFixture.publication(true);
        var projection=ResearchTechTreeProjectionBuilder.build(publication).projection(Domain.WEAPONS).orElseThrow();
        var input=ResearchTreeSceneInput.from(projection,policy());
        var scene=new ResearchTreeBranchSceneEngine().layout(input);
        assertEquals(5,scene.regions().size());
        assertSame(scene, ResearchTreeSceneValidator.validate(input, ResearchTreeBranchSceneEngine.REVISION, scene));
        var output = java.nio.file.Path.of("build/reports/research-tree/authored-branches");
        java.nio.file.Files.createDirectories(output);
        ResearchTreeSceneSvgWriter.write(output.resolve("example.svg"), "Authored branch example", input, scene);

        var regions=scene.regions().stream().collect(java.util.stream.Collectors.toMap(r -> r.seed().id(),r -> r));
        assertTrue(regions.get("authored/example:smg").bounds().contains(regions.get("authored/example:controlled").bounds()));
        assertTrue(regions.get("authored/example:foundation").bounds().contains(regions.get("authored/example:rifle").bounds()));
        var left=regions.get("authored/example:smg").bounds();var right=regions.get("authored/example:rifle").bounds();
        assertTrue(left.right() <= right.x() || right.right() <= left.x());
        assertEquals(publication.graph().nodes().size(),scene.nodes().size());
    }
    @Test void labelChangesRebindPresentationWithoutRelayingOutTheGraph() {
        var publication=AuthoredBranchFixture.publication(true);var tree=publication.techTree();var domain=tree.domains().get(0);
        var branches=domain.branches().stream().map(b -> new ResearchTechTreeBranch(b.id(),b.parent(),"New title",b.translationKey(),b.order(),b.anchor(),b.members())).toList();
        var renamed=new ResearchTreePublication(publication.graph(),publication.presentation(),new ResearchTechTreePresentation(tree.treeId(),tree.title(),tree.translationKey(),tree.iconNodeId(),tree.tiers(),tree.bands(),tree.maxNodesPerLayer(),
                List.of(new ResearchTechTreePresentation.DomainView(domain.domain(),domain.title(),domain.translationKey(),domain.iconNodeId(),domain.lanes(),branches))));
        var cache=new ResearchTreeSceneCache(p -> new ResearchTreeBranchSceneEngine(),new ResearchTreeSimpleSceneEngine());cache.bind(publication);
        var old=cache.scene(publication,Domain.WEAPONS,policy());cache.bind(renamed);
        var next=cache.scene(renamed,Domain.WEAPONS,policy());
        assertEquals(ResearchTreeSceneCache.Status.VALID,next.status());assertSame(old.geometry().orElseThrow(),next.geometry().orElseThrow());
        assertEquals("New title",next.presentation().orElseThrow().presentation().branches().get(0).title());
    }
}
