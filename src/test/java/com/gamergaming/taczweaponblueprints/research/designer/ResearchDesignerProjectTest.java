package com.gamergaming.taczweaponblueprints.research.designer;

import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.taczweaponblueprints.research.designer.ResearchDesignerEdits.*;
import com.gamergaming.taczweaponblueprints.research.designer.io.ResearchDesignerProjectStore;
import com.gamergaming.taczweaponblueprints.research.designer.ResearchDesignerSession.*;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class ResearchDesignerProjectTest {
    @TempDir Path game;
    static ResearchDesignerProject fixture(String id) {
        return ResearchDesignerProject.fromJson(ResearchDesignerContractTest.project(ResearchDesignerContractTest.named(id)));
    }
    static Stream<String> fixtures() { return ResearchDesignerContractTest.cases().map(c -> c.get("id").getAsString()); }
    static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    static String first(ResearchDesignerProject p) { return p.draft().nodes().get(0).id(); }
    Path dir(UUID id) { return game.resolve("taczweaponblueprints/designer/projects").resolve(id.toString()); }

    @ParameterizedTest @MethodSource("fixtures")
    void realReaderSaveAndReopenPreserveAllFoundationDrafts(String id) throws Exception {
        var p = fixture(id); var store = new ResearchDesignerProjectStore(game); var session = new ResearchDesignerSession(p);
        store.save(session); assertFalse(session.dirty());
        var loaded = store.load(p.projectId()); assertFalse(loaded.readOnly());
        assertEquals(p.json(), loaded.session(false).project().json());
        assertEquals(p.draft(), loaded.session(false).project().draft());
        assertEquals(p.projectId(), loaded.session(true).project().projectId());
    }
    @Test void createAndMutationCannotLeakMutableJson() {
        var p = ResearchDesignerProject.create("my_pack", "My tree"); var source = p.json(); source.addProperty("title", "Leaked");
        p.draft().profile().definition().json().addProperty("research_enabled", false);
        assertEquals("My tree", p.title()); assertTrue(p.draft().profile().definition().json().get("research_enabled").getAsBoolean());
        assertThrows(UnsupportedOperationException.class, () -> p.draft().nodes().clear());
    }
    @Test void unfinishedAndMissingNodesBlockCompilationPreparation() {
        var blank = new ResearchDesignerSession(fixture("blank")); assertEquals("empty_tree", blank.problems(Set.of()).get(0).code());
        var unassigned = new ResearchDesignerSession(fixture("unassigned")); assertTrue(unassigned.problems(Set.of()).stream().anyMatch(p -> p.code().equals("missing_blueprint")));
        var missing = new ResearchDesignerSession(fixture("missing")); assertFalse(missing.problems(Set.of("designer_fixture:root")).isEmpty());
        assertFalse(ResearchDesignerProject.open(missing.project().bytes()).readOnly());
    }
    @ParameterizedTest @ValueSource(strings={"{\"format\":1,\"format\":1}", "{\"format\":1} {}", "{format:1}", "{\"format\":NaN}", "{\"format\":1e999}", "{\"format\":1,}", "{\"format\":1/*comment*/}"})
    void strictReaderRejectsMalformedDocuments(String value) { assertThrows(IllegalArgumentException.class, () -> ResearchDesignerProject.open(bytes(value))); }
    @Test void rejectsInvalidUtf8SurrogatesDepthAndByteLimits() {
        assertThrows(IllegalArgumentException.class, () -> ResearchDesignerProject.open(new byte[]{(byte)0xC3, 0x28}));
        assertThrows(IllegalArgumentException.class, () -> DesignerJson.parse(bytes("{\"x\":\"\\ud800\"}"), DesignerJson.PROJECT_BYTES));
        assertThrows(IllegalArgumentException.class, () -> DesignerJson.parse(bytes("{\"x\":" + "[".repeat(70) + "0" + "]".repeat(70) + "}"), DesignerJson.PROJECT_BYTES));
        assertThrows(IllegalArgumentException.class, () -> ResearchDesignerProject.open(new byte[DesignerJson.PROJECT_BYTES + 1]));
    }
    @Test void duplicateIdentitiesAndFractionalCountsCannotBeSilentlyNormalized() {
        var p = fixture("single").json(); var nodes = p.getAsJsonObject("draft").getAsJsonArray("nodes"); nodes.add(nodes.get(0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> ResearchDesignerProject.fromJson(p));
        var n = fixture("single").json(); n.getAsJsonObject("draft").getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("policy").getAsJsonObject("research_cost").addProperty("points", 1.5);
        assertThrows(IllegalArgumentException.class, () -> ResearchDesignerProject.fromJson(n));
        var g = fixture("split_merge").json(); var all = g.getAsJsonObject("draft").getAsJsonArray("nodes");
        all.get(1).getAsJsonObject().getAsJsonArray("requirements").get(0).getAsJsonObject().addProperty("id", all.get(0).getAsJsonObject().get("id").getAsString());
        assertThrows(IllegalArgumentException.class, () -> ResearchDesignerProject.fromJson(g));
    }
    @Test void unsupportedProjectExtensionsAndNestedFieldsOpenReadOnlyWithoutLoss() {
        var future = fixture("single").json(); future.addProperty("format", 2); future.add("future_content", json("{\"unknown\":true}"));
        var opened = ResearchDesignerProject.open(DesignerJson.bytes(future, DesignerJson.PROJECT_BYTES));
        assertTrue(opened.readOnly()); assertEquals(future, opened.source().json());
        var extension = fixture("single").json(); extension.getAsJsonObject("extensions").add("future:rule", json("{\"version\":1,\"data\":{}}"));
        assertTrue(ResearchDesignerProject.open(DesignerJson.bytes(extension, DesignerJson.PROJECT_BYTES)).readOnly());
        var nested = fixture("single").json(); nested.getAsJsonObject("draft").getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("policy").getAsJsonObject("research_cost").addProperty("future_cost", 4);
        assertTrue(ResearchDesignerProject.open(DesignerJson.bytes(nested, DesignerJson.PROJECT_BYTES)).readOnly());
    }
    @Test void compoundEditUndoRedoRestoresEveryOwnedIdentityAndField() {
        var p = fixture("nested"); var s = new ResearchDesignerSession(p); String id = first(p);
        s.execute("Move and price", batch(List.of(membership(id, Optional.of("designer_fixture:left_branch")),
                placement(id, json("{\"domain\":\"ammo\",\"lane\":\"designer_fixture:ammo\",\"tier\":\"starter\",\"rank\":4,\"order\":6}")),
                policy(id, json("{\"research_cost\":{\"points\":9},\"visibility\":\"full\"}")))));
        var changed = s.project(); assertNotEquals(p, changed); s.undo(); assertEquals(p, s.project()); assertFalse(s.canUndo());
        s.redo(); assertEquals(changed, s.project()); assertTrue(s.sequence() >= 3);
        assertEquals(id, first(s.project()));
    }
    @Test void deletionReportsReferencesAndLeavesDependentsUnchanged() {
        var p = fixture("split_merge"); var s = new ResearchDesignerSession(p); String removed = p.draft().nodes().get(1).id();
        var endBefore = p.draft().nodes().get(3); var refs = s.execute("Delete", removeNode(removed));
        assertFalse(refs.isEmpty()); assertEquals(endBefore, s.project().draft().nodes().get(2));
        s.undo(); assertEquals(p, s.project());
    }
    @Test void branchUngroupAndDeleteContentsAreSeparateUndoableOperations() {
        var p = fixture("nested"); var s = new ResearchDesignerSession(p);
        s.execute("Ungroup", removeBranch("designer_fixture:foundation", BranchRemoval.UNGROUP));
        assertEquals(p.draft().nodes().size(), s.project().draft().nodes().size());
        assertTrue(s.project().draft().nodes().get(0).branchId().isEmpty());
        assertFalse(s.project().draft().tree().definition().json().getAsJsonArray("branches").get(0).getAsJsonObject().has("parent"));
        s.undo(); assertEquals(p, s.project());
        s.execute("Delete branch and contents", removeBranch("designer_fixture:foundation", BranchRemoval.DELETE_SUBTREE));
        assertTrue(s.project().draft().nodes().isEmpty()); s.undo(); assertEquals(p, s.project());
    }
    @Test void requirementChangesAndAddingNodesAreAtomicAndReversible() {
        var p = fixture("single"); var s = new ResearchDesignerSession(p); String id = first(p);
        var placeholder = new ResearchDesignerDraft.Node("placeholder", Optional.empty(), new DesignerJson.ObjectValue(new JsonObject()), Optional.empty(), new DesignerJson.ObjectValue(new JsonObject()), List.of());
        s.execute("Add", batch(List.of(addNode(placeholder), addRequirement(id, new ResearchDesignerDraft.Requirement("group", List.of("missing:bp"))))));
        s.execute("Resolve group", replaceRequirement(id, new ResearchDesignerDraft.Requirement("group", List.of("other:bp"))));
        s.execute("Remove group", removeRequirement(id, "group")); s.undo(); s.undo(); s.undo(); assertEquals(p, s.project());
        assertThrows(IllegalArgumentException.class, () -> s.execute("Broken batch", batch(List.of(title("Before failure"), removeNode("no-such-node")))));
        assertEquals(p, s.project()); assertFalse(s.canUndo());
    }
    @Test void dragCoalescesAndCancellationPreservesRedo() {
        var p = fixture("single"); var s = new ResearchDesignerSession(p);
        s.execute("First", title("First")); s.undo(); assertTrue(s.canRedo());
        s.beginEdit("Drag"); for (int i=0;i<20;i++) s.execute("Update", title("Update " + i)); s.cancelEdit();
        assertEquals(p, s.project()); assertTrue(s.canRedo());
        s.beginEdit("Continuous edit"); s.execute("One", title("One")); s.execute("Two", title("Two")); s.commitEdit();
        assertFalse(s.canRedo()); s.undo(); assertEquals(p, s.project()); assertFalse(s.canUndo());
    }
    @Test void pendingTextIsPreservedAndResolvesWithOneUndoStep() throws Exception {
        var p = fixture("single"); var s = new ResearchDesignerSession(p); var key = new InputKey(first(p), "/policy/research_cost/points");
        s.pending("Cost text", key, "1.2.3"); assertEquals(p, s.project()); assertTrue(s.problems(Set.of("designer_fixture:root")).stream().anyMatch(v -> v.code().equals("pending_input")));
        var store = new ResearchDesignerProjectStore(game); store.save(s);
        var recovered = store.load(p.projectId()).session(true); assertEquals(s.pendingInputs(), recovered.pendingInputs()); assertTrue(recovered.dirty());
        recovered.resolvePending("Set cost", key, policy(first(p), json("{\"research_cost\":{\"points\":12}}")));
        assertTrue(recovered.pendingInputs().isEmpty()); recovered.undo(); assertEquals("1.2.3", recovered.pendingInputs().get(0).text()); assertEquals(p, recovered.project());
    }
    @Test void navigationDoesNotDirtySourceOrInvalidateEditSequence() throws Exception {
        var p = fixture("single"); var s = new ResearchDesignerSession(p); var store = new ResearchDesignerProjectStore(game); store.save(s);
        s.view(new View(123, -44, 0.4, Optional.of(first(p)), "weapons")); store.saveView(p.projectId(), s.view());
        assertEquals(0, s.sequence()); assertFalse(s.dirty()); assertFalse(s.canUndo());
        assertEquals(s.view(), store.load(p.projectId()).view()); assertEquals(p, s.project());
    }
    @Test void historyAndAutosavesAreBoundedAndFreshAfterUndo() throws Exception {
        var p = fixture("single"); var s = new ResearchDesignerSession(p, Optional.empty(), 10_000); var store = new ResearchDesignerProjectStore(game);
        for(int i=0;i<40;i++) s.execute("Title", title("Title " + i)); assertTrue(s.historyBytes() <= 10_000);
        assertTrue(store.autosave(s, 0)); s.execute("Later", title("Later")); assertFalse(store.autosave(s, 1));
        assertTrue(store.autosave(s, ResearchDesignerSession.AUTOSAVE_INTERVAL_NANOS));
        assertEquals("Later", store.load(p.projectId()).session(true).project().title());
        s.undo(); assertTrue(store.autosave(s, ResearchDesignerSession.AUTOSAVE_INTERVAL_NANOS * 2));
    }
    @ParameterizedTest @EnumSource(ResearchDesignerProjectStore.WriteStage.class)
    void failedSaveKeepsOldDocumentAndNewRecoverableWork(ResearchDesignerProjectStore.WriteStage stage) throws Exception {
        var p = fixture("single"); var s = new ResearchDesignerSession(p); var store = new ResearchDesignerProjectStore(game); store.save(s); s.execute("New title", title("New title"));
        var failing = new ResearchDesignerProjectStore(game, (point, target) -> { if(point == stage && target.getFileName().toString().equals("project.json")) throw new java.io.IOException("Injected interruption"); });
        assertThrows(java.io.IOException.class, () -> failing.save(s)); assertTrue(s.dirty());
        var loaded = store.load(p.projectId()); assertEquals(p, loaded.session(false).project()); assertEquals("New title", loaded.session(true).project().title());
        store.save(s); assertFalse(s.dirty()); assertEquals("New title", store.load(p.projectId()).session(false).project().title());
        try (var files = Files.list(dir(p.projectId()))) { assertTrue(files.noneMatch(f -> f.getFileName().toString().endsWith(".tmp"))); }
    }
    @ParameterizedTest @EnumSource(ResearchDesignerProjectStore.WriteStage.class)
    void asynchronousSaveFailureReceiptAllowsRetryWithoutLosingNewerEdits(ResearchDesignerProjectStore.WriteStage stage) throws Exception {
        var p = fixture("single"); var session = new ResearchDesignerSession(p);
        var store = new ResearchDesignerProjectStore(game); store.save(session);
        session.execute("First edit", title("First edit"));
        var checkpoint = session.checkpoint();
        var failing = new ResearchDesignerProjectStore(game, (point, target) -> {
            if (point == stage && target.getFileName().toString().equals("project.json"))
                throw new java.io.IOException("Injected project replacement failure");
        });
        var failure = assertThrows(ResearchDesignerProjectStore.SaveFailure.class, () -> failing.save(checkpoint));
        // The UI can continue editing while the immutable checkpoint is being written.
        session.execute("Later edit", title("Later edit"));
        session.checkpointWritten(checkpoint, failure.recoveryDigest());
        assertTrue(session.dirty());
        assertEquals("First edit", store.load(p.projectId()).session(true).project().title());
        store.save(session);
        assertFalse(session.dirty());
        assertEquals("Later edit", store.load(p.projectId()).session(false).project().title());
    }
    @Test void failedRecoveryWriteDoesNotIssueAStorageReceipt() throws Exception {
        var p = fixture("single"); var session = new ResearchDesignerSession(p);
        var store = new ResearchDesignerProjectStore(game); store.save(session);
        session.execute("Edit", title("Edit")); var checkpoint = session.checkpoint();
        var failing = new ResearchDesignerProjectStore(game, (stage, target) -> {
            if (target.getFileName().toString().equals("recovery.json")) throw new java.io.IOException("Disk unavailable");
        });
        var failure = assertThrows(java.io.IOException.class, () -> failing.save(checkpoint));
        assertFalse(failure instanceof ResearchDesignerProjectStore.SaveFailure);
        assertEquals(checkpoint.recoveryDigest(), session.checkpoint().recoveryDigest());
        store.save(session); assertFalse(session.dirty());
    }
    @Test void truncatedCurrentAndChecksumFailureFallBackToPreviousFiles() throws Exception {
        var p = fixture("single"); var s = new ResearchDesignerSession(p); var store = new ResearchDesignerProjectStore(game); store.save(s);
        s.execute("New", title("New")); store.save(s);
        Files.writeString(dir(p.projectId()).resolve("project.json"), "{broken");
        var loaded = store.load(p.projectId()); assertEquals(p, loaded.session(false).project()); assertEquals("New", loaded.session(true).project().title());
        Files.writeString(dir(p.projectId()).resolve("recovery.json"), "{\"format\":1,\"checksum\":\"wrong\",\"payload\":{}}");
        assertEquals(p, store.load(p.projectId()).session(true).project());
    }
    @Test void newerFormatDoesNotFallBackOrAllowWrites() throws Exception {
        var p = fixture("single"); var s = new ResearchDesignerSession(p); var store = new ResearchDesignerProjectStore(game); store.save(s); store.save(s);
        var future = p.json(); future.addProperty("format", 99); byte[] preserved = DesignerJson.bytes(future, DesignerJson.PROJECT_BYTES);
        Files.write(dir(p.projectId()).resolve("project.json"), preserved);
        assertTrue(store.load(p.projectId()).readOnly()); assertThrows(java.io.IOException.class, () -> store.save(s));
        assertArrayEquals(preserved, Files.readAllBytes(dir(p.projectId()).resolve("project.json")));
    }
    @Test void saveAsKeepsBlueprintsAndPendingTextButChangesOnlyProjectIdentity() throws Exception {
        var p = fixture("single"); var s = new ResearchDesignerSession(p); var store = new ResearchDesignerProjectStore(game);
        s.pending("Title input", new InputKey(p.projectId().toString(), "/title"), "");
        var copy = store.saveAs(s); assertNotEquals(p.projectId(), copy.project().projectId()); assertEquals(p.draft(), copy.project().draft());
        assertEquals(copy.project().projectId().toString(), copy.pendingInputs().get(0).key().objectId());
        assertFalse(Files.exists(dir(p.projectId()))); assertEquals(copy.project(), store.load(copy.project().projectId()).session(true).project());
    }
    @Test void staleWriterCannotOverwriteNewerSavedProject() throws Exception {
        var p = fixture("single"); var store = new ResearchDesignerProjectStore(game); var first = new ResearchDesignerSession(p); store.save(first);
        var other = store.load(p.projectId()).session(false);
        first.execute("First", title("First")); store.save(first); other.execute("Second", title("Second"));
        assertThrows(java.io.IOException.class, () -> store.save(other)); assertEquals("First", store.load(p.projectId()).session(false).project().title());
    }
    @Test void linksAndNonProjectFilesCannotBecomeWriteTargets() throws Exception {
        var p = fixture("single"); var store = new ResearchDesignerProjectStore(game); var outside = game.resolve("outside"); Files.createDirectory(outside);
        Files.createSymbolicLink(dir(p.projectId()), outside);
        assertThrows(java.io.IOException.class, () -> store.save(new ResearchDesignerSession(p))); try (var files = Files.list(outside)) { assertEquals(0, files.count()); }
        Files.delete(dir(p.projectId())); Files.createDirectory(dir(p.projectId()));
        var sentinel = game.resolve("sentinel"); Files.writeString(sentinel, "preserve"); Files.createSymbolicLink(dir(p.projectId()).resolve("project.json"), sentinel);
        assertThrows(java.io.IOException.class, () -> store.save(new ResearchDesignerSession(p))); assertEquals("preserve", Files.readString(sentinel));
    }
    @Test void corruptedRecoveryEnvelopeNeverEscapesAsUnexpectedRuntimeFailure() {
        for (String malformed : List.of("{\"format\":1,\"checksum\":false,\"payload\":[]}", "{\"format\":1,\"checksum\":\"x\",\"payload\":null}"))
            assertThrows(IllegalArgumentException.class, () -> ResearchDesignerProjectStore.decodeRecovery(bytes(malformed)));
    }

    @Test void staleAutosaveCannotOverwriteAnotherSessionsRecovery() throws Exception {
        var p = fixture("single"); var store = new ResearchDesignerProjectStore(game); var one = new ResearchDesignerSession(p); store.save(one);
        var two = store.load(p.projectId()).session(false);
        one.execute("First draft", title("First draft")); two.execute("Second draft", title("Second draft"));
        assertTrue(store.autosave(one, 0));
        assertThrows(java.io.IOException.class, () -> store.autosave(two, 0));
        assertEquals("First draft", store.load(p.projectId()).session(true).project().title());
        assertEquals("Second draft", two.project().title());
    }
    @Test void integerInputPreservesUnrelatedRulesAndInvalidText() {
        var p = fixture("single"); var s = new ResearchDesignerSession(p); String id = first(p);
        s.execute("Existing policy", policy(id, json("{\"visibility\":\"full\",\"recycling_value\":7,\"research_cost\":{\"points\":2,\"ingredients\":[{\"items\":[\"minecraft:paper\"],\"count\":4}]}}")));
        var original = s.project(); var key = new InputKey(id, "/policy/research_cost/points");
        java.util.function.IntFunction<Edit> edit = value -> nodeField(id, key.pointer(), Optional.of(new JsonPrimitive(value)));
        for (String text : List.of("", "1.25", "no", "2147483648", "-1")) {
            assertFalse(s.integerInput("Cost", key, text, 0, 1000, edit)); assertEquals(original, s.project());
        }
        assertTrue(s.integerInput("Cost", key, "12", 0, 1000, edit)); assertTrue(s.pendingInputs().isEmpty());
        JsonObject after = s.project().draft().nodes().get(0).policy().json(); assertEquals(7, after.get("recycling_value").getAsInt());
        assertEquals(4, after.getAsJsonObject("research_cost").getAsJsonArray("ingredients").get(0).getAsJsonObject().get("count").getAsInt());
        s.undo(); assertEquals(original, s.project()); assertEquals("-1", s.pendingInputs().get(0).text());
    }
    @Test void futureRecoveryCannotBeOverwrittenByOlderEditor() throws Exception {
        var p = fixture("single"); var s = new ResearchDesignerSession(p); var store = new ResearchDesignerProjectStore(game); store.save(s);
        Path file = dir(p.projectId()).resolve("recovery.json"); String future = "{\"format\":2,\"future\":true}"; Files.writeString(file, future);
        assertTrue(store.load(p.projectId()).readOnly()); assertThrows(java.io.IOException.class, () -> store.save(s)); assertEquals(future, Files.readString(file));
    }
    @Test void pendingAndIdentityLimitsAreEnforcedBeforeHistoryChanges() {
        var s = new ResearchDesignerSession(fixture("single"));
        assertThrows(IllegalArgumentException.class, () -> s.pending("Oversized", new InputKey(first(s.project()), "/field"), "x".repeat(4097)));
        assertEquals(0, s.sequence()); assertFalse(s.canUndo());
        var duplicate = new PendingInput(new InputKey(first(s.project()), "/field"), "x");
        assertThrows(IllegalArgumentException.class, () -> new Checkpoint(s.project(), List.of(duplicate, duplicate), 0, Optional.empty(), Optional.empty()));
        var p = fixture("single").json(); p.addProperty("project_id", "../../outside");
        assertThrows(IllegalArgumentException.class, () -> ResearchDesignerProject.fromJson(p));
        assertThrows(IllegalArgumentException.class, () -> new View(Double.NaN, 0, 1, Optional.empty(), "weapons"));
    }
    @Test void largeDraftRecoveryAndHistoryStayWithinTheDocumentBudgets() throws Exception {
        var p = fixture("large_addon"); var s = new ResearchDesignerSession(p); var store = new ResearchDesignerProjectStore(game);
        long started = System.nanoTime();
        for (int i=0;i<25;i++) s.execute("Title", title("Large draft " + i));
        long edited = System.nanoTime(); store.save(s); long saved = System.nanoTime();
        var loaded = store.load(p.projectId()).session(true); long reopened = System.nanoTime();
        assertEquals(512, loaded.project().draft().nodes().size()); assertEquals(s.project(), loaded.project());
        assertTrue(s.historyBytes() <= ResearchDesignerSession.MAX_HISTORY_BYTES);
        assertTrue(Files.size(dir(p.projectId()).resolve("recovery.json")) <= DesignerJson.RECOVERY_BYTES);
        System.out.printf(java.util.Locale.ROOT, "Designer 512-node baseline: 25 edits %.3f ms, save %.3f ms, reopen %.3f ms; serialized history %d bytes%n",
                (edited-started)/1e6, (saved-edited)/1e6, (reopened-saved)/1e6, s.historyBytes());
    }

    @Test void autosaveAfterUndoToSavedStateDoesNotResurrectUndoneEdits() throws Exception {
        var p = fixture("single"); var s = new ResearchDesignerSession(p); var store = new ResearchDesignerProjectStore(game); store.save(s);
        s.execute("Discarded", title("Discarded")); assertTrue(store.autosave(s, 0));
        s.undo(); assertFalse(s.dirty()); assertTrue(store.autosave(s, ResearchDesignerSession.AUTOSAVE_INTERVAL_NANOS));
        assertEquals(p, store.load(p.projectId()).session(true).project());
    }
    @Test void partialSourceAndSupportingEconomyDefinitionsSurviveFieldEdits() throws Exception {
        var p = fixture("single").json(); JsonArray supporting = p.getAsJsonArray("supporting_resources");
        Path resources = Path.of("src/main/resources/data/taczweaponblueprints/taczweaponblueprints");
        for (String file : List.of("research_data/note.json", "research_data/report.json", "research_data/dossier.json", "analysis_reconstruction/research_data.json")) {
            JsonObject entry = new JsonObject(); entry.addProperty("kind", file.substring(0, file.indexOf('/'))); entry.addProperty("id", "taczweaponblueprints:" + file.substring(file.indexOf('/')+1, file.length()-5));
            entry.add("definition", JsonParser.parseString(Files.readString(resources.resolve(file)))); supporting.add(entry);
        }
        var s = new ResearchDesignerSession(ResearchDesignerProject.fromJson(p));
        s.execute("Price", nodeField(first(s.project()), "/policy/research_cost/points", Optional.of(new JsonPrimitive(15))));
        var store = new ResearchDesignerProjectStore(game); store.save(s);
        assertEquals(supporting, store.load(s.project().projectId()).session(false).project().json().getAsJsonArray("supporting_resources"));
    }
    @Test void rootLinksOversizedFilesAndAbandonedTempsAreHandled() throws Exception {
        var store = new ResearchDesignerProjectStore(game); var p = fixture("single"); var s = new ResearchDesignerSession(p); store.save(s);
        Path temp = dir(p.projectId()).resolve(".write-" + UUID.randomUUID() + ".tmp"); Files.writeString(temp, "interrupted");
        Path unrelated = dir(p.projectId()).resolve(".write-my-notes.tmp"); Files.writeString(unrelated, "keep");
        store.load(p.projectId()); assertFalse(Files.exists(temp)); assertTrue(Files.exists(unrelated));
        Files.write(dir(p.projectId()).resolve("project.json"), new byte[DesignerJson.PROJECT_BYTES+1]);
        assertTrue(store.load(p.projectId()).document().isEmpty()); assertThrows(java.io.IOException.class, () -> store.save(s));
        Path other = game.resolve("other-game"); Files.createDirectory(other); Files.createSymbolicLink(other.resolve("taczweaponblueprints"), game);
        assertThrows(java.io.IOException.class, () -> new ResearchDesignerProjectStore(other));
    }
    @Test void projectAndRecoveryLimitsAgreeWithTheFoundationContract() {
        JsonObject contract = ResearchDesignerContractTest.read(ResearchDesignerContractTest.CONTRACTS.resolve("contract.json"));
        var limits = contract.getAsJsonObject("limits");
        assertEquals(limits.get("project_bytes").getAsInt(), DesignerJson.PROJECT_BYTES);
        assertEquals(limits.get("json_depth").getAsInt(), DesignerJson.MAX_DEPTH);
        assertEquals(limits.get("draft_nodes").getAsInt(), ResearchDesignerProject.MAX_NODES);
        assertEquals(limits.get("draft_branches").getAsInt(), ResearchDesignerProject.MAX_BRANCHES);
        assertEquals(limits.get("requirement_groups").getAsInt(), ResearchDesignerProject.MAX_GROUPS);
    }

    @Test void lateStorageReceiptCannotRegressTheSavedBaseline() throws Exception {
        var s = new ResearchDesignerSession(fixture("single")); var store = new ResearchDesignerProjectStore(game);
        var first = s.checkpoint(); String receipt = store.save(first); s.saved(first, receipt);
        s.execute("Later", title("Later")); store.save(s); assertFalse(s.dirty());
        s.saved(first, receipt); assertFalse(s.dirty()); store.save(s);
        assertEquals("Later", store.load(s.project().projectId()).session(false).project().title());
    }
}
