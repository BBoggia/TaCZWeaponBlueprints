package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Release-contract gates for first-hour guidance and optional recipe viewers. */
class BlueprintKnowledgeFlowPhaseSevenTest {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));

    @Test
    void recipeViewersRemainOptionalAndUnbundled() throws IOException {
        String build = read("build.gradle");
        String mods = read("src/main/resources/META-INF/mods.toml");
        assertTrue(build.contains("compileOnly fg.deobf(\"dev.emi:emi-forge:"));
        assertTrue(build.contains("runtimeOnly fg.deobf(\"dev.emi:emi-forge:${emi_version}\")"));
        assertTrue(build.contains("compileOnly fg.deobf(\"mezz.jei:jei-${minecraft_version}-common-api:"));
        assertTrue(mods.contains("modId=\"jei\"\n    mandatory=false"));
        assertTrue(mods.contains("modId=\"emi\"\n    mandatory=false"));
    }

    @Test
    void viewerPluginsCannotEnumerateOrTransferHiddenResearch() throws IOException {
        String jei = read("src/main/java/com/gamergaming/taczweaponblueprints/compat/jei/"
                + "TaCZWeaponBlueprintsJeiPlugin.java");
        String emi = read("src/main/java/com/gamergaming/taczweaponblueprints/compat/emi/"
                + "TaCZWeaponBlueprintsEmiPlugin.java");
        String sources = jei + emi;
        for (String forbidden : List.of(
                "ClientBlueprintJournal",
                "BlueprintResearchDataManager",
                "BlueprintDataManager",
                "registerRecipeTransferHandlers",
                "addRecipeHandler")) {
            assertFalse(sources.contains(forbidden), "Viewer integration leaks authority: " + forbidden);
        }
        assertTrue(jei.contains("addItemStackInfo"));
        assertTrue(emi.contains("EmiInfoRecipe"));
        assertTrue(emi.contains("EmiSyntheticRecipeId.forTopic(topic)"));
        assertTrue(emi.contains("disclosedBlueprintIds"));
        assertTrue(emi.contains("EmiSyntheticRecipeId.forBlueprint(blueprintId)"));
    }

    @Test
    void artifactAndCandidateGatesRecordTheGuidanceBoundary() throws IOException {
        String build = read("build.gradle");
        for (String required : List.of(
                "client/BlueprintOnboardingPlan.class",
                "compat/recipeviewer/BlueprintRecipeViewerInfo.class",
                "compat/jei/TaCZWeaponBlueprintsJeiPlugin.class",
                "compat/emi/TaCZWeaponBlueprintsEmiPlugin.class",
                "onboarding: 'journal_getting_started'",
                "viewerContent: 'generic_information_only'",
                "recipeTransfer: 'none'",
                "hiddenCatalogEnumeration: 'none'")) {
            assertTrue(build.contains(required), "Missing Phase 7 gate: " + required);
        }
    }

    @Test
    void phaseRecordPreservesAuthorityCompatibilityAndManualQaBoundary() throws IOException {
        String record = read("docs/blueprint-knowledge-flow-phase-7.md");
        for (String required : List.of(
                "never reconstructs hidden blueprint",
                "No recipe-transfer handler",
                "custom network protocol remains `26`",
                "Player progression remains data version 2",
                "JEI and EMI remain optional and client-only",
                "does not claim the runtime checks")) {
            assertTrue(record.contains(required), "Missing Phase 7 contract: " + required);
        }
    }

    private static String read(String relative) throws IOException {
        return Files.readString(PROJECT.resolve(relative));
    }
}
