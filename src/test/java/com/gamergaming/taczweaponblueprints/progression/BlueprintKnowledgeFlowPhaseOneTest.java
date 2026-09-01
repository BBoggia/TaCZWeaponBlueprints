package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;

/** Locks Phase 1 to additive, behavior-neutral policy foundations. */
class BlueprintKnowledgeFlowPhaseOneTest {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));

    @Test
    void phaseOneVocabularyRemainsStableAfterLaterActivation()
            throws IOException {
        for (String relative : List.of(
                "src/main/java/com/gamergaming/taczweaponblueprints/"
                        + "mixin/GunSmithTableMenuMixin.java",
                "src/main/java/com/gamergaming/taczweaponblueprints/"
                        + "menu/BlueprintRecyclerMenu.java")) {
            String source = Files.readString(PROJECT.resolve(relative));
            assertFalse(
                    source.contains("BlueprintAccessPolicy"),
                    relative + " activated a later policy route early");
            assertFalse(
                    source.contains("TreeResearchResultMode"),
                    relative + " duplicated the Research Tree result policy");
        }

        assertEquals(
                List.of("DIRECT_LEARN", "CREATE_BLUEPRINT"),
                Arrays.stream(TreeResearchResultMode.values())
                        .map(Enum::name)
                        .toList());
        assertEquals("42", NetworkHandler.PROTOCOL_VERSION);
        assertEquals(
                Set.of("RECYCLE", "REDEEM", "REDEEM_STACK", "REVERSE_ENGINEER", "RECOVER_POINTS"),
                Arrays.stream(BlueprintRecyclerActionContract.Action.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet()));
    }

}
