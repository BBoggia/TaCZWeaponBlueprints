package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.network.SyncResearchBenchPreviewPacket;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.progression.currency.*;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;

class ResearchSelectionCurrencyPreviewTest {
    private static final ResourceLocation ROOT = id("test:root"), TARGET = id("test:target"), PROFILE = id("test:profile");
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void learnedPrerequisiteWithMaterialsButNoScrapStillHasAValidPreview() {
        verify(new Fixture(), 2, true, false, ResearchGuidanceSnapshot.State.MISSING_POINTS);
    }
    @Test void unlearnedPrerequisiteKeepsTheWholeRouteAndOnlyReportsMissingCurrency() {
        var f = new Fixture(); f.data = new PlayerRecipeData();
        var e = verify(f, 4, true, false, ResearchGuidanceSnapshot.State.MISSING_POINTS);
        assertEquals(2, e.unlockCount()); assertEquals(7, e.pointCost());
    }
    @Test void missingMaterialsAndScrapRemainDistinct() {
        var f = new Fixture(); f.inventory.set(0, ItemStack.EMPTY);
        verify(f, 0, false, false, ResearchGuidanceSnapshot.State.MISSING_POINTS_AND_MATERIALS);
    }
    @Test void fullyFundedRouteIsReadyAndPreviewDoesNotConsumeInventory() {
        var f = new Fixture(); f.inventory.set(1, new ItemStack(Items.IRON_INGOT, 3));
        verify(f, 2, true, true, ResearchGuidanceSnapshot.State.AFFORDABLE);
    }
    @Test void currencyOnlyCostCanBeUnaffordableWithAnEmptyCompleteMaterialList() {
        var f = new Fixture(); f.mode = ResearchCostMode.POINTS_ONLY;
        verify(f, 0, true, false, ResearchGuidanceSnapshot.State.MISSING_POINTS);
    }
    @Test void bankedPointsModeRetainsItsExistingIndependentMaterialCounts() {
        var f = new Fixture(); f.banked = true;
        verify(f, 2, true, false, ResearchGuidanceSnapshot.State.MISSING_POINTS);
    }
    @Test void overlappingCurrencyAndMaterialsReserveTheCurrencyBeforeShowingMaterialCounts() {
        var f = overlap(); f.inventory.set(1, new ItemStack(Items.IRON_INGOT, 4));
        verify(f, 1, false, false, ResearchGuidanceSnapshot.State.MISSING_MATERIALS);
    }
    @Test void overlappingCostIsReadyOnlyWhenTheCombinedAmountIsPresent() {
        var f = overlap(); f.inventory.set(1, new ItemStack(Items.IRON_INGOT, 7));
        verify(f, 4, true, true, ResearchGuidanceSnapshot.State.AFFORDABLE);
    }
    @Test void overlapWithTooLittleCurrencyDoesNotAllocateTheSameUnitsTwice() {
        var f = overlap(); f.inventory.set(1, new ItemStack(Items.IRON_INGOT, 2));
        verify(f, 0, false, false, ResearchGuidanceSnapshot.State.MISSING_POINTS_AND_MATERIALS);
    }
    @Test void alternativesCanUseNonCurrencyItemsAfterCurrencyIsReserved() {
        var f = overlap();
        f.materials = List.of(new BlueprintResearchIngredient(
                List.of(id("minecraft:iron_ingot"), id("minecraft:gold_ingot")), Optional.empty(), 4));
        f.inventory.set(1, new ItemStack(Items.IRON_INGOT, 3));
        f.inventory.set(2, new ItemStack(Items.GOLD_INGOT, 4));
        verify(f, 4, true, true, ResearchGuidanceSnapshot.State.AFFORDABLE);
    }
    @Test void currencyReservationSpansStacksAndPreservesNamedCurrency() {
        var f = overlap();
        var named = new ItemStack(Items.IRON_INGOT, 2);
        named.setHoverName(net.minecraft.network.chat.Component.literal("Named currency"));
        f.inventory.set(1, named); f.inventory.set(8, new ItemStack(Items.IRON_INGOT, 5));
        verify(f, 4, true, true, ResearchGuidanceSnapshot.State.AFFORDABLE);
    }
    @Test void itemsOnlyModeDoesNotReserveAnyCurrency() {
        var f = overlap(); f.mode = ResearchCostMode.ITEMS_ONLY;
        f.inventory.set(1, new ItemStack(Items.IRON_INGOT, 4));
        verify(f, 4, true, true, ResearchGuidanceSnapshot.State.AFFORDABLE);
    }
    @Test void creativeBypassDoesNotRequireOrConsumeCurrency() {
        var f = new Fixture(); f.creative = true; f.inventory.set(0, ItemStack.EMPTY);
        verify(f, 0, true, true, ResearchGuidanceSnapshot.State.AFFORDABLE);
    }
    @Test void physicalBlueprintOutputUsesThePhysicalCurrencyBalance() {
        var f = new Fixture(); f.direct = false; f.data.setResearchPoints(100);
        var e = verify(f, 2, true, false, null);
        assertEquals(0, e.pointBalance());
    }
    @Test void failedProgressionPresentationReturnsAnUnavailableNonCommittablePreview() {
        var f = new Fixture(); var e = f.evaluate();
        var preview = ResearchSelectionPreviewFactory.create(e, f.mode, tag -> List.of(),
                () -> { throw new IllegalStateException("injected presentation failure"); });
        assertUnavailable(preview);
    }
    @Test void failedTagPresentationReturnsAnUnavailablePreview() {
        var f = new Fixture(); f.mode = ResearchCostMode.ITEMS_ONLY;
        f.materials = List.of(new BlueprintResearchIngredient(List.of(), Optional.of(id("test:unknown_tag")), 2));
        var preview = ResearchSelectionPreviewFactory.create(f.evaluate(), f.mode,
                tag -> { throw new IllegalStateException("injected tag lookup failure"); },
                () -> ResearchSelectionProgressionPreview.EMPTY);
        assertUnavailable(preview);
    }
    @Test void aLaterSuccessfulRefreshRecoversAfterPresentationFailure() {
        var f = new Fixture(); var e = f.evaluate();
        assertUnavailable(ResearchSelectionPreviewFactory.create(e, f.mode, tag -> List.of(),
                () -> { throw new IllegalStateException("injected one-time failure"); }));
        verify(f, 2, true, false, ResearchGuidanceSnapshot.State.MISSING_POINTS);
    }

    @Test void materialSummaryRemainsConsistentWhenThePacketTruncatesALongRoute() {
        var f = new Fixture(); f.data = new PlayerRecipeData();
        var names = List.of("paper", "gold_ingot", "diamond", "coal", "redstone", "emerald", "quartz", "lapis_lazuli");
        var all = names.stream().map(name -> new BlueprintResearchIngredient(
                List.of(id("minecraft:" + name)), Optional.empty(), 2)).toList();
        f.rootMaterials = all.subList(0, 4); f.materials = all.subList(4, 8);
        for (int i = 0; i < names.size(); i++) f.inventory.set(i,
                new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id("minecraft:" + names.get(i))), 2));
        var e = verify(f, 16, true, false, ResearchGuidanceSnapshot.State.MISSING_POINTS);
        var preview = ResearchSelectionPreviewFactory.create(e, f.mode, tag -> List.of(), () -> ResearchSelectionProgressionPreview.EMPTY);
        assertEquals(BlueprintResearchCost.MAX_INGREDIENT_TYPES, preview.ingredients().size());
        assertEquals(8, preview.ingredientTypeCount());
        var page = ResearchSelectionPreviewFactory.create(e, f.mode, tag -> List.of(),
                () -> ResearchSelectionProgressionPreview.EMPTY, 6);
        assertEquals(6, page.ingredientOffset()); assertEquals(2, page.ingredients().size());
        assertEquals(preview.researchable(), page.researchable());
        assertEquals(preview.ingredientsSatisfied(), page.ingredientsSatisfied());
        assertEquals(preview.routeFingerprint(), page.routeFingerprint());
        var combined = new ArrayList<>(preview.ingredients()); combined.addAll(page.ingredients());
        assertEquals(e.requirements().stream().map(ResearchIngredientPlanner.Requirement::items).toList(),
                combined.stream().map(ResearchSelectionPreview.IngredientPreview::items).toList());
        for (int i = 0; i < combined.size(); i++) assertEquals(e.allocation().allocatedForIngredient(i),
                combined.get(i).inventoryAvailable());
        assertTrue(preview.acceptsMaterialPage(TARGET, 0, 6));
        assertTrue(page.acceptsMaterialPage(TARGET, 6, 0));
        assertFalse(page.acceptsMaterialPage(TARGET, 0, 6));
        assertFalse(page.acceptsMaterialPage(ROOT, 6, 0));
        assertFalse(page.acceptsMaterialPage(TARGET, 6, 12));
        assertFalse(page.acceptsMaterialPage(TARGET, 6, Integer.MIN_VALUE));
        roundTrip(page);
        // A shorter current route clamps the old page rather than leaking old costs.
        f.rootMaterials = List.of(); f.materials = List.of(all.get(0));
        var refreshed = ResearchSelectionPreviewFactory.create(f.evaluate(), f.mode, tag -> List.of(),
                () -> ResearchSelectionProgressionPreview.EMPTY, 6);
        assertEquals(0, refreshed.ingredientOffset()); assertEquals(1, refreshed.ingredientTypeCount());
    }
    @Test void previewAndCombinedPaymentAgreeAcrossOverlappingAlternatives() {
        var currency = ResearchCurrencyPolicy.physical(id("minecraft:iron_ingot"), 1);
        var requirements = List.of(
                new ResearchIngredientPlanner.Requirement(List.of(id("minecraft:iron_ingot"), id("minecraft:gold_ingot")), Optional.empty(), 3),
                new ResearchIngredientPlanner.Requirement(List.of(id("minecraft:gold_ingot"), id("minecraft:paper")), Optional.empty(), 2));
        for (int iron = 0; iron <= 7; iron++) for (int gold = 0; gold <= 5; gold++) for (int paper = 0; paper <= 3; paper++) {
            var inventory = new ArrayList<ItemStack>(Collections.nCopies(36, ItemStack.EMPTY));
            inventory.set(0, new ItemStack(Items.IRON_INGOT, iron));
            inventory.set(1, new ItemStack(Items.GOLD_INGOT, gold));
            inventory.set(2, new ItemStack(Items.PAPER, paper));
            var allocation = ResearchCurrencyPayments.previewMaterials(currency, inventory, 3, requirements).orElseThrow();
            boolean payable = ResearchCurrencyPayments.plan(currency, inventory, new PlayerRecipeData(), 3, requirements).isPresent();
            assertEquals(payable, iron >= 3 && allocation.complete(), "iron=" + iron + ", gold=" + gold + ", paper=" + paper);
        }
    }
    @Test void unknownCurrencyCannotProduceAnAffordableEvaluation() {
        assertTrue(ResearchCurrencyPayments.previewMaterials(
                ResearchCurrencyPolicy.physical(id("missing:currency"), 1),
                new Fixture().inventory, 0, List.of()).isEmpty());
    }

    private static Fixture overlap() {
        var f = new Fixture(); f.inventory.set(0, ItemStack.EMPTY);
        f.materials = List.of(new BlueprintResearchIngredient(List.of(id("minecraft:iron_ingot")), Optional.empty(), 4));
        return f;
    }
    private static void assertUnavailable(ResearchSelectionPreview preview) {
        assertEquals(Optional.of(TARGET), preview.blueprintId());
        assertEquals(ResearchSelectionPreview.PathPlanningState.TECH_TREE_UNAVAILABLE, preview.pathPlanningState());
        assertFalse(preview.researchable()); assertTrue(preview.routeFingerprint().isEmpty());
        assertFalse(ResearchPreviewCommitGuard.accepts(true, preview, Optional.empty()));
        assertFalse(ResearchPreviewCommitGuard.accepts(false, preview, Optional.empty()));
        roundTrip(preview);
    }
    private static ResearchRouteEvaluationService.Evaluation verify(Fixture f, int allocated,
            boolean materialsSatisfied, boolean ready, ResearchGuidanceSnapshot.State guidanceState) {
        var before = f.inventory.stream().map(ItemStack::copy).toList();
        var saved = f.data.serializeNBT().copy();
        var e = f.evaluate();
        var preview = ResearchSelectionPreviewFactory.create(e, f.mode, tag -> List.of(),
                () -> ResearchSelectionProgressionPreview.EMPTY);
        assertEquals(ResearchSelectionPreview.PathPlanningState.NONE, preview.pathPlanningState());
        assertEquals(materialsSatisfied, e.ingredientsSatisfied());
        assertEquals(materialsSatisfied, preview.ingredientsSatisfied());
        assertEquals(allocated, e.allocation().totalAllocated());
        assertEquals(ready, e.ready()); assertEquals(ready, preview.researchable());
        assertEquals(ready, e.costBypassed() || ResearchCurrencyPayments.plan(
                f.currency(), f.inventory, f.data, e.pointCost(), e.requirements()).isPresent());
        roundTrip(preview);
        if (f.direct) {
            var guidance = ResearchRouteEvaluationService.guidanceSnapshot(e, f.graph(), f.mode).orElseThrow();
            assertEquals(guidanceState, guidance.state());
            assertEquals(allocated, guidance.allocatedMaterialUnits());
        }
        for (int i = 0; i < before.size(); i++) assertTrue(ItemStack.matches(before.get(i), f.inventory.get(i)));
        assertEquals(saved, f.data.serializeNBT());
        return e;
    }
    private static void roundTrip(ResearchSelectionPreview preview) {
        var original = new FriendlyByteBuf(Unpooled.buffer());
        var encoded = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new SyncResearchBenchPreviewPacket(1, preview).toBytes(original);
            var decoded = new SyncResearchBenchPreviewPacket(original);
            decoded.toBytes(encoded); original.readerIndex(0);
            assertEquals(original, encoded);
        } finally { original.release(); encoded.release(); }
    }
    private static ResourceLocation id(String s) { return new ResourceLocation(s); }
    private static final class Fixture {
        PlayerRecipeData data = new PlayerRecipeData();
        List<ItemStack> inventory = new ArrayList<>(Collections.nCopies(36, ItemStack.EMPTY));
        List<BlueprintResearchIngredient> materials = List.of(new BlueprintResearchIngredient(
                List.of(id("minecraft:paper")), Optional.empty(), 2));
        List<BlueprintResearchIngredient> rootMaterials;
        ResearchCostMode mode = ResearchCostMode.POINTS_AND_ITEMS;
        boolean banked, creative; boolean direct = true;
        Fixture() { data.addBlueprint(ROOT.toString()); inventory.set(0, new ItemStack(Items.PAPER, 4)); }
        ResearchCurrencyPolicy currency() { return banked ? ResearchCurrencyPolicy.banked(100, 1)
                : ResearchCurrencyPolicy.physical(id("minecraft:iron_ingot"), 1); }
        ResearchRouteEvaluationService.Evaluation evaluate() {
            var config = new BlueprintProgressionConfigSnapshot(true, true, true, JournalVisibility.FULL, true,
                    DuplicateBlueprintPolicy.KEEP, false, 100, true, PROFILE,
                    direct ? TreeResearchResultMode.DIRECT_LEARN : TreeResearchResultMode.CREATE_BLUEPRINT,
                    mode, FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY, currency());
            var policies = Map.of(ROOT, policy(ROOT, ResearchRequirements.EMPTY, 4),
                    TARGET, policy(TARGET, ResearchRequirements.fromLegacy(List.of(ROOT)), 3));
            var result = ResearchRouteEvaluationService.evaluate(new ResearchRouteEvaluationService.Request(
                    TARGET, data, policies.get(TARGET), policies::get, k -> false, ResearchPathAuthority.authored(),
                    new ResearchRouteFingerprint.Context(1, 1, 1, config, BlueprintAccessConfigSnapshot.EMPTY),
                    k -> new BlueprintLearningService.LearningTarget(k, id("test:recipe/" + k.getPath())),
                    inventory, creative, direct, true)).orElseThrow();
            assertEquals(BlueprintResearchService.Status.SUCCESS, result.planningStatus());
            assertEquals(direct, result.path().isPresent());
            assertTrue(result.policyEligible());
            return result;
        }
        BlueprintResearchPolicy policy(ResourceLocation target, ResearchRequirements requirements, int points) {
            return new BlueprintResearchPolicy(target, PROFILE, true, false, true, data.hasBlueprint(target.toString()),
                    data.hasDiscoveredBlueprint(target.toString()), data.getResearchPoints(), 100,
                    requirements.satisfiedBy(k -> data.hasBlueprint(k.toString())), true, true, JournalVisibility.FULL,
                    true, true, false, 1, new BlueprintResearchCost(mode.pointsEnabled() ? points : 0,
                    mode.itemsEnabled() ? target.equals(ROOT) && rootMaterials != null ? rootMaterials : materials : List.of()), false, requirements,
                    requirements.conservativeAlternatives(), true, true, Optional.empty(), BlueprintResearchTarget.MatchSpecificity.NONE);
        }
        ResearchTreeGraph graph() {
            return new ResearchTreeGraph(List.of(node(0, ROOT, data.hasBlueprint(ROOT.toString())), node(1, TARGET, false)),
                    List.of(new ResearchTreeGraph.Edge(ROOT, TARGET)));
        }
        ResearchTreeGraph.Node node(int ordinal, ResourceLocation id, boolean learned) {
            return new ResearchTreeGraph.Node(ordinal, id, "name." + id.getPath(), "gun", id("test:slot/" + id.getPath()),
                    JournalVisibility.FULL, learned, true, !learned, 1, 1, id.equals(TARGET) ? 1 : 0, 0,
                    learned ? ResearchTreeGraph.Availability.LEARNED : ResearchTreeGraph.Availability.AVAILABLE);
        }
    }
}
