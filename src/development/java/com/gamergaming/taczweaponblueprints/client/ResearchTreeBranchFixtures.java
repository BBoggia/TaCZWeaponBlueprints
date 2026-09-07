package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;

/** Explicit development branch intent, separate from production inference and future authoring formats. */
public final class ResearchTreeBranchFixtures {
    private ResearchTreeBranchFixtures() { }

    public static ResearchTreeSceneInput input(ResearchTreeBaselineFixtures.Fixture fixture, int viewportWidth) {
        var policy = ResearchTreeScenePolicy.compatibility(fixture.input().policy(), viewportWidth);
        if (fixture.input().projections().isEmpty()) return ResearchTreeSceneInput.empty(Domain.WEAPONS, policy);
        var input = ResearchTreeSceneInput.from(fixture.input().projections().get(0).replay(), policy);
        return input.withBranchSeeds(seeds(fixture));
    }

    static List<ResearchTreeBranchStructure.Seed> seeds(ResearchTreeBaselineFixtures.Fixture fixture) {
        var branches = fixture.branches();
        boolean hasBase = branches.stream().anyMatch(b -> b.id().equals("base"));
        var result = new ArrayList<ResearchTreeBranchStructure.Seed>();
        for (int i = 0; i < branches.size(); i++) {
            var b = branches.get(i);
            // These named M0 fixtures explicitly intend a shared foundation/terminal merge.
            // This convention is never applied to an actual player projection or its lanes.
            if (hasBase && (b.id().equals("merge") || b.id().equals("shared"))) continue;
            var members = new ArrayList<>(b.directMembers());
            if (b.id().equals("base")) branches.stream().filter(other ->
                    other.id().equals("merge") || other.id().equals("shared"))
                    .forEach(other -> members.addAll(other.directMembers()));
            Optional<String> parent = b.parent();
            if (hasBase && !b.id().equals("base") && parent.isEmpty()) parent = Optional.of("base");
            result.add(new ResearchTreeBranchStructure.Seed(b.id(), parent, i, members));
        }
        return List.copyOf(result);
    }
}
