package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.item.BlueprintProvenance;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintReverseEngineeringPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class BlueprintReverseEngineeringEvaluatorTest {
    private static final ResourceLocation PROFILE = id("test:profile");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void resolvesAddonAmmoAndUsesCanonicalBatchWithoutMutatingState() {
        ResourceLocation ammoId = id("addon_pack:experimental_ammo");
        ItemStack ammo = new ItemStack(Items.PAPER, 12);
        ItemStack before = ammo.copy();
        PlayerRecipeData playerData = new PlayerRecipeData();
        playerData.setResearchPoints(41);
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                ammoId,
                data(ammoId, BlueprintKind.AMMO, "ammo", 12));

        BlueprintReverseEngineeringEvaluator.Evaluation evaluation =
                BlueprintReverseEngineeringEvaluator.evaluate(
                        ammo,
                        snapshot(BlueprintReverseEngineeringPolicy.DEFAULT),
                        catalog,
                        PROFILE,
                        playerData,
                        ignored -> false,
                        ignored -> false,
                        inspected -> {
                            inspected.getOrCreateTag().putBoolean("inspection_only", true);
                            return identity(ammoId, BlueprintKind.AMMO, false, false, false);
                        });

        assertTrue(evaluation.ready());
        assertEquals(ammoId, evaluation.physical().blueprintId().orElseThrow());
        assertEquals(12, evaluation.requiredInputCount());
        assertTrue(ItemStack.matches(before, ammo));
        assertEquals(41, playerData.getResearchPoints());
        assertTrue(playerData.getLearnedBlueprints().isEmpty());
        assertTrue(playerData.getDiscoveredBlueprints().isEmpty());
    }

    @Test
    void resolvesAttachmentIdentityAndHonorsBoundedInputOverride() {
        ResourceLocation attachmentId = id("thirdparty:optic_delta");
        ItemStack attachment = new ItemStack(Items.IRON_INGOT, 3);
        BlueprintReverseEngineeringPolicy policy = new BlueprintReverseEngineeringPolicy(
                true,
                Optional.of(3),
                new BlueprintResearchCost(4, List.of()),
                false,
                true,
                PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES,
                false,
                false);
        PlayerRecipeData playerData = new PlayerRecipeData();
        playerData.setResearchPoints(10);

        BlueprintReverseEngineeringEvaluator.Evaluation evaluation =
                BlueprintReverseEngineeringEvaluator.evaluate(
                        attachment,
                        snapshot(policy),
                        Map.of(attachmentId, data(
                                attachmentId,
                                BlueprintKind.ATTACHMENT,
                                "scope",
                                1)),
                        PROFILE,
                        playerData,
                        ignored -> false,
                        ignored -> false,
                        ignored -> identity(
                                attachmentId,
                                BlueprintKind.ATTACHMENT,
                                false,
                                false,
                                false));

        assertTrue(evaluation.ready());
        assertEquals(3, evaluation.requiredInputCount());
        assertEquals(4, evaluation.reversePolicy().orElseThrow().cost().points());
        assertEquals(
                PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES,
                evaluation.reversePolicy().orElseThrow().physicalBlueprintLearningMode());
    }

    @Test
    void rejectsLoadedGunBeforeAnyTransactionMutation() {
        ResourceLocation gunId = id("tacz:test_gun");
        ItemStack gunStack = new ItemStack(Items.IRON_INGOT);
        ItemStack before = gunStack.copy();

        BlueprintReverseEngineeringEvaluator.Evaluation evaluation =
                BlueprintReverseEngineeringEvaluator.evaluate(
                        gunStack,
                        snapshot(BlueprintReverseEngineeringPolicy.DEFAULT),
                        Map.of(gunId, data(gunId, BlueprintKind.GUN, "rifle", 1)),
                        PROFILE,
                        new PlayerRecipeData(),
                        ignored -> false,
                        ignored -> false,
                        ignored -> identity(gunId, BlueprintKind.GUN, true, false, false));

        assertEquals(BlueprintReverseEngineeringEvaluator.Status.LOADED_GUN, evaluation.status());
        assertTrue(ItemStack.matches(before, gunStack));
    }

    @Test
    void statusPrecedenceSeparatesBlockedExemptKnownAndCountFailures() {
        ResourceLocation ammoId = id("test:ammo");
        ItemStack ammo = new ItemStack(Items.PAPER, 2);
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                ammoId,
                data(ammoId, BlueprintKind.AMMO, "ammo", 8));
        BlueprintResearchSnapshot snapshot = snapshot(BlueprintReverseEngineeringPolicy.DEFAULT);
        PlayerRecipeData playerData = new PlayerRecipeData();

        assertEquals(
                BlueprintReverseEngineeringEvaluator.Status.BLOCKED,
                evaluate(
                        ammo,
                        snapshot,
                        catalog,
                        playerData,
                        ignored -> true,
                        ignored -> false,
                        ammoId,
                        BlueprintKind.AMMO).status());
        assertEquals(
                BlueprintReverseEngineeringEvaluator.Status.PROGRESSION_EXEMPT,
                evaluate(
                        ammo,
                        snapshot,
                        catalog,
                        playerData,
                        ignored -> false,
                        ignored -> true,
                        ammoId,
                        BlueprintKind.AMMO).status());

        playerData.addBlueprint(ammoId.toString());
        assertEquals(
                BlueprintReverseEngineeringEvaluator.Status.ALREADY_KNOWN,
                evaluate(
                        ammo,
                        snapshot,
                        catalog,
                        playerData,
                        ignored -> false,
                        ignored -> false,
                        ammoId,
                        BlueprintKind.AMMO).status());
        PlayerRecipeData unknown = new PlayerRecipeData();
        assertEquals(
                BlueprintReverseEngineeringEvaluator.Status.INSUFFICIENT_INPUT_COUNT,
                evaluate(
                        ammo,
                        snapshot,
                        catalog,
                        unknown,
                        ignored -> false,
                        ignored -> false,
                        ammoId,
                        BlueprintKind.AMMO).status());
    }

    @Test
    void unsafeKnownItemToRecyclableBlueprintEconomyFailsClosed() {
        BlueprintReverseEngineeringPolicy unsafe = new BlueprintReverseEngineeringPolicy(
                true,
                Optional.empty(),
                new BlueprintResearchCost(0, List.of()),
                true,
                true,
                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                true,
                false);

        assertThrows(IllegalArgumentException.class, () -> snapshot(unsafe));
    }

    @Test
    void provenanceIsAdditiveAndMalformedProtectedTagsFailClosed() {
        CompoundTag legacy = new CompoundTag();
        assertTrue(BlueprintProvenance.allowsRecycling(legacy));

        BlueprintProvenance provenance = BlueprintProvenance.reverseEngineered(
                false,
                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES);
        CompoundTag protectedBlueprint = legacy.copy();
        protectedBlueprint.put(BlueprintProvenance.TAG_KEY, provenance.toTag());
        assertFalse(BlueprintProvenance.allowsRecycling(protectedBlueprint));
        assertEquals(provenance, BlueprintProvenance.fromTag(protectedBlueprint).orElseThrow());

        CompoundTag malformed = legacy.copy();
        malformed.putString(BlueprintProvenance.TAG_KEY, "invalid");
        assertFalse(BlueprintProvenance.allowsRecycling(malformed));
    }

    private static BlueprintReverseEngineeringEvaluator.Evaluation evaluate(
            ItemStack stack,
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            PlayerRecipeData data,
            java.util.function.Predicate<String> blocked,
            java.util.function.Predicate<ResourceLocation> exempt,
            ResourceLocation blueprintId,
            BlueprintKind kind) {
        return BlueprintReverseEngineeringEvaluator.evaluate(
                stack,
                snapshot,
                catalog,
                PROFILE,
                data,
                blocked,
                exempt,
                ignored -> identity(blueprintId, kind, false, false, false));
    }

    private static BlueprintResearchSnapshot snapshot(BlueprintReverseEngineeringPolicy reverse) {
        BlueprintResearchProfile profile = new BlueprintResearchProfile(
                1,
                true,
                JournalVisibility.FULL,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(8, List.of()),
                false,
                false,
                true,
                List.of(),
                Map.of(),
                Optional.empty(),
                reverse);
        return BlueprintResearchSnapshot.create(Map.of(), Map.of(PROFILE, profile), Map.of());
    }

    private static BlueprintData data(
            ResourceLocation id,
            BlueprintKind kind,
            String itemType,
            int outputCount) {
        return new BlueprintData(
                id.toString(),
                "item." + id.getNamespace() + "." + id.getPath(),
                "tooltip.test",
                new ResourceLocation(id.getNamespace(), "recipe/" + id.getPath()),
                null,
                itemType,
                id("tacz:slot"),
                kind,
                outputCount);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }

    private static PhysicalItemBlueprintResolver.InspectedIdentity identity(
            ResourceLocation id,
            BlueprintKind kind,
            boolean loaded,
            boolean attachments,
            boolean modified) {
        return new PhysicalItemBlueprintResolver.InspectedIdentity(
                id,
                kind,
                loaded,
                attachments,
                modified);
    }
}
