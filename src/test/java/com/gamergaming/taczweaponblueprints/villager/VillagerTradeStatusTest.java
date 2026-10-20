package com.gamergaming.taczweaponblueprints.villager;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.gamergaming.taczweaponblueprints.network.SyncVillagerTradeStatusPacket;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.ResourceLocation;
class VillagerTradeStatusTest {
    private static VillagerTradeStatus.Row row(int index){return new VillagerTradeStatus.Row(index,5,"a".repeat(64),VillagerTradeStatus.Reason.SUPPLIES,4,Optional.of(ResourceLocation.parse("test:family")),Optional.of(ResourceLocation.parse("test:ammo")),30);}
    private static SyncVillagerTradeStatusPacket packet(long session,long revision){return new SyncVillagerTradeStatusPacket(3,session,revision,List.of(row(0)));}
    @Test void boundedPacketRoundTripsAndRejectsMalformedCountsAndEnums(){
        var b=new FriendlyByteBuf(Unpooled.buffer());try{
            var p=packet(7,9);p.toBytes(b);assertEquals(p,new SyncVillagerTradeStatusPacket(b));assertEquals(0,b.readableBytes());
            b.clear();b.writeVarInt(3);b.writeLong(7);b.writeLong(9);b.writeVarInt(257);assertThrows(IllegalArgumentException.class,()->new SyncVillagerTradeStatusPacket(b));
            b.clear();b.writeVarInt(3);b.writeLong(7);b.writeLong(9);b.writeVarInt(1);b.writeVarInt(0);b.writeLong(5);b.writeUtf("a".repeat(64));b.writeVarInt(99);b.writeVarInt(0);assertThrows(IllegalArgumentException.class,()->new SyncVillagerTradeStatusPacket(b));
            assertThrows(IllegalArgumentException.class,()->new SyncVillagerTradeStatusPacket(3,7,9,List.of(row(0),row(0))));
            assertThrows(IllegalArgumentException.class,()->row(256));
        }finally{b.release();}
    }
    @Test void closeContainerReuseOutOfOrderAndDisconnectCannotLeakStatus(){
        var inbox=new VillagerStatusInbox();var a=new Object();var b=new Object();
        assertFalse(inbox.accept(a,2,packet(7,1)));assertTrue(inbox.accept(a,3,packet(7,2)));
        assertFalse(inbox.accept(a,3,packet(7,1)));assertFalse(inbox.accept(a,3,packet(7,2)));
        assertTrue(inbox.accept(a,3,packet(7,3)));assertTrue(inbox.current(b).isEmpty());
        inbox.close();assertFalse(inbox.accept(b,3,packet(7,4)));assertTrue(inbox.accept(b,3,packet(8,1)));assertFalse(inbox.accept(b,3,packet(7,100)));
        inbox.disconnect();assertTrue(inbox.accept(a,3,packet(1,1)));
    }
}
