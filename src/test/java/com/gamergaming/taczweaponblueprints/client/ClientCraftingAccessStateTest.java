package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.CraftingEligibilityService;
import com.gamergaming.taczweaponblueprints.progression.CraftingEligibilityService.AccessIdentity;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

import net.minecraft.resources.ResourceLocation;

class ClientCraftingAccessStateTest {
    private static final AccessIdentity IDENTITY = identity(1L);

    @AfterEach
    void clearState() {
        ClientCraftingAccessState.clear();
    }

    @Test
    void oneMenuRequestsOnceAndAcceptsOnlyItsContainer() {
        Object menu = new Object();
        long requestId = ClientCraftingAccessState.beginRequest(4, menu);
        assertTrue(requestId > 0L);
        assertEquals(0L, ClientCraftingAccessState.beginRequest(4, menu));

        ClientCraftingAccessState.accept(
                5,
                requestId,
                1L,
                Optional.of(IDENTITY),
                CraftingEligibilityService.Status.ALLOWED,
                Set.of("test:wrong"));
        assertFalse(ClientCraftingAccessState.snapshot(4).received());

        assertTrue(ClientCraftingAccessState.beginSnapshot(
                4, requestId, 1L, Optional.of(IDENTITY)));
        ClientCraftingAccessState.accept(
                4,
                requestId + 1L,
                1L,
                Optional.of(IDENTITY),
                CraftingEligibilityService.Status.ALLOWED,
                Set.of("test:stale"));
        assertFalse(ClientCraftingAccessState.snapshot(4).received());

        ClientCraftingAccessState.accept(
                4,
                requestId,
                1L,
                Optional.of(IDENTITY),
                CraftingEligibilityService.Status.ALLOWED,
                Set.of("test:allowed"));
        assertTrue(ClientCraftingAccessState.snapshot(4).allows("test:allowed"));
        assertEquals(CraftingEligibilityService.Status.ALLOWED,
                ClientCraftingAccessState.snapshot(4).status());
    }

    @Test
    void reusedContainerIdStartsAFreshMenuSession() {
        Object firstMenu = new Object();
        Object secondMenu = new Object();
        long firstRequest = ClientCraftingAccessState.beginRequest(7, firstMenu);
        assertTrue(firstRequest > 0L);
        assertTrue(ClientCraftingAccessState.beginSnapshot(
                7, firstRequest, 1L, Optional.of(IDENTITY)));
        ClientCraftingAccessState.accept(
                7,
                firstRequest,
                1L,
                Optional.of(IDENTITY),
                CraftingEligibilityService.Status.ALLOWED,
                Set.of("test:first"));

        long secondRequest = ClientCraftingAccessState.beginRequest(7, secondMenu);
        assertTrue(secondRequest > firstRequest);
        ClientCraftingAccessState.accept(
                7,
                firstRequest,
                1L,
                Optional.of(IDENTITY),
                CraftingEligibilityService.Status.ALLOWED,
                Set.of("test:stale"));
        var second = ClientCraftingAccessState.snapshot(7);
        assertFalse(second.received());
        assertFalse(second.allows("test:first"));
    }

    @Test
    void incompleteResponseCanRetryOnlyOnceWithTheOriginalRequestId() {
        Object menu = new Object();
        long requestId = ClientCraftingAccessState.beginRequest(6, menu);
        long afterTimeout = System.nanoTime()
                + ClientCraftingAccessState.REQUEST_RETRY_DELAY_NANOS + 1L;

        assertEquals(requestId, ClientCraftingAccessState.retryRequestIfTimedOut(
                6, menu, afterTimeout));
        assertEquals(0L, ClientCraftingAccessState.retryRequestIfTimedOut(
                6, menu, Long.MAX_VALUE));
        assertEquals(0L, ClientCraftingAccessState.retryRequestIfTimedOut(
                7, menu, Long.MAX_VALUE));
    }

    @Test
    void completedResponseCannotBeRetried() {
        Object menu = new Object();
        long requestId = ClientCraftingAccessState.beginRequest(8, menu);
        assertTrue(ClientCraftingAccessState.beginSnapshot(
                8, requestId, 1L, Optional.of(IDENTITY)));
        assertTrue(ClientCraftingAccessState.accept(
                8,
                requestId,
                1L,
                Optional.of(IDENTITY),
                CraftingEligibilityService.Status.ALLOWED,
                Set.of("test:allowed")));

        assertEquals(0L, ClientCraftingAccessState.retryRequestIfTimedOut(
                8, menu, Long.MAX_VALUE));
    }

    @Test
    void exhaustedRequestCounterFailsClosedInsteadOfWrapping() throws Exception {
        var field = ClientCraftingAccessState.class.getDeclaredField("nextRequestId");
        field.setAccessible(true);
        field.setLong(null, Long.MAX_VALUE);

        Object menu = new Object();
        assertEquals(0L, ClientCraftingAccessState.beginRequest(10, menu));
        assertFalse(ClientCraftingAccessState.snapshot(10, menu).requested());
        assertEquals(0L, ClientCraftingAccessState.snapshot(10, menu).requestId());
    }

    @Test
    void menuAwareSnapshotRejectsAReusedContainerBeforeTheNewRequestBegins() {
        Object firstMenu = new Object();
        Object secondMenu = new Object();
        long requestId = ClientCraftingAccessState.beginRequest(9, firstMenu);
        assertTrue(ClientCraftingAccessState.beginSnapshot(
                9, requestId, 1L, Optional.of(IDENTITY)));
        ClientCraftingAccessState.accept(
                9,
                requestId,
                1L,
                Optional.of(IDENTITY),
                CraftingEligibilityService.Status.ALLOWED,
                Set.of("test:first"));

        var stale = ClientCraftingAccessState.snapshot(9, secondMenu);

        assertFalse(stale.received());
        assertFalse(stale.allows("test:first"));
        assertTrue(ClientCraftingAccessState.snapshot(9, firstMenu).allows("test:first"));
    }

    @Test
    void unavailableSnapshotRetainsTheServerDenialReason() {
        Object menu = new Object();
        long requestId = ClientCraftingAccessState.beginRequest(11, menu);
        assertTrue(ClientCraftingAccessState.beginSnapshot(
                11, requestId, 1L, Optional.of(IDENTITY)));

        ClientCraftingAccessState.accept(
                11,
                requestId,
                1L,
                Optional.of(IDENTITY),
                CraftingEligibilityService.Status.CRAFTING_DISABLED,
                Set.of());

        var snapshot = ClientCraftingAccessState.snapshot(11, menu);
        assertTrue(snapshot.received());
        assertFalse(snapshot.available());
        assertEquals(CraftingEligibilityService.Status.CRAFTING_DISABLED,
                snapshot.status());
    }

    @Test
    void newerSnapshotImmediatelyRevokesTheCompletedAllowList() {
        Object menu = new Object();
        long requestId = ClientCraftingAccessState.beginRequest(13, menu);
        assertTrue(ClientCraftingAccessState.beginSnapshot(
                13, requestId, 1L, Optional.of(IDENTITY)));
        assertTrue(ClientCraftingAccessState.accept(
                13,
                requestId,
                1L,
                Optional.of(IDENTITY),
                CraftingEligibilityService.Status.ALLOWED,
                Set.of("test:old")));
        assertTrue(ClientCraftingAccessState.snapshot(13, menu).allows("test:old"));

        AccessIdentity replacement = identity(2L);
        assertTrue(ClientCraftingAccessState.beginSnapshot(
                13, requestId, 2L, Optional.of(replacement)));

        var pending = ClientCraftingAccessState.snapshot(13, menu);
        assertFalse(pending.received());
        assertFalse(pending.available());
        assertFalse(pending.allows("test:old"));
        assertEquals(2L, pending.snapshotId());
        assertEquals(Optional.of(replacement), pending.accessIdentity());
    }

    @Test
    void completionMustMatchThePendingSnapshotAndIdentity() {
        Object menu = new Object();
        long requestId = ClientCraftingAccessState.beginRequest(15, menu);
        assertTrue(ClientCraftingAccessState.beginSnapshot(
                15, requestId, 4L, Optional.of(IDENTITY)));

        assertFalse(ClientCraftingAccessState.accept(
                15,
                requestId,
                3L,
                Optional.of(IDENTITY),
                CraftingEligibilityService.Status.ALLOWED,
                Set.of("test:stale")));
        assertFalse(ClientCraftingAccessState.accept(
                15,
                requestId,
                4L,
                Optional.of(identity(9L)),
                CraftingEligibilityService.Status.ALLOWED,
                Set.of("test:mixed")));
        assertFalse(ClientCraftingAccessState.snapshot(15, menu).received());
    }

    @Test
    void classicIdentityRepresentsAnExplicitUnrestrictedNativeView() {
        Object menu = new Object();
        long requestId = ClientCraftingAccessState.beginRequest(17, menu);
        AccessIdentity classic = new AccessIdentity(
                2L,
                0L,
                0L,
                0L,
                0L,
                0L,
                new ResourceLocation("test:profile"),
                new ResourceLocation("test:workbench"),
                ResearchWorkbenchTier.TIER_1,
                false,
                false,
                false,
                false,
                false);
        assertTrue(ClientCraftingAccessState.beginSnapshot(
                17, requestId, 1L, Optional.of(classic)));
        assertTrue(ClientCraftingAccessState.accept(
                17,
                requestId,
                1L,
                Optional.of(classic),
                CraftingEligibilityService.Status.ALLOWED,
                Set.of()));

        var snapshot = ClientCraftingAccessState.snapshot(17, menu);
        assertTrue(snapshot.unrestrictedCrafting());
        assertTrue(snapshot.allows("tacz:any_native_recipe"));
        assertTrue(snapshot.receivedForMode(false));
        assertTrue(snapshot.availableForMode(false));
        assertTrue(snapshot.unrestrictedCrafting(false));
        assertTrue(snapshot.allows("tacz:any_native_recipe", false));
        assertFalse(snapshot.receivedForMode(true));
        assertFalse(snapshot.availableForMode(true));
        assertFalse(snapshot.unrestrictedCrafting(true));
        assertFalse(snapshot.allows("tacz:any_native_recipe", true));
    }

    @Test
    void activeIdentityCannotAuthorizeAStaleClassicModeView() {
        Object menu = new Object();
        long requestId = ClientCraftingAccessState.beginRequest(19, menu);
        assertTrue(ClientCraftingAccessState.beginSnapshot(
                19, requestId, 1L, Optional.of(IDENTITY)));
        assertTrue(ClientCraftingAccessState.accept(
                19,
                requestId,
                1L,
                Optional.of(IDENTITY),
                CraftingEligibilityService.Status.ALLOWED,
                Set.of("test:allowed")));

        var snapshot = ClientCraftingAccessState.snapshot(19, menu);
        assertTrue(snapshot.receivedForMode(true));
        assertTrue(snapshot.availableForMode(true));
        assertTrue(snapshot.allows("test:allowed", true));
        assertFalse(snapshot.receivedForMode(false));
        assertFalse(snapshot.availableForMode(false));
        assertFalse(snapshot.allows("test:allowed", false));
    }

    private static AccessIdentity identity(long policyRevision) {
        return new AccessIdentity(
                2L,
                3L,
                4L,
                5L,
                6L,
                policyRevision,
                new ResourceLocation("test:profile"),
                new ResourceLocation("test:workbench"),
                ResearchWorkbenchTier.TIER_2,
                false,
                true,
                false,
                false,
                true);
    }
}
