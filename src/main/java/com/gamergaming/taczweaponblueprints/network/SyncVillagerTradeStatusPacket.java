package com.gamergaming.taczweaponblueprints.network;

import java.util.*;
import com.gamergaming.taczweaponblueprints.villager.VillagerTradeStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record SyncVillagerTradeStatusPacket(int containerId,long session,long revision,List<VillagerTradeStatus.Row> rows) implements CustomPacketPayload {
    public static final Type<SyncVillagerTradeStatusPacket> TYPE = new Type<>(
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("sync_villager_trade_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncVillagerTradeStatusPacket> STREAM_CODEC =
            StreamCodec.ofMember(SyncVillagerTradeStatusPacket::toBytes, SyncVillagerTradeStatusPacket::new);

    @Override
    public Type<SyncVillagerTradeStatusPacket> type() { return TYPE; }

    public SyncVillagerTradeStatusPacket {
        rows=List.copyOf(rows);
        if(containerId<0||containerId>255||session<=0||revision<0||rows.size()>256||rows.stream().map(VillagerTradeStatus.Row::index).distinct().count()!=rows.size())
            throw new IllegalArgumentException("Invalid merchant status packet");
    }
    public SyncVillagerTradeStatusPacket(FriendlyByteBuf b){this(b.readVarInt(),b.readLong(),b.readLong(),read(b));}
    private static List<VillagerTradeStatus.Row> read(FriendlyByteBuf b){
        int count=b.readVarInt();if(count<0||count>256)throw new IllegalArgumentException("Oversized merchant status");
        var rows=new ArrayList<VillagerTradeStatus.Row>();
        for(int i=0;i<count;i++){
            int index=b.readVarInt();long revision=b.readLong();String fingerprint=b.readUtf(64);int reason=b.readVarInt(),flags=b.readVarInt();
            if(reason<0||reason>=VillagerTradeStatus.Reason.values().length)throw new IllegalArgumentException("Invalid merchant reason");
            boolean magazine=b.readBoolean();var family=magazine?Optional.of(id(b)):Optional.<ResourceLocation>empty();var ammo=magazine?Optional.of(id(b)):Optional.<ResourceLocation>empty();int capacity=magazine?b.readVarInt():0;
            rows.add(new VillagerTradeStatus.Row(index,revision,fingerprint,VillagerTradeStatus.Reason.values()[reason],flags,family,ammo,capacity));
        }
        return rows;
    }
    private static ResourceLocation id(FriendlyByteBuf b){var raw=b.readUtf(256);var id=ResourceLocation.tryParse(raw);if(id==null||!id.toString().equals(raw))throw new IllegalArgumentException("Invalid merchant ID");return id;}
    public void toBytes(FriendlyByteBuf b){
        b.writeVarInt(containerId);b.writeLong(session);b.writeLong(revision);b.writeVarInt(rows.size());
        for(var row:rows){b.writeVarInt(row.index());b.writeLong(row.revision());b.writeUtf(row.fingerprint(),64);b.writeVarInt(row.reason().ordinal());b.writeVarInt(row.flags());b.writeBoolean(row.family().isPresent());
            if(row.family().isPresent()){b.writeUtf(row.family().orElseThrow().toString(),256);b.writeUtf(row.ammo().orElseThrow().toString(),256);b.writeVarInt(row.capacity());}}
    }
    public void handle(IPayloadContext context) {
        new ClientHandler().handle(context);
    }

    /** Loaded only when this clientbound payload is handled, never during server registration. */
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private final class ClientHandler {
        void handle(IPayloadContext supplier) {
            var context=supplier;context.enqueueWork(()->com.gamergaming.taczweaponblueprints.client.ClientVillagerTradeStatus.accept(SyncVillagerTradeStatusPacket.this));
        }
    }
}
