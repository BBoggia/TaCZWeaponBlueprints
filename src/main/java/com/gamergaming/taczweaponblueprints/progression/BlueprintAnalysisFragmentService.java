package com.gamergaming.taczweaponblueprints.progression;

import java.util.Optional;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
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
        // Origin and found-recovery actions are not part of fragment identity. Recorded
        // recycling permission remains intact; current learning rules can restrict it.
        var quote = BlueprintAnalyzerPolicyResolver.resolve(context, config.activeProfileId(), identity.target(), tier,
                BlueprintAnalyzerPolicy.Origin.UNKNOWN, FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY);
        boolean enabled = config.blueprintsEnabled() && context.catalog().containsKey(identity.target())
                && quote.fragmentThreshold() > 0 && quote.status() != AnalyzerPolicyQuote.Status.STALE_PROGRESSION
                && !ModConfigs.BLUEPRINT.isItemBlacklisted(identity.target().toString())
                && !BlueprintProgressionAccess.isProgressionExempt(identity.target());
        return new AnalysisFragmentReconstructionService.Policy(identity.target(), enabled,
                Math.max(1, quote.fragmentThreshold()), Math.max(1, quote.retainedProgressCap()), true,
                quote.attempt().map(BlueprintReverseEngineeringPolicy::physicalBlueprintLearningMode)
                        .orElse(PhysicalBlueprintLearningMode.DISABLED),
                Math.max(1L, context.researchRevision()));
    }

    public static AnalysisFragmentReconstructionService.Evaluation evaluate(ServerPlayer player,
            BlueprintFragmentAnalysisService.WorkstationTransaction station, AnalyzerTier tier) {
        var data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve().orElseThrow();
        return AnalysisFragmentReconstructionService.evaluate(station.physicalInput(), station.outputStack(), data, policy(player, station, tier));
    }

    /** The shared fragment wire fields describe reconstruction accurately without an RP conversion. */
    public static BlueprintFragmentAnalysisService.Evaluation preview(ServerPlayer player,
            BlueprintFragmentAnalysisService.WorkstationTransaction station, AnalyzerTier tier) {
        var playerData = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve();
        if (playerData.isEmpty()) {
            var identity = BlueprintFragmentItem.identity(station.physicalInput()).orElseThrow();
            int count = station.physicalInput().getCount();
            return new BlueprintFragmentAnalysisService.Evaluation(BlueprintFragmentAnalysisService.Status.PLAYER_DATA_UNAVAILABLE,
                    Optional.of(identity.target()), BlueprintFragmentPolicy.CompletionMode.DISABLED,
                    count, 0, count, 0, 0, 0, 0, 0, 0, false, false, 0, 0,
                    ModConfigs.BLUEPRINT.progressionSnapshot().pointCap(), false, 0);
        }
        var e = evaluate(player, station, tier);
        var reward = e.identity().analysisClass().orElseThrow();
        int before = e.before().get(reward), deposited = before + e.accepted();
        int threshold = e.policy().threshold();
        var data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve().orElseThrow();
        return new BlueprintFragmentAnalysisService.Evaluation(status(e.status()), Optional.of(e.policy().target()),
                BlueprintFragmentPolicy.CompletionMode.RECONSTRUCT_BLUEPRINT, e.offered(), e.accepted(), e.offered() - e.accepted(),
                before, deposited, e.after().get(reward), threshold, before / threshold, deposited / threshold,
                e.createsBlueprint(), data.hasBlueprint(e.policy().target().toString()), 0, data.getResearchPoints(),
                ModConfigs.BLUEPRINT.progressionSnapshot().pointCap(), e.createsBlueprint(), e.policy().revision());
    }

    public static BlueprintFragmentAnalysisService.Status analyze(ServerPlayer player,
            BlueprintFragmentAnalysisService.WorkstationTransaction station, AnalyzerTier tier) {
        var resolved = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve();
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
