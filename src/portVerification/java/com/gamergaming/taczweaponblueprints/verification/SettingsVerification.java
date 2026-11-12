package com.gamergaming.taczweaponblueprints.verification;

import static com.gamergaming.taczweaponblueprints.verification.CoreVerification.*;
import java.nio.file.Files;
import java.util.*;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.init.ModAttachments;
import com.gamergaming.taczweaponblueprints.settings.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** A real operator/non-operator connection exercising the production settings packets. */
final class SettingsVerification {
    private static Map<String, String> original;
    private static CompoundTag progress;
    private static ServerPlayer previousConnection;
    private static boolean reconnected;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static List<net.minecraft.world.item.ItemStack> savedInventory;
    private static net.minecraft.world.entity.npc.Villager merchant;
    private static net.minecraft.nbt.Tag offers;
    private static boolean integrated() { return Boolean.getBoolean("wrb.verifyIntegrated"); }
    static boolean enabled() { return Boolean.getBoolean("wrb.verifySettings"); }
    static void started() {
        // Keep unrelated passive awards out of the settings-only progression comparison.
        ModConfigs.BLUEPRINT.enableResearchPointAwards.validateAndSet(false);
        ModConfigs.BLUEPRINT.initializeLoadedSnapshots(); ModConfigs.BLUEPRINT.save();
        deadline = 6000;
    }
    static void login(ServerPlayer player) { if (stage == 409 && player != previousConnection) reconnected = true; }
    static void tick() {
        try {
            if (stage == 0) {
                if (integrated()) SettingsIntegratedVerification.publishLan();
                original = FzzySettingsAdapter.capture(ModConfigs.BLUEPRINT);
                var data = ModAttachments.recipeData(player).orElseThrow();
                data.addBlueprint(TARGET.toString());
                data.addRecipe(com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager.SERVER.getBlueprintData(TARGET.toString()).getRecipeId().toString());
                data.discoverBlueprint("tacz:glock_17"); data.setResearchPoints(137);
                require(data.replaceSupplementalProgression(Map.of(TARGET.toString(), 3), Map.of("wrb_core_verification:criterion", 2),
                        Map.of(TARGET.toString(), new com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisFragmentCounts(1, 2, 3, 4))), "seed nonempty fragment and criterion state");
                require(data.applyResearchPointTransaction(0, 10000, com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.Mutation.claim(
                        com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.ClaimKey.once(net.minecraft.resources.ResourceLocation.parse("wrb_core_verification:settings_claim")))), "seed retained award claim");
                com.gamergaming.taczweaponblueprints.network.NetworkHandler.syncAllPlayerData(player);
                progress = data.serializeNBT();
                player.getInventory().setItem(8, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
                savedInventory = inventory();
                merchant = FeatureVerification.villager(com.gamergaming.taczweaponblueprints.init.ModVillagers.BALLISTICS_RESEARCHER.get(), 2);
                var tag = new CompoundTag(); merchant.saveWithoutId(tag); offers = tag.get("Offers").copy();
                surfaceChecks();
                if (integrated()) {
                    server.getPlayerList().op(player.getGameProfile());
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                            new com.gamergaming.taczweaponblueprints.network.SyncBalancePresetPacket(BlueprintBalancePreset.CUSTOM));
                    send(402);
                }
                else { server.getPlayerList().deop(player.getGameProfile()); send(401); }
            } else if (stage == 409 && reconnected) {
                require(player != previousConnection, "reconnect uses a new server player connection");
                verifyDisk(); send(410);
            } else if (stage == 412 && reload != null && reload.isDone()) {
                reload.join(); reload = null; send(413);
            }
        } catch (Throwable error) { fail(error); }
    }
    static void acknowledge(int value) {
        acknowledge(player, value);
    }
    static void acknowledge(ServerPlayer sender, int value) {
        try {
            if (value >= 420) { concurrent(sender, value); return; }
            if (sender != player) return;
            require(value == stage, "settings client acknowledgment " + value);
            var config = ModConfigs.BLUEPRINT;
            if (stage == 401) { server.getPlayerList().op(player.getGameProfile()); send(402); }
            else if (stage == 402) {
                require(original.equals(FzzySettingsAdapter.capture(config)), "open and discard leave server settings unchanged");
                unchangedProgress(); send(integrated() ? 405 : 403);
            } else if (stage == 403) { server.getPlayerList().deop(player.getGameProfile()); send(404); }
            else if (stage == 404) {
                require(original.equals(FzzySettingsAdapter.capture(config)), "non-operator apply rejected after permission loss");
                unchangedProgress(); server.getPlayerList().op(player.getGameProfile()); send(405);
            } else if (stage == 405) {
                var expected = new LinkedHashMap<>(original); expected.put("enableGunsmith", SettingsValues.bool(false));
                require(expected.equals(FzzySettingsAdapter.capture(config)), "simple apply changes exactly its intended field");
                verifyDisk(); unchangedProgress(); send(406);
            } else if (stage == 406) {
                require(config.progressionPreset.getUnconditional() == ResearchProgressionPreset.CUSTOM
                        && config.customEnforceResearchTiers.getUnconditional() && !config.customEnforceCraftingTiers.getUnconditional(), "compound apply preserves research enforcement while disabling crafting levels");
                var expected = new LinkedHashMap<>(original); expected.put("enableGunsmith", SettingsValues.bool(false));
                expected.put("progressionPreset", SettingsValues.token(ResearchProgressionPreset.CUSTOM));
                expected.put("customEnforceResearchTiers", SettingsValues.bool(true)); expected.put("customEnforceCraftingTiers", SettingsValues.bool(false));
                require(expected.equals(FzzySettingsAdapter.capture(config)), "compound apply preserves all unrelated and dormant fields");
                verifyDisk(); unchangedProgress(); send(407);
            } else if (stage == 407) {
                config.enableJournal.validateAndSet(false); config.save();
                config.onUpdateServer(new me.fzzyhmstrs.fzzy_config.event.api.ServerUpdateContext(server, null));
                SettingsSynchronization.publish(config, server); send(408);
            } else if (stage == 408) {
                require(config.enableBallisticsResearcher.get() && !config.enableJournal.getUnconditional(), "stale apply preserves another operator's settings");
                if (integrated()) {
                    require(!server.isDedicatedServer(), "settings recipes, edits and stale review exercised in singleplayer"); unchangedProgress();
                    var peer = server.getPlayerList().getPlayerByName("WRBPeerQA");
                    require(peer != null, "remote LAN guest is connected");
                    stage = 431; net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(peer, new Probe(431)); return;
                }
                unchangedProgress(); previousConnection = player; stage = 409; delay = 20;
                player.connection.disconnect(Component.literal("WRB settings reconnect verification"));
            } else if (stage == 410) { unchangedProgress(); send(412); }
            else if (stage == 412) { reload = server.reloadResources(server.getPackRepository().getSelectedIds()); }
            else if (stage == 413) {
                require(config.enableBallisticsResearcher.get(), "datapack reload rejects an older settings proposal");
                send(414);
            } else if (stage == 414) {
                Files.writeString(config.getDir().toPath().resolve(config.getName() + ".toml"), "\n# settings external editor fixture\n", java.nio.file.StandardOpenOption.APPEND);
                send(415);
            } else if (stage == 415) {
                require(config.enableBallisticsResearcher.get(), "server file edit rejects an older settings proposal");
                var peer = server.getPlayerList().getPlayerByName("WRBPeerQA");
                require(peer != null, "second settings operator is connected"); server.getPlayerList().op(peer.getGameProfile()); send(420);
            }
        } catch (Throwable error) { fail(error); }
    }
    private static void unchangedProgress() {
        require(progress.equals(ModAttachments.recipeData(player).orElseThrow().serializeNBT()), "settings flow preserves learned blueprints, RP, fragments, and award state");
        require(same(savedInventory, inventory()), "settings flow preserves inventory");
        var tag = new CompoundTag(); merchant.saveWithoutId(tag);
        require(offers.equals(tag.get("Offers")), "settings flow preserves saved villager stock");
    }
    private static void concurrent(ServerPlayer sender, int value) {
        var peer = server.getPlayerList().getPlayerByName("WRBPeerQA");
        if (value == 431) {
            require(stage == 431 && sender == peer, "remote LAN guest acknowledges synchronized settings");
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(peer, new Probe(99)); pass(); return;
        }
        require(value == stage && (value == 421 || value == 423 ? sender == peer : sender == player), "concurrent settings acknowledgment " + value);
        if (value == 420) { stage = 421; net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(peer, new Probe(421)); }
        else if (value == 421) send(422);
        else if (value == 422) {
            require(ModConfigs.BLUEPRINT.enableGunsmith.get(), "first operator's settings were applied");
            stage = 423; net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(peer, new Probe(423));
        } else if (value == 423) {
            require(ModConfigs.BLUEPRINT.enableGunsmith.get() && ModConfigs.BLUEPRINT.enableBallisticsResearcher.get(), "second operator's stale edit cannot overwrite the first operator");
            unchangedProgress(); net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(peer, new Probe(99)); pass();
        }
    }
    private static void surfaceChecks() throws Exception {
        var config = ModConfigs.BLUEPRINT;
        var transaction = new SettingsTransaction(config, SettingsTransaction.fzzyStore(config), () -> "surface-fixture", () -> {
            config.onUpdateServer(new me.fzzyhmstrs.fzzy_config.event.api.ServerUpdateContext(server, player));
            SettingsSynchronization.publish(config, server);
        });
        var position = player.position();
        player.teleportTo(server.overworld(), .5, 85, 2.5, Set.of(), 0, 0);
        FeatureVerification.placeResearch(); var research = player.containerMenu;
        applyRecipe(transaction, SettingsRecipes.Recipe.SIMPLER_STATIONS);
        require(!research.stillValid(player), "station recipe edit invalidates an already-open Research Bench");
        var analyzer = FeatureVerification.openAnalyzer(1, net.minecraft.world.item.ItemStack.EMPTY);
        long analyzerToken = analyzer.preview().stateToken();
        applyRecipe(transaction, SettingsRecipes.Recipe.RECOMMENDED);
        analyzer.broadcastChanges();
        require(analyzer.stillValid(player) && analyzer.preview().stateToken() != analyzerToken,
                "station recipe edit refreshes the open Analyzer and invalidates its old action token");
        player.teleportTo(server.overworld(), .5, 81, 2.5, Set.of(), 0, 0);
        open(true);
        var before = com.gamergaming.taczweaponblueprints.progression.CraftingEligibilityService.evaluate(player, menu(),
                com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager.SERVER.getBlueprintData(TARGET.toString()).getRecipeId());
        var snapshot = transaction.capture(UUID.randomUUID(), true, SettingsPresentation.Profile.unavailable(config.activeResearchProfile.getUnconditional()));
        var edit = new SettingsEditSession(snapshot); edit.setCraftingTiers(false);
        require(transaction.apply(snapshot, edit.plan(), true) == SettingsTransaction.Status.APPLIED, "coordinated settings edit applies with crafting menu open");
        var after = com.gamergaming.taczweaponblueprints.progression.CraftingEligibilityService.evaluate(player, menu(),
                com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager.SERVER.getBlueprintData(TARGET.toString()).getRecipeId());
        require(before.status() != after.status(), "open native crafting menu evaluates the new tier policy: " + before.status() + " -> " + after.status());
        player.closeContainer(); player.teleportTo(server.overworld(), position.x, position.y, position.z, Set.of(), 0, 0);
        snapshot = transaction.capture(UUID.randomUUID(), true, SettingsPresentation.Profile.unavailable(config.activeResearchProfile.getUnconditional()));
        edit = new SettingsEditSession(snapshot);
        Map<String, String> restore = new LinkedHashMap<>(); var current = snapshot.values();
        original.forEach((key, value) -> { if (!value.equals(current.get(key))) restore.put(key, value); });
        edit.setAll(restore);
        require(transaction.apply(snapshot, edit.plan(), true) == SettingsTransaction.Status.APPLIED, "surface fixture restores the original server configuration");
        unchangedProgress();
    }
    private static void applyRecipe(SettingsTransaction transaction, SettingsRecipes.Recipe recipe) throws Exception {
        var snapshot = transaction.capture(UUID.randomUUID(), true, SettingsPresentation.Profile.unavailable(ModConfigs.BLUEPRINT.activeResearchProfile.getUnconditional()));
        var edit = new SettingsEditSession(snapshot); SettingsRecipes.queue(edit, SettingsRecipes.preview(edit, recipe));
        require(transaction.apply(snapshot, edit.plan(), true) == SettingsTransaction.Status.APPLIED, "server applies complete recipe batch " + recipe);
    }
    private static void verifyDisk() throws Exception {
        var config = ModConfigs.BLUEPRINT;
        String text = Files.readString(config.getDir().toPath().resolve(config.getName() + ".toml"));
        var restored = ConfigApi.deserializeConfig(new com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig(), text).get();
        restored.initializeLoadedSnapshots();
        require(FzzySettingsAdapter.capture(config).equals(FzzySettingsAdapter.capture(restored)), "accepted settings survive save and reload with flat field paths");
    }
}
