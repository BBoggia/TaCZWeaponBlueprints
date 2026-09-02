package com.gamergaming.taczweaponblueprints.resource.award;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.CombatFacts;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.CreditType;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.Difficulty;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.DispatchMode;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.SpawnProvenance;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTarget.CatalogSelector;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTarget.Specificity;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.BossMode;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchPointAwardResolverTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation TARGET = id("addon:gold_pistol");
    private static final ResourceLocation TAG = id("forge:pistols");

    @Test
    void resolvesSpecificityPriorityIdTiesAndIntentionalStacksDeterministically() {
        Map<ResourceLocation, ResearchPointAwardDefinition> definitions = new LinkedHashMap<>();
        definitions.put(id("test:generic"), definition(
                id("test:main"), 1_000_000, Optional.empty(), List.of()));
        definitions.put(id("test:namespace"), definition(
                id("test:main"), 50_000, Optional.of(target(List.of(), List.of(),
                        List.of("addon"), Optional.empty())), List.of()));
        definitions.put(id("test:selector"), definition(
                id("test:main"), 5_000, Optional.of(target(List.of(), List.of(), List.of(),
                        Optional.of(new CatalogSelector(
                                Optional.of("pistol"), Optional.of(BlueprintKind.GUN), Optional.empty())))),
                List.of()));
        definitions.put(id("test:tag"), definition(
                id("test:main"), 500, Optional.of(target(
                        List.of(), List.of(TAG), List.of(), Optional.empty())), List.of()));
        definitions.put(id("test:z_exact"), definition(
                id("test:main"), 10, Optional.of(target(
                        List.of(TARGET), List.of(), List.of(), Optional.empty())), List.of()));
        definitions.put(id("test:a_exact"), definition(
                id("test:main"), 10, Optional.of(target(
                        List.of(TARGET), List.of(), List.of(), Optional.empty())), List.of()));
        definitions.put(id("test:stack"), definition(
                id("test:a_stack"), -10, Optional.of(target(
                        List.of(TARGET), List.of(), List.of(), Optional.empty())), List.of()));
        definitions.put(id("test:wrong_profile"), definition(
                id("test:main"), 100, Optional.of(target(
                        List.of(TARGET), List.of(), List.of(), Optional.empty())),
                List.of(id("test:other_profile"))));
        definitions.put(id("test:dormant"), definition(
                id("test:main"), 1_000_000, Optional.of(target(
                        List.of(id("missing:optional_weapon")), List.of(), List.of(), Optional.empty())),
                List.of()));

        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(definitions);
        ResearchPointAwardResolver.Resolution resolution = ResearchPointAwardResolver.resolve(
                snapshot, blueprintContext(DispatchMode.LIVE, 0, 0));

        assertEquals(ResearchPointAwardResolver.Status.RESOLVED, resolution.status());
        assertEquals(2, resolution.matchedGroupCount());
        assertEquals(List.of(id("test:stack"), id("test:a_exact")), resolution.awards().stream()
                .map(value -> value.binding().definitionId()).toList());
        assertEquals(Specificity.EXACT, resolution.awards().get(1).specificity());
        assertEquals(1, resolution.competitions().size());
        assertEquals(List.of(id("test:a_exact"), id("test:z_exact")),
                resolution.competitions().get(0).tiedDefinitionIds());
        assertFalse(snapshot.candidatesFor(blueprintContext(DispatchMode.LIVE, 0, 0)).stream()
                .anyMatch(binding -> binding.definitionId().equals(id("test:dormant"))));
    }

    @Test
    void failsClosedWhenOneEventMatchesMoreThanTheBoundedNumberOfGroups() {
        Map<ResourceLocation, ResearchPointAwardDefinition> definitions = new LinkedHashMap<>();
        IntStream.range(0, 65).forEach(index -> definitions.put(
                id("test:definition_" + index),
                definition(id("test:group_" + index), 0, Optional.empty(), List.of())));

        ResearchPointAwardResolver.Resolution resolution = ResearchPointAwardResolver.resolve(
                ResearchPointAwardSnapshot.create(definitions),
                blueprintContext(DispatchMode.LIVE, 0, 0));

        Map<ResourceLocation, ResearchPointAwardDefinition> atLimit = new LinkedHashMap<>(definitions);
        atLimit.remove(id("test:definition_64"));
        ResearchPointAwardResolver.Resolution accepted = ResearchPointAwardResolver.resolve(
                ResearchPointAwardSnapshot.create(atLimit),
                blueprintContext(DispatchMode.LIVE, 0, 0));

        assertEquals(ResearchPointAwardResolver.Status.TOO_MANY_GROUPS, resolution.status());
        assertEquals(65, resolution.matchedGroupCount());
        assertTrue(resolution.awards().isEmpty());
        assertTrue(resolution.competitions().isEmpty());
        assertEquals(ResearchPointAwardResolver.Status.RESOLVED, accepted.status());
        assertEquals(64, accepted.awards().size());
    }

    @Test
    void milestoneAndRetroactiveConditionsRequireTheExactCrossingAndOptIn() {
        ResearchPointAwardTrigger milestoneTrigger = new ResearchPointAwardTrigger(
                ResearchPointAwardTrigger.Type.BLUEPRINT_MILESTONE,
                Optional.of(target(List.of(), List.of(), List.of(), Optional.of(
                        new CatalogSelector(Optional.of("pistol"), Optional.empty(), Optional.empty())))),
                true,
                Optional.of(new ResearchPointAwardTrigger.Milestone(
                        ResearchPointAwardTrigger.MilestoneState.LEARNED, 3)),
                Optional.empty());
        ResearchPointAwardDefinition milestone = definition(
                id("test:milestone"), 0, milestoneTrigger, List.of(), ResearchPointAwardRepeat.Type.ONCE);
        ResearchPointAwardDefinition liveOnly = definition(
                id("test:live_only"), 0,
                new ResearchPointAwardTrigger(
                        ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED,
                        Optional.empty(), false, Optional.empty(), Optional.empty()),
                List.of(), ResearchPointAwardRepeat.Type.UNLIMITED);

        ResearchPointAwardResolver.Resolution crossing = ResearchPointAwardResolver.resolve(
                ResearchPointAwardSnapshot.create(Map.of(
                        id("test:milestone"), milestone,
                        id("test:live_only"), liveOnly)),
                milestoneContext(2, 3));
        ResearchPointAwardResolver.Resolution alreadyAbove = ResearchPointAwardResolver.resolve(
                ResearchPointAwardSnapshot.create(Map.of(id("test:milestone"), milestone)),
                milestoneContext(3, 4));
        ResearchPointAwardResolver.Resolution retroactiveLiveOnly = ResearchPointAwardResolver.resolve(
                ResearchPointAwardSnapshot.create(Map.of(id("test:live_only"), liveOnly)),
                blueprintContext(DispatchMode.RETROACTIVE, 0, 0));

        assertEquals(List.of(id("test:milestone")), crossing.awards().stream()
                .map(value -> value.binding().definitionId()).toList());
        assertTrue(alreadyAbove.awards().isEmpty());
        assertTrue(retroactiveLiveOnly.awards().isEmpty());
    }

    @Test
    void combatDefaultsAndTypedFiltersFailClosedOnUnsafeFacts() {
        ResourceLocation overworld = id("minecraft:overworld");
        ResearchPointAwardTrigger.CombatConditions conditions =
                new ResearchPointAwardTrigger.CombatConditions(
                        true, false, false, false, false, false, true, false,
                        false, false, false, true, 100L,
                        List.of(overworld), List.of(Difficulty.NORMAL), BossMode.EXCLUDED);
        ResearchPointAwardTrigger trigger = new ResearchPointAwardTrigger(
                ResearchPointAwardTrigger.Type.ENTITY_KILLED,
                Optional.of(target(List.of(id("minecraft:zombie")), List.of(), List.of(), Optional.empty())),
                false, Optional.empty(), Optional.of(conditions));
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(Map.of(
                id("test:zombie"), definition(
                        id("test:combat"), 0, trigger, List.of(), ResearchPointAwardRepeat.Type.UNLIMITED)));

        assertEquals(1, resolveCombat(snapshot, combatFacts(
                CreditType.DIRECT, false, Optional.of(SpawnProvenance.NATURAL), 100L, false)).awards().size());
        assertEquals(1, resolveCombat(snapshot, combatFacts(
                CreditType.OWNED_PROJECTILE, false,
                Optional.of(SpawnProvenance.STRUCTURE), 120L, false)).awards().size());
        assertTrue(resolveCombat(snapshot, combatFacts(
                CreditType.INDIRECT, false, Optional.of(SpawnProvenance.NATURAL), 120L, false))
                .awards().isEmpty());
        assertTrue(resolveCombat(snapshot, combatFacts(
                CreditType.DIRECT, true, Optional.of(SpawnProvenance.NATURAL), 120L, false))
                .awards().isEmpty());
        assertTrue(resolveCombat(snapshot, combatFacts(
                CreditType.DIRECT, false, Optional.empty(), 120L, false)).awards().isEmpty());
        assertTrue(resolveCombat(snapshot, combatFacts(
                CreditType.DIRECT, false, Optional.of(SpawnProvenance.SPAWNER), 120L, false))
                .awards().isEmpty());
        assertTrue(resolveCombat(snapshot, combatFacts(
                CreditType.DIRECT, false, Optional.of(SpawnProvenance.NATURAL), 99L, false))
                .awards().isEmpty());
        assertTrue(resolveCombat(snapshot, combatFacts(
                CreditType.DIRECT, false, Optional.of(SpawnProvenance.NATURAL), 120L, true))
                .awards().isEmpty());
    }

    @Test
    void omittedCombatObjectStillUsesTheSafeAntiCheeseDefaults() {
        ResearchPointAwardTrigger trigger = new ResearchPointAwardTrigger(
                ResearchPointAwardTrigger.Type.ENTITY_KILLED,
                Optional.of(target(List.of(id("minecraft:zombie")), List.of(), List.of(), Optional.empty())),
                false, Optional.empty(), Optional.empty());
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(Map.of(
                id("test:zombie"), definition(
                        id("test:combat"), 0, trigger, List.of(), ResearchPointAwardRepeat.Type.UNLIMITED)));

        assertEquals(1, resolveCombat(snapshot, combatFacts(
                CreditType.DIRECT, false, Optional.of(SpawnProvenance.NATURAL), 0L, false)).awards().size());
        assertTrue(resolveCombat(snapshot, combatFacts(
                CreditType.INDIRECT, false,
                Optional.of(SpawnProvenance.NATURAL), 20L, false)).awards().isEmpty());
        assertTrue(resolveCombat(snapshot, combatFacts(
                CreditType.DIRECT, false, Optional.of(SpawnProvenance.SPAWNER), 20L, false))
                .awards().isEmpty());
        assertTrue(resolveCombat(snapshot, combatFacts(
                CreditType.DIRECT, false, Optional.empty(), 20L, false)).awards().isEmpty());
    }

    @Test
    void safeCombatDefaultsRejectEveryOptInFarmVectorButAllowNamedNaturalMobs() {
        ResearchPointAwardTrigger trigger = new ResearchPointAwardTrigger(
                ResearchPointAwardTrigger.Type.ENTITY_KILLED,
                Optional.of(target(List.of(), List.of(id("forge:undead")), List.of(), Optional.empty())),
                false, Optional.empty(), Optional.empty());
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(Map.of(
                id("test:tagged_combat"), definition(
                        id("test:combat"), 0, trigger, List.of(), ResearchPointAwardRepeat.Type.UNLIMITED)));

        CombatFacts safeNamed = new CombatFacts(
                CreditType.DIRECT, false, false, false, false, true, false,
                Optional.of(SpawnProvenance.NATURAL), 20L,
                id("minecraft:overworld"), Difficulty.NORMAL, false);
        assertEquals(1, resolveCombat(snapshot, safeNamed).awards().size());

        assertCombatRejected(snapshot, new CombatFacts(
                CreditType.PET, false, true, false, false, false, false,
                Optional.of(SpawnProvenance.NATURAL), 20L,
                id("minecraft:overworld"), Difficulty.NORMAL, false));
        assertCombatRejected(snapshot, new CombatFacts(
                CreditType.DIRECT, false, false, true, false, false, false,
                Optional.of(SpawnProvenance.NATURAL), 20L,
                id("minecraft:overworld"), Difficulty.NORMAL, false));
        assertCombatRejected(snapshot, new CombatFacts(
                CreditType.DIRECT, false, false, false, true, false, false,
                Optional.of(SpawnProvenance.NATURAL), 20L,
                id("minecraft:overworld"), Difficulty.NORMAL, false));
        assertCombatRejected(snapshot, new CombatFacts(
                CreditType.DIRECT, false, false, false, false, false, true,
                Optional.of(SpawnProvenance.NATURAL), 20L,
                id("minecraft:overworld"), Difficulty.NORMAL, false));
        assertCombatRejected(snapshot, combatFacts(
                CreditType.DIRECT, false, Optional.of(SpawnProvenance.BRED), 20L, false));
        assertCombatRejected(snapshot, combatFacts(
                CreditType.DIRECT, false, Optional.of(SpawnProvenance.SUMMONED), 20L, false));
    }

    private static void assertCombatRejected(
            ResearchPointAwardSnapshot snapshot,
            CombatFacts facts) {
        assertTrue(resolveCombat(snapshot, facts).awards().isEmpty());
    }

    @Test
    void nullInputsReturnAnExplicitInvalidResolution() {
        assertEquals(ResearchPointAwardResolver.Status.INVALID_CONTEXT,
                ResearchPointAwardResolver.resolve(null, blueprintContext(DispatchMode.LIVE, 0, 0)).status());
        assertEquals(ResearchPointAwardResolver.Status.INVALID_CONTEXT,
                ResearchPointAwardResolver.resolve(ResearchPointAwardSnapshot.EMPTY, null).status());
    }

    private static ResearchPointAwardResolver.Resolution resolveCombat(
            ResearchPointAwardSnapshot snapshot,
            CombatFacts facts) {
        return ResearchPointAwardResolver.resolve(snapshot, new ResearchPointAwardContext(
                ResearchPointAwardTrigger.Type.ENTITY_KILLED,
                PROFILE,
                DispatchMode.LIVE,
                Optional.of(id("minecraft:zombie")),
                Set.of(id("forge:undead")),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                0,
                Optional.of(facts)));
    }

    private static CombatFacts combatFacts(
            CreditType creditType,
            boolean fakePlayer,
            Optional<SpawnProvenance> provenance,
            long lifetime,
            boolean boss) {
        return new CombatFacts(
                creditType, fakePlayer, false, false, false, false, false,
                provenance, lifetime, id("minecraft:overworld"), Difficulty.NORMAL, boss);
    }

    private static ResearchPointAwardContext blueprintContext(
            DispatchMode dispatchMode,
            int previous,
            int current) {
        return new ResearchPointAwardContext(
                ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED,
                PROFILE,
                dispatchMode,
                Optional.of(TARGET),
                Set.of(TAG),
                Optional.of("PISTOL"),
                Optional.of(BlueprintKind.GUN),
                Optional.empty(),
                previous,
                current,
                Optional.empty());
    }

    private static ResearchPointAwardContext milestoneContext(int previous, int current) {
        return new ResearchPointAwardContext(
                ResearchPointAwardTrigger.Type.BLUEPRINT_MILESTONE,
                PROFILE,
                DispatchMode.RETROACTIVE,
                Optional.of(TARGET),
                Set.of(TAG),
                Optional.of("pistol"),
                Optional.of(BlueprintKind.GUN),
                Optional.of(ResearchPointAwardTrigger.MilestoneState.LEARNED),
                previous,
                current,
                Optional.empty());
    }

    private static ResearchPointAwardDefinition definition(
            ResourceLocation group,
            int priority,
            Optional<ResearchPointAwardTarget> target,
            List<ResourceLocation> profiles) {
        return definition(
                group,
                priority,
                new ResearchPointAwardTrigger(
                        ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED,
                        target,
                        false,
                        Optional.empty(),
                        Optional.empty()),
                profiles,
                ResearchPointAwardRepeat.Type.UNLIMITED);
    }

    private static ResearchPointAwardDefinition definition(
            ResourceLocation group,
            int priority,
            ResearchPointAwardTrigger trigger,
            List<ResourceLocation> profiles,
            ResearchPointAwardRepeat.Type repeatType) {
        return new ResearchPointAwardDefinition(
                1,
                true,
                profiles,
                group,
                priority,
                trigger,
                new ResearchPointAwardReward(2, ResearchPointAwardReward.Overflow.CLAMP),
                new ResearchPointAwardRepeat(
                        repeatType,
                        Optional.empty(),
                        ResearchPointAwardRepeat.Scope.DEFINITION,
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                Optional.empty(),
                new ResearchPointAwardPresentation(
                        ResearchPointAwardPresentation.Visibility.HIDDEN, Optional.empty()));
    }

    private static ResearchPointAwardTarget target(
            List<ResourceLocation> ids,
            List<ResourceLocation> tags,
            List<String> namespaces,
            Optional<CatalogSelector> selector) {
        return new ResearchPointAwardTarget(ids, tags, namespaces, selector);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
