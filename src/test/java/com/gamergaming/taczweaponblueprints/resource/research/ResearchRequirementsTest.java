package com.gamergaming.taczweaponblueprints.resource.research;

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

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisitePlan;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

class ResearchRequirementsTest {
    private static final ResourceLocation A = id("test:a");
    private static final ResourceLocation B = id("test:b");
    private static final ResourceLocation C = id("test:c");

    @Test
    void legacyPrerequisitesNormalizeToCanonicalSingletonAndGroups() {
        ResearchRequirements requirements = ResearchRequirements.fromLegacy(
                List.of(C, A, B));

        assertEquals(List.of(
                ResearchPrerequisiteGroup.singleton(A),
                ResearchPrerequisiteGroup.singleton(B),
                ResearchPrerequisiteGroup.singleton(C)), requirements.allOf());
        assertEquals(List.of(A, B, C), requirements.conservativeAlternatives());
        assertEquals(Optional.of(List.of(A, B, C)), requirements.legacySingletons());
        assertTrue(ResearchRequirements.fromLegacy(List.of()).allOf().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> requirements.allOf().add(ResearchPrerequisiteGroup.singleton(A)));
    }

    @Test
    void legacySingletonTruthTableRemainsMandatoryAnd() {
        ResearchRequirements requirements = ResearchRequirements.fromLegacy(List.of(A, B));

        assertFalse(requirements.satisfiedBy(Set.<ResourceLocation>of()::contains));
        assertFalse(requirements.satisfiedBy(Set.of(A)::contains));
        assertFalse(requirements.satisfiedBy(Set.of(B)::contains));
        assertTrue(requirements.satisfiedBy(Set.of(A, B)::contains));
    }

    @Test
    void singleAlternativeGroupTruthTableRemainsInclusiveOr() {
        ResearchRequirements requirements = new ResearchRequirements(List.of(
                new ResearchPrerequisiteGroup(List.of(A, B))));

        assertFalse(requirements.satisfiedBy(Set.<ResourceLocation>of()::contains));
        assertTrue(requirements.satisfiedBy(Set.of(A)::contains));
        assertTrue(requirements.satisfiedBy(Set.of(B)::contains));
        assertTrue(requirements.satisfiedBy(Set.of(A, B)::contains));
    }

    @Test
    void canonicalModelExpressesAndAcrossGroupsAndOrWithinEachGroup() {
        ResearchRequirements requirements = new ResearchRequirements(List.of(
                new ResearchPrerequisiteGroup(List.of(B, A)),
                ResearchPrerequisiteGroup.singleton(C)));

        assertFalse(requirements.satisfiedBy(Set.<ResourceLocation>of()::contains));
        assertFalse(requirements.satisfiedBy(Set.of(A)::contains));
        assertFalse(requirements.satisfiedBy(Set.of(B)::contains));
        assertTrue(requirements.satisfiedBy(Set.of(A, C)::contains));
        assertTrue(requirements.satisfiedBy(Set.of(B, C)::contains));
        assertFalse(requirements.satisfiedBy(Set.of(A, B)::contains));
        assertTrue(requirements.satisfiedBy(Set.of(A, B, C)::contains));
        assertEquals(Optional.empty(), requirements.legacySingletons());
    }

    @Test
    void alternativesAndGroupsHaveDeterministicOrderingAndEncoding() {
        ResearchRequirements requirements = new ResearchRequirements(List.of(
                ResearchPrerequisiteGroup.singleton(B),
                new ResearchPrerequisiteGroup(List.of(C, A))));

        assertEquals(List.of(A, C), requirements.allOf().get(0).anyOf());
        assertEquals(
                "[{\"any_of\":[\"test:a\",\"test:c\"]},{\"any_of\":[\"test:b\"]}]",
                ResearchRequirements.CODEC.encodeStart(JsonOps.INSTANCE, requirements)
                        .result().orElseThrow().toString());
        assertEquals(requirements, ResearchRequirements.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(
                        "[{\"any_of\":[\"test:c\",\"test:a\"]},"
                                + "{\"any_of\":[\"test:b\"]}]"))
                .result().orElseThrow());
    }

    @Test
    void malformedGroupsAndRequirementsAreRejectedStrictly() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchPrerequisiteGroup(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchPrerequisiteGroup(List.of(A, A)));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchPrerequisiteGroup.singleton(null));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchPrerequisiteGroup(ids(
                        ResearchPrerequisiteGroup.MAX_ALTERNATIVES + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchPrerequisiteGroup.singleton(new ResourceLocation(
                        "test", "a".repeat(300))));

        ResearchPrerequisiteGroup group = new ResearchPrerequisiteGroup(List.of(A, B));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchRequirements(List.of(
                        group,
                        new ResearchPrerequisiteGroup(List.of(B, A)))));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchRequirements.fromLegacy(ids(
                        ResearchRequirements.MAX_TOTAL_ALTERNATIVES + 1)));
        assertThrows(IllegalArgumentException.class, () -> group.validateFor(A));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchRequirements.fromLegacy(List.of(A, A)));
    }

    @Test
    void codecRejectsUnknownFieldsEmptyGroupsAndDuplicates() {
        assertTrue(ResearchPrerequisiteGroup.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(
                        "{\"any_of\":[\"test:a\"],\"unexpected\":true}"))
                .error().isPresent());
        assertTrue(ResearchPrerequisiteGroup.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("{\"any_of\":[]}"))
                .error().isPresent());
        assertTrue(ResearchPrerequisiteGroup.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(
                        "{\"any_of\":[\"test:a\",\"test:a\"]}"))
                .error().isPresent());
        assertTrue(ResearchRequirements.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(
                        "[{\"any_of\":[\"test:a\"]},"
                                + "{\"any_of\":[\"test:a\"]}]"))
                .error().isPresent());
    }

    @Test
    void conservativeUnionGraphRejectsCyclesAndDepthOverflow() {
        Map<ResourceLocation, ResearchRequirements> cyclic = new LinkedHashMap<>();
        cyclic.put(A, new ResearchRequirements(List.of(
                new ResearchPrerequisiteGroup(List.of(B, C)))));
        cyclic.put(C, ResearchRequirements.fromLegacy(List.of(A)));
        IllegalArgumentException cycle = assertThrows(IllegalArgumentException.class,
                () -> ResearchRequirements.validateConservativeGraph(cyclic, 64));
        assertTrue(cycle.getMessage().contains("cycle"));

        Map<ResourceLocation, ResearchRequirements> deep = Map.of(
                A, ResearchRequirements.fromLegacy(List.of(B)),
                B, ResearchRequirements.fromLegacy(List.of(C)),
                C, ResearchRequirements.EMPTY);
        assertThrows(IllegalArgumentException.class,
                () -> ResearchRequirements.validateConservativeGraph(deep, 2));
        ResearchRequirements.validateConservativeGraph(deep, 3);
    }

    @Test
    void currentRuleDefinitionPolicyAndAutomaticPlanExposeSingletonAdapters() {
        BlueprintResearchRule rule = phaseOneRule(Optional.of(List.of(B, A, B)));
        assertEquals(
                ResearchRequirements.fromLegacy(List.of(A, B)),
                rule.prerequisiteRequirements().orElseThrow());
        assertTrue(phaseOneRule(Optional.empty()).prerequisiteRequirements().isEmpty());
        assertEquals(
                ResearchRequirements.EMPTY,
                phaseOneRule(Optional.of(List.of()))
                        .prerequisiteRequirements().orElseThrow());

        BlueprintResearchPolicyDefinition definition =
                new BlueprintResearchPolicyDefinition(
                        true, true, JournalVisibility.FULL, true, false, false, 0,
                        new BlueprintResearchCost(1, List.of()),
                        false,
                        List.of(B, A),
                        false,
                        Optional.empty(),
                        BlueprintResearchTarget.MatchSpecificity.NONE,
                        false,
                        BlueprintReverseEngineeringPolicy.DISABLED);
        assertEquals(
                ResearchRequirements.fromLegacy(List.of(A, B)),
                definition.requirements());
        assertEquals(List.of(B, A), definition.prerequisites());

        BlueprintResearchPolicy policy = new BlueprintResearchPolicy(
                C,
                id("test:profile"),
                true,
                false,
                true,
                false,
                true,
                0,
                100,
                false,
                true,
                true,
                JournalVisibility.FULL,
                true,
                false,
                false,
                0,
                new BlueprintResearchCost(1, List.of()),
                false,
                List.of(B, A),
                false,
                Optional.empty(),
                BlueprintResearchTarget.MatchSpecificity.NONE);
        assertEquals(
                ResearchRequirements.fromLegacy(List.of(A, B)),
                policy.requirements());
        assertEquals(List.of(B, A), policy.prerequisites());

        AutomaticWeaponPrerequisitePlan automatic =
                new AutomaticWeaponPrerequisitePlan(
                        id("test:profile"),
                        id("test:tree"),
                        AutomaticPlacementMode.CONNECTED,
                        1L,
                        1L,
                        1,
                        Map.of(C, List.of(B, A)),
                        Map.of());
        assertEquals(
                ResearchRequirements.fromLegacy(List.of(A, B)),
                automatic.requirementsFor(C));
        assertEquals(List.of(B, A), automatic.prerequisitesFor(C));
    }

    private static BlueprintResearchRule phaseOneRule(
            Optional<List<ResourceLocation>> prerequisites) {
        return new BlueprintResearchRule(
                BlueprintResearchRule.CURRENT_FORMAT,
                id("test:profile"),
                0,
                new BlueprintResearchTarget(List.of(C), List.of(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                prerequisites,
                Optional.empty(),
                Optional.empty());
    }

    private static List<ResourceLocation> ids(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> id("test:value_" + index))
                .toList();
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
