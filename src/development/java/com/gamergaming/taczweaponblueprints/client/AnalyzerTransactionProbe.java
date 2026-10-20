package com.gamergaming.taczweaponblueprints.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import com.gamergaming.taczweaponblueprints.block.BlueprintRecyclerBlock;
import com.gamergaming.taczweaponblueprints.capabilities.*;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;

/** Native A4 acceptance against a disposable world's tiered profile fixture. */
final class AnalyzerTransactionProbe {
    private static final ResourceLocation TARGET = ResourceLocation.parse("tacz:ak47");
    private static int checks;
    private AnalyzerTransactionProbe() { }
    static void verify(ServerPlayer player, BlockPos pos, Path output) throws Exception {
        var blocks = List.of((BlueprintRecyclerBlock)ModBlocks.BLUEPRINT_RECYCLER.get(),
                ModBlocks.ADVANCED_BLUEPRINT_ANALYZER.get(), ModBlocks.EXPERIMENTAL_BLUEPRINT_ANALYZER.get());
        var data = com.gamergaming.taczweaponblueprints.init.ModAttachments.recipeData(player).orElseThrow();
        for (int tier = 1; tier <= 3; tier++) {
            player.closeContainer(); player.getInventory().clearContent();
            data.replaceProgression(Set.of(), Set.of(TARGET.toString()), 20);
            data.clearArchivedBlueprintFragments(); data.clearArchivedAnalysisFragments();
            player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 4));
            var block = blocks.get(tier-1);
            player.serverLevel().removeBlock(pos, false);
        if (!com.gamergaming.taczweaponblueprints.block.BlueprintRecyclerBlock.placeCompleteStructure(
                player.serverLevel(), pos, block.defaultBlockState())) throw new IllegalStateException("Analyzer fixture needs two blocks");
            player.serverLevel().getBlockState(pos).useWithoutItem(player.serverLevel(), player,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
            var menu = (BlueprintRecyclerMenu)player.containerMenu;
            menu.getSlot(0).set(gun()); menu.refreshAuthoritativePreview(player);
            check(menu.preview().pointCost() == 3 && menu.preview().actionable(), "tiered cost and action " + tier);
            long before = menu.preview().stateToken();
            player.getInventory().setItem(4, new ItemStack(Items.DIRT)); // No broadcast tick.
            var stale = menu.handleRecyclerRequest(player, 1, BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER, TARGET, 1, before);
            check(stale.code() == BlueprintRecyclerActionContract.ResultCode.STALE_INPUT, "between-tick inventory change rejected");
            check(stale.stateToken() > 0 && stale.stateToken() == menu.preview().stateToken(), "stale receipt identifies refreshed preview");
            check(menu.inputStack().getCount() == 1 && data.getResearchPoints() == 20, "stale request consumed nothing");
            var result = menu.handleRecyclerRequest(player, 2, BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER,
                    TARGET, 1, menu.preview().stateToken());
            check(result.successful(), "native tiered result " + tier + ": " + result.code());
            check(result.stateToken() > before && result.stateToken() == menu.preview().stateToken(), "committed receipt identifies post-action preview");
            check(menu.inputStack().isEmpty() && data.getResearchPoints() == 17, "weapon and RP consumed once");
            check(player.getInventory().getItem(0).getCount() == 2, "materials consumed once");
            check(data.getArchivedAnalysisFragments().isEmpty() && !data.hasBlueprint(TARGET.toString()), "no auto archive or learning");
            if (tier == 1) {
                check(menu.outputStack().getCount() == 3, "partial stack quantity");
                check(BlueprintFragmentItem.identity(menu.outputStack()).orElseThrow().analysisClass().orElseThrow()
                        == AnalysisRewardClass.PROTECTED_PREREQUISITES, "partial protection");
            } else if (tier == 2) {
                check(BlueprintItem.getBlueprintId(menu.outputStack()).filter(TARGET::equals).isPresent(), "full blueprint");
                var provenance = BlueprintItem.getProvenance(menu.outputStack()).orElseThrow();
                check(!provenance.recyclable() && provenance.learningMode() == PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES,
                        "full protection matches fragments");
            } else check(menu.outputStack().isEmpty(), "explicit no-result outcome");
            var partial = menu.outputStack().copy();
            menu.getSlot(1).set(ItemStack.EMPTY); menu.getSlot(0).set(gun());
            data.setResearchPoints(20); menu.refreshAuthoritativePreview(player);
            result = menu.handleRecyclerRequest(player, 2, BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER,
                    TARGET, 1, menu.preview().stateToken());
            check(result.code() == BlueprintRecyclerActionContract.ResultCode.STALE_INPUT, "request ID cannot be replayed against fresh state");
            check(result.stateToken() == menu.preview().stateToken(), "replayed request receipt remains bound to current preview");
            check(menu.inputStack().getCount() == 1 && data.getResearchPoints() == 20, "replay consumed nothing");
            if (tier == 1) {
                data.addBlueprint(TARGET.toString()); // Known-target fragments still reconstruct, never redeem for RP.
                data.applyAnalysisFragmentMutation(new AnalysisFragmentMutation(PlayerProgressValueMutation.Operation.COMMIT,
                        TARGET.toString(), AnalysisFragmentCounts.EMPTY, new AnalysisFragmentCounts(2,0,0,0)));
                menu.getSlot(0).set(partial.copy()); menu.refreshAuthoritativePreview(player);
                check(menu.preview().fragmentEvaluation().orElseThrow().createsBlueprint(), "analysis ignores ordinary discount preset");
                result = menu.handleRecyclerRequest(player, 3, BlueprintRecyclerActionContract.Action.ARCHIVE_FRAGMENTS,
                        TARGET, 3, menu.preview().stateToken());
                check(result.successful() && menu.inputStack().isEmpty(), "partial stack reconstruction");
                check(BlueprintItem.getProvenance(menu.outputStack()).orElseThrow().source() == BlueprintProvenance.Source.FRAGMENT_RECONSTRUCTION,
                        "reconstructed provenance");
                check(data.getResearchPoints() == 20 && data.getArchivedBlueprintFragments().isEmpty(), "no fragment RP or discount");
                menu.getSlot(1).set(ItemStack.EMPTY); menu.getSlot(0).set(partial.copy());
                data.applyAnalysisFragmentMutation(new AnalysisFragmentMutation(PlayerProgressValueMutation.Operation.COMMIT,
                        TARGET.toString(), AnalysisFragmentCounts.EMPTY, new AnalysisFragmentCounts(10,0,0,0)));
                menu.refreshAuthoritativePreview(player);
                check(menu.preview().fragmentEvaluation().orElseThrow().accepted() == 0, "archive above cap accepts no input");
                result = menu.handleRecyclerRequest(player, 4, BlueprintRecyclerActionContract.Action.ARCHIVE_FRAGMENTS,
                        TARGET, 3, menu.preview().stateToken());
                check(result.successful() && menu.inputStack().getCount() == 3, "stored set reconstructed without consuming identifying input");
                check(data.getArchivedAnalysisFragments().get(TARGET.toString()).total() == 5, "only one stored set consumed");
                menu.getSlot(1).set(ItemStack.EMPTY);
                var overstacked = partial.copy(); overstacked.setCount(65);
                menu.getSlot(0).set(overstacked); menu.inputStack().setCount(65); menu.refreshAuthoritativePreview(player);
                check(!menu.preview().actionable(), "overstacked analysis input rejected safely");
                var malformed = partial.copy(); net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, malformed, tag -> tag.putString(BlueprintFragmentItem.ANALYSIS_TAG, "bad"));
                menu.getSlot(0).set(malformed); menu.refreshAuthoritativePreview(player);
                check(!menu.preview().actionable(), "malformed analysis cannot fall into another reward route");
            }
        }
        player.closeContainer();
        Files.writeString(output.resolve("transactions.json"), "{\"assertions\":" + checks + ",\"passed\":true}\n");
    }
    private static ItemStack gun() {
        var item = BuiltInRegistries.ITEM.stream().filter(value -> value instanceof IGun).findFirst().orElseThrow();
        var stack = new ItemStack(item); ((IGun)item).setGunId(stack,TARGET);
        PhysicalWeaponProvenance.stampLootGenerated(stack,ResourceLocation.parse("minecraft:chests/simple_dungeon"));
        return stack;
    }
    private static void check(boolean value,String message) { if (!value) throw new IllegalStateException(message); checks++; }
}
