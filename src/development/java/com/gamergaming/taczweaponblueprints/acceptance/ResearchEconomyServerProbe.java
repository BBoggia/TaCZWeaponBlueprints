package com.gamergaming.taczweaponblueprints.acceptance;

import static com.gamergaming.taczweaponblueprints.acceptance.ResearchEconomyAcceptance.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.block.BlueprintRecyclerBlock;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.progression.currency.*;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Real dedicated-server acceptance, fenced to a marked disposable world. */
@EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID)
public final class ResearchEconomyServerProbe {
    private static MinecraftServer server;
    private static int age, warmup, step;
    private static boolean finished;
    private static final List<String> checks = new ArrayList<>();
    private static CompletableFuture<Void> reload;
    private static long oldToken;
    private static BlueprintRecyclerMenu oldMenu;
    private static CompoundTag beforeDeath;
    private static final ResourceLocation NOTE = ResourceLocation.parse("taczweaponblueprints:research_note");
    private static void verify(boolean value, String message) { check(value, message); checks.add(message); }
    private static IPlayerRecipeData data(ServerPlayer p) { return com.gamergaming.taczweaponblueprints.init.ModAttachments.recipeData(p).orElseThrow(); }
    @SubscribeEvent public static void start(ServerStartingEvent event) {
        if (!ENABLED) return;
        server = event.getServer();
        try {
            verify(server.isDedicatedServer(), "real dedicated server");
            verify(Set.of("gameplay", "restart", "upgrade").contains(PHASE), "recognized phase");
            verify(Files.isRegularFile(server.getWorldPath(LevelResource.ROOT).resolve(".research-economy-qa")), "marked disposable world");
            try (var files = Files.list(ROOT)) {
                verify(files.noneMatch(p -> p.getFileName().toString().startsWith(PHASE + "-")), "fresh evidence phase");
            }
            artifactOrigin(); checks.add("candidate artifact class origin verified");
            if (PHASE.equals("upgrade")) for (var role : List.of("Alice", "Bob")) {
                var uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + role).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var path = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat");
                var tag = NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                var cap = tag.getCompound("ForgeCaps").getCompound("taczweaponblueprints:player_recipe_data");
                verify(cap.getInt("DataVersion") == 5, "existing player saved before upgrade fixture");
                cap.putInt("DataVersion", 4); cap.remove("ArchivedAnalysisFragments");
                NbtIo.writeCompressed(tag, path);
            }
            server.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            write("command", "connect");
        } catch (Exception e) { fail(e); }
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (!ENABLED || finished || server == null) return;
        try {
            if (++age > 12000) throw new IllegalStateException("Economy dedicated timeout at step " + step);
            for (var role : List.of("Alice", "Bob")) check(read(role + "-error.txt").isEmpty(), read(role + "-error.txt"));
            if (step == 5 && server.getPlayerList().getPlayerCount() == 0) { write("command", "connect"); step = 6; warmup = 0; }
            var a = server.getPlayerList().getPlayerByName("Alice"); var b = server.getPlayerList().getPlayerByName("Bob");
            if (a == null || b == null) { warmup = 0; return; }
            if (++warmup < 60) return;
            if (!PHASE.equals("gameplay") || step == 6) { persistence(a); persistence(b); complete(); return; }
            if (step == 0) {
                if (Boolean.getBoolean("tacz.economyQA.workbenches") && !ResearchEconomyWorkbenchChecks.run(a, b, ResearchEconomyServerProbe::verify)) return;
                verify(!a.getUUID().equals(b.getUUID()), "two independent player identities");
                verify(ModConfigs.BLUEPRINT.currencyPolicy().kind() == ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM, "fresh physical default");
                verify(ModConfigs.BLUEPRINT.dataSettings().behavior() == ResearchDataPolicy.Behavior.ANALYSIS, "fresh Data analysis default");
                for (var p : List.of(a, b)) {
                    p.closeContainer(); p.getInventory().clearContent(); data(p).clearRecipes();
                    data(p).replaceProgression(Set.of(), Set.of(), 137);
                    data(p).clearArchivedAnalysisFragments(); data(p).clearArchivedBlueprintFragments();
                    var menu = open(p, p == a ? 0 : 8);
                    menu.getSlot(0).set(new ItemStack(ModItems.RESEARCH_NOTE.get(), 2)); menu.refreshAuthoritativePreview(p);
                    verify(menu.preview().actionable(), "fresh Note usable for " + p.getGameProfile().getName());
                    write(p.getGameProfile().getName() + "-preview", menu.preview().withStateToken(0).toString());
                }
                write("action", "go"); step = 1; return;
            }
            if (step == 1) {
                if (read("Alice-ack").isEmpty() || read("Bob-ack").isEmpty()) return;
                for (var p : List.of(a, b)) {
                    var m = (BlueprintRecyclerMenu)p.containerMenu;
                    verify(m.inputStack().getCount() == 1 && m.outputStack().getItem() instanceof BlueprintFragmentItem, "wire action consumes exactly one Note and returns fragments");
                    verify(data(p).getResearchPoints() == 137, "analysis preserves dormant RP");
                    var output = m.outputStack().copy(); long token = m.preview().stateToken();
                    verify(!m.handleRecyclerAction(p, BlueprintRecyclerActionContract.Action.ANALYZE_DATA, NOTE, 1, token).successful(), "occupied output rejects repeat");
                    verify(ItemStack.matches(output, m.outputStack()) && m.inputStack().getCount() == 1, "rejection conserves both slots");
                }
                exerciseRequestIsolation(a, b);
                exerciseInventory(a, b);
                exerciseLegacy(b);
                exerciseFilters(a, b);
                oldMenu = (BlueprintRecyclerMenu)b.containerMenu;
                oldMenu.refreshAuthoritativePreview(b); oldToken = oldMenu.preview().stateToken();
                reload = server.reloadResources(server.getPackRepository().getSelectedIds());
                step = 2; return;
            }
            if (step == 2) {
                if (!reload.isDone()) return;
                reload.join();
                verify(!oldMenu.handleRecyclerAction(b, BlueprintRecyclerActionContract.Action.ANALYZE_DATA, NOTE, 1, oldToken).successful(), "actual resource reload invalidates old request");
                verify(oldMenu.inputStack().getCount() == 1 && oldMenu.outputStack().isEmpty(), "reload rejection conserves input");
                oldMenu.refreshAuthoritativePreview(b);
                verify(oldMenu.preview().actionable(), "new preview works after reload");
                var input = oldMenu.inputStack().copy();
                int held = b.getInventory().countItem(input.getItem()); b.closeContainer();
                verify(!oldMenu.handleRecyclerAction(b, BlueprintRecyclerActionContract.Action.ANALYZE_DATA, NOTE, 1, oldMenu.preview().stateToken()).successful(), "closed menu rejects retained request");
                verify(oldMenu.inputStack().isEmpty() && b.getInventory().countItem(input.getItem()) == held + input.getCount(),
                        "menu closure returns exactly the unconsumed input");
                for (int i = 0; i < 36; i++) a.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
                var closing = (BlueprintRecyclerMenu)a.containerMenu;
                int droppedBefore = dropped(a, ModItems.RESEARCH_NOTE.get());
                a.closeContainer();
                verify(closing.inputStack().isEmpty() && dropped(a, ModItems.RESEARCH_NOTE.get()) == droppedBefore + 1,
                        "full-inventory menu closure drops exactly one unconsumed Note");
                for (var p : List.of(a, b)) p.closeContainer();
                a.getInventory().clearContent(); b.getInventory().clearContent();
                data(a).addBlueprint("tacz:ak47");
                data(a).replaceSupplementalProgression(Map.of("tacz:m4a1", 4), Map.of("qa:persisted", 2),
                        Map.of("tacz:ak47", new com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisFragmentCounts(2, 3, 4, 5)));
                BlueprintLearningService.migrateLegacyUnlocks(com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager.SERVER, data(a));
                // Seed through the real award path before recording a fixed
                // inventory. Otherwise login correctly grants a missed first
                // discovery reward and invalidates an artificial snapshot.
                ResearchPointAwardDispatcher.blueprintTransitions(a, data(a), ResourceLocation.parse("tacz:ak47"), true, true);
                a.getInventory().clearContent();
                a.getInventory().setItem(0, new ItemStack(ModItems.SCRAP.get(), 23));
                beforeDeath = data(a).serializeNBT().copy();
                server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, server);
                a.hurt(a.damageSources().genericKill(), Float.MAX_VALUE);
                step = 3; warmup = 0; return;
            }
            if (step == 3) {
                if (!a.isAlive()) return;
                int physical = a.getInventory().countItem(ModItems.SCRAP.get()) + b.getInventory().countItem(ModItems.SCRAP.get());
                for (var entity : a.serverLevel().getAllEntities()) if (entity instanceof ItemEntity item && item.getItem().is(ModItems.SCRAP.get())) physical += item.getItem().getCount();
                verify(physical == 23, "death and respawn conserve exactly 23 Scrap across players and world");
                verify(data(a).getResearchPoints() == 137, "death clone preserves dormant RP");
                var afterDeath = data(a).serializeNBT();
                for (var key : List.of("Recipes", "Blueprints", "DiscoveredBlueprints", "ArchivedBlueprintFragments", "ArchivedAnalysisFragments", "ProgressionCriteria", "ResearchPointAwards"))
                    verify(Objects.equals(beforeDeath.get(key), afterDeath.get(key)), "death clone preserves " + key);
                for (var p : List.of(a, b)) {
                    p.getInventory().clearContent(); p.getInventory().setItem(0, new ItemStack(ModItems.SCRAP.get(), p == a ? 17 : 29));
                    saveExpected(p);
                }
                server.getPlayerList().saveAll(); server.saveEverything(true, true, true);
                write("command", "disconnect"); step = 5;
            }
        } catch (Exception e) { fail(e); }
    }
    private static BlueprintRecyclerMenu open(ServerPlayer p, int x) {
        p.teleportTo(server.overworld(), x + .5, 100, .5, 0, 0);
        for (int dx = -2; dx < 4; dx++) for (int dz = -2; dz < 3; dz++)
            p.serverLevel().setBlockAndUpdate(new BlockPos(x + dx, 99, dz), Blocks.STONE.defaultBlockState());
        var pos = new BlockPos(x + 2, 100, 0); var block = (BlueprintRecyclerBlock)ModBlocks.BLUEPRINT_RECYCLER.get();
        p.serverLevel().removeBlock(pos, false); p.serverLevel().removeBlock(pos.above(), false);
        check(BlueprintRecyclerBlock.placeCompleteStructure(p.serverLevel(), pos, block.defaultBlockState()), "place Analyzer");
        p.serverLevel().getBlockState(pos).useWithoutItem(p.serverLevel(), p,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        return (BlueprintRecyclerMenu)p.containerMenu;
    }
    private static void exerciseRequestIsolation(ServerPlayer a, ServerPlayer b) {
        var menu = (BlueprintRecyclerMenu)a.containerMenu;
        var input = menu.inputStack().copy(); var output = menu.outputStack().copy();
        var alice = data(a).serializeNBT().copy(); var bob = data(b).serializeNBT().copy();
        long token = menu.preview().stateToken();
        verify(!menu.handleRecyclerRequest(b, 500, BlueprintRecyclerActionContract.Action.ANALYZE_DATA, NOTE, 1, token).successful(),
                "another player cannot operate a retained menu");
        verify(!menu.handleRecyclerRequest(a, 501, BlueprintRecyclerActionContract.Action.ANALYZE_DATA, NOTE, 2, token).successful(),
                "stale input count rejected");
        verify(!menu.handleRecyclerRequest(a, 502, BlueprintRecyclerActionContract.Action.ANALYZE_DATA, NOTE, 1, token + 1).successful(),
                "wrong state token rejected");
        verify(!menu.handleRecyclerRequest(a, 502, BlueprintRecyclerActionContract.Action.ANALYZE_DATA, NOTE, 1, token).successful(),
                "replayed request sequence rejected");
        verify(ItemStack.matches(input, menu.inputStack()) && ItemStack.matches(output, menu.outputStack())
                && alice.equals(data(a).serializeNBT()) && bob.equals(data(b).serializeNBT()),
                "invalid requests preserve both players and workstation inventory");
    }
    private static void exerciseInventory(ServerPlayer a, ServerPlayer b) {
        a.getInventory().clearContent(); b.getInventory().clearContent();
        var context = PlayerResearchCurrencyContext.inventory(a, data(a));
        var withdrawal = ResearchCurrencyPlanner.prepareWithdrawal(context.policy(), context.read(), 20);
        verify(ResearchCurrencyTransaction.commit(withdrawal, context, ResearchCurrencyTransaction.NONE).successful(), "native one-way withdrawal");
        verify(data(a).getResearchPoints() == 117 && a.getInventory().countItem(ModItems.SCRAP.get()) == 20, "withdrawal exact conservation");
        verify(!ResearchCurrencyTransaction.commit(withdrawal, context, ResearchCurrencyTransaction.NONE).successful(), "withdrawal cannot be claimed twice");
        var drop = a.drop(a.getInventory().removeItem(0, 7), false, false);
        verify(drop != null, "physical trade creates item entity");
        drop.setNoPickUpDelay(); drop.setTarget(b.getUUID()); drop.playerTouch(b);
        verify(drop.isRemoved() && b.getInventory().countItem(ModItems.SCRAP.get()) == 7
                && a.getInventory().countItem(ModItems.SCRAP.get()) == 13, "cross-player pickup conserves shared supplies");
        for (int i = 0; i < 36; i++) a.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        var full = ResearchCurrencyPlanner.prepareWithdrawal(context.policy(), context.read(), 1);
        verify(!full.quote().ready() && !ResearchCurrencyTransaction.commit(full, context, ResearchCurrencyTransaction.NONE).successful(), "full inventory refuses withdrawal");
        verify(data(a).getResearchPoints() == 117, "failed withdrawal preserves bank");
        a.getInventory().clearContent(); data(a).setResearchPoints(137);
        var commands = server.getCommands(); var source = a.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        verify(commandResult(commands, source, "gg progression currency inspect Alice") == 1, "native operator currency inspection");
        verify(commandResult(commands, source, "gg progression currency withdraw preview Alice 70") == 1, "native operator withdrawal preview");
        var plan = ResearchCurrencyPlanner.prepareWithdrawal(context.policy(), context.read(), 70);
        var registry = com.gamergaming.taczweaponblueprints.command.sub.ResearchCurrencyMigrationCommand.PENDING;
        var token = registry.preview(a.getUUID().toString(), a.getUUID(), server.overworld().getGameTime(), plan, context);
        var command = "gg progression currency withdraw confirm " + token;
        verify(commandResult(commands, source, command) == 1 && data(a).getResearchPoints() == 67
                && a.getInventory().countItem(ModItems.SCRAP.get()) == 70, "operator confirmation conserves bank plus Scrap");
        verify(commandResult(commands, source, command) == 0, "operator token is single-use");
        registry.clear(); a.getInventory().clearContent(); data(a).setResearchPoints(137);
    }
    public static int commandResult(net.minecraft.commands.Commands commands, net.minecraft.commands.CommandSourceStack source, String command) {
        int[] result = {0};
        commands.performPrefixedCommand(source.withCallback((success, value) -> result[0] = success ? value : 0), command);
        return result[0];
    }
    private static void exerciseFilters(ServerPlayer a, ServerPlayer b) throws Exception {
        ModConfigs.BLUEPRINT.researchDataAllowLearned.validateAndSet(false);
        ModConfigs.BLUEPRINT.initializeLoadedSnapshots();
        var catalog = BlueprintWeaponAnalysisService.context().catalog().keySet();
        for (var id : catalog) data(a).addBlueprint(id.toString());
        for (var p : List.of(a, b)) {
            var menu = (BlueprintRecyclerMenu)p.containerMenu;
            menu.getSlot(1).set(ItemStack.EMPTY); menu.refreshAuthoritativePreview(p);
            verify(menu.preview().actionable() == (p == b), "learned filter belongs to each player");
        }
        ModConfigs.BLUEPRINT.researchDataAllowLearned.validateAndSet(true);
        ModConfigs.BLUEPRINT.initializeLoadedSnapshots();
        var menu = (BlueprintRecyclerMenu)a.containerMenu; menu.refreshAuthoritativePreview(a);
        verify(menu.preview().actionable(), "allow learned restores exhausted pool");
        data(a).clearRecipes(); data(a).replaceProgression(Set.of(), Set.of(), 137);
    }
    private static int dropped(ServerPlayer p, Item item) {
        int count = 0;
        for (var entity : p.serverLevel().getAllEntities()) if (entity instanceof ItemEntity drop && drop.getItem().is(item)) count += drop.getItem().getCount();
        return count;
    }
    private static void exerciseLegacy(ServerPlayer p) {
        var config = ModConfigs.BLUEPRINT;
        try {
            config.researchCurrencyKind.validateAndSet(ResearchCurrencyPolicy.Kind.BANKED_RP);
            config.researchDataBehavior.validateAndSet(ResearchDataPolicy.Behavior.LEGACY_CURRENCY);
            config.initializeLoadedSnapshots();
            var menu = (BlueprintRecyclerMenu)p.containerMenu; menu.getSlot(1).set(ItemStack.EMPTY);
            menu.refreshAuthoritativePreview(p); int bank = data(p).getResearchPoints();
            int scrap = p.getInventory().countItem(ModItems.SCRAP.get());
            verify(menu.handleRecyclerAction(p, BlueprintRecyclerActionContract.Action.REDEEM, NOTE, 1, menu.preview().stateToken()).successful(),
                    "explicit legacy mode redeems Data through real station");
            verify(data(p).getResearchPoints() == bank + 1 && menu.inputStack().isEmpty() && menu.outputStack().isEmpty()
                    && p.getInventory().countItem(ModItems.SCRAP.get()) == scrap, "legacy redemption remains banked and deterministic");
            menu.getSlot(0).set(new ItemStack(ModItems.RESEARCH_NOTE.get())); data(p).setResearchPoints(bank);
        } finally {
            config.researchCurrencyKind.validateAndSet(ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM);
            config.researchDataBehavior.validateAndSet(ResearchDataPolicy.Behavior.ANALYSIS);
            config.initializeLoadedSnapshots();
        }
    }
    private static void saveExpected(ServerPlayer p) throws Exception {
        BlueprintLearningService.migrateLegacyUnlocks(com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager.SERVER, data(p));
        var expected = new CompoundTag(); expected.put("cap", data(p).serializeNBT()); expected.put("inventory", p.getInventory().save(new ListTag()));
        Files.writeString(ROOT.resolve(p.getGameProfile().getName() + "-expected.snbt"), expected.toString());
    }
    private static void persistence(ServerPlayer p) throws Exception {
        var expected = TagParser.parseTag(Files.readString(ROOT.resolve(p.getGameProfile().getName() + "-expected.snbt")));
        write(p.getGameProfile().getName() + "-actual.snbt", data(p).serializeNBT().toString());
        if (PHASE.equals("upgrade")) expected.getCompound("cap").put("ArchivedAnalysisFragments", new ListTag());
        verify(expected.get("cap").equals(data(p).serializeNBT()), "capability survives " + PHASE + " for " + p.getGameProfile().getName());
        verify(expected.get("inventory").equals(p.getInventory().save(new ListTag())), "physical inventory survives " + PHASE + " for " + p.getGameProfile().getName());
    }
    private static void complete() throws Exception {
        write("server-complete.json", JSON.toJson(Map.of("passed", true, "checks", checks, "assertions", checks.size())));
        write("command", "complete"); finished = true; server.halt(false);
    }
    private static void fail(Exception e) {
        try { write("server-error.txt", e + "\n" + Arrays.toString(e.getStackTrace())); write("command", "failed"); } catch (Exception ignored) { }
        finished = true; if (server != null) server.halt(false);
    }
}
