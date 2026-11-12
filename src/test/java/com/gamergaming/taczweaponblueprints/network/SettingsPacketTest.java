package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.settings.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

class SettingsPacketTest {
    private RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY); }
    @Test void requestsAndAuthoritativeRepliesRetainCorrelationAndValues() {
        var session = UUID.randomUUID();
        var plan = new SettingsChangePlan(session, "abc", Map.of("enableResearch", SettingsValues.bool(false),
                "exactCraftingOverrides", SettingsValues.canonical("value = { \"missing:gun\" = \"DISABLED\" }")));
        var b = buffer();
        try {
            for (var packet : List.of(new SettingsRequestPacket(UUID.randomUUID(), SettingsRequestPacket.Action.OPEN, null),
                    new SettingsRequestPacket(UUID.randomUUID(), SettingsRequestPacket.Action.APPLY, plan))) {
                SettingsRequestPacket.STREAM_CODEC.encode(b, packet);
                assertEquals(packet, SettingsRequestPacket.STREAM_CODEC.decode(b)); assertEquals(0, b.readableBytes());
            }
            var snapshot = new SettingsSnapshot(session, "abc", true, FzzySettingsAdapter.capture(new BlueprintConfig()),
                    SettingsPresentation.Profile.unavailable("example:missing"));
            for (var status : SettingsTransaction.Status.values()) {
                var reply = new SettingsReplyPacket(UUID.randomUUID(), status, snapshot);
                SettingsReplyPacket.STREAM_CODEC.encode(b, reply);
                assertEquals(reply, SettingsReplyPacket.STREAM_CODEC.decode(b)); assertEquals(0, b.readableBytes());
            }
            var empty = new SettingsReplyPacket(UUID.randomUUID(), SettingsTransaction.Status.UNAVAILABLE, null);
            SettingsReplyPacket.STREAM_CODEC.encode(b, empty); assertEquals(empty, SettingsReplyPacket.STREAM_CODEC.decode(b));
        } finally { b.release(); }
    }
    @Test void duplicateFieldsAndExcessiveCountsAreRejected() {
        for (int count : List.of(-1, 129, 2)) {
            var b = buffer();
            try {
                b.writeUUID(UUID.randomUUID()); b.writeEnum(SettingsRequestPacket.Action.APPLY);
                b.writeUUID(UUID.randomUUID()); b.writeUtf("abc"); b.writeVarInt(count);
                if (count == 2) for (int i = 0; i < 2; i++) { b.writeUtf("enableResearch"); b.writeUtf("value = false"); }
                assertThrows(IllegalArgumentException.class, () -> SettingsRequestPacket.STREAM_CODEC.decode(b));
            } finally { b.release(); }
        }
    }
    @Test void byteBudgetsAccountForMultibyteTextAndBoundInboundAllocation() {
        assertFalse(SettingsRequestPacket.mapFits(Map.of("startingBlueprints", "界".repeat(9000)), SettingsRequestPacket.MAX_BYTES));
        var b = buffer();
        try {
            b.writeZero(SettingsRequestPacket.MAX_BYTES + 1);
            assertThrows(IllegalArgumentException.class, () -> SettingsRequestPacket.STREAM_CODEC.decode(b));
            b.clear(); b.writeZero(SettingsReplyPacket.MAX_BYTES + 1);
            assertThrows(IllegalArgumentException.class, () -> SettingsReplyPacket.STREAM_CODEC.decode(b));
        } finally { b.release(); }
    }
    @Test void lanSyncRequiresOneCompleteBoundedConfig() {
        var values = FzzySettingsAdapter.capture(new BlueprintConfig());
        var packet = new SettingsConfigSyncPacket(values); var b = buffer();
        try {
            SettingsConfigSyncPacket.STREAM_CODEC.encode(b, packet);
            assertEquals(packet, SettingsConfigSyncPacket.STREAM_CODEC.decode(b));
            assertEquals(0, b.readableBytes());
            b.clear(); b.writeZero(SettingsReplyPacket.MAX_BYTES + 1);
            assertThrows(IllegalArgumentException.class, () -> SettingsConfigSyncPacket.STREAM_CODEC.decode(b));
        } finally { b.release(); }
        assertThrows(IllegalArgumentException.class, () -> new SettingsConfigSyncPacket(Map.of()));
        var oversized = new HashMap<>(values); oversized.put("startingBlueprints", "界".repeat(70000));
        assertThrows(IllegalArgumentException.class, () -> new SettingsConfigSyncPacket(oversized));
    }
}
