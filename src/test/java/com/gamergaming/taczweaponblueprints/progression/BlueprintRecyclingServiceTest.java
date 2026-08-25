package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.progression.BlueprintRecyclingService.RecyclingInput;
import com.gamergaming.taczweaponblueprints.progression.BlueprintRecyclingService.Status;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class BlueprintRecyclingServiceTest {
    private static final ResourceLocation BLUEPRINT = id("test:rifle");
    private static final ResourceLocation PROFILE = id("test:profile");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void consumesExactlyOneDuplicateAndCreditsTheCompleteAward() {
        PlayerRecipeData data = learnedData(8);
        TestInput input = blueprintInput(BLUEPRINT, 2);

        BlueprintRecyclingService.Result result = recycle(input, data, policy(data, true, true, false, 2, 10));

        assertTrue(result.successful());
        assertEquals(Status.SUCCESS, result.status());
        assertEquals(BLUEPRINT, result.blueprintId().orElseThrow());
        assertEquals(2, result.awardedPoints());
        assertEquals(10, result.newBalance());
        assertEquals(1, input.count());
        assertEquals(10, data.getResearchPoints());
        assertTrue(data.hasBlueprint(BLUEPRINT.toString()));
        assertTrue(data.hasDiscoveredBlueprint(BLUEPRINT.toString()));
    }

    @Test
    void permittedUnlearnedRecyclingDoesNotInventLearningOrRemoveDiscovery() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.discoverBlueprint(BLUEPRINT.toString());
        TestInput input = blueprintInput(BLUEPRINT, 1);

        BlueprintRecyclingService.Result result = recycle(input, data, policy(data, true, true, true, 3, 10));

        assertTrue(result.successful());
        assertEquals(0, input.count());
        assertEquals(3, data.getResearchPoints());
        assertFalse(data.hasBlueprint(BLUEPRINT.toString()));
        assertTrue(data.hasDiscoveredBlueprint(BLUEPRINT.toString()));
    }

    @Test
    void validatesThePhysicalStackBeforeResolvingPolicy() {
        PlayerRecipeData data = learnedData(0);
        AtomicInteger resolutions = new AtomicInteger();
        ItemStack nonBlueprint = new ItemStack(Items.PAPER);

        BlueprintRecyclingService.Result wrongItem = BlueprintRecyclingService.recycle(
                nonBlueprint,
                data,
                ignored -> {
                    resolutions.incrementAndGet();
                    return policy(data, true, true, false, 1, 10);
                });
        BlueprintRecyclingService.Result malformed = BlueprintRecyclingService.recycle(
                ItemStack.EMPTY,
                data,
                ignored -> {
                    resolutions.incrementAndGet();
                    return policy(data, true, true, false, 1, 10);
                });

        assertEquals(Status.INVALID_INPUT, wrongItem.status());
        assertEquals(Status.INVALID_INPUT, malformed.status());
        assertEquals(0, resolutions.get());
        assertEquals(1, nonBlueprint.getCount());
        assertEquals(0, data.getResearchPoints());
    }

    @Test
    void everyPolicyFailurePreservesBothStackAndBalance() {
        assertFailure(Status.POLICY_UNAVAILABLE, data -> null);
        assertFailure(Status.POLICY_MISMATCH,
                data -> policy(id("test:other"), data, true, true, false, 1, 10));
        assertFailure(Status.STALE_POLICY,
                data -> policy(BLUEPRINT, 1, true, true, true, false, 1, 10));
        assertFailure(Status.CONTENT_UNAVAILABLE,
                data -> policy(data, false, true, false, 1, 10));
        assertFailure(Status.BLOCKED,
                data -> policy(BLUEPRINT, data.getResearchPoints(), true, true, true, false, 1, 10, true));
        assertFailure(Status.RECYCLING_DISABLED,
                data -> policy(data, true, false, false, 1, 10));
        assertFailure(Status.NO_VALUE,
                data -> policy(data, true, true, false, 0, 10));

        PlayerRecipeData unlearned = new PlayerRecipeData();
        TestInput input = blueprintInput(BLUEPRINT, 1);
        BlueprintRecyclingService.Result duplicateRequired = recycle(
                input,
                unlearned,
                policy(unlearned, true, true, false, 1, 10));
        assertEquals(Status.DUPLICATE_REQUIRED, duplicateRequired.status());
        assertEquals(1, input.count());
        assertEquals(0, unlearned.getResearchPoints());
    }

    @Test
    void pointCapFailureIsAtomicAndExactCapCreditSucceeds() {
        PlayerRecipeData capped = learnedData(9);
        TestInput rejectedInput = blueprintInput(BLUEPRINT, 1);
        BlueprintRecyclingService.Result rejected = recycle(
                rejectedInput,
                capped,
                policy(capped, true, true, false, 2, 10));
        assertEquals(Status.POINT_CAP_REACHED, rejected.status());
        assertEquals(1, rejectedInput.count());
        assertEquals(9, capped.getResearchPoints());

        PlayerRecipeData exact = learnedData(8);
        TestInput acceptedInput = blueprintInput(BLUEPRINT, 1);
        BlueprintRecyclingService.Result accepted = recycle(
                acceptedInput,
                exact,
                policy(exact, true, true, false, 2, 10));
        assertTrue(accepted.successful());
        assertEquals(0, acceptedInput.count());
        assertEquals(10, exact.getResearchPoints());
    }

    @Test
    void resolverExceptionsAndMissingPlayerDataFailClosed() {
        TestInput input = blueprintInput(BLUEPRINT, 1);
        BlueprintRecyclingService.Result missingData = BlueprintRecyclingService.recycle(
                input, null, ignored -> { throw new AssertionError("resolver must not run"); });
        assertEquals(Status.PLAYER_DATA_UNAVAILABLE, missingData.status());

        PlayerRecipeData data = learnedData(4);
        BlueprintRecyclingService.Result resolverFailure = BlueprintRecyclingService.recycle(
                input, data, ignored -> { throw new IllegalStateException("reload race"); });
        assertEquals(Status.POLICY_UNAVAILABLE, resolverFailure.status());
        assertEquals(1, input.count());
        assertEquals(4, data.getResearchPoints());
    }

    @Test
    void blueprintIdsAndResultShapesAreStrictlyValidated() {
        assertEquals(BLUEPRINT, BlueprintItem.parseBlueprintId("test:rifle").orElseThrow());
        assertTrue(BlueprintItem.parseBlueprintId("").isEmpty());
        assertTrue(BlueprintItem.parseBlueprintId("   ").isEmpty());
        assertTrue(BlueprintItem.parseBlueprintId("not a resource location").isEmpty());
        assertTrue(BlueprintItem.parseBlueprintId(null).isEmpty());
        assertTrue(BlueprintItem.parseBlueprintId("a:" + "x".repeat(300)).isEmpty());

        assertThrows(IllegalArgumentException.class, () -> new BlueprintRecyclingService.Result(
                Status.SUCCESS, Optional.empty(), 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BlueprintRecyclingService.Result(
                Status.BLOCKED, Optional.of(BLUEPRINT), 1, 1));

        AtomicInteger resolutions = new AtomicInteger();
        BlueprintRecyclingService.Result empty = BlueprintRecyclingService.recycle(
                blueprintInput(BLUEPRINT, 0),
                learnedData(0),
                ignored -> {
                    resolutions.incrementAndGet();
                    return null;
                });
        assertEquals(Status.INVALID_INPUT, empty.status());
        assertEquals(0, resolutions.get());
    }

    private static void assertFailure(
            Status expected,
            java.util.function.Function<PlayerRecipeData, BlueprintResearchPolicy> policyFactory) {
        PlayerRecipeData data = learnedData(4);
        TestInput input = blueprintInput(BLUEPRINT, 2);
        BlueprintRecyclingService.Result result = recycle(input, data, policyFactory.apply(data));
        assertEquals(expected, result.status());
        assertFalse(result.successful());
        assertEquals(0, result.awardedPoints());
        assertEquals(4, result.newBalance());
        assertEquals(2, input.count());
        assertEquals(4, data.getResearchPoints());
    }

    private static BlueprintRecyclingService.Result recycle(
            RecyclingInput input,
            PlayerRecipeData data,
            BlueprintResearchPolicy policy) {
        return BlueprintRecyclingService.recycle(input, data, ignored -> policy);
    }

    private static PlayerRecipeData learnedData(int points) {
        PlayerRecipeData data = new PlayerRecipeData();
        data.addBlueprint(BLUEPRINT.toString());
        data.setResearchPoints(points);
        return data;
    }

    private static BlueprintResearchPolicy policy(
            PlayerRecipeData data,
            boolean available,
            boolean recyclingEnabled,
            boolean allowUnlearned,
            int value,
            int pointCap) {
        return policy(BLUEPRINT, data.getResearchPoints(), data.hasBlueprint(BLUEPRINT.toString()),
                available, recyclingEnabled, allowUnlearned, value, pointCap);
    }

    private static BlueprintResearchPolicy policy(
            ResourceLocation blueprintId,
            PlayerRecipeData data,
            boolean available,
            boolean recyclingEnabled,
            boolean allowUnlearned,
            int value,
            int pointCap) {
        return policy(blueprintId, data.getResearchPoints(), data.hasBlueprint(BLUEPRINT.toString()),
                available, recyclingEnabled, allowUnlearned, value, pointCap);
    }

    private static BlueprintResearchPolicy policy(
            ResourceLocation blueprintId,
            int points,
            boolean learned,
            boolean available,
            boolean recyclingEnabled,
            boolean allowUnlearned,
            int value,
            int pointCap) {
        return policy(blueprintId, points, learned, available, recyclingEnabled, allowUnlearned, value, pointCap, false);
    }

    private static BlueprintResearchPolicy policy(
            ResourceLocation blueprintId,
            int points,
            boolean learned,
            boolean available,
            boolean recyclingEnabled,
            boolean allowUnlearned,
            int value,
            int pointCap,
            boolean blocked) {
        return new BlueprintResearchPolicy(
                blueprintId,
                PROFILE,
                available,
                blocked,
                true,
                learned,
                true,
                points,
                pointCap,
                true,
                true,
                JournalVisibility.FULL,
                true,
                recyclingEnabled,
                allowUnlearned,
                value,
                new BlueprintResearchCost(8, List.of()),
                false,
                List.of(),
                false,
                Optional.empty(),
                MatchSpecificity.NONE);
    }

    private static TestInput blueprintInput(ResourceLocation id, int count) {
        return new TestInput(Optional.of(id), count);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }

    private static final class TestInput implements RecyclingInput {
        private final Optional<ResourceLocation> blueprintId;
        private int count;

        private TestInput(Optional<ResourceLocation> blueprintId, int count) {
            this.blueprintId = blueprintId;
            this.count = count;
        }

        @Override
        public Optional<ResourceLocation> blueprintId() {
            return blueprintId;
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public void consumeOne() {
            if (count <= 0) {
                throw new IllegalStateException("cannot consume an empty test input");
            }
            count--;
        }
    }
}
