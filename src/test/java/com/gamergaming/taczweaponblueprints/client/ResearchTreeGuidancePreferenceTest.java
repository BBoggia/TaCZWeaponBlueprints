package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResearchTreeGuidancePreferenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void dismissalPersistsAcrossPreferenceInstances() {
        ResearchTreeGuidancePreference first =
                new ResearchTreeGuidancePreference(temporaryDirectory);

        assertTrue(first.shouldShow());
        first.dismiss();
        assertFalse(first.shouldShow());
        assertTrue(Files.isRegularFile(first.file()));
        assertFalse(new ResearchTreeGuidancePreference(temporaryDirectory).shouldShow());
    }

    @Test
    void malformedPreferenceFailsOpenToShowingHelp() throws Exception {
        ResearchTreeGuidancePreference preference =
                new ResearchTreeGuidancePreference(temporaryDirectory);
        Files.writeString(preference.file(), "research_tree_guidance_dismissed=not-a-boolean\n");

        assertTrue(preference.shouldShow());
    }

    @Test
    void failedPersistenceStillDismissesForTheCurrentSession() throws Exception {
        Path blockedDirectory = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(blockedDirectory, "blocked");
        ResearchTreeGuidancePreference preference =
                new ResearchTreeGuidancePreference(blockedDirectory);

        assertTrue(preference.shouldShow());
        preference.dismiss();
        assertFalse(preference.shouldShow());
    }

    @Test
    void dismissalPreservesOtherClientPreferences() throws Exception {
        ResearchTreeGuidancePreference preference =
                new ResearchTreeGuidancePreference(temporaryDirectory);
        Files.writeString(preference.file(), "future_setting=kept\n");

        preference.dismiss();

        Properties stored = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(preference.file())) {
            stored.load(reader);
        }
        assertTrue(Boolean.parseBoolean(
                stored.getProperty("research_tree_guidance_dismissed")));
        assertTrue("kept".equals(stored.getProperty("future_setting")));
    }

    @Test
    void railPinChoicePersistsWithoutChangingGuidanceDismissal() {
        ResearchTreeGuidancePreference first =
                new ResearchTreeGuidancePreference(temporaryDirectory);

        first.setRailPinned(true);
        assertTrue(first.railPinned());
        assertTrue(first.shouldShow());

        ResearchTreeGuidancePreference restored =
                new ResearchTreeGuidancePreference(temporaryDirectory);
        assertTrue(restored.railPinned());
        assertTrue(restored.shouldShow());
        restored.dismiss();
        restored.setRailPinned(false);

        ResearchTreeGuidancePreference finalState =
                new ResearchTreeGuidancePreference(temporaryDirectory);
        assertFalse(finalState.railPinned());
        assertFalse(finalState.shouldShow());
    }

    @Test
    void onboardingHintAndDismissalAreIndependentAndPersistent() {
        ResearchTreeGuidancePreference first =
                new ResearchTreeGuidancePreference(temporaryDirectory);

        assertTrue(first.shouldShowOnboarding());
        assertTrue(first.claimOnboardingHint());
        assertFalse(first.claimOnboardingHint());
        assertTrue(first.shouldShowOnboarding());

        ResearchTreeGuidancePreference restored =
                new ResearchTreeGuidancePreference(temporaryDirectory);
        assertFalse(restored.claimOnboardingHint());
        assertTrue(restored.shouldShowOnboarding());
        restored.dismissOnboarding();

        ResearchTreeGuidancePreference dismissed =
                new ResearchTreeGuidancePreference(temporaryDirectory);
        assertFalse(dismissed.shouldShowOnboarding());
        assertFalse(dismissed.claimOnboardingHint());
        assertTrue(dismissed.shouldShow());
    }
}
