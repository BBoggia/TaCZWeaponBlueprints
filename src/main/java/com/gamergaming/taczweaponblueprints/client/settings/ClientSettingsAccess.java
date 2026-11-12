package com.gamergaming.taczweaponblueprints.client.settings;

import java.util.*;
import com.gamergaming.taczweaponblueprints.network.*;
import com.gamergaming.taczweaponblueprints.settings.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client transport only. Server edits use authoritative replies; Fzzy retains its normal synchronized-config cache. */
@EventBusSubscriber(modid = "taczweaponblueprints", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientSettingsAccess {
    private static ClientPacketListener connection;
    private static SettingsEditSession draft;
    private static SettingsSnapshot latest;
    private static UUID request;
    private static long deadline;
    private static SettingsTransaction.Status status;
    private static boolean applyPending;
    private static boolean refreshing, needsReview;
    private ClientSettingsAccess() {}
    /** Explicit new read discards only after the caller has handled any existing proposal. */
    public static void open() {
        if (pending() || draft != null && draft.dirty()) throw new IllegalStateException("Handle pending changes before opening another session");
        clear(); connection = Minecraft.getInstance().getConnection();
        if (connection == null) { status = SettingsTransaction.Status.UNAVAILABLE; return; }
        send(new SettingsRequestPacket(UUID.randomUUID(), SettingsRequestPacket.Action.OPEN, null));
    }
    public static void apply() {
        if (pending() || needsReview || draft == null || status != SettingsTransaction.Status.READY || connection != Minecraft.getInstance().getConnection())
            throw new IllegalStateException("No editable server session");
        try {
            var packet = new SettingsRequestPacket(UUID.randomUUID(), SettingsRequestPacket.Action.APPLY, draft.plan());
            draft.freeze(); applyPending = true; send(packet);
        } catch (IllegalArgumentException error) { status = SettingsTransaction.Status.TOO_LARGE; }
    }
    /** Fetch a fresh authoritative baseline while retaining the exact proposal for comparison. */
    public static void refresh() {
        if (pending() || connection == null || connection != Minecraft.getInstance().getConnection())
            throw new IllegalStateException("No connected settings session");
        if (draft != null) draft.freeze();
        refreshing = true;
        send(new SettingsRequestPacket(UUID.randomUUID(), SettingsRequestPacket.Action.OPEN, null));
    }
    public static boolean needsReview() { return needsReview; }
    /** The caller must show the differences and obtain the user's explicit review before this handoff. */
    public static void acceptReviewedChanges() {
        if (!needsReview || pending() || status != SettingsTransaction.Status.READY || latest == null || draft == null)
            throw new IllegalStateException("No refreshed proposal to review");
        var reviewed = draft.reviewedAgainst(latest);
        draft.discard(); draft = reviewed; needsReview = false;
    }
    private static void send(SettingsRequestPacket packet) {
        request = packet.request(); deadline = System.nanoTime() + 15_000_000_000L;
        PacketDistributor.sendToServer(packet);
    }
    public static void accept(SettingsReplyPacket reply) {
        if (request == null || !request.equals(reply.request()) || connection != Minecraft.getInstance().getConnection()) return;
        request = null; status = reply.status(); latest = reply.snapshot();
        if (latest != null && (status == SettingsTransaction.Status.READY || status == SettingsTransaction.Status.APPLIED || status == SettingsTransaction.Status.NO_CHANGES)) {
            if (refreshing && draft != null && draft.dirty()) needsReview = true;
            else {
                if (draft != null) draft.discard();
                draft = new SettingsEditSession(latest); needsReview = false;
            }
        }
        applyPending = false; refreshing = false;
    }
    /** UI acknowledges the successful result before allowing another apply. */
    public static void acknowledgeApplied() {
        if (status == SettingsTransaction.Status.APPLIED || status == SettingsTransaction.Status.NO_CHANGES) status = SettingsTransaction.Status.READY;
    }
    public static Optional<SettingsEditSession> draft() { return Optional.ofNullable(draft); }
    public static Optional<SettingsSnapshot> latest() { return Optional.ofNullable(latest); }
    public static Optional<SettingsTransaction.Status> status() { return Optional.ofNullable(status); }
    public static boolean pending() { return request != null; }
    /** Discard never sends an apply. An in-flight Apply has an unknown outcome until its reply/reconnect. */
    public static void discard() {
        if (applyPending) throw new IllegalStateException("Cannot cancel an apply already submitted to the server");
        clear();
    }
    private static void clear() {
        if (draft != null) draft.discard();
        draft = null; latest = null; connection = null; request = null; status = null; applyPending = false; refreshing = false; needsReview = false;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (connection != null && connection != Minecraft.getInstance().getConnection()) { clear(); return; }
        if (request != null && System.nanoTime() > deadline) {
            request = null; applyPending = false; status = SettingsTransaction.Status.EXPIRED;
            // Retain the proposal for review, but require a fresh read. Never retry a timed-out apply.
        }
    }
}
