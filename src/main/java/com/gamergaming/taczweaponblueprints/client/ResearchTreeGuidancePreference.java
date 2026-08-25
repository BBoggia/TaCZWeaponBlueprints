package com.gamergaming.taczweaponblueprints.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Small client-only preference file for the dismissible Research Tree guide. */
public final class ResearchTreeGuidancePreference {
    private static final String FILE_NAME = "taczweaponblueprints-client.properties";
    private static final String DISMISSED_KEY = "research_tree_guidance_dismissed";

    private final Path file;
    private boolean loaded;
    private boolean dismissed;

    public ResearchTreeGuidancePreference(Path configDirectory) {
        if (configDirectory == null) {
            throw new IllegalArgumentException("Research Tree guidance config directory cannot be null");
        }
        file = configDirectory.resolve(FILE_NAME);
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
        } catch (IOException | IllegalArgumentException | SecurityException ignored) {
            dismissed = false;
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
}
