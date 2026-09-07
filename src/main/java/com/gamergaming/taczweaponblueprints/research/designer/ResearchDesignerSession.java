package com.gamergaming.taczweaponblueprints.research.designer;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Thread-confined editing session. Checkpoints are immutable values suitable for IO handoff. */
public final class ResearchDesignerSession {
    public static final int MAX_HISTORY_ENTRIES = 100, MAX_PENDING_INPUTS = 1024;
    public static final long MAX_HISTORY_BYTES = 64L * 1024 * 1024, AUTOSAVE_INTERVAL_NANOS = 5_000_000_000L;
    public record InputKey(String objectId, String pointer) {
        public InputKey {
            if (objectId.contains(":")) ResearchDesignerProject.resourceId(objectId); else ResearchDesignerProject.editorId(objectId);
            if (pointer == null || pointer.length() > 256 || !pointer.startsWith("/") || pointer.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Invalid input field path");
        }
    }
    public record PendingInput(InputKey key, String text) {
        public PendingInput { Objects.requireNonNull(key); if (text == null || text.length() > 4096) throw new IllegalArgumentException("Input text exceeds limit"); }
    }
    public record Checkpoint(ResearchDesignerProject project, List<PendingInput> pendingInputs, long sequence, Optional<String> savedDigest, Optional<String> recoveryDigest) {
        public Checkpoint {
            Objects.requireNonNull(project); pendingInputs = List.copyOf(pendingInputs); Objects.requireNonNull(savedDigest);
            if (sequence < 0) throw new IllegalArgumentException("Invalid edit sequence");
            savedDigest.ifPresent(value -> { if (!value.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid saved digest"); });
            Objects.requireNonNull(recoveryDigest);
            recoveryDigest.ifPresent(value -> { if (!value.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid recovery digest"); });
            validatePending(pendingInputs);
        }
    }
    public record View(double x, double y, double zoom, Optional<String> selection, String domain) {
        public View {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(zoom) || zoom <= 0 || zoom > 100)
                throw new IllegalArgumentException("Invalid view coordinates");
            Objects.requireNonNull(selection); Objects.requireNonNull(domain);
            if (!Set.of("weapons", "ammo", "attachments").contains(domain)) throw new IllegalArgumentException("Invalid view domain");
        }
        public static View initial() { return new View(0, 0, 1, Optional.empty(), "weapons"); }
    }
    public record Problem(String code, String objectId, String pointer, int omittedCount) {
        public Problem(String code, String objectId, String pointer) { this(code, objectId, pointer, 0); }
    }
    private record State(ResearchDesignerProject project, List<PendingInput> pending) {
        State { pending = List.copyOf(pending); validatePending(pending); }
        long size() { return project.encodedSize() + pending.stream().mapToLong(p -> 2L * (p.text().length() + p.key().pointer().length() + p.key().objectId().length())).sum(); }
    }
    private record History(String label, State before, State after) { long size() { return before.size() + after.size(); } }
    private State state, gestureStart;
    private String gestureLabel;
    private View view = View.initial();
    private Optional<String> savedDigest, recoveryDigest = Optional.empty();
    private long sequence, lastCheckpointSequence = -1, lastStorageSequence = -1, lastAutosaveAttempt;
    private boolean attemptedAutosave;
    private final Deque<History> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final long historyBudget;

    public ResearchDesignerSession(ResearchDesignerProject project) { this(project, Optional.empty(), MAX_HISTORY_BYTES); }
    public ResearchDesignerSession(ResearchDesignerProject project, Optional<String> savedDigest, long historyBudget) {
        if (historyBudget < 0 || historyBudget > MAX_HISTORY_BYTES) throw new IllegalArgumentException("Invalid history budget");
        this.state = new State(project, List.of()); this.savedDigest = Objects.requireNonNull(savedDigest); this.historyBudget = historyBudget;
    }
    public static ResearchDesignerSession restore(Checkpoint checkpoint, Optional<String> actualSavedDigest, Optional<String> recoveryDigest) {
        var session = new ResearchDesignerSession(checkpoint.project(), actualSavedDigest, MAX_HISTORY_BYTES);
        session.state = new State(checkpoint.project(), checkpoint.pendingInputs()); session.sequence = checkpoint.sequence(); session.recoveryDigest = recoveryDigest; return session;
    }
    public ResearchDesignerProject project() { return state.project; }
    public List<PendingInput> pendingInputs() { return state.pending; }
    public long sequence() { return sequence; }
    public View view() { return view; }
    public void view(View value) { view = Objects.requireNonNull(value); }
    public boolean dirty() { return !state.pending.isEmpty() || savedDigest.filter(project().digest()::equals).isEmpty(); }
    public boolean canUndo() { return gestureStart == null && !undo.isEmpty(); }
    public boolean canRedo() { return gestureStart == null && !redo.isEmpty(); }
    public long historyBytes() { return undo.stream().mapToLong(History::size).sum() + redo.stream().mapToLong(History::size).sum(); }
    public Checkpoint checkpoint() { return new Checkpoint(project(), pendingInputs(), sequence, savedDigest, recoveryDigest); }
    public List<ResearchDesignerEdits.Reference> execute(String label, ResearchDesignerEdits.Edit edit) {
        var change = ResearchDesignerEdits.apply(project(), edit);
        change(label, new State(change.project(), state.pending)); return change.affected();
    }
    public void pending(String label, InputKey key, String text) {
        if (state.pending.stream().anyMatch(v -> v.key().equals(key) && v.text().equals(text))) return;
        var values = new ArrayList<>(state.pending); values.removeIf(v -> v.key().equals(key)); values.add(new PendingInput(key, text));
        change(label, new State(project(), values));
    }
    public void discardPending(InputKey key) {
        var values = new ArrayList<>(state.pending); values.removeIf(v -> v.key().equals(key)); change("Discard input", new State(project(), values));
    }
    /** Integer fields supply their runtime bounds; invalid text never replaces the last typed value. */
    public boolean integerInput(String label, InputKey key, String text, int minimum, int maximum,
                                java.util.function.IntFunction<ResearchDesignerEdits.Edit> edit) {
        if (minimum > maximum) throw new IllegalArgumentException("Invalid input bounds");
        int value;
        try {
            value = new java.math.BigDecimal(text).intValueExact();
            if (value < minimum || value > maximum) throw new ArithmeticException("Out of range");
        } catch (NumberFormatException | ArithmeticException invalid) { pending(label, key, text); return false; }
        resolvePending(label, key, edit.apply(value)); return true;
    }
    /** The field's validated source change and pending-text removal share a single undo step. */
    public void resolvePending(String label, InputKey key, ResearchDesignerEdits.Edit edit) {
        var change = ResearchDesignerEdits.apply(project(), edit);
        var values = new ArrayList<>(state.pending); values.removeIf(v -> v.key().equals(key)); change(label, new State(change.project(), values));
    }
    public void beginEdit(String label) {
        if (gestureStart != null) throw new IllegalStateException("An edit is already active");
        gestureStart = state; gestureLabel = Objects.requireNonNull(label);
    }
    public void commitEdit() {
        if (gestureStart == null) throw new IllegalStateException("No active edit");
        if (!gestureStart.equals(state)) remember(new History(gestureLabel, gestureStart, state));
        gestureStart = null; gestureLabel = null;
    }
    public void cancelEdit() {
        if (gestureStart == null) throw new IllegalStateException("No active edit");
        if (!gestureStart.equals(state)) { advance(); state = gestureStart; }
        gestureStart = null; gestureLabel = null;
    }
    public void undo() {
        if (!canUndo()) throw new IllegalStateException("Undo unavailable");
        advance(); var entry = undo.removeLast(); state = entry.before; redo.addLast(entry);
    }
    public void redo() {
        if (!canRedo()) throw new IllegalStateException("Redo unavailable");
        advance(); var entry = redo.removeLast(); state = entry.after; undo.addLast(entry);
    }
    private void change(String label, State next) {
        Objects.requireNonNull(label); if (next.equals(state)) return;
        if (!next.project.projectId().equals(project().projectId())) throw new IllegalArgumentException("Wrong project identity");
        State before = state; advance(); state = next;
        if (gestureStart == null) remember(new History(label, before, state));
    }
    private void advance() { sequence = Math.incrementExact(sequence); }
    private void remember(History entry) {
        redo.clear(); undo.addLast(entry);
        while (!undo.isEmpty() && (undo.size() > MAX_HISTORY_ENTRIES || historyBytes() > historyBudget)) undo.removeFirst();
    }
    public boolean claimAutosave(long nowNanos) {
        if (lastCheckpointSequence == sequence || (!dirty() && lastCheckpointSequence < 0 && recoveryDigest.isEmpty())) return false;
        if (attemptedAutosave && nowNanos - lastAutosaveAttempt < AUTOSAVE_INTERVAL_NANOS) return false;
        attemptedAutosave = true; lastAutosaveAttempt = nowNanos; return true;
    }
    public void checkpointWritten(Checkpoint checkpoint, String digest) {
        acceptReceipt(checkpoint, digest);
    }
    public void saved(Checkpoint checkpoint, String recoveryDigest) {
        if (acceptReceipt(checkpoint, recoveryDigest)) savedDigest = Optional.of(checkpoint.project().digest());
    }
    private boolean acceptReceipt(Checkpoint checkpoint, String digest) {
        sameProject(checkpoint);
        if (!digest.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid storage receipt");
        if (checkpoint.sequence() < lastStorageSequence || (!checkpoint.recoveryDigest().equals(recoveryDigest)
                && !recoveryDigest.filter(digest::equals).isPresent())) return false;
        recoveryDigest = Optional.of(digest); lastStorageSequence = checkpoint.sequence();
        if (checkpoint.sequence() == sequence) lastCheckpointSequence = sequence;
        return true;
    }
    private void sameProject(Checkpoint checkpoint) {
        if (!checkpoint.project().projectId().equals(project().projectId())) throw new IllegalArgumentException("Wrong project checkpoint");
    }
    /** Preliminary draft problems only; absence is not runtime validation or export authorization. */
    public List<Problem> problems(Set<String> availableBlueprints) {
        var problems = new ArrayList<Problem>();
        Set<String> declared = new HashSet<>(); project().draft().nodes().forEach(n -> n.blueprintId().ifPresent(declared::add));
        if (project().draft().nodes().isEmpty()) problems.add(new Problem("empty_tree", project().projectId().toString(), "/draft/nodes"));
        for (var n : project().draft().nodes()) {
            if (n.blueprintId().filter(availableBlueprints::contains).isEmpty()) problems.add(new Problem("missing_blueprint", n.id(), "/blueprint_id"));
            JsonObject placement = n.placement().json();
            for (String field : List.of("domain", "lane", "tier", "rank", "order"))
                if (!placement.has(field)) problems.add(new Problem("incomplete_placement", n.id(), "/placement/" + field));
            for (var group : n.requirements()) if (group.anyOf().isEmpty() || !availableBlueprints.containsAll(group.anyOf()) || !declared.containsAll(group.anyOf()))
                problems.add(new Problem("missing_requirement", group.id(), "/any_of"));
        }
        for (var pending : state.pending) problems.add(new Problem("pending_input", pending.key().objectId(), pending.key().pointer()));
        if (problems.size() > 1000) {
            var limited = new ArrayList<>(problems.subList(0, 999)); limited.add(new Problem("limit_exceeded", project().projectId().toString(), "/diagnostics", problems.size() - 999)); return List.copyOf(limited);
        }
        return List.copyOf(problems);
    }
    private static void validatePending(List<PendingInput> values) {
        if (values.size() > MAX_PENDING_INPUTS) throw new IllegalArgumentException("Too many pending inputs");
        var keys = new HashSet<InputKey>(); long bytes = 0;
        for (var value : values) {
            if (!keys.add(value.key())) throw new IllegalArgumentException("Duplicate pending field");
            bytes += value.text().getBytes(StandardCharsets.UTF_8).length;
        }
        if (bytes > 1024 * 1024) throw new IllegalArgumentException("Pending text exceeds byte limit");
    }
}
