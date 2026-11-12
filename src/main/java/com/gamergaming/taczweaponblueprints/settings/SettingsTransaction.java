package com.gamergaming.taczweaponblueprints.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.Supplier;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import net.peanuuutz.tomlkt.Toml;

/** Server-thread transaction. Validation and verified persistence precede any live gameplay changes. */
public final class SettingsTransaction {
    public enum Status {
        READY, APPLIED, NO_CHANGES, DENIED, STALE, INVALID, SAVE_FAILED, APPLIED_REFRESH_FAILED, UNAVAILABLE, TOO_LARGE, EXPIRED;
        public String translationKey() { return "settings.taczweaponblueprints.status." + name().toLowerCase(Locale.ROOT); }
    }
    public interface Store {
        String read() throws IOException;
        void save(BlueprintConfig candidate) throws IOException;
    }
    private final BlueprintConfig live;
    private final Store store;
    private final Supplier<String> policyRevision;
    private final Runnable publish;
    public SettingsTransaction(BlueprintConfig live, Store store, Supplier<String> policyRevision, Runnable publish) {
        this.live = live; this.store = store; this.policyRevision = policyRevision; this.publish = publish;
    }
    public static Store fzzyStore(BlueprintConfig config) {
        Path file = config.getDir().toPath().resolve(config.getName() + ".toml");
        return new Store() {
            public String read() throws IOException { return Files.exists(file) ? Files.readString(file) : ""; }
            public void save(BlueprintConfig candidate) {
                // Public save marks Fzzy's own file-watcher update, avoiding a second gameplay refresh.
                // It returns void and may swallow IO errors: the transaction MUST verify its result.
                candidate.save();
            }
        };
    }
    public String revision() throws IOException {
        return digest(FzzySettingsAdapter.serialize(live) + "\u0000" + store.read() + "\u0000" + policyRevision.get());
    }
    public SettingsSnapshot capture(UUID session, boolean editable, SettingsPresentation.Profile profile) throws IOException {
        return new SettingsSnapshot(session, revision(), editable, FzzySettingsAdapter.capture(live), profile);
    }
    public Status apply(SettingsSnapshot baseline, SettingsChangePlan plan, boolean authorized) {
        if (!authorized) return Status.DENIED;
        if (!baseline.session().equals(plan.session()) || !baseline.revision().equals(plan.revision())) return Status.STALE;
        try {
            if (!plan.revision().equals(revision())) return Status.STALE;
            if (plan.changes().isEmpty()) return Status.NO_CHANGES;
            BlueprintConfig candidate;
            try { candidate = FzzySettingsAdapter.prepare(live, plan.changes()); }
            catch (IllegalArgumentException | IllegalStateException error) { return Status.INVALID; }
            if (FzzySettingsAdapter.capture(live).equals(FzzySettingsAdapter.capture(candidate))) return Status.NO_CHANGES;
            if (!plan.revision().equals(revision())) return Status.STALE;
            store.save(candidate);
            // Compare parsed documents, including version and all fields; comments/whitespace are irrelevant.
            if (!Toml.Default.parseToTomlTable(store.read()).equals(
                    Toml.Default.parseToTomlTable(FzzySettingsAdapter.serialize(candidate)))) return Status.SAVE_FAILED;
            // Never call the update callback on a failed save or a read/cancel/no-op path.
            try {
                FzzySettingsAdapter.install(live, candidate, plan.changes().keySet());
                publish.run();
            } catch (RuntimeException error) {
                com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.LOGGER.error(
                        "Settings were saved but their runtime refresh failed; do not replay this edit", error);
                return Status.APPLIED_REFRESH_FAILED;
            }
            return Status.APPLIED;
        } catch (IOException | RuntimeException error) {
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.LOGGER.warn("Settings save could not be verified", error);
            return Status.SAVE_FAILED;
        }
    }
    private static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
