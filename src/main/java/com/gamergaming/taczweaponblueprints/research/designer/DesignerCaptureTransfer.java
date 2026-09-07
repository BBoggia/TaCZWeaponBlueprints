package com.gamergaming.taczweaponblueprints.research.designer;

import java.util.*;
import java.util.function.Supplier;

/** Stop-and-wait transfer state, independent of Forge. Permission checks precede every supplier/read. */
public final class DesignerCaptureTransfer {
    public static final int CHUNK_BYTES = 24 * 1024;
    public static final long IDLE_MS = 30_000, MAX_MS = 180_000;
    public enum Action { START, NEXT, COMMIT, CANCEL }
    public enum Status { CHUNK, COMPLETE, DENIED, BUSY, RETRY, EXPIRED, CANCELLED, INVALID, LIMIT_EXCEEDED, UNSUPPORTED }
    public record Request(UUID id, Action action, int index) {
        public Request { Objects.requireNonNull(id); Objects.requireNonNull(action); if(index<0||index>1366) throw new IllegalArgumentException("Invalid chunk index"); }
    }
    public record Reply(UUID id, Status status, int index, int totalBytes, String checksum, byte[] payload) {
        public Reply {
            Objects.requireNonNull(id); Objects.requireNonNull(status); Objects.requireNonNull(checksum); payload=payload.clone();
            if (status == Status.CHUNK) {
                if (totalBytes < 1 || totalBytes > ResearchDesignerCapture.MAX_BYTES || index < 0
                        || (long)index*CHUNK_BYTES >= totalBytes || !checksum.matches("[0-9a-f]{64}")
                        || payload.length != Math.min(CHUNK_BYTES,totalBytes-index*CHUNK_BYTES)) throw new IllegalArgumentException("Invalid capture chunk");
            } else if (index!=0||totalBytes!=0||!checksum.isEmpty()||payload.length!=0) throw new IllegalArgumentException("Status must not contain capture metadata");
        }
        @Override public byte[] payload(){return payload.clone();}
        public static Reply status(UUID id,Status status){return new Reply(id,status,0,0,"",new byte[0]);}
    }
    private final UUID id; private final byte[] bytes; private final String digest; private final long started;
    private long lastActivity; private int next; private boolean closed;
    public DesignerCaptureTransfer(UUID id,byte[] bytes,long now){
        if(bytes.length<1||bytes.length>ResearchDesignerCapture.MAX_BYTES)throw new IllegalArgumentException("Capture size out of bounds");
        this.id=id;this.bytes=bytes.clone();digest=DesignerJson.digest(bytes);started=lastActivity=now;
    }
    public boolean expired(long now){return now-lastActivity>IDLE_MS||now-started>MAX_MS;}
    public Reply handle(Request request,boolean authorized,long now){
        if(!authorized){closed=true;return Reply.status(request.id(),Status.DENIED);}
        if(!request.id().equals(id))return Reply.status(request.id(),Status.INVALID);
        if(closed||expired(now)){closed=true;return Reply.status(id,Status.EXPIRED);}
        if(request.action()==Action.CANCEL){closed=true;return Reply.status(id,Status.CANCELLED);}
        if(request.index()!=next){closed=true;return Reply.status(id,Status.INVALID);}
        lastActivity=now;
        if(request.action()==Action.COMMIT&&next*CHUNK_BYTES>=bytes.length){closed=true;return Reply.status(id,Status.COMPLETE);}
        if(request.action()!=Action.NEXT||next*CHUNK_BYTES>=bytes.length){closed=true;return Reply.status(id,Status.INVALID);}
        int index=next++;return new Reply(id,Status.CHUNK,index,bytes.length,digest,Arrays.copyOfRange(bytes,index*CHUNK_BYTES,Math.min(bytes.length,(index+1)*CHUNK_BYTES)));
    }
    public boolean closed(){return closed;}
    public int size(){return bytes.length;}
    public static Optional<DesignerCaptureTransfer> begin(UUID id,boolean authorized,long now,Supplier<byte[]> capture){
        return authorized?Optional.of(new DesignerCaptureTransfer(id,capture.get(),now)):Optional.empty();
    }
    public static final class Receiver {
        private final UUID id; private final long started; private long last; private byte[] bytes; private String checksum;
        private int next,offset; private boolean closed;
        public Receiver(UUID id,long now){this.id=id;started=last=now;}
        public boolean expired(long now){return now-last>IDLE_MS||now-started>MAX_MS;}
        public Optional<Request> accept(Reply reply,boolean authorized,long now){
            if(!id.equals(reply.id()))return Optional.empty();
            if(!authorized||closed||expired(now)){clear();return Optional.empty();}
            last=now;
            if(reply.status()!=Status.CHUNK){if(reply.status()!=Status.COMPLETE)clear();return Optional.empty();}
            if(reply.index()!=next){clear();throw new IllegalArgumentException("Out-of-order capture chunk");}
            if(bytes==null){bytes=new byte[reply.totalBytes()];checksum=reply.checksum();}
            if(bytes.length!=reply.totalBytes()||!checksum.equals(reply.checksum())){clear();throw new IllegalArgumentException("Mixed capture transfer");}
            byte[] payload=reply.payload();System.arraycopy(payload,0,bytes,offset,payload.length);offset+=payload.length;next++;
            if(offset==bytes.length&&!DesignerJson.digest(bytes).equals(checksum)){clear();throw new IllegalArgumentException("Capture checksum mismatch");}
            return Optional.of(new Request(id,offset==bytes.length?Action.COMMIT:Action.NEXT,next));
        }
        public ResearchDesignerCapture finish(Reply reply,boolean authorized,long now){
            if(!id.equals(reply.id())||reply.status()!=Status.COMPLETE||!authorized||closed||expired(now)||bytes==null||offset!=bytes.length)
                throw new IllegalArgumentException("Capture was not authorized and completed");
            try {
                var capture=ResearchDesignerCapture.open(bytes);
                if(!capture.json().get("session_token").getAsString().equals(id.toString()))throw new IllegalArgumentException("Capture belongs to another request");
                return capture;
            } finally {clear();}
        }
        public void clear(){bytes=null;checksum=null;closed=true;}
    }
}
