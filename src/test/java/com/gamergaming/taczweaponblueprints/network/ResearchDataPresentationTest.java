package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisRewardClass;
import com.gamergaming.taczweaponblueprints.client.*;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;

class ResearchDataPresentationTest {
    static ResearchDataPresentation data() {
        return new ResearchDataPresentation(1,3,true,true,true,AnalysisRewardClass.PROTECTED_PREREQUISITES,List.of(
                new ResearchDataPresentation.Station(1,3,.02,true,true,1,3),
                new ResearchDataPresentation.Station(2,3,.1,true,true,2,4),
                new ResearchDataPresentation.Station(3,3,1,true,false,0,0)));
    }
    @Test void boundedPresentationRoundTripsAndContainsNoTargetIds() {
        var p=AnalyzerPresentation.EMPTY.withData(data()).withStation(2);var b=new FriendlyByteBuf(Unpooled.buffer());
        try {AnalyzerPresentationCodec.write(b,p);assertTrue(b.readableBytes()<100);assertEquals(p,AnalyzerPresentationCodec.read(b));assertFalse(b.isReadable());}
        finally {b.release();}
        assertEquals("T1 / T2",ResearchDataPresentationText.tiers(3));
        assertEquals("≈2%",ResearchDataPresentationText.full(data().at(1)));
        assertEquals("100%",ResearchDataPresentationText.full(data().at(3)));
        assertEquals("0%",ResearchDataPresentationText.fragments(data().at(3)));
    }
    @Test void possibleOutcomesNeverRoundToImpossibleOrCertain() {
        assertEquals("<0.01%",AnalyzerProbabilityText.format(0,true,false));
        assertEquals(">99.99%",AnalyzerProbabilityText.format(1,true,false));
        assertThrows(IllegalArgumentException.class,()->AnalyzerProbabilityText.format(Double.NaN,true,false));
    }
    @Test void invalidOrMisorderedRowsAreRejected() {
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.1,1.01})
            assertThrows(IllegalArgumentException.class,()->new ResearchDataPresentation.Station(1,1,value,true,true,1,64));
        assertThrows(IllegalArgumentException.class,()->new ResearchDataPresentation.Station(1,8,.1,true,true,1,64));
        assertThrows(IllegalArgumentException.class,()->new ResearchDataPresentation.Station(1,1,.1,true,true,1,65));
        assertThrows(IllegalArgumentException.class,()->new ResearchDataPresentation(1,1,true,true,false,AnalysisRewardClass.PROTECTED_BYPASS,List.of(data().at(3),data().at(2),data().at(1))));
    }
    @Test void truncatedTailAndUnknownFormatAreRejected() {
        var full=new FriendlyByteBuf(Unpooled.buffer());
        try {
            AnalyzerPresentationCodec.write(full,AnalyzerPresentation.EMPTY.withData(data()));
            for(int size=6;size<full.writerIndex();size++) {
                var cut=new FriendlyByteBuf(full.copy(0,size));
                try {assertThrows(RuntimeException.class,()->AnalyzerPresentationCodec.read(cut));} finally {cut.release();}
            }
            full.setByte(4,99);assertThrows(IllegalArgumentException.class,()->AnalyzerPresentationCodec.read(full));
        } finally {full.release();}
    }
    @Test void currencyModeAndDataDefaultsTravelTogetherAndClearOnDisconnect() {
        var p=new SyncResearchCurrencyPacket(com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy.physical(new net.minecraft.resources.ResourceLocation("test:currency"),2),173,
                new com.gamergaming.taczweaponblueprints.progression.ResearchDataSettings(com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPolicy.Behavior.ANALYSIS,new com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPoolResolver.Defaults(false,true)));
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try {p.toBytes(b);assertEquals(p,new SyncResearchCurrencyPacket(b));ClientResearchCurrency.accept(p);assertEquals("analysis",ClientResearchCurrency.dataMode());
            ClientResearchCurrency.accept(new SyncResearchCurrencyPacket(com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy.banked(200,1),5));
            assertEquals(p,ClientResearchCurrency.current().orElseThrow());}
        finally {b.release();ClientResearchCurrency.clear();}
        assertTrue(ClientResearchCurrency.current().isEmpty());assertEquals("unknown",ClientResearchCurrency.dataMode());
    }
}
