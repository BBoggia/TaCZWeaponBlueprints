package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.client.ResearchBenchPresentationPolicy;
import com.gamergaming.taczweaponblueprints.init.ModBlocks;
import com.gamergaming.taczweaponblueprints.init.ModItems;
import com.gamergaming.taczweaponblueprints.init.ModMenus;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;

/** Final release-contract gates for the separated research workstations. */
class ResearchWorkstationsPhaseEightTest {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));
    private static final ResourceLocation BENCH =
            new ResourceLocation("taczweaponblueprints:research_bench");
    private static final ResourceLocation RECYCLER =
            new ResourceLocation("taczweaponblueprints:blueprint_recycler");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void finalOwnershipRegistryAndProtocolContractsRemainExact() {
        assertEquals(BENCH, ModBlocks.RESEARCH_BENCH.getId());
        assertEquals(BENCH, ModItems.RESEARCH_BENCH_ITEM.getId());
        assertEquals(BENCH, ModMenus.RESEARCH_BENCH.getId());
        assertEquals(RECYCLER, ModBlocks.BLUEPRINT_RECYCLER.getId());
        assertEquals(RECYCLER, ModItems.BLUEPRINT_RECYCLER_ITEM.getId());
        assertEquals(RECYCLER, ModMenus.BLUEPRINT_RECYCLER.getId());
        assertTrue(ResearchBenchPresentationPolicy.permanentFullscreen());
        assertEquals("36", NetworkHandler.PROTOCOL_VERSION);
    }

    @Test
    void benchAndRecyclerExposeOnlyTheirFinishedActionSurfaces() {
        assertEquals(
                List.of("SELECT", "RESEARCH"),
                Arrays.stream(ResearchBenchResearchAction.values()).map(Enum::name).toList());
        assertEquals(
                Set.of("RECYCLE", "REDEEM", "REDEEM_STACK", "REVERSE_ENGINEER"),
                Arrays.stream(BlueprintRecyclerActionContract.Action.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet()));
        assertTrue(Arrays.stream(ResearchBenchMenu.class.getDeclaredFields())
                .noneMatch(field -> SimpleContainer.class.isAssignableFrom(field.getType())));
        assertEquals(0, BlueprintRecyclerMenu.INPUT_SLOT);
        assertEquals(1, BlueprintRecyclerMenu.OUTPUT_SLOT);
        assertEquals(2, BlueprintRecyclerMenu.FIRST_PLAYER_SLOT);
    }

    @Test
    void releaseArtifactGateRequiresTheCompleteRecyclerRuntimeAndFinalArt()
            throws IOException {
        String build = Files.readString(PROJECT.resolve("build.gradle"));
        for (String required : List.of(
                "client/ClientModEvents.class",
                "init/ModBlocks.class",
                "init/ModItems.class",
                "init/ModMenus.class",
                "block/BlueprintRecyclerBlock.class",
                "client/BlueprintRecyclerScreen.class",
                "client/BlueprintRecyclerScreenModel.class",
                "menu/BlueprintRecyclerMenu.class",
                "menu/BlueprintRecyclerPreview.class",
                "menu/BlueprintRecyclerActionContract.class",
                "network/BlueprintRecyclerActionPacket.class",
                "network/BlueprintRecyclerActionResultPacket.class",
                "network/SyncBlueprintRecyclerPreviewPacket.class",
                "textures/block/blueprint_recycler.png")) {
            assertTrue(build.contains(required), "Missing artifact requirement " + required);
        }
        assertTrue(build.contains(
                "Packaged Blueprint Recycler model is not the final bounded eight-part workstation"));
        assertTrue(build.contains(
                "Packaged Blueprint Recycler texture must be a readable 256x256 PNG"));
        assertTrue(build.contains("researchWorkstations: ["));
        assertTrue(build.contains("presentation: 'permanent_fullscreen_research_tree'"));
        assertTrue(build.contains("presentation: 'two_slot_contextual_analyzer'"));
        assertTrue(build.contains("localOutputs: 1"));
        assertTrue(build.contains("manualQa       : 'docs/research-tree-manual-qa.md'"));
    }

    @Test
    void clientRegistrationBindsEachPermanentMenuToItsIntendedScreen()
            throws IOException {
        String clientEvents = Files.readString(PROJECT.resolve(
                "src/main/java/com/gamergaming/taczweaponblueprints/client/ClientModEvents.java"));

        assertTrue(clientEvents.contains(
                "MenuScreens.register(ModMenus.RESEARCH_BENCH.get(), ResearchBenchScreen::new)"));
        assertTrue(clientEvents.contains(
                "MenuScreens.register(ModMenus.BLUEPRINT_RECYCLER.get(), BlueprintRecyclerScreen::new)"));
    }

    @Test
    void fullscreenOverlayTransitionsCannotRetainHiddenKeyboardFocus()
            throws IOException {
        String screen = Files.readString(PROJECT.resolve(
                "src/main/java/com/gamergaming/taczweaponblueprints/client/ResearchBenchScreen.java"));

        assertTrue(screen.contains("private void clearFocusIfHidden()"));
        assertTrue(screen.contains("getFocused() instanceof AbstractWidget widget && !widget.visible"));
        assertTrue(screen.contains("updateFullscreenContextCardWidgets();\n        clearFocusIfHidden();"));
        assertTrue(screen.contains("trackResearchButton.visible = false;\n        }\n        clearFocusIfHidden();"));
    }

    @Test
    void releaseEvidenceUsesTheCurrentProtocolAndKeepsManualQaExplicit()
            throws IOException {
        String manual = Files.readString(PROJECT.resolve("docs/research-tree-manual-qa.md"));
        String checklist = Files.readString(PROJECT.resolve("docs/release-checklist.md"));
        String operations = Files.readString(PROJECT.resolve("docs/operations-and-migration.md"));

        assertTrue(manual.contains("protocol other than `36`"));
        assertFalse(manual.contains("protocol other than `20`"));
        assertTrue(manual.contains("does not certify any"));
        assertTrue(manual.contains("unchecked hands-on behavior below"));
        assertTrue(checklist.contains("research-workstation ownership"));
        assertTrue(checklist.contains("presentation contract"));
        assertTrue(operations.contains("research-workstation split"));
    }

    @Test
    void completeWorkstationPhaseHistoryIsRetained() {
        for (int phase = 0; phase <= 8; phase++) {
            assertTrue(Files.isRegularFile(PROJECT.resolve(
                    "docs/research-workstations-phase-" + phase + ".md")));
        }
    }
}
