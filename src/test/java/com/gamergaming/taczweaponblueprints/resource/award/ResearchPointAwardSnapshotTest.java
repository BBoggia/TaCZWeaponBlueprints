package com.gamergaming.taczweaponblueprints.resource.award;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchPointAwardSnapshotTest {
    @Test
    void buildsImmutableSortedIndexesAndRetainsDisabledDefinitions() {
        ResearchPointAwardDefinition disabled = definition(
                false,
                id("test:disabled_group"),
                Optional.of(new ResearchPointAwardTarget(
                        List.of(id("test:disabled")), List.of(), List.of(), Optional.empty())),
                Optional.empty());
        ResearchPointAwardDefinition exact = definition(
                true,
                id("test:enabled_group"),
                Optional.of(new ResearchPointAwardTarget(
                        List.of(id("test:item")), List.of(), List.of(), Optional.empty())),
                Optional.of(new ResearchPointAwardBudget(id("test:shared"), 4, 10, 200L)));
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(Map.of(
                id("test:z_disabled"), disabled,
                id("test:a_exact"), exact));

        assertEquals(List.of(id("test:a_exact"), id("test:z_disabled")),
                List.copyOf(snapshot.definitions().keySet()));
        assertEquals(2, snapshot.definitions().size());
        assertEquals(1, snapshot.enabledDefinitionCount());
        assertEquals(Set.of(id("test:shared")), snapshot.budgets().keySet());
        assertEquals(List.of(id("test:a_exact")), snapshot.candidatesFor(
                ResearchPointAwardContext.simple(
                        ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED,
                        id("test:profile"),
                        id("test:item"))).stream().map(
                                ResearchPointAwardSnapshot.Binding::definitionId).toList());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.definitions().put(id("test:new"), exact));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.bindingsByTrigger().get(
                        ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED).clear());
    }

    @Test
    void rejectsConflictingSharedBudgetDeclarations() {
        ResearchPointAwardDefinition first = definition(
                true, id("test:first"), Optional.empty(),
                Optional.of(new ResearchPointAwardBudget(id("test:shared"), 4, 10, 200L)));
        ResearchPointAwardDefinition conflicting = definition(
                true, id("test:second"), Optional.empty(),
                Optional.of(new ResearchPointAwardBudget(id("test:shared"), 5, 10, 200L)));

        assertThrows(IllegalArgumentException.class, () -> ResearchPointAwardSnapshot.create(Map.of(
                id("test:first"), first,
                id("test:second"), conflicting)));
    }

    @Test
    void snapshotRevalidatesProgrammaticDefinitionsThroughTheStrictCodec() {
        ResearchPointAwardDefinition oversizedReward = new ResearchPointAwardDefinition(
                1,
                true,
                List.of(),
                id("test:group"),
                0,
                new ResearchPointAwardTrigger(
                        ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED,
                        Optional.empty(), false, Optional.empty(), Optional.empty()),
                new ResearchPointAwardReward(
                        PlayerProgressionLimits.MAX_RESEARCH_POINTS + 1,
                        ResearchPointAwardReward.Overflow.CLAMP),
                unlimited(),
                Optional.empty(),
                hidden());

        assertThrows(IllegalArgumentException.class, () -> ResearchPointAwardSnapshot.create(
                Map.of(id("test:oversized"), oversizedReward)));
    }

    @Test
    void definitionAndContextHardLimitsFailClosed() {
        ResearchPointAwardDefinition definition = definition(
                true, id("test:group"), Optional.empty(), Optional.empty());
        Map<ResourceLocation, ResearchPointAwardDefinition> oversized = new LinkedHashMap<>();
        IntStream.range(0, PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_DEFINITIONS)
                .forEach(index -> oversized.put(id("test:definition_" + index), definition));
        assertEquals(PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_DEFINITIONS,
                ResearchPointAwardSnapshot.create(oversized).definitions().size());
        oversized.put(id("test:definition_beyond_limit"), definition);
        assertThrows(IllegalArgumentException.class,
                () -> ResearchPointAwardSnapshot.create(oversized));

        Set<ResourceLocation> tags = IntStream.rangeClosed(
                        0, PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_SELECTOR_TERMS)
                .mapToObj(index -> id("test:tag_" + index))
                .collect(java.util.stream.Collectors.toSet());
        assertThrows(IllegalArgumentException.class, () -> new ResearchPointAwardContext(
                ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED,
                id("test:profile"),
                ResearchPointAwardContext.DispatchMode.LIVE,
                Optional.of(id("test:item")),
                tags,
                Optional.empty(), Optional.empty(), Optional.empty(),
                0, 0, Optional.empty()));
    }

    @Test
    void milestoneDefinitionsHaveTheirOwnLiveEvaluationLimit() {
        Map<ResourceLocation, ResearchPointAwardDefinition> oversized = new LinkedHashMap<>();
        IntStream.rangeClosed(0, PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_MILESTONE_DEFINITIONS)
                .forEach(index -> oversized.put(
                        id("test:milestone_" + index), milestoneDefinition(index)));

        assertThrows(IllegalArgumentException.class,
                () -> ResearchPointAwardSnapshot.create(oversized));
    }

    @Test
    void diagnosticsAreDeterministicForEmptyAndPopulatedSnapshots() {
        ResearchPointAwardDiagnostics.Summary empty = new ResearchPointAwardDiagnostics.Summary(
                0, 0, 0, 0, 0, Map.of());
        assertTrue(empty.triggerCounts().isEmpty());

        ResearchPointAwardDefinition definition = definition(
                true,
                id("test:group"),
                Optional.of(new ResearchPointAwardTarget(
                        List.of(id("test:item")), List.of(id("test:tag")),
                        List.of("test"), Optional.empty())),
                Optional.empty());
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(
                Map.of(id("test:definition"), definition));
        ResearchPointAwardDiagnostics.Summary summary =
                ResearchPointAwardDiagnostics.summarize(snapshot);
        ResearchPointAwardDiagnostics.Inspection inspection =
                ResearchPointAwardDiagnostics.inspect(snapshot, id("test:definition")).orElseThrow();

        assertEquals(1, summary.definitionCount());
        assertEquals(3, summary.targetBindingCount());
        assertEquals(1, summary.triggerCounts().get(
                ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED));
        assertEquals(3, inspection.targetTerms());
        assertFalse(ResearchPointAwardDiagnostics.inspect(
                snapshot, id("test:missing")).isPresent());
        assertThrows(UnsupportedOperationException.class,
                () -> summary.triggerCounts().clear());
    }

    private static ResearchPointAwardDefinition definition(
            boolean enabled,
            ResourceLocation group,
            Optional<ResearchPointAwardTarget> target,
            Optional<ResearchPointAwardBudget> budget) {
        return new ResearchPointAwardDefinition(
                1,
                enabled,
                List.of(),
                group,
                0,
                new ResearchPointAwardTrigger(
                        ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED,
                        target,
                        false,
                        Optional.empty(),
                        Optional.empty()),
                new ResearchPointAwardReward(2, ResearchPointAwardReward.Overflow.CLAMP),
                unlimited(),
                budget,
                hidden());
    }

    private static ResearchPointAwardRepeat unlimited() {
        return new ResearchPointAwardRepeat(
                ResearchPointAwardRepeat.Type.UNLIMITED,
                Optional.empty(),
                ResearchPointAwardRepeat.Scope.DEFINITION,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static ResearchPointAwardDefinition milestoneDefinition(int index) {
        return new ResearchPointAwardDefinition(
                1,
                true,
                List.of(),
                id("test:milestone_group_" + index),
                0,
                new ResearchPointAwardTrigger(
                        ResearchPointAwardTrigger.Type.BLUEPRINT_MILESTONE,
                        Optional.empty(),
                        true,
                        Optional.of(new ResearchPointAwardTrigger.Milestone(
                                ResearchPointAwardTrigger.MilestoneState.DISCOVERED, 1)),
                        Optional.empty()),
                new ResearchPointAwardReward(1, ResearchPointAwardReward.Overflow.CLAMP),
                new ResearchPointAwardRepeat(
                        ResearchPointAwardRepeat.Type.ONCE,
                        Optional.of(id("test:milestone_claim_" + index)),
                        ResearchPointAwardRepeat.Scope.DEFINITION,
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                Optional.empty(),
                hidden());
    }

    private static ResearchPointAwardPresentation hidden() {
        return new ResearchPointAwardPresentation(
                ResearchPointAwardPresentation.Visibility.HIDDEN, Optional.empty());
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
