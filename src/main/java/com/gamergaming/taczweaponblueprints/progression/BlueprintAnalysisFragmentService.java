package com.gamergaming.taczweaponblueprints.progression;

import java.util.Optional;
import java.util.List;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy;
import net.minecraft.world.item.ItemStack;
import com.gamergaming.taczweaponblueprints.init.ModAttachments;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintFragmentItem;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisFragmentReconstructionService;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentAnalysisService;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import net.minecraft.server.level.ServerPlayer;

/** Acquires current reconstruction policy without treating fragments as a fresh weapon attempt. */
public final class BlueprintAnalysisFragmentService {
    private BlueprintAnalysisFragmentService() { }
    public static AnalysisFragmentReconstructionService.Policy policy(ServerPlayer player,
            BlueprintFragmentAnalysisService.WorkstationTransaction station, AnalyzerTier tier) {
        var identity = BlueprintFragmentItem.identity(station.physicalInput()).filter(id -> id.analysisClass().isPresent()).orElseThrow();
        var context = BlueprintWeaponAnalysisService.context();
        var config = ModConfigs.BLUEPRINT.progressionSnapshot();
        var shared = AnalysisReconstructionResolver.resolve(context, config.activeProfileId(), identity.target(), tier).policy();
        boolean enabled = config.blueprintsEnabled() && shared.enabled()
                && !ModConfigs.BLUEPRINT.isItemBlacklisted(identity.target().toString())
                && !BlueprintProgressionAccess.isProgressionExempt(identity.target());
        return new AnalysisFragmentReconstructionService.Policy(identity.target(), enabled, shared.threshold(),
                shared.retainedCap(), shared.recyclable(), shared.learning(), shared.revision());
    }

    public static AnalysisFragmentReconstructionService.Evaluation evaluate(ServerPlayer player,
            BlueprintFragmentAnalysisService.WorkstationTransaction station, AnalyzerTier tier) {
        var data = ModAttachments.recipeData(player).orElseThrow();
        return AnalysisFragmentReconstructionService.evaluate(station.physicalInput(), station.outputStack(), data, policy(player, station, tier));
    }

    /** The shared fragment wire fields describe reconstruction accurately without an RP conversion. */
    public static BlueprintFragmentAnalysisService.Evaluation preview(ServerPlayer player,
            BlueprintFragmentAnalysisService.WorkstationTransaction station, AnalyzerTier tier) {
        var playerData = ModAttachments.recipeData(player);
        if (playerData.isEmpty()) {
            var identity = BlueprintFragmentItem.identity(station.physicalInput()).orElseThrow();
            int count = station.physicalInput().getCount();
            return new BlueprintFragmentAnalysisService.Evaluation(BlueprintFragmentAnalysisService.Status.PLAYER_DATA_UNAVAILABLE,
                    Optional.of(identity.target()), BlueprintFragmentPolicy.CompletionMode.DISABLED,
                    count, 0, count, 0, 0, 0, 0, 0, 0, false, false, 0, 0,
                    ModConfigs.BLUEPRINT.progressionSnapshot().pointCap(), false, 0)
                    .withCurrencyBalance(ModConfigs.BLUEPRINT.currencyPolicy(), player.getInventory().items, null);
        }
        return preview(evaluate(player, station, tier), playerData.orElseThrow(),
                ModConfigs.BLUEPRINT.currencyPolicy(), player.getInventory().items,
                ModConfigs.BLUEPRINT.progressionSnapshot().pointCap());
    }

    static BlueprintFragmentAnalysisService.Evaluation preview(AnalysisFragmentReconstructionService.Evaluation e,
            IPlayerRecipeData data, ResearchCurrencyPolicy currency, List<ItemStack> inventory, int pointCap) {
        var reward = e.identity().analysisClass().orElseThrow();
        int before = e.before().get(reward), deposited = before + e.accepted();
        int threshold = e.policy().threshold();
        return new BlueprintFragmentAnalysisService.Evaluation(status(e.status()), Optional.of(e.policy().target()),
                BlueprintFragmentPolicy.CompletionMode.RECONSTRUCT_BLUEPRINT, e.offered(), e.accepted(), e.offered() - e.accepted(),
                before, deposited, e.after().get(reward), threshold, before / threshold, deposited / threshold,
                e.createsBlueprint(), data.hasBlueprint(e.policy().target().toString()), 0, 0,
                pointCap, e.createsBlueprint(), e.policy().revision())
                .withCurrencyBalance(currency, inventory, data);
    }

    public static BlueprintFragmentAnalysisService.Status analyze(ServerPlayer player,
            BlueprintFragmentAnalysisService.WorkstationTransaction station, AnalyzerTier tier) {
        var resolved = ModAttachments.recipeData(player);
        if (resolved.isEmpty()) return BlueprintFragmentAnalysisService.Status.PLAYER_DATA_UNAVAILABLE;
        var data = resolved.orElseThrow();
        var e = evaluate(player, station, tier);
        var context = BlueprintWeaponAnalysisService.context();
        var config = ModConfigs.BLUEPRINT.progressionSnapshot();
        var access = ModConfigs.BLUEPRINT.accessSnapshot();
        var menu = player.containerMenu;
        java.util.function.BooleanSupplier current = () -> player.isAlive() && player.containerMenu == menu
                && menu instanceof com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu analyzer
                && analyzer.isRecyclerMenuValid(player) && analyzer.analyzerTier().filter(tier::equals).isPresent()
                && BlueprintWeaponAnalysisService.context() == context && config.equals(ModConfigs.BLUEPRINT.progressionSnapshot())
                && access.equals(ModConfigs.BLUEPRINT.accessSnapshot())
                && !ModConfigs.BLUEPRINT.isItemBlacklisted(e.policy().target().toString());
        var result = e.ready() ? AnalysisFragmentReconstructionService.commit(e, policy(player, station, tier), data, station, current)
                : new AnalysisFragmentReconstructionService.Result(e.status(), 0, false);
        if (result.successful()) {
            try { NetworkHandler.syncPlayerRecipeData(player); }
            catch (RuntimeException error) { BlueprintProgressionSyncScheduler.markKnowledgeDirty(player); }
        }
        return status(result.status());
    }

    private static BlueprintFragmentAnalysisService.Status status(AnalysisFragmentReconstructionService.Status status) {
        return switch (status) {
            case READY -> BlueprintFragmentAnalysisService.Status.READY;
            case SUCCESS -> BlueprintFragmentAnalysisService.Status.SUCCESS;
            case INVALID_INPUT -> BlueprintFragmentAnalysisService.Status.INVALID_INPUT;
            case DISABLED -> BlueprintFragmentAnalysisService.Status.FRAGMENTS_DISABLED;
            case ARCHIVE_FULL -> BlueprintFragmentAnalysisService.Status.ARCHIVE_FULL;
            case OUTPUT_OCCUPIED -> BlueprintFragmentAnalysisService.Status.OUTPUT_OCCUPIED;
            case CAPACITY_REACHED -> BlueprintFragmentAnalysisService.Status.PROGRESSION_CAPACITY_EXHAUSTED;
            case UNSUPPORTED -> BlueprintFragmentAnalysisService.Status.PLAYER_DATA_UNAVAILABLE;
            case STALE -> BlueprintFragmentAnalysisService.Status.STALE_POLICY;
            case TRANSACTION_FAILED -> BlueprintFragmentAnalysisService.Status.TRANSACTION_FAILED;
            case ROLLBACK_FAILED -> BlueprintFragmentAnalysisService.Status.ROLLBACK_FAILED;
        };
    }
}
