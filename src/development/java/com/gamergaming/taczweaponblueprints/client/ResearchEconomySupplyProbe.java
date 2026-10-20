package com.gamergaming.taczweaponblueprints.client;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.progression.currency.*;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPolicy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;

/** Disposable-world checks using actual vanilla loot tables and shipped Data policies. */
final class ResearchEconomySupplyProbe {
    static void verify(ServerPlayer player,BlueprintRecyclerMenu menu,Path output) throws Exception {
        var checks=new ArrayList<String>();var config=ModConfigs.BLUEPRINT;
        var oldMode=config.researchDataBehavior.get();var oldKind=config.researchCurrencyKind.get();
        boolean oldEnabled=config.enableScrapLoot.get();int oldBudget=config.scrapLootBudget.get();
        var original=com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager.INSTANCE.snapshot();
        try {
            var profiles=new HashMap<>(original.profiles());
            try(var in=ResearchEconomySupplyProbe.class.getResourceAsStream("/data/taczweaponblueprints/taczweaponblueprints/research_profiles/duplicate_recovery.json")) {
                var profile=com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,JsonParser.parseString(new String(Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8))).getOrThrow();
                profiles.put(ResourceLocation.parse("taczweaponblueprints:duplicate_recovery"),profile);
                check(profile.analyzer().isEmpty(),"shipped profile replaces the QA Analyzer override",checks);
            }
            ResearchDataTransactionProbe.apply(com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot.create(original.tags(),profiles,original.rules(),original.groups(),original.techTrees(),original.techTreeEntryBundles(),original.automaticPlacementProfiles()).withResearchData(original.researchData()));
            var fresh=new com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig();
            check(fresh.currencyPolicy().kind()==ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM&&fresh.dataSettings().behavior()==ResearchDataPolicy.Behavior.ANALYSIS,"fresh defaults select Scrap and Data analysis",checks);
            config.researchDataBehavior.validateAndSet(ResearchDataPolicy.Behavior.ANALYSIS);config.enableScrapLoot.validateAndSet(true);config.scrapLootBudget.validateAndSet(32);config.initializeLoadedSnapshots();
            for(int i=0;i<12;i++){int amount=loot(player);check(amount>=8&&amount<=16,"early dungeon supplies 8-16 physical currency",checks);}
            config.scrapLootBudget.validateAndSet(9);check(loot(player)<=9,"supply respects reduced budget",checks);
            config.scrapLootBudget.validateAndSet(0);check(loot(player)==0,"zero budget stops supply",checks);
            config.scrapLootBudget.validateAndSet(32);config.enableScrapLoot.validateAndSet(false);check(loot(player)==0,"disabled supply stops injection",checks);
            config.enableScrapLoot.validateAndSet(true);config.researchCurrencyKind.validateAndSet(ResearchCurrencyPolicy.Kind.BANKED_RP);config.initializeLoadedSnapshots();check(loot(player)==0,"banked mode adds no physical supply",checks);
            config.researchCurrencyKind.validateAndSet(ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM);config.initializeLoadedSnapshots();
            var data=com.gamergaming.taczweaponblueprints.init.ModAttachments.recipeData(player).orElseThrow();data.setResearchPoints(0);player.getInventory().clearContent();
            for(var item:List.of(ModItems.RESEARCH_NOTE.get(),ModItems.RESEARCH_REPORT.get(),ModItems.RESEARCH_DOSSIER.get())) {
                menu.getSlot(1).set(ItemStack.EMPTY);menu.getSlot(0).set(new ItemStack(item));menu.refreshAuthoritativePreview(player);
                check(menu.preview().actionable(),"shipped Data ready without Scrap",checks);
                check(menu.handleRecyclerAction(player,com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract.Action.ANALYZE_DATA,net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item),1,menu.preview().stateToken()).successful(),"shipped Data produces physical reward",checks);
                check(!menu.outputStack().isEmpty()&&data.getResearchPoints()==0,"Data does not mint banked RP",checks);
            }
            var source=player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            check(com.gamergaming.taczweaponblueprints.acceptance.ResearchEconomyServerProbe.commandResult(player.server.getCommands(), source,"gg research export")==1,"resolved live economy export",checks);
            var summary=new JsonObject();summary.addProperty("passed",true);summary.addProperty("assertions",checks.size());summary.add("checks",new Gson().toJsonTree(checks));Files.writeString(output.resolve("supply-complete.json"),new GsonBuilder().setPrettyPrinting().create().toJson(summary));
        } finally {config.researchDataBehavior.validateAndSet(oldMode);config.researchCurrencyKind.validateAndSet(oldKind);config.enableScrapLoot.validateAndSet(oldEnabled);config.scrapLootBudget.validateAndSet(oldBudget);config.initializeLoadedSnapshots();ResearchDataTransactionProbe.apply(original);}
    }
    static int loot(ServerPlayer player) {
        var table=player.server.reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, ResourceLocation.parse("minecraft:chests/simple_dungeon")));
        var params=new LootParams.Builder(player.serverLevel()).withParameter(LootContextParams.ORIGIN,player.position()).create(LootContextParamSets.CHEST);
        return table.getRandomItems(params).stream().filter(s->s.is(ModItems.SCRAP.get())).mapToInt(ItemStack::getCount).sum();
    }
    static void check(boolean condition,String label,List<String> checks){if(!condition)throw new IllegalStateException(label);checks.add(label);}
}
