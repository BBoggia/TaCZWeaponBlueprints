package com.gamergaming.taczweaponblueprints.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import net.minecraftforge.fml.loading.FMLPaths;

/** Small client-only preference file for durable Research Tree presentation choices. */
public final class ResearchTreeGuidancePreference {
    private static final String FILE_NAME = "taczweaponblueprints-client.properties";
    private static final String DISMISSED_KEY = "research_tree_guidance_dismissed";
    private static final String RAIL_PINNED_KEY = "research_tree_rail_pinned";
    private static final String ONBOARDING_DISMISSED_KEY = "onboarding_dismissed";
    private static final String ONBOARDING_HINT_SHOWN_KEY = "onboarding_hint_shown";

    private final Path file;
    private boolean loaded;
    private boolean dismissed;
    private boolean railPinned;
    private boolean onboardingDismissed;
    private boolean onboardingHintShown;

    public ResearchTreeGuidancePreference(Path configDirectory) {
        if (configDirectory == null) {
            throw new IllegalArgumentException("Research Tree guidance config directory cannot be null");
        }
        file = configDirectory.resolve(FILE_NAME);
    }

    /** Shared client instance prevents separate screens from holding stale preference copies. */
    public static ResearchTreeGuidancePreference client() {
        return ClientHolder.INSTANCE;
    }

    public synchronized boolean shouldShow() {
        load();
        return !dismissed;
    }

    /** Updates memory first; an unavailable config directory never traps the player in the guide. */
    public synchronized void dismiss() {
        load();
        dismissed = true;
        save();
    }

    public synchronized boolean railPinned() {
        load();
        return railPinned;
    }

    /** Updates memory first so a failed disk write never makes the control feel unresponsive. */
    public synchronized void setRailPinned(boolean pinned) {
        load();
        railPinned = pinned;
        save();
    }

    public synchronized boolean shouldShowOnboarding() {
        load();
        return !onboardingDismissed;
    }

    /** Returns true once per installation profile and persists before presentation. */
    public synchronized boolean claimOnboardingHint() {
        load();
        if (onboardingDismissed || onboardingHintShown) {
            return false;
        }
        onboardingHintShown = true;
        save();
        return true;
    }

    /** Dismisses automatic onboarding while leaving the Journal's Help button available. */
    public synchronized void dismissOnboarding() {
        load();
        onboardingDismissed = true;
        onboardingHintShown = true;
        save();
    }

    Path file() {
        return file;
    }

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (!Files.isRegularFile(file)) {
                return;
            }
            Properties properties = readProperties();
            dismissed = Boolean.parseBoolean(properties.getProperty(DISMISSED_KEY, "false"));
            railPinned = Boolean.parseBoolean(properties.getProperty(RAIL_PINNED_KEY, "false"));
            onboardingDismissed = Boolean.parseBoolean(
                    properties.getProperty(ONBOARDING_DISMISSED_KEY, "false"));
            onboardingHintShown = Boolean.parseBoolean(
                    properties.getProperty(ONBOARDING_HINT_SHOWN_KEY, "false"));
        } catch (IOException | IllegalArgumentException | SecurityException ignored) {
            dismissed = false;
            railPinned = false;
            onboardingDismissed = false;
            onboardingHintShown = false;
        }
    }

    private void save() {
        Path temporary = null;
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Properties properties = Files.isRegularFile(file)
                    ? readProperties()
                    : new Properties();
            properties.setProperty(DISMISSED_KEY, Boolean.toString(dismissed));
            properties.setProperty(RAIL_PINNED_KEY, Boolean.toString(railPinned));
            properties.setProperty(
                    ONBOARDING_DISMISSED_KEY, Boolean.toString(onboardingDismissed));
            properties.setProperty(
                    ONBOARDING_HINT_SHOWN_KEY, Boolean.toString(onboardingHintShown));
            Path temporaryDirectory = parent == null ? file.toAbsolutePath().getParent() : parent;
            temporary = Files.createTempFile(
                    temporaryDirectory,
                    file.getFileName().toString() + ".",
                    ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                properties.store(writer, "TaCZ Weapon Blueprints client preferences");
            }
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | IllegalArgumentException | SecurityException ignored) {
            try {
                if (temporary != null) {
                    Files.deleteIfExists(temporary);
                }
            } catch (IOException | SecurityException ignoredCleanup) {
                // The in-memory dismissal still prevents a blocking guidance loop this session.
            }
        }
    }

    private Properties readProperties() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        }
        return properties;
    }

    private static final class ClientHolder {
        private static final ResearchTreeGuidancePreference INSTANCE =
                new ResearchTreeGuidancePreference(FMLPaths.CONFIGDIR.get());

        private ClientHolder() {
        }
    }
}
