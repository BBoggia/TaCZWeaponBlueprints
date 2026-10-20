package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;

class ResearchCurrencyPolicyCodecTest {
    @Test void invalidCurrencySettingsAreRejectedBeforeAnySnapshotPublication() {
        for (String invalid : List.of("minecraft:air", "example:" + "x".repeat(257), "iron_ingot", "BAD:item")) {
            for (String kind : List.of("PHYSICAL_ITEM", "BANKED_RP")) {
                var config = ConfigApi.deserializeConfig(new BlueprintConfig(),
                        "version = 4\nresearchCurrencyKind = \"" + kind
                                + "\"\nresearchCurrencyItem = \"" + invalid + "\"\n").get();
                assertEquals(ResearchCurrencyPolicy.SCRAP.toString(), config.researchCurrencyItem.get());
                assertDoesNotThrow(config::initializeLoadedSnapshots);
                assertDoesNotThrow(config::onUpdateClient);
                assertDoesNotThrow(config::onSyncClient);
                assertDoesNotThrow(config::onSyncServer);
                config.researchCurrencyKind.validateAndSet(ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM);
                assertDoesNotThrow(config::initializeLoadedSnapshots);
                assertEquals(ResearchCurrencyPolicy.SCRAP, config.currencyPolicy().item().orElseThrow());
                assertEquals(config.currencyPolicy(), config.progressionSnapshot().currencyPolicy());
            }
        }
    }

    @Test void rejectedRuntimeItemEditKeepsTheLastValidCurrencyAndRevision() {
        var config = new BlueprintConfig();
        config.researchCurrencyItem.validateAndSet("minecraft:iron_ingot");
        config.initializeLoadedSnapshots();
        var before = config.currencyPolicy();
        for (String invalid : List.of("minecraft:air", "example:" + "x".repeat(257))) {
            config.researchCurrencyItem.validateAndSet(invalid);
            config.onUpdateClient();
            assertEquals("minecraft:iron_ingot", config.researchCurrencyItem.get());
            assertSame(before, config.currencyPolicy());
        }
        String longest = "example:" + "x".repeat(256 - "example:".length());
        config.researchCurrencyItem.validateAndSet(longest);
        config.initializeLoadedSnapshots();
        assertEquals(longest, config.currencyPolicy().item().orElseThrow().toString());
    }

    @Test void bothPolicyKindsRoundTripWithoutLosingRevisionIdentityOrCap() {
        for(var p:List.of(ResearchCurrencyPolicy.banked(10000,1),ResearchCurrencyPolicy.physical(ResearchCurrencyPolicy.SCRAP,Long.MAX_VALUE))) {
            var b=new FriendlyByteBuf(Unpooled.buffer());
            try { ResearchCurrencyPolicyCodec.write(b,p); assertEquals(p,ResearchCurrencyPolicyCodec.read(b)); assertEquals(0,b.readableBytes()); }
            finally { b.release(); }
            var json=ResearchCurrencyPolicyCodec.CODEC.encodeStart(JsonOps.INSTANCE,p).result().orElseThrow();
            assertEquals(p,ResearchCurrencyPolicyCodec.CODEC.parse(JsonOps.INSTANCE,json).result().orElseThrow());
        }
    }
    @Test void malformedDescriptorsFailBeforeRegistryFallback() {
        for(String raw:List.of(
                "{\"format\":2,\"kind\":\"banked_rp\",\"point_cap\":1,\"revision\":1}",
                "{\"format\":1,\"kind\":\"future\",\"point_cap\":1,\"revision\":1}",
                "{\"format\":1,\"kind\":\"physical_item\",\"item\":\"iron_ingot\",\"point_cap\":0,\"revision\":1}",
                "{\"format\":1,\"kind\":\"physical_item\",\"item\":\"minecraft:air\",\"point_cap\":0,\"revision\":1}",
                "{\"format\":1,\"kind\":\"banked_rp\",\"point_cap\":-1,\"revision\":1}",
                "{\"format\":1,\"kind\":\"banked_rp\",\"point_cap\":1,\"revision\":0}",
                "{\"format\":1,\"kind\":\"physical_item\",\"point_cap\":0,\"revision\":1}",
                "{\"format\":1,\"kind\":\"banked_rp\",\"point_cap\":1,\"revision\":1,\"extra\":true}"))
            assertTrue(ResearchCurrencyPolicyCodec.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(raw)).error().isPresent(),raw);
        assertThrows(IllegalArgumentException.class,()->ResearchCurrencyPolicy.physical(ResourceLocation.parse("minecraft:air"),1));
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try { b.writeVarInt(1); b.writeVarInt(999); assertThrows(IllegalArgumentException.class,()->ResearchCurrencyPolicyCodec.read(b)); }
        finally { b.release(); }
    }
    @Test void publishedBankedPolicyTracksSynchronizedCap() {
        var config=new BlueprintConfig();
        config.researchCurrencyKind.validateAndSet(ResearchCurrencyPolicy.Kind.BANKED_RP); config.initializeLoadedSnapshots();
        var original=config.currencyPolicy(); assertEquals(ResearchCurrencyPolicy.Kind.BANKED_RP,original.kind());
        assertEquals(10000,original.pointCap());
        config.initializeLoadedSnapshots(); assertSame(original,config.currencyPolicy());
        var loaded=ConfigApi.deserializeConfig(config,"version = 4\nresearchPointCap = 2676\n").get();
        loaded.initializeLoadedSnapshots();
        assertEquals(2676,loaded.currencyPolicy().pointCap()); assertTrue(loaded.currencyPolicy().revision()>original.revision());
        var client=ConfigApi.deserializeConfig(new BlueprintConfig(),ConfigApi.serializeConfig(loaded).get()).get();
        client.onUpdateClient(); assertEquals(loaded.currencyPolicy().pointCap(),client.currencyPolicy().pointCap());
        assertEquals(loaded.currencyPolicy().kind(),client.currencyPolicy().kind());
        assertFalse(ConfigApi.serializeConfig(loaded).get().contains("currencyPolicy"),"process-local quote revision is not a persisted setting");
    }
    @Test void physicalModeAndConfiguredItemRoundTripWithoutUsingTheBankedCap() {
        var loaded = ConfigApi.deserializeConfig(new BlueprintConfig(),
                "version = 4\nresearchCurrencyKind = \"PHYSICAL_ITEM\"\nresearchCurrencyItem = \"minecraft:iron_ingot\"\nresearchPointCap = 0\n").get();
        loaded.initializeLoadedSnapshots();
        assertEquals(ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM, loaded.currencyPolicy().kind());
        assertEquals(ResourceLocation.parse("minecraft:iron_ingot"), loaded.currencyPolicy().item().orElseThrow());
        assertEquals(0, loaded.currencyPolicy().pointCap());
        assertEquals(loaded.currencyPolicy(), loaded.progressionSnapshot().currencyPolicy());
        var client = ConfigApi.deserializeConfig(new BlueprintConfig(), ConfigApi.serializeConfig(loaded).get()).get();
        client.onUpdateClient();
        assertEquals(loaded.currencyPolicy().kind(), client.currencyPolicy().kind());
        assertEquals(loaded.currencyPolicy().item(), client.currencyPolicy().item());
    }
    @Test void currencyPublicationPacketUsesAnExplicitPolicyAndBoundedCount() {
        var packet = new SyncResearchCurrencyPacket(ResearchCurrencyPolicy.physical(ResourceLocation.parse("minecraft:iron_ingot"), 7), 129);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { packet.toBytes(buffer); assertEquals(packet, new SyncResearchCurrencyPacket(buffer)); assertFalse(buffer.isReadable()); }
        finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> new SyncResearchCurrencyPacket(packet.policy(), -1));
    }

}
