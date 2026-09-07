package com.gamergaming.taczweaponblueprints.acceptance;

import java.util.*;
import java.util.function.BiConsumer;
import com.gamergaming.taczweaponblueprints.block.ResearchBenchBlock;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Actual dedicated menus and inventory transactions; separate from wire-click acceptance. */
final class ResearchEconomyWorkbenchChecks {
    private static final ResourceLocation TARGET = new ResourceLocation("tacz:ak47");
    private static IPlayerRecipeData data(ServerPlayer p) { return p.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve().orElseThrow(); }
    private static ResearchBenchMenu am, bm;
    private static long readyAt;
    private static boolean initialized;
    private static me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition<ResearchCostMode> cost;
    private static me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition<Boolean> awards;
    static boolean run(ServerPlayer a, ServerPlayer b, BiConsumer<Boolean, String> verify) {
        var config = ModConfigs.BLUEPRINT;

        try {
            if (!initialized) {
                cost = config.researchCostMode; awards = config.enableResearchPointAwards;
                config.researchCostMode = new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition<>(
                        new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum<>(ResearchCostMode.POINTS_ONLY), () -> ResearchCostMode.POINTS_ONLY);
                config.enableResearchPointAwards = new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition<>(
                        new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean(false), () -> false);
                config.initializeLoadedSnapshots();
                var catalog = BlueprintWeaponAnalysisService.context().catalog().keySet().stream().map(Object::toString).toList();
                for (var p : List.of(a, b)) {
                    p.closeContainer(); p.getInventory().clearContent(); data(p).clearRecipes();
                    data(p).replaceProgression(Set.of(), catalog, 137);
                    data(p).clearArchivedAnalysisFragments(); data(p).clearArchivedBlueprintFragments();
                    p.teleportTo(a.serverLevel(), 30.5, 100, .5, 0, 0);
                }
                var pos = new BlockPos(32, 100, 0);
                for (int x=28; x<36; x++) for (int z=-3; z<4; z++) a.serverLevel().setBlockAndUpdate(new BlockPos(x,99,z), Blocks.STONE.defaultBlockState());
                var block = ModBlocks.EXPERIMENTAL_RESEARCH_BENCH.get();
                verify.accept(ResearchBenchBlock.placeCompleteStructure(a.serverLevel(), pos, block.defaultBlockState()), "place shared T3 Research Bench");
                for (int i=0; i<30; i++) a.getInventory().setItem(i, new ItemStack(ModItems.SCRAP.get(),64));
                am = open(a, pos); bm = open(b, pos);
                verify.accept(am != bm, "same physical Bench creates independent player menus");
                am.handleAction(a, ResearchBenchResearchAction.SELECT, Optional.of(TARGET));
                bm.handleAction(b, ResearchBenchResearchAction.SELECT, Optional.of(TARGET));
                // Opening and selecting already use this tick's planning allowance.
                initialized = true; readyAt = a.serverLevel().getGameTime() + 20;
                return false;
            }
            if (a.serverLevel().getGameTime() < readyAt) return false;
            verify.accept(am.preview().researchable() && !bm.preview().researchable(), "same target uses each player's own Scrap balance");
            var beforeBob = data(b).serializeNBT().copy();
            verify.accept(am.handleAction(b, ResearchBenchResearchAction.RESEARCH, Optional.of(TARGET)).isEmpty(), "Bench rejects other player using retained menu");
            int before = a.getInventory().countItem(ModItems.SCRAP.get()), price = am.preview().pointCost(), unlocks = am.preview().unlockCount();
            verify.accept(unlocks > 1, "whole-path fixture contains multiple unlocks: " + unlocks);
            var result = am.handleAction(a, ResearchBenchResearchAction.RESEARCH, Optional.of(TARGET));
            verify.accept(result.orElseThrow().successful(), "whole-path Research Bench purchase succeeds: " + result);
            verify.accept(a.getInventory().countItem(ModItems.SCRAP.get()) == before-price && data(a).getResearchPoints()==137, "path payment spends exact Scrap and preserves dormant RP");
            verify.accept(data(a).getLearnedBlueprints().contains(TARGET.toString()) && data(b).serializeNBT().equals(beforeBob), "path learning is isolated to purchasing player");
            int paid = a.getInventory().countItem(ModItems.SCRAP.get());
            verify.accept(!am.handleAction(a, ResearchBenchResearchAction.RESEARCH, Optional.of(TARGET)).orElseThrow().successful()
                    && a.getInventory().countItem(ModItems.SCRAP.get())==paid, "repeat path request cannot charge twice");
            verify.accept(!bm.handleAction(b, ResearchBenchResearchAction.RESEARCH, Optional.of(TARGET)).orElseThrow().successful()
                    && data(b).serializeNBT().equals(beforeBob), "unfunded player cannot spend another player's Scrap");
            a.closeContainer();
            verify.accept(am.handleAction(a, ResearchBenchResearchAction.RESEARCH, Optional.of(TARGET)).isEmpty(), "closed Research Bench rejects retained request");
            b.teleportTo(b.serverLevel(), 50.5,100,.5,0,0);
            verify.accept(!bm.stillValid(b) && bm.handleAction(b, ResearchBenchResearchAction.RESEARCH, Optional.of(TARGET)).isEmpty(), "out-of-range Research Bench cannot transact");
            restore(a,b);
            return true;
        } catch (RuntimeException e) {
            restore(a,b); throw e;
        }
    }
    private static void restore(ServerPlayer a, ServerPlayer b) {
        a.closeContainer(); b.closeContainer();
        var config=ModConfigs.BLUEPRINT;
        config.researchCostMode=cost; config.enableResearchPointAwards=awards; config.initializeLoadedSnapshots();
    }
    private static ResearchBenchMenu open(ServerPlayer p, BlockPos pos) {
        var state=p.serverLevel().getBlockState(pos);
        state.getBlock().use(state,p.serverLevel(),pos,p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
        return (ResearchBenchMenu)p.containerMenu;
    }
}
