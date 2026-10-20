package com.gamergaming.taczweaponblueprints.verification;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.neoforged.fml.ModList;

/** Rejects loose development output when a packaged-artifact run was requested. */
final class RuntimeIdentity {
    private static String verified;
    static synchronized String verify() {
        if (!Boolean.getBoolean("wrb.verifyPackaged")) return "development";
        if (verified != null) return verified;
        try {
            var path = ModList.get().getModFileById("taczweaponblueprints").getFile().getFilePath();
            if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".jar"))
                throw new IllegalStateException("Production addon must load from an unmodified JAR: " + path);
            var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
            if (!hash.equals(System.getProperty("wrb.expectedArtifactHash")))
                throw new IllegalStateException("Loaded addon JAR differs from the requested artifact");
            verified = hash;
            CoreVerification.LOG.info("WRB_PACKAGED loaded verified addon {}", hash);
            return hash;
        } catch (Exception error) { throw new IllegalStateException("Packaged artifact identity failed", error); }
    }
    static void addTo(JsonObject report) { report.addProperty("artifactSha256", verify()); }
}
