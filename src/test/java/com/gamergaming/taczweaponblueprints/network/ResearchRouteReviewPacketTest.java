package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.progression.*;

class ResearchRouteReviewPacketTest {
    private final ResourceLocation target = new ResourceLocation("test:target");
    private ResearchRouteReviewRequestPacket request() { return new ResearchRouteReviewRequestPacket(7,19,42,target,new ResearchRouteFingerprint(11,12)); }
    @Test void requestAndSuccessRoundTripEveryCorrelationFieldAndBoundedRoute() {
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try {
            request().toBytes(b);assertEquals(request(),new ResearchRouteReviewRequestPacket(b));
            var snapshot=new ResearchGuidanceSnapshot(target,ResearchGuidanceSnapshot.State.MISSING_POINTS,3,0,
                    ResearchCostMode.POINTS_AND_ITEMS,false,true,0,List.of(),List.of(target),List.of(target),List.of(),Optional.of(target));
            var packet=new ResearchRouteReviewResultPacket(request(),new ResearchBenchMenu.GuidanceResult(ResearchBenchMenu.GuidanceResultCode.SUCCESS,Optional.of(snapshot)));
            packet.toBytes(b);assertEquals(packet,new ResearchRouteReviewResultPacket(b));assertEquals(0,b.readableBytes());
        } finally {b.release();}
    }
    @Test void failuresRoundTripWithoutPayloadAndInvalidHeadersFailClosed() {
        for(var code:List.of(ResearchBenchMenu.GuidanceResultCode.THROTTLED,ResearchBenchMenu.GuidanceResultCode.REJECTED)) {
            var b=new FriendlyByteBuf(Unpooled.buffer());try {
                var packet=new ResearchRouteReviewResultPacket(request(),new ResearchBenchMenu.GuidanceResult(code,Optional.empty()));
                packet.toBytes(b);assertEquals(packet,new ResearchRouteReviewResultPacket(b));
            } finally {b.release();}
        }
        assertThrows(IllegalArgumentException.class,()->new ResearchRouteReviewRequestPacket(7,0,42,target,new ResearchRouteFingerprint(1,2)));
        assertThrows(IllegalArgumentException.class,()->new ResearchRouteReviewRequestPacket(7,1,42,target,ResearchRouteFingerprint.NONE));
        var b=new FriendlyByteBuf(Unpooled.buffer());try {
            request().toBytes(b);b.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class,()->new ResearchRouteReviewResultPacket(b));
        } finally {b.release();}
        assertEquals("70",NetworkHandler.PROTOCOL_VERSION);
    }
    @Test void oversizedMaterialPayloadIsRejectedBeforeAllocation() {
        var b=new FriendlyByteBuf(Unpooled.buffer());try {
            request().toBytes(b);b.writeVarInt(ResearchBenchMenu.GuidanceResultCode.SUCCESS.ordinal());
            b.writeUtf(target.toString());b.writeVarInt(ResearchGuidanceSnapshot.State.AFFORDABLE.ordinal());
            b.writeVarInt(1);b.writeVarInt(1);b.writeVarInt(ResearchCostMode.POINTS_AND_ITEMS.ordinal());
            b.writeBoolean(false);b.writeBoolean(true);
            b.writeVarInt(1);b.writeVarInt(1);b.writeVarInt(1);b.writeVarInt(0);
            b.writeVarInt(ResearchGuidanceSnapshot.MAX_MATERIAL_PROGRESS+1);
            assertThrows(IllegalArgumentException.class,()->new ResearchRouteReviewResultPacket(b));
        } finally {b.release();}
    }

}
