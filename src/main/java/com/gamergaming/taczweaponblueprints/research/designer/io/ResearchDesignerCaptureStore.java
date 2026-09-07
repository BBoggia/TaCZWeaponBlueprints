package com.gamergaming.taczweaponblueprints.research.designer.io;

import com.gamergaming.taczweaponblueprints.research.designer.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

/** Immutable local context library. Projects and Save As copies refer to a capture by UUID and hash. */
public final class ResearchDesignerCaptureStore {
    private final Path root;
    public ResearchDesignerCaptureStore(Path gameDirectory) throws IOException {
        var game=gameDirectory.toRealPath();root=game.resolve("taczweaponblueprints/designer/captures");
        ResearchDesignerProjectStore.ensureDirectories(game,root);
    }
    public UUID save(ResearchDesignerCapture capture) throws IOException {
        UUID id=UUID.fromString(capture.json().get("capture_id").getAsString());
        ResearchDesignerProjectStore.ensureDirectories(root.getRoot(),root);
        Path lock=root.resolve(id+".lock");
        try(var channel=FileChannel.open(lock,CREATE,WRITE,NOFOLLOW_LINKS);var ignored=channel.tryLock()){
            if(ignored==null)throw new IOException("Capture is being saved");
            Path target=root.resolve(id+".json");byte[] bytes=capture.bytes();
            if(Files.exists(target,NOFOLLOW_LINKS)){
                if(!Arrays.equals(bytes,ResearchDesignerProjectStore.read(target,ResearchDesignerCapture.MAX_BYTES)))throw new IOException("Capture identity already has different content");
                return id;
            }
            Path temporary=root.resolve(".capture-"+UUID.randomUUID()+".tmp");
            try{
                try(var out=FileChannel.open(temporary,CREATE_NEW,WRITE,NOFOLLOW_LINKS)){
                    ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())out.write(buffer);out.force(true);
                }
                try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);}
                catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,target);}
            }finally{Files.deleteIfExists(temporary);}
            return id;
        }catch(java.nio.channels.OverlappingFileLockException busy){throw new IOException("Capture is being saved",busy);}
    }
    public Optional<ResearchDesignerCapture> load(ResearchDesignerProject project) throws IOException {
        var origin=project.json().getAsJsonObject("origin");
        if(!origin.get("kind").getAsString().equals("captured"))return Optional.empty();
        UUID id=UUID.fromString(origin.get("capture_id").getAsString());
        ResearchDesignerProjectStore.ensureDirectories(root.getRoot(),root);
        Path file=root.resolve(id+".json");if(!Files.exists(file,NOFOLLOW_LINKS))return Optional.empty();
        try{
            var capture=ResearchDesignerCapture.open(ResearchDesignerProjectStore.read(file,ResearchDesignerCapture.MAX_BYTES));
            if(!capture.json().get("capture_id").getAsString().equals(id.toString())||!capture.fingerprint().equals(origin.get("content_fingerprint").getAsString()))
                throw new IOException("Project and capture context do not match");
            return Optional.of(capture);
        }catch(IllegalArgumentException invalid){throw new IOException("Invalid captured context",invalid);}
    }
}
