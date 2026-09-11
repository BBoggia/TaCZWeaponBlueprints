package com.gamergaming.taczweaponblueprints.client;

/** A shared region encloses child branches; its landmark identifies only its own foundation nodes. */
final class ResearchTreeSceneLandmarks {
    static ResearchTreeScene.Bounds bounds(ResearchTreeScene scene, ResearchTreeScene.Region region) {
        if (!region.seed().id().equals("automatic/shared")) return region.bounds();
        var members = java.util.Set.copyOf(region.seed().directMembers());
        return scene.nodes().stream().filter(n -> members.contains(n.id())).map(ResearchTreeScene.Node::body)
                .reduce(ResearchTreeScene.Bounds::union).orElse(region.bounds());
    }
    private ResearchTreeSceneLandmarks() { }
}
