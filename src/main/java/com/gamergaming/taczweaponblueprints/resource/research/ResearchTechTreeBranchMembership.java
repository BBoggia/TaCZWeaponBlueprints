package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.*;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeBranch;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Exact presentation ownership, independent of priority-selected progression placements. */
public record ResearchTechTreeBranchMembership(ResourceLocation branch, List<ResourceLocation> members) {
    private static final Codec<ResearchTechTreeBranchMembership> RAW = RecordCodecBuilder.create(i -> i.group(
            BlueprintResearchCodecs.RESOURCE_LOCATION.fieldOf("branch").forGetter(ResearchTechTreeBranchMembership::branch),
            BlueprintResearchCodecs.RESOURCE_LOCATION.listOf().fieldOf("members").forGetter(ResearchTechTreeBranchMembership::members)
    ).apply(i, ResearchTechTreeBranchMembership::new));
    public static final Codec<ResearchTechTreeBranchMembership> CODEC = StrictRecordCodec.wrap("Tech Tree branch membership",RAW,"branch","members");
    public ResearchTechTreeBranchMembership {
        members = List.copyOf(members);
        new ResearchTechTreeBranch(branch,Optional.empty(),"",Optional.empty(),0,Optional.empty(),members);
        if (members.isEmpty()) throw new IllegalArgumentException("Empty branch membership " + branch);
    }
}
