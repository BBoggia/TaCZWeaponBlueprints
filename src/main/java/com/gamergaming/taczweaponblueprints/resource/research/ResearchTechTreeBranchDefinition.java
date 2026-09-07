package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.*;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeBranch;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Tree-owned structure and explicitly authorized public labels. */
public record ResearchTechTreeBranchDefinition(ResourceLocation id, Domain domain,
        Optional<ResourceLocation> parent, String title, Optional<String> translationKey,
        int order, Optional<ResourceLocation> anchor, LabelVisibility labelVisibility) {
    public enum LabelVisibility { PUBLIC_WHEN_POPULATED, ANCHOR_REVEALED }
    private static final Codec<ResearchTechTreeBranchDefinition> RAW = RecordCodecBuilder.create(i -> i.group(
            BlueprintResearchCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(ResearchTechTreeBranchDefinition::id),
            Codec.STRING.xmap(s -> Domain.valueOf(s.toUpperCase(Locale.ROOT)), d -> d.name().toLowerCase(Locale.ROOT)).fieldOf("domain").forGetter(ResearchTechTreeBranchDefinition::domain),
            new StrictOptionalFieldCodec<>("parent", BlueprintResearchCodecs.RESOURCE_LOCATION).forGetter(ResearchTechTreeBranchDefinition::parent),
            Codec.STRING.fieldOf("title").forGetter(ResearchTechTreeBranchDefinition::title),
            new StrictOptionalFieldCodec<>("translation_key", Codec.STRING).forGetter(ResearchTechTreeBranchDefinition::translationKey),
            Codec.intRange(0,1_000_000).fieldOf("order").forGetter(ResearchTechTreeBranchDefinition::order),
            new StrictOptionalFieldCodec<>("anchor", BlueprintResearchCodecs.RESOURCE_LOCATION).forGetter(ResearchTechTreeBranchDefinition::anchor),
            Codec.STRING.xmap(s -> LabelVisibility.valueOf(s.toUpperCase(Locale.ROOT)), v -> v.name().toLowerCase(Locale.ROOT)).fieldOf("label_visibility").forGetter(ResearchTechTreeBranchDefinition::labelVisibility)
    ).apply(i, ResearchTechTreeBranchDefinition::new));
    public static final Codec<ResearchTechTreeBranchDefinition> CODEC = StrictRecordCodec.wrap("Tech Tree branch",RAW,
            "id","domain","parent","title","translation_key","order","anchor","label_visibility");
    public ResearchTechTreeBranchDefinition {
        Objects.requireNonNull(domain); Objects.requireNonNull(labelVisibility);
        new ResearchTechTreeBranch(id,parent,title,translationKey,order,anchor,List.of());
        if (title.isBlank() || labelVisibility == LabelVisibility.ANCHOR_REVEALED && anchor.isEmpty())
            throw new IllegalArgumentException("Branch " + id + " needs a title and anchor for anchor_revealed labels");
    }
}
