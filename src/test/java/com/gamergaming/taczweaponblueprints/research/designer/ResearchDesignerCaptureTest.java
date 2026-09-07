package com.gamergaming.taczweaponblueprints.research.designer;

import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.taczweaponblueprints.research.designer.ResearchDesignerContractTest.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.gamergaming.taczweaponblueprints.research.designer.DesignerCaptureTransfer.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.*;
import java.util.stream.Stream;

class ResearchDesignerCaptureTest {
    static Stream<JsonObject> fixtures(){return runtimeCases().filter(c->!c.get("id").getAsString().equals("blank"));}
    static ResearchDesignerCapture fixture(JsonObject c){return fixture(c,project(c));}
    static ResearchDesignerCapture fixture(JsonObject c,JsonObject project){
        var f=compileFixture(project,c);
        var config=new BlueprintProgressionConfigSnapshot(true,true,true,JournalVisibility.FULL,true,DuplicateBlueprintPolicy.MANUAL_RECYCLING,false,100,false,f.profile());
        var dependencies=new JsonObject();dependencies.add("mods",new JsonObject());
        var context=DesignerCaptureAssembly.assemble(new DesignerCaptureAssembly.Inputs(f.snapshot(),f.catalog(),f.profile(),Map.of(),Map.of(),Map.of(),Map.of()),
                config,BlueprintAccessConfigSnapshot.EMPTY,ignored->false,new JsonObject(),new JsonObject(),dependencies,(kind,id)->"fixture");
        var revisions=new JsonObject();for(String key:List.of("catalog","research","automatic","config","access","tags"))revisions.addProperty(key,1);
        return ResearchDesignerCapture.create(UUID.randomUUID(),revisions,context);
    }
    @ParameterizedTest @MethodSource("fixtures") void capturesAndConvertsWholeCatalogIncludingHiddenNodes(JsonObject c){
        var capture=fixture(c);byte[] original=capture.bytes();
        var result=ResearchDesignerConversion.createCustom(capture,"custom_pack","Custom tree");
        assertEquals(c.getAsJsonArray("catalog").size(),result.project().draft().nodes().size());
        assertArrayEquals(original,capture.bytes());assertFalse(ResearchDesignerProject.open(result.project().bytes()).readOnly());
        assertTrue(result.project().json().getAsJsonObject("dependencies").getAsJsonArray("resources").asList().stream()
                .noneMatch(e -> Set.of("research_profiles","research_rules","research_automatic_placement_profiles").contains(e.getAsJsonObject().get("kind").getAsString())));
        var nodes=capture.json().getAsJsonObject("resolved_nodes");
        for(var node:result.project().draft().nodes()){
            var expected=nodes.getAsJsonObject(node.blueprintId().orElseThrow());
            assertEquals(expected.get("policy"),node.policy().json());
            assertEquals(expected.get("requirements").getAsJsonArray().size(),node.requirements().size());
            for(int i=0;i<node.requirements().size();i++)assertEquals(expected.getAsJsonArray("requirements").get(i).getAsJsonObject().getAsJsonArray("any_of").asList().stream().map(JsonElement::getAsString).toList(),node.requirements().get(i).anyOf());
        }
    }
    @Test void envelopeIsImmutableAndRejectsTamperingAndSyntheticIncompleteEvidence(){
        var capture=fixture(named("single"));var copy=capture.json();copy.getAsJsonObject("catalog").addProperty("bad",true);
        assertThrows(IllegalArgumentException.class,()->ResearchDesignerCapture.open(DesignerJson.bytes(copy,ResearchDesignerCapture.MAX_BYTES)));
        assertTrue(capture.sameContent(ResearchDesignerCapture.open(capture.bytes())));
        assertThrows(IllegalArgumentException.class,()->ResearchDesignerCapture.open(DesignerJson.bytes(read(ROOT.resolve("synthetic-resolved-capture.json")),ResearchDesignerCapture.MAX_BYTES)));
    }
    @Test void fingerprintNormalizesNumbersKeysAndUnicodeButPreservesOrderedAlternatives(){
        var a=JsonParser.parseString("{\"é\":\"🔧\",\"b\":2.00,\"a\":-0.0}");
        var b=JsonParser.parseString("{\"a\":0,\"b\":2e0,\"é\":\"🔧\"}");
        assertEquals(DesignerFingerprint.of(a),DesignerFingerprint.of(b));
        assertEquals("{\"a\":0,\"b\":2,\"é\":\"🔧\"}",DesignerFingerprint.canonical(a));
        assertNotEquals(DesignerFingerprint.of(JsonParser.parseString("[1,2]")),DesignerFingerprint.of(JsonParser.parseString("[2,1]")));
        assertTrue(DesignerFingerprint.canonical(JsonParser.parseString("1e-999999")).length()<30);
    }
    @Test void sessionIdsAndRevisionsDoNotChangeContentIdentityButContentDoes(){
        var a=fixture(named("single"));var b=fixture(named("single"));assertTrue(a.sameContent(b));
        assertNotEquals(a.json().get("capture_id"),b.json().get("capture_id"));
        var root=b.json();root.getAsJsonObject("settings").addProperty("cost",3);
        for(String key:List.of("format","capture_id","session_token","source_kind","disclosure","dependency_fingerprint","revisions"))root.remove(key);
        var changed=ResearchDesignerCapture.create(UUID.randomUUID(),a.json().getAsJsonObject("revisions"),root);
        assertFalse(a.sameContent(changed));
    }
    @Test void unauthorizedStartNeverReadsSource(){
        assertTrue(DesignerCaptureTransfer.begin(UUID.randomUUID(),false,0,()->{fail("Unauthorized source read");return new byte[0];}).isEmpty());
    }
    @Test void transferRequiresAllChunksAndFinalAuthorization(){
        var capture=fixture(named("large_addon"));UUID id=UUID.fromString(capture.json().get("session_token").getAsString());
        var server=new DesignerCaptureTransfer(id,capture.bytes(),0);var receiver=new DesignerCaptureTransfer.Receiver(id,0);
        Request request=new Request(id,Action.NEXT,0);Reply reply;
        do{reply=server.handle(request,true,10);if(reply.status()==Status.CHUNK)request=receiver.accept(reply,true,10).orElseThrow();}while(reply.status()==Status.CHUNK);
        assertEquals(Status.COMPLETE,reply.status());assertTrue(capture.sameContent(receiver.finish(reply,true,10)));
        assertThrows(IllegalArgumentException.class,()->receiver.finish(Reply.status(id,Status.COMPLETE),true,10));
    }
    @Test void permissionLossDisclosesNoMetadataAndInvalidatesReceiver(){
        UUID id=UUID.randomUUID();var server=new DesignerCaptureTransfer(id,fixture(named("single")).bytes(),0);
        var denied=server.handle(new Request(id,Action.NEXT,0),false,1);
        assertEquals(Status.DENIED,denied.status());assertEquals(0,denied.totalBytes());assertEquals("",denied.checksum());assertEquals(0,denied.payload().length);
        assertTrue(server.closed());
        var receiver=new DesignerCaptureTransfer.Receiver(id,0);
        receiver.accept(denied,false,1);
        assertThrows(IllegalArgumentException.class,()->receiver.finish(Reply.status(id,Status.COMPLETE),true,1));
    }
    @Test void rejectsOutOfOrderMixedOversizedAndStaleTransfers(){
        UUID id=UUID.randomUUID();byte[] data=new byte[50_000];Arrays.fill(data,(byte)1);
        var transfer=new DesignerCaptureTransfer(id,data,0);
        assertEquals(Status.INVALID,transfer.handle(new Request(UUID.randomUUID(),Action.NEXT,0),true,1).status());
        assertFalse(transfer.closed());
        assertEquals(Status.EXPIRED,transfer.handle(new Request(id,Action.NEXT,0),true,30_001).status());
        assertThrows(IllegalArgumentException.class,()->new Reply(id,Status.CHUNK,0,Integer.MAX_VALUE,"a".repeat(64),new byte[1]));
        var receiver=new DesignerCaptureTransfer.Receiver(id,0);
        assertTrue(receiver.accept(Reply.status(UUID.randomUUID(),Status.DENIED),true,1).isEmpty());
        var second=new DesignerCaptureTransfer(id,data,0);
        var first=second.handle(new Request(id,Action.NEXT,0),true,1);receiver.accept(first,true,1);
        assertThrows(IllegalArgumentException.class,()->receiver.accept(first,true,1));
    }
    @Test void cancellationAndEarlyCommitCannotExposeAnIncompleteCapture(){
        UUID id=UUID.randomUUID();var server=new DesignerCaptureTransfer(id,new byte[20],0);
        assertEquals(Status.INVALID,server.handle(new Request(id,Action.COMMIT,0),true,1).status());
        assertTrue(server.closed());var receiver=new DesignerCaptureTransfer.Receiver(id,0);
        assertThrows(IllegalArgumentException.class,()->receiver.finish(Reply.status(id,Status.COMPLETE),true,1));
        server=new DesignerCaptureTransfer(id,new byte[20],0);
        assertEquals(Status.CANCELLED,server.handle(new Request(id,Action.CANCEL,0),true,1).status());
    }
    @Test void offlineContextSurvivesCopiesAndRejectsMismatchedOrLinkedFiles(@org.junit.jupiter.api.io.TempDir java.nio.file.Path game) throws Exception {
        var capture=fixture(named("single"));var project=ResearchDesignerConversion.createCustom(capture,"local_pack","Offline").project();
        var store=new com.gamergaming.taczweaponblueprints.research.designer.io.ResearchDesignerCaptureStore(game);
        assertTrue(store.load(project).isEmpty());var id=store.save(capture);assertEquals(id,store.save(capture));
        assertTrue(store.load(project).orElseThrow().sameContent(capture));
        assertTrue(store.load(project.copyAs(UUID.randomUUID())).orElseThrow().sameContent(capture));
        var file=game.resolve("taczweaponblueprints/designer/captures/"+id+".json");
        java.nio.file.Files.write(file,fixture(named("single")).bytes());
        assertThrows(java.io.IOException.class,()->store.load(project));
        assertThrows(java.io.IOException.class,()->store.save(capture));
        java.nio.file.Files.delete(file);java.nio.file.Files.createSymbolicLink(file,game.resolve("outside.json"));
        assertThrows(java.io.IOException.class,()->store.load(project));
        assertThrows(java.io.IOException.class,()->store.save(capture));
    }
    @Test void wireCodecsAreBoundedAndDoNotCarrySourceInErrors(){
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{
            var reply=Reply.status(UUID.randomUUID(),Status.DENIED);
            new com.gamergaming.taczweaponblueprints.network.DesignerCaptureReplyPacket(reply).toBytes(buffer);
            assertEquals(reply.id(),new com.gamergaming.taczweaponblueprints.network.DesignerCaptureReplyPacket(buffer).reply().id());
            buffer.clear();buffer.writeUUID(UUID.randomUUID());buffer.writeEnum(Status.CHUNK);buffer.writeVarInt(0);
            buffer.writeVarInt(ResearchDesignerCapture.MAX_BYTES);buffer.writeUtf("a".repeat(64));buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(RuntimeException.class,()->new com.gamergaming.taczweaponblueprints.network.DesignerCaptureReplyPacket(buffer));
        }finally{buffer.release();}
    }

    @Test void inheritedVisibilityStillRevealsDiscoveredIdentityAfterConversion(){
        var c=named("single");var source=project(c);
        source.getAsJsonObject("draft").getAsJsonObject("profile").getAsJsonObject("definition").addProperty("visibility","hidden");
        source.getAsJsonObject("draft").getAsJsonArray("nodes").forEach(n -> n.getAsJsonObject().getAsJsonObject("policy").remove("visibility"));
        var captured=fixture(c,source);var converted=ResearchDesignerConversion.createCustom(captured,"custom_pack","Discovery").project();
        var f=compileFixture(converted.json(),c);var bp=f.catalog().keySet().iterator().next();
        var player=new com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData();player.discoverBlueprint(bp.toString());
        var policy=BlueprintResearchPolicyResolver.resolve(f.snapshot(),f.catalog(),f.profile(),bp,player,ignored -> false);
        assertEquals(JournalVisibility.PREVIEW,policy.visibility());
    }
    @Test void publishedFingerprintVectorsMatchIndependentHashes() throws Exception {
        var vectors=JsonParser.parseString(java.nio.file.Files.readString(ROOT.resolve("fingerprint-vectors.json"))).getAsJsonArray();
        for(var element:vectors){var vector=element.getAsJsonObject();var value=JsonParser.parseString(vector.get("source").getAsString());
            assertEquals(vector.get("canonical").getAsString(),DesignerFingerprint.canonical(value));
            assertEquals(vector.get("sha256").getAsString(),DesignerFingerprint.of(value));}
    }
    @Test void semanticHashExcludesProvenanceAndCurrencyCountersAndIncludesEveryDependencyFamily(){
        var source=fixture(named("single")).json();
        var base=ResearchDesignerCapture.semanticContext(source);
        var changed=source.deepCopy();changed.getAsJsonObject("definitions").entrySet().forEach(f -> f.getValue().getAsJsonObject().entrySet()
                .forEach(e -> e.getValue().getAsJsonObject().addProperty("source_pack","different_label")));
        assertEquals(DesignerFingerprint.of(base),DesignerFingerprint.of(ResearchDesignerCapture.semanticContext(changed)));
        var effective=new JsonObject();var currency=new JsonObject();currency.addProperty("revision",1);currency.addProperty("kind","physical_item");effective.add("currency",currency);
        source.getAsJsonObject("settings").add("effective",effective);var hash=DesignerFingerprint.of(ResearchDesignerCapture.semanticContext(source));currency.addProperty("revision",200);
        assertEquals(hash,DesignerFingerprint.of(ResearchDesignerCapture.semanticContext(source)));
        for(String family:source.getAsJsonObject("definitions").keySet()){
            var copy=source.deepCopy();var value=new JsonObject();var definition=new JsonObject();definition.addProperty("format",1);value.add("definition",definition);
            copy.getAsJsonObject("definitions").getAsJsonObject(family).add("fixture:new",value);
            assertNotEquals(hash,DesignerFingerprint.of(ResearchDesignerCapture.semanticContext(copy)),family);
        }
        for(String family:List.of("catalog","registry_facts","dependencies","settings")){
            var copy=source.deepCopy();copy.getAsJsonObject(family).addProperty("changed",true);
            assertNotEquals(hash,DesignerFingerprint.of(ResearchDesignerCapture.semanticContext(copy)),family);
        }
    }
    @Test void captureDepthUsesItsOwnLimitRatherThanRecoveryWrapperAllowance(){
        var root=new JsonObject();var cursor=root;
        for(int i=0;i<65;i++){var next=new JsonObject();cursor.add("n",next);cursor=next;}
        var bytes=DesignerJson.bytes(root,DesignerJson.RECOVERY_BYTES);
        assertThrows(IllegalArgumentException.class,()->ResearchDesignerCapture.open(bytes));
    }

    @Test void conversionCannotReuseTheCapturedManagedResourceIds(){
        var capture=fixture(named("single"));
        assertThrows(IllegalArgumentException.class,()->ResearchDesignerConversion.createCustom(capture,"designer_fixture","Conflicting IDs"));
    }
    @Test void finalAuthorizationRejectsAContextFromAnOlderRequest(){
        var capture=fixture(named("single"));UUID id=UUID.randomUUID();
        var server=new DesignerCaptureTransfer(id,capture.bytes(),0);var receiver=new DesignerCaptureTransfer.Receiver(id,0);
        Request request=new Request(id,Action.NEXT,0);Reply reply;
        do{reply=server.handle(request,true,1);if(reply.status()==Status.CHUNK)request=receiver.accept(reply,true,1).orElseThrow();}while(reply.status()==Status.CHUNK);
        final Reply finalReply=reply;
        assertThrows(IllegalArgumentException.class,()->receiver.finish(finalReply,true,1));
    }

}
