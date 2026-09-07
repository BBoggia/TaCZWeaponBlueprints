package com.gamergaming.taczweaponblueprints.client;

import java.nio.file.Path;
import java.util.function.Consumer;
import com.gamergaming.taczweaponblueprints.acceptance.AnalyzerCaptureEvidence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Shared capture receipt checks for the Analyzer's opt-in native probes. */
final class AnalyzerNativeCapture {
    private AnalyzerNativeCapture() { }
    static void grab(Path root, String filename, Runnable success, Consumer<Exception> failure) {
        var capture = AnalyzerCaptureEvidence.requireFresh(root, filename);
        var mc = Minecraft.getInstance();
        Screenshot.grab(root.toFile(), filename, mc.getMainRenderTarget(), message -> {
            try {
                AnalyzerCaptureEvidence.requireWritten(capture, message.getContents() instanceof TranslatableContents text
                        && text.getKey().equals("screenshot.success"));
                success.run();
            } catch (Exception error) { mc.execute(() -> failure.accept(error)); }
        });
    }
}
