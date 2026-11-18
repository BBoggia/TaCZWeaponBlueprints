package com.gamergaming.taczweaponblueprints.client;

import java.util.Optional;
import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import com.gamergaming.taczweaponblueprints.progression.ResearchRouteFingerprint;
import net.minecraft.resources.ResourceLocation;

/** A hold confirms one quoted purchase and can activate only once before release. */
final class ResearchPurchaseConfirmation {
    record Intent(ResourceLocation target, ResearchRouteFingerprint quote) {
        static Optional<Intent> from(ResearchSelectionPreview preview) {
            if (preview == null || !preview.researchable() || preview.blueprintId().isEmpty()) return Optional.empty();
            return preview.routeFingerprint().map(quote -> new Intent(preview.blueprintId().orElseThrow(), quote));
        }
        boolean matches(ResearchSelectionPreview preview) { return from(preview).filter(this::equals).isPresent(); }
    }
    private final ResearchTreeHoldActivationController hold = new ResearchTreeHoldActivationController();
    private Intent intent;
    private boolean pressed, byKey;

    void begin(ResearchSelectionPreview preview, boolean byKey, long now, int duration) {
        if (pressed) return;
        pressed = true;
        this.byKey = byKey;
        intent = Intent.from(preview).orElse(null);
        if (intent != null) hold.begin(intent.target(), now, duration);
    }
    Optional<Intent> advance(ResearchSelectionPreview preview, long now) {
        if (intent == null) return Optional.empty();
        if (!intent.matches(preview)) { cancel(); return Optional.empty(); }
        return hold.advance(now) == ResearchTreeHoldActivationController.Outcome.ACTIVATE
                ? Optional.of(intent) : Optional.empty();
    }
    void validate(ResearchSelectionPreview preview) { if (intent != null && !intent.matches(preview)) cancel(); }
    void cancel() { intent = null; hold.cancel(); }
    void release() { cancel(); pressed = false; }
    boolean pressed() { return pressed; }
    boolean byKey() { return byKey; }
    boolean holding(long now) { return hold.snapshot(now).status() == ResearchTreeHoldActivationController.Status.HOLDING; }
    float progress(long now) { return holding(now) ? (float) hold.snapshot(now).progress() : -1F; }
}
