package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.network.NetworkHandler;

import net.minecraft.world.SimpleContainer;

/** Regression gates for the destructive Phase 5 compatibility cleanup. */
class ResearchWorkstationsPhaseFiveTest {
    @Test
    void researchBenchOwnsOnlySelectionAndResearchActions() {
        assertEquals(
                List.of(
                        ResearchBenchResearchAction.SELECT,
                        ResearchBenchResearchAction.RESEARCH),
                List.of(ResearchBenchResearchAction.values()));
        Set<String> nestedTypes = Arrays.stream(ResearchBenchMenu.class.getDeclaredClasses())
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());
        assertFalse(nestedTypes.contains("Mode"));
        assertFalse(nestedTypes.contains("Action"));
    }

    @Test
    void researchPreviewCannotCarryRecyclerDecisions() {
        Set<String> fields = Arrays.stream(ResearchSelectionPreview.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "blueprintId",
                "pointCost",
                "pointBalance",
                "policyEligible",
                "ingredientsSatisfied",
                "outputSpace",
                "researchable",
                "creativeBypass",
                "ingredients",
                "unlockCount",
                "ingredientTypeCount",
                "pathPlanningState",
                "costMode"), fields);
        assertFalse(fields.contains("recycling"));
        assertFalse(fields.contains("researchData"));
    }

    @Test
    void researchMenuDeclaresNoPhysicalContainerAndRecyclerKeepsTurnIns() {
        assertTrue(Arrays.stream(ResearchBenchMenu.class.getDeclaredFields())
                .noneMatch(field -> SimpleContainer.class.isAssignableFrom(field.getType())));
        assertEquals(
                Set.of("RECYCLE", "REDEEM", "REDEEM_STACK", "REVERSE_ENGINEER", "RECOVER_POINTS"),
                Arrays.stream(BlueprintRecyclerActionContract.Action.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet()));
    }

    @Test
    void destructiveWireCleanupUsesANewExactProtocol() {
        assertEquals("40", NetworkHandler.PROTOCOL_VERSION);
    }
}
