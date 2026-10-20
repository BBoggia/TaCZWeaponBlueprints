package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.gamergaming.taczweaponblueprints.capabilities.*;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.item.BlueprintFragmentItem;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerPreview;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.resource.award.*;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.gamergaming.taczweaponblueprints.testing.AnalysisTestItems;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Pre-Scrap inputs, read independently of current writers; never regenerate on test failure. */
class ResearchEconomyCompatibilityTest {
    @BeforeAll static void initialize() { AnalysisTestItems.initialize(); }

    @Test void frozenInputsRetainTheirRecordedChecksums() throws Exception {
        var files = json("manifest.json").getAsJsonObject("files");
        assertEquals(28, files.size());
        for (var entry : files.entrySet()) {
            assertEquals(entry.getValue().getAsString(), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes(entry.getKey()))), entry.getKey());
        }
    }

    @Test void playerVersionFivePreservesCurrencyKnowledgeBothArchivesAndClaimHistory() throws Exception {
        var original = nbt("player-v5.snbt");
        var player = new PlayerRecipeData(); player.deserializeNBT(original);
        assertEquals(137, player.getResearchPoints());
        assertEquals(Set.of("tacz:cz75"), player.getLearnedBlueprints());
        assertEquals(Set.of("tacz:gun/cz75"), player.getLearnedRecipes());
        assertEquals(Set.of("tacz:cz75","tacz:ai_awp","tacz:aa12"), player.getDiscoveredBlueprints());
        assertEquals(Map.of("tacz:aa12",4), player.getArchivedBlueprintFragments());
        assertEquals(new AnalysisFragmentCounts(2,3,4,5), player.getArchivedAnalysisFragments().get("tacz:ai_awp"));
        assertEquals(Map.of("qa:persisted",2), player.getProgressionCriteria());
        assertEquals(14, player.getRecentUnlockBatches().size());
        var ledger = player.getResearchPointAwardLedger();
        assertEquals(original.getCompound("ResearchPointAwards").getList("Claims",Tag.TAG_COMPOUND).size(), ledger.claimCount());
        assertTrue(ledger.hasClaim(new ResearchPointAwardLedger.ClaimKey(
                id("taczweaponblueprints:first_blueprint_discovery"),Optional.of(id("tacz:cz75")))));
        var restored = new PlayerRecipeData(); restored.deserializeNBT(player.serializeNBT());
        assertEquals(original, restored.serializeNBT(), "resaving cannot erase old RP, claims, or fragment classes");
    }

    @Test void nativeAnalysisItemsRetainAllFourPermissionClassesAndRejectOrdinaryRedemption() throws Exception {
        var entries = nbt("analysis-inventory.snbt").getList("Inventory",Tag.TAG_COMPOUND);
        var seen = EnumSet.noneOf(AnalysisRewardClass.class);
        for (var raw : entries) {
            var item = (CompoundTag) raw;
            if (!item.getString("id").equals("taczweaponblueprints:blueprint_fragment")) continue;
            var stack = new ItemStack(AnalysisTestItems.fragment,item.getByte("Count"));
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(item.getCompound("tag").copy()));
            var identity = BlueprintFragmentItem.identity(stack).orElseThrow();
            assertEquals(id("tacz:ai_awp"),identity.target());
            seen.add(identity.analysisClass().orElseThrow());
            assertTrue(BlueprintFragmentItem.getOrdinaryTarget(stack).isEmpty());
        }
        assertEquals(EnumSet.allOf(AnalysisRewardClass.class),seen);
        var ordinary = new ItemStack(AnalysisTestItems.fragment);
        ordinary.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(nbt("ordinary-item-tags.snbt").getCompound("fragment")));
        assertEquals(id("tacz:ak47"),BlueprintFragmentItem.getOrdinaryTarget(ordinary).orElseThrow());
    }

    @Test void damagedHistoricalAnalysisMarkerCannotBecomeAnOrdinaryReward() throws Exception {
        var tag = nbt("analysis-inventory.snbt").getList("Inventory",Tag.TAG_COMPOUND).getCompound(0).getCompound("tag").copy();
        tag.getCompound(BlueprintFragmentItem.ANALYSIS_TAG).putString("reward_class","future_class");
        var stack = new ItemStack(AnalysisTestItems.fragment); stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        assertTrue(BlueprintFragmentItem.identity(stack).isEmpty());
        assertTrue(BlueprintFragmentItem.getOrdinaryTarget(stack).isEmpty());
    }

    @Test void storedServerConfigurationKeepsExplicitCostModesCapsAndRecoveryChoice() throws Exception {
        var defaults = ConfigApi.deserializeConfig(new BlueprintConfig(),read("config-default-v4.toml")).get();
        defaults.initializeLoadedSnapshots();
        assertEquals(10000,defaults.progressionSnapshot().pointCap());
        assertEquals(ResearchCostMode.POINTS_AND_ITEMS,defaults.researchCostMode.get());
        var custom = ConfigApi.deserializeConfig(new BlueprintConfig(),read("config-custom-v4.toml")).get();
        custom.initializeLoadedSnapshots();
        assertEquals(2676,custom.progressionSnapshot().pointCap());
        assertEquals(FoundWeaponRecoveryMode.PLAYER_CHOICE,custom.foundWeaponRecoveryMode.get());
        assertEquals(id("taczweaponblueprints:duplicate_recovery"),custom.progressionSnapshot().activeProfileId());
        assertTrue(custom.awardSnapshot().awardsEnabled());
    }

    @Test void missingEconomySelectorsUseConstructorDefaultsAndExplicitSelectorsWin() throws Exception {
        var fresh = new BlueprintConfig();
        var old = ConfigApi.deserializeConfig(new BlueprintConfig(),read("config-custom-v4.toml")).get();
        old.initializeLoadedSnapshots();
        assertEquals(fresh.researchCurrencyKind.get(),old.researchCurrencyKind.get());
        assertEquals(fresh.researchDataBehavior.get(),old.researchDataBehavior.get());
        String explicit = read("config-custom-v4.toml") + "\nresearchCurrencyKind = \"PHYSICAL_ITEM\"\nresearchDataBehavior = \"LEGACY_CURRENCY\"\nresearchCurrencyItem = \"minecraft:iron_ingot\"\n";
        var selected = ConfigApi.deserializeConfig(new BlueprintConfig(),explicit).get();
        selected.initializeLoadedSnapshots();
        assertEquals(com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM,selected.currencyPolicy().kind());
        assertEquals(ResearchDataPolicy.Behavior.LEGACY_CURRENCY,selected.dataSettings().behavior());
        assertEquals(2676,selected.researchPointCap.get());
        assertEquals(FoundWeaponRecoveryMode.PLAYER_CHOICE,selected.foundWeaponRecoveryMode.get());
    }

    @Test void oldProfilesKeepTheirSeparateLegacyAndTieredAnalysisSemantics() throws Exception {
        var legacy = profile("profile-legacy-v4.json");
        assertTrue(legacy.analyzer().isEmpty());
        assertTrue(legacy.reverseEngineering().allowKnown());
        assertEquals(0,legacy.reverseEngineering().cost().points());
        assertEquals(8,legacy.researchCost().points());
        var tiered = profile("profile-tiered-v5.json").analyzer().orElseThrow();
        assertEquals(BlueprintAnalyzerPolicy.Mode.TIERED,tiered.mode());
        var row = tiered.tiers().get(AnalyzerTier.TIER_1).get(AnalyzerTier.TIER_3);
        assertEquals(new AnalyzerOutcomeTable(10,90,0,1,1),row.outcomes());
        assertEquals(15,tiered.reconstruction().orElseThrow().thresholds().get(AnalyzerTier.TIER_3));
    }

    @Test void historicalAwardsPreserveFiniteClaimsAndOneThreeSixDataValues() throws Exception {
        Map<ResourceLocation,ResearchPointAwardDefinition> definitions = new LinkedHashMap<>();
        for (var name : json("manifest.json").getAsJsonObject("files").keySet()) {
            if (!name.startsWith("awards/")) continue;
            var award = ResearchPointAwardDefinition.CODEC.parse(JsonOps.INSTANCE,json(name)).result().orElseThrow();
            definitions.put(id("taczweaponblueprints:"+name.substring(7,name.length()-5)),award);
        }
        var snapshot = ResearchPointAwardSnapshot.create(definitions);
        assertEquals(15,snapshot.enabledDefinitionCount());
        for (var name : Map.of("note",1,"report",3,"dossier",6).entrySet()) {
            var award = definitions.get(id("taczweaponblueprints:research_data/"+name.getKey()));
            assertEquals(name.getValue(),award.reward().points());
            assertEquals(ResearchPointAwardReward.Overflow.REQUIRE_FULL,award.reward().overflow());
            assertEquals(ResearchPointAwardRepeat.Type.UNLIMITED,award.repeat().type());
        }
        assertEquals(46,definitions.values().stream().filter(d -> d.repeat().finite()
                && d.trigger().type()!=ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED).mapToInt(d -> d.reward().points()).sum());
    }

    @Test void frozenDataPreviewShowsRedemptionWithoutAnalysisOrMaterialCosts() throws Exception {
        var b = buffer("preview-data-v59.hex");
        try {
            var p = new SyncBlueprintRecyclerPreviewPacket(b).preview();
            assertEquals(0,b.readableBytes());
            assertEquals(BlueprintRecyclerPreview.InputKind.RESEARCH_DATA,p.inputKind());
            assertEquals(id("taczweaponblueprints:research_report"),p.inputId().orElseThrow());
            assertEquals(4,p.inputCount()); assertEquals(3,p.pointValue());
            assertEquals(137,p.pointBalance()); assertEquals(2676,p.pointCap()); assertEquals(71,p.stateToken());
            assertEquals(ResearchDataRedemptionService.Status.SUCCESS,p.researchDataStatus().orElseThrow());
            assertEquals(0,p.pointCost()); assertTrue(p.presentation().weapon().isEmpty());
        } finally { b.release(); }
    }

    @Test void frozenWeaponPreviewRetainsIndependentCostsOddsAndProtectedProgress() throws Exception {
        var b = buffer("preview-weapon-v59.hex");
        try {
            var p = new SyncBlueprintRecyclerPreviewPacket(b).preview();
            assertEquals(0,b.readableBytes()); assertEquals(3,p.pointCost());
            assertEquals(2,p.ingredients().get(0).required()); assertEquals(4,p.ingredients().get(0).inventoryAvailable());
            assertEquals(7,p.presentation().recoveryCost().orElseThrow().points());
            var w = p.presentation().weapon().orElseThrow();
            assertEquals(3,w.tier()); assertEquals(15,w.threshold()); assertEquals(2,w.saved()); assertEquals(14,w.totalSaved());
            assertEquals(AnalysisRewardClass.PROTECTED_PREREQUISITES,w.reward().orElseThrow());
            assertEquals(new AnalyzerOutcomeTable(10,90,0,1,1),w.outcomes().orElseThrow());
        } finally { b.release(); }
    }

    @Test void historicalCommittedResultsDistinguishBlueprintFragmentsAndNoResult() throws Exception {
        for (var kind : AnalyzerOutcomeTable.Kind.values()) {
            var b = buffer("result-"+kind.name().toLowerCase(Locale.ROOT)+"-v59.hex");
            try {
                var packet = new BlueprintRecyclerActionResultPacket(b);
                assertEquals(0,b.readableBytes()); assertEquals(7,packet.containerId()); assertEquals(9,packet.requestId());
                assertEquals(kind,packet.result().outcome().orElseThrow().kind());
                assertEquals(73,packet.result().stateToken());
            } finally { b.release(); }
        }
    }

    private static BlueprintResearchProfile profile(String name) throws Exception {
        return BlueprintResearchProfile.CODEC.parse(JsonOps.INSTANCE,json(name)).result().orElseThrow();
    }
    private static ResourceLocation id(String raw) { return ResourceLocation.parse(raw); }
    private static net.minecraft.network.FriendlyByteBuf buffer(String name) throws Exception {
        return new net.minecraft.network.FriendlyByteBuf(Unpooled.wrappedBuffer(HexFormat.of().parseHex(read(name).strip())));
    }
    private static CompoundTag nbt(String name) throws Exception { return TagParser.parseTag(read(name)); }
    private static JsonObject json(String name) throws Exception { return JsonParser.parseString(read(name)).getAsJsonObject(); }
    private static String read(String name) throws Exception { return new String(bytes(name),StandardCharsets.UTF_8); }
    private static byte[] bytes(String name) throws Exception {
        try (var input = ResearchEconomyCompatibilityTest.class.getResourceAsStream("/research-economy-compatibility/"+name)) {
            assertNotNull(input,"Missing frozen fixture "+name); return input.readAllBytes();
        }
    }
}
