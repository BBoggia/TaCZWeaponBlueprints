package com.gamergaming.taczweaponblueprints.settings;

import java.util.*;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.network.SettingsReplyPacket;
import com.gamergaming.taczweaponblueprints.network.SettingsRequestPacket;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import me.fzzyhmstrs.fzzy_config.event.api.ServerUpdateContext;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Authoritative adapter, explicitly bound to the connected player object, not a reusable player UUID. */
@EventBusSubscriber(modid = "taczweaponblueprints", bus = EventBusSubscriber.Bus.GAME)
public final class ServerSettingsAccess {
    private record Session(SettingsSnapshot baseline, long expires) {}
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();
    private static final long LIFETIME = 30L * 60L * 1_000_000_000L;
    private ServerSettingsAccess() {}
    public static void handle(ServerPlayer player, SettingsRequestPacket request) {
        if (player == null) return;
        long now = System.nanoTime();
        Long last = LAST_REQUEST.put(player, now);
        if (last != null && now - last < 100_000_000L) {
            reply(player, request.request(), SettingsTransaction.Status.UNAVAILABLE, null); return;
        }
        boolean allowed = player.hasPermissions(2);
        SettingsTransaction transaction = transaction(player);
        SettingsTransaction.Status status = SettingsTransaction.Status.READY;
        try {
            if (request.action() == SettingsRequestPacket.Action.APPLY) {
                // Consume the session even on failure. A delayed/retried packet can never replay side effects.
                Session session = SESSIONS.remove(player);
                if (!allowed) status = SettingsTransaction.Status.DENIED;
                else if (session == null || now > session.expires()) status = SettingsTransaction.Status.EXPIRED;
                else status = transaction.apply(session.baseline(), request.plan(), true);
            } else SESSIONS.remove(player);
            SettingsSnapshot snapshot = transaction.capture(UUID.randomUUID(), allowed, profile());
            SettingsReplyPacket packet = new SettingsReplyPacket(request.request(), status, snapshot);
            if (!packet.withinLimit()) {
                reply(player, request.request(), committed(status) ? SettingsTransaction.Status.APPLIED_REFRESH_FAILED : SettingsTransaction.Status.TOO_LARGE, null);
                return;
            }
            // Failed apply replies are informational. The client explicitly opens again before retrying.
            if (request.action() == SettingsRequestPacket.Action.OPEN || status == SettingsTransaction.Status.APPLIED || status == SettingsTransaction.Status.NO_CHANGES)
                SESSIONS.put(player, new Session(snapshot, now + LIFETIME));
            PacketDistributor.sendToPlayer(player, packet);
        } catch (RuntimeException | java.io.IOException error) {
            SESSIONS.remove(player);
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.LOGGER.warn("Unable to capture server settings", error);
            // If persistence succeeded, retain that outcome even if the subsequent read failed.
            reply(player, request.request(), committed(status) ? SettingsTransaction.Status.APPLIED_REFRESH_FAILED : SettingsTransaction.Status.UNAVAILABLE, null);
        }
    }
    private static boolean committed(SettingsTransaction.Status status) {
        return status == SettingsTransaction.Status.APPLIED || status == SettingsTransaction.Status.APPLIED_REFRESH_FAILED;
    }
    private static void reply(ServerPlayer player, UUID request, SettingsTransaction.Status status, SettingsSnapshot snapshot) {
        PacketDistributor.sendToPlayer(player, new SettingsReplyPacket(request, status, snapshot));
    }
    private static SettingsTransaction transaction(ServerPlayer player) {
        var config = ModConfigs.BLUEPRINT;
        return new SettingsTransaction(config, SettingsTransaction.fzzyStore(config), ServerSettingsAccess::policyRevision, () -> {
            config.onUpdateServer(new ServerUpdateContext(player.server, player));
            SettingsSynchronization.publish(config, player.server);
        });
    }
    private static String policyRevision() {
        return BlueprintDataManager.SERVER.catalogPublication().revision() + ":" + BlueprintResearchDataManager.INSTANCE.revision()
                + ":" + BlueprintProgressionPolicyManager.INSTANCE.publication().identity();
    }
    private static SettingsPresentation.Profile profile() {
        String id = ModConfigs.BLUEPRINT.activeResearchProfile.getUnconditional();
        if (ProgressionPolicyAccessService.acquire(ProgressionPolicyAccessService.Mode.CURRENT_ONLY).isEmpty())
            return SettingsPresentation.Profile.unavailable(id);
        var profile = BlueprintResearchDataManager.INSTANCE.snapshot().profiles().get(net.minecraft.resources.ResourceLocation.parse(id));
        if (profile == null) return SettingsPresentation.Profile.unavailable(id);
        return new SettingsPresentation.Profile(id, true, profile.domainPolicy(Domain.WEAPONS), profile.domainPolicy(Domain.AMMO), profile.domainPolicy(Domain.ATTACHMENTS));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { SESSIONS.remove(player); LAST_REQUEST.remove(player); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.clear(); LAST_REQUEST.clear(); }
}
