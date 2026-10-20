package com.gamergaming.taczweaponblueprints.acceptance;

import java.nio.file.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.block.ResearchBenchBlock;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.progression.StationSetup;
import com.gamergaming.taczweaponblueprints.progression.workbench.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Opt-in authority checks in a disposable dedicated-server world; excluded from releases. */
@EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID)
public final class StationAccessServerProbe {
    private static Pending pending;
    private record Pending(String output, List<String> results, StationSetup.ResearchAccess previous,
            StationSetup.AnalyzerMode previousAnalyzer, int deadline, int stage) { }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (pending == null) return;
        var server = event.getServer(); var p = pending; var config = ModConfigs.BLUEPRINT;
        boolean basic = server.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("taczweaponblueprints", "research_bench")).isPresent();
        boolean advanced = server.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("taczweaponblueprints", "advanced_research_bench")).isPresent();
        boolean analyzer = server.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("taczweaponblueprints", "advanced_blueprint_analyzer")).isPresent();
        boolean ready = p.stage() == 0 ? basic && !advanced && !analyzer : p.stage() == 1 ? !basic && !advanced : basic && advanced && analyzer;
        if (!ready && server.getTickCount() < p.deadline()) return;
        if (!ready) { p.results().add("FAIL: live recipe reload timeout at stage " + p.stage()); finish(server, p); return; }
        p.results().add("live recipe mode " + p.stage() + " refreshed");
        if (p.stage() == 2) { p.results().add("PASS"); finish(server, p); return; }
        config.researchAccess.validateAndSet(p.stage() == 0 ? StationSetup.ResearchAccess.PORTABLE : StationSetup.ResearchAccess.TIERED);
        if (p.stage() == 1) config.analyzerSetup.validateAndSet(StationSetup.AnalyzerMode.TIERED);
        pending = new Pending(p.output(), p.results(), p.previous(), p.previousAnalyzer(), server.getTickCount()+600, p.stage()+1);
    }
    private static void finish(net.minecraft.server.MinecraftServer server, Pending p) {
        pending = null;
        ModConfigs.BLUEPRINT.researchAccess.validateAndSet(p.previous());
        ModConfigs.BLUEPRINT.analyzerSetup.validateAndSet(p.previousAnalyzer());
        ModConfigs.BLUEPRINT.onSyncServer();
        try { Files.write(Path.of(p.output()), p.results()); } catch (Exception error) { throw new RuntimeException(error); }
        server.halt(false);
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST) public static void started(ServerStartedEvent event) {
        String output = System.getProperty("tacz.stationAccessQA", "");
        if (output.isEmpty()) return;
        event.getServer().execute(() -> {
            var results = new ArrayList<String>();
            var config = ModConfigs.BLUEPRINT;
            var previous = config.researchAccess.get();
            try {
                var player = FakePlayerFactory.getMinecraft(event.getServer().overworld());
                player.setHealth(20); player.moveTo(0.5, 100, 0.5);
                config.researchAccess.validateAndSet(StationSetup.ResearchAccess.PORTABLE); config.onSyncServer();
                var menu = ResearchBenchMenu.portable(7, player.getInventory()); player.containerMenu = menu;
                var context = new ResearchWorkbenchContext(player.blockPosition(), player.level().dimension().location(),
                        ResourceLocation.fromNamespaceAndPath("taczweaponblueprints", "portable_research"), ResearchWorkbenchTier.TIER_3,
                        ResearchInteractionMode.RESEARCH, 8);
                require(ResearchWorkbenchAuthority.validForResearch(player, context), "portable session accepted", results);
                player.moveTo(500.5, 100, 500.5);
                require(ResearchWorkbenchAuthority.validForResearch(player, context), "portable session survives movement", results);
                require(!ResearchWorkbenchAuthority.validForResearch(player, context.transitionTo(ResearchInteractionMode.RESEARCH, 9)), "forged session rejected", results);
                config.researchAccess.validateAndSet(StationSetup.ResearchAccess.SINGLE); config.onSyncServer();
                require(!ResearchWorkbenchAuthority.validForResearch(player, context), "disabled portable session rejected", results);
                player.containerMenu = player.inventoryMenu;
                player.moveTo(0.5, 100, 0.5);
                var pos = new BlockPos(1,100,0);
                require(ResearchBenchBlock.placeCompleteStructure(player.level(), pos, ModBlocks.RESEARCH_BENCH.get().defaultBlockState()), "basic bench placed", results);
                var bench = ResearchBenchMenu.server(8, player.getInventory(), player.level(), pos); player.containerMenu = bench;
                var benchContext = bench.authoritativeTransitionContext(player).orElseThrow();
                require(benchContext.tier() == ResearchWorkbenchTier.TIER_3 && ResearchWorkbenchAuthority.validForResearch(player, benchContext), "single basic bench authorizes all tiers", results);
                player.moveTo(100.5,100,100.5);
                require(!ResearchWorkbenchAuthority.validForResearch(player, benchContext), "physical bench still requires proximity", results);
                player.containerMenu = player.inventoryMenu;

            } catch (Throwable error) {
                results.add("FAIL: " + error); TaCZWeaponBlueprints.LOGGER.error("Station access probe failed", error);
            } finally {
                var next = new Pending(output, results, previous, config.analyzerSetup.get(), event.getServer().getTickCount()+600, 0);
                if (results.stream().anyMatch(line -> line.startsWith("FAIL"))) finish(event.getServer(), next);
                else {
                    pending = next;
                    config.researchAccess.validateAndSet(StationSetup.ResearchAccess.SINGLE);
                    config.analyzerSetup.validateAndSet(StationSetup.AnalyzerMode.SINGLE);
                }
            }
        });
    }
    private static void require(boolean value, String name, List<String> results) {
        if (!value) throw new AssertionError(name); results.add(name);
    }
}
