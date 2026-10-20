package com.gamergaming.taczweaponblueprints.verification;

import static com.gamergaming.taczweaponblueprints.verification.CoreVerification.*;

import com.gamergaming.taczweaponblueprints.capabilities.*;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisFragmentCounts;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.villager.OwnedOfferSerialization;
import com.gamergaming.taczweaponblueprints.villager.stock.StockCatalog;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Two real connections, native death/respawn, reload failure/recovery, and a second JVM. */
final class LifecycleVerification {
    static final String MAIN = "WRBCoreQA", PEER = "WRBPeerQA";
    static final Path SAVE = Path.of("lifecycle-expected.snbt");
    static final Map<String, CompoundTag> expected = new HashMap<>();
    static final Set<String> pending = new HashSet<>();
    static ServerPlayer peer, original;
    static List<net.minecraft.world.item.ItemStack> peerInventory;
    static CompletableFuture<Void> reload;
    static Path pack, invalid;
    static long previousRevision;
    static Villager merchant;

    static boolean enabled() { return Boolean.getBoolean("wrb.verifyLifecycle") || Boolean.getBoolean("wrb.verifyRestart"); }
    static String suite() { return Boolean.getBoolean("wrb.verifyRestart") ? "restart" : "lifecycle"; }
    static IPlayerRecipeData data(ServerPlayer p) { return ModAttachments.recipeData(p).orElseThrow(); }
    static void started() {
        // Publish the fixture configuration before login reconciliation captures it.
        // Mutating the backing value alone leaves the immutable award policy intact.
        ModConfigs.BLUEPRINT.enableResearchPointAwards = new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition<>(
                new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean(false), () -> false);
        ModConfigs.BLUEPRINT.onUpdateServer(new me.fzzyhmstrs.fzzy_config.event.api.ServerUpdateContext(server, null));
        require(!ModConfigs.BLUEPRINT.awardSnapshot().awardsEnabled(), "fixture disables unrelated automatic payouts before login");
        deadline = 12000;
    }
    static void tick() {
        deadline = 12000;
        player = server.getPlayerList().getPlayerByName(MAIN);
        peer = server.getPlayerList().getPlayerByName(PEER);
        if (player == null || peer == null) return;
        try {
            if (stage == 0) {
                blueprint = BlueprintDataManager.SERVER.getBlueprintData(TARGET.toString());
                require(blueprint != null, "lifecycle catalog contains target");
                if (suite().equals("restart")) {
                    restoreExpected();
                    unchanged("new server process reloads every progression field");
                    openBoth(221);
                    return;
                }
                server.overworld().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
                for (var p : List.of(player, peer)) {
                    p.setGameMode(GameType.SURVIVAL);
                    p.setHealth(20); p.setInvulnerable(true);
                    p.teleportTo(server.overworld(), p == player ? .5 : 2.5, 81, 2.5, Set.of(), 0, 0);
                    p.setNoGravity(true); p.getInventory().clearContent();
                    var d = data(p); d.deserializeNBT(new CompoundTag());
                    if (p == player) { d.addBlueprint(TARGET.toString()); d.addRecipe(blueprint.getRecipeId().toString()); }
                    d.discoverBlueprint("tacz:glock_17");
                    d.setResearchPoints(p == player ? 137 : 41);
                    require(d.replaceSupplementalProgression(Map.of(TARGET.toString(), p == player ? 3 : 7),
                            Map.of("wrb_core_verification:criterion", p == player ? 2 : 5),
                            Map.of(TARGET.toString(), new AnalysisFragmentCounts(1, 2, 3, p == player ? 4 : 6))), "seed independent supplemental progress " + p.getScoreboardName());
                    require(d.applyResearchPointTransaction(0, 10000, ResearchPointAwardLedger.Mutation.claim(
                            ResearchPointAwardLedger.ClaimKey.once(ResourceLocation.parse("wrb_core_verification:retained_claim")))), "seed award claim " + p.getScoreboardName());
                    for (var input : blueprint.getRecipe().getInputs()) p.getInventory().add(input.getIngredient().getItems()[0].copyWithCount(input.getCount()));
                    expected.put(p.getScoreboardName(), d.serializeNBT());
                }
                require(data(player) != data(peer), "connected players have independent attachments");
                outputs().forEach(net.minecraft.world.entity.item.ItemEntity::discard);
                peerInventory = peer.getInventory().items.stream().map(net.minecraft.world.item.ItemStack::copy).toList();
                openBoth(201);
            } else if (stage == 201 && pending.isEmpty()) {
                require(same(peerInventory, peer.getInventory().items) && outputs().isEmpty(), "unlearned peer's forged native craft is rejected without consumption");
                unchanged("one player's request cannot mutate another player's knowledge");
                player.closeContainer(); peer.closeContainer(); original = player;
                player.setRespawnPosition(Level.OVERWORLD, BENCH.south(2).above(), 0, true, false);
                stage = 203; pending.add(MAIN); player.kill();
                PacketDistributor.sendToPlayer(player, new Probe(203)); delay = 20;
            } else if (stage == 203 && pending.isEmpty()) {
                require(player != original && player.isAlive(), "native respawn replaces the actual server player");
                require(data(player) != data(original), "respawn attachment does not share mutable state with dead player");
                unchanged("death and native respawn retain all progression fields");
                player.setNoGravity(true);
                player.teleportTo(Objects.requireNonNull(server.getLevel(Level.NETHER)), .5, 100, .5, Set.of(), 0, 0);
                sendMain(205);
            } else if (stage == 205 && pending.isEmpty()) {
                unchanged("dimension change retains progression");
                player.teleportTo(server.overworld(), .5, 81, 2.5, Set.of(), 0, 0);
                pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("wrb-lifecycle");
                Files.createDirectories(pack);
                Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"Disposable lifecycle fixture\"}}");
                server.getPackRepository().reload();
                var selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
                if (!selected.contains("file/wrb-lifecycle")) selected.add("file/wrb-lifecycle");
                previousRevision = BlueprintResearchDataManager.INSTANCE.revision();
                reload = server.reloadResources(selected); stage = 206;
            } else if (stage == 206 && reload.isDone()) {
                reload.join(); require(BlueprintResearchDataManager.INSTANCE.revision() > previousRevision, "successful resource reload publishes fresh research");
                require(StockCatalog.current().coherent(), "villager catalog coherent after successful reload");
                unchanged("successful reload preserves both players' progression"); openBoth(207);
            } else if (stage == 207 && pending.isEmpty()) {
                invalid = pack.resolve("data/wrb_core_verification/taczweaponblueprints/research_profiles/invalid.json");
                Files.createDirectories(invalid.getParent()); Files.writeString(invalid, "{\"format\":-1}");
                reload = server.reloadResources(server.getPackRepository().getSelectedIds()); stage = 208;
            } else if (stage == 208 && reload.isDone()) {
                require(reload.isCompletedExceptionally(), "invalid research definition rejects the native resource reload");
                unchanged("failed reload preserves both players' progression");
                require(StockCatalog.current().coherent(), "failed reload restores coherent villager stock publication");
                openBoth(209);
            } else if (stage == 209 && pending.isEmpty()) {
                Files.delete(invalid); reload = server.reloadResources(server.getPackRepository().getSelectedIds()); stage = 210;
            } else if (stage == 210 && reload.isDone()) {
                reload.join(); unchanged("corrected datapack recovers without progression loss"); openBoth(211);
            } else if (stage == 211 && pending.isEmpty()) {
                saveExpected(); complete();
            } else if (stage == 221 && pending.isEmpty()) {
                unchanged("restarted clients retain independent server-authoritative access"); complete();
            }
        } catch (Throwable error) { fail(error); if (peer != null) PacketDistributor.sendToPlayer(peer, new Probe(-1)); }
    }
    static void unchanged(String label) {
        for (var p : List.of(player, peer)) {
            var before = expected.get(p.getScoreboardName()); var after = data(p).serializeNBT();
            if (!before.equals(after)) LOG.error("Lifecycle data mismatch for {}: expected={}, actual={}", p.getScoreboardName(), before, after);
            require(before.equals(after), label + ": " + p.getScoreboardName());
        }
    }
    static void openBoth(int next) {
        open(false);
        peer.closeContainer();
        server.overworld().getBlockState(BENCH).useWithoutItem(server.overworld(), peer, new BlockHitResult(Vec3.atCenterOf(BENCH), Direction.UP, BENCH, false));
        require(peer.containerMenu instanceof com.tacz.guns.inventory.GunSmithTableMenu, "peer opens its own native workbench menu");
        stage = next; pending.clear(); pending.addAll(List.of(MAIN, PEER)); delay = 20;
        for (var p : List.of(player, peer)) { NetworkHandler.syncAllPlayerData(p); PacketDistributor.sendToPlayer(p, new Probe(next)); }
    }
    static void sendMain(int next) { stage = next; pending.clear(); pending.add(MAIN); delay = 20; PacketDistributor.sendToPlayer(player, new Probe(next)); }
    static void acknowledge(ServerPlayer sender, int value) {
        if (!Set.of(MAIN, PEER).contains(sender.getScoreboardName())) return;
        try {
            require(value == stage && pending.remove(sender.getScoreboardName()), "identity-bound client acknowledgment " + sender.getScoreboardName() + "/" + value);
        } catch (Throwable error) { fail(error); if (peer != null) PacketDistributor.sendToPlayer(peer, new Probe(-1)); }
    }
    static void saveExpected() throws Exception {
        var root = new CompoundTag(); root.putString("Artifact", RuntimeIdentity.verify());
        player.getInventory().add(com.gamergaming.taczweaponblueprints.item.BlueprintItem.createBlueprint(
                TARGET.toString(), com.gamergaming.taczweaponblueprints.item.BlueprintProvenance.traded()));
        var gun = FeatureVerification.gun(TARGET, true);
        require(com.gamergaming.taczweaponblueprints.item.PhysicalWeaponProvenance.stampCrafted(gun, blueprint.getRecipeId()), "seed component-backed gun provenance for restart");
        player.getInventory().add(gun);
        for (var p : List.of(player, peer)) {
            p.closeContainer(); root.put(p.getScoreboardName(), data(p).serializeNBT());
            root.put(p.getScoreboardName() + "Inventory", p.getInventory().save(new ListTag()));
        }
        merchant = new Villager(EntityType.VILLAGER, server.overworld()); merchant.moveTo(4.5, 81, 2.5, 0, 0);
        merchant.setNoAi(true); merchant.setNoGravity(true); merchant.setInvulnerable(true);
        merchant.setVillagerData(merchant.getVillagerData().setProfession(ModVillagers.GUNSMITH.get()));
        server.overworld().addFreshEntity(merchant);
        var offer = merchant.getOffers().stream().filter(o -> o.getResult().getItem() instanceof com.tacz.guns.api.item.IAmmo).findFirst().orElseThrow();
        offer.increaseUses(); offer.increaseUses();
        root.putUUID("Merchant", merchant.getUUID());
        root.put("Offer", OwnedOfferSerialization.save(offer, player.registryAccess()));
        Files.writeString(SAVE, root.toString());
        server.getPlayerList().saveAll(); server.saveEverything(false, true, true);
        require(Files.isRegularFile(SAVE), "progress, inventories, and used villager offer saved for separate-process restart");
    }
    static void restoreExpected() throws Exception {
        var root = TagParser.parseTag(Files.readString(SAVE));
        require(root.getString("Artifact").equals(RuntimeIdentity.verify()), "restart snapshot belongs to the current artifact");
        for (var p : List.of(player, peer)) {
            expected.put(p.getScoreboardName(), root.getCompound(p.getScoreboardName()));
            var before = root.getList(p.getScoreboardName() + "Inventory", Tag.TAG_COMPOUND);
            var after = p.getInventory().save(new ListTag());
            if (!before.equals(after)) LOG.error("Restart inventory mismatch for {}: expected={}, actual={}", p.getScoreboardName(), before, after);
            require(before.equals(after), "player inventory survives process restart: " + p.getScoreboardName());
        }
        server.overworld().getChunkAt(BENCH);
        merchant = (Villager) server.overworld().getEntity(root.getUUID("Merchant"));
        require(merchant != null && merchant.getVillagerData().getProfession() == ModVillagers.GUNSMITH.get(), "gunsmith entity and profession survive process restart");
        var expectedOffer = OwnedOfferSerialization.read(root.getCompound("Offer"), player.registryAccess());
        require(merchant.getOffers().stream().anyMatch(o -> o.getUses() == expectedOffer.getUses()
                && net.minecraft.world.item.ItemStack.matches(o.getResult(), expectedOffer.getResult())
                && Objects.equals(((com.gamergaming.taczweaponblueprints.villager.OwnedVillagerOffer)o).taczweaponblueprints$binding(),
                        ((com.gamergaming.taczweaponblueprints.villager.OwnedVillagerOffer)expectedOffer).taczweaponblueprints$binding())), "used villager stock identity and output components survive process restart");
        require(server.overworld().getBlockState(BENCH).is(ModBlocks.WORKBENCH_LVL3.get()), "tiered workstation persists in the saved world");
    }
    static void complete() { PacketDistributor.sendToPlayer(peer, new Probe(99)); pass(); }
}
