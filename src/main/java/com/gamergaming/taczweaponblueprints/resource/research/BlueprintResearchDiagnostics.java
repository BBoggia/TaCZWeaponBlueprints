package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Map;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;

import net.minecraft.resources.ResourceLocation;

/** Deterministic, UI-neutral diagnostics for research policy inspection. */
public final class BlueprintResearchDiagnostics {
    private BlueprintResearchDiagnostics() {
    }

    public static Summary summarize(BlueprintResearchSnapshot snapshot) {
        BlueprintResearchSnapshot stable = snapshot == null ? BlueprintResearchSnapshot.EMPTY : snapshot;
        int exact = 0;
        int tags = 0;
        int selectors = 0;
        for (BlueprintResearchRule rule : stable.rules().values()) {
            exact += rule.target().blueprints().size();
            tags += rule.target().tags().size();
            selectors += rule.target().selector().isPresent() ? 1 : 0;
        }
        return new Summary(
                stable.tags().size(),
                stable.profiles().size(),
                stable.rules().size(),
                exact,
                tags,
                selectors);
    }

    public static BlueprintResearchPolicy inspect(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData) {
        return BlueprintResearchPolicyResolver.resolve(
                snapshot,
                catalog,
                profileId,
                blueprintId,
                playerData,
                ignored -> false);
    }

    public static BlueprintResearchPolicyResolver.RuleSelection inspectSelection(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            ResourceLocation blueprintId) {
        Map<ResourceLocation, BlueprintData> stableCatalog = catalog == null ? Map.of() : catalog;
        return BlueprintResearchPolicyResolver.ruleSelection(
                snapshot,
                profileId,
                blueprintId,
                stableCatalog.get(blueprintId));
    }

    public record Summary(
            int tagCount,
            int profileCount,
            int ruleCount,
            int exactTargetCount,
            int tagTargetCount,
            int selectorTargetCount) {
    }
}
