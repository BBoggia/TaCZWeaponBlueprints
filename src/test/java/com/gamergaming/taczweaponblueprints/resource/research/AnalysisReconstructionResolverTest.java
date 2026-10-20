package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPolicyTest.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.capabilities.*;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentAnalysisService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AnalysisReconstructionResolverTest {
    static final ResourceLocation TARGET = id("test:one");
    @BeforeAll static void bootstrap() { com.gamergaming.taczweaponblueprints.testing.AnalysisTestItems.initialize(); }
    @ParameterizedTest @EnumSource(AnalysisRewardClass.class)
    void legacyWeaponModeReconstructsDataAndExistingFragmentsWithoutPromotion(AnalysisRewardClass reward) {
        var source = context(policy("note"), Map.of(TARGET, 1));
        var resolved = AnalysisReconstructionResolver.resolve(source, PROFILE, TARGET, AnalyzerTier.TIER_1);
        assertTrue(resolved.available()); assertEquals("shared_fallback", resolved.source());
        var data = new PlayerRecipeData(); data.addBlueprint(TARGET.toString()); data.setResearchPoints(137);
        data.applyAnalysisFragmentMutation(new AnalysisFragmentMutation(PlayerProgressValueMutation.Operation.COMMIT,
                TARGET.toString(), AnalysisFragmentCounts.EMPTY, AnalysisFragmentCounts.EMPTY.with(reward, 3)));
        var tx = new Transaction(TARGET, reward, 4);
        var evaluation = AnalysisFragmentReconstructionService.evaluate(tx.input, tx.output, data, resolved.policy());
        assertTrue(evaluation.ready()); assertTrue(evaluation.createsBlueprint());
        var result = AnalysisFragmentReconstructionService.commit(evaluation, resolved.policy(), data, tx);
        assertTrue(result.successful()); assertEquals(2, data.getArchivedAnalysisFragments().get(TARGET.toString()).get(reward));
        assertEquals(reward.restrict(true, PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES).provenance(BlueprintProvenance.Source.FRAGMENT_RECONSTRUCTION),
                BlueprintItem.getProvenance(tx.output).orElseThrow());
        assertEquals(137, data.getResearchPoints()); assertTrue(data.getArchivedBlueprintFragments().isEmpty());
    }
    @Test void targetThenProfileThenLegacyThenFallbackAndSelectedDisableWins() {
        var target = definition(Optional.empty(), Optional.of(TARGET), true, 7);
        var profile = definition(Optional.of(PROFILE), Optional.empty(), true, 9);
        var definitions = Map.of(SHARED, shared(), id("test:profile"), profile, id("test:target"), target);
        var source = context(policy("note"), Map.of(TARGET, 1), definitions, true);
        assertEquals(7, resolve(source).policy().threshold()); assertEquals(Optional.of(id("test:target")), resolve(source).authority());
        source = context(policy("note"), Map.of(TARGET, 1), Map.of(SHARED, shared(), id("test:profile"), profile), true);
        assertEquals(9, resolve(source).policy().threshold());
        source = context(policy("note"), Map.of(TARGET, 1), Map.of(SHARED, definition(Optional.empty(), Optional.empty(), true, 99)), true);
        assertEquals(5, resolve(source).policy().threshold()); assertEquals("legacy_analyzer", resolve(source).source());
        var disabled = definition(Optional.empty(), Optional.of(TARGET), false, 7);
        source = context(policy("note"), Map.of(TARGET, 1), Map.of(SHARED, shared(), id("test:disable"), disabled), true);
        assertEquals(AnalysisReconstructionResolver.Status.DISABLED, resolve(source).status());
        assertEquals(ResearchDataPoolResolver.Status.EMPTY_POOL, pool(source, EMPTY_PLAYER).status());
        assertEquals(AnalyzerPolicyQuote.Status.DISABLED, BlueprintAnalyzerPolicyResolver.resolve(source, PROFILE, TARGET, AnalyzerTier.TIER_1,
                BlueprintAnalyzerPolicy.Origin.UNKNOWN, FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY).status());
    }
    @Test void newAuthorityUpdatesWeaponFragmentThresholdWithoutChangingOdds() {
        var baseline = context(policy("note"), Map.of(TARGET, 1), Map.of(SHARED, shared()), true);
        var source = context(policy("note"), Map.of(TARGET, 1), Map.of(SHARED, shared(), id("test:target"), definition(Optional.empty(), Optional.of(TARGET), true, 17)), true);
        var before = BlueprintAnalyzerPolicyResolver.resolve(baseline, PROFILE, TARGET, AnalyzerTier.TIER_1, BlueprintAnalyzerPolicy.Origin.UNKNOWN, FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY);
        var after = BlueprintAnalyzerPolicyResolver.resolve(source, PROFILE, TARGET, AnalyzerTier.TIER_1, BlueprintAnalyzerPolicy.Origin.UNKNOWN, FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY);
        assertEquals(before.outcomes(), after.outcomes()); assertEquals(before.attempt(), after.attempt()); assertEquals(17, after.fragmentThreshold());
    }
    @Test void thresholdReloadAndDisablePreserveArchivesAndInvalidateOldQuotes() {
        var source = context(policy("note"), Map.of(TARGET, 1)); var old = resolve(source).policy();
        var data = new PlayerRecipeData(); var tx = new Transaction(TARGET, AnalysisRewardClass.PROTECTED_PREREQUISITES, 6);
        var quote = AnalysisFragmentReconstructionService.evaluate(tx.input, tx.output, data, old);
        var changed = new AnalysisFragmentReconstructionService.Policy(TARGET, true, 10, 1000, true, old.learning(), 8);
        assertEquals(AnalysisFragmentReconstructionService.Status.STALE, AnalysisFragmentReconstructionService.commit(quote, changed, data, tx).status());
        assertEquals(6, tx.input.getCount()); assertTrue(data.getArchivedAnalysisFragments().isEmpty());
        var partial = AnalysisFragmentReconstructionService.evaluate(tx.input, tx.output, data, changed);
        assertTrue(AnalysisFragmentReconstructionService.commit(partial, changed, data, tx).successful());
        assertEquals(6, data.getArchivedAnalysisFragments().get(TARGET.toString()).protectedPrerequisites());
        var disabled = new AnalysisFragmentReconstructionService.Policy(TARGET, false, 10, 1000, true, old.learning(), 9);
        tx.input = fragment(TARGET, AnalysisRewardClass.PROTECTED_PREREQUISITES, 1);
        assertEquals(AnalysisFragmentReconstructionService.Status.DISABLED, AnalysisFragmentReconstructionService.evaluate(tx.input, tx.output, data, disabled).status());
        assertEquals(6, data.getArchivedAnalysisFragments().get(TARGET.toString()).protectedPrerequisites());
    }
    static AnalysisReconstructionResolver.Resolution resolve(BlueprintAnalyzerPolicyResolver.Context context) {
        return AnalysisReconstructionResolver.resolve(context, PROFILE, TARGET, AnalyzerTier.TIER_1);
    }
    static AnalysisReconstructionDefinition definition(Optional<ResourceLocation> profile, Optional<ResourceLocation> target, boolean enabled, int threshold) {
        return new AnalysisReconstructionDefinition(1, profile, target, enabled,
                new BlueprintAnalyzerPolicy.Reconstruction(Map.of(AnalyzerTier.TIER_1, threshold, AnalyzerTier.TIER_2, threshold, AnalyzerTier.TIER_3, threshold), 1000),
                true, PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES);
    }
    static ItemStack fragment(ResourceLocation target, AnalysisRewardClass reward, int count) {
        var stack = BlueprintFragmentItem.createAnalysis(com.gamergaming.taczweaponblueprints.testing.AnalysisTestItems.fragment, target, reward);
        stack.setCount(count); return stack;
    }
    static class Transaction implements BlueprintFragmentAnalysisService.WorkstationTransaction {
        ItemStack input, output = ItemStack.EMPTY;
        Transaction(ResourceLocation target, AnalysisRewardClass reward, int count) { input = fragment(target, reward, count); }
        public ItemStack physicalInput() { return input; }
        public ItemStack outputStack() { return output; }
        public ItemStack createOutput(ResourceLocation target, BlueprintProvenance provenance) {
            var stack = new ItemStack(com.gamergaming.taczweaponblueprints.testing.AnalysisTestItems.blueprint);
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, customTag -> customTag.putString("bpId", target.toString())); net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, customTag -> customTag.put(BlueprintProvenance.TAG_KEY, provenance.toTag())); return stack;
        }
        public boolean consumePhysical(ItemStack expected, int count) { if (!ItemStack.matches(input, expected)) return false; input.shrink(count); return true; }
        public boolean placeOutput(ItemStack stack, ItemStack expected) { if (!ItemStack.matches(output, expected)) return false; output = stack.copy(); return true; }
        public boolean restore(ItemStack before, ItemStack oldOutput) { input = before.copy(); output = oldOutput.copy(); return true; }
    }
}
