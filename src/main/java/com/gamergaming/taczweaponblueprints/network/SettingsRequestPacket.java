package com.gamergaming.taczweaponblueprints.network;

import java.nio.charset.StandardCharsets;
import java.util.*;
import com.gamergaming.taczweaponblueprints.settings.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SettingsRequestPacket(UUID request, Action action, SettingsChangePlan plan) implements CustomPacketPayload {
    public enum Action { OPEN, APPLY }
    public static final int MAX_BYTES = 24_000;
    public static final Type<SettingsRequestPacket> TYPE = new Type<>(com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("settings_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SettingsRequestPacket> STREAM_CODEC = StreamCodec.ofMember(SettingsRequestPacket::write, SettingsRequestPacket::read);
    public SettingsRequestPacket {
        Objects.requireNonNull(request); Objects.requireNonNull(action);
        if (action == Action.APPLY && (plan == null || plan.revision().length() > 64 || !mapFits(plan.changes(), MAX_BYTES - 128))) throw new IllegalArgumentException("Settings patch too large");
        if (action == Action.OPEN && plan != null) throw new IllegalArgumentException("Open must not contain edits");
    }
    @Override public Type<SettingsRequestPacket> type() { return TYPE; }
    private static SettingsRequestPacket read(FriendlyByteBuf b) {
        if (b.readableBytes() > MAX_BYTES) throw new IllegalArgumentException("Settings patch too large");
        UUID request = b.readUUID(); Action action = b.readEnum(Action.class);
        return new SettingsRequestPacket(request, action, action == Action.OPEN ? null : new SettingsChangePlan(b.readUUID(), b.readUtf(64), readMap(b, MAX_BYTES)));
    }
    private void write(FriendlyByteBuf b) {
        b.writeUUID(request); b.writeEnum(action);
        if (plan != null) { b.writeUUID(plan.session()); b.writeUtf(plan.revision(), 64); writeMap(b, plan.changes()); }
    }
    public void handle(IPayloadContext context) { context.enqueueWork(() -> ServerSettingsAccess.handle(NetworkHandler.sender(context), this)); }
    static Map<String, String> readMap(FriendlyByteBuf b, int limit) {
        int count = b.readVarInt(); if (count < 0 || count > 128) throw new IllegalArgumentException("Settings field count");
        Map<String, String> values = new LinkedHashMap<>(); int start = b.readerIndex();
        for (int i = 0; i < count; i++) {
            String id = b.readUtf(64); String value = b.readUtf(limit);
            if (values.put(id, value) != null || b.readerIndex() - start > limit) throw new IllegalArgumentException("Invalid settings payload");
        }
        return values;
    }
    static void writeMap(FriendlyByteBuf b, Map<String, String> values) {
        b.writeVarInt(values.size()); values.forEach((id, value) -> { b.writeUtf(id, 64); b.writeUtf(value, SettingsReplyPacket.MAX_BYTES); });
    }
    static boolean mapFits(Map<String, String> values, int limit) {
        if (values.size() > 128) return false;
        long size = 5;
        for (var entry : values.entrySet()) {
            if (entry.getKey().length() > 64) return false;
            size += entry.getKey().getBytes(StandardCharsets.UTF_8).length + entry.getValue().getBytes(StandardCharsets.UTF_8).length + 10L;
        }
        return size <= limit;
    }
}
