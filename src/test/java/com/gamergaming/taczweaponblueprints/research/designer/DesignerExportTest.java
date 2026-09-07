package com.gamergaming.taczweaponblueprints.research.designer;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.gamergaming.taczweaponblueprints.research.designer.io.*;
import com.gamergaming.taczweaponblueprints.progression.TreeResearchResultMode;
import net.minecraft.resources.ResourceLocation;

class DesignerExportTest {
    @TempDir Path game;
    final DesignerPropertiesSimulationTest fixtures=new DesignerPropertiesSimulationTest();
    ResearchDesignerExport.Prepared prepare(DesignerPropertiesSimulationTest.Fixture f)throws IOException{return ResearchDesignerExport.prepare(new ResearchDesignerSession(f.project()).checkpoint(),Optional.of(f.context().capture()));}
    @Test void repeatExportIsByteIdenticalAndContainsOnlyOwnedDefinitions()throws Exception{
        var f=fixtures.fixture("inclusive_or");var a=prepare(f);var b=prepare(f);assertArrayEquals(a.datapack(),b.datapack());
        var files=DesignerArchive.read(a.datapack());assertTrue(files.keySet().stream().allMatch(k->k.equals("pack.mcmeta")||k.startsWith("data/"+f.project().namespace()+"/taczweaponblueprints/")));assertFalse(files.containsKey("project.json"));
        var modified=a.datapack();modified[0]=0;assertArrayEquals(b.datapack(),a.datapack());
    }
    @Test void exportedBytesPreserveRuntimeGraphAndFundedResearchRoute()throws Exception{
        var f=fixtures.fixture("inclusive_or");var resources=ResearchDesignerExport.resources(DesignerArchive.read(prepare(f).datapack()));
        var after=new ResearchDesignerCompiler.Candidate(f.project().digest(),f.candidate().profile(),ResearchDesignerCompiler.decodeResources(resources),new DesignerJson.ObjectValue(resources));
        var state=fixtures.funded(f,TreeResearchResultMode.DIRECT_LEARN,Set.of());var selected=Optional.of(new ResourceLocation("designer_fixture:end"));
        var before=DesignerSimulation.evaluate(f.candidate(),f.context(),state,selected);var result=DesignerSimulation.evaluate(after,f.context(),state,selected);
        assertEquals(before.evaluation().tree(),result.evaluation().tree());assertEquals(before.quote(),result.quote());assertEquals(before.unlocks(),result.unlocks());assertEquals(before.ready(),result.ready());assertEquals(14,result.quote().orElseThrow().pointCost());
    }
    @Test void sharedBundleRetainsSourceAndCaptureAndImportsUnderNewIdentity()throws Exception{
        var f=fixtures.fixture("inclusive_or");var p=prepare(f);var read=ResearchDesignerExport.readProject(p.projectBundle());assertEquals(f.project().json(),read.checkpoint().project().json());assertEquals(f.context().capture().fingerprint(),read.capture().orElseThrow().fingerprint());assertTrue(read.checkpoint().savedDigest().isEmpty());
        var folder=game.resolve("taczweaponblueprints/designer/imports");Files.createDirectories(folder);Files.write(folder.resolve("shared.zip"),p.projectBundle());var first=ResearchDesignerExport.importProject(game,"shared.zip");var second=ResearchDesignerExport.importProject(game,"shared.zip");assertNotEquals(first.project().projectId(),f.project().projectId());assertNotEquals(first.project().projectId(),second.project().projectId());assertEquals(f.project().draft(),first.project().draft());assertFalse(first.canUndo());assertFalse(first.dirty());assertTrue(new ResearchDesignerProjectStore(game).load(first.project().projectId()).document().isPresent());
    }
    @Test void localPublishNeverOverwritesAndLeavesNoPartialDestination()throws Exception{
        var p=prepare(fixtures.fixture("single"));var output=ResearchDesignerExport.publish(game,"test",p);assertArrayEquals(p.datapack(),Files.readAllBytes(output.resolve("datapack.zip")));assertThrows(IOException.class,()->ResearchDesignerExport.publish(game,"test",p));
        assertThrows(IOException.class,()->ResearchDesignerExport.publish(game,"interrupted",p,n->{throw new IOException("disk interrupted");}));assertFalse(Files.exists(output.getParent().resolve("interrupted")));try(var paths=Files.list(output.getParent())){assertFalse(paths.anyMatch(v->v.getFileName().toString().startsWith(".export-")));}
    }
    @Test void unsafeDestinationsAndSymlinkRootsAreRejected()throws Exception{
        var p=prepare(fixtures.fixture("single"));assertThrows(IOException.class,()->ResearchDesignerExport.publish(game,"../world",p));var external=Files.createDirectory(game.resolve("external"));Files.createSymbolicLink(game.resolve("taczweaponblueprints"),external);assertThrows(IOException.class,()->ResearchDesignerExport.publish(game,"test",p));assertEquals(0,Files.list(external).count());
    }
    @Test void importDoesNotFollowSymlinkOrTraversal()throws Exception{
        var dir=game.resolve("taczweaponblueprints/designer/imports");Files.createDirectories(dir);var outside=game.resolve("outside.zip");Files.write(outside,prepare(fixtures.fixture("single")).projectBundle());Files.createSymbolicLink(dir.resolve("linked.zip"),outside);assertThrows(IOException.class,()->ResearchDesignerExport.importProject(game,"linked.zip"));assertThrows(IOException.class,()->ResearchDesignerExport.importProject(game,"../outside.zip"));
    }
    @Test void damagedManifestAndUnexpectedFilesAreRejected()throws Exception{
        var files=new TreeMap<>(DesignerArchive.read(prepare(fixtures.fixture("single")).projectBundle()));files.put("project.json","{}".getBytes());assertThrows(IOException.class,()->ResearchDesignerExport.readProject(DesignerArchive.write(files)));files.put("unexpected.txt",new byte[0]);assertThrows(IOException.class,()->ResearchDesignerExport.readProject(DesignerArchive.write(files)));
    }
    @Test void archiveOrderingIgnoresInsertionOrder()throws Exception{var a=new LinkedHashMap<String,byte[]>();a.put("b",new byte[]{2});a.put("a",new byte[]{1});var b=new LinkedHashMap<String,byte[]>();b.put("a",new byte[]{1});b.put("b",new byte[]{2});assertArrayEquals(DesignerArchive.write(a),DesignerArchive.write(b));}
    @Test void traversalPathsAreRejected(){for(String name:List.of("../a","a/../b","/root","C:/x","a\\b","a//b","a/./b"))assertThrows(IllegalArgumentException.class,()->DesignerArchive.write(Map.of(name,new byte[0])),name);}
    @Test void truncatedAndSymlinkArchivesAreRejected()throws Exception{
        byte[] archive=DesignerArchive.write(Map.of("a",new byte[]{1}));assertThrows(IOException.class,()->DesignerArchive.read(Arrays.copyOf(archive,archive.length-1)));
        var b=ByteBuffer.wrap(archive).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<archive.length-4;i++)if(b.getInt(i)==0x02014b50){b.putInt(i+38,0120777<<16);break;}assertThrows(IOException.class,()->DesignerArchive.read(archive));
    }
    @Test void duplicateCentralNamesAreRejected()throws Exception{
        byte[] archive=DesignerArchive.write(Map.of("a",new byte[]{1},"b",new byte[]{2}));var b=ByteBuffer.wrap(archive).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<archive.length-46;i++)if(b.getInt(i)==0x02014b50)archive[i+46]='a';assertThrows(IOException.class,()->DesignerArchive.read(archive));
    }
    @Test void deflatedArchivesAreAcceptedButExcessiveExpansionIsRejected()throws Exception{
        var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("a"));zip.write(new byte[]{1,2,3});zip.closeEntry();}assertArrayEquals(new byte[]{1,2,3},DesignerArchive.read(bytes.toByteArray()).get("a"));
        var b=ByteBuffer.wrap(bytes.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<b.capacity()-46;i++)if(b.getInt(i)==0x02014b50){b.putInt(i+24,DesignerArchive.MAX_ENTRY+1);break;}assertThrows(IOException.class,()->DesignerArchive.read(b.array()));
    }
    @Test void allExportableContractFixturesRetainResolvedResources()throws Exception{
        for(var test:ResearchDesignerContractTest.runtimeCases().toList()){
            var f=fixtures.fixture(test.get("id").getAsString());var exported=ResearchDesignerExport.resources(DesignerArchive.read(prepare(f).datapack()));
            var expected=f.candidate().resources().json();expected.entrySet().removeIf(e->e.getValue().getAsJsonObject().size()==0);
            assertEquals(DesignerFingerprint.of(expected),DesignerFingerprint.of(exported),test.get("id").getAsString());
        }
    }
    @Test void unsupportedBundleAndProjectVersionsAreNotImported()throws Exception{
        var files=new TreeMap<>(DesignerArchive.read(prepare(fixtures.fixture("single")).projectBundle()));var manifest=DesignerJson.parse(files.get("manifest.json"),16384);manifest.addProperty("format",99);files.put("manifest.json",DesignerJson.bytes(manifest,16384));assertThrows(IOException.class,()->ResearchDesignerExport.readProject(DesignerArchive.write(files)));
        manifest.addProperty("format",1);var source=DesignerJson.parse(files.get("project.json"),DesignerJson.PROJECT_BYTES);source.addProperty("format",99);files.put("project.json",DesignerJson.bytes(source,DesignerJson.PROJECT_BYTES));manifest.getAsJsonObject("files").addProperty("project.json",DesignerJson.digest(files.get("project.json")));files.put("manifest.json",DesignerJson.bytes(manifest,16384));assertThrows(IOException.class,()->ResearchDesignerExport.readProject(DesignerArchive.write(files)));
    }
    @Test void importRejectsChangesAfterReview()throws Exception{
        var folder=ResearchDesignerExport.localFolder(game,true);Files.write(folder.resolve("shared.zip"),prepare(fixtures.fixture("single")).projectBundle());assertThrows(IOException.class,()->ResearchDesignerExport.importProject(game,"shared.zip","0".repeat(64)));
    }
    @Test void importReviewCoversRecoveryTextAsWellAsProjectContent() throws Exception {
        var folder = ResearchDesignerExport.localFolder(game, true);
        var file = folder.resolve("shared.zip");
        Files.write(file, prepare(fixtures.fixture("single")).projectBundle());
        var reviewed = ResearchDesignerExport.reviewImport(game, "shared.zip");
        var checkpoint = reviewed.checkpoint();
        var pending = new ResearchDesignerSession.PendingInput(
                new ResearchDesignerSession.InputKey(checkpoint.project().projectId().toString(), "/title"), "Unreviewed text");
        var changed = new ResearchDesignerSession.Checkpoint(checkpoint.project(), List.of(pending),
                checkpoint.sequence(), Optional.empty(), Optional.empty());
        var files = new TreeMap<>(DesignerArchive.read(Files.readAllBytes(file)));
        files.put("checkpoint.json", ResearchDesignerProjectStore.encodeRecovery(changed));
        var manifest = DesignerJson.parse(files.get("manifest.json"), 16384);
        manifest.getAsJsonObject("files").addProperty("checkpoint.json", DesignerJson.digest(files.get("checkpoint.json")));
        files.put("manifest.json", DesignerJson.bytes(manifest, 16384));
        Files.write(file, DesignerArchive.write(files));
        assertEquals(reviewed.checkpoint().project().digest(), ResearchDesignerExport.reviewImport(game, "shared.zip").checkpoint().project().digest());
        assertThrows(IOException.class, () -> ResearchDesignerExport.importProject(game, "shared.zip", reviewed.bundleDigest()));
        var rereviewed = ResearchDesignerExport.reviewImport(game, "shared.zip");
        var imported = ResearchDesignerExport.importProject(game, "shared.zip", rereviewed.bundleDigest());
        assertEquals("Unreviewed text", imported.pendingInputs().get(0).text());
        assertTrue(imported.dirty());
    }
    @Test void exportableCandidateDoesNotReportInformationalNoticesAsExportBlocks() throws Exception {
        var f = fixtures.fixture("single");
        var result = ResearchDesignerCompiler.compile(f.project(), f.context().catalog(), Optional.of(f.context().capture()));
        assertTrue(result.candidate().isPresent());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.severity() == ResearchDesignerCompiler.Severity.INFO));
        assertTrue(result.diagnostics().stream().allMatch(d -> d.json(0).getAsJsonArray("blocks").isEmpty()));
        assertNotNull(prepare(f));
    }
    @Test void unfinishedFieldInputBlocksExport()throws Exception{
        var f=fixtures.fixture("single");var session=new ResearchDesignerSession(f.project());session.pending("Incomplete cost",new ResearchDesignerSession.InputKey(f.project().draft().nodes().get(0).id(),"/cost"),"-");assertThrows(IllegalArgumentException.class,()->ResearchDesignerExport.prepare(session.checkpoint(),Optional.of(f.context().capture())));
    }

}
