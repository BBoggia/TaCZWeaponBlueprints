package com.gamergaming.taczweaponblueprints.compat.magazines;

import java.util.Objects;
import java.util.function.Predicate;

import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.progression.CraftingEligibilityService.Status;
import com.gamergaming.taczweaponblueprints.progression.ResearchFeatureConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.workbench.CraftingWorkbenchTierResolver;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import net.minecraft.resources.ResourceLocation;

/** Optional magazine crafting rules; never grants or persists magazine blueprint knowledge. */
public record MagazineProgressionSettings(Mode mode, boolean keepBasicCrafting,
        ResearchWorkbenchTier basicTier, ResearchWorkbenchTier extendedTier) {
    public static final MagazineProgressionSettings DEFAULT = new MagazineProgressionSettings(
            Mode.COMPATIBILITY, true, ResearchWorkbenchTier.TIER_1, ResearchWorkbenchTier.TIER_1);

    public MagazineProgressionSettings {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(basicTier);
        Objects.requireNonNull(extendedTier);
    }

    public static MagazineProgressionSettings from(BlueprintConfig config) {
        return new MagazineProgressionSettings(config.magazineProgressionMode.get(),
                config.magazineKeepBasicCrafting.get(), config.magazineBasicWorkbenchTier.get(),
                config.magazineExtendedWorkbenchTier.get());
    }

    public Status evaluate(MagazineDescriptor magazine, ResearchWorkbenchTier workstationTier,
            boolean bypassTier, Predicate<ResourceLocation> usableLearnedGun) {
        if (magazine == null || workstationTier == null) return Status.UNKNOWN_RECIPE;
        var required = magazine.extensionLevel() == 0 ? basicTier : extendedTier;
        if (!bypassTier && !workstationTier.satisfies(required)) return Status.WORKBENCH_TIER_REQUIRED;
        if (mode == Mode.COMPATIBILITY || keepBasicCrafting && magazine.extensionLevel() == 0)
            return Status.ALLOWED;
        // Family membership comes from upstream, not shared caliber or its display gun.
        return usableLearnedGun != null && magazine.compatibleGuns().stream().anyMatch(usableLearnedGun)
                ? Status.ALLOWED : Status.RECIPE_NOT_LEARNED;
    }

    /** The original ammo Workbench is Tier 1 unless explicitly mapped by the pack. */
    public static ResearchWorkbenchTier workstationTier(ResourceLocation id, ResearchFeatureConfigSnapshot config) {
        if (MagazineCraftingAccess.AMMO_WORKBENCH.equals(id))
            return config.externalWorkstationTiers().getOrDefault(id, ResearchWorkbenchTier.TIER_1);
        return CraftingWorkbenchTierResolver.resolve(id, config).tier();
    }

    public enum Mode implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
        COMPATIBILITY, LINKED_WEAPON;
        @Override public String prefix() { return "config.taczweaponblueprints.blueprint.magazineProgressionMode"; }
    }
}
