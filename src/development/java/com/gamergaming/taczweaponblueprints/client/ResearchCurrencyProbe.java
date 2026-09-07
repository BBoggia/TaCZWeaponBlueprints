package com.gamergaming.taczweaponblueprints.client;

import java.nio.file.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.capabilities.*;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.progression.currency.*;
import com.gamergaming.taczweaponblueprints.resource.award.*;
import com.gamergaming.taczweaponblueprints.resource.research.ProgressionPolicyAccessService;
import com.gamergaming.taczweaponblueprints.block.BlueprintRecyclerBlock;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.api.item.IGun;
import me.fzzyhmstrs.fzzy_config.validation.misc.*;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;

/** Opt-in assertions on the real server, inventory, registered Scrap, and Blueprint Analyzer menu. */
final class ResearchCurrencyProbe {
    private static final ResourceLocation TARGET = new ResourceLocation("tacz:ak47");
    private static final List<String> checks = new ArrayList<>();
    private ResearchCurrencyProbe() { }
    private static void verifyWithdrawal(ServerPlayer player, IPlayerRecipeData data) throws Exception {
        var original = data.serializeNBT().copy();
        player.getInventory().clearContent(); data.setResearchPoints(137);
        var live = PlayerResearchCurrencyContext.inventory(player, data);
        var plan = ResearchCurrencyPlanner.prepareWithdrawal(live.policy(), live.read(), 70);
        check(plan.quote().ready() && data.getResearchPoints() == 137, "withdrawal preview preserves live bank and inventory");
        var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        var commands = player.server.getCommands();
        check(commands.performPrefixedCommand(source, "gg progression currency inspect " + player.getGameProfile().getName()) == 1, "native currency inspection command");
        check(commands.performPrefixedCommand(source, "gg progression currency withdraw preview " + player.getGameProfile().getName() + " 70") == 1, "native withdrawal preview command");
        var registry = com.gamergaming.taczweaponblueprints.command.sub.ResearchCurrencyMigrationCommand.PENDING;
        var token = registry.preview(player.getUUID().toString(), player.getUUID(), player.server.overworld().getGameTime(), plan, live);
        String confirm = "gg progression currency withdraw confirm " + token;
        check(commands.performPrefixedCommand(source, confirm) == 1, "native withdrawal confirmation command");
        check(data.getResearchPoints() == 67 && ResearchCurrencyPayments.balance(live.policy(),player.getInventory().items,data) == 70, "native withdrawal conserves RP plus Scrap");
        check(commands.performPrefixedCommand(source, confirm) == 0, "native withdrawal token retry rejected");
        var after = data.serializeNBT().copy();
        var expectedData = original.copy(); expectedData.putInt("ResearchPoints",67);
        check(expectedData.equals(after), "withdrawal preserves all noncurrency player data");
        data.deserializeNBT(after);
        check(data.getResearchPoints() == 67, "withdrawn balance survives native data reload");
        for(int i=0;i<36;i++)player.getInventory().items.set(i,new ItemStack(Items.STONE,64));
        check(!ResearchCurrencyPlanner.prepareWithdrawal(live.policy(),live.read(),1).quote().ready(), "native full inventory prevents withdrawal");
        check(commands.performPrefixedCommand(source, "gg progression currency withdraw preview MissingOfflinePlayer 1") == 0, "native offline player conversion rejected");
        registry.clear();
        var configPath=net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("taczweaponblueprints/blueprint.toml");
        byte[] configBytes=Files.readAllBytes(configPath);
        var kind=ModConfigs.BLUEPRINT.researchCurrencyKind.get(); var mode=ModConfigs.BLUEPRINT.researchDataBehavior.get();
        try {
            check(commands.performPrefixedCommand(source,"gg progression currency legacy apply")==1,"native legacy preset saved and verified");
            check(ModConfigs.BLUEPRINT.currencyPolicy().kind()==ResearchCurrencyPolicy.Kind.BANKED_RP
                    && ModConfigs.BLUEPRINT.dataSettings().behavior()==com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPolicy.Behavior.LEGACY_CURRENCY,"legacy preset selects independent banked and Data modes");
        } finally {
            ModConfigs.BLUEPRINT.researchCurrencyKind.validateAndSet(kind);ModConfigs.BLUEPRINT.researchDataBehavior.validateAndSet(mode);
            publish();Files.write(configPath,configBytes);
        }
        data.deserializeNBT(original); player.getInventory().clearContent();
    }

    static void verify(ServerPlayer player, BlockPos pos, Path output) throws Exception {
        var config = ModConfigs.BLUEPRINT;
        var oldMode = config.researchDataBehavior; var oldKind = config.researchCurrencyKind; var oldCap = config.researchPointCap; var oldRecovery = config.foundWeaponRecoveryMode;
        try {
            config.researchDataBehavior = new ValidatedEnum<>(com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPolicy.Behavior.LEGACY_CURRENCY);
            config.researchCurrencyKind = new ValidatedEnum<>(ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM);
            config.researchPointCap = new ValidatedCondition<>(new ValidatedInt(0, 1_000_000_000, 0), () -> 0);
            config.foundWeaponRecoveryMode = new ValidatedCondition<>(new ValidatedEnum<>(FoundWeaponRecoveryMode.PLAYER_CHOICE), () -> FoundWeaponRecoveryMode.PLAYER_CHOICE);
            publish();
            var data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve().orElseThrow();
            var menu = open(player, pos); reset(player, data, menu);
            ResearchEconomySupplyProbe.verify(player, menu, output);
            reset(player, data, menu);
            verifyWithdrawal(player, data);
            reset(player, data, menu);
            menu.getSlot(0).set(new ItemStack(ModItems.RESEARCH_REPORT.get(), 3)); menu.refreshAuthoritativePreview(player);
            check(menu.preview().actionable(), "Research Report is redeemable at zero bank cap");
            var redeemed = ResearchDataRedemptionService.redeemInput(player, menu.inputStack(), new ResourceLocation("taczweaponblueprints:research_report"), 3, true);
            check(redeemed.successful() && redeemed.consumedItems() == 3 && redeemed.awardedPoints() == 9, "bulk Report redemption delivers all nine Scrap");
            check(menu.inputStack().isEmpty() && menu.outputStack().is(ModItems.SCRAP.get()) && menu.outputStack().getCount() == 9, "Research Data uses the extract-only output slot");
            check(data.getResearchPoints() == 137, "Research Data preserves dormant RP");
            reset(player, data, menu); menu.getSlot(0).set(new ItemStack(ModItems.RESEARCH_NOTE.get())); menu.getSlot(1).set(new ItemStack(Items.STONE));
            check(!ResearchDataRedemptionService.evaluateInput(player, menu.inputStack()).redeemable(), "occupied output blocks Data preview");
            var before = data.serializeNBT().copy();
            redeemed = ResearchDataRedemptionService.redeemInput(player, menu.inputStack(), new ResourceLocation("taczweaponblueprints:research_note"), 1, false);
            check(!redeemed.successful() && menu.inputStack().getCount() == 1 && before.equals(data.serializeNBT()), "blocked Data retains input and claims");
            check(redeemed.newBalance() == 0, "failed Data return reports carried Scrap instead of dormant RP");
            check(ResearchDataRedemptionService.evaluateInput(player, ItemStack.EMPTY).pointBalance() == 0,
                    "empty Data input reports active currency balance");
            check(BlueprintResearchService.researchFromInventory(player, TARGET).balanceAfterCost() == 0,
                    "Bench rejection reports active currency balance");
            reset(player, data, menu); data.addBlueprint(TARGET.toString()); menu.getSlot(0).set(BlueprintItem.createBlueprint(TARGET.toString()));
            var recycled = BlueprintRecyclingService.recycle(player, menu.inputStack());
            check(recycled.successful() && menu.outputStack().is(ModItems.SCRAP.get()) && menu.inputStack().isEmpty(), "duplicate blueprint recycling produces Scrap");
            check(data.getResearchPoints() == 137, "recycling preserves dormant RP");
            reset(player, data, menu); player.getInventory().setItem(0, new ItemStack(ModItems.SCRAP.get(), 3)); player.getInventory().setItem(1, new ItemStack(Items.IRON_INGOT, 2));
            menu.getSlot(0).set(gun()); menu.refreshAuthoritativePreview(player);
            check(menu.preview().pointCost() == 3 && menu.preview().actionable(), "weapon analysis preview sees physical payment");
            var attempt = BlueprintReverseEngineeringService.reverseEngineerDetailed(player, new Station(menu, player));
            check(attempt.transaction().successful() && menu.inputStack().isEmpty() && player.getInventory().getItem(0).isEmpty(), "weapon attempt spends three Scrap");
            check(player.getInventory().getItem(1).isEmpty() && data.getResearchPoints() == 137, "weapon attempt spends materials but leaves RP intact");
            reset(player, data, menu); menu.getSlot(0).set(gun());
            var recovery = FoundWeaponRecoveryService.recover(player, new Station(menu, player));
            check(recovery.successful() && menu.outputStack().is(ModItems.SCRAP.get()) && menu.inputStack().isEmpty(), "verified found-weapon recovery delivers Scrap");
            check(data.getResearchPoints() == 137, "recovery preserves dormant RP");
            reset(player, data, menu); data.addBlueprint(TARGET.toString());
            var resolved = ProgressionPolicyAccessService.acquire(ProgressionPolicyAccessService.Mode.ENSURE_CURRENT).orElseThrow().policyFor(TARGET).orElseThrow();
            var fragments = BlueprintFragmentItem.create(TARGET); fragments.setCount(resolved.fragments().threshold()); menu.getSlot(0).set(fragments);
            menu.refreshAuthoritativePreview(player);
            var archived = menu.handleRecyclerAction(player, BlueprintRecyclerActionContract.Action.ARCHIVE_FRAGMENTS, TARGET, fragments.getCount(), menu.preview().stateToken());
            check(archived.successful() && menu.outputStack().is(ModItems.SCRAP.get()), "learned ordinary fragment set delivers Scrap");
            check(data.getResearchPoints() == 137, "ordinary fragments preserve dormant RP");
            reset(player, data, menu); data.addBlueprint(TARGET.toString());
            data.applyArchivedFragmentMutation(PlayerProgressValueMutation.Request.commit(TARGET.toString(), 0, resolved.fragments().retainedProgressCap()));
            menu.getSlot(0).set(BlueprintFragmentItem.create(TARGET)); menu.refreshAuthoritativePreview(player);
            check(menu.preview().fragmentEvaluation().orElseThrow().accepted() == 0 && menu.preview().actionable(), "saved completed set can be redeemed without depositing input");
            archived = menu.handleRecyclerAction(player, BlueprintRecyclerActionContract.Action.ARCHIVE_FRAGMENTS, TARGET, 1, menu.preview().stateToken());
            check(archived.successful() && menu.inputStack().getCount() == 1 && menu.outputStack().is(ModItems.SCRAP.get()), "saved fragment set preserves the unaccepted input");
            // Real entity publication and claim persistence with a full inventory.
            player.closeContainer(); player.getInventory().clearContent(); data.getResearchPointAwardLedger().clear();
            for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            var profile = config.awardSnapshot().activeProfileId();
            var definition = ResearchPointAwardDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"format":1,"profiles":["%s"],"award_group":"qa:currency","trigger":{"type":"blueprint_discovered"},
                 "reward":{"points":129,"overflow":"clamp"},"repeat":{"type":"once_per_target","claim_id":"qa:currency"},
                 "presentation":{"visibility":"public","name":"qa.currency"}}
                """.formatted(profile))).result().orElseThrow();
            var context = ResearchPointAwardContext.simple(ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED, profile, TARGET);
            var resolution = ResearchPointAwardResolver.resolve(ResearchPointAwardSnapshot.create(Map.of(new ResourceLocation("qa:currency"), definition)), context);
            var existingDrops = player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(3)).stream().map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet());
            var listener = new CancelCurrencyDrop(); net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(listener);
            try {
                var canceled = ResearchPointAwardService.awardResolved(player, data, resolution, context, config.awardSnapshot(), player.serverLevel().getGameTime());
                check(!canceled.changed() && data.getResearchPointAwardLedger().isEmpty() && data.getResearchPoints() == 137, "canceled native entity spawn restores the claim and bank");
                check(player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(3), e -> e.getItem().is(ModItems.SCRAP.get()) && !existingDrops.contains(e.getUUID())).isEmpty(), "canceled native spawn leaves no Scrap entity");
            } finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener); }
            var result = ResearchPointAwardService.awardResolved(player, data, resolution, context, config.awardSnapshot(), player.serverLevel().getGameTime());
            check(result.awardedPoints() == 129 && data.getResearchPoints() == 137, "full inventory award delivers physical overflow without touching RP");
            var dropped = player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(3), e -> e.getItem().is(ModItems.SCRAP.get()) && !existingDrops.contains(e.getUUID()));
            check(dropped.size() == 3 && dropped.stream().mapToInt(e -> e.getItem().getCount()).sum() == 129, "native overflow creates exactly three bounded Scrap entities");
            var saved = data.serializeNBT().copy(); data.deserializeNBT(saved);
            result = ResearchPointAwardService.awardResolved(player, data, resolution, context, config.awardSnapshot(), player.serverLevel().getGameTime());
            check(!result.changed() && data.getResearchPointAwardLedger().claimCount() == 1, "persisted finite claim cannot be awarded twice");
            dropped.forEach(net.minecraft.world.entity.Entity::discard);
            config.researchCurrencyKind = new ValidatedEnum<>(ResearchCurrencyPolicy.Kind.BANKED_RP);
            config.researchPointCap = new ValidatedCondition<>(new ValidatedInt(1000, 1_000_000_000, 0), () -> 1000);
            publish(); data.getResearchPointAwardLedger().clear();
            var bankedConfig = config.awardSnapshot();
            var switching = new SwitchCurrencyBeforeAward();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(switching);
            try {
                var switched = ResearchPointAwardService.awardResolved(player, data, resolution, context, bankedConfig, player.serverLevel().getGameTime());
                check(switching.called && !switched.changed() && data.getResearchPoints() == 137,
                        "banked pre-award currency switch aborts without changing dormant RP");
                check(data.getResearchPointAwardLedger().isEmpty(), "banked pre-award switch does not claim the reward");
            } finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(switching); }
            ResearchDataTransactionProbe.verify(player, open(player, pos), output);
            Files.createDirectories(output); Files.writeString(output.resolve("currency-complete.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed", true, "assertions", checks.size(), "checks", checks)));
        } finally { config.researchDataBehavior = oldMode; config.researchCurrencyKind = oldKind; config.researchPointCap = oldCap; config.foundWeaponRecoveryMode = oldRecovery; publish(); player.closeContainer(); }
    }
    private static final class SwitchCurrencyBeforeAward {
        boolean called;
        @net.minecraftforge.eventbus.api.SubscribeEvent public void change(com.gamergaming.taczweaponblueprints.api.event.ResearchPointAwardEvent.Pre event) {
            called = true;
            ModConfigs.BLUEPRINT.researchCurrencyKind = new ValidatedEnum<>(ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM);
            try { publish(); } catch (Exception failure) { throw new IllegalStateException(failure); }
        }
    }
    private static final class CancelCurrencyDrop {
        @net.minecraftforge.eventbus.api.SubscribeEvent public void cancel(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item && item.getItem().is(ModItems.SCRAP.get())) event.setCanceled(true);
        }
    }
    private static void reset(ServerPlayer player, IPlayerRecipeData data, BlueprintRecyclerMenu menu) {
        player.getInventory().clearContent(); menu.getSlot(0).set(ItemStack.EMPTY); menu.getSlot(1).set(ItemStack.EMPTY);
        data.replaceProgression(Set.of(), Set.of(TARGET.toString()), 137); data.clearArchivedBlueprintFragments(); data.clearArchivedAnalysisFragments(); data.getResearchPointAwardLedger().clear();
    }
    static BlueprintRecyclerMenu open(ServerPlayer player, BlockPos pos) {
        player.closeContainer(); var block = (BlueprintRecyclerBlock) ModBlocks.BLUEPRINT_RECYCLER.get(); player.serverLevel().removeBlock(pos, false);
        check(BlueprintRecyclerBlock.placeCompleteStructure(player.serverLevel(), pos, block.defaultBlockState()), "place native Analyzer");
        block.use(player.serverLevel().getBlockState(pos), player.serverLevel(), pos, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        return (BlueprintRecyclerMenu) player.containerMenu;
    }
    private static ItemStack gun() {
        var stack = new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz:modern_kinetic_gun")));
        IGun.getIGunOrNull(stack).setGunId(stack, TARGET); PhysicalWeaponProvenance.stampLootGenerated(stack, new ResourceLocation("minecraft:chests/simple_dungeon")); return stack;
    }
    private static void publish() throws Exception { var method = ModConfigs.BLUEPRINT.getClass().getDeclaredMethod("publishProgressionSnapshot"); method.setAccessible(true); method.invoke(ModConfigs.BLUEPRINT); }
    private static void check(boolean valid, String message) { if (!valid) throw new IllegalStateException(message); checks.add(message); }
    private record Station(BlueprintRecyclerMenu menu, ServerPlayer player) implements BlueprintReverseEngineeringService.WorkstationTransaction {
        public ResearchCurrencyPolicy currencyPolicy() { return ModConfigs.BLUEPRINT.currencyPolicy(); }
        public Optional<com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier> analyzerTier() { return menu.analyzerTier(); }
        public boolean stationValid() { return menu.isRecyclerMenuValid(player); }
        public ItemStack physicalInput() { return menu.inputStack(); }
        public ItemStack outputStack() { return menu.outputStack(); }
        public List<ItemStack> inventoryStacks() { return player.getInventory().items.stream().map(ItemStack::copy).toList(); }
        public ItemStack createOutput(ResourceLocation id, BlueprintProvenance provenance) { return BlueprintItem.createBlueprint(id.toString(), provenance); }
        public boolean consumeMaterials(ResearchIngredientPlanner.Plan plan, List<ItemStack> expected) {
            var after = plan.applyToCopies(expected); for (int i=0;i<36;i++) if (!ItemStack.matches(expected.get(i),player.getInventory().items.get(i))) return false;
            for (int i=0;i<36;i++) player.getInventory().items.set(i,after.get(i)); return true;
        }
        public boolean consumePhysical(ItemStack expected, int count) { if (!ItemStack.matches(expected,menu.inputStack())) return false; menu.inputStack().shrink(count); return true; }
        public boolean placeOutput(ItemStack output, ItemStack expected) { if (!ItemStack.matches(expected,menu.outputStack())) return false; menu.getSlot(1).set(output); return true; }
        public boolean restore(ItemStack input,List<ItemStack> inventory,ItemStack output) { for(int i=0;i<36;i++)player.getInventory().items.set(i,inventory.get(i).copy());menu.getSlot(0).set(input);menu.getSlot(1).set(output);return true; }
    }
}
