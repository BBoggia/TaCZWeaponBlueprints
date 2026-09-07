package com.gamergaming.taczweaponblueprints.acceptance;

import java.nio.file.*;
import com.google.gson.*;

/** Opt-in, local-only coordination; no commands are accepted from the network. */
public final class ResearchEconomyAcceptance {
    public static final boolean ENABLED = System.getProperty("tacz.economyQA.root") != null;
    public static final Path ROOT = Path.of(System.getProperty("tacz.economyQA.root", "."));
    public static final String ROLE = System.getProperty("tacz.economyQA.role", "Alice");
    public static final String PHASE = System.getProperty("tacz.economyQA.phase", "gameplay");
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private ResearchEconomyAcceptance() { }
    public static void check(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }
    public static void write(String name, String text) throws java.io.IOException {
        Files.createDirectories(ROOT);
        var path = ROOT.resolve(PHASE + "-" + name);
        var temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, text);
        Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    public static String read(String name) throws java.io.IOException {
        var path = ROOT.resolve(PHASE + "-" + name);
        return Files.exists(path) ? Files.readString(path) : "";
    }
    public static void artifactOrigin() {
        var expected = Path.of(System.getProperty("tacz.economyQA.artifact")).toAbsolutePath().normalize();
        var source = com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu.class
                .getResource("BlueprintRecyclerMenu.class").toString();
        check(source.contains(expected.toUri().getRawPath()) || source.contains(expected.toString()),
                "Runtime must load the candidate artifact, actual origin: " + source);
    }
}
