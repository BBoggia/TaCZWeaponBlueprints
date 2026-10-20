package com.gamergaming.taczweaponblueprints.research.designer.io;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import com.gamergaming.taczweaponblueprints.research.designer.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.*;

/** Detached export, verified from bytes before any output directory is published. */
public final class ResearchDesignerExport {
    public static final String MINECRAFT_VERSION=net.minecraft.SharedConstants.getCurrentVersion().getName();
    public static final int PACK_FORMAT=net.minecraft.SharedConstants.getCurrentVersion().getPackVersion(net.minecraft.server.packs.PackType.SERVER_DATA);
    private ResearchDesignerExport(){}
    public static final class Prepared {
        private final String projectDigest,instructions;private final byte[] datapack,projectBundle,review;
        private Prepared(String digest,byte[] pack,byte[] bundle,byte[] review,String instructions){projectDigest=digest;datapack=pack.clone();projectBundle=bundle.clone();this.review=review.clone();this.instructions=instructions;}
        public String projectDigest(){return projectDigest;}public String instructions(){return instructions;}
        public byte[] datapack(){return datapack.clone();}public byte[] projectBundle(){return projectBundle.clone();}public byte[] review(){return review.clone();}
    }
    public record Imported(ResearchDesignerSession.Checkpoint checkpoint,Optional<ResearchDesignerCapture> capture,String bundleDigest){}
    public static Prepared prepare(ResearchDesignerSession.Checkpoint checkpoint,Optional<ResearchDesignerCapture> capture)throws IOException{
        var project=checkpoint.project();var context=capture.map(DesignerCapturedContext::new);
        var result=ResearchDesignerCompiler.compile(checkpoint,context.map(DesignerCapturedContext::catalog).orElse(Map.of()),capture);
        var candidate=result.candidate().orElseThrow(()->new IllegalArgumentException(result.diagnostics().stream().map(ResearchDesignerCompiler.Diagnostic::cause).reduce((a,b)->a+"\n"+b).orElse("Draft cannot be exported")));
        var all=candidate.resources().json();var files=new TreeMap<String,byte[]>();var external=new JsonObject();
        for(var family:all.entrySet())for(var resource:family.getValue().getAsJsonObject().entrySet()){
            var id=ResourceLocation.parse(resource.getKey());if(id.getNamespace().equals(project.namespace()))files.put(resourcePath(family.getKey(),id),canonical(resource.getValue(),2_000_000));
            else{if(!external.has(family.getKey()))external.add(family.getKey(),new JsonObject());external.getAsJsonObject(family.getKey()).add(id.toString(),resource.getValue().deepCopy());}
        }
        var meta=new JsonObject();var pack=new JsonObject();pack.addProperty("pack_format",PACK_FORMAT);pack.addProperty("description",project.title());meta.add("pack",pack);files.put("pack.mcmeta",canonical(meta,4096));
        byte[] datapack=DesignerArchive.write(files);var decoded=resources(DesignerArchive.read(datapack));
        // External resource dependencies remain external; use the explicitly captured versions for round-trip checks.
        external.entrySet().forEach(f->{if(!decoded.has(f.getKey()))decoded.add(f.getKey(),new JsonObject());f.getValue().getAsJsonObject().entrySet().forEach(e->decoded.getAsJsonObject(f.getKey()).add(e.getKey(),e.getValue()));});
        all.entrySet().stream().filter(e->e.getValue().getAsJsonObject().size()==0).forEach(e->decoded.add(e.getKey(),new JsonObject()));
        if(!DesignerFingerprint.of(all).equals(DesignerFingerprint.of(decoded)))throw new IOException("Exported resource bytes differ from the compiled candidate");
        ResearchDesignerCompiler.decodeResources(decoded);
        var review=new JsonObject();review.addProperty("format",1);review.addProperty("project_digest",project.digest());review.addProperty("datapack_sha256",DesignerJson.digest(datapack));review.addProperty("active_profile",candidate.profile().toString());review.addProperty("minecraft",MINECRAFT_VERSION);review.addProperty("pack_format",PACK_FORMAT);
        review.add("dependencies",project.json().get("dependencies"));review.add("required_settings",project.json().get("settings_assumptions"));review.add("source_assumptions",project.json().get("origin"));
        capture.ifPresent(c->{review.add("captured_settings",c.json().getAsJsonObject("settings").get("effective"));review.add("captured_dependencies",c.json().get("dependencies"));});
        var notices=new JsonArray();notices.add("Export verified; server activation has not been attempted.");notices.add("Match captured server settings and content. Changed presets or dependencies can change behavior.");notices.add("External progression gates, inventory delivery and survival supply remain unassessed.");notices.add("Project and capture contain full authored information, including hidden blueprints. Share deliberately.");
        notices.add("TaCZ gun packs and artwork are dependencies and are not bundled. Custom translations/art require a separate resource pack.");
        result.diagnostics().forEach(d->notices.add(d.cause()));review.add("compatibility_notices",notices);review.addProperty("external_resource_fingerprint",DesignerFingerprint.of(external));
        String instructions="Install this datapack for Minecraft "+MINECRAFT_VERSION+" (pack format "+PACK_FORMAT+").\nRequired profile: "+candidate.profile()+"\n\n1. Install matching [TaCZ] Weapon Research & Blueprints, TaCZ and declared content dependencies. See export-review.json for captured settings and versions.\n2. Back up the world. Copy only datapack.zip into that world's datapacks directory. The editable project is not required by the server.\n3. Enable the pack and inspect /datapack list enabled. Packs with conflicting resource IDs need an explicit priority decision; remove superseded copies of this same project. Higher-priority conflicting resources can replace these definitions.\n4. Set the server's activeResearchProfile setting to "+candidate.profile()+" and match the required settings. Export does not select a server profile.\n5. Reload the server's datapacks and inspect logs and the Research Bench before using the pack in your main world. Invalid or conflicting data can leave the last valid publication active.\n\nExport validation is not successful server activation. Captured settings may be stale. Check changed presets, exemptions, tags and dependency versions on the destination server. No game assets are included; use a separate resource pack for custom translations or artwork.\n\nShare editable-project.zip with other authors. Importing creates a new local project identity and retains stable node/blueprint/resource IDs. The bundle retains the current recovery checkpoint, not session Undo history or synthetic player state.\n";
        var bundleFiles=new TreeMap<String,byte[]>();bundleFiles.put("project.json",project.bytes());
        var portable=new ResearchDesignerSession.Checkpoint(project,checkpoint.pendingInputs(),checkpoint.sequence(),Optional.empty(),Optional.empty());bundleFiles.put("checkpoint.json",ResearchDesignerProjectStore.encodeRecovery(portable));capture.ifPresent(c->bundleFiles.put("capture.json",c.bytes()));
        var manifest=new JsonObject();manifest.addProperty("format",1);manifest.addProperty("kind","research_designer_project");manifest.addProperty("project_digest",project.digest());var hashes=new JsonObject();bundleFiles.forEach((name,bytes)->hashes.addProperty(name,DesignerJson.digest(bytes)));manifest.add("files",hashes);bundleFiles.put("manifest.json",DesignerJson.bytes(manifest,16384));
        byte[] bundle=DesignerArchive.write(bundleFiles);readProject(bundle);return new Prepared(project.digest(),datapack,bundle,DesignerJson.bytes(review,DesignerArchive.MAX_ENTRY),instructions);
    }
    private static byte[] canonical(JsonElement value,int limit){byte[] bytes=DesignerFingerprint.canonical(value).getBytes(StandardCharsets.UTF_8);if(bytes.length>limit)throw new IllegalArgumentException("Exported definition exceeds runtime size limit");return bytes;}
    private static String resourcePath(String kind,ResourceLocation id){String path="data/"+id.getNamespace()+"/taczweaponblueprints/"+kind+"/"+id.getPath()+".json";DesignerArchive.path(path);return path;}
    public static JsonObject resources(Map<String,byte[]> files){
        var root=new JsonObject();boolean meta=false;
        for(var entry:files.entrySet()){
            DesignerArchive.path(entry.getKey());
            if(entry.getKey().equals("pack.mcmeta")){var pack=DesignerJson.parse(entry.getValue(),4096).getAsJsonObject("pack");if(DesignerJson.integer(pack.get("pack_format"))!=PACK_FORMAT)throw new IllegalArgumentException("Unsupported datapack format");meta=true;continue;}
            String[] parts=entry.getKey().split("/",5);if(parts.length!=5||!parts[0].equals("data")||!parts[2].equals("taczweaponblueprints")||!parts[4].endsWith(".json"))throw new IllegalArgumentException("Unexpected exported file");
            String id=ResourceLocation.fromNamespaceAndPath(parts[1],parts[4].substring(0,parts[4].length()-5)).toString();if(!root.has(parts[3]))root.add(parts[3],new JsonObject());root.getAsJsonObject(parts[3]).add(id,DesignerJson.parse(entry.getValue(),DesignerArchive.MAX_ENTRY));
        }if(!meta)throw new IllegalArgumentException("Missing pack.mcmeta");return root;
    }
    public static Imported readProject(byte[] bytes)throws IOException{
        var files=DesignerArchive.read(bytes);if(!Set.of("manifest.json","project.json","checkpoint.json","capture.json").containsAll(files.keySet())||!files.keySet().containsAll(Set.of("manifest.json","project.json","checkpoint.json")))throw new IOException("Unsupported project bundle contents");
        var manifest=DesignerJson.parse(files.get("manifest.json"),16384);if(DesignerJson.integer(manifest.get("format"))!=1||!"research_designer_project".equals(manifest.get("kind").getAsString()))throw new IOException("Unsupported project bundle version");
        var hashes=manifest.getAsJsonObject("files");var expected=new HashSet<>(files.keySet());expected.remove("manifest.json");if(!hashes.keySet().equals(expected))throw new IOException("Bundle manifest differs from files");
        for(String name:expected)if(!DesignerJson.digest(files.get(name)).equals(hashes.get(name).getAsString()))throw new IOException("Bundle checksum mismatch: "+name);
        var project=ResearchDesignerProject.open(files.get("project.json")).editable().orElseThrow(()->new IOException("Unsupported project version"));var checkpoint=ResearchDesignerProjectStore.decodeRecovery(files.get("checkpoint.json"));
        if(!project.digest().equals(checkpoint.project().digest())||!project.digest().equals(manifest.get("project_digest").getAsString()))throw new IOException("Project/checkpoint identity differs");
        var capture=files.containsKey("capture.json")?Optional.of(ResearchDesignerCapture.open(files.get("capture.json"))):Optional.<ResearchDesignerCapture>empty();var origin=project.json().getAsJsonObject("origin");
        if("captured".equals(origin.get("kind").getAsString())&&(capture.isEmpty()||!capture.get().json().get("capture_id").getAsString().equals(origin.get("capture_id").getAsString())||!capture.get().fingerprint().equals(origin.get("content_fingerprint").getAsString())))throw new IOException("Captured context is missing or differs from the project");
        return new Imported(checkpoint,capture,DesignerJson.digest(bytes));
    }
    public static Path localFolder(Path game,boolean importing)throws IOException{Path base=game.toRealPath(),folder=base.resolve("taczweaponblueprints/designer/"+(importing?"imports":"exports"));ResearchDesignerProjectStore.ensureDirectories(base,folder);return folder;}
    public interface WriteProbe { void written(String name) throws IOException; }
    public static Path publish(Path game,String name,Prepared prepared)throws IOException{return publish(game,name,prepared,n->{});}
    public static Path publish(Path game,String name,Prepared prepared,WriteProbe probe)throws IOException{
        if(!name.matches("[a-z0-9][a-z0-9_-]{0,63}"))throw new IOException("Use 1–64 lowercase letters, digits, dashes or underscores for the export name");
        Path base=game.toRealPath(),root=base.resolve("taczweaponblueprints/designer/exports");ResearchDesignerProjectStore.ensureDirectories(base,root);
        try(var channel=FileChannel.open(root.resolve("export.lock"),CREATE,WRITE,NOFOLLOW_LINKS);var lock=channel.tryLock()){
            if(lock==null)throw new IOException("An export is already being written");Path destination=root.resolve(name);if(Files.exists(destination,NOFOLLOW_LINKS))throw new IOException("Export name already exists; choose a new name");
            Path stage=root.resolve(".export-"+UUID.randomUUID()+".tmp");Files.createDirectory(stage);
            try{write(stage.resolve("datapack.zip"),prepared.datapack);probe.written("datapack.zip");write(stage.resolve("editable-project.zip"),prepared.projectBundle);write(stage.resolve("export-review.json"),prepared.review);write(stage.resolve("INSTALL.txt"),prepared.instructions.getBytes(StandardCharsets.UTF_8));
                try{Files.move(stage,destination,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(stage,destination);}return destination;
            }finally{if(Files.isDirectory(stage,NOFOLLOW_LINKS)){try(var paths=Files.list(stage)){for(var path:paths.toList())Files.delete(path);}Files.delete(stage);}}
        }catch(java.nio.channels.OverlappingFileLockException busy){throw new IOException("An export is already being written",busy);}
    }
    private static void write(Path file,byte[] bytes)throws IOException{try(var out=FileChannel.open(file,CREATE_NEW,WRITE,NOFOLLOW_LINKS)){var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())out.write(buffer);out.force(true);}}
    public static Imported reviewImport(Path game,String name)throws IOException{
        if(!name.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}\\.zip"))throw new IOException("Choose a ZIP filename from the imports folder");
        Path base=game.toRealPath(),root=base.resolve("taczweaponblueprints/designer/imports");ResearchDesignerProjectStore.ensureDirectories(base,root);
        return readProject(ResearchDesignerProjectStore.read(root.resolve(name),DesignerArchive.MAX_BYTES));
    }
    public static ResearchDesignerSession importProject(Path game,String name)throws IOException{
        return importProject(game,name,null);
    }
    public static ResearchDesignerSession importProject(Path game,String name,String reviewedDigest)throws IOException{
        var imported=reviewImport(game,name);var old=imported.checkpoint;if(reviewedDigest!=null&&!imported.bundleDigest().equals(reviewedDigest))throw new IOException("Bundle changed; review it again before importing");var project=old.project().copyAs(UUID.randomUUID());
        var pending=old.pendingInputs().stream().map(i->new ResearchDesignerSession.PendingInput(new ResearchDesignerSession.InputKey(i.key().objectId().equals(old.project().projectId().toString())?project.projectId().toString():i.key().objectId(),i.key().pointer()),i.text())).toList();
        var checkpoint=new ResearchDesignerSession.Checkpoint(project,pending,old.sequence(),Optional.empty(),Optional.empty());var session=ResearchDesignerSession.restore(checkpoint,Optional.empty(),Optional.empty());
        if(imported.capture.isPresent())new ResearchDesignerCaptureStore(game).save(imported.capture.get());new ResearchDesignerProjectStore(game).save(session);return session;
    }
}
