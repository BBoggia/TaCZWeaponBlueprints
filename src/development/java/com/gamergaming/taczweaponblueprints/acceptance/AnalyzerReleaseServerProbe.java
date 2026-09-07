package com.gamergaming.taczweaponblueprints.acceptance;

import static com.gamergaming.taczweaponblueprints.acceptance.AnalyzerReleaseExchange.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.block.BlueprintRecyclerBlock;
import com.gamergaming.taczweaponblueprints.capabilities.*;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPayments;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Opt-in dedicated-server acceptance. No client classes may be referenced here. */
@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID)
public final class AnalyzerReleaseServerProbe {
    private static MinecraftServer server;
    private static int step, age, warmup, assertions, attempts, reconnect;
    private static boolean ready, finished;
    private static BlueprintRecyclerPreview before;
    private static ItemStack loadedInput = ItemStack.EMPTY, returnedAmmo = ItemStack.EMPTY;
    private static int expectedAmmo, ammoBefore;
    private static final boolean LOADED_AMMO = Boolean.getBoolean("tacz.analyzerReleaseQA.loadedAmmo");
    private static final List<ResourceLocation> targets = new ArrayList<>();
    private static final JsonArray cases = new JsonArray();
    private static me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition<FoundWeaponRecoveryMode> originalRecovery;
    private AnalyzerReleaseServerProbe() { }
    private static void verify(boolean condition, String message) { check(condition, message); assertions++; }
    @SubscribeEvent public static void start(ServerStartingEvent event) {
        if (!ENABLED) return;
        server = event.getServer();
        try {
            verify(Set.of("gameplay", "restart", "migration").contains(PHASE), "recognized acceptance phase");
            verify(server.isDedicatedServer(), "real dedicated server");
            verify(Files.isRegularFile(server.getWorldPath(LevelResource.ROOT).resolve(".analyzer-release-copy")), "marked disposable world");
            requireFreshRun(ROOT, PHASE);
            if (PHASE.equals("migration")) {
                var uuid = Files.readString(ROOT.resolve("player-uuid.txt")).trim();
                var path = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat");
                var player = NbtIo.readCompressed(path.toFile());
                var cap = player.getCompound("ForgeCaps").getCompound("taczweaponblueprints:player_recipe_data");
                verify(cap.getInt("DataVersion") == 5, "version-5 saved player exists before migration fixture");
                cap.putInt("DataVersion", 4); cap.remove("ArchivedAnalysisFragments");
                NbtIo.writeCompressed(player, path.toFile());
                write("migration-fixture.snbt", cap.toString());
            }
            write("command", "connect");
        } catch (Exception e) { fail(e); }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || finished || server == null || event.phase != TickEvent.Phase.END) return;
        try {
            if (++age > 12000) throw new IllegalStateException("Dedicated Analyzer acceptance timed out");
            if (!read("client-error.txt").isEmpty()) throw new IllegalStateException("Client acceptance failed: " + read("client-error.txt"));
            var players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) {
                warmup = 0;
                if (reconnect == 1) { reconnect = 2; write("command", "reconnect"); }
                return;
            }
            var player = players.get(0);
            if (warmup++ < 60) return;
            if (!PHASE.equals("gameplay") || reconnect == 2) {
                verifyPersistence(player, PHASE.equals("migration"));
                complete(); return;
            }
            if (reconnect == 1) return;
            if (step == 20) { seedPersistence(player); reconnect = 1; write("command", "disconnect"); return; }
            if (!ready) {
                before = setup(player, step); ready = true;
                write("preview", before.withStateToken(0).toString());
                var request = new JsonObject(); request.addProperty("step", step); request.addProperty("name", name(step));
                request.addProperty("action", action(step)); request.addProperty("attempt", attempts);
                write("request.json", JSON.toJson(request)); return;
            }
            if (!read("ack").equals(step + ":" + attempts)) return;
            verifyResult(player);
            var row = new JsonObject(); row.addProperty("case", name(step)); row.addProperty("attempt", attempts);
            row.addProperty("preview", before.toString());
            if (LOADED_AMMO && !loadedInput.isEmpty()) {
                row.addProperty("loadedInput", loadedInput.save(new CompoundTag()).toString());
                row.addProperty("expectedReturnedRounds", action(step) ? expectedAmmo : 0);
                row.addProperty("physicalCurrency", physicalCurrency());
            }
            var menu = (BlueprintRecyclerMenu)player.containerMenu;
            row.addProperty("output", menu.outputStack().save(new CompoundTag()).toString());
            cases.add(row);
            if (step == 10 && !(menu.outputStack().getItem() instanceof BlueprintFragmentItem)) {
                verify(++attempts < 64, "observe partial result within bounded genuine attempts");
            } else { step++; attempts = 0; }
            ready = false;
        } catch (Exception e) { fail(e); }
    }
    private static boolean action(int n) { return n == 9 || n == 10 || n == 11 || n >= 16; }
    private static String name(int n) {
        if (n < 9) return "station-" + (n / 3 + 1) + "-weapon-" + (n % 3 + 1);
        return switch (n) { case 9 -> "full-blueprint"; case 10 -> "partial-fragment"; case 11 -> "reconstruction";
            case 12 -> "known-target"; case 13 -> "occupied-output"; case 14 -> "insufficient-rp"; case 15 -> "insufficient-iron";
            case 16 -> "research-data"; case 17 -> "ordinary-fragment-discount"; case 18 -> "duplicate-blueprint"; default -> "direct-rp"; };
    }
    private static BlueprintRecyclerPreview setup(ServerPlayer p, int n) {
        var context = BlueprintWeaponAnalysisService.context(); var config = ModConfigs.BLUEPRINT.progressionSnapshot();
        if (targets.isEmpty()) for (int tier = 1; tier <= 3; tier++) {
            final int wanted = tier;
            targets.add(context.catalog().keySet().stream().sorted().filter(id -> {
                var q = BlueprintAnalyzerPolicyResolver.resolve(context, config.activeProfileId(), id, AnalyzerTier.TIER_1,
                        com.gamergaming.taczweaponblueprints.resource.research.BlueprintAnalyzerPolicy.Origin.FOUND, config.foundWeaponRecoveryMode());
                return q.status() == AnalyzerPolicyQuote.Status.CONFIGURED && q.weaponTier().orElseThrow().level() == wanted;
            }).findFirst().orElseThrow());
        }
        p.closeContainer(); p.getInventory().clearContent();
        var data = data(p); var target = targets.get(n < 9 ? n % 3 : n == 10 || n == 11 ? 2 : 0);
        // Legacy recipe knowledge is migrated during synchronization; reset both knowledge channels.
        data.clearRecipes();
        data.replaceProgression(n == 12 || n == 18 ? Set.of(target.toString()) : Set.of(), Set.of(target.toString()), n == 14 ? 0 : 100);
        data.clearArchivedBlueprintFragments(); data.clearArchivedAnalysisFragments();
        p.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, n == 15 ? 1 : 8));
        if (physicalCurrency() && n != 14) {
            p.getInventory().setItem(1, new ItemStack(ModItems.SCRAP.get(), 50));
            p.getInventory().setItem(2, new ItemStack(ModItems.SCRAP.get(), 50));
        }
        int tier = n < 9 ? n / 3 + 1 : n == 9 ? 2 : 1;
        var block = switch (tier) { case 1 -> (BlueprintRecyclerBlock)ModBlocks.BLUEPRINT_RECYCLER.get(); case 2 -> ModBlocks.ADVANCED_BLUEPRINT_ANALYZER.get(); default -> ModBlocks.EXPERIMENTAL_BLUEPRINT_ANALYZER.get(); };
        var pos = p.blockPosition().offset(2, 0, 0); p.serverLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        p.serverLevel().removeBlock(pos, false);
        if (!com.gamergaming.taczweaponblueprints.block.BlueprintRecyclerBlock.placeCompleteStructure(
                p.serverLevel(), pos, block.defaultBlockState())) throw new IllegalStateException("Analyzer fixture needs two blocks");
        block.use(p.serverLevel().getBlockState(pos), p.serverLevel(), pos, p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        var menu = (BlueprintRecyclerMenu)p.containerMenu;
        var item = ForgeRegistries.ITEMS.getValues().stream().filter(i -> i instanceof IGun).findFirst().orElseThrow();
        var input = new ItemStack(item); ((IGun)item).setGunId(input, target);
        PhysicalWeaponProvenance.stampLootGenerated(input, new ResourceLocation("minecraft:chests/simple_dungeon"));
        if (n == 11) {
            input = BlueprintFragmentItem.createAnalysis(target, AnalysisRewardClass.PROTECTED_PREREQUISITES);
            data.applyAnalysisFragmentMutation(new AnalysisFragmentMutation(PlayerProgressValueMutation.Operation.COMMIT,
                    target.toString(), AnalysisFragmentCounts.EMPTY, new AnalysisFragmentCounts(14, 0, 0, 0)));
        }
        if (n == 13) menu.getSlot(1).set(new ItemStack(Items.STONE));
        if (n == 16) input = new ItemStack(ModItems.RESEARCH_NOTE.get(), 3);
        if (n == 17) { input = new ItemStack(ModItems.BLUEPRINT_FRAGMENT.get(), 5); input.getOrCreateTag().putString("BlueprintTarget", target.toString()); }
        if (n == 18) input = BlueprintItem.createBlueprint(target.toString());
        if (n == 19) {
            originalRecovery = ModConfigs.BLUEPRINT.foundWeaponRecoveryMode;
            ModConfigs.BLUEPRINT.foundWeaponRecoveryMode = new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition<>(
                    new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum<>(FoundWeaponRecoveryMode.DIRECT_RP_ONLY), () -> FoundWeaponRecoveryMode.DIRECT_RP_ONLY);
            republish();
        }
        loadedInput = ItemStack.EMPTY; returnedAmmo = ItemStack.EMPTY; expectedAmmo = 0;
        if (LOADED_AMMO && (n == 9 || n == 10 || n >= 12 && n <= 15 || n == 19)) {
            var gun = IGun.getIGunOrNull(input);
            gun.setCurrentAmmoCount(input, n == 10 ? 0 : 5); gun.setBulletInBarrel(input, true);
            var refund = AnalyzerAmmoRecovery.plan(input).orElseThrow();
            expectedAmmo = refund.count();
            if (expectedAmmo > 0) returnedAmmo = refund.stacks().get(0).copy();
            loadedInput = input.copy();
            if (n == 9) for (int slot=physicalCurrency()?3:1;slot<36;slot++) p.getInventory().setItem(slot,new ItemStack(Items.STONE,64));
            ammoBefore = ammunitionCount(p, returnedAmmo);
        }
        menu.getSlot(0).set(input); menu.broadcastChanges(); menu.refreshAuthoritativePreview(p);
        var preview = menu.preview();
        if (n < 9) {
            var odds = preview.presentation().weapon().orElseThrow().outcomes().orElseThrow();
            int[][] percentages = {{70, 30, 10}, {100, 70, 30}, {100, 100, 80}};
            verify(odds.blueprintWeight() * 100 == percentages[n / 3][n % 3] * (odds.blueprintWeight() + odds.fragmentWeight() + odds.noResultWeight()), "shipped probability matrix");
            int partialQuantity = odds.fragmentWeight() > 0 ? 1 : 0;
            int stationTier=n/3+1, weaponTier=n%3+1;
            int empty=weaponTier>stationTier ? (weaponTier-stationTier==2?50:20) : 0;
            verify(odds.noResultWeight() == empty && odds.minimumFragments() == partialQuantity && odds.maximumFragments() == partialQuantity, "shipped fragment quantity and mismatch risk");
            verify(preview.pointCost() == 3 && preview.ingredients().get(0).required() == 2, "shipped cost");
            verify(preview.presentation().stationTier() == tier && preview.presentation().weapon().orElseThrow().tier() == n % 3 + 1, "station and weapon tier presentation");
        }
        verify(preview.actionable() == !(n >= 12 && n <= 15), "expected action availability: " + name(n) + " " + preview);
        return preview;
    }
    private static void verifyResult(ServerPlayer p) {
        var menu = (BlueprintRecyclerMenu)p.containerMenu; var data = data(p);
        if (LOADED_AMMO && !loadedInput.isEmpty()) {
            verify(ammunitionCount(p, returnedAmmo) == ammoBefore + (action(step) ? expectedAmmo : 0),
                    "loaded ammo conserved for " + name(step) + ": " + expectedAmmo + " returned on success");
            if (!action(step)) verify(ItemStack.matches(loadedInput, menu.inputStack()), "blocked action preserves complete loaded gun NBT");
            if (step == 9 && expectedAmmo > 0) verify(p.getInventory().items.stream().noneMatch(stack -> ItemStack.isSameItemSameTags(stack, returnedAmmo)), "full inventory sends ammo to world");
        }
        if (LOADED_AMMO && step == 9) {
            int rounds = ammunitionCount(p, returnedAmmo);
            var replay = menu.handleRecyclerAction(p, BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER,
                    before.inputId().orElseThrow(), before.inputCount(), before.stateToken());
            verify(!replay.successful(), "loaded weapon action cannot be replayed");
            verify(ammunitionCount(p, returnedAmmo) == rounds, "replay cannot duplicate recovered ammo");
        }
        if (physicalCurrency()) verify(data.getResearchPoints() == (step == 14 ? 0 : 100), "physical operations preserve dormant banked RP");
        if (!action(step)) { verify(menu.inputStack().getCount() == before.inputCount(), "viewing does not consume input"); return; }
        if (step == 9 || step == 10) {
            verify(menu.inputStack().isEmpty() && currencyBalance(p) == 97 && p.getInventory().countItem(Items.IRON_INGOT) == 6, "exact analysis payment");
            verify(step != 9 || !menu.outputStack().isEmpty(), "lower-tier weapon always returns blueprint");
            if (step == 9) verify(menu.outputStack().getItem() instanceof BlueprintItem, "guaranteed full blueprint");
            else verify(menu.outputStack().isEmpty() || menu.outputStack().getCount() == 1, "partial, full or no-result output count");
        } else if (step == 11) {
            verify(menu.outputStack().getItem() instanceof BlueprintItem && menu.inputStack().isEmpty(), "reconstructed blueprint output");
            verify(data.getArchivedAnalysisFragments().isEmpty() && data.getResearchPoints() == 100, "reconstruction spends set without RP");
        } else if (step == 17) {
            verify(data.getArchivedBlueprintFragments().values().stream().mapToInt(Integer::intValue).sum() == 5, "ordinary fragments archived separately");
            verify(data.getArchivedAnalysisFragments().isEmpty(), "ordinary fragments do not populate analysis archive: " + data.getArchivedAnalysisFragments());
            verify(menu.outputStack().isEmpty(), "ordinary discount does not reconstruct: " + menu.outputStack());
            verify(data.getLearnedBlueprints().isEmpty(), "ordinary discount does not unlock targets: " + data.getLearnedBlueprints());
        } else {
            int outputCurrency = physicalCurrency() && menu.outputStack().is(ModItems.SCRAP.get()) ? menu.outputStack().getCount() : 0;
            verify(currencyBalance(p) + outputCurrency > 100, "active currency award: " + name(step));
            verify(menu.inputStack().getCount() < before.inputCount(), "legacy input consumed");
        }
    }
    private static boolean physicalCurrency() { return ResearchCurrencyPayments.physical(ModConfigs.BLUEPRINT.currencyPolicy()); }
    private static int currencyBalance(ServerPlayer p) {
        return ResearchCurrencyPayments.balance(ModConfigs.BLUEPRINT.currencyPolicy(), p.getInventory().items, data(p));
    }
    private static int ammunitionCount(ServerPlayer player, ItemStack ammo) {
        if (ammo.isEmpty()) return 0;
        int count=player.getInventory().items.stream().filter(stack->ItemStack.isSameItemSameTags(stack,ammo)).mapToInt(ItemStack::getCount).sum();
        for(var entity:player.serverLevel().getAllEntities()) if(entity instanceof net.minecraft.world.entity.item.ItemEntity item && ItemStack.isSameItemSameTags(item.getItem(),ammo)) count+=item.getItem().getCount();
        return count;
    }
    private static IPlayerRecipeData data(ServerPlayer p) { return p.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve().orElseThrow(); }
    private static void seedPersistence(ServerPlayer p) throws Exception {
        restore(); p.closeContainer(); p.getInventory().clearContent(); var d = data(p);
        d.replaceProgression(Set.of(targets.get(0).toString()), targets.stream().map(Object::toString).toList(), 137);
        d.replaceSupplementalProgression(Map.of(targets.get(1).toString(), 4), Map.of("qa:persisted", 2),
                Map.of(targets.get(2).toString(), new AnalysisFragmentCounts(2, 3, 4, 5)));
        for (var kind : AnalysisRewardClass.values()) p.getInventory().add(BlueprintFragmentItem.createAnalysis(targets.get(2), kind));
        StartingBlueprintGrantService.applyConfiguredGrants(p);
        // Save canonical knowledge, including the recipe corresponding to the learned blueprint.
        BlueprintLearningService.migrateLegacyUnlocks(com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager.SERVER, d);
        for (var id : targets) ResearchPointAwardDispatcher.blueprintTransitions(p, d, id, true, d.getLearnedBlueprints().contains(id.toString()));
        p.getInventory().clearContent();
        for (var kind : AnalysisRewardClass.values()) p.getInventory().add(BlueprintFragmentItem.createAnalysis(targets.get(2), kind));
        Files.writeString(ROOT.resolve("player-uuid.txt"), p.getUUID().toString());
        Files.writeString(ROOT.resolve("expected-player.snbt"), d.serializeNBT().toString());
        var inv = new CompoundTag(); inv.put("Inventory", p.getInventory().save(new ListTag()));
        Files.writeString(ROOT.resolve("expected-inventory.snbt"), inv.toString());
        server.getPlayerList().saveAll(); server.saveEverything(true, true, true);
    }
    private static void verifyPersistence(ServerPlayer p, boolean migrated) throws Exception {
        var expected = TagParser.parseTag(Files.readString(ROOT.resolve("expected-player.snbt")));
        var actual = data(p).serializeNBT();
        for (var key : List.of("Recipes", "Blueprints", "DiscoveredBlueprints", "ResearchPoints", "ArchivedBlueprintFragments", "ProgressionCriteria"))
            verify(Objects.equals(expected.get(key), actual.get(key)), "saved field survives " + PHASE + ": " + key
                    + " expected=" + expected.get(key) + " actual=" + actual.get(key));
        verify(actual.getInt("DataVersion") == 5, "current saved schema");
        verify(migrated ? data(p).getArchivedAnalysisFragments().isEmpty() : Objects.equals(expected.get("ArchivedAnalysisFragments"), actual.get("ArchivedAnalysisFragments")), "separate analysis classes survive or initialize empty for v4");
        var inv = TagParser.parseTag(Files.readString(ROOT.resolve("expected-inventory.snbt")));
        verify(inv.get("Inventory").equals(p.getInventory().save(new ListTag())), "physical marked fragment classes survive persisted inventory");
        server.getPlayerList().saveAll();
    }
    private static void complete() throws Exception {
        var report = new JsonObject(); report.addProperty("passed", true); report.addProperty("phase", PHASE);
        report.addProperty("assertions", assertions); report.add("cases", cases);
        write("server-complete.json", JSON.toJson(report)); finished = true; restore();
        write("command", "complete"); server.halt(false);
    }
    private static void republish() { try { var m = ModConfigs.BLUEPRINT.getClass().getDeclaredMethod("publishProgressionSnapshot"); m.setAccessible(true); m.invoke(ModConfigs.BLUEPRINT); } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); } }
    private static void restore() { if (originalRecovery != null) { ModConfigs.BLUEPRINT.foundWeaponRecoveryMode = originalRecovery; originalRecovery = null; republish(); } }
    private static void fail(Exception e) { try { write("server-error.txt", e + "\n" + Arrays.toString(e.getStackTrace())); write("command", "failed"); } catch (Exception ignored) { } finished = true; restore(); if (server != null) server.halt(false); }
}
