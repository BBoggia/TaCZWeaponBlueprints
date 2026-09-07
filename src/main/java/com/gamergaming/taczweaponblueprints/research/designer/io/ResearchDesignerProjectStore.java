package com.gamergaming.taczweaponblueprints.research.designer.io;

import com.gamergaming.taczweaponblueprints.research.designer.*;
import com.gamergaming.taczweaponblueprints.research.designer.ResearchDesignerSession.*;
import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.*;

/** Local UUID-addressed storage. No caller-provided relative paths or live-world destinations. */
public final class ResearchDesignerProjectStore {
    public enum WriteStage { TEMP_SYNCED, BACKUP_WRITTEN, BEFORE_REPLACE }
    @FunctionalInterface public interface WriteProbe { void at(WriteStage stage, Path target) throws IOException; }
    private final Path root;
    private final WriteProbe probe;
    public ResearchDesignerProjectStore(Path gameDirectory) throws IOException { this(gameDirectory, (stage, target) -> {}); }
    /** Probe supports filesystem-failure tests; production callers use the one-argument constructor. */
    public ResearchDesignerProjectStore(Path gameDirectory, WriteProbe probe) throws IOException {
        Path game = gameDirectory.toRealPath();
        if (!Files.isDirectory(game, NOFOLLOW_LINKS)) throw new IOException("Game directory is not a directory");
        root = game.resolve("taczweaponblueprints/designer/projects"); this.probe = Objects.requireNonNull(probe);
        ensureDirectories(game, root);
    }
    public record Loaded(Optional<ResearchDesignerProject.Opened> document, Optional<Checkpoint> recovery,
                         Optional<String> savedDigest, Optional<String> recoveryDigest, View view, List<String> notices) {
        public Loaded { notices = List.copyOf(notices); }
        public boolean readOnly() { return document.map(ResearchDesignerProject.Opened::readOnly).orElse(false) || notices.contains("unsupported_recovery"); }
        /** Explicit recovery choice; a caller can inspect notices and choose the saved document instead. */
        public ResearchDesignerSession session(boolean recover) {
            if (readOnly()) throw new IllegalStateException("Unsupported document is read-only");
            ResearchDesignerSession result;
            if (recover && recovery.isPresent()) result = ResearchDesignerSession.restore(recovery.get(), savedDigest, recoveryDigest);
            else {
                var project = document.flatMap(ResearchDesignerProject.Opened::editable)
                        .orElseThrow(() -> new IllegalStateException("No saved document; choose recovery"));
                long sequence = recovery.map(Checkpoint::sequence).orElse(0L);
                result = ResearchDesignerSession.restore(new Checkpoint(project, List.of(), sequence, savedDigest, recoveryDigest), savedDigest, recoveryDigest);
            }
            result.view(view); return result;
        }
    }
    public Loaded load(UUID projectId) throws IOException {
        return locked(projectId, directory -> loadLocked(directory, projectId));
    }
    private Loaded loadLocked(Path directory, UUID projectId) throws IOException {
        var notices = new ArrayList<String>(); Optional<ResearchDesignerProject.Opened> saved = Optional.empty();
        for (String name : List.of("project.json", "project.previous.json")) {
            Path file = directory.resolve(name);
            if (!Files.exists(file, NOFOLLOW_LINKS)) continue;
            try {
                var opened = ResearchDesignerProject.open(read(file, DesignerJson.PROJECT_BYTES));
                // A future-format file must never be silently replaced by an older backup.
                if (opened.editable().isPresent() && !opened.editable().get().projectId().equals(projectId)) throw new IOException("Project identity mismatch");
                saved = Optional.of(opened);
                if (name.contains("previous")) notices.add("previous_project_recovered");
                break;
            } catch (IllegalArgumentException | IOException e) { notices.add("invalid_" + name); }
        }
        Optional<String> digest = saved.flatMap(ResearchDesignerProject.Opened::editable).map(ResearchDesignerProject::digest);
        Optional<Checkpoint> recovery = Optional.empty();
        if (saved.map(ResearchDesignerProject.Opened::readOnly).orElse(false)) notices.add("read_only_project");
        else for (String name : List.of("recovery.json", "recovery.previous.json")) {
            Path file = directory.resolve(name); if (!Files.exists(file, NOFOLLOW_LINKS)) continue;
            try {
                byte[] data = read(file, DesignerJson.RECOVERY_BYTES);
                if (unsupportedRecovery(data)) { notices.add("unsupported_recovery"); break; }
                Checkpoint candidate = decodeRecovery(data);
                if (!candidate.project().projectId().equals(projectId)) throw new IOException("Recovery identity mismatch");
                if (!candidate.savedDigest().equals(digest) && !digest.filter(candidate.project().digest()::equals).isPresent()) {
                    notices.add("stale_" + name); continue;
                }
                recovery = Optional.of(candidate); if (name.contains("previous")) notices.add("previous_recovery_recovered"); break;
            } catch (IllegalArgumentException | IOException e) { notices.add("invalid_" + name); }
        }
        View view = View.initial();
        Path viewPath = directory.resolve("view.json");
        if (Files.exists(viewPath, NOFOLLOW_LINKS)) try { view = decodeView(read(viewPath, 16 * 1024)); }
        catch (IllegalArgumentException | IOException e) { notices.add("invalid_view"); }
        return new Loaded(saved, recovery, digest, recoveryDigest(directory), view, notices);
    }
    /** The recovery checkpoint is durable even though the main project replacement failed. */
    public static final class SaveFailure extends IOException {
        private final String recoveryDigest;
        private SaveFailure(IOException cause, String recoveryDigest) {
            super(cause.getMessage(), cause); this.recoveryDigest = recoveryDigest;
        }
        public String recoveryDigest() { return recoveryDigest; }
    }
    public void save(ResearchDesignerSession session) throws IOException {
        Checkpoint checkpoint = session.checkpoint();
        try { session.saved(checkpoint, save(checkpoint)); }
        catch (SaveFailure failure) {
            session.checkpointWritten(checkpoint, failure.recoveryDigest());
            throw failure;
        }
    }
    public String save(Checkpoint checkpoint) throws IOException {
        return locked(checkpoint.project().projectId(), directory -> {
            checkBaseline(directory, checkpoint);
            // Recovery first makes pending text durable even if the process stops after project replacement.
            byte[] recovery = encodeRecovery(checkpoint); write(directory, "recovery.json", recovery);
            String receipt = DesignerJson.digest(recovery);
            try { write(directory, "project.json", checkpoint.project().bytes()); }
            catch (IOException failure) { throw new SaveFailure(failure, receipt); }
            return receipt;
        });
    }
    public boolean autosave(ResearchDesignerSession session, long nowNanos) throws IOException {
        if (!session.claimAutosave(nowNanos)) return false;
        Checkpoint checkpoint = session.checkpoint(); String digest = recoverableCheckpoint(checkpoint); session.checkpointWritten(checkpoint, digest); return true;
    }
    public String recoverableCheckpoint(Checkpoint checkpoint) throws IOException {
        return locked(checkpoint.project().projectId(), directory -> {
            checkBaseline(directory, checkpoint); byte[] recovery = encodeRecovery(checkpoint); write(directory, "recovery.json", recovery); return DesignerJson.digest(recovery);
        });
    }
    public ResearchDesignerSession saveAs(ResearchDesignerSession original) throws IOException {
        Checkpoint old = original.checkpoint();
        var project = old.project().copyAs(UUID.randomUUID());
        var pending = old.pendingInputs().stream().map(input -> input.key().objectId().equals(old.project().projectId().toString())
                ? new PendingInput(new InputKey(project.projectId().toString(), input.key().pointer()), input.text()) : input).toList();
        var copy = new Checkpoint(project, pending, old.sequence(), Optional.empty(), Optional.empty());
        String digest = save(copy); var result = ResearchDesignerSession.restore(copy, Optional.of(copy.project().digest()), Optional.of(digest)); result.view(original.view());
        return result;
    }
    public void saveView(UUID projectId, View view) throws IOException {
        locked(projectId, directory -> { write(directory, "view.json", encodeView(view)); return null; });
    }
    private void checkBaseline(Path directory, Checkpoint checkpoint) throws IOException {
        Loaded current = loadLocked(directory, checkpoint.project().projectId());
        if (current.readOnly() || !current.savedDigest().equals(checkpoint.savedDigest()) || !current.recoveryDigest().equals(checkpoint.recoveryDigest())) throw new IOException("Project changed on disk; reopen before saving");
        if (current.document().isEmpty() && Files.exists(directory.resolve("project.json"), NOFOLLOW_LINKS))
            throw new IOException("Damaged project has no valid previous save; preserve files for recovery");
    }
    private static Optional<String> recoveryDigest(Path directory) throws IOException {
        ensureDirectories(directory.getRoot(), directory);
        Path file = directory.resolve("recovery.json");
        return Files.exists(file, NOFOLLOW_LINKS) ? Optional.of(DesignerJson.digest(read(file, DesignerJson.RECOVERY_BYTES))) : Optional.empty();
    }
    private static boolean unsupportedRecovery(byte[] bytes) {
        JsonObject envelope = DesignerJson.parse(bytes, DesignerJson.RECOVERY_BYTES);
        if (DesignerJson.integer(envelope.get("format")) != 1) return true;
        JsonElement payload = envelope.get("payload");
        if (payload != null && payload.isJsonObject() && payload.getAsJsonObject().has("project")) {
            JsonElement project = payload.getAsJsonObject().get("project");
            if (project.isJsonObject()) return ResearchDesignerProject.open(DesignerJson.bytes(project.getAsJsonObject(), DesignerJson.PROJECT_BYTES)).readOnly();
        }
        return false;
    }
    private interface IOAction<T> { T run(Path directory) throws IOException; }
    private <T> T locked(UUID projectId, IOAction<T> action) throws IOException {
        Path directory = root.resolve(projectId.toString()); ensureDirectories(root.getRoot(), directory);
        Path lock = directory.resolve("project.lock"); rejectLink(lock);
        try (FileChannel channel = FileChannel.open(lock, CREATE, WRITE, NOFOLLOW_LINKS)) {
            try (FileLock held = channel.tryLock()) {
                if (held == null) throw new IOException("Project is already being written");
                cleanInterruptedTemporaries(directory);
                return action.run(directory);
            } catch (OverlappingFileLockException e) { throw new IOException("Project is already being written", e); }
        }
    }
    private static void cleanInterruptedTemporaries(Path directory) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, ".write-*.tmp")) {
            int inspected = 0;
            for (Path file : files) {
                if (++inspected > 4096) throw new IOException("Too many interrupted project files");
                String name = file.getFileName().toString();
                if (name.matches("\\.write-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.tmp")
                        && Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)) Files.delete(file);
            }
        }
    }
    static void ensureDirectories(Path ancestor, Path target) throws IOException {
        Path current = ancestor;
        for (Path part : ancestor.relativize(target)) {
            current = current.resolve(part);
            if (!Files.exists(current, NOFOLLOW_LINKS)) try { Files.createDirectory(current); } catch (FileAlreadyExistsException ignored) { }
            if (Files.isSymbolicLink(current) || !Files.isDirectory(current, NOFOLLOW_LINKS)) throw new IOException("Project path contains a link or non-directory");
        }
    }
    private static void rejectLink(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || (Files.exists(path, NOFOLLOW_LINKS) && !Files.isRegularFile(path, NOFOLLOW_LINKS)))
            throw new IOException("Project file is a link or non-file");
    }
    static byte[] read(Path file, int limit) throws IOException {
        rejectLink(file);
        try (SeekableByteChannel channel = Files.newByteChannel(file, Set.of(READ, NOFOLLOW_LINKS))) {
            if (channel.size() > limit) throw new IOException("Project file exceeds byte limit");
            var out = new ByteArrayOutputStream(); ByteBuffer chunk = ByteBuffer.allocate(8192);
            int count;
            while ((count = channel.read(chunk)) != -1) {
                if (out.size() + count > limit) throw new IOException("Project file exceeds byte limit");
                out.write(chunk.array(), 0, count); chunk.clear();
            }
            return out.toByteArray();
        }
    }
    private void write(Path directory, String name, byte[] bytes) throws IOException {
        Path target = directory.resolve(name), previous = directory.resolve(name.replace(".json", ".previous.json"));
        rejectLink(target); rejectLink(previous);
        Path temporary = directory.resolve(".write-" + UUID.randomUUID() + ".tmp");
        try {
            syncNew(temporary, bytes); probe.at(WriteStage.TEMP_SYNCED, target);
            if (Files.exists(target, NOFOLLOW_LINKS)) {
                byte[] old = read(target, name.equals("project.json") ? DesignerJson.PROJECT_BYTES : DesignerJson.RECOVERY_BYTES);
                // Never rotate a corrupt current file over a valid previous checkpoint.
                boolean valid;
                try {
                    valid = switch (name) {
                        case "project.json" -> !ResearchDesignerProject.open(old).readOnly();
                        case "recovery.json" -> { decodeRecovery(old); yield true; }
                        default -> { decodeView(old); yield true; }
                    };
                } catch (IllegalArgumentException e) { valid = false; }
                if (valid) {
                    Path backupTemp = directory.resolve(".write-" + UUID.randomUUID() + ".tmp");
                    try { syncNew(backupTemp, old); replace(backupTemp, previous); } finally { Files.deleteIfExists(backupTemp); }
                }
            }
            probe.at(WriteStage.BACKUP_WRITTEN, target); probe.at(WriteStage.BEFORE_REPLACE, target);
            rejectLink(target); replace(temporary, target); syncDirectory(directory);
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void syncNew(Path path, byte[] bytes) throws IOException {
        try (FileChannel file = FileChannel.open(path, CREATE_NEW, WRITE, NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) file.write(buffer); file.force(true);
        }
    }
    private static void replace(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static void syncDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, READ)) { channel.force(true); }
        catch (IOException | UnsupportedOperationException ignored) { /* Not supported by every filesystem; the previous file remains available. */ }
    }
    public static byte[] encodeRecovery(Checkpoint checkpoint) {
        JsonObject payload = new JsonObject(); payload.add("project", checkpoint.project().json()); payload.addProperty("sequence", checkpoint.sequence());
        checkpoint.savedDigest().ifPresentOrElse(v -> payload.addProperty("saved_digest", v), () -> payload.add("saved_digest", JsonNull.INSTANCE));
        JsonArray pending = new JsonArray();
        for (var input : checkpoint.pendingInputs()) {
            JsonObject value = new JsonObject(); value.addProperty("object_id", input.key().objectId()); value.addProperty("pointer", input.key().pointer()); value.addProperty("text", input.text()); pending.add(value);
        }
        payload.add("pending_inputs", pending);
        JsonObject envelope = new JsonObject(); envelope.addProperty("format", 1);
        envelope.addProperty("checksum", DesignerJson.digest(DesignerJson.bytes(payload, DesignerJson.RECOVERY_BYTES))); envelope.add("payload", payload);
        return DesignerJson.bytes(envelope, DesignerJson.RECOVERY_BYTES);
    }
    public static Checkpoint decodeRecovery(byte[] bytes) {
        try { return parseRecovery(bytes); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Invalid recovery record", e); }
    }
    private static Checkpoint parseRecovery(byte[] bytes) {
        JsonObject envelope = DesignerJson.parse(bytes, DesignerJson.RECOVERY_BYTES);
        exact(envelope, Set.of("format", "checksum", "payload"));
        if (DesignerJson.integer(envelope.get("format")) != 1) throw new IllegalArgumentException("Unsupported recovery version");
        JsonObject payload = envelope.getAsJsonObject("payload"); exact(payload, Set.of("project", "sequence", "saved_digest", "pending_inputs"));
        if (!DesignerJson.digest(DesignerJson.bytes(payload, DesignerJson.RECOVERY_BYTES)).equals(DesignerJson.string(envelope.get("checksum")))) throw new IllegalArgumentException("Recovery checksum mismatch");
        var pending = new ArrayList<PendingInput>();
        for (var entry : payload.getAsJsonArray("pending_inputs")) {
            JsonObject p = entry.getAsJsonObject(); exact(p, Set.of("object_id", "pointer", "text"));
            pending.add(new PendingInput(new InputKey(DesignerJson.string(p.get("object_id")), DesignerJson.string(p.get("pointer"))), DesignerJson.string(p.get("text"))));
        }
        return new Checkpoint(ResearchDesignerProject.fromJson(payload.getAsJsonObject("project")), pending, DesignerJson.integer(payload.get("sequence")),
                payload.get("saved_digest").isJsonNull() ? Optional.empty() : Optional.of(DesignerJson.string(payload.get("saved_digest"))), Optional.empty());
    }
    private static byte[] encodeView(View view) {
        JsonObject v = new JsonObject(); v.addProperty("format", 1); v.addProperty("x", view.x()); v.addProperty("y", view.y()); v.addProperty("zoom", view.zoom());
        v.addProperty("domain", view.domain()); view.selection().ifPresentOrElse(id -> v.addProperty("selection", id), () -> v.add("selection", JsonNull.INSTANCE));
        return DesignerJson.bytes(v, 16 * 1024);
    }
    private static View decodeView(byte[] bytes) {
        try { return parseView(bytes); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Invalid view preferences", e); }
    }
    private static View parseView(byte[] bytes) {
        JsonObject v = DesignerJson.parse(bytes, 16 * 1024); exact(v, Set.of("format", "x", "y", "zoom", "domain", "selection"));
        if (DesignerJson.integer(v.get("format")) != 1) throw new IllegalArgumentException("Unsupported view version");
        return new View(v.get("x").getAsDouble(), v.get("y").getAsDouble(), v.get("zoom").getAsDouble(),
                v.get("selection").isJsonNull() ? Optional.empty() : Optional.of(DesignerJson.string(v.get("selection"))), DesignerJson.string(v.get("domain")));
    }
    private static void exact(JsonObject value, Set<String> fields) {
        if (value == null || !value.keySet().equals(fields)) throw new IllegalArgumentException("Malformed storage envelope");
    }
}
