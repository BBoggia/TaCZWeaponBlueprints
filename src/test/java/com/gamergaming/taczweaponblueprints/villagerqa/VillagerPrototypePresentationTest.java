package com.gamergaming.taczweaponblueprints.villagerqa;

import java.util.List;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VillagerPrototypePresentationTest {
    @Test void carriesOnlyMenuAndOfferIndices() {
        var status=new VillagerPrototypePresentation.Status(4,List.of(0,2));var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {status.encode(buffer);assertEquals(status,VillagerPrototypePresentation.Status.decode(buffer));assertEquals(0,buffer.readableBytes());}
        finally {buffer.release();}
    }
    @Test void boundsArrayBeforeAllocation() {
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {buffer.writeVarInt(4);buffer.writeVarInt(Integer.MAX_VALUE);assertThrows(IllegalArgumentException.class,()->VillagerPrototypePresentation.Status.decode(buffer));}
        finally {buffer.release();}
    }
    @Test void rejectsInvalidIndicesDuplicatesAndMenuIds() {
        assertThrows(IllegalArgumentException.class,()->new VillagerPrototypePresentation.Status(-1,List.of()));
        assertThrows(IllegalArgumentException.class,()->new VillagerPrototypePresentation.Status(4,List.of(256)));
        assertThrows(IllegalArgumentException.class,()->new VillagerPrototypePresentation.Status(4,List.of(1,1)));
    }
}
