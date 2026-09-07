package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.*;
import com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import static com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPolicy.*;

/** Target > profile > legacy Analyzer reconstruction > one enabled shared fallback. */
public record AnalysisReconstructionDefinition(int format, Optional<ResourceLocation> profile,
        Optional<ResourceLocation> target, boolean enabled, BlueprintAnalyzerPolicy.Reconstruction fragments,
        boolean recyclable, PhysicalBlueprintLearningMode learning) {
    private static final Codec<AnalysisReconstructionDefinition> RAW = RecordCodecBuilder.create(i -> i.group(
            AnalyzerOutcomeTable.integer(1, 1).fieldOf("format").forGetter(AnalysisReconstructionDefinition::format),
            optional("profile", ID).forGetter(AnalysisReconstructionDefinition::profile),
            optional("target", ID).forGetter(AnalysisReconstructionDefinition::target),
            defaulted("enabled", BOOL, true).forGetter(AnalysisReconstructionDefinition::enabled),
            BlueprintAnalyzerPolicy.Reconstruction.CODEC.fieldOf("fragments").forGetter(AnalysisReconstructionDefinition::fragments),
            defaulted("recyclable", BOOL, true).forGetter(AnalysisReconstructionDefinition::recyclable),
            defaulted("learning", BlueprintAnalyzerPolicy.enumCodec(PhysicalBlueprintLearningMode.class), PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES).forGetter(AnalysisReconstructionDefinition::learning)
    ).apply(i, AnalysisReconstructionDefinition::new));
    public static final Codec<AnalysisReconstructionDefinition> CODEC = StrictRecordCodec.wrap("shared analysis reconstruction", RAW,
            "format", "profile", "target", "enabled", "fragments", "recyclable", "learning");
    public AnalysisReconstructionDefinition {
        Objects.requireNonNull(profile); Objects.requireNonNull(target); Objects.requireNonNull(fragments); Objects.requireNonNull(learning);
        if (format != 1) throw new IllegalArgumentException("unsupported reconstruction format");
        if (enabled && !learning.learningPermitted()) throw new IllegalArgumentException("enabled reconstruction must permit learning");
    }
    public boolean fallback() { return profile.isEmpty() && target.isEmpty(); }
    public int specificity() { return target.isPresent() ? (profile.isPresent() ? 3 : 2) : profile.isPresent() ? 1 : 0; }
    public boolean matches(ResourceLocation activeProfile, ResourceLocation weapon) {
        return profile.map(activeProfile::equals).orElse(true) && target.map(weapon::equals).orElse(true);
    }
}
