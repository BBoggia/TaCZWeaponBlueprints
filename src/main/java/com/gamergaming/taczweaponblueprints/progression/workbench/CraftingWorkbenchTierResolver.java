package com.gamergaming.taczweaponblueprints.progression.workbench;

import java.util.Map;

import com.gamergaming.taczweaponblueprints.progression.ResearchFeatureConfigSnapshot;

import net.minecraft.resources.ResourceLocation;

/** Resolves the effective crafting capability of one TaCZ workstation ID. */
public final class CraftingWorkbenchTierResolver {
    public static final ResourceLocation TIER_1_ID =
            new ResourceLocation("taczweaponblueprints:workbench_lvl1");
    public static final ResourceLocation TIER_2_ID =
            new ResourceLocation("taczweaponblueprints:workbench_lvl2");
    public static final ResourceLocation TIER_3_ID =
            new ResourceLocation("taczweaponblueprints:workbench_lvl3");

    private static final Map<ResourceLocation, ResearchWorkbenchTier> NATIVE_TIERS = Map.of(
            TIER_1_ID, ResearchWorkbenchTier.TIER_1,
            TIER_2_ID, ResearchWorkbenchTier.TIER_2,
            TIER_3_ID, ResearchWorkbenchTier.TIER_3);

    private CraftingWorkbenchTierResolver() {
    }

    public static Resolution resolve(
            ResourceLocation workstationId,
            ResearchFeatureConfigSnapshot config) {
        if (workstationId == null || config == null) {
            throw new IllegalArgumentException("crafting workstation resolution inputs cannot be null");
        }
        ResearchWorkbenchTier nativeTier = NATIVE_TIERS.get(workstationId);
        if (nativeTier != null) {
            return new Resolution(nativeTier, false, Source.NATIVE_CRAFTING_WORKBENCH);
        }
        ResearchWorkbenchTier exact = config.externalWorkstationTiers().get(workstationId);
        if (exact != null) {
            return new Resolution(exact, false, Source.EXACT_EXTERNAL_MAPPING);
        }
        if (config.unknownExternalWorkstationsUnrestricted()) {
            // Tier 3 also satisfies explicit workbench-tier gate conditions while
            // the unrestricted flag bypasses the ordinary crafting-tier band.
            return new Resolution(
                    ResearchWorkbenchTier.TIER_3, true, Source.UNRESTRICTED_EXTERNAL);
        }
        return new Resolution(
                config.unknownWorkstationFallbackTier(),
                false,
                Source.EXTERNAL_FALLBACK);
    }

    public static boolean isNativeCraftingWorkbench(ResourceLocation workstationId) {
        return workstationId != null && NATIVE_TIERS.containsKey(workstationId);
    }

    public record Resolution(
            ResearchWorkbenchTier tier,
            boolean unrestricted,
            Source source) {
        public Resolution {
            if (tier == null || source == null
                    || unrestricted != (source == Source.UNRESTRICTED_EXTERNAL)) {
                throw new IllegalArgumentException("invalid crafting workstation resolution");
            }
        }
    }

    public enum Source {
        NATIVE_CRAFTING_WORKBENCH,
        EXACT_EXTERNAL_MAPPING,
        EXTERNAL_FALLBACK,
        UNRESTRICTED_EXTERNAL
    }
}
